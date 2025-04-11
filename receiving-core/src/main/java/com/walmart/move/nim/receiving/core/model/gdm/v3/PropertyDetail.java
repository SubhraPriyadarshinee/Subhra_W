package com.walmart.move.nim.receiving.core.model.gdm.v3;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyDetail {
  private String uom;
  private Float quantity;
}
