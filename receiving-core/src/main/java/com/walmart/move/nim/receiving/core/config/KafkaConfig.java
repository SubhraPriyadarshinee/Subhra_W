package com.walmart.move.nim.receiving.core.config;

import io.strati.configuration.annotation.Configuration;
import io.strati.configuration.annotation.Property;
import lombok.Getter;

@Configuration(configName = "kafkaConfig")
@Getter
public class KafkaConfig {
  @Property(propertyName = "kafka.security.protocol")
  private String kafkaSecurityProtocol;

  @Property(propertyName = "kafka.ssl.truststore.location")
  private String kafkaSSLTruststoreLocation;

  @Property(propertyName = "kafka.ssl.truststore.wcnp.location")
  private String kafkaSSLTruststoreWcnpLocation;

  @Property(propertyName = "taas.kafka.ssl.truststore.wcnp.location")
  private String taasKafkaSSLTruststoreWcnpLocation;

  @Property(propertyName = "kafka.ssl.truststore.password")
  private String kafkaSSLTruststorePassword;

  @Property(propertyName = "kafka.ssl.keystore.location")
  private String kafkaSSLKeystoreLocation;

  @Property(propertyName = "kafka.ssl.keystore.wcnp.location")
  private String kafkaSSLKeystoreWcnpLocation;

  @Property(propertyName = "taas.kafka.ssl.keystore.wcnp.location")
  private String taasKafkaSSLKeystoreWcnpLocation;

  @Property(propertyName = "kafka.ssl.keystore.password")
  private String kafkaSSLKeystorePassword;

  @Property(propertyName = "kafka.ssl.key.password")
  private String kafkaSSLKeyPassword;

  @Property(propertyName = "is.hawkeye.secure.publish")
  private boolean isHawkeyeSecurePublish;

  @Property(propertyName = "is.inventory.on.secure.publish")
  private boolean isInventoryOnSecureKafka;

  @Property(propertyName = "is.decant.on.secure.publish")
  private boolean isDecantOnSecureKafka;

  @Property(propertyName = "is.gdm.on.secure.kafka")
  private boolean isGDMOnSecureKafka;

  @Property(propertyName = "kafka.producer.max.request.size.bytes")
  private Integer kafkaProducerMaxRequestSizeInBytes = 1048576;

  @Property(propertyName = "is.kafka.consumer.group.id.by.topic.enabled")
  private boolean isKafkaConsumerGroupIdByTopicEnabled;
}
