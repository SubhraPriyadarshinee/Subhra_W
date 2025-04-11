package com.walmart.move.nim.receiving.core.common;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
@Getter
public class MarketConfigHelper {
  @Autowired private MarketConfigReader marketConfigReader;

  @Autowired private Gson gson;

  Map<String, String> facilityMarketConfig = new HashMap<>();

  /**
   * Two separate config one is feature config for beans and one is for flags. This method merge
   * them into one.
   *
   * @param facilityNum
   * @param countryCode
   * @return
   */
  public String getFeatureFlagsByFacility(String facilityNum, String countryCode) {
    String sites = marketConfigReader.getSites(facilityNum, countryCode);
    if (facilityMarketConfig.containsKey(sites)) {
      return facilityMarketConfig.get(sites);
    }

    Map<String, Object> featureConfig =
        marketConfigReader.getFeatureConfig(facilityNum, countryCode);
    Map<String, Object> flagConfig = marketConfigReader.getFlagConfig(facilityNum, countryCode);

    Map<String, Object> featureFlagConfig = mergeMaps(featureConfig, flagConfig);
    if (!CollectionUtils.isEmpty(featureFlagConfig)) {
      String featureFlagConfigString = gson.toJson(mergeMaps(featureConfig, flagConfig));
      facilityMarketConfig.put(sites, featureFlagConfigString);
      return featureFlagConfigString;
    }
    return null;
  };

  public Map<String, Object> mergeMaps(Map<String, Object> map1, Map<String, Object> map2) {
    Map<String, Object> mergedMap = new HashMap<>();
    mergedMap.putAll(map1);
    mergedMap.putAll(map2);

    return mergedMap;
  }
}
