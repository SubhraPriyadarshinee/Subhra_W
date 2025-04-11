package com.walmart.move.nim.receiving.core.model.fixit;

import lombok.AllArgsConstructor;
import lombok.Builder;

@Builder
@AllArgsConstructor
public class ReportProblemRequest {

  private String errorMessage;
  private UserInfo userInfo;
}
