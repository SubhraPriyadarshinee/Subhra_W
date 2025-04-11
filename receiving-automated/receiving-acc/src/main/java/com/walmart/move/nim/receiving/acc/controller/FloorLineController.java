package com.walmart.move.nim.receiving.acc.controller;

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

@ConditionalOnExpression("${enable.acc.app:false}")
@RestController
@RequestMapping("floor-line")
@Tag(name = "Floor Line Service", description = "FloorLine")
public class FloorLineController {

  @Autowired private UserLocationService userLocationService;

  @GetMapping(path = "/{scannedLocation}", produces = "application/json")
  @Operation(summary = "Floor line summary API", description = "This will return a 200")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true)
  })
  @Timed(
      name = "floorLineSummaryTimed",
      level1 = "uwms-receiving",
      level2 = "floorLineController",
      level3 = "floorLineSummary")
  @ExceptionCounted(
      name = "floorLineSummaryExceptionCount",
      level1 = "uwms-receiving",
      level2 = "floorLineController",
      level3 = "floorLineSummary")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public ResponseEntity<LocationInfo> floorLineSummary(
      @PathVariable(value = "scannedLocation") String scannedLocation,
      @RequestHeader HttpHeaders headers) {
    return new ResponseEntity<>(
        userLocationService.createUserLocationMappingForFloorLine(scannedLocation, headers),
        HttpStatus.OK);
  }
}
