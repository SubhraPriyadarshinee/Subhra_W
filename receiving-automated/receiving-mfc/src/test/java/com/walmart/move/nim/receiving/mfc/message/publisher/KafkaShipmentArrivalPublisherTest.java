package com.walmart.move.nim.receiving.mfc.message.publisher;

import static org.mockito.Mockito.*;
import static org.testng.Assert.assertThrows;

import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Shipment;
import com.walmart.move.nim.receiving.mfc.model.ngr.NGRShipment;
import com.walmart.move.nim.receiving.mfc.transformer.NGRShipmentTransformer;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class KafkaShipmentArrivalPublisherTest {

  @Mock private KafkaTemplate<String, String> kafkaTemplate;

  @InjectMocks private ShipmentArrivalPublisher shipmentArrivalPublisher;

  @Mock private NGRShipmentTransformer ngrShipmentTransformer;

  @BeforeClass
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(
        shipmentArrivalPublisher, "shipmentArrivalTopic", "SHIPMENT_TEST_TOPIC");
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32818);
  }

  @AfterMethod
  public void resetMocks() {
    Mockito.reset(kafkaTemplate);
    Mockito.reset(ngrShipmentTransformer);
  }

  @Test
  public void testPublish() {
    Delivery delivery = new Delivery();
    delivery.setDeliveryNumber(11234l);
    NGRShipment ngrShipment =
        NGRShipment.builder().delivery(delivery).shipment(new Shipment()).build();

    when(kafkaTemplate.send(any(Message.class))).thenReturn(null);

    shipmentArrivalPublisher.publish(ngrShipment);

    verify(kafkaTemplate, times(1)).send(any(Message.class));
  }

  @Test
  public void testPublish_withException() {
    Delivery delivery = new Delivery();
    delivery.setDeliveryNumber(11234l);
    NGRShipment ngrShipment =
        NGRShipment.builder().delivery(delivery).shipment(new Shipment()).build();

    when(kafkaTemplate.send(any(Message.class))).thenThrow(new RuntimeException("Test Exception"));

    assertThrows(
        ReceivingInternalException.class, () -> shipmentArrivalPublisher.publish(ngrShipment));

    verify(kafkaTemplate, times(1)).send(any(Message.class));
  }
}
