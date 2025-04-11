package com.walmart.move.nim.receiving.wfs.config;

import io.strati.configuration.annotation.Configuration;
import io.strati.configuration.annotation.Property;
import lombok.Getter;

@Configuration(configName = "wfsManagedConfig")
@Getter
public class WFSManagedConfig {
  @Property(propertyName = "fc.name.mapping")
  private String fcNameMapping;
}
