package com.walmart.move.nim.receiving.core.event.processor.summary;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.*;
import java.util.List;
import org.springframework.http.HttpHeaders;

public interface ReceiptSummaryProcessor {
  List<ReceiptSummaryResponse> receivedQtySummaryInVnpkByDelivery(Long deliveryNumber);

  ReceiptSummaryQtyByPoResponse getReceiptsSummaryByPo(Long deliveryNumber, HttpHeaders httpHeaders)
      throws ReceivingException;

  ReceiptSummaryQtyByPoLineResponse getReceiptsSummaryByPoLine(
      Long deliveryNumber, String purchaseReferenceNumber, HttpHeaders httpHeaders)
      throws ReceivingException;

  List<ReceiptQtySummaryByDeliveryNumberResponse> getReceiptQtySummaryByDeliveries(
      ReceiptSummaryQtyByDeliveries receiptSummaryQtyByDeliveries, HttpHeaders httpHeaders)
      throws ReceivingException;

  List<ReceiptQtySummaryByPoNumbersResponse> getReceiptQtySummaryByPoNumbers(
      ReceiptSummaryQtyByPos receiptSummaryQtyByPoNumbers, HttpHeaders httpHeaders)
      throws ReceivingException;

  List<DeliveryDocument> getStoreDistributionByDeliveryPoPoLine(
      Long deliveryNumber,
      String poNumber,
      int poLineNumber,
      HttpHeaders headers,
      boolean isAtlasItem)
      throws ReceivingException;
}
