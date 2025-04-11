package com.walmart.move.nim.receiving.endgame.controller;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.DeliveryDoorSummary;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.model.AttachPurchaseOrderRequest;
import com.walmart.move.nim.receiving.endgame.model.Location;
import com.walmart.move.nim.receiving.endgame.service.EndGameAttachPOService;
import com.walmart.move.nim.receiving.endgame.service.EndGameDeliveryService;
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
import javax.annotation.Resource;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ConditionalOnExpression("${enable.endgame.app:false}")
@RestController
@RequestMapping("endgame/delivery")
@Tag(name = "EndGame Delivery Service", description = "Endgame Delivery")
public class EndgameDeliveryController {

  @Autowired private EndGameAttachPOService endGameAttachPOService;

  @Resource(name = EndgameConstants.ENDGAME_DELIVERY_SERVICE)
  private EndGameDeliveryService endGameDeliveryService;

  /**
   * To be consumed by the Outbox. This HTTP endpoint attach PurchaseOrder to delivery
   *
   * @param attachPurchaseOrderRequest
   * @return
   */
  @PostMapping(path = "/attach/purchase-order", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Add Purchase Orders to Delivery", description = "This will return a 200")
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
        name = "WMT-CorrelationId",
        required = true,
        example = "Example: a1-b2-c3-d4-e6",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Responds with success or failure of the API")
      })
  @Timed(
      name = "attachPOsToDeliveryTimed",
      level1 = "uwms-receiving",
      level2 = "endgameDeliveryController",
      level3 = "attachPOsToDelivery")
  @ExceptionCounted(
      name = "attachPOsToDeliveryExceptionCount",
      level1 = "uwms-receiving",
      level2 = "endgameDeliveryController",
      level3 = "attachPOsToDelivery")
  public ResponseEntity<HttpStatus> attachPOToDelivery(
      @RequestBody @Valid AttachPurchaseOrderRequest attachPurchaseOrderRequest,
      @RequestHeader HttpHeaders headers) {

    try {
      endGameAttachPOService.attachPOsToDelivery(attachPurchaseOrderRequest, headers);
    } catch (ReceivingException exception) {
      return new ResponseEntity(exception.getHttpStatus());
    }
    return new ResponseEntity(HttpStatus.OK);
  }

  @Operation(
      summary = "Return door status based on door number",
      description =
          "This service will return door availability status based on the given door number")
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
        example = "Example: 32612",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        required = true,
        example = "Example: sysadmin",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  @Timed(
      name = "getDeliveryDoorStatusTimed",
      level1 = "uwms-receiving",
      level2 = "deliveryController",
      level3 = "getDeliverySummary")
  @ExceptionCounted(
      name = "getDeliveryDoorStatusExceptionCount",
      level1 = "uwms-receiving",
      level2 = "deliveryController",
      level3 = "getDeliverySummary")
  @Counted(
      name = "getDeliveryDoorStatusHitCount",
      level1 = "uwms-receiving",
      level2 = "deliveryController",
      level3 = "getDeliverySummary")
  @GetMapping(path = "/door/{doorNumber}/status", produces = "application/json")
  public DeliveryDoorSummary getDeliveryDoorStatus(
      @PathVariable(value = "doorNumber") String doorNumber, @RequestHeader HttpHeaders headers)
      throws ReceivingException {
    return endGameDeliveryService.getDoorStatus(doorNumber);
  }

  @PostMapping(path = "/process", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Process pending Delivery", description = "This will return a 200")
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
        name = "WMT-CorrelationId",
        required = true,
        example = "Example: a1-b2-c3-d4-e6",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Responds with success or failure of the API")
      })
  @Timed(
      name = "processDeliveryEventTimed",
      level1 = "uwms-receiving",
      level2 = "endgameDeliveryController",
      level3 = "processDeliveryEvent")
  @ExceptionCounted(
      name = "processDeliveryEventExceptionCount",
      level1 = "uwms-receiving",
      level2 = "endgameDeliveryController",
      level3 = "processDeliveryEvent")
  public ResponseEntity<HttpStatus> processDeliveryEvent(
      @RequestBody @Valid Location location, @RequestHeader HttpHeaders headers)
      throws ReceivingException {
    return endGameDeliveryService.processPendingDeliveryEvent(location);
  }
}
