/** */
package com.walmart.move.nim.receiving.core.repositories;

import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.ReceiptForOsrdProcess;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** @author a0b02ft */
@Repository
public interface ReceiptRepository extends JpaRepository<Receipt, Long> {
  /**
   * This method is responsible for providing received quantity by delivery number
   *
   * @param deliveryNumber
   * @return
   */
  List<Receipt> findByDeliveryNumber(Long deliveryNumber);

  /**
   * This method is responsible for providing received quantity by purchaseReferenceNumber
   *
   * @param purchaseReferenceNumber
   * @return
   */
  List<Receipt> findByPurchaseReferenceNumber(String purchaseReferenceNumber);

  /**
   * this method is for data clean up needed by either DIT or E2E test scripts
   *
   * @param purchaseReferenceNumber
   */
  void deleteByPurchaseReferenceNumber(String purchaseReferenceNumber);

  /**
   * This method is used for reporting for providing receipts by delivery and po/po line
   *
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @return
   */
  List<Receipt> findByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
      String purchaseReferenceNumber, Integer purchaseReferenceLineNumber);

  /**
   * This method is used to retrieve Receipt by DeliveryNumber, PurchaseReferenceNumber and
   * PurchaseReferenceLineNumber
   *
   * @param deliveryNumber
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @return
   */
  List<Receipt> findByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
      Long deliveryNumber, String purchaseReferenceNumber, Integer purchaseReferenceLineNumber);

  /**
   * This method is used to retrieve First Receipt by DeliveryNumber, PurchaseReferenceNumber and
   * PurchaseReferenceLineNumber
   *
   * @param deliveryNumber
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @return
   */
  Receipt findFirstByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
      Long deliveryNumber, String purchaseReferenceNumber, Integer purchaseReferenceLineNumber);

  /**
   * This method is used to retrieve Receipt by DeliveryNumber, PurchaseReferenceNumber
   *
   * @param deliveryNumber
   * @param purchaseReferenceNumber
   * @return
   */
  List<Receipt> findByDeliveryNumberAndPurchaseReferenceNumber(
      Long deliveryNumber, String purchaseReferenceNumber);

  /**
   * Get Non Finalized Receipt Count by Delivery Number and Purchase Reference Number
   *
   * @param deliveryNumber
   * @param purchaseReferenceNumber
   * @return
   */
  @Query(
      "SELECT COUNT(*) FROM Receipt r WHERE DELIVERY_NUMBER = :deliveryNumber AND PURCHASE_REFERENCE_NUMBER = :purchaseReferenceNumber AND OSDR_MASTER = 1 AND (FINALIZE_TS IS NOT NULL OR FINALIZED_USER_ID IS NOT NULL)")
  int getFinalizedReceiptCountByDeliveryAndPoRefNumber(
      @Param("deliveryNumber") String deliveryNumber,
      @Param("purchaseReferenceNumber") String purchaseReferenceNumber);

  /**
   * find Master OSDR Receipts by Delivery Number, PurchaseReferenceNumber,
   * purchaseReferenceLineNumber
   *
   * @param deliveryNumber
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @param masterOsdr
   * @return
   */
  Receipt findByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndOsdrMaster(
      Long deliveryNumber,
      String purchaseReferenceNumber,
      Integer purchaseReferenceLineNumber,
      int masterOsdr);

  /**
   * Get all osdr master receipts for given delivery
   *
   * @param deliveryNumber
   * @param osdrMaster
   * @return list of receipts
   */
  List<Receipt> findByDeliveryNumberAndOsdrMaster(Long deliveryNumber, int osdrMaster);

  /**
   * Get all finalized osdr master receipts for given delivery
   *
   * @param deliveryNumber
   * @param osdrMaster
   * @return list of receipts
   */
  List<Receipt> findByDeliveryNumberAndOsdrMasterAndFinalizedUserIdIsNotNullAndFinalizeTsIsNotNull(
      Long deliveryNumber, int osdrMaster);

  /**
   * find Master OSDR Receipts by Delivery Number, PurchaseReferenceNumber
   *
   * @param deliveryNumber
   * @param purchaseReferenceNumber
   * @param masterOsdr
   * @return
   */
  List<Receipt> findByDeliveryNumberAndPurchaseReferenceNumberAndOsdrMaster(
      Long deliveryNumber, String purchaseReferenceNumber, int masterOsdr);

  /**
   * * It returns the latest receipt by delivery number
   *
   * @param deliveryNumber
   * @return
   */
  Receipt findFirstByDeliveryNumberOrderByCreateTsDesc(Long deliveryNumber);

  @Query(
      "SELECT SUM(r.quantity) from Receipt r WHERE r.createTs > :fromDate AND r.createTs < :toDate")
  Integer countByCasesReceived(Date fromDate, Date toDate);

  /**
   * Get the unFinalized purchase orders
   *
   * @param deliveryNumber
   * @param osdrMaster
   * @return list of receipts
   */
  List<Receipt> findByDeliveryNumberAndOsdrMasterAndFinalizeTsIsNull(
      Long deliveryNumber, int osdrMaster);

  @Query(
      "SELECT SUM(palletQty) from Receipt WHERE DELIVERY_NUMBER = :deliveryNumber AND PURCHASE_REFERENCE_NUMBER = :purchaseReferenceNumber")
  Integer getTotalReceivedPalletQtyByPOAndDeliveryNumber(
      Long deliveryNumber, String purchaseReferenceNumber);

  @Query(
      "SELECT ABS(SUM(r.quantity)) from Receipt r WHERE r.quantity < 0 AND  r.createTs > :fromDate AND r.createTs < :toDate")
  Integer countByVtrCasesReceived(Date fromDate, Date toDate);

  List<Receipt> findByIdGreaterThanEqual(Long lastDeleteId, Pageable pageable);

  Receipt findFirstByDeliveryNumberAndPurchaseReferenceNumberAndPalletQtyIsNull(
      Long deliveryNumber, String purchaseReferenceNumber);

  /**
   * Get number of POs for which receipts were created based on the time frame
   *
   * @param fromDate starting timestamp
   * @param toDate ending timestamp
   * @return count of pos
   */
  @Query(
      value =
          "SELECT COUNT(DISTINCT R.purchaseReferenceNumber) FROM Receipt R WHERE R.createTs > ?1 AND R.createTs < ?2")
  Integer countDistinctPosByCreateTsBetween(Date fromDate, Date toDate);

  /**
   * Used to get the number of deliveries worked upon in a time frame
   *
   * @param fromDate starting time stamp
   * @param toDate ending time stamp
   * @return count of deliveries
   */
  @Query(
      value =
          "SELECT COUNT(DISTINCT R.deliveryNumber) FROM Receipt R WHERE R.createTs > ?1 AND R.createTs < ?2")
  Integer countDistinctDeliveryNumberByCreateTsBetween(Date fromDate, Date toDate);

  /**
   * Get number of distinct users who worked in a given time frame
   *
   * @param fromDate starting time stamp
   * @param fromDate ending time stamp
   * @return count of occurrences of user id
   */
  @Query(
      value =
          "SELECT COUNT(DISTINCT R.createUserId) FROM Receipt R WHERE R.createTs > ?1 AND R.createTs < ?2")
  Integer countDistinctCreateUserByCreateTsBetween(Date fromDate, Date toDate);

  Receipt
      findByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndSsccNumberAndOsdrMaster(
          Long deliveryNumber,
          String purchaseReferenceNumber,
          int purchaseReferenceLineNumber,
          String sscc,
          int i);

  Receipt
      findFirstByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndOsdrMasterOrderByCreateTsAsc(
          Long deliveryNumber,
          String purchaseReferenceNumber,
          Integer purchaseReferenceLineNumber,
          int masterOsdr);

  void deleteByDeliveryNumber(Long deliveryNumber);

  List<Receipt> findByDeliveryNumberIn(List<Long> deliveryNumbers);

  List<Receipt> findByDeliveryNumberAndCreateTsGreaterThanEqual(Long deliveryNumber, Date osdrDate);

  @Query(
      value =
          "SELECT NEW com.walmart.move.nim.receiving.core.model.ReceiptForOsrdProcess(r.deliveryNumber, r.purchaseReferenceNumber) FROM Receipt r WHERE r.createTs >= :unloadingCompleteDate")
  List<ReceiptForOsrdProcess> fetchReceiptForOsrdProcess(
      @Param("unloadingCompleteDate") Date unloadingCompleteDate);

  @Query(
      "SELECT SUM(quantity) from Receipt WHERE DELIVERY_NUMBER = :deliveryNumber AND PURCHASE_REFERENCE_NUMBER = :purchaseReferenceNumber")
  Integer getTotalReceivedQuantityByPOAndDeliveryNumber(
      Long deliveryNumber, String purchaseReferenceNumber);

  @Query("SELECT SUM(quantity) from Receipt WHERE DELIVERY_NUMBER = :deliveryNumber")
  Integer getTotalReceivedQuantityByDeliveryNumber(Long deliveryNumber);

  @Query(
      value = "SELECT * FROM RECEIPT WHERE CREATE_TS >= :fromTime AND CREATE_TS <= :toTime",
      nativeQuery = true)
  List<Receipt> fetchPoReceipts(LocalDateTime fromTime, LocalDateTime toTime);
}
