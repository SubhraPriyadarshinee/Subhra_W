package com.walmart.move.nim.receiving.core.config;

import io.strati.configuration.annotation.Configuration;
import io.strati.configuration.annotation.Property;
import lombok.Getter;

@Configuration(configName = "commonDbConfig")
@Getter
public class CommonDbConfig {

  @Property(propertyName = "hikari.idleTimeout")
  private long hikariIdleTimeoutMs;

  @Property(propertyName = "hikari.minIdle")
  private int hikariMinIdle;

  @Property(propertyName = "hikari.MaxPoolSize")
  private int hikariMaxPoolSize;

  @Property(propertyName = "hibernate.generate_statistics")
  private boolean hibernateSQLStatsEnabled;

  @Property(propertyName = "hibernate.jdbc.batch_size")
  private Integer jdbcBatchSize;

  @Property(propertyName = "hikari.leakDetectionThreshold")
  private long hikariLeakDetectionThreshold;

  @Property(propertyName = "hikari.idleTimeout.secondary")
  private long hikariIdleTimeoutMsSecondary;

  @Property(propertyName = "hikari.minIdle.secondary")
  private int hikariMinIdleSecondary;

  @Property(propertyName = "hikari.MaxPoolSize.secondary")
  private int hikariMaxPoolSizeSecondary;

  @Property(propertyName = "hikari.leakDetectionThreshold.secondary")
  private long hikariLeakDetectionThresholdSecondary;
}
