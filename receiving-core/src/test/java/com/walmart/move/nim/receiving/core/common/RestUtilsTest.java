package com.walmart.move.nim.receiving.core.common;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@ActiveProfiles("test")
public class RestUtilsTest extends AbstractTestNGSpringContextTests {

  @InjectMocks private RestUtils restUtils;

  @Mock private RestTemplate restTemplate;

  private HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
  private HttpEntity<String> entity = new HttpEntity<String>(httpHeaders);
  private HttpEntity<String> request = new HttpEntity<>("", httpHeaders);
  private String url = "http://localhost:8081/mock";

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);

    TenantContext.setFacilityNum(32987);
    TenantContext.setFacilityCountryCode("us");
  }

  @AfterMethod
  public void tearDown() {
    reset(restTemplate);
  }

  @Test
  public void testGetWithSuccessResponse() throws ReceivingException {
    when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), eq(entity), eq(String.class)))
        .thenReturn(new ResponseEntity<>("", HttpStatus.OK));

    Map<String, String> pathParams = new HashMap<>();
    pathParams.put("param1", "1234");
    pathParams.put("param2", "5678");

    ResponseEntity<String> response = restUtils.get(url, httpHeaders, pathParams);

    assertEquals(response.getStatusCode(), HttpStatus.OK);
  }

  @Test
  public void testGetWithNullResponse() throws ReceivingException {
    when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), eq(entity), eq(String.class)))
        .thenReturn(null);

    assertEquals(restUtils.get(url, httpHeaders, null), null);
  }

  @Test
  public void testGetRestclientResponseException() {
    when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), eq(entity), eq(String.class)))
        .thenThrow(
            new RestClientResponseException(
                "Error",
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                null,
                "Error".getBytes(),
                null));

    ResponseEntity<String> result = restUtils.get(url, httpHeaders, new HashMap<>());
    assertEquals(result.getStatusCode(), HttpStatus.CONFLICT);
    assertEquals(result.getBody(), "Error");
  }

  @Test
  public void testGetResourceAccessException() {
    when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), eq(entity), eq(String.class)))
        .thenThrow(new ResourceAccessException("ERROR"));

    ResponseEntity<String> result = restUtils.get(url, httpHeaders, new HashMap<>());
    assertEquals(result.getStatusCode(), HttpStatus.SERVICE_UNAVAILABLE);
    assertEquals(result.getBody(), "Error in fetching resource");
  }

  @Test
  public void testPostWithSuccessResponse() throws ReceivingException {
    when(restTemplate.exchange(
            any(URI.class), eq(HttpMethod.POST), isA(HttpEntity.class), eq(String.class)))
        .thenReturn(new ResponseEntity<>("", HttpStatus.OK));

    ResponseEntity<String> response = restUtils.post(url, httpHeaders, new HashMap<>(), "");
    assertEquals(response.getStatusCode(), HttpStatus.OK);
  }

  @Test
  public void testPostWithNullResponse() throws ReceivingException {
    when(restTemplate.exchange(any(URI.class), eq(HttpMethod.POST), eq(request), eq(String.class)))
        .thenReturn(null);

    assertEquals(restUtils.post(url, httpHeaders, new HashMap<>(), ""), null);
  }

  @Test
  public void testPostWithResourceAccessException() {
    when(restTemplate.exchange(
            any(URI.class), eq(HttpMethod.POST), isA(HttpEntity.class), eq(String.class)))
        .thenThrow(new ResourceAccessException("ERROR"));
    ResponseEntity<String> result = restUtils.post(url, httpHeaders, new HashMap<>(), "");
    assertEquals(result.getStatusCode(), HttpStatus.SERVICE_UNAVAILABLE);
    assertEquals(result.getBody(), "Error in fetching resource");
  }

  @Test
  public void testPostWithRestClientResponseException() {
    when(restTemplate.exchange(
            any(URI.class), eq(HttpMethod.POST), isA(HttpEntity.class), eq(String.class)))
        .thenThrow(new HttpServerErrorException(HttpStatus.CONFLICT));
    ResponseEntity<String> result = restUtils.post(url, httpHeaders, new HashMap<>(), "");
    assertEquals(result.getStatusCode(), HttpStatus.CONFLICT);
  }

  @Test
  public void testPutWithSuccessResponse() throws ReceivingException {
    when(restTemplate.exchange(
            any(URI.class), eq(HttpMethod.PUT), isA(HttpEntity.class), eq(String.class)))
        .thenReturn(new ResponseEntity<>("", HttpStatus.OK));

    ResponseEntity<String> response = restUtils.put(url, httpHeaders, new HashMap<>(), "");
    assertEquals(response.getStatusCode(), HttpStatus.OK);
  }

  @Test
  public void testPutWithNullResponse() throws ReceivingException {
    when(restTemplate.exchange(any(URI.class), eq(HttpMethod.PUT), eq(request), eq(String.class)))
        .thenReturn(null);

    assertEquals(restUtils.put(url, httpHeaders, new HashMap<>(), ""), null);
  }

  @Test
  public void testPutWithResourceAccessException() {
    when(restTemplate.exchange(
            any(URI.class), eq(HttpMethod.PUT), isA(HttpEntity.class), eq(String.class)))
        .thenThrow(new ResourceAccessException("ERROR"));
    ResponseEntity<String> result = restUtils.put(url, httpHeaders, new HashMap<>(), "");
    assertEquals(result.getStatusCode(), HttpStatus.SERVICE_UNAVAILABLE);
    assertEquals(result.getBody(), "Error in fetching resource");
  }

  @Test
  public void testPutWithRestClientResponseException() {
    when(restTemplate.exchange(
            any(URI.class), eq(HttpMethod.PUT), isA(HttpEntity.class), eq(String.class)))
        .thenThrow(new HttpServerErrorException(HttpStatus.BAD_REQUEST));
    ResponseEntity<String> result = restUtils.put(url, httpHeaders, new HashMap<>(), "");
    assertEquals(result.getStatusCode(), HttpStatus.BAD_REQUEST);
  }
}
