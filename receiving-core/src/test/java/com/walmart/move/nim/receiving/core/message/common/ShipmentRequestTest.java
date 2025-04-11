package com.walmart.move.nim.receiving.core.message.common;

import static org.testng.Assert.*;

import org.testng.annotations.Test;

public class ShipmentRequestTest {

  @Test
  public void testTestToString() {
    ShipmentRequest shipmentRequest = new ShipmentRequest();
    assertNotNull(shipmentRequest.toString());
  }

  @Test
  public void testBuilder() {
    ShipmentRequest shipmentRequest =
        ShipmentRequest.builder()
            .shipment(ShipmentInfo.builder().shipmentNumber("1").build())
            .build();
    assertNotNull(shipmentRequest.getShipment());
  }
}
