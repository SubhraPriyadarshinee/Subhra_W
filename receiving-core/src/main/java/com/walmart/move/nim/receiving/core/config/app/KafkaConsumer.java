package com.walmart.move.nim.receiving.core.config.app;

import com.walmart.move.nim.receiving.core.common.KafkaHelper;
import com.walmart.move.nim.receiving.core.common.validators.DRUtils;
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
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.kafka.listener.ContainerProperties;

@Configuration
@ConditionalOnExpression("${enable.receiving.kafka:true}")
@Slf4j
/** When disabling kafka, ensure to disable all dependent module like endgame , fixture. */
public class KafkaConsumer {

  private static final Logger LOGGER = LoggerFactory.getLogger(KafkaConsumer.class);

  @ManagedConfiguration private InfraConfig infraConfig;

  @ManagedConfiguration private DRConfig drConfig;

  @ManagedConfiguration private KafkaConfig kafkaConfig;

  @Value("${secrets.key}")
  private String secretKey;

  @Value("${platform:#{null}}")
  private String platform;

  @Value("${kafka.consumer.poll.interval.ms:60000}")
  private int kafkaMaxPollFrequency;

  @Value("${kafka.consumer.groupid:receiving-consumer}")
  private String groupId;

  @Value("${kafka.consumer.offline.groupid:atlas-receiving-offline-offlineInstructionDownload}")
  private String offlineInstructionGroupId;

  @Value("${kafka.session.timeout:10000}")
  private Integer sessionTimeout;

  @Value("${atlas.kafka.consumer.concurrency:1}")
  private Integer atlasKafkaConsumerConcurrency;

  @Value("${is-enable-in-primary-region-node}")
  private boolean isEnableInPrimaryRegionNode;

  @Autowired KafkaListenerFilter kafkaListenerFilter;

  public Map<String, Object> atlasConsumerConfigs() {
    Map<String, Object> property = new HashMap<>();
    property.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    property.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    property.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    property.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, sessionTimeout);
    property.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, kafkaMaxPollFrequency);
    return property;
  }

  private Map<String, Object> atlasOfflineConsumerConfigs() {
    Map<String, Object> property = new HashMap<>();
    property.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    property.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    property.put(ConsumerConfig.GROUP_ID_CONFIG, offlineInstructionGroupId);
    property.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, sessionTimeout);
    property.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, kafkaMaxPollFrequency);
    property.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    return property;
  }

  @Profile("!test")
  @ConditionalOnExpression("${enable.receiving.secure.kafka:true}")
  @Bean(ReceivingConstants.ATLAS_SECURED_KAFKA_LISTENER_CONTAINER_FACTORY)
  public KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<String, String>>
      atlasSecuredKafkaListenerContainerFactory()
          throws IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException,
              NoSuchAlgorithmException, InvalidKeyException {
    log.info("is-enable-in-primary-region-node: {}, infraConfig.activeSecureAtlasKafkaBrokers: {}"
            , isEnableInPrimaryRegionNode, infraConfig.getActiveSecureAtlasKafkaBrokers());
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(atlasSecuredConsumerFactory());
    factory.setRecordFilterStrategy(kafkaListenerFilter);
    factory.setConcurrency(atlasKafkaConsumerConcurrency);
    return factory;
  }

  @Profile("!test")
  @ConditionalOnExpression("${enable.offline.receiving.secure.kafka:false}")
  @Bean(ReceivingConstants.ATLAS_OFFLINE_SECURED_KAFKA_LISTENER_CONTAINER_FACTORY)
  public KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<String, String>>
      atlasOfflineSecuredKafkaListenerContainerFactory()
          throws IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException,
              NoSuchAlgorithmException, InvalidKeyException {
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(atlasSecuredConsumerFactoryForOffline());
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
    factory.setRecordFilterStrategy(kafkaListenerFilter);
    factory.setAckDiscarded(true);
    return factory;
  }

  public ConsumerFactory<String, String> atlasSecuredConsumerFactory()
      throws InvalidKeyException, BadPaddingException, NoSuchAlgorithmException,
          IllegalBlockSizeException, NoSuchPaddingException {
    Map<String, Object> property = atlasConsumerConfigs();
    KafkaHelper.setKafkaSecurityProps(property, kafkaConfig, secretKey, Objects.nonNull(platform));
    property.put(
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, infraConfig.getActiveSecureAtlasKafkaBrokers());
    property.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, infraConfig.getActiveKafkaOffsetMode());
    LOGGER.info(
        "Atlas Secure Kafka Consumer: Brokers:{};",
        property.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
    return new DefaultKafkaConsumerFactory<>(property);
  }

  private ConsumerFactory<String, String> atlasSecuredConsumerFactoryForOffline()
      throws InvalidKeyException, BadPaddingException, NoSuchAlgorithmException,
          IllegalBlockSizeException, NoSuchPaddingException {
    Map<String, Object> property = atlasOfflineConsumerConfigs();
    KafkaHelper.setKafkaSecurityProps(property, kafkaConfig, secretKey, Objects.nonNull(platform));
    DRUtils.setAtlasSecureKafkaProperties(property, drConfig, infraConfig);
    LOGGER.info(
        "Atlas Secure Kafka Consumer: Brokers:{}; DREnabled:{}",
        property.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG),
        drConfig.isEnableDR() || infraConfig.isEnableAtlasSecureKafkaDR());
    return new DefaultKafkaConsumerFactory<>(property);
  }
}
