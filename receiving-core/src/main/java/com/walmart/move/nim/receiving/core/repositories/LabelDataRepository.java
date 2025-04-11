package com.walmart.move.nim.receiving.core.repositories;

import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.label.acl.LabelType;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LabelDataRepository
    extends JpaRepository<LabelData, Long>, LabelDataCustomRepository {

  List<LabelData> findAllByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
      long deliveryNumber,
      String purchaseReferenceNumber,
      int purchaseReferenceLineNumber,
      Pageable pageable);

  Integer countByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndLabelType(
      long deliveryNumber,
      String purchaseReferenceNumber,
      int purchaseReferenceLineNumber,
      LabelType labelType);

  @Query(value = "SELECT SUM(lpnsCount) FROM LabelData WHERE deliveryNumber = :deliveryNumber")
  Integer countByDeliveryNumber(Long deliveryNumber);

  void deleteByDeliveryNumber(Long deliveryNumber);

  void deleteByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
      long deliveryNumber, String purchaseReferenceNumber, int purchaseReferenceLineNumber);

  List<LabelData> findByDeliveryNumber(long deliveryNumber);

  List<LabelData> findByDeliveryNumberOrderBySequenceNoAsc(long deliveryNumber);

  LabelData findFirstByDeliveryNumberOrderBySequenceNoDesc(long deliveryNumber);

  List<LabelData> findByIdGreaterThanEqual(Long lastDeleteId, Pageable pageable);

  @Query(
      value =
          "SELECT DISTINCT(itemNumber) "
              + "FROM LabelData "
              + "WHERE deliveryNumber IN :deliveryNumbers "
              + "AND rejectReason is NULL "
              + "AND itemNumber IN "
              + "(SELECT DISTINCT(itemNumber) "
              + "FROM LabelData "
              + "WHERE deliveryNumber = :currentDeliveryNumber AND rejectReason is NULL)")
  List<Long> findCommonItemsForDeliveriesWithCurrentDelivery(
      List<Long> deliveryNumbers, Long currentDeliveryNumber);

  @Query(
      value =
          "SELECT TOP(:labelQty) * FROM LABEL_DATA WHERE facilityNum = :facilityNum AND facilityCountryCode = :facilityCountryCode AND PURCHASE_REFERENCE_NUMBER = :purchaseReferenceNumber AND ITEM_NUMBER = :itemNumber AND STATUS = :labelStatus ORDER BY LABEL_SEQUENCE_NBR",
      nativeQuery = true)
  List<LabelData> fetchLabelDataByPoAndItemNumber(
      String purchaseReferenceNumber,
      Long itemNumber,
      String labelStatus,
      int labelQty,
      Integer facilityNum,
      String facilityCountryCode);

  public List<LabelData> findByLpnsIn(List<String> lpns);

  public LabelData findByLpns(String lpns);

  public List<LabelData> findByPurchaseReferenceNumber(String purchaseReferenceNumber);

  public List<LabelData> findByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
      String purchaseReferenceNumber, Integer purchaseReferenceLineNumber);

  List<LabelData> findByItemNumber(long itemNumber);

  List<LabelData> findByItemNumberAndStatus(Long itemNumber, String labelStatus);

  List<LabelData> findByDeliveryNumberAndItemNumberAndStatus(
      Long deliveryNumber, Long itemNumber, String labelStatus);

  List<LabelData> findByPurchaseReferenceNumberAndItemNumberAndStatus(
      String purchaseReferenceNumber, Long itemNumber, String labelStatus);

  List<LabelData> findByPurchaseReferenceNumberInAndItemNumberAndStatus(
      Set<String> purchaseReferenceNumberSet, Long itemNumber, String labelStatus);

  @Query(
      value =
          "SELECT COUNT(DISTINCT ITEM_NUMBER) FROM LABEL_DATA WHERE DELIVERY_NUMBER = :deliveryNumber",
      nativeQuery = true)
  Integer fetchItemCountByDeliveryNumber(Long deliveryNumber);

  @Query(
      value = "SELECT COUNT(*) FROM LABEL_DATA WHERE DELIVERY_NUMBER = :deliveryNumber",
      nativeQuery = true)
  Integer fetchLabelCountByDeliveryNumber(Long deliveryNumber);

  @Query(
      value =
          "SELECT TOP(:labelQty) * FROM LABEL_DATA WHERE facilityNum = :facilityNum AND facilityCountryCode = :facilityCountryCode AND PURCHASE_REFERENCE_NUMBER = :purchaseReferenceNumber AND ITEM_NUMBER = :itemNumber AND DEST_STR_NBR = :storeNumber AND STATUS = :labelStatus ORDER BY LABEL_SEQUENCE_NBR",
      nativeQuery = true)
  List<LabelData> fetchLabelDataByPoAndItemNumberAndStoreNumber(
      String purchaseReferenceNumber,
      Long itemNumber,
      Integer storeNumber,
      String labelStatus,
      int labelQty,
      Integer facilityNum,
      String facilityCountryCode);

  LabelData findByLpnsAndLabelIn(String lpns, List<String> offlineLabels);

  LabelData findByLpnsAndStatus(String lpns, String labelStatus);

  List<LabelData> findBySsccAndAsnNumberAndStatus(String sscc, String asnNbr, String status);

  List<LabelData> findBySsccAndAsnNumber(String sscc, String asnNbr);

  @Query(
      value =
          "SELECT Count(*) FROM LABEL_DATA l, LABEL_DOWNLOAD_EVENT ld WHERE l.PURCHASE_REFERENCE_NUMBER = ld.PURCHASE_REFERENCE_NUMBER AND l.ITEM_NUMBER = ld.ITEM_NUMBER AND ld.DELIVERY_NUMBER = :deliveryNumber",
      nativeQuery = true)
  Integer fetchLabelCountByDeliveryNumberInLabelDownloadEvent(Long deliveryNumber);

  @Query(
      value =
          "SELECT Count(DISTINCT l.ITEM_NUMBER) FROM LABEL_DATA l, LABEL_DOWNLOAD_EVENT ld WHERE l.PURCHASE_REFERENCE_NUMBER = ld.PURCHASE_REFERENCE_NUMBER AND l.ITEM_NUMBER = ld.ITEM_NUMBER AND ld.DELIVERY_NUMBER = :deliveryNumber",
      nativeQuery = true)
  Integer fetchItemCountByDeliveryNumberInLabelDownloadEvent(Long deliveryNumber);

  public List<LabelData> findByTrackingIdIn(List<String> trackingIds);

  public LabelData findByTrackingId(String trackingId);

  public LabelData findByTrackingIdAndLabelIn(String trackingId, List<String> offlineLabels);

  public LabelData findByTrackingIdAndStatus(String trackingId, String labelStatus);

  public List<LabelData> findByPurchaseReferenceNumberAndStatus(
      String purchaseReferenceNumber, String labelStatus);

  @Query(
      value =
          "SELECT new com.walmart.move.nim.receiving.core.model.Pair(ld.itemNumber as key, ld.possibleUPC as value) "
              + "FROM LabelData ld "
              + "WHERE deliveryNumber = :deliveryNumber "
              + "AND rejectReason is NULL "
              + "GROUP BY itemNumber, possibleUPC ")
  List<Pair<Long, String>> findItemPossibleUPCPairsForDeliveryNumber(Long deliveryNumber);

  @Query(
      value =
          "SELECT DISTINCT(possibleUPC) "
              + "FROM LabelData "
              + "WHERE deliveryNumber IN :activeDeliveries "
              + "AND rejectReason is NULL ")
  List<String> findPossibleUPCsForDeliveryNumbersIn(List<Long> activeDeliveries);
}
