package com.walmart.move.nim.receiving.endgame.model;

import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Builder
public class PalletResponse {
  private LabelResponse labelResponse;
  private List<ContainerDTO> container;
  private List<SlotLocation> moveDestinations;
}
