package com.walmart.move.nim.receiving.witron.service;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.MIN_LIFE_EXPECTANCY_V2;

import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.InstructionError;
import com.walmart.move.nim.receiving.core.common.exception.InstructionErrorCode;
import com.walmart.move.nim.receiving.core.common.validators.InstructionStateValidator;
import com.walmart.move.nim.receiving.core.common.validators.PurchaseReferenceValidator;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.ContainerDetails;
import com.walmart.move.nim.receiving.core.model.DocumentLine;
import com.walmart.move.nim.receiving.core.model.InstructionResponse;
import com.walmart.move.nim.receiving.core.model.InstructionResponseImplNew;
import com.walmart.move.nim.receiving.core.model.UpdateInstructionRequest;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.core.service.UpdateInstructionHandler;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.InstructionStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.RequestType;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

public class WitronUpdateInstructionHandler implements UpdateInstructionHandler {

  private static final Logger log = LoggerFactory.getLogger(WitronUpdateInstructionHandler.class);

  @Autowired private ReceiptService receiptService;
  @Autowired protected InstructionPersisterService instructionPersisterService;
  @Autowired private InstructionStateValidator instructionStateValidator;
  @Autowired private InstructionHelperService instructionHelperService;
  @Autowired private PurchaseReferenceValidator purchaseReferenceValidator;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  protected InstructionError instructionError;

  public WitronUpdateInstructionHandler() {}

  @Override
  public InstructionResponse updateInstruction(
      Long instructionId,
      UpdateInstructionRequest updateInstructionRequestFromClient,
      String parentTrackingId,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    DocumentLine documentLine =
        updateInstructionRequestFromClient.getDeliveryDocumentLines().get(0);
    Integer quantityToBeReceived = documentLine.getQuantity();
    String problemTagId = updateInstructionRequestFromClient.getProblemTagId();

    Instruction instructionFromDB = instructionPersisterService.getInstructionById(instructionId);

    String userId = httpHeaders.get(ReceivingConstants.USER_ID_HEADER_KEY).get(0);
    Integer receivedQuantity = instructionFromDB.getReceivedQuantity();
    Integer projectedQuantity = instructionFromDB.getProjectedReceiveQty();
    final String deliveryNumber = instructionFromDB.getDeliveryNumber().toString();
    final Boolean firstExpiryFirstOut = instructionFromDB.getFirstExpiryFirstOut();
    final String purchaseReferenceNumber = instructionFromDB.getPurchaseReferenceNumber();
    final Integer totalQuantityValueAfterReceiving = receivedQuantity + quantityToBeReceived;

    if (Boolean.TRUE.equals(instructionFromDB.getIsReceiveCorrection())) {
      log.info("Receive as correction after PO was confirmed for instructionId:{}", instructionId);
    } else {
      // PO state validation
      purchaseReferenceValidator.validatePOConfirmation(deliveryNumber, purchaseReferenceNumber);
    }

    // Multi user validations
    ReceivingUtils.verifyUser(instructionFromDB, userId, RequestType.UPDATE);

    // Instruction state validation
    instructionStateValidator.validate(instructionFromDB);

    // Get delivery metadata for ignoring expiry date
    boolean isManagerOverrideIgnoreExpiry =
        instructionHelperService.isManagerOverrideIgnoreExpiry(
            deliveryNumber,
            purchaseReferenceNumber,
            firstExpiryFirstOut,
            instructionFromDB.getPurchaseReferenceLineNumber());

    // Validate item life expectancy against the threshold
    InstructionUtils.validateThresholdForSellByDate(
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            getFacilityNum().toString(), MIN_LIFE_EXPECTANCY_V2, false),
        firstExpiryFirstOut,
        documentLine,
        isManagerOverrideIgnoreExpiry,
        false);

    if (totalQuantityValueAfterReceiving > projectedQuantity) {
      // Block if the totalQty exceeds projectedReceiveQty for a given instruction
      log.info(
          "InstructionId:{} ProjectedReceiveQty:{} TotalQtyAfterReceiving:{}",
          instructionId,
          projectedQuantity,
          totalQuantityValueAfterReceiving);
      throw new ReceivingException(
          ReceivingException.UPDATE_INSTRUCTION_EXCEEDS_QUANTITY,
          HttpStatus.INTERNAL_SERVER_ERROR,
          ReceivingException.UPDATE_INSTRUCTION_ERROR_CODE);
    }

    // In problem flow will fetch currentReceiveQuantity by problemId and
    // maxReceiveQuantity(resolvedQty) from UI
    Long currentReceiveQuantity;
    Long maxReceiveQuantity;
    boolean isGroceryProblemReceive = false;

    if (!StringUtils.isEmpty(problemTagId)) {
      currentReceiveQuantity = receiptService.getReceivedQtyByProblemId(problemTagId);
      maxReceiveQuantity = documentLine.getMaxReceiveQty();

      // Get the tenant specific feature flag
      isGroceryProblemReceive =
          tenantSpecificConfigReader.getConfiguredFeatureFlag(
              TenantContext.getFacilityNum().toString(),
              ReceivingConstants.GROCERY_PROBLEM_RECEIVE_FEATURE,
              false);

    } else {
      currentReceiveQuantity =
          receiptService.getReceivedQtyByPoAndPoLine(
              documentLine.getPurchaseReferenceNumber(),
              documentLine.getPurchaseReferenceLineNumber());
      maxReceiveQuantity = documentLine.getExpectedQty();
      if (documentLine.getMaxOverageAcceptQty() != null) {
        maxReceiveQuantity += documentLine.getMaxOverageAcceptQty();
      }
    }

    // Get delivery metadata for ignoring overages
    boolean isManagerOverrideIgnoreOverage =
        instructionHelperService.isManagerOverrideIgnoreOverage(
            deliveryNumber,
            documentLine.getPurchaseReferenceNumber(),
            documentLine.getPurchaseReferenceLineNumber());

    if (isGroceryProblemReceive) {
      log.info("No overage alert for grocery problem receiving. problemTagId:{}", problemTagId);
    } else {
      // quantity reached allowed limit
      if ((currentReceiveQuantity.equals(maxReceiveQuantity))
          && (!isManagerOverrideIgnoreOverage)) {
        instructionError = InstructionErrorCode.getErrorValue(ReceivingException.OVERAGE_ERROR);
        log.error(instructionError.getErrorMessage());
        throw new ReceivingException(
            instructionError.getErrorMessage(),
            HttpStatus.INTERNAL_SERVER_ERROR,
            instructionError.getErrorCode(),
            currentReceiveQuantity.intValue(),
            maxReceiveQuantity.intValue(),
            null);
      }

      // receivedAndToBeReceivedQty reached near limit
      if ((StringUtils.isEmpty(problemTagId))
          && (currentReceiveQuantity + quantityToBeReceived > maxReceiveQuantity)
          && (!isManagerOverrideIgnoreOverage)) {
        instructionError = InstructionErrorCode.getErrorValue(ReceivingException.OVERAGE_ERROR);
        log.error(instructionError.getErrorMessage());
        throw new ReceivingException(
            instructionError.getErrorMessage(),
            HttpStatus.INTERNAL_SERVER_ERROR,
            instructionError.getErrorCode(),
            currentReceiveQuantity.intValue(),
            maxReceiveQuantity.intValue(),
            null);
      }
    }

    List<ContainerDetails> childContainers = instructionFromDB.getChildContainers();
    // getPrint label before creating container and printJob so that if there is any exception while
    // preparing labelData, containers are not created
    Map<String, Object> childCtrLabelsInfo =
        instructionPersisterService.getPrintlabeldata(
            instructionFromDB, quantityToBeReceived, receivedQuantity, childContainers);

    // create container and print jobs
    instructionPersisterService.createContainersAndPrintJobs(
        updateInstructionRequestFromClient,
        httpHeaders,
        userId,
        instructionFromDB,
        quantityToBeReceived,
        receivedQuantity,
        childContainers);

    // publish instruction to WFM
    instructionHelperService.publishInstruction(
        instructionFromDB,
        updateInstructionRequestFromClient,
        quantityToBeReceived,
        null,
        InstructionStatus.UPDATED,
        httpHeaders);

    // send instruction response
    return new InstructionResponseImplNew(null, null, instructionFromDB, childCtrLabelsInfo);
  }
}
