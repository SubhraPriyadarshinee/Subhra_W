package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.InstructionResponse;
import org.springframework.http.HttpHeaders;

public interface RefreshInstructionHandler {
  InstructionResponse refreshInstruction(Instruction instructionId, HttpHeaders httpHeaders)
      throws ReceivingException;
}
