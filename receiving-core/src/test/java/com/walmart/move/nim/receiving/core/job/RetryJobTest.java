package com.walmart.move.nim.receiving.core.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.common.RestUtils;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.RetryEntity;
import com.walmart.move.nim.receiving.core.message.service.RetryService;
import com.walmart.move.nim.receiving.core.mock.data.MockRetryEntity;
import com.walmart.move.nim.receiving.core.model.RestRetryRequest;
import com.walmart.move.nim.receiving.core.service.AsyncPersister;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.RetryTargetType;
import java.util.Arrays;
import org.junit.Assert;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** @author Punith */
public class RetryJobTest extends ReceivingTestBase {

  @InjectMocks private RetryJob jmsRetryjob;

  @Mock private RetryService jmsRecoveryService;

  @Mock private JmsPublisher jmsPublisher;

  @Mock RestUtils restUtils;

  @Mock private AppConfig appConfig;

  @Mock private AsyncPersister asyncPersister;

  private Gson gson = new Gson();

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  public void tearDown() {
    reset(jmsRecoveryService);
    reset(jmsPublisher);
    reset(restUtils);
    reset(appConfig);
    reset(asyncPersister);
  }

  /** This will test the retriesSchedule method(). */
  @Test
  public void testRetriesSchedule() {

    when(jmsRecoveryService.findAndUpdateByRetryTargetTypeAndEventTargetStatus(
            eq(RetryTargetType.JMS), any(EventTargetStatus.class)))
        .thenReturn(Arrays.asList(MockRetryEntity.getForJmsRetry()));

    doNothing().when(jmsPublisher).publishRetries(any(), any(), any());

    when(jmsRecoveryService.findAndUpdateByRetryTargetTypeAndEventTargetStatus(
            eq(RetryTargetType.REST), any(EventTargetStatus.class), any(Long.class)))
        .thenReturn(Arrays.asList(MockRetryEntity.getForRestRetry()));

    doReturn(new ResponseEntity<String>("{}", HttpStatus.OK))
        .when(restUtils)
        .post(any(), any(), any(), any());

    jmsRetryjob.retriesSchedule();

    verify(jmsRecoveryService, times(1))
        .findAndUpdateByRetryTargetTypeAndEventTargetStatus(
            eq(RetryTargetType.REST), any(EventTargetStatus.class), any(Long.class));

    verify(jmsRecoveryService, times(1))
        .findAndUpdateByRetryTargetTypeAndEventTargetStatus(
            eq(RetryTargetType.JMS), any(EventTargetStatus.class));

    verify(jmsPublisher, times(1)).publishRetries(any(), any(), any());
    verify(restUtils, times(1)).post(anyString(), any(HttpHeaders.class), eq(null), anyString());
  }

  @Test
  public void testRetryRest() {
    RetryEntity retryEntity = MockRetryEntity.getForRestRetry();
    RestRetryRequest request = gson.fromJson(retryEntity.getPayload(), RestRetryRequest.class);

    doReturn(new ResponseEntity<String>("{}", HttpStatus.OK))
        .when(restUtils)
        .post(any(), any(), any(), any());

    jmsRetryjob.retryRest(retryEntity, request);

    retryEntity.setEventTargetStatus(EventTargetStatus.DELETE);
    verify(jmsRecoveryService, times(1)).save(retryEntity);
    reset(jmsRecoveryService);
    reset(restUtils);
  }

  @Test
  public void testRetryRest_WithoutEncoding() {
    RetryEntity retryEntity = MockRetryEntity.getForRestRetryWithoutEncoding();
    RestRetryRequest request = gson.fromJson(retryEntity.getPayload(), RestRetryRequest.class);

    doReturn(new ResponseEntity<String>("{}", HttpStatus.OK))
        .when(restUtils)
        .post(any(), any(), any(), any());

    jmsRetryjob.retryRest(retryEntity, request);

    retryEntity.setEventTargetStatus(EventTargetStatus.DELETE);
    verify(jmsRecoveryService, times(1)).save(retryEntity);
    reset(jmsRecoveryService);
    reset(restUtils);
  }

  @Test
  public void testRetryRest_Exception() {
    RetryEntity retryEntity = MockRetryEntity.getForRestRetry();
    RestRetryRequest request = gson.fromJson(retryEntity.getPayload(), RestRetryRequest.class);

    doReturn(new ResponseEntity<String>("{}", HttpStatus.INTERNAL_SERVER_ERROR))
        .when(restUtils)
        .post(any(), any(), any(), any());

    jmsRetryjob.retryRest(retryEntity, request);

    doReturn(null).when(restUtils).post(any(), any(), any(), any());

    jmsRetryjob.retryRest(retryEntity, request);

    verify(jmsRecoveryService, times(2)).save(retryEntity);
    reset(jmsRecoveryService);
    reset(restUtils);
  }

  @Test
  public void testRetryRest_ExceptionForSplunkAlertLog() {
    RetryEntity retryEntity = MockRetryEntity.getForRestRetry();

    RestRetryRequest request = gson.fromJson(retryEntity.getPayload(), RestRetryRequest.class);

    doReturn(new ResponseEntity<String>("{}", HttpStatus.INTERNAL_SERVER_ERROR))
        .when(restUtils)
        .post(any(), any(), any(), any());

    // regular exception not splunk
    retryEntity.setRetriesCount(4L);
    doReturn(5L).when(appConfig).getRestMaxRetries();

    jmsRetryjob.retryRest(retryEntity, request);

    // verify
    Assert.assertNull(retryEntity.getIsAlerted());

    doReturn(null).when(restUtils).post(any(), any(), any(), any());

    // logs splunk alert when
    retryEntity.setRetriesCount(5L);

    jmsRetryjob.retryRest(retryEntity, request);

    Assert.assertTrue(retryEntity.getIsAlerted());

    verify(jmsRecoveryService, times(2)).save(retryEntity);
    reset(jmsRecoveryService);
    reset(restUtils);
  }

  @Test
  public void staleJobTestEnable() {
    when(appConfig.getMarkRetryEventAsStaleRunEveryMin()).thenReturn(1);
    jmsRetryjob.identifyStaleAndMarkThemAsPending();
    verify(jmsRecoveryService, times(1)).findAndUpdateStaleEntries();
  }

  @Test
  public void staleJobTestDisable() {
    when(appConfig.getMarkRetryEventAsStaleRunEveryMin()).thenReturn(0);
    jmsRetryjob.identifyStaleAndMarkThemAsPending();
    verify(jmsRecoveryService, times(0)).findAndUpdateStaleEntries();
  }

  @Test
  public void testRetryRest_409Exception() {
    RetryEntity retryEntity = MockRetryEntity.getForRestRetry();
    RestRetryRequest request = gson.fromJson(retryEntity.getPayload(), RestRetryRequest.class);

    doReturn(new ResponseEntity<String>("{}", HttpStatus.CONFLICT))
        .when(restUtils)
        .post(any(), any(), any(), any());

    jmsRetryjob.retryRest(retryEntity, request);

    retryEntity.setEventTargetStatus(EventTargetStatus.DELETE);
    verify(jmsRecoveryService, times(1)).save(retryEntity);
    reset(jmsRecoveryService);
    reset(restUtils);
  }
}
