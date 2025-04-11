package com.walmart.move.nim.receiving.sib.model.ei;

import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class SourceNode {
  private Integer nodeId;
  private String countryCode;
  private String nodeType;
}
