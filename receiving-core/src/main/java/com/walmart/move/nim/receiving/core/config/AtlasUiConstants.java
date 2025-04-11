package com.walmart.move.nim.receiving.core.config;

import io.strati.configuration.annotation.Configuration;
import io.strati.configuration.annotation.Property;
import lombok.Getter;

@Configuration(configName = "uiconstants")
@Getter
public class AtlasUiConstants {

  @Property(propertyName = "constants")
  private String constants;
}
