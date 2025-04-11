package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.exception.ReceivingConflictException;
import com.walmart.move.nim.receiving.core.model.MultipleCancelInstructionsRequestBody;
import java.util.Arrays;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.Test;

public class DefaultCancelMultipleInstructionRequestHandlerTest {

  @Test(expectedExceptions = ReceivingConflictException.class)
  public void test_cancelInstructions() {
    MultipleCancelInstructionsRequestBody mockMultipleCancelInstructionsRequestBody =
        new MultipleCancelInstructionsRequestBody();
    mockMultipleCancelInstructionsRequestBody.setInstructionIds(Arrays.asList(12345l));

    HttpHeaders mockHeaders = new HttpHeaders();

    DefaultCancelMultipleInstructionRequestHandler defaultCancelMultipleInstructionRequestHandler =
        new DefaultCancelMultipleInstructionRequestHandler();
    defaultCancelMultipleInstructionRequestHandler.cancelInstructions(
        mockMultipleCancelInstructionsRequestBody, mockHeaders);
  }
}
