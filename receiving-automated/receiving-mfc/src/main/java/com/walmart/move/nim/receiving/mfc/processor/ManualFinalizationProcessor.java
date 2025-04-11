package com.walmart.move.nim.receiving.mfc.processor;

import com.walmart.move.nim.receiving.core.framework.message.processor.ProcessExecutor;
import com.walmart.move.nim.receiving.core.message.common.ReceivingEvent;
import com.walmart.move.nim.receiving.mfc.message.publisher.ManualFinalizationPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class ManualFinalizationProcessor extends BaseNGRProcessor implements ProcessExecutor {
  private static final Logger LOGGER = LoggerFactory.getLogger(ManualFinalizationProcessor.class);

  @Autowired private ManualFinalizationPublisher kafkaManualFinalizationPublisher;

  @Override
  public void doExecute(ReceivingEvent receivingEvent) {
    kafkaManualFinalizationPublisher.publish(processReceivingEvents(receivingEvent));
  }

  @Override
  public boolean isAsync() {
    return false;
  }
}
