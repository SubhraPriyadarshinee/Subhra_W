package com.walmart.move.nim.receiving.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabelDownloadEventMiscInfo {
  boolean isVoidLpnToHawkeyeRequired;
  String labelType;
  Integer purchaseReferenceLineNumber;
}
