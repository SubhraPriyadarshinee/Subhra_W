package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.model.ItemOverrideRequest;
import org.springframework.http.HttpHeaders;

public interface ItemServiceHandler {
  void updateItemProperties(ItemOverrideRequest itemOverrideRequest, HttpHeaders httpHeaders);
}
