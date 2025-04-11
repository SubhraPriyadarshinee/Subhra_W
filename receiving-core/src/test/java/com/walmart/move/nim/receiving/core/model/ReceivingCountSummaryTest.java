package com.walmart.move.nim.receiving.core.model;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

public class ReceivingCountSummaryTest {

  @Test
  public void test() {

    ReceivingCountSummary receivingCountSummary = new ReceivingCountSummary();

    receivingCountSummary.setOverageQty(10);
    receivingCountSummary.setShortageQty(15);

    receivingCountSummary.addDamageQty(10);
    receivingCountSummary.addDamageQty(10);
    receivingCountSummary.addDamageQty(5);

    assertEquals(receivingCountSummary.getOverageQty(), 10);
    assertEquals(receivingCountSummary.getShortageQty(), 15);
    assertEquals(receivingCountSummary.getDamageQty(), 25);

    assertTrue(receivingCountSummary.isOverage());
    assertTrue(receivingCountSummary.isShortage());
  }
}
