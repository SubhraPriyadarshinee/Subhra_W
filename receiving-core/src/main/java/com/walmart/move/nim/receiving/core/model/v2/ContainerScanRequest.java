package com.walmart.move.nim.receiving.core.model.v2;

import com.walmart.move.nim.receiving.core.common.OverageType;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ASNDocument;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ContainerScanRequest {

  @NotNull private String trackingId;
  private String loadNumber;
  private String trailerNumber;
  @NotNull private Long deliveryNumber;
  private ASNDocument asnDocument;
  private OverageType overageType;
  private Long originalDeliveryNumber;
  private Map<String, Object> miscInfo;
}
