package com.walmart.move.nim.receiving.endgame.controller;

import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.DeliveryStatusPublisher;
import com.walmart.move.nim.receiving.core.common.LabelType;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.endgame.common.LabelUtils;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.constants.LabelGenMode;
import com.walmart.move.nim.receiving.endgame.entity.PreLabelData;
import com.walmart.move.nim.receiving.endgame.model.*;
import com.walmart.move.nim.receiving.endgame.service.EndGameLabelingService;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import javax.annotation.Resource;
import javax.validation.Valid;
import org.apache.commons.lang3.EnumUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@ConditionalOnExpression("${enable.endgame.app:false}")
@RestController
@RequestMapping("endgame/labels")
@Tag(name = "EndGame Labelling Service", description = "Endgame Label")
public class LabelController {

  private static final Logger LOGGER = LoggerFactory.getLogger(LabelController.class);
  @Autowired private EndGameLabelingService labelingService;

  @Autowired private DeliveryStatusPublisher deliveryStatusPublisher;

  @Resource(name = EndgameConstants.ENDGAME_DELIVERY_SERVICE)
  private DeliveryService deliveryService;

  /**
   * This API generates labels for the given label request
   *
   * @param labelRequest LabelRequest
   * @param headers HttpHeaders
   * @param isPublishHawkeye isPublishHawkeye
   */
  @PostMapping(path = "/request", produces = "application/json")
  @Operation(
      summary = "Return list of EndGame specific labels as response",
      description = "This will return a 200")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true)
  })
  @Timed(name = "Manual-TCL-Gen", level1 = "uwms-receiving", level2 = "Manual-TCL-Gen")
  @ExceptionCounted(
      name = "Manual-TCL-Gen-Exception",
      level1 = "uwms-receiving",
      level2 = "Manual-TCL-Gen-Exception")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  @TimeTracing(component = AppComponent.ENDGAME, type = Type.REST, flow = "generateLabel")
  public ResponseEntity<LabelResponse> generateLabel(
      @RequestBody @Valid LabelRequest labelRequest,
      @RequestHeader HttpHeaders headers,
      @RequestParam(name = "isPublishHawkeye", defaultValue = "true") boolean isPublishHawkeye) {
    Delivery delivery;
    try {
      delivery =
          deliveryService.getGDMData(
              DeliveryUpdateMessage.builder()
                  .deliveryNumber(String.valueOf(labelRequest.getDeliveryNumber()))
                  .build());
    } catch (ReceivingException exception) {
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.DELIVERY_NOT_FOUND,
          String.format(
              "Delivery = %s not found while generating labels.", labelRequest.getDeliveryNumber()),
          exception);
    }
    LabelRequestVO labelRequestVO =
        LabelRequestVO.builder()
            .labelGenMode(LabelGenMode.valueOf(labelRequest.getLabelGenMode()))
            .quantity(labelRequest.getNumberOfLabels())
            .type(LabelType.valueOf(labelRequest.getLabelType()))
            .deliveryNumber(String.valueOf(delivery.getDeliveryNumber()))
            .door(delivery.getDoorNumber())
            .trailerId(delivery.getLoadInformation().getTrailerInformation().getTrailerId())
            .carrierName(delivery.getCarrierName())
            .carrierScanCode(delivery.getLoadInformation().getTrailerInformation().getScacCode())
            .billCode(delivery.getPurchaseOrders().get(0).getFreightTermCode())
            .build();

    EndGameLabelData labelData = labelingService.generateLabel(labelRequestVO);
    labelingService.persistLabel(labelData);
    if (isPublishHawkeye) {
      labelingService.send(labelData);
      LOGGER.info("Manual Label successfully sent to hawkeye {} ", labelData);
    }
    LabelResponse labelResponse =
        LabelUtils.generateLabelResponse(labelRequest, labelData, headers);
    return new ResponseEntity<>(labelResponse, HttpStatus.OK);
  }

  /**
   * This API fetches the PreLabelData Summary for the given DeliveryNumber
   *
   * @param deliveryNumber DeliverNumber
   * @param deliveryStatus DeliverStatus
   * @param requestHeaders RequestHeader
   * @param labelType LabelType
   * @param currIndex CurrentIndex
   * @param pageSize PageSize
   * @return PreLabelData with responseHeaders
   */
  @GetMapping(path = "/{deliveryNumber}", produces = "application/json")
  @Operation(
      summary = "Return list of EndGame specific labels as response",
      description = "This will return a 200")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true),
    @Parameter(name = "WMT-CorrelationId", required = true)
  })
  @Timed(name = "GetLabelSummary", level1 = "uwms-receiving", level2 = "GetLabelSummary")
  @ExceptionCounted(
      name = "GetLabelSummary-Exception",
      level1 = "uwms-receiving",
      level2 = "GetLabelSummary-Exception")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  @TimeTracing(component = AppComponent.ENDGAME, type = Type.REST, flow = "getLabelSummary")
  public ResponseEntity<List<PreLabelData>> getLabelSummary(
      @RequestHeader HttpHeaders requestHeaders,
      @PathVariable @Valid Long deliveryNumber,
      @RequestParam(name = "deliveryStatus") String deliveryStatus,
      @RequestParam(name = "label-type", defaultValue = EndgameConstants.ALL) String labelType,
      @RequestParam(name = "curr-index", defaultValue = "1") Integer currIndex,
      @RequestParam(name = "page-size", defaultValue = "10") Integer pageSize) {

    Page<PreLabelData> labelData =
        labelType.equalsIgnoreCase(EndgameConstants.ALL)
            ? labelingService.findByDeliveryNumber(deliveryNumber, currIndex, pageSize)
            : findByDeliveryNumberAndLabelType(deliveryNumber, labelType, currIndex, pageSize);

    LOGGER.info(
        "Getting PreLabelData for DeliveryNumber: {} , DeliveryStatus: {} and LabelType: {}",
        deliveryNumber,
        deliveryStatus,
        labelType);

    HttpHeaders responseHeaders =
        buildLabelSummaryResponseHeader(
            labelData.getTotalElements(), currIndex, pageSize, deliveryNumber, labelType);
    // To publish OPEN to GDM if status is ARV
    if (deliveryStatus.equalsIgnoreCase(DeliveryStatus.ARV.toString())) {
      LOGGER.info("Publishing DeliveryNumber: {} to GDM", deliveryNumber);
      deliveryStatusPublisher.publishDeliveryStatus(
          deliveryNumber,
          DeliveryStatus.OPEN.toString(),
          null,
          ReceivingUtils.getForwardablHeader(requestHeaders));
    }
    LOGGER.info("Label Summary response {} ", labelData);

    return new ResponseEntity<>(labelData.getContent(), responseHeaders, HttpStatus.OK);
  }

  /**
   * This method builds the ResponseHeader which contains tcl-count,
   * tpl-count,total-count,curr-index,page-size
   *
   * @param totalElements totalElements
   * @param currIndex currentIndex
   * @param pageNum PageNumber
   * @param labelType labelType
   * @return HttpHeaders
   */
  private HttpHeaders buildLabelSummaryResponseHeader(
      Long totalElements,
      Integer currIndex,
      Integer pageNum,
      Long deliveryNumber,
      String labelType) {
    HttpHeaders responseHeaders = new HttpHeaders();

    List<LabelSummary> labelCounts = labelingService.findLabelSummary(deliveryNumber, labelType);

    for (LabelSummary eachLabelSummary : labelCounts) {
      responseHeaders.set(
          eachLabelSummary.getType().getType().toLowerCase().concat("-count"),
          String.valueOf(eachLabelSummary.getCount()));
    }

    responseHeaders.set("total-count", String.valueOf(totalElements));
    responseHeaders.set("curr-index", String.valueOf(currIndex.intValue()));
    responseHeaders.set("page-size", String.valueOf(pageNum.intValue()));
    return responseHeaders;
  }

  /**
   * This method checks for valid LabelType if not throws Exception. If it is a valid labelType then
   * it fetches the PreLabel data based on deliveryNumber and LabelType
   *
   * @param deliveryNumber DeliveryNumber
   * @param labelType labelType
   * @param currIndex currIndex
   * @param pageSize pageSize
   * @return Page data
   */
  private Page<PreLabelData> findByDeliveryNumberAndLabelType(
      long deliveryNumber, String labelType, Integer currIndex, Integer pageSize) {
    if (!EnumUtils.isValidEnum(LabelType.class, labelType)) {
      LOGGER.error("Invalid LabelType {}", labelType);
      throw new ReceivingBadDataException(ExceptionCodes.INVALID_DATA, "LabelType is Not Valid");
    }
    return labelingService.findByDeliveryNumberAndLabelType(
        deliveryNumber, LabelType.valueOf(labelType), currIndex, pageSize);
  }
}
