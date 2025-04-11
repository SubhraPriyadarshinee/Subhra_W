package com.walmart.move.nim.receiving.wfs.mock.data;

import com.walmart.move.nim.receiving.core.entity.Receipt;

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
}
