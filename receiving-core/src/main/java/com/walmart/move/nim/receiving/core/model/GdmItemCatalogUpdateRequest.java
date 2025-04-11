package com.walmart.move.nim.receiving.core.model;

import lombok.Builder;
import lombok.ToString;

/** This is for the request made to GDM for updating the vendor UPC */
@ToString
@Builder
public class GdmItemCatalogUpdateRequest {
  private String vendorUPC;
}
