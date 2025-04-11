package com.walmart.move.nim.receiving.rdc.controller;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.model.docktag.CreateDockTagRequest;
import com.walmart.move.nim.receiving.core.model.docktag.DockTagResponse;
import com.walmart.move.nim.receiving.core.service.DockTagService;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.model.docktag.DockTagData;
import com.walmart.move.nim.receiving.rdc.service.RdcDockTagService;
import com.walmart.move.nim.receiving.rdc.utils.RdcUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Optional;
import javax.annotation.Resource;
import javax.validation.Valid;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * DockTag controller to expose docktag resource
 *
 * @author s1b041i
 */
@ConditionalOnExpression("${enable.rdc.app:false}")
@RestController
@RequestMapping("rdc/docktags")
@Tag(name = "Dock Tag Service for RDC market", description = "DockTag")
public class RdcDockTagController {

  private static final Logger LOGGER = LoggerFactory.getLogger(RdcDockTagController.class);

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Resource(name = RdcConstants.RDC_DOCKTAG_SERVICE)
  private RdcDockTagService dockTagService;

  @PostMapping(path = "", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Create dock tag for a given deliveries",
      description = "This will return a 201")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true),
    @Parameter(name = "WMT-User-Location", required = true),
    @Parameter(name = "WMT-User-Location-Type", required = true),
    @Parameter(name = "WMT-User-Location-SCC", required = true)
  })
  @Timed(
      name = "createRdcDockTagTimed",
      level1 = "uwms-receiving",
      level2 = "rdcDockTagController",
      level3 = "createDockTag")
  @ExceptionCounted(
      name = "createRdcDockTagExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rdcDockTagController",
      level3 = "createDockTag")
  @ApiResponses(value = {@ApiResponse(responseCode = "201", description = "")})
  public ResponseEntity<DockTagResponse> createDockTag(
      @RequestBody @Valid CreateDockTagRequest createDockTagRequest,
      @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    LOGGER.info("Got request to create docktag for RDC");
    RdcUtils.validateMandatoryRequestHeaders(httpHeaders);
    DockTagResponse response =
        tenantSpecificConfigReader
            .getConfiguredInstance(
                TenantContext.getFacilityNum().toString(),
                ReceivingConstants.DOCK_TAG_SERVICE,
                DockTagService.class)
            .createDockTags(createDockTagRequest, httpHeaders);
    return new ResponseEntity<>(response, HttpStatus.CREATED);
  }

  @GetMapping(path = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Get dock tags for given criteria", description = "This will return a 200")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true)
  })
  @Timed(
      name = "searchRdcDockTagTimed",
      level1 = "uwms-receiving",
      level2 = "dockTagController",
      level3 = "searchDockTag")
  @ExceptionCounted(
      name = "searchRdcDockTagExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rdcDockTagController",
      level3 = "searchDockTag")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public ResponseEntity<List<DockTagData>> searchDockTag(
      @RequestParam Optional<String> dockTagId,
      @RequestParam Optional<Long> deliveryNumber,
      @RequestParam Optional<Long> fromDate,
      @RequestParam Optional<Long> toDate,
      @RequestHeader HttpHeaders httpHeaders) {

    if (!dockTagId.isPresent() && !deliveryNumber.isPresent()) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_DOCKTAG_REQUEST, RdcConstants.DTID_DELIVERY_CANNOT_BE_EMPTY);
    }

    List<DockTagData> dockTags =
        dockTagService.searchDockTag(dockTagId, deliveryNumber, fromDate, toDate);

    if (CollectionUtils.isEmpty(dockTags)) {
      return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
    return new ResponseEntity<>(dockTags, HttpStatus.OK);
  }

  @GetMapping(path = "/{dockTagId}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Fetch docktag information by docktag id",
      description = "This will return a 200")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true)
  })
  @Timed(
      name = "receiveRdcDockTagTimed",
      level1 = "uwms-receiving",
      level2 = "rdcDockTagController",
      level3 = "receiveDockTag")
  @ExceptionCounted(
      name = "receiveRdcDockTagExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rdcDockTagController",
      level3 = "receiveDockTag")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public ResponseEntity<DockTagData> receiveDockTag(
      @PathVariable(name = "dockTagId", required = true) String dockTagId,
      @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    DockTagData dockTagDataResponse = dockTagService.receiveDockTag(dockTagId, httpHeaders);
    return new ResponseEntity<>(dockTagDataResponse, HttpStatus.OK);
  }

  @PutMapping(path = "/{dockTagId}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Completes the given dock tag",
      description = "This will return a 200 on successful and 500 on failure.")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true)
  })
  @Timed(
      name = "rdcCompleteDockTagTimed",
      level1 = "uwms-receiving",
      level2 = "rdcDockTagController",
      level3 = "completeDockTag")
  @ExceptionCounted(
      name = "rdcCompleteDockTagExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rdcDockTagController",
      level3 = "completeDockTag")
  public DockTagData completeDockTag(
      @PathVariable(value = "dockTagId", required = true) String dockTagId,
      @RequestHeader HttpHeaders headers) {
    return dockTagService.completeDockTagById(dockTagId, headers);
  }
}
