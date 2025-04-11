package com.walmart.move.nim.receiving.wfs.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;
import static org.testng.Assert.assertSame;
import static org.testng.AssertJUnit.fail;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Arrays;
import java.util.Date;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class WFSCancelMultipleInstructionRequestHandlerTest {
  @Mock private InstructionPersisterService instructionPersisterService;
  @Mock private WFSInstructionUtils wfsInstructionUtils;

  @InjectMocks
  private WFSCancelMultipleInstructionRequestHandler wfsCancelMultipleInstructionRequestHandler;

  @Mock private JmsPublisher jmsPublisher;
  private Gson gson = new Gson();

  @BeforeMethod
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testWFSCancelInstructions() throws ReceivingException {

    doAnswer(
            (Answer<Instruction>)
                invocation -> {
                  Long instructionId = (Long) invocation.getArguments()[0];

                  Instruction mockInstruction = new Instruction();

                  DeliveryDocument mockDeliveryDocument = new DeliveryDocument();
                  DeliveryDocumentLine mockDeliveryDocumentLine = new DeliveryDocumentLine();
                  mockDeliveryDocumentLine.setPurchaseReferenceNumber("12345");
                  mockDeliveryDocumentLine.setPurchaseReferenceLineNumber(1);
                  mockDeliveryDocumentLine.setVendorPack(10);
                  mockDeliveryDocumentLine.setWarehousePack(10);
                  mockDeliveryDocumentLine.setAdditionalInfo(new ItemData());
                  mockDeliveryDocument.setDeliveryDocumentLines(
                      Arrays.asList(mockDeliveryDocumentLine));
                  mockInstruction.setDeliveryDocument(gson.toJson(mockDeliveryDocument));

                  mockInstruction.setInstructionSetId(instructionId);
                  if (instructionId != 3) {
                    mockInstruction.setReceivedQuantity(10);
                    mockInstruction.setContainer(new ContainerDetails());
                  }
                  mockInstruction.setCreateUserId("sysadmin");
                  mockInstruction.setCreateTs(new Date());

                  LinkedTreeMap<String, Object> moveMap = new LinkedTreeMap<>();
                  moveMap.put(ReceivingConstants.MOVE_FROM_LOCATION, 123);
                  mockInstruction.setMove(moveMap);

                  return mockInstruction;
                })
        .when(instructionPersisterService)
        .getInstructionById(anyLong());

    when(wfsInstructionUtils.isCancelInstructionAllowed(any())).thenReturn(Boolean.TRUE);

    doNothing().when(wfsInstructionUtils).persistForCancelInstructions(anyList());

    doNothing().when(jmsPublisher).publish(anyString(), any(), anyBoolean());

    MultipleCancelInstructionsRequestBody mockMultipleCancelInstructionsRequestBody =
        new MultipleCancelInstructionsRequestBody();
    mockMultipleCancelInstructionsRequestBody.setInstructionIds(Arrays.asList(1l, 2l, 3l));

    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    wfsCancelMultipleInstructionRequestHandler.cancelInstructions(
        mockMultipleCancelInstructionsRequestBody, mockHttpHeaders);

    verify(instructionPersisterService, times(3)).getInstructionById(anyLong());
    verify(wfsInstructionUtils, times(3)).isCancelInstructionAllowed(any());
    verify(wfsInstructionUtils, times(1)).persistForCancelInstructions(anyList());
  }

  @Test
  public void testWFSCancelInstructions_fails() throws ReceivingException {
    doThrow(new ReceivingException("failed to getInstructionById", HttpStatus.BAD_REQUEST))
        .when(instructionPersisterService)
        .getInstructionById(anyLong());

    when(wfsInstructionUtils.isCancelInstructionAllowed(any())).thenReturn(Boolean.FALSE);

    doNothing().when(wfsInstructionUtils).persistForCancelInstructions(anyList());

    doNothing().when(jmsPublisher).publish(anyString(), any(), anyBoolean());

    MultipleCancelInstructionsRequestBody mockMultipleCancelInstructionsRequestBody =
        new MultipleCancelInstructionsRequestBody();
    mockMultipleCancelInstructionsRequestBody.setInstructionIds(Arrays.asList(1l, 2l, 3l));

    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    try {
      wfsCancelMultipleInstructionRequestHandler.cancelInstructions(
          mockMultipleCancelInstructionsRequestBody, mockHttpHeaders);
      fail("ReceivingBadDataException should be thrown");
    } catch (ReceivingBadDataException rbde) {
      assertSame(rbde.getErrorCode(), ExceptionCodes.CANCEL_INSTRUCTION_ERROR_MSG);
      assertSame(rbde.getDescription(), "failed to getInstructionById");
    }
  }
}
