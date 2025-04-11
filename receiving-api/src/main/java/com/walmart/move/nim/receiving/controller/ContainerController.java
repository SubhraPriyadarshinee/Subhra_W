package com.walmart.move.nim.receiving.controller;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.sanitize;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.CORRELATION_ID_HEADER_KEY;
import static java.util.Objects.nonNull;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.model.v2.ContainerScanRequest;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.core.transformer.ContainerTransformer;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

/**
 * Containers controller to expose container resource
 *
 * @author lkotthi
 */
@RestController
@RequestMapping("containers")
public class ContainerController {

  private static final Logger log = LoggerFactory.getLogger(ContainerController.class);

  @Autowired private TenantSpecificConfigReader configUtils;
  @Autowired private ContainerService containerService;
  @Autowired private ContainerTransformer containerTransformer;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @PostMapping(
      path = "/cancel",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Returns the request accepted",
      description = "This will return 202 with specific failures")
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
        example = "Example: 32987",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        required = true,
        example = "Example: sysadmin",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "202", description = "ACCEPTED")})
  @Timed(
      name = "cancelContainersTimed",
      level1 = "uwms-receiving",
      level2 = "containerController",
      level3 = "cancelContainersDetails")
  @ExceptionCounted(
      name = "cancelContainersExceptionCount",
      level1 = "uwms-receiving",
      level2 = "containerController",
      level3 = "cancelContainersDetails")
  public ResponseEntity<List<CancelContainerResponse>> cancelContainers(
      @RequestBody CancelContainerRequest cancelContainerRequest,
      @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {

    List<CancelContainerResponse> response =
        containerService.cancelContainers(cancelContainerRequest, httpHeaders);
    return new ResponseEntity<>(response, HttpStatus.ACCEPTED);
  }

  @PostMapping(
      path = "/swap",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<CancelContainerResponse>> swapContainers(
      @RequestBody List<SwapContainerRequest> swapContainerRequest,
      @RequestHeader HttpHeaders httpHeaders) {
    List<CancelContainerResponse> response =
        containerService.swapContainers(swapContainerRequest, httpHeaders);
    return CollectionUtils.isEmpty(response)
        ? new ResponseEntity<>(response, HttpStatus.OK)
        : new ResponseEntity<>(response, HttpStatus.CONFLICT);
  }

  @PutMapping(
      path = "/hold",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Returns the response",
      description = "This will return 200 with specific failures")
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
        example = "Example: 32987",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        required = true,
        example = "Example: sysadmin",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "OK")})
  @Timed(
      name = "palletsOnHoldTimed",
      level1 = "uwms-receiving",
      level2 = "containerController",
      level3 = "palletsOnHoldDetails")
  @ExceptionCounted(
      name = "palletsOnHoldExceptionCount",
      level1 = "uwms-receiving",
      level2 = "containerController",
      level3 = "palletsOnHoldDetails")
  public ResponseEntity<List<ContainerErrorResponse>> onHold(
      @RequestBody PalletsHoldRequest palletsHoldRequest, @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    // Get the proper implementation of PutOnHoldService based on tenant
    PutOnHoldService putOnHoldService =
        configUtils.getPutOnHoldServiceByFacility(TenantContext.getFacilityNum().toString());

    List<ContainerErrorResponse> response =
        putOnHoldService.palletsOnHold(palletsHoldRequest, httpHeaders);

    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @PutMapping(
      path = "/offHold",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Returns the response",
      description = "This will return 200 with specific failures")
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
        example = "Example: 32987",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        required = true,
        example = "Example: sysadmin",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "OK")})
  @Timed(
      name = "palletsOffHoldTimed",
      level1 = "uwms-receiving",
      level2 = "containerController",
      level3 = "palletsOffHoldDetails")
  @ExceptionCounted(
      name = "palletsOffHoldExceptionCount",
      level1 = "uwms-receiving",
      level2 = "containerController",
      level3 = "palletsOffHoldDetails")
  public ResponseEntity<List<ContainerErrorResponse>> offHold(
      @RequestBody PalletsHoldRequest palletsHoldRequest, @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    PutOnHoldService putOnHoldService =
        configUtils.getPutOnHoldServiceByFacility(TenantContext.getFacilityNum().toString());
    return new ResponseEntity<>(
        putOnHoldService.palletsOffHold(palletsHoldRequest, httpHeaders), HttpStatus.OK);
  }

  /**
   * get full details of a container by lpn/tracking id
   *
   * @param trackingId
   * @return Container
   * @throws ReceivingException
   */
  @GetMapping(path = "/{trackingId}", produces = "application/json")
  @Operation(summary = "Return Container based on LPN/TrackingId as response")
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
        required = false,
        example = "Example: sysAdmin",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "success")})
  @Timed(
      name = "getContainerByTrackingIdTimed",
      level1 = "uwms-receiving-api",
      level2 = "containerController",
      level3 = "getContainerByTrackingId")
  @ExceptionCounted(
      name = "getDeliveryExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "containerController",
      level3 = "getContainerByTrackingId")
  public Container getContainerByTrackingId(
      @PathVariable(value = "trackingId", required = true) String trackingId,
      @RequestParam(value = "includeChilds", required = true) boolean includeChilds,
      @RequestParam(value = "includeInventoryData", required = false) boolean includeInventoryData,
      @RequestParam(value = "uom", required = false, defaultValue = "EA") String uom,
      @RequestParam(value = "isReEngageDecantFlow", required = false, defaultValue = "false")
          boolean isReEngageDecantFlow,
      @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    log.info(
        "called getContainerByTrackingId trackingId: {}, includeChilds: {}, uom: {} includeInventoryData: {} isReEngageDecantFlow: {}",
        trackingId,
        includeChilds,
        uom,
        includeInventoryData,
        isReEngageDecantFlow);

    GetContainerRequestHandler getContainerRequestHandler =
        configUtils.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.GET_CONTAINER_REQUEST_HANDLER,
            GetContainerRequestHandler.class);

    Container container =
        getContainerRequestHandler.getContainerByTrackingId(
            trackingId, includeChilds, uom, isReEngageDecantFlow, httpHeaders);

    if (includeInventoryData) {
      container =
          containerService.updateContainerData(container, includeInventoryData, httpHeaders);
    }

    return container;
  }

  /**
   * update quantity of container by lpn/tracking id and return Updated Container and its associated
   * print label
   *
   * @param trackingId
   * @return ContainerUpdateResponse
   * @throws ReceivingException
   */
  @PostMapping(path = "/{trackingId}/adjust", produces = "application/json")
  @Operation(
      summary = "Adjusts Container Item Quantity and returns print job based on LPN/TrackingId")
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
        required = false,
        example = "Example: sysAdmin",
        description = "String",
        in = ParameterIn.HEADER)
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
      @Valid @RequestBody ContainerUpdateRequest containerUpdateRequest,
      @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    log.info(
        "step:0 cId={}, Requested to adjust Quantity for lpn={} containerUpdateRequest={}",
        httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY),
        trackingId,
        containerUpdateRequest);
    return containerService.updateQuantityByTrackingId(
        sanitize(trackingId), containerUpdateRequest, httpHeaders);
  }

  /**
   * Get Containers
   *
   * @return Container
   * @throws ReceivingException
   */
  @GetMapping(produces = "application/json")
  @Operation(summary = "Return Containers as response")
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
        required = false,
        example = "Example: sysAdmin",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "success")})
  @Timed(
      name = "getContainers",
      level1 = "uwms-receiving-api",
      level2 = "containerController",
      level3 = "getContainers")
  @ExceptionCounted(
      name = "getContainersExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "containerController",
      level3 = "getContainers")
  public List<Container> getContainers(
      @RequestParam(value = "orderBy", required = false, defaultValue = "createTs")
          String orderByColumnName,
      @RequestParam(value = "sortOrder", required = false, defaultValue = "desc") String sortOrder,
      @RequestParam(value = "page", required = false, defaultValue = "0") int page,
      @RequestParam(value = "limit", required = false, defaultValue = "4") int limit,
      @RequestParam(value = "parentOnly", required = false, defaultValue = "true")
          boolean parentOnly) {

    return containerService.getContainers(orderByColumnName, sortOrder, page, limit, parentOnly);
  }

  /**
   * get container labels by tracking ids
   *
   * @return Container
   * @throws ReceivingException
   */
  @PostMapping(path = "/labels/reprint", produces = "application/json")
  @Operation(summary = "Returns print jobs based on LPNs/TrackingIds as response")
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
        required = false,
        example = "Example: sysAdmin",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "success")})
  @Timed(
      name = "getContainerLabelsByTrackingIdsTimed",
      level1 = "uwms-receiving-api",
      level2 = "containerController",
      level3 = "getContainerLabelsByTrackingIds")
  @ExceptionCounted(
      name = "getContainerLabelsByTrackingIdsExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "containerController",
      level3 = "getContainerLabelsByTrackingIds")
  public ResponseEntity<Map<String, Object>> getContainerLabelsByTrackingIds(
      @RequestBody ReprintLabelRequest reprintLabelRequest, @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    AbstractContainerService containerServiceByFacility =
        tenantSpecificConfigReader.getConfiguredInstance(
            getFacilityNum().toString(),
            ReceivingConstants.CONTAINER_SERVICE,
            ReceivingConstants.DEFAULT_CONTAINER_SERVICE,
            ContainerService.class);
    return new ResponseEntity<>(
        containerServiceByFacility.getContainerLabelsByTrackingIds(
            reprintLabelRequest.getTrackingIds(), httpHeaders),
        HttpStatus.OK);
  }

  /**
   * Get container item details by UPC and ItemNumber
   *
   * @param upc
   * @param itemNumber
   * @return ContainerItem
   * @throws ReceivingBadDataException
   */
  @GetMapping(path = "/upc/{upc}/item/{itemNumber}", produces = "application/json")
  @Operation(summary = "Get container item details by upc and itemNumber")
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
        in = ParameterIn.HEADER)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "success")})
  @Timed(
      name = "getContainerItemByUpcAndItemNumberTimed",
      level1 = "uwms-receiving-api",
      level2 = "containerController",
      level3 = "getContainerItemByUpcAndItemNumber")
  @ExceptionCounted(
      name = "getContainerItemByUpcAndItemNumberExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "containerController",
      level3 = "getContainerItemByUpcAndItemNumber")
  public ContainerItem getContainerItemByUpcAndItemNumber(
      @PathVariable(value = "upc", required = true) String upc,
      @PathVariable(value = "itemNumber", required = true) String itemNumber,
      @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingBadDataException {
    ContainerItem containerItem =
        containerService.getContainerItemByUpcAndItemNumber(upc, Long.valueOf(itemNumber));

    return containerItem;
  }

  @GetMapping(path = "/delivery/{deliveryNumber}")
  @Operation(summary = "Returns container details based on delivery number as response")
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
        required = false,
        example = "Example: sysAdmin",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "success")})
  @Timed(
      name = "getAllContainersByDeliveryTimed",
      level1 = "uwms-receiving-api",
      level2 = "containerController",
      level3 = "getAllContainersByDelivery")
  @ExceptionCounted(
      name = "getAllContainersByDeliveryExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "containerController",
      level3 = "getAllContainersByDelivery")
  public List<ContainerDTO> getAllContainersByDelivery(
      @PathVariable(value = "deliveryNumber") Long deliveryNumber,
      @RequestParam(value = "forAsn", required = false) boolean forAsn)
      throws ReceivingException {

    Consumer<Container> processContainer = null;
    if (forAsn)
      processContainer =
          (container) -> {
            if (nonNull(container.getSsccNumber())
                && !org.apache.commons.lang3.StringUtils.equalsIgnoreCase(
                    container.getTrackingId(), container.getSsccNumber())) {
              container.setTrackingId(container.getSsccNumber());
              if (!CollectionUtils.isEmpty(container.getContainerItems()))
                container
                    .getContainerItems()
                    .forEach(
                        containerItem -> containerItem.setTrackingId(container.getSsccNumber()));
            }
          };

    return containerTransformer.transformList(
        containerService.getContainerByDeliveryNumber(deliveryNumber, processContainer));
  }

  @PutMapping("/v2/{trackingId}")
  public String receiveContainer(
      @PathVariable("trackingId") String trackingId,
      @Valid @RequestBody ContainerScanRequest containerScanRequest) {
    if (Objects.nonNull(containerScanRequest)) {
      ContainerScanRequest sanitizedcontainerScanRequest =
          ContainerScanRequest.builder()
              .trackingId(sanitize(containerScanRequest.getTrackingId()))
              .loadNumber(sanitize(containerScanRequest.getLoadNumber()))
              .trailerNumber(sanitize(containerScanRequest.getTrailerNumber()))
              .deliveryNumber(containerScanRequest.getDeliveryNumber())
              .asnDocument(containerScanRequest.getAsnDocument())
              .overageType(containerScanRequest.getOverageType())
              .originalDeliveryNumber(containerScanRequest.getOriginalDeliveryNumber())
              .build();
      String response =
          containerService.receiveContainer(sanitize(trackingId), sanitizedcontainerScanRequest);
      return response;
    }
    return null;
  }

  @PutMapping("/v3/{trackingId}")
  public String receiveContainers(
      @PathVariable("trackingId") String trackingId,
      @Valid @RequestBody ContainerScanRequest containerScanRequest) {
    if (Objects.nonNull(containerScanRequest)) {
      ContainerScanRequest sanitizedcontainerScanRequest =
          ContainerScanRequest.builder()
              .trackingId(sanitize(containerScanRequest.getTrackingId()))
              .loadNumber(sanitize(containerScanRequest.getLoadNumber()))
              .trailerNumber(sanitize(containerScanRequest.getTrailerNumber()))
              .deliveryNumber(containerScanRequest.getDeliveryNumber())
              .asnDocument(containerScanRequest.getAsnDocument())
              .overageType(containerScanRequest.getOverageType())
              .originalDeliveryNumber(containerScanRequest.getOriginalDeliveryNumber())
              .build();
      String response =
          containerService.receiveContainer(sanitize(trackingId), sanitizedcontainerScanRequest);
      return response;
    }
    return null;
  }

  @GetMapping(path = "/labels/{deliveryNumber}")
  @Operation(summary = "Returns container details based on delivery number/PO details as response")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "success")})
  @Timed(
      name = "getContainersLabelsByDeliveryNumberOrPOTimed",
      level1 = "uwms-receiving-api",
      level2 = "containerController",
      level3 = "getContainersLabelsByDeliveryNumberOrPO")
  @ExceptionCounted(
      name = "getContainersLabelsByDeliveryNumberOrPOExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "containerController",
      level3 = "getContainersLabelsByDeliveryNumberOrPO")
  public List<PalletHistory> getReceivedHistoryByDeliveryPoPoLine(
      @PathVariable(value = "deliveryNumber") Long deliveryNumber,
      @RequestParam(value = "po", required = false) String po,
      @RequestParam(value = "poLine", required = false) Integer poLine)
      throws ReceivingException {
    List<PalletHistory> palletHistoryList = null;
    if (nonNull(deliveryNumber) && nonNull(po) && nonNull(poLine)) {
      palletHistoryList =
          containerService.getReceivedHistoryByDeliveryNumberWithPO(deliveryNumber, po, poLine);
    } else {
      palletHistoryList = containerService.getReceivedHistoryByDeliveryNumber(deliveryNumber);
    }
    return palletHistoryList;
  }
}
