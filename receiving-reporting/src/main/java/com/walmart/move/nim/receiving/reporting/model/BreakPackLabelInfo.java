package com.walmart.move.nim.receiving.reporting.model;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BreakPackLabelInfo {

  private String inductLabelId;
  private List<BreakPackChildLabelInfo> breakPackChildLabelInfo;
  private String backOutDate;
  private String backOutTimeStamp;
  private String userId;
}
