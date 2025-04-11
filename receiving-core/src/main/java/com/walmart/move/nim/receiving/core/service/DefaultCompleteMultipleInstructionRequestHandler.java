package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingConflictException;
import com.walmart.move.nim.receiving.core.model.BulkCompleteInstructionRequest;
import com.walmart.move.nim.receiving.core.model.CompleteMultipleInstructionResponse;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/** @author v0k00fe */
@Component(ReceivingConstants.DEFAULT_COMPLETE_MULTIPLE_INSTR_REQ_HANDLER)
public class DefaultCompleteMultipleInstructionRequestHandler
    implements CompleteMultipleInstructionRequestHandler {

  @Override
  public CompleteMultipleInstructionResponse complete(
      BulkCompleteInstructionRequest bulkCompleteInstructionRequest, HttpHeaders headers)
      throws ReceivingException {
    throw new ReceivingConflictException(
        ExceptionCodes.METHOD_NOT_ALLOWED, ReceivingConstants.METHOD_NOT_ALLOWED);
  }
}
