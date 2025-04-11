package com.walmart.move.nim.receiving.mfc.service.problem;

import com.walmart.move.nim.receiving.core.entity.ProblemLabel;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Shipment;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.mfc.common.ProblemType;
import com.walmart.move.nim.receiving.mfc.model.problem.CreateExceptionResponse;

public interface ProblemRegistrationService {

  CreateExceptionResponse createProblem(
      Shipment shipment, ContainerDTO containerDTO, ProblemType problemType);

  void closeProblem(ProblemLabel problemLabel, ProblemType problemType);
}
