package com.walmart.move.nim.receiving.core.config;

import io.strati.configuration.annotation.Configuration;
import io.strati.configuration.annotation.Property;
import lombok.Getter;

@Configuration(configName = "uiConfig")
@Getter
public class AtlasUiConfig {

  @Property(propertyName = "featureFlags")
  private String featureFlags;
}
