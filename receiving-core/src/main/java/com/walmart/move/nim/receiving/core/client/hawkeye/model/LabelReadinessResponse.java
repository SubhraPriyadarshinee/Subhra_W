package com.walmart.move.nim.receiving.core.client.hawkeye.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabelReadinessResponse {
  private String groupNumber;
  private String groupType;
  private String locationId;
  private String status;
  private String path;
  private String code;
  private String inboundTagId;
  private List<Error> errors;
}
