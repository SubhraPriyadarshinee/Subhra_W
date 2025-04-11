package com.walmart.move.nim.receiving.endgame.model;

import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class ExtraAttributes {
  private String legacyType;
  private String containerSize;
  private String receivingType;
  private DeliveryStatus deliveryStatus;
  private Boolean isOverboxingPallet;
}
