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
import com.walmart.move.nim.receiving.fixture.model.ShipmentEvent;
import com.walmart.move.nim.receiving.fixture.service.FixtureDeliveryMetadataService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mockito.*;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ShipmentPersistProcessorTest extends ReceivingTestBase {
  @InjectMocks private ShipmentPersistProcessor shipmentPersistProcessor;

  @Mock private ItemREPServiceClient itemREPServiceClient;

  @Mock private ContainerPersisterService containerPersisterService;

  @Mock private FixtureDeliveryMetadataService fixtureDeliveryMetadataService;

  @Captor private ArgumentCaptor<List<Container>> containerList;

  @Captor private ArgumentCaptor<List<ContainerItem>> containerItemList;

  @Mock private FixtureManagedConfig fixtureManagedConfig;

  @Mock private ItemMDMServiceClient itemMDMServiceClient;

  private static Gson gson = new Gson();

  @BeforeClass
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(
        shipmentPersistProcessor,
        "selfReferenceShipmentPersistProcessor",
        shipmentPersistProcessor);
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
    shipmentPersistProcessor.execute(shipmentEvent);
    verify(containerPersisterService, times(0)).getExistingParentTrackingIds(anyList());
  }

  @Test
  public void testCanExecute_NoExistingContainersWithPackId() {
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
    when(fixtureManagedConfig.isItemMdmEnabled()).thenReturn(true);
    when(itemMDMServiceClient.retrieveItemDetails(anySet(), any(HttpHeaders.class), anyBoolean()))
        .thenReturn(getItemDetails());
    when(containerPersisterService.getExistingParentTrackingIds(anyList())).thenReturn(null);
    doNothing()
        .when(containerPersisterService)
        .saveContainerAndContainerItems(anyList(), anyList());
    when(itemREPServiceClient.getItemDetailsOfItemNumbersFromREP(anySet()))
        .thenReturn(new HashMap<>());
    shipmentPersistProcessor.execute(shipmentEvent);
    verify(containerPersisterService, times(1)).getExistingParentTrackingIds(anyList());
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
  public void testCanExecute_ExistingContainersWithPackId() {
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

    when(containerPersisterService.getExistingParentTrackingIds(anyList()))
        .thenReturn(Collections.singletonList("339242A060027394"));
    shipmentPersistProcessor.execute(shipmentEvent);
    verify(containerPersisterService, times(1)).getExistingParentTrackingIds(anyList());
    verify(itemREPServiceClient, times(0)).getItemDetailsOfItemNumbersFromREP(anySet());
    verify(containerPersisterService, times(0))
        .saveContainerAndContainerItems(anyList(), anyList());
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
}
