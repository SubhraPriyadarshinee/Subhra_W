package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;

import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.RetryEntity;
import com.walmart.move.nim.receiving.core.message.service.RetryService;
import com.walmart.move.nim.receiving.core.model.decant.DecantMessagePublishRequest;
import com.walmart.move.nim.receiving.core.retry.RetryableRestConnector;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.RetryTargetFlow;
import com.walmart.move.nim.receiving.utils.constants.RetryTargetType;
import java.util.Arrays;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DecantServiceTest {

  private static final int FACILITY_NUMBER = 266;
  private static final String FACILITY_COUNTRY_CODE = "US";
  private static final String CORRELATION_ID = "correlation-id";
  private static final String USER_ID = "a0k07nu";
  private static final String MESSAGE_1 =
      "{\"messageId\":\"TEST-ASH006\",\"trackingId\":\"3267981258510000000010\",\"userId\":\"a0k07nu\","
          + "\"eventType\":\"PALLET_RECEIVE\",\"containerCreatedDate\":\"2023-05-16T04:48:41.012+0000\","
          + "\"startTime\":\"2023-05-16T04:49:19.820Z\",\"itemNumber\":571014206,\"endTime\":\"2023-05-16T04:49:58.683Z\"}";
  private static final String MESSAGE_2 =
      "{\"messageId\":\"TEST-ASH007\",\"trackingId\":\"3267981258510000000020\",\"userId\":\"a0k07nu\","
          + "\"eventType\":\"PALLET_RECEIVE\",\"containerCreatedDate\":\"2023-05-16T04:48:41.012+0000\","
          + "\"startTime\":\"2023-05-16T04:49:19.820Z\",\"itemNumber\":571014206,\"endTime\":\"2023-05-16T04:49:58.683Z\"}";

  @Mock RetryService retryService;

  @Mock private RetryableRestConnector retryableRestConnector;

  @Mock private AppConfig appConfig;

  @InjectMocks private DecantService decantService;

  @Captor ArgumentCaptor<String> uriCaptor;

  @Captor ArgumentCaptor<HttpHeaders> httpHeadersCaptor;

  @Captor ArgumentCaptor<List<DecantMessagePublishRequest>> requestsCaptor;

  @Captor ArgumentCaptor<RetryEntity> entityCaptor;

  @BeforeClass
  public void setup() {
    MockitoAnnotations.openMocks(this);

    TenantContext.setFacilityNum(FACILITY_NUMBER);
    TenantContext.setFacilityCountryCode(FACILITY_COUNTRY_CODE);
    TenantContext.setCorrelationId(CORRELATION_ID);
    TenantContext.setAdditionalParams(ReceivingConstants.USER_ID_HEADER_KEY, USER_ID);
  }

  @AfterMethod
  public void resetMocks() {
    Mockito.reset(retryableRestConnector);
    Mockito.reset(retryService);
    Mockito.reset(appConfig);
  }

  @Test
  public void testInitiateMessagePublish_success() {
    doReturn("https://gls-atlas-decant-qa-cell002.walmart.com").when(appConfig).getDecantBaseUrl();

    DecantMessagePublishRequest decantMessagePublishRequest1 =
        createDecantMessagePublishRequest(MESSAGE_1);
    DecantMessagePublishRequest decantMessagePublishRequest2 =
        createDecantMessagePublishRequest(MESSAGE_2);

    ResponseEntity<String> responseEntity =
        new ResponseEntity<>("Bulk Message Published successfully", HttpStatus.OK);
    doReturn(responseEntity)
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), any(Class.class));

    decantService.initiateMessagePublish(
        Arrays.asList(decantMessagePublishRequest1, decantMessagePublishRequest2));

    verify(retryService, times(1))
        .putForRetries(
            uriCaptor.capture(), any(), httpHeadersCaptor.capture(), anyString(), any(), any());
    assertEquals(
        "https://gls-atlas-decant-qa-cell002.walmart.com/api/publish/v2", uriCaptor.getValue());
    assertHttpHeaders(httpHeadersCaptor.getValue());

    verify(retryableRestConnector, times(1))
        .post(uriCaptor.capture(), requestsCaptor.capture(), httpHeadersCaptor.capture(), any());
    assertEquals(
        "https://gls-atlas-decant-qa-cell002.walmart.com/api/publish/v2", uriCaptor.getValue());
    assertEquals(
        Arrays.asList(decantMessagePublishRequest1, decantMessagePublishRequest2),
        requestsCaptor.getValue());
    assertHttpHeaders(httpHeadersCaptor.getValue());
  }

  @Test
  public void testInitiateMessagePublish_exception() {
    doReturn("https://gls-atlas-decant-qa-cell002.walmart.com").when(appConfig).getDecantBaseUrl();

    DecantMessagePublishRequest decantMessagePublishRequest =
        createDecantMessagePublishRequest(MESSAGE_1);

    doThrow(RuntimeException.class)
        .when(retryableRestConnector)
        .post(anyString(), any(), any(HttpHeaders.class), any(Class.class));

    RetryEntity retryEntity = createRetryEntity();
    doReturn(retryEntity)
        .when(retryService)
        .putForRetries(any(), any(), any(), any(), any(), any());

    decantService.initiateMessagePublish(Arrays.asList(decantMessagePublishRequest));

    verify(retryService, times(1))
        .putForRetries(
            uriCaptor.capture(), any(), httpHeadersCaptor.capture(), anyString(), any(), any());
    assertEquals(
        "https://gls-atlas-decant-qa-cell002.walmart.com/api/publish/v2", uriCaptor.getValue());
    assertHttpHeaders(httpHeadersCaptor.getValue());

    verify(retryableRestConnector, times(1))
        .post(uriCaptor.capture(), requestsCaptor.capture(), httpHeadersCaptor.capture(), any());
    assertEquals(
        "https://gls-atlas-decant-qa-cell002.walmart.com/api/publish/v2", uriCaptor.getValue());
    assertEquals(Arrays.asList(decantMessagePublishRequest), requestsCaptor.getValue());
    assertHttpHeaders(httpHeadersCaptor.getValue());

    verify(retryService, times(1)).save(entityCaptor.capture());
    RetryEntity entity = entityCaptor.getValue();
    assertEquals(EventTargetStatus.PENDING, entity.getEventTargetStatus());
  }

  private void assertHttpHeaders(HttpHeaders httpHeaders) {
    assertEquals(
        Arrays.asList(String.valueOf(FACILITY_NUMBER)),
        httpHeaders.get(ReceivingConstants.TENENT_FACLITYNUM));
    assertEquals(
        Arrays.asList(FACILITY_COUNTRY_CODE),
        httpHeaders.get(ReceivingConstants.TENENT_COUNTRY_CODE));
    assertEquals(
        Arrays.asList(CORRELATION_ID),
        httpHeaders.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
    assertEquals(Arrays.asList(USER_ID), httpHeaders.get(ReceivingConstants.USER_ID_HEADER_KEY));
  }

  private DecantMessagePublishRequest createDecantMessagePublishRequest(String message) {
    DecantMessagePublishRequest decantMessagePublishRequest =
        DecantMessagePublishRequest.builder().message(message).scenario("decanting").build();

    return decantMessagePublishRequest;
  }

  private RetryEntity createRetryEntity() {
    RetryEntity retryEntity = new RetryEntity();
    retryEntity.setId(123456L);
    retryEntity.setRetryTargetType(RetryTargetType.REST);
    retryEntity.setRetryTargetFlow(RetryTargetFlow.DECANT_MESSAGE_PUBLISH);
    retryEntity.setEventTargetStatus(EventTargetStatus.SUCCESSFUL);
    return retryEntity;
  }
}
