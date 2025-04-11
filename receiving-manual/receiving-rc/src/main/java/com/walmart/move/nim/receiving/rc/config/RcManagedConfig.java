package com.walmart.move.nim.receiving.rc.config;

import io.strati.configuration.annotation.Configuration;
import io.strati.configuration.annotation.Property;
import lombok.Getter;

@Configuration(configName = "rcManagedConfig")
@Getter
public class RcManagedConfig {
  @Property(propertyName = "destination.parent.container.type")
  private String destinationParentContainerType;

  @Property(propertyName = "destination.container.type")
  private String destinationContainerType;

  @Property(propertyName = "enable.rcid.container")
  private Boolean containerRCIDEnabled;
}
