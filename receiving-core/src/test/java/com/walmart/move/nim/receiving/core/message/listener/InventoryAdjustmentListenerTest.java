package com.walmart.move.nim.receiving.core.message.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mockrunner.mock.jms.MockObjectMessage;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.core.service.InventoryEventProcessor;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.data.MockInventoryAdjustmentEvent;
import com.walmart.move.nim.receiving.data.MockMessageHeaders;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.TextMessage;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class InventoryAdjustmentListenerTest extends ReceivingTestBase {

  @InjectMocks private InventoryAdjustmentListener eventListener;

  @Spy private TextMessage textMessage;

  @Mock private AppConfig appConfig;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Mock private InventoryEventProcessor inventoryEventProcessor;

  @BeforeClass
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  public void cleanup() {
    reset(textMessage);
    reset(appConfig);
    reset(tenantSpecificConfigReader);
    reset(inventoryEventProcessor);
  }

  @Test
  public void testInventoryAdjustmentWithPubSubEnabled() throws JMSException, ReceivingException {
    when(appConfig.getPubsubEnabled()).thenReturn(Boolean.TRUE);
    when(textMessage.getText()).thenReturn(MockInventoryAdjustmentEvent.VALID_VTR_EVENT);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            MockHttpHeaders.getHeaders().getFirst(ReceivingConstants.TENENT_FACLITYNUM),
            ReceivingConstants.INVENTORY_ADJUSTMENT_PROCESSOR,
            EventProcessor.class))
        .thenReturn(inventoryEventProcessor);

    eventListener.inventoryAdjustmentListener(textMessage, MockMessageHeaders.getHeaders());

    verify(textMessage, times(1)).getText();
    verify(inventoryEventProcessor, times(1)).processEvent(any());
  }

  @Test
  public void testInventoryAdjustmentWithInvalidEventAndPubSubDisabled()
      throws JMSException, ReceivingException {
    when(appConfig.getPubsubEnabled()).thenReturn(Boolean.FALSE);

    when(textMessage.getText()).thenReturn(MockInventoryAdjustmentEvent.INVALID_VTR_EVENT);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            MockHttpHeaders.getHeaders().getFirst(ReceivingConstants.TENENT_FACLITYNUM),
            ReceivingConstants.INVENTORY_ADJUSTMENT_PROCESSOR,
            EventProcessor.class))
        .thenReturn(inventoryEventProcessor);

    eventListener.inventoryAdjustmentListener(textMessage, MockMessageHeaders.getHeaders());

    verify(textMessage, times(0)).getText();
    verify(inventoryEventProcessor, times(0)).processEvent(any());
  }

  @Test
  public void testInventoryAdjustmentWithInvalidMessageAndPubSubEnabled()
      throws JMSException, ReceivingException {
    when(appConfig.getPubsubEnabled()).thenReturn(Boolean.TRUE);
    Message message = new MockObjectMessage();
    eventListener.inventoryAdjustmentListener(message, MockMessageHeaders.getHeaders());

    verify(textMessage, times(0)).getText();
    verify(inventoryEventProcessor, times(0)).processEvent(any());
  }

  @Test
  public void testInventoryAdjustmentWithInvalidMessageEventAndPubSubEnabled()
      throws JMSException, ReceivingException {
    when(appConfig.getPubsubEnabled()).thenReturn(Boolean.TRUE);
    when(textMessage.getText())
        .thenReturn(MockInventoryAdjustmentEvent.INVALID_INVENTORY_ADJUSTMENT);
    eventListener.inventoryAdjustmentListener(textMessage, MockMessageHeaders.getHeaders());

    verify(textMessage, times(1)).getText();
    verify(inventoryEventProcessor, times(0)).processEvent(any());
  }

  @Test
  public void testInventoryAdjustmentWithInvalidMessageHeadersAndPubSubEnabled()
      throws JMSException, ReceivingException {
    when(appConfig.getPubsubEnabled()).thenReturn(Boolean.TRUE);
    when(textMessage.getText()).thenReturn(MockInventoryAdjustmentEvent.VALID_VTR_EVENT);
    eventListener.inventoryAdjustmentListener(
        textMessage, MockMessageHeaders.getHeadersWithoutTenantInformation());

    verify(textMessage, times(0)).getText();
    verify(inventoryEventProcessor, times(0)).processEvent(any());
  }

  @Test
  public void testInventoryAdjustmentWithEmptyStringAsMessageHeadersAndPubSubEnabled()
      throws JMSException, ReceivingException {

    when(appConfig.getPubsubEnabled()).thenReturn(Boolean.TRUE);
    when(textMessage.getText()).thenReturn(MockInventoryAdjustmentEvent.VALID_VTR_EVENT);
    eventListener.inventoryAdjustmentListener(
        textMessage, MockMessageHeaders.getHeadersWithEmptyStringAsTenantInformation());

    verify(textMessage, times(0)).getText();
    verify(inventoryEventProcessor, times(0)).processEvent(any());
  }
}
