package com.walmart.move.nim.receiving.core.config.app;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.walmart.certexpirycheck.CertificateExpirationMetrics;
import com.walmart.move.nim.receiving.core.common.KafkaHelper;
import com.walmart.move.nim.receiving.core.config.InfraConfig;
import com.walmart.move.nim.receiving.core.config.KafkaConfig;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * * This is Kafka Producer configurations
 *
 * @author sitakant
 */
@Configuration
@ConditionalOnExpression("${enable.receiving.kafka:true}")
@Profile("!test")
@Slf4j
/** When disabling kafka, ensure to disable all dependent module like endgame , fixture. */
public class KafkaProducerConfig {

  private static final Logger LOGGER = LoggerFactory.getLogger(KafkaProducerConfig.class);

  @ManagedConfiguration private InfraConfig infraConfig;

  @ManagedConfiguration private KafkaConfig kafkaConfig;

  @Value("${secrets.key}")
  private String secretKey;

  @Value("${platform:#{null}}")
  private String platform;

  @Value("${is-enable-in-primary-region-node}")
  private boolean isEnableInPrimaryRegionNode;

  public Map<String, Object> secureProducerConfigs() throws Exception {
    Map<String, Object> property = new HashMap<>();
    property.put(
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, infraConfig.getActiveSecureAtlasKafkaBrokers());
    LOGGER.info(
        "Atlas Secure Kafka Producer: Brokers:{};",
        property.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
    property.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    property.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    property.put(
        ProducerConfig.MAX_REQUEST_SIZE_CONFIG,
        kafkaConfig.getKafkaProducerMaxRequestSizeInBytes());
    KafkaHelper.setKafkaSecurityProps(property, kafkaConfig, secretKey, Objects.nonNull(platform));
    return property;
  }

  @Bean(ReceivingConstants.SECURE_KAFKA_PRODUCER)
  public ProducerFactory<String, String> secureProducerFactory() throws Exception {
    log.info("is-enable-in-primary-region-node: {}, infraConfig.activeSecureAtlasKafkaBrokers: {}"
            , isEnableInPrimaryRegionNode, infraConfig.getActiveSecureAtlasKafkaBrokers());
    return new DefaultKafkaProducerFactory<>(secureProducerConfigs());
  }

  @Bean(ReceivingConstants.SECURE_KAFKA_TEMPLATE)
  public KafkaTemplate<String, String> secureKafkaTemplate(
      @Qualifier(ReceivingConstants.SECURE_KAFKA_PRODUCER)
          ProducerFactory<String, String> producerFactory)
      throws Exception {
    log.info("KafkaProducerConfig::secureKafkaTemplate bean Initialization, is-enable-in-primary-region-node: {}, infraConfig.activeSecureAtlasKafkaBrokers: {}"
            , isEnableInPrimaryRegionNode, infraConfig.getActiveSecureAtlasKafkaBrokers());
    return new KafkaTemplate<>(producerFactory);
  }

  @Bean(ReceivingConstants.SECURE_KAFKA_TEMPLATE)
  public KafkaTemplate<String, String> drSecureKafkaTemplate()
          throws Exception {
    log.info("KafkaProducerConfig::drSecureKafkaTemplate bean Initialization, is-enable-in-primary-region-node: {}, infraConfig.activeSecureAtlasKafkaBrokers: {}"
            , isEnableInPrimaryRegionNode, infraConfig.getActiveSecureAtlasKafkaBrokers());
    Map<String, Object> emptyProperties = new HashMap<>();
    ProducerFactory<String, String> producerFactory =  new DefaultKafkaProducerFactory<>(emptyProperties);
    return new KafkaTemplate<>(producerFactory);
  }

  /**
   * Have CCM kafka.certificate.expiry.check.enabled value as true to enable. GDC as pilot will
   * enable, other markets will not impact as now. Once pilot has no impacts will change default to
   * true so all markets will have this feature irrespective of ccm config
   *
   * @return CertificateExpirationMetrics
   */
  @Bean
  @ConditionalOnExpression("${kafka.certificate.expiry.check.enabled:false}")
  public CertificateExpirationMetrics x509CertificateExpirationMetrics() {
    Map<String, String> certMap = new HashMap<>();
    final String kafkaSSLTruststoreLocation = kafkaConfig.getKafkaSSLTruststoreLocation();
    final String kafkaSSLTruststorePassword = kafkaConfig.getKafkaSSLTruststorePassword();
    if (isNotBlank(kafkaSSLTruststoreLocation) && new File(kafkaSSLTruststoreLocation).exists()) {
      certMap.put(kafkaSSLTruststoreLocation, kafkaSSLTruststorePassword);
      LOGGER.info(
          "receiving-api backend: Certificate Expiration Metric Bean is created with valid parameters");
    } else
      LOGGER.warn(
          "receiving-api backend: kafkaSSLTruststoreLocation file({}) does not exist",
          kafkaSSLTruststoreLocation);
    final String kafkaSSLTruststoreWcnpLocation = kafkaConfig.getKafkaSSLTruststoreWcnpLocation();
    if (isNotBlank(kafkaSSLTruststoreWcnpLocation)
        && new File(kafkaSSLTruststoreWcnpLocation).exists()) {
      certMap.put(kafkaSSLTruststoreWcnpLocation, kafkaSSLTruststorePassword);
      LOGGER.info(
          "receiving-api backend: Certificate Expiration Metric Bean is created with valid parameters(kafkaSSLTruststoreWcnpLocation)");
    } else
      LOGGER.warn(
          "receiving-api backend: kafkaSSLTruststoreWcnpLocation file({}) does not exist",
          kafkaSSLTruststoreWcnpLocation);

    return new CertificateExpirationMetrics(certMap);
  }
}
