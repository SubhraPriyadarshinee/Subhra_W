package com.walmart.move.nim.receiving.core.client.nimrds;

import com.walmart.move.nim.receiving.core.client.nimrds.model.RdsReceiptsSummaryByPoResponse;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceiveContainersRequestBody;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceiveContainersResponseBody;
import com.walmart.move.nim.receiving.core.client.nimrds.model.StoreDistribution;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryQtyByPoLineResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Async wrapper for NimRDS
 *
 * @author b0s06hg
 */
@Component
public class AsyncNimRdsRestApiClient {

  @Autowired private NimRDSRestApiClient nimRDSRestApiClient;

  @Async
  public CompletableFuture<RdsReceiptsSummaryByPoResponse> getReceivedQtySummaryByPo(
      Long deliveryNumber, Map<String, Object> httpHeaders) throws ReceivingException {
    return CompletableFuture.completedFuture(
        nimRDSRestApiClient.getReceivedQtySummaryByPo(deliveryNumber, httpHeaders));
  }

  @Async
  public CompletableFuture<ReceiptSummaryQtyByPoLineResponse> getReceivedQtySummaryByPoLine(
      Long deliveryNumber, String purchaseReferenceNumber, Map<String, Object> httpHeaders)
      throws ReceivingException {
    return CompletableFuture.completedFuture(
        nimRDSRestApiClient.getReceivedQtySummaryByPoLine(
            deliveryNumber, purchaseReferenceNumber, httpHeaders));
  }

  @Async
  public CompletableFuture<ReceiveContainersResponseBody> getReceivedContainers(
      ReceiveContainersRequestBody receiveContainersRequestBody, Map<String, Object> httpHeaders) {
    return CompletableFuture.completedFuture(
        nimRDSRestApiClient.receiveContainers(receiveContainersRequestBody, httpHeaders));
  }

  @Async
  public CompletableFuture<Pair<Integer, List<StoreDistribution>>>
      getStoreDistributionByDeliveryDocument(
          String purchaseReferenceNumber,
          int purchaseReferenceLineNumber,
          Map<String, Object> httpHeaders) {
    return CompletableFuture.completedFuture(
        nimRDSRestApiClient.getStoreDistributionByDeliveryDocumentLine(
            purchaseReferenceNumber, purchaseReferenceLineNumber, httpHeaders));
  }
}
