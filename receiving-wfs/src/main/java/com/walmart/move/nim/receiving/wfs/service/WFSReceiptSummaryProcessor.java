package com.walmart.move.nim.receiving.wfs.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.EACHES;

import com.walmart.move.nim.receiving.core.event.processor.summary.DefaultReceiptSummaryProcessor;
import com.walmart.move.nim.receiving.core.model.GdmPOLineResponse;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryQtyByPo;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryQtyByPoLine;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryQtyByPoLineResponse;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryQtyByPoResponse;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryResponse;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryVnpkResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;

public class WFSReceiptSummaryProcessor extends DefaultReceiptSummaryProcessor {
  private static final Logger LOGGER = LoggerFactory.getLogger(WFSReceiptSummaryProcessor.class);

  @Override
  public List<ReceiptSummaryResponse> receivedQtySummaryInVnpkByDelivery(Long deliveryNumber) {
    List<ReceiptSummaryVnpkResponse> receiptSummaryVnpkResponseList = null;
    receiptSummaryVnpkResponseList =
        receiptCustomRepository.receivedQtySummaryInEAByDelivery(deliveryNumber);
    List<ReceiptSummaryResponse> receiptSummaryResponseList = new ArrayList<>();
    if (CollectionUtils.isNotEmpty(receiptSummaryVnpkResponseList)) {
      for (ReceiptSummaryVnpkResponse receiptSummaryVnpkResponse : receiptSummaryVnpkResponseList) {
        receiptSummaryVnpkResponse.setReceivedQty(receiptSummaryVnpkResponse.getReceivedQty());
        receiptSummaryResponseList.add(receiptSummaryVnpkResponse);
      }
    }
    return receiptSummaryResponseList;
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
          (poNumber, receivedQty) ->
              receiptSummaryResponseByPoList.add(
                  new ReceiptSummaryVnpkResponse(poNumber, receivedQty)));
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
      Map<Integer, List<ReceiptSummaryVnpkResponse>> lineToReceiptSummaryMap =
          receiptSummaryVnpkResponseList
              .stream()
              .collect(
                  Collectors.groupingBy(
                      ReceiptSummaryVnpkResponse::getPurchaseReferenceLineNumber));

      lineToReceiptSummaryMap.forEach(
          (key, value) ->
              receiptSummaryResponseList.add(
                  new ReceiptSummaryResponse(
                      purchaseReferenceNumber,
                      key,
                      value.stream().mapToLong(ReceiptSummaryVnpkResponse::getReceivedQty).sum())));
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
    response.setReceivedQtyUom(EACHES);
    return response;
  }

  @Override
  public ReceiptSummaryQtyByPoLineResponse getReceiptsSummaryByPoLineResponse(
      String purchaseReferenceNumber,
      List<ReceiptSummaryQtyByPoLine> receiptSummaryQtyByPoLines,
      Long deliveryNumber,
      HttpHeaders httpHeaders) {
    ReceiptSummaryQtyByPoLineResponse response =
        getReceivedQtyResponseAndSetReceivedQtyAndFbq(receiptSummaryQtyByPoLines);
    response.setSummary(receiptSummaryQtyByPoLines);
    response.setReceivedQtyUom(EACHES);
    response.setPurchaseReferenceNumber(purchaseReferenceNumber);
    return response;
  }
}
