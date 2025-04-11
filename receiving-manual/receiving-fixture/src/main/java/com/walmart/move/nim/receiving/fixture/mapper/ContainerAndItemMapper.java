package com.walmart.move.nim.receiving.fixture.mapper;

import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Destination;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Item;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Pack;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Shipment;
import com.walmart.move.nim.receiving.fixture.common.FixtureConstants;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ContainerType;
import com.walmart.move.nim.receiving.utils.constants.InventoryStatus;
import com.walmart.move.nim.receiving.utils.constants.PurchaseReferenceType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;

public class ContainerAndItemMapper {
  public static void setContainerAttributes(
      Container container,
      String defaultUser,
      long deliveryNumber,
      String packNumber,
      Destination destination) {
    container.setTrackingId(packNumber);
    container.setMessageId(packNumber);
    container.setParentTrackingId(packNumber);
    container.setCreateUser(defaultUser);
    container.setContainerStatus(ReceivingConstants.STATUS_PENDING_COMPLETE);
    container.setDeliveryNumber(deliveryNumber);

    Map<String, String> lpnMap = new HashMap<>();
    lpnMap.put(ReceivingConstants.COUNTRY_CODE, destination.getCountryCode());
    lpnMap.put(ReceivingConstants.BU_NUMBER, destination.getNumber());
    container.setDestination(lpnMap);

    Map<String, String> facility = new HashMap<>();
    facility.put(ReceivingConstants.BU_NUMBER, TenantContext.getFacilityNum().toString());
    facility.put(ReceivingConstants.COUNTRY_CODE, TenantContext.getFacilityCountryCode());
    container.setFacility(facility);

    container.setCtrReusable(false);
    container.setCtrShippable(true);
    container.setOnConveyor(false);
    container.setIsConveyable(false);
    container.setInventoryStatus(InventoryStatus.AVAILABLE.name());
    container.setContainerType(ContainerType.PALLET.getText());
  }

  public static ContainerItem setContainerItemAttributes(
      Item item, String palletId, ContainerItem containerItem) {
    containerItem.setTrackingId(palletId);
    containerItem.setItemNumber(item.getItemNumber());
    containerItem.setDescription(item.getItemDescription());
    containerItem.setPurchaseReferenceNumber(item.getPurchaseOrder().getPoNumber());
    containerItem.setPurchaseReferenceLineNumber(
        Integer.valueOf(item.getPurchaseOrder().getPoLineNumber()));
    containerItem.setQuantityUOM(item.getInventoryDetail().getReportedUom());
    containerItem.setQuantity(item.getInventoryDetail().getReportedQuantity().intValue());
    containerItem.setOrderableQuantity(item.getInventoryDetail().getReportedQuantity().intValue());
    setMandatoryFieldForCreationOfInventoryContainer(containerItem);
    return containerItem;
  }

  private static void setMandatoryFieldForCreationOfInventoryContainer(
      ContainerItem containerItem) {
    containerItem.setVnpkQty(FixtureConstants.DEFAULT_VNPK_QTY);
    containerItem.setWhpkQty(FixtureConstants.DEFAULT_WHPK_QTY);
    containerItem.setBaseDivisionCode(ReceivingConstants.BASE_DIVISION_CODE);
    containerItem.setFinancialReportingGroupCode(TenantContext.getFacilityCountryCode());
    // TODO: Revisit the logic for deciding on channel methods
    containerItem.setInboundChannelMethod(PurchaseReferenceType.SSTKU.name());
    containerItem.setOutboundChannelMethod(PurchaseReferenceType.SSTKU.name());
  }

  public static Container createContainerFromPacks(Shipment shipment, Pack pack) {

    Container container = new Container();
    ContainerAndItemMapper.setContainerAttributes(
        container,
        ReceivingConstants.DEFAULT_USER,
        shipment.getShipmentNumber().hashCode(),
        pack.getPackNumber(),
        pack.getHandledOnBehalfOf());
    List<ContainerItem> containerItemList = new ArrayList<>();
    pack.getItems()
        .forEach(
            item ->
                containerItemList.add(
                    ContainerAndItemMapper.setContainerItemAttributes(
                        item, pack.getPackNumber(), new ContainerItem())));
    container.setContainerItems(containerItemList);
    return container;
  }
}
