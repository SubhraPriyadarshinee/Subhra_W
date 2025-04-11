package com.walmart.move.nim.receiving.core.builder;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.utils.constants.OSDRCode;
import java.util.ArrayList;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class POHashKeyBuilderTest {

  @Mock private ReceiptService receiptService;

  @InjectMocks private POHashKeyBuilder poHashKeyBuilder;

  @BeforeMethod
  public void createPOHashKeyBuilder() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testBuild_samehash_for_samedata() throws Exception {

    List<Receipt> receipts = new ArrayList<>();
    Receipt receipt1 = new Receipt();
    receipt1.setPurchaseReferenceNumber("1234567");
    receipt1.setPurchaseReferenceLineNumber(1);
    receipt1.setVnpkQty(10);
    receipt1.setFbProblemQty(0);
    receipt1.setFbProblemQtyUOM("ZA");
    receipt1.setFbDamagedQty(1);
    receipt1.setFbDamagedQtyUOM("ZA");
    receipt1.setFbDamagedReasonCode(OSDRCode.D10);

    Receipt receipt2 = new Receipt();
    receipt2.setPurchaseReferenceNumber("1234567");
    receipt2.setPurchaseReferenceLineNumber(2);
    receipt2.setVnpkQty(10);
    receipt2.setFbOverQty(1);
    receipt2.setFbOverQtyUOM("ZA");
    receipt2.setFbOverReasonCode(OSDRCode.O13);
    receipt2.setFbProblemQty(0);
    receipt2.setFbProblemQtyUOM("ZA");
    receipt2.setFbDamagedQty(1);
    receipt2.setFbDamagedQtyUOM("ZA");
    receipt2.setFbDamagedReasonCode(OSDRCode.D10);

    Receipt receipt3 = new Receipt();
    receipt3.setPurchaseReferenceNumber("1234567");
    receipt3.setPurchaseReferenceLineNumber(3);
    receipt3.setVnpkQty(10);
    receipt3.setFbShortQty(1);
    receipt3.setFbShortQtyUOM("ZA");
    receipt3.setFbShortReasonCode(OSDRCode.S10);
    receipt3.setFbProblemQty(0);
    receipt3.setFbProblemQtyUOM("ZA");
    receipt3.setFbDamagedQty(1);
    receipt3.setFbDamagedQtyUOM("ZA");
    receipt3.setFbDamagedReasonCode(OSDRCode.D10);

    receipts.add(receipt1);
    receipts.add(receipt2);
    receipts.add(receipt3);

    doReturn(receipts)
        .when(receiptService)
        .findByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());

    String poHashKey1 = poHashKeyBuilder.build(12345l, "0987654321");
    String poHashKey2 = poHashKeyBuilder.build(12345l, "0987654321");

    assertEquals(poHashKey1, poHashKey2);
  }

  @Test
  public void testBuild_samehash_for_differentdata() throws Exception {

    List<Receipt> receipts1 = new ArrayList<>();
    List<Receipt> receipts2 = new ArrayList<>();

    Receipt receipt1 = new Receipt();
    receipt1.setPurchaseReferenceNumber("1234567");
    receipt1.setPurchaseReferenceLineNumber(1);
    receipt1.setVnpkQty(10);
    receipt1.setFbProblemQty(0);
    receipt1.setFbProblemQtyUOM("ZA");
    receipt1.setFbDamagedQty(1);
    receipt1.setFbDamagedQtyUOM("ZA");
    receipt1.setFbDamagedReasonCode(OSDRCode.D10);

    Receipt receipt2 = new Receipt();
    receipt2.setPurchaseReferenceNumber("1234567");
    receipt2.setPurchaseReferenceLineNumber(2);
    receipt2.setVnpkQty(10);
    receipt2.setFbOverQty(1);
    receipt2.setFbOverQtyUOM("ZA");
    receipt2.setFbOverReasonCode(OSDRCode.O13);
    receipt2.setFbProblemQty(0);
    receipt2.setFbProblemQtyUOM("ZA");
    receipt2.setFbDamagedQty(1);
    receipt2.setFbDamagedQtyUOM("ZA");
    receipt2.setFbDamagedReasonCode(OSDRCode.D10);

    Receipt receipt3 = new Receipt();
    receipt3.setPurchaseReferenceNumber("1234567");
    receipt3.setPurchaseReferenceLineNumber(3);
    receipt3.setVnpkQty(10);
    receipt3.setFbShortQty(1);
    receipt3.setFbShortQtyUOM("ZA");
    receipt3.setFbShortReasonCode(OSDRCode.S10);
    receipt3.setFbProblemQty(0);
    receipt3.setFbProblemQtyUOM("ZA");
    receipt3.setFbDamagedQty(1);
    receipt3.setFbDamagedQtyUOM("ZA");
    receipt3.setFbDamagedReasonCode(OSDRCode.D10);

    Receipt receipt4 = new Receipt();
    receipt4.setPurchaseReferenceNumber("12345678");
    receipt4.setPurchaseReferenceLineNumber(3);
    receipt4.setVnpkQty(10);
    receipt4.setFbShortQty(2);
    receipt4.setFbShortQtyUOM("ZA");
    receipt4.setFbShortReasonCode(OSDRCode.S10);
    receipt4.setFbProblemQty(0);
    receipt4.setFbProblemQtyUOM("ZA");
    receipt4.setFbDamagedQty(1);
    receipt4.setFbDamagedQtyUOM("ZA");
    receipt4.setFbDamagedReasonCode(OSDRCode.D10);

    receipts1.add(receipt1);
    receipts1.add(receipt2);

    receipts2.add(receipt3);
    receipts2.add(receipt4);

    doReturn(receipts1)
        .when(receiptService)
        .findByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());

    String poHashKey1 = poHashKeyBuilder.build(12345l, "0987654321");

    doReturn(receipts2)
        .when(receiptService)
        .findByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());

    String poHashKey2 = poHashKeyBuilder.build(12345l, "0987654321");

    assertNotEquals(poHashKey1, poHashKey2);
  }
}
