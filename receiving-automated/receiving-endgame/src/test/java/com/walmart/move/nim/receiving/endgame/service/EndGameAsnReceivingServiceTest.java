package com.walmart.move.nim.receiving.endgame.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.GDM_SEARCH_DOCUMENT_ERROR_CODE;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.GDM_SERVICE_DOWN;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ASN_RECEIVING_ENABLED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.AUDIT_V2_ENABLED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.EMPTY_STRING;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_CREATE_CONTAINER_IN_SYNC_FOR_INVENTORY;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_OUTBOX_ENABLED_FOR_INVENTORY;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.MABD_DEFAULT_NO_OF_DAYS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertNull;

import com.google.gson.Gson;
import com.walmart.atlas.global.fc.service.IOutboxPublisherService;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingConflictException;
import com.walmart.move.nim.receiving.core.common.rest.SimpleRestConnector;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.framework.transformer.Transformer;
import com.walmart.move.nim.receiving.core.model.gdm.GdmAsnDeliveryResponse;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Pack;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrderLine;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Shipment;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.DCFinServiceV2;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import com.walmart.move.nim.receiving.core.service.EndgameOutboxHandler;
import com.walmart.move.nim.receiving.core.service.InventoryService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.core.transformer.ContainerTransformer;
import com.walmart.move.nim.receiving.endgame.common.EndGameUtilsTestCase;
import com.walmart.move.nim.receiving.endgame.config.EndgameManagedConfig;
import com.walmart.move.nim.receiving.endgame.entity.PreLabelData;
import com.walmart.move.nim.receiving.endgame.message.common.ScanEventData;
import com.walmart.move.nim.receiving.endgame.mock.data.MockMessageData;
import com.walmart.move.nim.receiving.endgame.model.ReceiveVendorPack;
import com.walmart.move.nim.receiving.endgame.model.ReceivingRequest;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Collections;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class EndGameAsnReceivingServiceTest extends ReceivingTestBase {

  @InjectMocks private EndGameReceivingService endGameReceivingService;
  @InjectMocks private EndGameAsnReceivingService endGameAsnReceivingService;

  @Mock private ReceiptService receiptService;
  @Mock private EndGameDeliveryService endGameDeliveryService;
  @Mock private EndgameContainerService endgameContainerService;
  @Mock private DCFinServiceV2 dcFinServiceV2;
  @Mock private EndGameSlottingService endGameSlottingService;
  @Mock private ContainerService containerService;
  @Mock private EndGameLabelingService endGameLabelingService;
  @Mock private SimpleRestConnector simpleRestConnector;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private InventoryService inventoryService;
  @MockBean private IOutboxPublisherService iOutboxPublisherService;
  @Mock private EndgameOutboxHandler endGameOutboxHandler;
  @Mock private DeliveryMetaDataService deliveryMetaDataService;

  @BeforeClass
  public void setRootUp() {
    Gson gson = new Gson();
    EndgameManagedConfig endgameManagedConfig = new EndgameManagedConfig();
    Transformer<Container, ContainerDTO> transformer = new ContainerTransformer();
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(endgameManagedConfig, "nosUPCForBulkScan", 1);
    ReflectionTestUtils.setField(endgameManagedConfig, "printerFormatName", "rcv_tpl_eg_zeb");
    ReflectionTestUtils.setField(
        endgameManagedConfig, "walmartDefaultSellerId", "F55CDC31AB754BB68FE0B39041159D63");
    ReflectionTestUtils.setField(endGameReceivingService, "gson", gson);
    ReflectionTestUtils.setField(endGameAsnReceivingService, "gson", gson);
    ReflectionTestUtils.setField(
        endGameReceivingService, "endgameManagedConfig", endgameManagedConfig);
    ReflectionTestUtils.setField(
        endGameAsnReceivingService, "endgameManagedConfig", endgameManagedConfig);
    ReflectionTestUtils.setField(
        endGameReceivingService, "endGameDeliveryService", endGameDeliveryService);
    ReflectionTestUtils.setField(endGameReceivingService, "transformer", transformer);
    ReflectionTestUtils.setField(endGameAsnReceivingService, "transformer", transformer);
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
    reset(tenantSpecificConfigReader);
    reset(inventoryService);
    reset(deliveryMetaDataService);
  }

  @Test
  public void testMissingASNDeliveryDocumentLine() throws ReceivingException {
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(ASN_RECEIVING_ENABLED)).thenReturn(true);
    when(endGameDeliveryService.getASNDataFromGDM(anyLong(), anyString()))
        .thenThrow(
            new ReceivingException(
                GDM_SERVICE_DOWN,
                HttpStatus.INTERNAL_SERVER_ERROR,
                GDM_SEARCH_DOCUMENT_ERROR_CODE));
    ReceivingRequest receivingRequest = MockMessageData.getMockReceivingRequest();
    receivingRequest.setBoxIds(Collections.singletonList("4789198850142556"));
    receivingRequest.setCaseUPC(null);
    receivingRequest.setPreLabelData(new PreLabelData());
    ReceiveVendorPack receiveVendorPack =
        endGameAsnReceivingService.receiveVendorPack(receivingRequest);
    assertNull(receiveVendorPack.getPurchaseOrderList());
    assertNull(receiveVendorPack.getContainer());
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
        .postReceiptUpdateToDCFin(any(), any(HttpHeaders.class), anyBoolean(), any(), anyString());
  }

  @Test(
      expectedExceptions = ReceivingConflictException.class,
      expectedExceptionsMessageRegExp = "All ASN PO/PO Lines are exhausted for UPC.*")
  public void testASNDeliveryPOLineExhaustedExceptionForPONoMismatch() throws ReceivingException {
    String filePath = "../../receiving-test/src/main/resources/json/ASNShipmentData.json";
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(ASN_RECEIVING_ENABLED)).thenReturn(true);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(AUDIT_V2_ENABLED)).thenReturn(true);
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
        .thenReturn(EndGameUtilsTestCase.getReceiptSummary_0664420451());
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(EndGameUtilsTestCase.getUPCOnOnePOLine());
    when(endGameSlottingService.findByCaseUPC(anyString()))
        .thenReturn(EndGameUtilsTestCase.getSingleSlottingDestination());

    when(endGameDeliveryService.getASNDataFromGDM(anyLong(), anyString()))
        .thenReturn(EndGameUtilsTestCase.getASNData(filePath));
    when(inventoryService.createContainers(anyList())).thenReturn(EMPTY_STRING);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            IS_CREATE_CONTAINER_IN_SYNC_FOR_INVENTORY))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    assertNotNull(tenantSpecificConfigReader.getConfiguredFeatureFlag(anyString()));
    ReceivingRequest receivingRequest = MockMessageData.getMockReceivingRequest();
    receivingRequest.setBoxIds(Collections.singletonList("4789198850142556"));
    receivingRequest.setPreLabelData(new PreLabelData());
    ReceiveVendorPack receiveVendorPack =
        endGameAsnReceivingService.receiveVendorPack(receivingRequest);

    assertEquals(receiveVendorPack.getPurchaseOrderList().size(), 1);
    assertNull(receiveVendorPack.getContainer());

    verify(endGameDeliveryService, times(1))
        .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
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
        .postReceiptUpdateToDCFin(any(), any(HttpHeaders.class), anyBoolean(), any(), anyString());
  }

  @Test(
      expectedExceptions = ReceivingConflictException.class,
      expectedExceptionsMessageRegExp = "All ASN PO/PO Lines are exhausted for UPC.*")
  public void testASNDeliveryPOLineExhaustedExceptionForPOItemMismatch() throws ReceivingException {
    String filePath = "../../receiving-test/src/main/resources/json/ASNShipmentDataValidPo.json";
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(ASN_RECEIVING_ENABLED)).thenReturn(true);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(AUDIT_V2_ENABLED)).thenReturn(true);
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
        .thenReturn(EndGameUtilsTestCase.getReceiptSummary_0664420451());
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(EndGameUtilsTestCase.getUPCOnOnePOLine());
    when(endGameSlottingService.findByCaseUPC(anyString()))
        .thenReturn(EndGameUtilsTestCase.getSingleSlottingDestination());

    when(endGameDeliveryService.getASNDataFromGDM(anyLong(), anyString()))
        .thenReturn(EndGameUtilsTestCase.getASNData(filePath));
    when(inventoryService.createContainers(anyList())).thenReturn(EMPTY_STRING);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            IS_CREATE_CONTAINER_IN_SYNC_FOR_INVENTORY))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    assertNotNull(tenantSpecificConfigReader.getConfiguredFeatureFlag(anyString()));
    ReceivingRequest receivingRequest = MockMessageData.getMockReceivingRequest();
    receivingRequest.setBoxIds(Collections.singletonList("4789198850142556"));
    receivingRequest.setPreLabelData(new PreLabelData());
    ReceiveVendorPack receiveVendorPack =
        endGameAsnReceivingService.receiveVendorPack(receivingRequest);

    assertEquals(receiveVendorPack.getPurchaseOrderList().size(), 1);
    assertNull(receiveVendorPack.getContainer());

    verify(endGameDeliveryService, times(1))
        .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
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
        .postReceiptUpdateToDCFin(any(), any(HttpHeaders.class), anyBoolean(), any(), anyString());
  }

  @Test
  public void testASNDeliveryDocumentLineForQtyValidation() throws ReceivingException {
    String filePath = "../../receiving-test/src/main/resources/json/ASNShipmentDataValidPoQty.json";
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(ASN_RECEIVING_ENABLED)).thenReturn(true);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(AUDIT_V2_ENABLED)).thenReturn(true);
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
        .thenReturn(EndGameUtilsTestCase.getReceiptSummary_0664420451());
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(EndGameUtilsTestCase.getUPCOnOnePOLine());
    when(endGameSlottingService.findByCaseUPC(anyString()))
        .thenReturn(EndGameUtilsTestCase.getSingleSlottingDestination());

    when(endGameDeliveryService.getASNDataFromGDM(anyLong(), anyString()))
        .thenReturn(EndGameUtilsTestCase.getASNData(filePath));
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
            IS_CREATE_CONTAINER_IN_SYNC_FOR_INVENTORY))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    assertNotNull(tenantSpecificConfigReader.getConfiguredFeatureFlag(anyString()));
    ReceivingRequest receivingRequest = MockMessageData.getMockReceivingRequest();
    receivingRequest.setBoxIds(Collections.singletonList("4789198850142556"));
    ReceiveVendorPack receiveVendorPack =
        endGameAsnReceivingService.receiveVendorPack(receivingRequest);

    assertNotNull(receiveVendorPack.getContainer());

    verify(endGameDeliveryService, times(1))
        .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
    verify(endgameContainerService, times(1))
        .createAndSaveContainerAndReceipt(
            any(ScanEventData.class),
            any(PurchaseOrder.class),
            any(PurchaseOrderLine.class),
            anyInt(),
            any(Container.class));
    verify(inventoryService, times(1)).createContainers(anyList());
    verify(containerService, times(1)).publishMultipleContainersToInventory(anyList());
    verify(dcFinServiceV2, times(1))
        .postReceiptUpdateToDCFin(any(), any(HttpHeaders.class), anyBoolean(), any(), anyString());
  }

  @Test(
      expectedExceptions = ReceivingConflictException.class,
      expectedExceptionsMessageRegExp = "All ASN PO/PO Lines are exhausted for UPC.*")
  public void testASNDeliveryDocumentLineForPoLineValidation() throws ReceivingException {
    String filePath = "../../receiving-test/src/main/resources/json/ASNShipmentDataValidPoQty.json";
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(ASN_RECEIVING_ENABLED)).thenReturn(true);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(AUDIT_V2_ENABLED)).thenReturn(true);
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
        .thenReturn(EndGameUtilsTestCase.getReceiptSummary_0664420451());
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(EndGameUtilsTestCase.getUPCOnOnePOLine());
    when(endGameSlottingService.findByCaseUPC(anyString()))
        .thenReturn(EndGameUtilsTestCase.getSingleSlottingDestination());
    GdmAsnDeliveryResponse response = EndGameUtilsTestCase.getASNData(filePath);
    response.getPacks().get(0).getItems().get(0).getInventoryDetail().setReportedQuantity(4.0);
    when(endGameDeliveryService.getASNDataFromGDM(anyLong(), anyString())).thenReturn(response);
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    assertNotNull(tenantSpecificConfigReader.getConfiguredFeatureFlag(anyString()));
    ReceivingRequest receivingRequest = MockMessageData.getMockReceivingRequest();
    receivingRequest.setBoxIds(Collections.singletonList("4789198850142556"));
    receivingRequest.setPreLabelData(new PreLabelData());
    ReceiveVendorPack receiveVendorPack =
        endGameAsnReceivingService.receiveVendorPack(receivingRequest);

    assertNotNull(receiveVendorPack.getContainer());

    verify(endGameDeliveryService, times(1))
        .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
    verify(endgameContainerService, times(0))
        .createAndSaveContainerAndReceipt(
            any(ScanEventData.class),
            any(PurchaseOrder.class),
            any(PurchaseOrderLine.class),
            anyInt(),
            any(Container.class));
    verify(inventoryService, times(0)).createContainers(anyList());
    verify(containerService, times(0)).publishMultipleContainersToInventory(anyList());
    verify(dcFinServiceV2, times(0))
        .postReceiptUpdateToDCFin(any(), any(HttpHeaders.class), anyBoolean(), any(), anyString());
  }

  @Test
  public void testReceiveForUPCOnMultiplePOLine_MultipleSeller() throws ReceivingException {
    String filePath = "../../receiving-test/src/main/resources/json/ASNShipmentDataValidPoQty.json";
    GdmAsnDeliveryResponse response = EndGameUtilsTestCase.getASNData(filePath);
    response.getPacks().get(0).getItems().get(0).getInventoryDetail().setReportedQuantity(4.0);
    when(endGameDeliveryService.getASNDataFromGDM(anyLong(), anyString())).thenReturn(response);
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
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            IS_CREATE_CONTAINER_IN_SYNC_FOR_INVENTORY))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    ReceivingRequest receivingRequest = MockMessageData.getMockReceivingRequest();
    receivingRequest.setBoxIds(Collections.singletonList("4789198850142556"));
    receivingRequest.setPreLabelData(new PreLabelData());
    endGameAsnReceivingService.receiveVendorPack(receivingRequest);
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
  public void testASNReceiveCode_Multiple_Seller_UPC_Delivery_Number() throws ReceivingException {
    String filePath = "../../receiving-test/src/main/resources/json/ASNShipmentDataValidPo.json";
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(ASN_RECEIVING_ENABLED)).thenReturn(true);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(AUDIT_V2_ENABLED)).thenReturn(true);
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
        .thenReturn(EndGameUtilsTestCase.getReceiptSummaryWithOverage_0664420451_0664420452());
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(EndGameUtilsTestCase.getUPCOnMultiPOLineMultipleSeller());
    when(endGameSlottingService.findByCaseUPC(anyString()))
        .thenReturn(EndGameUtilsTestCase.getSingleSlottingDestination());

    when(endGameDeliveryService.getASNDataFromGDM(anyLong(), anyString()))
        .thenReturn(EndGameUtilsTestCase.getASNData(filePath));
    when(inventoryService.createContainers(anyList())).thenReturn(EMPTY_STRING);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            IS_CREATE_CONTAINER_IN_SYNC_FOR_INVENTORY))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    assertNotNull(tenantSpecificConfigReader.getConfiguredFeatureFlag(anyString()));
    ReceivingRequest receivingRequest = MockMessageData.getMockReceivingRequest();
    receivingRequest.setBoxIds(Collections.singletonList("4789198850142556"));
    receivingRequest.setPreLabelData(new PreLabelData());
    ReceiveVendorPack receiveVendorPack =
        endGameAsnReceivingService.receiveVendorPack(receivingRequest);

    assertEquals(receiveVendorPack.getPurchaseOrderList().size(), 2);
    assertNull(receiveVendorPack.getContainer());

    verify(endGameDeliveryService, times(1))
        .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
    verify(endgameContainerService, times(0))
        .createAndSaveContainerAndReceipt(
            any(ScanEventData.class),
            any(PurchaseOrder.class),
            any(PurchaseOrderLine.class),
            anyInt(),
            any(Container.class));
    verify(containerService, times(0)).publishMultipleContainersToInventory(anyList());
    verify(endgameContainerService, times(0)).publishContainer(any(ContainerDTO.class));
  }

  @Test(
      expectedExceptions = ReceivingConflictException.class,
      expectedExceptionsMessageRegExp = "All ASN PO/PO Lines are exhausted for UPC.*")
  public void testASNReceiveCode_PO_Lines_Exhausted() throws ReceivingException {
    String filePath = "../../receiving-test/src/main/resources/json/ASNShipmentData.json";
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(ASN_RECEIVING_ENABLED)).thenReturn(true);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(AUDIT_V2_ENABLED)).thenReturn(true);
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
        .thenReturn(EndGameUtilsTestCase.getReceiptSummary_0664420451());
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(EndGameUtilsTestCase.getUPCOnOnePOLine());
    when(endGameSlottingService.findByCaseUPC(anyString()))
        .thenReturn(EndGameUtilsTestCase.getSingleSlottingDestination());

    when(endGameDeliveryService.getASNDataFromGDM(anyLong(), anyString()))
        .thenReturn(EndGameUtilsTestCase.getASNData(filePath));
    when(inventoryService.createContainers(anyList())).thenReturn(EMPTY_STRING);
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    assertNotNull(tenantSpecificConfigReader.getConfiguredFeatureFlag(anyString()));
    ReceivingRequest receivingRequest = MockMessageData.getMockReceivingRequest();
    receivingRequest.setBoxIds(Collections.singletonList("4789198850142556"));
    receivingRequest.setPreLabelData(new PreLabelData());
    ReceiveVendorPack receiveVendorPack =
        endGameAsnReceivingService.receiveVendorPack(receivingRequest);

    verify(endGameDeliveryService, times(1))
        .findDeliveryDocument(anyLong(), anyString(), any(HttpHeaders.class));
    verify(endgameContainerService, times(0))
        .createAndSaveContainerAndReceipt(
            any(ScanEventData.class),
            any(PurchaseOrder.class),
            any(PurchaseOrderLine.class),
            anyInt(),
            any(Container.class));
    verify(containerService, times(0)).publishMultipleContainersToInventory(anyList());
    verify(endgameContainerService, times(0)).publishContainer(any(ContainerDTO.class));
  }

  @Test(
      expectedExceptions = ReceivingConflictException.class,
      expectedExceptionsMessageRegExp = "All PO/PO Lines are exhausted for UPC.*")
  public void testASNReasonCode_POLineExhausted_NoAvailablePOLines() throws ReceivingException {
    String filePath = "../../receiving-test/src/main/resources/json/ASNShipmentDataValidPoQty.json";
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(ASN_RECEIVING_ENABLED)).thenReturn(true);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(AUDIT_V2_ENABLED)).thenReturn(true);
    when(receiptService.receivedQtyByPoAndPoLineList(anyList(), anySet()))
        .thenReturn(EndGameUtilsTestCase.getReceiptSummary__0664420451_2());
    when(endGameDeliveryService.findDeliveryDocument(
            anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(EndGameUtilsTestCase.getUPCOnOnePOLine());
    when(endGameSlottingService.findByCaseUPC(anyString()))
        .thenReturn(EndGameUtilsTestCase.getSingleSlottingDestination());

    when(endGameDeliveryService.getASNDataFromGDM(anyLong(), anyString()))
        .thenReturn(EndGameUtilsTestCase.getASNData(filePath));
    when(tenantSpecificConfigReader.getMabdNoOfDays(any())).thenReturn(MABD_DEFAULT_NO_OF_DAYS);
    ReceivingRequest receivingRequest = MockMessageData.getMockReceivingRequest();
    receivingRequest.setBoxIds(Collections.singletonList("4789198850142556"));
    receivingRequest.setPreLabelData(new PreLabelData());
    ReceiveVendorPack receiveVendorPack =
        endGameAsnReceivingService.receiveVendorPack(receivingRequest);

    assertNotNull(receiveVendorPack.getContainer());
  }

  @Test
  public void testGetShipmentPacksAndLinkDelivery_Success() throws ReceivingException {
    String boxId = "4789198850142556";
    String shipmentId = "12345";
    String deliveryNumber = "54321";
    ScanEventData scanEventData = new ScanEventData();
    scanEventData.setBoxIds(Collections.singletonList(boxId));
    scanEventData.setShipmentId(shipmentId);
    scanEventData.setDeliveryNumber(Long.parseLong(deliveryNumber));

    SsccScanResponse response = new SsccScanResponse();
    response.setShipments(Collections.singletonList(new Shipment()));
    response.setPacks(Collections.singletonList(new Pack()));

    when(endGameDeliveryService.getASNDataFromGDM(anyLong(), anyString())).thenReturn(null);
    when(endGameDeliveryService.globalPackSearch(anyString(), any(HttpHeaders.class)))
        .thenReturn(response);
    when(endGameDeliveryService.linkDeliveryWithShipment(
            anyString(), any(), any(), any(HttpHeaders.class)))
        .thenReturn("");

    ReceiveVendorPack receiveVendorPack =
        endGameAsnReceivingService.receiveVendorPack(scanEventData);

    assertNotNull(receiveVendorPack);
    verify(endGameDeliveryService, times(2)).getASNDataFromGDM(anyLong(), anyString());
    verify(endGameDeliveryService, times(1)).globalPackSearch(anyString(), any(HttpHeaders.class));
    verify(endGameDeliveryService, times(1))
        .linkDeliveryWithShipment(anyString(), any(), any(), any(HttpHeaders.class));
  }

  @Test
  public void testGetShipmentPacksAndLinkDelivery_Failure() throws ReceivingException {
    String boxId = "4789198850142556";
    String shipmentId = "12345";
    String deliveryNumber = "54321";
    ScanEventData scanEventData = new ScanEventData();
    scanEventData.setBoxIds(Collections.singletonList(boxId));
    scanEventData.setShipmentId(shipmentId);
    scanEventData.setDeliveryNumber(Long.parseLong(deliveryNumber));

    SsccScanResponse response = new SsccScanResponse();
    response.setShipments(Collections.singletonList(new Shipment()));
    response.setPacks(Collections.singletonList(new Pack()));

    when(endGameDeliveryService.getASNDataFromGDM(anyLong(), anyString())).thenReturn(null);
    when(endGameDeliveryService.globalPackSearch(anyString(), any(HttpHeaders.class)))
        .thenReturn(response);
    when(endGameDeliveryService.linkDeliveryWithShipment(
            anyString(), any(), any(), any(HttpHeaders.class)))
        .thenReturn(ReceivingConstants.RESTUTILS_ERROR_MESSAGE);

    ReceiveVendorPack receiveVendorPack =
        endGameAsnReceivingService.receiveVendorPack(scanEventData);

    assertNotNull(receiveVendorPack);
    verify(endGameDeliveryService, times(1)).getASNDataFromGDM(anyLong(), anyString());
    verify(endGameDeliveryService, times(1)).globalPackSearch(anyString(), any(HttpHeaders.class));
    verify(endGameDeliveryService, times(1))
        .linkDeliveryWithShipment(anyString(), any(), any(), any(HttpHeaders.class));
  }

  @Test
  public void testGetShipmentPacks_EmptyResponse() throws ReceivingException {
    String boxId = "4789198850142556";
    String shipmentId = "12345";
    String deliveryNumber = "54321";

    ScanEventData scanEventData = new ScanEventData();
    scanEventData.setBoxIds(Collections.singletonList(boxId));
    scanEventData.setShipmentId(shipmentId);
    scanEventData.setDeliveryNumber(Long.parseLong(deliveryNumber));

    when(endGameDeliveryService.getASNDataFromGDM(anyLong(), anyString())).thenReturn(null);
    when(endGameDeliveryService.globalPackSearch(anyString(), any(HttpHeaders.class)))
        .thenReturn(null);
    when(endGameDeliveryService.linkDeliveryWithShipment(
            anyString(), any(), any(), any(HttpHeaders.class)))
        .thenReturn(ReceivingConstants.RESTUTILS_ERROR_MESSAGE);

    endGameAsnReceivingService.receiveVendorPack(scanEventData);
    verify(endGameDeliveryService, times(0))
        .linkDeliveryWithShipment(anyString(), any(), any(), any(HttpHeaders.class));
  }
}
