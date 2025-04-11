package com.walmart.move.nim.receiving.fixture.mapper;

import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.fixture.common.FixtureConstants;
import com.walmart.move.nim.receiving.fixture.model.ItemDetails;
import com.walmart.move.nim.receiving.fixture.model.PutAwayInventory;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PutAwayDTOMapper {
  public static PutAwayInventory preparePutAwayPayloadFromContainer(Container container) {
    return PutAwayInventory.builder()
        .palletId(container.getParentTrackingId())
        .lpn(container.getTrackingId())
        .destination(container.getDestination().get(ReceivingConstants.BU_NUMBER))
        .putAwayLocation(container.getLocation())
        .weight(0)
        .items(prepareItemDetails(container))
        .build();
  }

  private static List<ItemDetails> prepareItemDetails(Container container) {
    String destination = container.getDestination().get(ReceivingConstants.BU_NUMBER);
    List<ItemDetails> itemDetails = new ArrayList<>();
    DateFormat dateFormat = new SimpleDateFormat(FixtureConstants.DATE_TIME_YYYY_MM_DD_HH_MM_SS);
    container
        .getContainerItems()
        .forEach(
            item ->
                itemDetails.add(
                    ItemDetails.builder()
                        .id(item.getItemNumber().toString())
                        .description(item.getDescription())
                        .quantity(item.getQuantity())
                        .destination(destination)
                        .purchaseOrder(item.getPurchaseReferenceNumber())
                        .poLineNumber(
                            Objects.nonNull(item.getPurchaseReferenceLineNumber())
                                ? item.getPurchaseReferenceLineNumber().toString()
                                : null)
                        .promiseDate(dateFormat.format(container.getCompleteTs()))
                        .build()));
    return itemDetails;
  }
}
