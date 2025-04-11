package com.walmart.move.nim.receiving.endgame.model;

import java.io.Serializable;
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
public class DimensionPayload implements Serializable {
  private String caseUPC;
  private Dimensions dimensions;
  private String dimensionsUnitOfMeasure;
  private Long itemNumber;
  private String itemUPC;
}
