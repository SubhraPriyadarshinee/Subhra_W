package com.walmart.move.nim.receiving.rx.service;

import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryResponse;
import com.walmart.move.nim.receiving.core.model.ShipmentDetails;
import com.walmart.move.nim.receiving.core.repositories.ReceiptCustomRepository;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** @author v0k00fe */
@Component
public class ShipmentSelectorService {
  private static final Logger LOG = LoggerFactory.getLogger(ShipmentSelectorService.class);

  @Autowired private ReceiptCustomRepository receiptCustomRepository;

  public ShipmentDetails autoSelectShipment(
      DeliveryDocumentLine deliveryDocumentLine, Map<String, Long> receivedQtyByShipmentNumber) {
    return compareShipmentQtyAndSelectShipment(deliveryDocumentLine, receivedQtyByShipmentNumber);
  }

  public ShipmentDetails autoSelectShipment(DeliveryDocumentLine deliveryDocumentLine) {
    Map<String, Long> receivedQtyByShipmentNumber =
        receivedQuantityByShipmentPoPoLine(deliveryDocumentLine);
    return compareShipmentQtyAndSelectShipment(deliveryDocumentLine, receivedQtyByShipmentNumber);
  }

  private ShipmentDetails compareShipmentQtyAndSelectShipment(
      DeliveryDocumentLine deliveryDocumentLine, Map<String, Long> receivedQtyByShipmentNumber) {
    List<ShipmentDetailsWithOpenQty> shipmentDetailsWithOpenQtyList = new ArrayList<>();
    for (ShipmentDetails shipmentDetails : deliveryDocumentLine.getShipmentDetailsList()) {
      Long receivedQty =
          receivedQtyByShipmentNumber.getOrDefault(shipmentDetails.getInboundShipmentDocId(), 0l);
      int openQty = shipmentDetails.getShippedQty() - receivedQty.intValue();
      if (openQty > 0) {
        shipmentDetailsWithOpenQtyList.add(
            new ShipmentDetailsWithOpenQty(shipmentDetails, openQty));
      }
    }
    if (CollectionUtils.isEmpty(shipmentDetailsWithOpenQtyList)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.NO_SHIPMENT_AVAILABLE_WITH_OPEN_QTY,
          RxConstants.NO_SHIPMENT_AVAILABLE_WITH_OPEN_QTY);
    }
    Collections.sort(
        shipmentDetailsWithOpenQtyList,
        Comparator.comparingInt(ShipmentDetailsWithOpenQty::getOpenQuantity));
    return shipmentDetailsWithOpenQtyList.get(0).getShipmentDetails();
  }

  public Map<String, Long> receivedQuantityByShipmentPoPoLine(
      DeliveryDocumentLine deliveryDocumentLine) {
    List<ReceiptSummaryResponse> receiptSummaryByShipmentNumber =
        receiptCustomRepository.receivedQtySummaryByShipmentNumberForPoAndPoLine(
            deliveryDocumentLine.getPurchaseReferenceNumber(),
            deliveryDocumentLine.getPurchaseReferenceLineNumber());
    Map<String, Long> receivedQtyByShipmentNumber =
        receiptSummaryByShipmentNumber
            .stream()
            .collect(
                Collectors.toMap(
                    ReceiptSummaryResponse::getInboundShipmentDocId,
                    ReceiptSummaryResponse::getReceivedQty));

    return receivedQtyByShipmentNumber;
  }

  @Data
  private class ShipmentDetailsWithOpenQty {
    private int openQuantity;
    private ShipmentDetails shipmentDetails;

    ShipmentDetailsWithOpenQty(ShipmentDetails shipmentDetails, int openQuantity) {
      this.openQuantity = openQuantity;
      this.shipmentDetails = shipmentDetails;
    }
  }
}
