package com.walmart.move.nim.receiving.rc.model.dto.request;

import java.util.Map;
import lombok.Data;

@Data
public class RcWorkflowAdditionalAttributes {
  private String doorNbr;
  private String trailerNbr;
  private String sealNbr;
  private String gtin;
  private Map<String, Object> additionalInfo;
}
