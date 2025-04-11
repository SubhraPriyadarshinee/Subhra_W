package com.walmart.move.nim.receiving.rdc.service;

import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.springframework.stereotype.Component;

/** @author v0k00fe */
@Component
public class RdcReceiptBuilder {

  public Receipt buildReceipt(Instruction instruction, String userId, int receivedQuantityInZA) {

    DeliveryDocumentLine deliveryDocumentLine =
        InstructionUtils.getDeliveryDocumentLine(instruction);
    Receipt receipt = new Receipt();
    receipt.setDeliveryNumber(instruction.getDeliveryNumber());
    receipt.setDoorNumber(
        instruction.getMove().get(ReceivingConstants.MOVE_FROM_LOCATION).toString());
    receipt.setPurchaseReferenceNumber(instruction.getPurchaseReferenceNumber());
    receipt.setPurchaseReferenceLineNumber(instruction.getPurchaseReferenceLineNumber());
    receipt.setQuantity(receivedQuantityInZA);
    receipt.setQuantityUom(ReceivingConstants.Uom.VNPK);
    receipt.setVnpkQty(deliveryDocumentLine.getVendorPack());
    receipt.setProblemId(instruction.getProblemTagId());
    receipt.setWhpkQty(deliveryDocumentLine.getWarehousePack());
    int backOutQuantityInEA =
        ReceivingUtils.conversionToEaches(
            receivedQuantityInZA,
            ReceivingConstants.Uom.VNPK,
            deliveryDocumentLine.getVendorPack(),
            deliveryDocumentLine.getWarehousePack());
    receipt.setEachQty(backOutQuantityInEA);
    receipt.setCreateUserId(userId);

    return receipt;
  }
}
