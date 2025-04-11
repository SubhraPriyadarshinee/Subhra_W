package com.walmart.move.nim.receiving.acc.controller;

import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.service.LabelDataLpnService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@ConditionalOnExpression("${enable.acc.app:false}")
@RestController
@RequestMapping("label-data")
public class LabelDataController {

  @Autowired private LabelDataLpnService labelDataLpnService;

  @GetMapping(path = "/{lpn}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Get label data by lpn API",
      description = "This will return a 200 if label data is found else, return 404 if not found")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public ResponseEntity<LabelData> getLabelDataByLpn(@PathVariable(value = "lpn") String lpn) {
    LabelData labelDataResponse =
        labelDataLpnService
            .findLabelDataByLpn(lpn)
            .orElseThrow(
                () ->
                    new ReceivingDataNotFoundException(
                        ExceptionCodes.LABEL_DATA_NOT_FOUND,
                        String.format(
                            ExceptionDescriptionConstants.LABEL_DATA_NOT_FOUND_ERROR_MSG, lpn)));
    return new ResponseEntity<>(labelDataResponse, HttpStatus.OK);
  }
}
