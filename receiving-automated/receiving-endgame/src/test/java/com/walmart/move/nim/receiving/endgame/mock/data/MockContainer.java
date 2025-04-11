package com.walmart.move.nim.receiving.endgame.mock.data;

import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerTag;
import com.walmart.move.nim.receiving.endgame.model.TransferPurchaseOrderDetails;
import com.walmart.move.nim.receiving.endgame.model.TransferReceivingRequest;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MockContainer {

  public static ContainerDTO getContainerDTO() {
    ContainerDTO container = new ContainerDTO();
    container.setTrackingId("TC00000001");
    container.setMessageId("a0e24c65-991b-46ac-ae65-dc7bc52edfe6");
    container.setLocation("100");
    container.setDeliveryNumber(Long.parseLong("18278904"));
    container.setParentTrackingId(null);
    container.setContainerType("Chep Pallet");
    container.setCtrShippable(Boolean.FALSE);
    container.setCtrReusable(Boolean.FALSE);
    container.setInventoryStatus("AVAILABLE");

    Map<String, String> facility = new HashMap<>();
    facility.put("countryCode", "US");
    facility.put("buNumber", "32612");
    container.setFacility(facility);

    List<ContainerItem> containerItems = new ArrayList<>();
    ContainerItem containerItem = new ContainerItem();
    containerItem.setTrackingId("TC00000001");
    containerItem.setPurchaseReferenceNumber("199557349");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setInboundChannelMethod("SSTKU");
    containerItem.setTotalPurchaseReferenceQty(100);
    containerItem.setPurchaseCompanyId(1);
    containerItem.setDeptNumber(1);
    containerItem.setPoDeptNumber("92");
    containerItem.setItemNumber(Long.parseLong("556565795"));
    containerItem.setGtin("00049807100011");
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
    containerItem.setActualTi(5);
    containerItem.setActualHi(4);
    containerItems.add(containerItem);
    container.setContainerItems(containerItems);

    return container;
  }

  public static Container getContainer() {
    Container container = new Container();
    container.setTrackingId("TC00000001");
    container.setMessageId("a0e24c65-991b-46ac-ae65-dc7bc52edfe6");
    container.setLocation("100");
    container.setDeliveryNumber(Long.parseLong("18278904"));
    container.setParentTrackingId(null);
    container.setContainerType("Chep Pallet");
    container.setCtrShippable(Boolean.FALSE);
    container.setCtrReusable(Boolean.FALSE);
    container.setInventoryStatus("AVAILABLE");

    Map<String, String> facility = new HashMap<>();
    facility.put("countryCode", "US");
    facility.put("buNumber", "32612");
    container.setFacility(facility);

    List<ContainerItem> containerItems = new ArrayList<>();
    ContainerItem containerItem = new ContainerItem();
    containerItem.setTrackingId("TC00000001");
    containerItem.setPurchaseReferenceNumber("199557349");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setInboundChannelMethod("SSTKU");
    containerItem.setTotalPurchaseReferenceQty(100);
    containerItem.setPurchaseCompanyId(1);
    containerItem.setDeptNumber(1);
    containerItem.setPoDeptNumber("92");
    containerItem.setItemNumber(Long.parseLong("556565795"));
    containerItem.setGtin("00049807100011");
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
    containerItem.setActualTi(5);
    containerItem.setActualHi(4);
    containerItems.add(containerItem);
    container.setContainerItems(containerItems);

    return container;
  }

  public static TransferReceivingRequest getContainerTransferRequest() {
    ContainerTag tag = new ContainerTag();
    tag.setTag("HOLD_FOR_SALE");
    tag.setAction("SET");

    TransferPurchaseOrderDetails poDetails = new TransferPurchaseOrderDetails();
    poDetails.setPurchaseReferenceNumber("1708069842");
    poDetails.setPurchaseReferenceLineNumber(1);
    poDetails.setGtin("00841342108678");
    poDetails.setItemNumber(587272806L);
    poDetails.setQuantity(3);

    TransferReceivingRequest receivingRequest = new TransferReceivingRequest();
    receivingRequest.setTrackingId("c040930000100000000376184");
    receivingRequest.setLocation("DECANT");
    receivingRequest.setTags(Collections.singletonList(tag));
    receivingRequest.setContents(Collections.singletonList(poDetails));
    return receivingRequest;
  }
}
