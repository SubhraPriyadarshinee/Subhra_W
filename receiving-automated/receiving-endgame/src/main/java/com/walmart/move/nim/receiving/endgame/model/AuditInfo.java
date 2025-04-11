package com.walmart.move.nim.receiving.endgame.model;

import java.util.Objects;
import lombok.Setter;
import lombok.ToString;

@Setter
@ToString
public class AuditInfo {
  private Boolean isCaseFlagged;
  private Boolean isPalletFlagged;
  private Integer palletSellableUnits;

  public Boolean getCaseFlagged() {
    return Objects.isNull(isCaseFlagged) ? Boolean.FALSE : isCaseFlagged;
  }

  public Boolean getPalletFlagged() {
    return Objects.isNull(isPalletFlagged) ? Boolean.FALSE : isPalletFlagged;
  }

  public Integer getPalletSellableUnits() {
    return palletSellableUnits;
  }
}
