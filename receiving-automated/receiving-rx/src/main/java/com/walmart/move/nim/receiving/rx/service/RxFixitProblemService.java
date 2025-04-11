package com.walmart.move.nim.receiving.rx.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.ProblemLabel;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.repositories.ProblemRepository;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.core.service.ProblemService;
import com.walmart.move.nim.receiving.core.service.ProblemServiceFixit;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component(RxConstants.RX_PROBLEM_FIXIT_SERVICE)
public class RxFixitProblemService extends ProblemServiceFixit {

  private static final Logger log = LoggerFactory.getLogger(RxFixitProblemService.class);

  @Autowired private ProblemRepository problemRepository;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private InstructionPersisterService instructionPersisterService;

  protected long receivedQtyByPoAndPoLine(
      Resolution resolution, DeliveryDocumentLine deliveryDocumentLine) {
    return Long.valueOf(
        ReceivingUtils.conversionToVendorPack(
            receiptService
                .getReceivedQtyByPoAndPoLineInEach(
                    resolution.getResolutionPoNbr(), resolution.getResolutionPoLineNbr())
                .intValue(),
            ReceivingConstants.Uom.EACHES,
            deliveryDocumentLine.getVendorPack(),
            deliveryDocumentLine.getWarehousePack()));
  }

  public void completeProblem(
      Instruction instruction, HttpHeaders httpHeaders, DeliveryDocumentLine deliveryDocumentLine)
      throws ReceivingException {

    // removing host header as fixit k8 lb is not routing properly if the host header is present
    httpHeaders.remove(ReceivingConstants.HOST);

    ProblemService configuredProblemService =
        tenantSpecificConfigReader.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.PROBLEM_SERVICE,
            ProblemService.class);

    String problemTagId = instruction.getProblemTagId();
    ProblemLabel problemLabelByProblemTagId =
        configuredProblemService.findProblemLabelByProblemTagId(problemTagId);
    FitProblemTagResponse fitProblemTagResponse =
        gson.fromJson(problemLabelByProblemTagId.getProblemResponse(), FitProblemTagResponse.class);

    Problem problem = new Problem();
    problem.setProblemTagId(problemTagId);
    problem.setDeliveryNumber(String.valueOf(instruction.getDeliveryNumber()));
    problem.setIssueId(problemLabelByProblemTagId.getIssueId());
    problem.setResolutionId(problemLabelByProblemTagId.getResolutionId());
    problem.setResolutionQty(fitProblemTagResponse.getResolutions().get(0).getQuantity());

    int currentInstructionRcvQty =
        ReceivingUtils.conversionToVendorPackRoundUp(
            instruction.getReceivedQuantity(),
            instruction.getReceivedQuantityUOM(),
            deliveryDocumentLine.getVendorPack(),
            deliveryDocumentLine.getWarehousePack());

    configuredProblemService.notifyCompleteProblemTag(
        problemTagId, problem, Long.valueOf(currentInstructionRcvQty));

    Long rcvQtyByCompletedInstructions =
        instructionPersisterService
            .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
                deliveryDocumentLine.getPurchaseReferenceNumber(),
                deliveryDocumentLine.getPurchaseReferenceLineNumber(),
                problemTagId);

    int rcvQtyByCompletedInstructionsInZA =
        ReceivingUtils.conversionToVendorPackRoundUp(
            rcvQtyByCompletedInstructions.intValue(),
            instruction.getReceivedQuantityUOM(),
            deliveryDocumentLine.getVendorPack(),
            deliveryDocumentLine.getWarehousePack());
    int totalRcvQtySoFar = rcvQtyByCompletedInstructionsInZA + currentInstructionRcvQty;
    if (totalRcvQtySoFar == problem.getResolutionQty()) {
      log.info(
          "Successfully received all the resolution quantity: {} for problem label: {}, so deleting label from Problem table",
          problem.getResolutionQty(),
          problemTagId);
      deleteProblemLabel(problemLabelByProblemTagId);
    }
  }
}
