package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.model.ItemCatalogDeleteRequest;
import com.walmart.move.nim.receiving.core.model.ItemCatalogUpdateRequest;
import org.springframework.http.HttpHeaders;

/**
 * Interface for item cataloging service
 *
 * @author g0k0072
 */
public interface ItemCatalogService {
  String updateVendorUPC(
      ItemCatalogUpdateRequest itemCatalogUpdateRequest, HttpHeaders httpHeaders);

  void deleteItemCatalog(ItemCatalogDeleteRequest itemCatalogUpdateRequest);
}
