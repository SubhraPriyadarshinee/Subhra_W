package com.walmart.move.nim.receiving.core.client.orderservice;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.orderservice.model.LpnUpdateRequest;
import com.walmart.move.nim.receiving.core.client.orderservice.model.LpnsInfo;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.mock.data.MockContainer;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.testng.annotations.AfterMethod;

public class OrderServiceRestApiClientTest {

  @Mock private AppConfig appConfig;
  @Mock private RestConnector retryableRestConnector;
  @InjectMocks private OrderServiceRestApiClient orderServiceRestApiClient;
  private Gson gson;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32679);
    ReflectionTestUtils.setField(orderServiceRestApiClient, "gson", new Gson());
  }

  private LpnUpdateRequest getLpnsCancellationRequest() {
    LpnsInfo failedLpns =
        LpnsInfo.builder()
            .trackingId("b326790000100000003150756")
            .prevTrackingId("c326790000100000003150756")
            .build();
    List<LpnsInfo> successLpnsInfos = new ArrayList<>();
    LpnsInfo suceessLpns1 =
        LpnsInfo.builder()
            .trackingId("b32679200200200200200")
            .prevTrackingId("c32679300300300200300")
            .build();
    LpnsInfo suceessLpns2 =
        LpnsInfo.builder()
            .trackingId("b32679201201201201201")
            .prevTrackingId("c32679300301301201301")
            .build();
    successLpnsInfos.add(suceessLpns1);
    successLpnsInfos.add(suceessLpns2);
    return LpnUpdateRequest.builder()
        .itemNumber(5454646L)
        .purchaseReferenceNumber("53515926956")
        .failedLpns(Collections.singletonList(failedLpns))
        .successLpns(successLpnsInfos)
        .build();
  }

  private String getOrderServiceUrl() {
    return "https://localhost:8080/";
  }

  @AfterMethod
  public void resetMocks() {
    reset(retryableRestConnector, appConfig);
  }

  @Test
  public void sendLabelUpdate() {
    Map<String, Object> printJob = MockContainer.getInstruction().getContainer().getCtrLabel();
    doReturn(getOrderServiceUrl()).when(appConfig).getOrderServiceBaseUrl();
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<>(String.valueOf(printJob), HttpStatus.OK);
    when(retryableRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
        .thenReturn(mockResponseEntity);
    orderServiceRestApiClient.sendLabelUpdate(
        getLpnsCancellationRequest(), MockHttpHeaders.getHeaders());
    verify(appConfig, times(1)).getOrderServiceBaseUrl();
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  public void sendLabelUpdate_4xx_ThrowsError() {
    doReturn(getOrderServiceUrl()).when(appConfig).getOrderServiceBaseUrl();
    when(retryableRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
        .thenThrow(
            new RestClientResponseException(
                "Some error.",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "",
                null,
                "".getBytes(),
                StandardCharsets.UTF_8));
    orderServiceRestApiClient.sendLabelUpdate(
        getLpnsCancellationRequest(), MockHttpHeaders.getHeaders());
  }

  public void sendLabelUpdate_5xx_ThrowsError() {
    doReturn(getOrderServiceUrl()).when(appConfig).getOrderServiceBaseUrl();
    when(retryableRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
        .thenThrow(new ResourceAccessException(ExceptionCodes.OF_SERVER_ERROR));
    orderServiceRestApiClient.sendLabelUpdate(
        getLpnsCancellationRequest(), MockHttpHeaders.getHeaders());
  }
}
