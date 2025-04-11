package com.walmart.move.nim.receiving.acc.service;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.acc.config.ACCManagedConfig;
import com.walmart.move.nim.receiving.acc.constants.ACCConstants;
import com.walmart.move.nim.receiving.acc.mock.data.MockACLMessageData;
import com.walmart.move.nim.receiving.acc.model.OverflowLPNReceivingRequest;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.DeliveryDocumentHelper;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceiptPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.SorterPublisher;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.InstructionError;
import com.walmart.move.nim.receiving.core.common.exception.InstructionErrorCode;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.config.KafkaConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.entity.LabelDataLpn;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.message.publisher.JMSReceiptPublisher;
import com.walmart.move.nim.receiving.core.message.publisher.KafkaInstructionMessagePublisher;
import com.walmart.move.nim.receiving.core.message.publisher.KafkaReceiptsMessagePublisher;
import com.walmart.move.nim.receiving.core.message.publisher.MessagePublisher;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.FdeCreateContainerRequest;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryEachesResponse;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.repositories.LabelDataLpnRepository;
import com.walmart.move.nim.receiving.core.repositories.LabelDataRepository;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.core.service.exceptioncontainer.ChannelFlipExceptionContainerHandler;
import com.walmart.move.nim.receiving.core.service.exceptioncontainer.DockTagExceptionContainerHandler;
import com.walmart.move.nim.receiving.core.service.exceptioncontainer.ExceptionContainerHandlerFactory;
import com.walmart.move.nim.receiving.core.service.exceptioncontainer.NoAllocationExceptionContainerHandler;
import com.walmart.move.nim.receiving.core.service.exceptioncontainer.OverageExceptionContainerHandler;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ContainerException;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.mockito.AdditionalAnswers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class OverflowLPNReceivingServiceTest extends ReceivingTestBase {

  @InjectMocks private OverflowLPNReceivingService overflowLPNReceivingService;
  @InjectMocks private LabelDataService labelDataService;
  @InjectMocks private LabelDataLpnService labelDataLpnService;
  @InjectMocks private InstructionHelperService instructionHelperService;
  @InjectMocks private KafkaInstructionMessagePublisher kafkaInstructionPublisher;
  @InjectMocks private InstructionPersisterService instructionPersisterService;
  @InjectMocks private DeliveryDocumentHelper deliveryDocumentHelper;
  @InjectMocks private ContainerService containerService;
  @InjectMocks private KafkaExceptionContainerPublisher kafkaExceptionContainerPublisher;
  @InjectMocks private DockTagExceptionContainerHandler dockTagExceptionContainerHandler;
  @InjectMocks private OverageExceptionContainerHandler overageExceptionContainerHandler;
  @InjectMocks private NoAllocationExceptionContainerHandler noAllocationExceptionContainerHandler;
  @InjectMocks private ChannelFlipExceptionContainerHandler channelFlipExceptionContainerHandler;

  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private LabelDataRepository labelDataRepository;
  @Mock private LabelDataLpnRepository labelDataLpnRepository;
  @Mock private AppConfig appConfig;
  @Mock private ACCManagedConfig accManagedConfig;
  @Mock private ReceiptService receiptService;
  @Mock private ExceptionContainerHandlerFactory exceptionContainerHandlerFactory;
  @Mock private RetryableFDEService fdeService;
  @Mock private JMSReceiptPublisher JMSReceiptPublisher;
  @Mock private KafkaReceiptsMessagePublisher kafkaReceiptPublisher;
  @Mock private DeliveryServiceRetryableImpl deliveryService;
  @Mock private InstructionRepository instructionRepository;
  @Mock private ACCDeliveryMetaDataService accDeliveryMetaDataService;
  @Mock private DefaultLabelIdProcessor defaultLabelIdProcessor;
  @Mock private SorterPublisher sorterPublisher;
  @Mock private DefaultDeliveryDocumentsSearchHandler deliveryDocumentsSearchHandler;
  @Mock private KafkaTemplate secureKafkaTemplate;
  @Mock private KafkaConfig kafkaConfig;
  @Mock private ReceiptPublisher receiptPublisher;
  @Mock private DCFinService dcFinService;

  private OverflowLPNReceivingRequest request;
  private LabelData labelData;
  private LabelDataLpn labelDataLpn;
  private HttpHeaders httpHeaders;
  private Gson gson = new Gson();

  @BeforeClass
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32987);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setCorrelationId("aafa2fcc-d299-4663-aa64-ba6f79704635");
    TenantContext.setAdditionalParams("WMT-UserId", "sysadmin");
    httpHeaders = ReceivingUtils.getHeaders();

    ReflectionTestUtils.setField(containerService, "gson", gson);
    ReflectionTestUtils.setField(containerService, "receiptPublisher", receiptPublisher);
    ReflectionTestUtils.setField(instructionHelperService, "deliveryService", deliveryService);
    ReflectionTestUtils.setField(instructionHelperService, "containerService", containerService);
    ReflectionTestUtils.setField(instructionHelperService, "receiptService", receiptService);
    ReflectionTestUtils.setField(
        overflowLPNReceivingService, "instructionHelperService", instructionHelperService);
    ReflectionTestUtils.setField(
        overflowLPNReceivingService, "deliveryDocumentHelper", deliveryDocumentHelper);
    ReflectionTestUtils.setField(
        overflowLPNReceivingService, "instructionPersisterService", instructionPersisterService);
    ReflectionTestUtils.setField(overflowLPNReceivingService, "containerService", containerService);
    ReflectionTestUtils.setField(labelDataLpnService, "labelDataRepository", labelDataRepository);
    ReflectionTestUtils.setField(
        labelDataLpnService, "labelDataLpnRepository", labelDataLpnRepository);
    ReflectionTestUtils.setField(labelDataService, "labelDataRepository", labelDataRepository);
    ReflectionTestUtils.setField(
        labelDataService, "labelDataLpnRepository", labelDataLpnRepository);
    ReflectionTestUtils.setField(labelDataService, "labelDataLpnService", labelDataLpnService);
    ReflectionTestUtils.setField(
        labelDataService, "tenantSpecificConfigReader", tenantSpecificConfigReader);
    ReflectionTestUtils.setField(overflowLPNReceivingService, "labelDataService", labelDataService);
    ReflectionTestUtils.setField(
        dockTagExceptionContainerHandler, "containerService", containerService);
    ReflectionTestUtils.setField(
        overageExceptionContainerHandler, "containerService", containerService);
    ReflectionTestUtils.setField(
        noAllocationExceptionContainerHandler, "containerService", containerService);
    ReflectionTestUtils.setField(
        channelFlipExceptionContainerHandler, "containerService", containerService);
    ReflectionTestUtils.setField(
        kafkaExceptionContainerPublisher, "secureKafkaTemplate", secureKafkaTemplate);
    ReflectionTestUtils.setField(kafkaExceptionContainerPublisher, "kafkaConfig", kafkaConfig);
    ReflectionTestUtils.setField(kafkaInstructionPublisher, "kafkaTemplate", secureKafkaTemplate);
  }

  @AfterMethod
  public void resetMocks() {
    reset(labelDataRepository);
    reset(labelDataLpnRepository);
    reset(containerPersisterService);
    reset(appConfig);
    reset(fdeService);
    reset(receiptService);
    reset(deliveryService);
    reset(instructionRepository);
    reset(accDeliveryMetaDataService);
    reset(sorterPublisher);
    reset(exceptionContainerHandlerFactory);
    reset(JMSReceiptPublisher);
    reset(kafkaReceiptPublisher);
    reset(deliveryDocumentsSearchHandler);
    reset(accManagedConfig);
    reset(tenantSpecificConfigReader);
    reset(defaultLabelIdProcessor);
    reset(secureKafkaTemplate);
    reset(kafkaConfig);
    reset(receiptPublisher);
    reset(dcFinService);
  }

  @BeforeMethod
  public void beforeMethod() {
    when(tenantSpecificConfigReader.getConfiguredInstance(
            String.valueOf(getFacilityNum()),
            ReceivingConstants.INSTRUCTION_PUBLISHER,
            MessagePublisher.class))
        .thenReturn(kafkaInstructionPublisher);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            String.valueOf(getFacilityNum()),
            ReceivingConstants.SORTER_PUBLISHER,
            SorterPublisher.class))
        .thenReturn(sorterPublisher);

    when(exceptionContainerHandlerFactory.exceptionContainerHandler(any(ContainerException.class)))
        .thenReturn(dockTagExceptionContainerHandler);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            any(),
            eq(ReceivingConstants.EXCEPTION_CONTAINER_PUBLISHER),
            eq(ReceivingConstants.JMS_EXCEPTION_CONTAINER_PUBLISHER),
            any()))
        .thenReturn(kafkaExceptionContainerPublisher);
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            getFacilityNum().toString(), ReceivingConstants.INSTRUCTION_SAVE_ENABLED, Boolean.TRUE);
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
        .isFeatureFlagEnabled(ReceivingConstants.ENABLE_NO_DELIVERY_DOC_SORTER_DIVERT);
    doReturn(Boolean.FALSE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ACCConstants.ENABLE_INVALID_ALLOCATIONS_EXCEPTION_CONTAINER_PUBLISH);
    doReturn(Boolean.FALSE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ACCConstants.ENABLE_GENERIC_FDE_ERROR_EXCEPTION_CONTAINER_PUBLISH);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            any(), eq(ReceivingConstants.RECEIPT_EVENT_HANDLER), any()))
        .thenReturn(JMSReceiptPublisher);
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ReceivingConstants.KAFKA_RECEIPT_PUBLISH_ENABLED);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            any(), eq(ReceivingConstants.KAFKA_RECEIPT_EVENT_HANDLER), any()))
        .thenReturn(kafkaReceiptPublisher);
    doReturn(deliveryDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            any(), eq(ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER), any());
    when(tenantSpecificConfigReader.getCcmValue(
            getFacilityNum(),
            ReceivingConstants.ELIGIBLE_TRANSFER_POS_CCM_CONFIG,
            ReceivingConstants.DEFAULT_ELIGIBLE_TRANSFER_PO_TYPE))
        .thenReturn(ReceivingConstants.DEFAULT_ELIGIBLE_TRANSFER_PO_TYPE);

    labelData =
        LabelData.builder()
            .deliveryNumber(42935742L)
            .purchaseReferenceNumber("3931400987")
            .purchaseReferenceLineNumber(1)
            .possibleUPC(
                "{\"orderableGTIN\":\"10074451115207\",\"consumableGTIN\":\"00074451115207\"}")
            .build();
    labelDataLpn = LabelDataLpn.builder().lpn("c32987000000000000000001").labelDataId(1L).build();
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
    lineItem.setItemNbr(1090110L);
    lineItem.setItemUpc("00074451115207");
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

    deliveryDocument.setDeliveryDocumentLines(Collections.singletonList(lineItem));
    return deliveryDocument;
  }

  @Test
  public void testReceiveByLpn_ContainerNotFound() throws ReceivingException {
    request =
        OverflowLPNReceivingRequest.builder()
            .lpn("c32987000000000000000001")
            .deliveryNumber(42935742L)
            .location("EOF")
            .verifyContainerExists(true)
            .build();

    when(containerPersisterService.getContainerDetails(anyString())).thenReturn(null);

    try {
      overflowLPNReceivingService.receiveByLPN(request, httpHeaders);
    } catch (ReceivingDataNotFoundException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.CONTAINER_NOT_FOUND);
    }
  }

  @Test
  public void testReceiveByLpn_DeliveryIsNull_LpnNotPresentInLabelDataLpnTable()
      throws ReceivingException {

    request =
        OverflowLPNReceivingRequest.builder()
            .lpn("c32987000000000000000001")
            .location("EOF")
            .build();

    when(containerPersisterService.getContainerDetails(anyString())).thenReturn(null);
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ReceivingConstants.LABEL_DATA_LPN_QUERY_ENABLED);
    when(labelDataLpnRepository.findByLpn(anyString())).thenReturn(null);

    try {
      overflowLPNReceivingService.receiveByLPN(request, httpHeaders);
    } catch (ReceivingDataNotFoundException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.LABEL_DATA_NOT_FOUND);
    }
  }

  @Test
  public void testReceiveByLpn_DeliveryIsNull_LpnPresentInLabelDataLpnTable()
      throws ReceivingException {

    request =
        OverflowLPNReceivingRequest.builder()
            .lpn("c32987000000000000000001")
            .location("EOF")
            .build();
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse("3931400987", 1);
    String OFResponse = MockACLMessageData.getBuildContainerResponseForACLReceiving();

    when(containerPersisterService.getContainerDetails(anyString())).thenReturn(null);
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ReceivingConstants.LABEL_DATA_LPN_QUERY_ENABLED);
    when(labelDataLpnRepository.findByLpn(anyString())).thenReturn(labelDataLpn);
    when(labelDataRepository.findById(anyLong())).thenReturn(Optional.of(labelData));
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(gdmPOLineResponse));
    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenReturn(OFResponse);
    when(containerPersisterService.saveContainer(any()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.FALSE);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);

    overflowLPNReceivingService.receiveByLPN(request, httpHeaders);

    verify(containerPersisterService, times(1)).getContainerDetails(anyString());
    verify(labelDataLpnRepository, times(1)).findByLpn(anyString());
    verify(labelDataRepository, times(0))
        .findByDeliveryNumberAndContainsLPN(anyLong(), anyString());
  }

  @Test
  public void testReceiveByLpn_DeliveryIsNull_LabelDataLpnTableFeatureDisabled()
      throws ReceivingException {

    request =
        OverflowLPNReceivingRequest.builder()
            .lpn("c32987000000000000000001")
            .location("EOF")
            .build();

    when(containerPersisterService.getContainerDetails(anyString())).thenReturn(null);
    doReturn(Boolean.FALSE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ReceivingConstants.LABEL_DATA_LPN_QUERY_ENABLED);

    try {
      overflowLPNReceivingService.receiveByLPN(request, httpHeaders);
    } catch (ReceivingDataNotFoundException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.LABEL_DATA_NOT_FOUND);
    }

    verify(containerPersisterService, times(1)).getContainerDetails(anyString());
    verify(labelDataLpnRepository, times(0)).findByLpn(anyString());
    verify(labelDataRepository, times(0))
        .findByDeliveryNumberAndContainsLPN(anyLong(), anyString());
  }

  @Test
  public void testReceiveByLpn_UpcValidationFail() throws ReceivingException {
    request =
        OverflowLPNReceivingRequest.builder()
            .lpn("c32987000000000000000001")
            .location("EOF")
            .upc("10757037950767")
            .upcValidationRequired(true)
            .build();

    when(containerPersisterService.getContainerDetails(anyString())).thenReturn(null);
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ReceivingConstants.LABEL_DATA_LPN_QUERY_ENABLED);
    when(labelDataLpnRepository.findByLpn(anyString())).thenReturn(labelDataLpn);
    when(labelDataRepository.findById(anyLong())).thenReturn(Optional.of(labelData));

    try {
      overflowLPNReceivingService.receiveByLPN(request, httpHeaders);
    } catch (ReceivingDataNotFoundException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.UPC_VALIDATION_FAILED);
    }

    verify(containerPersisterService, times(1)).getContainerDetails(anyString());
    verify(labelDataLpnRepository, times(1)).findByLpn(anyString());
    verify(labelDataRepository, times(0))
        .findByDeliveryNumberAndContainsLPN(anyLong(), anyString());
  }

  @Test
  public void testReceiveByLpn_ReopenDelivery() throws ReceivingException {
    request =
        OverflowLPNReceivingRequest.builder()
            .lpn("c32987000000000000000001")
            .location("EOF")
            .build();
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse("3931400987", 1);
    gdmPOLineResponse.setDeliveryStatus(DeliveryStatus.PNDFNL);
    String OFResponse = MockACLMessageData.getBuildContainerResponseForACLReceiving();

    when(containerPersisterService.getContainerDetails(anyString())).thenReturn(null);
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ReceivingConstants.LABEL_DATA_LPN_QUERY_ENABLED);
    when(labelDataLpnRepository.findByLpn(anyString())).thenReturn(labelDataLpn);
    when(labelDataRepository.findById(anyLong())).thenReturn(Optional.of(labelData));
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(gdmPOLineResponse));
    when(tenantSpecificConfigReader.getConfiguredInstance(
            any(), eq(ReceivingConstants.DELIVERY_METADATA_SERVICE), any()))
        .thenReturn(accDeliveryMetaDataService);
    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenReturn(OFResponse);
    when(containerPersisterService.saveContainer(any()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.FALSE);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);

    overflowLPNReceivingService.receiveByLPN(request, httpHeaders);

    verify(containerPersisterService, times(1)).getContainerDetails(anyString());
    verify(labelDataLpnRepository, times(1)).findByLpn(anyString());
    verify(labelDataRepository, times(0))
        .findByDeliveryNumberAndContainsLPN(anyLong(), anyString());
    verify(accDeliveryMetaDataService, times(1))
        .findAndUpdateDeliveryStatus("42935742", DeliveryStatus.SYS_REO);
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(anyString(), anyInt());
    verify(fdeService, times(1))
        .receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testReceiveByLpn_DeliveryStatusIsInvalidForReceiving() throws ReceivingException {
    request =
        OverflowLPNReceivingRequest.builder()
            .lpn("c32987000000000000000001")
            .deliveryNumber(42935742L)
            .location("EOF")
            .build();
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse("3931400987", 1);
    gdmPOLineResponse.setDeliveryStatus(DeliveryStatus.FNL);
    gdmPOLineResponse.setDeliveryLegacyStatus("FNL");

    when(containerPersisterService.getContainerDetails(anyString())).thenReturn(null);
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ReceivingConstants.LABEL_DATA_LPN_QUERY_ENABLED);
    when(labelDataLpnRepository.findByLpn(anyString())).thenReturn(labelDataLpn);
    when(labelDataRepository.findById(anyLong())).thenReturn(Optional.of(labelData));
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(gdmPOLineResponse));
    when(tenantSpecificConfigReader.getConfiguredInstance(
            any(), eq(ReceivingConstants.DELIVERY_METADATA_SERVICE), any()))
        .thenReturn(accDeliveryMetaDataService);

    try {
      overflowLPNReceivingService.receiveByLPN(request, httpHeaders);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.DELIVERY_NOT_RECEIVABLE);
    }

    verify(containerPersisterService, times(1)).getContainerDetails(anyString());
    verify(labelDataLpnRepository, times(1)).findByLpn(anyString());
    verify(labelDataRepository, times(0))
        .findByDeliveryNumberAndContainsLPN(anyLong(), anyString());
    verify(receiptService, times(0)).getReceivedQtyByPoAndPoLine(anyString(), anyInt());
    verify(fdeService, times(0))
        .receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testReceiveByLpn_ItemIsXBlocked() throws ReceivingException {
    request =
        OverflowLPNReceivingRequest.builder()
            .lpn("c32987000000000000000001")
            .location("EOF")
            .build();
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse("3931400987", 1);
    gdmPOLineResponse.getDeliveryDocumentLines().get(0).setHandlingCode("X");

    when(containerPersisterService.getContainerDetails(anyString())).thenReturn(null);
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ReceivingConstants.LABEL_DATA_LPN_QUERY_ENABLED);
    when(labelDataLpnRepository.findByLpn(anyString())).thenReturn(labelDataLpn);
    when(labelDataRepository.findById(anyLong())).thenReturn(Optional.of(labelData));
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(gdmPOLineResponse));
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(ReceivingConstants.ITEM_X_BLOCKED_VAL))
        .thenReturn(Boolean.TRUE);

    try {
      overflowLPNReceivingService.receiveByLPN(request, httpHeaders);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.ITEM_X_BLOCKED_ERROR);
    }

    verify(containerPersisterService, times(1)).getContainerDetails(anyString());
    verify(labelDataLpnRepository, times(1)).findByLpn(anyString());
    verify(labelDataRepository, times(0))
        .findByDeliveryNumberAndContainsLPN(anyLong(), anyString());
  }

  @Test
  public void testReceiveByLpn_OverageError() throws ReceivingException {
    request =
        OverflowLPNReceivingRequest.builder()
            .lpn("c32987000000000000000001")
            .location("EOF")
            .build();
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse("3931400987", 1);

    when(containerPersisterService.getContainerDetails(anyString())).thenReturn(null);
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ReceivingConstants.LABEL_DATA_LPN_QUERY_ENABLED);
    when(labelDataLpnRepository.findByLpn(anyString())).thenReturn(labelDataLpn);
    when(labelDataRepository.findById(anyLong())).thenReturn(Optional.of(labelData));
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(gdmPOLineResponse));
    when(receiptService.getReceivedQtyByPoAndPoLine("3931400987", 1)).thenReturn(15L);
    when(containerPersisterService.saveContainer(any()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(kafkaConfig.isInventoryOnSecureKafka()).thenReturn(Boolean.TRUE);

    try {
      overflowLPNReceivingService.receiveByLPN(request, httpHeaders);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.OVERAGE_ERROR);
    }

    verify(containerPersisterService, times(1)).getContainerDetails(anyString());
    verify(labelDataLpnRepository, times(1)).findByLpn(anyString());
    verify(labelDataRepository, times(0))
        .findByDeliveryNumberAndContainsLPN(anyLong(), anyString());
    verify(containerPersisterService, times(1)).saveContainer(any(Container.class));
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(anyString(), anyInt());
    verify(kafkaConfig, times(1)).isInventoryOnSecureKafka();
    verify(secureKafkaTemplate, times(1)).send(any(Message.class));
    verify(receiptService, times(0)).createReceiptsFromInstruction(any(), any(), any());
  }

  @Test
  public void testReceiveByLpn_OFCallFailed() throws ReceivingException {
    request =
        OverflowLPNReceivingRequest.builder()
            .lpn("c32987000000000000000001")
            .location("EOF")
            .build();
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse("3931400987", 1);

    when(containerPersisterService.getContainerDetails(anyString())).thenReturn(null);
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ReceivingConstants.LABEL_DATA_LPN_QUERY_ENABLED);
    when(labelDataLpnRepository.findByLpn(anyString())).thenReturn(labelDataLpn);
    when(labelDataRepository.findById(anyLong())).thenReturn(Optional.of(labelData));
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(gdmPOLineResponse));
    when(receiptService.getReceivedQtyByPoAndPoLine("3931400987", 1)).thenReturn(0L);
    InstructionError instructionError = InstructionErrorCode.getErrorValue("GLS-OF-BE-00047");
    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenThrow(
            new ReceivingException(
                instructionError.getErrorMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR,
                instructionError.getErrorCode(),
                instructionError.getErrorHeader()));
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ACCConstants.ENABLE_GENERIC_FDE_ERROR_EXCEPTION_CONTAINER_PUBLISH);
    when(containerPersisterService.saveContainer(any()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(kafkaConfig.isInventoryOnSecureKafka()).thenReturn(Boolean.TRUE);

    try {
      overflowLPNReceivingService.receiveByLPN(request, httpHeaders);
    } catch (ReceivingInternalException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.OF_GENERIC_ERROR);
    }

    verify(containerPersisterService, times(1)).getContainerDetails(anyString());
    verify(labelDataLpnRepository, times(1)).findByLpn(anyString());
    verify(labelDataRepository, times(0))
        .findByDeliveryNumberAndContainsLPN(anyLong(), anyString());
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(anyString(), anyInt());
    verify(fdeService, times(1))
        .receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class));
    verify(containerPersisterService, times(1)).saveContainer(any(Container.class));
    verify(kafkaConfig, times(1)).isInventoryOnSecureKafka();
    verify(secureKafkaTemplate, times(1)).send(any(Message.class));
    verify(receiptService, times(0)).createReceiptsFromInstruction(any(), any(), any());
  }

  @Test
  public void testReceiveByLpn_OF_Error_NoAllocation() throws ReceivingException {
    request =
        OverflowLPNReceivingRequest.builder()
            .lpn("c32987000000000000000001")
            .location("EOF")
            .build();
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse("3931400987", 1);

    when(containerPersisterService.getContainerDetails(anyString())).thenReturn(null);
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ReceivingConstants.LABEL_DATA_LPN_QUERY_ENABLED);
    when(labelDataLpnRepository.findByLpn(anyString())).thenReturn(labelDataLpn);
    when(labelDataRepository.findById(anyLong())).thenReturn(Optional.of(labelData));
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(gdmPOLineResponse));
    when(receiptService.getReceivedQtyByPoAndPoLine("3931400987", 1)).thenReturn(0L);
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
    when(kafkaConfig.isInventoryOnSecureKafka()).thenReturn(Boolean.TRUE);

    try {
      overflowLPNReceivingService.receiveByLPN(request, httpHeaders);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.NO_ALLOCATION);
    }

    verify(containerPersisterService, times(1)).getContainerDetails(anyString());
    verify(labelDataLpnRepository, times(1)).findByLpn(anyString());
    verify(labelDataRepository, times(0))
        .findByDeliveryNumberAndContainsLPN(anyLong(), anyString());
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(anyString(), anyInt());
    verify(fdeService, times(1))
        .receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class));
    verify(containerPersisterService, times(1)).saveContainer(any(Container.class));
    verify(kafkaConfig, times(1)).isInventoryOnSecureKafka();
    verify(secureKafkaTemplate, times(1)).send(any(Message.class));
    verify(receiptService, times(0)).createReceiptsFromInstruction(any(), any(), any());
  }

  @Test
  public void testReceiveByLpn_OF_Error_ChannelFlip() throws ReceivingException {
    request =
        OverflowLPNReceivingRequest.builder()
            .lpn("c32987000000000000000001")
            .location("EOF")
            .build();
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse("3931400987", 1);

    when(containerPersisterService.getContainerDetails(anyString())).thenReturn(null);
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ReceivingConstants.LABEL_DATA_LPN_QUERY_ENABLED);
    when(labelDataLpnRepository.findByLpn(anyString())).thenReturn(labelDataLpn);
    when(labelDataRepository.findById(anyLong())).thenReturn(Optional.of(labelData));
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(gdmPOLineResponse));
    when(receiptService.getReceivedQtyByPoAndPoLine("3931400987", 1)).thenReturn(0L);
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
    when(kafkaConfig.isInventoryOnSecureKafka()).thenReturn(Boolean.TRUE);

    try {
      overflowLPNReceivingService.receiveByLPN(request, httpHeaders);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.CHANNEL_FLIP);
    }

    verify(containerPersisterService, times(1)).getContainerDetails(anyString());
    verify(labelDataLpnRepository, times(1)).findByLpn(anyString());
    verify(labelDataRepository, times(0))
        .findByDeliveryNumberAndContainsLPN(anyLong(), anyString());
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(anyString(), anyInt());
    verify(fdeService, times(1))
        .receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class));
    verify(containerPersisterService, times(1)).saveContainer(any(Container.class));
    verify(kafkaConfig, times(1)).isInventoryOnSecureKafka();
    verify(secureKafkaTemplate, times(1)).send(any(Message.class));
    verify(receiptService, times(0)).createReceiptsFromInstruction(any(), any(), any());
  }

  @Test
  public void testReceiveByLpn_OF_Error_GenericError() throws ReceivingException {
    request =
        OverflowLPNReceivingRequest.builder()
            .lpn("c32987000000000000000001")
            .location("EOF")
            .build();
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse("3931400987", 1);

    when(containerPersisterService.getContainerDetails(anyString())).thenReturn(null);
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ReceivingConstants.LABEL_DATA_LPN_QUERY_ENABLED);
    when(labelDataLpnRepository.findByLpn(anyString())).thenReturn(labelDataLpn);
    when(labelDataRepository.findById(anyLong())).thenReturn(Optional.of(labelData));
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(gdmPOLineResponse));
    when(receiptService.getReceivedQtyByPoAndPoLine("3931400987", 1)).thenReturn(0L);
    InstructionError instructionError = InstructionErrorCode.getErrorValue("GLS-OF-BE-00010");
    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenThrow(
            new ReceivingException(
                instructionError.getErrorMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR,
                instructionError.getErrorCode(),
                instructionError.getErrorHeader()));
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ACCConstants.ENABLE_GENERIC_FDE_ERROR_EXCEPTION_CONTAINER_PUBLISH);
    when(containerPersisterService.saveContainer(any()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(kafkaConfig.isInventoryOnSecureKafka()).thenReturn(Boolean.TRUE);

    try {
      overflowLPNReceivingService.receiveByLPN(request, httpHeaders);
    } catch (ReceivingInternalException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.OF_GENERIC_ERROR);
    }

    verify(containerPersisterService, times(1)).getContainerDetails(anyString());
    verify(labelDataLpnRepository, times(1)).findByLpn(anyString());
    verify(labelDataRepository, times(0))
        .findByDeliveryNumberAndContainsLPN(anyLong(), anyString());
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(anyString(), anyInt());
    verify(fdeService, times(1))
        .receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class));
    verify(containerPersisterService, times(1)).saveContainer(any(Container.class));
    verify(kafkaConfig, times(1)).isInventoryOnSecureKafka();
    verify(secureKafkaTemplate, times(1)).send(any(Message.class));
    verify(receiptService, times(0)).createReceiptsFromInstruction(any(), any(), any());
  }

  @Test
  public void testReceiveByLpn_OF_Error_GenericError_FlagDisabled() throws ReceivingException {
    request =
        OverflowLPNReceivingRequest.builder()
            .lpn("c32987000000000000000001")
            .location("EOF")
            .build();
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse("3931400987", 1);

    when(containerPersisterService.getContainerDetails(anyString())).thenReturn(null);
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ReceivingConstants.LABEL_DATA_LPN_QUERY_ENABLED);
    when(labelDataLpnRepository.findByLpn(anyString())).thenReturn(labelDataLpn);
    when(labelDataRepository.findById(anyLong())).thenReturn(Optional.of(labelData));
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(gdmPOLineResponse));
    when(receiptService.getReceivedQtyByPoAndPoLine("3931400987", 1)).thenReturn(0L);
    InstructionError instructionError = InstructionErrorCode.getErrorValue("GLS-OF-BE-00010");
    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenThrow(
            new ReceivingException(
                instructionError.getErrorMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR,
                instructionError.getErrorCode(),
                instructionError.getErrorHeader()));
    doReturn(Boolean.FALSE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ACCConstants.ENABLE_GENERIC_FDE_ERROR_EXCEPTION_CONTAINER_PUBLISH);
    when(containerPersisterService.saveContainer(any()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(kafkaConfig.isInventoryOnSecureKafka()).thenReturn(Boolean.TRUE);

    try {
      overflowLPNReceivingService.receiveByLPN(request, httpHeaders);
    } catch (ReceivingInternalException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.OF_GENERIC_ERROR);
    }

    verify(containerPersisterService, times(1)).getContainerDetails(anyString());
    verify(labelDataLpnRepository, times(1)).findByLpn(anyString());
    verify(labelDataRepository, times(0))
        .findByDeliveryNumberAndContainsLPN(anyLong(), anyString());
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(anyString(), anyInt());
    verify(fdeService, times(1))
        .receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class));
    verify(containerPersisterService, times(0)).saveContainer(any(Container.class));
    verify(kafkaConfig, times(0)).isInventoryOnSecureKafka();
    verify(secureKafkaTemplate, times(0)).send(any(Message.class));
    verify(receiptService, times(0)).createReceiptsFromInstruction(any(), any(), any());
  }

  @Test
  public void testReceiveByLpn_happyPath() throws ReceivingException {
    request =
        OverflowLPNReceivingRequest.builder()
            .lpn("c32987000000000000000001")
            .location("EOF")
            .build();
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse("3931400987", 1);
    String OFResponse = MockACLMessageData.getBuildContainerResponseForACLReceiving();

    when(containerPersisterService.getContainerDetails(anyString())).thenReturn(null);
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ReceivingConstants.LABEL_DATA_LPN_QUERY_ENABLED);
    when(labelDataLpnRepository.findByLpn(anyString())).thenReturn(labelDataLpn);
    when(labelDataRepository.findById(anyLong())).thenReturn(Optional.of(labelData));
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(gdmPOLineResponse));
    when(tenantSpecificConfigReader.getConfiguredInstance(
            any(), eq(ReceivingConstants.DELIVERY_METADATA_SERVICE), any()))
        .thenReturn(accDeliveryMetaDataService);
    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenReturn(OFResponse);
    when(containerPersisterService.saveContainer(any()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.FALSE);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);

    overflowLPNReceivingService.receiveByLPN(request, httpHeaders);

    verify(containerPersisterService, times(1)).getContainerDetails(anyString());
    verify(labelDataLpnRepository, times(1)).findByLpn(anyString());
    verify(labelDataRepository, times(0))
        .findByDeliveryNumberAndContainsLPN(anyLong(), anyString());
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(anyString(), anyInt());
    verify(fdeService, times(1))
        .receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class));
    verify(secureKafkaTemplate, times(3)).send(any(Message.class));
    verify(receiptService, times(1)).createReceiptsFromInstruction(any(), any(), any());
  }

  @Test
  public void testReceiveByLpn_ExceptionToExceptionContainer() throws ReceivingException {
    request =
        OverflowLPNReceivingRequest.builder()
            .lpn("c32987000000000000000001")
            .location("EOF")
            .build();
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse("3931400987", 1);
    Container existingExceptionContainer =
        JacksonParser.convertJsonToObject(
            getFileAsString(
                "../../receiving-test/src/main/resources/json/ACCExceptionContainer.json"),
            Container.class);

    when(containerPersisterService.getContainerDetails(anyString()))
        .thenReturn(existingExceptionContainer);
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ReceivingConstants.LABEL_DATA_LPN_QUERY_ENABLED);
    when(labelDataLpnRepository.findByLpn(anyString())).thenReturn(labelDataLpn);
    when(labelDataRepository.findById(anyLong())).thenReturn(Optional.of(labelData));
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(gdmPOLineResponse));
    when(receiptService.getReceivedQtyByPoAndPoLine("3931400987", 1)).thenReturn(0L);
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
    when(kafkaConfig.isInventoryOnSecureKafka()).thenReturn(Boolean.TRUE);

    try {
      overflowLPNReceivingService.receiveByLPN(request, httpHeaders);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.CHANNEL_FLIP);
    }

    verify(containerPersisterService, times(1)).getContainerDetails(anyString());
    verify(labelDataLpnRepository, times(1)).findByLpn(anyString());
    verify(labelDataRepository, times(0))
        .findByDeliveryNumberAndContainsLPN(anyLong(), anyString());
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(anyString(), anyInt());
    verify(fdeService, times(1))
        .receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class));
    verify(containerPersisterService, times(1)).saveContainer(any(Container.class));
    verify(kafkaConfig, times(1)).isInventoryOnSecureKafka();
    verify(secureKafkaTemplate, times(1)).send(any(Message.class));
    verify(receiptService, times(0)).createReceiptsFromInstruction(any(), any(), any());
  }

  @Test
  public void testReceiveByLpn_ExceptionToPickedContainer() throws ReceivingException {
    request =
        OverflowLPNReceivingRequest.builder()
            .lpn("c32987000000000000000001")
            .location("EOF")
            .build();
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse("3931400987", 1);
    String OFResponse = MockACLMessageData.getBuildContainerResponseForACLReceiving();
    Container existingExceptionContainer =
        JacksonParser.convertJsonToObject(
            getFileAsString(
                "../../receiving-test/src/main/resources/json/ACCExceptionContainer.json"),
            Container.class);

    when(containerPersisterService.getContainerDetails(anyString()))
        .thenReturn(existingExceptionContainer);
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ReceivingConstants.LABEL_DATA_LPN_QUERY_ENABLED);
    when(labelDataLpnRepository.findByLpn(anyString())).thenReturn(labelDataLpn);
    when(labelDataRepository.findById(anyLong())).thenReturn(Optional.of(labelData));
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(gdmPOLineResponse));
    when(receiptService.getReceivedQtyByPoAndPoLine("3931400987", 1)).thenReturn(0L);
    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenReturn(OFResponse);
    when(containerPersisterService.saveContainer(any()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.FALSE);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);
    when(kafkaConfig.isInventoryOnSecureKafka()).thenReturn(Boolean.TRUE);

    overflowLPNReceivingService.receiveByLPN(request, httpHeaders);

    verify(containerPersisterService, times(1)).getContainerDetails(anyString());
    verify(labelDataLpnRepository, times(1)).findByLpn(anyString());
    verify(labelDataRepository, times(0))
        .findByDeliveryNumberAndContainsLPN(anyLong(), anyString());
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(anyString(), anyInt());
    verify(fdeService, times(1))
        .receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class));
    verify(secureKafkaTemplate, times(3)).send(any(Message.class));
    verify(receiptService, times(1)).createReceiptsFromInstruction(any(), any(), any());
  }

  @Test
  public void testReceiveByLpn_ExceptionToExceptionContainer_ItemChanged()
      throws ReceivingException {
    request =
        OverflowLPNReceivingRequest.builder()
            .lpn("c32987000000000000000001")
            .location("EOF")
            .build();
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse("3931400987", 1);
    DeliveryDocument gdmPOLineResponse2 = getGdmPOLineResponse("3931400987", 2);
    gdmPOLineResponse.getDeliveryDocumentLines().get(0).setItemNbr(566051127L);
    gdmPOLineResponse2.getDeliveryDocumentLines().get(0).setItemNbr(566051127L);
    List<DeliveryDocumentLine> poLines =
        new ArrayList<>(gdmPOLineResponse.getDeliveryDocumentLines());
    poLines.add(gdmPOLineResponse2.getDeliveryDocumentLines().get(0));
    gdmPOLineResponse.setDeliveryDocumentLines(poLines);
    ReceiptSummaryEachesResponse receiptSummaryEachesResponse1 =
        new ReceiptSummaryEachesResponse("3931400987", 1, null, 10L);
    ReceiptSummaryEachesResponse receiptSummaryEachesResponse2 =
        new ReceiptSummaryEachesResponse("3931400987", 2, null, 0L);
    Container existingExceptionContainer =
        JacksonParser.convertJsonToObject(
            getFileAsString(
                "../../receiving-test/src/main/resources/json/ACCExceptionContainer.json"),
            Container.class);

    when(containerPersisterService.getContainerDetails(anyString()))
        .thenReturn(existingExceptionContainer);
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ReceivingConstants.LABEL_DATA_LPN_QUERY_ENABLED);
    when(labelDataLpnRepository.findByLpn(anyString())).thenReturn(labelDataLpn);
    when(labelDataRepository.findById(anyLong())).thenReturn(Optional.of(labelData));
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(gdmPOLineResponse));
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_RECEIPT_AGGREGATOR_CHECK_ENABLED))
        .thenReturn(Boolean.TRUE);
    when(receiptService.receivedQtyByPoAndPoLineList(
            Collections.singletonList("3931400987"), Stream.of(1, 2).collect(Collectors.toSet())))
        .thenReturn(Arrays.asList(receiptSummaryEachesResponse1, receiptSummaryEachesResponse2));

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
    when(kafkaConfig.isInventoryOnSecureKafka()).thenReturn(Boolean.TRUE);

    try {
      overflowLPNReceivingService.receiveByLPN(request, httpHeaders);
    } catch (ReceivingInternalException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.ITEM_CHANGED_AFTER_PO_AUTO_SELECTION);
    }

    verify(containerPersisterService, times(1)).getContainerDetails(anyString());
    verify(labelDataLpnRepository, times(1)).findByLpn(anyString());
    verify(labelDataRepository, times(0))
        .findByDeliveryNumberAndContainsLPN(anyLong(), anyString());
    verify(receiptService, times(1)).receivedQtyByPoAndPoLineList(anyList(), anySet());
    verify(fdeService, times(1))
        .receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class));
    verify(containerPersisterService, times(0)).saveContainer(any(Container.class));
    verify(kafkaConfig, times(0)).isInventoryOnSecureKafka();
    verify(secureKafkaTemplate, times(0)).send(any(Message.class));
    verify(receiptService, times(0)).createReceiptsFromInstruction(any(), any(), any());
  }

  @Test
  public void testReceiveByLpn_ExceptionToPickedContainer_ItemChanged() throws ReceivingException {
    request =
        OverflowLPNReceivingRequest.builder()
            .lpn("c32987000000000000000001")
            .location("EOF")
            .build();
    String OFResponse = MockACLMessageData.getBuildContainerResponseForACLReceiving();
    DeliveryDocument gdmPOLineResponse = getGdmPOLineResponse("3931400987", 1);
    DeliveryDocument gdmPOLineResponse2 = getGdmPOLineResponse("3931400987", 2);
    gdmPOLineResponse.getDeliveryDocumentLines().get(0).setItemNbr(566051127L);
    gdmPOLineResponse2.getDeliveryDocumentLines().get(0).setItemNbr(566051127L);
    List<DeliveryDocumentLine> poLines =
        new ArrayList<>(gdmPOLineResponse.getDeliveryDocumentLines());
    poLines.add(gdmPOLineResponse2.getDeliveryDocumentLines().get(0));
    gdmPOLineResponse.setDeliveryDocumentLines(poLines);
    ReceiptSummaryEachesResponse receiptSummaryEachesResponse1 =
        new ReceiptSummaryEachesResponse("3931400987", 1, null, 10L);
    ReceiptSummaryEachesResponse receiptSummaryEachesResponse2 =
        new ReceiptSummaryEachesResponse("3931400987", 2, null, 0L);
    Container existingExceptionContainer =
        JacksonParser.convertJsonToObject(
            getFileAsString(
                "../../receiving-test/src/main/resources/json/ACCExceptionContainer.json"),
            Container.class);

    when(containerPersisterService.getContainerDetails(anyString()))
        .thenReturn(existingExceptionContainer);
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ReceivingConstants.LABEL_DATA_LPN_QUERY_ENABLED);
    when(labelDataLpnRepository.findByLpn(anyString())).thenReturn(labelDataLpn);
    when(labelDataRepository.findById(anyLong())).thenReturn(Optional.of(labelData));
    when(deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(gdmPOLineResponse));
    when(accManagedConfig.isMultiPOAutoSelectEnabled()).thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_RECEIPT_AGGREGATOR_CHECK_ENABLED))
        .thenReturn(Boolean.TRUE);
    when(receiptService.receivedQtyByPoAndPoLineList(
            Collections.singletonList("3931400987"), Stream.of(1, 2).collect(Collectors.toSet())))
        .thenReturn(Arrays.asList(receiptSummaryEachesResponse1, receiptSummaryEachesResponse2));
    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenReturn(OFResponse);
    when(containerPersisterService.saveContainer(any()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(instructionRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(tenantSpecificConfigReader.getConfiguredInstance(
            any(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), any()))
        .thenReturn(defaultLabelIdProcessor);
    when(kafkaConfig.isInventoryOnSecureKafka()).thenReturn(Boolean.TRUE);

    try {
      overflowLPNReceivingService.receiveByLPN(request, httpHeaders);
    } catch (ReceivingInternalException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.ITEM_CHANGED_AFTER_PO_AUTO_SELECTION);
    }

    verify(containerPersisterService, times(1)).getContainerDetails(anyString());
    verify(labelDataLpnRepository, times(1)).findByLpn(anyString());
    verify(labelDataRepository, times(0))
        .findByDeliveryNumberAndContainsLPN(anyLong(), anyString());
    verify(receiptService, times(1)).receivedQtyByPoAndPoLineList(anyList(), anySet());
    verify(fdeService, times(1))
        .receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class));
    verify(containerPersisterService, times(0)).saveContainer(any(Container.class));
    verify(kafkaConfig, times(0)).isInventoryOnSecureKafka();
    verify(secureKafkaTemplate, times(2)).send(any(Message.class));
    verify(receiptService, times(0)).createReceiptsFromInstruction(any(), any(), any());
  }
}
