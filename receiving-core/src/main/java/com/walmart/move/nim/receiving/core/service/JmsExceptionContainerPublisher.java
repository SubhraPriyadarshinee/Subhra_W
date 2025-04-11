package com.walmart.move.nim.receiving.core.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.config.MaasTopics;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.message.publisher.MessagePublisher;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component(ReceivingConstants.JMS_EXCEPTION_CONTAINER_PUBLISHER)
public class JmsExceptionContainerPublisher implements MessagePublisher<ContainerDTO> {

  private final Gson gson;

  @ManagedConfiguration private MaasTopics maasTopics;
  @Autowired private JmsPublisher jmsPublisher;

  public JmsExceptionContainerPublisher() {
    gson =
        new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .registerTypeAdapter(Date.class, new GsonUTCDateAdapter())
            .create();
  }

  @Override
  public void publish(ContainerDTO containerDTO, Map<String, Object> messageHeader) {
    String payload = gson.toJson(containerDTO);
    ReceivingJMSEvent receivingJMSEvent = new ReceivingJMSEvent(messageHeader, payload);
    String exceptionContainerPublishTopic =
        Objects.nonNull(maasTopics.getPubExceptionContainerTopic())
            ? maasTopics.getPubExceptionContainerTopic()
            : ReceivingConstants.PUB_RECEIPTS_EXCEPTION_TOPIC;
    jmsPublisher.publish(exceptionContainerPublishTopic, receivingJMSEvent, Boolean.TRUE);
  }
}
