package com.walmart.move.nim.receiving.endgame.mock.data;

import com.walmart.move.nim.receiving.core.model.osdr.OsdrData;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrPo;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrPoLine;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrSummary;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Arrays;

public class MockOsdrSummary {
  public static OsdrSummary getOsdrSummary() {
    OsdrData osdrData = OsdrData.builder().quantity(1).uom(ReceivingConstants.Uom.VNPK).build();
    OsdrData osdrDataPo = OsdrData.builder().quantity(2).uom(ReceivingConstants.Uom.VNPK).build();
    OsdrPoLine osdrPoLine =
        OsdrPoLine.builder()
            .lineNumber(Long.valueOf(1))
            .rcvdQty(2)
            .rcvdQtyUom(ReceivingConstants.Uom.VNPK)
            .overage(osdrData)
            .shortage(osdrData)
            .damage(osdrData)
            .reject(osdrData)
            .build();
    OsdrPo osdrPo =
        OsdrPo.builder()
            .purchaseReferenceNumber("9763140005")
            .rcvdQty(4)
            .rcvdQtyUom(ReceivingConstants.Uom.VNPK)
            .overage(osdrDataPo)
            .shortage(osdrDataPo)
            .damage(osdrDataPo)
            .reject(osdrDataPo)
            .lines(Arrays.asList(osdrPoLine, osdrPoLine))
            .build();
    OsdrSummary osdrSummary =
        OsdrSummary.builder()
            .eventType(EndgameConstants.ENDGAME_OSDR_EVENT_TYPE_VALUE)
            .summary(Arrays.asList(osdrPo))
            .build();
    osdrSummary.setDeliveryNumber(12333333L);
    osdrSummary.setUserId(EndgameConstants.DEFAULT_AUDIT_USER);
    return osdrSummary;
  }
}
