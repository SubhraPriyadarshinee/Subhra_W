package com.walmart.move.nim.receiving.mfc.transformer;

import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.PALLET_TYPE;

import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingNotImplementedException;
import com.walmart.move.nim.receiving.core.framework.transformer.Transformer;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.mfc.model.csm.ContainerEventItem;
import com.walmart.move.nim.receiving.mfc.model.csm.ConteinerEvent;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class ContainerDTOEventTransformer implements Transformer<ContainerDTO, ConteinerEvent> {

  @Override
  public ConteinerEvent transform(ContainerDTO containerDTO) {

    List<ContainerEventItem> containerEventItems =
        containerDTO
            .getContainerItems()
            .stream()
            .map(
                containerItem -> {
                  ContainerEventItem containerEventItem =
                      ContainerEventItem.builder()
                          .itemUPC(containerItem.getItemUPC())
                          .itemQty(containerItem.getQuantity())
                          .destTrackingId(UUID.randomUUID().toString())
                          .invoiceNumber(containerItem.getInvoiceNumber())
                          .locationName(
                              Objects.nonNull(containerDTO.getContainerMiscInfo())
                                  ? String.valueOf(
                                      containerDTO.getContainerMiscInfo().get(PALLET_TYPE))
                                  : "STORE")
                          .unitOfMeasurement(containerItem.getQuantityUOM())
                          .build();
                  return containerEventItem;
                })
            .collect(Collectors.toList());

    return ConteinerEvent.builder()
        .itemList(containerEventItems)
        .srcTrackingId(containerDTO.getTrackingId())
        .deliveryNumber(containerDTO.getDeliveryNumber())
        .build();
  }

  @Override
  public List<ConteinerEvent> transformList(List<ContainerDTO> containerDTOS) {
    return containerDTOS
        .stream()
        .map(container -> transform(container))
        .collect(Collectors.toList());
  }

  @Override
  public ContainerDTO reverseTransform(ConteinerEvent conteinerEvent) {
    throw new ReceivingNotImplementedException(
        ExceptionCodes.FEATURE_NOT_IMPLEMENTED, "Not implemented");
  }

  @Override
  public List<ContainerDTO> reverseTransformList(List<ConteinerEvent> d) {
    throw new ReceivingNotImplementedException(
        ExceptionCodes.FEATURE_NOT_IMPLEMENTED, "Not implemented");
  }
}
