package com.walmart.move.nim.receiving.core.repositories;

import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.InstructionDetails;
import com.walmart.move.nim.receiving.core.model.WFTResponse;
import java.util.Date;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Instruction JPA repository
 *
 * @author g0k0072
 */
@Repository
public interface InstructionRepository extends JpaRepository<Instruction, Long> {
  /**
   * Method to fetch instruction based on delivery number
   *
   * @param deliveryNumber
   * @return List of instruction
   */
  List<Instruction> findByDeliveryNumber(Long deliveryNumber);

  /**
   * Gets open instructions count by delivery
   *
   * @param deliveryNumber
   * @return open instruction count
   */
  Long countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(long deliveryNumber);

  /**
   * Finds instruction based on message id
   *
   * @param messageId message id of the instruction
   * @return instruction
   */
  Instruction findByMessageId(String messageId);

  /**
   * Finds instruction based on delivery number and problem tag id and instruction code is not null
   *
   * @param deliveryNumber
   * @param problemTagId
   * @return
   */
  List<Instruction> findByDeliveryNumberAndProblemTagIdAndInstructionCodeIsNotNull(
      Long deliveryNumber, String problemTagId);

  /**
   * Finds instruction based on problem tag id and where instruction code is not null
   *
   * @param problemTagId
   * @return
   */
  List<Instruction> findByProblemTagIdAndInstructionCodeIsNotNull(String problemTagId);

  /**
   * Get all open instruction
   *
   * @param deliveryNumber
   * @param purchaseReferenceNumber
   */
  List<Instruction>
      findByDeliveryNumberAndPurchaseReferenceNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(
          Long deliveryNumber, String purchaseReferenceNumber);

  /**
   * this method is for data clean up needed by either DIT or E2E test scripts
   *
   * @param purchaseReferenceNumber
   */
  void deleteByPurchaseReferenceNumber(String purchaseReferenceNumber);

  /**
   * Method to fetch instruction based on delivery number and where instruction code is not null
   *
   * @param deliveryNumber
   * @return List of instruction
   */
  List<Instruction> findByDeliveryNumberAndInstructionCodeIsNotNull(Long deliveryNumber);

  List<Instruction> findByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(
      Long deliveryNumber);

  @Query(
      "SELECT SUM(ins.projectedReceiveQty) from Instruction ins WHERE ins.purchaseReferenceNumber = :purchaseReferenceNumber AND ins.purchaseReferenceLineNumber = :purchaseReferenceLineNumber and ins.completeTs is null and ins.instructionCode is not null")
  Long getSumOfProjectedReceivedQuantityOfPendingInstructionsByPoPoLine(
      @Param("purchaseReferenceNumber") String purchaseReferenceNumber,
      @Param("purchaseReferenceLineNumber") int purchaseReferenceLineNumber);

  @Query(
      "SELECT SUM(ins.projectedReceiveQty) from Instruction ins WHERE ins.deliveryNumber = :deliveryNumber AND ins.purchaseReferenceNumber = :purchaseReferenceNumber AND ins.purchaseReferenceLineNumber = :purchaseReferenceLineNumber and ins.completeTs is null and ins.instructionCode is not null and ins.instructionSetId is null")
  Integer
      getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
          @Param("deliveryNumber") Long deliveryNumber,
          @Param("purchaseReferenceNumber") String purchaseReferenceNumber,
          @Param("purchaseReferenceLineNumber") int purchaseReferenceLineNumber);

  /**
   * Used for getting the instruction by po/po line, delivery
   *
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @return List of instruction by PO/PO line and delivery number
   */
  List<Instruction> findByPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndDeliveryNumber(
      String purchaseReferenceNumber, Integer purchaseReferenceLineNumber, Long deliveryNumber);

  /**
   * Used for getting list of number of pallets received after a given timestamp
   *
   * @param fromDate
   * @return
   */
  @Query(
      value =
          "SELECT COUNT(I.messageId) FROM Instruction I WHERE I.createTs > ?1 AND I.createTs < ?2 AND I.completeTs IS NOT NULL GROUP BY I.deliveryNumber")
  List<Long> findNumberOfPalletsPerDeliveryByCreateTsAfterAndCompleteTsNotNull(
      Date fromDate, Date toDate);

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

  List<Instruction> findByInstructionCodeAndDeliveryNumberAndGtinAndCompleteTsIsNull(
      String instructionCode, Long deliveryNumber, String gtin);

  /**
   * used to get sum of received qty against all activityName
   *
   * @param fromDate
   * @param toDate
   */
  @Query(
      value =
          "SELECT new com.walmart.move.nim.receiving.core.model.WFTResponse(I.activityName, SUM(I.receivedQuantity) as receivedQty) FROM Instruction I WHERE I.createTs > ?1 AND I.createTs < ?2 AND I.activityName is not NULL GROUP BY I.activityName")
  List<WFTResponse> getReceivedQtyAgainstActivityNameByTime(Date fromDate, Date toDate);

  /**
   * used to get sum of received qty against all activityName
   *
   * @param activityName
   * @param fromDate
   * @param toDate
   */
  @Query(
      value =
          "SELECT new com.walmart.move.nim.receiving.core.model.WFTResponse(I.createUserId, I.activityName, SUM(I.receivedQuantity) as receivedQty) FROM Instruction I WHERE I.activityName= ?1 AND I.createTs > ?2 AND I.createTs < ?3 GROUP BY I.createUserId, I.activityName")
  List<WFTResponse> getReceivedQtyAgainstUserNameGivenActivityName(
      String activityName, Date fromDate, Date toDate);

  /**
   * Finds instruction based on SSCC
   *
   * @param deliveryNumber deliveryNumber of the instruction
   * @return instruction
   */
  Instruction findByDeliveryNumberAndSsccNumberAndCreateUserIdAndCompleteTsIsNull(
      Long deliveryNumber, String ssccNumber, String userId);

  @Query(
      value =
          "SELECT * FROM Instruction i WHERE i.DELIVERY_NUMBER = :deliveryNumber AND i.SSCC_NUMBER = :ssccNumber "
              + "AND (i.CREATE_USER_ID = :userId OR i.LAST_CHANGE_USER_ID = :lastChangeUserId) AND COMPLETE_TS IS NULL "
              + "AND i.PROBLEM_TAG_ID IS NULL AND i.INSTRUCTION_SET_ID IS NULL",
      nativeQuery = true)
  Instruction
      findByDeliveryNumberAndSsccNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(
          @Param("deliveryNumber") Long deliveryNumber,
          @Param("ssccNumber") String ssccNumber,
          @Param("userId") String userId,
          @Param("lastChangeUserId") String lastChangeUserId);

  @Query(
      value =
          "SELECT * FROM Instruction i WHERE i.DELIVERY_NUMBER = :deliveryNumber AND i.SSCC_NUMBER = :ssccNumber "
              + " AND COMPLETE_TS IS NULL "
              + "AND i.PROBLEM_TAG_ID IS NULL AND i.INSTRUCTION_SET_ID IS NULL",
      nativeQuery = true)
  List<Instruction>
      findByDeliveryNumberAndSsccNumberAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(
          @Param("deliveryNumber") Long deliveryNumber, @Param("ssccNumber") String ssccNumber);

  @Query(
      value =
          "SELECT * FROM Instruction i WHERE i.DELIVERY_NUMBER = :deliveryNumber AND i.SSCC_NUMBER = :ssccNumber "
              + "AND (i.CREATE_USER_ID = :userId OR i.LAST_CHANGE_USER_ID = :lastChangeUserId) AND COMPLETE_TS IS NULL "
              + "AND i.PROBLEM_TAG_ID IS NULL AND i.INSTRUCTION_SET_ID = :instructionSetId",
      nativeQuery = true)
  Instruction
      findByDeliveryNumberAndSsccNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetId(
          @Param("deliveryNumber") Long deliveryNumber,
          @Param("ssccNumber") String ssccNumber,
          @Param("userId") String userId,
          @Param("lastChangeUserId") String lastChangeUserId,
          @Param("instructionSetId") Long instructionSetId);

  @Query(
      value =
          "SELECT * FROM Instruction i WHERE i.DELIVERY_NUMBER = :deliveryNumber AND i.SSCC_NUMBER = :ssccNumber "
              + "AND (i.CREATE_USER_ID = :userId OR i.LAST_CHANGE_USER_ID = :lastChangeUserId) AND COMPLETE_TS IS NULL "
              + "AND i.PROBLEM_TAG_ID IS NULL AND i.INSTRUCTION_SET_ID IS NOT NULL",
      nativeQuery = true)
  Instruction
      findByDeliveryNumberAndSsccNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNotNull(
          @Param("deliveryNumber") Long deliveryNumber,
          @Param("ssccNumber") String ssccNumber,
          @Param("userId") String userId,
          @Param("lastChangeUserId") String lastChangeUserId);

  List<Instruction> findByIdGreaterThanEqual(Long lastDeleteId, Pageable pageable);

  Instruction findByDeliveryNumberAndSsccNumberAndCreateUserIdAndCompleteTsIsNullAndProblemTagId(
      Long deliveryNumber, String sscc, String userId, String problemTagId);

  @Query(
      value =
          "SELECT * FROM Instruction i WHERE i.DELIVERY_NUMBER = :deliveryNumber AND i.SSCC_NUMBER = :ssccNumber AND (i.CREATE_USER_ID = :userId OR i.LAST_CHANGE_USER_ID = :lastChangeUserId) AND COMPLETE_TS IS NULL AND i.PROBLEM_TAG_ID = :problemTagId",
      nativeQuery = true)
  Instruction
      findByDeliveryNumberAndSsccNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagId(
          @Param("deliveryNumber") Long deliveryNumber,
          @Param("ssccNumber") String ssccNumber,
          @Param("userId") String userId,
          @Param("lastChangeUserId") String lastChangeUserId,
          @Param("problemTagId") String problemTagId);

  List<Instruction>
      findByCreateTsBeforeAndFacilityNumAndCompleteTsIsNullAndInstructionCodeIsNotNull(
          Date lastChangeTs, Integer facilityNum);

  /**
   * Finds instruction based on GTIN
   *
   * @return instruction
   */
  Instruction findByGtinAndCreateUserIdAndCompleteTsIsNull(String gtin, String userId);

  List<Instruction> findByDeliveryNumberAndGtinAndCreateUserIdAndCompleteTsIsNull(
      Long deliveryNumber, String gtin, String userId);

  List<Instruction> findByDeliveryNumberAndGtinAndCreateUserIdAndCompleteTsIsNullAndProblemTagId(
      Long deliveryNumber, String gtin, String userId, String problemTagId);

  List<Instruction> findByDeliveryNumberAndGtinInAndLastChangeUserIdAndCompleteTsIsNull(
      Long deliveryNumber, List<String> gtin, String userId);

  List<Instruction>
      findByDeliveryNumberAndGtinInAndLastChangeUserIdAndCompleteTsIsNullAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNull(
          Long deliveryNumber,
          List<String> gtin,
          String userId,
          String purchaseReferenceNumber,
          Integer purchaseReferenceLineNumber);

  List<Instruction>
      findByDeliveryNumberAndGtinInAndLastChangeUserIdAndCompleteTsIsNullAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndSsccNumberIsNull(
          Long deliveryNumber,
          List<String> gtin,
          String userId,
          String purchaseReferenceNumber,
          Integer purchaseReferenceLineNumber);

  @Query(
      value =
          "SELECT * FROM Instruction i WHERE i.DELIVERY_NUMBER = :deliveryNumber AND i.GTIN = :gtin AND (i.CREATE_USER_ID = :userId OR i.LAST_CHANGE_USER_ID = :lastChangeUserId) AND i.COMPLETE_TS IS NULL AND i.PROBLEM_TAG_ID = :problemTagId",
      nativeQuery = true)
  List<Instruction>
      findByDeliveryNumberAndGtinAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagId(
          @Param("deliveryNumber") Long deliveryNumber,
          @Param("gtin") String gtin,
          @Param("userId") String userId,
          @Param("lastChangeUserId") String lastChangeUserId,
          @Param("problemTagId") String problemTagId);

  @Query(
      value =
          "SELECT * FROM Instruction i WHERE i.DELIVERY_NUMBER = :deliveryNumber AND i.GTIN = :gtin "
              + "AND (i.CREATE_USER_ID = :userId OR i.LAST_CHANGE_USER_ID = :lastChangeUserId) AND i.COMPLETE_TS IS NULL "
              + "AND i.PROBLEM_TAG_ID IS NULL AND i.INSTRUCTION_SET_ID IS NULL",
      nativeQuery = true)
  List<Instruction>
      findByDeliveryNumberAndGtinAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(
          @Param("deliveryNumber") Long deliveryNumber,
          @Param("gtin") String gtin,
          @Param("userId") String userId,
          @Param("lastChangeUserId") String lastChangeUserId);

  @Query(
      value =
          "SELECT * FROM Instruction i WHERE i.DELIVERY_NUMBER = :deliveryNumber "
              + "AND (i.CREATE_USER_ID = :userId OR i.LAST_CHANGE_USER_ID = :lastChangeUserId) AND i.COMPLETE_TS IS NULL "
              + "AND i.PROBLEM_TAG_ID IS NULL AND i.INSTRUCTION_SET_ID IS NULL",
      nativeQuery = true)
  List<Instruction>
      findByDeliveryNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(
          @Param("deliveryNumber") Long deliveryNumber,
          @Param("userId") String userId,
          @Param("lastChangeUserId") String lastChangeUserId);

  @Query(
      value =
          "SELECT * FROM Instruction i WHERE i.DELIVERY_NUMBER = :deliveryNumber AND i.GTIN = :gtin "
              + "AND (i.CREATE_USER_ID = :userId OR i.LAST_CHANGE_USER_ID = :lastChangeUserId) AND i.COMPLETE_TS IS NULL "
              + "AND i.PROBLEM_TAG_ID IS NULL AND i.INSTRUCTION_SET_ID = :instructionSetId",
      nativeQuery = true)
  List<Instruction>
      findByDeliveryNumberAndGtinAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetId(
          @Param("deliveryNumber") Long deliveryNumber,
          @Param("gtin") String gtin,
          @Param("userId") String userId,
          @Param("lastChangeUserId") String lastChangeUserId,
          @Param("instructionSetId") Long instructionSetId);

  @Query(
      value =
          "SELECT * FROM Instruction i WHERE i.DELIVERY_NUMBER = :deliveryNumber "
              + "AND (i.CREATE_USER_ID = :userId OR i.LAST_CHANGE_USER_ID = :lastChangeUserId) AND i.COMPLETE_TS IS NULL "
              + "AND i.PROBLEM_TAG_ID IS NULL AND i.INSTRUCTION_SET_ID = :instructionSetId",
      nativeQuery = true)
  List<Instruction>
      findByDeliveryNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetId(
          Long deliveryNumber, String userId, String lastChangeUserId, Long instructionSetId);

  @Query(
      value =
          "SELECT * FROM Instruction i WHERE i.DELIVERY_NUMBER = :deliveryNumber "
              + "AND (i.CREATE_USER_ID = :userId OR i.LAST_CHANGE_USER_ID = :lastChangeUserId) AND i.COMPLETE_TS IS NULL "
              + "AND i.PROBLEM_TAG_ID IS NULL AND i.INSTRUCTION_SET_ID IS NOT NULL",
      nativeQuery = true)
  List<Instruction>
      findByDeliveryNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNotNull(
          @Param("deliveryNumber") Long deliveryNumber,
          @Param("userId") String userId,
          @Param("lastChangeUserId") String lastChangeUserId);

  List<Instruction> findByDeliveryNumberAndCompleteTsIsNullAndProblemTagIdIsNull(
      Long deliveryNumber);

  /**
   * used to get sum of received qty against all activityName
   *
   * @param deliveryNumber
   * @param facilityNum
   */
  @Query(
      value =
          "SELECT new com.walmart.move.nim.receiving.core.model.InstructionDetails(I.id, I.deliveryNumber, I.createUserId, I.lastChangeUserId, I.receivedQuantity) FROM Instruction I WHERE I.deliveryNumber= ?1 and I.facilityNum= ?2  and I.completeTs is null")
  List<InstructionDetails> getUncompletedInstructionDetailsByDeliveryNumber(
      Long deliveryNumber, Integer facilityNum);

  @Modifying
  @Query(
      value =
          "UPDATE INSTRUCTION SET LAST_CHANGE_USER_ID = :userId, LAST_CHANGE_TS = CURRENT_TIMESTAMP WHERE ID in (:instructionIds) AND COMPLETE_TS IS NULL AND COMPLETE_USER_ID IS NULL",
      nativeQuery = true)
  void updateLastChangeUserIdAndLastChangeTs(
      @Param("instructionIds") List<Long> instructionIds, @Param("userId") String userId);

  /**
   * Get instructions based on instruction Ids
   *
   * @param instructionIds
   */
  List<Instruction> findByIdIn(List<Long> instructionIds);

  /**
   * Get dock tag instruction by instruction code and delivery number
   *
   * @param instructionCode instruction code
   * @param deliveryNumber delivery number
   * @return
   */
  List<Instruction> findByInstructionCodeAndDeliveryNumberAndCompleteTsIsNull(
      String instructionCode, Long deliveryNumber);

  /** Generate a sequence value for Instruction Set Id */
  @Query(
      value = "SELECT (NEXT VALUE FOR INSTRUCTION_SET_ID_SEQUENCE) AS INSTRUCTION_SET_ID",
      nativeQuery = true)
  Long getNextInstructionSetId();

  /** Find Instructions by Delivery and InstructionSetId */
  List<Instruction> findByDeliveryNumberAndInstructionSetIdOrderByCreateTs(
      Long deliveryNumber, Long instructionSetId);

  /**
   * Find Instruction Slot Details by InstructionSetId and deliveryNumber
   *
   * @return
   */
  @Query(
      value =
          "SELECT DISTINCT(JSON_VALUE(DELIVERY_DOCUMENT , '$.deliveryDocumentLines[0].additionalInfo.primeSlot')) FROM INSTRUCTION "
              + "WHERE DELIVERY_NUMBER = :deliveryNumber AND INSTRUCTION_SET_ID = :instructionSetId AND COMPLETE_TS IS NULL "
              + "AND COMPLETE_USER_ID IS NULL",
      nativeQuery = true)
  List<String> getOpenInstructionSlotDetailsByDeliveryNumberAndInstructionSetId(
      @Param("deliveryNumber") String deliveryNumber,
      @Param("instructionSetId") Long instructionSetId);

  void deleteByDeliveryNumber(Long deliveryNumber);

  List<Instruction>
      findByDeliveryNumberAndGtinInAndLastChangeUserIdAndCompleteTsIsNullAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetId(
          Long deliveryNumber,
          List<String> gtin,
          String userId,
          String purchaseReferenceNumber,
          Integer purchaseReferenceLineNumber,
          Long instructionSetId);

  List<Instruction>
      findByDeliveryNumberAndGtinInAndLastChangeUserIdAndCompleteTsIsNullAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNotNull(
          Long deliveryNumber,
          List<String> gtin,
          String userId,
          String purchaseReferenceNumber,
          Integer purchaseReferenceLineNumber);

  @Query(
      "SELECT SUM(ins.receivedQuantity) from Instruction ins WHERE ins.deliveryNumber = :deliveryNumber AND ins.purchaseReferenceNumber = :purchaseReferenceNumber AND ins.purchaseReferenceLineNumber = :purchaseReferenceLineNumber and ins.completeTs is null and ins.instructionCode is not null")
  Integer getSumOfReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLine(
      @Param("deliveryNumber") Long deliveryNumber,
      @Param("purchaseReferenceNumber") String purchaseReferenceNumber,
      @Param("purchaseReferenceLineNumber") int purchaseReferenceLineNumber);

  int
      countByPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndInstructionSetIdIsNullAndCompleteTsIsNull(
          String purchaseReferenceNumber, int purchaseReferenceLineNumber);

  @Query(
      "SELECT SUM(ins.receivedQuantity) from Instruction ins WHERE ins.problemTagId = :problemTagId AND ins.purchaseReferenceNumber = :purchaseReferenceNumber AND ins.purchaseReferenceLineNumber = :purchaseReferenceLineNumber AND ins.completeTs is not null and ins.instructionCode is not null")
  Long getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
      @Param("purchaseReferenceNumber") String purchaseReferenceNumber,
      @Param("purchaseReferenceLineNumber") int purchaseReferenceLineNumber,
      @Param("problemTagId") String problemTagId);

  @Query(
      "SELECT DISTINCT(ins.createUserId) from Instruction ins WHERE ins.purchaseReferenceNumber = :purchaseReferenceNumber AND ins.purchaseReferenceLineNumber = :purchaseReferenceLineNumber AND ins.completeTs is null and ins.instructionSetId is null")
  List<String> getOpenInstructionsUsersByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
      @Param("purchaseReferenceNumber") String purchaseReferenceNumber,
      @Param("purchaseReferenceLineNumber") int purchaseReferenceLineNumber);

  @Query(
      "SELECT DISTINCT(ins.lastChangeUserId) from Instruction ins WHERE ins.purchaseReferenceNumber = :purchaseReferenceNumber AND ins.purchaseReferenceLineNumber = :purchaseReferenceLineNumber AND ins.completeTs is null and ins.instructionSetId is null")
  List<String>
      getOpenInstructionsLastChangedUserByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
          @Param("purchaseReferenceNumber") String purchaseReferenceNumber,
          @Param("purchaseReferenceLineNumber") int purchaseReferenceLineNumber);

  @Query(
      "SELECT (ins.deliveryDocument) FROM Instruction ins "
          + "WHERE ins.deliveryNumber = :deliveryNumber AND ins.instructionSetId = :instructionSetId")
  List<String> getDeliveryDocumentsByDeliveryNumberAndInstructionSetId(
      @Param("deliveryNumber") Long deliveryNumber,
      @Param("instructionSetId") Long instructionSetId);

  List<Instruction> findAllBySourceMessageId(String sourceMessageId);

  List<Instruction>
      findByDeliveryNumberAndGtinInAndLastChangeUserIdAndCompleteTsIsNullAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberIsNullAndInstructionSetIdIsNull(
          Long valueOf,
          List<String> possibleUpcList,
          String userId,
          String purchaseReferenceNumber);

  Instruction
      findFirstByInstructionCodeAndDeliveryNumberAndSsccNumberAndPurchaseReferenceNumberAndCompleteUserIdOrderByCreateTsDesc(
          String instructionCode,
          Long deliveryNumber,
          String ssccNumber,
          String poNumber,
          String userId);

  Instruction
      findFirstByInstructionCodeAndDeliveryNumberAndPurchaseReferenceNumberAndCompleteUserIdAndSsccNumberIsNullOrderByCreateTsDesc(
          String instructionCode, Long deliveryNumber, String poNumber, String userId);

  Instruction
      findFirstByInstructionCodeAndDeliveryNumberAndPurchaseReferenceNumberAndCompleteUserIdOrderByCreateTsDesc(
          String instructionCode, Long deliveryNumber, String poNumber, String userId);

  List<Instruction>
      findByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndCompleteTsIsNull(
          Long deliveryNumber, String purchaseReferenceNumber, Integer purchaseReferenceLineNumber);

  List<Instruction> findByDeliveryNumberAndSsccNumberAndCompleteTsIsNotNull(
      Long deliveryNumber, String ssccNumber);
}
