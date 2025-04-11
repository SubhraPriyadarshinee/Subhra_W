package com.walmart.move.nim.receiving.core.config.app;

import com.walmart.move.nim.receiving.core.common.KafkaHelper;
import com.walmart.move.nim.receiving.core.config.InfraConfig;
import com.walmart.move.nim.receiving.core.config.KafkaConfig;
import com.walmart.move.nim.receiving.core.handler.KafkaListenerFilter;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;

/**
 * This is Kafka Consumer configurations
 *
 * @author sitakant
 */
@ConditionalOnExpression("${enable.hawkeye.consumer:false}")
@Configuration
public class HawkeyeKafkaConsumerConfig {

  private static final Logger LOGGER = LoggerFactory.getLogger(HawkeyeKafkaConsumerConfig.class);

  @ManagedConfiguration private InfraConfig infraConfig;

  @Value("${secrets.key}")
  private String secretKey;

  @Value("${platform:#{null}}")
  private String platform;

  @Value("${kafka.consumer.poll.interval.ms:60000}")
  private int kafkaMaxPollFrequency;

  @Value("${kafka.consumer.groupid:receiving-consumer}")
  private String groupId;

  @Value("${kafka.session.timeout:10000}")
  private Integer sessionTimeout;

  @Value("${hawkeye.kafka.consumer.concurrency:1}")
  private Integer hawkEyeKafkaConsumerConcurrency;

  @Autowired KafkaListenerFilter kafkaListenerFilter;

  @ManagedConfiguration private KafkaConfig kafkaConfig;

  public Map<String, Object> hawkeyeConsumerConfigs() {
    Map<String, Object> property = new HashMap<>();
    property.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    property.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    property.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    property.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, sessionTimeout);
    property.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, kafkaMaxPollFrequency);
    return property;
  }

  @Profile("!test")
  @Bean(ReceivingConstants.HAWKEYE_SECURE_KAFKA_LISTENER_CONTAINER_FACTORY)
  @ConditionalOnExpression("${hawkeye.consumer.enable:true}")
  public KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<String, String>>
      hawkeyeSecuredKafkaListenerContainerFactory()
          throws IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException,
              NoSuchAlgorithmException, InvalidKeyException {
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(hawkeyeSecuredConsumerFactory());
    factory.setRecordFilterStrategy(kafkaListenerFilter);
    factory.setAckDiscarded(true);
    factory.setConcurrency(hawkEyeKafkaConsumerConcurrency);
    return factory;
  }

  private ConsumerFactory<String, String> hawkeyeSecuredConsumerFactory()
      throws InvalidKeyException, BadPaddingException, NoSuchAlgorithmException,
          IllegalBlockSizeException, NoSuchPaddingException {
    Map<String, Object> property = hawkeyeConsumerConfigs();
    KafkaHelper.setKafkaSecurityProps(property, kafkaConfig, secretKey, Objects.nonNull(platform));
    property.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, infraConfig.getActiveKafkaOffsetMode());
    property.put(
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, infraConfig.getActiveHawkeyeSecureKafkaBrokers());
    LOGGER.info(
        "Hawkeye Secure Kafka Consumer: Brokers:{};",
        property.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
    return new DefaultKafkaConsumerFactory<>(property);
  }

  @Profile("!test")
  @Bean(ReceivingConstants.HAWKEYE_SECURE_KAFKA_LISTENER_CONTAINER_FACTORY_EUS)
  @ConditionalOnExpression("${hawkeye.consumer.enable.eus:false}")
  public KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<String, String>>
      hawkeyeSecuredKafkaListenerContainerFactoryEUS()
          throws IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException,
              NoSuchAlgorithmException, InvalidKeyException {
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(hawkeyeSecuredConsumerFactoryEUS());
    factory.setRecordFilterStrategy(kafkaListenerFilter);
    factory.setAckDiscarded(true);
    return factory;
  }

  private ConsumerFactory<String, String> hawkeyeSecuredConsumerFactoryEUS()
      throws InvalidKeyException, BadPaddingException, NoSuchAlgorithmException,
          IllegalBlockSizeException, NoSuchPaddingException {
    Map<String, Object> property = hawkeyeConsumerConfigs();
    KafkaHelper.setKafkaSecurityProps(property, kafkaConfig, secretKey, Objects.nonNull(platform));
    property.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, infraConfig.getActiveKafkaOffsetMode());
    property.put(
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
        infraConfig.getActiveHawkeyeSecureKafkaBrokersEUS());
    LOGGER.info(
        "Hawkeye Secure Kafka Consumer EUS: Brokers:{};",
        property.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
    return new DefaultKafkaConsumerFactory<>(property);
  }

  @Profile("!test")
  @Bean(ReceivingConstants.HAWKEYE_SECURE_KAFKA_LISTENER_CONTAINER_FACTORY_SCUS)
  @ConditionalOnExpression("${hawkeye.consumer.enable.scus:false}")
  public KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<String, String>>
      hawkeyeSecuredKafkaListenerContainerFactorySCUS()
          throws IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException,
              NoSuchAlgorithmException, InvalidKeyException {
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(hawkeyeSecuredConsumerFactorySCUS());
    factory.setRecordFilterStrategy(kafkaListenerFilter);
    factory.setAckDiscarded(true);
    return factory;
  }

  private ConsumerFactory<String, String> hawkeyeSecuredConsumerFactorySCUS()
      throws InvalidKeyException, BadPaddingException, NoSuchAlgorithmException,
          IllegalBlockSizeException, NoSuchPaddingException {
    Map<String, Object> property = hawkeyeConsumerConfigs();
    KafkaHelper.setKafkaSecurityProps(property, kafkaConfig, secretKey, Objects.nonNull(platform));
    property.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, infraConfig.getActiveKafkaOffsetMode());
    property.put(
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
        infraConfig.getActiveHawkeyeSecureKafkaBrokersSCUS());
    LOGGER.info(
        "Hawkeye Secure Kafka Consumer SCUS: Brokers:{};",
        property.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
    return new DefaultKafkaConsumerFactory<>(property);
  }
}
