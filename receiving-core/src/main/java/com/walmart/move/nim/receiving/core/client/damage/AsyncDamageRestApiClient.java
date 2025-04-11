package com.walmart.move.nim.receiving.core.client.damage;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Async Wrapper for DamageRestApiClient
 *
 * @author v0k00fe
 */
@Component
public class AsyncDamageRestApiClient {

  @Autowired private DamageRestApiClient damageRestApiClient;

  @Async
  public CompletableFuture<Optional<List<DamageDeliveryInfo>>> findDamagesByDelivery(
      Long deliveryNumber, Map<String, Object> forwardableHeaders) {

    try {
      return CompletableFuture.completedFuture(
          damageRestApiClient.findDamagesByDelivery(deliveryNumber, forwardableHeaders));
    } catch (DamageRestApiClientException e) {
      throw new CompletionException(e);
    }
  }
}
