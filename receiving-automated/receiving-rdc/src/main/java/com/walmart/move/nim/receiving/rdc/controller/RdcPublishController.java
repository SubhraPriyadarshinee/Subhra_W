package com.walmart.move.nim.receiving.rdc.controller;

import com.walmart.move.nim.receiving.rdc.service.RdcPublishService;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.constraints.NotEmpty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("rdc/publish")
@Tag(name = "Publish messages to kafka topics", description = "PublishMessage")
public class RdcPublishController {

  @Autowired RdcPublishService rdcPublishService;

  @PostMapping(path = "/message", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Publish messages to kafka topics", description = "This will return a 200")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true)
  })
  @Timed(
      name = "RdcPublishAPITimed",
      level1 = "uwms-receiving",
      level2 = "RdcPublishController",
      level3 = "publishMessage")
  @ExceptionCounted(
      name = "RdcPublishAPIExceptionCount",
      level1 = "uwms-receiving",
      level2 = "RdcPublishController",
      level3 = "publishMessage")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public ResponseEntity<String> publishMessage(
      @RequestBody @NotEmpty String messageBody, @RequestHeader HttpHeaders httpHeaders) {
    rdcPublishService.publishMessage(messageBody, httpHeaders);
    return new ResponseEntity<>(ReceivingConstants.SUCCESS, HttpStatus.OK);
  }
}
