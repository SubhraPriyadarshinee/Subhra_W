package com.walmart.move.nim.receiving.data;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;

import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

public class GdcHttpHeaders {
  public static HttpHeaders getHeaders() {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.set(TENENT_FACLITYNUM, "32612");
    httpHeaders.set(TENENT_COUNTRY_CODE, "US");
    httpHeaders.set(USER_ID_HEADER_KEY, "witronTest");
    httpHeaders.set(CORRELATION_ID_HEADER_KEY, "3a2b6c1d2e");
    httpHeaders.set(SECURITY_HEADER_KEY, "1");
    httpHeaders.add(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    httpHeaders.add(REQUEST_ORIGINATOR, ReceivingConstants.APP_NAME_VALUE);
    return httpHeaders;
  }

  public static Map<String, Object> getMockHeadersMap() {
    Map<String, Object> mockHeaders = new HashMap<>();
    mockHeaders.put(TENENT_FACLITYNUM, "32612");
    mockHeaders.put(TENENT_COUNTRY_CODE, "US");
    mockHeaders.put(USER_ID_HEADER_KEY, "witronTest");
    mockHeaders.put(CORRELATION_ID_HEADER_KEY, "3a2b6c1d2e");
    mockHeaders.put(SECURITY_HEADER_KEY, 1);
    mockHeaders.put(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    mockHeaders.put(REQUEST_ORIGINATOR, ReceivingConstants.APP_NAME_VALUE);
    return mockHeaders;
  }

  public static HttpHeaders getHeadersWithKotlinFlag() {
    HttpHeaders httpHeaders = getHeaders();
    httpHeaders.add(IS_KOTLIN_CLIENT, "true");
    return httpHeaders;
  }

  public static HttpHeaders getManualGdcHeaders() {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.set(TENENT_FACLITYNUM, "6071");
    httpHeaders.set(TENENT_COUNTRY_CODE, "US");
    httpHeaders.set(USER_ID_HEADER_KEY, "manualGdcTest");
    httpHeaders.set(CORRELATION_ID_HEADER_KEY, "3a2b6c1d2e");
    httpHeaders.set(SECURITY_HEADER_KEY, "1");
    httpHeaders.add(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    httpHeaders.add(REQUEST_ORIGINATOR, ReceivingConstants.APP_NAME_VALUE);
    return httpHeaders;
  }
}
