package com.walmart.move.nim.receiving.core.config;

import io.strati.configuration.annotation.Configuration;
import io.strati.configuration.annotation.Property;
import lombok.Getter;

@Configuration(configName = "ssoConfig")
@Getter
public class SsoConfig {

  @Property(propertyName = "sso.platform.base.url")
  private String ssoPlatformBaseUrl;

  @Property(propertyName = "client.registered.redirect.url")
  private String clientRegisteredRedirectUri;

  @Property(propertyName = "client.id")
  private String clientId;

  @Property(propertyName = "client.secret")
  private String clientSecret;

  @Property(propertyName = "client.logout.redirect.url")
  private String clientLogoutRedirectUrl;
}
