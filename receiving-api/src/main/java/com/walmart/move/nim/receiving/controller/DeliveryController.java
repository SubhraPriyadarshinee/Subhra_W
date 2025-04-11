package com.walmart.move.nim.receiving.controller;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.isKotlinEnabled;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.CORRELATION_ID_HEADER_KEY;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.MISSING_ORG_UNIT_ID_CODE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.MISSING_ORG_UNIT_ID_DESC;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ORG_UNIT_ID_HEADER;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.OSDR_SERVICE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.TENENT_COUNTRY_CODE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.TENENT_FACLITYNUM;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.DeliveryStatusPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.entity.DeliveryItemOverride;
import com.walmart.move.nim.receiving.core.entity.UnloaderInfo;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.core.model.ConfirmPurchaseOrdersRequest;
import com.walmart.move.nim.receiving.core.model.ConfirmPurchaseOrdersResponse;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.core.model.DeliveryStatusSummary;
import com.walmart.move.nim.receiving.core.model.DeliverySummary;
import com.walmart.move.nim.receiving.core.model.DeliveryWithOSDRResponse;
import com.walmart.move.nim.receiving.core.model.GDMDeliveryTrailerTemperatureInfo;
import com.walmart.move.nim.receiving.core.model.OverrideRequest;
import com.walmart.move.nim.receiving.core.model.ReOpenDeliveryInfo;
import com.walmart.move.nim.receiving.core.model.ReceiveIntoOssRequest;
import com.walmart.move.nim.receiving.core.model.ReceiveIntoOssResponse;
import com.walmart.move.nim.receiving.core.model.RecordOSDRRequest;
import com.walmart.move.nim.receiving.core.model.RecordOSDRResponse;
import com.walmart.move.nim.receiving.core.model.RejectPalletRequest;
import com.walmart.move.nim.receiving.core.model.RejectionMetadata;
import com.walmart.move.nim.receiving.core.model.TemporaryPalletTiHiRequest;
import com.walmart.move.nim.receiving.core.model.TemporaryPalletTiHiResponse;
import com.walmart.move.nim.receiving.core.model.delivery.UnloaderInfoDTO;
import com.walmart.move.nim.receiving.core.model.osdr.v2.OSDRPayload;
import com.walmart.move.nim.receiving.core.osdr.service.OsdrService;
import com.walmart.move.nim.receiving.core.service.DeliveryItemOverrideService;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.core.service.SecurityService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Resource;
import javax.validation.Valid;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ClientApi controller exposes all REST APIs which client (tc70 or any other client) uses.
 *
 * @author a0s01qi
 */
@RestController
@RequestMapping("deliveries")
@Tag(name = "Delivery Service", description = "To expose delivery resource and related services")
public class DeliveryController {
  private static final Logger log = LoggerFactory.getLogger(DeliveryController.class);

  @Resource(name = ReceivingConstants.DELIVERY_SERVICE)
  private DeliveryService deliveryService;

  @Autowired private SecurityService securityService;
  @Autowired private DeliveryItemOverrideService deliveryItemOverrideService;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private Gson gson;
  @Autowired private DeliveryStatusPublisher deliveryStatusPublisher;

  @PutMapping(path = "/{deliveryNumber}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Completes the given delivery",
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
  @Timed(
      name = "completeDeliveryTimed",
      level1 = "uwms-receiving-api",
      level2 = "deliveryController")
  @ExceptionCounted(
      name = "completeDeliveryExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "deliveryController")
  public DeliveryInfo completeDelivery(
      @PathVariable(value = "deliveryNumber", required = true) Long deliveryNumber,
      @RequestParam(name = "performUnload", required = false) boolean performUnload,
      @RequestHeader HttpHeaders headers)
      throws ReceivingException {
    log.info("COMPLETE_DELIVERY delivery number {}", deliveryNumber);
    return deliveryService.completeDelivery(deliveryNumber, performUnload, headers);
  }

  @PatchMapping(path = "/{deliveryNumber}")
  @Operation(
      summary = "Reopen the given delivery",
      description = "This will return a 200 on successful with deliverynumber and userId")
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
        name = "Content-Type",
        required = true,
        example = "Example: application/json",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-CorrelationId",
        example = "Example: a1-b2-c3-d4-e6",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @Timed(name = "reOpenDeliveryTimed", level1 = "uwms-receiving-api", level2 = "deliveryController")
  @ExceptionCounted(
      name = "reOpenDeliveryExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "deliveryController")
  public ResponseEntity<ReOpenDeliveryInfo> reOpenDelivery(
      @PathVariable(value = "deliveryNumber", required = true) Long deliveryNumber,
      @RequestHeader HttpHeaders headers)
      throws ReceivingException {
    log.info("Re-open delivery number {}", deliveryNumber);
    return new ResponseEntity<>(
        deliveryService.reOpenDelivery(deliveryNumber, headers), HttpStatus.OK);
  }

  @PatchMapping(
      path = "/{deliveryNumber}/ref/{purchaseReferenceNumber}/line/{purchaseReferenceLineNumber}")
  @Operation(
      summary = "Record the OSDR Reason Codes against the line",
      description = "This will return a 200 on successful with updated OSDR counts as response")
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
        name = "Content-Type",
        required = true,
        example = "Example: application/json",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-CorrelationId",
        example = "Example: a1-b2-c3-d4-e6",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @Timed(name = "recordOSDRTimed", level1 = "uwms-receiving-api", level2 = "deliveryController")
  @ExceptionCounted(
      name = "recordOSDRExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "deliveryController")
  public RecordOSDRResponse recordOSDR(
      @PathVariable(value = "deliveryNumber") Long deliveryNumber,
      @PathVariable(value = "purchaseReferenceNumber") String purchaseReferenceNumber,
      @PathVariable(value = "purchaseReferenceLineNumber") Integer purchaseReferenceLineNumber,
      @Valid @RequestBody RecordOSDRRequest recordOSDRReasonCodesRequestBody,
      @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {

    log.info(
        "recordOSDR by deliveryNumber: {}, purchaseReferenceNumber : {}, purchaseReferenceLineNumber : {}, body : {} ",
        deliveryNumber,
        purchaseReferenceNumber,
        purchaseReferenceLineNumber,
        gson.toJson(recordOSDRReasonCodesRequestBody));

    ReceivingUtils.validatePoNumber(purchaseReferenceNumber);

    return deliveryService.recordOSDR(
        deliveryNumber,
        purchaseReferenceNumber,
        purchaseReferenceLineNumber,
        recordOSDRReasonCodesRequestBody,
        httpHeaders);
  }

  @GetMapping(path = "/{deliveryNumber}", produces = "application/json")
  @Operation(
      summary = "Return delivery based on delivery number as response",
      description = "This service will return delivery information with OSDR & Problem data")
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
  @Timed(
      name = "getDeliveryTimed",
      level1 = "uwms-receiving-api",
      level2 = "deliveryController",
      level3 = "getDelivery")
  @ExceptionCounted(
      name = "getDeliveryExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "deliveryController",
      level3 = "getDelivery")
  public DeliveryWithOSDRResponse getDelivery(
      @PathVariable(value = "deliveryNumber") Long deliveryNumber,
      @RequestParam(value = "includeOSDR") boolean includeOSDR,
      @RequestParam(value = "poNumber", required = false) String poNumber,
      @RequestHeader HttpHeaders headers)
      throws ReceivingException {

    log.info("Finding delivery by deliveryNumber: {}", deliveryNumber);

    Map<String, Object> forwardableHeaders =
        ReceivingUtils.getForwardablHeaderWithTenantData(headers);

    return deliveryService.getDeliveryWithOSDRByDeliveryNumber(
        deliveryNumber, forwardableHeaders, includeOSDR, poNumber);
  }

  @PostMapping(path = "/{deliveryNumber}/item/{itemNumber}")
  @Operation(
      summary = "Return the latest version of temporary pallet TI-HI",
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
        in = ParameterIn.HEADER),
    @Parameter(
        name = "Content-Type",
        required = true,
        example = "Example: application/json",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-CorrelationId",
        required = true,
        example = "Example: a1-b2-c3-d4-e6",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @Timed(
      name = "saveTemporaryPalletTiHiTimed",
      level1 = "uwms-receiving-api",
      level2 = "deliveryController")
  @ExceptionCounted(
      name = "saveTemporaryPalletTiHiExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "deliveryController",
      level3 = "saveTemporaryPalletTiHi")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public ResponseEntity<TemporaryPalletTiHiResponse> saveTemporaryPalletTiHi(
      @PathVariable(value = "deliveryNumber") Long deliveryNumber,
      @PathVariable(value = "itemNumber") Long itemNumber,
      @Valid @RequestBody TemporaryPalletTiHiRequest temporaryPalletTiHiRequest,
      @RequestHeader HttpHeaders headers)
      throws ReceivingException {

    log.info(
        "Save temporary TixHi for deliveryNumber :: {} and itemNumber :: {}",
        deliveryNumber,
        itemNumber);
    DeliveryItemOverride deliveryItemOverride = null;
    try {
      deliveryItemOverride =
          deliveryItemOverrideService.saveTemporaryPalletTiHi(
              deliveryNumber, itemNumber, temporaryPalletTiHiRequest, headers);
    } catch (ObjectOptimisticLockingFailureException e) {
      log.error(
          "Version error while saving temporary TixHi for deliveryNumber :: {} and itemNumber :: {}",
          deliveryNumber,
          itemNumber,
          e);
      throw new ReceivingException(
          ReceivingException.TEMPORARY_PALLET_TIHI_VERSION_ERROR_MESSAGE,
          HttpStatus.BAD_REQUEST,
          ReceivingException.TEMPORARY_PALLET_TIHI_VERSION_ERROR_CODE,
          ReceivingException.TEMPORARY_PALLET_TIHI_VERSION_ERROR_HEADER);
    }
    TemporaryPalletTiHiResponse temporaryPalletTiHiResponse = new TemporaryPalletTiHiResponse();
    temporaryPalletTiHiResponse.setVersion(deliveryItemOverride.getVersion());

    return new ResponseEntity<>(temporaryPalletTiHiResponse, HttpStatus.OK);
  }

  @PutMapping("/{deliveryNumber}/unload")
  @TimeTracing(component = AppComponent.CORE, type = Type.REST, flow = "Unload-Complete")
  public void unloadComplete(
      @PathVariable("deliveryNumber") long deliveryNumber,
      @RequestParam(name = "doorNumber", required = false) String doorNumber,
      @RequestParam(value = "action", required = false) String action,
      @RequestHeader HttpHeaders headers) {

    deliveryService.unloadComplete(deliveryNumber, doorNumber, action, headers);

    log.info("Unloaded completed event published for delivery number {} ", deliveryNumber);
  }

  // API for putting delivery into the given status
  @PutMapping("/{deliveryNumber}/publish/{deliveryStatus}")
  public void publishWrkForDelivery(
      @PathVariable("deliveryNumber") long deliveryNumber,
      @PathVariable("deliveryStatus") DeliveryStatus deliveryStatus,
      @RequestHeader HttpHeaders headers) {

    deliveryStatusPublisher.publishDeliveryStatus(
        deliveryNumber, deliveryStatus.name(), null, ReceivingUtils.getForwardablHeader(headers));

    log.info("{} event published for delivery number {} ", deliveryStatus, deliveryNumber);
  }

  @PatchMapping(
      path = "/{deliveryNumber}/ref",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Confirm muliple purchase orders",
      description = "This will return 200 with specific error messages")
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
        in = ParameterIn.HEADER),
    @Parameter(
        name = "action",
        required = true,
        example = "Example: confirmPO",
        description = "String",
        in = ParameterIn.QUERY)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "OK")})
  @Timed(
      name = "confirmPurchaseOrdersTimed",
      level1 = "uwms-receiving",
      level2 = "deliveryController",
      level3 = "confirmPurchaseOrders")
  @ExceptionCounted(
      name = "confirmPurchaseOrdersExceptionCount",
      level1 = "uwms-receiving",
      level2 = "deliveryController",
      level3 = "confirmPurchaseOrders")
  @Counted(
      name = "confirmPurchaseOrdersHitCount",
      level1 = "uwms-receiving",
      level2 = "deliveryController",
      level3 = "confirmPurchaseOrders")
  public ResponseEntity<ConfirmPurchaseOrdersResponse> confirmPurchaseOrders(
      @PathVariable(value = "deliveryNumber") Long deliveryNumber,
      @RequestParam(value = "action", required = true) String action,
      @RequestBody ConfirmPurchaseOrdersRequest confirmPOsRequest,
      @RequestHeader HttpHeaders httpHeaders) {

    Map<String, Object> forwardableHeaders =
        ReceivingUtils.getForwardablHeaderWithTenantData(httpHeaders);

    ConfirmPurchaseOrdersResponse confirmPOsResponse =
        deliveryService.confirmPOs(deliveryNumber, confirmPOsRequest, forwardableHeaders);

    return new ResponseEntity<>(confirmPOsResponse, HttpStatus.OK);
  }

  @GetMapping(path = "/osdr", produces = "application/json")
  @Operation(
      summary =
          "Return OSDR summary as response based on delivery number, PO number, Po-line number and Uom",
      description = "This service will return delivery information with OSDR")
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
        in = ParameterIn.HEADER),
    @Parameter(
        name = "version",
        example = "Example: v1, v2",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  @Timed(
      name = "getOSDRTimed",
      level1 = "uwms-receiving-api",
      level2 = "deliveryController",
      level3 = "getOSDRSummary")
  @ExceptionCounted(
      name = "getOSDRExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "deliveryController",
      level3 = "getOSDRSummary")
  public ResponseEntity<String> getOsdrSummary(
      @RequestParam(value = "deliveryNumber", required = true) Long deliveryNumber,
      @RequestParam(value = "purchaseReferenceNumber", required = false)
          String purchaseReferenceNumber,
      @RequestParam(value = "purchaseReferenceLineNumber", required = false)
          Integer purchaseReferenceLineNumber,
      @RequestParam(value = "uom", required = false) String uom,
      @RequestParam(value = "include", required = false) String include,
      @RequestHeader HttpHeaders headers)
      throws ReceivingException {

    List<String> version = headers.get(ReceivingConstants.VERSION);
    if (Objects.nonNull(version) && version.size() > 0 && version.get(0).equals("v2")) {
      OSDRPayload osdrPayload =
          tenantSpecificConfigReader
              .getConfiguredInstance(getFacilityNum().toString(), OSDR_SERVICE, OsdrService.class)
              .createOSDRv2Payload(deliveryNumber, include);
      return ResponseEntity.ok()
          .header(ReceivingConstants.OSDR_EVENT_TYPE_KEY, osdrPayload.getEventType())
          .body(gson.toJson(osdrPayload));
    }
    return ResponseEntity.ok()
        .header(ReceivingConstants.OSDR_EVENT_TYPE_KEY, ReceivingConstants.OSDR_EVENT_TYPE_VALUE)
        .body(
            gson.toJson(
                deliveryService.getOsdrInformation(
                    deliveryNumber,
                    purchaseReferenceNumber,
                    purchaseReferenceLineNumber,
                    headers.getFirst(ReceivingConstants.USER_ID_HEADER_KEY),
                    uom,
                    include)));
  }

  @PutMapping("osdr/pallet/reject")
  @Operation(
      summary = "Reject Pallet Request",
      description = "This service will reject requested pallet")
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
  @Timed(
      name = "getRejectPalletTimed",
      level1 = "uwms-receiving-api",
      level2 = "deliveryController",
      level3 = "getRejectPallet")
  @ExceptionCounted(
      name = "getRejectPalletExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "deliveryController",
      level3 = "getRejectPallet")
  public ResponseEntity<String> rejectPoLine(
      @Valid @RequestBody RejectPalletRequest rejectPalletRequest) {
    deliveryService.recordPalletReject(rejectPalletRequest);
    return new ResponseEntity<>(HttpStatus.CREATED.getReasonPhrase(), HttpStatus.CREATED);
  }

  /**
   * SM based authentication and authorization has been decommissioned and replaced with Token Based
   */
  @PostMapping(
      path = "/{deliveryNumber}/override",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Override overage or expiry item", description = "This will return 200")
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
        in = ParameterIn.HEADER),
    @Parameter(
        name = "action",
        required = true,
        example = "Example: overages or expiry",
        description = "String",
        in = ParameterIn.QUERY)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "OK")})
  @Timed(
      name = "overrideTimed",
      level1 = "uwms-receiving",
      level2 = "deliveryController",
      level3 = "override")
  @ExceptionCounted(
      name = "overrideExceptionCount",
      level1 = "uwms-receiving",
      level2 = "deliveryController",
      level3 = "override")
  @Counted(
      name = "overrideHitCount",
      level1 = "uwms-receiving",
      level2 = "deliveryController",
      level3 = "override")
  public ResponseEntity<String> override(
      @PathVariable(value = "deliveryNumber") Long deliveryNumber,
      @RequestParam(value = "action", required = true) String action,
      @RequestBody @Valid OverrideRequest overrideRequest,
      @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    Map<String, Object> forwardableHeaders =
        ReceivingUtils.getForwardablHeaderWithTenantData(httpHeaders);

    if (StringUtils.isBlank(action)) {
      throw new ReceivingException("action should not be empty", HttpStatus.BAD_REQUEST);
    }

    boolean authorization = false;
    final boolean isKotlinEnabled = isKotlinEnabled(httpHeaders, tenantSpecificConfigReader);
    if (isKotlinEnabled) {
      authorization = securityService.authorizeWithCcmToken(overrideRequest.getPassword(), action);
    } else {
      // Authenticate
      Map<String, Object> authDetails =
          securityService.authenticate(overrideRequest, forwardableHeaders);

      // Authorize
      authorization =
          securityService.authorize(
              overrideRequest.getUserId(),
              authDetails.get(ReceivingConstants.SECURITY_ID).toString(),
              authDetails.get(ReceivingConstants.TOKEN).toString(),
              action,
              forwardableHeaders);
    }

    securityService.validateAuthorization(
        overrideRequest.getUserId(), authorization, isKotlinEnabled);

    // Override
    deliveryItemOverrideService.override(action, deliveryNumber.toString(), overrideRequest);

    log.info(
        "User=[{}] got an approval from manager=[{}] for DELIVERY=[{}] PO=[{}], LINE=[{}], ACTION=[{}]",
        httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY),
        overrideRequest.getUserId(),
        deliveryNumber,
        overrideRequest.getPurchaseReferenceNumber(),
        overrideRequest.getPurchaseReferenceLineNumber(),
        action);

    return new ResponseEntity<>("", HttpStatus.OK);
  }

  @PostMapping(path = "/{publishstatus}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Successfully publish delivery status to GDM for given delivery payload",
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
  @Timed(
      name = "publishDeliveryStatusTimed",
      level1 = "uwms-receiving",
      level2 = "deliveryController",
      level3 = "publishDeliveryStatus")
  @ExceptionCounted(
      name = "publishDeliveryStatusExceptionCount",
      level1 = "uwms-receiving",
      level2 = "deliveryController",
      level3 = "publishDeliveryStatus")
  @Counted(
      name = "publishDeliveryStatusHitCount",
      level1 = "uwms-receiving",
      level2 = "deliveryController",
      level3 = "publishDeliveryStatus")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public ResponseEntity<String> publishDeliveryStatus(
      @RequestBody DeliveryInfo deliveryInfo, @RequestHeader HttpHeaders headers)
      throws ReceivingException {
    tenantSpecificConfigReader
        .getConfiguredInstance(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.DELIVERY_SERVICE_KEY,
            DeliveryService.class)
        .publishDeliveryStatus(deliveryInfo, headers);
    return new ResponseEntity<String>("Successfully published delivery status", HttpStatus.OK);
  }

  @PutMapping(path = "/{deliveryNumber}/trailerTemperature")
  @Operation(
      summary = "Update the trailer temperature zones for the delivery",
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
        in = ParameterIn.HEADER),
    @Parameter(
        name = "Content-Type",
        required = true,
        example = "Example: application/json",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-CorrelationId",
        example = "Example: a1-b2-c3-d4-e6",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @Timed(
      name = "updateDeliveryTrailerTemperatureTimed",
      level1 = "uwms-receiving",
      level2 = "deliveryController")
  @ExceptionCounted(
      name = "updateDeliveryTrailerTemperatureExceptionCount",
      level1 = "uwms-receiving",
      level2 = "deliveryController")
  public ResponseEntity<GDMDeliveryTrailerTemperatureInfo> updateDeliveryTrailerTemperature(
      @PathVariable(value = "deliveryNumber", required = true) Long deliveryNumber,
      @RequestBody GDMDeliveryTrailerTemperatureInfo deliveryTrailerTemperatureInfo,
      @RequestHeader HttpHeaders headers)
      throws ReceivingInternalException {

    HttpHeaders httpHeaders = ReceivingUtils.getForwardableHttpHeaders(headers);

    return new ResponseEntity<>(
        deliveryService.updateDeliveryTrailerTemperature(
            deliveryNumber, deliveryTrailerTemperatureInfo, httpHeaders),
        HttpStatus.OK);
  }

  // Fetch Trailer Zone Temperature from GDM
  @GetMapping(path = "/{deliveryNumber}/trailerTemperature", produces = "application/json")
  @Operation(
      summary = "Return trailerZones temperature based on delivery number as response",
      description = "This service will return trailerzone temperature with purchase oders data")
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
  @Timed(
      name = "getTrailerZoneTemperatureTimed",
      level1 = "uwms-receiving",
      level2 = "deliveryController",
      level3 = "getTrailerZoneTemperature")
  @ExceptionCounted(
      name = "getTrailerZoneTemperatureExceptionCount",
      level1 = "uwms-receiving",
      level2 = "deliveryController",
      level3 = "getTrailerZoneTemperature")
  public GDMDeliveryTrailerTemperatureInfo getTrailerZoneTemperature(
      @PathVariable(value = "deliveryNumber") Long deliveryNumber,
      @RequestHeader HttpHeaders headers)
      throws ReceivingException {

    log.info("Finding TrailerZoneTemperature by deliveryNumber: {}", deliveryNumber);

    HttpHeaders forwardableHttpHeaders = ReceivingUtils.getForwardableHttpHeaders(headers);

    return deliveryService.getTrailerZoneTemperature(deliveryNumber, forwardableHttpHeaders);
  }

  @Operation(
      summary = "Return delivery summary based on delivery number",
      description = "This service will return summarized view of the delivery")
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
      name = "getDeliverySummaryTimed",
      level1 = "uwms-receiving",
      level2 = "deliveryController",
      level3 = "getDeliverySummary")
  @ExceptionCounted(
      name = "getDeliverySummaryExceptionCount",
      level1 = "uwms-receiving",
      level2 = "deliveryController",
      level3 = "getDeliverySummary")
  @Counted(
      name = "getDeliverySummaryHitCount",
      level1 = "uwms-receiving",
      level2 = "deliveryController",
      level3 = "getDeliverySummary")
  @GetMapping(path = "/{deliveryNumber}/summary", produces = "application/json")
  public DeliverySummary getDeliverySummary(
      @PathVariable(value = "deliveryNumber") Long deliveryNumber,
      @RequestHeader HttpHeaders headers)
      throws ReceivingException {
    return deliveryService.getDeliverySummary(
        deliveryNumber, ReceivingUtils.getForwardableHttpHeaders(headers));
  }

  @PutMapping(path = "/{deliveryNumber}/closetrailer")
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
  @ApiResponses(
      value = {@ApiResponse(responseCode = "200", description = "Successfully closed the trailer")})
  @Timed(
      name = "closeTrailerTimed",
      level1 = "uwms-receiving",
      level2 = "deliveryController",
      level3 = "closeTrailer")
  @ExceptionCounted(
      name = "closeTrailerCount",
      level1 = "uwms-receiving",
      level2 = "deliveryController",
      level3 = "closeTrailer")
  public ResponseEntity<String> closeTrailer(
      @PathVariable("deliveryNumber") long deliveryNumber, @RequestHeader HttpHeaders headers) {
    deliveryService.closeTrailer(deliveryNumber, headers);
    return new ResponseEntity<>("Successfully closed the trailer", HttpStatus.OK);
  }

  @Operation(
      summary = "Return delivery status summary based on delivery number",
      description = "This service will return summarized view of the delivery status")
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
      name = "getDeliveryStatusSummaryTimed",
      level1 = "uwms-receiving",
      level2 = "deliveryController",
      level3 = "getDeliveryStatusSummary")
  @ExceptionCounted(
      name = "getDeliveryStatusSummaryExceptionCount",
      level1 = "uwms-receiving",
      level2 = "deliveryController",
      level3 = "getDeliveryStatusSummary")
  @Counted(
      name = "getDeliveryStatusSummaryHitCount",
      level1 = "uwms-receiving",
      level2 = "deliveryController",
      level3 = "getDeliveryStatusSummary")
  @GetMapping(path = "/{deliveryNumber}/status", produces = "application/json")
  public DeliveryStatusSummary getDeliveryStatus(
      @PathVariable(value = "deliveryNumber") Long deliveryNumber,
      @RequestHeader HttpHeaders headers)
      throws ReceivingException {
    return deliveryService.getDeliveryStatusSummary(deliveryNumber);
  }

  @Operation(
      summary = "Completes the given delivery and purchase orders on it",
      description = "This will return a 200 on successful and 500 on failure")
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
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "OK")})
  @Timed(
      name = "completeAllTimed",
      level1 = "uwms-receiving",
      level2 = "deliveryController",
      level3 = "completeAll")
  @ExceptionCounted(
      name = "completeAllExceptionCount",
      level1 = "uwms-receiving",
      level2 = "deliveryController",
      level3 = "completeAll")
  @Counted(
      name = "completeAllHitCount",
      level1 = "uwms-receiving",
      level2 = "deliveryController",
      level3 = "completeAll")
  @PutMapping(
      path = "/{deliveryNumber}/completeAll",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public DeliveryInfo completeAll(
      @PathVariable(value = "deliveryNumber") Long deliveryNumber,
      @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    return deliveryService.completeAll(deliveryNumber, httpHeaders);
  }

  @PostMapping(path = "receive/{deliveryNumber}", produces = "application/json")
  @Operation(summary = "Receives entire delivery", description = "Receives entire delivery")
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
  @Timed(
      name = "receiveIntoOssTimed",
      level1 = "uwms-receiving-api",
      level2 = "deliveryController",
      level3 = "receiveIntoOss")
  @ExceptionCounted(
      name = "receiveIntoOssExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "deliveryController",
      level3 = "receiveIntoOss")
  public ReceiveIntoOssResponse receiveIntoOss(
      @PathVariable(value = "deliveryNumber") Long deliveryNumber,
      @RequestBody ReceiveIntoOssRequest receiveIntoOssRequest,
      @RequestHeader HttpHeaders headers)
      throws ReceivingException {

    // validate OrgUnitId in request header
    if (StringUtils.isBlank(headers.getFirst(ORG_UNIT_ID_HEADER))) {
      throw new ReceivingBadDataException(MISSING_ORG_UNIT_ID_CODE, MISSING_ORG_UNIT_ID_DESC);
    }
    return deliveryService.receiveIntoOss(deliveryNumber, receiveIntoOssRequest, headers);
  }

  @PutMapping("delivery/{deliveryNumber}")
  @Operation(
      summary = "publish the unloader deliveryEventType",
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
  @Timed(
      name = "unloaderDeliveryStatusPublisherTimed",
      level1 = "uwms-receiving-api",
      level2 = "deliveryController")
  @ExceptionCounted(
      name = "unloaderDeliveryStatusPublisherExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "deliveryController")
  public void unloaderDeliveryEventPublisher(
      @PathVariable("deliveryNumber") long deliveryNumber,
      @RequestParam(name = "deliveryEventType") String deliveryEventType,
      @RequestHeader HttpHeaders headers)
      throws ReceivingBadDataException {

    ReceivingUtils.validateUnloaderEventType(deliveryEventType);
    deliveryService.deliveryEventTypePublisher(deliveryNumber, deliveryEventType, headers);
  }

  @PostMapping("delivery/unloaderInfo")
  @Operation(
      summary = "Record and publish the unloader deliveryEventType",
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
  @Timed(
      name = "saveUnloaderInfoTimed",
      level1 = "uwms-receiving-api",
      level2 = "deliveryController")
  @ExceptionCounted(
      name = "saveUnloaderInfoExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "deliveryController")
  public void saveUnloaderInfo(
      @RequestBody UnloaderInfoDTO unloaderInfo, @RequestHeader HttpHeaders headers)
      throws ReceivingBadDataException {

    ReceivingUtils.validateUnloaderInfoRequiredFields(unloaderInfo);
    deliveryService.saveUnloaderInfo(unloaderInfo, headers);
  }

  @GetMapping(path = "/{deliveryNumber}/getUnloaderInfo", produces = "application/json")
  @Operation(
      summary = "Return getUnloaderInfo based on delivery number as response",
      description = "This service will return getUnloaderInfo")
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
  @Timed(
      name = "getUnloaderInfoTimed",
      level1 = "uwms-receiving-api",
      level2 = "deliveryController",
      level3 = "getUnloaderInfo")
  @ExceptionCounted(
      name = "getUnloaderInfoExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "deliveryController",
      level3 = "getUnloaderInfo")
  public List<UnloaderInfo> getUnloaderInfo(
      @PathVariable(value = "deliveryNumber") Long deliveryNumber,
      @RequestParam(value = "purchaseReferenceNumber", required = false) String poNumber,
      @RequestParam(value = "poLine", required = false) Integer poLineNumber,
      @RequestHeader HttpHeaders headers)
      throws ReceivingException {

    log.info(
        "Finding UnloaderInfo by deliveryNumber: {},purchaseReferenceNumber: {}, poLine: {}",
        deliveryNumber,
        poNumber,
        poLineNumber);
    ReceivingUtils.validateDeliveryNumber(deliveryNumber);

    return deliveryService.getUnloaderInfo(deliveryNumber, poNumber, poLineNumber);
  }

  /**
   * * To be consumed by the OB relayer. This HTTP endpoint tends to replace the JMS listener
   *
   * @param deliveryUpdateMessage
   * @return
   */
  @PostMapping(path = "/update", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary =
          "To be consumed by the OB relayer. Successfully publish delivery update from GDM for given delivery payload.",
      description = "This will return a 200")
  @Timed(
      name = "deliveryUpdateTimed",
      level1 = "uwms-receiving",
      level2 = "deliveryController",
      level3 = "deliveryUpdate")
  @ExceptionCounted(
      name = "deliveryUpdateExceptionCount",
      level1 = "uwms-receiving",
      level2 = "deliveryController",
      level3 = "deliveryUpdate")
  @Counted(
      name = "deliveryUpdateHitCount",
      level1 = "uwms-receiving",
      level2 = "deliveryController",
      level3 = "deliveryUpdate")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Responds with success or failure of the API")
      })
  public ResponseEntity<HttpStatus> deliveryUpdate(
      @RequestHeader HttpHeaders headers,
      @RequestBody @Valid DeliveryUpdateMessage deliveryUpdateMessage)
      throws ReceivingException {

    ReceivingUtils.setTenantContext(
        headers.getFirst(TENENT_FACLITYNUM),
        headers.getFirst(TENENT_COUNTRY_CODE),
        headers.getFirst(CORRELATION_ID_HEADER_KEY),
        this.getClass().getName());

    EventProcessor deliveryEventProcessor =
        tenantSpecificConfigReader.getDeliveryEventProcessor(headers.getFirst(TENENT_FACLITYNUM));
    log.info("Got delivery update. payload = {} ", deliveryUpdateMessage);
    deliveryEventProcessor.processEvent(deliveryUpdateMessage);

    log.info("Delivery update from GDM processing completed");
    return new ResponseEntity<>(HttpStatus.OK);
  }

  @GetMapping(path = "/{deliveryNumber}/getRejectionMetadata", produces = "application/json")
  @Operation(
      summary = "Return getRejectionMetadata based on delivery number as response",
      description = "This service will return getRejectionMetadata to prepopulate values")
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
  @Timed(
      name = "getRejectionMetadataTimed",
      level1 = "uwms-receiving-api",
      level2 = "deliveryController",
      level3 = "getRejectionMetadata")
  @ExceptionCounted(
      name = "getRejectionMetadataExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "deliveryController",
      level3 = "getRejectionMetadata")
  public RejectionMetadata getRejectionMetadata(
      @PathVariable(value = "deliveryNumber") Long deliveryNumber,
      @RequestHeader HttpHeaders headers)
      throws ReceivingException {

    log.info("Finding RejectionMetadata by deliveryNumber: {}", deliveryNumber);
    ReceivingUtils.validateDeliveryNumber(deliveryNumber);

    return deliveryService.getRejectionMetadata(deliveryNumber);
  }
}
