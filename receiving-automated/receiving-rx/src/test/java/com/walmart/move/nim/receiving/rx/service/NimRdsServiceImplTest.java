package com.walmart.move.nim.receiving.rx.service;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.client.nimrds.NimRDSRestApiClient;
import com.walmart.move.nim.receiving.core.client.nimrds.model.*;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.CompleteInstructionRequest;
import com.walmart.move.nim.receiving.core.model.ContainerDetails;
import com.walmart.move.nim.receiving.core.model.Content;
import com.walmart.move.nim.receiving.core.model.SlotDetails;
import com.walmart.move.nim.receiving.data.MockRxHttpHeaders;
import com.walmart.move.nim.receiving.rx.model.RxInstructionType;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class NimRdsServiceImplTest {

  @Mock private NimRDSRestApiClient nimRDSRestApiClient;

  @InjectMocks private NimRdsServiceImpl nimRdsServiceImpl;

  @BeforeClass
  public void createNimRdsServiceImpl() throws Exception {
    MockitoAnnotations.initMocks(this);

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32897);
  }

  @AfterMethod
  public void afterMethod() {
    reset(nimRDSRestApiClient);
  }

  @Test
  public void test_acquireSlot_autoslotting() {

    Instruction instruction = new Instruction();
    instruction.setPurchaseReferenceNumber("123456789");
    instruction.setPurchaseReferenceLineNumber(1);
    instruction.setDeliveryNumber(999999l);
    instruction.setReceivedQuantity(2);
    instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);
    instruction.setSsccNumber("00107713130235472853");
    instruction.setInstructionMsg(RxInstructionType.BUILD_CONTAINER.getInstructionMsg());
    instruction.setInstructionCode(RxInstructionType.BUILD_CONTAINER.getInstructionType());

    LinkedTreeMap<String, Object> move = new LinkedTreeMap<>();
    move.put(ReceivingConstants.MOVE_FROM_LOCATION, "D123");
    instruction.setMove(move);

    ContainerDetails mockContainerDetails = new ContainerDetails();
    mockContainerDetails.setTrackingId("UNIT_TEST_MOCK_TRACKING_ID");

    Content mockContainerDetailsContent = new Content();
    mockContainerDetailsContent.setVendorPack(1440);
    mockContainerDetailsContent.setWarehousePack(60);
    mockContainerDetailsContent.setQty(2880);
    mockContainerDetailsContent.setQtyUom(ReceivingConstants.Uom.EACHES);
    mockContainerDetails.setContents(Arrays.asList(mockContainerDetailsContent));

    instruction.setContainer(mockContainerDetails);
    HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();

    ReceivedContainer mockReceivedContainer = new ReceivedContainer();
    Destination mockDestination = new Destination();
    mockDestination.setSlot("UNIT_TEST_MOCK_SLOT");

    List<Destination> destinations = new ArrayList<>();
    destinations.add(mockDestination);
    mockReceivedContainer.setDestinations(destinations);

    List<ReceivedContainer> receivedList = new ArrayList<>();
    receivedList.add(mockReceivedContainer);
    ReceiveContainersResponseBody mockReceiveContainersResponseBody =
        new ReceiveContainersResponseBody();
    mockReceiveContainersResponseBody.setReceived(receivedList);

    List<ContainerDetails> containerDetailsList = new ArrayList<>();

    ContainerDetails containerDetails1 = new ContainerDetails();
    List<Content> contents1 = new ArrayList<>();
    Content content1 = new Content();
    content1.setLot("11111");
    content1.setQty(1440);
    content1.setQtyUom(ReceivingConstants.Uom.EACHES);
    contents1.add(content1);
    containerDetails1.setContents(contents1);
    containerDetailsList.add(containerDetails1);

    ContainerDetails containerDetails2 = new ContainerDetails();
    List<Content> contents2 = new ArrayList<>();
    Content content2 = new Content();
    content2.setLot("22222");
    content2.setQty(1440);
    content2.setQtyUom(ReceivingConstants.Uom.EACHES);
    contents2.add(content2);
    containerDetails2.setContents(contents2);
    containerDetailsList.add(containerDetails2);

    instruction.setChildContainers(containerDetailsList);

    ArgumentCaptor<ReceiveContainersRequestBody> captor =
        ArgumentCaptor.forClass(ReceiveContainersRequestBody.class);
    doReturn(mockReceiveContainersResponseBody)
        .when(nimRDSRestApiClient)
        .receiveContainers(captor.capture(), any(Map.class));

    SlotDetails mockSlotDetails = new SlotDetails();
    CompleteInstructionRequest completeInstructionRequest = new CompleteInstructionRequest();
    completeInstructionRequest.setSlotDetails(mockSlotDetails);

    ReceiveContainersResponseBody acquiredSlot =
        nimRdsServiceImpl.acquireSlot(instruction, completeInstructionRequest, httpHeaders);

    assertNotNull(acquiredSlot);
    assertTrue(
        StringUtils.isNotBlank(
            acquiredSlot.getReceived().get(0).getDestinations().get(0).getSlot()));
    assertEquals(
        acquiredSlot.getReceived().get(0).getDestinations().get(0).getSlot(),
        "UNIT_TEST_MOCK_SLOT");

    assertNull(captor.getValue().getContainerOrders().get(0).getSstkSlotSize());
    assertNull(captor.getValue().getContainerOrders().get(0).getSlottingOverride());
    assertSame(captor.getValue().getContainerOrders().get(0).getQty(), 2);
    assertSame(captor.getValue().getContainerOrders().get(0).getReceivedUomTxt(), "ZA");
    assertEquals(
        captor.getValue().getContainerOrders().get(0).getLotNumbers().get(0).getLotNumber(),
        "11111");
    assertEquals(captor.getValue().getContainerOrders().get(0).getLotNumbers().get(0).getQty(), 1);
    assertEquals(
        captor.getValue().getContainerOrders().get(0).getLotNumbers().get(1).getLotNumber(),
        "22222");
    assertEquals(captor.getValue().getContainerOrders().get(0).getLotNumbers().get(1).getQty(), 1);
  }

  @Test
  public void test_acquireSlot_autoslotting_with_slotsize() {

    Instruction instruction = new Instruction();
    instruction.setPurchaseReferenceNumber("123456789");
    instruction.setPurchaseReferenceLineNumber(1);
    instruction.setDeliveryNumber(999999l);
    instruction.setReceivedQuantity(2);
    instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);
    instruction.setInstructionMsg(
        RxInstructionType.BUILDCONTAINER_SCAN_BY_GTIN_LOT.getInstructionMsg());
    instruction.setInstructionCode(
        RxInstructionType.BUILDCONTAINER_SCAN_BY_GTIN_LOT.getInstructionType());

    LinkedTreeMap<String, Object> move = new LinkedTreeMap<>();
    move.put(ReceivingConstants.MOVE_FROM_LOCATION, "D123");
    instruction.setMove(move);

    ContainerDetails mockContainerDetails = new ContainerDetails();
    mockContainerDetails.setTrackingId("UNIT_TEST_MOCK_TRACKING_ID");
    Content mockContainerDetailsContent = new Content();
    mockContainerDetailsContent.setVendorPack(6);
    mockContainerDetailsContent.setWarehousePack(6);
    mockContainerDetailsContent.setQtyUom(ReceivingConstants.Uom.EACHES);
    mockContainerDetails.setContents(Arrays.asList(mockContainerDetailsContent));

    instruction.setContainer(mockContainerDetails);
    HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();

    ReceivedContainer mockReceivedContainer = new ReceivedContainer();
    Destination mockDestination = new Destination();
    mockDestination.setSlot("UNIT_TEST_MOCK_SLOT");

    List<Destination> destinations = new ArrayList<>();
    destinations.add(mockDestination);
    mockReceivedContainer.setDestinations(destinations);

    List<ReceivedContainer> receivedList = new ArrayList<>();
    receivedList.add(mockReceivedContainer);
    ReceiveContainersResponseBody mockReceiveContainersResponseBody =
        new ReceiveContainersResponseBody();
    mockReceiveContainersResponseBody.setReceived(receivedList);

    List<ContainerDetails> containerDetailsList = new ArrayList<>();

    ContainerDetails containerDetails1 = new ContainerDetails();
    List<Content> contents1 = new ArrayList<>();
    Content content1 = new Content();
    content1.setLot("11111");
    content1.setQty(1);
    contents1.add(content1);
    containerDetails1.setContents(contents1);
    containerDetailsList.add(containerDetails1);

    ContainerDetails containerDetails2 = new ContainerDetails();
    List<Content> contents2 = new ArrayList<>();
    Content content2 = new Content();
    content2.setLot("11111");
    content2.setQty(1);
    contents2.add(content1);
    containerDetails2.setContents(contents2);
    containerDetailsList.add(containerDetails2);

    instruction.setChildContainers(containerDetailsList);

    ArgumentCaptor<ReceiveContainersRequestBody> captor =
        ArgumentCaptor.forClass(ReceiveContainersRequestBody.class);

    doReturn(mockReceiveContainersResponseBody)
        .when(nimRDSRestApiClient)
        .receiveContainers(captor.capture(), any(Map.class));

    SlotDetails mockSlotDetails = new SlotDetails();
    mockSlotDetails.setSlotSize(98);
    CompleteInstructionRequest completeInstructionRequest = new CompleteInstructionRequest();
    completeInstructionRequest.setSlotDetails(mockSlotDetails);

    ReceiveContainersResponseBody acquiredSlot =
        nimRdsServiceImpl.acquireSlot(instruction, completeInstructionRequest, httpHeaders);

    assertNotNull(acquiredSlot);
    assertTrue(
        StringUtils.isNotBlank(
            acquiredSlot.getReceived().get(0).getDestinations().get(0).getSlot()));
    assertEquals(
        acquiredSlot.getReceived().get(0).getDestinations().get(0).getSlot(),
        "UNIT_TEST_MOCK_SLOT");

    assertSame(captor.getValue().getContainerOrders().get(0).getSstkSlotSize(), 98);
  }

  @Test
  public void test_acquireSlot_manualslotting() {

    Instruction instruction = new Instruction();
    instruction.setPurchaseReferenceNumber("123456789");
    instruction.setPurchaseReferenceLineNumber(1);
    instruction.setDeliveryNumber(999999l);
    instruction.setReceivedQuantity(2);
    instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);
    instruction.setSsccNumber("00107713130235472853");
    instruction.setInstructionMsg(RxInstructionType.BUILD_CONTAINER.getInstructionMsg());
    instruction.setInstructionCode(RxInstructionType.BUILD_CONTAINER.getInstructionType());

    LinkedTreeMap<String, Object> move = new LinkedTreeMap<>();
    move.put(ReceivingConstants.MOVE_FROM_LOCATION, "D123");
    instruction.setMove(move);

    ContainerDetails mockContainerDetails = new ContainerDetails();
    mockContainerDetails.setTrackingId("UNIT_TEST_MOCK_TRACKING_ID");
    Content mockContainerDetailsContent = new Content();
    mockContainerDetailsContent.setVendorPack(6);
    mockContainerDetailsContent.setWarehousePack(6);
    mockContainerDetailsContent.setQtyUom(ReceivingConstants.Uom.EACHES);
    mockContainerDetails.setContents(Arrays.asList(mockContainerDetailsContent));

    instruction.setContainer(mockContainerDetails);
    HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();

    ReceivedContainer mockReceivedContainer = new ReceivedContainer();
    Destination mockDestination = new Destination();
    mockDestination.setSlot("UNIT_TEST_MOCK_SLOT");

    List<Destination> destinations = new ArrayList<>();
    destinations.add(mockDestination);
    mockReceivedContainer.setDestinations(destinations);

    List<ReceivedContainer> receivedList = new ArrayList<>();
    receivedList.add(mockReceivedContainer);
    ReceiveContainersResponseBody mockReceiveContainersResponseBody =
        new ReceiveContainersResponseBody();
    mockReceiveContainersResponseBody.setReceived(receivedList);

    List<ContainerDetails> containerDetailsList = new ArrayList<>();

    ContainerDetails containerDetails1 = new ContainerDetails();
    List<Content> contents1 = new ArrayList<>();
    Content content1 = new Content();
    content1.setLot("11111");
    content1.setQty(1);
    contents1.add(content1);
    containerDetails1.setContents(contents1);
    for (int i = 0; i < 6; i++) {
      containerDetailsList.add(containerDetails1);
    }

    instruction.setChildContainers(containerDetailsList);

    ArgumentCaptor<ReceiveContainersRequestBody> captor =
        ArgumentCaptor.forClass(ReceiveContainersRequestBody.class);
    doReturn(mockReceiveContainersResponseBody)
        .when(nimRDSRestApiClient)
        .receiveContainers(captor.capture(), any(Map.class));
    CompleteInstructionRequest completeInstructionRequest = new CompleteInstructionRequest();
    SlotDetails mockSlotDetails = new SlotDetails();
    mockSlotDetails.setSlotSize(72);
    mockSlotDetails.setSlot("MOCK_SLOTP_100");
    mockSlotDetails.setSlotRange("MOCK_SLOTP_999");
    completeInstructionRequest.setSlotDetails(mockSlotDetails);

    ReceiveContainersResponseBody acquiredSlot =
        nimRdsServiceImpl.acquireSlot(instruction, completeInstructionRequest, httpHeaders);

    assertNotNull(acquiredSlot);
    assertTrue(
        StringUtils.isNotBlank(
            acquiredSlot.getReceived().get(0).getDestinations().get(0).getSlot()));
    assertEquals(
        acquiredSlot.getReceived().get(0).getDestinations().get(0).getSlot(),
        "UNIT_TEST_MOCK_SLOT");

    assertNotNull(captor.getValue().getContainerOrders().get(0).getSlottingOverride());
    assertSame(
        captor.getValue().getContainerOrders().get(0).getSlottingOverride().getSlotSize(), 72);
    assertEquals(
        captor.getValue().getContainerOrders().get(0).getSlottingOverride().getSlot(),
        "MOCK_SLOTP_100");
    assertEquals(
        captor.getValue().getContainerOrders().get(0).getSlottingOverride().getSlotRangeEnd(),
        "MOCK_SLOTP_999");
    assertEquals(
        captor.getValue().getContainerOrders().get(0).getSlottingOverride().getSlottingType(),
        "MANUAL");
  }

  @Test
  public void test_acquireSlot_manualslotting_lot_less_than_vnpk() {

    Instruction instruction = new Instruction();
    instruction.setPurchaseReferenceNumber("123456789");
    instruction.setPurchaseReferenceLineNumber(1);
    instruction.setDeliveryNumber(999999l);
    instruction.setReceivedQuantity(2);
    instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);
    instruction.setSsccNumber("00107713130235472853");
    instruction.setInstructionMsg(RxInstructionType.BUILD_CONTAINER.getInstructionMsg());
    instruction.setInstructionCode(RxInstructionType.BUILD_CONTAINER.getInstructionType());

    LinkedTreeMap<String, Object> move = new LinkedTreeMap<>();
    move.put(ReceivingConstants.MOVE_FROM_LOCATION, "D123");
    instruction.setMove(move);

    ContainerDetails mockContainerDetails = new ContainerDetails();
    mockContainerDetails.setTrackingId("UNIT_TEST_MOCK_TRACKING_ID");
    Content mockContainerDetailsContent = new Content();
    mockContainerDetailsContent.setVendorPack(6);
    mockContainerDetailsContent.setWarehousePack(6);
    mockContainerDetailsContent.setQtyUom(ReceivingConstants.Uom.EACHES);
    mockContainerDetails.setContents(Arrays.asList(mockContainerDetailsContent));

    instruction.setContainer(mockContainerDetails);
    HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();

    ReceivedContainer mockReceivedContainer = new ReceivedContainer();
    Destination mockDestination = new Destination();
    mockDestination.setSlot("UNIT_TEST_MOCK_SLOT");

    List<Destination> destinations = new ArrayList<>();
    destinations.add(mockDestination);
    mockReceivedContainer.setDestinations(destinations);

    List<ReceivedContainer> receivedList = new ArrayList<>();
    receivedList.add(mockReceivedContainer);
    ReceiveContainersResponseBody mockReceiveContainersResponseBody =
        new ReceiveContainersResponseBody();
    mockReceiveContainersResponseBody.setReceived(receivedList);

    List<ContainerDetails> containerDetailsList = new ArrayList<>();

    ContainerDetails containerDetails1 = new ContainerDetails();
    List<Content> contents1 = new ArrayList<>();
    Content content1 = new Content();
    content1.setLot("11111");
    content1.setQty(1);
    contents1.add(content1);
    containerDetails1.setContents(contents1);
    containerDetailsList.add(containerDetails1);

    ContainerDetails containerDetails2 = new ContainerDetails();
    List<Content> contents2 = new ArrayList<>();
    Content content2 = new Content();
    content2.setLot("11111");
    content2.setQty(1);
    contents2.add(content1);
    containerDetails2.setContents(contents2);
    containerDetailsList.add(containerDetails2);

    instruction.setChildContainers(containerDetailsList);

    ArgumentCaptor<ReceiveContainersRequestBody> captor =
        ArgumentCaptor.forClass(ReceiveContainersRequestBody.class);
    doReturn(mockReceiveContainersResponseBody)
        .when(nimRDSRestApiClient)
        .receiveContainers(captor.capture(), any(Map.class));
    CompleteInstructionRequest completeInstructionRequest = new CompleteInstructionRequest();
    SlotDetails mockSlotDetails = new SlotDetails();
    mockSlotDetails.setSlotSize(72);
    mockSlotDetails.setSlot("MOCK_SLOTP_100");
    mockSlotDetails.setSlotRange("MOCK_SLOTP_999");
    completeInstructionRequest.setSlotDetails(mockSlotDetails);

    ReceiveContainersResponseBody acquiredSlot =
        nimRdsServiceImpl.acquireSlot(instruction, completeInstructionRequest, httpHeaders);

    assertNotNull(acquiredSlot);
    assertTrue(
        StringUtils.isNotBlank(
            acquiredSlot.getReceived().get(0).getDestinations().get(0).getSlot()));
    assertEquals(
        acquiredSlot.getReceived().get(0).getDestinations().get(0).getSlot(),
        "UNIT_TEST_MOCK_SLOT");

    assertNull(captor.getValue().getContainerOrders().get(0).getSstkSlotSize());
    assertNotNull(captor.getValue().getContainerOrders().get(0).getSlottingOverride());
    assertSame(
        captor.getValue().getContainerOrders().get(0).getSlottingOverride().getSlotSize(), 72);
    assertEquals(
        captor.getValue().getContainerOrders().get(0).getSlottingOverride().getSlot(),
        "MOCK_SLOTP_100");
    assertEquals(
        captor.getValue().getContainerOrders().get(0).getSlottingOverride().getSlotRangeEnd(),
        "MOCK_SLOTP_999");
    assertEquals(
        captor.getValue().getContainerOrders().get(0).getSlottingOverride().getSlottingType(),
        "MANUAL");
  }

  @Test
  public void test_acquireSlot_partialCase() {

    Instruction instruction = new Instruction();
    instruction.setPurchaseReferenceNumber("123456789");
    instruction.setPurchaseReferenceLineNumber(1);
    instruction.setDeliveryNumber(999999l);
    instruction.setReceivedQuantity(1);
    instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);
    instruction.setSsccNumber("00107713130235472853");
    instruction.setInstructionMsg(RxInstructionType.BUILD_PARTIAL_CONTAINER.getInstructionMsg());
    instruction.setInstructionCode(RxInstructionType.BUILD_PARTIAL_CONTAINER.getInstructionType());

    LinkedTreeMap<String, Object> move = new LinkedTreeMap<>();
    move.put(ReceivingConstants.MOVE_FROM_LOCATION, "D123");
    instruction.setMove(move);

    ContainerDetails mockContainerDetails = new ContainerDetails();
    mockContainerDetails.setTrackingId("UNIT_TEST_MOCK_TRACKING_ID");

    Content mockContainerDetailsContent = new Content();
    mockContainerDetailsContent.setVendorPack(1440);
    mockContainerDetailsContent.setWarehousePack(60);
    mockContainerDetailsContent.setQtyUom(ReceivingConstants.Uom.EACHES);
    mockContainerDetails.setContents(Arrays.asList(mockContainerDetailsContent));

    instruction.setContainer(mockContainerDetails);
    HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();

    ReceivedContainer mockReceivedContainer = new ReceivedContainer();
    Destination mockDestination = new Destination();
    mockDestination.setSlot("UNIT_TEST_MOCK_SLOT");

    List<Destination> destinations = new ArrayList<>();
    destinations.add(mockDestination);
    mockReceivedContainer.setDestinations(destinations);

    List<ReceivedContainer> receivedList = new ArrayList<>();
    receivedList.add(mockReceivedContainer);
    ReceiveContainersResponseBody mockReceiveContainersResponseBody =
        new ReceiveContainersResponseBody();
    mockReceiveContainersResponseBody.setReceived(receivedList);

    List<ContainerDetails> containerDetailsList = new ArrayList<>();

    ContainerDetails containerDetails1 = new ContainerDetails();
    List<Content> contents1 = new ArrayList<>();
    Content content1 = new Content();
    content1.setLot("11111");
    content1.setQty(60);
    content1.setQtyUom(ReceivingConstants.Uom.EACHES);
    contents1.add(content1);
    containerDetails1.setContents(contents1);
    containerDetailsList.add(containerDetails1);

    ContainerDetails containerDetails2 = new ContainerDetails();
    List<Content> contents2 = new ArrayList<>();
    Content content2 = new Content();
    content2.setLot("22222");
    content2.setQty(60);
    content2.setQtyUom(ReceivingConstants.Uom.EACHES);
    contents2.add(content2);
    containerDetails2.setContents(contents2);
    containerDetailsList.add(containerDetails2);

    instruction.setChildContainers(containerDetailsList);

    ArgumentCaptor<ReceiveContainersRequestBody> captor =
        ArgumentCaptor.forClass(ReceiveContainersRequestBody.class);
    doReturn(mockReceiveContainersResponseBody)
        .when(nimRDSRestApiClient)
        .receiveContainers(captor.capture(), any(Map.class));

    SlotDetails mockSlotDetails = new SlotDetails();
    CompleteInstructionRequest completeInstructionRequest = new CompleteInstructionRequest();
    completeInstructionRequest.setSlotDetails(mockSlotDetails);
    completeInstructionRequest.setPartialContainer(true);

    ReceiveContainersResponseBody acquiredSlot =
        nimRdsServiceImpl.acquireSlot(instruction, completeInstructionRequest, httpHeaders);

    assertNotNull(acquiredSlot);
    assertTrue(
        StringUtils.isNotBlank(
            acquiredSlot.getReceived().get(0).getDestinations().get(0).getSlot()));
    assertEquals(
        acquiredSlot.getReceived().get(0).getDestinations().get(0).getSlot(),
        "UNIT_TEST_MOCK_SLOT");

    assertNull(captor.getValue().getContainerOrders().get(0).getSstkSlotSize());
    assertNotNull(captor.getValue().getContainerOrders().get(0).getSlottingOverride().getSlot());

    assertSame(captor.getValue().getContainerOrders().get(0).getQty(), 2);
    assertSame(captor.getValue().getContainerOrders().get(0).getReceivedUomTxt(), "PH");
    assertEquals(
        captor.getValue().getContainerOrders().get(0).getLotNumbers().get(0).getLotNumber(),
        "11111");
    assertEquals(captor.getValue().getContainerOrders().get(0).getLotNumbers().get(0).getQty(), 1);
    assertEquals(
        captor.getValue().getContainerOrders().get(0).getLotNumbers().get(1).getLotNumber(),
        "22222");
    assertEquals(captor.getValue().getContainerOrders().get(0).getLotNumbers().get(1).getQty(), 1);
  }

  @Test
  public void test_acquireSlot_autoslotting_UpcReceiving() {

    Instruction instruction = new Instruction();
    instruction.setPurchaseReferenceNumber("123456789");
    instruction.setPurchaseReferenceLineNumber(1);
    instruction.setDeliveryNumber(999999l);
    instruction.setReceivedQuantity(20);
    instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);
    instruction.setInstructionMsg(
        RxInstructionType.BUILD_CONTAINER_FOR_UPC_RECEIVING.getInstructionMsg());
    instruction.setInstructionCode(
        RxInstructionType.BUILD_CONTAINER_FOR_UPC_RECEIVING.getInstructionType());

    LinkedTreeMap<String, Object> move = new LinkedTreeMap<>();
    move.put(ReceivingConstants.MOVE_FROM_LOCATION, "D123");
    instruction.setMove(move);

    ContainerDetails mockContainerDetails = new ContainerDetails();
    mockContainerDetails.setTrackingId("UNIT_TEST_MOCK_TRACKING_ID");

    Content mockContainerDetailsContent = new Content();
    mockContainerDetailsContent.setVendorPack(6);
    mockContainerDetailsContent.setWarehousePack(6);
    mockContainerDetailsContent.setQtyUom(ReceivingConstants.Uom.EACHES);
    mockContainerDetails.setContents(Arrays.asList(mockContainerDetailsContent));

    instruction.setContainer(mockContainerDetails);
    HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();

    ReceivedContainer mockReceivedContainer = new ReceivedContainer();
    Destination mockDestination = new Destination();
    mockDestination.setSlot("UNIT_TEST_MOCK_SLOT");

    List<Destination> destinations = new ArrayList<>();
    destinations.add(mockDestination);
    mockReceivedContainer.setDestinations(destinations);

    List<ReceivedContainer> receivedList = new ArrayList<>();
    receivedList.add(mockReceivedContainer);
    ReceiveContainersResponseBody mockReceiveContainersResponseBody =
        new ReceiveContainersResponseBody();
    mockReceiveContainersResponseBody.setReceived(receivedList);

    List<ContainerDetails> containerDetailsList = new ArrayList<>();

    ContainerDetails containerDetails1 = new ContainerDetails();
    List<Content> contents1 = new ArrayList<>();
    Content content1 = new Content();
    content1.setLot("11111");
    content1.setQty(1);
    contents1.add(content1);
    containerDetails1.setContents(contents1);
    containerDetailsList.add(containerDetails1);

    ContainerDetails containerDetails2 = new ContainerDetails();
    List<Content> contents2 = new ArrayList<>();
    Content content2 = new Content();
    content2.setLot("11111");
    content2.setQty(1);
    contents2.add(content1);
    containerDetails2.setContents(contents2);
    containerDetailsList.add(containerDetails2);

    instruction.setChildContainers(containerDetailsList);

    ArgumentCaptor<ReceiveContainersRequestBody> captor =
        ArgumentCaptor.forClass(ReceiveContainersRequestBody.class);
    doReturn(mockReceiveContainersResponseBody)
        .when(nimRDSRestApiClient)
        .receiveContainers(captor.capture(), any(Map.class));

    SlotDetails mockSlotDetails = new SlotDetails();
    CompleteInstructionRequest completeInstructionRequest = new CompleteInstructionRequest();
    completeInstructionRequest.setSlotDetails(mockSlotDetails);

    ReceiveContainersResponseBody acquiredSlot =
        nimRdsServiceImpl.acquireSlot(instruction, completeInstructionRequest, httpHeaders);

    assertNotNull(acquiredSlot);
    assertTrue(
        StringUtils.isNotBlank(
            acquiredSlot.getReceived().get(0).getDestinations().get(0).getSlot()));
    assertEquals(
        acquiredSlot.getReceived().get(0).getDestinations().get(0).getSlot(),
        "UNIT_TEST_MOCK_SLOT");

    assertNull(captor.getValue().getContainerOrders().get(0).getSstkSlotSize());
    assertNull(captor.getValue().getContainerOrders().get(0).getSlottingOverride());
  }

  @Test
  public void test_acquireSlot_autoslotting_UpcReceiving_partial() {

    Instruction instruction = new Instruction();
    instruction.setPurchaseReferenceNumber("123456789");
    instruction.setPurchaseReferenceLineNumber(1);
    instruction.setDeliveryNumber(999999l);
    instruction.setReceivedQuantity(20);
    instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.EACHES);
    instruction.setInstructionMsg(
        RxInstructionType.BUILD_CONTAINER_FOR_UPC_RECEIVING.getInstructionMsg());
    instruction.setInstructionCode(
        RxInstructionType.BUILD_CONTAINER_FOR_UPC_RECEIVING.getInstructionType());

    LinkedTreeMap<String, Object> move = new LinkedTreeMap<>();
    move.put(ReceivingConstants.MOVE_FROM_LOCATION, "D123");
    instruction.setMove(move);

    ContainerDetails mockContainerDetails = new ContainerDetails();
    mockContainerDetails.setTrackingId("UNIT_TEST_MOCK_TRACKING_ID");

    Content mockContainerDetailsContent = new Content();
    mockContainerDetailsContent.setVendorPack(6);
    mockContainerDetailsContent.setWarehousePack(6);
    mockContainerDetailsContent.setQtyUom(ReceivingConstants.Uom.EACHES);
    mockContainerDetails.setContents(Arrays.asList(mockContainerDetailsContent));

    instruction.setContainer(mockContainerDetails);
    HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();

    ReceivedContainer mockReceivedContainer = new ReceivedContainer();
    Destination mockDestination = new Destination();
    mockDestination.setSlot("UNIT_TEST_MOCK_SLOT");

    List<Destination> destinations = new ArrayList<>();
    destinations.add(mockDestination);
    mockReceivedContainer.setDestinations(destinations);

    List<ReceivedContainer> receivedList = new ArrayList<>();
    receivedList.add(mockReceivedContainer);
    ReceiveContainersResponseBody mockReceiveContainersResponseBody =
        new ReceiveContainersResponseBody();
    mockReceiveContainersResponseBody.setReceived(receivedList);

    List<ContainerDetails> containerDetailsList = new ArrayList<>();

    ContainerDetails containerDetails1 = new ContainerDetails();
    List<Content> contents1 = new ArrayList<>();
    Content content1 = new Content();
    content1.setLot("11111");
    content1.setQty(1);
    contents1.add(content1);
    containerDetails1.setContents(contents1);
    containerDetailsList.add(containerDetails1);

    ContainerDetails containerDetails2 = new ContainerDetails();
    List<Content> contents2 = new ArrayList<>();
    Content content2 = new Content();
    content2.setLot("11111");
    content2.setQty(1);
    contents2.add(content1);
    containerDetails2.setContents(contents2);
    containerDetailsList.add(containerDetails2);

    instruction.setChildContainers(containerDetailsList);

    ArgumentCaptor<ReceiveContainersRequestBody> captor =
        ArgumentCaptor.forClass(ReceiveContainersRequestBody.class);
    doReturn(mockReceiveContainersResponseBody)
        .when(nimRDSRestApiClient)
        .receiveContainers(captor.capture(), any(Map.class));

    SlotDetails mockSlotDetails = new SlotDetails();
    CompleteInstructionRequest completeInstructionRequest = new CompleteInstructionRequest();
    completeInstructionRequest.setPartialContainer(true);
    completeInstructionRequest.setSlotDetails(mockSlotDetails);

    ReceiveContainersResponseBody acquiredSlot =
        nimRdsServiceImpl.acquireSlot(instruction, completeInstructionRequest, httpHeaders);

    assertNotNull(acquiredSlot);
    assertTrue(
        StringUtils.isNotBlank(
            acquiredSlot.getReceived().get(0).getDestinations().get(0).getSlot()));
    assertEquals(
        acquiredSlot.getReceived().get(0).getDestinations().get(0).getSlot(),
        "UNIT_TEST_MOCK_SLOT");
  }

  @Test
  public void test_quantityChange() {

    QuantityChangeResponseBody mockQuantityChangeResponseBody = new QuantityChangeResponseBody();
    doReturn(mockQuantityChangeResponseBody)
        .when(nimRDSRestApiClient)
        .quantityChange(any(QuantityChangeRequestBody.class), any(Map.class));

    nimRdsServiceImpl.quantityChange(10, "MOCK_SCAN_TAG", MockRxHttpHeaders.getHeaders());

    verify(nimRDSRestApiClient)
        .quantityChange(any(QuantityChangeRequestBody.class), any(Map.class));
  }

  @Test
  public void test_acquireSlotForSplitPallet_manual_slot_upc_receiving() {

    doReturn(new ReceiveContainersResponseBody())
        .when(nimRDSRestApiClient)
        .receiveContainers(any(ReceiveContainersRequestBody.class), anyMap());

    List<Instruction> instructions = new ArrayList<>();
    instructions.add(
        getMockSplitPalletInstruction(
            RxInstructionType.BUILD_CONTAINER_FOR_UPC_RECEIVING.getInstructionType()));
    instructions.add(
        getMockSplitPalletInstruction(
            RxInstructionType.BUILD_CONTAINER_FOR_UPC_RECEIVING.getInstructionType()));
    instructions.add(
        getMockSplitPalletInstruction(
            RxInstructionType.BUILD_CONTAINER_FOR_UPC_RECEIVING.getInstructionType()));

    SlotDetails mockSlotDetails = new SlotDetails();
    mockSlotDetails.setSlot("MOCK_SLOT");
    mockSlotDetails.setSlotSize(72);

    ReceiveContainersResponseBody receiveContainersResponseBody =
        nimRdsServiceImpl.acquireSlotForSplitPallet(
            instructions, mockSlotDetails, MockRxHttpHeaders.getHeaders());
    assertNotNull(receiveContainersResponseBody);

    verify(nimRDSRestApiClient, times(1))
        .receiveContainers(any(ReceiveContainersRequestBody.class), anyMap());
  }

  @Test
  public void test_acquireSlotForSplitPallet_manual_slot_non_exempt() {

    doReturn(new ReceiveContainersResponseBody())
        .when(nimRDSRestApiClient)
        .receiveContainers(any(ReceiveContainersRequestBody.class), anyMap());

    List<Instruction> instructions = new ArrayList<>();
    instructions.add(
        getMockSplitPalletInstruction(
            RxInstructionType.BUILDCONTAINER_SCAN_BY_GTIN_LOT.getInstructionType()));
    instructions.add(
        getMockSplitPalletInstruction(
            RxInstructionType.BUILDCONTAINER_SCAN_BY_GTIN_LOT.getInstructionType()));
    instructions.add(
        getMockSplitPalletInstruction(
            RxInstructionType.BUILDCONTAINER_SCAN_BY_GTIN_LOT.getInstructionType()));

    SlotDetails mockSlotDetails = new SlotDetails();
    mockSlotDetails.setSlot("MOCK_SLOT");
    mockSlotDetails.setSlotSize(72);

    ReceiveContainersResponseBody receiveContainersResponseBody =
        nimRdsServiceImpl.acquireSlotForSplitPallet(
            instructions, mockSlotDetails, MockRxHttpHeaders.getHeaders());
    assertNotNull(receiveContainersResponseBody);

    verify(nimRDSRestApiClient, times(1))
        .receiveContainers(any(ReceiveContainersRequestBody.class), anyMap());
  }

  private Instruction getMockSplitPalletInstruction(String instructionCode) {
    Instruction instruction = new Instruction();
    instruction.setInstructionCode(instructionCode);
    instruction.setPurchaseReferenceNumber("123456789");
    instruction.setPurchaseReferenceLineNumber(1);
    instruction.setDeliveryNumber(999999l);
    instruction.setReceivedQuantity(20);
    instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.EACHES);
    instruction.setInstructionSetId(1l);

    ContainerDetails mockContainerDetails = new ContainerDetails();
    mockContainerDetails.setTrackingId("MOCK_CONTAINER_TRACKING_ID");
    Content mockContent = new Content();
    mockContent.setVendorPack(100);
    mockContent.setWarehousePack(10);
    mockContent.setLot("MOCK_LOT_1");
    mockContent.setQty(5);
    mockContent.setQtyUom("ZA");

    mockContainerDetails.setContents(Arrays.asList(mockContent));
    instruction.setContainer(mockContainerDetails);

    ContainerDetails mockContainerChildDetails = new ContainerDetails();
    Content mockChildContent = new Content();
    mockChildContent.setVendorPack(100);
    mockChildContent.setWarehousePack(10);
    mockChildContent.setLot("MOCK_LOT_1");
    mockChildContent.setQty(5);
    mockChildContent.setQtyUom("ZA");
    mockContainerChildDetails.setContents(Arrays.asList(mockChildContent));

    instruction.setChildContainers(Arrays.asList(mockContainerChildDetails));

    LinkedTreeMap<String, Object> mockMove = new LinkedTreeMap();
    mockMove.put(ReceivingConstants.MOVE_FROM_LOCATION, 999);
    instruction.setMove(mockMove);

    return instruction;
  }
}
