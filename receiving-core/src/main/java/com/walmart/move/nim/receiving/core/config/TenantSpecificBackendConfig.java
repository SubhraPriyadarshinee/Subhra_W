package com.walmart.move.nim.receiving.core.config;

import io.strati.configuration.annotation.Configuration;
import io.strati.configuration.annotation.Property;
import java.util.List;
import lombok.Getter;

@Configuration(configName = "tenantSpecificBackendConfig")
@Getter
public class TenantSpecificBackendConfig {

  @Property(propertyName = "featureFlags")
  private String featureFlags;

  @Property(propertyName = "notification.kafka.enabled.facilities")
  private List<Integer> notificationKafkaEnabledFacilities;

  @Property(propertyName = "is.automation.delivery.filter.enabled")
  private boolean isAutomationDeliveryFilterEnabled;
}
