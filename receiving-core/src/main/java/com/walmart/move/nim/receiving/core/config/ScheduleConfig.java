package com.walmart.move.nim.receiving.core.config;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.platform.SchedulerJsonConfig;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.Configuration;
import io.strati.configuration.annotation.Property;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;

@Configuration(configName = ReceivingConstants.SCHEDULER_CONFIG)
@Getter
public class ScheduleConfig {
  @Property(propertyName = "scheduler.yms.update.spec")
  private String ymsUpdateSpec;

  @Property(propertyName = "scheduler.rdc.label.generation.spec")
  private String rdcLabelGenerationSpec;

  public Set<Pair<String, Integer>> getSchedulerTenantConfig() {

    Gson gson = new Gson();
    List<SchedulerJsonConfig> configs =
        gson.fromJson(ymsUpdateSpec, new TypeToken<List<SchedulerJsonConfig>>() {}.getType());
    return configs
        .stream()
        .map(json -> new Pair<>(json.getFacilityCountryCode(), json.getFacilityNum()))
        .collect(Collectors.toSet());
  }

  public Set<Pair<String, Integer>> getSchedulerTenantConfig(String propertyValue) {

    Gson gson = new Gson();
    List<SchedulerJsonConfig> configs =
        gson.fromJson(propertyValue, new TypeToken<List<SchedulerJsonConfig>>() {}.getType());
    return configs
        .stream()
        .map(json -> new Pair<>(json.getFacilityCountryCode(), json.getFacilityNum()))
        .collect(Collectors.toSet());
  }
}
