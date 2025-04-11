package com.walmart.move.nim.receiving.sib.model.ei;

import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class ItemIdentifier {
  private String gtin;
  private String itemNbr;
}
