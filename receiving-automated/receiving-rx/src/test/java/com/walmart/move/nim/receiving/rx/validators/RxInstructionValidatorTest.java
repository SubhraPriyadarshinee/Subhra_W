package com.walmart.move.nim.receiving.rx.validators;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.rx.mock.MockInstruction;
import com.walmart.move.nim.receiving.rx.service.RxInstructionService;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RxInstructionValidatorTest {

  @Mock private RxInstructionService rxInstructionService;

  @InjectMocks private RxInstructionValidator rxInstructionValidator;

  @BeforeMethod
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testValidateInstructionStatus() throws ReceivingException {

    doThrow(
            new ReceivingException(
                "MOCK_ERROR_MESSAGE",
                HttpStatus.INTERNAL_SERVER_ERROR,
                ReceivingException.COMPLETE_INSTRUCTION_ERROR_CODE_FOR_ALREADY_COMPLETED,
                "MOCK_ERROR_HEADER"))
        .when(rxInstructionService)
        .validateInstructionCompleted(any(Instruction.class));

    try {
      rxInstructionValidator.validateInstructionStatus(new Instruction());
    } catch (ReceivingBadDataException receivingBadDataException) {
      assertEquals(
          ExceptionCodes.INSTRUCTION_COMPLETE_ALREADY, receivingBadDataException.getErrorCode());
    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }

    verify(rxInstructionService, times(1)).validateInstructionCompleted(any(Instruction.class));
  }

  @Test
  public void testVerifyCompleteUser() {

    try {
      rxInstructionValidator.verifyCompleteUser(
          MockInstruction.getCreatedInstruction(), "sysadmin", "MOCK_TEST_USER");
    } catch (ReceivingBadDataException receivingBadDataException) {
      assertEquals(
          ExceptionCodes.INSTRUCTION_MULTI_USER_ERROR_MESSAGE,
          receivingBadDataException.getErrorCode());
    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }
}
