package com.walmart.move.nim.receiving.rdc.service;

import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.utils.RdcInstructionUtils;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.stubbing.Answer;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RdcCancelMultipleInstructionRequestHandlerTest {

  @Mock private InstructionPersisterService instructionPersisterService;
  @Mock private RdcInstructionUtils rdcInstructionUtils;
  @Mock private ContainerService containerService;
  @Spy private RdcReceiptBuilder rdcReceiptBuilder = new RdcReceiptBuilder();
  @Mock private RdcInstructionHelper rdcInstructionHelper;

  @InjectMocks
  private RdcCancelMultipleInstructionRequestHandler rdcCancelMultipleInstructionRequestHandler;

  private Gson gson = new Gson();

  @BeforeMethod
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testCancelInstructionsAllNonAtlasItems() throws ReceivingException {

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
    when(rdcInstructionUtils.isCancelInstructionAllowed(any(), anyString()))
        .thenReturn(Boolean.TRUE);
    doAnswer(
            (Answer<List>)
                invocation -> {
                  Long instructionId = (Long) invocation.getArguments()[0];

                  Container mockContainer = new Container();
                  mockContainer.setTrackingId("MOCK_TRACKING_ID_" + instructionId);
                  mockContainer.setInstructionId(instructionId);

                  ContainerItem mockContainerItem = new ContainerItem();
                  mockContainerItem.setTrackingId("MOCK_TRACKING_ID_" + instructionId);

                  mockContainer.setContainerItems(Arrays.asList(mockContainerItem));

                  return Arrays.asList(mockContainer);
                })
        .when(containerService)
        .getContainerByInstruction(anyLong());

    doNothing()
        .when(rdcInstructionHelper)
        .persistForCancelInstructions(anyList(), anyList(), anyList());
    when(rdcInstructionUtils.isAtlasConvertedInstruction(any(Instruction.class))).thenReturn(false);

    MultipleCancelInstructionsRequestBody mockMultipleCancelInstructionsRequestBody =
        new MultipleCancelInstructionsRequestBody();
    mockMultipleCancelInstructionsRequestBody.setInstructionIds(Arrays.asList(1l, 2l, 3l));

    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    rdcCancelMultipleInstructionRequestHandler.cancelInstructions(
        mockMultipleCancelInstructionsRequestBody, mockHttpHeaders);

    verify(instructionPersisterService, times(3)).getInstructionById(anyLong());
    verify(rdcInstructionUtils, times(3)).isCancelInstructionAllowed(any(), anyString());
    verify(containerService, times(2)).getContainerByInstruction(anyLong());
    verify(rdcReceiptBuilder, times(0)).buildReceipt(any(), anyString(), anyInt());
    verify(rdcInstructionHelper, times(1))
        .persistForCancelInstructions(anyList(), anyList(), anyList());
    verify(rdcInstructionUtils, times(2)).isAtlasConvertedInstruction(any(Instruction.class));
  }

  @Test
  public void testCancelInstructionsAllAtlasItems() throws ReceivingException {

    doAnswer(
            (Answer<Instruction>)
                invocation -> {
                  Long instructionId = (Long) invocation.getArguments()[0];

                  Instruction mockInstruction = new Instruction();

                  DeliveryDocument mockDeliveryDocument = new DeliveryDocument();
                  ItemData itemData = new ItemData();
                  itemData.setAtlasConvertedItem(true);
                  DeliveryDocumentLine mockDeliveryDocumentLine = new DeliveryDocumentLine();
                  mockDeliveryDocumentLine.setPurchaseReferenceNumber("12345");
                  mockDeliveryDocumentLine.setPurchaseReferenceLineNumber(1);
                  mockDeliveryDocumentLine.setVendorPack(10);
                  mockDeliveryDocumentLine.setWarehousePack(10);
                  mockDeliveryDocumentLine.setAdditionalInfo(itemData);
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
    when(rdcInstructionUtils.isCancelInstructionAllowed(any(), anyString()))
        .thenReturn(Boolean.TRUE);
    when(rdcInstructionUtils.isAtlasConvertedInstruction(any(Instruction.class))).thenReturn(true);
    doAnswer(
            (Answer<List>)
                invocation -> {
                  Long instructionId = (Long) invocation.getArguments()[0];

                  Container mockContainer = new Container();
                  mockContainer.setTrackingId("MOCK_TRACKING_ID_" + instructionId);
                  mockContainer.setInstructionId(instructionId);

                  ContainerItem mockContainerItem = new ContainerItem();
                  mockContainerItem.setTrackingId("MOCK_TRACKING_ID_" + instructionId);

                  mockContainer.setContainerItems(Arrays.asList(mockContainerItem));

                  return Arrays.asList(mockContainer);
                })
        .when(containerService)
        .getContainerByInstruction(anyLong());

    doNothing()
        .when(rdcInstructionHelper)
        .persistForCancelInstructions(anyList(), anyList(), anyList());

    MultipleCancelInstructionsRequestBody mockMultipleCancelInstructionsRequestBody =
        new MultipleCancelInstructionsRequestBody();
    mockMultipleCancelInstructionsRequestBody.setInstructionIds(Arrays.asList(1l, 2l, 3l));

    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    rdcCancelMultipleInstructionRequestHandler.cancelInstructions(
        mockMultipleCancelInstructionsRequestBody, mockHttpHeaders);

    verify(instructionPersisterService, times(3)).getInstructionById(anyLong());
    verify(rdcInstructionUtils, times(3)).isCancelInstructionAllowed(any(), anyString());
    verify(containerService, times(2)).getContainerByInstruction(anyLong());
    verify(rdcReceiptBuilder, times(2)).buildReceipt(any(), anyString(), anyInt());
    verify(rdcInstructionHelper, times(1))
        .persistForCancelInstructions(anyList(), anyList(), anyList());
    verify(rdcInstructionUtils, times(2)).isAtlasConvertedInstruction(any(Instruction.class));
  }
}
