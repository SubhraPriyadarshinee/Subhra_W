package com.walmart.move.nim.receiving.rdc.mock.data;

import com.walmart.move.nim.receiving.core.model.symbotic.SymPutawayDistribution;
import com.walmart.move.nim.receiving.core.model.symbotic.SymPutawayItem;
import com.walmart.move.nim.receiving.core.model.symbotic.SymPutawayMessage;
import java.util.Collections;

public class MockSymPutawayRequest {

  public static SymPutawayMessage getMockSymPutawayMessage() {
    return SymPutawayMessage.builder()
        .action("add")
        .trackingId("5512098217046")
        .freightType("SSTK")
        .shippingLabelId("5512098217046")
        .labelType("PALLET")
        .inventoryStatus("AVAILABLE")
        .contents(Collections.singletonList(getMockSymPutawayItem()))
        .build();
  }

  private static SymPutawayItem getMockSymPutawayItem() {
    return SymPutawayItem.builder()
        .baseDivisionCode("WM")
        .childItemNumber(12345L)
        .itemNumber(2345L)
        .financialReportingGroupCode("US")
        .deptNumber(1)
        .packagedAsUom("ZA")
        .quantity(12)
        .quantityUOM("CA")
        .rotateDate("2021-01-12")
        .ti(10)
        .hi(5)
        .purchaseReferenceNumber("84587081631")
        .purchaseReferenceLineNumber(1)
        .vendorNumber(123455)
        .poTypeCode(20)
        .primeSlotId("A0001")
        .poEvent("POS REPLEN")
        .distributions(
            Collections.singletonList(SymPutawayDistribution.builder().orderId("1234").build()))
        .build();
  }
}
