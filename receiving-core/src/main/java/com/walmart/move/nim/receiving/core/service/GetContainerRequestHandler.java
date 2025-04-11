package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.entity.Container;
import org.springframework.http.HttpHeaders;

public interface GetContainerRequestHandler {
  Container getContainerByTrackingId(
      String trackingId,
      boolean includeChilds,
      String uom,
      boolean isReEngageDecantFlow,
      HttpHeaders httpHeaders)
      throws ReceivingException;
}
