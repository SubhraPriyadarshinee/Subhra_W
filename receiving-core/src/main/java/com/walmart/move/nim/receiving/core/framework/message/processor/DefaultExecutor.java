package com.walmart.move.nim.receiving.core.framework.message.processor;

import com.walmart.move.nim.receiving.core.message.common.ReceivingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DefaultExecutor implements ProcessExecutor {
  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultExecutor.class);

  @Override
  public void doExecute(ReceivingEvent receivingEvent) {
    LOGGER.warn("Loaded default process executor for this process");
  }

  @Override
  public boolean isAsync() {
    return false;
  }
}
