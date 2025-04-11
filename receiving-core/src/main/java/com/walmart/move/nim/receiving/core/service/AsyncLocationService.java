package com.walmart.move.nim.receiving.core.service;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class AsyncLocationService {

  @Autowired LocationService locationService;

  @Async
  public CompletableFuture<JsonObject> getBulkLocationInfo(
      List<String> locationNames, HttpHeaders httpHeaders) {
    return CompletableFuture.completedFuture(
        locationService.getBulkLocationInfo(locationNames, httpHeaders));
  }
}
