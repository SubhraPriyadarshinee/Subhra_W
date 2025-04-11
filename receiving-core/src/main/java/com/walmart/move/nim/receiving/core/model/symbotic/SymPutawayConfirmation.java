package com.walmart.move.nim.receiving.core.model.symbotic;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SymPutawayConfirmation {
  private String trackingId;
  private String status;
  private String quantityUOM;
  private Integer quantity;
  private List<SymErrorDetail> errorDetails;
}
