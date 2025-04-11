package com.walmart.move.nim.receiving.rc.controller;

import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.rc.contants.RcConstants;
import com.walmart.move.nim.receiving.rc.model.dto.request.PackageTrackerRequest;
import com.walmart.move.nim.receiving.rc.service.RcPackageTrackerService;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@ConditionalOnExpression("${enable.rc.app:false}")
@RestController
@RequestMapping(RcConstants.RETURNS_PACKAGE_URI)
@Tag(name = "Return center package service", description = "To expose package resource")
public class RcPackageController {

  @Autowired private RcPackageTrackerService rcPackageTrackerService;

  @PostMapping(path = RcConstants.PACKAGE_TRACKER_URI)
  @Operation(
      summary = "This will track package details for a return.",
      description = "This will return a 201 on successful.")
  @Parameters({
    @Parameter(name = RcConstants.TENENT_COUNTRY_CODE, required = true),
    @Parameter(name = RcConstants.TENENT_FACLITYNUM, required = true),
    @Parameter(name = RcConstants.USER_ID_HEADER_KEY, required = true),
    @Parameter(name = RcConstants.CORRELATION_ID_HEADER_KEY, required = true)
  })
  @Timed(
      name = "rcTrackPackageTimed",
      level1 = "uwms-receiving",
      level2 = "rcPackageController",
      level3 = "rcTrackPackage")
  @ExceptionCounted(
      name = "rcTrackPackageExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rcPackageController",
      level3 = "rcTrackPackage")
  @TimeTracing(component = AppComponent.RC, type = Type.REST, flow = "TrackPackage")
  public ResponseEntity trackPackage(
      @RequestBody PackageTrackerRequest packageTrackerRequest,
      @RequestHeader HttpHeaders httpHeaders) {
    rcPackageTrackerService.trackPackageStatus(packageTrackerRequest);
    return new ResponseEntity("Successfully created", HttpStatus.CREATED);
  }
}
