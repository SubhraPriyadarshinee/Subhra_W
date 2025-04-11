package com.walmart.move.nim.receiving.core.config;

import io.strati.configuration.annotation.Configuration;
import io.strati.configuration.annotation.Property;
import lombok.Getter;

@Configuration(configName = "hawkshawConfig")
@Getter
public class HawkshawConfig {
  @Property(propertyName = "hawkshaw.pub.id")
  private String publisherId;

  @Property(propertyName = "hawkshaw.sequence.registry.url")
  private String sequenceRegistryUrl;

  @Property(propertyName = "hawkshaw.app.id")
  private String appId;

  @Property(propertyName = "enable.hawkshaw.retry.tracking")
  private boolean retryTracking;

  @Property(propertyName = "hawkshaw.tenant.id")
  private String tenantId;
}
