package com.walmart.move.nim.receiving.acc.model;

import javax.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OverflowLPNReceivingRequest {

  @NotNull private String lpn;

  private Long deliveryNumber;

  private String location;

  private String upc;

  private boolean upcValidationRequired;

  private boolean verifyContainerExists;
}
