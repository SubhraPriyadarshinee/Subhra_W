package com.walmart.move.nim.receiving.core.common;

import com.walmart.move.nim.receiving.utils.constants.SorterExceptionReason;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DefaultSorterPublisher extends SorterPublisher {
  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultSorterPublisher.class);

  public void publishException(String lpn, SorterExceptionReason exceptionReason) {
    LOGGER.warn("No Implementation for DefaultSorterPublisher#publishException. LPN: {}", lpn);
  }

  public void publishStoreLabel(
      String lpn, String destinationBuNumber, String destinationBuCountryCode, Date labelDate) {
    LOGGER.warn("No Implementation for DefaultSorterPublisher#publishStoreLabel. LPN: {}", lpn);
  }
}
