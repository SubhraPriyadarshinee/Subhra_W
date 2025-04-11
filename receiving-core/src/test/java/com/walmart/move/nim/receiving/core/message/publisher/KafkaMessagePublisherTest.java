package com.walmart.move.nim.receiving.core.message.publisher;

import static com.walmart.move.nim.receiving.core.message.publisher.KafkaMoveMessagePublisherTest.getMockHeaders;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.AssertJUnit.assertEquals;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.service.RapidRelayerService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class KafkaMessagePublisherTest {

  @Mock KafkaTemplate secureKafkaTemplate;
  @Mock RapidRelayerService rapidRelayerService;
  @Mock TenantSpecificConfigReader tenantSpecificConfigReader;
  @InjectMocks private KafkaMessagePublisher kafkaMessagePublisher;

  @BeforeMethod
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(1234);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setCorrelationId("aafa2fcc-d299-4663-aa64-ba6f7970463");
  }

  @Test
  public void testPublish() {

    when(secureKafkaTemplate.send(any(Message.class))).thenReturn(null);

    kafkaMessagePublisher.publish("testKey", new Object(), "testTopic", getMockHeaders());

    verify(secureKafkaTemplate, times(1)).send(any(Message.class));
  }

  @Test
  public void testPublish_outbox() throws ReceivingException {

    when(secureKafkaTemplate.send(any(Message.class))).thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);

    kafkaMessagePublisher.publish("testKey", new Object(), "testTopic", getMockHeaders());

    verify(rapidRelayerService, times(1))
        .produceMessage(anyString(), anyString(), anyString(), anyMap());
  }

  @Test
  public void testPublish_error() {

    try {

      when(secureKafkaTemplate.send(any(Message.class))).thenThrow(new NullPointerException());

      kafkaMessagePublisher.publish("testKey", new Object(), "testTopic", getMockHeaders());

      verify(secureKafkaTemplate, times(1)).send(any(Message.class));
    } catch (ReceivingInternalException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.KAFKA_NOT_ACCESSABLE);
      assertEquals(e.getDescription(), ReceivingConstants.KAFKA_UNABLE_TO_SEND_ERROR_MSG);
    }
  }
}
