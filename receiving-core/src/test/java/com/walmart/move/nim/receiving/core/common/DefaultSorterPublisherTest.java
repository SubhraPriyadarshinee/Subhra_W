package com.walmart.move.nim.receiving.core.common;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.SorterExceptionReason;
import java.util.Date;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DefaultSorterPublisherTest extends ReceivingTestBase {
  @InjectMocks DefaultSorterPublisher sorterPublisher;
  @Mock JmsPublisher jmsPublisher;
  @Mock AppConfig appConfig;
  private String lpn = "a0000000000000001234";
  private static final String SORTER_EXCEPTION_TOPIC = "WMSOP/OA/LPN/EXCEPTION";
  private static final String SORTER_TOPIC = "WMSOP/OA/LPN";

  @BeforeClass
  public void setup() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32987);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setCorrelationId(UUID.randomUUID().toString());
  }

  @Test
  public void testPublishException() {
    when(appConfig.getSorterExceptionTopic()).thenReturn(SORTER_EXCEPTION_TOPIC);

    sorterPublisher.publishException(lpn, SorterExceptionReason.OVERAGE);

    ArgumentCaptor<ReceivingJMSEvent> sorterPublishCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(0))
        .publish(eq(SORTER_EXCEPTION_TOPIC), sorterPublishCaptor.capture(), eq(Boolean.TRUE));
  }

  @Test
  public void testPublishStoreLabel() {
    when(appConfig.getSorterTopic()).thenReturn(SORTER_TOPIC);

    sorterPublisher.publishStoreLabel(lpn, "6040", "US", new Date());

    ArgumentCaptor<ReceivingJMSEvent> sorterPublishCaptor =
        ArgumentCaptor.forClass(ReceivingJMSEvent.class);
    verify(jmsPublisher, times(0))
        .publish(eq(SORTER_TOPIC), sorterPublishCaptor.capture(), eq(Boolean.TRUE));
  }
}
