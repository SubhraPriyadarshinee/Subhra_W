package com.walmart.move.nim.receiving.mfc.processor;

import com.walmart.move.nim.receiving.core.entity.ProblemLabel;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Shipment;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.mfc.common.ProblemResolutionType;
import com.walmart.move.nim.receiving.mfc.common.ProblemType;

public interface ProblemHandingProcessor {

  void handleProblemCreation(Shipment shipment, ContainerDTO containerDTO, ProblemType problemType);

  void handleProblemUpdation(
      ProblemLabel problemLabel,
      ProblemType problemType,
      ProblemResolutionType problemResolutionType);
}
