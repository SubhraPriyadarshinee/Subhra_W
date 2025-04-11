package com.walmart.move.nim.receiving.sib.model.ei;

import lombok.*;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class NodeInfo {
  private Integer nodeId;
  private String countryCode;
  private String nodeType;
}
