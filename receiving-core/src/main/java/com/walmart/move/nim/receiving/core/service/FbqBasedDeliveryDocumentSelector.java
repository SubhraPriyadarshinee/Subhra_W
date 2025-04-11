package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryEachesResponse;
import com.walmart.move.nim.receiving.core.model.ReceiptsAggregator;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.libs.commons.lang3.tuple.ImmutablePair;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FbqBasedDeliveryDocumentSelector extends DefaultDeliveryDocumentSelector {

  @Override
  public ReceiptsAggregator getReceivedQtyByPoPol(
      List<DeliveryDocument> deliveryDocuments,
      List<String> poNumberList,
      Set<Integer> poLineNumberSet) {
    Long deliveryNumber = deliveryDocuments.get(0).getDeliveryNumber();
    List<ReceiptSummaryEachesResponse> deliveryPoLineReceipts =
        receiptService.receivedQtyByPoAndPoLinesAndDelivery(
            deliveryNumber, poNumberList, poLineNumberSet);
    List<ReceiptSummaryEachesResponse> poLineReceipts =
        receiptService.receivedQtyByPoAndPoLineList(poNumberList, poLineNumberSet);
    ReceiptsAggregator receiptsAggregator =
        ReceiptsAggregator.fromPOLandDPOLReceipts(poLineReceipts, deliveryPoLineReceipts);
    return receiptsAggregator;
  }

  @Override
  public ImmutablePair<Long, Long> getOpenQtyTotalReceivedQtyForLineSelection(
      DeliveryDocument deliveryDocument,
      DeliveryDocumentLine documentLine,
      ReceiptsAggregator receiptsAggregator,
      Boolean includeOverage) {
    // Calculate open qty on line fbq
    Integer maxReceiveQtyFbq = Optional.ofNullable(documentLine.getFreightBillQty()).orElse(0);
    Integer maxReceiveQtyFbqInEA =
        ReceivingUtils.conversionToEaches(
            maxReceiveQtyFbq,
            ReceivingConstants.Uom.VNPK,
            documentLine.getVendorPack(),
            documentLine.getWarehousePack());
    Long receivedQtyOnFbqInEA =
        receiptsAggregator.getByDeliveryPol(
            documentLine.getPurchaseReferenceNumber(),
            documentLine.getPurchaseReferenceLineNumber());
    long openQtyOnFbqInEA = maxReceiveQtyFbqInEA - receivedQtyOnFbqInEA;

    // Calculate open qty on po fbq
    Integer maxReceiveQtyPOL =
        documentLine.getTotalOrderQty()
            + Optional.ofNullable(documentLine.getOverageQtyLimit()).orElse(0);
    Integer maxReceiveQtyPOLInEA =
        ReceivingUtils.conversionToEaches(
            maxReceiveQtyPOL,
            documentLine.getQtyUOM(),
            documentLine.getVendorPack(),
            documentLine.getWarehousePack());
    Long receivedQtyOnPOLInEA =
        receiptsAggregator.getByPoPol(
            documentLine.getPurchaseReferenceNumber(),
            documentLine.getPurchaseReferenceLineNumber());

    long openQtyOnPOLInEA = maxReceiveQtyPOLInEA - receivedQtyOnPOLInEA;
    long effectiveOpenQtyEA = Math.min(openQtyOnFbqInEA, openQtyOnPOLInEA);

    log.info(
        "For Delivery: {} PO: {} POL: {}, "
            + "FBQ (EA): {}, DPOL Receipt (EA): {}, DPOL openQty (EA): {} "
            + "and POL maxReceiveQty (EA): {} POL Receipt (EA): {} POL openQty (EA): {}",
        deliveryDocument.getDeliveryNumber(),
        documentLine.getPurchaseReferenceNumber(),
        documentLine.getPurchaseReferenceLineNumber(),
        maxReceiveQtyFbqInEA,
        receivedQtyOnFbqInEA,
        openQtyOnFbqInEA,
        maxReceiveQtyPOLInEA,
        receivedQtyOnPOLInEA,
        openQtyOnPOLInEA);

    if (effectiveOpenQtyEA == openQtyOnFbqInEA) {
      return ImmutablePair.of(effectiveOpenQtyEA, receivedQtyOnFbqInEA);
    } else {
      return ImmutablePair.of(effectiveOpenQtyEA, receivedQtyOnPOLInEA);
    }
  }
}
