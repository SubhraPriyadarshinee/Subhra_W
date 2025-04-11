package com.walmart.move.nim.receiving.acc.service;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.CONTAINER_CREATE_TS;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.DATE_FORMAT_ISO8601;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.DEFAULT_ELIGIBLE_TRANSFER_PO_TYPE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ELIGIBLE_TRANSFER_POS_CCM_CONFIG;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.acc.config.ACCManagedConfig;
import com.walmart.move.nim.receiving.acc.constants.ACCConstants;
import com.walmart.move.nim.receiving.acc.constants.LocationType;
import com.walmart.move.nim.receiving.acc.entity.UserLocation;
import com.walmart.move.nim.receiving.acc.mock.data.MockACLMessageData;
import com.walmart.move.nim.receiving.acc.model.acl.verification.ACLVerificationEventMessage;
import com.walmart.move.nim.receiving.acc.repositories.UserLocationRepo;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.*;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.config.MaasTopics;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.message.publisher.JMSInstructionPublisher;
import com.walmart.move.nim.receiving.core.message.publisher.JMSReceiptPublisher;
import com.walmart.move.nim.receiving.core.message.publisher.MessagePublisher;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.FdeCreateContainerRequest;
import com.walmart.move.nim.receiving.core.model.LocationInfo;
import com.walmart.move.nim.receiving.core.model.UpdateInstructionRequest;
import com.walmart.move.nim.receiving.core.model.delivery.meta.PurchaseOrderInfo;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.model.label.PossibleUPC;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.core.service.exceptioncontainer.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ContainerException;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.POLineStatus;
import com.walmart.move.nim.receiving.utils.constants.PurchaseReferenceType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.SorterExceptionReason;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.CollectionUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class LPNReceivingServiceTest extends ReceivingTestBase {

  private static final String SORTER_EXCEPTION_TOPIC = "WMSOP/OA/LPN/EXCEPTION";
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private ACCManagedConfig accManagedConfig;
  @InjectMocks private LPNReceivingService lpnReceivingService;
  @InjectMocks private InstructionHelperService instructionHelperService;
  @InjectMocks private JMSInstructionPublisher jmsInstructionPublisher;
  @InjectMocks private InstructionPersisterService instructionPersisterService;
  @InjectMocks private DeliveryDocumentHelper deliveryDocumentHelper;
  @InjectMocks private UserLocationService userLocationService;
  @InjectMocks private ContainerService containerService;

  @Autowired private UserLocationRepo userLocationRepo;

  @Mock private JMSReceiptPublisher JMSReceiptPublisher;
  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private FdeService fdeService;
  @Mock private DCFinService dcFinService;
  @Mock private ReceiptService receiptService;
  @Mock private DeliveryServiceRetryableImpl deliveryService;
  @Mock private LabelDataService labelDataService;
  @Mock private JmsPublisher jmsPublisher;
  @Mock private MaasTopics maasTopics;
  @Mock private AppConfig appConfig;
  @Mock private InstructionRepository instructionRepository;
  @InjectMocks private JmsExceptionContainerPublisher jmsExceptionContainerPublisher;
  @Mock private ACCDeliveryMetaDataService accDeliveryMetaDataService;
  @Mock private DefaultLabelIdProcessor defaultLabelIdProcessor;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private SorterPublisher sorterPublisher;
  @Mock private ExceptionContainerHandlerFactory exceptionContainerHandlerFactory;
  @Mock private DefaultDeliveryDocumentsSearchHandler deliveryDocumentsSearchHandler;
  @Mock private ReceiptPublisher receiptPublisher;
  @InjectMocks private DockTagExceptionContainerHandler dockTagExceptionContainerHandler;
  @InjectMocks private OverageExceptionContainerHandler overageExceptionContainerHandler;
  @InjectMocks private NoAllocationExceptionContainerHandler noAllocationExceptionContainerHandler;
  @InjectMocks private ChannelFlipExceptionContainerHandler channelFlipExceptionContainerHandler;
  @InjectMocks private XBlockExceptionContainerHandler xBlockExceptionContainerHandler;

  @InjectMocks
  private NoDeliveryDocExceptionContainerHandler noDeliveryDocExceptionContainerHandler;

  private ACLVerificationEventMessage aclVerificationEventMessage;
  private PurchaseOrderInfo purchaseOrderInfo;
  private Gson gson = new Gson();
  @Autowired private JmsPublisher jmsPublisher1;

  @BeforeClass
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32987);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setCorrelationId("aafa2fcc-d299-4663-aa64-ba6f79704635");
    DateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT_ISO8601);
    TenantContext.setAdditionalParams(CONTAINER_CREATE_TS, dateFormat.format(new Date()));

    ReflectionTestUtils.setField(containerService, "configUtils", configUtils);
    ReflectionTestUtils.setField(instructionPersisterService, "containerService", containerService);
    ReflectionTestUtils.setField(
        instructionPersisterService, "tenantSpecificConfigReader", configUtils);

    ReflectionTestUtils.setField(instructionHelperService, "containerService", containerService);
    ReflectionTestUtils.setField(instructionHelperService, "jmsPublisher", jmsPublisher);
    ReflectionTestUtils.setField(instructionHelperService, "configUtils", configUtils);
    ReflectionTestUtils.setField(instructionHelperService, "deliveryService", deliveryService);
    ReflectionTestUtils.setField(userLocationService, "userLocationRepo", userLocationRepo);

    ReflectionTestUtils.setField(
        lpnReceivingService, "instructionHelperService", instructionHelperService);
    ReflectionTestUtils.setField(
        lpnReceivingService, "deliveryDocumentHelper", deliveryDocumentHelper);
    ReflectionTestUtils.setField(lpnReceivingService, "userLocationService", userLocationService);
    ReflectionTestUtils.setField(
        lpnReceivingService, "instructionPersisterService", instructionPersisterService);
    ReflectionTestUtils.setField(lpnReceivingService, "containerService", containerService);
    ReflectionTestUtils.setField(lpnReceivingService, "gson", gson);
    ReflectionTestUtils.setField(containerService, "gson", gson);
    ReflectionTestUtils.setField(jmsExceptionContainerPublisher, "jmsPublisher", jmsPublisher);
    ReflectionTestUtils.setField(jmsExceptionContainerPublisher, "gson", gson);
    ReflectionTestUtils.setField(jmsExceptionContainerPublisher, "maasTopics", maasTopics);
    ReflectionTestUtils.setField(
        lpnReceivingService, "tenantSpecificConfigReader", tenantSpecificConfigReader);
    ReflectionTestUtils.setField(
        lpnReceivingService, "exceptionContainerHandlerFactory", exceptionContainerHandlerFactory);
    ReflectionTestUtils.setField(
        dockTagExceptionContainerHandler, "containerService", containerService);
    ReflectionTestUtils.setField(
        overageExceptionContainerHandler, "containerService", containerService);
    ReflectionTestUtils.setField(
        noAllocationExceptionContainerHandler, "containerService", containerService);

    ReflectionTestUtils.setField(
        channelFlipExceptionContainerHandler, "containerService", containerService);

    aclVerificationEventMessage =
        JacksonParser.convertJsonToObject(
            MockACLMessageData.getVerificationEvent(), ACLVerificationEventMessage.class);

    purchaseOrderInfo =
        new PurchaseOrderInfo(
            1234567L,
            "1234567",
            1,
            "{\"sscc\":null,\"orderableGTIN\":\"10074451115207\",\"consumableGTIN\":\"10074451115207\",\"catalogGTIN\":null}");
    try {
      String dataPath =
          new File("../receiving-test/src/main/resources/json/MultiPoPol.json").getCanonicalPath();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @AfterMethod
  public void tearDown() {
    reset(accManagedConfig);
    reset(containerPersisterService);
    reset(fdeService);
    reset(dcFinService);
    reset(receiptService);
    reset(deliveryService);
    reset(labelDataService);
    reset(jmsPublisher);
    reset(appConfig);
    reset(instructionRepository);
    reset(accDeliveryMetaDataService);
    reset(sorterPublisher);
    reset(exceptionContainerHandlerFactory);
    reset(JMSReceiptPublisher);
    reset(deliveryDocumentsSearchHandler);
    userLocationRepo.deleteAll();
  }

  @BeforeMethod()
  public void before() {
    when(configUtils.getConfiguredInstance(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.INSTRUCTION_PUBLISHER,
            MessagePublisher.class))
        .thenReturn(jmsInstructionPublisher);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.SORTER_PUBLISHER,
            SorterPublisher.class))
        .thenReturn(sorterPublisher);
    when(exceptionContainerHandlerFactory.exceptionContainerHandler(any(ContainerException.class)))
        .thenReturn(dockTagExceptionContainerHandler);
    when(configUtils.getConfiguredInstance(
            any(),
            eq(ReceivingConstants.EXCEPTION_CONTAINER_PUBLISHER),
            eq(ReceivingConstants.JMS_EXCEPTION_CONTAINER_PUBLISHER),
            any()))
        .thenReturn(jmsExceptionContainerPublisher);
    doReturn(Boolean.TRUE)
        .when(configUtils)
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.INSTRUCTION_SAVE_ENABLED,
            Boolean.TRUE);
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ACCConstants.ENABLE_FILTER_CANCELLED_PO_FOR_ACL);
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ReceivingConstants.ENABLE_NA_SORTER_DIVERT);
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ReceivingConstants.ENABLE_CF_SORTER_DIVERT);
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ReceivingConstants.ENABLE_XBLOCK_SORTER_DIVERT);
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ReceivingConstants.ENABLE_NO_DELIVERY_DOC_SORTER_DIVERT);
    doReturn(Boolean.FALSE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ACCConstants.ENABLE_INVALID_ALLOCATIONS_EXCEPTION_CONTAINER_PUBLISH);
    doReturn(Boolean.FALSE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ACCConstants.ENABLE_GENERIC_FDE_ERROR_EXCEPTION_CONTAINER_PUBLISH);
    when(configUtils.getConfiguredInstance(
            any(), eq(ReceivingConstants.RECEIPT_EVENT_HANDLER), any()))
        .thenReturn(JMSReceiptPublisher);
    doReturn(deliveryDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            any(), eq(ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER), any());
    when(configUtils.getCcmValue(
            getFacilityNum(), ELIGIBLE_TRANSFER_POS_CCM_CONFIG, DEFAULT_ELIGIBLE_TRANSFER_PO_TYPE))
        .thenReturn(DEFAULT_ELIGIBLE_TRANSFER_PO_TYPE);
    doReturn(ReceivingConstants.PUB_RECEIPTS_EXCEPTION_TOPIC)
        .when(maasTopics)
        .getPubExceptionContainerTopic();
    doReturn(ReceivingConstants.PUB_RECEIPTS_EXCEPTION_TOPIC)
        .when(maasTopics)
        .getPubExceptionContainerTopic();
  }

  private void createUserLocationMapping(
      String locationId, String userId, LocationType locationType) {
    UserLocation userLocation = new UserLocation();
    userLocation.setUserId(userId);
    userLocation.setLocationId(locationId);
    userLocation.setLocationType(locationType);
    userLocation.setCreateTs(new Date());
    userLocationRepo.save(userLocation);
  }

  private DeliveryDocument getGdmPOLineResponse(String poNum, Integer poLine) {

    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setPurchaseReferenceNumber(poNum);
    deliveryDocument.setFinancialReportingGroup("US");
    deliveryDocument.setBaseDivisionCode("WM");
    deliveryDocument.setVendorNumber("125486526");
    deliveryDocument.setDeptNumber("10");
    deliveryDocument.setPurchaseCompanyId("1");
    deliveryDocument.setPurchaseReferenceLegacyType("33");
    deliveryDocument.setPoDCNumber("32987");
    deliveryDocument.setPurchaseReferenceStatus("ACTV");
    deliveryDocument.setTotalPurchaseReferenceQty(10);
    deliveryDocument.setDeliveryStatus(DeliveryStatus.WRK);
    deliveryDocument.setDeliveryLegacyStatus("WRK");

    DeliveryDocumentLine lineItem = new DeliveryDocumentLine();
    lineItem.setPurchaseRefType("CROSSU");
    lineItem.setPurchaseReferenceNumber(poNum);
    lineItem.setPurchaseReferenceLineNumber(poLine);
    lineItem.setPurchaseReferenceLineStatus("ACTIVE");
    lineItem.setItemNbr(566051127L);
    lineItem.setItemUpc("10074451115207");
    lineItem.setCaseUpc("10074451115207");
    lineItem.setTotalOrderQty(10);
    lineItem.setQtyUOM("ZA");
    lineItem.setVendorPack(1);
    lineItem.setWarehousePack(1);
    lineItem.setVendorPackCost(1.99f);
    lineItem.setWarehousePackSell(2.99f);
    lineItem.setWeight(1.25f);
    lineItem.setWeightUom("LB");
    lineItem.setCube(0.432f);
    lineItem.setCubeUom("CF");
    lineItem.setEvent("POS REPLEN");
    lineItem.setPalletHigh(2);
    lineItem.setPalletTie(2);
    lineItem.setIsConveyable(Boolean.TRUE);
    lineItem.setOverageQtyLimit(5);
    lineItem.setColor("WHITE");
    lineItem.setSize("SMALL");
    lineItem.setDescription("Sample item descr1");
    lineItem.setSecondaryDescription("Sample item descr2");
    lineItem.setActiveChannelMethods(new ArrayList<>());
    lineItem.setCurrency(null);
    lineItem.setIsHazmat(Boolean.FALSE);

    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    deliveryDocumentLines.add(lineItem);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);
    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    deliveryDocuments.add(deliveryDocument);

    return deliveryDocument;
  }

  @Test
  public void testReceiveByLPNContainerDoesNotExists() throws ReceivingException, IOException {
    Long deliveryNum = 94769060L;
    String lpn = "c32987000000000000000001";
    String location = "D102A";
    String poNum = "3615852071";
    Integer poLine = 8;
    ACLVerificationEventMessage aclVerificationEventMessage = new ACLVerificationEventMessage();
    aclVerificationEventMessage.setGroupNbr(deliveryNum.toString());
    aclVerificationEventMessage.setLpn(lpn);
    aclVerificationEventMessage.setLocationId(location);
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse(poNum, poLine);
    String OFResponse = MockACLMessageData.getBuildContainerResponseForACLReceiving();
    PossibleUPC possibleUPC =
        PossibleUPC.builder()
            .consumableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc())
            .orderableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getItemUpc())
            .build();
    PurchaseOrderInfo purchaseOrderInfo =
        new PurchaseOrderInfo(
            deliveryNum, poNum, poLine, JacksonParser.writeValueAsString(possibleUPC));

    // create user in DB
    createUserLocationMapping(location, "sysadmin.s32818", LocationType.ONLINE);
    createUserLocationMapping(location, "rcv.s32818", LocationType.ONLINE);

    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(null);
    when(labelDataService.getPurchaseOrderInfoFromLabelData(deliveryNum, lpn))
        .thenReturn(purchaseOrderInfo);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(gdmPOLineResponse));

    when(receiptService.getReceivedQtyByPoAndPoLine(poNum, poLine)).thenReturn(0L);

    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenReturn(OFResponse);

    LocationInfo locationInfo = new LocationInfo();
    locationInfo.setIsOnline(Boolean.TRUE);
    when(containerPersisterService.saveContainer(any()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.FALSE);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);
    try {
      lpnReceivingService.receiveByLPN(
          aclVerificationEventMessage.getLpn(),
          Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
          aclVerificationEventMessage.getLocationId());
    } catch (ReceivingException e) {
      fail("No exception is expected");
    }

    verify(containerPersisterService, times(1)).getContainerDetails(lpn);
    verify(labelDataService, times(1)).getPurchaseOrderInfoFromLabelData(deliveryNum, lpn);

    // verify headers are created properly and GDM is called
    ArgumentCaptor<HttpHeaders> captor = ArgumentCaptor.forClass(HttpHeaders.class);
    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            captor.capture());
    HttpHeaders httpHeaders = captor.getValue();
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM),
        TenantContext.getFacilityNum().toString());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE),
        TenantContext.getFacilityCountryCode());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY),
        TenantContext.getCorrelationId());

    verify(deliveryService, times(0)).reOpenDelivery(any(), any());
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(poNum, poLine);

    // verify OF request payload
    ArgumentCaptor<FdeCreateContainerRequest> fdeCaptor =
        ArgumentCaptor.forClass(FdeCreateContainerRequest.class);
    verify(fdeService, times(1)).receive(fdeCaptor.capture(), eq(httpHeaders));
    assertEquals(
        JacksonParser.writeValueAsStringExcludeNull(fdeCaptor.getValue()).replaceAll("\\s+", ""),
        MockACLMessageData.getBuildContainerRequestForACLReceiving().replaceAll("\\s+", ""));

    // verify WFM contract for update & complete
    ArgumentCaptor<ReceivingJMSEvent> wfmPublishCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(3))
        .publish(
            eq(ReceivingConstants.PUB_INSTRUCTION_TOPIC),
            wfmPublishCaptor.capture(),
            eq(Boolean.FALSE));
    List<ReceivingJMSEvent> receivingJMSEventList = wfmPublishCaptor.getAllValues();
    String WFMUpdateMessage = receivingJMSEventList.get(1).getMessageBody();
    String WFMCompleteMessage = receivingJMSEventList.get(2).getMessageBody();

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclWFMUpdateMessageSchema.json")
                            .getCanonicalPath()))),
            WFMUpdateMessage));
    // assertTrue(WFMUpdateMessage.contains("\"userId\":\"rcv\""));
    // assertTrue(WFMUpdateMessage.contains("\"activityName\":\"ACL-DOOR\""));

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclWFMCompleteMessageSchema.json")
                            .getCanonicalPath()))),
            WFMCompleteMessage));

    // verify container was created properly
    ArgumentCaptor<Container> containerCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerPersisterService, times(1)).saveContainer(containerCaptor.capture());

    Container savedContainer = containerCaptor.getValue();
    assertNotNull(savedContainer.getCompleteTs());
    assertEquals(savedContainer.getTrackingId(), lpn);
    assertNull(savedContainer.getParentTrackingId());
    assertEquals(savedContainer.getContainerItems().size(), 1);
    // assertEquals(savedContainer.getCreateUser(), "rcv");
    assertTrue(CollectionUtils.isEmpty(savedContainer.getChildContainers()));
    assertTrue(savedContainer.getIsConveyable());
    assertTrue(savedContainer.getOnConveyor());

    // verify instruction was saved
    ArgumentCaptor<Instruction> instructionCaptor = ArgumentCaptor.forClass(Instruction.class);
    verify(instructionRepository, times(1)).save(instructionCaptor.capture());
    Instruction savedInstruction = instructionCaptor.getValue();
    assertEquals(savedInstruction.getActivityName(), "ACL");

    // verify receipts were saved
    // verify(receiptService,
    // times(1)).createReceiptsFromInstruction(any(UpdateInstructionRequest.class), eq(null),
    // eq("rcv"));
    // validate contract for published container
    ArgumentCaptor<ContainerDTO> containerPublishCaptor =
        ArgumentCaptor.forClass(ContainerDTO.class);
    verify(JMSReceiptPublisher, times(1)).publish(containerPublishCaptor.capture(), any());
    String publishedContainer = gson.toJson(containerPublishCaptor.getValue());

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclReceivingContainerMessageSchema.json")
                            .getCanonicalPath()))),
            publishedContainer));
  }

  @Test
  public void testReceiveByLPNContainerDoesNotExistsAndChannelFlip()
      throws ReceivingException, IOException {
    Long deliveryNum = 94769060L;
    String lpn = "c32987000000000000000001";
    String location = "D102A";
    String poNum = "3615852071";
    Integer poLine = 8;
    ACLVerificationEventMessage aclVerificationEventMessage = new ACLVerificationEventMessage();
    aclVerificationEventMessage.setGroupNbr(deliveryNum.toString());
    aclVerificationEventMessage.setLpn(lpn);
    aclVerificationEventMessage.setLocationId(location);
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse(poNum, poLine);
    gdmPOLineResponse
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseRefType(PurchaseReferenceType.SSTKU.toString());
    gdmPOLineResponse
        .getDeliveryDocumentLines()
        .get(0)
        .setActiveChannelMethods(Arrays.asList(PurchaseReferenceType.CROSSU.toString()));
    String OFResponse = MockACLMessageData.getBuildContainerResponseForACLReceiving();
    PossibleUPC possibleUPC =
        PossibleUPC.builder()
            .consumableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc())
            .orderableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getItemUpc())
            .build();
    PurchaseOrderInfo purchaseOrderInfo =
        new PurchaseOrderInfo(
            deliveryNum, poNum, poLine, JacksonParser.writeValueAsString(possibleUPC));

    // create user in DB
    createUserLocationMapping(location, "sysadmin.s32818", LocationType.FLR_LINE);
    createUserLocationMapping(location, "rcv.s32818", LocationType.FLR_LINE);

    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(null);
    when(labelDataService.getPurchaseOrderInfoFromLabelData(deliveryNum, lpn))
        .thenReturn(purchaseOrderInfo);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(gdmPOLineResponse));

    when(receiptService.getReceivedQtyByPoAndPoLine("4763030227", 1)).thenReturn(0L);

    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenReturn(OFResponse);

    LocationInfo locationInfo = new LocationInfo();
    locationInfo.setIsOnline(Boolean.TRUE);
    when(containerPersisterService.saveContainer(any()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.FALSE);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);
    try {
      lpnReceivingService.receiveByLPN(
          aclVerificationEventMessage.getLpn(),
          Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
          aclVerificationEventMessage.getLocationId());
    } catch (ReceivingException e) {
      fail("No exception is expected");
    }

    verify(containerPersisterService, times(1)).getContainerDetails(lpn);
    verify(labelDataService, times(1)).getPurchaseOrderInfoFromLabelData(deliveryNum, lpn);

    // verify headers are created properly and GDM is called
    ArgumentCaptor<HttpHeaders> captor = ArgumentCaptor.forClass(HttpHeaders.class);
    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            captor.capture());

    HttpHeaders httpHeaders = captor.getValue();
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM),
        TenantContext.getFacilityNum().toString());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE),
        TenantContext.getFacilityCountryCode());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY),
        TenantContext.getCorrelationId());

    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(poNum, poLine);

    // verify OF request payload
    ArgumentCaptor<FdeCreateContainerRequest> fdeCaptor =
        ArgumentCaptor.forClass(FdeCreateContainerRequest.class);
    verify(fdeService, times(1)).receive(fdeCaptor.capture(), eq(httpHeaders));
    assertEquals(
        JacksonParser.writeValueAsStringExcludeNull(fdeCaptor.getValue()).replaceAll("\\s+", ""),
        MockACLMessageData.getBuildContainerRequestForACLReceiving().replaceAll("\\s+", ""));

    // verify WFM contract for update & complete
    ArgumentCaptor<ReceivingJMSEvent> wfmPublishCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(3))
        .publish(
            eq(ReceivingConstants.PUB_INSTRUCTION_TOPIC),
            wfmPublishCaptor.capture(),
            eq(Boolean.FALSE));
    List<ReceivingJMSEvent> receivingJMSEventList = wfmPublishCaptor.getAllValues();
    String WFMUpdateMessage = receivingJMSEventList.get(1).getMessageBody();
    String WFMCompleteMessage = receivingJMSEventList.get(2).getMessageBody();

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclWFMUpdateMessageSchema.json")
                            .getCanonicalPath()))),
            WFMUpdateMessage));
    // assertTrue(WFMUpdateMessage.contains("\"userId\":\"rcv\""));
    // assertTrue(WFMUpdateMessage.contains("\"activityName\":\"ACL-FLR-LINE\""));

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclWFMCompleteMessageSchema.json")
                            .getCanonicalPath()))),
            WFMCompleteMessage));

    // verify container was created properly
    ArgumentCaptor<Container> containerCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerPersisterService, times(1)).saveContainer(containerCaptor.capture());

    Container savedContainer = containerCaptor.getValue();
    assertNotNull(savedContainer.getCompleteTs());
    assertEquals(savedContainer.getTrackingId(), lpn);
    assertNull(savedContainer.getParentTrackingId());
    assertEquals(savedContainer.getContainerItems().size(), 1);
    // assertEquals(savedContainer.getCreateUser(), "rcv");
    assertTrue(CollectionUtils.isEmpty(savedContainer.getChildContainers()));
    assertTrue(savedContainer.getIsConveyable());
    assertTrue(savedContainer.getOnConveyor());

    // verify instruction was saved
    ArgumentCaptor<Instruction> instructionCaptor = ArgumentCaptor.forClass(Instruction.class);
    verify(instructionRepository, times(1)).save(instructionCaptor.capture());
    Instruction savedInstruction = instructionCaptor.getValue();
    assertEquals(savedInstruction.getActivityName(), "ACL");

    // verify receipts were saved
    // verify(receiptService,
    // times(1)).createReceiptsFromInstruction(any(UpdateInstructionRequest.class), eq(null),
    // eq("rcv"));
    // validate contract for published container
    ArgumentCaptor<ContainerDTO> containerPublishCaptor =
        ArgumentCaptor.forClass(ContainerDTO.class);
    verify(JMSReceiptPublisher, times(1)).publish(containerPublishCaptor.capture(), any());
    String publishedContainer = gson.toJson(containerPublishCaptor.getValue());
    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclReceivingContainerMessageSchema.json")
                            .getCanonicalPath()))),
            publishedContainer));
  }

  @Test
  public void testReceiveByLPNDeliveryClosed() throws IOException, ReceivingException {
    Long deliveryNum = 94769060L;
    String lpn = "c32987000000000000000001";
    String location = "D102A";
    String poNum = "3615852071";
    Integer poLine = 8;
    ACLVerificationEventMessage aclVerificationEventMessage = new ACLVerificationEventMessage();
    aclVerificationEventMessage.setGroupNbr(deliveryNum.toString());
    aclVerificationEventMessage.setLpn(lpn);
    aclVerificationEventMessage.setLocationId(location);
    // set deliveryStatus to PNDFNL
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse(poNum, poLine);
    gdmPOLineResponse.setDeliveryStatus(DeliveryStatus.PNDFNL);
    gdmPOLineResponse.setDeliveryLegacyStatus(DeliveryStatus.PNDFNL.name());
    String OFResponse = MockACLMessageData.getBuildContainerResponseForACLReceiving();
    PossibleUPC possibleUPC =
        PossibleUPC.builder()
            .consumableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc())
            .orderableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getItemUpc())
            .build();
    PurchaseOrderInfo purchaseOrderInfo =
        new PurchaseOrderInfo(
            deliveryNum, poNum, poLine, JacksonParser.writeValueAsString(possibleUPC));

    // create user in DB
    createUserLocationMapping(location, "rcv.s32818", LocationType.ONLINE);
    createUserLocationMapping(location, "sysadmin.s32818", LocationType.ONLINE);

    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(null);
    when(labelDataService.getPurchaseOrderInfoFromLabelData(deliveryNum, lpn))
        .thenReturn(purchaseOrderInfo);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(gdmPOLineResponse));

    when(receiptService.getReceivedQtyByPoAndPoLine("4763030227", 1)).thenReturn(0L);

    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenReturn(OFResponse);

    LocationInfo locationInfo = new LocationInfo();
    locationInfo.setIsOnline(Boolean.TRUE);
    when(containerPersisterService.saveContainer(any()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.FALSE);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);
    when(configUtils.getConfiguredInstance(
            any(), eq(ReceivingConstants.DELIVERY_METADATA_SERVICE), any()))
        .thenReturn(accDeliveryMetaDataService);
    try {
      lpnReceivingService.receiveByLPN(
          aclVerificationEventMessage.getLpn(),
          Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
          aclVerificationEventMessage.getLocationId());
    } catch (ReceivingException e) {
      fail("No exception is expected");
    }

    verify(containerPersisterService, times(1)).getContainerDetails(lpn);
    verify(labelDataService, times(1)).getPurchaseOrderInfoFromLabelData(deliveryNum, lpn);

    // verify headers are created properly and GDM is called
    ArgumentCaptor<HttpHeaders> captor = ArgumentCaptor.forClass(HttpHeaders.class);
    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            captor.capture());

    HttpHeaders httpHeaders = captor.getValue();
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM),
        TenantContext.getFacilityNum().toString());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE),
        TenantContext.getFacilityCountryCode());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY),
        TenantContext.getCorrelationId());

    verify(deliveryService, times(1)).reOpenDelivery(eq(deliveryNum), any(HttpHeaders.class));
    verify(accDeliveryMetaDataService, times(1))
        .findAndUpdateDeliveryStatus(deliveryNum.toString(), DeliveryStatus.SYS_REO);
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(poNum, poLine);

    // verify OF request payload
    ArgumentCaptor<FdeCreateContainerRequest> fdeCaptor =
        ArgumentCaptor.forClass(FdeCreateContainerRequest.class);
    verify(fdeService, times(1)).receive(fdeCaptor.capture(), eq(httpHeaders));
    assertEquals(
        JacksonParser.writeValueAsStringExcludeNull(fdeCaptor.getValue()).replaceAll("\\s+", ""),
        MockACLMessageData.getBuildContainerRequestForACLReceiving().replaceAll("\\s+", ""));

    // verify WFM contract for update & complete
    ArgumentCaptor<ReceivingJMSEvent> wfmPublishCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(3))
        .publish(
            eq(ReceivingConstants.PUB_INSTRUCTION_TOPIC),
            wfmPublishCaptor.capture(),
            eq(Boolean.FALSE));
    List<ReceivingJMSEvent> receivingJMSEventList = wfmPublishCaptor.getAllValues();
    String WFMUpdateMessage = receivingJMSEventList.get(1).getMessageBody();
    String WFMCompleteMessage = receivingJMSEventList.get(2).getMessageBody();

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclWFMUpdateMessageSchema.json")
                            .getCanonicalPath()))),
            WFMUpdateMessage));
    // assertTrue(WFMUpdateMessage.contains("\"userId\":\"sysadmin\""));
    // assertTrue(WFMUpdateMessage.contains("\"activityName\":\"ACL-DOOR\""));

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclWFMCompleteMessageSchema.json")
                            .getCanonicalPath()))),
            WFMCompleteMessage));

    // verify container was created properly
    ArgumentCaptor<Container> containerCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerPersisterService, times(1)).saveContainer(containerCaptor.capture());

    Container savedContainer = containerCaptor.getValue();
    assertNotNull(savedContainer.getCompleteTs());
    assertEquals(savedContainer.getTrackingId(), lpn);
    assertNull(savedContainer.getParentTrackingId());
    assertEquals(savedContainer.getContainerItems().size(), 1);
    //    assertEquals(savedContainer.getCreateUser(), "sysadmin");
    assertTrue(CollectionUtils.isEmpty(savedContainer.getChildContainers()));
    assertTrue(savedContainer.getIsConveyable());
    assertTrue(savedContainer.getOnConveyor());

    // verify instruction was saved
    ArgumentCaptor<Instruction> instructionCaptor = ArgumentCaptor.forClass(Instruction.class);
    verify(instructionRepository, times(1)).save(instructionCaptor.capture());
    Instruction savedInstruction = instructionCaptor.getValue();
    assertEquals(savedInstruction.getActivityName(), "ACL");

    // verify receipts were saved
    verify(receiptService, times(1))
        .createReceiptsFromInstruction(any(UpdateInstructionRequest.class), eq(null), any());
    // validate contract for published container
    ArgumentCaptor<ContainerDTO> containerPublishCaptor =
        ArgumentCaptor.forClass(ContainerDTO.class);
    verify(JMSReceiptPublisher, times(1)).publish(containerPublishCaptor.capture(), any());
    String publishedContainer = gson.toJson(containerPublishCaptor.getValue());
    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclReceivingContainerMessageSchema.json")
                            .getCanonicalPath()))),
            publishedContainer));
  }

  @Test
  public void testReceiveByLPNDeliveryPNDPT() throws IOException, ReceivingException {
    Long deliveryNum = 94769060L;
    String lpn = "c32987000000000000000001";
    String location = "D102A";
    String poNum = "3615852071";
    Integer poLine = 8;
    ACLVerificationEventMessage aclVerificationEventMessage = new ACLVerificationEventMessage();
    aclVerificationEventMessage.setGroupNbr(deliveryNum.toString());
    aclVerificationEventMessage.setLpn(lpn);
    aclVerificationEventMessage.setLocationId(location);
    // set deliveryStatus to OPN
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse(poNum, poLine);
    gdmPOLineResponse.setDeliveryStatus(DeliveryStatus.OPN);
    gdmPOLineResponse.setDeliveryLegacyStatus(DeliveryStatus.PNDPT.name());
    String OFResponse = MockACLMessageData.getBuildContainerResponseForACLReceiving();
    PossibleUPC possibleUPC =
        PossibleUPC.builder()
            .consumableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc())
            .orderableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getItemUpc())
            .build();
    PurchaseOrderInfo purchaseOrderInfo =
        new PurchaseOrderInfo(
            deliveryNum, poNum, poLine, JacksonParser.writeValueAsString(possibleUPC));

    // create user in DB
    createUserLocationMapping(location, "sysadmin.s32818", LocationType.ONLINE);
    createUserLocationMapping(location, "rcv.s32818", LocationType.ONLINE);

    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(null);
    when(labelDataService.getPurchaseOrderInfoFromLabelData(deliveryNum, lpn))
        .thenReturn(purchaseOrderInfo);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(gdmPOLineResponse));

    when(receiptService.getReceivedQtyByPoAndPoLine("4763030227", 1)).thenReturn(0L);

    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenReturn(OFResponse);

    LocationInfo locationInfo = new LocationInfo();
    locationInfo.setIsOnline(Boolean.TRUE);

    when(containerPersisterService.saveContainer(any()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.FALSE);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);
    when(configUtils.getConfiguredInstance(
            any(), eq(ReceivingConstants.DELIVERY_METADATA_SERVICE), any()))
        .thenReturn(accDeliveryMetaDataService);
    try {
      lpnReceivingService.receiveByLPN(
          aclVerificationEventMessage.getLpn(),
          Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
          aclVerificationEventMessage.getLocationId());
    } catch (ReceivingException e) {
      fail("No exception is expected");
    }

    verify(containerPersisterService, times(1)).getContainerDetails(lpn);
    verify(labelDataService, times(1)).getPurchaseOrderInfoFromLabelData(deliveryNum, lpn);

    // verify headers are created properly and GDM is called
    ArgumentCaptor<HttpHeaders> captor = ArgumentCaptor.forClass(HttpHeaders.class);
    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            captor.capture());

    HttpHeaders httpHeaders = captor.getValue();
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM),
        TenantContext.getFacilityNum().toString());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE),
        TenantContext.getFacilityCountryCode());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY),
        TenantContext.getCorrelationId());

    verify(deliveryService, times(1)).reOpenDelivery(eq(deliveryNum), any(HttpHeaders.class));
    verify(accDeliveryMetaDataService, times(1))
        .findAndUpdateDeliveryStatus(deliveryNum.toString(), DeliveryStatus.SYS_REO);
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(poNum, poLine);

    // verify OF request payload
    ArgumentCaptor<FdeCreateContainerRequest> fdeCaptor =
        ArgumentCaptor.forClass(FdeCreateContainerRequest.class);
    verify(fdeService, times(1)).receive(fdeCaptor.capture(), eq(httpHeaders));
    assertEquals(
        JacksonParser.writeValueAsStringExcludeNull(fdeCaptor.getValue()).replaceAll("\\s+", ""),
        MockACLMessageData.getBuildContainerRequestForACLReceiving().replaceAll("\\s+", ""));

    // verify WFM contract for update & complete
    ArgumentCaptor<ReceivingJMSEvent> wfmPublishCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(3))
        .publish(
            eq(ReceivingConstants.PUB_INSTRUCTION_TOPIC),
            wfmPublishCaptor.capture(),
            eq(Boolean.FALSE));
    List<ReceivingJMSEvent> receivingJMSEventList = wfmPublishCaptor.getAllValues();
    String WFMUpdateMessage = receivingJMSEventList.get(1).getMessageBody();
    String WFMCompleteMessage = receivingJMSEventList.get(2).getMessageBody();

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclWFMUpdateMessageSchema.json")
                            .getCanonicalPath()))),
            WFMUpdateMessage));
    // assertTrue(WFMUpdateMessage.contains("\"userId\":\"rcv\""), WFMUpdateMessage);
    // assertTrue(WFMUpdateMessage.contains("\"activityName\":\"ACL-DOOR\""));

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclWFMCompleteMessageSchema.json")
                            .getCanonicalPath()))),
            WFMCompleteMessage));

    // verify container was created properly
    ArgumentCaptor<Container> containerCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerPersisterService, times(1)).saveContainer(containerCaptor.capture());

    Container savedContainer = containerCaptor.getValue();
    assertNotNull(savedContainer.getCompleteTs());
    assertEquals(savedContainer.getTrackingId(), lpn);
    assertNull(savedContainer.getParentTrackingId());
    assertEquals(savedContainer.getContainerItems().size(), 1);
    // assertEquals(savedContainer.getCreateUser(), "rcv");
    assertTrue(CollectionUtils.isEmpty(savedContainer.getChildContainers()));
    assertTrue(savedContainer.getIsConveyable());
    assertTrue(savedContainer.getOnConveyor());

    // verify instruction was saved
    ArgumentCaptor<Instruction> instructionCaptor = ArgumentCaptor.forClass(Instruction.class);
    verify(instructionRepository, times(1)).save(instructionCaptor.capture());
    Instruction savedInstruction = instructionCaptor.getValue();
    assertEquals(savedInstruction.getActivityName(), "ACL");

    // verify receipts were saved
    // verify(receiptService,
    // times(1)).createReceiptsFromInstruction(any(UpdateInstructionRequest.class), eq(null),
    // eq("rcv"));
    // validate contract for published container
    ArgumentCaptor<ContainerDTO> containerPublishCaptor =
        ArgumentCaptor.forClass(ContainerDTO.class);
    verify(JMSReceiptPublisher, times(1)).publish(containerPublishCaptor.capture(), any());
    String publishedContainer = gson.toJson(containerPublishCaptor.getValue());
    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclReceivingContainerMessageSchema.json")
                            .getCanonicalPath()))),
            publishedContainer));
  }

  @Test
  public void testReceiveByLPNDeliveryLegacyOPN() throws IOException, ReceivingException {
    Long deliveryNum = 94769060L;
    String lpn = "c32987000000000000000001";
    String location = "D102A";
    String poNum = "3615852071";
    Integer poLine = 8;
    ACLVerificationEventMessage aclVerificationEventMessage = new ACLVerificationEventMessage();
    aclVerificationEventMessage.setGroupNbr(deliveryNum.toString());
    aclVerificationEventMessage.setLpn(lpn);
    aclVerificationEventMessage.setLocationId(location);
    // set deliveryStatus to OPN
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse(poNum, poLine);
    gdmPOLineResponse.setDeliveryStatus(DeliveryStatus.OPN);
    gdmPOLineResponse.setDeliveryLegacyStatus(DeliveryStatus.OPN.name());
    String OFResponse = MockACLMessageData.getBuildContainerResponseForACLReceiving();
    PossibleUPC possibleUPC =
        PossibleUPC.builder()
            .consumableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc())
            .orderableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getItemUpc())
            .build();
    PurchaseOrderInfo purchaseOrderInfo =
        new PurchaseOrderInfo(
            deliveryNum, poNum, poLine, JacksonParser.writeValueAsString(possibleUPC));

    // create user in DB
    createUserLocationMapping(location, "sysadmin.s32818", LocationType.ONLINE);
    createUserLocationMapping(location, "rcv.s32818", LocationType.ONLINE);

    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(null);
    when(labelDataService.getPurchaseOrderInfoFromLabelData(deliveryNum, lpn))
        .thenReturn(purchaseOrderInfo);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(gdmPOLineResponse));

    when(receiptService.getReceivedQtyByPoAndPoLine("4763030227", 1)).thenReturn(0L);

    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenReturn(OFResponse);

    LocationInfo locationInfo = new LocationInfo();
    locationInfo.setIsOnline(Boolean.TRUE);

    when(containerPersisterService.saveContainer(any()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.FALSE);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);
    try {
      lpnReceivingService.receiveByLPN(
          aclVerificationEventMessage.getLpn(),
          Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
          aclVerificationEventMessage.getLocationId());
    } catch (ReceivingException e) {
      fail("No exception is expected");
    }

    verify(containerPersisterService, times(1)).getContainerDetails(lpn);
    verify(labelDataService, times(1)).getPurchaseOrderInfoFromLabelData(deliveryNum, lpn);

    // verify headers are created properly and GDM is called
    ArgumentCaptor<HttpHeaders> captor = ArgumentCaptor.forClass(HttpHeaders.class);
    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            captor.capture());

    HttpHeaders httpHeaders = captor.getValue();
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM),
        TenantContext.getFacilityNum().toString());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE),
        TenantContext.getFacilityCountryCode());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY),
        TenantContext.getCorrelationId());

    verify(deliveryService, times(0)).reOpenDelivery(eq(deliveryNum), any(HttpHeaders.class));
    verify(accDeliveryMetaDataService, times(0))
        .findAndUpdateDeliveryStatus(deliveryNum.toString(), DeliveryStatus.SYS_REO);
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(poNum, poLine);

    // verify OF request payload
    ArgumentCaptor<FdeCreateContainerRequest> fdeCaptor =
        ArgumentCaptor.forClass(FdeCreateContainerRequest.class);
    verify(fdeService, times(1)).receive(fdeCaptor.capture(), eq(httpHeaders));
    assertEquals(
        JacksonParser.writeValueAsStringExcludeNull(fdeCaptor.getValue()).replaceAll("\\s+", ""),
        MockACLMessageData.getBuildContainerRequestForACLReceiving().replaceAll("\\s+", ""));

    // verify WFM contract for update & complete
    ArgumentCaptor<ReceivingJMSEvent> wfmPublishCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(3))
        .publish(
            eq(ReceivingConstants.PUB_INSTRUCTION_TOPIC),
            wfmPublishCaptor.capture(),
            eq(Boolean.FALSE));
    List<ReceivingJMSEvent> receivingJMSEventList = wfmPublishCaptor.getAllValues();
    String WFMUpdateMessage = receivingJMSEventList.get(1).getMessageBody();
    String WFMCompleteMessage = receivingJMSEventList.get(2).getMessageBody();

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclWFMUpdateMessageSchema.json")
                            .getCanonicalPath()))),
            WFMUpdateMessage));
    // assertTrue(WFMUpdateMessage.contains("\"userId\":\"rcv\""), WFMUpdateMessage);
    // assertTrue(WFMUpdateMessage.contains("\"activityName\":\"ACL-DOOR\""));

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclWFMCompleteMessageSchema.json")
                            .getCanonicalPath()))),
            WFMCompleteMessage));

    // verify container was created properly
    ArgumentCaptor<Container> containerCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerPersisterService, times(1)).saveContainer(containerCaptor.capture());

    Container savedContainer = containerCaptor.getValue();
    assertNotNull(savedContainer.getCompleteTs());
    assertEquals(savedContainer.getTrackingId(), lpn);
    assertNull(savedContainer.getParentTrackingId());
    assertEquals(savedContainer.getContainerItems().size(), 1);
    // assertEquals(savedContainer.getCreateUser(), "rcv");
    assertTrue(CollectionUtils.isEmpty(savedContainer.getChildContainers()));
    assertTrue(savedContainer.getIsConveyable());
    assertTrue(savedContainer.getOnConveyor());

    // verify instruction was saved
    ArgumentCaptor<Instruction> instructionCaptor = ArgumentCaptor.forClass(Instruction.class);
    verify(instructionRepository, times(1)).save(instructionCaptor.capture());
    Instruction savedInstruction = instructionCaptor.getValue();
    assertEquals(savedInstruction.getActivityName(), "ACL");

    // verify receipts were saved
    // verify(receiptService,
    // times(1)).createReceiptsFromInstruction(any(UpdateInstructionRequest.class), eq(null),
    // eq("rcv"));
    // validate contract for published container
    ArgumentCaptor<ContainerDTO> containerPublishCaptor =
        ArgumentCaptor.forClass(ContainerDTO.class);
    verify(JMSReceiptPublisher, times(1)).publish(containerPublishCaptor.capture(), any());
    String publishedContainer = gson.toJson(containerPublishCaptor.getValue());
    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclReceivingContainerMessageSchema.json")
                            .getCanonicalPath()))),
            publishedContainer));
  }

  @Test
  public void testReceiveByLPNDeliveryLegacyREO() throws IOException, ReceivingException {
    Long deliveryNum = 94769060L;
    String lpn = "c32987000000000000000001";
    String location = "D102A";
    String poNum = "3615852071";
    Integer poLine = 8;
    ACLVerificationEventMessage aclVerificationEventMessage = new ACLVerificationEventMessage();
    aclVerificationEventMessage.setGroupNbr(deliveryNum.toString());
    aclVerificationEventMessage.setLpn(lpn);
    aclVerificationEventMessage.setLocationId(location);
    // set deliveryStatus to OPN
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse(poNum, poLine);
    gdmPOLineResponse.setDeliveryStatus(DeliveryStatus.OPN);
    gdmPOLineResponse.setDeliveryLegacyStatus(DeliveryStatus.REO.name());
    String OFResponse = MockACLMessageData.getBuildContainerResponseForACLReceiving();
    PossibleUPC possibleUPC =
        PossibleUPC.builder()
            .consumableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc())
            .orderableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getItemUpc())
            .build();
    PurchaseOrderInfo purchaseOrderInfo =
        new PurchaseOrderInfo(
            deliveryNum, poNum, poLine, JacksonParser.writeValueAsString(possibleUPC));

    // create user in DB
    createUserLocationMapping(location, "sysadmin.s32818", LocationType.ONLINE);
    createUserLocationMapping(location, "rcv.s32818", LocationType.ONLINE);

    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(null);
    when(labelDataService.getPurchaseOrderInfoFromLabelData(deliveryNum, lpn))
        .thenReturn(purchaseOrderInfo);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(gdmPOLineResponse));

    when(receiptService.getReceivedQtyByPoAndPoLine("4763030227", 1)).thenReturn(0L);

    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenReturn(OFResponse);

    LocationInfo locationInfo = new LocationInfo();
    locationInfo.setIsOnline(Boolean.TRUE);

    when(containerPersisterService.saveContainer(any()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.FALSE);
    try {
      lpnReceivingService.receiveByLPN(
          aclVerificationEventMessage.getLpn(),
          Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
          aclVerificationEventMessage.getLocationId());
    } catch (ReceivingException e) {
      fail("No exception is expected");
    }

    verify(containerPersisterService, times(1)).getContainerDetails(lpn);
    verify(labelDataService, times(1)).getPurchaseOrderInfoFromLabelData(deliveryNum, lpn);

    // verify headers are created properly and GDM is called
    ArgumentCaptor<HttpHeaders> captor = ArgumentCaptor.forClass(HttpHeaders.class);
    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            captor.capture());

    HttpHeaders httpHeaders = captor.getValue();
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM),
        TenantContext.getFacilityNum().toString());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE),
        TenantContext.getFacilityCountryCode());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY),
        TenantContext.getCorrelationId());

    verify(deliveryService, times(0)).reOpenDelivery(eq(deliveryNum), any(HttpHeaders.class));
    verify(accDeliveryMetaDataService, times(0))
        .findAndUpdateDeliveryStatus(deliveryNum.toString(), DeliveryStatus.SYS_REO);
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(poNum, poLine);

    // verify OF request payload
    ArgumentCaptor<FdeCreateContainerRequest> fdeCaptor =
        ArgumentCaptor.forClass(FdeCreateContainerRequest.class);
    verify(fdeService, times(1)).receive(fdeCaptor.capture(), eq(httpHeaders));
    assertEquals(
        JacksonParser.writeValueAsStringExcludeNull(fdeCaptor.getValue()).replaceAll("\\s+", ""),
        MockACLMessageData.getBuildContainerRequestForACLReceiving().replaceAll("\\s+", ""));

    // verify WFM contract for update & complete
    ArgumentCaptor<ReceivingJMSEvent> wfmPublishCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(3))
        .publish(
            eq(ReceivingConstants.PUB_INSTRUCTION_TOPIC),
            wfmPublishCaptor.capture(),
            eq(Boolean.FALSE));
    List<ReceivingJMSEvent> receivingJMSEventList = wfmPublishCaptor.getAllValues();
    String WFMUpdateMessage = receivingJMSEventList.get(1).getMessageBody();
    String WFMCompleteMessage = receivingJMSEventList.get(2).getMessageBody();

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclWFMUpdateMessageSchema.json")
                            .getCanonicalPath()))),
            WFMUpdateMessage));
    // assertTrue(WFMUpdateMessage.contains("\"userId\":\"rcv\""));
    // assertTrue(WFMUpdateMessage.contains("\"activityName\":\"ACL-DOOR\""));

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclWFMCompleteMessageSchema.json")
                            .getCanonicalPath()))),
            WFMCompleteMessage));

    // verify container was created properly
    ArgumentCaptor<Container> containerCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerPersisterService, times(1)).saveContainer(containerCaptor.capture());

    Container savedContainer = containerCaptor.getValue();
    assertNotNull(savedContainer.getCompleteTs());
    assertEquals(savedContainer.getTrackingId(), lpn);
    assertNull(savedContainer.getParentTrackingId());
    assertEquals(savedContainer.getContainerItems().size(), 1);
    // assertEquals(savedContainer.getCreateUser(), "rcv");
    assertTrue(CollectionUtils.isEmpty(savedContainer.getChildContainers()));
    assertTrue(savedContainer.getIsConveyable());
    assertTrue(savedContainer.getOnConveyor());

    // verify instruction was saved
    ArgumentCaptor<Instruction> instructionCaptor = ArgumentCaptor.forClass(Instruction.class);
    verify(instructionRepository, times(1)).save(instructionCaptor.capture());
    Instruction savedInstruction = instructionCaptor.getValue();
    assertEquals(savedInstruction.getActivityName(), "ACL");

    // verify receipts were saved
    // verify(receiptService,
    // times(1)).createReceiptsFromInstruction(any(UpdateInstructionRequest.class), eq(null),
    // eq("rcv"));
    // validate contract for published container
    ArgumentCaptor<ContainerDTO> containerPublishCaptor =
        ArgumentCaptor.forClass(ContainerDTO.class);
    verify(JMSReceiptPublisher, times(1)).publish(containerPublishCaptor.capture(), any());
    String publishedContainer = gson.toJson(containerPublishCaptor.getValue());
    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclReceivingContainerMessageSchema.json")
                            .getCanonicalPath()))),
            publishedContainer));
  }

  @Test
  public void testReceiveByLPNDeliveryPNDDT() throws IOException, ReceivingException {
    Long deliveryNum = 94769060L;
    String lpn = "c32987000000000000000001";
    String location = "D102A";
    String poNum = "3615852071";
    Integer poLine = 8;
    ACLVerificationEventMessage aclVerificationEventMessage = new ACLVerificationEventMessage();
    aclVerificationEventMessage.setGroupNbr(deliveryNum.toString());
    aclVerificationEventMessage.setLpn(lpn);
    aclVerificationEventMessage.setLocationId(location);
    // set deliveryStatus to OPN
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse(poNum, poLine);
    gdmPOLineResponse.setDeliveryStatus(DeliveryStatus.OPN);
    gdmPOLineResponse.setDeliveryLegacyStatus("PNDDT");
    String OFResponse = MockACLMessageData.getBuildContainerResponseForACLReceiving();
    PossibleUPC possibleUPC =
        PossibleUPC.builder()
            .consumableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc())
            .orderableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getItemUpc())
            .build();
    PurchaseOrderInfo purchaseOrderInfo =
        new PurchaseOrderInfo(
            deliveryNum, poNum, poLine, JacksonParser.writeValueAsString(possibleUPC));

    // create user in DB
    createUserLocationMapping(location, "sysadmin.s32818", LocationType.ONLINE);
    createUserLocationMapping(location, "rcv.s32818", LocationType.ONLINE);

    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(null);
    when(labelDataService.getPurchaseOrderInfoFromLabelData(deliveryNum, lpn))
        .thenReturn(purchaseOrderInfo);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(gdmPOLineResponse));

    when(receiptService.getReceivedQtyByPoAndPoLine("4763030227", 1)).thenReturn(0L);

    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenReturn(OFResponse);

    LocationInfo locationInfo = new LocationInfo();
    locationInfo.setIsOnline(Boolean.TRUE);

    when(containerPersisterService.saveContainer(any()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.FALSE);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);
    try {
      lpnReceivingService.receiveByLPN(
          aclVerificationEventMessage.getLpn(),
          Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
          aclVerificationEventMessage.getLocationId());
    } catch (ReceivingException e) {
      fail("No exception is expected");
    }

    verify(containerPersisterService, times(1)).getContainerDetails(lpn);
    verify(labelDataService, times(1)).getPurchaseOrderInfoFromLabelData(deliveryNum, lpn);

    // verify headers are created properly and GDM is called
    ArgumentCaptor<HttpHeaders> captor = ArgumentCaptor.forClass(HttpHeaders.class);
    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            captor.capture());

    HttpHeaders httpHeaders = captor.getValue();
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM),
        TenantContext.getFacilityNum().toString());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE),
        TenantContext.getFacilityCountryCode());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY),
        TenantContext.getCorrelationId());

    verify(deliveryService, times(0)).reOpenDelivery(eq(deliveryNum), any(HttpHeaders.class));
    verify(accDeliveryMetaDataService, times(0))
        .findAndUpdateDeliveryStatus(deliveryNum.toString(), DeliveryStatus.SYS_REO);
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(poNum, poLine);

    // verify OF request payload
    ArgumentCaptor<FdeCreateContainerRequest> fdeCaptor =
        ArgumentCaptor.forClass(FdeCreateContainerRequest.class);
    verify(fdeService, times(1)).receive(fdeCaptor.capture(), eq(httpHeaders));
    assertEquals(
        JacksonParser.writeValueAsStringExcludeNull(fdeCaptor.getValue()).replaceAll("\\s+", ""),
        MockACLMessageData.getBuildContainerRequestForACLReceiving().replaceAll("\\s+", ""));

    // verify WFM contract for update & complete
    ArgumentCaptor<ReceivingJMSEvent> wfmPublishCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(3))
        .publish(
            eq(ReceivingConstants.PUB_INSTRUCTION_TOPIC),
            wfmPublishCaptor.capture(),
            eq(Boolean.FALSE));
    List<ReceivingJMSEvent> receivingJMSEventList = wfmPublishCaptor.getAllValues();
    String WFMUpdateMessage = receivingJMSEventList.get(1).getMessageBody();
    String WFMCompleteMessage = receivingJMSEventList.get(2).getMessageBody();

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclWFMUpdateMessageSchema.json")
                            .getCanonicalPath()))),
            WFMUpdateMessage));
    // assertTrue(WFMUpdateMessage.contains("\"userId\":\"rcv\""));
    // assertTrue(WFMUpdateMessage.contains("\"activityName\":\"ACL-DOOR\""));

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclWFMCompleteMessageSchema.json")
                            .getCanonicalPath()))),
            WFMCompleteMessage));

    // verify container was created properly
    ArgumentCaptor<Container> containerCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerPersisterService, times(1)).saveContainer(containerCaptor.capture());

    Container savedContainer = containerCaptor.getValue();
    assertNotNull(savedContainer.getCompleteTs());
    assertEquals(savedContainer.getTrackingId(), lpn);
    assertNull(savedContainer.getParentTrackingId());
    assertEquals(savedContainer.getContainerItems().size(), 1);
    // assertEquals(savedContainer.getCreateUser(), "rcv");
    assertTrue(CollectionUtils.isEmpty(savedContainer.getChildContainers()));
    assertTrue(savedContainer.getIsConveyable());
    assertTrue(savedContainer.getOnConveyor());

    // verify instruction was saved
    ArgumentCaptor<Instruction> instructionCaptor = ArgumentCaptor.forClass(Instruction.class);
    verify(instructionRepository, times(1)).save(instructionCaptor.capture());
    Instruction savedInstruction = instructionCaptor.getValue();
    assertEquals(savedInstruction.getActivityName(), "ACL");

    // verify receipts were saved
    // verify(receiptService,
    // times(1)).createReceiptsFromInstruction(any(UpdateInstructionRequest.class), eq(null),
    // eq("rcv"));
    // validate contract for published container
    ArgumentCaptor<ContainerDTO> containerPublishCaptor =
        ArgumentCaptor.forClass(ContainerDTO.class);
    verify(JMSReceiptPublisher, times(1)).publish(containerPublishCaptor.capture(), any());
    String publishedContainer = gson.toJson(containerPublishCaptor.getValue());
    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclReceivingContainerMessageSchema.json")
                            .getCanonicalPath()))),
            publishedContainer));
  }

  @Test
  public void testReceiveByLPNDeliveryFNL() throws IOException, ReceivingException {
    Long deliveryNum = 94769060L;
    String lpn = "c32987000000000000000001";
    String location = "D102A";
    String poNum = "3615852071";
    Integer poLine = 8;
    ACLVerificationEventMessage aclVerificationEventMessage = new ACLVerificationEventMessage();
    aclVerificationEventMessage.setGroupNbr(deliveryNum.toString());
    aclVerificationEventMessage.setLpn(lpn);
    aclVerificationEventMessage.setLocationId(location);
    // set deliveryStatus to OPN
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse(poNum, poLine);
    gdmPOLineResponse.setDeliveryStatus(DeliveryStatus.FNL);
    gdmPOLineResponse.setDeliveryLegacyStatus(DeliveryStatus.FNL.name());
    String OFResponse = MockACLMessageData.getBuildContainerResponseForACLReceiving();
    PossibleUPC possibleUPC =
        PossibleUPC.builder()
            .consumableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc())
            .orderableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getItemUpc())
            .build();
    PurchaseOrderInfo purchaseOrderInfo =
        new PurchaseOrderInfo(
            deliveryNum, poNum, poLine, JacksonParser.writeValueAsString(possibleUPC));

    // create user in DB
    createUserLocationMapping(location, "sysadmin.s32818", LocationType.ONLINE);
    createUserLocationMapping(location, "rcv.s32818", LocationType.ONLINE);

    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(null);
    when(labelDataService.getPurchaseOrderInfoFromLabelData(deliveryNum, lpn))
        .thenReturn(purchaseOrderInfo);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(gdmPOLineResponse));

    when(receiptService.getReceivedQtyByPoAndPoLine("4763030227", 1)).thenReturn(0L);

    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenReturn(OFResponse);

    LocationInfo locationInfo = new LocationInfo();
    locationInfo.setIsOnline(Boolean.TRUE);

    when(containerPersisterService.saveContainer(any()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.FALSE);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);
    try {
      lpnReceivingService.receiveByLPN(
          aclVerificationEventMessage.getLpn(),
          Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
          aclVerificationEventMessage.getLocationId());
    } catch (ReceivingException e) {
      fail("No exception is expected");
    }

    verify(containerPersisterService, times(1)).getContainerDetails(lpn);
    verify(labelDataService, times(1)).getPurchaseOrderInfoFromLabelData(deliveryNum, lpn);

    // verify headers are created properly and GDM is called
    ArgumentCaptor<HttpHeaders> captor = ArgumentCaptor.forClass(HttpHeaders.class);
    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            captor.capture());

    HttpHeaders httpHeaders = captor.getValue();
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM),
        TenantContext.getFacilityNum().toString());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE),
        TenantContext.getFacilityCountryCode());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY),
        TenantContext.getCorrelationId());

    verify(deliveryService, times(0)).reOpenDelivery(eq(deliveryNum), any(HttpHeaders.class));
    verify(accDeliveryMetaDataService, times(0))
        .findAndUpdateDeliveryStatus(deliveryNum.toString(), DeliveryStatus.SYS_REO);
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(poNum, poLine);

    // verify OF request payload
    ArgumentCaptor<FdeCreateContainerRequest> fdeCaptor =
        ArgumentCaptor.forClass(FdeCreateContainerRequest.class);
    verify(fdeService, times(1)).receive(fdeCaptor.capture(), eq(httpHeaders));
    assertEquals(
        JacksonParser.writeValueAsStringExcludeNull(fdeCaptor.getValue()).replaceAll("\\s+", ""),
        MockACLMessageData.getBuildContainerRequestForACLReceiving().replaceAll("\\s+", ""));

    // verify WFM contract for update & complete
    ArgumentCaptor<ReceivingJMSEvent> wfmPublishCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(3))
        .publish(
            eq(ReceivingConstants.PUB_INSTRUCTION_TOPIC),
            wfmPublishCaptor.capture(),
            eq(Boolean.FALSE));
    List<ReceivingJMSEvent> receivingJMSEventList = wfmPublishCaptor.getAllValues();
    String WFMUpdateMessage = receivingJMSEventList.get(1).getMessageBody();
    String WFMCompleteMessage = receivingJMSEventList.get(2).getMessageBody();

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclWFMUpdateMessageSchema.json")
                            .getCanonicalPath()))),
            WFMUpdateMessage));
    // assertTrue(WFMUpdateMessage.contains("\"userId\":\"rcv\""));
    // assertTrue(WFMUpdateMessage.contains("\"activityName\":\"ACL-DOOR\""));

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclWFMCompleteMessageSchema.json")
                            .getCanonicalPath()))),
            WFMCompleteMessage));

    // verify container was created properly
    ArgumentCaptor<Container> containerCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerPersisterService, times(1)).saveContainer(containerCaptor.capture());

    Container savedContainer = containerCaptor.getValue();
    assertNotNull(savedContainer.getCompleteTs());
    assertEquals(savedContainer.getTrackingId(), lpn);
    assertNull(savedContainer.getParentTrackingId());
    assertEquals(savedContainer.getContainerItems().size(), 1);
    // assertEquals(savedContainer.getCreateUser(), "rcv");
    assertTrue(CollectionUtils.isEmpty(savedContainer.getChildContainers()));
    assertTrue(savedContainer.getIsConveyable());
    assertTrue(savedContainer.getOnConveyor());

    // verify instruction was saved
    ArgumentCaptor<Instruction> instructionCaptor = ArgumentCaptor.forClass(Instruction.class);
    verify(instructionRepository, times(1)).save(instructionCaptor.capture());
    Instruction savedInstruction = instructionCaptor.getValue();
    assertEquals(savedInstruction.getActivityName(), "ACL");

    // verify receipts were saved
    // verify(receiptService,
    // times(1)).createReceiptsFromInstruction(any(UpdateInstructionRequest.class), eq(null),
    // eq("rcv"));
    // validate contract for published container
    ArgumentCaptor<ContainerDTO> containerPublishCaptor =
        ArgumentCaptor.forClass(ContainerDTO.class);
    verify(JMSReceiptPublisher, times(1)).publish(containerPublishCaptor.capture(), any());
    String publishedContainer = gson.toJson(containerPublishCaptor.getValue());
    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclReceivingContainerMessageSchema.json")
                            .getCanonicalPath()))),
            publishedContainer));
  }

  @Test
  public void testReceiveLpnOverage() throws IOException, ReceivingException {
    Long deliveryNum = 94769060L;
    String lpn = "c32987000000000000000001";
    String location = "D102A";
    String poNum = "3615852071";
    Integer poLine = 8;
    ACLVerificationEventMessage aclVerificationEventMessage = new ACLVerificationEventMessage();
    aclVerificationEventMessage.setGroupNbr(deliveryNum.toString());
    aclVerificationEventMessage.setLpn(lpn);
    aclVerificationEventMessage.setLocationId(location);
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse(poNum, poLine);
    PossibleUPC possibleUPC =
        PossibleUPC.builder()
            .consumableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc())
            .orderableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getItemUpc())
            .build();
    PurchaseOrderInfo purchaseOrderInfo =
        new PurchaseOrderInfo(
            deliveryNum, poNum, poLine, JacksonParser.writeValueAsString(possibleUPC));

    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(null);
    when(labelDataService.getPurchaseOrderInfoFromLabelData(deliveryNum, lpn))
        .thenReturn(purchaseOrderInfo);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(gdmPOLineResponse));
    when(receiptService.getReceivedQtyByPoAndPoLine(poNum, poLine)).thenReturn(15L);
    when(containerPersisterService.saveContainer(any()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.FALSE);
    when(appConfig.getSorterExceptionTopic()).thenReturn(SORTER_EXCEPTION_TOPIC);

    when(exceptionContainerHandlerFactory.exceptionContainerHandler(any(ContainerException.class)))
        .thenReturn(overageExceptionContainerHandler);
    try {
      lpnReceivingService.receiveByLPN(
          aclVerificationEventMessage.getLpn(),
          Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
          aclVerificationEventMessage.getLocationId());
    } catch (ReceivingException e) {
      fail("Exception is not expected");
    }

    verify(containerPersisterService, times(1)).getContainerDetails(anyString());
    verify(labelDataService, times(1)).getPurchaseOrderInfoFromLabelData(anyLong(), anyString());

    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            any());
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(anyString(), anyInt());

    verify(containerPersisterService, times(1)).saveContainer(any());

    // verify container published
    ArgumentCaptor<ReceivingJMSEvent> exceptionContainerPublishCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(1))
        .publish(
            eq(ReceivingConstants.PUB_RECEIPTS_EXCEPTION_TOPIC),
            exceptionContainerPublishCaptor.capture(),
            eq(Boolean.TRUE));
    ReceivingJMSEvent event = exceptionContainerPublishCaptor.getValue();

    // validate headers sent to inventory
    Map<String, Object> messageHeaders = event.getHeaders();
    assertNotNull(messageHeaders.get(ReceivingConstants.TENENT_FACLITYNUM));
    assertNotNull(messageHeaders.get(ReceivingConstants.TENENT_COUNTRY_CODE));
    assertNotNull(messageHeaders.get(ReceivingConstants.USER_ID_HEADER_KEY));
    assertNotNull(messageHeaders.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY));

    // validate message contract
    String exceptionContainerPayload = event.getMessageBody();
    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/exceptionContainerMessageSchema.json")
                            .getCanonicalPath()))),
            exceptionContainerPayload));

    // verify message to Sorter
    verify(sorterPublisher, times(1))
        .publishException(anyString(), eq(SorterExceptionReason.OVERAGE), any(Date.class));

    // validate that following were not called
    verify(fdeService, times(0)).receive(any(), any());
    verify(jmsPublisher, times(0))
        .publish(eq(ReceivingConstants.PUB_INSTRUCTION_TOPIC), any(), any());
    verify(jmsPublisher, times(0)).publish(eq(ReceivingConstants.PUB_RECEIPTS_TOPIC), any(), any());
    verify(receiptService, times(0)).createReceiptsFromInstruction(any(), any(), any());
    verify(instructionRepository, times(0)).save(any());
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp = "Unable to get deliveryMetaData for deliveryNumber.*")
  public void testReceiveLpn_LPNNotPresentInLabelData() throws ReceivingException {
    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(null);
    when(labelDataService.getPurchaseOrderInfoFromLabelData(
            Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
            aclVerificationEventMessage.getLpn()))
        .thenReturn(null);
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.FALSE);
    lpnReceivingService.receiveByLPN(
        aclVerificationEventMessage.getLpn(),
        Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
        aclVerificationEventMessage.getLocationId());
  }

  @Test
  public void testReceiveLpn_GDMDelDocNotPresent() throws ReceivingException {
    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(null);
    when(labelDataService.getPurchaseOrderInfoFromLabelData(anyLong(), anyString()))
        .thenReturn(purchaseOrderInfo);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            anyLong(), anyString(), any(HttpHeaders.class)))
        .thenThrow(
            new ReceivingDataNotFoundException(
                ExceptionCodes.PO_LINE_NOT_FOUND,
                String.format(
                    ReceivingException.PO_POLINE_NOT_FOUND,
                    JacksonParser.convertJsonToObject(
                            purchaseOrderInfo.getPossibleUPC(), PossibleUPC.class)
                        .getOrderableGTIN(),
                    aclVerificationEventMessage.getGroupNbr())));
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.FALSE);
    when(exceptionContainerHandlerFactory.exceptionContainerHandler(any(ContainerException.class)))
        .thenReturn(noDeliveryDocExceptionContainerHandler);
    try {
      lpnReceivingService.receiveByLPN(
          aclVerificationEventMessage.getLpn(),
          Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
          aclVerificationEventMessage.getLocationId());
      verify(sorterPublisher, times(1))
          .publishException(anyString(), any(SorterExceptionReason.class), any(Date.class));
    } catch (ReceivingDataNotFoundException exc) {
      assertEquals(exc.getErrorCode(), ExceptionCodes.PO_LINE_NOT_FOUND);
    }
  }

  @Test
  public void testReceiveLpn_GDMDelDocLineNotPresent() throws ReceivingException {
    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(null);
    Long deliveryNum = 94769060L;
    String poNum = "3615852071";
    Integer poLine = 8;
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse(poNum, poLine);
    PossibleUPC possibleUPC =
        PossibleUPC.builder()
            .consumableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc())
            .orderableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getItemUpc())
            .build();
    PurchaseOrderInfo purchaseOrderInfo =
        new PurchaseOrderInfo(
            deliveryNum, poNum, poLine, JacksonParser.writeValueAsString(possibleUPC));
    when(labelDataService.getPurchaseOrderInfoFromLabelData(anyLong(), anyString()))
        .thenReturn(purchaseOrderInfo);
    gdmPOLineResponse.setDeliveryDocumentLines(null);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(gdmPOLineResponse));
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.FALSE);
    when(exceptionContainerHandlerFactory.exceptionContainerHandler(any(ContainerException.class)))
        .thenReturn(noDeliveryDocExceptionContainerHandler);
    try {
      lpnReceivingService.receiveByLPN(
          aclVerificationEventMessage.getLpn(),
          Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
          aclVerificationEventMessage.getLocationId());
      verify(sorterPublisher, times(1))
          .publishException(anyString(), any(SorterExceptionReason.class), any(Date.class));
    } catch (ReceivingDataNotFoundException exc) {
      assertEquals(exc.getErrorCode(), ExceptionCodes.PO_LINE_NOT_FOUND);
    }
  }

  @Test
  public void testReceiveLpn_GDMPONotFound_FallbackSearchEnabled_PONotFound()
      throws ReceivingException {
    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(null);
    Long deliveryNum = 94769060L;
    String poNum = "3615852071";
    Integer poLine = 8;
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse(poNum, poLine);
    PossibleUPC possibleUPC =
        PossibleUPC.builder()
            .consumableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc())
            .orderableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getItemUpc())
            .build();
    PurchaseOrderInfo purchaseOrderInfo =
        new PurchaseOrderInfo(
            deliveryNum, poNum, poLine, JacksonParser.writeValueAsString(possibleUPC));
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ACCConstants.ENABLE_FALLBACK_PO_SEARCH_LPN_RECEIVING))
        .thenReturn(true);
    when(labelDataService.getPurchaseOrderInfoFromLabelData(anyLong(), anyString()))
        .thenReturn(purchaseOrderInfo);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            anyLong(), anyString(), any(HttpHeaders.class)))
        .thenThrow(
            new ReceivingDataNotFoundException(
                ExceptionCodes.PO_LINE_NOT_FOUND, "Po Line Not Found Error Msg"))
        .thenThrow(
            new ReceivingDataNotFoundException(
                ExceptionCodes.PO_LINE_NOT_FOUND, "Po Line Not Found Error Msg"));
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.FALSE);
    try {
      lpnReceivingService.receiveByLPN(
          aclVerificationEventMessage.getLpn(),
          Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
          aclVerificationEventMessage.getLocationId());
    } catch (ReceivingDataNotFoundException exc) {
      assertEquals(exc.getErrorCode(), ExceptionCodes.PO_LINE_NOT_FOUND);
    }
  }

  @Test
  public void testReceiveLpn_POContainsXBlockedItem() throws ReceivingException {
    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(null);
    Long deliveryNum = 94769060L;
    String poNum = "3615852071";
    Integer poLine = 8;
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse(poNum, poLine);
    // add duplicate po line to the po
    gdmPOLineResponse
        .getDeliveryDocumentLines()
        .add(gdmPOLineResponse.getDeliveryDocumentLines().get(0));
    // Set X handling code in first po line
    gdmPOLineResponse.getDeliveryDocumentLines().get(0).setHandlingCode("X");

    when(tenantSpecificConfigReader.isFeatureFlagEnabled(ReceivingConstants.ITEM_X_BLOCKED_VAL))
        .thenReturn(Boolean.TRUE);
    when(labelDataService.getPurchaseOrderInfoFromLabelData(anyLong(), anyString()))
        .thenReturn(purchaseOrderInfo);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(gdmPOLineResponse));
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.TRUE);
    when(receiptService.getReceivedQtyByPoAndPoLine(poNum, poLine)).thenReturn(0L);
    when(exceptionContainerHandlerFactory.exceptionContainerHandler(any(ContainerException.class)))
        .thenReturn(xBlockExceptionContainerHandler);
    try {
      lpnReceivingService.receiveByLPN(
          aclVerificationEventMessage.getLpn(),
          Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
          aclVerificationEventMessage.getLocationId());
    } catch (ReceivingBadDataException exc) {
      verify(sorterPublisher, times(1))
          .publishException(anyString(), any(SorterExceptionReason.class), any(Date.class));
      assertEquals(gdmPOLineResponse.getDeliveryDocumentLines().size(), 1);
      assertEquals(exc.getErrorCode(), ExceptionCodes.ITEM_X_BLOCKED_ERROR);
    }
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp = "Error encountered while grouping allocations.*")
  public void testReceiveLpn_OFCallFailed() throws ReceivingException {
    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(null);
    when(labelDataService.getPurchaseOrderInfoFromLabelData(anyLong(), anyString()))
        .thenReturn(purchaseOrderInfo);
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse("1234567", 1);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(gdmPOLineResponse));
    when(receiptService.getReceivedQtyByPoAndPoLine(any(), any())).thenReturn(0L);
    InstructionError instructionError = InstructionErrorCode.getErrorValue("GLS-OF-BE-00047");
    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenThrow(
            new ReceivingException(
                instructionError.getErrorMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR,
                instructionError.getErrorCode(),
                instructionError.getErrorHeader()));
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.FALSE);
    lpnReceivingService.receiveByLPN(
        aclVerificationEventMessage.getLpn(),
        Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
        aclVerificationEventMessage.getLocationId());

    verify(containerPersisterService, times(1)).getContainerDetails(anyString());
    verify(labelDataService, times(1)).getPurchaseOrderInfoFromLabelData(anyLong(), anyString());

    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByUpc(anyLong(), anyString(), any(HttpHeaders.class));
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(anyString(), anyInt());

    verify(fdeService, times(1)).receive(any(), any());
    verify(jmsPublisher, times(0)).publish(anyString(), any(), any());
    verify(receiptService, times(0)).createReceiptsFromInstruction(any(), any(), any());
  }

  @Test
  public void testReceiveLpn_OF_Error_NoAllocation() throws ReceivingException, IOException {
    Long deliveryNum = 94769060L;
    String lpn = "c32987000000000000000001";
    String location = "D102A";
    String poNum = "3615852071";
    Integer poLine = 8;
    ACLVerificationEventMessage aclVerificationEventMessage = new ACLVerificationEventMessage();
    aclVerificationEventMessage.setGroupNbr(deliveryNum.toString());
    aclVerificationEventMessage.setLpn(lpn);
    aclVerificationEventMessage.setLocationId(location);
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse(poNum, poLine);
    PossibleUPC possibleUPC =
        PossibleUPC.builder()
            .consumableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc())
            .orderableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getItemUpc())
            .build();
    PurchaseOrderInfo purchaseOrderInfo =
        new PurchaseOrderInfo(
            deliveryNum, poNum, poLine, JacksonParser.writeValueAsString(possibleUPC));
    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(null);
    when(labelDataService.getPurchaseOrderInfoFromLabelData(deliveryNum, lpn))
        .thenReturn(purchaseOrderInfo);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(gdmPOLineResponse));

    when(receiptService.getReceivedQtyByPoAndPoLine(poNum, poLine)).thenReturn(0L);

    InstructionError instructionError = InstructionErrorCode.getErrorValue("GLS-OF-BE-00009");
    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenThrow(
            new ReceivingException(
                instructionError.getErrorMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR,
                instructionError.getErrorCode(),
                instructionError.getErrorHeader()));
    when(containerPersisterService.saveContainer(any()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.FALSE);

    when(exceptionContainerHandlerFactory.exceptionContainerHandler(any(ContainerException.class)))
        .thenReturn(noAllocationExceptionContainerHandler);

    lpnReceivingService.receiveByLPN(
        aclVerificationEventMessage.getLpn(),
        Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
        aclVerificationEventMessage.getLocationId());

    verify(sorterPublisher, times(1))
        .publishException(anyString(), eq(SorterExceptionReason.NO_ALLOCATION), any(Date.class));
    verify(containerPersisterService, times(1)).getContainerDetails(anyString());
    verify(labelDataService, times(1)).getPurchaseOrderInfoFromLabelData(anyLong(), anyString());

    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByUpc(anyLong(), anyString(), any(HttpHeaders.class));
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(anyString(), anyInt());
    verify(fdeService, times(1)).receive(any(), any());
    verify(containerPersisterService, times(1)).saveContainer(any());

    // verify container published
    ArgumentCaptor<ReceivingJMSEvent> exceptionContainerPublishCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(1))
        .publish(
            eq(ReceivingConstants.PUB_RECEIPTS_EXCEPTION_TOPIC),
            exceptionContainerPublishCaptor.capture(),
            eq(Boolean.TRUE));
    ReceivingJMSEvent event = exceptionContainerPublishCaptor.getValue();

    // validate headers sent to inventory
    Map<String, Object> messageHeaders = event.getHeaders();
    assertNotNull(messageHeaders.get(ReceivingConstants.TENENT_FACLITYNUM));
    assertNotNull(messageHeaders.get(ReceivingConstants.TENENT_COUNTRY_CODE));
    assertNotNull(messageHeaders.get(ReceivingConstants.USER_ID_HEADER_KEY));
    assertNotNull(messageHeaders.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY));

    // validate message contract
    String exceptionContainerPayload = event.getMessageBody();
    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/exceptionContainerMessageSchema.json")
                            .getCanonicalPath()))),
            exceptionContainerPayload));

    assertTrue(exceptionContainerPayload.contains("NA"));
    verify(jmsPublisher, times(0))
        .publish(eq(ReceivingConstants.PUB_INSTRUCTION_TOPIC), any(), any());
    verify(jmsPublisher, times(0)).publish(eq(ReceivingConstants.PUB_RECEIPTS_TOPIC), any(), any());
    verify(receiptService, times(0)).createReceiptsFromInstruction(any(), any(), any());
    verify(instructionRepository, times(0)).save(any());
  }

  @Test
  public void testReceiveLpn_OF__Error_ChannelFlip() throws ReceivingException, IOException {
    Long deliveryNum = 94769060L;
    String lpn = "c32987000000000000000001";
    String location = "D102A";
    String poNum = "3615852071";
    Integer poLine = 8;
    ACLVerificationEventMessage aclVerificationEventMessage = new ACLVerificationEventMessage();
    aclVerificationEventMessage.setGroupNbr(deliveryNum.toString());
    aclVerificationEventMessage.setLpn(lpn);
    aclVerificationEventMessage.setLocationId(location);
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse(poNum, poLine);
    PossibleUPC possibleUPC =
        PossibleUPC.builder()
            .consumableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc())
            .orderableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getItemUpc())
            .build();
    PurchaseOrderInfo purchaseOrderInfo =
        new PurchaseOrderInfo(
            deliveryNum, poNum, poLine, JacksonParser.writeValueAsString(possibleUPC));

    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(null);
    when(labelDataService.getPurchaseOrderInfoFromLabelData(deliveryNum, lpn))
        .thenReturn(purchaseOrderInfo);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(gdmPOLineResponse));

    when(receiptService.getReceivedQtyByPoAndPoLine(poNum, poLine)).thenReturn(0L);

    InstructionError instructionError = InstructionErrorCode.getErrorValue("GLS-OF-BE-00035");
    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenThrow(
            new ReceivingException(
                instructionError.getErrorMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR,
                instructionError.getErrorCode(),
                instructionError.getErrorHeader()));
    when(containerPersisterService.saveContainer(any()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.FALSE);
    when(exceptionContainerHandlerFactory.exceptionContainerHandler(any(ContainerException.class)))
        .thenReturn(channelFlipExceptionContainerHandler);

    lpnReceivingService.receiveByLPN(
        aclVerificationEventMessage.getLpn(),
        Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
        aclVerificationEventMessage.getLocationId());

    verify(sorterPublisher, times(1))
        .publishException(anyString(), eq(SorterExceptionReason.CHFLIP), any(Date.class));
    verify(containerPersisterService, times(1)).getContainerDetails(anyString());
    verify(labelDataService, times(1)).getPurchaseOrderInfoFromLabelData(anyLong(), anyString());

    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByUpc(anyLong(), anyString(), any(HttpHeaders.class));
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(anyString(), anyInt());
    verify(fdeService, times(1)).receive(any(), any());
    verify(containerPersisterService, times(1)).saveContainer(any());

    // verify container published
    ArgumentCaptor<ReceivingJMSEvent> exceptionContainerPublishCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(1))
        .publish(
            eq(ReceivingConstants.PUB_RECEIPTS_EXCEPTION_TOPIC),
            exceptionContainerPublishCaptor.capture(),
            eq(Boolean.TRUE));
    ReceivingJMSEvent event = exceptionContainerPublishCaptor.getValue();

    // validate headers sent to inventory
    Map<String, Object> messageHeaders = exceptionContainerPublishCaptor.getValue().getHeaders();
    assertNotNull(messageHeaders.get(ReceivingConstants.TENENT_FACLITYNUM));
    assertNotNull(messageHeaders.get(ReceivingConstants.TENENT_COUNTRY_CODE));
    assertNotNull(messageHeaders.get(ReceivingConstants.USER_ID_HEADER_KEY));
    assertNotNull(messageHeaders.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY));

    // validate message contract
    String exceptionContainerPayload = event.getMessageBody();
    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/exceptionContainerMessageSchema.json")
                            .getCanonicalPath()))),
            exceptionContainerPayload));
    assertTrue(exceptionContainerPayload.contains("CF"));

    verify(jmsPublisher, times(0))
        .publish(eq(ReceivingConstants.PUB_INSTRUCTION_TOPIC), any(), any());
    verify(jmsPublisher, times(0)).publish(eq(ReceivingConstants.PUB_RECEIPTS_TOPIC), any(), any());
    verify(receiptService, times(0)).createReceiptsFromInstruction(any(), any(), any());
    verify(instructionRepository, times(0)).save(any());
  }

  @Test
  public void testReceiveLpn_OF_Error_InvalidAllocations_FlagEnabled()
      throws ReceivingException, IOException {
    Long deliveryNum = 94769060L;
    String lpn = "c32987000000000000000001";
    String location = "D102A";
    String poNum = "3615852071";
    Integer poLine = 8;
    ACLVerificationEventMessage aclVerificationEventMessage = new ACLVerificationEventMessage();
    aclVerificationEventMessage.setGroupNbr(deliveryNum.toString());
    aclVerificationEventMessage.setLpn(lpn);
    aclVerificationEventMessage.setLocationId(location);
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse(poNum, poLine);
    PossibleUPC possibleUPC =
        PossibleUPC.builder()
            .consumableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc())
            .orderableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getItemUpc())
            .build();
    PurchaseOrderInfo purchaseOrderInfo =
        new PurchaseOrderInfo(
            deliveryNum, poNum, poLine, JacksonParser.writeValueAsString(possibleUPC));
    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(null);
    when(labelDataService.getPurchaseOrderInfoFromLabelData(deliveryNum, lpn))
        .thenReturn(purchaseOrderInfo);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(gdmPOLineResponse));

    when(receiptService.getReceivedQtyByPoAndPoLine(poNum, poLine)).thenReturn(0L);
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ACCConstants.ENABLE_INVALID_ALLOCATIONS_EXCEPTION_CONTAINER_PUBLISH);
    InstructionError instructionError = InstructionErrorCode.getErrorValue("GLS-OF-BE-00010");
    ReceivingException ofException =
        new ReceivingException(
            instructionError.getErrorMessage(),
            HttpStatus.INTERNAL_SERVER_ERROR,
            instructionError.getErrorCode(),
            instructionError.getErrorHeader());
    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenThrow(ofException);
    when(containerPersisterService.saveContainer(any()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.FALSE);

    when(exceptionContainerHandlerFactory.exceptionContainerHandler(any(ContainerException.class)))
        .thenReturn(noAllocationExceptionContainerHandler);

    lpnReceivingService.receiveByLPN(
        aclVerificationEventMessage.getLpn(),
        Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
        aclVerificationEventMessage.getLocationId());

    verify(sorterPublisher, times(1))
        .publishException(anyString(), eq(SorterExceptionReason.NO_ALLOCATION), any(Date.class));
    verify(containerPersisterService, times(1)).getContainerDetails(anyString());
    verify(labelDataService, times(1)).getPurchaseOrderInfoFromLabelData(anyLong(), anyString());

    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByUpc(anyLong(), anyString(), any(HttpHeaders.class));
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(anyString(), anyInt());
    verify(fdeService, times(1)).receive(any(), any());
    verify(containerPersisterService, times(1)).saveContainer(any());

    // verify container published
    ArgumentCaptor<ReceivingJMSEvent> exceptionContainerPublishCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(1))
        .publish(
            eq(ReceivingConstants.PUB_RECEIPTS_EXCEPTION_TOPIC),
            exceptionContainerPublishCaptor.capture(),
            eq(Boolean.TRUE));
    ReceivingJMSEvent event = exceptionContainerPublishCaptor.getValue();

    // validate headers sent to inventory
    Map<String, Object> messageHeaders = event.getHeaders();
    assertNotNull(messageHeaders.get(ReceivingConstants.TENENT_FACLITYNUM));
    assertNotNull(messageHeaders.get(ReceivingConstants.TENENT_COUNTRY_CODE));
    assertNotNull(messageHeaders.get(ReceivingConstants.USER_ID_HEADER_KEY));
    assertNotNull(messageHeaders.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY));

    // validate message contract
    String exceptionContainerPayload = event.getMessageBody();
    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/exceptionContainerMessageSchema.json")
                            .getCanonicalPath()))),
            exceptionContainerPayload));

    assertTrue(exceptionContainerPayload.contains("NA"));
    verify(jmsPublisher, times(0))
        .publish(eq(ReceivingConstants.PUB_INSTRUCTION_TOPIC), any(), any());
    verify(jmsPublisher, times(0)).publish(eq(ReceivingConstants.PUB_RECEIPTS_TOPIC), any(), any());
    verify(receiptService, times(0)).createReceiptsFromInstruction(any(), any(), any());
    verify(instructionRepository, times(0)).save(any());
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testReceiveLpn_OF_Error_InvalidAllocations_FlagDisabled() throws ReceivingException {
    Long deliveryNum = 94769060L;
    String lpn = "c32987000000000000000001";
    String location = "D102A";
    String poNum = "3615852071";
    Integer poLine = 8;
    ACLVerificationEventMessage aclVerificationEventMessage = new ACLVerificationEventMessage();
    aclVerificationEventMessage.setGroupNbr(deliveryNum.toString());
    aclVerificationEventMessage.setLpn(lpn);
    aclVerificationEventMessage.setLocationId(location);
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse(poNum, poLine);
    PossibleUPC possibleUPC =
        PossibleUPC.builder()
            .consumableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc())
            .orderableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getItemUpc())
            .build();
    PurchaseOrderInfo purchaseOrderInfo =
        new PurchaseOrderInfo(
            deliveryNum, poNum, poLine, JacksonParser.writeValueAsString(possibleUPC));
    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(null);
    when(labelDataService.getPurchaseOrderInfoFromLabelData(deliveryNum, lpn))
        .thenReturn(purchaseOrderInfo);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(gdmPOLineResponse));

    when(receiptService.getReceivedQtyByPoAndPoLine(poNum, poLine)).thenReturn(0L);
    doReturn(Boolean.FALSE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ACCConstants.ENABLE_INVALID_ALLOCATIONS_EXCEPTION_CONTAINER_PUBLISH);
    InstructionError instructionError = InstructionErrorCode.getErrorValue("GLS-OF-BE-00010");
    ReceivingException ofException =
        new ReceivingException(
            instructionError.getErrorMessage(),
            HttpStatus.INTERNAL_SERVER_ERROR,
            instructionError.getErrorCode(),
            instructionError.getErrorHeader());
    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenThrow(ofException);

    lpnReceivingService.receiveByLPN(
        aclVerificationEventMessage.getLpn(),
        Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
        aclVerificationEventMessage.getLocationId());
  }

  @Test
  public void testReceiveLpn_OF_Error_GenericError() throws ReceivingException, IOException {
    Long deliveryNum = 94769060L;
    String lpn = "c32987000000000000000001";
    String location = "D102A";
    String poNum = "3615852071";
    Integer poLine = 8;
    ACLVerificationEventMessage aclVerificationEventMessage = new ACLVerificationEventMessage();
    aclVerificationEventMessage.setGroupNbr(deliveryNum.toString());
    aclVerificationEventMessage.setLpn(lpn);
    aclVerificationEventMessage.setLocationId(location);
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse(poNum, poLine);
    PossibleUPC possibleUPC =
        PossibleUPC.builder()
            .consumableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc())
            .orderableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getItemUpc())
            .build();
    PurchaseOrderInfo purchaseOrderInfo =
        new PurchaseOrderInfo(
            deliveryNum, poNum, poLine, JacksonParser.writeValueAsString(possibleUPC));
    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(null);
    when(labelDataService.getPurchaseOrderInfoFromLabelData(deliveryNum, lpn))
        .thenReturn(purchaseOrderInfo);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(gdmPOLineResponse));

    when(receiptService.getReceivedQtyByPoAndPoLine(poNum, poLine)).thenReturn(0L);
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ACCConstants.ENABLE_GENERIC_FDE_ERROR_EXCEPTION_CONTAINER_PUBLISH);
    InstructionError instructionError = InstructionErrorCode.getErrorValue("OF_GENERIC_ERROR");
    ReceivingException ofException =
        new ReceivingException(
            instructionError.getErrorMessage(),
            HttpStatus.INTERNAL_SERVER_ERROR,
            instructionError.getErrorCode(),
            instructionError.getErrorHeader());
    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenThrow(ofException);
    when(containerPersisterService.saveContainer(any()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.FALSE);

    when(exceptionContainerHandlerFactory.exceptionContainerHandler(any(ContainerException.class)))
        .thenReturn(noAllocationExceptionContainerHandler);

    lpnReceivingService.receiveByLPN(
        aclVerificationEventMessage.getLpn(),
        Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
        aclVerificationEventMessage.getLocationId());

    verify(sorterPublisher, times(1))
        .publishException(anyString(), eq(SorterExceptionReason.NO_ALLOCATION), any(Date.class));
    verify(containerPersisterService, times(1)).getContainerDetails(anyString());
    verify(labelDataService, times(1)).getPurchaseOrderInfoFromLabelData(anyLong(), anyString());

    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByUpc(anyLong(), anyString(), any(HttpHeaders.class));
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(anyString(), anyInt());
    verify(fdeService, times(1)).receive(any(), any());
    verify(containerPersisterService, times(1)).saveContainer(any());

    // verify container published
    ArgumentCaptor<ReceivingJMSEvent> exceptionContainerPublishCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(1))
        .publish(
            eq(ReceivingConstants.PUB_RECEIPTS_EXCEPTION_TOPIC),
            exceptionContainerPublishCaptor.capture(),
            eq(Boolean.TRUE));
    ReceivingJMSEvent event = exceptionContainerPublishCaptor.getValue();

    // validate headers sent to inventory
    Map<String, Object> messageHeaders = event.getHeaders();
    assertNotNull(messageHeaders.get(ReceivingConstants.TENENT_FACLITYNUM));
    assertNotNull(messageHeaders.get(ReceivingConstants.TENENT_COUNTRY_CODE));
    assertNotNull(messageHeaders.get(ReceivingConstants.USER_ID_HEADER_KEY));
    assertNotNull(messageHeaders.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY));

    // validate message contract
    String exceptionContainerPayload = event.getMessageBody();
    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/exceptionContainerMessageSchema.json")
                            .getCanonicalPath()))),
            exceptionContainerPayload));

    assertTrue(exceptionContainerPayload.contains("NA"));
    verify(jmsPublisher, times(0))
        .publish(eq(ReceivingConstants.PUB_INSTRUCTION_TOPIC), any(), any());
    verify(jmsPublisher, times(0)).publish(eq(ReceivingConstants.PUB_RECEIPTS_TOPIC), any(), any());
    verify(receiptService, times(0)).createReceiptsFromInstruction(any(), any(), any());
    verify(instructionRepository, times(0)).save(any());
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testReceiveLpn_OF_Error__FlagDisabled() throws ReceivingException {
    Long deliveryNum = 94769060L;
    String lpn = "c32987000000000000000001";
    String location = "D102A";
    String poNum = "3615852071";
    Integer poLine = 8;
    ACLVerificationEventMessage aclVerificationEventMessage = new ACLVerificationEventMessage();
    aclVerificationEventMessage.setGroupNbr(deliveryNum.toString());
    aclVerificationEventMessage.setLpn(lpn);
    aclVerificationEventMessage.setLocationId(location);
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse(poNum, poLine);
    PossibleUPC possibleUPC =
        PossibleUPC.builder()
            .consumableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc())
            .orderableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getItemUpc())
            .build();
    PurchaseOrderInfo purchaseOrderInfo =
        new PurchaseOrderInfo(
            deliveryNum, poNum, poLine, JacksonParser.writeValueAsString(possibleUPC));
    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(null);
    when(labelDataService.getPurchaseOrderInfoFromLabelData(deliveryNum, lpn))
        .thenReturn(purchaseOrderInfo);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(gdmPOLineResponse));

    when(receiptService.getReceivedQtyByPoAndPoLine(poNum, poLine)).thenReturn(0L);
    doReturn(Boolean.FALSE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ACCConstants.ENABLE_GENERIC_FDE_ERROR_EXCEPTION_CONTAINER_PUBLISH);
    InstructionError instructionError = InstructionErrorCode.getErrorValue("OF_GENERIC_ERROR");
    ReceivingException ofException =
        new ReceivingException(
            instructionError.getErrorMessage(),
            HttpStatus.INTERNAL_SERVER_ERROR,
            instructionError.getErrorCode(),
            instructionError.getErrorHeader());
    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenThrow(ofException);

    lpnReceivingService.receiveByLPN(
        aclVerificationEventMessage.getLpn(),
        Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
        aclVerificationEventMessage.getLocationId());
  }

  @Test
  public void testReceiveLpnNoUserAtLocation() throws ReceivingException, IOException {
    Long deliveryNum = 94769060L;
    String lpn = "c32987000000000000000001";
    String location = "D102A";
    String poNum = "3615852071";
    Integer poLine = 8;
    ACLVerificationEventMessage aclVerificationEventMessage = new ACLVerificationEventMessage();
    aclVerificationEventMessage.setGroupNbr(deliveryNum.toString());
    aclVerificationEventMessage.setLpn(lpn);
    aclVerificationEventMessage.setLocationId(location);
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse(poNum, poLine);
    String OFResponse = MockACLMessageData.getBuildContainerResponseForACLReceiving();
    PossibleUPC possibleUPC =
        PossibleUPC.builder()
            .consumableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc())
            .orderableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getItemUpc())
            .build();
    PurchaseOrderInfo purchaseOrderInfo =
        new PurchaseOrderInfo(
            deliveryNum, poNum, poLine, JacksonParser.writeValueAsString(possibleUPC));
    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(null);
    when(labelDataService.getPurchaseOrderInfoFromLabelData(deliveryNum, lpn))
        .thenReturn(purchaseOrderInfo);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(gdmPOLineResponse));

    when(receiptService.getReceivedQtyByPoAndPoLine("4763030227", 1)).thenReturn(0L);

    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenReturn(OFResponse);

    when(containerPersisterService.saveContainer(any()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.FALSE);
    try {
      lpnReceivingService.receiveByLPN(
          aclVerificationEventMessage.getLpn(),
          Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
          aclVerificationEventMessage.getLocationId());
    } catch (ReceivingException e) {
      e.printStackTrace();
      fail("No exception is expected");
    }

    verify(containerPersisterService, times(1)).getContainerDetails(lpn);
    verify(labelDataService, times(1)).getPurchaseOrderInfoFromLabelData(deliveryNum, lpn);

    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            any());

    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(poNum, poLine);

    // verify OF request payload
    verify(fdeService, times(1))
        .receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class));

    ArgumentCaptor<ReceivingJMSEvent> wfmPublishCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(3))
        .publish(
            eq(ReceivingConstants.PUB_INSTRUCTION_TOPIC),
            wfmPublishCaptor.capture(),
            eq(Boolean.FALSE));
    List<ReceivingJMSEvent> receivingJMSEventList = wfmPublishCaptor.getAllValues();
    String WFMUpdateMessage = receivingJMSEventList.get(1).getMessageBody();

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclWFMUpdateMessageSchema.json")
                            .getCanonicalPath()))),
            WFMUpdateMessage));
    // assertTrue(WFMUpdateMessage.contains("\"userId\":\"rcvuser\""));
    // assertTrue(WFMUpdateMessage.contains("\"activityName\":\"ACL-DOOR\""));

    verify(containerPersisterService, times(1)).saveContainer(any(Container.class));

    // verify instruction was saved
    verify(instructionRepository, times(1)).save(any(Instruction.class));

    // verify receipts were saved
    verify(receiptService, times(1))
        .createReceiptsFromInstruction(
            any(UpdateInstructionRequest.class), eq(null), eq("rcvuser"));
    // validate contract for published container
    ArgumentCaptor<ContainerDTO> containerPublishCaptor =
        ArgumentCaptor.forClass(ContainerDTO.class);
    verify(JMSReceiptPublisher, times(1)).publish(containerPublishCaptor.capture(), any());
    String publishedContainer = gson.toJson(containerPublishCaptor.getValue());
    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclReceivingContainerMessageSchema.json")
                            .getCanonicalPath()))),
            publishedContainer));
  }

  @Test
  public void testReceiveByLPNContainerExists() {

    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(new Container());
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.FALSE);
    try {
      lpnReceivingService.receiveByLPN(
          aclVerificationEventMessage.getLpn(),
          Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
          aclVerificationEventMessage.getLocationId());
    } catch (ReceivingException e) {
      fail("No exception is expected");
    }

    verify(containerPersisterService, times(1))
        .getContainerDetails(aclVerificationEventMessage.getLpn());
  }

  @Test
  public void testReceiveByLPNContainerWithContainerMiscExistsAndPublish() {
    Container ctnr = new Container();
    ctnr.setFacility(Collections.singletonMap(ReceivingConstants.BU_NUMBER, "6094"));
    ctnr.setContainerMiscInfo(new HashMap<>());
    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(ctnr);
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.FALSE);
    try {
      doReturn(Boolean.TRUE)
          .when(tenantSpecificConfigReader)
          .isFeatureFlagEnabled(ACCConstants.ENABLE_DUPLICATE_LPN_VERIFICATION);
      lpnReceivingService.receiveByLPN(
          aclVerificationEventMessage.getLpn(),
          Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
          aclVerificationEventMessage.getLocationId());
    } catch (ReceivingException e) {
      fail("No exception is expected");
    }

    verify(containerPersisterService, times(1))
        .getContainerDetails(aclVerificationEventMessage.getLpn());
  }

  @Test
  public void testReceiveByLPNContainerWithoutContainerMiscAndPublish() {
    Container ctnr = new Container();
    ctnr.setFacility(Collections.singletonMap(ReceivingConstants.BU_NUMBER, "6094"));
    // ctnr.setContainerMiscInfo(new HashMap<>());
    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(ctnr);
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.FALSE);
    try {
      doReturn(Boolean.TRUE)
          .when(tenantSpecificConfigReader)
          .isFeatureFlagEnabled(ACCConstants.ENABLE_DUPLICATE_LPN_VERIFICATION);
      lpnReceivingService.receiveByLPN(
          aclVerificationEventMessage.getLpn(),
          Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
          aclVerificationEventMessage.getLocationId());
    } catch (ReceivingException e) {
      fail("No exception is expected");
    }

    verify(containerPersisterService, times(1))
        .getContainerDetails(aclVerificationEventMessage.getLpn());
  }

  @Test
  public void testReceiveByLPNItemPresentInMultiplePOAndAutoSelectDisabled()
      throws ReceivingException, IOException {
    Long deliveryNum = 94769060L;
    String lpn = "c32987000000000000000001";
    String location = "D102A";
    String poNum = "3615852071";
    Integer poLine = 8;
    ACLVerificationEventMessage aclVerificationEventMessage = new ACLVerificationEventMessage();
    aclVerificationEventMessage.setGroupNbr(deliveryNum.toString());
    aclVerificationEventMessage.setLpn(lpn);
    aclVerificationEventMessage.setLocationId(location);
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse(poNum, poLine);
    DeliveryDocument gdmPOLineResponse2 = getGdmPOLineResponse("4615852072", 8);
    List<DeliveryDocument> deliveryDocumentList = new ArrayList<>();
    deliveryDocumentList.add(gdmPOLineResponse);
    deliveryDocumentList.add(gdmPOLineResponse2);
    String OFResponse = MockACLMessageData.getBuildContainerResponseForACLReceiving();
    PossibleUPC possibleUPC =
        PossibleUPC.builder()
            .consumableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc())
            .orderableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getItemUpc())
            .build();
    PurchaseOrderInfo purchaseOrderInfo =
        new PurchaseOrderInfo(
            deliveryNum, poNum, poLine, JacksonParser.writeValueAsString(possibleUPC));

    // create user in DB
    createUserLocationMapping(location, "sysadmin.s32818", LocationType.ONLINE);
    createUserLocationMapping(location, "rcv.s32818", LocationType.ONLINE);

    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(null);
    when(labelDataService.getPurchaseOrderInfoFromLabelData(deliveryNum, lpn))
        .thenReturn(purchaseOrderInfo);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            any(HttpHeaders.class)))
        .thenReturn(deliveryDocumentList);

    when(receiptService.getReceivedQtyByPoAndPoLine(poNum, poLine)).thenReturn(0L);

    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenReturn(OFResponse);

    LocationInfo locationInfo = new LocationInfo();
    locationInfo.setIsOnline(Boolean.TRUE);
    when(containerPersisterService.saveContainer(any()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.FALSE);
    try {
      lpnReceivingService.receiveByLPN(
          aclVerificationEventMessage.getLpn(),
          Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
          aclVerificationEventMessage.getLocationId());
    } catch (ReceivingException e) {
      fail("No exception is expected");
    }

    verify(containerPersisterService, times(1)).getContainerDetails(lpn);
    verify(labelDataService, times(1)).getPurchaseOrderInfoFromLabelData(deliveryNum, lpn);

    // verify headers are created properly and GDM is called
    ArgumentCaptor<HttpHeaders> captor = ArgumentCaptor.forClass(HttpHeaders.class);
    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            captor.capture());

    HttpHeaders httpHeaders = captor.getValue();
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM),
        TenantContext.getFacilityNum().toString());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE),
        TenantContext.getFacilityCountryCode());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY),
        TenantContext.getCorrelationId());

    verify(deliveryService, times(0)).reOpenDelivery(any(), any());
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(poNum, poLine);

    // verify OF request payload
    ArgumentCaptor<FdeCreateContainerRequest> fdeCaptor =
        ArgumentCaptor.forClass(FdeCreateContainerRequest.class);
    verify(fdeService, times(1)).receive(fdeCaptor.capture(), eq(httpHeaders));
    assertEquals(
        JacksonParser.writeValueAsStringExcludeNull(fdeCaptor.getValue()).replaceAll("\\s+", ""),
        MockACLMessageData.getBuildContainerRequestForACLReceiving().replaceAll("\\s+", ""));

    // verify WFM contract for update & complete
    ArgumentCaptor<ReceivingJMSEvent> wfmPublishCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(3))
        .publish(
            eq(ReceivingConstants.PUB_INSTRUCTION_TOPIC),
            wfmPublishCaptor.capture(),
            eq(Boolean.FALSE));
    List<ReceivingJMSEvent> receivingJMSEventList = wfmPublishCaptor.getAllValues();
    String WFMUpdateMessage = receivingJMSEventList.get(1).getMessageBody();
    String WFMCompleteMessage = receivingJMSEventList.get(2).getMessageBody();

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclWFMUpdateMessageSchema.json")
                            .getCanonicalPath()))),
            WFMUpdateMessage));
    // assertTrue(WFMUpdateMessage.contains("\"userId\":\"rcv\""));
    // assertTrue(WFMUpdateMessage.contains("\"activityName\":\"ACL-DOOR\""));

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclWFMCompleteMessageSchema.json")
                            .getCanonicalPath()))),
            WFMCompleteMessage));

    // verify container was created properly
    ArgumentCaptor<Container> containerCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerPersisterService, times(1)).saveContainer(containerCaptor.capture());

    Container savedContainer = containerCaptor.getValue();
    assertNotNull(savedContainer.getCompleteTs());
    assertEquals(savedContainer.getTrackingId(), lpn);
    assertNull(savedContainer.getParentTrackingId());
    assertEquals(savedContainer.getContainerItems().size(), 1);
    // assertEquals(savedContainer.getCreateUser(), "rcv");
    assertTrue(CollectionUtils.isEmpty(savedContainer.getChildContainers()));
    assertTrue(savedContainer.getIsConveyable());
    assertTrue(savedContainer.getOnConveyor());

    // verify instruction was saved
    ArgumentCaptor<Instruction> instructionCaptor = ArgumentCaptor.forClass(Instruction.class);
    verify(instructionRepository, times(1)).save(instructionCaptor.capture());
    Instruction savedInstruction = instructionCaptor.getValue();
    assertEquals(savedInstruction.getActivityName(), "ACL");

    // verify receipts were saved
    // verify(receiptService,
    // times(1)).createReceiptsFromInstruction(any(UpdateInstructionRequest.class), eq(null),
    // eq("rcv"));
    // validate contract for published container
    ArgumentCaptor<ContainerDTO> containerPublishCaptor =
        ArgumentCaptor.forClass(ContainerDTO.class);
    verify(JMSReceiptPublisher, times(1)).publish(containerPublishCaptor.capture(), any());
    String publishedContainer = gson.toJson(containerPublishCaptor.getValue());
    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclReceivingContainerMessageSchema.json")
                            .getCanonicalPath()))),
            publishedContainer));
  }

  @Test
  public void testReceiveByLPNItemPresentInMultiplePOLineAndAutoSelectDisabled()
      throws ReceivingException, IOException {
    Long deliveryNum = 94769060L;
    String lpn = "c32987000000000000000001";
    String location = "D102A";
    String poNum = "3615852071";
    Integer poLine = 8;
    ACLVerificationEventMessage aclVerificationEventMessage = new ACLVerificationEventMessage();
    aclVerificationEventMessage.setGroupNbr(deliveryNum.toString());
    aclVerificationEventMessage.setLpn(lpn);
    aclVerificationEventMessage.setLocationId(location);
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse(poNum, poLine);
    gdmPOLineResponse
        .getDeliveryDocumentLines()
        .addAll(getGdmPOLineResponse(poNum, 5).getDeliveryDocumentLines());
    List<DeliveryDocument> deliveryDocumentList = new ArrayList<>();
    deliveryDocumentList.add(gdmPOLineResponse);
    String OFResponse = MockACLMessageData.getBuildContainerResponseForACLReceiving();
    PossibleUPC possibleUPC =
        PossibleUPC.builder()
            .consumableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc())
            .orderableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getItemUpc())
            .build();
    PurchaseOrderInfo purchaseOrderInfo =
        new PurchaseOrderInfo(
            deliveryNum, poNum, poLine, JacksonParser.writeValueAsString(possibleUPC));

    // create user in DB
    createUserLocationMapping(location, "sysadmin.s32818", LocationType.ONLINE);
    createUserLocationMapping(location, "rcv.s32818", LocationType.ONLINE);

    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(null);
    when(labelDataService.getPurchaseOrderInfoFromLabelData(deliveryNum, lpn))
        .thenReturn(purchaseOrderInfo);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            any(HttpHeaders.class)))
        .thenReturn(deliveryDocumentList);

    when(receiptService.getReceivedQtyByPoAndPoLine(poNum, poLine)).thenReturn(0L);

    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenReturn(OFResponse);

    LocationInfo locationInfo = new LocationInfo();
    locationInfo.setIsOnline(Boolean.TRUE);
    when(containerPersisterService.saveContainer(any()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.FALSE);
    try {
      lpnReceivingService.receiveByLPN(
          aclVerificationEventMessage.getLpn(),
          Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
          aclVerificationEventMessage.getLocationId());
    } catch (ReceivingException e) {
      fail("No exception is expected");
    }

    verify(containerPersisterService, times(1)).getContainerDetails(lpn);
    verify(labelDataService, times(1)).getPurchaseOrderInfoFromLabelData(deliveryNum, lpn);

    // verify headers are created properly and GDM is called
    ArgumentCaptor<HttpHeaders> captor = ArgumentCaptor.forClass(HttpHeaders.class);
    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            captor.capture());

    HttpHeaders httpHeaders = captor.getValue();
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM),
        TenantContext.getFacilityNum().toString());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE),
        TenantContext.getFacilityCountryCode());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY),
        TenantContext.getCorrelationId());

    verify(deliveryService, times(0)).reOpenDelivery(any(), any());
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(poNum, poLine);

    // verify OF request payload
    ArgumentCaptor<FdeCreateContainerRequest> fdeCaptor =
        ArgumentCaptor.forClass(FdeCreateContainerRequest.class);
    verify(fdeService, times(1)).receive(fdeCaptor.capture(), eq(httpHeaders));
    assertEquals(
        JacksonParser.writeValueAsStringExcludeNull(fdeCaptor.getValue()).replaceAll("\\s+", ""),
        MockACLMessageData.getBuildContainerRequestForACLReceiving().replaceAll("\\s+", ""));

    // verify WFM contract for update & complete
    ArgumentCaptor<ReceivingJMSEvent> wfmPublishCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(3))
        .publish(
            eq(ReceivingConstants.PUB_INSTRUCTION_TOPIC),
            wfmPublishCaptor.capture(),
            eq(Boolean.FALSE));
    List<ReceivingJMSEvent> receivingJMSEventList = wfmPublishCaptor.getAllValues();
    String WFMUpdateMessage = receivingJMSEventList.get(1).getMessageBody();
    String WFMCompleteMessage = receivingJMSEventList.get(2).getMessageBody();

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclWFMUpdateMessageSchema.json")
                            .getCanonicalPath()))),
            WFMUpdateMessage));
    // assertTrue(WFMUpdateMessage.contains("\"userId\":\"rcv\""), WFMUpdateMessage);
    // assertTrue(WFMUpdateMessage.contains("\"activityName\":\"ACL-DOOR\""));

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclWFMCompleteMessageSchema.json")
                            .getCanonicalPath()))),
            WFMCompleteMessage));

    // verify container was created properly
    ArgumentCaptor<Container> containerCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerPersisterService, times(1)).saveContainer(containerCaptor.capture());

    Container savedContainer = containerCaptor.getValue();
    assertNotNull(savedContainer.getCompleteTs());
    assertEquals(savedContainer.getTrackingId(), lpn);
    assertNull(savedContainer.getParentTrackingId());
    assertEquals(savedContainer.getContainerItems().size(), 1);
    // assertEquals(savedContainer.getCreateUser(), "rcv");
    assertTrue(CollectionUtils.isEmpty(savedContainer.getChildContainers()));
    assertTrue(savedContainer.getIsConveyable());
    assertTrue(savedContainer.getOnConveyor());

    // verify instruction was saved
    ArgumentCaptor<Instruction> instructionCaptor = ArgumentCaptor.forClass(Instruction.class);
    verify(instructionRepository, times(1)).save(instructionCaptor.capture());
    Instruction savedInstruction = instructionCaptor.getValue();
    assertEquals(savedInstruction.getActivityName(), "ACL");

    // verify receipts were saved
    // verify(receiptService,
    // times(1)).createReceiptsFromInstruction(any(UpdateInstructionRequest.class), eq(null),
    // eq("rcv"));
    // validate contract for published container
    ArgumentCaptor<ContainerDTO> containerPublishCaptor =
        ArgumentCaptor.forClass(ContainerDTO.class);
    verify(JMSReceiptPublisher, times(1)).publish(containerPublishCaptor.capture(), any());
    String publishedContainer = gson.toJson(containerPublishCaptor.getValue());
    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclReceivingContainerMessageSchema.json")
                            .getCanonicalPath()))),
            publishedContainer));
  }

  @Test
  public void testReceiveByLPNItemPresentInMultiplePOPOLineAndAutoSelectDisabled()
      throws ReceivingException, IOException {
    Long deliveryNum = 94769060L;
    String lpn = "c32987000000000000000001";
    String location = "D102A";
    String poNum = "3615852071";
    Integer poLine = 8;
    ACLVerificationEventMessage aclVerificationEventMessage = new ACLVerificationEventMessage();
    aclVerificationEventMessage.setGroupNbr(deliveryNum.toString());
    aclVerificationEventMessage.setLpn(lpn);
    aclVerificationEventMessage.setLocationId(location);
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse(poNum, poLine);
    DeliveryDocument gdmPOLineResponse2 = getGdmPOLineResponse("4615852072", 8);
    gdmPOLineResponse
        .getDeliveryDocumentLines()
        .addAll(getGdmPOLineResponse(poNum, 5).getDeliveryDocumentLines());
    List<DeliveryDocument> deliveryDocumentList = new ArrayList<>();
    deliveryDocumentList.add(gdmPOLineResponse2);
    deliveryDocumentList.add(gdmPOLineResponse);
    String OFResponse = MockACLMessageData.getBuildContainerResponseForACLReceiving();
    PossibleUPC possibleUPC =
        PossibleUPC.builder()
            .consumableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc())
            .orderableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getItemUpc())
            .build();
    PurchaseOrderInfo purchaseOrderInfo =
        new PurchaseOrderInfo(
            deliveryNum, poNum, poLine, JacksonParser.writeValueAsString(possibleUPC));

    // create user in DB
    createUserLocationMapping(location, "sysadmin.s32818", LocationType.ONLINE);
    createUserLocationMapping(location, "rcv.s32818", LocationType.ONLINE);

    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(null);
    when(labelDataService.getPurchaseOrderInfoFromLabelData(deliveryNum, lpn))
        .thenReturn(purchaseOrderInfo);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            any(HttpHeaders.class)))
        .thenReturn(deliveryDocumentList);

    when(receiptService.getReceivedQtyByPoAndPoLine(poNum, poLine)).thenReturn(0L);

    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenReturn(OFResponse);

    LocationInfo locationInfo = new LocationInfo();
    locationInfo.setIsOnline(Boolean.TRUE);
    when(containerPersisterService.saveContainer(any()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.FALSE);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);
    try {
      lpnReceivingService.receiveByLPN(
          aclVerificationEventMessage.getLpn(),
          Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
          aclVerificationEventMessage.getLocationId());
    } catch (ReceivingException e) {
      fail("No exception is expected");
    }

    verify(containerPersisterService, times(1)).getContainerDetails(lpn);
    verify(labelDataService, times(1)).getPurchaseOrderInfoFromLabelData(deliveryNum, lpn);

    // verify headers are created properly and GDM is called
    ArgumentCaptor<HttpHeaders> captor = ArgumentCaptor.forClass(HttpHeaders.class);
    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            captor.capture());

    HttpHeaders httpHeaders = captor.getValue();
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM),
        TenantContext.getFacilityNum().toString());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE),
        TenantContext.getFacilityCountryCode());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY),
        TenantContext.getCorrelationId());

    verify(deliveryService, times(0)).reOpenDelivery(any(), any());
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(poNum, poLine);

    // verify OF request payload
    ArgumentCaptor<FdeCreateContainerRequest> fdeCaptor =
        ArgumentCaptor.forClass(FdeCreateContainerRequest.class);
    verify(fdeService, times(1)).receive(fdeCaptor.capture(), eq(httpHeaders));
    assertEquals(
        JacksonParser.writeValueAsStringExcludeNull(fdeCaptor.getValue()).replaceAll("\\s+", ""),
        MockACLMessageData.getBuildContainerRequestForACLReceiving().replaceAll("\\s+", ""));

    // verify WFM contract for update & complete
    ArgumentCaptor<ReceivingJMSEvent> wfmPublishCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(3))
        .publish(
            eq(ReceivingConstants.PUB_INSTRUCTION_TOPIC),
            wfmPublishCaptor.capture(),
            eq(Boolean.FALSE));
    List<ReceivingJMSEvent> receivingJMSEventList = wfmPublishCaptor.getAllValues();
    String WFMUpdateMessage = receivingJMSEventList.get(1).getMessageBody();
    String WFMCompleteMessage = receivingJMSEventList.get(2).getMessageBody();

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclWFMUpdateMessageSchema.json")
                            .getCanonicalPath()))),
            WFMUpdateMessage));
    //    assertTrue(WFMUpdateMessage.contains("\"userId\":\"rcv\""));
    // assertTrue(WFMUpdateMessage.contains("\"activityName\":\"ACL-DOOR\""));

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclWFMCompleteMessageSchema.json")
                            .getCanonicalPath()))),
            WFMCompleteMessage));

    // verify container was created properly
    ArgumentCaptor<Container> containerCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerPersisterService, times(1)).saveContainer(containerCaptor.capture());

    Container savedContainer = containerCaptor.getValue();
    assertNotNull(savedContainer.getCompleteTs());
    assertEquals(savedContainer.getTrackingId(), lpn);
    assertNull(savedContainer.getParentTrackingId());
    assertEquals(savedContainer.getContainerItems().size(), 1);
    // assertEquals(savedContainer.getCreateUser(), "rcv");
    assertTrue(CollectionUtils.isEmpty(savedContainer.getChildContainers()));
    assertTrue(savedContainer.getIsConveyable());
    assertTrue(savedContainer.getOnConveyor());

    // verify instruction was saved
    ArgumentCaptor<Instruction> instructionCaptor = ArgumentCaptor.forClass(Instruction.class);
    verify(instructionRepository, times(1)).save(instructionCaptor.capture());
    Instruction savedInstruction = instructionCaptor.getValue();
    assertEquals(savedInstruction.getActivityName(), "ACL");

    // verify receipts were saved
    // verify(receiptService,
    // times(1)).createReceiptsFromInstruction(any(UpdateInstructionRequest.class), eq(null),
    // eq("rcv"));
    // validate contract for published container
    ArgumentCaptor<ContainerDTO> containerPublishCaptor =
        ArgumentCaptor.forClass(ContainerDTO.class);
    verify(JMSReceiptPublisher, times(1)).publish(containerPublishCaptor.capture(), any());
    String publishedContainer = gson.toJson(containerPublishCaptor.getValue());
    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclReceivingContainerMessageSchema.json")
                            .getCanonicalPath()))),
            publishedContainer));
  }

  @Test
  public void
      testReceiveByLPNItemPresentInMultiplePOAndAutoSelectEnabled_selectAgainstOrderedQtyByMABD()
          throws ReceivingException, IOException {
    Long deliveryNum = 94769060L;
    String lpn = "c32987000000000000000001";
    String location = "D102A";
    String poNum = "3615852071";
    Integer poLine = 8;
    ACLVerificationEventMessage aclVerificationEventMessage = new ACLVerificationEventMessage();
    aclVerificationEventMessage.setGroupNbr(deliveryNum.toString());
    aclVerificationEventMessage.setLpn(lpn);
    aclVerificationEventMessage.setLocationId(location);
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse(poNum, poLine);
    gdmPOLineResponse.setPurchaseReferenceMustArriveByDate(new Date(1599471477635L));
    DeliveryDocument gdmPOLineResponse2 = getGdmPOLineResponse("4615852072", 8);
    gdmPOLineResponse2.setPurchaseReferenceMustArriveByDate(new Date(1599538169418L));
    List<DeliveryDocument> deliveryDocumentList = new ArrayList<>();
    deliveryDocumentList.add(gdmPOLineResponse);
    deliveryDocumentList.add(gdmPOLineResponse2);
    String OFResponse = MockACLMessageData.getBuildContainerResponseForACLReceiving();
    PossibleUPC possibleUPC =
        PossibleUPC.builder()
            .consumableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc())
            .orderableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getItemUpc())
            .build();
    PurchaseOrderInfo purchaseOrderInfo =
        new PurchaseOrderInfo(
            deliveryNum, "4615852072", 8, JacksonParser.writeValueAsString(possibleUPC));

    // create user in DB
    createUserLocationMapping(location, "sysadmin.s32818", LocationType.ONLINE);
    createUserLocationMapping(location, "rcv.s32818", LocationType.ONLINE);

    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(null);
    when(labelDataService.getPurchaseOrderInfoFromLabelData(deliveryNum, lpn))
        .thenReturn(purchaseOrderInfo);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            any(HttpHeaders.class)))
        .thenReturn(deliveryDocumentList);
    doReturn(true)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.IS_RECEIPT_AGGREGATOR_CHECK_ENABLED);

    List<ReceiptSummaryEachesResponse> receivedQtyResponse = new ArrayList<>();
    receivedQtyResponse.add(new ReceiptSummaryEachesResponse(poNum, poLine, null, 0L));
    receivedQtyResponse.add(new ReceiptSummaryEachesResponse("4615852072", 8, null, 0L));
    doReturn(receivedQtyResponse)
        .when(receiptService)
        .receivedQtyByPoAndPoLineList(anyList(), anySet());
    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenReturn(OFResponse);

    LocationInfo locationInfo = new LocationInfo();
    locationInfo.setIsOnline(Boolean.TRUE);
    when(containerPersisterService.saveContainer(any()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.TRUE);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);
    try {
      lpnReceivingService.receiveByLPN(
          aclVerificationEventMessage.getLpn(),
          Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
          aclVerificationEventMessage.getLocationId());
    } catch (ReceivingException e) {
      fail("No exception is expected");
    }

    verify(containerPersisterService, times(1)).getContainerDetails(lpn);
    verify(labelDataService, times(1)).getPurchaseOrderInfoFromLabelData(deliveryNum, lpn);

    // verify headers are created properly and GDM is called
    ArgumentCaptor<HttpHeaders> captor = ArgumentCaptor.forClass(HttpHeaders.class);
    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            captor.capture());

    HttpHeaders httpHeaders = captor.getValue();
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM),
        TenantContext.getFacilityNum().toString());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE),
        TenantContext.getFacilityCountryCode());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY),
        TenantContext.getCorrelationId());

    verify(deliveryService, times(0)).reOpenDelivery(any(), any());
    verify(receiptService, times(1)).receivedQtyByPoAndPoLineList(anyList(), anySet());

    // verify OF request payload
    ArgumentCaptor<FdeCreateContainerRequest> fdeCaptor =
        ArgumentCaptor.forClass(FdeCreateContainerRequest.class);
    verify(fdeService, times(1)).receive(fdeCaptor.capture(), eq(httpHeaders));
    assertEquals(
        JacksonParser.writeValueAsStringExcludeNull(fdeCaptor.getValue()).replaceAll("\\s+", ""),
        MockACLMessageData.getBuildContainerRequestForACLReceiving().replaceAll("\\s+", ""));

    // verify WFM contract for update & complete
    ArgumentCaptor<ReceivingJMSEvent> wfmPublishCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(3))
        .publish(
            eq(ReceivingConstants.PUB_INSTRUCTION_TOPIC),
            wfmPublishCaptor.capture(),
            eq(Boolean.FALSE));
    List<ReceivingJMSEvent> receivingJMSEventList = wfmPublishCaptor.getAllValues();
    String WFMUpdateMessage = receivingJMSEventList.get(1).getMessageBody();
    String WFMCompleteMessage = receivingJMSEventList.get(2).getMessageBody();

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclWFMUpdateMessageSchema.json")
                            .getCanonicalPath()))),
            WFMUpdateMessage));
    // assertTrue(WFMUpdateMessage.contains("\"userId\":\"rcv\""));
    // assertTrue(WFMUpdateMessage.contains("\"activityName\":\"ACL-DOOR\""));

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclWFMCompleteMessageSchema.json")
                            .getCanonicalPath()))),
            WFMCompleteMessage));

    // verify container was created properly
    ArgumentCaptor<Container> containerCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerPersisterService, times(1)).saveContainer(containerCaptor.capture());

    Container savedContainer = containerCaptor.getValue();
    assertNotNull(savedContainer.getCompleteTs());
    assertEquals(savedContainer.getTrackingId(), lpn);
    assertNull(savedContainer.getParentTrackingId());
    assertEquals(savedContainer.getContainerItems().size(), 1);
    // assertEquals(savedContainer.getCreateUser(), "rcv");
    assertTrue(CollectionUtils.isEmpty(savedContainer.getChildContainers()));
    assertTrue(savedContainer.getIsConveyable());
    assertTrue(savedContainer.getOnConveyor());

    // verify instruction was saved
    ArgumentCaptor<Instruction> instructionCaptor = ArgumentCaptor.forClass(Instruction.class);
    verify(instructionRepository, times(1)).save(instructionCaptor.capture());
    Instruction savedInstruction = instructionCaptor.getValue();
    assertEquals(savedInstruction.getActivityName(), "ACL");

    // verify receipts were saved
    // verify(receiptService,
    // times(1)).createReceiptsFromInstruction(any(UpdateInstructionRequest.class), eq(null),
    // eq("rcv"));
    // validate contract for published container
    ArgumentCaptor<ContainerDTO> containerPublishCaptor =
        ArgumentCaptor.forClass(ContainerDTO.class);
    verify(JMSReceiptPublisher, times(1)).publish(containerPublishCaptor.capture(), any());
    String publishedContainer = gson.toJson(containerPublishCaptor.getValue());
    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclReceivingContainerMessageSchema.json")
                            .getCanonicalPath()))),
            publishedContainer));
  }

  @Test
  public void
      testReceiveByLPNItemPresentInMultiplePOAndAutoSelectEnabled_selectAgainstAllowedOverageByMABD()
          throws ReceivingException, IOException {
    Long deliveryNum = 94769060L;
    String lpn = "c32987000000000000000001";
    String location = "D102A";
    String poNum = "3615852071";
    Integer poLine = 8;
    ACLVerificationEventMessage aclVerificationEventMessage = new ACLVerificationEventMessage();
    aclVerificationEventMessage.setGroupNbr(deliveryNum.toString());
    aclVerificationEventMessage.setLpn(lpn);
    aclVerificationEventMessage.setLocationId(location);
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse(poNum, poLine);
    gdmPOLineResponse.setPurchaseReferenceMustArriveByDate(new Date(1599471477635L));
    DeliveryDocument gdmPOLineResponse2 = getGdmPOLineResponse("4615852072", 8);
    gdmPOLineResponse2.setPurchaseReferenceMustArriveByDate(new Date(1599538169418L));
    List<DeliveryDocument> deliveryDocumentList = new ArrayList<>();
    deliveryDocumentList.add(gdmPOLineResponse);
    deliveryDocumentList.add(gdmPOLineResponse2);
    String OFResponse = MockACLMessageData.getBuildContainerResponseForACLReceiving();
    PossibleUPC possibleUPC =
        PossibleUPC.builder()
            .consumableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc())
            .orderableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getItemUpc())
            .build();
    PurchaseOrderInfo purchaseOrderInfo =
        new PurchaseOrderInfo(
            deliveryNum, poNum, poLine, JacksonParser.writeValueAsString(possibleUPC));

    // create user in DB
    createUserLocationMapping(location, "sysadmin.s32818", LocationType.ONLINE);
    createUserLocationMapping(location, "rcv.s32818", LocationType.ONLINE);
    doReturn(true)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.IS_RECEIPT_AGGREGATOR_CHECK_ENABLED);
    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(null);
    when(labelDataService.getPurchaseOrderInfoFromLabelData(deliveryNum, lpn))
        .thenReturn(purchaseOrderInfo);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            any(HttpHeaders.class)))
        .thenReturn(deliveryDocumentList);
    // exhausted ordered qty
    List<ReceiptSummaryEachesResponse> receivedQtyResponse = new ArrayList<>();
    receivedQtyResponse.add(new ReceiptSummaryEachesResponse(poNum, poLine, null, 10L));
    receivedQtyResponse.add(new ReceiptSummaryEachesResponse("4615852072", 8, null, 10L));
    doReturn(receivedQtyResponse)
        .when(receiptService)
        .receivedQtyByPoAndPoLineList(anyList(), anySet());

    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenReturn(OFResponse);

    LocationInfo locationInfo = new LocationInfo();
    locationInfo.setIsOnline(Boolean.TRUE);
    when(containerPersisterService.saveContainer(any()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.TRUE);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);
    try {
      lpnReceivingService.receiveByLPN(
          aclVerificationEventMessage.getLpn(),
          Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
          aclVerificationEventMessage.getLocationId());
    } catch (ReceivingException e) {
      fail("No exception is expected");
    }

    verify(containerPersisterService, times(1)).getContainerDetails(lpn);
    verify(labelDataService, times(1)).getPurchaseOrderInfoFromLabelData(deliveryNum, lpn);

    // verify headers are created properly and GDM is called
    ArgumentCaptor<HttpHeaders> captor = ArgumentCaptor.forClass(HttpHeaders.class);
    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            captor.capture());

    HttpHeaders httpHeaders = captor.getValue();
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM),
        TenantContext.getFacilityNum().toString());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE),
        TenantContext.getFacilityCountryCode());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY),
        TenantContext.getCorrelationId());

    verify(deliveryService, times(0)).reOpenDelivery(any(), any());
    verify(receiptService, times(1)).receivedQtyByPoAndPoLineList(anyList(), anySet());

    // verify OF request payload
    ArgumentCaptor<FdeCreateContainerRequest> fdeCaptor =
        ArgumentCaptor.forClass(FdeCreateContainerRequest.class);
    verify(fdeService, times(1)).receive(fdeCaptor.capture(), eq(httpHeaders));
    assertEquals(
        JacksonParser.writeValueAsStringExcludeNull(fdeCaptor.getValue()).replaceAll("\\s+", ""),
        MockACLMessageData.getBuildContainerRequestForACLReceivingAgainstAllowedOvg()
            .replaceAll("\\s+", ""));

    // verify WFM contract for update & complete
    ArgumentCaptor<ReceivingJMSEvent> wfmPublishCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(3))
        .publish(
            eq(ReceivingConstants.PUB_INSTRUCTION_TOPIC),
            wfmPublishCaptor.capture(),
            eq(Boolean.FALSE));
    List<ReceivingJMSEvent> receivingJMSEventList = wfmPublishCaptor.getAllValues();
    String WFMUpdateMessage = receivingJMSEventList.get(1).getMessageBody();
    String WFMCompleteMessage = receivingJMSEventList.get(2).getMessageBody();

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclWFMUpdateMessageSchema.json")
                            .getCanonicalPath()))),
            WFMUpdateMessage));
    // assertTrue(WFMUpdateMessage.contains("\"userId\":\"rcv\""));
    // assertTrue(WFMUpdateMessage.contains("\"activityName\":\"ACL-DOOR\""));

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclWFMCompleteMessageSchema.json")
                            .getCanonicalPath()))),
            WFMCompleteMessage));

    // verify container was created properly
    ArgumentCaptor<Container> containerCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerPersisterService, times(1)).saveContainer(containerCaptor.capture());

    Container savedContainer = containerCaptor.getValue();
    assertNotNull(savedContainer.getCompleteTs());
    assertEquals(savedContainer.getTrackingId(), lpn);
    assertNull(savedContainer.getParentTrackingId());
    assertEquals(savedContainer.getContainerItems().size(), 1);
    // assertEquals(savedContainer.getCreateUser(), "rcv");
    assertTrue(CollectionUtils.isEmpty(savedContainer.getChildContainers()));
    assertTrue(savedContainer.getIsConveyable());
    assertTrue(savedContainer.getOnConveyor());

    // verify instruction was saved
    ArgumentCaptor<Instruction> instructionCaptor = ArgumentCaptor.forClass(Instruction.class);
    verify(instructionRepository, times(1)).save(instructionCaptor.capture());
    Instruction savedInstruction = instructionCaptor.getValue();
    assertEquals(savedInstruction.getActivityName(), "ACL");

    // verify receipts were saved
    // verify(receiptService,
    // times(1)).createReceiptsFromInstruction(any(UpdateInstructionRequest.class), eq(null),
    // eq("rcv"));
    // validate contract for published container
    ArgumentCaptor<ContainerDTO> containerPublishCaptor =
        ArgumentCaptor.forClass(ContainerDTO.class);
    verify(JMSReceiptPublisher, times(1)).publish(containerPublishCaptor.capture(), any());
    String publishedContainer = gson.toJson(containerPublishCaptor.getValue());
    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclReceivingContainerMessageSchema.json")
                            .getCanonicalPath()))),
            publishedContainer));
  }

  @Test
  public void testReceiveByLPNItemPresentInMultiplePOAndAutoSelectEnabled_NoOpenQtyAvailable()
      throws ReceivingException, IOException {
    Long deliveryNum = 94769060L;
    String lpn = "c32987000000000000000001";
    String location = "D102A";
    String poNum = "3615852071";
    Integer poLine = 8;
    ACLVerificationEventMessage aclVerificationEventMessage = new ACLVerificationEventMessage();
    aclVerificationEventMessage.setGroupNbr(deliveryNum.toString());
    aclVerificationEventMessage.setLpn(lpn);
    aclVerificationEventMessage.setLocationId(location);
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse(poNum, poLine);
    gdmPOLineResponse.setPurchaseReferenceMustArriveByDate(new Date(1599471477635L));
    DeliveryDocument gdmPOLineResponse2 = getGdmPOLineResponse("4615852072", 8);
    gdmPOLineResponse2.setPurchaseReferenceMustArriveByDate(new Date(1599538169418L));
    List<DeliveryDocument> deliveryDocumentList = new ArrayList<>();
    deliveryDocumentList.add(gdmPOLineResponse);
    deliveryDocumentList.add(gdmPOLineResponse2);
    PossibleUPC possibleUPC =
        PossibleUPC.builder()
            .consumableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc())
            .orderableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getItemUpc())
            .build();
    PurchaseOrderInfo purchaseOrderInfo =
        new PurchaseOrderInfo(
            deliveryNum, poNum, poLine, JacksonParser.writeValueAsString(possibleUPC));

    // create user in DB
    createUserLocationMapping(location, "sysadmin.s32818", LocationType.ONLINE);
    createUserLocationMapping(location, "rcv.s32818", LocationType.ONLINE);
    doReturn(true)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.IS_RECEIPT_AGGREGATOR_CHECK_ENABLED);
    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(null);
    when(labelDataService.getPurchaseOrderInfoFromLabelData(deliveryNum, lpn))
        .thenReturn(purchaseOrderInfo);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            any(HttpHeaders.class)))
        .thenReturn(deliveryDocumentList);

    // exhausted ordered qty + allowed overage
    List<ReceiptSummaryEachesResponse> receivedQtyResponse = new ArrayList<>();
    receivedQtyResponse.add(new ReceiptSummaryEachesResponse(poNum, poLine, null, 15L));
    receivedQtyResponse.add(new ReceiptSummaryEachesResponse("4615852072", 8, null, 15L));
    doReturn(receivedQtyResponse)
        .when(receiptService)
        .receivedQtyByPoAndPoLineList(anyList(), anySet());

    LocationInfo locationInfo = new LocationInfo();
    locationInfo.setIsOnline(Boolean.TRUE);
    when(containerPersisterService.saveContainer(any()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.TRUE);
    when(appConfig.getSorterExceptionTopic()).thenReturn(SORTER_EXCEPTION_TOPIC);

    when(exceptionContainerHandlerFactory.exceptionContainerHandler(any(ContainerException.class)))
        .thenReturn(overageExceptionContainerHandler);
    try {
      lpnReceivingService.receiveByLPN(
          aclVerificationEventMessage.getLpn(),
          Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
          aclVerificationEventMessage.getLocationId());
    } catch (ReceivingException e) {
      fail("No exception is expected");
    }

    verify(containerPersisterService, times(1)).getContainerDetails(lpn);
    verify(receiptService, times(1)).receivedQtyByPoAndPoLineList(anyList(), anySet());

    // verify headers are created properly and GDM is called
    ArgumentCaptor<HttpHeaders> captor = ArgumentCaptor.forClass(HttpHeaders.class);
    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            captor.capture());

    HttpHeaders httpHeaders = captor.getValue();
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM),
        TenantContext.getFacilityNum().toString());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE),
        TenantContext.getFacilityCountryCode());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY),
        TenantContext.getCorrelationId());

    verify(deliveryService, times(0)).reOpenDelivery(any(), any());

    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            captor.capture());
    verify(receiptService, times(1)).receivedQtyByPoAndPoLineList(anyList(), anySet());

    verify(containerPersisterService, times(1)).saveContainer(any());

    // verify container published
    ArgumentCaptor<ReceivingJMSEvent> exceptionContainerPublishCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(1))
        .publish(
            eq(ReceivingConstants.PUB_RECEIPTS_EXCEPTION_TOPIC),
            exceptionContainerPublishCaptor.capture(),
            eq(Boolean.TRUE));
    ReceivingJMSEvent event = exceptionContainerPublishCaptor.getValue();

    // validate headers sent to inventory
    Map<String, Object> messageHeaders = event.getHeaders();
    assertNotNull(messageHeaders.get(ReceivingConstants.TENENT_FACLITYNUM));
    assertNotNull(messageHeaders.get(ReceivingConstants.TENENT_COUNTRY_CODE));
    assertNotNull(messageHeaders.get(ReceivingConstants.USER_ID_HEADER_KEY));
    assertNotNull(messageHeaders.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY));

    // validate message contract
    String exceptionContainerPayload = event.getMessageBody();
    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/exceptionContainerMessageSchema.json")
                            .getCanonicalPath()))),
            exceptionContainerPayload));

    // verify message to Sorter
    verify(sorterPublisher, times(1))
        .publishException(anyString(), eq(SorterExceptionReason.OVERAGE), any(Date.class));

    // validate that following were not called
    verify(fdeService, times(0)).receive(any(), any());
    verify(jmsPublisher, times(0))
        .publish(eq(ReceivingConstants.PUB_INSTRUCTION_TOPIC), any(), any());
    verify(jmsPublisher, times(0)).publish(eq(ReceivingConstants.PUB_RECEIPTS_TOPIC), any(), any());
    verify(receiptService, times(0)).createReceiptsFromInstruction(any(), any(), any());
    verify(instructionRepository, times(0)).save(any());
  }

  @Test
  public void
      testReceiveByLPNItemPresentInMultiplePOLAndAutoSelectEnabled_selectAgainstOrderedQtyByMABD()
          throws ReceivingException, IOException {
    Long deliveryNum = 94769060L;
    String lpn = "c32987000000000000000001";
    String location = "D102A";
    String poNum = "3615852071";
    Integer poLine = 8;
    ACLVerificationEventMessage aclVerificationEventMessage = new ACLVerificationEventMessage();
    aclVerificationEventMessage.setGroupNbr(deliveryNum.toString());
    aclVerificationEventMessage.setLpn(lpn);
    aclVerificationEventMessage.setLocationId(location);
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse(poNum, poLine);
    gdmPOLineResponse
        .getDeliveryDocumentLines()
        .addAll(getGdmPOLineResponse(poNum, 9).getDeliveryDocumentLines());
    List<DeliveryDocument> deliveryDocumentList = new ArrayList<>();
    deliveryDocumentList.add(gdmPOLineResponse);
    String OFResponse = MockACLMessageData.getBuildContainerResponseForACLReceiving();
    PossibleUPC possibleUPC =
        PossibleUPC.builder()
            .consumableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc())
            .orderableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getItemUpc())
            .build();
    PurchaseOrderInfo purchaseOrderInfo =
        new PurchaseOrderInfo(
            deliveryNum, "4615852072", 8, JacksonParser.writeValueAsString(possibleUPC));

    // create user in DB
    createUserLocationMapping(location, "sysadmin.s32818", LocationType.ONLINE);
    createUserLocationMapping(location, "rcv.s32818", LocationType.ONLINE);

    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(null);
    when(labelDataService.getPurchaseOrderInfoFromLabelData(deliveryNum, lpn))
        .thenReturn(purchaseOrderInfo);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            any(HttpHeaders.class)))
        .thenReturn(deliveryDocumentList);
    doReturn(true)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.IS_RECEIPT_AGGREGATOR_CHECK_ENABLED);
    List<ReceiptSummaryEachesResponse> receivedQtyResponse = new ArrayList<>();
    receivedQtyResponse.add(new ReceiptSummaryEachesResponse(poNum, poLine, null, 0L));
    receivedQtyResponse.add(new ReceiptSummaryEachesResponse(poNum, 9, null, 0L));
    doReturn(receivedQtyResponse)
        .when(receiptService)
        .receivedQtyByPoAndPoLineList(anyList(), anySet());
    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenReturn(OFResponse);

    LocationInfo locationInfo = new LocationInfo();
    locationInfo.setIsOnline(Boolean.TRUE);
    when(containerPersisterService.saveContainer(any()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.TRUE);
    try {
      lpnReceivingService.receiveByLPN(
          aclVerificationEventMessage.getLpn(),
          Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
          aclVerificationEventMessage.getLocationId());
    } catch (ReceivingException e) {
      fail("No exception is expected");
    }

    verify(containerPersisterService, times(1)).getContainerDetails(lpn);
    verify(labelDataService, times(1)).getPurchaseOrderInfoFromLabelData(deliveryNum, lpn);

    // verify headers are created properly and GDM is called
    ArgumentCaptor<HttpHeaders> captor = ArgumentCaptor.forClass(HttpHeaders.class);
    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            captor.capture());

    HttpHeaders httpHeaders = captor.getValue();
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM),
        TenantContext.getFacilityNum().toString());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE),
        TenantContext.getFacilityCountryCode());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY),
        TenantContext.getCorrelationId());

    verify(deliveryService, times(0)).reOpenDelivery(any(), any());
    verify(receiptService, times(1)).receivedQtyByPoAndPoLineList(anyList(), anySet());

    // verify OF request payload
    ArgumentCaptor<FdeCreateContainerRequest> fdeCaptor =
        ArgumentCaptor.forClass(FdeCreateContainerRequest.class);
    verify(fdeService, times(1)).receive(fdeCaptor.capture(), eq(httpHeaders));
    assertEquals(
        JacksonParser.writeValueAsStringExcludeNull(fdeCaptor.getValue()).replaceAll("\\s+", ""),
        MockACLMessageData.getBuildContainerRequestForACLReceiving().replaceAll("\\s+", ""));

    // verify WFM contract for update & complete
    ArgumentCaptor<ReceivingJMSEvent> wfmPublishCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(3))
        .publish(
            eq(ReceivingConstants.PUB_INSTRUCTION_TOPIC),
            wfmPublishCaptor.capture(),
            eq(Boolean.FALSE));
    List<ReceivingJMSEvent> receivingJMSEventList = wfmPublishCaptor.getAllValues();
    String WFMUpdateMessage = receivingJMSEventList.get(1).getMessageBody();
    String WFMCompleteMessage = receivingJMSEventList.get(2).getMessageBody();

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclWFMUpdateMessageSchema.json")
                            .getCanonicalPath()))),
            WFMUpdateMessage));
    // assertTrue(WFMUpdateMessage.contains("\"userId\":\"rcv\""));
    // assertTrue(WFMUpdateMessage.contains("\"activityName\":\"ACL-DOOR\""));

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclWFMCompleteMessageSchema.json")
                            .getCanonicalPath()))),
            WFMCompleteMessage));

    // verify container was created properly
    ArgumentCaptor<Container> containerCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerPersisterService, times(1)).saveContainer(containerCaptor.capture());

    Container savedContainer = containerCaptor.getValue();
    assertNotNull(savedContainer.getCompleteTs());
    assertEquals(savedContainer.getTrackingId(), lpn);
    assertNull(savedContainer.getParentTrackingId());
    assertEquals(savedContainer.getContainerItems().size(), 1);
    // assertEquals(savedContainer.getCreateUser(), "rcv");
    assertTrue(CollectionUtils.isEmpty(savedContainer.getChildContainers()));
    assertTrue(savedContainer.getIsConveyable());
    assertTrue(savedContainer.getOnConveyor());

    // verify instruction was saved
    ArgumentCaptor<Instruction> instructionCaptor = ArgumentCaptor.forClass(Instruction.class);
    verify(instructionRepository, times(1)).save(instructionCaptor.capture());
    Instruction savedInstruction = instructionCaptor.getValue();
    assertEquals(savedInstruction.getActivityName(), "ACL");

    // verify receipts were saved
    // verify(receiptService,
    // times(1)).createReceiptsFromInstruction(any(UpdateInstructionRequest.class), eq(null),
    // eq("rcv"));
    // validate contract for published container
    ArgumentCaptor<ContainerDTO> containerPublishCaptor =
        ArgumentCaptor.forClass(ContainerDTO.class);
    verify(JMSReceiptPublisher, times(1)).publish(containerPublishCaptor.capture(), any());
    String publishedContainer = gson.toJson(containerPublishCaptor.getValue());
    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclReceivingContainerMessageSchema.json")
                            .getCanonicalPath()))),
            publishedContainer));
  }

  @Test
  public void
      testReceiveByLPNItemPresentInMultiplePOLAndAutoSelectEnabled_selectAgainstAllowedOverageByMABD()
          throws ReceivingException, IOException {
    Long deliveryNum = 94769060L;
    String lpn = "c32987000000000000000001";
    String location = "D102A";
    String poNum = "3615852071";
    Integer poLine = 8;
    ACLVerificationEventMessage aclVerificationEventMessage = new ACLVerificationEventMessage();
    aclVerificationEventMessage.setGroupNbr(deliveryNum.toString());
    aclVerificationEventMessage.setLpn(lpn);
    aclVerificationEventMessage.setLocationId(location);
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse(poNum, poLine);
    gdmPOLineResponse
        .getDeliveryDocumentLines()
        .addAll(getGdmPOLineResponse(poNum, 9).getDeliveryDocumentLines());
    List<DeliveryDocument> deliveryDocumentList = new ArrayList<>();
    deliveryDocumentList.add(gdmPOLineResponse);
    String OFResponse = MockACLMessageData.getBuildContainerResponseForACLReceiving();
    PossibleUPC possibleUPC =
        PossibleUPC.builder()
            .consumableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc())
            .orderableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getItemUpc())
            .build();
    PurchaseOrderInfo purchaseOrderInfo =
        new PurchaseOrderInfo(
            deliveryNum, poNum, poLine, JacksonParser.writeValueAsString(possibleUPC));

    // create user in DB
    createUserLocationMapping(location, "sysadmin.s32818", LocationType.ONLINE);
    createUserLocationMapping(location, "rcv.s32818", LocationType.ONLINE);

    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(null);
    when(labelDataService.getPurchaseOrderInfoFromLabelData(deliveryNum, lpn))
        .thenReturn(purchaseOrderInfo);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            any(HttpHeaders.class)))
        .thenReturn(deliveryDocumentList);
    doReturn(true)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.IS_RECEIPT_AGGREGATOR_CHECK_ENABLED);
    // exhausted ordered qty
    List<ReceiptSummaryEachesResponse> receivedQtyResponse = new ArrayList<>();
    receivedQtyResponse.add(new ReceiptSummaryEachesResponse(poNum, poLine, null, 10L));
    receivedQtyResponse.add(new ReceiptSummaryEachesResponse(poNum, 9, null, 10L));
    doReturn(receivedQtyResponse)
        .when(receiptService)
        .receivedQtyByPoAndPoLineList(anyList(), anySet());

    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenReturn(OFResponse);

    LocationInfo locationInfo = new LocationInfo();
    locationInfo.setIsOnline(Boolean.TRUE);
    when(containerPersisterService.saveContainer(any()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.TRUE);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);
    try {
      lpnReceivingService.receiveByLPN(
          aclVerificationEventMessage.getLpn(),
          Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
          aclVerificationEventMessage.getLocationId());
    } catch (ReceivingException e) {
      fail("No exception is expected");
    }

    verify(containerPersisterService, times(1)).getContainerDetails(lpn);
    verify(labelDataService, times(1)).getPurchaseOrderInfoFromLabelData(deliveryNum, lpn);

    // verify headers are created properly and GDM is called
    ArgumentCaptor<HttpHeaders> captor = ArgumentCaptor.forClass(HttpHeaders.class);
    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            captor.capture());

    HttpHeaders httpHeaders = captor.getValue();
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM),
        TenantContext.getFacilityNum().toString());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE),
        TenantContext.getFacilityCountryCode());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY),
        TenantContext.getCorrelationId());

    verify(deliveryService, times(0)).reOpenDelivery(any(), any());
    verify(receiptService, times(1)).receivedQtyByPoAndPoLineList(anyList(), anySet());

    // verify OF request payload
    ArgumentCaptor<FdeCreateContainerRequest> fdeCaptor =
        ArgumentCaptor.forClass(FdeCreateContainerRequest.class);
    verify(fdeService, times(1)).receive(fdeCaptor.capture(), eq(httpHeaders));
    assertEquals(
        JacksonParser.writeValueAsStringExcludeNull(fdeCaptor.getValue()).replaceAll("\\s+", ""),
        MockACLMessageData.getBuildContainerRequestForACLReceivingAgainstAllowedOvg()
            .replaceAll("\\s+", ""));

    // verify WFM contract for update & complete
    ArgumentCaptor<ReceivingJMSEvent> wfmPublishCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(3))
        .publish(
            eq(ReceivingConstants.PUB_INSTRUCTION_TOPIC),
            wfmPublishCaptor.capture(),
            eq(Boolean.FALSE));
    List<ReceivingJMSEvent> receivingJMSEventList = wfmPublishCaptor.getAllValues();
    String WFMUpdateMessage = receivingJMSEventList.get(1).getMessageBody();
    String WFMCompleteMessage = receivingJMSEventList.get(2).getMessageBody();

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclWFMUpdateMessageSchema.json")
                            .getCanonicalPath()))),
            WFMUpdateMessage));
    // assertTrue(WFMUpdateMessage.contains("\"userId\":\"rcv\""));
    // assertTrue(WFMUpdateMessage.contains("\"activityName\":\"ACL-DOOR\""));

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclWFMCompleteMessageSchema.json")
                            .getCanonicalPath()))),
            WFMCompleteMessage));

    // verify container was created properly
    ArgumentCaptor<Container> containerCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerPersisterService, times(1)).saveContainer(containerCaptor.capture());

    Container savedContainer = containerCaptor.getValue();
    assertNotNull(savedContainer.getCompleteTs());
    assertEquals(savedContainer.getTrackingId(), lpn);
    assertNull(savedContainer.getParentTrackingId());
    assertEquals(savedContainer.getContainerItems().size(), 1);
    // assertEquals(savedContainer.getCreateUser(), "rcv");
    assertTrue(CollectionUtils.isEmpty(savedContainer.getChildContainers()));
    assertTrue(savedContainer.getIsConveyable());
    assertTrue(savedContainer.getOnConveyor());

    // verify instruction was saved
    ArgumentCaptor<Instruction> instructionCaptor = ArgumentCaptor.forClass(Instruction.class);
    verify(instructionRepository, times(1)).save(instructionCaptor.capture());
    Instruction savedInstruction = instructionCaptor.getValue();
    assertEquals(savedInstruction.getActivityName(), "ACL");

    // verify receipts were saved
    // verify(receiptService,
    // times(1)).createReceiptsFromInstruction(any(UpdateInstructionRequest.class), eq(null),
    // eq("rcv"));
    // validate contract for published container
    ArgumentCaptor<ContainerDTO> containerPublishCaptor =
        ArgumentCaptor.forClass(ContainerDTO.class);
    verify(JMSReceiptPublisher, times(1)).publish(containerPublishCaptor.capture(), any());
    String publishedContainer = gson.toJson(containerPublishCaptor.getValue());
    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclReceivingContainerMessageSchema.json")
                            .getCanonicalPath()))),
            publishedContainer));
  }

  @Test
  public void testReceiveByLPNItemPresentInMultiplePOLAndAutoSelectEnabled_NoOpenQtyAvailable()
      throws ReceivingException, IOException {
    Long deliveryNum = 94769060L;
    String lpn = "c32987000000000000000001";
    String location = "D102A";
    String poNum = "3615852071";
    Integer poLine = 8;
    ACLVerificationEventMessage aclVerificationEventMessage = new ACLVerificationEventMessage();
    aclVerificationEventMessage.setGroupNbr(deliveryNum.toString());
    aclVerificationEventMessage.setLpn(lpn);
    aclVerificationEventMessage.setLocationId(location);
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse(poNum, poLine);
    gdmPOLineResponse
        .getDeliveryDocumentLines()
        .addAll(getGdmPOLineResponse(poNum, 5).getDeliveryDocumentLines());
    List<DeliveryDocument> deliveryDocumentList = new ArrayList<>();
    deliveryDocumentList.add(gdmPOLineResponse);
    PossibleUPC possibleUPC =
        PossibleUPC.builder()
            .consumableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc())
            .orderableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getItemUpc())
            .build();
    PurchaseOrderInfo purchaseOrderInfo =
        new PurchaseOrderInfo(
            deliveryNum, poNum, poLine, JacksonParser.writeValueAsString(possibleUPC));

    // create user in DB
    createUserLocationMapping(location, "sysadmin.s32818", LocationType.ONLINE);
    createUserLocationMapping(location, "rcv.s32818", LocationType.ONLINE);

    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(null);
    when(labelDataService.getPurchaseOrderInfoFromLabelData(deliveryNum, lpn))
        .thenReturn(purchaseOrderInfo);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            any(HttpHeaders.class)))
        .thenReturn(deliveryDocumentList);
    doReturn(true)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.IS_RECEIPT_AGGREGATOR_CHECK_ENABLED);
    // exhausted ordered qty + allowed overage
    List<ReceiptSummaryEachesResponse> receivedQtyResponse = new ArrayList<>();
    receivedQtyResponse.add(new ReceiptSummaryEachesResponse(poNum, poLine, null, 15L));
    receivedQtyResponse.add(new ReceiptSummaryEachesResponse(poNum, 5, null, 15L));
    doReturn(receivedQtyResponse)
        .when(receiptService)
        .receivedQtyByPoAndPoLineList(anyList(), anySet());

    LocationInfo locationInfo = new LocationInfo();
    locationInfo.setIsOnline(Boolean.TRUE);
    when(containerPersisterService.saveContainer(any()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.TRUE);
    when(appConfig.getSorterExceptionTopic()).thenReturn(SORTER_EXCEPTION_TOPIC);

    when(exceptionContainerHandlerFactory.exceptionContainerHandler(any(ContainerException.class)))
        .thenReturn(overageExceptionContainerHandler);
    try {
      lpnReceivingService.receiveByLPN(
          aclVerificationEventMessage.getLpn(),
          Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
          aclVerificationEventMessage.getLocationId());
    } catch (ReceivingException e) {
      fail("No exception is expected");
    }

    verify(containerPersisterService, times(1)).getContainerDetails(lpn);
    verify(receiptService, times(1)).receivedQtyByPoAndPoLineList(anyList(), anySet());

    // verify headers are created properly and GDM is called
    ArgumentCaptor<HttpHeaders> captor = ArgumentCaptor.forClass(HttpHeaders.class);
    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            captor.capture());

    HttpHeaders httpHeaders = captor.getValue();
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM),
        TenantContext.getFacilityNum().toString());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE),
        TenantContext.getFacilityCountryCode());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY),
        TenantContext.getCorrelationId());

    verify(deliveryService, times(0)).reOpenDelivery(any(), any());

    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            captor.capture());
    verify(receiptService, times(1)).receivedQtyByPoAndPoLineList(anyList(), anySet());

    verify(containerPersisterService, times(1)).saveContainer(any());

    // verify container published
    ArgumentCaptor<ReceivingJMSEvent> exceptionContainerPublishCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(1))
        .publish(
            eq(ReceivingConstants.PUB_RECEIPTS_EXCEPTION_TOPIC),
            exceptionContainerPublishCaptor.capture(),
            eq(Boolean.TRUE));
    ReceivingJMSEvent event = exceptionContainerPublishCaptor.getValue();

    // validate headers sent to inventory
    Map<String, Object> messageHeaders = event.getHeaders();
    assertNotNull(messageHeaders.get(ReceivingConstants.TENENT_FACLITYNUM));
    assertNotNull(messageHeaders.get(ReceivingConstants.TENENT_COUNTRY_CODE));
    assertNotNull(messageHeaders.get(ReceivingConstants.USER_ID_HEADER_KEY));
    assertNotNull(messageHeaders.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY));

    // validate message contract
    String exceptionContainerPayload = event.getMessageBody();
    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/exceptionContainerMessageSchema.json")
                            .getCanonicalPath()))),
            exceptionContainerPayload));

    // verify message to Sorter
    verify(sorterPublisher, times(1))
        .publishException(anyString(), eq(SorterExceptionReason.OVERAGE), any(Date.class));

    // validate that following were not called
    verify(fdeService, times(0)).receive(any(), any());
    verify(jmsPublisher, times(0))
        .publish(eq(ReceivingConstants.PUB_INSTRUCTION_TOPIC), any(), any());
    verify(jmsPublisher, times(0)).publish(eq(ReceivingConstants.PUB_RECEIPTS_TOPIC), any(), any());
    verify(receiptService, times(0)).createReceiptsFromInstruction(any(), any(), any());
    verify(instructionRepository, times(0)).save(any());
  }

  @Test
  public void
      testReceiveByLPNItemPresentInMultiplePOPOLAndAutoSelectEnabled_selectAgainstOrderedQtyByMABD()
          throws ReceivingException, IOException {
    Long deliveryNum = 94769060L;
    String lpn = "c32987000000000000000001";
    String location = "D102A";
    String poNum = "3615852071";
    Integer poLine = 8;
    ACLVerificationEventMessage aclVerificationEventMessage = new ACLVerificationEventMessage();
    aclVerificationEventMessage.setGroupNbr(deliveryNum.toString());
    aclVerificationEventMessage.setLpn(lpn);
    aclVerificationEventMessage.setLocationId(location);
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse(poNum, poLine);
    gdmPOLineResponse.setPurchaseReferenceMustArriveByDate(new Date(1599471477635L));
    gdmPOLineResponse
        .getDeliveryDocumentLines()
        .addAll(getGdmPOLineResponse(poNum, 9).getDeliveryDocumentLines());
    DeliveryDocument gdmPOLineResponse2 = getGdmPOLineResponse("4615852072", 8);
    gdmPOLineResponse2.setPurchaseReferenceMustArriveByDate(new Date(1599538169418L));
    List<DeliveryDocument> deliveryDocumentList = new ArrayList<>();
    deliveryDocumentList.add(gdmPOLineResponse);
    deliveryDocumentList.add(gdmPOLineResponse2);
    String OFResponse = MockACLMessageData.getBuildContainerResponseForACLReceiving();
    PossibleUPC possibleUPC =
        PossibleUPC.builder()
            .consumableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc())
            .orderableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getItemUpc())
            .build();
    PurchaseOrderInfo purchaseOrderInfo =
        new PurchaseOrderInfo(
            deliveryNum, "4615852072", 8, JacksonParser.writeValueAsString(possibleUPC));

    // create user in DB
    createUserLocationMapping(location, "sysadmin.s32818", LocationType.ONLINE);
    createUserLocationMapping(location, "rcv.s32818", LocationType.ONLINE);

    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(null);
    when(labelDataService.getPurchaseOrderInfoFromLabelData(deliveryNum, lpn))
        .thenReturn(purchaseOrderInfo);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            any(HttpHeaders.class)))
        .thenReturn(deliveryDocumentList);
    doReturn(true)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.IS_RECEIPT_AGGREGATOR_CHECK_ENABLED);
    List<ReceiptSummaryEachesResponse> receivedQtyResponse = new ArrayList<>();
    receivedQtyResponse.add(new ReceiptSummaryEachesResponse(poNum, poLine, null, 0L));
    receivedQtyResponse.add(new ReceiptSummaryEachesResponse(poNum, 9, null, 0L));
    receivedQtyResponse.add(new ReceiptSummaryEachesResponse("4615852072", 8, null, 0L));
    doReturn(receivedQtyResponse)
        .when(receiptService)
        .receivedQtyByPoAndPoLineList(anyList(), anySet());
    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenReturn(OFResponse);

    LocationInfo locationInfo = new LocationInfo();
    locationInfo.setIsOnline(Boolean.TRUE);
    when(containerPersisterService.saveContainer(any()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.TRUE);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);
    try {
      lpnReceivingService.receiveByLPN(
          aclVerificationEventMessage.getLpn(),
          Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
          aclVerificationEventMessage.getLocationId());
    } catch (ReceivingException e) {
      fail("No exception is expected");
    }

    verify(containerPersisterService, times(1)).getContainerDetails(lpn);
    verify(labelDataService, times(1)).getPurchaseOrderInfoFromLabelData(deliveryNum, lpn);

    // verify headers are created properly and GDM is called
    ArgumentCaptor<HttpHeaders> captor = ArgumentCaptor.forClass(HttpHeaders.class);
    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            captor.capture());

    HttpHeaders httpHeaders = captor.getValue();
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM),
        TenantContext.getFacilityNum().toString());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE),
        TenantContext.getFacilityCountryCode());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY),
        TenantContext.getCorrelationId());

    verify(deliveryService, times(0)).reOpenDelivery(any(), any());
    verify(receiptService, times(1)).receivedQtyByPoAndPoLineList(anyList(), anySet());

    // verify OF request payload
    ArgumentCaptor<FdeCreateContainerRequest> fdeCaptor =
        ArgumentCaptor.forClass(FdeCreateContainerRequest.class);
    verify(fdeService, times(1)).receive(fdeCaptor.capture(), eq(httpHeaders));
    assertEquals(
        JacksonParser.writeValueAsStringExcludeNull(fdeCaptor.getValue()).replaceAll("\\s+", ""),
        MockACLMessageData.getBuildContainerRequestForACLReceiving().replaceAll("\\s+", ""));

    // verify WFM contract for update & complete
    ArgumentCaptor<ReceivingJMSEvent> wfmPublishCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(3))
        .publish(
            eq(ReceivingConstants.PUB_INSTRUCTION_TOPIC),
            wfmPublishCaptor.capture(),
            eq(Boolean.FALSE));
    List<ReceivingJMSEvent> receivingJMSEventList = wfmPublishCaptor.getAllValues();
    String WFMUpdateMessage = receivingJMSEventList.get(1).getMessageBody();
    String WFMCompleteMessage = receivingJMSEventList.get(2).getMessageBody();

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclWFMUpdateMessageSchema.json")
                            .getCanonicalPath()))),
            WFMUpdateMessage));
    // assertTrue(WFMUpdateMessage.contains("\"userId\":\"rcv\""));
    // assertTrue(WFMUpdateMessage.contains("\"activityName\":\"ACL-DOOR\""));

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclWFMCompleteMessageSchema.json")
                            .getCanonicalPath()))),
            WFMCompleteMessage));

    // verify container was created properly
    ArgumentCaptor<Container> containerCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerPersisterService, times(1)).saveContainer(containerCaptor.capture());

    Container savedContainer = containerCaptor.getValue();
    assertNotNull(savedContainer.getCompleteTs());
    assertEquals(savedContainer.getTrackingId(), lpn);
    assertNull(savedContainer.getParentTrackingId());
    assertEquals(savedContainer.getContainerItems().size(), 1);
    // assertEquals(savedContainer.getCreateUser(), "rcv");
    assertTrue(CollectionUtils.isEmpty(savedContainer.getChildContainers()));
    assertTrue(savedContainer.getIsConveyable());
    assertTrue(savedContainer.getOnConveyor());

    // verify instruction was saved
    ArgumentCaptor<Instruction> instructionCaptor = ArgumentCaptor.forClass(Instruction.class);
    verify(instructionRepository, times(1)).save(instructionCaptor.capture());
    Instruction savedInstruction = instructionCaptor.getValue();
    assertEquals(savedInstruction.getActivityName(), "ACL");

    // verify receipts were saved
    // verify(receiptService,
    // times(1)).createReceiptsFromInstruction(any(UpdateInstructionRequest.class), eq(null),
    // eq("rcv"));
    // validate contract for published container
    ArgumentCaptor<ContainerDTO> containerPublishCaptor =
        ArgumentCaptor.forClass(ContainerDTO.class);
    verify(JMSReceiptPublisher, times(1)).publish(containerPublishCaptor.capture(), any());
    String publishedContainer = gson.toJson(containerPublishCaptor.getValue());
    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclReceivingContainerMessageSchema.json")
                            .getCanonicalPath()))),
            publishedContainer));
  }

  @Test
  public void
      testReceiveByLPNItemPresentInMultiplePOPOLAndAutoSelectEnabled_selectAgainstAllowedOverageByMABD()
          throws ReceivingException, IOException {
    Long deliveryNum = 94769060L;
    String lpn = "c32987000000000000000001";
    String location = "D102A";
    String poNum = "3615852071";
    Integer poLine = 8;
    ACLVerificationEventMessage aclVerificationEventMessage = new ACLVerificationEventMessage();
    aclVerificationEventMessage.setGroupNbr(deliveryNum.toString());
    aclVerificationEventMessage.setLpn(lpn);
    aclVerificationEventMessage.setLocationId(location);
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse(poNum, poLine);
    gdmPOLineResponse.setPurchaseReferenceMustArriveByDate(new Date(1599471477635L));
    gdmPOLineResponse
        .getDeliveryDocumentLines()
        .addAll(getGdmPOLineResponse(poNum, 9).getDeliveryDocumentLines());
    DeliveryDocument gdmPOLineResponse2 = getGdmPOLineResponse("4615852072", 8);
    gdmPOLineResponse2.setPurchaseReferenceMustArriveByDate(new Date(1599538169418L));
    List<DeliveryDocument> deliveryDocumentList = new ArrayList<>();
    deliveryDocumentList.add(gdmPOLineResponse);
    deliveryDocumentList.add(gdmPOLineResponse2);
    String OFResponse = MockACLMessageData.getBuildContainerResponseForACLReceiving();
    PossibleUPC possibleUPC =
        PossibleUPC.builder()
            .consumableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc())
            .orderableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getItemUpc())
            .build();
    PurchaseOrderInfo purchaseOrderInfo =
        new PurchaseOrderInfo(
            deliveryNum, poNum, poLine, JacksonParser.writeValueAsString(possibleUPC));

    // create user in DB
    createUserLocationMapping(location, "sysadmin.s32818", LocationType.ONLINE);
    createUserLocationMapping(location, "rcv.s32818", LocationType.ONLINE);

    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(null);
    when(labelDataService.getPurchaseOrderInfoFromLabelData(deliveryNum, lpn))
        .thenReturn(purchaseOrderInfo);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            any(HttpHeaders.class)))
        .thenReturn(deliveryDocumentList);
    doReturn(true)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.IS_RECEIPT_AGGREGATOR_CHECK_ENABLED);
    // exhausted ordered qty
    List<ReceiptSummaryEachesResponse> receivedQtyResponse = new ArrayList<>();
    receivedQtyResponse.add(new ReceiptSummaryEachesResponse(poNum, poLine, null, 10L));
    receivedQtyResponse.add(new ReceiptSummaryEachesResponse(poNum, 9, null, 10L));
    receivedQtyResponse.add(new ReceiptSummaryEachesResponse("4615852072", 8, null, 10L));
    doReturn(receivedQtyResponse)
        .when(receiptService)
        .receivedQtyByPoAndPoLineList(anyList(), anySet());

    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenReturn(OFResponse);

    LocationInfo locationInfo = new LocationInfo();
    locationInfo.setIsOnline(Boolean.TRUE);
    when(containerPersisterService.saveContainer(any()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.TRUE);
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);
    try {
      lpnReceivingService.receiveByLPN(
          aclVerificationEventMessage.getLpn(),
          Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
          aclVerificationEventMessage.getLocationId());
    } catch (ReceivingException e) {
      fail("No exception is expected");
    }

    verify(containerPersisterService, times(1)).getContainerDetails(lpn);
    verify(labelDataService, times(1)).getPurchaseOrderInfoFromLabelData(deliveryNum, lpn);

    // verify headers are created properly and GDM is called
    ArgumentCaptor<HttpHeaders> captor = ArgumentCaptor.forClass(HttpHeaders.class);
    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            captor.capture());

    HttpHeaders httpHeaders = captor.getValue();
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM),
        TenantContext.getFacilityNum().toString());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE),
        TenantContext.getFacilityCountryCode());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY),
        TenantContext.getCorrelationId());

    verify(deliveryService, times(0)).reOpenDelivery(any(), any());
    verify(receiptService, times(1)).receivedQtyByPoAndPoLineList(anyList(), anySet());

    // verify OF request payload
    ArgumentCaptor<FdeCreateContainerRequest> fdeCaptor =
        ArgumentCaptor.forClass(FdeCreateContainerRequest.class);
    verify(fdeService, times(1)).receive(fdeCaptor.capture(), eq(httpHeaders));
    assertEquals(
        JacksonParser.writeValueAsStringExcludeNull(fdeCaptor.getValue()).replaceAll("\\s+", ""),
        MockACLMessageData.getBuildContainerRequestForACLReceivingAgainstAllowedOvg()
            .replaceAll("\\s+", ""));

    // verify WFM contract for update & complete
    ArgumentCaptor<ReceivingJMSEvent> wfmPublishCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(3))
        .publish(
            eq(ReceivingConstants.PUB_INSTRUCTION_TOPIC),
            wfmPublishCaptor.capture(),
            eq(Boolean.FALSE));
    List<ReceivingJMSEvent> receivingJMSEventList = wfmPublishCaptor.getAllValues();
    String WFMUpdateMessage = receivingJMSEventList.get(1).getMessageBody();
    String WFMCompleteMessage = receivingJMSEventList.get(2).getMessageBody();

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclWFMUpdateMessageSchema.json")
                            .getCanonicalPath()))),
            WFMUpdateMessage));
    // assertTrue(WFMUpdateMessage.contains("\"userId\":\"rcv\""));
    // assertTrue(WFMUpdateMessage.contains("\"activityName\":\"ACL-DOOR\""));

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclWFMCompleteMessageSchema.json")
                            .getCanonicalPath()))),
            WFMCompleteMessage));

    // verify container was created properly
    ArgumentCaptor<Container> containerCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerPersisterService, times(1)).saveContainer(containerCaptor.capture());

    Container savedContainer = containerCaptor.getValue();
    assertNotNull(savedContainer.getCompleteTs());
    assertEquals(savedContainer.getTrackingId(), lpn);
    assertNull(savedContainer.getParentTrackingId());
    assertEquals(savedContainer.getContainerItems().size(), 1);
    // assertEquals(savedContainer.getCreateUser(), "rcv");
    assertTrue(CollectionUtils.isEmpty(savedContainer.getChildContainers()));
    assertTrue(savedContainer.getIsConveyable());
    assertTrue(savedContainer.getOnConveyor());

    // verify instruction was saved
    ArgumentCaptor<Instruction> instructionCaptor = ArgumentCaptor.forClass(Instruction.class);
    verify(instructionRepository, times(1)).save(instructionCaptor.capture());
    Instruction savedInstruction = instructionCaptor.getValue();
    assertEquals(savedInstruction.getActivityName(), "ACL");

    // verify receipts were saved
    // verify(receiptService,
    // times(1)).createReceiptsFromInstruction(any(UpdateInstructionRequest.class), eq(null),
    // eq("rcv"));
    // validate contract for published container
    ArgumentCaptor<ContainerDTO> containerPublishCaptor =
        ArgumentCaptor.forClass(ContainerDTO.class);
    verify(JMSReceiptPublisher, times(1)).publish(containerPublishCaptor.capture(), any());
    String publishedContainer = gson.toJson(containerPublishCaptor.getValue());
    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclReceivingContainerMessageSchema.json")
                            .getCanonicalPath()))),
            publishedContainer));
  }

  @Test
  public void testReceiveByLPNItemPresentInMultiplePOPOLAndAutoSelectEnabled_NoOpenQtyAvailable()
      throws ReceivingException, IOException {
    Long deliveryNum = 94769060L;
    String lpn = "c32987000000000000000001";
    String location = "D102A";
    String poNum = "3615852071";
    Integer poLine = 8;
    ACLVerificationEventMessage aclVerificationEventMessage = new ACLVerificationEventMessage();
    aclVerificationEventMessage.setGroupNbr(deliveryNum.toString());
    aclVerificationEventMessage.setLpn(lpn);
    aclVerificationEventMessage.setLocationId(location);
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse(poNum, poLine);
    gdmPOLineResponse.setPurchaseReferenceMustArriveByDate(new Date(1599471477635L));
    gdmPOLineResponse
        .getDeliveryDocumentLines()
        .addAll(getGdmPOLineResponse(poNum, 9).getDeliveryDocumentLines());
    DeliveryDocument gdmPOLineResponse2 = getGdmPOLineResponse("4615852072", 8);
    gdmPOLineResponse2.setPurchaseReferenceMustArriveByDate(new Date(1599538169418L));
    List<DeliveryDocument> deliveryDocumentList = new ArrayList<>();
    deliveryDocumentList.add(gdmPOLineResponse);
    deliveryDocumentList.add(gdmPOLineResponse2);
    PossibleUPC possibleUPC =
        PossibleUPC.builder()
            .consumableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc())
            .orderableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getItemUpc())
            .build();
    PurchaseOrderInfo purchaseOrderInfo =
        new PurchaseOrderInfo(
            deliveryNum, poNum, poLine, JacksonParser.writeValueAsString(possibleUPC));

    // create user in DB
    createUserLocationMapping(location, "sysadmin.s32818", LocationType.ONLINE);
    createUserLocationMapping(location, "rcv.s32818", LocationType.ONLINE);

    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(null);
    when(labelDataService.getPurchaseOrderInfoFromLabelData(deliveryNum, lpn))
        .thenReturn(purchaseOrderInfo);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            any(HttpHeaders.class)))
        .thenReturn(deliveryDocumentList);

    doReturn(true)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.IS_RECEIPT_AGGREGATOR_CHECK_ENABLED);
    // exhausted ordered qty + allowed overage
    List<ReceiptSummaryEachesResponse> receivedQtyResponse = new ArrayList<>();
    receivedQtyResponse.add(new ReceiptSummaryEachesResponse(poNum, poLine, null, 15L));
    receivedQtyResponse.add(new ReceiptSummaryEachesResponse(poNum, 9, null, 15L));
    receivedQtyResponse.add(new ReceiptSummaryEachesResponse("4615852072", 8, null, 15L));
    doReturn(receivedQtyResponse)
        .when(receiptService)
        .receivedQtyByPoAndPoLineList(anyList(), anySet());

    LocationInfo locationInfo = new LocationInfo();
    locationInfo.setIsOnline(Boolean.TRUE);
    when(containerPersisterService.saveContainer(any()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.TRUE);
    when(appConfig.getSorterExceptionTopic()).thenReturn(SORTER_EXCEPTION_TOPIC);

    when(exceptionContainerHandlerFactory.exceptionContainerHandler(any(ContainerException.class)))
        .thenReturn(overageExceptionContainerHandler);

    try {
      lpnReceivingService.receiveByLPN(
          aclVerificationEventMessage.getLpn(),
          Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
          aclVerificationEventMessage.getLocationId());
    } catch (ReceivingException e) {
      fail("No exception is expected");
    }

    verify(containerPersisterService, times(1)).getContainerDetails(lpn);
    verify(receiptService, times(1)).receivedQtyByPoAndPoLineList(anyList(), anySet());

    // verify headers are created properly and GDM is called
    ArgumentCaptor<HttpHeaders> captor = ArgumentCaptor.forClass(HttpHeaders.class);
    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            captor.capture());

    HttpHeaders httpHeaders = captor.getValue();
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM),
        TenantContext.getFacilityNum().toString());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE),
        TenantContext.getFacilityCountryCode());
    assertEquals(
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY),
        TenantContext.getCorrelationId());

    verify(deliveryService, times(0)).reOpenDelivery(any(), any());

    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            captor.capture());
    verify(receiptService, times(1)).receivedQtyByPoAndPoLineList(anyList(), anySet());

    verify(containerPersisterService, times(1)).saveContainer(any());

    // verify container published
    ArgumentCaptor<ReceivingJMSEvent> exceptionContainerPublishCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(1))
        .publish(
            eq(ReceivingConstants.PUB_RECEIPTS_EXCEPTION_TOPIC),
            exceptionContainerPublishCaptor.capture(),
            eq(Boolean.TRUE));
    ReceivingJMSEvent event = exceptionContainerPublishCaptor.getValue();

    // validate headers sent to inventory
    Map<String, Object> messageHeaders = event.getHeaders();
    assertNotNull(messageHeaders.get(ReceivingConstants.TENENT_FACLITYNUM));
    assertNotNull(messageHeaders.get(ReceivingConstants.TENENT_COUNTRY_CODE));
    assertNotNull(messageHeaders.get(ReceivingConstants.USER_ID_HEADER_KEY));
    assertNotNull(messageHeaders.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY));

    // validate message contract
    String exceptionContainerPayload = event.getMessageBody();
    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/exceptionContainerMessageSchema.json")
                            .getCanonicalPath()))),
            exceptionContainerPayload));

    // verify message to Sorter
    verify(sorterPublisher, times(1))
        .publishException(anyString(), eq(SorterExceptionReason.OVERAGE), any(Date.class));

    // validate that following were not called
    verify(fdeService, times(0)).receive(any(), any());
    verify(jmsPublisher, times(0))
        .publish(eq(ReceivingConstants.PUB_INSTRUCTION_TOPIC), any(), any());
    verify(jmsPublisher, times(0)).publish(eq(ReceivingConstants.PUB_RECEIPTS_TOPIC), any(), any());
    verify(receiptService, times(0)).createReceiptsFromInstruction(any(), any(), any());
    verify(instructionRepository, times(0)).save(any());
  }

  @Test
  public void testReceiveByLPNInstructionSaveDisabled() throws ReceivingException, IOException {
    Long deliveryNum = 94769060L;
    String lpn = "c32987000000000000000001";
    String location = "D102A";
    String poNum = "3615852071";
    Integer poLine = 8;
    ACLVerificationEventMessage aclVerificationEventMessage = new ACLVerificationEventMessage();
    aclVerificationEventMessage.setGroupNbr(deliveryNum.toString());
    aclVerificationEventMessage.setLpn(lpn);
    aclVerificationEventMessage.setLocationId(location);
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse(poNum, poLine);
    String OFResponse = MockACLMessageData.getBuildContainerResponseForACLReceiving();
    PossibleUPC possibleUPC =
        PossibleUPC.builder()
            .consumableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc())
            .orderableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getItemUpc())
            .build();
    PurchaseOrderInfo purchaseOrderInfo =
        new PurchaseOrderInfo(
            deliveryNum, poNum, poLine, JacksonParser.writeValueAsString(possibleUPC));

    // create user in DB
    createUserLocationMapping(location, "sysadmin.s32818", LocationType.ONLINE);
    createUserLocationMapping(location, "rcv.s32818", LocationType.ONLINE);

    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(null);
    when(labelDataService.getPurchaseOrderInfoFromLabelData(deliveryNum, lpn))
        .thenReturn(purchaseOrderInfo);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(gdmPOLineResponse));
    doReturn(Boolean.FALSE)
        .when(configUtils)
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.INSTRUCTION_SAVE_ENABLED,
            Boolean.TRUE);
    when(receiptService.getReceivedQtyByPoAndPoLine(poNum, poLine)).thenReturn(0L);

    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenReturn(OFResponse);

    LocationInfo locationInfo = new LocationInfo();
    locationInfo.setIsOnline(Boolean.TRUE);
    when(containerPersisterService.saveContainer(any()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.FALSE);
    try {
      lpnReceivingService.receiveByLPN(
          aclVerificationEventMessage.getLpn(),
          Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
          aclVerificationEventMessage.getLocationId());
    } catch (ReceivingException e) {
      fail("No exception is expected");
    }

    verify(containerPersisterService, times(1)).getContainerDetails(lpn);
    verify(labelDataService, times(1)).getPurchaseOrderInfoFromLabelData(deliveryNum, lpn);

    // verify headers are created properly and GDM is called
    ArgumentCaptor<HttpHeaders> captor = ArgumentCaptor.forClass(HttpHeaders.class);
    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            captor.capture());
    HttpHeaders httpHeaders = captor.getValue();

    verify(deliveryService, times(0)).reOpenDelivery(any(), any());
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(poNum, poLine);

    // verify OF request payload
    ArgumentCaptor<FdeCreateContainerRequest> fdeCaptor =
        ArgumentCaptor.forClass(FdeCreateContainerRequest.class);
    verify(fdeService, times(1)).receive(fdeCaptor.capture(), eq(httpHeaders));
    assertEquals(
        JacksonParser.writeValueAsStringExcludeNull(fdeCaptor.getValue()).replaceAll("\\s+", ""),
        MockACLMessageData.getBuildContainerRequestForACLReceiving().replaceAll("\\s+", ""));

    // verify WFM contract for update & complete
    ArgumentCaptor<ReceivingJMSEvent> wfmPublishCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(3))
        .publish(
            eq(ReceivingConstants.PUB_INSTRUCTION_TOPIC),
            wfmPublishCaptor.capture(),
            eq(Boolean.FALSE));
    List<ReceivingJMSEvent> receivingJMSEventList = wfmPublishCaptor.getAllValues();
    String WFMUpdateMessage = receivingJMSEventList.get(1).getMessageBody();
    String WFMCompleteMessage = receivingJMSEventList.get(2).getMessageBody();

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclWFMUpdateMessageSchema.json")
                            .getCanonicalPath()))),
            WFMUpdateMessage));
    // assertTrue(WFMUpdateMessage.contains("\"userId\":\"rcv\""));
    // assertTrue(WFMUpdateMessage.contains("\"activityName\":\"ACL-DOOR\""));

    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclWFMCompleteMessageSchema.json")
                            .getCanonicalPath()))),
            WFMCompleteMessage));

    // verify container was created properly
    ArgumentCaptor<Container> containerCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerPersisterService, times(1)).saveContainer(containerCaptor.capture());

    Container savedContainer = containerCaptor.getValue();
    assertNotNull(savedContainer.getCompleteTs());
    assertEquals(savedContainer.getTrackingId(), lpn);
    assertNull(savedContainer.getParentTrackingId());
    assertEquals(savedContainer.getContainerItems().size(), 1);
    // assertEquals(savedContainer.getCreateUser(), "rcv");
    assertTrue(CollectionUtils.isEmpty(savedContainer.getChildContainers()));
    assertTrue(savedContainer.getIsConveyable());
    assertTrue(savedContainer.getOnConveyor());

    // verify instruction save was not called
    verify(instructionRepository, times(0)).save(any());

    // verify receipts were saved
    // verify(receiptService,
    // times(1)).createReceiptsFromInstruction(any(UpdateInstructionRequest.class), eq(null),
    // eq("rcv"));
    // validate contract for published container
    ArgumentCaptor<ContainerDTO> containerPublishCaptor =
        ArgumentCaptor.forClass(ContainerDTO.class);
    verify(JMSReceiptPublisher, times(1)).publish(containerPublishCaptor.capture(), any());
    String publishedContainer = gson.toJson(containerPublishCaptor.getValue());
    assertTrue(
        validateContract(
            new String(
                Files.readAllBytes(
                    Paths.get(
                        new File(
                                "../../receiving-test/src/main/resources/jsonSchema/aclReceivingContainerMessageSchema.json")
                            .getCanonicalPath()))),
            publishedContainer));
  }

  @Test
  public void testReceiveByLPNRoboDepalFloorline() throws ReceivingException {
    Long deliveryNum = 94769060L;
    String lpn = "c32987000000000000000001";
    String location = "CP07FL_C-DEPAL-1A";
    String poNum = "3615852071";
    Integer poLine = 8;
    ACLVerificationEventMessage aclVerificationEventMessage = new ACLVerificationEventMessage();
    aclVerificationEventMessage.setGroupNbr(deliveryNum.toString());
    aclVerificationEventMessage.setLpn(lpn);
    aclVerificationEventMessage.setLocationId(location);
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse(poNum, poLine);
    String OFResponse = MockACLMessageData.getBuildContainerResponseForACLReceiving();
    PossibleUPC possibleUPC =
        PossibleUPC.builder()
            .consumableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc())
            .orderableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getItemUpc())
            .build();
    PurchaseOrderInfo purchaseOrderInfo =
        new PurchaseOrderInfo(
            deliveryNum, poNum, poLine, JacksonParser.writeValueAsString(possibleUPC));

    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(null);
    when(labelDataService.getPurchaseOrderInfoFromLabelData(deliveryNum, lpn))
        .thenReturn(purchaseOrderInfo);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(gdmPOLineResponse));
    doReturn(Boolean.FALSE)
        .when(configUtils)
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.INSTRUCTION_SAVE_ENABLED,
            Boolean.TRUE);
    when(receiptService.getReceivedQtyByPoAndPoLine(poNum, poLine)).thenReturn(0L);
    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenReturn(OFResponse);
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.FALSE);
    when(tenantSpecificConfigReader.getCcmValue(
            any(),
            eq(ReceivingConstants.ROBO_DEPAL_PARENT_FLOORLINES),
            eq(ReceivingConstants.EMPTY_STRING)))
        .thenReturn("CP04FL_C,CP07FL_C");
    when(configUtils.getConfiguredInstance(any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);
    when(containerPersisterService.saveContainer(any()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    try {
      lpnReceivingService.receiveByLPN(
          aclVerificationEventMessage.getLpn(),
          Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
          aclVerificationEventMessage.getLocationId());
    } catch (ReceivingException e) {
      fail("No exception is expected");
    }
    verify(tenantSpecificConfigReader, times(1))
        .getCcmValue(
            eq(TenantContext.getFacilityNum()),
            eq(ReceivingConstants.ROBO_DEPAL_PARENT_FLOORLINES),
            eq(ReceivingConstants.EMPTY_STRING));
  }

  @Test
  public void testReceiveByLPNItemPresentInCancelledPoLine() throws ReceivingException {
    Long deliveryNum = 94769060L;
    String lpn = "c32987000000000000000001";
    String location = "D102A";
    String poNum = "3615852071";
    Integer poLine = 8;
    ACLVerificationEventMessage aclVerificationEventMessage = new ACLVerificationEventMessage();
    aclVerificationEventMessage.setGroupNbr(deliveryNum.toString());
    aclVerificationEventMessage.setLpn(lpn);
    aclVerificationEventMessage.setLocationId(location);
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse(poNum, poLine);
    gdmPOLineResponse.setPurchaseReferenceMustArriveByDate(new Date(1599471477635L));
    gdmPOLineResponse
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseReferenceLineStatus(POLineStatus.CANCELLED.name());
    DeliveryDocument gdmPOLineResponse2 = getGdmPOLineResponse("4615852072", 8);
    gdmPOLineResponse2.setPurchaseReferenceMustArriveByDate(new Date(1599538169418L));
    List<DeliveryDocument> deliveryDocumentList = new ArrayList<>();
    deliveryDocumentList.add(gdmPOLineResponse);
    deliveryDocumentList.add(gdmPOLineResponse2);
    PossibleUPC possibleUPC =
        PossibleUPC.builder()
            .consumableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc())
            .orderableGTIN(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getItemUpc())
            .build();
    PurchaseOrderInfo purchaseOrderInfo =
        new PurchaseOrderInfo(
            deliveryNum, poNum, poLine, JacksonParser.writeValueAsString(possibleUPC));

    when(containerPersisterService.getContainerDetails(aclVerificationEventMessage.getLpn()))
        .thenReturn(null);
    when(labelDataService.getPurchaseOrderInfoFromLabelData(deliveryNum, lpn))
        .thenReturn(purchaseOrderInfo);
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            eq(deliveryNum),
            eq(gdmPOLineResponse.getDeliveryDocumentLines().get(0).getCaseUpc()),
            any(HttpHeaders.class)))
        .thenReturn(deliveryDocumentList);
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.TRUE);

    try {
      lpnReceivingService.receiveByLPN(
          aclVerificationEventMessage.getLpn(),
          Long.parseLong(aclVerificationEventMessage.getGroupNbr()),
          aclVerificationEventMessage.getLocationId());
    } catch (ReceivingDataNotFoundException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.AUTO_SELECT_PO_POLINE_FAILED);
    }
  }
}
