package com.walmart.move.nim.receiving.rdc.controller;

import com.walmart.move.nim.receiving.core.model.ItemCatalogUpdateRequest;
import com.walmart.move.nim.receiving.rdc.service.RdcItemCatalogService;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller class for item cataloging
 *
 * @author s0g015w
 */
@ConditionalOnExpression("${enable.rdc.app:false}")
@RestController
@RequestMapping("rdc/itemcatalog")
@Tag(name = "Catalog upc for RDC market", description = "ItemCatalog")
public class RdcItemCatalogController {
  @Autowired private RdcItemCatalogService rdcItemCatalogService;

  @PostMapping
  @Operation(
      summary = "Updates the vendor UPC of the given item and maintains a catalog",
      description = "This will return a 200")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true)
  })
  @Timed(
      name = "RdcItemCatalogAPITimed",
      level1 = "uwms-receiving",
      level2 = "RdcItemCatalogController",
      level3 = "updateVendorUPC")
  @ExceptionCounted(
      name = "RdcItemCatalogAPIExceptionCount",
      level1 = "uwms-receiving",
      level2 = "RdcItemCatalogController",
      level3 = "updateVendorUPC")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public ResponseEntity<String> updateVendorUpc(
      @RequestBody @Valid ItemCatalogUpdateRequest itemCatalogUpdateRequest,
      @RequestHeader HttpHeaders httpHeaders) {
    String itemCatalogUpdateLog =
        rdcItemCatalogService.updateVendorUPC(itemCatalogUpdateRequest, httpHeaders);
    return new ResponseEntity<>(itemCatalogUpdateLog, HttpStatus.OK);
  }
}
