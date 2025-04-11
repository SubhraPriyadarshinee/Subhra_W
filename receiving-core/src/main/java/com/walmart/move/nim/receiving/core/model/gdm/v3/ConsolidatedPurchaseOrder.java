package com.walmart.move.nim.receiving.core.model.gdm.v3;

import java.util.List;
import javax.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ConsolidatedPurchaseOrder {
  @NotEmpty private String poNumber;
  @NotEmpty private List<ConsolidatedPurchaseOrderLine> lines;
  private String sellerId;
}
