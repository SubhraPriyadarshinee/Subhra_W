package com.walmart.move.nim.receiving.sib.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ContainerEventData {
  private String srcTrackingId;
  private Long deliveryNumber;
  private List<ItemData> itemList;
}
