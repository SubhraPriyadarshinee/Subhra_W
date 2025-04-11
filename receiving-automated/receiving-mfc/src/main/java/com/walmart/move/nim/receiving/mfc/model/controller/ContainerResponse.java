package com.walmart.move.nim.receiving.mfc.model.controller;

import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class ContainerResponse {
  private ContainerOperation type;

  private List<ContainerDTO> containers;
}
