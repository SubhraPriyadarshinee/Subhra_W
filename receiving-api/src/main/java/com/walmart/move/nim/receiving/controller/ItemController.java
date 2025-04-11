package com.walmart.move.nim.receiving.controller;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.model.ItemInfoResponse;
import com.walmart.move.nim.receiving.core.model.ItemOverrideRequest;
import com.walmart.move.nim.receiving.core.model.SaveConfirmationRequest;
import com.walmart.move.nim.receiving.core.model.UpdateVendorComplianceRequest;
import com.walmart.move.nim.receiving.core.service.DeliveryItemOverrideService;
import com.walmart.move.nim.receiving.core.service.ItemService;
import com.walmart.move.nim.receiving.core.service.RegulatedItemService;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/items")
public class ItemController {
  private static final Logger log = LoggerFactory.getLogger(ItemController.class);
  @Autowired private ItemService itemService;

  @Autowired private DeliveryItemOverrideService deliveryItemOverrideService;
  @Autowired private RegulatedItemService regulatedItemService;

  @GetMapping("/search/upcs/{upc}")
  public Set<Long> findLatestItemByUPC(@PathVariable("upc") String upc) {

    Set<Long> itemSet = new LinkedHashSet<>();
    itemService.findLatestItemByUPC(upc).forEach(itemNumber -> itemSet.add(itemNumber));
    return itemSet;
  }

  @GetMapping("/search/item-base-div-codes/{upc}")
  public List<ItemInfoResponse> findItemBaseDivCodesByUPC(@PathVariable("upc") String upc) {
    return itemService.findItemBaseDivCodesByUPC(upc).stream().distinct().collect(Collectors.toList());
  }

  @PutMapping("/vendor-compliance")
  @Operation(
      summary = "Update vendor compliance of an item ",
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
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public ResponseEntity<String> updateVendorCompliance(
      @RequestBody @Valid UpdateVendorComplianceRequest updateVendorComplianceRequest)
      throws ReceivingException {
    log.info("Update vendor compliance request: {}", updateVendorComplianceRequest);
    regulatedItemService.updateVendorComplianceItem(
        updateVendorComplianceRequest.getRegulatedItemType(),
        updateVendorComplianceRequest.getItemNumber());
    return new ResponseEntity<>(HttpStatus.OK);
  }

  @PostMapping("/override")
  @Operation(
      summary =
          "Override item properties such as pallet Ti, pallet Hi, packtypeCode, handlingCode to persist in delivery item override table",
      description = "This will return a 200")
  @Parameters({
    @Parameter(
        name = "facilityCountryCode",
        required = true,
        example = "Example: US",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "facilityNum",
        required = true,
        example = "Example: 32987",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        required = true,
        example = "Example: sysadmin",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-correlationId",
        required = true,
        example = "Example: 123e4567-e89b-12d3-a456-426655440000",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @Timed(
      name = "itemOverrideTimed",
      level1 = "uwms-receiving-api",
      level2 = "itemController",
      level3 = "itemOverride")
  @ExceptionCounted(
      name = "itemOverrideExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "itemController",
      level3 = "itemOverride")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public ResponseEntity<String> itemOverride(
      @RequestBody @Valid ItemOverrideRequest itemOverrideRequest,
      @RequestHeader HttpHeaders httpHeaders) {
    HttpHeaders forwardableHttpHeaders = ReceivingUtils.getForwardableHttpHeaders(httpHeaders);
    log.info("Item override request: {}", itemOverrideRequest);
    itemService.itemOverride(itemOverrideRequest, forwardableHttpHeaders);
    return new ResponseEntity<>(HttpStatus.OK);
  }

  @PostMapping("/saveConfirmation")
  @Operation(
      summary = "Save user selection for Item Validation.",
      description = "This will return a 200")
  @Parameters({
    @Parameter(
        name = "facilityCountryCode",
        required = true,
        example = "Example: US",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "facilityNum",
        required = true,
        example = "Example: 32987",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        required = true,
        example = "Example: sysadmin",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-correlationId",
        required = true,
        example = "Example: 123e4567-e89b-12d3-a456-426655440000",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @Timed(
      name = "saveConfirmationTimed",
      level1 = "uwms-receiving-api",
      level2 = "itemController",
      level3 = "saveConfirmation")
  @ExceptionCounted(
      name = "saveConfirmationExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "itemController",
      level3 = "saveConfirmation")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public ResponseEntity<Object> saveConfirmation(
      @RequestBody @Valid SaveConfirmationRequest saveConfirmationRequest,
      @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    HttpHeaders forwardableHttpHeaders = ReceivingUtils.getForwardableHttpHeaders(httpHeaders);
    log.info("Received Request to save COO: {}", saveConfirmationRequest);
    deliveryItemOverrideService.updateCountryOfOriginAndPackAknowlegementInfo(
        saveConfirmationRequest, forwardableHttpHeaders);
    return itemService.saveConfirmationRequest(saveConfirmationRequest);
  }
}
