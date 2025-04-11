package com.walmart.move.nim.receiving.core.common;

import static org.testng.Assert.*;
import static org.testng.Assert.assertFalse;

import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.mock.data.MockContainer;
import com.walmart.move.nim.receiving.core.mock.data.WitronContainer;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.ActiveProfiles;
import org.testng.annotations.Test;

@ActiveProfiles("test")
public class ContainerUtilsTest {
  @InjectMocks private ContainerUtils containerUtils;

  @Mock private MockContainer mockContainer;

  @Test
  public void singleItemContainerWeight() {
    Container ctr = WitronContainer.getContainer1();
    Float weight = ContainerUtils.calculateWeight(ctr);
    assertEquals(weight, new Float(160.0));
  }

  @Test
  public void multipleItemContainerWeight() {
    Container ctr = WitronContainer.getContainer1();
    ContainerItem secondItem = WitronContainer.getContainerItem(46L);
    ctr.getContainerItems().add(secondItem);
    assertEquals(ContainerUtils.calculateWeight(ctr), new Float(320.0));
  }

  @Test
  public void parentWithChildContainerWeight() {
    Container parentCtr = WitronContainer.getContainer1();
    Set<Container> containers = new HashSet<Container>();
    Container childCtr = WitronContainer.getContainer2();
    containers.add(childCtr);
    parentCtr.setChildContainers(containers);
    assertEquals(ContainerUtils.calculateWeight(parentCtr), new Float(320.0));
  }

  @Test
  public void singleItemContainerWeightUOM() {
    Container ctr = WitronContainer.getContainer1();
    String uom = ContainerUtils.getDefaultWeightUOM(ctr);
    assertEquals(uom, "LB");
  }

  @Test
  public void multipleItemContainerWeightUOM() {
    Container ctr = WitronContainer.getContainer1();
    ContainerItem secondItem = WitronContainer.getContainerItem(46L);
    ctr.getContainerItems().add(secondItem);
    String uom = ContainerUtils.getDefaultWeightUOM(ctr);
    assertEquals(uom, "LB");
  }

  @Test
  public void parentWithChildContainerWeightUOM() {
    Container parentCtr = new Container();
    Set<Container> containers = new HashSet<Container>();
    Container childCtr = WitronContainer.getContainer2();
    containers.add(childCtr);
    parentCtr.setChildContainers(containers);
    String uom = ContainerUtils.getDefaultWeightUOM(parentCtr);
    assertEquals(uom, "LB");
  }

  @Test
  public void test_calculateActualHi() {
    Container container = WitronContainer.getContainer1();
    final Integer actualHiForQuantity =
        ContainerUtils.calculateActualHi(container.getContainerItems().get(0));
    assertNotNull(actualHiForQuantity);
    assertEquals(actualHiForQuantity, new Integer(10));
  }

  @Test
  public void testImportsAttributes() {
    String deliveryDocumentString =
        " {\n"
            + "        \"documentNbr\": \"3515421377\",\n"
            + "        \"purchaseReferenceNumber\": \"3515421377\",\n"
            + "        \"poDCNumber\": \"6561\",\n"
            + "        \"baseDivCode\": \"WM\",\n"
            + "        \"purchaseReferenceLegacyType\": \"33\",\n"
            + "        \"poTypeCode\": 33,\n"
            + "        \"purchaseCompanyId\": 1,\n"
            + "        \"importInd\": true,\n"
            + "        \"deliveryDocumentLines\": [\n"
            + "            {\n"
            + "                \"purchaseRefType\": \"CROSSU\",\n"
            + "                \"purchaseReferenceNumber\": \"3515421377\",\n"
            + "                \"purchaseReferenceLineNumber\": 9,\n"
            + "                \"purchaseReferenceLineStatus\": \"PARTIALLY_RECEIVED\",\n"
            + "                \"itemNbr\": 574322171,\n"
            + "                \"itemUPC\": \"00673419302784\",\n"
            + "                \"caseUPC\": \"10673419302781\",\n"
            + "                \"expectedQty\": 600,\n"
            + "                \"expectedQtyUOM\": \"ZA\",\n"
            + "                \"vnpkQty\": 3,\n"
            + "                \"whpkQty\": 3,\n"
            + "                \"orderableQuantity\": 3,\n"
            + "                \"warehousePackQuantity\": 3,\n"
            + "                \"vendorStockNumber\": \"6251454\",\n"
            + "                \"vendorPackCost\": 41.97,\n"
            + "                \"whpkSell\": 42.41,\n"
            + "                \"vnpkWgtQty\": 2.491,\n"
            + "                \"vnpkWgtUom\": \"LB\",\n"
            + "                \"vnpkcbqty\": 0.405,\n"
            + "                \"vnpkcbuomcd\": \"CF\",\n"
            + "                \"event\": \"POS REPLEN\",\n"
            + "                \"palletTi\": 30,\n"
            + "                \"palletHi\": 4,\n"
            + "                \"freightBillQty\": 10,\n"
            + "                \"department\": \"7\",\n"
            + "                \"isHazmat\": false,\n"
            + "                \"isConveyable\": true,\n"
            + "                \"overageQtyLimit\": 11,\n"
            + "                \"overageThresholdQty\": 11,\n"
            + "                \"color\": \"76118\",\n"
            + "                \"size\": \"\",\n"
            + "                \"itemDescription1\": \"LG SH BATCYCLE BATTL\",\n"
            + "                \"itemDescription2\": \"NEW F20 WK 28\",\n"
            + "                \"additionalInfo\" : {"
            + "                                      \"warehouseGroupCode\": \"P\","
            + "                                      \"isNewItem\": false, "
            + "                                      \"warehouseAreaCode\": \"8\", "
            + "                                      \"profiledWarehouseArea\": \"CPS\","
            + "                                      \"warehouseRotationTypeCode\": \"3\","
            + "                                      \"recall\": false,"
            + "                                      \"weight\": 13.0,"
            + "                                      \"isVariableWeight\": true,"
            + "                                      \"weightFormatTypeCode\": \"F\","
            + "                                      \"warehouseMinLifeRemainingToReceive\": 70,"
            + "                                      \"weightUOM\": \"LB\""
            + "                                     }"
            + "            }\n"
            + "        ],\n"
            + "        \"deptNumber\": \"7\",\n"
            + "        \"vendorNumber\": \"299404\",\n"
            + "        \"weight\": 15816.0,\n"
            + "        \"freightTermCode\": \"COLL\",\n"
            + "        \"poDcCountry\": \"US\",\n"
            + "        \"financialGroupCode\": \"US\"\n"
            + "    }";
    DeliveryDocument deliveryDocument =
        JacksonParser.convertJsonToObject(deliveryDocumentString, DeliveryDocument.class);
    Container container = MockContainer.getContainer();
    ContainerUtils.setAttributesForImports(
        deliveryDocument.getPoDCNumber(),
        deliveryDocument.getPoDcCountry(),
        deliveryDocument.getImportInd(),
        container.getContainerItems().get(0));
    assertTrue(container.getContainerItems().get(0).getImportInd());
    assertEquals(container.getContainerItems().get(0).getPoDcCountry(), "US");
    assertEquals(container.getContainerItems().get(0).getPoDCNumber(), "6561");
  }

  @Test
  public void testAdjustContainerByQtyWithoutTiHi_HappyFlow() {

    Container container = MockContainer.getContainerInfo();
    ContainerItem containerItem = container.getContainerItems().get(0);
    containerItem.setVnpkWgtUom(ReceivingConstants.Uom.LB);
    containerItem.setQuantity(1);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES); // by default EA
    containerItem.setActualHi(9);

    Container retContainer = containerUtils.adjustContainerByQtyWithoutTiHi(container, 90);
    ContainerItem adjustedContainerItem = container.getContainerItems().get(0);

    assertEquals(retContainer.getWeight(), 30.0f);
    assertEquals(adjustedContainerItem.getQuantity(), Integer.valueOf(90));
    assertEquals(adjustedContainerItem.getActualHi(), Integer.valueOf(9));
    assertEquals(retContainer.getWeightUOM(), ReceivingConstants.Uom.LB);
  }

  @Test
  public void
      testIsAtlasConvertedItemReturnsTrueWhenAtlasConvertedFlagIsTrueInContainerItemMiscInfo() {
    Container container = WitronContainer.getContainer1();
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(ReceivingConstants.IS_ATLAS_CONVERTED_ITEM, "true");
    container.getContainerItems().get(0).setContainerItemMiscInfo(containerItemMiscInfo);
    boolean isAtlasConvertedItem =
        ContainerUtils.isAtlasConvertedItem(container.getContainerItems().get(0));
    assertTrue(isAtlasConvertedItem);
  }

  @Test
  public void
      testIsAtlasConvertedItemReturnsFalseWhenAtlasConvertedFlagIsFalseInContainerItemMiscInfo() {
    Container container = WitronContainer.getContainer1();
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(ReceivingConstants.IS_ATLAS_CONVERTED_ITEM, "false");
    container.getContainerItems().get(0).setContainerItemMiscInfo(containerItemMiscInfo);
    boolean isAtlasConvertedItem =
        ContainerUtils.isAtlasConvertedItem(container.getContainerItems().get(0));
    assertFalse(isAtlasConvertedItem);
  }

  @Test
  public void testIsAtlasConvertedItemReturnsFalseWhenContainerItemMiscInfoIsEmpty() {
    boolean isAtlasConvertedItem =
        ContainerUtils.isAtlasConvertedItem(
            WitronContainer.getContainer1().getContainerItems().get(0));
    assertFalse(isAtlasConvertedItem);
  }

  @Test
  public void testCopyContainerProperties() {
    Container container1 = MockContainer.getContainer();
    Container container2 = new Container();
    ContainerUtils.copyContainerProperties(container2, container1);

    assertEquals(container1.getMessageId(), container2.getMessageId());
    assertEquals(container1.getLocation(), container2.getLocation());
    assertEquals(container1.getDeliveryNumber(), container2.getDeliveryNumber());
    assertEquals(container1.getContainerType(), container2.getContainerType());
    assertEquals(container1.getContainerStatus(), container2.getContainerStatus());
    assertEquals(container1.getCtrShippable(), container2.getCtrShippable());
    assertEquals(container1.getCtrReusable(), container2.getCtrReusable());
    assertEquals(container1.getInventoryStatus(), container2.getInventoryStatus());
    assertEquals(container1.getCompleteTs(), container2.getCompleteTs());
    assertEquals(container1.getLastChangedTs(), container2.getLastChangedTs());
    assertEquals(container1.getLastChangedUser(), container2.getLastChangedUser());
    assertEquals(container1.getOnConveyor(), container2.getOnConveyor());
    assertEquals(container1.getContainerException(), container2.getContainerException());
    assertEquals(container1.getActivityName(), container2.getActivityName());
    assertEquals(container1.getContainerMiscInfo(), container2.getContainerMiscInfo());
  }

  @Test
  public void testCopyContainerItemProperties() {
    ContainerItem containerItem1 = MockContainer.getMockContainerItem().get(0);
    ContainerItem containerItem2 = new ContainerItem();
    ContainerUtils.copyContainerItemProperties(containerItem2, containerItem1);

    assertEquals(
        containerItem1.getPurchaseReferenceNumber(), containerItem2.getPurchaseReferenceNumber());
    assertEquals(
        containerItem1.getPurchaseReferenceLineNumber(),
        containerItem2.getPurchaseReferenceLineNumber());
    assertEquals(
        containerItem1.getInboundChannelMethod(), containerItem2.getInboundChannelMethod());
    assertEquals(
        containerItem1.getOutboundChannelMethod(), containerItem2.getOutboundChannelMethod());
    assertEquals(
        containerItem1.getTotalPurchaseReferenceQty(),
        containerItem2.getTotalPurchaseReferenceQty());
    assertEquals(containerItem1.getPurchaseCompanyId(), containerItem2.getPurchaseCompanyId());
    assertEquals(containerItem1.getDeptNumber(), containerItem2.getDeptNumber());
    assertEquals(containerItem1.getPoDeptNumber(), containerItem2.getPoDeptNumber());
    assertEquals(containerItem1.getGtin(), containerItem2.getGtin());
    assertEquals(containerItem1.getQuantity(), containerItem2.getQuantity());
    assertEquals(containerItem1.getQuantityUOM(), containerItem2.getQuantityUOM());
    assertEquals(containerItem1.getVnpkQty(), containerItem2.getVnpkQty());
    assertEquals(containerItem1.getWhpkQty(), containerItem2.getWhpkQty());
    assertEquals(containerItem1.getVendorPackCost(), containerItem2.getVendorPackCost());
    assertEquals(containerItem1.getWhpkSell(), containerItem2.getWhpkSell());
    assertEquals(containerItem1.getBaseDivisionCode(), containerItem2.getBaseDivisionCode());
    assertEquals(
        containerItem1.getFinancialReportingGroupCode(),
        containerItem2.getFinancialReportingGroupCode());
    assertEquals(containerItem1.getActualTi(), containerItem2.getActualTi());
    assertEquals(containerItem1.getActualHi(), containerItem2.getActualHi());
    assertEquals(
        containerItem1.getContainerItemMiscInfo(), containerItem2.getContainerItemMiscInfo());
  }
}
