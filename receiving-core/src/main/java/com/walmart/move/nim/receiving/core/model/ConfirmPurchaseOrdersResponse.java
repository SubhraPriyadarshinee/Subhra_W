package com.walmart.move.nim.receiving.core.model;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ConfirmPurchaseOrdersResponse {
  private List<ConfirmPurchaseOrdersError> errors;
}
