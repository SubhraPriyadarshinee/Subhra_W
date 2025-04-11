package com.walmart.move.nim.receiving.reporting.repositories;

import static org.testng.Assert.assertEquals;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryEachesResponse;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryResponse;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryVnpkResponse;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.core.repositories.ReceiptRepository;
import com.walmart.move.nim.receiving.reporting.model.UserCaseChannelTypeResponse;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ReportingReceiptRepositoryTest extends ReceivingTestBase {

  @Autowired private ReceiptRepository receiptRepository;
  @Autowired private ReportingReceiptCustomRepository receiptCustomRepository;

  private List<ReceiptSummaryResponse> receiptSummaryVnpkList;
  private List<ReceiptSummaryResponse> receiptSummaryEachesList;

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
    receipt8.setQuantity(10);
    receipt8.setQuantityUom("ZA");
    receipt8.setVnpkQty(48);
    receipt8.setWhpkQty(4);
    receipt8.setEachQty(96);
    receipt8.setCreateUserId("sysadmin");
    receipt8.setVersion(1);
    receipt8.setOsdrMaster(null);
    receipt8.setCreateTs(new Date());

    receipts.add(receipt1);
    receipts.add(receipt2);
    receipts.add(receipt3);
    receipts.add(receipt4);
    receipts.add(receipt5);
    receipts.add(receipt6);
    receipts.add(receipt7);
    receipts.add(receipt8);

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
  }

  @Test
  public void testUserCaseChannelTypeList() {

    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -24);
    Date afterDate = cal.getTime();
    List<UserCaseChannelTypeResponse> responses =
        receiptCustomRepository.getUserCasesByChannelType(
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode(),
            afterDate,
            Calendar.getInstance().getTime());
    assertEquals(responses.size(), 1);
    assertEquals(responses.get(0).getCasesCount(), 10);
    assertEquals(responses.get(0).getUser(), "sysadmin");
    assertEquals(responses.get(0).getChannelType(), "CROSSU");
  }
}
