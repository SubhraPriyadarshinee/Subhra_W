package com.walmart.move.nim.receiving.core.client.move;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class AsyncMoveRestApiClient {
  @Autowired MoveRestApiClient moveRestApiClient;

  @Async
  public CompletableFuture<List<String>> getMoveContainerDetails(
      String trackingId, HttpHeaders httpHeaders) throws ReceivingException {
    return CompletableFuture.completedFuture(
        moveRestApiClient.getMoveContainerDetails(trackingId, httpHeaders));
  }
}
