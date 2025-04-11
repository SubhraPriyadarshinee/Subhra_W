package com.walmart.move.nim.receiving.core.model.gdm.v3;

import javax.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class Dates {
  private String ordered;
  private String cancel;
  private String ship;
  @NotEmpty private String mabd;
}
