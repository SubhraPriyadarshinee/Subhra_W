package com.walmart.move.nim.receiving.core.config.app;

import com.walmart.move.nim.receiving.core.config.AppConfig;
import io.strati.configuration.annotation.ManagedConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class RestTemplateFactory {
  @ManagedConfiguration private AppConfig appConfig;

  @Qualifier("connectionPoolingRestTemplate")
  @Autowired(required = false)
  private RestTemplate connectionPoolingRestTemplate;

  @Qualifier("defaultRestTemplate")
  @Autowired(required = false)
  private RestTemplate defaultRestTemplate;

  public RestTemplate provideRestTemplate() {
    if (appConfig.getBasicConnectionManager() || appConfig.getConnectionPoolingEnabled()) {
      return connectionPoolingRestTemplate;
    } else {
      return defaultRestTemplate;
    }
  }
}
