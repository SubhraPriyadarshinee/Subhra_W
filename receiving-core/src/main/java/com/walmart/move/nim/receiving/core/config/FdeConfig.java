package com.walmart.move.nim.receiving.core.config;

import io.strati.configuration.annotation.Configuration;
import io.strati.configuration.annotation.Property;
import lombok.Getter;

@Configuration(configName = "fdeConfig")
@Getter
public class FdeConfig {
  @Property(propertyName = "spec")
  private String spec;
}
