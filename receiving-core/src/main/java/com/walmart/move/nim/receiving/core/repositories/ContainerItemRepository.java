package com.walmart.move.nim.receiving.core.repositories;

import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * ContainerItem Repository
 *
 * @author pcr000m
 */
@Repository
public interface ContainerItemRepository extends JpaRepository<ContainerItem, Long> {

  /**
   * This method is to fetch ContainerItem Record
   *
   * @param trackerId
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @return
   */
  ContainerItem findByTrackingIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
      String trackerId, String purchaseReferenceNumber, Integer purchaseReferenceLineNumber);

  /**
   * This method is to fetch ContainerItem Record
   *
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @return
   */
  List<ContainerItem> findByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
      String purchaseReferenceNumber, Integer purchaseReferenceLineNumber);

  /**
   * This method is to fetch container Item details based on trackingId
   *
   * @param trackingId
   * @return
   */
  List<ContainerItem> findByTrackingId(String trackingId);

  List<ContainerItem> findByTrackingIdIn(List<String> trackingIds);

  /**
   * This method will delete container items by tracking id list
   *
   * @param trackingIdList
   */
  void deleteByTrackingIdIn(List<String> trackingIdList);

  /**
   * This method is responsible for getting list
   *
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @return
   */
  @Query(
      value =
          "SELECT ci.trackingId FROM ContainerItem ci WHERE ci.purchaseReferenceNumber = :purchaseReferenceNumber AND ci.purchaseReferenceLineNumber = :purchaseReferenceLineNumber")
  List<String> getTrackingIdByPoAndPoLine(
      String purchaseReferenceNumber, Integer purchaseReferenceLineNumber);

  @Query(
      value =
          "SELECT SUM(quantity) from ContainerItem where trackingId IN (SELECT trackingId FROM Container where parentTrackingId = :trackingId)")
  Integer receivedContainerQuantityByTrackingId(@Param("trackingId") String trackingId);

  @Query(
      value =
          "SELECT SUM(quantity) from ContainerItem where trackingId IN (SELECT trackingId FROM Container where ssccNumber = :ssccNumber AND containerStatus != :containerStatus) AND facilityNum = :facilityNum AND facilityCountryCode = :facilityCountryCode")
  Integer receivedContainerQuantityBySSCCAndStatus(
      @Param("ssccNumber") String ssccNumber,
      String containerStatus,
      Integer facilityNum,
      String facilityCountryCode);

  @Query(
      value =
          "SELECT SUM(quantity) from ContainerItem where trackingId IN (SELECT trackingId FROM Container where ssccNumber = :ssccNumber AND containerStatus IS NULL) AND facilityNum = :facilityNum AND facilityCountryCode = :facilityCountryCode")
  Integer receivedContainerQuantityBySSCCAndStatusIsNull(
      @Param("ssccNumber") String ssccNumber, Integer facilityNum, String facilityCountryCode);

  @Query(
      value =
          "SELECT itemNumber from ContainerItem where trackingId IN (SELECT trackingId FROM Container where ssccNumber = :ssccNumber AND containerStatus != :containerStatus) AND facilityNum = :facilityNum AND facilityCountryCode = :facilityCountryCode")
  Optional<List<Long>> receivedContainerDetailsBySSCC(
      @Param("ssccNumber") String ssccNumber,
      String containerStatus,
      Integer facilityNum,
      String facilityCountryCode);

  @Query(
      value =
          "SELECT * FROM (SELECT *, ROW_NUMBER() OVER(PARTITION BY CI.ITEM_NUMBER ORDER BY CI.CONTAINER_ITEM_ID) as rowNumber "
              + "FROM CONTAINER_ITEM CI WHERE CI.ITEM_UPC = :upcNumber "
              + "AND CI.facilityNum = :facilityNum AND CI.facilityCountryCode = :facilityCountryCode) as LATEST_CONTAINER_ITEM "
              + "WHERE LATEST_CONTAINER_ITEM.rowNumber = 1",
      nativeQuery = true)
  List<ContainerItem> getContainerItemMetaDataByUpcNumber(
      @Param("upcNumber") String upcNumber,
      @Param("facilityNum") Integer facilityNum,
      @Param("facilityCountryCode") String facilityCountryCode);

  /**
   * Get the first containerItem
   *
   * @param itemNumber
   * @return ContainerItem
   */
  Optional<ContainerItem> findFirstByItemNumberOrderByIdDesc(Long itemNumber);

  /**
   * Get Top containerItem by itemNumber and itemUpc
   *
   * @param itemNumber
   * @param itemUPC
   * @return ContainerItem
   */
  Optional<ContainerItem> findTopByItemNumberAndItemUPCOrderByIdDesc(
      Long itemNumber, String itemUPC);

  Optional<ContainerItem> findTopByInvoiceNumberOrderByInvoiceLineNumberDesc(String invoiceNumber);

  List<ContainerItem> findByGtinAndFacilityCountryCodeAndFacilityNum(
      String gtin, String facilityCountryCode, Integer facilityNum);

  Optional<ContainerItem> findTopByFacilityNumAndFacilityCountryCodeAndGtinAndSerial(Integer facilityNum, String facilityCountryCode, String gtin, String serial);

  Optional<ContainerItem> findTopByGtinAndSerial(String gtin, String serial);

}
