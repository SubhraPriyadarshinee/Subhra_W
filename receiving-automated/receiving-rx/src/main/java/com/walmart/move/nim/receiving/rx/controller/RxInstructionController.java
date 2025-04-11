package com.walmart.move.nim.receiving.rx.controller;

import com.walmart.move.nim.receiving.core.model.PatchInstructionRequest;
import com.walmart.move.nim.receiving.rx.service.RxInstructionService;
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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnExpression("${enable.rx.app:false}")
@RequestMapping("instructions")
@Tag(
    name = "Rx Instruction Service",
    description = "To expose RxInstruction resource and related services")
public class RxInstructionController {

  @Autowired private RxInstructionService rxInstructionService;

  @PatchMapping(
      path = "/{instructionId}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Performs Instruction Update",
      description =
          "This will return a 200 on successful complete and 500 on failure and 400 on validation error.")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true)
  })
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Instruction was successfully updated.")
      })
  @Timed(
      name = "patchInstructionTimed",
      level1 = "uwms-receiving",
      level2 = "RxInstructionController",
      level3 = "patchInstructionDetails")
  @ExceptionCounted(
      name = "patchInstructionExceptionCount",
      level1 = "uwms-receiving",
      level2 = "RxInstructionController",
      level3 = "patchInstructionDetails")
  public void patchInstruction(
      @PathVariable(value = "instructionId", required = true) Long instructionId,
      @RequestBody(required = true) PatchInstructionRequest patchInstructionRequest,
      @RequestHeader HttpHeaders httpHeaders) {
    rxInstructionService.patchInstruction(instructionId, patchInstructionRequest, httpHeaders);
  }
}
