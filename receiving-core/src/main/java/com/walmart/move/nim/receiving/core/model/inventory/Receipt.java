package com.walmart.move.nim.receiving.core.model.inventory;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode
public class Receipt implements Reference {

  public Long deliveryNumber;
}
