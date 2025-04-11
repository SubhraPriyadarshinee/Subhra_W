package com.walmart.move.nim.receiving.core.common;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.dateConversionToUTC;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.getForwardableHttpHeaders;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;

import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.ItemCatalogUpdateRequest;
import com.walmart.move.nim.receiving.core.model.gdm.v2.VendorComplianceRequestDates;
import com.walmart.move.nim.receiving.core.model.itemupdate.ItemUpdateContent;
import com.walmart.move.nim.receiving.core.model.itemupdate.ItemUpdateGtinAttribute;
import com.walmart.move.nim.receiving.core.model.itemupdate.ItemUpdateRequest;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class ItemUpdateUtils {
  @ManagedConfiguration private AppConfig appConfig;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  public ItemUpdateRequest createItemUpdateRequest(
      ItemCatalogUpdateRequest itemCatalogUpdateRequest, HttpHeaders httpHeaders) {
    ItemUpdateGtinAttribute gtinAttribute =
        ItemUpdateGtinAttribute.builder()
            .gtin(itemCatalogUpdateRequest.getNewItemUPC())
            .isCataloguedItem(true)
            .build();
    String userId =
        CollectionUtils.isNotEmpty(httpHeaders.get(USER_ID_HEADER_KEY))
            ? httpHeaders.get(USER_ID_HEADER_KEY).get(0)
            : PROVIDER_ID;
    ItemUpdateContent content =
        ItemUpdateContent.builder()
            .dcGtinAttributeList(Collections.singletonList(gtinAttribute))
            .lastUpdateTs(dateConversionToUTC(new Date()))
            .lastUpdateUserId(userId)
            .build();
    ItemUpdateRequest itemUpdateRequest = getBaseItemUpdateRequest();
    itemUpdateRequest.setContent(content);
    itemUpdateRequest.setItemNbr(itemCatalogUpdateRequest.getItemNumber());
    return itemUpdateRequest;
  }

  public ItemUpdateRequest createVendorComplianceItemUpdateRequest(
      VendorComplianceRequestDates vendorComplianceRequestDates,
      String itemNumber,
      HttpHeaders httpHeaders) {
    String userId =
        CollectionUtils.isNotEmpty(httpHeaders.get(USER_ID_HEADER_KEY))
            ? httpHeaders.get(USER_ID_HEADER_KEY).get(0)
            : PROVIDER_ID;
    ItemUpdateContent itemUpdateContent = new ItemUpdateContent();
    if (!StringUtils.isEmpty(vendorComplianceRequestDates.getLithiumIonVerifiedOn())) {
      itemUpdateContent.setLithiumIonVerifiedOn(
          vendorComplianceRequestDates.getLithiumIonVerifiedOn());
    }
    if (!StringUtils.isEmpty(vendorComplianceRequestDates.getLimitedQtyVerifiedOn())) {
      itemUpdateContent.setLimitedQuantityLTD(
          vendorComplianceRequestDates.getLimitedQtyVerifiedOn());
    }
    if (!StringUtils.isEmpty(vendorComplianceRequestDates.getHazmatVerifiedOn())) {
      itemUpdateContent.setHazmatVerifiedOn(vendorComplianceRequestDates.getHazmatVerifiedOn());
    }
    itemUpdateContent.setLastUpdateTs(dateConversionToUTC(new Date()));
    itemUpdateContent.setLastUpdateUserId(userId);
    ItemUpdateRequest itemUpdateRequest = getBaseItemUpdateRequest();
    itemUpdateRequest.setContent(itemUpdateContent);
    itemUpdateRequest.setItemNbr(Long.valueOf(itemNumber));
    return itemUpdateRequest;
  }

  public HttpHeaders getIqsItemUpdateHeaders(HttpHeaders httpHeaders) {
    httpHeaders = getForwardableHttpHeaders(httpHeaders);
    httpHeaders.add(IQS_CORRELATION_ID_KEY, TenantContext.getCorrelationId());
    httpHeaders.add(IQS_CONSUMER_ID_KEY, appConfig.getReceivingConsumerId());
    httpHeaders.add(IQS_SVC_CHANNEL_TYPE_KEY, appConfig.getIqsChannelType());
    httpHeaders.add(IQS_SVC_KEY, IQS_SVC_VALUE);
    httpHeaders.add(IQS_SVC_ENV_KEY, appConfig.getIqsServiceEnv());
    return httpHeaders;
  }

  public ItemUpdateRequest getBaseItemUpdateRequest() {
    Integer facilityNumber = TenantContext.getFacilityNum();
    /* This configuration is only to support Node RT/IQS data set up issues for lower
    environment testing, overriding the facility number*/
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(), IQS_ITEM_UPDATE_TEST_ENABLED, false)) {
      facilityNumber = IQS_TEST_FACILITY_NUMBER;
    }
    return ItemUpdateRequest.builder()
        .correlationId(TenantContext.getCorrelationId())
        .country(TenantContext.getFacilityCountryCode())
        .eventType(ITEM_UPDATE_SERVICE_EVENT_TYPE)
        .division(BASE_DIVISION_CODE)
        .source(ITEM_UPDATE_REQUEST_SRC)
        .nodeType(ITEM_UPDATE_SERVICE_NODE_TYPE)
        .facilityNumber(facilityNumber)
        .build();
  }
}
