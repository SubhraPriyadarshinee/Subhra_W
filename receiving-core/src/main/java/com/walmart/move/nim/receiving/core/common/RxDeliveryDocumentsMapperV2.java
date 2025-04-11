package com.walmart.move.nim.receiving.core.common;

import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.ShipmentDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v3.*;
import java.util.*;
import javax.annotation.Resource;

import com.walmart.move.nim.receiving.core.model.gdm.v3.pack.GdmGtinHierarchy;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RxDeliveryDocumentsMapperV2 extends AsnToDeliveryDocumentsCustomMapper {

  @Resource private TenantSpecificConfigReader tenantSpecificConfigReader;

  /**
   * @param currentNodeResponse GDM CurrentNode API response
   * @return deliveryDocuments
   * @throws ReceivingException receiving exception
   */
  public List<DeliveryDocument> mapGdmResponse(SsccScanResponse currentNodeResponse)
      throws ReceivingException {
    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();

    // shipments (calls parent)
    List<Shipment> shipments = currentNodeResponse.getShipments();
    Map<String, ShipmentDetails> shipmentDetailsMap = populateShipments(shipments);

    // purchaseOrders (calls parent)
    constructDeliveryDocumentsFromAsnData(currentNodeResponse, deliveryDocuments);

    // container
    mapContainerToDeliveryDocumentLines(currentNodeResponse, deliveryDocuments, shipmentDetailsMap);

    // gdmContainers
    DeliveryDocument.GdmCurrentNodeDetail gdmCurrentNodeDetail =
        new DeliveryDocument.GdmCurrentNodeDetail(
            currentNodeResponse.getContainers(), currentNodeResponse.getAdditionalInfo());
    deliveryDocuments.forEach(x -> x.setGdmCurrentNodeDetail(gdmCurrentNodeDetail));

    log.info("[LT] Constructed deliveryDocuments: {}", deliveryDocuments);
    return deliveryDocuments;
  }

  private Pack mapContainerToPack(SsccScanResponse.Container container) {
    Pack pack = new Pack();

    pack.setExpiryDate(container.getExpiryDate());
    pack.setUnitCount(container.getUnitCount());
    pack.setShipmentNumber(container.getShipmentNumber());

    log.info("[LT] Mapped container to pack: {}", pack);
    return pack;
  }

  private void mapContainerToDeliveryDocumentLines(
      SsccScanResponse currentNodeResponse,
      List<DeliveryDocument> deliveryDocuments,
      Map<String, ShipmentDetails> shipmentDetailsMap) {
    // currentNodeResponse containers is a singleton list, so we can safely pass the first index
    Pack pack = mapContainerToPack(currentNodeResponse.getContainers().get(0));

    // based on mapPacksToDeliveryDocumentLines()
    List<PurchaseOrder> purchaseOrders = currentNodeResponse.getPurchaseOrders();
    purchaseOrders.forEach(
        purchaseOrder ->
            purchaseOrder
                .getLines()
                .forEach(
                    poLine -> {
                      DeliveryDocumentLine deliveryDocumentLine =
                          mapEpcisDeliveryDocumentLine(purchaseOrder, poLine, pack);
                      populateEpcisDocumentLine(
                          deliveryDocuments, purchaseOrder.getPoNumber(), deliveryDocumentLine);
                      populateEpcisManufactureDetailsToLineV2(pack, deliveryDocumentLine);
                      populateEpcisShipmentDetails(
                          shipmentDetailsMap, pack, deliveryDocumentLine, poLine);
                    }));
  }

  private void populateEpcisManufactureDetailsToLineV2(Pack pack, DeliveryDocumentLine line) {
    List<GdmGtinHierarchy> gdmGtinHierarchy = pack.getGtinHierarchy();
    List<GtinHierarchy> gtinHierarchies = new ArrayList<>();
    gtinHierarchies.add(
            new GtinHierarchy(line.getConsumableGTIN(), ReceivingConstants.ITEM_MDM_ITEM_UPC));
    gtinHierarchies.add(
            new GtinHierarchy(line.getOrderableGTIN(), ReceivingConstants.ITEM_MDM_CASE_UPC));
    gtinHierarchies.add(
            new GtinHierarchy(
                    line.getWarehousePackGtin(), ReceivingConstants.ITEM_WAREHOUSE_PACK_GTIN));
    gtinHierarchies.add(
            new GtinHierarchy(line.getCatalogGTIN(), ReceivingConstants.ITEM_CATALOG_GTIN));
    gtinHierarchies.add(new GtinHierarchy(line.getItemUpc(), ReceivingConstants.KEY_GTIN));
    if (Objects.nonNull(gdmGtinHierarchy)) {
      gdmGtinHierarchy.forEach(
              gtinHierarchy ->
                      gtinHierarchies.add(
                              new GtinHierarchy(gtinHierarchy.getGtin(), gtinHierarchy.getUom())));
    }
    line.setGtinHierarchy(gtinHierarchies);
  }

}
