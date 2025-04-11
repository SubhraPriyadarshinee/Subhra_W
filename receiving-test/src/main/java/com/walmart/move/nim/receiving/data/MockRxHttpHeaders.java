package com.walmart.move.nim.receiving.data;

import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

public class MockRxHttpHeaders {

  public static HttpHeaders getHeaders() {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.set(ReceivingConstants.TENENT_FACLITYNUM, "32898");
    httpHeaders.set(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.set(ReceivingConstants.USER_ID_HEADER_KEY, "rxTestUser");
    httpHeaders.set(ReceivingConstants.CORRELATION_ID_HEADER_KEY, "3a2b6c1d2e");
    httpHeaders.set(ReceivingConstants.SECURITY_HEADER_KEY, "1");
    httpHeaders.add(ReceivingConstants.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    return httpHeaders;
  }

  public static Map<String, Object> getMockHeadersMap() {
    Map<String, Object> mockHeaders = new HashMap<>();
    mockHeaders.put(ReceivingConstants.TENENT_FACLITYNUM, "32898");
    mockHeaders.put(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    mockHeaders.put(ReceivingConstants.USER_ID_HEADER_KEY, "rxTestUser");
    mockHeaders.put(ReceivingConstants.CORRELATION_ID_HEADER_KEY, "3a2b6c1d2e");
    mockHeaders.put(ReceivingConstants.SECURITY_HEADER_KEY, 1);
    mockHeaders.put(ReceivingConstants.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    return mockHeaders;
  }
}
