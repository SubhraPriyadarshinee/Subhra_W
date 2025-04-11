package com.walmart.move.nim.receiving.core.message.common;

import static org.testng.Assert.*;

import org.testng.annotations.Test;

public class ShipmentInfoTest {

  @Test
  public void testTestToString() {
    ShipmentInfo shipmentInfo = new ShipmentInfo();
    assertNotNull(shipmentInfo.toString());
  }

  @Test
  public void testBuilder() {
    ShipmentInfo shipmentInfo = ShipmentInfo.builder().shipmentNumber("1").build();
    assertNotNull(shipmentInfo.getShipmentNumber());
  }
}
