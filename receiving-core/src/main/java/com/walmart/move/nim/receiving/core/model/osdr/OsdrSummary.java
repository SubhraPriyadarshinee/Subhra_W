package com.walmart.move.nim.receiving.core.model.osdr;

import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class OsdrSummary extends DeliveryInfo {
  private String eventType;
  private Boolean auditPending;
  private List<OsdrPo> summary;
  private List<Map<String, Object>> openInstructions;
}
