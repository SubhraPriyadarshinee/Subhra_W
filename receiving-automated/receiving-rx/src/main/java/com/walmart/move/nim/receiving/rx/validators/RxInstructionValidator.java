package com.walmart.move.nim.receiving.rx.validators;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.rx.service.RxInstructionService;
import com.walmart.move.nim.receiving.utils.constants.RequestType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** @author v0k00fe */
@Component
public class RxInstructionValidator {
  private static final Logger LOG = LoggerFactory.getLogger(RxInstructionValidator.class);

  @Autowired private RxInstructionService rxInstructionService;

  public void validateInstructionStatus(Instruction instructionFromDB) {
    try {
      rxInstructionService.validateInstructionCompleted(instructionFromDB);
    } catch (ReceivingException e) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INSTRUCTION_COMPLETE_ALREADY,
          e.getMessage(),
          instructionFromDB.getCompleteUserId());
    }
  }

  public void verifyCompleteUser(
      Instruction instructionFromDB, String instructionOwner, String currentUserId) {
    try {
      ReceivingUtils.verifyUser(instructionFromDB, currentUserId, RequestType.COMPLETE);
    } catch (ReceivingException e) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INSTRUCTION_MULTI_USER_ERROR_MESSAGE,
          e.getMessage(),
          new Object[] {instructionOwner});
    }
  }
}
