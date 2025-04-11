package com.walmart.move.nim.receiving.rdc.utils;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.GET_PTAG_ERROR_CODE;
import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.PROBLEM_CONFLICT;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingConflictException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.entity.ProblemLabel;
import com.walmart.move.nim.receiving.core.helper.ProblemReceivingHelper;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.fixit.ReportProblemRequest;
import com.walmart.move.nim.receiving.core.model.fixit.UserInfo;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.core.service.ProblemService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.service.NimRdsService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Objects;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** @author s0g015w */
@Component
public class RdcProblemUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(RdcProblemUtils.class);

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private RdcInstructionUtils rdcInstructionUtils;
  @Autowired private ReceiptService receiptService;
  @Autowired private NimRdsService nimRdsService;
  @Autowired private InstructionPersisterService instructionPersisterService;
  @Autowired private ProblemReceivingHelper problemReceivingHelper;
  @Autowired private Gson gson;

  @Resource(name = ReceivingConstants.DELIVERY_SERVICE)
  protected DeliveryService deliveryService;

  /**
   * This is an utility method that invokes report problem api for reporting problem receiving error
   * to Fixit/FIT
   *
   * @param problemTagId
   * @param issueId
   * @param poNumber
   * @param poLineNbr
   * @param httpHeaders
   * @throws ReceivingException
   */
  public void reportErrorForProblemReceiving(
      String problemTagId,
      String issueId,
      String poNumber,
      Integer poLineNbr,
      HttpHeaders httpHeaders)
      throws ReceivingInternalException, ReceivingException {
    LOGGER.error(
        "Have already received maximum allowable quantity threshold for PO: {}, POL: {} and problemTagId: {}",
        poNumber,
        poLineNbr,
        problemTagId);

    UserInfo userinfo = new UserInfo();
    userinfo.setUserId(httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    String errorMessage =
        String.format(ReceivingException.MAX_QUANTITY_REACHED_ERROR_FOR_PROBLEM, problemTagId);

    if (Objects.isNull(issueId)) {
      ProblemLabel problemLabel =
          tenantSpecificConfigReader
              .getConfiguredInstance(
                  TenantContext.getFacilityNum().toString(),
                  ReceivingConstants.PROBLEM_SERVICE,
                  ProblemService.class)
              .findProblemLabelByProblemTagId(problemTagId);
      if (Objects.nonNull(problemLabel)) {
        issueId = problemLabel.getIssueId();
      }
    }

    ReportProblemRequest reportProblemRequest =
        ReportProblemRequest.builder().userInfo(userinfo).errorMessage(errorMessage).build();
    tenantSpecificConfigReader
        .getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.PROBLEM_SERVICE,
            ProblemService.class)
        .reportProblem(problemTagId, issueId, reportProblemRequest);
    throw new ReceivingInternalException(
        ExceptionCodes.MAX_QUANTITY_REACHED_FOR_PROBLEM,
        String.format(ReceivingException.MAX_QUANTITY_REACHED_FOR_PROBLEM, problemTagId),
        problemTagId);
  }
  /**
   * Fetches received quantity information for a given PO and PO line. If item which is available in
   * the delivery document line is atlas converted then receipts information will be retrieved from
   * RDS, otherwise fetches from receipts table in atlas receiving DB.
   *
   * @param resolution
   * @param deliveryDocumentLine
   * @return receivedQtyByPoAndPoL
   * @throws ReceivingException
   */
  @TimeTracing(
      component = AppComponent.RDS,
      type = Type.REST,
      executionFlow = "fetch-rcvdQty-for-problem-by-PO-POL")
  public long receivedQtyByPoAndPoLine(
      Resolution resolution, DeliveryDocumentLine deliveryDocumentLine) throws ReceivingException {
    String poNumber = resolution.getResolutionPoNbr();
    Integer poLineNumber = resolution.getResolutionPoLineNbr();
    return rdcInstructionUtils.getReceivedQtyByPoAndPoLine(
        deliveryDocumentLine, poNumber, poLineNumber);
  }

  @Transactional
  public ProblemTagResponse txGetProblemTagInfo(
      FitProblemTagResponse fitProblemTagResponse, String problemTag, HttpHeaders headers)
      throws ReceivingException {
    ProblemTagResponse problemTagResponse = null;
    long totalReceived = 0;
    int maxReceiveQty = 0;
    Issue issue = fitProblemTagResponse.getIssue();
    if (problemReceivingHelper.isContainerReceivable(fitProblemTagResponse)) {
      Resolution resolution = fitProblemTagResponse.getResolutions().get(0);
      GdmPOLineResponse gdmPOLineResponse =
          deliveryService.getPOLineInfoFromGDM(
              issue.getDeliveryNumber(),
              resolution.getResolutionPoNbr(),
              resolution.getResolutionPoLineNbr(),
              headers);

      DeliveryDocumentLine deliveryDocumentLine =
          gdmPOLineResponse.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
      try {
        rdcInstructionUtils.validatePoLineIsCancelledOrClosedOrRejected(deliveryDocumentLine);
      } catch (ReceivingException e) {
        LOGGER.error(
            "ResolutionPoLineNbr: {} of ResolutionPoNbr: {} is cancelled or rejected for problem ResolutionId: {}  and ProblemTagId: {} ",
            resolution.getResolutionPoLineNbr(),
            resolution.getResolutionPoNbr(),
            resolution.getId(),
            problemTag);
        throw new ReceivingConflictException(
            PROBLEM_CONFLICT,
            RdcConstants.PTAG_RESOLVED_BUT_LINE_REJECTED_OR_CANCELLED,
            GET_PTAG_ERROR_CODE,
            problemTag);
      }
      totalReceived = receivedQtyByPoAndPoLine(resolution, deliveryDocumentLine);
      maxReceiveQty =
          deliveryDocumentLine.getTotalOrderQty() + deliveryDocumentLine.getOverageQtyLimit();
      LOGGER.info(
          "ProblemTagId:{} ResolutionPoNbr:{} ResolutionPoLineNbr:{} MaxLimit:{} TotalReceived:{}",
          problemTag,
          resolution.getResolutionPoNbr(),
          resolution.getResolutionPoLineNbr(),
          maxReceiveQty,
          totalReceived);

      // Validate the line max limit
      if (totalReceived >= maxReceiveQty) {
        LOGGER.error(ReceivingException.PTAG_RESOLVED_BUT_LINE_ALREADY_RECEIVED);
        reportErrorForProblemReceiving(
            problemTag,
            issue.getId(),
            resolution.getResolutionPoNbr(),
            resolution.getResolutionPoLineNbr(),
            headers);
        throw new ReceivingConflictException(
            PROBLEM_CONFLICT,
            ReceivingException.PTAG_RESOLVED_BUT_LINE_ALREADY_RECEIVED,
            GET_PTAG_ERROR_CODE,
            problemTag);
      }
      tenantSpecificConfigReader
          .getConfiguredInstance(
              TenantContext.getFacilityNum().toString(),
              ReceivingConstants.PROBLEM_SERVICE,
              ProblemService.class)
          .saveProblemLabel(
              Long.parseLong(issue.getDeliveryNumber()),
              problemTag,
              issue.getId(),
              resolution.getId(),
              fitProblemTagResponse);

      problemTagResponse =
          tenantSpecificConfigReader
              .getConfiguredInstance(
                  TenantContext.getFacilityNum().toString(),
                  ReceivingConstants.PROBLEM_SERVICE,
                  ProblemService.class)
              .getConsolidatedResponse(fitProblemTagResponse, gdmPOLineResponse, null);
    } else {
      LOGGER.error("Problem:[{}] is not ready to receive.", problemTag);
      throw new ReceivingConflictException(
          PROBLEM_CONFLICT,
          ReceivingException.PTAG_NOT_READY_TO_RECEIVE,
          GET_PTAG_ERROR_CODE,
          problemTag);
    }
    return problemTagResponse;
  }
}
