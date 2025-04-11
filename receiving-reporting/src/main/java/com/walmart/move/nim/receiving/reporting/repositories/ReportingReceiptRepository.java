package com.walmart.move.nim.receiving.reporting.repositories;

import com.walmart.move.nim.receiving.core.entity.Receipt;
import java.util.Date;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** @author sks0013 Receipt JPA repository for reporting */
public interface ReportingReceiptRepository extends JpaRepository<Receipt, Long> {

  @Query(
      "SELECT SUM(r.quantity) from Receipt r WHERE r.createTs > :fromDate AND r.createTs < :toDate")
  Integer countByCasesReceived(Date fromDate, Date toDate);

  @Query(
      "SELECT ABS(SUM(r.quantity)) from Receipt r WHERE r.quantity < 0 AND  r.createTs > :fromDate AND r.createTs < :toDate")
  Integer countByVtrCasesReceived(Date fromDate, Date toDate);

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
}
