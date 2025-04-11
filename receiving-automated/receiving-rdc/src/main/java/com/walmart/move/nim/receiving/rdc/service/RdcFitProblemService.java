package com.walmart.move.nim.receiving.rdc.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.service.ProblemService;
import com.walmart.move.nim.receiving.rdc.utils.RdcProblemUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;

public class RdcFitProblemService extends ProblemService {
  @Autowired private RdcProblemUtils rdcProblemUtils;

  @Override
  public long receivedQtyByPoAndPoLine(
      Resolution resolution, DeliveryDocumentLine deliveryDocumentLine) throws ReceivingException {
    return rdcProblemUtils.receivedQtyByPoAndPoLine(resolution, deliveryDocumentLine);
  }

  @Override
  @Transactional
  public ProblemTagResponse txGetProblemTagInfo(String problemTag, HttpHeaders headers)
      throws ReceivingException {
    FitProblemTagResponse fitProblemTagResponse = getProblemDetails(problemTag);
    return rdcProblemUtils.txGetProblemTagInfo(fitProblemTagResponse, problemTag, headers);
  }
}
