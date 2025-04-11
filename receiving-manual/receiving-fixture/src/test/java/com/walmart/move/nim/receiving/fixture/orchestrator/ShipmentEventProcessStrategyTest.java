package com.walmart.move.nim.receiving.fixture.orchestrator;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Pack;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Shipment;
import com.walmart.move.nim.receiving.fixture.event.processor.ShipmentPersistProcessor;
import com.walmart.move.nim.receiving.fixture.event.processor.ShipmentReconcileProcessor;
import com.walmart.move.nim.receiving.fixture.event.processor.ShipmentUpdateEventProcessor;
import com.walmart.move.nim.receiving.fixture.model.ShipmentEvent;
import java.util.ArrayList;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ShipmentEventProcessStrategyTest extends ReceivingTestBase {

  @InjectMocks private ShipmentEventProcessStrategy shipmentEventProcessStrategy;

  @InjectMocks private ShipmentReconcileProcessor shipmentReconcileProcessor;

  @InjectMocks private ShipmentUpdateEventProcessor shipmentUpdateEventProcessor;

  @InjectMocks private ShipmentPersistProcessor shipmentPersistProcessor;

  @BeforeClass
  public void setup() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(
        shipmentEventProcessStrategy, "initialState", shipmentReconcileProcessor);
    ReflectionTestUtils.setField(
        shipmentReconcileProcessor,
        "selfReferenceShipmentReconcileProcessor",
        shipmentReconcileProcessor);
    ReflectionTestUtils.setField(
        shipmentUpdateEventProcessor,
        "selfReferenceShipmentUpdateProcessor",
        shipmentUpdateEventProcessor);
    ReflectionTestUtils.setField(
        shipmentPersistProcessor,
        "selfReferenceShipmentPersistProcessor",
        shipmentPersistProcessor);
    ReflectionTestUtils.setField(
        shipmentReconcileProcessor, "nextProcessor", shipmentUpdateEventProcessor);
    ReflectionTestUtils.setField(
        shipmentUpdateEventProcessor, "nextProcessor", shipmentPersistProcessor);
  }

  @Test
  public void orchestrateEvent() {
    ShipmentEvent shipmentEvent = new ShipmentEvent();
    Shipment shipment = new Shipment();
    shipment.setShipmentNumber("1");
    shipmentEvent.setShipment(shipment);
    List<Pack> packList = new ArrayList<>();
    shipmentEvent.setPackList(packList);
    shipmentEventProcessStrategy.orchestrateEvent(shipmentEvent);
  }
}
