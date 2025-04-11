package com.walmart.move.nim.receiving.core.model.gdm;

import com.walmart.move.nim.receiving.core.model.GdmDeliverySummary;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GdmDeliverySearchResponse {
  private List<GdmDeliverySummary> deliveries;
}
