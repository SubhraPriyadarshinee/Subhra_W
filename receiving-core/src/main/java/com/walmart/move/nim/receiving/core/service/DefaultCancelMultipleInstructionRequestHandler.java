package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingConflictException;
import com.walmart.move.nim.receiving.core.model.MultipleCancelInstructionsRequestBody;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component(ReceivingConstants.DEFAULT_CANCEL_MULTIPLE_INST_REQ_HANDLER)
public class DefaultCancelMultipleInstructionRequestHandler
    implements CancelMultipleInstructionRequestHandler {

  @Override
  public void cancelInstructions(
      MultipleCancelInstructionsRequestBody multipleCancelInstructionsRequestBody,
      HttpHeaders headers) {
    throw new ReceivingConflictException(
        ExceptionCodes.METHOD_NOT_ALLOWED, ReceivingConstants.METHOD_NOT_ALLOWED);
  }
}
