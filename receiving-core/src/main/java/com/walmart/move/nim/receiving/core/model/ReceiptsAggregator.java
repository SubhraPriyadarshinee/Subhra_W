/** */
package com.walmart.move.nim.receiving.core.model;

import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ToString
public class ReceiptsAggregator {
  private final Map<String, Long> acrossDeliveriesByPoPol;
  private final Map<String, Long> singleDeliveryByPoPol;

  public static ReceiptsAggregator empty() {
    return new ReceiptsAggregator(new HashMap<>(), new HashMap<>());
  }

  private ReceiptsAggregator(
      Map<String, Long> acrossDeliveriesByPoPol, Map<String, Long> singleDeliveryByPoPol) {
    this.acrossDeliveriesByPoPol = acrossDeliveriesByPoPol;
    this.singleDeliveryByPoPol = singleDeliveryByPoPol;
  }

  /**
   * This takes PO Line Receipts in EA and stores in map
   *
   * @param poLineReceipts
   * @return
   */
  public static ReceiptsAggregator fromPOLReceipts(
      List<ReceiptSummaryEachesResponse> poLineReceipts) {
    Map<String, Long> acrossDeliveriesByPoPol = processReceiptsResponse(poLineReceipts);
    Map<String, Long> singleDeliveryByPoPol = new HashMap<>();
    return new ReceiptsAggregator(acrossDeliveriesByPoPol, singleDeliveryByPoPol);
  }

  /**
   * This takes Pol and Del + POL receipts in EA and stores in maps
   *
   * @param poLineReceipts
   * @param deliveryPoLineReceipts
   * @return
   */
  public static ReceiptsAggregator fromPOLandDPOLReceipts(
      List<ReceiptSummaryEachesResponse> poLineReceipts,
      List<ReceiptSummaryEachesResponse> deliveryPoLineReceipts) {
    Map<String, Long> acrossDeliveriesByPoPol = processReceiptsResponse(poLineReceipts);
    Map<String, Long> singleDeliveryByPoPol = processReceiptsResponse(deliveryPoLineReceipts);
    return new ReceiptsAggregator(acrossDeliveriesByPoPol, singleDeliveryByPoPol);
  }

  private static Map<String, Long> processReceiptsResponse(
      List<ReceiptSummaryEachesResponse> poLineReceipts) {
    Map<String, Long> receivedQtyMap = new HashMap<>();
    for (ReceiptSummaryEachesResponse receiptSummary : poLineReceipts) {
      String key =
          getPoPoLineKey(
              receiptSummary.getPurchaseReferenceNumber(),
              receiptSummary.getPurchaseReferenceLineNumber());
      receivedQtyMap.put(key, receiptSummary.getReceivedQty());
    }
    return receivedQtyMap;
  }

  private static String getPoPoLineKey(
      String purchaseReferenceNumber, Integer purchaseReferenceLineNumber) {
    String key =
        purchaseReferenceNumber + ReceivingConstants.DELIM_DASH + purchaseReferenceLineNumber;
    return key;
  }

  /**
   * Returns EA received qty for current receipts for single (current delivery docs) delivery
   *
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @return
   */
  public Long getByDeliveryPol(
      String purchaseReferenceNumber, Integer purchaseReferenceLineNumber) {
    return singleDeliveryByPoPol.getOrDefault(
        getPoPoLineKey(purchaseReferenceNumber, purchaseReferenceLineNumber), 0L);
  }

  /**
   * Returns ZA received qty for current receipts for single (current) delivery
   *
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @param vnpkQty
   * @param whpkQty
   * @return
   */
  public Long getByDeliveryPoLineInZA(
      String purchaseReferenceNumber,
      Integer purchaseReferenceLineNumber,
      Integer vnpkQty,
      Integer whpkQty) {
    Long receivedQtyInEA =
        singleDeliveryByPoPol.getOrDefault(
            getPoPoLineKey(purchaseReferenceNumber, purchaseReferenceLineNumber), 0L);
    return Long.valueOf(
        ReceivingUtils.conversionToVendorPack(
            Math.toIntExact(receivedQtyInEA), ReceivingConstants.Uom.EACHES, vnpkQty, whpkQty));
  }

  /**
   * Returns EA received qty for current receipts for across delivery
   *
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @return
   */
  public Long getByPoPol(String purchaseReferenceNumber, Integer purchaseReferenceLineNumber) {
    return acrossDeliveriesByPoPol.getOrDefault(
        getPoPoLineKey(purchaseReferenceNumber, purchaseReferenceLineNumber), 0L);
  }

  /**
   * Returns ZA received qty for current receipts across delivery
   *
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @param vnpkQty
   * @param whpkQty
   * @return
   */
  public Long getByPoPolInZA(
      String purchaseReferenceNumber,
      Integer purchaseReferenceLineNumber,
      Integer vnpkQty,
      Integer whpkQty) {
    Long receivedQtyInEA =
        acrossDeliveriesByPoPol.getOrDefault(
            getPoPoLineKey(purchaseReferenceNumber, purchaseReferenceLineNumber), 0L);
    return Long.valueOf(
        ReceivingUtils.conversionToVendorPack(
            Math.toIntExact(receivedQtyInEA), ReceivingConstants.Uom.EACHES, vnpkQty, whpkQty));
  }
}
