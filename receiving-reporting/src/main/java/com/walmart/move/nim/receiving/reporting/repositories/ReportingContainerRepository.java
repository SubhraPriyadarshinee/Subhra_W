package com.walmart.move.nim.receiving.reporting.repositories;

import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.model.DcFinReconciledDate;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.ReceivingProductivityResponseDTO;
import com.walmart.move.nim.receiving.core.model.WFTResponse;
import java.util.Date;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** @author sks0013 ContainerModel JPA Repository fro reporting */
public interface ReportingContainerRepository extends JpaRepository<Container, Long> {

  /**
   * Method to fetch number of containers/labels printed based on given date
   *
   * @param fromDate start date after which we want the containers
   * @param toDate till date
   * @return
   */
  Integer countByCreateTsAfterAndCreateTsBefore(Date fromDate, Date toDate);

  /**
   * Method to fetch the number of containers VTRed based on a given date
   *
   * @param containerStatus
   * @param fromDate
   * @return
   */
  Integer countByContainerStatusAndCreateTsBetween(
      String containerStatus, Date fromDate, Date toDate);

  /**
   * Method to fetch the oldest record on a given deliverynumber
   *
   * @param deliveryNumber
   * @return
   */
  @Query(value = "SELECT MIN(createTs) from Container where  deliveryNumber= :deliveryNumber")
  Date getOldestContainer(@Param("deliveryNumber") Long deliveryNumber);

  /**
   * Method to fetch number of containers received using acl
   *
   * @param fromDate start date after which we want the containers
   * @param toDate till date
   * @return
   */
  Integer countByCreateTsBetweenAndOnConveyorIsTrueAndContainerExceptionIsNull(
      Date fromDate, Date toDate);

  // count of ACC manual receiving cases
  @Query(
      value =
          "SELECT COUNT(c.trackingId) FROM Container c WHERE c.onConveyor = 1 AND c.trackingId <> c.messageId AND c.createTs > :fromDate AND c.createTs < :toDate")
  Integer countOfManualReceivingCases(Date fromDate, Date toDate);

  // count of sstk pallets and cases
  @Query(
      value =
          "SELECT new com.walmart.move.nim.receiving.core.model.Pair(COUNT(c.trackingId) as key, SUM(ci.quantity/ ci.vnpkQty) as value) FROM Container c, ContainerItem ci WHERE c.trackingId = ci.trackingId AND ci.inboundChannelMethod = 'SSTKU' AND c.createTs > :fromDate AND c.createTs < :toDate")
  Pair<Integer, Integer> countOfSstkPalletsAndCases(Date fromDate, Date toDate);

  // count of Da Non con pallets and cases
  @Query(
      value =
          "SELECT new com.walmart.move.nim.receiving.core.model.Pair(COUNT(c.trackingId) as key, SUM(ci.quantity/ ci.vnpkQty) as value) FROM Container c, ContainerItem ci WHERE c.trackingId = ci.trackingId AND ci.inboundChannelMethod IN ('CROSSU', 'CROSSMU') AND c.isConveyable = 0 AND c.createTs > :fromDate AND c.createTs < :toDate")
  Pair<Integer, Integer> countOfDaNonConPalletsAndCases(Date fromDate, Date toDate);

  // count of Da Con pallets
  @Query(
      value =
          "select count(res.PARENT_TRACKING_ID) from "
              + "(select count(*) count_p, PARENT_TRACKING_ID from CONTAINER c, CONTAINER_ITEM ci "
              + "where c.TRACKING_ID = ci.TRACKING_ID "
              + "and CREATE_TS > ?1 and CREATE_TS < ?2 "
              + "and c.facilityNum = ?3 and c.facilityCountryCode = ?4 "
              + "and ci.INBOUND_CHANNEL_METHOD IN ('CROSSU', 'CROSSMU') and IS_CONVEYABLE = 1 "
              + "group by PARENT_TRACKING_ID) as res",
      nativeQuery = true)
  Integer countOfDaConPallets(
      Date fromDate, Date toDate, Integer facilityNum, String facilityCountryCode);

  // count of PoCon pallets and cases
  @Query(
      value =
          "SELECT new com.walmart.move.nim.receiving.core.model.Pair(COUNT(c.trackingId) as key, SUM(ci.quantity/ ci.vnpkQty) as value) FROM Container c, ContainerItem ci WHERE c.trackingId = ci.trackingId AND ci.inboundChannelMethod = 'POCON' AND c.createTs > :fromDate AND c.createTs < :toDate")
  Pair<Integer, Integer> countOfPoConPalletsAndCases(Date fromDate, Date toDate);

  // count of DSDC pallets and cases
  @Query(
      value =
          "SELECT new com.walmart.move.nim.receiving.core.model.Pair(COUNT(c.trackingId) as key, SUM(ci.quantity/ ci.vnpkQty) as value) FROM Container c, ContainerItem ci WHERE c.trackingId = ci.trackingId AND ci.inboundChannelMethod = 'DSDC' AND c.createTs > :fromDate AND c.createTs < :toDate")
  Pair<Integer, Integer> countOfDsdcPalletsAndCases(Date fromDate, Date toDate);

  // count of Da Con cases
  Integer countByContainerTypeAndCreateTsBetween(String containerType, Date fromDate, Date toDate);

  // count of PBYL pallets and cases
  @Query(
      value =
          "SELECT new com.walmart.move.nim.receiving.core.model.Pair(COUNT(c.trackingId) as key, SUM(ci.quantity/ ci.vnpkQty) as value) FROM Container c, ContainerItem ci WHERE c.trackingId = ci.trackingId AND c.inventoryStatus = 'AVAILABLE' AND c.isConveyable = 1 AND c.createTs > :fromDate AND c.createTs < :toDate")
  Pair<Integer, Integer> countOfPbylPalletsAndCases(Date fromDate, Date toDate);

  // count of items
  @Query(
      value =
          "SELECT COUNT(DISTINCT ci.itemNumber) FROM Container c, ContainerItem ci WHERE c.trackingId = ci.trackingId  AND c.createTs > :fromDate AND c.createTs < :toDate")
  Integer countOfItems(Date fromDate, Date toDate);

  // count of cases received after systematic reopen
  @Query(
      value =
          "SELECT COUNT(c.trackingId) FROM Container c, DeliveryMetaData dm WHERE c.deliveryNumber = dm.deliveryNumber AND c.onConveyor = 1 AND c.containerException IS NULL AND c.createTs > dm.createdDate AND dm.createdDate > :fromDate AND dm.createdDate < :toDate")
  Integer countOfCasesReceivedAfterSysReopen(Date fromDate, Date toDate);

  // Map of delivery, oldest create ts
  @Query(
      value =
          "SELECT c.deliveryNumber, MIN(c.createTs) FROM Container c WHERE c.deliveryNumber IN :deliveryList GROUP BY c.deliveryNumber")
  List<Object[]> findOldestContainerTsByDelivery(List<Long> deliveryList);

  // average count of pallets in a delivery
  @Query(
      value =
          "SELECT AVG(a.palletCount) FROM (SELECT COUNT(*) AS palletCount FROM CONTAINER c WHERE c.CONTAINER_TYPE = 'PALLET' AND c.CONTAINER_EXCEPTION_CODE IS NULL AND c.CREATE_TS > ?1 AND c.CREATE_TS < ?2 AND c.facilityNum = ?3 AND c.facilityCountryCode = ?4 GROUP BY c.DELIVERY_NUMBER) a",
      nativeQuery = true)
  Double averagePalletCountPerDelivery(
      Date fromDate, Date toDate, Integer facilityNum, String facilityCountryCode);

  // get dcFin reconciled data by time range
  @Query(
      value =
          "select new com.walmart.move.nim.receiving.core.model.DcFinReconciledDate(c.deliveryNumber,ci.purchaseReferenceNumber,ci.purchaseReferenceLineNumber,c.createTs,ci.quantity,ci.quantityUOM ,c.createUser,c.trackingId,ci.itemNumber, ci.promoBuyInd, ci.weightFormatTypeCode, ci.vnpkWgtQty, ci.vnpkWgtUom) from Container c, ContainerItem ci WHERE c.containerException is null AND c.createTs > ?1 AND c.createTs < ?2 AND ci.purchaseReferenceLineNumber IS NOT NULL AND c.trackingId = ci.trackingId AND c.facilityCountryCode= ?3  AND c.facilityNum= ?4")
  List<DcFinReconciledDate> reconciledDataSummaryByTime(
      Date fromDate, Date toDate, String facilityCountryCode, Integer facilityNum);

  @Query(
      value =
          "SELECT new com.walmart.move.nim.receiving.core.model.WFTResponse(c.createUser, ci.inboundChannelMethod as activityName, SUM(ci.quantity) as receivedQty) FROM Container c, ContainerItem ci WHERE c.trackingId = ci.trackingId AND ci.inboundChannelMethod= ?1 AND c.createTs > ?2 AND c.createTs < ?3  AND c.facilityCountryCode= ?4  AND c.facilityNum= ?5 GROUP BY c.createUser, ci.inboundChannelMethod")
  List<WFTResponse> getReceivedQtyAgainstUserNameGivenActivityName(
      String activityName,
      Date fromDate,
      Date toDate,
      String facilityCountryCode,
      Integer facilityNum);

  @Query(
      value =
          "SELECT new com.walmart.move.nim.receiving.core.model.WFTResponse(ci.inboundChannelMethod, SUM(ci.quantity) as receivedQty) FROM Container c, ContainerItem ci WHERE c.trackingId = ci.trackingId AND c.createTs > ?1 AND c.createTs < ?2  AND c.facilityCountryCode= ?3  AND c.facilityNum= ?4 GROUP BY ci.inboundChannelMethod")
  List<WFTResponse> getReceivedQtyAgainstActivityNameByTime(
      Date fromDate, Date toDate, String facilityCountryCode, Integer facilityNum);

  /**
   * Method to fetch container details based on tracker Id
   *
   * @param trackerId
   * @return Container
   */
  Container findByTrackingId(String trackerId);

  List<Container> findByDeliveryNumberAndCreateUserAndCreateTsBetween(
      Long deliveryNumber, String user, Date fromDate, Date toDate);

  List<Container> findByDeliveryNumberAndCreateUserAndCreateTsBefore(
      Long deliveryNumber, String user, Date toDate);

  @Query(nativeQuery = true)
  Page<ReceivingProductivityResponseDTO> getReceivingProductivityForOneUser(
          @Param("facilityNum") Integer facilityNum,
          @Param("facilityCountryCode") String facilityCountryCode,
          @Param("userId") String userId,
          @Param("fromDate") String fromDate,
          @Param("toDate") String toDate,
          Pageable reportPage
          );

  @Query(nativeQuery = true)
  Page<ReceivingProductivityResponseDTO> getReceivingProductivityForAllUsers(
          @Param("facilityNum") Integer facilityNum,
          @Param("facilityCountryCode") String facilityCountryCode,
          @Param("fromDate") String fromDate,
          @Param("toDate") String toDate,
          Pageable reportPage
          );

}
