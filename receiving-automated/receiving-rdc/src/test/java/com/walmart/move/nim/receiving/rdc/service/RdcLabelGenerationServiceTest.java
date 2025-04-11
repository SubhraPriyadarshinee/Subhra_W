package com.walmart.move.nim.receiving.rdc.service;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.hawkeye.HawkeyeRestApiClient;
import com.walmart.move.nim.receiving.core.client.itemconfig.ItemConfigApiClient;
import com.walmart.move.nim.receiving.core.client.itemconfig.ItemConfigRestApiClientException;
import com.walmart.move.nim.receiving.core.client.itemconfig.model.ItemConfigDetails;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.DeliveryEvent;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.entity.LabelDownloadEvent;
import com.walmart.move.nim.receiving.core.entity.LabelSequence;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.message.common.ItemUpdateMessage;
import com.walmart.move.nim.receiving.core.message.publisher.KafkaLabelDataPublisher;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.instruction.*;
import com.walmart.move.nim.receiving.core.model.label.FormattedLabels;
import com.walmart.move.nim.receiving.core.model.label.PossibleUPC;
import com.walmart.move.nim.receiving.core.model.label.ScanItem;
import com.walmart.move.nim.receiving.core.model.label.acl.ACLLabelDataTO;
import com.walmart.move.nim.receiving.core.model.label.acl.LabelType;
import com.walmart.move.nim.receiving.core.model.labeldownload.RejectReason;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingDivertLocations;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponse;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.data.MockInstructionDownloadEvent;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.mock.data.MockDeliveryDocuments;
import com.walmart.move.nim.receiving.rdc.model.LabelInstructionStatus;
import com.walmart.move.nim.receiving.rdc.utils.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.mockito.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RdcLabelGenerationServiceTest {
  @Spy @InjectMocks private RdcLabelGenerationService rdcLabelGenerationService;
  @Mock private RdcInstructionUtils rdcInstructionUtils;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private NimRdsService nimRdsService;
  @Mock private LabelDataService labelDataService;
  @Mock private VendorBasedDeliveryDocumentsSearchHandler deliveryDocumentsSearchHandler;
  @Mock private RdcManagedConfig rdcManagedConfig;
  @Mock private RdcInstructionService rdcInstructionService;
  @Mock private AppConfig appConfig;
  @Mock private RdcReceivingUtils rdcReceivingUtils;
  @Mock private RdcItemValidator rdcItemValidator;
  @Mock private KafkaLabelDataPublisher labelDataPublisher;
  @Mock private RdcLabelGenerationUtils rdcLabelGenerationUtils;
  @Mock private ItemConfigApiClient itemConfigApiClient;
  @Mock private HawkeyeRestApiClient hawkeyeRestApiClient;
  @Mock private LPNCacheService lpnCacheService;
  @Mock private LabelDownloadEventService labelDownloadEventService;
  @Mock private SlottingServiceImpl slottingService;
  private DeliveryDetails deliveryDetails;
  @Mock private DeliveryService deliveryService;
  @Mock private DeliveryEventPersisterService deliveryEventPersisterService;
  @Mock private RdcSSTKInstructionUtils rdcSSTKInstructionUtils;
  @Mock private RdcAsyncUtils rdcAsyncUtils;
  @Mock private RdcSSTKLabelGenerationUtils rdcSSTKLabelGenerationUtils;
  @Mock private RdcSSTKItemValidator rdcSSTKItemValidator;
  @Mock private LabelSequenceService labelSequenceService;

  private Gson gson = new Gson();
  private String deliveryDetailsJsonString;

  private final String[] orderQtyLpnSet = {
    "c32987000000000000000001",
    "c32987000000000000000002",
    "c32987000000000000000003",
    "c32987000000000000000004",
    "c32987000000000000000005",
    "c32987000000000000000006"
  };

  private final String[] overageQtyLpnSet = {
    "c32987000000000000000007", "c32987000000000000000008"
  };

  private String facilityNum = "32679";
  private String facilityCountryCode = "us";
  private String gdmBaseUrl = "https://atlas-gdm-cell001.walmart.com";

  @BeforeClass
  public void initMocks() throws ReceivingException {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(rdcLabelGenerationService, "labelDataBatchSize", 2);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    TenantContext.setFacilityCountryCode(facilityCountryCode);
    ReflectionTestUtils.setField(rdcLabelGenerationService, "gson", gson);
    TenantContext.setCorrelationId("2323-323dsds-323dwsd-3d23e");
    try {
      String dataPath =
          new File("../../receiving-test/src/main/resources/json/gdm/v2/DeliveryDetailsSSTK.json")
              .getCanonicalPath();

      deliveryDetailsJsonString = new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      assert (false);
    }
  }

  @BeforeMethod
  public void reInitData() {
    deliveryDetails =
        JacksonParser.convertJsonToObject(deliveryDetailsJsonString, DeliveryDetails.class);
  }

  @AfterMethod
  public void tearDown() {
    reset(
        rdcInstructionUtils,
        tenantSpecificConfigReader,
        nimRdsService,
        labelDataService,
        deliveryDocumentsSearchHandler,
        rdcInstructionService,
        rdcItemValidator,
        labelDataPublisher,
        rdcLabelGenerationUtils,
        appConfig,
        hawkeyeRestApiClient,
        lpnCacheService,
        deliveryService,
        deliveryEventPersisterService,
        rdcLabelGenerationService,
        labelDownloadEventService,
        itemConfigApiClient,
        rdcAsyncUtils,
        rdcSSTKItemValidator,
        rdcSSTKLabelGenerationUtils,
        labelSequenceService);
  }

  @Test
  public void fetchDeliveryDocumentsByPOAndDelivery() throws IOException, ReceivingException {
    doReturn(deliveryDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    doReturn(MockDeliveryDocuments.getDeliveryDocumentsForMultipleDA())
        .when(deliveryDocumentsSearchHandler)
        .fetchDeliveryDocumentByItemNumber(anyString(), anyInt(), any(HttpHeaders.class));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    doReturn(true).when(rdcManagedConfig).isSplitPalletEnabled();
    doNothing()
        .when(rdcInstructionUtils)
        .populatePrimeSlotFromSmartSlotting(
            any(DeliveryDocument.class), any(HttpHeaders.class), any(InstructionRequest.class));
    doReturn(Boolean.FALSE).when(rdcInstructionUtils).isSSTKDocument(any(DeliveryDocument.class));
    doNothing()
        .when(rdcInstructionService)
        .checkIfAtlasConvertedItem(eq(null), anyList(), any(HttpHeaders.class));
    doCallRealMethod().when(rdcInstructionUtils).updateAdditionalItemDetailsFromGDM(anyList());
    doNothing().when(rdcInstructionUtils).updateAdditionalItemDetailsFromGDM(anyList());
    List<DeliveryDocument> deliveryDocumentList =
        rdcLabelGenerationService.fetchDeliveryDocumentsByPOAndItemNumber(
            "60032433", 3804890, "8458708163", MockHttpHeaders.getHeaders());
    assertEquals(1, deliveryDocumentList.size());
    assertNotNull(
        deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0).getAdditionalInfo());
    verify(rdcInstructionUtils, times(1)).updateAdditionalItemDetailsFromGDM(anyList());
    verify(rdcInstructionUtils, times(0))
        .populatePrimeSlotFromSmartSlotting(
            any(DeliveryDocument.class), any(HttpHeaders.class), any(InstructionRequest.class));
    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByItemNumber(anyString(), anyInt(), any(HttpHeaders.class));
  }

  @Test
  public void fetchDeliveryDocumentsByPOAndDelivery_IQSDisabled()
      throws IOException, ReceivingException {
    doReturn(deliveryDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    List<DeliveryDocument> mockDeliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForMultipleDA();
    doReturn(mockDeliveryDocumentList)
        .when(deliveryDocumentsSearchHandler)
        .fetchDeliveryDocumentByItemNumber(anyString(), anyInt(), any(HttpHeaders.class));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
    doNothing()
        .when(rdcInstructionService)
        .checkIfAtlasConvertedItem(eq(null), anyList(), any(HttpHeaders.class));
    doNothing().when(nimRdsService).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    List<DeliveryDocument> deliveryDocumentList =
        rdcLabelGenerationService.fetchDeliveryDocumentsByPOAndItemNumber(
            "60032433", 3804890, "8458708163", MockHttpHeaders.getHeaders());
    assertEquals(1, deliveryDocumentList.size());
    verify(rdcInstructionUtils, times(0)).updateAdditionalItemDetailsFromGDM(anyList());
    verify(nimRdsService).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByItemNumber(anyString(), anyInt(), any(HttpHeaders.class));
  }

  @Test
  public void testValidateDuplicateLpnsAndProcessLabelData_Success()
      throws IOException, ReceivingException {
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(MockHttpHeaders.getHeaders());
    instructionDownloadMessageDTO.setPoNumber("8458708163");

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    doReturn(deliveryDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER), any());
    doReturn(MockDeliveryDocuments.getDeliveryDocumentsForMultipleDA())
        .when(deliveryDocumentsSearchHandler)
        .fetchDeliveryDocumentByItemNumber(anyString(), anyInt(), any(HttpHeaders.class));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
    doNothing()
        .when(rdcInstructionUtils)
        .populatePrimeSlotFromSmartSlotting(
            any(DeliveryDocument.class), any(HttpHeaders.class), any(InstructionRequest.class));
    doReturn(Boolean.TRUE).when(rdcInstructionUtils).isSSTKDocument(any(DeliveryDocument.class));
    doNothing()
        .when(rdcInstructionService)
        .checkIfAtlasConvertedItem(eq(null), anyList(), any(HttpHeaders.class));
    doCallRealMethod().when(rdcInstructionUtils).updateAdditionalItemDetailsFromGDM(anyList());
    doReturn(RejectReason.BREAKOUT).when(rdcItemValidator).validateItem(anyList());
    doReturn(labelDataPublisher)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.LABEL_DATA_PUBLISHER), any());
    doNothing().when(labelDataPublisher).publish(any(), any());
    doReturn(getMockLabelDownloadPayload())
        .when(rdcLabelGenerationUtils)
        .buildLabelDownloadForHawkeye(
            any(DeliveryDocument.class), anyList(), any(RejectReason.class));
    rdcLabelGenerationService.processLabelsForAutomation(
        instructionDownloadMessageDTO, getLabelDataForDA());
    doReturn(Collections.singletonList(mockLabelDownloadEvent()))
        .when(labelDownloadEventService)
        .saveAll(anyList());
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredInstance(anyString(), anyString(), any());
    verify(deliveryDocumentsSearchHandler, times(0))
        .fetchDeliveryDocumentByItemNumber(anyString(), anyInt(), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
    verify(rdcInstructionUtils, times(0))
        .populatePrimeSlotFromSmartSlotting(
            any(DeliveryDocument.class), any(HttpHeaders.class), any(InstructionRequest.class));
    verify(rdcInstructionService, times(0))
        .checkIfAtlasConvertedItem(eq(null), anyList(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(0)).updateAdditionalItemDetailsFromGDM(anyList());
    verify(rdcItemValidator, times(0)).validateItem(anyList());
    verify(labelDataPublisher, times(0)).publish(any(), any());
    verify(rdcLabelGenerationUtils, times(0))
        .buildLabelDownloadForHawkeye(
            any(DeliveryDocument.class), anyList(), any(RejectReason.class));
    verify(labelDownloadEventService, times(0)).saveAll(anyList());
  }

  @Test
  public void testValidateDuplicateLpnsAndProcessLabelDataAsync_Success()
      throws IOException, ReceivingException {
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(MockHttpHeaders.getHeaders());
    instructionDownloadMessageDTO.setPoNumber("8458708163");

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    doReturn(deliveryDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER), any());
    doReturn(MockDeliveryDocuments.getDeliveryDocumentsForMultipleDA())
        .when(deliveryDocumentsSearchHandler)
        .fetchDeliveryDocumentByItemNumber(anyString(), anyInt(), any(HttpHeaders.class));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
    doNothing()
        .when(rdcInstructionUtils)
        .populatePrimeSlotFromSmartSlotting(
            any(DeliveryDocument.class), any(HttpHeaders.class), any(InstructionRequest.class));
    doReturn(Boolean.TRUE).when(rdcInstructionUtils).isSSTKDocument(any(DeliveryDocument.class));
    doNothing()
        .when(rdcInstructionService)
        .checkIfAtlasConvertedItem(eq(null), anyList(), any(HttpHeaders.class));
    doCallRealMethod().when(rdcInstructionUtils).updateAdditionalItemDetailsFromGDM(anyList());
    doReturn(RejectReason.BREAKOUT).when(rdcItemValidator).validateItem(anyList());
    doReturn(labelDataPublisher)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.LABEL_DATA_PUBLISHER), any());
    doNothing().when(labelDataPublisher).publish(any(), any());
    doReturn(getMockLabelDownloadPayload())
        .when(rdcLabelGenerationUtils)
        .buildLabelDownloadForHawkeye(
            any(DeliveryDocument.class), anyList(), any(RejectReason.class));
    rdcLabelGenerationService.processLabelsForAutomationAsync(
        instructionDownloadMessageDTO, getLabelDataForDA());
    doReturn(Collections.singletonList(mockLabelDownloadEvent()))
        .when(labelDownloadEventService)
        .saveAll(anyList());
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredInstance(anyString(), anyString(), any());
    verify(deliveryDocumentsSearchHandler, times(0))
        .fetchDeliveryDocumentByItemNumber(anyString(), anyInt(), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
    verify(rdcInstructionUtils, times(0))
        .populatePrimeSlotFromSmartSlotting(
            any(DeliveryDocument.class), any(HttpHeaders.class), any(InstructionRequest.class));
    verify(rdcInstructionService, times(0))
        .checkIfAtlasConvertedItem(eq(null), anyList(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(0)).updateAdditionalItemDetailsFromGDM(anyList());
    verify(rdcItemValidator, times(0)).validateItem(anyList());
    verify(labelDataPublisher, times(0)).publish(any(), any());
    verify(rdcLabelGenerationUtils, times(0))
        .buildLabelDownloadForHawkeye(
            any(DeliveryDocument.class), anyList(), any(RejectReason.class));
    verify(labelDownloadEventService, times(0)).saveAll(anyList());
  }

  @Test
  public void testValidateDuplicateLpnsAndProcessLabelData_PublishOutboxLabelDataSuccess()
      throws IOException, ReceivingException {
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(MockHttpHeaders.getHeaders());
    instructionDownloadMessageDTO.setPoNumber("8458708163");
    instructionDownloadMessageDTO.setTrackingIds(Collections.singletonList("trackingId"));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_UPDATE_POSSIBLE_UPC_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    doReturn(getLabelDataForDA())
        .when(labelDataService)
        .findByPurchaseReferenceNumberAndItemNumberAndStatus(anyString(), anyLong(), anyString());
    doReturn(deliveryDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER), any());
    doReturn(MockDeliveryDocuments.getDeliveryDocumentsForMultipleDA())
        .when(deliveryDocumentsSearchHandler)
        .fetchDeliveryDocumentByItemNumber(anyString(), anyInt(), any(HttpHeaders.class));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED), anyBoolean()))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(ReceivingConstants.IS_AUTOMATION_OUTBOX_PATTERN_ENABLED), anyBoolean()))
        .thenReturn(true);
    when(rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(anyLong(), anyLong()))
        .thenReturn(true);
    doNothing()
        .when(rdcInstructionUtils)
        .populatePrimeSlotFromSmartSlotting(
            any(DeliveryDocument.class), any(HttpHeaders.class), any(InstructionRequest.class));
    doReturn(Boolean.TRUE).when(rdcInstructionUtils).isSSTKDocument(any(DeliveryDocument.class));
    doNothing()
        .when(rdcInstructionService)
        .checkIfAtlasConvertedItem(eq(null), anyList(), any(HttpHeaders.class));
    doCallRealMethod().when(rdcInstructionUtils).updateAdditionalItemDetailsFromGDM(anyList());
    doReturn(RejectReason.BREAKOUT).when(rdcItemValidator).validateItem(anyList());
    doReturn(labelDataPublisher)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.LABEL_DATA_PUBLISHER), any());
    doNothing().when(labelDataPublisher).publish(any(), any());
    doReturn(getMockLabelDownloadPayload())
        .when(rdcLabelGenerationUtils)
        .buildLabelDownloadForHawkeye(
            any(DeliveryDocument.class), anyList(), any(RejectReason.class));
    rdcLabelGenerationService.processLabelsForAutomation(
        instructionDownloadMessageDTO, getLabelDataForDA());
    doReturn(Collections.singletonList(mockLabelDownloadEvent()))
        .when(labelDownloadEventService)
        .saveAll(anyList());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(anyString(), anyString(), any());
    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByItemNumber(anyString(), anyInt(), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(6))
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            anyString(), eq(ReceivingConstants.IS_AUTOMATION_OUTBOX_PATTERN_ENABLED), anyBoolean());
    verify(rdcInstructionUtils, times(0))
        .populatePrimeSlotFromSmartSlotting(
            any(DeliveryDocument.class), any(HttpHeaders.class), any(InstructionRequest.class));
    verify(rdcInstructionService, times(0))
        .checkIfAtlasConvertedItem(eq(null), anyList(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(0)).updateAdditionalItemDetailsFromGDM(anyList());
    verify(rdcItemValidator, times(1)).validateItem(anyList());
    verify(labelDataPublisher, times(0)).publish(any(), any());
    verify(rdcLabelGenerationUtils, times(1))
        .buildLabelDownloadForHawkeye(
            any(DeliveryDocument.class), anyList(), any(RejectReason.class));
    verify(labelDownloadEventService, times(2)).saveAll(anyList());
  }

  @Test
  public void testValidateDuplicateLpnsAndProcessLabelData_LabelDataPartitionSuccess()
      throws IOException, ReceivingException {
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    List<LabelData> labelDataList = new ArrayList<>();
    labelDataList.add(getLabelData());
    labelDataList.add(getLabelData());
    labelDataList.add(getLabelData());
    instructionDownloadMessageDTO.setHttpHeaders(MockHttpHeaders.getHeaders());
    instructionDownloadMessageDTO.setPoNumber("8458708163");
    instructionDownloadMessageDTO.setTrackingIds(Collections.singletonList("trackingId"));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_UPDATE_POSSIBLE_UPC_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    doReturn(labelDataList)
        .when(labelDataService)
        .findByPurchaseReferenceNumberAndItemNumberAndStatus(anyString(), anyLong(), anyString());
    doReturn(deliveryDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER), any());
    doReturn(MockDeliveryDocuments.getDeliveryDocumentsForMultipleDA())
        .when(deliveryDocumentsSearchHandler)
        .fetchDeliveryDocumentByItemNumber(anyString(), anyInt(), any(HttpHeaders.class));
    doNothing()
        .when(rdcInstructionUtils)
        .populatePrimeSlotFromSmartSlotting(
            any(DeliveryDocument.class), any(HttpHeaders.class), any(InstructionRequest.class));
    doReturn(Boolean.TRUE).when(rdcInstructionUtils).isSSTKDocument(any(DeliveryDocument.class));
    doNothing()
        .when(rdcInstructionService)
        .checkIfAtlasConvertedItem(eq(null), anyList(), any(HttpHeaders.class));
    doCallRealMethod().when(rdcInstructionUtils).updateAdditionalItemDetailsFromGDM(anyList());
    doReturn(RejectReason.BREAKOUT).when(rdcItemValidator).validateItem(anyList());
    doReturn(labelDataPublisher)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.LABEL_DATA_PUBLISHER), any());
    doNothing().when(labelDataPublisher).publish(any(), any());
    doReturn(getMockLabelDownloadPayload())
        .when(rdcLabelGenerationUtils)
        .buildLabelDownloadForHawkeye(
            any(DeliveryDocument.class), anyList(), any(RejectReason.class));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED), anyBoolean()))
        .thenReturn(true);
    when(rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(any(), any())).thenReturn(true);
    rdcLabelGenerationService.processLabelsForAutomation(
        instructionDownloadMessageDTO, Collections.singletonList(getLabelData()));
    doReturn(Collections.singletonList(mockLabelDownloadEvent()))
        .when(labelDownloadEventService)
        .saveAll(anyList());
    verify(tenantSpecificConfigReader, times(3))
        .getConfiguredInstance(anyString(), anyString(), any());
    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByItemNumber(anyString(), anyInt(), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(7))
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
    verify(rdcInstructionUtils, times(0))
        .populatePrimeSlotFromSmartSlotting(
            any(DeliveryDocument.class), any(HttpHeaders.class), any(InstructionRequest.class));
    verify(rdcInstructionService, times(0))
        .checkIfAtlasConvertedItem(eq(null), anyList(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(0)).updateAdditionalItemDetailsFromGDM(anyList());
    verify(rdcItemValidator, times(1)).validateItem(anyList());
    verify(labelDataPublisher, times(2)).publish(any(), any());
    verify(rdcLabelGenerationUtils, times(2))
        .buildLabelDownloadForHawkeye(
            any(DeliveryDocument.class), anyList(), any(RejectReason.class));
    verify(labelDownloadEventService, times(2)).saveAll(anyList());
  }

  @Test
  public void testValidateDuplicateLpnsAndProcessLabelData_ReceivingException()
      throws ReceivingException {
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(MockHttpHeaders.getHeaders());
    instructionDownloadMessageDTO.setPoNumber("8458708163");
    doReturn(getLabelDataForDA())
        .when(labelDataService)
        .findByPurchaseReferenceNumberAndItemNumberAndStatus(anyString(), anyLong(), anyString());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    doReturn(deliveryDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER), any());
    doThrow(new ReceivingException("Mock exception"))
        .when(deliveryDocumentsSearchHandler)
        .fetchDeliveryDocumentByItemNumber(anyString(), anyInt(), any(HttpHeaders.class));

    rdcLabelGenerationService.processLabelsForAutomation(
        instructionDownloadMessageDTO, getLabelDataForDA());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(anyString(), anyString(), any());
    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByItemNumber(anyString(), anyInt(), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(2))
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
    verify(rdcItemValidator, times(0)).validateItem(anyList());
  }

  @Test
  public void testValidateDuplicateLpnsAndProcessLabelData_DuplicateLpns_Success()
      throws IOException, ReceivingException {
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(MockHttpHeaders.getHeaders());
    instructionDownloadMessageDTO.setPoNumber("8458708163");
    instructionDownloadMessageDTO.setTrackingIds(Collections.singletonList("trackingId"));
    instructionDownloadMessageDTO.setPurchaseReferenceLineNumber(1);

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_UPDATE_POSSIBLE_UPC_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    doReturn(getLabelDataForDA())
        .when(labelDataService)
        .findByPurchaseReferenceNumberAndItemNumberAndStatus(anyString(), anyLong(), anyString());
    doReturn(deliveryDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER), any());
    doReturn(MockDeliveryDocuments.getDeliveryDocumentsForMultipleDA())
        .when(deliveryDocumentsSearchHandler)
        .fetchDeliveryDocumentByItemNumber(anyString(), anyInt(), any(HttpHeaders.class));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.SMART_SLOTTING_INTEGRATION_ENABLED), anyBoolean()))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(ReceivingConstants.IS_IQS_INTEGRATION_ENABLED), anyBoolean()))
        .thenReturn(true);
    when(rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(anyLong(), anyLong()))
        .thenReturn(true);
    doNothing()
        .when(rdcInstructionUtils)
        .populatePrimeSlotFromSmartSlotting(
            any(DeliveryDocument.class), any(HttpHeaders.class), any(InstructionRequest.class));
    doReturn(Boolean.TRUE).when(rdcInstructionUtils).isSSTKDocument(any(DeliveryDocument.class));
    doNothing()
        .when(rdcInstructionService)
        .checkIfAtlasConvertedItem(eq(null), anyList(), any(HttpHeaders.class));
    doCallRealMethod().when(rdcInstructionUtils).updateAdditionalItemDetailsFromGDM(anyList());
    doReturn(RejectReason.BREAKOUT).when(rdcItemValidator).validateItem(anyList());
    doReturn(labelDataPublisher)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.LABEL_DATA_PUBLISHER), any());
    doNothing().when(labelDataPublisher).publish(any(), any());
    doReturn(getMockLabelDownloadPayload())
        .when(rdcLabelGenerationUtils)
        .buildLabelDownloadForHawkeye(
            any(DeliveryDocument.class), anyList(), any(RejectReason.class));
    doReturn(Collections.singletonList(mockLabelDownloadEvent()))
        .when(labelDownloadEventService)
        .saveAll(anyList());
    doNothing().when(rdcInstructionUtils).updateAdditionalItemDetailsFromGDM(anyList());
    rdcLabelGenerationService.processLabelsForAutomation(instructionDownloadMessageDTO, null);
    verify(tenantSpecificConfigReader, times(2))
        .getConfiguredInstance(anyString(), anyString(), any());
    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByItemNumber(anyString(), anyInt(), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(6))
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
    verify(rdcInstructionUtils, times(1))
        .populatePrimeSlotFromSmartSlotting(
            any(DeliveryDocument.class), any(HttpHeaders.class), any(InstructionRequest.class));
    verify(rdcInstructionService, times(0))
        .checkIfAtlasConvertedItem(eq(null), anyList(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1)).updateAdditionalItemDetailsFromGDM(anyList());
    verify(rdcItemValidator, times(1)).validateItem(anyList());
    verify(labelDataPublisher, times(1)).publish(any(), any());
    verify(labelDataService, times(1))
        .findByPurchaseReferenceNumberAndItemNumberAndStatus(anyString(), anyLong(), anyString());
    verify(rdcLabelGenerationUtils, times(1))
        .buildLabelDownloadForHawkeye(
            any(DeliveryDocument.class), anyList(), any(RejectReason.class));
    verify(labelDownloadEventService, times(2)).saveAll(anyList());
  }

  @Test
  public void testValidateDuplicateLpnsAndProcessLabelData_DuplicateLpns_UniqueLpnFlagEnabled()
      throws ReceivingException {
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(MockHttpHeaders.getHeaders());
    instructionDownloadMessageDTO.setPoNumber("8458708163");

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(),
            eq(RdcConstants.IS_ATLAS_SENDING_UNIQUE_LPN_TO_HAWKEYE_ENABLED),
            anyBoolean()))
        .thenReturn(true);
    when(rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(anyLong(), anyLong()))
        .thenReturn(true);
    rdcLabelGenerationService.processLabelsForAutomation(instructionDownloadMessageDTO, null);
    verify(labelDataService, times(0))
        .findByPurchaseReferenceNumberAndItemNumberAndStatus(anyString(), anyLong(), anyString());
    verify(tenantSpecificConfigReader, times(2))
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
    verify(rdcItemValidator, times(0)).validateItem(anyList());
    verify(labelDataPublisher, times(0)).publish(any(), any());
  }

  @Test
  public void testValidateDuplicateLpnsAndProcessLabelData_IgnoreProcessing_success() {
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(MockHttpHeaders.getHeaders());
    instructionDownloadMessageDTO.setPoNumber("8458708163");

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(),
            eq(RdcConstants.IS_ATLAS_SENDING_UNIQUE_LPN_TO_HAWKEYE_ENABLED),
            anyBoolean()))
        .thenReturn(true);
    when(rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(anyLong(), anyLong()))
        .thenReturn(false);
    rdcLabelGenerationService.processLabelsForAutomation(instructionDownloadMessageDTO, null);
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
  }

  @Test
  public void testProcessLabelsForAutomation_DSDC_DoNotPublishLabelsToHawkeye() {
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(MockHttpHeaders.getHeaders());
    instructionDownloadMessageDTO.setPoNumber("8458708163");
    List<LabelData> labelDataList = new ArrayList<>();
    LabelData labelData = new LabelData();
    labelData.setAsnNumber("32323");
    labelData.setSscc("000433433433344");
    labelData.setDeliveryNumber(323223L);
    labelDataList.add(labelData);
    rdcLabelGenerationService.processLabelsForAutomation(
        instructionDownloadMessageDTO, labelDataList);
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
  }

  @Test
  public void testProcessLabelsForAutomation_LabelDownloadEventExists() {
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(MockHttpHeaders.getHeaders());
    instructionDownloadMessageDTO.setPoNumber("8458708163");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(anyLong(), anyLong()))
        .thenReturn(true);
    LabelDownloadEvent labelDownloadEvent =
        LabelDownloadEvent.builder()
            .messagePayload(gson.toJson(instructionDownloadMessageDTO))
            .build();
    when(labelDownloadEventService
            .findByDeliveryNumberAndPurchaseReferenceNumberAndItemNumberAndStatus(
                anyLong(), anyString(), anyLong(), anyString()))
        .thenReturn(Collections.singletonList(labelDownloadEvent));
    rdcLabelGenerationService.processLabelsForAutomation(
        instructionDownloadMessageDTO, getLabelDataForDA());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
    verify(labelDownloadEventService, times(1))
        .findByDeliveryNumberAndPurchaseReferenceNumberAndItemNumberAndStatus(
            anyLong(), anyString(), anyLong(), anyString());
  }

  @Test
  public void testProcessLabelsForAutomation_LabelDownloadEventDoesNotExists() {
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(MockHttpHeaders.getHeaders());
    instructionDownloadMessageDTO.setPoNumber("8458708163");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(anyLong(), anyLong()))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_SENDING_UNIQUE_LPN_TO_HAWKEYE_ENABLED,
            false))
        .thenReturn(true);
    InstructionDownloadMessageDTO instructionDownloadMessageDTO1 =
        new InstructionDownloadMessageDTO();
    InstructionDownloadBlobStorageDTO blobStorage = new InstructionDownloadBlobStorageDTO();
    blobStorage.setBlobUri(
        "https://5180ea2283stg.blob.core.windows.net/dc-qa/98100016_4012345711_230524061212_%2801%2930353489118027%2821%2931050730CV120A0065.json");
    instructionDownloadMessageDTO1.setBlobStorage(Collections.singletonList(blobStorage));
    LabelDownloadEvent labelDownloadEvent =
        LabelDownloadEvent.builder()
            .messagePayload(gson.toJson(instructionDownloadMessageDTO1))
            .build();
    when(labelDownloadEventService
            .findByDeliveryNumberAndPurchaseReferenceNumberAndItemNumberAndStatus(
                anyLong(), anyString(), anyLong(), anyString()))
        .thenReturn(Collections.singletonList(labelDownloadEvent));
    rdcLabelGenerationService.processLabelsForAutomation(instructionDownloadMessageDTO, null);
    verify(tenantSpecificConfigReader, times(2))
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
    verify(labelDownloadEventService, times(1))
        .findByDeliveryNumberAndPurchaseReferenceNumberAndItemNumberAndStatus(
            anyLong(), anyString(), anyLong(), anyString());
  }

  @Test
  public void testUpdatePossibleUPC_notEnabled() throws ReceivingException, IOException {
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    List<LabelData> labelDataList = new ArrayList<>();
    labelDataList.add(getLabelData());
    labelDataList.add(getLabelData());
    labelDataList.add(getLabelData());
    instructionDownloadMessageDTO.setHttpHeaders(MockHttpHeaders.getHeaders());
    instructionDownloadMessageDTO.setPoNumber("8458708163");
    instructionDownloadMessageDTO.setTrackingIds(Collections.singletonList("trackingId"));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_UPDATE_POSSIBLE_UPC_ENABLED,
            false))
        .thenReturn(Boolean.FALSE);
    doReturn(labelDataList)
        .when(labelDataService)
        .findByPurchaseReferenceNumberAndItemNumberAndStatus(anyString(), anyLong(), anyString());
    doReturn(deliveryDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER), any());
    doReturn(MockDeliveryDocuments.getDeliveryDocumentsForMultipleDA())
        .when(deliveryDocumentsSearchHandler)
        .fetchDeliveryDocumentByItemNumber(anyString(), anyInt(), any(HttpHeaders.class));
    doNothing()
        .when(rdcInstructionUtils)
        .populatePrimeSlotFromSmartSlotting(
            any(DeliveryDocument.class), any(HttpHeaders.class), any(InstructionRequest.class));
    doReturn(Boolean.TRUE).when(rdcInstructionUtils).isSSTKDocument(any(DeliveryDocument.class));
    doNothing()
        .when(rdcInstructionService)
        .checkIfAtlasConvertedItem(eq(null), anyList(), any(HttpHeaders.class));
    doCallRealMethod().when(rdcInstructionUtils).updateAdditionalItemDetailsFromGDM(anyList());
    doReturn(RejectReason.BREAKOUT).when(rdcItemValidator).validateItem(anyList());
    doReturn(labelDataPublisher)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.LABEL_DATA_PUBLISHER), any());
    doNothing().when(labelDataPublisher).publish(any(), any());
    doReturn(getMockLabelDownloadPayload())
        .when(rdcLabelGenerationUtils)
        .buildLabelDownloadForHawkeye(
            any(DeliveryDocument.class), anyList(), any(RejectReason.class));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED), anyBoolean()))
        .thenReturn(true);
    when(rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(any(), any())).thenReturn(true);
    rdcLabelGenerationService.processLabelsForAutomation(
        instructionDownloadMessageDTO, Collections.singletonList(getLabelData()));
    verify(tenantSpecificConfigReader, times(7))
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
    verify(rdcLabelGenerationUtils, times(0))
        .getPossibleUPC(any(com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine.class));
    verify(labelDataService, times(0)).saveAll(anyList());
  }

  @Test
  public void testValidateDuplicateLpnsAndProcessLabelData_IgnoreProcessing_fail()
      throws ReceivingException {
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(MockHttpHeaders.getHeaders());
    instructionDownloadMessageDTO.setPoNumber("8458708163");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    doReturn(deliveryDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER), any());
    doThrow(new ReceivingException("Mock exception"))
        .when(deliveryDocumentsSearchHandler)
        .fetchDeliveryDocumentByItemNumber(anyString(), anyInt(), any(HttpHeaders.class));
    when(rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(anyLong(), anyLong()))
        .thenReturn(true);
    doReturn(getLabelDataForDA())
        .when(labelDataService)
        .findByPurchaseReferenceNumberAndItemNumberAndStatus(anyString(), anyLong(), anyString());

    rdcLabelGenerationService.processLabelsForAutomation(
        instructionDownloadMessageDTO, getLabelDataForDA());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(anyString(), anyString(), any());
    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByItemNumber(anyString(), anyInt(), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(2))
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
    verify(rdcItemValidator, times(0)).validateItem(anyList());
  }

  @Test
  public void testFetchSSTKAndAtlasDeliveryDocuments_conversion_enabled()
      throws IOException, ReceivingException, ItemConfigRestApiClientException {
    DeliveryDetails deliveryDetails =
        new Gson()
            .fromJson(
                MockDeliveryDocuments.getDeliveryDetailsByUriIncludesDummyPo(),
                DeliveryDetails.class);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    when(rdcInstructionUtils.isSSTKDocument(
            (com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument) any()))
        .thenReturn(Boolean.TRUE);
    doNothing()
        .when(rdcInstructionUtils)
        .validateAndAddAsAtlasConvertedItemsV2(anyList(), any(HttpHeaders.class));
    List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument> deliveryDocuments =
        rdcLabelGenerationService.fetchAndFilterSSTKDeliveryDocuments(
            deliveryDetails, MockHttpHeaders.getHeaders());
    verify(rdcInstructionUtils, times(1))
        .validateAndAddAsAtlasConvertedItemsV2(anyList(), any(HttpHeaders.class));
    assertNotNull(deliveryDocuments);
    assertEquals(deliveryDocuments.get(0), deliveryDetails.getDeliveryDocuments().get(0));
  }

  @Test
  public void testFetchSSTKAndAtlasDeliveryDocuments_conversion_disabled()
      throws IOException, ReceivingException, ItemConfigRestApiClientException {
    DeliveryDetails deliveryDetails =
        new Gson()
            .fromJson(
                MockDeliveryDocuments.getDeliveryDetailsByUriIncludesDummyPo(),
                DeliveryDetails.class);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
    when(rdcInstructionUtils.isSSTKDocument(
            (com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument) any()))
        .thenReturn(Boolean.TRUE);
    doNothing()
        .when(rdcInstructionUtils)
        .validateAndAddAsAtlasConvertedItemsV2(anyList(), any(HttpHeaders.class));
    List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument> deliveryDocuments =
        rdcLabelGenerationService.fetchAndFilterSSTKDeliveryDocuments(
            deliveryDetails, MockHttpHeaders.getHeaders());
    verify(rdcInstructionUtils, times(0))
        .validateAndAddAsAtlasConvertedItemsV2(anyList(), any(HttpHeaders.class));
    assertNotNull(deliveryDocuments);
    assertEquals(deliveryDocuments.get(0), deliveryDetails.getDeliveryDocuments().get(0));
  }

  private ItemConfigDetails mockItemConfigDetails() {
    return ItemConfigDetails.builder()
        .createdDateTime("2021-08-11T03:48:27.133Z")
        .desc("test desc")
        .item("5689452")
        .build();
  }

  @Test
  public void testGenerateSSTKLabels_POLineAdded() throws ReceivingException {
    when(labelDataService.fetchByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyString(), anyInt()))
        .thenReturn(Collections.emptyList());
    when(lpnCacheService.getLPNSBasedOnTenant(eq(6), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList(orderQtyLpnSet));
    when(lpnCacheService.getLPNSBasedOnTenant(eq(2), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList(overageQtyLpnSet));
    when(rdcLabelGenerationUtils.getPossibleUPCv2(any(DeliveryDocumentLine.class)))
        .thenReturn(new PossibleUPC());
    when(labelDownloadEventService.findByDeliveryNumberAndPurchaseReferenceNumberAndItemNumber(
            anyLong(), anyString(), anyLong()))
        .thenReturn(Collections.emptyList());
    when(labelDownloadEventService.saveAll(anyList())).thenAnswer(i -> i.getArguments()[0]);
    when(slottingService.getPrimeSlot(anyList(), any(HttpHeaders.class)))
        .thenReturn(getSlottingPalletResponse());
    when(rdcSSTKInstructionUtils.isAtlasItemSymEligible(any(DeliveryDocumentLine.class)))
        .thenReturn(true);
    doReturn(labelDataPublisher)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.LABEL_DATA_PUBLISHER), any());
    doThrow(new ReceivingInternalException("mock error", "mock error"))
        .when(labelDataPublisher)
        .publish(any(), any());
    when(rdcSSTKLabelGenerationUtils.buildLabelDownloadPayloadForSSTK(
            any(DeliveryDocumentLine.class), anyList(), eq(null), anyLong()))
        .thenReturn(getMockSSTKLabelDownloadPayload());
    doReturn(null).when(rdcSSTKItemValidator).validateItem(any(DeliveryDocumentLine.class));
    DeliveryUpdateMessage deliveryUpdateMessage =
        DeliveryUpdateMessage.builder()
            .deliveryNumber("23424243")
            .poNumber("232323323")
            .httpHeaders(MockHttpHeaders.getHeaders())
            .build();
    try {
      rdcLabelGenerationService.generateAndPublishSSTKLabels(
          deliveryDetails.getDeliveryDocuments(), deliveryUpdateMessage, Boolean.FALSE, null);
    } catch (ReceivingInternalException e) {
      verify(labelDataService, times(1))
          .findByPurchaseReferenceNumberAndItemNumberAndStatus(anyString(), anyLong(), anyString());
      verify(lpnCacheService, times(2)).getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class));
      verify(rdcLabelGenerationUtils, times(2)).getPossibleUPCv2(any(DeliveryDocumentLine.class));
      verify(labelDataService, times(1)).saveAllAndFlush(anyList());
      verify(labelDownloadEventService, times(1)).saveAll(anyList());
    }
  }

  @Test
  public void testGenerateSSTKLabels_lpnsExistsForPOAndItem_SendingUniqueLpnsToHE()
      throws ReceivingException {
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setExpectedQty(2);
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setOverageQtyLimit(0);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    when(labelDataService.findByPurchaseReferenceNumberAndItemNumberAndStatus(
            anyString(), anyLong(), anyString()))
        .thenReturn(getLabelDataByPOAndItem());
    when(labelDownloadEventService.saveAll(anyList())).thenAnswer(i -> i.getArguments()[0]);
    when(slottingService.getPrimeSlot(anyList(), any(HttpHeaders.class)))
        .thenReturn(getSlottingPalletResponse());
    doReturn(labelDataPublisher)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.LABEL_DATA_PUBLISHER), any());
    doNothing().when(labelDataPublisher).publish(any(), any());
    when(rdcSSTKLabelGenerationUtils.buildLabelDownloadPayloadForSSTK(
            any(com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine.class),
            anyList(),
            eq(null),
            anyLong()))
        .thenReturn(getMockSSTKLabelDownloadPayload());
    doReturn(null).when(rdcSSTKItemValidator).validateItem(any(DeliveryDocumentLine.class));
    DeliveryUpdateMessage deliveryUpdateMessage =
        DeliveryUpdateMessage.builder()
            .deliveryNumber("23424243")
            .poNumber("232323323")
            .httpHeaders(MockHttpHeaders.getHeaders())
            .build();
    rdcLabelGenerationService.generateAndPublishSSTKLabels(
        deliveryDetails.getDeliveryDocuments(), deliveryUpdateMessage, Boolean.FALSE, null);
    verify(labelDataService, times(1))
        .findByPurchaseReferenceNumberAndItemNumberAndStatus(anyString(), anyLong(), anyString());
    verify(tenantSpecificConfigReader, times(2))
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
    verify(labelDownloadEventService, times(2)).saveAll(anyList());
    verify(labelDataService, times(0)).saveAllAndFlush(anyList());
  }

  @Test
  public void testGenerateSSTKLabels_lpnsExistsForPOAndItem_NotSendingUniqueLpnsToHE()
      throws ReceivingException {
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setExpectedQty(2);
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setOverageQtyLimit(0);
    when(labelDataService.findByPurchaseReferenceNumberAndItemNumberAndStatus(
            anyString(), anyLong(), anyString()))
        .thenReturn(getLabelDataByPOAndItem());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
    when(labelDownloadEventService.findByDeliveryNumberAndPurchaseReferenceNumberAndItemNumber(
            anyLong(), anyString(), anyLong()))
        .thenReturn(null);
    when(labelDownloadEventService.saveAll(anyList())).thenAnswer(i -> i.getArguments()[0]);
    when(slottingService.getPrimeSlot(anyList(), any(HttpHeaders.class)))
        .thenReturn(getSlottingPalletResponse());
    doReturn(labelDataPublisher)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.LABEL_DATA_PUBLISHER), any());
    doNothing().when(labelDataPublisher).publish(any(), any());

    when(rdcSSTKLabelGenerationUtils.buildLabelDownloadPayloadForSSTK(
            any(com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine.class),
            anyList(),
            eq(null),
            anyLong()))
        .thenReturn(getMockSSTKLabelDownloadPayload());
    doReturn(null).when(rdcSSTKItemValidator).validateItem(any(DeliveryDocumentLine.class));
    DeliveryUpdateMessage deliveryUpdateMessage =
        DeliveryUpdateMessage.builder()
            .deliveryNumber("23424243")
            .poNumber("232323323")
            .httpHeaders(MockHttpHeaders.getHeaders())
            .build();
    rdcLabelGenerationService.generateAndPublishSSTKLabels(
        deliveryDetails.getDeliveryDocuments(), deliveryUpdateMessage, Boolean.FALSE, null);
    verify(labelDataService, times(1))
        .findByPurchaseReferenceNumberAndItemNumberAndStatus(anyString(), anyLong(), anyString());
    verify(tenantSpecificConfigReader, times(2))
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
    verify(labelDownloadEventService, times(2)).saveAll(anyList());
    verify(labelDataService, times(0)).saveAllAndFlush(anyList());
  }

  @Test
  public void testGenerateSSTKLabels_labelDownloadEventExistsForDelPOAndItem()
      throws ReceivingException {
    ItemData additionalInfo = new ItemData();
    additionalInfo.setPrimeSlot("A0002");
    additionalInfo.setAsrsAlignment("asrsAlignment");
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setAdditionalInfo(additionalInfo);
    when(labelDataService.fetchByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyString(), anyInt()))
        .thenReturn(Collections.emptyList());
    when(lpnCacheService.getLPNSBasedOnTenant(eq(6), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList(orderQtyLpnSet));
    when(lpnCacheService.getLPNSBasedOnTenant(eq(2), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList(overageQtyLpnSet));
    when(rdcLabelGenerationUtils.getPossibleUPCv2(any(DeliveryDocumentLine.class)))
        .thenReturn(new PossibleUPC());
    when(labelDataService.saveAll(anyList())).thenAnswer(i -> i.getArguments()[0]);
    when(labelDownloadEventService.findByDeliveryNumberAndPurchaseReferenceNumberAndItemNumber(
            anyLong(), anyString(), anyLong()))
        .thenReturn(Collections.singletonList(getMockLabelDownloadEvent()));
    when(labelDownloadEventService.saveAll(anyList())).thenAnswer(i -> i.getArguments()[0]);
    when(slottingService.getPrimeSlot(anyList(), any(HttpHeaders.class)))
        .thenReturn(getSlottingPalletResponse());
    when(rdcSSTKInstructionUtils.isAtlasItemSymEligible(any(DeliveryDocumentLine.class)))
        .thenReturn(true);

    doReturn(labelDataPublisher)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.LABEL_DATA_PUBLISHER), any());
    doNothing().when(labelDataPublisher).publish(any(), any());
    when(rdcSSTKLabelGenerationUtils.buildLabelDownloadPayloadForSSTK(
            any(com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine.class),
            anyList(),
            eq(null),
            anyLong()))
        .thenReturn(getMockSSTKLabelDownloadPayload());
    doReturn(null).when(rdcSSTKItemValidator).validateItem(any(DeliveryDocumentLine.class));
    DeliveryUpdateMessage deliveryUpdateMessage =
        DeliveryUpdateMessage.builder()
            .deliveryNumber("23424243")
            .poNumber("232323323")
            .httpHeaders(MockHttpHeaders.getHeaders())
            .build();
    rdcLabelGenerationService.generateAndPublishSSTKLabels(
        deliveryDetails.getDeliveryDocuments(), deliveryUpdateMessage, Boolean.FALSE, null);
    verify(labelDataService, times(1))
        .findByPurchaseReferenceNumberAndItemNumberAndStatus(anyString(), anyLong(), anyString());
    verify(lpnCacheService, times(2)).getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class));
    verify(rdcLabelGenerationUtils, times(2)).getPossibleUPCv2(any(DeliveryDocumentLine.class));
    verify(labelDataService, times(1)).saveAllAndFlush(anyList());
    verify(labelDownloadEventService, times(2)).saveAll(anyList());
  }

  @Test
  public void testGenerateSSTKLabels_fetchingLpnsThrowsReceivingException()
      throws ReceivingException {
    when(labelDataService.fetchByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyString(), anyInt()))
        .thenReturn(Collections.emptyList());
    doThrow(new ReceivingException("Receiving exception"))
        .when(lpnCacheService)
        .getLPNSBasedOnTenant(eq(6), any(HttpHeaders.class));
    try {
      when(slottingService.getPrimeSlot(anyList(), any(HttpHeaders.class)))
          .thenReturn(getSlottingPalletResponse());
      doReturn(null).when(rdcSSTKItemValidator).validateItem(any(DeliveryDocumentLine.class));
      DeliveryUpdateMessage deliveryUpdateMessage =
          DeliveryUpdateMessage.builder()
              .deliveryNumber("23424243")
              .poNumber("232323323")
              .httpHeaders(MockHttpHeaders.getHeaders())
              .build();
      rdcLabelGenerationService.generateAndPublishSSTKLabels(
          deliveryDetails.getDeliveryDocuments(), deliveryUpdateMessage, Boolean.FALSE, null);
    } catch (ReceivingException e) {
      verify(labelDataService, times(1))
          .findByPurchaseReferenceNumberAndItemNumberAndStatus(anyString(), anyLong(), anyString());
      verify(lpnCacheService, times(1)).getLPNSBasedOnTenant(eq(6), any(HttpHeaders.class));
      verify(labelDataService, times(0)).saveAllAndFlush(anyList());
    }
  }

  @Test
  public void testGenerateLabelsForSSTKPOLineQuantityChange_OrderQtyChange_EmptyLabelSequence()
      throws ReceivingException {
    ItemData additionalInfo = new ItemData();
    additionalInfo.setPrimeSlot("A0002");
    additionalInfo.setAsrsAlignment("asrsAlignment");
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setAdditionalInfo(additionalInfo);
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setExpectedQty(4);
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setOverageQtyLimit(2);
    List<LabelData> mockOverageLabelDataList = getLabelDataByPOAndItem();
    mockOverageLabelDataList.forEach(labelData -> labelData.setLabelType(LabelType.OVERAGE));
    List<LabelData> mockOrderedLabelDataList = getLabelDataByPOAndItem();
    mockOrderedLabelDataList.forEach(labelData -> labelData.setLabelType(LabelType.ORDERED));
    List<LabelData> mocklabelDataList =
        Stream.concat(mockOverageLabelDataList.stream(), mockOrderedLabelDataList.stream())
            .collect(Collectors.toList());
    mocklabelDataList.forEach(
        labelData -> labelData.setStatus(LabelInstructionStatus.AVAILABLE.name()));
    when(labelDataService.fetchByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyString(), anyInt()))
        .thenReturn(mocklabelDataList);
    when(lpnCacheService.getLPNSBasedOnTenant(eq(2), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList(orderQtyLpnSet[4], orderQtyLpnSet[5]));
    when(rdcLabelGenerationUtils.getPossibleUPCv2(any(DeliveryDocumentLine.class)))
        .thenReturn(new PossibleUPC());
    when(rdcSSTKInstructionUtils.isAtlasItemSymEligible(any(DeliveryDocumentLine.class)))
        .thenReturn(true);
    when(labelSequenceService.findByMABDPOLineNumberItemNumberLabelType(
            any(), anyInt(), anyLong(), any(LabelType.class)))
        .thenReturn(null);
    List<LabelData> labelDataList =
        rdcLabelGenerationService.generateLabelsForSSTKPOLineQuantityChange(
            deliveryDetails.getDeliveryDocuments().get(0));
    verify(lpnCacheService, times(1)).getLPNSBasedOnTenant(eq(2), any(HttpHeaders.class));
    verify(rdcLabelGenerationUtils, times(1)).getPossibleUPCv2(any(DeliveryDocumentLine.class));
    verify(labelDataService, times(1)).saveAllAndFlush(anyList());
    verify(labelDataService, times(1))
        .fetchByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(anyString(), anyInt());
    verify(labelSequenceService, times(1))
        .findByMABDPOLineNumberItemNumberLabelType(
            any(), anyInt(), anyLong(), any(LabelType.class));
    verify(labelSequenceService, times(1)).save(any(LabelSequence.class));
    long seqNbr = labelDataList.get(0).getLabelSequenceNbr();
    SimpleDateFormat simpleDateFormat =
        new SimpleDateFormat(RdcConstants.MABD_DATE_FORMAT_FOR_SSTK_LABEL_SEQUENCE_NBR);
    String purchaseReferenceMustArriveByDate =
        simpleDateFormat.format(
            deliveryDetails.getDeliveryDocuments().get(0).getPurchaseReferenceMustArriveByDate());
    long expectedSeqNbr =
        Long.valueOf(
            RdcConstants.LABEL_SEQUENCE_NUMBER_STARTING_DIGIT_ORDERED_SSTK
                .concat(purchaseReferenceMustArriveByDate)
                .concat("000800003"));
    assertEquals(expectedSeqNbr, seqNbr);
    assertEquals(6, labelDataList.size());
  }

  @Test
  public void testGenerateLabelsForSSTKPOLineQuantityChange_OrderQtyChange_NonNullLabelSequence()
      throws ReceivingException {
    ItemData additionalInfo = new ItemData();
    additionalInfo.setPrimeSlot("A0002");
    additionalInfo.setAsrsAlignment("asrsAlignment");
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setAdditionalInfo(additionalInfo);
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setExpectedQty(4);
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setOverageQtyLimit(2);
    List<LabelData> mockOverageLabelDataList = getLabelDataByPOAndItem();
    mockOverageLabelDataList.forEach(labelData -> labelData.setLabelType(LabelType.OVERAGE));
    List<LabelData> mockOrderedLabelDataList = getLabelDataByPOAndItem();
    mockOrderedLabelDataList.forEach(labelData -> labelData.setLabelType(LabelType.ORDERED));
    List<LabelData> mocklabelDataList =
        Stream.concat(mockOverageLabelDataList.stream(), mockOrderedLabelDataList.stream())
            .collect(Collectors.toList());
    mocklabelDataList.forEach(
        labelData -> labelData.setStatus(LabelInstructionStatus.AVAILABLE.name()));
    when(labelDataService.fetchByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyString(), anyInt()))
        .thenReturn(mocklabelDataList);
    when(lpnCacheService.getLPNSBasedOnTenant(eq(2), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList(orderQtyLpnSet[4], orderQtyLpnSet[5]));
    when(rdcLabelGenerationUtils.getPossibleUPCv2(any(DeliveryDocumentLine.class)))
        .thenReturn(new PossibleUPC());
    when(rdcSSTKInstructionUtils.isAtlasItemSymEligible(any(DeliveryDocumentLine.class)))
        .thenReturn(true);
    when(labelSequenceService.findByMABDPOLineNumberItemNumberLabelType(
            any(), anyInt(), anyLong(), any(LabelType.class)))
        .thenReturn(getLabelSequence());
    List<LabelData> labelDataList =
        rdcLabelGenerationService.generateLabelsForSSTKPOLineQuantityChange(
            deliveryDetails.getDeliveryDocuments().get(0));
    verify(lpnCacheService, times(1)).getLPNSBasedOnTenant(eq(2), any(HttpHeaders.class));
    verify(rdcLabelGenerationUtils, times(1)).getPossibleUPCv2(any(DeliveryDocumentLine.class));
    verify(labelDataService, times(1)).saveAllAndFlush(anyList());
    verify(labelDataService, times(1))
        .fetchByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(anyString(), anyInt());
    verify(labelSequenceService, times(1))
        .findByMABDPOLineNumberItemNumberLabelType(
            any(), anyInt(), anyLong(), any(LabelType.class));
    verify(labelSequenceService, times(1)).save(any(LabelSequence.class));
    long seqNbr = labelDataList.get(0).getLabelSequenceNbr();
    SimpleDateFormat simpleDateFormat =
        new SimpleDateFormat(RdcConstants.MABD_DATE_FORMAT_FOR_SSTK_LABEL_SEQUENCE_NBR);
    simpleDateFormat.setTimeZone(TimeZone.getTimeZone(ReceivingConstants.UTC_TIME_ZONE));
    String purchaseReferenceMustArriveByDate =
        simpleDateFormat.format(
            deliveryDetails.getDeliveryDocuments().get(0).getPurchaseReferenceMustArriveByDate());
    long expectedSeqNbr =
        Long.valueOf(
            RdcConstants.LABEL_SEQUENCE_NUMBER_STARTING_DIGIT_ORDERED_SSTK
                .concat(purchaseReferenceMustArriveByDate)
                .concat("000800003"));
    assertEquals(expectedSeqNbr, seqNbr);
    assertEquals(6, labelDataList.size());
  }

  @Test
  public void testGenerateLabelsForSSTKPOLineQuantityChange_NegativeQtyChange()
      throws ReceivingException {
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setExpectedQty(2);
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setOverageQtyLimit(2);
    List<LabelData> mockOverageLabelDataList = getLabelDataByPOAndItem();
    mockOverageLabelDataList.addAll(getLabelDataByPOAndItem());
    mockOverageLabelDataList.forEach(labelData -> labelData.setLabelType(LabelType.OVERAGE));
    List<LabelData> mockOrderedLabelDataList = getLabelDataByPOAndItem();
    mockOrderedLabelDataList.forEach(labelData -> labelData.setLabelType(LabelType.ORDERED));
    List<LabelData> labelDataList =
        Stream.concat(mockOverageLabelDataList.stream(), mockOrderedLabelDataList.stream())
            .collect(Collectors.toList());
    labelDataList.forEach(
        labelData -> {
          labelData.setStatus(LabelInstructionStatus.AVAILABLE.name());
          labelData.setVnpk(1);
          labelData.setWhpk(1);
        });
    doReturn(labelDataList)
        .when(labelDataService)
        .fetchByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(anyString(), anyInt());
    List<LabelData> generatedLabelDataList =
        rdcLabelGenerationService.generateLabelsForSSTKPOLineQuantityChange(
            deliveryDetails.getDeliveryDocuments().get(0));
    verify(labelDataService, times(1))
        .fetchByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(anyString(), anyInt());
    verify(lpnCacheService, times(0)).getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class));
    verify(labelDataService, times(1)).saveAllAndFlush(anyList());
    verify(rdcAsyncUtils, times(1))
        .updateLabelStatusVoidToHawkeye(anyList(), any(HttpHeaders.class));
    assertEquals(4, generatedLabelDataList.size());
  }

  @Test
  public void testGenerateLabelsForSSTKPOLineQuantityChange_fetchingLpnsThrowsReceivingException()
      throws ReceivingException {
    when(labelDataService.fetchByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyString(), anyInt()))
        .thenReturn(Collections.emptyList());
    doThrow(new ReceivingException("Receiving exception"))
        .when(lpnCacheService)
        .getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class));
    try {
      rdcLabelGenerationService.generateLabelsForSSTKPOLineQuantityChange(
          deliveryDetails.getDeliveryDocuments().get(0));
    } catch (ReceivingException e) {
      verify(labelDataService, times(1))
          .fetchByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(anyString(), anyInt());
      verify(lpnCacheService, times(1)).getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class));
      verify(labelDataService, times(0)).saveAllAndFlush(anyList());
    }
  }

  @Test
  public void testGenerateLabelsForSSTKPOLineQuantityChange_EmptyLpns() throws ReceivingException {
    ItemData additionalInfo = new ItemData();
    additionalInfo.setPrimeSlot("A0002");
    additionalInfo.setAsrsAlignment("asrsAlignment");
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setAdditionalInfo(additionalInfo);
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setExpectedQty(2);
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setOverageQtyLimit(3);
    List<LabelData> mockOverageLabelDataList = getLabelDataByPOAndItem();
    mockOverageLabelDataList.forEach(labelData -> labelData.setLabelType(LabelType.OVERAGE));
    List<LabelData> mockOrderedLabelDataList = getLabelDataByPOAndItem();
    mockOrderedLabelDataList.forEach(labelData -> labelData.setLabelType(LabelType.ORDERED));
    List<LabelData> mocklabelDataList =
        Stream.concat(mockOverageLabelDataList.stream(), mockOrderedLabelDataList.stream())
            .collect(Collectors.toList());
    mocklabelDataList.forEach(
        labelData -> labelData.setStatus(LabelInstructionStatus.AVAILABLE.name()));
    when(labelDataService.fetchByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyString(), anyInt()))
        .thenReturn(mocklabelDataList);
    when(lpnCacheService.getLPNSBasedOnTenant(eq(1), any(HttpHeaders.class)))
        .thenReturn(Collections.emptyList());
    when(rdcLabelGenerationUtils.getPossibleUPCv2(any(DeliveryDocumentLine.class)))
        .thenReturn(new PossibleUPC());
    when(rdcSSTKInstructionUtils.isAtlasItemSymEligible(any(DeliveryDocumentLine.class)))
        .thenReturn(false);
    when(labelSequenceService.findByMABDPOLineNumberItemNumberLabelType(
            any(), anyInt(), anyLong(), any(LabelType.class)))
        .thenReturn(getLabelSequence());
    List<LabelData> labelDataList =
        rdcLabelGenerationService.generateLabelsForSSTKPOLineQuantityChange(
            deliveryDetails.getDeliveryDocuments().get(0));
    verify(lpnCacheService, times(1)).getLPNSBasedOnTenant(eq(1), any(HttpHeaders.class));
    verify(labelDataService, times(1))
        .fetchByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(anyString(), anyInt());
  }

  @Test
  public void testValidatePrime() throws IOException {
    List<com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTKV2();
    SlottingPalletResponse mockSlottingPalletResponse = getSlottingPalletResponse();
    when(slottingService.getPrimeSlot(anyList(), any(HttpHeaders.class)))
        .thenReturn(mockSlottingPalletResponse);
    Map<Long, SlottingDivertLocations> expectedItemNumberAndLocation = new HashMap<>();
    expectedItemNumberAndLocation.put(3804890L, mockSlottingPalletResponse.getLocations().get(0));
    rdcLabelGenerationService.getPrimeSlotDetails(deliveryDocuments, MockHttpHeaders.getHeaders());
    assertNotNull(deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getAdditionalInfo());
    assertEquals(
        deliveryDocuments
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getSlotType(),
        expectedItemNumberAndLocation.get(3804890L).getSlotType());
  }

  private SlottingPalletResponse getSlottingPalletResponse() {
    SlottingPalletResponse slottingPalletResponse = new SlottingPalletResponse();
    slottingPalletResponse.setMessageId("3232323-2323-232323");
    List<SlottingDivertLocations> slottingDivertLocationsList = new ArrayList<>();
    SlottingDivertLocations slottingDivertLocations = new SlottingDivertLocations();
    slottingDivertLocations.setPrimeLocation("A0002");
    slottingDivertLocations.setType("success");
    slottingDivertLocations.setLocationSize(72);
    slottingDivertLocations.setSlotType("PRIME");
    slottingDivertLocations.setItemNbr(3804890L);
    slottingDivertLocations.setAsrsAlignment("SYMBP");
    slottingDivertLocationsList.add(slottingDivertLocations);
    slottingPalletResponse.setLocations(slottingDivertLocationsList);
    return slottingPalletResponse;
  }

  @Test
  public void testProcessDeliveryEvent_DoorAssignEvent() throws ReceivingException {
    RdcLabelGenerationService rdcLabelGenerationServiceTemp =
        Mockito.spy(rdcLabelGenerationService);
    DeliveryUpdateMessage deliveryUpdateMessage = getDeliveryUpdateMessage();
    deliveryUpdateMessage.setDeliveryStatus(DeliveryStatus.ARV.toString());
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_DOOR_ASSIGNED);
    deliveryDetails.setDoorNumber("D101");
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .setPurchaseReferenceStatus(String.valueOf(POStatus.ACTV));
    doReturn(deliveryDetails)
        .when(rdcSSTKLabelGenerationUtils)
        .fetchDeliveryDetails(anyString(), anyLong());
    doReturn(true)
        .when(rdcInstructionUtils)
        .isSSTKDocument(
            any(com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument.class));
    doReturn(getMockDeliveryEvent())
        .when(deliveryEventPersisterService)
        .getRdcDeliveryEventToProcess(any());
    when(appConfig.getGdmBaseUrl()).thenReturn(gdmBaseUrl);
    doReturn(deliveryService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    ArgumentCaptor<DeliveryEvent> deliveryEventArgumentCaptor =
        ArgumentCaptor.forClass(DeliveryEvent.class);
    when(deliveryEventPersisterService.save(deliveryEventArgumentCaptor.capture()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(slottingService.getPrimeSlot(anyList(), any(HttpHeaders.class)))
        .thenReturn(getSlottingPalletResponse());
    doReturn(null).when(rdcSSTKItemValidator).validateItem(any(DeliveryDocumentLine.class));
    when(lpnCacheService.getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList("a326790000000000000012345", "a326790000000000000012346"));
    when(labelDownloadEventService.saveAll(anyList())).thenAnswer(i -> i.getArguments()[0]);
    when(rdcSSTKLabelGenerationUtils.buildLabelDownloadPayloadForSSTK(
            any(com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine.class),
            anyList(),
            nullable(RejectReason.class),
            anyLong()))
        .thenReturn(getMockSSTKLabelDownloadPayload());
    doReturn(labelDataPublisher)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.LABEL_DATA_PUBLISHER), any());
    doNothing().when(labelDataPublisher).publish(any(), any());
    rdcLabelGenerationServiceTemp.processDeliveryEvent(deliveryUpdateMessage);
    verify(deliveryEventPersisterService, times(1))
        .getRdcDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventPersisterService, times(1)).save(any());
    verify(rdcSSTKLabelGenerationUtils, times(1)).fetchDeliveryDetails(anyString(), anyLong());
    verify(labelDataPublisher, times(2)).publish(any(), any());
    assertEquals(EventTargetStatus.DELETE, deliveryEventArgumentCaptor.getValue().getEventStatus());
  }

  @Test
  public void testProcessDeliveryEvent_POAddedEvent() throws ReceivingException {
    RdcLabelGenerationService rdcLabelGenerationServiceTemp =
        Mockito.spy(rdcLabelGenerationService);
    DeliveryUpdateMessage deliveryUpdateMessage = getDeliveryUpdateMessage();
    deliveryUpdateMessage.setDeliveryStatus(DeliveryStatus.OPN.toString());
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_PO_ADDED);
    deliveryDetails.setDoorNumber("D101");
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .setPurchaseReferenceStatus(String.valueOf(POStatus.ACTV));
    doReturn(true)
        .when(rdcInstructionUtils)
        .isSSTKDocument(
            any(com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument.class));
    doReturn(deliveryDetails)
        .when(rdcSSTKLabelGenerationUtils)
        .fetchDeliveryDetails(anyString(), anyLong());
    DeliveryEvent deliveryEvent = getMockDeliveryEvent();
    deliveryEvent.setEventType(ReceivingConstants.EVENT_PO_ADDED);
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getRdcDeliveryEventToProcess(any());
    when(appConfig.getGdmBaseUrl()).thenReturn(gdmBaseUrl);
    doReturn(deliveryService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    ArgumentCaptor<DeliveryEvent> deliveryEventArgumentCaptor =
        ArgumentCaptor.forClass(DeliveryEvent.class);
    when(deliveryEventPersisterService.save(deliveryEventArgumentCaptor.capture()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(slottingService.getPrimeSlot(anyList(), any(HttpHeaders.class)))
        .thenReturn(getSlottingPalletResponse());
    doReturn(null).when(rdcSSTKItemValidator).validateItem(any(DeliveryDocumentLine.class));
    when(lpnCacheService.getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList("a326790000000000000012345", "a326790000000000000012346"));
    when(labelDownloadEventService.saveAll(anyList())).thenAnswer(i -> i.getArguments()[0]);
    when(rdcSSTKLabelGenerationUtils.buildLabelDownloadPayloadForSSTK(
            any(com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine.class),
            anyList(),
            nullable(RejectReason.class),
            anyLong()))
        .thenReturn(getMockSSTKLabelDownloadPayload());
    doReturn(labelDataPublisher)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.LABEL_DATA_PUBLISHER), any());
    doNothing().when(labelDataPublisher).publish(any(), any());
    rdcLabelGenerationServiceTemp.processDeliveryEvent(deliveryUpdateMessage);
    verify(deliveryEventPersisterService, times(1))
        .getRdcDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventPersisterService, times(1)).save(any());
    verify(rdcSSTKLabelGenerationUtils, times(1)).fetchDeliveryDetails(anyString(), anyLong());
    verify(labelDataPublisher, times(2)).publish(any(), any());
    assertEquals(EventTargetStatus.DELETE, deliveryEventArgumentCaptor.getValue().getEventStatus());
  }

  @Test
  public void testProcessDeliveryEvent_DoorAssignEvent_EmptySSTKDeliveryDocuments()
      throws ReceivingException {
    RdcLabelGenerationService rdcLabelGenerationServiceTemp =
        Mockito.spy(rdcLabelGenerationService);
    DeliveryUpdateMessage deliveryUpdateMessage = getDeliveryUpdateMessage();
    deliveryUpdateMessage.setDeliveryStatus(DeliveryStatus.ARV.toString());
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_DOOR_ASSIGNED);
    deliveryDetails.setDoorNumber("D101");
    deliveryDetails.setDeliveryDocuments(Collections.emptyList());
    doReturn(deliveryDetails)
        .when(rdcSSTKLabelGenerationUtils)
        .fetchDeliveryDetails(anyString(), anyLong());
    doReturn(true)
        .when(rdcInstructionUtils)
        .isSSTKDocument(
            any(com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument.class));
    doReturn(getMockDeliveryEvent())
        .when(deliveryEventPersisterService)
        .getRdcDeliveryEventToProcess(any());
    when(appConfig.getGdmBaseUrl()).thenReturn(gdmBaseUrl);
    doReturn(deliveryService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    ArgumentCaptor<DeliveryEvent> deliveryEventArgumentCaptor =
        ArgumentCaptor.forClass(DeliveryEvent.class);
    when(deliveryEventPersisterService.save(deliveryEventArgumentCaptor.capture()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(slottingService.getPrimeSlot(anyList(), any(HttpHeaders.class)))
        .thenReturn(getSlottingPalletResponse());
    doReturn(null).when(rdcSSTKItemValidator).validateItem(any(DeliveryDocumentLine.class));
    when(lpnCacheService.getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList("a326790000000000000012345", "a326790000000000000012346"));
    when(labelDownloadEventService.saveAll(anyList())).thenAnswer(i -> i.getArguments()[0]);
    when(rdcSSTKLabelGenerationUtils.buildLabelDownloadPayloadForSSTK(
            any(com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine.class),
            anyList(),
            nullable(RejectReason.class),
            anyLong()))
        .thenReturn(getMockSSTKLabelDownloadPayload());
    doReturn(labelDataPublisher)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.LABEL_DATA_PUBLISHER), any());
    doNothing().when(labelDataPublisher).publish(any(), any());
    rdcLabelGenerationServiceTemp.processDeliveryEvent(deliveryUpdateMessage);
    verify(deliveryEventPersisterService, times(1))
        .getRdcDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventPersisterService, times(1)).save(any());
    verify(rdcSSTKLabelGenerationUtils, times(1)).fetchDeliveryDetails(anyString(), anyLong());
    verify(deliveryEventPersisterService, times(1)).save(any(DeliveryEvent.class));
    assertEquals(EventTargetStatus.DELETE, deliveryEventArgumentCaptor.getValue().getEventStatus());
  }

  @Test
  public void testProcessDeliveryEvent_POUpdatedEvent_HistoryStatus() throws ReceivingException {
    RdcLabelGenerationService rdcLabelGenerationServiceTemp =
        Mockito.spy(rdcLabelGenerationService);
    DeliveryUpdateMessage deliveryUpdateMessage = getDeliveryUpdateMessage();
    deliveryUpdateMessage.setDeliveryStatus(DeliveryStatus.OPN.toString());
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_PO_UPDATED);
    deliveryDetails.setDoorNumber("D101");
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .setPurchaseReferenceStatus(String.valueOf(POStatus.HISTORY));
    doReturn(true)
        .when(rdcInstructionUtils)
        .isSSTKDocument(
            any(com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument.class));
    doReturn(deliveryDetails)
        .when(rdcSSTKLabelGenerationUtils)
        .fetchDeliveryDetails(anyString(), anyLong());
    DeliveryEvent deliveryEvent = getMockDeliveryEvent();
    deliveryEvent.setEventType(ReceivingConstants.EVENT_PO_UPDATED);
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getRdcDeliveryEventToProcess(any());
    when(appConfig.getGdmBaseUrl()).thenReturn(gdmBaseUrl);
    doReturn(deliveryService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    ArgumentCaptor<DeliveryEvent> deliveryEventArgumentCaptor =
        ArgumentCaptor.forClass(DeliveryEvent.class);
    LabelData labelData =
        LabelData.builder().trackingId("a060200000000000000012345").vnpk(2).whpk(2).build();
    when(labelDataService.fetchByPurchaseReferenceNumberAndStatus(anyString(), anyString()))
        .thenReturn(Collections.singletonList(labelData));
    doNothing().when(labelDataService).saveAllAndFlush(anyList());
    doNothing()
        .when(rdcAsyncUtils)
        .updateLabelStatusVoidToHawkeye(anyList(), any(HttpHeaders.class));
    when(deliveryEventPersisterService.save(deliveryEventArgumentCaptor.capture()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(slottingService.getPrimeSlot(anyList(), any(HttpHeaders.class)))
        .thenReturn(getSlottingPalletResponse());
    rdcLabelGenerationServiceTemp.processDeliveryEvent(deliveryUpdateMessage);
    verify(labelDataService, times(1))
        .fetchByPurchaseReferenceNumberAndStatus(anyString(), anyString());
    verify(labelDataService, times(1)).saveAllAndFlush(anyList());
    verify(rdcAsyncUtils, times(1))
        .updateLabelStatusVoidToHawkeye(anyList(), any(HttpHeaders.class));
    verify(deliveryEventPersisterService, times(1))
        .getRdcDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventPersisterService, times(1)).save(any());
    verify(rdcSSTKLabelGenerationUtils, times(1)).fetchDeliveryDetails(anyString(), anyLong());
    assertEquals(EventTargetStatus.DELETE, deliveryEventArgumentCaptor.getValue().getEventStatus());
  }

  @Test
  public void testProcessDeliveryEvent_POUpdatedEvent_CancelledStatus() throws ReceivingException {
    RdcLabelGenerationService rdcLabelGenerationServiceTemp =
        Mockito.spy(rdcLabelGenerationService);
    DeliveryUpdateMessage deliveryUpdateMessage = getDeliveryUpdateMessage();
    deliveryUpdateMessage.setDeliveryStatus(DeliveryStatus.OPN.toString());
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_PO_UPDATED);
    deliveryDetails.setDoorNumber("D101");
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .setPurchaseReferenceStatus(String.valueOf(POStatus.CNCL));
    doReturn(true)
        .when(rdcInstructionUtils)
        .isSSTKDocument(
            any(com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument.class));
    doReturn(deliveryDetails)
        .when(rdcSSTKLabelGenerationUtils)
        .fetchDeliveryDetails(anyString(), anyLong());
    DeliveryEvent deliveryEvent = getMockDeliveryEvent();
    deliveryEvent.setEventType(ReceivingConstants.EVENT_PO_UPDATED);
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getRdcDeliveryEventToProcess(any());
    when(appConfig.getGdmBaseUrl()).thenReturn(gdmBaseUrl);
    doReturn(deliveryService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    ArgumentCaptor<DeliveryEvent> deliveryEventArgumentCaptor =
        ArgumentCaptor.forClass(DeliveryEvent.class);
    when(deliveryEventPersisterService.save(deliveryEventArgumentCaptor.capture()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(slottingService.getPrimeSlot(anyList(), any(HttpHeaders.class)))
        .thenReturn(getSlottingPalletResponse());
    rdcLabelGenerationServiceTemp.processDeliveryEvent(deliveryUpdateMessage);
    verify(labelDataService, times(0)).fetchByPurchaseReferenceNumber(anyString());
    verify(deliveryEventPersisterService, times(1))
        .getRdcDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventPersisterService, times(1)).save(any());
    verify(rdcSSTKLabelGenerationUtils, times(1)).fetchDeliveryDetails(anyString(), anyLong());
    assertEquals(EventTargetStatus.DELETE, deliveryEventArgumentCaptor.getValue().getEventStatus());
  }

  @Test
  public void testProcessDeliveryEvent_POUpdatedEvent() throws ReceivingException {
    RdcLabelGenerationService rdcLabelGenerationServiceTemp =
        Mockito.spy(rdcLabelGenerationService);
    DeliveryUpdateMessage deliveryUpdateMessage = getDeliveryUpdateMessage();
    deliveryUpdateMessage.setDeliveryStatus(DeliveryStatus.OPN.toString());
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_PO_UPDATED);
    deliveryDetails.setDoorNumber("D101");
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .setPurchaseReferenceStatus(String.valueOf(POStatus.ACTV));
    doReturn(true)
        .when(rdcInstructionUtils)
        .isSSTKDocument(
            any(com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument.class));
    doReturn(deliveryDetails)
        .when(rdcSSTKLabelGenerationUtils)
        .fetchDeliveryDetails(anyString(), anyLong());
    DeliveryEvent deliveryEvent = getMockDeliveryEvent();
    deliveryEvent.setEventType(ReceivingConstants.EVENT_PO_UPDATED);
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getRdcDeliveryEventToProcess(any());
    when(appConfig.getGdmBaseUrl()).thenReturn(gdmBaseUrl);
    doReturn(deliveryService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    ArgumentCaptor<DeliveryEvent> deliveryEventArgumentCaptor =
        ArgumentCaptor.forClass(DeliveryEvent.class);
    when(deliveryEventPersisterService.save(deliveryEventArgumentCaptor.capture()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    rdcLabelGenerationServiceTemp.processDeliveryEvent(deliveryUpdateMessage);
    verify(deliveryEventPersisterService, times(1))
        .getRdcDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventPersisterService, times(1)).save(any());
    verify(rdcSSTKLabelGenerationUtils, times(1)).fetchDeliveryDetails(anyString(), anyLong());
    assertEquals(EventTargetStatus.DELETE, deliveryEventArgumentCaptor.getValue().getEventStatus());
  }

  @Test
  public void testProcessDeliveryEvent_POLineAddedEvent() throws ReceivingException {
    RdcLabelGenerationService rdcLabelGenerationServiceTemp =
        Mockito.spy(rdcLabelGenerationService);
    DeliveryUpdateMessage deliveryUpdateMessage = getDeliveryUpdateMessage();
    deliveryUpdateMessage.setDeliveryStatus(DeliveryStatus.OPN.toString());
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_PO_LINE_ADDED);
    deliveryDetails.setDoorNumber("D101");
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .setPurchaseReferenceStatus(String.valueOf(POStatus.ACTV));
    doReturn(true)
        .when(rdcInstructionUtils)
        .isSSTKDocument(
            any(com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument.class));
    doReturn(deliveryDetails)
        .when(rdcSSTKLabelGenerationUtils)
        .fetchDeliveryDetails(anyString(), anyLong());
    DeliveryEvent deliveryEvent = getMockDeliveryEvent();
    deliveryEvent.setEventType(ReceivingConstants.EVENT_PO_LINE_ADDED);
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getRdcDeliveryEventToProcess(any());
    when(appConfig.getGdmBaseUrl()).thenReturn(gdmBaseUrl);
    doReturn(deliveryService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    ArgumentCaptor<DeliveryEvent> deliveryEventArgumentCaptor =
        ArgumentCaptor.forClass(DeliveryEvent.class);
    when(deliveryEventPersisterService.save(deliveryEventArgumentCaptor.capture()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(slottingService.getPrimeSlot(anyList(), any(HttpHeaders.class)))
        .thenReturn(getSlottingPalletResponse());
    doReturn(null).when(rdcSSTKItemValidator).validateItem(any(DeliveryDocumentLine.class));
    when(lpnCacheService.getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList("a326790000000000000012345", "a326790000000000000012346"));
    when(labelDownloadEventService.saveAll(anyList())).thenAnswer(i -> i.getArguments()[0]);
    when(rdcSSTKLabelGenerationUtils.buildLabelDownloadPayloadForSSTK(
            any(com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine.class),
            anyList(),
            nullable(RejectReason.class),
            anyLong()))
        .thenReturn(getMockSSTKLabelDownloadPayload());
    doReturn(labelDataPublisher)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.LABEL_DATA_PUBLISHER), any());
    doNothing().when(labelDataPublisher).publish(any(), any());
    rdcLabelGenerationServiceTemp.processDeliveryEvent(deliveryUpdateMessage);
    verify(deliveryEventPersisterService, times(1))
        .getRdcDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventPersisterService, times(1)).save(any());
    verify(rdcSSTKLabelGenerationUtils, times(1)).fetchDeliveryDetails(anyString(), anyLong());
    verify(labelDataPublisher, times(2)).publish(any(), any());
    assertEquals(EventTargetStatus.DELETE, deliveryEventArgumentCaptor.getValue().getEventStatus());
  }

  @Test
  public void testProcessDeliveryEvent_POLineAddedEvent_CancelledPOLineStatus()
      throws ReceivingException {
    RdcLabelGenerationService rdcLabelGenerationServiceTemp =
        Mockito.spy(rdcLabelGenerationService);
    DeliveryUpdateMessage deliveryUpdateMessage = getDeliveryUpdateMessage();
    deliveryUpdateMessage.setDeliveryStatus(DeliveryStatus.OPN.toString());
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_PO_LINE_ADDED);
    deliveryDetails.setDoorNumber("D101");
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .setPurchaseReferenceStatus(String.valueOf(POStatus.ACTV));
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseReferenceLineStatus(POLineStatus.CANCELLED.name());
    doReturn(true)
        .when(rdcInstructionUtils)
        .isSSTKDocument(
            any(com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument.class));
    doReturn(deliveryDetails)
        .when(rdcSSTKLabelGenerationUtils)
        .fetchDeliveryDetails(anyString(), anyLong());
    DeliveryEvent deliveryEvent = getMockDeliveryEvent();
    deliveryEvent.setEventType(ReceivingConstants.EVENT_PO_LINE_ADDED);
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getRdcDeliveryEventToProcess(any());
    when(appConfig.getGdmBaseUrl()).thenReturn(gdmBaseUrl);
    doReturn(deliveryService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    ArgumentCaptor<DeliveryEvent> deliveryEventArgumentCaptor =
        ArgumentCaptor.forClass(DeliveryEvent.class);
    when(deliveryEventPersisterService.save(deliveryEventArgumentCaptor.capture()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(slottingService.getPrimeSlot(anyList(), any(HttpHeaders.class)))
        .thenReturn(getSlottingPalletResponse());
    rdcLabelGenerationServiceTemp.processDeliveryEvent(deliveryUpdateMessage);
    verify(deliveryEventPersisterService, times(1))
        .getRdcDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventPersisterService, times(1)).save(any());
    verify(rdcSSTKLabelGenerationUtils, times(1)).fetchDeliveryDetails(anyString(), anyLong());
  }

  @Test
  public void testProcessDeliveryEvent_POLineUpdatedEvent() throws ReceivingException {
    RdcLabelGenerationService rdcLabelGenerationServiceTemp =
        Mockito.spy(rdcLabelGenerationService);
    DeliveryUpdateMessage deliveryUpdateMessage = getDeliveryUpdateMessage();
    deliveryUpdateMessage.setDeliveryStatus(DeliveryStatus.OPN.toString());
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_PO_LINE_UPDATED);
    deliveryDetails.setDoorNumber("D101");
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .setPurchaseReferenceStatus(String.valueOf(POStatus.ACTV));
    doReturn(true)
        .when(rdcInstructionUtils)
        .isSSTKDocument(
            any(com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument.class));
    doReturn(deliveryDetails)
        .when(rdcSSTKLabelGenerationUtils)
        .fetchDeliveryDetails(anyString(), anyLong());
    DeliveryEvent deliveryEvent = getMockDeliveryEvent();
    deliveryEvent.setEventType(ReceivingConstants.EVENT_PO_LINE_UPDATED);
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getRdcDeliveryEventToProcess(any());
    when(appConfig.getGdmBaseUrl()).thenReturn(gdmBaseUrl);
    doReturn(deliveryService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    ArgumentCaptor<DeliveryEvent> deliveryEventArgumentCaptor =
        ArgumentCaptor.forClass(DeliveryEvent.class);
    when(deliveryEventPersisterService.save(deliveryEventArgumentCaptor.capture()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(slottingService.getPrimeSlot(anyList(), any(HttpHeaders.class)))
        .thenReturn(getSlottingPalletResponse());
    doReturn(null).when(rdcSSTKItemValidator).validateItem(any(DeliveryDocumentLine.class));
    when(lpnCacheService.getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList("a326790000000000000012345", "a326790000000000000012346"));
    when(labelDownloadEventService.saveAll(anyList())).thenAnswer(i -> i.getArguments()[0]);
    when(rdcSSTKLabelGenerationUtils.buildLabelDownloadPayloadForSSTK(
            any(com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine.class),
            anyList(),
            nullable(RejectReason.class),
            anyLong()))
        .thenReturn(getMockSSTKLabelDownloadPayload());
    doReturn(labelDataPublisher)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.LABEL_DATA_PUBLISHER), any());
    doNothing().when(labelDataPublisher).publish(any(), any());
    rdcLabelGenerationServiceTemp.processDeliveryEvent(deliveryUpdateMessage);
    verify(deliveryEventPersisterService, times(1))
        .getRdcDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventPersisterService, times(1)).save(any());
    verify(rdcSSTKLabelGenerationUtils, times(1)).fetchDeliveryDetails(anyString(), anyLong());
    verify(labelDataPublisher, times(2)).publish(any(), any());
    assertEquals(EventTargetStatus.DELETE, deliveryEventArgumentCaptor.getValue().getEventStatus());
  }

  @Test
  public void testProcessDeliveryEvent_POLineUpdatedEvent_ReceivedPOLineStatus()
      throws ReceivingException {
    RdcLabelGenerationService rdcLabelGenerationServiceTemp =
        Mockito.spy(rdcLabelGenerationService);
    DeliveryUpdateMessage deliveryUpdateMessage = getDeliveryUpdateMessage();
    deliveryUpdateMessage.setDeliveryStatus(DeliveryStatus.OPN.toString());
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_PO_LINE_UPDATED);
    deliveryDetails.setDoorNumber("D101");
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .setPurchaseReferenceStatus(String.valueOf(POStatus.ACTV));
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseReferenceLineStatus(POLineStatus.RECEIVED.name());
    doReturn(true)
        .when(rdcInstructionUtils)
        .isSSTKDocument(
            any(com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument.class));
    doReturn(deliveryDetails)
        .when(rdcSSTKLabelGenerationUtils)
        .fetchDeliveryDetails(anyString(), anyLong());
    DeliveryEvent deliveryEvent = getMockDeliveryEvent();
    deliveryEvent.setEventType(ReceivingConstants.EVENT_PO_LINE_UPDATED);
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getRdcDeliveryEventToProcess(any());
    when(appConfig.getGdmBaseUrl()).thenReturn(gdmBaseUrl);
    doReturn(deliveryService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    ArgumentCaptor<DeliveryEvent> deliveryEventArgumentCaptor =
        ArgumentCaptor.forClass(DeliveryEvent.class);
    when(deliveryEventPersisterService.save(deliveryEventArgumentCaptor.capture()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(slottingService.getPrimeSlot(anyList(), any(HttpHeaders.class)))
        .thenReturn(getSlottingPalletResponse());
    rdcLabelGenerationServiceTemp.processDeliveryEvent(deliveryUpdateMessage);
    verify(deliveryEventPersisterService, times(1))
        .getRdcDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventPersisterService, times(1)).save(any());
    verify(rdcSSTKLabelGenerationUtils, times(1)).fetchDeliveryDetails(anyString(), anyLong());
  }

  @Test
  public void testProcessDeliveryEvent_POLineUpdatedEvent_CancelledPOLineStatus()
      throws ReceivingException {
    RdcLabelGenerationService rdcLabelGenerationServiceTemp =
        Mockito.spy(rdcLabelGenerationService);
    DeliveryUpdateMessage deliveryUpdateMessage = getDeliveryUpdateMessage();
    deliveryUpdateMessage.setDeliveryStatus(DeliveryStatus.OPN.toString());
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_PO_LINE_UPDATED);
    List<LabelData> mockLabelDataList = new ArrayList<>();
    LabelData mockLabelData1 = getLabelData();
    mockLabelData1.setLabelType(LabelType.OVERAGE);
    mockLabelDataList.add(mockLabelData1);
    LabelData mockLabelData2 = getLabelData();
    mockLabelData2.setLabelType(LabelType.ORDERED);
    mockLabelDataList.add(mockLabelData2);
    doReturn(mockLabelDataList)
        .when(labelDataService)
        .fetchByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(anyString(), anyInt());
    deliveryDetails.setDoorNumber("D101");
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .setPurchaseReferenceStatus(String.valueOf(POStatus.ACTV));
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseReferenceLineStatus(POLineStatus.CANCELLED.name());
    doReturn(true)
        .when(rdcInstructionUtils)
        .isSSTKDocument(
            any(com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument.class));
    doReturn(deliveryDetails)
        .when(rdcSSTKLabelGenerationUtils)
        .fetchDeliveryDetails(anyString(), anyLong());
    DeliveryEvent deliveryEvent = getMockDeliveryEvent();
    deliveryEvent.setEventType(ReceivingConstants.EVENT_PO_LINE_UPDATED);
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getRdcDeliveryEventToProcess(any());
    when(appConfig.getGdmBaseUrl()).thenReturn(gdmBaseUrl);
    doReturn(deliveryService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    ArgumentCaptor<DeliveryEvent> deliveryEventArgumentCaptor =
        ArgumentCaptor.forClass(DeliveryEvent.class);
    when(deliveryEventPersisterService.save(deliveryEventArgumentCaptor.capture()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(slottingService.getPrimeSlot(anyList(), any(HttpHeaders.class)))
        .thenReturn(getSlottingPalletResponse());
    rdcLabelGenerationServiceTemp.processDeliveryEvent(deliveryUpdateMessage);
    verify(deliveryEventPersisterService, times(1))
        .getRdcDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventPersisterService, times(1)).save(any());
    verify(rdcSSTKLabelGenerationUtils, times(1)).fetchDeliveryDetails(anyString(), anyLong());
    verify(labelDataService, times(1))
        .fetchByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(anyString(), anyInt());
  }

  @Test
  public void testProcessDeliveryEvent_notPreLabelEvent() throws ReceivingException {
    DeliveryUpdateMessage deliveryUpdateMessage = getDeliveryUpdateMessage();
    deliveryUpdateMessage.setEventType("SCHEDULED");
    deliveryDetails.setDoorNumber("D101");
    rdcLabelGenerationService.processDeliveryEvent(deliveryUpdateMessage);
    verify(deliveryEventPersisterService, times(0))
        .getRdcDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventPersisterService, times(0)).save(any());
    verify(deliveryService, times(0)).getDeliveryDetails(anyString(), anyLong());
  }

  @Test
  public void testProcessDeliveryEvent_deliveryNotInProcessableState() throws ReceivingException {
    DeliveryUpdateMessage deliveryUpdateMessage = getDeliveryUpdateMessage();
    deliveryUpdateMessage.setDeliveryStatus(DeliveryStatus.SCH.toString());
    deliveryDetails.setDoorNumber("D101");
    rdcLabelGenerationService.processDeliveryEvent(deliveryUpdateMessage);
    verify(deliveryEventPersisterService, times(0))
        .getRdcDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventPersisterService, times(0)).save(any());
    verify(deliveryService, times(0)).getDeliveryDetails(anyString(), anyLong());
  }

  public void testProcessDeliveryEvent_fetchDeliveryDetailsThrowsException()
      throws ReceivingException {
    DeliveryUpdateMessage deliveryUpdateMessage = getDeliveryUpdateMessage();
    deliveryDetails.setDoorNumber("D101");
    ArgumentCaptor<DeliveryEvent> deliveryEventArgumentCaptor =
        ArgumentCaptor.forClass(DeliveryEvent.class);
    when(appConfig.getGdmBaseUrl()).thenReturn(gdmBaseUrl);
    doReturn(deliveryService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    doThrow(
            new ReceivingException(
                ReceivingException.DELIVERY_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                ExceptionCodes.DELIVERY_NOT_FOUND))
        .when(rdcSSTKLabelGenerationUtils)
        .fetchDeliveryDetails(anyString(), anyLong());
    doReturn(getMockDeliveryEvent())
        .when(deliveryEventPersisterService)
        .getRdcDeliveryEventToProcess(any());
    when(deliveryEventPersisterService.save(deliveryEventArgumentCaptor.capture()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    rdcLabelGenerationService.processDeliveryEvent(deliveryUpdateMessage);
    verify(deliveryEventPersisterService, times(1))
        .getRdcDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventPersisterService, times(1)).save(any());
    verify(deliveryService, times(1)).getDeliveryDetails(anyString(), anyLong());
    assertEquals(
        EventTargetStatus.EVENT_PENDING, deliveryEventArgumentCaptor.getValue().getEventStatus());
  }

  @Test
  public void testProcessDeliveryEvent_fetchDeliveryDetailsReturnsNull() throws ReceivingException {
    DeliveryUpdateMessage deliveryUpdateMessage = getDeliveryUpdateMessage();
    deliveryDetails.setDoorNumber("D101");
    ArgumentCaptor<DeliveryEvent> deliveryEventArgumentCaptor =
        ArgumentCaptor.forClass(DeliveryEvent.class);
    when(appConfig.getGdmBaseUrl()).thenReturn(gdmBaseUrl);
    doReturn(deliveryService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    doReturn(null).when(rdcSSTKLabelGenerationUtils).fetchDeliveryDetails(anyString(), anyLong());
    doReturn(getMockDeliveryEvent())
        .when(deliveryEventPersisterService)
        .getRdcDeliveryEventToProcess(any());
    when(deliveryEventPersisterService.save(deliveryEventArgumentCaptor.capture()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    rdcLabelGenerationService.processDeliveryEvent(deliveryUpdateMessage);
    verify(deliveryEventPersisterService, times(1))
        .getRdcDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventPersisterService, times(1)).save(any());
    verify(rdcSSTKLabelGenerationUtils, times(1)).fetchDeliveryDetails(anyString(), anyLong());
    assertEquals(
        EventTargetStatus.EVENT_PENDING, deliveryEventArgumentCaptor.getValue().getEventStatus());
  }

  @Test
  public void testProcessDeliveryEvent_DA_POEventReceived() throws ReceivingException {
    DeliveryUpdateMessage deliveryUpdateMessage = getDeliveryUpdateMessage();
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_PO_LINE_UPDATED);
    deliveryDetails.setDoorNumber("D101");
    when(appConfig.getGdmBaseUrl()).thenReturn(gdmBaseUrl);
    doReturn(deliveryService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    doReturn(deliveryDetails)
        .when(rdcSSTKLabelGenerationUtils)
        .fetchDeliveryDetails(anyString(), anyLong());
    doReturn(Boolean.FALSE)
        .when(rdcInstructionUtils)
        .isSSTKDocument(
            any(com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument.class));
    rdcLabelGenerationService.processDeliveryEvent(deliveryUpdateMessage);
    verify(rdcSSTKLabelGenerationUtils, times(1)).fetchDeliveryDetails(anyString(), anyLong());
    verify(deliveryEventPersisterService, times(0))
        .getRdcDeliveryEventToProcess(any(DeliveryUpdateMessage.class));
  }

  @Test
  public void testProcessDeliveryEvent_DaPOs_DoorAssign() throws ReceivingException {
    DeliveryUpdateMessage deliveryUpdateMessage = getDeliveryUpdateMessage();
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_DOOR_ASSIGNED);
    deliveryDetails.setDoorNumber("D101");
    when(appConfig.getGdmBaseUrl()).thenReturn(gdmBaseUrl);
    doReturn(deliveryService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    doReturn(deliveryDetails)
        .when(rdcSSTKLabelGenerationUtils)
        .fetchDeliveryDetails(anyString(), anyLong());
    doReturn(Boolean.FALSE)
        .when(rdcInstructionUtils)
        .isSSTKDocument(
            any(com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument.class));
    rdcLabelGenerationService.processDeliveryEvent(deliveryUpdateMessage);
    verify(rdcSSTKLabelGenerationUtils, times(1)).fetchDeliveryDetails(anyString(), anyLong());
    verify(deliveryEventPersisterService, times(1))
        .getRdcDeliveryEventToProcess(any(DeliveryUpdateMessage.class));
  }

  @Test
  public void testProcessDeliveryEvent_deliveryEventIsNull() throws ReceivingException {
    DeliveryUpdateMessage deliveryUpdateMessage = getDeliveryUpdateMessage();
    deliveryDetails.setDoorNumber("D101");
    when(appConfig.getGdmBaseUrl()).thenReturn(gdmBaseUrl);
    doReturn(deliveryService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    doReturn(deliveryDetails)
        .when(rdcSSTKLabelGenerationUtils)
        .fetchDeliveryDetails(anyString(), anyLong());
    doReturn(Boolean.TRUE)
        .when(rdcInstructionUtils)
        .isSSTKDocument(
            any(com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument.class));
    doReturn(null).when(deliveryEventPersisterService).getRdcDeliveryEventToProcess(any());
    rdcLabelGenerationService.processDeliveryEvent(deliveryUpdateMessage);
    verify(deliveryEventPersisterService, times(1))
        .getRdcDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventPersisterService, times(0)).save(any());
    verify(rdcSSTKLabelGenerationUtils, times(1)).fetchDeliveryDetails(anyString(), anyLong());
  }

  @Test
  public void testProcessDeliveryEvent_labelGenerationThrowsException() throws ReceivingException {
    RdcLabelGenerationService rdcLabelGenerationServiceTemp =
        Mockito.spy(rdcLabelGenerationService);
    DeliveryUpdateMessage deliveryUpdateMessage = getDeliveryUpdateMessage();
    ArgumentCaptor<DeliveryEvent> deliveryEventArgumentCaptor =
        ArgumentCaptor.forClass(DeliveryEvent.class);
    DeliveryEvent deliveryEvent = getMockDeliveryEvent();
    deliveryEvent.setEventStatus(EventTargetStatus.STALE);
    deliveryDetails.setDoorNumber("D101");
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .setPurchaseReferenceStatus(String.valueOf(POStatus.ACTV));
    doReturn(deliveryDetails)
        .when(rdcSSTKLabelGenerationUtils)
        .fetchDeliveryDetails(anyString(), anyLong());
    doReturn(true)
        .when(rdcInstructionUtils)
        .isSSTKDocument(
            any(com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument.class));
    doReturn(getMockDeliveryEvent())
        .when(deliveryEventPersisterService)
        .getRdcDeliveryEventToProcess(any());
    when(deliveryEventPersisterService.save(deliveryEventArgumentCaptor.capture()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(appConfig.getGdmBaseUrl()).thenReturn(gdmBaseUrl);
    doReturn(deliveryService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    doThrow(
            new ReceivingException(
                ReceivingConstants.LPNS_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                ExceptionCodes.LPNS_NOT_FOUND))
        .when(rdcLabelGenerationServiceTemp)
        .generateGenericLabelForSSTK(
            any(DeliveryEvent.class), anyList(), any(DeliveryUpdateMessage.class));
    when(slottingService.getPrimeSlot(anyList(), any(HttpHeaders.class)))
        .thenReturn(getSlottingPalletResponse());
    rdcLabelGenerationServiceTemp.processDeliveryEvent(deliveryUpdateMessage);
    verify(deliveryEventPersisterService, times(1))
        .getRdcDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventPersisterService, times(1)).save(any());
    verify(rdcSSTKLabelGenerationUtils, times(1)).fetchDeliveryDetails(anyString(), anyLong());
    assertEquals(
        EventTargetStatus.EVENT_PENDING, deliveryEventArgumentCaptor.getValue().getEventStatus());
  }

  @Test
  public void testProcessDeliveryEventAsync_Success() throws ReceivingException {
    RdcLabelGenerationService rdcLabelGenerationServiceTemp =
        Mockito.spy(rdcLabelGenerationService);
    DeliveryUpdateMessage deliveryUpdateMessage = getDeliveryUpdateMessage();
    deliveryUpdateMessage.setDeliveryStatus(DeliveryStatus.ARV.toString());
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_DOOR_ASSIGNED);
    deliveryDetails.setDoorNumber("D101");
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .setPurchaseReferenceStatus(String.valueOf(POStatus.ACTV));
    doReturn(deliveryDetails)
        .when(rdcSSTKLabelGenerationUtils)
        .fetchDeliveryDetails(anyString(), anyLong());
    doReturn(true)
        .when(rdcInstructionUtils)
        .isSSTKDocument(
            any(com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument.class));
    doReturn(getMockDeliveryEvent())
        .when(deliveryEventPersisterService)
        .getRdcDeliveryEventToProcess(any());
    when(appConfig.getGdmBaseUrl()).thenReturn(gdmBaseUrl);
    doReturn(deliveryService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    ArgumentCaptor<DeliveryEvent> deliveryEventArgumentCaptor =
        ArgumentCaptor.forClass(DeliveryEvent.class);
    when(deliveryEventPersisterService.save(deliveryEventArgumentCaptor.capture()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(slottingService.getPrimeSlot(anyList(), any(HttpHeaders.class)))
        .thenReturn(getSlottingPalletResponse());
    doReturn(null).when(rdcSSTKItemValidator).validateItem(any(DeliveryDocumentLine.class));
    when(lpnCacheService.getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList("a326790000000000000012345", "a326790000000000000012346"));
    when(labelDownloadEventService.saveAll(anyList())).thenAnswer(i -> i.getArguments()[0]);
    when(rdcSSTKLabelGenerationUtils.buildLabelDownloadPayloadForSSTK(
            any(com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine.class),
            anyList(),
            nullable(RejectReason.class),
            anyLong()))
        .thenReturn(getMockSSTKLabelDownloadPayload());
    doReturn(labelDataPublisher)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.LABEL_DATA_PUBLISHER), any());
    doNothing().when(labelDataPublisher).publish(any(), any());
    rdcLabelGenerationService.processDeliveryEventAsync(deliveryUpdateMessage);
    verify(deliveryEventPersisterService, times(1))
        .getRdcDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventPersisterService, times(1)).save(any());
    verify(rdcSSTKLabelGenerationUtils, times(1)).fetchDeliveryDetails(anyString(), anyLong());
    verify(labelDataPublisher, times(2)).publish(any(), any());
    assertEquals(EventTargetStatus.DELETE, deliveryEventArgumentCaptor.getValue().getEventStatus());
  }

  @Test
  public void testValidateDuplicateLpnsAndProcessLabelData_DuplicateLpns_WithDeltaLabels()
      throws IOException, ReceivingException {
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new Gson()
            .fromJson(
                MockInstructionDownloadEvent.INSTRUCTION_DOWNLOAD_EVENT,
                InstructionDownloadMessageDTO.class);
    instructionDownloadMessageDTO.setHttpHeaders(MockHttpHeaders.getHeaders());
    instructionDownloadMessageDTO.setPoNumber("8458708163");
    instructionDownloadMessageDTO.setTrackingIds(Collections.singletonList("trackingId"));
    instructionDownloadMessageDTO.setPurchaseReferenceLineNumber(1);
    List<LabelData> labelDataList = new ArrayList<>();
    labelDataList.add(getLabelData());
    labelDataList.add(getLabelData());
    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_UPDATE_POSSIBLE_UPC_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    doReturn(labelDataList)
        .when(labelDataService)
        .findByPurchaseReferenceNumberAndItemNumberAndStatus(anyString(), anyLong(), anyString());
    doReturn(deliveryDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER), any());
    doReturn(MockDeliveryDocuments.getDeliveryDocumentsForMultipleDA())
        .when(deliveryDocumentsSearchHandler)
        .fetchDeliveryDocumentByItemNumber(anyString(), anyInt(), any(HttpHeaders.class));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.SMART_SLOTTING_INTEGRATION_ENABLED), anyBoolean()))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(ReceivingConstants.IS_IQS_INTEGRATION_ENABLED), anyBoolean()))
        .thenReturn(true);
    when(rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(anyLong(), anyLong()))
        .thenReturn(true);
    doNothing()
        .when(rdcInstructionUtils)
        .populatePrimeSlotFromSmartSlotting(
            any(DeliveryDocument.class), any(HttpHeaders.class), any(InstructionRequest.class));
    doReturn(Boolean.TRUE).when(rdcInstructionUtils).isSSTKDocument(any(DeliveryDocument.class));
    doNothing()
        .when(rdcInstructionService)
        .checkIfAtlasConvertedItem(eq(null), anyList(), any(HttpHeaders.class));
    doCallRealMethod().when(rdcInstructionUtils).updateAdditionalItemDetailsFromGDM(anyList());
    doReturn(RejectReason.BREAKOUT).when(rdcItemValidator).validateItem(anyList());
    doReturn(labelDataPublisher)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.LABEL_DATA_PUBLISHER), any());
    doNothing().when(labelDataPublisher).publish(any(), any());
    doNothing().when(rdcInstructionUtils).updateAdditionalItemDetailsFromGDM(anyList());
    doReturn(getMockLabelDownloadPayload())
        .when(rdcLabelGenerationUtils)
        .buildLabelDownloadForHawkeye(
            any(DeliveryDocument.class), captor.capture(), any(RejectReason.class));
    doReturn(Collections.singletonList(mockLabelDownloadEvent()))
        .when(labelDownloadEventService)
        .saveAll(anyList());
    rdcLabelGenerationService.processLabelsForAutomation(
        instructionDownloadMessageDTO, labelDataList);
    assertEquals(2, captor.getValue().size());
    verify(tenantSpecificConfigReader, times(2))
        .getConfiguredInstance(anyString(), anyString(), any());
    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByItemNumber(anyString(), anyInt(), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(6))
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
    verify(rdcInstructionUtils, times(1))
        .populatePrimeSlotFromSmartSlotting(
            any(DeliveryDocument.class), any(HttpHeaders.class), any(InstructionRequest.class));
    verify(rdcInstructionService, times(0))
        .checkIfAtlasConvertedItem(eq(null), anyList(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1)).updateAdditionalItemDetailsFromGDM(anyList());
    verify(rdcItemValidator, times(1)).validateItem(anyList());
    verify(labelDataPublisher, times(1)).publish(any(), any());
    verify(labelDataService, times(1))
        .findByPurchaseReferenceNumberAndItemNumberAndStatus(anyString(), anyLong(), anyString());
    verify(rdcLabelGenerationUtils, times(1))
        .buildLabelDownloadForHawkeye(
            any(DeliveryDocument.class), anyList(), any(RejectReason.class));
    verify(labelDownloadEventService, times(2)).saveAll(anyList());
  }

  private List<LabelData> getLabelDataForDA() {
    LabelData labelData1 = getLabelData();
    LabelData labelData2 = getLabelData();
    labelData2.setTrackingId("010840132679204331");
    return Arrays.asList(labelData1, labelData2);
  }

  private LabelData getLabelData() {
    LabelData labelData = new LabelData();
    labelData.setPurchaseReferenceNumber("5232232323");
    labelData.setPurchaseReferenceLineNumber(1);
    labelData.setDeliveryNumber(232323323L);
    labelData.setTrackingId("c060200000100000025796158");
    LabelDataAllocationDTO allocation = getMockLabelDataAllocationDTO();
    labelData.setAllocation(allocation);
    labelData.setStatus("AVAILABLE");
    labelData.setLabelSequenceNbr(20231016000100001L);
    return labelData;
  }

  private LabelDataAllocationDTO getMockLabelDataAllocationDTO() {
    LabelDataAllocationDTO allocation = new LabelDataAllocationDTO();
    InstructionDownloadContainerDTO container = new InstructionDownloadContainerDTO();
    container.setTrackingId("c060200000100000025796158");
    container.setCtrType("CASE");
    container.setOutboundChannelMethod("DA");
    InstructionDownloadDistributionsDTO distributions = new InstructionDownloadDistributionsDTO();
    InstructionDownloadItemDTO item = new InstructionDownloadItemDTO();
    item.setItemNbr(658790758L);
    item.setAisle("12");
    item.setItemUpc("78236478623");
    item.setPickBatch("281");
    item.setPrintBatch("281");
    item.setZone("03");
    item.setVnpk(1);
    item.setWhpk(1);
    distributions.setItem(item);
    container.setDistributions(Collections.singletonList(distributions));
    Facility finalDestination = new Facility();
    finalDestination.setBuNumber("87623");
    finalDestination.setCountryCode("US");
    container.setFinalDestination(finalDestination);
    allocation.setContainer(container);
    return allocation;
  }

  private ACLLabelDataTO getMockLabelDownloadPayload() {
    ACLLabelDataTO aclLabelDataTO = new ACLLabelDataTO();
    aclLabelDataTO.setGroupNumber("123456");
    FormattedLabels formattedLabels =
        FormattedLabels.builder()
            .seqNo("1")
            .purchaseReferenceLineNumber(1)
            .purchaseReferenceNumber("123456789")
            .lpns(Collections.singletonList("c060200000100000025796158"))
            .build();
    ScanItem scanItem =
        ScanItem.builder()
            .item(123456L)
            .itemDesc("ITEM DESC")
            .groupType("RCV_DA")
            .labels(Collections.singletonList(formattedLabels))
            .build();
    aclLabelDataTO.setScanItems(Collections.singletonList(scanItem));
    return aclLabelDataTO;
  }

  @Test
  public void testPublishNewLabelsToHawkeye_SendingUniqueLpns() {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    LabelData labelData = getMockLabelData();

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    when(rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(anyLong(), anyLong()))
        .thenReturn(true);
    doNothing()
        .when(rdcLabelGenerationService)
        .processAndPublishLabelData(
            any(InstructionDownloadMessageDTO.class),
            eq(Collections.singletonList(labelData)),
            eq(null));
    rdcLabelGenerationService.publishNewLabelToHawkeye(labelData, httpHeaders);
    verify(tenantSpecificConfigReader, times(2))
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
    verify(rdcLabelGenerationService, times(1))
        .processAndPublishLabelData(
            any(InstructionDownloadMessageDTO.class),
            eq(Collections.singletonList(labelData)),
            eq(null));
  }

  @Test
  public void testPublishNewLabelsToHawkeye_SendingUniqueLpnsDisabled() {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    LabelData labelData = getMockLabelData();
    List<LabelDownloadEvent> labelDownloadEventList = new ArrayList<>();
    labelDownloadEventList.add(getMockLabelDownloadEvent());
    LabelDownloadEvent labelDownloadEvent = getMockLabelDownloadEvent();
    labelDownloadEvent.setDeliveryNumber(82963726L);
    labelDownloadEventList.add(labelDownloadEvent);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);

    when(labelDownloadEventService.findByPurchaseReferenceNumberAndItemNumber(
            anyString(), anyLong()))
        .thenReturn(labelDownloadEventList);
    when(rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(anyLong(), anyLong()))
        .thenReturn(true);
    doNothing()
        .when(rdcLabelGenerationService)
        .processAndPublishLabelData(
            any(InstructionDownloadMessageDTO.class),
            eq(Collections.singletonList(labelData)),
            any(LabelDownloadEvent.class));
    rdcLabelGenerationService.publishNewLabelToHawkeye(labelData, httpHeaders);
    verify(tenantSpecificConfigReader, times(2))
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
    verify(labelDownloadEventService, times(1))
        .findByPurchaseReferenceNumberAndItemNumber(anyString(), anyLong());
    verify(rdcLabelGenerationService, times(2))
        .processAndPublishLabelData(
            any(InstructionDownloadMessageDTO.class),
            eq(Collections.singletonList(labelData)),
            any(LabelDownloadEvent.class));
  }

  @Test
  public void testPublishNewLabelsToHawkeye_NotAutomationEnabledSite() {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(ReceivingConstants.TENENT_FACLITYNUM, "32679");

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.FALSE);
    rdcLabelGenerationService.publishNewLabelToHawkeye(getMockLabelData(), httpHeaders);
    verify(rdcLabelGenerationService, times(0))
        .processAndPublishLabelData(
            any(InstructionDownloadMessageDTO.class), anyList(), any(LabelDownloadEvent.class));
  }

  @Test
  public void testPublishNewLabelsToHawkeye_EmptyHeaders() {
    HttpHeaders httpHeaders = new HttpHeaders();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    rdcLabelGenerationService.publishNewLabelToHawkeye(getMockLabelData(), httpHeaders);
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
  }

  @Test
  public void testPublsihNewLabelsToHawkeye_IgnoreProcessing() {
    HttpHeaders httpHeaders = new HttpHeaders();
    when(rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(anyLong(), anyLong()))
        .thenReturn(true);
    rdcLabelGenerationService.publishNewLabelToHawkeye(getMockLabelData(), httpHeaders);
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
  }

  @Test
  public void testRepublishLabelsToHawkeye() throws IOException, ReceivingException {
    Set<Long> deliveryList1 = new HashSet<>(Arrays.asList(123456L, 345678L));
    Set<Long> deliveryList2 = new HashSet<>(Arrays.asList(345678L, 123456L));
    Map<String, Set<Long>> poToDeliveryMap = new HashMap<>();
    poToDeliveryMap.put("8458708163", deliveryList1);
    poToDeliveryMap.put("8458708164", deliveryList2);
    doReturn(getLabelDataForDA())
        .when(labelDataService)
        .findByPurchaseReferenceNumberAndItemNumberAndStatus(anyString(), anyLong(), anyString());
    doReturn(null).when(rdcItemValidator).validateItem(anyList());
    doReturn(getMockLabelDownloadPayload())
        .when(rdcLabelGenerationUtils)
        .buildLabelDownloadForHawkeye(any(DeliveryDocument.class), anyList(), eq(null));
    doReturn(deliveryDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER), any());
    doReturn(MockDeliveryDocuments.getDeliveryDocumentsForMultipleDA())
        .when(deliveryDocumentsSearchHandler)
        .fetchDeliveryDocumentByItemNumber(anyString(), anyInt(), any(HttpHeaders.class));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.SMART_SLOTTING_INTEGRATION_ENABLED), anyBoolean()))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(ReceivingConstants.IS_IQS_INTEGRATION_ENABLED), anyBoolean()))
        .thenReturn(true);
    when(rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(anyLong(), anyLong()))
        .thenReturn(true);
    doNothing()
        .when(rdcInstructionUtils)
        .populatePrimeSlotFromSmartSlotting(
            any(DeliveryDocument.class), any(HttpHeaders.class), any(InstructionRequest.class));
    doReturn(Boolean.TRUE).when(rdcInstructionUtils).isSSTKDocument(any(DeliveryDocument.class));
    doNothing()
        .when(rdcInstructionService)
        .checkIfAtlasConvertedItem(eq(null), anyList(), any(HttpHeaders.class));
    doNothing().when(rdcInstructionUtils).updateAdditionalItemDetailsFromGDM(anyList());
    doReturn(labelDataPublisher)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.LABEL_DATA_PUBLISHER), any());
    doNothing().when(labelDataPublisher).publish(any(), any());

    rdcLabelGenerationService.republishLabelsToHawkeye(
        poToDeliveryMap, getItemUpdateMessage(), false);
    verify(labelDataService, times(2))
        .findByPurchaseReferenceNumberAndItemNumberAndStatus(anyString(), anyLong(), anyString());
    verify(tenantSpecificConfigReader, times(8)).getConfiguredInstance(anyString(), any(), any());
    verify(labelDataPublisher, times(4)).publish(any(), any());
    verify(deliveryDocumentsSearchHandler, times(4))
        .fetchDeliveryDocumentByItemNumber(anyString(), anyInt(), any(HttpHeaders.class));
    verify(rdcInstructionService, times(0))
        .checkIfAtlasConvertedItem(eq(null), anyList(), any(HttpHeaders.class));
    verify(rdcItemValidator, times(4)).validateItem(anyList());
    verify(rdcLabelGenerationUtils, times(4))
        .buildLabelDownloadForHawkeye(any(DeliveryDocument.class), anyList(), eq(null));
    verify(labelDownloadEventService, times(5)).saveAll(anyList());
  }

  @Test
  public void testRepublishLabelsToHawkeye_PublishSSTKLabelData_Success()
      throws IOException, ReceivingException {
    Set<Long> deliveryList1 = new HashSet<>(Arrays.asList(123456L, 345678L));
    Map<String, Set<Long>> poToDeliveryMap = new HashMap<>();
    poToDeliveryMap.put("3615852071", deliveryList1);
    List<LabelData> labelDataList = getLabelDataForDA();
    labelDataList.get(0).setItemNumber(566051127L);
    doReturn(labelDataList)
        .when(labelDataService)
        .findByPurchaseReferenceNumberAndItemNumberAndStatus(anyString(), anyLong(), anyString());
    doReturn(null).when(rdcItemValidator).validateItem(anyList());
    doReturn(getMockLabelDownloadPayload())
        .when(rdcSSTKLabelGenerationUtils)
        .buildLabelDownloadPayloadForSSTK(
            any(DeliveryDocumentLine.class), anyList(), nullable(RejectReason.class), anyLong());
    deliveryDetails.setDoorNumber("D101");
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .setPurchaseReferenceStatus(String.valueOf(POStatus.ACTV));
    doReturn(deliveryDetails)
        .when(rdcSSTKLabelGenerationUtils)
        .fetchDeliveryDetails(anyString(), anyLong());
    doReturn(deliveryService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.DELIVERY_SERVICE_KEY), any());
    doReturn(MockDeliveryDocuments.getDeliveryDocumentsForMultipleDA())
        .when(deliveryDocumentsSearchHandler)
        .fetchDeliveryDocumentByItemNumber(anyString(), anyInt(), any(HttpHeaders.class));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.SMART_SLOTTING_INTEGRATION_ENABLED), anyBoolean()))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(ReceivingConstants.IS_IQS_INTEGRATION_ENABLED), anyBoolean()))
        .thenReturn(true);
    when(rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(anyLong(), anyLong()))
        .thenReturn(true);
    doNothing()
        .when(rdcInstructionUtils)
        .populatePrimeSlotFromSmartSlotting(
            any(DeliveryDocument.class), any(HttpHeaders.class), any(InstructionRequest.class));
    doReturn(Boolean.TRUE).when(rdcInstructionUtils).isSSTKDocument(any(DeliveryDocument.class));
    doNothing()
        .when(rdcInstructionService)
        .checkIfAtlasConvertedItem(eq(null), anyList(), any(HttpHeaders.class));
    doCallRealMethod().when(rdcInstructionUtils).updateAdditionalItemDetailsFromGDM(anyList());
    doReturn(labelDataPublisher)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.LABEL_DATA_PUBLISHER), any());
    ItemUpdateMessage itemUpdateMessage = getItemUpdateMessage();
    itemUpdateMessage.setItemNumber(566051127);
    doNothing().when(labelDataPublisher).publish(any(), any());
    rdcLabelGenerationService.republishLabelsToHawkeye(poToDeliveryMap, itemUpdateMessage, true);
    verify(labelDataService, times(1))
        .findByPurchaseReferenceNumberAndItemNumberAndStatus(anyString(), anyLong(), anyString());
    verify(rdcInstructionService, times(0))
        .checkIfAtlasConvertedItem(eq(null), anyList(), any(HttpHeaders.class));
    verify(labelDownloadEventService, times(3)).saveAll(anyList());
  }

  @Test
  public void testRepublishLabelsToHawkeye_PublishSSTKLabelData_Failure()
      throws IOException, ReceivingException {
    Set<Long> deliveryList1 = new HashSet<>(Arrays.asList(123456L, 345678L));
    Set<Long> deliveryList2 = new HashSet<>(Arrays.asList(345678L, 123456L));
    Map<String, Set<Long>> poToDeliveryMap = new HashMap<>();
    poToDeliveryMap.put("8458708163", deliveryList1);
    poToDeliveryMap.put("8458708164", deliveryList2);
    List<LabelData> labelDataList = getLabelDataForDA();
    labelDataList.get(0).setItemNumber(566051128L);
    doReturn(labelDataList)
        .when(labelDataService)
        .findByPurchaseReferenceNumberAndItemNumberAndStatus(anyString(), anyLong(), anyString());
    doReturn(null).when(rdcItemValidator).validateItem(anyList());
    doReturn(getMockLabelDownloadPayload())
        .when(rdcLabelGenerationUtils)
        .buildLabelDownloadForHawkeye(any(DeliveryDocument.class), anyList(), eq(null));
    deliveryDetails.setDoorNumber("D101");
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .setPurchaseReferenceStatus(String.valueOf(POStatus.ACTV));
    doReturn(deliveryDetails)
        .when(rdcSSTKLabelGenerationUtils)
        .fetchDeliveryDetails(anyString(), anyLong());
    doReturn(deliveryService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.DELIVERY_SERVICE_KEY), any());
    doReturn(MockDeliveryDocuments.getDeliveryDocumentsForMultipleDA())
        .when(deliveryDocumentsSearchHandler)
        .fetchDeliveryDocumentByItemNumber(anyString(), anyInt(), any(HttpHeaders.class));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.SMART_SLOTTING_INTEGRATION_ENABLED), anyBoolean()))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(ReceivingConstants.IS_IQS_INTEGRATION_ENABLED), anyBoolean()))
        .thenReturn(true);
    when(rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(anyLong(), anyLong()))
        .thenReturn(true);
    doNothing()
        .when(rdcInstructionUtils)
        .populatePrimeSlotFromSmartSlotting(
            any(DeliveryDocument.class), any(HttpHeaders.class), any(InstructionRequest.class));
    doReturn(Boolean.TRUE).when(rdcInstructionUtils).isSSTKDocument(any(DeliveryDocument.class));
    doNothing()
        .when(rdcInstructionService)
        .checkIfAtlasConvertedItem(eq(null), anyList(), any(HttpHeaders.class));
    doCallRealMethod().when(rdcInstructionUtils).updateAdditionalItemDetailsFromGDM(anyList());
    doReturn(labelDataPublisher)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.LABEL_DATA_PUBLISHER), any());
    doNothing().when(labelDataPublisher).publish(any(), any());
    rdcLabelGenerationService.republishLabelsToHawkeye(
        poToDeliveryMap, getItemUpdateMessage(), true);
    verify(labelDataService, times(2))
        .findByPurchaseReferenceNumberAndItemNumberAndStatus(anyString(), anyLong(), anyString());
    verify(rdcInstructionService, times(0))
        .checkIfAtlasConvertedItem(eq(null), anyList(), any(HttpHeaders.class));
    verify(labelDownloadEventService, times(1)).saveAll(anyList());
  }

  @Test
  public void testRepublishLabelsToHawkeye_noLabelsForPOAndItem()
      throws IOException, ReceivingException {
    Set<Long> deliveryList1 = new HashSet<>(Arrays.asList(123456L, 345678L));
    Set<Long> deliveryList2 = new HashSet<>(Arrays.asList(345678L, 123456L));
    Map<String, Set<Long>> poToDeliveryMap = new HashMap<>();
    poToDeliveryMap.put("8458708163", deliveryList1);
    poToDeliveryMap.put("8458708164", deliveryList2);
    doReturn(getLabelDataForDA())
        .when(labelDataService)
        .findByPurchaseReferenceNumberAndItemNumberAndStatus(
            eq("8458708163"), anyLong(), anyString());
    doReturn(null).when(rdcItemValidator).validateItem(anyList());
    doReturn(getMockLabelDownloadPayload())
        .when(rdcLabelGenerationUtils)
        .buildLabelDownloadForHawkeye(any(DeliveryDocument.class), anyList(), eq(null));
    doReturn(deliveryDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER), any());
    doReturn(MockDeliveryDocuments.getDeliveryDocumentsForMultipleDA())
        .when(deliveryDocumentsSearchHandler)
        .fetchDeliveryDocumentByItemNumber(anyString(), anyInt(), any(HttpHeaders.class));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(RdcConstants.SMART_SLOTTING_INTEGRATION_ENABLED), anyBoolean()))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(ReceivingConstants.IS_IQS_INTEGRATION_ENABLED), anyBoolean()))
        .thenReturn(true);
    when(rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(anyLong(), anyLong()))
        .thenReturn(true);
    doNothing()
        .when(rdcInstructionUtils)
        .populatePrimeSlotFromSmartSlotting(
            any(DeliveryDocument.class), any(HttpHeaders.class), any(InstructionRequest.class));
    doReturn(Boolean.TRUE).when(rdcInstructionUtils).isSSTKDocument(any(DeliveryDocument.class));
    doNothing()
        .when(rdcInstructionService)
        .checkIfAtlasConvertedItem(eq(null), anyList(), any(HttpHeaders.class));
    doCallRealMethod().when(rdcInstructionUtils).updateAdditionalItemDetailsFromGDM(anyList());
    doReturn(labelDataPublisher)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.LABEL_DATA_PUBLISHER), any());
    doNothing().when(labelDataPublisher).publish(any(), any());
    doNothing().when(rdcInstructionUtils).updateAdditionalItemDetailsFromGDM(anyList());
    rdcLabelGenerationService.republishLabelsToHawkeye(
        poToDeliveryMap, getItemUpdateMessage(), false);
    verify(labelDataService, times(1))
        .findByPurchaseReferenceNumberAndItemNumberAndStatus(
            eq("8458708163"), anyLong(), anyString());
    verify(tenantSpecificConfigReader, times(2))
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.LABEL_DATA_PUBLISHER), any());
    verify(labelDataPublisher, times(2)).publish(any(), any());
    verify(deliveryDocumentsSearchHandler, times(2))
        .fetchDeliveryDocumentByItemNumber(anyString(), anyInt(), any(HttpHeaders.class));
    verify(rdcInstructionService, times(0))
        .checkIfAtlasConvertedItem(eq(null), anyList(), any(HttpHeaders.class));
    verify(rdcItemValidator, times(2)).validateItem(anyList());
    verify(rdcLabelGenerationUtils, times(2))
        .buildLabelDownloadForHawkeye(any(DeliveryDocument.class), anyList(), eq(null));
    verify(labelDownloadEventService, times(3)).saveAll(anyList());
  }

  @Test
  public void testGenerateSSTKLabels_rejectReason_SSTK() throws ReceivingException {
    ArgumentCaptor<com.walmart.move.nim.receiving.core.contract.prelabel.model.LabelData>
        payloadCaptor =
            ArgumentCaptor.forClass(
                com.walmart.move.nim.receiving.core.contract.prelabel.model.LabelData.class);
    when(rdcLabelGenerationUtils.getPossibleUPCv2(any(DeliveryDocumentLine.class)))
        .thenReturn(new PossibleUPC());
    when(labelDataService.saveAll(anyList())).thenAnswer(i -> i.getArguments()[0]);
    when(labelDownloadEventService.findByDeliveryNumberAndPurchaseReferenceNumberAndItemNumber(
            anyLong(), anyString(), anyLong()))
        .thenReturn(Collections.emptyList());
    when(labelDownloadEventService.saveAll(anyList())).thenAnswer(i -> i.getArguments()[0]);
    when(slottingService.getPrimeSlot(anyList(), any(HttpHeaders.class)))
        .thenReturn(getSlottingPalletResponse());
    when(rdcSSTKInstructionUtils.isAtlasItemSymEligible(any(DeliveryDocumentLine.class)))
        .thenReturn(true);
    doReturn(labelDataPublisher)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.LABEL_DATA_PUBLISHER), any());
    doReturn(RejectReason.RDC_SSTK)
        .when(rdcSSTKItemValidator)
        .validateItem(any(DeliveryDocumentLine.class));
    doNothing().when(labelDataPublisher).publish(payloadCaptor.capture(), any());
    doCallRealMethod()
        .when(rdcSSTKLabelGenerationUtils)
        .buildLabelDownloadPayloadForSSTK(
            any(com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine.class),
            anyList(),
            any(RejectReason.class),
            anyLong());
    doReturn(RejectReason.RDC_SSTK)
        .when(rdcSSTKItemValidator)
        .validateItem(any(DeliveryDocumentLine.class));
    DeliveryUpdateMessage deliveryUpdateMessage =
        DeliveryUpdateMessage.builder()
            .deliveryNumber("23424243")
            .poNumber("232323323")
            .httpHeaders(MockHttpHeaders.getHeaders())
            .build();
    rdcLabelGenerationService.generateAndPublishSSTKLabels(
        deliveryDetails.getDeliveryDocuments(), deliveryUpdateMessage, Boolean.FALSE, null);
    verify(labelDataService, times(0))
        .findByPurchaseReferenceNumberAndItemNumberAndStatus(anyString(), anyLong(), anyString());
    verify(labelDownloadEventService, times(2)).saveAll(anyList());
    assertNotNull(payloadCaptor.getValue());
  }

  @Test
  public void testGenerateSSTKLabels_Po_Line_Updates() throws ReceivingException {
    deliveryDetails.getDeliveryDocuments().get(0).setDeliveryNumber(123456L);
    when(lpnCacheService.getLPNSBasedOnTenant(eq(6), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList(orderQtyLpnSet));
    when(lpnCacheService.getLPNSBasedOnTenant(eq(2), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList(overageQtyLpnSet));
    when(rdcLabelGenerationUtils.getPossibleUPCv2(any(DeliveryDocumentLine.class)))
        .thenReturn(new PossibleUPC());
    when(labelDataService.saveAll(anyList())).thenAnswer(i -> i.getArguments()[0]);
    when(labelDownloadEventService.findByDeliveryNumberAndPurchaseReferenceNumberAndItemNumber(
            anyLong(), anyString(), anyLong()))
        .thenReturn(Collections.emptyList());
    when(labelDownloadEventService.saveAll(anyList())).thenAnswer(i -> i.getArguments()[0]);
    when(slottingService.getPrimeSlot(anyList(), any(HttpHeaders.class)))
        .thenReturn(getSlottingPalletResponse());
    when(rdcSSTKInstructionUtils.isAtlasItemSymEligible(any(DeliveryDocumentLine.class)))
        .thenReturn(true);
    doReturn(labelDataPublisher)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.LABEL_DATA_PUBLISHER), any());
    doNothing().when(labelDataPublisher).publish(any(), any());
    doReturn(getMockSSTKLabelDownloadPayload())
        .when(rdcSSTKLabelGenerationUtils)
        .buildLabelDownloadPayloadForSSTK(
            any(com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine.class),
            anyList(),
            eq(null),
            anyLong());
    doReturn(null).when(rdcSSTKItemValidator).validateItem(any(DeliveryDocumentLine.class));
    DeliveryUpdateMessage deliveryUpdateMessage =
        DeliveryUpdateMessage.builder()
            .deliveryNumber("23424243")
            .poNumber("232323323")
            .httpHeaders(MockHttpHeaders.getHeaders())
            .build();
    rdcLabelGenerationService.generateAndPublishSSTKLabels(
        deliveryDetails.getDeliveryDocuments(), deliveryUpdateMessage, Boolean.TRUE, null);
    verify(labelDataService, times(1))
        .fetchByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(anyString(), anyInt());
    verify(lpnCacheService, times(2)).getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class));
    verify(rdcLabelGenerationUtils, times(2)).getPossibleUPCv2(any(DeliveryDocumentLine.class));
    verify(labelDataService, times(1)).saveAllAndFlush(anyList());
    verify(labelDownloadEventService, times(2)).saveAll(anyList());
  }

  @Test
  public void testGenerateSSTKLabels_Po_Line_Updates_Labels_Generated() throws ReceivingException {
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setExpectedQty(2);
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setOverageQtyLimit(2);
    deliveryDetails.getDeliveryDocuments().get(0).setDeliveryNumber(232323323L);
    DeliveryUpdateMessage deliveryUpdateMessage =
        DeliveryUpdateMessage.builder()
            .deliveryNumber("23424243")
            .poNumber("232323323")
            .httpHeaders(MockHttpHeaders.getHeaders())
            .build();
    List<LabelData> mockOverageLabelDataList = getLabelDataByPOAndItem();
    mockOverageLabelDataList.forEach(labelData -> labelData.setLabelType(LabelType.OVERAGE));
    List<LabelData> mockOrderedLabelDataList = getLabelDataByPOAndItem();
    mockOrderedLabelDataList.forEach(labelData -> labelData.setLabelType(LabelType.ORDERED));
    List<LabelData> labelDataList =
        Stream.concat(mockOverageLabelDataList.stream(), mockOrderedLabelDataList.stream())
            .collect(Collectors.toList());
    labelDataList.forEach(
        labelData -> labelData.setStatus(LabelInstructionStatus.AVAILABLE.name()));
    doReturn(labelDataList)
        .when(labelDataService)
        .fetchByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(anyString(), anyInt());
    when(rdcLabelGenerationUtils.getPossibleUPCv2(any(DeliveryDocumentLine.class)))
        .thenReturn(new PossibleUPC());
    when(labelDataService.saveAll(anyList())).thenAnswer(i -> i.getArguments()[0]);
    when(labelDownloadEventService.findByDeliveryNumberAndPurchaseReferenceNumberAndItemNumber(
            anyLong(), anyString(), anyLong()))
        .thenReturn(Collections.emptyList());
    when(labelDownloadEventService.saveAll(anyList())).thenAnswer(i -> i.getArguments()[0]);
    when(slottingService.getPrimeSlot(anyList(), any(HttpHeaders.class)))
        .thenReturn(getSlottingPalletResponse());
    when(rdcSSTKInstructionUtils.isAtlasItemSymEligible(any(DeliveryDocumentLine.class)))
        .thenReturn(true);
    doReturn(labelDataPublisher)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.LABEL_DATA_PUBLISHER), any());
    doNothing().when(labelDataPublisher).publish(any(), any());
    when(rdcSSTKLabelGenerationUtils.buildLabelDownloadPayloadForSSTK(
            any(DeliveryDocumentLine.class), anyList(), nullable(RejectReason.class), anyLong()))
        .thenReturn(getMockSSTKLabelDownloadPayload());
    doReturn(null).when(rdcSSTKItemValidator).validateItem(any(DeliveryDocumentLine.class));
    rdcLabelGenerationService.generateAndPublishSSTKLabels(
        deliveryDetails.getDeliveryDocuments(), deliveryUpdateMessage, Boolean.TRUE, null);
    verify(labelDataService, times(1))
        .fetchByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(anyString(), anyInt());
    verify(rdcSSTKItemValidator, times(1)).validateItem(any(DeliveryDocumentLine.class));
    verify(labelDataService, times(0)).saveAll(anyList());
    verify(labelDownloadEventService, times(2)).saveAll(anyList());
  }

  @Test
  public void testProcessDeliveryEventForScheduler_LabelGenerationSuccess()
      throws ReceivingException {
    doReturn(getMockDeliveryEvent())
        .when(deliveryEventPersisterService)
        .getRdcDeliveryEventToProcess(any());
    doReturn(deliveryService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    deliveryDetails.setDoorNumber("D101");
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .setPurchaseReferenceStatus(String.valueOf(POStatus.ACTV));
    doReturn(deliveryDetails)
        .when(rdcSSTKLabelGenerationUtils)
        .fetchDeliveryDetails(anyString(), anyLong());
    when(lpnCacheService.getLPNSBasedOnTenant(eq(6), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList(orderQtyLpnSet));
    when(lpnCacheService.getLPNSBasedOnTenant(eq(2), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList(overageQtyLpnSet));
    when(rdcSSTKLabelGenerationUtils.buildLabelDownloadPayloadForSSTK(
            any(DeliveryDocumentLine.class), anyList(), nullable(RejectReason.class), anyLong()))
        .thenReturn(getMockSSTKLabelDownloadPayload());
    doReturn(labelDataPublisher)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.LABEL_DATA_PUBLISHER), any());
    doNothing().when(labelDataPublisher).publish(any(), any());
    assertTrue(rdcLabelGenerationService.processDeliveryEventForScheduler(getMockDeliveryEvent()));
    verify(rdcSSTKLabelGenerationUtils, times(1)).fetchDeliveryDetails(anyString(), anyLong());
    verify(deliveryEventPersisterService, times(2)).save(any(DeliveryEvent.class));
  }

  @Test
  public void testProcessDeliveryEventForScheduler_LabelGenerationFailure()
      throws ReceivingException {
    DeliveryEvent deliveryEvent = getMockDeliveryEvent();
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getRdcDeliveryEventToProcess(any());
    doReturn(deliveryService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.DELIVERY_SERVICE_KEY), any());
    doReturn(Boolean.TRUE)
        .when(rdcInstructionUtils)
        .isSSTKDocument(
            any(com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument.class));
    deliveryDetails.setDoorNumber("D101");
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .setPurchaseReferenceStatus(String.valueOf(POStatus.ACTV));
    doReturn(deliveryDetails)
        .when(rdcSSTKLabelGenerationUtils)
        .fetchDeliveryDetails(anyString(), anyLong());
    doThrow(new ReceivingException("error", HttpStatus.SERVICE_UNAVAILABLE, "service down"))
        .when(rdcLabelGenerationService)
        .generateGenericLabelForSSTK(
            any(DeliveryEvent.class), anyList(), any(DeliveryUpdateMessage.class));
    assertFalse(rdcLabelGenerationService.processDeliveryEventForScheduler(getMockDeliveryEvent()));
    verify(rdcSSTKLabelGenerationUtils, times(1)).fetchDeliveryDetails(anyString(), anyLong());
    verify(deliveryEventPersisterService, times(2)).save(any(DeliveryEvent.class));
  }

  @Test
  public void testProcessDeliveryEventForScheduler_EmptyDeliveryDetails()
      throws ReceivingException {
    doReturn(getMockDeliveryEvent())
        .when(deliveryEventPersisterService)
        .getRdcDeliveryEventToProcess(any());
    doReturn(deliveryService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    deliveryDetails.setDoorNumber("D101");
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .setPurchaseReferenceStatus(String.valueOf(POStatus.ACTV));
    doReturn(null).when(rdcSSTKLabelGenerationUtils).fetchDeliveryDetails(anyString(), anyLong());
    assertFalse(rdcLabelGenerationService.processDeliveryEventForScheduler(getMockDeliveryEvent()));
    verify(rdcSSTKLabelGenerationUtils, times(1)).fetchDeliveryDetails(anyString(), anyLong());
    verify(deliveryEventPersisterService, times(2)).save(any(DeliveryEvent.class));
  }

  @Test
  public void testProcessDeliveryEventForScheduler_HasDADeliveryDocuments()
      throws ReceivingException {
    DeliveryEvent deliveryEvent = getMockDeliveryEvent();
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getRdcDeliveryEventToProcess(any());
    doReturn(deliveryService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.DELIVERY_SERVICE_KEY), any());
    doReturn(Boolean.FALSE)
        .when(rdcInstructionUtils)
        .isSSTKDocument(
            any(com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocument.class));
    deliveryDetails.setDoorNumber("D101");
    deliveryDetails
        .getDeliveryDocuments()
        .get(0)
        .setPurchaseReferenceStatus(String.valueOf(POStatus.ACTV));
    doReturn(deliveryDetails)
        .when(rdcSSTKLabelGenerationUtils)
        .fetchDeliveryDetails(anyString(), anyLong());
    assertTrue(rdcLabelGenerationService.processDeliveryEventForScheduler(getMockDeliveryEvent()));
    verify(rdcSSTKLabelGenerationUtils, times(1)).fetchDeliveryDetails(anyString(), anyLong());
    verify(deliveryEventPersisterService, times(2)).save(any(DeliveryEvent.class));
    verify(rdcLabelGenerationService, times(0))
        .generateGenericLabelForSSTK(
            any(DeliveryEvent.class), anyList(), any(DeliveryUpdateMessage.class));
  }

  @Test
  public void testProcessAndPublishLabelDataAsync() throws IOException, ReceivingException {
    LabelData labelData =
        LabelData.builder()
            .trackingId("a060200000000000000012345")
            .purchaseReferenceNumber("8458708163")
            .vnpk(2)
            .whpk(2)
            .build();
    when(labelDataService.findByPurchaseReferenceNumberAndItemNumberAndStatus(
            anyString(), anyLong(), anyString()))
        .thenReturn(Collections.singletonList(labelData));
    doReturn(deliveryDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    doReturn(MockDeliveryDocuments.getDeliveryDocumentsForMultipleDA())
        .when(deliveryDocumentsSearchHandler)
        .fetchDeliveryDocumentByItemNumber(anyString(), anyInt(), any(HttpHeaders.class));
    doReturn(getMockLabelDownloadPayload())
        .when(rdcLabelGenerationUtils)
        .buildLabelDownloadForHawkeye(
            any(DeliveryDocument.class), anyList(), any(RejectReason.class));
    when(rdcItemValidator.validateItem(anyList())).thenReturn(RejectReason.RDC_SSTK);
    doReturn(labelDataPublisher)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.LABEL_DATA_PUBLISHER), any());
    rdcLabelGenerationService.processAndPublishLabelDataAsync(
        2323289L,
        234627L,
        Collections.singleton("8458708163"),
        Collections.singletonList(labelData),
        MockHttpHeaders.getHeaders());
    verify(labelDownloadEventService, times(2)).saveAll(anyList());
    verify(rdcItemValidator, times(1)).validateItem(anyList());
    verify(deliveryDocumentsSearchHandler, times(1))
        .fetchDeliveryDocumentByItemNumber(anyString(), anyInt(), any(HttpHeaders.class));
  }

  private LabelData getMockLabelData() {
    LabelData labelData = new LabelData();
    labelData.setDeliveryNumber(232323323L);
    labelData.setPurchaseReferenceNumber("5232232323");
    labelData.setPurchaseReferenceLineNumber(1);
    labelData.setItemNumber(661298341L);
    return labelData;
  }

  private List<LabelData> getLabelDataByPOAndItem() {
    List<LabelData> labelDataList = new ArrayList<>();
    labelDataList.add(
        LabelData.builder()
            .labelSequenceNbr(20200420000840000L)
            .lpns("q0602038494374242")
            .deliveryNumber(2323289L)
            .id(1004)
            .itemNumber(234627L)
            .purchaseReferenceLineNumber(2)
            .purchaseReferenceNumber("867678")
            .build());
    labelDataList.add(
        LabelData.builder()
            .labelSequenceNbr(20200420000840001L)
            .lpns("q0602038494374243")
            .deliveryNumber(2323289L)
            .id(1005)
            .itemNumber(234627L)
            .purchaseReferenceLineNumber(2)
            .purchaseReferenceNumber("867678")
            .build());
    return labelDataList;
  }

  private DeliveryEvent getMockDeliveryEvent() {
    return DeliveryEvent.builder()
        .id(1)
        .eventStatus(EventTargetStatus.EVENT_PENDING)
        .eventType(ReceivingConstants.EVENT_DOOR_ASSIGNED)
        .deliveryNumber(123456L)
        .url("https://delivery.test")
        .retriesCount(0)
        .build();
  }

  private DeliveryUpdateMessage getDeliveryUpdateMessage() {
    return DeliveryUpdateMessage.builder()
        .deliveryNumber("123456")
        .countryCode("US")
        .siteNumber("6051")
        .deliveryStatus(DeliveryStatus.ARV.toString())
        .url("https://delivery.test")
        .eventType(ReceivingConstants.EVENT_DOOR_ASSIGNED)
        .poNumber("567890")
        .poLineNumber(10)
        .httpHeaders(MockHttpHeaders.getHeaders())
        .build();
  }

  private LabelDownloadEvent mockLabelDownloadEvent() {
    LabelDownloadEvent labelDownloadEvent = new LabelDownloadEvent();
    labelDownloadEvent.setId(Long.valueOf(1));
    labelDownloadEvent.setFacilityNum(32679);
    labelDownloadEvent.setDeliveryNumber(39380405l);
    labelDownloadEvent.setItemNumber(658232698l);
    labelDownloadEvent.setPurchaseReferenceNumber("5030140191");
    labelDownloadEvent.setDeliveryNumber(12345L);
    return labelDownloadEvent;
  }

  private LabelDownloadEvent getMockLabelDownloadEvent() {
    return LabelDownloadEvent.builder()
        .purchaseReferenceNumber("5232232323")
        .itemNumber(661298341L)
        .deliveryNumber(232323323L)
        .build();
  }

  private ItemUpdateMessage getItemUpdateMessage() {
    return ItemUpdateMessage.builder()
        .itemNumber(123456)
        .httpHeaders(MockHttpHeaders.getHeaders())
        .build();
  }

  private ACLLabelDataTO getMockSSTKLabelDownloadPayload() {
    ACLLabelDataTO aclLabelDataTO = new ACLLabelDataTO();
    aclLabelDataTO.setGroupNumber("123456");
    FormattedLabels formattedLabels =
        FormattedLabels.builder()
            .seqNo("1")
            .purchaseReferenceLineNumber(1)
            .purchaseReferenceNumber("123456789")
            .lpns(Collections.singletonList("c060200000100000025796158"))
            .build();
    ScanItem scanItem =
        ScanItem.builder()
            .item(123456L)
            .itemDesc("ITEM DESC")
            .groupType("RCV_SSTK")
            .labels(Collections.singletonList(formattedLabels))
            .build();
    aclLabelDataTO.setScanItems(Collections.singletonList(scanItem));
    return aclLabelDataTO;
  }

  private LabelSequence getLabelSequence() {
    return LabelSequence.builder()
        .itemNumber(876987l)
        .labelType(LabelType.ORDERED)
        .nextSequenceNo(220200421000800001L)
        .build();
  }

  @Test
  public void fetchLabelDataAndUpdateLabelStatusToCancelledTest() {
    com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine deliveryDocumentLine =
        new com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine();
    deliveryDocumentLine.setPurchaseReferenceNumber("5647669");
    deliveryDocumentLine.setItemNbr(876987l);
    HttpHeaders httpHeaders = new HttpHeaders();
    List<LabelData> labelDataList = new ArrayList<>();
    LabelData labelData1 = new LabelData();
    labelData1.setDeliveryNumber(123l);
    labelData1.setLabelSequenceNbr(2l);

    LabelData labelData2 = new LabelData();
    labelData2.setDeliveryNumber(456l);
    labelData2.setLabelSequenceNbr(1l);

    LabelData labelData3 = new LabelData();
    labelData3.setDeliveryNumber(756675l);
    labelData3.setLabelSequenceNbr(8l);

    LabelData labelData4 = new LabelData();
    labelData4.setDeliveryNumber(98798l);
    labelData4.setLabelSequenceNbr(3l);

    labelDataList.add(labelData1);
    labelDataList.add(labelData2);
    labelDataList.add(labelData3);
    labelDataList.add(labelData4);
    when(labelDataService.findByPurchaseReferenceNumberAndItemNumberAndStatus(
            anyString(), anyLong(), anyString()))
        .thenReturn(labelDataList);
    rdcLabelGenerationService.fetchLabelDataAndUpdateLabelStatusToCancelled(
        deliveryDocumentLine, httpHeaders, 3);
  }

  @Test
  public void processLabelsForAutomationWithNoLabelsTest() {
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new InstructionDownloadMessageDTO();
    HttpHeaders httpHeaders = new HttpHeaders();
    instructionDownloadMessageDTO.setDeliveryNumber(85647679l);
    instructionDownloadMessageDTO.setItemNumber(4346769l);
    instructionDownloadMessageDTO.setPoNumber("768798709");
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    List<LabelData> labelDataList = new ArrayList<>();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    when(rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(anyLong(), anyLong()))
        .thenReturn(true);
    rdcLabelGenerationService.processLabelsForAutomation(
        instructionDownloadMessageDTO, labelDataList);
    verify(labelDownloadEventService, times(1)).saveAll(anyList());
  }

  @Test
  public void republishLabelsToHawkeyeWithNoLabelsTest() {
    HttpHeaders httpHeaders = new HttpHeaders();
    Map<String, Set<Long>> poToDeliveryMap = new HashMap<>();
    Set<Long> deliveryNumbers = new HashSet<>();
    deliveryNumbers.add(879879l);
    deliveryNumbers.add(765877l);
    poToDeliveryMap.put("76686987", deliveryNumbers);
    ItemUpdateMessage itemUpdateMessage = new ItemUpdateMessage();
    itemUpdateMessage.setItemNumber(78698709);
    itemUpdateMessage.setHttpHeaders(httpHeaders);
    rdcLabelGenerationService.republishLabelsToHawkeye(poToDeliveryMap, itemUpdateMessage, true);
    List<LabelData> labelDataList = new ArrayList<>();
    LabelData labelData = new LabelData();
    labelDataList.add(labelData);
    when(labelDataService.findByPurchaseReferenceNumberAndItemNumberAndStatus(
            anyString(), anyLong(), anyString()))
        .thenReturn(labelDataList);
    verify(labelDownloadEventService, times(1)).saveAll(anyList());
  }

  @Test
  public void testRepublishLabelsToHawkeyeNew() {
    Set<Long> deliveryList1 = new HashSet<>(Arrays.asList(123456L, 345678L));
    Set<Long> deliveryList2 = new HashSet<>(Arrays.asList(345678L, 123456L));
    Map<String, Set<Long>> poToDeliveryMap = new HashMap<>();
    poToDeliveryMap.put("8458708163", deliveryList1);
    poToDeliveryMap.put("8458708164", deliveryList2);
    List<LabelData> labelDataForDA = new ArrayList<>();
    LabelData labelData = new LabelData();
    labelData.setTrackingId("1");
    labelDataForDA.add(labelData);
    doReturn(labelDataForDA)
        .when(labelDataService)
        .findByPurchaseReferenceNumberAndItemNumberAndStatus(anyString(), anyLong(), anyString());
    rdcLabelGenerationService.republishLabelsToHawkeye(
        poToDeliveryMap, getItemUpdateMessage(), false);
    verify(labelDownloadEventService, times(3)).saveAll(anyList());
  }

  @Test
  public void processAndPublishLabelDataTestWithPOLine() throws ReceivingException {
    InstructionDownloadMessageDTO instructionDownloadMessageDTO =
        new InstructionDownloadMessageDTO();
    List<LabelData> labelDataList = new ArrayList<>();
    LabelData labelData = new LabelData();
    labelDataList.add(labelData);
    LabelDownloadEvent labelDownloadEvent = new LabelDownloadEvent();
    instructionDownloadMessageDTO.setDeliveryNumber(78897997l);
    instructionDownloadMessageDTO.setItemNumber(7897979l);
    instructionDownloadMessageDTO.setPurchaseReferenceLineNumber(1);
    instructionDownloadMessageDTO.setPoNumber("8767860909");
    HttpHeaders httpHeaders = new HttpHeaders();
    instructionDownloadMessageDTO.setHttpHeaders(httpHeaders);
    doReturn(deliveryDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any());
    ErrorResponse errorResponse =
        ErrorResponse.builder()
            .errorMessage("mock error")
            .errorCode("error code")
            .errorHeader("error header")
            .errorKey(ExceptionCodes.PO_LINE_NOT_FOUND)
            .build();
    ReceivingException receivingException =
        ReceivingException.builder()
            .httpStatus(HttpStatus.NOT_FOUND)
            .errorResponse(errorResponse)
            .build();
    doThrow(receivingException)
        .when(deliveryDocumentsSearchHandler)
        .fetchDeliveryDocumentByItemNumber(anyString(), anyInt(), any(HttpHeaders.class));
    rdcLabelGenerationService.processAndPublishLabelData(
        instructionDownloadMessageDTO, labelDataList, labelDownloadEvent);
    verify(labelDownloadEventService, times(1)).saveAll(anyList());
  }
}
