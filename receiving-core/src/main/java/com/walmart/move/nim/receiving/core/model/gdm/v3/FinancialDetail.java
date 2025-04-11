package com.walmart.move.nim.receiving.core.model.gdm.v3;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FinancialDetail {
  private Double reportedRetail;
  private Double derivedRetail;
  private Double derivedCost;
  private String reportedCostCurrency;
  private String financialReportingCountry;
}
