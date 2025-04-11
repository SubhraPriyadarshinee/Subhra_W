package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.entity.LabelDataLpn;
import com.walmart.move.nim.receiving.core.entity.PurgeData;
import com.walmart.move.nim.receiving.core.model.LabelDataIdentifier;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.delivery.meta.PurchaseOrderInfo;
import com.walmart.move.nim.receiving.core.model.label.acl.LabelType;
import com.walmart.move.nim.receiving.core.repositories.LabelDataLpnRepository;
import com.walmart.move.nim.receiving.core.repositories.LabelDataRepository;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** @author g0k0072 Service for label data entity */
@Service(ReceivingConstants.LABEL_INSTRUCTION_DATA_SERVICE)
public class LabelDataService implements Purge {
  private static final Logger LOGGER = LoggerFactory.getLogger(LabelDataService.class);
  @Autowired private LabelDataRepository labelDataRepository;
  @Autowired private LabelDataLpnRepository labelDataLpnRepository;
  @Autowired private LabelDataLpnService labelDataLpnService;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  /**
   * Find all label data by delivery number, purchase reference number and purchase reference line
   * number
   *
   * @param deliveryNumber delivery number
   * @param purchaseReferenceNumber purchase reference number
   * @param purchaseReferenceLineNumber purchase reference line number
   * @return list of label data matching the criteria
   */
  @Transactional(readOnly = true)
  @InjectTenantFilter
  public List<LabelData> findAllLabelDataByDeliveryPOPOL(
      long deliveryNumber, String purchaseReferenceNumber, int purchaseReferenceLineNumber) {
    return labelDataRepository
        .findAllByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            deliveryNumber,
            purchaseReferenceNumber,
            purchaseReferenceLineNumber,
            PageRequest.of(0, 3, Sort.by("createTs").ascending()));
  }

  /**
   * Find the count of all label data by delivery number, purchase reference number and purchase
   * reference line number
   *
   * @param deliveryNumber delivery number
   * @param purchaseReferenceNumber purchase reference number
   * @param purchaseReferenceLineNumber purchase reference line number
   * @param labelType the type of label
   * @return count label data matching the criteria
   */
  @Transactional(readOnly = true)
  @InjectTenantFilter
  public Integer countLabelDataOfTypeByDeliveryPOPOL(
      long deliveryNumber,
      String purchaseReferenceNumber,
      int purchaseReferenceLineNumber,
      LabelType labelType) {
    return labelDataRepository
        .countByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndLabelType(
            deliveryNumber, purchaseReferenceNumber, purchaseReferenceLineNumber, labelType);
  }

  /**
   * count label data by delivery number
   *
   * @param deliveryNumber delivery number
   * @return count of label data by delivery number
   */
  @Transactional
  @InjectTenantFilter
  public Integer countByDeliveryNumber(Long deliveryNumber) {
    return labelDataRepository.countByDeliveryNumber(deliveryNumber);
  }

  /**
   * Delete all label data by delivery number
   *
   * @param deliveryNumber delivery number
   */
  @Transactional
  @InjectTenantFilter
  public void deleteByDeliveryNumber(Long deliveryNumber) {
    labelDataRepository.deleteByDeliveryNumber(deliveryNumber);
  }

  /**
   * Delete all label data by delivery number, PO, PO line
   *
   * @param deliveryNumber delivery number
   * @param purchaseReferenceNumber PO number
   * @param purchaseReferenceLineNumber PO line number
   */
  @Transactional
  @InjectTenantFilter
  public void deleteByDeliveryNumberPoPoLine(
      long deliveryNumber, String purchaseReferenceNumber, int purchaseReferenceLineNumber) {
    labelDataRepository
        .deleteByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            deliveryNumber, purchaseReferenceNumber, purchaseReferenceLineNumber);
  }

  /**
   * Get all label data by delivery number
   *
   * @param deliveryNumber delivery number
   */
  @Transactional(readOnly = true)
  @InjectTenantFilter
  public List<LabelData> getLabelDataByDeliveryNumber(Long deliveryNumber) {
    return labelDataRepository.findByDeliveryNumber(deliveryNumber);
  }

  /**
   * Get all label data by delivery number sorted by sequence
   *
   * @param deliveryNumber delivery number
   */
  @Transactional(readOnly = true)
  @InjectTenantFilter
  public List<LabelData> getLabelDataByDeliveryNumberSortedBySeq(Long deliveryNumber) {
    return labelDataRepository.findByDeliveryNumberOrderBySequenceNoAsc(deliveryNumber);
  }

  @Transactional
  @InjectTenantFilter
  public List<LabelData> saveAll(List<LabelData> labelDataList) {
    List<LabelData> savedLabelDataList = labelDataRepository.saveAll(labelDataList);
    saveLabelDataLpns(labelDataList, savedLabelDataList);
    return savedLabelDataList;
  }

  @Transactional
  @InjectTenantFilter
  public LabelData save(LabelData labelData) {
    LabelData savedLabelData = labelDataRepository.save(labelData);
    saveLabelDataLpns(
        Collections.singletonList(labelData), Collections.singletonList(savedLabelData));
    return savedLabelData;
  }

  @Transactional(readOnly = true)
  @InjectTenantFilter
  public LabelData findByDeliveryNumberAndUPCAndLabelType(
      Long deliveryNumber, String upc, LabelType labelType) {
    return labelDataRepository.findByDeliveryNumberAndUPCAndLabelType(
        deliveryNumber, upc, labelType);
  }

  @Transactional(readOnly = true)
  @InjectTenantFilter
  public List<Long> findIfCommonItemExistsForDeliveries(
      List<Long> deliveryNumbers, Long currentDeliveryNumber) {
    return labelDataRepository.findCommonItemsForDeliveriesWithCurrentDelivery(
        deliveryNumbers, currentDeliveryNumber);
  }

  /**
   * count label data by delivery number
   *
   * @param deliveryNumber delivery number
   * @return count of label data by delivery number
   */
  @Transactional(readOnly = true)
  @InjectTenantFilter
  public Integer getMaxSequence(Long deliveryNumber) {
    LabelData labelData =
        labelDataRepository.findFirstByDeliveryNumberOrderBySequenceNoDesc(deliveryNumber);
    if (Objects.isNull(labelData) || Objects.isNull(labelData.getSequenceNo())) {
      return 0;
    } else {
      return labelData.getSequenceNo();
    }
  }

  public PurchaseOrderInfo getPurchaseOrderInfo(Long deliveryNumber, String lpn) {
    return Optional.ofNullable(getPurchaseOrderInfoFromLabelDataLpn(lpn))
        .orElseGet(() -> getPurchaseOrderInfoFromLabelData(deliveryNumber, lpn));
  }

  public PurchaseOrderInfo getPurchaseOrderInfoFromLabelDataLpn(String lpn) {
    if (tenantSpecificConfigReader.isFeatureFlagEnabled(
        ReceivingConstants.LABEL_DATA_LPN_QUERY_ENABLED)) {
      Optional<LabelData> labelDataOptional = labelDataLpnService.findLabelDataByLpn(lpn);
      if (labelDataOptional.isPresent()) {
        LabelData labelData = labelDataOptional.get();
        return PurchaseOrderInfo.builder()
            .deliveryNumber(labelData.getDeliveryNumber())
            .purchaseReferenceNumber(labelData.getPurchaseReferenceNumber())
            .purchaseReferenceLineNumber(labelData.getPurchaseReferenceLineNumber())
            .possibleUPC(labelData.getPossibleUPC())
            .build();
      } else {
        LOGGER.info(
            "LabelDataService: "
                + "PurchaseOrderInfo Search for lpn: {} in LabelDataLpn table Failed. "
                + "Entering fallback PurchaseOrderInfo Search in LabelData table.",
            lpn);
        return null;
      }
    }
    return null;
  }

  public PurchaseOrderInfo getPurchaseOrderInfoFromLabelData(Long deliveryNumber, String lpn) {
    PurchaseOrderInfo purchaseOrderInfo =
        labelDataRepository.findByDeliveryNumberAndContainsLPN(deliveryNumber, lpn);
    if (Objects.isNull(purchaseOrderInfo)) {
      LOGGER.info(
          "Couldn't find label for delivery {} and lpn {} using contains query. Triggering fallback.",
          deliveryNumber,
          lpn);
      purchaseOrderInfo = labelDataRepository.findByDeliveryNumberAndLPNLike(deliveryNumber, lpn);
    }
    return purchaseOrderInfo;
  }

  @Override
  @Transactional
  public long purge(PurgeData purgeEntity, PageRequest pageRequest, int purgeEntitiesBeforeXdays) {
    List<LabelData> labelDataList =
        labelDataRepository.findByIdGreaterThanEqual(purgeEntity.getLastDeleteId(), pageRequest);

    Date deleteDate = getPurgeDate(purgeEntitiesBeforeXdays);

    // filter out list by validating last createTs
    labelDataList =
        labelDataList
            .stream()
            .filter(item -> item.getCreateTs().before(deleteDate))
            .sorted(Comparator.comparing(LabelData::getId))
            .collect(Collectors.toList());

    if (CollectionUtils.isEmpty(labelDataList)) {
      LOGGER.info("Purge LABEL_DATA: Nothing to delete");
      return purgeEntity.getLastDeleteId();
    }
    long lastDeletedId = labelDataList.get(labelDataList.size() - 1).getId();

    LOGGER.info(
        "Purge LABEL_DATA: {} records : ID {} to {} : START",
        labelDataList.size(),
        labelDataList.get(0).getId(),
        lastDeletedId);
    labelDataRepository.deleteAll(labelDataList);
    LOGGER.info("Purge LABEL_DATA: END");
    return lastDeletedId;
  }

  @Transactional(
      readOnly = true,
      timeout = ReceivingConstants.DEFAULT_TRANSACTION_TIME_OUT_IN_SECONDS)
  @InjectTenantFilter
  public List<LabelData> fetchLabelDataByPoAndItemNumber(
      String purchaseReferenceNumber,
      Long itemNumber,
      String labelStatus,
      int labelQty,
      Integer facilityNum,
      String facilityCountryCode) {
    return labelDataRepository.fetchLabelDataByPoAndItemNumber(
        purchaseReferenceNumber,
        itemNumber,
        labelStatus,
        labelQty,
        facilityNum,
        facilityCountryCode);
  }

  @Transactional
  @InjectTenantFilter
  public List<LabelData> fetchByLpnsIn(List<String> lpns) {
    return labelDataRepository.findByLpnsIn(lpns);
  }

  @Transactional
  @InjectTenantFilter
  public LabelData fetchByLpns(String lpns) {
    return labelDataRepository.findByLpns(lpns);
  }

  @Transactional(
      readOnly = true,
      timeout = ReceivingConstants.DEFAULT_TRANSACTION_TIME_OUT_IN_SECONDS)
  @InjectTenantFilter
  public List<LabelData> fetchByPurchaseReferenceNumber(String purchaseReferenceNumber) {
    return labelDataRepository.findByPurchaseReferenceNumber(purchaseReferenceNumber);
  }

  @Transactional(
      readOnly = true,
      timeout = ReceivingConstants.DEFAULT_TRANSACTION_TIME_OUT_IN_SECONDS)
  @InjectTenantFilter
  public List<LabelData> fetchByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
      String purchaseReferenceNumber, Integer purchaseReferenceLineNumber) {
    return labelDataRepository.findByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
        purchaseReferenceNumber, purchaseReferenceLineNumber);
  }

  @Transactional(
      readOnly = true,
      timeout = ReceivingConstants.DEFAULT_TRANSACTION_TIME_OUT_IN_SECONDS)
  @InjectTenantFilter
  public List<LabelData> findByItemNumber(Long itemNumber) {
    return labelDataRepository.findByItemNumber(itemNumber);
  }

  @Transactional(
      readOnly = true,
      timeout = ReceivingConstants.DEFAULT_TRANSACTION_TIME_OUT_IN_SECONDS)
  @InjectTenantFilter
  public List<LabelData> findByItemNumberAndStatus(Long itemNumber, String labelStatus) {
    return labelDataRepository.findByItemNumberAndStatus(itemNumber, labelStatus);
  }

  @Transactional(
      readOnly = true,
      timeout = ReceivingConstants.DEFAULT_TRANSACTION_TIME_OUT_IN_SECONDS)
  @InjectTenantFilter
  public List<LabelData> findByDeliveryNumberAndItemNumberAndStatus(
      Long deliveryNumber, Long itemNumber, String labelStatus) {
    return labelDataRepository.findByDeliveryNumberAndItemNumberAndStatus(
        deliveryNumber, itemNumber, labelStatus);
  }

  @Transactional(
      readOnly = true,
      timeout = ReceivingConstants.DEFAULT_TRANSACTION_TIME_OUT_IN_SECONDS)
  @InjectTenantFilter
  public List<LabelData> findByPurchaseReferenceNumberAndItemNumberAndStatus(
      String purchaseReferenceNumber, Long itemNumber, String labelStatus) {
    return labelDataRepository.findByPurchaseReferenceNumberAndItemNumberAndStatus(
        purchaseReferenceNumber, itemNumber, labelStatus);
  }

  @Transactional(
      readOnly = true,
      timeout = ReceivingConstants.DEFAULT_TRANSACTION_TIME_OUT_IN_SECONDS)
  @InjectTenantFilter
  public List<LabelData> findByPurchaseReferenceNumberInAndItemNumberAndStatus(
      Set<String> purchaseReferenceNumberSet, Long itemNumber, String labelStatus) {
    return labelDataRepository.findByPurchaseReferenceNumberInAndItemNumberAndStatus(
        purchaseReferenceNumberSet, itemNumber, labelStatus);
  }

  @Transactional(
      readOnly = true,
      timeout = ReceivingConstants.DEFAULT_TRANSACTION_TIME_OUT_IN_SECONDS)
  @InjectTenantFilter
  public Integer fetchLabelCountByDeliveryNumber(Long deliveryNumber) {
    return labelDataRepository.fetchLabelCountByDeliveryNumber(deliveryNumber);
  }

  @Transactional(
      readOnly = true,
      timeout = ReceivingConstants.DEFAULT_TRANSACTION_TIME_OUT_IN_SECONDS)
  @InjectTenantFilter
  public Integer fetchItemCountByDeliveryNumber(Long deliveryNumber) {
    return labelDataRepository.fetchItemCountByDeliveryNumber(deliveryNumber);
  }

  @Transactional(
      readOnly = true,
      timeout = ReceivingConstants.DEFAULT_TRANSACTION_TIME_OUT_IN_SECONDS)
  @InjectTenantFilter
  public List<LabelData> fetchLabelDataByPoAndItemNumberAndStoreNumber(
      String purchaseReferenceNumber,
      Long itemNumber,
      Integer storeNumber,
      String labelStatus,
      int labelQty,
      Integer facilityNum,
      String facilityCountryCode) {
    return labelDataRepository.fetchLabelDataByPoAndItemNumberAndStoreNumber(
        purchaseReferenceNumber,
        itemNumber,
        storeNumber,
        labelStatus,
        labelQty,
        facilityNum,
        facilityCountryCode);
  }

  @Transactional
  @InjectTenantFilter
  public LabelData findByLpnsAndLabelIn(String lpns, List<String> offlineLabels) {
    return labelDataRepository.findByLpnsAndLabelIn(lpns, offlineLabels);
  }

  @Transactional
  @InjectTenantFilter
  public LabelData findByLpnsAndStatus(String lpn, String labelStatus) {
    return labelDataRepository.findByLpnsAndStatus(lpn, labelStatus);
  }

  @Transactional(
      readOnly = true,
      timeout = ReceivingConstants.DEFAULT_TRANSACTION_TIME_OUT_IN_SECONDS)
  @InjectTenantFilter
  public List<LabelData> findBySsccAndAsnNumberAndStatus(
      String sscc, String asnNbr, String status) {
    return labelDataRepository.findBySsccAndAsnNumberAndStatus(sscc, asnNbr, status);
  }

  @Transactional(
      readOnly = true,
      timeout = ReceivingConstants.DEFAULT_TRANSACTION_TIME_OUT_IN_SECONDS)
  @InjectTenantFilter
  public List<LabelData> findBySsccAndAsnNumber(String sscc, String asnNbr) {
    return labelDataRepository.findBySsccAndAsnNumber(sscc, asnNbr);
  }

  @Transactional(
      readOnly = true,
      timeout = ReceivingConstants.DEFAULT_TRANSACTION_TIME_OUT_IN_SECONDS)
  @InjectTenantFilter
  public Integer fetchLabelCountByDeliveryNumberInLabelDownloadEvent(Long deliveryNumber) {
    return labelDataRepository.fetchLabelCountByDeliveryNumberInLabelDownloadEvent(deliveryNumber);
  }

  @Transactional(
      readOnly = true,
      timeout = ReceivingConstants.DEFAULT_TRANSACTION_TIME_OUT_IN_SECONDS)
  @InjectTenantFilter
  public Integer fetchItemCountByDeliveryNumberInLabelDownloadEvent(Long deliveryNumber) {
    return labelDataRepository.fetchItemCountByDeliveryNumberInLabelDownloadEvent(deliveryNumber);
  }

  @Transactional(timeout = ReceivingConstants.DEFAULT_TRANSACTION_TIME_OUT_IN_SECONDS)
  @InjectTenantFilter
  @Retryable(
      value = {CannotAcquireLockException.class},
      maxAttemptsExpression = "${max.retry.count}",
      backoff = @Backoff(delayExpression = "${retry.delay}"))
  public void saveAllAndFlush(List<LabelData> labelDataList) {
    labelDataRepository.saveAllAndFlush(labelDataList);
  }

  @Transactional(
      readOnly = true,
      timeout = ReceivingConstants.DEFAULT_TRANSACTION_TIME_OUT_IN_SECONDS)
  @InjectTenantFilter
  public List<LabelData> findByTrackingIdIn(List<String> trackingIds) {
    return labelDataRepository.findByTrackingIdIn(trackingIds);
  }

  @Transactional(readOnly = true)
  @InjectTenantFilter
  public LabelData findByTrackingId(String trackingId) {
    return labelDataRepository.findByTrackingId(trackingId);
  }

  @Transactional(
      readOnly = true,
      timeout = ReceivingConstants.DEFAULT_TRANSACTION_TIME_OUT_IN_SECONDS)
  @InjectTenantFilter
  public LabelData findByTrackingIdAndLabelIn(String trackingId, List<String> offlineLabels) {
    return labelDataRepository.findByTrackingIdAndLabelIn(trackingId, offlineLabels);
  }

  @Transactional(
      readOnly = true,
      timeout = ReceivingConstants.DEFAULT_TRANSACTION_TIME_OUT_IN_SECONDS)
  @InjectTenantFilter
  public LabelData findByTrackingIdAndStatus(String trackingId, String labelStatus) {
    return labelDataRepository.findByTrackingIdAndStatus(trackingId, labelStatus);
  }

  @Transactional(
      readOnly = true,
      timeout = ReceivingConstants.DEFAULT_TRANSACTION_TIME_OUT_IN_SECONDS)
  @InjectTenantFilter
  public List<LabelData> fetchByPurchaseReferenceNumberAndStatus(
      String purchaseReferenceNumber, String labelStatus) {
    return labelDataRepository.findByPurchaseReferenceNumberAndStatus(
        purchaseReferenceNumber, labelStatus);
  }

  @Transactional(readOnly = true)
  @InjectTenantFilter
  public List<Pair<Long, String>> findItemPossibleUPCPairsForDeliveryNumber(
      Long currentDeliveryNumber) {
    return labelDataRepository.findItemPossibleUPCPairsForDeliveryNumber(currentDeliveryNumber);
  }

  @Transactional(readOnly = true)
  @InjectTenantFilter
  public List<String> findPossibleUPCsForDeliveryNumbersIn(List<Long> activeDeliveries) {
    return labelDataRepository.findPossibleUPCsForDeliveryNumbersIn(activeDeliveries);
  }

  /**
   * save label data lpn entities
   *
   * @param labelDataList in-memory LabelData entities with the transient field LabelDataLpnList as
   *     set if any new lpns were generated
   * @param savedLabelDataList saved (managed) LabelData entities with ID
   */
  protected List<LabelDataLpn> saveLabelDataLpns(
      List<LabelData> labelDataList, List<LabelData> savedLabelDataList) {

    if (tenantSpecificConfigReader.isFeatureFlagEnabled(
        ReceivingConstants.LABEL_DATA_LPN_INSERTION_ENABLED)) {
      List<LabelDataLpn> labelDataLpnsToSave = new ArrayList<>();
      HashMap<LabelDataIdentifier, List<LabelDataLpn>> labelDataLpnMap = new HashMap<>();
      for (LabelData labelData : CollectionUtils.emptyIfNull(labelDataList)) {
        if (CollectionUtils.isNotEmpty(labelData.getLabelDataLpnList())) {
          labelDataLpnMap.put(LabelDataIdentifier.from(labelData), labelData.getLabelDataLpnList());
        }
      }
      if (labelDataLpnMap.isEmpty()) {
        return Collections.emptyList();
      }
      for (LabelData labelData : CollectionUtils.emptyIfNull(savedLabelDataList)) {
        LabelDataIdentifier labelDataIdentifier = LabelDataIdentifier.from(labelData);
        List<LabelDataLpn> labelDataLpnList = labelDataLpnMap.get(labelDataIdentifier);
        for (LabelDataLpn labelDataLpn : CollectionUtils.emptyIfNull(labelDataLpnList)) {
          labelDataLpn.setLabelDataId(labelData.getId());
          labelDataLpnsToSave.add(labelDataLpn);
        }
      }
      return labelDataLpnRepository.saveAll(labelDataLpnsToSave);
    }
    return Collections.emptyList();
  }
}
