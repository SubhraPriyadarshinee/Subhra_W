package com.walmart.move.nim.receiving.fixture.event.processor;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.framework.transformer.Transformer;
import com.walmart.move.nim.receiving.core.model.InventoryItemPODetailUpdateRequest;
import com.walmart.move.nim.receiving.core.model.gdm.v3.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.pack.PurchaseOrder;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.InventoryService;
import com.walmart.move.nim.receiving.fixture.common.FixtureConstants;
import com.walmart.move.nim.receiving.fixture.entity.ControlTowerTracker;
import com.walmart.move.nim.receiving.fixture.mock.data.FixtureMockData;
import com.walmart.move.nim.receiving.fixture.model.ShipmentEvent;
import com.walmart.move.nim.receiving.fixture.service.ControlTowerService;
import com.walmart.move.nim.receiving.fixture.service.FixtureDeliveryMetadataService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.mockito.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ShipmentReconcileProcessorTest extends ReceivingTestBase {

  @InjectMocks private ShipmentReconcileProcessor shipmentReconcileProcessor;

  @Mock private TenantSpecificConfigReader configUtils;

  @Mock private FixtureDeliveryMetadataService fixtureDeliveryMetadataService;

  @Mock private ContainerPersisterService containerPersisterService;

  @Mock private ControlTowerService controlTowerService;

  @Mock private InventoryService inventoryService;

  @Mock private ContainerService containerService;

  @Mock private Transformer<Container, ContainerDTO> transformer;

  @Captor private ArgumentCaptor<List<Container>> containerList;

  @Captor private ArgumentCaptor<List<ContainerItem>> containerItemList;

  @BeforeClass
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(
        shipmentReconcileProcessor,
        "selfReferenceShipmentReconcileProcessor",
        shipmentReconcileProcessor);
    TenantContext.setFacilityNum(32987);
    TenantContext.setFacilityCountryCode("US");
  }

  @AfterMethod
  public void tearDown() {
    reset(containerPersisterService);
    reset(controlTowerService);
    reset(containerService);
    reset(configUtils);
    reset(inventoryService);
  }

  @Test
  public void testCanExecute_EmptyPackList() {
    ShipmentEvent shipmentEvent = new ShipmentEvent();
    Shipment shipment = new Shipment();
    shipment.setShipmentNumber("1");
    shipmentEvent.setShipment(shipment);
    shipmentReconcileProcessor.execute(shipmentEvent);
    verify(containerPersisterService, times(0))
        .getContainerByParentTrackingIdInAndContainerStatus(anyList(), anyList());
  }

  @Test
  public void testCanExecute_NoExistingContainerForASN() {
    ShipmentEvent shipmentEvent = new ShipmentEvent();
    Shipment shipment = new Shipment();
    shipment.setShipmentNumber("1");
    shipmentEvent.setShipment(shipment);
    List<Pack> packList = new ArrayList<>();
    Pack pack = new Pack();
    pack.setPackNumber("123");
    packList.add(pack);
    shipmentEvent.setPackList(packList);
    when(containerPersisterService.getContainerByParentTrackingIdInAndContainerStatus(
            anyList(), anyList()))
        .thenReturn(new ArrayList<>());
    shipmentReconcileProcessor.execute(shipmentEvent);
    verify(containerPersisterService, times(1))
        .getContainerByParentTrackingIdInAndContainerStatus(anyList(), anyList());
    verify(containerPersisterService, times(0))
        .saveContainerAndContainerItems(anyList(), anyList());
  }

  @Test
  public void testCanExecute_ExistingContainerForASN() {
    ShipmentEvent shipmentEvent = new ShipmentEvent();
    Shipment shipment = new Shipment();
    shipment.setShipmentNumber("1");
    shipmentEvent.setShipment(shipment);
    List<Pack> packList = new ArrayList<>();
    Pack pack = new Pack();
    pack.setPackNumber("339242A060027394");
    Destination handledOnBehalfOf = new Destination();
    handledOnBehalfOf.setNumber("10805");
    handledOnBehalfOf.setCountryCode("US");
    pack.setHandledOnBehalfOf(handledOnBehalfOf);
    Item item = new Item();
    item.setItemNumber(586297785l);
    PurchaseOrder purchaseOrder = new PurchaseOrder();
    purchaseOrder.setPoNumber("6566969649");
    purchaseOrder.setPoLineNumber("1");
    InventoryDetail inventoryDetail = new InventoryDetail();
    inventoryDetail.setReportedQuantity(5d);
    inventoryDetail.setReportedUom(ReceivingConstants.Uom.EACHES);
    item.setPurchaseOrder(purchaseOrder);
    pack.setItems(Collections.singletonList(item));
    packList.add(pack);
    shipmentEvent.setPackList(packList);
    List<Container> cl = new ArrayList<>();
    Container activeContainer = FixtureMockData.getCompletedContainer();
    activeContainer.setContainerStatus(ReceivingConstants.STATUS_COMPLETE_NO_ASN);
    activeContainer.setTrackingId("LPN 10656 INV 4784");
    activeContainer.setParentTrackingId("339242A060027394");
    activeContainer.getContainerItems().get(0).setPurchaseReferenceLineNumber(null);
    activeContainer.getContainerItems().get(0).setPurchaseReferenceNumber(null);
    activeContainer.getContainerItems().get(0).setTrackingId("LPN 10656 INV 4784");
    activeContainer.getContainerItems().get(0).setItemNumber(586297785l);

    cl.add(activeContainer);
    when(containerPersisterService.getContainerByParentTrackingIdInAndContainerStatus(
            anyList(), eq(FixtureConstants.CONTAINER_STATUS_WO_ASN)))
        .thenReturn(cl);
    doNothing().when(fixtureDeliveryMetadataService).persistsShipmentMetadata(any(Shipment.class));
    doNothing()
        .when(containerPersisterService)
        .saveContainerAndContainerItems(anyList(), anyList());
    doNothing()
        .when(inventoryService)
        .updateInventoryPoDetails(any(InventoryItemPODetailUpdateRequest.class));
    when(transformer.transformList(anyList())).thenReturn(new ArrayList<>());
    doNothing().when(containerService).publishMultipleContainersToInventory(anyList());
    when(configUtils.getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean()))
        .thenReturn(Boolean.FALSE);
    shipmentReconcileProcessor.execute(shipmentEvent);
    verify(containerPersisterService, times(1))
        .getContainerByParentTrackingIdInAndContainerStatus(anyList(), anyList());
    verify(containerPersisterService, times(1))
        .saveContainerAndContainerItems(containerList.capture(), containerItemList.capture());
    List<Container> containers = containerList.getValue();
    List<ContainerItem> containerItems = containerItemList.getValue();
    assertEquals(containers.size(), 1);
    assertEquals(containerItems.size(), 1);
    ContainerItem containerItem = containerItems.get(0);
    assertEquals(containerItem.getPurchaseReferenceNumber(), "6566969649");
    assertEquals(containerItem.getPurchaseReferenceLineNumber(), Integer.valueOf(1));
    assertEquals(containerItem.getQuantity(), Integer.valueOf(5));
    verify(containerService, times(1)).publishMultipleContainersToInventory(any());
  }

  @Test
  public void testCanExecute_ExistingContainerForASNCTEnabled() {
    ShipmentEvent shipmentEvent = new ShipmentEvent();
    Shipment shipment = new Shipment();
    shipment.setShipmentNumber("1");
    shipmentEvent.setShipment(shipment);
    List<Pack> packList = new ArrayList<>();
    Pack pack = new Pack();
    pack.setPackNumber("339242A060027394");
    Destination handledOnBehalfOf = new Destination();
    handledOnBehalfOf.setNumber("10805");
    handledOnBehalfOf.setCountryCode("US");
    pack.setHandledOnBehalfOf(handledOnBehalfOf);
    Item item = new Item();
    item.setItemNumber(586297785l);
    PurchaseOrder purchaseOrder = new PurchaseOrder();
    purchaseOrder.setPoNumber("6566969649");
    purchaseOrder.setPoLineNumber("1");
    InventoryDetail inventoryDetail = new InventoryDetail();
    inventoryDetail.setReportedQuantity(5d);
    inventoryDetail.setReportedUom(ReceivingConstants.Uom.EACHES);
    item.setPurchaseOrder(purchaseOrder);
    pack.setItems(Collections.singletonList(item));
    packList.add(pack);
    shipmentEvent.setPackList(packList);
    List<Container> cl = new ArrayList<>();
    Container activeContainer = FixtureMockData.getCompletedContainer();
    activeContainer.setContainerStatus(ReceivingConstants.STATUS_COMPLETE_NO_ASN);
    activeContainer.setTrackingId("LPN 10656 INV 4784");
    activeContainer.setParentTrackingId("339242A060027394");
    activeContainer.getContainerItems().get(0).setPurchaseReferenceLineNumber(null);
    activeContainer.getContainerItems().get(0).setPurchaseReferenceNumber(null);
    activeContainer.getContainerItems().get(0).setTrackingId("LPN 10656 INV 4784");
    activeContainer.getContainerItems().get(0).setItemNumber(586297785l);

    cl.add(activeContainer);
    when(containerPersisterService.getContainerByParentTrackingIdInAndContainerStatus(
            anyList(), eq(FixtureConstants.CONTAINER_STATUS_WO_ASN)))
        .thenReturn(cl);
    doNothing().when(fixtureDeliveryMetadataService).persistsShipmentMetadata(any(Shipment.class));
    doNothing()
        .when(containerPersisterService)
        .saveContainerAndContainerItems(anyList(), anyList());
    doNothing()
        .when(inventoryService)
        .updateInventoryPoDetails(any(InventoryItemPODetailUpdateRequest.class));
    when(transformer.transformList(anyList())).thenReturn(new ArrayList<>());
    doNothing().when(containerService).publishMultipleContainersToInventory(anyList());
    when(configUtils.getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean()))
        .thenReturn(Boolean.TRUE);
    ControlTowerTracker controlTowerTracker =
        ControlTowerTracker.builder()
            .id(1L)
            .lpn("LPN 10656 INV 4784")
            .submissionStatus(EventTargetStatus.PENDING)
            .build();

    when(controlTowerService.resetForTracking("LPN 10656 INV 4784"))
        .thenReturn(controlTowerTracker);
    shipmentReconcileProcessor.execute(shipmentEvent);
    verify(containerPersisterService, times(1))
        .getContainerByParentTrackingIdInAndContainerStatus(anyList(), anyList());
    verify(containerPersisterService, times(1))
        .saveContainerAndContainerItems(containerList.capture(), containerItemList.capture());
    verify(controlTowerService, times(1))
        .putAwayInventory(anyList(), any(ControlTowerTracker.class));
    verify(containerService, times(1)).publishMultipleContainersToInventory(any());
    List<Container> containers = containerList.getValue();
    List<ContainerItem> containerItems = containerItemList.getValue();
    assertEquals(containers.size(), 1);
    assertEquals(containerItems.size(), 1);
    ContainerItem containerItem = containerItems.get(0);
    assertEquals(containerItem.getPurchaseReferenceNumber(), "6566969649");
    assertEquals(containerItem.getPurchaseReferenceLineNumber(), Integer.valueOf(1));
    assertEquals(containerItem.getQuantity(), Integer.valueOf(5));
  }

  @Test
  public void testExecuteStep_NoMatchingPack() {
    ShipmentEvent shipmentEvent = new ShipmentEvent();
    Shipment shipment = new Shipment();
    shipment.setShipmentNumber("1");
    shipmentEvent.setShipment(shipment);
    List<Pack> packList = new ArrayList<>();
    Pack pack = new Pack();
    pack.setPackNumber("339242A060027394");
    Item item = new Item();
    item.setItemNumber(586297785l);
    PurchaseOrder purchaseOrder = new PurchaseOrder();
    purchaseOrder.setPoNumber("6566969649");
    purchaseOrder.setPoLineNumber("1");
    InventoryDetail inventoryDetail = new InventoryDetail();
    inventoryDetail.setReportedQuantity(5d);
    inventoryDetail.setReportedUom(ReceivingConstants.Uom.EACHES);
    item.setPurchaseOrder(purchaseOrder);
    pack.setItems(Collections.singletonList(item));
    packList.add(pack);
    shipmentEvent.setPackList(packList);
    List<Container> cl = new ArrayList<>();
    Container activeContainer = FixtureMockData.getCompletedContainer();
    activeContainer.setContainerStatus(ReceivingConstants.STATUS_COMPLETE_NO_ASN);
    activeContainer.setTrackingId("LPN 10656 INV 4784");
    activeContainer.setParentTrackingId("339242A060027395"); // Different tracking id
    activeContainer.getContainerItems().get(0).setPurchaseReferenceLineNumber(null);
    activeContainer.getContainerItems().get(0).setPurchaseReferenceNumber(null);
    activeContainer.getContainerItems().get(0).setTrackingId("LPN 10656 INV 4784");
    activeContainer.getContainerItems().get(0).setItemNumber(586297785l);

    cl.add(activeContainer);
    when(containerPersisterService.getContainerByParentTrackingIdInAndContainerStatus(
            anyList(), eq(FixtureConstants.CONTAINER_STATUS_WO_ASN)))
        .thenReturn(cl);
    doNothing().when(fixtureDeliveryMetadataService).persistsShipmentMetadata(any(Shipment.class));
    doNothing()
        .when(containerPersisterService)
        .saveContainerAndContainerItems(anyList(), anyList());
    doNothing()
        .when(inventoryService)
        .updateInventoryPoDetails(any(InventoryItemPODetailUpdateRequest.class));
    when(transformer.transformList(anyList())).thenReturn(new ArrayList<>());
    doNothing().when(containerService).publishMultipleContainersToInventory(anyList());
    when(configUtils.getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean()))
        .thenReturn(Boolean.FALSE);
    shipmentReconcileProcessor.execute(shipmentEvent);
    verify(containerPersisterService, times(1))
        .getContainerByParentTrackingIdInAndContainerStatus(anyList(), anyList());
    verify(containerPersisterService, times(0))
        .saveContainerAndContainerItems(anyList(), anyList());
  }

  @Test
  public void testExecuteStep_NoMatchingItem() {
    ShipmentEvent shipmentEvent = new ShipmentEvent();
    Shipment shipment = new Shipment();
    shipment.setShipmentNumber("1");
    shipmentEvent.setShipment(shipment);
    List<Pack> packList = new ArrayList<>();
    Pack pack = new Pack();
    pack.setPackNumber("339242A060027394");
    Destination handledOnBehalfOf = new Destination();
    handledOnBehalfOf.setNumber("10805");
    handledOnBehalfOf.setCountryCode("US");
    pack.setHandledOnBehalfOf(handledOnBehalfOf);
    Item item = new Item();
    item.setItemNumber(586297785l);
    PurchaseOrder purchaseOrder = new PurchaseOrder();
    purchaseOrder.setPoNumber("6566969649");
    purchaseOrder.setPoLineNumber("1");
    InventoryDetail inventoryDetail = new InventoryDetail();
    inventoryDetail.setReportedQuantity(5d);
    inventoryDetail.setReportedUom(ReceivingConstants.Uom.EACHES);
    item.setPurchaseOrder(purchaseOrder);
    pack.setItems(Collections.singletonList(item));
    packList.add(pack);
    shipmentEvent.setPackList(packList);
    List<Container> cl = new ArrayList<>();
    Container activeContainer = FixtureMockData.getCompletedContainer();
    activeContainer.setContainerStatus(ReceivingConstants.STATUS_COMPLETE_NO_ASN);
    activeContainer.setTrackingId("LPN 10656 INV 4784");
    activeContainer.setParentTrackingId("339242A060027394"); // Different tracking id
    activeContainer.getContainerItems().get(0).setPurchaseReferenceLineNumber(null);
    activeContainer.getContainerItems().get(0).setPurchaseReferenceNumber(null);
    activeContainer.getContainerItems().get(0).setTrackingId("LPN 10656 INV 4784");
    activeContainer.getContainerItems().get(0).setItemNumber(586297786l);

    cl.add(activeContainer);
    when(containerPersisterService.getContainerByParentTrackingIdInAndContainerStatus(
            anyList(), eq(FixtureConstants.CONTAINER_STATUS_WO_ASN)))
        .thenReturn(cl);
    doNothing().when(fixtureDeliveryMetadataService).persistsShipmentMetadata(any(Shipment.class));
    doNothing()
        .when(containerPersisterService)
        .saveContainerAndContainerItems(anyList(), anyList());
    doNothing()
        .when(inventoryService)
        .updateInventoryPoDetails(any(InventoryItemPODetailUpdateRequest.class));
    when(transformer.transformList(anyList())).thenReturn(new ArrayList<>());
    doNothing().when(containerService).publishMultipleContainersToInventory(anyList());
    when(configUtils.getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean()))
        .thenReturn(Boolean.FALSE);
    shipmentReconcileProcessor.execute(shipmentEvent);
    verify(containerPersisterService, times(1))
        .getContainerByParentTrackingIdInAndContainerStatus(anyList(), anyList());
    verify(containerPersisterService, times(0))
        .saveContainerAndContainerItems(anyList(), anyList());
  }
}
