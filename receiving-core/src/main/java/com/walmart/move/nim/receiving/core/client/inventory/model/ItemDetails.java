package com.walmart.move.nim.receiving.core.client.inventory.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemDetails {
  private String itemIdentifierType;
  private String itemIdentifierValue;
  private String baseDivisionCode;
  private String financialReportingGroup;
}
