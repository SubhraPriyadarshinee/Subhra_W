package com.walmart.move.nim.receiving.core.service.exceptioncontainer;

import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.utils.constants.SorterExceptionReason;
import org.springframework.stereotype.Service;

@Service
public class OverageExceptionContainerHandler extends ExceptionContainerHandler {

  @Override
  public void publishException(Container container) {
    // Publish to inventory
    containerService.publishExceptionContainer(
        container, ReceivingUtils.getHeaders(), Boolean.TRUE);

    // publish divert to sorter
    publishExceptionDivertToSorter(
        container.getTrackingId(), SorterExceptionReason.OVERAGE, container.getPublishTs());
  }
}
