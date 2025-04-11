package com.walmart.move.nim.receiving.mfc.model.problem.fixit;

public class Item {

  private static final String ITEM_UPC = "<ITEM_UPC>";
  private static final String DEPARTMENT_NUMBER = "<DEPARTMENT_NUMBER>";
  private static final String ITEM_DESCRIPTION = "<ITEM_DESCRIPTION>";
  private static final String ITEM_NUMBER = "<ITEM_NUMBER>";

  private String request =
      " { itemNumber: \\\\\""
          + ITEM_NUMBER
          + "\\\\\" itemDescription: \\\\\""
          + ITEM_DESCRIPTION
          + "\\\\\" departmentNumber: "
          + DEPARTMENT_NUMBER
          + " itemUpc: \\\\\""
          + ITEM_UPC
          + "\\\\\" }";

  public String getGraphQLString() {
    return request.replaceAll("<[a-z_]*>", "");
  }

  public void setItemUpc(String itemUpc) {
    request = request.replaceAll(ITEM_UPC, itemUpc);
  }

  public void setDepartmentNumber(String departmentNumber) {
    request = request.replaceAll(DEPARTMENT_NUMBER, departmentNumber);
  }

  public void setItemNumber(String itemNumber) {
    request = request.replaceAll(ITEM_NUMBER, itemNumber);
  }

  public void setItemDescription(String itemDescription) {
    request = request.replaceAll(ITEM_DESCRIPTION, itemDescription);
  }
}
