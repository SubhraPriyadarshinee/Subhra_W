package com.walmart.move.nim.receiving.mfc.model.controller;

import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ContainerRequestPayload {
  @NotNull private String trackingId;
  private Long deliveryNumber;
  private ContainerOperation type;
  private ContainerDTO container;
}
