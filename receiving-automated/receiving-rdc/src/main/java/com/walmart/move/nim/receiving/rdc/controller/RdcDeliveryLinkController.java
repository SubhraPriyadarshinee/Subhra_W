package com.walmart.move.nim.receiving.rdc.controller;

import com.walmart.move.nim.receiving.core.model.DeliveryLinkRequest;
import com.walmart.move.nim.receiving.rdc.service.RdcDeliveryLinkService;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("rdc/delivery")
public class RdcDeliveryLinkController {
  @Autowired RdcDeliveryLinkService rdcDeliveryLinkService;

  @PostMapping("/link")
  @Operation(
      summary = "Returns 200 OK when linking is successful and if delivery is already linked",
      description = "This will return a 200 on success and 400,500 on failure")
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
        in = ParameterIn.HEADER)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public ResponseEntity<String> validateReadinessAndLinkDelivery(
      @RequestBody DeliveryLinkRequest deliveryLinkRequest,
      @RequestHeader HttpHeaders httpHeaders) {
    rdcDeliveryLinkService.validateReadinessAndLinkDelivery(deliveryLinkRequest, httpHeaders);
    return new ResponseEntity<>(ReceivingConstants.SUCCESS, HttpStatus.OK);
  }
}
