package com.walmart.move.nim.receiving.core.config;

import io.strati.configuration.annotation.Configuration;
import io.strati.configuration.annotation.Property;
import lombok.Getter;
import lombok.Setter;

@Configuration(configName = "blobConfig")
@Getter
@Setter
public class BlobConfig {

  @Property(propertyName = "accountName")
  private String accountName;

  @Property(propertyName = "accountKey")
  private String accountKey;

  @Property(propertyName = "connectionSpec")
  private String connectionSpec;
}
