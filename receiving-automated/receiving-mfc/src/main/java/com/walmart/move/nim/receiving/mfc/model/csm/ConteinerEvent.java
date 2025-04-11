package com.walmart.move.nim.receiving.mfc.model.csm;

import java.util.List;
import lombok.*;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class ConteinerEvent {
  private String srcTrackingId;
  private Long deliveryNumber;
  private List<ContainerEventItem> itemList;
}
