package com.walmart.move.nim.receiving.controller;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.DockTag;
import com.walmart.move.nim.receiving.core.model.InstructionResponse;
import com.walmart.move.nim.receiving.core.model.OpenDockTagCount;
import com.walmart.move.nim.receiving.core.model.docktag.*;
import com.walmart.move.nim.receiving.core.service.DockTagService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.InstructionStatus;
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
import javax.annotation.Resource;
import javax.validation.Valid;
import javax.ws.rs.QueryParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * ClientApi controller exposes all REST APIs which client (tc70 or any other client) uses.
 *
 * @author a0s01qi
 */
@RestController
@RequestMapping("docktags")
@Tag(name = "Dock Tag Service", description = "To expose dock tag resource and related services")
public class DockTagController {

  private static final Logger LOGGER = LoggerFactory.getLogger(DockTagController.class);

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Resource(name = ReceivingConstants.DEFAULT_DOCK_TAG_SERVICE)
  private DockTagService dockTagService;

  @PutMapping(path = "/{dockTagId}/complete", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Completes the given dock tag",
      description = "This will return a 200 on successful and 500 on failure.")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true)
  })
  @Timed(
      name = "completeDockTagTimed",
      level1 = "uwms-receiving",
      level2 = "dockTagController",
      level3 = "completeDockTag")
  @ExceptionCounted(
      name = "completeDockTagExceptionCount",
      level1 = "uwms-receiving",
      level2 = "dockTagController",
      level3 = "completeDockTag")
  public String completeDockTag(
      @PathVariable(value = "dockTagId", required = true) String dockTagId,
      @RequestHeader HttpHeaders headers) {
    LOGGER.info("COMPLETE_DOCK_TAG dock tag id {}", dockTagId);
    return tenantSpecificConfigReader
        .getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.DOCK_TAG_SERVICE,
            DockTagService.class)
        .completeDockTag(dockTagId, headers);
  }

  @PutMapping(path = "/complete", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Completes the provided dock tag(s)",
      description = "This will return a 200 on successful and 207 on partial failure.")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true)
  })
  @Timed(
      name = "completeDockTagsTimed",
      level1 = "uwms-receiving",
      level2 = "dockTagController",
      level3 = "completeDockTags")
  @ExceptionCounted(
      name = "completeDockTagsExceptionCount",
      level1 = "uwms-receiving",
      level2 = "dockTagController",
      level3 = "completeDockTags")
  public ResponseEntity<CompleteDockTagResponse> completeDockTags(
      @RequestBody @Valid CompleteDockTagRequest completeDockTagRequest,
      @RequestHeader HttpHeaders headers) {
    CompleteDockTagResponse completeDockTagResponse =
        tenantSpecificConfigReader
            .getConfiguredInstance(
                TenantContext.getFacilityNum().toString(),
                ReceivingConstants.DOCK_TAG_SERVICE,
                DockTagService.class)
            .completeDockTags(completeDockTagRequest, headers);

    if (!CollectionUtils.isEmpty(completeDockTagResponse.getFailed()))
      return new ResponseEntity<>(completeDockTagResponse, HttpStatus.MULTI_STATUS);
    return new ResponseEntity<>(completeDockTagResponse, HttpStatus.OK);
  }

  @PostMapping
  @Operation(
      summary = "Used to fetch the instruction after scanning a dock tag",
      description = "This will return a 200")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true)
  })
  @Timed(
      name = "receiveDockTagTimed",
      level1 = "uwms-receiving",
      level2 = "dockTagController",
      level3 = "receiveDockTag")
  @ExceptionCounted(
      name = "receiveDockTagExceptionCount",
      level1 = "uwms-receiving",
      level2 = "dockTagController",
      level3 = "receiveDockTag")
  @ApiResponses(value = {@ApiResponse(responseCode = "201", description = "")})
  public ResponseEntity<InstructionResponse> receiveDockTag(
      @RequestBody @Valid ReceiveDockTagRequest receiveDockTagRequest,
      @RequestHeader HttpHeaders httpHeaders) {
    InstructionResponse instructionResponse =
        tenantSpecificConfigReader
            .getConfiguredInstance(
                TenantContext.getFacilityNum().toString(),
                ReceivingConstants.DOCK_TAG_SERVICE,
                DockTagService.class)
            .receiveDockTag(receiveDockTagRequest, httpHeaders);
    return new ResponseEntity<>(instructionResponse, HttpStatus.CREATED);
  }

  @PutMapping("/{dockTagId}/receive")
  @Operation(
      summary = "Used to fetch the delivery details after scanning a non con dock tag",
      description = "This will return a 200")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true)
  })
  @Timed(
      name = "receiveNonConDockTagTimed",
      level1 = "uwms-receiving",
      level2 = "dockTagController",
      level3 = "receiveNonConDockTag")
  @ExceptionCounted(
      name = "receiveNonConDockTagExceptionCount",
      level1 = "uwms-receiving",
      level2 = "dockTagController",
      level3 = "receiveNonConDockTag")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public ResponseEntity<ReceiveNonConDockTagResponse> receiveNonConDockTag(
      @PathVariable(value = "dockTagId", required = true) String dockTagId,
      @RequestHeader HttpHeaders httpHeaders) {
    ReceiveNonConDockTagResponse receiveNonConDockTagResponse =
        tenantSpecificConfigReader
            .getConfiguredInstance(
                TenantContext.getFacilityNum().toString(),
                ReceivingConstants.DOCK_TAG_SERVICE,
                DockTagService.class)
            .receiveNonConDockTag(dockTagId, httpHeaders);
    return new ResponseEntity<>(receiveNonConDockTagResponse, HttpStatus.OK);
  }

  @PostMapping(path = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Get dock tags for given deliveries", description = "This will return a 200")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true)
  })
  @Timed(
      name = "searchDockTagTimed",
      level1 = "uwms-receiving",
      level2 = "dockTagController",
      level3 = "searchDockTag")
  @ExceptionCounted(
      name = "searchDockTagExceptionCount",
      level1 = "uwms-receiving",
      level2 = "dockTagController",
      level3 = "searchDockTag")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public ResponseEntity<String> searchDockTag(
      @RequestBody @Valid SearchDockTagRequest searchDockTagRequest,
      @RequestHeader HttpHeaders httpHeaders,
      @QueryParam(value = "status") InstructionStatus status) {

    String searchDockTag =
        tenantSpecificConfigReader
            .getConfiguredInstance(
                TenantContext.getFacilityNum().toString(),
                ReceivingConstants.DOCK_TAG_SERVICE,
                DockTagService.class)
            .searchDockTag(searchDockTagRequest, status);
    if (StringUtils.isEmpty(searchDockTag)) {
      return new ResponseEntity<>(searchDockTag, HttpStatus.NO_CONTENT);
    }
    return new ResponseEntity<>(searchDockTag, HttpStatus.OK);
  }

  @PostMapping(path = "/create", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Create dock tag for a given deliveries",
      description = "This will return a 201")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true)
  })
  @Timed(
      name = "createDockTagTimed",
      level1 = "uwms-receiving",
      level2 = "dockTagController",
      level3 = "createDockTag")
  @ExceptionCounted(
      name = "createDockTagExceptionCount",
      level1 = "uwms-receiving",
      level2 = "dockTagController",
      level3 = "createDockTag")
  @ApiResponses(value = {@ApiResponse(responseCode = "201", description = "")})
  public ResponseEntity<InstructionResponse> createDockTag(
      @RequestBody @Valid CreateDockTagRequest createDockTagRequest,
      @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    InstructionResponse createdDockTag =
        tenantSpecificConfigReader
            .getConfiguredInstance(
                TenantContext.getFacilityNum().toString(),
                ReceivingConstants.DOCK_TAG_SERVICE,
                DockTagService.class)
            .createDockTag(createDockTagRequest, httpHeaders);
    return new ResponseEntity<>(createdDockTag, HttpStatus.CREATED);
  }

  @PostMapping(path = "/createMultiple", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Create dock tag for a given deliveries",
      description = "This will return a 201")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true),
  })
  @Timed(
      name = "createDockTagsTimed",
      level1 = "uwms-receiving",
      level2 = "dockTagController",
      level3 = "createDockTags")
  @ExceptionCounted(
      name = "createDockTagsExceptionCount",
      level1 = "uwms-receiving",
      level2 = "dockTagController",
      level3 = "createDockTags")
  @ApiResponses(value = {@ApiResponse(responseCode = "201", description = "")})
  public ResponseEntity<DockTagResponse> createDockTags(
      @RequestBody @Valid CreateDockTagRequest createDockTagRequest,
      @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    LOGGER.info("Got request to create multiple dock tags");
    DockTagResponse response =
        tenantSpecificConfigReader
            .getConfiguredInstance(
                TenantContext.getFacilityNum().toString(),
                ReceivingConstants.DOCK_TAG_SERVICE,
                DockTagService.class)
            .createDockTags(createDockTagRequest, httpHeaders);
    return new ResponseEntity<>(response, HttpStatus.CREATED);
  }

  @GetMapping(path = "/count", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Count dock tags for a given facilityNumber",
      description = "This will return a 200")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum"),
    @Parameter(name = "WMT-UserId", required = true)
  })
  @Timed(
      name = "countDockTagsTimed",
      level1 = "uwms-receiving",
      level2 = "dockTagController",
      level3 = "countDockTags")
  @ExceptionCounted(
      name = "countDockTagExceptionCount",
      level1 = "uwms-receiving",
      level2 = "dockTagController",
      level3 = "countDockTag")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public ResponseEntity<OpenDockTagCount> countDockTag(
      @RequestParam(value = "status") InstructionStatus status,
      @RequestHeader HttpHeaders httpHeaders) {
    OpenDockTagCount totalDockTags = dockTagService.countDockTag(status);
    return new ResponseEntity<>(totalDockTags, HttpStatus.OK);
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Get all dock tags for given facility number and country code",
      description = "This will return a 200")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true)
  })
  @Timed(
      name = "searchDockTagTimedByFacility",
      level1 = "uwms-receiving",
      level2 = "dockTagController",
      level3 = "searchDockTagsByFacility")
  @ExceptionCounted(
      name = "searchDockTagExceptionCount",
      level1 = "uwms-receiving",
      level2 = "dockTagController",
      level3 = "searchDockTag")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public ResponseEntity<List<DockTag>> searchAllDockTagsForGivenTenant(
      @RequestParam(value = "status", required = true) InstructionStatus status,
      @RequestHeader HttpHeaders httpHeaders) {

    List<DockTag> getAllDockTags = dockTagService.searchAllDockTagForGivenTenant(status);
    if (StringUtils.isEmpty(getAllDockTags)) {
      return new ResponseEntity<>(getAllDockTags, HttpStatus.NO_CONTENT);
    }
    return new ResponseEntity<>(getAllDockTags, HttpStatus.OK);
  }

  @PatchMapping(path = "/complete", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Completes the provided docktags for multiple delivery",
      description = "This will return a 200 on successful and 207 on partial failure.")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true)
  })
  @Timed(
      name = "completeDockTagsForMultipleDeliveriesTimed",
      level1 = "uwms-receiving",
      level2 = "dockTagController",
      level3 = "completeDockTagsForMultipleDeliveries")
  @ExceptionCounted(
      name = "completeDockTagsExceptionCount",
      level1 = "uwms-receiving",
      level2 = "dockTagController",
      level3 = "completeDockTags")
  public ResponseEntity<List<CompleteDockTagResponse>> completeDockTagsForGivenDeliveries(
      @Valid @RequestBody CompleteDockTagRequestsList completeDockTagRequests,
      @RequestHeader HttpHeaders headers) {
    List<CompleteDockTagResponse> completeDockTagResponses =
        tenantSpecificConfigReader
            .getConfiguredInstance(
                TenantContext.getFacilityNum().toString(),
                ReceivingConstants.DOCK_TAG_SERVICE,
                DockTagService.class)
            .completeDockTagsForGivenDeliveries(completeDockTagRequests, headers);

    for (CompleteDockTagResponse completeDockTagResponse : completeDockTagResponses) {
      if (!CollectionUtils.isEmpty(completeDockTagResponse.getFailed()))
        return new ResponseEntity<>(completeDockTagResponses, HttpStatus.MULTI_STATUS);
    }
    return new ResponseEntity<>(completeDockTagResponses, HttpStatus.OK);
  }

  @PutMapping(path = "/v2/{dockTagId}/complete", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Partial complete for the given dock tag",
      description = "This will return a 200 on SUCCESS and 500 on FAILURE")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true)
  })
  @Timed(
      name = "completeDockTagV2Timed",
      level1 = "uwms-receiving",
      level2 = "dockTagController",
      level3 = "completeDockTagV2")
  @ExceptionCounted(
      name = "completeDockTagV2ExceptionCount",
      level1 = "uwms-receiving",
      level2 = "dockTagController",
      level3 = "completeDockTagV2")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public ResponseEntity<DockTagResponse> completeDockTagV2(
      @RequestBody @Valid CreateDockTagRequest createDockTagRequest,
      @PathVariable(value = "dockTagId", required = true) String dockTagId,
      @QueryParam(value = "retry") boolean retry,
      @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    DockTagResponse partialCompleteDockTagResponse =
        tenantSpecificConfigReader
            .getConfiguredInstance(
                TenantContext.getFacilityNum().toString(),
                ReceivingConstants.DOCK_TAG_SERVICE,
                DockTagService.class)
            .partialCompleteDockTag(createDockTagRequest, dockTagId, retry, httpHeaders);
    return new ResponseEntity<>(partialCompleteDockTagResponse, HttpStatus.OK);
  }

  @GetMapping(path = "/{dockTagId}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Get dock tag details for given dockTagId",
      description = "This will return a 200")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true)
  })
  @Timed(
      name = "dockTagDetailsByDockTagIdTimed",
      level1 = "uwms-receiving",
      level2 = "dockTagController",
      level3 = "dockTagDetailsByDockTagId")
  @ExceptionCounted(
      name = "dockTagDetailsByDockTagIdCount",
      level1 = "uwms-receiving",
      level2 = "dockTagController",
      level3 = "dockTagDetailsByDockTagId")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public ResponseEntity<DockTagDTO> getDockTagDetailsByDockTagId(
      @PathVariable(value = "dockTagId") String dockTagId) {

    DockTagDTO dockTagDetails =
        tenantSpecificConfigReader
            .getConfiguredInstance(
                TenantContext.getFacilityNum().toString(),
                ReceivingConstants.DOCK_TAG_SERVICE,
                DockTagService.class)
            .searchDockTagById(dockTagId);

    return new ResponseEntity<>(dockTagDetails, HttpStatus.OK);
  }
}
