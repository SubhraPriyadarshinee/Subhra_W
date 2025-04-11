package com.walmart.move.nim.receiving.witron.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.GET_PTAG_ERROR_CODE;
import static com.walmart.move.nim.receiving.witron.constants.GdcConstants.GLS;

import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.helper.ProblemReceivingHelper;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.service.ProblemServiceFixit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GdcFixitProblemService extends ProblemServiceFixit {
  private static final Logger log = LoggerFactory.getLogger(GdcFixitProblemService.class);

  @Autowired private ProblemReceivingHelper problemReceivingHelper;

  @Transactional
  public ProblemTagResponse txGetProblemTagInfo(String problemTag, HttpHeaders headers)
      throws ReceivingException {
    log.info("GDC implementation of txGetProblemTagInfo for problemTag: {}", problemTag);

    // Get problem details from FIXIT
    FitProblemTagResponse fitProblemTagResponse = getProblemDetails(problemTag);
    if (!problemReceivingHelper.isContainerReceivable(fitProblemTagResponse)) {
      log.error("Problem is not ready to receive. PTAG:{}", problemTag);
      throw new ReceivingException(
          ReceivingException.PTAG_NOT_READY_TO_RECEIVE,
          HttpStatus.CONFLICT,
          GET_PTAG_ERROR_CODE,
          null,
          null,
          ExceptionCodes.PTAG_NOT_READY_TO_RECEIVE,
          null);
    }

    Issue issue = fitProblemTagResponse.getIssue();
    Resolution resolution = fitProblemTagResponse.getResolutions().get(0);
    String poNbr = resolution.getResolutionPoNbr();
    Integer poLineNbr = resolution.getResolutionPoLineNbr();

    // Get poline details from GDM
    GdmPOLineResponse gdmPOLineResponse =
        deliveryService.getPOLineInfoFromGDM(issue.getDeliveryNumber(), poNbr, poLineNbr, headers);
    DeliveryDocumentLine deliveryDocumentLine =
        gdmPOLineResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);

    // Block the rejected line
    if (InstructionUtils.isRejectedPOLine(deliveryDocumentLine)) {
      log.error(ReceivingException.PTAG_RESOLVED_BUT_LINE_REJECTED);
      throw new ReceivingException(
          ReceivingException.PTAG_RESOLVED_BUT_LINE_REJECTED,
          HttpStatus.CONFLICT,
          GET_PTAG_ERROR_CODE,
          null,
          null,
          ExceptionCodes.PTAG_RESOLVED_BUT_LINE_REJECTED,
          null);
    }

    // Block the GLS delivery
    if (GLS.equalsIgnoreCase(gdmPOLineResponse.getDeliveryOwnership())) {
      log.error(ReceivingException.GLS_DELIVERY_ERROR_MSG);
      throw new ReceivingBadDataException(
          ExceptionCodes.GLS_DELIVERY_ERROR, ReceivingException.GLS_DELIVERY_ERROR_MSG);
    }

    // Get received quantity
    Long pTagReceivedQty = receiptService.getReceivedQtyByProblemIdInVnpk(problemTag);
    long poLineReceivedQty = receiptService.getReceivedQtyByPoAndPoLine(poNbr, poLineNbr);

    // Calculate open quantity
    Integer totalOrderQty = deliveryDocumentLine.getTotalOrderQty();
    int openQty = Math.max((totalOrderQty - (int) poLineReceivedQty), 0);
    deliveryDocumentLine.setOpenQty(openQty);
    deliveryDocumentLine.setQuantity(totalOrderQty);

    log.info(
        "problemTag:{} poNbr:{} poLineNbr:{} poLineReceivedQty:{} pTagReceivedQty:{} totalOrderQty:{} openQty:{}",
        problemTag,
        poNbr,
        poLineNbr,
        poLineReceivedQty,
        pTagReceivedQty,
        totalOrderQty,
        openQty);

    // Persist the problem label
    saveProblemLabel(
        Long.parseLong(issue.getDeliveryNumber()),
        problemTag,
        issue.getId(),
        resolution.getId(),
        fitProblemTagResponse);

    return getConsolidatedResponse(fitProblemTagResponse, gdmPOLineResponse, pTagReceivedQty);
  }
}
