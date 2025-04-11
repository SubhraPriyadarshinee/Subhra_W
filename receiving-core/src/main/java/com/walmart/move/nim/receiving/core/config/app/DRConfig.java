package com.walmart.move.nim.receiving.core.config.app;

import io.strati.configuration.annotation.Configuration;
import io.strati.configuration.annotation.Property;
import lombok.Getter;
import lombok.ToString;

@Getter
@Configuration(configName = "drConfig")
@ToString
public class DRConfig {

  @Property(propertyName = "enable.dr")
  private boolean enableDR;
}
