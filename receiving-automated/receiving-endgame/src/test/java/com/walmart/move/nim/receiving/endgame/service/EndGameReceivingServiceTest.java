package com.walmart.move.nim.receiving.endgame.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ASNFailureReasonCode.AUDIT_CHECK_REQUIRED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ASNFailureReasonCode.WFS_CHECK_FAILED;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.atlas.global.fc.service.IOutboxPublisherService;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClient;
import com.walmart.move.nim.receiving.core.common.MovePublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingConflictException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.framework.transformer.Transformer;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrderLine;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerTag;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.DCFinService;
import com.walmart.move.nim.receiving.core.service.DCFinServiceV2;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import com.walmart.move.nim.receiving.core.service.EndgameOutboxHandler;
import com.walmart.move.nim.receiving.core.service.InventoryService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.core.transformer.ContainerTransformer;
import com.walmart.move.nim.receiving.endgame.common.AuditHelper;
import com.walmart.move.nim.receiving.endgame.common.EndGameUtilsTestCase;
import com.walmart.move.nim.receiving.endgame.config.EndgameManagedConfig;
import com.walmart.move.nim.receiving.endgame.constants.DivertStatus;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.entity.PreLabelData;
import com.walmart.move.nim.receiving.endgame.message.common.ScanEventData;
import com.walmart.move.nim.receiving.endgame.mock.data.MockContainer;
import com.walmart.move.nim.receiving.endgame.mock.data.MockMessageData;
import com.walmart.move.nim.receiving.endgame.mock.data.MockPalletReceiveContainer;
import com.walmart.move.nim.receiving.endgame.model.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.*;

import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.mockito.*;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.CollectionUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class EndGameReceivingServiceTest extends ReceivingTestBase {

  private Gson gson;
  @InjectMocks private EndGameReceivingService endGameReceivingService;
  private Transformer<Container, ContainerDTO> transformer;

  @Mock private ReceiptService receiptService;
  @Mock private EndGameDeliveryService endGameDeliveryService;
  @Mock private EndgameContainerService endgameContainerService;
  @Mock private DCFinService dcFinService;
  @Mock private DCFinServiceV2 dcFinServiceV2;
  @Mock private EndGameSlottingService endGameSlottingService;
  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private AppConfig appConfig;
  @Mock private MovePublisher movePublisher;
  @Mock private DeliveryMetaDataService deliveryMetaDataService;
  @Mock private ContainerService containerService;
  @Mock private EndGameLabelingService endGameLabelingService;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private InventoryService inventoryService;
  @MockBean private IOutboxPublisherService iOutboxPublisherService;
  @Mock private GDMRestApiClient gdmRestApiClient;
  @Mock private EndgameOutboxHandler endGameOutboxHandler;
  @Mock protected AuditHelper auditHelper;

  private EndgameManagedConfig endgameManagedConfig;

  @Mock protected EndGameReceivingHelperService endGameReceivingHelperService;

  @BeforeClass
  public void setRootUp() {
    gson = new Gson();
    endgameManagedConfig = new EndgameManagedConfig();
    ReflectionTestUtils.setField(endgameManagedConfig, "nosUPCForBulkScan", 1);
    ReflectionTestUtils.setField(endgameManagedConfig, "printerFormatName", "rcv_tpl_eg_zeb");
    ReflectionTestUtils.setField(
            endgameManagedConfig, "walmartDefaultSellerId", "F55CDC31AB754BB68FE0B39041159D63");
    ReflectionTestUtils.setField(endgameManagedConfig, "samsDefaultSellerId", "0");

    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(endGameReceivingService, "gson", gson);
    ReflectionTestUtils.setField(
            endGameReceivingService, "endgameManagedConfig", endgameManagedConfig);
    ReflectionTestUtils.setField(
            endGameReceivingService, "endGameDeliveryService", endGameDeliveryService);
    TenantContext.setFacilityNum(54321);
    TenantContext.setFacilityCountryCode("us");
    TenantContext.setCorrelationId("abc");
    this.transformer = new ContainerTransformer();
    ReflectionTestUtils.setField(endGameReceivingService, "transformer", transformer);
  }

  @AfterMethod
  public void resetMocks() {
    reset(receiptService);
    reset(endGameDeliveryService);
    reset(endgameContainerService);
    reset(dcFinService);
    reset(dcFinServiceV2);
    reset(containerService);
    reset(endGameSlottingService);
    reset(containerPersisterService);
    reset(deliveryMetaDataService);
    reset(movePublisher);
    reset(appConfig);
    reset(tenantSpecificConfigReader);
    reset(inventoryService);
  }

  @Test
  public void testReceiveForUPCOnOnePOLine() throws ReceivingException {
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(AUDIT_V2_ENABLED)).thenReturn(true);
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

    ReceivingRequest scanEventData = MockMessageData.getMockReceivingRequest();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            eq(IS_CREATE_CONTAINER_IN_SYNC_FOR_INVENTORY)))
            .thenReturn(true);
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    when(inventoryService.createContainers(anyList())).thenReturn(EMPTY_STRING);
    assertNotNull(tenantSpecificConfigReader.getConfiguredFeatureFlag(anyString()));
    endGameReceivingService.receiveVendorPack(scanEventData);

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
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(AUDIT_V2_ENABLED)).thenReturn(true);
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

    ReceivingRequest scanEventData = MockMessageData.getMockReceivingRequest();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            eq(IS_CREATE_CONTAINER_IN_SYNC_FOR_INVENTORY)))
            .thenReturn(false);
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    when(inventoryService.createContainers(anyList())).thenReturn(EMPTY_STRING);
    assertNotNull(tenantSpecificConfigReader.getConfiguredFeatureFlag(anyString()));
    endGameReceivingService.receiveVendorPack(scanEventData);

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
  public void testGetDeliveryDocumentLineToReceiveWithSinglePo() throws ReceivingException {
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(AUDIT_V2_ENABLED)).thenReturn(true);
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
            .thenReturn(EndGameUtilsTestCase.getReceiptSummary_0664420451());
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
            .thenReturn(EndGameUtilsTestCase.getUPCOnOnePOLine());
    when(endGameSlottingService.findByCaseUPC(anyString()))
            .thenReturn(EndGameUtilsTestCase.getSingleSlottingDestination());

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
    when(inventoryService.createContainers(anyList())).thenReturn(EMPTY_STRING);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            eq(IS_CREATE_CONTAINER_IN_SYNC_FOR_INVENTORY)))
            .thenReturn(true);
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    assertNotNull(tenantSpecificConfigReader.getConfiguredFeatureFlag(anyString()));
    endGameReceivingService.receiveVendorPack(MockMessageData.getMockReceivingRequest());

    assertEquals(poLineCaptor.getValue().getPoLineNumber().intValue(), 1);
    assertEquals(poLineCaptor.getValue().getItemDetails().getOrderableGTIN(), "20078742229434");
    assertEquals(poLineCaptor.getValue().getItemDetails().getConsumableGTIN(), "00078742229430");
    assertEquals(poCaptor.getValue().getPoNumber(), "0664420451");

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

  @Test(
          expectedExceptions = ReceivingConflictException.class,
          expectedExceptionsMessageRegExp = "All PO/PO Lines are exhausted.*")
  public void testGetDeliveryDocumentLineOverageToReceive() throws ReceivingException {
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
            .thenReturn(EndGameUtilsTestCase.getReceiptSummaryWithOverage_0664420451());
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
            .thenReturn(EndGameUtilsTestCase.getUPCOnOnePOLine());
    when(endGameSlottingService.findByCaseUPC(anyString()))
            .thenReturn(EndGameUtilsTestCase.getSingleSlottingDestination());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            eq(IS_CREATE_CONTAINER_IN_SYNC_FOR_INVENTORY)))
            .thenReturn(true);
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    assertNotNull(tenantSpecificConfigReader.getConfiguredFeatureFlag(anyString()));
    endGameReceivingService.receiveVendorPack(MockMessageData.getMockReceivingRequest());

    verify(endGameDeliveryService, times(1))
            .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
    verify(receiptService, times(1)).receivedQtyByPoAndPoLineList(anyList(), anySet());
    verify(inventoryService, times(0)).createContainers(anyList());
    verify(containerService, times(0)).publishMultipleContainersToInventory(anyList());
    verify(endgameContainerService, times(0)).publishContainer(any(ContainerDTO.class));
  }

  @Test
  public void testGetDeliveryDocumentLineToReceiveWithMultiplePo_MABD() throws ReceivingException {
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(AUDIT_V2_ENABLED)).thenReturn(true);
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
    when(endgameContainerService.getContainer(
            any(ScanEventData.class), poCaptor.capture(), poLineCaptor.capture(), anyInt()))
            .thenReturn(mockContainer);
    when(endgameContainerService.createAndSaveContainerAndReceipt(
            any(ScanEventData.class),
            poCaptor.capture(),
            poLineCaptor.capture(),
            anyInt(),
            any(Container.class)))
            .thenReturn(mockContainer);
    when(inventoryService.createContainers(anyList())).thenReturn(EMPTY_STRING);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            eq(IS_CREATE_CONTAINER_IN_SYNC_FOR_INVENTORY)))
            .thenReturn(true);
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    assertNotNull(tenantSpecificConfigReader.getConfiguredFeatureFlag(anyString()));
    endGameReceivingService.receiveVendorPack(MockMessageData.getMockReceivingRequest());

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
  public void testInventoryOutboxFeatureZeroDirectInteraction() throws ReceivingException {
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(AUDIT_V2_ENABLED)).thenReturn(true);
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
    when(endgameContainerService.getContainer(
            any(ScanEventData.class), poCaptor.capture(), poLineCaptor.capture(), anyInt()))
            .thenReturn(mockContainer);
    when(endgameContainerService.createAndSaveContainerAndReceipt(
            any(ScanEventData.class),
            poCaptor.capture(),
            poLineCaptor.capture(),
            anyInt(),
            any(Container.class)))
            .thenReturn(mockContainer);
    when(inventoryService.createContainers(anyList())).thenReturn(EMPTY_STRING);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            eq(IS_CREATE_CONTAINER_IN_SYNC_FOR_INVENTORY)))
            .thenReturn(true);
    when(tenantSpecificConfigReader.isOutboxEnabledForInventory()).thenReturn(true);
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    assertNotNull(tenantSpecificConfigReader.getConfiguredFeatureFlag(anyString()));
    endGameReceivingService.receiveVendorPack(MockMessageData.getMockReceivingRequest());

    verify(inventoryService, times(0)).createContainersThroughOutbox(anyList());
    verify(containerService, times(0)).publishMultipleContainersToInventory(anyList());
    verify(dcFinServiceV2, times(0))
            .postReceiptUpdateToDCFin(any(), any(HttpHeaders.class), anyBoolean(), any(), anyString());
  }

  @Test
  public void testIsAuditCheckByCaseWeight() throws ReceivingException {
    String path = "../../receiving-test/src/main/resources/json/EndgamePOLineAuditCheck.json";
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
            .thenReturn(EndGameUtilsTestCase.getAuditCaseUPCOnMultiPOLine(path));
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(eq(ENABLE_AUDIT_BY_CASE_WEIGHT)))
            .thenReturn(true);
    when(tenantSpecificConfigReader.getCaseWeightCheckConfig(eq(CASE_WEIGHT_MULTIPLIER)))
            .thenReturn(DEFAULT_CASE_WEIGHT_MULTIPLIER);
    when(tenantSpecificConfigReader.getCaseWeightCheckConfig(eq(CASE_WEIGHT_LOWER_LIMIT)))
            .thenReturn(DEFAULT_CASE_WEIGHT_LOWER_LIMIT);

    Container mockContainer = EndGameUtilsTestCase.getContainer();
    List<ContainerItem> containerItems = EndGameUtilsTestCase.getContainerItems();
    mockContainer.setContainerItems(containerItems);
    ArgumentCaptor<PurchaseOrder> poCaptor = ArgumentCaptor.forClass(PurchaseOrder.class);
    ArgumentCaptor<PurchaseOrderLine> poLineCaptor =
            ArgumentCaptor.forClass(PurchaseOrderLine.class);
    when(endgameContainerService.getContainer(
            any(ScanEventData.class), poCaptor.capture(), poLineCaptor.capture(), anyInt()))
            .thenReturn(mockContainer);

    endGameReceivingService.receiveVendorPack(
            MockMessageData.getAuditCaseMockReceivingRequest(4, 20000, null));

    verify(tenantSpecificConfigReader, times(1))
            .getConfiguredFeatureFlag(eq(ENABLE_AUDIT_BY_CASE_WEIGHT));
    verify(tenantSpecificConfigReader, times(1))
            .getCaseWeightCheckConfig(eq(CASE_WEIGHT_LOWER_LIMIT));
    verify(endGameDeliveryService, times(1))
            .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void testIsAuditCheckByCaseWeight_ScanEventWeightZero() throws ReceivingException {
    String path = "../../receiving-test/src/main/resources/json/EndgamePOLineAuditCheck.json";
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
            .thenReturn(EndGameUtilsTestCase.getAuditCaseUPCOnMultiPOLine(path));
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(eq(ENABLE_AUDIT_BY_CASE_WEIGHT)))
            .thenReturn(true);
    when(tenantSpecificConfigReader.getCaseWeightCheckConfig(eq(CASE_WEIGHT_MULTIPLIER)))
            .thenReturn(DEFAULT_CASE_WEIGHT_MULTIPLIER);
    when(tenantSpecificConfigReader.getCaseWeightCheckConfig(eq(CASE_WEIGHT_LOWER_LIMIT)))
            .thenReturn(DEFAULT_CASE_WEIGHT_LOWER_LIMIT);

    Container mockContainer = EndGameUtilsTestCase.getContainer();
    List<ContainerItem> containerItems = EndGameUtilsTestCase.getContainerItems();
    mockContainer.setContainerItems(containerItems);
    ArgumentCaptor<PurchaseOrder> poCaptor = ArgumentCaptor.forClass(PurchaseOrder.class);
    ArgumentCaptor<PurchaseOrderLine> poLineCaptor =
            ArgumentCaptor.forClass(PurchaseOrderLine.class);
    when(endgameContainerService.getContainer(
            any(ScanEventData.class), poCaptor.capture(), poLineCaptor.capture(), anyInt()))
            .thenReturn(mockContainer);

    endGameReceivingService.receiveVendorPack(
            MockMessageData.getAuditCaseMockReceivingRequest(4, 0, null));

    verify(tenantSpecificConfigReader, times(1))
            .getConfiguredFeatureFlag(eq(ENABLE_AUDIT_BY_CASE_WEIGHT));
    verify(tenantSpecificConfigReader, times(0))
            .getCaseWeightCheckConfig(eq(CASE_WEIGHT_LOWER_LIMIT));
    verify(endGameDeliveryService, times(1))
            .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void testIsAuditCheckByCaseWeight_ByAuditCheckRequired() throws ReceivingException {
    String path =
            "../../receiving-test/src/main/resources/json/EndgamePOLineAuditCheckRequired.json";
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
            .thenReturn(EndGameUtilsTestCase.getAuditCaseUPCOnMultiPOLine(path));
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(eq(ENABLE_AUDIT_BY_CASE_WEIGHT)))
            .thenReturn(true);
    when(tenantSpecificConfigReader.getCaseWeightCheckConfig(eq(CASE_WEIGHT_MULTIPLIER)))
            .thenReturn(DEFAULT_CASE_WEIGHT_MULTIPLIER);
    when(tenantSpecificConfigReader.getCaseWeightCheckConfig(eq(CASE_WEIGHT_LOWER_LIMIT)))
            .thenReturn(DEFAULT_CASE_WEIGHT_LOWER_LIMIT);

    Container mockContainer = EndGameUtilsTestCase.getContainer();
    List<ContainerItem> containerItems = EndGameUtilsTestCase.getContainerItems();
    mockContainer.setContainerItems(containerItems);
    ArgumentCaptor<PurchaseOrder> poCaptor = ArgumentCaptor.forClass(PurchaseOrder.class);
    ArgumentCaptor<PurchaseOrderLine> poLineCaptor =
            ArgumentCaptor.forClass(PurchaseOrderLine.class);
    when(endgameContainerService.getContainer(
            any(ScanEventData.class), poCaptor.capture(), poLineCaptor.capture(), anyInt()))
            .thenReturn(mockContainer);

    endGameReceivingService.receiveVendorPack(
            MockMessageData.getAuditCaseMockReceivingRequest(200, 4, null));

    verify(tenantSpecificConfigReader, times(1))
            .getConfiguredFeatureFlag(eq(ENABLE_AUDIT_BY_CASE_WEIGHT));
    verify(tenantSpecificConfigReader, times(1))
            .getCaseWeightCheckConfig(eq(CASE_WEIGHT_LOWER_LIMIT));
    verify(endGameDeliveryService, times(1))
            .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void testIsAuditCheckByCaseWeight_ByBelowExpectedRange() throws ReceivingException {
    String path =
            "../../receiving-test/src/main/resources/json/EndgamePOLineAuditCheckByBelowExpectedRange.json";
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
            .thenReturn(EndGameUtilsTestCase.getAuditCaseUPCOnMultiPOLine(path));
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(eq(ENABLE_AUDIT_BY_CASE_WEIGHT)))
            .thenReturn(true);
    when(tenantSpecificConfigReader.getCaseWeightCheckConfig(eq(CASE_WEIGHT_MULTIPLIER)))
            .thenReturn(DEFAULT_CASE_WEIGHT_MULTIPLIER);
    when(tenantSpecificConfigReader.getCaseWeightCheckConfig(eq(CASE_WEIGHT_LOWER_LIMIT)))
            .thenReturn(DEFAULT_CASE_WEIGHT_LOWER_LIMIT);

    Container mockContainer = EndGameUtilsTestCase.getContainer();
    List<ContainerItem> containerItems = EndGameUtilsTestCase.getContainerItems();
    mockContainer.setContainerItems(containerItems);
    ArgumentCaptor<PurchaseOrder> poCaptor = ArgumentCaptor.forClass(PurchaseOrder.class);
    ArgumentCaptor<PurchaseOrderLine> poLineCaptor =
            ArgumentCaptor.forClass(PurchaseOrderLine.class);
    when(endgameContainerService.getContainer(
            any(ScanEventData.class), poCaptor.capture(), poLineCaptor.capture(), anyInt()))
            .thenReturn(mockContainer);

    endGameReceivingService.receiveVendorPack(
            MockMessageData.getAuditCaseMockReceivingRequest(20, 25000, null));

    verify(tenantSpecificConfigReader, times(1))
            .getConfiguredFeatureFlag(eq(ENABLE_AUDIT_BY_CASE_WEIGHT));
    verify(tenantSpecificConfigReader, times(1))
            .getCaseWeightCheckConfig(eq(CASE_WEIGHT_LOWER_LIMIT));
    verify(endGameDeliveryService, times(1))
            .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void testIsAuditCheckByCaseWeight_ByAboveExpectedRange() throws ReceivingException {
    String path =
            "../../receiving-test/src/main/resources/json/EndgamePOLineAuditCheckByAboveExpectedRange.json";
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
            .thenReturn(EndGameUtilsTestCase.getAuditCaseUPCOnMultiPOLine(path));
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(eq(ENABLE_AUDIT_BY_CASE_WEIGHT)))
            .thenReturn(true);
    when(tenantSpecificConfigReader.getCaseWeightCheckConfig(eq(CASE_WEIGHT_MULTIPLIER)))
            .thenReturn(DEFAULT_CASE_WEIGHT_MULTIPLIER);
    when(tenantSpecificConfigReader.getCaseWeightCheckConfig(eq(CASE_WEIGHT_LOWER_LIMIT)))
            .thenReturn(DEFAULT_CASE_WEIGHT_LOWER_LIMIT);

    Container mockContainer = EndGameUtilsTestCase.getContainer();
    List<ContainerItem> containerItems = EndGameUtilsTestCase.getContainerItems();
    mockContainer.setContainerItems(containerItems);
    ArgumentCaptor<PurchaseOrder> poCaptor = ArgumentCaptor.forClass(PurchaseOrder.class);
    ArgumentCaptor<PurchaseOrderLine> poLineCaptor =
            ArgumentCaptor.forClass(PurchaseOrderLine.class);
    when(endgameContainerService.getContainer(
            any(ScanEventData.class), poCaptor.capture(), poLineCaptor.capture(), anyInt()))
            .thenReturn(mockContainer);

    endGameReceivingService.receiveVendorPack(
            MockMessageData.getAuditCaseMockReceivingRequest(2, 35000, null));

    verify(tenantSpecificConfigReader, times(1))
            .getConfiguredFeatureFlag(eq(ENABLE_AUDIT_BY_CASE_WEIGHT));
    verify(tenantSpecificConfigReader, times(1))
            .getCaseWeightCheckConfig(eq(CASE_WEIGHT_LOWER_LIMIT));
    verify(endGameDeliveryService, times(1))
            .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void testIsAuditCheckByCaseWeight_MissingScanEventWeightData() throws ReceivingException {
    String path = "../../receiving-test/src/main/resources/json/EndgamePOLineAuditCheck.json";
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
            .thenReturn(EndGameUtilsTestCase.getAuditCaseUPCOnMultiPOLine(path));
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(eq(ENABLE_AUDIT_BY_CASE_WEIGHT)))
            .thenReturn(true);
    when(tenantSpecificConfigReader.getCaseWeightCheckConfig(eq(CASE_WEIGHT_MULTIPLIER)))
            .thenReturn(DEFAULT_CASE_WEIGHT_MULTIPLIER);
    when(tenantSpecificConfigReader.getCaseWeightCheckConfig(eq(CASE_WEIGHT_LOWER_LIMIT)))
            .thenReturn(DEFAULT_CASE_WEIGHT_LOWER_LIMIT);

    Container mockContainer = EndGameUtilsTestCase.getContainer();
    List<ContainerItem> containerItems = EndGameUtilsTestCase.getContainerItems();
    mockContainer.setContainerItems(containerItems);
    ArgumentCaptor<PurchaseOrder> poCaptor = ArgumentCaptor.forClass(PurchaseOrder.class);
    ArgumentCaptor<PurchaseOrderLine> poLineCaptor =
            ArgumentCaptor.forClass(PurchaseOrderLine.class);
    when(endgameContainerService.getContainer(
            any(ScanEventData.class), poCaptor.capture(), poLineCaptor.capture(), anyInt()))
            .thenReturn(mockContainer);

    endGameReceivingService.receiveVendorPack(MockMessageData.getMockReceivingRequest());

    verify(tenantSpecificConfigReader, times(1))
            .getConfiguredFeatureFlag(eq(ENABLE_AUDIT_BY_CASE_WEIGHT));
    verify(tenantSpecificConfigReader, times(0))
            .getCaseWeightCheckConfig(eq(CASE_WEIGHT_LOWER_LIMIT));
    verify(endGameDeliveryService, times(1))
            .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void testIsAuditCheckByCaseWeight_FeatureFlag() throws ReceivingException {
    String path = "../../receiving-test/src/main/resources/json/EndgamePOLineAuditCheck.json";
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
            .thenReturn(EndGameUtilsTestCase.getAuditCaseUPCOnMultiPOLine(path));
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(eq(ENABLE_AUDIT_BY_CASE_WEIGHT)))
            .thenReturn(false);
    when(tenantSpecificConfigReader.getCaseWeightCheckConfig(eq(CASE_WEIGHT_MULTIPLIER)))
            .thenReturn(DEFAULT_CASE_WEIGHT_MULTIPLIER);
    when(tenantSpecificConfigReader.getCaseWeightCheckConfig(eq(CASE_WEIGHT_LOWER_LIMIT)))
            .thenReturn(DEFAULT_CASE_WEIGHT_LOWER_LIMIT);

    Container mockContainer = EndGameUtilsTestCase.getContainer();
    List<ContainerItem> containerItems = EndGameUtilsTestCase.getContainerItems();
    mockContainer.setContainerItems(containerItems);
    ArgumentCaptor<PurchaseOrder> poCaptor = ArgumentCaptor.forClass(PurchaseOrder.class);
    ArgumentCaptor<PurchaseOrderLine> poLineCaptor =
            ArgumentCaptor.forClass(PurchaseOrderLine.class);
    when(endgameContainerService.getContainer(
            any(ScanEventData.class), poCaptor.capture(), poLineCaptor.capture(), anyInt()))
            .thenReturn(mockContainer);

    endGameReceivingService.receiveVendorPack(
            MockMessageData.getAuditCaseMockReceivingRequest(4, 20000, null));

    verify(tenantSpecificConfigReader, times(1))
            .getConfiguredFeatureFlag(eq(ENABLE_AUDIT_BY_CASE_WEIGHT));
    verify(tenantSpecificConfigReader, times(0))
            .getCaseWeightCheckConfig(eq(CASE_WEIGHT_LOWER_LIMIT));
    verify(endGameDeliveryService, times(1))
            .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void testIsAuditCheckByCaseWeight_IsNewItem() throws ReceivingException {
    String path =
            "../../receiving-test/src/main/resources/json/EndgamePOLineAuditCheckByIsNewItem.json";
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
            .thenReturn(EndGameUtilsTestCase.getAuditCaseUPCOnMultiPOLine(path));
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(eq(ENABLE_AUDIT_BY_CASE_WEIGHT)))
            .thenReturn(true);
    when(tenantSpecificConfigReader.getCaseWeightCheckConfig(eq(CASE_WEIGHT_MULTIPLIER)))
            .thenReturn(DEFAULT_CASE_WEIGHT_MULTIPLIER);
    when(tenantSpecificConfigReader.getCaseWeightCheckConfig(eq(CASE_WEIGHT_LOWER_LIMIT)))
            .thenReturn(DEFAULT_CASE_WEIGHT_LOWER_LIMIT);

    Container mockContainer = EndGameUtilsTestCase.getContainer();
    List<ContainerItem> containerItems = EndGameUtilsTestCase.getContainerItems();
    mockContainer.setContainerItems(containerItems);
    ArgumentCaptor<PurchaseOrder> poCaptor = ArgumentCaptor.forClass(PurchaseOrder.class);
    ArgumentCaptor<PurchaseOrderLine> poLineCaptor =
            ArgumentCaptor.forClass(PurchaseOrderLine.class);
    when(endgameContainerService.getContainer(
            any(ScanEventData.class), poCaptor.capture(), poLineCaptor.capture(), anyInt()))
            .thenReturn(mockContainer);

    endGameReceivingService.receiveVendorPack(
            MockMessageData.getAuditCaseMockReceivingRequest(4, 20000, null));

    verify(tenantSpecificConfigReader, times(1))
            .getConfiguredFeatureFlag(eq(ENABLE_AUDIT_BY_CASE_WEIGHT));
    verify(tenantSpecificConfigReader, times(0))
            .getCaseWeightCheckConfig(eq(CASE_WEIGHT_LOWER_LIMIT));
    verify(endGameDeliveryService, times(1))
            .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void testIsAuditCheckByCaseWeight_itemWeightMissing() throws ReceivingException {
    String path =
            "../../receiving-test/src/main/resources/json/EndgamePOLineAuditCheckByItemWeightMissing.json";
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
            .thenReturn(EndGameUtilsTestCase.getAuditCaseUPCOnMultiPOLine(path));
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(eq(ENABLE_AUDIT_BY_CASE_WEIGHT)))
            .thenReturn(true);
    when(tenantSpecificConfigReader.getCaseWeightCheckConfig(eq(CASE_WEIGHT_MULTIPLIER)))
            .thenReturn(DEFAULT_CASE_WEIGHT_MULTIPLIER);
    when(tenantSpecificConfigReader.getCaseWeightCheckConfig(eq(CASE_WEIGHT_LOWER_LIMIT)))
            .thenReturn(DEFAULT_CASE_WEIGHT_LOWER_LIMIT);

    Container mockContainer = EndGameUtilsTestCase.getContainer();
    List<ContainerItem> containerItems = EndGameUtilsTestCase.getContainerItems();
    mockContainer.setContainerItems(containerItems);
    ArgumentCaptor<PurchaseOrder> poCaptor = ArgumentCaptor.forClass(PurchaseOrder.class);
    ArgumentCaptor<PurchaseOrderLine> poLineCaptor =
            ArgumentCaptor.forClass(PurchaseOrderLine.class);
    when(endgameContainerService.getContainer(
            any(ScanEventData.class), poCaptor.capture(), poLineCaptor.capture(), anyInt()))
            .thenReturn(mockContainer);

    endGameReceivingService.receiveVendorPack(
            MockMessageData.getAuditCaseMockReceivingRequest(4, 20000, null));

    verify(tenantSpecificConfigReader, times(1))
            .getConfiguredFeatureFlag(eq(ENABLE_AUDIT_BY_CASE_WEIGHT));
    verify(tenantSpecificConfigReader, times(0))
            .getCaseWeightCheckConfig(eq(CASE_WEIGHT_LOWER_LIMIT));
    verify(endGameDeliveryService, times(1))
            .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void testIsAuditCheckByCaseWeight_itemWeightZero() throws ReceivingException {
    String path =
            "../../receiving-test/src/main/resources/json/EndgamePOLineAuditCheckByItemWeightZero.json";
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
            .thenReturn(EndGameUtilsTestCase.getAuditCaseUPCOnMultiPOLine(path));
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(eq(ENABLE_AUDIT_BY_CASE_WEIGHT)))
            .thenReturn(true);
    when(tenantSpecificConfigReader.getCaseWeightCheckConfig(eq(CASE_WEIGHT_MULTIPLIER)))
            .thenReturn(DEFAULT_CASE_WEIGHT_MULTIPLIER);
    when(tenantSpecificConfigReader.getCaseWeightCheckConfig(eq(CASE_WEIGHT_LOWER_LIMIT)))
            .thenReturn(DEFAULT_CASE_WEIGHT_LOWER_LIMIT);

    Container mockContainer = EndGameUtilsTestCase.getContainer();
    List<ContainerItem> containerItems = EndGameUtilsTestCase.getContainerItems();
    mockContainer.setContainerItems(containerItems);
    ArgumentCaptor<PurchaseOrder> poCaptor = ArgumentCaptor.forClass(PurchaseOrder.class);
    ArgumentCaptor<PurchaseOrderLine> poLineCaptor =
            ArgumentCaptor.forClass(PurchaseOrderLine.class);
    when(endgameContainerService.getContainer(
            any(ScanEventData.class), poCaptor.capture(), poLineCaptor.capture(), anyInt()))
            .thenReturn(mockContainer);

    endGameReceivingService.receiveVendorPack(
            MockMessageData.getAuditCaseMockReceivingRequest(4, 20000, null));

    verify(tenantSpecificConfigReader, times(1))
            .getConfiguredFeatureFlag(eq(ENABLE_AUDIT_BY_CASE_WEIGHT));
    verify(tenantSpecificConfigReader, times(0))
            .getCaseWeightCheckConfig(eq(CASE_WEIGHT_LOWER_LIMIT));
    verify(endGameDeliveryService, times(1))
            .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void testIsAuditCheckByCaseWeight_AdditionalInformationMissing()
          throws ReceivingException {
    String path =
            "../../receiving-test/src/main/resources/json/EndgamePOLineAuditCheckByAdditionalInformationMissing.json";
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
            .thenReturn(EndGameUtilsTestCase.getAuditCaseUPCOnMultiPOLine(path));
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(eq(ENABLE_AUDIT_BY_CASE_WEIGHT)))
            .thenReturn(true);
    when(tenantSpecificConfigReader.getCaseWeightCheckConfig(eq(CASE_WEIGHT_MULTIPLIER)))
            .thenReturn(DEFAULT_CASE_WEIGHT_MULTIPLIER);
    when(tenantSpecificConfigReader.getCaseWeightCheckConfig(eq(CASE_WEIGHT_LOWER_LIMIT)))
            .thenReturn(DEFAULT_CASE_WEIGHT_LOWER_LIMIT);

    Container mockContainer = EndGameUtilsTestCase.getContainer();
    List<ContainerItem> containerItems = EndGameUtilsTestCase.getContainerItems();
    mockContainer.setContainerItems(containerItems);
    ArgumentCaptor<PurchaseOrder> poCaptor = ArgumentCaptor.forClass(PurchaseOrder.class);
    ArgumentCaptor<PurchaseOrderLine> poLineCaptor =
            ArgumentCaptor.forClass(PurchaseOrderLine.class);
    when(endgameContainerService.getContainer(
            any(ScanEventData.class), poCaptor.capture(), poLineCaptor.capture(), anyInt()))
            .thenReturn(mockContainer);

    endGameReceivingService.receiveVendorPack(
            MockMessageData.getAuditCaseMockReceivingRequest(4, 20000, null));

    verify(tenantSpecificConfigReader, times(1))
            .getConfiguredFeatureFlag(eq(ENABLE_AUDIT_BY_CASE_WEIGHT));
    verify(tenantSpecificConfigReader, times(0))
            .getCaseWeightCheckConfig(eq(CASE_WEIGHT_LOWER_LIMIT));
    verify(endGameDeliveryService, times(1))
            .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void testGetDeliveryDocumentLineToReceiveWithMultiplePo_MABD_withOnePoQuantityReceived()
          throws ReceivingException {
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(AUDIT_V2_ENABLED)).thenReturn(true);
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
            .thenReturn(EndGameUtilsTestCase.getReceiptSummary_0664420452());
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
    when(inventoryService.createContainers(anyList())).thenReturn(EMPTY_STRING);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            eq(IS_CREATE_CONTAINER_IN_SYNC_FOR_INVENTORY)))
            .thenReturn(true);
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    assertNotNull(tenantSpecificConfigReader.getConfiguredFeatureFlag(anyString()));
    endGameReceivingService.receiveVendorPack(MockMessageData.getMockReceivingRequest());

    assertEquals(poLineCaptor.getValue().getPoLineNumber().intValue(), 1);
    assertEquals(poLineCaptor.getValue().getItemDetails().getOrderableGTIN(), "20078742229434");
    assertEquals(poLineCaptor.getValue().getItemDetails().getConsumableGTIN(), "00078742229430");
    assertEquals(poCaptor.getValue().getPoNumber(), "0664420451");

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
  public void
  testGetDeliveryDocumentLineToReceiveWithMultiplePo_MABD_withBothPoQuantityReceived_OverageForFirstPoByMABD()
          throws ReceivingException {
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
            .thenReturn(EndGameUtilsTestCase.getReceiptSummary_0664420451_0664420452());
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
    when(inventoryService.createContainers(anyList())).thenReturn(EMPTY_STRING);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            eq(IS_CREATE_CONTAINER_IN_SYNC_FOR_INVENTORY)))
            .thenReturn(true);
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    assertNotNull(tenantSpecificConfigReader.getConfiguredFeatureFlag(anyString()));
    endGameReceivingService.receiveVendorPack(MockMessageData.getMockReceivingRequest());

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
  public void
  testGetDeliveryDocumentLineToReceiveWithMultiplePo_MABD_withBothPoQuantityReceived_OverageForSecondPoByMABD()
          throws ReceivingException {
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
            .thenReturn(
                    EndGameUtilsTestCase.getReceiptSummary_0664420451_0664420452_OverageFor_0664420451());
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
    when(inventoryService.createContainers(anyList())).thenReturn(EMPTY_STRING);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            eq(IS_CREATE_CONTAINER_IN_SYNC_FOR_INVENTORY)))
            .thenReturn(true);
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    assertNotNull(tenantSpecificConfigReader.getConfiguredFeatureFlag(anyString()));
    endGameReceivingService.receiveVendorPack(MockMessageData.getMockReceivingRequest());
    assertEquals(poLineCaptor.getValue().getPoLineNumber().intValue(), 1);
    assertEquals(poLineCaptor.getValue().getItemDetails().getOrderableGTIN(), "20078742229434");
    assertEquals(poLineCaptor.getValue().getItemDetails().getConsumableGTIN(), "00078742229430");
    assertEquals(poCaptor.getValue().getPoNumber(), "0664420451");

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

  @Test(
          expectedExceptions = ReceivingConflictException.class,
          expectedExceptionsMessageRegExp = "All PO/PO Lines are exhausted.*")
  public void
  testGetDeliveryDocumentLineToReceiveWithMultiplePo_MABD_withBothPoQuantityAndOverageQuantityReceived()
          throws ReceivingException {
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
            .thenReturn(EndGameUtilsTestCase.getReceiptSummaryWithOverage_0664420451_0664420452());
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
            .thenReturn(EndGameUtilsTestCase.getUPCOnMultiPOLine());
    when(endGameSlottingService.findByCaseUPC(anyString()))
            .thenReturn(EndGameUtilsTestCase.getSingleSlottingDestination());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            eq(IS_CREATE_CONTAINER_IN_SYNC_FOR_INVENTORY)))
            .thenReturn(true);
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    assertNotNull(tenantSpecificConfigReader.getConfiguredFeatureFlag(anyString()));
    endGameReceivingService.receiveVendorPack(MockMessageData.getMockReceivingRequest());

    verify(endGameDeliveryService, times(1))
            .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
    verify(receiptService, times(1)).receivedQtyByPoAndPoLineList(anyList(), anySet());
    verify(inventoryService, times(0)).createContainers(anyList());
    verify(containerService, times(0)).publishMultipleContainersToInventory(anyList());
    verify(endgameContainerService, times(0)).publishContainer(any(ContainerDTO.class));
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
    when(inventoryService.createContainers(anyList())).thenReturn(EMPTY_STRING);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            eq(IS_CREATE_CONTAINER_IN_SYNC_FOR_INVENTORY)))
            .thenReturn(true);
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    ReceivingRequest receivingRequest = MockMessageData.getMockReceivingRequest();
    endGameReceivingService.receiveMultiSKUContainer(receivingRequest);

    assertEquals(poLineCaptor.getValue().getPoLineNumber().intValue(), 1);
    assertEquals(poLineCaptor.getValue().getItemDetails().getOrderableGTIN(), "20078742229434");
    assertEquals(poLineCaptor.getValue().getItemDetails().getConsumableGTIN(), "00078742229430");
    assertEquals(poCaptor.getValue().getPoNumber(), "0664420452");
    assertEquals(poCaptor.getValue().getLines().get(0).getSscc(), "123456");

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
    when(inventoryService.createContainers(anyList())).thenReturn(EMPTY_STRING);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            eq(IS_CREATE_CONTAINER_IN_SYNC_FOR_INVENTORY)))
            .thenReturn(true);
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    ReceivingRequest receivingRequest = MockMessageData.getMockReceivingRequest();
    endGameReceivingService.receiveMultiSKUContainer(receivingRequest);

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
  public void testMultiSKUWithExistingContainerOutboxZeroDirectInteraction() throws ReceivingException {
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
    when(inventoryService.createContainers(anyList())).thenReturn(EMPTY_STRING);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            eq(IS_CREATE_CONTAINER_IN_SYNC_FOR_INVENTORY)))
            .thenReturn(true);
    when(tenantSpecificConfigReader.isOutboxEnabledForInventory()).thenReturn(true);
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    ReceivingRequest receivingRequest = MockMessageData.getMockReceivingRequest();
    endGameReceivingService.receiveMultiSKUContainer(receivingRequest);

    verify(inventoryService, times(0)).createContainersThroughOutbox(anyList());
  }

  @Test
  public void testReceiveForChildUPC() throws ReceivingException {
    Container container = EndGameUtilsTestCase.getContainer();
    List<ContainerItem> containerItems = EndGameUtilsTestCase.getContainerItems();
    container.setContainerItems(containerItems);
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
            .thenReturn(EndGameUtilsTestCase.getUPCOnOnePOLine());
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
            .thenReturn(EndGameUtilsTestCase.getReceiptSummary_0664420451());
    when(endGameSlottingService.findByCaseUPC(anyString())).thenReturn(Collections.emptyList());
    when(endGameSlottingService.findByPossibleUPCsContains(anyString()))
            .thenReturn(EndGameUtilsTestCase.getPossibleUPCResponse());
    doReturn(container)
            .when(endgameContainerService)
            .getContainer(
                    any(ScanEventData.class),
                    any(PurchaseOrder.class),
                    any(PurchaseOrderLine.class),
                    anyInt());
    doReturn(container)
            .when(endgameContainerService)
            .createAndSaveContainerAndReceipt(
                    any(ScanEventData.class),
                    any(PurchaseOrder.class),
                    any(PurchaseOrderLine.class),
                    anyInt(),
                    eq(container));
    when(inventoryService.createContainers(anyList())).thenReturn(EMPTY_STRING);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            eq(IS_CREATE_CONTAINER_IN_SYNC_FOR_INVENTORY)))
            .thenReturn(true);
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    ReceivingRequest scanEventData = MockMessageData.getMockReceivingRequest();
    assertNotNull(tenantSpecificConfigReader.getConfiguredFeatureFlag(anyString()));
    endGameReceivingService.receiveVendorPack(scanEventData);
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

    ArgumentCaptor<String> upcCaptor = ArgumentCaptor.forClass(String.class);
    verify(endGameSlottingService, times(1)).findByCaseUPC(anyString());
    verify(endGameSlottingService, times(1)).findByPossibleUPCsContains(upcCaptor.capture());
    assertEquals(upcCaptor.getValue(), "@12333334@");
  }

  @Test
  public void testMultiUPCScanUPC() throws ReceivingException {
    when(endGameSlottingService.findByCaseUPC(anyString())).thenReturn(Collections.emptyList());
    when(endGameSlottingService.findByPossibleUPCsContains(anyString()))
            .thenReturn(EndGameUtilsTestCase.getPossibleUPCResponse());

    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
            .thenReturn(EndGameUtilsTestCase.getUPCOnOnePOLine());
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    ScanEventData scanEventData = MockMessageData.getMockScanEventData();
    ReflectionTestUtils.invokeMethod(endGameReceivingService, "getPurchaseOrders", scanEventData);

    verify(endGameSlottingService, times(1)).findByCaseUPC(anyString());
    verify(endGameSlottingService, times(1)).findByPossibleUPCsContains(anyString());
    verify(endGameDeliveryService, times(1))
            .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void testMultiUPCScanUPCSuccessInSecondAttempt() throws ReceivingException {

    when(endGameSlottingService.findByCaseUPC(anyString())).thenReturn(Collections.emptyList());
    when(endGameSlottingService.findByPossibleUPCsContains(anyString()))
            .thenReturn(EndGameUtilsTestCase.getPossibleUPCResponse());
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
            .thenThrow(RuntimeException.class)
            .thenReturn(EndGameUtilsTestCase.getUPCOnOnePOLine());
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    ScanEventData scanEventData = MockMessageData.getMockScanEventData();
    ReflectionTestUtils.invokeMethod(endGameReceivingService, "getPurchaseOrders", scanEventData);

    verify(endGameSlottingService, times(1)).findByCaseUPC(anyString());
    verify(endGameSlottingService, times(1)).findByPossibleUPCsContains(anyString());
    verify(endGameDeliveryService, times(2))
            .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
  }

  @Test(
          expectedExceptions = ReceivingDataNotFoundException.class,
          expectedExceptionsMessageRegExp = "No po/poline information found for UPC.*")
  public void testMultiUPCScanUPCException() throws ReceivingException {

    when(endGameSlottingService.findByCaseUPC(anyString())).thenReturn(Collections.emptyList());
    when(endGameSlottingService.findByPossibleUPCsContains(anyString()))
            .thenReturn(EndGameUtilsTestCase.getPossibleUPCResponse());
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
            .thenThrow(RuntimeException.class);

    ScanEventData scanEventData = MockMessageData.getMockScanEventData();
    ReflectionTestUtils.invokeMethod(endGameReceivingService, "getPurchaseOrders", scanEventData);

    verify(endGameSlottingService, times(1)).findByCaseUPC(anyString());
    verify(endGameSlottingService, times(1)).findByPossibleUPCsContains(anyString());
    verify(endGameDeliveryService, times(3))
            .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
  }

  //  @Test
  public void testCancelledPO() throws ReceivingException {
    when(endGameSlottingService.findByCaseUPC(anyString()))
            .thenReturn(
                    EndGameUtilsTestCase.getSingleSlottingDestination(
                            "60047362599690", DivertStatus.DECANT));
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
            .thenReturn(EndGameUtilsTestCase.getCancelledPOLineData());
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    ScanEventData scanEventData = MockMessageData.getMockScanEventData();
    List<PurchaseOrder> purchaseOrders =
            ReflectionTestUtils.invokeMethod(
                    endGameReceivingService, "getPurchaseOrders", scanEventData);
    assertEquals(purchaseOrders.size(), 1);
    assertEquals(purchaseOrders.get(0).getPoNumber(), "4064031136");
    assertEquals(purchaseOrders.get(0).getLines().size(), 1);
    assertEquals(purchaseOrders.get(0).getLines().get(0).getPoLineNumber(), Integer.valueOf(1));

    verify(endGameSlottingService, times(1)).findByCaseUPC(anyString());
    verify(endGameDeliveryService, times(1))
            .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void testRetrieveContainerItemFromContainer() {
    ContainerItem containerItem =
            endGameReceivingService.retrieveContainerItemFromContainer(MockContainer.getContainerDTO());
    assertEquals("TC00000001", containerItem.getTrackingId());
    assertEquals("556565795", String.valueOf(containerItem.getItemNumber()));
  }

  @Test
  public void testRetrieveContainerItemFromContainer_ThrowsException() {
    ContainerDTO container = new ContainerDTO();
    container.setContainerItems(null);
    try {
      endGameReceivingService.retrieveContainerItemFromContainer(container);
    } catch (ReceivingBadDataException exception) {
      assertEquals(ExceptionCodes.INVALID_DATA, exception.getErrorCode());
      assertEquals(EndgameConstants.EMPTY_CONTAINER_ITEM_LIST, exception.getDescription());
    }
  }

  @Test
  public void testReceiveMultiplePallet() throws ReceivingException {
    MultiplePalletReceivingRequest multiplePalletReceivingRequest =
            MockPalletReceiveContainer.createMultiplePalletReceiveRequest();
    List<ContainerDTO> containers =
            MockPalletReceiveContainer.createMultiplePalletReceiveRequest().getContainers();
    containers.get(0).setTrackingId("PQ00000257");
    when(endGameSlottingService.multipleSlotsFromSlotting(
            any(PalletSlotRequest.class), anyBoolean()))
            .thenReturn(
                    PalletSlotResponse.builder()
                            .messageId(UUID.randomUUID().toString())
                            .locations(Arrays.asList(EndGameUtilsTestCase.getSlotWithContainerId("PQ00000257")))
                            .build());
    when(inventoryService.createContainers(anyList())).thenReturn(EMPTY_STRING);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            eq(IS_CREATE_CONTAINER_IN_SYNC_FOR_INVENTORY)))
            .thenReturn(true);
    when(deliveryMetaDataService.findByDeliveryNumber(anyString())).thenReturn(Optional.empty());
    doNothing().when(containerService).publishMultipleContainersToInventory(anyList());
    doNothing()
            .when(containerPersisterService)
            .createMultipleReceiptAndContainer(anyList(), anyList(), anyList());
    doNothing()
            .when(dcFinServiceV2)
            .postReceiptUpdateToDCFin(
                    anyList(), any(HttpHeaders.class), anyBoolean(), any(), anyString());

    PalletSlotResponse palletSlotResponse =
            endGameReceivingService.getSlotLocations(
                    containers, multiplePalletReceivingRequest.getExtraAttributes());
    LabelResponse labelResponse =
            endGameReceivingService.receiveMultiplePallets(containers, palletSlotResponse, "PO");

    assertEquals("PQ00000257", labelResponse.getPrintRequests().get(0).getLabelIdentifier());

    verify(endGameSlottingService, times(1))
            .multipleSlotsFromSlotting(any(PalletSlotRequest.class), anyBoolean());
    verify(containerPersisterService, times(1))
            .createMultipleReceiptAndContainer(anyList(), anyList(), anyList());
    verify(containerService, times(1)).publishMultipleContainersToInventory(anyList());
    verify(inventoryService, times(1)).createContainers(anyList());
    verify(deliveryMetaDataService, times(1)).findByDeliveryNumber(anyString());
    verify(dcFinServiceV2, times(1))
            .postReceiptUpdateToDCFin(
                    anyList(), any(HttpHeaders.class), anyBoolean(), any(), anyString());
  }

  @Test
  public void testReceiveMultiplePalletOutboxZeroDirectInteraction() throws ReceivingException {
    MultiplePalletReceivingRequest multiplePalletReceivingRequest =
            MockPalletReceiveContainer.createMultiplePalletReceiveRequest();
    List<ContainerDTO> containers =
            MockPalletReceiveContainer.createMultiplePalletReceiveRequest().getContainers();
    containers.get(0).setTrackingId("PQ00000257");
    when(endGameSlottingService.multipleSlotsFromSlotting(
            any(PalletSlotRequest.class), anyBoolean()))
            .thenReturn(
                    PalletSlotResponse.builder()
                            .messageId(UUID.randomUUID().toString())
                            .locations(Arrays.asList(EndGameUtilsTestCase.getSlotWithContainerId("PQ00000257")))
                            .build());
    when(inventoryService.createContainers(anyList())).thenReturn(EMPTY_STRING);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            eq(IS_CREATE_CONTAINER_IN_SYNC_FOR_INVENTORY)))
            .thenReturn(true);
    when(tenantSpecificConfigReader.isOutboxEnabledForInventory()).thenReturn(true);
    when(deliveryMetaDataService.findByDeliveryNumber(anyString())).thenReturn(Optional.empty());
    doNothing().when(containerService).publishMultipleContainersToInventory(anyList());
    doNothing()
            .when(containerPersisterService)
            .createMultipleReceiptAndContainer(anyList(), anyList(), anyList());
    doNothing()
            .when(dcFinServiceV2)
            .postReceiptUpdateToDCFin(
                    anyList(), any(HttpHeaders.class), anyBoolean(), any(), anyString());

    PalletSlotResponse palletSlotResponse =
            endGameReceivingService.getSlotLocations(
                    containers, multiplePalletReceivingRequest.getExtraAttributes());
    LabelResponse labelResponse =
            endGameReceivingService.receiveMultiplePallets(containers, palletSlotResponse, "PO");

    assertEquals("PQ00000257", labelResponse.getPrintRequests().get(0).getLabelIdentifier());

    verify(containerService, times(0)).publishMultipleContainersToInventory(anyList());
    verify(inventoryService, times(0)).createContainersThroughOutbox(anyList());
    verify(dcFinServiceV2, times(0))
            .postReceiptUpdateToDCFin(
                    anyList(), any(HttpHeaders.class), anyBoolean(), any(), anyString());
  }

  @Test
  public void testReceiveMultiplePalletWithoutInventoryContainerCreation()
          throws ReceivingException {
    MultiplePalletReceivingRequest multiplePalletReceivingRequest =
            MockPalletReceiveContainer.createMultiplePalletReceiveRequest();
    List<ContainerDTO> containers =
            MockPalletReceiveContainer.createMultiplePalletReceiveRequest().getContainers();
    containers.get(0).setTrackingId("PQ00000257");
    when(endGameSlottingService.multipleSlotsFromSlotting(
            any(PalletSlotRequest.class), anyBoolean()))
            .thenReturn(
                    PalletSlotResponse.builder()
                            .messageId(UUID.randomUUID().toString())
                            .locations(Arrays.asList(EndGameUtilsTestCase.getSlotWithContainerId("PQ00000257")))
                            .build());
    when(inventoryService.createContainers(anyList())).thenReturn(EMPTY_STRING);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            eq(IS_CREATE_CONTAINER_IN_SYNC_FOR_INVENTORY)))
            .thenReturn(false);
    when(deliveryMetaDataService.findByDeliveryNumber(anyString())).thenReturn(Optional.empty());
    doNothing().when(containerService).publishMultipleContainersToInventory(anyList());
    doNothing()
            .when(containerPersisterService)
            .createMultipleReceiptAndContainer(anyList(), anyList(), anyList());
    doNothing()
            .when(dcFinServiceV2)
            .postReceiptUpdateToDCFin(
                    anyList(), any(HttpHeaders.class), anyBoolean(), any(), anyString());

    PalletSlotResponse palletSlotResponse =
            endGameReceivingService.getSlotLocations(
                    containers, multiplePalletReceivingRequest.getExtraAttributes());
    LabelResponse labelResponse =
            endGameReceivingService.receiveMultiplePallets(containers, palletSlotResponse, "PO");

    assertEquals("PQ00000257", labelResponse.getPrintRequests().get(0).getLabelIdentifier());

    verify(endGameSlottingService, times(1))
            .multipleSlotsFromSlotting(any(PalletSlotRequest.class), anyBoolean());
    verify(containerPersisterService, times(1))
            .createMultipleReceiptAndContainer(anyList(), anyList(), anyList());
    verify(containerService, times(1)).publishMultipleContainersToInventory(anyList());
    verify(inventoryService, times(0)).createContainers(anyList());
    verify(endgameContainerService, times(0)).publishContainer(any(ContainerDTO.class));
    verify(deliveryMetaDataService, times(1)).findByDeliveryNumber(anyString());
    verify(dcFinServiceV2, times(1))
            .postReceiptUpdateToDCFin(
                    anyList(), any(HttpHeaders.class), anyBoolean(), any(), anyString());
  }

  @Test
  public void testPublishMoveForMultiPalletWithMultipleContainers() {
    when(appConfig.getMoveQtyUom()).thenReturn("moveQty");
    when(appConfig.getMoveTypeCode()).thenReturn(10);
    when(appConfig.getMovetypeDesc()).thenReturn("moveTypedesc");
    when(appConfig.getMovePriority()).thenReturn(2);
    doNothing().when(movePublisher).publishMove(anyList(), any(HttpHeaders.class));

    List<ContainerDTO> containers =
            MockPalletReceiveContainer.createMultiplePalletReceiveRequest().getContainers();
    containers.get(0).setTrackingId("PQ00000257");
    containers.add(containers.get(0));
    containers.get(1).setTrackingId("PQ00000331");

    endGameReceivingService.publishMove(
            containers,
            Arrays.asList(
                    EndGameUtilsTestCase.getSlotWithContainerId("PQ00000257"),
                    EndGameUtilsTestCase.getSlotWithContainerId("PQ00000331")));

    verify(movePublisher, times(1)).publishMove(anyList(), any(HttpHeaders.class));
    verify(appConfig, times(2)).getMovePriority();
    verify(appConfig, times(2)).getMovetypeDesc();
    verify(appConfig, times(2)).getMoveTypeCode();
    verify(appConfig, times(2)).getMoveQtyUom();
  }

  @Test
  public void testPublishMoveForMultiPalletWithOneContainer() {
    when(appConfig.getMoveQtyUom()).thenReturn("moveQty");
    when(appConfig.getMoveTypeCode()).thenReturn(10);
    when(appConfig.getMovetypeDesc()).thenReturn("moveTypedesc");
    when(appConfig.getMovePriority()).thenReturn(2);
    doNothing().when(movePublisher).publishMove(anyList(), any(HttpHeaders.class));

    List<ContainerDTO> containers =
            MockPalletReceiveContainer.createMultiplePalletReceiveRequest().getContainers();
    containers.get(0).setTrackingId("PQ00000257");

    endGameReceivingService.publishMove(
            containers, Arrays.asList(EndGameUtilsTestCase.getSlotWithContainerId("PQ00000257")));

    verify(movePublisher, times(1)).publishMove(anyList(), any(HttpHeaders.class));
    verify(appConfig, times(1)).getMovePriority();
    verify(appConfig, times(1)).getMovetypeDesc();
    verify(appConfig, times(1)).getMoveTypeCode();
    verify(appConfig, times(1)).getMoveQtyUom();
  }

  @Test
  public void testBuildPrintRequest() {
    ContainerDTO container =
            MockPalletReceiveContainer.createMultiplePalletReceiveRequest().getContainers().get(0);
    container.setTrackingId("PQ00000257");
    ContainerItem containerItem = container.getContainerItems().get(0);
    containerItem.setTrackingId("PQ00000257");

    PrintRequest printRequest =
            ReflectionTestUtils.invokeMethod(
                    endGameReceivingService,
                    "buildPrintRequest",
                    transformer.reverseTransform(container),
                    containerItem,
                    EndgameConstants.EMPTY_STRING);
    assertEquals("PQ00000257", printRequest.getLabelIdentifier());
    assertEquals("rcv_tpl_eg_zeb", printRequest.getFormatName());
    assertEquals(72, printRequest.getTtlInHours());

    // check keys and values in data array of print request
    assertEquals("trailer", printRequest.getData().get(0).getKey());
    assertEquals(EndgameConstants.EMPTY_STRING, printRequest.getData().get(0).getValue());

    assertEquals("Date", printRequest.getData().get(1).getKey());
    assertEquals(ReceivingUtils.dateInEST(), printRequest.getData().get(1).getValue());

    assertEquals("deliveryNumber", printRequest.getData().get(2).getKey());
    assertEquals("60077104", printRequest.getData().get(2).getValue());

    assertEquals("DESTINATION", printRequest.getData().get(3).getKey());
    assertEquals(EndgameConstants.EMPTY_STRING, printRequest.getData().get(3).getValue());

    assertEquals("Qty", printRequest.getData().get(4).getKey());
    assertEquals("80", printRequest.getData().get(4).getValue());

    assertEquals("ITEM", printRequest.getData().get(5).getKey());
    assertEquals("553708208", printRequest.getData().get(5).getValue());

    assertEquals("DESC", printRequest.getData().get(6).getKey());
    assertEquals("ROYAL BASMATI 20LB", printRequest.getData().get(6).getValue());

    assertEquals("UPCBAR", printRequest.getData().get(7).getKey());
    assertEquals("10745042112010", printRequest.getData().get(7).getValue());

    assertEquals("user", printRequest.getData().get(8).getKey());
    assertEquals("rcvuser", printRequest.getData().get(8).getValue());

    assertEquals("TCL", printRequest.getData().get(9).getKey());
    assertEquals("PQ00000257", printRequest.getData().get(9).getValue());

    assertEquals("TCLPREFIX", printRequest.getData().get(10).getKey());
    assertEquals("PQ0000", printRequest.getData().get(10).getValue());

    assertEquals("TCLSUFFIX", printRequest.getData().get(11).getKey());
    assertEquals("0257", printRequest.getData().get(11).getValue());
  }

  @Test
  public void testContainerTransfer() {
    EndgameReceivingRequest receivingRequest = MockMessageData.getMockEndgameReceivingRequest();
    receivingRequest.setPurchaseOrder(EndGameUtilsTestCase.getPurchaseOrder());
    Container container = EndGameUtilsTestCase.getContainer();
    container.setContainerItems(EndGameUtilsTestCase.getContainerItems());
    when(containerService.findByTrackingId(anyString())).thenReturn(null);
    when(endgameContainerService.getContainer(any(EndgameReceivingRequest.class), any(), any()))
            .thenReturn(container);
    when(endgameContainerService.createAndSaveContainerAndReceipt(any(), any(), any(), any()))
            .thenReturn(null);
    when(inventoryService.createContainers(anyList())).thenReturn(null);
    when(deliveryMetaDataService.findByDeliveryNumber(any())).thenReturn(Optional.empty());
    doNothing()
            .when(dcFinServiceV2)
            .postReceiptUpdateToDCFin(
                    anyList(), any(HttpHeaders.class), anyBoolean(), any(), anyString());
    doNothing().when(containerService).publishMultipleContainersToInventory(anyList());
    endGameReceivingService.receiveContainer(receivingRequest);
  }

  @Test
  public void testContainerTransfer_withDeliveryMetaData() {
    EndgameReceivingRequest receivingRequest = MockMessageData.getMockEndgameReceivingRequest();
    DeliveryMetaDataRequest metaDataRequest = new DeliveryMetaDataRequest();
    metaDataRequest.setCarrierName("WITTE BROTHERS EXCHANGE INCORPORATED");
    metaDataRequest.setCarrierScacCode("TTMS");
    receivingRequest.setDeliveryMetaData(metaDataRequest);
    receivingRequest.setPurchaseOrder(EndGameUtilsTestCase.getPurchaseOrder());
    Container container = EndGameUtilsTestCase.getContainer();
    container.setContainerItems(EndGameUtilsTestCase.getContainerItems());
    when(containerService.findByTrackingId(anyString())).thenReturn(null);
    when(endgameContainerService.getContainer(any(EndgameReceivingRequest.class), any(), any()))
            .thenReturn(container);
    when(endgameContainerService.createAndSaveContainerAndReceipt(any(), any(), any(), any()))
            .thenReturn(null);
    when(inventoryService.createContainers(anyList())).thenReturn(null);
    doNothing()
            .when(dcFinServiceV2)
            .postReceiptUpdateToDCFin(
                    anyList(), any(HttpHeaders.class), anyBoolean(), any(), anyString());
    doNothing().when(containerService).publishMultipleContainersToInventory(anyList());
    endGameReceivingService.receiveContainer(receivingRequest);
  }

  @Test
  public void testPersistAttachPurchaseOrderRequestToOutbox() {
    endGameReceivingService.persistAttachPurchaseOrderRequestToOutbox("");
    verify(endGameOutboxHandler, times(1)).sentToOutbox(any(), anyString(), any());
  }

  @Test
  public void testAutoReceiveReasonCode_InvalidDivertStatus() throws ReceivingException {
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(AUDIT_V2_ENABLED)).thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(ENABLE_AUDIT_REQUIRED_FLAG))
            .thenReturn(true);

    Container container = EndGameUtilsTestCase.getContainer();
    List<ContainerItem> containerItems = EndGameUtilsTestCase.getContainerItems();
    container.setContainerItems(containerItems);
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
            .thenReturn(EndGameUtilsTestCase.getUPCOnOnePOLine());
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    ReceivingRequest scanEventData = MockMessageData.getMockReceivingRequest();
    scanEventData.setDiverted(DivertStatus.DECANT);
    PreLabelData preLabelData = new PreLabelData();
    scanEventData.setPreLabelData(preLabelData);
    when(inventoryService.createContainers(anyList())).thenReturn(EMPTY_STRING);
    when(auditHelper.isAuditRequired(anyLong(), any(), anyInt(), anyString())).thenReturn(true);
    endGameReceivingService.receiveVendorPack(scanEventData);
    assertEquals(preLabelData.getReason(), AUDIT_CHECK_REQUIRED);
  }

  @Test
  public void testAutoReceiveReasonCode_WFSCheckFail() throws ReceivingException {
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(AUDIT_V2_ENABLED)).thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(ENABLE_AUDIT_REQUIRED_FLAG))
            .thenReturn(true);

    Container container = EndGameUtilsTestCase.getContainer();
    List<ContainerItem> containerItems = EndGameUtilsTestCase.getContainerItems();
    container.setContainerItems(containerItems);
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
            .thenReturn(EndGameUtilsTestCase.getWfsUPCOnOnePOLine());
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    ReceivingRequest scanEventData = MockMessageData.getMockReceivingRequest();
    scanEventData.setDiverted(DivertStatus.DECANT);
    PreLabelData preLabelData = new PreLabelData();
    scanEventData.setPreLabelData(preLabelData);
    when(inventoryService.createContainers(anyList())).thenReturn(EMPTY_STRING);
    when(auditHelper.isAuditRequired(anyLong(), any(), anyInt(), anyString())).thenReturn(true);
    endGameReceivingService.receiveVendorPack(scanEventData);
    assertEquals(preLabelData.getReason(), WFS_CHECK_FAILED);
  }
  @Test
  public void testEnrichContainerTag_HoldForSaleTagEnabled() {
    ScanEventData scanEventData = new ScanEventData();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(ReceivingConstants.IS_HOLD_FOR_SALE_TAG_ENABLED))
            .thenReturn(true);

    endGameReceivingService.enrichContainerTag(scanEventData);

    List<ContainerTag> tags = scanEventData.getContainerTagList();
    Assert.assertNotNull(tags);
    Assert.assertEquals(tags.size(), 1);
    assertEquals(tags.get(0).getTag(), ReceivingConstants.CONTAINER_TAG_HOLD_FOR_SALE);
  }

  @Test
  public void testEnrichContainerTag_HoldForSaleTagDisabled() {

    ScanEventData scanEventData = new ScanEventData();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(ReceivingConstants.IS_HOLD_FOR_SALE_TAG_ENABLED))
            .thenReturn(false);

    endGameReceivingService.enrichContainerTag(scanEventData);

    List<ContainerTag> tags = scanEventData.getContainerTagList();
    assertTrue(CollectionUtils.isEmpty(tags));
  }

  @Test
  public void testEnrichContainerTag_ExistingTags() {
    // Arrange
    ScanEventData scanEventData = new ScanEventData();
    List<ContainerTag> existingTags = new ArrayList<>();
    existingTags.add(new ContainerTag(ReceivingConstants.CONTAINER_TAG_HOLD_FOR_SALE, ReceivingConstants.CONTAINER_SET));
    scanEventData.setContainerTagList(existingTags);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(ReceivingConstants.IS_HOLD_FOR_SALE_TAG_ENABLED))
            .thenReturn(true);

    // Act
    endGameReceivingService.enrichContainerTag(scanEventData);

    // Assert
    List<ContainerTag> tags = scanEventData.getContainerTagList();
    Assert.assertNotNull(tags);
    Assert.assertEquals(tags.size(), 2);
    assertEquals(tags.get(0).getTag(), ReceivingConstants.CONTAINER_TAG_HOLD_FOR_SALE);
    assertEquals(tags.get(0).getAction(), ReceivingConstants.CONTAINER_SET);
    assertEquals(tags.get(1).getTag(), ReceivingConstants.CONTAINER_TAG_HOLD_FOR_SALE);
    assertEquals(tags.get(1).getAction(), ReceivingConstants.CONTAINER_SET);
  }

  @Test
  public void testAutoReceiveReasonCode_SAMS_Rcvd() throws ReceivingException {
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(AUDIT_V2_ENABLED)).thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(ENABLE_AUDIT_REQUIRED_FLAG))
            .thenReturn(true);
    Container container = EndGameUtilsTestCase.getContainer();
    List<ContainerItem> containerItems = EndGameUtilsTestCase.getContainerItems();
    container.setContainerItems(containerItems);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(IS_VNPK_PALLET_QTY_ENABLED)).thenReturn(true);
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    ReflectionTestUtils.setField(endgameManagedConfig, "samsDefaultSellerId", "FEBD61A8B53543578C96FBA3D54195F3");
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
            .thenReturn(EndGameUtilsTestCase.getUPCOnOnePOLineSams());

    ReceivingRequest scanEventData = MockMessageData.getMockReceivingRequest();
    scanEventData.setDiverted(DivertStatus.DECANT);
    PreLabelData preLabelData = new PreLabelData();
    scanEventData.setPreLabelData(preLabelData);
    when(inventoryService.createContainers(anyList())).thenReturn(EMPTY_STRING);
    when(auditHelper.isAuditRequired(anyLong(), any(), anyInt(), anyString())).thenReturn(true);
    endGameReceivingService.receiveVendorPack(scanEventData);
    assertEquals(preLabelData.getReason(), AUDIT_CHECK_REQUIRED);
  }

  public DeliveryMetaData getDeliveryMetaData_WithItemDetails() {
    String key = "561298341";
    LinkedTreeMap<String, String> itemDetails = new LinkedTreeMap<>();
    itemDetails.put(RECEIVED_CASE_QTY, "21");
    LinkedTreeMap<String, LinkedTreeMap<String, String>> itemDetailsMap = new LinkedTreeMap<>();
    itemDetailsMap.put(key, itemDetails);
    return DeliveryMetaData.builder()
            .deliveryNumber("12333333")
            .totalCaseCount(3)
            .totalCaseLabelSent(3)
            .trailerNumber("123")
            .doorNumber("123")
            .itemDetails(itemDetailsMap)
            .build();
  }

  @Test
  public void testAutoReceiveReasonCode_SAMS() throws ReceivingException {
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(AUDIT_V2_ENABLED)).thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(ENABLE_AUDIT_REQUIRED_FLAG))
            .thenReturn(true);
    when(deliveryMetaDataService.findByDeliveryNumber("12333333")).thenReturn(Optional.ofNullable(getDeliveryMetaData_WithItemDetails()));
    when(deliveryMetaDataService.getReceivedQtyFromMetadataWithoutAuditCheck(561298341L,12333333)).thenReturn(21);
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
            .thenReturn(EndGameUtilsTestCase.getReceiptSummary_0664420451());
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    Container container = EndGameUtilsTestCase.getContainer();
    List<ContainerItem> containerItems = EndGameUtilsTestCase.getContainerItems();
    container.setContainerItems(containerItems);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(IS_VNPK_PALLET_QTY_ENABLED)).thenReturn(true);
    ReflectionTestUtils.setField(endgameManagedConfig, "samsDefaultSellerId", "FEBD61A8B53543578C96FBA3D54195F3");
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
            .thenReturn(EndGameUtilsTestCase.getUPCOnOnePOLineSamsRcvd());

    ReceivingRequest scanEventData = MockMessageData.getMockReceivingRequest();
    scanEventData.setDiverted(DivertStatus.DECANT);
    PreLabelData preLabelData = new PreLabelData();
    scanEventData.setPreLabelData(preLabelData);
    when(inventoryService.createContainers(anyList())).thenReturn(EMPTY_STRING);
    when(auditHelper.isAuditRequired(anyLong(), any(), anyInt(), anyString())).thenReturn(true);
    endGameReceivingService.receiveVendorPack(scanEventData);
    assertEquals(preLabelData.getReason(), AUDIT_CHECK_REQUIRED);
  }
}
