package com.walmart.move.nim.receiving.core.mock.data;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.dateConversionToUTC;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ITEM_UPDATE_SERVICE_NODE_TYPE;

import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.model.ItemCatalogUpdateRequest;
import com.walmart.move.nim.receiving.core.model.itemupdate.ItemUpdateContent;
import com.walmart.move.nim.receiving.core.model.itemupdate.ItemUpdateRequest;
import com.walmart.move.nim.receiving.core.model.itemupdate.ItemUpdateResponse;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.Date;
import org.springframework.http.HttpHeaders;

public class MockItemUpdateData {
  public static ItemUpdateRequest getItemUpdateRequest() {
    ItemUpdateContent itemUpdateContent = new ItemUpdateContent();
    itemUpdateContent.setLastUpdateTs(dateConversionToUTC(new Date()));
    itemUpdateContent.setLastUpdateUserId("sysadmin");
    itemUpdateContent.setLithiumIonVerifiedOn(ReceivingUtils.dateConversionToUTC(new Date()));
    itemUpdateContent.setLimitedQuantityLTD(ReceivingUtils.dateConversionToUTC(new Date()));
    return ItemUpdateRequest.builder()
        .correlationId(TenantContext.getCorrelationId())
        .country(TenantContext.getFacilityCountryCode())
        .eventType(ITEM_UPDATE_SERVICE_EVENT_TYPE)
        .division(BASE_DIVISION_CODE)
        .source(ITEM_UPDATE_REQUEST_SRC)
        .nodeType(ITEM_UPDATE_SERVICE_NODE_TYPE)
        .facilityNumber(TenantContext.getFacilityNum())
        .itemNbr(12345L)
        .content(itemUpdateContent)
        .build();
  }

  public static ItemUpdateResponse getMockItemUpdateResponse() {
    return ItemUpdateResponse.builder()
        .country("us")
        .division("1")
        .node("TEST")
        .statusMessage("SUCCESS")
        .build();
  }

  public static ItemCatalogUpdateRequest getItemCatalogUpdateRequest() {
    ItemCatalogUpdateRequest itemCatalogUpdateRequest = new ItemCatalogUpdateRequest();
    itemCatalogUpdateRequest.setDeliveryNumber("87654321");
    itemCatalogUpdateRequest.setItemNumber(567898765L);
    itemCatalogUpdateRequest.setLocationId("100");
    itemCatalogUpdateRequest.setNewItemUPC("20000943037194");
    itemCatalogUpdateRequest.setOldItemUPC("00000943037194");
    return itemCatalogUpdateRequest;
  }

  public static HttpHeaders getIqsItemUpdateHeaders(HttpHeaders httpHeaders) {
    httpHeaders.add(IQS_CORRELATION_ID_KEY, TenantContext.getCorrelationId());
    httpHeaders.add(IQS_CONSUMER_ID_KEY, "5e17f2df-cc5e-40e1-a5b2-c4899d6c190b");
    httpHeaders.add(IQS_SVC_CHANNEL_TYPE_KEY, "e04ffghytre");
    httpHeaders.add(IQS_SVC_KEY, IQS_SVC_VALUE);
    httpHeaders.add(IQS_SVC_ENV_KEY, "tst");
    return httpHeaders;
  }
}
