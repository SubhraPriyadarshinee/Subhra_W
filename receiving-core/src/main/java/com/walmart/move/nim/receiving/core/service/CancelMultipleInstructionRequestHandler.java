package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.model.MultipleCancelInstructionsRequestBody;
import org.springframework.http.HttpHeaders;

public interface CancelMultipleInstructionRequestHandler {

  void cancelInstructions(
      MultipleCancelInstructionsRequestBody multipleCancelInstructionsRequestBody,
      HttpHeaders headers);
}
