package com.walmart.move.nim.receiving.rx.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.MultipleCancelInstructionsRequestBody;
import com.walmart.move.nim.receiving.core.service.CancelMultipleInstructionRequestHandler;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.rx.builders.RxReceiptsBuilder;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.config.RxManagedConfig;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.publisher.RxCancelInstructionReceiptPublisher;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.RequestType;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component(RxConstants.RX_CANCEL_MULTIPLE_INSTRUCTION_REQUEST_HANDLER)
public class RxCancelMultipleInstructionRequestHandler
    implements CancelMultipleInstructionRequestHandler {

  private static final Logger LOG =
      LoggerFactory.getLogger(RxCancelMultipleInstructionRequestHandler.class);

  @Autowired private InstructionPersisterService instructionPersisterService;
  @Autowired private ContainerService containerService;
  @Autowired private RxInstructionHelperService rxInstructionHelperService;
  @Autowired private RxReceiptsBuilder rxReceiptsBuilder;
  @Autowired private RxCancelInstructionReceiptPublisher rxCancelInstructionReceiptsPublisher;
  @ManagedConfiguration private RxManagedConfig rxManagedConfig;

  @Override
  public void cancelInstructions(
      MultipleCancelInstructionsRequestBody multipleCancelInstructionsRequestBody,
      HttpHeaders httpHeaders) {
    Instruction instruction = null;
    List<String> trackingIds = new ArrayList<>();
    List<Receipt> receipts = new ArrayList<>();
    List<Instruction> instructions = new ArrayList<>();
    try {
      for (Long instructionId : multipleCancelInstructionsRequestBody.getInstructionIds()) {
        instruction = instructionPersisterService.getInstructionById(instructionId);
        if (Objects.nonNull(instruction.getCompleteTs())) {
          LOG.error("Instruction: {} is already complete", instruction.getId());
          throw new ReceivingBadDataException(
              ExceptionCodes.INSTRUCITON_IS_ALREADY_COMPLETED,
              ReceivingException.INSTRUCTION_IS_ALREADY_COMPLETED);
        }
        String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);

        int backOutQuantity = instruction.getReceivedQuantity();

        ReceivingUtils.verifyUser(instruction, userId, RequestType.CANCEL);
        // Complete instruction with received quantity as ZERO
        instruction.setReceivedQuantity(0);
        instruction.setCompleteUserId(userId);
        instruction.setCompleteTs(new Date());

        // Publish the receipt
        if (instruction.getContainer() != null) {

          List<Container> containersByInstruction =
              containerService.getContainerByInstruction(instructionId);
          Optional<Container> parentContainerOptional =
              containersByInstruction
                  .stream()
                  .filter(container -> Objects.isNull(container.getParentTrackingId()))
                  .findFirst();
          int backoutQtyInEa = 0;
          if (parentContainerOptional.isPresent()) {
            Container parentContainer = parentContainerOptional.get();
            ContainerItem parentContainerItems = parentContainer.getContainerItems().get(0);
            backoutQtyInEa = parentContainerItems.getQuantity();
            backOutQuantity =
                ReceivingUtils.conversionToVendorPackRoundUp(
                    parentContainerItems.getQuantity(),
                    parentContainerItems.getQuantityUOM(),
                    parentContainerItems.getVnpkQty(),
                    parentContainerItems.getWhpkQty());
          }

          if (CollectionUtils.isNotEmpty(containersByInstruction)) {
            for (Container container : containersByInstruction) {
              trackingIds.add(container.getTrackingId());
            }
          }

          if (rxManagedConfig.isRollbackReceiptsByShipment()) {
            HashMap<String, Receipt> receiptsByShipment = new HashMap<>();

            if (CollectionUtils.isNotEmpty(containersByInstruction)
                && containersByInstruction.size() > 1) { // D40 will have only one Container.
              receipts.addAll(
                  rxReceiptsBuilder.constructRollbackReceiptsWithShipment(
                      containersByInstruction, receiptsByShipment, instruction));
            } else {
              receipts.add(
                  rxReceiptsBuilder.buildReceiptToRollbackInEaches(
                      instruction, userId, backOutQuantity, backoutQtyInEa));
            }
          } else {
            Receipt cancelledReceipt =
                rxReceiptsBuilder.buildReceiptToRollbackInEaches(
                    instruction, userId, backOutQuantity, backoutQtyInEa);
            receipts.add(cancelledReceipt);
          }
        }
        instructions.add(instruction);
      }
      // Delete all the persisted containers and receipts
      rxInstructionHelperService.rollbackContainers(trackingIds, receipts, instructions);
    } catch (ReceivingBadDataException receivingBadDataException) {
      LOG.error(receivingBadDataException.getDescription(), receivingBadDataException);
      throw receivingBadDataException;
    } catch (ReceivingException receivingException) {
      if (Objects.nonNull(instruction)
          && ReceivingException.MULTI_USER_ERROR_CODE.equals(
              receivingException.getErrorResponse().getErrorCode())) {
        throw new ReceivingBadDataException(
            ExceptionCodes.INSTRUCTION_MULTI_USER_ERROR_MESSAGE,
            receivingException.getMessage(),
            new Object[] {ReceivingUtils.getInstructionOwner(instruction)});
      } else {
        throw RxUtils.convertToReceivingBadDataException(receivingException);
      }
    } catch (Exception exception) {
      LOG.error("{}", ReceivingException.CANCEL_INSTRUCTION_ERROR_CODE, exception);
      throw new ReceivingBadDataException(
          ExceptionCodes.CANCEL_PALLET_ERROR, ReceivingException.CANCEL_INSTRUCTION_ERROR_MSG);
    }
  }
}
