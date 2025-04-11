package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.inventory.InventoryContainerDetails;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class AsyncInventoryService {
  @Autowired InventoryService inventoryService;

  @Async
  public CompletableFuture<InventoryContainerDetails> getInventoryContainerDetails(
      String trackingId, HttpHeaders httpHeaders) throws ReceivingException {
    return CompletableFuture.completedFuture(
        inventoryService.getInventoryContainerDetails(trackingId, httpHeaders));
  }
}
