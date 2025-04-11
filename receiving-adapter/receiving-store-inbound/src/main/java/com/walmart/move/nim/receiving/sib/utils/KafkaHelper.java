package com.walmart.move.nim.receiving.sib.utils;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.sib.model.ei.EIEvent;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.Objects;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;

public class KafkaHelper {

  public static Message<String> buildKafkaMessage(EIEvent eiEvent, String topic, Gson gson) {

    MessageHeaderAccessor accessor = new MessageHeaderAccessor();
    accessor.setHeader(KafkaHeaders.TOPIC, topic);

    String key =
        new StringBuilder()
            .append(TenantContext.getFacilityCountryCode())
            .append(Constants.UNDERSCORE)
            .append(TenantContext.getFacilityNum())
            .append(Constants.UNDERSCORE)
            .append(eiEvent.getBody().getDocuments().getPalletId())
            .toString();

    accessor.setHeader(KafkaHeaders.MESSAGE_KEY, key);
    accessor.setHeader("eventType", eiEvent.getHeader().getEventType().getBytes());
    accessor.setHeader("originatorId", eiEvent.getHeader().getOriginatorId().getBytes());
    accessor.setHeader(
        "eventCreationTime",
        ReceivingUtils.dateConversionToUTC(eiEvent.getHeader().getEventCreationTime()).getBytes());
    accessor.setHeader("key", key.getBytes());
    accessor.setHeader("version", eiEvent.getHeader().getVersion().getBytes());
    accessor.setHeader(
        "countryCode", eiEvent.getHeader().getNodeInfo().getCountryCode().getBytes());
    accessor.setHeader("messageId", eiEvent.getHeader().getMessageId().getBytes());
    accessor.setHeader("correlationId", eiEvent.getHeader().getCorrelationId().getBytes());
    accessor.setHeader(
        "nodeId", eiEvent.getHeader().getNodeInfo().getNodeId().toString().getBytes());
    accessor.setHeader(
        "msgTimestamp",
        ReceivingUtils.dateConversionToUTC(eiEvent.getHeader().getMsgTimestamp()).getBytes());
    if (Objects.nonNull(eiEvent.getHeader().getEventSubType())) {
      accessor.setHeader("eventSubType", eiEvent.getHeader().getEventSubType().getBytes());
    }

    return MessageBuilder.createMessage(gson.toJson(eiEvent), accessor.getMessageHeaders());
  }
}
