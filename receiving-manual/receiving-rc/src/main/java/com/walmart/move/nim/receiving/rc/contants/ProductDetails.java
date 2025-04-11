package com.walmart.move.nim.receiving.rc.contants;

public enum ProductDetails {
  L0("l0"),
  L1("l1"),
  L2("l2"),
  L3("l3"),
  PRODUCT_TYPE("product_type"),
  GROUP("group");

  public final String value;

  private ProductDetails(String value) {
    this.value = value;
  }

  private static ProductDetails fromString(String value) {
    for (ProductDetails productDetails : ProductDetails.values()) {
      if (productDetails.value.equalsIgnoreCase((value))) {
        return productDetails;
      }
    }
    return null;
  }
}
