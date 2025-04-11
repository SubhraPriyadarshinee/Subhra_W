package com.walmart.move.nim.receiving.core.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Class for update vendor compliance payload
 *
 * @author sks0013
 */
@Getter
@Setter
@ToString
public class UpdateVendorComplianceRequest {
  private VendorCompliance regulatedItemType;
  private String itemNumber;
}
