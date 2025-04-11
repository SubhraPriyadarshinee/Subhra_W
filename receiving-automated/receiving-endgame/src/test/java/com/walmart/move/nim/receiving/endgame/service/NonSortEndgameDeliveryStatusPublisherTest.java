package com.walmart.move.nim.receiving.endgame.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.walmart.atlas.global.fc.service.IOutboxPublisherService;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.MaasTopics;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import com.walmart.move.nim.receiving.core.service.EndgameOutboxHandler;
import com.walmart.move.nim.receiving.endgame.mock.data.MockMessageData;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import java.util.HashMap;
import java.util.Optional;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class NonSortEndgameDeliveryStatusPublisherTest extends ReceivingTestBase {

  @Mock private JmsPublisher jmsPublisher;

  @Mock private MaasTopics maasTopics;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Mock private DeliveryMetaDataService endGameDeliveryMetaDataService;

  @InjectMocks private NonSortEndgameDeliveryStatusPublisher nonSortEndgameDeliveryStatusPublisher;
  @Mock private EndgameOutboxHandler endGameOutboxHandler;
  @MockBean private IOutboxPublisherService iOutboxPublisherService;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(
        endGameOutboxHandler, "iOutboxPublisherService", iOutboxPublisherService);
  }

  @AfterMethod
  public void resetMocks() {
    reset(jmsPublisher);
    reset(endGameDeliveryMetaDataService);
  }

  @Test
  public void testDeliveryUnloadCompleteEvent() {
    when(endGameDeliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(getDeliveryMetaData());
    when(maasTopics.getPubDeliveryStatusTopic()).thenReturn("SOME_TOPIC");
    doNothing().when(jmsPublisher).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
    nonSortEndgameDeliveryStatusPublisher.publish(
        MockMessageData.getDeliveryInfo(
            12345678L, "D101", "TLR101", DeliveryStatus.UNLOADING_COMPLETE),
        new HashMap<>());
    verify(jmsPublisher, times(1)).publish(anyString(), any(ReceivingJMSEvent.class), anyBoolean());
    verify(endGameDeliveryMetaDataService, times(1)).findByDeliveryNumber(anyString());
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
}
