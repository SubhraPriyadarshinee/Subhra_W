package com.walmart.move.nim.receiving.core.config;

import io.strati.configuration.annotation.Configuration;
import io.strati.configuration.annotation.Property;
import java.util.List;
import lombok.Getter;

@Configuration(configName = "userCustomizedClientConfig")
@Getter
public class UserOverridenClientConfig {
  @Property(propertyName = "users")
  private List<String> users;

  @Property(propertyName = "featureFlags")
  private String featureFlags;
}
