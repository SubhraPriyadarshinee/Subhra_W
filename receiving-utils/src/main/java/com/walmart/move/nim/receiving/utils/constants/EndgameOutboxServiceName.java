package com.walmart.move.nim.receiving.utils.constants;

public enum EndgameOutboxServiceName {
  PO_RECEIPT("poReceipt"),
  OB_RECEIVE_TCL_AUTO_SCAN("receiveTCLAutoScanRequest"),
  OB_ATTACH_PO_TO_DELIVERY("attachPurchaseOrdertoDelivery"),
  DCFIN_PURCHASE_POSTING("dcfinPurchasePosting"),
  INVENTORY_RECEIPT_CREATION("inventoryReceiptCreation");
  private final String serviceName;

  EndgameOutboxServiceName(String serviceName) {
    this.serviceName = serviceName;
  }

  public String getServiceName() {
    return this.serviceName;
  }
}
