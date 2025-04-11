package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.OpenQtyFlowType;
import com.walmart.move.nim.receiving.core.model.OpenQtyResult;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FbqAndPolBasedOpenQtyCalculator extends DefaultOpenQtyCalculator {
  @Autowired ReceiptService receiptService;

  @Override
  public OpenQtyResult calculate(
      Long deliveryNumber, DeliveryDocument deliveryDocument, DeliveryDocumentLine documentLine) {
    // Check if current line is import po line
    // if yes, ONLY then use fbq based calculator
    // else use normal po line calculator
    // TODO see if we need to add check here (feature flag on this)
    if (!Boolean.TRUE.equals(deliveryDocument.getImportInd())) {
      // In case of non import freight and this bean is configured
      // use parent class (po line based calculator)
      return super.calculate(deliveryNumber, deliveryDocument, documentLine);
    }

    Integer maxReceiveQtyFbq = Optional.ofNullable(documentLine.getFreightBillQty()).orElse(0);
    Long receivedQtyOnFbq =
        receiptService.receivedQtyByDeliveryPoAndPoLine(
            deliveryNumber,
            documentLine.getPurchaseReferenceNumber(),
            documentLine.getPurchaseReferenceLineNumber());

    // get max receive from pol level
    // TODO: use a flag for this, to include allowed overage
    Integer maxReceiveQtyPOL =
        documentLine.getTotalOrderQty()
            + Optional.ofNullable(documentLine.getOverageQtyLimit()).orElse(0);

    Long receivedQtyOnPOL =
        receiptService.getReceivedQtyByPoAndPoLine(
            documentLine.getPurchaseReferenceNumber(),
            documentLine.getPurchaseReferenceLineNumber());

    long openQtyOnPOL = maxReceiveQtyPOL - receivedQtyOnPOL;
    long openQtyOnFBQ = maxReceiveQtyFbq - receivedQtyOnFbq;
    long effectiveOpenQty = Math.min(openQtyOnPOL, openQtyOnFBQ);

    log.info(
        "For Delivery: {} PO: {} POL: {}, "
            + "FBQ: {}, DPOL Receipt: {}, DPOL openQty: {} "
            + "and POL maxReceiveQty: {} POL Receipt: {} POL openQty: {}",
        deliveryNumber,
        documentLine.getPurchaseReferenceNumber(),
        documentLine.getPurchaseReferenceLineNumber(),
        maxReceiveQtyFbq,
        receivedQtyOnFbq,
        openQtyOnFBQ,
        maxReceiveQtyPOL,
        receivedQtyOnPOL,
        openQtyOnPOL);

    OpenQtyResult openQtyResult;
    if (effectiveOpenQty == openQtyOnPOL) {
      openQtyResult =
          OpenQtyResult.builder()
              .openQty(openQtyOnPOL)
              .totalReceivedQty(Math.toIntExact(receivedQtyOnPOL))
              .maxReceiveQty(Long.valueOf(maxReceiveQtyPOL))
              .flowType(OpenQtyFlowType.POLINE)
              .build();
    } else {
      openQtyResult =
          OpenQtyResult.builder()
              .openQty(openQtyOnFBQ)
              .totalReceivedQty(Math.toIntExact(receivedQtyOnFbq))
              .maxReceiveQty(Long.valueOf(maxReceiveQtyFbq))
              .flowType(OpenQtyFlowType.FBQ)
              .build();
    }
    log.info("fbq pol based calculator: openQtyResult: {}", openQtyResult);
    return openQtyResult;
  }
}
