package com.walmart.move.nim.receiving.core.repositories;

import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.printlabel.GdcReprintLabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.ReprintLabelData;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** @author pcr000m ContainerModel JPA Repository */
@Repository
public interface ContainerRepository extends JpaRepository<Container, Long> {
  /**
   * Method to check if container exists based on tracker Id
   *
   * @param trackingId
   * @return count
   */
  boolean existsByTrackingId(String trackingId);

  /**
   * Method to fetch container details based on tracker Id
   *
   * @param trackerId
   * @return Container
   */
  Container findByTrackingId(String trackerId);

  /**
   * Method to fetch all the container details based on tracker Ids
   *
   * @param trackingIdList
   * @return Set<Container>
   */
  Set<Container> findByTrackingIdIn(List<String> trackingIdList);

  /**
   * Method to fetch all child Containers
   *
   * @param trackingId
   * @return Set<Container>
   */
  Set<Container> findAllByParentTrackingId(String trackingId);

  /**
   * Method to fetch container based on deliveryNumber
   *
   * @param deliveryNumber
   * @return
   */
  List<Container> findByDeliveryNumber(Long deliveryNumber);

  /**
   * Method to fetch container based on instruction id
   *
   * @param instructionId
   * @return
   */
  List<Container> findByInstructionId(Long instructionId);

  /**
   * Method to fetch container based on given date in past It returns containers created before N
   * mins
   *
   * @param fromDate date after which we want the containers
   * @return
   */
  List<Container> findByCreateTsGreaterThanEqual(Date fromDate);

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

  // TODO Needs to debug further if tenant needs to be required or not
  @Query(
      "SELECT new java.lang.Long(ci.itemNumber) FROM Container c JOIN ContainerItem ci ON c.trackingId = ci.trackingId "
          + "WHERE (ci.itemUPC = :upc or ci.caseUPC = :upc) AND ci.facilityCountryCode = :facilityCountryCode AND ci.facilityNum = :facilityNum "
          + "ORDER BY c.createTs DESC")
  List<Long> findLatestItemByUPC(String upc, String facilityCountryCode, Integer facilityNum);

  @Query(
      value =
          "SELECT new com.walmart.move.nim.receiving.core.model.ItemInfoResponse(itemNumber,baseDivisionCode) FROM ContainerItem "
              + "WHERE (itemUPC = :upc or caseUPC = :upc) AND facilityCountryCode = :facilityCountryCode AND facilityNum = :facilityNum "
          + "ORDER BY createTs DESC")
  List<ItemInfoResponse> findItemBaseDivCodesByUPC(
      String upc, String facilityCountryCode, Integer facilityNum);

  /**
   * This method is responsible for deleting containers by tracking id list
   *
   * @param trackingIdList
   */
  void deleteByTrackingIdIn(List<String> trackingIdList);

  /**
   * This method is responsible of returning distinct parent tracking id by po and po line
   *
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @return
   */
  @Query(
      value =
          "SELECT DISTINCT c.parentTrackingId FROM Container c INNER JOIN ContainerItem ci ON c.trackingId = ci.trackingId WHERE ci.purchaseReferenceNumber = :purchaseReferenceNumber AND ci.purchaseReferenceLineNumber = :purchaseReferenceLineNumber AND c.parentTrackingId IS NOT NULL")
  List<String> getParentTrackingIdByPoAndPoLine(
      String purchaseReferenceNumber, Integer purchaseReferenceLineNumber);

  Page<Container> findByParentTrackingIdIsNull(Pageable pageable);

  List<Container> findAllByParentTrackingIdAndContainerStatus(
      String parentTrackingId, String containerStatus);

  List<Container> findByIdGreaterThanEqual(Long lastDeleteId, Pageable pageable);

  @Query(
      value =
          "SELECT new com.walmart.move.nim.receiving.core.model.printlabel.ReprintLabelData(c.trackingId, ci.description, c.lastChangedUser, c.completeTs, c.containerException, c.parentTrackingId, c.ssccNumber) FROM Container c LEFT JOIN ContainerItem ci ON c.trackingId = ci.trackingId WHERE c.facilityNum = :facilityNum AND c.facilityCountryCode = :facilityCode AND c.deliveryNumber = :deliveryNumber AND c.lastChangedUser = :userId AND c.parentTrackingId is NULL AND c.publishTs is not NULL AND (c.containerStatus is NULL OR c.containerStatus != :containerStatus) AND (c.containerException is NULL OR c.containerException not in :containerExceptions) order by c.completeTs desc")
  List<ReprintLabelData> getDataForPrintingLabelByDeliveryNumberByUserId(
      Long deliveryNumber,
      String userId,
      List<String> containerExceptions,
      Integer facilityNum,
      String facilityCode,
      String containerStatus,
      Pageable pageable);

  @Query(
      value =
          "SELECT new com.walmart.move.nim.receiving.core.model.printlabel.ReprintLabelData(c.trackingId, ci.description, c.lastChangedUser, c.completeTs, c.containerException, c.parentTrackingId, c.ssccNumber) FROM Container c LEFT JOIN ContainerItem ci ON c.trackingId = ci.trackingId WHERE c.facilityNum = :facilityNum AND c.facilityCountryCode = :facilityCode AND c.deliveryNumber = :deliveryNumber AND c.parentTrackingId is NULL AND c.publishTs is not NULL AND (c.containerStatus is NULL OR c.containerStatus != :containerStatus) AND (c.containerException is NULL OR c.containerException not in :containerExceptions) order by c.completeTs desc")
  List<ReprintLabelData> getDataForPrintingLabelByDeliveryNumber(
      Long deliveryNumber,
      List<String> containerExceptions,
      Integer facilityNum,
      String facilityCode,
      String containerStatus,
      Pageable pageable);

  @Query(
      value =
          "SELECT DISTINCT new com.walmart.move.nim.receiving.core.model.printlabel.ReprintLabelData(c.trackingId, ci.description, c.createUser, c.createTs, c.containerException, c.parentTrackingId, c.ssccNumber) FROM Container c LEFT JOIN ContainerItem ci ON c.trackingId = ci.trackingId WHERE c.facilityNum = :facilityNum AND c.facilityCountryCode = :facilityCountryCode AND c.deliveryNumber = :deliveryNumber AND c.createUser = :userId AND c.labelId is not null AND (c.containerException is NULL OR c.containerException is :dockTagExceptionCode) order by c.createTs desc")
  List<ReprintLabelData> getLabelDataByDeliveryNumberByUserId(
      Long deliveryNumber,
      String userId,
      String dockTagExceptionCode,
      Integer facilityNum,
      String facilityCountryCode,
      Pageable pageable);

  @Query(
      value =
          "SELECT DISTINCT new com.walmart.move.nim.receiving.core.model.printlabel.ReprintLabelData(c.trackingId, ci.description, c.createUser, c.createTs, c.containerException, c.parentTrackingId, c.ssccNumber) FROM Container c LEFT JOIN ContainerItem ci ON c.trackingId = ci.trackingId WHERE c.facilityNum = :facilityNum AND c.facilityCountryCode = :facilityCountryCode AND c.deliveryNumber = :deliveryNumber AND c.labelId is not null AND (c.containerException is NULL OR c.containerException is :dockTagExceptionCode) order by c.createTs desc")
  List<ReprintLabelData> getLabelDataByDeliveryNumber(
      Long deliveryNumber,
      String dockTagExceptionCode,
      Integer facilityNum,
      String facilityCountryCode,
      Pageable pageable);

  @Query(
      value =
          "SELECT new com.walmart.move.nim.receiving.core.model.printlabel.GdcReprintLabelData(c.trackingId, ci.description, c.lastChangedUser, c.lastChangedTs, c.containerException, ci.quantity, ci.vnpkQty, ci.whpkQty) FROM Container c LEFT JOIN ContainerItem ci ON c.trackingId = ci.trackingId and ci.facilityNum = c.facilityNum  WHERE c.facilityNum = :facilityNum AND c.facilityCountryCode = :facilityCode AND c.deliveryNumber = :deliveryNumber AND c.lastChangedUser = :userId AND c.parentTrackingId is NULL AND c.publishTs is not NULL AND (c.containerStatus is NULL OR c.containerStatus != :containerStatus) AND (c.containerException is NULL OR c.containerException not in :containerExceptions) order by c.lastChangedTs desc")
  List<GdcReprintLabelData> getGdcDataForPrintingLabelByDeliveryNumberByUserId(
      Long deliveryNumber,
      String userId,
      List<String> containerExceptions,
      Integer facilityNum,
      String facilityCode,
      String containerStatus,
      Pageable pageable);

  @Query(
      value =
          "SELECT new com.walmart.move.nim.receiving.core.model.printlabel.GdcReprintLabelData(c.trackingId, ci.description, c.lastChangedUser, c.lastChangedTs, c.containerException, ci.quantity, ci.vnpkQty, ci.whpkQty) FROM Container c LEFT JOIN ContainerItem ci ON c.trackingId = ci.trackingId and ci.facilityNum = c.facilityNum WHERE c.facilityNum = :facilityNum AND c.facilityCountryCode = :facilityCode AND c.deliveryNumber = :deliveryNumber AND c.parentTrackingId is NULL AND c.publishTs is not NULL AND (c.containerStatus is NULL OR c.containerStatus != :containerStatus) AND (c.containerException is NULL OR c.containerException not in :containerExceptions) order by c.lastChangedTs desc")
  List<GdcReprintLabelData> getGdcDataForPrintingLabelByDeliveryNumber(
      Long deliveryNumber,
      List<String> containerExceptions,
      Integer facilityNum,
      String facilityCode,
      String containerStatus,
      Pageable pageable);

  /**
   * Method to fetch container based by parentTrackingId and status
   *
   * @param parentTrackingIds
   * @param containerStatusList
   * @return
   */
  List<Container> findByParentTrackingIdInAndContainerStatusIn(
      List<String> parentTrackingIds, List<String> containerStatusList);

  /**
   * Method to fetch instructionIds based on container trackingIds
   *
   * @param trackingIds
   * @param facilityCountryCode
   * @param facilityNum
   * @return instructionIds
   */
  @Query(
      value =
          "SELECT DISTINCT c.instructionId FROM Container c WHERE c.trackingId in (:trackingIds) and c.facilityNum = :facilityNum and c.facilityCountryCode = :facilityCountryCode")
  List<Long> getInstructionIdsByTrackingIds(
      List<String> trackingIds, Integer facilityNum, String facilityCountryCode);

  @Query(
      value =
          "SELECT DISTINCT new com.walmart.move.nim.receiving.core.model.InstructionIdAndTrackingIdPair(c.instructionId,c.trackingId) FROM Container c WHERE c.trackingId in (:trackingIds) and c.facilityNum = :facilityNum and c.facilityCountryCode = :facilityCountryCode and c.instructionId  is not null")
  List<InstructionIdAndTrackingIdPair> getInstructionIdsObjByTrackingIds(
      List<String> trackingIds, Integer facilityNum, String facilityCountryCode);

  @Query(
      value =
          "SELECT new com.walmart.move.nim.receiving.core.model.LabelIdAndTrackingIdPair(trackingId, labelId) from Container WHERE trackingId in :trackingIds and labelId is not null")
  List<LabelIdAndTrackingIdPair> getLabelIdsByTrackingIdsWhereLabelIdNotNull(
      Set<String> trackingIds);

  @Query(
      value =
          "SELECT new com.walmart.move.nim.receiving.core.model.ContainerMetaDataForCaseLabel(c.trackingId, c.destination, ci.itemNumber, ci.gtin, ci.description, ci.secondaryDescription, ci.vnpkQty, ci.whpkQty, ci.poDeptNumber, ci.purchaseReferenceNumber, ci.purchaseReferenceLineNumber, ci.inboundChannelMethod, ci.containerItemMiscInfo, ci.vendorNumber, c.createUser, c.deliveryNumber, c.location, c.facilityNum) FROM Container c INNER JOIN ContainerItem ci ON c.trackingId=ci.trackingId where c.trackingId in :trackingIds and c.facilityNum = :facilityNum and c.facilityCountryCode = :facilityCountryCode")
  List<ContainerMetaDataForCaseLabel> getContainerMetaDataForCaseLabelByTrackingIds(
      List<String> trackingIds, Integer facilityNum, String facilityCountryCode);

  @Query(
      value =
          "SELECT new com.walmart.move.nim.receiving.core.model.ContainerMetaDataForPalletLabel(trackingId, deliveryNumber, location, destination, createUser) FROM Container where trackingId in :trackingIds")
  List<ContainerMetaDataForPalletLabel> getContainerMetaDataForpalletLabelByTrackingIds(
      List<String> trackingIds);

  @Query(
      value =
          "SELECT new com.walmart.move.nim.receiving.core.model.ContainerMetaDataForPalletLabel(c.parentTrackingId, ci.itemNumber, ci.gtin, ci.description, count(c.trackingId)) FROM Container c INNER JOIN ContainerItem ci ON c.trackingId=ci.trackingId where c.parentTrackingId in :trackingIds and c.facilityNum = :facilityNum and c.facilityCountryCode = :facilityCountryCode group by c.parentTrackingId, ci.itemNumber, ci.gtin, ci.description")
  List<ContainerMetaDataForPalletLabel> getContainerItemMetaDataForPalletLabelByTrackingIds(
      List<String> trackingIds, Integer facilityNum, String facilityCountryCode);

  @Query(
      value =
          "SELECT new com.walmart.move.nim.receiving.core.model.ContainerMetaDataForPalletLabel(c.trackingId, c.destination, ci.itemNumber, ci.gtin, ci.description, c.createUser,  c.deliveryNumber,  c.location, ci.quantity, ci.vnpkQty, (SELECT i.move FROM Instruction i where i.id = c.instructionId), ci.lotNumber, ci.expiryDate) FROM Container c INNER JOIN ContainerItem ci ON c.trackingId=ci.trackingId where c.trackingId in :trackingIds and c.facilityNum = :facilityNum and c.facilityCountryCode = :facilityCountryCode")
  List<ContainerMetaDataForPalletLabel>
      getContainerAndContainerItemMetaDataForPalletLabelByTrackingIds(
          List<String> trackingIds, Integer facilityNum, String facilityCountryCode);

  @Query(
      value =
          "SELECT new com.walmart.move.nim.receiving.core.model.ContainerMetaDataForNonNationalPoLabel(c.trackingId, c.destination, ci.outboundChannelMethod, c.createUser, c.deliveryNumber, c.location, c.containerMiscInfo, ci.purchaseReferenceNumber, ci.quantity) FROM Container c INNER JOIN ContainerItem ci ON c.trackingId=ci.trackingId where c.trackingId in :trackingIds and c.facilityNum = :facilityNum and c.facilityCountryCode = :facilityCountryCode")
  List<ContainerMetaDataForNonNationalPoLabel>
      getContainerItemMetaDataForNonNationalLabelByTrackingIds(
          List<String> trackingIds, Integer facilityNum, String facilityCountryCode);

  @Query(
      value =
          "SELECT new com.walmart.move.nim.receiving.core.model.ContainerMetaDataForDockTagLabel(trackingId, createUser, deliveryNumber, location, createTs) FROM Container where trackingId in :trackingIds")
  List<ContainerMetaDataForDockTagLabel> getContainerItemMetaDataForDockTagLabelByTrackingIds(
      List<String> trackingIds);

  @Query(
      "SELECT new com.walmart.move.nim.receiving.core.model.ContainerSummary(c.trackingId, c.parentTrackingId, "
          + "ci.serial, ci.lotNumber, ci.quantity, ci.quantityUOM) "
          + "FROM Container c JOIN ContainerItem ci ON c.trackingId = ci.trackingId "
          + "WHERE c.instructionId = :instructionId AND ci.serial = :serial AND ci.lotNumber = :lotNumber "
          + "AND (c.containerStatus is NULL OR c.containerStatus != 'backout')")
  List<ContainerSummary> findByInstructionIdLotSerial(
      long instructionId, String serial, String lotNumber);

  Set<Container> findByParentTrackingIdAndTrackingIdIn(
      String parentTrackingId, List<String> trackingIdList);

  List<Container> findByDeliveryNumberAndShipmentIdIsNotNull(Long deliveryNumber);

  List<Container> findAllBySsccNumberAndInventoryStatus(String ssccNumber, String inventoryStatus);

  Container findTopBySsccNumberAndDeliveryNumber(String ssccNumber, Long deliveryNumber);

  List<Container> findBySsccNumberAndDeliveryNumberIn(String sscc, List<Long> deliveries);

  List<Container> findAllBySsccNumber(String sscc);

  List<Container> findAllBySsccNumberIn(List<String> sscc);

  /**
   * This method is returns existing parent tracking id from given pack ids or parent tracking ids
   *
   * @param parentTrackingIds parent tracking/pack id list
   * @return existing parent tracking ids
   */
  @Query(
      value =
          "SELECT c.parentTrackingId FROM Container c where c.parentTrackingId in :parentTrackingIds")
  List<String> getExistingParentTrackingIds(List<String> parentTrackingIds);

  List<Container> findAllByDeliveryNumberAndSsccNumberIn(Long deliveryNumber, List<String> sscc);

  List<Container> findByDeliveryNumberIn(List<Long> deliveryNumber);

  @Query(
      "SELECT new com.walmart.move.nim.receiving.core.model.PalletHistory(c.trackingId,ci.itemNumber,(ci.quantity / ci.vnpkQty) as quantity, "
          + "  ci.rotateDate as rotateDate, c.createTs as createdTimeStamp, c.destination) "
          + "FROM Container c , ContainerItem ci  "
          + "WHERE  c.trackingId = ci.trackingId "
          + "AND  c.facilityNum = ci.facilityNum "
          + "AND c.deliveryNumber = :deliveryNumber "
          + "AND  (c.containerStatus is NULL OR c.containerStatus != 'backout') order by c.createTs desc")
  List<PalletHistory> findByOnlyDeliveryNumber(Long deliveryNumber);

  @Query(
      "SELECT new com.walmart.move.nim.receiving.core.model.PalletHistory(c.trackingId,ci.itemNumber,(ci.quantity / ci.vnpkQty) as quantity, "
          + "  ci.rotateDate as rotateDate, c.createTs as createdTimeStamp, c.destination) "
          + "FROM Container c , ContainerItem ci  "
          + "WHERE  c.trackingId = ci.trackingId "
          + "AND  c.facilityNum = ci.facilityNum "
          + "AND c.deliveryNumber = :deliveryNumber "
          + "AND ci.purchaseReferenceNumber = :po "
          + "AND ci.purchaseReferenceLineNumber = :poLine "
          + "AND  (c.containerStatus is NULL OR c.containerStatus != 'backout') order by c.createTs desc")
  List<PalletHistory> findByDeliveryNumberWithPO(Long deliveryNumber, String po, Integer poLine);

  @Query(
      value =
          "SELECT distinct(c.ssccNumber) FROM Container c where c.deliveryNumber = :deliveryNumber")
  Set<String> findSsccByDelivery(Long deliveryNumber);

  @Query(
      value =
          "select cnt from Container cnt "
              + "where cnt.facilityNum = :facilityNumber "
              + "and cnt.parentTrackingId is null "
              + "and cnt.containerStatus = :containerStatus "
              + "and cnt.inventoryStatus = :inventoryStatus "
              + "and cnt.facilityCountryCode = :facilityCountryCode "
              + "and json_value(cnt.destination, '$.buNumber') = :facilityNumber "
              + "order by cnt.id desc ")
  List<Container> findBreakPackReceiveContainer(
      Integer facilityNumber,
      String containerStatus,
      String inventoryStatus,
      String facilityCountryCode,
      Pageable pageable);

  @Query(
      value =
          "select cnt from Container cnt where cnt.facilityNum IN(:facilityNumbers) and cnt.parentTrackingId IN(:trackingIds)")
  List<Container> fetchAllocatedStores(Set<String> trackingIds, List<Integer> facilityNumbers);
}
