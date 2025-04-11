package com.walmart.move.nim.receiving.rc.util;

import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.INVALID_WORKFLOW_REQUEST;
import static com.walmart.move.nim.receiving.rc.contants.RcConstants.PO;
import static com.walmart.move.nim.receiving.rc.contants.RcConstants.RMA;
import static com.walmart.move.nim.receiving.rc.contants.RcConstants.RMAT;
import static com.walmart.move.nim.receiving.rc.contants.RcConstants.SO;
import static com.walmart.move.nim.receiving.rc.contants.RcConstants.SOT;

import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.rc.model.container.RcContainer;
import com.walmart.move.nim.receiving.rc.model.container.RcContainerAdditionalAttributes;
import com.walmart.move.nim.receiving.rc.model.container.RcContainerItem;
import com.walmart.move.nim.receiving.rc.model.gdm.CarrierInformation;
import com.walmart.move.nim.receiving.rc.model.gdm.ReturnOrder;
import com.walmart.move.nim.receiving.rc.model.gdm.ReturnOrderLine;
import com.walmart.move.nim.receiving.rc.model.gdm.SalesOrder;
import com.walmart.move.nim.receiving.rc.model.gdm.SalesOrderLine;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

public class OrderLinesEnrichmentUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(OrderLinesEnrichmentUtil.class);

  /**
   * Enrich event with sales order and return order details based on scanned package type
   *
   * @param salesOrder
   * @param packageBarcodeValue
   * @param packageBarcodeType
   * @param gtin
   * @param rcContainer
   */
  public void enrichEventWithOrderLines(
      SalesOrder salesOrder,
      String packageBarcodeValue,
      String packageBarcodeType,
      String gtin,
      RcContainer rcContainer) {
    RcContainerItem rcContainerItem = rcContainer.getContents().get(0);
    RcContainerAdditionalAttributes rcContainerAdditionalAttributes =
        rcContainerItem.getAdditionalAttributes();

    Map<Integer, SalesOrderLine> salesOrderLineMap = new LinkedHashMap<>();
    salesOrder
        .getLines()
        .stream()
        .filter(Objects::nonNull)
        .forEach(
            salesOrderLine ->
                salesOrderLineMap.put(salesOrderLine.getLineNumber(), salesOrderLine));

    rcContainerItem.setSalesOrderNumber(salesOrder.getSoNumber());

    switch (packageBarcodeType) {
      case RMA:
        enrichOrderLinesForRMA(
            salesOrder,
            salesOrderLineMap,
            packageBarcodeValue,
            gtin,
            rcContainerItem,
            rcContainerAdditionalAttributes);
        break;

      case RMAT:
        enrichOrderLinesForRMAT(
            salesOrder,
            salesOrderLineMap,
            packageBarcodeValue,
            gtin,
            rcContainerItem,
            rcContainerAdditionalAttributes);
        break;

      case SO:
        enrichOrderLinesForSO(
            salesOrder, salesOrderLineMap, gtin, rcContainerItem, rcContainerAdditionalAttributes);
        break;

      case SOT:
        enrichOrderLinesForSOT(
            salesOrder,
            salesOrderLineMap,
            packageBarcodeValue,
            gtin,
            rcContainerItem,
            rcContainerAdditionalAttributes);
        break;

      case PO:
        enrichOrderLinesForPO(
            salesOrder,
            salesOrderLineMap,
            packageBarcodeValue,
            gtin,
            rcContainerItem,
            rcContainerAdditionalAttributes);
        break;

      default:
        LOGGER.warn(
            "Scanned label type is not RMA/RMAT/SO/SOT/PO. [packageBarcodeType={}]",
            packageBarcodeType);
        enrichOrderLinesForSO(
            salesOrder, salesOrderLineMap, gtin, rcContainerItem, rcContainerAdditionalAttributes);
        break;
    }

    // if GTIN was not found in sales order, throw error
    if (Objects.isNull(rcContainerItem.getSalesOrderLineNumber())
        || Objects.isNull(rcContainerItem.getSalesOrderNumber())) {
      LOGGER.error(String.format("Invalid sales order details for GTIN=%s", gtin));
      throw new ReceivingBadDataException(
          INVALID_WORKFLOW_REQUEST, String.format("Invalid sales order details for GTIN=%s", gtin));
    }
  }

  /** When RMA is scanned, look for the GTIN line in return order lines of that particular RMA */
  private void enrichOrderLinesForRMA(
      SalesOrder salesOrder,
      Map<Integer, SalesOrderLine> salesOrderLineMap,
      String packageBarcodeValue,
      String gtin,
      RcContainerItem rcContainerItem,
      RcContainerAdditionalAttributes rcContainerAdditionalAttributes) {
    if (Objects.nonNull(salesOrder.getReturnOrders())) {
      for (ReturnOrder returnOrder : salesOrder.getReturnOrders()) {
        if (Objects.nonNull(returnOrder)
            && !StringUtils.isEmpty(returnOrder.getRoNumber())
            && packageBarcodeValue.equals(returnOrder.getRoNumber())
            && !CollectionUtils.isEmpty(returnOrder.getLines())) {
          for (ReturnOrderLine returnOrderLine : returnOrder.getLines()) {
            if (Objects.nonNull(returnOrderLine)) {
              SalesOrderLine salesOrderLine =
                  salesOrderLineMap.get(returnOrderLine.getSoLineNumber());
              if (Objects.nonNull(salesOrderLine)
                  && Objects.nonNull(salesOrderLine.getItemDetails())
                  && gtin.equals(salesOrderLine.getItemDetails().getConsumableGTIN())) {
                rcContainerItem.setSalesOrderLineNumber(salesOrderLine.getLineNumber());
                rcContainerAdditionalAttributes.setReturnOrderNumber(returnOrder.getRoNumber());
                rcContainerAdditionalAttributes.setReturnOrderLineNumber(
                    returnOrderLine.getLineNumber());
                rcContainerAdditionalAttributes.setTrackingNumber(
                    Optional.ofNullable(returnOrderLine.getCarrierInformation())
                        .map(CarrierInformation::getTrackingNumber)
                        .orElse(null));
                return;
              }
            }
          }
        }
      }
    }
  }

  /**
   * When RMAT is scanned, look for the GTIN line in return order lines with that particular RMAT
   */
  private void enrichOrderLinesForRMAT(
      SalesOrder salesOrder,
      Map<Integer, SalesOrderLine> salesOrderLineMap,
      String packageBarcodeValue,
      String gtin,
      RcContainerItem rcContainerItem,
      RcContainerAdditionalAttributes rcContainerAdditionalAttributes) {
    if (Objects.nonNull(salesOrder.getReturnOrders())) {
      for (ReturnOrder returnOrder : salesOrder.getReturnOrders()) {
        if (Objects.nonNull(returnOrder) && !CollectionUtils.isEmpty(returnOrder.getLines())) {
          for (ReturnOrderLine returnOrderLine : returnOrder.getLines()) {
            if (Objects.nonNull(returnOrderLine)
                && Objects.nonNull(returnOrderLine.getCarrierInformation())
                && !StringUtils.isEmpty(returnOrderLine.getCarrierInformation().getTrackingNumber())
                && packageBarcodeValue.equals(
                    returnOrderLine.getCarrierInformation().getTrackingNumber())) {
              SalesOrderLine salesOrderLine =
                  salesOrderLineMap.get(returnOrderLine.getSoLineNumber());
              if (Objects.nonNull(salesOrderLine)
                  && Objects.nonNull(salesOrderLine.getItemDetails())
                  && gtin.equals(salesOrderLine.getItemDetails().getConsumableGTIN())) {
                rcContainerItem.setSalesOrderLineNumber(salesOrderLine.getLineNumber());
                rcContainerAdditionalAttributes.setReturnOrderNumber(returnOrder.getRoNumber());
                rcContainerAdditionalAttributes.setReturnOrderLineNumber(
                    returnOrderLine.getLineNumber());
                rcContainerAdditionalAttributes.setTrackingNumber(
                    Optional.ofNullable(returnOrderLine.getCarrierInformation())
                        .map(CarrierInformation::getTrackingNumber)
                        .orElse(null));
                return;
              }
            }
          }
        }
      }
    }
  }

  /** When SO is scanned, look for the GTIN line in all sales order lines */
  private void enrichOrderLinesForSO(
      SalesOrder salesOrder,
      Map<Integer, SalesOrderLine> salesOrderLineMap,
      String gtin,
      RcContainerItem rcContainerItem,
      RcContainerAdditionalAttributes rcContainerAdditionalAttributes) {
    for (Map.Entry<Integer, SalesOrderLine> salesOrderLineEntry : salesOrderLineMap.entrySet()) {
      SalesOrderLine salesOrderLine = salesOrderLineEntry.getValue();
      if (Objects.nonNull(salesOrderLine.getItemDetails())
          && gtin.equals(salesOrderLine.getItemDetails().getConsumableGTIN())) {
        Integer salesOrderLineNumber = salesOrderLine.getLineNumber();
        rcContainerItem.setSalesOrderLineNumber(salesOrderLineNumber);
        enrichReturnOrderLines(salesOrder, salesOrderLineNumber, rcContainerAdditionalAttributes);
        return;
      }
    }
  }

  /**
   * When SOT is scanned, look for the GTIN line in sales order lines matching that particular SOT
   */
  private void enrichOrderLinesForSOT(
      SalesOrder salesOrder,
      Map<Integer, SalesOrderLine> salesOrderLineMap,
      String packageBarcodeValue,
      String gtin,
      RcContainerItem rcContainerItem,
      RcContainerAdditionalAttributes rcContainerAdditionalAttributes) {
    for (Map.Entry<Integer, SalesOrderLine> salesOrderLineEntry : salesOrderLineMap.entrySet()) {
      SalesOrderLine salesOrderLine = salesOrderLineEntry.getValue();
      if (Objects.nonNull(salesOrderLine.getItemDetails())
          && !StringUtils.isEmpty(salesOrderLine.getTrackingNumber())
          && packageBarcodeValue.equals(salesOrderLine.getTrackingNumber())
          && gtin.equals(salesOrderLine.getItemDetails().getConsumableGTIN())) {
        Integer salesOrderLineNumber = salesOrderLine.getLineNumber();
        rcContainerItem.setSalesOrderLineNumber(salesOrderLineNumber);
        enrichReturnOrderLines(salesOrder, salesOrderLineNumber, rcContainerAdditionalAttributes);
        return;
      }
    }
  }

  /** When PO is scanned, look for the GTIN line in sales order lines matching that particular PO */
  private void enrichOrderLinesForPO(
      SalesOrder salesOrder,
      Map<Integer, SalesOrderLine> salesOrderLineMap,
      String packageBarcodeValue,
      String gtin,
      RcContainerItem rcContainerItem,
      RcContainerAdditionalAttributes rcContainerAdditionalAttributes) {
    for (Map.Entry<Integer, SalesOrderLine> salesOrderLineEntry : salesOrderLineMap.entrySet()) {
      SalesOrderLine salesOrderLine = salesOrderLineEntry.getValue();
      if (Objects.nonNull(salesOrderLine)
          && Objects.nonNull(salesOrderLine.getItemDetails())
          && !StringUtils.isEmpty(salesOrderLine.getPoNumber())
          && packageBarcodeValue.equals(salesOrderLine.getPoNumber())
          && gtin.equals(salesOrderLine.getItemDetails().getConsumableGTIN())) {
        Integer salesOrderLineNumber = salesOrderLine.getLineNumber();
        rcContainerItem.setSalesOrderLineNumber(salesOrderLineNumber);
        enrichReturnOrderLines(salesOrder, salesOrderLineNumber, rcContainerAdditionalAttributes);
        return;
      }
    }
  }

  /**
   * Enrich return order lines when SO/SOT/PO is scanned by finding the corresponding return order
   * line
   */
  private void enrichReturnOrderLines(
      SalesOrder salesOrder,
      Integer salesOrderLineNumber,
      RcContainerAdditionalAttributes rcContainerAdditionalAttributes) {
    if (Objects.nonNull(salesOrder.getReturnOrders())) {
      for (ReturnOrder returnOrder : salesOrder.getReturnOrders()) {
        if (Objects.nonNull(returnOrder) && !CollectionUtils.isEmpty(returnOrder.getLines())) {
          for (ReturnOrderLine returnOrderLine : returnOrder.getLines()) {
            if (Objects.nonNull(returnOrderLine)
                && !StringUtils.isEmpty(returnOrderLine.getSoLineNumber())
                && returnOrderLine.getSoLineNumber().equals(salesOrderLineNumber)) {
              rcContainerAdditionalAttributes.setReturnOrderNumber(returnOrder.getRoNumber());
              rcContainerAdditionalAttributes.setReturnOrderLineNumber(
                  returnOrderLine.getLineNumber());
              rcContainerAdditionalAttributes.setTrackingNumber(
                  Optional.ofNullable(returnOrderLine.getCarrierInformation())
                      .map(CarrierInformation::getTrackingNumber)
                      .orElse(null));
              return;
            }
          }
        }
      }
    }
  }
}
