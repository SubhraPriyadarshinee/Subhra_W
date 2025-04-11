package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.BulkCompleteInstructionRequest;
import com.walmart.move.nim.receiving.core.model.CompleteMultipleInstructionResponse;
import org.springframework.http.HttpHeaders;

public interface CompleteMultipleInstructionRequestHandler {
  CompleteMultipleInstructionResponse complete(
      BulkCompleteInstructionRequest bulkCompleteInstructionRequest, HttpHeaders headers)
      throws ReceivingException;
}
