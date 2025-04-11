package com.walmart.move.nim.receiving.rdc.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.service.ProblemServiceFixit;
import com.walmart.move.nim.receiving.rdc.utils.RdcProblemUtils;
import io.strati.metrics.annotation.Counted;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;

public class RdcFixitProblemService extends ProblemServiceFixit {

  @Autowired private RdcProblemUtils rdcProblemUtils;

  @Override
  public long receivedQtyByPoAndPoLine(
      Resolution resolution, DeliveryDocumentLine deliveryDocumentLine) throws ReceivingException {
    return rdcProblemUtils.receivedQtyByPoAndPoLine(resolution, deliveryDocumentLine);
  }

  @Counted(
      name = "scanProblemTagCount",
      level1 = "uwms-receiving",
      level2 = "RdcFixitProblemService",
      level3 = "txGetProblemTagInfo")
  @Timed(
      name = "scanProblemTagAPITimed",
      level1 = "uwms-receiving",
      level2 = "RdcFixitProblemService",
      level3 = "txGetProblemTagInfo")
  @ExceptionCounted(
      name = "scanProblemTagAPIExceptionCount",
      level1 = "uwms-receiving",
      level2 = "RdcFixitProblemService",
      level3 = "txGetProblemTagInfo")
  @Override
  @Transactional
  public ProblemTagResponse txGetProblemTagInfo(String problemTag, HttpHeaders headers)
      throws ReceivingException {
    FitProblemTagResponse fitProblemTagResponse = getProblemDetails(problemTag);
    return rdcProblemUtils.txGetProblemTagInfo(fitProblemTagResponse, problemTag, headers);
  }
}
