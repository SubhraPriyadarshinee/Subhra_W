package com.walmart.move.nim.receiving.rc.model.gdm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Dimensions {
  private String uom;
  private Double depth;
  private Double width;
  private Double height;
}
