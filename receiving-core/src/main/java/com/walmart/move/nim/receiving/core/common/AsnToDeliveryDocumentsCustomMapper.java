package com.walmart.move.nim.receiving.core.common;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static java.util.stream.Collectors.groupingBy;

import com.walmart.move.nim.receiving.core.common.exception.Error;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.ManufactureDetail;
import com.walmart.move.nim.receiving.core.model.gdm.v3.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Item;
import com.walmart.move.nim.receiving.core.model.gdm.v3.pack.GdmGtinHierarchy;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.Counted;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component(value = ReceivingConstants.DEFAULT_ASN_CUSTOM_MAPPER)
public class AsnToDeliveryDocumentsCustomMapper {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(AsnToDeliveryDocumentsCustomMapper.class);

  @ManagedConfiguration protected AppConfig appConfig;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Counted(
      name = "mapGdmResponseHitCount",
      level1 = "uwms-receiving",
      level2 = "asnToDeliveryDocumentsCustomMapper",
      level3 = "mapGdmResponse")
  @Timed(
      name = "mapGdmResponseTimed",
      level1 = "uwms-receiving",
      level2 = "asnToDeliveryDocumentsCustomMapper",
      level3 = "mapGdmResponse")
  @ExceptionCounted(
      name = "mapGdmResponseExceptionCount",
      level1 = "uwms-receiving",
      level2 = "asnToDeliveryDocumentsCustomMapper",
      level3 = "mapGdmResponse")
  public List<DeliveryDocument> mapGdmResponse(
      SsccScanResponse ssccScanAsnDetails, String scannedSscc, HttpHeaders httpHeaders)
      throws ReceivingException {
    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    List<Pack> packs = ssccScanAsnDetails.getPacks();
    List<Shipment> shipments = ssccScanAsnDetails.getShipments();
    Map<String, ShipmentDetails> shipmentDetailsMap = populateShipments(shipments);
    constructDeliveryDocumentsFromAsnData(ssccScanAsnDetails, deliveryDocuments);
    mapPacksToDeliveryDocumentLines(
        ssccScanAsnDetails, packs, deliveryDocuments, scannedSscc, shipmentDetailsMap, httpHeaders);
    LOGGER.info("Constructed deliveryDocuments:{}", deliveryDocuments);
    return deliveryDocuments;
  }

  public void checkIfPartialContent(List<Error> errors) {
    if (!CollectionUtils.isEmpty(errors)) {
      if (errors.get(0).getErrorCode().equals(ExceptionCodes.GDM_EPCIS_DATA_NOT_FOUND)) {
        throw new ReceivingBadDataException(
            ExceptionCodes.GDM_EPCIS_DATA_NOT_FOUND, ReceivingException.EPCIS_DATA_UNAVAILABLE);
      } else {
        throw new ReceivingBadDataException(
            ExceptionCodes.GDM_PARTIAL_SHIPMENT_DATA,
            "Partial response from GDM, Delivery or Po/PoLine is not present",
            errors.get(0).getDescription());
      }
    }
  }

  public boolean isGtinMatchingWithPOLine(Pack pack, PurchaseOrderLine poLine) {
    String alternateGtin = null;
    if (Objects.nonNull(poLine.getItemDetails().getItemAdditonalInformation())) {
      alternateGtin =
              poLine.getItemDetails().getItemAdditonalInformation()
                      .getOrDefault(ALTERNATE_GTIN, EMPTY_STRING)
                      .toString();
    }
    return StringUtils.equals(poLine.getItemDetails().getOrderableGTIN(), pack.getGtin())
            || StringUtils.equals(poLine.getItemDetails().getConsumableGTIN(), pack.getGtin())
            || StringUtils.equals(poLine.getItemDetails().getWarehousePackGTIN(), pack.getGtin())
            || StringUtils.equals(alternateGtin, pack.getGtin());
  }

  public void constructEpcisDeliveryDocumentAndLine(
          List<DeliveryDocument> deliveryDocuments,
          PurchaseOrderLine poLine,
          Pack pack,
          Map<String, ShipmentDetails> shipmentDetailsMap,
          PurchaseOrder purchaseOrder) {
    String poNumber = purchaseOrder.getPoNumber();
    Optional<DeliveryDocumentLine> deliveryDocumentLineOptional =
            checkIfPoLineExistsinEpcisDoc(deliveryDocuments, poLine, poNumber);
    DeliveryDocumentLine line;
    if (deliveryDocumentLineOptional.isPresent()) {
      line = deliveryDocumentLineOptional.get();
    } else {
      line = mapEpcisDeliveryDocumentLine(purchaseOrder, poLine, pack);
      populateEpcisDocumentLine(deliveryDocuments, purchaseOrder.getPoNumber(), line);
    }
    populateEpcisManufactureDetailsToLine(pack, line);
    populateEpcisShipmentDetails(shipmentDetailsMap, pack, line, poLine);
    populateEpcisPacks(line, pack);
  }

  public void mapPacksToDeliveryDocumentLines(
      SsccScanResponse ssccScanAsnDetails,
      List<Pack> packs,
      List<DeliveryDocument> deliveryDocuments,
      String scannedSscc,
      Map<String, ShipmentDetails> shipmentDetailsMap,
      HttpHeaders httpHeaders) {
    DeliveryDocumentLine line = null;
    List<PurchaseOrder> purchaseOrders = ssccScanAsnDetails.getPurchaseOrders();
    Map<String, List<InventoryDetail>> aggregatedQtyByPoAndPoLineMap = new HashMap<>();
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
          List<InventoryDetail> inventoryDetails =
              aggregatedQtyByPoAndPoLineMap.getOrDefault(asnPoPoLineKey, new ArrayList<>());
          inventoryDetails.add(item.getInventoryDetail());
          aggregatedQtyByPoAndPoLineMap.put(asnPoPoLineKey, inventoryDetails);

          Optional<DeliveryDocumentLine> deliveryDocumentLineOptional =
              checkIfPoLineExistsinDoc(deliveryDocuments, item);
          if (deliveryDocumentLineOptional.isPresent()) {
            // Add item to existing delivery doc line, same PO/PoLine but
            // probably split in different lots.
            DeliveryDocumentLine deliveryDocumentLine = deliveryDocumentLineOptional.get();
            populateManufactureDetailsToLine(item, deliveryDocumentLine);
            populateShipmentDetails(aggregatedQtyByPoAndPoLineMap, item, deliveryDocumentLine);
          } else {
            PurchaseOrder purchaseOrder = readPo(purchaseOrders, item);
            PurchaseOrderLine purchaseOrderLine = readPoLine(purchaseOrder, item);
            if (Objects.isNull(purchaseOrderLine)) {
              for (PurchaseOrderLine pol : purchaseOrder.getLines()) {
                if (Objects.equals(item.getItemNumber(), pol.getItemDetails().getNumber())) {
                  constructDeliveryDocumentAndLine(
                      purchaseOrder,
                      item,
                      pol,
                      deliveryDocuments,
                      aggregatedQtyByPoAndPoLineMap,
                      shipmentDetailsMap);
                }
              }
            } else {
              constructDeliveryDocumentAndLine(
                  purchaseOrder,
                  item,
                  purchaseOrderLine,
                  deliveryDocuments,
                  aggregatedQtyByPoAndPoLineMap,
                      shipmentDetailsMap);

              updateDeliveryDocumentIfDsdcDelivery(deliveryDocuments, ssccScanAsnDetails, pack);
            }
          }
        }
      }
      if (!ReceivingUtils.isDsdcDeliveryDocuments(deliveryDocuments)) {
        populateSSCC(deliveryDocuments, scannedSscc);
      }
    }
    LOGGER.info("GDM mapping completed successfully");
  }

  public void updateDeliveryDocumentIfDsdcDelivery(
      List<DeliveryDocument> deliveryDocuments, SsccScanResponse ssccScanAsnDetails, Pack pack) {
    boolean isDsdcSsccPackAvailableInGdm =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_DSDC_SSCC_PACKS_AVAILABLE_IN_GDM,
            false);

    if (isDsdcSsccPackAvailableInGdm && ReceivingUtils.isDsdcDeliveryDocuments(deliveryDocuments)) {
      for (DeliveryDocument deliveryDocument : deliveryDocuments) {
        if (Objects.equals(
            deliveryDocument.getDeliveryNumber(),
            ssccScanAsnDetails.getDelivery().getDeliveryNumber())) {
          /*Below two conditions verify different use cases:
          1. Before receiving started, in GDM audit status is null, audit required is true, If its flagged as audit by audit system
          2. When receiving prints audit label, GDM change audit status from null to "Pending" So it handles condition where same SSCC is scanned again before audit is completed*/
          if ((StringUtils.isBlank(pack.getAuditStatus()) && Objects.nonNull(pack.getAuditDetail()))
              || (StringUtils.equalsIgnoreCase(pack.getReceivingStatus(), OPEN)
                  && StringUtils.equalsIgnoreCase(pack.getAuditStatus(), PENDING))) {
            deliveryDocument.setAuditDetails(pack.getAuditDetail().isAuditRequired());
          }
          deliveryDocument.setAsnNumber(pack.getShipmentNumber());
        }
      }
    }
  }

  public void populateEpcisPacks(DeliveryDocumentLine line, Pack pack) {
    List<Pack> packs = line.getPacks();
    if (Objects.isNull(packs)) {
      packs = new ArrayList<>();
    }
    if (!packs.contains(pack)) {
      packs.add(pack);
    }
    line.setPacks(packs);
  }

  private void constructDeliveryDocumentAndLine(
      PurchaseOrder purchaseOrder,
      Item item,
      PurchaseOrderLine pol,
      List<DeliveryDocument> deliveryDocuments,
      Map<String, List<InventoryDetail>> aggregatedQtyByPoAndPoLineMap,
      Map<String, ShipmentDetails> shipmentDetailsMap) {
    DeliveryDocumentLine line =
        mapDeliveryDocumentLine(purchaseOrder, item, pol, shipmentDetailsMap);
    populateManufactureDetailsToLine(item, line);
    populateDocumentLine(
        deliveryDocuments,
        item.getPurchaseOrder().getPoNumber(),
        line); // Map PO to Items in the pack.
    boolean isDsdcSsccPackAvailableInGdm =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_DSDC_SSCC_PACKS_AVAILABLE_IN_GDM,
            false);
    if (!isDsdcSsccPackAvailableInGdm) {
      populateShipmentDetails(aggregatedQtyByPoAndPoLineMap, item, line);
    }
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

  public void populateEpcisShipmentDetails(
      Map<String, ShipmentDetails> shipmentDetailsMap,
      Pack pack,
      DeliveryDocumentLine line,
      PurchaseOrderLine poLine) {
    ShipmentDetails epcisShipmentDetails = shipmentDetailsMap.get(pack.getShipmentNumber());
    ShipmentDetails shipmentDetails = createShipmentDetails(epcisShipmentDetails);
    int shippedQty =
        ReceivingUtils.conversionToEaches(
            poLine.getOrderedQty(),
            poLine.getOrderedQtyUom(),
            line.getVendorPack(),
            line.getWarehousePack());
    shipmentDetails.setShippedQty(shippedQty);
    shipmentDetails.setShippedQtyUom(ReceivingConstants.Uom.EACHES);
    line.setShippedQty(shippedQty);
    line.setShippedQtyUom(ReceivingConstants.Uom.EACHES);
    List<ShipmentDetails> deliveryDocumentLineShipmentDetails = line.getShipmentDetailsList();
    if (Objects.isNull(deliveryDocumentLineShipmentDetails)) {
      deliveryDocumentLineShipmentDetails = new ArrayList<>();
    }
    if (!deliveryDocumentLineShipmentDetails.contains(shipmentDetails)) {
      deliveryDocumentLineShipmentDetails.add(shipmentDetails);
    }
    line.setShipmentDetailsList(deliveryDocumentLineShipmentDetails);
  }

  private void populateShipmentDetails(
      Map<String, List<InventoryDetail>> aggregatedQtyByPoAndPoLineMap,
      Item item,
      DeliveryDocumentLine line) {
    for (Map.Entry<String, List<InventoryDetail>> poPoLineEntry :
        aggregatedQtyByPoAndPoLineMap.entrySet()) {
      String poPoLineKey =
          item.getPurchaseOrder().getPoNumber() + item.getPurchaseOrder().getPoLineNumber();
      int shippedQty = 0;
      if (Objects.equals(poPoLineKey, poPoLineEntry.getKey())) {
        List<InventoryDetail> poPoLineInventoryDetails = poPoLineEntry.getValue();
        shippedQty =
            poPoLineInventoryDetails
                .stream()
                .mapToInt(
                    poPoLineInventoryDetail ->
                        ReceivingUtils.conversionToVendorPack(
                            poPoLineInventoryDetail.getDerivedQuantity().intValue(),
                            poPoLineInventoryDetail.getDerivedUom(),
                            line.getVendorPack(),
                            line.getWarehousePack()))
                .sum();
        line.setShippedQty(shippedQty);
        line.setShippedQtyUom(ReceivingConstants.Uom.VNPK);
      }
    }
  }

  private boolean checkIfPalletOrSSCC(List<DeliveryDocument> deliveryDocuments) {
    boolean isPalletSSCC = false;
    int casesForItem = 0;
    for (DeliveryDocument deliveryDocument : deliveryDocuments)
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        if (!CollectionUtils.isEmpty(deliveryDocumentLine.getManufactureDetails())) {
          casesForItem +=
              expectedQuantityInPallet(
                  deliveryDocumentLine.getManufactureDetails(),
                  deliveryDocumentLine.getVendorPack(),
                  deliveryDocumentLine.getWarehousePack());
        } else {
          casesForItem +=
              expectedShippedQuantityInPallet(
                  deliveryDocumentLine.getShippedQty(),
                  deliveryDocumentLine.getShippedQtyUom(),
                  deliveryDocumentLine.getVendorPack(),
                  deliveryDocumentLine.getWarehousePack());
        }

        if (casesForItem > 1) {
          break;
        }
      }
    if (casesForItem > 1 || isMultiSKUPallet(deliveryDocuments)) {
      isPalletSSCC = true;
    }
    return isPalletSSCC;
  }

  public static int expectedQuantityInPallet(
      List<ManufactureDetail> manufactureDetails, Integer vnpkQty, Integer whpkQty) {
    return manufactureDetails
        .stream()
        .mapToInt(
            manufactureDetail ->
                ReceivingUtils.conversionToVendorPack(
                    manufactureDetail.getQty(),
                    manufactureDetail.getReportedUom(),
                    vnpkQty,
                    whpkQty))
        .sum();
  }

  public static int expectedShippedQuantityInPallet(
      Integer shippedQty, String shippedQtyUom, Integer vendorPack, Integer warehousePack) {
    return ReceivingUtils.conversionToVendorPackRoundUp(
        shippedQty, shippedQtyUom, vendorPack, warehousePack);
  }

  public static boolean isMultiSKUPallet(List<DeliveryDocument> deliveryDocuments) {
    if (!CollectionUtils.isEmpty(deliveryDocuments)
        && !CollectionUtils.isEmpty(deliveryDocuments.get(0).getDeliveryDocumentLines())) {
      Map<String, List<DeliveryDocumentLine>> distinctItems =
          deliveryDocuments
              .get(0)
              .getDeliveryDocumentLines()
              .stream()
              .collect(groupingBy(DeliveryDocumentLine::getGtin));

      return (distinctItems.size() > 1);
    }
    return false;
  }

  public void populateSSCC(List<DeliveryDocument> deliveryDocuments, String scannedSscc) {
    if (StringUtils.isNotEmpty(scannedSscc)) {
      List<DeliveryDocumentLine> deliveryDocumentLines =
          deliveryDocuments.get(0).getDeliveryDocumentLines();
      boolean isPalletSscc = checkIfPalletOrSSCC(deliveryDocuments);
      deliveryDocumentLines.forEach(
          line -> {
            if (isPalletSscc) {
              line.setPalletSSCC(scannedSscc);
            } else {
              line.setPackSSCC(scannedSscc);
            }
          });
    }
  }

  protected Map<String, ShipmentDetails> populateShipments(List<Shipment> shipments) {
    Map<String, ShipmentDetails> shipmentDetailsMap = new HashMap<>();
    for (Shipment asnShipmentDetails : shipments) {
      ShipmentDetails shipmentDetails = new ShipmentDetails();
      shipmentDetails.setInboundShipmentDocId(asnShipmentDetails.getDocumentId());
      if (Objects.nonNull(asnShipmentDetails.getSource())) {
        shipmentDetails.setSourceGlobalLocationNumber(
            asnShipmentDetails.getSource().getGlobalLocationNumber());
      }
      if (Objects.nonNull(asnShipmentDetails.getLoadNumber())) {
        shipmentDetails.setLoadNumber(asnShipmentDetails.getLoadNumber());
      }
      if (Objects.nonNull(asnShipmentDetails.getDestination())) {
        shipmentDetails.setDestinationGlobalLocationNumber(
            asnShipmentDetails.getDestination().getGlobalLocationNumber());
      }
      shipmentDetails.setDocumentType(asnShipmentDetails.getDocumentType());
      shipmentDetails.setShipmentNumber(asnShipmentDetails.getShipmentNumber());
      shipmentDetailsMap.put(asnShipmentDetails.getShipmentNumber(), shipmentDetails);
    }
    return shipmentDetailsMap;
  }

  public void populateEpcisManufactureDetailsToLine(Pack pack, DeliveryDocumentLine line) {
    ManufactureDetail details;
    details = new ManufactureDetail();
    details.setLot(StringUtils.trim(pack.getLotNumber()));
    details.setExpiryDate(StringUtils.trim(pack.getExpiryDate()));
    details.setQty(
        ReceivingUtils.conversionToEaches(
            pack.getUnitCount().intValue(),
            ReceivingConstants.Uom.WHPK,
            line.getVendorPack(),
            line.getWarehousePack()));
    details.setReportedUom(ReceivingConstants.Uom.EACHES);
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

    List<ManufactureDetail> listOfManufactureDetails = line.getManufactureDetails();
    if (listOfManufactureDetails == null) {
      List<ManufactureDetail> manufactureDetailsList = new ArrayList<>();
      manufactureDetailsList.add(details);
      line.setManufactureDetails(manufactureDetailsList);
    } else {
      Optional<ManufactureDetail> manufactureDetailOptional =
          listOfManufactureDetails
              .stream()
              .filter(
                  detail ->
                      StringUtils.isNotBlank(detail.getLot())
                          && detail
                              .getLot()
                              .equalsIgnoreCase(StringUtils.trim(pack.getLotNumber())))
              .findFirst();
      if (manufactureDetailOptional.isPresent()) {
        manufactureDetailOptional
            .get()
            .setQty(
                manufactureDetailOptional.get().getQty()
                    + ReceivingUtils.conversionToEaches(
                        pack.getUnitCount().intValue(),
                        ReceivingConstants.Uom.WHPK,
                        line.getVendorPack(),
                        line.getWarehousePack()));
      } else {
        listOfManufactureDetails.add(details);
      }
    }
  }

  public void populateManufactureDetailsToLine(Item item, DeliveryDocumentLine line) {
    ManufactureDetail details;
    List<com.walmart.move.nim.receiving.core.model.gdm.v3.ManufactureDetail> manufactureDetails =
        item.getManufactureDetails();
    if (!CollectionUtils.isEmpty(manufactureDetails)) {
      for (com.walmart.move.nim.receiving.core.model.gdm.v3.ManufactureDetail manufactureDetail :
          manufactureDetails) {
        details = new ManufactureDetail();
        details.setLot(manufactureDetail.getLotNumber());
        details.setExpiryDate(manufactureDetail.getExpirationDate());
        int reportedQuantity =
            manufactureDetail.getReportedQuantity() != null
                ? manufactureDetail.getReportedQuantity().intValue()
                : item.getInventoryDetail().getDerivedQuantity().intValue();
        details.setQty(reportedQuantity);
        if (Objects.nonNull(manufactureDetail.getReportedQuantity())) {
          details.setQty(manufactureDetail.getReportedQuantity().intValue());
          String reportedUom = item.getInventoryDetail().getDerivedUom();
          if (reportedUom.equalsIgnoreCase(ReceivingConstants.Uom.EACHES)) {
            reportedUom = ReceivingConstants.Uom.WHPK;
          }
          details.setReportedUom(reportedUom);
        } else {
          details.setReportedUom(item.getInventoryDetail().getDerivedUom());
          details.setQty(item.getInventoryDetail().getDerivedQuantity().intValue());
        }
        List<GdmGtinHierarchy> gdmGtinHierarchy = item.getGtinHierarchy();
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

        List<ManufactureDetail> listOfManufactureDetails = line.getManufactureDetails();
        if (listOfManufactureDetails == null) {
          List<ManufactureDetail> manufactureDetailsList = new ArrayList<>();
          manufactureDetailsList.add(details);
          line.setManufactureDetails(manufactureDetailsList);
        } else {
          Optional<ManufactureDetail> manufactureDetailOptional =
              listOfManufactureDetails
                  .stream()
                  .filter(
                      detail ->
                          StringUtils.isNotBlank(detail.getLot())
                              && detail.getLot().equalsIgnoreCase(manufactureDetail.getLotNumber()))
                  .findFirst();
          if (manufactureDetailOptional.isPresent()) {
            manufactureDetailOptional
                .get()
                .setQty(
                    manufactureDetailOptional.get().getQty()
                        + item.getInventoryDetail().getDerivedQuantity().intValue());
          } else {
            listOfManufactureDetails.add(details);
          }
        }
      }
    }
  }

  public void populateEpcisDocumentLine(
      List<DeliveryDocument> deliveryDocuments, String poNumber, DeliveryDocumentLine line) {
    for (DeliveryDocument deliveryDoc : deliveryDocuments) {
      if (StringUtils.equals(deliveryDoc.getPurchaseReferenceNumber(), poNumber)) {
        if (deliveryDoc.getDeliveryDocumentLines() != null) {
          deliveryDoc.getDeliveryDocumentLines().add(line);
        } else {
          deliveryDoc.setDeliveryDocumentLines(createDeliveryDocumentLine(line));
        }
      }
    }
  }

  public void populateDocumentLine(
      List<DeliveryDocument> deliveryDocuments, String packPoNumber, DeliveryDocumentLine line) {
    Optional<DeliveryDocument> deliveryDoc =
        deliveryDocuments
            .stream()
            .filter(document -> document.getPurchaseReferenceNumber().equals(packPoNumber))
            .findFirst();
    if (deliveryDoc.isPresent()) {
      if (deliveryDoc.get().getDeliveryDocumentLines() != null) {
        deliveryDoc.get().getDeliveryDocumentLines().add(line);
      } else {
        deliveryDoc.get().setDeliveryDocumentLines(createDeliveryDocumentLine(line));
      }
    }
  }

  public DeliveryDocumentLine mapEpcisDeliveryDocumentLine(
      PurchaseOrder purchaseOrder, PurchaseOrderLine purchaseOrderLine, Pack pack) {
    ItemDetails purchaseOrderLineItem = purchaseOrderLine.getItemDetails();
    DeliveryDocumentLine line = new DeliveryDocumentLine();

    if (StringUtils.isNotBlank(pack.getGtin())) {
      line.setItemUpc(pack.getGtin());
    } else {
      line.setItemUpc(purchaseOrderLineItem.getOrderableGTIN());
    }
    line.setCaseUpc(purchaseOrderLineItem.getOrderableGTIN());
    line.setConsumableGTIN(purchaseOrderLineItem.getConsumableGTIN());
    line.setOrderableGTIN(purchaseOrderLineItem.getOrderableGTIN());
    line.setCatalogGTIN(purchaseOrderLineItem.getCatalogGTIN());
    line.setWarehousePackGtin(purchaseOrderLineItem.getWarehousePackGTIN());
    line.setPurchaseReferenceNumber(purchaseOrder.getPoNumber());
    line.setPurchaseReferenceLineNumber(purchaseOrderLine.getPoLineNumber());
    line.setEvent(purchaseOrderLine.getEvent());
    line.setPurchaseReferenceLineStatus(purchaseOrderLine.getPoLineStatus());
    line.setWarehousePackSell(purchaseOrderLine.getWhpkSell());
    line.setVendorPackCost(purchaseOrderLine.getVnpkCost());
    line.setVendorPack(purchaseOrderLine.getVnpkQty());
    line.setWarehousePack(purchaseOrderLine.getWhpkQty());
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        ReceivingConstants.CONVERT_QUANTITY_TO_EACHES_FLAG,
        false)) {

      line.setTotalOrderQty(
          ReceivingUtils.conversionToEaches(
              purchaseOrderLine.getOrderedQty(),
              purchaseOrderLine.getOrderedQtyUom(),
              purchaseOrderLine.getVnpkQty(),
              purchaseOrderLine.getWhpkQty()));
      line.setQtyUOM(ReceivingConstants.Uom.EACHES);
    } else {
      line.setQtyUOM(purchaseOrderLine.getOrderedQtyUom());
      line.setTotalOrderQty(purchaseOrderLine.getOrderedQty());
    }
    line.setItemNbr(purchaseOrderLineItem.getNumber());
    line.setPurchaseRefType(purchaseOrderLine.getChannel());
    line.setPalletTie(purchaseOrderLineItem.getPalletTi());
    line.setPalletHigh(purchaseOrderLineItem.getPalletHi());
    line.setWeight(purchaseOrderLine.getVnpkWeightQty()); // vnpkWgtQty
    line.setWeightUom(purchaseOrderLine.getVnpkWeightQtyUom()); // vnpkWgtUom
    line.setCube(purchaseOrderLine.getVnpkCubeQty()); // vnpkcbqty
    line.setCubeUom(purchaseOrderLine.getVnpkCubeQtyUom()); // vnpkcbuomcd
    line.setDescription(purchaseOrderLineItem.getDescriptions().get(0));
    line.setColor(purchaseOrderLineItem.getColor());
    line.setSize(purchaseOrderLineItem.getSize());
    line.setIsHazmat(purchaseOrderLineItem.getHazmat());
    line.setDeptNumber(purchaseOrder.getVendorInformation().getDepartment().toString());
    line.setDepartment(purchaseOrder.getVendorInformation().getDepartment().toString());
    if (Objects.nonNull(purchaseOrder.getVendorInformation().getNumber())) {
      line.setVendorNbrDeptSeq(Integer.valueOf(purchaseOrder.getVendorInformation().getNumber()));
    }

    line.setOverageQtyLimit(purchaseOrderLine.getOvgThresholdQuantityLimit());
    if (StringUtils.isNotBlank(pack.getGtin())) {
      line.setGtin(pack.getGtin());
    } else {
      line.setGtin(purchaseOrderLineItem.getOrderableGTIN());
    }
    line.setNdc(purchaseOrderLineItem.getSupplierStockId());
    line.setLimitedQtyVerifiedOn(purchaseOrderLineItem.getLimitedQtyVerifiedOn());
    line.setTransportationModes(purchaseOrderLineItem.getTransportationModes());
    line.setSize(purchaseOrderLineItem.getSize());
    line.setFreightBillQty(purchaseOrderLine.getFreightBillQty());
    if (Objects.nonNull(purchaseOrderLine.getItemDetails())) {
      ItemDetails itemDetails = purchaseOrderLine.getItemDetails();
      line.setBolWeight(purchaseOrderLine.getBolWeightQty());
      line.setVendorStockNumber(itemDetails.getSupplierStockId());

      line.setWarehousePackQuantity(itemDetails.getWarehousePackQuantity());
      line.setOrderableQuantity(purchaseOrderLine.getItemDetails().getOrderableQuantity());
      Map<String, Object> polAdditionalFields = purchaseOrderLine.getPolAdditionalFields();
      if (Objects.nonNull(polAdditionalFields)) {
        line.setPromoBuyInd(
            getOrDefaultValue(
                polAdditionalFields, ReceivingConstants.PROMO_BUY_IND, String.class, EMPTY_STRING));
      }

      if (Objects.nonNull(itemDetails.getItemAdditonalInformation())) {
        Map<String, Object> itemAdditonalInformation =
            purchaseOrderLine.getItemDetails().getItemAdditonalInformation();
        String isDscsaExemptionInd =
            itemAdditonalInformation
                .getOrDefault(ReceivingConstants.IS_DSCSA_EXEMPTION_IND, Boolean.FALSE.toString())
                .toString();
        ItemData additionalInfo = new ItemData();
        additionalInfo.setIsDscsaExemptionInd(Boolean.valueOf(isDscsaExemptionInd));
        additionalInfo.setDcWeightFormatTypeCode(
            getOrDefaultValue(
                itemAdditonalInformation,
                ReceivingConstants.DC_WEIGHT_FORMAT_TYPE_CODE,
                String.class,
                null));
        additionalInfo.setOmsWeightFormatTypeCode(
            getOrDefaultValue(
                itemAdditonalInformation,
                ReceivingConstants.OMS_WEIGHT_FORMAT_TYPE_CODE,
                String.class,
                null));
        additionalInfo.setProfiledWarehouseArea(
            getOrDefaultValue(
                itemAdditonalInformation,
                ReceivingConstants.PROFILED_WARE_HOUSE_AREA,
                String.class,
                null));
        additionalInfo.setIsHACCP(
            getOrDefaultValue(
                itemAdditonalInformation,
                ReceivingConstants.IS_HACCP,
                Boolean.class,
                Boolean.FALSE));

        additionalInfo.setWarehouseMinLifeRemainingToReceive(
            getOrDefaultValue(
                    itemAdditonalInformation,
                    ReceivingConstants.WARE_HOUSE_MIN_LIFE_REMAINING_TO_RECEIVE,
                    Integer.class,
                    0)
                .intValue());
        additionalInfo.setWarehouseRotationTypeCode(
            getOrDefaultValue(
                itemAdditonalInformation,
                ReceivingConstants.WARE_HOUSE_ROTATION_TYPE_CODE,
                String.class,
                null));
        additionalInfo.setWeightFormatTypeCode(
            getOrDefaultValue(
                itemAdditonalInformation,
                ReceivingConstants.WEIGHT_FORMAT_TYPE_CODE,
                String.class,
                null));
        additionalInfo.setWarehouseAreaCode(
            getOrDefaultValue(
                itemAdditonalInformation,
                ReceivingConstants.WARE_HOUSE_AREA_CODE,
                String.class,
                null));
        additionalInfo.setWarehouseGroupCode(
            getOrDefaultValue(
                itemAdditonalInformation,
                ReceivingConstants.WARE_HOUSE_GROUP_CODE,
                String.class,
                EMPTY_STRING));
        additionalInfo.setWarehouseAreaDesc(
            getOrDefaultValue(
                itemAdditonalInformation,
                ReceivingConstants.WAREHOUSE_AREA_DESC,
                String.class,
                null));
        ;
        additionalInfo.setWeight(
            getOrDefaultValue(
                    itemAdditonalInformation, ReceivingConstants.WEIGHT, Double.class, 0.0)
                .floatValue());
        additionalInfo.setWeightUOM(
            getOrDefaultValue(
                itemAdditonalInformation, ReceivingConstants.WEIGHT_UOM, String.class, null));
        additionalInfo.setIsCompliancePack(itemDetails.getComplianceItem());
        additionalInfo.setHandlingCode(itemDetails.getHandlingCode());
        additionalInfo.setIsTemperatureSensitive(itemDetails.getIsTemperatureSensitive());
        additionalInfo.setIsControlledSubstance(itemDetails.getIsControlledSubstance());
        if (Objects.nonNull(purchaseOrder.getVendorInformation())
            && (tenantSpecificConfigReader.getConfiguredFeatureFlag(
                TenantContext.getFacilityNum().toString(),
                IS_GDM_SHIPMENT_GET_BY_SCAN_v4_ENABLED,
                false))) {
          additionalInfo.setIsEpcisEnabledVendor(
              purchaseOrder.getVendorInformation().isSerialInfoEnabled());
        }
        // against same unit
        line.setAdditionalInfo(additionalInfo);
      }
    }
    if (ObjectUtils.allNotNull(purchaseOrderLine.getOrderedQty())) {
      int aggregatedItemQty =
          ReceivingUtils.conversionToEaches(
              purchaseOrderLine.getOrderedQty(),
              purchaseOrderLine.getOrderedQtyUom(),
              line.getVendorPack(),
              line.getWarehousePack());
      line.setShippedQty(aggregatedItemQty);
      line.setShippedQtyUom(ReceivingConstants.Uom.EACHES);
    }

    if (Objects.nonNull(pack.getLotNumber())) {
      line.setLotNumber(StringUtils.trim(pack.getLotNumber()));
    }

    return line;
  }

  public DeliveryDocumentLine mapDeliveryDocumentLine(
      PurchaseOrder purchaseOrder,
      Item item,
      PurchaseOrderLine purchaseOrderLine,
      Map<String, ShipmentDetails> shipmentDetailsMap) {
    ItemDetails purchaseOrderLineItem = purchaseOrderLine.getItemDetails();
    DeliveryDocumentLine line = new DeliveryDocumentLine();
    line.setItemUpc(item.getGtin());
    line.setCaseUpc(purchaseOrderLineItem.getOrderableGTIN());
    line.setItemUpc(purchaseOrderLineItem.getConsumableGTIN());
    line.setConsumableGTIN(purchaseOrderLineItem.getConsumableGTIN());
    line.setOrderableGTIN(purchaseOrderLineItem.getOrderableGTIN());
    line.setCatalogGTIN(purchaseOrderLineItem.getCatalogGTIN());
    line.setWarehousePackGtin(purchaseOrderLineItem.getWarehousePackGTIN());
    line.setPurchaseReferenceNumber(item.getPurchaseOrder().getPoNumber());
    line.setPurchaseReferenceLineNumber(purchaseOrderLine.getPoLineNumber());
    line.setEvent(purchaseOrderLine.getEvent());
    line.setPurchaseReferenceLineStatus(purchaseOrderLine.getPoLineStatus());
    line.setWarehousePackSell(purchaseOrderLine.getWhpkSell());
    line.setVendorPackCost(purchaseOrderLine.getVnpkCost());
    line.setVendorPack(purchaseOrderLine.getVnpkQty());
    line.setWarehousePack(purchaseOrderLine.getWhpkQty());
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        ReceivingConstants.CONVERT_QUANTITY_TO_EACHES_FLAG,
        false)) {

      line.setTotalOrderQty(
          ReceivingUtils.conversionToEaches(
              purchaseOrderLine.getOrderedQty(),
              purchaseOrderLine.getOrderedQtyUom(),
              purchaseOrderLine.getVnpkQty(),
              purchaseOrderLine.getWhpkQty()));
      line.setQtyUOM(ReceivingConstants.Uom.EACHES);
    } else {
      line.setQtyUOM(purchaseOrderLine.getOrderedQtyUom());
      line.setTotalOrderQty(purchaseOrderLine.getOrderedQty());
    }
    line.setItemNbr(purchaseOrderLineItem.getNumber());
    line.setPurchaseRefType(purchaseOrderLine.getChannel());
    line.setPalletTie(purchaseOrderLineItem.getPalletTi());
    line.setPalletHigh(purchaseOrderLineItem.getPalletHi());
    line.setWeight(purchaseOrderLine.getVnpkWeightQty()); // vnpkWgtQty
    line.setWeightUom(purchaseOrderLine.getVnpkWeightQtyUom()); // vnpkWgtUom
    line.setCube(purchaseOrderLine.getVnpkCubeQty()); // vnpkcbqty
    line.setCubeUom(purchaseOrderLine.getVnpkCubeQtyUom()); // vnpkcbuomcd
    populateItemDecription(line, item, purchaseOrderLineItem);
    line.setColor(purchaseOrderLineItem.getColor());
    line.setSize(purchaseOrderLineItem.getSize());
    line.setIsHazmat(purchaseOrderLineItem.getHazmat());
    line.setDeptNumber(purchaseOrder.getVendorInformation().getDepartment().toString());
    line.setDepartment(purchaseOrder.getVendorInformation().getDepartment().toString());
    if (Objects.nonNull(item.getVendorId())) {
      line.setVendorNbrDeptSeq(Integer.valueOf(item.getVendorId()));
    }

    line.setOverageQtyLimit(purchaseOrderLine.getOvgThresholdQuantityLimit());
    line.setGtin(item.getGtin());
    if (StringUtils.isNotBlank(item.getGtin())) {
      line.setGtin(item.getGtin());
    } else {
      line.setGtin(purchaseOrderLineItem.getOrderableGTIN());
    }
    line.setNdc(item.getNationalDrugCode());
    line.setLimitedQtyVerifiedOn(purchaseOrderLineItem.getLimitedQtyVerifiedOn());
    line.setLithiumIonVerifiedOn(purchaseOrderLineItem.getLimitedIonVerifiedOn());
    line.setTransportationModes(purchaseOrderLineItem.getTransportationModes());
    line.setSize(purchaseOrderLineItem.getSize());
    line.setFreightBillQty(purchaseOrderLine.getFreightBillQty());
    if (Objects.nonNull(purchaseOrderLine.getItemDetails())) {
      ItemDetails itemDetails = purchaseOrderLine.getItemDetails();
      line.setBolWeight(purchaseOrderLine.getBolWeightQty());
      line.setVendorStockNumber(itemDetails.getSupplierStockId());
      line.setWarehousePackQuantity(itemDetails.getWarehousePackQuantity());
      line.setOrderableQuantity(purchaseOrderLine.getItemDetails().getOrderableQuantity());
      line.setNewItem(
          Objects.nonNull(itemDetails.getIsNewItem()) ? itemDetails.getIsNewItem() : Boolean.FALSE);
      Map<String, Object> polAdditionalFields = purchaseOrderLine.getPolAdditionalFields();
      if (Objects.nonNull(polAdditionalFields)) {
        line.setPromoBuyInd(
            getOrDefaultValue(
                polAdditionalFields, ReceivingConstants.PROMO_BUY_IND, String.class, EMPTY_STRING));
      }

      if (Objects.nonNull(itemDetails.getItemAdditonalInformation())) {
        Map<String, Object> itemAdditonalInformation =
            purchaseOrderLine.getItemDetails().getItemAdditonalInformation();
        String isDscsaExemptionInd =
            itemAdditonalInformation
                .getOrDefault(ReceivingConstants.IS_DSCSA_EXEMPTION_IND, Boolean.FALSE.toString())
                .toString();
        ItemData additionalInfo = new ItemData();
        additionalInfo.setIsDscsaExemptionInd(Boolean.valueOf(isDscsaExemptionInd));
        String handlingCode =
            StringUtils.isNotEmpty(itemDetails.getHandlingCode())
                ? itemDetails.getHandlingCode()
                : null;
        String packTypeCode =
            StringUtils.isNotEmpty(itemDetails.getPackType()) ? itemDetails.getPackType() : null;
        additionalInfo.setHandlingCode(handlingCode);
        additionalInfo.setIsTemperatureSensitive(itemDetails.getIsTemperatureSensitive());
        additionalInfo.setIsControlledSubstance(itemDetails.getIsControlledSubstance());
        additionalInfo.setPackTypeCode(packTypeCode);
        String itemHandlingMethod =
            PACKTYPE_HANDLINGCODE_MAP.get(
                org.apache.commons.lang3.StringUtils.join(packTypeCode, handlingCode));
        additionalInfo.setItemHandlingMethod(
            Objects.isNull(itemHandlingMethod)
                ? INVALID_HANDLING_METHOD_OR_PACK_TYPE
                : itemHandlingMethod);
        additionalInfo.setDcWeightFormatTypeCode(
            getOrDefaultValue(
                itemAdditonalInformation,
                ReceivingConstants.DC_WEIGHT_FORMAT_TYPE_CODE,
                String.class,
                null));
        additionalInfo.setOmsWeightFormatTypeCode(
            getOrDefaultValue(
                itemAdditonalInformation,
                ReceivingConstants.OMS_WEIGHT_FORMAT_TYPE_CODE,
                String.class,
                null));
        additionalInfo.setProfiledWarehouseArea(
            getOrDefaultValue(
                itemAdditonalInformation,
                ReceivingConstants.PROFILED_WARE_HOUSE_AREA,
                String.class,
                null));
        additionalInfo.setIsHACCP(
            getOrDefaultValue(
                itemAdditonalInformation,
                ReceivingConstants.IS_HACCP,
                Boolean.class,
                Boolean.FALSE));

        additionalInfo.setWarehouseMinLifeRemainingToReceive(
            getOrDefaultValue(
                    itemAdditonalInformation,
                    ReceivingConstants.WARE_HOUSE_MIN_LIFE_REMAINING_TO_RECEIVE,
                    Integer.class,
                    0)
                .intValue());
        additionalInfo.setWarehouseRotationTypeCode(
            getOrDefaultValue(
                itemAdditonalInformation,
                ReceivingConstants.WARE_HOUSE_ROTATION_TYPE_CODE,
                String.class,
                null));
        additionalInfo.setWeightFormatTypeCode(
            getOrDefaultValue(
                itemAdditonalInformation,
                ReceivingConstants.WEIGHT_FORMAT_TYPE_CODE,
                String.class,
                null));
        additionalInfo.setWarehouseAreaCode(
            getOrDefaultValue(
                itemAdditonalInformation,
                ReceivingConstants.WARE_HOUSE_AREA_CODE,
                String.class,
                null));
        additionalInfo.setWarehouseGroupCode(
            getOrDefaultValue(
                itemAdditonalInformation,
                ReceivingConstants.WARE_HOUSE_GROUP_CODE,
                String.class,
                EMPTY_STRING));
        additionalInfo.setWarehouseAreaDesc(
            getOrDefaultValue(
                itemAdditonalInformation,
                ReceivingConstants.WAREHOUSE_AREA_DESC,
                String.class,
                null));
        ;
        additionalInfo.setWeight(
            getOrDefaultValue(
                    itemAdditonalInformation, ReceivingConstants.WEIGHT, Double.class, 0.0)
                .floatValue());
        additionalInfo.setWeightUOM(
            getOrDefaultValue(
                itemAdditonalInformation, ReceivingConstants.WEIGHT_UOM, String.class, null));
        additionalInfo.setIsCompliancePack(itemDetails.getComplianceItem());
        additionalInfo.setHandlingCode(itemDetails.getHandlingCode());
        additionalInfo.setIsTemperatureSensitive(itemDetails.getIsTemperatureSensitive());
        additionalInfo.setIsControlledSubstance(itemDetails.getIsControlledSubstance());
        if (Objects.nonNull(purchaseOrder.getVendorInformation())
            && (tenantSpecificConfigReader.getConfiguredFeatureFlag(
                TenantContext.getFacilityNum().toString(),
                IS_GDM_SHIPMENT_GET_BY_SCAN_v4_ENABLED,
                false))) {
          additionalInfo.setIsEpcisEnabledVendor(
              purchaseOrder.getVendorInformation().isSerialInfoEnabled());
          Optional<ShipmentDetails> shipmentDetails =
              shipmentDetailsMap.values().stream().findFirst();
          if (shipmentDetails.isPresent() && null != shipmentDetails.get().getDocumentType()) {
            additionalInfo.setAutoSwitchEpcisToAsn(
                shipmentDetails.get().getDocumentType().equalsIgnoreCase(ReceivingConstants.ASN));
          }
        }
        // against same unit
        line.setAdditionalInfo(additionalInfo);
      }
    }
    if (ObjectUtils.allNotNull(item.getAggregatedItemQty(), item.getAggregatedItemQtyUom())) {
      int aggregatedItemQty =
          ReceivingUtils.conversionToEaches(
              item.getAggregatedItemQty(),
              item.getAggregatedItemQtyUom(),
              line.getVendorPack(),
              line.getWarehousePack());
      line.setShippedQty(aggregatedItemQty);
      line.setShippedQtyUom(ReceivingConstants.Uom.EACHES);
    }

    if (!CollectionUtils.isEmpty(item.getManufactureDetails())) {
      line.setLotNumber(
          StringUtils.isNotEmpty(item.getManufactureDetails().get(0).getLotNumber())
              ? item.getManufactureDetails().get(0).getLotNumber()
              : null);
    }

    return line;
  }

  private void populateItemDecription(
      DeliveryDocumentLine line, Item packItem, ItemDetails poItemDetails) {
    if (StringUtils.isNotBlank(packItem.getItemDescription())) {
      line.setDescription(packItem.getItemDescription());
    } else if (!CollectionUtils.isEmpty(poItemDetails.getDescriptions())) {
      line.setDescription(poItemDetails.getDescriptions().get(0));
    } else {
      line.setDescription(EMPTY_STRING);
    }
  }

  public PurchaseOrder readEpcisPo(List<PurchaseOrder> purchaseOrders, Pack pack) {
    Optional<PurchaseOrder> purchaseOrderOptional =
        purchaseOrders
            .stream()
            .filter(po -> po.getPoNumber().equals(pack.getPoNumber()))
            .findFirst();
    if (!purchaseOrderOptional.isPresent()) {
      throw new ReceivingBadDataException(
          ExceptionCodes.PO_NOT_FOUND,
          "EPCIS Purchase Order is not available for pack in GDM response.");
    }
    return purchaseOrderOptional.get();
  }

  public PurchaseOrder readPo(List<PurchaseOrder> purchaseOrders, Item item) {
    Optional<PurchaseOrder> purchaseOrderOptional =
        purchaseOrders
            .stream()
            .filter(po -> po.getPoNumber().equals(item.getPurchaseOrder().getPoNumber()))
            .findFirst();
    if (!purchaseOrderOptional.isPresent()) {
      throw new ReceivingBadDataException(
          ExceptionCodes.PO_NOT_FOUND, "Purchase Order is not available for pack in GDM response.");
    }
    return purchaseOrderOptional.get();
  }

  public PurchaseOrderLine readPoLine(PurchaseOrder purchaseOrder, Item item) {
    if (Objects.isNull(item.getPurchaseOrder().getPoLineNumber())) {
      return null;
    }
    Optional<PurchaseOrderLine> purchaseOrderLineOptional =
        purchaseOrder
            .getLines()
            .stream()
            .filter(
                poLine ->
                    poLine
                        .getPoLineNumber()
                        .equals(Integer.valueOf(item.getPurchaseOrder().getPoLineNumber())))
            .findFirst();
    if (!purchaseOrderLineOptional.isPresent()) {
      throw new ReceivingBadDataException(
          ExceptionCodes.PO_LINE_NOT_FOUND,
          "Purchase Order/Line is not available for pack in GDM response.");
    }
    return purchaseOrderLineOptional.get();
  }

  public Optional<DeliveryDocumentLine> checkIfPoLineExistsinEpcisDoc(
      List<DeliveryDocument> deliveryDocuments, PurchaseOrderLine pol, String poNumber) {
    if (CollectionUtils.isEmpty(deliveryDocuments)) {
      return Optional.empty();
    }
    Optional<DeliveryDocumentLine> deliveryDocumentLine = Optional.empty();
    try {
      Optional<DeliveryDocument> deliveryDoc =
          deliveryDocuments
              .stream()
              .filter(document -> document.getPurchaseReferenceNumber().equals(poNumber))
              .findFirst();
      if (deliveryDoc.isPresent()) {
        List<DeliveryDocumentLine> deliveryDocumentLines =
            deliveryDoc.get().getDeliveryDocumentLines();
        if (!CollectionUtils.isEmpty(deliveryDocumentLines)) {
          deliveryDocumentLine =
              deliveryDocumentLines
                  .stream()
                  .filter(
                      line -> pol.getPoLineNumber().equals(line.getPurchaseReferenceLineNumber()))
                  .findFirst();
        }
      }
      return deliveryDocumentLine;
    } catch (Exception e) {
      LOGGER.info("No such element found", e);
    }
    return deliveryDocumentLine;
  }

  public Optional<DeliveryDocumentLine> checkIfPoLineExistsinDoc(
      List<DeliveryDocument> deliveryDocuments, Item item) {
    if (CollectionUtils.isEmpty(deliveryDocuments)) {
      return Optional.empty();
    }
    Optional<DeliveryDocumentLine> deliveryDocumentLine = Optional.empty();
    try {
      Optional<DeliveryDocument> deliveryDoc =
          deliveryDocuments
              .stream()
              .filter(
                  document ->
                      document
                          .getPurchaseReferenceNumber()
                          .equals(
                              item.getPurchaseOrder()
                                  .getPoNumber())) // TODO map packPONum and po line
              .findFirst();
      if (deliveryDoc.isPresent()) {
        List<DeliveryDocumentLine> deliveryDocumentLines =
            deliveryDoc.get().getDeliveryDocumentLines();
        if (!CollectionUtils.isEmpty(deliveryDocumentLines)) {
          deliveryDocumentLine =
              deliveryDocumentLines
                  .stream()
                  .filter(
                      line ->
                          item.getPurchaseOrder()
                              .getPoLineNumber()
                              .equals(String.valueOf(line.getPurchaseReferenceLineNumber())))
                  .findFirst();
        }
      }

      return deliveryDocumentLine;
    } catch (Exception e) {
      LOGGER.info("No such element found", e);
    }
    return deliveryDocumentLine;
  }

  protected void constructDeliveryDocumentsFromAsnData(
      SsccScanResponse ssccScanAsnDetails, List<DeliveryDocument> deliveryDocuments)
      throws ReceivingException {
    DeliveryDocument deliveryDocument;
    List<PurchaseOrder> purchaseOrders = ssccScanAsnDetails.getPurchaseOrders();
    for (PurchaseOrder purchaseOrder : purchaseOrders) {
      deliveryDocument = new DeliveryDocument();
      deliveryDocument.setPurchaseReferenceNumber(purchaseOrder.getPoNumber());
      deliveryDocument.setFinancialReportingGroup(purchaseOrder.getFinancialGroupCode());
      deliveryDocument.setBaseDivisionCode(purchaseOrder.getBaseDivisionCode());
      Vendor vendorInformation = purchaseOrder.getVendorInformation();
      if (Objects.nonNull(vendorInformation)) {
        deliveryDocument.setVendorNumber(
            Objects.nonNull(vendorInformation.getNumber())
                ? vendorInformation.getNumber().toString()
                : null);
        deliveryDocument.setDeptNumber(
            Objects.nonNull(vendorInformation.getDepartment())
                ? vendorInformation.getDepartment().toString()
                : null);
      }
      deliveryDocument.setPurchaseCompanyId(purchaseOrder.getPurchaseCompanyId().toString());
      deliveryDocument.setPurchaseReferenceLegacyType(
          purchaseOrder.getPoType()); // Is this the right mapping ?
      deliveryDocument.setPoTypeCode(Integer.valueOf(purchaseOrder.getPoType()));
      deliveryDocument.setPoDCNumber(purchaseOrder.getPoDcNumber());
      deliveryDocument.setPurchaseReferenceStatus(purchaseOrder.getPoStatus());
      // TODO: totalPurchaseReferenceQty,weight, weightUOM, cubeQty, cubeUOM
      deliveryDocument.setFreightTermCode(purchaseOrder.getFreightTermCode());
      deliveryDocument.setDeliveryStatus(
          DeliveryStatus.valueOf(
              ssccScanAsnDetails.getDelivery().getStatusInformation().getStatus()));
      deliveryDocument.setDeliveryLegacyStatus(
          ssccScanAsnDetails.getDelivery().getStatusInformation().getOperationalStatus());
      deliveryDocument.setTrailerId(ssccScanAsnDetails.getDelivery().getTrailerId());
      // TODO: poTypeCode ?
      deliveryDocument.setTotalBolFbq(purchaseOrder.getFreightBillQty());
      deliveryDocument.setDeliveryNumber(ssccScanAsnDetails.getDelivery().getDeliveryNumber());
      if (ObjectUtils.allNotNull(
          ssccScanAsnDetails.getShipments().get(0),
          ssccScanAsnDetails.getShipments().get(0).getSource())) {
        deliveryDocument.setSourceType(
            ssccScanAsnDetails.getShipments().get(0).getSource().getType());
      }

      try {
        deliveryDocument.setPurchaseReferenceMustArriveByDate(
            new SimpleDateFormat(ReceivingConstants.SIMPLE_DATE)
                .parse(purchaseOrder.getPoDates().getMabd()));
      } catch (ParseException e) {
        throw new ReceivingException(e.getMessage());
      }

      deliveryDocuments.add(deliveryDocument);
    }
    if (!CollectionUtils.isEmpty(deliveryDocuments) && deliveryDocuments.size() > 1) {
      LOGGER.info(
          "More than one delivery document found, so sorting the delivery documents by MABD");
      sortDeliveryDocumentsByMabd(deliveryDocuments);
    }
  }

  private List<DeliveryDocumentLine> createDeliveryDocumentLine(DeliveryDocumentLine line) {
    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    deliveryDocumentLines.add(line);
    return deliveryDocumentLines;
  }

  private void sortDeliveryDocumentsByMabd(List<DeliveryDocument> deliveryDocuments) {
    deliveryDocuments.sort(
        Comparator.comparing(DeliveryDocument::getPurchaseReferenceMustArriveByDate));
  }

  private <T> T getOrDefaultValue(
      Map<String, Object> itemAdditonalInformation, String key, Class<T> type, T defaultValue) {
    try {
      return type.cast(itemAdditonalInformation.getOrDefault(key, defaultValue));
    } catch (Exception e) {
      LOGGER.info("Failed parsing itemAdditionalAttribute key:{}", key, e);
    }
    return defaultValue;
  }
}
