package com.walmart.move.nim.receiving.data;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.APPLICATION_JSON;

import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;

public class MockHttpHeaders {
  public static String EVENT_TYPE = "eventType";

  public static HttpHeaders getHeaders() {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.set(ReceivingConstants.TENENT_FACLITYNUM, "32987");
    httpHeaders.set(ReceivingConstants.TENENT_COUNTRY_CODE, "us");
    httpHeaders.set(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.set(ReceivingConstants.CORRELATION_ID_HEADER_KEY, "1a2bc3d4");
    httpHeaders.set(ReceivingConstants.SECURITY_HEADER_KEY, "1");
    httpHeaders.add(ReceivingConstants.CONTENT_TYPE, "application/json");
    httpHeaders.add(REQUEST_ORIGINATOR, APP_NAME_VALUE);
    return httpHeaders;
  }

  public static HttpHeaders getRDCHeaders() {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.set(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.set(ReceivingConstants.TENENT_COUNTRY_CODE, "us");
    httpHeaders.set(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.set(ReceivingConstants.CORRELATION_ID_HEADER_KEY, "1a2bc3d4");
    httpHeaders.set(ReceivingConstants.SECURITY_HEADER_KEY, "1");
    httpHeaders.add(ReceivingConstants.CONTENT_TYPE, "application/json");
    httpHeaders.add(REQUEST_ORIGINATOR, APP_NAME_VALUE);
    return httpHeaders;
  }

  public static HttpHeaders getHeaders(String facilityNum, String countryCode) {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.set(ReceivingConstants.TENENT_FACLITYNUM, facilityNum);
    httpHeaders.set(ReceivingConstants.TENENT_COUNTRY_CODE, countryCode);
    httpHeaders.set(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.set(ReceivingConstants.CORRELATION_ID_HEADER_KEY, "1a2bc3d4");
    httpHeaders.set(ReceivingConstants.SECURITY_HEADER_KEY, "1");
    httpHeaders.add(ReceivingConstants.CONTENT_TYPE, "application/json");
    return httpHeaders;
  }

  public static HttpHeaders getUserIdHeader(String userId) {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.set(ReceivingConstants.USER_ID_HEADER_KEY, userId);
    httpHeaders.add(ReceivingConstants.CONTENT_TYPE, "application/json");
    return httpHeaders;
  }

  public static HttpHeaders getMoveEventInventoryHeaders() {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.set(ReceivingConstants.TENENT_FACLITYNUM, "32818");
    httpHeaders.set(ReceivingConstants.TENENT_COUNTRY_CODE, "us");
    httpHeaders.set(ReceivingConstants.JMS_USER_ID, "sysadmin");
    httpHeaders.set(ReceivingConstants.JMS_CORRELATION_ID, "1a2bc3d4");
    httpHeaders.set(ReceivingConstants.INVENTORY_EVENT, "moved");
    httpHeaders.set(EVENT_TYPE, ReceivingConstants.INVENTORY_ADJUSTMENT_EVENT_CONTAINER_UPDATED);
    httpHeaders.add(ReceivingConstants.CONTENT_TYPE, "application/json");
    httpHeaders.add(REQUEST_ORIGINATOR, APP_NAME_VALUE);
    return httpHeaders;
  }

  public static HttpHeaders getImportHeaders() {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.set(ReceivingConstants.TENENT_FACLITYNUM, "32888");
    httpHeaders.set(ReceivingConstants.TENENT_COUNTRY_CODE, "us");
    httpHeaders.set(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.set(ReceivingConstants.CORRELATION_ID_HEADER_KEY, "1a2bc3d4");
    httpHeaders.set(ReceivingConstants.SECURITY_HEADER_KEY, "1");
    httpHeaders.add(ReceivingConstants.CONTENT_TYPE, "application/json");
    return httpHeaders;
  }

  public static HttpHeaders getLoadedEventHeaders() {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.set(ReceivingConstants.TENENT_FACLITYNUM, "32818");
    httpHeaders.set(ReceivingConstants.TENENT_COUNTRY_CODE, "us");
    httpHeaders.set(ReceivingConstants.JMS_USER_ID, "sysadmin");
    httpHeaders.set(ReceivingConstants.JMS_CORRELATION_ID, "1a2bc3d4");
    httpHeaders.set(ReceivingConstants.INVENTORY_EVENT, "loaded");
    httpHeaders.set(EVENT_TYPE, ReceivingConstants.INVENTORY_ADJUSTMENT_EVENT_CONTAINER_UPDATED);
    httpHeaders.add(ReceivingConstants.CONTENT_TYPE, "application/json");
    httpHeaders.add(REQUEST_ORIGINATOR, APP_NAME_VALUE);
    return httpHeaders;
  }
  /**
   * Inventory headers with event as 'deleted'
   *
   * @return
   */
  public static HttpHeaders getDeleteEventInventoryHeaders() {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.set(ReceivingConstants.TENENT_FACLITYNUM, "32818");
    httpHeaders.set(ReceivingConstants.TENENT_COUNTRY_CODE, "us");
    httpHeaders.set(ReceivingConstants.JMS_USER_ID, "sysadmin");
    httpHeaders.set(ReceivingConstants.JMS_CORRELATION_ID, "1a2bc3d4");
    httpHeaders.set(ReceivingConstants.INVENTORY_EVENT, "deleted");
    httpHeaders.set(ReceivingConstants.FLOW_DESCRIPTOR, "adjustmentFlow");
    httpHeaders.set(
        ReceivingConstants.EVENT_TYPE,
        ReceivingConstants.INVENTORY_ADJUSTMENT_EVENT_CONTAINER_DELETED);
    httpHeaders.add(ReceivingConstants.CONTENT_TYPE, "application/json");
    httpHeaders.add(REQUEST_ORIGINATOR, APP_NAME_VALUE);
    return httpHeaders;
  }

  public static HttpHeaders getPickedEventHeaders() {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.set(ReceivingConstants.TENENT_FACLITYNUM, "32818");
    httpHeaders.set(ReceivingConstants.TENENT_COUNTRY_CODE, "us");
    httpHeaders.set(ReceivingConstants.JMS_USER_ID, "sysadmin");
    httpHeaders.set(ReceivingConstants.JMS_CORRELATION_ID, "1a2bc3d4");
    httpHeaders.set(ReceivingConstants.INVENTORY_EVENT, "picked");
    httpHeaders.set(EVENT_TYPE, ReceivingConstants.INVENTORY_ADJUSTMENT_EVENT_CONTAINER_UPDATED);
    httpHeaders.add(ReceivingConstants.CONTENT_TYPE, "application/json");
    httpHeaders.add(REQUEST_ORIGINATOR, "OF-SYS");
    return httpHeaders;
  }

  public static HttpHeaders getInventoryContainerDetailsHeaders() {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.set(ReceivingConstants.TENENT_FACLITYNUM, "32679");
    httpHeaders.set(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.set(USER_ID_HEADER_KEY, "sysadmin");
    httpHeaders.add(CONTENT_TYPE, APPLICATION_JSON);
    httpHeaders.add(REQUEST_ORIGINATOR, APP_NAME_VALUE);
    return httpHeaders;
  }

  public static Map<String, Object> getHttpHeadersMap() {
    Map<String, Object> httpHeadersMap = new HashMap<>();
    httpHeadersMap.put(TENENT_FACLITYNUM, "32679");
    httpHeadersMap.put(TENENT_COUNTRY_CODE, "US");
    httpHeadersMap.put(USER_ID_HEADER_KEY, "sysadmin");
    httpHeadersMap.put(CORRELATION_ID_HEADER_KEY, "1a2bc3d4");
    return httpHeadersMap;
  }
}
