package com.walmart.move.nim.receiving.core.config;

import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.utils.constants.PurgeEntityType;
import io.strati.configuration.annotation.Configuration;
import io.strati.configuration.annotation.Ignore;
import io.strati.configuration.annotation.PostInit;
import io.strati.configuration.annotation.PostRefresh;
import io.strati.configuration.annotation.Property;
import io.strati.configuration.context.ConfigurationContext;
import io.strati.configuration.listener.ChangeLog;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;

@Configuration(configName = "purgeConfig")
@Getter
public class PurgeConfig {

  @Property(propertyName = "disable.purge.for.entity")
  List<PurgeEntityType> disabledEntities;

  @Property(propertyName = "purge.job.run.every.x.minute")
  private int purgeJobRunEveryXMinute;

  @Property(propertyName = "retention.policy")
  @Getter(value = AccessLevel.NONE)
  private String retentionPolicy;

  @Property(propertyName = "batch.size")
  @Getter(value = AccessLevel.NONE)
  private String batchSize;

  @Ignore private Map<String, Integer> retentionPolicyMap;
  @Ignore private Map<String, Integer> batchSizeMap;

  /**
   * Post Init Life Cycle. e.g
   * https://gecgithub01.walmart.com/platform/scm-examples/blob/master/ccm-lab/config-change-annotated-listener/src/main/java/io/strati/configuration/samples/CartConfiguration.java
   *
   * @param configName
   * @param context
   */
  @PostInit
  public void postInit(String configName, ConfigurationContext context) {
    this.retentionPolicyMap = JacksonParser.convertJsonToObject(retentionPolicy, Map.class);
    this.batchSizeMap = JacksonParser.convertJsonToObject(batchSize, Map.class);
  }

  /**
   * Post Refresh Lifecycle e.g
   * https://gecgithub01.walmart.com/platform/scm-examples/blob/master/ccm-lab/config-change-annotated-listener/src/main/java/io/strati/configuration/samples/CartConfiguration.java
   *
   * @param configName
   * @param changes
   * @param context
   */
  @PostRefresh
  public void postRefresh(
      String configName, List<ChangeLog> changes, ConfigurationContext context) {
    this.retentionPolicyMap = JacksonParser.convertJsonToObject(retentionPolicy, Map.class);
    this.batchSizeMap = JacksonParser.convertJsonToObject(batchSize, Map.class);
  }
}
