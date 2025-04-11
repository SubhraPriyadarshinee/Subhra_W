package com.walmart.move.nim.receiving.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class POLineOSDR extends OSDR {

  private String damageReasonCode;
  private String damageClaimType;
  private String overageReasonCode;
  private String rejectedReasonCode;
  private String shortageReasonCode;
  private String rejectedComment;
}
