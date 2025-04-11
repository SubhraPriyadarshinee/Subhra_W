package com.walmart.move.nim.receiving.core.event.processor.update;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultUpdateEventProcessor implements EventProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultUpdateEventProcessor.class);

  @Override
  public void processEvent(MessageData messageData) throws ReceivingException {
    LOGGER.warn("Default implementation found");
  }
}
