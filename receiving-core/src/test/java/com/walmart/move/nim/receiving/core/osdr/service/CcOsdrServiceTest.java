package com.walmart.move.nim.receiving.core.osdr.service;

import static org.mockito.Mockito.reset;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.mock.data.MockReceipt;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrPo;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrSummary;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.utils.constants.OSDRCode;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class CcOsdrServiceTest extends ReceivingTestBase {

  @Mock ReceiptService receiptService;

  @InjectMocks CcOsdrService ccOsdrService;

  @BeforeClass
  public void initMock() {
    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  public void resetMocks() {
    reset(receiptService);
  }

  @Test
  public void testGetOsdrDetailsForCc() {

    List<Receipt> receiptList = MockReceipt.getDSDCReceiptsForOSDRDetails();

    OsdrSummary osdrSummary =
        ccOsdrService.getOsdrDetails(
            12333333L,
            receiptList,
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

    assertEquals(osdrSummary.getSummary().size(), 2, "Osdr summary details not matched");
    for (OsdrPo osdrPo : osdrSummary.getSummary()) {

      assertEquals(
          osdrPo.getRcvdQty(), Integer.valueOf(8), "Osdr summary po received qty not matched");
      assertNull(osdrPo.getOverage(), "Osdr summary for DSDC shouldn't have overage");
      assertNull(osdrPo.getOverage(), "Osdr summary for DSDC shouldn't have shortage");
      assertNull(osdrPo.getOverage(), "Osdr summary for DSDC shouldn't have damage");

      assertEquals(
          osdrPo.getPalletQty(), Integer.valueOf(2), "Osdr summary for DSDC must have palletQty");

      assertEquals(
          osdrPo.getReject().getQuantity(),
          Integer.valueOf(0),
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

      assertEquals(osdrPo.getLines(), null, "No po-line level data for DSDC");
    }
  }
}
