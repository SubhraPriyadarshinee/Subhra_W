package com.walmart.move.nim.receiving.core.common;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.OSDR_EVENT_TYPE_VALUE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.POLineOSDR;
import com.walmart.move.nim.receiving.core.model.POPOLineKey;
import com.walmart.move.nim.receiving.core.model.ReceivingCountSummary;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrData;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrSummary;
import com.walmart.move.nim.receiving.utils.constants.ConcealedShortageCode;
import com.walmart.move.nim.receiving.utils.constants.OSDRCode;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class OsdrUtilsTest extends ReceivingTestBase {
  private Receipt receipt;
  private List<Receipt> receipts;
  Gson gson = new Gson();
  List<ReceivingCountSummary> receivingCountSummary = new ArrayList<>();

  @BeforeClass
  public void initMock() {
    MockitoAnnotations.initMocks(this);
    // Mock receipts
    receipt = new Receipt();
    receipt.setProblemId(null);
    receipt.setCreateUserId("sysUser");
    receipt.setEachQty(4);
    receipt.setDeliveryNumber(6849404L);
    receipt.setDoorNumber("123");
    receipt.setPurchaseReferenceLineNumber(1);
    receipt.setPurchaseReferenceNumber("9763140004");
    receipt.setQuantity(4);
    receipt.setQuantityUom(ReceivingConstants.Uom.VNPK);
    receipt.setFbRejectedQty(4);
    receipt.setFbRejectedQtyUOM(ReceivingConstants.Uom.VNPK);
    receipt.setFbRejectionComment("rejection comment");
    receipt.setFbOverQty(1);
    receipt.setFbOverQtyUOM(ReceivingConstants.Uom.VNPK);
    receipt.setFbOverReasonCode(OSDRCode.O31);
    receipt.setFbDamagedQty(1);
    receipt.setFbDamagedQtyUOM(ReceivingConstants.Uom.VNPK);
    receipt.setFbDamagedReasonCode(OSDRCode.D53);
    receipt.setFbShortQty(1);
    receipt.setFbShortReasonCode(OSDRCode.S10);
    receipt.setFbConcealedShortageQty(1);
    receipt.setFbConcealedShortageReasonCode(OSDRCode.O55);
    receipt.setVnpkQty(1);
    receipt.setWhpkQty(1);
    receipts = new ArrayList<>();
    receipts.add(receipt);
  }

  @Test
  public void createOsdrTest() {
    POLineOSDR osdr = OsdrUtils.createOsdr(receipt);
    assertEquals(osdr.getDamageUOM(), "EA");
    assertEquals(osdr.getOverageUOM(), "EA");
  }

  @Test
  public void getRejectedQtyWhenUOMisAvalilableTest() {
    POLineOSDR osdr = OsdrUtils.createOsdr(receipt);
    OsdrUtils.getRejectedQty(receipt, osdr);
    assertEquals(receipt.getFbRejectedQty(), osdr.getRejectedQty());
    assertEquals(osdr.getRejectedUOM(), "EA");
  }

  @Test
  public void getRejectedQtyWithoutUOMTest() {
    POLineOSDR osdr = OsdrUtils.createOsdr(receipt);
    receipt.setFbRejectedQtyUOM(null);
    OsdrUtils.getRejectedQty(receipt, osdr);
    assertEquals(receipt.getFbRejectedQty(), osdr.getRejectedQty());
    assertEquals(osdr.getRejectedUOM(), "EA");
  }

  @Test
  public void getRejectedQtyWithReasonCodeTest() {
    POLineOSDR osdr = OsdrUtils.createOsdr(receipt);
    receipt.setFbRejectedReasonCode(OSDRCode.R10);
    OsdrUtils.getRejectedQty(receipt, osdr);
    assertEquals(receipt.getFbRejectedQty(), osdr.getRejectedQty());
    assertEquals(receipt.getFbRejectedReasonCode(), OSDRCode.R10);
    assertEquals(osdr.getRejectedUOM(), "EA");
  }

  @Test
  public void getFbOverQtyWhenUOMisAvailableTest() {
    POLineOSDR osdr = OsdrUtils.createOsdr(receipt);
    OsdrUtils.populateFbOverQty(receipt, osdr);
    assertEquals(receipt.getFbOverQty(), osdr.getOverageQty());
    assertEquals(osdr.getOverageUOM(), "EA");
  }

  @Test
  public void getFbOverQtyWithoutUOMTest() {
    POLineOSDR osdr = OsdrUtils.createOsdr(receipt);
    receipt.setFbOverQtyUOM(null);
    OsdrUtils.populateFbOverQty(receipt, osdr);
    assertEquals(receipt.getFbOverQty(), osdr.getOverageQty());
    assertEquals(osdr.getOverageUOM(), "EA");
  }

  @Test
  public void getFbOverQtyWithReasonCodeTest() {
    POLineOSDR osdr = OsdrUtils.createOsdr(receipt);
    receipt.setFbOverReasonCode(OSDRCode.O31);
    OsdrUtils.populateFbOverQty(receipt, osdr);
    assertEquals(receipt.getFbOverReasonCode(), OSDRCode.O31);
    assertEquals(receipt.getFbOverQty(), osdr.getOverageQty());
    assertEquals(osdr.getOverageUOM(), "EA");
  }

  @Test
  public void getFbshortQtyWhenUOMisAvailableTest() {
    POLineOSDR osdr = OsdrUtils.createOsdr(receipt);
    OsdrUtils.populateFbShortQty(receipt, osdr);
    assertEquals(receipt.getFbShortQty(), osdr.getShortageQty());
    assertEquals(osdr.getShortageUOM(), "EA");
  }

  @Test
  public void getFbshortQtyWithoutUOMTest() {
    POLineOSDR osdr = OsdrUtils.createOsdr(receipt);
    receipt.setFbShortQtyUOM(null);
    OsdrUtils.populateFbShortQty(receipt, osdr);
    assertEquals(receipt.getFbShortQty(), osdr.getShortageQty());
    assertEquals(osdr.getShortageUOM(), "EA");
  }

  @Test
  public void getFbshortQtyWithReasonCodeTest() {
    POLineOSDR osdr = OsdrUtils.createOsdr(receipt);
    OsdrUtils.populateFbShortQty(receipt, osdr);
    assertEquals(receipt.getFbShortReasonCode().getCode(), OSDRCode.S10.getCode());
    assertEquals(receipt.getFbShortQty(), osdr.getShortageQty());
    assertEquals(osdr.getShortageUOM(), "EA");
  }

  @Test
  public void getFbDamageQtyWhenUOMisAvailableTest() {
    POLineOSDR osdr = OsdrUtils.createOsdr(receipt);
    OsdrUtils.populateFbDamageQty(receipt, osdr);
    assertEquals(receipt.getFbDamagedQty(), osdr.getDamageQty());
    assertEquals(osdr.getDamageUOM(), "EA");
  }

  @Test
  public void getFbDamageQtyWithoutUOMTest() {
    POLineOSDR osdr = OsdrUtils.createOsdr(receipt);
    receipt.setFbDamagedQtyUOM(null);
    OsdrUtils.populateFbDamageQty(receipt, osdr);
    assertEquals(receipt.getFbDamagedQty(), osdr.getDamageQty());
    assertEquals(osdr.getDamageUOM(), "EA");
  }

  @Test
  public void getFbDamageQtyWithReasonCodeTest() {
    POLineOSDR osdr = OsdrUtils.createOsdr(receipt);
    OsdrUtils.populateFbDamageQty(receipt, osdr);
    assertEquals(receipt.getFbDamagedReasonCode().getCode(), OSDRCode.D53.getCode());
    assertEquals(receipt.getFbDamagedQty(), osdr.getDamageQty());
    assertEquals(osdr.getDamageUOM(), "EA");
  }

  @Test
  public void getFbConcealedShortageQtyisAvailableTest() {
    POLineOSDR osdr = OsdrUtils.createOsdr(receipt);
    OsdrUtils.populateFbConcealedShortageQty(receipt, osdr);
    assertEquals(receipt.getFbConcealedShortageQty(), osdr.getConcealedShortageQty());
    assertEquals(osdr.getConcealedShortageUOM(), "EA");
  }

  @Test
  public void getFbConcealedOverageQtyWithShortageQtyTest() {
    POLineOSDR osdr = OsdrUtils.createOsdr(receipt);
    receipt.setFbConcealedShortageQty(-1);
    OsdrUtils.populateFbConcealedShortageQty(receipt, osdr);
    assertEquals(receipt.getFbConcealedShortageQty(), osdr.getConcealedShortageQty());
    assertEquals(osdr.getOverageReasonCode(), ConcealedShortageCode.O55.getCode());
  }

  @Test
  public void getFbConcealedShortageQtyWithReasonCodeTest() {
    POLineOSDR osdr = OsdrUtils.createOsdr(receipt);
    OsdrUtils.populateFbConcealedShortageQty(receipt, osdr);
    assertEquals(
        receipt.getFbConcealedShortageReasonCode().getCode(), ConcealedShortageCode.O55.getCode());
    assertEquals(receipt.getFbConcealedShortageQty(), osdr.getConcealedShortageQty());
    assertEquals(osdr.getConcealedShortageUOM(), "EA");
  }

  @Test
  public void getReceivedQtyTest() {
    POLineOSDR osdr = OsdrUtils.createOsdr(receipt);
    OsdrUtils.populateReceivedQty(receipt, osdr);
    assertEquals(receipt.getQuantity(), osdr.getReceivedQty());
    assertEquals(osdr.getReceivedUOM(), "EA");
  }

  @Test
  public void getReceivedQtyWithoutUOMTest() {
    POLineOSDR osdr = OsdrUtils.createOsdr(receipt);
    receipt.setQuantityUom(null);
    OsdrUtils.populateReceivedQty(receipt, osdr);
    assertEquals(receipt.getQuantity(), osdr.getReceivedQty());
    assertEquals(osdr.getReceivedUOM(), "EA");
  }

  @Test
  public void getReceivedQtyAndPalletQtyisNotnullTest() {
    POLineOSDR osdr = OsdrUtils.createOsdr(receipt);
    receipt.setPalletQty(1);
    OsdrUtils.populateReceivedQty(receipt, osdr);
    assertEquals(receipt.getQuantity(), osdr.getReceivedQty());
    assertEquals(osdr.getReceivedUOM(), "EA");
  }

  @Test
  public void getReceivedQtyAndPalletQtyisNotnullAndosdrPalletQtyisNotNullTest() {
    POLineOSDR osdr = OsdrUtils.createOsdr(receipt);
    receipt.setPalletQty(1);
    osdr.setPalletQty(1);
    OsdrUtils.populateReceivedQty(receipt, osdr);
    assertEquals(receipt.getQuantity(), osdr.getReceivedQty());
    assertEquals(osdr.getReceivedUOM(), "EA");
  }

  @Test(priority = 1)
  public void getReceivingCountSummaryTest() {
    Map<POPOLineKey, POLineOSDR> receiptSummary = new HashMap<>();
    POLineOSDR osdr = OsdrUtils.createOsdr(receipt);
    osdr.setDamageQty(1);
    osdr.setDamageReasonCode(OSDRCode.D53.getCode());
    osdr.setDamageUOM("ZA");
    osdr.setOverageQty(2);
    osdr.setOverageReasonCode(OSDRCode.O31.getCode());
    osdr.setOverageUOM("ZA");
    osdr.setPalletQty(1);
    osdr.setConcealedShortageQty(1);
    osdr.setConcealedShortageUOM("ZA");
    osdr.setRejectedQty(1);
    osdr.setRejectedUOM("ZA");
    osdr.setRejectedReasonCode(ReceivingConstants.Uom.VNPK);
    osdr.setShortageQty(1);
    osdr.setShortageReasonCode(OSDRCode.S10.getCode());
    osdr.setShortageUOM("ZA");
    osdr.setReceivedQty(4);
    osdr.setReceivedUOM("ZA");
    osdr.setVnpkQty(1);
    osdr.setWhpkQty(1);
    POPOLineKey poPoLineKey =
        new POPOLineKey(
            receipt.getPurchaseReferenceNumber(), receipt.getPurchaseReferenceLineNumber());
    receiptSummary.put(poPoLineKey, osdr);
    receivingCountSummary = OsdrUtils.getReceivingCountSummary(receiptSummary, "ZA");
    assertEquals(receivingCountSummary.get(0).getReceiveQty(), 4);
  }

  @Test
  public void buildOsdrPoDtlsForOverageAndShortageTest() {
    OsdrData osdrData =
        OsdrUtils.buildOsdrPoDtlsForOverageAndShortage(5, "ZA", OSDRCode.O31.getCode());
    assertEquals(osdrData.getUom(), "ZA");
  }

  @Test
  public void buildOsdrPoDtlsForDamageTest() {
    ReceivingCountSummary receivingSummary = new ReceivingCountSummary();
    receivingSummary.addDamageQty(1);
    receivingCountSummary.add(receivingSummary);
    OsdrData osdrData = OsdrUtils.buildOsdrPoDtlsForDamage(receivingCountSummary.get(0));
    assertEquals(osdrData.getUom(), null);
  }

  @Test
  public void buildOsdrPoDtlsForRejectTest() {
    ReceivingCountSummary receivingSummary = new ReceivingCountSummary();
    receivingSummary.addReceiveQty(4);
    receivingCountSummary.add(receivingSummary);
    OsdrData osdrData = OsdrUtils.buildOsdrPoDtlsForReject(receivingCountSummary.get(0));
    assertEquals(osdrData.getUom(), null);
  }

  @Test
  public void test_newOsdrSummary() {
    final String userid = "sysadmin";
    final Long deliveryNumber = Long.valueOf(22223332);
    final OsdrSummary osdrSummary = OsdrUtils.newOsdrSummary(deliveryNumber, userid);
    assertNotNull(osdrSummary);
    assertEquals(osdrSummary.getUserId(), userid);
    assertEquals(osdrSummary.getEventType(), OSDR_EVENT_TYPE_VALUE);
    assertNotNull(osdrSummary.getTs());
    assertEquals(osdrSummary.getDeliveryNumber(), deliveryNumber);
  }
}
