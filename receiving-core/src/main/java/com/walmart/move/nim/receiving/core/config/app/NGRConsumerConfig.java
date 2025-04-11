package com.walmart.move.nim.receiving.core.config.app;

import com.walmart.move.nim.receiving.core.common.KafkaHelper;
import com.walmart.move.nim.receiving.core.common.validators.DRUtils;
import com.walmart.move.nim.receiving.core.config.InfraConfig;
import com.walmart.move.nim.receiving.core.config.KafkaConfig;
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

@ConditionalOnExpression("${ngr.consumer.enable:false}")
@Configuration
public class NGRConsumerConfig {

  private static final Logger LOGGER = LoggerFactory.getLogger(NGRConsumerConfig.class);

  @ManagedConfiguration private InfraConfig infraConfig;

  @ManagedConfiguration private DRConfig drConfig;

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

  @ManagedConfiguration private KafkaConfig kafkaConfig;

  public Map<String, Object> ngrConsumerConfigs() {
    Map<String, Object> property = new HashMap<>();
    property.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    property.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    property.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    property.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, sessionTimeout);
    property.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, kafkaMaxPollFrequency);
    return property;
  }

  @Profile("!test")
  @Bean(ReceivingConstants.STORE_NGR_SECURE_KAFKA_LISTENER_CONTAINER_FACTORY)
  @ConditionalOnExpression("${ngr.consumer.enable:false}")
  public KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<String, String>>
      ngrSecuredKafkaListenerContainerFactory()
          throws IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException,
              NoSuchAlgorithmException, InvalidKeyException {
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(ngrSecuredConsumerFactory());
    return factory;
  }

  private ConsumerFactory<String, String> ngrSecuredConsumerFactory()
      throws InvalidKeyException, BadPaddingException, NoSuchAlgorithmException,
          IllegalBlockSizeException, NoSuchPaddingException {
    Map<String, Object> property = ngrConsumerConfigs();
    KafkaHelper.setKafkaSecurityProps(property, kafkaConfig, secretKey, Objects.nonNull(platform));
    DRUtils.setNGRSecureKafkaProperties(property, drConfig, infraConfig);
    LOGGER.info(
        "NGR Secure Kafka Consumer: Brokers:{}; DREnabled:{}",
        property.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG),
        drConfig.isEnableDR() || infraConfig.isEnableNgrSecureKafkaDR());
    return new DefaultKafkaConsumerFactory<>(property);
  }
}
