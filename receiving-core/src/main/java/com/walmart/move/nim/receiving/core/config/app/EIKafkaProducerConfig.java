package com.walmart.move.nim.receiving.core.config.app;

import com.walmart.move.nim.receiving.core.common.validators.DRUtils;
import com.walmart.move.nim.receiving.core.config.InfraConfig;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@Configuration
@ConditionalOnExpression(ReceivingConstants.ENABLE_EI_KAFKA)
@Profile("!test")
public class EIKafkaProducerConfig {

  @ManagedConfiguration private InfraConfig infraConfig;

  @ManagedConfiguration private DRConfig drConfig;

  public Map<String, Object> eiProducerConfigs() {
    Map<String, Object> property = new HashMap<>();
    DRUtils.setEIKafkaBrokers(property, drConfig, infraConfig);
    property.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    property.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    return property;
  }

  @Bean(ReceivingConstants.EI_KAFKA_PRODUCER)
  public ProducerFactory<String, String> eiProducerFactory() {
    return new DefaultKafkaProducerFactory<>(eiProducerConfigs());
  }

  @Bean(ReceivingConstants.EI_KAFKA_TEMPLATE)
  public KafkaTemplate<String, String> nonSecureKafkaTemplate(
      @Qualifier(ReceivingConstants.EI_KAFKA_PRODUCER)
          ProducerFactory<String, String> producerFactory) {
    return new KafkaTemplate<>(producerFactory);
  }
}
