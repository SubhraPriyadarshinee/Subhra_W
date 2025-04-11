package com.walmart.move.nim.receiving.endgame.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class ContainerDetail {

  private List<ContainerItemsDetail> containerItemsDetails;
  private String containerTrackingId;
  private String containerType;
  private String containerLabel;
  private String containerName;
}
