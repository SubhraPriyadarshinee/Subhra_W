package com.walmart.move.nim.receiving.core.model;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class RecordOSDRResponse {

  private Integer overageQty;
  private String overageUOM;

  private Integer shortageQty;
  private String shortageUOM;

  private Integer damageQty;
  private String damageUOM;

  private Integer rejectedQty;
  private String rejectedUOM;

  private Integer concealedShortageQty = 0;

  private Integer problemQty;
  private String problemUOM;

  @NotNull private Integer version;

  @NotBlank private String poHashKey;
}
