package com.walmart.move.nim.receiving.rdc.service;

import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.PublishInstructionSummary;
import com.walmart.move.nim.receiving.core.model.UpdateInstructionRequest;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.mock.data.MockDeliveryDocuments;
import com.walmart.move.nim.receiving.rdc.mock.data.MockInstructionResponse;
import com.walmart.move.nim.receiving.rdc.model.wft.RdcInstructionType;
import com.walmart.move.nim.receiving.rdc.model.wft.WFTInstruction;
import com.walmart.move.nim.receiving.rdc.utils.RdcContainerUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcInstructionUtils;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.IOException;
import java.util.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RdcInstructionHelperTest {
  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private InstructionPersisterService instructionPersisterService;
  @Mock private ReceiptService receiptService;
  @Mock private RdcContainerUtils rdcContainerUtils;
  @InjectMocks private RdcInstructionHelper rdcInstructionHelper;
  @Mock private RdcInstructionUtils rdcInstructionUtils;
  @Mock private InstructionHelperService instructionHelperService;

  private String slotId = "A0001";

  @BeforeMethod
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testBuildContainerAndContainerItem() throws IOException {

    Instruction mockInstruction = MockInstructionResponse.getMockInstruction();
    DeliveryDocument deliveryDocument =
        new Gson().fromJson(mockInstruction.getDeliveryDocument(), DeliveryDocument.class);
    deliveryDocument.getDeliveryDocumentLines().get(0).getAdditionalInfo().setPrimeSlot(slotId);
    UpdateInstructionRequest mockUpdateInstructionRequest = new UpdateInstructionRequest();

    when(rdcContainerUtils.buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class)))
        .thenReturn(getMockContainerItem());
    when(rdcContainerUtils.buildContainer(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            anyString(),
            any()))
        .thenReturn(getMockContainer());

    Container result =
        rdcInstructionHelper.buildContainerAndContainerItem(
            mockInstruction,
            deliveryDocument,
            mockUpdateInstructionRequest,
            Integer.valueOf(0),
            mockInstruction.getCreateUserId(),
            "MOCK_TRACKING_ID");

    Assert.assertEquals(result.getCreateUser(), mockInstruction.getCreateUserId());
    Assert.assertEquals(result.getTrackingId(), "MOCK_TRACKING_ID");

    verify(rdcContainerUtils, times(1))
        .buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(DeliveryDocument.class),
            anyString(),
            anyString(),
            anyString(),
            any());
  }

  @Test
  public void testPersistForUpdateInstruction() {

    when(containerPersisterService.saveContainer(any())).thenReturn(new Container());
    when(instructionPersisterService.saveInstruction(any())).thenReturn(new Instruction());
    when(receiptService.saveAll(any())).thenReturn(Arrays.<Receipt>asList(new Receipt()));

    rdcInstructionHelper.persistForUpdateInstruction(
        new Instruction(), new Container(), Arrays.<Receipt>asList(new Receipt()));

    verify(containerPersisterService, times(1)).saveContainer(any());
    verify(instructionPersisterService, times(1)).saveInstruction(any());
    verify(receiptService, times(1)).saveAll(any());
  }

  @Test
  public void testPublishAutoReceiveInstructionToWFT() throws IOException {
    Instruction instruction = new Instruction();
    instruction.setMessageId("2323-23323=2323");
    instruction.setPrintChildContainerLabels(false);
    instruction.setInstructionMsg(RdcInstructionType.FLIB_SSTK_CASE_RECEIVING.getInstructionMsg());
    instruction.setInstructionCode(
        RdcInstructionType.FLIB_SSTK_CASE_RECEIVING.getInstructionCode());
    instruction.setActivityName(WFTInstruction.SSTK.getActivityName());
    instruction.setReceivedQuantity(1);
    String lpn = "a06020343423212132323";
    when(rdcInstructionUtils.prepareInstructionMessage(
            any(Instruction.class), anyInt(), any(HttpHeaders.class), anyInt(), anyInt()))
        .thenReturn(new PublishInstructionSummary());
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
    rdcInstructionHelper.publishInstruction(
        instruction,
        MockHttpHeaders.getHeaders(),
        lpn,
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0));
    verify(rdcInstructionUtils, times(1))
        .prepareInstructionMessage(
            any(Instruction.class), anyInt(), any(HttpHeaders.class), anyInt(), anyInt());
    verify(instructionHelperService, times(1))
        .publishInstruction(any(HttpHeaders.class), any(PublishInstructionSummary.class));
  }

  private List<ContainerItem> getMockContainerItem() {
    ContainerItem containerItem = new ContainerItem();
    containerItem.setTrackingId("MOCK_TRACKING_ID");
    containerItem.setPurchaseReferenceNumber("PO123");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setQuantity(20);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem.setItemNumber(123456L);
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(ReceivingConstants.IS_ATLAS_CONVERTED_ITEM, "true");
    containerItem.setContainerItemMiscInfo(containerItemMiscInfo);
    return Collections.singletonList(containerItem);
  }

  private Container getMockContainer() {
    Container container = new Container();
    container.setTrackingId("MOCK_TRACKING_ID");
    container.setInstructionId(123L);
    container.setParentTrackingId(null);
    container.setContainerItems(getMockContainerItem());
    container.setCreateUser("sysadmin");
    Map<String, String> destination = new HashMap<>();
    destination.put(ReceivingConstants.SLOT, "A0001");
    container.setDestination(destination);
    return container;
  }
}
