package com.walmart.move.nim.receiving.mfc.processor;

import com.walmart.move.nim.receiving.core.framework.message.processor.ProcessExecutor;
import com.walmart.move.nim.receiving.core.message.common.ReceivingEvent;
import com.walmart.move.nim.receiving.mfc.message.publisher.ShipmentArrivalPublisher;
import org.springframework.beans.factory.annotation.Autowired;

public class ShipmentFinanceProcessor extends BaseNGRProcessor implements ProcessExecutor {

  @Autowired private ShipmentArrivalPublisher kafkaShipmentArrivalPublisher;

  @Override
  public void doExecute(ReceivingEvent receivingEvent) {
    kafkaShipmentArrivalPublisher.publish(processReceivingEvents(receivingEvent));
  }

  @Override
  public boolean isAsync() {
    return false;
  }
}
