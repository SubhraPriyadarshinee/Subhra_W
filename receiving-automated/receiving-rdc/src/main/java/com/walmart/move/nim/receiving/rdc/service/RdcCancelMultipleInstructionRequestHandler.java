package com.walmart.move.nim.receiving.rdc.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.MultipleCancelInstructionsRequestBody;
import com.walmart.move.nim.receiving.core.service.CancelMultipleInstructionRequestHandler;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.utils.RdcInstructionUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcUtils;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/** @author v0k00fe */
@Component(RdcConstants.RDC_CANCEL_MULTIPLE_INSTRUCTION_REQUEST_HANDLER)
public class RdcCancelMultipleInstructionRequestHandler
    implements CancelMultipleInstructionRequestHandler {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(RdcCancelMultipleInstructionRequestHandler.class);

  @Autowired protected InstructionPersisterService instructionPersisterService;
  @Autowired private RdcInstructionUtils rdcInstructionUtils;
  @Autowired private ContainerService containerService;
  @Autowired private RdcReceiptBuilder rdcReceiptBuilder;
  @Autowired private RdcInstructionHelper rdcInstructionHelper;

  @Override
  public void cancelInstructions(
      MultipleCancelInstructionsRequestBody multipleCancelInstructionsRequestBody,
      HttpHeaders httpHeaders) {
    try {
      String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
      List<Instruction> modifiedInstructions = new ArrayList<>();
      List<String> containerTrackingIds = new ArrayList<>();
      List<Receipt> receipts = new ArrayList<>();
      for (Long instructionId : multipleCancelInstructionsRequestBody.getInstructionIds()) {
        Instruction instruction = instructionPersisterService.getInstructionById(instructionId);
        if (rdcInstructionUtils.isCancelInstructionAllowed(instruction, userId)) {
          // delete containers and container items
          if (Objects.nonNull(instruction.getContainer())
              && instruction.getReceivedQuantity() > 0) {
            List<Container> containerByInstruction =
                containerService.getContainerByInstruction(instructionId);
            for (Container container : containerByInstruction) {
              containerTrackingIds.add(container.getTrackingId());
            }
            if (rdcInstructionUtils.isAtlasConvertedInstruction(instruction)) {
              int backoutQty = instruction.getReceivedQuantity() * -1;
              receipts.add(rdcReceiptBuilder.buildReceipt(instruction, userId, backoutQty));
            }
          }
          // Complete instruction with received quantity as ZERO
          instruction.setReceivedQuantity(0);
          instruction.setCompleteUserId(userId);
          instruction.setCompleteTs(new Date());
          modifiedInstructions.add(instruction);
        }
      }
      rdcInstructionHelper.persistForCancelInstructions(
          containerTrackingIds, receipts, modifiedInstructions);
    } catch (ReceivingException re) {
      LOGGER.error(re.getMessage(), re);
      throw RdcUtils.convertToReceivingBadDataException(re);
    }
  }
}
