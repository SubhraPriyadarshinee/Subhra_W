package com.walmart.move.nim.receiving.core.framework.transformer;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;

import com.walmart.move.nim.receiving.core.common.InventoryLabelType;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.transformer.ContainerTransformer;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ContainerTransformerTest {

  private Transformer<Container, ContainerDTO> transformer;

  @BeforeClass
  public void init() {
    this.transformer = new ContainerTransformer();
  }

  @Test
  public void testTransform() {
    Container container = createDefaultContainer();
    ContainerDTO containerDTO = this.transformer.transform(container);
    assertEquals(containerDTO.getTrackingId(), container.getTrackingId());
    assertNull(containerDTO.getLabelType());
  }

  private Container createDefaultContainer() {
    Container container = new Container();
    container.setMessageId("a0e24c65-991b-46ac-ae65-dc7bc52edfe6");
    container.setLocation("DEC12");
    container.setDeliveryNumber(Long.parseLong("60077104"));
    container.setParentTrackingId(null);
    container.setContainerType("PALLET");
    container.setCtrShippable(Boolean.FALSE);
    container.setCtrReusable(Boolean.FALSE);
    container.setInventoryStatus("AVAILABLE");
    container.setAudited(true);
    container.setWeight((float) 20.5);
    container.setWeightUOM("LB");
    container.setCube((float) 0.5669999718666077);
    container.setCubeUOM("CF");

    Map<String, String> facility = new HashMap<>();
    facility.put("facilityCountryCode", "US");
    facility.put("facilityNum", "54321");
    container.setFacility(facility);

    List<ContainerItem> containerItems = new ArrayList<>();
    ContainerItem containerItem = new ContainerItem();
    containerItem.setPurchaseReferenceNumber("7519270066");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setInboundChannelMethod("SSTKU");
    containerItem.setTotalPurchaseReferenceQty(100);
    containerItem.setPurchaseCompanyId(1);
    containerItem.setDeptNumber(1);
    containerItem.setPoDeptNumber("92");
    containerItem.setItemNumber(Long.parseLong("553708208"));
    containerItem.setDescription("ROYAL BASMATI 20LB");
    containerItem.setGtin("00745042112013");
    containerItem.setQuantity(80);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem.setVnpkQty(4);
    containerItem.setWhpkQty(4);
    containerItem.setVendorPackCost(25.0);
    containerItem.setWhpkSell(25.0);
    containerItem.setBaseDivisionCode("WM");
    containerItem.setFinancialReportingGroupCode("US");
    containerItem.setVendorNumber(1234);
    containerItem.setPackagedAsUom(ReceivingConstants.Uom.VNPK);
    containerItem.setPromoBuyInd("Y");
    containerItem.setVnpkWgtQty((float) 20.5);
    containerItem.setVnpkcbuomcd("CF");
    containerItem.setCaseUPC("10745042112010");
    containerItem.setFacilityNum(54321);
    containerItem.setFacilityCountryCode("US");
    containerItem.setOutboundChannelMethod("STAPLESTOCK");
    containerItem.setItemUPC("00745042112013");
    containerItem.setVnpkWgtUom("LB");
    containerItem.setCaseQty(1);
    containerItem.setVnpkcbqty((float) 0.5669999718666077);
    containerItems.add(containerItem);
    containerItem.setRotateDate(new Date());
    container.setContainerItems(containerItems);
    return container;
  }

  private ContainerDTO createDefaultContainerDTO() {
    ContainerDTO container = new ContainerDTO();
    container.setMessageId("a0e24c65-991b-46ac-ae65-dc7bc52edfe6");
    container.setLocation("DEC12");
    container.setDeliveryNumber(Long.parseLong("60077104"));
    container.setParentTrackingId(null);
    container.setContainerType("PALLET");
    container.setCtrShippable(Boolean.FALSE);
    container.setCtrReusable(Boolean.FALSE);
    container.setInventoryStatus("AVAILABLE");
    container.setAudited(true);
    container.setWeight((float) 20.5);
    container.setWeightUOM("LB");
    container.setCube((float) 0.5669999718666077);
    container.setCubeUOM("CF");

    Map<String, String> facility = new HashMap<>();
    facility.put("facilityCountryCode", "US");
    facility.put("facilityNum", "54321");
    container.setFacility(facility);

    List<ContainerItem> containerItems = new ArrayList<>();
    ContainerItem containerItem = new ContainerItem();
    containerItem.setPurchaseReferenceNumber("7519270066");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setInboundChannelMethod("SSTKU");
    containerItem.setTotalPurchaseReferenceQty(100);
    containerItem.setPurchaseCompanyId(1);
    containerItem.setDeptNumber(1);
    containerItem.setPoDeptNumber("92");
    containerItem.setItemNumber(Long.parseLong("553708208"));
    containerItem.setDescription("ROYAL BASMATI 20LB");
    containerItem.setGtin("00745042112013");
    containerItem.setQuantity(80);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem.setVnpkQty(4);
    containerItem.setWhpkQty(4);
    containerItem.setVendorPackCost(25.0);
    containerItem.setWhpkSell(25.0);
    containerItem.setBaseDivisionCode("WM");
    containerItem.setFinancialReportingGroupCode("US");
    containerItem.setVendorNumber(1234);
    containerItem.setPackagedAsUom(ReceivingConstants.Uom.VNPK);
    containerItem.setPromoBuyInd("Y");
    containerItem.setVnpkWgtQty((float) 20.5);
    containerItem.setVnpkcbuomcd("CF");
    containerItem.setCaseUPC("10745042112010");
    containerItem.setFacilityNum(54321);
    containerItem.setFacilityCountryCode("US");
    containerItem.setOutboundChannelMethod("STAPLESTOCK");
    containerItem.setItemUPC("00745042112013");
    containerItem.setVnpkWgtUom("LB");
    containerItem.setCaseQty(1);
    containerItem.setVnpkcbqty((float) 0.5669999718666077);
    containerItems.add(containerItem);
    containerItem.setRotateDate(new Date());
    container.setContainerItems(containerItems);
    return container;
  }

  @Test
  public void testTransformForLabelType() {
    Container container = createDefaultContainer();
    Map<String, Object> containerMiscInfo = new HashMap<String, Object>();
    containerMiscInfo.put(
        ReceivingConstants.INVENTORY_LABEL_TYPE, InventoryLabelType.R8000_DA_FULL_CASE.getType());
    containerMiscInfo.put(ReceivingConstants.OP_FULFILLMENT_METHOD, "RECEIVING");
    container.setContainerMiscInfo(containerMiscInfo);
    ContainerDTO containerDTO = this.transformer.transform(container);
    assertEquals(containerDTO.getTrackingId(), container.getTrackingId());
    assertEquals(containerDTO.getLabelType(), InventoryLabelType.R8000_DA_FULL_CASE.getType());
    assertEquals(containerDTO.getFulfillmentMethod(), "RECEIVING");
  }

  @Test
  public void testTransformList() {
    Container container = createDefaultContainer();
    List<ContainerDTO> containerDTOList = this.transformer.transformList(Arrays.asList(container));
    assertEquals(1, containerDTOList.size());
  }

  @Test
  public void testReverseTransform() {
    ContainerDTO containerdto = createDefaultContainerDTO();
    Container container = this.transformer.reverseTransform(containerdto);
    assertEquals(containerdto.getTrackingId(), container.getTrackingId());
  }

  @Test
  public void testReverseTransformList() {
    ContainerDTO containerDTO = createDefaultContainerDTO();
    List<Container> containers = this.transformer.reverseTransformList(Arrays.asList(containerDTO));
    assertEquals(1, containers.size());
  }
}
