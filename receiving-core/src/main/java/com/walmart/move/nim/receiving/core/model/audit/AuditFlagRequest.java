package com.walmart.move.nim.receiving.core.model.audit;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuditFlagRequest {
  private List<AuditFlagRequestItem> items;
  private Long deliveryNumber;
}
