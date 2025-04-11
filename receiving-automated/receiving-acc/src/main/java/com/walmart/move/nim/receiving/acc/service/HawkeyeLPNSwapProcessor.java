package com.walmart.move.nim.receiving.acc.service;

import com.walmart.move.nim.receiving.acc.model.HawkeyeLpnSwapEventMessage;
import com.walmart.move.nim.receiving.core.advice.SecurePublisher;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.KafkaHelper;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;

public class HawkeyeLPNSwapProcessor implements EventProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(HawkeyeLPNSwapProcessor.class);

  @SecurePublisher private KafkaTemplate secureKafkaTemplate;

  @Value("${acc.pa.lpn.swap.process.topic}")
  protected String deliveryStatusTopic;

  @Override
  public void processEvent(MessageData messageData) {

    HawkeyeLpnSwapEventMessage hawkeyeLpnSwapEventMessage =
        (HawkeyeLpnSwapEventMessage) messageData;
    String lpnDetails =
        hawkeyeLpnSwapEventMessage.getFinalContainer().getTrackingId()
            + "_"
            + hawkeyeLpnSwapEventMessage.getSwapContainer().getTrackingId();

    try {
      Map<String, Object> headers = new HashMap<>();
      headers.put(ReceivingConstants.MSG_TIMESTAMP, new Date());
      headers.put(
          ReceivingConstants.MESSAGE_ID,
          hawkeyeLpnSwapEventMessage.getFinalContainer().getMessageId());
      headers.put(ReceivingConstants.TENENT_FACLITYNUM, TenantContext.getFacilityNum());
      headers.put(ReceivingConstants.TENENT_COUNTRY_CODE, TenantContext.getFacilityCountryCode());
      headers.put(ReceivingConstants.SYM_EVENT_TYPE_KEY, ReceivingConstants.EVENT_DESTINATION_SWAP);

      LOGGER.info(
          "Publishing LPN Swap message LPN_SwapLPN {}, to kafka topic {}",
          lpnDetails,
          deliveryStatusTopic);
      String payload = JacksonParser.writeValueAsString(hawkeyeLpnSwapEventMessage);
      Message<String> message =
          KafkaHelper.buildKafkaMessage(lpnDetails, payload, deliveryStatusTopic, headers);

      // publish to inventory and OP
      secureKafkaTemplate.send(message);

      LOGGER.info(
          "Secure Kafka: Successfully published the LPN SWAP message = {} to topic = {}",
          payload,
          deliveryStatusTopic);

    } catch (Exception ex) {
      LOGGER.error(
          "Secure Kafka: Failed to publish the LPN_swapLPN {} to topic = {}",
          lpnDetails,
          deliveryStatusTopic);
    }
  }
}
