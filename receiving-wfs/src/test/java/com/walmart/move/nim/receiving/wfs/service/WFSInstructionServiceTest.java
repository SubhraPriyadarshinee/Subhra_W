package com.walmart.move.nim.receiving.wfs.service;

import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClient;
import com.walmart.move.nim.receiving.core.common.DeliveryDocumentHelper;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.InstructionError;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.validators.DeliveryValidator;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.PrintJob;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.docktag.CreateDockTagRequest;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.repositories.PrintJobRepository;
import com.walmart.move.nim.receiving.core.repositories.ReceiptCustomRepository;
import com.walmart.move.nim.receiving.core.repositories.ReceiptRepository;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.wfs.config.WFSManagedConfig;
import com.walmart.move.nim.receiving.wfs.constants.WFSConstants;
import com.walmart.move.nim.receiving.wfs.mock.data.MockInstruction;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.mockito.*;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class WFSInstructionServiceTest {

  @Spy @InjectMocks private WFSInstructionService wfsInstructionService;
  @InjectMocks protected InstructionHelperService instructionHelperService;
  @InjectMocks private ReceiptService receiptService;
  @InjectMocks private PrintJobService printJobService;
  @InjectMocks private InstructionPersisterService instructionPersisterService;
  @InjectMocks private ContainerService containerService;
  @Spy private WFSInstructionUtils wfsInstructionUtils;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private DefaultDeliveryDocumentsSearchHandler defaultDeliveryDocumentsSearchHandler;
  @Mock private DeliveryValidator deliveryValidator;
  @Mock private AppConfig appConfig;
  @Mock private WFSManagedConfig wfsManagedConfig;
  @Mock protected DeliveryDocumentHelper deliveryDocumentHelper;
  @Mock protected InstructionHelperService instructionHelperServiceMock;
  @Mock private InstructionRepository instructionRepository;
  @Mock private InstructionPersisterService instructionPersisterServiceMock;
  @Spy protected WFSInstructionHelperService wfsInstructionHelperService;
  @Mock private ReceiptCustomRepository receiptCustomRepository;
  @Mock private ContainerService containerService1;
  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private ReceiptRepository receiptRepository;
  @Mock private PrintJobRepository printJobRepository;
  @Mock private InstructionService instructionService;
  @Mock private FdeService fdeService;
  @Mock private RetryableFDEService retryableFDEService;
  @Mock private DeliveryServiceImpl deliveryService;
  @Mock private DeliveryMetaDataService deliveryMetaDataService;
  @Mock private DefaultDeliveryDocumentSelector defaultDeliveryDocumentSelector;
  @Mock private PrintingAndLabellingService printingAndLabellingService;
  @Mock private RegulatedItemService regulatedItemService;
  @Mock private WFSLabelIdProcessor wfsLabelIdProcessor;
  @Mock private GDMRestApiClient gdmRestApiClient;
  private HttpHeaders headers = MockHttpHeaders.getHeaders();
  private Gson gson;
  private Container container;

  String inputPayloadWithoutDeliveryDocumentFilePath =
      "../receiving-test/src/main/resources/json/wfs/wfsInputPayloadWithoutDeliveryDocument.json";
  String fdeCreateContainerResponseFilePath =
      "../receiving-test/src/main/resources/json/wfs/wfsOPResponseForCasePackBuildInstruction.json";
  String fdeCreateContainerResponseFilePathBreakPack =
      "../receiving-test/src/main/resources/json/wfs/wfsOPResponseForBreakPackBuildInstruction.json";
  String inputPayloadWithDeliveryDocumentFilePath =
      "../receiving-test/src/main/resources/json/wfs/wfsInputPayloadWithDeliveryDocument.json";
  String inputPayloadWithDeliveryDocumentHazmatFilePath =
      "../receiving-test/src/main/resources/json/wfs/wfsInputPayloadWithDeliveryDocumentHazmat.json";

  String inputPayloadWithMultipleDeliveryDocumentLinesFilePath =
      "../receiving-test/src/main/resources/json/wfs/wfsInputPayloadWithMultipleDeliveryDocumentLines.json";

  String fdeCreateContainerRequestForSingleSKUFilePath =
      "../receiving-test/src/main/resources/json/wfs/wfsOPRequestForSingleSKU.json";

  String inputPayloadWithDeliveryDocumentForPalletReceivingFilePath =
      "../receiving-test/src/main/resources/json/wfs/palletReceiving/wfsInputPayloadWithDeliveryDocumentForPalletReceiving.json";

  String inputGDMLpnDetailsResponseFilePath =
      "../receiving-test/src/main/resources/json/GdmLpnDetailsResponse.json";
  String inputPayloadWithDeliveryDocument =
      getJSONStringResponse(inputPayloadWithDeliveryDocumentFilePath);
  String inputPayloadWithDeliveryDocumentHazmat =
      getJSONStringResponse(inputPayloadWithDeliveryDocumentHazmatFilePath);

  String inputPayloadWithMultipleDeliveryDocumentLines =
      getJSONStringResponse(inputPayloadWithMultipleDeliveryDocumentLinesFilePath);

  String inputPayloadWithoutDeliveryDocument =
      getJSONStringResponse(inputPayloadWithoutDeliveryDocumentFilePath);
  String fdeCreateContainerResponseString =
      getJSONStringResponse(fdeCreateContainerResponseFilePath);
  String fdeCreateContainerResponseStringBreakPack =
      getJSONStringResponse(fdeCreateContainerResponseFilePathBreakPack);

  String fdeCreateContainerRequestString =
      getJSONStringResponse(fdeCreateContainerRequestForSingleSKUFilePath);

  String inputPayloadWithDeliveryDocumentForPalletReceiving =
      getJSONStringResponse(inputPayloadWithDeliveryDocumentForPalletReceivingFilePath);

  String inputGDMLpnDetailsResponse =
      getResponseFromJSONFilePath(inputGDMLpnDetailsResponseFilePath);

  String fcNumberToNameMapping;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(6280);
    gson = new Gson();
    ReflectionTestUtils.setField(wfsInstructionService, "gson", gson);
    ReflectionTestUtils.setField(wfsInstructionHelperService, "gson", gson);
    ReflectionTestUtils.setField(
        wfsInstructionService, "instructionHelperService", instructionHelperService);
    ReflectionTestUtils.setField(
        wfsInstructionService, "instructionPersisterService", instructionPersisterService);
    ReflectionTestUtils.setField(
        wfsInstructionService, "wfsInstructionHelperService", wfsInstructionHelperService);
    ReflectionTestUtils.setField(wfsInstructionService, "wfsManagedConfig", wfsManagedConfig);

    ReflectionTestUtils.setField(instructionPersisterService, "containerService", containerService);
    ReflectionTestUtils.setField(instructionPersisterService, "receiptService", receiptService);
    ReflectionTestUtils.setField(instructionPersisterService, "printJobService", printJobService);

    ReflectionTestUtils.setField(instructionHelperService, "receiptService", receiptService);
    ReflectionTestUtils.setField(instructionHelperService, "configUtils", configUtils);
    ReflectionTestUtils.setField(instructionHelperService, "deliveryService", deliveryService);
    ReflectionTestUtils.setField(
        wfsInstructionUtils, "instructionPersisterService", instructionPersisterService);
    ReflectionTestUtils.setField(
        wfsInstructionUtils, "tenantSpecificConfigReader", tenantSpecificConfigReader);

    ReflectionTestUtils.setField(wfsInstructionHelperService, "configUtils", configUtils);
    ReflectionTestUtils.setField(
        wfsInstructionHelperService, "instructionPersisterService", instructionPersisterService);
    ReflectionTestUtils.setField(wfsInstructionHelperService, "containerService", containerService);
    ReflectionTestUtils.setField(wfsInstructionHelperService, "receiptService", receiptService);
    ReflectionTestUtils.setField(wfsInstructionHelperService, "printJobService", printJobService);
    ReflectionTestUtils.setField(wfsInstructionHelperService, "wfsManagedConfig", wfsManagedConfig);
    ReflectionTestUtils.setField(
        containerService, AbstractContainerService.class, "configUtils", configUtils, null);
    ReflectionTestUtils.setField(wfsInstructionHelperService, "gdmRestApiClient", gdmRestApiClient);
    doReturn(defaultDeliveryDocumentsSearchHandler)
        .when(configUtils)
        .getConfiguredInstance(
            anyString(),
            eq(ReceivingConstants.DELIVERY_DOCUMENT_SEARCH_HANDLER),
            eq(DeliveryDocumentsSearchHandler.class));
    doReturn(deliveryMetaDataService)
        .when(configUtils)
        .getConfiguredInstance(
            anyString(),
            eq(ReceivingConstants.DELIVERY_METADATA_SERVICE),
            eq(DeliveryMetaDataService.class));
    doReturn(wfsLabelIdProcessor)
        .when(configUtils)
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.LABEL_ID_PROCESSOR), eq(LabelIdProcessor.class));
    doReturn(501).when(wfsLabelIdProcessor).getLabelId(anyString(), anyString());
    doReturn(Boolean.FALSE)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), eq(ReceivingConstants.KOTLIN_ENABLED), anyBoolean());
    doReturn(Boolean.FALSE)
        .when(configUtils)
        .getConfiguredFeatureFlag(
            eq("6280"), eq(WFSConstants.IS_RE_RECEIVING_CONTAINER_CHECK_ENABLED), anyBoolean());
    fcNumberToNameMapping =
        "{" + "\"7441\":\"ORD1\"," + "\"9208\":\"ORD2\"," + "\"6094\":\"ORD3\"" + "}";
    when(wfsManagedConfig.getFcNameMapping()).thenReturn(fcNumberToNameMapping);
  }

  @BeforeMethod
  public void reset() {
    Mockito.reset(defaultDeliveryDocumentsSearchHandler);
    Mockito.reset(fdeService);
    Mockito.reset(wfsInstructionService);
    Mockito.reset(instructionPersisterServiceMock);
    Mockito.reset(instructionRepository);
    Mockito.reset(receiptRepository);
    Mockito.reset(containerPersisterService);
    Mockito.reset(printJobRepository);
    Mockito.reset(instructionHelperServiceMock);
    Mockito.reset(containerService1);
    Mockito.reset(deliveryService);
    Mockito.reset(deliveryMetaDataService);
    Mockito.reset(defaultDeliveryDocumentSelector);
    Mockito.reset(printingAndLabellingService);
    Mockito.reset(regulatedItemService);
    Mockito.reset(tenantSpecificConfigReader);
    Mockito.reset(deliveryDocumentHelper);
    Mockito.reset(wfsInstructionHelperService);
    Mockito.reset(gdmRestApiClient);

    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.FDE_SERVICE_BEAN), eq(FdeService.class)))
        .thenReturn(fdeService);

    if ("instructionHelperServiceMock"
        .equals(
            ReflectionTestUtils.getField(wfsInstructionService, "instructionHelperService")
                .toString())) {
      // in case, the field instructionHelper service is set to mocked version,
      // set it back to @injectmocks version
      // tests like testGetDockTagInstruction set the instructionHelperSvc field to mock, and does
      // not set it back, this is useful there.
      // even if test fails, it will set back field so that other tests are not impacted.
      ReflectionTestUtils.setField(
          wfsInstructionService, "instructionHelperService", instructionHelperService);
    }
  }

  @Test
  public void testServeInstructionRequest_WithoutDeliveryDocument_SinglePOLine()
      throws ReceivingException {
    doReturn(getDefaultDeliveryDocumentList())
        .when(wfsInstructionService)
        .fetchDeliveryDocument(any(), any());
    doReturn(Boolean.FALSE)
        .when(configUtils)
        .isFeatureFlagEnabled(eq(ReceivingConstants.ENABLE_FILTER_CANCELLED_PO));
    doReturn(Boolean.TRUE).when(appConfig).isCheckDeliveryStatusReceivable();
    doReturn(getDefaultDeliveryDocumentList())
        .when(deliveryDocumentHelper)
        .updateDeliveryDocuments(any());
    doReturn(Boolean.FALSE)
        .when(configUtils)
        .isFeatureFlagEnabled(eq(ReceivingConstants.ENABLE_POPULATE_RECEIVED_QTY_PO));
    doReturn(Boolean.FALSE)
        .when(deliveryDocumentHelper)
        .updateVendorCompliance(any(DeliveryDocumentLine.class));
    doNothing().when(wfsInstructionHelperService).checkForPendingShelfContainers(any(), any());
    InstructionResponse instructionResponse =
        wfsInstructionService.serveInstructionRequest(inputPayloadWithoutDeliveryDocument, headers);

    verify(wfsInstructionService, never()).updateOpenQtyForEachPoPoline(any(), anyString());
    verify(wfsInstructionService, never())
        .createInstructionForUpcReceiving(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(wfsInstructionService, never())
        .completeInstructionForWFS(
            any(Instruction.class), any(InstructionRequest.class), any(HttpHeaders.class));
    verify(defaultDeliveryDocumentSelector, never()).autoSelectDeliveryDocumentLine(anyList());
    verify(deliveryService, never()).reOpenDelivery(anyLong(), any());
    verify(deliveryMetaDataService, never()).findAndUpdateDeliveryStatus(anyString(), any());

    assertEquals(instructionResponse.getDeliveryStatus(), DeliveryStatus.WRK.toString());
    assertNotEquals(instructionResponse.getDeliveryDocuments().size(), 0);
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        WFSConstants.WFS_QUANTITY_CAPTURE_INSTRUCTION_CODE);
  }

  @Test
  public void testServeInstructionRequest_WithoutDeliveryDocument_SinglePOLine_PopulateRcvQty()
      throws ReceivingException {
    doReturn(getDefaultDeliveryDocumentList())
        .when(wfsInstructionService)
        .fetchDeliveryDocument(any(), any());
    doReturn(Boolean.FALSE)
        .when(configUtils)
        .isFeatureFlagEnabled(eq(ReceivingConstants.ENABLE_FILTER_CANCELLED_PO));
    doReturn(Boolean.TRUE).when(appConfig).isCheckDeliveryStatusReceivable();
    doReturn(getDefaultDeliveryDocumentList())
        .when(deliveryDocumentHelper)
        .updateDeliveryDocuments(any());
    doReturn(Boolean.TRUE)
        .when(configUtils)
        .isFeatureFlagEnabled(eq(ReceivingConstants.ENABLE_POPULATE_RECEIVED_QTY_PO));
    doReturn(null)
        .when(wfsInstructionService)
        .updateOpenQtyForEachPoPoline(anyList(), eq(ReceivingConstants.Uom.EACHES));
    doReturn(Boolean.FALSE)
        .when(deliveryDocumentHelper)
        .updateVendorCompliance(any(DeliveryDocumentLine.class));
    doNothing().when(wfsInstructionHelperService).checkForPendingShelfContainers(any(), any());
    InstructionResponse instructionResponse =
        wfsInstructionService.serveInstructionRequest(inputPayloadWithoutDeliveryDocument, headers);

    verify(wfsInstructionService, times(1)).updateOpenQtyForEachPoPoline(any(), anyString());
    verify(wfsInstructionService, never())
        .createInstructionForUpcReceiving(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(wfsInstructionService, never())
        .completeInstructionForWFS(
            any(Instruction.class), any(InstructionRequest.class), any(HttpHeaders.class));
    verify(defaultDeliveryDocumentSelector, never()).autoSelectDeliveryDocumentLine(anyList());
    verify(deliveryService, never()).reOpenDelivery(anyLong(), any());
    verify(deliveryMetaDataService, never()).findAndUpdateDeliveryStatus(anyString(), any());

    assertEquals(instructionResponse.getDeliveryStatus(), DeliveryStatus.WRK.toString());
    assertNotEquals(instructionResponse.getDeliveryDocuments().size(), 0);
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        WFSConstants.WFS_QUANTITY_CAPTURE_INSTRUCTION_CODE);
  }

  @Test
  public void
      testServeInstructionRequest_WithoutDeliveryDocument_SinglePOLine_PopulateRcvQty_KotlinClient()
          throws ReceivingException {
    doReturn(getDefaultDeliveryDocumentList())
        .when(wfsInstructionService)
        .fetchDeliveryDocument(any(), any());
    doReturn(Boolean.FALSE)
        .when(configUtils)
        .isFeatureFlagEnabled(eq(ReceivingConstants.ENABLE_FILTER_CANCELLED_PO));
    doReturn(Boolean.TRUE).when(appConfig).isCheckDeliveryStatusReceivable();
    doReturn(getDefaultDeliveryDocumentList())
        .when(deliveryDocumentHelper)
        .updateDeliveryDocuments(any());
    doReturn(Boolean.TRUE)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), eq(ReceivingConstants.KOTLIN_ENABLED), anyBoolean());
    doReturn(Boolean.TRUE)
        .when(configUtils)
        .isFeatureFlagEnabled(eq(ReceivingConstants.ENABLE_POPULATE_RECEIVED_QTY_PO_MOBILE));
    doReturn(null)
        .when(wfsInstructionService)
        .updateOpenQtyForEachPoPoline(anyList(), eq(ReceivingConstants.Uom.EACHES));
    doReturn(Boolean.FALSE)
        .when(deliveryDocumentHelper)
        .updateVendorCompliance(any(DeliveryDocumentLine.class));
    doNothing().when(wfsInstructionHelperService).checkForPendingShelfContainers(any(), any());
    InstructionResponse instructionResponse =
        wfsInstructionService.serveInstructionRequest(inputPayloadWithoutDeliveryDocument, headers);

    verify(wfsInstructionService, times(1)).updateOpenQtyForEachPoPoline(any(), anyString());
    verify(wfsInstructionService, never())
        .createInstructionForUpcReceiving(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(wfsInstructionService, never())
        .completeInstructionForWFS(
            any(Instruction.class), any(InstructionRequest.class), any(HttpHeaders.class));
    verify(defaultDeliveryDocumentSelector, never()).autoSelectDeliveryDocumentLine(anyList());
    verify(deliveryService, never()).reOpenDelivery(anyLong(), any());
    verify(deliveryMetaDataService, never()).findAndUpdateDeliveryStatus(anyString(), any());

    assertEquals(instructionResponse.getDeliveryStatus(), DeliveryStatus.WRK.toString());
    assertNotEquals(instructionResponse.getDeliveryDocuments().size(), 0);
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        WFSConstants.WFS_QUANTITY_CAPTURE_INSTRUCTION_CODE);
  }

  @Test
  public void testServeInstructionRequest_WithoutDeliveryDocument_MultiPOLine_PopulateRcvQty()
      throws ReceivingException {
    List<DeliveryDocument> deliveryDocuments = getDefaultDeliveryDocumentList();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    DeliveryDocumentLine otherDeliveryDocumentLine =
        gson.fromJson(gson.toJson(deliveryDocumentLine), DeliveryDocumentLine.class);
    otherDeliveryDocumentLine.setPurchaseReferenceLineNumber(2);
    deliveryDocuments.get(0).getDeliveryDocumentLines().add(otherDeliveryDocumentLine);

    doReturn(deliveryDocuments).when(wfsInstructionService).fetchDeliveryDocument(any(), any());
    doReturn(Boolean.FALSE)
        .when(configUtils)
        .isFeatureFlagEnabled(eq(ReceivingConstants.ENABLE_FILTER_CANCELLED_PO));
    doReturn(Boolean.TRUE).when(appConfig).isCheckDeliveryStatusReceivable();
    doReturn(deliveryDocuments).when(deliveryDocumentHelper).updateDeliveryDocuments(any());
    doReturn(Boolean.TRUE)
        .when(configUtils)
        .isFeatureFlagEnabled(eq(ReceivingConstants.ENABLE_POPULATE_RECEIVED_QTY_PO));
    doReturn(null)
        .when(wfsInstructionService)
        .updateOpenQtyForEachPoPoline(anyList(), eq(ReceivingConstants.Uom.EACHES));
    doReturn(Boolean.FALSE)
        .when(deliveryDocumentHelper)
        .updateVendorCompliance(any(DeliveryDocumentLine.class));
    doNothing().when(wfsInstructionHelperService).checkForPendingShelfContainers(any(), any());
    InstructionResponse instructionResponse =
        wfsInstructionService.serveInstructionRequest(inputPayloadWithoutDeliveryDocument, headers);

    verify(wfsInstructionService, times(1)).updateOpenQtyForEachPoPoline(any(), anyString());
    verify(wfsInstructionService, never())
        .createInstructionForUpcReceiving(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(wfsInstructionService, never())
        .completeInstructionForWFS(
            any(Instruction.class), any(InstructionRequest.class), any(HttpHeaders.class));
    verify(defaultDeliveryDocumentSelector, times(1))
        .autoSelectDeliveryDocumentLine(ArgumentMatchers.<DeliveryDocument>anyList());
    verify(deliveryService, never()).reOpenDelivery(anyLong(), any());
    verify(deliveryMetaDataService, never()).findAndUpdateDeliveryStatus(anyString(), any());

    assertEquals(instructionResponse.getDeliveryStatus(), DeliveryStatus.WRK.toString());
    assertNotEquals(instructionResponse.getDeliveryDocuments().size(), 0);
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        WFSConstants.WFS_QUANTITY_CAPTURE_INSTRUCTION_CODE);
  }

  @Test
  public void
      testServeInstructionRequest_WithoutDeliveryDocument_MultiPOLine_PopulateRcvQty_Hazmat()
          throws ReceivingException {
    List<DeliveryDocument> deliveryDocuments = getDefaultDeliveryDocumentList();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    DeliveryDocumentLine otherDeliveryDocumentLine =
        gson.fromJson(gson.toJson(deliveryDocumentLine), DeliveryDocumentLine.class);
    otherDeliveryDocumentLine.setPurchaseReferenceLineNumber(2);
    deliveryDocuments.get(0).getDeliveryDocumentLines().add(otherDeliveryDocumentLine);

    doReturn(deliveryDocuments).when(wfsInstructionService).fetchDeliveryDocument(any(), any());
    doReturn(Boolean.FALSE)
        .when(configUtils)
        .isFeatureFlagEnabled(eq(ReceivingConstants.ENABLE_FILTER_CANCELLED_PO));
    doReturn(Boolean.TRUE).when(appConfig).isCheckDeliveryStatusReceivable();
    doReturn(deliveryDocuments).when(deliveryDocumentHelper).updateDeliveryDocuments(any());
    doReturn(Boolean.TRUE)
        .when(configUtils)
        .isFeatureFlagEnabled(eq(ReceivingConstants.ENABLE_POPULATE_RECEIVED_QTY_PO));
    doReturn(null)
        .when(wfsInstructionService)
        .updateOpenQtyForEachPoPoline(anyList(), eq(ReceivingConstants.Uom.EACHES));
    doReturn(Boolean.TRUE)
        .when(deliveryDocumentHelper)
        .updateVendorCompliance(any(DeliveryDocumentLine.class));
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(eq(ReceivingConstants.WFS_TM_HAZMAT_CHECK_ENABLED));
    doNothing().when(wfsInstructionHelperService).checkForPendingShelfContainers(any(), any());
    InstructionResponse instructionResponse =
        wfsInstructionService.serveInstructionRequest(inputPayloadWithoutDeliveryDocument, headers);

    verify(wfsInstructionService, times(1)).updateOpenQtyForEachPoPoline(any(), anyString());
    verify(wfsInstructionService, never())
        .createInstructionForUpcReceiving(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(wfsInstructionService, never())
        .completeInstructionForWFS(
            any(Instruction.class), any(InstructionRequest.class), any(HttpHeaders.class));
    verify(defaultDeliveryDocumentSelector, times(1))
        .autoSelectDeliveryDocumentLine(ArgumentMatchers.<DeliveryDocument>anyList());
    verify(deliveryService, never()).reOpenDelivery(anyLong(), any());
    verify(deliveryMetaDataService, never()).findAndUpdateDeliveryStatus(anyString(), any());

    assertEquals(instructionResponse.getDeliveryStatus(), DeliveryStatus.WRK.toString());
    assertNotEquals(instructionResponse.getDeliveryDocuments().size(), 0);
    assertNull(instructionResponse.getInstruction());

    assertTrue(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getIsHazmat());
  }

  @Test
  public void
      testServeInstructionRequest_WithoutDeliveryDocument_SinglePOLine_UpdateVendorComplianceTrue()
          throws ReceivingException {
    doReturn(getDefaultDeliveryDocumentList())
        .when(wfsInstructionService)
        .fetchDeliveryDocument(any(), any());
    doReturn(Boolean.FALSE)
        .when(configUtils)
        .isFeatureFlagEnabled(eq(ReceivingConstants.ENABLE_FILTER_CANCELLED_PO));
    doReturn(Boolean.TRUE).when(appConfig).isCheckDeliveryStatusReceivable();
    doReturn(getDefaultDeliveryDocumentList())
        .when(deliveryDocumentHelper)
        .updateDeliveryDocuments(any());
    doReturn(Boolean.TRUE)
        .when(configUtils)
        .isFeatureFlagEnabled(eq(ReceivingConstants.ENABLE_POPULATE_RECEIVED_QTY_PO));
    doReturn(null)
        .when(wfsInstructionService)
        .updateOpenQtyForEachPoPoline(anyList(), eq(ReceivingConstants.Uom.EACHES));
    doReturn(Boolean.TRUE)
        .when(deliveryDocumentHelper)
        .updateVendorCompliance(any(DeliveryDocumentLine.class));
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(eq(ReceivingConstants.WFS_TM_HAZMAT_CHECK_ENABLED));
    doNothing().when(wfsInstructionHelperService).checkForPendingShelfContainers(any(), any());
    InstructionResponse instructionResponse =
        wfsInstructionService.serveInstructionRequest(inputPayloadWithoutDeliveryDocument, headers);

    verify(wfsInstructionService, times(1)).updateOpenQtyForEachPoPoline(any(), anyString());
    verify(wfsInstructionService, never())
        .createInstructionForUpcReceiving(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(wfsInstructionService, never())
        .completeInstructionForWFS(
            any(Instruction.class), any(InstructionRequest.class), any(HttpHeaders.class));
    verify(defaultDeliveryDocumentSelector, never()).autoSelectDeliveryDocumentLine(anyList());
    verify(deliveryService, never()).reOpenDelivery(anyLong(), any());
    verify(deliveryMetaDataService, never()).findAndUpdateDeliveryStatus(anyString(), any());

    assertEquals(instructionResponse.getDeliveryStatus(), DeliveryStatus.WRK.toString());
    assertNotEquals(instructionResponse.getDeliveryDocuments().size(), 0);
    assertNull(instructionResponse.getInstruction());
    assertTrue(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getIsHazmat());
  }

  @Test
  public void testServeInstructionRequest_WithoutDeliveryDocument_MultiplePO()
      throws ReceivingException {
    List<DeliveryDocument> deliveryDocumentList = getDefaultDeliveryDocumentList();
    // copying deliveryDocument into GDM Response (for multiple POs)
    deliveryDocumentList.add(deliveryDocumentList.get(0));

    doReturn(deliveryDocumentList).when(wfsInstructionService).fetchDeliveryDocument(any(), any());
    doReturn(Boolean.FALSE)
        .when(configUtils)
        .isFeatureFlagEnabled(eq(ReceivingConstants.ENABLE_FILTER_CANCELLED_PO));
    doReturn(Boolean.TRUE).when(appConfig).isCheckDeliveryStatusReceivable();
    doReturn(deliveryDocumentList).when(deliveryDocumentHelper).updateDeliveryDocuments(any());
    doReturn(Boolean.FALSE)
        .when(configUtils)
        .isFeatureFlagEnabled(eq(ReceivingConstants.ENABLE_POPULATE_RECEIVED_QTY_PO));
    doNothing().when(wfsInstructionHelperService).checkForPendingShelfContainers(any(), any());
    InstructionResponse instructionResponse =
        wfsInstructionService.serveInstructionRequest(inputPayloadWithoutDeliveryDocument, headers);

    verify(wfsInstructionService, never()).updateOpenQtyForEachPoPoline(any(), anyString());
    verify(wfsInstructionService, never())
        .createInstructionForUpcReceiving(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(wfsInstructionService, never())
        .completeInstructionForWFS(
            any(Instruction.class), any(InstructionRequest.class), any(HttpHeaders.class));
    verify(defaultDeliveryDocumentSelector, never()).autoSelectDeliveryDocumentLine(anyList());
    verify(deliveryService, never()).reOpenDelivery(anyLong(), any());
    verify(deliveryMetaDataService, never()).findAndUpdateDeliveryStatus(anyString(), any());

    assertEquals(instructionResponse.getDeliveryStatus(), DeliveryStatus.WRK.toString());
    assertNotEquals(instructionResponse.getDeliveryDocuments().size(), 0);
    assertNotNull(instructionResponse.getInstruction());
    Instruction instruction = instructionResponse.getInstruction();
    assertEquals(instruction.getInstructionCode(), ReceivingConstants.MANUAL_PO_SELECTION);
  }

  @Test()
  public void testServeInstructionRequest_WithoutDeliveryDocument_DeliveryNotReceivable_ARV()
      throws ReceivingException {
    List<DeliveryDocument> deliveryDocuments = getDefaultDeliveryDocumentList();
    deliveryDocuments.get(0).setDeliveryStatus(DeliveryStatus.ARV);
    deliveryDocuments.get(0).setDeliveryLegacyStatus("ARV");

    doReturn(deliveryDocuments).when(wfsInstructionService).fetchDeliveryDocument(any(), any());
    doReturn(Boolean.FALSE)
        .when(configUtils)
        .isFeatureFlagEnabled(eq(ReceivingConstants.ENABLE_FILTER_CANCELLED_PO));
    doReturn(Boolean.TRUE).when(appConfig).isCheckDeliveryStatusReceivable();
    doNothing().when(wfsInstructionHelperService).checkForPendingShelfContainers(any(), any());
    try {
      InstructionResponse instructionResponse =
          wfsInstructionService.serveInstructionRequest(
              inputPayloadWithoutDeliveryDocument, headers);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.DELIVERY_NOT_RECEIVABLE);
    }
  }

  @Test()
  public void testServeInstructionRequestWithoutDeliveryDocument_DeliveryNotReceivable_SCH()
      throws ReceivingException {
    List<DeliveryDocument> deliveryDocuments = getDefaultDeliveryDocumentList();
    deliveryDocuments.get(0).setDeliveryStatus(DeliveryStatus.SCH);
    deliveryDocuments.get(0).setDeliveryLegacyStatus("SCH");

    doReturn(deliveryDocuments).when(wfsInstructionService).fetchDeliveryDocument(any(), any());
    doReturn(Boolean.FALSE)
        .when(configUtils)
        .isFeatureFlagEnabled(eq(ReceivingConstants.ENABLE_FILTER_CANCELLED_PO));
    doNothing().when(wfsInstructionHelperService).checkForPendingShelfContainers(any(), any());
    doReturn(Boolean.TRUE).when(appConfig).isCheckDeliveryStatusReceivable();
    try {
      InstructionResponse instructionResponse =
          wfsInstructionService.serveInstructionRequest(
              inputPayloadWithoutDeliveryDocument, headers);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.DELIVERY_NOT_RECEIVABLE);
    }
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "This delivery can not be received as the status is in ARV in GDM .Please contact your supervisor")
  public void
      testServeInstructionRequest_WithDeliveryDocument_V2FlowEnabled_DeliveryNotReceivable_ARV()
          throws ReceivingException {

    InstructionRequest instructionRequest =
        gson.fromJson(inputPayloadWithDeliveryDocument, InstructionRequest.class);
    List<DeliveryDocument> deliveryDocuments = instructionRequest.getDeliveryDocuments();
    deliveryDocuments.get(0).setDeliveryStatus(DeliveryStatus.ARV);
    deliveryDocuments.get(0).setDeliveryLegacyStatus("ARV");
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    String instructionRequestString = gson.toJson(instructionRequest);
    doReturn(Boolean.FALSE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(WFSConstants.WFS_INSTRUCTION_FLOW_V2_ENABLED);
    doReturn(Boolean.TRUE).when(appConfig).isCheckDeliveryStatusReceivable();

    InstructionResponse instructionResponse =
        wfsInstructionService.serveInstructionRequest(instructionRequestString, headers);
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "This delivery can not be received as the status is in SCH in GDM .Please contact your supervisor")
  public void
      testServeInstructionRequest_WithDeliveryDocument_V2FlowEnabled_DeliveryNotReceivable_SCH()
          throws ReceivingException {

    InstructionRequest instructionRequest =
        gson.fromJson(inputPayloadWithDeliveryDocument, InstructionRequest.class);
    List<DeliveryDocument> deliveryDocuments = instructionRequest.getDeliveryDocuments();
    deliveryDocuments.get(0).setDeliveryStatus(DeliveryStatus.SCH);
    deliveryDocuments.get(0).setDeliveryLegacyStatus("SCH");
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    String instructionRequestString = gson.toJson(instructionRequest);
    doReturn(Boolean.FALSE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(WFSConstants.WFS_INSTRUCTION_FLOW_V2_ENABLED);
    doReturn(Boolean.TRUE).when(appConfig).isCheckDeliveryStatusReceivable();

    InstructionResponse instructionResponse =
        wfsInstructionService.serveInstructionRequest(instructionRequestString, headers);
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "The scanned item, UPC: 06238657142208 number: 577595930 does not have weight and cube")
  public void testServeInstructionRequest_WithDeliveryDocument_DeliveryDocumentHasNoWeight()
      throws ReceivingException {
    InstructionRequest instructionRequest =
        gson.fromJson(inputPayloadWithDeliveryDocument, InstructionRequest.class);
    List<DeliveryDocument> deliveryDocuments = instructionRequest.getDeliveryDocuments();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setWeightQty(null);
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    String instructionRequestString = gson.toJson(instructionRequest);

    doReturn(Boolean.FALSE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(WFSConstants.WFS_INSTRUCTION_FLOW_V2_ENABLED);
    doReturn(Boolean.TRUE).when(appConfig).isCheckDeliveryStatusReceivable();
    InstructionResponse instructionResponse =
        wfsInstructionService.serveInstructionRequest(instructionRequestString, headers);
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "The scanned item, UPC: 06238657142208 number: 577595930 does not have weight and cube")
  public void testServeInstructionRequest_WithDeliveryDocument_DeliveryDocumentHasNoCube()
      throws ReceivingException {

    InstructionRequest instructionRequest =
        gson.fromJson(inputPayloadWithDeliveryDocument, InstructionRequest.class);
    List<DeliveryDocument> deliveryDocuments = instructionRequest.getDeliveryDocuments();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getAdditionalInfo().setCubeQty(null);
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    String instructionRequestString = gson.toJson(instructionRequest);

    doReturn(Boolean.FALSE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(WFSConstants.WFS_INSTRUCTION_FLOW_V2_ENABLED);
    doReturn(Boolean.TRUE).when(appConfig).isCheckDeliveryStatusReceivable();
    InstructionResponse instructionResponse =
        wfsInstructionService.serveInstructionRequest(instructionRequestString, headers);
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "The scanned item, UPC: 06238657142208 number: 577595930 does not have weight and cube")
  public void
      testServeInstructionRequest_WithDeliveryDocument_DeliveryDocumentHasNoWeight_UOM_null()
          throws ReceivingException {
    InstructionRequest instructionRequest =
        gson.fromJson(inputPayloadWithDeliveryDocument, InstructionRequest.class);
    List<DeliveryDocument> deliveryDocuments = instructionRequest.getDeliveryDocuments();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setWeightQtyUom(null);
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    String instructionRequestString = gson.toJson(instructionRequest);

    doReturn(Boolean.FALSE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(WFSConstants.WFS_INSTRUCTION_FLOW_V2_ENABLED);
    doReturn(Boolean.TRUE).when(appConfig).isCheckDeliveryStatusReceivable();
    InstructionResponse instructionResponse =
        wfsInstructionService.serveInstructionRequest(instructionRequestString, headers);
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "The scanned item, UPC: 06238657142208 number: 577595930 does not have weight and cube")
  public void testServeInstructionRequest_WithDeliveryDocument_DeliveryDocumentHasNoCube_UOM_null()
      throws ReceivingException {

    InstructionRequest instructionRequest =
        gson.fromJson(inputPayloadWithDeliveryDocument, InstructionRequest.class);
    List<DeliveryDocument> deliveryDocuments = instructionRequest.getDeliveryDocuments();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setCubeUomCode(null);
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    String instructionRequestString = gson.toJson(instructionRequest);

    doReturn(Boolean.FALSE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(WFSConstants.WFS_INSTRUCTION_FLOW_V2_ENABLED);
    doReturn(Boolean.TRUE).when(appConfig).isCheckDeliveryStatusReceivable();
    InstructionResponse instructionResponse =
        wfsInstructionService.serveInstructionRequest(instructionRequestString, headers);
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "The scanned item, UPC: 06238657142208 number: 577595930 does not have weight and cube")
  public void
      testServeInstructionRequest_WithDeliveryDocument_DeliveryDocumentHasNoWeight_UOM_Empty()
          throws ReceivingException {
    InstructionRequest instructionRequest =
        gson.fromJson(inputPayloadWithDeliveryDocument, InstructionRequest.class);
    List<DeliveryDocument> deliveryDocuments = instructionRequest.getDeliveryDocuments();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setWeightQtyUom("");
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    String instructionRequestString = gson.toJson(instructionRequest);

    doReturn(Boolean.FALSE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(WFSConstants.WFS_INSTRUCTION_FLOW_V2_ENABLED);
    doReturn(Boolean.TRUE).when(appConfig).isCheckDeliveryStatusReceivable();
    InstructionResponse instructionResponse =
        wfsInstructionService.serveInstructionRequest(instructionRequestString, headers);
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "The scanned item, UPC: 06238657142208 number: 577595930 does not have weight and cube")
  public void testServeInstructionRequest_WithDeliveryDocument_DeliveryDocumentHasNoCube_UOM_Empty()
      throws ReceivingException {

    InstructionRequest instructionRequest =
        gson.fromJson(inputPayloadWithDeliveryDocument, InstructionRequest.class);
    List<DeliveryDocument> deliveryDocuments = instructionRequest.getDeliveryDocuments();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setCubeUomCode("");
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    String instructionRequestString = gson.toJson(instructionRequest);

    doReturn(Boolean.FALSE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(WFSConstants.WFS_INSTRUCTION_FLOW_V2_ENABLED);
    doReturn(Boolean.TRUE).when(appConfig).isCheckDeliveryStatusReceivable();
    InstructionResponse instructionResponse =
        wfsInstructionService.serveInstructionRequest(instructionRequestString, headers);
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "The scanned item, UPC: 06238657142208 number: 577595930 does not have weight and cube")
  public void testServeInstructionRequest_WithDeliveryDocument_DeliveryDocumentHasNoWeight_Empty()
      throws ReceivingException {
    InstructionRequest instructionRequest =
        gson.fromJson(inputPayloadWithDeliveryDocument, InstructionRequest.class);
    List<DeliveryDocument> deliveryDocuments = instructionRequest.getDeliveryDocuments();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getAdditionalInfo().setWeightQty("");
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    String instructionRequestString = gson.toJson(instructionRequest);

    doReturn(Boolean.FALSE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(WFSConstants.WFS_INSTRUCTION_FLOW_V2_ENABLED);
    doReturn(Boolean.TRUE).when(appConfig).isCheckDeliveryStatusReceivable();
    InstructionResponse instructionResponse =
        wfsInstructionService.serveInstructionRequest(instructionRequestString, headers);
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "The scanned item, UPC: 06238657142208 number: 577595930 does not have weight and cube")
  public void testServeInstructionRequest_WithDeliveryDocument_DeliveryDocumentHasNoCube_Empty()
      throws ReceivingException {

    InstructionRequest instructionRequest =
        gson.fromJson(inputPayloadWithDeliveryDocument, InstructionRequest.class);
    List<DeliveryDocument> deliveryDocuments = instructionRequest.getDeliveryDocuments();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getAdditionalInfo().setCubeQty("");
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    String instructionRequestString = gson.toJson(instructionRequest);

    doReturn(Boolean.FALSE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(WFSConstants.WFS_INSTRUCTION_FLOW_V2_ENABLED);
    doReturn(Boolean.TRUE).when(appConfig).isCheckDeliveryStatusReceivable();
    InstructionResponse instructionResponse =
        wfsInstructionService.serveInstructionRequest(instructionRequestString, headers);
  }

  @Test
  public void
      testServeInstructionRequest_WithDeliveryDocument_SinglePOLine_V2FlowEnabled_ItemIsVendorComplianceValidated()
          throws ReceivingException {
    InstructionRequest instructionRequest =
        gson.fromJson(inputPayloadWithDeliveryDocument, InstructionRequest.class);
    instructionRequest.setVendorComplianceValidated(Boolean.TRUE);
    instructionRequest.setRegulatedItemType(VendorCompliance.LITHIUM_ION_AND_LIMITED_QUANTITY);
    instructionRequest.setEnteredQty(null);
    String instructionRequestString = gson.toJson(instructionRequest);

    doReturn(Boolean.TRUE).when(appConfig).isCheckDeliveryStatusReceivable();
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(WFSConstants.WFS_INSTRUCTION_FLOW_V2_ENABLED);

    InstructionResponse instructionResponse =
        wfsInstructionService.serveInstructionRequest(instructionRequestString, headers);

    verify(wfsInstructionService, times(1))
        .autoSelectDeliveryDocumentLineIfNeeded(
            any(InstructionRequest.class), any(DeliveryDocument.class));
    verify(wfsInstructionService, never())
        .createInstructionForUpcReceiving(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(wfsInstructionService, never())
        .completeInstructionForWFS(
            any(Instruction.class), any(InstructionRequest.class), any(HttpHeaders.class));

    assertEquals(instructionResponse.getDeliveryStatus(), instructionRequest.getDeliveryStatus());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        WFSConstants.WFS_QUANTITY_CAPTURE_INSTRUCTION_CODE);
    assertNotEquals(instructionResponse.getDeliveryDocuments().size(), 0);
    assertNotNull(instructionResponse.getDeliveryDocuments().get(0).getSellerId());
    assertEquals(instructionResponse.getDeliveryDocuments().get(0).getSellerId().length(), 32);
    assertNotNull(instructionResponse.getDeliveryDocuments().get(0).getSellerType());
    assertEquals(
        instructionResponse.getDeliveryDocuments().get(0).getSellerType(),
        ReceivingConstants.WFS_CHANNEL_METHOD);
  }

  @Test
  public void
      testServeInstructionRequest_WithDeliveryDocument_SinglePOLine_V2FlowEnabled_ItemIsNotVendorComplianceValidated_UpdateVendorComplianceTrue()
          throws ReceivingException {
    InstructionRequest instructionRequest =
        gson.fromJson(inputPayloadWithDeliveryDocument, InstructionRequest.class);
    instructionRequest.setVendorComplianceValidated(Boolean.FALSE);
    // TODO: move this to separate tests, and test this logic separately
    instructionRequest.setRegulatedItemType(null);
    instructionRequest.setEnteredQty(null);
    instructionRequest
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setEnteredQty(null);
    String instructionRequestString = gson.toJson(instructionRequest);

    doReturn(Boolean.TRUE).when(appConfig).isCheckDeliveryStatusReceivable();
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(WFSConstants.WFS_INSTRUCTION_FLOW_V2_ENABLED);
    doReturn(Boolean.TRUE)
        .when(deliveryDocumentHelper)
        .updateVendorCompliance(any(DeliveryDocumentLine.class));
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(eq(ReceivingConstants.WFS_TM_HAZMAT_CHECK_ENABLED));

    InstructionResponse instructionResponse =
        wfsInstructionService.serveInstructionRequest(instructionRequestString, headers);

    verify(wfsInstructionService, times(1))
        .autoSelectDeliveryDocumentLineIfNeeded(
            any(InstructionRequest.class), any(DeliveryDocument.class));
    verify(deliveryDocumentHelper, times(1))
        .updateVendorCompliance(any(DeliveryDocumentLine.class));
    verify(wfsInstructionService, never())
        .createInstructionForUpcReceiving(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(wfsInstructionService, never())
        .completeInstructionForWFS(
            any(Instruction.class), any(InstructionRequest.class), any(HttpHeaders.class));

    assertEquals(instructionResponse.getDeliveryStatus(), DeliveryStatus.WRK.name());
    assertNull(instructionResponse.getInstruction());
    assertNotEquals(instructionResponse.getDeliveryDocuments().size(), 0);
    assertNotNull(instructionResponse.getDeliveryDocuments().get(0).getSellerId());
    assertEquals(instructionResponse.getDeliveryDocuments().get(0).getSellerId().length(), 32);
    assertNotNull(instructionResponse.getDeliveryDocuments().get(0).getSellerType());
    assertEquals(
        instructionResponse.getDeliveryDocuments().get(0).getSellerType(),
        ReceivingConstants.WFS_CHANNEL_METHOD);
    assertTrue(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getIsHazmat());
  }

  @Test
  public void
      testServeInstructionRequest_WithDeliveryDocument_SinglePOLine_V2FlowEnabled_ItemIsNotVendorComplianceValidated_UpdateVendorComplianceFalse_HazmatCheck()
          throws ReceivingException {
    InstructionRequest instructionRequest =
        gson.fromJson(inputPayloadWithDeliveryDocument, InstructionRequest.class);
    instructionRequest.setVendorComplianceValidated(Boolean.FALSE);
    DeliveryDocumentLine documentLine =
        instructionRequest.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    documentLine.setIsHazmat(Boolean.FALSE);
    documentLine.setLimitedQtyVerificationRequired(Boolean.TRUE);
    documentLine.setLithiumIonVerificationRequired(Boolean.TRUE);
    String instructionRequestString = gson.toJson(instructionRequest);

    InstructionResponse mockInstructionResponse = new InstructionResponseImplNew();
    mockInstructionResponse.setDeliveryDocuments(instructionRequest.getDeliveryDocuments());

    doReturn(Boolean.TRUE).when(appConfig).isCheckDeliveryStatusReceivable();
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(WFSConstants.WFS_INSTRUCTION_FLOW_V2_ENABLED);
    doReturn(Boolean.FALSE)
        .when(deliveryDocumentHelper)
        .updateVendorCompliance(any(DeliveryDocumentLine.class));
    doReturn(Boolean.TRUE)
        .when(configUtils)
        .isFeatureFlagEnabled(eq(ReceivingConstants.WFS_TM_HAZMAT_CHECK_ENABLED));
    doReturn(new Instruction())
        .when(wfsInstructionService)
        .createInstructionForUpcReceiving(any(InstructionRequest.class), any(HttpHeaders.class));
    doReturn(mockInstructionResponse)
        .when(wfsInstructionService)
        .completeInstructionForWFS(
            any(Instruction.class), any(InstructionRequest.class), any(HttpHeaders.class));

    InstructionResponse instructionResponse =
        wfsInstructionService.serveInstructionRequest(instructionRequestString, headers);

    verify(wfsInstructionService, times(1))
        .autoSelectDeliveryDocumentLineIfNeeded(
            any(InstructionRequest.class), any(DeliveryDocument.class));
    verify(deliveryDocumentHelper, never()).updateVendorCompliance(any(DeliveryDocumentLine.class));
    verify(wfsInstructionService, times(1))
        .createInstructionForUpcReceiving(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(wfsInstructionService, times(1))
        .completeInstructionForWFS(
            any(Instruction.class), any(InstructionRequest.class), any(HttpHeaders.class));

    assertEquals(instructionResponse.getDeliveryStatus(), DeliveryStatus.WRK.name());
    assertTrue(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getIsHazmat());
    assertNotEquals(instructionResponse.getDeliveryDocuments().size(), 0);
    assertNotNull(instructionResponse.getDeliveryDocuments().get(0).getSellerId());
    assertEquals(instructionResponse.getDeliveryDocuments().get(0).getSellerId().length(), 32);
    assertNotNull(instructionResponse.getDeliveryDocuments().get(0).getSellerType());
    assertEquals(
        instructionResponse.getDeliveryDocuments().get(0).getSellerType(),
        ReceivingConstants.WFS_CHANNEL_METHOD);
  }

  @Test
  public void
      testServeInstructionRequest_WithDeliveryDocument_SinglePOLine_V2FlowEnabled_ItemIsVendorComplianceValidated_QtyNonNull_ReturnInstruction()
          throws ReceivingException {
    InstructionRequest instructionRequest =
        gson.fromJson(inputPayloadWithDeliveryDocument, InstructionRequest.class);
    instructionRequest.setVendorComplianceValidated(Boolean.TRUE);
    DeliveryDocumentLine documentLine =
        instructionRequest.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    documentLine.setIsHazmat(Boolean.FALSE);
    documentLine.setLimitedQtyVerificationRequired(Boolean.TRUE);
    documentLine.setLithiumIonVerificationRequired(Boolean.TRUE);
    String instructionRequestString = gson.toJson(instructionRequest);

    InstructionResponse mockInstructionResponse = new InstructionResponseImplNew();
    mockInstructionResponse.setDeliveryDocuments(instructionRequest.getDeliveryDocuments());

    doReturn(Boolean.TRUE).when(appConfig).isCheckDeliveryStatusReceivable();
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(WFSConstants.WFS_INSTRUCTION_FLOW_V2_ENABLED);
    doReturn(Boolean.FALSE)
        .when(deliveryDocumentHelper)
        .updateVendorCompliance(any(DeliveryDocumentLine.class));
    doReturn(Boolean.TRUE)
        .when(configUtils)
        .isFeatureFlagEnabled(eq(ReceivingConstants.WFS_TM_HAZMAT_CHECK_ENABLED));
    doReturn(new Instruction())
        .when(wfsInstructionService)
        .createInstructionForUpcReceiving(any(InstructionRequest.class), any(HttpHeaders.class));
    doReturn(mockInstructionResponse)
        .when(wfsInstructionService)
        .completeInstructionForWFS(
            any(Instruction.class), any(InstructionRequest.class), any(HttpHeaders.class));

    InstructionResponse instructionResponse =
        wfsInstructionService.serveInstructionRequest(instructionRequestString, headers);

    verify(wfsInstructionService, times(1))
        .autoSelectDeliveryDocumentLineIfNeeded(
            any(InstructionRequest.class), any(DeliveryDocument.class));
    verify(deliveryDocumentHelper, times(0))
        .updateVendorCompliance(any(DeliveryDocumentLine.class));
    verify(wfsInstructionService, times(1))
        .createInstructionForUpcReceiving(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(wfsInstructionService, times(1))
        .completeInstructionForWFS(
            any(Instruction.class), any(InstructionRequest.class), any(HttpHeaders.class));

    assertEquals(instructionResponse.getDeliveryStatus(), DeliveryStatus.WRK.name());
    assertTrue(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getIsHazmat());
    assertNotEquals(instructionResponse.getDeliveryDocuments().size(), 0);
    assertNotNull(instructionResponse.getDeliveryDocuments().get(0).getSellerId());
    assertEquals(instructionResponse.getDeliveryDocuments().get(0).getSellerId().length(), 32);
    assertNotNull(instructionResponse.getDeliveryDocuments().get(0).getSellerType());
    assertEquals(
        instructionResponse.getDeliveryDocuments().get(0).getSellerType(),
        ReceivingConstants.WFS_CHANNEL_METHOD);
  }

  @Test
  public void
      testServeInstructionRequest_WithDeliveryDocument_SinglePOLine_V2FlowDisabled_HazmatCheck()
          throws ReceivingException {
    InstructionRequest instructionRequest =
        gson.fromJson(inputPayloadWithDeliveryDocument, InstructionRequest.class);
    DeliveryDocumentLine documentLine =
        instructionRequest.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    documentLine.setIsHazmat(Boolean.FALSE);
    String instructionRequestString = gson.toJson(instructionRequest);

    InstructionResponse mockInstructionResponse = new InstructionResponseImplNew();
    mockInstructionResponse.setDeliveryDocuments(instructionRequest.getDeliveryDocuments());

    doReturn(Boolean.TRUE).when(appConfig).isCheckDeliveryStatusReceivable();
    doReturn(Boolean.FALSE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(WFSConstants.WFS_INSTRUCTION_FLOW_V2_ENABLED);
    doReturn(Boolean.FALSE)
        .when(deliveryDocumentHelper)
        .updateVendorCompliance(any(DeliveryDocumentLine.class));
    doReturn(Boolean.TRUE)
        .when(configUtils)
        .isFeatureFlagEnabled(eq(ReceivingConstants.WFS_TM_HAZMAT_CHECK_ENABLED));
    doReturn(Boolean.TRUE)
        .when(regulatedItemService)
        .isVendorComplianceRequired(any(DeliveryDocumentLine.class));
    doReturn(new Instruction())
        .when(wfsInstructionService)
        .createInstructionForUpcReceiving(any(InstructionRequest.class), any(HttpHeaders.class));
    doReturn(mockInstructionResponse)
        .when(wfsInstructionService)
        .completeInstructionForWFS(
            any(Instruction.class), any(InstructionRequest.class), any(HttpHeaders.class));

    InstructionResponse instructionResponse =
        wfsInstructionService.serveInstructionRequest(instructionRequestString, headers);

    verify(wfsInstructionService, times(1))
        .autoSelectDeliveryDocumentLineIfNeeded(
            any(InstructionRequest.class), any(DeliveryDocument.class));
    verify(deliveryDocumentHelper, never()).updateVendorCompliance(any(DeliveryDocumentLine.class));
    verify(regulatedItemService, times(1))
        .isVendorComplianceRequired(any(DeliveryDocumentLine.class));
    verify(wfsInstructionService, times(1))
        .createInstructionForUpcReceiving(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(wfsInstructionService, times(1))
        .completeInstructionForWFS(
            any(Instruction.class), any(InstructionRequest.class), any(HttpHeaders.class));

    assertEquals(instructionResponse.getDeliveryStatus(), DeliveryStatus.WRK.name());
    assertTrue(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getIsHazmat());
    assertNotEquals(instructionResponse.getDeliveryDocuments().size(), 0);
    assertNotNull(instructionResponse.getDeliveryDocuments().get(0).getSellerId());
    assertEquals(instructionResponse.getDeliveryDocuments().get(0).getSellerId().length(), 32);
    assertNotNull(instructionResponse.getDeliveryDocuments().get(0).getSellerType());
    assertEquals(
        instructionResponse.getDeliveryDocuments().get(0).getSellerType(),
        ReceivingConstants.WFS_CHANNEL_METHOD);
  }

  @Test
  public void
      testServeInstructionRequest_WithDeliveryDocument_SinglePOLine_V2FlowDisabled_AlreadyHazmatCompliant()
          throws ReceivingException {
    InstructionRequest instructionRequest =
        gson.fromJson(inputPayloadWithDeliveryDocumentHazmat, InstructionRequest.class);
    DeliveryDocumentLine documentLine =
        instructionRequest.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    //    documentLine.setIsHazmat(Boolean.TRUE);
    String instructionRequestString = gson.toJson(instructionRequest);

    InstructionResponse mockInstructionResponse = new InstructionResponseImplNew();
    mockInstructionResponse.setDeliveryDocuments(instructionRequest.getDeliveryDocuments());

    doReturn(Boolean.TRUE).when(appConfig).isCheckDeliveryStatusReceivable();
    doReturn(Boolean.FALSE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(WFSConstants.WFS_INSTRUCTION_FLOW_V2_ENABLED);
    doReturn(Boolean.FALSE)
        .when(deliveryDocumentHelper)
        .updateVendorCompliance(any(DeliveryDocumentLine.class));
    doReturn(Boolean.TRUE)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.WFS_TM_HAZMAT_CHECK_ENABLED);
    doReturn(Boolean.FALSE)
        .when(regulatedItemService)
        .isVendorComplianceRequired(any(DeliveryDocumentLine.class));
    doReturn(new Instruction())
        .when(wfsInstructionService)
        .createInstructionForUpcReceiving(any(InstructionRequest.class), any(HttpHeaders.class));
    doReturn(mockInstructionResponse)
        .when(wfsInstructionService)
        .completeInstructionForWFS(
            any(Instruction.class), any(InstructionRequest.class), any(HttpHeaders.class));

    ArgumentCaptor<InstructionRequest> instructionRequestCaptor =
        ArgumentCaptor.forClass(InstructionRequest.class);
    InstructionResponse instructionResponse =
        wfsInstructionService.serveInstructionRequest(instructionRequestString, headers);

    verify(wfsInstructionService, times(1))
        .autoSelectDeliveryDocumentLineIfNeeded(
            any(InstructionRequest.class), any(DeliveryDocument.class));
    verify(deliveryDocumentHelper, never()).updateVendorCompliance(any(DeliveryDocumentLine.class));
    verify(regulatedItemService, never())
        .isVendorComplianceRequired(any(DeliveryDocumentLine.class));
    verify(wfsInstructionService, times(1))
        .createInstructionForUpcReceiving(
            instructionRequestCaptor.capture(), any(HttpHeaders.class));
    verify(wfsInstructionService, times(1))
        .completeInstructionForWFS(
            any(Instruction.class), any(InstructionRequest.class), any(HttpHeaders.class));

    assertTrue(
        instructionRequestCaptor
            .getValue()
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .stream()
            .allMatch(DeliveryDocumentLine::getIsHazmat));

    assertEquals(instructionResponse.getDeliveryStatus(), DeliveryStatus.WRK.name());
    assertTrue(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getIsHazmat());
    assertNotEquals(instructionResponse.getDeliveryDocuments().size(), 0);
    assertNotNull(instructionResponse.getDeliveryDocuments().get(0).getSellerId());
    assertEquals(instructionResponse.getDeliveryDocuments().get(0).getSellerId().length(), 32);
    assertNotNull(instructionResponse.getDeliveryDocuments().get(0).getSellerType());
    assertEquals(
        instructionResponse.getDeliveryDocuments().get(0).getSellerType(),
        ReceivingConstants.WFS_CHANNEL_METHOD);
  }

  @Test
  public void testServeInstructionRequest_WithDeliveryDocument_MultiplePOLines_V2FlowEnabled()
      throws ReceivingException {
    InstructionRequest instructionRequest =
        gson.fromJson(inputPayloadWithDeliveryDocument, InstructionRequest.class);
    DeliveryDocumentLine documentLine =
        instructionRequest.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    DeliveryDocumentLine documentLineDuplicate =
        instructionRequest.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    documentLine.setPurchaseReferenceNumber("0032043GDM");
    documentLineDuplicate.setPurchaseReferenceNumber("0042043GDM");
    instructionRequest
        .getDeliveryDocuments()
        .get(0)
        .setDeliveryDocumentLines(Arrays.asList(documentLine, documentLineDuplicate));
    String instructionRequestString = gson.toJson(instructionRequest);

    InstructionResponse mockInstructionResponse = new InstructionResponseImplNew();
    mockInstructionResponse.setDeliveryDocuments(instructionRequest.getDeliveryDocuments());

    doReturn(Boolean.TRUE).when(appConfig).isCheckDeliveryStatusReceivable();
    doAnswer(
            invocationOnMock -> {
              InstructionRequest argumentInstructionRequest = invocationOnMock.getArgument(0);
              argumentInstructionRequest
                  .getDeliveryDocuments()
                  .get(0)
                  .setDeliveryDocumentLines(Collections.singletonList(documentLineDuplicate));
              return null;
            })
        .when(wfsInstructionService)
        .autoSelectDeliveryDocumentLineIfNeeded(
            any(InstructionRequest.class), any(DeliveryDocument.class));
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(WFSConstants.WFS_INSTRUCTION_FLOW_V2_ENABLED);
    doReturn(Boolean.FALSE)
        .when(deliveryDocumentHelper)
        .updateVendorCompliance(any(DeliveryDocumentLine.class));
    doReturn(Boolean.TRUE)
        .when(configUtils)
        .isFeatureFlagEnabled(ReceivingConstants.WFS_TM_HAZMAT_CHECK_ENABLED);
    doReturn(new Instruction())
        .when(wfsInstructionService)
        .createInstructionForUpcReceiving(any(InstructionRequest.class), any(HttpHeaders.class));
    doReturn(mockInstructionResponse)
        .when(wfsInstructionService)
        .completeInstructionForWFS(
            any(Instruction.class), any(InstructionRequest.class), any(HttpHeaders.class));

    InstructionResponse instructionResponse =
        wfsInstructionService.serveInstructionRequest(instructionRequestString, headers);

    ArgumentCaptor<InstructionRequest> instructionRequestCaptor =
        ArgumentCaptor.forClass(InstructionRequest.class);
    verify(wfsInstructionService, times(1))
        .autoSelectDeliveryDocumentLineIfNeeded(
            any(InstructionRequest.class), any(DeliveryDocument.class));
    verify(deliveryDocumentHelper, never()).updateVendorCompliance(any(DeliveryDocumentLine.class));
    verify(wfsInstructionService, times(1))
        .createInstructionForUpcReceiving(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(wfsInstructionService, times(1))
        .completeInstructionForWFS(
            any(Instruction.class), any(InstructionRequest.class), any(HttpHeaders.class));

    // TODO: Complete this.
    assertEquals(instructionResponse.getDeliveryStatus(), DeliveryStatus.WRK.name());
    assertNotEquals(instructionResponse.getDeliveryDocuments().size(), 0);
    assertNotNull(instructionResponse.getDeliveryDocuments().get(0).getSellerId());
    assertEquals(instructionResponse.getDeliveryDocuments().get(0).getSellerId().length(), 32);
    assertNotNull(instructionResponse.getDeliveryDocuments().get(0).getSellerType());
    assertEquals(
        instructionResponse.getDeliveryDocuments().get(0).getSellerType(),
        ReceivingConstants.WFS_CHANNEL_METHOD);
    assertEquals(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceNumber(),
        "0042043GDM");
  }

  @Test
  public void testServeInstructionRequestWithDeliveryDocumentForPalletReceiving_HappyFlow()
      throws ReceivingException {
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(Boolean.TRUE);
    when(configUtils.isFeatureFlagEnabled(eq(ReceivingConstants.WFS_LABELLING_POSTING_ENABLED)))
        .thenReturn(Boolean.FALSE);
    doReturn(fdeCreateContainerResponseString).when(fdeService).receive(any(), any());

    when(receiptCustomRepository.receivedQtyByPoAndPoLine(any(), any())).thenReturn(null);
    when(instructionPersisterServiceMock.saveInstruction(any())).then(returnsFirstArg());
    when(instructionRepository.save(any())).then(returnsFirstArg());
    when(containerPersisterService.saveContainer(any())).then(returnsFirstArg());
    doNothing().when(containerService1).publishContainer(any(), any());

    List<String> fdeCreateContainerResponses =
        Stream.of(
                "../receiving-test/src/main/resources/json/wfs/palletReceiving/wfsOPResponseForPalletReceiving_Iteration1.json",
                "../receiving-test/src/main/resources/json/wfs/palletReceiving/wfsOPResponseForPalletReceiving_Iteration2.json")
            .map(this::getJSONStringResponse)
            .collect(Collectors.toList());

    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenReturn(fdeCreateContainerResponses.get(0))
        .thenReturn(fdeCreateContainerResponses.get(1));

    when(instructionPersisterServiceMock.saveInstruction(any(Instruction.class)))
        .then(returnsFirstArg());
    when(configUtils.getCcmConfigValue(
            anyString(), eq(WFSConstants.MAX_NUMBER_OF_WFS_PALLET_LABELS)))
        .thenReturn(new JsonParser().parse("4"));

    InstructionResponse instructionResponse =
        wfsInstructionService.serveInstructionRequest(
            inputPayloadWithDeliveryDocumentForPalletReceiving, headers);

    verify(wfsInstructionService, times(1))
        .createInstructionsForUPCPalletReceiving(
            any(InstructionRequest.class), any(HttpHeaders.class));

    verify(wfsInstructionService, times(0))
        .createInstructionForUpcReceiving(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(wfsInstructionService, times(0))
        .completeInstructionForWFS(
            any(Instruction.class), any(InstructionRequest.class), any(HttpHeaders.class));
    verify(defaultDeliveryDocumentSelector, never()).autoSelectDeliveryDocumentLine(anyList());

    assertEquals(instructionResponse.getDeliveryStatus(), DeliveryStatus.WRK.toString());
    assertNotNull(instructionResponse.getDeliveryDocuments());
    assertNull(((InstructionResponseImplNew) instructionResponse).getPrintJob());

    assertEquals(instructionResponse.getInstructions().size(), 2);
    Instruction instruction1 = instructionResponse.getInstructions().get(0);
    assertNotEquals(instruction1.getSourceMessageId(), instruction1.getMessageId());
    assertEquals(instruction1.getProjectedReceiveQty(), 12);
    assertEquals(instruction1.getProjectedReceiveQtyUOM(), ReceivingConstants.Uom.VNPK);
    assertNotNull(instruction1.getContainer().getCtrDestination());

    Instruction instruction2 = instructionResponse.getInstructions().get(1);
    assertNotEquals(instruction2.getSourceMessageId(), instruction2.getMessageId());
    assertEquals(instruction2.getProjectedReceiveQty(), 8);
    assertEquals(instruction2.getProjectedReceiveQtyUOM(), ReceivingConstants.Uom.VNPK);
    assertNotNull(instruction2.getContainer().getCtrDestination());

    assertEquals(instruction2.getSourceMessageId(), instruction1.getSourceMessageId());
    assertNotEquals(instruction2.getMessageId(), instruction1.getMessageId());
  }

  @Test
  public void
      testServeInstructionRequestWithDeliveryDocumentForPalletReceiving_FailedOPRequestAfterOneIteration()
          throws ReceivingException {
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(Boolean.TRUE);
    when(configUtils.isFeatureFlagEnabled(eq(ReceivingConstants.WFS_LABELLING_POSTING_ENABLED)))
        .thenReturn(Boolean.FALSE);
    doReturn(fdeCreateContainerResponseString).when(fdeService).receive(any(), any());

    when(receiptCustomRepository.receivedQtyByPoAndPoLine(any(), any())).thenReturn(null);
    when(instructionPersisterServiceMock.saveInstruction(any())).then(returnsFirstArg());
    when(instructionRepository.save(any())).then(returnsFirstArg());
    when(containerPersisterService.saveContainer(any())).then(returnsFirstArg());
    doNothing().when(containerService1).publishContainer(any(), any());

    List<String> fdeCreateContainerResponses =
        Stream.of(
                "../receiving-test/src/main/resources/json/wfs/palletReceiving/wfsOPResponseForPalletReceiving_Iteration1.json",
                "../receiving-test/src/main/resources/json/wfs/palletReceiving/wfsOPResponseForPalletReceiving_Iteration2.json")
            .map(this::getJSONStringResponse)
            .collect(Collectors.toList());

    ErrorResponse noAllocationErrorResponse =
        ErrorResponse.builder()
            .errorCode(InstructionError.NO_ALLOCATION.getErrorCode())
            .errorMessage(InstructionError.NO_ALLOCATION.getErrorMessage())
            .errorKey(ExceptionCodes.NO_ALLOCATION)
            .build();

    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenReturn(fdeCreateContainerResponses.get(0))
        .thenThrow(new ReceivingException(HttpStatus.CONFLICT, noAllocationErrorResponse));

    when(instructionPersisterServiceMock.saveInstruction(any(Instruction.class)))
        .then(returnsFirstArg());
    when(configUtils.getCcmConfigValue(
            anyString(), eq(WFSConstants.MAX_NUMBER_OF_WFS_PALLET_LABELS)))
        .thenReturn(new JsonParser().parse("4"));

    InstructionResponse instructionResponse =
        wfsInstructionService.serveInstructionRequest(
            inputPayloadWithDeliveryDocumentForPalletReceiving, headers);

    verify(wfsInstructionService, times(1))
        .createInstructionsForUPCPalletReceiving(
            any(InstructionRequest.class), any(HttpHeaders.class));

    verify(wfsInstructionService, times(0))
        .createInstructionForUpcReceiving(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(wfsInstructionService, times(0))
        .completeInstructionForWFS(
            any(Instruction.class), any(InstructionRequest.class), any(HttpHeaders.class));
    verify(defaultDeliveryDocumentSelector, never()).autoSelectDeliveryDocumentLine(anyList());

    assertEquals(instructionResponse.getDeliveryStatus(), DeliveryStatus.WRK.toString());
    assertNull(((InstructionResponseImplNew) instructionResponse).getPrintJob());
    assertEquals(instructionResponse.getInstructions().size(), 2);
    assertNotNull(instructionResponse.getDeliveryDocuments());

    Instruction instruction1 = instructionResponse.getInstructions().get(0);
    assertEquals(instruction1.getProjectedReceiveQty(), 12);
    assertEquals(instruction1.getProjectedReceiveQtyUOM(), ReceivingConstants.Uom.VNPK);
    assertNotEquals(instruction1.getMessageId(), instruction1.getSourceMessageId());
    assertNotNull(instruction1.getContainer().getCtrDestination());

    Instruction workstationInstruction = instructionResponse.getInstructions().get(1);
    assertNull(workstationInstruction.getMessageId());
    assertEquals(
        workstationInstruction.getInstructionCode(), WFSConstants.WFS_WORKSTATION_INSTRUCTION_CODE);
    assertEquals(workstationInstruction.getProjectedReceiveQty(), 8);
    assertEquals(workstationInstruction.getProjectedReceiveQtyUOM(), ReceivingConstants.Uom.VNPK);
    assertNotNull(workstationInstruction.getId());
    assertEquals(workstationInstruction.getGtin(), instruction1.getGtin());
  }

  @Test()
  public void
      testServeInstructionRequestWithDeliveryDocumentForPalletReceiving_FailedOPRequestDuringFirstIteration()
          throws ReceivingException {
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(Boolean.TRUE);
    when(configUtils.isFeatureFlagEnabled(eq(ReceivingConstants.WFS_LABELLING_POSTING_ENABLED)))
        .thenReturn(Boolean.FALSE);
    doReturn(fdeCreateContainerResponseString).when(fdeService).receive(any(), any());
    when(receiptCustomRepository.receivedQtyByPoAndPoLine(any(), any())).thenReturn(null);
    when(instructionPersisterServiceMock.saveInstruction(any())).then(returnsFirstArg());
    when(instructionRepository.save(any())).then(returnsFirstArg());
    when(containerPersisterService.saveContainer(any())).then(returnsFirstArg());
    doNothing().when(containerService1).publishContainer(any(), any());

    when(instructionPersisterServiceMock.saveInstruction(any(Instruction.class)))
        .then(returnsFirstArg());
    when(configUtils.getCcmConfigValue(
            anyString(), eq(WFSConstants.MAX_NUMBER_OF_WFS_PALLET_LABELS)))
        .thenReturn(new JsonParser().parse("4"));

    InstructionResponse instructionResponse =
        wfsInstructionService.serveInstructionRequest(
            inputPayloadWithDeliveryDocumentForPalletReceiving, headers);

    verify(wfsInstructionService, times(1))
        .createInstructionsForUPCPalletReceiving(
            any(InstructionRequest.class), any(HttpHeaders.class));

    verify(wfsInstructionService, times(0))
        .createInstructionForUpcReceiving(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(wfsInstructionService, times(0))
        .completeInstructionForWFS(
            any(Instruction.class), any(InstructionRequest.class), any(HttpHeaders.class));
    verify(defaultDeliveryDocumentSelector, never()).autoSelectDeliveryDocumentLine(anyList());
  }

  @Test
  public void testServeInstructionRequestWithDeliveryDocument_havingMultipleDeliveryDocumentLines()
      throws ReceivingException {
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(Boolean.TRUE);
    when(configUtils.isFeatureFlagEnabled(eq(ReceivingConstants.WFS_LABELLING_POSTING_ENABLED)))
        .thenReturn(Boolean.FALSE);
    doReturn(fdeCreateContainerResponseString).when(fdeService).receive(any(), any());

    when(receiptCustomRepository.receivedQtyByPoAndPoLine(any(), any())).thenReturn(null);
    when(instructionPersisterServiceMock.saveInstruction(any())).then(returnsFirstArg());
    when(instructionRepository.save(any())).then(returnsFirstArg());
    when(containerPersisterService.saveContainer(any())).then(returnsFirstArg());
    doNothing().when(containerService1).publishContainer(any(), any());
    List<DeliveryDocument> defaultDeliveryDocument = getDefaultDeliveryDocumentList();
    DeliveryDocument selectedDeliveryDocument = defaultDeliveryDocument.get(0);
    DeliveryDocumentLine selectedDeliveryDocumentLine =
        selectedDeliveryDocument.getDeliveryDocumentLines().get(0);
    Pair<DeliveryDocument, DeliveryDocumentLine> selectedLine =
        new Pair<>(selectedDeliveryDocument, selectedDeliveryDocumentLine);
    doReturn(selectedLine)
        .when(defaultDeliveryDocumentSelector)
        .autoSelectDeliveryDocumentLine(anyList());

    InstructionResponse instructionResponse =
        wfsInstructionService.serveInstructionRequest(
            inputPayloadWithMultipleDeliveryDocumentLines, headers);

    verify(wfsInstructionService, times(1))
        .createInstructionForUpcReceiving(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(wfsInstructionService, times(1))
        .completeInstructionForWFS(
            any(Instruction.class), any(InstructionRequest.class), any(HttpHeaders.class));
    verify(defaultDeliveryDocumentSelector, times(1)).autoSelectDeliveryDocumentLine(anyList());

    assertEquals(
        instructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(), 1);
    testAttributesSpecificToWFSServeInstructionFlow(instructionResponse);
  }

  @Test
  public void testAutoSelectionOfDeliveryDocumentLine_whenNullIsReturned()
      throws ReceivingException {
    when(wfsManagedConfig.getFcNameMapping()).thenReturn(fcNumberToNameMapping);
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(Boolean.TRUE);
    when(configUtils.isFeatureFlagEnabled(eq(ReceivingConstants.WFS_LABELLING_POSTING_ENABLED)))
        .thenReturn(Boolean.FALSE);
    doReturn(fdeCreateContainerResponseString).when(fdeService).receive(any(), any());

    when(receiptCustomRepository.receivedQtyByPoAndPoLine(any(), any())).thenReturn(null);
    when(instructionPersisterServiceMock.saveInstruction(any())).then(returnsFirstArg());
    when(instructionRepository.save(any())).then(returnsFirstArg());
    when(containerPersisterService.saveContainer(any())).then(returnsFirstArg());
    doNothing().when(containerService1).publishContainer(any(), any());
    doReturn(null).when(defaultDeliveryDocumentSelector).autoSelectDeliveryDocumentLine(anyList());

    InstructionResponseImplNew instructionResponse =
        (InstructionResponseImplNew)
            wfsInstructionService.serveInstructionRequest(
                inputPayloadWithMultipleDeliveryDocumentLines, headers);

    verify(wfsInstructionService, times(1))
        .createInstructionForUpcReceiving(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(wfsInstructionService, times(1))
        .completeInstructionForWFS(
            any(Instruction.class), any(InstructionRequest.class), any(HttpHeaders.class));
    verify(defaultDeliveryDocumentSelector, times(1)).autoSelectDeliveryDocumentLine(anyList());

    assertEquals(
        instructionResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().size(), 1);

    assertEquals(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceNumber(),
        "0032043GDM");

    ArrayList<LinkedTreeMap> printRequests =
        (ArrayList<LinkedTreeMap>) instructionResponse.getPrintJob().get("printRequests");

    ArrayList<LinkedTreeMap> dataInPrintRequest =
        (ArrayList<LinkedTreeMap>) printRequests.get(0).get("data");

    Map<String, String> fcNumberToNameMap = (Map<String, String>) dataInPrintRequest.get(30);
    String actual_ = "";
    for (Map.Entry<String, String> entry : fcNumberToNameMap.entrySet()) {
      String name = entry.getKey();
      if (name.equalsIgnoreCase("value")) {
        actual_ = entry.getValue();
        break;
      }
    }
    String expected_ = "ORD3";

    assertEquals(actual_, expected_);

    testAttributesSpecificToWFSServeInstructionFlow(instructionResponse);
  }

  @Test
  public void testAutoSelectDeliveryDocumentLineIfNeeded_SinglePOLine() {
    InstructionRequest instructionRequest =
        gson.fromJson(inputPayloadWithDeliveryDocument, InstructionRequest.class);
    DeliveryDocument deliveryDocument = instructionRequest.getDeliveryDocuments().get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.setPurchaseReferenceNumber("0032043GDM");

    doReturn(null)
        .when(defaultDeliveryDocumentSelector)
        .autoSelectDeliveryDocumentLine(ArgumentMatchers.<DeliveryDocument>anyList());

    wfsInstructionService.autoSelectDeliveryDocumentLineIfNeeded(
        instructionRequest, deliveryDocument);
    verify(defaultDeliveryDocumentSelector, never())
        .autoSelectDeliveryDocumentLine(ArgumentMatchers.<DeliveryDocument>anyList());

    assertEquals(instructionRequest.getDeliveryDocuments().size(), 1);
    assertEquals(
        instructionRequest
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceNumber(),
        "0032043GDM");
  }

  @Test
  public void testAutoSelectDeliveryDocumentLineIfNeeded_MultiplePOLines() {
    InstructionRequest instructionRequest =
        gson.fromJson(inputPayloadWithDeliveryDocument, InstructionRequest.class);
    DeliveryDocumentLine deliveryDocumentLine =
        instructionRequest.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.setPurchaseReferenceNumber("0032043GDM");
    DeliveryDocumentLine otherDeliveryDocumentLine =
        gson.fromJson(gson.toJson(deliveryDocumentLine), DeliveryDocumentLine.class);
    otherDeliveryDocumentLine.setPurchaseReferenceNumber("0042043GDM");

    instructionRequest
        .getDeliveryDocuments()
        .get(0)
        .setDeliveryDocumentLines(Arrays.asList(deliveryDocumentLine, otherDeliveryDocumentLine));

    doReturn(
            new Pair<DeliveryDocument, DeliveryDocumentLine>(
                instructionRequest.getDeliveryDocuments().get(0), otherDeliveryDocumentLine))
        .when(defaultDeliveryDocumentSelector)
        .autoSelectDeliveryDocumentLine(ArgumentMatchers.<DeliveryDocument>anyList());

    wfsInstructionService.autoSelectDeliveryDocumentLineIfNeeded(
        instructionRequest, instructionRequest.getDeliveryDocuments().get(0));
    verify(defaultDeliveryDocumentSelector, times(1))
        .autoSelectDeliveryDocumentLine(ArgumentMatchers.<DeliveryDocument>anyList());
    assertEquals(instructionRequest.getDeliveryDocuments().size(), 1);
    assertEquals(
        instructionRequest
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceNumber(),
        "0042043GDM");
  }

  @Test
  public void testAutoSelectDeliveryDocumentLineIfNeeded_MultiplePOLines_AutoSelectorReturnsNull() {
    InstructionRequest instructionRequest =
        gson.fromJson(inputPayloadWithDeliveryDocument, InstructionRequest.class);
    DeliveryDocumentLine deliveryDocumentLine =
        instructionRequest.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.setPurchaseReferenceNumber("0032043GDM");
    DeliveryDocumentLine otherDeliveryDocumentLine =
        gson.fromJson(gson.toJson(deliveryDocumentLine), DeliveryDocumentLine.class);
    otherDeliveryDocumentLine.setPurchaseReferenceNumber("0042043GDM");

    instructionRequest
        .getDeliveryDocuments()
        .get(0)
        .setDeliveryDocumentLines(Arrays.asList(deliveryDocumentLine, otherDeliveryDocumentLine));

    doReturn(null)
        .when(defaultDeliveryDocumentSelector)
        .autoSelectDeliveryDocumentLine(ArgumentMatchers.<DeliveryDocument>anyList());

    wfsInstructionService.autoSelectDeliveryDocumentLineIfNeeded(
        instructionRequest, instructionRequest.getDeliveryDocuments().get(0));
    verify(defaultDeliveryDocumentSelector, times(1))
        .autoSelectDeliveryDocumentLine(ArgumentMatchers.<DeliveryDocument>anyList());
    assertEquals(instructionRequest.getDeliveryDocuments().size(), 1);
    assertEquals(
        instructionRequest
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceNumber(),
        "0032043GDM");
  }

  @Test
  public void testUpdateOpenQtyForEachPoPoline_UoM_Eaches() {
    List<DeliveryDocument> deliveryDocuments =
        gson.fromJson(inputPayloadWithDeliveryDocument, InstructionRequest.class)
            .getDeliveryDocuments();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalOrderQty(10);
    doReturn(
            Collections.singletonList(
                new ReceiptSummaryQtyByPoAndPoLineResponse(
                    deliveryDocuments.get(0).getPurchaseReferenceNumber(),
                    deliveryDocuments
                        .get(0)
                        .getDeliveryDocumentLines()
                        .get(0)
                        .getPurchaseReferenceLineNumber(),
                    6L)))
        .when(receiptCustomRepository)
        .receivedQtyInEaByPoAndPoLineList(any(), any());

    wfsInstructionService.updateOpenQtyForEachPoPoline(
        deliveryDocuments, ReceivingConstants.Uom.EACHES);

    DeliveryDocumentLine POLine = deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    assertEquals(
        POLine.getTotalOrderQty(),
        Integer.valueOf(10)); // sanity check, that it has not been changed
    assertEquals(POLine.getOpenQty(), Integer.valueOf(4));
    assertEquals(POLine.getTotalReceivedQty(), Integer.valueOf(6));
  }

  @Test
  public void testUpdateOpenQtyForEachPoPoline_UoM_Eaches_Negative() {
    List<DeliveryDocument> deliveryDocuments =
        gson.fromJson(inputPayloadWithDeliveryDocument, InstructionRequest.class)
            .getDeliveryDocuments();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalOrderQty(4);
    doReturn(
            Collections.singletonList(
                new ReceiptSummaryQtyByPoAndPoLineResponse(
                    deliveryDocuments.get(0).getPurchaseReferenceNumber(),
                    deliveryDocuments
                        .get(0)
                        .getDeliveryDocumentLines()
                        .get(0)
                        .getPurchaseReferenceLineNumber(),
                    6L)))
        .when(receiptCustomRepository)
        .receivedQtyInEaByPoAndPoLineList(any(), any());

    wfsInstructionService.updateOpenQtyForEachPoPoline(
        deliveryDocuments, ReceivingConstants.Uom.EACHES);

    DeliveryDocumentLine POLine = deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    assertEquals(
        POLine.getTotalOrderQty(),
        Integer.valueOf(4)); // sanity check, that it has not been changed
    assertEquals(POLine.getOpenQty(), Integer.valueOf(0));
    assertEquals(POLine.getTotalReceivedQty(), Integer.valueOf(6));
  }

  @Test
  public void testCreateInstructionForUpcReceiving_toCheckSuccessfulOPCall()
      throws ReceivingException {
    doReturn(fdeCreateContainerResponseString)
        .when(fdeService)
        .receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class));
    when(instructionPersisterServiceMock.saveInstruction(any(Instruction.class)))
        .then(returnsFirstArg());
    when(instructionRepository.save(any(Instruction.class))).then(returnsFirstArg());
    InstructionRequest instructionRequest =
        gson.fromJson(inputPayloadWithDeliveryDocument, InstructionRequest.class);
    Instruction instruction =
        wfsInstructionService.createInstructionForUpcReceiving(instructionRequest, headers);
    verify(fdeService, times(1))
        .receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testCreateInstructionForUpcReceiving_toCheckCreatedInstruction()
      throws ReceivingException {
    doReturn(fdeCreateContainerResponseString)
        .when(fdeService)
        .receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class));
    when(instructionPersisterServiceMock.saveInstruction(any(Instruction.class)))
        .then(returnsFirstArg());
    when(instructionRepository.save(any(Instruction.class))).then(returnsFirstArg());
    InstructionRequest instructionRequest =
        gson.fromJson(inputPayloadWithDeliveryDocument, InstructionRequest.class);

    FdeCreateContainerResponse fdeCreateContainerResponse = getFdeCreateContainerResponse();
    FdeCreateContainerRequest fdeCreateContainerRequest = getFdeCreateContainerRequest();

    ContainerDetails expectedContainer = fdeCreateContainerResponse.getContainer();

    Instruction instruction =
        wfsInstructionService.createInstructionForUpcReceiving(instructionRequest, headers);

    assertEquals(instruction.getReceivedQuantity(), 0);
    assertEquals(instruction.getReceivedQuantityUOM(), ReceivingConstants.Uom.EACHES);
    assertEquals(gson.toJson(instruction.getContainer()), gson.toJson(expectedContainer));

    DeliveryDocument deliverydocument =
        gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    DeliveryDocumentLine deliveryDocumentLine = deliverydocument.getDeliveryDocumentLines().get(0);
    assertEquals(deliverydocument.getSellerType(), ReceivingConstants.WFS_CHANNEL_METHOD);
    assertEquals(deliverydocument.getSellerId(), "AE70EDC9EA4D455D908B70ACB7B43393");
    assertNotNull(deliveryDocumentLine.getImageUrl());
    assertEquals(deliveryDocumentLine.getPurchaseRefType(), ReceivingConstants.WFS_CHANNEL_METHOD);
    assertEquals(instruction.getActivityName(), "CASEPACK");

    verify(instructionRepository, times(1)).save(eq(instruction));
  }

  @Test
  public void testCompleteInstructionForWFS_toCheckIfCompleteInstructionPersistedInDB()
      throws ReceivingException {
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(Boolean.TRUE);
    when(configUtils.isFeatureFlagEnabled(eq(ReceivingConstants.WFS_LABELLING_POSTING_ENABLED)))
        .thenReturn(Boolean.FALSE);
    doReturn(fdeCreateContainerResponseString)
        .when(fdeService)
        .receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class));
    when(instructionPersisterServiceMock.saveInstruction(any(Instruction.class)))
        .then(returnsFirstArg());
    when(instructionRepository.save(any(Instruction.class))).then(returnsFirstArg());
    when(containerPersisterService.saveContainer(any())).then(returnsFirstArg());
    InstructionRequest instructionRequest =
        gson.fromJson(inputPayloadWithDeliveryDocument, InstructionRequest.class);
    when(wfsManagedConfig.getFcNameMapping()).thenReturn(fcNumberToNameMapping);

    Instruction instruction =
        wfsInstructionService.createInstructionForUpcReceiving(instructionRequest, headers);
    InstructionResponse instructionResponse =
        wfsInstructionService.completeInstructionForWFS(instruction, instructionRequest, headers);
    verify(printingAndLabellingService, never()).getPrintableLabelDataRequest(anyMap());
    verify(printingAndLabellingService, never()).postToLabelling(anyList(), any());
    // 2 calls are made to instructionRepository first one is while creating the instruction and
    // second one is to complete the instruction
    verify(instructionRepository, times(2)).save(any(Instruction.class));
    assertNotNull(instructionResponse.getInstruction());
    assertNotNull(instructionResponse.getInstruction().getCompleteTs());
    assertNotNull(instructionResponse.getInstruction().getCompleteUserId());

    testAttributesSpecificToWFSFlow(instructionResponse);
    assertEquals(instructionResponse.getInstruction().getActivityName(), "CASEPACK");
  }

  @Test
  public void testCompleteInstructionForWFS_toCheckIfContainersPersistedInDB()
      throws ReceivingException {
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(Boolean.TRUE);
    when(configUtils.isFeatureFlagEnabled(eq(ReceivingConstants.WFS_LABELLING_POSTING_ENABLED)))
        .thenReturn(Boolean.FALSE);
    doReturn(fdeCreateContainerResponseString)
        .when(fdeService)
        .receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class));
    when(instructionPersisterServiceMock.saveInstruction(any(Instruction.class)))
        .then(returnsFirstArg());
    when(instructionRepository.save(any(Instruction.class))).then(returnsFirstArg());
    when(containerPersisterService.saveContainer(any())).then(returnsFirstArg());
    InstructionRequest instructionRequest =
        gson.fromJson(inputPayloadWithDeliveryDocument, InstructionRequest.class);

    Instruction instruction =
        wfsInstructionService.createInstructionForUpcReceiving(instructionRequest, headers);
    InstructionResponse instructionResponse =
        wfsInstructionService.completeInstructionForWFS(instruction, instructionRequest, headers);
    verify(printingAndLabellingService, never()).getPrintableLabelDataRequest(anyMap());
    verify(printingAndLabellingService, never()).postToLabelling(anyList(), any());
    verify(containerPersisterService, times(1)).saveContainer(any());

    testAttributesSpecificToWFSFlow(instructionResponse);
    assertEquals(instructionResponse.getInstruction().getActivityName(), "CASEPACK");
  }

  @Test
  public void testCompleteInstructionForWFS_toCheckIfReceiptsPersistedInDB()
      throws ReceivingException {
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(Boolean.TRUE);
    when(configUtils.isFeatureFlagEnabled(eq(ReceivingConstants.WFS_LABELLING_POSTING_ENABLED)))
        .thenReturn(Boolean.FALSE);
    doReturn(fdeCreateContainerResponseString)
        .when(fdeService)
        .receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class));
    when(instructionPersisterServiceMock.saveInstruction(any(Instruction.class)))
        .then(returnsFirstArg());
    when(instructionRepository.save(any(Instruction.class))).then(returnsFirstArg());
    when(containerPersisterService.saveContainer(any())).then(returnsFirstArg());

    InstructionRequest instructionRequest =
        gson.fromJson(inputPayloadWithDeliveryDocument, InstructionRequest.class);

    Instruction instruction =
        wfsInstructionService.createInstructionForUpcReceiving(instructionRequest, headers);
    InstructionResponse instructionResponse =
        wfsInstructionService.completeInstructionForWFS(instruction, instructionRequest, headers);
    verify(printingAndLabellingService, never()).getPrintableLabelDataRequest(anyMap());
    verify(printingAndLabellingService, never()).postToLabelling(anyList(), any());
    verify(receiptRepository, times(1)).saveAll(anyList());

    testAttributesSpecificToWFSFlow(instructionResponse);
    assertEquals(instructionResponse.getInstruction().getActivityName(), "CASEPACK");
  }

  @Test
  public void testCompleteInstructionForWFS_toCheckIfPrintJobsPersistedInDB()
      throws ReceivingException {
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(Boolean.TRUE);
    when(configUtils.isFeatureFlagEnabled(eq(ReceivingConstants.WFS_LABELLING_POSTING_ENABLED)))
        .thenReturn(Boolean.FALSE);
    doReturn(fdeCreateContainerResponseString)
        .when(fdeService)
        .receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class));
    when(instructionPersisterServiceMock.saveInstruction(any(Instruction.class)))
        .then(returnsFirstArg());
    when(instructionRepository.save(any(Instruction.class))).then(returnsFirstArg());
    when(containerPersisterService.saveContainer(any())).then(returnsFirstArg());
    when(printJobRepository.save(any())).then(returnsFirstArg());

    InstructionRequest instructionRequest =
        gson.fromJson(inputPayloadWithDeliveryDocument, InstructionRequest.class);

    Instruction instruction =
        wfsInstructionService.createInstructionForUpcReceiving(instructionRequest, headers);
    InstructionResponse instructionResponse =
        wfsInstructionService.completeInstructionForWFS(instruction, instructionRequest, headers);
    verify(printingAndLabellingService, never()).getPrintableLabelDataRequest(anyMap());
    verify(printingAndLabellingService, never()).postToLabelling(anyList(), any());
    verify(printJobRepository, times(1)).save(any(PrintJob.class));

    testAttributesSpecificToWFSFlow(instructionResponse);
    assertEquals(instructionResponse.getInstruction().getActivityName(), "CASEPACK");
  }

  @Test
  public void testCompleteInstructionForWFS_toCheckIfContainerInfoIsPublished()
      throws ReceivingException {
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(Boolean.TRUE);
    when(configUtils.isFeatureFlagEnabled(eq(ReceivingConstants.WFS_LABELLING_POSTING_ENABLED)))
        .thenReturn(Boolean.FALSE);
    doReturn(fdeCreateContainerResponseString)
        .when(fdeService)
        .receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class));
    when(instructionPersisterServiceMock.saveInstruction(any(Instruction.class)))
        .then(returnsFirstArg());
    when(instructionRepository.save(any(Instruction.class))).then(returnsFirstArg());
    when(containerPersisterService.saveContainer(any())).then(returnsFirstArg());
    when(printJobRepository.save(any())).then(returnsFirstArg());
    doNothing()
        .when(instructionHelperServiceMock)
        .publishConsolidatedContainer(
            any(Container.class), any(HttpHeaders.class), eq(Boolean.TRUE));
    doNothing().when(containerService1).publishContainer(any(), any());

    InstructionRequest instructionRequest =
        gson.fromJson(inputPayloadWithDeliveryDocument, InstructionRequest.class);

    Instruction instruction =
        wfsInstructionService.createInstructionForUpcReceiving(instructionRequest, headers);
    InstructionResponse instructionResponse =
        wfsInstructionService.completeInstructionForWFS(instruction, instructionRequest, headers);

    verify(printingAndLabellingService, never()).getPrintableLabelDataRequest(anyMap());
    verify(printingAndLabellingService, never()).postToLabelling(anyList(), any());
    verify(containerService1, times(1))
        .publishContainer(any(Container.class), any(Map.class), eq(Boolean.TRUE));

    testAttributesSpecificToWFSFlow(instructionResponse);
    assertEquals(instructionResponse.getInstruction().getActivityName(), "CASEPACK");
  }

  @Test
  public void testCompleteInstructionForWFS_toCheckIfContainerInfoIsPublished_BreakPack()
      throws ReceivingException {
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(Boolean.TRUE);
    when(configUtils.isFeatureFlagEnabled(eq(ReceivingConstants.WFS_LABELLING_POSTING_ENABLED)))
        .thenReturn(Boolean.FALSE);
    doReturn(fdeCreateContainerResponseStringBreakPack)
        .when(fdeService)
        .receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class));
    when(instructionPersisterServiceMock.saveInstruction(any(Instruction.class)))
        .then(returnsFirstArg());
    when(instructionRepository.save(any(Instruction.class))).then(returnsFirstArg());
    when(containerPersisterService.saveContainer(any())).then(returnsFirstArg());
    when(printJobRepository.save(any())).then(returnsFirstArg());
    doNothing()
        .when(instructionHelperServiceMock)
        .publishConsolidatedContainer(
            any(Container.class), any(HttpHeaders.class), eq(Boolean.TRUE));
    doNothing().when(containerService1).publishContainer(any(), any());

    InstructionRequest instructionRequest =
        gson.fromJson(inputPayloadWithDeliveryDocument, InstructionRequest.class);

    Instruction instruction =
        wfsInstructionService.createInstructionForUpcReceiving(instructionRequest, headers);
    InstructionResponse instructionResponse =
        wfsInstructionService.completeInstructionForWFS(instruction, instructionRequest, headers);

    verify(printingAndLabellingService, never()).getPrintableLabelDataRequest(anyMap());
    verify(printingAndLabellingService, never()).postToLabelling(anyList(), any());

    ArgumentCaptor<Container> containerArgumentCaptor = ArgumentCaptor.forClass(Container.class);
    verify(containerService1, times(1))
        .publishContainer(containerArgumentCaptor.capture(), any(Map.class), eq(Boolean.TRUE));

    assertEquals(containerArgumentCaptor.getValue().getLocation(), ReceivingConstants.ZERO_STRING);
    testAttributesSpecificToWFSFlow(instructionResponse);
    assertEquals(instructionResponse.getInstruction().getActivityName(), "BREAKPACK");
  }

  @Test
  public void testCompleteInstructionForWFS_toCheckIfLabellingPostingIsDone()
      throws ReceivingException {
    when(configUtils.isPrintingAndroidComponentEnabled()).thenReturn(Boolean.TRUE);
    when(configUtils.isFeatureFlagEnabled(eq(ReceivingConstants.WFS_LABELLING_POSTING_ENABLED)))
        .thenReturn(Boolean.TRUE);
    doReturn(fdeCreateContainerResponseString)
        .when(fdeService)
        .receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class));
    when(instructionPersisterServiceMock.saveInstruction(any(Instruction.class)))
        .then(returnsFirstArg());
    when(instructionRepository.save(any(Instruction.class))).then(returnsFirstArg());
    when(containerPersisterService.saveContainer(any())).then(returnsFirstArg());
    when(printJobRepository.save(any())).then(returnsFirstArg());
    doNothing()
        .when(instructionHelperServiceMock)
        .publishConsolidatedContainer(
            any(Container.class), any(HttpHeaders.class), eq(Boolean.TRUE));
    doNothing().when(containerService1).publishContainer(any(), any());

    InstructionRequest instructionRequest =
        gson.fromJson(inputPayloadWithDeliveryDocument, InstructionRequest.class);

    Instruction instruction =
        wfsInstructionService.createInstructionForUpcReceiving(instructionRequest, headers);
    InstructionResponse instructionResponse =
        wfsInstructionService.completeInstructionForWFS(instruction, instructionRequest, headers);

    verify(containerService1, times(1))
        .publishContainer(any(Container.class), any(Map.class), eq(Boolean.TRUE));

    verify(printingAndLabellingService, times(1)).getPrintableLabelDataRequest(anyMap());
    verify(printingAndLabellingService, times(1)).postToLabelling(anyList(), any());

    testAttributesSpecificToWFSFlow(instructionResponse);
    assertEquals(instructionResponse.getInstruction().getActivityName(), "CASEPACK");
  }

  @Test
  public void testCreateInstructionForUpcReceiving_toCheckUnSuccessfulOPCall()
      throws ReceivingException {
    doThrow(
            new ReceivingException(
                ReceivingException.FDE_RECEIVE_FDE_CALL_FAILED,
                HttpStatus.SERVICE_UNAVAILABLE,
                ReceivingException.CREATE_INSTRUCTION_ERROR_CODE))
        .when(fdeService)
        .receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class));

    when(instructionPersisterServiceMock.saveInstruction(any(Instruction.class)))
        .then(returnsFirstArg());
    when(instructionRepository.save(any(Instruction.class))).then(returnsFirstArg());
    try {
      InstructionRequest instructionRequest =
          gson.fromJson(inputPayloadWithDeliveryDocument, InstructionRequest.class);
      Instruction instruction =
          wfsInstructionService.createInstructionForUpcReceiving(instructionRequest, headers);
      verify(fdeService, times(1))
          .receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class));

    } catch (ReceivingException e) {
      verify(instructionRepository, times(1)).save(any(Instruction.class));
      assert (true);
    }
  }

  @Test
  public void testCreateInstructionsForUPCPalletReceiving_HappyFlow() throws ReceivingException {
    // Refer: (https://gecgithub01.walmart.com/i0a02l3/docs/wiki/Pallet-Receiving-Flow-Examples)
    // Two Iterations in OP, setting up context here:
    //   Request -> entered=20 VNPK, vnpkQty=2
    //   Iteration 1 -> entered=20 VNPK => projected=24 EA (12 VNPK)
    //   (entered updated 20 VNPK -> 20 - 12 = 8 VNPK)
    //   Iteration 2 -> entered=8 VNPK  => projected=16 EA (8 VNPK)
    List<String> fdeCreateContainerResponses =
        Stream.of(
                "../receiving-test/src/main/resources/json/wfs/palletReceiving/wfsOPResponseForPalletReceiving_Iteration1.json",
                "../receiving-test/src/main/resources/json/wfs/palletReceiving/wfsOPResponseForPalletReceiving_Iteration2.json")
            .map(this::getJSONStringResponse)
            .collect(Collectors.toList());

    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenReturn(fdeCreateContainerResponses.get(0))
        .thenReturn(fdeCreateContainerResponses.get(1));

    when(instructionPersisterServiceMock.saveInstruction(any(Instruction.class)))
        .then(returnsFirstArg());
    when(configUtils.getCcmConfigValue(
            anyString(), eq(WFSConstants.MAX_NUMBER_OF_WFS_PALLET_LABELS)))
        .thenReturn(new JsonParser().parse("4"));

    InstructionRequest instructionRequest =
        gson.fromJson(inputPayloadWithDeliveryDocumentForPalletReceiving, InstructionRequest.class);

    List<Instruction> createdInstructions =
        wfsInstructionService.createInstructionsForUPCPalletReceiving(instructionRequest, headers);

    Instruction instruction1 = createdInstructions.get(0);
    assertNotEquals(instruction1.getSourceMessageId(), instruction1.getMessageId());
    assertEquals(instruction1.getProjectedReceiveQty(), 12);
    assertEquals(instruction1.getProjectedReceiveQtyUOM(), ReceivingConstants.Uom.VNPK);

    Instruction instruction2 = createdInstructions.get(1);
    assertNotEquals(instruction2.getSourceMessageId(), instruction2.getMessageId());
    assertEquals(instruction2.getProjectedReceiveQty(), 8);
    assertEquals(instruction2.getProjectedReceiveQtyUOM(), ReceivingConstants.Uom.VNPK);

    assertEquals(instruction2.getSourceMessageId(), instruction1.getSourceMessageId());
    assertNotEquals(instruction2.getMessageId(), instruction1.getMessageId());
  }

  @Test()
  public void testCreateInstructionsForUPCPalletReceiving_FailedOPRequestInFirstIteration()
      throws ReceivingException {

    when(instructionPersisterServiceMock.saveInstruction(any(Instruction.class)))
        .then(returnsFirstArg());
    when(configUtils.getCcmConfigValue(
            anyString(), eq(WFSConstants.MAX_NUMBER_OF_WFS_PALLET_LABELS)))
        .thenReturn(new JsonParser().parse("4"));
    when(fdeService.receive(any(), any()))
        .thenThrow(
            ReceivingException.builder()
                .httpStatus(HttpStatus.BAD_REQUEST)
                .errorResponse(
                    ErrorResponse.builder()
                        .errorCode(ExceptionCodes.OF_GENERIC_ERROR)
                        .errorMessage("message")
                        .build())
                .build());

    InstructionRequest instructionRequest =
        gson.fromJson(inputPayloadWithDeliveryDocumentForPalletReceiving, InstructionRequest.class);

    List<Instruction> createdInstructions =
        wfsInstructionService.createInstructionsForUPCPalletReceiving(instructionRequest, headers);

    assertEquals(createdInstructions.size(), 1);
    assertEquals(
        createdInstructions.get(0).getInstructionCode(),
        WFSConstants.WFS_WORKSTATION_INSTRUCTION_CODE);
    assertEquals(
        Optional.of(createdInstructions.get(0).getProjectedReceiveQty()),
        Optional.of(instructionRequest.getEnteredQty()));
    assertEquals(
        createdInstructions.get(0).getProjectedReceiveQtyUOM(), ReceivingConstants.Uom.VNPK);
    assertEquals(
        createdInstructions.get(0).getGtin(),
        instructionRequest
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getGtin());
  }

  @Test
  public void testCreateInstructionsForUPCPalletReceiving_FailedOPRequestAfterOneIteration()
      throws ReceivingException {
    List<String> fdeCreateContainerResponses =
        Stream.of(
                "../receiving-test/src/main/resources/json/wfs/palletReceiving/wfsOPResponseForPalletReceiving_Iteration1.json",
                "../receiving-test/src/main/resources/json/wfs/palletReceiving/wfsOPResponseForPalletReceiving_Iteration2.json")
            .map(this::getJSONStringResponse)
            .collect(Collectors.toList());

    ErrorResponse noAllocationErrorResponse =
        ErrorResponse.builder()
            .errorCode(InstructionError.NO_ALLOCATION.getErrorCode())
            .errorMessage(InstructionError.NO_ALLOCATION.getErrorMessage())
            .errorKey(ExceptionCodes.NO_ALLOCATION)
            .build();

    when(fdeService.receive(any(FdeCreateContainerRequest.class), any(HttpHeaders.class)))
        .thenReturn(fdeCreateContainerResponses.get(0))
        .thenThrow(new ReceivingException(HttpStatus.CONFLICT, noAllocationErrorResponse));

    when(instructionPersisterServiceMock.saveInstruction(any(Instruction.class)))
        .then(returnsFirstArg());
    when(configUtils.getCcmConfigValue(
            anyString(), eq(WFSConstants.MAX_NUMBER_OF_WFS_PALLET_LABELS)))
        .thenReturn(new JsonParser().parse("4"));

    InstructionRequest instructionRequest =
        gson.fromJson(inputPayloadWithDeliveryDocumentForPalletReceiving, InstructionRequest.class);

    List<Instruction> createdInstructions =
        wfsInstructionService.createInstructionsForUPCPalletReceiving(instructionRequest, headers);
    assertEquals(createdInstructions.size(), 2);

    Instruction instruction1 = createdInstructions.get(0);
    assertNotEquals(instruction1.getMessageId(), instruction1.getSourceMessageId());
    assertEquals(instruction1.getProjectedReceiveQty(), 12);
    assertEquals(instruction1.getProjectedReceiveQtyUOM(), ReceivingConstants.Uom.VNPK);

    Instruction workstationInstruction = createdInstructions.get(1);
    assertNull(workstationInstruction.getMessageId());
    assertEquals(
        workstationInstruction.getInstructionCode(), WFSConstants.WFS_WORKSTATION_INSTRUCTION_CODE);
    assertEquals(workstationInstruction.getProjectedReceiveQty(), 8);
    assertEquals(workstationInstruction.getProjectedReceiveQtyUOM(), ReceivingConstants.Uom.VNPK);
    assertNotNull(workstationInstruction.getId());
    assertEquals(workstationInstruction.getGtin(), instruction1.getGtin());
  }

  @Test
  public void testSetEnteredQtyAndUomInstructionRequest() {
    InstructionRequest instructionRequest =
        gson.fromJson(inputPayloadWithDeliveryDocumentForPalletReceiving, InstructionRequest.class);
    instructionRequest.setEnteredQty(null);
    instructionRequest.setEnteredQtyUOM(null);
    instructionRequest
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setEnteredQty(20);
    instructionRequest
        .getDeliveryDocuments()
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setEnteredQtyUOM("ZA");

    wfsInstructionService.setEnteredQtyAndUomInstructionRequest(instructionRequest);
    assertNotNull(instructionRequest.getEnteredQty());
    assertEquals(instructionRequest.getEnteredQty().intValue(), 20);
    assertNotNull(instructionRequest.getEnteredQtyUOM());
    assertEquals(instructionRequest.getEnteredQtyUOM(), "ZA");
  }

  @Test
  public void testGetDockTagInstruction() {
    ReflectionTestUtils.setField(
        wfsInstructionService, "instructionHelperService", instructionHelperServiceMock);
    CreateDockTagRequest createDockTagRequest = mock(CreateDockTagRequest.class);
    String dockTagId = "";
    HttpHeaders httpHeaders = mock(HttpHeaders.class);
    ContainerDetails containerDetails = mock(ContainerDetails.class);
    when(httpHeaders.getFirst(anyString())).thenReturn("");
    doReturn(containerDetails)
        .when(instructionHelperServiceMock)
        .getDockTagContainer(any(), any(), any(), any(), any());
    doNothing().when(wfsInstructionHelperService).updatePrintJobsInInstructionForWFS(any(), any());
    Instruction instruction =
        wfsInstructionService.getDockTagInstruction(createDockTagRequest, dockTagId, httpHeaders);
    assertNotNull(instruction);
    assertNotNull(instruction.getInstructionCode());
    assertNotNull(instruction.getInstructionMsg());
    verify(instructionHelperServiceMock, times(1))
        .getDockTagContainer(any(), any(), any(), any(), any());
    verify(wfsInstructionHelperService, times(1)).updatePrintJobsInInstructionForWFS(any(), any());
  }

  @Test
  public void test_CompleteInstructionForWFS_fails() throws ReceivingException {
    doThrow(
            new ReceivingException(
                ReceivingException.COMPLETE_INSTRUCTION_ERROR_MSG,
                HttpStatus.BAD_REQUEST,
                ReceivingException.COMPLETE_INSTRUCTION_ERROR_CODE))
        .when(wfsInstructionHelperService)
        .createContainersAndReceiptsForWFSPos(any(), any(), any(), any());
    Instruction instruction = new Instruction();
    InstructionRequest instructionRequest = new InstructionRequest();
    Map<String, Object> additionalParams = new HashMap<>();
    additionalParams.put("isReReceivingLPNFlow", Boolean.TRUE);
    instructionRequest.setAdditionalParams(additionalParams);
    doNothing()
        .when(instructionPersisterServiceMock)
        .saveInstructionWithInstructionCodeAsNull(instruction);
    try {
      wfsInstructionService.completeInstructionForWFS(instruction, instructionRequest, headers);
    } catch (ReceivingException rbde) {
      assertSame(rbde.getMessage(), ReceivingException.COMPLETE_INSTRUCTION_ERROR_MSG);
    }
  }

  @Test
  public void testAutoCancelInstruction_Normal() throws Exception {
    Instruction instructionToBeCancelled = MockInstruction.getInstruction();

    when(instructionRepository.findById(anyLong()))
        .thenReturn(Optional.of(instructionToBeCancelled));
    when(tenantSpecificConfigReader.getCcmConfigValue(
            anyString(), eq(ReceivingConstants.AUTO_CANCEL_INSTRUCTION_MINUTES)))
        .thenReturn(new JsonParser().parse("5"));
    when(instructionRepository
            .findByCreateTsBeforeAndFacilityNumAndCompleteTsIsNullAndInstructionCodeIsNotNull(
                any(Date.class), anyInt()))
        .thenReturn(new ArrayList<>(Collections.singletonList(instructionToBeCancelled)));
    doReturn(null).when(wfsInstructionService).cancelInstruction(any(), any());
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(WFSConstants.VALIDATE_INSTRUCTION_WITH_SOURCE_MESSAGE_ID)))
        .thenReturn(Boolean.FALSE);
    wfsInstructionService.autoCancelInstruction(4093);

    verify(instructionRepository, times(1))
        .findByCreateTsBeforeAndFacilityNumAndCompleteTsIsNullAndInstructionCodeIsNotNull(
            any(), any());
    verify(instructionRepository, never()).findAllBySourceMessageId(anyString());
    verify(wfsInstructionService, times(1)).cancelInstruction(anyLong(), any());
  }

  @Test
  public void testAutoCancelInstruction_ValidateWithSourceMessageIdEnabled_InvalidCancellation()
      throws Exception {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, 1);
    Date dateAfterFromDate = cal.getTime();

    Instruction instructionToBeCancelled = MockInstruction.getInstruction();
    instructionToBeCancelled.setSourceMessageId("sourceMessageId1");

    Instruction instructionWithSameSourceMessageId = MockInstruction.getInstruction();
    instructionWithSameSourceMessageId.setSourceMessageId("sourceMessageId1");
    instructionWithSameSourceMessageId.setId(instructionToBeCancelled.getId() + 1L);
    instructionWithSameSourceMessageId.setLastChangeTs(dateAfterFromDate);

    when(instructionRepository.findById(anyLong()))
        .thenReturn(Optional.of(instructionToBeCancelled));
    when(tenantSpecificConfigReader.getCcmConfigValue(
            anyString(), eq(ReceivingConstants.AUTO_CANCEL_INSTRUCTION_MINUTES)))
        .thenReturn(new JsonParser().parse("5"));
    when(instructionRepository
            .findByCreateTsBeforeAndFacilityNumAndCompleteTsIsNullAndInstructionCodeIsNotNull(
                any(Date.class), anyInt()))
        .thenReturn(new ArrayList<>(Collections.singletonList(instructionToBeCancelled)));
    doNothing().when(wfsInstructionService).cancelInstructionsMultiple(any(), any());

    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(WFSConstants.VALIDATE_INSTRUCTION_WITH_SOURCE_MESSAGE_ID)))
        .thenReturn(Boolean.TRUE);
    when(instructionRepository.findAllBySourceMessageId(anyString()))
        .thenReturn(Collections.singletonList(instructionWithSameSourceMessageId));
    wfsInstructionService.autoCancelInstruction(4093);

    verify(instructionRepository, times(1))
        .findByCreateTsBeforeAndFacilityNumAndCompleteTsIsNullAndInstructionCodeIsNotNull(
            any(), any());
    verify(instructionRepository, times(1)).findAllBySourceMessageId(anyString());
    verify(wfsInstructionService, never()).cancelInstructionsMultiple(any(), any());
  }

  @Test
  public void
      testAutoCancelInstruction_ValidateWithSourceMessageIdEnabled_FindBySourceMessageId_Empty()
          throws Exception {
    Instruction instructionToBeCancelled = MockInstruction.getInstruction();
    instructionToBeCancelled.setSourceMessageId("sourceMessageId1");

    doReturn(new JsonParser().parse("5"))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(anyString(), eq(ReceivingConstants.AUTO_CANCEL_INSTRUCTION_MINUTES));
    doReturn(new ArrayList<>(Collections.singletonList(instructionToBeCancelled)))
        .when(instructionRepository)
        .findByCreateTsBeforeAndFacilityNumAndCompleteTsIsNullAndInstructionCodeIsNotNull(
            any(Date.class), anyInt());
    doNothing().when(wfsInstructionService).cancelInstructionsMultiple(any(), any());
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(eq(WFSConstants.VALIDATE_INSTRUCTION_WITH_SOURCE_MESSAGE_ID));
    doReturn(null).when(instructionRepository).findAllBySourceMessageId(anyString());

    wfsInstructionService.autoCancelInstruction(4093);

    verify(instructionRepository, times(1))
        .findByCreateTsBeforeAndFacilityNumAndCompleteTsIsNullAndInstructionCodeIsNotNull(
            any(), any());
    verify(instructionRepository, times(1)).findAllBySourceMessageId(anyString());
    verify(wfsInstructionService, never()).cancelInstructionsMultiple(any(), any());
  }

  @Test
  public void
      testAutoCancelInstruction_ValidateWithSourceMessageIdEnabled_AllLastChangeTsNull_Cancelled()
          throws Exception {
    // fetched instructions with same sourceMessageId has all lastChangeTs as null
    Instruction instructionToBeCancelled = MockInstruction.getInstruction();
    instructionToBeCancelled.setSourceMessageId("sourceMessageId1");

    Instruction instructionWithSameSourceMessageId = MockInstruction.getInstruction();
    instructionWithSameSourceMessageId.setSourceMessageId("sourceMessageId1");
    instructionWithSameSourceMessageId.setId(instructionToBeCancelled.getId() + 1L);
    instructionWithSameSourceMessageId.setLastChangeTs(null);

    Instruction otherInstructionWithSameSourceMessageId = MockInstruction.getInstruction();
    otherInstructionWithSameSourceMessageId.setSourceMessageId("sourceMessageId1");
    otherInstructionWithSameSourceMessageId.setId(instructionToBeCancelled.getId() + 2L);
    otherInstructionWithSameSourceMessageId.setLastChangeTs(null);

    when(instructionRepository.findById(anyLong()))
        .thenReturn(Optional.of(instructionToBeCancelled));
    doReturn(new JsonParser().parse("5"))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(anyString(), eq(ReceivingConstants.AUTO_CANCEL_INSTRUCTION_MINUTES));
    doReturn(new ArrayList<>(Collections.singletonList(instructionToBeCancelled)))
        .when(instructionRepository)
        .findByCreateTsBeforeAndFacilityNumAndCompleteTsIsNullAndInstructionCodeIsNotNull(
            any(Date.class), anyInt());
    doNothing().when(wfsInstructionService).cancelInstructionsMultiple(any(), any());
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(eq(WFSConstants.VALIDATE_INSTRUCTION_WITH_SOURCE_MESSAGE_ID));
    doReturn(
            Arrays.asList(
                instructionToBeCancelled,
                instructionWithSameSourceMessageId,
                otherInstructionWithSameSourceMessageId))
        .when(instructionRepository)
        .findAllBySourceMessageId(anyString());

    wfsInstructionService.autoCancelInstruction(4093);

    verify(instructionRepository, times(1))
        .findByCreateTsBeforeAndFacilityNumAndCompleteTsIsNullAndInstructionCodeIsNotNull(
            any(), any());
    verify(instructionRepository, times(1)).findAllBySourceMessageId(anyString());
    verify(wfsInstructionService, times(1)).cancelInstructionsMultiple(any(), any());
  }

  @Test
  public void
      testAutoCancelInstruction_ValidateWithSourceMessageIdEnabled_1LastChangeTsNull_1LastChangeTsBefore_Cancelled()
          throws Exception {
    // fetched instructions with same sourceMessageId has props :
    // - one has last change ts null
    // - one has last change ts < fromDate (before fromDate)
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -1);
    Date dateBeforeFromDate = cal.getTime();

    Instruction instructionToBeCancelled = MockInstruction.getInstruction();
    instructionToBeCancelled.setSourceMessageId("sourceMessageId1");

    Instruction instructionWithSameSourceMessageId = MockInstruction.getInstruction();
    instructionWithSameSourceMessageId.setSourceMessageId("sourceMessageId1");
    instructionWithSameSourceMessageId.setId(instructionToBeCancelled.getId() + 1L);
    instructionWithSameSourceMessageId.setLastChangeTs(null);

    Instruction otherInstructionWithSameSourceMessageId = MockInstruction.getInstruction();
    otherInstructionWithSameSourceMessageId.setSourceMessageId("sourceMessageId1");
    otherInstructionWithSameSourceMessageId.setId(instructionToBeCancelled.getId() + 2L);
    otherInstructionWithSameSourceMessageId.setLastChangeTs(dateBeforeFromDate);

    when(instructionRepository.findById(anyLong()))
        .thenReturn(Optional.of(instructionToBeCancelled));
    doReturn(new JsonParser().parse("5"))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(anyString(), eq(ReceivingConstants.AUTO_CANCEL_INSTRUCTION_MINUTES));
    doReturn(new ArrayList<>(Collections.singletonList(instructionToBeCancelled)))
        .when(instructionRepository)
        .findByCreateTsBeforeAndFacilityNumAndCompleteTsIsNullAndInstructionCodeIsNotNull(
            any(Date.class), anyInt());
    doNothing().when(wfsInstructionService).cancelInstructionsMultiple(any(), any());
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(eq(WFSConstants.VALIDATE_INSTRUCTION_WITH_SOURCE_MESSAGE_ID));
    doReturn(
            Arrays.asList(
                instructionToBeCancelled,
                instructionWithSameSourceMessageId,
                otherInstructionWithSameSourceMessageId))
        .when(instructionRepository)
        .findAllBySourceMessageId(anyString());

    wfsInstructionService.autoCancelInstruction(4093);

    verify(instructionRepository, times(1))
        .findByCreateTsBeforeAndFacilityNumAndCompleteTsIsNullAndInstructionCodeIsNotNull(
            any(), any());
    verify(instructionRepository, times(1)).findAllBySourceMessageId(anyString());
    verify(wfsInstructionService, times(1)).cancelInstructionsMultiple(any(), any());
  }

  @Test
  public void
      testAutoCancelInstruction_ValidateWithSourceMessageIdEnabled_1LastChangeTsNull_1LastChangeTsAfter_NotCancelled()
          throws Exception {
    // fetched instructions with same sourceMessageId has props :
    // - one has last change ts null
    // - one has last change ts > fromDate (after fromDate)
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, 1);
    Date dateAfterFromDate = cal.getTime();

    Instruction instructionToBeCancelled = MockInstruction.getInstruction();
    instructionToBeCancelled.setSourceMessageId("sourceMessageId1");

    Instruction instructionWithSameSourceMessageId = MockInstruction.getInstruction();
    instructionWithSameSourceMessageId.setSourceMessageId("sourceMessageId1");
    instructionWithSameSourceMessageId.setId(instructionToBeCancelled.getId() + 1L);
    instructionWithSameSourceMessageId.setLastChangeTs(null);

    Instruction otherInstructionWithSameSourceMessageId = MockInstruction.getInstruction();
    otherInstructionWithSameSourceMessageId.setSourceMessageId("sourceMessageId1");
    otherInstructionWithSameSourceMessageId.setId(instructionToBeCancelled.getId() + 2L);
    otherInstructionWithSameSourceMessageId.setLastChangeTs(dateAfterFromDate);

    when(instructionRepository.findById(anyLong()))
        .thenReturn(Optional.of(instructionToBeCancelled));
    doReturn(new JsonParser().parse("5"))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(anyString(), eq(ReceivingConstants.AUTO_CANCEL_INSTRUCTION_MINUTES));
    doReturn(new ArrayList<>(Collections.singletonList(instructionToBeCancelled)))
        .when(instructionRepository)
        .findByCreateTsBeforeAndFacilityNumAndCompleteTsIsNullAndInstructionCodeIsNotNull(
            any(Date.class), anyInt());
    doNothing().when(wfsInstructionService).cancelInstructionsMultiple(any(), any());
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(eq(WFSConstants.VALIDATE_INSTRUCTION_WITH_SOURCE_MESSAGE_ID));
    doReturn(
            Arrays.asList(
                instructionToBeCancelled,
                instructionWithSameSourceMessageId,
                otherInstructionWithSameSourceMessageId))
        .when(instructionRepository)
        .findAllBySourceMessageId(anyString());

    wfsInstructionService.autoCancelInstruction(4093);

    verify(instructionRepository, times(1))
        .findByCreateTsBeforeAndFacilityNumAndCompleteTsIsNullAndInstructionCodeIsNotNull(
            any(), any());
    verify(instructionRepository, times(1)).findAllBySourceMessageId(anyString());
    verify(wfsInstructionService, never()).cancelInstructionsMultiple(any(), any());
  }

  @Test
  public void
      testAutoCancelInstruction_ValidateWithSourceMessageIdEnabled_AllLastChangeTsAfter_NotCancelled()
          throws Exception {
    // fetched instructions with same sourceMessageId has props :
    // - all has last change ts > fromDate (after fromDate)
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, 1);
    Date dateAfterFromDate = cal.getTime();

    Instruction instructionToBeCancelled = MockInstruction.getInstruction();
    instructionToBeCancelled.setSourceMessageId("sourceMessageId1");

    Instruction instructionWithSameSourceMessageId = MockInstruction.getInstruction();
    instructionWithSameSourceMessageId.setSourceMessageId("sourceMessageId1");
    instructionWithSameSourceMessageId.setId(instructionToBeCancelled.getId() + 1L);
    instructionWithSameSourceMessageId.setLastChangeTs(dateAfterFromDate);

    Instruction otherInstructionWithSameSourceMessageId = MockInstruction.getInstruction();
    otherInstructionWithSameSourceMessageId.setSourceMessageId("sourceMessageId1");
    otherInstructionWithSameSourceMessageId.setId(instructionToBeCancelled.getId() + 2L);
    otherInstructionWithSameSourceMessageId.setLastChangeTs(dateAfterFromDate);

    when(instructionRepository.findById(anyLong()))
        .thenReturn(Optional.of(instructionToBeCancelled));
    doReturn(new JsonParser().parse("5"))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(anyString(), eq(ReceivingConstants.AUTO_CANCEL_INSTRUCTION_MINUTES));
    doReturn(new ArrayList<>(Collections.singletonList(instructionToBeCancelled)))
        .when(instructionRepository)
        .findByCreateTsBeforeAndFacilityNumAndCompleteTsIsNullAndInstructionCodeIsNotNull(
            any(Date.class), anyInt());
    doNothing().when(wfsInstructionService).cancelInstructionsMultiple(any(), any());
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(eq(WFSConstants.VALIDATE_INSTRUCTION_WITH_SOURCE_MESSAGE_ID));
    doReturn(
            Arrays.asList(
                instructionToBeCancelled,
                instructionWithSameSourceMessageId,
                otherInstructionWithSameSourceMessageId))
        .when(instructionRepository)
        .findAllBySourceMessageId(anyString());

    wfsInstructionService.autoCancelInstruction(4093);

    verify(instructionRepository, times(1))
        .findByCreateTsBeforeAndFacilityNumAndCompleteTsIsNullAndInstructionCodeIsNotNull(
            any(), any());
    verify(instructionRepository, times(1)).findAllBySourceMessageId(anyString());
    verify(wfsInstructionService, never()).cancelInstructionsMultiple(any(), any());
  }

  @Test
  public void
      testAutoCancelInstruction_ValidateWithSourceMessageIdEnabled_AllLastChangeTsBefore_Cancelled()
          throws Exception {
    // fetched instructions with same sourceMessageId has props :
    // - all has last change ts < fromDate (before fromDate)
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -1);
    Date dateBeforeFromDate = cal.getTime();

    Instruction instructionToBeCancelled = MockInstruction.getInstruction();
    instructionToBeCancelled.setSourceMessageId("sourceMessageId1");

    Instruction instructionWithSameSourceMessageId = MockInstruction.getInstruction();
    instructionWithSameSourceMessageId.setSourceMessageId("sourceMessageId1");
    instructionWithSameSourceMessageId.setId(instructionToBeCancelled.getId() + 1L);
    instructionWithSameSourceMessageId.setLastChangeTs(dateBeforeFromDate);

    Instruction otherInstructionWithSameSourceMessageId = MockInstruction.getInstruction();
    otherInstructionWithSameSourceMessageId.setSourceMessageId("sourceMessageId1");
    otherInstructionWithSameSourceMessageId.setId(instructionToBeCancelled.getId() + 2L);
    otherInstructionWithSameSourceMessageId.setLastChangeTs(dateBeforeFromDate);

    when(instructionRepository.findById(anyLong()))
        .thenReturn(Optional.of(instructionToBeCancelled));
    doReturn(new JsonParser().parse("5"))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(anyString(), eq(ReceivingConstants.AUTO_CANCEL_INSTRUCTION_MINUTES));
    doReturn(new ArrayList<>(Collections.singletonList(instructionToBeCancelled)))
        .when(instructionRepository)
        .findByCreateTsBeforeAndFacilityNumAndCompleteTsIsNullAndInstructionCodeIsNotNull(
            any(Date.class), anyInt());
    doNothing().when(wfsInstructionService).cancelInstructionsMultiple(any(), any());
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(eq(WFSConstants.VALIDATE_INSTRUCTION_WITH_SOURCE_MESSAGE_ID));
    doReturn(
            Arrays.asList(
                instructionToBeCancelled,
                instructionWithSameSourceMessageId,
                otherInstructionWithSameSourceMessageId))
        .when(instructionRepository)
        .findAllBySourceMessageId(anyString());

    wfsInstructionService.autoCancelInstruction(4093);

    verify(instructionRepository, times(1))
        .findByCreateTsBeforeAndFacilityNumAndCompleteTsIsNullAndInstructionCodeIsNotNull(
            any(), any());
    verify(instructionRepository, times(1)).findAllBySourceMessageId(anyString());
    verify(wfsInstructionService, times(1)).cancelInstructionsMultiple(any(), any());
  }

  @Test
  public void
      testAutoCancelInstruction_ValidateWithSourceMessageIdEnabled_MultipleInstructionsToCancel_AllLastChangeTsBefore_Cancelled()
          throws ReceivingException {
    // Scenario:
    // Two instructions sent when repository is queried for facility number
    // - I1: sourceMessageId1, lastChangeTs "after" fromDate
    // - I2: sourceMessageId2, lastChangeTs "after" fromDate
    // In db, also exist these (returned when queried for findAllBySourceMessageId)
    // - I3: sourceMessageId1, lastChangeTs "after" fromDate
    // - I4: sourceMessageId2, lastChangeTs "after" fromDate
    // All sourceMessageId groups by themselves are eligible to cancel the fetched instruction part
    // so cancelMultiple will be called twice.
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -1);
    Date dateBeforeFromDate = cal.getTime();

    Instruction instruction1 = MockInstruction.getInstruction();
    instruction1.setSourceMessageId("sourceMessageId1");
    instruction1.setLastChangeTs(dateBeforeFromDate);

    Instruction instruction2 = MockInstruction.getInstruction();
    instruction2.setSourceMessageId("sourceMessageId2");
    instruction2.setLastChangeTs(dateBeforeFromDate);

    Instruction instruction3 = MockInstruction.getInstruction();
    instruction3.setSourceMessageId("sourceMessageId1");
    instruction3.setLastChangeTs(dateBeforeFromDate);

    Instruction instruction4 = MockInstruction.getInstruction();
    instruction4.setSourceMessageId("sourceMessageId2");
    instruction4.setLastChangeTs(dateBeforeFromDate);

    // fromDate will be 15 mins before current time
    doReturn(new JsonParser().parse("15"))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(anyString(), eq(ReceivingConstants.AUTO_CANCEL_INSTRUCTION_MINUTES));
    doReturn(Arrays.asList(instruction1, instruction2))
        .when(instructionRepository)
        .findByCreateTsBeforeAndFacilityNumAndCompleteTsIsNullAndInstructionCodeIsNotNull(
            any(Date.class), anyInt());
    doNothing().when(wfsInstructionService).cancelInstructionsMultiple(any(), any());
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(eq(WFSConstants.VALIDATE_INSTRUCTION_WITH_SOURCE_MESSAGE_ID));
    when(instructionRepository.findAllBySourceMessageId(anyString()))
        .thenReturn(Arrays.asList(instruction1, instruction3))
        .thenReturn(Arrays.asList(instruction2, instruction4));

    wfsInstructionService.autoCancelInstruction(4093);

    verify(instructionRepository, times(1))
        .findByCreateTsBeforeAndFacilityNumAndCompleteTsIsNullAndInstructionCodeIsNotNull(
            any(), any());
    verify(instructionRepository, times(2)).findAllBySourceMessageId(anyString());
    verify(wfsInstructionService, times(2)).cancelInstructionsMultiple(any(), any());
  }

  private void testAttributesSpecificToWFSFlow(InstructionResponse instructionResponse) {
    DeliveryDocument deliverydocument =
        gson.fromJson(
            instructionResponse.getInstruction().getDeliveryDocument(), DeliveryDocument.class);
    DeliveryDocumentLine deliveryDocumentLine = deliverydocument.getDeliveryDocumentLines().get(0);

    assertEquals(deliverydocument.getSellerType(), ReceivingConstants.WFS_CHANNEL_METHOD);
    assertEquals(deliverydocument.getSellerId(), "AE70EDC9EA4D455D908B70ACB7B43393");
    assertNotNull(deliveryDocumentLine.getImageUrl());
    assertEquals(deliveryDocumentLine.getPurchaseRefType(), ReceivingConstants.WFS_CHANNEL_METHOD);
  }

  private void testAttributesSpecificToWFSServeInstructionFlow(
      InstructionResponse instructionResponse) {
    assertEquals(instructionResponse.getDeliveryStatus(), DeliveryStatus.WRK.toString());
    assertNotNull(instructionResponse.getInstruction().getActivityName());
    assertEquals(instructionResponse.getInstruction().getInstructionCode(), "Pass");
    assertNotNull(instructionResponse.getInstruction().getInstructionMsg());
    assertNotEquals(instructionResponse.getDeliveryDocuments().size(), 0);

    assertNotNull(instructionResponse.getDeliveryDocuments().get(0).getSellerId());
    assertEquals(instructionResponse.getDeliveryDocuments().get(0).getSellerId().length(), 32);

    assertNotNull(instructionResponse.getDeliveryDocuments().get(0).getSellerType());
    assertEquals(
        instructionResponse.getDeliveryDocuments().get(0).getSellerType(),
        ReceivingConstants.WFS_CHANNEL_METHOD);

    assertNotNull(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getImageUrl());
  }

  @Test
  public void testServeInstructionRequest_WithoutDeliveryDocument_CheckShelfContainers()
      throws ReceivingException {
    doReturn(inputGDMLpnDetailsResponse)
        .when(gdmRestApiClient)
        .getReReceivingContainerResponseFromGDM(any(), any());
    when(appConfig.getGdmBaseUrl()).thenReturn("https://stg-int.gdm.prod.us.walmart.net");
    doReturn(Boolean.TRUE)
        .when(configUtils)
        .getConfiguredFeatureFlag(
            eq("6280"), eq(WFSConstants.IS_RE_RECEIVING_CONTAINER_CHECK_ENABLED), anyBoolean());
    try {
      InstructionResponse instructionResponse =
          wfsInstructionService.serveInstructionRequest(
              inputPayloadWithoutDeliveryDocument, headers);
    } catch (Exception e) {
      verify(gdmRestApiClient, times(1)).getReReceivingContainerResponseFromGDM(any(), any());
    }
  }

  private String getJSONStringResponse(String path) {
    String response = null;
    response = getResponseFromJSONFilePath(path);
    if (Objects.nonNull(response)) {
      return response;
    }
    assert (false);
    return null;
  }

  private String getResponseFromJSONFilePath(String path) {
    String payload = null;
    try {
      String filePath = new File(path).getCanonicalPath();
      payload = new String(Files.readAllBytes(Paths.get(filePath)));
    } catch (IOException e) {
      e.printStackTrace();
    }
    return payload;
  }

  private List<DeliveryDocument> getDefaultDeliveryDocumentList() {
    InstructionRequest instructionRequest =
        gson.fromJson(inputPayloadWithDeliveryDocument, InstructionRequest.class);
    List<DeliveryDocument> deliveryDocumentList = instructionRequest.getDeliveryDocuments();
    for (DeliveryDocument deliveryDocument : deliveryDocumentList) {
      if (Objects.nonNull(deliveryDocument.getAdditionalInfo()))
        deliveryDocument.getAdditionalInfo().setReceivingTier(ReceivingTier.HIGH);
    }
    return deliveryDocumentList;
  }

  private FdeCreateContainerResponse getFdeCreateContainerResponse() {
    return gson.fromJson(fdeCreateContainerResponseString, FdeCreateContainerResponse.class);
  }

  private FdeCreateContainerRequest getFdeCreateContainerRequest() {
    return gson.fromJson(fdeCreateContainerRequestString, FdeCreateContainerRequest.class);
  }
}
