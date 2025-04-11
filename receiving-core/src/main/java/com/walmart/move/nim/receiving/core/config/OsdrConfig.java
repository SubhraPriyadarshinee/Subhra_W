package com.walmart.move.nim.receiving.core.config;

import io.strati.configuration.annotation.Configuration;
import io.strati.configuration.annotation.Property;
import lombok.Getter;

@Configuration(configName = "osdrConfig")
@Getter
public class OsdrConfig {
  @Property(propertyName = "osdr.config.specifications")
  private String specifications;

  @Property(propertyName = "osdr.config.scheduler.cron")
  private String schedulerCron;

  @Property(propertyName = "osdr.config.frequency.interval.in.minutes")
  private long frequencyIntervalInMinutes;

  @Property(propertyName = "osdr.config.page.size")
  private int pageSize;
}
