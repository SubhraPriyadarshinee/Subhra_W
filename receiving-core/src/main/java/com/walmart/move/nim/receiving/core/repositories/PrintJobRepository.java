package com.walmart.move.nim.receiving.core.repositories;

import com.walmart.move.nim.receiving.core.entity.PrintJob;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PrintJobRepository extends JpaRepository<PrintJob, Long> {
  /**
   * This method will fetch print jobs based on delivery number
   *
   * @param deliveryNumber
   * @return
   */
  List<PrintJob> findByDeliveryNumber(long deliveryNumber);

  /**
   * This method will fetch print jobs based on instruction number
   *
   * @param instructionId
   * @return
   */
  List<PrintJob> findByInstructionId(Long instructionId);

  List<PrintJob> findByIdGreaterThanEqual(Long lastDeleteId, Pageable pageable);

  @Query(
      value =
          "SELECT TOP(?1) * FROM PrintJob WHERE DELIVERY_NUMBER = ?2 AND facilityNum = ?3 AND facilityCountryCode = ?4 ORDER BY CREATE_TS DESC",
      nativeQuery = true)
  List<PrintJob> getRecentlyPrintedLabelsByDeliveryNumber(
      Integer labelCount, Long deliveryNumber, Integer facilityNum, String facilityCountryCode);

  @Query(
      value =
          "SELECT TOP(?1) * FROM PrintJob WHERE DELIVERY_NUMBER = ?2 AND CREATE_USER_ID = ?3 AND facilityNum = ?4 AND facilityCountryCode = ?5 ORDER BY CREATE_TS DESC",
      nativeQuery = true)
  List<PrintJob> findRecentlyPrintedLabelsByDeliveryNumberAndUserId(
      Integer labelCount,
      Long deliveryNumber,
      String userId,
      Integer facilityNum,
      String facilityCountryCode);

  void deleteByDeliveryNumber(Long deliveryNumber);
}
