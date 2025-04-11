package com.walmart.move.nim.receiving.endgame.controller;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.sanitize;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.DECANT_API;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.fasterxml.jackson.core.type.TypeReference;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.endgame.constants.DivertStatus;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.constants.LabelStatus;
import com.walmart.move.nim.receiving.endgame.entity.PreLabelData;
import com.walmart.move.nim.receiving.endgame.model.ReceiveVendorPack;
import com.walmart.move.nim.receiving.endgame.model.ReceivingRequest;
import com.walmart.move.nim.receiving.endgame.service.EndGameDeliveryService;
import com.walmart.move.nim.receiving.endgame.service.EndGameLabelingService;
import com.walmart.move.nim.receiving.endgame.service.EndGameReceivingService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import javax.annotation.Resource;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ConditionalOnExpression("${enable.endgame.app:false}")
@RestController
@RequestMapping("/endgame/tcls")
public class TCLController {

  @Autowired private EndGameLabelingService endGameLabelingService;
  @Autowired private EndGameDeliveryService endGameDeliveryService;

  @Resource(name = EndgameConstants.ENDGAME_MANUAL_RECEIVING_SERVICE)
  private EndGameReceivingService endGameReceivingService;

  @Resource(name = EndgameConstants.ENDGAME_DIVERT_ACK_EVENT_PROCESSOR)
  private EventProcessor eventProcessor;

  @GetMapping("/status/{tcl}")
  @Operation(
      summary = "Get the TCL Status",
      description = "Get the TCL Status for the given Label Number")
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
        example = "Example: sysAdmin",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(name = "tcl", description = "TCL Label Number", in = ParameterIn.PATH)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Success")})
  @TimeTracing(component = AppComponent.ENDGAME, type = Type.REST, flow = "getTCLStatus")
  public String getTCLStatus(@PathVariable("tcl") String tcl) {
    String finalTcl = sanitize(tcl);
    PreLabelData preLabelData =
        endGameLabelingService
            .findByTcl(finalTcl)
            .orElseThrow(
                () ->
                    new ReceivingDataNotFoundException(
                        ExceptionCodes.TCL_NOT_FOUND,
                        String.format(EndgameConstants.TCL_NOT_FOUND_ERROR_MSG, finalTcl)));
    return preLabelData.getStatus().getStatus();
  }

  @GetMapping("/{tcl}")
  @Operation(
      summary = "Get the TCL Details",
      description = "Get the TCL Details for the given Label Number")
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
        example = "Example: sysAdmin",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(name = "tcl", description = "TCL Label Number", in = ParameterIn.PATH)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Success")})
  @TimeTracing(component = AppComponent.ENDGAME, type = Type.REST, flow = "getTCLDetails")
  public ResponseEntity<PreLabelData> getTCLDetails(@PathVariable("tcl") String tcl) {
    PreLabelData preLabelData =
        endGameLabelingService
            .findByTcl(tcl)
            .orElseThrow(
                () ->
                    new ReceivingDataNotFoundException(
                        ExceptionCodes.TCL_NOT_FOUND,
                        String.format(EndgameConstants.TCL_NOT_FOUND_ERROR_MSG, tcl)));

    return new ResponseEntity<>(preLabelData, HttpStatus.OK);
  }

  @PostMapping("/receive")
  @Operation(summary = "Receiving the TCL", description = "Process the Receiving TCL Request")
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
        example = "Example: sysAdmin",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      description = "Receiving Request Object to be processed",
      required = true)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Success")})
  @TimeTracing(component = AppComponent.ENDGAME, type = Type.REST, flow = "receiveTCL")
  public ResponseEntity<ReceiveVendorPack> receiveTCL(
      @RequestBody @Valid ReceivingRequest receivingRequest) {
    PreLabelData preLabelData =
        endGameLabelingService
            .findByTcl(receivingRequest.getTrailerCaseLabel())
            .orElseThrow(
                () ->
                    new ReceivingDataNotFoundException(
                        ExceptionCodes.TCL_NOT_FOUND,
                        String.format(
                            EndgameConstants.TCL_NOT_FOUND_ERROR_MSG,
                            receivingRequest.getTrailerCaseLabel())));
    receivingRequest.setDeliveryNumber(preLabelData.getDeliveryNumber());
    receivingRequest.setPreLabelData(preLabelData);
    if (StringUtils.hasLength(preLabelData.getCaseUpc())
        && StringUtils.hasLength(preLabelData.getDiverAckEvent())) {
      ReceivingRequest tclReceivingRequest =
          ReceivingUtils.convertStringToObject(
              preLabelData.getDiverAckEvent(), new TypeReference<ReceivingRequest>() {});
      receivingRequest.setWeight(tclReceivingRequest.getWeight());
      receivingRequest.setWeightUnitOfMeasure(tclReceivingRequest.getWeightUnitOfMeasure());
    }
    /*
    Divert is set as Decant as the API will be used by decant-api for manual receiving
    */
    if (isNull(receivingRequest.getDiverted())) {
      receivingRequest.setDiverted(DivertStatus.DECANT);
    }
    if (isNull(receivingRequest.getDiverted())) {
      receivingRequest.setDiverted(DivertStatus.DECANT);
    }
    if (isNull(receivingRequest.getRequestOriginator())) {
      receivingRequest.setRequestOriginator(DECANT_API);
    }
    endGameDeliveryService.publishWorkingEventIfApplicable(receivingRequest.getDeliveryNumber());
    ReceiveVendorPack receiveVendorPack;
    if (receivingRequest.getIsMultiSKU()) {
      receiveVendorPack = endGameReceivingService.receiveMultiSKUContainer(receivingRequest);
    } else {
      endGameReceivingService.verifyContainerReceivable(receivingRequest.getTrailerCaseLabel());
      receiveVendorPack = endGameReceivingService.receiveVendorPack(receivingRequest);
    }
    if (nonNull(receiveVendorPack.getContainer())) {
      if (!preLabelData.getStatus().getStatus().equalsIgnoreCase(LabelStatus.SCANNED.getStatus())) {
        preLabelData.setStatus(LabelStatus.SCANNED);
        endGameLabelingService.saveOrUpdateLabel(preLabelData);
      }
      return new ResponseEntity<>(receiveVendorPack, HttpStatus.CREATED);
    } else {
      return new ResponseEntity<>(receiveVendorPack, HttpStatus.OK);
    }
  }

  @PostMapping("/auto/receive")
  @Operation(summary = "Receiving the TCL", description = "Process the Scan tunnel TCL Request")
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
        example = "Example: sysAdmin",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      description = "Scan Request Object to be processed",
      required = true)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Success")})
  @TimeTracing(component = AppComponent.ENDGAME, type = Type.REST, flow = "receiveTCL")
  public ResponseEntity receiveTCLScanRequest(
      @RequestBody @Valid ReceivingRequest receivingRequest) {

    try {
      setSCTHeaders();
      eventProcessor.processEvent(receivingRequest);
    } catch (ReceivingException re) {
      throw new ReceivingInternalException(
          ExceptionCodes.UNABLE_TO_CREATE_CONTAINER,
          String.format(
              EndgameConstants.UNABLE_TO_CREATE_CONTAINER_ERROR_MSG,
              receivingRequest.getTrailerCaseLabel()),
          re);
    }
    return new ResponseEntity<>(HttpStatus.OK);
  }

  private void setSCTHeaders() {
    TenantContext.setAdditionalParams("caseAutoReceived", Boolean.TRUE);
  }
}
