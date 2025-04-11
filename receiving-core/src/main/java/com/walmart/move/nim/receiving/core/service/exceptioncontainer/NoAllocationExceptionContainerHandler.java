package com.walmart.move.nim.receiving.core.service.exceptioncontainer;

import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.SorterExceptionReason;
import org.springframework.stereotype.Service;

@Service
public class NoAllocationExceptionContainerHandler extends ExceptionContainerHandler {
  @Override
  public void publishException(Container container) {
    // Publish to inventory
    containerService.publishExceptionContainer(
        container, ReceivingUtils.getHeaders(), Boolean.TRUE);

    // publish divert to sorter
    if (tenantSpecificConfigReader.isFeatureFlagEnabled(
        ReceivingConstants.ENABLE_NA_SORTER_DIVERT)) {
      publishExceptionDivertToSorter(
          container.getTrackingId(), SorterExceptionReason.NO_ALLOCATION, container.getPublishTs());
    }
  }
}
