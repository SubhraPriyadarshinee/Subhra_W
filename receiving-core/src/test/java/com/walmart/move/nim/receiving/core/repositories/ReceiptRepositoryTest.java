package com.walmart.move.nim.receiving.core.repositories;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryEachesResponse;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryQtyByPoAndPoLineResponse;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryQtyByProblemIdResponse;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryResponse;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryVnpkResponse;
import com.walmart.move.nim.receiving.core.model.RxReceiptsSummaryResponse;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ReceiptRepositoryTest extends ReceivingTestBase {

  @Autowired private ReceiptRepository receiptRepository;
  @Autowired private ReceiptCustomRepository receiptCustomRepository;

  private List<ReceiptSummaryResponse> receiptSummaryVnpkList;
  private List<ReceiptSummaryResponse> receiptSummaryEachesList;
  private List<ReceiptSummaryVnpkResponse> receiptSummaryByVnpkList;

  @Autowired private ContainerItemRepository containerItemRepository;

  private ContainerItem containerItem;

  List<Receipt> receipts = new ArrayList<>();

  /** Insert receipt into H2 database */
  @BeforeClass
  public void insertDataIntoH2Db() {
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32987);

    Receipt receipt1 = new Receipt();
    receipt1.setDeliveryNumber(Long.valueOf("21119003"));
    receipt1.setDoorNumber("123");
    receipt1.setPurchaseReferenceNumber("9763140007");
    receipt1.setPurchaseReferenceLineNumber(1);
    receipt1.setQuantity(2);
    receipt1.setQuantityUom("ZA");
    receipt1.setVnpkQty(48);
    receipt1.setWhpkQty(4);
    receipt1.setEachQty(96);
    receipt1.setCreateUserId("sysadmin");
    receipt1.setProblemId("1");
    receipt1.setVersion(1);
    receipt1.setOsdrMaster(null);

    Receipt receipt2 = new Receipt();
    receipt2.setDeliveryNumber(Long.valueOf("21119003"));
    receipt2.setDoorNumber("123");
    receipt2.setPurchaseReferenceNumber("9763140007");
    receipt2.setPurchaseReferenceLineNumber(1);
    receipt2.setQuantity(1);
    receipt2.setQuantityUom("ZA");
    receipt2.setVnpkQty(48);
    receipt2.setWhpkQty(4);
    receipt2.setEachQty(48);
    receipt2.setCreateUserId("sysadmin");
    receipt2.setProblemId("1");
    receipt2.setOsdrMaster(1);

    Receipt receipt3 = new Receipt();
    receipt3.setDeliveryNumber(Long.valueOf("21119003"));
    receipt3.setDoorNumber("123");
    receipt3.setPurchaseReferenceNumber("9763140005");
    receipt3.setPurchaseReferenceLineNumber(1);
    receipt3.setQuantity(1);
    receipt3.setQuantityUom("ZA");
    receipt3.setVnpkQty(48);
    receipt3.setWhpkQty(4);
    receipt3.setEachQty(48);
    receipt3.setCreateUserId("sysadmin");
    receipt3.setOsdrMaster(null);

    Receipt receipt4 = new Receipt();
    receipt4.setDeliveryNumber(Long.valueOf("21119003"));
    receipt4.setDoorNumber("123");
    receipt4.setPurchaseReferenceNumber("9763140005");
    receipt4.setPurchaseReferenceLineNumber(1);
    receipt4.setQuantity(1);
    receipt4.setQuantityUom("ZA");
    receipt4.setVnpkQty(48);
    receipt4.setWhpkQty(4);
    receipt4.setEachQty(48);
    receipt4.setCreateUserId("sysadmin");
    receipt4.setOsdrMaster(1);

    Receipt receipt5 = new Receipt();
    receipt5.setDeliveryNumber(Long.valueOf("21119003"));
    receipt5.setDoorNumber("123");
    receipt5.setPurchaseReferenceNumber("9763140004");
    receipt5.setPurchaseReferenceLineNumber(1);
    receipt5.setQuantity(2);
    receipt5.setQuantityUom("ZA");
    receipt5.setVnpkQty(24);
    receipt5.setWhpkQty(4);
    receipt5.setEachQty(48);
    receipt5.setCreateUserId("sysadmin");
    receipt5.setOsdrMaster(null);

    Receipt receipt6 = new Receipt();
    receipt6.setDeliveryNumber(Long.valueOf("21119003"));
    receipt6.setDoorNumber("123");
    receipt6.setPurchaseReferenceNumber("9763140004");
    receipt6.setPurchaseReferenceLineNumber(1);
    receipt6.setQuantity(2);
    receipt6.setQuantityUom("ZA");
    receipt6.setVnpkQty(24);
    receipt6.setWhpkQty(4);
    receipt6.setEachQty(48);
    receipt6.setCreateUserId("sysadmin");
    receipt6.setOsdrMaster(null);

    Receipt receipt7 = new Receipt();
    receipt7.setDeliveryNumber(Long.valueOf("21119003"));
    receipt7.setDoorNumber("123");
    receipt7.setPurchaseReferenceNumber("9763140004");
    receipt7.setPurchaseReferenceLineNumber(2);
    receipt7.setQuantity(2);
    receipt7.setQuantityUom("ZA");
    receipt7.setVnpkQty(24);
    receipt7.setWhpkQty(4);
    receipt7.setEachQty(48);
    receipt7.setCreateUserId("sysadmin");
    receipt7.setOsdrMaster(1);
    receipt7.setFinalizedUserId("sysadmin");
    receipt7.setFinalizeTs(new Date());

    Receipt receipt8 = new Receipt();
    receipt8.setDeliveryNumber(Long.valueOf("21119003"));
    receipt8.setDoorNumber("123");
    receipt8.setPurchaseReferenceNumber("2323");
    receipt8.setPurchaseReferenceLineNumber(3);
    receipt8.setSsccNumber("00123");
    receipt8.setQuantity(10);
    receipt8.setQuantityUom("ZA");
    receipt8.setVnpkQty(48);
    receipt8.setWhpkQty(4);
    receipt8.setEachQty(96);
    receipt8.setCreateUserId("sysadmin");
    receipt8.setVersion(1);
    receipt8.setOsdrMaster(null);
    receipt8.setCreateTs(new Date());

    Receipt receipt9 = new Receipt();
    receipt9.setDeliveryNumber(Long.valueOf("20000001"));
    receipt9.setDoorNumber("100");
    receipt9.setPurchaseReferenceNumber("9000000001");
    receipt9.setPurchaseReferenceLineNumber(1);
    receipt9.setQuantity(1);
    receipt9.setQuantityUom("ZA");
    receipt9.setVnpkQty(6);
    receipt9.setWhpkQty(6);
    receipt9.setEachQty(6);
    receipt9.setCreateUserId("sysadmin");
    receipt9.setOsdrMaster(1);
    receipt9.setFinalizedUserId("sysadmin");
    receipt9.setFinalizeTs(null);

    Receipt receipt10 = new Receipt();
    receipt10.setDeliveryNumber(Long.valueOf("20000001"));
    receipt10.setDoorNumber("100");
    receipt10.setPurchaseReferenceNumber("9000000002");
    receipt10.setPurchaseReferenceLineNumber(2);
    receipt10.setQuantity(1);
    receipt10.setQuantityUom("ZA");
    receipt10.setVnpkQty(6);
    receipt10.setWhpkQty(6);
    receipt10.setEachQty(6);
    receipt10.setCreateUserId("sysadmin");
    receipt10.setOsdrMaster(1);
    receipt10.setFinalizedUserId(null);
    receipt10.setFinalizeTs(new Date());

    receipts.add(receipt1);
    receipts.add(receipt2);
    receipts.add(receipt3);
    receipts.add(receipt4);
    receipts.add(receipt5);
    receipts.add(receipt6);
    receipts.add(receipt7);
    receipts.add(receipt8);
    receipts.add(receipt9);
    receipts.add(receipt10);

    receiptRepository.saveAll(receipts);

    containerItem = new ContainerItem();
    containerItem.setPurchaseReferenceNumber("2323");
    containerItem.setTrackingId("a328990000000000000106529");
    containerItem.setPurchaseReferenceLineNumber(3);
    containerItem.setInboundChannelMethod("CROSSU");
    containerItem.setOutboundChannelMethod("CROSSU");

    containerItemRepository.save(containerItem);

    receiptSummaryVnpkList = new ArrayList<>();
    receiptSummaryVnpkList.add(new ReceiptSummaryVnpkResponse("9763140004", 1, Long.valueOf(4)));
    receiptSummaryVnpkList.add(new ReceiptSummaryVnpkResponse("9763140005", 1, Long.valueOf(2)));
    receiptSummaryVnpkList.add(new ReceiptSummaryVnpkResponse("9763140007", 1, Long.valueOf(3)));

    receiptSummaryEachesList = new ArrayList<>();
    receiptSummaryEachesList.add(
        new ReceiptSummaryEachesResponse("9763140004", 1, null, Long.valueOf(96)));
    receiptSummaryEachesList.add(
        new ReceiptSummaryEachesResponse("9763140005", 1, null, Long.valueOf(96)));
    receiptSummaryEachesList.add(
        new ReceiptSummaryEachesResponse("9763140007", 1, null, Long.valueOf(144)));

    receiptSummaryByVnpkList = new ArrayList<>();
    receiptSummaryByVnpkList.add(
        new ReceiptSummaryVnpkResponse("2323", 3, 48, 4, ReceivingConstants.Uom.VNPK, 96L));
    receiptSummaryByVnpkList.add(
        new ReceiptSummaryVnpkResponse("9763140004", 2, 24, 4, ReceivingConstants.Uom.VNPK, 48L));
    receiptSummaryByVnpkList.add(
        new ReceiptSummaryVnpkResponse("9763140004", 1, 24, 4, ReceivingConstants.Uom.VNPK, 96L));
    receiptSummaryByVnpkList.add(
        new ReceiptSummaryVnpkResponse("9763140005", 1, 48, 4, ReceivingConstants.Uom.VNPK, 96L));
    receiptSummaryByVnpkList.add(
        new ReceiptSummaryVnpkResponse("9763140007", 1, 48, 4, ReceivingConstants.Uom.VNPK, 144L));
  }

  /** Test case for Received quantity summary in vnpk by delivery. */
  @Test
  public void testReceivedQtySummaryInVnpkByDelivery() {
    List<ReceiptSummaryResponse> resultList =
        receiptCustomRepository.receivedQtySummaryInVnpkByDelivery(Long.valueOf("21119003"));
    assertTrue(listsAreEquivelent(receiptSummaryVnpkList, resultList));
  }

  /** Test case for Received quantity summary in eaches by delivery. */
  @Test
  public void testReceivedQtySummaryInEachesByDelivery() {
    List<ReceiptSummaryResponse> resultList =
        receiptCustomRepository.receivedQtySummaryInEachesByDelivery(Long.valueOf("21119003"));
    assertTrue(listsAreEquivelent(receiptSummaryEachesList, resultList));
  }

  @Test
  public void testReceivedQtySummaryInEachesQtyByDelivery() {
    List<ReceiptSummaryVnpkResponse> resultList =
        receiptCustomRepository.receivedQtySummaryInEAByDelivery(Long.valueOf("21119003"));
    assertEquals(receiptSummaryByVnpkList.size(), resultList.size());
  }

  @Test
  public void testReceivedQtyByPoAndPoLine() {
    ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponse =
        receiptCustomRepository.receivedQtyByPoAndPoLine("9763140007", 1);
    Assert.assertEquals(receiptSummaryQtyByPoAndPoLineResponse.getReceivedQty(), new Long(3));
  }

  @Test
  public void receivedQtyByPoAndPoLineInEach() {
    ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponse =
        receiptCustomRepository.receivedQtyByPoAndPoLineInEach("9763140007", 1);
    Assert.assertEquals(receiptSummaryQtyByPoAndPoLineResponse.getReceivedQty(), new Long(144));
  }

  @Test
  public void testReceivedQtyByProblemId() {
    ReceiptSummaryQtyByProblemIdResponse receiptSummaryQtyByProblemIdResponse =
        receiptCustomRepository.receivedQtyByProblemIdInVnpk("1");
    Assert.assertEquals((long) receiptSummaryQtyByProblemIdResponse.getReceivedQty(), 3l);
  }

  /** Test case for report's get receipts by po/po line */
  @Test
  public void testReceiptsByPoPoLine() {
    List<Receipt> receiptsByPoPoLine =
        receiptRepository.findByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            "9763140007", 1);

    Assert.assertEquals(receiptsByPoPoLine.size(), 2);
    Assert.assertEquals(receiptsByPoPoLine.get(0).getVersion(), Integer.valueOf(1));
    Assert.assertEquals(receiptsByPoPoLine.get(0).getQuantity(), receipts.get(0).getQuantity());
    Assert.assertEquals(receiptsByPoPoLine.get(1).getQuantity(), receipts.get(1).getQuantity());
  }

  @Test
  public void testReceivedQtyByPoAndPoLineList() {
    List<String> purchaseReferenceNumberList = new ArrayList<>();
    purchaseReferenceNumberList.add("9763140004");
    purchaseReferenceNumberList.add("9763140005");
    purchaseReferenceNumberList.add("9763140007");
    Set<Integer> purchaseReferenceLineNumberSet = new HashSet<>();
    purchaseReferenceLineNumberSet.add(1);
    purchaseReferenceLineNumberSet.add(2);
    List<ReceiptSummaryEachesResponse> responses =
        receiptCustomRepository.receivedQtyByPoAndPoLineList(
            purchaseReferenceNumberList, purchaseReferenceLineNumberSet);
    assertEquals(responses.size(), 4);
    assertTrue(listsEquals(receiptSummaryEachesList, responses));
  }

  /**
   * This method checks if two list of ReceiptSummaryQtyByPoAndPoLineResponse are equals or not.
   *
   * @param expectedList<ReceiptSummaryResponse>
   * @param actualList<ReceiptSummaryResponse>
   * @return boolean
   */
  private boolean listsEquals(
      List<ReceiptSummaryResponse> expectedList, List<ReceiptSummaryEachesResponse> actualList) {
    for (ReceiptSummaryResponse e : expectedList) {
      boolean tmpFlag = false;
      for (ReceiptSummaryResponse a : actualList) {
        if (e.getPurchaseReferenceNumber().equals(a.getPurchaseReferenceNumber())
            && e.getPurchaseReferenceLineNumber().equals(a.getPurchaseReferenceLineNumber())
            && e.getReceivedQty().equals(a.getReceivedQty())
            && e.getQtyUOM().equals(a.getQtyUOM())) {
          tmpFlag = true;
          break;
        }
      }
      if (!tmpFlag) {
        return false;
      }
    }
    return true;
  }

  /**
   * This method checks if two list of ReceiptSummaryQtyByPoAndPoLineResponse are equals or not.
   *
   * @param expectedList<ReceiptSummaryResponse>
   * @param actualList<ReceiptSummaryResponse>
   * @return boolean
   */
  private boolean listsEqualsByVNPK(
      List<ReceiptSummaryResponse> expectedList,
      List<ReceiptSummaryQtyByPoAndPoLineResponse> actualList) {
    for (ReceiptSummaryResponse e : expectedList) {
      boolean tmpFlag = false;
      for (ReceiptSummaryResponse a : actualList) {
        if (e.getPurchaseReferenceNumber().equals(a.getPurchaseReferenceNumber())
            && e.getPurchaseReferenceLineNumber().equals(a.getPurchaseReferenceLineNumber())
            && e.getReceivedQty().equals(a.getReceivedQty())
            && e.getQtyUOM().equals(a.getQtyUOM())) {
          tmpFlag = true;
          break;
        }
      }
      if (!tmpFlag) {
        return false;
      }
    }
    return true;
  }

  /**
   * This method checks if two list of ReceiptSummaryResponse are equals or not.
   *
   * @param expectedList<ReceiptSummaryResponse>
   * @param actualList<ReceiptSummaryResponse>
   * @return boolean
   */
  private boolean listsAreEquivelent(
      List<ReceiptSummaryResponse> expectedList, List<ReceiptSummaryResponse> actualList) {
    for (ReceiptSummaryResponse e : expectedList) {
      boolean tmpFlag = false;
      for (ReceiptSummaryResponse a : actualList) {
        if (e.getPurchaseReferenceNumber().equals(a.getPurchaseReferenceNumber())
            && e.getPurchaseReferenceLineNumber().equals(a.getPurchaseReferenceLineNumber())
            && e.getReceivedQty().equals(a.getReceivedQty())
            && e.getQtyUOM().equals(a.getQtyUOM())) {
          tmpFlag = true;
          break;
        }
      }
      if (!tmpFlag) {
        return false;
      }
    }
    return true;
  }

  @Test
  public void testFindByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber() {

    List<Receipt> receipstFromDB =
        receiptRepository
            .findByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                21119003l, "9763140005", 1);

    assertFalse("Receipt should exists in the database", CollectionUtils.isEmpty(receipstFromDB));
    assertEquals(Long.valueOf(21119003l), receipstFromDB.get(0).getDeliveryNumber());
    assertSame(
        "Purchase Reference Number should match",
        "9763140005",
        receipstFromDB.get(0).getPurchaseReferenceNumber());
    assertEquals(Integer.valueOf(1), receipstFromDB.get(0).getPurchaseReferenceLineNumber());
  }

  @Test
  public void testFindByDeliveryNumberAndOsdrMasterAndFinalizeTsIsNull() {
    List<Receipt> receipts =
        receiptRepository.findByDeliveryNumberAndOsdrMasterAndFinalizeTsIsNull(
            Long.valueOf("21119003"), 1);

    Set<String> poSet = new HashSet<>();
    for (Receipt receipt : receipts) {
      poSet.add(receipt.getPurchaseReferenceNumber());
    }

    assertEquals(poSet.size(), 2);
  }

  @Test
  public void testReceivedQtyInVNPKByPoAndPoLineList() {
    List<String> purchaseReferenceNumberList = new ArrayList<>();
    purchaseReferenceNumberList.add("9763140004");
    purchaseReferenceNumberList.add("9763140005");
    purchaseReferenceNumberList.add("9763140007");
    Set<Integer> purchaseReferenceLineNumberSet = new HashSet<>();
    purchaseReferenceLineNumberSet.add(1);
    List<ReceiptSummaryQtyByPoAndPoLineResponse> responses =
        receiptCustomRepository.receivedQtyInVNPKByPoAndPoLineList(
            purchaseReferenceNumberList, purchaseReferenceLineNumberSet);
    assertEquals(responses.size(), 3);
    assertTrue(listsEqualsByVNPK(receiptSummaryVnpkList, responses));
  }

  @Test
  public void testGetReceiptsSummaryByDeliveryAndPoAndPoLineAndSsccRetunsNotNullQuantity() {
    RxReceiptsSummaryResponse receiptsSummary =
        receiptCustomRepository.getReceiptsQtySummaryByDeliveryAndPoAndPoLineAndSSCC(
            Long.valueOf("21119003"), "2323", 3, "00123");
    assertNotNull(receiptsSummary);
    assertTrue(receiptsSummary.getReceivedQty() == Long.valueOf(10));
  }

  @Test
  public void testGetReceiptsSummaryByDeliveryAndPoAndPoLineAndSsccReturnsNull() {
    RxReceiptsSummaryResponse receiptsSummary =
        receiptCustomRepository.getReceiptsQtySummaryByDeliveryAndPoAndPoLineAndSSCC(
            Long.valueOf("21119003"), "2323", 3, "001234");
    assertNull(receiptsSummary);
  }

  @Test
  public void testGetReceiptsSummaryByPoReturnsEmpty() {
    List<String> poNumbers = new ArrayList<>();
    poNumbers.add("2121323");
    List<ReceiptSummaryVnpkResponse> receiptsSummary =
        receiptCustomRepository.receivedQtySummaryByPoNumbers(poNumbers);
    assertEquals(receiptsSummary.size(), 0);
  }

  @Test
  public void testGetReceiptsSummaryByPoReturnsNotNullQuantity() {
    List<String> poNumbers = new ArrayList<>();
    poNumbers.add("9763140004");
    List<ReceiptSummaryVnpkResponse> receiptsSummary =
        receiptCustomRepository.receivedQtySummaryByPoNumbers(poNumbers);
    assertEquals(receiptsSummary.size(), 1);
    Assert.assertSame(receiptsSummary.get(0).getReceivedQty(), 6L);
  }

  @Test
  public void testGetFinalizedReceiptCountByDeliveryAndPoRefNumber() {
    assertEquals(
        receiptRepository.getFinalizedReceiptCountByDeliveryAndPoRefNumber(
            "20000001", "9000000001"),
        1);

    assertEquals(
        receiptRepository.getFinalizedReceiptCountByDeliveryAndPoRefNumber(
            "20000001", "9000000002"),
        1);
  }

  @Test
  public void testFindByDeliveryNumberAndOsdrMaster() {
    List<Receipt> osdrMasterReceipts =
        receiptRepository.findByDeliveryNumberAndOsdrMaster(Long.valueOf("21119003"), 1);

    Set<String> purchaseOrders = new HashSet<>();
    for (Receipt osdrMasterReceipt : osdrMasterReceipts) {
      purchaseOrders.add(osdrMasterReceipt.getPurchaseReferenceNumber());
    }

    assertEquals(purchaseOrders.size(), 3);
  }
}
