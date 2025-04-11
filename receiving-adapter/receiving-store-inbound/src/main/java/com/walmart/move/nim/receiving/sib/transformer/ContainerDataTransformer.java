package com.walmart.move.nim.receiving.sib.transformer;

import static com.walmart.move.nim.receiving.sib.utils.Constants.DEST_TRACKING_ID;
import static com.walmart.move.nim.receiving.sib.utils.Constants.PALLET_TYPE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.EVENT_TYPE;

import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.utils.UomUtils;
import com.walmart.move.nim.receiving.sib.exception.ExceptionMessage;
import com.walmart.move.nim.receiving.sib.exception.StoreExceptionCodes;
import com.walmart.move.nim.receiving.sib.model.ContainerEventData;
import com.walmart.move.nim.receiving.sib.model.ItemData;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.util.Pair;

public class ContainerDataTransformer {

  private static final Logger LOGGER = LoggerFactory.getLogger(ContainerDataTransformer.class);

  @ManagedConfiguration private AppConfig appConfig;

  public ContainerEventData transformToContainerEvent(ContainerDTO containerDTO) {
    String location =
        Objects.nonNull(containerDTO.getContainerMiscInfo())
            ? String.valueOf(containerDTO.getContainerMiscInfo().get(PALLET_TYPE))
            : "STORE";
    Map<String, Object> containerMiscInfo = containerDTO.getContainerMiscInfo();
    String destTrackingId;
    try {
      destTrackingId = containerMiscInfo.get(DEST_TRACKING_ID).toString();
    } catch (Exception exception) {
      LOGGER.error(
          "Dest tracking id not set for processing store finalization event SSCC: {}.",
          containerDTO.getTrackingId(),
          exception);
      throw new ReceivingBadDataException(
          StoreExceptionCodes.INVALID_DEST_TRACKING_ID,
          String.format(
              ExceptionMessage.INVALID_DEST_TRACKING_ID_MSG, containerDTO.getTrackingId()));
    }
    return Objects.nonNull(containerDTO)
        ? ContainerEventData.builder()
            .srcTrackingId(containerDTO.getTrackingId())
            .itemList(
                transformToItemList(containerDTO.getContainerItems(), location, destTrackingId))
            .deliveryNumber(containerDTO.getDeliveryNumber())
            .build()
        : ContainerEventData.builder().build();
  }

  private List<ItemData> transformToItemList(
      List<ContainerItem> containerItems, String location, String destTrackingId) {
    return Objects.nonNull(containerItems)
        ? containerItems
            .stream()
            .map(
                containerItem -> {
                  Pair<Double, String> baseUnitQuantity =
                      UomUtils.getBaseUnitQuantity(
                          containerItem.getQuantity(), containerItem.getQuantityUOM());
                  return ItemData.builder()
                      .destTrackingId(destTrackingId)
                      .invoiceNumber(containerItem.getInvoiceNumber())
                      .itemQty(containerItem.getQuantity())
                      .itemUPC(containerItem.getItemUPC())
                      .locationName(location)
                      .unitOfMeasurement(containerItem.getQuantityUOM())
                      .derivedItemQty(baseUnitQuantity.getFirst())
                      .derivedUnitOfMeasurement(baseUnitQuantity.getSecond())
                      .eventType(
                          Objects.nonNull(containerItem.getContainerItemMiscInfo())
                                  && Objects.nonNull(
                                      containerItem.getContainerItemMiscInfo().get(EVENT_TYPE))
                              ? String.valueOf(
                                  containerItem.getContainerItemMiscInfo().get(EVENT_TYPE))
                              : null)
                      .build();
                })
            .collect(Collectors.toList())
        : new ArrayList<>();
  }
}
