package com.walmart.move.nim.receiving.core.config.app;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.walmart.move.nim.receiving.core.config.ReportConfig;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.ReportingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Configuration;

/**
 * Used for maintaining the config which determines what statistics should be displayed
 *
 * @author sks0013
 */
@Configuration
public class TenantSpecificReportConfig {
  @ManagedConfiguration private ReportConfig reportConfig;

  /**
   * Facility Number list
   *
   * @return
   */
  public List<Integer> getFacilityNumList() {
    JsonObject reportSpecifications =
        new JsonParser().parse(reportConfig.getSpecifications()).getAsJsonObject();

    return reportSpecifications
        .keySet()
        .parallelStream()
        .filter(StringUtils::isNumeric)
        .map(Integer::parseInt)
        .distinct()
        .collect(Collectors.toList());
  }

  /**
   * Facility country code list
   *
   * @return
   */
  public Set<String> getFacilityCountryCodeList() {
    return Collections.singleton(
        ReceivingConstants
            .COUNTRY_CODE_US); // TODO Hardcoded for now. Change when country code is added to
    // backend config
  }

  public JsonObject getFeatureFlagsByFacility(String facilityNum) {

    JsonObject tenantSpecificFeatureFlags =
        new JsonParser().parse(reportConfig.getTenantConfig()).getAsJsonObject();

    return tenantSpecificFeatureFlags.get(facilityNum) == null
        ? tenantSpecificFeatureFlags.get("default").getAsJsonObject()
        : tenantSpecificFeatureFlags.get(facilityNum).getAsJsonObject();
  }

  public boolean isFeatureFlagEnabled(String featureFlag) {
    JsonObject featureFlagsByFacility =
        getFeatureFlagsByFacility(TenantContext.getFacilityNum().toString());
    JsonElement featureFlagElement = featureFlagsByFacility.get(featureFlag);
    return Objects.nonNull(featureFlagElement) && featureFlagElement.getAsBoolean();
  }

  /**
   * get timezone according to facilitynum
   *
   * @param facilityNum
   */
  public String getDCTimeZone(String facilityNum) {
    String timeZone;

    JsonObject reportSpecificFeatureFlags =
        new JsonParser().parse(reportConfig.getSpecifications()).getAsJsonObject();

    if (reportSpecificFeatureFlags.get(facilityNum) == null)
      timeZone =
          reportSpecificFeatureFlags
              .get("default")
              .getAsJsonObject()
              .get(ReportingConstants.DC_TIMEZONE)
              .getAsString();
    else
      timeZone =
          reportSpecificFeatureFlags
              .get(facilityNum)
              .getAsJsonObject()
              .get(ReportingConstants.DC_TIMEZONE)
              .getAsString();
    return timeZone;
  }
}
