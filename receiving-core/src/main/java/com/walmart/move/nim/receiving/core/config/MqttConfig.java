package com.walmart.move.nim.receiving.core.config;

import static org.eclipse.paho.client.mqttv3.MqttClient.generateClientId;

import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.Objects;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@ConditionalOnExpression("${enable.mqtt.notifications:false}")
@Profile("!test")
@Configuration
public class MqttConfig implements DisposableBean {
  private static final Logger LOGGER = LoggerFactory.getLogger(MqttConfig.class);
  IMqttClient mqttClient;
  @ManagedConfiguration private InfraConfig infraConfig;

  @Bean
  public MqttConnectOptions mqttConnectOptions() {
    MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
    // Sets whether the client and server should remember state across restarts and reconnects.
    mqttConnectOptions.setCleanSession(true);
    mqttConnectOptions.setAutomaticReconnect(true);
    return mqttConnectOptions;
  }

  @Bean
  public IMqttClient mqttClient() throws MqttException {
    MemoryPersistence memoryPersistence = new MemoryPersistence();
    String brokerUrl = infraConfig.getMaasBrokerUrl();
    String clientId = generateClientId();
    mqttClient = new MqttClient(brokerUrl, clientId, memoryPersistence);

    mqttClient.setCallback(
        new MqttCallback() {
          @Override
          public void connectionLost(Throwable throwable) {
            LOGGER.info("Connection lost to the MQTT server at: {}", System.currentTimeMillis());
          }

          @Override
          public void messageArrived(String s, MqttMessage mqttMessage) {
            LOGGER.info(
                "Message arrived using MQTT: {} at: {}", mqttMessage, System.currentTimeMillis());
          }

          @Override
          public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
            LOGGER.info(
                "Successfully published the message using MQTT with complete status as {}",
                iMqttDeliveryToken.isComplete());
          }
        });

    mqttClient.connect(mqttConnectOptions());
    LOGGER.info(
        "MQTT client created successfully with brokerUrl as: {} and clientId as: {}",
        brokerUrl,
        clientId);
    return mqttClient;
  }

  @Override
  public void destroy() throws MqttException {
    try {
      if (Objects.nonNull(mqttClient) && mqttClient.isConnected()) {
        mqttClient.disconnect();
        mqttClient.close();
        LOGGER.info("MQTT client has been disconnected");
      }
    } catch (MqttException e) {
      LOGGER.error(
          "Error while closing the connection with MQTT having exception - {}",
          ExceptionUtils.getStackTrace(e));
    }
  }
}
