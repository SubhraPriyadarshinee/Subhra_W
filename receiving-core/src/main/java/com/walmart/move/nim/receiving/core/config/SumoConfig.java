package com.walmart.move.nim.receiving.core.config;

import io.strati.configuration.annotation.Configuration;
import io.strati.configuration.annotation.Property;
import lombok.Getter;

@Configuration(configName = "sumoConfig")
@Getter
public class SumoConfig {

  @Property(propertyName = "sumo.base.url")
  private String baseUrl;

  @Property(propertyName = "sumo.master.key")
  private String masterKey;

  @Property(propertyName = "sumo.app.key")
  private String appKey;

  @Property(propertyName = "sumo.expiration.minute")
  private long expirationMin;

  @Property(propertyName = "acl.notification.title")
  private String aclNotificationTitle;

  @Property(propertyName = "acl.notification.alert")
  private String aclNotificationAlert;

  @Property(propertyName = "sumo.app.uuid")
  private String appUUID;

  @Property(propertyName = "sumo.v2.base.url")
  private String sumo2BaseUrl;

  @Property(propertyName = "sumo.v2.context.path")
  private String sumo2ContextPath;

  @Property(propertyName = "sumo.v2.app.uuid")
  private String sumo2AppUUId;

  @Property(propertyName = "sumo.v2.domain")
  private String sumoDomain;

  @Property(propertyName = "sumo.privatekey.location")
  private String privateKeyLocation;

  @Property(propertyName = "sumo.content.available")
  private int contentAvailable;
}
