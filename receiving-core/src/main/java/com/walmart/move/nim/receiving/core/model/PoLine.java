package com.walmart.move.nim.receiving.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PoLine {
  private Integer lineNum;
  private Integer receiveQty;
  private String receiveQtyUOM;
  private Integer rejectQty;
  private String rejectQtyUOM;
  private String rejectReasonCode;
  private Integer damageQty;
  private String damageQtyUOM;
  private String damageReasonCode;
  private String damageClaimType;
  private String errorCode;
  private String errorMessage;
}
