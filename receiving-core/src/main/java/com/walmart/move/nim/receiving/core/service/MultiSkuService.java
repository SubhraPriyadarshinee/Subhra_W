package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.InstructionResponse;

public interface MultiSkuService {
  InstructionResponse handleMultiSku(
      Boolean isAsnMultiSkuReceivingEnabled,
      InstructionRequest instructionRequest,
      InstructionResponse instructionResponse,
      Instruction instruction);
}
