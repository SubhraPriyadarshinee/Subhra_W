package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.InstructionSummary;
import com.walmart.move.nim.receiving.core.model.MultipleTransferInstructionsRequestBody;
import com.walmart.move.nim.receiving.core.model.TransferInstructionRequest;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class TransferInstructionServiceTest {

  @InjectMocks private InstructionService instructionService;
  @InjectMocks private InstructionPersisterService instructionPersisterService;
  @Mock protected InstructionHelperService instructionHelperService;
  @Mock private InstructionRepository instructionRepository;
  private List<Instruction> instructionList = new ArrayList<>();
  private TransferInstructionRequest transferInstructionRequest;
  private HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
  private String transferUser = "utuser";
  private Gson gson = new Gson();

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);

    ReflectionTestUtils.setField(instructionService, "gson", gson);
    ReflectionTestUtils.setField(
        instructionService, "instructionPersisterService", instructionPersisterService);

    httpHeaders.set(ReceivingConstants.USER_ID_HEADER_KEY, transferUser);

    transferInstructionRequest = new TransferInstructionRequest();
    transferInstructionRequest.setDeliveryNumber(Long.valueOf("21119003"));

    Instruction instruction1 = new Instruction();
    instruction1.setId(Long.valueOf("1"));
    instruction1.setContainer(null);
    instruction1.setChildContainers(null);
    instruction1.setCreateTs(new Date());
    instruction1.setCreateUserId("sysadmin");
    instruction1.setLastChangeTs(new Date());
    instruction1.setLastChangeUserId("sysadmin");
    instruction1.setDeliveryNumber(Long.valueOf("21119003"));
    instruction1.setGtin("00000943037204");
    instruction1.setInstructionCode("Build Container");
    instruction1.setInstructionMsg("Build the Container");
    instruction1.setItemDescription("HEM VALUE PACK (5)");
    instruction1.setMessageId("58e1df00-ebf6-11e8-9c25-dd4bfc2b96f8");
    instruction1.setMove(null);
    instruction1.setPoDcNumber("32899");
    instruction1.setPrintChildContainerLabels(false);
    instruction1.setPurchaseReferenceNumber("9763140005");
    instruction1.setPurchaseReferenceLineNumber(1);
    instruction1.setProjectedReceiveQty(2);
    instruction1.setProviderId("DA");
    instruction1.setFirstExpiryFirstOut(Boolean.FALSE);
    instructionList.add(instruction1);

    Instruction instruction2 = new Instruction();
    instruction2.setId(Long.valueOf("2"));
    instruction2.setContainer(null);
    instruction2.setChildContainers(null);
    instruction2.setCreateTs(new Date());
    instruction2.setCreateUserId("sysadmin");
    instruction2.setCompleteTs(new Date());
    instruction2.setCompleteUserId("sysadmin");
    instruction2.setDeliveryNumber(Long.valueOf("21119003"));
    instruction2.setGtin("00000943037194");
    instruction2.setInstructionCode("Build Container");
    instruction2.setInstructionMsg("Build the Container");
    instruction2.setItemDescription("HEM VALUE PACK (4)");
    instruction2.setMessageId("58e1df00-ebf6-11e8-9c25-dd4bfc2a06f8");
    instruction2.setMove(null);
    instruction2.setPoDcNumber("32899");
    instruction2.setPrintChildContainerLabels(false);
    instruction2.setPurchaseReferenceNumber("9763140004");
    instruction2.setPurchaseReferenceLineNumber(1);
    instruction2.setProjectedReceiveQty(2);
    instruction2.setProviderId("DA");
    instruction2.setReceivedQuantity(2);
    instruction2.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
    instruction2.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);
    instruction2.setFirstExpiryFirstOut(Boolean.TRUE);
    instructionList.add(instruction2);
  }

  @AfterMethod
  public void restRestUtilCalls() {

    reset(instructionRepository, instructionHelperService);
  }

  @Test
  public void testTransferInstructionsNoInstructionsAvailableForProvidedUser() {
    transferInstructionRequest.setUserIds(Arrays.asList("sysadmin"));
    List<Instruction> instructions = new ArrayList<>();
    when(instructionRepository.findByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(
            anyLong()))
        .thenReturn(instructions);

    int exceptionCount = 0;
    try {
      List<InstructionSummary> instruction =
          instructionService.transferInstructions(transferInstructionRequest, httpHeaders);
    } catch (ReceivingException e) {
      exceptionCount++;
      assertEquals(
          e.getErrorResponse().getErrorMessage(),
          String.format(
              ReceivingException.NO_TRANSFERRABLE_INSTRUCTIONS,
              transferInstructionRequest.getUserIds(),
              transferInstructionRequest.getDeliveryNumber()));
    }

    assertEquals(exceptionCount, 1);
  }

  @Test
  public void testTransferInstructions() {
    transferInstructionRequest.setUserIds(Arrays.asList("sysadmin"));

    when(instructionRepository.findByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(
            anyLong()))
        .thenReturn(instructionList);
    when(instructionRepository.saveAll(instructionList)).thenReturn(instructionList);
    when(instructionRepository.findByDeliveryNumber(anyLong())).thenReturn(instructionList);

    try {
      List<InstructionSummary> instructions =
          instructionService.transferInstructions(transferInstructionRequest, httpHeaders);
      verify(instructionRepository, times(1))
          .findByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(
              Long.valueOf("21119003"));
      verify(instructionRepository, times(1)).saveAll(instructionList);
      verify(instructionRepository, times(1))
          .findByDeliveryNumberAndInstructionCodeIsNotNull(Long.valueOf("21119003"));

    } catch (ReceivingException e) {
      assertTrue(false);
    }
  }

  @Test
  public void test_transferInstructionsMultiple() {

    MultipleTransferInstructionsRequestBody mockMultiTransferInstrRequestBody =
        new MultipleTransferInstructionsRequestBody();
    mockMultiTransferInstrRequestBody.setInstructionId(Arrays.asList(1234l));

    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();

    doNothing().when(instructionHelperService).transferInstructions(anyList(), anyString());

    instructionService.transferInstructionsMultiple(
        mockMultiTransferInstrRequestBody, mockHttpHeaders);

    verify(instructionHelperService, times(1)).transferInstructions(anyList(), anyString());
  }

  @Test
  public void test_transferInstructionsMultiple_invalid_userId() {

    try {
      MultipleTransferInstructionsRequestBody mockMultiTransferInstrRequestBody =
          new MultipleTransferInstructionsRequestBody();
      mockMultiTransferInstrRequestBody.setInstructionId(Arrays.asList(1234l));
      HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
      mockHttpHeaders.remove(ReceivingConstants.USER_ID_HEADER_KEY);

      doNothing()
          .when(instructionRepository)
          .updateLastChangeUserIdAndLastChangeTs(anyList(), anyString());

      instructionService.transferInstructionsMultiple(
          mockMultiTransferInstrRequestBody, mockHttpHeaders);
    } catch (ReceivingBadDataException e) {

      assertEquals(e.getErrorCode(), ExceptionCodes.INVALID_INPUT_USERID);
      assertEquals(e.getMessage(), ReceivingConstants.INVALID_INPUT_USERID);
    }
  }

  @Test
  public void test_transferInstructionsMultiple_empty_instructionIds() {

    try {
      MultipleTransferInstructionsRequestBody mockMultiTransferInstrRequestBody =
          new MultipleTransferInstructionsRequestBody();
      HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
      mockHttpHeaders.remove(ReceivingConstants.USER_ID_HEADER_KEY);

      doNothing()
          .when(instructionRepository)
          .updateLastChangeUserIdAndLastChangeTs(anyList(), anyString());

      instructionService.transferInstructionsMultiple(
          mockMultiTransferInstrRequestBody, mockHttpHeaders);
    } catch (ReceivingBadDataException e) {

      assertEquals(e.getErrorCode(), ExceptionCodes.INVALID_INPUT_INSTRUCTION_IDS);
      assertEquals(e.getMessage(), ReceivingConstants.INVALID_INPUT_INSTRUCTION_IDS);
    }
  }

  @Test
  public void test_transferInstructionsMultiple_completed_instructions_error() {

    MultipleTransferInstructionsRequestBody mockMultiTransferInstrRequestBody =
        new MultipleTransferInstructionsRequestBody();
    mockMultiTransferInstrRequestBody.setInstructionId(Arrays.asList(1234l));

    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();

    List<Instruction> modifiedList = new ArrayList<>();
    Instruction modifiedInstruction = instructionList.get(0);
    modifiedInstruction.setCompleteTs(new Date());
    modifiedList.add(modifiedInstruction);
    modifiedInstruction = instructionList.get(1);
    modifiedInstruction.setCompleteTs(new Date());
    modifiedList.add(modifiedInstruction);

    doReturn(modifiedList).when(instructionRepository).findByIdIn(anyList());
    doNothing().when(instructionHelperService).transferInstructions(anyList(), anyString());

    try {
      instructionService.transferInstructionsMultiple(
          mockMultiTransferInstrRequestBody, mockHttpHeaders);
    } catch (ReceivingBadDataException exception) {
      assertEquals(exception.getErrorCode(), ExceptionCodes.INVALID_INSTRUCTION_STATE);
      assertEquals(exception.getDescription(), ReceivingConstants.INVALID_INSTRUCTION_STATE);
    }
    verify(instructionRepository, times(1)).findByIdIn(anyList());
    verify(instructionHelperService, times(0)).transferInstructions(anyList(), anyString());
  }
}
