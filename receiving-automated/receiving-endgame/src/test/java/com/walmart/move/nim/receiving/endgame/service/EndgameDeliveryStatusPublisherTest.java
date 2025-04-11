package com.walmart.move.nim.receiving.endgame.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.walmart.atlas.global.fc.service.IOutboxPublisherService;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.KafkaConfig;
import com.walmart.move.nim.receiving.core.config.MaasTopics;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import com.walmart.move.nim.receiving.endgame.mock.data.MockMessageData;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import java.util.HashMap;
import java.util.Optional;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class EndgameDeliveryStatusPublisherTest extends ReceivingTestBase {

  @Mock private KafkaTemplate kafkaTemplate;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private KafkaConfig kafkaConfig;

  @Mock private JmsPublisher jmsPublisher;

  @Mock private MaasTopics maasTopics;

  @Mock private DeliveryMetaDataService endGameDeliveryMetaDataService;

  @InjectMocks private EndgameDeliveryStatusPublisher endgameDeliveryStatusPublisher;

  @Mock private IOutboxPublisherService iOutboxPublisherService;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(
        endgameDeliveryStatusPublisher, "deliveryStatusTopic", "delivery-status-topic");
  }

  @AfterMethod
  public void resetMocks() {
    reset(kafkaTemplate);
    reset(jmsPublisher);
    reset(endGameDeliveryMetaDataService);
  }

  @Test
  public void testDeliveryOpenEvent() {
    when(endGameDeliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(getDeliveryMetaData());
    when(maasTopics.getPubDeliveryStatusTopic()).thenReturn("SOME_TOPIC");
    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
    endgameDeliveryStatusPublisher.publish(
        MockMessageData.getDeliveryInfo(12345678L, "D101", "TLR101", DeliveryStatus.OPEN),
        new HashMap<>());
    verify(jmsPublisher, times(1)).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
    verify(endGameDeliveryMetaDataService, times(1)).findByDeliveryNumber(anyString());
    verify(kafkaTemplate, times(0)).send(any(Message.class));
  }

  @Test
  public void testDeliveryWorkingEvent() {
    when(endGameDeliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(getDeliveryMetaData());
    when(maasTopics.getPubDeliveryStatusTopic()).thenReturn("SOME_TOPIC");
    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
    endgameDeliveryStatusPublisher.publishMessage(
        MockMessageData.getDeliveryInfo(12345678L, "D101", "TLR101", DeliveryStatus.WRK),
        new HashMap<>());
    verify(jmsPublisher, times(1)).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
    verify(endGameDeliveryMetaDataService, times(0)).findByDeliveryNumber(anyString());
    verify(kafkaTemplate, times(0)).send(any(Message.class));
  }

  @Test
  public void testDeliveryCompleteEvent() {
    when(endGameDeliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(getDeliveryMetaData());
    when(maasTopics.getPubDeliveryStatusTopic()).thenReturn("SOME_TOPIC");
    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
    endgameDeliveryStatusPublisher.publish(
        MockMessageData.getDeliveryInfo(12345678L, "D101", "TLR101", DeliveryStatus.COMPLETE),
        new HashMap<>());
    verify(jmsPublisher, times(1)).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
    verify(endGameDeliveryMetaDataService, times(1)).findByDeliveryNumber(anyString());
    verify(kafkaTemplate, times(0)).send(any(Message.class));
  }

  @Test
  public void testDeliveryUnloadCompleteEvent() throws ReceivingException {
    when(endGameDeliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(getDeliveryMetaData());
    when(maasTopics.getPubDeliveryStatusTopic()).thenReturn("SOME_TOPIC");
    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
    when(tenantSpecificConfigReader.isUnloadCompleteOutboxKafkaPublishEnabled(anyInt())).thenReturn(false);
    endgameDeliveryStatusPublisher.publish(
        MockMessageData.getDeliveryInfo(
            12345678L, "D101", "TLR101", DeliveryStatus.UNLOADING_COMPLETE),
        new HashMap<>());
    verify(jmsPublisher, times(1)).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
    verify(endGameDeliveryMetaDataService, times(1)).findByDeliveryNumber(anyString());
    verify(kafkaTemplate, times(1)).send(any(Message.class));
  }

  private Optional<DeliveryMetaData> getDeliveryMetaData() {

    DeliveryMetaData deliveryMetaData =
        DeliveryMetaData.builder()
            .totalCaseCount(10)
            .totalCaseLabelSent(10)
            .deliveryNumber("12345678")
            .trailerNumber("TLR123456")
            .doorNumber("D101")
            .build();
    return Optional.of(deliveryMetaData);
  }

  @Test
  public void publishMessageToKafkaWhenUnloadCompleteOutboxKafkaPublishEnabled() throws ReceivingException {
    when(endGameDeliveryMetaDataService.findByDeliveryNumber(anyString()))
            .thenReturn(getDeliveryMetaData());
    when(maasTopics.getPubDeliveryStatusTopic()).thenReturn("SOME_TOPIC");
    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
    when(tenantSpecificConfigReader.isUnloadCompleteOutboxKafkaPublishEnabled(anyInt())).thenReturn(true);
    doNothing().when(iOutboxPublisherService).publishToKafka(anyString(), anyMap(), anyString(), anyInt(), anyString(), anyString());

    DeliveryInfo deliveryInfo = MockMessageData.getDeliveryInfo(
            12345678L, "D101", "TLR101", DeliveryStatus.UNLOADING_COMPLETE);
    endgameDeliveryStatusPublisher.publish(deliveryInfo, new HashMap<>());

    verify(iOutboxPublisherService, times(1)).publishToKafka(anyString(), anyMap(), anyString(), anyInt(), anyString(), anyString());
  }
}
