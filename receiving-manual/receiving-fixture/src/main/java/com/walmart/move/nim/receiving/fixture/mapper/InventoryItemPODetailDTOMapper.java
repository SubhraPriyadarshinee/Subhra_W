package com.walmart.move.nim.receiving.fixture.mapper;

import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.model.InventoryItemPODetailUpdateRequest;
import com.walmart.move.nim.receiving.core.model.ItemPODetails;
import com.walmart.move.nim.receiving.core.model.SearchCriteria;
import com.walmart.move.nim.receiving.core.model.UpdateAttributes;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class InventoryItemPODetailDTOMapper {
  public static InventoryItemPODetailUpdateRequest getInventoryItemPODetailUpdateRequest(
      Container container) {
    return InventoryItemPODetailUpdateRequest.builder()
        .updateAttributes(
            UpdateAttributes.builder().updatePODetails(getUpdateAttributes(container)).build())
        .searchCriteria(
            SearchCriteria.builder()
                .trackingIds(Collections.singletonList(container.getTrackingId()))
                .baseDivisionCode(ReceivingConstants.BASE_DIVISION_CODE)
                .financialReportingGroup(TenantContext.getFacilityCountryCode())
                .build())
        .build();
  }

  private static Map<String, ItemPODetails> getUpdateAttributes(Container container) {
    Map<String, ItemPODetails> itemPODetails = new HashMap<>();
    String destNbr = container.getDestination().get(ReceivingConstants.BU_NUMBER);
    String destCC = container.getDestination().get(ReceivingConstants.COUNTRY_CODE);
    container
        .getContainerItems()
        .forEach(
            item ->
                itemPODetails.put(
                    item.getItemNumber().toString(),
                    ItemPODetails.builder()
                        .poNum(item.getPurchaseReferenceNumber())
                        .purchaseReferenceLineNumber(
                            Objects.nonNull(item.getPurchaseReferenceLineNumber())
                                ? item.getPurchaseReferenceLineNumber()
                                : null)
                        .poQty(item.getOrderableQuantity())
                        .destNbr(destNbr)
                        .destCC(destCC)
                        .build()));
    return itemPODetails;
  }
}
