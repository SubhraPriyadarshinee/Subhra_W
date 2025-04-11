package com.walmart.move.nim.receiving.core.model;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * ContainerUpdateRequest
 *
 * @author vn50o7n
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
public class ContainerUpdateRequest {

  /**
   * For given LPN's adjustQuantity is the 'new' Quantity to be adjusted to. Though variable name
   * says 'adjust' its actually 'new' Quantity
   */
  @NotNull
  @Min(value = 0, message = ReceivingException.ADJUST_PALLET_QUANTITY_INVALID)
  private Integer adjustQuantity;

  /** UOM of the adjustQuantity */
  private String adjustQuantityUOM;

  /**
   * inventoryQuantity is the actual quantity currently we have that accounts for any adjustments
   * like damages etc have been absorbed in inventory.
   */
  private Integer inventoryQuantity;

  /**
   * No need of printer id so making it optional as business expecting because quanity is no more
   * printed so no need of new label
   */
  private Integer printerId;

  /** isInventoryReceivingCorrection to identify if the request initiated from Inventory */
  private boolean isInventoryReceivingCorrection = false;
}
