package com.walmart.move.nim.receiving.core.model.osdr.v2;

import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OSDRPayload {
  private Summary summary;
  private String eventType;
  private String userId;
  private Date ts;
  private Long deliveryNumber;
}
