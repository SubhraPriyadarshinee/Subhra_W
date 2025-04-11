package com.walmart.move.nim.receiving.rc.repositories;

import com.walmart.move.nim.receiving.rc.entity.ProductCategoryGroup;
import com.walmart.move.nim.receiving.rc.entity.ProductCategoryGroupPK;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ProductCategoryGroupRepository
    extends JpaRepository<ProductCategoryGroup, ProductCategoryGroupPK>,
        JpaSpecificationExecutor<ProductCategoryGroup> {
  /**
   * Get product category group details based on product type value.
   *
   * @param productType product type value
   * @return product category group row
   */
  @Query(
      value =
          "SELECT top(1) * FROM PRODUCT_CATEGORY_GROUP pcg where pcg.PRODUCT_TYPE = :productType ORDER by LAST_CHANGED_TS DESC",
      nativeQuery = true)
  ProductCategoryGroup getProductCategoryGroupByProductType(String productType);
}
