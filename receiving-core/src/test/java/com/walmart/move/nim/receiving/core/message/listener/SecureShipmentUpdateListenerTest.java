package com.walmart.move.nim.receiving.core.message.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertNotNull;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.event.processor.update.DefaultUpdateEventProcessor;
import com.walmart.move.nim.receiving.core.helper.DeliveryShipmentUpdateHelper;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.data.MockMessageHeaders;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class SecureShipmentUpdateListenerTest extends ReceivingTestBase {
  @InjectMocks private SecureDeliveryShipmentUpdateListener secureDeliveryShipmentUpdateListener;
  @InjectMocks private DeliveryShipmentUpdateHelper deliveryShipmentUpdateHelper;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private DefaultUpdateEventProcessor defaultEventProcessor;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(
        secureDeliveryShipmentUpdateListener,
        "deliveryShipmentUpdateHelper",
        deliveryShipmentUpdateHelper);
  }

  @AfterMethod
  public void tearDown() {
    reset(tenantSpecificConfigReader);
  }

  @Test
  public void testListen() throws ReceivingException {
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(),
            eq(ReceivingConstants.KAFKA_DELIVERY_EVENT_HANDLER),
            eq(EventProcessor.class)))
        .thenReturn(defaultEventProcessor);
    String eventMessage =
        getFileAsString("../receiving-test/src/main/resources/json/fixtureShipmentAddedEvent.json");
    secureDeliveryShipmentUpdateListener.listen(
        eventMessage, MockMessageHeaders.getMockKafkaListenerHeaders());
    ArgumentCaptor<DeliveryUpdateMessage> captor =
        ArgumentCaptor.forClass(DeliveryUpdateMessage.class);
    verify(defaultEventProcessor, times(1)).processEvent(captor.capture());
    DeliveryUpdateMessage value = captor.getValue();
    assertNotNull(value.getEvent());
    assertNotNull(value.getPayload());
    assertNotNull(value.getEvent().getWMTCorrelationId());
  }

  @Test(
      expectedExceptions = ReceivingInternalException.class,
      expectedExceptionsMessageRegExp = "Unable to process the delivery update message.*")
  public void testListenException() throws ReceivingException {
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(),
            eq(ReceivingConstants.KAFKA_DELIVERY_EVENT_HANDLER),
            eq(EventProcessor.class)))
        .thenReturn(defaultEventProcessor);

    doThrow(new ReceivingException("exception"))
        .when(defaultEventProcessor)
        .processEvent(any(DeliveryUpdateMessage.class));
    String eventMessage =
        getFileAsString("../receiving-test/src/main/resources/json/fixtureShipmentAddedEvent.json");
    secureDeliveryShipmentUpdateListener.listen(
        eventMessage, MockMessageHeaders.getMockKafkaListenerHeaders());
    verify(defaultEventProcessor, times(0)).processEvent(any());
  }
}
