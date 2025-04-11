package com.walmart.move.nim.receiving.core.client.damage;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
public class DamageDeliveryInfo {

  private String deliveryNumber;
  private String poNumber;
  private Integer poLineNumber;
  private String itemNumber;
  private Integer quantity;
  private DamageCode damageCode;
  private String uom;
  private String claimType;
}
