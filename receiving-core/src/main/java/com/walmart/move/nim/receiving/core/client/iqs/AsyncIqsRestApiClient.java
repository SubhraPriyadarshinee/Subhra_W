package com.walmart.move.nim.receiving.core.client.iqs;

import com.walmart.move.nim.receiving.core.client.iqs.model.ItemBulkResponseDto;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class AsyncIqsRestApiClient {

  @Autowired private IqsRestApiClient iqsRestApiClient;

  @Async
  public CompletableFuture<Optional<ItemBulkResponseDto>> getItemDetailsFromItemNumber(
      Set<String> itemNumbers, String facilityNumber, HttpHeaders httpHeaders) {
    try {
      return CompletableFuture.completedFuture(
          iqsRestApiClient.getItemDetailsFromItemNumber(itemNumbers, facilityNumber, httpHeaders));
    } catch (IqsRestApiClientException e) {
      throw new CompletionException(e);
    }
  }
}
