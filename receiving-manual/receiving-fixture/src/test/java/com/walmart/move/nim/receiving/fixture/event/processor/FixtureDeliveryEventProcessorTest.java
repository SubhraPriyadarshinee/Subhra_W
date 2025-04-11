package com.walmart.move.nim.receiving.fixture.event.processor;

import static org.mockito.Mockito.*;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.fixture.config.FixtureManagedConfig;
import com.walmart.move.nim.receiving.fixture.mock.data.FixtureMockData;
import com.walmart.move.nim.receiving.fixture.model.ShipmentEvent;
import com.walmart.move.nim.receiving.fixture.orchestrator.IOrchestratorStrategy;
import com.walmart.move.nim.receiving.fixture.service.PalletReceivingService;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class FixtureDeliveryEventProcessorTest extends ReceivingTestBase {
  @InjectMocks private FixtureDeliveryEventProcessor fixtureDeliveryEventProcessor;

  @Mock private PalletReceivingService palletReceivingService;

  @Mock private IOrchestratorStrategy<ShipmentEvent> shipmentEventProcessStrategy;

  @Mock private FixtureManagedConfig fixtureManagedConfig;

  @BeforeClass
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testProcessEvent_InvalidEvent() throws ReceivingException {
    when(fixtureManagedConfig.getShipmentEventProcessorEnabled()).thenReturn(Boolean.FALSE);
    DeliveryUpdateMessage deliveryUpdateMessage =
        JacksonParser.convertJsonToObject(
            FixtureMockData.getInvalidShipmentEventTypePayload(), DeliveryUpdateMessage.class);
    fixtureDeliveryEventProcessor.processEvent(deliveryUpdateMessage);
    verify(palletReceivingService, times(0)).processShipmentEvent(deliveryUpdateMessage);
  }

  @Test
  public void testProcessEvent_FixtureDeliveryEventProcessorEnabled() throws ReceivingException {
    when(fixtureManagedConfig.getShipmentEventProcessorEnabled()).thenReturn(Boolean.TRUE);
    DeliveryUpdateMessage deliveryUpdateMessage =
        JacksonParser.convertJsonToObject(
            FixtureMockData.getShipmentAddedEventPayload(), DeliveryUpdateMessage.class);
    fixtureDeliveryEventProcessor.processEvent(deliveryUpdateMessage);
    verify(shipmentEventProcessStrategy, times(1)).orchestrateEvent(any(ShipmentEvent.class));
    verify(palletReceivingService, times(0)).processShipmentEvent(deliveryUpdateMessage);
  }

  @Test
  public void testProcessEvent_FixtureDeliveryEventProcessorDisabled() throws ReceivingException {
    when(fixtureManagedConfig.getShipmentEventProcessorEnabled()).thenReturn(Boolean.FALSE);
    DeliveryUpdateMessage deliveryUpdateMessage =
        JacksonParser.convertJsonToObject(
            FixtureMockData.getShipmentAddedEventPayload(), DeliveryUpdateMessage.class);
    fixtureDeliveryEventProcessor.processEvent(deliveryUpdateMessage);
    verify(shipmentEventProcessStrategy, times(0)).orchestrateEvent(any(ShipmentEvent.class));
    verify(palletReceivingService, times(1)).processShipmentEvent(deliveryUpdateMessage);
  }
}
