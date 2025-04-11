package com.walmart.move.nim.receiving.core.model.fixit;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ProblemReponse {

  private String deliveryId;

  private List<ProblemFreightSolution> problemFreightSolution;
}
