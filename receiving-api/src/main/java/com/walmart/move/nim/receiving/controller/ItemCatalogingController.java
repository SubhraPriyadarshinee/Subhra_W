package com.walmart.move.nim.receiving.controller;

import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.validators.ItemCatalogValidator;
import com.walmart.move.nim.receiving.core.model.ItemCatalogDeleteRequest;
import com.walmart.move.nim.receiving.core.model.ItemCatalogUpdateRequest;
import com.walmart.move.nim.receiving.core.service.DefaultItemCatalogService;
import com.walmart.move.nim.receiving.core.service.ItemCatalogService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller class for item cataloging
 *
 * @author s0g015w
 */
@RestController
@RequestMapping("item-catalog")
@Tag(name = "Service for Item cataloguing", description = "Item Catalog")
public class ItemCatalogingController {

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private DefaultItemCatalogService defaultItemCatalogService;

  @PostMapping
  @Operation(
      summary = "Updates the vendor UPC of the given item and maintains a catalog",
      description = "This will return a 200")
  @Parameters({
    @Parameter(
        name = "facilityCountryCode",
        required = true,
        example = "Country code",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "facilityNum",
        required = true,
        example = "Site Number",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        required = true,
        example = "User ID",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @Timed(
      name = "itemCatalogUpdateAPITimed",
      level1 = "uwms-receiving",
      level2 = "itemCatalogUpdateController",
      level3 = "updateVendorUPC")
  @ExceptionCounted(
      name = "itemCatalogUpdateAPIExceptionCount",
      level1 = "uwms-receiving",
      level2 = "itemCatalogUpdateController",
      level3 = "updateVendorUPC")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public ResponseEntity<String> updateVendorUPC(
      @RequestBody @Valid ItemCatalogUpdateRequest itemCatalogUpdateRequest,
      @RequestHeader HttpHeaders httpHeaders) {
    ItemCatalogValidator.validateCatalogUPC(itemCatalogUpdateRequest);
    HttpHeaders forwardableHttpHeaders = ReceivingUtils.getForwardableHttpHeaders(httpHeaders);
    ItemCatalogService itemCatalogService =
        tenantSpecificConfigReader.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_SERVICE,
            ItemCatalogService.class);
    String itemCatalogUpdateLog =
        itemCatalogService.updateVendorUPC(itemCatalogUpdateRequest, forwardableHttpHeaders);
    return new ResponseEntity<>(itemCatalogUpdateLog, HttpStatus.OK);
  }

  @DeleteMapping
  @Operation(summary = "Delete item catalog entry", description = "This will return a 200")
  @Parameters({
    @Parameter(
        name = "facilityCountryCode",
        required = true,
        example = "Country code",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "facilityNum",
        required = true,
        example = "Site Number",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        required = true,
        example = "User ID",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @Timed(
      name = "deleteItemCatalogAPITimed",
      level1 = "uwms-receiving",
      level2 = "itemCatalogController",
      level3 = "deleteItemCatalog")
  @ExceptionCounted(
      name = "deleteItemCatalogAPIExceptionCount",
      level1 = "uwms-receiving",
      level2 = "itemCatalogController",
      level3 = "deleteItemCatalog")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public void deleteItemCatalog(
      @RequestBody @Valid ItemCatalogDeleteRequest itemCatalogUpdateRequest) {
    ItemCatalogService itemCatalogService =
        tenantSpecificConfigReader.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_SERVICE,
            ItemCatalogService.class);
    itemCatalogService.deleteItemCatalog(itemCatalogUpdateRequest);
  }
}
