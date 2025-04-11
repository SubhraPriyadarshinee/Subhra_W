package com.walmart.move.nim.receiving.core.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SaveConfirmationRequest {
  private Long itemNumber;
  private Long deliveryNumber;
  private List<String> originCountryCode;
  private Integer vnpkQty;
  private Integer whpkQty;
  private Boolean isOriginCountryCodeAcknowledged;
  private Boolean isOriginCountryCodeConditionalAcknowledged;
  private Boolean isPackTypeAcknowledged;
}
