package com.walmart.move.nim.receiving.reporting.repositories;

import com.walmart.move.nim.receiving.core.entity.Instruction;
import java.util.Date;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Instruction JPA repository for reporting
 *
 * @author sks0013
 */
public interface ReportingInstructionRepository extends JpaRepository<Instruction, Long> {

  /**
   * Used for getting average build time of pallets received after a given timestamp Since JPQL does
   * not have DATEDIFF function, native query SQL has to be used
   *
   * @param fromDate
   * @return
   */
  @Query(
      value =
          "SELECT AVG(CONVERT(BIGINT, DATEDIFF(s, I.CREATE_TS, I.COMPLETE_TS))) FROM Instruction I"
              + " WHERE I.CREATE_TS > ?1 AND I.CREATE_TS <?2 AND I.facilityNum = ?3 AND I.facilityCountryCode = ?4 AND I.COMPLETE_TS IS NOT NULL ",
      nativeQuery = true)
  Long averagePalletBuildTime(
      Date fromDate, Date todate, Integer facilityNum, String facilityCountryCode);

  /**
   * Used for getting the number of pallets received after problem resolution
   *
   * @param fromDate
   * @return
   */
  Integer countByProblemTagIdIsNotNullAndCreateTsAfterAndCreateTsBeforeAndCompleteTsNotNull(
      Date fromDate, Date todate);

  List<Instruction> findByIdIn(List<Long> instructionIds);

  @Query(
      value =
          "SELECT count(DISTINCT I.DELIVERY_NUMBER)  from INSTRUCTION I"
              + " WHERE I.CREATE_TS > ?1 AND I.CREATE_TS <?2 AND I.facilityNum in(?3) AND I.facilityCountryCode = ?4 ",
      nativeQuery = true)
  Integer getAtlasReportCount(
      Date fromDate, Date todate, Integer facilityNum, String facilityCountryCode);

  @Query(
      value =
          "SELECT count(*)  from INSTRUCTION I"
              + " WHERE I.CREATE_TS > ?1 AND I.CREATE_TS <?2 AND I.facilityNum in(?3) AND I.facilityCountryCode = ?4 "
              + " and INSTRUCTION_CODE in (?5) and COMPLETE_TS IS NOT NULL and RECEIVED_QUANTITY > 0",
      nativeQuery = true)
  Integer getPalletSSCCScanCount(
      Date fromDate,
      Date todate,
      Integer facilityNum,
      String facilityCountryCode,
      List<String> instructionCode);

  @Query(
      value =
          "SELECT count(*)  from INSTRUCTION I"
              + " WHERE I.CREATE_TS > ?1 AND I.CREATE_TS <?2 AND I.facilityNum in(?3) AND I.facilityCountryCode = ?4 "
              + " and INSTRUCTION_CODE in (?5) and COMPLETE_TS IS NOT NULL and RECEIVED_QUANTITY > 0",
      nativeQuery = true)
  Integer getCaseSSCCScanCount(
      Date fromDate,
      Date todate,
      Integer facilityNum,
      String facilityCountryCode,
      List<String> instructionCode);

  @Query(
      value =
          "SELECT count(*)  from INSTRUCTION I"
              + " WHERE I.CREATE_TS > ?1 AND I.CREATE_TS <?2 AND I.facilityNum in(?3) AND I.facilityCountryCode = ?4 "
              + " and INSTRUCTION_CODE in (?5) and COMPLETE_TS IS NOT NULL and RECEIVED_QUANTITY > 0",
      nativeQuery = true)
  Integer get2DScanCounts(
      Date fromDate,
      Date todate,
      Integer facilityNum,
      String facilityCountryCode,
      List<String> instructionCode);

  @Query(
      value =
          "Select count(DISTINCT instruction_id) from CONTAINER"
              + " WHERE CREATE_TS > ?1 AND CREATE_TS <?2 AND facilityNum in(?3) AND facilityCountryCode = ?4 AND CONTAINER_STATUS = ?5 ",
      nativeQuery = true)
  Integer getPalletLabelCanceledCount(
      Date fromDate,
      Date todate,
      Integer facilityNum,
      String facilityCountryCode,
      String containerStatus);

  @Query(
          value =
                  "SELECT coalesce(sum(CASE WHEN(ci.quantity/ci.vnpk_qty) = 0 THEN 1 " +
                          "  ELSE (ci.quantity/ci.vnpk_qty) END),0) as casesReceived " +
                          "  FROM Container c, Container_Item ci , Instruction i" +
                          "  WHERE c.tracking_Id = ci.tracking_Id " +
                          "  AND c.facilityNum = ci.facilityNum " +
                          "  AND c.facilityCountryCode = ci.facilityCountryCode " +
                          "  AND c.INSTRUCTION_ID = i.id " +
                          "  AND c.facilityNum = i.facilityNum " +
                          "  AND c.parent_tracking_id is null " +
                          "  AND c.create_ts > ?1 AND c.create_ts < ?2 " +
                          "  AND c.facilityNum in (?3) AND c.facilityCountryCode= ?4 " +
                          "  AND i.INSTRUCTION_CODE in (?5) " +
                          "  and i.COMPLETE_TS IS NOT NULL and i.RECEIVED_QUANTITY > 0 ",
          nativeQuery = true)
  Integer getNumberOfCasesReceived(
          Date fromDate,
          Date todate,
          Integer facilityNum,
          String facilityCountryCode,
          List<String> instructionCodes);

  @Query(
          value =
                  "SELECT coalesce(sum(CASE WHEN(ci.quantity/ci.whpk_qty) = 0 THEN 1 " +
                          "  ELSE (ci.quantity/ci.whpk_qty) END),0) as unitsReceived " +
                          "  FROM Container c, Container_Item ci , Instruction i" +
                          "  WHERE c.tracking_Id = ci.tracking_Id " +
                          "  AND c.facilityNum = ci.facilityNum " +
                          "  AND c.facilityCountryCode = ci.facilityCountryCode " +
                          "  AND c.INSTRUCTION_ID = i.id " +
                          "  AND c.facilityNum = i.facilityNum " +
                          "  AND c.parent_tracking_id is null " +
                          "  AND c.create_ts > ?1 AND c.create_ts < ?2 " +
                          "  AND c.facilityNum in (?3) AND c.facilityCountryCode= ?4 " +
                          "  AND i.INSTRUCTION_CODE in (?5) " +
                          "  and i.COMPLETE_TS IS NOT NULL and i.RECEIVED_QUANTITY > 0 ",
          nativeQuery = true)
  Integer getNumberOfUnitsReceived(
          Date fromDate,
          Date todate,
          Integer facilityNum,
          String facilityCountryCode,
          List<String> instructionCodes);

  @Query(
          value =
                  "SELECT count(distinct ci.item_number) " +
                          "  FROM Container c, Container_Item ci , Instruction i" +
                          "  WHERE c.tracking_Id = ci.tracking_Id " +
                          "  AND c.facilityNum = ci.facilityNum " +
                          "  AND c.facilityCountryCode = ci.facilityCountryCode " +
                          "  AND c.INSTRUCTION_ID = i.id " +
                          "  AND c.facilityNum = i.facilityNum " +
                          "  AND c.parent_tracking_id is null " +
                          "  AND c.create_ts > ?1 AND c.create_ts < ?2 " +
                          "  AND c.facilityNum in (?3) AND c.facilityCountryCode= ?4 " +
                          "  AND i.INSTRUCTION_CODE in (?5) " +
                          "  and i.COMPLETE_TS IS NOT NULL and i.RECEIVED_QUANTITY > 0 ",
          nativeQuery = true)
  Integer getNumberOfItemsReceived(
          Date fromDate,
          Date todate,
          List<Integer> facilityNum,
          String facilityCountryCode,
          List<String> instructionCodes);
}
