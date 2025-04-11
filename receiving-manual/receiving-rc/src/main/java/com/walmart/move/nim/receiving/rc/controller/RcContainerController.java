package com.walmart.move.nim.receiving.rc.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.rc.contants.RcConstants;
import com.walmart.move.nim.receiving.rc.entity.ContainerRLog;
import com.walmart.move.nim.receiving.rc.entity.ReceivingWorkflow;
import com.walmart.move.nim.receiving.rc.model.container.RcContainerDetails;
import com.walmart.move.nim.receiving.rc.model.dto.request.PublishContainerRequest;
import com.walmart.move.nim.receiving.rc.model.dto.request.ReceiveContainerRequest;
import com.walmart.move.nim.receiving.rc.model.dto.request.UpdateContainerRequest;
import com.walmart.move.nim.receiving.rc.model.dto.request.UpdateReturnOrderDataRequest;
import com.walmart.move.nim.receiving.rc.model.dto.response.ReceiveContainerResponse;
import com.walmart.move.nim.receiving.rc.service.RcContainerService;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@ConditionalOnExpression("${enable.rc.app:false}")
@RestController
@RequestMapping(RcConstants.RETURNS_CONTAINER_URI)
@Tag(name = "Return center Container Service", description = "To expose container resource")
public class RcContainerController {

  private static final Logger LOGGER = LoggerFactory.getLogger(RcContainerController.class);

  @Autowired private RcContainerService rcContainerService;
  private final Gson gsonWithDateAdapter;

  public RcContainerController() {
    gsonWithDateAdapter =
        new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
  }

  @PostMapping(path = RcConstants.RECEIVE_URI, produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
  @Operation(
      summary = "This will create Container for a return.",
      description = "This will return a 200 on successful.")
  @Parameters({
    @Parameter(name = RcConstants.TENENT_COUNTRY_CODE, required = true),
    @Parameter(name = RcConstants.TENENT_FACLITYNUM, required = true),
    @Parameter(name = RcConstants.USER_ID_HEADER_KEY, required = true),
    @Parameter(name = RcConstants.CORRELATION_ID_HEADER_KEY, required = true)
  })
  @Timed(
      name = "rcReceiveContainerTimed",
      level1 = "uwms-receiving",
      level2 = "rcContainerController",
      level3 = "rcReceiveContainer")
  @ExceptionCounted(
      name = "rcReceiveContainerExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rcContainerController",
      level3 = "rcReceiveContainer")
  @TimeTracing(component = AppComponent.RC, type = Type.REST, flow = "ReceiveContainer")
  public ResponseEntity<ReceiveContainerResponse> receiveContainer(
      @RequestBody ReceiveContainerRequest receiveContainerRequest,
      @RequestHeader HttpHeaders httpHeaders) {
    RcContainerDetails rcContainerDetails =
        rcContainerService.receiveContainer(receiveContainerRequest, httpHeaders);
    return new ResponseEntity<>(
        ReceiveContainerResponse.builder()
            .id(rcContainerDetails.getContainerRLog().getId())
            .trackingId(rcContainerDetails.getContainerRLog().getTrackingId())
            .workflowId(
                Optional.ofNullable(rcContainerDetails.getReceivingWorkflow())
                    .map(ReceivingWorkflow::getWorkflowId)
                    .orElse(null))
            .build(),
        HttpStatus.CREATED);
  }

  @PatchMapping(
      path = RcConstants.UPDATE_BY_TRACKING_ID_URI,
      produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
  @Operation(
      summary = "This will update a particular container.",
      description = "This will return a 200 on successful.")
  @Parameters({
    @Parameter(name = RcConstants.TENENT_COUNTRY_CODE, required = true),
    @Parameter(name = RcConstants.TENENT_FACLITYNUM, required = true),
    @Parameter(name = RcConstants.USER_ID_HEADER_KEY, required = true),
    @Parameter(name = RcConstants.CORRELATION_ID_HEADER_KEY, required = true)
  })
  @Timed(
      name = "rcUpdateContainerTimed",
      level1 = "uwms-receiving",
      level2 = "rcContainerController",
      level3 = "rcUpdateContainer")
  @ExceptionCounted(
      name = "rcUpdateContainerExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rcContainerController",
      level3 = "rcUpdateContainer")
  @TimeTracing(component = AppComponent.RC, type = Type.REST, flow = "UpdateContainer")
  public ResponseEntity<String> updateContainer(
      @PathVariable(value = "trackingId") @NotEmpty String trackingId,
      @RequestBody @NotNull UpdateContainerRequest updateContainerRequest,
      @RequestHeader HttpHeaders httpHeaders) {
    RcContainerDetails rcContainerDetails =
        rcContainerService.updateContainer(trackingId, updateContainerRequest, httpHeaders);
    return new ResponseEntity<>("Successfully updated!", HttpStatus.OK);
  }

  @PostMapping(path = RcConstants.PUBLISH_URI, produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
  @Operation(
      summary = "This will publish container events.",
      description = "This will return a 200 on successful.")
  @Parameters({
    @Parameter(name = RcConstants.TENENT_COUNTRY_CODE, required = true),
    @Parameter(name = RcConstants.TENENT_FACLITYNUM, required = true),
    @Parameter(name = RcConstants.USER_ID_HEADER_KEY, required = true),
    @Parameter(name = RcConstants.CORRELATION_ID_HEADER_KEY, required = true)
  })
  @Timed(
      name = "rcPublishContainersTimed",
      level1 = "uwms-receiving",
      level2 = "rcContainerController",
      level3 = "rcPublishContainers")
  @ExceptionCounted(
      name = "rcPublishContainersExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rcContainerController",
      level3 = "rcPublishContainers")
  @TimeTracing(component = AppComponent.RC, type = Type.REST, flow = "PublishContainers")
  public ResponseEntity<String> publishContainers(
      @RequestBody @Valid PublishContainerRequest publishContainerRequest,
      @RequestHeader HttpHeaders httpHeaders) {
    publishContainerRequest
        .getPublishContainerItemList()
        .forEach(
            publishContainerItem -> {
              LOGGER.info(
                  "Starting to publish event for request: {}",
                  gsonWithDateAdapter.toJson(publishContainerItem));
              rcContainerService.publishContainer(
                  publishContainerItem.getRcContainerDetails(),
                  httpHeaders,
                  publishContainerItem.getActionType(),
                  publishContainerItem.getIgnoreSct(),
                  publishContainerItem.getIgnoreRap(),
                  publishContainerItem.getIgnoreWfs());
            });
    return new ResponseEntity<>("Successful!", HttpStatus.OK);
  }

  @Operation(
      summary = "This will fetch latest received container by gtin for a return.",
      description = "This will return a 200 on successful.")
  @Parameters({
    @Parameter(name = RcConstants.TENENT_COUNTRY_CODE, required = true),
    @Parameter(name = RcConstants.TENENT_FACLITYNUM, required = true),
    @Parameter(name = RcConstants.USER_ID_HEADER_KEY, required = true),
    @Parameter(name = RcConstants.CORRELATION_ID_HEADER_KEY, required = true)
  })
  @Timed(
      name = "rcGetContainerByGtinTimed",
      level1 = "uwms-receiving",
      level2 = "rcContainerController",
      level3 = "rcGetContainerByGtin")
  @ExceptionCounted(
      name = "rcGetContainerByGtinExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rcContainerController",
      level3 = "rcGetContainerByGtin")
  @TimeTracing(component = AppComponent.RC, type = Type.REST, flow = "GetContainerByGtin")
  @GetMapping(path = RcConstants.RETURNS_CONTAINER_BY_GTIN_URI, produces = "application/json")
  public ResponseEntity<ContainerRLog> getContainerByGtin(
      @PathVariable(value = "gtin") String gtin,
      @RequestParam(value = "dispositionType", required = false) String dispositionType) {
    return new ResponseEntity<>(
        rcContainerService.getLatestReceivedContainerByGtin(gtin, dispositionType), HttpStatus.OK);
  }

  @Operation(
      summary = "This will fetch received container by tracking ID.",
      description = "This will return a 200 on successful.")
  @Parameters({
    @Parameter(name = RcConstants.TENENT_COUNTRY_CODE, required = true),
    @Parameter(name = RcConstants.TENENT_FACLITYNUM, required = true),
    @Parameter(name = RcConstants.USER_ID_HEADER_KEY, required = true),
    @Parameter(name = RcConstants.CORRELATION_ID_HEADER_KEY, required = true)
  })
  @Timed(
      name = "rcGetContainerByTrackingId",
      level1 = "uwms-receiving",
      level2 = "rcContainerController",
      level3 = "rcGetContainerByTrackingId")
  @ExceptionCounted(
      name = "rcGetContainerByTrackingIdExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rcContainerController",
      level3 = "rcGetContainerByTrackingId")
  @TimeTracing(component = AppComponent.RC, type = Type.REST, flow = "GetContainerByTrackingId")
  @GetMapping(path = RcConstants.TRACKING_ID_URI, produces = "application/json")
  public ResponseEntity<ContainerRLog> getContainerByTrackingId(
      @PathVariable(value = "trackingId") String trackingId) {
    return new ResponseEntity<>(
        rcContainerService.getReceivedContainerByTrackingId(trackingId), HttpStatus.OK);
  }

  @Operation(
      summary = "This will fetch received containers by package Scan Data",
      description = "This will return a 200 on successful.")
  @Parameters({
    @Parameter(name = RcConstants.TENENT_COUNTRY_CODE, required = true),
    @Parameter(name = RcConstants.TENENT_FACLITYNUM, required = true),
    @Parameter(name = RcConstants.USER_ID_HEADER_KEY, required = true),
    @Parameter(name = RcConstants.CORRELATION_ID_HEADER_KEY, required = true)
  })
  @Timed(
      name = "rcGetContainerByPackageBarCode",
      level1 = "uwms-receiving",
      level2 = "rcContainerController",
      level3 = "rcGetContainerByPackageBarCode")
  @ExceptionCounted(
      name = "rcGetContainerByPackageBarCodeExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rcContainerController",
      level3 = "rcGetContainerByPackageBarCode")
  @TimeTracing(component = AppComponent.RC, type = Type.REST, flow = "GetContainerByPackageBarCode")
  @GetMapping(path = RcConstants.PACKAGE_BAR_CODE_URI, produces = "application/json")
  public ResponseEntity<List<ContainerRLog>> getContainersByPackageBarCode(
      @PathVariable(value = "packageBarCode") String packageBarCode) {
    return new ResponseEntity<>(
        rcContainerService.getReceivedContainersByPackageBarCode(packageBarCode), HttpStatus.OK);
  }

  @Operation(
      summary = "This will fetch received containers by So Number",
      description = "This will return a 200 on successful.")
  @Parameters({
    @Parameter(name = RcConstants.TENENT_COUNTRY_CODE, required = true),
    @Parameter(name = RcConstants.TENENT_FACLITYNUM, required = true),
    @Parameter(name = RcConstants.USER_ID_HEADER_KEY, required = true),
    @Parameter(name = RcConstants.CORRELATION_ID_HEADER_KEY, required = true)
  })
  @Timed(
      name = "rcGetContainerBySoNumber",
      level1 = "uwms-receiving",
      level2 = "rcContainerController",
      level3 = "rcGetContainerBySoNumber")
  @ExceptionCounted(
      name = "rcGetContainerBySoNumberExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rcContainerController",
      level3 = "rcGetContainerBySoNumber")
  @TimeTracing(component = AppComponent.RC, type = Type.REST, flow = "GetContainerBySoNumber")
  @GetMapping(path = RcConstants.SO_NUMBER_URI, produces = "application/json")
  public ResponseEntity<List<ContainerRLog>> getContainersBySoNumber(
      @PathVariable(value = "soNumber") String soNumber) {
    return new ResponseEntity<>(
        rcContainerService.getReceivedContainersBySoNumber(soNumber), HttpStatus.OK);
  }

  @PatchMapping(
      path = RcConstants.UPDATE_RO_BY_TRACKING_ID_URI,
      produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
  @Operation(
      summary = "This will update a Return Order Data of a particular container.",
      description = "This will return a 200 on successful.")
  @Parameters({
    @Parameter(name = RcConstants.TENENT_COUNTRY_CODE, required = true),
    @Parameter(name = RcConstants.TENENT_FACLITYNUM, required = true),
    @Parameter(name = RcConstants.USER_ID_HEADER_KEY, required = true),
    @Parameter(name = RcConstants.CORRELATION_ID_HEADER_KEY, required = true)
  })
  @Timed(
      name = "rcUpdateRoTimed",
      level1 = "uwms-receiving",
      level2 = "rcContainerController",
      level3 = "rcUpdateContainer")
  @ExceptionCounted(
      name = "rcUpdateROExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rcContainerController",
      level3 = "rcUpdateContainer")
  @TimeTracing(component = AppComponent.RC, type = Type.REST, flow = "UpdateROContainer")
  public ResponseEntity<String> updateReturnOrderData(
      @PathVariable(value = "rcTrackingId") @NotEmpty String rcTrackingId,
      @RequestBody @NotNull UpdateReturnOrderDataRequest updateContainerRequest,
      @RequestHeader HttpHeaders httpHeaders) {
    rcContainerService.updateReturnOrderData(updateContainerRequest, httpHeaders);
    return new ResponseEntity<>("Successfully updated!", HttpStatus.OK);
  }
}
