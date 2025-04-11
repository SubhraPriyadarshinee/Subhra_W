package com.walmart.move.nim.receiving.rdc.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MirageExceptionRequest {
  private List<String> groupNbr;
  private String lpn;
  private String aclErrorString;
  private String itemNbr;
  private String printerNbr;
  private String tokenId;
  private String containerLabel;
  private String upc;
}
