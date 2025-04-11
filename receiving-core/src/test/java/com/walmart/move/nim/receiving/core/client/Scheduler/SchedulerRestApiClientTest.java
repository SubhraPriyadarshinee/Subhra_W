package com.walmart.move.nim.receiving.core.client.Scheduler;

import static org.mockito.Mockito.*;
import static org.testng.Assert.assertTrue;

import com.walmart.move.nim.receiving.core.client.scheduler.SchedulerRestApiClient;
import com.walmart.move.nim.receiving.core.client.scheduler.model.ExternalPurchaseOrder;
import com.walmart.move.nim.receiving.core.client.scheduler.model.PoAppendRequest;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.retry.RetryableRestConnector;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.Arrays;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.*;
import org.springframework.web.client.RestClientResponseException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class SchedulerRestApiClientTest {
  @Mock private RetryableRestConnector retryableRestConnector;

  @Mock private AppConfig appConfig;

  @InjectMocks private SchedulerRestApiClient schedulerRestApiClient = new SchedulerRestApiClient();

  @BeforeClass
  public void setup() {

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32612);

    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  public void tearDown() {
    reset(retryableRestConnector, appConfig);
  }

  @Test
  public void testAppendPoToDelivery_success() {

    doReturn("http://uat.scheduler.walmart.com").when(appConfig).getSchedulerBaseUrl();

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>("Success", HttpStatus.OK);

    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    ExternalPurchaseOrder externalPurchaseOrder = new ExternalPurchaseOrder("72835763", 4);

    schedulerRestApiClient.appendPoToDelivery(
        PoAppendRequest.builder()
            .deliveryId("28176323")
            .externalPurchaseOrderList(Arrays.asList(externalPurchaseOrder))
            .build(),
        new HttpHeaders());
    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test()
  public void testAppendPoToDelivery_Exception() {

    doReturn("http://uat.scheduler.walmart.com").when(appConfig).getSchedulerBaseUrl();

    ResponseEntity<String> mockResponseEntity = new ResponseEntity<String>(HttpStatus.NOT_FOUND);

    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    ExternalPurchaseOrder externalPurchaseOrder = new ExternalPurchaseOrder("72835763", 4);

    try {

      schedulerRestApiClient.appendPoToDelivery(
          PoAppendRequest.builder()
              .deliveryId("28176323")
              .externalPurchaseOrderList(Arrays.asList(externalPurchaseOrder))
              .build(),
          new HttpHeaders());
      verify(retryableRestConnector, atLeastOnce())
          .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    } catch (RestClientResponseException e) {
      assertTrue(e.getRawStatusCode() == 404);
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }
}
