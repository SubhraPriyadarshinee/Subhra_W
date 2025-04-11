package com.walmart.move.nim.receiving.acc.service;

import static org.mockito.Mockito.*;
import static org.testng.Assert.fail;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.service.DeliveryEventService;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ACCDeliveryEventProcessorTest extends ReceivingTestBase {

  @InjectMocks private ACCDeliveryEventProcessor genericPreLabelDeliveryEventProcessor;
  @Mock private PreLabelDeliveryService preLabelDeliveryService;
  @Mock private DeliveryEventService deliveryEventService;
  private DeliveryUpdateMessage deliveryUpdateMessage;

  @Mock private ACCDeliveryMetaDataService accDeliveryMetaDataService;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @BeforeClass
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    deliveryUpdateMessage =
        DeliveryUpdateMessage.builder()
            .deliveryNumber("123456")
            .countryCode("US")
            .siteNumber("6051")
            .deliveryStatus("ARV")
            .url("https://delivery.test")
            .build();
  }

  @AfterMethod
  public void tearDown() {
    reset(deliveryEventService);
    reset(preLabelDeliveryService);
    reset(accDeliveryMetaDataService);
  }

  @Test
  public void testProcessEventFinalized() {
    try {
      deliveryUpdateMessage.setEventType("FINALIZED");
      genericPreLabelDeliveryEventProcessor.processEvent(deliveryUpdateMessage);
      verify(deliveryEventService, times(1))
          .processOpenInstuctionsAfterDeliveryFinalized(deliveryUpdateMessage);
      verify(preLabelDeliveryService, times(0)).processDeliveryEvent(deliveryUpdateMessage);
    } catch (ReceivingException e) {
      fail("Exception not expected");
    }
  }

  @Test
  public void testProcessEventDoorAssign() {
    try {
      deliveryUpdateMessage.setEventType("DOOR_ASSIGN");
      genericPreLabelDeliveryEventProcessor.processEvent(deliveryUpdateMessage);
      verify(deliveryEventService, times(0))
          .processOpenInstuctionsAfterDeliveryFinalized(deliveryUpdateMessage);
      verify(preLabelDeliveryService, times(1)).processDeliveryEvent(deliveryUpdateMessage);
    } catch (ReceivingException e) {
      fail("Exception not expected");
    }
  }

  @Test
  public void testProcessEventDoorAssign_update_delivery_metadata() {
    try {
      deliveryUpdateMessage.setEventType("DOOR_ASSIGN");
      when(tenantSpecificConfigReader.isFeatureFlagEnabled(
              eq(ReceivingConstants.ENABLE_PUBLISH_UNLOAD_PROGRESS_AT_DELIVERY_COMPLETE)))
          .thenReturn(true);
      genericPreLabelDeliveryEventProcessor.processEvent(deliveryUpdateMessage);
      verify(accDeliveryMetaDataService, times(1)).persistMetaData(deliveryUpdateMessage);
      verify(deliveryEventService, times(0))
          .processOpenInstuctionsAfterDeliveryFinalized(deliveryUpdateMessage);
      verify(preLabelDeliveryService, times(1)).processDeliveryEvent(deliveryUpdateMessage);
    } catch (ReceivingException e) {
      fail("Exception not expected");
    }
  }
}
