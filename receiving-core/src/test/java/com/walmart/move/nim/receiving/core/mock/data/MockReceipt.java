package com.walmart.move.nim.receiving.core.mock.data;

import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.utils.constants.OSDRCode;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class MockReceipt {

  public static Receipt getReceipt() {
    Receipt receipt = new Receipt();
    receipt.setProblemId(null);
    receipt.setCreateUserId("sysadmin");
    receipt.setEachQty(4);
    receipt.setDeliveryNumber(21119003L);
    receipt.setDoorNumber("171");
    receipt.setPurchaseReferenceLineNumber(1);
    receipt.setPurchaseReferenceNumber("9763140005");
    receipt.setQuantity(2);
    receipt.setQuantityUom("ZA");
    receipt.setVnpkQty(2);
    receipt.setWhpkQty(4);
    return receipt;
  }

  public static Receipt getOSDRMasterReceipt() {
    Receipt receipt = new Receipt();
    receipt.setCreateTs(Date.from(Instant.now()));
    receipt.setQuantity(20);
    receipt.setQuantityUom(ReceivingConstants.Uom.VNPK);
    receipt.setDeliveryNumber(21119003L);
    receipt.setPurchaseReferenceNumber("9763140005");
    receipt.setPurchaseReferenceLineNumber(1);
    receipt.setOsdrMaster(1);
    receipt.setFbProblemQty(0);
    receipt.setFbDamagedQty(0);
    receipt.setFbRejectedQty(0);
    receipt.setFbShortQty(0);
    receipt.setFbOverQty(0);
    receipt.setFbConcealedShortageQty(0);
    return receipt;
  }

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

  public static List<Receipt> getAccReceiptsForOSDRDetails() {

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
    receipt1.setFbRejectedQty(0);
    receipt1.setFbRejectedQtyUOM(ReceivingConstants.Uom.VNPK);
    receipt1.setFbRejectedReasonCode(OSDRCode.R10);
    receipt1.setFbRejectionComment("rejection comment");

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
    receipt2.setFbRejectedQty(0);
    receipt2.setFbRejectedQtyUOM(ReceivingConstants.Uom.VNPK);
    receipt2.setFbRejectedReasonCode(OSDRCode.R10);
    receipt2.setFbRejectionComment("rejection comment");

    return Arrays.asList(receipt1, receipt2);
  }

  public static List<Receipt> getDSDCReceiptsForOSDRDetails() {

    Receipt receipt1 = new Receipt();
    receipt1.setDeliveryNumber(12333333L);
    receipt1.setPurchaseReferenceNumber("9763140005");
    receipt1.setWhpkQty(1);
    receipt1.setVnpkQty(1);
    receipt1.setCreateTs(new Date());
    receipt1.setCreateUserId("sysadmin");
    receipt1.setQuantity(4);
    receipt1.setQuantityUom(ReceivingConstants.Uom.VNPK);
    receipt1.setFbRejectedQty(0);
    receipt1.setFbRejectedQtyUOM(ReceivingConstants.Uom.VNPK);
    receipt1.setFbRejectedReasonCode(OSDRCode.R10);
    receipt1.setFbRejectionComment("rejection comment");
    receipt1.setPalletQty(1);

    Receipt receipt2 = new Receipt();
    receipt2.setDeliveryNumber(12333333L);
    receipt2.setPurchaseReferenceNumber("9763140005");
    receipt2.setCreateTs(new Date());
    receipt2.setCreateUserId("sysadmin");
    receipt2.setWhpkQty(1);
    receipt2.setVnpkQty(1);
    receipt2.setQuantity(4);
    receipt2.setQuantityUom(ReceivingConstants.Uom.VNPK);
    receipt2.setFbRejectedQty(0);
    receipt2.setFbRejectedQtyUOM(ReceivingConstants.Uom.VNPK);
    receipt2.setFbRejectedReasonCode(OSDRCode.R10);
    receipt2.setFbRejectionComment("rejection comment");
    receipt2.setPalletQty(1);

    Receipt receipt3 = new Receipt();
    receipt3.setDeliveryNumber(12333333L);
    receipt3.setPurchaseReferenceNumber("9763140006");
    receipt3.setCreateTs(new Date());
    receipt3.setCreateUserId("sysadmin");
    receipt3.setWhpkQty(1);
    receipt3.setVnpkQty(1);
    receipt3.setQuantity(4);
    receipt3.setQuantityUom(ReceivingConstants.Uom.VNPK);
    receipt3.setFbRejectedQty(0);
    receipt3.setFbRejectedQtyUOM(ReceivingConstants.Uom.VNPK);
    receipt3.setFbRejectedReasonCode(OSDRCode.R10);
    receipt3.setFbRejectionComment("rejection comment");
    receipt3.setPalletQty(1);

    Receipt receipt4 = new Receipt();
    receipt4.setDeliveryNumber(12333333L);
    receipt4.setPurchaseReferenceNumber("9763140006");
    receipt4.setCreateTs(new Date());
    receipt4.setCreateUserId("sysadmin");
    receipt4.setWhpkQty(1);
    receipt4.setVnpkQty(1);
    receipt4.setQuantity(4);
    receipt4.setQuantityUom(ReceivingConstants.Uom.VNPK);
    receipt4.setFbRejectedQty(0);
    receipt4.setFbRejectedQtyUOM(ReceivingConstants.Uom.VNPK);
    receipt4.setFbRejectedReasonCode(OSDRCode.R10);
    receipt4.setFbRejectionComment("rejection comment");
    receipt4.setPalletQty(1);

    return Arrays.asList(receipt1, receipt2, receipt3, receipt4);
  }
}
