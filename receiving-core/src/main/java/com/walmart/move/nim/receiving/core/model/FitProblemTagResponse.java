package com.walmart.move.nim.receiving.core.model;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class FitProblemTagResponse {
  private String id;
  private String label;
  private String slot;
  private String status;
  private Integer remainingQty;
  private Integer reportedQty;
  private Issue issue;
  private List<Resolution> resolutions;
}
