package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.OpenQtyResult;

public interface OpenQtyCalculator {
  OpenQtyResult calculate(
      Long deliveryNumber, DeliveryDocument deliveryDocument, DeliveryDocumentLine documentLine);

  OpenQtyResult calculate(
      String problemId,
      Long deliveryNumber,
      DeliveryDocument deliveryDocument,
      DeliveryDocumentLine documentLine)
      throws ReceivingException;
}
