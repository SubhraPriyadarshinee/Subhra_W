package com.walmart.move.nim.receiving.rx.builders;

import static com.walmart.move.nim.receiving.rx.constants.RxConstants.SHIPMENT_DOCUMENT_ID;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.EMPTY_STRING;

import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.DocumentLine;
import com.walmart.move.nim.receiving.core.model.UpdateInstructionRequest;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.model.RxInstructionType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class RxReceiptsBuilder {

  /*
   * Creating Receipts from instructions
   */
  public List<Receipt> buildReceipts(
      Instruction instruction,
      UpdateInstructionRequest instructionRequest,
      String userId,
      int eachQty,
      int quantity,
      String inboundShipmentDocId) {

    String ssccNumber = RxUtils.getSSCCFromInstruction(instruction);
    final List<Receipt> receipts = new ArrayList<>();
    for (DocumentLine documentLine : instructionRequest.getDeliveryDocumentLines()) {
      Receipt receipt = new Receipt();
      receipt.setDeliveryNumber(instruction.getDeliveryNumber());
      receipt.setDoorNumber(instructionRequest.getDoorNumber());
      receipt.setPurchaseReferenceNumber(documentLine.getPurchaseReferenceNumber());
      receipt.setPurchaseReferenceLineNumber(documentLine.getPurchaseReferenceLineNumber());
      receipt.setSsccNumber(ssccNumber);
      receipt.setInboundShipmentDocId(inboundShipmentDocId);
      // Qty will be always 1 vnpk for RX on each update instruction
      receipt.setQuantity(quantity);
      receipt.setQuantityUom(ReceivingConstants.Uom.VNPK);
      receipt.setVnpkQty(documentLine.getVnpkQty());
      receipt.setProblemId(instruction.getProblemTagId());
      receipt.setWhpkQty(documentLine.getWhpkQty());
      receipt.setEachQty(eachQty);
      receipt.setCreateUserId(userId);
      receipt.setCreateTs(new Date());

      receipts.add(receipt);
    }
    return receipts;
  }

  // Do not use receivedQuantity & containerQtyInEa from instruction
  // this method is specifically created this way to workaround a production issue
  public Receipt buildReceipt(
      Instruction instruction, String userId, int receivedQuantity, int containerQtyInEa) {

    String ssccNumber = RxUtils.getSSCCFromInstruction(instruction);
    DeliveryDocumentLine deliveryDocumentLine = RxUtils.getDeliveryDocumentLine(instruction);

    Receipt receipt = new Receipt();
    receipt.setDeliveryNumber(instruction.getDeliveryNumber());
    receipt.setDoorNumber(
        instruction.getMove().get(ReceivingConstants.MOVE_FROM_LOCATION).toString());
    receipt.setPurchaseReferenceNumber(instruction.getPurchaseReferenceNumber());
    receipt.setPurchaseReferenceLineNumber(instruction.getPurchaseReferenceLineNumber());
    receipt.setSsccNumber(ssccNumber);
    receipt.setInboundShipmentDocId(deliveryDocumentLine.getInboundShipmentDocId());
    int backOutQuantity =
        ReceivingUtils.conversionToVendorPackRoundUp(
                receivedQuantity,
                instruction.getReceivedQuantityUOM(),
                deliveryDocumentLine.getVendorPack(),
                deliveryDocumentLine.getWarehousePack())
            * -1;
    receipt.setQuantity(backOutQuantity);
    receipt.setQuantityUom(ReceivingConstants.Uom.VNPK);
    receipt.setVnpkQty(deliveryDocumentLine.getVendorPack());
    receipt.setProblemId(instruction.getProblemTagId());
    receipt.setWhpkQty(deliveryDocumentLine.getWarehousePack());
    int receivedQtyInEA = 0;
    if (instruction
        .getInstructionCode()
        .equalsIgnoreCase(
            RxInstructionType.BUILD_CONTAINER_FOR_UPC_RECEIVING.getInstructionType())) {
      receivedQtyInEA =
          ReceivingUtils.conversionToEaches(
              receivedQuantity,
              instruction.getReceivedQuantityUOM(),
              deliveryDocumentLine.getVendorPack(),
              deliveryDocumentLine.getWarehousePack());
    } else {
      receivedQtyInEA =
          containerQtyInEa > 0 ? containerQtyInEa : RxUtils.findEachQtySummary(instruction);
    }
    int backOutQuantityInEA = receivedQtyInEA * -1;
    receipt.setEachQty(backOutQuantityInEA);
    receipt.setCreateUserId(userId);

    return receipt;
  }

  public Receipt buildReceiptToRollbackInEaches(
      Instruction instruction, String userId, int receivedQuantity, int containerQtyInEa) {

    String ssccNumber = RxUtils.getSSCCFromInstruction(instruction);
    DeliveryDocumentLine deliveryDocumentLine = RxUtils.getDeliveryDocumentLine(instruction);

    Receipt receipt = new Receipt();
    receipt.setDeliveryNumber(instruction.getDeliveryNumber());
    receipt.setDoorNumber(
        instruction.getMove().get(ReceivingConstants.MOVE_FROM_LOCATION).toString());
    receipt.setPurchaseReferenceNumber(instruction.getPurchaseReferenceNumber());
    receipt.setPurchaseReferenceLineNumber(instruction.getPurchaseReferenceLineNumber());
    receipt.setSsccNumber(ssccNumber);
    receipt.setInboundShipmentDocId(deliveryDocumentLine.getInboundShipmentDocId());
    int backOutQuantity = receivedQuantity * -1;
    receipt.setQuantity(backOutQuantity);
    receipt.setQuantityUom(ReceivingConstants.Uom.VNPK);
    receipt.setVnpkQty(deliveryDocumentLine.getVendorPack());
    receipt.setProblemId(instruction.getProblemTagId());
    receipt.setWhpkQty(deliveryDocumentLine.getWarehousePack());
    int receivedQtyInEA =
        containerQtyInEa > 0 ? containerQtyInEa : RxUtils.findEachQtySummary(instruction);

    int backOutQuantityInEA = receivedQtyInEA * -1;
    receipt.setEachQty(backOutQuantityInEA);
    receipt.setCreateUserId(userId);

    return receipt;
  }

  public List<Receipt> constructRollbackReceiptsWithShipment(
      List<Container> modifiedContainers,
      HashMap<String, Receipt> receiptsByShipment,
      Instruction instruction) {
    List<Receipt> rollbackReceiptsWithShipment = new ArrayList<>();
    for (Container modifiedContainer : modifiedContainers) {
      if (Objects.nonNull(modifiedContainer.getContainerMiscInfo())
          && modifiedContainer.getContainerMiscInfo().containsKey(SHIPMENT_DOCUMENT_ID)) {
        String shipmentDocumentId =
            modifiedContainer
                .getContainerMiscInfo()
                .getOrDefault(SHIPMENT_DOCUMENT_ID, EMPTY_STRING)
                .toString();
        if (receiptsByShipment.containsKey(shipmentDocumentId)) {
          Receipt receipt = receiptsByShipment.get(shipmentDocumentId);
          int eachQty =
              receipt.getEachQty() - modifiedContainer.getContainerItems().get(0).getQuantity();
          receipt.setEachQty(eachQty);
          receipt.setQuantity(
              ReceivingUtils.conversionToVendorPack(
                  eachQty,
                  modifiedContainer.getContainerItems().get(0).getQuantityUOM(),
                  modifiedContainer.getContainerItems().get(0).getVnpkQty(),
                  modifiedContainer.getContainerItems().get(0).getWhpkQty()));
        } else {
          Receipt receipt = RxUtils.resetRecieptQty(modifiedContainer);
          receipt.setProblemId(instruction.getProblemTagId());
          int eachQty = modifiedContainer.getContainerItems().get(0).getQuantity() * -1;
          receipt.setQuantity(
              ReceivingUtils.conversionToVendorPack(
                  eachQty,
                  modifiedContainer.getContainerItems().get(0).getQuantityUOM(),
                  modifiedContainer.getContainerItems().get(0).getVnpkQty(),
                  modifiedContainer.getContainerItems().get(0).getWhpkQty()));
          receipt.setEachQty(eachQty);
          receipt.setInboundShipmentDocId(shipmentDocumentId);

          receiptsByShipment.put(shipmentDocumentId, receipt);
        }
        rollbackReceiptsWithShipment =
            receiptsByShipment.values().stream().collect(Collectors.toList());
      }
    }
    return rollbackReceiptsWithShipment;
  }
}
