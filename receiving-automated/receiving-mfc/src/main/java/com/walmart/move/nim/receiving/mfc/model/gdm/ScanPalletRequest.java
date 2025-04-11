package com.walmart.move.nim.receiving.mfc.model.gdm;

import lombok.*;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class ScanPalletRequest {

  private String palletNumber;
  private Long deliveryNumber;
}
