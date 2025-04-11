package com.walmart.move.nim.receiving.fixture.event.processor;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.model.gdm.v3.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.pack.PurchaseOrder;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.fixture.client.ItemMDMServiceClient;
import com.walmart.move.nim.receiving.fixture.client.ItemREPServiceClient;
import com.walmart.move.nim.receiving.fixture.config.FixtureManagedConfig;
import com.walmart.move.nim.receiving.fixture.mock.data.FixtureMockData;
import com.walmart.move.nim.receiving.fixture.model.ShipmentEvent;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import org.mockito.*;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ShipmentUpdateEventProcessorTest extends ReceivingTestBase {

  @InjectMocks private ShipmentUpdateEventProcessor shipmentUpdateEventProcessor;

  @Mock private ItemREPServiceClient itemREPServiceClient;

  @Mock private ContainerPersisterService containerPersisterService;

  @Captor private ArgumentCaptor<List<Container>> containerList;

  @Captor private ArgumentCaptor<List<ContainerItem>> containerItemList;
  @Mock private FixtureManagedConfig fixtureManagedConfig;
  private static Gson gson = new Gson();
  @Mock private ItemMDMServiceClient itemMDMServiceClient;

  @BeforeClass
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(
        shipmentUpdateEventProcessor,
        "selfReferenceShipmentUpdateProcessor",
        shipmentUpdateEventProcessor);
    TenantContext.setFacilityNum(32987);
    TenantContext.setFacilityCountryCode("US");
  }

  @AfterMethod
  public void tearDown() {
    reset(containerPersisterService);
    reset(itemREPServiceClient);
  }

  @Test
  public void testExecuteStep_EmptyPack() {
    ShipmentEvent shipmentEvent = new ShipmentEvent();
    Shipment shipment = new Shipment();
    shipment.setShipmentNumber("1");
    shipmentEvent.setShipment(shipment);
    shipmentUpdateEventProcessor.execute(shipmentEvent);
    verify(containerPersisterService, times(0))
        .getContainerByParentTrackingIdInAndContainerStatus(anyList(), anyList());
  }

  @Test
  public void testExecuteStep_NoExistingContainer() {
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
    shipmentUpdateEventProcessor.execute(shipmentEvent);
    verify(containerPersisterService, times(1))
        .getContainerByParentTrackingIdInAndContainerStatus(anyList(), anyList());
    verify(itemREPServiceClient, times(0)).getItemDetailsOfItemNumbersFromREP(anySet());
  }

  @Test
  public void testCanExecute_MatchingContainerForUpdate() {
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
    item.setInventoryDetail(inventoryDetail);
    item.setPurchaseOrder(purchaseOrder);
    pack.setItems(Collections.singletonList(item));
    Destination destination = new Destination();
    destination.setNumber("318");
    destination.setCountryCode("US");
    pack.setHandledOnBehalfOf(destination);
    packList.add(pack);
    shipmentEvent.setPackList(packList);
    List<Container> cl = new ArrayList<>();
    Container activeContainer = FixtureMockData.getCompletedContainer();
    activeContainer.setContainerStatus(ReceivingConstants.STATUS_PENDING_COMPLETE);
    activeContainer.setTrackingId("LPN 10656 INV 4784");
    activeContainer.setParentTrackingId("339242A060027394");
    activeContainer.getContainerItems().get(0).setPurchaseReferenceLineNumber(null);
    activeContainer.getContainerItems().get(0).setPurchaseReferenceNumber(null);
    activeContainer.getContainerItems().get(0).setTrackingId("LPN 10656 INV 4784");
    activeContainer.getContainerItems().get(0).setItemNumber(586297785l);

    cl.add(activeContainer);
    when(containerPersisterService.getContainerByParentTrackingIdInAndContainerStatus(
            anyList(), anyList()))
        .thenReturn(cl);
    doNothing()
        .when(containerPersisterService)
        .saveContainerAndContainerItems(anyList(), anyList());
    when(itemREPServiceClient.getItemDetailsOfItemNumbersFromREP(anySet()))
        .thenReturn(new HashMap<>());
    when(fixtureManagedConfig.isItemMdmEnabled()).thenReturn(true);
    when(itemMDMServiceClient.retrieveItemDetails(anySet(), any(HttpHeaders.class), anyBoolean()))
        .thenReturn(getItemDetails());
    shipmentUpdateEventProcessor.execute(shipmentEvent);
    verify(containerPersisterService, times(1))
        .getContainerByParentTrackingIdInAndContainerStatus(anyList(), anyList());
    verify(itemREPServiceClient, times(1)).getItemDetailsOfItemNumbersFromREP(anySet());
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
  }

  @Test
  public void testCanExecute_MatchingContainerForUpdateWithAdditionalItem() {
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
    item.setInventoryDetail(inventoryDetail);
    item.setPurchaseOrder(purchaseOrder);
    Item item1 = new Item();
    item1.setItemNumber(586297786l);
    PurchaseOrder purchaseOrder1 = new PurchaseOrder();
    purchaseOrder1.setPoNumber("6566969649");
    purchaseOrder1.setPoLineNumber("2");
    InventoryDetail inventoryDetail1 = new InventoryDetail();
    inventoryDetail1.setReportedQuantity(6d);
    inventoryDetail1.setReportedUom(ReceivingConstants.Uom.EACHES);
    item1.setInventoryDetail(inventoryDetail1);
    item1.setPurchaseOrder(purchaseOrder1);
    pack.setItems(Arrays.asList(item, item1));
    Destination destination = new Destination();
    destination.setNumber("318");
    destination.setCountryCode("US");
    pack.setHandledOnBehalfOf(destination);
    packList.add(pack);
    shipmentEvent.setPackList(packList);
    List<Container> cl = new ArrayList<>();
    Container activeContainer = FixtureMockData.getCompletedContainer();
    activeContainer.setContainerStatus(ReceivingConstants.STATUS_PENDING_COMPLETE);
    activeContainer.setTrackingId("LPN 10656 INV 4784");
    activeContainer.setParentTrackingId("339242A060027394");
    activeContainer.getContainerItems().get(0).setPurchaseReferenceLineNumber(null);
    activeContainer.getContainerItems().get(0).setPurchaseReferenceNumber(null);
    activeContainer.getContainerItems().get(0).setTrackingId("LPN 10656 INV 4784");
    activeContainer.getContainerItems().get(0).setItemNumber(586297785l);

    cl.add(activeContainer);
    when(containerPersisterService.getContainerByParentTrackingIdInAndContainerStatus(
            anyList(), anyList()))
        .thenReturn(cl);
    doNothing()
        .when(containerPersisterService)
        .saveContainerAndContainerItems(anyList(), anyList());
    when(itemREPServiceClient.getItemDetailsOfItemNumbersFromREP(anySet()))
        .thenReturn(new HashMap<>());
    when(fixtureManagedConfig.isItemMdmEnabled()).thenReturn(true);
    when(itemMDMServiceClient.retrieveItemDetails(anySet(), any(HttpHeaders.class), anyBoolean()))
        .thenReturn(getDetails());
    shipmentUpdateEventProcessor.execute(shipmentEvent);
    verify(containerPersisterService, times(1))
        .getContainerByParentTrackingIdInAndContainerStatus(anyList(), anyList());
    verify(itemREPServiceClient, times(1)).getItemDetailsOfItemNumbersFromREP(anySet());
    verify(containerPersisterService, times(1))
        .saveContainerAndContainerItems(containerList.capture(), containerItemList.capture());
    List<Container> containers = containerList.getValue();
    List<ContainerItem> containerItems = containerItemList.getValue();
    assertEquals(containers.size(), 1);
    assertEquals(containerItems.size(), 2);
    ContainerItem containerItem =
        containerItems
            .stream()
            .filter(containerItem2 -> containerItem2.getItemNumber() == 586297785l)
            .collect(Collectors.toList())
            .get(0);
    assertEquals(containerItem.getPurchaseReferenceNumber(), "6566969649");
    assertEquals(containerItem.getPurchaseReferenceLineNumber(), Integer.valueOf(1));
    assertEquals(containerItem.getQuantity(), Integer.valueOf(5));
    ContainerItem containerItemNew =
        containerItems
            .stream()
            .filter(containerItem2 -> containerItem2.getItemNumber() == 586297786l)
            .collect(Collectors.toList())
            .get(0);
    assertEquals(containerItemNew.getPurchaseReferenceNumber(), "6566969649");
    assertEquals(containerItemNew.getPurchaseReferenceLineNumber(), Integer.valueOf(2));
    assertEquals(containerItemNew.getQuantity(), Integer.valueOf(6));
  }

  public static Map<String, Object> getItemDetails() {
    try {
      String itemDetails =
          new File("../../receiving-test/src/main/resources/json/item_details_93.json")
              .getCanonicalPath();
      return gson.fromJson(new String(Files.readAllBytes(Paths.get(itemDetails))), Map.class);
    } catch (IOException e) {
      assertTrue(false, "Unable to read file " + e.getMessage());
      return null;
    }
  }

  public static Map<String, Object> getDetails() {
    try {
      String itemDetails =
          new File("../../receiving-test/src/main/resources/json/item_details_94.json")
              .getCanonicalPath();
      return gson.fromJson(new String(Files.readAllBytes(Paths.get(itemDetails))), Map.class);
    } catch (IOException e) {
      assertTrue(false, "Unable to read file " + e.getMessage());
      return null;
    }
  }
}
