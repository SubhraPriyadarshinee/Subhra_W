package com.walmart.move.nim.receiving.witron.mock.data;

import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class WitronContainer {
  public static Container getContainer1() {
    Container container = new Container();
    container.setDeliveryNumber(Long.valueOf("121212121"));
    container.setTrackingId("027734368100444931");
    container.setContainerType("13");
    container.setLocation("101");
    container.setContainerStatus(null);
    container.setCompleteTs(new Date());
    container.setPublishTs(new Date());

    ContainerItem containerItem = new ContainerItem();
    List<ContainerItem> containerItems = new ArrayList<>();
    containerItem.setTrackingId("027734368100444931");
    containerItem.setPurchaseReferenceNumber("6712345678");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setInboundChannelMethod("SSTKU");
    containerItem.setOutboundChannelMethod("SSTKU");
    containerItem.setTotalPurchaseReferenceQty(80);
    containerItem.setGtin("7874213228");
    containerItem.setItemNumber(Long.valueOf("554930276"));
    containerItem.setQuantity(480);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem.setVnpkQty(6);
    containerItem.setWhpkQty(6);
    containerItem.setActualTi(8);
    containerItem.setActualHi(10);
    containerItem.setLotNumber("555");
    containerItem.setVendorNumber(579284);
    containerItem.setPackagedAsUom(ReceivingConstants.Uom.VNPK);
    containerItem.setVnpkWgtQty((float) 2.0);
    containerItem.setVnpkWgtUom("LB");
    containerItems.add(containerItem);
    container.setContainerItems(containerItems);

    return container;
  }

  public static Container getContainer2() {
    Container container = new Container();
    container.setDeliveryNumber(Long.valueOf("121212121"));
    container.setTrackingId("027734368100444932");
    container.setContainerType("13");
    container.setLocation("101");
    container.setContainerStatus(null);
    container.setCompleteTs(new Date());
    container.setPublishTs(new Date());

    ContainerItem containerItem = new ContainerItem();
    List<ContainerItem> containerItems = new ArrayList<>();
    containerItem.setTrackingId("027734368100444931");
    containerItem.setPurchaseReferenceNumber("6712345678");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setInboundChannelMethod("SSTKU");
    containerItem.setOutboundChannelMethod("SSTKU");
    containerItem.setTotalPurchaseReferenceQty(80);
    containerItem.setGtin("7874213228");
    containerItem.setItemNumber(Long.valueOf("554930276"));
    containerItem.setQuantity(480);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem.setVnpkQty(6);
    containerItem.setWhpkQty(6);
    containerItem.setActualTi(8);
    containerItem.setActualHi(10);
    containerItem.setLotNumber("555");
    containerItem.setVendorNumber(579284);
    containerItem.setPackagedAsUom(ReceivingConstants.Uom.VNPK);
    containerItem.setVnpkWgtQty((float) 2.0);
    containerItem.setVnpkWgtUom("LB");
    containerItems.add(containerItem);
    container.setContainerItems(containerItems);

    return container;
  }

  public static Container getContainer3() {
    Container container = new Container();
    container.setDeliveryNumber(Long.valueOf("121212121"));
    container.setTrackingId("027734368100444933");
    container.setContainerType("13");
    container.setLocation("101");
    container.setContainerStatus("backout");
    container.setCompleteTs(new Date());
    container.setPublishTs(new Date());

    ContainerItem containerItem = new ContainerItem();
    List<ContainerItem> containerItems = new ArrayList<>();
    containerItem.setTrackingId("027734368100444931");
    containerItem.setPurchaseReferenceNumber("6712345678");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setInboundChannelMethod("SSTKU");
    containerItem.setOutboundChannelMethod("SSTKU");
    containerItem.setTotalPurchaseReferenceQty(80);
    containerItem.setGtin("7874213228");
    containerItem.setItemNumber(Long.valueOf("554930276"));
    containerItem.setQuantity(480);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem.setVnpkQty(6);
    containerItem.setWhpkQty(6);
    containerItem.setActualTi(8);
    containerItem.setActualHi(10);
    containerItem.setLotNumber("555");
    containerItem.setVendorNumber(579284);
    containerItem.setPackagedAsUom(ReceivingConstants.Uom.VNPK);
    containerItem.setVnpkWgtQty((float) 2.0);
    containerItem.setVnpkWgtUom("LB");
    containerItems.add(containerItem);
    container.setContainerItems(containerItems);

    return container;
  }

  public static ContainerItem getContainerItem(Long itemNumber) {
    ContainerItem containerItem = new ContainerItem();
    containerItem.setTrackingId("027734368100444931");
    containerItem.setPurchaseReferenceNumber("6712345678");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setInboundChannelMethod("SSTKU");
    containerItem.setOutboundChannelMethod("SSTKU");
    containerItem.setTotalPurchaseReferenceQty(80);
    containerItem.setGtin("7874213228");
    containerItem.setItemNumber(itemNumber);
    containerItem.setQuantity(480);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem.setVnpkQty(6);
    containerItem.setWhpkQty(6);
    containerItem.setActualTi(8);
    containerItem.setActualHi(10);
    containerItem.setLotNumber("555");
    containerItem.setVendorNumber(579284);
    containerItem.setPackagedAsUom(ReceivingConstants.Uom.VNPK);
    containerItem.setVnpkWgtQty((float) 2.0);
    containerItem.setVnpkWgtUom("LB");
    return containerItem;
  }
}
