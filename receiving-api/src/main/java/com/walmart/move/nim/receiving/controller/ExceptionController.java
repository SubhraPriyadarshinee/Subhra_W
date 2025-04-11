package com.walmart.move.nim.receiving.controller;

import com.walmart.move.nim.receiving.core.client.hawkeye.model.DeliverySearchRequest;
import com.walmart.move.nim.receiving.core.client.hawkeye.model.DeliverySearchResponse;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.InstructionResponse;
import com.walmart.move.nim.receiving.core.model.InventoryUpdateRequest;
import com.walmart.move.nim.receiving.core.model.ReceiveExceptionRequest;
import com.walmart.move.nim.receiving.core.service.ExceptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("exception")
public class ExceptionController {
  private static final Logger log = LoggerFactory.getLogger(ExceptionController.class);

  @Autowired private ExceptionService exceptionService;

  @PostMapping("/receive")
  @Operation(
      summary =
          "Returns printjob if already received else returns"
              + " the completed instruction and print jobs when completing an instruction ",
      description = "This will return a 200 on success and 500 on failure")
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
        example = "Example: 32899",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        required = true,
        example = "Example: sysAdmin",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-correlationId",
        required = true,
        example = "Example: 123e4567-e89b-12d3-a456-426655440000",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-User-Location",
        required = true,
        example = "Example: 10516",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-User-Location-Type",
        required = true,
        example = "Example: Door-25",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-User-Location-SCC",
        required = true,
        example = "Example: 001002001",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public ResponseEntity<InstructionResponse> receiveException(
      @RequestBody ReceiveExceptionRequest receiveExceptionRequest,
      @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    return new ResponseEntity<>(
        exceptionService.receiveException(receiveExceptionRequest, httpHeaders), HttpStatus.OK);
  }

  @PostMapping(path = "/deliveries/search")
  @Operation(
      summary = "Returns delivery and UPC details",
      description =
          "This will return a 200 for No deliveries found, "
              + "500 on failure and 404 on Invalid Request parameter")
  @Parameters({
    @Parameter(
        name = "facilityNum",
        required = true,
        example = "Example: 06020",
        description = "Numeric value",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "facilityCountryCode",
        required = true,
        example = "Example: US",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-correlationId",
        required = true,
        example = "Example: 123e4567-e89b-12d3-a456-426655440000",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        required = true,
        example = "Example: sysAdmin",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-User-Location",
        required = true,
        example = "Example: 10516",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-User-Location-Type",
        required = true,
        example = "Example: Door-25",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-User-Location-SCC",
        required = true,
        example = "Example: 001002001",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public ResponseEntity<DeliverySearchResponse> getDeliveryDocumentsForDeliverySearch(
      @RequestBody DeliverySearchRequest deliverySearchRequest,
      @RequestHeader HttpHeaders httpHeaders) {
    DeliverySearchResponse response =
        DeliverySearchResponse.builder()
            .deliveryDocuments(
                exceptionService.getDeliveryDocumentsForDeliverySearch(
                    deliverySearchRequest, httpHeaders))
            .build();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @PutMapping(path = "/printshiplabel/{trackingId}")
  @Operation(
      summary = "This will return PrintJob for printing SL from RL ",
      description = "This will return 200 for PrintJob")
  @Parameter(
      name = "facilityNum",
      required = true,
      example = "Example: 06020",
      description = "Numeric value",
      in = ParameterIn.HEADER)
  @Parameter(
      name = "facilityCountryCode",
      required = true,
      example = "Example: US",
      description = "String",
      in = ParameterIn.HEADER)
  @Parameter(
      name = "WMT-correlationId",
      required = true,
      example = "Example: 123e4567-e89b-12d3-a456-426655440000",
      description = "String",
      in = ParameterIn.HEADER)
  @Parameter(
      name = "WMT-UserId",
      required = true,
      example = "Example: sysAdmin",
      description = "String",
      in = ParameterIn.HEADER)
  @Parameter(
      name = "WMT-User-Location",
      required = true,
      example = "Example: 10516",
      description = "String",
      in = ParameterIn.HEADER)
  @Parameter(
      name = "WMT-User-Location-Type",
      required = true,
      example = "Example: Door-25",
      description = "String",
      in = ParameterIn.HEADER)
  @Parameter(
      name = "WMT-User-Location-SCC",
      required = true,
      example = "Example: 001002001",
      description = "String",
      in = ParameterIn.HEADER)
  @Parameter(
      name = "WMT-User-Location-Name",
      required = true,
      example = "Example: EXCEPTIONWKS-001",
      description = "String",
      in = ParameterIn.HEADER)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public ResponseEntity<Map<String, Object>> printShippingLabel(
      @PathVariable(value = "trackingId", required = true) String trackingId,
      @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    Map<String, Object> printJobResponse =
        exceptionService.printShippingLabel(trackingId, httpHeaders);
    return new ResponseEntity<>(printJobResponse, HttpStatus.OK);
  }

  @PostMapping("/container/inventoryupdate")
  @Operation(
      summary = "Update Container in inventory for SSTK received reject case",
      description = "This will return a 200 on success and 500 on failure")
  @Parameter(
      name = "facilityCountryCode",
      required = true,
      example = "Example: US",
      description = "String",
      in = ParameterIn.HEADER)
  @Parameter(
      name = "facilityNum",
      required = true,
      example = "Example: 32899",
      description = "String",
      in = ParameterIn.HEADER)
  @Parameter(
      name = "WMT-UserId",
      required = true,
      example = "Example: sysAdmin",
      description = "String",
      in = ParameterIn.HEADER)
  @Parameter(
      name = "WMT-correlationId",
      required = true,
      example = "Example: 123e4567-e89b-12d3-a456-426655440000",
      description = "String",
      in = ParameterIn.HEADER)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Inventory Update")})
  public ResponseEntity<Object> inventoryContainerUpdate(
      @RequestBody InventoryUpdateRequest inventoryUpdateRequest,
      @RequestHeader HttpHeaders httpHeaders) {
    exceptionService.inventoryContainerUpdate(inventoryUpdateRequest, httpHeaders);
    return new ResponseEntity<>(HttpStatus.OK);
  }
}
