package com.walmart.move.nim.receiving.controller;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.TENENT_COUNTRY_CODE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.TENENT_FACLITYNUM;
import static java.util.Objects.requireNonNull;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.rx.model.FixitAttpRequest;
import com.walmart.move.nim.receiving.rx.service.EpsicHelper;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("publishEpcisEvent")
@Tag(name = "Epcis APi Service", description = "To expose epcis and GDM update Services")
public class EpcisController {

  private static final Logger log = LoggerFactory.getLogger(EpcisController.class);

  @Autowired private EpsicHelper epcisHelper;

  @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Enables FIXit to Publishing events to GDM and Epcis",
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
  @Timed(
      name = "publishFixitEventsToAttpTimed",
      level1 = "uwms-receiving",
      level2 = "epcisController",
      level3 = "publishFixitEventsToAttpDetails")
  @ExceptionCounted(
      name = "instructionResponseSummaryExceptionCount",
      level1 = "uwms-receiving",
      level2 = "epcisController",
      level3 = "publishFixitEventsToAttpDetails")
  public ResponseEntity<String> publishFixitEventsToAttp(
      @Valid @RequestBody FixitAttpRequest fixitAttpRequest,
      @RequestHeader HttpHeaders httpHeaders) {

    log.info("Called publishToAttp fixitAttpRequest : {}", fixitAttpRequest);
    ResponseEntity<String> response = null;

    try {
      epcisHelper.validateRequest(fixitAttpRequest);
    } catch (ReceivingException e) {
      log.error("Fixit Request Validation failed : {}", e);
      return new ResponseEntity<String>(
          e.getErrorResponse().getErrorMessage().toString(), e.getHttpStatus());
    }

    String countryCode = httpHeaders.getFirst(TENENT_COUNTRY_CODE);
    int facilityNum = Integer.parseInt(requireNonNull(httpHeaders.getFirst(TENENT_FACLITYNUM)));
    TenantContext.setFacilityCountryCode(countryCode);
    TenantContext.setFacilityNum(facilityNum);

    try {
      epcisHelper.publishFixitEventsToAttp(fixitAttpRequest, httpHeaders);
      response = new ResponseEntity<>("Event Submitted", HttpStatus.OK);
    } catch (ReceivingException e) {
      HttpStatus status = HttpStatus.CONFLICT;
      response = new ResponseEntity<>("GDM Shipment Status Failed", status);
      log.error("GDM Shipment Status Failed : {}", e);
    }

    return response;
  }
}
