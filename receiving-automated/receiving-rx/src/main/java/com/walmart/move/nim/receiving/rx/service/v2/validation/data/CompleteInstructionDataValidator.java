package com.walmart.move.nim.receiving.rx.service.v2.validation.data;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.ItemData;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.rx.model.RxInstructionType;
import com.walmart.move.nim.receiving.rx.validators.RxInstructionValidator;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Resource;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class CompleteInstructionDataValidator extends RxInstructionValidator {

  @Resource private InstructionPersisterService instructionPersisterService;

  public Instruction validateAndGetInstruction(Long instructionId, String userId)
      throws ReceivingException {
    // get instruction and validate
    Instruction instruction = instructionPersisterService.getInstructionById(instructionId);
    validateInstructionStatus(instruction);

    // get complete user and validate
    String instructionOwner =
        StringUtils.isNotBlank(instruction.getLastChangeUserId())
            ? instruction.getLastChangeUserId()
            : instruction.getCreateUserId();
    verifyCompleteUser(instruction, instructionOwner, userId);

    return instruction;
  }

  public boolean isEpcisSmartReceivingFlow(
      Instruction instruction, DeliveryDocument deliveryDocument) {
    List<String> validCodes =
        Arrays.asList(
            RxInstructionType.RX_SER_BUILD_CONTAINER.getInstructionType(),
            RxInstructionType.RX_SER_CNTR_CASE_SCAN.getInstructionType(),
            RxInstructionType.RX_SER_CNTR_GTIN_AND_LOT.getInstructionType(),
            RxInstructionType.RX_SER_BUILD_UNITS_SCAN.getInstructionType(),
            RxInstructionType.RX_SER_MULTI_SKU_PALLET.getInstructionType());
    ItemData additionalInfo =
        deliveryDocument.getDeliveryDocumentLines().get(0).getAdditionalInfo();
    boolean isEpcisSmartReceivingEnabled = additionalInfo.getIsEpcisSmartReceivingEnabled();
    String instructionCode = instruction.getInstructionCode();
    return validCodes.contains(instructionCode) && isEpcisSmartReceivingEnabled;
  }
}
