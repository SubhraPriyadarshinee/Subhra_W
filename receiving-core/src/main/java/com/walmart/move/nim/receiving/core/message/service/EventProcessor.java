package com.walmart.move.nim.receiving.core.message.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.utils.common.MessageData;

/**
 * This is an interface to process events after being received by message listener.
 *
 * @author a0b02ft
 */
public interface EventProcessor {

  /**
   * This method has to implemented to process the events after being received by message listener.
   *
   * @param t
   * @throws ReceivingException
   */
  <T extends MessageData> void processEvent(T t) throws ReceivingException;
}
