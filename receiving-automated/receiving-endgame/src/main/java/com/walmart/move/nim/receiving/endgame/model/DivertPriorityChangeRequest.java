package com.walmart.move.nim.receiving.endgame.model;

import javax.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class DivertPriorityChangeRequest {
  private String previousDivert;

  @NotEmpty(message = "caseUPC can not be empty")
  private String caseUPC;

  @NotEmpty(message = "newDivert can not be empty")
  private String newDivert;
}
