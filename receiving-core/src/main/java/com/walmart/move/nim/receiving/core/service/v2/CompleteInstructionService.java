package com.walmart.move.nim.receiving.core.service.v2;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.CompleteInstructionRequest;
import com.walmart.move.nim.receiving.core.model.InstructionResponse;
import org.springframework.http.HttpHeaders;

public interface CompleteInstructionService {
  InstructionResponse completeInstruction(
      Long instructionId,
      CompleteInstructionRequest completeInstructionRequest,
      HttpHeaders httpHeaders)
      throws ReceivingException;
}
