package com.walmart.move.nim.receiving.core.framework.message.processor;

import com.walmart.move.nim.receiving.core.message.common.ReceivingEvent;

/** ProcessExecutor for receiving */
public interface ProcessExecutor {

  /**
   * Process Executor method. Please make sure, you are handling duplicate message processing as
   * well as offset commit. Otherwise, it may lead to consumer re-balance soon which needs to be
   * avoided
   *
   * @param receivingEvent the receiving event
   */
  void doExecute(ReceivingEvent receivingEvent);

  /**
   * Method which tells {@link
   * com.walmart.move.nim.receiving.core.helper.ProcessInitiator#initiateProcess} if your process is
   * configured to use Self Loop Async flow. Can use CCM flags or a logic.
   *
   * @return the boolean
   */
  boolean isAsync();
}
