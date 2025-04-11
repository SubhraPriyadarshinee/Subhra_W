package com.walmart.move.nim.receiving.core.transformer;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

import com.walmart.move.nim.receiving.core.common.BusinessTransactionType;
import com.walmart.move.nim.receiving.core.common.LabelDataConstants;
import com.walmart.move.nim.receiving.core.common.ProducerIdentifier;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.model.ei.Inventory;
import com.walmart.move.nim.receiving.core.model.ei.InventoryDetails;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class InventoryTransformerTest {

  @InjectMocks private InventoryTransformer inventoryTransformer;
  @Mock AppConfig appConfig;

  @BeforeMethod
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testDCReceivingTransformToInventory() throws Exception {
    Container container = getMockContainer();
    when(appConfig.getEiSourceNodeDivisionCode()).thenReturn(7);
    when(appConfig.getEiDestinationNodeDivisionCode()).thenReturn(1);
    InventoryDetails inventoryDetails =
        inventoryTransformer.transformToInventory(container, ReceivingConstants.DC_RECEIVING);
    assertNotNull(inventoryDetails);
    assertNotNull(inventoryDetails.getInventory());
    Inventory inventory = inventoryDetails.getInventory().get(0);
    assertNotNull(inventory);
    assertNotNull(inventory.getEventInfo());
    assertEquals(inventory.getWhseAreaCode().intValue(), 1);
    assertNotNull(inventory.getEventInfo().getProducerIdentifier());
    assertEquals(
        inventory.getEventInfo().getProducerIdentifier().intValue(),
        ProducerIdentifier.DC_RECEIVING_IDENTIFIER.getValue());
    assertNotNull(inventory.getEventInfo().getEventFromCreationTs());
    assertNotNull(inventory.getEventInfo().getEventReceivedTs());
    assertNotNull(inventory.getQuantity());
    assertNotNull(inventory.getQuantity().getValue());
    assertTrue(inventory.getQuantity().getValue() > 0);
    assertNotNull(inventory.getChannelType());
    assertNotNull(inventory.getItemIdentifier());
    assertNull(inventory.getItemIdentifier().getRequestedItemNbr());
    assertNotNull(inventory.getIdempotentKey());
    assertNotNull(inventory.getTrackingNumber());
    assertNotNull(inventory.getDocuments());
    assertNotNull(inventory.getNodes());
    assertNotNull(inventory.getNodes().getToNode());
    assertNotNull(inventory.getNodes().getDestinationNode());
    assertNull(inventory.getNodes().getFromNode());
    assertNull(inventory.getMessageCode());
  }

  @Test
  public void testDCPicksTransformToInventory() throws Exception {
    Container container = getMockContainer();
    when(appConfig.getEiSourceNodeDivisionCode()).thenReturn(7);
    when(appConfig.getEiDestinationNodeDivisionCode()).thenReturn(1);
    InventoryDetails inventoryDetails =
        inventoryTransformer.transformToInventory(container, ReceivingConstants.DC_PICKS);
    assertNotNull(inventoryDetails);
    assertNotNull(inventoryDetails.getInventory());
    Inventory inventory = inventoryDetails.getInventory().get(0);
    assertNotNull(inventory);
    assertNotNull(inventory.getEventInfo());
    assertEquals(inventory.getWhseAreaCode().intValue(), 1);
    assertNotNull(inventory.getEventInfo().getProducerIdentifier());
    assertEquals(
        inventory.getEventInfo().getProducerIdentifier().intValue(),
        ProducerIdentifier.DC_PICKS_IDENTIFIER.getValue());
    assertNotNull(inventory.getEventInfo().getEventFromCreationTs());
    assertNotNull(inventory.getEventInfo().getEventReceivedTs());
    assertNotNull(inventory.getQuantity());
    assertNotNull(inventory.getQuantity().getValue());
    assertTrue(inventory.getQuantity().getValue() > 0);
    assertNotNull(inventory.getChannelType());
    assertNotNull(inventory.getItemIdentifier());
    assertNotNull(inventory.getItemIdentifier().getRequestedItemNbr());
    assertNotNull(inventory.getIdempotentKey());
    assertNotNull(inventory.getTrackingNumber());
    assertNotNull(inventory.getDocuments());
    assertNotNull(inventory.getNodes());
    assertNotNull(inventory.getNodes().getToNode());
    assertNotNull(inventory.getNodes().getDestinationNode());
    assertNotNull(inventory.getNodes().getFromNode());
    assertNotNull(inventory.getMessageCode());
  }

  @Test
  public void testDCVoidTransformToInventory() throws Exception {
    Container container = getMockContainer();
    when(appConfig.getEiSourceNodeDivisionCode()).thenReturn(7);
    when(appConfig.getEiDestinationNodeDivisionCode()).thenReturn(1);
    InventoryDetails inventoryDetails =
        inventoryTransformer.transformToInventory(container, ReceivingConstants.DC_VOID);
    assertNotNull(inventoryDetails);
    assertNotNull(inventoryDetails.getInventory());
    Inventory inventory = inventoryDetails.getInventory().get(0);
    assertNotNull(inventory);

    assertNotNull(inventory.getEventInfo());
    assertNotNull(inventory.getEventInfo().getProducerIdentifier());
    assertEquals(inventory.getWhseAreaCode().intValue(), 1);
    assertEquals(
        inventory.getEventInfo().getProducerIdentifier().intValue(),
        ProducerIdentifier.DC_VOID_IDENTIFIER.getValue());
    assertNotNull(inventory.getEventInfo().getEventFromCreationTs());
    assertNotNull(inventory.getEventInfo().getEventReceivedTs());
    assertNotNull(inventory.getQuantity());
    assertNotNull(inventory.getQuantity().getValue());
    assertTrue(inventory.getQuantity().getValue() < 0);
    assertNotNull(inventory.getChannelType());
    assertNotNull(inventory.getItemIdentifier());
    assertNotNull(inventory.getItemIdentifier().getRequestedItemNbr());
    assertNotNull(inventory.getIdempotentKey());
    assertNotNull(inventory.getTrackingNumber());
    assertNotNull(inventory.getDocuments());
    assertNotNull(inventory.getNodes());
    assertNotNull(inventory.getNodes().getToNode());
    assertNotNull(inventory.getNodes().getDestinationNode());
    assertNotNull(inventory.getNodes().getFromNode());
    assertNotNull(inventory.getMessageCode());
  }

  @Test
  public void testDCPicksTransformToInventoryWithEmptyItemUPC() throws Exception {
    Container container = getMockContainer();
    when(appConfig.getEiSourceNodeDivisionCode()).thenReturn(7);
    when(appConfig.getEiDestinationNodeDivisionCode()).thenReturn(1);
    container.getContainerItems().get(0).setItemUPC(null);
    InventoryDetails inventoryDetails =
        inventoryTransformer.transformToInventory(container, ReceivingConstants.DC_PICKS);
    assertNotNull(inventoryDetails);
    assertNotNull(inventoryDetails.getInventory());
    Inventory inventory = inventoryDetails.getInventory().get(0);
    assertNotNull(inventory);
    assertNotNull(inventory.getEventInfo());
    assertNotNull(inventory.getEventInfo().getEventFromCreationTs());
    assertNotNull(inventory.getEventInfo().getEventReceivedTs());
    assertNotNull(inventory.getQuantity());
    assertNotNull(inventory.getChannelType());
    assertNotNull(inventory.getItemIdentifier());
    assertNotNull(inventory.getItemIdentifier().getRequestedItemNbr());
    assertNotNull(inventory.getIdempotentKey());
    assertNotNull(inventory.getTrackingNumber());
    assertNotNull(inventory.getDocuments());
    assertNotNull(inventory.getNodes());
    assertNotNull(inventory.getNodes().getToNode());
    assertNotNull(inventory.getNodes().getDestinationNode());
    assertNotNull(inventory.getNodes().getFromNode());
    assertNotNull(inventory.getMessageCode());
  }

  private Container getMockContainer() {
    Container container = new Container();
    container.setFacilityNum(6043);
    container.setDeliveryNumber(123L);
    container.setInstructionId(12345L);
    container.setTrackingId("lpn123");
    container.setParentTrackingId(null);
    container.setCreateUser("sysadmin");
    container.setCompleteTs(new Date());
    container.setLastChangedUser("sysadmin");
    container.setLastChangedTs(new Date());
    container.setPublishTs(new Date());

    Map<String, String> destination = new HashMap<>();
    destination.put(LabelDataConstants.LABEL_FIELD_BU_NUMBER, "6043");
    destination.put(LabelDataConstants.LABEL_FIELD_COUNTRY_CODE, "US");
    container.setDestination(destination);

    Map<String, Object> containerMiscInfo = new HashMap<>();
    containerMiscInfo.put(ReceivingConstants.PURCHASE_REF_LEGACY_TYPE, "23");
    container.setContainerMiscInfo(containerMiscInfo);

    ContainerItem containerItem = new ContainerItem();
    containerItem.setItemUPC("UPC19919199191919191");
    containerItem.setTrackingId("lpn123");
    containerItem.setPurchaseReferenceNumber("PO1");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setItemNumber(12345678L);
    containerItem.setQuantity(80);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem.setVnpkQty(4);
    containerItem.setWhpkQty(4);
    containerItem.setActualTi(5);
    containerItem.setActualHi(4);
    containerItem.setPurchaseCompanyId(1);
    containerItem.setPoTypeCode(BusinessTransactionType.PURCHASE_ORDER.getPoTypeCode());

    container.setContainerItems(Collections.singletonList(containerItem));
    return container;
  }

  @Test
  public void testDCShipVoidTransformToInventory() throws Exception {
    Container container = getMockContainer();
    when(appConfig.getEiSourceNodeDivisionCode()).thenReturn(7);
    when(appConfig.getEiDestinationNodeDivisionCode()).thenReturn(1);
    InventoryDetails inventoryDetails =
        inventoryTransformer.transformToInventory(container, ReceivingConstants.DC_SHIP_VOID);
    assertNotNull(inventoryDetails);
    assertNotNull(inventoryDetails.getInventory());
    Inventory inventory = inventoryDetails.getInventory().get(0);
    assertNotNull(inventory);

    assertNotNull(inventory.getEventInfo());
    assertNotNull(inventory.getEventInfo().getProducerIdentifier());
    assertEquals(inventory.getWhseAreaCode().intValue(), 1);
    assertEquals(
        inventory.getEventInfo().getProducerIdentifier().intValue(),
        ProducerIdentifier.DC_SHIP_VOID_IDENTIFIER.getValue());
    assertNotNull(inventory.getEventInfo().getEventFromCreationTs());
    assertNotNull(inventory.getEventInfo().getEventReceivedTs());
    assertNotNull(inventory.getQuantity());
    assertNotNull(inventory.getQuantity().getValue());
    assertTrue(inventory.getQuantity().getValue() < 0);
    assertNotNull(inventory.getChannelType());
    assertNotNull(inventory.getItemIdentifier());
    assertNotNull(inventory.getItemIdentifier().getRequestedItemNbr());
    assertNotNull(inventory.getIdempotentKey());
    assertNotNull(inventory.getTrackingNumber());
    assertNull(inventory.getDocuments());
    assertNotNull(inventory.getNodes());
    assertNotNull(inventory.getNodes().getToNode());
    assertNotNull(inventory.getNodes().getDestinationNode());
    assertNotNull(inventory.getNodes().getFromNode());
    assertNotNull(inventory.getMessageCode());
  }

  @Test
  public void testDCTrueOutTransformToInventory() throws Exception {
    Container container = getMockContainer();
    when(appConfig.getEiSourceNodeDivisionCode()).thenReturn(7);
    when(appConfig.getEiDestinationNodeDivisionCode()).thenReturn(1);
    InventoryDetails inventoryDetails =
        inventoryTransformer.transformToInventory(container, ReceivingConstants.DC_TRUE_OUT);
    assertNotNull(inventoryDetails);
    assertNotNull(inventoryDetails.getInventory());
    Inventory inventory = inventoryDetails.getInventory().get(0);
    assertNotNull(inventory);

    assertNotNull(inventory.getEventInfo());
    assertNotNull(inventory.getEventInfo().getProducerIdentifier());
    assertEquals(inventory.getWhseAreaCode().intValue(), 1);
    assertEquals(
        inventory.getEventInfo().getProducerIdentifier().intValue(),
        ProducerIdentifier.DC_TRUE_OUT_IDENTIFIER.getValue());
    assertNotNull(inventory.getEventInfo().getEventFromCreationTs());
    assertNotNull(inventory.getEventInfo().getEventReceivedTs());
    assertNotNull(inventory.getQuantity());
    assertNotNull(inventory.getQuantity().getValue());
    assertTrue(inventory.getQuantity().getValue() < 0);
    assertNotNull(inventory.getChannelType());
    assertNotNull(inventory.getItemIdentifier());
    assertNotNull(inventory.getItemIdentifier().getRequestedItemNbr());
    assertNotNull(inventory.getIdempotentKey());
    assertNotNull(inventory.getTrackingNumber());
    assertNull(inventory.getDocuments());
    assertNotNull(inventory.getNodes());
    assertNotNull(inventory.getNodes().getToNode());
    assertNotNull(inventory.getNodes().getDestinationNode());
    assertNotNull(inventory.getNodes().getFromNode());
    assertNotNull(inventory.getMessageCode());
  }

  @Test
  public void testDCXDKVoidTransformToInventory() throws Exception {
    Container container = getMockContainer();
    when(appConfig.getEiSourceNodeDivisionCode()).thenReturn(7);
    when(appConfig.getEiDestinationNodeDivisionCode()).thenReturn(1);
    InventoryDetails inventoryDetails =
        inventoryTransformer.transformToInventory(container, ReceivingConstants.DC_XDK_VOID);
    assertNotNull(inventoryDetails);
    assertNotNull(inventoryDetails.getInventory());
    Inventory inventory = inventoryDetails.getInventory().get(0);
    assertNotNull(inventory);

    assertNotNull(inventory.getEventInfo());
    assertNotNull(inventory.getEventInfo().getProducerIdentifier());
    assertEquals(inventory.getWhseAreaCode().intValue(), 1);
    assertEquals(
        inventory.getEventInfo().getProducerIdentifier().intValue(),
        ProducerIdentifier.DC_XDK_VOID_IDENTIFIER.getValue());
    assertNotNull(inventory.getEventInfo().getEventFromCreationTs());
    assertNotNull(inventory.getEventInfo().getEventReceivedTs());
    assertNotNull(inventory.getQuantity());
    assertNotNull(inventory.getQuantity().getValue());
    assertTrue(inventory.getQuantity().getValue() < 0);
    assertNotNull(inventory.getChannelType());
    assertNotNull(inventory.getItemIdentifier());
    assertNotNull(inventory.getItemIdentifier().getRequestedItemNbr());
    assertNotNull(inventory.getIdempotentKey());
    assertNotNull(inventory.getTrackingNumber());
    assertNull(inventory.getDocuments());
    assertNotNull(inventory.getNodes());
    assertNotNull(inventory.getNodes().getToNode());
    assertNotNull(inventory.getNodes().getDestinationNode());
    assertNotNull(inventory.getNodes().getFromNode());
    assertNotNull(inventory.getMessageCode());
  }
}
