package com.walmart.move.nim.receiving.core.model;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

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
public class ItemCatalogUpdateRequest {

  @NotEmpty(message = "deliveryNumber cannot be empty")
  @Digits(message = "deliveryNumber should be a number", integer = 20, fraction = 0)
  private String deliveryNumber;

  @NotNull(message = "itemNumber cannot be empty")
  private Long itemNumber;

  @NotNull(message = "locationId cannot be empty")
  private String locationId;

  private String oldItemUPC;

  @NotEmpty(message = "newItemUPC cannot be empty")
  private String newItemUPC;

  private boolean itemInfoHandKeyed;

  private String vendorStockNumber;

  private String vendorNumber;

  @JsonUnwrapped private LocationInfo locationInfo;
}
