package com.walmart.move.nim.receiving.core.service;

import static org.testng.AssertJUnit.assertEquals;

import com.walmart.move.nim.receiving.core.model.ReceivingCountSummary;
import org.testng.annotations.Test;

public class GDMOSDRCalculatorTest {

  @Test
  public void testCalculateShortage() {

    GDMOSDRCalculator calculator = new GDMOSDRCalculator();

    ReceivingCountSummary receivingCountSummary = new ReceivingCountSummary();
    receivingCountSummary.setTotalFBQty(100);
    receivingCountSummary.setReceiveQty(50);
    receivingCountSummary.setProblemQty(10);
    receivingCountSummary.setDamageQty(10);
    receivingCountSummary.setRejectedQty(10);

    calculator.calculate(receivingCountSummary);

    assertEquals(0, receivingCountSummary.getOverageQty());
    assertEquals(30, receivingCountSummary.getShortageQty());
  }

  @Test
  public void testCalculateOverage() {

    GDMOSDRCalculator calculator = new GDMOSDRCalculator();

    ReceivingCountSummary receivingCountSummary = new ReceivingCountSummary();
    receivingCountSummary.setTotalFBQty(60);
    receivingCountSummary.setReceiveQty(60);
    receivingCountSummary.setProblemQty(10);
    receivingCountSummary.setDamageQty(10);
    receivingCountSummary.setRejectedQty(10);

    calculator.calculate(receivingCountSummary);

    assertEquals(20, receivingCountSummary.getOverageQty());
    assertEquals(0, receivingCountSummary.getShortageQty());
  }
}
