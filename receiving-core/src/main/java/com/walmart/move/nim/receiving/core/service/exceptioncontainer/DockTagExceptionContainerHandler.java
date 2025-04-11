package com.walmart.move.nim.receiving.core.service.exceptioncontainer;

import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.entity.Container;
import org.springframework.stereotype.Service;

@Service
public class DockTagExceptionContainerHandler extends ExceptionContainerHandler {
  @Override
  public void publishException(Container container) {
    // Publish to inventory
    containerService.publishExceptionContainer(
        container, ReceivingUtils.getHeaders(), Boolean.TRUE);
  }
}
