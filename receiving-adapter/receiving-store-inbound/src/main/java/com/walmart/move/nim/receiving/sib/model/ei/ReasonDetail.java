package com.walmart.move.nim.receiving.sib.model.ei;

import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class ReasonDetail {
  private String reasonCode;
  private String reasonDesc;
}
