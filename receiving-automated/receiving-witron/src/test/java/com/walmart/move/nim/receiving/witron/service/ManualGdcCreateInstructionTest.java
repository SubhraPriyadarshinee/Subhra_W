package com.walmart.move.nim.receiving.witron.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.slotting.SlottingPalletBuildResponse;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.validators.DeliveryValidator;
import com.walmart.move.nim.receiving.core.common.validators.PurchaseReferenceValidator;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.DeliveryItemOverride;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.data.GdcHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.witron.builder.ContainerLabelBuilder;
import com.walmart.move.nim.receiving.witron.common.GDCFlagReader;
import com.walmart.move.nim.receiving.witron.model.ContainerLabel;
import com.walmart.move.nim.receiving.witron.model.PrintLabelRequest;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import org.mockito.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ManualGdcCreateInstructionTest {
  private final Gson gson = new Gson();
  @InjectMocks private GdcInstructionService gdcInstructionService;

  @InjectMocks @Spy
  private VendorBasedDeliveryDocumentsSearchHandler vendorBasedDeliveryDocumentsSearchHandler;

  @Mock private AppConfig appConfig;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private LPNCacheService lpnCacheService;
  @Mock private GdcSlottingServiceImpl slottingService;
  @Mock private DeliveryService deliveryService;
  @Mock private InstructionService instructionService;
  @Mock private InstructionRepository instructionRepository;
  @Mock private InstructionHelperService instructionHelperService;
  @Mock private InstructionPersisterService instructionPersisterService;
  @Mock private DeliveryItemOverrideService deliveryItemOverrideService;
  @Mock private PurchaseReferenceValidator purchaseReferenceValidator;
  @Mock private DeliveryDocumentHelper deliveryDocumentHelper;
  @Mock private ASNReceivingAuditLogger asnReceivingAuditLogger;
  @Mock private ContainerLabelBuilder containerLabelBuilder;
  @Mock private ContainerService containerService;
  @Mock private GDCFlagReader gdcFlagReader;
  @Mock private InstructionUtils instructionUtils;

  private final HttpHeaders httpHeaders = GdcHttpHeaders.getManualGdcHeaders();

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(6071);

    ReflectionTestUtils.setField(
        gdcInstructionService, GdcInstructionService.class, "gson", gson, Gson.class);
    ReflectionTestUtils.setField(
        gdcInstructionService, InstructionService.class, "gson", gson, Gson.class);
    ReflectionTestUtils.setField(
        gdcInstructionService,
        InstructionService.class,
        "deliveryValidator",
        new DeliveryValidator(),
        DeliveryValidator.class);
    ReflectionTestUtils.setField(
        instructionService, "purchaseReferenceValidator", purchaseReferenceValidator);
    ReflectionTestUtils.setField(vendorBasedDeliveryDocumentsSearchHandler, "gson", gson);
    ReflectionTestUtils.setField(
        gdcInstructionService,
        GdcInstructionService.class,
        "instructionUtils",
        instructionUtils,
        InstructionUtils.class);
    ReflectionTestUtils.setField(
        gdcInstructionService,
        GdcInstructionService.class,
        "tenantSpecificConfigReader",
        configUtils,
        TenantSpecificConfigReader.class);
  }

  @BeforeMethod
  public void beforeMethod() {
    when(configUtils.getCcmValue(
            TenantContext.getFacilityNum(),
            ELIGIBLE_TRANSFER_POS_CCM_CONFIG,
            DEFAULT_ELIGIBLE_TRANSFER_PO_TYPE))
        .thenReturn("28");
  }

  @AfterMethod
  public void tearDown() {
    reset(lpnCacheService);
    reset(slottingService);
    reset(configUtils);
    reset(instructionRepository);
    reset(instructionPersisterService);
    reset(instructionHelperService);
    reset(deliveryItemOverrideService);
    reset(purchaseReferenceValidator);
    reset(deliveryService);
    reset(deliveryDocumentHelper);
    reset(asnReceivingAuditLogger);
    reset(containerLabelBuilder);
    reset(containerService);
    reset(gdcFlagReader);
  }

  @Test
  public void testServeInstructionReques_lpnGenDisabled() throws Exception {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setDeliveryNumber("9967271326");
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    instructionRequest.setDoorNumber("101");
    instructionRequest.setMessageId("a883782f-f811-4fc6-abff-65d14d4af231");
    instructionRequest.setUpcNumber("10681131135822");
    instructionRequest.setReceivingType("UPC");

    doReturn(vendorBasedDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            "6071",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    doReturn(deliveryService)
        .when(configUtils)
        .getConfiguredInstance(
            "6071", ReceivingConstants.DELIVERY_SERVICE_KEY, DeliveryService.class);

    File resource = new ClassPathResource("gdm_scanUpc_response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    doReturn(mockResponse)
        .when(deliveryService)
        .findDeliveryDocument(9967271326l, "10681131135822", httpHeaders);
    List<DeliveryDocument> deliveryDocuments =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    doReturn(deliveryDocuments).when(deliveryDocumentHelper).updateDeliveryDocuments(any());
    doReturn(true).when(configUtils).isDeliveryItemOverrideEnabled(anyInt());
    Optional<DeliveryItemOverride> optionalDeliveryItemOverride = Optional.empty();
    doReturn(optionalDeliveryItemOverride)
        .when(deliveryItemOverrideService)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());

    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doReturn(new Pair<>(830, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetails(eq(instructionRequest.getProblemTagId()), any());
    when(appConfig.isCheckDeliveryStatusReceivable()).thenReturn(false);
    when(gdcFlagReader.isManualGdcEnabled()).thenReturn(true);
    when(gdcFlagReader.isLpnGenApiDisabled()).thenReturn(true);
    when(gdcFlagReader.publishToWFTDisabled()).thenReturn(true);
    when(gdcFlagReader.isSmartSlottingApiDisabled()).thenReturn(true);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag("32612", ITEM_CONFIG_SERVICE_ENABLED, false);
    doReturn("012")
        .when(configUtils)
        .getCcmValue(
            anyInt(), eq(ELIGIBLE_TRANSFER_POS_CCM_CONFIG), eq(DEFAULT_ELIGIBLE_TRANSFER_PO_TYPE));
    InstructionResponse instructionResponse =
        gdcInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);

    verify(deliveryService, times(1))
        .findDeliveryDocument(9967271326l, "10681131135822", httpHeaders);
    verify(lpnCacheService, times(0)).getLPNBasedOnTenant(httpHeaders);
    verify(slottingService, times(0))
        .acquireSlot(any(InstructionRequest.class), anyString(), anyString(), any());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(instructionHelperService, times(0))
        .publishInstruction(any(), any(), any(), any(), any(), any());

    assertNotNull(instructionResponse);
    assertNull(instructionResponse.getInstruction().getContainer());
    assertNull(instructionResponse.getInstruction().getMove());
    assertEquals(instructionResponse.getInstruction().getProviderId(), "GDC-RCV");
    assertEquals(instructionResponse.getInstruction().getInstructionCode(), "ManlGrocBuildPallet");
    assertEquals(
        instructionResponse.getInstruction().getInstructionMsg(), "Manl Groc Build Pallet");
  }

  @Test
  public void testServeInstructionReques_lpnGenEnabled() throws Exception {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setDeliveryNumber("9967271326");
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    instructionRequest.setDoorNumber("101");
    instructionRequest.setMessageId("a883782f-f811-4fc6-abff-65d14d4af231");
    instructionRequest.setUpcNumber("10681131135822");
    instructionRequest.setReceivingType("UPC");

    doReturn(vendorBasedDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            "6071",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    doReturn(deliveryService)
        .when(configUtils)
        .getConfiguredInstance(
            "6071", ReceivingConstants.DELIVERY_SERVICE_KEY, DeliveryService.class);

    File resource = new ClassPathResource("gdm_scanUpc_response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    doReturn(mockResponse)
        .when(deliveryService)
        .findDeliveryDocument(9967271326l, "10681131135822", httpHeaders);
    List<DeliveryDocument> deliveryDocuments =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));
    doReturn(deliveryDocuments).when(deliveryDocumentHelper).updateDeliveryDocuments(any());
    doReturn(true).when(configUtils).isDeliveryItemOverrideEnabled(anyInt());
    Optional<DeliveryItemOverride> optionalDeliveryItemOverride = Optional.empty();
    doReturn(optionalDeliveryItemOverride)
        .when(deliveryItemOverrideService)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());

    doReturn("A0607100002000001").when(lpnCacheService).getLPNBasedOnTenant(httpHeaders);

    SlottingPalletBuildResponse slottingPalletBuildResponse = new SlottingPalletBuildResponse();
    slottingPalletBuildResponse.setContainerTrackingId("A0607100002000001");
    slottingPalletBuildResponse.setDivertLocation("SLOT-1");
    doReturn(slottingPalletBuildResponse)
        .when(slottingService)
        .acquireSlot(any(InstructionRequest.class), anyString(), anyString(), any());

    ContainerLabel containerLabel = new ContainerLabel();
    containerLabel.setClientId("GDC-RCV");
    List<PrintLabelRequest> printRequests = new ArrayList<>();
    containerLabel.setPrintRequests(printRequests);
    doReturn(containerLabel)
        .when(containerLabelBuilder)
        .generateContainerLabel(anyString(), anyString(), any(DeliveryDocumentLine.class), any());

    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doReturn(new Pair<>(830, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetails(eq(instructionRequest.getProblemTagId()), any());
    when(appConfig.isCheckDeliveryStatusReceivable()).thenReturn(false);
    when(gdcFlagReader.isManualGdcEnabled()).thenReturn(true);
    doReturn("012")
        .when(configUtils)
        .getCcmValue(
            anyInt(), eq(ELIGIBLE_TRANSFER_POS_CCM_CONFIG), eq(DEFAULT_ELIGIBLE_TRANSFER_PO_TYPE));
    InstructionResponse instructionResponse =
        gdcInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);

    verify(deliveryService, times(1))
        .findDeliveryDocument(9967271326l, "10681131135822", httpHeaders);
    verify(lpnCacheService, times(1)).getLPNBasedOnTenant(httpHeaders);
    verify(slottingService, times(1))
        .acquireSlot(any(InstructionRequest.class), anyString(), anyString(), any());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(instructionHelperService, times(1))
        .publishInstruction(any(), any(), any(), any(), any(), any());

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction().getContainer());
    assertNotNull(instructionResponse.getInstruction().getMove());
    assertEquals(instructionResponse.getInstruction().getProviderId(), "GDC-RCV");
    assertEquals(instructionResponse.getInstruction().getInstructionCode(), "ManlGrocBuildPallet");
    assertEquals(
        instructionResponse.getInstruction().getInstructionMsg(), "Manl Groc Build Pallet");
  }

  @Test
  public void testServeInstructionReques_glsDelivery() throws Exception {
    try {
      InstructionRequest instructionRequest = new InstructionRequest();
      instructionRequest.setDeliveryNumber("9967271326");
      instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
      instructionRequest.setDoorNumber("101");
      instructionRequest.setMessageId("a883782f-f811-4fc6-abff-65d14d4af231");
      instructionRequest.setUpcNumber("10681131135822");
      instructionRequest.setReceivingType("UPC");

      doReturn(vendorBasedDeliveryDocumentsSearchHandler)
          .when(configUtils)
          .getConfiguredInstance(
              "6071",
              ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
              DeliveryDocumentsSearchHandler.class);

      doReturn(deliveryService)
          .when(configUtils)
          .getConfiguredInstance(
              "6071", ReceivingConstants.DELIVERY_SERVICE_KEY, DeliveryService.class);

      File resource = new ClassPathResource("gdc_scanUpc_response.json").getFile();
      String mockResponse = new String(Files.readAllBytes(resource.toPath()));
      doReturn(mockResponse)
          .when(deliveryService)
          .findDeliveryDocument(9967271326l, "10681131135822", httpHeaders);

      gdcInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);

    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), "GLS-RCV-DELIVERY-400");
      assertEquals(e.getDescription(), "This delivery will need to be received in GLS");
    }
  }

  @Test
  public void testServeInstructionReques_IgnoreNewItemAlert() throws Exception {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setDeliveryNumber("9967271326");
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    instructionRequest.setDoorNumber("101");
    instructionRequest.setMessageId("a883782f-f811-4fc6-abff-65d14d4af231");
    instructionRequest.setUpcNumber("10681131135822");
    instructionRequest.setReceivingType("UPC");

    doReturn(vendorBasedDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            "6071",
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    doReturn(deliveryService)
        .when(configUtils)
        .getConfiguredInstance(
            "6071", ReceivingConstants.DELIVERY_SERVICE_KEY, DeliveryService.class);
    doReturn("012")
        .when(configUtils)
        .getCcmValue(
            anyInt(), eq(ELIGIBLE_TRANSFER_POS_CCM_CONFIG), eq(DEFAULT_ELIGIBLE_TRANSFER_PO_TYPE));
    File resource = new ClassPathResource("gdm_scanUpc_response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    doReturn(mockResponse)
        .when(deliveryService)
        .findDeliveryDocument(9967271326l, "10681131135822", httpHeaders);
    List<DeliveryDocument> deliveryDocuments =
        Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class));

    // Set profiledWarehouseArea as null for new item
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setProfiledWarehouseArea(null);

    doReturn(deliveryDocuments).when(deliveryDocumentHelper).updateDeliveryDocuments(any());
    doReturn(true).when(configUtils).isDeliveryItemOverrideEnabled(anyInt());
    Optional<DeliveryItemOverride> optionalDeliveryItemOverride = Optional.empty();
    doReturn(optionalDeliveryItemOverride)
        .when(deliveryItemOverrideService)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());

    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doReturn(new Pair<>(830, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetails(eq(instructionRequest.getProblemTagId()), any());
    when(appConfig.isCheckDeliveryStatusReceivable()).thenReturn(false);
    when(gdcFlagReader.isManualGdcEnabled()).thenReturn(true);
    when(gdcFlagReader.isLpnGenApiDisabled()).thenReturn(true);
    when(gdcFlagReader.publishToWFTDisabled()).thenReturn(true);
    when(gdcFlagReader.isSmartSlottingApiDisabled()).thenReturn(true);

    InstructionResponse instructionResponse =
        gdcInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);

    verify(deliveryService, times(1))
        .findDeliveryDocument(9967271326l, "10681131135822", httpHeaders);
    verify(lpnCacheService, times(0)).getLPNBasedOnTenant(httpHeaders);
    verify(slottingService, times(0))
        .acquireSlot(any(InstructionRequest.class), anyString(), anyString(), any());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(instructionHelperService, times(0))
        .publishInstruction(any(), any(), any(), any(), any(), any());

    assertNotNull(instructionResponse);
    assertNull(instructionResponse.getInstruction().getContainer());
    assertNull(instructionResponse.getInstruction().getMove());
    assertEquals(instructionResponse.getInstruction().getProviderId(), "GDC-RCV");
    assertEquals(instructionResponse.getInstruction().getInstructionCode(), "ManlGrocBuildPallet");
    assertEquals(
        instructionResponse.getInstruction().getInstructionMsg(), "Manl Groc Build Pallet");
  }
}
