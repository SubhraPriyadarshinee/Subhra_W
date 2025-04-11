package com.walmart.move.nim.receiving.endgame.mock.data;

import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.endgame.model.ExtraAttributes;
import com.walmart.move.nim.receiving.endgame.model.MultiplePalletReceivingRequest;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;

public class MockPalletReceiveContainer {

  public static MultiplePalletReceivingRequest createMultiplePalletReceiveRequest() {
    List<ContainerDTO> containers = new ArrayList<>();
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

    ExtraAttributes extraArributes = new ExtraAttributes();
    extraArributes.setLegacyType("PO");
    extraArributes.setDeliveryStatus(DeliveryStatus.OPEN);
    extraArributes.setIsOverboxingPallet(Boolean.FALSE);

    containers.add(container);
    return MultiplePalletReceivingRequest.builder()
        .containers(containers)
        .deliveryStatus(DeliveryStatus.OPEN)
        .isOverboxingPallet(Boolean.FALSE)
        .extraAttributes(extraArributes)
        .build();
  }

  public static MultiplePalletReceivingRequest createMultiplePalletReceiveRequestForTplScan() {
    List<ContainerDTO> containers = new ArrayList<>();
    containers.add(MockContainer.getContainerDTO());

    ExtraAttributes extraArributes = new ExtraAttributes();
    extraArributes.setLegacyType("PO");

    return MultiplePalletReceivingRequest.builder()
        .containers(containers)
        .extraAttributes(extraArributes)
        .deliveryStatus(DeliveryStatus.OPEN)
        .isOverboxingPallet(Boolean.FALSE)
        .build();
  }
}
