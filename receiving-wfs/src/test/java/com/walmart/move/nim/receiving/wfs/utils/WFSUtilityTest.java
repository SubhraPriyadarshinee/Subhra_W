package com.walmart.move.nim.receiving.wfs.utils;

import static com.walmart.move.nim.receiving.wfs.WFSTestUtils.getJSONStringResponse;
import static org.testng.AssertJUnit.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.InstructionResponse;
import com.walmart.move.nim.receiving.core.model.InstructionResponseImplNew;
import com.walmart.move.nim.receiving.wfs.constants.WFSConstants;
import org.junit.Test;

public class WFSUtilityTest {

  String instructionRequestPayloadExceedingThresholdFilePath =
      "src/test/resources/InstructionRequestOverage.json";
  String instructionRequestPayloadNotExceedingThresholdFilePath =
      "src/test/resources/InstructionRequestNotOverage.json";
  String instructionRequestPayloadExceedingThresholdMobileFilePath =
      "src/test/resources/InstructionRequestOverageMobile.json";
  String instructionRequestPayloadNotExceedingThresholdMobileFilePath =
      "src/test/resources/InstructionRequestNotOverageMobile.json";

  @Test
  public void testIsExceedingOverageThreshold() {
    String instructionRequestPayloadExceedingThresholdString =
        getJSONStringResponse(instructionRequestPayloadExceedingThresholdFilePath);
    String instructionRequestPayloadNotExceedingThresholdString =
        getJSONStringResponse(instructionRequestPayloadNotExceedingThresholdFilePath);
    String instructionRequestPayloadExceedingThresholdMobileString =
        getJSONStringResponse(instructionRequestPayloadExceedingThresholdMobileFilePath);
    String instructionRequestPayloadNotExceedingThresholdMobileString =
        getJSONStringResponse(instructionRequestPayloadNotExceedingThresholdMobileFilePath);

    Gson gson = new Gson();
    InstructionRequest instructionRequestPayloadExceedingThreshold =
        gson.fromJson(instructionRequestPayloadExceedingThresholdString, InstructionRequest.class);
    InstructionRequest instructionRequestPayloadNotExceedingThreshold =
        gson.fromJson(
            instructionRequestPayloadNotExceedingThresholdString, InstructionRequest.class);
    InstructionRequest instructionRequestPayloadExceedingThresholdMobile =
        gson.fromJson(
            instructionRequestPayloadExceedingThresholdMobileString, InstructionRequest.class);
    InstructionRequest instructionRequestPayloadNotExceedingThresholdMobile =
        gson.fromJson(
            instructionRequestPayloadNotExceedingThresholdMobileString, InstructionRequest.class);

    assertTrue(
        WFSUtility.isExceedingOverageThreshold(instructionRequestPayloadExceedingThreshold, false));
    assertTrue(
        WFSUtility.isExceedingOverageThreshold(
            instructionRequestPayloadExceedingThresholdMobile, true));
    assertFalse(
        WFSUtility.isExceedingOverageThreshold(
            instructionRequestPayloadNotExceedingThreshold, false));
    assertFalse(
        WFSUtility.isExceedingOverageThreshold(
            instructionRequestPayloadNotExceedingThresholdMobile, true));
  }

  @Test
  public void testCreateInstructionResponseForOverageReceiving() {
    String instructionRequestPayloadExceedingThresholdString =
        getJSONStringResponse(instructionRequestPayloadExceedingThresholdFilePath);
    Gson gson = new Gson();
    InstructionRequest instructionRequestPayloadExceedingThreshold =
        gson.fromJson(instructionRequestPayloadExceedingThresholdString, InstructionRequest.class);

    InstructionResponse expectedInstructionResponse = new InstructionResponseImplNew();
    Instruction expectedInstruction = new Instruction();
    expectedInstruction.setInstructionCode(WFSConstants.OVERAGE_RECEIVING_INSTRUCTION_CODE);
    expectedInstruction.setInstructionMsg(WFSConstants.OVERAGE_RECEIVING_INSTRUCTION_CODE);
    expectedInstructionResponse.setInstruction(expectedInstruction);
    expectedInstructionResponse.setDeliveryStatus(
        instructionRequestPayloadExceedingThreshold.getDeliveryStatus());
    expectedInstructionResponse.setDeliveryDocuments(
        instructionRequestPayloadExceedingThreshold.getDeliveryDocuments());

    assertEquals(
        expectedInstructionResponse.getInstruction().getInstructionCode(),
        WFSUtility.createInstructionResponseForOverageReceiving(
                instructionRequestPayloadExceedingThreshold)
            .getInstruction()
            .getInstructionCode());
  }
}
