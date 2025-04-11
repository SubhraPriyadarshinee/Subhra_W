package com.walmart.move.nim.receiving.utils.common;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.DEFAULT_USER;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.USER_ID_HEADER_KEY;
import static java.util.Objects.isNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * this is the context for tenant calling any API this context can be used by further classes such
 * as jpa repos, jms publisher etc.
 *
 * @author a0s01qi
 */
public class TenantContext {
  private TenantContext() {}

  // tenantid is for MT persistence
  private static ThreadLocal<TenantData> applicationTenantContext = new ThreadLocal<>();

  public static Integer getFacilityNum() {
    TenantData tenantData = get();
    return tenantData == null ? null : tenantData.getFacilityNum();
  }

  public static void setFacilityNum(Integer facilityNum) {
    set().setFacilityNum(facilityNum);
  }

  public static Integer getSubcenterId() {
    TenantData tenantData = get();
    return tenantData == null ? null : tenantData.getSubcenterId();
  }

  public static Integer getOrgUnitId() {
    TenantData tenantData = get();
    return tenantData == null ? null : tenantData.getOrgUnitId();
  }

  public static void setSubcenterId(Integer subcenterId) {
    set().setSubcenterId(subcenterId);
  }

  public static void setOrgUnitId(Integer orgUnitId) {
    set().setOrgUnitId(orgUnitId);
  }

  public static String getFacilityCountryCode() {
    TenantData tenantData = get();
    return tenantData == null ? null : tenantData.getFacilityCountryCode();
  }

  public static void setFacilityCountryCode(String facilityCountryCode) {
    set().setFacilityCountryCode(facilityCountryCode);
  }

  public static void setCorrelationId(String correlationId) {
    set().setCorrelationId(correlationId);
  }

  public static void setMessageId(String messageId) {
    set().setMessageId(messageId);
  }

  public static void setMessageIdempotencyId(String messageIdempotencyId) {
    set().setMessageIdempotencyId(messageIdempotencyId);
  }

  /**
   * Get userId from context that MT Filter sets else default to RCV's Default user
   *
   * @return
   */
  public static String getUserId() {
    final TenantData data = get();
    if (isNull(data)
        || isNull(data.getAdditionalParams())
        || isNull(data.getAdditionalParams().get(USER_ID_HEADER_KEY))) {
      return DEFAULT_USER;
    }
    return data.getAdditionalParams().get(USER_ID_HEADER_KEY).toString();
  }

  public static String getMessageIdempotencyId() {
    TenantData tenantData = get();
    return tenantData == null ? null : tenantData.getMessageIdempotencyId();
  }

  public static String getCorrelationId() {
    TenantData tenantData = get();
    return tenantData == null ? null : tenantData.getCorrelationId();
  }

  public static String getMessageId() {
    TenantData tenantData = get();
    return tenantData == null ? null : tenantData.getMessageId();
  }

  public static String getEventType() {
    TenantData tenantData = get();
    return tenantData == null ? null : tenantData.getEventType();
  }

  public static void setEventType(String eventType) {
    set().setEventType(eventType);
  }

  public static TenantData get() {
    return applicationTenantContext.get();
  }

  public static void setAdditionalParams(String key, Object value) {
    Map<String, Object> additionalParams = get().getAdditionalParams();
    additionalParams = Objects.isNull(additionalParams) ? new HashMap<>() : additionalParams;
    additionalParams.put(key, value);
    get().setAdditionalParams(additionalParams);
  }

  public static Map<String, Object> getAdditionalParams() {
    return get() == null ? null : get().getAdditionalParams();
  }

  private static TenantData set() {
    TenantData tenantData = get();
    if (tenantData == null) {
      tenantData = new TenantData();
      applicationTenantContext.set(tenantData);
      return tenantData;
    } else {
      return tenantData;
    }
  }

  public static void clear() {
    applicationTenantContext.remove();
  }
}
