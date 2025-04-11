package com.walmart.move.nim.receiving.rc.transformer;

import com.walmart.move.nim.receiving.rc.entity.ProductCategoryGroup;
import com.walmart.move.nim.receiving.rc.model.dto.response.RcProductCategoryGroupResponse;

public class ProductCategoryGroupTransformer {

  /**
   * Transform a receiving product category group entity to a response DTO
   *
   * @param productCategoryGroup entity
   * @return response DTO
   */
  public RcProductCategoryGroupResponse transformProductCategoryGroupEntityToDTO(
      ProductCategoryGroup productCategoryGroup) {
    return RcProductCategoryGroupResponse.builder()
        .l0(productCategoryGroup.getId().getL0())
        .l1(productCategoryGroup.getId().getL1())
        .l2(productCategoryGroup.getId().getL2())
        .l3(productCategoryGroup.getId().getL3())
        .productType(productCategoryGroup.getId().getProductType())
        .group(productCategoryGroup.getGroup())
        .createUser(productCategoryGroup.getCreateUser())
        .createTs(productCategoryGroup.getCreateTs())
        .lastChangedUser(productCategoryGroup.getLastChangedUser())
        .lastChangedTs(productCategoryGroup.getLastChangedTs())
        .build();
  }
}
