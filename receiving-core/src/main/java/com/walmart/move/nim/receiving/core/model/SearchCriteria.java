package com.walmart.move.nim.receiving.core.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SearchCriteria {

  private String financialReportingGroup;
  private String baseDivisionCode;
  private List<String> trackingIds;
}
