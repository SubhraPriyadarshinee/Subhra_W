package com.walmart.move.nim.receiving.sib.model.ei;

import java.util.Date;
import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class LineItem {
  private ItemIdentifier itemIdentifier;
  private Double quantity;
  private String quantityType;
  private String uom;
  private Date expiryDate;
  private SourceLocation sourceLocation;
  private DestinationLocation destinationLocation;
  private String eventCode;
  private Long deptNbr;
  private Long warehousePackQuantity;
  private String invoiceNbr;

  private transient LineMetaInfo lineMetaInfo;
}
