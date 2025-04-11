package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.ContainerUpdateRequest;
import com.walmart.move.nim.receiving.core.model.ContainerUpdateResponse;
import org.springframework.http.HttpHeaders;

public interface UpdateContainerQuantityRequestHandler {

  public ContainerUpdateResponse updateQuantityByTrackingId(
      String trackingId, ContainerUpdateRequest containerUpdateRequest, HttpHeaders httpHeaders)
      throws ReceivingException;
}
