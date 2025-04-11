package com.walmart.move.nim.receiving.rx.service;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.InstructionError;
import com.walmart.move.nim.receiving.core.common.exception.InstructionErrorCode;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.service.v2.data.CreateInstructionServiceHelper;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * This service is responsible for persisting smaller transaction related to instruction. As in
 * spring transaction boundary is not getting created if caller method and called method are in same
 * class.
 *
 * <p>Reason - The problem here is, that Spring's AOP proxies don't extend but rather wrap your
 * service instance to intercept calls. This has the effect, that any call to "this" from within
 * your service instance is directly invoked on that instance and cannot be intercepted by the
 * wrapping proxy (the proxy is not even aware of any such call).
 */
@Service("RxInstructionPersisterService")
public class RxInstructionPersisterService {

  private static final Logger LOG = LoggerFactory.getLogger(RxInstructionPersisterService.class);
  @Autowired private InstructionRepository instructionRepository;
  @Autowired private InstructionPersisterService instructionPersisterService;
  @Autowired private CreateInstructionServiceHelper createInstructionServiceHelper;
  @InjectTenantFilter
  @Transactional(readOnly = true)
  public Instruction fetchInstructionByDeliveryAndGtinAndLotNumberAndUserIdV2(
          InstructionRequest instructionRequest, String userId) {
    Long deliveryNumber = Long.parseLong(instructionRequest.getDeliveryNumber());
    Map<String, ScannedData> scannedDataMap =
            RxUtils.scannedDataMap(instructionRequest.getScannedDataList());
    String lotNumber = scannedDataMap.get(ApplicationIdentifier.LOT.getKey()).getValue();
    String serial = scannedDataMap.get(ApplicationIdentifier.SERIAL.getKey()).getValue();
    String gtin = scannedDataMap.get(ApplicationIdentifier.GTIN.getKey()).getValue();
    String expiryDate = scannedDataMap.get(ApplicationIdentifier.EXP.getKey()).getValue();
    TenantContext.get().setAtlasRcvChkInsExistStart(System.currentTimeMillis());
    List<Instruction> existingInstructionsList;
    if (RxUtils.isSplitPalletInstructionRequest(instructionRequest)) {
      if (Objects.nonNull(instructionRequest.getInstructionSetId())) {
        existingInstructionsList =
                instructionRepository
                        .findByDeliveryNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetId(
                                deliveryNumber, userId, userId, instructionRequest.getInstructionSetId());
      } else {
        existingInstructionsList =
                instructionRepository
                        .findByDeliveryNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNotNull(
                                deliveryNumber, userId, userId);
      }
    } else {
      existingInstructionsList =
              instructionRepository
                      .findByDeliveryNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(
                              deliveryNumber, userId, userId);
    }
    TenantContext.get().setAtlasRcvChkInsExistEnd(System.currentTimeMillis());

    // GTIN/SERIAL/LOT/EXPIRY VALIDATION
    existingInstructionsList =
            createInstructionServiceHelper.filterInstructionMatching2DV2(
                    existingInstructionsList, gtin, serial, lotNumber, expiryDate);

    Instruction instructionResponse = null;
    if(!CollectionUtils.isEmpty(existingInstructionsList)) {
      for (Instruction instruction : existingInstructionsList) {
        if (!StringUtils.isEmpty(instruction.getInstructionMsg()) &&
                RxUtils.isUserEligibleToReceive(userId, instruction)) {
          instructionResponse = instruction;

        }

      }
    }

    return instructionResponse;

  }

  /**
   * Method to fetch instruction by id
   *
   * @param instructionRequest
   * @return existingInstruction or null
   */
  @Transactional(readOnly = true)
  @InjectTenantFilter
  public Instruction fetchExistingInstructionIfexists(InstructionRequest instructionRequest) {
    TenantContext.get().setAtlasRcvChkInsExistStart(System.currentTimeMillis());
    Instruction existingInstructionByMessageId =
            instructionRepository.findByMessageId(instructionRequest.getMessageId());
    TenantContext.get().setAtlasRcvChkInsExistEnd(System.currentTimeMillis());
    if (existingInstructionByMessageId != null
            && existingInstructionByMessageId.getInstructionMsg() != null) {
      return existingInstructionByMessageId;
    }
    return null;
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public Instruction fetchInstructionByDeliveryNumberAndSSCCAndUserId(
      InstructionRequest instructionRequest, String userId) {
    TenantContext.get().setAtlasRcvChkInsExistStart(System.currentTimeMillis());
    Instruction existingInstructionBySSCC = null;
    if (RxUtils.isSplitPalletInstructionRequest(instructionRequest)) {
      if (Objects.nonNull(instructionRequest.getInstructionSetId())) {
        existingInstructionBySSCC =
            instructionRepository
                .findByDeliveryNumberAndSsccNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetId(
                    Long.valueOf(instructionRequest.getDeliveryNumber()),
                    instructionRequest.getSscc(),
                    userId,
                    userId,
                    instructionRequest.getInstructionSetId());
      } else {
        existingInstructionBySSCC =
            instructionRepository
                .findByDeliveryNumberAndSsccNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNotNull(
                    Long.valueOf(instructionRequest.getDeliveryNumber()),
                    instructionRequest.getSscc(),
                    userId,
                    userId);
      }
    } else {
      existingInstructionBySSCC =
          instructionRepository
              .findByDeliveryNumberAndSsccNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(
                  Long.valueOf(instructionRequest.getDeliveryNumber()),
                  instructionRequest.getSscc(),
                  userId,
                  userId);
    }
    TenantContext.get().setAtlasRcvChkInsExistEnd(System.currentTimeMillis());
    if (existingInstructionBySSCC != null
        && existingInstructionBySSCC.getInstructionMsg() != null
        && RxUtils.isUserEligibleToReceive(userId, existingInstructionBySSCC)) {
      return existingInstructionBySSCC;
    }
    return null;
  }

  /**
   * This method fetches instruction by delivery, gtin and user id
   *
   * @param instructionRequest
   * @param userId
   * @return Instruction
   */
  @InjectTenantFilter
  @Transactional(readOnly = true)
  public Instruction
      fetchInstructionByDeliveryAndGtinAndUserIdOrLastChangeUserIdAndProblemTagIdIsNull(
          InstructionRequest instructionRequest, String userId) {
    TenantContext.get().setAtlasRcvChkInsExistStart(System.currentTimeMillis());
    List<Instruction> existingInstructionsList = null;
    if (RxUtils.isSplitPalletInstructionRequest(instructionRequest)) {
      if (Objects.nonNull(instructionRequest.getInstructionSetId())) {
        existingInstructionsList =
            instructionRepository
                .findByDeliveryNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetId(
                    Long.valueOf(instructionRequest.getDeliveryNumber()),
                    userId,
                    userId,
                    instructionRequest.getInstructionSetId());
      } else {
        existingInstructionsList =
            instructionRepository
                .findByDeliveryNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNotNull(
                    Long.valueOf(instructionRequest.getDeliveryNumber()), userId, userId);
      }
    } else {
      existingInstructionsList =
          instructionRepository
              .findByDeliveryNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(
                  Long.valueOf(instructionRequest.getDeliveryNumber()), userId, userId);
    }
    TenantContext.get().setAtlasRcvChkInsExistEnd(System.currentTimeMillis());
    existingInstructionsList =
        RxUtils.filterInstructionMatchingGtin(
            existingInstructionsList, instructionRequest.getUpcNumber());
    if (!CollectionUtils.isEmpty(existingInstructionsList)
        && existingInstructionsList.get(0).getInstructionMsg() != null
        && RxUtils.isUserEligibleToReceive(userId, existingInstructionsList.get(0))) {
      return existingInstructionsList.get(0);
    }
    return null;
  }

  /**
   * Fetch instruction by delivery number, gtin, lotNumber and userId
   *
   * @param instructionRequest
   * @param userId
   * @return Instruction
   */
  @InjectTenantFilter
  @Transactional(readOnly = true)
  public Instruction fetchInstructionByDeliveryAndGtinAndLotNumberAndUserId(
      InstructionRequest instructionRequest, String userId) {
    Long deliveryNumber = Long.parseLong(instructionRequest.getDeliveryNumber());
    Map<String, ScannedData> scannedDataMap =
        RxUtils.scannedDataMap(instructionRequest.getScannedDataList());
    String lotNumber = scannedDataMap.get(ApplicationIdentifier.LOT.getKey()).getValue();
    String serial = scannedDataMap.get(ApplicationIdentifier.SERIAL.getKey()).getValue();
    Gson gson = new Gson();
    TenantContext.get().setAtlasRcvChkInsExistStart(System.currentTimeMillis());
    List<Instruction> existingInstructionsList;
    if (RxUtils.isSplitPalletInstructionRequest(instructionRequest)) {
      if (Objects.nonNull(instructionRequest.getInstructionSetId())) {
        existingInstructionsList =
            instructionRepository
                .findByDeliveryNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetId(
                    deliveryNumber, userId, userId, instructionRequest.getInstructionSetId());
      } else {
        existingInstructionsList =
            instructionRepository
                .findByDeliveryNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNotNull(
                    deliveryNumber, userId, userId);
      }
    } else {
      existingInstructionsList =
          instructionRepository
              .findByDeliveryNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(
                  deliveryNumber, userId, userId);
    }
    TenantContext.get().setAtlasRcvChkInsExistEnd(System.currentTimeMillis());
    existingInstructionsList =
        RxUtils.filterInstructionMatchingGtin(
            existingInstructionsList, instructionRequest.getUpcNumber());
    Instruction instructionResponse = null;
    if (!CollectionUtils.isEmpty(existingInstructionsList)) {
      for (Instruction instruction : existingInstructionsList) {
        DeliveryDocument document =
            gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
        DeliveryDocumentLine documentLine = document.getDeliveryDocumentLines().get(0);
        if (org.apache.commons.lang3.StringUtils.equalsIgnoreCase(
                lotNumber, documentLine.getLotNumber())
            && RxUtils.isUserEligibleToReceive(userId, instruction)) {
          // for epcis check if scanned serial is in one of serializedInfo or scannedCase
          ItemData additionalInfo = documentLine.getAdditionalInfo();
          if (Objects.nonNull(additionalInfo)
              && Objects.nonNull(additionalInfo.getSerializedInfo())) {
            List<ManufactureDetail> serializedInfo = additionalInfo.getSerializedInfo();
            List<ManufactureDetail> epcisSerialMatch;
            epcisSerialMatch =
                serializedInfo
                    .stream()
                    .filter(info -> info.getSerial().equalsIgnoreCase(serial))
                    .collect(Collectors.toList());
            ManufactureDetail scannedCase = additionalInfo.getScannedCase();
            if (Objects.nonNull(scannedCase) && serial.equalsIgnoreCase(scannedCase.getSerial())) {
              epcisSerialMatch.add(scannedCase);
            }
            if (epcisSerialMatch.isEmpty()) break;
          }
          instructionResponse = instruction;
          break;
        }
      }
      if (instructionResponse != null
          && StringUtils.isEmpty(instructionResponse.getInstructionMsg())) {
        instructionResponse = null;
      }
    }

    return instructionResponse;
  }

  /**
   * This method checks if a new instruction can be created for the given item. Prevent creation of
   * new instruction if projectedRecvQty/rcvQty for all instruction created till now is equal to
   * maxReceiveQty
   *
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @param totalReceivedQty
   * @param maxReceiveQty
   * @throws ReceivingException
   */
  @InjectTenantFilter
  @Transactional(readOnly = true)
  public void checkIfNewInstructionCanBeCreated(
      String purchaseReferenceNumber,
      int purchaseReferenceLineNumber,
      long totalReceivedQty,
      int maxReceiveQty,
      boolean isSplitPalletInstruction,
      String userId)
      throws ReceivingException {
    TenantContext.get().setAtlasRcvChkNewInstCanBeCreatedStart(System.currentTimeMillis());
    Long pendingInstructionsCumulativeProjectedReceivedQty =
        instructionRepository.getSumOfProjectedReceivedQuantityOfPendingInstructionsByPoPoLine(
            purchaseReferenceNumber, purchaseReferenceLineNumber);
    TenantContext.get().setAtlasRcvChkNewInstCanBeCreatedEnd(System.currentTimeMillis());
    boolean isPendingInstructionExist =
        pendingInstructionsCumulativeProjectedReceivedQty != null
            && pendingInstructionsCumulativeProjectedReceivedQty > 0;
    if (isPendingInstructionExist
        && (pendingInstructionsCumulativeProjectedReceivedQty + totalReceivedQty)
            >= maxReceiveQty) {

      InstructionError instructionError;
      String errorCode;
      String errorMessage;
      // if openInstruction is only split pallet, throw different error code and message so that
      // client will not show transfer instructions dialog
      int nonSplitPalletInstructionCount =
          instructionPersisterService.findNonSplitPalletInstructionCount(
              purchaseReferenceNumber, purchaseReferenceLineNumber);
      if (nonSplitPalletInstructionCount == 0 || isSplitPalletInstruction) {
        instructionError =
            InstructionErrorCode.getErrorValue(ReceivingException.MUTLI_USER_ERROR_SPLIT_PALLET);
        errorCode = ReceivingConstants.MULTI_INSTR_ERROR_CODE;
        errorMessage = instructionError.getErrorMessage();
      } else {
        List<String> openInstructionsUserList =
            instructionRepository
                .getOpenInstructionsLastChangedUserByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                    purchaseReferenceNumber, purchaseReferenceLineNumber);
        if (!CollectionUtils.isEmpty(openInstructionsUserList)
            && openInstructionsUserList.contains(userId)) {
          return;
        }
        // else below MUTLI_USER_ERROR
        instructionError = InstructionErrorCode.getErrorValue(RxConstants.RX_MUTLI_USER_ERROR);
        errorMessage =
            String.format(
                instructionError.getErrorMessage(),
                org.apache.commons.lang3.StringUtils.join(openInstructionsUserList, ","));
        errorCode = instructionError.getErrorCode();
      }
      throw new ReceivingException(
          errorMessage,
          HttpStatus.INTERNAL_SERVER_ERROR,
          errorCode,
          instructionError.getErrorHeader());
    }
  }
}
