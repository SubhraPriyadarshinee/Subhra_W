package com.walmart.move.nim.receiving.wfs.mock.data;

import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.CollectionUtils;

public class MockContainer {

  public static Container getWFSContainerInfo() {
    Container container = new Container();
    ContainerItem containerItem = new ContainerItem();
    List<ContainerItem> containerItems = new ArrayList<>();

    containerItem.setTrackingId("a329870000000000000000001");
    containerItem.setPurchaseReferenceNumber("1008799412");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setVnpkQty(6);
    containerItem.setWhpkQty(6);
    containerItem.setItemNumber(557959102L);
    containerItem.setQuantity(90);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem.setInboundChannelMethod("SSTKU");
    containerItem.setActualTi(5);
    containerItem.setActualHi(3);
    containerItem.setVnpkWgtQty(2.0f);
    containerItem.setVnpkWgtUom("LB");
    containerItems.add(containerItem);
    container.setDeliveryNumber(15057089L);
    container.setCreateUser("sysadmin");
    container.setTrackingId("a329870000000000000000001");
    container.setContainerStatus("");
    container.setWeight(30.0f);
    container.setWeightUOM("LB");
    container.setContainerItems(containerItems);

    return updateContainerForWFS(container);
  }

  public static Container updateContainerForWFS(Container container) {
    List<ContainerItem> containerItems = container.getContainerItems();
    if (!CollectionUtils.isEmpty(containerItems)) {
      ContainerItem containerItem = containerItems.get(0);
      containerItem.setInboundChannelMethod("WFS");
    }
    container.setContainerItems(containerItems);
    return container;
  }
}
