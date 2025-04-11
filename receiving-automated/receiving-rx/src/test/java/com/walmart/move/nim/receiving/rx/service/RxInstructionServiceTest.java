package com.walmart.move.nim.receiving.rx.service;

import static com.walmart.move.nim.receiving.rx.constants.RxConstants.*;
import static com.walmart.move.nim.receiving.rx.model.RxInstructionType.BUILD_PARTIAL_CONTAINER_UPC_RECEIVING;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_DC_ONE_ATLAS_ENABLED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_GDM_SHIPMENT_GET_BY_SCAN_v4_ENABLED;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.*;
import com.walmart.move.nim.receiving.core.common.validators.DeliveryValidator;
import com.walmart.move.nim.receiving.core.common.validators.PurchaseReferenceValidator;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.*;
import com.walmart.move.nim.receiving.core.framework.transformer.Transformer;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.GtinHierarchy;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Pack;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PackItemResponse;
import com.walmart.move.nim.receiving.core.model.gdm.v3.pack.UnitSerialRequest;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelData;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.data.MockRxHttpHeaders;
import com.walmart.move.nim.receiving.rx.builders.RxContainerLabelBuilder;
import com.walmart.move.nim.receiving.rx.builders.RxReceiptsBuilder;
import com.walmart.move.nim.receiving.rx.common.RxLpnUtils;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.config.RxManagedConfig;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.mock.MockInstruction;
import com.walmart.move.nim.receiving.rx.mock.MockRDSContainer;
import com.walmart.move.nim.receiving.rx.model.RxInstructionType;
import com.walmart.move.nim.receiving.rx.model.RxReceivingType;
import com.walmart.move.nim.receiving.rx.publisher.RxCancelInstructionReceiptPublisher;
import com.walmart.move.nim.receiving.rx.validators.RxInstructionValidator;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.POLineStatus;
import com.walmart.move.nim.receiving.utils.constants.POStatus;
import com.walmart.move.nim.receiving.utils.constants.ProblemResolutionType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.mockito.*;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RxInstructionServiceTest {

  private Gson gson = new Gson();

  @InjectMocks private RxInstructionService rxInstructionService;
  @Mock private RxDeliveryServiceImpl rxDeliveryService;
  @Mock private RxInstructionPersisterService rxInstructionPersisterService;
  @Mock private InstructionPersisterService instructionPersisterService;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private InstructionHelperService instructionHelperService;
  @Mock private RxContainerLabelBuilder containerLabelBuilder;
  @Spy private DeliveryDocumentHelper deliveryDocumentHelper = new DeliveryDocumentHelper();
  @Mock private EpcisService epcisService;
  @Mock private ContainerService containerService;
  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private ContainerItemRepository containerItemRepository;
  @Mock private PrintJobService printJobService;
  @Mock private NimRdsServiceImpl nimRdsServiceImpl;
  @Mock private InstructionRepository instructionRepository;
  @Mock private ReceiptService receiptService;
  @Mock private JmsPublisher jmsPublisher;
  @Mock private AppConfig appConfig;
  @Mock private RxManagedConfig rxManagedConfig;
  @Mock private ProblemService problemService;
  @Mock private PurchaseReferenceValidator purchaseReferenceValidator;
  @Mock private RxSlottingServiceImpl rxSlottingServiceImpl;
  @Mock private InstructionSetIdGenerator instructionSetIdGenerator;
  @Mock private DeliveryStatusPublisher deliveryStatusPublisher;
  @Mock private RxCancelInstructionReceiptPublisher rxCancelInstructionReceiptsPublisher;
  @Mock private RegulatedItemService regulatedItemService;
  @Mock private RxInstructionValidator rxInstructionValidator;
  @Spy private RxReceiptsBuilder rxReceiptsBuilder = new RxReceiptsBuilder();
  @Mock private ShipmentSelectorService shipmentSelector;
  @Mock private Transformer<Container, ContainerDTO> transformer;
  @InjectMocks @Spy private RxInstructionHelperService rxInstructionHelperService;
  @Mock private RxFixitProblemService rxFixitProblemService;
  @Mock private DeliveryService deliveryService;
  @Mock private RestUtils restUtils;

  @Mock private RxLpnUtils rxLpnUtils;

  @InjectMocks @Spy
  private TwoDBarcodeScanTypeDocumentsSearchHandler twoDBarcodeScanTypeDocumentsSearchHandler;

  @Captor private ArgumentCaptor<Instruction> instructionCaptor;

  private HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();

  @BeforeClass
  public void initMocks() {

    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(rxInstructionService, "gson", gson);
    ReflectionTestUtils.setField(
        rxInstructionService, InstructionService.class, "gson", gson, Gson.class);
    ReflectionTestUtils.setField(
        rxInstructionService,
        InstructionService.class,
        "deliveryValidator",
        new DeliveryValidator(),
        DeliveryValidator.class);
    ReflectionTestUtils.setField(
        rxInstructionService, "purchaseReferenceValidator", purchaseReferenceValidator);
    ReflectionTestUtils.setField(
        rxInstructionService, "containerLabelBuilder", containerLabelBuilder);
    ReflectionTestUtils.setField(rxInstructionService, "receiptService", receiptService);
    ReflectionTestUtils.setField(rxInstructionHelperService, "gson", gson);
    ReflectionTestUtils.setField(rxInstructionHelperService, "appConfig", appConfig);
    ReflectionTestUtils.setField(
        rxInstructionService, "rxInstructionHelperService", rxInstructionHelperService);
    ReflectionTestUtils.setField(
        rxInstructionService, "regulatedItemService", regulatedItemService);
    ReflectionTestUtils.setField(rxInstructionHelperService, "gson", gson);
    ReflectionTestUtils.setField(twoDBarcodeScanTypeDocumentsSearchHandler, "gson", gson);
    ReflectionTestUtils.setField(
        deliveryDocumentHelper, "tenantSpecificConfigReader", tenantSpecificConfigReader);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32898);

    doReturn(false).when(appConfig).isCloseDateCheckEnabled();
  }

  @AfterMethod
  public void afterMethod() {
    reset(rxDeliveryService);
    reset(instructionPersisterService);
    reset(configUtils);
    reset(tenantSpecificConfigReader);
    reset(instructionHelperService);
    reset(deliveryDocumentHelper);
    reset(epcisService);
    reset(containerService);
    reset(containerItemRepository);
    reset(nimRdsServiceImpl);
    reset(instructionRepository);
    reset(receiptService);
    reset(jmsPublisher);
    reset(appConfig);
    reset(rxSlottingServiceImpl);
    reset(rxInstructionHelperService);
    reset(instructionSetIdGenerator);
    reset(rxReceiptsBuilder);
    reset(rxCancelInstructionReceiptsPublisher);
    reset(regulatedItemService);
    reset(rxInstructionPersisterService);
    reset(shipmentSelector);
    reset(rxManagedConfig);
    reset(rxFixitProblemService);
    reset(deliveryService);
    reset(deliveryStatusPublisher);
    reset(rxLpnUtils);
  }

  @BeforeMethod
  public void beforeMethod() {
    doReturn(true).when(appConfig).isOverrideServeInstrMethod();
    doReturn(365).when(appConfig).getCloseDateLimitDays();
    doReturn(false).when(rxManagedConfig).isProblemItemCheckEnabled();
    doReturn(true).when(rxManagedConfig).isTrimCompleteInstructionResponseEnabled();
    doReturn(true).when(rxManagedConfig).isPublishContainersToKafkaEnabled();
    doReturn(false)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ReceivingConstants.IS_IQS_INTEGRATION_ENABLED);
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
        .autoSelectShipment(any(DeliveryDocumentLine.class), anyMap());
  }

  @Test
  public void test_serveInstructionRequest() throws Exception {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
    instructionRequest.setDeliveryNumber("2356895623");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setSscc("00123456789562356895656");
    instructionRequest.setDoorNumber("V6949");

    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData ssccScannedData = new ScannedData();
    ssccScannedData.setKey(ApplicationIdentifier.SSCC.getKey());
    ssccScannedData.setValue("001234567897984653");
    scannedDataList.add(ssccScannedData);

    instructionRequest.setScannedDataList(scannedDataList);
    instructionRequest.setReceivingType(RxReceivingType.SSCC.getReceivingType());

    File resource = new ClassPathResource("GdmMappedResponseV2.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    doReturn("MOCK_REPACKAGING_VENDOR1,MOCK_REPACKAGING_VENDOR1")
        .when(appConfig)
        .getRepackageVendors();
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doReturn(twoDBarcodeScanTypeDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            "32898",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(rxDeliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class)));
    doReturn(0L)
        .when(receiptService)
        .findReceiptsSummaryByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndSSCC(
            anyLong(), anyString(), anyInt(), anyString());
    doReturn(new Pair<>(50, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsInEaAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber()));
    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) {
                List<DeliveryDocument> deliveryDocuments =
                    (List<DeliveryDocument>) invocation.getArguments()[0];

                return new Pair<>(deliveryDocuments.get(0), 0);
              }
            })
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    InstructionResponse serveInstructionResponse =
        rxInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    assertNotNull(serveInstructionResponse);
    assertEquals(serveInstructionResponse.getDeliveryDocuments().size(), 1);
    assertEquals(
        serveInstructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(),
        1);
    assertEquals(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getManufactureDetails()
            .size(),
        1);
    assertNotNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPalletSSCC());
    assertNotNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPackSSCC());

    verify(instructionHelperService, times(1))
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());
  }

  @Test
  public void test_serveInstructionRequest_PartialCase() throws Exception {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
    instructionRequest.setDeliveryNumber("2356895623");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setSscc("00123456789562356895656");
    instructionRequest.setDoorNumber("V6949");

    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData ssccScannedData = new ScannedData();
    ssccScannedData.setKey(ApplicationIdentifier.SSCC.getKey());
    ssccScannedData.setValue("001234567897984653");
    scannedDataList.add(ssccScannedData);

    instructionRequest.setScannedDataList(scannedDataList);
    instructionRequest.setReceivingType(RxReceivingType.SSCC.getReceivingType());

    File resource = new ClassPathResource("PartialCaseMappedGdmResponse.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    doReturn("MOCK_REPACKAGING_VENDOR1,MOCK_REPACKAGING_VENDOR1")
        .when(appConfig)
        .getRepackageVendors();
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    when(rxDeliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class)));
    doReturn(0L)
        .when(receiptService)
        .findReceiptsSummaryByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndSSCC(
            anyLong(), anyString(), anyInt(), anyString());
    doReturn(twoDBarcodeScanTypeDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            "32898",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    doReturn(new Pair<>(50, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsInEaAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber()));

    doAnswer(
            (Answer<Pair>)
                invocation -> {
                  List<DeliveryDocument> deliveryDocuments =
                      (List<DeliveryDocument>) invocation.getArguments()[0];

                  return new Pair<>(deliveryDocuments.get(0), 0);
                })
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    InstructionResponse serveInstructionResponse =
        rxInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    assertNotNull(serveInstructionResponse);
    assertEquals(serveInstructionResponse.getDeliveryDocuments().size(), 1);
    assertEquals(
        serveInstructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(),
        1);
    assertEquals(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getManufactureDetails()
            .size(),
        1);
    assertEquals(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPalletSSCC(),
        "");
    assertNotNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPackSSCC());
    assertEquals(
        serveInstructionResponse.getInstruction().getInstructionCode(),
        RxInstructionType.BUILD_PARTIAL_CONTAINER.getInstructionType());
    verify(instructionHelperService, times(1))
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());
  }

  @Test
  public void test_serveInstructionRequest_PartialCase_2DBarcode() throws Exception {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
    instructionRequest.setDeliveryNumber("2356895623");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setSscc("00123456789562356895656");
    instructionRequest.setDoorNumber("V6949");

    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData ssccScannedData = new ScannedData();
    ssccScannedData.setKey(ApplicationIdentifier.SSCC.getKey());
    ssccScannedData.setValue("001234567897984653");
    scannedDataList.add(ssccScannedData);

    ScannedData gtinScannedData = new ScannedData();
    gtinScannedData.setKey(ApplicationIdentifier.GTIN.getKey());
    gtinScannedData.setApplicationIdentifier("01");
    gtinScannedData.setValue("001234567897984653");
    scannedDataList.add(gtinScannedData);

    instructionRequest.setScannedDataList(scannedDataList);
    instructionRequest.setReceivingType(RxReceivingType.TWOD_BARCODE_PARTIALS.getReceivingType());

    File resource = new ClassPathResource("PartialCaseMappedGdmResponse.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    doReturn("MOCK_REPACKAGING_VENDOR1,MOCK_REPACKAGING_VENDOR1")
        .when(appConfig)
        .getRepackageVendors();
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    when(rxDeliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class)));
    doReturn(0L)
        .when(receiptService)
        .findReceiptsSummaryByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndSSCC(
            anyLong(), anyString(), anyInt(), anyString());
    doReturn(twoDBarcodeScanTypeDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            "32898",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    doReturn(new Pair<>(50, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsInEaAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber()));

    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) {
                List<DeliveryDocument> deliveryDocuments =
                    (List<DeliveryDocument>) invocation.getArguments()[0];

                return new Pair<>(deliveryDocuments.get(0), 0);
              }
            })
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    InstructionResponse serveInstructionResponse =
        rxInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    assertNotNull(serveInstructionResponse);
    assertEquals(serveInstructionResponse.getDeliveryDocuments().size(), 1);
    assertEquals(
        serveInstructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(),
        1);
    assertEquals(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getManufactureDetails()
            .size(),
        1);
    assertEquals(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPalletSSCC(),
        "");
    assertNotNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPackSSCC());
    assertEquals(
        serveInstructionResponse.getInstruction().getInstructionCode(),
        RxInstructionType.BUILD_PARTIAL_CONTAINER.getInstructionType());
    verify(instructionHelperService, times(1))
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());
  }

  @Test
  public void testServeInstructionRequestForCaseReceiving() throws Exception {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
    instructionRequest.setDeliveryNumber("2356895623");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setSscc("B32899000020014243");
    instructionRequest.setDoorNumber("V6949");

    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData ssccScannedData = new ScannedData();
    ssccScannedData.setKey(ApplicationIdentifier.SSCC.getKey());
    ssccScannedData.setValue("B32899000020014243");
    scannedDataList.add(ssccScannedData);

    instructionRequest.setScannedDataList(scannedDataList);
    instructionRequest.setReceivingType(RxReceivingType.TWOD_BARCODE.getReceivingType());

    File resource = new ClassPathResource("GdmMappedResponseV2_pack_sscc_response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    doReturn("MOCK_REPACKAGING_VENDOR1,MOCK_REPACKAGING_VENDOR1")
        .when(appConfig)
        .getRepackageVendors();
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doReturn(twoDBarcodeScanTypeDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            "32898",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(rxDeliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class)));
    doReturn(0L)
        .when(receiptService)
        .receivedQtyByDeliveryPoAndPoLine(anyLong(), anyString(), anyInt());
    doReturn(new Pair<>(50, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsInEaAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber()));
    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) {
                List<DeliveryDocument> deliveryDocuments =
                    (List<DeliveryDocument>) invocation.getArguments()[0];

                return new Pair<>(deliveryDocuments.get(0), 0);
              }
            })
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    InstructionResponse serveInstructionResponse =
        rxInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    assertNotNull(serveInstructionResponse);
    assertEquals(serveInstructionResponse.getDeliveryDocuments().size(), 1);
    assertEquals(
        serveInstructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(),
        1);
    assertEquals(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getManufactureDetails()
            .size(),
        1);
    assertNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPalletSSCC());
    assertNotNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPackSSCC());
    verify(instructionHelperService, times(1))
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());
  }

  @Test
  public void testServeInstructionRequestForCaseReceivingEpcisEnabledVendor() throws Exception {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
    instructionRequest.setDeliveryNumber("2356895623");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setSscc("B32899000020014243");
    instructionRequest.setDoorNumber("V6949");

    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData ssccScannedData = new ScannedData();
    ssccScannedData.setKey(ApplicationIdentifier.SSCC.getKey());
    ssccScannedData.setValue("B32899000020014243");
    scannedDataList.add(ssccScannedData);

    instructionRequest.setScannedDataList(scannedDataList);
    instructionRequest.setReceivingType(RxReceivingType.TWOD_BARCODE.getReceivingType());

    File resource = new ClassPathResource("GdmMappedResponseV2_pack_sscc_response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    doReturn("MOCK_REPACKAGING_VENDOR1,MOCK_REPACKAGING_VENDOR1")
        .when(appConfig)
        .getRepackageVendors();
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doReturn(twoDBarcodeScanTypeDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            "32898",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    List<DeliveryDocument> mockResponseDelivery =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));

    ItemData additionalInfo =
        mockResponseDelivery.get(0).getDeliveryDocumentLines().get(0).getAdditionalInfo();

    List<ManufactureDetail> serializedInfo = new ArrayList<>();
    ManufactureDetail manufactureDetail = new ManufactureDetail();
    manufactureDetail.setLot("ABCDEF1234");
    manufactureDetail.setExpiryDate("12-30-2020");
    manufactureDetail.setGtin("20029695410987");
    manufactureDetail.setQty(1);
    serializedInfo.add(manufactureDetail);
    mockResponseDelivery
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setSerializedInfo(serializedInfo);

    Pack pack = new Pack();
    //    pack.setItems(Arrays.asList(items));
    pack.setUnitCount(2.0);
    pack.setPalletNumber("B32899000020014243");
    List<Pack> packs = new ArrayList<>();
    packs.add(pack);
    mockResponseDelivery.get(0).getDeliveryDocumentLines().get(0).setPacks(packs);
    additionalInfo.setIsEpcisEnabledVendor(true);

    //    instructionRequest.setDeliveryDocuments(mockResponseDelivery);
    when(rxDeliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class)))
        .thenReturn(mockResponseDelivery);

    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag("32898", IS_GDM_SHIPMENT_GET_BY_SCAN_v4_ENABLED, false);
    doReturn(0L)
        .when(receiptService)
        .receivedQtyByDeliveryPoAndPoLine(anyLong(), anyString(), anyInt());
    doReturn(new Pair<>(50, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsInEaAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber()));
    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) {
                List<DeliveryDocument> deliveryDocuments =
                    (List<DeliveryDocument>) invocation.getArguments()[0];

                return new Pair<>(deliveryDocuments.get(0), 0);
              }
            })
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());
    InstructionResponse serveInstructionResponse = null;
    try {
      serveInstructionResponse =
          rxInstructionService.serveInstructionRequest(
              gson.toJson(instructionRequest), httpHeaders);
    } catch (ReceivingException e) {
      fail(e.getMessage());
    } catch (ReceivingBadDataException e) {
      assertNotNull(e.getErrorCode());
      assertNotNull(e.getLocalizedMessage());
    }
    assertNull(serveInstructionResponse);
    verify(instructionHelperService, times(1))
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());
  }

  @Test
  public void testServeInstructionRequestForCaseReceivingAutoSwitch() throws Exception {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
    instructionRequest.setDeliveryNumber("2356895623");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setSscc("B32899000020014243");
    instructionRequest.setDoorNumber("V6949");

    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData ssccScannedData = new ScannedData();
    ssccScannedData.setKey(ApplicationIdentifier.SSCC.getKey());
    ssccScannedData.setValue("B32899000020014243");
    scannedDataList.add(ssccScannedData);

    instructionRequest.setScannedDataList(scannedDataList);
    instructionRequest.setReceivingType(RxReceivingType.TWOD_BARCODE.getReceivingType());

    File resource = new ClassPathResource("GdmMappedResponseV2_pack_sscc_response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    doReturn("MOCK_REPACKAGING_VENDOR1,MOCK_REPACKAGING_VENDOR1")
        .when(appConfig)
        .getRepackageVendors();
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doReturn(twoDBarcodeScanTypeDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            "32898",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    List<DeliveryDocument> mockResponseDelivery =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));

    ItemData additionalInfo =
        mockResponseDelivery.get(0).getDeliveryDocumentLines().get(0).getAdditionalInfo();
    additionalInfo.setAutoSwitchEpcisToAsn(true);
    additionalInfo.setIsEpcisEnabledVendor(true);

    instructionRequest.setDeliveryDocuments(mockResponseDelivery);
    //    when(rxDeliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
    //            anyString(), anyString(), any(HttpHeaders.class)))
    //        .thenReturn(Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class)));

    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag("32898", IS_GDM_SHIPMENT_GET_BY_SCAN_v4_ENABLED, false);
    doReturn(0L)
        .when(receiptService)
        .receivedQtyByDeliveryPoAndPoLine(anyLong(), anyString(), anyInt());
    doReturn(new Pair<>(50, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsInEaAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber()));
    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) {
                List<DeliveryDocument> deliveryDocuments =
                    (List<DeliveryDocument>) invocation.getArguments()[0];

                return new Pair<>(deliveryDocuments.get(0), 0);
              }
            })
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    InstructionResponse serveInstructionResponse =
        rxInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);

    assertNotNull(serveInstructionResponse);
    assertEquals(serveInstructionResponse.getDeliveryDocuments().size(), 1);
    assertEquals(
        serveInstructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(),
        1);
    assertEquals(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getManufactureDetails()
            .size(),
        1);
    assertNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPalletSSCC());
    assertNotNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPackSSCC());
    verify(instructionHelperService, times(1))
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());
  }

  @Test
  public void test_serveInstructionRequest_SecondInstructionForSamePallet() throws Exception {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
    instructionRequest.setDeliveryNumber("2356895623");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setSscc("00123456789562356895656");
    instructionRequest.setDoorNumber("V6949");

    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData ssccScannedData = new ScannedData();
    ssccScannedData.setKey(ApplicationIdentifier.SSCC.getKey());
    ssccScannedData.setValue("001234567897984653");
    scannedDataList.add(ssccScannedData);

    instructionRequest.setScannedDataList(scannedDataList);
    instructionRequest.setReceivingType(RxReceivingType.SSCC.getReceivingType());

    File resource = new ClassPathResource("GdmMappedResponseV2.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    doReturn("MOCK_REPACKAGING_VENDOR1,MOCK_REPACKAGING_VENDOR1")
        .when(appConfig)
        .getRepackageVendors();
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doReturn(twoDBarcodeScanTypeDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            "32898",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(rxDeliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class)));
    doReturn(new Pair<>(72, 36L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsInEaAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber()));

    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) {
                List<DeliveryDocument> deliveryDocuments =
                    (List<DeliveryDocument>) invocation.getArguments()[0];

                return new Pair<>(deliveryDocuments.get(0), 0);
              }
            })
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    InstructionResponse serveInstructionResponse =
        rxInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    assertNotNull(serveInstructionResponse);
    assertEquals(serveInstructionResponse.getDeliveryDocuments().size(), 1);
    assertEquals(
        serveInstructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(),
        1);
    assertEquals(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getManufactureDetails()
            .size(),
        1);
    assertNotNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPalletSSCC());
    assertNotNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPackSSCC());
    assertEquals(serveInstructionResponse.getInstruction().getProjectedReceiveQty(), 6);
    verify(instructionHelperService, times(1))
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_serveInstructionRequest_all_cases_received() throws Exception {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
    instructionRequest.setDeliveryNumber("2356895623");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setSscc("00123456789562356895656");
    instructionRequest.setDoorNumber("V6949");

    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData ssccScannedData = new ScannedData();
    ssccScannedData.setKey(ApplicationIdentifier.SSCC.getKey());
    ssccScannedData.setValue("001234567897984653");
    scannedDataList.add(ssccScannedData);

    instructionRequest.setScannedDataList(scannedDataList);
    instructionRequest.setReceivingType(RxReceivingType.SSCC.getReceivingType());

    File resource = new ClassPathResource("GdmMappedResponseV2.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    doReturn("MOCK_REPACKAGING_VENDOR1,MOCK_REPACKAGING_VENDOR1")
        .when(appConfig)
        .getRepackageVendors();
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doReturn(twoDBarcodeScanTypeDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            "32898",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(rxDeliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class)));
    doReturn(new Pair<>(72, 72L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsInEaAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber()));

    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) {
                List<DeliveryDocument> deliveryDocuments =
                    (List<DeliveryDocument>) invocation.getArguments()[0];

                return new Pair<>(deliveryDocuments.get(0), 0);
              }
            })
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    InstructionResponse serveInstructionResponse =
        rxInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    assertNotNull(serveInstructionResponse);
    assertEquals(serveInstructionResponse.getDeliveryDocuments().size(), 1);
    assertEquals(
        serveInstructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(),
        1);
    assertEquals(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getManufactureDetails()
            .size(),
        1);
    assertNotNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPalletSSCC());
    assertNotNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPackSSCC());
    verify(instructionHelperService, times(1))
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());
  }

  @Test
  public void testServeInstructionRequestReturnsNewPartialInstructionFor2dBarcodeScan()
      throws Exception {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequestFor2dBarcodeScan();
    instructionRequest.setReceivingType(RxReceivingType.TWOD_BARCODE_PARTIALS.getReceivingType());

    Pair<DeliveryDocument, Long> autoSelectedDocument =
        new Pair<DeliveryDocument, Long>(instructionRequest.getDeliveryDocuments().get(0), 0L);
    doReturn("MOCK_REPACKAGING_VENDOR1,MOCK_REPACKAGING_VENDOR1")
        .when(appConfig)
        .getRepackageVendors();
    doReturn(autoSelectedDocument)
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    doReturn(new ArrayList<>())
        .when(instructionPersisterService)
        .findInstructionByDeliveryAndGtin(anyLong());
    doReturn(0L)
        .when(receiptService)
        .findReceiptsSummaryByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndSSCC(
            anyLong(), anyString(), anyInt(), anyString());

    doReturn(new Pair<>(50, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsInEaAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber()));

    doReturn(getRxPartialInstructionFor2dBarcodeReceiving_CaseUpc())
        .when(instructionPersisterService)
        .saveInstruction(any(Instruction.class));

    Instruction instructionResponse =
        rxInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);

    assertNotNull(instructionResponse);

    Map<String, ScannedData> scannedDataMap =
        RxUtils.scannedDataMap(instructionRequest.getScannedDataList());
    String gtin = scannedDataMap.get(ApplicationIdentifier.GTIN.getKey()).getValue();
    assertTrue(instructionResponse.getGtin().equalsIgnoreCase(gtin));

    assertTrue(
        instructionResponse
            .getInstructionMsg()
            .equalsIgnoreCase(RxInstructionType.BUILD_PARTIAL_CONTAINER.getInstructionMsg()));
    assertTrue(
        instructionResponse
            .getInstructionCode()
            .equalsIgnoreCase(RxInstructionType.BUILD_PARTIAL_CONTAINER.getInstructionType()));

    verify(instructionPersisterService, times(1)).findInstructionByDeliveryAndGtin(anyLong());
  }

  @Test
  public void testServeInstructionRequestReturnsExistingPartialInstructionFor2dBarcodeScan()
      throws Exception {
    InstructionRequest instructionRequest =
        MockInstruction.getInstructionRequestFor2dBarcodeScan_NewInstruction();
    instructionRequest.setReceivingType(RxReceivingType.TWOD_BARCODE_PARTIALS.getReceivingType());

    Pair<DeliveryDocument, Long> autoSelectedDocument =
        new Pair<DeliveryDocument, Long>(instructionRequest.getDeliveryDocuments().get(0), 0L);
    doReturn("MOCK_REPACKAGING_VENDOR1,MOCK_REPACKAGING_VENDOR1")
        .when(appConfig)
        .getRepackageVendors();
    doReturn(autoSelectedDocument)
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    doReturn(Arrays.asList(getRxPartialInstructionFor2dBarcodeReceiving()))
        .when(instructionPersisterService)
        .findInstructionByDeliveryAndGtin(anyLong());
    doReturn(0L)
        .when(receiptService)
        .findReceiptsSummaryByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndSSCC(
            anyLong(), anyString(), anyInt(), anyString());

    doReturn(new Pair<>(50, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsInEaAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber()));

    doReturn(getRxPartialInstructionFor2dBarcodeReceiving())
        .when(instructionPersisterService)
        .saveInstruction(any(Instruction.class));

    Instruction instructionResponse =
        rxInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);

    assertNotNull(instructionResponse);

    Map<String, ScannedData> scannedDataMap =
        RxUtils.scannedDataMap(instructionRequest.getScannedDataList());
    String gtin = scannedDataMap.get(ApplicationIdentifier.GTIN.getKey()).getValue();
    assertTrue(instructionResponse.getGtin().equalsIgnoreCase(gtin));

    assertTrue(
        instructionResponse
            .getInstructionMsg()
            .equalsIgnoreCase(RxInstructionType.BUILD_PARTIAL_CONTAINER.getInstructionMsg()));
    assertTrue(
        instructionResponse
            .getInstructionCode()
            .equalsIgnoreCase(RxInstructionType.BUILD_PARTIAL_CONTAINER.getInstructionType()));

    verify(instructionPersisterService, times(1)).findInstructionByDeliveryAndGtin(anyLong());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testServeInstructionRequestNewPartialInstructionExists_PartialInsAlreadyPresent()
      throws Exception {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequestFor2dBarcodeScan();
    instructionRequest.setReceivingType(RxReceivingType.TWOD_BARCODE_PARTIALS.getReceivingType());

    Pair<DeliveryDocument, Long> autoSelectedDocument =
        new Pair<DeliveryDocument, Long>(instructionRequest.getDeliveryDocuments().get(0), 0L);
    doReturn("MOCK_REPACKAGING_VENDOR1,MOCK_REPACKAGING_VENDOR1")
        .when(appConfig)
        .getRepackageVendors();
    doReturn(autoSelectedDocument)
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());
    Instruction existingInstruction = getRxPartialInstructionFor2dBarcodeReceiving();
    existingInstruction.setCreateUserId("user1");
    existingInstruction.setLastChangeUserId("user1");
    doReturn(Arrays.asList(existingInstruction))
        .when(instructionPersisterService)
        .findInstructionByDeliveryAndGtin(anyLong());

    doReturn(new Pair<>(50, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsInEaAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber()));

    doReturn(getRxPartialInstructionFor2dBarcodeReceiving())
        .when(instructionPersisterService)
        .saveInstruction(any(Instruction.class));

    Instruction instructionResponse =
        rxInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);

    assertNotNull(instructionResponse);

    Map<String, ScannedData> scannedDataMap =
        RxUtils.scannedDataMap(instructionRequest.getScannedDataList());
    String gtin = scannedDataMap.get(ApplicationIdentifier.GTIN.getKey()).getValue();
    assertTrue(instructionResponse.getGtin().equalsIgnoreCase(gtin));

    assertTrue(
        instructionResponse
            .getInstructionMsg()
            .equalsIgnoreCase(RxInstructionType.BUILD_PARTIAL_CONTAINER.getInstructionMsg()));
    assertTrue(
        instructionResponse
            .getInstructionCode()
            .equalsIgnoreCase(RxInstructionType.BUILD_PARTIAL_CONTAINER.getInstructionType()));

    verify(instructionPersisterService, times(1)).findInstructionByDeliveryAndGtin(anyLong());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testServeInstructionRequestNewPartialInstructionExists_RegularInsAlreadyPresent()
      throws Exception {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequestFor2dBarcodeScan();
    instructionRequest.setReceivingType(RxReceivingType.TWOD_BARCODE_PARTIALS.getReceivingType());

    Pair<DeliveryDocument, Long> autoSelectedDocument =
        new Pair<DeliveryDocument, Long>(instructionRequest.getDeliveryDocuments().get(0), 0L);
    doReturn("MOCK_REPACKAGING_VENDOR1,MOCK_REPACKAGING_VENDOR1")
        .when(appConfig)
        .getRepackageVendors();
    doReturn(autoSelectedDocument)
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    doReturn(Arrays.asList(getRxInstructionFor2dBarcodeReceiving()))
        .when(instructionPersisterService)
        .findInstructionByDeliveryAndGtin(anyLong());

    doReturn(new Pair<>(50, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsInEaAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber()));

    doReturn(getRxPartialInstructionFor2dBarcodeReceiving())
        .when(instructionPersisterService)
        .saveInstruction(any(Instruction.class));

    Instruction instructionResponse =
        rxInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);

    assertNotNull(instructionResponse);

    Map<String, ScannedData> scannedDataMap =
        RxUtils.scannedDataMap(instructionRequest.getScannedDataList());
    String gtin = scannedDataMap.get(ApplicationIdentifier.GTIN.getKey()).getValue();
    assertTrue(instructionResponse.getGtin().equalsIgnoreCase(gtin));

    assertTrue(
        instructionResponse
            .getInstructionMsg()
            .equalsIgnoreCase(RxInstructionType.BUILD_PARTIAL_CONTAINER.getInstructionMsg()));
    assertTrue(
        instructionResponse
            .getInstructionCode()
            .equalsIgnoreCase(RxInstructionType.BUILD_PARTIAL_CONTAINER.getInstructionType()));

    verify(instructionPersisterService, times(1)).findInstructionByDeliveryAndGtin(anyLong());
  }

  @Test
  public void test_serveInstructionRequest_InvalidReq() throws IOException {
    InstructionResponse serveInstructionResponse = null;
    try {
      InstructionRequest instructionRequest = new InstructionRequest();
      instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
      instructionRequest.setDeliveryNumber("2356895623");
      instructionRequest.setDeliveryStatus("WRK");
      instructionRequest.setDoorNumber("V6949");

      List<ScannedData> scannedDataList = new ArrayList<>();
      ScannedData ssccScannedData = new ScannedData();
      ssccScannedData.setKey(ApplicationIdentifier.SSCC.getKey());
      ssccScannedData.setValue("001234567897984653");
      scannedDataList.add(ssccScannedData);

      instructionRequest.setScannedDataList(scannedDataList);
      File resource = new ClassPathResource("GdmMappedResponseV2.json").getFile();
      String mockResponse = new String(Files.readAllBytes(resource.toPath()));

      serveInstructionResponse =
          rxInstructionService.serveInstructionRequest(
              gson.toJson(instructionRequest), httpHeaders);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), "GLS-RCV-NO-UPC-400");
      assertEquals(
          e.getDescription(), "Unknown request. Request doesn't contain UPC or ASN barcode info.");
    } catch (ReceivingException e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
    assertNull(serveInstructionResponse);
  }

  @Test
  public void test_serveInstructionRequest_InvalidReqScannedData() throws IOException {
    InstructionResponse serveInstructionResponse = null;
    try {
      InstructionRequest instructionRequest = new InstructionRequest();
      instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
      instructionRequest.setDeliveryNumber("2356895623");
      instructionRequest.setDeliveryStatus("WRK");
      instructionRequest.setSscc("0012345678909877");
      instructionRequest.setDoorNumber("V6949");

      File resource = new ClassPathResource("GdmMappedResponseV2.json").getFile();
      String mockResponse = new String(Files.readAllBytes(resource.toPath()));
      when(rxDeliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
              anyString(), anyString(), any(HttpHeaders.class)))
          .thenReturn(Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class)));
      doReturn(twoDBarcodeScanTypeDocumentsSearchHandler)
          .when(tenantSpecificConfigReader)
          .getConfiguredInstance(
              "32898",
              ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
              DeliveryDocumentsSearchHandler.class);
      doAnswer(
              new Answer<Pair>() {
                public Pair answer(InvocationOnMock invocation) {
                  List<DeliveryDocument> deliveryDocuments =
                      (List<DeliveryDocument>) invocation.getArguments()[0];

                  return new Pair<>(deliveryDocuments.get(0), 0);
                }
              })
          .when(instructionHelperService)
          .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

      serveInstructionResponse =
          rxInstructionService.serveInstructionRequest(
              gson.toJson(instructionRequest), httpHeaders);
    } catch (ReceivingException e) {
      fail(e.getMessage());
    } catch (ReceivingBadDataException e) {
      assertNotNull(e.getErrorCode());
      assertNotNull(e.getLocalizedMessage());
    }
    assertNull(serveInstructionResponse);
  }

  @Test
  public void test_serveInstructionRequest_InvalidReqBlankScannedData() throws IOException {
    InstructionResponse serveInstructionResponse = null;
    try {
      InstructionRequest instructionRequest = new InstructionRequest();
      instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
      instructionRequest.setDeliveryNumber("2356895623");
      instructionRequest.setDeliveryStatus("WRK");
      instructionRequest.setSscc("0012345678909877");
      instructionRequest.setDoorNumber("V6949");

      List<ScannedData> scannedDataList = new ArrayList<>();
      ScannedData gtinScannedData = new ScannedData();
      gtinScannedData.setKey(ApplicationIdentifier.GTIN.getKey());
      gtinScannedData.setApplicationIdentifier("01");
      gtinScannedData.setValue("001234567897984653");
      scannedDataList.add(gtinScannedData);
      instructionRequest.setScannedDataList(scannedDataList);

      File resource = new ClassPathResource("GdmMappedResponseV2.json").getFile();
      String mockResponse = new String(Files.readAllBytes(resource.toPath()));
      when(rxDeliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
              anyString(), anyString(), any(HttpHeaders.class)))
          .thenReturn(Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class)));
      doReturn(twoDBarcodeScanTypeDocumentsSearchHandler)
          .when(tenantSpecificConfigReader)
          .getConfiguredInstance(
              "32898",
              ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
              DeliveryDocumentsSearchHandler.class);
      doAnswer(
              new Answer<Pair>() {
                public Pair answer(InvocationOnMock invocation) {
                  List<DeliveryDocument> deliveryDocuments =
                      (List<DeliveryDocument>) invocation.getArguments()[0];

                  return new Pair<>(deliveryDocuments.get(0), 0);
                }
              })
          .when(instructionHelperService)
          .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

      serveInstructionResponse =
          rxInstructionService.serveInstructionRequest(
              gson.toJson(instructionRequest), httpHeaders);
    } catch (ReceivingException e) {
      fail(e.getMessage());
    } catch (ReceivingBadDataException e) {
      assertNotNull(e.getErrorCode());
      assertNotNull(e.getLocalizedMessage());
    }
    assertNull(serveInstructionResponse);
  }

  @Test
  public void test_completeInstruction_autoSlotting() throws ReceivingException {

    Container mockConsolidatedContainer = new Container();

    Instruction mockInstructionFromDB = MockInstruction.getInstruction();
    mockInstructionFromDB.setId(12345l);
    mockInstructionFromDB.setLastChangeUserId("sysadmin");
    mockInstructionFromDB.setCreateUserId("sysadmin");
    mockInstructionFromDB.setMove(new LinkedTreeMap<>());
    ContainerDetails childContainerDetails = new ContainerDetails();
    mockInstructionFromDB.setChildContainers(Arrays.asList(childContainerDetails));

    CompleteInstructionRequest mockCompleteInstructionRequest = new CompleteInstructionRequest();
    Map<String, Object> mockMap = new HashMap<>();
    mockMap.put("instruction", mockInstructionFromDB);
    mockMap.put("container", mockConsolidatedContainer);

    doReturn(true).when(configUtils).isPrintingAndroidComponentEnabled();
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag("32898", ENABLE_EPCIS_SERVICES_FEATURE_FLAG);
    doReturn(mockInstructionFromDB).when(instructionPersisterService).getInstructionById(anyLong());
    doReturn(mockMap)
        .when(instructionPersisterService)
        .completeAndCreatePrintJob(any(HttpHeaders.class), any(Instruction.class));
    doNothing()
        .when(epcisService)
        .publishSerializedData(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            any(CompleteInstructionRequest.class),
            any(HttpHeaders.class));
    doReturn(MockRDSContainer.mockRdsContainer())
        .when(nimRdsServiceImpl)
        .acquireSlot(
            any(Instruction.class),
            nullable(CompleteInstructionRequest.class),
            any(HttpHeaders.class));
    doReturn(mockConsolidatedContainer)
        .when(containerService)
        .getContainerIncludingChild(any(Container.class));
    doReturn(getParentContainerWithChilds())
        .when(containerService)
        .getContainerWithChildsByTrackingId(anyString(), any(Boolean.class));
    doReturn(new PrintLabelData())
        .when(containerLabelBuilder)
        .generateContainerLabel(any(), any(), any(), any(), any());
    doNothing()
        .when(rxInstructionHelperService)
        .persist(any(Container.class), any(Instruction.class), anyString());

    InstructionResponse completeInstruction =
        rxInstructionService.completeInstruction(
            mockInstructionFromDB.getId(), mockCompleteInstructionRequest, httpHeaders);

    assertNotNull(completeInstruction);

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(nimRdsServiceImpl, times(1))
        .acquireSlot(
            any(Instruction.class),
            nullable(CompleteInstructionRequest.class),
            any(HttpHeaders.class));
    verify(rxInstructionHelperService, times(1))
        .persist(any(Container.class), any(Instruction.class), anyString());
    verify(epcisService, times(1))
        .publishSerializedData(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            any(CompleteInstructionRequest.class),
            any(HttpHeaders.class));
  }

  @Test
  public void test_completeInstruction_manualSlotting() throws ReceivingException {

    Container mockConsolidatedContainer = new Container();

    Instruction mockInstructionFromDB = MockInstruction.getInstruction();
    mockInstructionFromDB.setId(12345l);
    mockInstructionFromDB.setLastChangeUserId("sysadmin");
    mockInstructionFromDB.setCreateUserId("sysadmin");
    mockInstructionFromDB.setMove(new LinkedTreeMap<>());
    ContainerDetails childContainerDetails = new ContainerDetails();
    mockInstructionFromDB.setChildContainers(Arrays.asList(childContainerDetails));

    CompleteInstructionRequest mockCompleteInstructionRequest = new CompleteInstructionRequest();

    SlotDetails slotDetails = new SlotDetails();
    slotDetails.setSlotSize(78);
    slotDetails.setSlot("ABC100");
    slotDetails.setSlotRange("ABC999");
    mockCompleteInstructionRequest.setSlotDetails(slotDetails);

    Map<String, Object> mockMap = new HashMap<>();
    mockMap.put("instruction", mockInstructionFromDB);
    mockMap.put("container", mockConsolidatedContainer);

    doReturn(true).when(configUtils).isPrintingAndroidComponentEnabled();
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag("32898", ENABLE_EPCIS_SERVICES_FEATURE_FLAG);
    doReturn(mockInstructionFromDB).when(instructionPersisterService).getInstructionById(anyLong());
    doNothing()
        .when(epcisService)
        .publishSerializedData(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            any(CompleteInstructionRequest.class),
            any(HttpHeaders.class));
    doReturn(MockRDSContainer.mockRdsContainer())
        .when(nimRdsServiceImpl)
        .acquireSlot(
            any(Instruction.class), any(CompleteInstructionRequest.class), any(HttpHeaders.class));
    doReturn(mockConsolidatedContainer)
        .when(containerService)
        .getContainerIncludingChild(any(Container.class));
    doReturn(getParentContainerWithChilds())
        .when(containerService)
        .getContainerWithChildsByTrackingId(anyString(), any(Boolean.class));
    doReturn(new PrintLabelData())
        .when(containerLabelBuilder)
        .generateContainerLabel(any(), any(), any(), any(), any());
    doNothing()
        .when(rxInstructionHelperService)
        .persist(any(Container.class), any(Instruction.class), anyString());

    InstructionResponse completeInstruction =
        rxInstructionService.completeInstruction(
            mockInstructionFromDB.getId(), mockCompleteInstructionRequest, httpHeaders);

    assertNotNull(completeInstruction);

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(nimRdsServiceImpl, times(1))
        .acquireSlot(
            any(Instruction.class), any(CompleteInstructionRequest.class), any(HttpHeaders.class));
    verify(rxInstructionHelperService, times(1))
        .persist(any(Container.class), any(Instruction.class), anyString());
    verify(epcisService, times(1))
        .publishSerializedData(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            any(CompleteInstructionRequest.class),
            any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_completeInstruction_completeInstructionValidation() throws ReceivingException {

    Container mockConsolidatedContainer = new Container();

    Instruction mockInstructionFromDB = MockInstruction.getCompleteInstruction();
    mockInstructionFromDB.setId(12345l);
    mockInstructionFromDB.setLastChangeUserId("sysadmin");
    mockInstructionFromDB.setCreateUserId("sysadmin");

    CompleteInstructionRequest mockCompleteInstructionRequest = new CompleteInstructionRequest();
    PrintLabelData mockContainerLabel = new PrintLabelData();
    Map<String, Object> mockMap = new HashMap<>();
    mockMap.put("instruction", mockInstructionFromDB);
    mockMap.put("container", mockConsolidatedContainer);

    doReturn(mockInstructionFromDB).when(instructionPersisterService).getInstructionById(anyLong());
    doReturn(mockMap)
        .when(instructionPersisterService)
        .completeAndCreatePrintJob(any(HttpHeaders.class), any(Instruction.class));
    doNothing()
        .when(epcisService)
        .publishSerializedData(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            any(CompleteInstructionRequest.class),
            any(HttpHeaders.class));

    doReturn(mockConsolidatedContainer)
        .when(containerService)
        .getContainerIncludingChild(any(Container.class));

    InstructionResponse completeInstruction =
        rxInstructionService.completeInstruction(
            mockInstructionFromDB.getId(), mockCompleteInstructionRequest, httpHeaders);

    assertNotNull(completeInstruction);

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(instructionPersisterService, times(1))
        .completeAndCreatePrintJob(any(HttpHeaders.class), any(Instruction.class));
    verify(configUtils, times(1)).getConfiguredInstance(anyString(), anyString(), any(Class.class));
    verify(epcisService, times(1))
        .publishSerializedData(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            any(CompleteInstructionRequest.class),
            any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_completeInstruction_multiUserCase() throws ReceivingException {

    Container mockConsolidatedContainer = new Container();

    Instruction mockInstructionFromDB = MockInstruction.getInstruction();
    mockInstructionFromDB.setId(12345l);
    mockInstructionFromDB.setLastChangeUserId("DITSys");
    mockInstructionFromDB.setCreateUserId("DITSys");

    CompleteInstructionRequest mockCompleteInstructionRequest = new CompleteInstructionRequest();
    PrintLabelData mockContainerLabel = new PrintLabelData();
    Map<String, Object> mockMap = new HashMap<>();
    mockMap.put("instruction", mockInstructionFromDB);
    mockMap.put("container", mockConsolidatedContainer);

    doReturn(mockInstructionFromDB).when(instructionPersisterService).getInstructionById(anyLong());
    doReturn(mockMap)
        .when(instructionPersisterService)
        .completeAndCreatePrintJob(any(HttpHeaders.class), any(Instruction.class));
    doNothing()
        .when(epcisService)
        .publishSerializedData(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            any(CompleteInstructionRequest.class),
            any(HttpHeaders.class));

    doReturn(mockConsolidatedContainer)
        .when(containerService)
        .getContainerIncludingChild(any(Container.class));

    InstructionResponse completeInstruction =
        rxInstructionService.completeInstruction(
            mockInstructionFromDB.getId(), mockCompleteInstructionRequest, httpHeaders);

    assertNotNull(completeInstruction);

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(instructionPersisterService, times(1))
        .completeAndCreatePrintJob(any(HttpHeaders.class), any(Instruction.class));
    verify(configUtils, times(1)).getConfiguredInstance(anyString(), anyString(), any(Class.class));
    verify(epcisService, times(1))
        .publishSerializedData(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            any(CompleteInstructionRequest.class),
            any(HttpHeaders.class));
  }

  private Container getParentContainerWithChilds() {

    Container parentContainer = new Container();
    ContainerItem parentContainerItem = new ContainerItem();

    parentContainer.setContainerItems(Arrays.asList(parentContainerItem));

    Container childContainer = new Container();
    ContainerItem childContainerItem = new ContainerItem();

    childContainer.setContainerItems(Arrays.asList(childContainerItem));

    Set<Container> childContainerSet = new HashSet<>();
    childContainerSet.add(childContainer);
    parentContainer.setChildContainers(childContainerSet);

    return parentContainer;
  }

  @Test
  public void test_cancelInstruction() throws ReceivingException {

    Instruction mockInstruction = MockInstruction.getInstruction();

    LinkedTreeMap<String, Object> move = new LinkedTreeMap<>();
    move.put(ReceivingConstants.MOVE_FROM_LOCATION, "DUMMY_LOACTON");
    mockInstruction.setMove(move);

    doReturn(mockInstruction).when(instructionPersisterService).getInstructionById(anyLong());
    doNothing()
        .when(rxInstructionHelperService)
        .rollbackContainers(anyList(), any(Receipt.class), any(Instruction.class));
    doNothing()
        .when(rxCancelInstructionReceiptsPublisher)
        .publishReceipt(any(Instruction.class), any(HttpHeaders.class));
    doNothing()
        .when(rxCancelInstructionReceiptsPublisher)
        .publishReceipt(any(Instruction.class), any(HttpHeaders.class));

    mockInstruction.setCreateUserId("sysadmin");
    InstructionSummary cancelInstrResponse =
        rxInstructionService.cancelInstruction(12345l, httpHeaders);

    assertNotNull(cancelInstrResponse);
    assertSame(cancelInstrResponse.getReceivedQuantity(), 0);
    assertNotNull(cancelInstrResponse.getCompleteTs());
    assertNotNull(cancelInstrResponse.getCompleteUserId());

    verify(rxReceiptsBuilder, times(1))
        .buildReceiptToRollbackInEaches(any(Instruction.class), anyString(), anyInt(), anyInt());
    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(rxInstructionHelperService, times(1))
        .rollbackContainers(anyList(), any(Receipt.class), any(Instruction.class));
  }

  @Test
  public void test_cancelInstruction_rollbackReceiptsWithShipmentD40() throws ReceivingException {

    Instruction mockInstruction = MockInstruction.getInstruction();

    LinkedTreeMap<String, Object> move = new LinkedTreeMap<>();
    move.put(ReceivingConstants.MOVE_FROM_LOCATION, "DUMMY_LOACTON");
    mockInstruction.setMove(move);

    doReturn(mockInstruction).when(instructionPersisterService).getInstructionById(anyLong());
    doReturn(true).when(rxManagedConfig).isRollbackReceiptsByShipment();
    doNothing()
        .when(rxInstructionHelperService)
        .rollbackContainers(anyList(), any(List.class), any(Instruction.class));
    doNothing()
        .when(rxCancelInstructionReceiptsPublisher)
        .publishReceipt(any(Instruction.class), any(HttpHeaders.class));
    doNothing()
        .when(rxCancelInstructionReceiptsPublisher)
        .publishReceipt(any(Instruction.class), any(HttpHeaders.class));

    mockInstruction.setCreateUserId("sysadmin");
    InstructionSummary cancelInstrResponse =
        rxInstructionService.cancelInstruction(12345l, httpHeaders);

    assertNotNull(cancelInstrResponse);
    assertSame(cancelInstrResponse.getReceivedQuantity(), 0);
    assertNotNull(cancelInstrResponse.getCompleteTs());
    assertNotNull(cancelInstrResponse.getCompleteUserId());

    verify(rxReceiptsBuilder, times(1))
        .buildReceiptToRollbackInEaches(any(Instruction.class), anyString(), anyInt(), anyInt());
    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(rxInstructionHelperService, times(1))
        .rollbackContainers(anyList(), any(List.class), any(Instruction.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_completeInstruction_autoSlotting_rollbackRdsReceiptsForException()
      throws ReceivingException {

    Container mockConsolidatedContainer = new Container();

    Instruction mockInstructionFromDB = MockInstruction.getInstruction();
    mockInstructionFromDB.setId(12345l);
    mockInstructionFromDB.setLastChangeUserId("sysadmin");
    mockInstructionFromDB.setCreateUserId("sysadmin");
    mockInstructionFromDB.setMove(new LinkedTreeMap<>());
    ContainerDetails childContainerDetails = new ContainerDetails();
    mockInstructionFromDB.setChildContainers(Arrays.asList(childContainerDetails));

    CompleteInstructionRequest mockCompleteInstructionRequest = new CompleteInstructionRequest();
    Map<String, Object> mockMap = new HashMap<>();
    mockMap.put("instruction", mockInstructionFromDB);
    mockMap.put("container", mockConsolidatedContainer);

    doReturn(true).when(configUtils).isPrintingAndroidComponentEnabled();
    doReturn(true).when(rxManagedConfig).isRollbackNimRdsReceiptsEnabled();
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag("32898", ENABLE_EPCIS_SERVICES_FEATURE_FLAG);
    doReturn(mockInstructionFromDB).when(instructionPersisterService).getInstructionById(anyLong());
    doReturn(mockMap)
        .when(instructionPersisterService)
        .completeAndCreatePrintJob(any(HttpHeaders.class), any(Instruction.class));
    doNothing()
        .when(epcisService)
        .publishSerializedData(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            any(CompleteInstructionRequest.class),
            any(HttpHeaders.class));
    doReturn(MockRDSContainer.mockRdsContainer())
        .when(nimRdsServiceImpl)
        .acquireSlot(
            any(Instruction.class),
            nullable(CompleteInstructionRequest.class),
            any(HttpHeaders.class));
    /*doThrow(new ReceivingBadDataException("",""))
    .when(containerService)
    .getContainerIncludingChild(any(Container.class));*/
    doThrow(new NullPointerException())
        .when(containerService)
        .getContainerWithChildsByTrackingId(anyString(), any(Boolean.class));
    doReturn(new PrintLabelData())
        .when(containerLabelBuilder)
        .generateContainerLabel(any(), any(), any(), any(), any());
    doNothing()
        .when(rxInstructionHelperService)
        .persist(any(Container.class), any(Instruction.class), anyString());

    InstructionResponse completeInstruction =
        rxInstructionService.completeInstruction(
            mockInstructionFromDB.getId(), mockCompleteInstructionRequest, httpHeaders);

    assertNotNull(completeInstruction);

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(nimRdsServiceImpl, times(1))
        .acquireSlot(
            any(Instruction.class),
            nullable(CompleteInstructionRequest.class),
            any(HttpHeaders.class));
    verify(nimRdsServiceImpl, times(1))
        .quantityChange(any(Integer.class), any(String.class), any(HttpHeaders.class));
    verify(rxInstructionHelperService, times(0))
        .persist(any(Container.class), any(Instruction.class), anyString());
    verify(epcisService, times(1))
        .publishSerializedData(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            any(CompleteInstructionRequest.class),
            any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_completeInstruction_autoSlotting_rollbackRdsReceiptsForExceptionOneAtlas()
      throws ReceivingException {

    Container mockConsolidatedContainer = new Container();

    Instruction mockInstructionFromDB = MockInstruction.getInstruction();
    mockInstructionFromDB.setId(12345l);
    mockInstructionFromDB.setLastChangeUserId("sysadmin");
    mockInstructionFromDB.setCreateUserId("sysadmin");
    mockInstructionFromDB.setMove(new LinkedTreeMap<>());
    ContainerDetails childContainerDetails = new ContainerDetails();
    mockInstructionFromDB.setChildContainers(Arrays.asList(childContainerDetails));

    CompleteInstructionRequest mockCompleteInstructionRequest = new CompleteInstructionRequest();
    Map<String, Object> mockMap = new HashMap<>();
    mockMap.put("instruction", mockInstructionFromDB);
    mockMap.put("container", mockConsolidatedContainer);

    doReturn(true).when(configUtils).isPrintingAndroidComponentEnabled();
    doReturn(true).when(rxManagedConfig).isRollbackNimRdsReceiptsEnabled();
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag("32898", ENABLE_EPCIS_SERVICES_FEATURE_FLAG);

    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag("32898", IS_DC_ONE_ATLAS_ENABLED, false);
    doReturn(mockInstructionFromDB).when(instructionPersisterService).getInstructionById(anyLong());
    doReturn(mockMap)
        .when(instructionPersisterService)
        .completeAndCreatePrintJob(any(HttpHeaders.class), any(Instruction.class));
    doNothing()
        .when(epcisService)
        .publishSerializedData(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            any(CompleteInstructionRequest.class),
            any(HttpHeaders.class));
    doReturn(MockRDSContainer.mockRdsContainer())
        .when(nimRdsServiceImpl)
        .acquireSlot(
            any(Instruction.class),
            nullable(CompleteInstructionRequest.class),
            any(HttpHeaders.class));
    /*doThrow(new ReceivingBadDataException("",""))
    .when(containerService)
    .getContainerIncludingChild(any(Container.class));*/
    doThrow(new NullPointerException())
        .when(containerService)
        .getContainerWithChildsByTrackingId(anyString(), any(Boolean.class));
    doReturn(new PrintLabelData())
        .when(containerLabelBuilder)
        .generateContainerLabel(any(), any(), any(), any(), any());
    doNothing()
        .when(rxInstructionHelperService)
        .persist(any(Container.class), any(Instruction.class), anyString());

    InstructionResponse completeInstruction =
        rxInstructionService.completeInstruction(
            mockInstructionFromDB.getId(), mockCompleteInstructionRequest, httpHeaders);

    assertNotNull(completeInstruction);

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(nimRdsServiceImpl, times(1))
        .acquireSlot(
            any(Instruction.class),
            nullable(CompleteInstructionRequest.class),
            any(HttpHeaders.class));
    verify(nimRdsServiceImpl, times(0))
        .quantityChange(any(Integer.class), any(String.class), any(HttpHeaders.class));
    verify(rxInstructionHelperService, times(0))
        .persist(any(Container.class), any(Instruction.class), anyString());
    verify(epcisService, times(1))
        .publishSerializedData(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            any(CompleteInstructionRequest.class),
            any(HttpHeaders.class));
  }

  @Test
  public void test_cancelInstruction_rollbackReceiptsWithShipment() throws ReceivingException {

    Instruction mockInstruction = MockInstruction.getInstruction();

    LinkedTreeMap<String, Object> move = new LinkedTreeMap<>();
    move.put(ReceivingConstants.MOVE_FROM_LOCATION, "DUMMY_LOACTON");
    mockInstruction.setMove(move);
    HashMap<String, Object> containerMiscInfo = new HashMap<>();
    containerMiscInfo.put(RxConstants.SHIPMENT_DOCUMENT_ID, "shipmentDocId");

    Container container1 = new Container();
    container1.setTrackingId("a32612000000000001");
    List<ContainerItem> containerItems = new ArrayList<>();
    ContainerItem containerItem = new ContainerItem();
    containerItem.setQuantity(3);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem.setVnpkQty(3);
    containerItem.setWhpkQty(3);
    containerItems.add(containerItem);
    container1.setContainerMiscInfo(containerMiscInfo);
    container1.setContainerItems(containerItems);

    Container container2 = new Container();
    container2.setTrackingId("a32612000000000002");
    container2.setContainerMiscInfo(containerMiscInfo);
    container2.setContainerItems(containerItems);

    doReturn(mockInstruction).when(instructionPersisterService).getInstructionById(anyLong());
    doReturn(true).when(rxManagedConfig).isRollbackReceiptsByShipment();
    doNothing()
        .when(rxInstructionHelperService)
        .rollbackContainers(anyList(), any(List.class), any(Instruction.class));
    doNothing()
        .when(rxCancelInstructionReceiptsPublisher)
        .publishReceipt(any(Instruction.class), any(HttpHeaders.class));
    doNothing()
        .when(rxCancelInstructionReceiptsPublisher)
        .publishReceipt(any(Instruction.class), any(HttpHeaders.class));
    doReturn(Arrays.asList(container1, container2))
        .when(containerService)
        .getContainerByInstruction(anyLong());

    mockInstruction.setCreateUserId("sysadmin");
    InstructionSummary cancelInstrResponse =
        rxInstructionService.cancelInstruction(12345l, httpHeaders);

    assertNotNull(cancelInstrResponse);
    assertSame(cancelInstrResponse.getReceivedQuantity(), 0);
    assertNotNull(cancelInstrResponse.getCompleteTs());
    assertNotNull(cancelInstrResponse.getCompleteUserId());

    verify(rxReceiptsBuilder, times(0))
        .buildReceiptToRollbackInEaches(any(Instruction.class), anyString(), anyInt(), anyInt());
    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(rxInstructionHelperService, times(1))
        .rollbackContainers(anyList(), any(List.class), any(Instruction.class));
  }

  @Test
  public void test_cancelInstruction_existing_reciepts() throws ReceivingException {

    Instruction mockInstruction = MockInstruction.getInstruction();
    mockInstruction.setInstructionCode(
        RxInstructionType.SERIALIZED_CASES_SCAN.getInstructionType());
    mockInstruction.setReceivedQuantity(1);

    LinkedTreeMap<String, Object> move = new LinkedTreeMap<>();
    move.put(ReceivingConstants.MOVE_FROM_LOCATION, "DUMMY_LOACTON");
    mockInstruction.setMove(move);

    ContainerDetails containerDetails1 = new ContainerDetails();
    containerDetails1.setTrackingId("CHILD_TRACKING_ID");
    containerDetails1.setParentTrackingId("a32612000000000001");
    List<Content> contents1 = new ArrayList<>();
    Content content1 = new Content();
    content1.setLot("11111");
    content1.setVendorPack(1);
    content1.setRotateDate("2020-12-12");
    content1.setQtyUom(ReceivingConstants.Uom.EACHES);
    content1.setQty(1);
    contents1.add(content1);

    containerDetails1.setContents(contents1);
    mockInstruction.setChildContainers(Arrays.asList(containerDetails1));

    Container container1 = new Container();
    container1.setTrackingId("a32612000000000001");
    List<ContainerItem> containerItems = new ArrayList<>();
    ContainerItem containerItem = new ContainerItem();
    containerItem.setQuantity(3);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem.setVnpkQty(3);
    containerItem.setWhpkQty(3);
    containerItems.add(containerItem);
    container1.setContainerItems(containerItems);

    Container container2 = new Container();
    container2.setTrackingId("CHILD_TRACKING_ID");
    container2.setParentTrackingId("a32612000000000001");

    ArgumentCaptor<Receipt> receiptCaptor = ArgumentCaptor.forClass(Receipt.class);
    doReturn(mockInstruction).when(instructionPersisterService).getInstructionById(anyLong());
    doReturn(Arrays.asList(container1, container2))
        .when(containerService)
        .getContainerByInstruction(anyLong());

    ArgumentCaptor<ArrayList> trackingIdListCaptor = ArgumentCaptor.forClass(ArrayList.class);
    doNothing()
        .when(rxInstructionHelperService)
        .rollbackContainers(
            trackingIdListCaptor.capture(), receiptCaptor.capture(), any(Instruction.class));
    doNothing()
        .when(rxCancelInstructionReceiptsPublisher)
        .publishReceipt(any(Instruction.class), any(HttpHeaders.class));
    doNothing()
        .when(rxCancelInstructionReceiptsPublisher)
        .publishReceipt(any(Instruction.class), any(HttpHeaders.class));

    mockInstruction.setCreateUserId("sysadmin");
    InstructionSummary cancelInstrResponse =
        rxInstructionService.cancelInstruction(12345l, httpHeaders);

    assertNotNull(cancelInstrResponse);
    assertSame(cancelInstrResponse.getReceivedQuantity(), 0);
    assertNotNull(cancelInstrResponse.getCompleteTs());
    assertNotNull(cancelInstrResponse.getCompleteUserId());

    assertSame(receiptCaptor.getValue().getQuantity(), -1);
    assertSame(receiptCaptor.getValue().getEachQty(), -3);

    assertEquals(
        trackingIdListCaptor.getValue(), Arrays.asList("a32612000000000001", "CHILD_TRACKING_ID"));

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(rxReceiptsBuilder, times(1))
        .buildReceiptToRollbackInEaches(any(Instruction.class), anyString(), anyInt(), anyInt());
    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(rxInstructionHelperService, times(1))
        .rollbackContainers(
            trackingIdListCaptor.capture(), receiptCaptor.capture(), any(Instruction.class));
  }

  @Test
  public void test_cancelInstruction_existing_reciepts_Upc() throws ReceivingException {

    Instruction mockInstruction = MockInstruction.getInstruction();
    mockInstruction.setReceivedQuantity(1);

    LinkedTreeMap<String, Object> move = new LinkedTreeMap<>();
    move.put(ReceivingConstants.MOVE_FROM_LOCATION, "DUMMY_LOACTON");
    mockInstruction.setMove(move);

    ContainerDetails containerDetails1 = new ContainerDetails();
    containerDetails1.setTrackingId("CHILD_TRACKING_ID");
    containerDetails1.setParentTrackingId("a32612000000000001");
    List<Content> contents1 = new ArrayList<>();
    Content content1 = new Content();
    content1.setLot("11111");
    content1.setVendorPack(1);
    content1.setRotateDate("2020-12-12");
    content1.setQtyUom(ReceivingConstants.Uom.EACHES);
    content1.setQty(1);
    contents1.add(content1);

    containerDetails1.setContents(contents1);
    mockInstruction.setChildContainers(Arrays.asList(containerDetails1));

    ArgumentCaptor<Receipt> receiptCaptor = ArgumentCaptor.forClass(Receipt.class);
    doReturn(new Receipt()).when(receiptService).saveReceipt(receiptCaptor.capture());
    doReturn(mockInstruction).when(instructionPersisterService).getInstructionById(anyLong());
    doAnswer(invocation -> invocation.getArgument(0))
        .when(instructionRepository)
        .save(any(Instruction.class));

    Container container1 = new Container();
    container1.setTrackingId("a32612000000000001");
    List<ContainerItem> containerItems = new ArrayList<>();
    ContainerItem containerItem = new ContainerItem();
    containerItem.setQuantity(3);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem.setVnpkQty(3);
    containerItem.setWhpkQty(3);
    containerItems.add(containerItem);
    container1.setContainerItems(containerItems);

    Container container2 = new Container();
    container2.setTrackingId("CHILD_TRACKING_ID");

    doReturn(Arrays.asList(container1, container2))
        .when(containerService)
        .getContainerByInstruction(anyLong());

    ArgumentCaptor<ArrayList> trackingIdListCaptor = ArgumentCaptor.forClass(ArrayList.class);
    doReturn(mockInstruction).when(instructionPersisterService).getInstructionById(anyLong());
    doNothing()
        .when(rxInstructionHelperService)
        .rollbackContainers(
            trackingIdListCaptor.capture(), receiptCaptor.capture(), any(Instruction.class));
    doNothing()
        .when(rxCancelInstructionReceiptsPublisher)
        .publishReceipt(any(Instruction.class), any(HttpHeaders.class));
    doNothing()
        .when(rxCancelInstructionReceiptsPublisher)
        .publishReceipt(any(Instruction.class), any(HttpHeaders.class));

    mockInstruction.setCreateUserId("sysadmin");
    InstructionSummary cancelInstrResponse =
        rxInstructionService.cancelInstruction(12345l, httpHeaders);

    assertNotNull(cancelInstrResponse);
    assertSame(cancelInstrResponse.getReceivedQuantity(), 0);
    assertNotNull(cancelInstrResponse.getCompleteTs());
    assertNotNull(cancelInstrResponse.getCompleteUserId());

    assertSame(receiptCaptor.getValue().getQuantity(), -1);
    assertSame(receiptCaptor.getValue().getEachQty(), -3);

    assertEquals(
        trackingIdListCaptor.getValue(), Arrays.asList("a32612000000000001", "CHILD_TRACKING_ID"));

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(rxInstructionHelperService, times(1))
        .rollbackContainers(anyList(), any(Receipt.class), any(Instruction.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_cancelInstruction_exceptions() throws ReceivingException {

    Instruction mockInstruction = MockInstruction.getInstruction();

    ArgumentCaptor<Receipt> receiptCaptor = ArgumentCaptor.forClass(Receipt.class);
    doReturn(new Receipt()).when(receiptService).saveReceipt(receiptCaptor.capture());
    doReturn(mockInstruction).when(instructionPersisterService).getInstructionById(anyLong());
    doAnswer(invocation -> invocation.getArgument(0))
        .when(instructionRepository)
        .save(any(Instruction.class));

    ArgumentCaptor<ArrayList> trackingIdListCaptor = ArgumentCaptor.forClass(ArrayList.class);
    doThrow(new NullPointerException())
        .when(containerService)
        .deleteContainersByTrackingIds(trackingIdListCaptor.capture());
    Receipt receipt4mDB = new Receipt();
    receipt4mDB.setQuantity(2);
    receipt4mDB.setQuantityUom("ZA");
    receipt4mDB.setEachQty(6);
    receipt4mDB.setVnpkQty(3);
    receipt4mDB.setWhpkQty(3);

    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));

    mockInstruction.setCreateUserId("sysadmin");
    InstructionSummary cancelInstrResponse =
        rxInstructionService.cancelInstruction(12345l, httpHeaders);

    assertNotNull(cancelInstrResponse);
    assertSame(cancelInstrResponse.getReceivedQuantity(), 0);
    assertNotNull(cancelInstrResponse.getCompleteTs());
    assertNotNull(cancelInstrResponse.getCompleteUserId());

    assertSame(receiptCaptor.getValue().getQuantity(), 0);
    assertSame(receiptCaptor.getValue().getEachQty(), 0);

    assertEquals(trackingIdListCaptor.getValue(), Arrays.asList("a32612000000000001"));

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(instructionRepository, times(1)).save(any(Instruction.class));
    verify(containerService, times(1)).deleteContainersByTrackingIds(any(ArrayList.class));
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));
    verify(receiptService, times(1)).saveReceipt(any(Receipt.class));
  }

  @Test
  public void test_cancelInstruction_exceptions_completed_instruction() throws ReceivingException {

    try {
      Instruction mockInstruction = MockInstruction.getInstruction();
      mockInstruction.setCompleteTs(new Date());
      doReturn(mockInstruction).when(instructionPersisterService).getInstructionById(anyLong());

      rxInstructionService.cancelInstruction(12345l, httpHeaders);

    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.INSTRUCITON_IS_ALREADY_COMPLETED);
      assertEquals(e.getDescription(), ReceivingException.INSTRUCTION_IS_ALREADY_COMPLETED);
    }
  }

  @Test
  public void test_serveInstructionRequest_existing_open_instruction_bysscc() throws Exception {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
    instructionRequest.setDeliveryNumber("2356895623");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setSscc("00123456789562356895656");
    instructionRequest.setDoorNumber("V6949");

    ScannedData ssccScannedData = new ScannedData();
    ssccScannedData.setKey(ApplicationIdentifier.SSCC.getKey());
    ssccScannedData.setValue("001234567897984653");
    instructionRequest.setScannedDataList(Arrays.asList(ssccScannedData));

    File resource = new ClassPathResource("GdmMappedResponseV2.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doReturn(twoDBarcodeScanTypeDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            "32898",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(rxDeliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class)));
    doReturn(0L)
        .when(receiptService)
        .findReceiptsSummaryByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndSSCC(
            anyLong(), anyString(), anyInt(), anyString());

    doReturn(new Pair<>(50, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber()),
            eq(false),
            eq(false));

    doReturn(MockInstruction.getInstructionWithManufactureDetails())
        .when(rxInstructionPersisterService)
        .fetchInstructionByDeliveryNumberAndSSCCAndUserId(
            any(InstructionRequest.class), anyString());
    doAnswer(
            (Answer<Pair>)
                invocation -> {
                  List<DeliveryDocument> deliveryDocuments =
                      (List<DeliveryDocument>) invocation.getArguments()[0];

                  return new Pair<>(deliveryDocuments.get(0), 0);
                })
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    InstructionResponse serveInstructionResponse =
        rxInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    assertNotNull(serveInstructionResponse);
    assertEquals(serveInstructionResponse.getDeliveryDocuments().size(), 1);
    assertEquals(
        serveInstructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(),
        1);
    assertEquals(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getManufactureDetails()
            .size(),
        1);
    assertNotNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPalletSSCC());
    assertNotNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPackSSCC());

    verify(rxInstructionPersisterService, times(1))
        .fetchInstructionByDeliveryNumberAndSSCCAndUserId(
            any(InstructionRequest.class), anyString());
  }

  @Test
  public void test_serveInstructionRequest_Problem() throws Exception {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
    instructionRequest.setDeliveryNumber("2356895623");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setSscc("00123456789562356895656");
    instructionRequest.setDoorNumber("V6949");
    instructionRequest.setProblemTagId("06001647754402");

    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData ssccScannedData = new ScannedData();
    ssccScannedData.setKey(ApplicationIdentifier.SSCC.getKey());
    ssccScannedData.setValue("001234567897984653");
    scannedDataList.add(ssccScannedData);

    instructionRequest.setScannedDataList(scannedDataList);
    instructionRequest.setReceivingType(RxReceivingType.SSCC.getReceivingType());

    File resource = new ClassPathResource("GdmMappedResponseV2.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    doReturn("MOCK_REPACKAGING_VENDOR1,MOCK_REPACKAGING_VENDOR1")
        .when(appConfig)
        .getRepackageVendors();
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doReturn(Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class)))
        .when(rxDeliveryService)
        .findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class));
    doReturn(true).when(appConfig).isAttachLatestShipments();
    doReturn(Optional.of(Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class))))
        .when(rxDeliveryService)
        .findDeliveryDocumentBySSCCWithLatestShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class));
    doReturn(0L)
        .when(receiptService)
        .findReceiptsSummaryByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndSSCC(
            anyLong(), anyString(), anyInt(), anyString());
    doReturn(twoDBarcodeScanTypeDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            "32898",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    doReturn(new Pair<>(50, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsInEaAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber()));

    doReturn("MOCK_RX_DEFAULT_DOOR").when(appConfig).getRxProblemDefaultDoor();
    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) {
                List<DeliveryDocument> deliveryDocuments =
                    (List<DeliveryDocument>) invocation.getArguments()[0];

                return new Pair<>(deliveryDocuments.get(0), 0);
              }
            })
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());
    doReturn(mockFitProblemTagResponse())
        .when(rxInstructionHelperService)
        .getFitProblemTagResponse(anyString());
    InstructionResponse serveInstructionResponse =
        rxInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    assertNotNull(serveInstructionResponse);
    assertEquals(serveInstructionResponse.getDeliveryDocuments().size(), 1);
    assertEquals(
        serveInstructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(),
        1);
    assertEquals(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getManufactureDetails()
            .size(),
        1);
    assertNotNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPalletSSCC());
    assertNotNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPackSSCC());

    assertEquals(
        serveInstructionResponse
            .getInstruction()
            .getMove()
            .get(ReceivingConstants.MOVE_FROM_LOCATION),
        "MOCK_RX_DEFAULT_DOOR");

    verify(appConfig, times(1)).getRxProblemDefaultDoor();
    verify(instructionHelperService, times(1))
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());
  }

  @Test
  public void test_serveInstructionRequest_dsda_flag_missing() throws Exception {
    try {
      InstructionRequest instructionRequest = new InstructionRequest();
      instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
      instructionRequest.setDeliveryNumber("2356895623");
      instructionRequest.setDeliveryStatus("WRK");
      instructionRequest.setSscc("00123456789562356895656");
      instructionRequest.setDoorNumber("V6949");

      List<ScannedData> scannedDataList = new ArrayList<>();
      ScannedData ssccScannedData = new ScannedData();
      ssccScannedData.setKey(ApplicationIdentifier.SSCC.getKey());
      ssccScannedData.setValue("001234567897984653");
      scannedDataList.add(ssccScannedData);

      instructionRequest.setScannedDataList(scannedDataList);
      instructionRequest.setReceivingType(RxReceivingType.SSCC.getReceivingType());

      File resource = new ClassPathResource("GdmMappedResponseV2_no_dscsa_ind.json").getFile();
      String mockResponse = new String(Files.readAllBytes(resource.toPath()));
      doReturn("MOCK_REPACKAGING_VENDOR1,MOCK_REPACKAGING_VENDOR1")
          .when(appConfig)
          .getRepackageVendors();
      doReturn(twoDBarcodeScanTypeDocumentsSearchHandler)
          .when(tenantSpecificConfigReader)
          .getConfiguredInstance(
              "32898",
              ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
              DeliveryDocumentsSearchHandler.class);
      when(instructionPersisterService.saveInstruction(any(Instruction.class)))
          .thenAnswer(i -> i.getArguments()[0]);
      when(rxDeliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
              anyString(), anyString(), any(HttpHeaders.class)))
          .thenReturn(Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class)));
      doReturn(0L)
          .when(receiptService)
          .findReceiptsSummaryByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndSSCC(
              anyLong(), anyString(), anyInt(), anyString());

      doReturn(new Pair<>(50, 0L))
          .when(instructionHelperService)
          .getReceivedQtyDetailsInEaAndValidate(
              eq(instructionRequest.getProblemTagId()),
              any(),
              eq(instructionRequest.getDeliveryNumber()));

      doAnswer(
              new Answer<Pair>() {
                public Pair answer(InvocationOnMock invocation) {
                  List<DeliveryDocument> deliveryDocuments =
                      (List<DeliveryDocument>) invocation.getArguments()[0];

                  return new Pair<>(deliveryDocuments.get(0), 0);
                }
              })
          .when(instructionHelperService)
          .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

      InstructionResponse serveInstructionResponse =
          rxInstructionService.serveInstructionRequest(
              gson.toJson(instructionRequest), httpHeaders);
    } catch (ReceivingBadDataException e) {

      assertEquals(e.getErrorCode(), ExceptionCodes.INVALID_GDM_DSDA_IND_VALUE);
      assertEquals(e.getDescription(), RxConstants.INVALID_GDM_DSDA_IND_VALUE);

    } catch (ReceivingException e) {
      fail(e.getMessage(), e);
    } catch (Exception e) {
      fail(e.getMessage(), e);
    }
  }

  @Test
  public void test_serveInstructionRequest_D40_sscc_receiving_not_allowed() throws Exception {
    try {
      InstructionRequest instructionRequest = new InstructionRequest();
      instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
      instructionRequest.setDeliveryNumber("2356895623");
      instructionRequest.setDeliveryStatus("WRK");
      instructionRequest.setSscc("00123456789562356895656");
      instructionRequest.setDoorNumber("V6949");

      List<ScannedData> scannedDataList = new ArrayList<>();
      ScannedData ssccScannedData = new ScannedData();
      ssccScannedData.setKey(ApplicationIdentifier.SSCC.getKey());
      ssccScannedData.setValue("001234567897984653");
      scannedDataList.add(ssccScannedData);

      instructionRequest.setScannedDataList(scannedDataList);
      instructionRequest.setReceivingType(RxReceivingType.SSCC.toString());

      File resource = new ClassPathResource("GdmMappedResponseV2_D40_items.json").getFile();
      String mockResponse = new String(Files.readAllBytes(resource.toPath()));
      doReturn(twoDBarcodeScanTypeDocumentsSearchHandler)
          .when(tenantSpecificConfigReader)
          .getConfiguredInstance(
              "32898",
              ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
              DeliveryDocumentsSearchHandler.class);
      when(rxDeliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
              anyString(), anyString(), any(HttpHeaders.class)))
          .thenReturn(Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class)));
      doAnswer(
              (Answer<Pair>)
                  invocation -> {
                    List<DeliveryDocument> deliveryDocuments =
                        (List<DeliveryDocument>) invocation.getArguments()[0];

                    return new Pair<>(deliveryDocuments.get(0), 0);
                  })
          .when(instructionHelperService)
          .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

      InstructionResponse serveInstructionResponse =
          rxInstructionService.serveInstructionRequest(
              gson.toJson(instructionRequest), httpHeaders);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.INVLID_D40_RECEIVING_FLOW);
      assertEquals(e.getDescription(), ReceivingException.INVLID_D40_RECEIVING_FLOW_DESC);

    } catch (ReceivingException e) {
      fail(e.getMessage(), e);
    } catch (Exception e) {
      fail(e.getMessage(), e);
    }
  }

  @Test
  public void test_serveInstructionRequest_D38_StoreTransferPo_sscc_not_allowed() throws Exception {
    try {
      InstructionRequest instructionRequest = new InstructionRequest();
      instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
      instructionRequest.setDeliveryNumber("2356895623");
      instructionRequest.setDeliveryStatus("WRK");
      instructionRequest.setSscc("00123456789562356895656");
      instructionRequest.setDoorNumber("V6949");

      List<ScannedData> scannedDataList = new ArrayList<>();
      ScannedData ssccScannedData = new ScannedData();
      ssccScannedData.setKey(ApplicationIdentifier.SSCC.getKey());
      ssccScannedData.setValue("001234567897984653");
      scannedDataList.add(ssccScannedData);

      instructionRequest.setScannedDataList(scannedDataList);
      instructionRequest.setReceivingType(RxReceivingType.SSCC.toString());

      File resource = new ClassPathResource("GdmResponseV2_D38_StoreTransfer.json").getFile();
      String mockResponse = new String(Files.readAllBytes(resource.toPath()));
      doReturn(twoDBarcodeScanTypeDocumentsSearchHandler)
          .when(tenantSpecificConfigReader)
          .getConfiguredInstance(
              "32898",
              ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
              DeliveryDocumentsSearchHandler.class);
      when(rxDeliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
              anyString(), anyString(), any(HttpHeaders.class)))
          .thenReturn(Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class)));
      doAnswer(
              new Answer<Pair>() {
                public Pair answer(InvocationOnMock invocation) {
                  List<DeliveryDocument> deliveryDocuments =
                      (List<DeliveryDocument>) invocation.getArguments()[0];

                  return new Pair<>(deliveryDocuments.get(0), 0);
                }
              })
          .when(instructionHelperService)
          .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

      InstructionResponse serveInstructionResponse =
          rxInstructionService.serveInstructionRequest(
              gson.toJson(instructionRequest), httpHeaders);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.INVLID_D40_RECEIVING_FLOW);
      assertEquals(e.getDescription(), ReceivingException.INVLID_D40_RECEIVING_FLOW_DESC);

    } catch (ReceivingException e) {
      fail(e.getMessage(), e);
    } catch (Exception e) {
      fail(e.getMessage(), e);
    }
  }

  @Test
  public void test_completeInstruction_autoSlotting_problem_receiving() throws ReceivingException {

    Container mockConsolidatedContainer = new Container();

    Instruction mockInstructionFromDB = MockInstruction.getInstruction();
    mockInstructionFromDB.setId(12345l);
    mockInstructionFromDB.setLastChangeUserId("sysadmin");
    mockInstructionFromDB.setCreateUserId("sysadmin");
    mockInstructionFromDB.setMove(new LinkedTreeMap<>());
    ContainerDetails childContainerDetails = new ContainerDetails();
    mockInstructionFromDB.setChildContainers(Arrays.asList(childContainerDetails));
    mockInstructionFromDB.setProblemTagId("MOCK_PROBLEM_TAG_ID");
    mockInstructionFromDB.setInstructionCode(
        RxInstructionType.RX_SER_BUILD_CONTAINER.getInstructionType());

    CompleteInstructionRequest mockCompleteInstructionRequest = new CompleteInstructionRequest();
    Map<String, Object> mockMap = new HashMap<>();
    mockMap.put("instruction", mockInstructionFromDB);
    mockMap.put("container", mockConsolidatedContainer);

    doReturn(true).when(configUtils).isPrintingAndroidComponentEnabled();
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag("32898", ENABLE_EPCIS_SERVICES_FEATURE_FLAG);
    doReturn(mockInstructionFromDB).when(instructionPersisterService).getInstructionById(anyLong());
    doReturn(mockMap)
        .when(instructionPersisterService)
        .completeAndCreatePrintJob(any(HttpHeaders.class), any(Instruction.class));
    doNothing()
        .when(epcisService)
        .publishSerializedData(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            any(CompleteInstructionRequest.class),
            any(HttpHeaders.class));
    doReturn(MockRDSContainer.mockRdsContainer())
        .when(nimRdsServiceImpl)
        .acquireSlot(
            any(Instruction.class), any(CompleteInstructionRequest.class), any(HttpHeaders.class));
    doReturn(mockConsolidatedContainer)
        .when(containerService)
        .getContainerIncludingChild(any(Container.class));
    doReturn(getParentContainerWithChilds())
        .when(containerService)
        .getContainerWithChildsByTrackingId(anyString(), any(Boolean.class));

    doReturn(problemService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));

    ProblemLabel problemLabel = new ProblemLabel();
    problemLabel.setResolutionId("MOCK_RESOLUTION_ID");

    doReturn(problemLabel).when(problemService).findProblemLabelByProblemTagId(anyString());
    doReturn(new PrintLabelData())
        .when(containerLabelBuilder)
        .generateContainerLabel(any(), any(), any(), any(), any());
    doNothing()
        .when(rxInstructionHelperService)
        .persist(any(Container.class), any(Instruction.class), anyString());

    doNothing()
        .when(rxFixitProblemService)
        .completeProblem(
            any(Instruction.class), any(HttpHeaders.class), any(DeliveryDocumentLine.class));

    // mocks for publishDeliveryStatus
    doReturn(Collections.emptyList())
        .when(receiptService)
        .getReceivedQtySummaryByPOForDelivery(anyLong(), any());
    JsonObject deliveryResponseJson = new JsonObject();
    deliveryResponseJson.addProperty("deliveryStatus", "ARV");
    doReturn(deliveryResponseJson.toString())
        .when(deliveryService)
        .getDeliveryByDeliveryNumber(anyLong(), any());
    doReturn(new DeliveryInfo())
        .when(deliveryStatusPublisher)
        .publishDeliveryStatus(anyLong(), anyString(), anyList(), anyMap());

    InstructionResponse completeInstruction =
        rxInstructionService.completeInstruction(
            mockInstructionFromDB.getId(), mockCompleteInstructionRequest, httpHeaders);

    assertNotNull(completeInstruction);

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(nimRdsServiceImpl, times(1))
        .acquireSlot(
            any(Instruction.class), any(CompleteInstructionRequest.class), any(HttpHeaders.class));
    verify(rxInstructionHelperService, times(1))
        .persist(any(Container.class), any(Instruction.class), anyString());
    verify(epcisService, times(1))
        .publishSerializedData(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            any(CompleteInstructionRequest.class),
            any(HttpHeaders.class));
    verify(rxSlottingServiceImpl, times(0))
        .acquireSlot(
            anyString(),
            anyList(),
            anyInt(),
            eq(ReceivingConstants.SLOTTING_FIND_SLOT),
            any(HttpHeaders.class));

    verify(rxFixitProblemService, times(1))
        .completeProblem(
            any(Instruction.class), any(HttpHeaders.class), any(DeliveryDocumentLine.class));

    // mocks for publishDeliveryStatus
    verify(receiptService, times(1)).getReceivedQtySummaryByPOForDelivery(anyLong(), any());
    verify(deliveryService, times(1)).getDeliveryByDeliveryNumber(anyLong(), any());
    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(anyLong(), anyString(), anyList(), anyMap());
  }

  @Test
  public void test_serveInstructionRequest_Dept40() throws Exception {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
    instructionRequest.setDeliveryNumber("2356895623");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setUpcNumber("00301691837020");
    instructionRequest.setDoorNumber("V6949");
    ScannedData scannedData = new ScannedData();
    scannedData.setKey("BARCODE_SCAN");
    scannedData.setValue("00301691837020");
    List<ScannedData> scannedDataList = new ArrayList<>();
    scannedDataList.add(scannedData);
    instructionRequest.setScannedDataList(scannedDataList);

    File resource = new ClassPathResource("gdm_Upc_response_D40.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    when(instructionPersisterService.saveInstruction(instructionCaptor.capture()))
        .thenAnswer(i -> i.getArguments()[0]);
    doReturn(twoDBarcodeScanTypeDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            "32898",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(rxDeliveryService.findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(mockResponse);
    when(configUtils.getConfiguredFeatureFlag("32898", RxConstants.ENABLE_DEPT_CHECK_FEATURE_FLAG))
        .thenReturn(false);
    doReturn(new Pair<>(50, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsInEaAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber()));
    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) {
                List<DeliveryDocument> deliveryDocuments =
                    (List<DeliveryDocument>) invocation.getArguments()[0];

                return new Pair<>(deliveryDocuments.get(0), 0);
              }
            })
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    InstructionResponse serveInstructionResponse =
        rxInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    assertNotNull(serveInstructionResponse);
    assertEquals(serveInstructionResponse.getDeliveryDocuments().size(), 1);
    assertEquals(
        serveInstructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(),
        1);
    assertNotNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getVendorStockNumber());
    assertEquals(serveInstructionResponse.getInstruction().getInstructionCode(), "Build Container");
    assertEquals(serveInstructionResponse.getInstruction().getInstructionMsg(), "Build Container");
    verify(instructionHelperService, times(1))
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    assertEquals(instructionCaptor.getValue().getGtin(), instructionRequest.getUpcNumber());
  }

  @Test
  public void test_serveInstructionRequest_Dept40_PartialCase() throws Exception {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
    instructionRequest.setDeliveryNumber("2356895623");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setUpcNumber("00301691837020");
    instructionRequest.setDoorNumber("V6949");
    ScannedData scannedData = new ScannedData();
    scannedData.setKey("BARCODE_SCAN");
    scannedData.setValue("00301691837020");
    List<ScannedData> scannedDataList = new ArrayList<>();
    scannedDataList.add(scannedData);
    instructionRequest.setScannedDataList(scannedDataList);
    instructionRequest.setReceivingType(RxReceivingType.UPC_PARTIALS.getReceivingType());

    File resource = new ClassPathResource("gdm_Upc_response_D40.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    when(instructionPersisterService.saveInstruction(instructionCaptor.capture()))
        .thenAnswer(i -> i.getArguments()[0]);
    when(rxDeliveryService.findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(mockResponse);
    doReturn(twoDBarcodeScanTypeDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            "32898",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(configUtils.getConfiguredFeatureFlag("32898", RxConstants.ENABLE_DEPT_CHECK_FEATURE_FLAG))
        .thenReturn(false);
    doReturn(new Pair<>(50, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsInEaAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber()));
    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) {
                List<DeliveryDocument> deliveryDocuments =
                    (List<DeliveryDocument>) invocation.getArguments()[0];

                return new Pair<>(deliveryDocuments.get(0), 0);
              }
            })
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    InstructionResponse serveInstructionResponse =
        rxInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    assertNotNull(serveInstructionResponse);
    assertEquals(serveInstructionResponse.getDeliveryDocuments().size(), 1);
    assertEquals(
        serveInstructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(),
        1);
    assertNotNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getVendorStockNumber());
    assertEquals(
        serveInstructionResponse.getInstruction().getInstructionCode(),
        BUILD_PARTIAL_CONTAINER_UPC_RECEIVING.getInstructionType());
    assertEquals(
        serveInstructionResponse.getInstruction().getInstructionMsg(),
        BUILD_PARTIAL_CONTAINER_UPC_RECEIVING.getInstructionMsg());
    verify(instructionHelperService, times(1))
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    assertEquals(instructionCaptor.getValue().getGtin(), instructionRequest.getUpcNumber());
  }

  @Test
  public void test_serveInstructionRequest_Dept40_PartialCase_ExistingInstruction()
      throws Exception {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
    instructionRequest.setDeliveryNumber("2356895623");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setUpcNumber("00301691837020");
    instructionRequest.setDoorNumber("V6949");
    ScannedData scannedData = new ScannedData();
    scannedData.setKey("BARCODE_SCAN");
    scannedData.setValue("00301691837020");
    List<ScannedData> scannedDataList = new ArrayList<>();
    scannedDataList.add(scannedData);
    instructionRequest.setScannedDataList(scannedDataList);
    instructionRequest.setReceivingType(RxReceivingType.UPC_PARTIALS.getReceivingType());

    File resource = new ClassPathResource("gdm_Upc_response_D40.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    when(instructionPersisterService.saveInstruction(instructionCaptor.capture()))
        .thenAnswer(i -> i.getArguments()[0]);
    when(rxDeliveryService.findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(mockResponse);
    when(configUtils.getConfiguredFeatureFlag("32898", RxConstants.ENABLE_DEPT_CHECK_FEATURE_FLAG))
        .thenReturn(false);
    doReturn(MockInstruction.getInstruction())
        .when(rxInstructionHelperService)
        .checkIfInstructionExistsBeforeAllowingPartialReceiving(
            anyString(), anyString(), anyString());
    doReturn(twoDBarcodeScanTypeDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            "32898",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    doReturn(new Pair<>(50, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsInEaAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber()));
    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) {
                List<DeliveryDocument> deliveryDocuments =
                    (List<DeliveryDocument>) invocation.getArguments()[0];

                return new Pair<>(deliveryDocuments.get(0), 0);
              }
            })
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    InstructionResponse serveInstructionResponse =
        rxInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    assertNotNull(serveInstructionResponse);
    assertEquals(serveInstructionResponse.getDeliveryDocuments().size(), 1);
    assertEquals(
        serveInstructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(),
        1);
    assertNotNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getVendorStockNumber());
  }

  @Test
  public void test_serveInstructionRequest_Dept40_FeatureFlag() throws Exception {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
    instructionRequest.setDeliveryNumber("2356895623");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setUpcNumber("00301691837020");
    instructionRequest.setDoorNumber("V6949");
    ScannedData scannedData = new ScannedData();
    scannedData.setKey("BARCODE_SCAN");
    scannedData.setValue("00301691837020");
    List<ScannedData> scannedDataList = new ArrayList<>();
    scannedDataList.add(scannedData);
    instructionRequest.setScannedDataList(scannedDataList);

    File resource = new ClassPathResource("gdm_Upc_response_D40.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    when(rxDeliveryService.findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(mockResponse);
    doReturn(twoDBarcodeScanTypeDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            "32898",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(configUtils.getConfiguredFeatureFlag("32898", RxConstants.ENABLE_DEPT_CHECK_FEATURE_FLAG))
        .thenReturn(true);
    doReturn(new Pair<>(50, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsInEaAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber()));
    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) {
                List<DeliveryDocument> deliveryDocuments =
                    (List<DeliveryDocument>) invocation.getArguments()[0];

                return new Pair<>(deliveryDocuments.get(0), 0);
              }
            })
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    InstructionResponse serveInstructionResponse =
        rxInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    assertNotNull(serveInstructionResponse);
    assertEquals(serveInstructionResponse.getDeliveryDocuments().size(), 1);
    assertEquals(
        serveInstructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(),
        1);
    assertNotNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getVendorStockNumber());
    assertEquals(serveInstructionResponse.getInstruction().getInstructionCode(), "Build Container");
    assertEquals(serveInstructionResponse.getInstruction().getInstructionMsg(), "Build Container");
    verify(instructionHelperService, times(1))
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());
  }

  @Test
  public void test_serveInstructionRequest_Dept40_empty_ScannedDataList() throws Exception {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
    instructionRequest.setDeliveryNumber("2356895623");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setUpcNumber("00301691837020");
    instructionRequest.setDoorNumber("V6949");
    ScannedData scannedData = new ScannedData();
    scannedData.setKey("BARCODE_SCAN");
    scannedData.setValue("00301691837020");
    List<ScannedData> scannedDataList = new ArrayList<>();
    scannedDataList.add(scannedData);
    scannedDataList.add(scannedData);
    instructionRequest.setScannedDataList(scannedDataList);

    File resource = new ClassPathResource("gdm_Upc_response_D40.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    when(rxDeliveryService.findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(mockResponse);
    doReturn(twoDBarcodeScanTypeDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            "32898",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    doReturn(new Pair<>(50, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsInEaAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber()));
    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) {
                List<DeliveryDocument> deliveryDocuments =
                    (List<DeliveryDocument>) invocation.getArguments()[0];

                return new Pair<>(deliveryDocuments.get(0), 0);
              }
            })
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    InstructionResponse serveInstructionResponse =
        rxInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    assertNotNull(serveInstructionResponse);
    assertEquals(serveInstructionResponse.getDeliveryDocuments().size(), 1);
    assertEquals(
        serveInstructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(),
        1);
    assertNotNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getVendorStockNumber());
    assertEquals(serveInstructionResponse.getInstruction().getInstructionCode(), "Build Container");
    assertEquals(serveInstructionResponse.getInstruction().getInstructionMsg(), "Build Container");
    verify(instructionHelperService, times(1))
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());
  }

  @Test
  public void test_serveInstructionRequest_StoreTransferPo_Upc_Allow() throws Exception {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
    instructionRequest.setDeliveryNumber("2356895623");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setUpcNumber("00301691837020");
    instructionRequest.setDoorNumber("V6949");
    ScannedData scannedData = new ScannedData();
    scannedData.setKey("BARCODE_SCAN");
    scannedData.setValue("00301691837020");
    List<ScannedData> scannedDataList = new ArrayList<>();
    scannedDataList.add(scannedData);
    scannedDataList.add(scannedData);
    instructionRequest.setScannedDataList(scannedDataList);

    File resource = new ClassPathResource("gdm_Upc_response_store_transferPo.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doReturn(twoDBarcodeScanTypeDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            "32898",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(rxDeliveryService.findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(mockResponse);
    doReturn(new Pair<>(50, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsInEaAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber()));
    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) {
                List<DeliveryDocument> deliveryDocuments =
                    (List<DeliveryDocument>) invocation.getArguments()[0];

                return new Pair<>(deliveryDocuments.get(0), 0);
              }
            })
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    InstructionResponse serveInstructionResponse =
        rxInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    assertNotNull(serveInstructionResponse);
    assertEquals(serveInstructionResponse.getDeliveryDocuments().size(), 1);
    assertEquals(
        serveInstructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(),
        1);
    assertNotNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getVendorStockNumber());
    assertEquals(serveInstructionResponse.getInstruction().getInstructionCode(), "Build Container");
    assertEquals(serveInstructionResponse.getInstruction().getInstructionMsg(), "Build Container");
    verify(instructionHelperService, times(1))
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_serveInstructionRequest_BlockD38_UpcReceiving() throws Exception {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
    instructionRequest.setDeliveryNumber("2356895623");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setUpcNumber("00301691837020");
    instructionRequest.setDoorNumber("V6949");

    File resource = new ClassPathResource("gdm_Upc_response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doReturn(twoDBarcodeScanTypeDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            "32898",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(rxDeliveryService.findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(mockResponse);
    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) {
                List<DeliveryDocument> deliveryDocuments =
                    (List<DeliveryDocument>) invocation.getArguments()[0];

                return new Pair<>(deliveryDocuments.get(0), 0);
              }
            })
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    InstructionResponse serveInstructionResponse =
        rxInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    assertNotNull(serveInstructionResponse);
    assertEquals(serveInstructionResponse.getDeliveryDocuments().size(), 1);
    assertEquals(
        serveInstructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(),
        1);
    assertNotNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getVendorStockNumber());
    assertEquals(serveInstructionResponse.getInstruction().getInstructionCode(), "Build Container");
    assertEquals(serveInstructionResponse.getInstruction().getInstructionMsg(), "Build Container");

    verify(instructionHelperService, times(1))
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());
  }

  @Test
  public void test_serveInstructionRequest_GrandFatheredProblemReceiving() throws Exception {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
    instructionRequest.setDeliveryNumber("2356895623");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setUpcNumber("00301691837020");
    instructionRequest.setDoorNumber("V6949");
    instructionRequest.setProblemTagId("0094526568695");
    Map<String, Object> additionalParams = new HashMap<>();
    additionalParams.put(ReceivingConstants.PROBLEM_RESOLUTION_KEY, "GRANDFATHERED");
    instructionRequest.setAdditionalParams(additionalParams);
    ScannedData scannedData = new ScannedData();
    scannedData.setKey("BARCODE_SCAN");
    scannedData.setValue("00301691837020");
    List<ScannedData> scannedDataList = new ArrayList<>();
    scannedDataList.add(scannedData);
    scannedDataList.add(scannedData);
    instructionRequest.setScannedDataList(scannedDataList);

    File resource = new ClassPathResource("gdm_Upc_response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    when(rxDeliveryService.findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(mockResponse);
    doReturn(twoDBarcodeScanTypeDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            "32898",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    doReturn(new Pair<>(50, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsInEaAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber()));
    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) {
                List<DeliveryDocument> deliveryDocuments =
                    (List<DeliveryDocument>) invocation.getArguments()[0];

                return new Pair<>(deliveryDocuments.get(0), 0);
              }
            })
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());
    doReturn(mockFitProblemTagResponse())
        .when(rxInstructionHelperService)
        .getFitProblemTagResponse(anyString());
    InstructionResponse serveInstructionResponse =
        rxInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    assertNotNull(serveInstructionResponse);
    assertEquals(serveInstructionResponse.getDeliveryDocuments().size(), 1);
    assertEquals(
        serveInstructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(),
        1);
    assertNotNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getVendorStockNumber());
    assertEquals(serveInstructionResponse.getInstruction().getInstructionCode(), "Build Container");
    assertEquals(serveInstructionResponse.getInstruction().getInstructionMsg(), "Build Container");
    verify(instructionHelperService, times(1))
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());
  }

  @Test
  public void testServeInstructionRequestReturnsExistingOpenInstructionFor2dBarcodeScan()
      throws Exception {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequestFor2dBarcodeScan();

    Pair<DeliveryDocument, Long> autoSelectedDocument =
        new Pair<DeliveryDocument, Long>(instructionRequest.getDeliveryDocuments().get(0), 0L);

    doReturn(autoSelectedDocument)
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    doReturn(MockInstruction.getInstructionWithManufactureDetails())
        .when(rxInstructionPersisterService)
        .fetchInstructionByDeliveryAndGtinAndLotNumberAndUserId(
            any(InstructionRequest.class), anyString());

    Instruction instructionResponse =
        rxInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
    assertNotNull(instructionResponse);

    verify(rxInstructionPersisterService, times(1))
        .fetchInstructionByDeliveryAndGtinAndLotNumberAndUserId(
            any(InstructionRequest.class), anyString());
  }

  @Test
  public void testServeInstructionRequestReturnsNewInstructionFor2dBarcodeScan() throws Exception {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequestFor2dBarcodeScan();

    Pair<DeliveryDocument, Long> autoSelectedDocument =
        new Pair<DeliveryDocument, Long>(instructionRequest.getDeliveryDocuments().get(0), 0L);
    doReturn("MOCK_REPACKAGING_VENDOR1,MOCK_REPACKAGING_VENDOR1")
        .when(appConfig)
        .getRepackageVendors();
    doReturn(autoSelectedDocument)
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    doReturn(null)
        .when(rxInstructionPersisterService)
        .fetchInstructionByDeliveryAndGtinAndLotNumberAndUserId(
            any(InstructionRequest.class), anyString());
    doReturn(0L)
        .when(receiptService)
        .findReceiptsSummaryByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndSSCC(
            anyLong(), anyString(), anyInt(), anyString());

    doReturn(new Pair<>(50, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsInEaAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber()));

    doReturn(getRxInstructionFor2dBarcodeReceiving())
        .when(instructionPersisterService)
        .saveInstruction(any(Instruction.class));

    Instruction instructionResponse =
        rxInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);

    assertNotNull(instructionResponse);

    Map<String, ScannedData> scannedDataMap =
        RxUtils.scannedDataMap(instructionRequest.getScannedDataList());
    String gtin = scannedDataMap.get(ApplicationIdentifier.GTIN.getKey()).getValue();
    assertTrue(instructionResponse.getGtin().equalsIgnoreCase(gtin));

    assertTrue(
        instructionResponse
            .getInstructionMsg()
            .equalsIgnoreCase(
                RxInstructionType.BUILDCONTAINER_SCAN_BY_GTIN_LOT.getInstructionMsg()));
    assertTrue(
        instructionResponse
            .getInstructionCode()
            .equalsIgnoreCase(
                RxInstructionType.BUILDCONTAINER_SCAN_BY_GTIN_LOT.getInstructionType()));

    verify(rxInstructionPersisterService, times(1))
        .fetchInstructionByDeliveryAndGtinAndLotNumberAndUserId(
            any(InstructionRequest.class), anyString());
  }

  @Test
  public void
      testServeInstructionRequestReturnsNewInstructionFor2dBarcodeScan_CloseDatedItems_Problemreceive()
          throws Exception {
    doReturn(true).when(rxManagedConfig).isProblemItemCheckEnabled();
    InstructionRequest instructionRequest =
        MockInstruction.getInstructionRequestFor2dBarcodeScan_CloseDatedItem();
    instructionRequest.setProblemTagId("problemTagId");

    Pair<DeliveryDocument, Long> autoSelectedDocument =
        new Pair<DeliveryDocument, Long>(instructionRequest.getDeliveryDocuments().get(0), 0L);
    doReturn(autoSelectedDocument)
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    doReturn(null)
        .when(rxInstructionPersisterService)
        .fetchInstructionByDeliveryAndGtinAndLotNumberAndUserId(
            any(InstructionRequest.class), anyString());
    doReturn(0L)
        .when(receiptService)
        .findReceiptsSummaryByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndSSCC(
            anyLong(), anyString(), anyInt(), anyString());

    doReturn(new Pair<>(50, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsInEaAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber()));
    doReturn("MOCK_REPACKAGING_VENDOR1,MOCK_REPACKAGING_VENDOR1")
        .when(appConfig)
        .getRepackageVendors();
    doReturn(true).when(appConfig).isCloseDateCheckEnabled();
    doReturn(problemService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    ProblemLabel problemLabel = new ProblemLabel();
    problemLabel.setResolutionId("MOCK_RESOLUTION_ID");
    problemLabel.setProblemResponse(
        "{\"id\":\"98339bce-42f6-414a-be41-e40e78edb56c\",\"status\":\"OPEN\",\"label\":\"06001304645906\",\"slot\":\"M4032\",\"reportedQty\":5,\"remainingQty\":5,\"issue\":{\"id\":\"43f8a5df-c1b5-4018-9a99-567b3f2b41f0\",\"identifier\":\"210408-69961-6638-0000\",\"type\":\"DI\",\"subType\":\"ASN_ISSUE\",\"uom\":\"ZA\",\"status\":\"ANSWERED\",\"businessStatus\":\"READY_TO_RECEIVE\",\"resolutionStatus\":\"COMPLETE_RESOLUTON\",\"upc\":\"10350742190017\",\"itemNumber\":550129241,\"deliveryNumber\":\"78869668\",\"quantity\":5},\"resolutions\":[{\"id\":\"917dbbb0-8bd7-4667-82af-89068f2bc2e5\",\"state\":\"OPEN\",\"type\":\"RECEIVE_AGAINST_ORIGINAL_LINE\",\"provider\":\"Manual\",\"quantity\":5,\"remainingQty\":5,\"acceptedQuantity\":0,\"rejectedQuantity\":0,\"resolutionPoNbr\":\"7702953583\",\"resolutionPoLineNbr\":1}]}");
    doReturn(problemLabel).when(problemService).findProblemLabelByProblemTagId(anyString());

    doReturn(getRxInstructionFor2dBarcodeReceiving())
        .when(instructionPersisterService)
        .saveInstruction(any(Instruction.class));
    Instruction instructionResponse =
        rxInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
    verify(instructionPersisterService, times(1))
        .fetchInstructionByDeliveryAndGtinAndLotNumberAndUserIdAndProblemTagId(
            anyLong(), anyMap(), anyString(), anyString());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void
      testServeInstructionRequestReturnsNewInstructionFor2dBarcodeScan_CloseDated_Problemreceive_ScanDiffItem()
          throws Exception {
    doReturn(true).when(rxManagedConfig).isProblemItemCheckEnabled();

    InstructionRequest instructionRequest =
        MockInstruction.getInstructionRequestFor2dBarcodeScan_CloseDatedItem();
    instructionRequest.setProblemTagId("problemTagId");

    Pair<DeliveryDocument, Long> autoSelectedDocument =
        new Pair<DeliveryDocument, Long>(instructionRequest.getDeliveryDocuments().get(0), 0L);
    doReturn(autoSelectedDocument)
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    doReturn(null)
        .when(rxInstructionPersisterService)
        .fetchInstructionByDeliveryAndGtinAndLotNumberAndUserId(
            any(InstructionRequest.class), anyString());
    doReturn(0L)
        .when(receiptService)
        .findReceiptsSummaryByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndSSCC(
            anyLong(), anyString(), anyInt(), anyString());

    doReturn(new Pair<>(50, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber()),
            eq(false),
            eq(false));
    doReturn("MOCK_REPACKAGING_VENDOR1,MOCK_REPACKAGING_VENDOR1")
        .when(appConfig)
        .getRepackageVendors();
    doReturn(true).when(appConfig).isCloseDateCheckEnabled();
    doReturn(problemService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    ProblemLabel problemLabel = new ProblemLabel();
    problemLabel.setResolutionId("MOCK_RESOLUTION_ID");
    problemLabel.setProblemResponse(
        "{\"id\":\"98339bce-42f6-414a-be41-e40e78edb56c\",\"status\":\"OPEN\",\"label\":\"06001304645906\",\"slot\":\"M4032\",\"reportedQty\":5,\"remainingQty\":5,\"issue\":{\"id\":\"43f8a5df-c1b5-4018-9a99-567b3f2b41f0\",\"identifier\":\"210408-69961-6638-0000\",\"type\":\"DI\",\"subType\":\"ASN_ISSUE\",\"uom\":\"ZA\",\"status\":\"ANSWERED\",\"businessStatus\":\"READY_TO_RECEIVE\",\"resolutionStatus\":\"COMPLETE_RESOLUTON\",\"upc\":\"10350742190017\",\"itemNumber\":550129245,\"deliveryNumber\":\"78869668\",\"quantity\":5},\"resolutions\":[{\"id\":\"917dbbb0-8bd7-4667-82af-89068f2bc2e5\",\"state\":\"OPEN\",\"type\":\"RECEIVE_AGAINST_ORIGINAL_LINE\",\"provider\":\"Manual\",\"quantity\":5,\"remainingQty\":5,\"acceptedQuantity\":0,\"rejectedQuantity\":0,\"resolutionPoNbr\":\"7702953583\",\"resolutionPoLineNbr\":1}]}");
    doReturn(problemLabel).when(problemService).findProblemLabelByProblemTagId(anyString());

    doReturn(getRxInstructionFor2dBarcodeReceiving())
        .when(instructionPersisterService)
        .saveInstruction(any(Instruction.class));
    Instruction instructionResponse =
        rxInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
    verify(instructionPersisterService, times(1))
        .fetchInstructionByDeliveryAndGtinAndLotNumberAndUserIdAndProblemTagId(
            anyLong(), anyMap(), anyString(), anyString());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testServeInstructionRequestReturnsNewInstructionFor2dBarcodeScan_CloseDatedItems()
      throws Exception {
    doReturn(true).when(rxManagedConfig).isProblemItemCheckEnabled();

    InstructionRequest instructionRequest =
        MockInstruction.getInstructionRequestFor2dBarcodeScan_CloseDatedItem();

    Pair<DeliveryDocument, Long> autoSelectedDocument =
        new Pair<DeliveryDocument, Long>(instructionRequest.getDeliveryDocuments().get(0), 0L);
    doReturn(autoSelectedDocument)
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    doReturn(null)
        .when(rxInstructionPersisterService)
        .fetchInstructionByDeliveryAndGtinAndLotNumberAndUserId(
            any(InstructionRequest.class), anyString());
    doReturn(0L)
        .when(receiptService)
        .findReceiptsSummaryByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndSSCC(
            anyLong(), anyString(), anyInt(), anyString());

    doReturn(new Pair<>(50, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber()),
            eq(false),
            eq(false));
    doReturn("MOCK_REPACKAGING_VENDOR1,MOCK_REPACKAGING_VENDOR1")
        .when(appConfig)
        .getRepackageVendors();
    doReturn(true).when(appConfig).isCloseDateCheckEnabled();
    doReturn(getRxInstructionFor2dBarcodeReceiving())
        .when(instructionPersisterService)
        .saveInstruction(any(Instruction.class));
    Instruction instructionResponse =
        rxInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
    verify(rxInstructionPersisterService, times(1))
        .fetchInstructionByDeliveryAndGtinAndLotNumberAndUserId(
            any(InstructionRequest.class), anyString());
  }

  @Test
  public void testPatchInstructionReturnsUpdatedInstruction() throws ReceivingException {
    Long instructionId = 123L;
    PatchInstructionRequest patchInstructionRequest = new PatchInstructionRequest("mockUpc");

    doReturn(MockInstruction.getInstructionWithManufactureDetails())
        .when(instructionPersisterService)
        .getInstructionById(anyLong());

    doReturn(MockInstruction.getPatchedRxInstruction())
        .when(instructionPersisterService)
        .saveInstruction(any());

    Instruction instructionResponse =
        rxInstructionService.patchInstruction(instructionId, patchInstructionRequest, httpHeaders);
    assertNotNull(instructionResponse);
    DeliveryDocument deliveryDocument =
        gson.fromJson(instructionResponse.getDeliveryDocument(), DeliveryDocument.class);
    assertTrue(instructionResponse.getGtin().equalsIgnoreCase("mockUpc"));
    assertTrue(
        deliveryDocument
            .getDeliveryDocumentLines()
            .get(0)
            .getCatalogGTIN()
            .equalsIgnoreCase("mockUpc"));
    Optional<GtinHierarchy> catalogGtin =
        deliveryDocument
            .getDeliveryDocumentLines()
            .get(0)
            .getGtinHierarchy()
            .stream()
            .filter(x -> x.getType().equalsIgnoreCase(ReceivingConstants.ITEM_CATALOG_GTIN))
            .findFirst();
    assertTrue(catalogGtin.isPresent());
    assertTrue(catalogGtin.get().getGtin().equalsIgnoreCase("mockUpc"));
  }

  @Test
  public void testPatchInstructionReturnsAnErrorForInvalidInstructionId()
      throws ReceivingException {
    Long instructionId = 123L;
    PatchInstructionRequest patchInstructionRequest = new PatchInstructionRequest("mockUpc");

    doThrow(
            new ReceivingException(
                ReceivingException.INSTRUCTION_NOT_FOUND, HttpStatus.INTERNAL_SERVER_ERROR))
        .when(instructionPersisterService)
        .getInstructionById(anyLong());

    try {
      Instruction instructionResponse =
          rxInstructionService.patchInstruction(
              instructionId, patchInstructionRequest, httpHeaders);
    } catch (ReceivingBadDataException error) {
      assertNotNull(error);
    }
  }

  @Test
  public void testPatchInstructionReturnsAnErrorForCompletedInstruction()
      throws ReceivingException {
    Long instructionId = 123L;
    PatchInstructionRequest patchInstructionRequest = new PatchInstructionRequest("mockUpc");
    Instruction instruction = MockInstruction.getPatchedRxInstruction();
    instruction.setCompleteTs(new Date());

    doReturn(MockInstruction.getInstructionWithManufactureDetails())
        .when(instructionPersisterService)
        .getInstructionById(anyLong());

    try {
      Instruction instructionResponse =
          rxInstructionService.patchInstruction(
              instructionId, patchInstructionRequest, httpHeaders);
    } catch (ReceivingBadDataException error) {
      assertNotNull(error);
    }
  }

  @Test
  public void testServeInstructionRequestReturnsExistingOpenInstructionForD40Receiving()
      throws Exception {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequestForD40Receiving();

    Pair<DeliveryDocument, Long> autoSelectedDocument =
        new Pair<DeliveryDocument, Long>(instructionRequest.getDeliveryDocuments().get(0), 0L);

    doReturn(autoSelectedDocument)
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    when(rxInstructionPersisterService
            .fetchInstructionByDeliveryAndGtinAndUserIdOrLastChangeUserIdAndProblemTagIdIsNull(
                instructionRequest, httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY)))
        .thenReturn(MockInstruction.getOpenInstruction());

    Instruction instructionResponse =
        rxInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);

    assertNotNull(instructionResponse);

    assertTrue(
        instructionResponse
            .getInstructionMsg()
            .equalsIgnoreCase(
                RxInstructionType.BUILD_CONTAINER_FOR_UPC_RECEIVING.getInstructionMsg()));
    assertTrue(
        instructionResponse
            .getInstructionCode()
            .equalsIgnoreCase(
                RxInstructionType.BUILD_CONTAINER_FOR_UPC_RECEIVING.getInstructionType()));

    verify(rxInstructionPersisterService, times(1))
        .fetchInstructionByDeliveryAndGtinAndUserIdOrLastChangeUserIdAndProblemTagIdIsNull(
            any(), anyString());
  }

  public Instruction getRxInstructionFor2dBarcodeReceiving() {
    Instruction instruction = MockInstruction.getInstructionWithManufactureDetailsAndPrimeSlot();
    instruction.setGtin("00028000114603");
    instruction.setInstructionMsg(
        RxInstructionType.BUILDCONTAINER_SCAN_BY_GTIN_LOT.getInstructionMsg());
    instruction.setInstructionCode(
        RxInstructionType.BUILDCONTAINER_SCAN_BY_GTIN_LOT.getInstructionType());
    return instruction;
  }

  public Instruction getRxPartialInstructionFor2dBarcodeReceiving() {
    Instruction instruction = MockInstruction.getInstructionWithManufactureDetailsAndPrimeSlot();
    instruction.setGtin("1111111111111111");
    instruction.setInstructionMsg(RxInstructionType.BUILD_PARTIAL_CONTAINER.getInstructionMsg());
    instruction.setInstructionCode(RxInstructionType.BUILD_PARTIAL_CONTAINER.getInstructionType());
    instruction.setCreateUserId("sysadmin");
    instruction.setLastChangeUserId("sysadmin");
    return instruction;
  }

  public Instruction getRxPartialInstructionFor2dBarcodeReceiving_CaseUpc() {
    Instruction instruction = MockInstruction.getInstructionWithManufactureDetailsAndPrimeSlot();
    instruction.setGtin("00028000114603");
    instruction.setInstructionMsg(RxInstructionType.BUILD_PARTIAL_CONTAINER.getInstructionMsg());
    instruction.setInstructionCode(RxInstructionType.BUILD_PARTIAL_CONTAINER.getInstructionType());
    instruction.setCreateUserId("sysadmin");
    instruction.setLastChangeUserId("sysadmin");
    return instruction;
  }

  public Instruction getRxInstructionFor2dBarcodeReceivingSmartPrimeSlot() {
    Instruction instruction = MockInstruction.getInstructionWithManufactureDetailsAndPrimeSlot();
    instruction.setGtin("00028000114603");
    instruction.setInstructionMsg(
        RxInstructionType.BUILDCONTAINER_SCAN_BY_GTIN_LOT.getInstructionMsg());
    instruction.setInstructionCode(
        RxInstructionType.BUILDCONTAINER_SCAN_BY_GTIN_LOT.getInstructionType());
    return instruction;
  }

  @Test
  public void test_completeInstruction_autoSlotting_smartSlotting() throws ReceivingException {

    Container mockConsolidatedContainer = new Container();

    Instruction mockInstructionFromDB = MockInstruction.getInstruction();
    mockInstructionFromDB.setId(12345l);
    mockInstructionFromDB.setLastChangeUserId("sysadmin");
    mockInstructionFromDB.setCreateUserId("sysadmin");
    mockInstructionFromDB.setMove(new LinkedTreeMap<>());
    ContainerDetails childContainerDetails = new ContainerDetails();
    mockInstructionFromDB.setChildContainers(Arrays.asList(childContainerDetails));
    mockInstructionFromDB.setInstructionCode(
        RxInstructionType.RX_SER_CNTR_CASE_SCAN.getInstructionType());

    CompleteInstructionRequest mockCompleteInstructionRequest = new CompleteInstructionRequest();
    Map<String, Object> mockMap = new HashMap<>();
    mockMap.put("instruction", mockInstructionFromDB);
    mockMap.put("container", mockConsolidatedContainer);

    doReturn(true).when(configUtils).isPrintingAndroidComponentEnabled();
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag("32898", ENABLE_EPCIS_SERVICES_FEATURE_FLAG);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32898", SMART_SLOTING_RX_FEATURE_FLAG);

    doReturn(mockInstructionFromDB).when(instructionPersisterService).getInstructionById(anyLong());
    doNothing()
        .when(epcisService)
        .publishSerializedData(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            any(CompleteInstructionRequest.class),
            any(HttpHeaders.class));
    doReturn(MockRDSContainer.mockRdsContainer())
        .when(nimRdsServiceImpl)
        .acquireSlot(
            any(Instruction.class),
            nullable(CompleteInstructionRequest.class),
            any(HttpHeaders.class));
    doReturn(mockConsolidatedContainer)
        .when(containerService)
        .getContainerIncludingChild(any(Container.class));
    doReturn(getParentContainerWithChilds())
        .when(containerService)
        .getContainerWithChildsByTrackingId(anyString(), any(Boolean.class));
    doReturn(MockInstruction.mockSmartSlotting())
        .when(rxSlottingServiceImpl)
        .acquireSlot(
            anyString(),
            anyList(),
            anyInt(),
            eq(ReceivingConstants.SLOTTING_FIND_SLOT),
            any(HttpHeaders.class));
    doReturn(new PrintLabelData())
        .when(containerLabelBuilder)
        .generateContainerLabel(any(), any(), any(), any(), any());
    doNothing()
        .when(rxInstructionHelperService)
        .persist(any(Container.class), any(Instruction.class), anyString());

    InstructionResponse completeInstruction =
        rxInstructionService.completeInstruction(
            mockInstructionFromDB.getId(), mockCompleteInstructionRequest, httpHeaders);

    assertNotNull(completeInstruction);

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(nimRdsServiceImpl, times(1))
        .acquireSlot(
            any(Instruction.class),
            nullable(CompleteInstructionRequest.class),
            any(HttpHeaders.class));
    verify(rxInstructionHelperService, times(1))
        .persist(any(Container.class), any(Instruction.class), anyString());
    verify(epcisService, times(1))
        .publishSerializedData(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            any(CompleteInstructionRequest.class),
            any(HttpHeaders.class));
    verify(rxSlottingServiceImpl, times(1))
        .acquireSlot(
            anyString(),
            anyList(),
            anyInt(),
            eq(ReceivingConstants.SLOTTING_FIND_SLOT),
            isNull(),
            isNull(),
            isNull(),
            eq("00673419302784"),
            isNull(),
            isNull(),
            any(HttpHeaders.class));
  }

  @Test
  public void test_completeInstruction_autoSlotting_smartSlotting_oneAtlas()
      throws ReceivingException {

    Container mockConsolidatedContainer = new Container();

    Instruction mockInstructionFromDB = MockInstruction.getInstruction();
    mockInstructionFromDB.setId(12345l);
    mockInstructionFromDB.setLastChangeUserId("sysadmin");
    mockInstructionFromDB.setCreateUserId("sysadmin");
    mockInstructionFromDB.setMove(new LinkedTreeMap<>());
    ContainerDetails childContainerDetails = new ContainerDetails();
    mockInstructionFromDB.setChildContainers(Arrays.asList(childContainerDetails));
    mockInstructionFromDB.setInstructionCode(
        RxInstructionType.RX_SER_CNTR_CASE_SCAN.getInstructionType());

    CompleteInstructionRequest mockCompleteInstructionRequest = new CompleteInstructionRequest();
    Map<String, Object> mockMap = new HashMap<>();
    mockMap.put("instruction", mockInstructionFromDB);
    mockMap.put("container", mockConsolidatedContainer);

    doReturn(true).when(configUtils).isPrintingAndroidComponentEnabled();
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag("32898", ENABLE_EPCIS_SERVICES_FEATURE_FLAG);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32898", SMART_SLOTING_RX_FEATURE_FLAG);

    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag("32898", IS_DC_ONE_ATLAS_ENABLED, false);
    doReturn(Arrays.asList("B100000000004343989"))
        .when(rxLpnUtils)
        .get18DigitLPNs(eq(1), any(HttpHeaders.class));

    doReturn(mockInstructionFromDB).when(instructionPersisterService).getInstructionById(anyLong());
    doNothing()
        .when(epcisService)
        .publishSerializedData(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            any(CompleteInstructionRequest.class),
            any(HttpHeaders.class));

    doReturn(MockRDSContainer.mockRdsContainer())
        .when(nimRdsServiceImpl)
        .acquireSlot(
            any(Instruction.class),
            nullable(CompleteInstructionRequest.class),
            any(HttpHeaders.class));
    doReturn(mockConsolidatedContainer)
        .when(containerService)
        .getContainerIncludingChild(any(Container.class));
    doReturn(getParentContainerWithChilds())
        .when(containerService)
        .getContainerWithChildsByTrackingId(anyString(), any(Boolean.class));
    doReturn(MockInstruction.mockSmartSlotting())
        .when(rxSlottingServiceImpl)
        .acquireSlot(
            anyString(),
            anyList(),
            anyInt(),
            eq(ReceivingConstants.SLOTTING_FIND_SLOT),
            eq("B100000000004343989"),
            eq(2),
            eq(ReceivingConstants.Uom.VNPK),
            any(),
            any(),
            any(),
            any(HttpHeaders.class));
    doReturn(new PrintLabelData())
        .when(containerLabelBuilder)
        .generateContainerLabel(any(), any(), any(), any(), any());
    doNothing()
        .when(rxInstructionHelperService)
        .persist(any(Container.class), any(Instruction.class), anyString());

    InstructionResponse completeInstruction =
        rxInstructionService.completeInstruction(
            mockInstructionFromDB.getId(), mockCompleteInstructionRequest, httpHeaders);

    assertNotNull(completeInstruction);

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(rxInstructionHelperService, times(1))
        .persist(any(Container.class), any(Instruction.class), anyString());
    verify(epcisService, times(1))
        .publishSerializedData(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            any(CompleteInstructionRequest.class),
            any(HttpHeaders.class));
  }

  @Test
  public void test_completeInstruction_autoSlotting_partialCase() throws ReceivingException {

    Container mockConsolidatedContainer = new Container();

    Instruction mockInstructionFromDB = MockInstruction.getInstruction();
    mockInstructionFromDB.setId(12345l);
    mockInstructionFromDB.setLastChangeUserId("sysadmin");
    mockInstructionFromDB.setCreateUserId("sysadmin");
    mockInstructionFromDB.setMove(new LinkedTreeMap<>());
    ContainerDetails childContainerDetails = new ContainerDetails();
    mockInstructionFromDB.setChildContainers(Arrays.asList(childContainerDetails));
    mockInstructionFromDB.setInstructionCode(
        RxInstructionType.RX_SER_BUILD_UNITS_SCAN.getInstructionType());

    CompleteInstructionRequest mockCompleteInstructionRequest = new CompleteInstructionRequest();
    Map<String, Object> mockMap = new HashMap<>();
    mockMap.put("instruction", mockInstructionFromDB);
    mockMap.put("container", mockConsolidatedContainer);
    mockCompleteInstructionRequest.setPartialContainer(true);

    doReturn(true).when(configUtils).isPrintingAndroidComponentEnabled();
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag("32898", ENABLE_EPCIS_SERVICES_FEATURE_FLAG);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32898", SMART_SLOTING_RX_FEATURE_FLAG);

    doReturn(mockInstructionFromDB).when(instructionPersisterService).getInstructionById(anyLong());
    doReturn(mockMap)
        .when(instructionPersisterService)
        .completeAndCreatePrintJob(any(HttpHeaders.class), any(Instruction.class));
    doNothing()
        .when(epcisService)
        .publishSerializedData(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            any(CompleteInstructionRequest.class),
            any(HttpHeaders.class));
    doReturn(MockRDSContainer.mockRdsContainer())
        .when(nimRdsServiceImpl)
        .acquireSlot(
            any(Instruction.class),
            nullable(CompleteInstructionRequest.class),
            any(HttpHeaders.class));
    doReturn(mockConsolidatedContainer)
        .when(containerService)
        .getContainerIncludingChild(any(Container.class));
    doReturn(getParentContainerWithChilds())
        .when(containerService)
        .getContainerWithChildsByTrackingId(anyString(), any(Boolean.class));
    doReturn(new PrintLabelData())
        .when(containerLabelBuilder)
        .generateContainerLabel(any(), any(), any(), any(), any());
    doNothing()
        .when(rxInstructionHelperService)
        .persist(any(Container.class), any(Instruction.class), anyString());

    InstructionResponse completeInstruction =
        rxInstructionService.completeInstruction(
            mockInstructionFromDB.getId(), mockCompleteInstructionRequest, httpHeaders);

    assertNotNull(completeInstruction);

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(nimRdsServiceImpl, times(1))
        .acquireSlot(
            any(Instruction.class),
            nullable(CompleteInstructionRequest.class),
            any(HttpHeaders.class));
    verify(rxInstructionHelperService, times(1))
        .persist(any(Container.class), any(Instruction.class), anyString());
    verify(epcisService, times(1))
        .publishSerializedData(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            any(CompleteInstructionRequest.class),
            any(HttpHeaders.class));
    verify(rxSlottingServiceImpl, times(0))
        .acquireSlot(
            anyString(),
            anyList(),
            anyInt(),
            eq(ReceivingConstants.SLOTTING_FIND_SLOT),
            any(HttpHeaders.class));
  }

  @Test
  public void test_completeInstruction_autoSlotting_partialCaseOneAtlas()
      throws ReceivingException {

    Container mockConsolidatedContainer = new Container();

    Instruction mockInstructionFromDB = MockInstruction.getInstruction();
    mockInstructionFromDB.setId(12345l);
    mockInstructionFromDB.setLastChangeUserId("sysadmin");
    mockInstructionFromDB.setCreateUserId("sysadmin");
    mockInstructionFromDB.setMove(new LinkedTreeMap<>());
    ContainerDetails childContainerDetails = new ContainerDetails();
    mockInstructionFromDB.setChildContainers(Arrays.asList(childContainerDetails));
    mockInstructionFromDB.setInstructionCode(
        RxInstructionType.RX_SER_BUILD_UNITS_SCAN.getInstructionType());

    CompleteInstructionRequest mockCompleteInstructionRequest = new CompleteInstructionRequest();
    Map<String, Object> mockMap = new HashMap<>();
    mockMap.put("instruction", mockInstructionFromDB);
    mockMap.put("container", mockConsolidatedContainer);
    mockCompleteInstructionRequest.setPartialContainer(true);

    doReturn(true).when(configUtils).isPrintingAndroidComponentEnabled();
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag("32898", ENABLE_EPCIS_SERVICES_FEATURE_FLAG);
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag("32898", IS_DC_ONE_ATLAS_ENABLED, false);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32898", SMART_SLOTING_RX_FEATURE_FLAG);

    doReturn(mockInstructionFromDB).when(instructionPersisterService).getInstructionById(anyLong());
    doReturn(mockMap)
        .when(instructionPersisterService)
        .completeAndCreatePrintJob(any(HttpHeaders.class), any(Instruction.class));
    doNothing()
        .when(epcisService)
        .publishSerializedData(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            any(CompleteInstructionRequest.class),
            any(HttpHeaders.class));
    doReturn(Arrays.asList("B100000000004343989"))
        .when(rxLpnUtils)
        .get18DigitLPNs(eq(1), any(HttpHeaders.class));

    doReturn(MockRDSContainer.mockRdsContainer())
        .when(nimRdsServiceImpl)
        .acquireSlot(
            any(Instruction.class),
            nullable(CompleteInstructionRequest.class),
            any(HttpHeaders.class));
    doReturn(mockConsolidatedContainer)
        .when(containerService)
        .getContainerIncludingChild(any(Container.class));
    doReturn(getParentContainerWithChilds())
        .when(containerService)
        .getContainerWithChildsByTrackingId(anyString(), any(Boolean.class));
    doReturn(MockInstruction.mockSmartSlotting())
        .when(rxSlottingServiceImpl)
        .acquireSlot(
            anyString(),
            anyList(),
            anyInt(),
            eq(ReceivingConstants.SLOTTING_FIND_PRIME_SLOT),
            anyString(),
            eq(2),
            eq(ReceivingConstants.Uom.WHPK),
            anyString(),
            any(),
            any(),
            any(HttpHeaders.class));
    doReturn(new PrintLabelData())
        .when(containerLabelBuilder)
        .generateContainerLabel(any(), any(), any(), any(), any());
    doNothing()
        .when(rxInstructionHelperService)
        .persist(any(Container.class), any(Instruction.class), anyString());

    InstructionResponse completeInstruction =
        rxInstructionService.completeInstruction(
            mockInstructionFromDB.getId(), mockCompleteInstructionRequest, httpHeaders);

    assertNotNull(completeInstruction);

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(nimRdsServiceImpl, times(0))
        .acquireSlot(
            any(Instruction.class),
            nullable(CompleteInstructionRequest.class),
            any(HttpHeaders.class));
    verify(rxInstructionHelperService, times(1))
        .persist(any(Container.class), any(Instruction.class), anyString());
    verify(epcisService, times(1))
        .publishSerializedData(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            any(CompleteInstructionRequest.class),
            any(HttpHeaders.class));
  }

  @Test
  public void
      testServeInstructionRequestReturnsNewInstructionFor2dBarcodeScan_smartSlottingPrimeSlot()
          throws Exception {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequestFor2dBarcodeScan();

    Pair<DeliveryDocument, Long> autoSelectedDocument =
        new Pair<DeliveryDocument, Long>(instructionRequest.getDeliveryDocuments().get(0), 0L);

    doReturn(autoSelectedDocument)
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    doReturn(null)
        .when(rxInstructionPersisterService)
        .fetchInstructionByDeliveryAndGtinAndLotNumberAndUserId(
            any(InstructionRequest.class), anyString());
    doReturn(0L)
        .when(receiptService)
        .findReceiptsSummaryByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndSSCC(
            anyLong(), anyString(), anyInt(), anyString());

    doReturn(getRxInstructionFor2dBarcodeReceivingSmartPrimeSlot())
        .when(instructionPersisterService)
        .saveInstruction(any(Instruction.class));
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32898", SMART_SLOTING_RX_FEATURE_FLAG);
    doReturn(MockInstruction.mockSmartSlotting())
        .when(rxSlottingServiceImpl)
        .acquireSlot(
            anyString(),
            anyList(),
            anyInt(),
            eq(ReceivingConstants.SLOTTING_FIND_PRIME_SLOT),
            any(HttpHeaders.class));
    doReturn("MOCK_REPACKAGING_VENDOR1,MOCK_REPACKAGING_VENDOR1")
        .when(appConfig)
        .getRepackageVendors();

    doReturn(new Pair<>(50, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsInEaAndValidate(any(), any(), any());

    Instruction instructionResponse =
        rxInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);

    assertNotNull(instructionResponse);

    Map<String, ScannedData> scannedDataMap =
        RxUtils.scannedDataMap(instructionRequest.getScannedDataList());
    String gtin = scannedDataMap.get(ApplicationIdentifier.GTIN.getKey()).getValue();
    assertTrue(instructionResponse.getGtin().equalsIgnoreCase(gtin));

    assertTrue(
        instructionResponse
            .getInstructionMsg()
            .equalsIgnoreCase(
                RxInstructionType.BUILDCONTAINER_SCAN_BY_GTIN_LOT.getInstructionMsg()));
    assertTrue(
        instructionResponse
            .getInstructionCode()
            .equalsIgnoreCase(
                RxInstructionType.BUILDCONTAINER_SCAN_BY_GTIN_LOT.getInstructionType()));

    verify(rxInstructionPersisterService, times(1))
        .fetchInstructionByDeliveryAndGtinAndLotNumberAndUserId(
            any(InstructionRequest.class), anyString());
    verify(rxSlottingServiceImpl, times(1))
        .acquireSlot(
            anyString(),
            anyList(),
            anyInt(),
            eq(ReceivingConstants.SLOTTING_FIND_PRIME_SLOT),
            any(HttpHeaders.class));
  }

  @Test
  public void test_completeInstruction_autoSlotting_problem_receiving_smartSlotting()
      throws ReceivingException {

    Container mockConsolidatedContainer = new Container();

    Instruction mockInstructionFromDB = MockInstruction.getInstruction();
    mockInstructionFromDB.setId(12345l);
    mockInstructionFromDB.setLastChangeUserId("sysadmin");
    mockInstructionFromDB.setCreateUserId("sysadmin");
    mockInstructionFromDB.setMove(new LinkedTreeMap<>());
    ContainerDetails childContainerDetails = new ContainerDetails();
    mockInstructionFromDB.setChildContainers(Arrays.asList(childContainerDetails));
    mockInstructionFromDB.setProblemTagId("MOCK_PROBLEM_TAG_ID");

    CompleteInstructionRequest mockCompleteInstructionRequest = new CompleteInstructionRequest();
    Map<String, Object> mockMap = new HashMap<>();
    mockMap.put("instruction", mockInstructionFromDB);
    mockMap.put("container", mockConsolidatedContainer);

    doReturn(true).when(configUtils).isPrintingAndroidComponentEnabled();
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag("32898", ENABLE_EPCIS_SERVICES_FEATURE_FLAG);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32898", SMART_SLOTING_RX_FEATURE_FLAG);
    doReturn(mockInstructionFromDB).when(instructionPersisterService).getInstructionById(anyLong());
    doReturn(mockMap)
        .when(instructionPersisterService)
        .completeAndCreatePrintJob(any(HttpHeaders.class), any(Instruction.class));
    doNothing()
        .when(epcisService)
        .publishSerializedData(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            any(CompleteInstructionRequest.class),
            any(HttpHeaders.class));
    doReturn(MockRDSContainer.mockRdsContainer())
        .when(nimRdsServiceImpl)
        .acquireSlot(
            any(Instruction.class), any(CompleteInstructionRequest.class), any(HttpHeaders.class));
    doReturn(mockConsolidatedContainer)
        .when(containerService)
        .getContainerIncludingChild(any(Container.class));
    doReturn(getParentContainerWithChilds())
        .when(containerService)
        .getContainerWithChildsByTrackingId(anyString(), any(Boolean.class));

    doReturn(MockInstruction.mockSmartSlotting())
        .when(rxSlottingServiceImpl)
        .acquireSlot(
            anyString(),
            anyList(),
            anyInt(),
            eq(ReceivingConstants.SLOTTING_FIND_SLOT),
            any(HttpHeaders.class));
    doReturn(problemService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    doReturn(new PrintLabelData())
        .when(containerLabelBuilder)
        .generateContainerLabel(any(), any(), any(), any(), any());

    ProblemLabel problemLabel = new ProblemLabel();
    problemLabel.setResolutionId("MOCK_RESOLUTION_ID");

    doReturn(problemLabel).when(problemService).findProblemLabelByProblemTagId(anyString());
    doNothing()
        .when(rxInstructionHelperService)
        .persist(any(Container.class), any(Instruction.class), anyString());
    doNothing()
        .when(rxFixitProblemService)
        .completeProblem(
            any(Instruction.class), any(HttpHeaders.class), any(DeliveryDocumentLine.class));

    // mocks for publishDeliveryStatus
    doReturn(Collections.emptyList())
        .when(receiptService)
        .getReceivedQtySummaryByPOForDelivery(anyLong(), any());
    JsonObject deliveryResponseJson = new JsonObject();
    deliveryResponseJson.addProperty("deliveryStatus", "ARV");
    doReturn(deliveryResponseJson.toString())
        .when(deliveryService)
        .getDeliveryByDeliveryNumber(anyLong(), any());
    doReturn(new DeliveryInfo())
        .when(deliveryStatusPublisher)
        .publishDeliveryStatus(anyLong(), anyString(), anyList(), anyMap());

    InstructionResponse completeInstruction =
        rxInstructionService.completeInstruction(
            mockInstructionFromDB.getId(), mockCompleteInstructionRequest, httpHeaders);

    assertNotNull(completeInstruction);

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(nimRdsServiceImpl, times(1))
        .acquireSlot(
            any(Instruction.class), any(CompleteInstructionRequest.class), any(HttpHeaders.class));
    verify(rxInstructionHelperService, times(1))
        .persist(any(Container.class), any(Instruction.class), anyString());
    verify(epcisService, times(1))
        .publishSerializedData(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            any(CompleteInstructionRequest.class),
            any(HttpHeaders.class));
    verify(rxSlottingServiceImpl, times(1))
        .acquireSlot(
            anyString(),
            anyList(),
            anyInt(),
            eq(ReceivingConstants.SLOTTING_FIND_SLOT),
            isNull(),
            isNull(),
            isNull(),
            eq("00673419302784"),
            isNull(),
            isNull(),
            any(HttpHeaders.class));

    verify(rxFixitProblemService, times(1))
        .completeProblem(
            any(Instruction.class), any(HttpHeaders.class), any(DeliveryDocumentLine.class));

    // mocks for publishDeliveryStatus
    verify(receiptService, times(1)).getReceivedQtySummaryByPOForDelivery(anyLong(), any());
    verify(deliveryService, times(1)).getDeliveryByDeliveryNumber(anyLong(), any());
    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(anyLong(), anyString(), anyList(), anyMap());
  }

  @Test
  public void test_serveInstructionRequest_repackaged_vendor_sscc_receiving_not_allowed()
      throws Exception {
    try {
      InstructionRequest instructionRequest = new InstructionRequest();
      instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
      instructionRequest.setDeliveryNumber("2356895623");
      instructionRequest.setDeliveryStatus("WRK");
      instructionRequest.setSscc("00123456789562356895656");
      instructionRequest.setDoorNumber("V6949");
      instructionRequest.setReceivingType(RxReceivingType.SSCC.toString());

      List<ScannedData> scannedDataList = new ArrayList<>();
      ScannedData ssccScannedData = new ScannedData();
      ssccScannedData.setKey(ApplicationIdentifier.SSCC.getKey());
      ssccScannedData.setValue("001234567897984653");
      scannedDataList.add(ssccScannedData);

      instructionRequest.setScannedDataList(scannedDataList);

      File resource = new ClassPathResource("gdm_sscc_response_repackaged_vendors.json").getFile();
      String mockResponse = new String(Files.readAllBytes(resource.toPath()));

      doReturn("53717").when(appConfig).getRepackageVendors();
      doReturn(twoDBarcodeScanTypeDocumentsSearchHandler)
          .when(tenantSpecificConfigReader)
          .getConfiguredInstance(
              "32898",
              ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
              DeliveryDocumentsSearchHandler.class);
      when(rxDeliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
              anyString(), anyString(), any(HttpHeaders.class)))
          .thenReturn(Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class)));
      doAnswer(
              (Answer<Pair>)
                  invocation -> {
                    List<DeliveryDocument> deliveryDocuments =
                        (List<DeliveryDocument>) invocation.getArguments()[0];

                    return new Pair<>(deliveryDocuments.get(0), 0);
                  })
          .when(instructionHelperService)
          .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

      InstructionResponse serveInstructionResponse =
          rxInstructionService.serveInstructionRequest(
              gson.toJson(instructionRequest), httpHeaders);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.INVLID_D40_RECEIVING_FLOW);
      assertEquals(e.getDescription(), ReceivingException.INVLID_D40_RECEIVING_FLOW_DESC);

    } catch (ReceivingException e) {
      fail(e.getMessage(), e);
    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage(), e);
    }
  }

  @Test
  public void test_serveInstructionRequest_repackaged_vendors_Upc_Allow() throws Exception {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
    instructionRequest.setDeliveryNumber("2356895623");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setUpcNumber("00301691837020");
    instructionRequest.setDoorNumber("V6949");
    ScannedData scannedData = new ScannedData();
    scannedData.setKey("BARCODE_SCAN");
    scannedData.setValue("00301691837020");
    List<ScannedData> scannedDataList = new ArrayList<>();
    scannedDataList.add(scannedData);
    scannedDataList.add(scannedData);
    instructionRequest.setScannedDataList(scannedDataList);

    File resource = new ClassPathResource("gdm_Upc_response_repackaged_vendors.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    doReturn("645184").when(appConfig).getRepackageVendors();
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doReturn(twoDBarcodeScanTypeDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            "32898",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(rxDeliveryService.findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(mockResponse);

    doReturn(new Pair<>(50, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsInEaAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber()));
    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) {
                List<DeliveryDocument> deliveryDocuments =
                    (List<DeliveryDocument>) invocation.getArguments()[0];

                return new Pair<>(deliveryDocuments.get(0), 0);
              }
            })
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    InstructionResponse serveInstructionResponse =
        rxInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    assertNotNull(serveInstructionResponse);
    assertEquals(serveInstructionResponse.getDeliveryDocuments().size(), 1);
    assertEquals(
        serveInstructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(),
        1);
    assertNotNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getVendorStockNumber());
    assertEquals(serveInstructionResponse.getInstruction().getInstructionCode(), "Build Container");
    assertEquals(serveInstructionResponse.getInstruction().getInstructionMsg(), "Build Container");
    verify(instructionHelperService, times(1))
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());
  }

  @Test
  public void test_serveInstructionRequest_cancel_po() {

    try {
      when(configUtils.getConfiguredFeatureFlag(
              "32898", ReceivingConstants.ALLOW_SINGLE_ITEM_MULTI_PO_LINE))
          .thenReturn(true);
      when(appConfig.isFilteringInvalidposEnabled()).thenReturn(true);

      InstructionRequest instructionRequest = new InstructionRequest();
      instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
      instructionRequest.setDeliveryNumber("2356895623");
      instructionRequest.setDeliveryStatus("WRK");
      instructionRequest.setSscc("00123456789562356895656");
      instructionRequest.setDoorNumber("V6949");

      List<ScannedData> scannedDataList = new ArrayList<>();
      ScannedData ssccScannedData = new ScannedData();
      ssccScannedData.setKey(ApplicationIdentifier.SSCC.getKey());
      ssccScannedData.setValue("001234567897984653");
      scannedDataList.add(ssccScannedData);

      instructionRequest.setScannedDataList(scannedDataList);

      File resource = new ClassPathResource("GdmMappedResponseV2_canceled_po.json").getFile();
      String mockResponse = new String(Files.readAllBytes(resource.toPath()));
      doReturn("MOCK_REPACKAGING_VENDOR1,MOCK_REPACKAGING_VENDOR1")
          .when(appConfig)
          .getRepackageVendors();
      when(instructionPersisterService.saveInstruction(any(Instruction.class)))
          .thenAnswer(i -> i.getArguments()[0]);
      doReturn(twoDBarcodeScanTypeDocumentsSearchHandler)
          .when(tenantSpecificConfigReader)
          .getConfiguredInstance(
              "32898",
              ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
              DeliveryDocumentsSearchHandler.class);
      when(rxDeliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
              anyString(), anyString(), any(HttpHeaders.class)))
          .thenReturn(
              new ArrayList(Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class))));
      doReturn(0L)
          .when(receiptService)
          .findReceiptsSummaryByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndSSCC(
              anyLong(), anyString(), anyInt(), anyString());
      doReturn(new Pair<>(50, 0L))
          .when(instructionHelperService)
          .getReceivedQtyDetailsAndValidate(
              eq(instructionRequest.getProblemTagId()),
              any(),
              eq(instructionRequest.getDeliveryNumber()),
              eq(false),
              eq(false));

      doAnswer(
              new Answer<Pair>() {
                public Pair answer(InvocationOnMock invocation) {
                  List<DeliveryDocument> deliveryDocuments =
                      (List<DeliveryDocument>) invocation.getArguments()[0];

                  return new Pair<>(deliveryDocuments.get(0), 0);
                }
              })
          .when(instructionHelperService)
          .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

      InstructionResponse serveInstructionResponse =
          rxInstructionService.serveInstructionRequest(
              gson.toJson(instructionRequest), httpHeaders);

    } catch (ReceivingInternalException e) {

      assertEquals(e.getErrorCode(), ExceptionCodes.INVALID_PO_PO_LINE_STATUS);
      assertEquals(e.getMessage(), RxConstants.INVALID_PO_PO_LINE_STATUS);
    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
    verify(instructionHelperService, times(0))
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());
  }

  @Test
  public void test_serveInstructionRequest_cancel_po_1() {

    try {
      when(configUtils.getConfiguredFeatureFlag(
              "32898", ReceivingConstants.ALLOW_SINGLE_ITEM_MULTI_PO_LINE))
          .thenReturn(true);
      when(appConfig.isFilteringInvalidposEnabled()).thenReturn(true);

      InstructionRequest instructionRequest = new InstructionRequest();
      instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
      instructionRequest.setDeliveryNumber("2356895623");
      instructionRequest.setDeliveryStatus("WRK");
      instructionRequest.setSscc("00123456789562356895656");
      instructionRequest.setDoorNumber("V6949");

      List<ScannedData> scannedDataList = new ArrayList<>();
      ScannedData ssccScannedData = new ScannedData();
      ssccScannedData.setKey(ApplicationIdentifier.SSCC.getKey());
      ssccScannedData.setValue("001234567897984653");
      scannedDataList.add(ssccScannedData);

      instructionRequest.setScannedDataList(scannedDataList);

      File resource = new ClassPathResource("GdmMappedResponseV2_canceled_po2.json").getFile();
      String mockResponse = new String(Files.readAllBytes(resource.toPath()));
      doReturn("MOCK_REPACKAGING_VENDOR1,MOCK_REPACKAGING_VENDOR1")
          .when(appConfig)
          .getRepackageVendors();
      doReturn(twoDBarcodeScanTypeDocumentsSearchHandler)
          .when(tenantSpecificConfigReader)
          .getConfiguredInstance(
              "32898",
              ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
              DeliveryDocumentsSearchHandler.class);
      when(instructionPersisterService.saveInstruction(any(Instruction.class)))
          .thenAnswer(i -> i.getArguments()[0]);
      when(rxDeliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
              anyString(), anyString(), any(HttpHeaders.class)))
          .thenReturn(
              new ArrayList(Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class))));
      doReturn(0L)
          .when(receiptService)
          .findReceiptsSummaryByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndSSCC(
              anyLong(), anyString(), anyInt(), anyString());
      doReturn(new Pair<>(50, 0L))
          .when(instructionHelperService)
          .getReceivedQtyDetailsAndValidate(
              eq(instructionRequest.getProblemTagId()),
              any(),
              eq(instructionRequest.getDeliveryNumber()),
              eq(false),
              eq(false));

      doAnswer(
              new Answer<Pair>() {
                public Pair answer(InvocationOnMock invocation) {
                  List<DeliveryDocument> deliveryDocuments =
                      (List<DeliveryDocument>) invocation.getArguments()[0];

                  return new Pair<>(deliveryDocuments.get(0), 0);
                }
              })
          .when(instructionHelperService)
          .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

      InstructionResponse serveInstructionResponse =
          rxInstructionService.serveInstructionRequest(
              gson.toJson(instructionRequest), httpHeaders);

    } catch (ReceivingInternalException e) {

      assertEquals(e.getErrorCode(), ExceptionCodes.INVALID_PO_PO_LINE_STATUS);
      assertEquals(e.getMessage(), RxConstants.INVALID_PO_PO_LINE_STATUS);
    } catch (Exception e) {
      fail(e.getMessage());
    }
    verify(instructionHelperService, times(0))
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());
  }

  @Test
  public void test_serveInstructionRequest_cancel_multi_po_line() throws Exception {

    when(configUtils.getConfiguredFeatureFlag(
            "32898", ReceivingConstants.ALLOW_SINGLE_ITEM_MULTI_PO_LINE))
        .thenReturn(true);

    when(appConfig.isFilteringInvalidposEnabled()).thenReturn(true);

    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
    instructionRequest.setDeliveryNumber("2356895623");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setSscc("00123456789562356895656");
    instructionRequest.setDoorNumber("V6949");

    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData ssccScannedData = new ScannedData();
    ssccScannedData.setKey(ApplicationIdentifier.SSCC.getKey());
    ssccScannedData.setValue("001234567897984653");
    scannedDataList.add(ssccScannedData);

    instructionRequest.setScannedDataList(scannedDataList);
    instructionRequest.setReceivingType(RxReceivingType.SSCC.getReceivingType());

    File resource =
        new ClassPathResource("GdmMappedResponseV2_canceled_multi_po_line.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    doReturn("MOCK_REPACKAGING_VENDOR1,MOCK_REPACKAGING_VENDOR1")
        .when(appConfig)
        .getRepackageVendors();
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doReturn(twoDBarcodeScanTypeDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            "32898",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(rxDeliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class)));
    doReturn(0L)
        .when(receiptService)
        .findReceiptsSummaryByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndSSCC(
            anyLong(), anyString(), anyInt(), anyString());
    doReturn(new Pair<>(50, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsInEaAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber()));

    doAnswer(
            (Answer<Pair>)
                invocation -> {
                  List<DeliveryDocument> deliveryDocuments =
                      (List<DeliveryDocument>) invocation.getArguments()[0];

                  return new Pair<>(deliveryDocuments.get(0), 0);
                })
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    InstructionResponse serveInstructionResponse =
        rxInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    assertNotNull(serveInstructionResponse);
    assertEquals(serveInstructionResponse.getDeliveryDocuments().size(), 1);
    assertEquals(
        serveInstructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(),
        1);
    assertEquals(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getManufactureDetails()
            .size(),
        1);
    assertNotNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPalletSSCC());
    assertNotNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPackSSCC());

    assertEquals(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceLineNumber(),
        2);

    verify(instructionHelperService, times(1))
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());
  }

  @Test
  public void test_serveInstructionRequest_multi_sku() throws Exception {

    File instructionRequestResource =
        new ClassPathResource("multi_sku_sscc_instruction_request.json").getFile();
    String instructionRequestStr =
        new String(Files.readAllBytes(instructionRequestResource.toPath()));
    InstructionRequest instructionRequest =
        gson.fromJson(instructionRequestStr, InstructionRequest.class);
    instructionRequest.setReceivingType(RxReceivingType.SSCC.getReceivingType());

    File resource = new ClassPathResource("GdmMappedResponseV2.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    doReturn("MOCK_REPACKAGING_VENDOR1,MOCK_REPACKAGING_VENDOR1")
        .when(appConfig)
        .getRepackageVendors();
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    when(rxDeliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class)));
    doReturn(0L)
        .when(receiptService)
        .findReceiptsSummaryByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndSSCC(
            anyLong(), anyString(), anyInt(), anyString());
    doReturn(new Pair<>(50, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsInEaAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber()));

    doAnswer(
            (Answer<Pair>)
                invocation -> {
                  List<DeliveryDocument> deliveryDocuments =
                      (List<DeliveryDocument>) invocation.getArguments()[0];

                  return new Pair<>(deliveryDocuments.get(0), 0);
                })
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    InstructionResponse serveInstructionResponse =
        rxInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    assertNotNull(serveInstructionResponse);
    assertEquals(serveInstructionResponse.getDeliveryDocuments().size(), 1);
    assertEquals(
        serveInstructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(),
        1);
    assertEquals(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getManufactureDetails()
            .size(),
        1);
    assertNotNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPalletSSCC());
    assertNotNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPackSSCC());

    verify(instructionHelperService, times(1))
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    assertEquals(
        serveInstructionResponse.getDeliveryDocuments().get(0).getDeliveryNumber(), 60148930l);
    DeliveryDocument deliveryDocument =
        gson.fromJson(
            serveInstructionResponse.getInstruction().getDeliveryDocument(),
            DeliveryDocument.class);

    assertEquals(deliveryDocument.getDeliveryNumber(), 60148930l);
  }

  @Test
  public void test_serveInstructionRequest_Dept40_multi_sku() throws Exception {

    File instructionRequestResource =
        new ClassPathResource("multi_sku_upc_instruction_request.json").getFile();
    String instructionRequestStr =
        new String(Files.readAllBytes(instructionRequestResource.toPath()));
    InstructionRequest instructionRequest =
        gson.fromJson(instructionRequestStr, InstructionRequest.class);

    File resource = new ClassPathResource("gdm_Upc_response_D40.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    when(instructionPersisterService.saveInstruction(instructionCaptor.capture()))
        .thenAnswer(i -> i.getArguments()[0]);
    when(rxDeliveryService.findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(mockResponse);
    when(configUtils.getConfiguredFeatureFlag("32898", RxConstants.ENABLE_DEPT_CHECK_FEATURE_FLAG))
        .thenReturn(false);
    doReturn(new Pair<>(50, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsInEaAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber()));
    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) {
                List<DeliveryDocument> deliveryDocuments =
                    (List<DeliveryDocument>) invocation.getArguments()[0];

                return new Pair<>(deliveryDocuments.get(0), 0);
              }
            })
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());
    doReturn("MOCK_REPACKAGING_VENDOR1,MOCK_REPACKAGING_VENDOR1")
        .when(appConfig)
        .getRepackageVendors();

    InstructionResponse serveInstructionResponse =
        rxInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    assertNotNull(serveInstructionResponse);
    assertEquals(serveInstructionResponse.getDeliveryDocuments().size(), 1);
    assertEquals(
        serveInstructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(),
        1);
    assertNotNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getVendorStockNumber());
    assertEquals(serveInstructionResponse.getInstruction().getInstructionCode(), "Build Container");
    assertEquals(serveInstructionResponse.getInstruction().getInstructionMsg(), "Build Container");
    verify(instructionHelperService, times(1))
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    assertEquals(instructionCaptor.getValue().getGtin(), instructionRequest.getUpcNumber());

    assertEquals(
        serveInstructionResponse.getDeliveryDocuments().get(0).getDeliveryNumber(), 83077752l);
    DeliveryDocument deliveryDocument =
        gson.fromJson(
            serveInstructionResponse.getInstruction().getDeliveryDocument(),
            DeliveryDocument.class);

    assertEquals(deliveryDocument.getDeliveryNumber(), 83077752l);
  }

  @Test
  public void test_transferInstructionsMultiple() {

    MultipleTransferInstructionsRequestBody mockMultiTransferInstrRequestBody =
        new MultipleTransferInstructionsRequestBody();
    mockMultiTransferInstrRequestBody.setInstructionId(Arrays.asList(1234l));

    HttpHeaders mockHttpHeaders = MockRxHttpHeaders.getHeaders();

    doNothing().when(instructionHelperService).transferInstructions(anyList(), anyString());

    rxInstructionService.transferInstructionsMultiple(
        mockMultiTransferInstrRequestBody, mockHttpHeaders);

    verify(instructionHelperService, times(1)).transferInstructions(anyList(), anyString());
  }

  @Test
  public void test_transferInstructionsMultiple_invalid_userId() {

    try {
      MultipleTransferInstructionsRequestBody mockMultiTransferInstrRequestBody =
          new MultipleTransferInstructionsRequestBody();
      mockMultiTransferInstrRequestBody.setInstructionId(Arrays.asList(12345l));
      HttpHeaders mockHttpHeaders = MockRxHttpHeaders.getHeaders();
      mockHttpHeaders.remove(ReceivingConstants.USER_ID_HEADER_KEY);

      doNothing()
          .when(instructionRepository)
          .updateLastChangeUserIdAndLastChangeTs(anyList(), anyString());

      rxInstructionService.transferInstructionsMultiple(
          mockMultiTransferInstrRequestBody, mockHttpHeaders);
    } catch (ReceivingBadDataException e) {

      assertEquals(e.getErrorCode(), ExceptionCodes.INVALID_INPUT_USERID);
      assertEquals(e.getMessage(), ReceivingConstants.INVALID_INPUT_USERID);
    }
  }

  @Test
  public void test_serveInstructionRequest_multi_user_error_code() {

    try {
      InstructionRequest instructionRequest = new InstructionRequest();
      instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
      instructionRequest.setDeliveryNumber("2356895623");
      instructionRequest.setDeliveryStatus("WRK");
      instructionRequest.setSscc("00123456789562356895656");
      instructionRequest.setDoorNumber("V6949");

      List<ScannedData> scannedDataList = new ArrayList<>();
      ScannedData ssccScannedData = new ScannedData();
      ssccScannedData.setKey(ApplicationIdentifier.SSCC.getKey());
      ssccScannedData.setValue("001234567897984653");
      scannedDataList.add(ssccScannedData);

      instructionRequest.setScannedDataList(scannedDataList);
      instructionRequest.setReceivingType(RxReceivingType.SSCC.getReceivingType());

      File resource = new ClassPathResource("GdmMappedResponseV2.json").getFile();
      String mockResponse = new String(Files.readAllBytes(resource.toPath()));

      InstructionError instructionError =
          InstructionErrorCode.getErrorValue(ReceivingException.MULTI_USER_ERROR);
      ReceivingException mockReceivingException =
          new ReceivingException(
              instructionError.getErrorMessage(),
              HttpStatus.INTERNAL_SERVER_ERROR,
              instructionError.getErrorCode(),
              instructionError.getErrorHeader());

      doThrow(mockReceivingException)
          .when(rxInstructionPersisterService)
          .checkIfNewInstructionCanBeCreated(
              anyString(), anyInt(), anyLong(), anyInt(), anyBoolean(), anyString());

      doReturn("MOCK_REPACKAGING_VENDOR1,MOCK_REPACKAGING_VENDOR1")
          .when(appConfig)
          .getRepackageVendors();
      doReturn(twoDBarcodeScanTypeDocumentsSearchHandler)
          .when(tenantSpecificConfigReader)
          .getConfiguredInstance(
              "32898",
              ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
              DeliveryDocumentsSearchHandler.class);
      when(instructionPersisterService.saveInstruction(any(Instruction.class)))
          .thenAnswer(i -> i.getArguments()[0]);
      when(rxDeliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
              anyString(), anyString(), any(HttpHeaders.class)))
          .thenReturn(Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class)));
      doReturn(0L)
          .when(receiptService)
          .findReceiptsSummaryByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndSSCC(
              anyLong(), anyString(), anyInt(), anyString());
      doReturn(new Pair<>(50, 0L))
          .when(instructionHelperService)
          .getReceivedQtyDetailsInEaAndValidate(
              eq(instructionRequest.getProblemTagId()),
              any(),
              eq(instructionRequest.getDeliveryNumber()));

      doAnswer(
              new Answer<Pair>() {
                public Pair answer(InvocationOnMock invocation) {
                  List<DeliveryDocument> deliveryDocuments =
                      (List<DeliveryDocument>) invocation.getArguments()[0];

                  return new Pair<>(deliveryDocuments.get(0), 0);
                }
              })
          .when(instructionHelperService)
          .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

      InstructionResponse serveInstructionResponse =
          rxInstructionService.serveInstructionRequest(
              gson.toJson(instructionRequest), httpHeaders);

      verify(rxInstructionPersisterService, times(1))
          .checkIfNewInstructionCanBeCreated(
              anyString(), anyInt(), anyLong(), anyInt(), anyBoolean(), anyString());
    } catch (JsonSyntaxException | IOException | ReceivingException e) {
      fail(e.getMessage());
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), RxConstants.REQUEST_TRANSFTER_INSTR_ERROR_CODE);
      assertEquals(
          e.getDescription(),
          "A new pallet cannot be created until the pallets owned by other users for this item are completed. Please work on another item or request for pallet transfer.");
    }
  }

  @Test
  public void test_serveInstructionRequest_without_override() throws Exception {

    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
    instructionRequest.setDeliveryNumber("2356895623");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setSscc("00123456789562356895656");
    instructionRequest.setDoorNumber("V6949");

    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData ssccScannedData = new ScannedData();
    ssccScannedData.setKey(ApplicationIdentifier.SSCC.getKey());
    ssccScannedData.setValue("001234567897984653");
    scannedDataList.add(ssccScannedData);

    instructionRequest.setScannedDataList(scannedDataList);
    instructionRequest.setReceivingType(RxReceivingType.SSCC.getReceivingType());

    File resource = new ClassPathResource("GdmMappedResponseV2.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    doReturn(false).when(appConfig).isOverrideServeInstrMethod();
    doReturn("MOCK_REPACKAGING_VENDOR1,MOCK_REPACKAGING_VENDOR1")
        .when(appConfig)
        .getRepackageVendors();
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doReturn(twoDBarcodeScanTypeDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            "32898",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(rxDeliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class)));
    doReturn(0L)
        .when(receiptService)
        .findReceiptsSummaryByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndSSCC(
            anyLong(), anyString(), anyInt(), anyString());
    doReturn(new Pair<>(50, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsInEaAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber()));

    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) {
                List<DeliveryDocument> deliveryDocuments =
                    (List<DeliveryDocument>) invocation.getArguments()[0];

                return new Pair<>(deliveryDocuments.get(0), 0);
              }
            })
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    InstructionResponse serveInstructionResponse =
        rxInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    assertNotNull(serveInstructionResponse);
    assertEquals(serveInstructionResponse.getDeliveryDocuments().size(), 1);
    assertEquals(
        serveInstructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(),
        1);
    assertEquals(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getManufactureDetails()
            .size(),
        1);
    assertNotNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPalletSSCC());
    assertNotNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPackSSCC());

    verify(instructionHelperService, times(1))
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());
  }

  @Test
  public void test_serveInstructionRequest_same_item_multi_po_line() throws Exception {

    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
    instructionRequest.setDeliveryNumber("2356895623");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setSscc("00123456789562356895656");
    instructionRequest.setDoorNumber("V6949");

    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData ssccScannedData = new ScannedData();
    ssccScannedData.setKey(ApplicationIdentifier.SSCC.getKey());
    ssccScannedData.setValue("001234567897984653");
    scannedDataList.add(ssccScannedData);

    instructionRequest.setScannedDataList(scannedDataList);
    instructionRequest.setReceivingType(RxReceivingType.SSCC.getReceivingType());

    File resource =
        new ClassPathResource("GdmMappedResponseV2_same_item_multi_po_line.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    doReturn(true).when(configUtils).getConfiguredFeatureFlag(anyString(), anyString());
    doReturn("MOCK_REPACKAGING_VENDOR1,MOCK_REPACKAGING_VENDOR1")
        .when(appConfig)
        .getRepackageVendors();
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doReturn(twoDBarcodeScanTypeDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            "32898",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(rxDeliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class)));
    doReturn(0L)
        .when(receiptService)
        .findReceiptsSummaryByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndSSCC(
            anyLong(), anyString(), anyInt(), anyString());
    doReturn(new Pair<>(50, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsInEaAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber()));

    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) {
                List<DeliveryDocument> deliveryDocuments =
                    (List<DeliveryDocument>) invocation.getArguments()[0];

                return new Pair<>(deliveryDocuments.get(0), 0);
              }
            })
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    InstructionResponse serveInstructionResponse =
        rxInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    assertNotNull(serveInstructionResponse);
    assertEquals(serveInstructionResponse.getDeliveryDocuments().size(), 1);
    assertEquals(
        serveInstructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(),
        1);
    assertEquals(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getManufactureDetails()
            .size(),
        1);
    assertNotNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPalletSSCC());
    assertNotNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPackSSCC());

    verify(instructionHelperService, times(1))
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());
  }

  @Test
  public void test_serveInstructionRequest_same_item_multi_po() throws Exception {

    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
    instructionRequest.setDeliveryNumber("2356895623");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setSscc("00123456789562356895656");
    instructionRequest.setDoorNumber("V6949");

    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData ssccScannedData = new ScannedData();
    ssccScannedData.setKey(ApplicationIdentifier.SSCC.getKey());
    ssccScannedData.setValue("001234567897984653");
    scannedDataList.add(ssccScannedData);

    instructionRequest.setScannedDataList(scannedDataList);
    instructionRequest.setReceivingType(RxReceivingType.SSCC.getReceivingType());

    File resource = new ClassPathResource("GdmMappedResponseV2_same_item_multi_po.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    doReturn(true).when(configUtils).getConfiguredFeatureFlag(anyString(), anyString());
    doReturn("MOCK_REPACKAGING_VENDOR1,MOCK_REPACKAGING_VENDOR1")
        .when(appConfig)
        .getRepackageVendors();
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doReturn(twoDBarcodeScanTypeDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            "32898",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(rxDeliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class)));
    doReturn(0L)
        .when(receiptService)
        .findReceiptsSummaryByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndSSCC(
            anyLong(), anyString(), anyInt(), anyString());
    doReturn(new Pair<>(50, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsInEaAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber()));

    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) {
                List<DeliveryDocument> deliveryDocuments =
                    (List<DeliveryDocument>) invocation.getArguments()[0];

                return new Pair<>(deliveryDocuments.get(0), 0);
              }
            })
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    InstructionResponse serveInstructionResponse =
        rxInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    assertNotNull(serveInstructionResponse);
    assertEquals(serveInstructionResponse.getDeliveryDocuments().size(), 1);
    assertEquals(
        serveInstructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(),
        1);
    assertEquals(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getManufactureDetails()
            .size(),
        1);
    assertNotNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPalletSSCC());
    assertNotNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPackSSCC());

    verify(instructionHelperService, times(1))
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());
  }

  /**
   * if the problem issues type is ASN issue, we should allow instruction creation on different item
   * as well
   *
   * @throws Exception
   */
  @Test
  public void
      testServeInstructionRequestReturnsNewInstructionFor2dBarcodeScan_asnIssueType_Problemreceive()
          throws Exception {
    doReturn(true).when(rxManagedConfig).isProblemItemCheckEnabled();
    doReturn("ASN_ISSUE").when(rxManagedConfig).getWhiteListedProblemTypes();
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequestFor2dBarcodeScan();
    ScannedData expDateScannedData = new ScannedData();
    expDateScannedData.setApplicationIdentifier(
        ApplicationIdentifier.EXP.getApplicationIdentifier());
    expDateScannedData.setKey(ReceivingConstants.KEY_EXPIRY_DATE);
    expDateScannedData.setValue(
        DateFormatUtils.format(
            DateUtils.addYears(new Date(), 2), ReceivingConstants.EXPIRY_DATE_FORMAT));
    instructionRequest.getScannedDataList().add(expDateScannedData);

    instructionRequest.setProblemTagId("problemTagId");

    Pair<DeliveryDocument, Long> autoSelectedDocument =
        new Pair<DeliveryDocument, Long>(instructionRequest.getDeliveryDocuments().get(0), 0L);
    doReturn(autoSelectedDocument)
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    doReturn(null)
        .when(rxInstructionPersisterService)
        .fetchInstructionByDeliveryAndGtinAndLotNumberAndUserId(
            any(InstructionRequest.class), anyString());
    doReturn(0L)
        .when(receiptService)
        .findReceiptsSummaryByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndSSCC(
            anyLong(), anyString(), anyInt(), anyString());

    doReturn(new Pair<>(50, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsInEaAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber()));
    doReturn("MOCK_REPACKAGING_VENDOR1,MOCK_REPACKAGING_VENDOR1")
        .when(appConfig)
        .getRepackageVendors();
    doReturn(true).when(appConfig).isCloseDateCheckEnabled();
    doReturn(problemService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    ProblemLabel problemLabel = new ProblemLabel();
    problemLabel.setResolutionId("MOCK_RESOLUTION_ID");
    problemLabel.setProblemResponse(
        "{\"id\":\"98339bce-42f6-414a-be41-e40e78edb56c\",\"status\":\"OPEN\",\"label\":\"06001304645906\",\"slot\":\"M4032\",\"reportedQty\":5,\"remainingQty\":5,\"issue\":{\"id\":\"43f8a5df-c1b5-4018-9a99-567b3f2b41f0\",\"identifier\":\"210408-69961-6638-0000\",\"type\":\"ASN_ISSUE\",\"subType\":\"ASN_ISSUE\",\"uom\":\"ZA\",\"status\":\"ANSWERED\",\"businessStatus\":\"READY_TO_RECEIVE\",\"resolutionStatus\":\"COMPLETE_RESOLUTON\",\"upc\":\"10350742190017\",\"itemNumber\":550129240,\"deliveryNumber\":\"78869668\",\"quantity\":5},\"resolutions\":[{\"id\":\"917dbbb0-8bd7-4667-82af-89068f2bc2e5\",\"state\":\"OPEN\",\"type\":\"RECEIVE_AGAINST_ORIGINAL_LINE\",\"provider\":\"Manual\",\"quantity\":5,\"remainingQty\":5,\"acceptedQuantity\":0,\"rejectedQuantity\":0,\"resolutionPoNbr\":\"7702953583\",\"resolutionPoLineNbr\":1}]}");
    doReturn(problemLabel).when(problemService).findProblemLabelByProblemTagId(anyString());

    doReturn(getRxInstructionFor2dBarcodeReceiving())
        .when(instructionPersisterService)
        .saveInstruction(any(Instruction.class));
    Instruction instructionResponse =
        rxInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
    verify(instructionPersisterService, times(1))
        .fetchInstructionByDeliveryAndGtinAndLotNumberAndUserIdAndProblemTagId(
            anyLong(), anyMap(), anyString(), anyString());
  }

  @Test
  public void test_filter_InvalidPOs_rejected() {

    List<DeliveryDocumentLine> deliveryDocumentLineList = new ArrayList<>();
    DeliveryDocumentLine deliveryDocumentLine1 = new DeliveryDocumentLine();
    deliveryDocumentLine1.setPurchaseReferenceNumber("MOCK_PO");
    deliveryDocumentLine1.setPurchaseReferenceLineNumber(1);
    deliveryDocumentLine1.setPurchaseReferenceLineStatus(POLineStatus.REJECTED.toString());
    deliveryDocumentLineList.add(deliveryDocumentLine1);

    DeliveryDocumentLine deliveryDocumentLine2 = new DeliveryDocumentLine();
    deliveryDocumentLine2.setPurchaseReferenceNumber("MOCK_PO");
    deliveryDocumentLine2.setPurchaseReferenceLineNumber(2);
    deliveryDocumentLine2.setPurchaseReferenceLineStatus(POLineStatus.REJECTED.toString());
    deliveryDocumentLineList.add(deliveryDocumentLine2);

    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    DeliveryDocument mockDeliveryDocument = new DeliveryDocument();
    mockDeliveryDocument.setDeliveryDocumentLines(deliveryDocumentLineList);
    deliveryDocuments.add(mockDeliveryDocument);

    try {
      ReflectionTestUtils.invokeMethod(rxInstructionService, "filterInvalidPOs", deliveryDocuments);
    } catch (ReceivingInternalException receivingInternalException) {
      assertEquals(
          ExceptionCodes.INVALID_PO_PO_LINE_STATUS, receivingInternalException.getErrorCode());
      assertEquals(
          RxConstants.INVALID_PO_PO_LINE_STATUS, receivingInternalException.getDescription());
    }
  }

  @Test
  public void test_filter_InvalidPOs_cancelled_po_line() {

    List<DeliveryDocumentLine> deliveryDocumentLineList = new ArrayList<>();
    DeliveryDocumentLine deliveryDocumentLine1 = new DeliveryDocumentLine();
    deliveryDocumentLine1.setPurchaseReferenceNumber("MOCK_PO");
    deliveryDocumentLine1.setPurchaseReferenceLineNumber(1);
    deliveryDocumentLine1.setPurchaseReferenceLineStatus(POLineStatus.CANCELLED.toString());
    deliveryDocumentLineList.add(deliveryDocumentLine1);

    DeliveryDocumentLine deliveryDocumentLine2 = new DeliveryDocumentLine();
    deliveryDocumentLine2.setPurchaseReferenceNumber("MOCK_PO");
    deliveryDocumentLine2.setPurchaseReferenceLineNumber(2);
    deliveryDocumentLine2.setPurchaseReferenceLineStatus(POLineStatus.CANCELLED.toString());
    deliveryDocumentLineList.add(deliveryDocumentLine2);

    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    DeliveryDocument mockDeliveryDocument = new DeliveryDocument();
    mockDeliveryDocument.setDeliveryDocumentLines(deliveryDocumentLineList);
    deliveryDocuments.add(mockDeliveryDocument);

    try {
      ReflectionTestUtils.invokeMethod(rxInstructionService, "filterInvalidPOs", deliveryDocuments);
    } catch (ReceivingInternalException receivingInternalException) {
      assertEquals(
          ExceptionCodes.INVALID_PO_PO_LINE_STATUS, receivingInternalException.getErrorCode());
      assertEquals(
          RxConstants.INVALID_PO_PO_LINE_STATUS, receivingInternalException.getDescription());
    }
  }

  @Test
  public void test_filter_InvalidPOs_cancelled_po() {

    List<DeliveryDocumentLine> deliveryDocumentLineList = new ArrayList<>();
    DeliveryDocumentLine deliveryDocumentLine1 = new DeliveryDocumentLine();
    deliveryDocumentLine1.setPurchaseReferenceNumber("MOCK_PO");
    deliveryDocumentLine1.setPurchaseReferenceLineNumber(1);
    deliveryDocumentLine1.setPurchaseReferenceLineStatus(POLineStatus.CANCELLED.toString());
    deliveryDocumentLineList.add(deliveryDocumentLine1);

    DeliveryDocumentLine deliveryDocumentLine2 = new DeliveryDocumentLine();
    deliveryDocumentLine2.setPurchaseReferenceNumber("MOCK_PO");
    deliveryDocumentLine2.setPurchaseReferenceLineNumber(2);
    deliveryDocumentLine2.setPurchaseReferenceLineStatus(POLineStatus.CANCELLED.toString());
    deliveryDocumentLineList.add(deliveryDocumentLine2);

    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    DeliveryDocument mockDeliveryDocument = new DeliveryDocument();
    mockDeliveryDocument.setPurchaseReferenceStatus(POStatus.CNCL.toString());
    mockDeliveryDocument.setDeliveryDocumentLines(deliveryDocumentLineList);
    deliveryDocuments.add(mockDeliveryDocument);

    try {
      ReflectionTestUtils.invokeMethod(rxInstructionService, "filterInvalidPOs", deliveryDocuments);
    } catch (ReceivingInternalException receivingInternalException) {
      assertEquals(
          ExceptionCodes.INVALID_PO_PO_LINE_STATUS, receivingInternalException.getErrorCode());
      assertEquals(
          RxConstants.INVALID_PO_PO_LINE_STATUS, receivingInternalException.getDescription());
    }
  }

  @Test
  public void test_filter_InvalidPOs_closed_po_line() {

    List<DeliveryDocumentLine> deliveryDocumentLineList = new ArrayList<>();
    DeliveryDocumentLine deliveryDocumentLine1 = new DeliveryDocumentLine();
    deliveryDocumentLine1.setPurchaseReferenceNumber("MOCK_PO");
    deliveryDocumentLine1.setPurchaseReferenceLineNumber(1);
    deliveryDocumentLine1.setPurchaseReferenceLineStatus(POLineStatus.CLOSED.toString());
    deliveryDocumentLineList.add(deliveryDocumentLine1);

    DeliveryDocumentLine deliveryDocumentLine2 = new DeliveryDocumentLine();
    deliveryDocumentLine2.setPurchaseReferenceNumber("MOCK_PO");
    deliveryDocumentLine2.setPurchaseReferenceLineNumber(2);
    deliveryDocumentLine2.setPurchaseReferenceLineStatus(POLineStatus.CLOSED.toString());
    deliveryDocumentLineList.add(deliveryDocumentLine2);

    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    DeliveryDocument mockDeliveryDocument = new DeliveryDocument();
    mockDeliveryDocument.setDeliveryDocumentLines(deliveryDocumentLineList);
    deliveryDocuments.add(mockDeliveryDocument);

    try {
      ReflectionTestUtils.invokeMethod(rxInstructionService, "filterInvalidPOs", deliveryDocuments);
    } catch (ReceivingInternalException receivingInternalException) {
      assertEquals(
          ExceptionCodes.INVALID_PO_PO_LINE_STATUS, receivingInternalException.getErrorCode());
      assertEquals(
          RxConstants.INVALID_PO_PO_LINE_STATUS, receivingInternalException.getDescription());
    }
  }

  @Test
  public void test_filter_InvalidPOs_closed_po_() {

    List<DeliveryDocumentLine> deliveryDocumentLineList = new ArrayList<>();
    DeliveryDocumentLine deliveryDocumentLine1 = new DeliveryDocumentLine();
    deliveryDocumentLine1.setPurchaseReferenceNumber("MOCK_PO");
    deliveryDocumentLine1.setPurchaseReferenceLineNumber(1);
    deliveryDocumentLine1.setPurchaseReferenceLineStatus(POLineStatus.CLOSED.toString());
    deliveryDocumentLineList.add(deliveryDocumentLine1);

    DeliveryDocumentLine deliveryDocumentLine2 = new DeliveryDocumentLine();
    deliveryDocumentLine2.setPurchaseReferenceNumber("MOCK_PO");
    deliveryDocumentLine2.setPurchaseReferenceLineNumber(2);
    deliveryDocumentLine2.setPurchaseReferenceLineStatus(POLineStatus.CLOSED.toString());
    deliveryDocumentLineList.add(deliveryDocumentLine2);

    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    DeliveryDocument mockDeliveryDocument = new DeliveryDocument();
    mockDeliveryDocument.setPurchaseReferenceStatus(POStatus.CLOSED.toString());
    mockDeliveryDocument.setDeliveryDocumentLines(deliveryDocumentLineList);
    deliveryDocuments.add(mockDeliveryDocument);

    try {
      ReflectionTestUtils.invokeMethod(rxInstructionService, "filterInvalidPOs", deliveryDocuments);
    } catch (ReceivingInternalException receivingInternalException) {
      assertEquals(
          ExceptionCodes.INVALID_PO_PO_LINE_STATUS, receivingInternalException.getErrorCode());
      assertEquals(
          RxConstants.INVALID_PO_PO_LINE_STATUS, receivingInternalException.getDescription());
    }
  }

  @Test
  public void test_create_split_pallet_instruction_exempt_item() throws Exception {

    // enable feature flag
    doReturn(true).when(rxManagedConfig).isSplitPalletEnabled();

    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("a1-b2-c3-d4-e5");
    instructionRequest.setDeliveryNumber("95334888");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setUpcNumber("00681131240161");
    instructionRequest.setDoorNumber("V6949");
    instructionRequest.setReceivingType(RxReceivingType.SPLIT_PALLET_UPC.getReceivingType());
    ScannedData scannedData = new ScannedData();
    scannedData.setKey("BARCODE_SCAN");
    scannedData.setValue("00681131240161");
    List<ScannedData> scannedDataList = new ArrayList<>();
    scannedDataList.add(scannedData);
    instructionRequest.setScannedDataList(scannedDataList);

    File resource = new ClassPathResource("gdm_Upc_response_D40.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    when(instructionPersisterService.saveInstruction(instructionCaptor.capture()))
        .thenAnswer(i -> i.getArguments()[0]);
    doReturn(twoDBarcodeScanTypeDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            "32898",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(rxDeliveryService.findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(mockResponse);
    when(configUtils.getConfiguredFeatureFlag("32898", RxConstants.ENABLE_DEPT_CHECK_FEATURE_FLAG))
        .thenReturn(false);
    doReturn(new Pair<>(50, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsInEaAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber()));
    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) {
                List<DeliveryDocument> deliveryDocuments =
                    (List<DeliveryDocument>) invocation.getArguments()[0];

                return new Pair<>(deliveryDocuments.get(0), 0);
              }
            })
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    doAnswer(
            new Answer<Long>() {
              public Long answer(InvocationOnMock invocation) {
                return 1234l;
              }
            })
        .when(instructionSetIdGenerator)
        .generateInstructionSetId();

    InstructionResponse serveInstructionResponse =
        rxInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    assertNotNull(serveInstructionResponse);
    assertEquals(serveInstructionResponse.getDeliveryDocuments().size(), 1);
    assertEquals(
        serveInstructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(),
        1);
    assertNotNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getVendorStockNumber());
    assertEquals(serveInstructionResponse.getInstruction().getInstructionCode(), "Build Container");
    assertEquals(serveInstructionResponse.getInstruction().getInstructionMsg(), "Build Container");
    verify(instructionHelperService, times(1))
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    assertEquals(instructionCaptor.getValue().getGtin(), instructionRequest.getUpcNumber());
    verify(instructionSetIdGenerator, times(1)).generateInstructionSetId();

    assertTrue(instructionCaptor.getValue().getInstructionSetId() == 1234);
    assertTrue(serveInstructionResponse.getInstruction().getInstructionSetId() == 1234);
  }

  @Test
  public void test_create_split_pallet_instruction_exempt_item_multi() throws Exception {
    // enable feature flag
    doReturn(true).when(rxManagedConfig).isSplitPalletEnabled();

    File resource = new ClassPathResource("gdm_Upc_response_D40.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    doReturn(twoDBarcodeScanTypeDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            "32898",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(instructionPersisterService.saveInstruction(instructionCaptor.capture()))
        .thenAnswer(i -> i.getArguments()[0]);
    when(rxDeliveryService.findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(mockResponse);
    when(configUtils.getConfiguredFeatureFlag("32898", RxConstants.ENABLE_DEPT_CHECK_FEATURE_FLAG))
        .thenReturn(false);
    doReturn(new Pair<>(50, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsInEaAndValidate(nullable(String.class), any(), any(String.class));
    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) {
                List<DeliveryDocument> deliveryDocuments =
                    (List<DeliveryDocument>) invocation.getArguments()[0];

                return new Pair<>(deliveryDocuments.get(0), 0);
              }
            })
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    doAnswer(
            new Answer<Long>() {
              public Long answer(InvocationOnMock invocation) {
                return 1234l;
              }
            })
        .when(instructionSetIdGenerator)
        .generateInstructionSetId();

    Long instructionSetId = null;
    for (int item = 0; item < 2; item++) {

      InstructionRequest instructionRequest = new InstructionRequest();
      instructionRequest.setMessageId("a1-b2-c3-d4-e5");
      instructionRequest.setDeliveryNumber("95334888");
      instructionRequest.setDeliveryStatus("WRK");
      instructionRequest.setUpcNumber("00681131240161");
      instructionRequest.setDoorNumber("V6949");
      instructionRequest.setReceivingType(RxReceivingType.SPLIT_PALLET_UPC.getReceivingType());
      instructionRequest.setInstructionSetId(instructionSetId);
      ScannedData scannedData = new ScannedData();
      scannedData.setKey("BARCODE_SCAN");
      scannedData.setValue("00681131240161");
      List<ScannedData> scannedDataList = new ArrayList<>();
      scannedDataList.add(scannedData);
      instructionRequest.setScannedDataList(scannedDataList);

      InstructionResponse serveInstructionResponse =
          rxInstructionService.serveInstructionRequest(
              gson.toJson(instructionRequest), httpHeaders);
      assertNotNull(serveInstructionResponse);
      assertEquals(serveInstructionResponse.getDeliveryDocuments().size(), 1);
      assertEquals(
          serveInstructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(),
          1);
      assertNotNull(
          serveInstructionResponse
              .getDeliveryDocuments()
              .get(0)
              .getDeliveryDocumentLines()
              .get(0)
              .getVendorStockNumber());
      assertEquals(
          serveInstructionResponse.getInstruction().getInstructionCode(), "Build Container");
      assertEquals(
          serveInstructionResponse.getInstruction().getInstructionMsg(), "Build Container");

      assertEquals(instructionCaptor.getValue().getGtin(), instructionRequest.getUpcNumber());

      assertTrue(instructionCaptor.getValue().getInstructionSetId() == 1234);
      assertTrue(serveInstructionResponse.getInstruction().getInstructionSetId() == 1234);
      instructionSetId = instructionCaptor.getValue().getInstructionSetId();
    }
    verify(instructionHelperService, times(2))
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());
    verify(instructionSetIdGenerator, times(1)).generateInstructionSetId();
  }

  @Test
  public void test_getInstructionById() {
    try {
      doReturn(MockInstruction.getInstructionWithManufactureDetails())
          .when(instructionPersisterService)
          .getInstructionById(anyLong());
      Instruction instruction = rxInstructionService.getInstructionById(12345L);
      assertNotNull(instruction);
      assertNotNull(instruction.getId());
      assertEquals(instruction.getProjectedReceiveQtyUOM(), ReceivingConstants.Uom.VNPK);
      verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    } catch (ReceivingException e) {
      Assert.assertTrue(false, "Exception not expected");
    }
  }

  @Test
  public void test_getInstructionSummaryByDeliveryAndInstructionSetId() {

    ArgumentCaptor<Long> deliveryNumberCaptor = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<Long> instructionSetIdCaptor = ArgumentCaptor.forClass(Long.class);

    List<Instruction> mockSummaryList = new ArrayList<>();
    mockSummaryList.add(MockInstruction.getInstructionWithManufactureDetails());

    doReturn(mockSummaryList)
        .when(instructionRepository)
        .findByDeliveryNumberAndInstructionSetIdOrderByCreateTs(
            deliveryNumberCaptor.capture(), instructionSetIdCaptor.capture());

    List<InstructionSummary> instructionSummaryByDeliveryAndInstructionSetId =
        rxInstructionService.getInstructionSummaryByDeliveryAndInstructionSetId(12345l, 98765l);
    assertNotNull(instructionSummaryByDeliveryAndInstructionSetId);

    assertTrue(deliveryNumberCaptor.getValue() == 12345l);
    assertTrue(instructionSetIdCaptor.getValue() == 98765l);

    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndInstructionSetIdOrderByCreateTs(anyLong(), anyLong());
  }

  @Test
  public void test_getInstructionSummaryByDeliveryAndInstructionSetId_optional_InstructionSetId() {

    ArgumentCaptor<Long> deliveryNumberCaptor = ArgumentCaptor.forClass(Long.class);

    List<Instruction> mockSummaryList = new ArrayList<>();
    mockSummaryList.add(MockInstruction.getInstructionWithManufactureDetails());

    doReturn(mockSummaryList)
        .when(instructionRepository)
        .findByDeliveryNumber(deliveryNumberCaptor.capture());

    List<InstructionSummary> instructionSummaryByDeliveryAndInstructionSetId =
        rxInstructionService.getInstructionSummaryByDeliveryAndInstructionSetId(12345l, null);
    assertNotNull(instructionSummaryByDeliveryAndInstructionSetId);

    assertTrue(deliveryNumberCaptor.getValue() == 12345l);

    verify(instructionRepository, times(1)).findByDeliveryNumber(anyLong());
  }

  @Test
  public void test_filterPOsByLot() {
    List<DeliveryDocument> deliveryDocumentList = new ArrayList<>();
    for (int k = 0; k < 3; k++) {
      DeliveryDocument mockDeliveryDocument = new DeliveryDocument();
      mockDeliveryDocument.setVendorNumber("MOCK_NON_WH_VENDOR");
      List<DeliveryDocumentLine> deliveryDocumentLineList = new ArrayList<>();
      for (int j = 0; j < 3; j++) {
        DeliveryDocumentLine mockDeliveryDocumentLine = new DeliveryDocumentLine();
        List<ManufactureDetail> mockManufactureDetailList = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
          ManufactureDetail mockManufactureDetail = new ManufactureDetail();
          mockManufactureDetail.setLot("MOCK_LOT_" + j + "_" + i + "_" + k);
          mockManufactureDetailList.add(mockManufactureDetail);
        }
        mockDeliveryDocumentLine.setManufactureDetails(mockManufactureDetailList);
        deliveryDocumentLineList.add(mockDeliveryDocumentLine);
      }
      mockDeliveryDocument.setDeliveryDocumentLines(deliveryDocumentLineList);
      deliveryDocumentList.add(mockDeliveryDocument);
    }

    doReturn("MOCK_WH_VENDOR").when(rxManagedConfig).getWholesalerVendors();
    doReturn(true).when(rxManagedConfig).isWholesalerLotCheckEnabled();

    RxInstructionService rxInstructionService = new RxInstructionService();
    ReflectionTestUtils.setField(rxInstructionService, "rxManagedConfig", rxManagedConfig);

    ReflectionTestUtils.invokeMethod(
        rxInstructionService, "filterPOsByLot", deliveryDocumentList, "mock_lot_1_1_1");

    assertTrue(CollectionUtils.isNotEmpty(deliveryDocumentList));
    assertSame(deliveryDocumentList.size(), 1);
    assertSame(deliveryDocumentList.get(0).getDeliveryDocumentLines().size(), 1);
    assertSame(
        deliveryDocumentList
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getManufactureDetails()
            .size(),
        3);
    assertTrue(
        deliveryDocumentList
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getManufactureDetails()
            .stream()
            .map(ManufactureDetail::getLot)
            .collect(Collectors.toList())
            .contains("MOCK_LOT_1_1_1"));

    verify(rxManagedConfig, times(3)).getWholesalerVendors();
    verify(rxManagedConfig, times(3)).isWholesalerLotCheckEnabled();
  }

  @Test
  public void test_serveInstructionRequest_epcis_unit_flow() throws Exception {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
    instructionRequest.setDeliveryNumber("2356895623");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setSscc("00123456789562356895656");
    instructionRequest.setDoorNumber("V6949");

    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData ssccScannedData = new ScannedData();
    ssccScannedData.setKey(ApplicationIdentifier.SSCC.getKey());
    ssccScannedData.setValue("001234567897984653");
    scannedDataList.add(ssccScannedData);

    instructionRequest.setScannedDataList(scannedDataList);
    instructionRequest.setReceivingType(RxReceivingType.SSCC.getReceivingType());

    File resource =
        new ClassPathResource("GdmMappedResponse_with_serialized_pack_unit_response.json")
            .getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    File sertialPackResource =
        new ClassPathResource("serialized_pack_unit_response.json").getFile();
    String sertialPackResponse = new String(Files.readAllBytes(sertialPackResource.toPath()));
    List<DeliveryDocument> deliveryDocuments =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    List<Pack> packs = Arrays.asList(gson.fromJson(sertialPackResponse, Pack[].class));
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setPacks(packs);
    doReturn("MOCK_REPACKAGING_VENDOR1,MOCK_REPACKAGING_VENDOR1")
        .when(appConfig)
        .getRepackageVendors();
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doReturn(twoDBarcodeScanTypeDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            "32898",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
    when(rxDeliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class)))
        .thenReturn(deliveryDocuments);
    doReturn(0L)
        .when(receiptService)
        .findReceiptsSummaryByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndSSCC(
            anyLong(), anyString(), anyInt(), anyString());
    doReturn(new Pair<>(50, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsInEaAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber()));
    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) {
                List<DeliveryDocument> deliveryDocuments =
                    (List<DeliveryDocument>) invocation.getArguments()[0];

                return new Pair<>(deliveryDocuments.get(0), 0);
              }
            })
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    InstructionResponse serveInstructionResponse =
        rxInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    assertNotNull(serveInstructionResponse);
    assertEquals(
        serveInstructionResponse.getInstruction().getInstructionCode(),
        RxInstructionType.RX_SER_BUILD_UNITS_SCAN.getInstructionType());
    assertEquals(
        serveInstructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(),
        1);
    assertTrue(
        CollectionUtils.isNotEmpty(
            serveInstructionResponse
                .getDeliveryDocuments()
                .get(0)
                .getDeliveryDocumentLines()
                .get(0)
                .getAdditionalInfo()
                .getSerializedInfo()));
    assertNotNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPackSSCC());
    assertNotNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPackSSCC());

    verify(instructionHelperService, times(1))
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());
  }

  @Test
  public void test_serveInstructionRequest_epcis_pallet_flow_happy_path() throws Exception {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
    instructionRequest.setDeliveryNumber("2356895623");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setSscc("00100700302232313333");
    instructionRequest.setDoorNumber("V6949");

    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData ssccScannedData = new ScannedData();
    ssccScannedData.setKey(ApplicationIdentifier.SSCC.getKey());
    ssccScannedData.setValue("00100700302232313333");
    scannedDataList.add(ssccScannedData);

    instructionRequest.setScannedDataList(scannedDataList);
    instructionRequest.setReceivingType(RxReceivingType.SSCC.getReceivingType());

    File resource =
        new ClassPathResource("GdmMappedResponse_with_serialized_pack_unit_response.json")
            .getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    File sertialPackResource = new ClassPathResource("serialized_pallet_response.json").getFile();
    String sertialPackResponse = new String(Files.readAllBytes(sertialPackResource.toPath()));
    List<DeliveryDocument> deliveryDocuments =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    List<Pack> packs = Arrays.asList(gson.fromJson(sertialPackResponse, Pack[].class));
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setPacks(packs);
    doReturn("MOCK_REPACKAGING_VENDOR1,MOCK_REPACKAGING_VENDOR1")
        .when(appConfig)
        .getRepackageVendors();
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doReturn(twoDBarcodeScanTypeDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            "32898",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
    when(rxDeliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class)))
        .thenReturn(deliveryDocuments);
    doReturn(0L)
        .when(receiptService)
        .findReceiptsSummaryByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndSSCC(
            anyLong(), anyString(), anyInt(), anyString());
    doReturn(new Pair<>(50, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsInEaAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber()));
    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) {
                List<DeliveryDocument> deliveryDocuments =
                    (List<DeliveryDocument>) invocation.getArguments()[0];

                return new Pair<>(deliveryDocuments.get(0), 0);
              }
            })
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    InstructionResponse serveInstructionResponse =
        rxInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    assertNotNull(serveInstructionResponse);
    assertEquals(
        serveInstructionResponse.getInstruction().getInstructionCode(),
        RxInstructionType.RX_SER_BUILD_CONTAINER.getInstructionType());
    assertEquals(
        serveInstructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(),
        1);
    assertEquals(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getSerializedInfo()
            .size(),
        packs.size());
    assertNotNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPalletSSCC());
    assertNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPackSSCC());

    verify(instructionHelperService, times(1))
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());
  }

  @Test
  public void test_serveInstructionRequest_epcis_multi_case_flow() throws Exception {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
    instructionRequest.setDeliveryNumber("2356895623");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setSscc("00909899000020014394");
    instructionRequest.setDoorNumber("V6949");

    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData ssccScannedData = new ScannedData();
    ssccScannedData.setKey(ApplicationIdentifier.SSCC.getKey());
    ssccScannedData.setValue("00909899000020014394");
    scannedDataList.add(ssccScannedData);

    instructionRequest.setScannedDataList(scannedDataList);
    instructionRequest.setReceivingType(RxReceivingType.SSCC.getReceivingType());

    File resource =
        new ClassPathResource("GdmMappedResponse_with_serialized_pack_unit_response.json")
            .getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    File serialPackResource = new ClassPathResource("serialized_pallet_response.json").getFile();
    String sertialPackResponse = new String(Files.readAllBytes(serialPackResource.toPath()));
    List<DeliveryDocument> deliveryDocuments =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    List<Pack> packs = Arrays.asList(gson.fromJson(sertialPackResponse, Pack[].class));
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setPacks(packs);
    File unitSerialResource =
        new ClassPathResource("serialized_multi_unit_response.json").getFile();
    String unitSerialResponse = new String(Files.readAllBytes(unitSerialResource.toPath()));
    PackItemResponse packItemResponse = gson.fromJson(unitSerialResponse, PackItemResponse.class);
    doReturn("MOCK_REPACKAGING_VENDOR1,MOCK_REPACKAGING_VENDOR1")
        .when(appConfig)
        .getRepackageVendors();
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doReturn(twoDBarcodeScanTypeDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            "32898",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
    when(rxDeliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class)))
        .thenReturn(deliveryDocuments);
    when(rxDeliveryService.getUnitSerializedInfo(
            any(UnitSerialRequest.class), any(HttpHeaders.class)))
        .thenReturn(packItemResponse);
    doReturn(0L)
        .when(receiptService)
        .findReceiptsSummaryByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndSSCC(
            anyLong(), anyString(), anyInt(), anyString());
    doReturn(new Pair<>(50, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsInEaAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber()));
    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) {
                List<DeliveryDocument> deliveryDocuments =
                    (List<DeliveryDocument>) invocation.getArguments()[0];

                return new Pair<>(deliveryDocuments.get(0), 0);
              }
            })
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    InstructionResponse serveInstructionResponse =
        rxInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    assertNotNull(serveInstructionResponse);
    assertEquals(
        serveInstructionResponse.getInstruction().getInstructionCode(),
        RxInstructionType.RX_SER_CNTR_CASE_SCAN.getInstructionType());
    assertEquals(
        serveInstructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(),
        1);
    assertEquals(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getSerializedInfo()
            .size(),
        packs.size() - 1 + packItemResponse.getPacks().get(0).getItems().size());
    assertNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPalletSSCC());
    assertNotNull(
        serveInstructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPackSSCC());

    verify(instructionHelperService, times(1))
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());
  }

  private List<Pack> getPackfromFile() throws IOException {
    File resource = new ClassPathResource("gdm_epcis_invalid_pack.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    List<Pack> packs = Arrays.asList(gson.fromJson(mockResponse, Pack[].class));
    return packs;
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_serveInstructionRequest_epcis_enabled_vendor_no_serialized_data_exception()
      throws Exception {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("asdrvdesv-6h7b-466d-ba2e-sdrcgt67huj6");
    instructionRequest.setDeliveryNumber("2356895623");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setSscc("00909899000020014394");
    instructionRequest.setDoorNumber("V6949");
    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData ssccScannedData = new ScannedData();
    ssccScannedData.setKey(ApplicationIdentifier.SSCC.getKey());
    ssccScannedData.setValue("00909899000020014394");
    scannedDataList.add(ssccScannedData);
    instructionRequest.setScannedDataList(scannedDataList);
    instructionRequest.setReceivingType(RxReceivingType.SSCC.getReceivingType());
    File resource =
        new ClassPathResource("GdmMappedResponse_with_serialized_pack_unit_response.json")
            .getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> deliveryDocuments =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    doReturn("MOCK_REPACKAGING_VENDOR1,MOCK_REPACKAGING_VENDOR1")
        .when(appConfig)
        .getRepackageVendors();
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doReturn(twoDBarcodeScanTypeDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            "32898",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
    List<Pack> packs = getPackfromFile();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setPacks(packs);
    when(rxDeliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class)))
        .thenReturn(deliveryDocuments);
    doReturn(0L)
        .when(receiptService)
        .findReceiptsSummaryByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndSSCC(
            anyLong(), anyString(), anyInt(), anyString());
    doReturn(new Pair<>(50, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsInEaAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber()));
    doAnswer(
            new Answer<Pair>() {
              public Pair answer(InvocationOnMock invocation) {
                List<DeliveryDocument> deliveryDocuments =
                    (List<DeliveryDocument>) invocation.getArguments()[0];

                return new Pair<>(deliveryDocuments.get(0), 0);
              }
            })
        .when(instructionHelperService)
        .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    InstructionResponse serveInstructionResponse =
        rxInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
  }

  @Test
  public void test_CreateRequestToUpdatePackStatus() throws ReceivingException {
    Instruction mockInstructionFromDB = MockInstruction.getInstruction();
    mockInstructionFromDB.setId(12345l);
    mockInstructionFromDB.setLastChangeUserId("sysadmin");
    mockInstructionFromDB.setCreateUserId("sysadmin");
    mockInstructionFromDB.setInstructionCode(
        RxInstructionType.BUILD_CONTAINER_FOR_UPC_RECEIVING.getInstructionType());
    DeliveryDocumentLine documentLine = new DeliveryDocumentLine();

    documentLine.setPackSSCC("test");
    String result =
        rxInstructionService.createRequestToUpdatePackStatus(
            mockInstructionFromDB, getParentContainerWithChilds(), documentLine);

    Assert.assertNotNull(result);
  }

  private PackItemResponse mockPackItemResponse(List<Pack> packs) {
    PackItemResponse packItemResponse = new PackItemResponse();
    packItemResponse.setPacks(packs);
    return packItemResponse;
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_serveInstructionRequest_epcisInvalidLotExpiry()
      throws ReceivingException, IOException {
    // given
    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData scannedData = new ScannedData();
    scannedData.setApplicationIdentifier(ApplicationIdentifier.SERIAL.getApplicationIdentifier());
    scannedData.setKey(ApplicationIdentifier.SERIAL.getKey());
    scannedData.setValue("abcd");
    scannedDataList.add(scannedData);
    scannedData = new ScannedData();
    scannedData.setApplicationIdentifier(ApplicationIdentifier.GTIN.getApplicationIdentifier());
    scannedData.setKey(ApplicationIdentifier.GTIN.getKey());
    scannedData.setValue("00368180121015");
    scannedDataList.add(scannedData);
    scannedData = new ScannedData();
    scannedData.setApplicationIdentifier(ApplicationIdentifier.LOT.getApplicationIdentifier());
    scannedData.setKey(ApplicationIdentifier.LOT.getKey());
    scannedData.setValue("00L032C09AX");
    scannedDataList.add(scannedData);
    scannedData = new ScannedData();
    scannedData.setApplicationIdentifier(ReceivingConstants.KEY_EXPIRY_DATE);
    scannedData.setKey(ReceivingConstants.KEY_EXPIRY_DATE);
    scannedData.setValue("251224");
    scannedDataList.add(scannedData);

    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setDeliveryNumber("1234");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setUpcNumber("123456789");
    instructionRequest.setScannedDataList(scannedDataList);
    instructionRequest.setReceivingType(RxReceivingType.TWOD_BARCODE.getReceivingType());

    String mockMappedPackUnit =
        FileUtils.readFileToString(
            new ClassPathResource("GdmMappedResponse_with_serialized_pack_unit_response.json")
                .getFile(),
            Charset.defaultCharset());
    String mockPacks =
        FileUtils.readFileToString(
            new ClassPathResource("serialized_pallet_response.json").getFile(),
            Charset.defaultCharset());
    List<DeliveryDocument> deliveryDocuments =
        Arrays.asList(gson.fromJson(mockMappedPackUnit, DeliveryDocument[].class));
    List<Pack> packs = Arrays.asList(gson.fromJson(mockPacks, Pack[].class));
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setPacks(packs);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setIsSerUnit2DScan(true);
    DeliveryDocument deliveryDocument =
        gson.fromJson(gson.toJson(deliveryDocuments.get(0)), DeliveryDocument.class);
    deliveryDocument.getDeliveryDocumentLines().get(0).setPacks(packs);
    deliveryDocument.setPurchaseReferenceNumber("1");
    List<DeliveryDocument> newDeliveryDocs =
        Arrays.asList(deliveryDocuments.get(0), deliveryDocument);

    when(appConfig.getRepackageVendors()).thenReturn("MOCK_REPACKAGING_VENDOR");
    when(tenantSpecificConfigReader.getConfiguredInstance(
            "32898",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class))
        .thenReturn(twoDBarcodeScanTypeDocumentsSearchHandler);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    when(rxDeliveryService.findDeliveryDocumentByGtinAndLotNumberWithShipmentLinking(
            anyString(), anyMap(), any(HttpHeaders.class)))
        .thenReturn(newDeliveryDocs);
    when(instructionHelperService.autoSelectDocumentAndDocumentLineMABD(
            anyList(), anyInt(), anyString()))
        .thenReturn(new Pair<>(newDeliveryDocs.get(0), 0L));

    // when
    rxInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);

    // then should throw invalid scanned data exception
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_serveInstructionRequest_epcisMultiSku() throws ReceivingException, IOException {
    // given
    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData scannedData = new ScannedData();
    scannedData.setKey(ApplicationIdentifier.SSCC.getKey());
    scannedData.setValue("00909899000020014394");
    scannedDataList.add(scannedData);

    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setDeliveryNumber("1234");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setUpcNumber("123456789");
    instructionRequest.setScannedDataList(scannedDataList);
    instructionRequest.setSscc("00909899000020014394");
    instructionRequest.setReceivingType(RxReceivingType.SSCC.getReceivingType());

    String mockMappedPackUnit =
        FileUtils.readFileToString(
            new ClassPathResource("GdmMappedResponse_with_serialized_pack_unit_response.json")
                .getFile(),
            Charset.defaultCharset());
    String mockPacks =
        FileUtils.readFileToString(
            new ClassPathResource("serialized_pallet_response.json").getFile(),
            Charset.defaultCharset());
    List<DeliveryDocument> deliveryDocuments =
        Arrays.asList(gson.fromJson(mockMappedPackUnit, DeliveryDocument[].class));
    List<Pack> packs = Arrays.asList(gson.fromJson(mockPacks, Pack[].class));
    packs.get(0).setReceivingStatus(RxConstants.RECEIVED_ATTP_SERIALIZED_RECEIVING_STATUS);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPacksOfMultiSkuPallet(packs);
    instructionRequest.setDeliveryDocuments(deliveryDocuments);

    when(appConfig.getRepackageVendors()).thenReturn("MOCK_REPACKAGING_VENDOR");
    when(tenantSpecificConfigReader.getConfiguredInstance(
            "32898",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class))
        .thenReturn(twoDBarcodeScanTypeDocumentsSearchHandler);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);

    Instruction instruction = new Instruction();
    instruction.setDeliveryDocument(gson.toJson(deliveryDocuments.get(0)));
    instruction.setInstructionCode(
        RxInstructionType.SERIALIZED_MULTISKU_PALLET.getInstructionType());
    doReturn(instruction)
        .when(rxInstructionHelperService)
        .fetchMultiSkuInstrDeliveryDocument(anyString(), anyString(), anyString(), anyString());

    // when
    rxInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);

    // then should throw receiving data exception
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_serveInstructionRequest_epcisMultiSkuNoSscc()
      throws ReceivingException, IOException {
    // given
    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData scannedData = new ScannedData();
    scannedData.setKey(ApplicationIdentifier.SSCC.getKey());
    scannedData.setValue("00909899000020014394");
    scannedDataList.add(scannedData);

    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setDeliveryNumber("1234");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setUpcNumber("123456789");
    instructionRequest.setScannedDataList(scannedDataList);

    String mockMappedPackUnit =
        FileUtils.readFileToString(
            new ClassPathResource("GdmMappedResponse_with_serialized_pack_unit_response.json")
                .getFile(),
            Charset.defaultCharset());
    String mockPacks =
        FileUtils.readFileToString(
            new ClassPathResource("serialized_pallet_response.json").getFile(),
            Charset.defaultCharset());
    List<DeliveryDocument> deliveryDocuments =
        Arrays.asList(gson.fromJson(mockMappedPackUnit, DeliveryDocument[].class));
    List<Pack> packs = Arrays.asList(gson.fromJson(mockPacks, Pack[].class));
    packs.get(0).setReceivingStatus(RxConstants.RECEIVED_ATTP_SERIALIZED_RECEIVING_STATUS);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPacksOfMultiSkuPallet(packs);
    instructionRequest.setDeliveryDocuments(deliveryDocuments);

    when(appConfig.getRepackageVendors()).thenReturn("MOCK_REPACKAGING_VENDOR");
    when(tenantSpecificConfigReader.getConfiguredInstance(
            "32898",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class))
        .thenReturn(twoDBarcodeScanTypeDocumentsSearchHandler);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);

    Instruction instruction = new Instruction();
    instruction.setDeliveryDocument(gson.toJson(deliveryDocuments.get(0)));
    instruction.setInstructionCode(
        RxInstructionType.SERIALIZED_MULTISKU_PALLET.getInstructionType());
    doReturn(instruction)
        .when(rxInstructionHelperService)
        .fetchMultiSkuInstrDeliveryDocumentByDelivery(anyString(), anyString(), anyString());

    // when
    rxInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);

    // then should throw receiving data exception
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void callGdmToUpdatePackStatus_PalletRcvEqualQty_GdmError() throws ReceivingException {
    // given
    Container parent = new Container();
    Map<String, Object> containerMiscInfo = new HashMap<>();
    containerMiscInfo.put(RxConstants.DOCUMENT_ID, "1234");
    containerMiscInfo.put(RxConstants.SHIPMENT_NUMBER, "1234");
    parent.setContainerMiscInfo(containerMiscInfo);

    Instruction instruction = MockInstruction.getMockInstructionEpcis();
    instruction.setInstructionCode(RxInstructionType.SERIALIZED_CONTAINER.getInstructionType());
    instruction.setReceivedQuantity(1);
    instruction.setProjectedReceiveQty(1);
    DeliveryDocument deliveryDocument =
        gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);

    ResponseEntity<String> response = new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    when(restUtils.put(anyString(), any(), anyMap(), anyString())).thenReturn(response);

    // when
    rxInstructionService.callGdmToUpdatePackStatus(
        instruction, parent, MockRxHttpHeaders.getHeaders(), deliveryDocumentLine);

    // then should throw receiving exception gdm update status error
  }

  @Test
  public void createRequestToUpdatePackStatus_PalletRcv() throws ReceivingException {
    // given
    Instruction instruction = MockInstruction.getMockInstructionEpcis();
    instruction.setInstructionCode(RxInstructionType.SERIALIZED_CONTAINER.getInstructionType());
    DeliveryDocument deliveryDocument =
        gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);

    Container parent = MockInstruction.getContainer();
    Map<String, Object> containerMiscInfo = new HashMap<>();
    containerMiscInfo.put(
        RxConstants.INSTRUCTION_CODE, RxInstructionType.SERIALIZED_CONTAINER.getInstructionType());
    parent.setContainerMiscInfo(containerMiscInfo);
    Set<Container> childContainers =
        parent.getChildContainers().parallelStream().collect(Collectors.toSet());
    containerMiscInfo.put(RxConstants.DOCUMENT_ID, "1234");
    containerMiscInfo.put(RxConstants.DOCUMENT_PACK_ID, "1234");
    containerMiscInfo.put(RxConstants.SHIPMENT_NUMBER, "1234");
    childContainers.forEach(child -> child.setContainerMiscInfo(containerMiscInfo));
    parent.setChildContainers(childContainers);

    when(containerService.getContainerWithChildsByTrackingId(anyString(), anyBoolean()))
        .thenReturn(parent);

    // when
    String request =
        rxInstructionService.createRequestToUpdatePackStatus(
            instruction, parent, deliveryDocumentLine);

    // then
    assertNotNull(request);
  }

  @Test
  public void createRequestToUpdatePackStatus_CaseRcvPalletOfCase() throws ReceivingException {
    // given
    Container parent = new Container();
    Map<String, Object> containerMiscInfo = new HashMap<>();
    containerMiscInfo.put(RxConstants.DOCUMENT_ID, "1234");
    containerMiscInfo.put(RxConstants.SHIPMENT_NUMBER, "1234");
    parent.setContainerMiscInfo(containerMiscInfo);
    parent.setChildContainers(new HashSet());

    Instruction instruction = MockInstruction.getMockInstructionEpcis();
    instruction.setInstructionCode(RxInstructionType.SERIALIZED_CASES_SCAN.getInstructionType());
    instruction.setReceivedQuantity(1);
    instruction.setProjectedReceiveQty(1);
    DeliveryDocument deliveryDocument =
        gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setPalletOfCase("1234567890");
    instruction.setDeliveryDocument(gson.toJson(deliveryDocument));

    // when
    String request =
        rxInstructionService.createRequestToUpdatePackStatus(
            instruction, parent, deliveryDocumentLine);

    // then
    assertNotNull(request);
  }

  @Test
  public void createRequestToUpdatePackStatus_CaseRcv() throws ReceivingException {
    // given
    Instruction instruction = MockInstruction.getMockInstructionEpcis();
    instruction.setInstructionCode(RxInstructionType.SERIALIZED_CASES_SCAN.getInstructionType());
    DeliveryDocument deliveryDocument =
        gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);

    Container parent = MockInstruction.getContainer();
    Map<String, Object> containerMiscInfo = new HashMap<>();
    containerMiscInfo.put(
        RxConstants.INSTRUCTION_CODE, RxInstructionType.SERIALIZED_CASES_SCAN.getInstructionType());
    parent.setContainerMiscInfo(containerMiscInfo);
    Set<Container> childContainers =
        parent.getChildContainers().parallelStream().collect(Collectors.toSet());
    containerMiscInfo.put(RxConstants.DOCUMENT_ID, "1234");
    containerMiscInfo.put(RxConstants.DOCUMENT_PACK_ID, "1234");
    containerMiscInfo.put(RxConstants.SHIPMENT_NUMBER, "1234");
    childContainers.forEach(child -> child.setContainerMiscInfo(containerMiscInfo));
    parent.setChildContainers(childContainers);

    when(containerService.getContainerWithChildsByTrackingId(anyString(), anyBoolean()))
        .thenReturn(parent);

    // when
    String request =
        rxInstructionService.createRequestToUpdatePackStatus(
            instruction, parent, deliveryDocumentLine);

    // then
    assertNotNull(request);
  }

  @Test
  public void createRequestToUpdatePackStatus_CaseRcvProblem() throws ReceivingException {
    // given
    Instruction instruction = MockInstruction.getMockInstructionEpcis();
    instruction.setProblemTagId("32709034332423234");
    instruction.setInstructionCode(RxInstructionType.SERIALIZED_CASES_SCAN.getInstructionType());
    instruction.setReceivedQuantity(60);
    DeliveryDocument deliveryDocument =
        gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);

    Container parent = MockInstruction.getContainer();
    Map<String, Object> containerMiscInfo = new HashMap<>();
    containerMiscInfo.put(
        RxConstants.INSTRUCTION_CODE, RxInstructionType.SERIALIZED_CASES_SCAN.getInstructionType());
    parent.setContainerMiscInfo(containerMiscInfo);
    Set<Container> childContainers =
        parent.getChildContainers().parallelStream().collect(Collectors.toSet());
    containerMiscInfo.put(RxConstants.DOCUMENT_ID, "1234");
    containerMiscInfo.put(RxConstants.DOCUMENT_PACK_ID, "1234");
    containerMiscInfo.put(RxConstants.SHIPMENT_NUMBER, "1234");
    childContainers.forEach(child -> child.setContainerMiscInfo(containerMiscInfo));
    parent.setChildContainers(childContainers);

    when(containerService.getContainerWithChildsByTrackingId(anyString(), anyBoolean()))
        .thenReturn(parent);

    // when
    String request =
        rxInstructionService.createRequestToUpdatePackStatus(
            instruction, parent, deliveryDocumentLine);

    // then
    assertNotNull(request);
  }

  @Test
  public void createRequestToUpdatePackStatus_PartialRcv() throws ReceivingException {
    // given
    Instruction instruction = MockInstruction.getMockInstructionEpcis();
    instruction.setInstructionCode(RxInstructionType.RX_SER_BUILD_UNITS_SCAN.getInstructionType());
    DeliveryDocument deliveryDocument =
        gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);

    Container parent = MockInstruction.getContainer();
    Map<String, Object> containerMiscInfo = new HashMap<>();
    containerMiscInfo.put(
        RxConstants.INSTRUCTION_CODE,
        RxInstructionType.RX_SER_BUILD_UNITS_SCAN.getInstructionType());
    parent.setContainerMiscInfo(containerMiscInfo);
    Set<Container> childContainers =
        parent.getChildContainers().parallelStream().collect(Collectors.toSet());
    containerMiscInfo.put(RxConstants.DOCUMENT_ID, "1234");
    containerMiscInfo.put(RxConstants.DOCUMENT_PACK_ID, "1234");
    containerMiscInfo.put(RxConstants.SHIPMENT_NUMBER, "1234");
    childContainers.forEach(child -> child.setContainerMiscInfo(containerMiscInfo));
    parent.setChildContainers(childContainers);

    when(containerService.getContainerWithChildsByTrackingId(anyString(), anyBoolean()))
        .thenReturn(parent);

    // when
    String request =
        rxInstructionService.createRequestToUpdatePackStatus(
            instruction, parent, deliveryDocumentLine);

    // then
    assertNotNull(request);
  }

  @Test
  public void getUnitSerialRequestForUnit2D() {
    // when
    UnitSerialRequest request =
        ReflectionTestUtils.invokeMethod(
            rxInstructionService, "getUnitSerialRequestForUnit2D", "1234", "1234567890", "12345");

    // then
    assertNotNull(request);
  }

  @Test
  public void isSingleItemMultiPoPoLine() throws ReceivingException, IOException {
    // given
    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData scannedData = new ScannedData();
    scannedData.setApplicationIdentifier(ApplicationIdentifier.SERIAL.getApplicationIdentifier());
    scannedData.setKey(ApplicationIdentifier.SERIAL.getKey());
    scannedData.setValue("abc");
    scannedDataList.add(scannedData);
    scannedData = new ScannedData();
    scannedData.setApplicationIdentifier(ApplicationIdentifier.GTIN.getApplicationIdentifier());
    scannedData.setKey(ApplicationIdentifier.GTIN.getKey());
    scannedData.setValue("00368180121015");
    scannedDataList.add(scannedData);
    scannedData = new ScannedData();
    scannedData.setApplicationIdentifier(ApplicationIdentifier.LOT.getApplicationIdentifier());
    scannedData.setKey(ApplicationIdentifier.LOT.getKey());
    scannedData.setValue("00L032C09A");
    scannedDataList.add(scannedData);
    scannedData = new ScannedData();
    scannedData.setApplicationIdentifier(ReceivingConstants.KEY_EXPIRY_DATE);
    scannedData.setKey(ReceivingConstants.KEY_EXPIRY_DATE);
    scannedData.setValue("251224");
    scannedDataList.add(scannedData);

    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setDeliveryNumber("1234");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setUpcNumber("123456789");
    instructionRequest.setScannedDataList(scannedDataList);
    instructionRequest.setReceivingType(RxReceivingType.TWOD_BARCODE.getReceivingType());

    String mockPacks =
        FileUtils.readFileToString(
            new ClassPathResource("serialized_pallet_response.json").getFile(),
            Charset.defaultCharset());
    List<Pack> packs = Arrays.asList(gson.fromJson(mockPacks, Pack[].class));
    packs.get(0).setReceivingStatus(RxConstants.RECEIVED_ATTP_SERIALIZED_RECEIVING_STATUS);
    Instruction instruction = MockInstruction.getMockInstructionEpcis();
    DeliveryDocument deliveryDocument =
        gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    DeliveryDocumentLine line1 = deliveryDocument.getDeliveryDocumentLines().get(0);
    line1.setPacks(packs);
    DeliveryDocumentLine line2 = gson.fromJson(gson.toJson(line1), DeliveryDocumentLine.class);
    line2.setItemNbr(123456L);
    line2.setPacks(packs);
    deliveryDocument.setDeliveryDocumentLines(Arrays.asList(line1, line2));

    InstructionResponse instructionResponse = new InstructionResponseImplNew();

    when(rxManagedConfig.isMultiSkuInstructionViewEnabled()).thenReturn(true);
    ReflectionTestUtils.setField(rxManagedConfig, "isEpcisMultiSkuPalletEnabled", true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);

    // when
    rxInstructionService.isSingleItemMultiPoPoLine(
        Collections.singletonList(deliveryDocument),
        instructionRequest,
        instructionResponse,
        MockRxHttpHeaders.getHeaders());

    // then
    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getDeliveryDocuments());
  }

  private Optional<FitProblemTagResponse> mockFitProblemTagResponse() {
    FitProblemTagResponse fitProblemTagResponse = new FitProblemTagResponse();
    Resolution resolution = new Resolution();
    resolution.setType(ProblemResolutionType.RECEIVE_AGAINST_ORIGINAL_LINE.toString());
    fitProblemTagResponse.setResolutions(Collections.singletonList(resolution));
    return Optional.ofNullable(fitProblemTagResponse);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_serveInstructionRequest_epcisXblocked() throws Exception {
    // given
    ScannedData scannedData = new ScannedData();
    scannedData.setKey(ApplicationIdentifier.SSCC.getKey());
    scannedData.setValue("00100700302232313333");

    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setDeliveryNumber("1234");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setSscc("00100700302232313333");
    instructionRequest.setScannedDataList(Collections.singletonList(scannedData));
    instructionRequest.setReceivingType(RxReceivingType.SSCC.getReceivingType());

    String mockMappedPackUnit =
        FileUtils.readFileToString(
            new ClassPathResource("GdmMappedResponse_with_serialized_pack_unit_response.json")
                .getFile(),
            Charset.defaultCharset());
    String mockPacks =
        FileUtils.readFileToString(
            new ClassPathResource("serialized_pallet_response.json").getFile(),
            Charset.defaultCharset());
    List<DeliveryDocument> deliveryDocuments =
        Arrays.asList(gson.fromJson(mockMappedPackUnit, DeliveryDocument[].class));
    List<Pack> packs = Arrays.asList(gson.fromJson(mockPacks, Pack[].class));
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setPacks(packs);

    when(appConfig.getRepackageVendors()).thenReturn("MOCK_REPACKAGING_VENDOR");
    when(tenantSpecificConfigReader.getConfiguredInstance(
            "32898",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class))
        .thenReturn(twoDBarcodeScanTypeDocumentsSearchHandler);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    when(rxDeliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class)))
        .thenReturn(deliveryDocuments);
    when(instructionHelperService.autoSelectDocumentAndDocumentLineMABD(
            anyList(), anyInt(), anyString()))
        .thenReturn(new Pair<>(deliveryDocuments.get(0), 0L));
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(RX_XBLOCK_FEATURE_FLAG)).thenReturn(true);

    // when
    rxInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    // then should throw x blocked item exception
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_serveInstructionRequest_epcisControlledSubstance() throws Exception {
    // given
    ScannedData scannedData = new ScannedData();
    scannedData.setKey(ApplicationIdentifier.SSCC.getKey());
    scannedData.setValue("00100700302232313333");

    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setDeliveryNumber("1234");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setSscc("00100700302232313333");
    instructionRequest.setScannedDataList(Collections.singletonList(scannedData));
    instructionRequest.setReceivingType(RxReceivingType.SSCC.getReceivingType());

    String mockMappedPackUnit =
            FileUtils.readFileToString(
                    new ClassPathResource("GdmMappedResponse_with_controlledSubstance.json")
                            .getFile(),
                    Charset.defaultCharset());
    String mockPacks =
            FileUtils.readFileToString(
                    new ClassPathResource("serialized_pallet_response.json").getFile(),
                    Charset.defaultCharset());
    List<DeliveryDocument> deliveryDocuments =
            Arrays.asList(gson.fromJson(mockMappedPackUnit, DeliveryDocument[].class));
    List<Pack> packs = Arrays.asList(gson.fromJson(mockPacks, Pack[].class));
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setPacks(packs);

    when(appConfig.getRepackageVendors()).thenReturn("MOCK_REPACKAGING_VENDOR");
    when(tenantSpecificConfigReader.getConfiguredInstance(
            "32898",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class))
            .thenReturn(twoDBarcodeScanTypeDocumentsSearchHandler);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
            .thenReturn(true);
    when(rxDeliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class)))
            .thenReturn(deliveryDocuments);
    when(instructionHelperService.autoSelectDocumentAndDocumentLineMABD(
            anyList(), anyInt(), anyString()))
            .thenReturn(new Pair<>(deliveryDocuments.get(0), 0L));
    // when
    rxInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    // then should throw controlled substance exception
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_serveInstructionRequest_Dept40ControlledSubstance() throws Exception {
    // given
    ScannedData scannedData = new ScannedData();
    scannedData.setKey("BARCODE_SCAN");
    scannedData.setValue("00301691837020");

    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setDeliveryNumber("2356895623");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setUpcNumber("00301691837020");
    instructionRequest.setScannedDataList(Collections.singletonList(scannedData));

    String mockUpcResponse =
            FileUtils.readFileToString(
                    new ClassPathResource("GdmMappedResponse_with_controlledSubstance_D40.json").getFile(), Charset.defaultCharset());
    List<DeliveryDocument> deliveryDocuments =
            Arrays.asList(gson.fromJson(mockUpcResponse, DeliveryDocument[].class));

    when(tenantSpecificConfigReader.getConfiguredInstance(
            "32898",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class))
            .thenReturn(twoDBarcodeScanTypeDocumentsSearchHandler);
    when(configUtils.getConfiguredFeatureFlag("32898", RxConstants.ENABLE_DEPT_CHECK_FEATURE_FLAG))
            .thenReturn(false);
    when(rxDeliveryService.findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class)))
            .thenReturn(mockUpcResponse);
    when(instructionHelperService.getReceivedQtyDetailsInEaAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber())))
            .thenReturn(new Pair<>(50, 0L));
    when(instructionHelperService.autoSelectDocumentAndDocumentLineMABD(
            anyList(), anyInt(), anyString()))
            .thenReturn(new Pair<>(deliveryDocuments.get(0), 0L));

    // when
    rxInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    // then should throw controlled substance exception
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_serveInstructionRequest_Dept40XBlocked() throws Exception {
    // given
    ScannedData scannedData = new ScannedData();
    scannedData.setKey("BARCODE_SCAN");
    scannedData.setValue("00301691837020");

    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setDeliveryNumber("2356895623");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setUpcNumber("00301691837020");
    instructionRequest.setScannedDataList(Collections.singletonList(scannedData));

    String mockUpcResponse =
        FileUtils.readFileToString(
            new ClassPathResource("gdm_Upc_response_D40.json").getFile(), Charset.defaultCharset());
    List<DeliveryDocument> deliveryDocuments =
        Arrays.asList(gson.fromJson(mockUpcResponse, DeliveryDocument[].class));

    when(tenantSpecificConfigReader.getConfiguredInstance(
            "32898",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class))
        .thenReturn(twoDBarcodeScanTypeDocumentsSearchHandler);
    when(configUtils.getConfiguredFeatureFlag("32898", RxConstants.ENABLE_DEPT_CHECK_FEATURE_FLAG))
        .thenReturn(false);
    when(rxDeliveryService.findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(mockUpcResponse);
    when(instructionHelperService.getReceivedQtyDetailsInEaAndValidate(
            eq(instructionRequest.getProblemTagId()),
            any(),
            eq(instructionRequest.getDeliveryNumber())))
        .thenReturn(new Pair<>(50, 0L));
    when(instructionHelperService.autoSelectDocumentAndDocumentLineMABD(
            anyList(), anyInt(), anyString()))
        .thenReturn(new Pair<>(deliveryDocuments.get(0), 0L));
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(RX_XBLOCK_FEATURE_FLAG)).thenReturn(true);

    // when
    rxInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    // then should throw x blocked item exception
  }

  @Test
  public void
  testServeInstructionRequestReturnsNewInstructionFor2dBarcodeScan_asnIssueType_Problemreceiveeee()
          throws Exception {
    doReturn(true).when(rxManagedConfig).isProblemItemCheckEnabled();
    doReturn("ASN_ISSUE").when(rxManagedConfig).getWhiteListedProblemTypes();
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequestFor2dBarcodeScan_CloseDatedItem();
    ScannedData expDateScannedData = new ScannedData();
    expDateScannedData.setApplicationIdentifier(
            ApplicationIdentifier.EXP.getApplicationIdentifier());
    expDateScannedData.setKey(ReceivingConstants.KEY_EXPIRY_DATE);
    expDateScannedData.setValue(
            DateFormatUtils.format(
                    DateUtils.addYears(new Date(), 2), ReceivingConstants.EXPIRY_DATE_FORMAT));
    instructionRequest.getScannedDataList().add(expDateScannedData);


    instructionRequest.setProblemTagId("problemTagId");

    Pair<DeliveryDocument, Long> autoSelectedDocument =
            new Pair<DeliveryDocument, Long>(instructionRequest.getDeliveryDocuments().get(0), 0L);
    doReturn(autoSelectedDocument)
            .when(instructionHelperService)
            .autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString());

    doReturn(null)
            .when(rxInstructionPersisterService)
            .fetchInstructionByDeliveryAndGtinAndLotNumberAndUserId(
                    any(InstructionRequest.class), anyString());
    doReturn(0L)
            .when(receiptService)
            .findReceiptsSummaryByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndSSCC(
                    anyLong(), anyString(), anyInt(), anyString());

    doReturn(new Pair<>(50, 0L))
            .when(instructionHelperService)
            .getReceivedQtyDetailsInEaAndValidate(
                    eq(instructionRequest.getProblemTagId()),
                    any(),
                    eq(instructionRequest.getDeliveryNumber()));
    doReturn("MOCK_REPACKAGING_VENDOR1,MOCK_REPACKAGING_VENDOR1")
            .when(appConfig)
            .getRepackageVendors();
    doReturn(true).when(appConfig).isCloseDateCheckEnabled();
    doReturn(problemService)
            .when(tenantSpecificConfigReader)
            .getConfiguredInstance(anyString(), anyString(), any(Class.class));

    doReturn(true)
            .when(tenantSpecificConfigReader)
            .getConfiguredFeatureFlag("32898", IS_GDM_SHIPMENT_GET_BY_SCAN_v4_ENABLED, false);
    ProblemLabel problemLabel = new ProblemLabel();
    problemLabel.setResolutionId("MOCK_RESOLUTION_ID");
    problemLabel.setProblemResponse(
            "{\"id\":\"98339bce-42f6-414a-be41-e40e78edb56c\",\"status\":\"OPEN\",\"label\":\"06001304645906\",\"slot\":\"M4032\",\"reportedQty\":5,\"remainingQty\":5,\"issue\":{\"id\":\"43f8a5df-c1b5-4018-9a99-567b3f2b41f0\",\"identifier\":\"210408-69961-6638-0000\",\"type\":\"ASN_ISSUE\",\"subType\":\"ASN_ISSUE\",\"uom\":\"ZA\",\"status\":\"ANSWERED\",\"businessStatus\":\"READY_TO_RECEIVE\",\"resolutionStatus\":\"COMPLETE_RESOLUTON\",\"upc\":\"10350742190017\",\"itemNumber\":550129240,\"deliveryNumber\":\"78869668\",\"quantity\":5},\"resolutions\":[{\"id\":\"917dbbb0-8bd7-4667-82af-89068f2bc2e5\",\"state\":\"OPEN\",\"type\":\"RECEIVE_AGAINST_ORIGINAL_LINE\",\"provider\":\"Manual\",\"quantity\":5,\"remainingQty\":5,\"acceptedQuantity\":0,\"rejectedQuantity\":0,\"resolutionPoNbr\":\"7702953583\",\"resolutionPoLineNbr\":1}]}");
    doReturn(problemLabel).when(problemService).findProblemLabelByProblemTagId(anyString());

    doReturn(getRxInstructionFor2dBarcodeReceiving())
            .when(instructionPersisterService)
            .saveInstruction(any(Instruction.class));
    Instruction instructionResponse =
            rxInstructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
    verify(instructionPersisterService, times(1))
            .fetchInstructionByDeliveryAndGtinAndLotNumberAndUserIdAndProblemTagId(
                    anyLong(), anyMap(), anyString(), anyString());
  }

}
