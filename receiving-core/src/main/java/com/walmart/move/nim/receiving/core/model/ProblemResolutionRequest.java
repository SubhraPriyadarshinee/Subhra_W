package com.walmart.move.nim.receiving.core.model;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProblemResolutionRequest {

  private Long receivedQuantity;

  private String receivingUser;

  private List<Resolution> resolutions;
}
