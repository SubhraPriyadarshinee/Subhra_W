package com.walmart.move.nim.receiving.endgame.controller;

import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.model.EndgameReceivingRequest;
import com.walmart.move.nim.receiving.endgame.service.EndGameReceivingService;
import com.walmart.move.nim.receiving.endgame.service.EndgameContainerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import javax.annotation.Resource;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@ConditionalOnExpression("${enable.endgame.app:false}")
@RestController
@RequestMapping("/endgame/receiving")
public class EndgameReceivingController {

  private static final Logger LOGGER = LoggerFactory.getLogger(EndgameReceivingController.class);

  @Autowired private EndgameContainerService endgameContainerService;

  @Resource(name = EndgameConstants.ENDGAME_MANUAL_RECEIVING_SERVICE)
  private EndGameReceivingService endGameReceivingService;

  @PostMapping
  @Operation(summary = "Receiving the containers", description = "Process the containers Request")
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
  @TimeTracing(component = AppComponent.ENDGAME, type = Type.REST, flow = "receive container")
  public ResponseEntity receiveContainer(
      @RequestBody @Valid EndgameReceivingRequest receivingRequest) {
    endGameReceivingService.receiveContainer(receivingRequest);
    return new ResponseEntity<>(HttpStatus.CREATED);
  }

  @PostMapping("/container/updates")
  @Operation(summary = "Update container information", description = "Process the container updates request")
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
          description = "Container updates request to be processed", required = true)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Success")})
  @TimeTracing(component = AppComponent.ENDGAME, type = Type.REST, flow = "update container")
  public ResponseEntity receiveContainerUpdates(@RequestBody @NotNull String payload) {
    LOGGER.info("Received container updates request: {}", payload);
    endgameContainerService.processContainerUpdates(payload);
    LOGGER.info("Processed container updates request: {}", payload);
    return new ResponseEntity<>(HttpStatus.OK);
  }

}
