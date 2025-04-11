package com.walmart.move.nim.receiving.core.common;

import java.util.Map;

public interface ConfigReader {

  /**
   * This method provide the feature config for a facility and countryCOde.
   *
   * @param facilityNum
   * @param countryCode
   * @return
   */
  Map<String, Object> getFeatureConfig(String facilityNum, String countryCode);

  /**
   * This method provide the flag config for a facility and countryCOde.
   *
   * @param facilityNum
   * @param countryCode
   * @return
   */
  Map<String, Object> getFlagConfig(String facilityNum, String countryCode);

  /**
   * This method provide the bean for a facility, if not present then return default bean
   *
   * @param facilityNum
   * @param ccmKey
   * @param defaultBeanName
   * @return
   */
  Object getConfigInstance(String facilityNum, String ccmKey, String defaultBeanName);

  /**
   * This method provide the property for a facility, if not present then return default property
   *
   * @param facilityNum
   * @param ccmKey
   * @param defaultProperty
   * @return
   */
  String getConfigProperties(String facilityNum, String ccmKey, String defaultProperty);

  /**
   * This method provide the property for a facility, if not present then return default flag
   *
   * @param facilityNum
   * @param ccmKey
   * @param defaultFlag
   * @return
   */
  Boolean getConfigFlag(String facilityNum, String ccmKey, String defaultFlag);
}
