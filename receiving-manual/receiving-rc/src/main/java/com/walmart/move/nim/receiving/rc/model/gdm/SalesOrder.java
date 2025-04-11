package com.walmart.move.nim.receiving.rc.model.gdm;

import java.util.List;
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
public class SalesOrder {
  private String soNumber;
  private String tenantId;
  private List<ReturnOrder> returnOrders;
  private List<SalesOrderLine> lines;
}
