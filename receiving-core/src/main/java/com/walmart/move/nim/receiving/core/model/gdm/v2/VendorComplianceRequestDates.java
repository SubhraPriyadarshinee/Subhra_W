package com.walmart.move.nim.receiving.core.model.gdm.v2;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class VendorComplianceRequestDates {
  String hazmatVerifiedOn;
  String lithiumIonVerifiedOn;
  String limitedQtyVerifiedOn;
}
