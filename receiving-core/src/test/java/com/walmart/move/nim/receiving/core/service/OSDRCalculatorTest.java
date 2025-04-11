package com.walmart.move.nim.receiving.core.service;

import static org.testng.AssertJUnit.assertEquals;

import com.walmart.move.nim.receiving.core.model.ReceivingCountSummary;
import org.testng.annotations.Test;

public class OSDRCalculatorTest {

  @Test
  public void testCalculateShortage() {

    OSDRCalculator calculator = new OSDRCalculator();

    ReceivingCountSummary receivingCountSummary = new ReceivingCountSummary();
    receivingCountSummary.setTotalFBQty(100);
    receivingCountSummary.setReceiveQty(50);
    receivingCountSummary.setProblemQty(10);
    receivingCountSummary.setDamageQty(10);
    receivingCountSummary.setRejectedQty(10);

    calculator.calculate(receivingCountSummary);

    assertEquals(receivingCountSummary.getOverageQty(), 0);
    assertEquals(receivingCountSummary.getShortageQty(), 20);
  }

  @Test
  public void testCalculateOverage() {

    OSDRCalculator calculator = new OSDRCalculator();

    ReceivingCountSummary receivingCountSummary = new ReceivingCountSummary();
    receivingCountSummary.setTotalFBQty(60);
    receivingCountSummary.setReceiveQty(50);
    receivingCountSummary.setProblemQty(10);
    receivingCountSummary.setDamageQty(10);
    receivingCountSummary.setRejectedQty(10);

    calculator.calculate(receivingCountSummary);

    assertEquals(receivingCountSummary.getOverageQty(), 20);
    assertEquals(receivingCountSummary.getShortageQty(), 0);
  }
}
