package com.walmart.move.nim.receiving.core.service;

import java.util.List;
import org.springframework.http.HttpHeaders;

public interface DeleteContainersRequestHandler {

  void deleteContainersByTrackingId(List<String> trackingIds, HttpHeaders httpHeaders);
}
