package com.walmart.move.nim.receiving.core.common;

import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import java.util.Objects;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MqttHelper {
  private static final Logger LOGGER = LoggerFactory.getLogger(MqttHelper.class);

  public static MqttMessage buildMqttMessage(String payload, int qos, boolean toBeRetained) {

    if (Objects.isNull(payload)) {
      throw new ReceivingInternalException(
          ExceptionCodes.INVALID_MQTT_PAYLOAD, "MessageBody cannot be null");
    }
    MqttMessage mqttMessage = new MqttMessage();
    mqttMessage.setPayload(payload.getBytes());
    mqttMessage.setQos(qos);
    mqttMessage.setRetained(toBeRetained);

    LOGGER.info(
        "Message to be sent via Mqtt is created with payload = {}, QoS = {} and toBeRetained = {}",
        payload,
        qos,
        toBeRetained);
    return mqttMessage;
  }
}
