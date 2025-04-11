package com.walmart.move.nim.receiving.mfc.model.common;

import com.walmart.move.nim.receiving.utils.constants.Eligibility;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class PalletInfo {
  private String palletType;
  private Eligibility eligibility;
}
