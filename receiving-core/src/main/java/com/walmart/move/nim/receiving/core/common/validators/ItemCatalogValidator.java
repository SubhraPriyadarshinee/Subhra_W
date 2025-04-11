package com.walmart.move.nim.receiving.core.common.validators;

import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.model.ItemCatalogUpdateRequest;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;

public class ItemCatalogValidator {
  public static void validateCatalogUPC(ItemCatalogUpdateRequest itemCatalogUpdateRequest) {
    if (itemCatalogUpdateRequest.getOldItemUPC().equals(itemCatalogUpdateRequest.getNewItemUPC())) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_CATALOG_UPC_REQUEST,
          String.format(
              ReceivingConstants.INVALID_ITEM_CATALOG_REQUEST,
              itemCatalogUpdateRequest.getNewItemUPC(),
              itemCatalogUpdateRequest.getOldItemUPC(),
              itemCatalogUpdateRequest.getItemNumber()));
    }
  }
}
