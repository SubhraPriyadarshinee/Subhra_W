package com.walmart.move.nim.receiving.mfc.model.mixedpallet;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class MixedPalletAdjustmentTO {
  private String sourceCreationTimestamp;
  private String containerId;
  private int revision;
  private String userId;
  private String correlationId;
  private List<PalletItem> items;
}
