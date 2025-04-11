package com.walmart.move.nim.receiving.sib.mapper;

import static com.walmart.move.nim.receiving.core.utils.UomUtils.getScaledQuantity;
import static com.walmart.move.nim.receiving.sib.utils.Constants.BANNER_CODE;
import static com.walmart.move.nim.receiving.sib.utils.Constants.BANNER_DESCRIPTION;
import static com.walmart.move.nim.receiving.sib.utils.Constants.DEST_TRACKING_ID;
import static com.walmart.move.nim.receiving.sib.utils.Constants.DUMMY_PURCHASE_REF_NUMBER;
import static com.walmart.move.nim.receiving.sib.utils.Constants.PACK_NUMBER;
import static com.walmart.move.nim.receiving.sib.utils.Constants.PALLET_TYPE;
import static com.walmart.move.nim.receiving.sib.utils.Constants.TIMEZONE_CODE;
import static com.walmart.move.nim.receiving.sib.utils.Util.getPackType;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.AVAILABLE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.BASE_DIVISION_CODE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.CASE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.EA;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.EVENT_TYPE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.FINANCIAL_REPORTING_GROUP_CODE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.RECEIVED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.STAPLESTOCK;

import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Item;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Pack;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Shipment;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.util.Pair;
import org.springframework.util.CollectionUtils;

public class ContainerMapper {

  public static Container populateContainer(
      Long deliveryNumber,
      Shipment shipment,
      Pack pack,
      String defaultTrackingId,
      String defaultDestTrackingId) {
    Container container = new Container();
    container.setTrackingId(defaultTrackingId);
    container.setSsccNumber(pack.getPackNumber());
    container.setDeliveryNumber(deliveryNumber);
    container.setContainerStatus(RECEIVED);
    container.setInventoryStatus(AVAILABLE);
    container.setContainerType(CASE);
    container.setLocation(getPackType(pack));

    // Defaulting it as it is not relevant in Store/MFC
    container.setWeight(0.0f);
    container.setWeightUOM("LB");
    container.setCube(0.0f);
    container.setCubeUOM("CF");

    container.setCtrShippable(false);
    container.setCtrReusable(false);
    String user = ReceivingConstants.DEFAULT_USER;
    if (Objects.nonNull(TenantContext.getAdditionalParams())
        && Objects.nonNull(
            TenantContext.getAdditionalParams().get(ReceivingConstants.USER_ID_HEADER_KEY))) {
      user =
          String.valueOf(
              TenantContext.getAdditionalParams().get(ReceivingConstants.USER_ID_HEADER_KEY));
    }
    container.setCreateUser(user);
    container.setLastChangedUser(user);

    Date date = new Date();
    container.setCreateTs(date);
    container.setCompleteTs(date);
    container.setPublishTs(date);
    container.setMessageId(UUID.randomUUID().toString());

    container.setShipmentId(shipment.getDocumentId());

    // Timezone & Market type info
    Map<String, Object> containerMiscInfo = new HashMap<>();
    if (Objects.nonNull(shipment.getAdditionalInfo())) {
      containerMiscInfo.put(BANNER_CODE, shipment.getAdditionalInfo().getBannerCode());
      containerMiscInfo.put(
          BANNER_DESCRIPTION, shipment.getAdditionalInfo().getBannerDescription());
      containerMiscInfo.put(TIMEZONE_CODE, shipment.getAdditionalInfo().getTimeZoneCode());
    }
    containerMiscInfo.put(DEST_TRACKING_ID, defaultDestTrackingId);
    containerMiscInfo.put(PALLET_TYPE, getPackType(pack));
    container.setContainerMiscInfo(containerMiscInfo);

    return container;
  }

  public static ContainerItem populatePackItem(
      Pack pack, Item item, String targetUom, List<String> scalableUomList) {
    ContainerItem containerItem = new ContainerItem();
    containerItem.setItemNumber(item.getItemNumber());
    if (Objects.isNull(targetUom) || CollectionUtils.isEmpty(scalableUomList)) {
      containerItem.setQuantityUOM(EA);
      containerItem.setQuantity((int) Math.ceil(item.getInventoryDetail().getReportedQuantity()));
      containerItem.setOrderFilledQty(0L);
      containerItem.setOrderFilledQtyUom(EA);
    } else {
      Pair<Integer, String> scaledQuantity =
          getScaledQuantity(
              item.getInventoryDetail().getReportedQuantity(),
              item.getInventoryDetail().getReportedUom(),
              targetUom,
              scalableUomList);
      containerItem.setQuantityUOM(scaledQuantity.getSecond());
      containerItem.setQuantity(scaledQuantity.getFirst());
      containerItem.setOrderFilledQty(0L);
      containerItem.setOrderFilledQtyUom(scaledQuantity.getSecond());
    }
    containerItem.setGtin(item.getGtin());
    containerItem.setItemUPC(item.getGtin());
    containerItem.setInboundChannelMethod(STAPLESTOCK);
    containerItem.setOutboundChannelMethod(STAPLESTOCK);
    containerItem.setTrackingId(pack.getPackNumber());
    containerItem.setDeptNumber(Integer.valueOf(item.getItemDepartment()));

    if (Objects.isNull(item.getVendorId())) {
      populateDefaultVendor(containerItem);
    } else {
      populateVendor(containerItem, Integer.valueOf(item.getVendorId()));
    }

    populateDefaultLineWeight(containerItem);
    populateDefaultLineCube(containerItem);

    containerItem.setBaseDivisionCode(BASE_DIVISION_CODE);
    containerItem.setFinancialReportingGroupCode(FINANCIAL_REPORTING_GROUP_CODE);
    containerItem.setVnpkQty(item.getInventoryDetail().getVendorCaseQuantity());
    containerItem.setWhpkQty(item.getInventoryDetail().getWarehouseCaseQuantity());
    containerItem.setWhpkSell(item.getFinancialDetail().getDerivedCost());
    containerItem.setDescription(item.getItemDescription());
    containerItem.setTotalPurchaseReferenceQty(
        item.getInventoryDetail().getReportedQuantity().intValue());

    containerItem.setInvoiceNumber(item.getInvoice().getInvoiceNumber());
    containerItem.setInvoiceLineNumber(item.getInvoice().getInvoiceLineNumber());

    // setting purchase order for sct
    containerItem.setPurchaseReferenceNumber(
        Objects.nonNull(item.getPurchaseOrder())
                && Objects.nonNull(item.getPurchaseOrder().getPoNumber())
            ? item.getPurchaseOrder().getPoNumber()
            : DUMMY_PURCHASE_REF_NUMBER);
    containerItem.setPurchaseReferenceLineNumber(item.getInvoice().getInvoiceLineNumber());
    containerItem.setCid(item.getReplenishmentGroupNumber());
    Map<String, String> containerItemMisc = new HashMap<>();
    containerItemMisc.put(PACK_NUMBER, pack.getPackNumber());
    containerItemMisc.put(
        EVENT_TYPE,
        (Objects.nonNull(item.getAdditionalInfo())
                && Objects.nonNull(item.getAdditionalInfo().getEventType())
            ? item.getAdditionalInfo().getEventType()
            : null));
    containerItem.setContainerItemMiscInfo(containerItemMisc);
    return containerItem;
  }

  public static void populateDefaultLineWeight(ContainerItem containerItem) {
    containerItem.setVnpkWgtQty(0.0f);
    containerItem.setVnpkWgtUom("LB");
  }

  public static void populateDefaultLineCube(ContainerItem containerItem) {
    containerItem.setVnpkcbqty(0.0f);
    containerItem.setVnpkcbuomcd("CF");
  }

  public static void populateDefaultVendor(ContainerItem containerItem) {
    containerItem.setVendorNumber(0);
  }

  public static void populateVendor(ContainerItem containerItem, Integer vendor) {

    int vendorNumber = Objects.isNull(vendor) ? 0 : vendor;
    containerItem.setVendorNumber(vendorNumber);
    // PODept is not required
    //    containerItem.setPoDeptNumber(String.valueOf(vendor.getDepartment()));
  }
}
