package com.walmart.move.nim.receiving.reporting.model;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReportDeliveryDetails {
  private String deliveryStatus;
  private String doorNumber;
  private List<ReportDeliveryDocument> deliveryDocuments;
}
