package com.walmart.move.nim.receiving.core.event.processor.delivery;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.advice.SecurePublisher;
import com.walmart.move.nim.receiving.core.common.KafkaHelper;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.framework.message.processor.ProcessExecutor;
import com.walmart.move.nim.receiving.core.message.common.ReceivingEvent;
import com.walmart.move.nim.receiving.core.model.GdmPOLineResponse;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryQtyByPo;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryQtyByPoResponse;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryResponse;
import com.walmart.move.nim.receiving.core.model.yms.v2.*;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component(ReceivingConstants.BEAN_DELIVERY_PROGRESS_UPDATE_PROCESSOR)
public class DeliveryProcessUpdateProcessor implements ProcessExecutor {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DeliveryProcessUpdateProcessor.class);
  private static final String CASES = "CASES";
  private static final String YMS_CONTRACT_VERSION = "1.0";
  private static final String SOURCE_ATLAS = "Atlas";
  private static final String EVENT_TYPE_UNLOAD_PROGRESS = "UNLOAD_PROGRESS";
  @ManagedConfiguration private AppConfig appConfig;

  @Autowired private ReceiptService receiptService;

  @SecurePublisher private KafkaTemplate kafkaTemplate;

  @Value("${delivery.update.yms.topic:default_yms_topic}")
  private String deliveryUpdateProgressTopic;

  private Gson gson;

  public DeliveryProcessUpdateProcessor() {
    this.gson = new Gson();
  }

  @Override
  public void doExecute(ReceivingEvent receivingEvent) {
    ProgressUpdateDTO progressUpdateDTO = null;

    try {

      progressUpdateDTO = this.gson.fromJson(receivingEvent.getPayload(), ProgressUpdateDTO.class);

      HttpHeaders httpHeaders = ReceivingUtils.getHeaders();

      ReceiptSummaryQtyByPoResponse receiptSummaryQtyByPoResponse =
          receiptService.getReceiptsSummaryByPo(progressUpdateDTO.getDeliveryNumber(), httpHeaders);

      Map<String, List<ReceiptSummaryQtyByPo>> poMap =
          receiptSummaryQtyByPoResponse
              .getSummary()
              .stream()
              .collect(Collectors.groupingBy(ReceiptSummaryQtyByPo::getPurchaseReferenceNumber));

      List<DeliveryPurchaseOrder> deliveryPurchaseOrders = new ArrayList<>();
      poMap
          .entrySet()
          .stream()
          .forEach(
              entry -> {
                AtomicReference<Long> fbq = new AtomicReference<>(0L);
                AtomicReference<Long> receivedQty = new AtomicReference<>(0L);
                entry
                    .getValue()
                    .stream()
                    .forEach(
                        poQty -> {
                          fbq.updateAndGet(v -> v + poQty.getFreightBillQuantity());
                          receivedQty.updateAndGet(v -> v + poQty.getReceivedQty());
                        });

                // TODO : To be converted to VNPK if YMS not support eaches
                DeliveryPurchaseOrder deliveryPurchaseOrder =
                    DeliveryPurchaseOrder.builder()
                        .poNumber(entry.getKey())
                        .scheduledUnitCount(fbq.get())
                        .unloadedUnitCount(receivedQty.get())
                        .unloadPercent(calculatePercentage(receivedQty.get(), fbq.get()))
                        .unitUOM(CASES)
                        .build();
                deliveryPurchaseOrders.add(deliveryPurchaseOrder);
              });

      String workingUserId =
          Objects.nonNull(receiptSummaryQtyByPoResponse.getGdmPOLineResponse())
                  && Objects.nonNull(receiptSummaryQtyByPoResponse.getGdmPOLineResponse())
                  && Objects.nonNull(
                      receiptSummaryQtyByPoResponse.getGdmPOLineResponse().getWorkingUserId())
              ? receiptSummaryQtyByPoResponse.getGdmPOLineResponse().getWorkingUserId()
              : Objects.nonNull(
                      receiptSummaryQtyByPoResponse.getGdmPOLineResponse().getDoorOpenUserId())
                  ? receiptSummaryQtyByPoResponse.getGdmPOLineResponse().getDoorOpenUserId()
                  : ReceivingConstants.DEFAULT_USER;

      long scheduledUnitCount =
          retrieveScheduledQty(receiptSummaryQtyByPoResponse.getGdmPOLineResponse());

      long unloadUnitCount =
          retrieveUnloadCount(deliveryPurchaseOrders, progressUpdateDTO.getDeliveryNumber());

      DeliveryUnloaderDetails deliveryUnloaderDetails =
          DeliveryUnloaderDetails.builder()
              .unLoaderId(workingUserId)
              .unLoaderAssignedTimestamp(
                  receiptSummaryQtyByPoResponse.getGdmPOLineResponse().getWorkingTimeStamp())
              .build();

      DeliveryProgressUpdatePayload deliveryProgressUpdatePayload =
          DeliveryProgressUpdatePayload.builder()
              .deliveryNumber(String.valueOf(progressUpdateDTO.getDeliveryNumber()))
              .scheduledUnitCount(scheduledUnitCount)
              .unloadedUnitCount(unloadUnitCount)
              .unloadPercent(calculatePercentage(unloadUnitCount, scheduledUnitCount))
              .purchaseOrders(deliveryPurchaseOrders)
              .unLoaderDetails(Arrays.asList(deliveryUnloaderDetails))
              .build();

      DeliveryProgressUpdateHeader deliveryProgressUpdateHeader =
          DeliveryProgressUpdateHeader.builder()
              .correlationId(TenantContext.getCorrelationId())
              .countryCode(TenantContext.getFacilityCountryCode())
              .eventTimestamp(ReceivingUtils.dateInEST())
              .dcNumber(TenantContext.getFacilityNum())
              .version(YMS_CONTRACT_VERSION)
              .userId(workingUserId)
              .sourceId(SOURCE_ATLAS)
              .facilityNum(TenantContext.getFacilityNum())
              .facilityCountryCode(TenantContext.getFacilityCountryCode())
              .eventType(EVENT_TYPE_UNLOAD_PROGRESS)
              .eventTimestamp(ReceivingUtils.dateConversionToUTC(new Date()))
              .build();

      DeliveryProgressUpdateDTO deliveryProgressUpdateDTO =
          DeliveryProgressUpdateDTO.builder()
              .payload(deliveryProgressUpdatePayload)
              .header(deliveryProgressUpdateHeader)
              .build();
      LOGGER.info(
          "Message to be publish for delivery-progress-update {}",
          ReceivingUtils.stringfyJson(deliveryProgressUpdateDTO));

      Map<String, Object> headers = new HashMap<>(ReceivingUtils.getHeaders().toSingleValueMap());
      String corrId = (String) headers.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY);
      corrId = Objects.nonNull(corrId) ? corrId : UUID.randomUUID().toString();
      headers.put(ReceivingConstants.MESSAGE_ID_HEADER, corrId);
      headers.put(ReceivingConstants.CORRELATION_ID_HEADER_KEY, corrId);

      Message<String> message =
          KafkaHelper.buildKafkaMessage(
              deliveryProgressUpdateDTO.getPayload().getDeliveryNumber(),
              gson.toJson(deliveryProgressUpdateDTO),
              deliveryUpdateProgressTopic,
              headers);
      kafkaTemplate.send(message);

    } catch (ReceivingException e) {
      LOGGER.error(
          "Exception occured while processing the YMS delivery update. errorCode = {} ",
          ReceivingConstants.ERROR_DELIVERY_UPDATE_YMS,
          e);
    }
  }

  private long retrieveUnloadCount(
      List<DeliveryPurchaseOrder> deliveryPurchaseOrders, Long deliveryNumber) {
    if (CollectionUtils.isEmpty(deliveryPurchaseOrders)) {
      List<ReceiptSummaryResponse> receiptSummaryResponseList =
          receiptService.getReceivedQtySummaryByPoInVnpk(deliveryNumber);
      return receiptSummaryResponseList.stream().mapToLong(rsp -> rsp.getReceivedQty()).sum();
    }
    return deliveryPurchaseOrders.stream().mapToLong(po -> po.getUnloadedUnitCount()).sum();
  }

  private long retrieveScheduledQty(GdmPOLineResponse gdmPOLineResponse) {

    // Accumulate all the BOL for a PO if not exists default it to totalPurchasseReferenceQty
    return gdmPOLineResponse
        .getDeliveryDocuments()
        .stream()
        .mapToLong(
            doc -> {
              if (CollectionUtils.isEmpty(doc.getBolNumbers())) {
                return doc.getTotalPurchaseReferenceQty();
              }
              return doc.getBolNumbers()
                  .stream()
                  .mapToLong(bol -> bol.getFreightBillQuantity())
                  .sum();
            })
        .sum();
  }

  @Override
  public boolean isAsync() {
    return appConfig.isYmsUpdateAsyncEnable();
  }

  private double calculatePercentage(Long receivedQty, Long totalQty) {
    return totalQty != 0 ? ((double) receivedQty * 100 / totalQty) : 0;
  }
}
