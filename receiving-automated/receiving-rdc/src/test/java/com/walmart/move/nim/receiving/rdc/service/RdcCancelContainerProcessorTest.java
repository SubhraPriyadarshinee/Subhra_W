package com.walmart.move.nim.receiving.rdc.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_ATLAS_CONVERTED_ITEM;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.walmart.move.nim.receiving.core.client.inventory.InventoryRestApiClient;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.SymboticPutawayPublishHelper;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.*;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.CancelContainerRequest;
import com.walmart.move.nim.receiving.core.model.CancelContainerResponse;
import com.walmart.move.nim.receiving.core.model.InventoryExceptionRequest;
import com.walmart.move.nim.receiving.core.model.SwapContainerRequest;
import com.walmart.move.nim.receiving.core.model.instruction.InstructionDownloadContainerDTO;
import com.walmart.move.nim.receiving.core.model.instruction.LabelDataAllocationDTO;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.core.transformer.InventoryTransformer;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.mock.data.MockLocationHeaders;
import com.walmart.move.nim.receiving.rdc.model.LabelAction;
import com.walmart.move.nim.receiving.rdc.model.LabelInstructionStatus;
import com.walmart.move.nim.receiving.rdc.utils.RdcContainerUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcInstructionUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcReceivingUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ContainerType;
import com.walmart.move.nim.receiving.utils.constants.PurchaseReferenceType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.platform.repositories.OutboxEvent;
import java.util.*;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RdcCancelContainerProcessorTest {

  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private LabelDataService labelDataService;
  @Mock private ContainerAdjustmentHelper containerAdjustmentHelper;
  @Mock private InventoryRestApiClient inventoryRestApiClient;
  @Mock private NimRdsService nimRdsService;
  @Mock private DeliveryService deliveryService;
  @Mock private InstructionHelperService instructionHelperService;
  @Mock private ReceiptService receiptService;
  @Mock private RdcInstructionUtils rdcInstructionUtils;
  @Mock private RdcContainerUtils rdcContainerUtils;
  @Mock private LocationService locationService;
  @Mock private InstructionRepository instructionRepository;
  @Mock private SymboticPutawayPublishHelper symboticPutawayPublishHelper;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private AppConfig appConfig;
  @Mock private RdcManagedConfig rdcManagedConfig;
  @Mock private RdcLabelGenerationService rdcLabelGenerationService;

  @InjectMocks private RdcCancelContainerProcessor rdcCancelContainerProcessor;
  @Mock private InventoryTransformer inventoryTransformer;;
  @Mock private EIService eiService;
  @Mock private RdcReceivingUtils rdcReceivingUtils;
  @Mock private InstructionPersisterService instructionPersisterService;

  private HttpHeaders httpHeaders;
  private Gson gson;
  private final String countryCode = "US";
  private final String facilityNum = "32818";
  private final List<String> VALID_ASRS_LIST =
      Arrays.asList(
          ReceivingConstants.SYM_BRKPK_ASRS_VALUE, ReceivingConstants.SYM_CASE_PACK_ASRS_VALUE);

  @BeforeMethod
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    httpHeaders = MockLocationHeaders.getHeaders(facilityNum, countryCode);
    gson = new Gson();
    ReflectionTestUtils.setField(rdcCancelContainerProcessor, "gson", gson);
    TenantContext.setFacilityNum(32818);
  }

  @AfterMethod
  public void resetMocks() {
    reset(
        containerPersisterService,
        labelDataService,
        inventoryRestApiClient,
        nimRdsService,
        deliveryService,
        instructionHelperService,
        receiptService,
        rdcInstructionUtils,
        rdcContainerUtils,
        locationService,
        instructionRepository,
        symboticPutawayPublishHelper,
        rdcManagedConfig,
        rdcLabelGenerationService,
        inventoryTransformer,
        eiService,
        rdcReceivingUtils,
        instructionPersisterService);
  }

  @Test
  public void testCancelContainers_WhenNoContainerExists() throws ReceivingException {
    doReturn(null)
        .when(containerPersisterService)
        .getContainerWithChildContainersExcludingChildContents(anyString());
    CancelContainerRequest cancelContainerRequest = new CancelContainerRequest();
    cancelContainerRequest.setTrackingIds(Collections.singletonList("lpn123"));

    List<CancelContainerResponse> cancelContainersResponse =
        rdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertNotNull(cancelContainersResponse);

    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents("lpn123");
    assertEquals(
        ReceivingException.CONTAINER_NOT_FOUND_ERROR_CODE,
        cancelContainersResponse.get(0).getErrorCode());
    assertEquals(
        ReceivingException.CONTAINER_NOT_FOUND_ERROR_MSG,
        cancelContainersResponse.get(0).getErrorMessage());
  }

  @Test
  public void testPublishInvDeleteEventsToEI_WhenExpectionOccurs() throws ReceivingException {
    Container container = getMockContainer();
    doThrow(NullPointerException.class)
        .when(rdcContainerUtils)
        .publishContainerToEI(container, ReceivingConstants.DC_VOID);
    rdcCancelContainerProcessor.publishInvDeleteEventsToEI(container, ReceivingConstants.DC_VOID);
    assertEquals(container.getTrackingId(), "12344");
  }

  @Test
  public void testPublishInvDeleteEventsToEI_WhenNoContainerExists() throws ReceivingException {
    Container container = getMockContainer();
    doNothing().when(rdcContainerUtils).publishContainerToEI(container, ReceivingConstants.DC_VOID);
    rdcCancelContainerProcessor.publishInvDeleteEventsToEI(container, ReceivingConstants.DC_VOID);
    assertEquals(container.getTrackingId(), "12344");
  }

  @Test
  public void testSwapCancelContainers_WhenNoContainerExists() {
    doReturn(null).when(labelDataService).findByTrackingId(anyString());
    List<SwapContainerRequest> swapCancelList = new ArrayList<>();
    SwapContainerRequest swapCancelContainer = new SwapContainerRequest();
    swapCancelContainer.setTargetLpn("lpn123");
    swapCancelContainer.setSourceLpn("lpn333");
    swapCancelList.add(swapCancelContainer);

    List<CancelContainerResponse> cancelContainersResponse =
        rdcCancelContainerProcessor.swapContainers(swapCancelList, MockHttpHeaders.getHeaders());

    assertNotNull(cancelContainersResponse);
    assertEquals(
        ExceptionCodes.SOURCE_CONTAINER_NOT_FOUND, cancelContainersResponse.get(0).getErrorCode());
    assertEquals(
        String.format(ReceivingException.SOURCE_CONTAINER_NOT_FOUND, "lpn333"),
        cancelContainersResponse.get(0).getErrorMessage());
    verify(labelDataService, times(1)).findByTrackingId("lpn333");
  }

  @Test
  public void testSwapCancelContainers_WhenSourceContainerExists_With_InvalidStatus() {
    LabelData labelData = new LabelData();
    labelData.setStatus("COMPLETE");
    doReturn(labelData).when(labelDataService).findByTrackingId(anyString());
    List<SwapContainerRequest> swapCancellist = new ArrayList<>();
    SwapContainerRequest swapCancelContainer = new SwapContainerRequest();
    swapCancelContainer.setSourceLpn("lpn333");
    swapCancelContainer.setTargetLpn("lpn444");
    swapCancellist.add(swapCancelContainer);

    List<CancelContainerResponse> cancelContainersResponse =
        rdcCancelContainerProcessor.swapContainers(swapCancellist, MockHttpHeaders.getHeaders());
    assertNotNull(cancelContainersResponse);

    verify(labelDataService, times(1)).findByTrackingId("lpn333");
    assertEquals(
        ExceptionCodes.SOURCE_CONTAINER_NOT_ELIGIBLE,
        cancelContainersResponse.get(0).getErrorCode());
    assertEquals(
        String.format(ReceivingException.SOURCE_CONTAINER_NOT_ELIGIBLE, "lpn333"),
        cancelContainersResponse.get(0).getErrorMessage());
  }

  @Test
  public void testSwapCancelContainers_WhenContainerExists_With_CANCELLED_Status()
      throws ReceivingException {
    LabelData labelData = new LabelData();
    String sourceLpn = "lpn333";
    String targetLpn = "lpn444";
    labelData.setStatus(LabelInstructionStatus.CANCELLED.name());
    labelData.setTrackingId(sourceLpn);
    InstructionDownloadContainerDTO instructionDownloadContainerDTO =
        new InstructionDownloadContainerDTO();
    instructionDownloadContainerDTO.setTrackingId(sourceLpn);
    LabelDataAllocationDTO allocation = new LabelDataAllocationDTO();
    allocation.setContainer(instructionDownloadContainerDTO);
    labelData.setAllocation(allocation);
    doReturn(labelData).when(labelDataService).findByTrackingId(anyString());

    List<SwapContainerRequest> swapCancellist = new ArrayList<>();
    SwapContainerRequest swapCancelContainer = new SwapContainerRequest();
    swapCancelContainer.setTargetLpn(targetLpn);
    swapCancelContainer.setSourceLpn(sourceLpn);
    swapCancellist.add(swapCancelContainer);

    doNothing()
        .when(rdcLabelGenerationService)
        .publishNewLabelToHawkeye(any(LabelData.class), any(HttpHeaders.class));
    List<CancelContainerResponse> cancelContainersResponse =
        rdcCancelContainerProcessor.swapContainers(swapCancellist, MockHttpHeaders.getHeaders());

    assertTrue(cancelContainersResponse.isEmpty());
    verify(labelDataService, times(1)).findByTrackingId("lpn333");
    verify(rdcLabelGenerationService, times(1))
        .publishNewLabelToHawkeye(any(LabelData.class), any(HttpHeaders.class));
  }

  @Test
  public void
      testSwapCancelContainers_WhenContainerExists_With_CANCELLED_Status_ListOfContainers() {
    LabelData labelData1 = new LabelData();
    String sourceLpn1 = "lpn1";
    String targetLpn1 = "lpn11";
    String sourceLpn2 = "lpn2";
    String targetLpn2 = "lpn22";
    labelData1.setStatus(LabelInstructionStatus.CANCELLED.name());
    labelData1.setTrackingId(sourceLpn1);
    InstructionDownloadContainerDTO instructionDownloadContainerDTO1 =
        new InstructionDownloadContainerDTO();
    instructionDownloadContainerDTO1.setTrackingId(sourceLpn1);
    LabelDataAllocationDTO allocation1 = new LabelDataAllocationDTO();
    allocation1.setContainer(instructionDownloadContainerDTO1);
    labelData1.setAllocation(allocation1);

    LabelData labelData2 = new LabelData();
    labelData2.setStatus(LabelInstructionStatus.CANCELLED.name());
    labelData2.setTrackingId(sourceLpn2);
    InstructionDownloadContainerDTO instructionDownloadContainerDTO2 =
        new InstructionDownloadContainerDTO();
    instructionDownloadContainerDTO2.setTrackingId(sourceLpn2);
    LabelDataAllocationDTO allocation2 = new LabelDataAllocationDTO();
    allocation2.setContainer(instructionDownloadContainerDTO2);
    labelData2.setAllocation(allocation2);

    doReturn(labelData1).when(labelDataService).findByTrackingId(eq(sourceLpn1));
    doReturn(labelData2).when(labelDataService).findByTrackingId(eq(sourceLpn2));

    List<SwapContainerRequest> swapContainerRequests = new ArrayList<>();
    SwapContainerRequest swapCancelContainer1 = new SwapContainerRequest();
    swapCancelContainer1.setSourceLpn(sourceLpn1);
    swapCancelContainer1.setTargetLpn(targetLpn1);
    swapContainerRequests.add(swapCancelContainer1);
    SwapContainerRequest swapCancelContainer2 = new SwapContainerRequest();
    swapCancelContainer2.setSourceLpn(sourceLpn2);
    swapCancelContainer2.setTargetLpn(targetLpn2);
    swapContainerRequests.add(swapCancelContainer2);

    doNothing()
        .when(rdcLabelGenerationService)
        .publishNewLabelToHawkeye(any(LabelData.class), any(HttpHeaders.class));
    List<CancelContainerResponse> cancelContainersResponse =
        rdcCancelContainerProcessor.swapContainers(
            swapContainerRequests, MockHttpHeaders.getHeaders());

    assertTrue(cancelContainersResponse.isEmpty());
    verify(labelDataService, times(1)).findByTrackingId(eq(sourceLpn1));
    verify(labelDataService, times(1)).findByTrackingId(eq(sourceLpn2));
    verify(rdcLabelGenerationService, times(2))
        .publishNewLabelToHawkeye(any(LabelData.class), any(HttpHeaders.class));
  }

  @Test
  public void
      testCancelContainers_IsSuccess_WhenNoLocationHeadersExists_AndInventoryIntegrationIsNotEnabled()
          throws ReceivingException {
    Container palletContainer = createContainer();
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(IS_ATLAS_CONVERTED_ITEM, "false");
    palletContainer.getContainerItems().get(0).setContainerItemMiscInfo(containerItemMiscInfo);
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(palletContainer);
    when(containerAdjustmentHelper.validateContainerForAdjustment(
            any(Container.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);
    doNothing().when(nimRdsService).quantityChange(anyInt(), anyString(), any(HttpHeaders.class));
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction().get());

    CancelContainerRequest cancelContainerRequest = new CancelContainerRequest();
    cancelContainerRequest.setTrackingIds(Collections.singletonList("lpn123"));

    List<CancelContainerResponse> cancelContainersResponse =
        rdcCancelContainerProcessor.cancelContainers(
            cancelContainerRequest, MockHttpHeaders.getHeaders(facilityNum, countryCode));

    ArgumentCaptor<Container> containerCaptor = ArgumentCaptor.forClass(Container.class);

    assertEquals(cancelContainersResponse.size(), 0);

    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(containerAdjustmentHelper, times(1))
        .validateContainerForAdjustment(any(Container.class), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(nimRdsService, times(1)).quantityChange(anyInt(), anyString(), any(HttpHeaders.class));
    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(rdcReceivingUtils, times(1))
        .postCancelContainersUpdates(
            any(), any(), any(Container.class), any(Instruction.class), any());
    verify(rdcInstructionUtils, times(1))
        .publishInstructionToWft(
            any(Container.class),
            any(Integer.class),
            any(Integer.class),
            any(LabelAction.class),
            any(HttpHeaders.class));
  }

  @Test
  public void
      testCancelContainers_ThrowsException_WhenLocationServiceThrowsException_AndInventoryIntegrationIsNotEnabled()
          throws ReceivingException {
    Container palletContainer = createContainer();
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(IS_ATLAS_CONVERTED_ITEM, "false");
    palletContainer.getContainerItems().get(0).setContainerItemMiscInfo(containerItemMiscInfo);
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(palletContainer);
    when(containerAdjustmentHelper.validateContainerForAdjustment(
            any(Container.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);
    doNothing().when(nimRdsService).quantityChange(anyInt(), anyString(), any(HttpHeaders.class));
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction().get());

    CancelContainerRequest cancelContainerRequest = new CancelContainerRequest();
    cancelContainerRequest.setTrackingIds(Collections.singletonList("lpn123"));
    rdcCancelContainerProcessor.cancelContainers(
        cancelContainerRequest, MockHttpHeaders.getHeaders(facilityNum, countryCode));

    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(containerAdjustmentHelper, times(1))
        .validateContainerForAdjustment(any(Container.class), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(nimRdsService, times(1)).quantityChange(anyInt(), anyString(), any(HttpHeaders.class));
    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(rdcReceivingUtils, times(1))
        .postCancelContainersUpdates(
            any(), any(), any(Container.class), any(Instruction.class), any());
    verify(rdcInstructionUtils, times(1))
        .publishInstructionToWft(
            any(Container.class),
            any(Integer.class),
            any(Integer.class),
            any(LabelAction.class),
            any(HttpHeaders.class));
  }

  @Test
  public void
      testCancelContainers_IsSuccess_WhenInventoryIntegrationIsEnabled_ItemIsSSTK_AndAtlasConvertedItemIsTrue()
          throws ReceivingException {
    Container palletContainer = createContainer();
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(IS_ATLAS_CONVERTED_ITEM, "true");
    palletContainer.getContainerItems().get(0).setContainerItemMiscInfo(containerItemMiscInfo);
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(palletContainer);
    when(containerAdjustmentHelper.validateContainerForAdjustment(
            any(Container.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(inventoryRestApiClient.notifyBackoutAdjustment(
            any(InventoryExceptionRequest.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(containerAdjustmentHelper.adjustReceipts(any(Container.class)))
        .thenReturn(getAdjustedReceipt());

    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction().get());
    doNothing()
        .when(rdcReceivingUtils)
        .postCancelContainersUpdates(any(), any(), any(), any(), any());
    doNothing()
        .when(rdcInstructionUtils)
        .publishInstructionToWft(
            any(Container.class),
            any(Integer.class),
            any(Integer.class),
            any(LabelAction.class),
            any(HttpHeaders.class));
    CancelContainerRequest cancelContainerRequest = new CancelContainerRequest();
    cancelContainerRequest.setTrackingIds(Collections.singletonList("lpn123"));

    List<CancelContainerResponse> cancelContainersResponse =
        rdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertTrue(cancelContainersResponse.isEmpty());

    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(containerAdjustmentHelper, times(1))
        .validateContainerForAdjustment(any(Container.class), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(inventoryRestApiClient, times(1))
        .notifyBackoutAdjustment(any(InventoryExceptionRequest.class), any(HttpHeaders.class));
    verify(containerAdjustmentHelper, times(1)).adjustReceipts(any(Container.class));
    verify(rdcReceivingUtils, times(1))
        .postCancelContainersUpdates(any(), any(), any(), any(), any());
    verify(rdcInstructionUtils, times(1))
        .publishInstructionToWft(
            any(Container.class),
            any(Integer.class),
            any(Integer.class),
            any(LabelAction.class),
            any(HttpHeaders.class));
    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
  }

  @Test
  public void
      testCancelContainers_IsSuccess_WhenInventoryIntegrationIsEnabled_ItemIsSSTK_AndAtlasConvertedItemIsTrue_overrideVTRFromCCM()
          throws ReceivingException {
    Container palletContainer = createContainer();
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(IS_ATLAS_CONVERTED_ITEM, "true");
    palletContainer.getContainerItems().get(0).setContainerItemMiscInfo(containerItemMiscInfo);
    when(rdcManagedConfig.getVtrReasonCode()).thenReturn(20);
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(palletContainer);
    when(containerAdjustmentHelper.validateContainerForAdjustment(
            any(Container.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(inventoryRestApiClient.notifyBackoutAdjustment(
            any(InventoryExceptionRequest.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(containerAdjustmentHelper.adjustReceipts(any(Container.class)))
        .thenReturn(getAdjustedReceipt());
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction().get());
    doNothing()
        .when(rdcReceivingUtils)
        .postCancelContainersUpdates(any(), any(), any(), any(), any());
    doNothing()
        .when(rdcInstructionUtils)
        .publishInstructionToWft(
            any(Container.class),
            any(Integer.class),
            any(Integer.class),
            any(LabelAction.class),
            any(HttpHeaders.class));

    CancelContainerRequest cancelContainerRequest = new CancelContainerRequest();
    cancelContainerRequest.setTrackingIds(Collections.singletonList("lpn123"));

    List<CancelContainerResponse> cancelContainersResponse =
        rdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertTrue(cancelContainersResponse.isEmpty());

    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(containerAdjustmentHelper, times(1))
        .validateContainerForAdjustment(any(Container.class), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(inventoryRestApiClient, times(1))
        .notifyBackoutAdjustment(any(InventoryExceptionRequest.class), any(HttpHeaders.class));
    verify(containerAdjustmentHelper, times(1)).adjustReceipts(any(Container.class));
    verify(rdcReceivingUtils, times(1))
        .postCancelContainersUpdates(any(), any(), any(), any(), any());
    verify(rdcInstructionUtils, times(1))
        .publishInstructionToWft(
            any(Container.class),
            any(Integer.class),
            any(Integer.class),
            any(LabelAction.class),
            any(HttpHeaders.class));
    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
  }

  @Test
  public void
      testCancelContainers_IsSuccess_WhenInventoryIntegrationIsEnabled_ItemIsSSTK_ButAtlasConvertedItemIsFalse()
          throws ReceivingException {
    Container palletContainer = createContainer();
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(IS_ATLAS_CONVERTED_ITEM, "false");
    palletContainer.getContainerItems().get(0).setContainerItemMiscInfo(containerItemMiscInfo);
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(palletContainer);
    when(containerAdjustmentHelper.validateContainerForAdjustment(
            any(Container.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    doNothing().when(nimRdsService).quantityChange(anyInt(), anyString(), any(HttpHeaders.class));
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction().get());
    doNothing()
        .when(rdcReceivingUtils)
        .postCancelContainersUpdates(
            any(), any(), any(Container.class), any(Instruction.class), any());

    CancelContainerRequest cancelContainerRequest = new CancelContainerRequest();
    cancelContainerRequest.setTrackingIds(Collections.singletonList("lpn123"));

    List<CancelContainerResponse> cancelContainersResponse =
        rdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertTrue(cancelContainersResponse.isEmpty());

    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(containerAdjustmentHelper, times(1))
        .validateContainerForAdjustment(any(Container.class), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(nimRdsService, times(1)).quantityChange(anyInt(), anyString(), any(HttpHeaders.class));
    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(rdcReceivingUtils, times(1))
        .postCancelContainersUpdates(
            any(), any(), any(Container.class), any(Instruction.class), any());
    verify(rdcInstructionUtils, times(1))
        .publishInstructionToWft(
            any(Container.class),
            any(Integer.class),
            any(Integer.class),
            any(LabelAction.class),
            any(HttpHeaders.class));
  }

  @Test
  public void testCancelContainers_IsSuccess_WhenInventoryIntegrationIsNotEnabled_ItemIsDA_Label()
      throws ReceivingException {
    Container container = createContainer();
    container
        .getContainerItems()
        .get(0)
        .setInboundChannelMethod(PurchaseReferenceType.CROSSU.name());
    container.setContainerType(ContainerType.CASE.name());
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(container);
    when(containerAdjustmentHelper.validateContainerForAdjustment(
            any(Container.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);
    doNothing().when(nimRdsService).backoutDALabels(anyList(), any(HttpHeaders.class));
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction().get());

    CancelContainerRequest cancelContainerRequest = new CancelContainerRequest();
    cancelContainerRequest.setTrackingIds(Collections.singletonList("lpn123"));

    List<CancelContainerResponse> cancelContainersResponse =
        rdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertTrue(cancelContainersResponse.isEmpty());

    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(containerAdjustmentHelper, times(1))
        .validateContainerForAdjustment(any(Container.class), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(nimRdsService, times(1)).backoutDALabels(anyList(), any(HttpHeaders.class));
    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(rdcReceivingUtils, times(1))
        .postCancelContainersUpdates(
            any(), any(), any(Container.class), any(Instruction.class), any());
    verify(rdcInstructionUtils, times(1))
        .publishInstructionToWft(
            any(Container.class),
            any(Integer.class),
            any(Integer.class),
            any(LabelAction.class),
            any(HttpHeaders.class));
  }

  @Test
  public void testCancelContainers_IsSuccess_WhenInventoryIntegrationIsNotEnabled_ItemIsDA_Pallet()
      throws ReceivingException {
    Container container = createContainer();
    container
        .getContainerItems()
        .get(0)
        .setInboundChannelMethod(PurchaseReferenceType.CROSSU.name());
    container.setContainerType(ContainerType.PALLET.getText());
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(container);
    when(containerAdjustmentHelper.validateContainerForAdjustment(
            any(Container.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);
    doNothing().when(nimRdsService).quantityChange(anyInt(), anyString(), any(HttpHeaders.class));
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction().get());

    CancelContainerRequest cancelContainerRequest = new CancelContainerRequest();
    cancelContainerRequest.setTrackingIds(Collections.singletonList("lpn123"));

    List<CancelContainerResponse> cancelContainersResponse =
        rdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertTrue(cancelContainersResponse.isEmpty());

    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(containerAdjustmentHelper, times(1))
        .validateContainerForAdjustment(any(Container.class), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(nimRdsService, times(1)).quantityChange(anyInt(), anyString(), any(HttpHeaders.class));
    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(rdcReceivingUtils, times(1))
        .postCancelContainersUpdates(
            any(), any(), any(Container.class), any(Instruction.class), any());
    verify(rdcInstructionUtils, times(1))
        .publishInstructionToWft(
            any(Container.class),
            any(Integer.class),
            any(Integer.class),
            any(LabelAction.class),
            any(HttpHeaders.class));
  }

  @Test
  public void
      testCancelContainers_ReturnsErrorResponse_WhenInventoryIntegrationIsNotEnabled_ItemIsDA()
          throws ReceivingException {
    Container container = createContainer();
    container
        .getContainerItems()
        .get(0)
        .setInboundChannelMethod(PurchaseReferenceType.CROSSU.name());
    container.setContainerStatus(ReceivingConstants.LABEL_BACKOUT);

    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(container);
    when(containerAdjustmentHelper.validateContainerForAdjustment(
            any(Container.class), any(HttpHeaders.class)))
        .thenReturn(
            new CancelContainerResponse(
                container.getTrackingId(),
                ReceivingException.CONTAINER_ALREADY_CANCELED_ERROR_CODE,
                ReceivingException.CONTAINER_ALREADY_CANCELED_ERROR_MSG));

    CancelContainerRequest cancelContainerRequest = new CancelContainerRequest();
    cancelContainerRequest.setTrackingIds(Collections.singletonList("lpn123"));

    List<CancelContainerResponse> cancelContainersResponse =
        rdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertNotNull(cancelContainersResponse);
    assertNotNull(cancelContainersResponse.get(0).getErrorCode());
    assertNotNull(cancelContainersResponse.get(0).getErrorMessage());
    assertTrue(
        cancelContainersResponse
            .get(0)
            .getErrorCode()
            .equals(ReceivingException.CONTAINER_ALREADY_CANCELED_ERROR_CODE));
    assertTrue(
        cancelContainersResponse
            .get(0)
            .getErrorMessage()
            .equals(ReceivingException.CONTAINER_ALREADY_CANCELED_ERROR_MSG));

    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(containerAdjustmentHelper, times(1))
        .validateContainerForAdjustment(any(Container.class), any(HttpHeaders.class));
  }

  @Test
  public void testCancelContainers_ReturnsInvalidChannelMethodErrorResponse_WhenItemIsDSDC()
      throws ReceivingException {
    Container container = createContainer();
    container.getContainerItems().get(0).setInboundChannelMethod("DSDC");

    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(container);
    when(containerAdjustmentHelper.validateContainerForAdjustment(
            any(Container.class), any(HttpHeaders.class)))
        .thenReturn(
            new CancelContainerResponse(
                container.getTrackingId(),
                ReceivingException.INVALID_CHANNEL_METHOD_FOR_LABEL_BACKOUT_CODE,
                String.format(
                    ReceivingException.INVALID_CHANNEL_METHOD_FOR_LABEL_BACKOUT_MSG,
                    container.getContainerItems().get(0).getInboundChannelMethod())));

    CancelContainerRequest cancelContainerRequest = new CancelContainerRequest();
    cancelContainerRequest.setTrackingIds(Collections.singletonList("lpn123"));

    List<CancelContainerResponse> cancelContainersResponse =
        rdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertNotNull(cancelContainersResponse);
    assertNotNull(cancelContainersResponse.get(0).getErrorCode());
    assertNotNull(cancelContainersResponse.get(0).getErrorMessage());
    assertTrue(
        cancelContainersResponse
            .get(0)
            .getErrorCode()
            .equals(ReceivingException.INVALID_CHANNEL_METHOD_FOR_LABEL_BACKOUT_CODE));
    assertTrue(
        cancelContainersResponse
            .get(0)
            .getErrorMessage()
            .equals("This Channel method: DSDC is not supported now for label backout"));

    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(containerAdjustmentHelper, times(1))
        .validateContainerForAdjustment(any(Container.class), any(HttpHeaders.class));
  }

  @Test
  public void testCancelContainers_ForAtlasConvertedItem_AndPublishSymPutAwayMessage_IsSuccess()
      throws ReceivingException {
    Container palletContainer = createContainer();
    palletContainer.getContainerItems().get(0).setSlotType(ReceivingConstants.PRIME_SLOT_TYPE);
    palletContainer
        .getContainerItems()
        .get(0)
        .setAsrsAlignment(ReceivingConstants.SYM_CASE_PACK_ASRS_VALUE);
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(IS_ATLAS_CONVERTED_ITEM, "true");
    palletContainer.getContainerItems().get(0).setContainerItemMiscInfo(containerItemMiscInfo);
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(palletContainer);
    when(containerAdjustmentHelper.validateContainerForAdjustment(
            any(Container.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_SYM_CUTOVER_COMPLETED,
            false))
        .thenReturn(false);
    when(inventoryRestApiClient.notifyBackoutAdjustment(
            any(InventoryExceptionRequest.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(containerAdjustmentHelper.adjustReceipts(any(Container.class)))
        .thenReturn(getAdjustedReceipt());
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);
    doNothing()
        .when(symboticPutawayPublishHelper)
        .publishSymPutawayUpdateOrDeleteMessage(
            anyString(), any(ContainerItem.class), anyString(), anyInt(), any(HttpHeaders.class));
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction().get());
    CancelContainerRequest cancelContainerRequest = new CancelContainerRequest();
    cancelContainerRequest.setTrackingIds(Collections.singletonList("lpn123"));
    doNothing()
        .when(rdcReceivingUtils)
        .postCancelContainersUpdates(any(), any(), any(), any(), any());
    doNothing()
        .when(rdcInstructionUtils)
        .publishInstructionToWft(
            any(Container.class),
            any(Integer.class),
            any(Integer.class),
            any(LabelAction.class),
            any(HttpHeaders.class));
    List<CancelContainerResponse> cancelContainersResponse =
        rdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(containerAdjustmentHelper, times(1))
        .validateContainerForAdjustment(any(Container.class), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(inventoryRestApiClient, times(1))
        .notifyBackoutAdjustment(any(InventoryExceptionRequest.class), any(HttpHeaders.class));
    verify(containerAdjustmentHelper, times(1)).adjustReceipts(any(Container.class));
    verify(symboticPutawayPublishHelper, times(1))
        .publishSymPutawayUpdateOrDeleteMessage(
            anyString(), any(ContainerItem.class), anyString(), anyInt(), any(HttpHeaders.class));
    verify(rdcReceivingUtils, times(1))
        .postCancelContainersUpdates(any(), any(), any(), any(), any());
    verify(rdcInstructionUtils, times(1))
        .publishInstructionToWft(
            any(Container.class),
            any(Integer.class),
            any(Integer.class),
            any(LabelAction.class),
            any(HttpHeaders.class));
    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
  }

  @Test
  public void
      testCancelContainers_SaveLabelData_WithCancelStatus_ForAtlasConvertedItem_AndPublishSymPutAwayMessage_IsSuccess()
          throws ReceivingException {
    LabelData labelData = new LabelData();
    Container palletContainer = createContainer();
    palletContainer.getContainerItems().get(0).setSlotType(ReceivingConstants.PRIME_SLOT_TYPE);
    palletContainer
        .getContainerItems()
        .get(0)
        .setAsrsAlignment(ReceivingConstants.SYM_CASE_PACK_ASRS_VALUE);
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(IS_ATLAS_CONVERTED_ITEM, "true");
    palletContainer.getContainerItems().get(0).setContainerItemMiscInfo(containerItemMiscInfo);
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(palletContainer);
    when(containerAdjustmentHelper.validateContainerForAdjustment(
            any(Container.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_SYM_CUTOVER_COMPLETED,
            false))
        .thenReturn(true);
    when(inventoryRestApiClient.notifyBackoutAdjustment(
            any(InventoryExceptionRequest.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(containerAdjustmentHelper.adjustReceipts(any(Container.class)))
        .thenReturn(getAdjustedReceipt());
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);
    doNothing()
        .when(symboticPutawayPublishHelper)
        .publishSymPutawayUpdateOrDeleteMessage(
            anyString(), any(ContainerItem.class), anyString(), anyInt(), any(HttpHeaders.class));
    when(labelDataService.findByTrackingId(anyString())).thenReturn(new LabelData());
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction().get());
    doNothing()
        .when(rdcReceivingUtils)
        .postCancelContainersUpdates(any(), any(), any(), any(), any());
    doNothing()
        .when(rdcInstructionUtils)
        .publishInstructionToWft(
            any(Container.class),
            any(Integer.class),
            any(Integer.class),
            any(LabelAction.class),
            any(HttpHeaders.class));
    CancelContainerRequest cancelContainerRequest = new CancelContainerRequest();
    cancelContainerRequest.setTrackingIds(Collections.singletonList("lpn123"));
    List<CancelContainerResponse> cancelContainersResponse =
        rdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);
    verify(labelDataService, times(0)).findByTrackingIdIn(anyList());
    verify(rdcReceivingUtils, times(1))
        .postCancelContainersUpdates(any(), any(), any(), any(), any());
    verify(rdcInstructionUtils, times(1))
        .publishInstructionToWft(
            any(Container.class),
            any(Integer.class),
            any(Integer.class),
            any(LabelAction.class),
            any(HttpHeaders.class));
    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
  }

  @Test
  public void
      testCancelContainers_ForAtlasItem_NotPublishing_MessageToSymbotic_ForInvalidAsrsAlignmentValue()
          throws ReceivingException {
    Container palletContainer = createContainer();
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(IS_ATLAS_CONVERTED_ITEM, "true");
    palletContainer.getContainerItems().get(0).setContainerItemMiscInfo(containerItemMiscInfo);
    palletContainer.getContainerItems().get(0).setSlotType(ReceivingConstants.PRIME_SLOT_TYPE);
    palletContainer.getContainerItems().get(0).setAsrsAlignment(ReceivingConstants.PTL_ASRS_VALUE);
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(palletContainer);
    when(containerAdjustmentHelper.validateContainerForAdjustment(
            any(Container.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(inventoryRestApiClient.notifyBackoutAdjustment(
            any(InventoryExceptionRequest.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(containerAdjustmentHelper.adjustReceipts(any(Container.class)))
        .thenReturn(getAdjustedReceipt());
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);
    CancelContainerRequest cancelContainerRequest = new CancelContainerRequest();
    cancelContainerRequest.setTrackingIds(Collections.singletonList("lpn123"));
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction().get());
    doNothing()
        .when(rdcReceivingUtils)
        .postCancelContainersUpdates(any(), any(), any(), any(), any());
    doNothing()
        .when(rdcInstructionUtils)
        .publishInstructionToWft(
            any(Container.class),
            any(Integer.class),
            any(Integer.class),
            any(LabelAction.class),
            any(HttpHeaders.class));
    List<CancelContainerResponse> cancelContainersResponse =
        rdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);
    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(containerAdjustmentHelper, times(1))
        .validateContainerForAdjustment(any(Container.class), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(inventoryRestApiClient, times(1))
        .notifyBackoutAdjustment(any(InventoryExceptionRequest.class), any(HttpHeaders.class));
    verify(containerAdjustmentHelper, times(1)).adjustReceipts(any(Container.class));
    verify(symboticPutawayPublishHelper, times(0))
        .publishSymPutawayUpdateOrDeleteMessage(
            anyString(), any(ContainerItem.class), anyString(), anyInt(), any(HttpHeaders.class));
    verify(rdcReceivingUtils, times(1))
        .postCancelContainersUpdates(any(), any(), any(), any(), any());
    verify(rdcInstructionUtils, times(1))
        .publishInstructionToWft(
            any(Container.class),
            any(Integer.class),
            any(Integer.class),
            any(LabelAction.class),
            any(HttpHeaders.class));
    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
  }

  private Container createContainer() {
    Container container = new Container();
    container.setParentTrackingId(null);
    container.setTrackingId("lpn1");
    container.setDeliveryNumber(12345L);
    container.setLocation("200");
    container.setInstructionId(123L);

    ContainerItem containerItem = new ContainerItem();
    containerItem.setPurchaseReferenceNumber("PO1");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setInboundChannelMethod(PurchaseReferenceType.SSTKU.name());

    containerItem.setQuantity(6);
    containerItem.setVnpkQty(6);
    containerItem.setWhpkQty(6);
    container.setContainerItems(Collections.singletonList(containerItem));

    container.setPublishTs(new Date());
    container.setCompleteTs(new Date());
    container.setCreateTs(new Date());
    container.setLastChangedUser("sysadmin");

    return container;
  }

  private Container createContainerWithStatusStarBackOut() {
    Container container = new Container();
    container.setParentTrackingId(null);
    container.setTrackingId("lpn1");
    container.setDeliveryNumber(12345L);
    container.setLocation("200");
    container.setInstructionId(123L);
    container.setContainerStatus(ReceivingConstants.STATUS_BACKOUT);

    ContainerItem containerItem = new ContainerItem();
    containerItem.setPurchaseReferenceNumber("PO1");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setInboundChannelMethod(PurchaseReferenceType.SSTKU.name());

    containerItem.setQuantity(6);
    containerItem.setVnpkQty(12);
    containerItem.setWhpkQty(6);
    container.setContainerItems(Collections.singletonList(containerItem));

    container.setPublishTs(new Date());
    container.setCompleteTs(new Date());
    container.setCreateTs(new Date());
    container.setLastChangedUser("sysadmin");

    return container;
  }

  private Container createContainerWithChildContainers() {
    Container container = new Container();
    container.setTrackingId("lpn1");
    container.setDeliveryNumber(12345L);
    container.setLocation("200");
    container.setInstructionId(123L);

    Container childContainer1 = new Container();
    childContainer1.setTrackingId("child1");
    ContainerItem containerItem1 = new ContainerItem();
    containerItem1.setPurchaseReferenceNumber("PO1");
    containerItem1.setPurchaseReferenceLineNumber(1);
    containerItem1.setInboundChannelMethod(PurchaseReferenceType.CROSSU.name());

    containerItem1.setQuantity(6);
    containerItem1.setVnpkQty(6);
    containerItem1.setWhpkQty(6);
    childContainer1.setContainerItems(Collections.singletonList(containerItem1));
    Container childContainer2 = new Container();
    childContainer2.setTrackingId("child2");
    ContainerItem containerItem2 = new ContainerItem();
    containerItem2.setPurchaseReferenceNumber("PO1");
    containerItem2.setPurchaseReferenceLineNumber(2);
    containerItem2.setInboundChannelMethod(PurchaseReferenceType.CROSSU.name());
    containerItem2.setQuantity(6);
    containerItem2.setVnpkQty(6);
    containerItem2.setWhpkQty(6);
    childContainer2.setContainerItems(Collections.singletonList(containerItem2));

    Set<Container> childContainers = new HashSet<>();
    childContainers.add(childContainer1);
    childContainers.add(childContainer2);
    container.setChildContainers(childContainers);
    container.setPublishTs(new Date());
    container.setCompleteTs(new Date());
    container.setCreateTs(new Date());
    container.setLastChangedUser("sysadmin");

    ContainerItem parentContainerItem = new ContainerItem();
    parentContainerItem.setInboundChannelMethod(PurchaseReferenceType.CROSSU.name());
    container.setContainerItems(Collections.singletonList(parentContainerItem));

    return container;
  }

  private Receipt getAdjustedReceipt() {
    Receipt receipt = new Receipt();
    receipt.setDeliveryNumber(1234L);
    receipt.setPurchaseReferenceNumber("PO1");
    receipt.setPurchaseReferenceLineNumber(1);
    receipt.setQuantity(-1);
    receipt.setQuantityUom(ReceivingConstants.Uom.VNPK);
    receipt.setVnpkQty(6);
    receipt.setWhpkQty(6);
    receipt.setEachQty(-6);
    receipt.setCreateUserId("sysadmin");

    return receipt;
  }

  private Optional<Instruction> getMockInstruction() {
    Instruction instruction = new Instruction();
    instruction.setId(123L);
    instruction.setDeliveryNumber(1234L);
    instruction.setPurchaseReferenceNumber("PO1");
    instruction.setPurchaseReferenceLineNumber(1);
    instruction.setProjectedReceiveQty(25);
    instruction.setReceivedQuantity(25);
    instruction.setCreateUserId("sysadmin");
    instruction.setCreateTs(new Date());
    instruction.setLastChangeUserId("syadmin");
    instruction.setLastChangeTs(new Date());
    return Optional.of(instruction);
  }

  @Test
  public void
      testCancelContainers_IsSuccess_WhenInventoryIntegrationIsEnabled_ItemIsSSTK_AndAtlasConvertedItemIsTrueAndEIDCVoid_NotInvoked()
          throws ReceivingException {
    Container palletContainer = createContainer();
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(IS_ATLAS_CONVERTED_ITEM, "true");
    palletContainer.getContainerItems().get(0).setContainerItemMiscInfo(containerItemMiscInfo);
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(palletContainer);
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(palletContainer);
    when(containerAdjustmentHelper.validateContainerForAdjustment(
            any(Container.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(inventoryRestApiClient.notifyBackoutAdjustment(
            any(InventoryExceptionRequest.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(containerAdjustmentHelper.adjustReceipts(any(Container.class)))
        .thenReturn(getAdjustedReceipt());
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction().get());
    doNothing()
        .when(rdcReceivingUtils)
        .postCancelContainersUpdates(any(), any(), any(), any(), any());
    doNothing()
        .when(rdcInstructionUtils)
        .publishInstructionToWft(
            any(Container.class),
            any(Integer.class),
            any(Integer.class),
            any(LabelAction.class),
            any(HttpHeaders.class));
    CancelContainerRequest cancelContainerRequest = new CancelContainerRequest();
    cancelContainerRequest.setTrackingIds(Collections.singletonList("lpn123"));

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_EI_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    doNothing().when(rdcContainerUtils).publishContainerToEI(any(Container.class), any());

    List<CancelContainerResponse> cancelContainersResponse =
        rdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertTrue(cancelContainersResponse.isEmpty());

    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(containerAdjustmentHelper, times(1))
        .validateContainerForAdjustment(any(Container.class), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(inventoryRestApiClient, times(1))
        .notifyBackoutAdjustment(any(InventoryExceptionRequest.class), any(HttpHeaders.class));
    verify(containerAdjustmentHelper, times(1)).adjustReceipts(any(Container.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_EI_INTEGRATION_ENABLED,
            false);
    verify(rdcContainerUtils, times(0)).publishContainerToEI(any(Container.class), any());
    verify(containerPersisterService, times(1)).getConsolidatedContainerForPublish(anyString());
    verify(rdcReceivingUtils, times(1))
        .postCancelContainersUpdates(any(), any(), any(), any(), any());
    verify(rdcInstructionUtils, times(1))
        .publishInstructionToWft(
            any(Container.class),
            any(Integer.class),
            any(Integer.class),
            any(LabelAction.class),
            any(HttpHeaders.class));
    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
  }

  @Test
  public void
      testCancelContainers_IsSuccess_WhenInventoryIntegrationIsEnabled_ItemIsDA_AndAtlasConvertedItemIsTrueAndEIDCVoid_()
          throws ReceivingException {
    Container palletContainer = createContainer();
    palletContainer
        .getContainerItems()
        .get(0)
        .setInboundChannelMethod(PurchaseReferenceType.CROSSU.name());
    palletContainer.setContainerType(ContainerType.PALLET.getText());
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(IS_ATLAS_CONVERTED_ITEM, "true");
    palletContainer.getContainerItems().get(0).setContainerItemMiscInfo(containerItemMiscInfo);
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(palletContainer);
    when(containerPersisterService.getConsolidatedContainerForPublish(anyString()))
        .thenReturn(palletContainer);
    when(containerAdjustmentHelper.validateContainerForAdjustment(
            any(Container.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_SYM_CUTOVER_COMPLETED,
            false))
        .thenReturn(false);
    when(inventoryRestApiClient.notifyBackoutAdjustment(
            any(InventoryExceptionRequest.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(containerAdjustmentHelper.adjustReceipts(any(Container.class)))
        .thenReturn(getAdjustedReceipt());

    CancelContainerRequest cancelContainerRequest = new CancelContainerRequest();
    cancelContainerRequest.setTrackingIds(Collections.singletonList("lpn123"));

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_EI_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    doNothing().when(rdcContainerUtils).publishContainerToEI(any(Container.class), any());
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction().get());
    doNothing()
        .when(rdcReceivingUtils)
        .postCancelContainersUpdates(any(), any(), any(), any(), any());
    doNothing()
        .when(rdcInstructionUtils)
        .publishInstructionToWft(
            any(Container.class),
            any(Integer.class),
            any(Integer.class),
            any(LabelAction.class),
            any(HttpHeaders.class));

    List<CancelContainerResponse> cancelContainersResponse =
        rdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertTrue(cancelContainersResponse.isEmpty());

    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(containerAdjustmentHelper, times(1))
        .validateContainerForAdjustment(any(Container.class), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(inventoryRestApiClient, times(1))
        .notifyBackoutAdjustment(any(InventoryExceptionRequest.class), any(HttpHeaders.class));
    verify(containerAdjustmentHelper, times(1)).adjustReceipts(any(Container.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_EI_INTEGRATION_ENABLED,
            false);
    verify(rdcContainerUtils, times(1)).publishContainerToEI(any(Container.class), any());
    verify(containerPersisterService, times(1)).getConsolidatedContainerForPublish(anyString());
    verify(rdcReceivingUtils, times(1))
        .postCancelContainersUpdates(any(), any(), any(), any(), any());
    verify(rdcInstructionUtils, times(1))
        .publishInstructionToWft(
            any(Container.class),
            any(Integer.class),
            any(Integer.class),
            any(LabelAction.class),
            any(HttpHeaders.class));
    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
  }

  @Test
  public void
      testCancelContainers_IsSuccess_WhenInventoryIntegrationIsEnabled_ItemIsDA_AndAtlasConvertedItemIsTrueAndOutboxEnable()
          throws ReceivingException {
    Container palletContainer = createContainer();
    palletContainer
        .getContainerItems()
        .get(0)
        .setInboundChannelMethod(PurchaseReferenceType.CROSSU.name());
    palletContainer.setContainerType(ContainerType.PALLET.getText());
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(IS_ATLAS_CONVERTED_ITEM, "true");
    palletContainer.getContainerItems().get(0).setContainerItemMiscInfo(containerItemMiscInfo);
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(palletContainer);
    when(containerAdjustmentHelper.validateContainerForAdjustment(
            any(Container.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(inventoryRestApiClient.notifyBackoutAdjustment(
            any(InventoryExceptionRequest.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(containerAdjustmentHelper.adjustReceipts(any(Container.class)))
        .thenReturn(getAdjustedReceipt());

    CancelContainerRequest cancelContainerRequest = new CancelContainerRequest();
    cancelContainerRequest.setTrackingIds(Collections.singletonList("lpn123"));

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_CANCEL_CONTAINER_OUTBOX_PATTERN_ENABLED,
            false))
        .thenReturn(true);

    Collection<OutboxEvent> outboxEvents = null;
    when(rdcReceivingUtils.buildOutboxEventsForCancelContainers(any(Container.class), any(), any()))
        .thenReturn(outboxEvents);
    doNothing()
        .when(rdcReceivingUtils)
        .postCancelContainersUpdates(any(), any(), any(), any(), any());
    when(labelDataService.findByTrackingId(anyString())).thenReturn(new LabelData());
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction().get());
    List<CancelContainerResponse> cancelContainersResponse =
        rdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertTrue(cancelContainersResponse.isEmpty());

    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(containerAdjustmentHelper, times(1))
        .validateContainerForAdjustment(any(Container.class), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(inventoryRestApiClient, times(1))
        .notifyBackoutAdjustment(any(InventoryExceptionRequest.class), any(HttpHeaders.class));
    verify(containerAdjustmentHelper, times(1)).adjustReceipts(any(Container.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_CANCEL_CONTAINER_OUTBOX_PATTERN_ENABLED,
            false);
    verify(rdcReceivingUtils, times(1))
        .postCancelContainersUpdates(any(), any(), any(), any(), any());
    verify(labelDataService, times(1)).findByTrackingIdIn(anyList());
    verify(rdcReceivingUtils, times(1))
        .buildOutboxEventsForCancelContainers(any(Container.class), any(), any());
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction().get());
  }

  @Test
  public void
      testCancelContainers_IsSuccess_WhenInventoryIntegrationIsEnabled_ItemIsDA_AndAtlasConvertedItemIsTrueAndOutboxEnable_ChildContainerExists()
          throws ReceivingException {
    Container parentContainer = createContainerWithChildContainers();
    List<String> parentTrackingIdList = new ArrayList<>();
    parentTrackingIdList.add("lpn1");
    List<String> childTrackingIdList = new ArrayList<>();
    childTrackingIdList.add("child1");
    childTrackingIdList.add("child2");

    LabelData labelData1 = new LabelData();
    labelData1.setTrackingId("child1");
    labelData1.setStatus("COMPLETE");
    LabelData labelData2 = new LabelData();
    labelData2.setTrackingId("child2");
    labelData2.setStatus("COMPLETE");
    List<LabelData> labelDataList = new ArrayList<>();
    labelDataList.add(labelData1);
    labelDataList.add(labelData2);

    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(IS_ATLAS_CONVERTED_ITEM, "true");
    parentContainer.getContainerItems().get(0).setContainerItemMiscInfo(containerItemMiscInfo);
    parentContainer.setContainerType(ContainerType.CASE.getText());
    parentContainer
        .getContainerItems()
        .get(0)
        .setInboundChannelMethod(PurchaseReferenceType.CROSSU.name());
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(parentContainer);
    when(containerAdjustmentHelper.validateContainerForAdjustment(
            any(Container.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(inventoryRestApiClient.notifyBackoutAdjustment(
            any(InventoryExceptionRequest.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(containerAdjustmentHelper.adjustReceipts(any(Container.class)))
        .thenReturn(getAdjustedReceipt());

    CancelContainerRequest cancelContainerRequest = new CancelContainerRequest();
    cancelContainerRequest.setTrackingIds(Collections.singletonList("lpn123"));

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_CANCEL_CONTAINER_OUTBOX_PATTERN_ENABLED,
            false))
        .thenReturn(true);

    Collection<OutboxEvent> outboxEvents = null;
    when(rdcReceivingUtils.buildOutboxEventsForCancelContainers(any(Container.class), any(), any()))
        .thenReturn(outboxEvents);
    doNothing()
        .when(rdcReceivingUtils)
        .postCancelContainersUpdates(any(), any(), any(), any(), any());
    when(labelDataService.findByTrackingIdIn(parentTrackingIdList)).thenReturn(new ArrayList<>());
    when(labelDataService.findByTrackingIdIn(childTrackingIdList)).thenReturn(labelDataList);
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction().get());
    List<CancelContainerResponse> cancelContainersResponse =
        rdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertTrue(cancelContainersResponse.isEmpty());

    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(containerAdjustmentHelper, times(1))
        .validateContainerForAdjustment(any(Container.class), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(inventoryRestApiClient, times(1))
        .notifyBackoutAdjustment(any(InventoryExceptionRequest.class), any(HttpHeaders.class));
    verify(containerAdjustmentHelper, times(1)).adjustReceipts(any(Container.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_CANCEL_CONTAINER_OUTBOX_PATTERN_ENABLED,
            false);
    verify(rdcReceivingUtils, times(1))
        .postCancelContainersUpdates(any(), any(), any(), any(), any());
    verify(labelDataService, times(2)).findByTrackingIdIn(anyList());
    verify(rdcReceivingUtils, times(1))
        .buildOutboxEventsForCancelContainers(any(Container.class), any(), any());
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction().get());
  }

  @Test
  public void
      testCancelContainers_IsSuccess_WhenInventoryIntegrationIsEnabled_ItemIsSSTK_AndAtlasConvertedItemIsTrue_InboundChannelRdc()
          throws ReceivingException {
    Container palletContainer = createContainer();
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(IS_ATLAS_CONVERTED_ITEM, "true");
    palletContainer.getContainerItems().get(0).setContainerItemMiscInfo(containerItemMiscInfo);
    palletContainer.getContainerItems().get(0).setInboundChannelMethod("CROSSU");
    palletContainer.getContainerItems().get(0).setVnpkQty(12);
    palletContainer.setContainerType(ContainerType.PALLET.getText());
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(palletContainer);
    when(containerAdjustmentHelper.validateContainerForAdjustment(
            any(Container.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(inventoryRestApiClient.notifyBackoutAdjustment(
            any(InventoryExceptionRequest.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(containerAdjustmentHelper.adjustReceipts(any(Container.class)))
        .thenReturn(getAdjustedReceipt());

    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction().get());
    doNothing()
        .when(rdcReceivingUtils)
        .postCancelContainersUpdates(any(), any(), any(), any(), any());
    doNothing()
        .when(rdcInstructionUtils)
        .publishInstructionToWft(
            any(Container.class),
            any(Integer.class),
            any(Integer.class),
            any(LabelAction.class),
            any(HttpHeaders.class));
    CancelContainerRequest cancelContainerRequest = new CancelContainerRequest();
    cancelContainerRequest.setTrackingIds(Collections.singletonList("lpn123"));

    List<CancelContainerResponse> cancelContainersResponse =
        rdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(containerAdjustmentHelper, times(1))
        .validateContainerForAdjustment(any(Container.class), any(HttpHeaders.class));
  }

  @Test
  public void
      testCancelContainers_IsSuccess_WhenInventoryIntegrationIsEnabled_ItemIsSSTK_AndAtlasConvertedItemIsTrue_InboundChannelDSDC()
          throws ReceivingException {
    Container palletContainer = createContainer();
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(IS_ATLAS_CONVERTED_ITEM, "true");
    palletContainer.getContainerItems().get(0).setContainerItemMiscInfo(containerItemMiscInfo);
    palletContainer.getContainerItems().get(0).setInboundChannelMethod("DSDC");
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(palletContainer);
    when(containerAdjustmentHelper.validateContainerForAdjustment(
            any(Container.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(inventoryRestApiClient.notifyBackoutAdjustment(
            any(InventoryExceptionRequest.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(containerAdjustmentHelper.adjustReceipts(any(Container.class)))
        .thenReturn(getAdjustedReceipt());

    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction().get());
    doNothing()
        .when(rdcReceivingUtils)
        .postCancelContainersUpdates(any(), any(), any(), any(), any());
    doNothing()
        .when(rdcInstructionUtils)
        .publishInstructionToWft(
            any(Container.class),
            any(Integer.class),
            any(Integer.class),
            any(LabelAction.class),
            any(HttpHeaders.class));
    CancelContainerRequest cancelContainerRequest = new CancelContainerRequest();
    cancelContainerRequest.setTrackingIds(Collections.singletonList("lpn123"));

    List<CancelContainerResponse> cancelContainersResponse =
        rdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertFalse(cancelContainersResponse.isEmpty());

    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(containerAdjustmentHelper, times(1))
        .validateContainerForAdjustment(any(Container.class), any(HttpHeaders.class));
  }

  @Test
  public void
      testCancelContainers_IsSuccess_When_Container_Created_Within_Three_Days_WhenInventoryIntegrationIsEnabled_ItemIsDA_()
          throws ReceivingException {
    Container container = createContainer();
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(IS_ATLAS_CONVERTED_ITEM, "true");
    container.getContainerItems().get(0).setContainerItemMiscInfo(containerItemMiscInfo);
    container.setContainerType(ContainerType.PALLET.getText());
    container.getContainerItems().get(0).setInboundChannelMethod("CROSSU");
    container.setCreateTs(new Date(124, 2, 18, 6, 0));
    doReturn(new JsonParser().parse("72"))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(
            TenantContext.getFacilityNum().toString(),
            String.valueOf(RdcConstants.MAX_ALLOWED_TIME_FOR_CONTAINER_BACKOUT));
    when(tenantSpecificConfigReader.getDCTimeZone(anyInt())).thenReturn("Asia/Kolkata");
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(container);
    when(containerAdjustmentHelper.validateContainerForAdjustment(
            any(Container.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(inventoryRestApiClient.notifyBackoutAdjustment(
            any(InventoryExceptionRequest.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(containerAdjustmentHelper.adjustReceipts(any(Container.class)))
        .thenReturn(getAdjustedReceipt());

    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction().get());
    doNothing()
        .when(rdcReceivingUtils)
        .postCancelContainersUpdates(any(), any(), any(), any(), any());
    doNothing()
        .when(rdcInstructionUtils)
        .publishInstructionToWft(
            any(Container.class),
            any(Integer.class),
            any(Integer.class),
            any(LabelAction.class),
            any(HttpHeaders.class));
    CancelContainerRequest cancelContainerRequest = new CancelContainerRequest();
    cancelContainerRequest.setTrackingIds(Collections.singletonList("lpn123"));

    List<CancelContainerResponse> cancelContainersResponse =
        rdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(containerAdjustmentHelper, times(1))
        .validateContainerForAdjustment(any(Container.class), any(HttpHeaders.class));
  }

  @Test
  public void
      testCancelContainers_IsFailure_When_Container_Created_Before_Three_Days_WhenInventoryIntegrationIsEnabled_ItemIsDA_()
          throws ReceivingException {
    Container container = createContainer();
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(IS_ATLAS_CONVERTED_ITEM, "true");
    container.getContainerItems().get(0).setContainerItemMiscInfo(containerItemMiscInfo);
    container.setContainerType(ContainerType.PALLET.getText());
    container.getContainerItems().get(0).setInboundChannelMethod("CROSSU");
    container.setCreateTs(new Date(124, 2, 14, 0, 0));
    when(tenantSpecificConfigReader.getDCTimeZone(anyInt())).thenReturn("Asia/Kolkata");
    doReturn(new JsonParser().parse("82"))
        .when(tenantSpecificConfigReader)
        .getCcmConfigValue(
            TenantContext.getFacilityNum().toString(),
            String.valueOf(RdcConstants.MAX_ALLOWED_TIME_FOR_CONTAINER_BACKOUT));
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(container);
    when(containerAdjustmentHelper.validateContainerForAdjustment(
            any(Container.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(inventoryRestApiClient.notifyBackoutAdjustment(
            any(InventoryExceptionRequest.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(containerAdjustmentHelper.adjustReceipts(any(Container.class)))
        .thenReturn(getAdjustedReceipt());

    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction().get());
    doNothing()
        .when(rdcReceivingUtils)
        .postCancelContainersUpdates(any(), any(), any(), any(), any());
    doNothing()
        .when(rdcInstructionUtils)
        .publishInstructionToWft(
            any(Container.class),
            any(Integer.class),
            any(Integer.class),
            any(LabelAction.class),
            any(HttpHeaders.class));
    CancelContainerRequest cancelContainerRequest = new CancelContainerRequest();
    cancelContainerRequest.setTrackingIds(Collections.singletonList("lpn123"));

    List<CancelContainerResponse> cancelContainersResponse =
        rdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(containerAdjustmentHelper, times(1))
        .validateContainerForAdjustment(any(Container.class), any(HttpHeaders.class));
  }

  @Test
  public void
      testCancelContainers_IsSuccess_WhenInventoryIntegrationIsEnabled_ItemIsDA_AndAtlasConvertedItemIsTrueAndOutboxEnableAndBreakPackConveyPicks()
          throws ReceivingException {
    Container palletContainer = createContainerWithStatusStarBackOut();
    palletContainer
        .getContainerItems()
        .get(0)
        .setInboundChannelMethod(PurchaseReferenceType.CROSSU.name());
    palletContainer.setContainerType(ContainerType.PALLET.getText());
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(IS_ATLAS_CONVERTED_ITEM, "true");
    containerItemMiscInfo.put(ReceivingConstants.PACK_TYPE_CODE, RdcConstants.BREAK_PACK_TYPE_CODE);
    containerItemMiscInfo.put(
        ReceivingConstants.HANDLING_CODE, RdcConstants.CONVEY_PICKS_HANDLING_CODE);
    palletContainer.getContainerItems().get(0).setContainerItemMiscInfo(containerItemMiscInfo);
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(palletContainer);
    when(containerAdjustmentHelper.validateContainerForAdjustment(
            any(Container.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(inventoryRestApiClient.notifyBackoutAdjustment(
            any(InventoryExceptionRequest.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(containerAdjustmentHelper.adjustReceipts(any(Container.class)))
        .thenReturn(getAdjustedReceipt());

    CancelContainerRequest cancelContainerRequest = new CancelContainerRequest();
    cancelContainerRequest.setTrackingIds(Collections.singletonList("lpn123"));

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_CANCEL_CONTAINER_OUTBOX_PATTERN_ENABLED,
            false))
        .thenReturn(true);

    Container containerByInstructionId = createContainer();
    containerByInstructionId.getContainerItems().get(0).setQuantity(12);
    containerByInstructionId.getContainerItems().get(0).setWhpkQty(12);
    when(containerPersisterService.getContainersByInstructionId(anyLong()))
        .thenReturn(Collections.singletonList(containerByInstructionId));
    Collection<OutboxEvent> outboxEvents = null;
    when(rdcReceivingUtils.buildOutboxEventsForCancelContainers(any(Container.class), any(), any()))
        .thenReturn(outboxEvents);
    doNothing()
        .when(rdcReceivingUtils)
        .postCancelContainersUpdates(any(), any(), any(), any(), any());
    when(labelDataService.findByTrackingId(anyString())).thenReturn(new LabelData());
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction().get());
    List<CancelContainerResponse> cancelContainersResponse =
        rdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertTrue(cancelContainersResponse.isEmpty());

    verify(containerPersisterService, times(1))
        .getContainerWithChildContainersExcludingChildContents(anyString());
    verify(containerAdjustmentHelper, times(1))
        .validateContainerForAdjustment(any(Container.class), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(inventoryRestApiClient, times(1))
        .notifyBackoutAdjustment(any(InventoryExceptionRequest.class), any(HttpHeaders.class));
    verify(containerAdjustmentHelper, times(1))
        .adjustReceipts(any(Container.class), anyInt(), anyInt());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_CANCEL_CONTAINER_OUTBOX_PATTERN_ENABLED,
            false);
    verify(rdcReceivingUtils, times(1))
        .postCancelContainersUpdates(any(), any(), any(), any(), any());
    verify(labelDataService, times(1)).findByTrackingIdIn(anyList());
    verify(rdcReceivingUtils, times(1))
        .buildOutboxEventsForCancelContainers(any(Container.class), any(), any());
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction().get());
  }

  @Test
  public void
      testCancelContainers_WhenInventoryIntegrationIsEnabled_ItemIsSSTK_AndAtlasConvertedItemIsTrue_WithParentTrackingId()
          throws ReceivingException {
    Container palletContainer = createContainer();
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(IS_ATLAS_CONVERTED_ITEM, "true");
    palletContainer.setParentTrackingId("lpn34623056353307423");
    palletContainer.getContainerItems().get(0).setContainerItemMiscInfo(containerItemMiscInfo);
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(palletContainer);
    when(containerAdjustmentHelper.validateContainerForAdjustment(
            any(Container.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(inventoryRestApiClient.notifyBackoutAdjustment(
            any(InventoryExceptionRequest.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(containerAdjustmentHelper.adjustReceipts(any(Container.class)))
        .thenReturn(getAdjustedReceipt());

    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction().get());
    doNothing()
        .when(rdcReceivingUtils)
        .postCancelContainersUpdates(any(), any(), any(), any(), any());
    doNothing()
        .when(rdcInstructionUtils)
        .publishInstructionToWft(
            any(Container.class),
            any(Integer.class),
            any(Integer.class),
            any(LabelAction.class),
            any(HttpHeaders.class));
    CancelContainerRequest cancelContainerRequest = new CancelContainerRequest();
    cancelContainerRequest.setTrackingIds(Collections.singletonList("lpn123"));

    List<CancelContainerResponse> cancelContainersResponse =
        rdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertNotNull(cancelContainersResponse);

    assertEquals(
        cancelContainersResponse.get(0).getErrorCode(),
        ReceivingException.CONTAINER_WITH_PARENT_ERROR_CODE);
  }

  @Test
  public void
      testCancelContainers_WhenInventoryIntegrationIsNotEnabled_ItemIsDA_Label_WithParentTrackingId()
          throws ReceivingException {
    Container container = createContainer();
    container
        .getContainerItems()
        .get(0)
        .setInboundChannelMethod(PurchaseReferenceType.CROSSU.name());
    container.setParentTrackingId("lpn34623056353307423");
    container.setContainerType(ContainerType.CASE.name());
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(container);
    when(containerAdjustmentHelper.validateContainerForAdjustment(
            any(Container.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);
    doNothing().when(nimRdsService).backoutDALabels(anyList(), any(HttpHeaders.class));
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction().get());

    CancelContainerRequest cancelContainerRequest = new CancelContainerRequest();
    cancelContainerRequest.setTrackingIds(Collections.singletonList("lpn123"));

    List<CancelContainerResponse> cancelContainersResponse =
        rdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertNotNull(cancelContainersResponse);

    assertEquals(
        cancelContainersResponse.get(0).getErrorCode(),
        ReceivingException.CONTAINER_WITH_PARENT_ERROR_CODE);
  }

  @Test
  public void
      testCancelContainers_WhenInventoryIntegrationIsNotEnabled_ItemIsDA_Pallet_WithParentTrackingId()
          throws ReceivingException {
    Container container = createContainer();
    container
        .getContainerItems()
        .get(0)
        .setInboundChannelMethod(PurchaseReferenceType.CROSSU.name());
    container.setParentTrackingId("lpn34623056353307423");
    container.setContainerType(ContainerType.PALLET.getText());
    when(containerPersisterService.getContainerWithChildContainersExcludingChildContents(
            anyString()))
        .thenReturn(container);
    when(containerAdjustmentHelper.validateContainerForAdjustment(
            any(Container.class), any(HttpHeaders.class)))
        .thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);
    doNothing().when(nimRdsService).quantityChange(anyInt(), anyString(), any(HttpHeaders.class));
    when(instructionPersisterService.getInstructionById(anyLong()))
        .thenReturn(getMockInstruction().get());

    CancelContainerRequest cancelContainerRequest = new CancelContainerRequest();
    cancelContainerRequest.setTrackingIds(Collections.singletonList("lpn123"));

    List<CancelContainerResponse> cancelContainersResponse =
        rdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertNotNull(cancelContainersResponse);

    assertEquals(
        cancelContainersResponse.get(0).getErrorCode(),
        ReceivingException.CONTAINER_WITH_PARENT_ERROR_CODE);
  }

  private Container getMockContainer() {
    Container container = new Container();
    container.setDeliveryNumber(123L);
    container.setInstructionId(123L);
    container.setTrackingId("12344");
    ContainerItem containerItem = new ContainerItem();
    containerItem.setInboundChannelMethod("CROSSU");
    container.setContainerItems(Collections.singletonList(containerItem));
    return container;
  }
}
