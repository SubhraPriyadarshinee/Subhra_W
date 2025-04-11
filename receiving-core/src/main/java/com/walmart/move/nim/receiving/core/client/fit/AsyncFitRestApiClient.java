package com.walmart.move.nim.receiving.core.client.fit;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Async wrapper for FitRestApiClient
 *
 * @author v0k00fe
 */
@Component
public class AsyncFitRestApiClient {

  @Autowired private FitRestApiClient fitRestApiClient;

  @Async
  public CompletableFuture<Optional<ProblemCountByDeliveryResponse>> findProblemCountByDelivery(
      Long deliveryNumber, Map<String, Object> forwardableHeaders) {

    try {
      return CompletableFuture.completedFuture(
          fitRestApiClient.findProblemCountByDelivery(deliveryNumber, forwardableHeaders));
    } catch (FitRestApiClientException e) {
      throw new CompletionException(e);
    }
  }
}
