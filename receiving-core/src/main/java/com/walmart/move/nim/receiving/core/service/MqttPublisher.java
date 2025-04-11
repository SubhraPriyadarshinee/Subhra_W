package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.MqttHelper;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.message.publisher.MessagePublisher;
import com.walmart.move.nim.receiving.core.model.mqtt.MqttNotificationData;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Map;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@ConditionalOnExpression("${enable.mqtt.notifications:false}")
@Component(ReceivingConstants.MQTT_NOTIFICATION_PUBLISHER)
public class MqttPublisher implements MessagePublisher<MqttNotificationData> {
  @Autowired private IMqttClient mqttClient;

  private static final Logger LOGGER = LoggerFactory.getLogger(MqttPublisher.class);

  @Override
  public void publish(MqttNotificationData notification, Map messageHeader) {
    try {

      MqttMessage mqttMessage =
          MqttHelper.buildMqttMessage(
              notification.getPayload(),
              ReceivingConstants.MQTT_QOS,
              ReceivingConstants.MQTT_MESSAGE_TO_BE_RETAINED);
      mqttClient.publish(ReceivingConstants.PUB_MQTT_NOTIFICATIONS_TOPIC, mqttMessage);
      LOGGER.info(
          "Payload: {} successfully published to topic: {} with QoS: {} and toBeRetained: {}",
          notification.getPayload(),
          ReceivingConstants.PUB_MQTT_NOTIFICATIONS_TOPIC,
          ReceivingConstants.MQTT_QOS,
          ReceivingConstants.MQTT_MESSAGE_TO_BE_RETAINED);
    } catch (Exception exception) {
      LOGGER.error(
          "Error while publishing notification using MQTT with exception - {}",
          ExceptionUtils.getStackTrace(exception));
      throw new ReceivingInternalException(
          ExceptionCodes.UNABLE_TO_PUBLISH_MQTT_VIA_MQTT, "Unable to publish message via MQTT");
    }
  }
}
