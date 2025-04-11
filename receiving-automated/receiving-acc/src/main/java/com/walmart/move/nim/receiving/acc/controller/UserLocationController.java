package com.walmart.move.nim.receiving.acc.controller;

import com.walmart.move.nim.receiving.acc.model.UserLocationRequest;
import com.walmart.move.nim.receiving.acc.service.UserLocationService;
import com.walmart.move.nim.receiving.core.model.LocationInfo;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** @author s0g015w */
@ConditionalOnExpression("${enable.acc.app:false}")
@RestController
@RequestMapping("location-users")
@Tag(name = "user location mapping Service", description = "UserLocation")
public class UserLocationController {

  @Autowired private UserLocationService userLocationService;

  @PutMapping(path = "", produces = "application/json")
  @Operation(
      summary = "Successfully mapped user to given location",
      description = "This will return a 200")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true)
  })
  @Timed(
      name = "userLocAPITimed",
      level1 = "uwms-receiving",
      level2 = "userLocationController",
      level3 = "updateUserLocationInfo")
  @ExceptionCounted(
      name = "userLocAPIExceptionCount",
      level1 = "uwms-receiving",
      level2 = "userLocationController",
      level3 = "updateUserLocationInfo")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public ResponseEntity<LocationInfo> updateUserLocationInfo(
      @RequestBody UserLocationRequest userLocationRequest, @RequestHeader HttpHeaders headers) {
    LocationInfo locationResponse =
        userLocationService.getLocationInfo(userLocationRequest, headers);
    return new ResponseEntity<>(locationResponse, HttpStatus.OK);
  }
}
