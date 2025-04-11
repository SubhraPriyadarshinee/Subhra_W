package com.walmart.move.nim.receiving.endgame.model;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class LabelRequest {

  @NotNull private Long deliveryNumber;
  private String doorNumber;
  @NotEmpty private String labelType;
  @NotNull private Long numberOfLabels;
  private String trailerId;
  @NotEmpty private String labelGenMode;
}
