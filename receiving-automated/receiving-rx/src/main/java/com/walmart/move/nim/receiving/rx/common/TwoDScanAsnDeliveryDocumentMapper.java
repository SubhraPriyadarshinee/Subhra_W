package com.walmart.move.nim.receiving.rx.common;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_GDM_SHIPMENT_GET_BY_SCAN_v4_ENABLED;

import com.walmart.move.nim.receiving.core.common.AsnToDeliveryDocumentsCustomMapper;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.ShipmentDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v3.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component("twoDScanAsnDeliveryDocumentMapper")
public class TwoDScanAsnDeliveryDocumentMapper extends AsnToDeliveryDocumentsCustomMapper {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(TwoDScanAsnDeliveryDocumentMapper.class);

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Override
  public void mapPacksToDeliveryDocumentLines(
      SsccScanResponse ssccScanAsnDetails,
      List<Pack> packs,
      List<DeliveryDocument> deliveryDocuments,
      String scannedSscc,
      Map<String, ShipmentDetails> shipmentDetailsMap,
      HttpHeaders httpHeaders) {
    DeliveryDocumentLine line = null;
    List<PurchaseOrder> purchaseOrders = ssccScanAsnDetails.getPurchaseOrders();
    Map<String, Map<String, Integer>> aggregatedQtyByShipmentPoAndPoLineMap = new HashMap<>();
    if (Objects.nonNull(purchaseOrders.get(0).getVendorInformation())
        && purchaseOrders.get(0).getVendorInformation().isSerialInfoEnabled()
        && (tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            IS_GDM_SHIPMENT_GET_BY_SCAN_v4_ENABLED,
            false))
        && !ReceivingUtils.isAsnReceivingOverrideEligible(httpHeaders)) {
      for (Pack pack : packs) {
        for (PurchaseOrder purchaseOrder : purchaseOrders) {
          for (PurchaseOrderLine poLine : purchaseOrder.getLines()) {
            if ((StringUtils.isNotBlank(pack.getGtin()) && isGtinMatchingWithPOLine(pack, poLine))
                || (StringUtils.isBlank(pack.getGtin()))) {
              constructEpcisDeliveryDocumentAndLine(
                  deliveryDocuments, poLine, pack, shipmentDetailsMap, purchaseOrder);
            }
          }
        }
      }
    } else {
      for (Pack pack : packs) {
        List<Item> items = pack.getItems();
        for (Item item : items) {
          String packPoNumber = item.getPurchaseOrder().getPoNumber();
          String asnPoPoLineKey = packPoNumber + item.getPurchaseOrder().getPoLineNumber();
          Map<String, Integer> aggregatedQtyByPoAndPoLineMap = new HashMap<>();
          if (Objects.isNull(item.getAggregatedItemQty())) {
            aggregatedQtyByPoAndPoLineMap.put(
                asnPoPoLineKey, item.getInventoryDetail().getDerivedQuantity().intValue());
          } else {
            aggregatedQtyByPoAndPoLineMap.put(asnPoPoLineKey, item.getAggregatedItemQty());
          }
          aggregatedQtyByShipmentPoAndPoLineMap.put(
              pack.getShipmentNumber(), aggregatedQtyByPoAndPoLineMap);

          Optional<DeliveryDocumentLine> deliveryDocumentLineOptional =
              checkIfPoLineExistsinDoc(deliveryDocuments, item);
          if (deliveryDocumentLineOptional.isPresent()) {
            // Add item to existing delivery doc line, same PO/PoLine but
            // probably split in different lots.
            DeliveryDocumentLine deliveryDocumentLine = deliveryDocumentLineOptional.get();
            populateManufactureDetailsToLine(item, deliveryDocumentLine);
            if (StringUtils.isBlank(scannedSscc)) {
              int shippedQty = 0;
              for (Map.Entry<String, Map<String, Integer>> poPoLineEntry :
                  aggregatedQtyByShipmentPoAndPoLineMap.entrySet()) {
                Map<String, Integer> poPoLineMap = poPoLineEntry.getValue();
                String poPoLineKey =
                    item.getPurchaseOrder().getPoNumber()
                        + item.getPurchaseOrder().getPoLineNumber();
                Integer shippedQtyFromPoPoLineMap = poPoLineMap.get(poPoLineKey);
                if (Objects.nonNull(shippedQtyFromPoPoLineMap)) {
                  shippedQty += shippedQtyFromPoPoLineMap;
                }
              }
              shippedQty =
                  ReceivingUtils.conversionToEaches(
                      shippedQty,
                      ReceivingConstants.Uom.VNPK,
                      deliveryDocumentLine.getVendorPack(),
                      deliveryDocumentLine.getWarehousePack());
              deliveryDocumentLine.setShippedQty(shippedQty);
              deliveryDocumentLine.setShippedQtyUom(ReceivingConstants.Uom.EACHES);
            }
            populateShipmentDetails(
                shipmentDetailsMap,
                aggregatedQtyByShipmentPoAndPoLineMap,
                pack,
                item,
                deliveryDocumentLine);
          } else {
            PurchaseOrder purchaseOrder = readPo(purchaseOrders, item);
            PurchaseOrderLine purchaseOrderLine = readPoLine(purchaseOrder, item);
            line =
                mapDeliveryDocumentLine(purchaseOrder, item, purchaseOrderLine, shipmentDetailsMap);
            populateManufactureDetailsToLine(item, line);
            populateDocumentLine(
                deliveryDocuments, packPoNumber, line); // Map PO to Items in the pack.
            populateShipmentDetails(
                shipmentDetailsMap, aggregatedQtyByShipmentPoAndPoLineMap, pack, item, line);
          }
        }
      }
      populateSSCC(deliveryDocuments, scannedSscc);
    }
    LOGGER.info("GDM mapping completed successfully");
  }

  private void populateShipmentDetails(
      Map<String, ShipmentDetails> shipmentDetailsMap,
      Map<String, Map<String, Integer>> aggregatedQtyByShipmentPoAndPoLineMap,
      Pack pack,
      Item item,
      DeliveryDocumentLine line) {
    ShipmentDetails asnShipmentDetails = shipmentDetailsMap.get(pack.getShipmentNumber());
    ShipmentDetails shipmentDetails = new ShipmentDetails();
    Map<String, Integer> poPoLineQtyMap =
        aggregatedQtyByShipmentPoAndPoLineMap.get(pack.getShipmentNumber());
    if (ObjectUtils.allNotNull(asnShipmentDetails, poPoLineQtyMap)) {
      shipmentDetails = createShipmentDetails(asnShipmentDetails);
      String poPoLineKey =
          item.getPurchaseOrder().getPoNumber() + item.getPurchaseOrder().getPoLineNumber();
      Integer shippedQtyFromPoPoLineMap = poPoLineQtyMap.getOrDefault(poPoLineKey, 0);
      shippedQtyFromPoPoLineMap =
          ReceivingUtils.conversionToEaches(
              shippedQtyFromPoPoLineMap,
              ReceivingConstants.Uom.VNPK,
              line.getVendorPack(),
              line.getWarehousePack());
      shipmentDetails.setShippedQty(shippedQtyFromPoPoLineMap);
      shipmentDetails.setShippedQtyUom(ReceivingConstants.Uom.EACHES);
    } else if (Objects.nonNull(asnShipmentDetails) && Objects.isNull(poPoLineQtyMap)) {
      int shippedQty =
          ReceivingUtils.conversionToEaches(
              item.getInventoryDetail().getDerivedQuantity().intValue(),
              item.getInventoryDetail().getDerivedUom(),
              line.getVendorPack(),
              line.getWarehousePack());
      shipmentDetails.setShippedQty(shippedQty);
      shipmentDetails.setShippedQtyUom(ReceivingConstants.Uom.EACHES);
    }
    List<ShipmentDetails> deliveryDocumentLineShipmentDetails = line.getShipmentDetailsList();
    if (Objects.isNull(deliveryDocumentLineShipmentDetails)) {
      deliveryDocumentLineShipmentDetails = new ArrayList<>();
    }
    if (!deliveryDocumentLineShipmentDetails.contains(shipmentDetails)) {
      deliveryDocumentLineShipmentDetails.add(shipmentDetails);
    }
    line.setShipmentDetailsList(deliveryDocumentLineShipmentDetails);
  }

  private ShipmentDetails createShipmentDetails(ShipmentDetails asnShipmentDetails) {
    return ShipmentDetails.builder()
        .shipmentNumber(asnShipmentDetails.getShipmentNumber())
        .inboundShipmentDocId(asnShipmentDetails.getInboundShipmentDocId())
        .destinationGlobalLocationNumber(asnShipmentDetails.getDestinationGlobalLocationNumber())
        .loadNumber(asnShipmentDetails.getLoadNumber())
        .shipperId(asnShipmentDetails.getShipperId())
        .sourceGlobalLocationNumber(asnShipmentDetails.getSourceGlobalLocationNumber())
        .build();
  }
}
