package com.walmart.move.nim.receiving.core.model;

import javax.validation.constraints.Digits;
import javax.validation.constraints.NotEmpty;
import lombok.*;

/**
 * This class is used to map to the item catalog request made by the client
 *
 * @author sks0013
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ItemCatalogDeleteRequest {

  @NotEmpty(message = "deliveryNumber cannot be empty")
  @Digits(message = "deliveryNumber should be a number", integer = 20, fraction = 0)
  private String deliveryNumber;

  @NotEmpty(message = "newItemUPC cannot be empty")
  private String newItemUPC;
}
