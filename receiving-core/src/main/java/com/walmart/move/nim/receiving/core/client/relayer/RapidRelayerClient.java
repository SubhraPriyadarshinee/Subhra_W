package com.walmart.move.nim.receiving.core.client.relayer;

import com.walmart.move.nim.atlas.platform.policy.commons.Message;
import com.walmart.move.nim.receiving.core.model.RapidRelayerData;
import com.walmart.platform.messages.MetaData;
import com.walmart.platform.repositories.OutboxEvent;
import com.walmart.platform.repositories.PayloadRef;
import com.walmart.platform.service.OutboxEventSinkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RapidRelayerClient {
  @Autowired OutboxEventSinkService outboxEventSinkService;

  private OutboxEvent mapToOutboxEvent(RapidRelayerData rapidRelayerData) {
    Message message =
        Message.builder()
            .headers(rapidRelayerData.getHeaders())
            .body(rapidRelayerData.getBody())
            .build();
    PayloadRef payloadRef =
        PayloadRef.builder()
            .storagePolicyId(rapidRelayerData.getStoragePolicyId())
            .data(message)
            .ref(rapidRelayerData.getRef())
            .build();
    return OutboxEvent.builder()
        .eventIdentifier(rapidRelayerData.getEventIdentifier())
        .executionTs(rapidRelayerData.getExecutionTs())
        .publisherPolicyId(rapidRelayerData.getPublisherPolicyId())
        .payloadRef(payloadRef)
        .metaData(new MetaData(rapidRelayerData.getMetaDataValues()))
        .build();
  }

  public void sendDataToRapidRelayer(RapidRelayerData rapidRelayerData) {
    outboxEventSinkService.saveEvent(mapToOutboxEvent(rapidRelayerData));
  }
}
