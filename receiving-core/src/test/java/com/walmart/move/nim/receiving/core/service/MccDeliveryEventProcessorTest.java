package com.walmart.move.nim.receiving.core.service;

import static org.mockito.Mockito.reset;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class MccDeliveryEventProcessorTest extends ReceivingTestBase {
  private DeliveryUpdateMessage deliveryUpdateMessage;
  @InjectMocks private MccDeliveryEventProcessor mccDeliveryEventProcessor;
  @Mock private DeliveryEventService deliveryEventService;

  @BeforeClass
  private void initMocks() {
    MockitoAnnotations.initMocks(this);

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32987);
    TenantContext.setCorrelationId("qwert");

    deliveryUpdateMessage = new DeliveryUpdateMessage();
    deliveryUpdateMessage.setDeliveryNumber("21119003");
    deliveryUpdateMessage.setEventType("FINALIZED");
    deliveryUpdateMessage.setDeliveryStatus("FNL");
    deliveryUpdateMessage.setCountryCode("US");
    deliveryUpdateMessage.setSiteNumber("32987");
    deliveryUpdateMessage.setDeliveryNumber("21119003");
    deliveryUpdateMessage.setDeliveryStatus("FNL");
    deliveryUpdateMessage.setUrl(
        "https://dev.gdm.prod.us.walmart.net/document/v2/deliveries/21119003");
  }

  @AfterMethod
  public void cleanup() {
    reset(deliveryEventService);
  }

  @Test
  public void testFinalizedEventTypeMessage() throws ReceivingException {

    deliveryEventService.processOpenInstuctionsAfterDeliveryFinalized(deliveryUpdateMessage);

    mccDeliveryEventProcessor.processEvent(deliveryUpdateMessage);
  }

  @Test
  public void testNotFinalizedEventTypeMessage() throws ReceivingException {
    deliveryUpdateMessage.setEventType("SCHEDULED");
    mccDeliveryEventProcessor.processEvent(deliveryUpdateMessage);
  }
}
