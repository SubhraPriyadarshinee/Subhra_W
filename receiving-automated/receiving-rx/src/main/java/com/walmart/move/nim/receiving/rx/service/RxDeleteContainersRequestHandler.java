package com.walmart.move.nim.receiving.rx.service;

import static com.walmart.move.nim.receiving.rx.constants.RxConstants.SHIPMENT_DOCUMENT_ID;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.EMPTY_STRING;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.ContainerDetails;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.ItemData;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.DeleteContainersRequestHandler;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.config.RxManagedConfig;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;

public class RxDeleteContainersRequestHandler implements DeleteContainersRequestHandler {

  @Autowired private ReceiptService receiptService;

  @Autowired private ContainerService containerService;

  @Autowired private ContainerItemRepository containerItemRepository;

  @Autowired private InstructionRepository instructionRepository;

  @ManagedConfiguration private RxManagedConfig rxManagedConfig;

  @Autowired private Gson gson;

  @Override
  @Transactional
  @InjectTenantFilter
  public void deleteContainersByTrackingId(List<String> trackingIds, HttpHeaders httpHeaders) {

    List<Instruction> adjustedInstructions = new ArrayList<>();
    List<ContainerItem> adjustedContainerItems = new ArrayList<>();
    List<Receipt> adjustedReceipts = new ArrayList<>();

    try {
      for (String trackingId : trackingIds) {
        Container container = containerService.getContainerByTrackingId(trackingId);
        if (RxUtils.isParentContainer(container)) {
          throw new ReceivingBadDataException(
              ExceptionCodes.PARENT_CONTAINER_CANNOT_BE_DELETED,
              RxConstants.PARENT_CONTAINER_CANNOT_BE_DELETED);
        }

        Optional<Instruction> instructionOptional =
            instructionRepository.findById(container.getInstructionId());
        if (instructionOptional.isPresent()) {
          Instruction instruction = instructionOptional.get();
          if (!Objects.isNull(instruction.getCompleteTs())) {
            throw new ReceivingBadDataException(
                ExceptionCodes.INSTRUCTION_CLOSED_CONTAINER_CANNOT_BE_DELETED,
                RxConstants.INSTRUCTION_CLOSED_CONTAINER_CANNOT_BE_DELETED);
          }
        } else {
          throw new ReceivingBadDataException(
              ExceptionCodes.INSTRUCTION_NOT_FOUND_FOR_CONTAINER,
              RxConstants.INSTRUCTION_NOT_FOUND_FOR_CONTAINER);
        }

        Instruction instruction = instructionOptional.get();
        // Adjust Instruction Qty
        adjustedInstructions.add(adjustInstructions(instruction, container));

        ContainerItem containerItem = container.getContainerItems().get(0);
        // Adjust Container Parent Qty
        adjustedContainerItems.add(adjustParentContainer(container, containerItem));

        String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
        adjustedReceipts.add(adjustReceipts(instruction, containerItem, userId, container));
      }

      instructionRepository.saveAll(adjustedInstructions);
      containerItemRepository.saveAll(adjustedContainerItems);
      receiptService.saveAll(adjustedReceipts);

      // Delete Specified Container
      containerService.deleteContainersByTrackingIds(trackingIds);
    } catch (ReceivingException e) {
      throw RxUtils.convertToReceivingBadDataException(e);
    }
  }

  private Instruction adjustInstructions(Instruction instruction, Container container) {
    ContainerItem containerItem = container.getContainerItems().get(0);
    Integer containerQty;
    if (rxManagedConfig.isRollbackPartialContainerEnabled()) {
      containerQty =
          ReceivingUtils.conversionToEaches(
              containerItem.getQuantity(),
              containerItem.getQuantityUOM(),
              containerItem.getVnpkQty(),
              containerItem.getWhpkQty());
    } else {
      containerQty =
          ReceivingUtils.conversionToVendorPack(
              containerItem.getQuantity(),
              containerItem.getQuantityUOM(),
              containerItem.getVnpkQty(),
              containerItem.getWhpkQty());
    }

    instruction.setReceivedQuantity(instruction.getReceivedQuantity() - containerQty);

    if (null != instruction.getDeliveryDocument()) {
      DeliveryDocument deliveryDocument =
          gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
      DeliveryDocumentLine deliveryDocumentLine =
          deliveryDocument.getDeliveryDocumentLines().get(0);

      if (null != deliveryDocumentLine.getAdditionalInfo()) {
        ItemData additionalInfo = deliveryDocumentLine.getAdditionalInfo();
        if (additionalInfo.getAuditCompletedQty() > 0) {
          additionalInfo.setAuditCompletedQty(
              additionalInfo.getAuditCompletedQty() - ReceivingConstants.ONE);
          deliveryDocumentLine.setAdditionalInfo(additionalInfo);
          deliveryDocument.setDeliveryDocumentLines(
              Collections.singletonList(deliveryDocumentLine));
          instruction.setDeliveryDocument(gson.toJson(deliveryDocument));
        }
      }
    }
    List<ContainerDetails> childContainers = instruction.getChildContainers();
    if (CollectionUtils.isNotEmpty(childContainers)) {
      Iterator<ContainerDetails> iterator = childContainers.iterator();
      while (iterator.hasNext()) {
        if (iterator.next().getTrackingId().equals(container.getTrackingId())) {
          iterator.remove();
        }
      }
    }
    instruction.setChildContainers(childContainers);
    return instruction;
  }

  private ContainerItem adjustParentContainer(Container container, ContainerItem containerItem)
      throws ReceivingException {
    Container parentContainer =
        containerService.getContainerByTrackingId(container.getParentTrackingId());
    ContainerItem parentContainerItem = parentContainer.getContainerItems().get(0);
    parentContainerItem.setQuantity(
        parentContainerItem.getQuantity() - containerItem.getQuantity());
    return parentContainerItem;
  }

  private Receipt adjustReceipts(
      Instruction instruction, ContainerItem containerItem, String userId, Container container) {

    String ssccNumber = RxUtils.getSSCCFromInstruction(instruction);
    DeliveryDocumentLine deliveryDocumentLine = RxUtils.getDeliveryDocumentLine(instruction);

    Receipt receipt = new Receipt();
    receipt.setDeliveryNumber(instruction.getDeliveryNumber());
    receipt.setDoorNumber(
        instruction.getMove().get(ReceivingConstants.MOVE_FROM_LOCATION).toString());
    receipt.setPurchaseReferenceNumber(instruction.getPurchaseReferenceNumber());
    receipt.setPurchaseReferenceLineNumber(instruction.getPurchaseReferenceLineNumber());
    receipt.setSsccNumber(ssccNumber);
    receipt.setVnpkQty(deliveryDocumentLine.getVendorPack());
    receipt.setProblemId(instruction.getProblemTagId());
    receipt.setWhpkQty(deliveryDocumentLine.getWarehousePack());
    receipt.setCreateUserId(userId);

    int adjustedQuantity =
        ReceivingUtils.conversionToVendorPack(
                containerItem.getQuantity(),
                containerItem.getQuantityUOM(),
                containerItem.getVnpkQty(),
                containerItem.getWhpkQty())
            * -1;
    receipt.setQuantity(adjustedQuantity);
    if (rxManagedConfig.isRollbackPartialContainerEnabled()) {
      receipt.setQuantityUom(ReceivingConstants.Uom.VNPK);
    } else {
      receipt.setQuantityUom(containerItem.getQuantityUOM());
    }
    receipt.setEachQty(containerItem.getQuantity() * -1);
    if (Objects.nonNull(container.getContainerMiscInfo())
        && container.getContainerMiscInfo().containsKey(SHIPMENT_DOCUMENT_ID)) {
      receipt.setInboundShipmentDocId(
          container
              .getContainerMiscInfo()
              .getOrDefault(SHIPMENT_DOCUMENT_ID, EMPTY_STRING)
              .toString());
    }
    return receipt;
  }
}
