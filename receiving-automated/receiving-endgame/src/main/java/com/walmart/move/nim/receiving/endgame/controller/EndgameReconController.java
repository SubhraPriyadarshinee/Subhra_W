package com.walmart.move.nim.receiving.endgame.controller;

import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.entity.PreLabelData;
import com.walmart.move.nim.receiving.endgame.model.OSDRRequest;
import com.walmart.move.nim.receiving.endgame.service.EndGameLabelingService;
import com.walmart.move.nim.receiving.endgame.service.EndGameOsdrProcessor;
import com.walmart.move.nim.receiving.endgame.service.EndGameSlottingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.List;
import java.util.Optional;
import javax.annotation.Resource;
import javax.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@ConditionalOnExpression("${enable.endgame.app:false}")
@RestController
@RequestMapping("/endgame/recon")
public class EndgameReconController {

  @Resource(name = EndgameConstants.ENDGAME_LABELING_SERVICE)
  private EndGameLabelingService endGameLabelingService;

  @Resource(name = EndgameConstants.ENDGAME_SLOTTING_SERVICE)
  private EndGameSlottingService endGameSlottingService;

  @Resource(name = EndgameConstants.ENDGAME_OSDR_PROCESSOR)
  private EndGameOsdrProcessor endGameOsdrProcessor;

  @GetMapping("/deliveries/{deliveryNumber}")
  @Operation(
      summary = "Get Delivery Details for a given Delivery Number",
      description = "Given the Delivery Number it will return the Delivery Meta Data Details.")
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
    @Parameter(name = "deliveryNumber", description = "Delivery Number", in = ParameterIn.PATH)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Success")})
  @TimeTracing(component = AppComponent.ENDGAME, type = Type.REST, flow = "getDeliveryMetaData")
  public ResponseEntity<DeliveryMetaData> getDeliveryMetaData(
      @PathVariable("deliveryNumber") String deliveryNumber) {
    Optional<DeliveryMetaData> deliveryMetaData =
        endGameLabelingService.findDeliveryMetadataByDeliveryNumber(deliveryNumber);
    deliveryMetaData.orElseThrow(
        () ->
            new ReceivingDataNotFoundException(
                ExceptionCodes.DELIVERY_METADATA_NOT_FOUND,
                String.format(EndgameConstants.METADATA_NOT_FOUND_ERROR_MSG, deliveryNumber)));
    return new ResponseEntity(deliveryMetaData, HttpStatus.OK);
  }

  @GetMapping("/deliveries/{deliveryNumber}/tcls")
  @Operation(
      summary = "Get the label Details for a given Delivery Number",
      description = "Given the Delivery Number it will return the label details.")
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
    @Parameter(name = "deliveryNumber", description = "Delivery Number", in = ParameterIn.PATH)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Success")})
  @TimeTracing(component = AppComponent.ENDGAME, type = Type.REST, flow = "getTCLsByDeliveryNumber")
  public List<PreLabelData> getTCLsByDeliveryNumber(
      @PathVariable("deliveryNumber") long deliveryNumber) {
    return endGameLabelingService.findTCLsByDeliveryNumber(deliveryNumber);
  }

  @PostMapping(path = "/osdr", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Process PO receipts and sent osdr details",
      description = "Process the deliver with OSDR details.")
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
      description = "OSDR to be processed",
      required = true)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Success")})
  @TimeTracing(component = AppComponent.ENDGAME, type = Type.REST, flow = "processOSDR")
  public ResponseEntity<String> processOSDR(
      @Valid @RequestBody(required = true) OSDRRequest osdrRequest) {
    if (!osdrRequest.isValid()) {
      throw new ReceivingBadDataException(
          String.valueOf(HttpStatus.BAD_REQUEST.value()),
          "Please provide either DeliveryNos or POs");
    }
    endGameOsdrProcessor.processOSDR(osdrRequest);
    return new ResponseEntity(EndgameConstants.SUCCESS, HttpStatus.OK);
  }
}
