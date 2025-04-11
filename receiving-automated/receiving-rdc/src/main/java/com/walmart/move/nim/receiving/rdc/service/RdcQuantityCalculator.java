package com.walmart.move.nim.receiving.rdc.service;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.ProblemLabel;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.FitProblemTagResponse;
import com.walmart.move.nim.receiving.core.repositories.ProblemRepository;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** @author v0k00fe */
@Component
public class RdcQuantityCalculator {
  private static final Logger LOG = LoggerFactory.getLogger(RdcQuantityCalculator.class);
  @Autowired private ProblemRepository problemServiceFixit;
  @Autowired private Gson gson;
  @Autowired private InstructionPersisterService instructionPersisterService;

  private int getProjectedReceiveQtyByTiHi(
      DeliveryDocumentLine deliveryDocumentLine,
      Long pendingInstructionsCumulativeProjectedReceivedQty,
      Instruction instruction4mDB) {
    final int palletTi = deliveryDocumentLine.getPalletTie();
    final int palletHi = deliveryDocumentLine.getPalletHigh();
    final int maxReceivedQty =
        Objects.isNull(deliveryDocumentLine.getAutoPoSelectionOverageIncluded())
                || deliveryDocumentLine.getAutoPoSelectionOverageIncluded()
            ? deliveryDocumentLine.getTotalOrderQty() + deliveryDocumentLine.getOverageQtyLimit()
            : deliveryDocumentLine.getTotalOrderQty();
    int quantityCanBeReceived;
    int tiHiProjectedQty = 0;
    if (palletTi > 0 && palletHi > 0) {
      tiHiProjectedQty = palletTi * palletHi;
    }
    if (Objects.nonNull(instruction4mDB.getProblemTagId())) {
      quantityCanBeReceived =
          deliveryDocumentLine.getOpenQty() < tiHiProjectedQty
              ? deliveryDocumentLine.getOpenQty()
              : tiHiProjectedQty;
      LOG.info(
          "Projected instruction quantity for problemTagId:{} is {}",
          instruction4mDB.getProblemTagId(),
          quantityCanBeReceived);
      ProblemLabel problemLabel =
          problemServiceFixit.findProblemLabelByProblemTagId(instruction4mDB.getProblemTagId());
      FitProblemTagResponse fitProblemTagResponse =
          gson.fromJson(problemLabel.getProblemResponse(), FitProblemTagResponse.class);
      Integer resolutionQty = fitProblemTagResponse.getResolutions().get(0).getQuantity();
      Integer totalReceivedProblemQty =
          instructionPersisterService
              .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
                  instruction4mDB.getPurchaseReferenceNumber(),
                  instruction4mDB.getPurchaseReferenceLineNumber(),
                  instruction4mDB.getProblemTagId())
              .intValue();
      // override total order qty & total receive qty as specific to problem receiving
      deliveryDocumentLine.setTotalOrderQty(resolutionQty);
      deliveryDocumentLine.setTotalReceivedQty(totalReceivedProblemQty);
      return quantityCanBeReceived;
    }
    // in case of creating new instruction
    if (instruction4mDB.getProjectedReceiveQty() == 0) {
      quantityCanBeReceived =
          (deliveryDocumentLine.getTotalReceivedQty() == 0)
              ? maxReceivedQty - pendingInstructionsCumulativeProjectedReceivedQty.intValue()
              : maxReceivedQty - deliveryDocumentLine.getTotalReceivedQty();
    } else {
      if (deliveryDocumentLine.getTotalReceivedQty() == 0) {
        quantityCanBeReceived =
            tiHiProjectedQty < maxReceivedQty ? tiHiProjectedQty : maxReceivedQty;
      } else {
        quantityCanBeReceived = maxReceivedQty - deliveryDocumentLine.getTotalReceivedQty();
      }
    }
    final int projectedReceiveQty =
        (tiHiProjectedQty > 0 && tiHiProjectedQty < quantityCanBeReceived)
            ? tiHiProjectedQty
            : quantityCanBeReceived;
    return projectedReceiveQty;
  }

  private int getProjectedReceiveQtyByTiHiForSSCCReceiving(
      DeliveryDocumentLine deliveryDocumentLine,
      Long pendingInstructionsCumulativeProjectedReceivedQty,
      Instruction instruction4mDB) {
    final int palletTi = deliveryDocumentLine.getPalletTie();
    final int palletHi = deliveryDocumentLine.getPalletHigh();
    final int maxReceivedQtyByPO =
        Objects.isNull(deliveryDocumentLine.getAutoPoSelectionOverageIncluded())
                || deliveryDocumentLine.getAutoPoSelectionOverageIncluded()
            ? deliveryDocumentLine.getTotalOrderQty() + deliveryDocumentLine.getOverageQtyLimit()
            : deliveryDocumentLine.getTotalOrderQty();
    int shipmentQty =
        ReceivingUtils.conversionToVendorPack(
            deliveryDocumentLine.getShippedQty(),
            deliveryDocumentLine.getShippedQtyUom(),
            deliveryDocumentLine.getVendorPack(),
            deliveryDocumentLine.getWarehousePack());

    final int maxReceivedQtyByASN = shipmentQty;
    int quantityCanBeReceived;
    int tiHiProjectedQty = 0;
    if (palletTi > 0 && palletHi > 0) {
      tiHiProjectedQty = palletTi * palletHi;
    }
    if (Objects.nonNull(instruction4mDB.getProblemTagId())) {
      quantityCanBeReceived =
          deliveryDocumentLine.getOpenQty() < tiHiProjectedQty
              ? deliveryDocumentLine.getOpenQty()
              : tiHiProjectedQty;
      LOG.info(
          "Projected instruction quantity for problemTagId:{} is {}",
          instruction4mDB.getProblemTagId(),
          quantityCanBeReceived);
      ProblemLabel problemLabel =
          problemServiceFixit.findProblemLabelByProblemTagId(instruction4mDB.getProblemTagId());
      FitProblemTagResponse fitProblemTagResponse =
          gson.fromJson(problemLabel.getProblemResponse(), FitProblemTagResponse.class);
      Integer resolutionQty = fitProblemTagResponse.getResolutions().get(0).getQuantity();
      Integer totalReceivedProblemQty =
          instructionPersisterService
              .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
                  instruction4mDB.getPurchaseReferenceNumber(),
                  instruction4mDB.getPurchaseReferenceLineNumber(),
                  instruction4mDB.getProblemTagId())
              .intValue();
      // override total order qty & total receive qty as specific to problem receiving
      deliveryDocumentLine.setTotalOrderQty(resolutionQty);
      deliveryDocumentLine.setTotalReceivedQty(totalReceivedProblemQty);
      return quantityCanBeReceived;
    }
    // in case of creating new instruction
    if (instruction4mDB.getProjectedReceiveQty() == 0) {
      quantityCanBeReceived =
          (deliveryDocumentLine.getTotalReceivedQty() == 0)
              ? maxReceivedQtyByPO - pendingInstructionsCumulativeProjectedReceivedQty.intValue()
              : maxReceivedQtyByPO - deliveryDocumentLine.getTotalReceivedQty();
    } else {
      quantityCanBeReceived = maxReceivedQtyByPO - deliveryDocumentLine.getTotalReceivedQty();
    }

    return Math.min(Math.min(quantityCanBeReceived, maxReceivedQtyByASN), tiHiProjectedQty);
  }

  public int getProjectedReceiveQtyByTiHiBasedOnScanType(
      DeliveryDocumentLine deliveryDocumentLine,
      Long pendingInstructionsCumulativeProjectedReceivedQty,
      Instruction instruction4mDB) {
    int projectedReceivedQty;
    String errorMessage;
    if (StringUtils.isNotBlank(instruction4mDB.getSsccNumber())) {
      projectedReceivedQty =
          getProjectedReceiveQtyByTiHiForSSCCReceiving(
              deliveryDocumentLine,
              pendingInstructionsCumulativeProjectedReceivedQty,
              instruction4mDB);
      errorMessage =
          String.format(
              ReceivingException.PO_POLINE_NOT_FOUND,
              instruction4mDB.getSsccNumber(),
              instruction4mDB.getDeliveryNumber());
    } else {
      projectedReceivedQty =
          getProjectedReceiveQtyByTiHi(
              deliveryDocumentLine,
              pendingInstructionsCumulativeProjectedReceivedQty,
              instruction4mDB);
      errorMessage =
          String.format(
              ReceivingException.PO_POLINE_NOT_FOUND,
              instruction4mDB.getGtin(),
              instruction4mDB.getDeliveryNumber());
    }
    if (projectedReceivedQty <= 0) {
      throw new ReceivingDataNotFoundException(ExceptionCodes.PO_LINE_NOT_FOUND, errorMessage);
    }
    return projectedReceivedQty;
  }
}
