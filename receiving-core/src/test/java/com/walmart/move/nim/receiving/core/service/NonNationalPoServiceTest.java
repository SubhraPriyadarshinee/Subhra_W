package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.common.MovePublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.mock.data.MockInstruction;
import com.walmart.move.nim.receiving.core.model.FdeCreateContainerResponse;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.InstructionResponseImplNew;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.gdm.v3.DeliveryList;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.repositories.ReceiptRepository;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.StringUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class NonNationalPoServiceTest extends ReceivingTestBase {

  @InjectMocks private NonNationalPoService nonNationalPoService;
  @InjectMocks private InstructionPersisterService instructionPersisterService;
  @InjectMocks private ReceiptService receiptService;
  @InjectMocks private ContainerService containerServiceInjectMocked;
  @InjectMocks private InstructionService instructionServiceInjectMock;
  @Spy NonNationalPoService spyNonNationalPo;
  @Mock private NonNationalPoService NonNationalPoServiceMocked;
  @Mock private InstructionHelperService instructionServiceHelper;
  @Mock private ReceiptService receiptServiceMocked;
  @Mock private AppConfig appConfig;
  @Mock private FdeService fdeService;
  @Mock private MovePublisher movePublisher;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private JmsPublisher jmsPublisher;
  @Mock private PrintJobService printJobService;
  @Mock private ReceiptRepository receiptRepository;
  @Mock private InstructionRepository instructionRepository;
  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private InstructionPersisterService instructPersisterService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private ContainerService containerService;
  @Mock private DeliveryServiceImpl deliveryService;
  @Mock private CCLabelIdProcessor ccLabelIdProcessor;
  @Mock private PrintLabelHelper printLabelHelper;

  private FdeCreateContainerResponse fdeCreateContainerResponseForDSDC =
      MockInstruction.getFdeCreateContainerResponseForDSDC();
  private FdeCreateContainerResponse fdeCreateContainerResponseForPocon =
      MockInstruction.getFdeCreateContainerResponseForPOCON();

  private HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
  private Gson gson = new Gson();
  private DeliveryList deliveries;

  private InstructionRequest dsdcInstructionRequestWithDeliveryDocuments =
      MockInstruction.getInstructionRequestWithDeliveryDocumentsForDSDC();

  private InstructionRequest poconInstructionRequestWithDeliveryDocuments =
      MockInstruction.getInstructionRequestWithDeliveryDocumentsForPoCon();

  private InstructionRequest clientRequestForDSDCPO = MockInstruction.clientRequestForDSDCPOs();
  private InstructionRequest clientRequestForPOCONPO = MockInstruction.clientRequestForPOCONPOs();

  private InstructionRequest poconMultiPOInstructionRequestWithDeliveryDocuments =
      MockInstruction.getMultiPoInstructionRequestWithDeliveryDocumentsForPoCon();

  private InstructionRequest clientRequestForDSDCPrintJob =
      MockInstruction.clientRequestForDSDCPrintJob();

  private InstructionRequest clientRequestForPOCONPrintJob =
      MockInstruction.clientRequestForPOCONPrintJob();

  private Instruction dsdcInstruction = MockInstruction.getCreatedInstruction();
  private Container container1 = MockInstruction.getContainer();
  private Instruction poconInstruction = MockInstruction.getCreatedInstruction();

  private Container container;
  private Container poConContainer = new Container();
  private Container dsdcContainer = new Container();
  private ContainerItem poconContainerItem = new ContainerItem();
  private ContainerItem dsdcContainerItem = new ContainerItem();
  private ContainerItem containerItem;
  private List<ContainerItem> containerItems = new ArrayList<>();
  Map<String, Object> printRequest;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(
        nonNationalPoService, "instructionPersisterService", instructionPersisterService);
    ReflectionTestUtils.setField(nonNationalPoService, "gson", gson);
    ReflectionTestUtils.setField(nonNationalPoService, "deliveryService", deliveryService);
    ReflectionTestUtils.setField(instructionServiceInjectMock, "gson", gson);
    ReflectionTestUtils.setField(
        nonNationalPoService, "tenantSpecificConfigReader", tenantSpecificConfigReader);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32987);

    Map<String, String> ctrDestination = new HashMap<String, String>();
    ctrDestination.put("countryCode", "US");
    ctrDestination.put("buNumber", "32987");

    container = new Container();
    container.setTrackingId("a32L8990000000000000106519");
    container.setMessageId("aebdfdf0-feb6-11e8-9ed2-f32La312b7689");
    container.setInventoryStatus("PICKED");
    container.setLocation("171");
    container.setDeliveryNumber(21119003L);
    container.setFacility(ctrDestination);
    container.setDestination(ctrDestination);
    container.setContainerType("PALLET");
    container.setContainerStatus("");
    container.setWeight(5F);
    container.setWeightUOM("LB");
    container.setCube(1F);
    container.setCubeUOM("CF");
    container.setCtrShippable(Boolean.TRUE);
    container.setCtrShippable(Boolean.TRUE);
    container.setCompleteTs(new Date());
    container.setOrgUnitId("1");
    container.setPublishTs(new Date());
    container.setCreateTs(new Date(0));
    container.setCreateUser("sysAdmin");
    container.setLastChangedTs(null);
    container.setLastChangedUser("sysAdmin");
    container.setCtrReusable(true);

    poConContainer = container;
    dsdcContainer = container;

    containerItem = new ContainerItem();
    containerItem.setPurchaseReferenceNumber("45678967");
    containerItem.setTrackingId("a328990000000000000106519");

    containerItem.setPurchaseCompanyId(1);
    containerItem.setPoDeptNumber("0092");
    containerItem.setDeptNumber(1);
    containerItem.setItemNumber(-1L);
    containerItem.setVendorGS128("");
    containerItem.setVnpkQty(1);
    containerItem.setWhpkQty(1);
    containerItem.setQuantity(1);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES); // by default EA
    containerItem.setBaseDivisionCode("VM");
    containerItem.setFinancialReportingGroupCode("US");
    containerItem.setRotateDate(new Date());
    containerItem.setTotalPurchaseReferenceQty(100);

    containerItems.add(containerItem);
    container.setContainerItems(containerItems);

    dsdcContainerItem = containerItem;
    dsdcContainerItem.setPoTypeCode(73);
    dsdcContainerItem.setInboundChannelMethod("DSDC");
    dsdcContainerItem.setOutboundChannelMethod("DSDC");

    poconContainerItem = containerItem;
    poconContainerItem.setPoTypeCode(20);
    poconContainerItem.setInboundChannelMethod("POCON");
    poconContainerItem.setOutboundChannelMethod("POCON");

    poConContainer.setContainerItems(Arrays.asList(poconContainerItem));
    dsdcContainer.setContainerItems(Arrays.asList(dsdcContainerItem));

    Map<String, Object> labelData = new HashMap<String, Object>();
    labelData.put("key", "ITEM");
    labelData.put("value", "100001");
    labelData.put("key", "DESTINATION");
    labelData.put("value", "06021 US");
    labelData.put("key", "UPCBAR");
    labelData.put("value", "00075486091132");
    labelData.put("key", "LPN");
    labelData.put("value", "a328990000000000000106509");
    labelData.put("key", "FULLUSERID");
    labelData.put("value", "sysadmin");
    labelData.put("key", "TYPE");
    labelData.put("value", "DA");
    labelData.put("key", "DESC1");
    labelData.put("value", "Sample");

    List<Map<String, Object>> labelDataList = new ArrayList<Map<String, Object>>();
    labelDataList.add(labelData);

    printRequest = new HashMap<>();
    printRequest.put("labelIdentifier", "a328990000000000000106509");
    printRequest.put("formatName", "pallet_lpn_format");
    printRequest.put("ttlInHours", 1);
    printRequest.put("data", labelDataList);
  }

  @AfterMethod
  public void tearDown() {
    reset(configUtils);
    reset(fdeService);
    reset(instructPersisterService);
    reset(configUtils);
    reset(jmsPublisher);
    reset(movePublisher);
    reset(instructionRepository);
    reset(instructionServiceHelper);
    reset(tenantSpecificConfigReader);
    reset(receiptServiceMocked);
  }

  /**
   * This method is used to check for DSDC instruction creation
   *
   * @throws ReceivingException
   */
  @Test
  public void testGetInstructionForDSDCReceiving_caseWise() throws ReceivingException, IOException {
    HashMap<String, Object> printJobMap = new HashMap<>();
    InstructionResponseImplNew instructionResponse =
        new InstructionResponseImplNew(
            null, MockInstruction.getDeliveryDocumentsForPOCON(), dsdcInstruction, printJobMap);
    doNothing()
        .when(instructionServiceHelper)
        .publishInstruction(any(), any(), any(), any(), any(), any());
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);

    doReturn(instructionResponse)
        .when(instructionServiceHelper)
        .prepareInstructionResponse(any(), any(), any(), any());

    when(instructionRepository.save(Mockito.any())).thenReturn(dsdcInstruction);
    when(containerService.processCreateContainersForNonNationalPO(any(), any(), any()))
        .thenReturn(dsdcContainer);
    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    doReturn(gson.toJson(fdeCreateContainerResponseForDSDC)).when(fdeService).receive(any(), any());

    nonNationalPoService.createInstructionForNonNationalPoReceiving(
        dsdcInstructionRequestWithDeliveryDocuments, httpHeaders);

    verify(fdeService, times(1)).receive(any(), any());
    verify(instructionServiceHelper, times(3))
        .publishInstruction(any(), any(), any(), any(), any(), any());

    ArgumentCaptor<Container> argumentCaptor = ArgumentCaptor.forClass(Container.class);
    verify(instructionServiceHelper)
        .publishConsolidatedContainer(argumentCaptor.capture(), any(), any(Boolean.class));
    Container container = argumentCaptor.getValue();

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../receiving-test/src/main/resources/jsonSchema/nonNationalPOsPublishContainerMessageSchema.json")
                            .getCanonicalPath()))),
            gson.toJson(container)));
  }

  /**
   * This method is used to check for DSDC instruction creation
   *
   * @throws ReceivingException
   */
  @Test
  public void testGetInstructionForPoconReceiving_caseWise()
      throws ReceivingException, IOException {
    HashMap<String, Object> printJobMap = new HashMap<>();
    InstructionResponseImplNew instructionResponse =
        new InstructionResponseImplNew(
            null, MockInstruction.getDeliveryDocumentsForPOCON(), poconInstruction, printJobMap);
    doNothing()
        .when(instructionServiceHelper)
        .publishInstruction(any(), any(), any(), any(), any(), any());
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);

    doReturn(instructionResponse)
        .when(instructionServiceHelper)
        .prepareInstructionResponse(any(), any(), any(), any());
    when(instructionRepository.save(Mockito.any())).thenReturn(poconInstruction);
    when(containerService.processCreateContainersForNonNationalPO(any(), any(), any()))
        .thenReturn(poConContainer);
    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    doReturn(gson.toJson(fdeCreateContainerResponseForPocon))
        .when(fdeService)
        .receive(any(), any());
    nonNationalPoService.createInstructionForNonNationalPoReceiving(
        poconInstructionRequestWithDeliveryDocuments, httpHeaders);
    verify(fdeService, times(1)).receive(any(), any());
    verify(instructionServiceHelper, times(3))
        .publishInstruction(any(), any(), any(), any(), any(), any());

    ArgumentCaptor<Container> argumentCaptor = ArgumentCaptor.forClass(Container.class);
    verify(instructionServiceHelper)
        .publishConsolidatedContainer(argumentCaptor.capture(), any(), any(Boolean.class));
    Container container = argumentCaptor.getValue();

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../receiving-test/src/main/resources/jsonSchema/nonNationalPOsPublishContainerMessageSchema.json")
                            .getCanonicalPath()))),
            gson.toJson(container)));
  }

  /**
   * This method is used to check for POCON instruction creation
   *
   * @throws ReceivingException
   */
  @Test
  public void testGetInstructionForPoconReceiving_palletWise()
      throws ReceivingException, IOException {
    InstructionResponseImplNew instructionResponse =
        new InstructionResponseImplNew(
            null,
            MockInstruction.getDeliveryDocumentsForPOCON(),
            poconInstruction,
            new HashMap<>());
    ArgumentCaptor<Integer> avgQty = ArgumentCaptor.forClass(Integer.class);

    doNothing()
        .when(instructionServiceHelper)
        .publishInstruction(any(), any(), any(), any(), any(), any());
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);

    doReturn(instructionResponse)
        .when(instructionServiceHelper)
        .prepareInstructionResponse(any(), any(), any(), any());
    when(instructionRepository.save(Mockito.any())).thenReturn(poconInstruction);
    when(containerService.processCreateContainersForNonNationalPO(any(), any(), any()))
        .thenReturn(poConContainer);
    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    doReturn(gson.toJson(fdeCreateContainerResponseForPocon))
        .when(fdeService)
        .receive(any(), any());

    InstructionRequest instructionRequest = poconInstructionRequestWithDeliveryDocuments;
    instructionRequest.getDeliveryDocuments().get(0).setEnteredPalletQty(1);
    instructionRequest.getDeliveryDocuments().get(0).setQuantity(null);
    instructionRequest.getDeliveryDocuments().get(0).setPalletQty(2);
    instructionRequest.getDeliveryDocuments().get(0).setTotalPurchaseReferenceQty(10);

    nonNationalPoService.createInstructionForNonNationalPoReceiving(
        instructionRequest, httpHeaders);
    verify(fdeService, times(1)).receive(any(), any());
    verify(instructionServiceHelper, times(3))
        .publishInstruction(any(), any(), avgQty.capture(), any(), any(), any());

    assertEquals(avgQty.getAllValues().get(1), Integer.valueOf(5));

    ArgumentCaptor<Container> argumentCaptor = ArgumentCaptor.forClass(Container.class);
    verify(instructionServiceHelper)
        .publishConsolidatedContainer(argumentCaptor.capture(), any(), any(Boolean.class));
    Container container = argumentCaptor.getValue();

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../receiving-test/src/main/resources/jsonSchema/nonNationalPOsPublishContainerMessageSchema.json")
                            .getCanonicalPath()))),
            gson.toJson(container)));
  }

  /**
   * This method is used to check exception
   *
   * @throws ReceivingException
   */
  @Test
  public void testGetInstructionForNonNationalPoReceivingExceptionCase() throws ReceivingException {

    doThrow(
            new ReceivingException(
                ReceivingException.FDE_RECEIVE_FDE_CALL_FAILED,
                HttpStatus.SERVICE_UNAVAILABLE,
                ReceivingException.CREATE_INSTRUCTION_ERROR_CODE))
        .when(fdeService)
        .receive(any(), any());
    try {
      nonNationalPoService.createInstructionForNonNationalPoReceiving(
          dsdcInstructionRequestWithDeliveryDocuments, httpHeaders);
    } catch (ReceivingException e) {
      assert (true);
    }
    verify(fdeService, times(1)).receive(any(), any());
  }

  /**
   * This method is used to check exception
   *
   * @throws ReceivingException
   */
  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp = "There is an error while completing instruction.")
  public void testGetInstructionForNonNationalReceiving_ExceptionCasewhileUpdate()
      throws ReceivingException {
    doNothing()
        .when(instructionServiceHelper)
        .publishInstruction(any(), any(), any(), any(), any(), any());

    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    doReturn(gson.toJson(fdeCreateContainerResponseForDSDC)).when(fdeService).receive(any(), any());

    nonNationalPoService.createInstructionForNonNationalPoReceiving(
        dsdcInstructionRequestWithDeliveryDocuments, httpHeaders);
  }

  /**
   * Thise method is used to check exception
   *
   * @throws ReceivingException
   */
  @Test
  public void testGetInstructionForPoconReceivingExceptionCase() throws ReceivingException {

    doThrow(
            new ReceivingException(
                ReceivingException.FDE_RECEIVE_FDE_CALL_FAILED,
                HttpStatus.SERVICE_UNAVAILABLE,
                ReceivingException.CREATE_INSTRUCTION_ERROR_CODE))
        .when(fdeService)
        .receive(any(), any());
    try {
      nonNationalPoService.createInstructionForNonNationalPoReceiving(
          poconInstructionRequestWithDeliveryDocuments, httpHeaders);
    } catch (ReceivingException e) {
      assert (true);
    }
    verify(fdeService, times(1)).receive(any(), any());
  }

  @Test
  public void testProcessCreateContainersForDSDC() {
    reset(containerPersisterService);
    when(configUtils.getConfiguredInstance(any(), any(), any())).thenReturn(ccLabelIdProcessor);
    ArgumentCaptor<Container> argCaptorContainer = ArgumentCaptor.forClass(Container.class);
    containerServiceInjectMocked.processCreateContainersForNonNationalPO(
        dsdcInstruction, dsdcInstructionRequestWithDeliveryDocuments, httpHeaders);

    verify(containerPersisterService, Mockito.times(1)).saveContainer(argCaptorContainer.capture());
    Container savedContainer = argCaptorContainer.getValue();
    assertEquals("sysadmin", savedContainer.getLastChangedUser());
    assertNotEquals(null, savedContainer.getLastChangedTs());
  }

  @Test
  public void testProcessCreateContainersForPocon() {
    reset(containerPersisterService);
    when(configUtils.getConfiguredInstance(any(), any(), any())).thenReturn(ccLabelIdProcessor);
    containerServiceInjectMocked.processCreateContainersForNonNationalPO(
        poconInstruction, poconInstructionRequestWithDeliveryDocuments, httpHeaders);
    verify(containerPersisterService, Mockito.times(1)).saveContainer(any(Container.class));
  }

  @Test
  public void testProcessCreateContainersForPocon_Imports() {
    poconInstructionRequestWithDeliveryDocuments
        .getDeliveryDocuments()
        .get(0)
        .setImportInd(Boolean.TRUE);
    reset(containerPersisterService);
    when(configUtils.getConfiguredInstance(any(), any(), any())).thenReturn(ccLabelIdProcessor);
    containerServiceInjectMocked.processCreateContainersForNonNationalPO(
        poconInstruction, poconInstructionRequestWithDeliveryDocuments, httpHeaders);
    ArgumentCaptor<Container> argumentCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerPersisterService, Mockito.times(1)).saveContainer(argumentCaptor.capture());
    assertTrue(
        argumentCaptor
            .getValue()
            .getContainerItems()
            .stream()
            .allMatch(
                o ->
                    o.getImportInd()
                        && !StringUtils.isEmpty(o.getPoDCNumber())
                        && !StringUtils.isEmpty(o.getPoDcCountry())));
    poconInstructionRequestWithDeliveryDocuments.getDeliveryDocuments().get(0).setImportInd(null);
  }

  @Test
  public void testProcessCreateContainersForPocon_NotImports() {
    reset(containerPersisterService);
    when(configUtils.getConfiguredInstance(any(), any(), any())).thenReturn(ccLabelIdProcessor);
    containerServiceInjectMocked.processCreateContainersForNonNationalPO(
        poconInstruction, poconInstructionRequestWithDeliveryDocuments, httpHeaders);
    ArgumentCaptor<Container> argumentCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerPersisterService, Mockito.times(1)).saveContainer(argumentCaptor.capture());
    assertTrue(
        argumentCaptor
            .getValue()
            .getContainerItems()
            .stream()
            .noneMatch(
                o ->
                    (Objects.nonNull(o.getImportInd()) && o.getImportInd())
                        || !StringUtils.isEmpty(o.getPoDCNumber())
                        || !StringUtils.isEmpty(o.getPoDcCountry())));
  }

  @Test
  public void testProcessCreateContainersForPocon_MPP() {
    reset(containerPersisterService);
    when(configUtils.getConfiguredInstance(any(), any(), any())).thenReturn(ccLabelIdProcessor);
    containerServiceInjectMocked.processCreateContainersForNonNationalPO(
        poconInstruction, poconMultiPOInstructionRequestWithDeliveryDocuments, httpHeaders);
    ArgumentCaptor<Container> argumentCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerPersisterService, Mockito.times(1)).saveContainer(argumentCaptor.capture());
    assertTrue(
        argumentCaptor
            .getValue()
            .getContainerItems()
            .stream()
            .allMatch(ContainerItem::getIsMultiPoPallet));
  }

  @Test
  public void testProcessCreateContainersForPocon_NotMPP() {
    reset(containerPersisterService);
    when(configUtils.getConfiguredInstance(any(), any(), any())).thenReturn(ccLabelIdProcessor);
    containerServiceInjectMocked.processCreateContainersForNonNationalPO(
        poconInstruction, poconInstructionRequestWithDeliveryDocuments, httpHeaders);
    ArgumentCaptor<Container> argumentCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerPersisterService, Mockito.times(1)).saveContainer(argumentCaptor.capture());
    assertTrue(
        argumentCaptor
            .getValue()
            .getContainerItems()
            .stream()
            .noneMatch(ContainerItem::getIsMultiPoPallet));
  }

  @Test
  public void testCreateReceiptsForDSDC() {

    receiptService.createReceiptsFromInstructionForNonNationalPo(
        dsdcInstruction, poconInstructionRequestWithDeliveryDocuments, "XYZ");

    verify(receiptRepository, times(1)).saveAll(any());
    reset(receiptRepository);
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp = "DSDC receiving is not available at this DC.")
  public void testDsdcFeatureFlagDisabled_Error() throws ReceivingException {
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(any(), any())).thenReturn(false);
    instructionServiceInjectMock.serveInstructionRequest(
        gson.toJson(clientRequestForDSDCPO), httpHeaders);
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp = "POCON receiving is not available at this DC.")
  public void testPoconFeatureFlagDisabled_Error() throws ReceivingException {
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(any(), any())).thenReturn(false);
    instructionServiceInjectMock.serveInstructionRequest(
        gson.toJson(clientRequestForPOCONPO), httpHeaders);
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp = "No po/poline found in 89768798")
  public void testDsdcNotAvailableInDelivery() throws ReceivingException {
    String gdmResponse =
        "{\n" + "    \"data\": [],\n" + "    \"pageSize\": 10,\n" + "    \"pageNumber\": 0\n" + "}";

    deliveries = gson.fromJson(gdmResponse, DeliveryList.class);
    when(deliveryService.getDeliveryDocumentByPOChannelType(any(), any(), any()))
        .thenReturn(gson.toJson(deliveries));
    nonNationalPoService.serveNonNationalPoRequest(clientRequestForDSDCPrintJob, httpHeaders);
  }

  @Test
  public void testDsdcFeatureFlagEnabled_Success() throws ReceivingException {
    reset(NonNationalPoServiceMocked);
    reset(tenantSpecificConfigReader);
    ReflectionTestUtils.setField(
        instructionServiceInjectMock, "nonNationalPoService", NonNationalPoServiceMocked);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(anyString(), anyInt())).thenReturn(true);
    when(NonNationalPoServiceMocked.serveNonNationalPoRequest(any(), any()))
        .thenReturn(new InstructionResponseImplNew());
    try {
      instructionServiceInjectMock.serveInstructionRequest(
          gson.toJson(clientRequestForDSDCPO), httpHeaders);
    } catch (ReceivingException re) {
      fail();
    }
    verify(tenantSpecificConfigReader, times(1)).isFeatureFlagEnabled(anyString(), any());
  }

  @Test
  public void testPoconFeatureFlagEnabled_Success() throws ReceivingException {
    reset(NonNationalPoServiceMocked);
    reset(tenantSpecificConfigReader);
    ReflectionTestUtils.setField(
        instructionServiceInjectMock, "nonNationalPoService", NonNationalPoServiceMocked);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(anyString(), anyInt())).thenReturn(true);
    when(NonNationalPoServiceMocked.serveNonNationalPoRequest(any(), any()))
        .thenReturn(new InstructionResponseImplNew());
    try {
      instructionServiceInjectMock.serveInstructionRequest(
          gson.toJson(clientRequestForPOCONPO), httpHeaders);
    } catch (ReceivingException re) {
      fail();
    }
    verify(tenantSpecificConfigReader, times(1)).isFeatureFlagEnabled(anyString(), any());
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "CubeQty, weight and UOM information are mandatory for DSDC request.Please see a supervisor for assistance.")
  public void testMandatoryFieldsMissing() throws ReceivingException, IOException {

    String dataPath =
        new File("../receiving-test/src/main/resources/json/DSDCDeliveryV3FromGDM.json")
            .getCanonicalPath();
    deliveries =
        gson.fromJson(new String(Files.readAllBytes(Paths.get(dataPath))), DeliveryList.class);
    deliveries.getData().get(0).getPurchaseOrders().get(0).setCube(null);
    Map<String, Pair<Long, Long>> mockResponse = new HashMap();
    mockResponse.put("7320250027", new Pair<>(0L, 0L));

    when(deliveryService.getDeliveryDocumentByPOChannelType(any(), any(), any()))
        .thenReturn(gson.toJson(deliveries));
    when(receiptServiceMocked.getReceivedQtyAndPalletQtyByPoAndDeliveryNumber(anySet(), anyLong()))
        .thenReturn(mockResponse);
    nonNationalPoService.serveNonNationalPoRequest(clientRequestForDSDCPrintJob, httpHeaders);
  }

  @Test
  public void testProvideAllDSDCPOsToUI() throws ReceivingException, IOException {

    reset(deliveryService);
    String dataPath =
        new File("../receiving-test/src/main/resources/json/DSDCDeliveryV3FromGDM.json")
            .getCanonicalPath();

    deliveries =
        gson.fromJson(new String(Files.readAllBytes(Paths.get(dataPath))), DeliveryList.class);
    Map<String, Pair<Long, Long>> mockResponse = new HashMap();
    mockResponse.put("7320250027", new Pair<>(0L, 0L));

    when(deliveryService.getDeliveryDocumentByPOChannelType(any(), any(), any()))
        .thenReturn(gson.toJson(deliveries));
    when(receiptServiceMocked.getReceivedQtyAndPalletQtyByPoAndDeliveryNumber(anySet(), anyLong()))
        .thenReturn(mockResponse);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ReceivingConstants.IS_NON_NATIONAL_INSTRUCTION_V2_ENABLED)))
        .thenReturn(false);

    try {
      nonNationalPoService.serveNonNationalPoRequest(clientRequestForDSDCPO, httpHeaders);
    } catch (ReceivingException re) {
      fail();
    }
    verify(deliveryService, times(1)).getDeliveryDocumentByPOChannelType(any(), any(), any());
    verify(receiptServiceMocked, times(1))
        .getReceivedQtyAndPalletQtyByPoAndDeliveryNumber(anySet(), anyLong());
  }

  @Test
  public void testProvideAllDSDCPOsToUI_V2API_PoList() throws ReceivingException, IOException {

    reset(deliveryService);
    String dataPath =
        new File("../receiving-test/src/main/resources/json/DSDCDeliveryV3FromGDM.json")
            .getCanonicalPath();

    deliveries =
        gson.fromJson(new String(Files.readAllBytes(Paths.get(dataPath))), DeliveryList.class);
    Map<String, Pair<Long, Long>> mockResponse = new HashMap();
    mockResponse.put("7320250027", new Pair<>(0L, 0L));

    when(deliveryService.getDeliveryDocumentByPOChannelType(any(), any(), any()))
        .thenReturn(gson.toJson(deliveries));
    when(receiptServiceMocked.getReceivedQtyAndPalletQtyByPoAndDeliveryNumber(anySet(), anyLong()))
        .thenReturn(mockResponse);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ReceivingConstants.IS_NON_NATIONAL_INSTRUCTION_V2_ENABLED)))
        .thenReturn(true);

    try {
      nonNationalPoService.serveNonNationalPoRequest(clientRequestForDSDCPO, httpHeaders);
    } catch (ReceivingException re) {
      fail();
    }
    verify(deliveryService, times(1)).getDeliveryDocumentByPOChannelType(any(), any(), any());
    verify(receiptServiceMocked, times(0))
        .getReceivedQtyAndPalletQtyByPoAndDeliveryNumber(anySet(), anyLong());
  }

  @Test
  public void testProvideAllDSDCPOsToUI_V2API_SelectedPos() throws ReceivingException, IOException {

    reset(deliveryService);
    String dataPath =
        new File("../receiving-test/src/main/resources/json/DSDCDeliveryV3FromGDM.json")
            .getCanonicalPath();

    deliveries =
        gson.fromJson(new String(Files.readAllBytes(Paths.get(dataPath))), DeliveryList.class);
    Map<String, Pair<Long, Long>> mockResponse = new HashMap();
    mockResponse.put("7320250027", new Pair<>(0L, 0L));

    when(deliveryService.getDeliveryDocumentByPOChannelType(any(), any(), any()))
        .thenReturn(gson.toJson(deliveries));

    when(receiptServiceMocked.getReceivedQtyAndPalletQtyByPoAndDeliveryNumber(anySet(), anyLong()))
        .thenReturn(mockResponse);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ReceivingConstants.IS_NON_NATIONAL_INSTRUCTION_V2_ENABLED)))
        .thenReturn(true);
    doReturn(gson.toJson(fdeCreateContainerResponseForDSDC)).when(fdeService).receive(any(), any());

    try {
      nonNationalPoService.serveNonNationalPoRequest(
          dsdcInstructionRequestWithDeliveryDocuments, httpHeaders);
    } catch (ReceivingException re) {
      fail();
    }
    verify(deliveryService, times(1)).getDeliveryDocumentByPOChannelType(any(), any(), any());
    verify(receiptServiceMocked, times(1))
        .getReceivedQtyAndPalletQtyByPoAndDeliveryNumber(anySet(), anyLong());
  }

  @Test
  public void testPoConfromClientRequestToCreateV2Instruction()
      throws ReceivingException, IOException {
    reset(deliveryService);

    ReflectionTestUtils.setField(
        spyNonNationalPo, "instructionPersisterService", instructionPersisterService);
    ReflectionTestUtils.setField(spyNonNationalPo, "gson", gson);
    ReflectionTestUtils.setField(spyNonNationalPo, "deliveryService", deliveryService);
    ReflectionTestUtils.setField(spyNonNationalPo, "receiptService", receiptServiceMocked);
    ReflectionTestUtils.setField(
        spyNonNationalPo, "tenantSpecificConfigReader", tenantSpecificConfigReader);

    InstructionResponseImplNew instructionResponse =
        new InstructionResponseImplNew(
            null,
            MockInstruction.getDeliveryDocumentsForPOCON(),
            poconInstruction,
            new HashMap<>());
    String dataPath =
        new File("../receiving-test/src/main/resources/json/DSDCDeliveryV3FromGDM.json")
            .getCanonicalPath();

    deliveries =
        gson.fromJson(new String(Files.readAllBytes(Paths.get(dataPath))), DeliveryList.class);

    deliveries.getData().get(0).getPurchaseOrders().get(0).setPoType("POCON");
    deliveries
        .getData()
        .get(0)
        .getPurchaseOrders()
        .get(0)
        .getLines()
        .get(0)
        .setOriginalChannel("CROSSU");
    Map<String, Pair<Long, Long>> mockResponse = new HashMap();
    mockResponse.put("7320250027", new Pair<>(0L, 0L));

    when(deliveryService.getDeliveryDocumentByPOChannelType(any(), any(), any()))
        .thenReturn(gson.toJson(deliveries));
    when(receiptServiceMocked.getReceivedQtyAndPalletQtyByPoAndDeliveryNumber(anySet(), anyLong()))
        .thenReturn(mockResponse);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ReceivingConstants.IS_NON_NATIONAL_INSTRUCTION_V2_ENABLED)))
        .thenReturn(false);
    doReturn(instructionResponse)
        .when(instructionServiceHelper)
        .prepareInstructionResponse(any(), any(), any(), any());

    doReturn(null).when(spyNonNationalPo).createInstructionForNonNationalPoReceiving(any(), any());
    spyNonNationalPo.serveNonNationalPoRequest(clientRequestForPOCONPrintJob, httpHeaders);
  }

  @Test
  public void testPoconFlowForMUltiPO_receivingCaseWise() throws ReceivingException, IOException {
    ArgumentCaptor<Integer> totalCaseQty = ArgumentCaptor.forClass(Integer.class);

    doReturn(gson.toJson(fdeCreateContainerResponseForPocon))
        .when(fdeService)
        .receive(any(), any());
    InstructionResponseImplNew instructionResponse =
        new InstructionResponseImplNew(
            null,
            MockInstruction.getDeliveryDocumentsForPOCON(),
            poconInstruction,
            new HashMap<>());
    doNothing()
        .when(instructionServiceHelper)
        .publishInstruction(any(), any(), totalCaseQty.capture(), any(), any(), any());
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ReceivingConstants.IS_NON_NATIONAL_INSTRUCTION_V2_ENABLED)))
        .thenReturn(false);
    when(containerService.processCreateContainersForNonNationalPO(any(), any(), any()))
        .thenReturn(poConContainer);
    doReturn(instructionResponse)
        .when(instructionServiceHelper)
        .prepareInstructionResponse(any(), any(), any(), any());
    when(instructionRepository.save(Mockito.any())).thenReturn(poconInstruction);
    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    nonNationalPoService.createInstructionForNonNationalPoReceiving(
        poconMultiPOInstructionRequestWithDeliveryDocuments, httpHeaders);
    verify(instructionServiceHelper, times(3))
        .publishInstruction(any(), any(), any(), any(), any(), any());
    verify(instructionRepository, times(1)).save(any());
    verify(movePublisher, times(1))
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());

    assertEquals(totalCaseQty.getAllValues().get(1), Integer.valueOf(20));
    ArgumentCaptor<Container> argumentCaptor = ArgumentCaptor.forClass(Container.class);
    verify(instructionServiceHelper)
        .publishConsolidatedContainer(argumentCaptor.capture(), any(), any(Boolean.class));
    Container container = argumentCaptor.getValue();

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../receiving-test/src/main/resources/jsonSchema/nonNationalPOsPublishContainerMessageSchema.json")
                            .getCanonicalPath()))),
            gson.toJson(container)));
  }

  @Test
  public void testPoconFlowForMUltiPO_receivingCaseWise_DestMoveEnabled()
      throws ReceivingException, IOException {
    ArgumentCaptor<Integer> totalCaseQty = ArgumentCaptor.forClass(Integer.class);

    doReturn(gson.toJson(fdeCreateContainerResponseForPocon))
        .when(fdeService)
        .receive(any(), any());
    InstructionResponseImplNew instructionResponse =
        new InstructionResponseImplNew(
            null,
            MockInstruction.getDeliveryDocumentsForPOCON(),
            poconInstruction,
            new HashMap<>());
    doNothing()
        .when(instructionServiceHelper)
        .publishInstruction(any(), any(), totalCaseQty.capture(), any(), any(), any());
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ReceivingConstants.IS_NON_NATIONAL_INSTRUCTION_V2_ENABLED)))
        .thenReturn(false);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ReceivingConstants.MOVE_DEST_BU_ENABLED)))
        .thenReturn(true);
    when(containerService.processCreateContainersForNonNationalPO(any(), any(), any()))
        .thenReturn(poConContainer);
    doReturn(instructionResponse)
        .when(instructionServiceHelper)
        .prepareInstructionResponse(any(), any(), any(), any());
    when(instructionRepository.save(Mockito.any())).thenReturn(poconInstruction);
    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(),
            anyString(),
            any(HttpHeaders.class),
            any(LinkedTreeMap.class),
            anyString(),
            anyInt());
    nonNationalPoService.createInstructionForNonNationalPoReceiving(
        poconMultiPOInstructionRequestWithDeliveryDocuments, httpHeaders);
    verify(instructionServiceHelper, times(3))
        .publishInstruction(any(), any(), any(), any(), any(), any());
    verify(instructionRepository, times(1)).save(any());
    verify(movePublisher, times(0))
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    verify(movePublisher, times(1))
        .publishMove(
            anyInt(),
            anyString(),
            any(HttpHeaders.class),
            any(LinkedTreeMap.class),
            anyString(),
            anyInt());

    assertEquals(totalCaseQty.getAllValues().get(1), Integer.valueOf(20));
    ArgumentCaptor<Container> argumentCaptor = ArgumentCaptor.forClass(Container.class);
    verify(instructionServiceHelper)
        .publishConsolidatedContainer(argumentCaptor.capture(), any(), any(Boolean.class));
    Container container = argumentCaptor.getValue();

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../receiving-test/src/main/resources/jsonSchema/nonNationalPOsPublishContainerMessageSchema.json")
                            .getCanonicalPath()))),
            gson.toJson(container)));
  }

  @Test
  public void testPoconFlowForMUltiPO_receivingPalletWise() throws ReceivingException, IOException {

    ArgumentCaptor<Integer> totalQty = ArgumentCaptor.forClass(Integer.class);

    doReturn(gson.toJson(fdeCreateContainerResponseForPocon))
        .when(fdeService)
        .receive(any(), any());
    InstructionResponseImplNew instructionResponse =
        new InstructionResponseImplNew(
            null,
            MockInstruction.getDeliveryDocumentsForPOCON(),
            poconInstruction,
            new HashMap<>());
    doNothing()
        .when(instructionServiceHelper)
        .publishInstruction(any(), any(), totalQty.capture(), any(), any(), any());
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);
    when(containerService.processCreateContainersForNonNationalPO(any(), any(), any()))
        .thenReturn(poConContainer);
    doReturn(instructionResponse)
        .when(instructionServiceHelper)
        .prepareInstructionResponse(any(), any(), any(), any());
    when(instructionRepository.save(Mockito.any())).thenReturn(poconInstruction);
    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());

    InstructionRequest instructionRequest = poconMultiPOInstructionRequestWithDeliveryDocuments;
    instructionRequest.getDeliveryDocuments().get(0).setEnteredPalletQty(1);
    instructionRequest.getDeliveryDocuments().get(1).setEnteredPalletQty(1);
    instructionRequest.getDeliveryDocuments().get(0).setQuantity(null);
    instructionRequest.getDeliveryDocuments().get(1).setQuantity(null);

    nonNationalPoService.createInstructionForNonNationalPoReceiving(
        poconMultiPOInstructionRequestWithDeliveryDocuments, httpHeaders);
    verify(instructionServiceHelper, times(3))
        .publishInstruction(any(), any(), any(), any(), any(), any());
    verify(instructionRepository, times(1)).save(any());
    verify(movePublisher, times(1))
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    assertEquals(totalQty.getAllValues().get(1), Integer.valueOf(17));
    ArgumentCaptor<Container> argumentCaptor = ArgumentCaptor.forClass(Container.class);
    verify(instructionServiceHelper)
        .publishConsolidatedContainer(argumentCaptor.capture(), any(), any(Boolean.class));
    Container container = argumentCaptor.getValue();

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../receiving-test/src/main/resources/jsonSchema/nonNationalPOsPublishContainerMessageSchema.json")
                            .getCanonicalPath()))),
            gson.toJson(container)));
  }

  /**
   * This method is used to check for POCON instruction creation
   *
   * @throws ReceivingException
   */
  @Test
  public void testGetInstructionForPoconReceiving_palletWiseWhenFbqAndPalletQtyRatioIsLessThan1()
      throws ReceivingException, IOException {
    InstructionResponseImplNew instructionResponse =
        new InstructionResponseImplNew(
            null,
            MockInstruction.getDeliveryDocumentsForPOCON(),
            poconInstruction,
            new HashMap<>());
    ArgumentCaptor<Integer> avgQty = ArgumentCaptor.forClass(Integer.class);

    doNothing()
        .when(instructionServiceHelper)
        .publishInstruction(any(), any(), avgQty.capture(), any(), any(), any());
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);

    doReturn(instructionResponse)
        .when(instructionServiceHelper)
        .prepareInstructionResponse(any(), any(), any(), any());
    when(instructionRepository.save(Mockito.any())).thenReturn(poconInstruction);
    when(containerService.processCreateContainersForNonNationalPO(any(), any(), any()))
        .thenReturn(poConContainer);
    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    doReturn(gson.toJson(fdeCreateContainerResponseForPocon))
        .when(fdeService)
        .receive(any(), any());

    InstructionRequest instructionRequest = poconInstructionRequestWithDeliveryDocuments;
    instructionRequest.getDeliveryDocuments().get(0).setEnteredPalletQty(1);
    instructionRequest.getDeliveryDocuments().get(0).setQuantity(null);
    instructionRequest.getDeliveryDocuments().get(0).setTotalPurchaseReferenceQty(3);
    instructionRequest.getDeliveryDocuments().get(0).setPalletQty(5);

    nonNationalPoService.createInstructionForNonNationalPoReceiving(
        instructionRequest, httpHeaders);
    verify(fdeService, times(1)).receive(any(), any());
    verify(instructionServiceHelper, times(3))
        .publishInstruction(any(), any(), any(), any(), any(), any());

    assertEquals(avgQty.getAllValues().get(1), Integer.valueOf(1));

    ArgumentCaptor<Container> argumentCaptor = ArgumentCaptor.forClass(Container.class);
    verify(instructionServiceHelper)
        .publishConsolidatedContainer(argumentCaptor.capture(), any(), any(Boolean.class));
    Container container = argumentCaptor.getValue();

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../receiving-test/src/main/resources/jsonSchema/nonNationalPOsPublishContainerMessageSchema.json")
                            .getCanonicalPath()))),
            gson.toJson(container)));
  }

  /**
   * This method is used to check for POCON instruction creation
   *
   * @throws ReceivingException
   */
  @Test
  public void testGetInstructionForPoconReceiving_palletWiseWhenPalletQtyIsSetToDefault()
      throws ReceivingException, IOException {
    InstructionResponseImplNew instructionResponse =
        new InstructionResponseImplNew(
            null,
            MockInstruction.getDeliveryDocumentsForPOCON(),
            poconInstruction,
            new HashMap<>());
    ArgumentCaptor<Integer> avgQty = ArgumentCaptor.forClass(Integer.class);

    doNothing()
        .when(instructionServiceHelper)
        .publishInstruction(any(), any(), avgQty.capture(), any(), any(), any());
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);

    doReturn(instructionResponse)
        .when(instructionServiceHelper)
        .prepareInstructionResponse(any(), any(), any(), any());
    when(instructionRepository.save(Mockito.any())).thenReturn(poconInstruction);
    when(containerService.processCreateContainersForNonNationalPO(any(), any(), any()))
        .thenReturn(poConContainer);
    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    doReturn(gson.toJson(fdeCreateContainerResponseForPocon))
        .when(fdeService)
        .receive(any(), any());

    InstructionRequest instructionRequest = poconInstructionRequestWithDeliveryDocuments;
    instructionRequest.getDeliveryDocuments().get(0).setEnteredPalletQty(1);
    instructionRequest.getDeliveryDocuments().get(0).setQuantity(null);
    instructionRequest.getDeliveryDocuments().get(0).setTotalPurchaseReferenceQty(3);
    instructionRequest.getDeliveryDocuments().get(0).setPalletQty(1);

    nonNationalPoService.createInstructionForNonNationalPoReceiving(
        instructionRequest, httpHeaders);
    verify(fdeService, times(1)).receive(any(), any());
    verify(instructionServiceHelper, times(3))
        .publishInstruction(any(), any(), any(), any(), any(), any());

    // TODO Confirm with product if this is expected or it should be 1 per pallet
    assertEquals(avgQty.getAllValues().get(1), Integer.valueOf(3));

    ArgumentCaptor<Container> argumentCaptor = ArgumentCaptor.forClass(Container.class);
    verify(instructionServiceHelper)
        .publishConsolidatedContainer(argumentCaptor.capture(), any(), any(Boolean.class));
    Container container = argumentCaptor.getValue();

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../receiving-test/src/main/resources/jsonSchema/nonNationalPOsPublishContainerMessageSchema.json")
                            .getCanonicalPath()))),
            gson.toJson(container)));
  }

  /**
   * This method is used to check for POCON instruction creation
   *
   * @throws ReceivingException
   */
  @Test
  public void testGetInstructionForPoconReceiving_expectedFieldsAreNull()
      throws ReceivingException, IOException {
    InstructionResponseImplNew instructionResponse =
        new InstructionResponseImplNew(
            null,
            MockInstruction.getDeliveryDocumentsForPOCON(),
            poconInstruction,
            new HashMap<>());
    ArgumentCaptor<Integer> avgQty = ArgumentCaptor.forClass(Integer.class);

    doNothing()
        .when(instructionServiceHelper)
        .publishInstruction(any(), any(), avgQty.capture(), any(), any(), any());
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(true);

    doReturn(instructionResponse)
        .when(instructionServiceHelper)
        .prepareInstructionResponse(any(), any(), any(), any());
    when(instructionRepository.save(Mockito.any())).thenReturn(poconInstruction);
    poConContainer.getContainerItems().get(0).setPoTypeCode(null);
    when(containerService.processCreateContainersForNonNationalPO(any(), any(), any()))
        .thenReturn(poConContainer);
    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
    doReturn(gson.toJson(fdeCreateContainerResponseForPocon))
        .when(fdeService)
        .receive(any(), any());

    InstructionRequest instructionRequest = poconInstructionRequestWithDeliveryDocuments;
    instructionRequest.getDeliveryDocuments().get(0).setEnteredPalletQty(1);
    instructionRequest.getDeliveryDocuments().get(0).setQuantity(null);
    instructionRequest.getDeliveryDocuments().get(0).setTotalPurchaseReferenceQty(3);
    instructionRequest.getDeliveryDocuments().get(0).setPalletQty(5);

    nonNationalPoService.createInstructionForNonNationalPoReceiving(
        instructionRequest, httpHeaders);
    verify(fdeService, times(1)).receive(any(), any());
    verify(instructionServiceHelper, times(3))
        .publishInstruction(any(), any(), any(), any(), any(), any());

    assertEquals(avgQty.getAllValues().get(1), Integer.valueOf(1));

    ArgumentCaptor<Container> argumentCaptor = ArgumentCaptor.forClass(Container.class);
    verify(instructionServiceHelper)
        .publishConsolidatedContainer(argumentCaptor.capture(), any(), any(Boolean.class));
    Container container = argumentCaptor.getValue();

    assertFalse(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../receiving-test/src/main/resources/jsonSchema/nonNationalPOsPublishContainerMessageSchema.json")
                            .getCanonicalPath()))),
            gson.toJson(container)));
    poConContainer.getContainerItems().get(0).setPoTypeCode(20);
  }
}
