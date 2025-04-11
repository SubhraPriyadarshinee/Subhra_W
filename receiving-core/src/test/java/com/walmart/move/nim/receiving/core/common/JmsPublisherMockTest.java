/** */
package com.walmart.move.nim.receiving.core.common;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.RetryEntity;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.message.service.RetryService;
import com.walmart.move.nim.receiving.core.message.service.RetryServiceImpl;
import com.walmart.move.nim.receiving.core.repositories.RetryRepository;
import com.walmart.move.nim.receiving.core.service.AsyncPersister;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.RetryTargetType;
import java.util.ArrayList;
import java.util.HashMap;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** @author Punith */
// TODO Needs to optimize the configuration as This is loading all the unnecessary configurations
// like Loading MaaS and others
public class JmsPublisherMockTest extends ReceivingTestBase {

  @InjectMocks private JmsPublisher publisher;

  @Spy private RetryServiceImpl jmsRecoveryServiceImpl;

  @InjectMocks private Gson gson;

  @Mock private RetryRepository eventRetryRepo;

  @Spy private AsyncPersister asyncPersister;

  @Mock private RetryService jmsRecoveryService;

  @Mock private JMSSyncPublisher jmsSyncPublisher;

  @Mock private AppConfig appConfig;

  private ReceivingJMSEvent receivingJMSEvent;
  private ReceivingJMSEvent receivingJMSEvent2;
  private RetryEntity jmsEventRetryEntity;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);

    ReflectionTestUtils.setField(publisher, "gson", gson);
    ReflectionTestUtils.setField(publisher, "jmsRecoveryService", jmsRecoveryServiceImpl);
    ReflectionTestUtils.setField(asyncPersister, "jmsRecoveryService", jmsRecoveryServiceImpl);
    ReflectionTestUtils.setField(
        jmsRecoveryServiceImpl, "applicationRetriesRepository", eventRetryRepo);
    ReflectionTestUtils.setField(asyncPersister, "jmsSyncPublisher", jmsSyncPublisher);

    receivingJMSEvent = new ReceivingJMSEvent(new HashMap<>(), "Hello Test");
    jmsEventRetryEntity = new RetryEntity();
    jmsEventRetryEntity.setId(0L);
    jmsEventRetryEntity.setJmsQueueName("QUEUE.TEST");
    jmsEventRetryEntity.setRetryTargetType(RetryTargetType.JMS);
    jmsEventRetryEntity.setIsAlerted(true);
    jmsEventRetryEntity.setPayload(gson.toJson(receivingJMSEvent));
    jmsEventRetryEntity.setRetriesCount(0L);
    jmsEventRetryEntity.setEventTargetStatus(EventTargetStatus.PENDING);

    receivingJMSEvent2 = new ReceivingJMSEvent(new HashMap<>(), "Hello Test");
  }

  @AfterMethod
  public void tearDown() {
    reset(eventRetryRepo);
    reset(jmsRecoveryService);
    reset(asyncPersister);
    reset(jmsSyncPublisher);
  }

  @Test
  public void testSyncPublish() {
    when(appConfig.getJmsAsyncPublishEnabled()).thenReturn(Boolean.FALSE);
    doNothing().when(jmsSyncPublisher).publishInternal(anyString(), any(ReceivingJMSEvent.class));

    publisher.publish("QUEUE.TEST", receivingJMSEvent, Boolean.FALSE);

    verify(jmsSyncPublisher, times(1)).publishInternal(anyString(), any(ReceivingJMSEvent.class));
  }

  @Test
  public void testAsyncPublish() {
    when(appConfig.getJmsAsyncPublishEnabled()).thenReturn(Boolean.TRUE);
    when(jmsRecoveryService.save(any(RetryEntity.class))).thenReturn(jmsEventRetryEntity);
    doReturn(new RetryEntity())
        .when(jmsRecoveryService)
        .putForRetries("QUEUE.TEST", receivingJMSEvent);
    doNothing().when(asyncPersister).asyncPublish(any(), any(), any(), any());

    publisher.publish("QUEUE.TEST", receivingJMSEvent, Boolean.TRUE);

    verify(asyncPersister, times(1)).asyncPublish(any(), any(), any(), any());
    reset(asyncPersister);
  }

  @Test
  public void testAsyncPublish_WhenPutForRetriesIsDisabled() {
    when(appConfig.getJmsAsyncPublishEnabled()).thenReturn(Boolean.TRUE);
    when(jmsRecoveryService.save(any(RetryEntity.class))).thenReturn(jmsEventRetryEntity);
    doNothing().when(asyncPersister).asyncPublish(any(), any(), any(), any());
    publisher.publish("QUEUE.TEST", receivingJMSEvent, Boolean.FALSE);
    verify(asyncPersister, times(1)).asyncPublish(any(), any(), any(), any());
    reset(asyncPersister);
  }

  @Test
  public void testPublishRetries() {
    when(eventRetryRepo.save(any(RetryEntity.class))).thenReturn(new RetryEntity());
    doNothing().when(jmsSyncPublisher).publishInternal(anyString(), any(ReceivingJMSEvent.class));

    RetryEntity retryEntity = new RetryEntity();
    retryEntity.setRetriesCount(0L);
    ReceivingJMSEvent receivingJMSEvent = new ReceivingJMSEvent(new HashMap<>(), "Hello Test");
    publisher.publishRetries("QUEUE.TEST.RETRY", retryEntity, receivingJMSEvent);
  }

  @Test
  public void publishSequentially() {

    when(appConfig.getJmsAsyncPublishEnabled()).thenReturn(Boolean.TRUE);
    when(jmsRecoveryService.save(any(RetryEntity.class))).thenReturn(jmsEventRetryEntity);
    doReturn(new RetryEntity())
        .when(jmsRecoveryService)
        .putForRetries("QUEUE.TEST", receivingJMSEvent);
    when(eventRetryRepo.save(any(RetryEntity.class))).thenReturn(new RetryEntity());

    doReturn(jmsEventRetryEntity)
        .when(jmsRecoveryService)
        .putForRetries(anyString(), any(ReceivingJMSEvent.class));

    ArrayList<ReceivingJMSEvent> receivingJMSEvents = new ArrayList<>(2);
    receivingJMSEvents.add(receivingJMSEvent);
    receivingJMSEvents.add(receivingJMSEvent2);

    publisher.publishSequentially("QUEUE.TEST", receivingJMSEvents, Boolean.TRUE);

    verify(asyncPersister, times(1)).asyncPublishSequentially(any(), any(), any());
    verify(jmsSyncPublisher, times(2)).publishInternal(anyString(), any(ReceivingJMSEvent.class));

    reset(asyncPersister);
    reset(jmsRecoveryService);
  }
}
