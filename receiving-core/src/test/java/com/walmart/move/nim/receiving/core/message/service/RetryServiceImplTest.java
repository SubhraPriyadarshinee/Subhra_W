package com.walmart.move.nim.receiving.core.message.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.PurgeData;
import com.walmart.move.nim.receiving.core.entity.RetryEntity;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.mock.data.MockRetryEntity;
import com.walmart.move.nim.receiving.core.model.RestRetryRequest;
import com.walmart.move.nim.receiving.core.repositories.RetryRepository;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.constants.*;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.time.DateUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RetryServiceImplTest {

  @InjectMocks private RetryServiceImpl jmsRecoveryServiceImpl;

  @Mock private RetryRepository eventRetryRepo;
  @Mock private AppConfig appConfig;

  private ReceivingJMSEvent receivingJMSEvent;
  private PageRequest pageReq;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);

    receivingJMSEvent = new ReceivingJMSEvent(new HashMap<>(), "Hello Test");
    pageReq = PageRequest.of(0, 10);
  }

  @AfterMethod
  public void tearDown() {
    reset(eventRetryRepo);
  }

  @Test
  public void testSave() {
    when(eventRetryRepo.save(any(RetryEntity.class))).thenReturn(MockRetryEntity.getForJmsRetry());

    jmsRecoveryServiceImpl.save(MockRetryEntity.getForJmsRetry());

    verify(eventRetryRepo, times(1)).save(any(RetryEntity.class));
  }

  @Test
  public void testDelete() {
    doNothing().when(eventRetryRepo).deleteById(anyLong());

    jmsRecoveryServiceImpl.delete(MockRetryEntity.getForJmsRetry());

    verify(eventRetryRepo, times(1)).deleteById(anyLong());
  }

  @Test
  public void testFindByRetryTargetTypeAndEventTargetStatus() {
    when(appConfig.getJmsRetryPublishPageSize()).thenReturn(10);
    when(eventRetryRepo.findByRetryTargetTypeAndEventTargetStatusAndFuturePickupTimeLessThan(
            any(RetryTargetType.class), any(EventTargetStatus.class), any(Date.class), any()))
        .thenReturn(Arrays.asList(MockRetryEntity.getForJmsRetry()));

    when(jmsRecoveryServiceImpl.save(any(RetryEntity.class)))
        .thenReturn(MockRetryEntity.getForJmsRetry());

    jmsRecoveryServiceImpl.findAndUpdateByRetryTargetTypeAndEventTargetStatus(
        RetryTargetType.JMS, EventTargetStatus.PENDING);

    verify(eventRetryRepo, times(1))
        .findByRetryTargetTypeAndEventTargetStatusAndFuturePickupTimeLessThan(
            any(RetryTargetType.class), any(EventTargetStatus.class), any(), any());
  }

  @Test
  public void testFindByRetryTargetTypeAndEventTargetStatus_WithRetryCounts() {
    when(appConfig.getJmsRetryPublishPageSize()).thenReturn(10);
    when(eventRetryRepo
            .findByRetryTargetTypeAndEventTargetStatusAndFuturePickupTimeLessThanAndRetriesCountLessThan(
                any(RetryTargetType.class),
                any(EventTargetStatus.class),
                any(Date.class),
                any(Long.class),
                any()))
        .thenReturn(Arrays.asList(MockRetryEntity.getForRestRetry()));

    when(eventRetryRepo.save(any(RetryEntity.class))).thenReturn(MockRetryEntity.getForRestRetry());

    jmsRecoveryServiceImpl.findAndUpdateByRetryTargetTypeAndEventTargetStatus(
        RetryTargetType.REST, EventTargetStatus.PENDING, 5L);

    verify(eventRetryRepo, times(1))
        .findByRetryTargetTypeAndEventTargetStatusAndFuturePickupTimeLessThanAndRetriesCountLessThan(
            any(RetryTargetType.class),
            any(EventTargetStatus.class),
            any(Date.class),
            any(Long.class),
            any());
  }

  @Test
  public void testPutForRetries_Rest() {
    RetryEntity jmsEventRetryEntity = MockRetryEntity.getForRestRetry();
    RestRetryRequest restRetryRequest =
        new Gson().fromJson(jmsEventRetryEntity.getPayload(), RestRetryRequest.class);
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.add(ReceivingConstants.DCFIN_WMT_API_KEY, "a1-b1-c2");
    when(eventRetryRepo.save(any(RetryEntity.class))).thenReturn(jmsEventRetryEntity);
    RetryEntity jmsEventRetryEntityActual =
        jmsRecoveryServiceImpl.putForRetries(
            restRetryRequest.getUrl(),
            restRetryRequest.getHttpMethodType(),
            httpHeaders,
            restRetryRequest.getBody(),
            RetryTargetFlow.DCFIN_PURCHASE_POSTING);
    assertEquals(jmsEventRetryEntity, jmsEventRetryEntityActual);
    verify(eventRetryRepo, times(1)).save(any(RetryEntity.class));
  }

  @Test
  public void testPutForRetries_JMS() {
    RetryEntity jmsEventRetryEntity = MockRetryEntity.getForJmsRetry();
    when(eventRetryRepo.save(any(RetryEntity.class))).thenReturn(jmsEventRetryEntity);
    RetryEntity jmsEventRetryEntityActual =
        jmsRecoveryServiceImpl.putForRetries("Hello Test", receivingJMSEvent);
    assertEquals(jmsEventRetryEntity, jmsEventRetryEntityActual);
    verify(eventRetryRepo, times(1)).save(any(RetryEntity.class));
  }

  @Test
  public void testFindById() {
    Optional<RetryEntity> optionalJmsEventRetryEntity =
        Optional.of(MockRetryEntity.getForJmsRetry());
    when(eventRetryRepo.findById(anyLong())).thenReturn(optionalJmsEventRetryEntity);
    Optional<RetryEntity> optionalJmsEventRetryEntityActual = jmsRecoveryServiceImpl.findById(1L);
    assertEquals(optionalJmsEventRetryEntity, optionalJmsEventRetryEntityActual);
    verify(eventRetryRepo, times(1)).findById(anyLong());
  }

  @Test
  public void testPurge() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -60 * 24);

    RetryEntity jmsRetry = MockRetryEntity.getForJmsRetry();
    jmsRetry.setId(1L);
    jmsRetry.setFuturePickupTime(cal.getTime());

    RetryEntity jmsRetry1 = MockRetryEntity.getForJmsRetry();
    jmsRetry1.setId(10L);
    jmsRetry1.setFuturePickupTime(cal.getTime());

    when(eventRetryRepo.findByIdGreaterThanEqual(anyLong(), any()))
        .thenReturn(Arrays.asList(jmsRetry, jmsRetry1));
    doNothing().when(eventRetryRepo).deleteAll();
    PurgeData purgeData =
        PurgeData.builder()
            .purgeEntityType(PurgeEntityType.JMS_EVENT_RETRY)
            .eventTargetStatus(EventTargetStatus.PENDING)
            .lastDeleteId(0L)
            .build();

    long lastDeletedId = jmsRecoveryServiceImpl.purge(purgeData, pageReq, 30);
    Assert.assertEquals(lastDeletedId, 10L);
  }

  @Test
  public void testPurgeWithNoDataToDeleteBeforeDate() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -60 * 24);

    RetryEntity jmsRetry = MockRetryEntity.getForJmsRetry();
    jmsRetry.setId(1L);
    jmsRetry.setFuturePickupTime(cal.getTime());

    RetryEntity jmsRetry1 = MockRetryEntity.getForJmsRetry();
    jmsRetry1.setId(10L);
    jmsRetry1.setFuturePickupTime(cal.getTime());

    when(eventRetryRepo.findByIdGreaterThanEqual(anyLong(), any()))
        .thenReturn(Arrays.asList(jmsRetry, jmsRetry1));
    doNothing().when(eventRetryRepo).deleteAll();
    PurgeData purgeData =
        PurgeData.builder()
            .purgeEntityType(PurgeEntityType.JMS_EVENT_RETRY)
            .eventTargetStatus(EventTargetStatus.PENDING)
            .lastDeleteId(0L)
            .build();

    long lastDeletedId = jmsRecoveryServiceImpl.purge(purgeData, pageReq, 90);
    Assert.assertEquals(lastDeletedId, 0L);
  }

  @Test
  public void testPurgeWithFewDataToDeleteBeforeDate() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -60 * 24);

    RetryEntity jmsRetry = MockRetryEntity.getForJmsRetry();
    jmsRetry.setId(1L);
    jmsRetry.setFuturePickupTime(cal.getTime());

    RetryEntity jmsRetry1 = MockRetryEntity.getForJmsRetry();
    jmsRetry1.setId(10L);
    jmsRetry1.setFuturePickupTime(new Date());

    when(eventRetryRepo.findByIdGreaterThanEqual(anyLong(), any()))
        .thenReturn(Arrays.asList(jmsRetry, jmsRetry1));
    doNothing().when(eventRetryRepo).deleteAll();
    PurgeData purgeData =
        PurgeData.builder()
            .purgeEntityType(PurgeEntityType.JMS_EVENT_RETRY)
            .eventTargetStatus(EventTargetStatus.PENDING)
            .lastDeleteId(0L)
            .build();

    long lastDeletedId = jmsRecoveryServiceImpl.purge(purgeData, pageReq, 30);
    Assert.assertEquals(lastDeletedId, 1L);
  }

  @Test
  public void testFindAndUpdateStaleEntries() {
    when(appConfig.getJmsRetryPublishPageSize()).thenReturn(10);
    when(appConfig.getJmsRetryStaleTimeOut()).thenReturn(10);
    when(eventRetryRepo.findByEventTargetStatusAndLastUpdatedDateLessThan(
            any(EventTargetStatus.class), any(Date.class), any()))
        .thenReturn(Arrays.asList(MockRetryEntity.getInRetryJmsEvents()));

    jmsRecoveryServiceImpl.findAndUpdateStaleEntries();

    ArgumentCaptor<List<RetryEntity>> argumentCaptor = ArgumentCaptor.forClass(List.class);
    verify(eventRetryRepo, times(1)).saveAll(argumentCaptor.capture());
    List<RetryEntity> value = argumentCaptor.getValue();
    assertNotNull(value);
    assertEquals(value.size(), 1);
    assertEquals(value.get(0).getEventTargetStatus(), EventTargetStatus.PENDING);
    assertEquals(value.get(0).getRetriesCount(), Long.valueOf(0));
  }

  @Test
  public void testFindAndUpdateStaleEntries_NoEntries() {
    when(appConfig.getJmsRetryPublishPageSize()).thenReturn(10);
    when(appConfig.getJmsRetryStaleTimeOut()).thenReturn(10);
    when(eventRetryRepo.findByEventTargetStatusAndLastUpdatedDateLessThan(
            any(EventTargetStatus.class), any(Date.class), any()))
        .thenReturn(null);

    jmsRecoveryServiceImpl.findAndUpdateStaleEntries();

    verify(eventRetryRepo, times(0)).saveAll(any());
  }

  @Test
  public void test_resetJmsRetryCount() {

    doNothing()
        .when(eventRetryRepo)
        .resetRetryCountByDateRange(
            anyInt(), anyLong(), anyInt(), any(Date.class), any(Date.class), anyInt());

    jmsRecoveryServiceImpl.resetJmsRetryCount(
        10, 1l, 1, DateUtils.addDays(new Date(), -2), new Date(), 1);

    verify(eventRetryRepo, times(1))
        .resetRetryCountByDateRange(
            anyInt(), anyLong(), anyInt(), any(Date.class), any(Date.class), anyInt());
  }

  @Test
  public void test_resetJmsRetryCountById() {

    doNothing().when(eventRetryRepo).resetRetryCountById(anyInt(), anyLong(), anyInt(), anyList());

    jmsRecoveryServiceImpl.resetJmsRetryCount(10, 1l, 1, Arrays.asList(1l, 2l));

    verify(eventRetryRepo, times(1)).resetRetryCountById(anyInt(), anyLong(), anyInt(), anyList());
  }
}
