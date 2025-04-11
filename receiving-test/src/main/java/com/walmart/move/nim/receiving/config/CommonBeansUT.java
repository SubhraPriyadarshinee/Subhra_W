package com.walmart.move.nim.receiving.config;

import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class CommonBeansUT {

  @Bean
  @Profile("test")
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }

  @Bean
  public ResourceBundleMessageSource resourceBundleMessageSource() {
    ResourceBundleMessageSource resourceBundleMessageSource = new ResourceBundleMessageSource();
    resourceBundleMessageSource.setBasenames("messages");
    return resourceBundleMessageSource;
  }

  @Bean
  public ProducerFactory producerFactory() {
    Map<String, Object> properties = new HashMap<>();
    properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "");
    properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    return new DefaultKafkaProducerFactory(properties);
  }

  @Bean(ReceivingConstants.SECURE_KAFKA_TEMPLATE)
  public KafkaTemplate mockSecurekafka() {
    return new KafkaTemplate(producerFactory());
  }

  @Bean
  @Profile("test")
  public MqttConnectOptions mockMqttConnectOptions() {
    return new MqttConnectOptions();
  }

  @Bean
  @Profile("test")
  public MqttClient mockMqttClient() throws MqttException {
    try {
      return new MqttClient("tcp://test", "test", new MemoryPersistence());
    } catch (MqttException e) {
      throw e;
    }
  }
}
