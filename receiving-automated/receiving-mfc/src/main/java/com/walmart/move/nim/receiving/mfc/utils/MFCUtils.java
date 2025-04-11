package com.walmart.move.nim.receiving.mfc.utils;

import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.*;
import static com.walmart.move.nim.receiving.mfc.common.PalletType.MFC;
import static com.walmart.move.nim.receiving.mfc.common.PalletType.STORE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.EMPTY_STRING;
import static io.strati.libs.commons.lang.StringUtils.equalsIgnoreCase;

import com.walmart.move.nim.receiving.core.common.OverageType;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.SourceType;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ASNDocument;
import com.walmart.move.nim.receiving.core.model.gdm.v3.InvoiceDetail;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Item;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Pack;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Shipment;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.model.ngr.NGRPack;
import com.walmart.move.nim.receiving.core.model.ngr.PackItem;
import com.walmart.move.nim.receiving.mfc.common.OperationType;
import com.walmart.move.nim.receiving.mfc.common.PalletType;
import com.walmart.move.nim.receiving.mfc.model.common.PalletInfo;
import com.walmart.move.nim.receiving.mfc.model.common.Quantity;
import com.walmart.move.nim.receiving.mfc.model.common.QuantityType;
import com.walmart.move.nim.receiving.utils.constants.Eligibility;
import com.walmart.move.nim.receiving.utils.constants.OSDRCode;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;

/**
 * Utils to be used for MFC. Must contain only static methods. This class cannot have state
 * variables except logger or for performance instrumentation cases.
 */
public class MFCUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(MFCUtils.class);

  private MFCUtils() {
    throw new ReceivingInternalException(
        ExceptionCodes.RECEIVING_INTERNAL_ERROR, "Cannot be instantiated.");
  }

  /**
   * * Method to validate if proceed for pre-container generation or not
   *
   * @param eventType
   * @return Boolean
   */
  public static boolean isValidPreLabelEvent(String eventType) {
    if (ObjectUtils.isEmpty(eventType)) return false;
    return ReceivingConstants.EVENT_DELIVERY_SHIPMENT_ADDED.equalsIgnoreCase(eventType);
  }

  public static Container replaceSSCCWithTrackingId(Container container) {
    container.setTrackingId(container.getSsccNumber());
    return container;
  }

  public static ContainerDTO replaceSSCCWithTrackingId(ContainerDTO containerDTO) {
    containerDTO.setTrackingId(containerDTO.getSsccNumber());
    return containerDTO;
  }

  private static boolean isValidQuantity(Integer quantity) {
    return Objects.nonNull(quantity) && !(quantity.intValue() == 0);
  }

  public static String getPackId(Pack pack) {
    return Objects.isNull(pack.getPalletNumber()) ? pack.getPackNumber() : pack.getPalletNumber();
  }

  public static Map<String, String> getPalletTypeMap(List<Pack> packs) {
    Map<String, String> palletTypeMap = new HashMap<>();
    if (CollectionUtils.isNotEmpty(packs)) {
      Map<String, List<Pack>> packMap =
          packs.stream().collect(Collectors.groupingBy(MFCUtils::getPackId));
      packMap.forEach(
          (pallet, _packs) -> {
            palletTypeMap.put(pallet, getPalletType(_packs));
          });
    }
    return palletTypeMap;
  }

  public static String getPalletType(List<Pack> packs) {
    if (CollectionUtils.isNotEmpty(packs)) {
      for (Pack pack : packs) {
        if (CollectionUtils.isNotEmpty(pack.getItems())) {
          for (Item item : pack.getItems()) {
            if (!equalsIgnoreCase(MARKET_FULFILLMENT_CENTER, item.getReplenishmentCode())) {
              return STORE.toString();
            }
          }
        }
      }
      return MFC.toString();
    }
    return STORE.toString();
  }

  public static String getOperationType(ASNDocument asnDocument) {
    return Objects.nonNull(asnDocument) && asnDocument.isOverage()
        ? OperationType.OVERAGE.toString()
        : OperationType.NORMAL.toString();
  }

  public static void populateContainerItemOrderFilledQty(
      ContainerItem containerItem, QuantityType quantityType, long qty, String qtyUom) {
    containerItem.setOrderFilledQty(containerItem.getOrderFilledQty() + qty);
    containerItem.setOrderFilledQtyUom(qtyUom);
    Map<String, String> miscInfo = containerItem.getContainerItemMiscInfo();
    if (Objects.isNull(miscInfo)) {
      miscInfo = new HashMap<>();
      containerItem.setContainerItemMiscInfo(miscInfo);
    }
    String presentQty = miscInfo.get(quantityType.toString());
    long updatedQty;
    if (Objects.isNull(presentQty)) updatedQty = qty;
    else {
      updatedQty = Long.parseLong(presentQty) + qty;
    }
    miscInfo.put(quantityType.toString(), String.valueOf(updatedQty));
  }

  public static String getOverageType(OverageType overageType) {
    return Objects.nonNull(overageType)
        ? overageType.getName()
        : OverageType.defaultType().getName();
  }

  public static Receipt createContainerReceipt(
      Container container, ContainerItem containerItem, Quantity quantity, int qty, String qtyUom) {
    Receipt receipt = new Receipt();
    receipt.setDeliveryNumber(container.getDeliveryNumber());
    receipt.setInvoiceNumber(containerItem.getInvoiceNumber());
    receipt.setInvoiceLineNumber(containerItem.getInvoiceLineNumber());
    receipt.setInboundShipmentDocId(container.getShipmentId());

    // Dummy values to satisfy Receipt entity constraints
    receipt.setPurchaseReferenceNumber(
        Objects.nonNull(containerItem.getPurchaseReferenceNumber())
            ? containerItem.getPurchaseReferenceNumber()
            : DUMMY_PURCHASE_REF_NUMBER);
    receipt.setPurchaseReferenceLineNumber(containerItem.getPurchaseReferenceLineNumber());

    receipt.setVnpkQty(containerItem.getVnpkQty());
    receipt.setWhpkQty(containerItem.getWhpkQty());
    receipt.setCreateUserId(ReceivingUtils.retrieveUserId());
    receipt.setCreateTs(new Date());

    setQuantity(receipt, quantity.getType(), qty, qtyUom);
    return receipt;
  }

  public static void setQuantity(
      Receipt receipt, QuantityType quantityType, int eachQty, String qtyUom) {
    if (eachQty <= 0) {
      return;
    }
    switch (quantityType) {
      case DECANTED:
      case RECEIVED:
        receipt.setQuantity(returnZeroIfNull(receipt.getQuantity()) + eachQty);
        receipt.setEachQty(returnZeroIfNull(receipt.getEachQty()) + eachQty);
        receipt.setQuantityUom(qtyUom);
        break;
      case OVERAGE:
        receipt.setFbOverQty(returnZeroIfNull(receipt.getFbOverQty()) + eachQty);
        receipt.setFbOverQtyUOM(qtyUom);
        receipt.setFbOverReasonCode(OSDRCode.valueOf(quantityType.getReasonCode()));
        break;
      case SHORTAGE:
        receipt.setFbShortQty(returnZeroIfNull(receipt.getFbShortQty()) + eachQty);
        receipt.setFbShortQtyUOM(qtyUom);
        receipt.setFbShortReasonCode(OSDRCode.valueOf(quantityType.getReasonCode()));
        break;
      case DAMAGE:
        receipt.setFbDamagedQty(returnZeroIfNull(receipt.getFbDamagedQty()) + eachQty);
        receipt.setFbDamagedQtyUOM(qtyUom);
        receipt.setFbDamagedReasonCode(OSDRCode.valueOf(quantityType.getReasonCode()));
        break;
      case REJECTED:
      case COLD_CHAIN_REJECT:
      case NOTMFCASSORTMENT:
      case MFCOVERSIZE:
      case FRESHNESSEXPIRATION:
      case MFC_TO_STORE_TRANSFER:
      case WRONG_TEMP_ZONE:
      case NGR_REJECT:
        receipt.setFbRejectedQty(returnZeroIfNull(receipt.getFbRejectedQty()) + eachQty);
        receipt.setFbRejectedQtyUOM(qtyUom);
        receipt.setFbRejectedReasonCode(OSDRCode.valueOf(quantityType.getReasonCode()));
        break;
      case NGR_SHORTAGE:
        receipt.setFbShortQty(returnZeroIfNull(receipt.getFbShortQty()) + eachQty);
        receipt.setFbShortQtyUOM(qtyUom);
        receipt.setFbConcealedShortageQty(
            returnZeroIfNull(receipt.getFbConcealedShortageQty()) + eachQty);
        receipt.setFbShortReasonCode(OSDRCode.valueOf(quantityType.getReasonCode()));
        receipt.setFbConcealedShortageReasonCode(OSDRCode.valueOf(quantityType.getReasonCode()));
        break;
    }
  }

  public static Integer returnZeroIfNull(Integer qty) {
    return Objects.nonNull(qty) ? qty : 0;
  }

  public static boolean isStorePallet(Container container) {
    return Objects.nonNull(container.getContainerMiscInfo())
        && PalletType.STORE.equalsType(
            container.getContainerMiscInfo().getOrDefault(PALLET_TYPE, EMPTY_STRING).toString());
  }

  public static String getDateAsString(String dateformat) {
    Date date = Calendar.getInstance().getTime();
    SimpleDateFormat sdf = new SimpleDateFormat(dateformat);
    return sdf.format(date);
  }

  public static void populatePackNumber(Container container, ContainerItem containerItem) {
    if (Objects.nonNull(container) && CollectionUtils.isNotEmpty(container.getContainerItems())) {
      Optional<ContainerItem> _containerItem =
          container
              .getContainerItems()
              .stream()
              .filter(
                  _ci ->
                      Objects.nonNull(_ci.getContainerItemMiscInfo())
                          && _ci.getContainerItemMiscInfo().containsKey(PACK_NUMBER))
              .findFirst();
      if (_containerItem.isPresent()) {
        Map<String, String> itemMiscInfo =
            Objects.nonNull(containerItem.getContainerItemMiscInfo())
                ? containerItem.getContainerItemMiscInfo()
                : new HashMap<>();
        itemMiscInfo.put(
            PACK_NUMBER, _containerItem.get().getContainerItemMiscInfo().get(PACK_NUMBER));
        containerItem.setContainerItemMiscInfo(itemMiscInfo);
      }
    }
  }

  public static ASNDocument createASNDeepClone(ASNDocument originalASNDocument) {
    ASNDocument asnDocument = new ASNDocument();
    asnDocument.setItems(originalASNDocument.getItems());
    asnDocument.setShipment(originalASNDocument.getShipment());
    asnDocument.setOverage(originalASNDocument.isOverage());

    asnDocument.setDelivery(originalASNDocument.getDelivery());
    asnDocument.setShipments(originalASNDocument.getShipments());
    asnDocument.setPurchaseOrders(originalASNDocument.getPurchaseOrders());
    asnDocument.setPacks(originalASNDocument.getPacks());
    return asnDocument;
  }

  public static ASNDocument removeLoosePacks(ASNDocument asnDocument) {
    return removeLoosePacks(Arrays.asList(asnDocument)).stream().findFirst().get();
  }

  public static List<ASNDocument> removeLoosePacks(List<ASNDocument> asnDocumentList) {
    List<ASNDocument> asnDocuments = new ArrayList<>();
    for (ASNDocument asnDocument : asnDocumentList) {
      List<Pack> packs = new ArrayList<>();
      for (Pack pack : asnDocument.getPacks()) {
        if (Objects.isNull(pack.getPalletNumber())) {
          LOGGER.info("Ignoring pack {} as no pallet number present", pack.getPackNumber());
          continue;
        }
        packs.add(pack);
      }
      asnDocument.setPacks(packs);
      asnDocuments.add(asnDocument);
    }
    return asnDocuments;
  }

  public static void removeMFCPallets(
      ASNDocument asnDocument, Map<String, String> palletAndPalletTypeMap) {

    List<Pack> packs =
        asnDocument
            .getPacks()
            .stream()
            .filter(
                pack ->
                    !palletAndPalletTypeMap
                        .getOrDefault(pack.getPalletNumber(), "NONE")
                        .equalsIgnoreCase(MFC.toString()))
            .collect(Collectors.toList());

    asnDocument.setPacks(packs);
  }

  public static void removeReceivedPallets(ASNDocument asnDocument, Set<String> palletIds) {
    List<Pack> packs =
        asnDocument
            .getPacks()
            .stream()
            .filter(pack -> !palletIds.contains(pack.getPalletNumber()))
            .collect(Collectors.toList());
    asnDocument.setPacks(packs);
  }

  public static Integer generateInvoiceLine(Container container) {
    Integer maxLineNo =
        container
                .getContainerItems()
                .stream()
                .mapToInt(ci -> ci.getInvoiceLineNumber())
                .max()
                .getAsInt()
            + 1;
    int hash = container.getTrackingId().hashCode();
    Integer offset = Math.abs(hash % 10000);
    return maxLineNo + offset;
  }

  public static Eligibility getEligibilityFromPalletInfoMap(
      Map<String, PalletInfo> palletInfoMap, String palletNumber) {
    if (MapUtils.isNotEmpty(palletInfoMap)
        && palletInfoMap.containsKey(palletNumber)
        && Objects.nonNull(palletInfoMap.get(palletNumber))) {
      return palletInfoMap.get(palletNumber).getEligibility();
    }
    return null;
  }

  public static Map<String, PalletInfo> getPalletInfoMap(List<Pack> packs) {
    Map<String, PalletInfo> palletInfoMap = new HashMap<>();
    if (CollectionUtils.isNotEmpty(packs)) {
      Map<String, List<Pack>> packMap =
          packs.stream().collect(Collectors.groupingBy(MFCUtils::getPackId));
      packMap.forEach(
          (palletNumber, _packs) -> palletInfoMap.put(palletNumber, getPalletInfo(_packs)));
    }
    return palletInfoMap;
  }

  private static PalletInfo getPalletInfo(List<Pack> packs) {
    return PalletInfo.builder()
        .palletType(getPalletType(packs))
        .eligibility(getPalletEligibility(packs))
        .build();
  }

  // MFC-Manual MFC , AMFC - Auto MFC , HMFC - Mixed(Hybrid) MFC
  private static Eligibility getPalletEligibility(List<Pack> packs) {
    if (CollectionUtils.isNotEmpty(packs)) {
      Set<String> hybridStorageFlagSet =
          packs
              .stream()
              .filter(pack -> Objects.nonNull(pack) && CollectionUtils.isNotEmpty(pack.getItems()))
              .flatMap(pack -> pack.getItems().stream())
              .map(Item::getHybridStorageFlag)
              .filter(Objects::nonNull)
              .collect(Collectors.toSet());
      if (hybridStorageFlagSet.isEmpty()) {
        return null;
      }
      if (hybridStorageFlagSet.size() == 1) {
        if (hybridStorageFlagSet.contains(Eligibility.MFC.toString())) {
          return Eligibility.MFC;
        } else if (hybridStorageFlagSet.contains(Eligibility.AMFC.toString())) {
          return Eligibility.AMFC;
        }
      } else {
        return Eligibility.HMFC;
      }
    }
    return null;
  }

  public static String getPalletTypeFromPalletInfoMap(
      Map<String, PalletInfo> palletInfoMap, String palletNumber) {
    if (MapUtils.isNotEmpty(palletInfoMap)
        && palletInfoMap.containsKey(palletNumber)
        && Objects.nonNull(palletInfoMap.get(palletNumber))) {
      return palletInfoMap.get(palletNumber).getPalletType();
    }
    return null;
  }

  public static boolean isStorePalletPublishingDisabled(
      ContainerDTO containerDTO, TenantSpecificConfigReader tenantSpecificConfigReader) {
    if (!tenantSpecificConfigReader.isFeatureFlagEnabled(ENABLE_STORE_PALLET_PUBLISH)
        && Objects.nonNull(containerDTO.getContainerMiscInfo())
        && PalletType.STORE.equalsType(
            containerDTO.getContainerMiscInfo().getOrDefault(PALLET_TYPE, "").toString())) {
      LOGGER.info(
          "StoreInbound : Store pallets with ssccNumber {} is not published to inventory",
          containerDTO.getSsccNumber());
      return true;
    }
    return false;
  }

  public static boolean isDSDShipment(Shipment shipment) {
    return Objects.nonNull(shipment)
        && Objects.nonNull(shipment.getSource())
        && SourceType.VENDOR.equals(
            EnumUtils.getEnumIgnoreCase(SourceType.class, shipment.getSource().getType()));
  }

  public static boolean isAuditReportedShortage(Receipt receipt) {
    return Objects.nonNull(receipt.getFbConcealedShortageReasonCode())
        || (Objects.nonNull(receipt.getFbRejectedReasonCode())
            && QuantityType.NGR_REJECT
                .getReasonCode()
                .equalsIgnoreCase(receipt.getFbRejectedReasonCode().name()));
  }

  public static List<com.walmart.move.nim.receiving.mfc.model.gdm.newinvoice.Pack> getPacks(
      List<Pack> asnPacks, Map<String, String> replenishmentCodeMismatching) {

    List<com.walmart.move.nim.receiving.mfc.model.gdm.newinvoice.Pack> packs = new ArrayList<>();

    if (CollectionUtils.isNotEmpty(asnPacks)) {
      asnPacks.forEach(
          asnPack -> {
            com.walmart.move.nim.receiving.mfc.model.gdm.newinvoice.Pack pack =
                com.walmart.move.nim.receiving.mfc.model.gdm.newinvoice.Pack.builder()
                    .palletNumber(asnPack.getPalletNumber())
                    .packNumber(asnPack.getPackNumber())
                    .build();
            if (CollectionUtils.isNotEmpty(asnPack.getItems())) {
              pack.setItems(getItems(asnPack.getItems(), replenishmentCodeMismatching));
            }
            packs.add(pack);
          });
    }

    return packs;
  }

  private static List<com.walmart.move.nim.receiving.mfc.model.gdm.newinvoice.Item> getItems(
      List<Item> items, Map<String, String> replenishmentCodeMismatching) {
    List<com.walmart.move.nim.receiving.mfc.model.gdm.newinvoice.Item> invoiceItems =
        new ArrayList<>();
    items.forEach(
        item -> {
          String replenishmentCode =
              replenishmentCodeMismatching.get(String.valueOf(item.getItemNumber()));
          if (Objects.nonNull(replenishmentCode)) {
            com.walmart.move.nim.receiving.mfc.model.gdm.newinvoice.Item itemDetail =
                com.walmart.move.nim.receiving.mfc.model.gdm.newinvoice.Item.builder()
                    .replenishmentCode(replenishmentCode)
                    .gtin(item.getGtin())
                    .itemNumber(item.getItemNumber())
                    .invoice(prepareInvoiceDetails(item.getInvoice()))
                    .build();
            invoiceItems.add(itemDetail);
          }
        });

    return invoiceItems;
  }

  public static InvoiceDetail prepareInvoiceDetails(InvoiceDetail invoiceDetail) {
    return InvoiceDetail.builder()
        .invoiceNumber(invoiceDetail.getInvoiceNumber())
        .invoiceLineNumber(invoiceDetail.getInvoiceLineNumber())
        .build();
  }

  public static Map<String, String> getReplenishmentCodeIfMismatching(
      ASNDocument asnDocument, NGRPack finalizedPack) {

    Map<String, List<Pack>> packsUponShipmentNo =
        asnDocument.getPacks().stream().collect(Collectors.groupingBy(Pack::getPackNumber));

    if (org.springframework.util.CollectionUtils.isEmpty(packsUponShipmentNo))
      return Collections.emptyMap();

    List<Pack> packs = packsUponShipmentNo.get(finalizedPack.getPackNumber());
    Map<Long, String> asnItemReplenCodeMap = new HashMap<>();
    packs.forEach(
        pack ->
            asnItemReplenCodeMap.putAll(
                pack.getItems()
                    .stream()
                    .collect(
                        Collectors.toMap(
                            Item::getItemNumber,
                            item ->
                                StringUtils.defaultIfEmpty(
                                    item.getReplenishmentCode(), StringUtils.EMPTY)))));
    Map<String, String> ngrItemReplenCodeMap =
        finalizedPack
            .getItems()
            .stream()
            .collect(Collectors.toMap(PackItem::getItemNumber, PackItem::getReplenishmentCode));
    Map<String, String> itemReplenCodeMap = new HashMap<>();
    ngrItemReplenCodeMap.forEach(
        (itemNo, replenCode) -> {
          if (Objects.nonNull(asnItemReplenCodeMap.get(Long.parseLong(itemNo)))
              && !asnItemReplenCodeMap.get(Long.parseLong(itemNo)).equalsIgnoreCase(replenCode)) {
            itemReplenCodeMap.putIfAbsent(itemNo, replenCode);
          }
        });
    return itemReplenCodeMap;
  }
}
