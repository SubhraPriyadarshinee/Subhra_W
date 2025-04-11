package com.walmart.move.nim.receiving.rdc.mock.data;

import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import org.springframework.http.HttpHeaders;

public class MockLocationHeaders {

  private static HttpHeaders httpHeaders = new HttpHeaders();

  public static HttpHeaders getHeaders(String facilityNum, String countryCode) {
    httpHeaders = MockHttpHeaders.getHeaders(facilityNum, countryCode);
    httpHeaders.add(RdcConstants.WFT_LOCATION_ID, "100");
    httpHeaders.add(RdcConstants.WFT_LOCATION_TYPE, "Door");
    httpHeaders.add(RdcConstants.WFT_SCC_CODE, "001002001");
    return httpHeaders;
  }
}
