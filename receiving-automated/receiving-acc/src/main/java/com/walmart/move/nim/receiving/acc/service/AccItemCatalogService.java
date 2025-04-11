package com.walmart.move.nim.receiving.acc.service;

import com.walmart.move.nim.receiving.acc.config.ACCManagedConfig;
import com.walmart.move.nim.receiving.acc.model.VendorUpcUpdateRequest;
import com.walmart.move.nim.receiving.acc.util.ACCUtils;
import com.walmart.move.nim.receiving.core.model.ItemCatalogUpdateRequest;
import com.walmart.move.nim.receiving.core.model.LocationInfo;
import com.walmart.move.nim.receiving.core.service.DefaultItemCatalogService;
import io.strati.configuration.annotation.ManagedConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

public class AccItemCatalogService extends DefaultItemCatalogService {
  private static final Logger LOGGER = LoggerFactory.getLogger(AccItemCatalogService.class);

  @Autowired private ACLService aclService;

  @ManagedConfiguration private ACCManagedConfig accManagedConfig;

  @Override
  public String updateVendorUPC(
      ItemCatalogUpdateRequest itemCatalogUpdateRequest, HttpHeaders httpHeaders) {
    LocationInfo locationInfo = itemCatalogUpdateRequest.getLocationInfo();

    if (ACCUtils.checkIfLocationIsEitherOnlineOrFloorLine(locationInfo)) {
      VendorUpcUpdateRequest vendorUpcUpdateRequest =
          VendorUpcUpdateRequest.builder()
              .deliveryNumber(itemCatalogUpdateRequest.getDeliveryNumber())
              .itemNumber(itemCatalogUpdateRequest.getItemNumber().toString())
              .locationId(itemCatalogUpdateRequest.getLocationId())
              .catalogGTIN(itemCatalogUpdateRequest.getNewItemUPC())
              .build();
      aclService.updateVendorUpc(vendorUpcUpdateRequest, httpHeaders);
    }

    return super.updateVendorUPC(itemCatalogUpdateRequest, httpHeaders);
  }
}
