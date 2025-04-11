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
public class PalletSlotRequest {

  private List<ContainerDetail> containerDetails;
  private String messageId;
  private String sourceLocation;
}
