package com.walmart.move.nim.receiving.witron.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class WitronLPNServiceImplTest {

  @Mock private AppConfig appConfig;

  @Mock private RestConnector retryableRestConnector;
  @Mock private TenantSpecificConfigReader configUtils;

  @InjectMocks private WitronLPNServiceImpl witronLPNServiceImpl;

  @BeforeMethod
  public void createWitronLPNServiceImpl() throws Exception {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32612);
    TenantContext.setCorrelationId("a1-b2-c3");
  }

  @Test
  public void testRetrieveLPN() throws Exception {

    Map response = new HashMap();
    response.put("lpns", Arrays.asList("C32612000020000001"));

    doReturn("https://mocklpnServer").when(appConfig).getLpnBaseUrl();

    ResponseEntity<Map> mockResponseEntity = new ResponseEntity<Map>(response, HttpStatus.OK);

    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    HttpHeaders mockHeaders = ReceivingUtils.getHeaders();

    CompletableFuture<Set<String>> retrieveLPN = witronLPNServiceImpl.retrieveLPN(1, mockHeaders);

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    assertFalse(retrieveLPN.get().isEmpty());
  }

  @Test
  public void testRetrieveLPN_error() throws Exception {

    doReturn("https://mocklpnServer").when(appConfig).getLpnBaseUrl();

    doThrow(new ResourceAccessException("mock_error"))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    HttpHeaders mockHeaders = ReceivingUtils.getHeaders();

    CompletableFuture<Set<String>> retrieveLPN = witronLPNServiceImpl.retrieveLPN(1, mockHeaders);

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    assertTrue(retrieveLPN.get().isEmpty());
  }
}
