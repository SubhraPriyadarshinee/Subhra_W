package com.walmart.move.nim.receiving.rx.controller;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.CORRELATION_ID_HEADER_KEY;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.model.ContainerSummary;
import com.walmart.move.nim.receiving.core.model.ContainerUpdateResponse;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.model.SerializedContainerUpdateRequest;
import com.walmart.move.nim.receiving.rx.service.RxGetContainerRequestHandler;
import com.walmart.move.nim.receiving.rx.service.RxUpdateSerializedContainerQtyRequestHandler;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.Arrays;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

/**
 * Containers controller to expose container resource
 *
 * @author v0k00fe
 */
@RestController
@RequestMapping("containers")
public class RxContainerController {

  private static final Logger log = LoggerFactory.getLogger(RxContainerController.class);

  @Autowired private ContainerService containerService;
  @Autowired private RxGetContainerRequestHandler rxGetContainerRequestHandler;

  @Autowired
  private RxUpdateSerializedContainerQtyRequestHandler rxUpdateSerializedContainerQtyRequestHandler;

  @DeleteMapping(
      consumes = MediaType.APPLICATION_JSON_UTF8_VALUE,
      produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
  @Operation(
      summary = "Returns the request",
      description = "This will return 200 with specific failures")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "SUCCESS")})
  @Timed(
      name = "deleteContainerTimed",
      level1 = "uwms-receiving",
      level2 = "containerController",
      level3 = "deleteContainer")
  @ExceptionCounted(
      name = "deleteContainerExceptionCount",
      level1 = "uwms-receiving",
      level2 = "containerController",
      level3 = "deleteContainer")
  public void deleteContainer(
      @RequestParam(value = "trackingIds", required = true) @Valid @NotBlank String trackingIds,
      @RequestParam(value = "hash", required = true) @Valid @NotBlank String hash,
      @RequestHeader HttpHeaders httpHeaders) {

    List<String> trackingIdList = Arrays.asList(StringUtils.split(trackingIds, ","));
    if (CollectionUtils.isEmpty(trackingIdList)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.TRACKING_ID_CANNOT_BE_EMPTY, RxConstants.TRACKING_ID_CANNOT_BE_EMPTY);
    }
    String constructedHash =
        DigestUtils.md5Hex("trackingIds=" + trackingIds + "receiving-secret").toUpperCase();
    if (!constructedHash.equals(hash)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_HASH_VALUE_SENT, RxConstants.INVALID_HASH_VALUE_SENT);
    }

    containerService.deleteContainers(trackingIdList, httpHeaders);
  }

  /**
   * Find Container by InstructionId, Gtin, Serial and Lot Number
   *
   * @param instructionId
   * @param lotNumber
   * @param serial
   * @return
   * @throws ReceivingException
   */
  @GetMapping(path = "/summary", produces = "application/json")
  @Operation(summary = "Return Container Summary List based on request parameters")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = false)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "success")})
  @Timed(
      name = "getContainersSummaryTimed",
      level1 = "uwms-receiving-api",
      level2 = "containerController",
      level3 = "getContainersSummary")
  @ExceptionCounted(
      name = "getContainersSummaryExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "containerController",
      level3 = "getContainersSummary")
  public List<ContainerSummary> getContainersSummary(
      @RequestParam(value = "instructionId", required = true) long instructionId,
      @RequestParam(value = "serial", required = true) String serial,
      @RequestParam(value = "lotNumber", required = true) String lotNumber) {

    return rxGetContainerRequestHandler.getContainersSummary(instructionId, serial, lotNumber);
  }

  /**
   * update quantity of container by lpn/tracking id and return Updated Container and its associated
   * print label
   *
   * @param trackingId
   * @return ContainerUpdateResponse
   * @throws ReceivingException
   */
  @PostMapping(
      path = "/{trackingId}/adjust",
      produces = "application/json",
      params = {"serializedContainer"})
  @Operation(
      summary = "Adjusts Container Item Quantity and returns print job based on LPN/TrackingId")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = false)
  })
  @ApiResponses(
      value = {@ApiResponse(responseCode = "200", description = "successfully updated quantity")})
  @Timed(
      name = "updateQuantityByTrackingIdTimed",
      level1 = "uwms-receiving-api",
      level2 = "containerController",
      level3 = "updateQuantityByTrackingId")
  @ExceptionCounted(
      name = "getDeliveryExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "containerController",
      level3 = "updateQuantityByTrackingId")
  public ContainerUpdateResponse updateQuantityByTrackingId(
      @PathVariable(value = "trackingId") String trackingId,
      @Valid @RequestBody SerializedContainerUpdateRequest containerUpdateRequest,
      @RequestParam(value = "serializedContainer", defaultValue = "true")
          boolean serializedContainer,
      @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    log.info(
        "step:0 cId={}, Requested to adjust Quantity for lpn={} containerUpdateRequest={}",
        httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY),
        trackingId,
        containerUpdateRequest);
    return rxUpdateSerializedContainerQtyRequestHandler.updateQuantityByTrackingId(
        trackingId, containerUpdateRequest, httpHeaders);
  }
}
