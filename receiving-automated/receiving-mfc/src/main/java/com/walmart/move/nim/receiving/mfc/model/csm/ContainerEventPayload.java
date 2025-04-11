package com.walmart.move.nim.receiving.mfc.model.csm;

import java.util.List;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ContainerEventPayload {
  private List<ConteinerEvent> payload;
}
