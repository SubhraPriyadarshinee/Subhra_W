package com.walmart.move.nim.receiving.rc.repositories;

import com.walmart.move.nim.receiving.rc.entity.ContainerRLog;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContainerRLogRepository extends JpaRepository<ContainerRLog, Long> {

  /**
   * This method is responsible for fetching all the {@link ContainerRLog} by packageBarcodeValue
   *
   * @param packageBarcodeValue
   * @return
   */
  List<ContainerRLog> findByPackageBarCodeValue(String packageBarcodeValue);

  /**
   * This method is responsible for deleting {@link ContainerRLog} by packageBarcodeValue
   *
   * @param packageBarcodeValue
   */
  void deleteByPackageBarCodeValue(String packageBarcodeValue);

  /**
   * This method is responsible for fetching all the {@link ContainerRLog} those are having id
   * greater than @lastDeleteId
   *
   * @param lastDeleteId
   * @param pageable
   * @return
   */
  List<ContainerRLog> findByIdGreaterThanEqual(Long lastDeleteId, Pageable pageable);
  /**
   * This method is responsible for fetching the latest received container by @gtin
   *
   * @param gtin
   * @return
   */
  Optional<ContainerRLog> findFirstByGtinOrderByCreateTsDesc(String gtin);
  /**
   * This method is responsible for fetching the latest received container by @gtin
   * and @finalDispositionType
   *
   * @param gtin
   * @param finalDispositionType
   * @return
   */
  Optional<ContainerRLog> findFirstByGtinAndFinalDispositionTypeOrderByCreateTsDesc(
      String gtin, String finalDispositionType);

  /**
   * This method is responsible for fetching the received container by @trackingId
   *
   * @param trackingId
   * @return
   */
  Optional<ContainerRLog> findByTrackingId(String trackingId);

  /**
   * This method is responsible for fetching all the {@link ContainerRLog} by soNumber
   *
   * @param salesOrderNumber
   * @return
   */
  List<ContainerRLog> findBySalesOrderNumber(String salesOrderNumber);

  /**
   * This method is responsible for Updating Return Order data in {@link ContainerRLog} by
   * trackingId
   *
   * @param roNumber, roLineNumber,soLineNumber,trackingId
   */
  @Modifying
  @Query(
      value =
          "UPDATE CONTAINER_RLOG SET RETURN_ORDER_NUMBER = :roNumber, RETURN_ORDER_LINE_NUMBER = :roLineNumber, SALES_ORDER_LINE_NUMBER = :soLineNumber, MISSING_RETURN_RECEIVED = 1 "
              + " WHERE TRACKING_ID = :trackingId",
      nativeQuery = true)
  void updateReturnOrderData(
      @Param("roNumber") String roNumber,
      @Param("roLineNumber") int roLineNumber,
      @Param("soLineNumber") int soLineNumber,
      @Param("trackingId") String trackingId);

  /**
   * This method is responsible for fetching all the {@link ContainerRLog} by soNumber
   *
   * @param salesOrderNumber
   * @return
   */
  List<ContainerRLog> findBySalesOrderNumberAndSalesOrderLineNumber(
      String salesOrderNumber, Integer salesOrderLineNumber);
}
