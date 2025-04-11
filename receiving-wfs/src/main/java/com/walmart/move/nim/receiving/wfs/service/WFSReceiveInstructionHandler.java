package com.walmart.move.nim.receiving.wfs.service;

import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.validators.InstructionStateValidator;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.core.service.ReceiveInstructionHandler;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

public class WFSReceiveInstructionHandler implements ReceiveInstructionHandler {
  private static final Logger logger = LoggerFactory.getLogger(WFSReceiveInstructionHandler.class);

  @Autowired protected InstructionPersisterService instructionPersisterService;
  @Autowired private InstructionStateValidator instructionStateValidator;
  @Autowired protected InstructionHelperService instructionHelperService;
  @Autowired private TenantSpecificConfigReader configUtils;
  @Autowired private WFSInstructionService wfsInstructionService;
  @Autowired private WFSInstructionHelperService wfsInstructionHelperservice;

  @Override
  public InstructionResponse receiveInstruction(
      Long instructionId,
      ReceiveInstructionRequest receiveInstructionRequest,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    TenantContext.get().setReceiveInstrStart(System.currentTimeMillis());
    logger.info(
        "Default implementation of receive instruction for instruction request {}",
        receiveInstructionRequest);

    // Get instruction details from DB
    Instruction instruction = instructionPersisterService.getInstructionById(instructionId);
    DeliveryDocumentLine deliveryDocumentLine =
        InstructionUtils.getDeliveryDocumentLine(instruction);

    // Basic sanity check
    if (Objects.isNull(instruction) || Objects.isNull(deliveryDocumentLine)) {
      logger.error("Invalid instructionId: {}", instructionId);
      throw new ReceivingBadDataException(
          ExceptionCodes.RECEIVING_INTERNAL_ERROR,
          ReceivingException.COMPLETE_INSTRUCTION_ERROR_MSG);
    }

    try {
      // Check for valid instruction status
      instructionStateValidator.validate(instruction);
    } catch (ReceivingException re) {
      logger.error(
          "{} {}",
          ReceivingException.COMPLETE_INSTRUCTION_ERROR_MSG,
          ExceptionUtils.getStackTrace(re));
      logger.error("Instruction: {} is already complete", instruction.getId());
      throw new ReceivingBadDataException(
          ExceptionCodes.INSTRUCTION_IS_ALREADY_COMPLETED_WFS,
          ReceivingException.INSTRUCTION_IS_ALREADY_COMPLETED);
    }

    try {
      // As instruction table should be in ZA, convert explicitly whatever quantity passed in to ZA.
      // Doing this after validation
      instruction.setReceivedQuantity(
          ReceivingUtils.conversionToVendorPack(
              receiveInstructionRequest.getQuantity(),
              receiveInstructionRequest.getQuantityUOM(),
              deliveryDocumentLine.getVendorPack(),
              deliveryDocumentLine.getWarehousePack()));
      instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);

      // Retrieve Delivery document lines through instruction
      DeliveryDocument deliveryDocument = InstructionUtils.getDeliveryDocument(instruction);
      List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
      deliveryDocumentLines.add(deliveryDocument.getDeliveryDocumentLines().get(0));
      receiveInstructionRequest.setDeliveryDocumentLines(deliveryDocumentLines);

      String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);

      Map<String, Object> instructionContainerMap =
          wfsInstructionHelperservice.createContainersAndReceiptsForWFSPosRIR(
              receiveInstructionRequest, httpHeaders, userId, instruction);

      instruction = (Instruction) instructionContainerMap.get("instruction");
      Container consolidatedContainer = (Container) instructionContainerMap.get("container");
      String dcTimeZone = configUtils.getDCTimeZone(TenantContext.getFacilityNum());
      InstructionResponseImplNew instructionResponse =
          (InstructionResponseImplNew)
              wfsInstructionHelperservice.prepareWFSInstructionResponse(
                  instruction, consolidatedContainer, dcTimeZone);

      // done null check
      if (Objects.nonNull(instructionResponse.getPrintJob())
          && Objects.nonNull(instructionResponse.getInstruction())
          && Objects.nonNull(instructionResponse.getInstruction().getContainer())
          && Objects.nonNull(
              instructionResponse.getInstruction().getContainer().getCtrDestination())) {
        Map<String, Object> printJob = instructionResponse.getPrintJob();
        List<Map<String, Object>> printRequests =
            (List<Map<String, Object>>) printJob.get("printRequests");
        Map<String, Object> printRequest = printRequests.get(0);
        List<Map<String, Object>> labelData = (List<Map<String, Object>>) printRequest.get("data");
        Map<String, String> fcNumberToFcName =
            wfsInstructionHelperservice.mapFCNumberToFCName(
                instructionResponse.getInstruction().getContainer().getCtrDestination());

        Map<String, Object> fcName = new HashMap<String, Object>();
        fcName.put("key", "FCNAME");
        String fcNameValue = "";
        if (fcNumberToFcName.containsKey(ReceivingConstants.FACILITY_NAME)) {
          fcNameValue = fcNumberToFcName.get(ReceivingConstants.FACILITY_NAME);
        }
        fcName.put("value", fcNameValue);
        labelData.add(fcName);
        printRequests.set(0, printRequest);
        printJob.put("printRequests", printRequests);
        instructionResponse.setPrintJob(printJob);
      }
      instructionHelperService.publishConsolidatedContainer(
          consolidatedContainer, httpHeaders, Boolean.TRUE);
      return instructionResponse;
    } catch (Exception e) {
      logger.error(
          "{} {}",
          ReceivingException.COMPLETE_INSTRUCTION_ERROR_MSG,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingException(
          ReceivingException.COMPLETE_INSTRUCTION_ERROR_MSG,
          HttpStatus.BAD_REQUEST,
          ReceivingException.COMPLETE_INSTRUCTION_ERROR_CODE);
    }
  }

  @Override
  public InstructionResponse receiveInstruction(
      ReceiveInstructionRequest receiveInstructionRequest, HttpHeaders httpHeaders) {
    logger.info(
        "Default implementation of receive instruction for instruction request {}",
        receiveInstructionRequest);
    return null;
  }
}
