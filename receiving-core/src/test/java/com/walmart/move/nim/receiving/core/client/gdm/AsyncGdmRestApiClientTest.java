package com.walmart.move.nim.receiving.core.client.gdm;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.mock.data.MockGdmResponse;
import com.walmart.move.nim.receiving.core.model.DeliveryWithOSDRResponse;
import com.walmart.move.nim.receiving.core.model.GdmDeliveryHistoryResponse;
import com.walmart.move.nim.receiving.core.service.DeliveryServiceRetryableImpl;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UriComponentsBuilder;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class AsyncGdmRestApiClientTest {

  @Mock private GDMRestApiClient gdmRestApiClient;
  @Mock private DeliveryServiceRetryableImpl deliveryService;
  @InjectMocks private AsyncGdmRestApiClient asyncGdmRestApiClient = new AsyncGdmRestApiClient();

  private Gson gson = new Gson();

  @BeforeClass
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  public void resetMocks() {
    reset(gdmRestApiClient, deliveryService);
  }

  @Test
  public void testGetDelivery()
      throws InterruptedException, ExecutionException, GDMRestApiClientException {

    DeliveryWithOSDRResponse mockDeliveryWithOSDRResponse = new DeliveryWithOSDRResponse();
    mockDeliveryWithOSDRResponse.setDeliveryNumber(1l);

    doReturn(mockDeliveryWithOSDRResponse)
        .when(gdmRestApiClient)
        .getDelivery(anyLong(), any(Map.class));

    Map<String, Object> mockHeaders = new HashMap<>();

    CompletableFuture<DeliveryWithOSDRResponse> completableFuture =
        asyncGdmRestApiClient.getDelivery(1l, mockHeaders);
    DeliveryWithOSDRResponse deliveryWithOSDRResponse = completableFuture.get();

    assertEquals(
        mockDeliveryWithOSDRResponse.getDeliveryNumber().longValue(),
        deliveryWithOSDRResponse.getDeliveryNumber().longValue());

    verify(gdmRestApiClient, times(1)).getDelivery(anyLong(), any(Map.class));
  }

  @Test(expectedExceptions = CompletionException.class)
  public void testGetDeliveryExceptionSenario()
      throws InterruptedException, ExecutionException, GDMRestApiClientException {

    DeliveryWithOSDRResponse mockDeliveryWithOSDRResponse = new DeliveryWithOSDRResponse();
    mockDeliveryWithOSDRResponse.setDeliveryNumber(1l);

    doThrow(new GDMRestApiClientException("", HttpStatus.INTERNAL_SERVER_ERROR))
        .when(gdmRestApiClient)
        .getDelivery(anyLong(), any(Map.class));

    Map<String, Object> mockHeaders = new HashMap<>();

    CompletableFuture<DeliveryWithOSDRResponse> completableFuture =
        asyncGdmRestApiClient.getDelivery(1l, mockHeaders);
    DeliveryWithOSDRResponse deliveryWithOSDRResponse = completableFuture.get();

    assertEquals(
        mockDeliveryWithOSDRResponse.getDeliveryNumber().longValue(),
        deliveryWithOSDRResponse.getDeliveryNumber().longValue());

    verify(gdmRestApiClient, times(1)).getDelivery(anyLong(), any(Map.class));
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testGetDeliveryDetailsReturnsFailureResponse() throws ReceivingException {
    String gdmURI = "getDeliveryDetails";
    URI gdmGetDeliveryUri = UriComponentsBuilder.fromUriString(gdmURI).buildAndExpand().toUri();

    doThrow(new ReceivingException("", HttpStatus.INTERNAL_SERVER_ERROR))
        .when(deliveryService)
        .getDeliveryByURI(any(URI.class), any(HttpHeaders.class));

    asyncGdmRestApiClient.getDeliveryDetails(gdmGetDeliveryUri, MockHttpHeaders.getHeaders());
    verify(deliveryService, times(1)).getDeliveryByURI(any(URI.class), any(HttpHeaders.class));
  }

  @Test
  public void testGetDeliveryDetailsReturnsSuccessResponse()
      throws InterruptedException, ExecutionException, IOException, ReceivingException {
    String gdmURI = "getDeliveryDetails";
    URI gdmGetDeliveryUri = UriComponentsBuilder.fromUriString(gdmURI).buildAndExpand().toUri();

    doReturn(MockGdmResponse.getDeliveryDetailsByUriIncludesDummyPoReturnsSuccessResponse())
        .when(deliveryService)
        .getDeliveryByURI(any(URI.class), any(HttpHeaders.class));

    CompletableFuture<String> completableFuture =
        asyncGdmRestApiClient.getDeliveryDetails(gdmGetDeliveryUri, MockHttpHeaders.getHeaders());
    String deliveryDetailsResponse = completableFuture.get();

    assertNotNull(deliveryDetailsResponse);
    verify(deliveryService, times(1)).getDeliveryByURI(any(URI.class), any(HttpHeaders.class));
  }

  @Test(expectedExceptions = CompletionException.class)
  public void testGetDeliveryHistoryReturnsFailureResponse() throws GDMRestApiClientException {

    doThrow(new GDMRestApiClientException("", HttpStatus.INTERNAL_SERVER_ERROR))
        .when(gdmRestApiClient)
        .getDeliveryHistory(anyLong(), any(HttpHeaders.class));

    asyncGdmRestApiClient.getDeliveryHistory(1234L, MockHttpHeaders.getHeaders());
    verify(gdmRestApiClient, times(1)).getDeliveryHistory(anyLong(), any(HttpHeaders.class));
  }

  @Test
  public void testGetDeliveryHistoryReturnsSuccessResponse()
      throws InterruptedException, ExecutionException, IOException, GDMRestApiClientException {

    GdmDeliveryHistoryResponse gdmDeliveryHistoryResponse =
        gson.fromJson(
            MockGdmResponse.getDeliveryHistoryReturnsSuccessResponse(),
            GdmDeliveryHistoryResponse.class);

    doReturn(gdmDeliveryHistoryResponse)
        .when(gdmRestApiClient)
        .getDeliveryHistory(anyLong(), any(HttpHeaders.class));

    CompletableFuture<GdmDeliveryHistoryResponse> completableFuture =
        asyncGdmRestApiClient.getDeliveryHistory(1234L, MockHttpHeaders.getHeaders());
    GdmDeliveryHistoryResponse deliveryDetailsResponse = completableFuture.get();

    assertNotNull(deliveryDetailsResponse);
    verify(gdmRestApiClient, times(1)).getDeliveryHistory(anyLong(), any(HttpHeaders.class));
  }
}
