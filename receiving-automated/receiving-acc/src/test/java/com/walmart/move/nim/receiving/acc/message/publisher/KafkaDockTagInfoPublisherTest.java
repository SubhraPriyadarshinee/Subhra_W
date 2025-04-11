package com.walmart.move.nim.receiving.acc.message.publisher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.model.docktag.DockTagInfo;
import com.walmart.move.nim.receiving.data.MockMessageHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.Map;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class KafkaDockTagInfoPublisherTest {
  @InjectMocks private KafkaDockTagInfoPublisher kafkaDocktagInfoPublisher;
  @Mock private KafkaTemplate securePublisher;

  private DockTagInfo dockTagInfo;
  private Map<String, Object> headers;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32888);
    dockTagInfo =
        DockTagInfo.builder()
            .trackingId("e328980000100000001412462")
            .location("123")
            .deliveryNumber(123456L)
            .containerType("PALLET")
            .skuIndicator("SINGLE")
            .priority(3)
            .build();
    headers = MockMessageHeaders.getHeadersMap();
  }

  @Test
  public void testPublish() {
    when(securePublisher.send(any(Message.class))).thenReturn(null);
    kafkaDocktagInfoPublisher.publish(dockTagInfo, headers);
    verify(securePublisher, times(1)).send(any(Message.class));
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testPublish_throwsException() {
    when(securePublisher.send(any(Message.class))).thenThrow(new NullPointerException());
    kafkaDocktagInfoPublisher.publish(dockTagInfo, headers);
    verify(securePublisher, times(1)).send(any(Message.class));
  }
}
