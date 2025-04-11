package com.walmart.move.nim.receiving.fixture.event.processor;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.fixture.common.FixtureConstants;
import com.walmart.move.nim.receiving.fixture.config.FixtureManagedConfig;
import com.walmart.move.nim.receiving.fixture.model.ShipmentEvent;
import com.walmart.move.nim.receiving.fixture.orchestrator.IOrchestratorStrategy;
import com.walmart.move.nim.receiving.fixture.service.PalletReceivingService;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import io.strati.configuration.annotation.ManagedConfiguration;
import javax.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;

public class FixtureDeliveryEventProcessor implements EventProcessor {

  @Autowired private PalletReceivingService palletReceivingService;

  @Resource(name = FixtureConstants.SHIPMENT_EVENT_PROCESSOR_STRATEGY)
  private IOrchestratorStrategy<ShipmentEvent> shipmentEventProcessStrategy;

  @ManagedConfiguration private FixtureManagedConfig fixtureManagedConfig;

  @Override
  public void processEvent(MessageData messageData) throws ReceivingException {
    DeliveryUpdateMessage deliveryUpdateMessage = (DeliveryUpdateMessage) messageData;
    if (ReceivingUtils.isValidShipmentEvent(deliveryUpdateMessage.getEvent())) {
      if (fixtureManagedConfig.getShipmentEventProcessorEnabled()) {
        ShipmentEvent shipmentEvent = new ShipmentEvent();
        shipmentEvent.setPackList(deliveryUpdateMessage.getPayload().getPacks());
        shipmentEvent.setShipment(deliveryUpdateMessage.getPayload().getShipment());
        shipmentEvent.setEventType(deliveryUpdateMessage.getEvent().getType());
        shipmentEventProcessStrategy.orchestrateEvent(shipmentEvent);
      } else palletReceivingService.processShipmentEvent(deliveryUpdateMessage);
    }
  }
}
