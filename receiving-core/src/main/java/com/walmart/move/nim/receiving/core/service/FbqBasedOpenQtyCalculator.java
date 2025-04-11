package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.model.*;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FbqBasedOpenQtyCalculator extends DefaultOpenQtyCalculator {
  @Autowired ReceiptService receiptService;
  @Autowired protected TenantSpecificConfigReader tenantSpecificConfigReader;

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

    long openQtyOnFBQ = maxReceiveQtyFbq - receivedQtyOnFbq;

    log.info(
        "For Delivery: {} PO: {} POL: {}, " + "FBQ: {}, DPOL Receipt: {}, DPOL openQty: {} ",
        deliveryNumber,
        documentLine.getPurchaseReferenceNumber(),
        documentLine.getPurchaseReferenceLineNumber(),
        maxReceiveQtyFbq,
        receivedQtyOnFbq,
        openQtyOnFBQ);

    return OpenQtyResult.builder()
        .openQty(openQtyOnFBQ)
        .totalReceivedQty(Math.toIntExact(receivedQtyOnFbq))
        .maxReceiveQty(Long.valueOf(maxReceiveQtyFbq))
        .flowType(OpenQtyFlowType.FBQ)
        .build();
  }

  public OpenQtyResult calculate(
      String problemTagId,
      Long deliveryNumber,
      DeliveryDocument deliveryDocument,
      DeliveryDocumentLine documentLine)
      throws ReceivingException {

    OpenQtyResult openQtyResultOnPtag =
        super.calculate(problemTagId, deliveryNumber, deliveryDocument, documentLine);

    if (!Boolean.TRUE.equals(deliveryDocument.getImportInd())) {
      // In case of non import freight and this bean is configured
      // use parent class (po line based calculator)
      return openQtyResultOnPtag;
    }

    long maxReceiveQtyOnPtag = openQtyResultOnPtag.getMaxReceiveQty();
    long receivedQtyOnPtag = openQtyResultOnPtag.getTotalReceivedQty();
    long openQtyOnPtag = openQtyResultOnPtag.getOpenQty();

    log.info(
        "For ProblemTag: {} maxReceiveQty : {},  Receipt : {}, openQty : {}",
        problemTagId,
        maxReceiveQtyOnPtag,
        receivedQtyOnPtag,
        openQtyOnPtag);

    OpenQtyResult openQtyResultOnPoLineByFBQ =
        calculate(deliveryNumber, deliveryDocument, documentLine);

    long resultantOpenQtyOnPtag = Math.min(openQtyOnPtag, openQtyResultOnPoLineByFBQ.getOpenQty());
    long resultantMaxReceiveQtyOnPtag = receivedQtyOnPtag + resultantOpenQtyOnPtag;

    log.info(
        "For ProblemTag: {} effective maxReceiveQty : {},  Receipt : {}, effective openQty : {}",
        problemTagId,
        maxReceiveQtyOnPtag,
        receivedQtyOnPtag,
        openQtyOnPtag);

    return OpenQtyResult.builder()
        .openQty(resultantOpenQtyOnPtag)
        .totalReceivedQty(Math.toIntExact(receivedQtyOnPtag))
        .maxReceiveQty(resultantMaxReceiveQtyOnPtag)
        .flowType(OpenQtyFlowType.PTAG)
        .build();
  }
}
