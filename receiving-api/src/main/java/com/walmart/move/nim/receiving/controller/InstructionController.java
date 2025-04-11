package com.walmart.move.nim.receiving.controller;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.CONTAINER_CREATE_TS;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.DATE_FORMAT_ISO8601;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.audit.ReceivePackResponse;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.core.service.InstructionSearchRequestHandler;
import com.walmart.move.nim.receiving.core.service.InstructionService;
import com.walmart.move.nim.receiving.rdc.service.RdcAtlasDsdcService;
import com.walmart.move.nim.receiving.rx.service.RxCompleteInstructionOutboxHandler;
import com.walmart.move.nim.receiving.rx.service.v2.instruction.complete.CompleteInstructionOutboxService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.metrics.annotation.Counted;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.annotation.Resource;
import javax.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Instruction controller to expose instruction resource
 *
 * @author g0k0072
 */
@RestController
@RequestMapping("instructions")
@Tag(
    name = "Instruction Service",
    description = "To expose instruction resource and related services")
public class InstructionController {
  private static final Logger log = LoggerFactory.getLogger(InstructionController.class);

  @Resource(name = ReceivingConstants.DEFAULT_INSTRUCTION_SERVICE)
  private InstructionService instructionService;

  @Autowired private Gson gson;
  @Autowired private InstructionPersisterService instructionPersisterService;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Resource RxCompleteInstructionOutboxHandler rxCompleteInstructionOutboxHandler;
  @Autowired private RdcAtlasDsdcService rdcAtlasDsdcService;

  @Resource private CompleteInstructionOutboxService completeInstructionOutboxService;

  @PostMapping(path = "/search", produces = "application/json")
  @Operation(
      summary = "Return list of instruction(summary) as response",
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
      name = "instructionResponseSummaryTimed",
      level1 = "uwms-receiving",
      level2 = "instructionController",
      level3 = "getInstructionResponseSummaryDetails")
  @ExceptionCounted(
      name = "instructionResponseSummaryExceptionCount",
      level1 = "uwms-receiving",
      level2 = "instructionController",
      level3 = "getInstructionResponseSummaryDetails")
  public ResponseEntity<List<InstructionSummary>> instructions(
      @RequestBody @Valid InstructionSearchRequest instructionSearchRequest,
      @RequestHeader HttpHeaders headers)
      throws ReceivingException {
    List<InstructionSummary> instructionSummaryList;
    Map<String, Object> forwardableHeaders =
        ReceivingUtils.getForwardablHeaderWithTenantData(headers);
    InstructionSearchRequestHandler instructionSearchRequestHandler =
        tenantSpecificConfigReader.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.INSTRUCTION_SEARCH_REQUEST_HANDLER,
            InstructionSearchRequestHandler.class);
    instructionSummaryList =
        instructionSearchRequestHandler.getInstructionSummary(
            instructionSearchRequest, forwardableHeaders);
    return new ResponseEntity<>(instructionSummaryList, HttpStatus.OK);
  }

  @PostMapping(path = "/request", produces = "application/json")
  @Operation(
      hidden = true,
      summary = "Return list of PO Line or instruction as response",
      description = "This will return a 201")
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
        in = ParameterIn.HEADER)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "201", description = "")})
  @Timed(
      name = "instructionRequestTimed",
      level1 = "uwms-receiving",
      level2 = "instructionController",
      level3 = "instructionRequest")
  @ExceptionCounted(
      name = "instructionRequestExceptionCount",
      level1 = "uwms-receiving",
      level2 = "instructionController",
      level3 = "instructionRequest")
  @Counted(
      name = "instructionRequestHitCount",
      level1 = "uwms-receiving",
      level2 = "instructionController",
      level3 = "instructionRequest")
  public ResponseEntity<InstructionResponse> instructionRequest(
      @RequestBody @Valid String instructionRequest, @RequestHeader HttpHeaders headers)
      throws ReceivingException {
    TenantContext.get().setAtlasRcvCrInsStart(System.currentTimeMillis());

    InstructionService instructionServiceByFacility =
        tenantSpecificConfigReader.getInstructionServiceByFacility(
            TenantContext.getFacilityNum().toString());
    InstructionResponse response =
        instructionServiceByFacility.serveInstructionRequest(instructionRequest, headers);
    instructionServiceByFacility.publishWorkingIfNeeded(response, headers);
    TenantContext.get().setAtlasRcvCrInsEnd(System.currentTimeMillis());
    long cumulativeDBCallTime =
        ReceivingUtils.getTimeDifferenceInMillis(
                TenantContext.get().getAtlasRcvChkInsExistStart(),
                TenantContext.get().getAtlasRcvChkInsExistEnd())
            + ReceivingUtils.getTimeDifferenceInMillis(
                TenantContext.get().getAtlasRcvGetRcvdQtyStart(),
                TenantContext.get().getAtlasRcvGetRcvdQtyEnd())
            + ReceivingUtils.getTimeDifferenceInMillis(
                TenantContext.get().getAtlasRcvChkNewInstCanBeCreatedStart(),
                TenantContext.get().getAtlasRcvChkNewInstCanBeCreatedEnd())
            + ReceivingUtils.getTimeDifferenceInMillis(
                TenantContext.get().getAtlasRcvCompInsSaveStart(),
                TenantContext.get().getAtlasRcvCompInsSaveEnd());
    long processTimeGdm =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getAtlasRcvGdmGetDocLineStart(),
            TenantContext.get().getAtlasRcvGdmGetDocLineEnd());
    long processTimeWithOfGdm =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getAtlasRcvCrInsStart(), TenantContext.get().getAtlasRcvCrInsEnd());
    long processTimeWithoutOfGdm =
        processTimeWithOfGdm
            - processTimeGdm
            - ReceivingUtils.getTimeDifferenceInMillis(
                TenantContext.get().getAtlasRcvOfCallStart(),
                TenantContext.get().getAtlasRcvOfCallEnd());

    long lpnServiceProcessTime =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getAtlasRcvLpnCallStart(),
            TenantContext.get().getAtlasRcvLpnCallEnd());

    String messageId = "";
    if (response != null && response.getInstruction() != null) {
      messageId = response.getInstruction().getMessageId();
    }

    log.warn(
        "LatencyCheck receiving item for single PO at ts ={} time in publishing delivery update ={} timeTakenTillGDMCall={} timeTakenByGDM={} timeTillOFcall={} timeTakenByOF={} timeTakenInEntireFlow={} timeTakenByReceivingOnly={}  timeTakenByAllDBcallInReceiving={} lpnServiceProcessTime={} messageid={}",
        TenantContext.get().getAtlasRcvCrInsStart(),
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getAtlasRcvGdmDelStatPubStart(),
            TenantContext.get().getAtlasRcvGdmDelStatPubEnd()),
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getAtlasRcvCrInsStart(),
            TenantContext.get().getAtlasRcvGdmGetDocLineStart()),
        processTimeGdm,
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getAtlasRcvCrInsStart(),
            TenantContext.get().getAtlasRcvOfCallStart()),
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getAtlasRcvOfCallStart(),
            TenantContext.get().getAtlasRcvOfCallEnd()),
        processTimeWithOfGdm,
        processTimeWithoutOfGdm,
        cumulativeDBCallTime,
        lpnServiceProcessTime,
        messageId);
    return new ResponseEntity<>(response, HttpStatus.CREATED);
  }

  @GetMapping(path = "/{instructionId}", produces = "application/json")
  @Operation(
      summary = "Return instruction based on instruction id as response",
      description = "This will return a 200")
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
        in = ParameterIn.HEADER)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public ResponseEntity<Instruction> getInstruction(
      @PathVariable(value = "instructionId", required = true) Long instructionId,
      @RequestHeader HttpHeaders headers)
      throws ReceivingException {
    log.debug("Entering with deliveryNumber: {} ", instructionId);
    InstructionService instructionServiceByFacility =
        tenantSpecificConfigReader.getInstructionServiceByFacility(
            TenantContext.getFacilityNum().toString());
    return new ResponseEntity<>(
        instructionServiceByFacility.getInstructionById(instructionId), HttpStatus.OK);
  }

  @PutMapping(path = "/{instructionId}/update")
  @Operation(
      summary = "Returns the updated instruction and print jobs when updating an instruction",
      description = "This will return a 200 on successful update and 500 on failure")
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
        in = ParameterIn.HEADER)
  })
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Instruction was successfully updated")
      })
  @Timed(
      name = "updateInstructionTimed",
      level1 = "uwms-receiving",
      level2 = "instructionController",
      level3 = "updateInstructionDetails")
  @ExceptionCounted(
      name = "updateInstructionExceptionCount",
      level1 = "uwms-receiving",
      level2 = "instructionController",
      level3 = "updateInstructionDetails")
  public ResponseEntity<InstructionResponse> updateInstruction(
      @PathVariable(value = "instructionId", required = true) Long instructionId,
      @RequestBody UpdateInstructionRequest instructionUpdateRequestFromClient,
      @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    log.info("Request is : {}", instructionUpdateRequestFromClient);
    return new ResponseEntity<>(
        instructionService.updateInstruction(
            instructionId,
            instructionUpdateRequestFromClient,
            ReceivingConstants.EMPTY_STRING,
            httpHeaders),
        HttpStatus.OK);
  }

  @PostMapping(path = "/receive")
  @Operation(
      summary = "Returns the completed instruction and print jobs when completing an instruction",
      description = "This will return a 200 on successful update and 500 on failure")
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
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-User-Location",
        required = true,
        example = "Example: 10516",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-User-Location-Type",
        required = true,
        example = "Example: Door-25",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-User-Location-SCC",
        required = true,
        example = "Example: 001002001",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Instruction was successfully completed")
      })
  @Timed(
      name = "receiveInstructionByDeliveryDocumentTimed",
      level1 = "uwms-receiving",
      level2 = "instructionController",
      level3 = "receiveInstructionDetails")
  @ExceptionCounted(
      name = "receiveInstructionByDeliveryDocumentExceptionCount",
      level1 = "uwms-receiving",
      level2 = "instructionController",
      level3 = "receiveInstructionDetails")
  public ResponseEntity<InstructionResponse> receiveInstruction(
      @RequestBody ReceiveInstructionRequest receiveInstructionRequest,
      @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    return new ResponseEntity<>(
        instructionService.receiveInstruction(receiveInstructionRequest, httpHeaders),
        HttpStatus.OK);
  }

  @PutMapping(path = "/{instructionId}/receive")
  @Operation(
      summary = "Returns the completed instruction and print jobs when completing an instruction",
      description = "This will return a 200 on successful update and 500 on failure")
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
        in = ParameterIn.HEADER)
  })
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Instruction was successfully completed")
      })
  @Timed(
      name = "receiveInstructionTimed",
      level1 = "uwms-receiving",
      level2 = "instructionController",
      level3 = "receiveInstructionDetails")
  @ExceptionCounted(
      name = "receiveInstructionExceptionCount",
      level1 = "uwms-receiving",
      level2 = "instructionController",
      level3 = "receiveInstructionDetails")
  public ResponseEntity<InstructionResponse> receiveInstruction(
      @PathVariable(value = "instructionId", required = true) Long instructionId,
      @RequestBody @Valid String receiveInstructionRequestString,
      @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    TenantContext.setAdditionalParams(
        CONTAINER_CREATE_TS, new SimpleDateFormat(DATE_FORMAT_ISO8601).format(new Date()));
    return new ResponseEntity<>(
        instructionService.receiveInstruction(
            instructionId, receiveInstructionRequestString, httpHeaders),
        HttpStatus.OK);
  }

  @PutMapping(path = {"/{instructionId}/update/{parentTrackingId}"})
  @Operation(
      summary = "Returns the updated instruction and print jobs when updating an instruction",
      description = "This will return a 200 on successful update and 500 on failure")
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
        in = ParameterIn.HEADER)
  })
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Instruction was successfully updated")
      })
  @Timed(
      name = "updateInstructionTimed",
      level1 = "uwms-receiving",
      level2 = "instructionController",
      level3 = "updateInstructionDetails")
  @ExceptionCounted(
      name = "updateInstructionExceptionCount",
      level1 = "uwms-receiving",
      level2 = "instructionController",
      level3 = "updateInstructionDetails")
  public ResponseEntity<InstructionResponse> updateInstruction(
      @PathVariable(value = "instructionId", required = true) Long instructionId,
      @PathVariable(value = "parentTrackingId", required = false) String parentTrackingId,
      @RequestBody UpdateInstructionRequest instructionUpdateRequestFromClient,
      @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    log.info("Request is : {}", instructionUpdateRequestFromClient);
    return new ResponseEntity<>(
        instructionService.updateInstruction(
            instructionId, instructionUpdateRequestFromClient, parentTrackingId, httpHeaders),
        HttpStatus.OK);
  }

  @PutMapping(
      path = "/{instructionId}/complete",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Returns the completed instruction and print jobs when completing an instruction",
      description =
          "This will return a 200 on successful complete and 500 on failure and 400 on validation error.")
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
        in = ParameterIn.HEADER)
  })
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Instruction was successfully completed.")
      })
  @Timed(
      name = "completeInstructionTimed",
      level1 = "uwms-receiving",
      level2 = "instructionController",
      level3 = "completeInstructionDetails")
  @ExceptionCounted(
      name = "completeInstructionExceptionCount",
      level1 = "uwms-receiving",
      level2 = "instructionController",
      level3 = "completeInstructionDetails")
  public ResponseEntity<InstructionResponse> completeInstruction(
      @PathVariable(value = "instructionId", required = true) Long instructionId,
      @RequestBody(required = false) CompleteInstructionRequest completeInstructionRequest,
      @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    InstructionService instructionServiceByFacility =
        tenantSpecificConfigReader.getInstructionServiceByFacility(
            TenantContext.getFacilityNum().toString());
    return new ResponseEntity<>(
        instructionServiceByFacility.completeInstruction(
            instructionId, completeInstructionRequest, httpHeaders),
        HttpStatus.OK);
  }

  @PutMapping(
      path = "/{instructionId}/cancel",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Returns the cancelled instruction",
      description = "This will return a 200 on successful and 500 on failure.")
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
        in = ParameterIn.HEADER)
  })
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Instruction was successfully cancelled.")
      })
  @Timed(
      name = "cancelInstructionTimed",
      level1 = "uwms-receiving",
      level2 = "instructionController",
      level3 = "cancelInstructionDetails")
  @ExceptionCounted(
      name = "cancelInstructionExceptionCount",
      level1 = "uwms-receiving",
      level2 = "instructionController",
      level3 = "cancelInstructionDetails")
  public ResponseEntity<CancelledInstructionResponse> cancelInstruction(
      @PathVariable(value = "instructionId", required = true) Long instructionId,
      @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    CancelledInstructionResponse response = new CancelledInstructionResponse();

    InstructionService instructionServiceByFacility =
        tenantSpecificConfigReader.getInstructionServiceByFacility(
            TenantContext.getFacilityNum().toString());
    InstructionSummary cancelInstruction =
        instructionServiceByFacility.cancelInstruction(instructionId, httpHeaders);
    response.setCancelledInstruction(cancelInstruction);
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  /**
   * This method is for transferring instruction ownership to handle multi-user scenarios.
   *
   * @param transferInstructionRequest
   * @param headers
   * @return
   */
  @PutMapping(path = "/transfer", consumes = "application/json", produces = "application/json")
  @Operation(
      summary = "Transfers the instruction and returns all instruction for the delivery",
      description = "This will return a 200 on successful and 500 on failure.")
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
        in = ParameterIn.HEADER)
  })
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Instructions were successfully transferred.")
      })
  @Timed(
      name = "transferInstructionTimed",
      level1 = "uwms-receiving",
      level2 = "instructionController",
      level3 = "transferInstructions")
  @ExceptionCounted(
      name = "transferInstructionExceptionCount",
      level1 = "uwms-receiving",
      level2 = "instructionController",
      level3 = "transferInstructions")
  @Counted(
      name = "transferInstructionHitCount",
      level1 = "uwms-receiving",
      level2 = "instructionController",
      level3 = "transferInstructions")
  public ResponseEntity<List<InstructionSummary>> transferInstructions(
      @Valid @RequestBody TransferInstructionRequest transferInstructionRequest,
      @RequestHeader HttpHeaders headers)
      throws ReceivingException {
    List<InstructionSummary> instructions =
        instructionService.transferInstructions(transferInstructionRequest, headers);
    return ResponseEntity.ok(instructions);
  }

  /**
   * This method is for transferring instruction ownership to handle multi-user scenarios.
   *
   * @param
   * @param headers
   * @return
   * @return
   */
  @PutMapping(
      path = "/transferMultiple",
      consumes = "application/json",
      produces = "application/json")
  @Operation(
      summary = "Transfers multiple instructions",
      description = "This will return a 202 on successful and 500 on failure.")
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
        in = ParameterIn.HEADER)
  })
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "202",
            description = "Multiple Instructions transfer request accepted.")
      })
  @Timed(
      name = "transferInstructionMultipleTimed",
      level1 = "uwms-receiving",
      level2 = "instructionController",
      level3 = "transferInstructionsMultiple")
  @ExceptionCounted(
      name = "transferInstructionMultipleExceptionCount",
      level1 = "uwms-receiving",
      level2 = "instructionController",
      level3 = "transferInstructionsMultiple")
  @Counted(
      name = "transferInstructioMultiplenHitCount",
      level1 = "uwms-receiving",
      level2 = "instructionController",
      level3 = "transferInstructionsMultiple")
  public ResponseEntity<Object> transferInstructionsMultiple(
      @Valid @RequestBody
          MultipleTransferInstructionsRequestBody multipleTransferInstructionsRequestBody,
      @RequestHeader HttpHeaders headers) {

    instructionService.transferInstructionsMultiple(
        multipleTransferInstructionsRequestBody, headers);

    return ResponseEntity.accepted().build();
  }

  @GetMapping(produces = "application/json")
  @Operation(
      summary = "Return instruction summary based on delivery and instructionSetId",
      description = "This will return a 200")
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
        in = ParameterIn.HEADER)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public List<InstructionSummary> getInstructionSummaryByDeliveryAndInstructionSetId(
      @RequestParam(value = "deliveryNumber", required = true) Long deliveryNumber,
      @RequestParam(value = "instructionSetId", required = false) Long instructionSetId)
      throws ReceivingException {
    InstructionService instructionServiceByFacility =
        tenantSpecificConfigReader.getInstructionServiceByFacility(
            TenantContext.getFacilityNum().toString());
    return instructionServiceByFacility.getInstructionSummaryByDeliveryAndInstructionSetId(
        deliveryNumber, instructionSetId);
  }

  /**
   * This method is for cancelling multiple instructions
   *
   * @param
   * @param headers
   * @return
   * @return
   */
  @PutMapping(
      path = "/cancelMultiple",
      consumes = "application/json",
      produces = "application/json")
  @Operation(
      summary = "Cancelling multiple instructions",
      description = "This will return a 202 on successful and 500 on failure.")
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
        in = ParameterIn.HEADER)
  })
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "202",
            description = "Multiple Instructions cancel request accepted.")
      })
  @Timed(
      name = "cancelInstructionMultipleTimed",
      level1 = "uwms-receiving",
      level2 = "instructionController",
      level3 = "cancelInstructionsMultiple")
  @ExceptionCounted(
      name = "cancelInstructionMultipleExceptionCount",
      level1 = "uwms-receiving",
      level2 = "instructionController",
      level3 = "cancelInstructionsMultiple")
  @Counted(
      name = "cancelInstructioMultiplenHitCount",
      level1 = "uwms-receiving",
      level2 = "instructionController",
      level3 = "cancelInstructionsMultiple")
  public ResponseEntity<Object> cancelInstructionsMultiple(
      @Valid @RequestBody
          MultipleCancelInstructionsRequestBody multipleCancelInstructionsRequestBody,
      @RequestHeader HttpHeaders headers) {

    instructionService.cancelInstructionsMultiple(multipleCancelInstructionsRequestBody, headers);

    return ResponseEntity.accepted().build();
  }

  @PutMapping(
      path = "/bulkComplete",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Accepts to complete multiple instructions ",
      description =
          "This will return a 200 on successful complete and 500 on failure and 400 on validation error.")
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
        in = ParameterIn.HEADER)
  })
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Instruction request is completed.")
      })
  @Timed(
      name = "bulkCompleteInstructionsTimed",
      level1 = "uwms-receiving",
      level2 = "instructionController",
      level3 = "bulkCompleteInstructions")
  @ExceptionCounted(
      name = "bulkCompleteInstructionsExceptionCount",
      level1 = "uwms-receiving",
      level2 = "instructionController",
      level3 = "bulkCompleteInstructions")
  @Counted(
      name = "bulkCompleteInstructionsMultipleHitCount",
      level1 = "uwms-receiving",
      level2 = "instructionController",
      level3 = "bulkCompleteInstructions")
  public CompleteMultipleInstructionResponse bulkCompleteInstructions(
      @Valid @RequestBody(required = true)
          BulkCompleteInstructionRequest bulkCompleteInstructionRequest,
      @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {

    return instructionService.bulkCompleteInstructions(bulkCompleteInstructionRequest, httpHeaders);
  }

  @PutMapping(path = "/{instructionId}/refresh")
  public ResponseEntity<InstructionResponse> refreshInstruction(
      @PathVariable(value = "instructionId", required = true) Long instructionId,
      @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingBadDataException, ReceivingException {
    return new ResponseEntity<>(
        instructionService.refreshInstruction(instructionId, httpHeaders), HttpStatus.OK);
  }

  /**
   * This method is for receive all pallet in loop for gdc market that has single item in an PO
   *
   * @param receiveAllRequestString
   * @param httpHeaders
   * @return ReceiveAllResponse
   */
  @PutMapping(path = "/line/receive")
  @Operation(
      summary = "Returns print jobs when completing an instruction",
      description = "This will return a 200 on successful update and 500 on failure")
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
        in = ParameterIn.HEADER)
  })
  @ApiResponses(
      value = {@ApiResponse(responseCode = "200", description = "Successfully Received pallet")})
  @Timed(
      name = "receiveAllTimed",
      level1 = "uwms-receiving",
      level2 = "instructionController",
      level3 = "receiveAllDetails")
  @ExceptionCounted(
      name = "receiveAllExceptionCount",
      level1 = "uwms-receiving",
      level2 = "instructionController",
      level3 = "receiveAllDetails")
  public ResponseEntity<ReceiveAllResponse> receiveAll(
      @RequestBody @Valid String receiveAllRequestString, @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    InstructionService instructionServiceByFacility =
        tenantSpecificConfigReader.getInstructionServiceByFacility(
            TenantContext.getFacilityNum().toString());
    TenantContext.setAdditionalParams(
        CONTAINER_CREATE_TS, new SimpleDateFormat(DATE_FORMAT_ISO8601).format(new Date()));
    return new ResponseEntity<>(
        instructionServiceByFacility.receiveAll(receiveAllRequestString, httpHeaders),
        HttpStatus.OK);
  }

  @PostMapping(path = "/pendingContainers/{trackingId}")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
      summary = "Creates pending containers if any and outboxes for further processing",
      description = "Will be called by relayer after complete instruction")
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
        in = ParameterIn.HEADER)
  })
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Pending containers if any were created and outboxed")
      })
  @Timed(
      name = "pendingContainersTimed",
      level1 = "uwms-receiving",
      level2 = "instructionController",
      level3 = "outboxPendingContainers")
  @ExceptionCounted(
      name = "pendingContainersExceptionCount",
      level1 = "uwms-receiving",
      level2 = "instructionController",
      level3 = "outboxPendingContainers")
  public void pendingContainers(
      @PathVariable(value = "trackingId") String trackingId, @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    rxCompleteInstructionOutboxHandler.pendingContainers(trackingId, httpHeaders);
  }

  @PostMapping(path = "/eachesDetail")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
      summary = "Calls GDM to fetch eaches details and transforms parent container",
      description = "Will be called by relayer after containers are outboxed")
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
        in = ParameterIn.HEADER)
  })
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Unit details were fetched, transformed and outboxed")
      })
  @Timed(
      name = "eachesDetailTimed",
      level1 = "uwms-receiving",
      level2 = "instructionController",
      level3 = "outboxEachesDetail")
  @ExceptionCounted(
      name = "eachesDetailExceptionCount",
      level1 = "uwms-receiving",
      level2 = "instructionController",
      level3 = "outboxEachesDetail")
  public void eachesDetail(
      @RequestBody Container container, @RequestHeader HttpHeaders httpHeaders) {
    rxCompleteInstructionOutboxHandler.eachesDetail(container, httpHeaders);
  }

  @PostMapping(path = "receive/pack/{trackingId}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Receive pack details",
      description =
          "This will return a 200 on successful,  404 Scanned pack Number is not found in GDM (unlikely), No Allocation found in Label Data, 500 Server error during SSCC Receiving")
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
        name = "trackingId",
        required = true,
        example = "Example: 43432323",
        description = "String",
        in = ParameterIn.PATH)
  })
  @Timed(name = "receivePack", level1 = "uwms-receiving-api", level2 = "instructionController")
  @ExceptionCounted(
      name = "receivePackExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "instructionController")
  public ReceivePackResponse receivePack(
      @PathVariable(value = "trackingId", required = true) String trackingId,
      @RequestHeader HttpHeaders httpHeaders)
      throws Exception {
    return instructionService.receiveDsdcPackByTrackingId(trackingId, httpHeaders);
  }

  @PostMapping(path = "/v2/pending-containers/{trackingId}")
  @ResponseStatus(HttpStatus.OK)
  public void pendingContainersV2(
          @PathVariable(value = ReceivingConstants.CONTAINER_TRACKING_ID) String trackingId,
          @RequestHeader HttpHeaders httpHeaders)
          throws ReceivingException {
    completeInstructionOutboxService.pendingContainers(trackingId, httpHeaders);
  }

  @PostMapping(path = "/v2/eaches-detail")
  @ResponseStatus(HttpStatus.OK)
  public void eachesDetailV2(
          @RequestBody Container container, @RequestHeader HttpHeaders httpHeaders) {
    completeInstructionOutboxService.eachesDetail(container, httpHeaders);
  }
}
