package com.walmart.move.nim.receiving.rc.model.gdm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesOrderLine {
  private Integer lineNumber;
  private Integer lineId;
  private Seller seller;
  private String channel;
  private String trackingNumber;
  private String poNumber;
  private Ordered ordered;
  private GDMItemDetails itemDetails;
}
