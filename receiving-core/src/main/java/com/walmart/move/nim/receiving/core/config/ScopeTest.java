package com.walmart.move.nim.receiving.core.config;

import io.strati.configuration.annotation.Configuration;
import io.strati.configuration.annotation.Property;
import lombok.Getter;

@Configuration(configName = "scopeTest")
@Getter
public class ScopeTest {

  @Property(propertyName = "scopeSpecificValue")
  private String scopeSpecificValue;
}
