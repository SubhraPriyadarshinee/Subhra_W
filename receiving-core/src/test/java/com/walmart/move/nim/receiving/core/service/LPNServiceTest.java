package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.retry.RetryableRestConnector;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class LPNServiceTest extends ReceivingTestBase {

  @Mock private RetryableRestConnector retryableRestConnector;

  @Mock private AppConfig appConfig;

  @InjectMocks private final LPNServiceImpl lpnService = new LPNServiceImpl();

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32987);
  }

  @AfterMethod
  public void resetMocks() {
    reset(retryableRestConnector);
  }

  @Test
  public void testRetrieveLpn() {
    Map<String, List> mockResponse = new HashMap<>();
    mockResponse.put(
        ReceivingConstants.LPNS, Collections.singletonList("c32987000000000000000001"));
    doReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK))
        .when(retryableRestConnector)
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class));
    lpnService.retrieveLPN(10, MockHttpHeaders.getHeaders());
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class));
  }

  @Test
  public void testRetrieveLpnEmptyResponseScenario() {
    Map<String, List> mockResponse = new HashMap<>();
    mockResponse.put(ReceivingConstants.LPNS, new ArrayList());
    doReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK))
        .when(retryableRestConnector)
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class));
    lpnService.retrieveLPN(10, MockHttpHeaders.getHeaders());

    verify(retryableRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class));
  }

  @Test
  public void testRetrieveLpnServiceDown() {
    doThrow(new ResourceAccessException("Error"))
        .when(retryableRestConnector)
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class));
    lpnService.retrieveLPN(10, MockHttpHeaders.getHeaders());

    verify(retryableRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class));
  }

  @Test
  public void testRetrieveLpnNotFound() {
    doThrow(
            new RestClientResponseException(
                "Some error.",
                HttpStatus.NOT_FOUND.value(),
                "",
                null,
                "".getBytes(),
                Charset.forName("UTF-8")))
        .when(retryableRestConnector)
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class));
    lpnService.retrieveLPN(10, MockHttpHeaders.getHeaders());

    verify(retryableRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class));
  }
}
