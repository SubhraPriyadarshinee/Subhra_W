package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.InstructionResponse;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.springframework.stereotype.Component;

@Component(value = ReceivingConstants.DEFAULT_MULTI_SKU_HANDLER)
public class DefaultMultiSkuHandler implements MultiSkuService {

  public InstructionResponse handleMultiSku(
      Boolean isAsnMultiSkuReceivingEnabled,
      InstructionRequest instructionRequest,
      InstructionResponse instructionResponse,
      Instruction instruction) {
    if (InstructionUtils.isInstructionRequestSsccOrLpn(instructionRequest)) {
      if (isAsnMultiSkuReceivingEnabled) {
        instruction.setInstructionCode(ReceivingConstants.MULTI_SKU_INST_CODE);
        instruction.setInstructionMsg(ReceivingConstants.MULTI_SKU_INST_MSG);
        instructionResponse.setInstruction(instruction);
      } else {
        throw new ReceivingBadDataException(
            ExceptionCodes.MULTI_SKU_PALLET, ReceivingConstants.MULTI_SKU_PALLET);
      }
    }
    return instructionResponse;
  }
}
