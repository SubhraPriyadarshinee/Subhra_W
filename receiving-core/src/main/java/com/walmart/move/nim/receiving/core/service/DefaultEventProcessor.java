package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service(ReceivingConstants.DEFAULT_DELIVERY_EVENT_PROCESSOR)
public class DefaultEventProcessor implements EventProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(EventProcessor.class);

  @Override
  public void processEvent(MessageData messageData) {
    LOGGER.info("No implementation for DefaultEventProcessor, Event: {}", messageData);
  }
}
