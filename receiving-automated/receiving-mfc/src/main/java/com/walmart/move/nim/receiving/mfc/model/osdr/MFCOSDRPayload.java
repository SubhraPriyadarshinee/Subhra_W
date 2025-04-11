package com.walmart.move.nim.receiving.mfc.model.osdr;

import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MFCOSDRPayload {
  private Long deliveryNumber;
  private Set<String> asnNumber;
  private List<MFCOSDRContainer> containers;
  private String errorMessage;
  private String exceptionCode;
}
