package com.walmart.move.nim.receiving.sib.event.processing;

import com.walmart.move.nim.receiving.sib.entity.Event;
import com.walmart.move.nim.receiving.sib.model.ei.EIEvent;
import com.walmart.move.nim.receiving.sib.utils.KafkaHelper;
import java.util.Date;
import org.springframework.messaging.Message;

public class DefaultEventProcessing extends EventProcessing {

  @Override
  public Date decoratePickupTime(Event event) {
    return null;
  }

  @Override
  public void sendArrivalEvent(EIEvent eiEvent) {
    Message<String> message =
        KafkaHelper.buildKafkaMessage(eiEvent, getEiEventTopicName(), getGson());
    getKafkaTemplate().send(message);
  }
}
