package com.walmart.move.nim.receiving.fixture.controller;

import com.walmart.move.nim.receiving.fixture.model.*;
import com.walmart.move.nim.receiving.fixture.service.PalletReceivingService;
import io.strati.metrics.annotation.Counted;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import javax.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@ConditionalOnExpression("${enable.fixture.app:false}")
@RestController
@RequestMapping("pallet")
public class FixturePalletController {
  private static final Logger LOGGER = LoggerFactory.getLogger(FixturePalletController.class);
  @Autowired private PalletReceivingService palletReceivingService;

  @PutMapping("/receive")
  @Operation(summary = "Receives a pallet", description = "This will return a 200")
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
      name = "receivePalletTimed",
      level1 = "uwms-receiving",
      level2 = "fixturePalletController",
      level3 = "receive")
  @ExceptionCounted(
      name = "receivePalletExceptionCount",
      level1 = "uwms-receiving",
      level2 = "fixturePalletController",
      level3 = "receive")
  @Counted(
      name = "receivePalletCount",
      level1 = "uwms-receiving",
      level2 = "fixturePalletController",
      level3 = "receive")
  public ResponseEntity<PalletReceiveResponse> receive(
      @RequestBody @Valid PalletReceiveRequest palletReceiveRequest,
      @RequestHeader HttpHeaders headers) {

    PalletReceiveResponse response = palletReceivingService.receive(palletReceiveRequest, headers);

    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @PutMapping("/lpn")
  @Operation(
      summary = "Maps LPN for a given pallet",
      description = "Updates the LPN for a given pallet")
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
      name = "mapLPNTimed",
      level1 = "uwms-receiving",
      level2 = "fixturePalletController",
      level3 = "mapLPN")
  @ExceptionCounted(
      name = "mapLPNExceptionCount",
      level1 = "uwms-receiving",
      level2 = "fixturePalletController",
      level3 = "mapLPN")
  public ResponseEntity<PalletPutAwayResponse> mapLPN(
      @RequestBody @Valid PalletMapLPNRequest palletMapLPNRequest,
      @RequestHeader HttpHeaders headers) {
    PalletPutAwayResponse palletPutAwayResponse =
        palletReceivingService.mapLpn(palletMapLPNRequest, headers);
    return new ResponseEntity<>(palletPutAwayResponse, HttpStatus.OK);
  }

  @PutMapping("/putaway")
  @Operation(
      summary = "Updates the location of the container",
      description = "Updates the location and publishes information for inventory creation.")
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
      name = "putAwayTimed",
      level1 = "uwms-receiving",
      level2 = "fixturePalletController",
      level3 = "receive")
  @ExceptionCounted(
      name = "putAwayExceptionCount",
      level1 = "uwms-receiving",
      level2 = "fixturePalletController",
      level3 = "receive")
  @Counted(
      name = "putAwayCount",
      level1 = "uwms-receiving",
      level2 = "fixturePalletController",
      level3 = "receive")
  public ResponseEntity<PalletPutAwayResponse> putAway(
      @RequestBody @Valid PalletPutAwayRequest palletPutAwayRequest,
      @RequestHeader HttpHeaders headers) {
    PalletPutAwayResponse palletPutAwayResponse =
        palletReceivingService.putAway(palletPutAwayRequest, headers);
    return new ResponseEntity<>(palletPutAwayResponse, HttpStatus.OK);
  }

  @PutMapping("/v2/receive")
  @Operation(summary = "Receives a pallet", description = "This will return a 200")
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
      name = "receivePalletTimed",
      level1 = "uwms-receiving",
      level2 = "fixturePalletController",
      level3 = "receiveV2")
  @ExceptionCounted(
      name = "receivePalletExceptionCount",
      level1 = "uwms-receiving",
      level2 = "fixturePalletController",
      level3 = "receiveV2")
  @Counted(
      name = "receivePalletCount",
      level1 = "uwms-receiving",
      level2 = "fixturePalletController",
      level3 = "receiveV2")
  public ResponseEntity<PalletReceiveResponse> receiveV2(
      @RequestBody @Valid PalletReceiveRequest palletReceiveRequest,
      @RequestHeader HttpHeaders headers) {

    PalletReceiveResponse response =
        palletReceivingService.receiveV2(palletReceiveRequest, headers);

    return new ResponseEntity<>(response, HttpStatus.OK);
  }
}
