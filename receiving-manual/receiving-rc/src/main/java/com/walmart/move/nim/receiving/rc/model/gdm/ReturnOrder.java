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
public class ReturnOrder {
  private String roNumber;
  private String orderType;
  private String tenantId;
  private Channel channel;
  private List<ReturnOrderLine> lines;
}
