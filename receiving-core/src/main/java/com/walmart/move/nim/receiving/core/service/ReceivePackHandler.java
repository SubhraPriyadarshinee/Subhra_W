package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.audit.ReceivePackResponse;
import org.springframework.http.HttpHeaders;

public interface ReceivePackHandler {

  ReceivePackResponse receiveDsdcPackByTrackingId(String trackingId, HttpHeaders httpHeaders)
      throws ReceivingException;
}
