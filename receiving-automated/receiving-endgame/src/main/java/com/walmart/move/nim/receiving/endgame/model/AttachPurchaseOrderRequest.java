package com.walmart.move.nim.receiving.endgame.model;

import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AttachPurchaseOrderRequest {
  @NotNull private Long deliveryNumber;
  @NotNull private List<String> poNumbers;
}
