package com.walmart.move.nim.receiving.core.common;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.LoggingConstants.LOG_ERROR_GET_CCM_VALUE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.google.gson.*;
import com.walmart.atlas.argus.metrics.annotations.CaptureMethodMetric;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.config.ReportConfig;
import com.walmart.move.nim.receiving.core.config.TenantSpecificBackendConfig;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.core.model.ErrorResponse;
import com.walmart.move.nim.receiving.core.service.InstructionService;
import com.walmart.move.nim.receiving.core.service.PutOnHoldService;
import com.walmart.move.nim.receiving.core.service.PutawayService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
public class TenantSpecificConfigReader {
  private static final Logger log = LoggerFactory.getLogger(TenantSpecificConfigReader.class);
  @Autowired private ApplicationContext applicationContext;
  @ManagedConfiguration private TenantSpecificBackendConfig tenantSpecificBackendConfig;
  @ManagedConfiguration private ReportConfig reportConfig;
  @ManagedConfiguration private AppConfig appConfig;
  @Autowired private Gson gson;

  @Autowired MarketConfigHelper marketConfigHelper;

  /**
   * Get feature flags from CCM
   *
   * @param facilityNum
   * @return Map<String, Object>
   * @throws ReceivingException
   */
  public JsonObject getFeatureFlagsByFacility(String facilityNum) throws ReceivingException {

    try {
      JsonObject tenantSpecificFeatureFlags =
          new JsonParser().parse(tenantSpecificBackendConfig.getFeatureFlags()).getAsJsonObject();

      return Optional.ofNullable(tenantSpecificFeatureFlags.get(facilityNum))
          .map(JsonElement::getAsJsonObject)
          .orElseGet(
              () ->
                  Optional.ofNullable(
                          marketConfigHelper.getFeatureFlagsByFacility(
                              facilityNum, TenantContext.getFacilityCountryCode()))
                      .map(jsonString -> JsonParser.parseString(jsonString).getAsJsonObject())
                      .orElseGet(
                          () -> tenantSpecificFeatureFlags.get("default").getAsJsonObject()));

    } catch (Exception exception) {
      log.error(
          SPLUNK_ALERT + " error while reading tenant={}, may impact cell. StackTrace={}",
          facilityNum,
          ExceptionUtils.getStackTrace(exception));
      ErrorResponse errorResponse =
          ErrorResponse.builder()
              .errorMessage(ReceivingException.TENANT_CONFIG_ERROR)
              .errorCode(ReceivingException.RECEIVE_CONTAINER_ERROR_CODE)
              .errorKey(ExceptionCodes.TENANT_CONFIG_ERROR)
              .build();
      throw ReceivingException.builder()
          .httpStatus(HttpStatus.CONFLICT)
          .errorResponse(errorResponse)
          .build();
    }
  }

  /**
   * Fetch the recipient email id list from CCM for a facility number
   *
   * @param facilityNum
   * @return
   * @throws ReceivingException
   */
  public List<String> getEmailIdListByTenant(String facilityNum) throws ReceivingException {
    log.info("Fetching email id list corresponding to facility number: {}", facilityNum);

    try {
      JsonObject tenantSpecificDetails =
          new JsonParser().parse(reportConfig.getTenantOpsEmailRecipients()).getAsJsonObject();

      JsonArray emailIdJsonArray =
          tenantSpecificDetails.get(facilityNum) == null
              ? tenantSpecificDetails
                  .get(ReceivingConstants.DEFAULT_NODE)
                  .getAsJsonObject()
                  .getAsJsonArray("emailIds")
              : tenantSpecificDetails.get(facilityNum).getAsJsonObject().getAsJsonArray("emailIds");

      List<String> emailsIdList = new ArrayList<>();

      for (int i = 0; i < emailIdJsonArray.size(); i++) {
        emailsIdList.add(emailIdJsonArray.get(i).getAsString());
      }
      log.info(
          "Email id list corresponding to facility number: {} is: {}", facilityNum, emailsIdList);

      return emailsIdList;
    } catch (Exception exception) {
      log.error(ExceptionUtils.getStackTrace(exception));
      throw new ReceivingException(
          ReceivingException.TENANT_CONFIG_ERROR,
          HttpStatus.CONFLICT,
          ReceivingException.RECEIVE_CONTAINER_ERROR_CODE);
    }
  }

  /**
   * Get the Bean for PutawayService Please use TenantContext.getFacilityNum().toString()
   *
   * @param facilityNum
   * @return PutawayService
   * @throws ReceivingException
   */
  public PutawayService getPutawayServiceByFacility(String facilityNum) throws ReceivingException {
    final String beanName =
        getFeatureFlagsByFacility(facilityNum).get(PUTAWAY_HANDLER).getAsString();
    log.info("PutawayService bean instance is [{} for {}]", beanName, facilityNum);
    return (PutawayService) applicationContext.getBean(beanName);
  }

  /**
   * @param facilityNum
   * @return PutOnHoldService
   * @throws ReceivingException
   */
  public PutOnHoldService getPutOnHoldServiceByFacility(String facilityNum)
      throws ReceivingException {
    JsonObject featureFlags = getFeatureFlagsByFacility(facilityNum);
    log.info("Tenant specific featureFlags :{}", featureFlags);

    return (PutOnHoldService)
        applicationContext.getBean(
            featureFlags.get(ReceivingConstants.PUT_ON_HOLD_SERVICE).getAsString());
  }

  /**
   * Gets market specific delivery event processor bean based on CCM config.
   *
   * @param facilityNum the facility num
   * @return the delivery event processor
   * @throws ReceivingException the receiving exception
   */
  public EventProcessor getDeliveryEventProcessor(String facilityNum) throws ReceivingException {
    JsonObject tenantData = getFeatureFlagsByFacility(facilityNum);
    final String beanName = tenantData.get(DELIVERY_EVENT_HANDLER).getAsString();
    log.info(
        "Config BeanName={} for key={} tenant={}", beanName, DELIVERY_EVENT_HANDLER, facilityNum);
    EventProcessor eventProcessor = (EventProcessor) applicationContext.getBean(beanName);

    if (eventProcessor == null) {
      throw new ReceivingInternalException(
          ExceptionCodes.CONFIGURATION_ERROR,
          String.format(ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, facilityNum));
    }

    return eventProcessor;
  }

  public EventProcessor getDeliveryStatusEventProcessor(String facilityNum)
      throws ReceivingException {
    JsonObject tenantData = getFeatureFlagsByFacility(facilityNum);
    final String beanName = tenantData.get(DELIVERY_COMPLETE_EVENT_HANDLER).getAsString();
    log.info(
        "Config BeanName={} for key={} tenant={}",
        beanName,
        DELIVERY_COMPLETE_EVENT_HANDLER,
        facilityNum);
    EventProcessor eventProcessor = (EventProcessor) applicationContext.getBean(beanName);

    if (eventProcessor == null) {
      throw new ReceivingInternalException(
          ExceptionCodes.CONFIGURATION_ERROR,
          String.format(ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, facilityNum));
    }

    return eventProcessor;
  }

  /**
   * checks if the POConfirmation flag is enabled or not
   *
   * @param facilityNum
   * @return
   * @throws ReceivingException
   */
  public boolean isPoConfirmationFlagEnabled(Integer facilityNum) throws ReceivingException {
    JsonObject featureFlagsByFacility = getFeatureFlagsByFacility(facilityNum.toString());
    JsonElement poConfirmationEnabledJsonElement =
        featureFlagsByFacility.get("poConfirmationEnabled");

    return poConfirmationEnabledJsonElement != null
        && poConfirmationEnabledJsonElement.getAsBoolean();
  }

  /**
   * checks if the tclInfoOutboxKafkaPublishEnabled flag is enabled or not
   *
   * @param facilityNum
   * @return
   * @throws ReceivingException
   */
  public boolean isTCLInfoOutboxKafkaPublishEnabled(Integer facilityNum) throws ReceivingException {
    JsonObject featureFlagsByFacility = getFeatureFlagsByFacility(facilityNum.toString());
    JsonElement poConfirmationEnabledJsonElement =
        featureFlagsByFacility.get("tclInfoOutboxKafkaPublishEnabled");

    return poConfirmationEnabledJsonElement != null
        && poConfirmationEnabledJsonElement.getAsBoolean();
  }

  /**
   * checks if the divertInfoOutboxKafkaPublishEnabled flag is enabled or not
   *
   * @param facilityNum
   * @return
   * @throws ReceivingException
   */
  public boolean isDivertInfoOutboxKafkaPublishEnabled(Integer facilityNum)
      throws ReceivingException {
    JsonObject featureFlagsByFacility = getFeatureFlagsByFacility(facilityNum.toString());
    JsonElement poConfirmationEnabledJsonElement =
        featureFlagsByFacility.get("divertInfoOutboxKafkaPublishEnabled");

    return poConfirmationEnabledJsonElement != null
        && poConfirmationEnabledJsonElement.getAsBoolean();
  }

  /**
   * checks if the unloadCompleteOutboxKafkaPublishEnabled flag is enabled or not
   *
   * @param facilityNum
   * @return
   * @throws ReceivingException
   */
  public boolean isUnloadCompleteOutboxKafkaPublishEnabled(Integer facilityNum)
      throws ReceivingException {
    JsonObject featureFlagsByFacility = getFeatureFlagsByFacility(facilityNum.toString());
    JsonElement poConfirmationEnabledJsonElement =
        featureFlagsByFacility.get("unloadCompleteOutboxKafkaPublishEnabled");

    return poConfirmationEnabledJsonElement != null
        && poConfirmationEnabledJsonElement.getAsBoolean();
  }

  /**
   * checks if the FBQ is required for container build request
   *
   * @param facilityNum
   * @return
   * @throws ReceivingException
   */
  public boolean useFbqInCbr(Integer facilityNum) throws ReceivingException {
    JsonObject featureFlagsByFacility = getFeatureFlagsByFacility(facilityNum.toString());
    JsonElement useFbqInCbr = featureFlagsByFacility.get("useFbqInCbr");

    return useFbqInCbr != null && useFbqInCbr.getAsBoolean();
  }

  /**
   * Check if the deliveryItemOverrideEnabled flag is enabled or not
   *
   * @param facilityNum
   * @return boolean
   * @throws ReceivingException
   */
  public boolean isDeliveryItemOverrideEnabled(Integer facilityNum) throws ReceivingException {
    JsonObject featureFlagsByFacility = getFeatureFlagsByFacility(facilityNum.toString());
    JsonElement deliveryItemOverrideEnabled =
        featureFlagsByFacility.get("deliveryItemOverrideEnabled");

    return deliveryItemOverrideEnabled != null && deliveryItemOverrideEnabled.getAsBoolean();
  }

  /**
   * Check if showRotateDateOnPrintLabel flag is enabled or not
   *
   * @param facilityNum
   * @return
   * @throws ReceivingException
   */
  public boolean isShowRotateDateOnPrintLabelEnabled(Integer facilityNum)
      throws ReceivingException {
    JsonObject featureFlagsByFacility = getFeatureFlagsByFacility(facilityNum.toString());
    JsonElement showRotateDateOnPrintLabelEnabled =
        featureFlagsByFacility.get("showRotateDateOnPrintLabel");

    return showRotateDateOnPrintLabelEnabled != null
        && showRotateDateOnPrintLabelEnabled.getAsBoolean();
  }

  public boolean isFeatureFlagEnabled(String featureFlag) {
    String facilityNum = DEFAULT_NODE;
    if (getFacilityNum() != null) facilityNum = getFacilityNum().toString();
    JsonObject featureFlagsByFacility = null;
    try {
      featureFlagsByFacility = getFeatureFlagsByFacility(facilityNum);
    } catch (ReceivingException e) {
      log.error("Unable to get tenant info for {} for featureflag {}", facilityNum, featureFlag);
      return false;
    }
    JsonElement featureFlagElement = featureFlagsByFacility.get(featureFlag);
    return nonNull(featureFlagElement) && featureFlagElement.getAsBoolean();
  }

  public boolean isFeatureFlagEnabled(String featureFlag, Integer facilityNum) {
    JsonObject featureFlagsByFacility = null;
    try {
      featureFlagsByFacility = getFeatureFlagsByFacility(facilityNum.toString());
    } catch (ReceivingException e) {
      return false;
    }
    JsonElement featureFlagElement = featureFlagsByFacility.get(featureFlag);
    return nonNull(featureFlagElement) && featureFlagElement.getAsBoolean();
  }

  /**
   * Facility Number list for a feature
   *
   * @return
   */
  public List<Integer> getEnabledFacilityNumListForFeature(String feature) {
    JsonObject tenantSpecificFeatureFlags =
        new JsonParser().parse(tenantSpecificBackendConfig.getFeatureFlags()).getAsJsonObject();

    return tenantSpecificFeatureFlags
        .keySet()
        .stream()
        .filter(StringUtils::isNumeric)
        .filter(o -> isFeatureFlagEnabled(feature, Integer.parseInt(o)))
        .map(Integer::parseInt)
        .distinct()
        .collect(Collectors.toList());
  }

  /**
   * Returns facility Num list for which is not present in given list
   *
   * @param facilityNumListPresent
   * @param feature
   * @return
   */
  public List<Integer> getMissingFacilityNumList(
      List<Integer> facilityNumListPresent, String feature) {
    return getEnabledFacilityNumListForFeature(feature)
        .stream()
        .filter(o -> !facilityNumListPresent.contains(o))
        .collect(Collectors.toList());
  }

  /**
   * @param facilityNum
   * @return PutOnHoldService
   * @throws ReceivingException
   */
  @CaptureMethodMetric
  public InstructionService getInstructionServiceByFacility(String facilityNum)
      throws ReceivingException {
    JsonObject featureFlags = getFeatureFlagsByFacility(facilityNum);
    JsonElement instructionServiceFlagElement =
        featureFlags.get(ReceivingConstants.INSTRUCTION_SERVICE);

    if (Objects.isNull(instructionServiceFlagElement)) {
      return (InstructionService)
          applicationContext.getBean(ReceivingConstants.DEFAULT_INSTRUCTION_SERVICE);
    } else {
      return (InstructionService)
          applicationContext.getBean(instructionServiceFlagElement.getAsString());
    }
  }

  public boolean isBOLWeightCheckEnabled(Integer facilityNum) throws ReceivingException {
    JsonObject featureFlagsByFacility = getFeatureFlagsByFacility(facilityNum.toString());
    JsonElement bolWeightCheckEnabled =
        featureFlagsByFacility.get(ReceivingConstants.BOL_WEIGHT_CHECK_ENABLED);

    return nonNull(bolWeightCheckEnabled) && bolWeightCheckEnabled.getAsBoolean();
  }

  /**
   * This method will return the tenant specific bean. The defaultBean is mandatory , incase bean is
   * not configured for a specific tenant , then it will fallback to default bean
   *
   * @param facilityNum
   * @param ccmKey
   * @param type
   * @param <T>
   * @return <T></T>
   */
  public <T> T getConfiguredInstance(String facilityNum, String ccmKey, Class<T> type) {
    JsonObject tenantData = getCCMTenantConfig(facilityNum);
    log.debug("Get bean={} in tenant={}, specific configs={}", ccmKey, facilityNum, tenantData);

    String beanName =
        nonNull(tenantData)
                && nonNull(tenantData.get(ccmKey))
                && isNotBlank(tenantData.get(ccmKey).getAsString())
            ? tenantData.get(ccmKey).getAsString()
            : getCCMTenantConfig(DEFAULT_NODE).get(ccmKey).getAsString();

    T instance = (T) getBean(beanName, facilityNum);
    log.info("Selected instance for bean is={}, for tenant = {} ", instance, facilityNum);
    return instance;
  }

  public <T> T getConfiguredInstance(
      String facilityNum, String ccmKey, String defaultBeanName, Class<T> type) {
    JsonObject tenantData = getCCMTenantConfig(facilityNum);
    log.debug("Get bean={} in tenant={}, specific configs={}", ccmKey, facilityNum, tenantData);

    String beanName =
        nonNull(tenantData)
                && nonNull(tenantData.get(ccmKey))
                && isNotBlank(tenantData.get(ccmKey).getAsString())
            ? tenantData.get(ccmKey).getAsString()
            : nonNull(getCCMTenantConfig(DEFAULT_NODE).get(ccmKey))
                ? getCCMTenantConfig(DEFAULT_NODE).get(ccmKey).getAsString()
                : defaultBeanName;

    T instance = null;
    try {
      instance = (T) getBean(beanName, facilityNum);
    } catch (Exception exception) {
      instance = getDefaultInstance(defaultBeanName, type);
    }
    log.info("Selected instance for bean is={}, for tenant = {} ", instance, facilityNum);
    return instance;
  }

  private <T> T getDefaultInstance(String beanName, Class<T> type) {

    try {
      return (T) applicationContext.getBean(beanName);
    } catch (BeansException beansException) {
      log.error("No bean specified for beanName={} ", beanName);
      throw new ReceivingInternalException(
          ExceptionCodes.CONFIGURATION_ERROR,
          String.format(ReceivingConstants.INVALID_BEAN_NAME_ERROR_MSG, beanName));
    }
  }

  public int getPrintLabelTtlHrs(Integer facilityNum, String ccmKey) {
    try {
      return getCcmConfigValue(facilityNum, ccmKey).getAsInt();
    } catch (Exception e) {
      log.error(LOG_ERROR_GET_CCM_VALUE, facilityNum, ccmKey);
      return ReceivingConstants.PRINT_LABEL_DEFAULT_TTL;
    }
  }

  public long getTCLMaxValPerDelivery(String ccmKey) {
    try {
      return getCcmConfigValue(getFacilityNum(), ccmKey).getAsLong();
    } catch (Exception e) {
      log.error(LOG_ERROR_GET_CCM_VALUE, getFacilityNum(), ccmKey);
      return ReceivingConstants.TCL_MAX_PER_DELIVERY;
    }
  }

  public long getTCLMinValPerDelivery(String ccmKey) {
    try {
      return getCcmConfigValue(getFacilityNum(), ccmKey).getAsLong();
    } catch (Exception e) {
      log.error(LOG_ERROR_GET_CCM_VALUE, getFacilityNum(), ccmKey);
      return ReceivingConstants.TCL_MIN_PER_DELIVERY;
    }
  }

  public int getMabdNoOfDays(String ccmKey) {
    try {
      return getCcmConfigValue(getFacilityNum(), ccmKey).getAsInt();
    } catch (Exception e) {
      log.error(LOG_ERROR_GET_CCM_VALUE, getFacilityNum(), ccmKey);
      return ReceivingConstants.MABD_DEFAULT_NO_OF_DAYS;
    }
  }

  public Float getWhiteWoodPalletMaxWeight(Integer facilityNum, String ccmKey) {
    try {
      return getCcmConfigValue(facilityNum, ccmKey).getAsFloat();
    } catch (Exception e) {
      log.error(LOG_ERROR_GET_CCM_VALUE, facilityNum, ccmKey);
      return ReceivingConstants.MAX_WHITE_WOOD_PALLET_WEIGHT;
    }
  }

  public String getSmBaseUrl(Integer facilityNum, String facilityNumberOverride) {
    try {
      return getCcmConfigValue(facilityNum, SM_BASE_URL_KEY).getAsString();
    } catch (Exception e) {
      final String smBaseUrlDefault =
          String.format(SM_BASE_URL, facilityNumberOverride, facilityNumberOverride);
      log.warn(
          "warn getting CCM for tenant={}, ccmKey={}, facilityNumberOverride={}, returning smBaseUrlDefault={}",
          facilityNum,
          SM_BASE_URL_KEY,
          facilityNumberOverride,
          smBaseUrlDefault);
      return smBaseUrlDefault;
    }
  }

  /**
   * Gets CCM provided value for given tenant and ccmKey if available as JsonElement. Else if
   * TenantConfig value is null or empty returns Default Config's value as JsonElement.
   *
   * <p>This is generic method returning JsonElement for given inputs User may need to convert
   * desired type as example: getCcmConfigValue(facilityNum, ccmKey).getAs<T>
   * jsonElement.getAsBoolean(), jsonElement.getAsInt()
   *
   * @param facilityNumString a String
   * @param ccmKey as String
   * @return ccmValue as JsonElement object
   */
  public JsonElement getCcmConfigValue(String facilityNumString, String ccmKey) {
    final JsonObject ccmTenantConfig = getCCMTenantConfig(facilityNumString);
    log.debug(
        "CCM for tenant={}, key={}, ccmTenantConfig={}",
        facilityNumString,
        ccmKey,
        ccmTenantConfig);
    final JsonElement ccmConfigValue =
        nonNull(ccmTenantConfig)
                && nonNull(ccmTenantConfig.get(ccmKey))
                // Ignores blank value against Tenant's specific Key
                && isNotBlank(ccmTenantConfig.get(ccmKey).getAsString())
            ? ccmTenantConfig.get(ccmKey)
            : getCCMTenantConfig(DEFAULT_NODE).get(ccmKey);
    log.debug("CCM for tenant={}, key={}, value={}", facilityNumString, ccmKey, ccmConfigValue);
    return ccmConfigValue;
  }

  public JsonElement getCcmConfigValueAsJson(String facilityNumString, String ccmKey) {
    final JsonObject ccmTenantConfig = getCCMTenantConfig(facilityNumString);
    log.debug(
        "CCM for tenant={}, key={}, ccmTenantConfig={}",
        facilityNumString,
        ccmKey,
        ccmTenantConfig);
    final JsonElement ccmConfigValue =
        nonNull(ccmTenantConfig) && nonNull(ccmTenantConfig.get(ccmKey))
            ? ccmTenantConfig.get(ccmKey)
            : getCCMTenantConfig(DEFAULT_NODE).get(ccmKey);
    log.debug("CCM for tenant={}, key={}, value={}", facilityNumString, ccmKey, ccmConfigValue);
    return ccmConfigValue;
  }

  public String getCcmValue(Integer facilityNumString, String ccmKey, String defaultValue) {
    try {
      return getCcmConfigValue(facilityNumString.toString(), ccmKey).getAsString();
    } catch (Exception e) {
      log.warn(
          "returning default value={} as ccm config key={} missing for tenant={}",
          defaultValue,
          ccmKey,
          facilityNumString);
      return defaultValue;
    }
  }
  /**
   * Gets CCM provided value for given tenant and ccmKey if available.
   *
   * @param facilityNum
   * @param ccmKey
   * @return ccmValue as JsonElement
   */
  public JsonElement getCcmConfigValue(Integer facilityNum, String ccmKey) {
    return getCcmConfigValue(facilityNum.toString(), ccmKey);
  }
  /**
   * To use generic method and convert to boolean using getAsBoolean()
   *
   * @see
   *     <p>{@link #getCcmConfigValue(String, String)}
   */
  public boolean getConfiguredFeatureFlag(String facilityNum, String ccmKey) {
    JsonObject tenantData = getCCMTenantConfig(facilityNum);
    log.debug("Tenant specific configs :{}", tenantData);
    boolean featureFlagValue =
        nonNull(tenantData)
                && nonNull(tenantData.get(ccmKey))
                && isNotBlank(tenantData.get(ccmKey).getAsString())
            ? tenantData.get(ccmKey).getAsBoolean()
            : getCCMTenantConfig(DEFAULT_NODE).get(ccmKey).getAsBoolean();
    log.info("[{}={} for {}]", ccmKey, featureFlagValue, facilityNum);
    return featureFlagValue;
  }

  /**
   * if no feature flag configured in ccm
   *
   * @param facilityNum
   * @param ccmKey
   * @param defaultValue
   * @return
   */
  public boolean getConfiguredFeatureFlag(String facilityNum, String ccmKey, boolean defaultValue) {
    try {
      return getConfiguredFeatureFlag(facilityNum, ccmKey);
    } catch (Exception e) {
      log.warn(
          "returning defaultValue={} as key={} is missing in ccm for tenant={}",
          defaultValue,
          ccmKey,
          facilityNum);
      return defaultValue;
    }
  }

  public boolean getConfiguredFeatureFlag(String ccmKey) {
    String facilityNum = DEFAULT_NODE;
    if (getFacilityNum() != null) facilityNum = getFacilityNum().toString();
    return getConfiguredFeatureFlag(facilityNum, ccmKey, false);
  }

  private JsonObject getCCMTenantConfig(String facilityNum) {
    try {
      return getFeatureFlagsByFacility(facilityNum);
    } catch (ReceivingException e) {
      // Parent method has logged it . So not logging here
      return null;
    }
  }

  private Object getBean(String beanName, String facilityNum) {
    try {
      return this.applicationContext.getBean(beanName);
    } catch (BeansException beansException) {
      log.error("No bean specified for beanName={} ", beanName);
      throw new ReceivingInternalException(
          ExceptionCodes.CONFIGURATION_ERROR,
          String.format(ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, facilityNum));
    }
  }

  public boolean getProcessExpiry() throws ReceivingException {
    JsonObject featureFlagsByFacility = getFeatureFlagsByFacility(getFacilityNum().toString());
    log.info("Tenant specific configs :{}", featureFlagsByFacility);

    JsonElement processExpiryEnabled =
        featureFlagsByFacility.get(ReceivingConstants.PROCESS_EXPIRY_ENALBED);

    return nonNull(processExpiryEnabled) && processExpiryEnabled.getAsBoolean();
  }

  public boolean isPrintingAndroidComponentEnabled() {
    return isFeatureFlagEnabled(ReceivingConstants.PRINTING_ANDROID_ENABLED);
  }

  /**
   * Get DC specific timezone according to facility number other wise returns as UTC as Default.
   *
   * @param facilityNum
   */
  public String getDCTimeZone(Integer facilityNum) {
    JsonObject featureFlagsByFacility = null;
    try {
      featureFlagsByFacility = getFeatureFlagsByFacility(facilityNum.toString());
    } catch (ReceivingException e) {
      log.error("error in getting DC time zone returning empty as timezone");
      return UTC_TIME_ZONE;
    }
    JsonElement dcTimeZone = featureFlagsByFacility.get(DC_TIMEZONE);

    return nonNull(dcTimeZone) ? dcTimeZone.getAsString() : "";
  }

  public boolean isOutboxEnabledForDeliveryEvents() {
    return getConfiguredFeatureFlag(
        String.valueOf(getFacilityNum()),
        ReceivingConstants.OUTBOX_DELIVERY_EVENT_ENABLED,
        Boolean.FALSE);
  }

  public String getOutboxDeliveryEventServiceName() {
    Integer facilityNum = getFacilityNum();
    return getCcmValue(facilityNum, OUTBOX_DELIVERY_EVENT_SERVICE_NAME, null);
  }

  public boolean isOutboxEnabledForVendorDimensionEvents() {
    return getConfiguredFeatureFlag(
            String.valueOf(getFacilityNum()),
            ReceivingConstants.OUTBOX_VENDOR_DIMENSION_EVENT_ENABLED,
            Boolean.FALSE);
  }
  public String getOutboxDecantVendorDimensionEventServiceName() {
    Integer facilityNum = getFacilityNum();
    return getCcmValue(facilityNum, OUTBOX_VENDOR_DIMENSION_EVENT_SERVICE_NAME, null);
  }

  public boolean isReceiptPostingDisabled(String baseDivisionCode) {
    StringBuilder receiptPostingSB =
        new StringBuilder()
            .append(IS_RECEIPT_POSTING_DISABLED)
            .append("|")
            .append(baseDivisionCode);
    return getConfiguredFeatureFlag(
        String.valueOf(getFacilityNum()), receiptPostingSB.toString(), Boolean.FALSE);
  }

  public String overwriteFacilityInfo() {
    Integer facilityNum = getFacilityNum();
    return getCcmValue(facilityNum, OVERWRITE_FACILITY_INFO, String.valueOf(facilityNum));
  }

  /**
   * Get DC specific destination for non-Con docktag
   *
   * @param facilityNum
   */
  public String getDCSpecificMoveDestinationForNonConDockTag(Integer facilityNum) {
    JsonObject featureFlagsByFacility = null;
    try {
      featureFlagsByFacility = getFeatureFlagsByFacility(facilityNum.toString());
    } catch (ReceivingException e) {
      log.error("error in getting destination for non con docktag");
      return "";
    }
    JsonElement toLocation =
        featureFlagsByFacility.get(ReceivingConstants.MOVE_DEST_NON_CON_DOCK_TAG);

    return nonNull(toLocation) ? toLocation.getAsString() : "";
  }

  /**
   * Get DC specific floor line destination for docktag
   *
   * @param facilityNum facility number
   */
  public String getDCSpecificMoveFloorLineDestinationForNonConDockTag(Integer facilityNum) {
    JsonObject featureFlagsByFacility = null;
    try {
      featureFlagsByFacility = getFeatureFlagsByFacility(facilityNum.toString());
    } catch (ReceivingException e) {
      log.error("error in getting floor line destination for docktag");
      return "";
    }
    JsonElement toLocation =
        featureFlagsByFacility.get(ReceivingConstants.MOVE_FLOOR_LINE_DEST_NON_CON_DOCK_TAG);

    return nonNull(toLocation) ? toLocation.getAsString() : "";
  }

  /**
   * Get DC specific batch size for round robin PO distibution
   *
   * @param facilityNum facility number
   */
  public Long getDCSpecificPODistributionBatchSize(Integer facilityNum) {
    JsonObject featureFlagsByFacility = null;
    try {
      featureFlagsByFacility = getFeatureFlagsByFacility(facilityNum.toString());
    } catch (ReceivingException e) {
      log.error("error in getting batch threshold");
      // TODO: decide default threshold
      return null;
    }
    JsonElement threshold = featureFlagsByFacility.get(ROUND_ROBIN_BATCH_THRESHOLD);

    return nonNull(threshold) ? threshold.getAsLong() : null;
  }

  /**
   * This is a generic method to check for a particular facility is enabled or not by receiving-api
   * in a particular production cell.
   *
   * @param facilityNum
   * @return
   */
  public Boolean isFacilityEnabled(Integer facilityNum) {
    JsonObject tenantSpecificFeatureFlags =
        new JsonParser().parse(tenantSpecificBackendConfig.getFeatureFlags()).getAsJsonObject();
    List<Integer> tenants =
        tenantSpecificFeatureFlags
            .keySet()
            .stream()
            .filter(StringUtils::isNumeric)
            .map(Integer::parseInt)
            .distinct()
            .collect(Collectors.toList());
    if (!CollectionUtils.isEmpty(tenants) && tenants.contains(facilityNum)) {
      return Boolean.TRUE;
    }
    return Boolean.FALSE;
  }

  public String getInventoryBaseUrlByFacility() {
    Integer facilityNumber = getFacilityNum();
    String inventoryBaseUrlMap = appConfig.getMultiTenantInventoryBaseUrl();
    if ((ObjectUtils.allNotNull(facilityNumber, inventoryBaseUrlMap))) {
      JsonObject tenantBasedInventoryBaseUrlMapJson =
          gson.fromJson(inventoryBaseUrlMap, JsonObject.class);
      JsonElement inventoryBaseUrlJsonElement =
          tenantBasedInventoryBaseUrlMapJson.get(facilityNumber.toString());
      if (nonNull(inventoryBaseUrlJsonElement)) {
        return inventoryBaseUrlJsonElement.getAsString();
      }
    }
    return appConfig.getInventoryBaseUrl(); // Not Changed
  }
  /**
   * Get DC specific subcenterId if it's passed from client header otherwise returns CCM Default
   * value. Note: If any market has No subcenter functionality, subcenterId will be Default to null.
   */
  public String getSubcenterId() {
    return nonNull(TenantContext.getSubcenterId()) && TenantContext.getSubcenterId() != 0
        ? TenantContext.getSubcenterId().toString()
        : getCcmValue(TenantContext.getFacilityNum(), ReceivingConstants.SUBCENTER_ID_HEADER, null);
  }

  /**
   * Get DC specific orgUnitId(OSS) if it's passed from client header otherwise returns CCM Default
   * value. Note: If any market has No OSS functionality, orgUnitId will be Default to null.
   */
  public String getOrgUnitId() {
    return nonNull(TenantContext.getOrgUnitId()) && TenantContext.getOrgUnitId() != 0
        ? TenantContext.getOrgUnitId().toString()
        : getCcmValue(TenantContext.getFacilityNum(), SUBCENTER_ID_HEADER, null);
  }

  public List<String> getScalingQtyEnabledForReplenishmentTypes() {
    JsonArray replenishmentCodes =
        getCcmConfigValueAsJson(
                TenantContext.getFacilityNum().toString(),
                SCALING_QTY_ENABLED_FOR_REPLENISHMENT_TYPES)
            .getAsJsonArray();
    List<String> scalingEnabledFor = new ArrayList<>();
    replenishmentCodes.forEach(
        replenishmentCode -> scalingEnabledFor.add(replenishmentCode.getAsString()));
    return scalingEnabledFor;
  }

  public double getCaseWeightCheckConfig(String ccmKey) {
    try {
      return getCcmConfigValue(getFacilityNum(), ccmKey).getAsDouble();
    } catch (Exception e) {
      log.error(LOG_ERROR_GET_CCM_VALUE, getFacilityNum(), ccmKey);
      if (ccmKey.equalsIgnoreCase(CASE_WEIGHT_MULTIPLIER)) return DEFAULT_CASE_WEIGHT_MULTIPLIER;
      return DEFAULT_CASE_WEIGHT_LOWER_LIMIT;
    }
  }

  public String getHawkEyeRoninUrlOrDefault(Supplier<String> defaultValueSupplier) {
    Integer facilityNumber = TenantContext.getFacilityNum();
    String baseUrlMap = appConfig.getMultiTenantHawkEyeRoninBaseUrl();
    if ((ObjectUtils.allNotNull(facilityNumber, baseUrlMap))) {
      JsonObject baseUrlMapJson = gson.fromJson(baseUrlMap, JsonObject.class);
      JsonElement baseUrlMapJsonElement = baseUrlMapJson.get(String.valueOf(facilityNumber));
      if (Objects.nonNull(baseUrlMapJsonElement)) {
        String baseUrl = baseUrlMapJsonElement.getAsString();
        log.info(
            "tenant: [{}] found in config map, hawkeye ronin base url: [{}]",
            facilityNumber,
            baseUrl);
        return baseUrl;
      }
    }

    String defaultValue = defaultValueSupplier.get();
    log.info(
        "tenant: [{}] not found in config map: [{}], providing default value with supplier: [{}]",
        facilityNumber,
        baseUrlMap,
        defaultValue);
    return defaultValue;
  }

  public String getLabellingServiceUrlOrDefault(
      Supplier<String> defaultValueSupplier, String headerFacilityNum) {
    Integer facilityNumber = TenantContext.getFacilityNum();
    if (facilityNumber == null && headerFacilityNum != null) {
      facilityNumber = Integer.valueOf(headerFacilityNum);
    }
    String baseUrlMap = appConfig.getMultiTenantLabellingBaseUrl();
    if ((ObjectUtils.allNotNull(facilityNumber, baseUrlMap))) {
      JsonObject baseUrlMapJson = gson.fromJson(baseUrlMap, JsonObject.class);
      JsonElement baseUrlMapJsonElement = baseUrlMapJson.get(String.valueOf(facilityNumber));
      if (Objects.nonNull(baseUrlMapJsonElement)) {
        String baseUrl = baseUrlMapJsonElement.getAsString();
        log.info(
            "tenant: [{}] found in config map, labelling base url: [{}]", facilityNumber, baseUrl);
        return baseUrl;
      }
    }

    String defaultValue = defaultValueSupplier.get();
    log.info(
        "tenant: [{}] not found in config map: [{}], providing default value for labelling URL with supplier: [{}]",
        facilityNumber,
        baseUrlMap,
        defaultValue);
    return defaultValue;
  }

  public List<String> getFreightSpecificType(Integer facilityNum) {
    List<String> freightTypeList = new ArrayList<>();
    try {
      JsonObject featureFlagsByFacility = getFeatureFlagsByFacility(facilityNum.toString());
      JsonElement frightType =
          featureFlagsByFacility.get(ReceivingConstants.ATLAS_FREIGHT_MIGRATED_TYPES);
      if (frightType != null && frightType.getAsString() != null) {
        freightTypeList = Arrays.asList(frightType.getAsString().split(","));
      }
    } catch (ReceivingException e) {
      log.error("error in getting freight type");
    }
    return freightTypeList;
  }

  /**
   * This method will fetch the configured values from CCM for allowing the slot entered by user for
   * DA automation Pallet Slotting into Symbiotic when slot selection done manually and not
   * automatically.
   *
   * @param facilityNum
   * @return
   */
  public List<String> allowedAutomationManuallyEnteredSlotsForDAConveyableItem(
      Integer facilityNum) {
    List<String> allowedAutomationSlots = Collections.emptyList();
    String allowedAutomationSlotStr =
        getCcmValue(
            facilityNum,
            ReceivingConstants.ALLOWED_AUTOMATION_MANUALLY_ENTERED_SLOTS_FOR_DA_CONVEYABLE_ITEM,
            EMPTY_STRING);
    if (StringUtils.isNotBlank(allowedAutomationSlotStr)) {
      allowedAutomationSlots = Arrays.asList(allowedAutomationSlotStr.split(","));
    }
    return allowedAutomationSlots;
  }

  /**
   * This method will fetch the configured values from CCM for flag sorter-contract-version used to
   * publish the lpn create message to Athena
   *
   * @param facilityNum
   * @return
   */
  public Integer getSorterContractVersion(Integer facilityNum) {
    Integer sorterContractVersion = 1;
    try {
      JsonObject featureFlagsByFacility = getFeatureFlagsByFacility(facilityNum.toString());
      JsonElement contractVersionFromConfig =
          featureFlagsByFacility.get(ReceivingConstants.SORTER_CONTRACT_VERSION);
      if (contractVersionFromConfig != null) {
        sorterContractVersion = Integer.parseInt(String.valueOf(contractVersionFromConfig));
        log.info(
            "Sorter Contract Version is :{},For facilityNum :{}",
            sorterContractVersion,
            facilityNum);
        return sorterContractVersion;
      }
    } catch (Exception e) {
      log.error("Error occurred while fetching sorter contract version :{}", e.getMessage());
    }
    return sorterContractVersion;
  }

  public List<String> getTenantConfigurationAsList(String ccmKey) {
    String ccmValue =
        getCcmConfigValue(TenantContext.getFacilityNum().toString(), ccmKey).getAsString();
    return Arrays.asList(ccmValue.split(","));
  }

  public int getPurchaseOrderPartitionSize(String ccmKey) {
    try {
      return getCcmConfigValue(getFacilityNum(), ccmKey).getAsInt();
    } catch (Exception e) {
      log.error(LOG_ERROR_GET_CCM_VALUE, getFacilityNum(), ccmKey);
      return PURCHASE_ORDER_PARTITION_SIZE;
    }
  }

  public boolean isOutboxEnabledForInventory() {
    return getConfiguredFeatureFlag(IS_OUTBOX_ENABLED_FOR_INVENTORY);
  }
}
