package com.walmart.move.nim.receiving.mfc.model.problem.fixit;

public class Detail {

  private static final String EXCEPTION_TYPE = "<EXCEPTION_TYPE>";
  private static final String EXCEPTION_QTY = "<EXCEPTION_QTY>";
  private static final String DESTINATION_BU_NUMBER = "<DESTINATION_BU_NUMBER>";
  private static final String STORE_NUMBER = "<STORE_NUMBER>";
  private static final String SSCC_NUMBER = "<SSCC_NUMBER>";
  private static final String SHIPMENT_DOC_ID = "<SHIPMENT_DOC_ID>";

  private String request =
      " { exceptionType: \\\\\""
          + EXCEPTION_TYPE
          + "\\\\\" exceptionQty: "
          + EXCEPTION_QTY
          + " exceptionUOM: \\\\\"ZA\\\\\""
          + " destinationBuNumber: "
          + DESTINATION_BU_NUMBER
          + " shippedTo: \\\\\""
          + STORE_NUMBER
          + "\\\\\" referenceType: \\\\\"PACK_NUMBER\\\\\""
          + " referenceTicket: \\\\\""
          + SSCC_NUMBER
          + "\\\\\" shipmentNumber: \\\\\""
          + SHIPMENT_DOC_ID
          + "\\\\\" }";

  public String getGraphQLString() {
    return request.replaceAll("<[a-z_]*>", "");
  }

  public void setExceptionType(String exceptionType) {
    request = request.replaceAll(EXCEPTION_TYPE, exceptionType);
  }

  public void setExceptionQty(String exceptionQty) {
    request = request.replaceAll(EXCEPTION_QTY, exceptionQty);
  }

  public void setDestinationBuNumber(String destinationBuNumber) {
    request = request.replaceAll(DESTINATION_BU_NUMBER, destinationBuNumber);
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
