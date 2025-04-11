package com.walmart.move.nim.receiving.mfc.transformer;

import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.framework.transformer.Transformer;
import com.walmart.move.nim.receiving.mfc.model.common.CommonReceiptDTO;
import com.walmart.move.nim.receiving.mfc.model.common.Quantity;
import com.walmart.move.nim.receiving.mfc.model.common.QuantityType;
import com.walmart.move.nim.receiving.mfc.model.inventory.ItemListItem;
import com.walmart.move.nim.receiving.mfc.model.inventory.MFCInventoryAdjustmentDTO;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class InventoryReceiptTransformer
    implements Transformer<MFCInventoryAdjustmentDTO, CommonReceiptDTO> {
  @Override
  public void observe() {
    Transformer.super.observe();
  }

  @Override
  public CommonReceiptDTO transform(MFCInventoryAdjustmentDTO mfcInventoryAdjustmentDTO) {
    return CommonReceiptDTO.builder()
        .containerId(mfcInventoryAdjustmentDTO.getEventObject().getTrackingId())
        .deliveryNumber(
            Long.valueOf(mfcInventoryAdjustmentDTO.getEventObject().getDeliveryNumber()))
        .quantities(createQuantities(mfcInventoryAdjustmentDTO))
        .gtin(retriveGTIN(mfcInventoryAdjustmentDTO))
        .build();
  }

  private String retriveGTIN(MFCInventoryAdjustmentDTO mfcInventoryAdjustmentDTO) {

    return mfcInventoryAdjustmentDTO
        .getEventObject()
        .getItemList()
        .stream()
        .filter(invItem -> Objects.nonNull(invItem.getAdjustmentTO()))
        .map(item -> item.getItemUPC())
        .findFirst()
        .orElse(null);
  }

  private List<Quantity> createQuantities(MFCInventoryAdjustmentDTO mfcInventoryAdjustmentDTO) {

    List<Quantity> quantities = new ArrayList<>();
    List<ItemListItem> items = mfcInventoryAdjustmentDTO.getEventObject().getItemList();
    ItemListItem item =
        items
            .stream()
            .filter(invItem -> Objects.nonNull(invItem.getAdjustmentTO()))
            .findFirst()
            .orElseThrow(
                () ->
                    new ReceivingInternalException(
                        ExceptionCodes.RECEIVING_INTERNAL_ERROR,
                        String.format(
                            "Inventory data is not proper for %s",
                            mfcInventoryAdjustmentDTO.getEventObject().getTrackingId())));
    quantities.add(
        new Quantity(
            Long.valueOf(Math.abs(item.getAdjustmentTO().getValue())),
            item.getAdjustmentTO().getUom(),
            retriveReasonCode(item.getAdjustmentTO().getReasonCode())));

    return quantities;
  }

  private QuantityType retriveReasonCode(Integer reasonCode) {
    return QuantityType.getQuantityType(reasonCode);
  }

  @Override
  public List<CommonReceiptDTO> transformList(
      List<MFCInventoryAdjustmentDTO> mfcInventoryAdjustmentDTOS) {
    return null;
  }

  @Override
  public MFCInventoryAdjustmentDTO reverseTransform(CommonReceiptDTO commonReceiptDTO) {
    return null;
  }

  @Override
  public List<MFCInventoryAdjustmentDTO> reverseTransformList(
      List<CommonReceiptDTO> commonReceiptDTOs) {
    return null;
  }
}
