package com.walmart.move.nim.receiving.acc.service;

import com.walmart.move.nim.receiving.acc.constants.ACCConstants;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component(value = ACCConstants.DEFAULT_LPN_SWAP_PROCESSOR)
public class DefaultLPNSwapProcessor implements EventProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultLPNSwapProcessor.class);

  @Override
  public void processEvent(MessageData messageData) throws ReceivingException {
    LOGGER.warn("Default implementation found");
  }
}
