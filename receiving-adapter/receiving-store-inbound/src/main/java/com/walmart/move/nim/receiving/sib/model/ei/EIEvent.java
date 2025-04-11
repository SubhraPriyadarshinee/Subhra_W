package com.walmart.move.nim.receiving.sib.model.ei;

import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class EIEvent {
  private EventHeader header;
  private EventBody body;
}
