package com.walmart.move.nim.receiving.core.model;

import java.util.Date;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ReprintLabelRequest {

  private Long deliveryNumber;
  private Date fromDate;
  private Date toDate;
  private String trackingId;
  private List<String> trackingIds;
}
