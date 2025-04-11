package com.walmart.move.nim.receiving.endgame.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.walmart.atlas.global.fc.service.IOutboxPublisherService;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.KafkaConfig;
import com.walmart.move.nim.receiving.core.config.MaasTopics;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.endgame.mock.data.MockMessageData;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import java.util.HashMap;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class EndgameDeliveryStatusEventProcessorTest extends ReceivingTestBase {

  @Mock private KafkaTemplate secureKafkaTemplate;
  @Mock private KafkaConfig kafkaConfig;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private MaasTopics maasTopics;
  @InjectMocks private EndgameDeliveryStatusPublisher endgameDeliveryStatusPublisher;
  @Mock private JmsPublisher jmsPublisher;
  @Mock private EndGameDeliveryMetaDataService endGameDeliveryMetaDataService;
  @MockBean private IOutboxPublisherService iOutboxPublisherService;

  private Gson gson;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    this.gson = new Gson();
    ReflectionTestUtils.setField(endgameDeliveryStatusPublisher, "gson", this.gson);
    ReflectionTestUtils.setField(
        endgameDeliveryStatusPublisher, "deliveryStatusTopic", "SOME_TOPIC");
  }

  @AfterMethod
  public void resetMocks() {
    reset(secureKafkaTemplate);
  }

  @Test
  public void testInvalidPayload() throws ReceivingException {
    DeliveryInfo messageData =
        MockMessageData.getDeliveryInfo(12345678L, "D101", "TLR12345", DeliveryStatus.WORKING);
    when(maasTopics.getPubDeliveryStatusTopic()).thenReturn("SOME_TOPIC");
    endgameDeliveryStatusPublisher.publish(messageData, new HashMap<>());
    verify(secureKafkaTemplate, times(0)).send(any(Message.class));
  }

  @Test
  public void testValidPayload() throws ReceivingException {
    DeliveryInfo messageData =
        MockMessageData.getDeliveryInfo(
            12345678L, "D101", "TLR12345", DeliveryStatus.UNLOADING_COMPLETE);
    when(maasTopics.getPubDeliveryStatusTopic()).thenReturn("SOME_TOPIC");
    endgameDeliveryStatusPublisher.publish(messageData, new HashMap<>());
    verify(secureKafkaTemplate, times(1)).send(any(Message.class));
  }
}
