package com.walmart.move.nim.receiving.core.model.osdr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class OsdrData {
  private Integer quantity;
  private String uom;
  private String code;
  private String claimType;
  private String comment;

  public void addQuantity(Integer quantity) {
    this.quantity += quantity;
  }
}
