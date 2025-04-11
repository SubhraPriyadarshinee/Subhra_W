package com.walmart.move.nim.receiving.mfc.model.problem.fixit;

public class FixitCloseExceptionRequest {

  private static final String EXCEPTION_ID = "<EXCEPTION_ID>";
  private static final String STORE_NUMBER = "<STORE_NUMBER>";

  private String request =
      "{\"query\":\" mutation  {  closeException ( exceptionId:\\\""
          + EXCEPTION_ID
          + "\\\",input:{reason:\\\"SYSTEM_ISSUE\\\",comment:\\\"Ticket not Required.\\\"},userInfo:{userId:\\\""
          + STORE_NUMBER
          + "\\\",userName:\\\"Store Admin\\\"} ) { exceptionId,identifier } }\",\"variables\":{}}";

  public String getRequest() {
    return request.replaceAll("<[a-z_]*>", "");
  }

  public void setExceptionId(String exceptionId) {
    request = request.replaceAll(EXCEPTION_ID, exceptionId);
  }

  public void setStoreNumber(String storeNumber) {
    request = request.replaceAll(STORE_NUMBER, storeNumber);
  }
}
