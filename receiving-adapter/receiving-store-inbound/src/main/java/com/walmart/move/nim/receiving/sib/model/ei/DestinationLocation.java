package com.walmart.move.nim.receiving.sib.model.ei;

import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class DestinationLocation {
  private String locationArea;
  private String location;
}
