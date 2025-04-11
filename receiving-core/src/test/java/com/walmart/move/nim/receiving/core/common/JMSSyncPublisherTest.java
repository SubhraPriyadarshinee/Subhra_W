package com.walmart.move.nim.receiving.core.common;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import java.util.HashMap;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessagePostProcessor;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class JMSSyncPublisherTest extends AbstractTestNGSpringContextTests {

  @InjectMocks private JMSSyncPublisher jmsSyncPublisher;

  @InjectMocks private Gson gson;

  @Mock private JmsTemplate jmsQueueTemplate;

  @Mock private JmsTemplate jmsTopicTemplate;

  private ReceivingJMSEvent receivingJMSEvent;

  @Mock private AppConfig appConfig;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);

    ReflectionTestUtils.setField(jmsSyncPublisher, "gson", gson);

    receivingJMSEvent = new ReceivingJMSEvent(new HashMap<>(), "TEST MESSAGE");
  }

  @AfterMethod
  public void tearDown() {
    reset(jmsQueueTemplate);
    reset(jmsTopicTemplate);
    reset(appConfig);
  }

  @Test
  public void testSetHeader() {
    MessagePostProcessor messagePostProcessor =
        ReflectionTestUtils.invokeMethod(jmsSyncPublisher, "setHeaders", receivingJMSEvent);

    Assert.assertNotNull(messagePostProcessor);
  }

  @Test
  public void testPublishInternalToQueueWhenPubSubEnabled() {
    when(appConfig.getPubsubEnabled()).thenReturn(Boolean.TRUE);
    doNothing()
        .when(jmsQueueTemplate)
        .convertAndSend(anyString(), any(Object.class), any(MessagePostProcessor.class));

    jmsSyncPublisher.publishInternal("QUEUE.TEST", receivingJMSEvent);

    verify(jmsQueueTemplate, times(1))
        .convertAndSend(anyString(), any(Object.class), any(MessagePostProcessor.class));
  }

  @Test
  public void testPublishInternalToQueueWhenPubSubDisabled() {
    when(appConfig.getPubsubEnabled()).thenReturn(Boolean.FALSE);

    jmsSyncPublisher.publishInternal("QUEUE.TEST", receivingJMSEvent);

    verify(jmsQueueTemplate, times(0))
        .convertAndSend(anyString(), any(Object.class), any(MessagePostProcessor.class));
  }

  @Test
  public void testPublishInternalToTopicWhenPubSubEnabled() {
    when(appConfig.getPubsubEnabled()).thenReturn(Boolean.TRUE);
    doNothing()
        .when(jmsTopicTemplate)
        .convertAndSend(anyString(), any(Object.class), any(MessagePostProcessor.class));

    jmsSyncPublisher.publishInternal("TOPIC.TEST", receivingJMSEvent);

    verify(jmsTopicTemplate, times(1))
        .convertAndSend(anyString(), any(Object.class), any(MessagePostProcessor.class));
  }

  @Test
  public void testPublishInternalToTopicWhenPubSubDisabled() {
    when(appConfig.getPubsubEnabled()).thenReturn(Boolean.FALSE);

    jmsSyncPublisher.publishInternal("TOPIC.TEST", receivingJMSEvent);

    verify(jmsTopicTemplate, times(0))
        .convertAndSend(anyString(), any(Object.class), any(MessagePostProcessor.class));
  }

  @Test
  public void testPublishInternalException() {
    when(appConfig.getPubsubEnabled()).thenReturn(Boolean.TRUE);
    doThrow(RuntimeException.class)
        .when(jmsTopicTemplate)
        .convertAndSend(anyString(), any(Object.class), any(MessagePostProcessor.class));
    try {
      jmsSyncPublisher.publishInternal("TOPIC.TEST", receivingJMSEvent);
    } catch (Exception e) {
      // Will throw an error as not connecting to actual queue/topic in case of unit test
    }
    verify(jmsTopicTemplate, times(1))
        .convertAndSend(anyString(), any(Object.class), any(MessagePostProcessor.class));
  }

  @Test
  public void testPublishInternalWithNullTopic() {
    when(appConfig.getPubsubEnabled()).thenReturn(Boolean.TRUE);
    jmsSyncPublisher.publishInternal(null, receivingJMSEvent);

    verify(jmsTopicTemplate, times(0))
        .convertAndSend(anyString(), any(Object.class), any(MessagePostProcessor.class));
  }
}
