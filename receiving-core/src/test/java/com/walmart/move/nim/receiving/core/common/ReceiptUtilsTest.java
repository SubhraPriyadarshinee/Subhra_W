package com.walmart.move.nim.receiving.core.common;

import static com.walmart.move.nim.receiving.core.common.ReceiptUtils.isPoFinalized;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.ReceivingCountSummary;
import java.util.Date;
import org.testng.annotations.Test;

public class ReceiptUtilsTest {

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_setValidVnpkWnpkFromGdm() {

    Receipt receipt = new Receipt();
    ReceivingCountSummary gdmSummary = new ReceivingCountSummary();
    String poNumber = "1234";
    Integer poLineNumber = 1;

    Receipt receiptResult =
        ReceiptUtils.populateValidVnpkAndWnpk(receipt, gdmSummary, poNumber, poLineNumber);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_setValidVnpkWnpkFromGdm_nullSummary() {

    Receipt receipt = new Receipt();
    ReceivingCountSummary gdmSummary = null;
    String poNumber = "1234";
    Integer poLineNumber = 1;

    Receipt receiptResult =
        ReceiptUtils.populateValidVnpkAndWnpk(receipt, gdmSummary, poNumber, poLineNumber);
  }

  @Test
  public void test_Receipt_isPoFinalized() {
    // case null receipt
    assertFalse(isPoFinalized(null));

    // case two attributes null
    Receipt receipt = new Receipt();
    assertFalse(isPoFinalized(receipt));

    // case one attribute FinalizeTs null
    assertFalse(isPoFinalized(receipt));
    receipt.setFinalizedUserId("user1");

    // case two attributes not null
    receipt.setFinalizeTs(new Date());
    assertTrue(isPoFinalized(receipt));

    // case one attribute FinalizedUserId null
    receipt.setFinalizedUserId(null);
    assertFalse(isPoFinalized(receipt));
  }
}
