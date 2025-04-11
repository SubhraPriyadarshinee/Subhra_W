package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.mock.data.MockACLNotificationSentUsingMQTT;
import com.walmart.move.nim.receiving.core.model.mqtt.MqttNotificationData;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class MqttPublisherTest extends ReceivingTestBase {

  @Mock private IMqttClient mqttClient;

  @InjectMocks private MqttPublisher mqttPublisher;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  public void resetMocks() {
    reset(mqttClient);
  }

  @Test
  public void testPublishing() throws MqttException {
    String payload = MockACLNotificationSentUsingMQTT.getACLNotificationPublishedUsingMqtt();
    doNothing().when(mqttClient).publish(any(String.class), any(MqttMessage.class));
    MqttNotificationData mqttNotificationData =
        MqttNotificationData.builder().payload(payload).build();
    mqttPublisher.publish(mqttNotificationData, null);

    verify(mqttClient, times(1))
        .publish(eq(ReceivingConstants.PUB_MQTT_NOTIFICATIONS_TOPIC), any(MqttMessage.class));
  }

  @Test
  public void testPublishing_WhenPayloadIsNull() throws MqttException {
    try {
      MqttNotificationData mqttNotificationData =
          MqttNotificationData.builder().payload(null).build();
      mqttPublisher.publish(mqttNotificationData, null);

      verify(mqttClient, times(0))
          .publish(eq(ReceivingConstants.PUB_MQTT_NOTIFICATIONS_TOPIC), any(MqttMessage.class));

    } catch (ReceivingInternalException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.UNABLE_TO_PUBLISH_MQTT_VIA_MQTT);
      assertEquals(e.getDescription(), "Unable to publish message via MQTT");
    }
  }
}
