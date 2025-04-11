package com.walmart.move.nim.receiving.rdc.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_DSDC_SSCC_PACKS_AVAILABLE_IN_GDM;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.assertNull;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.nimrds.NimRDSRestApiClient;
import com.walmart.move.nim.receiving.core.client.nimrds.model.DsdcReceiveRequest;
import com.walmart.move.nim.receiving.core.client.nimrds.model.DsdcReceiveResponse;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.PrintJob;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.printlabel.LabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;
import com.walmart.move.nim.receiving.core.service.ContainerItemService;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.core.service.PrintJobService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.label.LabelConstants;
import com.walmart.move.nim.receiving.rdc.label.LabelFormat;
import com.walmart.move.nim.receiving.rdc.mock.data.MockInstructionRequest;
import com.walmart.move.nim.receiving.rdc.model.wft.RdcInstructionType;
import com.walmart.move.nim.receiving.rdc.model.wft.WFTInstruction;
import com.walmart.move.nim.receiving.rdc.utils.RdcContainerUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcInstructionUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcReceivingUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RdcDsdcServiceTest {

  @Mock private RdcInstructionUtils rdcInstructionUtils;
  @Mock private RdcReceivingUtils rdcReceivingUtils;
  @Mock private NimRDSRestApiClient nimRDSRestApiClient;
  @Mock private NimRdsService nimRdsService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private RdcContainerUtils rdcContainerUtils;
  @Mock private PrintJobService printJobService;
  @Mock private InstructionPersisterService instructionPersisterService;
  @Mock private ContainerService containerService;
  @Mock private ContainerItemService containerItemService;
  @InjectMocks private RdcDsdcService rdcDsdcService;
  private HttpHeaders httpHeaders;
  private final String facilityNum = "32818";
  private final String facilityCountryCode = "us";
  private Gson gson;

  @BeforeClass
  public void initMocks() {
    gson = new Gson();
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(rdcDsdcService, "gson", gson);
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
        containerService,
        containerItemService);
  }

  @Test
  public void createInstructionForDSDCReceiving_Success() {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getSSCCInstructionRequestWithWorkStationEnabled();
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);

    when(nimRdsService.getDsdcReceiveContainerRequest(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(getMockDsdcReceiveRequest());
    when(nimRdsService.receiveDsdcContainerInRds(
            any(DsdcReceiveRequest.class), any(HttpHeaders.class)))
        .thenReturn(getMockDsdcReceiveResponseSuccess());
    when(tenantSpecificConfigReader.getDCTimeZone(anyInt())).thenReturn("US/Eastern");
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);
    when(containerService.findByTrackingId(anyString())).thenReturn(null);
    when(containerItemService.findByTrackingId(anyString())).thenReturn(null);
    when(rdcContainerUtils.buildContainerItem(
            any(DsdcReceiveResponse.class), anyString(), anyInt(), nullable(ContainerItem.class)))
        .thenReturn(Collections.singletonList(new ContainerItem()));
    when(rdcContainerUtils.buildContainer(
            any(InstructionRequest.class),
            any(DsdcReceiveResponse.class),
            anyLong(),
            anyString(),
            nullable(Container.class)))
        .thenReturn(new Container());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDsdcInstruction());

    InstructionResponse instructionResponse =
        rdcDsdcService.createInstructionForDSDCReceiving(instructionRequest, httpHeaders);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertEquals(instructionResponse.getDeliveryDocuments().size(), 0);
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.DSDC_RECEIVING.getInstructionCode());

    assertNotNull(response);
    assertEquals(response.getDeliveryDocuments().size(), 0);
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
    assertEquals(
        printLabelRequests
            .stream()
            .filter(
                printRequest -> printRequest.getFormatName().equals(LabelFormat.DSDC.getFormat()))
            .count(),
        1);
    List<LabelData> labelDataList = printLabelRequests.get(0).getData();
    Optional<LabelData> labelSlot =
        labelDataList
            .stream()
            .filter(label -> label.getKey().equals(LabelConstants.LBL_SLOT))
            .findFirst();
    Optional<LabelData> labelStore =
        labelDataList
            .stream()
            .filter(label -> label.getKey().equals(LabelConstants.LBL_STORE))
            .findFirst();
    Optional<LabelData> labelTrackingId =
        labelDataList
            .stream()
            .filter(label -> label.getKey().equals(LabelConstants.LBL_BAR))
            .findFirst();
    assertEquals(labelSlot.get().getValue(), RdcConstants.DA_R8002_SLOT);
    assertEquals(labelStore.get().getValue(), "12345");
    assertEquals(labelTrackingId.get().getValue(), "123456789012345678");

    verify(nimRdsService, times(1))
        .getDsdcReceiveContainerRequest(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(nimRdsService, times(1))
        .receiveDsdcContainerInRds(any(DsdcReceiveRequest.class), any(HttpHeaders.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            any(InstructionRequest.class),
            any(DsdcReceiveResponse.class),
            anyLong(),
            anyString(),
            nullable(Container.class));
    verify(rdcContainerUtils, times(1))
        .buildContainerItem(
            any(DsdcReceiveResponse.class), anyString(), anyInt(), nullable(ContainerItem.class));
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
  }

  @Test
  public void createInstructionForDSDCReceiving_Success_ContainersAlreadyReceivedInAtlas() {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getSSCCInstructionRequestWithWorkStationEnabled();
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);

    List<ContainerItem> containerItems = new ArrayList<>();
    ContainerItem containerItem = new ContainerItem();
    containerItems.add(containerItem);

    when(nimRdsService.getDsdcReceiveContainerRequest(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(getMockDsdcReceiveRequest());
    when(nimRdsService.receiveDsdcContainerInRds(
            any(DsdcReceiveRequest.class), any(HttpHeaders.class)))
        .thenReturn(getMockDsdcReceiveResponseSuccess());
    when(tenantSpecificConfigReader.getDCTimeZone(anyInt())).thenReturn("US/Eastern");
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);
    when(containerService.findByTrackingId(anyString())).thenReturn(new Container());
    when(containerItemService.findByTrackingId(anyString())).thenReturn(containerItems);
    when(rdcContainerUtils.buildContainerItem(
            any(DsdcReceiveResponse.class), anyString(), anyInt(), any(ContainerItem.class)))
        .thenReturn(Collections.singletonList(new ContainerItem()));
    when(rdcContainerUtils.buildContainer(
            any(InstructionRequest.class),
            any(DsdcReceiveResponse.class),
            anyLong(),
            anyString(),
            any(Container.class)))
        .thenReturn(new Container());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDsdcInstruction());

    InstructionResponse instructionResponse =
        rdcDsdcService.createInstructionForDSDCReceiving(instructionRequest, httpHeaders);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertEquals(instructionResponse.getDeliveryDocuments().size(), 0);
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.DSDC_RECEIVING.getInstructionCode());

    assertNotNull(response);
    assertEquals(response.getDeliveryDocuments().size(), 0);
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
    assertEquals(
        printLabelRequests
            .stream()
            .filter(
                printRequest -> printRequest.getFormatName().equals(LabelFormat.DSDC.getFormat()))
            .count(),
        1);
    List<LabelData> labelDataList = printLabelRequests.get(0).getData();
    Optional<LabelData> labelSlot =
        labelDataList
            .stream()
            .filter(label -> label.getKey().equals(LabelConstants.LBL_SLOT))
            .findFirst();
    Optional<LabelData> labelStore =
        labelDataList
            .stream()
            .filter(label -> label.getKey().equals(LabelConstants.LBL_STORE))
            .findFirst();
    Optional<LabelData> labelTrackingId =
        labelDataList
            .stream()
            .filter(label -> label.getKey().equals(LabelConstants.LBL_BAR))
            .findFirst();
    assertEquals(labelSlot.get().getValue(), RdcConstants.DA_R8002_SLOT);
    assertEquals(labelStore.get().getValue(), "12345");
    assertEquals(labelTrackingId.get().getValue(), "123456789012345678");

    verify(nimRdsService, times(1))
        .getDsdcReceiveContainerRequest(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(nimRdsService, times(1))
        .receiveDsdcContainerInRds(any(DsdcReceiveRequest.class), any(HttpHeaders.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            any(InstructionRequest.class),
            any(DsdcReceiveResponse.class),
            anyLong(),
            anyString(),
            any(Container.class));
    verify(rdcContainerUtils, times(1))
        .buildContainerItem(
            any(DsdcReceiveResponse.class), anyString(), anyInt(), any(ContainerItem.class));
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
  }

  @Test
  public void createInstructionForDotComReceiving_Success() {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getSSCCInstructionRequestWithWorkStationEnabled();
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);

    when(nimRdsService.getDsdcReceiveContainerRequest(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(getMockDsdcReceiveRequest());
    when(nimRdsService.receiveDsdcContainerInRds(
            any(DsdcReceiveRequest.class), any(HttpHeaders.class)))
        .thenReturn(getMockDotComReceiveResponseSuccess());
    when(tenantSpecificConfigReader.getDCTimeZone(anyInt())).thenReturn("US/Eastern");
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);
    when(containerService.findByTrackingId(anyString())).thenReturn(null);
    when(containerItemService.findByTrackingId(anyString())).thenReturn(null);
    when(rdcContainerUtils.buildContainerItem(
            any(DsdcReceiveResponse.class), anyString(), anyInt(), nullable(ContainerItem.class)))
        .thenReturn(Collections.singletonList(new ContainerItem()));
    when(rdcContainerUtils.buildContainer(
            any(InstructionRequest.class),
            any(DsdcReceiveResponse.class),
            anyLong(),
            anyString(),
            nullable(Container.class)))
        .thenReturn(new Container());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDsdcInstruction());

    InstructionResponse instructionResponse =
        rdcDsdcService.createInstructionForDSDCReceiving(instructionRequest, httpHeaders);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertEquals(instructionResponse.getDeliveryDocuments().size(), 0);
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.DSDC_RECEIVING.getInstructionCode());

    assertNotNull(response);
    assertEquals(response.getDeliveryDocuments().size(), 0);
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
    assertEquals(
        printLabelRequests
            .stream()
            .filter(
                printRequest -> printRequest.getFormatName().equals(LabelFormat.DOTCOM.getFormat()))
            .count(),
        1);
    List<LabelData> labelDataList = printLabelRequests.get(0).getData();
    Optional<LabelData> labelSlot =
        labelDataList
            .stream()
            .filter(label -> label.getKey().equals(LabelConstants.LBL_SLOT))
            .findFirst();
    Optional<LabelData> labelStore =
        labelDataList
            .stream()
            .filter(label -> label.getKey().equals(LabelConstants.LBL_STORE))
            .findFirst();
    Optional<LabelData> labelTrackingId =
        labelDataList
            .stream()
            .filter(label -> label.getKey().equals(LabelConstants.LBL_BAR))
            .findFirst();
    assertEquals(labelSlot.get().getValue(), RdcConstants.DA_R8005_SLOT);
    assertEquals(labelStore.get().getValue(), "12345");
    assertEquals(labelTrackingId.get().getValue(), "123456789012345678");

    verify(nimRdsService, times(1))
        .getDsdcReceiveContainerRequest(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(nimRdsService, times(1))
        .receiveDsdcContainerInRds(any(DsdcReceiveRequest.class), any(HttpHeaders.class));
    verify(rdcContainerUtils, times(1))
        .buildContainer(
            any(InstructionRequest.class),
            any(DsdcReceiveResponse.class),
            anyLong(),
            anyString(),
            nullable(Container.class));
    verify(rdcContainerUtils, times(1))
        .buildContainerItem(
            any(DsdcReceiveResponse.class), anyString(), anyInt(), nullable(ContainerItem.class));
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
  }

  @Test
  public void createInstructionForDSDCReceiving_AuditPack() {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getSSCCInstructionRequestWithWorkStationEnabled();
    PrintJob printJob = new PrintJob();
    printJob.setId(232323L);

    when(nimRdsService.getDsdcReceiveContainerRequest(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(getMockDsdcReceiveRequest());
    when(nimRdsService.receiveDsdcContainerInRds(
            any(DsdcReceiveRequest.class), any(HttpHeaders.class)))
        .thenReturn(getMockDsdcReceiveResponseAuditPack());
    when(tenantSpecificConfigReader.getDCTimeZone(anyInt())).thenReturn("US/Eastern");
    when(printJobService.createPrintJob(anyLong(), anyLong(), anySet(), anyString()))
        .thenReturn(printJob);

    when(rdcContainerUtils.buildContainerItem(
            any(DsdcReceiveResponse.class), anyString(), anyInt(), any(ContainerItem.class)))
        .thenReturn(Collections.singletonList(new ContainerItem()));
    when(rdcContainerUtils.buildContainer(
            any(InstructionRequest.class),
            any(DsdcReceiveResponse.class),
            anyLong(),
            anyString(),
            any(Container.class)))
        .thenReturn(new Container());
    doNothing()
        .when(rdcReceivingUtils)
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(getMockDsdcAuditPackInstruction());

    InstructionResponse instructionResponse =
        rdcDsdcService.createInstructionForDSDCReceiving(instructionRequest, httpHeaders);
    InstructionResponseImplNew response = (InstructionResponseImplNew) instructionResponse;

    assertNotNull(instructionResponse);
    assertEquals(instructionResponse.getDeliveryDocuments().size(), 0);
    assertNotNull(instructionResponse.getInstruction());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.DSDC_AUDIT_REQUIRED.getInstructionCode());
    assertNotNull(response);
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
    assertEquals(
        printLabelRequests
            .stream()
            .filter(
                printRequest ->
                    printRequest.getFormatName().equals(LabelFormat.DSDC_AUDIT.getFormat()))
            .count(),
        1);

    verify(nimRdsService, times(1))
        .getDsdcReceiveContainerRequest(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(nimRdsService, times(1))
        .receiveDsdcContainerInRds(any(DsdcReceiveRequest.class), any(HttpHeaders.class));
    verify(rdcContainerUtils, times(0))
        .buildContainer(
            any(InstructionRequest.class),
            any(DsdcReceiveResponse.class),
            anyLong(),
            anyString(),
            any(Container.class));
    verify(rdcContainerUtils, times(0))
        .buildContainerItem(
            any(DsdcReceiveResponse.class), anyString(), anyInt(), any(ContainerItem.class));
    verify(rdcReceivingUtils, times(1))
        .persistReceivedContainerDetails(anyList(), anyList(), anyList(), anyList(), anyList());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void createInstructionForDSDCReceiving_AsnAlreadyReceivedError() {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getSSCCInstructionRequestWithWorkStationEnabled();
    HttpHeaders httpHeaders = new HttpHeaders();

    when(nimRdsService.getDsdcReceiveContainerRequest(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(getMockDsdcReceiveRequest());
    when(nimRdsService.receiveDsdcContainerInRds(
            any(DsdcReceiveRequest.class), any(HttpHeaders.class)))
        .thenReturn(getMockDsdcReceiveResponseErrorAsnAlreadyReceived());

    InstructionResponse instructionResponse =
        rdcDsdcService.createInstructionForDSDCReceiving(instructionRequest, httpHeaders);

    assertNull(instructionResponse);

    verify(nimRdsService, times(1))
        .getDsdcReceiveContainerRequest(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(nimRdsService, times(1))
        .receiveDsdcContainerInRds(any(DsdcReceiveRequest.class), any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void createInstructionForDSDCReceiving_AsnNotFoundError() {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getSSCCInstructionRequestWithWorkStationEnabled();
    HttpHeaders httpHeaders = new HttpHeaders();

    when(nimRdsService.getDsdcReceiveContainerRequest(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(getMockDsdcReceiveRequest());
    when(nimRdsService.receiveDsdcContainerInRds(
            any(DsdcReceiveRequest.class), any(HttpHeaders.class)))
        .thenReturn(getMockDsdcReceiveResponseErrorAsnNotFound());

    InstructionResponse instructionResponse =
        rdcDsdcService.createInstructionForDSDCReceiving(instructionRequest, httpHeaders);

    assertNotNull(instructionResponse);
    assertNull(instructionResponse.getInstruction());

    verify(nimRdsService, times(1))
        .getDsdcReceiveContainerRequest(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(nimRdsService, times(1))
        .receiveDsdcContainerInRds(any(DsdcReceiveRequest.class), any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void createInstructionForDSDCReceiving_AsnNotFoundError_AsnsAreAvailableInGDM() {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getSSCCInstructionRequestWithWorkStationEnabled();
    HttpHeaders httpHeaders = new HttpHeaders();
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), IS_DSDC_SSCC_PACKS_AVAILABLE_IN_GDM, false);

    when(nimRdsService.getDsdcReceiveContainerRequest(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(getMockDsdcReceiveRequest());
    when(nimRdsService.receiveDsdcContainerInRds(
            any(DsdcReceiveRequest.class), any(HttpHeaders.class)))
        .thenReturn(getMockDsdcReceiveResponseErrorAsnNotFound());

    InstructionResponse instructionResponse =
        rdcDsdcService.createInstructionForDSDCReceiving(instructionRequest, httpHeaders);

    assertNotNull(instructionResponse);
    assertNull(instructionResponse.getInstruction());

    verify(nimRdsService, times(1))
        .getDsdcReceiveContainerRequest(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(nimRdsService, times(1))
        .receiveDsdcContainerInRds(any(DsdcReceiveRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void
      createInstructionForDSDCReceiving_AsnNotFoundError_MobileQtyReceiving_DoNotThrowError() {
    InstructionRequest instructionRequest =
        MockInstructionRequest.getSSCCInstructionRequestForDsdcQtyReceiving();
    HttpHeaders httpHeaders = new HttpHeaders();

    when(nimRdsService.getDsdcReceiveContainerRequest(
            any(InstructionRequest.class), any(HttpHeaders.class)))
        .thenReturn(getMockDsdcReceiveRequest());
    when(nimRdsService.receiveDsdcContainerInRds(
            any(DsdcReceiveRequest.class), any(HttpHeaders.class)))
        .thenReturn(getMockDsdcReceiveResponseErrorAsnNotFound());

    InstructionResponse instructionResponse =
        rdcDsdcService.createInstructionForDSDCReceiving(instructionRequest, httpHeaders);

    assertNull(instructionResponse);

    verify(nimRdsService, times(1))
        .getDsdcReceiveContainerRequest(any(InstructionRequest.class), any(HttpHeaders.class));
    verify(nimRdsService, times(1))
        .receiveDsdcContainerInRds(any(DsdcReceiveRequest.class), any(HttpHeaders.class));
  }

  private DsdcReceiveRequest getMockDsdcReceiveRequest() {
    return DsdcReceiveRequest.builder()
        ._id("12345")
        .doorNum("100")
        .manifest("12345678")
        .pack_nbr("00123456789012345678")
        .userId("sysadmin")
        .build();
  }

  private DsdcReceiveResponse getMockDsdcReceiveResponseAuditPack() {
    return DsdcReceiveResponse.builder()
        .auditFlag("Y")
        .po_nbr("1234567")
        .pocode("73")
        .sneEnabled("true")
        .build();
  }

  private DsdcReceiveResponse getMockDsdcReceiveResponseSuccess() {
    return DsdcReceiveResponse.builder()
        .message("SUCCESS")
        .auditFlag("N")
        .batch("123")
        .dccarton("12345678")
        .dept("1")
        .div("2")
        .event("POS REPLEN")
        .hazmat("")
        .label_bar_code("123456789012345678")
        .lane_nbr("1")
        .po_nbr("12345678")
        .pocode("73")
        .rcvr_nbr("12345")
        .slot("R8002")
        .sneEnabled("1")
        .store("12345")
        .build();
  }

  private DsdcReceiveResponse getMockDotComReceiveResponseSuccess() {
    return DsdcReceiveResponse.builder()
        .message("SUCCESS")
        .auditFlag("N")
        .batch("123")
        .dccarton("12345678")
        .dept("1")
        .div("2")
        .event("POS REPLEN")
        .hazmat("")
        .label_bar_code("123456789012345678")
        .lane_nbr("1")
        .po_nbr("12345678")
        .pocode("73")
        .rcvr_nbr("12345")
        .slot("R8005")
        .sneEnabled("1")
        .store("12345")
        .build();
  }

  private DsdcReceiveResponse getMockDsdcReceiveResponseErrorAsnNotFound() {
    return DsdcReceiveResponse.builder()
        .errorCode("NIMRDS-022")
        .message("RDS DSDC validation failed => Error: ASN information was not found")
        .build();
  }

  private DsdcReceiveResponse getMockDsdcReceiveResponseErrorAsnAlreadyReceived() {
    return DsdcReceiveResponse.builder()
        .errorCode("NIMRDS-022")
        .message("RDS DSDC validation failed => Error: ASN already received")
        .build();
  }

  private Instruction getMockDsdcInstruction() {
    Instruction instruction = new Instruction();
    instruction.setId(2323L);
    instruction.setActivityName(WFTInstruction.DSDC.getActivityName());
    instruction.setInstructionMsg(RdcInstructionType.DSDC_RECEIVING.getInstructionMsg());
    instruction.setInstructionCode(RdcInstructionType.DSDC_RECEIVING.getInstructionCode());
    return instruction;
  }

  private Instruction getMockDsdcAuditPackInstruction() {
    Instruction instruction = new Instruction();
    instruction.setId(2323L);
    instruction.setActivityName(WFTInstruction.DSDC.getActivityName());
    instruction.setInstructionMsg(RdcInstructionType.DSDC_AUDIT_REQUIRED.getInstructionMsg());
    instruction.setInstructionCode(RdcInstructionType.DSDC_AUDIT_REQUIRED.getInstructionCode());
    return instruction;
  }
}
