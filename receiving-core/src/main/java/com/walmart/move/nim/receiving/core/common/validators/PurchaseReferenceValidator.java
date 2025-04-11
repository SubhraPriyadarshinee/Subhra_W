package com.walmart.move.nim.receiving.core.common.validators;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.*;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.ADJUST_PALLET_QUANTITY_ERROR_HEADER_PO_NOT_FINALIZE;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

import com.walmart.move.nim.receiving.core.client.dcfin.DCFinRestApiClient;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.GdmError;
import com.walmart.move.nim.receiving.core.common.exception.GdmErrorCode;
import com.walmart.move.nim.receiving.core.common.exception.InstructionError;
import com.walmart.move.nim.receiving.core.common.exception.InstructionErrorCode;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.ErrorResponse;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.POLineStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class PurchaseReferenceValidator {

  private static final Logger log = LoggerFactory.getLogger(PurchaseReferenceValidator.class);

  @Autowired private ReceiptService receiptService;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Autowired private DCFinRestApiClient dcFinRestApiClient;

  /**
   * Validate PO finalization
   *
   * @param deliveryNumber
   * @param purchaseReferenceNumber
   * @throws ReceivingException
   */
  public void validatePOConfirmation(String deliveryNumber, String purchaseReferenceNumber)
      throws ReceivingException {
    if (tenantSpecificConfigReader.isPoConfirmationFlagEnabled(TenantContext.getFacilityNum())) {
      boolean poFinalized = receiptService.isPOFinalized(deliveryNumber, purchaseReferenceNumber);

      if (poFinalized) {
        GdmError gdmError = GdmErrorCode.getErrorValue(ReceivingException.PO_FINALIZED_ERROR);
        String errorMessage = String.format(gdmError.getErrorMessage(), purchaseReferenceNumber);
        log.error(errorMessage);
        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(errorMessage)
                .errorCode(gdmError.getErrorCode())
                .errorHeader(gdmError.getErrorHeader())
                .errorKey(ExceptionCodes.PO_FINALIZED_ERROR)
                .values(new Object[] {purchaseReferenceNumber})
                .build();
        throw ReceivingException.builder()
            .httpStatus(HttpStatus.CONFLICT)
            .errorResponse(errorResponse)
            .build();
      }
    }
  }

  /**
   * Check BOL weight for a given line
   *
   * @param deliveryDocumentLine
   * @throws ReceivingException
   */
  public void validateVariableWeight(DeliveryDocumentLine deliveryDocumentLine)
      throws ReceivingException {
    Float bolWeight = deliveryDocumentLine.getBolWeight();
    log.info(
        "po={} poLine={} itemNbr={} bolWeight={}",
        deliveryDocumentLine.getPurchaseReferenceNumber(),
        deliveryDocumentLine.getPurchaseReferenceLineNumber(),
        deliveryDocumentLine.getItemNbr(),
        bolWeight);

    if (Objects.isNull(bolWeight) || bolWeight == 0) {
      InstructionError instructionError =
          InstructionErrorCode.getErrorValue("INVALID_BOL_WEIGHT_ERROR");
      ErrorResponse errorResponse =
          ErrorResponse.builder()
              .errorMessage(instructionError.getErrorMessage())
              .errorCode(instructionError.getErrorCode())
              .errorHeader(instructionError.getErrorHeader())
              .errorKey(ExceptionCodes.INVALID_BOL_WEIGHT_ERROR)
              .build();
      throw ReceivingException.builder()
          .httpStatus(HttpStatus.BAD_REQUEST)
          .errorResponse(errorResponse)
          .build();
    }
  }

  /**
   * Check if we can receive as correction after PO confirmation
   *
   * @param deliveryNumber
   * @param poNumber
   * @param isProblemReceiveFlow
   * @param instructionRequest
   * @throws ReceivingException
   */
  public void validateReceiveAsCorrection(
      String deliveryNumber,
      String poNumber,
      boolean isProblemReceiveFlow,
      InstructionRequest instructionRequest)
      throws ReceivingException {
    // todo
    // honor isReceiveAsCorrection only if DCFin truly finalized
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.CHECK_DCFIN_PO_STATUS_ENABLED,
            false)
        && instructionRequest.isReceiveAsCorrection()) {

      boolean isPOFinalizedInDCFin =
          dcFinRestApiClient.isPoFinalizedInDcFin(deliveryNumber, poNumber);

      if (!isPOFinalizedInDCFin) {
        log.error(
            "PO is not Finalized in DCFin for deliveryNumber={}, poNumber={}",
            deliveryNumber,
            poNumber);
        throw new ReceivingException(
            String.format(ADJUST_PALLET_QUANTITY_ERROR_MSG_PO_NOT_FINALIZE_DCFIN, poNumber),
            BAD_REQUEST,
            ADJUST_PALLET_QUANTITY_ERROR_CODE_PO_NOT_FINALIZE,
            ADJUST_PALLET_QUANTITY_ERROR_HEADER_PO_NOT_FINALIZE);
      }
    } else {
      final boolean isPOFinalized =
          receiptService.isPOFinalized(instructionRequest.getDeliveryNumber(), poNumber);
      log.info(
          "deliveryNumber={} poNumber={} isReceiveAsCorrection={} isPOFinalized={}, isProblemReceiveFlow={}",
          deliveryNumber,
          poNumber,
          instructionRequest.isReceiveAsCorrection(),
          isPOFinalized,
          isProblemReceiveFlow);

      if (isPOFinalized) {
        if (isProblemReceiveFlow) {
          log.info("Problem Receive Flow and PO is Finalized going to Receive As Correction");
          instructionRequest.setReceiveAsCorrection(true);
        }
        if (!instructionRequest.isReceiveAsCorrection()) {
          InstructionError instructionError =
              InstructionErrorCode.getErrorValue(RCV_AS_CORRECTION_ERROR);

          String errorMessage = String.format(instructionError.getErrorMessage(), poNumber);
          throw new ReceivingException(
              errorMessage,
              HttpStatus.CONFLICT,
              instructionError.getErrorCode(),
              instructionError.getErrorHeader());
        }
      }
    }
  }

  public void checkIfPOLineNotOnBOL(
      DeliveryDocumentLine deliveryDocumentLine, boolean isReceiveAsCorrection)
      throws ReceivingException {
    if (isReceiveAsCorrection) {
      // Allow Receiving Correction if line was marked as NOT_ON_BOL
      return;
    }

    String poLineStatus =
        Objects.nonNull(deliveryDocumentLine.getOperationalInfo())
            ? deliveryDocumentLine.getOperationalInfo().getState()
            : null;
    log.info(
        "po={} poLine={} itemNbr={} poLineStatus={}",
        deliveryDocumentLine.getPurchaseReferenceNumber(),
        deliveryDocumentLine.getPurchaseReferenceLineNumber(),
        deliveryDocumentLine.getItemNbr(),
        poLineStatus);

    if (Objects.isNull(poLineStatus)
        || POLineStatus.NOT_ON_BOL
            .name()
            .equalsIgnoreCase(deliveryDocumentLine.getOperationalInfo().getState())) {
      InstructionError instructionError =
          InstructionErrorCode.getErrorValue("ITEM_NOT_ON_BOL_ERROR");
      ErrorResponse errorResponse =
          ErrorResponse.builder()
              .errorMessage(instructionError.getErrorMessage())
              .errorCode(instructionError.getErrorCode())
              .errorHeader(instructionError.getErrorHeader())
              .errorKey(ExceptionCodes.ITEM_NOT_ON_BOL_ERROR)
              .build();
      throw ReceivingException.builder()
          .httpStatus(HttpStatus.BAD_REQUEST)
          .errorResponse(errorResponse)
          .build();
    }
  }
}
