package com.walmart.move.nim.receiving.core.common.validators;

import com.google.gson.JsonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.model.DocumentLine;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class WeightThresholdValidator {

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  private static final float DEFAULT_WEIGHT_THRESHOLD_POUNDS = 2000.0F;

  /**
   * @param deliveryDocumentLine Po Line against which we are receiving. (From Instruction table)
   * @param quantityAlreadyReceived applicable for subsequent update/receive instruction flows. It
   *     is the quantity on instruction that has already been received against this instruction and
   *     labels printed
   * @param quantityToBeReceived additional quantity to be received in this instruction
   * @param quantityUom
   */
  public void validate(
      DocumentLine deliveryDocumentLine,
      Integer quantityAlreadyReceived,
      Integer quantityToBeReceived,
      String quantityUom) {
    if (tenantSpecificConfigReader.isFeatureFlagEnabled(
        ReceivingConstants.TIHI_WEIGHT_THRESHOLD_VALIDATION_ENABLED))
      validateWeightThreshold(
          deliveryDocumentLine, quantityAlreadyReceived, quantityToBeReceived, quantityUom);
  }

  private void validateWeightThreshold(
      DocumentLine deliveryDocumentLine,
      Integer quantityAlreadyReceived,
      Integer quantityToBeReceived,
      String quantityUom) {
    float weightThreshold =
        Optional.of(
                tenantSpecificConfigReader.getCcmConfigValue(
                    TenantContext.getFacilityNum(),
                    ReceivingConstants.TIHI_WEIGHT_THRESHOLD_POUNDS))
            .orElse(new JsonParser().parse(String.valueOf(DEFAULT_WEIGHT_THRESHOLD_POUNDS)))
            .getAsFloat();

    Integer qtyAlreadyReceivedVnpk =
        ReceivingUtils.conversionToVNPK(
            quantityAlreadyReceived,
            quantityUom,
            deliveryDocumentLine.getVnpkQty(),
            deliveryDocumentLine.getWhpkQty());

    Integer qtyToBeReceivedVnpk =
        ReceivingUtils.conversionToVNPK(
            quantityToBeReceived,
            quantityUom,
            deliveryDocumentLine.getVnpkQty(),
            deliveryDocumentLine.getWhpkQty());

    float vnpkWgtInPounds =
        convertWeightToPounds(
            deliveryDocumentLine.getVnpkWgtQty(), deliveryDocumentLine.getVnpkWgtUom());

    int qtyAfterReceivingVnpk = qtyAlreadyReceivedVnpk + qtyToBeReceivedVnpk;
    float totalCasesWeightInPounds = qtyAfterReceivingVnpk * vnpkWgtInPounds;
    if (totalCasesWeightInPounds > weightThreshold) {
      float weightOverThreshold = totalCasesWeightInPounds - weightThreshold;
      int numCasesOverThreshold = (int) Math.ceil(weightOverThreshold / vnpkWgtInPounds);
      int allowedNumberOfCasesToReceive = qtyToBeReceivedVnpk - numCasesOverThreshold;
      log.error(
          "Pallet weight {} exceeded threshold {} for item: {} po: {} pol: {} for qtyAlreadyReceived: {} qtyToBeReceived: {}  Allowed number of cases to receive: {}",
          totalCasesWeightInPounds,
          weightThreshold,
          deliveryDocumentLine.getItemNumber(),
          deliveryDocumentLine.getPurchaseReferenceNumber(),
          deliveryDocumentLine.getPurchaseReferenceLineNumber(),
          qtyAlreadyReceivedVnpk,
          qtyToBeReceivedVnpk,
          allowedNumberOfCasesToReceive);
      throw new ReceivingBadDataException(
          ExceptionCodes.WEIGHT_THRESHOLD_EXCEEDED_ERROR_CODE,
          ReceivingConstants.WEIGHT_THRESHOLD_EXCEEDED_ERROR_MESSAGE,
          allowedNumberOfCasesToReceive);
    }

    // pallet weight validated, return to calling method
    log.info(
        "Pallet weight {} within threshold {} for item: {} po: {} pol: {} for qtyAlreadyReceived: {} qtyToBeReceived: {}",
        totalCasesWeightInPounds,
        weightThreshold,
        deliveryDocumentLine.getItemNumber(),
        deliveryDocumentLine.getPurchaseReferenceNumber(),
        deliveryDocumentLine.getPurchaseReferenceLineNumber(),
        qtyAlreadyReceivedVnpk,
        qtyToBeReceivedVnpk);
  }

  private float convertWeightToPounds(Float weightQty, String weightUom) {
    if (weightUom.equalsIgnoreCase(ReceivingConstants.Uom.LB)) {
      return weightQty;
    }
    // TODO; add other weight if needed, and move to utils
    throw new ReceivingBadDataException(
        ExceptionCodes.WEIGHT_UOM_INVALID_ERROR_CODE,
        ReceivingConstants.INVALID_WEIGHT_UOM_ERROR_MESSAGE,
        weightUom);
  }
}
