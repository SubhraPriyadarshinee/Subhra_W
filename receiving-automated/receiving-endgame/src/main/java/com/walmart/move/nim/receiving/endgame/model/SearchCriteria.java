package com.walmart.move.nim.receiving.endgame.model;

import java.util.List;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class SearchCriteria {
  private Long deliveryNumber;
  private String itemNumber;
  private String itemUPC;
  private String caseUPC;
  private String financialReportingGroup;
  private String baseDivisionCode;
  private List<String> trackingIds;
  private String trackingId;
}
