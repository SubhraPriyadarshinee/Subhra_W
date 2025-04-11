package com.walmart.move.nim.receiving.core.model.osdr.v2;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class OsdrDataWeightBased {
  private Double quantity;
  private String uom;
  private Double derivedQuantity;
  private String derivedUom;
  private String code;
  private String claimType;
  private String comment;
  @JsonIgnore private Integer scaledQuantity;
  @JsonIgnore private String scaledUom;

  public void addQuantity(Double quantity) {
    this.quantity += quantity;
    this.derivedQuantity += quantity;
  }
}
