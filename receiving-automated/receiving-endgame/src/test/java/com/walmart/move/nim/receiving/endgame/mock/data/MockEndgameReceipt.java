package com.walmart.move.nim.receiving.endgame.mock.data;

import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.utils.constants.OSDRCode;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class MockEndgameReceipt {

  public static List<Receipt> getReceiptsForOSDRDetails() {

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
    receipt1.setFbShortQtyUOM(ReceivingConstants.Uom.VNPK);
    receipt1.setFbDamagedQty(1);
    receipt1.setFbDamagedQtyUOM(ReceivingConstants.Uom.VNPK);
    receipt1.setFbDamagedReasonCode(OSDRCode.D53);
    receipt1.setFbDamagedClaimType(ReceivingConstants.VDM_CLAIM_TYPE);

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
    receipt2.setFbShortQty(1);
    receipt2.setFbShortQtyUOM(ReceivingConstants.Uom.VNPK);
    receipt2.setFbShortReasonCode(OSDRCode.S10);
    receipt2.setFbDamagedQty(1);
    receipt2.setFbDamagedQtyUOM(ReceivingConstants.Uom.VNPK);
    receipt2.setFbDamagedReasonCode(OSDRCode.D53);
    receipt2.setFbDamagedClaimType(ReceivingConstants.VDM_CLAIM_TYPE);

    return Arrays.asList(receipt1, receipt2);
  }
}
