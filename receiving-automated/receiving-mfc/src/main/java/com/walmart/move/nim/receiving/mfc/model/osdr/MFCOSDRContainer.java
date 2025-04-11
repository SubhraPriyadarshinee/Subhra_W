package com.walmart.move.nim.receiving.mfc.model.osdr;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MFCOSDRContainer {
  private String trackingId;
  private String type;
  private String operationType;
  private List<MFCOSDRItem> content;
}
