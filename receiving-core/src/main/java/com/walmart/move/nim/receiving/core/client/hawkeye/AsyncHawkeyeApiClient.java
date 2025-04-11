package com.walmart.move.nim.receiving.core.client.hawkeye;

import com.walmart.move.nim.receiving.core.client.hawkeye.model.DeliverySearchRequest;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class AsyncHawkeyeApiClient {

  @Autowired private HawkeyeRestApiClient hawkeyeRestApiClient;

  @Async
  public CompletableFuture<Optional<List<String>>> getDeliveriesFromHawkeye(
      DeliverySearchRequest deliverySearchRequest, HttpHeaders httpHeaders) {

    try {
      return CompletableFuture.completedFuture(
          hawkeyeRestApiClient.getHistoryDeliveriesFromHawkeye(deliverySearchRequest, httpHeaders));
    } catch (ReceivingInternalException e) {
      throw new CompletionException(e);
    }
  }
}
