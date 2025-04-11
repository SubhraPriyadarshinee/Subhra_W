package com.walmart.move.nim.receiving.core.config;

import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "market.type")
@Setter
@Getter
public class MarketTenantConfig {
  private Map<String, Set<String>> sites;
}
