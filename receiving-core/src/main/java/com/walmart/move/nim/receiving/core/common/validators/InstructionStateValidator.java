package com.walmart.move.nim.receiving.core.common.validators;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.*;
import static java.util.Objects.nonNull;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class InstructionStateValidator {

  private static final Logger log = LoggerFactory.getLogger(InstructionStateValidator.class);

  public void validate(Instruction instruction4mDB) throws ReceivingException {
    validate(
        instruction4mDB,
        COMPLETE_INSTRUCTION_ERROR_CODE_FOR_ALREADY_COMPLETED,
        INTERNAL_SERVER_ERROR);
  }

  public void validate(Instruction instruction, String errorCode, HttpStatus httpStatus)
      throws ReceivingException {
    if (nonNull(instruction.getCompleteTs())) {
      final int receivedQuantity = instruction.getReceivedQuantity();
      log.error(
          "Invalid cancel request for InstructionId={} receivedQuantity={} {}",
          instruction.getId(),
          receivedQuantity,
          errorCode);
      if (receivedQuantity == 0) {
        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(
                    String.format(
                        COMPLETE_INSTRUCTION_PALLET_CANCELLED, instruction.getCompleteUserId()))
                .errorCode(errorCode)
                .errorHeader(ERROR_HEADER_PALLET_CANCELLED)
                .errorKey(ExceptionCodes.COMPLETE_INSTRUCTION_PALLET_CANCELLED)
                .values(new Object[] {instruction.getCompleteUserId()})
                .build();
        throw ReceivingException.builder()
            .httpStatus(httpStatus)
            .errorResponse(errorResponse)
            .build();
      } else {
        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(
                    String.format(
                        String.format(
                            COMPLETE_INSTRUCTION_ALREADY_COMPLETE,
                            instruction.getCompleteUserId())))
                .errorCode(errorCode)
                .errorHeader(ERROR_HEADER_PALLET_COMPLETED)
                .errorKey(ExceptionCodes.COMPLETE_INSTRUCTION_ALREADY_COMPLETE)
                .values(new Object[] {instruction.getCompleteUserId()})
                .build();
        throw ReceivingException.builder()
            .httpStatus(httpStatus)
            .errorResponse(errorResponse)
            .build();
      }
    }
  }
}
