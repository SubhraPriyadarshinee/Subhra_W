package com.walmart.move.nim.receiving.rdc.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.GDM_SSCC_SCAN_ASN_NOT_FOUND;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceivedQuantityResponseFromRDS;
import com.walmart.move.nim.receiving.core.common.DeliveryDocumentHelper;
import com.walmart.move.nim.receiving.core.common.MovePublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.validators.DeliveryValidator;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.helper.ProblemReceivingHelper;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.labeldownload.RejectReason;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.client.ngr.NgrRestApiClient;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.mock.data.*;
import com.walmart.move.nim.receiving.rdc.model.wft.RdcInstructionType;
import com.walmart.move.nim.receiving.rdc.utils.RdcInstructionUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcReceivingUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import org.mockito.*;
import org.mockito.stubbing.Answer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RdcInstructionServiceTest {

  private final Gson gson = new Gson();

  @Mock private RdcManagedConfig rdcManagedConfig;
  @Mock private MovePublisher movePublisher;
  @Mock private NimRdsService nimRdsService;
  @Mock private RdcInstructionUtils rdcInstructionUtils;
  @Mock private InstructionPersisterService instructionPersisterService;
  @Mock private InstructionRepository instructionRepository;
  @Mock private RegulatedItemService regulatedItemService;
  @Mock private RdcFixitProblemService rdcFixitProblemService;
  @Mock private ProblemReceivingHelper problemReceivingHelper;
  @Mock private RdcDeliveryService rdcDeliveryService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private ProblemService problemService;
  @Mock private ASNReceivingAuditLogger asnReceivingAuditLogger;
  @Mock private MultiSkuService multiSkuService;
  @Mock private ContainerService containerService;
  @Mock private ScanTypeDeliveryDocumentsSearchHandler scanTypeDeliveryDocumentsSearchHandler;
  @Mock private DeliveryDocumentHelper deliveryDocumentHelper;
  @Mock private NgrRestApiClient ngrRestApiClient;
  @Mock private RdcDaService rdcDaService;
  @Mock private RdcReceiptBuilder rdcReceiptBuilder;
  @Mock private RdcInstructionHelper rdcInstructionHelper;
  @Mock private RdcReceivingUtils rdcReceivingUtils;
  @Mock private RdcDsdcService rdcDsdcService;
  @Mock private RdcAtlasDsdcService rdcAtlasDsdcService;
  private ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS;

  @InjectMocks @Spy
  VendorBasedDeliveryDocumentsSearchHandler vendorBasedDeliveryDocumentsSearchHandler;

  @Mock private TenantSpecificConfigReader configUtils;
  @Mock RdcVendorValidator rdcVendorValidator;

  @Mock private RdcItemServiceHandler rdcItemServiceHandler;

  @InjectMocks private RdcInstructionService rdcInstructionService;
  @Mock private AppConfig appConfig;

  private static final String facilityNum = "32818";
  private static final String countryCode = "US";
  private HttpHeaders headers;
  private Container container;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(
        rdcInstructionService, "regulatedItemService", regulatedItemService);
    ReflectionTestUtils.setField(rdcInstructionService, "gson", gson);
    ReflectionTestUtils.setField(
        rdcInstructionService, InstructionService.class, "gson", gson, Gson.class);
    // ReflectionTestUtils.setField(rdcInstructionService, "multiSkuHandler", multiSkuHandler);
    ReflectionTestUtils.setField(
        rdcInstructionService,
        InstructionService.class,
        "deliveryValidator",
        new DeliveryValidator(),
        DeliveryValidator.class);
    ReflectionTestUtils.setField(vendorBasedDeliveryDocumentsSearchHandler, "gson", gson);
    ReflectionTestUtils.setField(scanTypeDeliveryDocumentsSearchHandler, "gson", gson);

    TenantContext.setFacilityCountryCode(countryCode);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    headers = MockHttpHeaders.getHeaders(facilityNum, countryCode);
    headers.add(RdcConstants.WFT_LOCATION_ID, "122");
    headers.add(RdcConstants.WFT_LOCATION_TYPE, "DOOR-122");
    headers.add(RdcConstants.WFT_SCC_CODE, "34332323");
    container = new Container();
    container.setLocation("100");
  }

  @AfterMethod
  public void resetMocks() {
    reset(
        rdcManagedConfig,
        movePublisher,
        rdcInstructionUtils,
        instructionPersisterService,
        instructionRepository,
        nimRdsService,
        rdcFixitProblemService,
        problemReceivingHelper,
        rdcDeliveryService,
        tenantSpecificConfigReader,
        problemService,
        multiSkuService,
        containerService,
        scanTypeDeliveryDocumentsSearchHandler,
        deliveryDocumentHelper,
        rdcDaService,
        rdcReceivingUtils,
        rdcDsdcService,
        regulatedItemService,
        ngrRestApiClient,
        rdcItemServiceHandler);
  }

  @BeforeMethod
  public void initData() {
    when(rdcManagedConfig.getAsnEnabledVendorsList())
        .thenReturn(Arrays.asList("12345", "99999", "39833"));

    doReturn(rdcDeliveryService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_SERVICE_KEY), any(Class.class));
    doReturn(multiSkuService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            facilityNum, ReceivingConstants.ASN_MULTISKU_HANDLER, MultiSkuService.class);
  }

  @Test
  public void testServeInstructionRequestHasDeliveryDocumentsReturnsInstruction()
      throws ReceivingException, IOException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithLimitedQtyCompliance();
    when(rdcInstructionUtils.filterSSTKDeliveryDocuments(anyList()))
        .thenReturn(instructionRequest.getDeliveryDocuments());
    doNothing()
        .when(regulatedItemService)
        .updateVendorComplianceItem(any(VendorCompliance.class), anyString());
    doNothing().when(ngrRestApiClient).updateHazmatVerificationTsInItemCache(any(), any());
    doNothing()
        .when(rdcItemServiceHandler)
        .updateItemRejectReason(
            nullable(RejectReason.class), any(ItemOverrideRequest.class), any(HttpHeaders.class));
    when(rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class)))
        .thenReturn(getInstructionResponseForSingleSSTK());
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(receivedQuantityResponseFromRDS);
    when(rdcInstructionUtils.autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString()))
        .thenReturn(new Pair<>(instructionRequest.getDeliveryDocuments().get(0), 10L));
    when(rdcInstructionUtils.checkAllSSTKPoFulfilled(
            anyList(), any(InstructionRequest.class), any(ReceivedQuantityResponseFromRDS.class)))
        .thenReturn(instructionRequest.getDeliveryDocuments());
    when(rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class)))
        .thenReturn(getInstructionResponseForSingleSSTK());

    InstructionResponse instructionResponse =
        rdcInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), headers);

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(1, instructionResponse.getDeliveryDocuments().size());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.SSTK_UPC_RECEIVING.getInstructionCode());
    assertFalse(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .isLithiumIonVerificationRequired());
    assertFalse(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .isLimitedQtyVerificationRequired());
    assertFalse(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getIsDefaultTiHiUsed());
    verify(rdcInstructionUtils, times(1)).filterSSTKDeliveryDocuments(anyList());
    verify(rdcInstructionUtils, times(1))
        .checkAllSSTKPoFulfilled(
            anyList(), any(InstructionRequest.class), any(ReceivedQuantityResponseFromRDS.class));
    verify(regulatedItemService, times(1))
        .updateVendorComplianceItem(
            eq(instructionRequest.getRegulatedItemType()),
            eq(
                instructionRequest
                    .getDeliveryDocuments()
                    .get(0)
                    .getDeliveryDocumentLines()
                    .get(0)
                    .getItemNbr()
                    .toString()));
    verify(rdcItemServiceHandler, times(1))
        .updateItemRejectReason(
            nullable(RejectReason.class), any(ItemOverrideRequest.class), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1))
        .createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1))
        .autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString());
  }

  @Test
  public void testServeInstructionRequest_WithoutDeliveryDocuments_Returns_Instruction()
      throws ReceivingException, IOException {
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    List<DeliveryDocument> deliveryDocuments =
        MockInstructionRequest.getInstructionRequestWithDeliveryDocuments().getDeliveryDocuments();
    receivedQuantityResponseFromRDS = MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(0L);
    doReturn(vendorBasedDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNum,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(rdcDeliveryService.findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(gson.toJson(deliveryDocuments));
    when(rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(anyList(), any()))
        .thenReturn(deliveryDocuments);
    when(rdcInstructionUtils.filterNonDADeliveryDocuments(anyList(), any(InstructionRequest.class)))
        .thenReturn(deliveryDocuments);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.ITEM_CONFIG_SERVICE_ENABLED_FOR_SSTK,
            false))
        .thenReturn(true);
    doNothing()
        .when(rdcInstructionUtils)
        .validateAtlasConvertedItems(anyList(), any(HttpHeaders.class));
    when(rdcInstructionUtils.filterSSTKDeliveryDocuments(anyList())).thenReturn(deliveryDocuments);
    when(rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class)))
        .thenReturn(getInstructionResponseForSingleSSTK());
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(receivedQuantityResponseFromRDS);
    when(rdcInstructionUtils.autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString()))
        .thenReturn(new Pair<>(deliveryDocuments.get(0), 10L));

    InstructionResponse instructionResponse =
        rdcInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), headers);

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(1, instructionResponse.getDeliveryDocuments().size());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.SSTK_UPC_RECEIVING.getInstructionCode());
    assertFalse(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .isLithiumIonVerificationRequired());
    assertFalse(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .isLimitedQtyVerificationRequired());

    verify(rdcDeliveryService, times(1))
        .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1)).validateAndProcessGdmDeliveryDocuments(anyList(), any());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.DELIVERY_SERVICE_KEY,
            DeliveryService.class);
    verify(rdcInstructionUtils, times(1)).checkIfDsdcInstructionAlreadyExists(any());
    verify(rdcInstructionUtils, times(2)).filterSSTKDeliveryDocuments(anyList());
    verify(rdcInstructionUtils, times(1))
        .createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1))
        .autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString());
  }

  @Test
  public void testServeInstructionRequestItemConfigServiceDisabled()
      throws ReceivingException, IOException {
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    List<DeliveryDocument> deliveryDocuments =
        MockInstructionRequest.getInstructionRequestWithDeliveryDocuments().getDeliveryDocuments();
    receivedQuantityResponseFromRDS = MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(0L);
    doReturn(vendorBasedDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNum,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(rdcDeliveryService.findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(gson.toJson(deliveryDocuments));
    when(rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(anyList(), any()))
        .thenReturn(deliveryDocuments);
    when(rdcInstructionUtils.filterNonDADeliveryDocuments(anyList(), any(InstructionRequest.class)))
        .thenReturn(deliveryDocuments);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.ITEM_CONFIG_SERVICE_ENABLED_FOR_SSTK,
            false))
        .thenReturn(false);
    when(rdcInstructionUtils.filterSSTKDeliveryDocuments(anyList())).thenReturn(deliveryDocuments);
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());
    when(rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class)))
        .thenReturn(getInstructionResponseForSingleSSTK());
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(receivedQuantityResponseFromRDS);
    when(rdcInstructionUtils.autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString()))
        .thenReturn(new Pair<>(deliveryDocuments.get(0), 10L));

    InstructionResponse instructionResponse =
        rdcInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), headers);

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(1, instructionResponse.getDeliveryDocuments().size());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.SSTK_UPC_RECEIVING.getInstructionCode());
    assertFalse(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .isLithiumIonVerificationRequired());
    assertFalse(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .isLimitedQtyVerificationRequired());

    verify(rdcDeliveryService, times(1))
        .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1)).validateAndProcessGdmDeliveryDocuments(anyList(), any());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.DELIVERY_SERVICE_KEY,
            DeliveryService.class);
    verify(rdcInstructionUtils, times(0))
        .validateAtlasConvertedItems(anyList(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(2)).filterSSTKDeliveryDocuments(anyList());
    verify(rdcInstructionUtils, times(1))
        .createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1))
        .autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString());
  }

  @Test
  public void testServeInstructionRequest_ForProblemTagFIXIT_Returns_Instruction()
      throws ReceivingException, IOException {
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    instructionRequest.setProblemTagId("PROB123");

    GdmPOLineResponse gdmPOLineResponse =
        MockInstructionRequest.getGDMPoLineResponseForPoAndPoLineNbr();
    List<DeliveryDocument> deliveryDocuments = gdmPOLineResponse.getDeliveryDocuments();
    receivedQuantityResponseFromRDS = MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(0L);
    doReturn(rdcFixitProblemService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));

    when(rdcFixitProblemService.getProblemDetails(anyString()))
        .thenReturn(MockProblemResponse.getProlemDetails());
    when(problemReceivingHelper.isContainerReceivable(any(FitProblemTagResponse.class)))
        .thenReturn(true);
    when(rdcDeliveryService.getDeliveryDocumentsByPoAndPoLineFromGDM(
            anyString(), anyString(), anyInt(), any(HttpHeaders.class)))
        .thenReturn(deliveryDocuments);
    when(rdcInstructionUtils.isSameUpc(anyString(), any(DeliveryDocumentLine.class)))
        .thenReturn(true);
    when(rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(anyList(), any()))
        .thenReturn(deliveryDocuments);
    when(rdcInstructionUtils.filterNonDADeliveryDocuments(anyList(), any(InstructionRequest.class)))
        .thenReturn(deliveryDocuments);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.ITEM_CONFIG_SERVICE_ENABLED_FOR_SSTK,
            false))
        .thenReturn(true);
    doNothing()
        .when(rdcInstructionUtils)
        .validateAtlasConvertedItems(anyList(), any(HttpHeaders.class));
    when(rdcInstructionUtils.filterSSTKDeliveryDocuments(anyList())).thenReturn(deliveryDocuments);
    when(rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class)))
        .thenReturn(getInstructionResponseForSingleSSTK());
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(receivedQuantityResponseFromRDS);
    when(rdcInstructionUtils.autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString()))
        .thenReturn(new Pair<>(deliveryDocuments.get(0), 10L));

    InstructionResponse instructionResponse =
        rdcInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), headers);

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(1, instructionResponse.getDeliveryDocuments().size());
    assertNotNull(instructionResponse.getDeliveryDocuments().get(0).getDeliveryStatus());
    assertEquals(
        instructionResponse.getDeliveryDocuments().get(0).getDeliveryStatus().toString(),
        gdmPOLineResponse.getDeliveryStatus());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.SSTK_UPC_RECEIVING.getInstructionCode());
    assertFalse(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .isLithiumIonVerificationRequired());
    assertFalse(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .isLimitedQtyVerificationRequired());

    verify(rdcFixitProblemService, times(1)).getProblemDetails(anyString());
    verify(problemReceivingHelper, times(1))
        .isContainerReceivable(any(FitProblemTagResponse.class));
    verify(rdcDeliveryService, times(1))
        .getDeliveryDocumentsByPoAndPoLineFromGDM(
            anyString(), anyString(), anyInt(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1)).isSameUpc(anyString(), any(DeliveryDocumentLine.class));
    verify(rdcInstructionUtils, times(1)).validateAndProcessGdmDeliveryDocuments(anyList(), any());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.PROBLEM_SERVICE,
            ProblemService.class);
    verify(rdcInstructionUtils, times(1))
        .checkIfInstructionExistsWithSsccAndValidateInstruction(any(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(2)).filterSSTKDeliveryDocuments(anyList());
    verify(rdcInstructionUtils, times(1))
        .createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    verify(rdcInstructionUtils, times(1))
        .autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString());
  }

  @Test
  public void testServeInstructionRequest_ForProblemTagFIT_Returns_Instruction()
      throws ReceivingException, IOException {
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    instructionRequest.setProblemTagId("PROB123");

    GdmPOLineResponse gdmPOLineResponse =
        MockInstructionRequest.getGDMPoLineResponseForPoAndPoLineNbr();
    List<DeliveryDocument> deliveryDocuments = gdmPOLineResponse.getDeliveryDocuments();
    receivedQuantityResponseFromRDS = MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(0L);
    doReturn(problemService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));

    when(problemService.getProblemDetails(anyString()))
        .thenReturn(MockProblemResponse.getProlemDetails());
    when(problemReceivingHelper.isContainerReceivable(any(FitProblemTagResponse.class)))
        .thenReturn(true);
    when(rdcDeliveryService.getDeliveryDocumentsByPoAndPoLineFromGDM(
            anyString(), anyString(), anyInt(), any(HttpHeaders.class)))
        .thenReturn(deliveryDocuments);
    when(rdcInstructionUtils.isSameUpc(anyString(), any(DeliveryDocumentLine.class)))
        .thenReturn(true);
    when(rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(anyList(), any()))
        .thenReturn(deliveryDocuments);
    when(rdcInstructionUtils.filterNonDADeliveryDocuments(anyList(), any(InstructionRequest.class)))
        .thenReturn(deliveryDocuments);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.ITEM_CONFIG_SERVICE_ENABLED_FOR_SSTK,
            false))
        .thenReturn(true);
    doNothing()
        .when(rdcInstructionUtils)
        .validateAtlasConvertedItems(anyList(), any(HttpHeaders.class));
    when(rdcInstructionUtils.filterSSTKDeliveryDocuments(anyList())).thenReturn(deliveryDocuments);
    when(rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class)))
        .thenReturn(getInstructionResponseForSingleSSTK());
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(receivedQuantityResponseFromRDS);
    when(rdcInstructionUtils.autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString()))
        .thenReturn(new Pair<>(deliveryDocuments.get(0), 10L));

    InstructionResponse instructionResponse =
        rdcInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), headers);

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(1, instructionResponse.getDeliveryDocuments().size());
    assertNotNull(instructionResponse.getDeliveryDocuments().get(0).getDeliveryStatus());
    assertEquals(
        instructionResponse.getDeliveryDocuments().get(0).getDeliveryStatus().toString(),
        gdmPOLineResponse.getDeliveryStatus());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.SSTK_UPC_RECEIVING.getInstructionCode());
    assertFalse(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .isLithiumIonVerificationRequired());
    assertFalse(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .isLimitedQtyVerificationRequired());

    verify(problemService, times(1)).getProblemDetails(anyString());
    verify(problemReceivingHelper, times(1))
        .isContainerReceivable(any(FitProblemTagResponse.class));
    verify(rdcDeliveryService, times(1))
        .getDeliveryDocumentsByPoAndPoLineFromGDM(
            anyString(), anyString(), anyInt(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1)).isSameUpc(anyString(), any(DeliveryDocumentLine.class));
    verify(rdcInstructionUtils, times(1)).validateAndProcessGdmDeliveryDocuments(anyList(), any());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.PROBLEM_SERVICE,
            ProblemService.class);
    verify(rdcInstructionUtils, times(1)).checkIfDsdcInstructionAlreadyExists(any());
    verify(rdcInstructionUtils, times(2)).filterSSTKDeliveryDocuments(anyList());
    verify(rdcInstructionUtils, times(1))
        .createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    verify(rdcInstructionUtils, times(1))
        .autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString());
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void
      testServeInstructionRequest_ForProblemTag_ReturnsError_WhenProblemIsNotInReceivableState()
          throws ReceivingException, IOException {
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    instructionRequest.setProblemTagId("PROB123");
    List<DeliveryDocument> deliveryDocuments =
        MockInstructionRequest.getInstructionRequestWithDeliveryDocuments().getDeliveryDocuments();
    GdmPOLineResponse gdmPOLineResponse = new GdmPOLineResponse();
    gdmPOLineResponse.setDeliveryDocuments(deliveryDocuments);

    doReturn(rdcFixitProblemService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    when(rdcFixitProblemService.getProblemDetails(anyString()))
        .thenReturn(MockProblemResponse.getProlemDetails());
    when(problemReceivingHelper.isContainerReceivable(any(FitProblemTagResponse.class)))
        .thenReturn(false);
    when(rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(anyList(), any()))
        .thenReturn(deliveryDocuments);
    when(rdcInstructionUtils.filterNonDADeliveryDocuments(anyList(), any(InstructionRequest.class)))
        .thenReturn(deliveryDocuments);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.ITEM_CONFIG_SERVICE_ENABLED_FOR_SSTK,
            false))
        .thenReturn(true);
    doNothing()
        .when(rdcInstructionUtils)
        .validateAtlasConvertedItems(anyList(), any(HttpHeaders.class));
    when(rdcInstructionUtils.filterSSTKDeliveryDocuments(anyList())).thenReturn(deliveryDocuments);
    when(rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class)))
        .thenReturn(getInstructionResponseForSingleSSTK());
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(receivedQuantityResponseFromRDS);
    when(rdcInstructionUtils.autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString()))
        .thenReturn(new Pair<>(deliveryDocuments.get(0), 10L));

    rdcInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), headers);

    verify(rdcFixitProblemService, times(1)).getProblemDetails(anyString());
    verify(problemReceivingHelper, times(1))
        .isContainerReceivable(any(FitProblemTagResponse.class));
    verify(rdcInstructionUtils, times(1)).validateAndProcessGdmDeliveryDocuments(anyList(), any());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.ITEM_CONFIG_SERVICE_ENABLED_FOR_SSTK,
            false);
    verify(rdcInstructionUtils, times(1))
        .validateAtlasConvertedItems(anyList(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1)).filterSSTKDeliveryDocuments(anyList());
    verify(rdcInstructionUtils, times(1))
        .createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    verify(rdcInstructionUtils, times(1))
        .autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString());
  }

  @Test
  public void testServeInstructionRequest_ForProblemTagFIXIT_ReturnsError_WhenUpcIsDifferent()
      throws ReceivingException, IOException {
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    instructionRequest.setProblemTagId("PROB123");

    GdmPOLineResponse gdmPOLineResponse =
        MockInstructionRequest.getGDMPoLineResponseForPoAndPoLineNbr();
    List<DeliveryDocument> deliveryDocuments = gdmPOLineResponse.getDeliveryDocuments();

    doReturn(rdcFixitProblemService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));

    when(rdcFixitProblemService.getProblemDetails(anyString()))
        .thenReturn(MockProblemResponse.getProlemDetails());
    when(problemReceivingHelper.isContainerReceivable(any(FitProblemTagResponse.class)))
        .thenReturn(true);
    when(rdcDeliveryService.getDeliveryDocumentsByPoAndPoLineFromGDM(
            anyString(), anyString(), anyInt(), any(HttpHeaders.class)))
        .thenReturn(deliveryDocuments);
    when(rdcInstructionUtils.isSameUpc(anyString(), any(DeliveryDocumentLine.class)))
        .thenReturn(false);
    try {
      rdcInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), headers);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), "searchDocument");
      assertEquals(e.getDescription(), "No PO/POLine information found.");
    }
    verify(rdcFixitProblemService, times(1)).getProblemDetails(anyString());
    verify(problemReceivingHelper, times(1))
        .isContainerReceivable(any(FitProblemTagResponse.class));
    verify(rdcDeliveryService, times(1))
        .getDeliveryDocumentsByPoAndPoLineFromGDM(
            anyString(), anyString(), anyInt(), any(HttpHeaders.class));
  }

  @Test
  public void
      testServeInstructionRequest_WithDeliveryDocuments_AndWithOut_RegulatedItemType_Returns_Instruction()
          throws ReceivingException, IOException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithLimitedQtyCompliance();
    instructionRequest.setRegulatedItemType(null);

    when(rdcInstructionUtils.filterSSTKDeliveryDocuments(anyList()))
        .thenReturn(instructionRequest.getDeliveryDocuments());
    when(rdcInstructionUtils.checkAllSSTKPoFulfilled(
            anyList(), any(InstructionRequest.class), any(ReceivedQuantityResponseFromRDS.class)))
        .thenReturn(instructionRequest.getDeliveryDocuments());
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());
    when(rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class)))
        .thenReturn(getInstructionResponseForSingleSSTK());
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(receivedQuantityResponseFromRDS);
    when(rdcInstructionUtils.autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString()))
        .thenReturn(new Pair<>(instructionRequest.getDeliveryDocuments().get(0), 10L));

    InstructionResponse instructionResponse =
        rdcInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), headers);

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(1, instructionResponse.getDeliveryDocuments().size());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.SSTK_UPC_RECEIVING.getInstructionCode());
    assertFalse(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .isLithiumIonVerificationRequired());
    assertFalse(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .isLimitedQtyVerificationRequired());

    verify(rdcInstructionUtils, times(1)).filterSSTKDeliveryDocuments(anyList());
    verify(rdcInstructionUtils, times(1))
        .checkAllSSTKPoFulfilled(
            anyList(), any(InstructionRequest.class), any(ReceivedQuantityResponseFromRDS.class));
    verify(rdcInstructionUtils, times(1))
        .createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1))
        .autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString());
  }

  @Test
  public void
      testServeInstructionRequestForSinglePoReturnsNoInstructionDueToLimitedQtyComplianceError()
          throws ReceivingException, IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockInstructionRequest.getInstructionRequestWithLimitedQtyCompliance()
            .getDeliveryDocuments();
    InstructionResponse mockInstructionResponse = new InstructionResponseImplNew();
    receivedQuantityResponseFromRDS =
        MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(10L);
    mockInstructionResponse.setDeliveryDocuments(deliveryDocumentList);
    deliveryDocumentList
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setTransportationModes(MockInstructionRequest.getMockTransportationModeForLimitedQty());
    doReturn(vendorBasedDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNum,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(rdcDeliveryService.findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(gson.toJson(deliveryDocumentList));
    when(rdcInstructionUtils.isSinglePoAndPoLine(anyList())).thenReturn(true);
    when(rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(anyList(), any()))
        .thenReturn(deliveryDocumentList);
    when(rdcInstructionUtils.filterNonDADeliveryDocuments(anyList(), any(InstructionRequest.class)))
        .thenReturn(deliveryDocumentList);
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(mockInstructionResponse);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.ITEM_CONFIG_SERVICE_ENABLED_FOR_SSTK,
            false))
        .thenReturn(true);
    doNothing()
        .when(rdcInstructionUtils)
        .validateAtlasConvertedItems(anyList(), any(HttpHeaders.class));
    when(rdcInstructionUtils.filterSSTKDeliveryDocuments(anyList()))
        .thenReturn(deliveryDocumentList);
    when(deliveryDocumentHelper.updateVendorCompliance(any(DeliveryDocumentLine.class)))
        .thenReturn(Boolean.TRUE);
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(receivedQuantityResponseFromRDS);
    when(rdcInstructionUtils.autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString()))
        .thenReturn(new Pair<>(deliveryDocumentList.get(0), 10L));
    when(rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class)))
        .thenReturn(mockInstructionResponse);

    InstructionResponse instructionResponse =
        rdcInstructionService.serveInstructionRequest(
            gson.toJson(MockInstructionRequest.getInstructionRequest()), headers);

    assertNotNull(instructionResponse);
    assertNull(instructionResponse.getInstruction());
    assertEquals(1, instructionResponse.getDeliveryDocuments().size());

    verify(rdcDeliveryService, times(1))
        .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1)).validateAndProcessGdmDeliveryDocuments(anyList(), any());
    when(rdcInstructionUtils.enableAtlasConvertedItemValidationForSSTKReceiving()).thenReturn(true);
    doNothing().when(rdcInstructionUtils).validateAtlasConvertedItems(any(), any());
    verify(rdcInstructionUtils, times(2)).filterSSTKDeliveryDocuments(anyList());
    verify(rdcInstructionUtils, times(1)).isSinglePoAndPoLine(anyList());
    verify(rdcInstructionUtils, times(1))
        .filterNonDADeliveryDocuments(anyList(), any(InstructionRequest.class));
    verify(rdcInstructionUtils, times(1))
        .autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString());
    verify(rdcInstructionUtils, times(1))
        .createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class));
  }

  @Test
  public void
      testServeInstructionRequestForSinglePoReturnsNoInstructionDueToLithiumIonComplianceError()
          throws ReceivingException, IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockInstructionRequest.getInstructionRequestWithLithiumIonCompliance()
            .getDeliveryDocuments();
    InstructionResponse mockInstructionResponse = new InstructionResponseImplNew();
    mockInstructionResponse.setDeliveryDocuments(deliveryDocumentList);
    deliveryDocumentList
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setTransportationModes(MockInstructionRequest.getMockTransportationModeForLithiumIon());
    doReturn(vendorBasedDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNum,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(rdcDeliveryService.findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(gson.toJson(deliveryDocumentList));
    when(rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(anyList(), any()))
        .thenReturn(deliveryDocumentList);
    when(rdcInstructionUtils.filterNonDADeliveryDocuments(anyList(), any(InstructionRequest.class)))
        .thenReturn(deliveryDocumentList);
    when(rdcInstructionUtils.isSinglePoAndPoLine(anyList())).thenReturn(true);
    when(rdcInstructionUtils.filterSSTKDeliveryDocuments(anyList()))
        .thenReturn(deliveryDocumentList);
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(mockInstructionResponse);
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(receivedQuantityResponseFromRDS);
    when(rdcInstructionUtils.autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString()))
        .thenReturn(new Pair<>(deliveryDocumentList.get(0), 10L));
    when(rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class)))
        .thenReturn(mockInstructionResponse);

    InstructionResponse instructionResponse =
        rdcInstructionService.serveInstructionRequest(
            gson.toJson(MockInstructionRequest.getInstructionRequest()), headers);

    assertNotNull(instructionResponse);
    assertNull(instructionResponse.getInstruction());
    assertEquals(1, instructionResponse.getDeliveryDocuments().size());

    verify(rdcDeliveryService, times(1))
        .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1)).validateAndProcessGdmDeliveryDocuments(anyList(), any());
    verify(rdcInstructionUtils, times(2)).filterSSTKDeliveryDocuments(anyList());
    verify(rdcInstructionUtils, times(1)).isSinglePoAndPoLine(anyList());
    verify(rdcInstructionUtils, times(1))
        .filterNonDADeliveryDocuments(anyList(), any(InstructionRequest.class));
    verify(rdcInstructionUtils, times(1))
        .autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString());
    verify(rdcInstructionUtils, times(1))
        .createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class));
  }

  @Test
  public void testServeInstructionRequest_DA_And_SSTK_items()
      throws ReceivingException, IOException {
    File resource = new ClassPathResource("GdmMappedResponseV2_SSTK_DA_Items.json").getFile();
    String mockDeliveryDocumentsResponse = new String(Files.readAllBytes(resource.toPath()));
    receivedQuantityResponseFromRDS = MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(0L);
    List<DeliveryDocument> deliveryDocumentList =
        gson.fromJson(
            mockDeliveryDocumentsResponse, new TypeToken<List<DeliveryDocument>>() {}.getType());
    when(rdcDeliveryService.findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(mockDeliveryDocumentsResponse);
    when(rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(anyList(), any()))
        .thenReturn(deliveryDocumentList);
    when(rdcInstructionUtils.filterNonDADeliveryDocuments(anyList(), any(InstructionRequest.class)))
        .thenReturn(deliveryDocumentList);
    when(rdcInstructionUtils.filterSSTKDeliveryDocuments(anyList()))
        .thenReturn(Collections.singletonList(deliveryDocumentList.get(0)));
    when(rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class)))
        .thenReturn(getInstructionResponseForSingleSSTK());
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(receivedQuantityResponseFromRDS);
    doReturn(vendorBasedDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNum,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(rdcInstructionUtils.autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString()))
        .thenReturn(new Pair<>(deliveryDocumentList.get(0), 10L));
    when(rdcInstructionUtils.checkAllSSTKPoFulfilled(
            anyList(), any(InstructionRequest.class), any(ReceivedQuantityResponseFromRDS.class)))
        .thenReturn(deliveryDocumentList);

    InstructionResponse instructionResponse =
        rdcInstructionService.serveInstructionRequest(
            gson.toJson(MockInstructionRequest.getInstructionRequest()), headers);

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(1, instructionResponse.getDeliveryDocuments().size());

    verify(rdcDeliveryService, times(1))
        .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1)).validateAndProcessGdmDeliveryDocuments(anyList(), any());
    verify(rdcInstructionUtils, times(2)).filterSSTKDeliveryDocuments(anyList());
    verify(rdcInstructionUtils, times(1))
        .createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1))
        .autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testServeInstructionRequest_ThrowsException_ForNotSupportedChannelMethods()
      throws ReceivingException, IOException {
    File resource = new ClassPathResource("GdmMappedResponseV2_Single_DSDC_Item.json").getFile();
    String mockDeliveryDocumentsResponse = new String(Files.readAllBytes(resource.toPath()));
    receivedQuantityResponseFromRDS =
        MockReceivedQtyRespFromRds.getReceivedQtyMapForOnePoWithOneLineDA();
    List<DeliveryDocument> deliveryDocumentList =
        gson.fromJson(
            mockDeliveryDocumentsResponse, new TypeToken<List<DeliveryDocument>>() {}.getType());
    doReturn(vendorBasedDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNum,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(rdcDeliveryService.findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(mockDeliveryDocumentsResponse);
    when(rdcInstructionUtils.filterSSTKDeliveryDocuments(anyList())).thenReturn(null);
    when(rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(anyList(), any()))
        .thenReturn(deliveryDocumentList);
    when(rdcInstructionUtils.filterNonDADeliveryDocuments(anyList(), any(InstructionRequest.class)))
        .thenReturn(deliveryDocumentList);
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(receivedQuantityResponseFromRDS);
    when(rdcReceivingUtils.checkAllPOsFulfilled(
            any(InstructionRequest.class), any(ReceivedQuantityResponseFromRDS.class)))
        .thenReturn(new Pair<>(Boolean.FALSE, deliveryDocumentList));
    when(rdcInstructionUtils.autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString()))
        .thenReturn(new Pair<>(deliveryDocumentList.get(0), 10L));

    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.add(RdcConstants.DA_RECEIVING_CAPABILITY, "true");
    rdcInstructionService.serveInstructionRequest(
        gson.toJson(MockInstructionRequest.getInstructionRequest()), httpHeaders);
    verify(rdcDeliveryService, times(1))
        .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1)).validateAndProcessGdmDeliveryDocuments(anyList(), any());
    verify(rdcInstructionUtils, times(1)).filterSSTKDeliveryDocuments(anyList());
    verify(
        rdcInstructionUtils.autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString()));
  }

  @Test
  public void testServeInstructionRequest_Multiple_SSTK_items()
      throws ReceivingException, IOException {
    File resource = new ClassPathResource("GdmMappedResponseV2_ListOfSSTK_Items.json").getFile();
    String mockDeliveryDocumentsResponse = new String(Files.readAllBytes(resource.toPath()));

    List<DeliveryDocument> deliveryDocumentList =
        gson.fromJson(
            mockDeliveryDocumentsResponse, new TypeToken<List<DeliveryDocument>>() {}.getType());

    when(rdcDeliveryService.findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(mockDeliveryDocumentsResponse);
    when(rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(anyList(), any()))
        .thenReturn(deliveryDocumentList);
    when(rdcInstructionUtils.filterNonDADeliveryDocuments(anyList(), any(InstructionRequest.class)))
        .thenReturn(deliveryDocumentList);
    when(rdcInstructionUtils.filterSSTKDeliveryDocuments(anyList()))
        .thenReturn(deliveryDocumentList);
    when(rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class)))
        .thenReturn(getInstructionResponseForSingleSSTK());
    doReturn(vendorBasedDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNum,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(receivedQuantityResponseFromRDS);
    when(rdcInstructionUtils.autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString()))
        .thenReturn(new Pair<>(deliveryDocumentList.get(0), 10L));

    InstructionResponse instructionResponse =
        rdcInstructionService.serveInstructionRequest(
            gson.toJson(MockInstructionRequest.getInstructionRequest()), headers);

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(1, instructionResponse.getDeliveryDocuments().size());

    verify(rdcDeliveryService, times(1))
        .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1)).validateAndProcessGdmDeliveryDocuments(anyList(), any());
    verify(rdcInstructionUtils, times(2)).filterSSTKDeliveryDocuments(anyList());
    verify(rdcInstructionUtils, times(1))
        .createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1))
        .autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString());
  }

  @Test
  public void
      testServeInstructionRequest_Multiple_SSTK_PO_has_different_items_returns_only_deliveryDocuments_whenFeatureIsEnabled()
          throws ReceivingException, IOException {
    File resource = new ClassPathResource("GdmMappedResponseV2_ListOfSSTK_Items.json").getFile();
    String mockDeliveryDocumentsResponse = new String(Files.readAllBytes(resource.toPath()));

    when(rdcDeliveryService.findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(mockDeliveryDocumentsResponse);
    when(rdcInstructionUtils.hasMoreUniqueItems(anyList())).thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_FREIGHT_IDENTIFICATION_FEATURE_ENABLED,
            false))
        .thenReturn(true);
    doNothing().when(nimRdsService).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    doNothing()
        .when(rdcInstructionUtils)
        .populateOpenAndReceivedQtyInDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString());

    InstructionResponse instructionResponse =
        rdcInstructionService.serveInstructionRequest(
            gson.toJson(MockInstructionRequest.getInstructionRequest()), headers);

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getDeliveryDocuments());
    assertNull(instructionResponse.getInstruction());

    verify(rdcDeliveryService, times(1))
        .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1)).validateAndProcessGdmDeliveryDocuments(anyList(), any());
    verify(rdcInstructionUtils, times(1)).hasMoreUniqueItems(anyList());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_FREIGHT_IDENTIFICATION_FEATURE_ENABLED,
            false);
    verify(nimRdsService, times(1)).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1))
        .populateOpenAndReceivedQtyInDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString());
  }

  @Test
  public void
      testServeInstructionRequest_Multiple_SSTK_PO_has_different_items_returns_only_deliveryDocumentsAndDummyInstruction_SsccIsScanned()
          throws ReceivingException, IOException {
    File resource =
        new ClassPathResource("GdmMappedPackResponseToDeliveryDocumentLinesMultiSku.json")
            .getFile();
    String mockDeliveryDocumentsResponse = new String(Files.readAllBytes(resource.toPath()));

    when(rdcDeliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class)))
        .thenReturn(
            Arrays.asList(gson.fromJson(mockDeliveryDocumentsResponse, DeliveryDocument[].class)));
    when(rdcManagedConfig.isAsnReceivingEnabled()).thenReturn(true);
    when(rdcInstructionUtils.hasMoreUniqueItems(anyList())).thenReturn(true);
    when(rdcManagedConfig.getInternalAsnSourceTypes()).thenReturn(new ArrayList<>());
    when(rdcVendorValidator.isAsnReceivingEnabled()).thenReturn(true);
    when(rdcVendorValidator.isPilotVendorForAsnReceiving(anyString())).thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_FREIGHT_IDENTIFICATION_FEATURE_ENABLED,
            false))
        .thenReturn(false);
    doReturn(vendorBasedDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNum,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ASN_MULTI_SKU_ENABLED,
            false))
        .thenReturn(true);
    doNothing().when(nimRdsService).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    doNothing()
        .when(rdcInstructionUtils)
        .populateOpenAndReceivedQtyInDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString());
    doReturn(rdcVendorValidator)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            facilityNum, ReceivingConstants.VENDOR_VALIDATOR, VendorValidator.class);
    doReturn(multiSkuService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            facilityNum, ReceivingConstants.ASN_MULTISKU_HANDLER, MultiSkuService.class);

    when(asnReceivingAuditLogger.isVendorEnabledForAsnReceiving(
            any(DeliveryDocument.class), any(InstructionRequest.class)))
        .thenReturn(true);
    when(rdcDsdcService.createInstructionForDSDCReceiving(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(new InstructionResponseImplNew());
    InstructionResponse multiSkuResponse = new InstructionResponseImplNew();
    Instruction dummyInstruction = new Instruction();
    dummyInstruction.setInstructionCode(ReceivingConstants.MULTI_SKU_INST_CODE);
    dummyInstruction.setInstructionMsg(ReceivingConstants.MULTI_SKU_INST_MSG);
    multiSkuResponse.setInstruction(dummyInstruction);

    InstructionResponse instructionResponse =
        rdcInstructionService.serveInstructionRequest(
            gson.toJson(MockInstructionRequest.getSSCCInstructionRequest()), headers);

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getDeliveryDocuments());

    verify(rdcDeliveryService, times(1))
        .findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1)).validateAndProcessGdmDeliveryDocuments(anyList(), any());
    verify(rdcInstructionUtils, times(1)).hasMoreUniqueItems(anyList());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_FREIGHT_IDENTIFICATION_FEATURE_ENABLED,
            false);
    verify(nimRdsService, times(1)).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1))
        .populateOpenAndReceivedQtyInDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString());
    verify(multiSkuService, times(1))
        .handleMultiSku(
            anyBoolean(),
            any(InstructionRequest.class),
            any(InstructionResponse.class),
            any(Instruction.class));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "Scanned label is multi item Pallet. Sort and receive by each Item")
  public void
      testServeInstructionRequest_Multiple_SSTK_PO_has_different_items_MultiSkuDisable_ThrowException_SsccIsScanned()
          throws ReceivingException, IOException {
    File resource =
        new ClassPathResource("GdmMappedPackResponseToDeliveryDocumentLinesMultiSku.json")
            .getFile();
    String mockDeliveryDocumentsResponse = new String(Files.readAllBytes(resource.toPath()));

    when(rdcDeliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class)))
        .thenReturn(
            Arrays.asList(gson.fromJson(mockDeliveryDocumentsResponse, DeliveryDocument[].class)));
    when(rdcManagedConfig.isAsnReceivingEnabled()).thenReturn(true);
    when(rdcInstructionUtils.hasMoreUniqueItems(anyList())).thenReturn(true);
    when(rdcManagedConfig.getInternalAsnSourceTypes()).thenReturn(new ArrayList<>());
    when(rdcDsdcService.createInstructionForDSDCReceiving(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_FREIGHT_IDENTIFICATION_FEATURE_ENABLED,
            false))
        .thenReturn(true);
    doReturn(vendorBasedDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNum,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    doReturn(rdcVendorValidator)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            facilityNum, ReceivingConstants.VENDOR_VALIDATOR, VendorValidator.class);
    when(asnReceivingAuditLogger.isVendorEnabledForAsnReceiving(
            any(DeliveryDocument.class), any(InstructionRequest.class)))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ASN_MULTI_SKU_ENABLED,
            false))
        .thenReturn(false);
    when(rdcVendorValidator.isAsnReceivingEnabled()).thenReturn(true);
    when(rdcVendorValidator.isPilotVendorForAsnReceiving(anyString())).thenReturn(true);
    doNothing().when(nimRdsService).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    doNothing()
        .when(rdcInstructionUtils)
        .populateOpenAndReceivedQtyInDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString());
    doReturn(multiSkuService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            facilityNum, ReceivingConstants.ASN_MULTISKU_HANDLER, MultiSkuService.class);
    when(multiSkuService.handleMultiSku(
            anyBoolean(),
            any(InstructionRequest.class),
            any(InstructionResponse.class),
            any(Instruction.class)))
        .thenThrow(
            new ReceivingBadDataException(
                ReceivingConstants.MULTI_SKU_PALLET, ReceivingConstants.MULTI_SKU_PALLET));

    when(rdcDsdcService.createInstructionForDSDCReceiving(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(new InstructionResponseImplNew());
    InstructionResponse instructionResponse =
        rdcInstructionService.serveInstructionRequest(
            gson.toJson(MockInstructionRequest.getSSCCInstructionRequest()), headers);
    tenantSpecificConfigReader.getConfiguredInstance(
        TenantContext.getFacilityNum().toString(),
        ReceivingConstants.VENDOR_VALIDATOR,
        VendorValidator.class);

    assertNotNull(instructionResponse.getDeliveryDocuments());
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        ReceivingConstants.MULTI_SKU_INST_CODE);

    verify(rdcDeliveryService, times(1))
        .findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1)).validateAndProcessGdmDeliveryDocuments(anyList(), any());
    verify(rdcInstructionUtils, times(1)).hasMoreUniqueItems(anyList());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_FREIGHT_IDENTIFICATION_FEATURE_ENABLED,
            false);
    verify(nimRdsService, times(1)).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1))
        .populateOpenAndReceivedQtyInDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString());
    verify(multiSkuService, times(1))
        .handleMultiSku(
            anyBoolean(),
            any(InstructionRequest.class),
            any(InstructionResponse.class),
            any(Instruction.class));
    verify(rdcDsdcService, times(1))
        .createInstructionForDSDCReceiving(any(InstructionRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testServeInstructionRequest_FreightIdentificationEnabled_IQSIntegrationEnabled()
      throws ReceivingException, IOException {
    File resource = new ClassPathResource("GdmMappedResponseV2_ListOfSSTK_Items.json").getFile();
    String mockDeliveryDocumentsResponse = new String(Files.readAllBytes(resource.toPath()));

    when(rdcDeliveryService.findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(mockDeliveryDocumentsResponse);
    when(rdcInstructionUtils.hasMoreUniqueItems(anyList())).thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_FREIGHT_IDENTIFICATION_FEATURE_ENABLED,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    doNothing().when(rdcInstructionUtils).updateAdditionalItemDetailsFromGDM(anyList());
    doNothing().when(nimRdsService).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    doNothing()
        .when(rdcInstructionUtils)
        .populateOpenAndReceivedQtyInDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString());

    InstructionResponse instructionResponse =
        rdcInstructionService.serveInstructionRequest(
            gson.toJson(MockInstructionRequest.getInstructionRequest()), headers);

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getDeliveryDocuments());
    assertNull(instructionResponse.getInstruction());

    verify(rdcDeliveryService, times(1))
        .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1)).validateAndProcessGdmDeliveryDocuments(anyList(), any());
    verify(rdcInstructionUtils, times(1)).hasMoreUniqueItems(anyList());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_FREIGHT_IDENTIFICATION_FEATURE_ENABLED,
            false);
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
            false);
    verify(nimRdsService, times(0)).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1))
        .populateOpenAndReceivedQtyInDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString());
  }

  @Test
  public void testServeInstructionRequest_FreightIdentificationEnabled_IQSIntegrationDisabled()
      throws ReceivingException, IOException {
    File resource = new ClassPathResource("GdmMappedResponseV2_ListOfSSTK_Items.json").getFile();
    String mockDeliveryDocumentsResponse = new String(Files.readAllBytes(resource.toPath()));

    when(rdcDeliveryService.findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(mockDeliveryDocumentsResponse);
    when(rdcInstructionUtils.hasMoreUniqueItems(anyList())).thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_FREIGHT_IDENTIFICATION_FEATURE_ENABLED,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);
    doNothing().when(rdcInstructionUtils).updateAdditionalItemDetailsFromGDM(anyList());
    doNothing().when(nimRdsService).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    doNothing()
        .when(rdcInstructionUtils)
        .populateOpenAndReceivedQtyInDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString());

    InstructionResponse instructionResponse =
        rdcInstructionService.serveInstructionRequest(
            gson.toJson(MockInstructionRequest.getInstructionRequest()), headers);

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getDeliveryDocuments());
    assertNull(instructionResponse.getInstruction());

    verify(rdcDeliveryService, times(1))
        .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1)).validateAndProcessGdmDeliveryDocuments(anyList(), any());
    verify(rdcInstructionUtils, times(1)).hasMoreUniqueItems(anyList());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_FREIGHT_IDENTIFICATION_FEATURE_ENABLED,
            false);
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
            false);
    verify(nimRdsService, times(1)).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1))
        .populateOpenAndReceivedQtyInDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void
      testServeInstructionRequest_Multiple_SSTK_PO_has_different_items_returns_exception_whenFeatureIsNotEnabled()
          throws ReceivingException, IOException {
    File resource = new ClassPathResource("GdmMappedResponseV2_ListOfSSTK_Items.json").getFile();
    String mockDeliveryDocumentsResponse = new String(Files.readAllBytes(resource.toPath()));

    when(rdcDeliveryService.findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(mockDeliveryDocumentsResponse);
    when(rdcInstructionUtils.hasMoreUniqueItems(anyList())).thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_FREIGHT_IDENTIFICATION_FEATURE_ENABLED,
            false))
        .thenReturn(false);

    rdcInstructionService.serveInstructionRequest(
        gson.toJson(MockInstructionRequest.getInstructionRequest()), headers);

    verify(rdcDeliveryService, times(1))
        .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1)).validateAndProcessGdmDeliveryDocuments(anyList(), any());
    verify(rdcInstructionUtils, times(1)).hasMoreUniqueItems(anyList());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_FREIGHT_IDENTIFICATION_FEATURE_ENABLED,
            false);
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testServeInstructionRequest_Bad_Request_UPC_NULL()
      throws ReceivingException, IOException {
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    instructionRequest.setUpcNumber(null);
    rdcInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), headers);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testServeInstructionRequestThrowsExceptionWhenAllTheLinesAreCancelledForTheMatchedPO()
      throws IOException, ReceivingException {
    File resource =
        new ClassPathResource("GdmMappedResponseV2_Multi_SSTK_PO_With_All_Cancelled_lines.json")
            .getFile();
    String mockDeliveryDocumentsResponse = new String(Files.readAllBytes(resource.toPath()));
    when(rdcDeliveryService.findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(mockDeliveryDocumentsResponse);
    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.INVALID_PO_PO_LINE_STATUS,
                ReceivingException.PO_POL_CANCELLED_ERROR))
        .when(rdcInstructionUtils)
        .validateAndProcessGdmDeliveryDocuments(anyList(), any());

    rdcInstructionService.serveInstructionRequest(
        gson.toJson(MockInstructionRequest.getInstructionRequest()), headers);

    verify(rdcDeliveryService, times(1))
        .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1)).validateAndProcessGdmDeliveryDocuments(anyList(), any());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testGenerateInstructionThrowsExceptionWhenSSTKPoFulfilledButDAPoExists()
      throws IOException, ReceivingException {
    File resource = new ClassPathResource("GdmMappedResponseV2_SSTK_DA_Items.json").getFile();
    String mockDeliveryDocumentsResponse = new String(Files.readAllBytes(resource.toPath()));

    List<DeliveryDocument> deliveryDocumentList =
        gson.fromJson(
            mockDeliveryDocumentsResponse, new TypeToken<List<DeliveryDocument>>() {}.getType());
    receivedQuantityResponseFromRDS =
        MockReceivedQtyRespFromRds.getReceivedQtyMapForMoreThanOnePo();
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    instructionRequest.setDeliveryDocuments(deliveryDocumentList);
    when(rdcInstructionUtils.isSinglePoAndPoLine(anyList())).thenReturn(false);
    when(rdcInstructionUtils.filterSSTKDeliveryDocuments(anyList()))
        .thenReturn(Collections.singletonList(deliveryDocumentList.get(0)));
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(receivedQuantityResponseFromRDS);
    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.DA_PURCHASE_REF_TYPE, RdcConstants.DA_PURCHASE_REF_TYPE_MSG))
        .when(rdcInstructionUtils)
        .checkAllSSTKPoFulfilled(
            anyList(), any(InstructionRequest.class), any(ReceivedQuantityResponseFromRDS.class));

    rdcInstructionService.generateInstruction(instructionRequest, headers);

    verify(rdcInstructionUtils, times(1)).isSinglePoAndPoLine(anyList());
    verify(rdcInstructionUtils, times(1)).filterSSTKDeliveryDocuments(anyList());
    verify(rdcInstructionUtils, times(1))
        .checkAllSSTKPoFulfilled(
            anyList(), any(InstructionRequest.class), any(ReceivedQuantityResponseFromRDS.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void
      testGenerateInstructionThrowsExceptionWhenMultiPOsWithSSTKAndDaScanned_WorkStationNotSupportedForSSTK()
          throws IOException, ReceivingException {
    File resource = new ClassPathResource("GdmMappedResponseV2_SSTK_DA_Items.json").getFile();
    String mockDeliveryDocumentsResponse = new String(Files.readAllBytes(resource.toPath()));

    List<DeliveryDocument> deliveryDocumentList =
        gson.fromJson(
            mockDeliveryDocumentsResponse, new TypeToken<List<DeliveryDocument>>() {}.getType());
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.add(RdcConstants.DA_RECEIVING_CAPABILITY, "true");
    receivedQuantityResponseFromRDS =
        MockReceivedQtyRespFromRds.getReceivedQtyMapForMoreThanOnePo();
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequest_WorkStation();
    instructionRequest.setDeliveryDocuments(deliveryDocumentList);
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(receivedQuantityResponseFromRDS);
    when(rdcInstructionUtils.autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString()))
        .thenReturn(new Pair<>(deliveryDocumentList.get(0), 10L));
    when(rdcInstructionUtils.isSSTKDocument(any(DeliveryDocument.class))).thenReturn(true);
    when(rdcInstructionUtils.checkAllSSTKPoFulfilled(
            anyList(), any(InstructionRequest.class), any(ReceivedQuantityResponseFromRDS.class)))
        .thenReturn(Collections.singletonList(deliveryDocumentList.get(0)));

    rdcInstructionService.generateInstruction(instructionRequest, httpHeaders);

    verify(rdcInstructionUtils, times(1)).isSinglePoAndPoLine(anyList());
    verify(rdcInstructionUtils, times(1)).filterSSTKDeliveryDocuments(anyList());
    verify(rdcInstructionUtils, times(1))
        .checkAllSSTKPoFulfilled(
            anyList(), any(InstructionRequest.class), any(ReceivedQuantityResponseFromRDS.class));
  }

  public void
      testGenerateInstructionThrowsExceptionWhenMultiPOsWithSSTKAndDaScanned_QytReceivingSupportsSSTK()
          throws IOException, ReceivingException {
    File resource = new ClassPathResource("GdmMappedResponseV2_SSTK_DA_Items.json").getFile();
    String mockDeliveryDocumentsResponse = new String(Files.readAllBytes(resource.toPath()));
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    instructionResponse.setInstruction(new Instruction());
    List<DeliveryDocument> deliveryDocumentList =
        gson.fromJson(
            mockDeliveryDocumentsResponse, new TypeToken<List<DeliveryDocument>>() {}.getType());
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.add(RdcConstants.DA_RECEIVING_CAPABILITY, "true");
    receivedQuantityResponseFromRDS =
        MockReceivedQtyRespFromRds.getReceivedQtyMapForMoreThanOnePo();
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequest_WorkStation();
    instructionRequest.setDeliveryDocuments(deliveryDocumentList);
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(receivedQuantityResponseFromRDS);
    when(rdcInstructionUtils.autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString()))
        .thenReturn(new Pair<>(deliveryDocumentList.get(0), 10L));
    when(rdcInstructionUtils.isSSTKDocument(any(DeliveryDocument.class))).thenReturn(false);
    when(rdcDaService.createInstructionForDACaseReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class)))
        .thenReturn(instructionResponse);
    rdcInstructionService.generateInstruction(instructionRequest, httpHeaders);
    verify(rdcDaService, times(1))
        .createInstructionForDACaseReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class));
  }

  @Test
  public void testGenerateInstructionReturnsInstructionWhenSSTKPoIsPartiallyFulfilledButDAPoExists()
      throws IOException, ReceivingException {
    File resource = new ClassPathResource("GdmMappedResponseV2_SSTK_DA_Items.json").getFile();
    String mockDeliveryDocumentsResponse = new String(Files.readAllBytes(resource.toPath()));

    List<DeliveryDocument> deliveryDocumentList =
        gson.fromJson(
            mockDeliveryDocumentsResponse, new TypeToken<List<DeliveryDocument>>() {}.getType());

    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    instructionRequest.setDeliveryDocuments(deliveryDocumentList);
    receivedQuantityResponseFromRDS =
        MockReceivedQtyRespFromRds.getReceivedQtyMapForMoreThanOnePo();

    when(rdcInstructionUtils.filterSSTKDeliveryDocuments(anyList()))
        .thenReturn(Collections.singletonList(deliveryDocumentList.get(0)));
    when(rdcInstructionUtils.checkAllSSTKPoFulfilled(
            anyList(), any(InstructionRequest.class), any(ReceivedQuantityResponseFromRDS.class)))
        .thenReturn((Collections.singletonList(deliveryDocumentList.get(0))));
    when(rdcInstructionUtils.isSinglePoAndPoLine(anyList())).thenReturn(false);
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(receivedQuantityResponseFromRDS);
    when(rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class)))
        .thenReturn(getInstructionResponseForSingleSSTK());
    when(rdcInstructionUtils.autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString()))
        .thenReturn(new Pair<>(deliveryDocumentList.get(0), 10L));
    InstructionResponse instructionResponse =
        rdcInstructionService.generateInstruction(instructionRequest, headers);

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(1, instructionResponse.getDeliveryDocuments().size());

    verify(rdcInstructionUtils, times(1)).filterSSTKDeliveryDocuments(anyList());
    verify(rdcInstructionUtils, times(1))
        .checkAllSSTKPoFulfilled(
            anyList(), any(InstructionRequest.class), any(ReceivedQuantityResponseFromRDS.class));
    verify(rdcInstructionUtils, times(1))
        .createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1)).isSinglePoAndPoLine(anyList());
    verify(rdcInstructionUtils, times(1))
        .autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString());
  }

  @Test
  public void testGenerateInstructionReturnsInstruction_SmartSlottingEnabled_AtlasConvertedItems()
      throws IOException, ReceivingException {

    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK_AtlasConvertedItem();
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    instructionRequest.setDeliveryDocuments(deliveryDocumentList);
    receivedQuantityResponseFromRDS = MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(0L);
    when(rdcInstructionUtils.isSinglePoAndPoLine(anyList())).thenReturn(true);
    when(rdcInstructionUtils.isProblemTagValidationApplicable(any())).thenReturn(true);
    when(rdcInstructionUtils.filterSSTKDeliveryDocuments(anyList()))
        .thenReturn(Collections.singletonList(deliveryDocumentList.get(0)));
    when(rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class)))
        .thenReturn(getInstructionResponseForSingleSSTK_AtlasConvertedItem());
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(receivedQuantityResponseFromRDS);
    when(rdcInstructionUtils.autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString()))
        .thenReturn(new Pair<>(deliveryDocumentList.get(0), 10L));
    InstructionResponse instructionResponse =
        rdcInstructionService.generateInstruction(instructionRequest, headers);

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(1, instructionResponse.getDeliveryDocuments().size());
    assertNotNull(
        instructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0));
    assertTrue(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .isAtlasConvertedItem());

    verify(rdcInstructionUtils, times(1)).filterSSTKDeliveryDocuments(anyList());
    verify(rdcInstructionUtils, times(1)).isSinglePoAndPoLine(anyList());
    verify(rdcInstructionUtils, times(0))
        .checkAllSSTKPoFulfilled(
            anyList(), any(InstructionRequest.class), any(ReceivedQuantityResponseFromRDS.class));
    verify(rdcInstructionUtils, times(1))
        .createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1))
        .autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString());
  }

  @Test
  public void
      testGenerateInstructionReturnsInstructionMultiplePO_SmartSlottingEnabled_AtlasConvertedItems()
          throws IOException, ReceivingException {

    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForMoreThanOneSSTKPo_AtlasConvertedItems();
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        MockReceivedQtyRespFromRds.getReceivedQtyMapForMoreThanOnePo();
    instructionRequest.setDeliveryDocuments(deliveryDocumentList);
    when(rdcInstructionUtils.isSinglePoAndPoLine(anyList())).thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.ITEM_CONFIG_SERVICE_ENABLED_FOR_SSTK,
            false))
        .thenReturn(true);
    when(rdcInstructionUtils.filterSSTKDeliveryDocuments(anyList()))
        .thenReturn(
            MockDeliveryDocuments.getDeliveryDocumentsForMoreThanOneSSTKPo_AtlasConvertedItems());
    when(rdcInstructionUtils.autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString()))
        .thenReturn(new Pair<>(deliveryDocumentList.get(0), 10L));
    when(rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class)))
        .thenReturn(getInstructionResponseForMultiSSTKPO_AtlasConvertedItems());
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(receivedQuantityResponseFromRDS);
    when(rdcInstructionUtils.checkAllSSTKPoFulfilled(
            anyList(), any(InstructionRequest.class), any(ReceivedQuantityResponseFromRDS.class)))
        .thenReturn(deliveryDocumentList);
    InstructionResponse instructionResponse =
        rdcInstructionService.generateInstruction(instructionRequest, headers);

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(1, instructionResponse.getDeliveryDocuments().size());
    assertNotNull(
        instructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0));
    assertTrue(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .isAtlasConvertedItem());

    verify(rdcInstructionUtils, times(1)).isSinglePoAndPoLine(anyList());
    verify(rdcInstructionUtils, times(1)).filterSSTKDeliveryDocuments(anyList());
    verify(rdcInstructionUtils, times(1))
        .checkAllSSTKPoFulfilled(
            anyList(), any(InstructionRequest.class), any(ReceivedQuantityResponseFromRDS.class));
    verify(rdcInstructionUtils, times(1))
        .createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class));
    verify(nimRdsService, times(1))
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
    verify(rdcInstructionUtils, times(1))
        .autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString());
  }

  @Test
  public void
      testGenerateInstructionReturnsInstructionReturnsExceptionForNewItem_SmartSlottingEnabled_AtlasConvertedItems()
          throws IOException, ReceivingException {

    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK_AtlasConvertedItem();
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    instructionRequest.setDeliveryDocuments(deliveryDocumentList);
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(0L);
    String errorMsg =
        String.format(
            ReceivingException.NEW_ITEM_ERROR_MSG,
            deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0).getItemNbr());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.ITEM_CONFIG_SERVICE_ENABLED_FOR_SSTK,
            false))
        .thenReturn(true);
    when(rdcInstructionUtils.filterSSTKDeliveryDocuments(anyList()))
        .thenReturn(Collections.singletonList(deliveryDocumentList.get(0)));
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(receivedQuantityResponseFromRDS);
    when(rdcInstructionUtils.isSinglePoAndPoLine(anyList())).thenReturn(true);
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(receivedQuantityResponseFromRDS);
    doThrow(
            new ReceivingInternalException(
                ExceptionCodes.NEW_ITEM,
                errorMsg,
                ReceivingException.NEW_ITEM_ERROR_HEADER,
                deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0).getItemNbr()))
        .when(rdcInstructionUtils)
        .isNewItem(any(DeliveryDocumentLine.class));
    when(rdcInstructionUtils.filterSSTKDeliveryDocuments(anyList()))
        .thenReturn(MockDeliveryDocuments.getDeliveryDocumentsForSSTK_AtlasConvertedItem());
    when(rdcInstructionUtils.checkAllSSTKPoFulfilled(
            anyList(), any(InstructionRequest.class), any(ReceivedQuantityResponseFromRDS.class)))
        .thenReturn(MockDeliveryDocuments.getDeliveryDocumentsForSSTK_AtlasConvertedItem());
    when(rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class)))
        .thenReturn(getInstructionResponseForSingleSSTK_AtlasConvertedItem());
    when(rdcInstructionUtils.autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString()))
        .thenReturn(new Pair<>(deliveryDocumentList.get(0), 10L));

    try {
      rdcInstructionService.generateInstruction(instructionRequest, headers);
    } catch (ReceivingInternalException excep) {
      assertNotNull(excep);
      assertSame(excep.getErrorCode(), ExceptionCodes.NEW_ITEM);
      assertTrue(
          excep
              .getDescription()
              .equals(
                  String.format(
                      ReceivingException.NEW_ITEM_ERROR_MSG,
                      deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0).getItemNbr())));
    }

    verify(rdcInstructionUtils, times(1)).isSinglePoAndPoLine(anyList());
    verify(rdcInstructionUtils, times(1)).filterSSTKDeliveryDocuments(anyList());
    verify(rdcInstructionUtils, times(0))
        .checkAllSSTKPoFulfilled(
            anyList(), any(InstructionRequest.class), any(ReceivedQuantityResponseFromRDS.class));
    verify(rdcInstructionUtils, times(1))
        .createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class));
    verify(nimRdsService, times(1))
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
    verify(rdcInstructionUtils, times(1))
        .autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString());
  }

  @Test
  public void
      testGenerateInstructionReturnsInstruction_SmartSlottingEnabled_NonAtlasConvertedItems()
          throws IOException, ReceivingException {

    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    receivedQuantityResponseFromRDS =
        MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(10L);
    instructionRequest.setDeliveryDocuments(deliveryDocumentList);
    when(rdcInstructionUtils.filterSSTKDeliveryDocuments(anyList()))
        .thenReturn(Collections.singletonList(deliveryDocumentList.get(0)));
    when(rdcInstructionUtils.isSinglePoAndPoLine(anyList())).thenReturn(true);
    when(rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class)))
        .thenReturn(getInstructionResponseForSingleSSTK());
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(receivedQuantityResponseFromRDS);
    when(rdcInstructionUtils.autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString()))
        .thenReturn(new Pair<>(deliveryDocumentList.get(0), 10L));
    InstructionResponse instructionResponse =
        rdcInstructionService.generateInstruction(instructionRequest, headers);

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(1, instructionResponse.getDeliveryDocuments().size());
    assertNotNull(
        instructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0));
    assertFalse(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .isAtlasConvertedItem());

    verify(rdcInstructionUtils, times(1)).filterSSTKDeliveryDocuments(anyList());
    verify(rdcInstructionUtils, times(0))
        .checkAllSSTKPoFulfilled(
            anyList(), any(InstructionRequest.class), any(ReceivedQuantityResponseFromRDS.class));
    verify(rdcInstructionUtils, times(1))
        .createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class));
    verify(nimRdsService, times(1))
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
    verify(rdcInstructionUtils, times(1))
        .autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString());
  }

  @Test
  public void testCancelInstructionIsSuccess() throws ReceivingException {
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenAnswer(
            i -> {
              Instruction instruction = getInstructions();
              instruction.setInstructionSetId((Long) i.getArguments()[0]);
              instruction.setReceivedQuantity(10);
              instruction.setContainer(new ContainerDetails());
              return instruction;
            });
    when(rdcInstructionUtils.isCancelInstructionAllowed(any(Instruction.class), anyString()))
        .thenReturn(true);
    when(rdcInstructionUtils.isAtlasConvertedInstruction(any(Instruction.class))).thenReturn(true);
    doAnswer(
            (Answer<List>)
                invocation -> {
                  Long instructionId = (Long) invocation.getArguments()[0];

                  Container mockContainer = new Container();
                  mockContainer.setTrackingId("MOCK_TRACKING_ID_" + instructionId);
                  mockContainer.setInstructionId(instructionId);

                  ContainerItem mockContainerItem = new ContainerItem();
                  mockContainerItem.setTrackingId("MOCK_TRACKING_ID_" + instructionId);

                  mockContainer.setContainerItems(Arrays.asList(mockContainerItem));

                  return Arrays.asList(mockContainer);
                })
        .when(containerService)
        .getContainerByInstruction(anyLong());

    doNothing()
        .when(rdcInstructionHelper)
        .persistForCancelInstructions(anyList(), anyList(), anyList());
    InstructionSummary cancelInstructionSummary =
        rdcInstructionService.cancelInstruction(123L, headers);

    assertNotNull(cancelInstructionSummary);
    assertNotNull(cancelInstructionSummary.getCompleteTs());
    assertNotNull(cancelInstructionSummary.getCompleteUserId());
    assertSame(cancelInstructionSummary.getReceivedQuantity(), 0);
    assertSame(
        cancelInstructionSummary.getCompleteUserId(),
        headers.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(rdcInstructionUtils, times(1))
        .isCancelInstructionAllowed(any(Instruction.class), anyString());
    verify(rdcInstructionHelper, times(1))
        .persistForCancelInstructions(anyList(), anyList(), anyList());
    verify(rdcReceiptBuilder, times(1)).buildReceipt(any(Instruction.class), anyString(), anyInt());
    verify(containerService, times(1)).getContainerByInstruction(anyLong());
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testCancelInstructionThrowsExceptionWhenInstructionIdIsNotValid()
      throws ReceivingException {
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenThrow(
            new ReceivingException(
                ReceivingException.INSTRUCTION_NOT_FOUND, HttpStatus.INTERNAL_SERVER_ERROR));

    rdcInstructionService.cancelInstruction(123L, headers);

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testCancelInstructionThrowsExceptionWhenInstructionIsAlreadyCompleted()
      throws ReceivingException {
    Instruction instruction = getInstructions();
    instruction.setCreateUserId(ReceivingConstants.DEFAULT_USER);
    instruction.setCompleteTs(new Date());
    instruction.setCompleteUserId(ReceivingConstants.DEFAULT_USER);

    when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instruction);
    when(rdcInstructionUtils.isCancelInstructionAllowed(any(Instruction.class), anyString()))
        .thenThrow(
            new ReceivingBadDataException(
                ExceptionCodes.INSTRUCITON_IS_ALREADY_COMPLETED,
                ReceivingException.INSTRUCTION_IS_ALREADY_COMPLETED));

    rdcInstructionService.cancelInstruction(123L, headers);

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(rdcInstructionUtils, times(1))
        .isCancelInstructionAllowed(any(Instruction.class), anyString());
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testCancelInstructionThrowsExceptionWhenInstructionIsPartiallyReceived()
      throws ReceivingException {
    Instruction instruction = getInstructions();
    instruction.setCreateUserId(ReceivingConstants.DEFAULT_USER);
    instruction.setReceivedQuantity(100);

    when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instruction);
    when(rdcInstructionUtils.isCancelInstructionAllowed(any(Instruction.class), anyString()))
        .thenThrow(
            new ReceivingBadDataException(
                ExceptionCodes.INSTRUCITON_IS_ALREADY_COMPLETED,
                ReceivingException.INSTRUCTION_IS_ALREADY_COMPLETED));

    rdcInstructionService.cancelInstruction(123L, headers);

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(rdcInstructionUtils, times(1))
        .isCancelInstructionAllowed(any(Instruction.class), anyString());
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testCancelInstructionThrowsExceptionWhenInstructionIsNotOwnedByCurrentUser()
      throws ReceivingException {
    Instruction instruction = getInstructions();
    instruction.setCreateUserId(ReceivingConstants.DEFAULT_USER);

    when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instruction);
    when(rdcInstructionUtils.isCancelInstructionAllowed(any(Instruction.class), anyString()))
        .thenThrow(
            new ReceivingBadDataException(
                ExceptionCodes.MULTI_USER_ERROR_MESSAGE, ReceivingException.MULTI_USER_ERROR_CODE));

    rdcInstructionService.cancelInstruction(123L, headers);

    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(rdcInstructionUtils, times(1))
        .isCancelInstructionAllowed(any(Instruction.class), anyString());
  }

  @Test
  public void testPublishContainerAndMovePublishesContainerWhenMoveConfigIsNotEnabled() {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    doNothing().when(containerService).publishDockTagContainer(any(Container.class));
    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());

    rdcInstructionService.publishContainerAndMove("DT160443041812736961", container, headers);

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(containerService, times(1)).publishDockTagContainer(any(Container.class));
    verify(rdcManagedConfig, times(0)).getMoveToLocationForDockTag();
    verify(movePublisher, times(0))
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
  }

  @Test
  public void testPublishContainerAndMovePublishesMoveWhenContainerConfigIsNotEnabled() {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);
    doNothing().when(containerService).publishDockTagContainer(any(Container.class));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DOCKTAG_MOVE_PUBLISH_ENABLED,
            false))
        .thenReturn(true);
    when(rdcManagedConfig.getMoveToLocationForDockTag()).thenReturn("STG0");
    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());

    rdcInstructionService.publishContainerAndMove("DT160443041812736961", container, headers);

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(containerService, times(0)).publishDockTagContainer(any(Container.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DOCKTAG_MOVE_PUBLISH_ENABLED,
            false);
    verify(rdcManagedConfig, times(1)).getMoveToLocationForDockTag();
    verify(movePublisher, times(1))
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
  }

  @Test
  public void testPublishContainerAndMoveDoNotPublishesMoveWhenDocktagPublishIsNotEnabled() {
    doNothing().when(containerService).publishDockTagContainer(any(Container.class));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.IS_MOVE_PUBLISH_ENABLED, false))
        .thenReturn(true);
    when(rdcManagedConfig.getMoveToLocationForDockTag()).thenReturn("STG0");
    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());

    rdcInstructionService.publishContainerAndMove("DT160443041812736961", container, headers);

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DOCKTAG_MOVE_PUBLISH_ENABLED,
            false);
    verify(rdcManagedConfig, times(0)).getMoveToLocationForDockTag();
    verify(movePublisher, times(0))
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
  }

  @Test
  public void testPublishContainerAndMove_WhenMCPIBDocktagConfigIsEnabled() {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_PARITY_MCPIB_DOCKTAG_PUBLISH_ENABLED,
            false))
        .thenReturn(true);
    doNothing().when(containerService).publishDockTagContainer(any(Container.class));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DOCKTAG_MOVE_PUBLISH_ENABLED,
            false))
        .thenReturn(true);
    when(rdcManagedConfig.getMoveToLocationForDockTag()).thenReturn("STG0");
    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());

    rdcInstructionService.publishContainerAndMove("DT160443041812736961", container, headers);

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_PARITY_MCPIB_DOCKTAG_PUBLISH_ENABLED,
            false);
    verify(containerService, times(1)).publishDockTagContainer(any(Container.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DOCKTAG_MOVE_PUBLISH_ENABLED,
            false);
    verify(rdcManagedConfig, times(1)).getMoveToLocationForDockTag();
    verify(movePublisher, times(1))
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
  }

  @Test
  public void testPublishContainerAndMovePublishesBothContainerAndMoveWhenBothConfigsAreEnabled() {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    doNothing().when(containerService).publishDockTagContainer(any(Container.class));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DOCKTAG_MOVE_PUBLISH_ENABLED,
            false))
        .thenReturn(true);
    when(rdcManagedConfig.getMoveToLocationForDockTag()).thenReturn("STG0");
    doNothing()
        .when(movePublisher)
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());

    rdcInstructionService.publishContainerAndMove("DT160443041812736961", container, headers);

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(containerService, times(1)).publishDockTagContainer(any(Container.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DOCKTAG_MOVE_PUBLISH_ENABLED,
            false);
    verify(rdcManagedConfig, times(1)).getMoveToLocationForDockTag();
    verify(movePublisher, times(1))
        .publishMove(
            anyInt(), anyString(), any(HttpHeaders.class), any(LinkedTreeMap.class), anyString());
  }

  @Test
  public void testValidateUpcNumber() {
    try {
      rdcInstructionService.validateInstructionRequest(
          MockInstructionRequest.getInstructionRequest());
    } catch (Exception e) {
      assertTrue(false, "Exception not expected");
    }
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testValidateFeatureTypeForInvalidFeatureType()
      throws ReceivingException, IOException {
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    instructionRequest.setFeatureType("TEST");
    rdcInstructionService.validateFeatureTypes(instructionRequest.getFeatureType());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testValidateLocationHeadersNotMissingForDAReceiving() throws IOException {
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    instructionRequest.setFeatureType("WORK_STATION");
    rdcInstructionService.validateLocationHeadersForWorkStationReceiving(
        instructionRequest.getFeatureType(), MockHttpHeaders.getHeaders());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testWorkStationDaReceivingThrowsExceptionWhenNoDADocumentsFound()
      throws ReceivingException, IOException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDAReceivingFeatureType();
    doReturn(scanTypeDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNum,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    List<DeliveryDocument> deliveryDocuments =
        MockInstructionRequest.getInstructionRequestWithDeliveryDocuments().getDeliveryDocuments();
    when(scanTypeDeliveryDocumentsSearchHandler.fetchDeliveryDocument(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(deliveryDocuments);
    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.NON_DA_PURCHASE_REF_TYPE, RdcConstants.NON_DA_PURCHASE_REF_TYPE_MSG))
        .when(rdcInstructionUtils)
        .filterDADeliveryDocuments(anyList(), any(InstructionRequest.class));
    rdcInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), headers);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testSSTKReceivingThrowsExceptionWhenNoSSTKDocumentsFoundAndDAFound()
      throws ReceivingException, IOException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDAReceivingFeatureType();
    doReturn(scanTypeDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNum,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    when(scanTypeDeliveryDocumentsSearchHandler.fetchDeliveryDocument(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(deliveryDocuments);
    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.DA_PURCHASE_REF_TYPE, RdcConstants.DA_PURCHASE_REF_TYPE_MSG))
        .when(rdcInstructionUtils)
        .filterDADeliveryDocuments(anyList(), any(InstructionRequest.class));
    rdcInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), headers);
  }

  @Test
  public void testWorkStationDAReceivingReturnsSuccessResponse()
      throws ReceivingException, IOException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDAReceivingFeatureType();
    doReturn(scanTypeDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNum,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();

    when(rdcDeliveryService.findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(gson.toJson(deliveryDocuments));
    when(scanTypeDeliveryDocumentsSearchHandler.fetchDeliveryDocument(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(deliveryDocuments);
    when(rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(
            anyList(), any(InstructionRequest.class)))
        .thenReturn(deliveryDocuments);
    when(rdcInstructionUtils.isSinglePoAndPoLine(anyList())).thenReturn(true);
    when(rdcInstructionUtils.filterDADeliveryDocuments(anyList(), any(InstructionRequest.class)))
        .thenReturn(deliveryDocuments);
    when(rdcDaService.createInstructionForDACaseReceiving(
            any(InstructionRequest.class), any(), any(HttpHeaders.class)))
        .thenReturn(getInstructionResponseForSingleDA());
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(10L));
    when(rdcInstructionUtils.filterSSTKDeliveryDocuments(anyList()))
        .thenReturn(instructionRequest.getDeliveryDocuments());
    when(rdcInstructionUtils.checkAllSSTKPoFulfilled(
            anyList(), any(InstructionRequest.class), any(ReceivedQuantityResponseFromRDS.class)))
        .thenReturn(instructionRequest.getDeliveryDocuments());
    when(rdcInstructionUtils.autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString()))
        .thenReturn(new Pair<>(deliveryDocuments.get(0), 10L));
    when(rdcInstructionUtils.isProblemTagValidationApplicable(any())).thenReturn(true);
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.add(RdcConstants.WFT_LOCATION_ID, "122");
    httpHeaders.add(RdcConstants.WFT_LOCATION_TYPE, "DOOR-122");
    httpHeaders.add(RdcConstants.WFT_SCC_CODE, "34332323");
    httpHeaders.add(RdcConstants.DA_RECEIVING_CAPABILITY, "true");
    InstructionResponse instructionResponse =
        rdcInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);
    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(instructionResponse.getDeliveryDocuments().size(), 1);
    assertFalse(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .isAtlasConvertedItem());

    verify(rdcInstructionUtils, times(1)).validateAndProcessGdmDeliveryDocuments(anyList(), any());
    verify(rdcDaService, times(1))
        .createInstructionForDACaseReceiving(
            any(InstructionRequest.class), any(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1))
        .filterDADeliveryDocuments(anyList(), any(InstructionRequest.class));
    verify(rdcDaService, times(1))
        .createInstructionForDACaseReceiving(
            any(InstructionRequest.class), any(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1))
        .autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testWorkStationDAReceivingReturnErrorResponse()
      throws ReceivingException, IOException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDAReceivingFeatureType();
    doReturn(scanTypeDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNum,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    when(scanTypeDeliveryDocumentsSearchHandler.fetchDeliveryDocument(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(deliveryDocuments);
    when(rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(
            anyList(), any(InstructionRequest.class)))
        .thenReturn(deliveryDocuments);
    when(rdcInstructionUtils.filterDADeliveryDocuments(anyList(), any(InstructionRequest.class)))
        .thenReturn(deliveryDocuments);
    when(rdcInstructionUtils.isSinglePoAndPoLine(anyList())).thenReturn(true);
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(10L));
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(receivedQuantityResponseFromRDS);

    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.OVERAGE_ERROR, ReceivingException.OVERAGE_ERROR))
        .when(rdcDaService)
        .createInstructionForDACaseReceiving(
            any(InstructionRequest.class), any(), any(HttpHeaders.class));
    when(rdcInstructionUtils.autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString()))
        .thenReturn(new Pair<>(deliveryDocuments.get(0), 10L));

    rdcInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), headers);

    verify(rdcInstructionUtils, times(1)).validateAndProcessGdmDeliveryDocuments(anyList(), any());
    verify(rdcDaService, times(1))
        .createInstructionForDACaseReceiving(
            any(InstructionRequest.class), any(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1))
        .filterDADeliveryDocuments(anyList(), any(InstructionRequest.class));
    verify(rdcDaService, times(1))
        .createInstructionForDACaseReceiving(
            any(InstructionRequest.class), any(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1))
        .autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString());
  }

  @Test
  public void testGenerateInstruction_ReceiveDA_PO_WithDA_ReceivingCapability()
      throws IOException, ReceivingException {
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    instructionRequest.setDeliveryDocuments(
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA());
    InstructionResponse mockInstructionResponse = new InstructionResponseImplNew();
    mockInstructionResponse.setInstruction(new Instruction());
    mockInstructionResponse.setDeliveryDocuments(
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA());
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(RdcConstants.DA_RECEIVING_CAPABILITY, "true");
    doNothing().when(rdcInstructionUtils).updateAdditionalItemDetailsFromGDM(anyList());
    receivedQuantityResponseFromRDS =
        MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(10L);
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(receivedQuantityResponseFromRDS);
    when(rdcInstructionUtils.autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString()))
        .thenReturn(
            new Pair<>(MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0), 10L));
    when(rdcInstructionUtils.isSSTKDocument(any(DeliveryDocument.class))).thenReturn(Boolean.FALSE);
    when(rdcInstructionUtils.isDADocument(any(DeliveryDocument.class))).thenReturn(Boolean.TRUE);
    when(rdcDaService.createInstructionForDACaseReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class)))
        .thenReturn(mockInstructionResponse);
    when(rdcInstructionUtils.isProblemTagValidationApplicable(any())).thenReturn(true);

    InstructionResponse instructionResponse =
        rdcInstructionService.generateInstruction(instructionRequest, httpHeaders);

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertNotNull(instructionResponse.getDeliveryDocuments());
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);

    verify(nimRdsService, times(1))
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
    verify(rdcInstructionUtils, times(1))
        .autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString());
    verify(rdcInstructionUtils, times(1)).isSSTKDocument(any(DeliveryDocument.class));
    verify(rdcInstructionUtils, times(1)).isDADocument(any(DeliveryDocument.class));
    verify(rdcDaService, times(1))
        .createInstructionForDACaseReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class));
  }

  @Test
  public void testGenerateInstruction_ReceiveDA_PO_With_WorkStationReceiving()
      throws IOException, ReceivingException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDAReceivingFeatureType();
    instructionRequest.setDeliveryDocuments(
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA());
    InstructionResponse mockInstructionResponse = new InstructionResponseImplNew();
    mockInstructionResponse.setInstruction(new Instruction());
    mockInstructionResponse.setDeliveryDocuments(
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA());
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.add(RdcConstants.DA_RECEIVING_CAPABILITY, "true");
    doNothing().when(rdcInstructionUtils).updateAdditionalItemDetailsFromGDM(anyList());
    receivedQuantityResponseFromRDS =
        MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(10L);
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(receivedQuantityResponseFromRDS);
    when(rdcInstructionUtils.autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString()))
        .thenReturn(
            new Pair<>(MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0), 10L));
    when(rdcDaService.createInstructionForDACaseReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class)))
        .thenReturn(mockInstructionResponse);

    InstructionResponse instructionResponse =
        rdcInstructionService.generateInstruction(instructionRequest, httpHeaders);

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertNotNull(instructionResponse.getDeliveryDocuments());
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);

    verify(nimRdsService, times(1))
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
    verify(rdcInstructionUtils, times(1))
        .autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString());
    verify(rdcDaService, times(1))
        .createInstructionForDACaseReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void
      testGenerateInstruction_ReceiveSSTK_PO_With_WorkStationReceiving_throwsExceptionAsSSTKIsNotSupported()
          throws IOException, ReceivingException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDAReceivingFeatureType();
    instructionRequest.setDeliveryDocuments(
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA());
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(RdcConstants.DA_RECEIVING_CAPABILITY, "true");
    doNothing().when(rdcInstructionUtils).updateAdditionalItemDetailsFromGDM(anyList());
    receivedQuantityResponseFromRDS =
        MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(10L);
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(receivedQuantityResponseFromRDS);
    when(rdcInstructionUtils.autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString()))
        .thenReturn(
            new Pair<>(MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0), 10L));
    when(rdcInstructionUtils.isSSTKDocument(any(DeliveryDocument.class))).thenReturn(true);
    when(rdcReceivingUtils.checkIfMaxOverageReceived(
            any(DeliveryDocument.class), anyLong(), anyInt()))
        .thenReturn(false);

    rdcInstructionService.generateInstruction(instructionRequest, httpHeaders);

    verify(rdcInstructionUtils, times(1)).updateAdditionalItemDetailsFromGDM(anyList());
    verify(nimRdsService, times(1))
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
    verify(rdcInstructionUtils, times(1))
        .autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString());
    verify(rdcInstructionUtils, times(1)).isSSTKDocument(any(DeliveryDocument.class));
    verify(rdcReceivingUtils, times(1))
        .checkIfMaxOverageReceived(any(DeliveryDocument.class), anyLong(), anyInt());
  }

  @Test
  public void
      testGenerateInstruction_ReceiveSSTK_PO_With_WorkStationReceiving_ReportOverageForSSTKFreight()
          throws IOException, ReceivingException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDAReceivingFeatureType();
    instructionRequest.setDeliveryDocuments(
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA());
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(RdcConstants.DA_RECEIVING_CAPABILITY, "true");
    doNothing().when(rdcInstructionUtils).updateAdditionalItemDetailsFromGDM(anyList());
    receivedQuantityResponseFromRDS =
        MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(10L);
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(receivedQuantityResponseFromRDS);
    when(rdcInstructionUtils.autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString()))
        .thenReturn(
            new Pair<>(MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0), 10L));
    when(rdcInstructionUtils.isSSTKDocument(any(DeliveryDocument.class))).thenReturn(true);
    when(rdcReceivingUtils.checkIfMaxOverageReceived(
            any(DeliveryDocument.class), anyLong(), anyInt()))
        .thenReturn(true);
    when(rdcInstructionUtils.getOverageAlertInstruction(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(new Instruction());
    InstructionResponse instructionResponse =
        rdcInstructionService.generateInstruction(instructionRequest, httpHeaders);

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    verify(nimRdsService, times(1))
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
    verify(rdcInstructionUtils, times(1))
        .autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString());
    verify(rdcInstructionUtils, times(1)).isSSTKDocument(any(DeliveryDocument.class));
    verify(rdcReceivingUtils, times(1))
        .checkIfMaxOverageReceived(any(DeliveryDocument.class), anyLong(), anyInt());
  }

  @Test
  public void testGenerateInstruction_ReceiveSSTK_PO_WithDA_Receiving_Capability()
      throws IOException, ReceivingException {
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(RdcConstants.DA_RECEIVING_CAPABILITY, "true");
    doNothing().when(rdcInstructionUtils).updateAdditionalItemDetailsFromGDM(anyList());
    receivedQuantityResponseFromRDS =
        MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(10L);
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(receivedQuantityResponseFromRDS);
    when(rdcInstructionUtils.isSSTKDocument(any(DeliveryDocument.class))).thenReturn(Boolean.TRUE);
    when(rdcInstructionUtils.isDADocument(any(DeliveryDocument.class))).thenReturn(Boolean.FALSE);
    when(rdcInstructionUtils.filterSSTKDeliveryDocuments(anyList())).thenReturn(deliveryDocuments);
    when(rdcInstructionUtils.isSinglePoAndPoLine(anyList())).thenReturn(Boolean.TRUE);
    when(rdcInstructionUtils.autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString()))
        .thenReturn(new Pair<>(MockDeliveryDocuments.getDeliveryDocumentsForSSTK().get(0), 10L));
    when(rdcInstructionUtils.isProblemTagValidationApplicable(any())).thenReturn(true);
    when(rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class)))
        .thenReturn(getInstructionResponseForSingleSSTK());
    InstructionResponse instructionResponse =
        rdcInstructionService.generateInstruction(instructionRequest, httpHeaders);

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertNotNull(instructionResponse.getDeliveryDocuments());
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);

    verify(nimRdsService, times(1))
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
    verify(rdcInstructionUtils, times(2))
        .autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString());
    verify(rdcInstructionUtils, times(1)).isSSTKDocument(any(DeliveryDocument.class));
    verify(rdcInstructionUtils, times(0)).isDADocument(any(DeliveryDocument.class));
    verify(rdcInstructionUtils, times(1)).filterSSTKDeliveryDocuments(anyList());
    verify(rdcInstructionUtils, times(1)).isSinglePoAndPoLine(anyList());
    verify(rdcInstructionUtils, times(1))
        .createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class));
  }

  @Test
  public void testServeInstructionRequest_ReceiveDA_PO_WithDA_ReceivingCapability()
      throws IOException, ReceivingException {
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    InstructionResponse mockInstructionResponse = new InstructionResponseImplNew();
    mockInstructionResponse.setInstruction(new Instruction());
    mockInstructionResponse.setDeliveryDocuments(
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA());
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(RdcConstants.DA_RECEIVING_CAPABILITY, "true");
    doNothing().when(rdcInstructionUtils).updateAdditionalItemDetailsFromGDM(anyList());
    doNothing().when(rdcReceivingUtils).updateQuantitiesBasedOnUOM(anyList());
    doReturn(scanTypeDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNum,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(rdcInstructionUtils.validateAndProcessGdmDeliveryDocuments(
            anyList(), any(InstructionRequest.class)))
        .thenReturn(deliveryDocuments);
    when(rdcInstructionUtils.hasMoreUniqueItems(anyList())).thenReturn(Boolean.FALSE);
    when(scanTypeDeliveryDocumentsSearchHandler.fetchDeliveryDocument(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(deliveryDocuments);
    when(rdcDeliveryService.findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(gson.toJson(deliveryDocuments));
    receivedQuantityResponseFromRDS =
        MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(10L);
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(receivedQuantityResponseFromRDS);
    when(rdcInstructionUtils.isProblemTagValidationApplicable(any())).thenReturn(true);
    when(rdcInstructionUtils.autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString()))
        .thenReturn(
            new Pair<>(MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0), 10L));
    when(rdcInstructionUtils.isSSTKDocument(any(DeliveryDocument.class))).thenReturn(Boolean.FALSE);
    when(rdcInstructionUtils.isDADocument(any(DeliveryDocument.class))).thenReturn(Boolean.TRUE);
    when(rdcDaService.createInstructionForDACaseReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class)))
        .thenReturn(mockInstructionResponse);

    InstructionResponse instructionResponse =
        rdcInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), httpHeaders);

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertNotNull(instructionResponse.getDeliveryDocuments());
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);

    verify(nimRdsService, times(1))
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
    verify(rdcInstructionUtils, times(1))
        .autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString());
    verify(rdcInstructionUtils, times(1)).isSSTKDocument(any(DeliveryDocument.class));
    verify(rdcInstructionUtils, times(1)).isDADocument(any(DeliveryDocument.class));
    verify(rdcDaService, times(1))
        .createInstructionForDACaseReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class));
  }

  @Test
  public void testGenerateInstruction_ReceiveSSTK_PO_WithOut_DA_Receiving_Capability()
      throws IOException, ReceivingException {
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequest();
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    receivedQuantityResponseFromRDS =
        MockReceivedQtyRespFromRds.getReceivedQtyMapByPoAndPoLine(10L);
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(receivedQuantityResponseFromRDS);
    when(rdcInstructionUtils.isSSTKDocument(any(DeliveryDocument.class))).thenReturn(Boolean.TRUE);
    when(rdcInstructionUtils.isDADocument(any(DeliveryDocument.class))).thenReturn(Boolean.FALSE);
    when(rdcInstructionUtils.filterSSTKDeliveryDocuments(anyList())).thenReturn(deliveryDocuments);
    when(rdcInstructionUtils.isSinglePoAndPoLine(anyList())).thenReturn(Boolean.TRUE);
    when(rdcInstructionUtils.autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString()))
        .thenReturn(new Pair<>(MockDeliveryDocuments.getDeliveryDocumentsForSSTK().get(0), 10L));
    when(rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class)))
        .thenReturn(getInstructionResponseForSingleSSTK());
    InstructionResponse instructionResponse =
        rdcInstructionService.generateInstruction(instructionRequest, httpHeaders);

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertNotNull(instructionResponse.getDeliveryDocuments());
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);

    verify(nimRdsService, times(1))
        .getReceivedQtyByDeliveryDocuments(anyList(), any(HttpHeaders.class), anyString());
    verify(rdcInstructionUtils, times(1))
        .autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString());
    verify(rdcInstructionUtils, times(1)).filterSSTKDeliveryDocuments(anyList());
    verify(rdcInstructionUtils, times(1)).isSinglePoAndPoLine(anyList());
    verify(rdcInstructionUtils, times(1))
        .createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class));
  }

  @Test
  public void testValidateUpcNumber_SSCC() {
    try {
      rdcInstructionService.validateInstructionRequest(
          MockInstructionRequest.getSSCCInstructionRequest());
    } catch (Exception e) {
      assertTrue(false, "Exception not expected");
    }
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testValidateUpcNumber_NoUpcOrSscc() throws ReceivingException {

    rdcInstructionService.validateInstructionRequest(new InstructionRequest());
  }

  @Test
  public void testServeInstructionRequest_three_scan_docktag_type()
      throws ReceivingException, IOException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestForThreeScanDocktag();
    doReturn(scanTypeDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNum,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    List<DeliveryDocument> deliveryDocuments =
        MockInstructionRequest.getInstructionRequestWithDeliveryDocuments().getDeliveryDocuments();
    when(scanTypeDeliveryDocumentsSearchHandler.fetchDeliveryDocument(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(deliveryDocuments);
    when(rdcDeliveryService.findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(gson.toJson(deliveryDocuments));

    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    Instruction instruction = new Instruction();
    instruction.setInstructionCode(RdcConstants.THREE_SCAN_DOCKTAG_INSTRUCTION_CODE);
    instruction.setInstructionMsg(RdcConstants.THREE_SCAN_DOCKTAG_INSTRUCTION_MESSAGE);
    instructionResponse.setInstruction(instruction);
    when(rdcInstructionUtils.createInstructionForThreeScanDocktag(
            any(InstructionRequest.class),
            any(InstructionResponse.class),
            anyBoolean(),
            any(HttpHeaders.class)))
        .thenReturn(instructionResponse);
    InstructionResponse response =
        rdcInstructionService.serveInstructionRequest(
            gson.toJson(instructionRequest), MockHttpHeaders.getHeaders());
    assertEquals(
        RdcConstants.THREE_SCAN_DOCKTAG_INSTRUCTION_CODE,
        response.getInstruction().getInstructionCode());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testServeInstructionRequest_three_scan_docktag_type_exception()
      throws ReceivingException, IOException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestForThreeScanDocktag();
    doReturn(scanTypeDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNum,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    List<DeliveryDocument> deliveryDocuments =
        MockInstructionRequest.getInstructionRequestWithDeliveryDocuments().getDeliveryDocuments();
    when(scanTypeDeliveryDocumentsSearchHandler.fetchDeliveryDocument(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(deliveryDocuments);
    when(rdcDeliveryService.findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(gson.toJson(deliveryDocuments));
    when(rdcInstructionUtils.createInstructionForThreeScanDocktag(
            any(InstructionRequest.class),
            any(InstructionResponse.class),
            anyBoolean(),
            any(HttpHeaders.class)))
        .thenThrow(ReceivingBadDataException.class);
    InstructionResponse response =
        rdcInstructionService.serveInstructionRequest(
            gson.toJson(instructionRequest), MockHttpHeaders.getHeaders());
    assertEquals(
        RdcConstants.THREE_SCAN_DOCKTAG_INSTRUCTION_CODE,
        response.getInstruction().getInstructionCode());
  }

  @Test
  public void testServeInstructionRequest_SsccIsScanned_DSDCSuccess()
      throws ReceivingException, IOException {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.add(RdcConstants.WFT_LOCATION_ID, "122");
    httpHeaders.add(RdcConstants.WFT_LOCATION_TYPE, "DOOR-122");
    httpHeaders.add(RdcConstants.WFT_SCC_CODE, "34332323");
    httpHeaders.add(RdcConstants.DA_RECEIVING_CAPABILITY, "true");
    when(rdcManagedConfig.isAsnReceivingEnabled()).thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_FREIGHT_IDENTIFICATION_FEATURE_ENABLED,
            false))
        .thenReturn(false);
    doReturn(vendorBasedDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNum,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ASN_MULTI_SKU_ENABLED,
            false))
        .thenReturn(true);
    doNothing().when(nimRdsService).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    doNothing()
        .when(rdcInstructionUtils)
        .populateOpenAndReceivedQtyInDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString());
    doReturn(rdcVendorValidator)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            facilityNum, ReceivingConstants.VENDOR_VALIDATOR, VendorValidator.class);
    doReturn(multiSkuService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            facilityNum, ReceivingConstants.ASN_MULTISKU_HANDLER, MultiSkuService.class);

    when(asnReceivingAuditLogger.isVendorEnabledForAsnReceiving(
            any(DeliveryDocument.class), any(InstructionRequest.class)))
        .thenReturn(true);
    when(rdcDsdcService.createInstructionForDSDCReceiving(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(getMockDsdcSuccessInstructionReponse());
    InstructionResponse multiSkuResponse = new InstructionResponseImplNew();
    Instruction dummyInstruction = new Instruction();
    dummyInstruction.setInstructionCode(ReceivingConstants.MULTI_SKU_INST_CODE);
    dummyInstruction.setInstructionMsg(ReceivingConstants.MULTI_SKU_INST_MSG);
    multiSkuResponse.setInstruction(dummyInstruction);

    InstructionResponse instructionResponse =
        rdcInstructionService.serveInstructionRequest(
            gson.toJson(MockInstructionRequest.getSSCCInstructionRequestForDsdc()), httpHeaders);

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());

    verify(rdcDeliveryService, times(0))
        .findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(0)).validateAndProcessGdmDeliveryDocuments(anyList(), any());
    verify(rdcInstructionUtils, times(0)).hasMoreUniqueItems(anyList());
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_FREIGHT_IDENTIFICATION_FEATURE_ENABLED,
            false);
    verify(nimRdsService, times(0)).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(0))
        .populateOpenAndReceivedQtyInDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString());
    verify(multiSkuService, times(0))
        .handleMultiSku(
            anyBoolean(),
            any(InstructionRequest.class),
            any(InstructionResponse.class),
            any(Instruction.class));
    verify(rdcDsdcService, times(1))
        .createInstructionForDSDCReceiving(any(InstructionRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testServeInstructionRequest_SsccIsScanned_DSDCAuditPack()
      throws ReceivingException, IOException {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.add(RdcConstants.WFT_LOCATION_ID, "122");
    httpHeaders.add(RdcConstants.WFT_LOCATION_TYPE, "DOOR-122");
    httpHeaders.add(RdcConstants.WFT_SCC_CODE, "34332323");
    httpHeaders.add(RdcConstants.DA_RECEIVING_CAPABILITY, "true");
    when(rdcManagedConfig.isAsnReceivingEnabled()).thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_FREIGHT_IDENTIFICATION_FEATURE_ENABLED,
            false))
        .thenReturn(false);
    doReturn(vendorBasedDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNum,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ASN_MULTI_SKU_ENABLED,
            false))
        .thenReturn(true);
    doNothing().when(nimRdsService).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    doNothing()
        .when(rdcInstructionUtils)
        .populateOpenAndReceivedQtyInDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString());
    doReturn(rdcVendorValidator)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            facilityNum, ReceivingConstants.VENDOR_VALIDATOR, VendorValidator.class);
    doReturn(multiSkuService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            facilityNum, ReceivingConstants.ASN_MULTISKU_HANDLER, MultiSkuService.class);

    when(asnReceivingAuditLogger.isVendorEnabledForAsnReceiving(
            any(DeliveryDocument.class), any(InstructionRequest.class)))
        .thenReturn(true);
    when(rdcDsdcService.createInstructionForDSDCReceiving(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(getMockDsdcAuditPackInstructionReponse());
    InstructionResponse multiSkuResponse = new InstructionResponseImplNew();
    Instruction dummyInstruction = new Instruction();
    dummyInstruction.setInstructionCode(ReceivingConstants.MULTI_SKU_INST_CODE);
    dummyInstruction.setInstructionMsg(ReceivingConstants.MULTI_SKU_INST_MSG);
    multiSkuResponse.setInstruction(dummyInstruction);

    InstructionResponse instructionResponse =
        rdcInstructionService.serveInstructionRequest(
            gson.toJson(MockInstructionRequest.getSSCCInstructionRequestForDsdc()), httpHeaders);

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.DSDC_RECEIVING.getInstructionCode());

    verify(rdcDeliveryService, times(0))
        .findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(0)).validateAndProcessGdmDeliveryDocuments(anyList(), any());
    verify(rdcInstructionUtils, times(0)).hasMoreUniqueItems(anyList());
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_FREIGHT_IDENTIFICATION_FEATURE_ENABLED,
            false);
    verify(nimRdsService, times(0)).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(0))
        .populateOpenAndReceivedQtyInDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString());
    verify(multiSkuService, times(0))
        .handleMultiSku(
            anyBoolean(),
            any(InstructionRequest.class),
            any(InstructionResponse.class),
            any(Instruction.class));
    verify(rdcDsdcService, times(1))
        .createInstructionForDSDCReceiving(any(InstructionRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testServeInstructionRequest_SsccIsScanned_RDS_Fallback_DSDCAuditPack()
      throws ReceivingException, IOException {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.add(RdcConstants.WFT_LOCATION_ID, "122");
    httpHeaders.add(RdcConstants.WFT_LOCATION_TYPE, "DOOR-122");
    httpHeaders.add(RdcConstants.WFT_SCC_CODE, "34332323");
    httpHeaders.add(RdcConstants.DA_RECEIVING_CAPABILITY, "true");
    File resource =
        new ClassPathResource("GdmMappedResponseV2_DSDC_DeliveryDocuments.json").getFile();
    String mockDeliveryDocumentsResponse = new String(Files.readAllBytes(resource.toPath()));

    when(rdcDeliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class)))
        .thenReturn(
            Arrays.asList(gson.fromJson(mockDeliveryDocumentsResponse, DeliveryDocument[].class)));
    when(rdcVendorValidator.isAsnReceivingEnabled()).thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_FREIGHT_IDENTIFICATION_FEATURE_ENABLED,
            false))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_DSDC_SSCC_PACKS_AVAILABLE_IN_GDM,
            false))
        .thenReturn(true);
    List<DeliveryDocument> deliveryDocuments =
        MockInstructionRequest.getSSCCInstructionRequestForDsdcWithDeliveryDocuments()
            .getDeliveryDocuments();
    doReturn(vendorBasedDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNum,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ASN_MULTI_SKU_ENABLED,
            false))
        .thenReturn(true);
    doNothing().when(nimRdsService).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    doNothing()
        .when(rdcInstructionUtils)
        .populateOpenAndReceivedQtyInDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString());
    doReturn(rdcVendorValidator)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            facilityNum, ReceivingConstants.VENDOR_VALIDATOR, VendorValidator.class);
    doReturn(rdcDeliveryService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_SERVICE_KEY), any(Class.class));
    doReturn(multiSkuService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            facilityNum, ReceivingConstants.ASN_MULTISKU_HANDLER, MultiSkuService.class);

    when(asnReceivingAuditLogger.isVendorEnabledForAsnReceiving(
            any(DeliveryDocument.class), any(InstructionRequest.class)))
        .thenReturn(true);
    when(rdcDsdcService.createInstructionForDSDCReceiving(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(getMockDsdcAuditPackInstructionReponse());
    when(rdcAtlasDsdcService.receiveDsdcPacksInAtlas(
            any(InstructionRequest.class), any(HttpHeaders.class), any()))
        .thenReturn(getMockDsdcAuditPackInstructionReponse());
    InstructionResponse multiSkuResponse = new InstructionResponseImplNew();
    Instruction dummyInstruction = new Instruction();
    dummyInstruction.setInstructionCode(ReceivingConstants.MULTI_SKU_INST_CODE);
    dummyInstruction.setInstructionMsg(ReceivingConstants.MULTI_SKU_INST_MSG);
    multiSkuResponse.setInstruction(dummyInstruction);

    InstructionResponse instructionResponse =
        rdcInstructionService.serveInstructionRequest(
            gson.toJson(
                MockInstructionRequest.getSSCCInstructionRequestForDsdcWithDeliveryDocuments()),
            httpHeaders);

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.DSDC_RECEIVING.getInstructionCode());

    verify(rdcDeliveryService, times(1))
        .findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(0)).validateAndProcessGdmDeliveryDocuments(anyList(), any());
    verify(rdcInstructionUtils, times(0)).hasMoreUniqueItems(anyList());
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_FREIGHT_IDENTIFICATION_FEATURE_ENABLED,
            false);
    verify(nimRdsService, times(0)).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(0))
        .populateOpenAndReceivedQtyInDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString());
    verify(multiSkuService, times(0))
        .handleMultiSku(
            anyBoolean(),
            any(InstructionRequest.class),
            any(InstructionResponse.class),
            any(Instruction.class));
    verify(rdcDsdcService, times(1))
        .createInstructionForDSDCReceiving(any(InstructionRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void
      testServeInstructionRequest_SsccIsScanned_RDS_Fallback_GDM_SSCC_SCAN_Error_DSDCAuditPack()
          throws ReceivingException, IOException {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.add(RdcConstants.WFT_LOCATION_ID, "122");
    httpHeaders.add(RdcConstants.WFT_LOCATION_TYPE, "DOOR-122");
    httpHeaders.add(RdcConstants.WFT_SCC_CODE, "34332323");
    httpHeaders.add(RdcConstants.DA_RECEIVING_CAPABILITY, "true");
    File resource =
        new ClassPathResource("GdmMappedResponseV2_DSDC_DeliveryDocuments.json").getFile();
    String mockDeliveryDocumentsResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> gdmDeliveryDocumentList =
        Arrays.asList(gson.fromJson(mockDeliveryDocumentsResponse, DeliveryDocument[].class));
    gdmDeliveryDocumentList.get(0).setAsnNumber(GDM_SSCC_SCAN_ASN_NOT_FOUND);
    when(rdcDeliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class)))
        .thenReturn(
            Arrays.asList(gson.fromJson(mockDeliveryDocumentsResponse, DeliveryDocument[].class)));
    when(rdcVendorValidator.isAsnReceivingEnabled()).thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_FREIGHT_IDENTIFICATION_FEATURE_ENABLED,
            false))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_DSDC_SSCC_PACKS_AVAILABLE_IN_GDM,
            false))
        .thenReturn(true);
    doReturn(vendorBasedDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNum,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ASN_MULTI_SKU_ENABLED,
            false))
        .thenReturn(true);
    doNothing().when(nimRdsService).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    doNothing()
        .when(rdcInstructionUtils)
        .populateOpenAndReceivedQtyInDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString());
    doReturn(rdcVendorValidator)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            facilityNum, ReceivingConstants.VENDOR_VALIDATOR, VendorValidator.class);
    doReturn(rdcDeliveryService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_SERVICE_KEY), any(Class.class));
    doReturn(multiSkuService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            facilityNum, ReceivingConstants.ASN_MULTISKU_HANDLER, MultiSkuService.class);

    when(asnReceivingAuditLogger.isVendorEnabledForAsnReceiving(
            any(DeliveryDocument.class), any(InstructionRequest.class)))
        .thenReturn(true);
    when(rdcDsdcService.createInstructionForDSDCReceiving(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(getMockDsdcAuditPackInstructionReponse());
    when(rdcAtlasDsdcService.receiveDsdcPacksInAtlas(
            any(InstructionRequest.class), any(HttpHeaders.class), any()))
        .thenReturn(getMockDsdcAuditPackInstructionReponse());
    InstructionResponse multiSkuResponse = new InstructionResponseImplNew();
    Instruction dummyInstruction = new Instruction();
    dummyInstruction.setInstructionCode(ReceivingConstants.MULTI_SKU_INST_CODE);
    dummyInstruction.setInstructionMsg(ReceivingConstants.MULTI_SKU_INST_MSG);
    multiSkuResponse.setInstruction(dummyInstruction);

    InstructionResponse instructionResponse =
        rdcInstructionService.serveInstructionRequest(
            gson.toJson(
                MockInstructionRequest.getSSCCInstructionRequestForDsdcWithDeliveryDocuments()),
            httpHeaders);

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.DSDC_RECEIVING.getInstructionCode());

    verify(rdcDeliveryService, times(1))
        .findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(0)).validateAndProcessGdmDeliveryDocuments(anyList(), any());
    verify(rdcInstructionUtils, times(0)).hasMoreUniqueItems(anyList());
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_FREIGHT_IDENTIFICATION_FEATURE_ENABLED,
            false);
    verify(nimRdsService, times(0)).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(0))
        .populateOpenAndReceivedQtyInDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString());
    verify(multiSkuService, times(0))
        .handleMultiSku(
            anyBoolean(),
            any(InstructionRequest.class),
            any(InstructionResponse.class),
            any(Instruction.class));
    verify(rdcDsdcService, times(1))
        .createInstructionForDSDCReceiving(any(InstructionRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testServeInstructionRequest_SsccIsScanned_RDS_Fallback_DSDCAuditPack_ASN_Not_Found()
      throws ReceivingException, IOException {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.add(RdcConstants.WFT_LOCATION_ID, "122");
    httpHeaders.add(RdcConstants.WFT_LOCATION_TYPE, "DOOR-122");
    httpHeaders.add(RdcConstants.WFT_SCC_CODE, "34332323");
    httpHeaders.add(RdcConstants.DA_RECEIVING_CAPABILITY, "true");
    File resource =
        new ClassPathResource("GdmMappedResponseV2_DSDC_DeliveryDocuments.json").getFile();
    String mockDeliveryDocumentsResponse = new String(Files.readAllBytes(resource.toPath()));

    List<DeliveryDocument> mockDeliveryDocumentsList =
        Arrays.asList(gson.fromJson(mockDeliveryDocumentsResponse, DeliveryDocument[].class));
    mockDeliveryDocumentsList.get(0).setAsnNumber(GDM_SSCC_SCAN_ASN_NOT_FOUND);
    when(rdcDeliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class)))
        .thenReturn(mockDeliveryDocumentsList);
    when(rdcVendorValidator.isAsnReceivingEnabled()).thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_FREIGHT_IDENTIFICATION_FEATURE_ENABLED,
            false))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_DSDC_SSCC_PACKS_AVAILABLE_IN_GDM,
            false))
        .thenReturn(true);
    List<DeliveryDocument> deliveryDocuments =
        MockInstructionRequest.getSSCCInstructionRequestForDsdcWithDeliveryDocuments()
            .getDeliveryDocuments();
    doReturn(vendorBasedDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNum,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ASN_MULTI_SKU_ENABLED,
            false))
        .thenReturn(true);
    doNothing().when(nimRdsService).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    doNothing()
        .when(rdcInstructionUtils)
        .populateOpenAndReceivedQtyInDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString());
    doReturn(rdcVendorValidator)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            facilityNum, ReceivingConstants.VENDOR_VALIDATOR, VendorValidator.class);
    doReturn(rdcDeliveryService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_SERVICE_KEY), any(Class.class));
    doReturn(multiSkuService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            facilityNum, ReceivingConstants.ASN_MULTISKU_HANDLER, MultiSkuService.class);

    when(asnReceivingAuditLogger.isVendorEnabledForAsnReceiving(
            any(DeliveryDocument.class), any(InstructionRequest.class)))
        .thenReturn(true);
    when(rdcDsdcService.createInstructionForDSDCReceiving(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(getMockDsdcAuditPackInstructionReponse());
    when(rdcAtlasDsdcService.receiveDsdcPacksInAtlas(
            any(InstructionRequest.class), any(HttpHeaders.class), any()))
        .thenReturn(getMockDsdcAuditPackInstructionReponse());
    InstructionResponse multiSkuResponse = new InstructionResponseImplNew();
    Instruction dummyInstruction = new Instruction();
    dummyInstruction.setInstructionCode(ReceivingConstants.MULTI_SKU_INST_CODE);
    dummyInstruction.setInstructionMsg(ReceivingConstants.MULTI_SKU_INST_MSG);
    multiSkuResponse.setInstruction(dummyInstruction);

    InstructionResponse instructionResponse =
        rdcInstructionService.serveInstructionRequest(
            gson.toJson(
                MockInstructionRequest.getSSCCInstructionRequestForDsdcWithDeliveryDocuments()),
            httpHeaders);

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.DSDC_RECEIVING.getInstructionCode());

    verify(rdcDeliveryService, times(1))
        .findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(0)).validateAndProcessGdmDeliveryDocuments(anyList(), any());
    verify(rdcInstructionUtils, times(0)).hasMoreUniqueItems(anyList());
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_FREIGHT_IDENTIFICATION_FEATURE_ENABLED,
            false);
    verify(nimRdsService, times(0)).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(0))
        .populateOpenAndReceivedQtyInDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString());
    verify(multiSkuService, times(0))
        .handleMultiSku(
            anyBoolean(),
            any(InstructionRequest.class),
            any(InstructionResponse.class),
            any(Instruction.class));
    verify(rdcDsdcService, times(1))
        .createInstructionForDSDCReceiving(any(InstructionRequest.class), any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testServeInstructionRequest_SsccIsScanned_DSDCPackisAlreadyReceived()
      throws ReceivingException, IOException {
    when(rdcManagedConfig.isAsnReceivingEnabled()).thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_FREIGHT_IDENTIFICATION_FEATURE_ENABLED,
            false))
        .thenReturn(false);
    doReturn(vendorBasedDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNum,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ASN_MULTI_SKU_ENABLED,
            false))
        .thenReturn(true);
    doNothing().when(nimRdsService).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    doNothing()
        .when(rdcInstructionUtils)
        .populateOpenAndReceivedQtyInDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString());
    doReturn(rdcVendorValidator)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            facilityNum, ReceivingConstants.VENDOR_VALIDATOR, VendorValidator.class);
    doReturn(multiSkuService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            facilityNum, ReceivingConstants.ASN_MULTISKU_HANDLER, MultiSkuService.class);

    when(asnReceivingAuditLogger.isVendorEnabledForAsnReceiving(
            any(DeliveryDocument.class), any(InstructionRequest.class)))
        .thenReturn(true);

    InstructionResponse multiSkuResponse = new InstructionResponseImplNew();
    Instruction dummyInstruction = new Instruction();
    dummyInstruction.setInstructionCode(ReceivingConstants.MULTI_SKU_INST_CODE);
    dummyInstruction.setInstructionMsg(ReceivingConstants.MULTI_SKU_INST_MSG);
    multiSkuResponse.setInstruction(dummyInstruction);

    InstructionRequest instructionRequest =
        MockInstructionRequest.getSSCCInstructionRequestForDsdc();
    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.RDS_DSDC_RECEIVE_VALIDATION_ERROR,
                String.format(
                    ReceivingException.RDS_DSDC_RECEIVE_VALIDATION_ERROR_MSG,
                    instructionRequest.getSscc(),
                    "ASN already received"),
                instructionRequest.getSscc(),
                "ASN already received"))
        .when(rdcDsdcService)
        .createInstructionForDSDCReceiving(any(InstructionRequest.class), any(HttpHeaders.class));

    InstructionResponse instructionResponse =
        rdcInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), headers);

    assertNull(instructionResponse);

    verify(rdcDeliveryService, times(0))
        .findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(0)).validateAndProcessGdmDeliveryDocuments(anyList(), any());
    verify(rdcInstructionUtils, times(0)).hasMoreUniqueItems(anyList());
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_FREIGHT_IDENTIFICATION_FEATURE_ENABLED,
            false);
    verify(nimRdsService, times(0)).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(0))
        .populateOpenAndReceivedQtyInDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString());
    verify(multiSkuService, times(0))
        .handleMultiSku(
            anyBoolean(),
            any(InstructionRequest.class),
            any(InstructionResponse.class),
            any(Instruction.class));
    verify(rdcDsdcService, times(1))
        .createInstructionForDSDCReceiving(any(InstructionRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void
      testServeInstructionRequest_SSCCIsScanned_DsdcNotFoundInRds_ContinueToGDM_VendorASNReceivingSuccess()
          throws ReceivingException, IOException {
    File resource =
        new ClassPathResource("GdmMappedPackResponseToDeliveryDocumentLinesMultiSku.json")
            .getFile();
    String mockDeliveryDocumentsResponse = new String(Files.readAllBytes(resource.toPath()));

    when(rdcDeliveryService.findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class)))
        .thenReturn(
            Arrays.asList(gson.fromJson(mockDeliveryDocumentsResponse, DeliveryDocument[].class)));
    when(rdcManagedConfig.isAsnReceivingEnabled()).thenReturn(true);
    when(rdcInstructionUtils.hasMoreUniqueItems(anyList())).thenReturn(true);
    when(rdcManagedConfig.getInternalAsnSourceTypes()).thenReturn(new ArrayList<>());
    when(rdcVendorValidator.isAsnReceivingEnabled()).thenReturn(true);
    when(rdcVendorValidator.isPilotVendorForAsnReceiving(anyString())).thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_FREIGHT_IDENTIFICATION_FEATURE_ENABLED,
            false))
        .thenReturn(false);
    doReturn(vendorBasedDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNum,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ASN_MULTI_SKU_ENABLED,
            false))
        .thenReturn(true);
    doNothing().when(nimRdsService).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    doNothing()
        .when(rdcInstructionUtils)
        .populateOpenAndReceivedQtyInDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString());
    doReturn(rdcVendorValidator)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            facilityNum, ReceivingConstants.VENDOR_VALIDATOR, VendorValidator.class);
    doReturn(multiSkuService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            facilityNum, ReceivingConstants.ASN_MULTISKU_HANDLER, MultiSkuService.class);

    when(asnReceivingAuditLogger.isVendorEnabledForAsnReceiving(
            any(DeliveryDocument.class), any(InstructionRequest.class)))
        .thenReturn(true);
    when(rdcDsdcService.createInstructionForDSDCReceiving(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(new InstructionResponseImplNew());
    InstructionResponse multiSkuResponse = new InstructionResponseImplNew();
    Instruction dummyInstruction = new Instruction();
    dummyInstruction.setInstructionCode(ReceivingConstants.MULTI_SKU_INST_CODE);
    dummyInstruction.setInstructionMsg(ReceivingConstants.MULTI_SKU_INST_MSG);
    multiSkuResponse.setInstruction(dummyInstruction);

    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.add(RdcConstants.WFT_LOCATION_ID, "122");
    httpHeaders.add(RdcConstants.WFT_LOCATION_TYPE, "DOOR-122");
    httpHeaders.add(RdcConstants.WFT_SCC_CODE, "34332323");
    httpHeaders.add(RdcConstants.DA_RECEIVING_CAPABILITY, "true");
    InstructionResponse instructionResponse =
        rdcInstructionService.serveInstructionRequest(
            gson.toJson(MockInstructionRequest.getSSCCInstructionRequestForDsdc()), httpHeaders);

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getDeliveryDocuments());

    verify(rdcDsdcService, times(1))
        .createInstructionForDSDCReceiving(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(rdcDeliveryService, times(1))
        .findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1)).validateAndProcessGdmDeliveryDocuments(anyList(), any());
    verify(rdcInstructionUtils, times(1)).hasMoreUniqueItems(anyList());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_FREIGHT_IDENTIFICATION_FEATURE_ENABLED,
            false);
    verify(nimRdsService, times(1)).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1))
        .populateOpenAndReceivedQtyInDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString());
    verify(multiSkuService, times(1))
        .handleMultiSku(
            anyBoolean(),
            any(InstructionRequest.class),
            any(InstructionResponse.class),
            any(Instruction.class));
  }

  @Test
  private void testCheckIfAtlasConvertedItemSSTKItem() throws IOException, ReceivingException {
    String featureType = null;
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    when(rdcInstructionUtils.filterSSTKDeliveryDocuments(anyList())).thenReturn(deliveryDocuments);
    doNothing()
        .when(rdcInstructionUtils)
        .validateAtlasConvertedItems(anyList(), any(HttpHeaders.class));
    when(rdcInstructionUtils.enableAtlasConvertedItemValidationForSSTKReceiving()).thenReturn(true);
    doNothing().when(rdcInstructionUtils).validateAtlasConvertedItems(any(), any());
    rdcInstructionService.checkIfAtlasConvertedItem(
        featureType, deliveryDocuments, MockHttpHeaders.getHeaders());
    verify(rdcInstructionUtils, times(1)).filterSSTKDeliveryDocuments(anyList());
    verify(rdcInstructionUtils, times(1))
        .validateAtlasConvertedItems(anyList(), any(HttpHeaders.class));
  }

  @Test
  private void testCheckIfAtlasConvertedItemDAItemByCCMConfig()
      throws IOException, ReceivingException {
    String featureType = "SCAN_TO_PRINT";
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CC");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
            false))
        .thenReturn(true);
    doNothing().when(rdcReceivingUtils).overrideItemProperties(any(DeliveryDocument.class));
    when(rdcManagedConfig.getDaAtlasItemList())
        .thenReturn(
            Collections.singletonList(
                deliveryDocuments
                    .get(0)
                    .getDeliveryDocumentLines()
                    .get(0)
                    .getItemNbr()
                    .toString()));
    when(rdcManagedConfig.getDaAtlasItemEnabledPackHandlingCode())
        .thenReturn(
            Collections.singletonList(
                deliveryDocuments
                    .get(0)
                    .getDeliveryDocumentLines()
                    .get(0)
                    .getAdditionalInfo()
                    .getItemPackAndHandlingCode()));

    rdcInstructionService.checkIfAtlasConvertedItem(
        featureType, deliveryDocuments, MockHttpHeaders.getHeaders());

    assertTrue(
        deliveryDocuments
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .isAtlasConvertedItem());

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
            false);
    verify(rdcManagedConfig, times(0)).getDaAtlasItemList();
    verify(rdcManagedConfig, times(1)).getDaAtlasItemEnabledPackHandlingCode();
  }

  @Test
  private void testCheckIfAtlasConvertedItemDAItemByItemConfigService()
      throws IOException, ReceivingException {
    String featureType = "SCAN_TO_PRINT";
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CC");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ITEM_CONFIG_ENABLED_ON_ATLAS_DA_ITEMS,
            false))
        .thenReturn(true);
    doNothing().when(rdcReceivingUtils).overrideItemProperties(any(DeliveryDocument.class));
    when(rdcInstructionUtils.isSinglePoAndPoLine(anyList())).thenReturn(true);
    doNothing()
        .when(rdcInstructionUtils)
        .validateAtlasConvertedItems(anyList(), any(HttpHeaders.class));
    when(rdcManagedConfig.getDaAtlasItemEnabledPackHandlingCode())
        .thenReturn(
            Collections.singletonList(
                deliveryDocuments
                    .get(0)
                    .getDeliveryDocumentLines()
                    .get(0)
                    .getAdditionalInfo()
                    .getItemPackAndHandlingCode()));

    rdcInstructionService.checkIfAtlasConvertedItem(
        featureType, deliveryDocuments, MockHttpHeaders.getHeaders());

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
            false);
    verify(rdcManagedConfig, times(1)).getDaAtlasItemEnabledPackHandlingCode();
    verify(rdcInstructionUtils, times(1)).isSinglePoAndPoLine(anyList());
    verify(rdcInstructionUtils, times(1))
        .validateAtlasConvertedItems(anyList(), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ITEM_CONFIG_ENABLED_ON_ATLAS_DA_ITEMS,
            false);
  }

  @Test
  private void testCheckIfAtlasConvertedItemForMultipleDAItems()
      throws IOException, ReceivingException {
    String featureType = "SCAN_TO_PRINT";
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForMultipleDA();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CC");
    deliveryDocuments
        .get(1)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CC");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
            false))
        .thenReturn(true);
    doNothing().when(rdcReceivingUtils).overrideItemProperties(any(DeliveryDocument.class));
    when(rdcManagedConfig.getDaAtlasItemList())
        .thenReturn(
            Collections.singletonList(
                deliveryDocuments
                    .get(0)
                    .getDeliveryDocumentLines()
                    .get(0)
                    .getItemNbr()
                    .toString()));
    when(rdcManagedConfig.getDaAtlasItemEnabledPackHandlingCode())
        .thenReturn(
            Collections.singletonList(
                deliveryDocuments
                    .get(0)
                    .getDeliveryDocumentLines()
                    .get(0)
                    .getAdditionalInfo()
                    .getItemPackAndHandlingCode()));

    rdcInstructionService.checkIfAtlasConvertedItem(
        featureType, deliveryDocuments, MockHttpHeaders.getHeaders());

    assertTrue(
        deliveryDocuments
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .isAtlasConvertedItem());
    assertTrue(
        deliveryDocuments
            .get(1)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .isAtlasConvertedItem());

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
            false);
    verify(rdcManagedConfig, times(0)).getDaAtlasItemList();
    verify(rdcManagedConfig, times(2)).getDaAtlasItemEnabledPackHandlingCode();
  }

  @Test
  private void testCheckIfAtlasConvertedItemForMultipleDAItems_ItemConfigService()
      throws IOException, ReceivingException {
    String featureType = "SCAN_TO_PRINT";
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForMultipleDA();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CC");
    deliveryDocuments
        .get(1)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CC");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CC");
    deliveryDocuments
        .get(1)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CC");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ITEM_CONFIG_ENABLED_ON_ATLAS_DA_ITEMS,
            false))
        .thenReturn(true);
    doNothing().when(rdcReceivingUtils).overrideItemProperties(any(DeliveryDocument.class));
    when(rdcInstructionUtils.isSinglePoAndPoLine(anyList())).thenReturn(false);
    doNothing()
        .when(rdcInstructionUtils)
        .validateAtlasConvertedItems(anyList(), any(HttpHeaders.class));
    when(rdcManagedConfig.getDaAtlasItemEnabledPackHandlingCode())
        .thenReturn(
            Collections.singletonList(
                deliveryDocuments
                    .get(0)
                    .getDeliveryDocumentLines()
                    .get(0)
                    .getAdditionalInfo()
                    .getItemPackAndHandlingCode()));

    rdcInstructionService.checkIfAtlasConvertedItem(
        featureType, deliveryDocuments, MockHttpHeaders.getHeaders());

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
            false);
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
            false);
    verify(rdcInstructionUtils, times(1)).isSinglePoAndPoLine(anyList());
    verify(rdcInstructionUtils, times(1))
        .validateAtlasConvertedItems(anyList(), any(HttpHeaders.class));
  }

  @Test
  private void testCheckIfAtlasConvertedItemForMixedPOs_ByCCMConfigs()
      throws IOException, ReceivingException {
    String featureType = "SCAN_TO_PRINT";
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForMixedPO();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CC");
    deliveryDocuments
        .get(1)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CC");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ITEM_CONFIG_ENABLED_ON_ATLAS_DA_ITEMS,
            false))
        .thenReturn(false);
    doNothing().when(rdcReceivingUtils).overrideItemProperties(any(DeliveryDocument.class));
    when(rdcManagedConfig.getDaAtlasItemList())
        .thenReturn(
            Collections.singletonList(
                deliveryDocuments
                    .get(0)
                    .getDeliveryDocumentLines()
                    .get(0)
                    .getItemNbr()
                    .toString()));
    when(rdcManagedConfig.getDaAtlasItemEnabledPackHandlingCode())
        .thenReturn(
            Collections.singletonList(
                deliveryDocuments
                    .get(0)
                    .getDeliveryDocumentLines()
                    .get(0)
                    .getAdditionalInfo()
                    .getItemPackAndHandlingCode()));

    rdcInstructionService.checkIfAtlasConvertedItem(
        featureType, deliveryDocuments, MockHttpHeaders.getHeaders());

    assertFalse(
        deliveryDocuments
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .isAtlasConvertedItem());
    assertTrue(
        deliveryDocuments
            .get(1)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .isAtlasConvertedItem());

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
            false);
    verify(rdcManagedConfig, times(0)).getDaAtlasItemList();
    verify(rdcManagedConfig, times(1)).getDaAtlasItemEnabledPackHandlingCode();
  }

  @Test
  private void
      isAtlasConvertedDaItem_ValidateAtlasItemByHandlingCodeButItemConfigNeededForBreakPackConveyable()
          throws IOException, ReceivingException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_ITEM_CONVERSION_ENABLED,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ITEM_CONFIG_ENABLED_ON_ATLAS_DA_ITEMS,
            false))
        .thenReturn(false);
    doNothing()
        .when(rdcInstructionUtils)
        .validateAtlasConvertedItems(anyList(), any(HttpHeaders.class));
    when(rdcInstructionUtils.isValidPackHandlingCodeForItemConfigApi(anyList())).thenReturn(true);
    rdcInstructionService.isAtlasConvertedDaItem(deliveryDocuments, MockHttpHeaders.getHeaders());

    verify(rdcInstructionUtils, times(1))
        .validateAtlasConvertedItems(anyList(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1)).isValidPackHandlingCodeForItemConfigApi(anyList());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testServeInstructionRequest_SsccIsScanned_DSDCPackAlreadyReceived()
      throws ReceivingException, IOException {
    when(rdcManagedConfig.isAsnReceivingEnabled()).thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_FREIGHT_IDENTIFICATION_FEATURE_ENABLED,
            false))
        .thenReturn(false);
    doReturn(vendorBasedDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNum,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ASN_MULTI_SKU_ENABLED,
            false))
        .thenReturn(true);
    doNothing().when(nimRdsService).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    doNothing()
        .when(rdcInstructionUtils)
        .populateOpenAndReceivedQtyInDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString());
    doReturn(rdcVendorValidator)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            facilityNum, ReceivingConstants.VENDOR_VALIDATOR, VendorValidator.class);
    doReturn(multiSkuService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            facilityNum, ReceivingConstants.ASN_MULTISKU_HANDLER, MultiSkuService.class);

    when(asnReceivingAuditLogger.isVendorEnabledForAsnReceiving(
            any(DeliveryDocument.class), any(InstructionRequest.class)))
        .thenReturn(true);

    InstructionResponse multiSkuResponse = new InstructionResponseImplNew();
    Instruction dummyInstruction = new Instruction();
    dummyInstruction.setInstructionCode(ReceivingConstants.MULTI_SKU_INST_CODE);
    dummyInstruction.setInstructionMsg(ReceivingConstants.MULTI_SKU_INST_MSG);
    multiSkuResponse.setInstruction(dummyInstruction);

    InstructionRequest instructionRequest =
        MockInstructionRequest.getSSCCInstructionRequestForDsdc();
    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.RDS_DSDC_RECEIVE_VALIDATION_ERROR,
                String.format(
                    ReceivingException.RDS_DSDC_RECEIVE_VALIDATION_ERROR_MSG,
                    instructionRequest.getSscc(),
                    "ASN already received"),
                instructionRequest.getSscc(),
                "ASN already received"))
        .when(rdcDsdcService)
        .createInstructionForDSDCReceiving(any(InstructionRequest.class), any(HttpHeaders.class));

    InstructionResponse instructionResponse =
        rdcInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), headers);

    assertNull(instructionResponse);

    verify(rdcDeliveryService, times(0))
        .findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(0)).validateAndProcessGdmDeliveryDocuments(anyList(), any());
    verify(rdcInstructionUtils, times(0)).hasMoreUniqueItems(anyList());
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_FREIGHT_IDENTIFICATION_FEATURE_ENABLED,
            false);
    verify(nimRdsService, times(0)).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(0))
        .populateOpenAndReceivedQtyInDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString());
    verify(multiSkuService, times(0))
        .handleMultiSku(
            anyBoolean(),
            any(InstructionRequest.class),
            any(InstructionResponse.class),
            any(Instruction.class));
    verify(rdcDsdcService, times(1))
        .createInstructionForDSDCReceiving(any(InstructionRequest.class), any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testServeInstructionRequest_SsccIsScanned_DSDCAuditPack_InstructionExists()
      throws ReceivingException, IOException {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.add(RdcConstants.WFT_LOCATION_ID, "122");
    httpHeaders.add(RdcConstants.WFT_LOCATION_TYPE, "DOOR-122");
    httpHeaders.add(RdcConstants.WFT_SCC_CODE, "34332323");
    httpHeaders.add(RdcConstants.DA_RECEIVING_CAPABILITY, "true");
    when(rdcManagedConfig.isAsnReceivingEnabled()).thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_FREIGHT_IDENTIFICATION_FEATURE_ENABLED,
            false))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_DSDC_SSCC_PACKS_AVAILABLE_IN_GDM,
            false))
        .thenReturn(true);
    doReturn(vendorBasedDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            facilityNum,
            ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER,
            DeliveryDocumentsSearchHandler.class);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ASN_MULTI_SKU_ENABLED,
            false))
        .thenReturn(true);
    doNothing().when(nimRdsService).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    doNothing()
        .when(rdcInstructionUtils)
        .populateOpenAndReceivedQtyInDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString());
    doReturn(rdcVendorValidator)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            facilityNum, ReceivingConstants.VENDOR_VALIDATOR, VendorValidator.class);
    doReturn(multiSkuService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            facilityNum, ReceivingConstants.ASN_MULTISKU_HANDLER, MultiSkuService.class);

    when(asnReceivingAuditLogger.isVendorEnabledForAsnReceiving(
            any(DeliveryDocument.class), any(InstructionRequest.class)))
        .thenReturn(true);
    when(rdcInstructionUtils.checkIfDsdcInstructionAlreadyExists(any(InstructionRequest.class)))
        .thenReturn(true);
    when(rdcDsdcService.createInstructionForDSDCReceiving(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(getMockDsdcAuditPackInstructionReponse());
    InstructionResponse multiSkuResponse = new InstructionResponseImplNew();
    Instruction dummyInstruction = new Instruction();
    dummyInstruction.setInstructionCode(ReceivingConstants.MULTI_SKU_INST_CODE);
    dummyInstruction.setInstructionMsg(ReceivingConstants.MULTI_SKU_INST_MSG);
    multiSkuResponse.setInstruction(dummyInstruction);

    InstructionResponse instructionResponse =
        rdcInstructionService.serveInstructionRequest(
            gson.toJson(MockInstructionRequest.getSSCCInstructionRequestForDsdc()), httpHeaders);

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.DSDC_RECEIVING.getInstructionCode());

    verify(rdcDeliveryService, times(0))
        .findDeliveryDocumentBySSCCWithShipmentLinking(
            anyString(), anyString(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(0)).validateAndProcessGdmDeliveryDocuments(anyList(), any());
    verify(rdcInstructionUtils, times(0)).hasMoreUniqueItems(anyList());
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_FREIGHT_IDENTIFICATION_FEATURE_ENABLED,
            false);
    verify(nimRdsService, times(0)).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(0))
        .populateOpenAndReceivedQtyInDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString());
    verify(multiSkuService, times(0))
        .handleMultiSku(
            anyBoolean(),
            any(InstructionRequest.class),
            any(InstructionResponse.class),
            any(Instruction.class));
    verify(rdcDsdcService, times(1))
        .createInstructionForDSDCReceiving(any(InstructionRequest.class), any(HttpHeaders.class));
  }

  private InstructionResponse getInstructionResponseForSingleSSTK() throws IOException {
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    instructionResponse.setInstruction(getInstructions());
    instructionResponse.setDeliveryDocuments(MockDeliveryDocuments.getDeliveryDocumentsForSSTK());
    return instructionResponse;
  }

  private InstructionResponse getInstructionResponseForSingleDA() throws IOException {
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    instructionResponse.setInstruction(getInstructionForDAReceiving());
    instructionResponse.setDeliveryDocuments(
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA());
    return instructionResponse;
  }

  private InstructionResponse getInstructionResponseForSingleSSTK_AtlasConvertedItem()
      throws IOException {
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    instructionResponse.setInstruction(getInstructions());
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    deliveryDocumentList
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    instructionResponse.setDeliveryDocuments(deliveryDocumentList);
    return instructionResponse;
  }

  private InstructionResponse getInstructionResponseForMultiSSTKPO_AtlasConvertedItems()
      throws IOException {
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    instructionResponse.setInstruction(getInstructions());
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK_AtlasConvertedItem();
    deliveryDocumentList
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    instructionResponse.setDeliveryDocuments(deliveryDocumentList);
    return instructionResponse;
  }

  private Instruction getInstructions() {
    Instruction instruction = new Instruction();
    instruction.setGtin("2323232323");
    instruction.setProviderId(RdcConstants.PROVIDER_ID);
    instruction.setInstructionMsg(RdcInstructionType.SSTK_UPC_RECEIVING.getInstructionMsg());
    instruction.setInstructionCode(RdcInstructionType.SSTK_UPC_RECEIVING.getInstructionCode());
    instruction.setCreateTs(new Date());
    instruction.setCreateUserId("sysadmin");
    return instruction;
  }

  private Instruction getInstructionForDAReceiving() {
    Instruction instruction = new Instruction();
    instruction.setGtin("2323232323");
    instruction.setProviderId(RdcConstants.PROVIDER_ID);
    instruction.setInstructionMsg(
        RdcInstructionType.DA_WORK_STATION_SCAN_TO_PRINT_RECEIVING.getInstructionMsg());
    instruction.setInstructionCode(
        RdcInstructionType.DA_WORK_STATION_SCAN_TO_PRINT_RECEIVING.getInstructionCode());
    instruction.setCreateTs(new Date());
    instruction.setCreateUserId("sysadmin");
    return instruction;
  }

  private Instruction getDsdcInstructions() {
    Instruction instruction = new Instruction();
    instruction.setGtin("2323232323");
    instruction.setProviderId(RdcConstants.PROVIDER_ID);
    instruction.setInstructionMsg(RdcInstructionType.DSDC_RECEIVING.getInstructionMsg());
    instruction.setInstructionCode(RdcInstructionType.DSDC_RECEIVING.getInstructionCode());
    instruction.setCreateTs(new Date());
    instruction.setCreateUserId("sysadmin");
    return instruction;
  }

  private Instruction getDsdcAuditInstructions() {
    Instruction instruction = new Instruction();
    instruction.setGtin("2323232323");
    instruction.setProviderId(RdcConstants.PROVIDER_ID);
    instruction.setInstructionMsg(RdcInstructionType.DSDC_RECEIVING.getInstructionMsg());
    instruction.setInstructionCode(RdcInstructionType.DSDC_RECEIVING.getInstructionCode());
    instruction.setCreateTs(new Date());
    instruction.setCreateUserId("sysadmin");
    return instruction;
  }

  private InstructionResponse getMockDsdcSuccessInstructionReponse() {
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    instructionResponse.setInstruction(getDsdcInstructions());
    return instructionResponse;
  }

  private InstructionResponse getMockDsdcAuditPackInstructionReponse() {
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    instructionResponse.setInstruction(getDsdcAuditInstructions());
    return instructionResponse;
  }

  @Test
  public void
      testServeInstructionRequestHasDeliveryDocumentsReturnsInstructionAndNGRServicesEnabled()
          throws ReceivingException, IOException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithLimitedQtyCompliance();
    when(rdcInstructionUtils.filterSSTKDeliveryDocuments(anyList()))
        .thenReturn(instructionRequest.getDeliveryDocuments());
    doNothing()
        .when(regulatedItemService)
        .updateVendorComplianceItem(any(VendorCompliance.class), anyString());
    doNothing().when(ngrRestApiClient).updateHazmatVerificationTsInItemCache(any(), any());
    doNothing()
        .when(rdcItemServiceHandler)
        .updateItemRejectReason(
            nullable(RejectReason.class), any(ItemOverrideRequest.class), any(HttpHeaders.class));
    when(rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class)))
        .thenReturn(getInstructionResponseForSingleSSTK());
    when(nimRdsService.getReceivedQtyByDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString()))
        .thenReturn(receivedQuantityResponseFromRDS);
    when(rdcInstructionUtils.autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString()))
        .thenReturn(new Pair<>(instructionRequest.getDeliveryDocuments().get(0), 10L));
    when(rdcInstructionUtils.checkAllSSTKPoFulfilled(
            anyList(), any(InstructionRequest.class), any(ReceivedQuantityResponseFromRDS.class)))
        .thenReturn(instructionRequest.getDeliveryDocuments());
    when(rdcInstructionUtils.createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class)))
        .thenReturn(getInstructionResponseForSingleSSTK());
    when(rdcReceivingUtils.isNGRServicesEnabled()).thenReturn(true);
    InstructionResponse instructionResponse =
        rdcInstructionService.serveInstructionRequest(gson.toJson(instructionRequest), headers);

    assertNotNull(instructionResponse);
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(instructionResponse.getDeliveryDocuments().size(), 1);
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.SSTK_UPC_RECEIVING.getInstructionCode());
    assertFalse(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .isLithiumIonVerificationRequired());
    assertFalse(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .isLimitedQtyVerificationRequired());
    assertFalse(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getIsDefaultTiHiUsed());
    verify(rdcInstructionUtils, times(1)).filterSSTKDeliveryDocuments(anyList());
    verify(rdcInstructionUtils, times(1))
        .checkAllSSTKPoFulfilled(
            anyList(), any(InstructionRequest.class), any(ReceivedQuantityResponseFromRDS.class));
    verify(rdcItemServiceHandler, times(1))
        .updateItemRejectReason(
            nullable(RejectReason.class), any(ItemOverrideRequest.class), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1))
        .createInstructionForStapleStockUpcReceiving(
            any(InstructionRequest.class), anyLong(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1))
        .autoSelectPoPoLine(
            anyList(), any(ReceivedQuantityResponseFromRDS.class), anyInt(), anyString());
    verify(rdcReceivingUtils, times(1)).isNGRServicesEnabled();
  }
}
