package com.walmart.move.nim.receiving.fixture.orchestrator;

import com.walmart.move.nim.receiving.fixture.common.FixtureConstants;
import com.walmart.move.nim.receiving.fixture.event.processor.AbstractEventProcessor;
import com.walmart.move.nim.receiving.fixture.event.processor.ShipmentPersistProcessor;
import com.walmart.move.nim.receiving.fixture.event.processor.ShipmentReconcileProcessor;
import com.walmart.move.nim.receiving.fixture.event.processor.ShipmentUpdateEventProcessor;
import com.walmart.move.nim.receiving.fixture.model.ShipmentEvent;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShipmentEventProcessStrategy implements IOrchestratorStrategy<ShipmentEvent> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ShipmentEventProcessStrategy.class);

  private AbstractEventProcessor<ShipmentEvent> initialState;

  @Resource(name = FixtureConstants.SHIPMENT_RECONCILE_PROCESSOR_BEAN)
  private ShipmentReconcileProcessor shipmentReconcileProcessor;

  @Resource(name = FixtureConstants.SHIPMENT_UPDATE_PROCESSOR_BEAN)
  private ShipmentUpdateEventProcessor shipmentUpdateEventProcessor;

  @Resource(name = FixtureConstants.SHIPMENT_PERSIST_PROCESSOR_BEAN)
  private ShipmentPersistProcessor shipmentPersistProcessor;

  @PostConstruct
  public void init() {
    shipmentReconcileProcessor.setNextProcessor(shipmentUpdateEventProcessor);
    shipmentUpdateEventProcessor.setNextProcessor(shipmentPersistProcessor);

    initialState = shipmentReconcileProcessor;
  }

  @Override
  public void orchestrateEvent(ShipmentEvent shipmentEvent) {
    initialState.execute(shipmentEvent);
  }
}
