package com.walmart.move.nim.receiving.sib.config;

import io.strati.configuration.annotation.Configuration;
import io.strati.configuration.annotation.Property;
import lombok.Getter;

@Configuration(configName = "fireflyConfig")
@Getter
public class FireflyConfig {
  @Property(propertyName = "firefly.event.enabled.stores")
  private String fireflyEventEnabledStores;

  @Property(propertyName = "firefly.event.enabled.event.names")
  private String fireflyEventEnabledEventNames;

  @Property(propertyName = "firefly.event.enabled.asset.types")
  private String fireflyEventEnabledAssetTypes;
}
