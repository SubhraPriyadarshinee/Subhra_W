package com.walmart.move.nim.receiving.core.service.exceptioncontainer;

import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.SorterExceptionReason;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class XBlockExceptionContainerHandler extends ExceptionContainerHandler {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(XBlockExceptionContainerHandler.class);

  @Override
  public void publishExceptionDivertToSorter(
      String lpn, SorterExceptionReason sorterExceptionReason, Date labelDate) {
    // publish divert to sorter
    if (tenantSpecificConfigReader.isFeatureFlagEnabled(
        ReceivingConstants.ENABLE_XBLOCK_SORTER_DIVERT)) {
      LOGGER.info("Publishing XBLOCK exception divert to sorter for lpn: {}", lpn);
      super.publishExceptionDivertToSorter(lpn, SorterExceptionReason.XBLOCK, labelDate);
    }
  }
}
