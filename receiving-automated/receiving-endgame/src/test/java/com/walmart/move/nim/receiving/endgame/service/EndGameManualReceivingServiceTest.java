package com.walmart.move.nim.receiving.endgame.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.AUDIT_V2_ENABLED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ENABLE_AUDIT_REQUIRED_FLAG;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_CREATE_CONTAINER_IN_SYNC_FOR_INVENTORY;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.MABD_DEFAULT_NO_OF_DAYS;
import static io.strati.libs.commons.lang.StringUtils.EMPTY;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;

import com.google.gson.Gson;
import com.walmart.atlas.global.fc.service.IOutboxPublisherService;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.MovePublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingConflictException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.framework.transformer.Transformer;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrderLine;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.core.transformer.ContainerTransformer;
import com.walmart.move.nim.receiving.endgame.common.AuditHelper;
import com.walmart.move.nim.receiving.endgame.common.EndGameUtilsTestCase;
import com.walmart.move.nim.receiving.endgame.config.EndgameManagedConfig;
import com.walmart.move.nim.receiving.endgame.constants.LabelStatus;
import com.walmart.move.nim.receiving.endgame.entity.PreLabelData;
import com.walmart.move.nim.receiving.endgame.message.common.ScanEventData;
import com.walmart.move.nim.receiving.endgame.mock.data.MockMessageData;
import com.walmart.move.nim.receiving.endgame.model.ReceivingRequest;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class EndGameManualReceivingServiceTest extends ReceivingTestBase {
  private Gson gson;
  @InjectMocks private EndGameReceivingService endGameReceivingService;
  @InjectMocks private EndGameManualReceivingService endGameManualReceivingService;
  @Mock private EndgameOutboxHandler endGameOutboxHandler;
  private Transformer<Container, ContainerDTO> transformer;

  @Mock private ReceiptService receiptService;
  @Mock private EndGameDeliveryService endGameDeliveryService;
  @Mock private EndgameContainerService endgameContainerService;
  @Mock private DCFinServiceV2 dcFinServiceV2;
  @Mock private EndGameSlottingService endGameSlottingService;
  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private AppConfig appConfig;
  @Mock private MovePublisher movePublisher;
  @Mock private DeliveryMetaDataService deliveryMetaDataService;
  @Mock private ContainerService containerService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private InventoryService inventoryService;
  @Mock private AuditHelper auditHelper;
  @Mock private EndGameLabelingService endGameLabelingService;
  @MockBean private IOutboxPublisherService iOutboxPublisherService;
  private EndgameManagedConfig endgameManagedConfig;

  @BeforeClass
  public void setRootUp() {
    gson = new Gson();
    endgameManagedConfig = new EndgameManagedConfig();
    transformer = new ContainerTransformer();
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(endgameManagedConfig, "nosUPCForBulkScan", 1);
    ReflectionTestUtils.setField(endgameManagedConfig, "printerFormatName", "rcv_tpl_eg_zeb");
    ReflectionTestUtils.setField(
            endgameManagedConfig, "walmartDefaultSellerId", "F55CDC31AB754BB68FE0B39041159D63");
    ReflectionTestUtils.setField(endGameReceivingService, "gson", gson);
    ReflectionTestUtils.setField(endGameManualReceivingService, "gson", gson);
    ReflectionTestUtils.setField(
            endGameReceivingService, "endgameManagedConfig", endgameManagedConfig);
    ReflectionTestUtils.setField(
            endGameManualReceivingService, "endgameManagedConfig", endgameManagedConfig);
    ReflectionTestUtils.setField(
            endGameReceivingService, "endGameDeliveryService", endGameDeliveryService);
    ReflectionTestUtils.setField(
            endGameManualReceivingService, "endGameDeliveryService", endGameDeliveryService);
    ReflectionTestUtils.setField(endGameReceivingService, "transformer", transformer);
    ReflectionTestUtils.setField(endGameManualReceivingService, "transformer", transformer);
    ReflectionTestUtils.setField(
            endGameOutboxHandler, "iOutboxPublisherService", iOutboxPublisherService);
    TenantContext.setFacilityNum(54321);
    TenantContext.setFacilityCountryCode("us");
    TenantContext.setCorrelationId("abc");
  }

  @AfterMethod
  public void resetMocks() {
    reset(receiptService);
    reset(endGameDeliveryService);
    reset(endgameContainerService);
    reset(dcFinServiceV2);
    reset(containerService);
    reset(endGameSlottingService);
    reset(containerPersisterService);
    reset(deliveryMetaDataService);
    reset(movePublisher);
    reset(appConfig);
    reset(tenantSpecificConfigReader);
    reset(inventoryService);
    reset(endGameLabelingService);
  }

  @Test
  public void testReceiveForUPCOnOnePOLine() throws ReceivingException {
    Container container = EndGameUtilsTestCase.getContainer();
    List<ContainerItem> containerItems = EndGameUtilsTestCase.getContainerItems();
    container.setContainerItems(containerItems);
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
            .thenReturn(EndGameUtilsTestCase.getUPCOnOnePOLine());
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
            .thenReturn(EndGameUtilsTestCase.getReceiptSummary_0664420451());
    when(endGameSlottingService.findByCaseUPC(anyString()))
            .thenReturn(EndGameUtilsTestCase.getSingleSlottingDestination());
    when(endgameContainerService.getContainer(
            any(ScanEventData.class),
            any(PurchaseOrder.class),
            any(PurchaseOrderLine.class),
            anyInt()))
            .thenReturn(container);
    when(endgameContainerService.createAndSaveContainerAndReceipt(
            any(ScanEventData.class),
            any(PurchaseOrder.class),
            any(PurchaseOrderLine.class),
            anyInt(),
            eq(container)))
            .thenReturn(container);
    when(inventoryService.createContainers(anyList())).thenReturn(EMPTY);
    ReceivingRequest scanEventData = MockMessageData.getMockReceivingRequest();
    assertNotNull(tenantSpecificConfigReader.getConfiguredFeatureFlag(anyString()));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            eq(IS_CREATE_CONTAINER_IN_SYNC_FOR_INVENTORY)))
            .thenReturn(true);
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    endGameManualReceivingService.receiveVendorPack(scanEventData);
    verify(endGameDeliveryService, times(1))
            .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
    verify(receiptService, times(1)).receivedQtyByPoAndPoLineList(anyList(), anySet());
    verify(endgameContainerService, times(1))
            .createAndSaveContainerAndReceipt(
                    any(ScanEventData.class),
                    any(PurchaseOrder.class),
                    any(PurchaseOrderLine.class),
                    anyInt(),
                    any(Container.class));
    verify(inventoryService, times(1)).createContainers(anyList());
    verify(containerService, times(1)).publishMultipleContainersToInventory(anyList());
    verify(endgameContainerService, times(0)).publishContainer(any(ContainerDTO.class));
    verify(dcFinServiceV2, times(1))
            .postReceiptUpdateToDCFin(any(), any(HttpHeaders.class), anyBoolean(), any(), anyString());
  }

  @Test
  public void testReceiveForUPCOnOnePOLineAndPublishJMS() throws ReceivingException {
    Container container = EndGameUtilsTestCase.getContainer();
    List<ContainerItem> containerItems = EndGameUtilsTestCase.getContainerItems();
    container.setContainerItems(containerItems);
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
            .thenReturn(EndGameUtilsTestCase.getUPCOnOnePOLine());
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
            .thenReturn(EndGameUtilsTestCase.getReceiptSummary_0664420451());
    when(endGameSlottingService.findByCaseUPC(anyString()))
            .thenReturn(EndGameUtilsTestCase.getSingleSlottingDestination());
    when(endgameContainerService.getContainer(
            any(ScanEventData.class),
            any(PurchaseOrder.class),
            any(PurchaseOrderLine.class),
            anyInt()))
            .thenReturn(container);
    when(endgameContainerService.createAndSaveContainerAndReceipt(
            any(ScanEventData.class),
            any(PurchaseOrder.class),
            any(PurchaseOrderLine.class),
            anyInt(),
            eq(container)))
            .thenReturn(container);
    when(inventoryService.createContainers(anyList())).thenReturn(EMPTY);
    ReceivingRequest scanEventData = MockMessageData.getMockReceivingRequest();
    assertNotNull(tenantSpecificConfigReader.getConfiguredFeatureFlag(anyString()));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            eq(IS_CREATE_CONTAINER_IN_SYNC_FOR_INVENTORY)))
            .thenReturn(false);
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    endGameManualReceivingService.receiveVendorPack(scanEventData);
    verify(endGameDeliveryService, times(1))
            .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
    verify(receiptService, times(1)).receivedQtyByPoAndPoLineList(anyList(), anySet());
    verify(endgameContainerService, times(1))
            .createAndSaveContainerAndReceipt(
                    any(ScanEventData.class),
                    any(PurchaseOrder.class),
                    any(PurchaseOrderLine.class),
                    anyInt(),
                    any(Container.class));
    verify(inventoryService, times(0)).createContainers(anyList());
    verify(containerService, times(0)).publishMultipleContainersToInventory(anyList());
    verify(endgameContainerService, times(1)).publishContainer(any(ContainerDTO.class));
    verify(dcFinServiceV2, times(1))
            .postReceiptUpdateToDCFin(any(), any(HttpHeaders.class), anyBoolean(), any(), anyString());
  }

  @Test
  public void testReceiveForUPCOnMultiplePOLine_MultipleSeller() throws ReceivingException {
    Container container = EndGameUtilsTestCase.getContainer();
    List<ContainerItem> containerItems = EndGameUtilsTestCase.getContainerItems();
    container.setContainerItems(containerItems);
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
            .thenReturn(EndGameUtilsTestCase.getUPCOnMultiPOLineMultipleSeller());
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
            .thenReturn(EndGameUtilsTestCase.getReceiptSummary_0664420451());
    when(endGameSlottingService.findByCaseUPC(anyString()))
            .thenReturn(EndGameUtilsTestCase.getSingleSlottingDestination());
    when(endgameContainerService.createAndSaveContainerAndReceipt(
            any(ScanEventData.class),
            any(PurchaseOrder.class),
            any(PurchaseOrderLine.class),
            anyInt(),
            any(Container.class)))
            .thenReturn(container);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            eq(IS_CREATE_CONTAINER_IN_SYNC_FOR_INVENTORY)))
            .thenReturn(true);
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    ReceivingRequest scanEventData = MockMessageData.getMockReceivingRequest();
    endGameManualReceivingService.receiveVendorPack(scanEventData);
    verify(endGameDeliveryService, times(1))
            .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
    verify(receiptService, times(0)).receivedQtyByPoAndPoLineList(anyList(), anySet());
    verify(endgameContainerService, times(0))
            .createAndSaveContainerAndReceipt(
                    any(ScanEventData.class),
                    any(PurchaseOrder.class),
                    any(PurchaseOrderLine.class),
                    anyInt(),
                    any(Container.class));
    verify(endgameContainerService, times(0)).publishContainer(any(ContainerDTO.class));
    verify(inventoryService, times(0)).createContainers(anyList());
    verify(containerService, times(0)).publishMultipleContainersToInventory(anyList());
    verify(dcFinServiceV2, times(0))
            .postReceiptUpdateToDCFin(
                    anyList(), any(HttpHeaders.class), anyBoolean(), any(), anyString());
  }

  @Test
  public void testReceiveForUPCOnProvidedPOLine() {
    Container container = EndGameUtilsTestCase.getContainer();
    List<ContainerItem> containerItems = EndGameUtilsTestCase.getContainerItems();
    container.setContainerItems(containerItems);
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
            .thenReturn(EndGameUtilsTestCase.getReceiptSummary_0664420451());
    when(endGameSlottingService.findByCaseUPC(anyString()))
            .thenReturn(EndGameUtilsTestCase.getSingleSlottingDestination());
    when(endgameContainerService.getContainer(
            any(ScanEventData.class),
            any(PurchaseOrder.class),
            any(PurchaseOrderLine.class),
            anyInt()))
            .thenReturn(container);
    when(endgameContainerService.createAndSaveContainerAndReceipt(
            any(ScanEventData.class),
            any(PurchaseOrder.class),
            any(PurchaseOrderLine.class),
            anyInt(),
            eq(container)))
            .thenReturn(container);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            eq(IS_CREATE_CONTAINER_IN_SYNC_FOR_INVENTORY)))
            .thenReturn(true);
    when(inventoryService.createContainers(anyList())).thenReturn(EMPTY);
    ReceivingRequest scanEventData = MockMessageData.getMockReceivingRequest();
    List<PurchaseOrder> purchaseOrderList =
            Arrays.asList(
                    gson.fromJson(EndGameUtilsTestCase.getUPCOnOnePOLine(), PurchaseOrder[].class));
    scanEventData.setPurchaseOrder(purchaseOrderList.get(0));

    endGameManualReceivingService.receiveVendorPack(scanEventData);

    verify(receiptService, times(1)).receivedQtyByPoAndPoLineList(anyList(), anySet());
    verify(endgameContainerService, times(1))
            .createAndSaveContainerAndReceipt(
                    any(ScanEventData.class),
                    any(PurchaseOrder.class),
                    any(PurchaseOrderLine.class),
                    anyInt(),
                    any(Container.class));
    verify(inventoryService, times(1)).createContainers(anyList());
    verify(containerService, times(1)).publishMultipleContainersToInventory(anyList());
    verify(endgameContainerService, times(0)).publishContainer(any(ContainerDTO.class));
    verify(dcFinServiceV2, times(1))
            .postReceiptUpdateToDCFin(any(), any(HttpHeaders.class), anyBoolean(), any(), anyString());
  }

  @Test(
          expectedExceptions = ReceivingConflictException.class,
          expectedExceptionsMessageRegExp = "All PO/PO Lines are exhausted.*")
  public void testGetDeliveryDocumentLineOverageToReceive() throws ReceivingException {
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
            .thenReturn(EndGameUtilsTestCase.getReceiptSummaryWithOverage_0664420451());
    when(endGameSlottingService.findByCaseUPC(anyString()))
            .thenReturn(EndGameUtilsTestCase.getSingleSlottingDestination());

    ScanEventData scanEventData = MockMessageData.getMockReceivingRequest();
    List<PurchaseOrder> purchaseOrderList =
            Arrays.asList(
                    gson.fromJson(EndGameUtilsTestCase.getUPCOnOnePOLine(), PurchaseOrder[].class));
    scanEventData.setPurchaseOrder(purchaseOrderList.get(0));
    endGameManualReceivingService.receiveVendorPack(scanEventData);

    verify(receiptService, times(1)).receivedQtyByPoAndPoLineList(anyList(), anySet());
  }

  @Test
  public void testMultiSKUWithNewContainer() throws ReceivingException {
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
            .thenReturn(new ArrayList<>());
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
            .thenReturn(EndGameUtilsTestCase.getUPCOnMultiPOLine());
    when(endGameSlottingService.findByCaseUPC(anyString()))
            .thenReturn(EndGameUtilsTestCase.getSingleSlottingDestination());
    when(containerService.findByTrackingId(anyString())).thenReturn(null);

    Container mockContainer = EndGameUtilsTestCase.getContainer();
    List<ContainerItem> containerItems = EndGameUtilsTestCase.getContainerItems();
    mockContainer.setContainerItems(containerItems);
    ArgumentCaptor<PurchaseOrder> poCaptor = ArgumentCaptor.forClass(PurchaseOrder.class);
    ArgumentCaptor<PurchaseOrderLine> poLineCaptor =
            ArgumentCaptor.forClass(PurchaseOrderLine.class);
    when(endgameContainerService.getContainer(
            any(ScanEventData.class), poCaptor.capture(), poLineCaptor.capture(), anyInt()))
            .thenReturn(mockContainer);
    when(endgameContainerService.createAndSaveContainerAndReceipt(
            any(ScanEventData.class),
            poCaptor.capture(),
            poLineCaptor.capture(),
            anyInt(),
            eq(mockContainer)))
            .thenReturn(mockContainer);
    when(inventoryService.createContainers(anyList())).thenReturn(EMPTY);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            eq(IS_CREATE_CONTAINER_IN_SYNC_FOR_INVENTORY)))
            .thenReturn(true);
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    ReceivingRequest receivingRequest = MockMessageData.getMockReceivingRequest();
    endGameManualReceivingService.receiveMultiSKUContainer(receivingRequest);

    assertEquals(poLineCaptor.getValue().getPoLineNumber().intValue(), 1);
    assertEquals(poLineCaptor.getValue().getItemDetails().getOrderableGTIN(), "20078742229434");
    assertEquals(poLineCaptor.getValue().getItemDetails().getConsumableGTIN(), "00078742229430");
    assertEquals(poCaptor.getValue().getPoNumber(), "0664420452");

    verify(endGameDeliveryService, times(1))
            .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
    verify(receiptService, times(1)).receivedQtyByPoAndPoLineList(anyList(), anySet());
    verify(endgameContainerService, times(1))
            .createAndSaveContainerAndReceipt(
                    any(ScanEventData.class),
                    any(PurchaseOrder.class),
                    any(PurchaseOrderLine.class),
                    anyInt(),
                    any(Container.class));
    verify(endgameContainerService, times(0)).publishContainer(any(ContainerDTO.class));
    verify(inventoryService, times(1)).createContainers(anyList());
    verify(containerService, times(1)).publishMultipleContainersToInventory(anyList());
    verify(dcFinServiceV2, times(1))
            .postReceiptUpdateToDCFin(any(), any(HttpHeaders.class), anyBoolean(), any(), anyString());
  }

  @Test
  public void testMultiSKUWithNewContainerAndPublishJMS() throws ReceivingException {
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
            .thenReturn(new ArrayList<>());
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
            .thenReturn(EndGameUtilsTestCase.getUPCOnMultiPOLine());
    when(endGameSlottingService.findByCaseUPC(anyString()))
            .thenReturn(EndGameUtilsTestCase.getSingleSlottingDestination());
    when(containerService.findByTrackingId(anyString())).thenReturn(null);

    Container mockContainer = EndGameUtilsTestCase.getContainer();
    List<ContainerItem> containerItems = EndGameUtilsTestCase.getContainerItems();
    mockContainer.setContainerItems(containerItems);
    ArgumentCaptor<PurchaseOrder> poCaptor = ArgumentCaptor.forClass(PurchaseOrder.class);
    ArgumentCaptor<PurchaseOrderLine> poLineCaptor =
            ArgumentCaptor.forClass(PurchaseOrderLine.class);
    when(endgameContainerService.getContainer(
            any(ScanEventData.class), poCaptor.capture(), poLineCaptor.capture(), anyInt()))
            .thenReturn(mockContainer);
    when(endgameContainerService.createAndSaveContainerAndReceipt(
            any(ScanEventData.class),
            poCaptor.capture(),
            poLineCaptor.capture(),
            anyInt(),
            eq(mockContainer)))
            .thenReturn(mockContainer);
    when(inventoryService.createContainers(anyList())).thenReturn(EMPTY);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            eq(IS_CREATE_CONTAINER_IN_SYNC_FOR_INVENTORY)))
            .thenReturn(false);
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    ReceivingRequest receivingRequest = MockMessageData.getMockReceivingRequest();
    endGameManualReceivingService.receiveMultiSKUContainer(receivingRequest);

    assertEquals(poLineCaptor.getValue().getPoLineNumber().intValue(), 1);
    assertEquals(poLineCaptor.getValue().getItemDetails().getOrderableGTIN(), "20078742229434");
    assertEquals(poLineCaptor.getValue().getItemDetails().getConsumableGTIN(), "00078742229430");
    assertEquals(poCaptor.getValue().getPoNumber(), "0664420452");

    verify(endGameDeliveryService, times(1))
            .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
    verify(receiptService, times(1)).receivedQtyByPoAndPoLineList(anyList(), anySet());
    verify(endgameContainerService, times(1))
            .createAndSaveContainerAndReceipt(
                    any(ScanEventData.class),
                    any(PurchaseOrder.class),
                    any(PurchaseOrderLine.class),
                    anyInt(),
                    any(Container.class));
    verify(endgameContainerService, times(1)).publishContainer(any(ContainerDTO.class));
    verify(inventoryService, times(0)).createContainers(anyList());
    verify(containerService, times(0)).publishMultipleContainersToInventory(anyList());
    verify(dcFinServiceV2, times(1))
            .postReceiptUpdateToDCFin(any(), any(HttpHeaders.class), anyBoolean(), any(), anyString());
  }

  @Test
  public void testMultiSKUWithExistingContainer() throws ReceivingException {
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
            .thenReturn(new ArrayList<>());
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
            .thenReturn(EndGameUtilsTestCase.getUPCOnMultiPOLine());
    when(endGameSlottingService.findByCaseUPC(anyString()))
            .thenReturn(EndGameUtilsTestCase.getSingleSlottingDestination());

    Container mockContainer = EndGameUtilsTestCase.getContainer();
    List<ContainerItem> containerItems = EndGameUtilsTestCase.getContainerItems();
    mockContainer.setContainerItems(containerItems);
    ArgumentCaptor<PurchaseOrder> poCaptor = ArgumentCaptor.forClass(PurchaseOrder.class);
    ArgumentCaptor<PurchaseOrderLine> poLineCaptor =
            ArgumentCaptor.forClass(PurchaseOrderLine.class);
    when(containerService.findByTrackingId(anyString())).thenReturn(mockContainer);
    when(endgameContainerService.addItemAndGetContainer(
            any(), any(ScanEventData.class), poCaptor.capture(), poLineCaptor.capture(), anyInt()))
            .thenReturn(mockContainer);
    when(endgameContainerService.createAndSaveContainerAndReceipt(
            any(ScanEventData.class), poCaptor.capture(), poLineCaptor.capture(), anyInt(), any()))
            .thenReturn(mockContainer);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            eq(IS_CREATE_CONTAINER_IN_SYNC_FOR_INVENTORY)))
            .thenReturn(true);
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    when(inventoryService.createContainers(anyList())).thenReturn(EMPTY);
    ReceivingRequest receivingRequest = MockMessageData.getMockReceivingRequest();
    endGameManualReceivingService.receiveMultiSKUContainer(receivingRequest);

    assertEquals(poLineCaptor.getValue().getPoLineNumber().intValue(), 1);
    assertEquals(poLineCaptor.getValue().getItemDetails().getOrderableGTIN(), "20078742229434");
    assertEquals(poLineCaptor.getValue().getItemDetails().getConsumableGTIN(), "00078742229430");
    assertEquals(poCaptor.getValue().getPoNumber(), "0664420452");

    verify(endGameDeliveryService, times(1))
            .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
    verify(receiptService, times(1)).receivedQtyByPoAndPoLineList(anyList(), anySet());
    verify(endgameContainerService, times(1))
            .createAndSaveContainerAndReceipt(
                    any(ScanEventData.class),
                    any(PurchaseOrder.class),
                    any(PurchaseOrderLine.class),
                    anyInt(),
                    any(Container.class));
    verify(inventoryService, times(1)).createContainers(anyList());
    verify(containerService, times(1)).publishMultipleContainersToInventory(anyList());
    verify(endgameContainerService, times(0)).publishContainer(any(ContainerDTO.class));
    verify(dcFinServiceV2, times(1))
            .postReceiptUpdateToDCFin(any(), any(HttpHeaders.class), anyBoolean(), any(), anyString());
  }

  @Test
  public void testMultiSKUWithExistingContainerAndPublisJMS() throws ReceivingException {
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
            .thenReturn(new ArrayList<>());
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
            .thenReturn(EndGameUtilsTestCase.getUPCOnMultiPOLine());
    when(endGameSlottingService.findByCaseUPC(anyString()))
            .thenReturn(EndGameUtilsTestCase.getSingleSlottingDestination());

    Container mockContainer = EndGameUtilsTestCase.getContainer();
    List<ContainerItem> containerItems = EndGameUtilsTestCase.getContainerItems();
    mockContainer.setContainerItems(containerItems);
    ArgumentCaptor<PurchaseOrder> poCaptor = ArgumentCaptor.forClass(PurchaseOrder.class);
    ArgumentCaptor<PurchaseOrderLine> poLineCaptor =
            ArgumentCaptor.forClass(PurchaseOrderLine.class);
    when(containerService.findByTrackingId(anyString())).thenReturn(mockContainer);
    when(endgameContainerService.addItemAndGetContainer(
            any(), any(ScanEventData.class), poCaptor.capture(), poLineCaptor.capture(), anyInt()))
            .thenReturn(mockContainer);
    when(endgameContainerService.createAndSaveContainerAndReceipt(
            any(ScanEventData.class), poCaptor.capture(), poLineCaptor.capture(), anyInt(), any()))
            .thenReturn(mockContainer);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            eq(IS_CREATE_CONTAINER_IN_SYNC_FOR_INVENTORY)))
            .thenReturn(false);
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    when(inventoryService.createContainers(anyList())).thenReturn(EMPTY);
    ReceivingRequest receivingRequest = MockMessageData.getMockReceivingRequest();
    endGameManualReceivingService.receiveMultiSKUContainer(receivingRequest);

    assertEquals(poLineCaptor.getValue().getPoLineNumber().intValue(), 1);
    assertEquals(poLineCaptor.getValue().getItemDetails().getOrderableGTIN(), "20078742229434");
    assertEquals(poLineCaptor.getValue().getItemDetails().getConsumableGTIN(), "00078742229430");
    assertEquals(poCaptor.getValue().getPoNumber(), "0664420452");

    verify(endGameDeliveryService, times(1))
            .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
    verify(receiptService, times(1)).receivedQtyByPoAndPoLineList(anyList(), anySet());
    verify(endgameContainerService, times(1))
            .createAndSaveContainerAndReceipt(
                    any(ScanEventData.class),
                    any(PurchaseOrder.class),
                    any(PurchaseOrderLine.class),
                    anyInt(),
                    any(Container.class));
    verify(inventoryService, times(0)).createContainers(anyList());
    verify(containerService, times(0)).publishMultipleContainersToInventory(anyList());
    verify(endgameContainerService, times(1)).publishContainer(any(ContainerDTO.class));
    verify(dcFinServiceV2, times(1))
            .postReceiptUpdateToDCFin(any(), any(HttpHeaders.class), anyBoolean(), any(), anyString());
  }

  @Test
  public void testMultiSKUWithNewContainerWithMultipleSellerId() throws ReceivingException {
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
            .thenReturn(new ArrayList<>());
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
            .thenReturn(EndGameUtilsTestCase.getUPCOnMultiPOLineMultipleSeller());
    when(endGameSlottingService.findByCaseUPC(anyString()))
            .thenReturn(EndGameUtilsTestCase.getSingleSlottingDestination());
    when(containerService.findByTrackingId(anyString())).thenReturn(null);

    Container mockContainer = EndGameUtilsTestCase.getContainer();
    List<ContainerItem> containerItems = EndGameUtilsTestCase.getContainerItems();
    mockContainer.setContainerItems(containerItems);
    ArgumentCaptor<PurchaseOrder> poCaptor = ArgumentCaptor.forClass(PurchaseOrder.class);
    ArgumentCaptor<PurchaseOrderLine> poLineCaptor =
            ArgumentCaptor.forClass(PurchaseOrderLine.class);
    when(endgameContainerService.createAndSaveContainerAndReceipt(
            any(ScanEventData.class),
            poCaptor.capture(),
            poLineCaptor.capture(),
            anyInt(),
            any(Container.class)))
            .thenReturn(mockContainer);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            eq(IS_CREATE_CONTAINER_IN_SYNC_FOR_INVENTORY)))
            .thenReturn(true);
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    ReceivingRequest receivingRequest = MockMessageData.getMockReceivingRequest();
    endGameManualReceivingService.receiveMultiSKUContainer(receivingRequest);

    verify(endGameDeliveryService, times(1))
            .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
    verify(receiptService, times(0)).receivedQtyByPoAndPoLineList(anyList(), anySet());
    verify(endgameContainerService, times(0))
            .createAndSaveContainerAndReceipt(
                    any(ScanEventData.class),
                    any(PurchaseOrder.class),
                    any(PurchaseOrderLine.class),
                    anyInt(),
                    any(Container.class));
    verify(inventoryService, times(0)).createContainers(anyList());
    verify(containerService, times(0)).publishMultipleContainersToInventory(anyList());
    verify(endgameContainerService, times(0)).publishContainer(any(ContainerDTO.class));
    verify(dcFinServiceV2, times(0))
            .postReceiptUpdateToDCFin(anyList(), any(HttpHeaders.class), anyBoolean(), any(), any());
  }

  @Test
  public void testMultiSKUWithExistingContainerWithProvidedPurchaseOrder() {
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
            .thenReturn(new ArrayList<>());
    when(endGameSlottingService.findByCaseUPC(anyString()))
            .thenReturn(EndGameUtilsTestCase.getSingleSlottingDestination());

    Container mockContainer = EndGameUtilsTestCase.getContainer();
    List<ContainerItem> containerItems = EndGameUtilsTestCase.getContainerItems();
    mockContainer.setContainerItems(containerItems);
    ArgumentCaptor<PurchaseOrder> poCaptor = ArgumentCaptor.forClass(PurchaseOrder.class);
    ArgumentCaptor<PurchaseOrderLine> poLineCaptor =
            ArgumentCaptor.forClass(PurchaseOrderLine.class);
    when(containerService.findByTrackingId(anyString())).thenReturn(mockContainer);
    when(endgameContainerService.addItemAndGetContainer(
            any(), any(ScanEventData.class), poCaptor.capture(), poLineCaptor.capture(), anyInt()))
            .thenReturn(mockContainer);
    when(endgameContainerService.createAndSaveContainerAndReceipt(
            any(ScanEventData.class), poCaptor.capture(), poLineCaptor.capture(), anyInt(), any()))
            .thenReturn(mockContainer);
    when(inventoryService.createContainers(anyList())).thenReturn(EMPTY);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            eq(IS_CREATE_CONTAINER_IN_SYNC_FOR_INVENTORY)))
            .thenReturn(true);
    ReceivingRequest receivingRequest = MockMessageData.getMockReceivingRequest();
    List<PurchaseOrder> purchaseOrderList =
            Arrays.asList(
                    gson.fromJson(EndGameUtilsTestCase.getUPCOnOnePOLine(), PurchaseOrder[].class));
    receivingRequest.setPurchaseOrder(purchaseOrderList.get(0));
    endGameManualReceivingService.receiveMultiSKUContainer(receivingRequest);

    assertEquals(poLineCaptor.getValue().getPoLineNumber().intValue(), 1);
    assertEquals(poLineCaptor.getValue().getItemDetails().getOrderableGTIN(), "20078742229434");
    assertEquals(poLineCaptor.getValue().getItemDetails().getConsumableGTIN(), "00078742229430");
    assertEquals(poCaptor.getValue().getPoNumber(), "0664420451");

    verify(receiptService, times(1)).receivedQtyByPoAndPoLineList(anyList(), anySet());
    verify(endgameContainerService, times(1))
            .createAndSaveContainerAndReceipt(
                    any(ScanEventData.class),
                    any(PurchaseOrder.class),
                    any(PurchaseOrderLine.class),
                    anyInt(),
                    any(Container.class));
    verify(inventoryService, times(1)).createContainers(anyList());
    verify(containerService, times(1)).publishMultipleContainersToInventory(anyList());
    verify(endgameContainerService, times(0)).publishContainer(any(ContainerDTO.class));
    verify(dcFinServiceV2, times(1))
            .postReceiptUpdateToDCFin(any(), any(HttpHeaders.class), anyBoolean(), any(), anyString());
  }

  @Test(
          expectedExceptions = ReceivingConflictException.class,
          expectedExceptionsMessageRegExp = "All PO/PO Lines are exhausted.*")
  public void testMultiSKUGetDeliveryDocumentLineOverageToReceive() {
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
            .thenReturn(EndGameUtilsTestCase.getReceiptSummaryWithOverage_0664420451());
    when(endGameSlottingService.findByCaseUPC(anyString()))
            .thenReturn(EndGameUtilsTestCase.getSingleSlottingDestination());

    ReceivingRequest receivingRequest = MockMessageData.getMockReceivingRequest();
    List<PurchaseOrder> purchaseOrderList =
            Arrays.asList(
                    gson.fromJson(EndGameUtilsTestCase.getUPCOnOnePOLine(), PurchaseOrder[].class));
    receivingRequest.setPurchaseOrder(purchaseOrderList.get(0));
    endGameManualReceivingService.receiveMultiSKUContainer(receivingRequest);

    verify(receiptService, times(1)).receivedQtyByPoAndPoLineList(anyList(), anySet());
  }

  @Test
  public void testReceiveForUPCOnProvidedPOLineWithAuditEnabled() {
    Container container = EndGameUtilsTestCase.getContainer();
    List<ContainerItem> containerItems = EndGameUtilsTestCase.getContainerItems();
    container.setContainerItems(containerItems);
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
            .thenReturn(EndGameUtilsTestCase.getReceiptSummary_0664420451());
    when(endGameSlottingService.findByCaseUPC(anyString()))
            .thenReturn(EndGameUtilsTestCase.getSingleSlottingDestination());
    when(endgameContainerService.getContainer(
            any(ScanEventData.class),
            any(PurchaseOrder.class),
            any(PurchaseOrderLine.class),
            anyInt()))
            .thenReturn(container);
    when(endgameContainerService.createAndSaveContainerAndReceipt(
            any(ScanEventData.class),
            any(PurchaseOrder.class),
            any(PurchaseOrderLine.class),
            anyInt(),
            eq(container)))
            .thenReturn(container);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            eq(IS_CREATE_CONTAINER_IN_SYNC_FOR_INVENTORY)))
            .thenReturn(true);
    when(inventoryService.createContainers(anyList())).thenReturn(EMPTY);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(ENABLE_AUDIT_REQUIRED_FLAG))
            .thenReturn(true);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(AUDIT_V2_ENABLED)).thenReturn(true);
    when(auditHelper.isAuditRequired(anyLong(), any(), anyInt(), anyString())).thenReturn(false);
    when(deliveryMetaDataService.updateAuditInfoInDeliveryMetaData(anyList(), anyInt(), anyLong()))
            .thenReturn(false);
    ReceivingRequest scanEventData = MockMessageData.getMockReceivingRequest();
    List<PurchaseOrder> purchaseOrderList =
            Arrays.asList(
                    gson.fromJson(EndGameUtilsTestCase.getUPCOnOnePOLine(), PurchaseOrder[].class));
    scanEventData.setPurchaseOrder(purchaseOrderList.get(0));
    endGameManualReceivingService.receiveVendorPack(scanEventData);
    verify(receiptService, times(1)).receivedQtyByPoAndPoLineList(anyList(), anySet());
    verify(endgameContainerService, times(1))
            .createAndSaveContainerAndReceipt(
                    any(ScanEventData.class),
                    any(PurchaseOrder.class),
                    any(PurchaseOrderLine.class),
                    anyInt(),
                    any(Container.class));
    verify(inventoryService, times(1)).createContainers(anyList());
    verify(containerService, times(1)).publishMultipleContainersToInventory(anyList());
    verify(endgameContainerService, times(0)).publishContainer(any(ContainerDTO.class));
    verify(dcFinServiceV2, times(1))
            .postReceiptUpdateToDCFin(any(), any(HttpHeaders.class), anyBoolean(), any(), anyString());
  }

  @Test
  public void testFetchLabelDetailsAndPersistToOutbox() {
    ReceivingRequest scanEventData = MockMessageData.getMockReceivingRequest();
    List<PurchaseOrder> purchaseOrderList =
            Arrays.asList(
                    gson.fromJson(EndGameUtilsTestCase.getUPCOnOnePOLine(), PurchaseOrder[].class));
    scanEventData.setPurchaseOrder(purchaseOrderList.get(0));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(ENABLE_AUDIT_REQUIRED_FLAG))
            .thenReturn(true);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(AUDIT_V2_ENABLED)).thenReturn(true);
    when(deliveryMetaDataService.updateAuditInfoInDeliveryMetaData(anyList(), anyInt(), anyLong()))
            .thenReturn(true);
    when(endGameLabelingService.findByDeliveryNumberAndCaseUpcAndStatusAndDiverAckEventIsNotNull(
            scanEventData.getDeliveryNumber(), scanEventData.getCaseUPC(), LabelStatus.ATTACHED))
            .thenReturn(Collections.singletonList(getPreLabelData(scanEventData)));
    endGameReceivingService.updateAuditEventInDeliveryMetadata(
            purchaseOrderList, scanEventData.getDeliveryNumber(), scanEventData.getCaseUPC(), 1);
    verify(endGameOutboxHandler, times(1))
            .sentToOutbox(anyString(), anyString(), any(Map.class), any(Map.class));
  }

  private PreLabelData getPreLabelData(ScanEventData scanEventData) {
    PreLabelData data = new PreLabelData();
    data.setDeliveryNumber(scanEventData.getDeliveryNumber());
    data.setCaseUpc(scanEventData.getCaseUPC());
    data.setTcl(scanEventData.getTrailerCaseLabel());
    data.setDiverAckEvent(
            "{\"deliveryNumber\":94003288,\"trailerCaseLabel\":\"TA00376918\",\"caseUPC\":\"10051000000078\"}");
    return data;
  }
}
