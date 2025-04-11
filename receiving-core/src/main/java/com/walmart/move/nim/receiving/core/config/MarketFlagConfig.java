package com.walmart.move.nim.receiving.core.config;

import io.strati.configuration.annotation.Configuration;
import io.strati.configuration.annotation.Property;
import lombok.Getter;

@Configuration(configName = "marketFeatureFlagConfig")
@Getter
public class MarketFlagConfig {
  @Property(propertyName = "featureFlagConfig")
  private String featureFlags;
}
