package com.walmart.move.nim.receiving.core.model.decant;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DecantItemRequest {
  private String type;
  private String source;
  private String facilityNumber;
  private List<String> responseGroup;
  private List<String> ids;
}
