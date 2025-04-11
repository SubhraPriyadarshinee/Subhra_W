package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DefaultOpenQtyCalculator implements OpenQtyCalculator {
  @Autowired private ReceiptService receiptService;
  @Autowired protected TenantSpecificConfigReader configUtils;

  @Override
  public OpenQtyResult calculate(
      Long deliveryNumber, DeliveryDocument deliveryDocument, DeliveryDocumentLine documentLine) {
    // TODO: use a flag for this, to include allowed overage
    Integer maxReceiveQtyPOL = documentLine.getTotalOrderQty();

    Long receivedQtyOnPOL =
        receiptService.getReceivedQtyByPoAndPoLine(
            documentLine.getPurchaseReferenceNumber(),
            documentLine.getPurchaseReferenceLineNumber());

    long openQtyOnPOL = maxReceiveQtyPOL - receivedQtyOnPOL;
    if (openQtyOnPOL <= 0) {
      maxReceiveQtyPOL =
          documentLine.getTotalOrderQty()
              + Optional.ofNullable(documentLine.getOverageQtyLimit()).orElse(0);
    }
    log.info(
        "For Delivery: {} PO: {} POL: {}, "
            + " POL maxReceiveQty : {} POL Receipt : {} POL openQty : {}",
        deliveryNumber,
        documentLine.getPurchaseReferenceNumber(),
        documentLine.getPurchaseReferenceLineNumber(),
        maxReceiveQtyPOL,
        receivedQtyOnPOL,
        openQtyOnPOL);

    return OpenQtyResult.builder()
        .openQty(openQtyOnPOL)
        .totalReceivedQty(Math.toIntExact(receivedQtyOnPOL))
        .maxReceiveQty(Long.valueOf(maxReceiveQtyPOL))
        .flowType(OpenQtyFlowType.POLINE)
        .build();
  }

  public OpenQtyResult calculate(
      String problemTagId,
      Long deliveryNumber,
      DeliveryDocument deliveryDocument,
      DeliveryDocumentLine documentLine)
      throws ReceivingException {
    FitProblemTagResponse fitProblemTagResponse =
        configUtils
            .getConfiguredInstance(
                TenantContext.getFacilityNum().toString(),
                ReceivingConstants.PROBLEM_SERVICE,
                ProblemService.class)
            .getProblemDetails(problemTagId);
    Long totalReceivedQtyOnPtag = receiptService.getReceivedQtyByProblemId(problemTagId);
    Integer maxReceivedQtyOnPtag =
        Optional.ofNullable(fitProblemTagResponse.getReportedQty()).orElse(0);
    int openQtyOnPtag = maxReceivedQtyOnPtag - Math.toIntExact(totalReceivedQtyOnPtag);
    log.info(
        "For ProblemTag: {} maxReceiveQty : {},  Receipt : {}, openQty : {}",
        problemTagId,
        maxReceivedQtyOnPtag,
        totalReceivedQtyOnPtag,
        openQtyOnPtag);
    return OpenQtyResult.builder()
        .openQty((long) openQtyOnPtag)
        .totalReceivedQty(Math.toIntExact(totalReceivedQtyOnPtag))
        .maxReceiveQty(Long.valueOf(maxReceivedQtyOnPtag))
        .flowType(OpenQtyFlowType.PTAG)
        .build();
  }
}
