package com.walmart.move.nim.receiving.rdc.service;

import static com.walmart.move.nim.receiving.rdc.constants.RdcConstants.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.client.hawkeye.HawkeyeRestApiClient;
import com.walmart.move.nim.receiving.core.client.hawkeye.model.HawkeyeGetLpnsRequest;
import com.walmart.move.nim.receiving.core.client.nimrds.NimRDSRestApiClient;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ContainerOrder;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceiveContainersRequestBody;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceiveContainersResponseBody;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceivedContainer;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.*;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.instruction.InstructionDownloadDistributionsDTO;
import com.walmart.move.nim.receiving.core.model.instruction.InstructionDownloadItemDTO;
import com.walmart.move.nim.receiving.core.model.instruction.LabelDataAllocationDTO;
import com.walmart.move.nim.receiving.core.model.printlabel.LabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingDivertLocations;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponse;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.label.LabelConstants;
import com.walmart.move.nim.receiving.rdc.label.LabelFormat;
import com.walmart.move.nim.receiving.rdc.mock.data.MockDeliveryDocuments;
import com.walmart.move.nim.receiving.rdc.mock.data.MockInstructionRequest;
import com.walmart.move.nim.receiving.rdc.mock.data.MockRdsResponse;
import com.walmart.move.nim.receiving.rdc.model.LabelInstructionStatus;
import com.walmart.move.nim.receiving.rdc.model.wft.RdcInstructionType;
import com.walmart.move.nim.receiving.rdc.model.wft.WFTInstruction;
import com.walmart.move.nim.receiving.rdc.utils.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.platform.messages.MetaData;
import com.walmart.platform.repositories.OutboxEvent;
import com.walmart.platform.repositories.PayloadRef;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RdcDaServiceTest {

  @Mock private RdcInstructionUtils rdcInstructionUtils;
  @Mock private RdcReceivingUtils rdcReceivingUtils;
  @Mock private NimRDSRestApiClient nimRDSRestApiClient;
  @Mock private NimRdsService nimRdsService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private RdcContainerUtils rdcContainerUtils;
  @Mock private PrintJobService printJobService;
  @Mock private InstructionPersisterService instructionPersisterService;
  @Mock private RdcManagedConfig rdcManagedConfig;
  @Mock private AppConfig appConfig;
  @Mock private ProblemServiceFixit problemServiceFixit;
  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private ContainerService containerService;
  @Mock private RdcLpnUtils rdcLpnUtils;
  @Mock private HawkeyeRestApiClient hawkeyeRestApiClient;
  @InjectMocks private RdcDaService rdcDaService;
  private HttpHeaders httpHeaders;
  private String facilityNum = "32818";
  private String facilityCountryCode = "us";
  private Gson gson;
  private Pair<DeliveryDocument, Long> deliveryDocumentLongPair;
  @Mock private LabelDataService labelDataService;
  @Mock private SlottingDivertLocations slottingDivertLocations;
  @Mock private RdcSlottingUtils rdcSlottingUtils;
  @Mock private ReceiptService receiptService;
  @Mock private LPNCacheService lpnCacheService;
  private final List<String> VALID_ASRS_LIST =
      Arrays.asList(
          ReceivingConstants.SYM_BRKPK_ASRS_VALUE, ReceivingConstants.SYM_CASE_PACK_ASRS_VALUE);
  private final List<String> VALID_PACKTYPE_HANDLING_CODE_COMBINATIONS =
      Arrays.asList("CC", "CI", "CJ");
  private final List<String> VALID_PACKTYPE_HANDLING_CODE_COMBINATIONS_LABEL_FORMAT =
      Arrays.asList("BC", "BM", "CN", "BN", "CB", "CL", "CV", "BV");

  @Mock private RdcAsyncUtils rdcAsyncUtils;
  @Mock private RdcLabelGenerationService rdcLabelGenerationService;
  @Mock private LabelDownloadEventService labelDownloadEventService;
  @Mock private RdcSSTKLabelGenerationUtils rdcSSTKLabelGenerationUtils;

  @BeforeClass
  public void initMocks() {
    gson = new Gson();
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(rdcDaService, "gson", gson);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    TenantContext.setFacilityCountryCode(facilityCountryCode);
    TenantContext.setCorrelationId("2323-323dsds-323dwsd-3d23e");
  }

  @BeforeMethod
  public void setup() {
    httpHeaders = MockHttpHeaders.getHeaders(facilityNum, facilityCountryCode);
    httpHeaders.add(RdcConstants.WFT_LOCATION_ID, "23");
    httpHeaders.add(RdcConstants.WFT_LOCATION_TYPE, "DOOR-23");
    httpHeaders.add(RdcConstants.WFT_SCC_CODE, "0086623");
  }

  @AfterMethod
  public void resetMocks() {
    reset(
        rdcInstructionUtils,
        rdcReceivingUtils,
        nimRdsService,
        nimRDSRestApiClient,
        tenantSpecificConfigReader,
        rdcContainerUtils,
        printJobService,
        instructionPersisterService,
        problemServiceFixit,
        receiptService,
        containerPersisterService,
        containerService,
        labelDataService,
        appConfig,
        rdcLpnUtils,
        slottingDivertLocations,
        lpnCacheService,
        hawkeyeRestApiClient,
        rdcAsyncUtils,
        rdcManagedConfig,
        rdcLabelGenerationService,
        rdcSSTKLabelGenerationUtils);
  }

  @Test
  public void testOverageAlertInstruction() throws IOException, ReceivingException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDAReceivingFeatureType();
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    when(rdcReceivingUtils.checkIfMaxOverageReceived(
            any(DeliveryDocument.class), anyLong(), anyInt()))
        .thenReturn(Boolean.TRUE);
    when(rdcInstructionUtils.getOverageAlertInstruction(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(getMockOverageAlertInstruction());
    doNothing().when(rdcReceivingUtils).overrideItemProperties(any(DeliveryDocument.class));
    InstructionResponse instructionResponse =
        rdcDaService.createInstructionForDACaseReceiving(instructionRequest, 379L, httpHeaders);

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getOpenQty());
    assertNotNull(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getTotalReceivedQty());
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.OVERAGE_ALERT_RECEIVING.getInstructionCode());
    verify(rdcReceivingUtils, times(1))
        .checkIfMaxOverageReceived(any(DeliveryDocument.class), anyLong(), anyInt());
    verify(rdcInstructionUtils, times(1))
        .getOverageAlertInstruction(any(InstructionRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testReceiveDAInstructionForVendorComplianceRequired()
      throws IOException, ReceivingException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDAReceivingFeatureType();
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    when(rdcReceivingUtils.checkIfMaxOverageReceived(
            any(DeliveryDocument.class), anyLong(), anyInt()))
        .thenReturn(Boolean.FALSE);
    doNothing()
        .when(rdcReceivingUtils)
        .isPoAndPoLineInReceivableStatus(any(DeliveryDocumentLine.class));
    when(rdcReceivingUtils.validateBreakPackItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());
    when(rdcReceivingUtils.validateRtsPutItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class),
            any(HttpHeaders.class)))
        .thenReturn(new InstructionResponseImplNew());
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(getRegulatedItemInstructionResponse());
    doNothing().when(rdcReceivingUtils).overrideItemProperties(any(DeliveryDocument.class));
    InstructionResponse instructionResponse =
        rdcDaService.createInstructionForDACaseReceiving(instructionRequest, 10L, httpHeaders);

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNull(instructionResponse.getInstruction());

    verify(rdcReceivingUtils, times(1))
        .checkIfMaxOverageReceived(any(DeliveryDocument.class), anyLong(), anyInt());
    verify(rdcInstructionUtils, times(0))
        .getOverageAlertInstruction(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(rdcReceivingUtils, times(1))
        .isPoAndPoLineInReceivableStatus(any(DeliveryDocumentLine.class));
    verify(rdcReceivingUtils, times(1))
        .checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class));
  }

  @Test
  public void testReceiveDAInstructionForBreakPackConveyPicks_DisplayPopUpMessage()
      throws IOException, ReceivingException {
    InstructionResponse mockInstructionResponse = new InstructionResponseImplNew();
    Instruction instruction = new Instruction();
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDAReceivingFeatureType();
    instruction.setInstructionCode(
        RdcInstructionType.DA_RECEIVING_PACK_TYPE_VALIDATION.getInstructionCode());
    instruction.setInstructionMsg(
        RdcInstructionType.DA_RECEIVING_PACK_TYPE_VALIDATION.getInstructionMsg());

    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("B");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("M");
    instructionRequest.setDeliveryDocuments(deliveryDocuments);

    mockInstructionResponse.setDeliveryDocuments(deliveryDocuments);
    mockInstructionResponse.setInstruction(instruction);
    when(rdcReceivingUtils.checkIfMaxOverageReceived(
            any(DeliveryDocument.class), anyLong(), anyInt()))
        .thenReturn(Boolean.FALSE);
    doNothing()
        .when(rdcReceivingUtils)
        .isPoAndPoLineInReceivableStatus(any(DeliveryDocumentLine.class));
    when(rdcReceivingUtils.validateBreakPackItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class)))
        .thenReturn(mockInstructionResponse);
    doNothing().when(rdcReceivingUtils).overrideItemProperties(any(DeliveryDocument.class));
    when(rdcReceivingUtils.validateRtsPutItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class),
            any(HttpHeaders.class)))
        .thenReturn(new InstructionResponseImplNew());
    doNothing().when(rdcReceivingUtils).overrideItemProperties(any(DeliveryDocument.class));
    InstructionResponse instructionResponse =
        rdcDaService.createInstructionForDACaseReceiving(instructionRequest, 10L, httpHeaders);

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.DA_RECEIVING_PACK_TYPE_VALIDATION.getInstructionCode());
    assertEquals(
        instructionResponse.getInstruction().getInstructionMsg(),
        RdcInstructionType.DA_RECEIVING_PACK_TYPE_VALIDATION.getInstructionMsg());

    verify(rdcReceivingUtils, times(1))
        .checkIfMaxOverageReceived(any(DeliveryDocument.class), anyLong(), anyInt());
    verify(rdcInstructionUtils, times(0))
        .getOverageAlertInstruction(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(rdcReceivingUtils, times(1))
        .isPoAndPoLineInReceivableStatus(any(DeliveryDocumentLine.class));
    verify(rdcReceivingUtils, times(1))
        .validateBreakPackItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class));
  }

  @Test
  public void testReceiveDAInstructionForBreakPackConveyPicks_ReceiveContainers()
      throws IOException, ReceivingException, ExecutionException, InterruptedException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    InstructionResponse mockInstructionResponse = new InstructionResponseImplNew();
    Instruction instruction = new Instruction();
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDAReceivingFeatureType();
    instruction.setInstructionCode(
        RdcInstructionType.DA_RECEIVING_PACK_TYPE_VALIDATION.getInstructionCode());
    instruction.setInstructionMsg(
        RdcInstructionType.DA_RECEIVING_PACK_TYPE_VALIDATION.getInstructionMsg());
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("B");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("M");
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode(RdcConstants.DA_BREAK_PACK_CONVEY_PICKS_ITEM_HANDLING_CODE);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setBreakPackValidationRequired(Boolean.TRUE);
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    mockInstructionResponse.setInstruction(instruction);
    when(rdcReceivingUtils.checkIfMaxOverageReceived(
            any(DeliveryDocument.class), anyLong(), anyInt()))
        .thenReturn(Boolean.FALSE);
    doNothing()
        .when(rdcReceivingUtils)
        .isPoAndPoLineInReceivableStatus(any(DeliveryDocumentLine.class));
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(mockInstructionResponse);
    when(rdcReceivingUtils.validateBreakPackItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class)))
        .thenReturn(mockInstructionResponse);
    when(rdcReceivingUtils.validateRtsPutItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class),
            any(HttpHeaders.class)))
        .thenReturn(new InstructionResponseImplNew());
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(rdcReceivingUtils.checkIfNonConveyableItem(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class)))
        .thenReturn(mockInstructionResponse);
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            nullable(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(2);
    when(nimRdsService.getReceiveContainersRequestBodyForDAReceiving(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            anyInt(),
            any(ReceiveInstructionRequest.class)))
        .thenReturn(getMockRdsContainerRequest());
    when(containerPersisterService.findAllItemByTrackingId(anyList())).thenReturn(null);
    when(containerService.getContainerListByTrackingIdList(anyList())).thenReturn(null);
    when(rdcReceivingUtils.receiveContainers(
            anyInt(),
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            eq(null)))
        .thenReturn(MockRdsResponse.getRdsResponseForDABreakConveyPacks().getReceived());
    when(tenantSpecificConfigReader.getDCTimeZone(anyInt())).thenReturn("US/Eastern");
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);
    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());
    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            any(Container.class),
            eq(null)))
        .thenReturn(new Container());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    doNothing().when(rdcReceivingUtils).overrideItemProperties(any(DeliveryDocument.class));
    InstructionResponse instructionResponse =
        rdcDaService.createInstructionForDACaseReceiving(instructionRequest, 10L, httpHeaders);

    assertNotNull(instructionResponse);
    assertEquals(instructionResponse.getDeliveryDocuments().size(), 1);
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.DA_WORK_STATION_SCAN_TO_PRINT_RECEIVING.getInstructionCode());

    verify(rdcReceivingUtils, times(1))
        .checkIfMaxOverageReceived(any(DeliveryDocument.class), anyLong(), anyInt());
    verify(rdcInstructionUtils, times(0))
        .getOverageAlertInstruction(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(rdcReceivingUtils, times(1))
        .isPoAndPoLineInReceivableStatus(any(DeliveryDocumentLine.class));
    verify(rdcReceivingUtils, times(1))
        .validateBreakPackItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class));
    verify(rdcReceivingUtils, times(1))
        .checkIfNonConveyableItem(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            nullable(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcContainerUtils, times(2))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null));
    verify(rdcContainerUtils, times(2))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(
            anyList(), anyList(), anyList(), anyList(), nullable(List.class));
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(containerPersisterService, times(1)).findAllItemByTrackingId(anyList());
    verify(containerService, times(1)).getContainerListByTrackingIdList(anyList());
  }

  @Test
  public void testReceiveDAInstructionForNonConHandlingCode_DisplayPopUpMessage()
      throws IOException, ReceivingException {
    InstructionResponse mockInstructionResponse = new InstructionResponseImplNew();
    Instruction instruction = new Instruction();
    String handlingCode = "N";
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode(handlingCode);
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDAReceivingFeatureType();
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    instruction.setInstructionCode(
        RdcInstructionType.DA_RECEIVING_PACK_TYPE_VALIDATION.getInstructionCode());
    instruction.setInstructionMsg(
        String.format(
            RdcConstants.NON_CON_HANDLING_CODES_INFO_MESSAGE,
            RdcConstants.DA_NON_CON_HANDLING_CODES_MAP.get(handlingCode)));
    mockInstructionResponse.setDeliveryDocuments(deliveryDocuments);
    mockInstructionResponse.setInstruction(instruction);
    when(rdcReceivingUtils.checkIfMaxOverageReceived(
            any(DeliveryDocument.class), anyLong(), anyInt()))
        .thenReturn(Boolean.FALSE);
    doNothing()
        .when(rdcReceivingUtils)
        .isPoAndPoLineInReceivableStatus(any(DeliveryDocumentLine.class));
    when(rdcReceivingUtils.validateBreakPackItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());
    when(rdcReceivingUtils.validateRtsPutItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class),
            any(HttpHeaders.class)))
        .thenReturn(new InstructionResponseImplNew());
    doNothing().when(rdcReceivingUtils).overrideItemProperties(any(DeliveryDocument.class));
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());
    when(rdcReceivingUtils.checkIfNonConveyableItem(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class)))
        .thenReturn(mockInstructionResponse);
    InstructionResponse instructionResponse =
        rdcDaService.createInstructionForDACaseReceiving(instructionRequest, 10L, httpHeaders);

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.DA_RECEIVING_PACK_TYPE_VALIDATION.getInstructionCode());
    assertEquals(
        instructionResponse.getInstruction().getInstructionMsg(),
        String.format(
            RdcConstants.NON_CON_HANDLING_CODES_INFO_MESSAGE,
            RdcConstants.DA_NON_CON_HANDLING_CODES_MAP.get(handlingCode)));

    verify(rdcReceivingUtils, times(1))
        .checkIfMaxOverageReceived(any(DeliveryDocument.class), anyLong(), anyInt());
    verify(rdcInstructionUtils, times(0))
        .getOverageAlertInstruction(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(rdcReceivingUtils, times(1))
        .isPoAndPoLineInReceivableStatus(any(DeliveryDocumentLine.class));
    verify(rdcReceivingUtils, times(1))
        .validateBreakPackItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class));
    verify(rdcReceivingUtils, times(1))
        .checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class));
    verify(rdcReceivingUtils, times(1))
        .checkIfNonConveyableItem(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class));
  }

  @Test
  public void testReceiveDAInstructionForNonConHandlingCode_ReceiveContainers()
      throws IOException, ReceivingException, ExecutionException, InterruptedException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    InstructionResponse mockInstructionResponse = new InstructionResponseImplNew();
    Instruction instruction = new Instruction();
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDAReceivingFeatureType();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("N");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setNonConValidationRequired(Boolean.TRUE);
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    instruction.setInstructionCode(
        RdcInstructionType.DA_RECEIVING_PACK_TYPE_VALIDATION.getInstructionCode());
    instruction.setInstructionMsg(
        RdcInstructionType.DA_RECEIVING_PACK_TYPE_VALIDATION.getInstructionMsg());
    mockInstructionResponse.setInstruction(instruction);
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    when(rdcReceivingUtils.checkIfMaxOverageReceived(
            any(DeliveryDocument.class), anyLong(), anyInt()))
        .thenReturn(Boolean.FALSE);
    when(rdcInstructionUtils.isProblemTagValidationApplicable(any())).thenReturn(true);
    doNothing()
        .when(rdcReceivingUtils)
        .isPoAndPoLineInReceivableStatus(any(DeliveryDocumentLine.class));
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(mockInstructionResponse);
    when(rdcReceivingUtils.validateBreakPackItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class)))
        .thenReturn(mockInstructionResponse);
    when(rdcReceivingUtils.validateRtsPutItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class),
            any(HttpHeaders.class)))
        .thenReturn(new InstructionResponseImplNew());
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(containerPersisterService.findAllItemByTrackingId(anyList())).thenReturn(null);
    when(containerService.getContainerListByTrackingIdList(anyList())).thenReturn(null);
    when(rdcReceivingUtils.checkIfNonConveyableItem(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class)))
        .thenReturn(mockInstructionResponse);
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            nullable(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(2);
    when(nimRdsService.getReceiveContainersRequestBodyForDAReceiving(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            anyInt(),
            any(ReceiveInstructionRequest.class)))
        .thenReturn(getMockRdsContainerRequest());
    when(rdcReceivingUtils.receiveContainers(
            anyInt(),
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            eq(null)))
        .thenReturn(MockRdsResponse.getRdsResponseForDABreakConveyPacks().getReceived());
    when(tenantSpecificConfigReader.getDCTimeZone(anyInt())).thenReturn("US/Eastern");
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);
    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());
    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            any(Container.class),
            eq(null)))
        .thenReturn(new Container());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    doNothing().when(rdcReceivingUtils).overrideItemProperties(any(DeliveryDocument.class));
    InstructionResponse instructionResponse =
        rdcDaService.createInstructionForDACaseReceiving(instructionRequest, 10L, httpHeaders);

    assertNotNull(instructionResponse);
    assertEquals(instructionResponse.getDeliveryDocuments().size(), 1);
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.DA_WORK_STATION_SCAN_TO_PRINT_RECEIVING.getInstructionCode());

    verify(rdcReceivingUtils, times(1))
        .checkIfMaxOverageReceived(any(DeliveryDocument.class), anyLong(), anyInt());
    verify(rdcInstructionUtils, times(0))
        .getOverageAlertInstruction(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(rdcReceivingUtils, times(1))
        .isPoAndPoLineInReceivableStatus(any(DeliveryDocumentLine.class));
    verify(rdcReceivingUtils, times(1))
        .validateBreakPackItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class));
    verify(rdcReceivingUtils, times(1))
        .checkIfNonConveyableItem(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            nullable(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcContainerUtils, times(2))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null));
    verify(rdcContainerUtils, times(2))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(
            anyList(), anyList(), anyList(), anyList(), nullable(List.class));
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
  }

  @Test
  public void testReceiveDAInstructionSuccessDACasePack()
      throws IOException, ReceivingException, ExecutionException, InterruptedException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDAReceivingFeatureType();
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(Boolean.FALSE);
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(121);
    deliveryDocumentLongPair = new Pair<>(deliveryDocuments.get(0), 10L);
    when(rdcInstructionUtils.isProblemTagValidationApplicable(any())).thenReturn(true);
    when(rdcReceivingUtils.checkIfMaxOverageReceived(
            deliveryDocumentLongPair.getKey(),
            deliveryDocumentLongPair.getValue(),
            RdcConstants.RDC_DA_CASE_RECEIVE_QTY))
        .thenReturn(Boolean.FALSE);
    doNothing()
        .when(rdcReceivingUtils)
        .isPoAndPoLineInReceivableStatus(any(DeliveryDocumentLine.class));
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            nullable(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);
    when(containerPersisterService.findAllItemByTrackingId(anyList())).thenReturn(null);
    when(containerService.getContainerListByTrackingIdList(anyList())).thenReturn(null);
    when(nimRdsService.getReceiveContainersRequestBodyForDAReceiving(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            anyInt(),
            any(ReceiveInstructionRequest.class)))
        .thenReturn(getMockRdsContainerRequest());
    when(rdcReceivingUtils.receiveContainers(
            anyInt(),
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            eq(null)))
        .thenReturn(MockRdsResponse.getRdsResponseForDABreakPack().getReceived());
    when(tenantSpecificConfigReader.getDCTimeZone(anyInt())).thenReturn("US/Eastern");
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);
    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());
    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            any(Container.class),
            eq(null)))
        .thenReturn(new Container());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(rdcReceivingUtils.validateBreakPackItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());
    when(rdcReceivingUtils.validateRtsPutItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class),
            any(HttpHeaders.class)))
        .thenReturn(new InstructionResponseImplNew());
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());
    doNothing().when(rdcReceivingUtils).overrideItemProperties(any(DeliveryDocument.class));
    when(rdcReceivingUtils.checkIfNonConveyableItem(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());

    InstructionResponse instructionResponse =
        rdcDaService.createInstructionForDACaseReceiving(
            instructionRequest, deliveryDocumentLongPair.getValue(), httpHeaders);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(response);
    assertEquals(response.getDeliveryDocuments().size(), 1);
    assertNotNull(response.getInstruction());
    assertNotNull(response);
    assertEquals(response.getPrintJob().size(), 3);
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_HEADERS_KEY));
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_CLIENT_ID_KEY));
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_REQUEST_KEY));
    assertEquals(
        response.getPrintJob().get(ReceivingConstants.PRINT_CLIENT_ID_KEY),
        ReceivingConstants.ATLAS_RECEIVING);
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_REQUEST_KEY));

    List<PrintLabelRequest> printLabelRequests =
        (List<PrintLabelRequest>) response.getPrintJob().get(ReceivingConstants.PRINT_REQUEST_KEY);
    assertEquals(printLabelRequests.size(), 1);

    when(rdcReceivingUtils.checkIfMaxOverageReceived(
            deliveryDocumentLongPair.getKey(),
            deliveryDocumentLongPair.getValue(),
            RdcConstants.RDC_DA_CASE_RECEIVE_QTY))
        .thenReturn(Boolean.FALSE);
    verify(rdcInstructionUtils, times(0))
        .getOverageAlertInstruction(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(rdcReceivingUtils, times(1))
        .isPoAndPoLineInReceivableStatus(any(DeliveryDocumentLine.class));
    verify(rdcReceivingUtils, times(1))
        .checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            nullable(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .validateBreakPackItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class));
    verify(rdcReceivingUtils, times(1))
        .checkIfNonConveyableItem(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null));
    verify(rdcContainerUtils, times(1))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(
            anyList(), anyList(), anyList(), anyList(), nullable(List.class));
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
  }

  @Test
  public void testReceiveDAQtyReceivingInstructionSuccessDACasePack_NothingReceivedInRDS()
      throws IOException, ReceivingException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDAQtyReceiving();
    Instruction instruction = new Instruction();
    instruction.setInstructionCode(RdcInstructionType.DA_QTY_RECEIVING.getInstructionCode());
    instruction.setInstructionMsg(RdcInstructionType.DA_QTY_RECEIVING.getInstructionMsg());
    instruction.setProjectedReceiveQty(0);
    instruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    when(rdcManagedConfig.getDaQtyReceiveMaxLimit()).thenReturn(30);
    when(rdcReceivingUtils.checkIfMaxOverageReceived(
            any(DeliveryDocument.class), anyLong(), anyInt()))
        .thenReturn(Boolean.FALSE);
    doNothing()
        .when(rdcReceivingUtils)
        .isPoAndPoLineInReceivableStatus(any(DeliveryDocumentLine.class));
    when(rdcReceivingUtils.validateBreakPackItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(instruction);
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());
    when(containerPersisterService.findAllItemByTrackingId(anyList())).thenReturn(null);
    when(containerService.getContainerListByTrackingIdList(anyList())).thenReturn(null);

    doNothing().when(rdcReceivingUtils).overrideItemProperties(any(DeliveryDocument.class));
    InstructionResponse instructionResponse =
        rdcDaService.createInstructionForDACaseReceiving(instructionRequest, 0L, httpHeaders);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(response);
    assertEquals(response.getDeliveryDocuments().size(), 1);
    assertNotNull(response.getInstruction());
    assertEquals(
        response.getInstruction().getInstructionCode(),
        RdcInstructionType.DA_QTY_RECEIVING.getInstructionCode());
    assertEquals(
        response.getInstruction().getInstructionMsg(),
        RdcInstructionType.DA_QTY_RECEIVING.getInstructionMsg());
    assertEquals(response.getInstruction().getProjectedReceiveQty(), 30);
    assertEquals(
        response.getInstruction().getProjectedReceiveQtyUOM(), ReceivingConstants.Uom.VNPK);
    assertEquals(
        response
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getTotalReceivedQty()
            .intValue(),
        0);
    assertEquals(
        response
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getOpenQty()
            .intValue(),
        378);
    assertEquals(
        response
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getMaxReceiveQty()
            .intValue(),
        378);

    verify(rdcInstructionUtils, times(0))
        .getOverageAlertInstruction(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(rdcReceivingUtils, times(1))
        .isPoAndPoLineInReceivableStatus(any(DeliveryDocumentLine.class));
    verify(rdcReceivingUtils, times(1))
        .checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class));
    verify(rdcReceivingUtils, times(1))
        .validateBreakPackItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class));
    verify(instructionPersisterService, times(0)).saveInstruction(any(Instruction.class));
  }

  @Test
  public void
      testReceiveDAQtyReceivingInstructionSuccessDACasePack_SomethingIsAlreadyReceivedInRDS()
          throws IOException, ReceivingException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDAQtyReceiving();
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    Instruction instruction = new Instruction();
    instruction.setInstructionCode(RdcInstructionType.DA_QTY_RECEIVING.getInstructionCode());
    instruction.setInstructionMsg(RdcInstructionType.DA_QTY_RECEIVING.getInstructionMsg());
    instruction.setProjectedReceiveQty(0);
    instruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    when(rdcManagedConfig.getDaQtyReceiveMaxLimit()).thenReturn(30);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    when(rdcReceivingUtils.checkIfMaxOverageReceived(
            any(DeliveryDocument.class), anyLong(), anyInt()))
        .thenReturn(Boolean.FALSE);
    doNothing()
        .when(rdcReceivingUtils)
        .isPoAndPoLineInReceivableStatus(any(DeliveryDocumentLine.class));
    when(rdcReceivingUtils.validateBreakPackItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(instruction);
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());
    doNothing().when(rdcReceivingUtils).overrideItemProperties(any(DeliveryDocument.class));

    InstructionResponse instructionResponse =
        rdcDaService.createInstructionForDACaseReceiving(instructionRequest, 100L, httpHeaders);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(response);
    assertTrue(response.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getInstruction());
    assertEquals(
        response.getInstruction().getInstructionCode(),
        RdcInstructionType.DA_QTY_RECEIVING.getInstructionCode());
    assertEquals(
        response.getInstruction().getInstructionMsg(),
        RdcInstructionType.DA_QTY_RECEIVING.getInstructionMsg());
    assertEquals(response.getInstruction().getProjectedReceiveQty(), 30);
    assertEquals(
        response.getInstruction().getProjectedReceiveQtyUOM(), ReceivingConstants.Uom.VNPK);
    assertEquals(
        response
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getTotalReceivedQty()
            .intValue(),
        100);
    assertEquals(
        response
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getOpenQty()
            .intValue(),
        278);
    assertEquals(
        response
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getMaxReceiveQty()
            .intValue(),
        378);

    verify(rdcInstructionUtils, times(0))
        .getOverageAlertInstruction(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(rdcReceivingUtils, times(1))
        .isPoAndPoLineInReceivableStatus(any(DeliveryDocumentLine.class));
    verify(rdcReceivingUtils, times(1))
        .checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class));
    verify(rdcReceivingUtils, times(1))
        .validateBreakPackItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class));
    verify(instructionPersisterService, times(0)).saveInstruction(any(Instruction.class));
  }

  @Test
  public void
      testReceiveDAQtyReceivingInstructionSuccessDACasePack_OpenQtyLessThanProjectedQtyLimit()
          throws IOException, ReceivingException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDAQtyReceiving();
    Instruction instruction = new Instruction();
    instruction.setInstructionCode(RdcInstructionType.DA_QTY_RECEIVING.getInstructionCode());
    instruction.setInstructionMsg(RdcInstructionType.DA_QTY_RECEIVING.getInstructionMsg());
    instruction.setProjectedReceiveQty(0);
    instruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    when(rdcManagedConfig.getDaQtyReceiveMaxLimit()).thenReturn(30);
    when(rdcReceivingUtils.checkIfMaxOverageReceived(
            any(DeliveryDocument.class), anyLong(), anyInt()))
        .thenReturn(Boolean.FALSE);
    doNothing()
        .when(rdcReceivingUtils)
        .isPoAndPoLineInReceivableStatus(any(DeliveryDocumentLine.class));
    when(rdcReceivingUtils.validateBreakPackItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(instruction);
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());
    doNothing().when(rdcReceivingUtils).overrideItemProperties(any(DeliveryDocument.class));
    InstructionResponse instructionResponse =
        rdcDaService.createInstructionForDACaseReceiving(instructionRequest, 360L, httpHeaders);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(response);
    assertTrue(response.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getInstruction());
    assertEquals(
        response.getInstruction().getInstructionCode(),
        RdcInstructionType.DA_QTY_RECEIVING.getInstructionCode());
    assertEquals(
        response.getInstruction().getInstructionMsg(),
        RdcInstructionType.DA_QTY_RECEIVING.getInstructionMsg());
    assertEquals(response.getInstruction().getProjectedReceiveQty(), 18);
    assertEquals(
        response.getInstruction().getProjectedReceiveQtyUOM(), ReceivingConstants.Uom.VNPK);
    assertEquals(
        response
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getTotalReceivedQty()
            .intValue(),
        360);
    assertEquals(
        response
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getOpenQty()
            .intValue(),
        18);
    assertEquals(
        response
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getMaxReceiveQty()
            .intValue(),
        378);

    verify(rdcInstructionUtils, times(0))
        .getOverageAlertInstruction(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(rdcReceivingUtils, times(1))
        .isPoAndPoLineInReceivableStatus(any(DeliveryDocumentLine.class));
    verify(rdcReceivingUtils, times(1))
        .checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class));
    verify(rdcReceivingUtils, times(1))
        .validateBreakPackItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class));
    verify(instructionPersisterService, times(0)).saveInstruction(any(Instruction.class));
    assertFalse(
        response
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .isMaxAllowedOverageQtyIncluded());
  }

  @Test
  public void
      testReceiveDAQtyReceivingInstructionSuccessDACasePack_MaxAllowedOverageIncludedForQtyReceiving()
          throws IOException, ReceivingException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDAQtyReceiving();
    Instruction instruction = new Instruction();
    instruction.setInstructionCode(RdcInstructionType.DA_QTY_RECEIVING.getInstructionCode());
    instruction.setInstructionMsg(RdcInstructionType.DA_QTY_RECEIVING.getInstructionMsg());
    instruction.setProjectedReceiveQty(0);
    instruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    when(rdcManagedConfig.getDaQtyReceiveMaxLimit()).thenReturn(30);
    when(rdcReceivingUtils.checkIfMaxOverageReceived(
            any(DeliveryDocument.class), anyLong(), anyInt()))
        .thenReturn(Boolean.FALSE);
    doNothing()
        .when(rdcReceivingUtils)
        .isPoAndPoLineInReceivableStatus(any(DeliveryDocumentLine.class));
    when(rdcReceivingUtils.validateBreakPackItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(instruction);
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());
    doNothing().when(rdcReceivingUtils).overrideItemProperties(any(DeliveryDocument.class));
    InstructionResponse instructionResponse =
        rdcDaService.createInstructionForDACaseReceiving(instructionRequest, 378L, httpHeaders);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(response);
    assertTrue(response.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getInstruction());
    assertEquals(
        response.getInstruction().getInstructionCode(),
        RdcInstructionType.DA_QTY_RECEIVING.getInstructionCode());
    assertEquals(
        response.getInstruction().getInstructionMsg(),
        RdcInstructionType.DA_QTY_RECEIVING.getInstructionMsg());
    assertEquals(response.getInstruction().getProjectedReceiveQty(), 0);
    assertEquals(
        response.getInstruction().getProjectedReceiveQtyUOM(), ReceivingConstants.Uom.VNPK);
    assertEquals(
        response
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getTotalReceivedQty()
            .intValue(),
        378);
    assertEquals(
        response
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getOpenQty()
            .intValue(),
        0);
    assertEquals(
        response
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getMaxReceiveQty()
            .intValue(),
        378);
    assertTrue(
        response
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .isMaxAllowedOverageQtyIncluded());

    verify(rdcInstructionUtils, times(0))
        .getOverageAlertInstruction(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(rdcReceivingUtils, times(1))
        .isPoAndPoLineInReceivableStatus(any(DeliveryDocumentLine.class));
    verify(rdcReceivingUtils, times(1))
        .checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class));
    verify(rdcReceivingUtils, times(1))
        .validateBreakPackItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class));
    verify(instructionPersisterService, times(0)).saveInstruction(any(Instruction.class));
  }

  @Test
  public void testReceiveDAInstructionSuccessDABreakPack()
      throws IOException, ReceivingException, ExecutionException, InterruptedException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDAReceivingFeatureType();
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(Boolean.FALSE);
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(121);
    when(rdcReceivingUtils.checkIfMaxOverageReceived(
            any(DeliveryDocument.class), anyLong(), anyInt()))
        .thenReturn(Boolean.FALSE);
    doNothing()
        .when(rdcReceivingUtils)
        .isPoAndPoLineInReceivableStatus(any(DeliveryDocumentLine.class));
    when(nimRdsService.getReceiveContainersRequestBodyForDAReceiving(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            anyInt(),
            any(ReceiveInstructionRequest.class)))
        .thenReturn(getMockRdsContainerRequest());
    when(rdcReceivingUtils.receiveContainers(
            anyInt(),
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            eq(null)))
        .thenReturn(MockRdsResponse.getRdsResponseForDABreakPack().getReceived());
    when(tenantSpecificConfigReader.getDCTimeZone(anyInt())).thenReturn("US/Eastern");
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);
    when(containerPersisterService.findAllItemByTrackingId(anyList())).thenReturn(null);
    when(containerService.getContainerListByTrackingIdList(anyList())).thenReturn(null);
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            nullable(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);
    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());
    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            any(Container.class),
            eq(null)))
        .thenReturn(new Container());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(rdcReceivingUtils.validateBreakPackItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());
    when(rdcReceivingUtils.validateRtsPutItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class),
            any(HttpHeaders.class)))
        .thenReturn(new InstructionResponseImplNew());
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());
    when(rdcReceivingUtils.checkIfNonConveyableItem(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());

    doNothing().when(rdcReceivingUtils).overrideItemProperties(any(DeliveryDocument.class));

    InstructionResponse instructionResponse =
        rdcDaService.createInstructionForDACaseReceiving(instructionRequest, 10L, httpHeaders);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(response);
    assertTrue(response.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getInstruction());
    assertNotNull(response);
    assertEquals(response.getPrintJob().size(), 3);
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_HEADERS_KEY));
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_CLIENT_ID_KEY));
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_REQUEST_KEY));
    assertEquals(
        response.getPrintJob().get(ReceivingConstants.PRINT_CLIENT_ID_KEY),
        ReceivingConstants.ATLAS_RECEIVING);
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_REQUEST_KEY));

    List<PrintLabelRequest> printLabelRequests =
        (List<PrintLabelRequest>) response.getPrintJob().get(ReceivingConstants.PRINT_REQUEST_KEY);
    List<LabelData> labelDataList = printLabelRequests.get(0).getData();
    assertEquals(printLabelRequests.size(), 1);
    assertEquals(
        printLabelRequests
            .stream()
            .filter(
                printRequest ->
                    printRequest
                        .getFormatName()
                        .equals(LabelFormat.DA_CONVEYABLE_INDUCT_PUT.getFormat()))
            .count(),
        1);

    Optional<LabelData> labelSlot =
        labelDataList
            .stream()
            .filter(label -> label.getKey().equals(LabelConstants.LBL_SLOT))
            .findFirst();
    Optional<LabelData> labelSection =
        labelDataList
            .stream()
            .filter(label -> label.getKey().equals(LabelConstants.LBL_SECTION))
            .findFirst();
    Optional<LabelData> labelPlttag =
        labelDataList
            .stream()
            .filter(label -> label.getKey().equals(LabelConstants.LBL_PLTTAG))
            .findFirst();
    assertEquals(labelSlot.get().getValue(), RdcConstants.DA_P1001_SLOT);
    assertEquals(labelSection.get().getValue(), "2-8");
    assertEquals(labelPlttag.get().getValue(), "965124010015");

    when(rdcReceivingUtils.checkIfMaxOverageReceived(
            any(DeliveryDocument.class), anyLong(), anyInt()))
        .thenReturn(Boolean.FALSE);
    verify(rdcInstructionUtils, times(0))
        .getOverageAlertInstruction(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(rdcReceivingUtils, times(1))
        .isPoAndPoLineInReceivableStatus(any(DeliveryDocumentLine.class));
    verify(rdcReceivingUtils, times(1))
        .checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            nullable(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .validateBreakPackItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class));
    verify(rdcReceivingUtils, times(1))
        .checkIfNonConveyableItem(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null));
    verify(rdcContainerUtils, times(1))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(
            anyList(), anyList(), anyList(), anyList(), nullable(List.class));
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testReceiveDAInstructionError() throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDAReceivingFeatureType();
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CC");
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(Boolean.FALSE);
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(121);
    when(rdcReceivingUtils.checkIfMaxOverageReceived(
            any(DeliveryDocument.class), anyLong(), anyInt()))
        .thenReturn(Boolean.FALSE);
    doNothing()
        .when(rdcReceivingUtils)
        .isPoAndPoLineInReceivableStatus(any(DeliveryDocumentLine.class));

    when(rdcReceivingUtils.validateBreakPackItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());
    when(rdcReceivingUtils.validateRtsPutItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class),
            any(HttpHeaders.class)))
        .thenReturn(new InstructionResponseImplNew());
    when(nimRdsService.getReceiveContainersRequestBodyForDAReceiving(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            anyInt(),
            any(ReceiveInstructionRequest.class)))
        .thenReturn(getMockRdsContainerRequest());
    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.INVALID_QUANTITY_CORRECTION_REQ,
                String.format(
                    ReceivingConstants.SLOTTING_QTY_CORRECTION_BAD_RESPONSE_ERROR_MSG,
                    HttpStatus.BAD_REQUEST,
                    "Error from RDS")))
        .when(nimRDSRestApiClient)
        .receiveContainers(any(ReceiveContainersRequestBody.class), anyMap());
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());
    when(rdcReceivingUtils.checkIfNonConveyableItem(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());
    doNothing().when(rdcReceivingUtils).overrideItemProperties(any(DeliveryDocument.class));

    rdcDaService.createInstructionForDACaseReceiving(instructionRequest, 10L, httpHeaders);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testReceiveInstruction_DA_ForNonAtlasItem_RDSErrorThrowsException()
      throws IOException, ExecutionException, InterruptedException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            nullable(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(5);
    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.RECEIVED_QTY_SUMMARY_BY_POLINE_ERROR_FROM_RDS,
                ReceivingConstants.RDS_RESPONSE_ERROR_MSG))
        .when(rdcReceivingUtils)
        .receiveContainers(
            anyInt(),
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(ReceiveInstructionRequest.class));

    when(containerPersisterService.findAllItemByTrackingId(anyList())).thenReturn(null);
    when(containerService.getContainerListByTrackingIdList(anyList())).thenReturn(null);
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setSlotDetails(getMockAutoSlotDetails());
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            getMockInstructionRequest(receiveInstructionRequest, httpHeaders),
            2,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.DA_WORK_STATION_SCAN_TO_PRINT_RECEIVING.getInstructionCode());
    assertNotNull(response.getPrintJob());
    verify(rdcReceivingUtils, times(1))
        .receiveContainers(
            anyInt(),
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(ReceiveInstructionRequest.class));
    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());
    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            any(Container.class),
            null))
        .thenReturn(new Container());
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            nullable(ReceiveInstructionRequest.class),
            false);
    verify(rdcReceivingUtils, times(0))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    verify(rdcAsyncUtils, times(2)).labelUpdateToHawkeye(any(HttpHeaders.class), anyList());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testReceiveInstruction_DA_AutoSlotting_ForNonAtlasItem_RDSErrorThrowsException()
      throws IOException, ExecutionException, InterruptedException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(2);

    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.RECEIVED_QTY_SUMMARY_BY_POLINE_ERROR_FROM_RDS,
                ReceivingConstants.RDS_RESPONSE_ERROR_MSG))
        .when(rdcReceivingUtils)
        .receiveContainers(
            anyInt(),
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(ReceiveInstructionRequest.class));

    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            getMockInstructionRequest(receiveInstructionRequest, httpHeaders),
            5,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.DA_WORK_STATION_SCAN_TO_PRINT_RECEIVING.getInstructionCode());
    assertNotNull(response.getPrintJob());
    verify(rdcReceivingUtils, times(1))
        .receiveContainers(
            anyInt(),
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class), anyInt(), any(ReceiveInstructionRequest.class), false);
  }

  @Test
  public void testReceiveInstruction_DA_AutoSlotting_HappyPath_ForNonAtlasItem()
      throws IOException, ExecutionException, InterruptedException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(Long.valueOf(1234));
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    ReceiveContainersResponseBody receiveContainersResponseBody =
        MockRdsResponse.getRdsResponseForDABreakConveyPacks();
    List<ReceivedContainer> recievedContainerList = receiveContainersResponseBody.getReceived();
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_LABEL_FORMAT_VALIDATION_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(5);

    when(rdcReceivingUtils.receiveContainers(
            anyInt(),
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(ReceiveInstructionRequest.class)))
        .thenReturn(recievedContainerList);

    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);
    when(containerPersisterService.findAllItemByTrackingId(anyList())).thenReturn(null);
    when(containerService.getContainerListByTrackingIdList(anyList())).thenReturn(null);
    when(rdcContainerUtils.buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class)))
        .thenReturn(Arrays.asList(new ContainerItem()));
    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null)))
        .thenReturn(new Container());
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    doNothing()
        .when(rdcReceivingUtils)
        .publishInstruction(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean());
    doCallRealMethod()
        .when(rdcReceivingUtils)
        .getLabelFormatForPallet(any(DeliveryDocumentLine.class));
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setSlotDetails(getMockAutoSlotDetails());
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            getMockInstructionRequest(receiveInstructionRequest, httpHeaders),
            5,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(2))
        .buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class));
    verify(rdcContainerUtils, times(2))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(2)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(2)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    verify(rdcReceivingUtils, times(2))
        .publishInstruction(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean());
  }

  @Test
  public void testReceiveInstruction_DA_ManualSlotting_HappyPath()
      throws IOException, ExecutionException, InterruptedException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(Long.valueOf(1234));
    Instruction instruction = getMockDAInstruction();
    LinkedTreeMap<String, Object> move = new LinkedTreeMap<>();
    move.put(ReceivingConstants.MOVE_FROM_LOCATION, "D123");
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    ReceiveContainersResponseBody receiveContainersResponseBody =
        MockRdsResponse.getRdsResponseForDABreakConveyPacks();
    List<ReceivedContainer> recievedContainerList =
        Arrays.asList(receiveContainersResponseBody.getReceived().get(0));
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);
    when(rdcReceivingUtils.receiveContainers(
            anyInt(),
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(ReceiveInstructionRequest.class)))
        .thenReturn(recievedContainerList);

    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(instruction);
    when(rdcInstructionUtils.moveDetailsForInstruction(
            anyString(), any(DeliveryDocument.class), any(HttpHeaders.class)))
        .thenReturn(move);
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);

    when(rdcContainerUtils.buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class)))
        .thenReturn(Arrays.asList(new ContainerItem()));
    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null)))
        .thenReturn(new Container());
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    doNothing()
        .when(rdcReceivingUtils)
        .publishInstruction(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean());
    doCallRealMethod()
        .when(rdcReceivingUtils)
        .getLabelFormatForPallet(any(DeliveryDocumentLine.class));
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setSlotDetails(getMockAutoSlotDetails());
    receiveInstructionRequest.setPalletQuantities(null);

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            getMockInstructionRequest(receiveInstructionRequest, httpHeaders),
            5,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(1))
        .buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    verify(rdcReceivingUtils, times(1))
        .publishInstruction(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean());
    verify(rdcInstructionUtils, times(1))
        .moveDetailsForInstruction(
            anyString(), any(DeliveryDocument.class), any(HttpHeaders.class));
  }

  @Test
  public void testReceiveInstruction_DA_ManualSlotting_ProblemReporting_HappyPath()
      throws IOException, ExecutionException, InterruptedException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(Long.valueOf(1234));
    Instruction instruction = getMockDAInstruction();
    LinkedTreeMap<String, Object> move = new LinkedTreeMap<>();
    move.put(ReceivingConstants.MOVE_FROM_LOCATION, "D123");
    when(rdcManagedConfig.getDaQtyReceiveMaxLimit()).thenReturn(25);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    ReceiveContainersResponseBody receiveContainersResponseBody =
        MockRdsResponse.getRdsResponseForDABreakConveyPacks();
    List<ReceivedContainer> recievedContainerList =
        Arrays.asList(receiveContainersResponseBody.getReceived().get(0));
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);
    when(rdcReceivingUtils.receiveContainers(
            anyInt(),
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(ReceiveInstructionRequest.class)))
        .thenReturn(recievedContainerList);

    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(instruction);
    when(rdcInstructionUtils.moveDetailsForInstruction(
            anyString(), any(DeliveryDocument.class), any(HttpHeaders.class)))
        .thenReturn(move);
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);

    when(rdcContainerUtils.buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class)))
        .thenReturn(Arrays.asList(new ContainerItem()));
    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null)))
        .thenReturn(new Container());
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    doNothing()
        .when(rdcReceivingUtils)
        .publishInstruction(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean());
    doReturn(problemServiceFixit)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.PROBLEM_SERVICE), eq(ProblemService.class));
    doNothing().when(problemServiceFixit).completeProblem(any(Instruction.class));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setProblemTagId("23232323");
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setSlotDetails(getMockAutoSlotDetails());
    receiveInstructionRequest.setPalletQuantities(null);
    doCallRealMethod()
        .when(rdcReceivingUtils)
        .getLabelFormatForPallet(any(DeliveryDocumentLine.class));
    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            getMockInstructionRequest(receiveInstructionRequest, httpHeaders),
            5,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(1))
        .buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    verify(rdcReceivingUtils, times(1))
        .publishInstruction(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean());
    verify(rdcInstructionUtils, times(1))
        .moveDetailsForInstruction(
            anyString(), any(DeliveryDocument.class), any(HttpHeaders.class));
    verify(problemServiceFixit, times(1)).completeProblem(any(Instruction.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testReceiveInstruction_DA_ManualSlotting_ForNonAtlasItem_RDSErrorThrowsException()
      throws IOException, ExecutionException, InterruptedException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);

    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.RECEIVED_QTY_SUMMARY_BY_POLINE_ERROR_FROM_RDS,
                ReceivingConstants.RDS_RESPONSE_ERROR_MSG))
        .when(rdcReceivingUtils)
        .receiveContainers(
            anyInt(),
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(ReceiveInstructionRequest.class));
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    rdcDaService.receiveContainers(
        deliveryDocuments.get(0),
        httpHeaders,
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders),
        5,
        receiveInstructionRequest);
    verify(rdcReceivingUtils, times(1))
        .receiveContainers(
            anyInt(),
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(ReceiveInstructionRequest.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class), anyInt(), any(ReceiveInstructionRequest.class), false);
  }

  @Test
  public void testReceiveInstruction_DA_AutoSlotting_HappyPath_ForAtlasItem()
      throws IOException, ExecutionException, InterruptedException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(Long.valueOf(1234));
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(121);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CN");
    List<String> nonConveyableHandlingCodes = new ArrayList<>();
    nonConveyableHandlingCodes.add("CN");
    when(rdcManagedConfig.getAtlasDaNonConPackAndHandlingCodes())
        .thenReturn(nonConveyableHandlingCodes);
    List<ReceivedContainer> recievedContainerList = new ArrayList<>();
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setSlotDetails(getMockAutoSlotDetails());
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDAQtyReceiving();

    instructionRequest.setProblemTagId("06001647754402");
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SMART_SLOTTING_ENABLED_ON_DA_ITEMS,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);

    when(rdcReceivingUtils.receiveContainers(
            anyInt(),
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(ReceiveInstructionRequest.class)))
        .thenReturn(recievedContainerList);

    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);
    when(containerPersisterService.findAllItemByTrackingId(anyList())).thenReturn(null);
    when(containerService.getContainerListByTrackingIdList(anyList())).thenReturn(null);
    when(rdcContainerUtils.buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class)))
        .thenReturn(Arrays.asList(new ContainerItem()));
    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null)))
        .thenReturn(new Container());
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(rdcLpnUtils.getLPNs(anyInt(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList("a311121121"));

    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            anyString(), anyLong(), anyString(), anyInt(), anyInt(), anyString()))
        .thenReturn(getMockLabelDataSym());
    SlottingPalletResponse slottingPalletResponse = mock(SlottingPalletResponse.class);
    if (!slottingPalletResponse.getLocations().isEmpty()) {
      slottingPalletResponse.getLocations().get(0).setLocation("Location1");
    }
    when(slottingDivertLocations.getLocation()).thenReturn("Location1");
    List<SlottingDivertLocations> locations = new ArrayList<>();
    locations.add(slottingDivertLocations);
    when(slottingPalletResponse.getLocations()).thenReturn(locations);
    when(rdcSlottingUtils.receiveContainers(
            receiveInstructionRequest, "a311121121", httpHeaders, null))
        .thenReturn(slottingPalletResponse);
    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    doNothing()
        .when(rdcReceivingUtils)
        .publishInstruction(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean());
    Set labelTrackingIdsMock = mock(Set.class);
    when(printJobService.createPrintJob(anyLong(), any(), eq(labelTrackingIdsMock), any()))
        .thenReturn(printJob);
    when(rdcReceivingUtils.getLabelFormatForPallet(any(DeliveryDocumentLine.class)))
        .thenReturn(LabelFormat.ATLAS_RDC_PALLET);
    List<ReceivedContainer> mockReceivedContainers = mock(List.class);

    when(mockReceivedContainers.stream()).thenReturn(Stream.empty());
    List<ReceivedContainer> receivedContainers = mock(List.class);
    ReceivedContainer receivedContainer = mock(ReceivedContainer.class);
    when(receivedContainers.get(0)).thenReturn(receivedContainer);
    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            getMockInstructionRequest(receiveInstructionRequest, httpHeaders),
            5,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(2))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
  }

  @Test
  public void testReceiveInstruction_DA_HappyPath_ForNonAtlasItem()
      throws IOException, ExecutionException, InterruptedException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(Long.valueOf(1234));
    String DCTimeZone = "US/Eastern";
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    ReceiveContainersResponseBody receiveContainersResponseBody =
        MockRdsResponse.getRdsResponseForDABreakConveyPacks();
    List<ReceivedContainer> recievedContainerList = receiveContainersResponseBody.getReceived();
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(2);

    when(rdcReceivingUtils.receiveContainers(
            anyInt(),
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(ReceiveInstructionRequest.class)))
        .thenReturn(recievedContainerList);

    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);

    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());
    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null)))
        .thenReturn(new Container());
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    doNothing()
        .when(rdcReceivingUtils)
        .publishInstruction(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean());
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            getMockInstructionRequest(receiveInstructionRequest, httpHeaders),
            2,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(instructionResponse.getInstruction());
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(2))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcContainerUtils, times(2))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(
            anyList(),
            anyList(),
            anyList(),
            anyList(),
            argThat(argument -> argument == null || argument instanceof List));
  }

  @Test
  public void testReceiveInstruction_DA_HappyPath_ForNonAtlasItem_OutBoxIntegrationEnabled()
      throws IOException, ExecutionException, InterruptedException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(Long.valueOf(1234));
    Collection<OutboxEvent> outboxEvents = new ArrayList<>();
    outboxEvents.add(
        OutboxEvent.builder()
            .eventIdentifier("a232323223")
            .executionTs(Instant.now())
            .metaData(MetaData.with(ReceivingConstants.KEY, "a32323223"))
            .publisherPolicyId("test")
            .payloadRef(new PayloadRef())
            .build());
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    ReceiveContainersResponseBody receiveContainersResponseBody =
        MockRdsResponse.getRdsResponseForDABreakConveyPacks();
    List<ReceivedContainer> recievedContainerList = receiveContainersResponseBody.getReceived();
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    doNothing().when(rdcReceivingUtils).persistOutboxEvents(anyList());
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(2);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_OUTBOX_PATTERN_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);

    when(rdcReceivingUtils.receiveContainers(
            anyInt(),
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(ReceiveInstructionRequest.class)))
        .thenReturn(recievedContainerList);

    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);

    when(rdcReceivingUtils.buildOutboxEventForWFT(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean()))
        .thenReturn(outboxEvents);
    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());
    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class)))
        .thenReturn(new Container());
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            getMockInstructionRequest(receiveInstructionRequest, httpHeaders),
            2,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(instructionResponse.getInstruction());
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(2))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcContainerUtils, times(2))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(
            anyList(),
            anyList(),
            anyList(),
            anyList(),
            argThat(argument -> argument == null || argument instanceof List));
    verify(rdcReceivingUtils, times(1))
        .buildOutboxEventForWFT(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1)).persistOutboxEvents(anyList());
  }

  @Test
  public void testReceiveInstruction_DA_HappyPath_ForNonAtlasItem_ExistingContainerExists()
      throws IOException, ExecutionException, InterruptedException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(Long.valueOf(1234));
    Set<Container> containers = new HashSet<>();
    Container container1 = new Container();
    container1.setTrackingId("003420200000087625");
    Container container2 = new Container();
    container2.setTrackingId("099970200000087625");
    containers.add(container1);
    containers.add(container2);

    List<ContainerItem> containerItems = new ArrayList<>();
    ContainerItem containerItem1 = new ContainerItem();
    containerItem1.setTrackingId("003420200000087625");
    ContainerItem containerItem2 = new ContainerItem();
    containerItem2.setTrackingId("099970200000087625");
    containerItems.add(containerItem1);
    containerItems.add(containerItem2);

    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    ReceiveContainersResponseBody receiveContainersResponseBody =
        MockRdsResponse.getRdsResponseForDABreakConveyPacks();
    List<ReceivedContainer> recievedContainerList = receiveContainersResponseBody.getReceived();
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(2);

    when(containerPersisterService.findAllItemByTrackingId(anyList())).thenReturn(containerItems);
    when(containerService.getContainerListByTrackingIdList(anyList())).thenReturn(containers);

    when(rdcReceivingUtils.receiveContainers(
            anyInt(),
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(ReceiveInstructionRequest.class)))
        .thenReturn(recievedContainerList);
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);

    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            any(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());
    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            any(Container.class),
            eq(null)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    doNothing()
        .when(rdcReceivingUtils)
        .publishInstruction(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean());
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            getMockInstructionRequest(receiveInstructionRequest, httpHeaders),
            2,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(instructionResponse.getInstruction());
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(2))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            any(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcContainerUtils, times(2))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            any(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(
            anyList(),
            anyList(),
            anyList(),
            anyList(),
            argThat(argument -> argument == null || argument instanceof List));
  }

  @Test
  public void testReceiveInstruction_CompleteProblem()
      throws IOException, ExecutionException, InterruptedException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(Long.valueOf(1234));

    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    Instruction problemInstruction = getMockDAInstruction();
    problemInstruction.setProblemTagId("232323232");
    ReceiveContainersResponseBody receiveContainersResponseBody =
        MockRdsResponse.getRdsResponseForDABreakConveyPacks();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    List<ReceivedContainer> recievedContainerList = receiveContainersResponseBody.getReceived();
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(2);
    doReturn(problemServiceFixit)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.PROBLEM_SERVICE), eq(ProblemService.class));
    doNothing().when(problemServiceFixit).completeProblem(any(Instruction.class));
    when(rdcReceivingUtils.receiveContainers(
            anyInt(),
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(ReceiveInstructionRequest.class)))
        .thenReturn(recievedContainerList);

    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(problemInstruction);
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);
    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());
    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null)))
        .thenReturn(new Container());
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    doNothing()
        .when(rdcReceivingUtils)
        .publishInstruction(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean());
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setProblemTagId("323232323");
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            getMockInstructionRequest(receiveInstructionRequest, httpHeaders),
            2,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(instructionResponse.getInstruction());
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(2))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcContainerUtils, times(2))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(
            anyList(),
            anyList(),
            anyList(),
            anyList(),
            argThat(argument -> argument == null || argument instanceof List));
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(
            anyList(),
            anyList(),
            anyList(),
            anyList(),
            argThat(argument -> argument == null || argument instanceof List));
    verify(problemServiceFixit, times(1)).completeProblem(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());
  }

  @Test
  public void testRecieveInstructionForRtsPut() throws IOException, ReceivingException {
    InstructionResponse mockInstructionResponse = new InstructionResponseImplNew();
    Instruction instruction = new Instruction();
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForRtsPut();
    InstructionRequest instructionRequest = MockInstructionRequest.getInstructionRequestForRtsPut();
    instruction.setInstructionCode(
        RdcInstructionType.NON_CON_RTS_PUT_RECEIVING.getInstructionCode());
    instruction.setInstructionMsg(RdcInstructionType.NON_CON_RTS_PUT_RECEIVING.getInstructionMsg());
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("B");
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    mockInstructionResponse.setDeliveryDocuments(deliveryDocuments);
    mockInstructionResponse.setInstruction(instruction);
    when(rdcReceivingUtils.checkIfMaxOverageReceived(
            any(DeliveryDocument.class), anyLong(), anyInt()))
        .thenReturn(Boolean.FALSE);
    doNothing()
        .when(rdcReceivingUtils)
        .isPoAndPoLineInReceivableStatus(any(DeliveryDocumentLine.class));
    when(rdcReceivingUtils.validateRtsPutItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class),
            any(HttpHeaders.class)))
        .thenReturn(mockInstructionResponse);
    doNothing().when(rdcReceivingUtils).overrideItemProperties(any(DeliveryDocument.class));
    InstructionResponse instructionResponse =
        rdcDaService.createInstructionForDACaseReceiving(instructionRequest, 1L, httpHeaders);

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.NON_CON_RTS_PUT_RECEIVING.getInstructionCode());
    assertEquals(
        instructionResponse.getInstruction().getInstructionMsg(),
        RdcInstructionType.NON_CON_RTS_PUT_RECEIVING.getInstructionMsg());
    assertTrue(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .isMaxAllowedOverageQtyIncluded());

    verify(rdcReceivingUtils, times(1))
        .checkIfMaxOverageReceived(any(DeliveryDocument.class), anyLong(), anyInt());
    verify(rdcInstructionUtils, times(0))
        .getOverageAlertInstruction(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(rdcReceivingUtils, times(1))
        .isPoAndPoLineInReceivableStatus(any(DeliveryDocumentLine.class));
    verify(rdcReceivingUtils, times(0))
        .validateBreakPackItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class));
    verify(rdcReceivingUtils, times(1))
        .validateRtsPutItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class),
            any(HttpHeaders.class));
  }

  @Test
  public void
      testReceiveInstruction_DA_CaseConveyable_HappyPath_ForDAConveyableItem_QtyReceiving_Putaway_Req()
          throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CC");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            Objects.requireNonNull(TenantContext.getFacilityNum()).toString(),
            RdcConstants.IS_SMART_SLOTTING_ENABLED_ON_DA_ITEMS,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DA_AUTOMATION_SLOTTING_ENABLED_FOR_ATLAS_CONVEYABLE_ITEM,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(2);
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);
    when(containerPersisterService.findAllItemByTrackingId(anyList())).thenReturn(null);
    when(containerService.getContainerListByTrackingIdList(anyList())).thenReturn(null);
    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(getMockLabelDataSym());

    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt()))
        .thenReturn(Collections.singletonList(new Receipt()));
    when(rdcLpnUtils.getLPNs(anyInt(), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList("a311121121"));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setSlotDetails(getMockAutoSlotDetailsForDAConveyableItem());

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_SCAN_TO_PRINT_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");

    assertThrows(
        ReceivingBadDataException.class,
        () ->
            rdcDaService.receiveContainers(
                deliveryDocuments.get(0),
                httpHeaders,
                instructionRequest,
                2,
                receiveInstructionRequest));
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    when(rdcManagedConfig.getAtlasConveyableHandlingCodesForDaAutomationSlotting())
        .thenReturn(Arrays.asList("C", "I", "J"));
    when(rdcReceivingUtils.getLabelFormatForPallet(any(DeliveryDocumentLine.class)))
        .thenReturn(LabelFormat.ATLAS_RDC_PALLET);
    rdcDaService.receiveContainers(
        deliveryDocuments.get(0), httpHeaders, instructionRequest, 2, receiveInstructionRequest);
  }

  private ReceiveContainersRequestBody getMockRdsContainerRequest() {
    ReceiveContainersRequestBody receiveContainersRequestBody = new ReceiveContainersRequestBody();
    ContainerOrder containerOrder = new ContainerOrder();
    containerOrder.setQty(1);
    containerOrder.setPoNumber("34232323");
    containerOrder.setPoLine(1);
    containerOrder.setBreakpackRatio(1);
    containerOrder.setDoorNum("423");
    containerOrder.setUserId("vr03fd4");
    receiveContainersRequestBody.setContainerOrders(Arrays.asList(containerOrder));
    return receiveContainersRequestBody;
  }

  private ReceiveContainersRequestBody getMockRdsContainersRequest() {
    ReceiveContainersRequestBody receiveContainersRequestBody = new ReceiveContainersRequestBody();
    List<ContainerOrder> containerOrders = new ArrayList<>();
    ContainerOrder containerOrder = new ContainerOrder();
    containerOrder.setQty(1);
    containerOrder.setPoNumber("34232323");
    containerOrder.setPoLine(1);
    containerOrder.setBreakpackRatio(1);
    containerOrder.setDoorNum("423");
    containerOrder.setUserId("vr03fd4");
    ContainerOrder containerOrder1 = new ContainerOrder();
    containerOrder1.setQty(1);
    containerOrder1.setPoNumber("34232323");
    containerOrder1.setPoLine(1);
    containerOrder1.setBreakpackRatio(1);
    containerOrder1.setDoorNum("423");
    containerOrder1.setUserId("vr03fd4");
    containerOrders.add(containerOrder);
    containerOrders.add(containerOrder1);
    receiveContainersRequestBody.setContainerOrders(containerOrders);
    return receiveContainersRequestBody;
  }

  private Instruction getMockOverageAlertInstruction() {
    Instruction instruction = new Instruction();
    instruction.setInstructionCode(RdcInstructionType.OVERAGE_ALERT_RECEIVING.getInstructionCode());
    instruction.setInstructionMsg(RdcInstructionType.OVERAGE_ALERT_RECEIVING.getInstructionMsg());
    return instruction;
  }

  private Instruction getMockDAInstruction() {
    Instruction instruction = new Instruction();
    instruction.setId(2323L);
    instruction.setActivityName(WFTInstruction.DA.getActivityName());
    instruction.setInstructionMsg(
        RdcInstructionType.DA_WORK_STATION_SCAN_TO_PRINT_RECEIVING.getInstructionMsg());
    instruction.setInstructionCode(
        RdcInstructionType.DA_WORK_STATION_SCAN_TO_PRINT_RECEIVING.getInstructionCode());
    return instruction;
  }

  private InstructionResponse getRegulatedItemInstructionResponse() throws IOException {
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    instructionResponse.setDeliveryDocuments(
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA());
    return instructionResponse;
  }

  private ReceiveInstructionRequest getMockReceiveInstructionRequest() throws IOException {
    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDoorNumber("6");
    receiveInstructionRequest.setQuantity(25);
    receiveInstructionRequest.setDeliveryNumber(Long.valueOf("2356895623"));
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentForReceiveInstructionFromInstructionEntity();
    receiveInstructionRequest.setDeliveryDocuments(Arrays.asList(deliveryDocument));
    receiveInstructionRequest.setDeliveryDocumentLines(deliveryDocument.getDeliveryDocumentLines());
    return receiveInstructionRequest;
  }

  private InstructionRequest getMockInstructionRequest(
      ReceiveInstructionRequest receiveInstructionRequest, HttpHeaders httpHeaders) {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setUpcNumber(
        receiveInstructionRequest
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getCaseUpc());
    Long deliveryNumber =
        Objects.nonNull(receiveInstructionRequest.getDeliveryNumber())
            ? receiveInstructionRequest.getDeliveryNumber()
            : receiveInstructionRequest.getDeliveryDocuments().get(0).getDeliveryNumber();
    instructionRequest.setDeliveryNumber(deliveryNumber.toString());
    instructionRequest.setDoorNumber(receiveInstructionRequest.getDoorNumber());
    String messageId =
        Objects.nonNull(receiveInstructionRequest.getMessageId())
            ? receiveInstructionRequest.getMessageId()
            : httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY);
    instructionRequest.setMessageId(messageId);
    if (Objects.nonNull(receiveInstructionRequest.getProblemTagId())) {
      instructionRequest.setProblemTagId(receiveInstructionRequest.getProblemTagId());
    }
    return instructionRequest;
  }

  private SlotDetails getMockAutoSlotDetails() {
    SlotDetails slotDetails = new SlotDetails();
    slotDetails.setSlot(null);
    slotDetails.setSlotRange(null);
    slotDetails.setMaxPallet(2);
    slotDetails.setStockType("N");
    slotDetails.setSlotSize(72);
    slotDetails.setCrossReferenceDoor("000");
    return slotDetails;
  }

  private SlotDetails getMockAutoSlotDetailsForDAConveyableItem() {
    SlotDetails slotDetails = new SlotDetails();
    slotDetails.setSlot(null);
    slotDetails.setSlotRange(null);
    slotDetails.setMaxPallet(2);
    slotDetails.setStockType(STOCK_TYPE_CONVEYABLE);
    slotDetails.setSlotType(SLOT_TYPE_AUTOMATION);
    slotDetails.setSlotSize(72);
    slotDetails.setCrossReferenceDoor("000");
    return slotDetails;
  }

  private List<PalletQuantities> getMockPalletQuantities() {
    List<PalletQuantities> palletQuantitiesList = new ArrayList<>();
    PalletQuantities palletQuantities = new PalletQuantities();
    palletQuantities.setPallet(1);
    palletQuantities.setQuantity(2);
    palletQuantitiesList.add(palletQuantities);
    PalletQuantities palletQuantities1 = new PalletQuantities();
    palletQuantities1.setPallet(2);
    palletQuantities1.setQuantity(3);
    palletQuantitiesList.add(palletQuantities1);
    return palletQuantitiesList;
  }

  @Test
  public void
      testReceiveInstruction_DA_CaseConveyable_HappyPath_ForAtlasItem_QtyReceiving_Putaway_Req()
          throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CC");
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(2);
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);
    when(containerPersisterService.findAllItemByTrackingId(anyList())).thenReturn(null);
    when(containerService.getContainerListByTrackingIdList(anyList())).thenReturn(null);
    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(getMockLabelDataSym());

    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt()))
        .thenReturn(Collections.singletonList(new Receipt()));
    when(rdcLpnUtils.getLPNs(anyInt(), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList("a311121121"));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_SCAN_TO_PRINT_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            2,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;
    assertNotNull(instructionResponse);
  }

  @Test
  public void testReceiveInstruction_DA_BreakPackFullCaseConveyabl()
      throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("B");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("BC");
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(rdcManagedConfig.getSymEligibleHandlingCodesForRoutingLabel())
        .thenReturn(Arrays.asList("I", "J", "C"));
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(2);
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);
    when(containerPersisterService.findAllItemByTrackingId(anyList())).thenReturn(null);
    when(containerService.getContainerListByTrackingIdList(anyList())).thenReturn(null);
    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(getMockLabelDataSym());

    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt()))
        .thenReturn(Collections.singletonList(new Receipt()));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_SCAN_TO_PRINT_FEATURE_TYPE);

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            2,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;
    assertNotNull(instructionResponse);
  }

  @Test
  public void testBuildReceivedContainer_Offline() throws IOException {
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack().get(0);
    deliveryDocument.setTrackingId("E06938000020267142");
    deliveryDocument.getDeliveryDocumentLines().get(0).setChildTrackingId("5000000566385787");
    deliveryDocument.getDeliveryDocumentLines().get(0).setTrackingId("E06938000020267142");
    deliveryDocument.getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocument.getDeliveryDocumentLines().get(0).getAdditionalInfo().setHandlingCode("C");
    deliveryDocument.getDeliveryDocumentLines().get(0).getAdditionalInfo().setPackTypeCode("B");
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("BC");
    deliveryDocument.setOriginFacilityNum(6014);
    deliveryDocument.setEventType(EventType.valueOf("OFFLINE_RECEIVING"));
    deliveryDocument.setLabelType("XDK1");
    List<com.walmart.move.nim.receiving.core.entity.LabelData> labelDataList =
        getMockLabelDataWithChildContainer();
    List<InstructionDownloadDistributionsDTO> distributionsDTOList = new ArrayList<>();
    InstructionDownloadDistributionsDTO distribution = new InstructionDownloadDistributionsDTO();
    InstructionDownloadItemDTO item = new InstructionDownloadItemDTO();
    item.setItemNbr(123L);
    item.setVnpk(12);
    item.setVnpk(6);
    distribution.setItem(item);
    distribution.setAllocQty(123);
    distributionsDTOList.add(distribution);
    labelDataList.get(0).getAllocation().getContainer().setDistributions(distributionsDTOList);
    Facility finalDestination = new Facility();
    finalDestination.setBuNumber("123");
    finalDestination.setDestType("destType");
    labelDataList.get(0).getAllocation().getContainer().setFinalDestination(finalDestination);
    ArrayList wpmSites = new ArrayList<>();
    wpmSites.add("6014");
    when(rdcManagedConfig.getWpmSites()).thenReturn(wpmSites);
    ReceivedContainer receivedContainer =
        rdcDaService.buildReceivedContainer(
            labelDataList.get(0),
            deliveryDocument,
            deliveryDocument.getDeliveryDocumentLines().get(0),
            "5000000566385787",
            "E06938000020267142",
            distributionsDTOList,
            finalDestination,
            false,
            false,
            false);
    assertEquals(receivedContainer.getLabelType(), "XDK1");
    assertEquals(receivedContainer.getInventoryLabelType(), InventoryLabelType.XDK1);
  }

  @Test
  public void testBuildReceivedContainer_rdc2rdc() throws IOException {
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack().get(0);
    deliveryDocument.setTrackingId("E06938000020267142");
    deliveryDocument.getDeliveryDocumentLines().get(0).setChildTrackingId("5000000566385787");
    deliveryDocument.getDeliveryDocumentLines().get(0).setTrackingId("E06938000020267142");
    deliveryDocument.getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocument.getDeliveryDocumentLines().get(0).getAdditionalInfo().setHandlingCode("C");
    deliveryDocument.getDeliveryDocumentLines().get(0).getAdditionalInfo().setPackTypeCode("B");
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("BC");
    deliveryDocument.setOriginFacilityNum(6014);
    deliveryDocument.setEventType(EventType.valueOf("OFFLINE_RECEIVING"));
    deliveryDocument.setLabelType("XDK1");
    List<com.walmart.move.nim.receiving.core.entity.LabelData> labelDataList =
        getMockLabelDataWithChildContainer();
    List<InstructionDownloadDistributionsDTO> distributionsDTOList = new ArrayList<>();
    InstructionDownloadDistributionsDTO distribution = new InstructionDownloadDistributionsDTO();
    InstructionDownloadItemDTO item = new InstructionDownloadItemDTO();
    item.setItemNbr(123L);
    item.setVnpk(12);
    item.setVnpk(6);
    distribution.setItem(item);
    distribution.setAllocQty(123);
    distributionsDTOList.add(distribution);
    labelDataList.get(0).getAllocation().getContainer().setDistributions(distributionsDTOList);
    Facility finalDestination = new Facility();
    finalDestination.setBuNumber("123");
    finalDestination.setDestType("destType");
    labelDataList.get(0).getAllocation().getContainer().setFinalDestination(finalDestination);
    ArrayList<String> rdc2rdcSites = new ArrayList<>();
    rdc2rdcSites.add("6014");
    when(rdcManagedConfig.getRdc2rdcSites()).thenReturn(rdc2rdcSites);
    ReceivedContainer receivedContainer =
        rdcDaService.buildReceivedContainer(
            labelDataList.get(0),
            deliveryDocument,
            deliveryDocument.getDeliveryDocumentLines().get(0),
            "5000000566385787",
            "E06938000020267142",
            distributionsDTOList,
            finalDestination,
            false,
            false,
            false);
    assertEquals(receivedContainer.getLabelType(), "XDK1");
    assertEquals(receivedContainer.getInventoryLabelType(), InventoryLabelType.XDK1);
  }

  @Test(expectedExceptions = NullPointerException.class)
  public void testBuildReceivedContainer_rdc2rdcFalse() throws IOException {
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack().get(0);
    deliveryDocument.setTrackingId("E06938000020267142");
    deliveryDocument.getDeliveryDocumentLines().get(0).setChildTrackingId("5000000566385787");
    deliveryDocument.getDeliveryDocumentLines().get(0).setTrackingId("E06938000020267142");
    deliveryDocument.getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocument.getDeliveryDocumentLines().get(0).getAdditionalInfo().setHandlingCode("C");
    deliveryDocument.getDeliveryDocumentLines().get(0).getAdditionalInfo().setPackTypeCode("B");
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("BC");
    deliveryDocument.setOriginFacilityNum(6014);
    deliveryDocument.setEventType(EventType.valueOf("OFFLINE_RECEIVING"));
    deliveryDocument.setLabelType("XDK1");
    List<com.walmart.move.nim.receiving.core.entity.LabelData> labelDataList =
        getMockLabelDataWithChildContainer();
    List<InstructionDownloadDistributionsDTO> distributionsDTOList = new ArrayList<>();
    InstructionDownloadDistributionsDTO distribution = new InstructionDownloadDistributionsDTO();
    InstructionDownloadItemDTO item = new InstructionDownloadItemDTO();
    item.setItemNbr(123L);
    item.setVnpk(12);
    item.setVnpk(6);
    distribution.setItem(item);
    distribution.setAllocQty(123);
    distributionsDTOList.add(distribution);
    labelDataList.get(0).getAllocation().getContainer().setDistributions(distributionsDTOList);
    Facility finalDestination = new Facility();
    finalDestination.setBuNumber("123");
    finalDestination.setDestType("destType");
    labelDataList.get(0).getAllocation().getContainer().setFinalDestination(finalDestination);
    ArrayList<String> rdc2rdcSites = new ArrayList<>();
    rdc2rdcSites.add("12345");
    when(rdcManagedConfig.getRdc2rdcSites()).thenReturn(rdc2rdcSites);
    ReceivedContainer receivedContainer =
        rdcDaService.buildReceivedContainer(
            labelDataList.get(0),
            deliveryDocument,
            deliveryDocument.getDeliveryDocumentLines().get(0),
            "5000000566385787",
            "E06938000020267142",
            distributionsDTOList,
            finalDestination,
            false,
            false,
            false);
    assertEquals(receivedContainer.getLabelType(), "XDK1");
    assertEquals(receivedContainer.getInventoryLabelType(), InventoryLabelType.XDK1);
  }

  @Test
  public void testBuildReceivedContainer_Offline_WithoutChild() throws IOException {
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    deliveryDocument.setTrackingId("E06938000020267142");
    deliveryDocument.getDeliveryDocumentLines().get(0).setTrackingId("E06938000020267142");
    deliveryDocument.getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocument.getDeliveryDocumentLines().get(0).getAdditionalInfo().setHandlingCode("C");
    deliveryDocument.getDeliveryDocumentLines().get(0).getAdditionalInfo().setPackTypeCode("B");
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("BC");
    deliveryDocument.setOriginFacilityNum(6014);
    deliveryDocument.setEventType(EventType.valueOf("OFFLINE_RECEIVING"));
    List<com.walmart.move.nim.receiving.core.entity.LabelData> labelDataList = getMockLabelData();
    List<InstructionDownloadDistributionsDTO> distributionsDTOList = new ArrayList<>();
    InstructionDownloadDistributionsDTO distribution = new InstructionDownloadDistributionsDTO();
    InstructionDownloadItemDTO item = new InstructionDownloadItemDTO();
    item.setItemNbr(123L);
    item.setVnpk(12);
    item.setVnpk(6);
    distribution.setItem(item);
    distribution.setAllocQty(123);
    distributionsDTOList.add(distribution);
    labelDataList.get(0).getAllocation().getContainer().setDistributions(distributionsDTOList);
    labelDataList.get(0).setLabel("XDK2");
    Facility finalDestination = new Facility();
    finalDestination.setBuNumber("123");
    finalDestination.setDestType("destType");
    labelDataList.get(0).getAllocation().getContainer().setFinalDestination(finalDestination);
    ArrayList wpmSites = new ArrayList<>();
    wpmSites.add("6014");
    when(rdcManagedConfig.getWpmSites()).thenReturn(wpmSites);
    ReceivedContainer receivedContainer =
        rdcDaService.buildReceivedContainer(
            labelDataList.get(0),
            deliveryDocument,
            deliveryDocument.getDeliveryDocumentLines().get(0),
            "5000000566385787",
            null,
            distributionsDTOList,
            finalDestination,
            false,
            false,
            false);
    assertEquals(receivedContainer.getLabelType(), "XDK2");
    assertEquals(receivedContainer.getInventoryLabelType(), InventoryLabelType.XDK2);
  }

  @Test
  public void
      testReceiveInstruction_DA_CaseConveyable_HappyPath_ForAtlasItem_QtyReceiving_PalletPull()
          throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setStoreNumber(1021);
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CC");
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);
    when(containerPersisterService.findAllItemByTrackingId(anyList())).thenReturn(null);
    when(containerService.getContainerListByTrackingIdList(anyList())).thenReturn(null);
    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());
    when(lpnCacheService.getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList("a311121121"));
    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(labelDataService.fetchLabelDataByPoAndItemNumberAndStoreNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            receiveInstructionRequest.getStoreNumber(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(getMockLabelDataEmptyChildContainer());

    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt()))
        .thenReturn(Collections.singletonList(new Receipt()));

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_SCAN_TO_PRINT_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");
    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            2,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;
    assertNotNull(instructionResponse);

    verify(labelDataService, times(1))
        .fetchLabelDataByPoAndItemNumberAndStoreNumber(
            anyString(), anyLong(), anyInt(), anyString(), anyInt(), anyInt(), anyString());
    verify(lpnCacheService, times(1)).getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class));
  }

  @Test
  public void
      testReceiveInstruction_DA_CaseConveyable_HappyPath_ForAtlasItem_HawkeyeEnabled_WhenAutomationEnabled()
          throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CC");
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_HAWKEYE_INTEGRATION_ENABLED_FOR_MANUAL_RECEIVING,
            false))
        .thenReturn(Boolean.TRUE);
    when(hawkeyeRestApiClient.getLpnsFromHawkeye(
            any(HawkeyeGetLpnsRequest.class), any(HttpHeaders.class)))
        .thenReturn(Optional.of(Arrays.asList("a6020202222222222")));
    when(appConfig.getValidItemPackTypeHandlingCodeCombinations())
        .thenReturn(VALID_PACKTYPE_HANDLING_CODE_COMBINATIONS);
    when(labelDataService.findByTrackingIdIn(anyList())).thenReturn(getMockLabelDataSym());
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(2);
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);
    when(containerPersisterService.findAllItemByTrackingId(anyList())).thenReturn(null);
    when(containerService.getContainerListByTrackingIdList(anyList())).thenReturn(null);
    when(rdcLpnUtils.getLPNs(anyInt(), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList("a6020202222222222"));
    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());
    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt()))
        .thenReturn(Collections.singletonList(new Receipt()));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_SCAN_TO_PRINT_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            2,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;
    assertNotNull(instructionResponse);
    verify(hawkeyeRestApiClient, times(1))
        .getLpnsFromHawkeye(any(HawkeyeGetLpnsRequest.class), any(HttpHeaders.class));
    verify(labelDataService, times(1)).findByTrackingIdIn(anyList());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testReceiveInstruction_DA_CaseConveyable_ForAtlasItem_NoDeliveriesFoundException()
      throws IOException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CC");
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_HAWKEYE_INTEGRATION_ENABLED_FOR_MANUAL_RECEIVING,
            false))
        .thenReturn(Boolean.TRUE);
    when(hawkeyeRestApiClient.getLpnsFromHawkeye(
            any(HawkeyeGetLpnsRequest.class), any(HttpHeaders.class)))
        .thenThrow(
            new ReceivingBadDataException(
                ExceptionCodes.HAWKEYE_FETCH_LPNS_FAILED,
                ReceivingConstants.HAWKEYE_NO_DELIVERY_OR_ITEM_FOUND_DESCRIPTION));
    doReturn(getLabelDownloadEvents())
        .when(labelDownloadEventService)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
    com.walmart.move.nim.receiving.core.entity.LabelData labelData =
        com.walmart.move.nim.receiving.core.entity.LabelData.builder()
            .trackingId("a060200000000000000012345")
            .vnpk(2)
            .whpk(2)
            .build();
    when(labelDataService.findByPurchaseReferenceNumberInAndItemNumberAndStatus(
            anySet(), anyLong(), anyString()))
        .thenReturn(Collections.singletonList(labelData));
    when(appConfig.getValidItemPackTypeHandlingCodeCombinations())
        .thenReturn(VALID_PACKTYPE_HANDLING_CODE_COMBINATIONS);
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(2);
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_SCAN_TO_PRINT_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            2,
            receiveInstructionRequest);
    assertNotNull(instructionResponse);
    verify(hawkeyeRestApiClient, times(1))
        .getLpnsFromHawkeye(any(HawkeyeGetLpnsRequest.class), any(HttpHeaders.class));
    verify(labelDataService, times(1))
        .findByPurchaseReferenceNumberInAndItemNumberAndStatus(anySet(), anyLong(), anyString());
    verify(rdcSSTKLabelGenerationUtils, times(3))
        .isSSTKLabelDownloadEvent(any(LabelDownloadEvent.class));
    verify(rdcLabelGenerationService, times(1))
        .processAndPublishLabelDataAsync(
            anyLong(), anyLong(), anySet(), anyList(), any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void
      testReceiveInstruction_DA_CaseConveyable_ForAtlasItem_NoDeliveriesFoundException_NoLabelData()
          throws IOException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CC");
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_HAWKEYE_INTEGRATION_ENABLED_FOR_MANUAL_RECEIVING,
            false))
        .thenReturn(Boolean.TRUE);
    when(hawkeyeRestApiClient.getLpnsFromHawkeye(
            any(HawkeyeGetLpnsRequest.class), any(HttpHeaders.class)))
        .thenThrow(
            new ReceivingBadDataException(
                ExceptionCodes.HAWKEYE_FETCH_LPNS_FAILED,
                ReceivingConstants.HAWKEYE_NO_DELIVERY_OR_ITEM_FOUND_DESCRIPTION));
    doReturn(Collections.emptyList())
        .when(labelDownloadEventService)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
    when(appConfig.getValidItemPackTypeHandlingCodeCombinations())
        .thenReturn(VALID_PACKTYPE_HANDLING_CODE_COMBINATIONS);
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(2);
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_SCAN_TO_PRINT_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            2,
            receiveInstructionRequest);
    assertNotNull(instructionResponse);
    verify(hawkeyeRestApiClient, times(1))
        .getLpnsFromHawkeye(any(HawkeyeGetLpnsRequest.class), any(HttpHeaders.class));
    verify(labelDataService, times(1))
        .findByPurchaseReferenceNumberInAndItemNumberAndStatus(anySet(), anyLong(), anyString());
    verify(rdcLabelGenerationService, times(0))
        .processAndPublishLabelDataAsync(
            anyLong(), anyLong(), anySet(), anyList(), any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testReceiveInstruction_DA_CaseConveyable_ForAtlasItem_OtherException()
      throws IOException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CC");
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_HAWKEYE_INTEGRATION_ENABLED_FOR_MANUAL_RECEIVING,
            false))
        .thenReturn(Boolean.TRUE);
    when(hawkeyeRestApiClient.getLpnsFromHawkeye(
            any(HawkeyeGetLpnsRequest.class), any(HttpHeaders.class)))
        .thenThrow(
            new ReceivingBadDataException(
                ExceptionCodes.HAWKEYE_ITEM_UPDATE_FAILED, "Some Error has occured"));
    when(appConfig.getValidItemPackTypeHandlingCodeCombinations())
        .thenReturn(VALID_PACKTYPE_HANDLING_CODE_COMBINATIONS);
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(2);
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_SCAN_TO_PRINT_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            2,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;
    assertNotNull(instructionResponse);
    verify(hawkeyeRestApiClient, times(1))
        .getLpnsFromHawkeye(any(HawkeyeGetLpnsRequest.class), any(HttpHeaders.class));
    verify(labelDataService, times(0))
        .fetchByPurchaseReferenceNumberAndStatus(anyString(), anyString());
    verify(rdcLabelGenerationService, times(0))
        .processAndPublishLabelDataAsync(
            anyLong(), anyLong(), anySet(), anyList(), any(HttpHeaders.class));
  }

  @Test
  public void testReceiveInstruction_DA_BreakPack_Conveyable_HappyPath_ForAtlasItem_ScanToPrint()
      throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("B");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("BC");
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);

    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(Collections.singletonList(getMockLabelData().get(0)));

    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean()))
        .thenReturn(Arrays.asList(new Receipt()));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_SCAN_TO_PRINT_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            1,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(3))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcContainerUtils, times(3))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    verify(rdcReceivingUtils, times(1))
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    verify(labelDataService, times(1))
        .fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode());

    verify(receiptService, times(1))
        .buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean());
  }

  @Test
  public void testReceiveInstruction_DA_BreakPack_ConveyPicks_HappyPath_ForAtlasItem_ScanToPrint()
      throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(2);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("M");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("B");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("BM");
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);

    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(getMockLabelDataEmptyChildContainer());

    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean()))
        .thenReturn(Arrays.asList(new Receipt()));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_SCAN_TO_PRINT_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            1,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(2))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcContainerUtils, times(2))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    verify(rdcReceivingUtils, times(1))
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    verify(labelDataService, times(1))
        .fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode());

    verify(receiptService, times(1))
        .buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean());
  }

  @Test
  public void testReceiveInstruction_DA_CaseConveyable_HappyPath_ForAtlasItem_QtyReceiving()
      throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(2);
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("B");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("BC");
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);
    when(containerPersisterService.findAllItemByTrackingId(anyList())).thenReturn(null);
    when(containerService.getContainerListByTrackingIdList(anyList())).thenReturn(null);
    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(getMockLabelDataEmptyChildContainer());

    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean()))
        .thenReturn(Collections.singletonList(new Receipt()));
    when(rdcLpnUtils.getLPNs(anyInt(), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList("a311121121"));
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_SCAN_TO_PRINT_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            2,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    List<PrintLabelRequest> printLabelRequests =
        (List<PrintLabelRequest>) response.getPrintJob().get(ReceivingConstants.PRINT_REQUEST_KEY);

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());
    assertEquals(printLabelRequests.size(), 2);

    verify(rdcContainerUtils, times(2))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcContainerUtils, times(2))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));

    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    verify(rdcReceivingUtils, times(1))
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    verify(labelDataService, times(1))
        .fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode());

    verify(receiptService, times(1))
        .buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testReceiveInstruction_DA_CaseConveyable_HappyPath_ForAtlasItemEmptyLabels()
      throws IOException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CC");
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(5);

    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);

    when(rdcContainerUtils.buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class)))
        .thenReturn(Collections.singletonList(new ContainerItem()));

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            any(Container.class),
            eq(null)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            1,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(Collections.emptyList());

    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt()))
        .thenReturn(Collections.singletonList(new Receipt()));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setSlotDetails(getMockAutoSlotDetails());
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setProblemTagId("06001647754402");

    rdcDaService.receiveContainers(
        deliveryDocuments.get(0), httpHeaders, instructionRequest, 5, receiveInstructionRequest);

    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class), anyInt(), any(ReceiveInstructionRequest.class), false);

    verify(labelDataService, times(1))
        .fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            1,
            anyInt(),
            anyString());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void
      testReceiveInstruction_DA_CaseConveyable_HappyPath_ForAtlasItemLabelsLessThanTheExpected_NoAutomationFlow_ThrowsException()
          throws IOException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<com.walmart.move.nim.receiving.core.entity.LabelData> labelDataList = new ArrayList<>();
    com.walmart.move.nim.receiving.core.entity.LabelData labelData1 =
        new com.walmart.move.nim.receiving.core.entity.LabelData();
    com.walmart.move.nim.receiving.core.entity.LabelData labelData2 =
        new com.walmart.move.nim.receiving.core.entity.LabelData();
    labelDataList.add(labelData1);
    labelDataList.add(labelData2);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CC");
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(5);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_LABEL_COUNT_VALIDATION_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    doNothing().when(rdcAsyncUtils).labelUpdateToHawkeye(any(HttpHeaders.class), anyList());
    when(appConfig.getValidItemPackTypeHandlingCodeCombinations())
        .thenReturn(VALID_PACKTYPE_HANDLING_CODE_COMBINATIONS);
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            5,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(labelDataList);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_HAWKEYE_INTEGRATION_ENABLED_FOR_MANUAL_RECEIVING,
            false))
        .thenReturn(Boolean.TRUE);
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setProblemTagId("06001647754402");

    rdcDaService.receiveContainers(
        deliveryDocuments.get(0), httpHeaders, instructionRequest, 5, receiveInstructionRequest);

    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class), anyInt(), any(ReceiveInstructionRequest.class), false);

    verify(labelDataService, times(1))
        .fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            5,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode());
    verify(rdcAsyncUtils, times(1)).labelUpdateToHawkeye(any(HttpHeaders.class), anyList());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void
      testReceiveInstruction_DA_CaseConveyable_HappyPath_ForAtlasItemLabelsLessThanTheExpected_AutomationFlow_ThrowsException()
          throws IOException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<com.walmart.move.nim.receiving.core.entity.LabelData> labelDataList = new ArrayList<>();
    com.walmart.move.nim.receiving.core.entity.LabelData labelData1 =
        new com.walmart.move.nim.receiving.core.entity.LabelData();
    com.walmart.move.nim.receiving.core.entity.LabelData labelData2 =
        new com.walmart.move.nim.receiving.core.entity.LabelData();
    labelDataList.add(labelData1);
    labelDataList.add(labelData2);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CC");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_LABEL_COUNT_VALIDATION_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_HAWKEYE_INTEGRATION_ENABLED_FOR_MANUAL_RECEIVING,
            false))
        .thenReturn(Boolean.TRUE);
    when(hawkeyeRestApiClient.getLpnsFromHawkeye(
            any(HawkeyeGetLpnsRequest.class), any(HttpHeaders.class)))
        .thenReturn(Optional.of(Arrays.asList("a6020202222222222", "b3267932323", "c326793232")));
    when(appConfig.getValidItemPackTypeHandlingCodeCombinations())
        .thenReturn(VALID_PACKTYPE_HANDLING_CODE_COMBINATIONS);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(5);
    when(labelDataService.findByTrackingIdIn(anyList())).thenReturn(labelDataList);
    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt()))
        .thenReturn(Collections.singletonList(new Receipt()));
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);

    rdcDaService.receiveContainers(
        deliveryDocuments.get(0), httpHeaders, instructionRequest, 5, receiveInstructionRequest);

    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class), anyInt(), any(ReceiveInstructionRequest.class), false);
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "Invalid label status found for the Lpns acquired from Hawkeye for deliveryNumber: 60032433 and itemNumber: 32323233. Please report this to your supervisor.")
  public void
      testReceiveInstruction_DA_CaseConveyable_HawkeyeProvidedLpnsAreNotInAvailableStatusInLabelData()
          throws IOException {
    List<com.walmart.move.nim.receiving.core.entity.LabelData> labelDataList = new ArrayList<>();
    com.walmart.move.nim.receiving.core.entity.LabelData labelData1 =
        new com.walmart.move.nim.receiving.core.entity.LabelData();
    labelData1.setTrackingId("a060202223232323");
    labelData1.setItemNumber(32323233L);
    labelData1.setStatus(LabelInstructionStatus.AVAILABLE.name());
    com.walmart.move.nim.receiving.core.entity.LabelData labelData2 =
        new com.walmart.move.nim.receiving.core.entity.LabelData();
    labelData2.setTrackingId("a060202223232324");
    labelData2.setItemNumber(32323233L);
    labelData2.setStatus(LabelInstructionStatus.COMPLETE.name());
    labelDataList.add(labelData1);
    labelDataList.add(labelData2);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CC");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_DA_LABEL_COUNT_VALIDATION_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_HAWKEYE_INTEGRATION_ENABLED_FOR_MANUAL_RECEIVING,
            false))
        .thenReturn(Boolean.TRUE);
    when(hawkeyeRestApiClient.getLpnsFromHawkeye(
            any(HawkeyeGetLpnsRequest.class), any(HttpHeaders.class)))
        .thenReturn(Optional.of(Arrays.asList("a060202223232323", "a060202223232324")));
    when(appConfig.getValidItemPackTypeHandlingCodeCombinations())
        .thenReturn(VALID_PACKTYPE_HANDLING_CODE_COMBINATIONS);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(2);
    when(labelDataService.findByTrackingIdIn(anyList())).thenReturn(labelDataList);
    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt()))
        .thenReturn(Collections.singletonList(new Receipt()));
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);

    rdcDaService.receiveContainers(
        deliveryDocuments.get(0), httpHeaders, instructionRequest, 5, receiveInstructionRequest);

    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class), anyInt(), any(ReceiveInstructionRequest.class), false);
  }

  @Test
  public void testOverageAlertInstructionWithAtlasItem() throws IOException, ReceivingException {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDAReceivingFeatureType();
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    when(rdcReceivingUtils.checkIfMaxOverageReceived(
            any(DeliveryDocument.class), anyLong(), anyInt()))
        .thenReturn(Boolean.TRUE);
    when(rdcInstructionUtils.getOverageAlertInstruction(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(getMockOverageAlertInstruction());
    InstructionResponse instructionResponse =
        rdcDaService.createInstructionForDACaseReceiving(instructionRequest, 379L, httpHeaders);

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getOpenQty());
    assertNotNull(
        instructionResponse
            .getDeliveryDocuments()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getTotalReceivedQty());
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.OVERAGE_ALERT_RECEIVING.getInstructionCode());
    verify(rdcReceivingUtils, times(1))
        .checkIfMaxOverageReceived(any(DeliveryDocument.class), anyLong(), anyInt());
    verify(rdcInstructionUtils, times(1))
        .getOverageAlertInstruction(any(InstructionRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void
      testReceiveInstruction_DA_CaseConveyable_HappyPath_ForAtlasItem_ScanToPrintWithEmptyChildContainers()
          throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CC");
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);

    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(Collections.singletonList(getMockLabelDataEmptyChildContainer().get(0)));

    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean()))
        .thenReturn(Arrays.asList(new Receipt()));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_SCAN_TO_PRINT_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            1,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(1))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    verify(rdcReceivingUtils, times(1))
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    verify(labelDataService, times(1))
        .fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode());

    verify(receiptService, times(1))
        .buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean());
  }

  @Test
  public void
      testReceiveInstruction_DA_CaseConveyable_HappyPath_ForAtlasItem_ScanToPrint_OutboxIntegrationEnabled()
          throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    Collection<OutboxEvent> outboxEvents = new ArrayList<>();
    outboxEvents.add(
        OutboxEvent.builder()
            .eventIdentifier("a232323223")
            .executionTs(Instant.now())
            .metaData(MetaData.with(ReceivingConstants.KEY, "a32323223"))
            .publisherPolicyId("test")
            .payloadRef(new PayloadRef())
            .build());
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_OUTBOX_PATTERN_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcReceivingUtils.buildOutboxEvents(
            anyList(), any(HttpHeaders.class), any(Instruction.class), any(DeliveryDocument.class)))
        .thenReturn(outboxEvents);
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CC");
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);

    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(Collections.singletonList(getMockLabelDataEmptyChildContainer().get(0)));

    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean()))
        .thenReturn(Arrays.asList(new Receipt()));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_SCAN_TO_PRINT_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            1,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(1))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    verify(rdcReceivingUtils, times(0))
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    verify(labelDataService, times(1))
        .fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode());

    verify(receiptService, times(1))
        .buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .buildOutboxEvents(
            anyList(), any(HttpHeaders.class), any(Instruction.class), any(DeliveryDocument.class));
  }

  @Test
  public void testReceiveInstruction_BreakPack_HappyPath_ForAtlasItem_ScanToPrint()
      throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<String> lpns =
        Arrays.asList(
            "a47263319186056300", "a47263319186056301", "a47263319186056302", "a47263319186056303");
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("B");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("BC");
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);

    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_RTS_PUT_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(Collections.singletonList(getMockLabelData().get(0)));

    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean()))
        .thenReturn(Arrays.asList(new Receipt()));
    when(rdcLpnUtils.getLPNs(anyInt(), any(HttpHeaders.class))).thenReturn(lpns);
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_SCAN_TO_PRINT_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            1,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(3))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcContainerUtils, times(3))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    verify(rdcReceivingUtils, times(1))
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    verify(labelDataService, times(1))
        .fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode());

    verify(receiptService, times(1))
        .buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean());
  }

  @Test
  public void testReceiveInstruction_BreakPack_HappyPath_ForAtlasItem_RtsPut()
      throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    List<String> lpns =
        Arrays.asList(
            "a47263319186056300", "a47263319186056301", "a47263319186056302", "a47263319186056303");
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("B");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CB");
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);

    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_RTS_PUT_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(lpnCacheService.getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class))).thenReturn(lpns);
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(Collections.singletonList(getMockLabelDataEmptyChildContainer().get(0)));

    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean()))
        .thenReturn(Arrays.asList(new Receipt()));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_WORK_STATION_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            1,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(2))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcContainerUtils, times(2))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    verify(rdcReceivingUtils, times(1))
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    verify(labelDataService, times(1))
        .fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode());

    verify(receiptService, times(1))
        .buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean());
  }

  @Test
  public void
      testReceiveInstruction_DA_CaseConveyable_HappyPath_ForAtlasItem_ScanToPrintWithEmptyChildContainers_NonConItem_SorterDivertNotRequired()
          throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("N");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CN");
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);

    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(Collections.singletonList(getMockLabelDataEmptyChildContainer().get(0)));

    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean()))
        .thenReturn(Arrays.asList(new Receipt()));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_SCAN_TO_PRINT_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            1,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(1))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    verify(rdcReceivingUtils, times(1))
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    verify(labelDataService, times(1))
        .fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode());

    verify(receiptService, times(1))
        .buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean());
  }

  @Test
  public void testReceiveInstruction_CasePack_NonConHanlingCodeAsL_ForAtlasItem()
      throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    List<String> lpns =
        Arrays.asList(
            "a47263319186056300", "a47263319186056301", "a47263319186056302", "a47263319186056303");
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("L");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CL");
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);

    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_RTS_PUT_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.FALSE);
    when(lpnCacheService.getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class))).thenReturn(lpns);
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(Collections.singletonList(getMockLabelDataEmptyChildContainer().get(0)));

    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean()))
        .thenReturn(Arrays.asList(new Receipt()));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_WORK_STATION_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            1,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(1))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    verify(rdcReceivingUtils, times(1))
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    verify(labelDataService, times(1))
        .fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode());

    verify(receiptService, times(1))
        .buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean());
  }

  @Test
  public void
      testReceiveInstruction_CasePack_NonConHanlingCodeAsL_ForAtlasItem_NonConHandlingCodesFromRdcManagedConfig()
          throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    List<String> lpns =
        Arrays.asList(
            "a47263319186056300", "a47263319186056301", "a47263319186056302", "a47263319186056303");
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("L");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CL");
    when(rdcManagedConfig.getAtlasNonConveyableHandlingCodesForDaSlotting())
        .thenReturn(Arrays.asList("N", "L"));
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);

    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_RTS_PUT_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.FALSE);
    when(lpnCacheService.getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class))).thenReturn(lpns);
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(Collections.singletonList(getMockLabelDataEmptyChildContainer().get(0)));

    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt()))
        .thenReturn(Arrays.asList(new Receipt()));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_WORK_STATION_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            1,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(1))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    verify(rdcReceivingUtils, times(1))
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    verify(labelDataService, times(1))
        .fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode());

    verify(receiptService, times(1))
        .buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testReceiveInstruction_CasePackConveyableItemSlotting_BlockAtlasConveyableItems()
      throws IOException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(Long.valueOf(1234));
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(121);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setSlotDetails(getMockAutoSlotDetails());
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDAQtyReceiving();
    List<String> conveyableHandlingCodes = new ArrayList<>();
    conveyableHandlingCodes.add("C");
    conveyableHandlingCodes.add("I");
    when(rdcManagedConfig.getAtlasConveyableHandlingCodesForDaSlotting())
        .thenReturn(conveyableHandlingCodes);
    instructionRequest.setProblemTagId("06001647754402");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DA_SLOTTING_BLOCKED_FOR_ATLAS_CONVEYABLE_ITEMS,
            false))
        .thenReturn(Boolean.TRUE);

    rdcDaService.receiveContainers(
        deliveryDocuments.get(0),
        httpHeaders,
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders),
        5,
        receiveInstructionRequest);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void throwException_when_item_not_eligible_for_slotting()
      throws IOException, ReceivingException, ExecutionException, InterruptedException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(Long.valueOf(1234));
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(121);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setSlotDetails(getMockAutoSlotDetails());
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDAQtyReceiving();
    List<String> conveyableHandlingCodes = new ArrayList<>();
    conveyableHandlingCodes.add("C");
    conveyableHandlingCodes.add("I");
    when(rdcManagedConfig.getAtlasConveyableHandlingCodesForDaSlotting())
        .thenReturn(conveyableHandlingCodes);
    instructionRequest.setProblemTagId("06001647754402");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DA_SLOTTING_BLOCKED_FOR_ATLAS_CONVEYABLE_ITEMS,
            false))
        .thenReturn(Boolean.FALSE);
    ReceiveContainersResponseBody receiveContainersResponseBody =
        MockRdsResponse.getRdsResponseForDABreakConveyPacks();
    List<ReceivedContainer> recievedContainerList = receiveContainersResponseBody.getReceived();
    rdcDaService.receiveContainers(
        deliveryDocuments.get(0),
        httpHeaders,
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders),
        5,
        receiveInstructionRequest);
  }

  @Test
  public void testReceiveInstruction_CasePackNonConveyableItemSlotting_DoNotBlockReceivingItems()
      throws IOException, ReceivingException, ExecutionException, InterruptedException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(Long.valueOf(1234));
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(121);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("N");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CN");
    List<String> nonConveyableHandlingCodes = new ArrayList<>();
    nonConveyableHandlingCodes.add("CN");
    when(rdcManagedConfig.getAtlasDaNonConPackAndHandlingCodes())
        .thenReturn(nonConveyableHandlingCodes);
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setSlotDetails(getMockAutoSlotDetails());
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    ReceiveContainersResponseBody receiveContainersResponseBody =
        MockRdsResponse.getRdsResponseForDABreakConveyPacks();
    List<ReceivedContainer> recievedContainerList = receiveContainersResponseBody.getReceived();
    List<String> conveyableHandlingCodes = new ArrayList<>();
    conveyableHandlingCodes.add("C");
    conveyableHandlingCodes.add("I");
    when(rdcManagedConfig.getAtlasConveyableHandlingCodesForDaSlotting())
        .thenReturn(conveyableHandlingCodes);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DA_SLOTTING_BLOCKED_FOR_ATLAS_CONVEYABLE_ITEMS,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SMART_SLOTTING_ENABLED_ON_DA_ITEMS,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);

    when(rdcReceivingUtils.receiveContainers(
            anyInt(),
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(ReceiveInstructionRequest.class)))
        .thenReturn(recievedContainerList);

    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);
    when(containerPersisterService.findAllItemByTrackingId(anyList())).thenReturn(null);
    when(containerService.getContainerListByTrackingIdList(anyList())).thenReturn(null);
    when(rdcContainerUtils.buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class)))
        .thenReturn(Arrays.asList(new ContainerItem()));
    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null)))
        .thenReturn(new Container());
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(rdcLpnUtils.getLPNs(anyInt(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList("a311121121"));

    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            anyString(), anyLong(), anyString(), anyInt(), anyInt(), anyString()))
        .thenReturn(getMockLabelDataSym());
    SlottingPalletResponse slottingPalletResponse = mock(SlottingPalletResponse.class);
    if (!slottingPalletResponse.getLocations().isEmpty()) {
      slottingPalletResponse.getLocations().get(0).setLocation("Location1");
    }
    when(slottingDivertLocations.getLocation()).thenReturn("Location1");
    List<SlottingDivertLocations> locations = new ArrayList<>();
    locations.add(slottingDivertLocations);
    when(slottingPalletResponse.getLocations()).thenReturn(locations);
    when(rdcSlottingUtils.receiveContainers(
            receiveInstructionRequest, "a311121121", httpHeaders, null))
        .thenReturn(slottingPalletResponse);
    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    doNothing()
        .when(rdcReceivingUtils)
        .publishInstruction(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean());
    Set labelTrackingIdsMock = mock(Set.class);
    when(printJobService.createPrintJob(anyLong(), any(), eq(labelTrackingIdsMock), any()))
        .thenReturn(printJob);
    List<ReceivedContainer> mockReceivedContainers = mock(List.class);

    when(mockReceivedContainers.stream()).thenReturn(Stream.empty());
    List<ReceivedContainer> receivedContainers = mock(List.class);
    ReceivedContainer receivedContainer = mock(ReceivedContainer.class);
    when(receivedContainers.get(0)).thenReturn(receivedContainer);
    when(rdcReceivingUtils.getLabelFormatForPallet(any(DeliveryDocumentLine.class)))
        .thenReturn(LabelFormat.ATLAS_RDC_PALLET);
    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            getMockInstructionRequest(receiveInstructionRequest, httpHeaders),
            5,
            receiveInstructionRequest);

    assertNotNull(instructionResponse);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void
      throw_exception_when_automation_or_conventional_slotting_disabled_and_packtypehandlingcode_not_in_atlasDaNonConPackAndHandlingCodes()
          throws IOException, ExecutionException, InterruptedException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(Long.valueOf(1234));
    LinkedTreeMap<String, Object> move = new LinkedTreeMap<>();
    move.put(ReceivingConstants.MOVE_FROM_LOCATION, "D123");
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(121);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    List<String> handlingCodes = Arrays.asList("C", "I", "J", "E", "M");
    when(rdcManagedConfig.getAtlasConveyableHandlingCodesForDaSlotting()).thenReturn(handlingCodes);
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setSlotDetails(getMockAutoSlotDetails());
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    rdcDaService.receiveContainers(
        deliveryDocuments.get(0),
        httpHeaders,
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders),
        5,
        receiveInstructionRequest);
  }

  private List<com.walmart.move.nim.receiving.core.entity.LabelData> getMockLabelData() {
    try {
      List<com.walmart.move.nim.receiving.core.entity.LabelData> labelDataList = new ArrayList<>();
      com.walmart.move.nim.receiving.core.entity.LabelData labelData1 =
          new com.walmart.move.nim.receiving.core.entity.LabelData();
      com.walmart.move.nim.receiving.core.entity.LabelData labelData2 =
          new com.walmart.move.nim.receiving.core.entity.LabelData();
      labelData1.setId(1L);
      labelData1.setDeliveryNumber(21958062L);
      labelData1.setPurchaseReferenceNumber("5030140190");
      labelData1.setPurchaseReferenceLineNumber(1);
      labelData1.setItemNumber(658232698L);
      labelData1.setLabelSequenceNbr(20231023000100001L);
      labelData1.setVnpk(1);
      labelData1.setWhpk(1);
      labelData2.setId(1L);
      labelData2.setDeliveryNumber(21958062L);
      labelData2.setPurchaseReferenceNumber("5030140190");
      labelData2.setPurchaseReferenceLineNumber(1);
      labelData2.setItemNumber(658232698L);
      labelData2.setLabelSequenceNbr(20231023000100001L);
      labelData2.setVnpk(1);
      labelData2.setWhpk(1);
      String allocationPayload =
          "{\n"
              + "   \"container\": {\n"
              + "    \"trackingId\": \"q060200000100000000767093\",\n"
              + "    \"cartonTag\": \"060439900003727268\",\n"
              + "    \"ctrType\": \"CASE\",\n"
              + "    \"finalDestination\": {\n"
              + "        \"countryCode\": \"US\",\n"
              + "        \"buNumber\": \"06030\"\n"
              + "    },\n"
              + "    \"outboundChannelMethod\": \"DA\",\n"
              + "    \"ctrReusable\": false,\n"
              + "    \"fulfillmentMethod\": \"VOICE_PUT\"\n"
              + "},"
              + "  \"childContainers\": [\n"
              + "    {\n"
              + "      \"trackingId\": \"5000000566385787\",\n"
              + "      \"distributions\": [\n"
              + "        {\n"
              + "          \"orderId\": \"dff8b9a0-7fb9-4799-bb16-bc3d5754acaa\",\n"
              + "          \"allocQty\": 4,\n"
              + "          \"qtyUom\":\"EA\",\n"
              + "          \"item\": {\n"
              + "            \"itemNbr\": 650064585,\n"
              + "            \"itemUpc\" : \"0238408293492\",\n"
              + "            \"vnpk\" : 2,\n"
              + "            \"whpk\" : 2,\n"
              + "            \"itemdept\": \"\",\n"
              + "            \"baseDivisionCode\": \"WM\",\n"
              + "            \"financialReportingGroup\": \"US\",\n"
              + "            \"reportingGroup\": \"US\"\n"
              + "          }\n"
              + "        }\n"
              + "      ],\n"
              + "      \"ctrType\": \"Warehouse Pack\",\n"
              + "      \"ctrDestination\": {\n"
              + "        \"buNumber\": 101,\n"
              + "        \"countryCode\": \"US\",\n"
              + "            \"aisle\":\"12\",\n"
              + "            \"zone\": \"03\",\n"
              + "        \"pickBatch\": \"281\",\n"
              + "        \"printBatch\":\"281\",\n"
              + "        \"eventCharacter\": \"\",\n"
              + "        \"shipLaneNumber\": 12\n"
              + "       }\n"
              + "    },\n"
              + "    {\n"
              + "      \"trackingId\": \"5000000566385788\",\n"
              + "      \"distributions\": [\n"
              + "        {\n"
              + "          \"orderId\": \"dff8b9a0-7fb9-4799-bb16-bc3d5754aca\",\n"
              + "          \"allocQty\": 4,\n"
              + "          \"qtyUom\":\"EA\",\n"
              + "          \"item\": {\n"
              + "            \"itemNbr\": 650064585,\n"
              + "            \"itemUpc\" : \"0238408293492\",\n"
              + "            \"vnpk\" : 2,\n"
              + "            \"whpk\" : 2,\n"
              + "            \"itemdept\": \"\",\n"
              + "            \"baseDivisionCode\": \"WM\",\n"
              + "            \"financialReportingGroup\": \"US\",\n"
              + "            \"reportingGroup\": \"US\"\n"
              + "          }\n"
              + "        }\n"
              + "      ],\n"
              + "      \"ctrType\": \"Warehouse Pack\",\n"
              + "      \"ctrDestination\": {\n"
              + "        \"buNumber\": 102,\n"
              + "        \"countryCode\": \"US\",\n"
              + "        \"aisle\":\"12\",\n"
              + "        \"zone\": \"03\",\n"
              + "        \"pickBatch\": \"281\",\n"
              + "        \"printBatch\":\"281\",\n"
              + "        \"eventCharacter\": \"\",\n"
              + "        \"shipLaneNumber\": 12      \n"
              + "        }\n"
              + "    }\n"
              + "  ]\n"
              + "}";
      LabelDataAllocationDTO instructionDownloadContainerDTO1 =
          new ObjectMapper().readValue(allocationPayload, LabelDataAllocationDTO.class);
      labelData1.setAllocation(instructionDownloadContainerDTO1);
      labelData2.setAllocation(instructionDownloadContainerDTO1);
      labelData1.setQuantityUOM("EA");
      labelData1.setStatus(LabelInstructionStatus.AVAILABLE.name());
      labelData1.setOrderQuantity(1);
      labelData1.setQuantity(1);
      labelData1.setTrackingId("q060200000100000000767093");
      labelData2.setQuantityUOM("EA");
      labelData2.setStatus(LabelInstructionStatus.AVAILABLE.name());
      labelData2.setOrderQuantity(1);
      labelData1.setQuantity(1);
      labelData2.setTrackingId("q060200000100000000767091");
      labelDataList.add(labelData1);
      labelDataList.add(labelData2);
      return labelDataList;

    } catch (Exception ex) {
      return Collections.emptyList();
    }
  }

  private List<com.walmart.move.nim.receiving.core.entity.LabelData>
      getMockLabelData_BC_For_InvalidLabelFormat() {
    try {
      List<com.walmart.move.nim.receiving.core.entity.LabelData> labelDataList = new ArrayList<>();
      com.walmart.move.nim.receiving.core.entity.LabelData labelData1 =
          new com.walmart.move.nim.receiving.core.entity.LabelData();
      com.walmart.move.nim.receiving.core.entity.LabelData labelData2 =
          new com.walmart.move.nim.receiving.core.entity.LabelData();
      labelData1.setId(1L);
      labelData1.setDeliveryNumber(21958062L);
      labelData1.setPurchaseReferenceNumber("5030140190");
      labelData1.setPurchaseReferenceLineNumber(1);
      labelData1.setItemNumber(658232698L);
      labelData1.setLabelSequenceNbr(20231023000100001L);
      labelData1.setVnpk(1);
      labelData1.setWhpk(1);
      labelData2.setId(1L);
      labelData2.setDeliveryNumber(21958062L);
      labelData2.setPurchaseReferenceNumber("5030140190");
      labelData2.setPurchaseReferenceLineNumber(1);
      labelData2.setItemNumber(658232698L);
      labelData2.setLabelSequenceNbr(20231023000100001L);
      labelData2.setVnpk(1);
      labelData2.setWhpk(1);
      String allocationPayload =
          "{\n"
              + "   \"container\": {\n"
              + "    \"trackingId\": \"q060200000100000000767093\",\n"
              + "    \"cartonTag\": \"060439900003727268\",\n"
              + "    \"ctrType\": \"CASE\",\n"
              + "    \"finalDestination\": {\n"
              + "        \"countryCode\": \"US\",\n"
              + "        \"buNumber\": \"06030\"\n"
              + "    },\n"
              + "    \"outboundChannelMethod\": \"DA\",\n"
              + "    \"ctrReusable\": false,\n"
              + "    \"fulfillmentMethod\": \"PUT_LIGHT\"\n"
              + "},"
              + "  \"childContainers\": [\n"
              + "    {\n"
              + "      \"trackingId\": \"5000000566385787\",\n"
              + "      \"distributions\": [\n"
              + "        {\n"
              + "          \"orderId\": \"dff8b9a0-7fb9-4799-bb16-bc3d5754acaa\",\n"
              + "          \"allocQty\": 4,\n"
              + "          \"qtyUom\":\"EA\",\n"
              + "          \"item\": {\n"
              + "            \"itemNbr\": 650064585,\n"
              + "            \"itemUpc\" : \"0238408293492\",\n"
              + "            \"vnpk\" : 2,\n"
              + "            \"whpk\" : 2,\n"
              + "            \"itemdept\": \"\",\n"
              + "            \"baseDivisionCode\": \"WM\",\n"
              + "            \"financialReportingGroup\": \"US\",\n"
              + "            \"reportingGroup\": \"US\",\n"
              + "            \"packType\": \"BP\",\n"
              + "            \"itemHandlingCode\": \"C\"\n"
              + "          }\n"
              + "        }\n"
              + "      ],\n"
              + "      \"ctrType\": \"Warehouse Pack\",\n"
              + "      \"ctrDestination\": {\n"
              + "        \"buNumber\": 101,\n"
              + "        \"countryCode\": \"US\",\n"
              + "            \"aisle\":\"12\",\n"
              + "            \"zone\": \"03\",\n"
              + "        \"pickBatch\": \"281\",\n"
              + "        \"printBatch\":\"281\",\n"
              + "        \"eventCharacter\": \"\",\n"
              + "        \"shipLaneNumber\": 12\n"
              + "       }\n"
              + "    },\n"
              + "    {\n"
              + "      \"trackingId\": \"5000000566385788\",\n"
              + "      \"distributions\": [\n"
              + "        {\n"
              + "          \"orderId\": \"dff8b9a0-7fb9-4799-bb16-bc3d5754aca\",\n"
              + "          \"allocQty\": 4,\n"
              + "          \"qtyUom\":\"EA\",\n"
              + "          \"item\": {\n"
              + "            \"itemNbr\": 650064585,\n"
              + "            \"itemUpc\" : \"0238408293492\",\n"
              + "            \"vnpk\" : 2,\n"
              + "            \"whpk\" : 2,\n"
              + "            \"itemdept\": \"\",\n"
              + "            \"baseDivisionCode\": \"WM\",\n"
              + "            \"financialReportingGroup\": \"US\",\n"
              + "            \"reportingGroup\": \"US\",\n"
              + "            \"packType\": \"BP\",\n"
              + "            \"itemHandlingCode\": \"C\"\n"
              + "          }\n"
              + "        }\n"
              + "      ],\n"
              + "      \"ctrType\": \"Warehouse Pack\",\n"
              + "      \"ctrDestination\": {\n"
              + "        \"buNumber\": 102,\n"
              + "        \"countryCode\": \"US\",\n"
              + "        \"aisle\":\"12\",\n"
              + "        \"zone\": \"03\",\n"
              + "        \"pickBatch\": \"281\",\n"
              + "        \"printBatch\":\"281\",\n"
              + "        \"eventCharacter\": \"\",\n"
              + "        \"shipLaneNumber\": 12      \n"
              + "        }\n"
              + "    }\n"
              + "  ]\n"
              + "}";
      LabelDataAllocationDTO instructionDownloadContainerDTO1 =
          new ObjectMapper().readValue(allocationPayload, LabelDataAllocationDTO.class);
      labelData1.setAllocation(instructionDownloadContainerDTO1);
      labelData2.setAllocation(instructionDownloadContainerDTO1);
      labelData1.setQuantityUOM("EA");
      labelData1.setStatus(LabelInstructionStatus.AVAILABLE.name());
      labelData1.setOrderQuantity(1);
      labelData1.setQuantity(1);
      labelData1.setTrackingId("q060200000100000000767093");
      labelData2.setQuantityUOM("EA");
      labelData2.setStatus(LabelInstructionStatus.AVAILABLE.name());
      labelData2.setOrderQuantity(1);
      labelData1.setQuantity(1);
      labelData2.setTrackingId("q060200000100000000767091");
      labelDataList.add(labelData1);
      labelDataList.add(labelData2);
      return labelDataList;

    } catch (Exception ex) {
      return Collections.emptyList();
    }
  }

  private List<com.walmart.move.nim.receiving.core.entity.LabelData> getMockLabelDataSym() {
    try {
      List<com.walmart.move.nim.receiving.core.entity.LabelData> labelDataList = new ArrayList<>();
      com.walmart.move.nim.receiving.core.entity.LabelData labelData1 =
          new com.walmart.move.nim.receiving.core.entity.LabelData();
      labelData1.setId(1L);
      labelData1.setDeliveryNumber(21958062L);
      labelData1.setPurchaseReferenceNumber("5030140190");
      labelData1.setPurchaseReferenceLineNumber(1);
      labelData1.setItemNumber(658232698L);
      labelData1.setLabelSequenceNbr(20231023000100001L);
      labelData1.setVnpk(1);
      labelData1.setWhpk(1);
      String allocationPayload =
          "{\n"
              + "    \"container\": {\n"
              + "      \"trackingId\": \"E06938000020267142\",\n"
              + "      \"cartonTag\": \"060439900003727268\",\n"
              + "      \"ctrType\": \"CASE\",\n"
              + "      \"distributions\": [\n"
              + "        {\n"
              + "          \"orderId\": \"72debb0e-4fce-4477-8f5b-4fa2aee9e80c\",\n"
              + "          \"allocQty\": 4,\n"
              + "          \"qtyUom\": \"EA\",\n"
              + "          \"item\": {\n"
              + "            \"itemNbr\": 596942996,\n"
              + "            \"itemUpc\": \"0238408293492\",\n"
              + "            \"vnpk\": 4,\n"
              + "            \"whpk\": 4,\n"
              + "            \"itemdept\": \"\",\n"
              + "            \"baseDivisionCode\": \"WM\",\n"
              + "            \"financialReportingGroup\": \"US\",\n"
              + "            \"reportingGroup\": \"US\",\n"
              + "            \"aisle\": \"12\",\n"
              + "            \"zone\": \"03\",\n"
              + "            \"pickBatch\": \"281\",\n"
              + "            \"printBatch\": \"281\",\n"
              + "            \"shipLaneNumber\": 12,\n"
              + "            \"storeAlignment\": \"SYM2\"\n"
              + "          }\n"
              + "        }\n"
              + "      ],\n"
              + "      \"finalDestination\": {\n"
              + "        \"buNumber\": 100,\n"
              + "        \"countryCode\": \"US\"\n"
              + "      },\n"
              + "      \"outboundChannelMethod\": \"DA\",\n"
              + "      \"ctrReusable\": false,\n"
              + "      \"fulfillmentMethod\": \"PUT\"\n"
              + "    },\n"
              + "    \"childContainers\": []\n"
              + "}";
      LabelDataAllocationDTO instructionDownloadContainerDTO1 =
          new ObjectMapper().readValue(allocationPayload, LabelDataAllocationDTO.class);
      labelData1.setAllocation(instructionDownloadContainerDTO1);
      labelData1.setQuantityUOM("EA");
      labelData1.setStatus(LabelInstructionStatus.AVAILABLE.name());
      labelData1.setOrderQuantity(1);
      labelData1.setQuantity(1);
      labelData1.setTrackingId("q060200000100000000767093");
      labelData1.setQuantity(1);
      labelDataList.add(labelData1);
      return labelDataList;
    } catch (Exception ex) {
      return Collections.emptyList();
    }
  }

  private List<com.walmart.move.nim.receiving.core.entity.LabelData>
      getMockLabelDataEmptyChildContainer() {
    try {
      List<com.walmart.move.nim.receiving.core.entity.LabelData> labelDataList = new ArrayList<>();
      com.walmart.move.nim.receiving.core.entity.LabelData labelData1 =
          new com.walmart.move.nim.receiving.core.entity.LabelData();
      com.walmart.move.nim.receiving.core.entity.LabelData labelData2 =
          new com.walmart.move.nim.receiving.core.entity.LabelData();
      labelData1.setId(1L);
      labelData1.setDeliveryNumber(21958062L);
      labelData1.setPurchaseReferenceNumber("5030140190");
      labelData1.setPurchaseReferenceLineNumber(1);
      labelData1.setItemNumber(658232698L);
      labelData1.setLabelSequenceNbr(20231023000100001L);
      labelData1.setVnpk(1);
      labelData1.setWhpk(1);
      labelData2.setId(1L);
      labelData2.setDeliveryNumber(21958062L);
      labelData2.setPurchaseReferenceNumber("5030140190");
      labelData2.setPurchaseReferenceLineNumber(1);
      labelData2.setItemNumber(658232698L);
      labelData2.setLabelSequenceNbr(20231023000100001L);
      labelData2.setVnpk(1);
      labelData2.setWhpk(1);
      String allocationPayload =
          "{\n"
              + "   \"container\": {\n"
              + "    \"trackingId\": \"002560132679202860\",\n"
              + "    \"cartonTag\": \"060439900003727268\",\n"
              + "    \"ctrType\": \"CASE\",\n"
              + "    \"distributions\": [\n"
              + "        {\n"
              + "            \"orderId\": \"72debb0e-4fce-4477-8f5b-4fa2aee9e80c\",\n"
              + "            \"allocQty\": 1,\n"
              + "            \"qtyUom\": \"EA\",\n"
              + "            \"item\": {\n"
              + "                \"itemNbr\": 658232698,\n"
              + "                \"itemUpc\": \"0238408293492\",\n"
              + "                \"vnpk\": 2,\n"
              + "                \"whpk\": 2,\n"
              + "                \"itemdept\": \"\",\n"
              + "                \"baseDivisionCode\": \"WM\",\n"
              + "                \"financialReportingGroup\": \"US\",\n"
              + "                \"reportingGroup\": \"US\",\n"
              + "                \"aisle\": \"12\",\n"
              + "                \"zone\": \"03\",\n"
              + "                \"storeZone\": \"03\",\n"
              + "                \"dcZone\": \"03\",\n"
              + "                \"pickBatch\": \"281\",\n"
              + "                \"printBatch\": \"281\",\n"
              + "                \"storeAlignment\": \"CONVENTIONAL\",\n"
              + "                 \"packType\": \"CP\",\n"
              + "                 \"itemHandlingCode\": \"N\"\n"
              + "            }\n"
              + "        }\n"
              + "    ],\n"
              + "    \"finalDestination\": {\n"
              + "        \"countryCode\": \"US\",\n"
              + "        \"buNumber\": \"06030\"\n"
              + "    },\n"
              + "    \"outboundChannelMethod\": \"DA\",\n"
              + "    \"ctrReusable\": false,\n"
              + "    \"fulfillmentMethod\": \"VOICE_PUT\"\n"
              + "}"
              + "}";
      LabelDataAllocationDTO instructionDownloadContainerDTO1 =
          new ObjectMapper().readValue(allocationPayload, LabelDataAllocationDTO.class);
      labelData1.setAllocation(instructionDownloadContainerDTO1);
      labelData2.setAllocation(instructionDownloadContainerDTO1);
      labelData1.setQuantityUOM("EA");
      labelData1.setStatus(LabelInstructionStatus.AVAILABLE.name());
      labelData1.setOrderQuantity(1);
      labelData1.setQuantity(1);
      labelData1.setTrackingId("002560132679202860");
      labelData2.setQuantityUOM("EA");
      labelData2.setStatus(LabelInstructionStatus.AVAILABLE.name());
      labelData2.setOrderQuantity(1);
      labelData1.setQuantity(1);
      labelData2.setTrackingId("002560132679202861");
      labelDataList.add(labelData1);
      labelDataList.add(labelData2);
      return labelDataList;

    } catch (Exception ex) {
      return Collections.emptyList();
    }
  }

  private List<com.walmart.move.nim.receiving.core.entity.LabelData>
      getMockLabelDataEmptyChildContainer_RTSPUT() {
    try {
      List<com.walmart.move.nim.receiving.core.entity.LabelData> labelDataList = new ArrayList<>();
      com.walmart.move.nim.receiving.core.entity.LabelData labelData1 =
          new com.walmart.move.nim.receiving.core.entity.LabelData();
      com.walmart.move.nim.receiving.core.entity.LabelData labelData2 =
          new com.walmart.move.nim.receiving.core.entity.LabelData();
      labelData1.setId(1L);
      labelData1.setDeliveryNumber(21958062L);
      labelData1.setPurchaseReferenceNumber("5030140190");
      labelData1.setPurchaseReferenceLineNumber(1);
      labelData1.setItemNumber(658232698L);
      labelData1.setLabelSequenceNbr(20231023000100001L);
      labelData1.setVnpk(1);
      labelData1.setWhpk(1);
      labelData2.setId(1L);
      labelData2.setDeliveryNumber(21958062L);
      labelData2.setPurchaseReferenceNumber("5030140190");
      labelData2.setPurchaseReferenceLineNumber(1);
      labelData2.setItemNumber(658232698L);
      labelData2.setLabelSequenceNbr(20231023000100001L);
      labelData2.setVnpk(1);
      labelData2.setWhpk(1);
      String allocationPayload =
          "{\n"
              + "   \"container\": {\n"
              + "    \"trackingId\": \"002560132679202860\",\n"
              + "    \"cartonTag\": \"060439900003727268\",\n"
              + "    \"ctrType\": \"CASE\",\n"
              + "    \"distributions\": [\n"
              + "        {\n"
              + "            \"orderId\": \"72debb0e-4fce-4477-8f5b-4fa2aee9e80c\",\n"
              + "            \"allocQty\": 1,\n"
              + "            \"qtyUom\": \"EA\",\n"
              + "            \"item\": {\n"
              + "                \"itemNbr\": 658232698,\n"
              + "                \"itemUpc\": \"0238408293492\",\n"
              + "                \"vnpk\": 2,\n"
              + "                \"whpk\": 2,\n"
              + "                \"itemdept\": \"\",\n"
              + "                \"baseDivisionCode\": \"WM\",\n"
              + "                \"financialReportingGroup\": \"US\",\n"
              + "                \"reportingGroup\": \"US\",\n"
              + "                \"aisle\": \"12\",\n"
              + "                \"zone\": \"03\",\n"
              + "                \"storeZone\": \"03\",\n"
              + "                \"dcZone\": \"03\",\n"
              + "                \"pickBatch\": \"281\",\n"
              + "                \"printBatch\": \"281\",\n"
              + "                \"storeAlignment\": \"CONVENTIONAL\",\n"
              + "                        \"packType\": \"CP\",\n"
              + "                        \"itemHandlingCode\": \"B\"\n"
              + "            }\n"
              + "        }\n"
              + "    ],\n"
              + "    \"finalDestination\": {\n"
              + "        \"countryCode\": \"US\",\n"
              + "        \"buNumber\": \"06030\"\n"
              + "    },\n"
              + "    \"outboundChannelMethod\": \"DA\",\n"
              + "    \"ctrReusable\": false,\n"
              + "    \"fulfillmentMethod\": \"VOICE_PUT\"\n"
              + "}"
              + "}";
      LabelDataAllocationDTO instructionDownloadContainerDTO1 =
          new ObjectMapper().readValue(allocationPayload, LabelDataAllocationDTO.class);
      labelData1.setAllocation(instructionDownloadContainerDTO1);
      labelData2.setAllocation(instructionDownloadContainerDTO1);
      labelData1.setQuantityUOM("EA");
      labelData1.setStatus(LabelInstructionStatus.AVAILABLE.name());
      labelData1.setOrderQuantity(1);
      labelData1.setQuantity(1);
      labelData1.setTrackingId("002560132679202860");
      labelData2.setQuantityUOM("EA");
      labelData2.setStatus(LabelInstructionStatus.AVAILABLE.name());
      labelData2.setOrderQuantity(1);
      labelData1.setQuantity(1);
      labelData2.setTrackingId("002560132679202861");
      labelDataList.add(labelData1);
      labelDataList.add(labelData2);
      return labelDataList;

    } catch (Exception ex) {
      return Collections.emptyList();
    }
  }

  private List<com.walmart.move.nim.receiving.core.entity.LabelData>
      getMockLabelDataEmptyChildContainer_BreakPackXBlocked() {
    try {
      List<com.walmart.move.nim.receiving.core.entity.LabelData> labelDataList = new ArrayList<>();
      com.walmart.move.nim.receiving.core.entity.LabelData labelData1 =
          new com.walmart.move.nim.receiving.core.entity.LabelData();
      com.walmart.move.nim.receiving.core.entity.LabelData labelData2 =
          new com.walmart.move.nim.receiving.core.entity.LabelData();
      labelData1.setId(1L);
      labelData1.setDeliveryNumber(21958062L);
      labelData1.setPurchaseReferenceNumber("5030140190");
      labelData1.setPurchaseReferenceLineNumber(1);
      labelData1.setItemNumber(658232698L);
      labelData1.setLabelSequenceNbr(20231023000100001L);
      labelData1.setVnpk(1);
      labelData1.setWhpk(1);
      labelData2.setId(1L);
      labelData2.setDeliveryNumber(21958062L);
      labelData2.setPurchaseReferenceNumber("5030140190");
      labelData2.setPurchaseReferenceLineNumber(1);
      labelData2.setItemNumber(658232698L);
      labelData2.setLabelSequenceNbr(20231023000100001L);
      labelData2.setVnpk(1);
      labelData2.setWhpk(1);
      String allocationPayload =
          "{\n"
              + "   \"container\": {\n"
              + "    \"trackingId\": \"a060201234567891234567891\",\n"
              + "    \"cartonTag\": \"060439900003727268\",\n"
              + "    \"ctrType\": \"CASE\",\n"
              + "    \"distributions\": [\n"
              + "        {\n"
              + "            \"orderId\": \"72debb0e-4fce-4477-8f5b-4fa2aee9e80c\",\n"
              + "            \"allocQty\": 1,\n"
              + "            \"qtyUom\": \"EA\",\n"
              + "            \"item\": {\n"
              + "                \"itemNbr\": 658232698,\n"
              + "                \"itemUpc\": \"0238408293492\",\n"
              + "                \"vnpk\": 2,\n"
              + "                \"whpk\": 2,\n"
              + "                \"itemdept\": \"\",\n"
              + "                \"baseDivisionCode\": \"WM\",\n"
              + "                \"financialReportingGroup\": \"US\",\n"
              + "                \"reportingGroup\": \"US\",\n"
              + "                \"aisle\": \"12\",\n"
              + "                \"zone\": \"03\",\n"
              + "                \"storeZone\": \"03\",\n"
              + "                \"dcZone\": \"03\",\n"
              + "                \"pickBatch\": \"281\",\n"
              + "                \"printBatch\": \"281\",\n"
              + "                \"storeAlignment\": \"CONVENTIONAL\",\n"
              + "                        \"packType\": \"BP\",\n"
              + "                        \"itemHandlingCode\": \"X\"\n"
              + "            }\n"
              + "        }\n"
              + "    ],\n"
              + "    \"finalDestination\": {\n"
              + "        \"countryCode\": \"US\",\n"
              + "        \"buNumber\": \"06030\"\n"
              + "    },\n"
              + "    \"outboundChannelMethod\": \"DA\",\n"
              + "    \"ctrReusable\": false,\n"
              + "    \"fulfillmentMethod\": \"RECEIVING\"\n"
              + "}"
              + "}";
      LabelDataAllocationDTO instructionDownloadContainerDTO1 =
          new ObjectMapper().readValue(allocationPayload, LabelDataAllocationDTO.class);
      labelData1.setAllocation(instructionDownloadContainerDTO1);
      labelData2.setAllocation(instructionDownloadContainerDTO1);
      labelData1.setQuantityUOM("EA");
      labelData1.setStatus(LabelInstructionStatus.AVAILABLE.name());
      labelData1.setOrderQuantity(1);
      labelData1.setQuantity(1);
      labelData1.setTrackingId("a060201234567891234567891");
      labelData2.setQuantityUOM("EA");
      labelData2.setStatus(LabelInstructionStatus.AVAILABLE.name());
      labelData2.setOrderQuantity(1);
      labelData1.setQuantity(1);
      labelData2.setTrackingId("a060201234567891234567892");
      labelDataList.add(labelData1);
      labelDataList.add(labelData2);
      return labelDataList;

    } catch (Exception ex) {
      return Collections.emptyList();
    }
  }

  private List<com.walmart.move.nim.receiving.core.entity.LabelData>
      getMockLabelDataWithChildContainer() {
    try {
      List<com.walmart.move.nim.receiving.core.entity.LabelData> labelDataList = new ArrayList<>();
      com.walmart.move.nim.receiving.core.entity.LabelData labelData1 =
          new com.walmart.move.nim.receiving.core.entity.LabelData();
      com.walmart.move.nim.receiving.core.entity.LabelData labelData2 =
          new com.walmart.move.nim.receiving.core.entity.LabelData();
      labelData1.setId(1L);
      labelData1.setDeliveryNumber(21958062L);
      labelData1.setPurchaseReferenceNumber("5030140190");
      labelData1.setPurchaseReferenceLineNumber(1);
      labelData1.setItemNumber(658232698L);
      labelData1.setLabelSequenceNbr(20231023000100001L);
      labelData1.setVnpk(1);
      labelData1.setWhpk(1);
      labelData2.setId(1L);
      labelData2.setDeliveryNumber(21958062L);
      labelData2.setPurchaseReferenceNumber("5030140190");
      labelData2.setPurchaseReferenceLineNumber(1);
      labelData2.setItemNumber(658232698L);
      labelData2.setLabelSequenceNbr(20231023000100001L);
      labelData2.setVnpk(1);
      labelData2.setWhpk(1);
      String allocationPayload =
          "{\n"
              + "    \"container\": {\n"
              + "        \"trackingId\": \"a326790000100000004667766\",\n"
              + "        \"ctrType\": \"CASE\",\n"
              + "        \"finalDestination\": {\n"
              + "            \"countryCode\": \"US\",\n"
              + "            \"buNumber\": \"32679\"\n"
              + "        },\n"
              + "        \"outboundChannelMethod\": \"DA\",\n"
              + "        \"ctrReusable\": false,\n"
              + "        \"fulfillmentMethod\": \"PUT_LIGHT\"\n"
              + "    },\n"
              + "    \"childContainers\": [\n"
              + "        {\n"
              + "            \"trackingId\": \"010840132679203364\",\n"
              + "            \"poNbr\": \"0339758177\",\n"
              + "            \"poLineNbr\": 1,\n"
              + "            \"distributions\": [\n"
              + "                {\n"
              + "                    \"orderId\": \"4e566546-e78f-47e4-99b3-4dbb10844fb0\",\n"
              + "                    \"allocQty\": 3,\n"
              + "                    \"qtyUom\": \"EA\",\n"
              + "                    \"item\": {\n"
              + "                        \"itemNbr\": 254555,\n"
              + "                        \"itemUpc\": \"00883484002278\",\n"
              + "                        \"vnpk\": 12,\n"
              + "                        \"whpk\": 3,\n"
              + "                        \"baseDivisionCode\": \"WM\",\n"
              + "                        \"financialReportingGroup\": \"US\",\n"
              + "                        \"reportingGroup\": \"US\",\n"
              + "                        \"storeAlignment\": \"MANUAL\",\n"
              + "                        \"packType\": \"BP\",\n"
              + "                        \"itemHandlingCode\": \"C\"\n"
              + "                    }\n"
              + "                }\n"
              + "            ],\n"
              + "            \"ctrType\": \"WAREHOUSE PACK\",\n"
              + "            \"inventoryTag\": \"READY_TO_PICK\",\n"
              + "            \"ctrDestination\": {\n"
              + "                \"aisle\": \"25\",\n"
              + "                \"zone\": \"E\",\n"
              + "                \"storeZone\": \"E\",\n"
              + "                \"dcZone\": \"1\",\n"
              + "                \"shipLaneNumber\": 32,\n"
              + "                \"countryCode\": \"US\",\n"
              + "                \"buNumber\": \"1084\"\n"
              + "            }\n"
              + "        },\n"
              + "        {\n"
              + "            \"trackingId\": \"010840132679203365\",\n"
              + "            \"poNbr\": \"0339758177\",\n"
              + "            \"poLineNbr\": 1,\n"
              + "            \"distributions\": [\n"
              + "                {\n"
              + "                    \"orderId\": \"e6923e2e-8208-47b5-9e11-348628cf5348\",\n"
              + "                    \"allocQty\": 3,\n"
              + "                    \"qtyUom\": \"EA\",\n"
              + "                    \"item\": {\n"
              + "                        \"itemNbr\": 254555,\n"
              + "                        \"itemUpc\": \"00883484002278\",\n"
              + "                        \"vnpk\": 12,\n"
              + "                        \"whpk\": 3,\n"
              + "                        \"baseDivisionCode\": \"WM\",\n"
              + "                        \"financialReportingGroup\": \"US\",\n"
              + "                        \"reportingGroup\": \"US\",\n"
              + "                        \"storeAlignment\": \"MANUAL\",\n"
              + "                        \"packType\": \"BP\",\n"
              + "                        \"itemHandlingCode\": \"C\"\n"
              + "                    }\n"
              + "                }\n"
              + "            ],\n"
              + "            \"ctrType\": \"WAREHOUSE PACK\",\n"
              + "            \"inventoryTag\": \"READY_TO_PICK\",\n"
              + "            \"ctrDestination\": {\n"
              + "                \"aisle\": \"25\",\n"
              + "                \"zone\": \"E\",\n"
              + "                \"storeZone\": \"E\",\n"
              + "                \"dcZone\": \"12\",\n"
              + "                \"shipLaneNumber\": 32,\n"
              + "                \"countryCode\": \"US\",\n"
              + "                \"buNumber\": \"1084\"\n"
              + "            }\n"
              + "        }\n"
              + "    ]\n"
              + "}";
      LabelDataAllocationDTO instructionDownloadContainerDTO1 =
          new ObjectMapper().readValue(allocationPayload, LabelDataAllocationDTO.class);
      labelData1.setAllocation(instructionDownloadContainerDTO1);
      labelData2.setAllocation(instructionDownloadContainerDTO1);
      labelData1.setQuantityUOM("EA");
      labelData1.setStatus(LabelInstructionStatus.AVAILABLE.name());
      labelData1.setOrderQuantity(1);
      labelData1.setQuantity(1);
      labelData1.setTrackingId("q060200000100000000767093");
      labelData2.setQuantityUOM("EA");
      labelData2.setStatus(LabelInstructionStatus.AVAILABLE.name());
      labelData2.setOrderQuantity(1);
      labelData1.setQuantity(1);
      labelData2.setTrackingId("q060200000100000000767091");
      labelDataList.add(labelData1);
      labelDataList.add(labelData2);
      return labelDataList;

    } catch (Exception ex) {
      return Collections.emptyList();
    }
  }

  private List<com.walmart.move.nim.receiving.core.entity.LabelData>
      getMockLabelDataWithChildContainer_BreakPack_Con() {
    try {
      List<com.walmart.move.nim.receiving.core.entity.LabelData> labelDataList = new ArrayList<>();
      com.walmart.move.nim.receiving.core.entity.LabelData labelData1 =
          new com.walmart.move.nim.receiving.core.entity.LabelData();
      com.walmart.move.nim.receiving.core.entity.LabelData labelData2 =
          new com.walmart.move.nim.receiving.core.entity.LabelData();
      labelData1.setId(1L);
      labelData1.setDeliveryNumber(21958062L);
      labelData1.setPurchaseReferenceNumber("5030140190");
      labelData1.setPurchaseReferenceLineNumber(1);
      labelData1.setItemNumber(658232698L);
      labelData1.setLabelSequenceNbr(20231023000100001L);
      labelData1.setVnpk(1);
      labelData1.setWhpk(1);
      labelData2.setId(1L);
      labelData2.setDeliveryNumber(21958062L);
      labelData2.setPurchaseReferenceNumber("5030140190");
      labelData2.setPurchaseReferenceLineNumber(1);
      labelData2.setItemNumber(658232698L);
      labelData2.setLabelSequenceNbr(20231023000100001L);
      labelData2.setVnpk(1);
      labelData2.setWhpk(1);
      String allocationPayload =
          "{\n"
              + "    \"container\": {\n"
              + "        \"trackingId\": \"354637823463786543\",\n"
              + "        \"ctrType\": \"CASE\",\n"
              + "        \"finalDestination\": {\n"
              + "            \"countryCode\": \"US\",\n"
              + "            \"buNumber\": \"32679\"\n"
              + "        },\n"
              + "        \"outboundChannelMethod\": \"DA\",\n"
              + "        \"ctrReusable\": false,\n"
              + "        \"fulfillmentMethod\": \"PUT_LIGHT\"\n"
              + "    },\n"
              + "    \"childContainers\": [\n"
              + "        {\n"
              + "            \"trackingId\": \"010840132679203364\",\n"
              + "            \"poNbr\": \"0339758177\",\n"
              + "            \"poLineNbr\": 1,\n"
              + "            \"distributions\": [\n"
              + "                {\n"
              + "                    \"orderId\": \"4e566546-e78f-47e4-99b3-4dbb10844fb0\",\n"
              + "                    \"allocQty\": 3,\n"
              + "                    \"qtyUom\": \"EA\",\n"
              + "                    \"item\": {\n"
              + "                        \"itemNbr\": 254555,\n"
              + "                        \"itemUpc\": \"00883484002278\",\n"
              + "                        \"vnpk\": 12,\n"
              + "                        \"whpk\": 3,\n"
              + "                        \"baseDivisionCode\": \"WM\",\n"
              + "                        \"financialReportingGroup\": \"US\",\n"
              + "                        \"reportingGroup\": \"US\",\n"
              + "                        \"storeAlignment\": \"MANUAL\",\n"
              + "                        \"packType\": \"BP\",\n"
              + "                        \"itemHandlingCode\": \"C\"\n"
              + "                    }\n"
              + "                }\n"
              + "            ],\n"
              + "            \"ctrType\": \"WAREHOUSE PACK\",\n"
              + "            \"inventoryTag\": \"READY_TO_PICK\",\n"
              + "            \"ctrDestination\": {\n"
              + "                \"aisle\": \"25\",\n"
              + "                \"zone\": \"E\",\n"
              + "                \"storeZone\": \"E\",\n"
              + "                \"dcZone\": \"1\",\n"
              + "                \"shipLaneNumber\": 32,\n"
              + "                \"countryCode\": \"US\",\n"
              + "                \"buNumber\": \"1084\"\n"
              + "            }\n"
              + "        },\n"
              + "        {\n"
              + "            \"trackingId\": \"010840132679203365\",\n"
              + "            \"poNbr\": \"0339758177\",\n"
              + "            \"poLineNbr\": 1,\n"
              + "            \"distributions\": [\n"
              + "                {\n"
              + "                    \"orderId\": \"e6923e2e-8208-47b5-9e11-348628cf5348\",\n"
              + "                    \"allocQty\": 3,\n"
              + "                    \"qtyUom\": \"EA\",\n"
              + "                    \"item\": {\n"
              + "                        \"itemNbr\": 254555,\n"
              + "                        \"itemUpc\": \"00883484002278\",\n"
              + "                        \"vnpk\": 12,\n"
              + "                        \"whpk\": 3,\n"
              + "                        \"baseDivisionCode\": \"WM\",\n"
              + "                        \"financialReportingGroup\": \"US\",\n"
              + "                        \"reportingGroup\": \"US\",\n"
              + "                        \"storeAlignment\": \"MANUAL\",\n"
              + "                        \"packType\": \"BP\",\n"
              + "                        \"itemHandlingCode\": \"C\"\n"
              + "                    }\n"
              + "                }\n"
              + "            ],\n"
              + "            \"ctrType\": \"WAREHOUSE PACK\",\n"
              + "            \"inventoryTag\": \"READY_TO_PICK\",\n"
              + "            \"ctrDestination\": {\n"
              + "                \"aisle\": \"25\",\n"
              + "                \"zone\": \"E\",\n"
              + "                \"storeZone\": \"E\",\n"
              + "                \"dcZone\": \"12\",\n"
              + "                \"shipLaneNumber\": 32,\n"
              + "                \"countryCode\": \"US\",\n"
              + "                \"buNumber\": \"1084\"\n"
              + "            }\n"
              + "        }\n"
              + "    ]\n"
              + "}";
      LabelDataAllocationDTO instructionDownloadContainerDTO1 =
          new ObjectMapper().readValue(allocationPayload, LabelDataAllocationDTO.class);
      labelData1.setAllocation(instructionDownloadContainerDTO1);
      labelData2.setAllocation(instructionDownloadContainerDTO1);
      labelData1.setQuantityUOM("EA");
      labelData1.setStatus(LabelInstructionStatus.AVAILABLE.name());
      labelData1.setOrderQuantity(1);
      labelData1.setQuantity(1);
      labelData1.setTrackingId("354637823463786543");
      labelData2.setQuantityUOM("EA");
      labelData2.setStatus(LabelInstructionStatus.AVAILABLE.name());
      labelData2.setOrderQuantity(1);
      labelData1.setQuantity(1);
      labelData2.setTrackingId("354637823463786542");
      labelDataList.add(labelData1);
      labelDataList.add(labelData2);
      return labelDataList;

    } catch (Exception ex) {
      return Collections.emptyList();
    }
  }

  private List<com.walmart.move.nim.receiving.core.entity.LabelData>
      getMockLabelDataEmptyChildContainer_InvalidLabelFormat() {
    try {
      List<com.walmart.move.nim.receiving.core.entity.LabelData> labelDataList = new ArrayList<>();
      com.walmart.move.nim.receiving.core.entity.LabelData labelData1 =
          new com.walmart.move.nim.receiving.core.entity.LabelData();
      com.walmart.move.nim.receiving.core.entity.LabelData labelData2 =
          new com.walmart.move.nim.receiving.core.entity.LabelData();
      labelData1.setId(1L);
      labelData1.setDeliveryNumber(21958062L);
      labelData1.setPurchaseReferenceNumber("5030140190");
      labelData1.setPurchaseReferenceLineNumber(1);
      labelData1.setItemNumber(658232698L);
      labelData1.setLabelSequenceNbr(20231023000100001L);
      labelData1.setVnpk(1);
      labelData1.setWhpk(1);
      labelData2.setId(1L);
      labelData2.setDeliveryNumber(21958062L);
      labelData2.setPurchaseReferenceNumber("5030140190");
      labelData2.setPurchaseReferenceLineNumber(1);
      labelData2.setItemNumber(658232698L);
      labelData2.setLabelSequenceNbr(20231023000100001L);
      labelData2.setVnpk(1);
      labelData2.setWhpk(1);
      String allocationPayload =
          "{\n"
              + "   \"container\": {\n"
              + "    \"trackingId\": \"042232323233\",\n"
              + "    \"cartonTag\": \"060439900003727268\",\n"
              + "    \"ctrType\": \"CASE\",\n"
              + "    \"distributions\": [\n"
              + "        {\n"
              + "            \"orderId\": \"72debb0e-4fce-4477-8f5b-4fa2aee9e80c\",\n"
              + "            \"allocQty\": 1,\n"
              + "            \"qtyUom\": \"EA\",\n"
              + "            \"item\": {\n"
              + "                \"itemNbr\": 658232698,\n"
              + "                \"itemUpc\": \"0238408293492\",\n"
              + "                \"vnpk\": 2,\n"
              + "                \"whpk\": 2,\n"
              + "                \"itemdept\": \"\",\n"
              + "                \"baseDivisionCode\": \"WM\",\n"
              + "                \"financialReportingGroup\": \"US\",\n"
              + "                \"reportingGroup\": \"US\",\n"
              + "                \"aisle\": \"12\",\n"
              + "                \"zone\": \"03\",\n"
              + "                \"storeZone\": \"03\",\n"
              + "                \"dcZone\": \"03\",\n"
              + "                \"pickBatch\": \"281\",\n"
              + "                \"printBatch\": \"281\",\n"
              + "                \"storeAlignment\": \"CONVENTIONAL\",\n"
              + "                        \"packType\": \"CP\",\n"
              + "                        \"itemHandlingCode\": \"N\"\n"
              + "            }\n"
              + "        }\n"
              + "    ],\n"
              + "    \"finalDestination\": {\n"
              + "        \"countryCode\": \"US\",\n"
              + "        \"buNumber\": \"06030\"\n"
              + "    },\n"
              + "    \"outboundChannelMethod\": \"DA\",\n"
              + "    \"ctrReusable\": false,\n"
              + "    \"fulfillmentMethod\": \"VOICE_PUT\"\n"
              + "}"
              + "}";
      LabelDataAllocationDTO instructionDownloadContainerDTO1 =
          new ObjectMapper().readValue(allocationPayload, LabelDataAllocationDTO.class);
      labelData1.setAllocation(instructionDownloadContainerDTO1);
      labelData2.setAllocation(instructionDownloadContainerDTO1);
      labelData1.setQuantityUOM("EA");
      labelData1.setStatus(LabelInstructionStatus.AVAILABLE.name());
      labelData1.setOrderQuantity(1);
      labelData1.setQuantity(1);
      labelData1.setTrackingId("be002560132679202860");
      labelData2.setQuantityUOM("EA");
      labelData2.setStatus(LabelInstructionStatus.AVAILABLE.name());
      labelData2.setOrderQuantity(1);
      labelData1.setQuantity(1);
      labelData2.setTrackingId("002560132679202861");
      labelDataList.add(labelData1);
      labelDataList.add(labelData2);
      return labelDataList;

    } catch (Exception ex) {
      return Collections.emptyList();
    }
  }

  private List<com.walmart.move.nim.receiving.core.entity.LabelData>
      getMockLabelDataBreakPack_ConveyPicks() {
    try {
      List<com.walmart.move.nim.receiving.core.entity.LabelData> labelDataList = new ArrayList<>();
      com.walmart.move.nim.receiving.core.entity.LabelData labelData1 =
          new com.walmart.move.nim.receiving.core.entity.LabelData();
      com.walmart.move.nim.receiving.core.entity.LabelData labelData2 =
          new com.walmart.move.nim.receiving.core.entity.LabelData();
      labelData1.setId(1L);
      labelData1.setDeliveryNumber(21958062L);
      labelData1.setPurchaseReferenceNumber("5030140190");
      labelData1.setPurchaseReferenceLineNumber(1);
      labelData1.setItemNumber(658232698L);
      labelData1.setLabelSequenceNbr(20231023000100001L);
      labelData1.setVnpk(1);
      labelData1.setWhpk(1);
      labelData2.setId(1L);
      labelData2.setDeliveryNumber(21958062L);
      labelData2.setPurchaseReferenceNumber("5030140190");
      labelData2.setPurchaseReferenceLineNumber(1);
      labelData2.setItemNumber(658232698L);
      labelData2.setLabelSequenceNbr(20231023000100001L);
      labelData2.setVnpk(1);
      labelData2.setWhpk(1);
      String allocationPayload =
          "{\n"
              + "   \"container\": {\n"
              + "    \"trackingId\": \"be2560132679202860\",\n"
              + "    \"cartonTag\": \"060439900003727268\",\n"
              + "    \"ctrType\": \"CASE\",\n"
              + "    \"distributions\": [\n"
              + "        {\n"
              + "            \"orderId\": \"72debb0e-4fce-4477-8f5b-4fa2aee9e80c\",\n"
              + "            \"allocQty\": 1,\n"
              + "            \"qtyUom\": \"EA\",\n"
              + "            \"item\": {\n"
              + "                \"itemNbr\": 658232698,\n"
              + "                \"itemUpc\": \"0238408293492\",\n"
              + "                \"vnpk\": 2,\n"
              + "                \"whpk\": 2,\n"
              + "                \"itemdept\": \"\",\n"
              + "                \"baseDivisionCode\": \"WM\",\n"
              + "                \"financialReportingGroup\": \"US\",\n"
              + "                \"reportingGroup\": \"US\",\n"
              + "                \"aisle\": \"12\",\n"
              + "                \"zone\": \"03\",\n"
              + "                \"storeZone\": \"03\",\n"
              + "                \"dcZone\": \"03\",\n"
              + "                \"pickBatch\": \"281\",\n"
              + "                \"printBatch\": \"281\",\n"
              + "                \"storeAlignment\": \"CONVENTIONAL\",\n"
              + "                        \"packType\": \"BP\",\n"
              + "                        \"itemHandlingCode\": \"M\"\n"
              + "            }\n"
              + "        }\n"
              + "    ],\n"
              + "    \"finalDestination\": {\n"
              + "        \"countryCode\": \"US\",\n"
              + "        \"buNumber\": \"06030\"\n"
              + "    },\n"
              + "    \"outboundChannelMethod\": \"DA\",\n"
              + "    \"ctrReusable\": false,\n"
              + "    \"fulfillmentMethod\": \"VOICE_PUT\"\n"
              + "}"
              + "}";
      LabelDataAllocationDTO instructionDownloadContainerDTO1 =
          new ObjectMapper().readValue(allocationPayload, LabelDataAllocationDTO.class);
      labelData1.setAllocation(instructionDownloadContainerDTO1);
      labelData2.setAllocation(instructionDownloadContainerDTO1);
      labelData1.setQuantityUOM("EA");
      labelData1.setStatus(LabelInstructionStatus.AVAILABLE.name());
      labelData1.setOrderQuantity(1);
      labelData1.setQuantity(1);
      labelData1.setTrackingId("be2560132679202860");
      labelData2.setQuantityUOM("EA");
      labelData2.setStatus(LabelInstructionStatus.AVAILABLE.name());
      labelData2.setOrderQuantity(1);
      labelData1.setQuantity(1);
      labelData2.setTrackingId("be2560132679202861");
      labelDataList.add(labelData1);
      labelDataList.add(labelData2);
      return labelDataList;

    } catch (Exception ex) {
      return Collections.emptyList();
    }
  }

  private List<com.walmart.move.nim.receiving.core.entity.LabelData>
      getMockLabelDataBreakPack_ConveyPicks_InvalidLabelFormat() {
    try {
      List<com.walmart.move.nim.receiving.core.entity.LabelData> labelDataList = new ArrayList<>();
      com.walmart.move.nim.receiving.core.entity.LabelData labelData1 =
          new com.walmart.move.nim.receiving.core.entity.LabelData();
      com.walmart.move.nim.receiving.core.entity.LabelData labelData2 =
          new com.walmart.move.nim.receiving.core.entity.LabelData();
      labelData1.setId(1L);
      labelData1.setDeliveryNumber(21958062L);
      labelData1.setPurchaseReferenceNumber("5030140190");
      labelData1.setPurchaseReferenceLineNumber(1);
      labelData1.setItemNumber(658232698L);
      labelData1.setLabelSequenceNbr(20231023000100001L);
      labelData1.setVnpk(1);
      labelData1.setWhpk(1);
      labelData2.setId(1L);
      labelData2.setDeliveryNumber(21958062L);
      labelData2.setPurchaseReferenceNumber("5030140190");
      labelData2.setPurchaseReferenceLineNumber(1);
      labelData2.setItemNumber(658232698L);
      labelData2.setLabelSequenceNbr(20231023000100001L);
      labelData2.setVnpk(1);
      labelData2.setWhpk(1);
      String allocationPayload =
          "{\n"
              + "   \"container\": {\n"
              + "    \"trackingId\": \"be002560132679202860\",\n"
              + "    \"cartonTag\": \"060439900003727268\",\n"
              + "    \"ctrType\": \"CASE\",\n"
              + "    \"distributions\": [\n"
              + "        {\n"
              + "            \"orderId\": \"72debb0e-4fce-4477-8f5b-4fa2aee9e80c\",\n"
              + "            \"allocQty\": 1,\n"
              + "            \"qtyUom\": \"EA\",\n"
              + "            \"item\": {\n"
              + "                \"itemNbr\": 658232698,\n"
              + "                \"itemUpc\": \"0238408293492\",\n"
              + "                \"vnpk\": 2,\n"
              + "                \"whpk\": 2,\n"
              + "                \"itemdept\": \"\",\n"
              + "                \"baseDivisionCode\": \"WM\",\n"
              + "                \"financialReportingGroup\": \"US\",\n"
              + "                \"reportingGroup\": \"US\",\n"
              + "                \"aisle\": \"12\",\n"
              + "                \"zone\": \"03\",\n"
              + "                \"storeZone\": \"03\",\n"
              + "                \"dcZone\": \"03\",\n"
              + "                \"pickBatch\": \"281\",\n"
              + "                \"printBatch\": \"281\",\n"
              + "                \"storeAlignment\": \"CONVENTIONAL\"\n"
              + "            }\n"
              + "        }\n"
              + "    ],\n"
              + "    \"finalDestination\": {\n"
              + "        \"countryCode\": \"US\",\n"
              + "        \"buNumber\": \"06030\"\n"
              + "    },\n"
              + "    \"outboundChannelMethod\": \"DA\",\n"
              + "    \"ctrReusable\": false,\n"
              + "    \"fulfillmentMethod\": \"VOICE_PUT\"\n"
              + "}"
              + "}";
      LabelDataAllocationDTO instructionDownloadContainerDTO1 =
          new ObjectMapper().readValue(allocationPayload, LabelDataAllocationDTO.class);
      labelData1.setAllocation(instructionDownloadContainerDTO1);
      labelData2.setAllocation(instructionDownloadContainerDTO1);
      labelData1.setQuantityUOM("EA");
      labelData1.setStatus(LabelInstructionStatus.AVAILABLE.name());
      labelData1.setOrderQuantity(1);
      labelData1.setQuantity(1);
      labelData1.setTrackingId("be002560132679202860");
      labelData2.setQuantityUOM("EA");
      labelData2.setStatus(LabelInstructionStatus.AVAILABLE.name());
      labelData2.setOrderQuantity(1);
      labelData1.setQuantity(1);
      labelData2.setTrackingId("be2560132679202861");
      labelDataList.add(labelData1);
      labelDataList.add(labelData2);
      return labelDataList;

    } catch (Exception ex) {
      return Collections.emptyList();
    }
  }

  private List<com.walmart.move.nim.receiving.core.entity.LabelData>
      getMockLabelDataSym_LabelFormatValidation() {
    try {
      List<com.walmart.move.nim.receiving.core.entity.LabelData> labelDataList = new ArrayList<>();
      com.walmart.move.nim.receiving.core.entity.LabelData labelData1 =
          new com.walmart.move.nim.receiving.core.entity.LabelData();
      labelData1.setId(1L);
      labelData1.setDeliveryNumber(21958062L);
      labelData1.setPurchaseReferenceNumber("5030140190");
      labelData1.setPurchaseReferenceLineNumber(1);
      labelData1.setItemNumber(658232698L);
      labelData1.setLabelSequenceNbr(20231023000100001L);
      labelData1.setVnpk(1);
      labelData1.setWhpk(1);
      String allocationPayload =
          "{\n"
              + "    \"container\": {\n"
              + "      \"trackingId\": \"354637823463786543\",\n"
              + "      \"cartonTag\": \"060439900003727268\",\n"
              + "      \"ctrType\": \"CASE\",\n"
              + "      \"distributions\": [\n"
              + "        {\n"
              + "          \"orderId\": \"72debb0e-4fce-4477-8f5b-4fa2aee9e80c\",\n"
              + "          \"allocQty\": 4,\n"
              + "          \"qtyUom\": \"EA\",\n"
              + "          \"item\": {\n"
              + "            \"itemNbr\": 596942996,\n"
              + "            \"itemUpc\": \"0238408293492\",\n"
              + "            \"vnpk\": 4,\n"
              + "            \"whpk\": 4,\n"
              + "            \"itemdept\": \"\",\n"
              + "            \"baseDivisionCode\": \"WM\",\n"
              + "            \"financialReportingGroup\": \"US\",\n"
              + "            \"reportingGroup\": \"US\",\n"
              + "            \"aisle\": \"12\",\n"
              + "            \"zone\": \"03\",\n"
              + "            \"pickBatch\": \"281\",\n"
              + "            \"printBatch\": \"281\",\n"
              + "            \"shipLaneNumber\": 12,\n"
              + "            \"storeAlignment\": \"SYM2\"\n"
              + "          }\n"
              + "        }\n"
              + "      ],\n"
              + "      \"finalDestination\": {\n"
              + "        \"buNumber\": 100,\n"
              + "        \"countryCode\": \"US\"\n"
              + "      },\n"
              + "      \"outboundChannelMethod\": \"DA\",\n"
              + "      \"ctrReusable\": false,\n"
              + "      \"fulfillmentMethod\": \"PUT\"\n"
              + "    },\n"
              + "    \"childContainers\": []\n"
              + "}";
      LabelDataAllocationDTO instructionDownloadContainerDTO1 =
          new ObjectMapper().readValue(allocationPayload, LabelDataAllocationDTO.class);
      labelData1.setAllocation(instructionDownloadContainerDTO1);
      labelData1.setQuantityUOM("EA");
      labelData1.setStatus(LabelInstructionStatus.AVAILABLE.name());
      labelData1.setOrderQuantity(1);
      labelData1.setQuantity(1);
      labelData1.setTrackingId("354637823463786543");
      labelData1.setQuantity(1);
      labelDataList.add(labelData1);
      return labelDataList;
    } catch (Exception ex) {
      return Collections.emptyList();
    }
  }

  private List<com.walmart.move.nim.receiving.core.entity.LabelData>
      getMockLabelData_ValidateLabelForChildContainers() {
    try {
      List<com.walmart.move.nim.receiving.core.entity.LabelData> labelDataList = new ArrayList<>();
      com.walmart.move.nim.receiving.core.entity.LabelData labelData1 =
          new com.walmart.move.nim.receiving.core.entity.LabelData();
      com.walmart.move.nim.receiving.core.entity.LabelData labelData2 =
          new com.walmart.move.nim.receiving.core.entity.LabelData();
      labelData1.setId(1L);
      labelData1.setDeliveryNumber(21958062L);
      labelData1.setPurchaseReferenceNumber("5030140190");
      labelData1.setPurchaseReferenceLineNumber(1);
      labelData1.setItemNumber(658232698L);
      labelData1.setLabelSequenceNbr(20231023000100001L);
      labelData1.setVnpk(1);
      labelData1.setWhpk(1);
      labelData2.setId(1L);
      labelData2.setDeliveryNumber(21958062L);
      labelData2.setPurchaseReferenceNumber("5030140190");
      labelData2.setPurchaseReferenceLineNumber(1);
      labelData2.setItemNumber(658232698L);
      labelData2.setLabelSequenceNbr(20231023000100001L);
      labelData2.setVnpk(1);
      labelData2.setWhpk(1);
      String allocationPayload =
          "{\n"
              + "    \"container\": {\n"
              + "        \"trackingId\": \"a326790000100000004667766\",\n"
              + "        \"ctrType\": \"CASE\",\n"
              + "        \"finalDestination\": {\n"
              + "            \"countryCode\": \"US\",\n"
              + "            \"buNumber\": \"32679\"\n"
              + "        },\n"
              + "        \"outboundChannelMethod\": \"DA\",\n"
              + "        \"ctrReusable\": false,\n"
              + "        \"fulfillmentMethod\": \"PUT_LIGHT\"\n"
              + "    },\n"
              + "    \"childContainers\": [\n"
              + "        {\n"
              + "            \"trackingId\": \"010840132679203364\",\n"
              + "            \"poNbr\": \"0339758177\",\n"
              + "            \"poLineNbr\": 1,\n"
              + "            \"distributions\": [\n"
              + "                {\n"
              + "                    \"orderId\": \"4e566546-e78f-47e4-99b3-4dbb10844fb0\",\n"
              + "                    \"allocQty\": 3,\n"
              + "                    \"qtyUom\": \"EA\",\n"
              + "                    \"item\": {\n"
              + "                        \"itemNbr\": 254555,\n"
              + "                        \"itemUpc\": \"00883484002278\",\n"
              + "                        \"vnpk\": 12,\n"
              + "                        \"whpk\": 3,\n"
              + "                        \"baseDivisionCode\": \"WM\",\n"
              + "                        \"financialReportingGroup\": \"US\",\n"
              + "                        \"reportingGroup\": \"US\",\n"
              + "                        \"storeAlignment\": \"MANUAL\",\n"
              + "                        \"packType\": \"CP\",\n"
              + "                        \"itemHandlingCode\": \"N\"\n"
              + "                    }\n"
              + "                }\n"
              + "            ],\n"
              + "            \"ctrType\": \"WAREHOUSE PACK\",\n"
              + "            \"inventoryTag\": \"READY_TO_PICK\",\n"
              + "            \"ctrDestination\": {\n"
              + "                \"aisle\": \"25\",\n"
              + "                \"zone\": \"E\",\n"
              + "                \"storeZone\": \"E\",\n"
              + "                \"dcZone\": \"1\",\n"
              + "                \"shipLaneNumber\": 32,\n"
              + "                \"countryCode\": \"US\",\n"
              + "                \"buNumber\": \"1084\"\n"
              + "            }\n"
              + "        },\n"
              + "        {\n"
              + "            \"trackingId\": \"010840132679203365\",\n"
              + "            \"poNbr\": \"0339758177\",\n"
              + "            \"poLineNbr\": 1,\n"
              + "            \"distributions\": [\n"
              + "                {\n"
              + "                    \"orderId\": \"e6923e2e-8208-47b5-9e11-348628cf5348\",\n"
              + "                    \"allocQty\": 3,\n"
              + "                    \"qtyUom\": \"EA\",\n"
              + "                    \"item\": {\n"
              + "                        \"itemNbr\": 254555,\n"
              + "                        \"itemUpc\": \"00883484002278\",\n"
              + "                        \"vnpk\": 12,\n"
              + "                        \"whpk\": 3,\n"
              + "                        \"baseDivisionCode\": \"WM\",\n"
              + "                        \"financialReportingGroup\": \"US\",\n"
              + "                        \"reportingGroup\": \"US\",\n"
              + "                        \"storeAlignment\": \"MANUAL\",\n"
              + "                        \"packType\": \"CP\",\n"
              + "                        \"itemHandlingCode\": \"N\"\n"
              + "                    }\n"
              + "                }\n"
              + "            ],\n"
              + "            \"ctrType\": \"WAREHOUSE PACK\",\n"
              + "            \"inventoryTag\": \"READY_TO_PICK\",\n"
              + "            \"ctrDestination\": {\n"
              + "                \"aisle\": \"25\",\n"
              + "                \"zone\": \"E\",\n"
              + "                \"storeZone\": \"E\",\n"
              + "                \"dcZone\": \"12\",\n"
              + "                \"shipLaneNumber\": 32,\n"
              + "                \"countryCode\": \"US\",\n"
              + "                \"buNumber\": \"1084\"\n"
              + "            }\n"
              + "        }\n"
              + "    ]\n"
              + "}";
      LabelDataAllocationDTO instructionDownloadContainerDTO1 =
          new ObjectMapper().readValue(allocationPayload, LabelDataAllocationDTO.class);
      labelData1.setAllocation(instructionDownloadContainerDTO1);
      labelData2.setAllocation(instructionDownloadContainerDTO1);
      labelData1.setQuantityUOM("EA");
      labelData1.setStatus(LabelInstructionStatus.AVAILABLE.name());
      labelData1.setOrderQuantity(1);
      labelData1.setQuantity(1);
      labelData1.setTrackingId("q060200000100000000767093");
      labelData2.setQuantityUOM("EA");
      labelData2.setStatus(LabelInstructionStatus.AVAILABLE.name());
      labelData2.setOrderQuantity(1);
      labelData1.setQuantity(1);
      labelData2.setTrackingId("q060200000100000000767091");
      labelDataList.add(labelData1);
      labelDataList.add(labelData2);
      return labelDataList;

    } catch (Exception ex) {
      return Collections.emptyList();
    }
  }

  @Test
  public void
      testReceiveInstruction_Lessthanacase_BC_CaseConveyable_HappyPath_ForAtlasItem_QtyReceiving()
          throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(2);
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("B");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("BC");
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);
    when(containerPersisterService.findAllItemByTrackingId(anyList())).thenReturn(null);
    when(containerService.getContainerListByTrackingIdList(anyList())).thenReturn(null);
    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            1,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(getMockLabelData());

    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean()))
        .thenReturn(Collections.singletonList(new Receipt()));
    when(rdcLpnUtils.getLPNs(anyInt(), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList("a311121121"));
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_SCAN_TO_PRINT_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");

    receiveInstructionRequest.setIsLessThanCase(true);
    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            2,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    List<PrintLabelRequest> printLabelRequests =
        (List<PrintLabelRequest>) response.getPrintJob().get(ReceivingConstants.PRINT_REQUEST_KEY);

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());
    assertEquals(printLabelRequests.size(), 4);

    verify(rdcContainerUtils, times(4))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcContainerUtils, times(4))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));

    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    verify(rdcReceivingUtils, times(1))
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    verify(labelDataService, times(1))
        .fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            1,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode());

    verify(receiptService, times(1))
        .buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean());
  }

  private List<com.walmart.move.nim.receiving.core.entity.LabelData>
      getMockLabelData_BreakPack_NonCon() {
    try {
      List<com.walmart.move.nim.receiving.core.entity.LabelData> labelDataList = new ArrayList<>();
      com.walmart.move.nim.receiving.core.entity.LabelData labelData1 =
          new com.walmart.move.nim.receiving.core.entity.LabelData();
      com.walmart.move.nim.receiving.core.entity.LabelData labelData2 =
          new com.walmart.move.nim.receiving.core.entity.LabelData();
      labelData1.setId(1L);
      labelData1.setDeliveryNumber(21958062L);
      labelData1.setPurchaseReferenceNumber("5030140190");
      labelData1.setPurchaseReferenceLineNumber(1);
      labelData1.setItemNumber(658232698L);
      labelData1.setLabelSequenceNbr(20231023000100001L);
      labelData1.setVnpk(12);
      labelData1.setWhpk(6);
      labelData2.setId(1L);
      labelData2.setDeliveryNumber(21958062L);
      labelData2.setPurchaseReferenceNumber("5030140190");
      labelData2.setPurchaseReferenceLineNumber(1);
      labelData2.setItemNumber(658232698L);
      labelData2.setLabelSequenceNbr(20231023000100001L);
      labelData2.setVnpk(1);
      labelData2.setWhpk(1);
      String allocationPayload =
          "{\n"
              + "    \"container\": {\n"
              + "        \"trackingId\": \"c326790000100000027855003\",\n"
              + "        \"ctrType\": \"CASE\",\n"
              + "        \"distributions\": [\n"
              + "            {\n"
              + "                \"orderId\": \"dcde32c3-0dc0-4f14-b4a7-eec2b4508b4c\",\n"
              + "                \"allocQty\": 12,\n"
              + "                \"qtyUom\": \"EA\",\n"
              + "                \"item\": {\n"
              + "                    \"itemNbr\": 550878240,\n"
              + "                    \"itemUpc\": \"00816559010793\",\n"
              + "                    \"vnpk\": 12,\n"
              + "                    \"whpk\": 6,\n"
              + "                    \"itemdept\": \"2\",\n"
              + "                    \"baseDivisionCode\": \"WM\",\n"
              + "                    \"financialReportingGroup\": \"US\",\n"
              + "                    \"reportingGroup\": \"US\",\n"
              + "                    \"dcZone\": \"12\",\n"
              + "                    \"pickBatch\": \"343\",\n"
              + "                    \"printBatch\": \"281\",\n"
              + "                    \"storeAlignment\": \"SYM2\",\n"
              + "                    \"shipLaneNumber\": 34,\n"
              + "                    \"divisionNumber\": 1,\n"
              + "                    \"packType\": \"BP\",\n"
              + "                    \"itemHandlingCode\": \"N\",\n"
              + "                    \"messageNumber\": \"%\"\n"
              + "                }\n"
              + "            }\n"
              + "        ],\n"
              + "        \"finalDestination\": {\n"
              + "            \"countryCode\": \"US\",\n"
              + "            \"buNumber\": \"100\",\n"
              + "            \"shipLaneNumber\": 0,\n"
              + "            \"destType\": \"STORE\"\n"
              + "        },\n"
              + "        \"outboundChannelMethod\": \"DA\",\n"
              + "        \"ctrReusable\": false,\n"
              + "        \"fulfillmentMethod\": \"RECEIVING\"\n"
              + "    }\n"
              + "}";
      LabelDataAllocationDTO instructionDownloadContainerDTO1 =
          new ObjectMapper().readValue(allocationPayload, LabelDataAllocationDTO.class);
      labelData1.setAllocation(instructionDownloadContainerDTO1);
      labelData2.setAllocation(instructionDownloadContainerDTO1);
      labelData1.setQuantityUOM("EA");
      labelData1.setStatus(LabelInstructionStatus.AVAILABLE.name());
      labelData1.setOrderQuantity(1);
      labelData1.setQuantity(1);
      labelData1.setTrackingId("q060200000100000000767093");
      labelData2.setQuantityUOM("EA");
      labelData2.setStatus(LabelInstructionStatus.AVAILABLE.name());
      labelData2.setOrderQuantity(1);
      labelData1.setQuantity(1);
      labelData2.setTrackingId("q060200000100000000767091");
      labelDataList.add(labelData1);
      labelDataList.add(labelData2);
      return labelDataList;

    } catch (Exception ex) {
      return Collections.emptyList();
    }
  }

  private List<com.walmart.move.nim.receiving.core.entity.LabelData>
      getMockLabelData_BreakPack_NonCon_ValidateLabelForChildContainers() {
    try {
      List<com.walmart.move.nim.receiving.core.entity.LabelData> labelDataList = new ArrayList<>();
      com.walmart.move.nim.receiving.core.entity.LabelData labelData1 =
          new com.walmart.move.nim.receiving.core.entity.LabelData();
      com.walmart.move.nim.receiving.core.entity.LabelData labelData2 =
          new com.walmart.move.nim.receiving.core.entity.LabelData();
      labelData1.setId(1L);
      labelData1.setDeliveryNumber(21958062L);
      labelData1.setPurchaseReferenceNumber("5030140190");
      labelData1.setPurchaseReferenceLineNumber(1);
      labelData1.setItemNumber(658232698L);
      labelData1.setLabelSequenceNbr(20231023000100001L);
      labelData1.setVnpk(1);
      labelData1.setWhpk(1);
      labelData2.setId(1L);
      labelData2.setDeliveryNumber(21958062L);
      labelData2.setPurchaseReferenceNumber("5030140190");
      labelData2.setPurchaseReferenceLineNumber(1);
      labelData2.setItemNumber(658232698L);
      labelData2.setLabelSequenceNbr(20231023000100001L);
      labelData2.setVnpk(1);
      labelData2.setWhpk(1);
      String allocationPayload =
          "{\n"
              + "    \"container\": {\n"
              + "        \"trackingId\": \"a326790000100000004667766\",\n"
              + "        \"ctrType\": \"CASE\",\n"
              + "        \"finalDestination\": {\n"
              + "            \"countryCode\": \"US\",\n"
              + "            \"buNumber\": \"32679\"\n"
              + "        },\n"
              + "        \"outboundChannelMethod\": \"DA\",\n"
              + "        \"ctrReusable\": false,\n"
              + "        \"fulfillmentMethod\": \"PUT_LIGHT\"\n"
              + "    },\n"
              + "    \"childContainers\": [\n"
              + "        {\n"
              + "            \"trackingId\": \"010840132679203364\",\n"
              + "            \"poNbr\": \"0339758177\",\n"
              + "            \"poLineNbr\": 1,\n"
              + "            \"distributions\": [\n"
              + "                {\n"
              + "                    \"orderId\": \"4e566546-e78f-47e4-99b3-4dbb10844fb0\",\n"
              + "                    \"allocQty\": 3,\n"
              + "                    \"qtyUom\": \"EA\",\n"
              + "                    \"item\": {\n"
              + "                        \"itemNbr\": 254555,\n"
              + "                        \"itemUpc\": \"00883484002278\",\n"
              + "                        \"vnpk\": 12,\n"
              + "                        \"whpk\": 3,\n"
              + "                        \"baseDivisionCode\": \"WM\",\n"
              + "                        \"financialReportingGroup\": \"US\",\n"
              + "                        \"reportingGroup\": \"US\",\n"
              + "                        \"storeAlignment\": \"MANUAL\",\n"
              + "                        \"packType\": \"BP\",\n"
              + "                        \"itemHandlingCode\": \"N\"\n"
              + "                    }\n"
              + "                }\n"
              + "            ],\n"
              + "            \"ctrType\": \"WAREHOUSE PACK\",\n"
              + "            \"inventoryTag\": \"READY_TO_PICK\",\n"
              + "            \"ctrDestination\": {\n"
              + "                \"aisle\": \"25\",\n"
              + "                \"zone\": \"E\",\n"
              + "                \"storeZone\": \"E\",\n"
              + "                \"dcZone\": \"1\",\n"
              + "                \"shipLaneNumber\": 32,\n"
              + "                \"countryCode\": \"US\",\n"
              + "                \"buNumber\": \"1084\"\n"
              + "            }\n"
              + "        },\n"
              + "        {\n"
              + "            \"trackingId\": \"010840132679203365\",\n"
              + "            \"poNbr\": \"0339758177\",\n"
              + "            \"poLineNbr\": 1,\n"
              + "            \"distributions\": [\n"
              + "                {\n"
              + "                    \"orderId\": \"e6923e2e-8208-47b5-9e11-348628cf5348\",\n"
              + "                    \"allocQty\": 3,\n"
              + "                    \"qtyUom\": \"EA\",\n"
              + "                    \"item\": {\n"
              + "                        \"itemNbr\": 254555,\n"
              + "                        \"itemUpc\": \"00883484002278\",\n"
              + "                        \"vnpk\": 12,\n"
              + "                        \"whpk\": 3,\n"
              + "                        \"baseDivisionCode\": \"WM\",\n"
              + "                        \"financialReportingGroup\": \"US\",\n"
              + "                        \"reportingGroup\": \"US\",\n"
              + "                        \"storeAlignment\": \"MANUAL\",\n"
              + "                        \"packType\": \"BP\",\n"
              + "                        \"itemHandlingCode\": \"N\"\n"
              + "                    }\n"
              + "                }\n"
              + "            ],\n"
              + "            \"ctrType\": \"WAREHOUSE PACK\",\n"
              + "            \"inventoryTag\": \"READY_TO_PICK\",\n"
              + "            \"ctrDestination\": {\n"
              + "                \"aisle\": \"25\",\n"
              + "                \"zone\": \"E\",\n"
              + "                \"storeZone\": \"E\",\n"
              + "                \"dcZone\": \"12\",\n"
              + "                \"shipLaneNumber\": 32,\n"
              + "                \"countryCode\": \"US\",\n"
              + "                \"buNumber\": \"1084\"\n"
              + "            }\n"
              + "        }\n"
              + "    ]\n"
              + "}";
      LabelDataAllocationDTO instructionDownloadContainerDTO1 =
          new ObjectMapper().readValue(allocationPayload, LabelDataAllocationDTO.class);
      labelData1.setAllocation(instructionDownloadContainerDTO1);
      labelData2.setAllocation(instructionDownloadContainerDTO1);
      labelData1.setQuantityUOM("EA");
      labelData1.setStatus(LabelInstructionStatus.AVAILABLE.name());
      labelData1.setOrderQuantity(1);
      labelData1.setQuantity(1);
      labelData1.setTrackingId("q060200000100000000767093");
      labelData2.setQuantityUOM("EA");
      labelData2.setStatus(LabelInstructionStatus.AVAILABLE.name());
      labelData2.setOrderQuantity(1);
      labelData1.setQuantity(1);
      labelData2.setTrackingId("q060200000100000000767091");
      labelDataList.add(labelData1);
      labelDataList.add(labelData2);
      return labelDataList;

    } catch (Exception ex) {
      return Collections.emptyList();
    }
  }

  @Test
  public void
      testReceiveInstruction_Lessthanacase_BreakPack_ConveyPicks_HappyPath_ForAtlasItem_QtyReceiving()
          throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(2);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("M");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("B");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("BM");
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);

    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(getMockLabelDataEmptyChildContainer());

    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean()))
        .thenReturn(Arrays.asList(new Receipt()));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_SCAN_TO_PRINT_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");

    receiveInstructionRequest.setIsLessThanCase(true);
    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            1,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(2))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcContainerUtils, times(2))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    verify(rdcReceivingUtils, times(1))
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    verify(labelDataService, times(1))
        .fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode());

    verify(receiptService, times(1))
        .buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void receiveContainersWithUnusedLabelsTest()
      throws IOException, ExecutionException, InterruptedException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CC");
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_HAWKEYE_INTEGRATION_ENABLED_FOR_MANUAL_RECEIVING,
            false))
        .thenReturn(Boolean.TRUE);
    when(hawkeyeRestApiClient.getLpnsFromHawkeye(
            any(HawkeyeGetLpnsRequest.class), any(HttpHeaders.class)))
        .thenReturn(Optional.of(Arrays.asList("a6020202222222222")));
    when(appConfig.getValidItemPackTypeHandlingCodeCombinations())
        .thenReturn(VALID_PACKTYPE_HANDLING_CODE_COMBINATIONS);
    when(labelDataService.findByTrackingIdIn(anyList())).thenReturn(getMockLabelDataSym());
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(2);
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);
    when(containerPersisterService.findAllItemByTrackingId(anyList()))
        .thenThrow(new ReceivingBadDataException("", ""));
    when(containerService.getContainerListByTrackingIdList(anyList())).thenReturn(null);
    when(rdcLpnUtils.getLPNs(anyInt(), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList("a6020202222222222"));
    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());
    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt()))
        .thenReturn(Collections.singletonList(new Receipt()));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_SCAN_TO_PRINT_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");
    doNothing().when(hawkeyeRestApiClient).labelUpdateToHawkeye(anyList(), any(HttpHeaders.class));
    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            2,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;
    assertNotNull(instructionResponse);
    verify(hawkeyeRestApiClient, times(1))
        .getLpnsFromHawkeye(any(HawkeyeGetLpnsRequest.class), any(HttpHeaders.class));
    verify(labelDataService, times(1)).findByTrackingIdIn(anyList());
    verify(hawkeyeRestApiClient, times(1)).labelUpdateToHawkeye(anyList(), any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void receiveContainersWithLabelDataListNullTest()
      throws IOException, ExecutionException, InterruptedException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CC");
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_HAWKEYE_INTEGRATION_ENABLED_FOR_MANUAL_RECEIVING,
            false))
        .thenReturn(Boolean.TRUE);
    when(hawkeyeRestApiClient.getLpnsFromHawkeye(
            any(HawkeyeGetLpnsRequest.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(appConfig.getValidItemPackTypeHandlingCodeCombinations()).thenReturn(null);
    when(labelDataService.findByTrackingIdIn(anyList())).thenReturn(getMockLabelDataSym());
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(2);
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);
    when(containerPersisterService.findAllItemByTrackingId(anyList()))
        .thenThrow(new ReceivingBadDataException("", ""));
    when(containerService.getContainerListByTrackingIdList(anyList())).thenReturn(null);
    when(rdcLpnUtils.getLPNs(anyInt(), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList("a6020202222222222"));
    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());
    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt()))
        .thenReturn(Collections.singletonList(new Receipt()));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_SCAN_TO_PRINT_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");
    doNothing().when(hawkeyeRestApiClient).labelUpdateToHawkeye(anyList(), any(HttpHeaders.class));
    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            2,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;
    assertNotNull(instructionResponse);
    verify(hawkeyeRestApiClient, times(1))
        .getLpnsFromHawkeye(any(HawkeyeGetLpnsRequest.class), any(HttpHeaders.class));
    verify(labelDataService, times(1)).findByTrackingIdIn(anyList());
    verify(hawkeyeRestApiClient, times(1)).labelUpdateToHawkeye(anyList(), any(HttpHeaders.class));
  }

  @Test
  public void
      testReceiveInstruction_Lessthanacase_BC_CaseConveyable_HappyPath_ForAtlasItem_QtyReceiving_Empty_Child_Containers()
          throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(2);
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("B");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("BC");
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);
    when(containerPersisterService.findAllItemByTrackingId(anyList())).thenReturn(null);
    when(containerService.getContainerListByTrackingIdList(anyList())).thenReturn(null);
    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);

    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            1,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(getMockLabelDataWithEmptyChildContainer());

    when(labelDataService.fetchLabelDataByPoAndItemNumberAndStoreNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            Integer.valueOf(facilityNum).intValue(),
            LabelInstructionStatus.AVAILABLE.name(),
            1,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(getMockLabelData());

    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean()))
        .thenReturn(Collections.singletonList(new Receipt()));
    when(rdcLpnUtils.getLPNs(anyInt(), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList("a311121121"));
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_SCAN_TO_PRINT_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");

    receiveInstructionRequest.setIsLessThanCase(true);
    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            2,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    List<PrintLabelRequest> printLabelRequests =
        (List<PrintLabelRequest>) response.getPrintJob().get(ReceivingConstants.PRINT_REQUEST_KEY);

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());
    assertEquals(printLabelRequests.size(), 4);

    verify(rdcContainerUtils, times(4))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcContainerUtils, times(4))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));

    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    verify(rdcReceivingUtils, times(1))
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    verify(labelDataService, times(1))
        .fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            1,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode());

    verify(labelDataService, times(1))
        .fetchLabelDataByPoAndItemNumberAndStoreNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            Integer.valueOf(facilityNum).intValue(),
            LabelInstructionStatus.AVAILABLE.name(),
            1,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode());

    verify(receiptService, times(1))
        .buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean());
  }

  @Test
  public void
      testReceiveInstruction_Lessthanacase_BC_CaseConveyable_ForAtlasItem_QtyReceiving_Empty_Child_Containers_Throw_Empty_Label_Data()
          throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(2);
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("B");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("BC");
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);

    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            1,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(getMockLabelDataWithEmptyChildContainer());

    when(labelDataService.fetchLabelDataByPoAndItemNumberAndStoreNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            Integer.valueOf(facilityNum).intValue(),
            LabelInstructionStatus.AVAILABLE.name(),
            1,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(Collections.emptyList());
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_SCAN_TO_PRINT_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");

    receiveInstructionRequest.setIsLessThanCase(true);
    try {
      InstructionResponse instructionResponse =
          rdcDaService.receiveContainers(
              deliveryDocuments.get(0),
              httpHeaders,
              instructionRequest,
              2,
              receiveInstructionRequest);
    } catch (Exception ex) {
    }

    verify(labelDataService, times(1))
        .fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            1,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode());
    verify(labelDataService, times(1))
        .fetchLabelDataByPoAndItemNumberAndStoreNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            Integer.valueOf(facilityNum).intValue(),
            LabelInstructionStatus.AVAILABLE.name(),
            1,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode());
  }

  @Test
  public void testReceiveInstruction_DA_BreakPack_HappyPath_ForAtlasItem_Putaway_MinMaxZoneCheck()
      throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("B");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CC");
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(2);
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);
    when(containerPersisterService.findAllItemByTrackingId(anyList())).thenReturn(null);
    when(containerService.getContainerListByTrackingIdList(anyList())).thenReturn(null);
    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(getMockLabelDataWithChildContainer());

    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt()))
        .thenReturn(Collections.singletonList(new Receipt()));
    when(rdcLpnUtils.getLPNs(anyInt(), any(HttpHeaders.class)))
        .thenReturn(Arrays.asList("a311121121"));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_SCAN_TO_PRINT_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            2,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;
    assertNotNull(instructionResponse);
    List<PrintLabelRequest> printLabelRequests =
        (List<PrintLabelRequest>)
            ((InstructionResponseImplNew) instructionResponse)
                .getPrintJob()
                .get(ReceivingConstants.PRINT_REQUEST_KEY);
    List<LabelData> labelDataList = printLabelRequests.get(0).getData();
    assertEquals(
        printLabelRequests
            .stream()
            .filter(printRequest -> printRequest.getData().get(3).getValue().equals("1-12"))
            .count(),
        2);
  }

  @Test
  public void testReceiveInstruction_ValidateLabelFormat_RtsPut()
      throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    List<String> lpns =
        Arrays.asList(
            "234536787654328976", "234536787654328975", "234536787654328974", "234536787654328973");
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("B");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CB");
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);

    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_RTS_PUT_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_LABEL_FORMAT_VALIDATION_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(lpnCacheService.getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class))).thenReturn(lpns);
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(getMockLabelDataEmptyChildContainer_RTSPUT());

    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean()))
        .thenReturn(Arrays.asList(new Receipt()));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_WORK_STATION_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            1,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(3))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcContainerUtils, times(3))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    verify(rdcReceivingUtils, times(1))
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    verify(labelDataService, times(1))
        .fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode());

    verify(receiptService, times(1))
        .buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean());
  }

  @Test
  public void testReceiveInstruction_ValidateLabelFormat_MoreThan18DigitAlphNumeric_RtsPut()
      throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    List<String> lpns =
        Arrays.asList(
            "4536787654328976", "234536787654328975", "234536787654328974", "234536787654328973");
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("B");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CB");
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);

    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_RTS_PUT_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_LABEL_FORMAT_VALIDATION_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(lpnCacheService.getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class))).thenReturn(lpns);
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(getMockLabelDataEmptyChildContainer_RTSPUT());

    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean()))
        .thenReturn(Arrays.asList(new Receipt()));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_WORK_STATION_FEATURE_TYPE);
    instructionRequest.setProblemTagId(null);

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            1,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(3))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcContainerUtils, times(3))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    verify(rdcReceivingUtils, times(1))
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    verify(labelDataService, times(1))
        .fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode());

    verify(receiptService, times(1))
        .buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void
      testReceiveInstruction_ValidateLabelFormat_OriginallyGeneratedAsNonConRtsPutInOrdersButHandlingCodeChangedAsNonConInReceiving()
          throws IOException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("N");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CN");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_RTS_PUT_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_LABEL_FORMAT_VALIDATION_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcManagedConfig.getDaAtlasLabelTypeValidationsEligiblePackHandlingCodes())
        .thenReturn(VALID_PACKTYPE_HANDLING_CODE_COMBINATIONS_LABEL_FORMAT);
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(getMockLabelDataEmptyChildContainer_RTSPUT());

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_WORK_STATION_FEATURE_TYPE);
    instructionRequest.setProblemTagId(null);

    rdcDaService.receiveContainers(
        deliveryDocuments.get(0), httpHeaders, instructionRequest, 1, receiveInstructionRequest);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void
      testReceiveInstruction_ValidateLabelFormat_OriginallyGeneratedAsBreakPackXBlockedInOrdersButHandlingCodeChangedAsBreakPackConveyPicksInReceiving()
          throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("M");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("B");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("BM");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_RTS_PUT_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_LABEL_FORMAT_VALIDATION_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcManagedConfig.getDaAtlasLabelTypeValidationsEligiblePackHandlingCodes())
        .thenReturn(VALID_PACKTYPE_HANDLING_CODE_COMBINATIONS_LABEL_FORMAT);
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(getMockLabelDataEmptyChildContainer_BreakPackXBlocked());

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_WORK_STATION_FEATURE_TYPE);

    rdcDaService.receiveContainers(
        deliveryDocuments.get(0), httpHeaders, instructionRequest, 1, receiveInstructionRequest);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void
      testReceiveInstruction_ValidateLabelFormat_OriginallyGeneratedAsBreakPackConveyablePUTInOrdersButHandlingCodeChangedAsBreakPackConveyPicksInReceiving()
          throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("M");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("B");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("BM");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_RTS_PUT_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_LABEL_FORMAT_VALIDATION_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcManagedConfig.getDaAtlasLabelTypeValidationsEligiblePackHandlingCodes())
        .thenReturn(VALID_PACKTYPE_HANDLING_CODE_COMBINATIONS_LABEL_FORMAT);
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(getMockLabelDataWithChildContainer());

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_WORK_STATION_FEATURE_TYPE);

    rdcDaService.receiveContainers(
        deliveryDocuments.get(0), httpHeaders, instructionRequest, 1, receiveInstructionRequest);
  }

  @Test
  public void testReceiveInstruction_ValidateLabelFormat_DA_BreakPackFullCaseConveyable()
      throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("B");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("BC");
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_LABEL_FORMAT_VALIDATION_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcManagedConfig.getSymEligibleHandlingCodesForRoutingLabel())
        .thenReturn(Arrays.asList("I", "J", "C"));
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(2);
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);
    when(containerPersisterService.findAllItemByTrackingId(anyList())).thenReturn(null);
    when(containerService.getContainerListByTrackingIdList(anyList())).thenReturn(null);
    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(getMockLabelDataWithChildContainer_BreakPack_Con());

    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt()))
        .thenReturn(Collections.singletonList(new Receipt()));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_SCAN_TO_PRINT_FEATURE_TYPE);

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            2,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;
    assertNotNull(instructionResponse);
  }

  @Test
  public void testReceiveInstruction_ValidateLabelFormat_DA_CaseConveyable_NonConItem()
      throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_LABEL_FORMAT_VALIDATION_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("N");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CN");
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);

    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(Collections.singletonList(getMockLabelDataEmptyChildContainer().get(0)));

    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean()))
        .thenReturn(Arrays.asList(new Receipt()));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_SCAN_TO_PRINT_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            1,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(1))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    verify(rdcReceivingUtils, times(1))
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    verify(labelDataService, times(1))
        .fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode());

    verify(receiptService, times(1))
        .buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean());
  }

  @Test
  public void testReceiveInstruction_ValidateLabelFormat_DA_CaseConveyable_NonConItem_InvalidLPN()
      throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_LABEL_FORMAT_VALIDATION_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("N");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CN");
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);

    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(getMockLabelDataEmptyChildContainer_InvalidLabelFormat());

    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean()))
        .thenReturn(Arrays.asList(new Receipt()));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_SCAN_TO_PRINT_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            1,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(2))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcContainerUtils, times(2))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    verify(rdcReceivingUtils, times(1))
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    verify(labelDataService, times(1))
        .fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode());

    verify(receiptService, times(1))
        .buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean());
  }

  @Test
  public void testReceiveInstruction_ValidateLabelFormat_Lessthanacase_BreakPack_ConveyPicks()
      throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_LABEL_FORMAT_VALIDATION_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(2);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("M");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("B");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("BM");
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);

    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(getMockLabelDataBreakPack_ConveyPicks());

    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean()))
        .thenReturn(Arrays.asList(new Receipt()));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_SCAN_TO_PRINT_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");

    receiveInstructionRequest.setIsLessThanCase(true);
    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            1,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(2))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcContainerUtils, times(2))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    verify(rdcReceivingUtils, times(1))
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    verify(labelDataService, times(1))
        .fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode());

    verify(receiptService, times(1))
        .buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void
      testReceiveInstruction_ValidateLabelFormat_Lessthanacase_BreakPack_ConveyPicks_InvalidLabelFormat()
          throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_LABEL_FORMAT_VALIDATION_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(2);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("M");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("B");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("BM");
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);

    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(getMockLabelDataBreakPack_ConveyPicks_InvalidLabelFormat());

    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean()))
        .thenReturn(Arrays.asList(new Receipt()));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_SCAN_TO_PRINT_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");

    receiveInstructionRequest.setIsLessThanCase(true);
    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            1,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(2))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcContainerUtils, times(2))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    verify(rdcReceivingUtils, times(1))
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    verify(labelDataService, times(1))
        .fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode());

    verify(receiptService, times(1))
        .buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean());
  }

  @Test
  public void
      testReceiveInstruction_ValidateLabelFormat_BreakPack_Conveyable_OriginalLabelWasBreakPackConveyable_ValidLabelTypes()
          throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_LABEL_FORMAT_VALIDATION_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcManagedConfig.getDaAtlasLabelTypeValidationsEligiblePackHandlingCodes())
        .thenReturn(VALID_PACKTYPE_HANDLING_CODE_COMBINATIONS_LABEL_FORMAT);
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(2);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("B");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("BC");

    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);

    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(Collections.singletonList(getMockLabelData().get(0)));

    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean()))
        .thenReturn(Arrays.asList(new Receipt()));

    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            anyString(), anyLong(), anyString(), anyInt(), anyInt(), anyString()))
        .thenReturn(getMockLabelData_BC_For_InvalidLabelFormat());

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setIsLessThanCase(false);
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_SCAN_TO_PRINT_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            1,
            receiveInstructionRequest);
    assertNotNull(instructionResponse);
  }

  @Test
  public void testReceiveInstruction_ValidateLabelFormat_AutoSlotting()
      throws IOException, ExecutionException, InterruptedException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(Long.valueOf(1234));
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(121);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("N");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CN");
    List<ReceivedContainer> recievedContainerList = new ArrayList<>();
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setSlotDetails(getMockAutoSlotDetails());
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDAQtyReceiving();

    instructionRequest.setProblemTagId("06001647754402");
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SMART_SLOTTING_ENABLED_ON_DA_ITEMS,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_LABEL_FORMAT_VALIDATION_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);

    when(rdcReceivingUtils.receiveContainers(
            anyInt(),
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(ReceiveInstructionRequest.class)))
        .thenReturn(recievedContainerList);

    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);
    when(containerPersisterService.findAllItemByTrackingId(anyList())).thenReturn(null);
    when(containerService.getContainerListByTrackingIdList(anyList())).thenReturn(null);
    when(rdcContainerUtils.buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class)))
        .thenReturn(Arrays.asList(new ContainerItem()));
    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null)))
        .thenReturn(new Container());
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(rdcLpnUtils.getLPNs(anyInt(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList("a311121121"));

    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            anyString(), anyLong(), anyString(), anyInt(), anyInt(), anyString()))
        .thenReturn(getMockLabelDataSym_LabelFormatValidation());
    SlottingPalletResponse slottingPalletResponse = mock(SlottingPalletResponse.class);
    if (!slottingPalletResponse.getLocations().isEmpty()) {
      slottingPalletResponse.getLocations().get(0).setLocation("Location1");
    }
    when(slottingDivertLocations.getLocation()).thenReturn("Location1");
    List<SlottingDivertLocations> locations = new ArrayList<>();
    locations.add(slottingDivertLocations);
    when(slottingPalletResponse.getLocations()).thenReturn(locations);
    when(rdcSlottingUtils.receiveContainers(
            receiveInstructionRequest, "a311121121", httpHeaders, null))
        .thenReturn(slottingPalletResponse);
    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    doNothing()
        .when(rdcReceivingUtils)
        .publishInstruction(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean());
    Set labelTrackingIdsMock = mock(Set.class);
    when(printJobService.createPrintJob(anyLong(), any(), eq(labelTrackingIdsMock), any()))
        .thenReturn(printJob);
    List<ReceivedContainer> mockReceivedContainers = mock(List.class);

    when(mockReceivedContainers.stream()).thenReturn(Stream.empty());
    List<ReceivedContainer> receivedContainers = mock(List.class);
    ReceivedContainer receivedContainer = mock(ReceivedContainer.class);
    when(receivedContainers.get(0)).thenReturn(receivedContainer);
    when(rdcReceivingUtils.getLabelFormatForPallet(any(DeliveryDocumentLine.class)))
        .thenReturn(LabelFormat.ATLAS_RDC_PALLET);
    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            getMockInstructionRequest(receiveInstructionRequest, httpHeaders),
            5,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(2))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testReceiveInstruction_ValidateLabelFormat_AutoSlotting_InvalidLabelFormat()
      throws IOException, ExecutionException, InterruptedException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(Long.valueOf(1234));
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(121);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("N");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CN");
    List<ReceivedContainer> recievedContainerList = new ArrayList<>();
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setSlotDetails(getMockAutoSlotDetails());
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDAQtyReceiving();

    instructionRequest.setProblemTagId("06001647754402");
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SMART_SLOTTING_ENABLED_ON_DA_ITEMS,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_LABEL_FORMAT_VALIDATION_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);

    when(rdcReceivingUtils.receiveContainers(
            anyInt(),
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(ReceiveInstructionRequest.class)))
        .thenReturn(recievedContainerList);

    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);
    when(containerPersisterService.findAllItemByTrackingId(anyList())).thenReturn(null);
    when(containerService.getContainerListByTrackingIdList(anyList())).thenReturn(null);
    when(rdcContainerUtils.buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class)))
        .thenReturn(Arrays.asList(new ContainerItem()));
    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null)))
        .thenReturn(new Container());
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(rdcLpnUtils.getLPNs(anyInt(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList("a311121121"));
    List<com.walmart.move.nim.receiving.core.entity.LabelData> mockLabelDataSym =
        getMockLabelDataSym_LabelFormatValidation();
    mockLabelDataSym.get(0).setTrackingId("ac098346278364758291");
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            anyString(), anyLong(), anyString(), anyInt(), anyInt(), anyString()))
        .thenReturn(mockLabelDataSym);
    SlottingPalletResponse slottingPalletResponse = mock(SlottingPalletResponse.class);
    if (!slottingPalletResponse.getLocations().isEmpty()) {
      slottingPalletResponse.getLocations().get(0).setLocation("Location1");
    }
    when(slottingDivertLocations.getLocation()).thenReturn("Location1");
    List<SlottingDivertLocations> locations = new ArrayList<>();
    locations.add(slottingDivertLocations);
    when(slottingPalletResponse.getLocations()).thenReturn(locations);
    when(rdcSlottingUtils.receiveContainers(
            receiveInstructionRequest, "a311121121", httpHeaders, null))
        .thenReturn(slottingPalletResponse);
    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    doNothing()
        .when(rdcReceivingUtils)
        .publishInstruction(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean());
    Set labelTrackingIdsMock = mock(Set.class);
    when(printJobService.createPrintJob(anyLong(), any(), eq(labelTrackingIdsMock), any()))
        .thenReturn(printJob);
    List<ReceivedContainer> mockReceivedContainers = mock(List.class);

    when(mockReceivedContainers.stream()).thenReturn(Stream.empty());
    List<ReceivedContainer> receivedContainers = mock(List.class);
    ReceivedContainer receivedContainer = mock(ReceivedContainer.class);
    when(receivedContainers.get(0)).thenReturn(receivedContainer);
    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            getMockInstructionRequest(receiveInstructionRequest, httpHeaders),
            5,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(2))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
  }

  private List<com.walmart.move.nim.receiving.core.entity.LabelData>
      getMockLabelDataWithEmptyChildContainer() {
    try {
      List<com.walmart.move.nim.receiving.core.entity.LabelData> labelDataList = new ArrayList<>();
      com.walmart.move.nim.receiving.core.entity.LabelData labelData1 =
          new com.walmart.move.nim.receiving.core.entity.LabelData();
      labelData1.setId(1L);
      labelData1.setDeliveryNumber(21958062L);
      labelData1.setPurchaseReferenceNumber("5030140190");
      labelData1.setPurchaseReferenceLineNumber(1);
      labelData1.setItemNumber(658232698L);
      labelData1.setLabelSequenceNbr(20231023000100001L);
      labelData1.setVnpk(1);
      labelData1.setWhpk(1);
      String allocationPayload =
          "{\n"
              + "   \"container\": {\n"
              + "    \"trackingId\": \"q060200000100000000767093\",\n"
              + "    \"cartonTag\": \"060439900003727268\",\n"
              + "    \"ctrType\": \"CASE\",\n"
              + "    \"finalDestination\": {\n"
              + "        \"countryCode\": \"US\",\n"
              + "        \"buNumber\": \"06030\"\n"
              + "    },\n"
              + "    \"outboundChannelMethod\": \"DA\",\n"
              + "    \"ctrReusable\": false,\n"
              + "    \"fulfillmentMethod\": \"VOICE_PUT\"\n"
              + "}"
              + "}";
      LabelDataAllocationDTO instructionDownloadContainerDTO1 =
          new ObjectMapper().readValue(allocationPayload, LabelDataAllocationDTO.class);
      labelData1.setAllocation(instructionDownloadContainerDTO1);
      labelData1.setQuantityUOM("EA");
      labelData1.setStatus(LabelInstructionStatus.AVAILABLE.name());
      labelData1.setOrderQuantity(1);
      labelData1.setQuantity(1);
      labelData1.setTrackingId("q060200000100000000767093");
      labelData1.setQuantity(1);
      labelDataList.add(labelData1);
      return labelDataList;

    } catch (Exception ex) {
      return Collections.emptyList();
    }
  }

  @Test
  public void testUpdateLabelStatusVoidToHawkeye() throws Exception {
    List<com.walmart.move.nim.receiving.core.entity.LabelData> labelDataList = getMockLabelData();
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    ItemData additionalInfo = new ItemData();
    additionalInfo.setItemPackAndHandlingCode("CE");
    deliveryDocumentLine.setAdditionalInfo(additionalInfo);

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_VOID_LABELS_FOR_CASE_PACK_SYM_INELIGIBLE_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_HAWKEYE_INTEGRATION_ENABLED_FOR_MANUAL_RECEIVING,
            false))
        .thenReturn(Boolean.TRUE);

    when(rdcManagedConfig.getCasePackSymIneligibleHandlingCodes()).thenReturn(Arrays.asList("CE"));
    doNothing().when(rdcAsyncUtils).updateLabelStatusVoidToHawkeye(any(), any());
    rdcDaService.updateLabelStatusVoidToHawkeye(
        labelDataList, true, httpHeaders, deliveryDocumentLine);

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_VOID_LABELS_FOR_CASE_PACK_SYM_INELIGIBLE_ENABLED,
            false);

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false);

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_HAWKEYE_INTEGRATION_ENABLED_FOR_MANUAL_RECEIVING,
            false);

    verify(rdcManagedConfig, times(1)).getCasePackSymIneligibleHandlingCodes();
    verify(rdcAsyncUtils, times(1)).updateLabelStatusVoidToHawkeye(any(), any());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testReceiveDAInstruction_Non_Supported_Handling_Code_ForNonAtlasItem()
      throws IOException, ReceivingException, ExecutionException, InterruptedException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDAReceivingFeatureType();
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("S");
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(Boolean.TRUE);
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(121);
    deliveryDocumentLongPair = new Pair<>(deliveryDocuments.get(0), 10L);
    when(rdcInstructionUtils.isProblemTagValidationApplicable(any())).thenReturn(true);
    when(rdcReceivingUtils.checkIfMaxOverageReceived(
            deliveryDocumentLongPair.getKey(),
            deliveryDocumentLongPair.getValue(),
            RdcConstants.RDC_DA_CASE_RECEIVE_QTY))
        .thenReturn(Boolean.FALSE);
    doNothing()
        .when(rdcReceivingUtils)
        .isPoAndPoLineInReceivableStatus(any(DeliveryDocumentLine.class));
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            nullable(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);
    when(containerPersisterService.findAllItemByTrackingId(anyList())).thenReturn(null);
    when(containerService.getContainerListByTrackingIdList(anyList())).thenReturn(null);
    when(nimRdsService.getReceiveContainersRequestBodyForDAReceiving(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            anyInt(),
            any(ReceiveInstructionRequest.class)))
        .thenReturn(getMockRdsContainerRequest());
    when(rdcReceivingUtils.receiveContainers(
            anyInt(),
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            eq(null)))
        .thenReturn(MockRdsResponse.getRdsResponseForDABreakPack().getReceived());
    when(tenantSpecificConfigReader.getDCTimeZone(anyInt())).thenReturn("US/Eastern");
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);
    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());
    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            any(Container.class),
            eq(null)))
        .thenReturn(new Container());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(rdcReceivingUtils.validateBreakPackItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());
    when(rdcReceivingUtils.validateRtsPutItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class),
            any(HttpHeaders.class)))
        .thenReturn(new InstructionResponseImplNew());
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());
    doNothing().when(rdcReceivingUtils).overrideItemProperties(any(DeliveryDocument.class));
    when(rdcReceivingUtils.checkIfNonConveyableItem(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DA_ATLAS_NON_SUPPORTED_HANDLING_CODES_BLOCKED,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcManagedConfig.getDaAtlasItemEnabledPackHandlingCode())
        .thenReturn(
            Arrays.asList(
                "CC", "CI", "CJ", "CX", "CE", "BC", "BX", "BM", "CB", "CN", "BN", "BB", "CL", "CV",
                "BV"));

    InstructionResponse instructionResponse =
        rdcDaService.createInstructionForDACaseReceiving(
            instructionRequest, deliveryDocumentLongPair.getValue(), httpHeaders);

    when(rdcReceivingUtils.checkIfMaxOverageReceived(
            deliveryDocumentLongPair.getKey(),
            deliveryDocumentLongPair.getValue(),
            RdcConstants.RDC_DA_CASE_RECEIVE_QTY))
        .thenReturn(Boolean.FALSE);
    verify(rdcInstructionUtils, times(0))
        .getOverageAlertInstruction(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(rdcReceivingUtils, times(1))
        .isPoAndPoLineInReceivableStatus(any(DeliveryDocumentLine.class));
    verify(rdcReceivingUtils, times(1))
        .checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            nullable(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .validateBreakPackItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class));
    verify(rdcReceivingUtils, times(1))
        .checkIfNonConveyableItem(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null));
    verify(rdcContainerUtils, times(1))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(
            anyList(), anyList(), anyList(), anyList(), nullable(List.class));
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
  }

  @Test
  public void
      testReceiveDAInstruction_Non_Supported_Handling_Code_ForNonAtlasItem_CCM_Flag_Disabled()
          throws IOException, ReceivingException, ExecutionException, InterruptedException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDAReceivingFeatureType();
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("L");
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(Boolean.FALSE);
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(121);
    deliveryDocumentLongPair = new Pair<>(deliveryDocuments.get(0), 10L);
    when(rdcInstructionUtils.isProblemTagValidationApplicable(any())).thenReturn(true);
    when(rdcReceivingUtils.checkIfMaxOverageReceived(
            deliveryDocumentLongPair.getKey(),
            deliveryDocumentLongPair.getValue(),
            RdcConstants.RDC_DA_CASE_RECEIVE_QTY))
        .thenReturn(Boolean.FALSE);
    doNothing()
        .when(rdcReceivingUtils)
        .isPoAndPoLineInReceivableStatus(any(DeliveryDocumentLine.class));
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            nullable(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);
    when(containerPersisterService.findAllItemByTrackingId(anyList())).thenReturn(null);
    when(containerService.getContainerListByTrackingIdList(anyList())).thenReturn(null);
    when(nimRdsService.getReceiveContainersRequestBodyForDAReceiving(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            anyInt(),
            any(ReceiveInstructionRequest.class)))
        .thenReturn(getMockRdsContainerRequest());
    when(rdcReceivingUtils.receiveContainers(
            anyInt(),
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            eq(null)))
        .thenReturn(MockRdsResponse.getRdsResponseForDABreakPack().getReceived());
    when(tenantSpecificConfigReader.getDCTimeZone(anyInt())).thenReturn("US/Eastern");
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);
    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());
    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            any(Container.class),
            eq(null)))
        .thenReturn(new Container());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(rdcReceivingUtils.validateBreakPackItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());
    when(rdcReceivingUtils.validateRtsPutItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class),
            any(HttpHeaders.class)))
        .thenReturn(new InstructionResponseImplNew());
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());
    doNothing().when(rdcReceivingUtils).overrideItemProperties(any(DeliveryDocument.class));
    when(rdcReceivingUtils.checkIfNonConveyableItem(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DA_ATLAS_NON_SUPPORTED_HANDLING_CODES_BLOCKED,
            false))
        .thenReturn(Boolean.FALSE);
    when(rdcManagedConfig.getDaAtlasItemEnabledPackHandlingCode())
        .thenReturn(
            Arrays.asList(
                "CC", "CI", "CJ", "CX", "CE", "BC", "BX", "BM", "CB", "CN", "BN", "BB", "CL", "CV",
                "BV"));

    InstructionResponse instructionResponse =
        rdcDaService.createInstructionForDACaseReceiving(
            instructionRequest, deliveryDocumentLongPair.getValue(), httpHeaders);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(response);
    assertEquals(response.getDeliveryDocuments().size(), 1);
    assertNotNull(response.getInstruction());
    assertNotNull(response);
    assertEquals(response.getPrintJob().size(), 3);
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_HEADERS_KEY));
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_CLIENT_ID_KEY));
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_REQUEST_KEY));
    assertEquals(
        response.getPrintJob().get(ReceivingConstants.PRINT_CLIENT_ID_KEY),
        ReceivingConstants.ATLAS_RECEIVING);
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_REQUEST_KEY));

    List<PrintLabelRequest> printLabelRequests =
        (List<PrintLabelRequest>) response.getPrintJob().get(ReceivingConstants.PRINT_REQUEST_KEY);
    assertEquals(printLabelRequests.size(), 1);

    when(rdcReceivingUtils.checkIfMaxOverageReceived(
            deliveryDocumentLongPair.getKey(),
            deliveryDocumentLongPair.getValue(),
            RdcConstants.RDC_DA_CASE_RECEIVE_QTY))
        .thenReturn(Boolean.FALSE);
    verify(rdcInstructionUtils, times(0))
        .getOverageAlertInstruction(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(rdcReceivingUtils, times(1))
        .isPoAndPoLineInReceivableStatus(any(DeliveryDocumentLine.class));
    verify(rdcReceivingUtils, times(1))
        .checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            nullable(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .validateBreakPackItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class));
    verify(rdcReceivingUtils, times(1))
        .checkIfNonConveyableItem(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null));
    verify(rdcContainerUtils, times(1))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(
            anyList(), anyList(), anyList(), anyList(), nullable(List.class));
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
  }

  @Test
  public void testReceiveDAInstruction_Supported_Handling_Code_ForNonAtlasItem_CCM_Flag_Enabled()
      throws IOException, ReceivingException, ExecutionException, InterruptedException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDAReceivingFeatureType();
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(Boolean.FALSE);
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(121);
    deliveryDocumentLongPair = new Pair<>(deliveryDocuments.get(0), 10L);
    when(rdcInstructionUtils.isProblemTagValidationApplicable(any())).thenReturn(true);
    when(rdcReceivingUtils.checkIfMaxOverageReceived(
            deliveryDocumentLongPair.getKey(),
            deliveryDocumentLongPair.getValue(),
            RdcConstants.RDC_DA_CASE_RECEIVE_QTY))
        .thenReturn(Boolean.FALSE);
    doNothing()
        .when(rdcReceivingUtils)
        .isPoAndPoLineInReceivableStatus(any(DeliveryDocumentLine.class));
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            nullable(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);
    when(containerPersisterService.findAllItemByTrackingId(anyList())).thenReturn(null);
    when(containerService.getContainerListByTrackingIdList(anyList())).thenReturn(null);
    when(nimRdsService.getReceiveContainersRequestBodyForDAReceiving(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            anyInt(),
            any(ReceiveInstructionRequest.class)))
        .thenReturn(getMockRdsContainerRequest());
    when(rdcReceivingUtils.receiveContainers(
            anyInt(),
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            eq(null)))
        .thenReturn(MockRdsResponse.getRdsResponseForDABreakPack().getReceived());
    when(tenantSpecificConfigReader.getDCTimeZone(anyInt())).thenReturn("US/Eastern");
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);
    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());
    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            any(Container.class),
            eq(null)))
        .thenReturn(new Container());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(rdcReceivingUtils.validateBreakPackItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());
    when(rdcReceivingUtils.validateRtsPutItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class),
            any(HttpHeaders.class)))
        .thenReturn(new InstructionResponseImplNew());
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());
    doNothing().when(rdcReceivingUtils).overrideItemProperties(any(DeliveryDocument.class));
    when(rdcReceivingUtils.checkIfNonConveyableItem(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class)))
        .thenReturn(new InstructionResponseImplNew());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DA_ATLAS_NON_SUPPORTED_HANDLING_CODES_BLOCKED,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcManagedConfig.getAtlasDaNonSupportedHandlingCodes())
        .thenReturn(Arrays.asList("D", "L", "V", "P"));

    InstructionResponse instructionResponse =
        rdcDaService.createInstructionForDACaseReceiving(
            instructionRequest, deliveryDocumentLongPair.getValue(), httpHeaders);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(response);
    assertEquals(response.getDeliveryDocuments().size(), 1);
    assertNotNull(response.getInstruction());
    assertNotNull(response);
    assertEquals(response.getPrintJob().size(), 3);
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_HEADERS_KEY));
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_CLIENT_ID_KEY));
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_REQUEST_KEY));
    assertEquals(
        response.getPrintJob().get(ReceivingConstants.PRINT_CLIENT_ID_KEY),
        ReceivingConstants.ATLAS_RECEIVING);
    assertTrue(response.getPrintJob().containsKey(ReceivingConstants.PRINT_REQUEST_KEY));

    List<PrintLabelRequest> printLabelRequests =
        (List<PrintLabelRequest>) response.getPrintJob().get(ReceivingConstants.PRINT_REQUEST_KEY);
    assertEquals(printLabelRequests.size(), 1);

    when(rdcReceivingUtils.checkIfMaxOverageReceived(
            deliveryDocumentLongPair.getKey(),
            deliveryDocumentLongPair.getValue(),
            RdcConstants.RDC_DA_CASE_RECEIVE_QTY))
        .thenReturn(Boolean.FALSE);
    verify(rdcInstructionUtils, times(0))
        .getOverageAlertInstruction(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(rdcReceivingUtils, times(1))
        .isPoAndPoLineInReceivableStatus(any(DeliveryDocumentLine.class));
    verify(rdcReceivingUtils, times(1))
        .checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            nullable(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .validateBreakPackItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class));
    verify(rdcReceivingUtils, times(1))
        .checkIfNonConveyableItem(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null));
    verify(rdcContainerUtils, times(1))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(
            anyList(), anyList(), anyList(), anyList(), nullable(List.class));
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
  }

  @Test
  public void
      testReceiveInstruction_CasePack_NonConHanlingCodeAsL_AtlasItem_ForHandlingCodeConversion()
          throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    List<String> lpns =
        Arrays.asList(
            "a47263319186056300", "a47263319186056301", "a47263319186056302", "a47263319186056303");
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("L");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CL");
    when(rdcManagedConfig.getAtlasDaNonSupportedHandlingCodes())
        .thenReturn(Arrays.asList("D", "L", "V", "P"));
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);

    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_RTS_PUT_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.FALSE);
    when(lpnCacheService.getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class))).thenReturn(lpns);
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(
            Collections.singletonList(
                getMockLabelDataEmptyChildContainer_HandlingCodeAsL().get(0)));

    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt()))
        .thenReturn(Arrays.asList(new Receipt()));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_WORK_STATION_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            1,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(1))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    verify(rdcReceivingUtils, times(1))
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    verify(labelDataService, times(1))
        .fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode());

    verify(receiptService, times(1))
        .buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean());
  }

  @Test
  public void
      testReceiveInstruction_CasePack_NonConHanlingCodeAsV_AtlasItem_HandlingCode_NotInRdcManagedConfig()
          throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    List<String> lpns =
        Arrays.asList(
            "a47263319186056300", "a47263319186056301", "a47263319186056302", "a47263319186056303");
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("V");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CV");
    when(rdcManagedConfig.getAtlasDaNonSupportedHandlingCodes())
        .thenReturn(Arrays.asList("L", "P"));
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);

    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_RTS_PUT_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.FALSE);
    when(lpnCacheService.getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class))).thenReturn(lpns);
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(
            Collections.singletonList(
                getMockLabelDataEmptyChildContainer_WithNonSupportedHandlingCode_NotInRdcManagedConfig()
                    .get(0)));

    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt()))
        .thenReturn(Arrays.asList(new Receipt()));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_WORK_STATION_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            1,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(1))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    verify(rdcReceivingUtils, times(1))
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    verify(labelDataService, times(1))
        .fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode());

    verify(receiptService, times(1))
        .buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean());
  }

  @Test
  public void testReceiveDAQtyReceivingInstructionDACasePackConventionalSlotting_Success()
      throws IOException, ReceivingException, ExecutionException, InterruptedException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(Long.valueOf(1234));
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(121);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CC");
    List<ReceivedContainer> recievedContainerList = new ArrayList<>();
    ReceiveInstructionRequest receiveInstructionRequest =
        getMockReceiveInstructionRequest_DaCasePackConventionalSlotting();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SMART_SLOTTING_ENABLED_ON_DA_ITEMS,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DA_AUTOMATION_SLOTTING_ENABLED_FOR_ATLAS_CONVEYABLE_ITEM,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DA_CONVENTIONAL_SLOTTING_ENABLED_FOR_ATLAS_CONVEYABLE_ITEM,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_HAWKEYE_INTEGRATION_ENABLED_FOR_MANUAL_RECEIVING,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcManagedConfig.getAtlasConveyableHandlingCodesForDaSlotting())
        .thenReturn(Arrays.asList("C", "I", "J", "E"));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SMART_SLOTTING_ENABLED_ON_DA_ITEMS,
            false))
        .thenReturn(Boolean.TRUE);
    when(hawkeyeRestApiClient.getLpnsFromHawkeye(
            any(HawkeyeGetLpnsRequest.class), any(HttpHeaders.class)))
        .thenReturn(
            Optional.of(Arrays.asList("a326790000100000005887074", "a326790000100000005887075")));
    when(labelDataService.findByTrackingIdIn(anyList())).thenReturn(getMockLabelDataSym());
    when(rdcReceivingUtils.receiveContainers(
            anyInt(),
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(ReceiveInstructionRequest.class)))
        .thenReturn(recievedContainerList);

    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);
    when(containerPersisterService.findAllItemByTrackingId(anyList())).thenReturn(null);
    when(containerService.getContainerListByTrackingIdList(anyList())).thenReturn(null);
    when(rdcContainerUtils.buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class)))
        .thenReturn(Arrays.asList(new ContainerItem()));
    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null)))
        .thenReturn(new Container());
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(rdcLpnUtils.getLPNs(anyInt(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList("a311121121"));

    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            anyString(), anyLong(), anyString(), anyInt(), anyInt(), anyString()))
        .thenReturn(getMockLabelDataSym());
    SlottingPalletResponse slottingPalletResponse = mock(SlottingPalletResponse.class);
    if (!slottingPalletResponse.getLocations().isEmpty()) {
      slottingPalletResponse.getLocations().get(0).setLocation("Location1");
    }
    when(slottingDivertLocations.getLocation()).thenReturn("Location1");
    List<SlottingDivertLocations> locations = new ArrayList<>();
    locations.add(slottingDivertLocations);
    when(slottingPalletResponse.getLocations()).thenReturn(locations);
    when(rdcSlottingUtils.receiveContainers(
            receiveInstructionRequest, "a311121121", httpHeaders, null))
        .thenReturn(slottingPalletResponse);
    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    doNothing()
        .when(rdcReceivingUtils)
        .publishInstruction(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean());
    Set labelTrackingIdsMock = mock(Set.class);
    when(printJobService.createPrintJob(anyLong(), any(), eq(labelTrackingIdsMock), any()))
        .thenReturn(printJob);
    List<ReceivedContainer> mockReceivedContainers = mock(List.class);

    when(mockReceivedContainers.stream()).thenReturn(Stream.empty());
    List<ReceivedContainer> receivedContainers = mock(List.class);
    ReceivedContainer receivedContainer = mock(ReceivedContainer.class);
    when(receivedContainers.get(0)).thenReturn(receivedContainer);
    when(rdcReceivingUtils.getLabelFormatForPallet(any(DeliveryDocumentLine.class)))
        .thenReturn(LabelFormat.ATLAS_RDC_PALLET);
    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            getMockInstructionRequest(receiveInstructionRequest, httpHeaders),
            5,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(2))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "DA Conventional slotting is not allowed for Non Conveyable item handling codes. Please verify the item handling code before slotting the item.")
  public void
      testReceiveDAQtyReceivingInstructionDACasePackConventionalSlotting_ThrowsExceptionWhenHandlingCodeIsNonCon()
          throws IOException, ReceivingException, ExecutionException, InterruptedException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(Long.valueOf(1234));
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(121);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("N");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CN");
    ReceiveInstructionRequest receiveInstructionRequest =
        getMockReceiveInstructionRequest_DaCasePackConventionalSlotting();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            anyString(), anyLong(), anyString(), anyInt(), anyInt(), anyString()))
        .thenReturn(getMockLabelDataSym());
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DA_AUTOMATION_SLOTTING_ENABLED_FOR_ATLAS_CONVEYABLE_ITEM,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DA_CONVENTIONAL_SLOTTING_ENABLED_FOR_ATLAS_CONVEYABLE_ITEM,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_HAWKEYE_INTEGRATION_ENABLED_FOR_MANUAL_RECEIVING,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SMART_SLOTTING_ENABLED_ON_DA_ITEMS,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcManagedConfig.getAtlasConveyableHandlingCodesForDaSlotting())
        .thenReturn(Arrays.asList("C", "I", "J", "E"));

    rdcDaService.receiveContainers(
        deliveryDocuments.get(0),
        httpHeaders,
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders),
        5,
        receiveInstructionRequest);
  }

  @Test
  public void testReceiveDAQtyReceivingInstructionDACasePackConventionalManualSlotting_Success()
      throws IOException, ReceivingException, ExecutionException, InterruptedException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(Long.valueOf(1234));
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(121);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CC");
    List<ReceivedContainer> recievedContainerList = new ArrayList<>();
    ReceiveInstructionRequest receiveInstructionRequest =
        getMockReceiveInstructionRequest_DaCasePackConventionalSlotting();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    SlotDetails slotDetails = receiveInstructionRequest.getSlotDetails();
    slotDetails.setSlot("V43256");
    slotDetails.setSlotType("");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SMART_SLOTTING_ENABLED_ON_DA_ITEMS,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DA_AUTOMATION_SLOTTING_ENABLED_FOR_ATLAS_CONVEYABLE_ITEM,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DA_CONVENTIONAL_SLOTTING_ENABLED_FOR_ATLAS_CONVEYABLE_ITEM,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_HAWKEYE_INTEGRATION_ENABLED_FOR_MANUAL_RECEIVING,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcManagedConfig.getAtlasConveyableHandlingCodesForDaSlotting())
        .thenReturn(Arrays.asList("C", "I", "J", "E"));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SMART_SLOTTING_ENABLED_ON_DA_ITEMS,
            false))
        .thenReturn(Boolean.TRUE);
    when(hawkeyeRestApiClient.getLpnsFromHawkeye(
            any(HawkeyeGetLpnsRequest.class), any(HttpHeaders.class)))
        .thenReturn(
            Optional.of(Arrays.asList("a326790000100000005887074", "a326790000100000005887075")));
    when(labelDataService.findByTrackingIdIn(anyList())).thenReturn(getMockLabelDataSym());
    when(rdcReceivingUtils.receiveContainers(
            anyInt(),
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(ReceiveInstructionRequest.class)))
        .thenReturn(recievedContainerList);

    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);
    when(containerPersisterService.findAllItemByTrackingId(anyList())).thenReturn(null);
    when(containerService.getContainerListByTrackingIdList(anyList())).thenReturn(null);
    when(rdcContainerUtils.buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class)))
        .thenReturn(Arrays.asList(new ContainerItem()));
    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null)))
        .thenReturn(new Container());
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(rdcLpnUtils.getLPNs(anyInt(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList("a311121121"));

    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            anyString(), anyLong(), anyString(), anyInt(), anyInt(), anyString()))
        .thenReturn(getMockLabelDataSym());
    SlottingPalletResponse slottingPalletResponse = mock(SlottingPalletResponse.class);
    if (!slottingPalletResponse.getLocations().isEmpty()) {
      slottingPalletResponse.getLocations().get(0).setLocation("Location1");
    }
    when(slottingDivertLocations.getLocation()).thenReturn("Location1");
    List<SlottingDivertLocations> locations = new ArrayList<>();
    locations.add(slottingDivertLocations);
    when(slottingPalletResponse.getLocations()).thenReturn(locations);
    when(rdcSlottingUtils.receiveContainers(
            receiveInstructionRequest, "a311121121", httpHeaders, null))
        .thenReturn(slottingPalletResponse);
    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    doNothing()
        .when(rdcReceivingUtils)
        .publishInstruction(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean());
    Set labelTrackingIdsMock = mock(Set.class);
    when(printJobService.createPrintJob(anyLong(), any(), eq(labelTrackingIdsMock), any()))
        .thenReturn(printJob);
    List<ReceivedContainer> mockReceivedContainers = mock(List.class);

    when(mockReceivedContainers.stream()).thenReturn(Stream.empty());
    List<ReceivedContainer> receivedContainers = mock(List.class);
    ReceivedContainer receivedContainer = mock(ReceivedContainer.class);
    when(receivedContainers.get(0)).thenReturn(receivedContainer);
    when(rdcReceivingUtils.getLabelFormatForPallet(any(DeliveryDocumentLine.class)))
        .thenReturn(LabelFormat.ATLAS_RDC_PALLET);
    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            getMockInstructionRequest(receiveInstructionRequest, httpHeaders),
            5,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(2))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
  }

  @Test
  public void
      testReceiveDAQtyReceivingInstructionDACasePackConventionalManualSlotting_SlotDetailsNull()
          throws IOException, ReceivingException, ExecutionException, InterruptedException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(Long.valueOf(1234));
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(121);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CC");
    List<ReceivedContainer> recievedContainerList = new ArrayList<>();
    ReceiveInstructionRequest receiveInstructionRequest =
        getMockReceiveInstructionRequest_DaCasePackConventionalSlotting();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    SlotDetails slotDetails = receiveInstructionRequest.getSlotDetails();
    slotDetails.setSlot(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SMART_SLOTTING_ENABLED_ON_DA_ITEMS,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DA_AUTOMATION_SLOTTING_ENABLED_FOR_ATLAS_CONVEYABLE_ITEM,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DA_CONVENTIONAL_SLOTTING_ENABLED_FOR_ATLAS_CONVEYABLE_ITEM,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_HAWKEYE_INTEGRATION_ENABLED_FOR_MANUAL_RECEIVING,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcManagedConfig.getAtlasConveyableHandlingCodesForDaSlotting())
        .thenReturn(Arrays.asList("C", "I", "J", "E"));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SMART_SLOTTING_ENABLED_ON_DA_ITEMS,
            false))
        .thenReturn(Boolean.TRUE);
    when(hawkeyeRestApiClient.getLpnsFromHawkeye(
            any(HawkeyeGetLpnsRequest.class), any(HttpHeaders.class)))
        .thenReturn(
            Optional.of(Arrays.asList("a326790000100000005887074", "a326790000100000005887075")));
    when(labelDataService.findByTrackingIdIn(anyList())).thenReturn(getMockLabelDataSym());
    when(rdcReceivingUtils.receiveContainers(
            anyInt(),
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(ReceiveInstructionRequest.class)))
        .thenReturn(recievedContainerList);

    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);
    when(containerPersisterService.findAllItemByTrackingId(anyList())).thenReturn(null);
    when(containerService.getContainerListByTrackingIdList(anyList())).thenReturn(null);
    when(rdcContainerUtils.buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class)))
        .thenReturn(Arrays.asList(new ContainerItem()));
    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null)))
        .thenReturn(new Container());
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(rdcLpnUtils.getLPNs(anyInt(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList("a311121121"));

    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            anyString(), anyLong(), anyString(), anyInt(), anyInt(), anyString()))
        .thenReturn(getMockLabelDataSym());
    SlottingPalletResponse slottingPalletResponse = mock(SlottingPalletResponse.class);
    if (!slottingPalletResponse.getLocations().isEmpty()) {
      slottingPalletResponse.getLocations().get(0).setLocation("Location1");
    }
    when(slottingDivertLocations.getLocation()).thenReturn("Location1");
    List<SlottingDivertLocations> locations = new ArrayList<>();
    locations.add(slottingDivertLocations);
    when(slottingPalletResponse.getLocations()).thenReturn(locations);
    when(rdcSlottingUtils.receiveContainers(
            receiveInstructionRequest, "a311121121", httpHeaders, null))
        .thenReturn(slottingPalletResponse);
    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    doNothing()
        .when(rdcReceivingUtils)
        .publishInstruction(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean());
    Set labelTrackingIdsMock = mock(Set.class);
    when(printJobService.createPrintJob(anyLong(), any(), eq(labelTrackingIdsMock), any()))
        .thenReturn(printJob);
    when(rdcReceivingUtils.getLabelFormatForPallet(any(DeliveryDocumentLine.class)))
        .thenReturn(LabelFormat.ATLAS_RDC_PALLET);
    List<ReceivedContainer> mockReceivedContainers = mock(List.class);

    when(mockReceivedContainers.stream()).thenReturn(Stream.empty());
    List<ReceivedContainer> receivedContainers = mock(List.class);
    ReceivedContainer receivedContainer = mock(ReceivedContainer.class);
    when(receivedContainers.get(0)).thenReturn(receivedContainer);
    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            getMockInstructionRequest(receiveInstructionRequest, httpHeaders),
            5,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(2))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
  }

  @Test
  public void
      testReceiveDAQtyReceivingInstructionDACasePackConventionalManualSlotting_SlotNotAvailable()
          throws IOException, ReceivingException, ExecutionException, InterruptedException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(Long.valueOf(1234));
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(121);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CC");
    List<ReceivedContainer> recievedContainerList = new ArrayList<>();
    ReceiveInstructionRequest receiveInstructionRequest =
        getMockReceiveInstructionRequest_DaCasePackConventionalSlotting();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    SlotDetails slotDetails = receiveInstructionRequest.getSlotDetails();
    slotDetails.setSlot(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SMART_SLOTTING_ENABLED_ON_DA_ITEMS,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DA_AUTOMATION_SLOTTING_ENABLED_FOR_ATLAS_CONVEYABLE_ITEM,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DA_CONVENTIONAL_SLOTTING_ENABLED_FOR_ATLAS_CONVEYABLE_ITEM,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_HAWKEYE_INTEGRATION_ENABLED_FOR_MANUAL_RECEIVING,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcManagedConfig.getAtlasConveyableHandlingCodesForDaSlotting())
        .thenReturn(Arrays.asList("C", "I", "J", "E"));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SMART_SLOTTING_ENABLED_ON_DA_ITEMS,
            false))
        .thenReturn(Boolean.TRUE);
    when(hawkeyeRestApiClient.getLpnsFromHawkeye(
            any(HawkeyeGetLpnsRequest.class), any(HttpHeaders.class)))
        .thenReturn(
            Optional.of(Arrays.asList("a326790000100000005887074", "a326790000100000005887075")));
    when(labelDataService.findByTrackingIdIn(anyList())).thenReturn(getMockLabelDataSym());
    when(rdcReceivingUtils.receiveContainers(
            anyInt(),
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(ReceiveInstructionRequest.class)))
        .thenReturn(recievedContainerList);

    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);
    when(containerPersisterService.findAllItemByTrackingId(anyList())).thenReturn(null);
    when(containerService.getContainerListByTrackingIdList(anyList())).thenReturn(null);
    when(rdcContainerUtils.buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class)))
        .thenReturn(Arrays.asList(new ContainerItem()));
    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null)))
        .thenReturn(new Container());
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(rdcLpnUtils.getLPNs(anyInt(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList("a311121121"));

    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            anyString(), anyLong(), anyString(), anyInt(), anyInt(), anyString()))
        .thenReturn(getMockLabelDataSym());
    SlottingPalletResponse slottingPalletResponse = mock(SlottingPalletResponse.class);
    if (!slottingPalletResponse.getLocations().isEmpty()) {
      slottingPalletResponse.getLocations().get(0).setLocation("Location1");
    }
    when(slottingDivertLocations.getLocation()).thenReturn("Location1");
    List<SlottingDivertLocations> locations = new ArrayList<>();
    locations.add(slottingDivertLocations);
    when(slottingPalletResponse.getLocations()).thenReturn(locations);
    when(rdcSlottingUtils.receiveContainers(
            receiveInstructionRequest, "a311121121", httpHeaders, null))
        .thenReturn(slottingPalletResponse);
    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    doNothing()
        .when(rdcReceivingUtils)
        .publishInstruction(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean());
    Set labelTrackingIdsMock = mock(Set.class);
    when(printJobService.createPrintJob(anyLong(), any(), eq(labelTrackingIdsMock), any()))
        .thenReturn(printJob);
    List<ReceivedContainer> mockReceivedContainers = mock(List.class);

    when(mockReceivedContainers.stream()).thenReturn(Stream.empty());
    List<ReceivedContainer> receivedContainers = mock(List.class);
    ReceivedContainer receivedContainer = mock(ReceivedContainer.class);
    when(receivedContainers.get(0)).thenReturn(receivedContainer);
    when(rdcReceivingUtils.getLabelFormatForPallet(any(DeliveryDocumentLine.class)))
        .thenReturn(LabelFormat.ATLAS_RDC_PALLET);
    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            getMockInstructionRequest(receiveInstructionRequest, httpHeaders),
            5,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(2))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void throwsExceptionWhenDACasePackConventionalManualSlotting_ConventionalSlottingDisabled()
      throws IOException, ReceivingException, ExecutionException, InterruptedException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(Long.valueOf(1234));
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(121);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CC");
    List<ReceivedContainer> recievedContainerList = new ArrayList<>();
    ReceiveInstructionRequest receiveInstructionRequest =
        getMockReceiveInstructionRequest_DaCasePackConventionalSlotting();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    SlotDetails slotDetails = receiveInstructionRequest.getSlotDetails();
    slotDetails.setSlot(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SMART_SLOTTING_ENABLED_ON_DA_ITEMS,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DA_AUTOMATION_SLOTTING_ENABLED_FOR_ATLAS_CONVEYABLE_ITEM,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_DA_CONVENTIONAL_SLOTTING_ENABLED_FOR_ATLAS_CONVEYABLE_ITEM,
            false))
        .thenReturn(Boolean.FALSE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_HAWKEYE_INTEGRATION_ENABLED_FOR_MANUAL_RECEIVING,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcManagedConfig.getAtlasConveyableHandlingCodesForDaSlotting())
        .thenReturn(Arrays.asList("C", "I", "J", "E"));
    when(rdcManagedConfig.getAtlasDaNonConPackAndHandlingCodes())
        .thenReturn(Arrays.asList("CN", "BN", "CV", "BV"));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SMART_SLOTTING_ENABLED_ON_DA_ITEMS,
            false))
        .thenReturn(Boolean.TRUE);
    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            getMockInstructionRequest(receiveInstructionRequest, httpHeaders),
            5,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;
  }

  private ReceiveInstructionRequest getMockReceiveInstructionRequest_DaCasePackAutomationSlotting()
      throws IOException {
    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDoorNumber("117");
    receiveInstructionRequest.setQuantity(0);
    receiveInstructionRequest.setPalletQuantities(
        getMockPalletQuantities_DaCasePackAutomationSlotting());
    receiveInstructionRequest.setDeliveryNumber(Long.valueOf("80426073"));
    receiveInstructionRequest.setSlotDetails(getMockAutoSlotDetails_DaCasePackAutomationSlotting());
    return receiveInstructionRequest;
  }

  private ReceiveInstructionRequest
      getMockReceiveInstructionRequest_DaCasePackConventionalSlotting() {
    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDoorNumber("117");
    receiveInstructionRequest.setQuantity(0);
    receiveInstructionRequest.setPalletQuantities(
        getMockPalletQuantities_DaCasePackAutomationSlotting());
    receiveInstructionRequest.setDeliveryNumber(Long.valueOf("80426073"));
    receiveInstructionRequest.setSlotDetails(
        getMockAutoSlotDetails_DaCasePackConventionalSlotting());
    return receiveInstructionRequest;
  }

  private List<PalletQuantities> getMockPalletQuantities_DaCasePackAutomationSlotting() {
    List<PalletQuantities> palletQuantitiesList = new ArrayList<>();
    PalletQuantities palletQuantities = new PalletQuantities();
    palletQuantities.setPallet(1);
    palletQuantities.setQuantity(2);
    palletQuantitiesList.add(palletQuantities);
    return palletQuantitiesList;
  }

  private SlotDetails getMockAutoSlotDetails_DaCasePackAutomationSlotting() {
    SlotDetails slotDetails = new SlotDetails();
    slotDetails.setSlotType(RdcConstants.SLOT_TYPE_AUTOMATION);
    slotDetails.setStockType(RdcConstants.STOCK_TYPE_CONVEYABLE);
    return slotDetails;
  }

  private SlotDetails getMockAutoSlotDetails_DaCasePackConventionalSlotting() {
    SlotDetails slotDetails = new SlotDetails();
    slotDetails.setSlotType(RdcConstants.SLOT_TYPE_CONVENTIONAL);
    slotDetails.setStockType(RdcConstants.STOCK_TYPE_CONVEYABLE);
    slotDetails.setSlotSize(60);
    return slotDetails;
  }

  private List<com.walmart.move.nim.receiving.core.entity.LabelData>
      getMockLabelDataEmptyChildContainer_HandlingCodeAsL() {
    try {
      List<com.walmart.move.nim.receiving.core.entity.LabelData> labelDataList = new ArrayList<>();
      com.walmart.move.nim.receiving.core.entity.LabelData labelData1 =
          new com.walmart.move.nim.receiving.core.entity.LabelData();
      com.walmart.move.nim.receiving.core.entity.LabelData labelData2 =
          new com.walmart.move.nim.receiving.core.entity.LabelData();
      labelData1.setId(1L);
      labelData1.setDeliveryNumber(21958062L);
      labelData1.setPurchaseReferenceNumber("5030140190");
      labelData1.setPurchaseReferenceLineNumber(1);
      labelData1.setItemNumber(658232698L);
      labelData1.setLabelSequenceNbr(20231023000100001L);
      labelData1.setVnpk(1);
      labelData1.setWhpk(1);
      labelData2.setId(1L);
      labelData2.setDeliveryNumber(21958062L);
      labelData2.setPurchaseReferenceNumber("5030140190");
      labelData2.setPurchaseReferenceLineNumber(1);
      labelData2.setItemNumber(658232698L);
      labelData2.setLabelSequenceNbr(20231023000100001L);
      labelData2.setVnpk(1);
      labelData2.setWhpk(1);
      String allocationPayload =
          "{\n"
              + "   \"container\": {\n"
              + "    \"trackingId\": \"002560132679202860\",\n"
              + "    \"cartonTag\": \"060439900003727268\",\n"
              + "    \"ctrType\": \"CASE\",\n"
              + "    \"distributions\": [\n"
              + "        {\n"
              + "            \"orderId\": \"72debb0e-4fce-4477-8f5b-4fa2aee9e80c\",\n"
              + "            \"allocQty\": 1,\n"
              + "            \"qtyUom\": \"EA\",\n"
              + "            \"item\": {\n"
              + "                \"itemNbr\": 658232698,\n"
              + "                \"itemUpc\": \"0238408293492\",\n"
              + "                \"vnpk\": 2,\n"
              + "                \"whpk\": 2,\n"
              + "                \"itemdept\": \"\",\n"
              + "                \"baseDivisionCode\": \"WM\",\n"
              + "                \"financialReportingGroup\": \"US\",\n"
              + "                \"reportingGroup\": \"US\",\n"
              + "                \"aisle\": \"12\",\n"
              + "                \"zone\": \"03\",\n"
              + "                \"storeZone\": \"03\",\n"
              + "                \"dcZone\": \"03\",\n"
              + "                \"pickBatch\": \"281\",\n"
              + "                \"printBatch\": \"281\",\n"
              + "                \"storeAlignment\": \"CONVENTIONAL\",\n"
              + "                 \"packType\": \"CP\",\n"
              + "                 \"itemHandlingCode\": \"L\"\n"
              + "            }\n"
              + "        }\n"
              + "    ],\n"
              + "    \"finalDestination\": {\n"
              + "        \"countryCode\": \"US\",\n"
              + "        \"buNumber\": \"06030\"\n"
              + "    },\n"
              + "    \"outboundChannelMethod\": \"DA\",\n"
              + "    \"ctrReusable\": false,\n"
              + "    \"fulfillmentMethod\": \"VOICE_PUT\"\n"
              + "}"
              + "}";
      LabelDataAllocationDTO instructionDownloadContainerDTO1 =
          new ObjectMapper().readValue(allocationPayload, LabelDataAllocationDTO.class);
      labelData1.setAllocation(instructionDownloadContainerDTO1);
      labelData2.setAllocation(instructionDownloadContainerDTO1);
      labelData1.setQuantityUOM("EA");
      labelData1.setStatus(LabelInstructionStatus.AVAILABLE.name());
      labelData1.setOrderQuantity(1);
      labelData1.setQuantity(1);
      labelData1.setTrackingId("002560132679202860");
      labelData2.setQuantityUOM("EA");
      labelData2.setStatus(LabelInstructionStatus.AVAILABLE.name());
      labelData2.setOrderQuantity(1);
      labelData1.setQuantity(1);
      labelData2.setTrackingId("002560132679202861");
      labelDataList.add(labelData1);
      labelDataList.add(labelData2);
      return labelDataList;

    } catch (Exception ex) {
      return Collections.emptyList();
    }
  }

  private List<com.walmart.move.nim.receiving.core.entity.LabelData>
      getMockLabelDataEmptyChildContainer_WithNonSupportedHandlingCode_NotInRdcManagedConfig() {
    try {
      List<com.walmart.move.nim.receiving.core.entity.LabelData> labelDataList = new ArrayList<>();
      com.walmart.move.nim.receiving.core.entity.LabelData labelData1 =
          new com.walmart.move.nim.receiving.core.entity.LabelData();
      com.walmart.move.nim.receiving.core.entity.LabelData labelData2 =
          new com.walmart.move.nim.receiving.core.entity.LabelData();
      labelData1.setId(1L);
      labelData1.setDeliveryNumber(21958062L);
      labelData1.setPurchaseReferenceNumber("5030140190");
      labelData1.setPurchaseReferenceLineNumber(1);
      labelData1.setItemNumber(658232698L);
      labelData1.setLabelSequenceNbr(20231023000100001L);
      labelData1.setVnpk(1);
      labelData1.setWhpk(1);
      labelData2.setId(1L);
      labelData2.setDeliveryNumber(21958062L);
      labelData2.setPurchaseReferenceNumber("5030140190");
      labelData2.setPurchaseReferenceLineNumber(1);
      labelData2.setItemNumber(658232698L);
      labelData2.setLabelSequenceNbr(20231023000100001L);
      labelData2.setVnpk(1);
      labelData2.setWhpk(1);
      String allocationPayload =
          "{\n"
              + "   \"container\": {\n"
              + "    \"trackingId\": \"002560132679202860\",\n"
              + "    \"cartonTag\": \"060439900003727268\",\n"
              + "    \"ctrType\": \"CASE\",\n"
              + "    \"distributions\": [\n"
              + "        {\n"
              + "            \"orderId\": \"72debb0e-4fce-4477-8f5b-4fa2aee9e80c\",\n"
              + "            \"allocQty\": 1,\n"
              + "            \"qtyUom\": \"EA\",\n"
              + "            \"item\": {\n"
              + "                \"itemNbr\": 658232698,\n"
              + "                \"itemUpc\": \"0238408293492\",\n"
              + "                \"vnpk\": 2,\n"
              + "                \"whpk\": 2,\n"
              + "                \"itemdept\": \"\",\n"
              + "                \"baseDivisionCode\": \"WM\",\n"
              + "                \"financialReportingGroup\": \"US\",\n"
              + "                \"reportingGroup\": \"US\",\n"
              + "                \"aisle\": \"12\",\n"
              + "                \"zone\": \"03\",\n"
              + "                \"storeZone\": \"03\",\n"
              + "                \"dcZone\": \"03\",\n"
              + "                \"pickBatch\": \"281\",\n"
              + "                \"printBatch\": \"281\",\n"
              + "                \"storeAlignment\": \"CONVENTIONAL\",\n"
              + "                 \"packType\": \"CP\",\n"
              + "                 \"itemHandlingCode\": \"V\"\n"
              + "            }\n"
              + "        }\n"
              + "    ],\n"
              + "    \"finalDestination\": {\n"
              + "        \"countryCode\": \"US\",\n"
              + "        \"buNumber\": \"06030\"\n"
              + "    },\n"
              + "    \"outboundChannelMethod\": \"DA\",\n"
              + "    \"ctrReusable\": false,\n"
              + "    \"fulfillmentMethod\": \"VOICE_PUT\"\n"
              + "}"
              + "}";
      LabelDataAllocationDTO instructionDownloadContainerDTO1 =
          new ObjectMapper().readValue(allocationPayload, LabelDataAllocationDTO.class);
      labelData1.setAllocation(instructionDownloadContainerDTO1);
      labelData2.setAllocation(instructionDownloadContainerDTO1);
      labelData1.setQuantityUOM("EA");
      labelData1.setStatus(LabelInstructionStatus.AVAILABLE.name());
      labelData1.setOrderQuantity(1);
      labelData1.setQuantity(1);
      labelData1.setTrackingId("002560132679202860");
      labelData2.setQuantityUOM("EA");
      labelData2.setStatus(LabelInstructionStatus.AVAILABLE.name());
      labelData2.setOrderQuantity(1);
      labelData1.setQuantity(1);
      labelData2.setTrackingId("002560132679202861");
      labelDataList.add(labelData1);
      labelDataList.add(labelData2);
      return labelDataList;

    } catch (Exception ex) {
      return Collections.emptyList();
    }
  }

  private List<com.walmart.move.nim.receiving.core.entity.LabelData>
      getMockLabelData_DaCasePackAutomationSlotting() {
    return null;
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void receiveContainersWithQuantityReceivingNotAllowedBreakPackConveyPicks()
      throws IOException, ExecutionException, InterruptedException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("BM");
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_SCAN_TO_PRINT_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");
    rdcDaService.receiveContainers(
        deliveryDocuments.get(0), httpHeaders, instructionRequest, 2, receiveInstructionRequest);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void receiveContainersWithQuantityReceivingNotAllowedBreakPackNonConveyable()
      throws IOException, ExecutionException, InterruptedException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode(RdcConstants.DA_BREAK_PACK_NON_CONVEYABLE_ITEM_HANDLING_CODE);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_SCAN_TO_PRINT_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");
    rdcDaService.receiveContainers(
        deliveryDocuments.get(0), httpHeaders, instructionRequest, 2, receiveInstructionRequest);
  }

  @Test
  public void testReceiveInstruction_DA_HappyPath_ForAtlasItem_BreakPackNonConRtsPutAtlasItem()
      throws IOException, ExecutionException, InterruptedException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(Long.valueOf(1234));
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(121);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("B");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CB");
    List<String> nonConveyableHandlingCodes = new ArrayList<>();
    nonConveyableHandlingCodes.add("CB");
    when(rdcManagedConfig.getAtlasDaNonConPackAndHandlingCodes())
        .thenReturn(nonConveyableHandlingCodes);
    List<ReceivedContainer> recievedContainerList = new ArrayList<>();
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setSlotDetails(getMockAutoSlotDetails());
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDAQtyReceiving();

    instructionRequest.setProblemTagId("06001647754402");
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SMART_SLOTTING_ENABLED_ON_DA_ITEMS,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);

    when(rdcReceivingUtils.receiveContainers(
            anyInt(),
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(ReceiveInstructionRequest.class)))
        .thenReturn(recievedContainerList);

    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);
    when(containerPersisterService.findAllItemByTrackingId(anyList())).thenReturn(null);
    when(containerService.getContainerListByTrackingIdList(anyList())).thenReturn(null);
    when(rdcContainerUtils.buildContainerItem(
            anyString(), any(DeliveryDocument.class), anyInt(), nullable(String.class)))
        .thenReturn(Arrays.asList(new ContainerItem()));
    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null)))
        .thenReturn(new Container());
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(rdcLpnUtils.getLPNs(anyInt(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList("a311121121"));

    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            anyString(), anyLong(), anyString(), anyInt(), anyInt(), anyString()))
        .thenReturn(getMockLabelDataSym());
    SlottingPalletResponse slottingPalletResponse = mock(SlottingPalletResponse.class);
    if (!slottingPalletResponse.getLocations().isEmpty()) {
      slottingPalletResponse.getLocations().get(0).setLocation("Location1");
    }
    when(slottingDivertLocations.getLocation()).thenReturn("Location1");
    List<SlottingDivertLocations> locations = new ArrayList<>();
    locations.add(slottingDivertLocations);
    when(slottingPalletResponse.getLocations()).thenReturn(locations);
    when(rdcSlottingUtils.receiveContainers(
            receiveInstructionRequest, "a311121121", httpHeaders, null))
        .thenReturn(slottingPalletResponse);
    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    doNothing()
        .when(rdcReceivingUtils)
        .publishInstruction(
            any(Instruction.class),
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean());
    Set labelTrackingIdsMock = mock(Set.class);
    when(printJobService.createPrintJob(anyLong(), any(), eq(labelTrackingIdsMock), any()))
        .thenReturn(printJob);
    List<ReceivedContainer> mockReceivedContainers = mock(List.class);
    when(rdcReceivingUtils.getLabelFormatForPallet(any(DeliveryDocumentLine.class)))
        .thenReturn(LabelFormat.ATLAS_RDC_PALLET);
    when(mockReceivedContainers.stream()).thenReturn(Stream.empty());
    List<ReceivedContainer> receivedContainers = mock(List.class);
    ReceivedContainer receivedContainer = mock(ReceivedContainer.class);
    when(receivedContainers.get(0)).thenReturn(receivedContainer);
    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            getMockInstructionRequest(receiveInstructionRequest, httpHeaders),
            5,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(2))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void receiveContainersWithEligibleItemPackHandlingCode()
      throws IOException, ExecutionException, InterruptedException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode(RdcConstants.PALLET_RECEIVING_HANDLING_CODE);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode(RdcConstants.CASE_PACK_TYPE_CODE);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setStoreNumber(123);
    receiveInstructionRequest.setSlotDetails(new SlotDetails());
    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_SCAN_TO_PRINT_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");
    rdcDaService.receiveContainers(
        deliveryDocuments.get(0), httpHeaders, instructionRequest, 2, receiveInstructionRequest);
  }

  @Test
  public void testReceiveInstruction_CasePack_NonConHanlingCodeAsL_LabelsAvailableForNonCon()
      throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    List<String> lpns =
        Arrays.asList(
            "a47263319186056300", "a47263319186056301", "a47263319186056302", "a47263319186056303");
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(rdcManagedConfig.getAtlasDaNonConInterchangeableHandlingCodes())
        .thenReturn(Arrays.asList("CV", "CN"));
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("V");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CV");
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);

    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_RTS_PUT_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.FALSE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_LABEL_FORMAT_VALIDATION_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcManagedConfig.getDaAtlasLabelTypeValidationsEligiblePackHandlingCodes())
        .thenReturn(VALID_PACKTYPE_HANDLING_CODE_COMBINATIONS_LABEL_FORMAT);
    when(lpnCacheService.getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class))).thenReturn(lpns);
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(Collections.singletonList(getMockLabelDataEmptyChildContainer().get(0)));

    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean()))
        .thenReturn(Arrays.asList(new Receipt()));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_WORK_STATION_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            1,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(1))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    verify(rdcReceivingUtils, times(1))
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    verify(labelDataService, times(1))
        .fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode());

    verify(receiptService, times(1))
        .buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean());
  }

  @Test
  public void testReceiveInstruction_CasePack_NonConHanlingCodeAsN_LabelsAvailableForHandlingCodeN()
      throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    List<String> lpns =
        Arrays.asList(
            "a47263319186056300", "a47263319186056301", "a47263319186056302", "a47263319186056303");
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(rdcManagedConfig.getAtlasDaNonConInterchangeableHandlingCodes())
        .thenReturn(Arrays.asList("CV", "CN"));
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("N");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CN");
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);

    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_RTS_PUT_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.FALSE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_LABEL_FORMAT_VALIDATION_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcManagedConfig.getDaAtlasLabelTypeValidationsEligiblePackHandlingCodes())
        .thenReturn(VALID_PACKTYPE_HANDLING_CODE_COMBINATIONS_LABEL_FORMAT);
    when(lpnCacheService.getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class))).thenReturn(lpns);
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(Collections.singletonList(getMockLabelDataEmptyChildContainer().get(0)));

    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean()))
        .thenReturn(Arrays.asList(new Receipt()));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_WORK_STATION_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            1,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(1))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    verify(rdcReceivingUtils, times(1))
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    verify(labelDataService, times(1))
        .fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode());

    verify(receiptService, times(1))
        .buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean());
  }

  @Test
  public void
      testReceiveInstruction_CasePack_NonConHanlingCodeAsN_LabelsAvailableForSameHandlingCode()
          throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    List<String> lpns =
        Arrays.asList(
            "a47263319186056300", "a47263319186056301", "a47263319186056302", "a47263319186056303");
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(rdcManagedConfig.getAtlasDaNonConInterchangeableHandlingCodes())
        .thenReturn(Arrays.asList("CV", "CN"));
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("N");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CN");
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);

    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_RTS_PUT_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.FALSE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_LABEL_FORMAT_VALIDATION_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcManagedConfig.getDaAtlasLabelTypeValidationsEligiblePackHandlingCodes())
        .thenReturn(VALID_PACKTYPE_HANDLING_CODE_COMBINATIONS_LABEL_FORMAT);
    when(lpnCacheService.getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class))).thenReturn(lpns);
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(
            Collections.singletonList(getMockLabelData_ValidateLabelForChildContainers().get(0)));

    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean()))
        .thenReturn(Arrays.asList(new Receipt()));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_WORK_STATION_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            1,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(3))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcContainerUtils, times(3))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    verify(rdcReceivingUtils, times(1))
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    verify(labelDataService, times(1))
        .fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode());

    verify(receiptService, times(1))
        .buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean());
  }

  @Test
  public void
      testReceiveInstruction_CasePack_NonConHanlingCodeAsV_ForAtlasItem_NonConHandlingCodesFromRdcManagedConfig()
          throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    List<String> lpns =
        Arrays.asList(
            "a47263319186056300", "a47263319186056301", "a47263319186056302", "a47263319186056303");
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("V");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CV");
    when(rdcManagedConfig.getAtlasNonConveyableHandlingCodesForDaSlotting())
        .thenReturn(Arrays.asList("N", "L", "V"));
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);

    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_RTS_PUT_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.FALSE);
    when(lpnCacheService.getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class))).thenReturn(lpns);
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(Collections.singletonList(getMockLabelDataEmptyChildContainer().get(0)));

    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt()))
        .thenReturn(Arrays.asList(new Receipt()));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_WORK_STATION_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            1,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(1))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    verify(rdcReceivingUtils, times(1))
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    verify(labelDataService, times(1))
        .fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode());

    verify(receiptService, times(1))
        .buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean());
  }

  private List<LabelDownloadEvent> getLabelDownloadEvents() {
    LabelDownloadEventMiscInfo sstkLabelDownloadEventMiscInfo =
        LabelDownloadEventMiscInfo.builder().labelType("SSTK").build();
    List<LabelDownloadEvent> labelDownloadEvents = new ArrayList<>();
    LabelDownloadEvent labelDownloadEvent = new LabelDownloadEvent();
    labelDownloadEvent.setDeliveryNumber(39380405l);
    labelDownloadEvent.setItemNumber(658232698l);
    labelDownloadEvent.setPurchaseReferenceNumber("5030140191");
    LabelDownloadEventMiscInfo labelDownloadEventMiscInfo =
        LabelDownloadEventMiscInfo.builder().labelType("DA").build();
    labelDownloadEvent.setMiscInfo(gson.toJson(labelDownloadEventMiscInfo));
    LabelDownloadEvent labelDownloadEvent2 = new LabelDownloadEvent();
    labelDownloadEvent2.setDeliveryNumber(39380405l);
    labelDownloadEvent2.setItemNumber(658232698l);
    labelDownloadEvent2.setPurchaseReferenceNumber("5030140192");
    labelDownloadEvent2.setMiscInfo(gson.toJson(sstkLabelDownloadEventMiscInfo));
    LabelDownloadEvent labelDownloadEvent3 = new LabelDownloadEvent();
    labelDownloadEvent3.setDeliveryNumber(39380405l);
    labelDownloadEvent3.setItemNumber(658232698l);
    labelDownloadEvent3.setPurchaseReferenceNumber("8458708163");
    labelDownloadEvent3.setMiscInfo(gson.toJson(labelDownloadEventMiscInfo));
    labelDownloadEvents.add(labelDownloadEvent);
    labelDownloadEvents.add(labelDownloadEvent2);
    labelDownloadEvents.add(labelDownloadEvent3);
    return labelDownloadEvents;
  }

  @Test
  public void testReceiveInstruction_BreakPack_NonConHandlingCodeAsV_LabelsAvailableForNonCon()
      throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack();
    List<String> lpns =
        Arrays.asList(
            "147263319186056300", "147263319186056301", "147263319186056302", "147263319186056303");
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(rdcManagedConfig.getDaAtlasLabelTypeValidationsEligiblePackHandlingCodes())
        .thenReturn(Arrays.asList("CV", "CN", "BV", "BN"));
    when(rdcManagedConfig.getAtlasDaNonConInterchangeableHandlingCodes())
        .thenReturn(Arrays.asList("CV", "CN", "BV", "BN"));
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("V");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("B");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("BV");
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);

    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_RTS_PUT_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.FALSE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_LABEL_FORMAT_VALIDATION_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcManagedConfig.getDaAtlasLabelTypeValidationsEligiblePackHandlingCodes())
        .thenReturn(VALID_PACKTYPE_HANDLING_CODE_COMBINATIONS_LABEL_FORMAT);
    when(lpnCacheService.getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class))).thenReturn(lpns);
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(getMockLabelData_BreakPack_NonCon());

    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean()))
        .thenReturn(Arrays.asList(new Receipt()));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_WORK_STATION_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            1,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(2))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcContainerUtils, times(2))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    verify(rdcReceivingUtils, times(1))
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    verify(labelDataService, times(1))
        .fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode());

    verify(receiptService, times(1))
        .buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean());
  }

  @Test
  public void
      testReceiveInstruction_BreakPack_NonConHanlingCodeAsN_LabelsAvailableForHandlingCodeN()
          throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack();
    List<String> lpns =
        Arrays.asList(
            "147263319186056300", "147263319186056301", "147263319186056302", "147263319186056303");
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(rdcManagedConfig.getDaAtlasLabelTypeValidationsEligiblePackHandlingCodes())
        .thenReturn(Arrays.asList("CV", "CN", "BV", "BN"));
    when(rdcManagedConfig.getAtlasDaNonConInterchangeableHandlingCodes())
        .thenReturn(Arrays.asList("CV", "CN", "BV", "BN"));
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("N");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("B");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("BN");
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);

    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_RTS_PUT_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.FALSE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_LABEL_FORMAT_VALIDATION_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcManagedConfig.getDaAtlasLabelTypeValidationsEligiblePackHandlingCodes())
        .thenReturn(VALID_PACKTYPE_HANDLING_CODE_COMBINATIONS_LABEL_FORMAT);
    when(lpnCacheService.getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class))).thenReturn(lpns);
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(getMockLabelData_BreakPack_NonCon());

    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean()))
        .thenReturn(Arrays.asList(new Receipt()));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_WORK_STATION_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            1,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(2))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcContainerUtils, times(2))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    verify(rdcReceivingUtils, times(1))
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    verify(labelDataService, times(1))
        .fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode());

    verify(receiptService, times(1))
        .buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void
      testReceiveInstruction_BreakPack_NonConHanlingCodeAsN_LabelsAvailableForDifferentHandlingCodeNotInRdcManagedConfig()
          throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack();
    List<String> lpns =
        Arrays.asList(
            "147263319186056300", "147263319186056301", "147263319186056302", "147263319186056303");
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(rdcManagedConfig.getAtlasDaNonConInterchangeableHandlingCodes())
        .thenReturn(Arrays.asList("CV", "CN", "BN"));
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("V");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("B");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("BV");
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);

    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_RTS_PUT_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.FALSE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_LABEL_FORMAT_VALIDATION_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcManagedConfig.getDaAtlasLabelTypeValidationsEligiblePackHandlingCodes())
        .thenReturn(VALID_PACKTYPE_HANDLING_CODE_COMBINATIONS_LABEL_FORMAT);
    when(lpnCacheService.getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class))).thenReturn(lpns);
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(
            Collections.singletonList(
                getMockLabelData_BreakPack_NonCon_ValidateLabelForChildContainers().get(0)));

    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean()))
        .thenReturn(Arrays.asList(new Receipt()));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_WORK_STATION_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            1,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(3))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcContainerUtils, times(3))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    verify(rdcReceivingUtils, times(1))
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    verify(labelDataService, times(1))
        .fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode());

    verify(receiptService, times(1))
        .buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean());
  }

  @Test
  public void testReceiveInstruction_BreakPack_ConHanlingCodeAsBC_LabelsAvailableForCon()
      throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    List<String> lpns =
        Arrays.asList(
            "a47263319186056300", "a47263319186056301", "a47263319186056302", "a47263319186056303");
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(rdcManagedConfig.getAtlasDaBreakPackInterchangeableHandlingCodes())
        .thenReturn(Arrays.asList("BC", "BJ", "BI"));
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("B");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("BC");
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);

    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_RTS_PUT_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.FALSE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_LABEL_FORMAT_VALIDATION_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcManagedConfig.getDaAtlasLabelTypeValidationsEligiblePackHandlingCodes())
        .thenReturn(VALID_PACKTYPE_HANDLING_CODE_COMBINATIONS_LABEL_FORMAT);
    when(lpnCacheService.getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class))).thenReturn(lpns);
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    com.walmart.move.nim.receiving.core.entity.LabelData labelData =
        getMockLabelDataEmptyChildContainer().get(0);
    InstructionDownloadItemDTO instructionDownloadItemDTO =
        labelData.getAllocation().getContainer().getDistributions().get(0).getItem();
    instructionDownloadItemDTO.setPackType("BP");
    instructionDownloadItemDTO.setItemHandlingCode("J");

    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(Collections.singletonList(labelData));

    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean()))
        .thenReturn(Arrays.asList(new Receipt()));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_WORK_STATION_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            1,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(1))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    verify(rdcReceivingUtils, times(1))
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    verify(labelDataService, times(1))
        .fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode());

    verify(receiptService, times(1))
        .buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean());
    verify(rdcManagedConfig, times(1)).getAtlasDaBreakPackInterchangeableHandlingCodes();
  }

  @Test
  public void testReceiveInstruction_BreakPack_ConHanlingCodeAsBJ_LabelsAvailableForCon()
      throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    List<String> lpns =
        Arrays.asList(
            "a47263319186056300", "a47263319186056301", "a47263319186056302", "a47263319186056303");
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(rdcManagedConfig.getAtlasDaBreakPackInterchangeableHandlingCodes())
        .thenReturn(Arrays.asList("BC", "BJ", "BI"));
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("J");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("B");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("BJ");
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);

    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_RTS_PUT_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.FALSE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_LABEL_FORMAT_VALIDATION_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcManagedConfig.getDaAtlasLabelTypeValidationsEligiblePackHandlingCodes())
        .thenReturn(VALID_PACKTYPE_HANDLING_CODE_COMBINATIONS_LABEL_FORMAT);
    when(lpnCacheService.getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class))).thenReturn(lpns);
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    com.walmart.move.nim.receiving.core.entity.LabelData labelData =
        getMockLabelDataEmptyChildContainer().get(0);
    InstructionDownloadItemDTO instructionDownloadItemDTO =
        labelData.getAllocation().getContainer().getDistributions().get(0).getItem();
    instructionDownloadItemDTO.setPackType("BP");
    instructionDownloadItemDTO.setItemHandlingCode("C");

    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(Collections.singletonList(labelData));

    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean()))
        .thenReturn(Arrays.asList(new Receipt()));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_WORK_STATION_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            1,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(1))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    verify(rdcReceivingUtils, times(1))
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    verify(labelDataService, times(1))
        .fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode());

    verify(receiptService, times(1))
        .buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean());
  }

  @Test
  public void testReceiveInstruction_BreakPack_ConHanlingCodeAsBI_LabelsAvailableForCon()
      throws IOException, ReceivingException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    printJob.setInstructionId(1234L);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    List<String> lpns =
        Arrays.asList(
            "a47263319186056300", "a47263319186056301", "a47263319186056302", "a47263319186056303");
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getDCTimeZone(any(Integer.class))).thenReturn("US/Central");
    when(rdcManagedConfig.getAtlasDaBreakPackInterchangeableHandlingCodes())
        .thenReturn(Arrays.asList("BC", "BJ", "BI"));
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(1);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("C");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("B");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("BC");
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);

    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());

    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class)))
        .thenReturn(new Container());

    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_RTS_PUT_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.FALSE);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_LABEL_FORMAT_VALIDATION_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcManagedConfig.getDaAtlasLabelTypeValidationsEligiblePackHandlingCodes())
        .thenReturn(VALID_PACKTYPE_HANDLING_CODE_COMBINATIONS_LABEL_FORMAT);
    when(lpnCacheService.getLPNSBasedOnTenant(anyInt(), any(HttpHeaders.class))).thenReturn(lpns);
    doNothing()
        .when(rdcReceivingUtils)
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    com.walmart.move.nim.receiving.core.entity.LabelData labelData =
        getMockLabelDataEmptyChildContainer().get(0);
    InstructionDownloadItemDTO instructionDownloadItemDTO =
        labelData.getAllocation().getContainer().getDistributions().get(0).getItem();
    instructionDownloadItemDTO.setPackType("BP");
    instructionDownloadItemDTO.setItemHandlingCode("I");

    when(labelDataService.fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode()))
        .thenReturn(Collections.singletonList(labelData));

    when(receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean()))
        .thenReturn(Arrays.asList(new Receipt()));

    ReceiveInstructionRequest receiveInstructionRequest = getMockReceiveInstructionRequest();
    receiveInstructionRequest.setDeliveryDocuments(deliveryDocuments);
    receiveInstructionRequest.setPalletQuantities(getMockPalletQuantities());

    InstructionRequest instructionRequest =
        getMockInstructionRequest(receiveInstructionRequest, httpHeaders);
    instructionRequest.setFeatureType(RdcConstants.DA_WORK_STATION_FEATURE_TYPE);
    instructionRequest.setProblemTagId("06001647754402");

    InstructionResponse instructionResponse =
        rdcDaService.receiveContainers(
            deliveryDocuments.get(0),
            httpHeaders,
            instructionRequest,
            1,
            receiveInstructionRequest);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertTrue(instructionResponse.getDeliveryDocuments().size() > 0);
    assertNotNull(response.getPrintJob());

    verify(rdcContainerUtils, times(1))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            nullable(ReceiveInstructionRequest.class));
    verify(printJobService, times(1)).createPrintJob(anyLong(), anyLong(), anySet(), anyString());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            any(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    verify(rdcReceivingUtils, times(1))
        .postReceivingUpdates(
            any(Instruction.class),
            any(DeliveryDocument.class),
            anyInt(),
            any(HttpHeaders.class),
            anyBoolean(),
            anyList(),
            anyBoolean());

    verify(labelDataService, times(1))
        .fetchLabelDataByPoAndItemNumber(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getItemNbr(),
            LabelInstructionStatus.AVAILABLE.name(),
            2,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode());

    verify(receiptService, times(1))
        .buildReceiptsFromInstructionWithOsdrMasterUpdate(
            any(DeliveryDocument.class), anyString(), any(), anyString(), anyInt(), anyBoolean());
    verify(rdcManagedConfig, times(1)).getAtlasDaBreakPackInterchangeableHandlingCodes();
  }

  @Test
  public void
      testReceiveDAInstructionForBreakPackConveyPicks_ReceiveContainers_enrichSyncPrintingRequest()
          throws IOException, ReceivingException, ExecutionException, InterruptedException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    InstructionResponse mockInstructionResponse = new InstructionResponseImplNew();
    Instruction instruction = new Instruction();
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDAReceivingFeatureType();
    instruction.setInstructionCode(
        RdcInstructionType.DA_RECEIVING_PACK_TYPE_VALIDATION.getInstructionCode());
    instruction.setInstructionMsg(
        RdcInstructionType.DA_RECEIVING_PACK_TYPE_VALIDATION.getInstructionMsg());
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("B");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("M");
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    instructionRequest.setSyncPrintEnabled(true);
    instructionRequest.setFeatureType(DA_WORK_STATION_FEATURE_TYPE);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode(RdcConstants.DA_BREAK_PACK_CONVEY_PICKS_ITEM_HANDLING_CODE);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setBreakPackValidationRequired(Boolean.TRUE);
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    mockInstructionResponse.setInstruction(instruction);
    when(rdcReceivingUtils.checkIfMaxOverageReceived(
            any(DeliveryDocument.class), anyLong(), anyInt()))
        .thenReturn(Boolean.FALSE);
    doNothing()
        .when(rdcReceivingUtils)
        .isPoAndPoLineInReceivableStatus(any(DeliveryDocumentLine.class));
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(mockInstructionResponse);
    when(rdcReceivingUtils.validateBreakPackItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class)))
        .thenReturn(mockInstructionResponse);
    when(rdcReceivingUtils.validateRtsPutItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class),
            any(HttpHeaders.class)))
        .thenReturn(new InstructionResponseImplNew());
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(rdcReceivingUtils.checkIfNonConveyableItem(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class)))
        .thenReturn(mockInstructionResponse);
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            nullable(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(2);
    when(nimRdsService.getReceiveContainersRequestBodyForDAReceiving(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            anyInt(),
            any(ReceiveInstructionRequest.class)))
        .thenReturn(getMockRdsContainerRequest());
    when(containerPersisterService.findAllItemByTrackingId(anyList())).thenReturn(null);
    when(containerService.getContainerListByTrackingIdList(anyList())).thenReturn(null);
    when(rdcReceivingUtils.receiveContainers(
            anyInt(),
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            eq(null)))
        .thenReturn(MockRdsResponse.getRdsResponseForDABreakConveyPacks().getReceived());
    when(tenantSpecificConfigReader.getDCTimeZone(anyInt())).thenReturn("US/Eastern");
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);
    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());
    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            any(Container.class),
            eq(null)))
        .thenReturn(new Container());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    doNothing().when(rdcReceivingUtils).overrideItemProperties(any(DeliveryDocument.class));
    InstructionResponse instructionResponse =
        rdcDaService.createInstructionForDACaseReceiving(instructionRequest, 10L, httpHeaders);

    assertNotNull(instructionResponse);
    assertEquals(instructionResponse.getDeliveryDocuments().size(), 1);
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.DA_WORK_STATION_SCAN_TO_PRINT_RECEIVING.getInstructionCode());

    verify(rdcReceivingUtils, times(1))
        .checkIfMaxOverageReceived(any(DeliveryDocument.class), anyLong(), anyInt());
    verify(rdcInstructionUtils, times(0))
        .getOverageAlertInstruction(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(rdcReceivingUtils, times(1))
        .isPoAndPoLineInReceivableStatus(any(DeliveryDocumentLine.class));
    verify(rdcReceivingUtils, times(1))
        .validateBreakPackItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class));
    verify(rdcReceivingUtils, times(1))
        .checkIfNonConveyableItem(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            nullable(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcContainerUtils, times(2))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null));
    verify(rdcContainerUtils, times(2))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(
            anyList(), anyList(), anyList(), anyList(), nullable(List.class));
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(containerPersisterService, times(1)).findAllItemByTrackingId(anyList());
    verify(containerService, times(1)).getContainerListByTrackingIdList(anyList());
  }

  @Test
  public void testReceiveDAInstructionForBreakPackConveyPicks_enrichSyncPrintingRequest()
      throws IOException, ReceivingException, ExecutionException, InterruptedException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    InstructionResponse mockInstructionResponse = new InstructionResponseImplNew();
    Instruction instruction = new Instruction();
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDAReceivingFeatureType();
    instructionRequest.setSyncPrintEnabled(true);
    instruction.setInstructionCode(
        RdcInstructionType.DA_RECEIVING_PACK_TYPE_VALIDATION.getInstructionCode());
    instruction.setInstructionMsg(
        RdcInstructionType.DA_RECEIVING_PACK_TYPE_VALIDATION.getInstructionMsg());
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("B");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("M");
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    instructionRequest.setFeatureType(DA_WORK_STATION_FEATURE_TYPE);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode(RdcConstants.DA_BREAK_PACK_CONVEY_PICKS_ITEM_HANDLING_CODE);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setBreakPackValidationRequired(Boolean.TRUE);
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    mockInstructionResponse.setInstruction(instruction);
    when(rdcReceivingUtils.checkIfMaxOverageReceived(
            any(DeliveryDocument.class), anyLong(), anyInt()))
        .thenReturn(Boolean.FALSE);
    doNothing()
        .when(rdcReceivingUtils)
        .isPoAndPoLineInReceivableStatus(any(DeliveryDocumentLine.class));
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(mockInstructionResponse);
    when(rdcReceivingUtils.validateBreakPackItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class)))
        .thenReturn(mockInstructionResponse);
    when(rdcReceivingUtils.validateRtsPutItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class),
            any(HttpHeaders.class)))
        .thenReturn(new InstructionResponseImplNew());
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(rdcReceivingUtils.checkIfNonConveyableItem(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class)))
        .thenReturn(mockInstructionResponse);
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            nullable(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(2);
    when(nimRdsService.getReceiveContainersRequestBodyForDAReceiving(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            anyInt(),
            any(ReceiveInstructionRequest.class)))
        .thenReturn(getMockRdsContainerRequest());
    when(containerPersisterService.findAllItemByTrackingId(anyList())).thenReturn(null);
    when(containerService.getContainerListByTrackingIdList(anyList())).thenReturn(null);
    when(rdcReceivingUtils.receiveContainers(
            anyInt(),
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            eq(null)))
        .thenReturn(MockRdsResponse.getRdsResponseForDABreakConveyPacks().getReceived());
    when(tenantSpecificConfigReader.getDCTimeZone(anyInt())).thenReturn("US/Eastern");
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);
    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());
    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            any(Container.class),
            eq(null)))
        .thenReturn(new Container());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    doNothing().when(rdcReceivingUtils).overrideItemProperties(any(DeliveryDocument.class));
    when(rdcManagedConfig.getAtlasDaPrintingAsyncBlockedHandlingCodes())
        .thenReturn(Arrays.asList("BN"));
    InstructionResponse instructionResponse =
        rdcDaService.createInstructionForDACaseReceiving(instructionRequest, 10L, httpHeaders);
    assertNotNull(instructionResponse);
    assertEquals(instructionResponse.getDeliveryDocuments().size(), 1);
    InstructionResponseImplNew instructionResponseImplNew =
        (InstructionResponseImplNew) instructionResponse;
    assertNotNull(instructionResponseImplNew.getPrintJob());
    assertTrue(instructionResponseImplNew.getPrintJob().containsKey("syncPrintRequired"));
    Boolean syncPrintRequired =
        Boolean.valueOf(
            instructionResponseImplNew.getPrintJob().get("syncPrintRequired").toString());
    assertTrue(syncPrintRequired);
    verify(rdcManagedConfig, times(2)).getAtlasDaPrintingAsyncBlockedHandlingCodes();
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.DA_WORK_STATION_SCAN_TO_PRINT_RECEIVING.getInstructionCode());
    verify(rdcReceivingUtils, times(1))
        .checkIfMaxOverageReceived(any(DeliveryDocument.class), anyLong(), anyInt());
    verify(rdcInstructionUtils, times(0))
        .getOverageAlertInstruction(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(rdcReceivingUtils, times(1))
        .isPoAndPoLineInReceivableStatus(any(DeliveryDocumentLine.class));
    verify(rdcReceivingUtils, times(1))
        .validateBreakPackItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class));
    verify(rdcReceivingUtils, times(1))
        .checkIfNonConveyableItem(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            nullable(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcContainerUtils, times(2))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null));
    verify(rdcContainerUtils, times(2))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(
            anyList(), anyList(), anyList(), anyList(), nullable(List.class));
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(containerPersisterService, times(1)).findAllItemByTrackingId(anyList());
    verify(containerService, times(1)).getContainerListByTrackingIdList(anyList());
  }

  @Test
  public void testReceiveDAInstructionForBreakPackConveyPicks_enrichSyncPrintingNotRequired()
      throws IOException, ReceivingException, ExecutionException, InterruptedException {
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);
    InstructionResponse mockInstructionResponse = new InstructionResponseImplNew();
    Instruction instruction = new Instruction();
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    InstructionRequest instructionRequest =
        MockInstructionRequest.getInstructionRequestWithDAReceivingFeatureType();
    instructionRequest.setSyncPrintEnabled(true);
    instruction.setInstructionCode(
        RdcInstructionType.DA_RECEIVING_PACK_TYPE_VALIDATION.getInstructionCode());
    instruction.setInstructionMsg(
        RdcInstructionType.DA_RECEIVING_PACK_TYPE_VALIDATION.getInstructionMsg());
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setPackTypeCode("B");
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setHandlingCode("M");
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    instructionRequest.setFeatureType(DA_WORK_STATION_FEATURE_TYPE);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode(RdcConstants.DA_BREAK_PACK_CONVEY_PICKS_ITEM_HANDLING_CODE);
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setBreakPackValidationRequired(Boolean.TRUE);
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setTotalReceivedQty(10);
    mockInstructionResponse.setInstruction(instruction);
    when(rdcReceivingUtils.checkIfMaxOverageReceived(
            any(DeliveryDocument.class), anyLong(), anyInt()))
        .thenReturn(Boolean.FALSE);
    doNothing()
        .when(rdcReceivingUtils)
        .isPoAndPoLineInReceivableStatus(any(DeliveryDocumentLine.class));
    when(rdcReceivingUtils.checkIfVendorComplianceRequired(
            any(InstructionRequest.class),
            any(DeliveryDocument.class),
            any(InstructionResponse.class)))
        .thenReturn(mockInstructionResponse);
    when(rdcReceivingUtils.validateBreakPackItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class)))
        .thenReturn(mockInstructionResponse);
    when(rdcReceivingUtils.validateRtsPutItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class),
            any(HttpHeaders.class)))
        .thenReturn(new InstructionResponseImplNew());
    when(rdcReceivingUtils.populateInstructionFields(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            any(DeliveryDocument.class)))
        .thenReturn(getMockDAInstruction());
    when(rdcReceivingUtils.checkIfNonConveyableItem(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class)))
        .thenReturn(mockInstructionResponse);
    when(rdcReceivingUtils.getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            nullable(ReceiveInstructionRequest.class),
            anyBoolean()))
        .thenReturn(2);
    when(nimRdsService.getReceiveContainersRequestBodyForDAReceiving(
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            anyInt(),
            any(ReceiveInstructionRequest.class)))
        .thenReturn(getMockRdsContainerRequest());
    when(containerPersisterService.findAllItemByTrackingId(anyList())).thenReturn(null);
    when(containerService.getContainerListByTrackingIdList(anyList())).thenReturn(null);
    when(rdcReceivingUtils.receiveContainers(
            anyInt(),
            any(InstructionRequest.class),
            any(DeliveryDocumentLine.class),
            any(HttpHeaders.class),
            eq(null)))
        .thenReturn(MockRdsResponse.getRdsResponseForDABreakConveyPacks().getReceived());
    when(tenantSpecificConfigReader.getDCTimeZone(anyInt())).thenReturn("US/Eastern");
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);
    when(rdcContainerUtils.buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class)))
        .thenReturn(new ContainerItem());
    when(rdcContainerUtils.buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            any(Container.class),
            eq(null)))
        .thenReturn(new Container());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDAInstruction());
    doNothing().when(rdcReceivingUtils).overrideItemProperties(any(DeliveryDocument.class));
    when(rdcManagedConfig.getAtlasDaPrintingAsyncBlockedHandlingCodes())
        .thenReturn(Arrays.asList("BM"));
    InstructionResponse instructionResponse =
        rdcDaService.createInstructionForDACaseReceiving(instructionRequest, 10L, httpHeaders);
    assertNotNull(instructionResponse);
    assertEquals(instructionResponse.getDeliveryDocuments().size(), 1);
    InstructionResponseImplNew instructionResponseImplNew =
        (InstructionResponseImplNew) instructionResponse;
    assertNotNull(instructionResponseImplNew.getPrintJob());
    assertTrue(instructionResponseImplNew.getPrintJob().containsKey("syncPrintRequired"));
    Boolean syncPrintRequired =
        Boolean.valueOf(
            instructionResponseImplNew.getPrintJob().get("syncPrintRequired").toString());
    assertFalse(syncPrintRequired);
    verify(rdcManagedConfig, times(2)).getAtlasDaPrintingAsyncBlockedHandlingCodes();
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.DA_WORK_STATION_SCAN_TO_PRINT_RECEIVING.getInstructionCode());
    verify(rdcReceivingUtils, times(1))
        .checkIfMaxOverageReceived(any(DeliveryDocument.class), anyLong(), anyInt());
    verify(rdcInstructionUtils, times(0))
        .getOverageAlertInstruction(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(rdcReceivingUtils, times(1))
        .isPoAndPoLineInReceivableStatus(any(DeliveryDocumentLine.class));
    verify(rdcReceivingUtils, times(1))
        .validateBreakPackItems(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class));
    verify(rdcReceivingUtils, times(1))
        .checkIfNonConveyableItem(
            any(DeliveryDocument.class),
            any(InstructionRequest.class),
            any(InstructionResponse.class));
    verify(rdcReceivingUtils, times(1))
        .getContainersCountToBeReceived(
            any(DeliveryDocumentLine.class),
            anyInt(),
            nullable(ReceiveInstructionRequest.class),
            anyBoolean());
    verify(rdcContainerUtils, times(2))
        .buildContainer(
            anyString(),
            anyLong(),
            anyLong(),
            anyString(),
            any(DeliveryDocument.class),
            anyString(),
            any(ReceivedContainer.class),
            nullable(Container.class),
            eq(null));
    verify(rdcContainerUtils, times(2))
        .buildContainerItemDetails(
            anyString(),
            any(DeliveryDocument.class),
            anyInt(),
            nullable(ContainerItem.class),
            nullable(String.class),
            nullable(List.class),
            nullable(String.class));
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(
            anyList(), anyList(), anyList(), anyList(), nullable(List.class));
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(containerPersisterService, times(1)).findAllItemByTrackingId(anyList());
    verify(containerService, times(1)).getContainerListByTrackingIdList(anyList());
  }
}
