package com.walmart.move.nim.receiving.witron.service;

import static org.mockito.Mockito.reset;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrPo;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrPoLine;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrSummary;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.utils.constants.OSDRCode;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class WitronOsdrServiceTest extends ReceivingTestBase {

  @Mock ReceiptService receiptService;
  @InjectMocks WitronOsdrService witronOsdrService;

  @BeforeClass
  public void initMock() {
    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  public void resetMocks() {
    reset(receiptService);
  }

  private List<Receipt> getReceiptsForOSDRDetails() {

    Receipt receipt1 = new Receipt();
    receipt1.setDeliveryNumber(12333333L);
    receipt1.setPurchaseReferenceNumber("9763140005");
    receipt1.setPurchaseReferenceLineNumber(1);
    receipt1.setWhpkQty(1);
    receipt1.setVnpkQty(1);
    receipt1.setCreateTs(new Date());
    receipt1.setCreateUserId("sysadmin");
    receipt1.setQuantity(4);
    receipt1.setQuantityUom(ReceivingConstants.Uom.VNPK);
    receipt1.setFbRejectedQty(1);
    receipt1.setFbRejectedQtyUOM(ReceivingConstants.Uom.VNPK);
    receipt1.setFbRejectedReasonCode(OSDRCode.R10);
    receipt1.setFbRejectionComment("rejection comment");
    receipt1.setFbOverQty(1);
    receipt1.setFbOverQtyUOM(ReceivingConstants.Uom.VNPK);
    receipt1.setFbOverReasonCode(OSDRCode.O13);
    receipt1.setFbShortQty(1);
    receipt1.setFbShortQtyUOM(ReceivingConstants.Uom.VNPK);
    receipt1.setFbDamagedQty(2);
    receipt1.setFbDamagedQtyUOM(ReceivingConstants.Uom.VNPK);
    receipt1.setFbDamagedReasonCode(OSDRCode.D53);
    receipt1.setFbDamagedClaimType(ReceivingConstants.VDM_CLAIM_TYPE);
    receipt1.setOsdrMaster(1);

    Receipt receipt2 = new Receipt();
    receipt2.setDeliveryNumber(12333333L);
    receipt2.setPurchaseReferenceNumber("9763140005");
    receipt2.setPurchaseReferenceLineNumber(2);
    receipt2.setCreateTs(new Date());
    receipt2.setCreateUserId("sysadmin");
    receipt2.setWhpkQty(1);
    receipt2.setVnpkQty(1);
    receipt2.setQuantity(4);
    receipt2.setQuantityUom(ReceivingConstants.Uom.VNPK);
    receipt2.setFbRejectedQty(1);
    receipt2.setFbRejectedQtyUOM(ReceivingConstants.Uom.VNPK);
    receipt2.setFbRejectedReasonCode(OSDRCode.R10);
    receipt2.setFbRejectionComment("rejection comment");
    receipt2.setFbOverQtyUOM(ReceivingConstants.Uom.VNPK);
    receipt2.setFbOverQty(1);
    receipt2.setFbShortQty(1);
    receipt2.setFbShortQtyUOM(ReceivingConstants.Uom.VNPK);
    receipt2.setFbShortReasonCode(OSDRCode.S10);
    receipt2.setFbDamagedQty(2);
    receipt2.setFbDamagedQtyUOM(ReceivingConstants.Uom.VNPK);
    receipt2.setFbDamagedReasonCode(OSDRCode.D53);
    receipt2.setFbDamagedClaimType(ReceivingConstants.VDM_CLAIM_TYPE);
    receipt2.setOsdrMaster(1);

    return Arrays.asList(receipt1, receipt2);
  }

  @Test
  public void testGetOsdrDetailsForWitron() {

    OsdrSummary osdrSummary =
        witronOsdrService.getOsdrDetails(
            12333333L,
            getReceiptsForOSDRDetails(),
            ReceivingConstants.Uom.VNPK,
            ReceivingConstants.DEFAULT_AUDIT_USER);
    assertEquals(
        osdrSummary.getDeliveryNumber(),
        Long.valueOf(12333333),
        "Osdr summary delivery number not matched");
    assertEquals(
        osdrSummary.getUserId(),
        ReceivingConstants.DEFAULT_AUDIT_USER,
        "Osdr summary user id not matched");
    assertEquals(
        osdrSummary.getEventType(),
        ReceivingConstants.OSDR_EVENT_TYPE_VALUE,
        "Osdr summary event type not matched");

    assertEquals(osdrSummary.getSummary().size(), 1, "Osdr summary details not matched");
    for (OsdrPo osdrPo : osdrSummary.getSummary()) {

      assertEquals(
          osdrPo.getRcvdQty(), Integer.valueOf(8), "Osdr summary po received qty not matched");
      assertNull(osdrPo.getOverage(), "Osdr summary for witron shouldn't have overage");
      assertNull(osdrPo.getOverage(), "Osdr summary for witron shouldn't have shortage");
      assertNull(osdrPo.getOverage(), "Osdr summary for witron shouldn't have damage");
      assertNull(osdrPo.getPalletQty(), "Osdr summary for witron shouldn't have palletQty");

      assertEquals(
          osdrPo.getReject().getQuantity(),
          Integer.valueOf(2),
          "Osdr summary po reject qty not matched");
      assertEquals(
          osdrPo.getReject().getCode(),
          OSDRCode.R10.getCode(),
          "Osdr summary po reject code not matched");
      assertEquals(
          osdrPo.getReject().getComment(),
          "rejection comment",
          "Osdr summary po reject comment not matched");
      assertEquals(
          osdrPo.getRcvdQtyUom(),
          ReceivingConstants.Uom.VNPK,
          "Osdr summary po received qty uom not matched");
      assertEquals(
          osdrPo.getReject().getUom(),
          ReceivingConstants.Uom.VNPK,
          "Osdr summary po reject qty uom not matched");
      assertEquals(
          osdrPo.getPurchaseReferenceNumber(),
          "9763140005",
          "Osdr summary purchase reference number not matched");
      assertEquals(osdrPo.getLines().size(), 2, "Osdr summary details for line not matched");

      for (OsdrPoLine osdrPoLine : osdrPo.getLines()) {

        assertEquals(
            osdrPoLine.getRcvdQty(),
            Integer.valueOf(4),
            "Osdr summary po line received qty not matched");

        /*
        purchase reference number - 9763140005 and line 2 will have shortage
        */
        if (osdrPoLine.getLineNumber().equals(Long.valueOf(2))) {
          assertEquals(
              osdrPoLine.getReject().getQuantity(),
              Integer.valueOf(1),
              "Osdr summary po line reject qty not matched");
          assertEquals(
              osdrPo.getReject().getCode(),
              OSDRCode.R10.getCode(),
              "Osdr summary po reject code not matched");
          assertEquals(
              osdrPo.getReject().getComment(),
              "rejection comment",
              "Osdr summary po reject comment not matched");
          assertEquals(
              osdrPoLine.getRcvdQtyUom(),
              ReceivingConstants.Uom.VNPK,
              "Osdr summary po line received qty uom not matched");
          assertEquals(
              osdrPoLine.getReject().getUom(),
              ReceivingConstants.Uom.VNPK,
              "Osdr summary po line reject qty uom not matched");

          if (!(osdrPoLine.getLineNumber().equals(Long.valueOf(1))
              || osdrPoLine.getLineNumber().equals(Long.valueOf(2)))) {
            assertTrue(false, "Osdr summary po line number not matched");
          }
        }
      }
    }
  }
}
