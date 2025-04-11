package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.InstructionResponse;
import com.walmart.move.nim.receiving.core.model.UpdateInstructionRequest;
import org.springframework.http.HttpHeaders;

public interface UpdateInstructionHandler {

  InstructionResponse updateInstruction(
      Long instructionId,
      UpdateInstructionRequest instructionUpdateRequestFromClient,
      String parentTrackingId,
      HttpHeaders httpHeaders)
      throws ReceivingException;
}
