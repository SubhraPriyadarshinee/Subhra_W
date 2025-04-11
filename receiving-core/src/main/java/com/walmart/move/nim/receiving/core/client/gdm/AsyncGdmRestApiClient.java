package com.walmart.move.nim.receiving.core.client.gdm;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.DeliveryWithOSDRResponse;
import com.walmart.move.nim.receiving.core.model.GdmDeliveryHistoryResponse;
import com.walmart.move.nim.receiving.core.service.DeliveryServiceRetryableImpl;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Async wrapper for GDMRestApiClient
 *
 * @author v0k00fe
 */
@Component
public class AsyncGdmRestApiClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(AsyncGdmRestApiClient.class);

  @Autowired private GDMRestApiClient gdmRestApiClient;
  @Autowired @Lazy private DeliveryServiceRetryableImpl deliveryService;

  @Async
  public CompletableFuture<DeliveryWithOSDRResponse> getDelivery(
      Long deliveryNumber, Map<String, Object> headers) {

    try {
      return CompletableFuture.completedFuture(
          gdmRestApiClient.getDelivery(deliveryNumber, headers));
    } catch (GDMRestApiClientException e) {
      throw new CompletionException(e);
    }
  }

  @Async
  public CompletableFuture<String> getDeliveryDetails(URI uri, HttpHeaders headers)
      throws ReceivingException {
    return CompletableFuture.completedFuture(deliveryService.getDeliveryByURI(uri, headers));
  }

  @Async
  public CompletableFuture<GdmDeliveryHistoryResponse> getDeliveryHistory(
      Long deliveryNumber, HttpHeaders headers) {
    try {
      return CompletableFuture.completedFuture(
          gdmRestApiClient.getDeliveryHistory(deliveryNumber, headers));
    } catch (GDMRestApiClientException e) {
      throw new CompletionException(e);
    }
  }
}
