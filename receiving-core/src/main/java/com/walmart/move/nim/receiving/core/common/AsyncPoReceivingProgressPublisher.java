package com.walmart.move.nim.receiving.core.common;

import static com.walmart.move.nim.receiving.core.common.JpaConverterJson.gson;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static java.util.Objects.nonNull;

import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.message.publisher.KafkaMessagePublisher;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.delivery.meta.PoProgressDetails;
import com.walmart.move.nim.receiving.core.model.delivery.meta.ReceiveProgressMeta;
import com.walmart.move.nim.receiving.core.repositories.DeliveryMetaDataRepository;
import com.walmart.move.nim.receiving.core.repositories.ReceiptRepository;
import com.walmart.move.nim.receiving.core.service.DeliveryServiceRetryableImpl;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.lang3.Range;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class AsyncPoReceivingProgressPublisher {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(AsyncPoReceivingProgressPublisher.class);
  @Autowired ReceiptRepository receiptRepository;
  @Autowired protected KafkaMessagePublisher kafkaMessagePublisher;
  @ManagedConfiguration protected AppConfig appConfig;
  @Autowired protected DeliveryServiceRetryableImpl deliveryService;
  @Autowired protected DeliveryMetaDataRepository deliveryMetaDataRepository;

  @Value("${atlas.receive.progress.topic:null}")
  protected String receiveProgressTopic;

  private String PROGRESS_DETAILS_LIST = "poProgressDetailsList";

  @Async
  public void publishPoReceiveProgress(
      Long deliveryNumber, String poNumber, HttpHeaders httpHeaders) {
    Integer currentPoReceivedPercentage = ZERO_QTY;
    Integer previousProgressSentPercentage = ZERO_QTY;
    try {
      LOGGER.info(
          "publishPoReceiveProgress for delivery={} , PO={}, CorrelationID={}",
          deliveryNumber,
          poNumber,
          httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY));
      TenantContext.setFacilityNum(
          Integer.valueOf(httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM)));
      TenantContext.setFacilityCountryCode(
          httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE));
      boolean isCommitDeliveryMetaData = false;
      DeliveryMetaData deliveryMetaData = getDeliveryMetaData(deliveryNumber);
      if (null == deliveryMetaData
          || (StringUtils.isBlank(deliveryMetaData.getReceiveProgress())
              || !(deliveryMetaData.getReceiveProgress().contains(PROGRESS_DETAILS_LIST)))) {
        isCommitDeliveryMetaData = true;
        deliveryMetaData =
            getUpdatedDeliveryMetaData(deliveryMetaData, deliveryNumber, httpHeaders);
      }
      ReceiveProgressMeta receiveProgressMeta =
          gson.fromJson(deliveryMetaData.getReceiveProgress(), ReceiveProgressMeta.class);
      PoProgressDetails poProgressDetails = getPoProgressDetails(receiveProgressMeta, poNumber);
      previousProgressSentPercentage = poProgressDetails.getPoReceivedPercentage();
      Range<Integer> firstRange = Range.between(25, 50);
      Range<Integer> secondRange = Range.between(51, 75);
      Range<Integer> thirdRange = Range.between(76, 99);
      Range<Integer> fourthRange = Range.between(100, 150);
      String sendProgressPercentage = null;
      Double receivedPoQty =
          getTotalReceivedQuantityByPOAndDeliveryNumber(deliveryNumber, poNumber).doubleValue();
      currentPoReceivedPercentage =
          getPercentage(poProgressDetails.getTotalPoQty().doubleValue(), receivedPoQty);
      if (isProgressNotSent(
          firstRange,
          previousProgressSentPercentage,
          currentPoReceivedPercentage,
          TWENTY_FIVE_CONSTANT)) {
        sendProgressPercentage = TWENTY_FIVE_CONSTANT;
      } else if (isProgressNotSent(
          secondRange,
          previousProgressSentPercentage,
          currentPoReceivedPercentage,
          FIFTY_CONSTANT)) {
        sendProgressPercentage = FIFTY_CONSTANT;
      } else if (isProgressNotSent(
          thirdRange,
          previousProgressSentPercentage,
          currentPoReceivedPercentage,
          SEVENTY_FIVE_CONSTANT)) {
        sendProgressPercentage = SEVENTY_FIVE_CONSTANT;
      } else if (isProgressNotSent(
          fourthRange,
          previousProgressSentPercentage,
          currentPoReceivedPercentage,
          HUNDRED_CONSTANT)) {
        sendProgressPercentage = HUNDRED_CONSTANT;
      }

      if (StringUtils.isNotBlank(sendProgressPercentage)) {
        poProgressDetails.setPoReceivedPercentage(Integer.valueOf(sendProgressPercentage));
        Map<String, Object> headers = getReceivingProgressHeader(httpHeaders);
        // Publish receipts progress in Kafka - yms consumer
        kafkaMessagePublisher.publish(
            poNumber.concat("-" + sendProgressPercentage),
            getReceivingProgressPayload(
                deliveryNumber,
                poNumber,
                currentPoReceivedPercentage.toString(),
                receivedPoQty,
                headers,
                receiveProgressMeta,
                poProgressDetails,
                deliveryMetaData),
            receiveProgressTopic,
            headers);
      }

      if (isCommitDeliveryMetaData || StringUtils.isNotBlank(sendProgressPercentage)) {
        // Save published percentage into DB by PO
        persistReceivingProgressSentPercentage(deliveryMetaData, receiveProgressMeta);
      }
      LOGGER.info(
          "Sent ReceivingPoProgress={} for delivery={}, PO={},previousProgressSentPercentage={},currentPoReceivedPercentage={} CorrelationID={}",
          StringUtils.isNotBlank(sendProgressPercentage),
          deliveryNumber,
          poNumber,
          previousProgressSentPercentage,
          currentPoReceivedPercentage,
          httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY));
    } catch (Exception e) {
      LOGGER.error(
          "Error in publishing PoReceiveProgress for the delivery={}, PO={}, CorrelationID={},previousProgressSentPercentage={},currentPoReceivedPercentage={}, stack={}",
          deliveryNumber,
          poNumber,
          httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY),
          previousProgressSentPercentage,
          currentPoReceivedPercentage,
          ExceptionUtils.getStackTrace(e));
    }
  }

  private boolean isProgressNotSent(
      Range<Integer> range,
      Integer previousUpdateSentPercentage,
      Integer currentPoReceivedPercentage,
      String currentPercentageRange) {
    return range.contains(currentPoReceivedPercentage)
        && !previousUpdateSentPercentage.toString().equalsIgnoreCase(currentPercentageRange);
  }

  private Integer getPercentage(Double totalQty, Double receivedQty) {
    Double receivedPercentage = (double) 0;
    if (totalQty != 0 && receivedQty != 0)
      receivedPercentage = Math.ceil((receivedQty * 100) / totalQty);
    return receivedPercentage.intValue();
  }

  private PoProgressDetails getPoProgressDetails(
      ReceiveProgressMeta receiveProgressMeta, String poNumber) {
    return receiveProgressMeta
        .getPoProgressDetailsList()
        .stream()
        .filter(poProgressDetail -> poProgressDetail.getPoNumber().equalsIgnoreCase(poNumber))
        .findFirst()
        .get();
  }

  @InjectTenantFilter
  private void persistReceivingProgressSentPercentage(
      DeliveryMetaData deliveryData, ReceiveProgressMeta receiveProgressMeta) {
    deliveryData.setReceiveProgress(gson.toJson(receiveProgressMeta, ReceiveProgressMeta.class));
    deliveryMetaDataRepository.save(deliveryData);
  }

  @InjectTenantFilter
  private DeliveryMetaData getDeliveryMetaData(Long deliveryNumber) {
    Optional<DeliveryMetaData> deliveryMetaData = Optional.empty();
    DeliveryMetaData deliveryData = null;
    try {
      deliveryMetaData = deliveryMetaDataRepository.findByDeliveryNumber(deliveryNumber.toString());
      deliveryData = deliveryMetaData.isPresent() ? deliveryMetaData.get() : null;
    } catch (Exception e) {
      // no element found
      LOGGER.error(
          "Exception getting deliveryMetaData deliveryNumber={}, exception={}",
          deliveryNumber,
          ExceptionUtils.getStackTrace(e));
      return deliveryData;
    }
    return deliveryData;
  }

  private DeliveryMetaData getUpdatedDeliveryMetaData(
      DeliveryMetaData deliveryData, Long deliveryNumber, HttpHeaders httpHeaders)
      throws ReceivingException {
    if (null == deliveryData)
      deliveryData = getNewDeliveryMetaData(deliveryNumber.toString(), httpHeaders);
    String deliveryInfo = getDeliveryInfoFromGDM(deliveryNumber, httpHeaders);
    GdmPOLineResponse gdmPOLineResponse = gson.fromJson(deliveryInfo, GdmPOLineResponse.class);
    ReceiveProgressMeta receiveProgressMeta = new ReceiveProgressMeta();
    receiveProgressMeta.setTotalDeliveryQty(getTotalFrightQty(gdmPOLineResponse));
    receiveProgressMeta.setPoProgressDetailsList(getNewPoProgressDetails(gdmPOLineResponse));
    deliveryData.setReceiveProgress(gson.toJson(receiveProgressMeta, ReceiveProgressMeta.class));
    deliveryData.setTrailerNumber(gdmPOLineResponse.getTrailerId());
    deliveryData.setCarrierName(gdmPOLineResponse.getCarrierCode());
    return deliveryData;
  }

  private List<PoProgressDetails> getNewPoProgressDetails(GdmPOLineResponse gdmPOLineResponse) {
    List<PoProgressDetails> poProgressDetailsList = new ArrayList<>();
    List<DeliveryDocument> deliveryDocuments = gdmPOLineResponse.getDeliveryDocuments();

    deliveryDocuments
        .stream()
        .map(DeliveryDocument::getPurchaseReferenceNumber)
        .distinct()
        .collect(Collectors.toList())
        .stream()
        .forEach(
            poNumber -> {
              PoProgressDetails poProgressDetails = new PoProgressDetails();
              poProgressDetails.setPoReceivedPercentage(ZERO_QTY);
              poProgressDetails.setTotalPoQty(getTotalFrightQtyByPo(gdmPOLineResponse, poNumber));
              poProgressDetails.setPoNumber(poNumber);
              DeliveryDocumentLine deliveryDocumentLine =
                  getDeliveryDocumentLine(gdmPOLineResponse, poNumber);
              poProgressDetails.setQtyUOM(
                  !Objects.isNull(deliveryDocumentLine) ? deliveryDocumentLine.getQtyUOM() : null);
              poProgressDetailsList.add(poProgressDetails);
            });
    return poProgressDetailsList;
  }

  private ReceivingProgressMessage getReceivingProgressPayload(
      Long deliveryNumber,
      String poNumber,
      String currentPoReceivedPercentage,
      Double receivedPoQty,
      Map<String, Object> headers,
      ReceiveProgressMeta receiveProgressMeta,
      PoProgressDetails poProgressDetails,
      DeliveryMetaData deliveryMetaData) {
    ReceivingProgressMessage receivingProgressMessage = new ReceivingProgressMessage();
    ReceivingProgressPayload receivingProgressPayload = new ReceivingProgressPayload();
    receivingProgressMessage.setHeader(headers);
    int unloadedUnitCount = getReceivedTotalByDelivery(deliveryNumber);
    PurchaseOrderInfo purchaseOrder = new PurchaseOrderInfo();
    purchaseOrder.setPoNumber(poNumber);
    purchaseOrder.setUnitUOM(poProgressDetails.getQtyUOM());
    purchaseOrder.setUnloadPercent(Integer.valueOf(currentPoReceivedPercentage));
    purchaseOrder.setScheduledUnitCount(poProgressDetails.getTotalPoQty());
    purchaseOrder.setUnloadedUnitCount(receivedPoQty.intValue());

    UnLoaderDetail unLoaderDetail = new UnLoaderDetail();
    unLoaderDetail.setUnLoaderId(
        nonNull(headers.get(PRINT_LABEL_USER_ID_V1))
            ? headers.get(PRINT_LABEL_USER_ID_V1).toString()
            : null);
    unLoaderDetail.setUnLoaderAssignedTimestamp(LocalDateTime.now().toString());

    receivingProgressPayload.setDeliveryNumber(deliveryNumber.toString());
    receivingProgressPayload.setCarrierId(deliveryMetaData.getCarrierName());
    receivingProgressPayload.setTrailerId(deliveryMetaData.getTrailerNumber());
    receivingProgressPayload.setScheduledUnitCount(receiveProgressMeta.getTotalDeliveryQty());
    receivingProgressPayload.setUnitUOM(poProgressDetails.getQtyUOM());
    receivingProgressPayload.setUnloadedUnitCount(unloadedUnitCount);
    receivingProgressPayload.setUnloadPercent(
        getPercentage(
            Double.valueOf(receiveProgressMeta.getTotalDeliveryQty()),
            Double.valueOf(unloadedUnitCount)));
    receivingProgressPayload.setPurchaseOrders(Arrays.asList(purchaseOrder));
    receivingProgressPayload.setUnLoaderDetails(Arrays.asList(unLoaderDetail));
    receivingProgressMessage.setPayload(receivingProgressPayload);
    return receivingProgressMessage;
  }

  @InjectTenantFilter
  private Integer getReceivedTotalByDelivery(Long deliveryNumber) {
    return receiptRepository.getTotalReceivedQuantityByDeliveryNumber(deliveryNumber);
  }

  @InjectTenantFilter
  private Integer getTotalReceivedQuantityByPOAndDeliveryNumber(
      Long deliveryNumber, String poNumber) {
    return receiptRepository.getTotalReceivedQuantityByPOAndDeliveryNumber(
        deliveryNumber, poNumber);
  }

  private Map<String, Object> getReceivingProgressHeader(HttpHeaders httpHeaders) {
    Map<String, Object> headers = new HashMap<>();
    headers.put(EVENT_TYPE, UNLOAD_PROGRESS);
    headers.put(SYM_VERSION_KEY, VERSION_1_0);
    headers.put(EVENT_TIMESTAMP, LocalDateTime.now().toString());
    headers.put(PRINT_LABEL_USER_ID_V1, httpHeaders.getFirst(USER_ID_HEADER_KEY));
    headers.put(CORRELATION_ID, httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY));
    headers.put(CORRELATION_ID_HEADER_KEY, httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY));
    headers.put(SOURCE_ID, RECEIVING);
    headers.put(TENENT_FACLITYNUM, httpHeaders.getFirst(TENENT_FACLITYNUM));
    headers.put(TENENT_COUNTRY_CODE, httpHeaders.getFirst(TENENT_COUNTRY_CODE));
    return headers;
  }

  private String getDeliveryInfoFromGDM(Long deliveryNumber, HttpHeaders httpHeaders)
      throws ReceivingException {
    Map<String, Long> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.DELIVERY_NUMBER, deliveryNumber);
    String gdmBaseUri = appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_GET_DELIVERY_URI;
    URI gdmGetDeliveryUri =
        UriComponentsBuilder.fromUriString(gdmBaseUri).buildAndExpand(pathParams).toUri();
    return deliveryService.getDeliveryByURI(gdmGetDeliveryUri, httpHeaders);
  }

  private Integer getTotalFrightQty(GdmPOLineResponse gdmPOLineResponse) {
    List<DeliveryDocument> deliveryDocuments = gdmPOLineResponse.getDeliveryDocuments();
    return deliveryDocuments
        .stream()
        .map(DeliveryDocument::getTotalPurchaseReferenceQty)
        .reduce(0, Integer::sum);
  }

  private DeliveryDocumentLine getDeliveryDocumentLine(
      GdmPOLineResponse gdmPOLineResponse, String poNumber) {
    DeliveryDocumentLine deliveryDocumentLine = null;
    Optional<DeliveryDocument> deliveryDocument =
        gdmPOLineResponse
            .getDeliveryDocuments()
            .stream()
            .filter(
                deliveryDoc -> deliveryDoc.getPurchaseReferenceNumber().equalsIgnoreCase(poNumber))
            .findFirst();
    if (deliveryDocument.isPresent()) {
      Optional<DeliveryDocumentLine> optionalDeliveryDocumentLine =
          deliveryDocument.get().getDeliveryDocumentLines().stream().findFirst();
      if (optionalDeliveryDocumentLine.isPresent())
        deliveryDocumentLine = optionalDeliveryDocumentLine.get();
    }
    return deliveryDocumentLine;
  }

  private DeliveryMetaData getNewDeliveryMetaData(String deliveryNumber, HttpHeaders httpHeaders) {
    DeliveryMetaData deliveryMetaData = new DeliveryMetaData();
    deliveryMetaData.setDeliveryNumber(deliveryNumber);
    deliveryMetaData.setFacilityNum(Integer.valueOf(httpHeaders.getFirst(TENENT_FACLITYNUM)));
    deliveryMetaData.setFacilityCountryCode(httpHeaders.getFirst(TENENT_COUNTRY_CODE));
    return deliveryMetaData;
  }

  private Integer getTotalFrightQtyByPo(GdmPOLineResponse gdmPOLineResponse, String poNumber) {
    List<DeliveryDocument> deliveryDocuments = gdmPOLineResponse.getDeliveryDocuments();
    return deliveryDocuments
        .stream()
        .filter(
            deliveryDocument ->
                deliveryDocument.getPurchaseReferenceNumber().equalsIgnoreCase(poNumber))
        .map(DeliveryDocument::getTotalPurchaseReferenceQty)
        .reduce(0, Integer::sum);
  }
}
