package com.walmart.move.nim.receiving.core.client.orderfulfillment;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.orderfulfillment.model.PrintShippingLabelRequest;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
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
import org.testng.Assert;
import org.testng.annotations.AfterMethod;

public class OrderFulfillmentRestApiClientTest {
  @Mock private AppConfig appConfig;
  @Mock private RestConnector retryableRestConnector;
  @InjectMocks private OrderFulfillmentRestApiClient orderFulfillmentRestApiClient;
  private Gson gson;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32679);
    ReflectionTestUtils.setField(orderFulfillmentRestApiClient, "gson", new Gson());
  }

  private PrintShippingLabelRequest getPrintShippingLabelRequest() {
    return PrintShippingLabelRequest.builder()
        .routingLabelId("b326790000100000003150756")
        .stagingLocation("100")
        .build();
  }

  private String getOrderFulfillmentUrl() {
    return "https://localhost:8080/";
  }

  @AfterMethod
  public void resetMocks() {
    reset(retryableRestConnector, appConfig);
  }

  @Test
  public void printShippingLabelFromRoutingLabel_HappyPath() {
    Map<String, Object> printJob = MockContainer.getInstruction().getContainer().getCtrLabel();
    doReturn(getOrderFulfillmentUrl()).when(appConfig).getOrderFulfillmentBaseUrl();
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<>(String.valueOf(printJob), HttpStatus.OK);
    when(retryableRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
        .thenReturn(mockResponseEntity);
    Optional<Map<String, Object>> response =
        orderFulfillmentRestApiClient.printShippingLabelFromRoutingLabel(
            getPrintShippingLabelRequest(), MockHttpHeaders.getHeaders());
    verify(appConfig, times(1)).getOrderFulfillmentBaseUrl();
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    Assert.assertNotNull(response);
  }

  @Test(expected = ReceivingBadDataException.class)
  public void printShippingLabelFromRoutingLabel_4xx_ThrowsError() {
    doReturn(getOrderFulfillmentUrl()).when(appConfig).getOrderFulfillmentBaseUrl();
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
    orderFulfillmentRestApiClient.printShippingLabelFromRoutingLabel(
        getPrintShippingLabelRequest(), MockHttpHeaders.getHeaders());
  }

  @Test(expected = ReceivingInternalException.class)
  public void printShippingLabelFromRoutingLabel_5xx_ThrowsError() {
    doReturn(getOrderFulfillmentUrl()).when(appConfig).getOrderFulfillmentBaseUrl();
    when(retryableRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
        .thenThrow(new ResourceAccessException(ExceptionCodes.OF_SERVER_ERROR));
    orderFulfillmentRestApiClient.printShippingLabelFromRoutingLabel(
        getPrintShippingLabelRequest(), MockHttpHeaders.getHeaders());
  }
}
