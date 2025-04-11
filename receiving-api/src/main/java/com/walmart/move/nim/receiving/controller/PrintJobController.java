package com.walmart.move.nim.receiving.controller;

import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.ReprintLabelsRequestBody;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintJobResponse;
import com.walmart.move.nim.receiving.core.model.printlabel.ReprintLabelResponseBody;
import com.walmart.move.nim.receiving.core.service.LabelService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.*;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Print Job controller to get recently printed labels
 *
 * @author s1b041i
 */
@RestController
@RequestMapping("printjobs")
@Tag(name = "Label Service to query printed labels for RDC market", description = "ReprintLabels")
public class PrintJobController {

  private static final Logger LOGGER = LoggerFactory.getLogger(PrintJobController.class);

  @Autowired TenantSpecificConfigReader tenantSpecificConfigReader;
  @ManagedConfiguration private AppConfig appConfig;
  @Autowired Gson gson;

  @GetMapping(path = "/{deliveryNumber}/labels", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Get recently printed labels for the logged in user",
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
      name = "getLabelsTimed",
      level1 = "uwms-receiving",
      level2 = "printJobController",
      level3 = "getLabels")
  @ExceptionCounted(
      name = "getLabelsExceptionCount",
      level1 = "uwms-receiving",
      level2 = "printJobController",
      level3 = "getLabels")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public ResponseEntity<List<PrintJobResponse>> getLabels(
      @PathVariable(name = "deliveryNumber", required = true) Long deliveryNumber,
      @RequestParam(name = "allusers") Optional<Boolean> allusers,
      @RequestHeader HttpHeaders httpHeaders) {

    boolean labelsByUser = !allusers.orElse(false);

    LOGGER.info(
        "Received request to get labels for the deliveryNumber:{}, getLabelsByUser: {} and userId:{} ",
        deliveryNumber,
        labelsByUser,
        httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));

    LabelService labelService =
        tenantSpecificConfigReader.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.LABEL_SERVICE,
            LabelService.class);
    List<PrintJobResponse> labels =
        labelService.getLabels(
            deliveryNumber,
            httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY),
            labelsByUser);

    return new ResponseEntity<>(labels, HttpStatus.OK);
  }

  @PostMapping(path = "/reprint", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Prepare and return reprint payload based on list of tracking IDs",
      description = "This will return label response with status 200 and 206 for partial success")
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
      name = "getLabelsTimed",
      level1 = "uwms-receiving",
      level2 = "printJobController",
      level3 = "reptintLabels")
  @ExceptionCounted(
      name = "getLabelsExceptionCount",
      level1 = "uwms-receiving",
      level2 = "printJobController",
      level3 = "reptintLabels")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public ResponseEntity<ReprintLabelResponseBody> reprintLabels(
      @RequestParam(required = false) String language,
      @RequestBody ReprintLabelsRequestBody requestedTrackingIds,
      @RequestHeader HttpHeaders httpHeaders) {

    // TODO as enhancement: select processor based on tenant and language
    if (Objects.nonNull(language) && !language.equalsIgnoreCase(ReceivingConstants.JSON_STRING)) {
      LOGGER.warn(
          "No implementation for {} reprint in this tenant {}",
          language,
          TenantContext.getFacilityNum());
      throw new ReceivingInternalException(
          ExceptionCodes.CONFIGURATION_ERROR,
          String.format(
              ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
    }

    LabelService labelService =
        tenantSpecificConfigReader.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.LABEL_SERVICE,
            LabelService.class);

    if (requestedTrackingIds.getTrackingIds().size() > appConfig.getMaxAllowedReprintLabels()) {
      LOGGER.error(
          "Requested labels to reprint are {} that is more than allowed {}",
          requestedTrackingIds.getTrackingIds().size(),
          appConfig.getMaxAllowedReprintLabels());
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_REQUESTED_LPN, ReceivingConstants.INVALID_LPN);
    }

    ReprintLabelResponseBody labels =
        labelService.getReprintLabelData(requestedTrackingIds.getTrackingIds(), httpHeaders);

    if (CollectionUtils.isEmpty(labels.getPrintRequests())) {
      LOGGER.error(
          "Requested LPN(s) not found {}", requestedTrackingIds.getTrackingIds().toString());
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_REQUESTED_LPN, ReceivingConstants.INVALID_LPN);
    }

    Set<String> successfullyCreatedlabelDataForTrackingIds = new HashSet<>();
    labels
        .getPrintRequests()
        .stream()
        .forEach(
            labelPrintRequest ->
                successfullyCreatedlabelDataForTrackingIds.add(
                    labelPrintRequest.getLabelIdentifier()));

    LOGGER.info(
        "Successfully created label data for trackingIds {} and Failed to create label data for trackingIds {}",
        successfullyCreatedlabelDataForTrackingIds.toString(),
        Sets.difference(
            requestedTrackingIds.getTrackingIds(), successfullyCreatedlabelDataForTrackingIds));

    return new ResponseEntity<>(
        labels,
        requestedTrackingIds.getTrackingIds().size() == labels.getPrintRequests().size()
            ? HttpStatus.OK
            : HttpStatus.PARTIAL_CONTENT);
  }
}
