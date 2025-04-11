package com.walmart.move.nim.receiving.witron.service;

import static com.walmart.move.nim.receiving.core.client.move.Move.*;
import static com.walmart.move.nim.receiving.core.client.move.Move.OPEN;
import static com.walmart.move.nim.receiving.core.client.move.Move.PENDING;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.EACHES;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.VNPK;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.walmart.move.nim.receiving.core.client.dcfin.DCFinRestApiClient;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClient;
import com.walmart.move.nim.receiving.core.client.gls.GlsRestApiClient;
import com.walmart.move.nim.receiving.core.client.gls.model.GlsAdjustPayload;
import com.walmart.move.nim.receiving.core.client.inventory.InventoryRestApiClient;
import com.walmart.move.nim.receiving.core.client.itemconfig.ItemConfigApiClient;
import com.walmart.move.nim.receiving.core.client.move.Move;
import com.walmart.move.nim.receiving.core.client.move.MoveRestApiClient;
import com.walmart.move.nim.receiving.core.common.GdcPutawayPublisher;
import com.walmart.move.nim.receiving.core.common.MovePublisher;
import com.walmart.move.nim.receiving.core.common.ReceiptPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.CancelContainerRequest;
import com.walmart.move.nim.receiving.core.model.CancelContainerResponse;
import com.walmart.move.nim.receiving.core.model.SwapContainerRequest;
import com.walmart.move.nim.receiving.core.model.UpdateInstructionRequest;
import com.walmart.move.nim.receiving.core.model.inventory.InventoryContainerDetails;
import com.walmart.move.nim.receiving.core.repositories.ContainerRepository;
import com.walmart.move.nim.receiving.core.service.ContainerAdjustmentHelper;
import com.walmart.move.nim.receiving.core.service.ContainerAdjustmentValidator;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.core.service.InventoryService;
import com.walmart.move.nim.receiving.core.service.LocationService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.data.GdcHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.InstructionStatus;
import com.walmart.move.nim.receiving.utils.constants.PurchaseReferenceType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.witron.common.GDCFlagReader;
import com.walmart.move.nim.receiving.witron.mock.data.MockInstruction;
import java.util.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class GdcCancelContainerProcessorTest {
  @InjectMocks private GdcCancelContainerProcessor gdcCancelContainerProcessor;
  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private ContainerAdjustmentValidator containerAdjustmentValidator;
  @Mock private ReceiptService receiptService;
  @Mock private GDCFlagReader gdcFlagReader;
  @Mock private InventoryRestApiClient inventoryRestApiClient;
  @Mock private ContainerAdjustmentHelper containerAdjustmentHelper;
  @Mock private GdcPutawayPublisher gdcPutawayPublisher;
  @Mock private ReceiptPublisher receiptPublisher;
  @Mock private ItemConfigApiClient itemConfigApiClient;
  @Mock private MovePublisher movePublisher;
  @Mock private GlsRestApiClient glsRestApiClient;
  @Mock private DCFinRestApiClient dcfinRestApiClient;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private MoveRestApiClient mvClient;
  @Mock private InventoryService inventoryService;
  @Mock private LocationService locationService;

  @Mock private InstructionPersisterService instructionPersisterService;
  @Mock private InstructionHelperService instructionHelperService;
  @Spy private ContainerRepository containerRepository;

  @Mock private GDMRestApiClient gdmRestApiClient;

  public static final String UNLOADER = "UNLR"; // WFT to use this user Role for Performance for GDC

  private String trackingId = "A32612000000000001";
  private HttpHeaders httpHeaders = GdcHttpHeaders.getHeaders();
  private CancelContainerRequest cancelContainerRequest = new CancelContainerRequest();

  @BeforeMethod
  public void initMocks() {
    MockitoAnnotations.initMocks(this);

    TenantContext.setFacilityNum(32612);
    TenantContext.setFacilityCountryCode("US");
    cancelContainerRequest.setTrackingIds(Collections.singletonList(trackingId));
  }

  @AfterMethod
  public void resetMocks() {
    reset(
        mvClient,
        containerPersisterService,
        containerAdjustmentValidator,
        receiptService,
        gdcFlagReader,
        inventoryRestApiClient,
        containerAdjustmentHelper,
        gdcPutawayPublisher,
        receiptPublisher,
        itemConfigApiClient,
        movePublisher,
        glsRestApiClient,
        configUtils,
        dcfinRestApiClient);
  }

  private Container getContainerDetails() {
    Container container = new Container();
    container.setParentTrackingId(null);
    container.setTrackingId(trackingId);
    container.setDeliveryNumber(10912105L);
    container.setLocation("100");
    container.setInstructionId(1125L);

    ContainerItem containerItem = new ContainerItem();
    containerItem.setPurchaseReferenceNumber("1034508007");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setInboundChannelMethod(PurchaseReferenceType.SSTKU.name());
    containerItem.setItemNumber(9048195L);
    containerItem.setQuantity(12);
    containerItem.setQuantityUOM(EACHES);
    containerItem.setVnpkQty(12);
    containerItem.setWhpkQty(12);
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(USER_ROLE, UNLOADER);
    containerItem.setContainerItemMiscInfo(containerItemMiscInfo);
    container.setContainerItems(Collections.singletonList(containerItem));

    container.setPublishTs(new Date());
    container.setCompleteTs(new Date());
    container.setLastChangedUser("sysadmin");

    return container;
  }

  private Receipt getNegativeReceipt() {
    Receipt receipt = new Receipt();
    receipt.setDeliveryNumber(10912105L);
    receipt.setPurchaseReferenceNumber("1034508007");
    receipt.setPurchaseReferenceLineNumber(1);
    receipt.setQuantity(-1);
    receipt.setQuantityUom(VNPK);
    receipt.setVnpkQty(12);
    receipt.setWhpkQty(12);
    receipt.setEachQty(-12);
    receipt.setCreateUserId("sysadmin");

    return receipt;
  }

  @Test
  public void testCancelContainers_WhenContainerNotExists() throws ReceivingException {
    doReturn(null).when(containerPersisterService).getContainerDetailsWithoutChild(anyString());

    when(gdcFlagReader.publishVtrToWFTDisabled()).thenReturn(Boolean.TRUE);
    List<CancelContainerResponse> cancelContainersResponse =
        gdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertNotNull(cancelContainersResponse);
    verify(containerPersisterService, times(1)).getContainerDetailsWithoutChild(anyString());
    assertEquals(CONTAINER_NOT_FOUND_ERROR_CODE, cancelContainersResponse.get(0).getErrorCode());
    assertEquals(CONTAINER_NOT_FOUND_ERROR_MSG, cancelContainersResponse.get(0).getErrorMessage());
  }

  @Test
  public void testCancelContainers_WhenContainerAlreadyCanceled() throws ReceivingException {
    doReturn(getContainerDetails())
        .when(containerPersisterService)
        .getContainerDetailsWithoutChild(anyString());
    doReturn(
            new CancelContainerResponse(
                "A32612000000000001",
                CONTAINER_ALREADY_CANCELED_ERROR_CODE,
                CONTAINER_ALREADY_CANCELED_ERROR_MSG))
        .when(containerAdjustmentValidator)
        .validateContainerForAdjustment(any());
    when(gdcFlagReader.publishVtrToWFTDisabled()).thenReturn(Boolean.TRUE);
    List<CancelContainerResponse> cancelContainersResponse =
        gdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertNotNull(cancelContainersResponse);
    verify(containerPersisterService, times(1)).getContainerDetailsWithoutChild(anyString());
    verify(containerAdjustmentValidator, times(1)).validateContainerForAdjustment(any());
    assertEquals(
        CONTAINER_ALREADY_CANCELED_ERROR_CODE, cancelContainersResponse.get(0).getErrorCode());
    assertEquals(
        CONTAINER_ALREADY_CANCELED_ERROR_MSG, cancelContainersResponse.get(0).getErrorMessage());
  }

  @Test
  public void testCancelContainers_WhenPoFinalized() throws ReceivingException {
    doReturn(getContainerDetails())
        .when(containerPersisterService)
        .getContainerDetailsWithoutChild(anyString());
    doReturn(null).when(containerAdjustmentValidator).validateContainerForAdjustment(any());
    when(receiptService.isPOFinalized(anyString(), anyString())).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.publishVtrToWFTDisabled()).thenReturn(Boolean.TRUE);
    List<CancelContainerResponse> cancelContainersResponse =
        gdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertNotNull(cancelContainersResponse);
    verify(containerPersisterService, times(1)).getContainerDetailsWithoutChild(anyString());
    verify(containerAdjustmentValidator, times(1)).validateContainerForAdjustment(any());
    verify(receiptService, times(1)).isPOFinalized(anyString(), anyString());
    assertEquals(CONFIRM_PO_ERROR_CODE, cancelContainersResponse.get(0).getErrorCode());
    assertEquals(PO_ALREADY_FINALIZED, cancelContainersResponse.get(0).getErrorMessage());
  }

  @Test
  public void testCancelContainers_HandleAutomatedGdc() throws ReceivingException {
    doReturn(getContainerDetails())
        .when(containerPersisterService)
        .getContainerDetailsWithoutChild(anyString());
    doReturn(null).when(containerAdjustmentValidator).validateContainerForAdjustment(any());
    when(receiptService.isPOFinalized(anyString(), anyString())).thenReturn(Boolean.FALSE);
    when(gdcFlagReader.isManualGdcEnabled()).thenReturn(Boolean.FALSE);
    when(gdcFlagReader.publishVtrToWFTDisabled()).thenReturn(Boolean.TRUE);
    when(inventoryRestApiClient.notifyVtrToInventory(anyString(), any())).thenReturn(null);
    when(containerAdjustmentHelper.adjustReceipts(any())).thenReturn(getNegativeReceipt());
    doNothing().when(containerAdjustmentHelper).persistAdjustedReceiptsAndContainer(any(), any());
    doNothing().when(gdcPutawayPublisher).publishMessage(any(), anyString(), any());
    doNothing().when(receiptPublisher).publishReceiptUpdate(any(), any());

    List<CancelContainerResponse> cancelContainersResponse =
        gdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertTrue(cancelContainersResponse.isEmpty());
    verify(containerPersisterService, times(1)).getContainerDetailsWithoutChild(anyString());
    verify(containerAdjustmentValidator, times(1)).validateContainerForAdjustment(any());
    verify(receiptService, times(1)).isPOFinalized(anyString(), anyString());
    verify(inventoryRestApiClient, times(1)).notifyVtrToInventory(anyString(), any());
    verify(containerAdjustmentHelper, times(1)).adjustReceipts(any());
    verify(containerAdjustmentHelper, times(1)).persistAdjustedReceiptsAndContainer(any(), any());
    verify(gdcPutawayPublisher, times(1)).publishMessage(any(), anyString(), any());
    verify(receiptPublisher, times(1)).publishReceiptUpdate(any(), any());
  }

  @Test
  public void testCancelContainers_ManualGdcWithOneAtlasConvertedItem() throws ReceivingException {
    doReturn(getContainerDetails())
        .when(containerPersisterService)
        .getContainerDetailsWithoutChild(anyString());
    doReturn(null).when(containerAdjustmentValidator).validateContainerForAdjustment(any());
    when(receiptService.isPOFinalized(anyString(), anyString())).thenReturn(Boolean.FALSE);
    when(gdcFlagReader.isManualGdcEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.isDCOneAtlasEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.publishVtrToWFTDisabled()).thenReturn(Boolean.TRUE);
    when(itemConfigApiClient.isAtlasConvertedItem(anyLong(), any())).thenReturn(Boolean.TRUE);
    when(inventoryRestApiClient.notifyVtrToInventory(anyString(), any())).thenReturn(null);
    when(configUtils.getConfiguredFeatureFlag(
            anyString(), eq(PUBLISH_TO_DCFIN_ADJUSTMENTS_ENABLED), anyBoolean()))
        .thenReturn(true);
    doNothing().when(dcfinRestApiClient).adjustOrVtr(any(), any());
    doNothing().when(receiptPublisher).publishReceiptUpdate(any(), any());
    doNothing().when(movePublisher).publishCancelMove(anyString(), any());
    when(containerAdjustmentHelper.adjustReceipts(any())).thenReturn(getNegativeReceipt());
    doNothing().when(containerAdjustmentHelper).persistAdjustedReceiptsAndContainer(any(), any());

    when(configUtils.getCcmValue(anyInt(), anyString(), anyString())).thenReturn("");

    List<CancelContainerResponse> cancelContainersResponse =
        gdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertTrue(cancelContainersResponse.isEmpty());
    verify(containerPersisterService, times(1)).getContainerDetailsWithoutChild(anyString());
    verify(containerAdjustmentValidator, times(1)).validateContainerForAdjustment(any());
    verify(receiptService, times(1)).isPOFinalized(anyString(), anyString());
    verify(gdcFlagReader, times(1)).isManualGdcEnabled();
    verify(gdcFlagReader, times(1)).isDCOneAtlasEnabled();
    verify(itemConfigApiClient, times(1)).isAtlasConvertedItem(anyLong(), any());
    verify(inventoryRestApiClient, times(1)).notifyVtrToInventory(anyString(), any());
    verify(containerAdjustmentHelper, times(1)).adjustReceipts(any());
    verify(containerAdjustmentHelper, times(1)).persistAdjustedReceiptsAndContainer(any(), any());
    verify(receiptPublisher, times(1)).publishReceiptUpdate(any(), any());
    verify(dcfinRestApiClient, times(1)).adjustOrVtr(any(), any());
  }

  @Test
  public void testCancelContainers_validateAgainstInv_fail() throws ReceivingException {
    doReturn(getContainerDetails())
        .when(containerPersisterService)
        .getContainerDetailsWithoutChild(anyString());
    doReturn(null).when(containerAdjustmentValidator).validateContainerForAdjustment(any());
    when(receiptService.isPOFinalized(anyString(), anyString())).thenReturn(Boolean.FALSE);
    when(gdcFlagReader.isManualGdcEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.isDCOneAtlasEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.publishVtrToWFTDisabled()).thenReturn(Boolean.TRUE);
    when(itemConfigApiClient.isAtlasConvertedItem(anyLong(), any())).thenReturn(Boolean.TRUE);
    when(inventoryRestApiClient.notifyVtrToInventory(anyString(), any())).thenReturn(null);
    doNothing().when(receiptPublisher).publishReceiptUpdate(any(), any());
    doNothing().when(movePublisher).publishCancelMove(anyString(), any());
    when(containerAdjustmentHelper.adjustReceipts(any())).thenReturn(getNegativeReceipt());
    doNothing().when(containerAdjustmentHelper).persistAdjustedReceiptsAndContainer(any(), any());

    when(configUtils.getCcmValue(anyInt(), anyString(), anyString())).thenReturn("");

    when(configUtils.getConfiguredFeatureFlag(
            eq("32612"), eq(ENFORCE_INVENTORY_CHECK_FOR_VTR), eq(false)))
        .thenReturn(true);
    when(configUtils.getConfiguredFeatureFlag(
            eq("32612"), eq(ENFORCE_MOVES_CHECK_FOR_VTR), eq(false)))
        .thenReturn(true);
    InventoryContainerDetails inventoryContainerDetails = new InventoryContainerDetails();
    inventoryContainerDetails.setContainerStatus(AVAILABLE);
    inventoryContainerDetails.setAllocatedQty(1);
    doReturn(inventoryContainerDetails)
        .when(inventoryService)
        .getInventoryContainerDetails(anyString(), any(HttpHeaders.class));

    List<CancelContainerResponse> cancelContainersResponse =
        gdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);
    final CancelContainerResponse cancelContainerResponse = cancelContainersResponse.get(0);
    assertEquals(
        cancelContainerResponse.getErrorMessage(),
        "Cancel pallet cannot be performed as pallet is NOT in available status, please contact QA to do an adjustment");
  }

  @Test
  public void testCancelContainers_validateAgainstInv_pass() throws ReceivingException {
    doReturn(getContainerDetails())
        .when(containerPersisterService)
        .getContainerDetailsWithoutChild(anyString());
    doReturn(null).when(containerAdjustmentValidator).validateContainerForAdjustment(any());
    when(receiptService.isPOFinalized(anyString(), anyString())).thenReturn(Boolean.FALSE);
    when(gdcFlagReader.isManualGdcEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.isDCOneAtlasEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.publishVtrToWFTDisabled()).thenReturn(Boolean.TRUE);
    when(itemConfigApiClient.isAtlasConvertedItem(anyLong(), any())).thenReturn(Boolean.TRUE);
    when(inventoryRestApiClient.notifyVtrToInventory(anyString(), any())).thenReturn(null);
    doNothing().when(receiptPublisher).publishReceiptUpdate(any(), any());
    doNothing().when(movePublisher).publishCancelMove(anyString(), any());
    when(containerAdjustmentHelper.adjustReceipts(any())).thenReturn(getNegativeReceipt());
    doNothing().when(containerAdjustmentHelper).persistAdjustedReceiptsAndContainer(any(), any());

    when(configUtils.getConfiguredFeatureFlag(
            eq("32612"), eq(ENFORCE_INVENTORY_CHECK_FOR_VTR), eq(false)))
        .thenReturn(true);
    InventoryContainerDetails inventoryContainerDetails = new InventoryContainerDetails();
    inventoryContainerDetails.setContainerStatus(AVAILABLE);
    inventoryContainerDetails.setAllocatedQty(0);
    doReturn(inventoryContainerDetails)
        .when(inventoryService)
        .getInventoryContainerDetails(anyString(), any(HttpHeaders.class));

    when(configUtils.getConfiguredFeatureFlag(
            eq("32612"), eq(ENFORCE_MOVES_CHECK_FOR_VTR), eq(false)))
        .thenReturn(true);
    final Move move = new Move();
    move.setType(HAUL);
    move.setStatus(PENDING);
    final List<Move> moves = Collections.singletonList(move);
    doReturn(moves).when(mvClient).getMovesByContainerId(anyString(), any(HttpHeaders.class));

    List<CancelContainerResponse> cancelContainersResponse =
        gdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertTrue(cancelContainersResponse.isEmpty());
    verify(containerPersisterService, times(1)).getContainerDetailsWithoutChild(anyString());
    verify(containerAdjustmentValidator, times(1)).validateContainerForAdjustment(any());
    verify(receiptService, times(1)).isPOFinalized(anyString(), anyString());
    verify(gdcFlagReader, times(1)).isManualGdcEnabled();
    verify(gdcFlagReader, times(1)).isDCOneAtlasEnabled();
    verify(itemConfigApiClient, times(1)).isAtlasConvertedItem(anyLong(), any());
    verify(inventoryRestApiClient, times(1)).notifyVtrToInventory(anyString(), any());
    verify(containerAdjustmentHelper, times(1)).adjustReceipts(any());
    verify(containerAdjustmentHelper, times(1)).persistAdjustedReceiptsAndContainer(any(), any());
    verify(receiptPublisher, times(1)).publishReceiptUpdate(any(), any());
  }

  @Test
  public void testCancelContainers_Move_HAUL_Pending_NoPutaway_forVTR() throws ReceivingException {
    doReturn(getContainerDetails())
        .when(containerPersisterService)
        .getContainerDetailsWithoutChild(anyString());
    doReturn(null).when(containerAdjustmentValidator).validateContainerForAdjustment(any());
    when(receiptService.isPOFinalized(anyString(), anyString())).thenReturn(Boolean.FALSE);
    when(gdcFlagReader.isManualGdcEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.isDCOneAtlasEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.publishVtrToWFTDisabled()).thenReturn(Boolean.TRUE);
    when(itemConfigApiClient.isAtlasConvertedItem(anyLong(), any())).thenReturn(Boolean.TRUE);
    when(inventoryRestApiClient.notifyVtrToInventory(anyString(), any())).thenReturn(null);
    doNothing().when(receiptPublisher).publishReceiptUpdate(any(), any());
    doNothing().when(movePublisher).publishCancelMove(anyString(), any());
    when(containerAdjustmentHelper.adjustReceipts(any())).thenReturn(getNegativeReceipt());
    doNothing().when(containerAdjustmentHelper).persistAdjustedReceiptsAndContainer(any(), any());

    when(configUtils.getConfiguredFeatureFlag(
            eq("32612"), eq(ENFORCE_MOVES_CHECK_FOR_VTR), eq(false)))
        .thenReturn(true);

    final Move move = new Move();
    move.setType(HAUL);
    move.setStatus(PENDING);
    final List<Move> moves = Collections.singletonList(move);
    doReturn(moves).when(mvClient).getMovesByContainerId(anyString(), any(HttpHeaders.class));

    List<CancelContainerResponse> cancelContainersResponse =
        gdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertTrue(cancelContainersResponse.isEmpty());
    verify(containerPersisterService, times(1)).getContainerDetailsWithoutChild(anyString());
    verify(containerAdjustmentValidator, times(1)).validateContainerForAdjustment(any());
    verify(receiptService, times(1)).isPOFinalized(anyString(), anyString());
    verify(gdcFlagReader, times(1)).isManualGdcEnabled();
    verify(gdcFlagReader, times(1)).isDCOneAtlasEnabled();
    verify(itemConfigApiClient, times(1)).isAtlasConvertedItem(anyLong(), any());
    verify(inventoryRestApiClient, times(1)).notifyVtrToInventory(anyString(), any());
    verify(containerAdjustmentHelper, times(1)).adjustReceipts(any());
    verify(containerAdjustmentHelper, times(1)).persistAdjustedReceiptsAndContainer(any(), any());
    verify(receiptPublisher, times(1)).publishReceiptUpdate(any(), any());
  }

  @Test
  public void testCancelContainers_haulCompleted_noPutaway() throws ReceivingException {
    doReturn(getContainerDetails())
        .when(containerPersisterService)
        .getContainerDetailsWithoutChild(anyString());
    doReturn(null).when(containerAdjustmentValidator).validateContainerForAdjustment(any());
    when(receiptService.isPOFinalized(anyString(), anyString())).thenReturn(Boolean.FALSE);
    when(gdcFlagReader.isManualGdcEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.isDCOneAtlasEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.publishVtrToWFTDisabled()).thenReturn(Boolean.TRUE);
    when(itemConfigApiClient.isAtlasConvertedItem(anyLong(), any())).thenReturn(Boolean.TRUE);
    when(inventoryRestApiClient.notifyVtrToInventory(anyString(), any())).thenReturn(null);
    doNothing().when(receiptPublisher).publishReceiptUpdate(any(), any());
    doNothing().when(movePublisher).publishCancelMove(anyString(), any());
    when(containerAdjustmentHelper.adjustReceipts(any())).thenReturn(getNegativeReceipt());
    doNothing().when(containerAdjustmentHelper).persistAdjustedReceiptsAndContainer(any(), any());
    // MOVES
    when(configUtils.getConfiguredFeatureFlag(
            eq("32612"), eq(ENFORCE_MOVES_CHECK_FOR_VTR), eq(false)))
        .thenReturn(true);
    final Move move = new Move();
    move.setType(HAUL);
    move.setStatus(COMPLETED);
    doReturn(Collections.singletonList(move))
        .when(mvClient)
        .getMovesByContainerId(anyString(), any(HttpHeaders.class));

    List<CancelContainerResponse> cancelContainersResponse =
        gdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    final CancelContainerResponse cancelContainerResponse = cancelContainersResponse.get(0);
    assertEquals(
        cancelContainerResponse.getErrorMessage(),
        "Pallet has been moved off of the dock and cancel pallet can not be performed.");
  }

  @Test
  public void testCancelContainers_noHaul_NoHaul_And_PutawayOpen_forVTR()
      throws ReceivingException {
    doReturn(getContainerDetails())
        .when(containerPersisterService)
        .getContainerDetailsWithoutChild(anyString());
    doReturn(null).when(containerAdjustmentValidator).validateContainerForAdjustment(any());
    when(receiptService.isPOFinalized(anyString(), anyString())).thenReturn(Boolean.FALSE);
    when(gdcFlagReader.isManualGdcEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.isDCOneAtlasEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.publishVtrToWFTDisabled()).thenReturn(Boolean.TRUE);
    when(itemConfigApiClient.isAtlasConvertedItem(anyLong(), any())).thenReturn(Boolean.TRUE);
    when(inventoryRestApiClient.notifyVtrToInventory(anyString(), any())).thenReturn(null);
    doNothing().when(receiptPublisher).publishReceiptUpdate(any(), any());
    doNothing().when(movePublisher).publishCancelMove(anyString(), any());
    when(containerAdjustmentHelper.adjustReceipts(any())).thenReturn(getNegativeReceipt());
    doNothing().when(containerAdjustmentHelper).persistAdjustedReceiptsAndContainer(any(), any());

    // MOVES
    when(configUtils.getConfiguredFeatureFlag(
            eq("32612"), eq(ENFORCE_MOVES_CHECK_FOR_VTR), eq(false)))
        .thenReturn(true);
    final Move move = new Move();
    move.setType(PUTAWAY);
    move.setStatus(OPEN);
    doReturn(Collections.singletonList(move))
        .when(mvClient)
        .getMovesByContainerId(anyString(), any(HttpHeaders.class));

    List<CancelContainerResponse> cancelContainersResponse =
        gdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertTrue(cancelContainersResponse.isEmpty());

    verify(containerPersisterService, times(1)).getContainerDetailsWithoutChild(anyString());
    verify(containerAdjustmentValidator, times(1)).validateContainerForAdjustment(any());
    verify(receiptService, times(1)).isPOFinalized(anyString(), anyString());
    verify(gdcFlagReader, times(1)).isManualGdcEnabled();
    verify(gdcFlagReader, times(1)).isDCOneAtlasEnabled();
    verify(itemConfigApiClient, times(1)).isAtlasConvertedItem(anyLong(), any());

    verify(inventoryRestApiClient, times(1)).notifyVtrToInventory(anyString(), any());
    verify(containerAdjustmentHelper, times(1)).adjustReceipts(any());
    verify(containerAdjustmentHelper, times(1)).persistAdjustedReceiptsAndContainer(any(), any());
    verify(receiptPublisher, times(1)).publishReceiptUpdate(any(), any());
  }

  @Test
  public void testCancelContainers_noHaul_But_PutawayPENDING_forVTR() throws ReceivingException {
    doReturn(getContainerDetails())
        .when(containerPersisterService)
        .getContainerDetailsWithoutChild(anyString());
    doReturn(null).when(containerAdjustmentValidator).validateContainerForAdjustment(any());
    when(receiptService.isPOFinalized(anyString(), anyString())).thenReturn(Boolean.FALSE);
    when(gdcFlagReader.isManualGdcEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.isDCOneAtlasEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.publishVtrToWFTDisabled()).thenReturn(Boolean.TRUE);
    when(itemConfigApiClient.isAtlasConvertedItem(anyLong(), any())).thenReturn(Boolean.TRUE);
    when(inventoryRestApiClient.notifyVtrToInventory(anyString(), any())).thenReturn(null);
    doNothing().when(receiptPublisher).publishReceiptUpdate(any(), any());
    doNothing().when(movePublisher).publishCancelMove(anyString(), any());
    when(containerAdjustmentHelper.adjustReceipts(any())).thenReturn(getNegativeReceipt());
    doNothing().when(containerAdjustmentHelper).persistAdjustedReceiptsAndContainer(any(), any());

    // MOVES
    when(configUtils.getConfiguredFeatureFlag(
            eq("32612"), eq(ENFORCE_MOVES_CHECK_FOR_VTR), eq(false)))
        .thenReturn(true);
    final Move move = new Move();
    move.setType(PUTAWAY);
    move.setStatus(PENDING);
    doReturn(Collections.singletonList(move))
        .when(mvClient)
        .getMovesByContainerId(anyString(), any(HttpHeaders.class));

    List<CancelContainerResponse> cancelContainersResponse =
        gdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertTrue(cancelContainersResponse.isEmpty());

    verify(containerPersisterService, times(1)).getContainerDetailsWithoutChild(anyString());
    verify(containerAdjustmentValidator, times(1)).validateContainerForAdjustment(any());
    verify(receiptService, times(1)).isPOFinalized(anyString(), anyString());
    verify(gdcFlagReader, times(1)).isManualGdcEnabled();
    verify(gdcFlagReader, times(1)).isDCOneAtlasEnabled();
    verify(itemConfigApiClient, times(1)).isAtlasConvertedItem(anyLong(), any());

    verify(inventoryRestApiClient, times(1)).notifyVtrToInventory(anyString(), any());
    verify(containerAdjustmentHelper, times(1)).adjustReceipts(any());
    verify(containerAdjustmentHelper, times(1)).persistAdjustedReceiptsAndContainer(any(), any());
    verify(receiptPublisher, times(1)).publishReceiptUpdate(any(), any());
  }

  @Test
  public void testCancelContainers_HaulCancelled_PutawayCancelled_forVTR()
      throws ReceivingException {
    doReturn(getContainerDetails())
        .when(containerPersisterService)
        .getContainerDetailsWithoutChild(anyString());
    doReturn(null).when(containerAdjustmentValidator).validateContainerForAdjustment(any());
    when(receiptService.isPOFinalized(anyString(), anyString())).thenReturn(Boolean.FALSE);
    when(gdcFlagReader.isManualGdcEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.isDCOneAtlasEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.publishVtrToWFTDisabled()).thenReturn(Boolean.TRUE);
    when(itemConfigApiClient.isAtlasConvertedItem(anyLong(), any())).thenReturn(Boolean.TRUE);
    when(inventoryRestApiClient.notifyVtrToInventory(anyString(), any())).thenReturn(null);
    doNothing().when(receiptPublisher).publishReceiptUpdate(any(), any());
    doNothing().when(movePublisher).publishCancelMove(anyString(), any());
    when(containerAdjustmentHelper.adjustReceipts(any())).thenReturn(getNegativeReceipt());
    doNothing().when(containerAdjustmentHelper).persistAdjustedReceiptsAndContainer(any(), any());
    // MOVES
    when(configUtils.getConfiguredFeatureFlag(
            eq("32612"), eq(ENFORCE_MOVES_CHECK_FOR_VTR), eq(false)))
        .thenReturn(true);
    final Move mv1 = new Move();
    mv1.setType(HAUL);
    mv1.setStatus(CANCELLED);
    final Move mv2 = new Move();
    mv2.setType(PUTAWAY);
    mv2.setStatus(CANCELLED);
    final List<Move> moves = Arrays.asList(mv1, mv2);
    when(mvClient.getMovesByContainerId(anyString(), any(HttpHeaders.class))).thenReturn(moves);

    List<CancelContainerResponse> cancelContainersResponse =
        gdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertTrue(cancelContainersResponse.isEmpty());

    verify(containerPersisterService, times(1)).getContainerDetailsWithoutChild(anyString());
    verify(containerAdjustmentValidator, times(1)).validateContainerForAdjustment(any());
    verify(receiptService, times(1)).isPOFinalized(anyString(), anyString());
    verify(gdcFlagReader, times(1)).isManualGdcEnabled();
    verify(gdcFlagReader, times(1)).isDCOneAtlasEnabled();
    verify(itemConfigApiClient, times(1)).isAtlasConvertedItem(anyLong(), any());

    verify(inventoryRestApiClient, times(1)).notifyVtrToInventory(anyString(), any());
    verify(containerAdjustmentHelper, times(1)).adjustReceipts(any());
    verify(containerAdjustmentHelper, times(1)).persistAdjustedReceiptsAndContainer(any(), any());
    verify(receiptPublisher, times(1)).publishReceiptUpdate(any(), any());
  }

  @Test
  public void testCancelContainers_HaulOpen_PutawayCancelled_forVTR() throws ReceivingException {
    doReturn(getContainerDetails())
        .when(containerPersisterService)
        .getContainerDetailsWithoutChild(anyString());
    doReturn(null).when(containerAdjustmentValidator).validateContainerForAdjustment(any());
    when(receiptService.isPOFinalized(anyString(), anyString())).thenReturn(Boolean.FALSE);
    when(gdcFlagReader.isManualGdcEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.isDCOneAtlasEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.publishVtrToWFTDisabled()).thenReturn(Boolean.TRUE);
    when(itemConfigApiClient.isAtlasConvertedItem(anyLong(), any())).thenReturn(Boolean.TRUE);
    when(inventoryRestApiClient.notifyVtrToInventory(anyString(), any())).thenReturn(null);
    doNothing().when(receiptPublisher).publishReceiptUpdate(any(), any());
    doNothing().when(movePublisher).publishCancelMove(anyString(), any());
    when(containerAdjustmentHelper.adjustReceipts(any())).thenReturn(getNegativeReceipt());
    doNothing().when(containerAdjustmentHelper).persistAdjustedReceiptsAndContainer(any(), any());

    // MOVES
    when(configUtils.getConfiguredFeatureFlag(
            eq("32612"), eq(ENFORCE_MOVES_CHECK_FOR_VTR), eq(false)))
        .thenReturn(true);
    final Move mv1 = new Move();
    mv1.setType(HAUL);
    mv1.setStatus(OPEN);
    final Move mv2 = new Move();
    mv2.setType(PUTAWAY);
    mv2.setStatus(CANCELLED);
    final List<Move> moves = Arrays.asList(mv1, mv2);
    when(mvClient.getMovesByContainerId(anyString(), any(HttpHeaders.class))).thenReturn(moves);

    List<CancelContainerResponse> cancelContainersResponse =
        gdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertTrue(cancelContainersResponse.isEmpty());

    verify(containerPersisterService, times(1)).getContainerDetailsWithoutChild(anyString());
    verify(containerAdjustmentValidator, times(1)).validateContainerForAdjustment(any());
    verify(receiptService, times(1)).isPOFinalized(anyString(), anyString());
    verify(gdcFlagReader, times(1)).isManualGdcEnabled();
    verify(gdcFlagReader, times(1)).isDCOneAtlasEnabled();
    verify(itemConfigApiClient, times(1)).isAtlasConvertedItem(anyLong(), any());

    verify(inventoryRestApiClient, times(1)).notifyVtrToInventory(anyString(), any());
    verify(containerAdjustmentHelper, times(1)).adjustReceipts(any());
    verify(containerAdjustmentHelper, times(1)).persistAdjustedReceiptsAndContainer(any(), any());
    verify(receiptPublisher, times(1)).publishReceiptUpdate(any(), any());
  }

  @Test
  public void testCancelContainers_HaulCancelled_PutawayPending_forVTR() throws ReceivingException {
    doReturn(getContainerDetails())
        .when(containerPersisterService)
        .getContainerDetailsWithoutChild(anyString());
    doReturn(null).when(containerAdjustmentValidator).validateContainerForAdjustment(any());
    when(receiptService.isPOFinalized(anyString(), anyString())).thenReturn(Boolean.FALSE);
    when(gdcFlagReader.isManualGdcEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.isDCOneAtlasEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.publishVtrToWFTDisabled()).thenReturn(Boolean.TRUE);
    when(itemConfigApiClient.isAtlasConvertedItem(anyLong(), any())).thenReturn(Boolean.TRUE);
    when(inventoryRestApiClient.notifyVtrToInventory(anyString(), any())).thenReturn(null);
    doNothing().when(receiptPublisher).publishReceiptUpdate(any(), any());
    doNothing().when(movePublisher).publishCancelMove(anyString(), any());
    when(containerAdjustmentHelper.adjustReceipts(any())).thenReturn(getNegativeReceipt());
    doNothing().when(containerAdjustmentHelper).persistAdjustedReceiptsAndContainer(any(), any());

    // MOVES
    when(configUtils.getConfiguredFeatureFlag(
            eq("32612"), eq(ENFORCE_MOVES_CHECK_FOR_VTR), eq(false)))
        .thenReturn(true);
    final Move mv1 = new Move();
    mv1.setType(HAUL);
    mv1.setStatus(CANCELLED);
    final Move mv2 = new Move();
    mv2.setType(PUTAWAY);
    mv2.setStatus(PENDING);
    final List<Move> moves = Arrays.asList(mv1, mv2);
    doReturn(moves).when(mvClient).getMoveContainerDetails(anyString(), any(HttpHeaders.class));

    List<CancelContainerResponse> cancelContainersResponse =
        gdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertTrue(cancelContainersResponse.isEmpty());

    verify(containerPersisterService, times(1)).getContainerDetailsWithoutChild(anyString());
    verify(containerAdjustmentValidator, times(1)).validateContainerForAdjustment(any());
    verify(receiptService, times(1)).isPOFinalized(anyString(), anyString());
    verify(gdcFlagReader, times(1)).isManualGdcEnabled();
    verify(gdcFlagReader, times(1)).isDCOneAtlasEnabled();
    verify(itemConfigApiClient, times(1)).isAtlasConvertedItem(anyLong(), any());

    verify(inventoryRestApiClient, times(1)).notifyVtrToInventory(anyString(), any());
    verify(containerAdjustmentHelper, times(1)).adjustReceipts(any());
    verify(containerAdjustmentHelper, times(1)).persistAdjustedReceiptsAndContainer(any(), any());
    verify(receiptPublisher, times(1)).publishReceiptUpdate(any(), any());
  }

  @Test
  public void testCancelContainers_HAUL_OnHold_NoPutaway() throws ReceivingException {
    doReturn(getContainerDetails())
        .when(containerPersisterService)
        .getContainerDetailsWithoutChild(anyString());
    doReturn(null).when(containerAdjustmentValidator).validateContainerForAdjustment(any());
    when(receiptService.isPOFinalized(anyString(), anyString())).thenReturn(Boolean.FALSE);
    when(gdcFlagReader.isManualGdcEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.isDCOneAtlasEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.publishVtrToWFTDisabled()).thenReturn(Boolean.TRUE);
    when(itemConfigApiClient.isAtlasConvertedItem(anyLong(), any())).thenReturn(Boolean.TRUE);
    when(inventoryRestApiClient.notifyVtrToInventory(anyString(), any())).thenReturn(null);
    doNothing().when(receiptPublisher).publishReceiptUpdate(any(), any());
    doNothing().when(movePublisher).publishCancelMove(anyString(), any());
    when(containerAdjustmentHelper.adjustReceipts(any())).thenReturn(getNegativeReceipt());
    doNothing().when(containerAdjustmentHelper).persistAdjustedReceiptsAndContainer(any(), any());

    // MOVES
    when(configUtils.getConfiguredFeatureFlag(
            eq("32612"), eq(ENFORCE_MOVES_CHECK_FOR_VTR), eq(false)))
        .thenReturn(true);
    final Move mv1 = new Move();
    mv1.setType(HAUL);
    mv1.setStatus(ONHOLD);
    doReturn(Arrays.asList(mv1))
        .when(mvClient)
        .getMovesByContainerId(anyString(), any(HttpHeaders.class));

    List<CancelContainerResponse> cancelContainersResponse =
        gdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertTrue(cancelContainersResponse.isEmpty());

    verify(containerPersisterService, times(1)).getContainerDetailsWithoutChild(anyString());
    verify(containerAdjustmentValidator, times(1)).validateContainerForAdjustment(any());
    verify(receiptService, times(1)).isPOFinalized(anyString(), anyString());
    verify(gdcFlagReader, times(1)).isManualGdcEnabled();
    verify(gdcFlagReader, times(1)).isDCOneAtlasEnabled();
    verify(itemConfigApiClient, times(1)).isAtlasConvertedItem(anyLong(), any());

    verify(inventoryRestApiClient, times(1)).notifyVtrToInventory(anyString(), any());
    verify(containerAdjustmentHelper, times(1)).adjustReceipts(any());
    verify(containerAdjustmentHelper, times(1)).persistAdjustedReceiptsAndContainer(any(), any());
    verify(receiptPublisher, times(1)).publishReceiptUpdate(any(), any());
  }

  @Test
  public void testCancelContainers_noHaul_NoHaul_And_NoPutawayOpen_forVTR()
      throws ReceivingException {
    doReturn(getContainerDetails())
        .when(containerPersisterService)
        .getContainerDetailsWithoutChild(anyString());
    doReturn(null).when(containerAdjustmentValidator).validateContainerForAdjustment(any());
    when(receiptService.isPOFinalized(anyString(), anyString())).thenReturn(Boolean.FALSE);
    when(gdcFlagReader.isManualGdcEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.isDCOneAtlasEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.publishVtrToWFTDisabled()).thenReturn(Boolean.TRUE);
    when(itemConfigApiClient.isAtlasConvertedItem(anyLong(), any())).thenReturn(Boolean.TRUE);
    when(inventoryRestApiClient.notifyVtrToInventory(anyString(), any())).thenReturn(null);
    doNothing().when(receiptPublisher).publishReceiptUpdate(any(), any());
    doNothing().when(movePublisher).publishCancelMove(anyString(), any());
    when(containerAdjustmentHelper.adjustReceipts(any())).thenReturn(getNegativeReceipt());
    doNothing().when(containerAdjustmentHelper).persistAdjustedReceiptsAndContainer(any(), any());

    when(configUtils.getCcmValue(eq(32612), eq(VALID_MOVE_STATUS_VTR), eq("")))
        .thenReturn("HaulCompleted");
    List<String> moveContainerDetailList = new ArrayList<>();
    moveContainerDetailList.add("FULLPULLOPEN".toLowerCase());
    doReturn(moveContainerDetailList)
        .when(mvClient)
        .getMoveContainerDetails(anyString(), any(HttpHeaders.class));

    List<CancelContainerResponse> cancelContainersResponse =
        gdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertTrue(cancelContainersResponse.isEmpty());

    verify(containerPersisterService, times(1)).getContainerDetailsWithoutChild(anyString());
    verify(containerAdjustmentValidator, times(1)).validateContainerForAdjustment(any());
    verify(receiptService, times(1)).isPOFinalized(anyString(), anyString());
    verify(gdcFlagReader, times(1)).isManualGdcEnabled();
    verify(gdcFlagReader, times(1)).isDCOneAtlasEnabled();
    verify(itemConfigApiClient, times(1)).isAtlasConvertedItem(anyLong(), any());

    verify(inventoryRestApiClient, times(1)).notifyVtrToInventory(anyString(), any());
    verify(containerAdjustmentHelper, times(1)).adjustReceipts(any());
    verify(containerAdjustmentHelper, times(1)).persistAdjustedReceiptsAndContainer(any(), any());
    verify(receiptPublisher, times(1)).publishReceiptUpdate(any(), any());
  }

  @Test
  public void testCancelContainers_noHaul_But_PutawayOpen_forVTR() throws ReceivingException {
    doReturn(getContainerDetails())
        .when(containerPersisterService)
        .getContainerDetailsWithoutChild(anyString());
    doReturn(null).when(containerAdjustmentValidator).validateContainerForAdjustment(any());
    when(receiptService.isPOFinalized(anyString(), anyString())).thenReturn(Boolean.FALSE);
    when(gdcFlagReader.isManualGdcEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.isDCOneAtlasEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.publishVtrToWFTDisabled()).thenReturn(Boolean.TRUE);
    when(itemConfigApiClient.isAtlasConvertedItem(anyLong(), any())).thenReturn(Boolean.TRUE);
    when(inventoryRestApiClient.notifyVtrToInventory(anyString(), any())).thenReturn(null);
    doNothing().when(receiptPublisher).publishReceiptUpdate(any(), any());
    doNothing().when(movePublisher).publishCancelMove(anyString(), any());
    when(containerAdjustmentHelper.adjustReceipts(any())).thenReturn(getNegativeReceipt());
    doNothing().when(containerAdjustmentHelper).persistAdjustedReceiptsAndContainer(any(), any());

    when(configUtils.getCcmValue(eq(32612), eq(VALID_MOVE_STATUS_VTR), eq("")))
        .thenReturn("HaulCompleted");
    List<String> moveContainerDetailList = new ArrayList<>();
    moveContainerDetailList.add(PUTAWAY + OPEN);
    moveContainerDetailList.add("FULLPULLOPEN".toLowerCase());
    doReturn(moveContainerDetailList)
        .when(mvClient)
        .getMoveContainerDetails(anyString(), any(HttpHeaders.class));

    List<CancelContainerResponse> cancelContainersResponse =
        gdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertTrue(cancelContainersResponse.isEmpty());

    verify(containerPersisterService, times(1)).getContainerDetailsWithoutChild(anyString());
    verify(containerAdjustmentValidator, times(1)).validateContainerForAdjustment(any());
    verify(receiptService, times(1)).isPOFinalized(anyString(), anyString());
    verify(gdcFlagReader, times(1)).isManualGdcEnabled();
    verify(gdcFlagReader, times(1)).isDCOneAtlasEnabled();
    verify(itemConfigApiClient, times(1)).isAtlasConvertedItem(anyLong(), any());

    verify(inventoryRestApiClient, times(1)).notifyVtrToInventory(anyString(), any());
    verify(containerAdjustmentHelper, times(1)).adjustReceipts(any());
    verify(containerAdjustmentHelper, times(1)).persistAdjustedReceiptsAndContainer(any(), any());
    verify(receiptPublisher, times(1)).publishReceiptUpdate(any(), any());
  }

  @Test
  public void testCancelContainers_Haul_Open_But_noPutaway_forVTR() throws ReceivingException {
    doReturn(getContainerDetails())
        .when(containerPersisterService)
        .getContainerDetailsWithoutChild(anyString());
    doReturn(null).when(containerAdjustmentValidator).validateContainerForAdjustment(any());
    when(receiptService.isPOFinalized(anyString(), anyString())).thenReturn(Boolean.FALSE);
    when(gdcFlagReader.isManualGdcEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.isDCOneAtlasEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.publishVtrToWFTDisabled()).thenReturn(Boolean.TRUE);
    when(itemConfigApiClient.isAtlasConvertedItem(anyLong(), any())).thenReturn(Boolean.TRUE);
    when(inventoryRestApiClient.notifyVtrToInventory(anyString(), any())).thenReturn(null);
    doNothing().when(receiptPublisher).publishReceiptUpdate(any(), any());
    doNothing().when(movePublisher).publishCancelMove(anyString(), any());
    when(containerAdjustmentHelper.adjustReceipts(any())).thenReturn(getNegativeReceipt());
    doNothing().when(containerAdjustmentHelper).persistAdjustedReceiptsAndContainer(any(), any());

    when(configUtils.getCcmValue(eq(32612), eq(VALID_MOVE_STATUS_VTR), eq("")))
        .thenReturn("HaulCompleted");
    List<String> moveContainerDetailList = new ArrayList<>();
    moveContainerDetailList.add(HAUL + OPEN);
    moveContainerDetailList.add(PUTAWAY + OPEN);
    doReturn(moveContainerDetailList)
        .when(mvClient)
        .getMoveContainerDetails(anyString(), any(HttpHeaders.class));

    List<CancelContainerResponse> cancelContainersResponse =
        gdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertTrue(cancelContainersResponse.isEmpty());

    verify(containerPersisterService, times(1)).getContainerDetailsWithoutChild(anyString());
    verify(containerAdjustmentValidator, times(1)).validateContainerForAdjustment(any());
    verify(receiptService, times(1)).isPOFinalized(anyString(), anyString());
    verify(gdcFlagReader, times(1)).isManualGdcEnabled();
    verify(gdcFlagReader, times(1)).isDCOneAtlasEnabled();
    verify(itemConfigApiClient, times(1)).isAtlasConvertedItem(anyLong(), any());

    verify(inventoryRestApiClient, times(1)).notifyVtrToInventory(anyString(), any());
    verify(containerAdjustmentHelper, times(1)).adjustReceipts(any());
    verify(containerAdjustmentHelper, times(1)).persistAdjustedReceiptsAndContainer(any(), any());
    verify(receiptPublisher, times(1)).publishReceiptUpdate(any(), any());
  }

  @Test
  public void testCancelContainers_HAUL_OnHold_Putaway_Cancelled() throws ReceivingException {
    doReturn(getContainerDetails())
        .when(containerPersisterService)
        .getContainerDetailsWithoutChild(anyString());
    doReturn(null).when(containerAdjustmentValidator).validateContainerForAdjustment(any());
    when(receiptService.isPOFinalized(anyString(), anyString())).thenReturn(Boolean.FALSE);
    when(gdcFlagReader.isManualGdcEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.isDCOneAtlasEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.publishVtrToWFTDisabled()).thenReturn(Boolean.TRUE);
    when(itemConfigApiClient.isAtlasConvertedItem(anyLong(), any())).thenReturn(Boolean.TRUE);
    when(inventoryRestApiClient.notifyVtrToInventory(anyString(), any())).thenReturn(null);
    doNothing().when(receiptPublisher).publishReceiptUpdate(any(), any());
    doNothing().when(movePublisher).publishCancelMove(anyString(), any());
    when(containerAdjustmentHelper.adjustReceipts(any())).thenReturn(getNegativeReceipt());
    doNothing().when(containerAdjustmentHelper).persistAdjustedReceiptsAndContainer(any(), any());

    // MOVES
    when(configUtils.getConfiguredFeatureFlag(
            eq("32612"), eq(ENFORCE_MOVES_CHECK_FOR_VTR), eq(false)))
        .thenReturn(true);
    final Move mv1 = new Move();
    mv1.setType(HAUL);
    mv1.setStatus(ONHOLD);
    doReturn(Arrays.asList(mv1))
        .when(mvClient)
        .getMovesByContainerId(anyString(), any(HttpHeaders.class));

    List<CancelContainerResponse> cancelContainersResponse =
        gdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertTrue(cancelContainersResponse.isEmpty());

    verify(containerPersisterService, times(1)).getContainerDetailsWithoutChild(anyString());
    verify(containerAdjustmentValidator, times(1)).validateContainerForAdjustment(any());
    verify(receiptService, times(1)).isPOFinalized(anyString(), anyString());
    verify(gdcFlagReader, times(1)).isManualGdcEnabled();
    verify(gdcFlagReader, times(1)).isDCOneAtlasEnabled();
    verify(itemConfigApiClient, times(1)).isAtlasConvertedItem(anyLong(), any());

    verify(inventoryRestApiClient, times(1)).notifyVtrToInventory(anyString(), any());
    verify(containerAdjustmentHelper, times(1)).adjustReceipts(any());
    verify(containerAdjustmentHelper, times(1)).persistAdjustedReceiptsAndContainer(any(), any());
    verify(receiptPublisher, times(1)).publishReceiptUpdate(any(), any());
  }

  @Test
  public void testCancelContainers_HAULOpen_NoPutaway() throws ReceivingException {
    doReturn(getContainerDetails())
        .when(containerPersisterService)
        .getContainerDetailsWithoutChild(anyString());
    doReturn(null).when(containerAdjustmentValidator).validateContainerForAdjustment(any());
    when(receiptService.isPOFinalized(anyString(), anyString())).thenReturn(Boolean.FALSE);
    when(gdcFlagReader.isManualGdcEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.isDCOneAtlasEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.publishVtrToWFTDisabled()).thenReturn(Boolean.TRUE);
    when(itemConfigApiClient.isAtlasConvertedItem(anyLong(), any())).thenReturn(Boolean.TRUE);
    when(inventoryRestApiClient.notifyVtrToInventory(anyString(), any())).thenReturn(null);
    doNothing().when(receiptPublisher).publishReceiptUpdate(any(), any());
    doNothing().when(movePublisher).publishCancelMove(anyString(), any());
    when(containerAdjustmentHelper.adjustReceipts(any())).thenReturn(getNegativeReceipt());
    doNothing().when(containerAdjustmentHelper).persistAdjustedReceiptsAndContainer(any(), any());

    // MOVES
    when(configUtils.getConfiguredFeatureFlag(
            eq("32612"), eq(ENFORCE_MOVES_CHECK_FOR_VTR), eq(false)))
        .thenReturn(true);
    final Move mv1 = new Move();
    mv1.setType(HAUL);
    mv1.setStatus(OPEN);
    doReturn(Arrays.asList(mv1))
        .when(mvClient)
        .getMovesByContainerId(anyString(), any(HttpHeaders.class));

    List<CancelContainerResponse> cancelContainersResponse =
        gdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertTrue(cancelContainersResponse.isEmpty());

    verify(containerPersisterService, times(1)).getContainerDetailsWithoutChild(anyString());
    verify(containerAdjustmentValidator, times(1)).validateContainerForAdjustment(any());
    verify(receiptService, times(1)).isPOFinalized(anyString(), anyString());
    verify(gdcFlagReader, times(1)).isManualGdcEnabled();
    verify(gdcFlagReader, times(1)).isDCOneAtlasEnabled();
    verify(itemConfigApiClient, times(1)).isAtlasConvertedItem(anyLong(), any());

    verify(inventoryRestApiClient, times(1)).notifyVtrToInventory(anyString(), any());
    verify(containerAdjustmentHelper, times(1)).adjustReceipts(any());
    verify(containerAdjustmentHelper, times(1)).persistAdjustedReceiptsAndContainer(any(), any());
    verify(receiptPublisher, times(1)).publishReceiptUpdate(any(), any());
  }

  @Test
  public void testCancelContainers_HAULWORKING_PUTAWAYOPEN_forVTR() throws ReceivingException {
    doReturn(getContainerDetails())
        .when(containerPersisterService)
        .getContainerDetailsWithoutChild(anyString());
    doReturn(null).when(containerAdjustmentValidator).validateContainerForAdjustment(any());
    when(receiptService.isPOFinalized(anyString(), anyString())).thenReturn(Boolean.FALSE);
    when(gdcFlagReader.isManualGdcEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.isDCOneAtlasEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.publishVtrToWFTDisabled()).thenReturn(Boolean.TRUE);
    when(itemConfigApiClient.isAtlasConvertedItem(anyLong(), any())).thenReturn(Boolean.TRUE);
    when(inventoryRestApiClient.notifyVtrToInventory(anyString(), any())).thenReturn(null);
    doNothing().when(receiptPublisher).publishReceiptUpdate(any(), any());
    doNothing().when(movePublisher).publishCancelMove(anyString(), any());
    when(containerAdjustmentHelper.adjustReceipts(any())).thenReturn(getNegativeReceipt());
    doNothing().when(containerAdjustmentHelper).persistAdjustedReceiptsAndContainer(any(), any());

    // MOVES
    when(configUtils.getConfiguredFeatureFlag(
            eq("32612"), eq(ENFORCE_MOVES_CHECK_FOR_VTR), eq(false)))
        .thenReturn(true);
    final Move mv1 = new Move();
    mv1.setType(HAUL);
    mv1.setStatus(WORKING);
    final Move mv2 = new Move();
    mv2.setType(PUTAWAY);
    mv2.setStatus(OPEN);
    final List<Move> moveList = Arrays.asList(mv1, mv2);
    doReturn(moveList).when(mvClient).getMovesByContainerId(anyString(), any(HttpHeaders.class));

    List<CancelContainerResponse> cancelContainersResponse =
        gdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertTrue(cancelContainersResponse.size() > 0);
    final CancelContainerResponse cancelContainerResponse = cancelContainersResponse.get(0);
    assertEquals(
        cancelContainerResponse.getErrorMessage(),
        "Pallet has been moved off of the dock and cancel pallet can not be performed.");
  }

  @Test
  public void testCancelContainers_HAULOPEN_PUTWAY_COMPLETED() throws ReceivingException {
    doReturn(getContainerDetails())
        .when(containerPersisterService)
        .getContainerDetailsWithoutChild(anyString());
    doReturn(null).when(containerAdjustmentValidator).validateContainerForAdjustment(any());
    when(receiptService.isPOFinalized(anyString(), anyString())).thenReturn(Boolean.FALSE);
    when(gdcFlagReader.isManualGdcEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.isDCOneAtlasEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.publishVtrToWFTDisabled()).thenReturn(Boolean.TRUE);
    when(itemConfigApiClient.isAtlasConvertedItem(anyLong(), any())).thenReturn(Boolean.TRUE);
    when(inventoryRestApiClient.notifyVtrToInventory(anyString(), any())).thenReturn(null);
    doNothing().when(receiptPublisher).publishReceiptUpdate(any(), any());
    doNothing().when(movePublisher).publishCancelMove(anyString(), any());
    when(containerAdjustmentHelper.adjustReceipts(any())).thenReturn(getNegativeReceipt());
    doNothing().when(containerAdjustmentHelper).persistAdjustedReceiptsAndContainer(any(), any());

    // MOVES
    when(configUtils.getConfiguredFeatureFlag(
            eq("32612"), eq(ENFORCE_MOVES_CHECK_FOR_VTR), eq(false)))
        .thenReturn(true);
    final Move mv1 = new Move();
    mv1.setType(HAUL);
    mv1.setStatus(OPEN);
    final Move mv2 = new Move();
    mv2.setType(PUTAWAY);
    mv2.setStatus(COMPLETED);
    doReturn(Arrays.asList(mv1, mv2))
        .when(mvClient)
        .getMovesByContainerId(anyString(), any(HttpHeaders.class));

    List<CancelContainerResponse> cancelContainersResponse =
        gdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertFalse(cancelContainersResponse.isEmpty());
    final CancelContainerResponse resp = cancelContainersResponse.get(0);
    assertNotNull(resp);
    assertEquals(
        resp.getErrorMessage(),
        "Pallet has been moved off of the dock and cancel pallet can not be performed.");
    assertEquals(resp.getErrorCode(), "Invalid Move status");
    assertEquals(resp.getTrackingId(), "A32612000000000001");
  }

  @Test
  public void testCancelContainers_ManualGdcWithOneAtlasNonConvertedItem()
      throws ReceivingException {
    doReturn(getContainerDetails())
        .when(containerPersisterService)
        .getContainerDetailsWithoutChild(anyString());
    doReturn(null).when(containerAdjustmentValidator).validateContainerForAdjustment(any());
    when(receiptService.isPOFinalized(anyString(), anyString())).thenReturn(Boolean.FALSE);
    when(gdcFlagReader.isManualGdcEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.isDCOneAtlasEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.publishVtrToWFTDisabled()).thenReturn(Boolean.TRUE);
    when(itemConfigApiClient.isAtlasConvertedItem(anyLong(), any())).thenReturn(Boolean.FALSE);
    when(glsRestApiClient.createGlsAdjustPayload(
            anyString(), anyString(), anyInt(), anyInt(), anyString()))
        .thenReturn(new GlsAdjustPayload());
    when(glsRestApiClient.adjustOrCancel(any(), any())).thenReturn(new GlsAdjustPayload());
    doNothing().when(dcfinRestApiClient).adjustOrVtr(any(), any());
    when(containerAdjustmentHelper.adjustReceipts(any())).thenReturn(getNegativeReceipt());
    doNothing().when(containerAdjustmentHelper).persistAdjustedReceiptsAndContainer(any(), any());

    List<CancelContainerResponse> cancelContainersResponse =
        gdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertTrue(cancelContainersResponse.isEmpty());
    verify(containerPersisterService, times(1)).getContainerDetailsWithoutChild(anyString());
    verify(containerAdjustmentValidator, times(1)).validateContainerForAdjustment(any());
    verify(receiptService, times(1)).isPOFinalized(anyString(), anyString());
    verify(gdcFlagReader, times(1)).isManualGdcEnabled();
    verify(gdcFlagReader, times(1)).isDCOneAtlasEnabled();
    verify(itemConfigApiClient, times(1)).isAtlasConvertedItem(anyLong(), any());
    verify(glsRestApiClient, times(1))
        .createGlsAdjustPayload(anyString(), anyString(), anyInt(), anyInt(), anyString());
    verify(glsRestApiClient, times(1)).adjustOrCancel(any(), any());
    verify(dcfinRestApiClient, times(1)).adjustOrVtr(any(), any());
    verify(containerAdjustmentHelper, times(1)).adjustReceipts(any());
    verify(containerAdjustmentHelper, times(1)).persistAdjustedReceiptsAndContainer(any(), any());
  }

  @Test
  public void testSwapCancelContainers() throws Exception {
    List<SwapContainerRequest> swapCancellist = new ArrayList<>();
    try {
      gdcCancelContainerProcessor.swapContainers(swapCancellist, httpHeaders);
    } catch (ReceivingInternalException ex) {
      assertEquals("GLS-RCV-CNF-500", ex.getErrorCode());
    }
  }

  @Test
  public void testCancelContainers_ManualGdcWithFullGLS() throws Exception {
    doReturn(getContainerDetails())
        .when(containerPersisterService)
        .getContainerDetailsWithoutChild(anyString());
    doReturn(null).when(containerAdjustmentValidator).validateContainerForAdjustment(any());
    when(receiptService.isPOFinalized(anyString(), anyString())).thenReturn(Boolean.FALSE);
    when(gdcFlagReader.isManualGdcEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.isDCOneAtlasEnabled()).thenReturn(Boolean.FALSE);
    when(gdcFlagReader.publishVtrToWFTDisabled()).thenReturn(Boolean.TRUE);
    when(glsRestApiClient.createGlsAdjustPayload(
            anyString(), anyString(), anyInt(), anyInt(), anyString()))
        .thenReturn(new GlsAdjustPayload());
    when(glsRestApiClient.adjustOrCancel(any(), any())).thenReturn(new GlsAdjustPayload());
    when(containerAdjustmentHelper.adjustReceipts(any())).thenReturn(getNegativeReceipt());
    doNothing().when(containerAdjustmentHelper).persistAdjustedReceiptsAndContainer(any(), any());

    when(configUtils.getConfiguredFeatureFlag(
            "32612", ReceivingConstants.SEND_UPDATE_EVENTS_TO_GDM, false))
        .thenReturn(true);

    List<CancelContainerResponse> cancelContainersResponse =
        gdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertTrue(cancelContainersResponse.isEmpty());
    verify(containerPersisterService, times(1)).getContainerDetailsWithoutChild(anyString());
    verify(containerAdjustmentValidator, times(1)).validateContainerForAdjustment(any());
    verify(receiptService, times(1)).isPOFinalized(anyString(), anyString());
    verify(gdcFlagReader, times(1)).isManualGdcEnabled();
    verify(gdcFlagReader, times(1)).isDCOneAtlasEnabled();
    verify(glsRestApiClient, times(1))
        .createGlsAdjustPayload(anyString(), anyString(), anyInt(), anyInt(), anyString());
    verify(glsRestApiClient, times(1)).adjustOrCancel(any(), any());
    verify(containerAdjustmentHelper, times(1)).adjustReceipts(any());
    verify(containerAdjustmentHelper, times(1)).persistAdjustedReceiptsAndContainer(any(), any());
    verify(gdmRestApiClient, times(1)).receivingToGDMEvent(any(), anyMap());
  }

  @Test
  public void testCancelContainers_ManualGdcWithOneAtlasConvertedItem_noMoves_Response()
      throws ReceivingException {
    doReturn(getContainerDetails())
        .when(containerPersisterService)
        .getContainerDetailsWithoutChild(anyString());
    doReturn(null).when(containerAdjustmentValidator).validateContainerForAdjustment(any());
    when(receiptService.isPOFinalized(anyString(), anyString())).thenReturn(Boolean.FALSE);
    when(gdcFlagReader.isManualGdcEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.isDCOneAtlasEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.publishVtrToWFTDisabled()).thenReturn(Boolean.TRUE);
    when(itemConfigApiClient.isAtlasConvertedItem(anyLong(), any())).thenReturn(Boolean.TRUE);
    when(inventoryRestApiClient.notifyVtrToInventory(anyString(), any())).thenReturn(null);
    doNothing().when(receiptPublisher).publishReceiptUpdate(any(), any());
    doNothing().when(movePublisher).publishCancelMove(anyString(), any());
    when(containerAdjustmentHelper.adjustReceipts(any())).thenReturn(getNegativeReceipt());
    doNothing().when(containerAdjustmentHelper).persistAdjustedReceiptsAndContainer(any(), any());

    when(configUtils.getConfiguredFeatureFlag(
            eq("32612"), eq(ENFORCE_MOVES_CHECK_FOR_VTR), eq(false)))
        .thenReturn(true);

    List<CancelContainerResponse> cancelContainersResponse =
        gdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertTrue(cancelContainersResponse.isEmpty());
    verify(containerPersisterService, times(1)).getContainerDetailsWithoutChild(anyString());
    verify(containerAdjustmentValidator, times(1)).validateContainerForAdjustment(any());
    verify(receiptService, times(1)).isPOFinalized(anyString(), anyString());
    verify(gdcFlagReader, times(1)).isManualGdcEnabled();
    verify(gdcFlagReader, times(1)).isDCOneAtlasEnabled();
    verify(itemConfigApiClient, times(1)).isAtlasConvertedItem(anyLong(), any());
    verify(inventoryRestApiClient, times(1)).notifyVtrToInventory(anyString(), any());
    verify(containerAdjustmentHelper, times(1)).adjustReceipts(any());
    verify(containerAdjustmentHelper, times(1)).persistAdjustedReceiptsAndContainer(any(), any());
    verify(receiptPublisher, times(1)).publishReceiptUpdate(any(), any());
  }

  @Test
  public void testCancelContainers_ManualGdcWithOssVtrEnabled() throws ReceivingException {
    doReturn(getContainerWithOSSTransferPO())
        .when(containerPersisterService)
        .getContainerDetailsWithoutChild(anyString());
    doReturn(null).when(containerAdjustmentValidator).validateContainerForAdjustment(any());
    when(receiptService.isPOFinalized(anyString(), anyString())).thenReturn(Boolean.FALSE);
    when(gdcFlagReader.isManualGdcEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.isDCOneAtlasEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.isOssVtrEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.publishVtrToWFTDisabled()).thenReturn(Boolean.TRUE);
    when(itemConfigApiClient.isAtlasConvertedItem(anyLong(), any())).thenReturn(Boolean.TRUE);
    when(configUtils.getCcmValue(anyInt(), anyString(), anyString())).thenReturn("");
    when(inventoryRestApiClient.notifyOssVtrToInventory(any(), any(), any())).thenReturn(null);
    doNothing().when(receiptPublisher).publishReceiptUpdate(any(), any());
    doNothing().when(movePublisher).publishCancelMove(anyString(), any());
    when(containerAdjustmentHelper.adjustReceipts(any())).thenReturn(getNegativeReceipt());
    doNothing().when(containerAdjustmentHelper).persistAdjustedReceiptsAndContainer(any(), any());

    List<CancelContainerResponse> cancelContainersResponse =
        gdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertTrue(cancelContainersResponse.isEmpty());
    verify(containerPersisterService, times(1)).getContainerDetailsWithoutChild(anyString());
    verify(containerAdjustmentValidator, times(1)).validateContainerForAdjustment(any());
    verify(receiptService, times(1)).isPOFinalized(anyString(), anyString());
    verify(gdcFlagReader, times(1)).isManualGdcEnabled();
    verify(gdcFlagReader, times(1)).isDCOneAtlasEnabled();
    verify(itemConfigApiClient, times(1)).isAtlasConvertedItem(anyLong(), any());
    verify(inventoryRestApiClient, times(1)).notifyOssVtrToInventory(any(), any(), any());
    verify(containerAdjustmentHelper, times(1)).adjustReceipts(any());
    verify(containerAdjustmentHelper, times(1)).persistAdjustedReceiptsAndContainer(any(), any());
    verify(receiptPublisher, times(1)).publishReceiptUpdate(any(), any());
  }

  @Test
  public void testCancelContainers_ManualGdcWithOssVtrDisabled() throws ReceivingException {
    doReturn(getContainerWithOSSTransferPO())
        .when(containerPersisterService)
        .getContainerDetailsWithoutChild(anyString());
    doReturn(null).when(containerAdjustmentValidator).validateContainerForAdjustment(any());
    when(receiptService.isPOFinalized(anyString(), anyString())).thenReturn(Boolean.FALSE);
    when(gdcFlagReader.isManualGdcEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.isDCOneAtlasEnabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.publishVtrToWFTDisabled()).thenReturn(Boolean.TRUE);
    when(gdcFlagReader.isOssVtrEnabled()).thenReturn(Boolean.FALSE);
    when(itemConfigApiClient.isAtlasConvertedItem(anyLong(), any())).thenReturn(Boolean.TRUE);
    when(configUtils.getCcmValue(anyInt(), anyString(), anyString())).thenReturn("");

    List<CancelContainerResponse> cancelContainersResponse =
        gdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertFalse(cancelContainersResponse.isEmpty());
    final CancelContainerResponse resp = cancelContainersResponse.get(0);
    assertNotNull(resp);
    assertEquals(resp.getErrorMessage(), "VTR not allowed on Outside Storage PO's");
    assertEquals(resp.getErrorCode(), "vtrError");
    assertEquals(resp.getTrackingId(), "A32612000000000001");
  }

  @Test
  public void testCancelContainers_AutomatedGdcWithOssVtrDisabled() throws ReceivingException {
    doReturn(getContainerWithOSSTransferPO())
        .when(containerPersisterService)
        .getContainerDetailsWithoutChild(anyString());
    doReturn(null).when(containerAdjustmentValidator).validateContainerForAdjustment(any());
    when(receiptService.isPOFinalized(anyString(), anyString())).thenReturn(Boolean.FALSE);
    when(gdcFlagReader.isManualGdcEnabled()).thenReturn(Boolean.FALSE);
    when(gdcFlagReader.isOssVtrEnabled()).thenReturn(Boolean.FALSE);
    when(gdcFlagReader.publishVtrToWFTDisabled()).thenReturn(Boolean.TRUE);
    when(configUtils.getCcmValue(anyInt(), anyString(), anyString())).thenReturn("");

    List<CancelContainerResponse> cancelContainersResponse =
        gdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertFalse(cancelContainersResponse.isEmpty());
    final CancelContainerResponse resp = cancelContainersResponse.get(0);
    assertNotNull(resp);
    assertEquals(resp.getErrorMessage(), "VTR not allowed on Outside Storage PO's");
    assertEquals(resp.getErrorCode(), "vtrError");
    assertEquals(resp.getTrackingId(), "A32612000000000001");
  }

  @Test
  public void testCancelContainers_PublishToWFT() throws ReceivingException {
    final String userId = "system";
    doReturn(getContainerDetails())
        .when(containerPersisterService)
        .getContainerDetailsWithoutChild(anyString());
    when(gdcFlagReader.publishVtrToWFTDisabled()).thenReturn(Boolean.FALSE);
    List<Long> instructionIds = new ArrayList<>();
    instructionIds.add(1125L);
    when(containerRepository.getInstructionIdsByTrackingIds(
            anyList(), any(Integer.class), any(String.class)))
        .thenReturn(instructionIds);
    Instruction instruction = MockInstruction.getInstruction();
    instruction.setCompleteTs(new Date());
    instruction.setReceivedQuantity(54);
    instruction.setCreateUserId(userId);
    when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instruction);
    List<CancelContainerResponse> cancelContainersResponse =
        gdcCancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    verify(instructionHelperService, times(1))
        .publishInstruction(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(Container.class),
            any(InstructionStatus.class),
            any(HttpHeaders.class));
  }

  private Container getContainerWithOSSTransferPO() {
    Container container = new Container();
    container.setParentTrackingId(null);
    container.setTrackingId(trackingId);
    container.setDeliveryNumber(10912105L);
    container.setLocation("100");
    container.setInstructionId(1125L);

    ContainerItem containerItem = new ContainerItem();
    containerItem.setPurchaseReferenceNumber("1034508007");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setInboundChannelMethod(PurchaseReferenceType.SSTKU.name());
    containerItem.setItemNumber(9048195L);
    containerItem.setQuantity(12);
    containerItem.setQuantityUOM(EACHES);
    containerItem.setVnpkQty(12);
    containerItem.setWhpkQty(12);

    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(TO_SUBCENTER, "1");
    containerItemMiscInfo.put(FROM_SUBCENTER, "5");
    containerItemMiscInfo.put(PO_TYPE, "28");
    containerItemMiscInfo.put(IS_RECEIVE_FROM_OSS, TRUE_STRING);
    containerItem.setContainerItemMiscInfo(containerItemMiscInfo);
    container.setContainerItems(Collections.singletonList(containerItem));

    container.setPublishTs(new Date());
    container.setCompleteTs(new Date());
    container.setLastChangedUser("sysadmin");

    return container;
  }
}
