package com.walmart.move.nim.receiving.wfs.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.getForwardableHeadersWithRequestOriginator;

import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.model.MultipleCancelInstructionsRequestBody;
import com.walmart.move.nim.receiving.core.model.PublishReceiptsCancelInstruction;
import com.walmart.move.nim.receiving.core.service.CancelMultipleInstructionRequestHandler;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

public class WFSCancelMultipleInstructionRequestHandler
    implements CancelMultipleInstructionRequestHandler {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(WFSCancelMultipleInstructionRequestHandler.class);
  @Autowired private InstructionPersisterService instructionPersisterService;
  @Autowired private JmsPublisher jmsPublisher;
  @Autowired private WFSInstructionUtils wfsInstructionUtils;

  @Override
  public void cancelInstructions(
      MultipleCancelInstructionsRequestBody multipleCancelInstructionsRequestBody,
      HttpHeaders httpHeaders) {
    try {
      String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
      List<Instruction> modifiedInstructions = new ArrayList<>();

      for (Long instructionId : multipleCancelInstructionsRequestBody.getInstructionIds()) {
        Instruction instruction = instructionPersisterService.getInstructionById(instructionId);

        if (wfsInstructionUtils.isCancelInstructionAllowed(instruction)) {

          // Publish the receipt
          if (instruction.getContainer() != null) {

            // Prepare the payload to publish receipt with ZERO quantity

            PublishReceiptsCancelInstruction.ContentsData contentsData =
                new PublishReceiptsCancelInstruction.ContentsData(
                    instruction.getPurchaseReferenceNumber(),
                    instruction.getPurchaseReferenceLineNumber(),
                    0,
                    ReceivingConstants.Uom.EACHES);
            PublishReceiptsCancelInstruction receiptsCancelInstruction =
                new PublishReceiptsCancelInstruction();
            receiptsCancelInstruction.setMessageId(instruction.getMessageId());
            receiptsCancelInstruction.setTrackingId(instruction.getContainer().getTrackingId());
            receiptsCancelInstruction.setDeliveryNumber(instruction.getDeliveryNumber());
            receiptsCancelInstruction.setContents(Collections.singletonList(contentsData));
            receiptsCancelInstruction.setActivityName(instruction.getActivityName());

            ReceivingJMSEvent receivingJMSEvent =
                new ReceivingJMSEvent(
                    getForwardableHeadersWithRequestOriginator(httpHeaders),
                    new GsonBuilder()
                        .registerTypeAdapter(Date.class, new GsonUTCDateAdapter())
                        .create()
                        .toJson(receiptsCancelInstruction));
            jmsPublisher.publish(
                ReceivingConstants.PUB_RECEIPTS_TOPIC, receivingJMSEvent, Boolean.TRUE);
          }

          // Complete instruction with received quantity as ZERO
          instruction.setReceivedQuantity(0);
          instruction.setCompleteUserId(userId);
          instruction.setCompleteTs(new Date());
          modifiedInstructions.add(instruction);
        }
      }

      wfsInstructionUtils.persistForCancelInstructions(modifiedInstructions);
    } catch (ReceivingException exc) {
      LOGGER.error("Exception while cancelInstructions: {} {}", exc.getMessage(), exc);
      throw new ReceivingBadDataException(
          ExceptionCodes.CANCEL_INSTRUCTION_ERROR_MSG, exc.getMessage());
    }
  }
}
