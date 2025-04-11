package com.walmart.move.nim.receiving.rc.model.dto.response;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Builder
@ToString
@EqualsAndHashCode
public class RcProductCategoryGroupImportStatsResponse<T> {
  private long totalProductCategoryGroupCount;
  private long insertedProductCategoryGroupCount;
  private long updatedProductCategoryGroupCount;
  private long failedProductCategoryGroupCount;
}
