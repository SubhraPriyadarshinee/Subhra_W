package com.walmart.move.nim.receiving.core.common.validators;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.COMPLETE_INSTRUCTION_ERROR_CODE_FOR_ALREADY_COMPLETED;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.testng.Assert.*;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.mock.data.MockInstruction;
import com.walmart.move.nim.receiving.core.model.ErrorResponse;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.Date;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/** Tests to validate Instruction State */
public class InstructionStateValidatorTest extends ReceivingTestBase {

  @InjectMocks private InstructionStateValidator instructionStateValidator;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  private Long instructionId = Long.valueOf("297252");
  private Instruction instruction = MockInstruction.getInstruction();

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);

    TenantContext.setFacilityNum(32612);
    TenantContext.setFacilityCountryCode("US");
  }

  @BeforeMethod
  public void setUpTestData() {
    instruction.setId(instructionId);
    instruction.setCompleteUserId("sysadmin");
    instruction.setCompleteTs(new Date());
  }

  @AfterMethod
  public void tearDown() {}

  @Test
  public void testValidate_default() {
    try {
      instructionStateValidator.validate(instruction);
      fail();
    } catch (ReceivingException re) {
      final ErrorResponse er = re.getErrorResponse();
      assertEquals(er.getErrorCode(), COMPLETE_INSTRUCTION_ERROR_CODE_FOR_ALREADY_COMPLETED);
      assertEquals(re.getHttpStatus(), INTERNAL_SERVER_ERROR);
      assertEquals(
          re.getMessage(),
          "This pallet was completed by sysadmin, please start a new pallet to continue receiving.");
    }
  }

  @Test
  public void testValidate_v2_customErrorCodeAndHttpStatus() {
    String errorCode = "myNewUniqueCode-123";
    HttpStatus badRequest = HttpStatus.BAD_REQUEST;
    try {
      instructionStateValidator.validate(instruction, errorCode, badRequest);
      fail();
    } catch (ReceivingException re) {
      final ErrorResponse er = re.getErrorResponse();
      assertEquals(er.getErrorCode(), errorCode);
      assertEquals(re.getHttpStatus(), badRequest);
      assertEquals(
          re.getMessage(),
          "This pallet was completed by sysadmin, please start a new pallet to continue receiving.");
    }
  }
}
