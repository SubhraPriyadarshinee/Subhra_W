package com.walmart.move.nim.receiving.core.model.gdm.v3;

import java.util.List;
import javax.validation.constraints.NotEmpty;
import lombok.*;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsolidatedDelivery {
  private Long deliveryNumber;
  @NotEmpty private List<ConsolidatedPurchaseOrder> purchaseOrders;
  private StatusInformation statusInformation;
}
