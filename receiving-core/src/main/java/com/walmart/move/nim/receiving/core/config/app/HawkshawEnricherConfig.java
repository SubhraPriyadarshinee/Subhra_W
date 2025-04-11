package com.walmart.move.nim.receiving.core.config.app;

import static java.util.Objects.nonNull;

import com.walmart.move.nim.receiving.core.config.HawkshawConfig;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmartlabs.hawkshaw.clients.generic.enricher.HawkshawEnricher;
import com.walmartlabs.hawkshaw.clients.generic.util.HawkshawClientConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.Properties;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/** * This is HawkshawEnricher configurations */
@Component
public class HawkshawEnricherConfig {
  @ManagedConfiguration private HawkshawConfig hawkshawConfig;

  @Bean(ReceivingConstants.HAWKSHAW_ENRICHER_BEAN)
  public HawkshawEnricher createHawkshawEnricher() throws Exception {
    final Properties properties = new Properties();

    // Setting app id to default if config is null
    properties.setProperty(
        HawkshawClientConstants.PUBLISHER_ID,
        hawkshawConfig != null
            ? hawkshawConfig.getPublisherId()
            : ReceivingConstants.APP_NAME_VALUE);

    if (nonNull(hawkshawConfig)) {
      properties.setProperty(
          HawkshawClientConstants.SEQ_REGISTRY_URL, hawkshawConfig.getSequenceRegistryUrl());
      properties.setProperty(HawkshawClientConstants.APP_ID, hawkshawConfig.getAppId());
      properties.setProperty(HawkshawClientConstants.TENANT_ID, hawkshawConfig.getTenantId());
      properties.setProperty(
          HawkshawClientConstants.PRODUCER_RETRY_TRACKING,
          String.valueOf(hawkshawConfig.isRetryTracking()));
    }
    return new HawkshawEnricher(properties);
  }
}
