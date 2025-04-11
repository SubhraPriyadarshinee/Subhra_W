package com.walmart.move.nim.receiving.rdc.service;

import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.UpdateInstructionRequest;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.rdc.utils.RdcContainerUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcInstructionUtils;
import java.util.List;
import java.util.Objects;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** @author v0k00fe */
@Component
public class RdcInstructionHelper {

  @Autowired private ContainerPersisterService containerPersisterService;
  @Autowired private InstructionPersisterService instructionPersisterService;
  @Autowired private ReceiptService receiptService;
  @Autowired private ContainerService containerService;
  @Autowired private RdcContainerUtils rdcContainerUtils;
  @Autowired private InstructionHelperService instructionHelperService;
  @Autowired private RdcInstructionUtils rdcInstructionUtils;

  private static final Logger LOGGER = LoggerFactory.getLogger(RdcInstructionHelper.class);

  public Container buildContainerAndContainerItem(
      Instruction instruction,
      DeliveryDocument deliveryDocument,
      UpdateInstructionRequest updateInstructionRequest,
      Integer receivedQuantity,
      String userId,
      String labelTrackingId) {

    String slotId =
        deliveryDocument.getDeliveryDocumentLines().get(0).getAdditionalInfo().getPrimeSlot();
    List<ContainerItem> containerItems =
        rdcContainerUtils.buildContainerItem(
            labelTrackingId, deliveryDocument, receivedQuantity, null);
    Container container =
        rdcContainerUtils.buildContainer(
            instruction,
            updateInstructionRequest,
            deliveryDocument,
            userId,
            labelTrackingId,
            slotId,
            null);

    container.setContainerItems(containerItems);
    return container;
  }

  @Transactional
  @InjectTenantFilter
  public void persistForUpdateInstruction(
      Instruction instruction, Container container, List<Receipt> receipts) {
    if (Objects.nonNull(instruction)) {
      instructionPersisterService.saveInstruction(instruction);
    }
    if (Objects.nonNull(container)) {
      containerPersisterService.saveContainer(container);
    }
    if (CollectionUtils.isNotEmpty(receipts)) {
      receiptService.saveAll(receipts);
    }
  }

  @Transactional
  @InjectTenantFilter
  public void persistForCancelInstructions(
      List<String> trackingIds, List<Receipt> receipts, List<Instruction> instructions) {
    if (CollectionUtils.isNotEmpty(instructions)) {
      instructionPersisterService.saveAllInstruction(instructions);
    }
    if (CollectionUtils.isNotEmpty(trackingIds)) {
      containerService.deleteContainersByTrackingIds(trackingIds);
    }
    if (CollectionUtils.isNotEmpty(receipts)) {
      receiptService.saveAll(receipts);
    }
  }

  @Transactional
  @InjectTenantFilter
  public void persistForCompleteInstruction(
      List<Instruction> instructions,
      List<Container> containers,
      List<ContainerItem> containerItems) {
    if (CollectionUtils.isNotEmpty(instructions)) {
      instructionPersisterService.saveAllInstruction(instructions);
    }
    if (CollectionUtils.isNotEmpty(containers)) {
      containerPersisterService.saveContainerAndContainerItems(containers, containerItems);
    }
  }

  /**
   * Publish Instruction to WFT on a kafka topic
   *
   * @param instruction
   * @param httpHeaders
   * @param labelTrackingId
   * @param deliveryDocumentLine
   */
  public void publishInstruction(
      Instruction instruction,
      HttpHeaders httpHeaders,
      String labelTrackingId,
      DeliveryDocumentLine deliveryDocumentLine) {
    LOGGER.info("Publishing instruction message to WFT for labelTrackingId:{}", labelTrackingId);
    instructionHelperService.publishInstruction(
        httpHeaders,
        rdcInstructionUtils.prepareInstructionMessage(
            instruction,
            instruction.getReceivedQuantity(),
            httpHeaders,
            deliveryDocumentLine.getVendorPack(),
            deliveryDocumentLine.getWarehousePack()));
  }
}
