package com.walmart.move.nim.receiving.rx.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingConflictException;
import com.walmart.move.nim.receiving.core.common.validators.InstructionStateValidator;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.*;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.data.MockRxHttpHeaders;
import com.walmart.move.nim.receiving.rx.builders.RxContainerItemBuilder;
import com.walmart.move.nim.receiving.rx.builders.RxReceiptsBuilder;
import com.walmart.move.nim.receiving.rx.config.RxManagedConfig;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.mock.MockInstruction;
import com.walmart.move.nim.receiving.rx.mock.RxMockContainer;
import com.walmart.move.nim.receiving.rx.model.RxInstructionType;
import com.walmart.move.nim.receiving.rx.service.v2.data.UpdateInstructionServiceHelper;
import com.walmart.move.nim.receiving.rx.service.v2.validation.data.UpdateInstructionDataValidator;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.InstructionStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.mockito.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RxUpdateInstructionHandlerTest {

  @Mock private InstructionPersisterService instructionPersisterService;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Mock private InstructionHelperService instructionHelperService;

  @Mock private LPNCacheService lpnCacheService;

  @Mock private EpcisService epcisService;

  @Mock private ContainerItemRepository containerItemRepository;

  @Mock private ContainerService containerService;

  @Mock private ReceiptService receiptService;

  @Mock private AppConfig appConfig;

  @Spy private RxReceiptsBuilder rxReceiptsBuilder = new RxReceiptsBuilder();

  @InjectMocks @Spy private RxInstructionHelperService rxInstructionHelperService;

  @Spy private RxContainerItemBuilder containerItemBuilder = new RxContainerItemBuilder();

  @Mock private ContainerItemService containerItemService;

  @Mock private ProblemService problemService;

  @Mock private RxManagedConfig rxManagedConfig;

  @InjectMocks private RxUpdateInstructionHandler rxUpdateInstructionHandler;

  @Mock private ContainerPersisterService containerPersisterService;

  @Mock private ShipmentSelectorService shipmentSelector;
  @Mock
  private UpdateInstructionServiceHelper updateInstructionServiceHelper;
  @Mock
  private UpdateInstructionDataValidator updateInstructionDataValidator;

  private Gson gson = new Gson();

  @BeforeClass
  public void setUp() throws Exception {

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32612);

    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(rxUpdateInstructionHandler, "gson", gson);
    ReflectionTestUtils.setField(
        rxUpdateInstructionHandler, "instructionStateValidator", new InstructionStateValidator());
    ReflectionTestUtils.setField(rxUpdateInstructionHandler, "receiptService", receiptService);
    ReflectionTestUtils.setField(rxInstructionHelperService, "gson", gson);
    ReflectionTestUtils.setField(
        rxUpdateInstructionHandler, "rxInstructionHelperService", rxInstructionHelperService);

    ReflectionTestUtils.setField(containerItemBuilder, "appConfig", appConfig);
    ReflectionTestUtils.setField(
        rxUpdateInstructionHandler, "containerItemBuilder", containerItemBuilder);
    ReflectionTestUtils.setField(rxUpdateInstructionHandler, "updateInstructionDataValidator", updateInstructionDataValidator);
    doReturn(false).when(appConfig).isCloseDateCheckEnabled();
  }

  @AfterMethod
  public void tearDown() {
    reset(instructionPersisterService);
    reset(tenantSpecificConfigReader);
    reset(instructionHelperService);
    reset(lpnCacheService);
    reset(epcisService);
    reset(containerItemRepository);
    reset(containerService);
    reset(receiptService);
    reset(appConfig);

    reset(rxReceiptsBuilder);
    reset(containerItemBuilder);
    reset(containerItemService);
    reset(rxInstructionHelperService);
    reset(shipmentSelector);
  }

  @BeforeMethod
  public void beforeMethod() {
    ShipmentDetails shipmentDetails = new ShipmentDetails();
    shipmentDetails.setShipperId("ShipperId");
    shipmentDetails.setSourceGlobalLocationNumber("32898");
    shipmentDetails.setShipmentNumber("1234566");
    shipmentDetails.setLoadNumber("1230000");
    shipmentDetails.setDestinationGlobalLocationNumber("32898");
    shipmentDetails.setShipperId("12355");
    shipmentDetails.setInboundShipmentDocId("shipmentdocumentId");
    shipmentDetails.setShippedQty(72);
    shipmentDetails.setShippedQtyUom("EA");
    doReturn(shipmentDetails)
        .when(shipmentSelector)
        .autoSelectShipment(any(DeliveryDocumentLine.class));
  }

  @Test
  public void testUpdateUpcInstruction_first_case() throws ReceivingException {

    doReturn(getMockNewInstruction()).when(instructionPersisterService).getInstructionById(12345l);
    doReturn(0l)
        .when(receiptService)
        .receivedQtyByDeliveryPoAndPoLineInEaches(anyLong(), anyString(), anyInt());
    doNothing()
        .when(epcisService)
        .verifySerializedData(
            anyMap(),
            any(ShipmentDetails.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class));
    doReturn("MOCK_UNIT_TEST_TRACKING_ID")
        .when(lpnCacheService)
        .getLPNBasedOnTenant(any(HttpHeaders.class));
    doReturn(RxMockContainer.getContainer())
        .when(containerService)
        .constructContainer(any(), any(), any(), any(), any());
    doReturn(RxMockContainer.getContainer().getContainerItems().get(0))
            .when(containerItemBuilder)
            .build(any(), any(), any(), any());

    ArgumentCaptor<List<Receipt>> receiptListCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<List<ContainerItem>> containerItemsListCaptor =
        ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<List<Container>> containerListCaptor = ArgumentCaptor.forClass(List.class);
    doNothing()
        .when(rxInstructionHelperService)
        .persistForUpdateInstruction(
            any(),
            receiptListCaptor.capture(),
            containerListCaptor.capture(),
            containerItemsListCaptor.capture());

    UpdateInstructionRequest updateInstructionRequest = getMockUpdateInstructionRequest();
    updateInstructionRequest.getDeliveryDocumentLines().get(0).setQuantity(1);
    updateInstructionRequest.getDeliveryDocumentLines().get(0).setQuantityUOM("ZA");
    updateInstructionRequest.setScannedDataList(getMockScannedDataList());
    doNothing()
            .when(updateInstructionDataValidator)
            .validateContainerDoesNotAlreadyExist(anyInt(), anyString(), anyString(), anyInt(), anyString());
    HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();
    InstructionResponse updateInstructionResponse =
        rxUpdateInstructionHandler.updateInstruction(
            12345l, updateInstructionRequest, null, httpHeaders);

    assertNotNull(updateInstructionResponse);

    assertEquals(updateInstructionResponse.getInstruction().getReceivedQuantity(), 1);
    assertEquals(updateInstructionResponse.getInstruction().getReceivedQuantityUOM(), "ZA");

    assertEquals(updateInstructionResponse.getInstruction().getActivityName(), "RxSSTK");
    assertEquals(updateInstructionResponse.getInstruction().getProviderId(), "RxSSTK");
    assertEquals(updateInstructionResponse.getInstruction().getInstructionMsg(), "RxBuildPallet");
    assertEquals(updateInstructionResponse.getInstruction().getInstructionCode(), "RxBuildPallet");

    assertNotNull(updateInstructionResponse.getInstruction().getContainer());
    assertTrue(CollectionUtils.isNotEmpty(containerListCaptor.getValue()));
    assertEquals(
        containerListCaptor
            .getValue()
            .get(0)
            .getContainerMiscInfo()
            .get(RxConstants.INSTRUCTION_CODE)
            .toString(),
        "RxBuildPallet");
    if (!rxManagedConfig.isTrimUpdateInstructionResponseEnabled()) {
      assertTrue(
          CollectionUtils.isNotEmpty(
              updateInstructionResponse.getInstruction().getChildContainers()));

      assertEquals(updateInstructionResponse.getInstruction().getChildContainers().size(), 1);
    }
    assertEquals(receiptListCaptor.getValue().size(), 1);
    assertSame(receiptListCaptor.getValue().get(0).getQuantity(), 1);
    assertSame(receiptListCaptor.getValue().get(0).getEachQty(), 6);

    assertTrue(CollectionUtils.isNotEmpty(containerItemsListCaptor.getValue()));
    assertNotNull(containerItemsListCaptor.getValue().get(0).getRotateDate());
    assertNotNull(containerItemsListCaptor.getValue().get(1).getRotateDate());
    assertEquals(containerItemsListCaptor.getValue().get(1).getLotNumber(), "00L032C09A");
    assertEquals(containerItemsListCaptor.getValue().get(1).getSerial(), "SN345678");

    verify(instructionPersisterService, times(1)).getInstructionById(12345l);
    verify(receiptService, times(1))
        .receivedQtyByDeliveryPoAndPoLineInEaches(anyLong(), anyString(), anyInt());
    verify(epcisService, times(1))
        .verifySerializedData(
            anyMap(),
            any(ShipmentDetails.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class));
    verify(rxInstructionHelperService, times(1))
        .persistForUpdateInstruction(any(), any(), any(), any());
  }

  @Test
  public void testUpdateUpcInstructionForPalletSsccReceiving_invalid_lpn() {

    try {
      doReturn(getMockNewInstructionForPalletSscc())
          .when(instructionPersisterService)
          .getInstructionById(12345l);
      doReturn(0l)
          .when(receiptService)
          .receivedQtyByDeliveryPoAndPoLine(anyLong(), anyString(), anyInt());
      doNothing()
          .when(epcisService)
          .verifySerializedData(
              anyMap(),
              any(ShipmentDetails.class),
              any(DeliveryDocumentLine.class),
              any(HttpHeaders.class));

      ArgumentCaptor<List<Receipt>> receiptListCaptor = ArgumentCaptor.forClass(List.class);
      doReturn(Collections.emptyList()).when(receiptService).saveAll(receiptListCaptor.capture());

      UpdateInstructionRequest updateInstructionRequest = getMockUpdateInstructionRequest();
      updateInstructionRequest.getDeliveryDocumentLines().get(0).setQuantity(1);
      updateInstructionRequest.getDeliveryDocumentLines().get(0).setQuantityUOM("ZA");
      updateInstructionRequest.setScannedDataList(getMockScannedDataList());
      doNothing()
              .when(updateInstructionDataValidator)
              .validateContainerDoesNotAlreadyExist(anyInt(), anyString(), anyString(), anyInt(), anyString());
      HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();
      InstructionResponse updateInstructionResponse =
          rxUpdateInstructionHandler.updateInstruction(
              12345l, updateInstructionRequest, null, httpHeaders);

      assertNotNull(updateInstructionResponse);

      assertEquals(updateInstructionResponse.getInstruction().getReceivedQuantity(), 1);
      assertEquals(updateInstructionResponse.getInstruction().getReceivedQuantityUOM(), "ZA");

      assertEquals(updateInstructionResponse.getInstruction().getActivityName(), "RxSSTK");
      assertEquals(updateInstructionResponse.getInstruction().getProviderId(), "RxSSTK");
      assertEquals(updateInstructionResponse.getInstruction().getInstructionMsg(), "RxBuildPallet");
      assertEquals(
          updateInstructionResponse.getInstruction().getInstructionCode(), "RxBuildPallet");

      assertNotNull(updateInstructionResponse.getInstruction().getContainer());
      assertTrue(
          CollectionUtils.isNotEmpty(
              updateInstructionResponse.getInstruction().getChildContainers()));

      assertEquals(updateInstructionResponse.getInstruction().getChildContainers().size(), 1);

      assertEquals(receiptListCaptor.getValue().size(), 1);
      assertSame(receiptListCaptor.getValue().get(0).getQuantity(), 1);
      assertSame(receiptListCaptor.getValue().get(0).getEachQty(), 6);

      verify(instructionPersisterService, times(1)).getInstructionById(12345l);
      verify(receiptService, times(2))
          .receivedQtyByDeliveryPoAndPoLine(anyLong(), anyString(), anyInt());
      verify(epcisService, times(1))
          .verifySerializedData(
              anyMap(),
              any(ShipmentDetails.class),
              any(DeliveryDocumentLine.class),
              any(HttpHeaders.class));
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.INVALID_LPN);
      assertEquals(e.getDescription(), RxConstants.INVALID_LPN);

    } catch (ReceivingException e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testUpdateUpcInstructionForPalletSsccReceiving() throws ReceivingException {

    doReturn(getMockNewInstructionForPalletSscc())
        .when(instructionPersisterService)
        .getInstructionById(12345l);
    doReturn(0l)
        .when(receiptService)
        .receivedQtyByDeliveryPoAndPoLineInEaches(anyLong(), anyString(), anyInt());
    doNothing()
        .when(epcisService)
        .verifySerializedData(
            anyMap(),
            any(ShipmentDetails.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class));
    doReturn("MOCK_UNIT_TEST_TRACKING_ID")
        .when(lpnCacheService)
        .getLPNBasedOnTenant(any(HttpHeaders.class));
    doReturn(RxMockContainer.getContainer())
        .when(containerService)
        .constructContainer(any(), any(), any(), any(), any());
    doReturn(RxMockContainer.getMockContainerItem().get(0))
        .when(containerItemBuilder)
        .build(any(), any(), any(), any());

    ArgumentCaptor<List<Container>> containerListCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<List<Receipt>> receiptListCaptor = ArgumentCaptor.forClass(List.class);
    doNothing()
        .when(rxInstructionHelperService)
        .persistForUpdateInstruction(
            any(), receiptListCaptor.capture(), containerListCaptor.capture(), any());

    UpdateInstructionRequest updateInstructionRequest = getMockUpdateInstructionRequest();
    updateInstructionRequest.getDeliveryDocumentLines().get(0).setQuantity(1);
    updateInstructionRequest.getDeliveryDocumentLines().get(0).setQuantityUOM("ZA");
    updateInstructionRequest.setScannedDataList(getMockScannedDataList());
    doNothing()
            .when(updateInstructionDataValidator)
            .validateContainerDoesNotAlreadyExist(anyInt(), anyString(), anyString(), anyInt(), anyString());
    HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();
    InstructionResponse updateInstructionResponse =
        rxUpdateInstructionHandler.updateInstruction(
            12345l, updateInstructionRequest, null, httpHeaders);

    assertNotNull(updateInstructionResponse);

    assertEquals(updateInstructionResponse.getInstruction().getReceivedQuantity(), 1);
    assertEquals(updateInstructionResponse.getInstruction().getReceivedQuantityUOM(), "ZA");

    assertEquals(updateInstructionResponse.getInstruction().getActivityName(), "RxSSTK");
    assertEquals(updateInstructionResponse.getInstruction().getProviderId(), "RxSSTK");
    assertEquals(updateInstructionResponse.getInstruction().getInstructionMsg(), "RxBuildPallet");
    assertEquals(updateInstructionResponse.getInstruction().getInstructionCode(), "RxBuildPallet");

    assertNotNull(updateInstructionResponse.getInstruction().getContainer());
    assertEquals(
        containerListCaptor
            .getValue()
            .get(0)
            .getContainerMiscInfo()
            .get(RxConstants.INSTRUCTION_CODE)
            .toString(),
        "RxBuildPallet");
    if (!rxManagedConfig.isTrimUpdateInstructionResponseEnabled()) {
      assertTrue(
          CollectionUtils.isNotEmpty(
              updateInstructionResponse.getInstruction().getChildContainers()));

      assertEquals(updateInstructionResponse.getInstruction().getChildContainers().size(), 1);
    }
    assertEquals(receiptListCaptor.getValue().size(), 1);
    assertSame(receiptListCaptor.getValue().get(0).getQuantity(), 1);
    assertSame(receiptListCaptor.getValue().get(0).getEachQty(), 6);

    verify(instructionPersisterService, times(1)).getInstructionById(12345l);
    verify(receiptService, times(1))
        .receivedQtyByDeliveryPoAndPoLineInEaches(anyLong(), anyString(), anyInt());
    verify(epcisService, times(1))
        .verifySerializedData(
            anyMap(),
            any(ShipmentDetails.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class));
  }

  @Test
  public void testUpdateUpcInstruction_ReceiveFirstEach() throws ReceivingException {

    UpdateInstructionRequest updateInstructionRequest = getMockUpdateInstructionRequest();
    updateInstructionRequest.getDeliveryDocumentLines().get(0).setQuantity(1);
    updateInstructionRequest
        .getDeliveryDocumentLines()
        .get(0)
        .setQuantityUOM(ReceivingConstants.Uom.EACHES);

    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData expScannedData = new ScannedData();
    expScannedData.setKey("expiryDate");
    expScannedData.setValue("200505");
    scannedDataList.add(expScannedData);

    ScannedData gtinScannedData = new ScannedData();
    gtinScannedData.setKey("gtin");
    gtinScannedData.setValue("01123840356119");
    scannedDataList.add(gtinScannedData);

    ScannedData lotScannedData = new ScannedData();
    lotScannedData.setKey("lot");
    lotScannedData.setValue("00L032C09A");
    scannedDataList.add(lotScannedData);

    ScannedData serialScannedData = new ScannedData();
    serialScannedData.setKey("serial");
    serialScannedData.setValue("SN345678");
    scannedDataList.add(serialScannedData);

    updateInstructionRequest.setScannedDataList(scannedDataList);
    doNothing()
        .when(epcisService)
        .verifySerializedData(
            anyMap(),
            any(ShipmentDetails.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class));
    doReturn(getMockNewInstruction()).when(instructionPersisterService).getInstructionById(12345l);
    doReturn(5L)
        .when(receiptService)
        .receivedQtyByDeliveryPoAndPoLine(anyLong(), anyString(), anyInt());
    doNothing()
        .when(instructionPersisterService)
        .processCreateChildContainers(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(ContainerDetails.class),
            any(ContainerDetails.class),
            anyString());
    doReturn("MOCK_UNIT_TEST_TRACKING_ID")
        .when(lpnCacheService)
        .getLPNBasedOnTenant(any(HttpHeaders.class));

    doReturn(RxMockContainer.getContainer())
        .when(containerService)
        .constructContainer(any(), any(), any(), any(), any());
    doReturn(RxMockContainer.getMockContainerItem().get(0))
        .when(containerItemBuilder)
        .build(any(), any(), any(), any());

    ArgumentCaptor<List<Receipt>> receiptListCaptor = ArgumentCaptor.forClass(List.class);
    doNothing()
        .when(rxInstructionHelperService)
        .persistForUpdateInstruction(any(), receiptListCaptor.capture(), any(), any());
    doNothing()
            .when(updateInstructionDataValidator)
            .validateContainerDoesNotAlreadyExist(anyInt(), anyString(), anyString(), anyInt(), anyString());
    InstructionResponse updateInstructionResponse =
        rxUpdateInstructionHandler.updateInstruction(
            12345l, updateInstructionRequest, "", MockRxHttpHeaders.getHeaders());

    assertNotNull(updateInstructionResponse);
    assertEquals(updateInstructionResponse.getInstruction().getReceivedQuantity(), 0);
    if (!rxManagedConfig.isTrimUpdateInstructionResponseEnabled()) {
      assertEquals(updateInstructionResponse.getInstruction().getChildContainers().size(), 1);
    }

    verify(instructionPersisterService, times(1)).getInstructionById(12345l);
    verify(epcisService, times(1))
        .verifySerializedData(
            anyMap(),
            any(ShipmentDetails.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testUpdateUpcInstruction_other_instruction_received_all_cases()
      throws ReceivingException {

    UpdateInstructionRequest updateInstructionRequest = getMockUpdateInstructionRequest();

    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData expScannedData = new ScannedData();
    expScannedData.setKey("expiryDate");
    expScannedData.setValue("20-05-05");
    scannedDataList.add(expScannedData);

    ScannedData gtinScannedData = new ScannedData();
    gtinScannedData.setKey("gtin");
    gtinScannedData.setValue("01123840356119");
    scannedDataList.add(gtinScannedData);

    ScannedData lotScannedData = new ScannedData();
    lotScannedData.setKey("lot");
    lotScannedData.setValue("12345678");
    scannedDataList.add(lotScannedData);

    ScannedData serialScannedData = new ScannedData();
    serialScannedData.setKey("serial");
    serialScannedData.setValue("SN345678");
    scannedDataList.add(serialScannedData);

    updateInstructionRequest.setScannedDataList(scannedDataList);
    doNothing()
        .when(epcisService)
        .verifySerializedData(
            anyMap(),
            any(ShipmentDetails.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class));
    doReturn(getMockNewInstruction()).when(instructionPersisterService).getInstructionById(12345l);
    doReturn(20L)
        .when(receiptService)
        .findReceiptsSummaryByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndSSCC(
            anyLong(), anyString(), anyInt(), anyString());
    doNothing()
        .when(instructionPersisterService)
        .processCreateChildContainers(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(ContainerDetails.class),
            any(ContainerDetails.class),
            anyString());

    ArgumentCaptor<List<Receipt>> receiptListCaptor = ArgumentCaptor.forClass(List.class);
    doReturn(Collections.emptyList()).when(receiptService).saveAll(receiptListCaptor.capture());

    InstructionResponse updateInstructionResponse =
        rxUpdateInstructionHandler.updateInstruction(
            12345l, updateInstructionRequest, "", MockRxHttpHeaders.getHeaders());

    assertNotNull(updateInstructionResponse);
    assertTrue(
        updateInstructionResponse
            .getInstruction()
            .getLabels()
            .getAvailableLabels()
            .contains("MOCK_UNIT_TEST_TRACKING_ID"));

    verify(instructionPersisterService, times(1)).getInstructionById(12345l);
    verify(instructionPersisterService, times(1))
        .createContainersAndPrintJobs(
            any(UpdateInstructionRequest.class),
            any(HttpHeaders.class),
            anyString(),
            any(Instruction.class),
            any(Integer.class),
            any(Integer.class),
            any(List.class));

    verify(lpnCacheService, times(1)).getLPNBasedOnTenant(any(HttpHeaders.class));
    verify(epcisService, times(1))
        .verifySerializedData(
            anyMap(),
            any(ShipmentDetails.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testUpdateUpcInstruction_multi_user_senario() throws ReceivingException {

    UpdateInstructionRequest updateInstructionRequest = getMockUpdateInstructionRequest();

    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData expScannedData = new ScannedData();
    expScannedData.setKey("expiryDate");
    expScannedData.setValue("20-05-05");
    scannedDataList.add(expScannedData);

    ScannedData gtinScannedData = new ScannedData();
    gtinScannedData.setKey("gtin");
    gtinScannedData.setValue("01123840356119");
    scannedDataList.add(gtinScannedData);

    ScannedData lotScannedData = new ScannedData();
    lotScannedData.setKey("lot");
    lotScannedData.setValue("12345678");
    scannedDataList.add(lotScannedData);

    ScannedData serialScannedData = new ScannedData();
    serialScannedData.setKey("serial");
    serialScannedData.setValue("SN345678");
    scannedDataList.add(serialScannedData);

    updateInstructionRequest.setScannedDataList(scannedDataList);
    doNothing()
        .when(epcisService)
        .verifySerializedData(
            anyMap(),
            any(ShipmentDetails.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class));
    doReturn(getMockNewInstruction()).when(instructionPersisterService).getInstructionById(12345l);
    doNothing()
        .when(instructionPersisterService)
        .processCreateChildContainers(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(ContainerDetails.class),
            any(ContainerDetails.class),
            anyString());

    ArgumentCaptor<List<Receipt>> receiptListCaptor = ArgumentCaptor.forClass(List.class);
    doReturn(Collections.emptyList()).when(receiptService).saveAll(receiptListCaptor.capture());

    HttpHeaders headers = MockRxHttpHeaders.getHeaders();
    // setting different user id in the header
    headers.set(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");

    rxUpdateInstructionHandler.updateInstruction(12345l, updateInstructionRequest, "", headers);
    verify(epcisService, times(1))
        .verifySerializedData(
            anyMap(),
            any(ShipmentDetails.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class));
  }

  @Test
  public void testUpdateUpcInstruction_completed_instruction_cancelled_pallet() {

    try {
      UpdateInstructionRequest updateInstructionRequest = getMockUpdateInstructionRequest();

      List<ScannedData> scannedDataList = new ArrayList<>();
      ScannedData expScannedData = new ScannedData();
      expScannedData.setKey("expiryDate");
      expScannedData.setValue("20-05-05");
      scannedDataList.add(expScannedData);

      ScannedData gtinScannedData = new ScannedData();
      gtinScannedData.setKey("gtin");
      gtinScannedData.setValue("01123840356119");
      scannedDataList.add(gtinScannedData);

      ScannedData lotScannedData = new ScannedData();
      lotScannedData.setKey("lot");
      lotScannedData.setValue("12345678");
      scannedDataList.add(lotScannedData);

      ScannedData serialScannedData = new ScannedData();
      serialScannedData.setKey("serial");
      serialScannedData.setValue("SN345678");
      scannedDataList.add(serialScannedData);

      updateInstructionRequest.setScannedDataList(scannedDataList);

      Instruction mockNewInstruction = getMockNewInstruction();
      // setting CompleteTs & CompleteUserId
      mockNewInstruction.setCompleteTs(new Date());
      mockNewInstruction.setCompleteUserId("rxTestUser");

      doNothing()
          .when(epcisService)
          .verifySerializedData(
              anyMap(),
              any(ShipmentDetails.class),
              any(DeliveryDocumentLine.class),
              any(HttpHeaders.class));
      doReturn(mockNewInstruction).when(instructionPersisterService).getInstructionById(12345l);
      doNothing()
          .when(instructionPersisterService)
          .processCreateChildContainers(
              any(Instruction.class),
              any(UpdateInstructionRequest.class),
              any(ContainerDetails.class),
              any(ContainerDetails.class),
              anyString());

      ArgumentCaptor<List<Receipt>> receiptListCaptor = ArgumentCaptor.forClass(List.class);
      doReturn(Collections.emptyList()).when(receiptService).saveAll(receiptListCaptor.capture());

      rxUpdateInstructionHandler.updateInstruction(
          12345l, updateInstructionRequest, "", MockRxHttpHeaders.getHeaders());
    } catch (ReceivingException e) {
      assertEquals(
          e.getErrorResponse().getErrorCode(),
          ReceivingException.COMPLETE_INSTRUCTION_ERROR_CODE_FOR_ALREADY_COMPLETED);
      assertEquals(
          e.getErrorResponse().getErrorHeader(), ReceivingException.ERROR_HEADER_PALLET_CANCELLED);
      assertEquals(
          e.getErrorResponse().getErrorMessage(),
          String.format(ReceivingException.COMPLETE_INSTRUCTION_PALLET_CANCELLED, "rxTestUser"));
      assertEquals(e.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @Test
  public void testUpdateUpcInstruction_completed_instruction_completed_pallet() {

    try {
      UpdateInstructionRequest updateInstructionRequest = getMockUpdateInstructionRequest();

      List<ScannedData> scannedDataList = new ArrayList<>();
      ScannedData expScannedData = new ScannedData();
      expScannedData.setKey("expiryDate");
      expScannedData.setValue("20-05-05");
      scannedDataList.add(expScannedData);

      ScannedData gtinScannedData = new ScannedData();
      gtinScannedData.setKey("gtin");
      gtinScannedData.setValue("01123840356119");
      scannedDataList.add(gtinScannedData);

      ScannedData lotScannedData = new ScannedData();
      lotScannedData.setKey("lot");
      lotScannedData.setValue("12345678");
      scannedDataList.add(lotScannedData);

      ScannedData serialScannedData = new ScannedData();
      serialScannedData.setKey("serial");
      serialScannedData.setValue("SN345678");
      scannedDataList.add(serialScannedData);

      updateInstructionRequest.setScannedDataList(scannedDataList);

      Instruction mockNewInstruction = getMockNewInstruction();
      mockNewInstruction.setReceivedQuantity(20);
      mockNewInstruction.setCompleteTs(new Date());
      mockNewInstruction.setCompleteUserId("rxTestUser");

      doNothing()
          .when(epcisService)
          .verifySerializedData(
              anyMap(),
              any(ShipmentDetails.class),
              any(DeliveryDocumentLine.class),
              any(HttpHeaders.class));
      doReturn(mockNewInstruction).when(instructionPersisterService).getInstructionById(12345l);
      doNothing()
          .when(instructionPersisterService)
          .processCreateChildContainers(
              any(Instruction.class),
              any(UpdateInstructionRequest.class),
              any(ContainerDetails.class),
              any(ContainerDetails.class),
              anyString());

      // publish instruction to WFM
      doNothing()
          .when(instructionHelperService)
          .publishInstruction(
              any(Instruction.class),
              any(UpdateInstructionRequest.class),
              any(Integer.class),
              any(Container.class),
              any(InstructionStatus.class),
              any(HttpHeaders.class));

      doReturn("MOCK_UNIT_TEST_TRACKING_ID")
          .when(lpnCacheService)
          .getLPNBasedOnTenant(any(HttpHeaders.class));

      rxUpdateInstructionHandler.updateInstruction(
          12345l, updateInstructionRequest, "", MockRxHttpHeaders.getHeaders());
    } catch (ReceivingException e) {
      assertEquals(
          e.getErrorResponse().getErrorCode(),
          ReceivingException.COMPLETE_INSTRUCTION_ERROR_CODE_FOR_ALREADY_COMPLETED);
      assertEquals(
          e.getErrorResponse().getErrorHeader(), ReceivingException.ERROR_HEADER_PALLET_COMPLETED);
      assertEquals(
          e.getErrorResponse().getErrorMessage(),
          String.format(ReceivingException.COMPLETE_INSTRUCTION_ALREADY_COMPLETE, "rxTestUser"));
      assertEquals(e.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @Test
  public void testUpdateUpcInstruction_counts_mismatch() {

    try {
      UpdateInstructionRequest updateInstructionRequest = getMockUpdateInstructionRequest();

      List<ScannedData> scannedDataList = new ArrayList<>();
      ScannedData expScannedData = new ScannedData();
      expScannedData.setKey("expiryDate");
      expScannedData.setValue("20-05-05");
      scannedDataList.add(expScannedData);

      ScannedData gtinScannedData = new ScannedData();
      gtinScannedData.setKey("gtin");
      gtinScannedData.setValue("01123840356119");
      scannedDataList.add(gtinScannedData);

      ScannedData lotScannedData = new ScannedData();
      lotScannedData.setKey("lot");
      lotScannedData.setValue("00L032C09A");
      scannedDataList.add(lotScannedData);

      ScannedData serialScannedData = new ScannedData();
      serialScannedData.setKey("serial");
      serialScannedData.setValue("SN345678");
      scannedDataList.add(serialScannedData);

      updateInstructionRequest.setScannedDataList(scannedDataList);

      Instruction mockNewInstruction = getMockNewInstruction();
      mockNewInstruction.setReceivedQuantity(10);
      mockNewInstruction.setProjectedReceiveQty(10);

      doNothing()
          .when(epcisService)
          .verifySerializedData(
              anyMap(),
              any(ShipmentDetails.class),
              any(DeliveryDocumentLine.class),
              any(HttpHeaders.class));
      doReturn(mockNewInstruction).when(instructionPersisterService).getInstructionById(12345l);
      doNothing()
          .when(instructionPersisterService)
          .processCreateChildContainers(
              any(Instruction.class),
              any(UpdateInstructionRequest.class),
              any(ContainerDetails.class),
              any(ContainerDetails.class),
              anyString());
      doNothing()
              .when(updateInstructionDataValidator)
              .validateContainerDoesNotAlreadyExist(anyInt(), anyString(), anyString(), anyInt(), anyString());
      doReturn("MOCK_UNIT_TEST_TRACKING_ID")
              .when(lpnCacheService)
              .getLPNBasedOnTenant(any(HttpHeaders.class));
      rxUpdateInstructionHandler.updateInstruction(
          12345l, updateInstructionRequest, "", MockRxHttpHeaders.getHeaders());
    } catch (ReceivingException e) {
      fail(e.getMessage());
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), "GLS-RCV-CASESRCVD-400");
      assertEquals(e.getMessage(), ReceivingException.UPDATE_INSTRUCTION_EXCEEDS_QUANTITY);
    }
  }

  @Test
  public void testUpdateUpcInstruction_no_rx_details() {

    try {
      UpdateInstructionRequest updateInstructionRequest = getMockUpdateInstructionRequest();
      // setting scannedDataList to null
      updateInstructionRequest.setScannedDataList(null);

      Instruction mockNewInstruction = getMockNewInstruction();
      mockNewInstruction.setReceivedQuantity(10);
      mockNewInstruction.setProjectedReceiveQty(10);
      doNothing()
          .when(epcisService)
          .verifySerializedData(
              anyMap(),
              any(ShipmentDetails.class),
              any(DeliveryDocumentLine.class),
              any(HttpHeaders.class));
      doReturn(mockNewInstruction).when(instructionPersisterService).getInstructionById(12345l);
      doNothing()
          .when(instructionPersisterService)
          .createContainersAndPrintJobs(
              any(UpdateInstructionRequest.class),
              any(HttpHeaders.class),
              anyString(),
              any(Instruction.class),
              any(Integer.class),
              any(Integer.class),
              any(List.class));

      rxUpdateInstructionHandler.updateInstruction(
          12345l, updateInstructionRequest, "", MockRxHttpHeaders.getHeaders());
    } catch (ReceivingException e) {
      fail(e.getMessage());
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), "GLS-RCV-INV-SCANDATA-400");
    }
  }

  @Test
  public void testUpdateUpcInstruction_UpcReceiving_null_lpn() {

    try {
      Instruction instruction = getMockNewInstruction();
      instruction.setSsccNumber("");
      instruction.setReceivedQuantityUOM("EA");
      instruction.setProjectedReceiveQty(120);
      instruction.setReceivedQuantityUOM("EA");
      instruction.setInstructionCode(
          RxInstructionType.BUILD_CONTAINER_FOR_UPC_RECEIVING.getInstructionType());
      instruction.setInstructionMsg(
          RxInstructionType.BUILD_CONTAINER_FOR_UPC_RECEIVING.getInstructionMsg());
      doReturn(instruction).when(instructionPersisterService).getInstructionById(12345l);
      doReturn(null).when(lpnCacheService).getLPNBasedOnTenant(any(HttpHeaders.class));

      UpdateInstructionRequest updateInstructionRequest = getMockUpdateInstructionRequest();
      updateInstructionRequest.getDeliveryDocumentLines().get(0).setQuantity(6);
      updateInstructionRequest.getDeliveryDocumentLines().get(0).setQuantityUOM("ZA");

      List<ScannedData> scannedDataList = new ArrayList<>();
      ScannedData barCodeScannedData = new ScannedData();
      barCodeScannedData.setKey("GTIN");
      barCodeScannedData.setApplicationIdentifier("01");
      barCodeScannedData.setValue("10368382330069");
      scannedDataList.add(barCodeScannedData);
      updateInstructionRequest.setScannedDataList(scannedDataList);

      HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();
      InstructionResponse updateInstructionResponse =
          rxUpdateInstructionHandler.updateInstruction(
              12345l, updateInstructionRequest, null, httpHeaders);

      assertNotNull(updateInstructionResponse);

      assertNotNull(updateInstructionResponse.getInstruction().getContainer());

      verify(instructionPersisterService, times(1)).getInstructionById(12345l);
      verify(containerService, times(1)).processCreateContainers(any(), any(), any());
      verify(receiptService, times(1)).saveAll(any());
      verify(epcisService, times(0))
          .verifySerializedData(
              anyMap(),
              any(ShipmentDetails.class),
              any(DeliveryDocumentLine.class),
              any(HttpHeaders.class));
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.INVALID_LPN);
      assertEquals(e.getDescription(), RxConstants.INVALID_LPN);

    } catch (ReceivingException e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testUpdateUpcInstruction_UpcReceiving() throws ReceivingException {
    Instruction instruction = getMockNewInstruction();
    instruction.setReceivedQuantityUOM("EA");
    instruction.setProjectedReceiveQty(120);
    instruction.setReceivedQuantityUOM("EA");
    instruction.setSsccNumber("");
    instruction.setInstructionCode(
        RxInstructionType.BUILD_CONTAINER_FOR_UPC_RECEIVING.getInstructionType());
    instruction.setInstructionMsg(
        RxInstructionType.BUILD_CONTAINER_FOR_UPC_RECEIVING.getInstructionMsg());
    doReturn(instruction).when(instructionPersisterService).getInstructionById(12345l);
    doReturn("MOCK_UNIT_TEST_TRACKING_ID")
        .when(lpnCacheService)
        .getLPNBasedOnTenant(any(HttpHeaders.class));
    doReturn(RxMockContainer.getContainer())
        .when(containerService)
        .constructContainer(any(), any(), any(), any(), any());
    doReturn(RxMockContainer.getMockContainerItem().get(0))
        .when(containerItemBuilder)
        .build(any(), any(), any(), any());

    ArgumentCaptor<List<Container>> containerListCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<List<Receipt>> receiptListCaptor = ArgumentCaptor.forClass(List.class);
    doNothing()
        .when(rxInstructionHelperService)
        .persistForUpdateInstruction(
            any(), receiptListCaptor.capture(), containerListCaptor.capture(), any());

    UpdateInstructionRequest updateInstructionRequest = getMockUpdateInstructionRequest();
    updateInstructionRequest.getDeliveryDocumentLines().get(0).setQuantity(20);
    updateInstructionRequest.getDeliveryDocumentLines().get(0).setQuantityUOM("ZA");

    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData barCodeScannedData = new ScannedData();
    barCodeScannedData.setKey("GTIN");
    barCodeScannedData.setApplicationIdentifier("01");
    barCodeScannedData.setValue("10368382330069");
    scannedDataList.add(barCodeScannedData);
    updateInstructionRequest.setScannedDataList(scannedDataList);

    HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();
    InstructionResponse updateInstructionResponse =
        rxUpdateInstructionHandler.updateInstruction(
            12345l, updateInstructionRequest, null, httpHeaders);

    assertNotNull(updateInstructionResponse);

    assertNotNull(updateInstructionResponse.getInstruction().getContainer());
    assertEquals(receiptListCaptor.getValue().size(), 1);
    assertSame(receiptListCaptor.getValue().get(0).getQuantity(), 20);
    assertSame(receiptListCaptor.getValue().get(0).getEachQty(), 120);
    assertEquals(
        containerListCaptor
            .getValue()
            .get(0)
            .getContainerMiscInfo()
            .get(RxConstants.INSTRUCTION_CODE)
            .toString(),
        "Build Container");

    verify(instructionPersisterService, times(1)).getInstructionById(12345l);
    verify(epcisService, times(0))
        .verifySerializedData(
            anyMap(),
            any(ShipmentDetails.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class));
  }

  @Test
  public void testUpdateUpcInstruction_UpcReceiving_RollbackReceiptsIfPresent()
      throws ReceivingException {
    Instruction instruction = getMockNewInstruction();
    instruction.setReceivedQuantityUOM("EA");
    instruction.setProjectedReceiveQty(120);
    instruction.setReceivedQuantity(30);
    instruction.setReceivedQuantityUOM("EA");
    instruction.setSsccNumber("");
    instruction.setInstructionCode(
        RxInstructionType.BUILD_CONTAINER_FOR_UPC_RECEIVING.getInstructionType());
    instruction.setInstructionMsg(
        RxInstructionType.BUILD_CONTAINER_FOR_UPC_RECEIVING.getInstructionMsg());
    doReturn(instruction).when(instructionPersisterService).getInstructionById(12345l);
    doReturn("MOCK_UNIT_TEST_TRACKING_ID")
        .when(lpnCacheService)
        .getLPNBasedOnTenant(any(HttpHeaders.class));
    doReturn(RxMockContainer.getContainer())
        .when(containerService)
        .constructContainer(any(), any(), any(), any(), any());
    doReturn(RxMockContainer.getMockContainerItem().get(0))
        .when(containerItemBuilder)
        .build(any(), any(), any(), any());

    ArgumentCaptor<List<Receipt>> receiptListCaptor = ArgumentCaptor.forClass(List.class);
    doNothing()
        .when(rxInstructionHelperService)
        .persistForUpdateInstruction(any(), receiptListCaptor.capture(), any(), any());

    UpdateInstructionRequest updateInstructionRequest = getMockUpdateInstructionRequest();
    updateInstructionRequest.getDeliveryDocumentLines().get(0).setQuantity(20);
    updateInstructionRequest.getDeliveryDocumentLines().get(0).setQuantityUOM("ZA");

    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData barCodeScannedData = new ScannedData();
    barCodeScannedData.setKey("GTIN");
    barCodeScannedData.setApplicationIdentifier("01");
    barCodeScannedData.setValue("10368382330069");
    scannedDataList.add(barCodeScannedData);
    updateInstructionRequest.setScannedDataList(scannedDataList);

    HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();
    InstructionResponse updateInstructionResponse =
        rxUpdateInstructionHandler.updateInstruction(
            12345l, updateInstructionRequest, null, httpHeaders);

    assertNotNull(updateInstructionResponse);

    assertNotNull(updateInstructionResponse.getInstruction().getContainer());
    assertEquals(receiptListCaptor.getValue().size(), 2);
    assertSame(receiptListCaptor.getValue().get(0).getEachQty(), -30);
    assertSame(receiptListCaptor.getValue().get(1).getEachQty(), 120);

    verify(instructionPersisterService, times(1)).getInstructionById(12345l);
    verify(epcisService, times(0))
        .verifySerializedData(
            anyMap(),
            any(ShipmentDetails.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class));
  }

  @Test
  public void testUpdateUpcInstruction_UpcReceiving_Partials() throws ReceivingException {
    Instruction instruction = getMockNewInstruction();
    instruction.setSsccNumber("");
    instruction.setInstructionCode(
        RxInstructionType.BUILD_PARTIAL_CONTAINER_UPC_RECEIVING.getInstructionType());
    instruction.setInstructionMsg(
        RxInstructionType.BUILD_PARTIAL_CONTAINER_UPC_RECEIVING.getInstructionMsg());
    doReturn(instruction).when(instructionPersisterService).getInstructionById(12345l);
    doReturn("MOCK_UNIT_TEST_TRACKING_ID")
        .when(lpnCacheService)
        .getLPNBasedOnTenant(any(HttpHeaders.class));
    doReturn(RxMockContainer.getContainer())
        .when(containerService)
        .constructContainer(any(), any(), any(), any(), any());
    doReturn(RxMockContainer.getMockContainerItem().get(0))
        .when(containerItemBuilder)
        .build(any(), any(), any(), any());

    ArgumentCaptor<List<Receipt>> receiptListCaptor = ArgumentCaptor.forClass(List.class);
    doNothing()
        .when(rxInstructionHelperService)
        .persistForUpdateInstruction(any(), receiptListCaptor.capture(), any(), any());

    UpdateInstructionRequest updateInstructionRequest = getMockUpdateInstructionRequest();
    updateInstructionRequest
        .getDeliveryDocumentLines()
        .get(0)
        .setQuantity(instruction.getProjectedReceiveQty());
    updateInstructionRequest.getDeliveryDocumentLines().get(0).setQuantityUOM("EA");
    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData barCodeScannedData = new ScannedData();
    barCodeScannedData.setKey("GTIN");
    barCodeScannedData.setApplicationIdentifier("01");
    barCodeScannedData.setValue("10368382330069");
    scannedDataList.add(barCodeScannedData);
    updateInstructionRequest.setScannedDataList(scannedDataList);

    HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();
    InstructionResponse updateInstructionResponse =
        rxUpdateInstructionHandler.updateInstruction(
            12345l, updateInstructionRequest, null, httpHeaders);
    assertNotNull(updateInstructionResponse);
    assertEquals(
        updateInstructionResponse.getInstruction().getReceivedQuantityUOM(),
        ReceivingConstants.Uom.WHPK);
    assertNotNull(updateInstructionResponse.getInstruction().getContainer());
    verify(instructionPersisterService, times(1)).getInstructionById(12345l);
    verify(epcisService, times(0))
        .verifySerializedData(
            anyMap(),
            any(ShipmentDetails.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class));
  }

  @Test
  public void testUpdateUpcInstruction_UpcReceiving_Partials_FullVendorPack()
      throws ReceivingException {
    Instruction instruction = getMockNewInstruction();
    instruction.setSsccNumber("");
    instruction.setInstructionCode(
        RxInstructionType.BUILD_PARTIAL_CONTAINER_UPC_RECEIVING.getInstructionType());
    instruction.setInstructionMsg(
        RxInstructionType.BUILD_PARTIAL_CONTAINER_UPC_RECEIVING.getInstructionMsg());
    doReturn(instruction).when(instructionPersisterService).getInstructionById(12345l);
    doReturn("MOCK_UNIT_TEST_TRACKING_ID")
        .when(lpnCacheService)
        .getLPNBasedOnTenant(any(HttpHeaders.class));
    doReturn(RxMockContainer.getContainer())
        .when(containerService)
        .constructContainer(any(), any(), any(), any(), any());
    doReturn(RxMockContainer.getMockContainerItem().get(0))
        .when(containerItemBuilder)
        .build(any(), any(), any(), any());

    ArgumentCaptor<List<Receipt>> receiptListCaptor = ArgumentCaptor.forClass(List.class);
    doNothing()
        .when(rxInstructionHelperService)
        .persistForUpdateInstruction(any(), receiptListCaptor.capture(), any(), any());

    UpdateInstructionRequest updateInstructionRequest = getMockUpdateInstructionRequest();
    updateInstructionRequest
        .getDeliveryDocumentLines()
        .get(0)
        .setQuantity(updateInstructionRequest.getDeliveryDocumentLines().get(0).getVnpkQty());
    updateInstructionRequest.getDeliveryDocumentLines().get(0).setQuantityUOM("EA");
    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData barCodeScannedData = new ScannedData();
    barCodeScannedData.setKey("GTIN");
    barCodeScannedData.setApplicationIdentifier("01");
    barCodeScannedData.setValue("10368382330069");
    scannedDataList.add(barCodeScannedData);
    updateInstructionRequest.setScannedDataList(scannedDataList);

    HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();
    InstructionResponse updateInstructionResponse =
        rxUpdateInstructionHandler.updateInstruction(
            12345l, updateInstructionRequest, null, httpHeaders);
    assertNotNull(updateInstructionResponse);
    assertEquals(
        updateInstructionResponse.getInstruction().getReceivedQuantityUOM(),
        ReceivingConstants.Uom.WHPK);
    assertEquals(updateInstructionResponse.getInstruction().getReceivedQuantity(), 1);
    assertNotNull(updateInstructionResponse.getInstruction().getContainer());
    verify(instructionPersisterService, times(1)).getInstructionById(12345l);
    verify(epcisService, times(0))
        .verifySerializedData(
            anyMap(),
            any(ShipmentDetails.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testUpdateInstructionForPalletSsccReceiving_CloseDatedItems()
      throws ReceivingException {

    doReturn(getMockNewInstructionForPalletSscc())
        .when(instructionPersisterService)
        .getInstructionById(12345l);
    doReturn(0l)
        .when(receiptService)
        .receivedQtyByDeliveryPoAndPoLine(anyLong(), anyString(), anyInt());
    doNothing()
        .when(epcisService)
        .verifySerializedData(
            anyMap(),
            any(ShipmentDetails.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class));
    doReturn("MOCK_UNIT_TEST_TRACKING_ID")
        .when(lpnCacheService)
        .getLPNBasedOnTenant(any(HttpHeaders.class));
    doReturn(true).when(appConfig).isCloseDateCheckEnabled();

    ArgumentCaptor<List<Receipt>> receiptListCaptor = ArgumentCaptor.forClass(List.class);
    doReturn(Collections.emptyList()).when(receiptService).saveAll(receiptListCaptor.capture());

    UpdateInstructionRequest updateInstructionRequest = getMockUpdateInstructionRequest();
    updateInstructionRequest.getDeliveryDocumentLines().get(0).setQuantity(1);
    updateInstructionRequest.getDeliveryDocumentLines().get(0).setQuantityUOM("ZA");

    HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();
    doReturn("MOCK_UNIT_TEST_TRACKING_ID")
            .when(lpnCacheService)
            .getLPNBasedOnTenant(any(HttpHeaders.class));
    InstructionResponse updateInstructionResponse =
        rxUpdateInstructionHandler.updateInstruction(
            12345l, updateInstructionRequest, null, httpHeaders);

    verify(instructionPersisterService, times(1)).getInstructionById(12345l);
    verify(receiptService, times(0))
        .receivedQtyByDeliveryPoAndPoLine(anyLong(), anyString(), anyInt());
    verify(epcisService, times(0))
        .verifySerializedData(
            anyMap(),
            any(ShipmentDetails.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class));
  }

  @Test
  public void testUpdateInstructionForPalletSsccReceiving_CloseDatedItems_ProblemReceiving()
          throws ReceivingException, ParseException {
    Instruction instruction = getMockNewInstructionForPalletSscc();
    instruction.setProblemTagId("problemtag");
    doReturn(instruction).when(instructionPersisterService).getInstructionById(12345l);
    doReturn(0l)
        .when(receiptService)
        .receivedQtyByDeliveryPoAndPoLineInEaches(anyLong(), anyString(), anyInt());
    doNothing()
        .when(epcisService)
        .verifySerializedData(
            anyMap(),
            any(ShipmentDetails.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class));
    doReturn("MOCK_UNIT_TEST_TRACKING_ID")
        .when(lpnCacheService)
        .getLPNBasedOnTenant(any(HttpHeaders.class));
    doReturn(RxMockContainer.getContainer())
        .when(containerService)
        .constructContainer(any(), any(), any(), any(), any());
    doReturn(RxMockContainer.getMockContainerItem().get(0))
        .when(containerItemBuilder)
        .build(any(), any(), any(), any());

    doReturn(true).when(appConfig).isCloseDateCheckEnabled();
    ReflectionTestUtils.setField(rxManagedConfig, "isEpcisProblemFallbackToASN", true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    doReturn(problemService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));

    ProblemLabel problemLabel = new ProblemLabel();
    problemLabel.setResolutionId("MOCK_RESOLUTION_ID");
    problemLabel.setProblemResponse(
        "{\"id\":\"98339bce-42f6-414a-be41-e40e78edb56c\",\"status\":\"OPEN\",\"label\":\"06001304645906\",\"slot\":\"M4032\",\"reportedQty\":5,\"remainingQty\":5,\"issue\":{\"id\":\"43f8a5df-c1b5-4018-9a99-567b3f2b41f0\",\"identifier\":\"210408-69961-6638-0000\",\"type\":\"DI\",\"subType\":\"ASN_ISSUE\",\"uom\":\"ZA\",\"status\":\"ANSWERED\",\"businessStatus\":\"READY_TO_RECEIVE\",\"resolutionStatus\":\"COMPLETE_RESOLUTON\",\"upc\":\"10350742190017\",\"itemNumber\":561291081,\"deliveryNumber\":\"78869668\",\"quantity\":5},\"resolutions\":[{\"id\":\"917dbbb0-8bd7-4667-82af-89068f2bc2e5\",\"state\":\"OPEN\",\"type\":\"RECEIVE_AGAINST_ORIGINAL_LINE\",\"provider\":\"Manual\",\"quantity\":5,\"remainingQty\":5,\"acceptedQuantity\":0,\"rejectedQuantity\":0,\"resolutionPoNbr\":\"7702953583\",\"resolutionPoLineNbr\":1}]}");
    doReturn(problemLabel).when(problemService).findProblemLabelByProblemTagId(anyString());

    ArgumentCaptor<List<Receipt>> receiptListCaptor = ArgumentCaptor.forClass(List.class);
    doNothing()
        .when(rxInstructionHelperService)
        .persistForUpdateInstruction(any(), receiptListCaptor.capture(), any(), any());

    UpdateInstructionRequest updateInstructionRequest = getMockUpdateInstructionRequest();
    updateInstructionRequest.getDeliveryDocumentLines().get(0).setQuantity(1);
    updateInstructionRequest.getDeliveryDocumentLines().get(0).setQuantityUOM("ZA");
    updateInstructionRequest.setProblemTagId("problem");

    updateInstructionRequest.setScannedDataList(
        MockInstruction.getInstructionRequestFor2dBarcodeScan_CloseDatedItem()
            .getScannedDataList());
    String dateString = "2025-01-15";
    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
    doReturn(formatter.parse(dateString))
            .when(updateInstructionServiceHelper)
            .parseAndGetRotateDate();

    HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();
    doReturn("MOCK_UNIT_TEST_TRACKING_ID")
            .when(lpnCacheService)
            .getLPNBasedOnTenant(any(HttpHeaders.class));
    InstructionResponse updateInstructionResponse =
        rxUpdateInstructionHandler.updateInstruction(
            12345l, updateInstructionRequest, null, httpHeaders);

    assertNotNull(updateInstructionResponse);

    assertEquals(updateInstructionResponse.getInstruction().getReceivedQuantity(), 1);
    assertEquals(updateInstructionResponse.getInstruction().getReceivedQuantityUOM(), "ZA");

    assertEquals(updateInstructionResponse.getInstruction().getActivityName(), "RxSSTK");
    assertEquals(updateInstructionResponse.getInstruction().getProviderId(), "RxSSTK");
    assertEquals(updateInstructionResponse.getInstruction().getInstructionMsg(), "RxBuildPallet");
    assertEquals(updateInstructionResponse.getInstruction().getInstructionCode(), "RxBuildPallet");

    assertNotNull(updateInstructionResponse.getInstruction().getContainer());
    if (!rxManagedConfig.isTrimUpdateInstructionResponseEnabled()) {
      assertTrue(
          CollectionUtils.isNotEmpty(
              updateInstructionResponse.getInstruction().getChildContainers()));
      assertEquals(updateInstructionResponse.getInstruction().getChildContainers().size(), 1);
    }

    assertEquals(receiptListCaptor.getValue().size(), 1);
    assertSame(receiptListCaptor.getValue().get(0).getQuantity(), 1);
    assertSame(receiptListCaptor.getValue().get(0).getEachQty(), 6);

    assertEquals(receiptListCaptor.getValue().size(), 1);
    assertSame(receiptListCaptor.getValue().get(0).getQuantity(), 1);
    assertSame(receiptListCaptor.getValue().get(0).getEachQty(), 6);

    verify(instructionPersisterService, times(1)).getInstructionById(12345l);
    verify(receiptService, times(1))
        .receivedQtyByDeliveryPoAndPoLineInEaches(anyLong(), anyString(), anyInt());
    verify(epcisService, times(1))
        .verifySerializedData(
            anyMap(),
            any(ShipmentDetails.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class));
  }

  @Test
  public void test_updateInstruction_optimistic_lock() throws ReceivingException {

    try {
      Instruction instruction = getMockNewInstruction();
      instruction.setSsccNumber("");
      instruction.setInstructionCode(
          RxInstructionType.BUILD_CONTAINER_FOR_UPC_RECEIVING.getInstructionType());
      instruction.setInstructionMsg(
          RxInstructionType.BUILD_CONTAINER_FOR_UPC_RECEIVING.getInstructionMsg());
      doReturn(instruction).when(instructionPersisterService).getInstructionById(12345l);
      doReturn("MOCK_UNIT_TEST_TRACKING_ID")
          .when(lpnCacheService)
          .getLPNBasedOnTenant(any(HttpHeaders.class));
      doReturn(RxMockContainer.getContainer())
          .when(containerService)
          .constructContainer(any(), any(), any(), any(), any());
      doReturn(RxMockContainer.getMockContainerItem().get(0))
          .when(containerItemBuilder)
          .build(any(), any(), any(), any());

      ArgumentCaptor<List<Receipt>> receiptListCaptor = ArgumentCaptor.forClass(List.class);
      doThrow(new ObjectOptimisticLockingFailureException("", null))
          .when(rxInstructionHelperService)
          .persistForUpdateInstruction(any(), receiptListCaptor.capture(), any(), any());

      UpdateInstructionRequest updateInstructionRequest = getMockUpdateInstructionRequest();
      updateInstructionRequest.getDeliveryDocumentLines().get(0).setQuantity(1);
      updateInstructionRequest.getDeliveryDocumentLines().get(0).setQuantityUOM("ZA");

      updateInstructionRequest.setScannedDataList(
          MockInstruction.getInstructionRequestFor2dBarcodeScan().getScannedDataList());

      HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();
      rxUpdateInstructionHandler.updateInstruction(
          12345l, updateInstructionRequest, null, httpHeaders);
    } catch (ReceivingConflictException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.INSTR_OPTIMISTIC_LOCK_GENERIC_ERROR);
      assertEquals(e.getDescription(), RxConstants.INSTR_OPTIMISTIC_LOCK_GENERIC_ERROR);
    }
  }

  private UpdateInstructionRequest getMockUpdateInstructionRequest() {
    String updateInstructionRequestBody =
        "{\n"
            + "  \"deliveryNumber\": 95352689,\n"
            + "  \"doorNumber\": \"608\",\n"
            + "  \"containerType\": \"Chep Pallet\",\n"
            + "  \"facility\": {\n"
            + "    \"buNumber\": \"{{siteId}}\",\n"
            + "    \"countryCode\": \"us\"\n"
            + "  },\n"
            + "  \"deliveryDocumentLines\": [\n"
            + "    {\n"
            + "      \"quantity\": 1,\n"
            + "      \"totalPurchaseReferenceQty\": 100,\n"
            + "      \"purchaseReferenceNumber\": \"8458709164\",\n"
            + "      \"purchaseReferenceLineNumber\": 1,\n"
            + "      \"purchaseRefType\": \"{{type}}\",\n"
            + "      \"poDCNumber\": \"{{siteId}}\",\n"
            + "      \"quantityUOM\": \"CA\",\n"
            + "      \"purchaseCompanyId\": \"1\",\n"
            + "      \"shippedQty\": \"12\",\n"
            + "      \"shippedQtyUom\": \"ZA\",\n"
            + "      \"deptNumber\": \"94\",\n"
            + "      \"gtin\": \"00029695410987\",\n"
            + "      \"itemNumber\": 561298341,\n"
            + "      \"vnpkQty\": 6,\n"
            + "      \"whpkQty\": 6,\n"
            + "      \"palletTi\": 0,\n"
            + "      \"palletHi\": 0,\n"
            + "      \"vendorPackCost\": 23.89,\n"
            + "      \"whpkSell\": 23.89,\n"
            + "      \"maxOverageAcceptQty\": 20,\n"
            + "      \"maxReceiveQty\": 120,\n"
            + "      \"expectedQty\": 100,\n"
            + "      \"vnpkWgtQty\": 10,\n"
            + "      \"vnpkWgtUom\": \"LB\",\n"
            + "      \"vnpkcbqty\": 0.852,\n"
            + "      \"vnpkcbuomcd\": \"CF\",\n"
            + "      \"description\": \"Tylenol\",\n"
            + "      \"secondaryDescription\": \"<T&S>\",\n"
            + "      \"financialReportingGroupCode\": \"US\",\n"
            + "      \"baseDivisionCode\": \"WM\",\n"
            + "      \"rotateDate\": \"2020-01-03\",\n"
            + "      \"warehouseMinLifeRemainingToReceive\": 30,\n"
            + "      \"promoBuyInd\": \"N\",\n"
            + "      \"shipmentDetailsList\": [\n"
            + "        {\n"
            + "          \"inboundShipmentDocId\": \"546191213_20191106_719468_VENDOR_US\",\n"
            + "          \"shipmentNumber\": \"546191213\",\n"
            + "          \"loadNumber\": \"88528711\",\n"
            + "          \"sourceGlobalLocationNumber\": \"0069382035222\",\n"
            + "          \"shipperId\": null,\n"
            + "          \"destinationGlobalLocationNumber\": \"0078742035222\"\n"
            + "        }\n"
            + "      ],\n"
            + "      \"profiledWarehouseArea\": \"CPS\"\n"
            + "    }\n"
            + "  ],\n"
            + "  \"scannedDataList\": [\n"
            + "    {\n"
            + "      \"key\": \"gtin\",\n"
            + "      \"value\": \"00368180121015\"\n"
            + "    },\n"
            + "    {\n"
            + "      \"key\": \"lot\",\n"
            + "      \"value\": \"00L032C09C\"\n"
            + "    },\n"
            + "    {\n"
            + "      \"key\": \"serial\",\n"
            + "      \"value\": \"abc124\"\n"
            + "    },\n"
            + "    {\n"
            + "      \"key\": \"expiryDate\",\n"
            + "      \"value\": \"250108\"\n"
            + "    }\n"
            + "  ],\n"
            + "  \"additionalInfo\": {\n"
            + "    \"warehouseGroupCode\": \"\",\n"
            + "    \"isNewItem\": false,\n"
            + "    \"weight\": 0,\n"
            + "    \"warehouseMinLifeRemainingToReceive\": 0,\n"
            + "    \"isDscsaExemptionInd\": false,\n"
            + "    \"isHACCP\": false,\n"
            + "    \"primeSlotSize\": 0,\n"
            + "    \"isHazardous\": 0,\n"
            + "    \"atlasConvertedItem\": false,\n"
            + "    \"isWholesaler\": false,\n"
            + "    \"isDefaultTiHiUsed\": false,\n"
            + "    \"qtyValidationDone\": true,\n"
            + "    \"isEpcisEnabledVendor\": true,\n"
            + "    \"auditQty\": 1,\n"
            + "    \"lotList\": [\n"
            + "      \"00L032C09E\",\n"
            + "      \"00L032C09B\",\n"
            + "      \"00L032C09A\",\n"
            + "      \"00L032C09D\",\n"
            + "      \"00L032C09C\"\n"
            + "    ],\n"
            + "    \"gtinList\": [\n"
            + "      \"00368180121015\"\n"
            + "    ],\n"
            + "    \"attpQtyInEaches\": 15,\n"
            + "    \"serializedInfo\": [\n"
            + "      {\n"
            + "        \"lot\": \"00L032C09C\",\n"
            + "        \"serial\": \"abc124\",\n"
            + "        \"expiryDate\": \"2025-01-08\",\n"
            + "        \"reportedUom\": \"CA\",\n"
            + "        \"gtin\": \"00368180121015\"\n"
            + "      },\n"
            + "      {\n"
            + "        \"lot\": \"00L032C09C\",\n"
            + "        \"serial\": \"abc124\",\n"
            + "        \"expiryDate\": \"2025-01-08\",\n"
            + "        \"reportedUom\": \"CA\",\n"
            + "        \"gtin\": \"00368180121015\"\n"
            + "      },\n"
            + "      {\n"
            + "        \"lot\": \"00L032C09C\",\n"
            + "        \"serial\": \"abc124\",\n"
            + "        \"expiryDate\": \"2025-01-08\",\n"
            + "        \"reportedUom\": \"CA\",\n"
            + "        \"gtin\": \"00368180121015\"\n"
            + "      },\n"
            + "      {\n"
            + "        \"lot\": \"00L032C09C\",\n"
            + "        \"serial\": \"abc124\",\n"
            + "        \"expiryDate\": \"2025-01-08\",\n"
            + "        \"reportedUom\": \"CA\",\n"
            + "        \"gtin\": \"00368180121015\"\n"
            + "      },\n"
            + "      {\n"
            + "        \"lot\": \"00L032C09C\",\n"
            + "        \"serial\": \"abc124\",\n"
            + "        \"expiryDate\": \"2025-01-08\",\n"
            + "        \"reportedUom\": \"CA\",\n"
            + "        \"gtin\": \"00368180121015\"\n"
            + "      }\n"
            + "    ]\n"
            + "  }\n"
            + "}";

    return gson.fromJson(updateInstructionRequestBody, UpdateInstructionRequest.class);
  }

  public Instruction getMockNewInstruction() {

    // Move data
    LinkedTreeMap<String, Object> move = new LinkedTreeMap<>();
    move.put("fromLocation", "608");
    move.put("correlationID", "a1-b2-c3-d4-e6");
    move.put("lastChangedOn", new Date());
    move.put("lastChangedBy", "rxTestUser");

    Instruction instruction = new Instruction();
    instruction.setId(12345l);
    instruction.setActivityName("RxSSTK");
    instruction.setContainer(null);
    instruction.setChildContainers(null);
    instruction.setCreateTs(new Date());
    instruction.setCreateUserId("rxTestUser");
    instruction.setLastChangeTs(new Date());
    instruction.setLastChangeUserId("rxTestUser");
    instruction.setDeliveryNumber(Long.valueOf("21119003"));
    instruction.setGtin("00000943037194");
    instruction.setInstructionCode("RxBuildPallet");
    instruction.setInstructionMsg("RxBuildPallet");
    instruction.setItemDescription("HEM VALUE PACK (4)");
    instruction.setMessageId("58e1df00-ebf6-11e8-9c25-dd4bfc2a06f8");
    instruction.setMove(move);
    instruction.setPoDcNumber("32898");
    instruction.setPrintChildContainerLabels(false);
    instruction.setPurchaseReferenceNumber("9763140004");
    instruction.setPurchaseReferenceLineNumber(1);
    instruction.setProjectedReceiveQty(60);
    instruction.setProjectedReceiveQtyUOM("EA");
    instruction.setReceivedQuantity(0);
    instruction.setReceivedQuantityUOM("EA");
    instruction.setProviderId("RxSSTK");
    instruction.setSsccNumber("0012345678890");
    instruction.setDeliveryDocument(
        "{\n"
            + "  \"purchaseReferenceNumber\": \"8458709164\",\n"
            + "  \"financialGroupCode\": \"US\",\n"
            + "  \"baseDivCode\": \"WM\",\n"
            + "  \"deptNumber\": \"38\",\n"
            + "  \"purchaseCompanyId\": \"1\",\n"
            + "  \"purchaseReferenceLegacyType\": \"33\",\n"
            + "  \"poDCNumber\": \"32898\",\n"
            + "  \"purchaseReferenceStatus\": \"ACTV\",\n"
            + "  \"deliveryDocumentLines\": [\n"
            + "    {\n"
            + "      \"gtin\": \"00029695410987\",\n"
            + "      \"itemUPC\": \"00029695410987\",\n"
            + "      \"caseUPC\": \"20029695410987\",\n"
            + "      \"shippedQty\": \"12\",\n"
            + "      \"shippedQtyUom\": \"ZA\",\n"
            + "      \"purchaseReferenceNumber\": \"8458709164\",\n"
            + "      \"purchaseReferenceLineNumber\": 1,\n"
            + "      \"event\": \"POS REPLEN\",\n"
            + "      \"purchaseReferenceLineStatus\": \"RECEIVED\",\n"
            + "      \"whpkSell\": 8.22,\n"
            + "      \"vendorPackCost\": 6.6,\n"
            + "      \"vnpkQty\": 6,\n"
            + "      \"whpkQty\": 6,\n"
            + "      \"expectedQtyUOM\": \"ZA\",\n"
            + "      \"openQty\": 10,\n"
            + "      \"expectedQty\": 10,\n"
            + "      \"overageQtyLimit\": 0,\n"
            + "      \"itemNbr\": 561291081,\n"
            + "      \"purchaseRefType\": \"33\",\n"
            + "      \"palletTi\": 0,\n"
            + "      \"palletHi\": 0,\n"
            + "      \"vnpkWgtQty\": 14.84,\n"
            + "      \"vnpkWgtUom\": \"LB\",\n"
            + "      \"vnpkcbqty\": 0.432,\n"
            + "      \"vnpkcbuomcd\": \"CF\",\n"
            + "      \"isHazmat\": false,\n"
            + "      \"itemDescription1\": \"TOYS\",\n"
            + "      \"palletSSCC\": \"00100700302232310010\",\n"
            + "      \"packSSCC\": \"909899000020014377\",\n"
            + "      \"ndc\": \"43547-282-11\",\n"
            + "      \"shipmentNumber\": \"90989110\",\n"
            + "      \"shipmentDetailsList\": [\n"
            + "        {\n"
            + "          \"inboundShipmentDocId\": \"90989110_20191106_719468_VENDOR_US\",\n"
            + "          \"shipmentNumber\": \"90989110\",\n"
            + "          \"sourceGlobalLocationNumber\": \"0069382035222\",\n"
            + "          \"destinationGlobalLocationNumber\": \"0078742035222\"\n"
            + "        }\n"
            + "      ],\n"
            + "      \"manufactureDetails\": [\n"
            + "        {\n"
            + "          \"lot\": \"00L032C09A\",\n"
            + "          \"expiryDate\": \"2025-01-08\",\n"
            + "          \"qty\": 10,\n"
            + "          \"reportedUom\": \"ZA\"\n"
            + "        }\n"
            + "      ],\n"
            + "      \"additionalInfo\": {\n"
            + "        \"warehouseGroupCode\": \"\",\n"
            + "        \"isNewItem\": false,\n"
            + "        \"weight\": 0,\n"
            + "        \"warehouseMinLifeRemainingToReceive\": 0,\n"
            + "        \"isDscsaExemptionInd\": false,\n"
            + "        \"isHACCP\": false,\n"
            + "        \"primeSlotSize\": 0,\n"
            + "        \"isHazardous\": 0,\n"
            + "        \"atlasConvertedItem\": false,\n"
            + "        \"isWholesaler\": false,\n"
            + "        \"isDefaultTiHiUsed\": false,\n"
            + "        \"qtyValidationDone\": true,\n"
            + "        \"isEpcisEnabledVendor\": true,\n"
            + "        \"auditQty\": 1,\n"
            + "        \"lotList\": [\n"
            + "          \"00L032C09E\",\n"
            + "          \"00L032C09B\",\n"
            + "          \"00L032C09A\",\n"
            + "          \"00L032C09D\",\n"
            + "          \"00L032C09C\"\n"
            + "        ],\n"
            + "        \"gtinList\": [\n"
            + "          \"00368180121015\"\n"
            + "        ],\n"
            + "        \"attpQtyInEaches\": 15\n"
            + "      },\n"
            + "      \"deptNumber\": \"38\"\n"
            + "    }\n"
            + "  ],\n"
            + "  \"weight\": 0,\n"
            + "  \"cubeQty\": 0,\n"
            + "  \"deliveryStatus\": \"ARV\",\n"
            + "  \"totalBolFbq\": 106\n"
            + "}");

    return instruction;
  }

  public Instruction getMockNewInstructionForPalletSscc() {

    // Move data
    LinkedTreeMap<String, Object> move = new LinkedTreeMap<>();
    move.put("fromLocation", "608");
    move.put("correlationID", "a1-b2-c3-d4-e6");
    move.put("lastChangedOn", new Date());
    move.put("lastChangedBy", "rxTestUser");

    Instruction instruction = new Instruction();
    instruction.setId(12345l);
    instruction.setActivityName("RxSSTK");
    instruction.setContainer(null);
    instruction.setChildContainers(null);
    instruction.setCreateTs(new Date());
    instruction.setCreateUserId("rxTestUser");
    instruction.setLastChangeTs(new Date());
    instruction.setLastChangeUserId("rxTestUser");
    instruction.setDeliveryNumber(Long.valueOf("21119003"));
    instruction.setGtin("00000943037194");
    instruction.setInstructionCode("RxBuildPallet");
    instruction.setInstructionMsg("RxBuildPallet");
    instruction.setItemDescription("HEM VALUE PACK (4)");
    instruction.setMessageId("58e1df00-ebf6-11e8-9c25-dd4bfc2a06f8");
    instruction.setMove(move);
    instruction.setPoDcNumber("32898");
    instruction.setPrintChildContainerLabels(false);
    instruction.setPurchaseReferenceNumber("9763140004");
    instruction.setPurchaseReferenceLineNumber(1);
    instruction.setProjectedReceiveQty(60);
    instruction.setProjectedReceiveQtyUOM("EA");
    instruction.setReceivedQuantity(0);
    instruction.setReceivedQuantityUOM("EA");
    instruction.setProviderId("RxSSTK");
    instruction.setSsccNumber("00100700302232310010");
    instruction.setDeliveryDocument(
        "{\n"
            + "  \"purchaseReferenceNumber\": \"8458709164\",\n"
            + "  \"financialGroupCode\": \"US\",\n"
            + "  \"baseDivCode\": \"WM\",\n"
            + "  \"deptNumber\": \"38\",\n"
            + "  \"purchaseCompanyId\": \"1\",\n"
            + "  \"purchaseReferenceLegacyType\": \"33\",\n"
            + "  \"poDCNumber\": \"32898\",\n"
            + "  \"purchaseReferenceStatus\": \"ACTV\",\n"
            + "  \"deliveryDocumentLines\": [\n"
            + "    {\n"
            + "      \"gtin\": \"00029695410987\",\n"
            + "      \"itemUPC\": \"00029695410987\",\n"
            + "      \"caseUPC\": \"20029695410987\",\n"
            + "      \"shippedQty\": \"10\",\n"
            + "      \"shippedQtyUom\": \"ZA\",\n"
            + "      \"purchaseReferenceNumber\": \"8458709164\",\n"
            + "      \"purchaseReferenceLineNumber\": 1,\n"
            + "      \"event\": \"POS REPLEN\",\n"
            + "      \"purchaseReferenceLineStatus\": \"RECEIVED\",\n"
            + "      \"whpkSell\": 8.22,\n"
            + "      \"vendorPackCost\": 6.6,\n"
            + "      \"vnpkQty\": 6,\n"
            + "      \"whpkQty\": 6,\n"
            + "      \"expectedQtyUOM\": \"ZA\",\n"
            + "      \"openQty\": 10,\n"
            + "      \"expectedQty\": 10,\n"
            + "      \"overageQtyLimit\": 0,\n"
            + "      \"itemNbr\": 561291081,\n"
            + "      \"purchaseRefType\": \"33\",\n"
            + "      \"palletTi\": 0,\n"
            + "      \"palletHi\": 0,\n"
            + "      \"vnpkWgtQty\": 14.84,\n"
            + "      \"vnpkWgtUom\": \"LB\",\n"
            + "      \"vnpkcbqty\": 0.432,\n"
            + "      \"vnpkcbuomcd\": \"CF\",\n"
            + "      \"isHazmat\": false,\n"
            + "      \"itemDescription1\": \"TOYS\",\n"
            + "      \"palletSSCC\": \"00100700302232310010\",\n"
            + "      \"packSSCC\": \"909899000020014377\",\n"
            + "      \"ndc\": \"43547-282-11\",\n"
            + "      \"shipmentNumber\": \"90989110\",\n"
            + "      \"shipmentDetailsList\": [\n"
            + "        {\n"
            + "          \"inboundShipmentDocId\": \"90989110_20191106_719468_VENDOR_US\",\n"
            + "          \"shipmentNumber\": \"90989110\",\n"
            + "          \"sourceGlobalLocationNumber\": \"0069382035222\",\n"
            + "          \"destinationGlobalLocationNumber\": \"0078742035222\"\n"
            + "        }\n"
            + "      ],\n"
            + "      \"manufactureDetails\": [\n"
            + "        {\n"
            + "          \"lot\": \"00L032C09A\",\n"
            + "          \"expiryDate\": \"2025-01-08\",\n"
            + "          \"qty\": 10,\n"
            + "          \"reportedUom\": \"ZA\"\n"
            + "        }\n"
            + "      ],\n"
            + "      \"additionalInfo\": {\n"
            + "        \"warehouseGroupCode\": \"\",\n"
            + "        \"isNewItem\": false,\n"
            + "        \"weight\": 0,\n"
            + "        \"warehouseMinLifeRemainingToReceive\": 0,\n"
            + "        \"isDscsaExemptionInd\": false,\n"
            + "        \"isHACCP\": false,\n"
            + "        \"primeSlotSize\": 0,\n"
            + "        \"isHazardous\": 0,\n"
            + "        \"atlasConvertedItem\": false,\n"
            + "        \"isWholesaler\": false,\n"
            + "        \"isDefaultTiHiUsed\": false,\n"
            + "        \"qtyValidationDone\": true,\n"
            + "        \"isEpcisEnabledVendor\": true,\n"
            + "        \"auditQty\": 1,\n"
            + "        \"lotList\": [\n"
            + "          \"00L032C09E\",\n"
            + "          \"00L032C09B\",\n"
            + "          \"00L032C09A\",\n"
            + "          \"00L032C09D\",\n"
            + "          \"00L032C09C\"\n"
            + "        ],\n"
            + "        \"gtinList\": [\n"
            + "          \"00368180121015\"\n"
            + "        ],\n"
            + "        \"attpQtyInEaches\": 15,\n"
            + "        \"scannedCaseAttpQtyUOM\": 1,\n"
            + "        \"serializedInfo\": [\n"
            + "          {\n"
            + "            \"lot\": \"00L032C09C\",\n"
            + "            \"serial\": \"abc124\",\n"
            + "            \"expiryDate\": \"2025-01-08\",\n"
            + "            \"reportedUom\": \"CA\",\n"
            + "            \"gtin\": \"00368180121015\"\n"
            + "          },\n"
            + "          {\n"
            + "            \"lot\": \"00L032C09C\",\n"
            + "            \"serial\": \"abc124\",\n"
            + "            \"expiryDate\": \"2025-01-08\",\n"
            + "            \"reportedUom\": \"CA\",\n"
            + "            \"gtin\": \"00368180121015\"\n"
            + "          },\n"
            + "          {\n"
            + "            \"lot\": \"00L032C09C\",\n"
            + "            \"serial\": \"abc124\",\n"
            + "            \"expiryDate\": \"2025-01-08\",\n"
            + "            \"reportedUom\": \"CA\",\n"
            + "            \"gtin\": \"00368180121015\"\n"
            + "          },\n"
            + "          {\n"
            + "            \"lot\": \"00L032C09C\",\n"
            + "            \"serial\": \"abc124\",\n"
            + "            \"expiryDate\": \"2025-01-08\",\n"
            + "            \"reportedUom\": \"CA\",\n"
            + "            \"gtin\": \"00368180121015\"\n"
            + "          },\n"
            + "          {\n"
            + "            \"lot\": \"00L032C09C\",\n"
            + "            \"serial\": \"abc124\",\n"
            + "            \"expiryDate\": \"2025-01-08\",\n"
            + "            \"reportedUom\": \"CA\",\n"
            + "            \"gtin\": \"00368180121015\"\n"
            + "          }\n"
            + "        ]\n"
            + "      },\n"
            + "      \"deptNumber\": \"38\"\n"
            + "    }\n"
            + "  ],\n"
            + "  \"weight\": 0,\n"
            + "  \"cubeQty\": 0,\n"
            + "  \"deliveryStatus\": \"ARV\",\n"
            + "  \"totalBolFbq\": 106\n"
            + "}");

    return instruction;
  }

  private List<ScannedData> getMockScannedDataList() {

    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData expScannedData = new ScannedData();
    expScannedData.setKey("expiryDate");
    expScannedData.setValue("200505");
    scannedDataList.add(expScannedData);

    ScannedData gtinScannedData = new ScannedData();
    gtinScannedData.setKey("gtin");
    gtinScannedData.setValue("01123840356119");
    scannedDataList.add(gtinScannedData);

    ScannedData lotScannedData = new ScannedData();
    lotScannedData.setKey("lot");
    lotScannedData.setValue("00L032C09A");
    scannedDataList.add(lotScannedData);

    ScannedData serialScannedData = new ScannedData();
    serialScannedData.setKey("serial");
    serialScannedData.setValue("SN345678");
    scannedDataList.add(serialScannedData);

    return scannedDataList;
  }

  @Test
  public void test_updateInstruction_whiteSpace() throws Exception {

    UpdateInstructionRequest updateInstructionRequest = getMockUpdateInstructionRequest();
    updateInstructionRequest.getDeliveryDocumentLines().get(0).setQuantity(1);

    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData expScannedData = new ScannedData();
    expScannedData.setKey("expiryDate");
    expScannedData.setValue("200505");
    scannedDataList.add(expScannedData);

    ScannedData gtinScannedData = new ScannedData();
    gtinScannedData.setKey("gtin");
    gtinScannedData.setValue("01123840356119");
    scannedDataList.add(gtinScannedData);

    ScannedData lotScannedData = new ScannedData();
    lotScannedData.setKey("lot");
    lotScannedData.setValue("00L032C09A   ");
    scannedDataList.add(lotScannedData);

    ScannedData serialScannedData = new ScannedData();
    serialScannedData.setKey("serial");
    serialScannedData.setValue("SN345678  ");
    scannedDataList.add(serialScannedData);

    updateInstructionRequest.setScannedDataList(scannedDataList);
    doNothing()
        .when(epcisService)
        .verifySerializedData(
            anyMap(),
            any(ShipmentDetails.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class));
    doReturn(getMockNewInstruction()).when(instructionPersisterService).getInstructionById(12345l);
    doReturn(5L)
        .when(receiptService)
        .receivedQtyByDeliveryPoAndPoLine(anyLong(), anyString(), anyInt());
    doNothing()
        .when(instructionPersisterService)
        .processCreateChildContainers(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            any(ContainerDetails.class),
            any(ContainerDetails.class),
            anyString());
    doReturn("MOCK_UNIT_TEST_TRACKING_ID")
        .when(lpnCacheService)
        .getLPNBasedOnTenant(any(HttpHeaders.class));

    doReturn(RxMockContainer.getContainer())
        .when(containerService)
        .constructContainer(any(), any(), any(), any(), any());
    doReturn(RxMockContainer.getMockContainerItem().get(0))
        .when(containerItemBuilder)
        .build(any(), any(), any(), any());

    ArgumentCaptor<List<Receipt>> receiptListCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<Instruction> instructionCaptor = ArgumentCaptor.forClass(Instruction.class);
    doNothing()
        .when(rxInstructionHelperService)
        .persistForUpdateInstruction(
            instructionCaptor.capture(), receiptListCaptor.capture(), any(), any());
    doNothing()
            .when(updateInstructionDataValidator)
            .validateContainerDoesNotAlreadyExist(anyInt(), anyString(), anyString(), anyInt(), anyString());
    InstructionResponse updateInstructionResponse =
        rxUpdateInstructionHandler.updateInstruction(
            12345l, updateInstructionRequest, "", MockRxHttpHeaders.getHeaders());

    assertNotNull(updateInstructionResponse);
    assertEquals(updateInstructionResponse.getInstruction().getReceivedQuantity(), 1);
    if (!rxManagedConfig.isTrimUpdateInstructionResponseEnabled()) {
      assertEquals(updateInstructionResponse.getInstruction().getChildContainers().size(), 1);
    }

    verify(instructionPersisterService, times(1)).getInstructionById(12345l);
    verify(epcisService, times(1))
        .verifySerializedData(
            anyMap(),
            any(ShipmentDetails.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class));

    List<Instruction> capturedInstructions = instructionCaptor.getAllValues();
    for (Instruction capturedInstruction : capturedInstructions) {
      List<ContainerDetails> capturedInstructionChildContainers =
          capturedInstruction.getChildContainers();
      for (ContainerDetails containerDetails : capturedInstructionChildContainers) {
        for (Content content : containerDetails.getContents()) {
          assertEquals(StringUtils.trim(content.getLot()), content.getLot());
          assertEquals(StringUtils.trim(content.getSerial()), content.getSerial());
        }
      }
    }
  }

  @Test
  public void populateInstructionCodeInContainerMiscInfo() {
    // given
    Container container = new Container();
    Instruction instruction = getMockNewInstruction();
    ManufactureDetail manufactureDetail = new ManufactureDetail();
    manufactureDetail.setDocumentId("1");
    manufactureDetail.setDocumentPackId("1");
    manufactureDetail.setShipmentNumber("1");

    // when
    ReflectionTestUtils.invokeMethod(
        rxUpdateInstructionHandler,
        "populateInstructionCodeInContainerMiscInfo",
        container,
        instruction,
        true,
        manufactureDetail);

    // then
    assertNotNull(container.getContainerMiscInfo());
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void updateInstruction_EpcisPalletRcv() throws ReceivingException {
    doReturn(getMockNewInstructionForPalletSscc())
        .when(instructionPersisterService)
        .getInstructionById(12345l);
    doReturn(0l)
        .when(receiptService)
        .receivedQtyByDeliveryPoAndPoLine(anyLong(), anyString(), anyInt());
    doNothing()
        .when(epcisService)
        .verifySerializedData(
            anyMap(),
            any(ShipmentDetails.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class));
    doReturn("MOCK_UNIT_TEST_TRACKING_ID")
        .when(lpnCacheService)
        .getLPNBasedOnTenant(any(HttpHeaders.class));
    doReturn(false).when(appConfig).isCloseDateCheckEnabled();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    when(containerService.constructContainer(any(), any(), anyBoolean(), anyBoolean(), any()))
        .thenThrow(new ReceivingException(""));

    ArgumentCaptor<List<Receipt>> receiptListCaptor = ArgumentCaptor.forClass(List.class);
    doReturn(Collections.emptyList()).when(receiptService).saveAll(receiptListCaptor.capture());

    UpdateInstructionRequest updateInstructionRequest = getMockUpdateInstructionRequest();
    updateInstructionRequest.getDeliveryDocumentLines().get(0).setQuantity(1);
    updateInstructionRequest.getDeliveryDocumentLines().get(0).setQuantityUOM("CA");

    doNothing()
            .when(updateInstructionDataValidator)
            .validateContainerDoesNotAlreadyExist(anyInt(), anyString(), anyString(), anyInt(), anyString());
    HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();
    doReturn("MOCK_UNIT_TEST_TRACKING_ID")
            .when(lpnCacheService)
            .getLPNBasedOnTenant(any(HttpHeaders.class));
    InstructionResponse updateInstructionResponse =
        rxUpdateInstructionHandler.updateInstruction(
            12345l, updateInstructionRequest, null, httpHeaders);
  }


  @Test
  public void updateInstruction_EpcisValidateQty() throws ReceivingException {
    Instruction instruction = getMockNewInstructionForPalletSscc();
    DeliveryDocument deliveryDocument =
            gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    deliveryDocument.getDeliveryDocumentLines().get(0).getAdditionalInfo().setPalletFlowInMultiSku(false);

    List<ScannedData> mockScannedData =  getMockScannedDataList();
    ScannedData scannedData = new ScannedData();
    scannedData.setKey("ZA");
    scannedData.setValue("10");
    mockScannedData.add(scannedData);

    UpdateInstructionRequest updateInstructionRequest = getMockUpdateInstructionRequest();
    updateInstructionRequest.getDeliveryDocumentLines().get(0).setQuantity(1);
    updateInstructionRequest.getDeliveryDocumentLines().get(0).setQuantityUOM("EA");
    updateInstructionRequest.setUserEnteredDataList(mockScannedData);
    DocumentLine documentLine = new DocumentLine();
    documentLine.setVnpkQty(6);
    documentLine.setWhpkQty(1);
    when(tenantSpecificConfigReader.getCcmValue(anyInt(), anyString(), anyString())).thenReturn("1");
     rxUpdateInstructionHandler.validateUserEnteredQty(
                    updateInstructionRequest, instruction);
  }



  public Instruction getMockNewInstructionForPalletSscc_PO_28() {

    // Move data
    LinkedTreeMap<String, Object> move = new LinkedTreeMap<>();
    move.put("fromLocation", "608");
    move.put("correlationID", "a1-b2-c3-d4-e6");
    move.put("lastChangedOn", new Date());
    move.put("lastChangedBy", "rxTestUser");

    Instruction instruction = new Instruction();
    instruction.setId(12345l);
    instruction.setActivityName("RxSSTK");
    instruction.setContainer(null);
    instruction.setChildContainers(null);
    instruction.setCreateTs(new Date());
    instruction.setCreateUserId("rxTestUser");
    instruction.setLastChangeTs(new Date());
    instruction.setLastChangeUserId("rxTestUser");
    instruction.setDeliveryNumber(Long.valueOf("21119003"));
    instruction.setGtin("00000943037194");
    instruction.setInstructionCode("RxBuildPallet");
    instruction.setInstructionMsg("RxBuildPallet");
    instruction.setItemDescription("HEM VALUE PACK (4)");
    instruction.setMessageId("58e1df00-ebf6-11e8-9c25-dd4bfc2a06f8");
    instruction.setMove(move);
    instruction.setPoDcNumber("32898");
    instruction.setPrintChildContainerLabels(false);
    instruction.setPurchaseReferenceNumber("9763140004");
    instruction.setPurchaseReferenceLineNumber(1);
    instruction.setProjectedReceiveQty(60);
    instruction.setProjectedReceiveQtyUOM("EA");
    instruction.setReceivedQuantity(0);
    instruction.setReceivedQuantityUOM("EA");
    instruction.setProviderId("RxSSTK");
    instruction.setSsccNumber("00100700302232310010");
    instruction.setDeliveryDocument(
            "{\n"
                    + "  \"purchaseReferenceNumber\": \"8458709164\",\n"
                    + "  \"financialGroupCode\": \"US\",\n"
                    + "  \"baseDivCode\": \"WM\",\n"
                    + "  \"deptNumber\": \"38\",\n"
                    + "  \"purchaseCompanyId\": \"1\",\n"
                    + "  \"purchaseReferenceLegacyType\": \"33\",\n"
                    + "  \"poDCNumber\": \"32898\",\n"
                    + "  \"purchaseReferenceStatus\": \"ACTV\",\n"
                    + "  \"deliveryDocumentLines\": [\n"
                    + "    {\n"
                    + "      \"gtin\": \"00029695410987\",\n"
                    + "      \"itemUPC\": \"00029695410987\",\n"
                    + "      \"caseUPC\": \"20029695410987\",\n"
                    + "      \"shippedQty\": \"10\",\n"
                    + "      \"shippedQtyUom\": \"ZA\",\n"
                    + "      \"purchaseReferenceNumber\": \"8458709164\",\n"
                    + "      \"purchaseReferenceLineNumber\": 1,\n"
                    + "      \"event\": \"POS REPLEN\",\n"
                    + "      \"purchaseReferenceLineStatus\": \"RECEIVED\",\n"
                    + "      \"whpkSell\": 8.22,\n"
                    + "      \"vendorPackCost\": 6.6,\n"
                    + "      \"vnpkQty\": 6,\n"
                    + "      \"whpkQty\": 6,\n"
                    + "      \"expectedQtyUOM\": \"ZA\",\n"
                    + "      \"openQty\": 10,\n"
                    + "      \"expectedQty\": 10,\n"
                    + "      \"overageQtyLimit\": 0,\n"
                    + "      \"itemNbr\": 561291081,\n"
                    + "      \"purchaseRefType\": \"33\",\n"
                    + "      \"palletTi\": 0,\n"
                    + "      \"palletHi\": 0,\n"
                    + "      \"vnpkWgtQty\": 14.84,\n"
                    + "      \"vnpkWgtUom\": \"LB\",\n"
                    + "      \"vnpkcbqty\": 0.432,\n"
                    + "      \"vnpkcbuomcd\": \"CF\",\n"
                    + "      \"isHazmat\": false,\n"
                    + "      \"itemDescription1\": \"TOYS\",\n"
                    + "      \"palletSSCC\": \"00100700302232310010\",\n"
                    + "      \"packSSCC\": \"909899000020014377\",\n"
                    + "      \"ndc\": \"43547-282-11\",\n"
                    + "      \"shipmentNumber\": \"90989110\",\n"
                    + "      \"shipmentDetailsList\": [\n"
                    + "        {\n"
                    + "          \"inboundShipmentDocId\": \"90989110_20191106_719468_VENDOR_US\",\n"
                    + "          \"shipmentNumber\": \"90989110\",\n"
                    + "          \"sourceGlobalLocationNumber\": \"0069382035222\",\n"
                    + "          \"destinationGlobalLocationNumber\": \"0078742035222\"\n"
                    + "        }\n"
                    + "      ],\n"
                    + "      \"manufactureDetails\": [\n"
                    + "        {\n"
                    + "          \"lot\": \"00L032C09A\",\n"
                    + "          \"expiryDate\": \"2025-01-08\",\n"
                    + "          \"qty\": 10,\n"
                    + "          \"reportedUom\": \"ZA\"\n"
                    + "        }\n"
                    + "      ],\n"
                    + "      \"additionalInfo\": {\n"
                    + "        \"warehouseGroupCode\": \"\",\n"
                    + "        \"isNewItem\": false,\n"
                    + "        \"weight\": 0,\n"
                    + "        \"warehouseMinLifeRemainingToReceive\": 0,\n"
                    + "        \"isDscsaExemptionInd\": false,\n"
                    + "        \"isHACCP\": false,\n"
                    + "        \"primeSlotSize\": 0,\n"
                    + "        \"isHazardous\": 0,\n"
                    + "        \"atlasConvertedItem\": false,\n"
                    + "        \"isWholesaler\": false,\n"
                    + "        \"isDefaultTiHiUsed\": false,\n"
                    + "        \"qtyValidationDone\": true,\n"
                    + "        \"isEpcisEnabledVendor\": true,\n"
                    + "        \"auditQty\": 1,\n"
                    + "        \"lotList\": [\n"
                    + "          \"00L032C09E\",\n"
                    + "          \"00L032C09B\",\n"
                    + "          \"00L032C09A\",\n"
                    + "          \"00L032C09D\",\n"
                    + "          \"00L032C09C\"\n"
                    + "        ],\n"
                    + "        \"gtinList\": [\n"
                    + "          \"00368180121015\"\n"
                    + "        ],\n"
                    + "        \"attpQtyInEaches\": 15,\n"
                    + "        \"scannedCaseAttpQtyUOM\": 1,\n"
                    + "        \"serializedInfo\": [\n"
                    + "          {\n"
                    + "            \"lot\": \"00L032C09C\",\n"
                    + "            \"serial\": \"abc124\",\n"
                    + "            \"expiryDate\": \"2025-01-08\",\n"
                    + "            \"reportedUom\": \"CA\",\n"
                    + "            \"gtin\": \"00368180121015\"\n"
                    + "          },\n"
                    + "          {\n"
                    + "            \"lot\": \"00L032C09C\",\n"
                    + "            \"serial\": \"abc124\",\n"
                    + "            \"expiryDate\": \"2025-01-08\",\n"
                    + "            \"reportedUom\": \"CA\",\n"
                    + "            \"gtin\": \"00368180121015\"\n"
                    + "          },\n"
                    + "          {\n"
                    + "            \"lot\": \"00L032C09C\",\n"
                    + "            \"serial\": \"abc124\",\n"
                    + "            \"expiryDate\": \"2025-01-08\",\n"
                    + "            \"reportedUom\": \"CA\",\n"
                    + "            \"gtin\": \"00368180121015\"\n"
                    + "          },\n"
                    + "          {\n"
                    + "            \"lot\": \"00L032C09C\",\n"
                    + "            \"serial\": \"abc124\",\n"
                    + "            \"expiryDate\": \"2025-01-08\",\n"
                    + "            \"reportedUom\": \"CA\",\n"
                    + "            \"gtin\": \"00368180121015\"\n"
                    + "          },\n"
                    + "          {\n"
                    + "            \"lot\": \"00L032C09C\",\n"
                    + "            \"serial\": \"abc124\",\n"
                    + "            \"expiryDate\": \"2025-01-08\",\n"
                    + "            \"reportedUom\": \"CA\",\n"
                    + "            \"gtin\": \"00368180121015\"\n"
                    + "          }\n"
                    + "        ]\n"
                    + "      },\n"
                    + "      \"deptNumber\": \"38\"\n"
                    + "    }\n"
                    + "  ],\n"
                    + "  \"weight\": 0,\n"
                    + "  \"cubeQty\": 0,\n"
                    + "  \"poTypeCode\": 28,\n"
                    + "  \"deliveryStatus\": \"ARV\",\n"
                    + "  \"totalBolFbq\": 106\n"
                    + "}");

    return instruction;
  }

}
