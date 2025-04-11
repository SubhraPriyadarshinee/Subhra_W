package com.walmart.move.nim.receiving.rx.service;

import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.event.processor.summary.DefaultReceiptSummaryProcessor;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.apache.commons.collections4.CollectionUtils;

public class RxReceiptSummaryProcessor extends DefaultReceiptSummaryProcessor {

  @Override
  public List<ReceiptSummaryResponse> receivedQtySummaryInVnpkByDelivery(Long deliveryNumber) {
    List<ReceiptSummaryVnpkResponse> receiptSummaryVnpkResponseList = null;
    receiptSummaryVnpkResponseList =
        receiptCustomRepository.receivedQtySummaryInEAByDelivery(deliveryNumber);
    List<ReceiptSummaryResponse> receiptSummaryResponseList = new ArrayList<>();
    if (CollectionUtils.isNotEmpty(receiptSummaryVnpkResponseList)) {
      for (ReceiptSummaryVnpkResponse receiptSummaryVnpkResponse : receiptSummaryVnpkResponseList) {
        receiptSummaryVnpkResponse.setReceivedQty(
            ReceivingUtils.conversionToVendorPackRoundUp(
                    receiptSummaryVnpkResponse.getReceivedQty().intValue(),
                    ReceivingConstants.Uom.EACHES,
                    receiptSummaryVnpkResponse.getVnpkQty(),
                    receiptSummaryVnpkResponse.getWhpkQty())
                .longValue());
        receiptSummaryResponseList.add(receiptSummaryVnpkResponse);
      }
    }
    return receiptSummaryResponseList;
  }

  @Override
  public ReceiptSummaryQtyByPoResponse getReceiptsSummaryByPoResponse(
      Long deliveryNumber,
      GdmPOLineResponse gdmPOLineResponse,
      List<ReceiptSummaryQtyByPo> receiptSummaryQtyByPos) {
    ReceiptSummaryQtyByPoResponse response =
        super.getReceiptsSummaryByPoResponse(
            deliveryNumber, gdmPOLineResponse, receiptSummaryQtyByPos);
    return populateAsnInfo(gdmPOLineResponse, response);
  }

  @Override
  public List<ReceiptSummaryResponse> getReceivedQtyByPo(Long deliveryNumber) {
    List<ReceiptSummaryResponse> receiptSummaryResponseByPoPoLine =
        receivedQtySummaryInVnpkByDelivery(deliveryNumber);
    List<ReceiptSummaryResponse> receiptSummaryResponseByPoList = new ArrayList<>();

    if (!CollectionUtils.isEmpty(receiptSummaryResponseByPoPoLine)) {
      Map<String, Long> receiptSummaryQtyByPoMap = new HashMap<>();
      for (ReceiptSummaryResponse receiptSummaryResponse : receiptSummaryResponseByPoPoLine) {
        if (receiptSummaryQtyByPoMap.containsKey(
            receiptSummaryResponse.getPurchaseReferenceNumber())) {
          receiptSummaryQtyByPoMap.computeIfPresent(
              receiptSummaryResponse.getPurchaseReferenceNumber(),
              (key, value) -> value + receiptSummaryResponse.getReceivedQty());
        } else {
          receiptSummaryQtyByPoMap.put(
              receiptSummaryResponse.getPurchaseReferenceNumber(),
              receiptSummaryResponse.getReceivedQty());
        }
      }

      receiptSummaryQtyByPoMap.forEach(
          (poNumber, receivedQty) -> {
            receiptSummaryResponseByPoList.add(
                new ReceiptSummaryVnpkResponse(poNumber, receivedQty));
          });
    }
    return receiptSummaryResponseByPoList;
  }

  @Override
  public List<ReceiptSummaryResponse> getReceivedQtyByPoLine(
      Long deliveryNumber, String purchaseReferenceNumber) {
    List<ReceiptSummaryVnpkResponse> receiptSummaryVnpkResponseList =
        receiptService.getReceivedQtySummaryByPoLineInEaches(
            deliveryNumber, purchaseReferenceNumber);
    List<ReceiptSummaryResponse> receiptSummaryResponseList = new ArrayList<>();
    if (CollectionUtils.isNotEmpty(receiptSummaryVnpkResponseList)) {
      for (ReceiptSummaryVnpkResponse receiptSummaryVnpkResponse : receiptSummaryVnpkResponseList) {
        receiptSummaryVnpkResponse.setReceivedQty(
            ReceivingUtils.conversionToVendorPackRoundUp(
                    receiptSummaryVnpkResponse.getReceivedQty().intValue(),
                    ReceivingConstants.Uom.EACHES,
                    receiptSummaryVnpkResponse.getVnpkQty(),
                    receiptSummaryVnpkResponse.getWhpkQty())
                .longValue());
        receiptSummaryResponseList.add(receiptSummaryVnpkResponse);
      }
    }
    return receiptSummaryResponseList;
  }
}
