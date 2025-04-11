package com.walmart.move.nim.receiving.endgame.service;

import static com.walmart.move.nim.receiving.endgame.common.AuditHelper.isNonTrustedVendor;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static java.util.stream.Collectors.toSet;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.model.DeliveryDoorSummary;
import com.walmart.move.nim.receiving.core.model.DeliveryHeaderSearchDetails;
import com.walmart.move.nim.receiving.core.model.ReceiptForOsrdProcess;
import com.walmart.move.nim.receiving.core.model.audit.AuditFlagResponse;
import com.walmart.move.nim.receiving.core.model.delivery.meta.DeliveryPOMap;
import com.walmart.move.nim.receiving.core.model.gdm.GdmDeliverySearchByStatusRequest;
import com.walmart.move.nim.receiving.core.model.gdm.v3.DeliveryList;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrderLine;
import com.walmart.move.nim.receiving.core.repositories.DeliveryMetaDataRepository;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import com.walmart.move.nim.receiving.core.service.DeliveryServiceImpl;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.endgame.common.EndGameUtils;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

public class EndGameDeliveryMetaDataService extends DeliveryMetaDataService {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(EndGameDeliveryMetaDataService.class);
  @Autowired private DeliveryMetaDataRepository deliveryMetaDataRepository;
  @Autowired private ReceiptService receiptService;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Resource(name = ReceivingConstants.DELIVERY_SERVICE)
  private DeliveryServiceImpl deliveryService;

  @Autowired protected Gson gson;

  /**
   * This method is responsible to store rotate date for a particular po/poline for a delivery in
   * DeliveryMetaData Table.
   *
   * @param deliveryMetaData
   * @param itemNumber
   * @param rotateDate
   * @param divertDestination
   */
  @Override
  @Transactional
  @InjectTenantFilter
  public void updateDeliveryMetaDataForItemOverrides(
      DeliveryMetaData deliveryMetaData,
      String itemNumber,
      String rotateDate,
      String divertDestination) {
    if (isUpdateRequiredInDeliveryMetaData(
        deliveryMetaData, itemNumber, rotateDate, divertDestination)) {
      updateDeliveryMetaData(deliveryMetaData, itemNumber, rotateDate, divertDestination);
      deliveryMetaDataRepository.save(deliveryMetaData);
    }
  }

  @Override
  @Transactional
  @InjectTenantFilter
  public void updateAuditInfo(
      DeliveryMetaData deliveryMetaData, List<AuditFlagResponse> auditFlagResponseList) {
    if (tenantSpecificConfigReader.isFeatureFlagEnabled(AUDIT_V2_ENABLED)) {
      updateAuditInfoInDeliveryMetaDataV2(deliveryMetaData, auditFlagResponseList);
    } else {
      updateAuditInfoInDeliveryMetaData(deliveryMetaData, auditFlagResponseList);
    }
    deliveryMetaDataRepository.save(deliveryMetaData);
  }

  private void updateAuditInfoInDeliveryMetaData(
      DeliveryMetaData deliveryMetaData, List<AuditFlagResponse> auditFlagResponseList) {
    auditFlagResponseList
        .stream()
        .filter(flagResponse -> isNonTrustedVendor(flagResponse, false))
        .forEach(
            auditFlagResponse -> {
              String itemNumber = String.valueOf(auditFlagResponse.getItemNumber());
              LinkedTreeMap<String, String> itemDetails =
                  getItemDetails(deliveryMetaData, itemNumber);
              itemDetails.put(IS_AUDIT_REQUIRED, Boolean.TRUE.toString());
            });
  }

  private void updateAuditInfoInDeliveryMetaDataV2(
      DeliveryMetaData deliveryMetaData, List<AuditFlagResponse> auditFlagResponseList) {
    auditFlagResponseList.forEach(
        auditFlagResponse -> {
          String itemNumber = String.valueOf(auditFlagResponse.getItemNumber());
          LinkedTreeMap<String, String> itemDetails = getItemDetails(deliveryMetaData, itemNumber);
          LOGGER.info(
              "Vendor Type: {}, Item Number: {}", auditFlagResponse.getVendorType(), itemDetails);
          if (auditFlagResponse.getVendorType().equals(TRUSTED_VENDOR)
              && Boolean.TRUE.equals(auditFlagResponse.getIsFrequentlyReceivedQuantityRequired())) {
            itemDetails.put(RECEIVED_CASE_QTY, auditFlagResponse.getReceivedQuantity());
          } else if (auditFlagResponse.getVendorType().equals(NON_TRUSTED_VENDOR)) {
            itemDetails.put(IS_AUDIT_REQUIRED, Boolean.TRUE.toString());
            itemDetails.put(IS_AUDIT_IN_PROGRESS, Boolean.FALSE.toString());
          } else if (auditFlagResponse.getVendorType().equals(IN_CONSISTENT_VENDOR)) {
            itemDetails.put(IS_AUDIT_REQUIRED, Boolean.TRUE.toString());
            itemDetails.put(IS_AUDIT_IN_PROGRESS, Boolean.TRUE.toString());
            LOGGER.info(
                "EXPECTED_CASE_QUANTITY: {}, Item Number: {}",
                getExpectedCaseQty(auditFlagResponse),
                itemDetails);
            itemDetails.put(EXPECTED_CASE_QUANTITY, getExpectedCaseQty(auditFlagResponse));
            itemDetails.put(
                CASES_TO_BE_AUDITED, String.valueOf(auditFlagResponse.getCaseToAudit()));
          }
        });
  }

  private String getExpectedCaseQty(AuditFlagResponse auditFlagResponse) {
    return String.valueOf(
        auditFlagResponse.getQtyUom().equalsIgnoreCase(VENDORPACK)
            ? auditFlagResponse.getVnpkRatio()
            : !auditFlagResponse.getQtyUom().equalsIgnoreCase(EACHES)
                ? auditFlagResponse.getWhpkRatio()
                : ONE);
  }

  private void updateDeliveryMetaData(
      DeliveryMetaData deliveryMetaData,
      String itemNumber,
      String rotateDate,
      String divertDestination) {
    LinkedTreeMap<String, String> itemDetails = getItemDetails(deliveryMetaData, itemNumber);
    if (!ObjectUtils.isEmpty(rotateDate)) {
      itemDetails.put(EndgameConstants.ROTATE_DATE, rotateDate);
    }
    if (!ObjectUtils.isEmpty(divertDestination)) {
      itemDetails.put(EndgameConstants.DIVERT_DESTINATION, divertDestination);
    }
  }

  private LinkedTreeMap<String, String> getItemDetails(
      DeliveryMetaData deliveryMetaData, String itemNumber) {
    LinkedTreeMap<String, String> itemDetails;
    if (Objects.isNull(deliveryMetaData.getItemDetails())) {
      itemDetails = new LinkedTreeMap<>();
      LinkedTreeMap<String, LinkedTreeMap<String, String>> itemDetailsMap = new LinkedTreeMap<>();
      itemDetailsMap.put(itemNumber, itemDetails);
      deliveryMetaData.setItemDetails(itemDetailsMap);
    } else {
      LinkedTreeMap<String, LinkedTreeMap<String, String>> itemDetailsMap =
          deliveryMetaData.getItemDetails();
      if (itemDetailsMap.containsKey(itemNumber)) {
        itemDetails = itemDetailsMap.get(itemNumber);
      } else {
        itemDetails = new LinkedTreeMap<>();
        itemDetailsMap.put(itemNumber, itemDetails);
      }
    }
    return itemDetails;
  }

  private boolean isUpdateRequiredInDeliveryMetaData(
      DeliveryMetaData deliveryMetaData,
      String itemNumber,
      String rotateDate,
      String divertDestination) {
    String metaDataRotateDate =
        EndGameUtils.getItemAttributeFromDeliveryMetaData(
            deliveryMetaData, itemNumber, EndgameConstants.ROTATE_DATE);
    String metaDataDivert =
        EndGameUtils.getItemAttributeFromDeliveryMetaData(
            deliveryMetaData, itemNumber, EndgameConstants.DIVERT_DESTINATION);
    return (!ObjectUtils.isEmpty(rotateDate) && !rotateDate.equals(metaDataRotateDate))
        || (!ObjectUtils.isEmpty(divertDestination) && !divertDestination.equals(metaDataDivert));
  }

  /**
   * This method is responsible for fetching list of {@link DeliveryMetaData} where
   * unloadingCompleteDate is greater than the allowed no of days after unloading complete and
   * osdrLastProcessedDate is either not available or not processed with in the frequency interval.
   *
   * <p>Then it will update all those {@link DeliveryMetaData} for osdr last processed date with
   * current date and time.
   *
   * @param allowedNoOfDaysAfterUnloadingComplete
   * @param frequencyIntervalInMinutes
   * @return
   */
  @Override
  @Transactional
  @InjectTenantFilter
  public List<DeliveryMetaData> findAndUpdateForOsdrProcessing(
      int allowedNoOfDaysAfterUnloadingComplete,
      long frequencyIntervalInMinutes,
      int pageSize,
      DeliveryPOMap deliveryPOMap) {

    List<DeliveryMetaData> deliveryMetaDataListToProcess = new ArrayList<>();

    Date unloadingCompleteDate =
        Date.from(Instant.now().minus(Duration.ofMinutes(frequencyIntervalInMinutes)));

    List<ReceiptForOsrdProcess> result =
        receiptService.fetchReceiptForOsrdProcess(unloadingCompleteDate);

    Map<String, Set<String>> deliveryPOList =
        result
            .stream()
            .filter(r -> !r.getDeliveryNumber().equals(DEFAULT_DELIVERY_NUMBER))
            .collect(
                Collectors.groupingBy(
                    r -> String.valueOf(r.getDeliveryNumber()),
                    Collectors.mapping(
                        ReceiptForOsrdProcess::getPurchaseReferenceNumber, toSet())));
    List<DeliveryMetaData> deliveryMetaDataList =
        findAllByDeliveryNumber(new ArrayList<>(deliveryPOList.keySet()));

    for (DeliveryMetaData deliveryMetaData : deliveryMetaDataList) {
      String deliveryNumber = deliveryMetaData.getDeliveryNumber();
      if (!Objects.isNull(deliveryMetaData.getOsdrLastProcessedDate())) {
        List<String> pos =
            receiptService.fetchReceiptPOsBasedOnDelivery(
                deliveryNumber, deliveryMetaData.getOsdrLastProcessedDate());
        LOGGER.debug(
            "EndGame osrd filter Pos [DeliveryNo={}] [OsdrLastProcessedDate={}] [Pos = {}] ",
            deliveryNumber,
            deliveryMetaData.getOsdrLastProcessedDate(),
            ReceivingUtils.stringfyJson(pos));
        // update any new pos for delivery.
        if (CollectionUtils.isNotEmpty(pos)) {
          deliveryPOList.replace(deliveryNumber, new HashSet<>(pos));
        } else {
          deliveryPOList.remove(deliveryNumber);
          continue;
        }
      }
      deliveryMetaData.setOsdrLastProcessedDate(new Date());
      deliveryMetaDataListToProcess.add(deliveryMetaData);
    }
    saveAll(deliveryMetaDataListToProcess);
    deliveryPOMap.setDeliveryPOs(convert(deliveryPOList));
    return deliveryMetaDataListToProcess;
  }

  private Map<String, List<String>> convert(Map<String, Set<String>> oldMap) {
    Map<String, List<String>> newMap = new HashMap<>();
    for (Map.Entry<String, Set<String>> entry : oldMap.entrySet()) {
      newMap.put(entry.getKey(), new ArrayList<>(entry.getValue()));
    }
    return newMap;
  }

  @Override
  public boolean updateAuditInfoInDeliveryMetaData(
      List<PurchaseOrder> purchaseOrders, int receivedQty, long deliveryNumber) {
    DeliveryMetaData deliveryMetaData = getDeliveryMetaData(deliveryNumber);
    List<PurchaseOrderLine> poLines = purchaseOrders.get(0).getLines();
    Pair<Boolean, Boolean> metaDataUpdateAndOutBoxFlag =
        updateItemMetadata(deliveryMetaData, poLines, receivedQty);
    if (metaDataUpdateAndOutBoxFlag.getFirst()) deliveryMetaDataRepository.save(deliveryMetaData);
    return metaDataUpdateAndOutBoxFlag.getSecond();
  }

  public void updateAttachPoInfoInDeliveryMetaData(DeliveryMetaData deliveryMetaData) {
    deliveryMetaDataRepository.save(deliveryMetaData);
  }

  public DeliveryMetaData getDeliveryMetaData(long deliveryNumber) {
    return deliveryMetaDataRepository
        .findByDeliveryNumber(String.valueOf(deliveryNumber))
        .orElseThrow(
            () ->
                new ReceivingDataNotFoundException(
                    ExceptionCodes.DELIVERY_METADATA_NOT_FOUND,
                    String.format(
                        EndgameConstants.DELIVERY_METADATA_NOT_FOUND_ERROR_MSG, deliveryNumber)));
  }

  private Pair<Boolean, Boolean> updateItemMetadata(
      DeliveryMetaData deliveryMetaData, List<PurchaseOrderLine> poLines, int receivedQty) {
    boolean metadataUpdated = false;
    for (PurchaseOrderLine pl : poLines) {
      String itemNumber = String.valueOf(pl.getItemDetails().getNumber());
      LinkedTreeMap<String, String> itemDetails = getItemDetails(deliveryMetaData, itemNumber);
      boolean auditRequired = Boolean.parseBoolean(itemDetails.get(IS_AUDIT_REQUIRED));
      boolean auditInProgress = Boolean.parseBoolean(itemDetails.get(IS_AUDIT_IN_PROGRESS));
      if (auditRequired && auditInProgress) {
        int cases = convertStringToIntValue(itemDetails.get(CASES_TO_BE_AUDITED));
        if (cases > 0) {
          if (!itemDetails.containsKey(RECEIVED_CASE_QTY)) {
            itemDetails.put(RECEIVED_CASE_QTY, String.valueOf(receivedQty));
          } else if (convertStringToIntValue(itemDetails.get(RECEIVED_CASE_QTY)) != receivedQty) {
            itemDetails.put(IS_AUDIT_IN_PROGRESS, Boolean.FALSE.toString());
            itemDetails.remove(CASES_TO_BE_AUDITED);
            return Pair.of(true, false);
          }
          itemDetails.put(CASES_TO_BE_AUDITED, String.valueOf(--cases));
          metadataUpdated = true;
          if (cases == 0) {
            itemDetails.put(IS_AUDIT_REQUIRED, Boolean.FALSE.toString());
            itemDetails.remove(IS_AUDIT_IN_PROGRESS);
            itemDetails.remove(CASES_TO_BE_AUDITED);
            return Pair.of(metadataUpdated, true);
          }
        }
      }
    }
    return Pair.of(metadataUpdated, false);
  }

  private int convertStringToIntValue(String qtyString) {
    return !StringUtils.isEmpty(qtyString) ? Integer.parseInt(qtyString) : 0;
  }

  public int getReceivedQtyFromMetadata(Long itemNumber, long deliveryNumber) {
    DeliveryMetaData deliveryMetaData = getDeliveryMetaData(deliveryNumber);
    int receivedCaseQty = 0;
    LinkedTreeMap<String, String> itemDetails =
        getItemDetails(deliveryMetaData, String.valueOf(itemNumber));
    if (!Boolean.parseBoolean(itemDetails.get(IS_AUDIT_REQUIRED))
        && !Boolean.parseBoolean(itemDetails.get(IS_AUDIT_IN_PROGRESS))
        && itemDetails.containsKey(RECEIVED_CASE_QTY)) {
      receivedCaseQty = Integer.parseInt(itemDetails.get(RECEIVED_CASE_QTY));
    }
    return receivedCaseQty;
  }

  @Override
  public int getReceivedQtyFromMetadataWithoutAuditCheck(Long itemNumber, long deliveryNumber) {
    DeliveryMetaData deliveryMetaData = getDeliveryMetaData(deliveryNumber);
    int receivedCaseQty = 0;
    LinkedTreeMap<String, String> itemDetails =
        getItemDetails(deliveryMetaData, String.valueOf(itemNumber));
    if (itemDetails.containsKey(RECEIVED_CASE_QTY)) {
      receivedCaseQty = Integer.parseInt(itemDetails.get(RECEIVED_CASE_QTY));
    }
    return receivedCaseQty;
  }

  @Override
  public DeliveryDoorSummary findDoorStatus(
      Integer facilityNumber, String countryCode, String doorNumber) throws ReceivingException {
    GdmDeliverySearchByStatusRequest deliverySearchByStatusRequest =
        GdmDeliverySearchByStatusRequest.builder()
            .criteria(DeliveryHeaderSearchDetails.builder().doorNumber(doorNumber).build())
            .build();
    String requestString = gson.toJson(deliverySearchByStatusRequest);
    String deliveryDocumentString =
        deliveryService.getDeliveryDocumentBySearchCriteria(requestString);

    DeliveryList deliveries = gson.fromJson(deliveryDocumentString, DeliveryList.class);
    DeliveryDoorSummary deliveryDoorSummary = new DeliveryDoorSummary();
    deliveryDoorSummary.setDoorNumber(doorNumber);
    deliveryDoorSummary.setDoorOccupied(false);
    if (!CollectionUtils.isEmpty(deliveries.getData())) {
      deliveryDoorSummary.setDoorOccupied(true);
      deliveryDoorSummary.setDeliveryNumber(
          String.valueOf(deliveries.getData().get(0).getDeliveryNumber()));
    }
    return deliveryDoorSummary;
  }
}
