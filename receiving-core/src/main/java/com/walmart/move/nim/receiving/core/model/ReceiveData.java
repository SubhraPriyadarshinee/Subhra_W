package com.walmart.move.nim.receiving.core.model;

import lombok.*;

@Data
@Getter
@Builder
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReceiveData {
  private String eventType;
  private String itemNumber;
  private String itemDescription;
  private int qty;
  private int tiQty;
  private int hiQty;
  private String rejectReason;
  private String disposition;
  private String claimType;
  private String containerId;
}
