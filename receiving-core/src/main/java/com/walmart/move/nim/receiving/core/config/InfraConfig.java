package com.walmart.move.nim.receiving.core.config;

import io.strati.configuration.annotation.Configuration;
import io.strati.configuration.annotation.DefaultValue;
import io.strati.configuration.annotation.Property;
import lombok.Getter;

@Configuration(configName = "infraConfig")
@Getter
public class InfraConfig {

  //  Active Config:

  @Property(propertyName = "atlas.db.active.url")
  private String activeDBURL;

  @Property(propertyName = "atlas.db.active.username")
  private String activeDBUsername;

  @Property(propertyName = "atlas.db.active.password")
  private String activeDBPassword;

  @Property(propertyName = "atlas.maas.active.ccm.name")
  private String activeMaaSCCMName;

  @Property(propertyName = "atlas.maas.active.username")
  private String activeMaaSUsername;

  @Property(propertyName = "atlas.maas.active.password")
  private String activeMaaSPassword;

  @Property(propertyName = "atlas.kafka.active.brokers")
  private String activeAtlasKafkaBrokers;

  @Property(propertyName = "atlas.secure.kafka.active.brokers")
  private String activeSecureAtlasKafkaBrokers;

  @Property(propertyName = "hawkeye.kafka.active.brokers")
  private String activeHawkeyeKafkaBrokers;

  @Property(propertyName = "hawkeye.secure.kafka.active.brokers")
  private String activeHawkeyeSecureKafkaBrokers;

  @Property(propertyName = "atlas.kafka.active.offset.mode")
  private String activeKafkaOffsetMode;

  @Property(propertyName = "hawkeye.secure.kafka.active.brokers.eus")
  private String activeHawkeyeSecureKafkaBrokersEUS;

  @Property(propertyName = "hawkeye.secure.kafka.active.brokers.scus")
  private String activeHawkeyeSecureKafkaBrokersSCUS;

  @Property(propertyName = "hawkeye.consumer.enable.eus")
  private boolean enableHawkeyeConsumerEUS;

  @Property(propertyName = "hawkeye.consumer.enable.scus")
  private boolean enableHawkeyeConsumerSCUS;

  @Property(propertyName = "hawkeye.consumer.enable")
  private boolean enableHawkeyeConsumer;

  @Property(propertyName = "firefly.consumer.enable")
  private boolean enableFireflyConsumer;

  @Property(propertyName = "firefly.secure.kafka.active.brokers")
  private String activeFireflySecureKafkaBrokers;

  @Property(propertyName = "ngr.secure.kafka.active.brokers")
  private String activeNgrSecureKafkaBrokers;

  // MaaS Secured Attributes
  @Property(propertyName = "atlas.maas.secure.enable")
  @DefaultValue.Boolean(false)
  private boolean enableAtlasMaaSSecure;

  @Property(propertyName = "atlas.maas.secure.active.ccm.name")
  private String activeSecureMaaSCCMName;

  @Property(propertyName = "atlas.maas.ssl.keystore.location")
  private String activeMaaSKeyStoreLocation;

  @Property(propertyName = "atlas.maas.ssl.keystore.password")
  private String activeMaaSKeyStorePassword;

  //  DR Flags:

  @Property(propertyName = "atlas.kafka.dr.enable")
  private boolean enableAtlasKafkaDR;

  @Property(propertyName = "atlas.secure.kafka.dr.enable")
  private boolean enableAtlasSecureKafkaDR;

  @Property(propertyName = "hawkeye.kafka.dr.enable")
  private boolean enableHawkeyeKafkaDR;

  @Property(propertyName = "firefly.secure.kafka.dr.enable")
  private boolean enableFireflySecureKafkaDR;

  @Property(propertyName = "atlas.db.dr.enable")
  private boolean enableDBDR;

  @Property(propertyName = "atlas.maas.dr.enable")
  private boolean enableMaaSDR;

  @Property(propertyName = "ngr.secure.kafka.dr.enable")
  private boolean enableNgrSecureKafkaDR;

  //  DR Config:

  @Property(propertyName = "atlas.db.dr.url")
  private String drDBURL;

  @Property(propertyName = "atlas.db.dr.username")
  private String drDBUsername;

  @Property(propertyName = "atlas.db.dr.password")
  private String drDBPassword;

  @Property(propertyName = "atlas.maas.dr.ccm.name")
  private String drMaaSCCMName;

  @Property(propertyName = "atlas.maas.secure.dr.ccm.name")
  private String drMaaSSecureCCMName;

  @Property(propertyName = "atlas.maas.dr.username")
  private String drMaaSUsername;

  @Property(propertyName = "atlas.maas.dr.password")
  private String drMaaSPassword;

  @Property(propertyName = "atlas.kafka.dr.brokers")
  private String drAtlasKafkaBrokers;

  @Property(propertyName = "atlas.secure.kafka.dr.brokers")
  private String drSecureAtlasKafkaBrokers;

  @Property(propertyName = "hawkeye.kafka.dr.brokers")
  private String drHawkeyeKafkaBrokers;

  @Property(propertyName = "hawkeye.secure.kafka.dr.brokers")
  private String drHawkeyeSecureKafkaBrokers;

  @Property(propertyName = "hawkeye.secure.kafka.dr.brokers.eus")
  private String drHawkeyeSecureKafkaBrokersEUS;

  @Property(propertyName = "hawkeye.secure.kafka.dr.brokers.scus")
  private String drHawkeyeSecureKafkaBrokersSCUS;

  @Property(propertyName = "atlas.kafka.dr.offset.mode")
  private String drKafkaOffsetMode;

  @Property(propertyName = "maas.broker.url")
  private String maasBrokerUrl;

  @Property(propertyName = "maas.broker.clientId")
  private String maasBrokerClientId;

  @Property(propertyName = "ei.kafka.dr.brokers")
  private String eiDRKafkaBrokers;

  @Property(propertyName = "ei.kafka.active.brokers")
  private String eiActiveKafkaBrokers;

  @Property(propertyName = "firefly.secure.kafka.dr.brokers")
  private String drFireflySecureKafkaBrokers;

  @Property(propertyName = "ngr.secure.kafka.dr.brokers")
  private String drNgrSecureKafkaBrokers;
}
