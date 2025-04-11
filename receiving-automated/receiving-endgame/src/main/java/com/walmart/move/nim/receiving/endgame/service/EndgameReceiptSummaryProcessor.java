package com.walmart.move.nim.receiving.endgame.service;

import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.event.processor.summary.DefaultReceiptSummaryProcessor;
import com.walmart.move.nim.receiving.core.event.processor.summary.ReceiptSummaryProcessor;
import com.walmart.move.nim.receiving.core.model.POPOLineKey;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryResponse;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryVnpkResponse;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

public class EndgameReceiptSummaryProcessor extends DefaultReceiptSummaryProcessor
    implements ReceiptSummaryProcessor {

  @Override
  public List<ReceiptSummaryResponse> receivedQtySummaryInVnpkByDelivery(Long deliveryNumber) {

    // For OSDR Qty and QtyUOM is null
    List<ReceiptSummaryVnpkResponse> receiptSummaryQtyByPoAndPoLineResponses =
        receiptCustomRepository.receivedQtySummaryByDelivery(deliveryNumber);

    Map<POPOLineKey, ReceiptSummaryVnpkResponse> responseByPOPOLInEach = new HashMap<>();

    for (ReceiptSummaryVnpkResponse receiptSummaryVnpkResponse :
        receiptSummaryQtyByPoAndPoLineResponses) {

      POPOLineKey popoLineKey =
          new POPOLineKey(
              receiptSummaryVnpkResponse.getPurchaseReferenceNumber(),
              receiptSummaryVnpkResponse.getPurchaseReferenceLineNumber());

      Long receivedQty =
          !StringUtils.equalsIgnoreCase(
                  receiptSummaryVnpkResponse.getQtyUOM(), ReceivingConstants.Uom.EACHES)
              ? Long.valueOf(
                  ReceivingUtils.conversionToEaches(
                      receiptSummaryVnpkResponse.getReceivedQty().intValue(),
                      receiptSummaryVnpkResponse.getQtyUOM(),
                      receiptSummaryVnpkResponse.getVnpkQty(),
                      receiptSummaryVnpkResponse.getWhpkQty()))
              : receiptSummaryVnpkResponse.getReceivedQty();
      if (!responseByPOPOLInEach.containsKey(popoLineKey)) {

        receiptSummaryVnpkResponse.setReceivedQty(receivedQty);
        receiptSummaryVnpkResponse.setQtyUOM(ReceivingConstants.Uom.EACHES);
        responseByPOPOLInEach.put(popoLineKey, receiptSummaryVnpkResponse);
        continue;
      }

      ReceiptSummaryVnpkResponse _receiptSummaryVnpkResponse =
          responseByPOPOLInEach.get(popoLineKey);

      _receiptSummaryVnpkResponse.setReceivedQty(
          receivedQty + _receiptSummaryVnpkResponse.getReceivedQty());
      _receiptSummaryVnpkResponse.setQtyUOM(ReceivingConstants.Uom.EACHES);
      responseByPOPOLInEach.put(popoLineKey, _receiptSummaryVnpkResponse);
    }

    List<ReceiptSummaryResponse> receiptSummaryResponses = new ArrayList<>();
    responseByPOPOLInEach
        .values()
        .stream()
        .forEach(
            receiptSummary -> {
              // whpk is not handle here as , we wont receive anything on whpk
              Long qty =
                  StringUtils.equalsIgnoreCase(
                          receiptSummary.getQtyUOM(), ReceivingConstants.Uom.EACHES)
                      ? ReceivingUtils.calculateUOMSpecificQuantity(
                              receiptSummary.getReceivedQty().intValue(),
                              ReceivingConstants.Uom.VNPK,
                              receiptSummary.getVnpkQty(),
                              receiptSummary.getWhpkQty())
                          .longValue()
                      : StringUtils.equalsIgnoreCase(
                              receiptSummary.getQtyUOM(), ReceivingConstants.Uom.VNPK)
                          ? receiptSummary.getReceivedQty()
                          : 0L;

              ReceiptSummaryResponse receiptSummaryResponse =
                  new ReceiptSummaryVnpkResponse(
                      receiptSummary.getPurchaseReferenceNumber(),
                      receiptSummary.getPurchaseReferenceLineNumber(),
                      qty);
              receiptSummaryResponses.add(receiptSummaryResponse);
            });

    return receiptSummaryResponses;
  }
}
