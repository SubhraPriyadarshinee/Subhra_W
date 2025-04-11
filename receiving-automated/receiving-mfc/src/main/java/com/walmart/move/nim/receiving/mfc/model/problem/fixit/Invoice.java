package com.walmart.move.nim.receiving.mfc.model.problem.fixit;

public class Invoice {

  private static final String INVOICE_NUMBER = "<INVOICE_NUMBER>";
  private static final String INVOICE_LINE_NUMBER = "<INVOICE_LINE_NUMBER>";

  private String request =
      " { number: \\\\\"" + INVOICE_NUMBER + "\\\\\" lineNumber: " + INVOICE_LINE_NUMBER + " }";

  public String getGraphQLString() {
    return request.replaceAll("<[a-z_]*>", "");
  }

  public void setInvoiceNumber(String invoiceNumber) {
    request = request.replaceAll(INVOICE_NUMBER, invoiceNumber);
  }

  public void setInvoiceLineNumber(String invoiceLineNumber) {
    request = request.replaceAll(INVOICE_LINE_NUMBER, invoiceLineNumber);
  }
}
