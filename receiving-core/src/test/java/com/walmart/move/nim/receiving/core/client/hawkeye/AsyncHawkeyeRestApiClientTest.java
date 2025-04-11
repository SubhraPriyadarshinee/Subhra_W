package com.walmart.move.nim.receiving.core.client.hawkeye;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.walmart.move.nim.receiving.core.client.hawkeye.model.DeliverySearchRequest;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Assertions;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class AsyncHawkeyeRestApiClientTest {
  @Mock HawkeyeRestApiClient hawkeyeRestApiClient;
  @InjectMocks AsyncHawkeyeApiClient asyncHawkeyeApiClient;

  @BeforeClass
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void getDeliveriesFromHawkeyeTest() throws ExecutionException, InterruptedException {
    DeliverySearchRequest deliverySearchRequest = getDeliverySearchRequest();

    HttpHeaders httpHeaders = getHttpHeaders();

    List<String> deliveryList = Arrays.asList("delivery1", "delivery2");
    Mockito.when(
            hawkeyeRestApiClient.getHistoryDeliveriesFromHawkeye(
                deliverySearchRequest, httpHeaders))
        .thenReturn(Optional.of(deliveryList));

    CompletableFuture<Optional<List<String>>> completableFuture =
        asyncHawkeyeApiClient.getDeliveriesFromHawkeye(deliverySearchRequest, httpHeaders);
    Optional<List<String>> deliveriesListOptional = completableFuture.get();
    Assertions.assertNotNull(deliveriesListOptional);
    Assertions.assertNotNull(deliveriesListOptional.get());
    Assertions.assertEquals(2, deliveriesListOptional.get().size());
    Assertions.assertTrue(deliveriesListOptional.get().contains("delivery1"));
    Assertions.assertTrue(
        deliveriesListOptional.get().containsAll(Arrays.asList("delivery1", "delivery2")));
  }

  @Test(expectedExceptions = CompletionException.class)
  public void getDeliveriesFromHawkeyeTest_Exception() throws ReceivingInternalException {
    DeliverySearchRequest deliverySearchRequest = getDeliverySearchRequest();
    HttpHeaders httpHeaders = getHttpHeaders();

    doThrow(new ReceivingInternalException("Error Code", "ReceivingInternalException Description"))
        .when(hawkeyeRestApiClient)
        .getHistoryDeliveriesFromHawkeye(Mockito.any(), Mockito.any());

    asyncHawkeyeApiClient.getDeliveriesFromHawkeye(deliverySearchRequest, httpHeaders);
    verify(hawkeyeRestApiClient, times(1)).getHistoryDeliveriesFromHawkeye(any(), any());
  }

  private HttpHeaders getHttpHeaders() {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("WMT-UserId", "sysadmin");
    httpHeaders.add("WMT-CorrelationId", "correlation");
    return httpHeaders;
  }

  private DeliverySearchRequest getDeliverySearchRequest() {
    DeliverySearchRequest deliverySearchRequest = new DeliverySearchRequest();
    deliverySearchRequest.setUpc("Upc");
    deliverySearchRequest.setFromDate("2023-06-01T13:14:15.123+01:00");
    deliverySearchRequest.setToDate("2023-06-20T13:14:15.123+01:00");
    deliverySearchRequest.setLocationId("LocationId");
    return deliverySearchRequest;
  }
}
