package com.walmart.move.nim.receiving.core.model.gdm;

import com.walmart.move.nim.receiving.core.model.GdmTimeCriteria;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GdmDeliverySearchRequest {
  private String loadNumber;
  private String status;
  private List<GdmTimeCriteria> timeCriteria;
  private String trailerNumber;
  private String palletNumber;
  private String identifier;
}
