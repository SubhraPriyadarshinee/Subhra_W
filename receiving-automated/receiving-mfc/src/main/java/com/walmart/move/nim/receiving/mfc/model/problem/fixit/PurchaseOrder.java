package com.walmart.move.nim.receiving.mfc.model.problem.fixit;

public class PurchaseOrder {

  private static final String STORE_NUMBER = "<STORE_NUMBER>";
  private static final String SSCC_NUMBER = "<SSCC_NUMBER>";
  private static final String SHIPMENT_DOC_ID = "<SHIPMENT_DOC_ID>";

  private String request =
      " { storeNumber: \\\\\""
          + STORE_NUMBER
          + "\\\\\" packNumber: \\\\\""
          + SSCC_NUMBER
          + "\\\\\" asnNumber: \\\\\""
          + SHIPMENT_DOC_ID
          + "\\\\\" }";

  public String getGraphQLString() {
    return request.replaceAll("<[a-z_]*>", "");
  }

  public void setStoreNumber(String storeNumber) {
    request = request.replaceAll(STORE_NUMBER, storeNumber);
  }

  public void setSsccNumber(String ssccNumber) {
    request = request.replaceAll(SSCC_NUMBER, ssccNumber);
  }

  public void setShipmentDocId(String shipmentDocID) {
    request = request.replaceAll(SHIPMENT_DOC_ID, shipmentDocID);
  }
}
