package com.walmart.move.nim.receiving.rx.service;

import static org.mockito.AdditionalMatchers.or;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.ContainerDetails;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.data.MockRxHttpHeaders;
import com.walmart.move.nim.receiving.rx.config.RxManagedConfig;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom;
import java.util.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RxDeleteContainersRequestHandlerTest {

  @Mock private ReceiptService receiptService;

  @Mock private ContainerService containerService;

  @Mock private ContainerItemRepository containerItemRepository;

  @Mock private InstructionRepository instructionRepository;

  @Mock private RxManagedConfig rxManagedConfig;

  @InjectMocks private RxDeleteContainersRequestHandler rxDeleteContainersRequestHandler;

  @Captor private ArgumentCaptor<List> instructionListCaptor;
  @Captor private ArgumentCaptor<List> containerItemListCaptor;
  @Captor private ArgumentCaptor<List> receiptListCaptor;
  @Captor private ArgumentCaptor<List> containerListCaptor;

  private Gson gson = new Gson();

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(rxDeleteContainersRequestHandler, "gson", gson);
  }

  @AfterMethod
  public void tearDown() {
    reset(receiptService);
    reset(containerService);
    reset(containerItemRepository);
    reset(instructionRepository);
  }

  private Container getParentContainer() {
    Container container = new Container();
    container.setTrackingId("MOCK_PARENT_TRACKING_ID");
    container.setInstructionId(1234l);

    ContainerItem containerItem = new ContainerItem();
    containerItem.setQuantity(72);
    containerItem.setQuantityUOM(Uom.EACHES);
    containerItem.setVnpkQty(24);
    containerItem.setWhpkQty(6);

    container.setContainerItems(Arrays.asList(containerItem));

    return container;
  }

  private Container getContainer(String trackingId) {
    Container container = new Container();
    container.setParentTrackingId("MOCK_PARENT_TRACKING_ID");
    container.setTrackingId(trackingId);
    container.setInstructionId(1234l);

    ContainerItem containerItem = new ContainerItem();
    containerItem.setTrackingId(trackingId);
    containerItem.setQuantity(24);
    containerItem.setQuantityUOM(Uom.EACHES);
    containerItem.setVnpkQty(24);
    containerItem.setWhpkQty(6);
    HashMap<String, Object> containerMiscInfo = new HashMap<>();
    containerMiscInfo.put(RxConstants.SHIPMENT_DOCUMENT_ID, "shipmentDocId");
    container.setContainerMiscInfo(containerMiscInfo);

    container.setContainerItems(Arrays.asList(containerItem));

    return container;
  }

  private Optional<Instruction> getInstruction() {
    Instruction instruction = new Instruction();
    instruction.setReceivedQuantity(3);
    instruction.setReceivedQuantityUOM(Uom.VNPK);
    instruction.setSsccNumber("MOCK_SSCC_NUMBER");

    ContainerDetails containerDetails1 = new ContainerDetails();
    containerDetails1.setTrackingId("MOCK_TRACKING_ID_1");

    ContainerDetails containerDetails2 = new ContainerDetails();
    containerDetails2.setTrackingId("MOCK_TRACKING_ID_2");

    ContainerDetails containerDetails3 = new ContainerDetails();
    containerDetails3.setTrackingId("MOCK_TRACKING_ID_3");

    instruction.setChildContainers(
        Arrays.asList(containerDetails1, containerDetails2, containerDetails3));

    DeliveryDocument deliveryDocument = new DeliveryDocument();
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setPurchaseReferenceNumber("MOCK_PO_NUMBER");
    deliveryDocumentLine.setPurchaseReferenceLineNumber(1);
    deliveryDocument.setDeliveryDocumentLines(Arrays.asList(deliveryDocumentLine));

    instruction.setDeliveryDocument(new Gson().toJson(deliveryDocument));

    LinkedTreeMap<String, Object> moveMap = new LinkedTreeMap<>();
    moveMap.put(ReceivingConstants.MOVE_FROM_LOCATION, "MOCK_DOOR");
    instruction.setMove(moveMap);

    return Optional.of(instruction);
  }

  @Test
  public void testDeleteContainersByTrackingId() throws Exception {

    List<String> trackingIds =
        Arrays.asList("MOCK_TRACKING_ID_1", "MOCK_TRACKING_ID_2", "MOCK_TRACKING_ID_3");

    doAnswer(
            new Answer<Container>() {
              public Container answer(InvocationOnMock invocation) {
                String trackingId = (String) invocation.getArguments()[0];
                return getContainer(trackingId);
              }
            })
        .when(containerService)
        .getContainerByTrackingId(
            or(or(eq("MOCK_TRACKING_ID_1"), eq("MOCK_TRACKING_ID_2")), eq("MOCK_TRACKING_ID_3")));
    doReturn(getParentContainer())
        .when(containerService)
        .getContainerByTrackingId(eq("MOCK_PARENT_TRACKING_ID"));
    doReturn(getInstruction()).when(instructionRepository).findById(anyLong());

    doReturn(null).when(instructionRepository).saveAll(instructionListCaptor.capture());
    doReturn(null).when(containerItemRepository).saveAll(containerItemListCaptor.capture());
    doReturn(null).when(receiptService).saveAll(receiptListCaptor.capture());
    doNothing().when(containerService).deleteContainersByTrackingIds(containerListCaptor.capture());

    rxDeleteContainersRequestHandler.deleteContainersByTrackingId(
        trackingIds, MockRxHttpHeaders.getHeaders());

    assertEquals(((Instruction) instructionListCaptor.getValue().get(0)).getReceivedQuantity(), 0);
    assertEquals(
        ((Instruction) instructionListCaptor.getValue().get(0)).getChildContainers().size(), 0);
    assertEquals(((Instruction) instructionListCaptor.getValue().get(1)).getReceivedQuantity(), 0);
    assertEquals(((Instruction) instructionListCaptor.getValue().get(2)).getReceivedQuantity(), 0);

    assertSame(((ContainerItem) containerItemListCaptor.getValue().get(0)).getQuantity(), 0);
    assertSame(((ContainerItem) containerItemListCaptor.getValue().get(1)).getQuantity(), 0);
    assertSame(((ContainerItem) containerItemListCaptor.getValue().get(2)).getQuantity(), 0);

    assertSame(((Receipt) receiptListCaptor.getValue().get(0)).getQuantity(), -1);
    assertSame(((Receipt) receiptListCaptor.getValue().get(0)).getEachQty(), -24);
    assertSame(((Receipt) receiptListCaptor.getValue().get(1)).getQuantity(), -1);
    assertSame(((Receipt) receiptListCaptor.getValue().get(1)).getEachQty(), -24);
    assertSame(((Receipt) receiptListCaptor.getValue().get(2)).getQuantity(), -1);
    assertSame(((Receipt) receiptListCaptor.getValue().get(2)).getEachQty(), -24);

    assertSame(((String) containerListCaptor.getValue().get(0)), "MOCK_TRACKING_ID_1");
    assertSame(((String) containerListCaptor.getValue().get(1)), "MOCK_TRACKING_ID_2");
    assertSame(((String) containerListCaptor.getValue().get(2)), "MOCK_TRACKING_ID_3");

    verify(containerService, times(3))
        .getContainerByTrackingId(
            or(or(eq("MOCK_TRACKING_ID_1"), eq("MOCK_TRACKING_ID_2")), eq("MOCK_TRACKING_ID_3")));
    verify(containerService, times(3)).getContainerByTrackingId(eq("MOCK_PARENT_TRACKING_ID"));
    verify(instructionRepository, times(3)).findById(anyLong());

    verify(instructionRepository, times(1)).saveAll(instructionListCaptor.capture());
    verify(containerItemRepository, times(1)).saveAll(containerItemListCaptor.capture());
    verify(receiptService, times(1)).saveAll(receiptListCaptor.capture());
    verify(containerService, times(1)).deleteContainersByTrackingIds(containerListCaptor.capture());
  }

  @Test
  public void testDeleteContainersByTrackingId_Eaches() throws Exception {

    List<String> trackingIds =
        Arrays.asList("MOCK_TRACKING_ID_1", "MOCK_TRACKING_ID_2", "MOCK_TRACKING_ID_3");

    doAnswer(
            new Answer<Container>() {
              public Container answer(InvocationOnMock invocation) {
                String trackingId = (String) invocation.getArguments()[0];
                return getContainer(trackingId);
              }
            })
        .when(containerService)
        .getContainerByTrackingId(
            or(or(eq("MOCK_TRACKING_ID_1"), eq("MOCK_TRACKING_ID_2")), eq("MOCK_TRACKING_ID_3")));
    doReturn(getParentContainer())
        .when(containerService)
        .getContainerByTrackingId(eq("MOCK_PARENT_TRACKING_ID"));
    Optional<Instruction> instructionOptional = getInstruction();
    Instruction instruction = instructionOptional.get();
    instruction.setReceivedQuantity(72);
    instruction.setReceivedQuantityUOM(Uom.EACHES);
    doReturn(Optional.of(instruction)).when(instructionRepository).findById(anyLong());
    doReturn(true).when(rxManagedConfig).isRollbackPartialContainerEnabled();

    doReturn(null).when(instructionRepository).saveAll(instructionListCaptor.capture());
    doReturn(null).when(containerItemRepository).saveAll(containerItemListCaptor.capture());
    doReturn(null).when(receiptService).saveAll(receiptListCaptor.capture());
    doNothing().when(containerService).deleteContainersByTrackingIds(containerListCaptor.capture());

    rxDeleteContainersRequestHandler.deleteContainersByTrackingId(
        trackingIds, MockRxHttpHeaders.getHeaders());

    assertEquals(((Instruction) instructionListCaptor.getValue().get(0)).getReceivedQuantity(), 0);
    assertEquals(
        ((Instruction) instructionListCaptor.getValue().get(0)).getChildContainers().size(), 0);
    assertEquals(((Instruction) instructionListCaptor.getValue().get(1)).getReceivedQuantity(), 0);
    assertEquals(((Instruction) instructionListCaptor.getValue().get(2)).getReceivedQuantity(), 0);

    assertSame(((ContainerItem) containerItemListCaptor.getValue().get(0)).getQuantity(), 0);
    assertSame(((ContainerItem) containerItemListCaptor.getValue().get(1)).getQuantity(), 0);
    assertSame(((ContainerItem) containerItemListCaptor.getValue().get(2)).getQuantity(), 0);

    assertSame(((Receipt) receiptListCaptor.getValue().get(0)).getQuantity(), -1);
    assertSame(((Receipt) receiptListCaptor.getValue().get(0)).getEachQty(), -24);
    assertSame(((Receipt) receiptListCaptor.getValue().get(1)).getQuantity(), -1);
    assertSame(((Receipt) receiptListCaptor.getValue().get(1)).getEachQty(), -24);
    assertSame(((Receipt) receiptListCaptor.getValue().get(2)).getQuantity(), -1);
    assertSame(((Receipt) receiptListCaptor.getValue().get(2)).getEachQty(), -24);

    assertSame(((String) containerListCaptor.getValue().get(0)), "MOCK_TRACKING_ID_1");
    assertSame(((String) containerListCaptor.getValue().get(1)), "MOCK_TRACKING_ID_2");
    assertSame(((String) containerListCaptor.getValue().get(2)), "MOCK_TRACKING_ID_3");

    verify(containerService, times(3))
        .getContainerByTrackingId(
            or(or(eq("MOCK_TRACKING_ID_1"), eq("MOCK_TRACKING_ID_2")), eq("MOCK_TRACKING_ID_3")));
    verify(containerService, times(3)).getContainerByTrackingId(eq("MOCK_PARENT_TRACKING_ID"));
    verify(instructionRepository, times(3)).findById(anyLong());

    verify(instructionRepository, times(1)).saveAll(instructionListCaptor.capture());
    verify(containerItemRepository, times(1)).saveAll(containerItemListCaptor.capture());
    verify(receiptService, times(1)).saveAll(receiptListCaptor.capture());
    verify(containerService, times(1)).deleteContainersByTrackingIds(containerListCaptor.capture());
  }

  @Test
  public void testDeleteContainersByTrackingId_error_parent_trackingId() throws Exception {

    List<String> trackingIds = Arrays.asList("PARENT_TRACKING_ID");

    doAnswer(
            new Answer<Container>() {
              public Container answer(InvocationOnMock invocation) {
                return getParentContainer();
              }
            })
        .when(containerService)
        .getContainerByTrackingId(eq("PARENT_TRACKING_ID"));

    try {
      rxDeleteContainersRequestHandler.deleteContainersByTrackingId(
          trackingIds, MockRxHttpHeaders.getHeaders());
    } catch (ReceivingBadDataException rbe) {
      assertEquals(rbe.getErrorCode(), ExceptionCodes.PARENT_CONTAINER_CANNOT_BE_DELETED);
      assertEquals(rbe.getDescription(), RxConstants.PARENT_CONTAINER_CANNOT_BE_DELETED);
    }
  }

  @Test
  public void testDeleteContainersByTrackingId_error_closed_instruction() throws Exception {

    List<String> trackingIds =
        Arrays.asList("MOCK_TRACKING_ID_1", "MOCK_TRACKING_ID_2", "MOCK_TRACKING_ID_3");

    doAnswer(
            new Answer<Container>() {
              public Container answer(InvocationOnMock invocation) {
                String trackingId = (String) invocation.getArguments()[0];
                return getContainer(trackingId);
              }
            })
        .when(containerService)
        .getContainerByTrackingId(
            or(or(eq("MOCK_TRACKING_ID_1"), eq("MOCK_TRACKING_ID_2")), eq("MOCK_TRACKING_ID_3")));
    doReturn(getParentContainer())
        .when(containerService)
        .getContainerByTrackingId(eq("MOCK_PARENT_TRACKING_ID"));
    Optional<Instruction> instruction = getInstruction();
    instruction.get().setCompleteTs(new Date());
    doReturn(instruction).when(instructionRepository).findById(anyLong());

    try {
      rxDeleteContainersRequestHandler.deleteContainersByTrackingId(
          trackingIds, MockRxHttpHeaders.getHeaders());
    } catch (ReceivingBadDataException rbe) {
      assertEquals(
          rbe.getErrorCode(), ExceptionCodes.INSTRUCTION_CLOSED_CONTAINER_CANNOT_BE_DELETED);
      assertEquals(
          rbe.getDescription(), RxConstants.INSTRUCTION_CLOSED_CONTAINER_CANNOT_BE_DELETED);
    }
  }

  @Test
  public void testDeleteContainersByTrackingId_error_instruction_not_found() throws Exception {

    List<String> trackingIds =
        Arrays.asList("MOCK_TRACKING_ID_1", "MOCK_TRACKING_ID_2", "MOCK_TRACKING_ID_3");

    doAnswer(
            new Answer<Container>() {
              public Container answer(InvocationOnMock invocation) {
                String trackingId = (String) invocation.getArguments()[0];
                return getContainer(trackingId);
              }
            })
        .when(containerService)
        .getContainerByTrackingId(
            or(or(eq("MOCK_TRACKING_ID_1"), eq("MOCK_TRACKING_ID_2")), eq("MOCK_TRACKING_ID_3")));
    doReturn(getParentContainer())
        .when(containerService)
        .getContainerByTrackingId(eq("MOCK_PARENT_TRACKING_ID"));
    doReturn(Optional.empty()).when(instructionRepository).findById(anyLong());

    try {
      rxDeleteContainersRequestHandler.deleteContainersByTrackingId(
          trackingIds, MockRxHttpHeaders.getHeaders());
    } catch (ReceivingBadDataException rbe) {
      assertEquals(rbe.getErrorCode(), ExceptionCodes.INSTRUCTION_NOT_FOUND_FOR_CONTAINER);
      assertEquals(rbe.getDescription(), RxConstants.INSTRUCTION_NOT_FOUND_FOR_CONTAINER);
    }
  }
}
