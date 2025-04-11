package com.walmart.move.nim.receiving.core.config;

import io.strati.configuration.annotation.Configuration;
import io.strati.configuration.annotation.Property;
import lombok.Getter;

@Configuration(configName = "marketSpecificBackendConfig")
@Getter
public class MarketBackendConfig {
  @Property(propertyName = "featureConfig")
  private String featureConfig;
}
