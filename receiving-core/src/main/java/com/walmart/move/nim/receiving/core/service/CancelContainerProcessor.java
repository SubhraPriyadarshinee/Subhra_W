package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.CancelContainerRequest;
import com.walmart.move.nim.receiving.core.model.CancelContainerResponse;
import com.walmart.move.nim.receiving.core.model.SwapContainerRequest;
import java.util.List;
import org.springframework.http.HttpHeaders;

public interface CancelContainerProcessor {

  public List<CancelContainerResponse> cancelContainers(
      CancelContainerRequest cancelContainerRequest, HttpHeaders httpHeaders)
      throws ReceivingException;

  public List<CancelContainerResponse> swapContainers(
      List<SwapContainerRequest> swapContainerRequest, HttpHeaders httpHeaders);
}
