package com.walmart.move.nim.receiving.reporting.model;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReportDeliveryDocument {

  private List<ReportDeliveryDocumentLine> deliveryDocumentLines;
}
