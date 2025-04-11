package com.walmart.move.nim.receiving.controller;

import com.walmart.move.nim.receiving.core.model.LocationInfo;
import com.walmart.move.nim.receiving.core.service.LocationService;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller class to fetch location information.
 *
 * @author s0g015w
 */
@RestController
@RequestMapping("locations")
@Tag(name = "Get location information by Id", description = "Get location information")
public class LocationController {
  @Autowired private LocationService locationService;

  @GetMapping(path = "/{locationId}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Successfully retrieved location info for the given location",
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
      name = "getLocationByIdAPITimed",
      level1 = "uwms-receiving",
      level2 = "locationController",
      level3 = "getLocationById")
  @ExceptionCounted(
      name = "getLocationByIdAPIExceptionCount",
      level1 = "uwms-receiving",
      level2 = "locationController",
      level3 = "getLocationById")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public ResponseEntity<LocationInfo> getLocationById(
      @PathVariable(value = "locationId", required = true) String locationId,
      @RequestHeader HttpHeaders headers) {
    LocationInfo locationResponse =
        locationService.getLocationInfoByIdentifier(locationId, headers);
    return new ResponseEntity<>(locationResponse, HttpStatus.OK);
  }
}
