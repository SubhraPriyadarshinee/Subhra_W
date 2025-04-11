package com.walmart.move.nim.receiving.utils.constants;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ActivityName {
  DA_CONVENYABLE("DACon"),
  DA_NON_CONVEYABLE("DANonCon"),
  PBYL_CONVENYABLE("PBYL"),
  PBYL_NON_CONVEYABLE("PBYL"),
  DA_CASE("ACL"),
  STAPLE_STOCK("SSTK"),
  POCON("POCON"),
  DSDC("DSDC");

  private String activityName;
}
