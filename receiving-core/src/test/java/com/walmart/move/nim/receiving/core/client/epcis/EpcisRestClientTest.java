package com.walmart.move.nim.receiving.core.client.epcis;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import java.util.ArrayList;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class EpcisRestClientTest {

  @InjectMocks private EpcisRestClient epcisRestClient;
  @Mock private RestConnector retryableRestConnector;
  @Mock private AppConfig appConfig;
  private Gson gson = new Gson();

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(epcisRestClient, "gson", gson);
  }

  @Test
  public void testPublishReceiveEvent() {
    doReturn(new ResponseEntity<String>(HttpStatus.OK))
        .when(retryableRestConnector)
        .exchange(anyString(), any(), any(), any(Class.class));
    doReturn("https://dev-pharma-serialization.prod.us.walmart.net")
        .when(appConfig)
        .getEpcisServiceBaseUrl();
    epcisRestClient.publishReceiveEvent(new ArrayList<>(), new HttpHeaders());
  }

  @Test
  public void testPublishReceiveEvent_failure() {
    doThrow(
            new RestClientResponseException(
                "call failed", HttpStatus.BAD_REQUEST.value(), "call failed", null, null, null))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), any(Class.class));
    doReturn("https://dev-pharma-serialization.prod.us.walmart.net")
        .when(appConfig)
        .getEpcisServiceBaseUrl();
    epcisRestClient.publishReceiveEvent(new ArrayList<>(), new HttpHeaders());
  }

  @Test
  public void testPublishReceiveEvent_resourceEx() {
    doThrow(new ResourceAccessException("call failed"))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), any(Class.class));
    doReturn("https://dev-pharma-serialization.prod.us.walmart.net")
        .when(appConfig)
        .getEpcisServiceBaseUrl();
    epcisRestClient.publishReceiveEvent(new ArrayList<>(), new HttpHeaders());
  }

  @Test
  public void testVerifyReceiveEvent() {
    doReturn(new ResponseEntity<String>(HttpStatus.OK))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), any(Class.class));
    doReturn("https://dev-pharma-serialization.prod.us.walmart.net")
        .when(appConfig)
        .getEpcisServiceBaseUrl();
    epcisRestClient.verifySerializedData(new EpcisVerifyRequest(), new HttpHeaders());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testVerifyReceiveEvent_failure() {
    doThrow(
            new RestClientResponseException(
                "call failed", HttpStatus.BAD_REQUEST.value(), "call failed", null, null, null))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), any(Class.class));
    doReturn("https://dev-pharma-serialization.prod.us.walmart.net")
        .when(appConfig)
        .getEpcisServiceBaseUrl();
    epcisRestClient.verifySerializedData(new EpcisVerifyRequest(), new HttpHeaders());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testVerifyReceiveEvent_resourceEx() {
    doThrow(new ResourceAccessException("call failed"))
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), any(Class.class));
    doReturn("https://dev-pharma-serialization.prod.us.walmart.net")
        .when(appConfig)
        .getEpcisServiceBaseUrl();
    epcisRestClient.verifySerializedData(new EpcisVerifyRequest(), new HttpHeaders());
  }
}
