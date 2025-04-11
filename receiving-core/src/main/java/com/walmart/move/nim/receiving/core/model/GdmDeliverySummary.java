package com.walmart.move.nim.receiving.core.model;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GdmDeliverySummary {
  private Long deliveryNumber;
  private String loadNumber;
  private String trailerId;
  private String scheduled;
  private String arrivalTimeStamp;
  private List<String> shipmentNumbers;
  private List<String> shipmentDocumentIds;
  private String status;
}
