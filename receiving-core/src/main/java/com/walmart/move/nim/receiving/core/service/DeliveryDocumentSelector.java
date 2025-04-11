package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.ReceiptsAggregator;
import io.strati.libs.commons.lang3.tuple.ImmutablePair;
import java.util.List;
import java.util.Set;

public interface DeliveryDocumentSelector {

  /**
   * Auto select the deliveryDocumentLine
   *
   * @param deliveryDocuments
   * @return Pair<DeliveryDocument, DeliveryDocumentLine>
   */
  Pair<DeliveryDocument, DeliveryDocumentLine> autoSelectDeliveryDocumentLine(
      List<DeliveryDocument> deliveryDocuments);

  ReceiptsAggregator getReceivedQtyByPoPol(
      List<DeliveryDocument> deliveryDocuments,
      List<String> poNumberList,
      Set<Integer> poLineNumberSet);

  ImmutablePair<Long, Long> getOpenQtyTotalReceivedQtyForLineSelection(
      DeliveryDocument deliveryDocument,
      DeliveryDocumentLine documentLine,
      ReceiptsAggregator receiptsAggregator,
      Boolean includeOverage);
}
