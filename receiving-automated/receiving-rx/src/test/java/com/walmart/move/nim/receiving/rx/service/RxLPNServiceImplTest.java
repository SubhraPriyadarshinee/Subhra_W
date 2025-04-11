package com.walmart.move.nim.receiving.rx.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.*;
import org.springframework.web.client.ResourceAccessException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RxLPNServiceImplTest {

  @Mock private AppConfig appConfig;
  @Mock private RestConnector retryableRestConnector;
  @InjectMocks private RxLPNServiceImpl rxLPNService;

  @BeforeMethod
  public void createRdcPNServiceImpl() throws Exception {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32818);
  }

  @AfterMethod
  public void resetMocks() {
    reset(appConfig, retryableRestConnector);
  }

  @Test
  public void testRxRetrieveLPN() throws Exception {
    Map response = new HashMap<>();
    response.put("lpns", Collections.singletonList("F06001000020000009"));
    when(appConfig.getLpnBaseUrl()).thenReturn("mockRxLPNServer");
    ResponseEntity<Map> mockResponseEntity = new ResponseEntity<>(response, HttpStatus.OK);

    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    HttpHeaders mockHeaders = MockHttpHeaders.getHeaders();
    CompletableFuture<Set<String>> retrieveLPN = rxLPNService.retrieveLPN(1, mockHeaders);
    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    assertFalse(retrieveLPN.get().isEmpty());
  }

  @Test
  public void testRetrieveLPN_error() throws Exception {
    when(appConfig.getLpnBaseUrl()).thenReturn("mockRxLPNServer");
    HttpHeaders mockHeaders = MockHttpHeaders.getHeaders();
    doThrow(new ResourceAccessException("mock_error"))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    CompletableFuture<Set<String>> retrieveLPN = rxLPNService.retrieveLPN(1, mockHeaders);
    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    assertTrue(retrieveLPN.get().isEmpty());
  }
}
