package com.walmart.move.nim.receiving.mfc.model.hawkeye;

import java.util.List;
import lombok.Data;

@Data
public class HawkeyeAdjustment {
  private String sourceCreationTimestamp;
  private String containerId;
  private int revision;
  private String userId;
  private List<DecantItem> items;
}
