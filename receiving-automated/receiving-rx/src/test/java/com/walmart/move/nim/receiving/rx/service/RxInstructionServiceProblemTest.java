package com.walmart.move.nim.receiving.rx.service;

import static com.walmart.move.nim.receiving.rx.constants.RxConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_GDM_SHIPMENT_GET_BY_SCAN_v4_ENABLED;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.*;
import com.walmart.move.nim.receiving.core.common.validators.DeliveryValidator;
import com.walmart.move.nim.receiving.core.common.validators.PurchaseReferenceValidator;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.*;
import com.walmart.move.nim.receiving.core.framework.transformer.Transformer;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rx.builders.RxContainerLabelBuilder;
import com.walmart.move.nim.receiving.rx.builders.RxReceiptsBuilder;
import com.walmart.move.nim.receiving.rx.config.RxManagedConfig;
import com.walmart.move.nim.receiving.rx.model.RxReceivingType;
import com.walmart.move.nim.receiving.rx.publisher.RxCancelInstructionReceiptPublisher;
import com.walmart.move.nim.receiving.rx.validators.RxInstructionValidator;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ProblemResolutionType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import org.mockito.*;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RxInstructionServiceProblemTest {

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
  public void test_serveInstructionRequest_ProblemUsingASN() throws Exception {
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

    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag("32898", IS_GDM_SHIPMENT_GET_BY_SCAN_v4_ENABLED, false);

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
    Optional<FitProblemTagResponse> mockProblemResponse = mockFitProblemTagResponse();
    mockProblemResponse
        .get()
        .getResolutions()
        .get(0)
        .setType(ProblemResolutionType.RECEIVE_USING_ASN_FLOW.toString());
    mockProblemResponse.get().getResolutions().get(0).setState(OPEN.toString());
    doReturn(mockProblemResponse)
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

    doReturn(false)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag("32898", IS_GDM_SHIPMENT_GET_BY_SCAN_v4_ENABLED, false);
  }

  private Optional<FitProblemTagResponse> mockFitProblemTagResponse() {
    FitProblemTagResponse fitProblemTagResponse = new FitProblemTagResponse();
    Resolution resolution = new Resolution();
    resolution.setType(ProblemResolutionType.RECEIVE_AGAINST_ORIGINAL_LINE.toString());
    fitProblemTagResponse.setResolutions(Collections.singletonList(resolution));
    return Optional.ofNullable(fitProblemTagResponse);
  }
}
