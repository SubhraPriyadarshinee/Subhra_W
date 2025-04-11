package com.walmart.move.nim.receiving.endgame.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.stringfyJson;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.DEFAULT_DELIVERY_NUMBER;
import static java.util.stream.Collectors.groupingBy;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.config.OsdrConfig;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.OsdrConfigSpecification;
import com.walmart.move.nim.receiving.core.model.delivery.meta.DeliveryPOMap;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrPo;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrSummary;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import com.walmart.move.nim.receiving.core.service.EndgameOutboxHandler;
import com.walmart.move.nim.receiving.core.service.OsdrProcessor;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.model.OSDRRequest;
import com.walmart.move.nim.receiving.endgame.model.PoReceipt;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.EndgameOutboxServiceName;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

public class EndGameOsdrProcessor implements OsdrProcessor {
  private static final Logger LOGGER = LoggerFactory.getLogger(EndGameOsdrProcessor.class);

  @Resource(name = EndgameConstants.ENDGAME_DELIVERY_METADATA_SERVICE)
  private DeliveryMetaDataService endGameDeliveryMetaDataService;

  @Autowired private Gson gson;

  @Autowired EndGameOsdrService endGameOsdrService;

  @Autowired ReceiptService receiptService;

  @Autowired private EndgameDeliveryStatusPublisher endgameDeliveryStatusPublisher;

  @ManagedConfiguration private OsdrConfig osdrConfig;

  @Autowired EndgameOutboxHandler endgameOutboxHandler;

  /**
   * This method is responsible for processing the {@link OsdrSummary} for each delivery number for
   * which unloading has been completed
   *
   * @param osdrConfigSpecification
   */
  @Override
  public void process(OsdrConfigSpecification osdrConfigSpecification) {
    DeliveryPOMap deliveryPOMap = new DeliveryPOMap();
    List<DeliveryMetaData> deliveryMetaDataList =
        endGameDeliveryMetaDataService.findAndUpdateForOsdrProcessing(
            osdrConfigSpecification.getNosOfDay(),
            osdrConfigSpecification.getFrequencyIntervalInMinutes(),
            osdrConfig.getPageSize(),
            deliveryPOMap);
    processDeliveryPos(deliveryMetaDataList, osdrConfigSpecification, deliveryPOMap);
    processPoReceiptOSDR(osdrConfigSpecification);
  }

  public void processPoReceiptOSDR(OsdrConfigSpecification osdrConfigSpecification) {
    LocalDateTime lastProcessTime =
        LocalDateTime.now().minusMinutes(osdrConfigSpecification.getFrequencyIntervalInMinutes());
    processPoReceipt(lastProcessTime, LocalDateTime.now());
  }

  public void processPoReceipt(LocalDateTime fromTime, LocalDateTime toTime) {
    List<Receipt> receipts = receiptService.fetchPoReceipts(fromTime, toTime);
    receipts =
        receipts
            .stream()
            .filter(r -> r.getDeliveryNumber().equals(DEFAULT_DELIVERY_NUMBER))
            .collect(Collectors.toList());
    Map<String, List<Receipt>> poReceipts =
        receipts.stream().collect(groupingBy(Receipt::getPurchaseReferenceNumber));
    for (Map.Entry<String, List<Receipt>> receipt : poReceipts.entrySet()) {
      PoReceipt poReceipt =
          endGameOsdrService.generatePoReceipt(receipt.getKey(), receipt.getValue());
      publishPoReceiptToGDM(receipt.getKey(), poReceipt);
    }
  }

  private void publishPoReceiptToGDM(String poNumber, PoReceipt poReceipt) {
    LOGGER.debug("publishPoReceiptToGDM :: PoReceipt : {}", poReceipt);
    Map<String, Object> parameters = new HashMap<>(1);
    parameters.put("poNumber", poNumber);
    endgameOutboxHandler.sentToOutbox(
        stringfyJson(poReceipt), EndgameOutboxServiceName.PO_RECEIPT.getServiceName(), parameters);
  }

  public void processOSDR(OSDRRequest osdrRequest) {
    OsdrConfigSpecification osdrConfigSpecification =
        getOsdrConfigSpecifications(TenantContext.getFacilityNum());
    DeliveryPOMap deliveryPOMap = new DeliveryPOMap();
    if (!CollectionUtils.isEmpty(osdrRequest.getPoNos())) {
      getDeliveryPOMap(osdrRequest, deliveryPOMap);
    }
    List<DeliveryMetaData> deliveryMetaDataList =
        endGameDeliveryMetaDataService.findAllByDeliveryNumber(osdrRequest.getDeliveryNos());
    if (CollectionUtils.isEmpty(deliveryMetaDataList)) {
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.DELIVERY_METADATA_NOT_FOUND, ExceptionCodes.DELIVERY_METADATA_NOT_FOUND);
    }
    processDeliveryPos(deliveryMetaDataList, osdrConfigSpecification, deliveryPOMap);
  }

  private OsdrConfigSpecification getOsdrConfigSpecifications(Integer facilityNumber) {
    String specifications = osdrConfig.getSpecifications();
    if (!StringUtils.hasText(specifications)) {
      return null;
    }
    Type OsdrConfigSpecificationType =
        new TypeToken<ArrayList<OsdrConfigSpecification>>() {
          private static final long serialVersionUID = 1L;
        }.getType();
    List<OsdrConfigSpecification> osdrConfigSpecificationList =
        gson.fromJson(specifications, OsdrConfigSpecificationType);

    return osdrConfigSpecificationList
        .stream()
        .filter(isFacilityNumber(facilityNumber))
        .findFirst()
        .orElseThrow(receivingBadDataException(facilityNumber));
  }

  private Supplier<ReceivingBadDataException> receivingBadDataException(Integer facilityNumber) {
    return () ->
        new ReceivingBadDataException(
            ExceptionCodes.INVALID_REQUEST,
            String.format(
                ExceptionDescriptionConstants.OSDR_CONFIG_NOT_DONE_FOR_DC_ERROR_MSG,
                facilityNumber));
  }

  private Predicate<OsdrConfigSpecification> isFacilityNumber(Integer facilityNumber) {
    return s ->
        Objects.nonNull(s.getFacilityNum())
            && facilityNumber.intValue() == s.getFacilityNum().intValue();
  }

  private void getDeliveryPOMap(OSDRRequest osdrRequest, DeliveryPOMap deliveryPOMap) {
    deliveryPOMap.setDeliveryPOs(new HashMap<>());
    for (String po : osdrRequest.getPoNos()) {
      List<Receipt> receipts = receiptService.getReceiptSummary(po);
      receipts.forEach(
          receipt -> {
            String deliveryNumberStr = String.valueOf(receipt.getDeliveryNumber());
            if (!receipt.getDeliveryNumber().equals(DEFAULT_DELIVERY_NUMBER)) {
              if (!deliveryPOMap.getDeliveryPOs().containsKey(deliveryNumberStr)) {
                List<String> pos = new ArrayList<>();
                pos.add(po);
                deliveryPOMap.getDeliveryPOs().put(deliveryNumberStr, pos);
              } else {
                deliveryPOMap.getDeliveryPOs().get(deliveryNumberStr).add(po);
              }
            }
          });
      List<String> deliveryNumberList = new ArrayList<>(deliveryPOMap.getDeliveryPOs().keySet());
      osdrRequest.setDeliveryNos(deliveryNumberList);
    }
  }

  public void processDeliveryPos(
      List<DeliveryMetaData> deliveryMetaDataList,
      OsdrConfigSpecification osdrConfigSpecification,
      DeliveryPOMap deliveryPOMap) {

    if (!CollectionUtils.isEmpty(deliveryMetaDataList)) {
      for (DeliveryMetaData deliveryMetaData : deliveryMetaDataList) {
        try {
          // TODO Can be improved with in clause
          List<Receipt> receipts =
              receiptService.getReceiptSummary(
                  Long.valueOf(deliveryMetaData.getDeliveryNumber()), null, null);

          if (!CollectionUtils.isEmpty(deliveryPOMap.getDeliveryPOs())
              && deliveryPOMap.getDeliveryPOs().containsKey(deliveryMetaData.getDeliveryNumber())) {
            receipts =
                filterOSDRReceipts(
                    receipts,
                    deliveryPOMap.getDeliveryPOs().get(deliveryMetaData.getDeliveryNumber()));
          }
          OsdrSummary osdrSummary =
              endGameOsdrService.getOsdrDetails(
                  Long.valueOf(deliveryMetaData.getDeliveryNumber()),
                  receipts,
                  osdrConfigSpecification.getUom(),
                  EndgameConstants.DEFAULT_AUDIT_USER);
          LOGGER.info("EndGame [OsdrSummary = {}]", stringfyJson(osdrSummary));
          batchifyOsdrSummary(osdrSummary, osdrConfigSpecification);
        } catch (ReceivingDataNotFoundException e) {
          LOGGER.error(
              EndgameConstants.OSDR_DETAILS_NOT_FOUND_ERROR_MSG,
              deliveryMetaData.getDeliveryNumber(),
              ExceptionUtils.getStackTrace(e));
        }
      }
    }
  }

  private List<Receipt> filterOSDRReceipts(List<Receipt> receipts, List<String> osdrPONumbers) {
    List<Receipt> osdrReceipts = new ArrayList<>();
    for (Receipt receipt : receipts) {
      if (osdrPONumbers.contains(receipt.getPurchaseReferenceNumber())) {
        osdrReceipts.add(receipt);
      }
    }
    return osdrReceipts;
  }

  private void batchifyOsdrSummary(
      OsdrSummary osdrSummary, OsdrConfigSpecification osdrConfigSpecification) {
    if (Objects.nonNull(osdrConfigSpecification.getOsdrPOBatchSize())
        && !CollectionUtils.isEmpty(osdrSummary.getSummary())) {
      Collection<List<OsdrPo>> osdrPOSummaryBatch =
          ReceivingUtils.batchifyCollection(
              osdrSummary.getSummary(), osdrConfigSpecification.getOsdrPOBatchSize());
      osdrPOSummaryBatch.forEach(
          osdrPos -> {
            OsdrSummary summary =
                OsdrSummary.builder()
                    .summary(osdrPos)
                    .eventType(osdrSummary.getEventType())
                    .build();
            summary.setDeliveryNumber(osdrSummary.getDeliveryNumber());
            summary.setUserId(osdrSummary.getUserId());
            summary.setTs(new Date());
            publishOsdrSummary(summary);
          });
    } else {
      publishOsdrSummary(osdrSummary);
    }
  }

  /**
   * This method is responsible for publishing {@link OsdrSummary} to GDM
   *
   * @param osdrSummary
   */
  private void publishOsdrSummary(OsdrSummary osdrSummary) {
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    httpHeaders.set(EndgameConstants.USER_ID_HEADER_KEY, osdrSummary.getUserId());
    Map<String, Object> messageHeader = ReceivingUtils.getForwardablHeader(httpHeaders);
    messageHeader.put(EndgameConstants.DELIVERY_NUMBER, osdrSummary.getDeliveryNumber());
    messageHeader.put(EndgameConstants.OSDR_EVENT_TYPE_KEY, osdrSummary.getEventType());
    messageHeader.put(EndgameConstants.IDEM_POTENCY_KEY, UUID.randomUUID().toString());
    endgameDeliveryStatusPublisher.publishMessage(osdrSummary, messageHeader);
  }
}
