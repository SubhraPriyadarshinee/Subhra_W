package com.walmart.move.nim.receiving.rc.controller;

import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.INVALID_WORKFLOW_REQUEST;
import static com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants.WORKFLOW_CREATE_WITH_TRK_ID_NOT_SUPPORTED_ERROR_MSG;

import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingNotImplementedException;
import com.walmart.move.nim.receiving.core.model.PaginatedResponse;
import com.walmart.move.nim.receiving.rc.contants.RcConstants;
import com.walmart.move.nim.receiving.rc.entity.ReceivingWorkflow;
import com.walmart.move.nim.receiving.rc.model.dto.request.RcWorkflowCreateRequest;
import com.walmart.move.nim.receiving.rc.model.dto.request.RcWorkflowSearchRequest;
import com.walmart.move.nim.receiving.rc.model.dto.request.RcWorkflowStatsRequest;
import com.walmart.move.nim.receiving.rc.model.dto.request.RcWorkflowUpdateRequest;
import com.walmart.move.nim.receiving.rc.model.dto.response.RcWorkflowResponse;
import com.walmart.move.nim.receiving.rc.model.dto.response.RcWorkflowStatsResponse;
import com.walmart.move.nim.receiving.rc.service.RcItemImageService;
import com.walmart.move.nim.receiving.rc.service.RcWorkflowService;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.Objects;
import javax.validation.Valid;
import javax.validation.constraints.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ConditionalOnExpression("${enable.rc.app:false}")
@RestController
@RequestMapping(RcConstants.RETURNS_WORKFLOW_URI)
@Validated
@Tag(name = "Return center workflow controller", description = "Return Center Workflow")
public class RcWorkflowController {

  private static final Logger LOGGER = LoggerFactory.getLogger(RcWorkflowController.class);

  @Autowired private RcWorkflowService rcWorkflowService;

  @Autowired private RcItemImageService itemImageService;

  @PostMapping
  @Operation(
      summary = "This will create a new workflow",
      description = "This will return a 201 on successful.")
  @Parameters({
    @Parameter(name = RcConstants.TENENT_COUNTRY_CODE, required = true),
    @Parameter(name = RcConstants.TENENT_FACLITYNUM, required = true),
    @Parameter(name = RcConstants.USER_ID_HEADER_KEY, required = true),
    @Parameter(name = RcConstants.CORRELATION_ID_HEADER_KEY, required = true)
  })
  @Timed(
      name = "rcWorkflowCreateTimed",
      level1 = "uwms-receiving",
      level2 = "rcWorkflowController",
      level3 = "createWorkflow")
  @ExceptionCounted(
      name = "rcCreateWorkflowExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rcWorkflowController",
      level3 = "createWorkflow")
  @TimeTracing(component = AppComponent.RC, type = Type.REST, flow = "CreateWorkflow")
  public ResponseEntity<String> createWorkflow(
      @RequestBody @Valid RcWorkflowCreateRequest rcWorkflowCreateRequest,
      @RequestParam(name = "publishEvents", required = false, defaultValue = "false")
          Boolean publishEvents,
      @RequestHeader HttpHeaders httpHeaders) {
    // Creating a workflow with trackingId is not supported for now
    if (!CollectionUtils.isEmpty(rcWorkflowCreateRequest.getItems())
        && rcWorkflowCreateRequest
            .getItems()
            .stream()
            .filter(Objects::nonNull)
            .anyMatch(rcWorkflowItem -> Objects.nonNull(rcWorkflowItem.getItemTrackingId()))) {
      LOGGER.error(
          WORKFLOW_CREATE_WITH_TRK_ID_NOT_SUPPORTED_ERROR_MSG + " Request: {}",
          rcWorkflowCreateRequest);
      throw new ReceivingNotImplementedException(
          INVALID_WORKFLOW_REQUEST, WORKFLOW_CREATE_WITH_TRK_ID_NOT_SUPPORTED_ERROR_MSG);
    }
    ReceivingWorkflow receivingWorkflow =
        rcWorkflowService.createWorkflow(rcWorkflowCreateRequest, httpHeaders, publishEvents);
    return new ResponseEntity<>(receivingWorkflow.getWorkflowId(), HttpStatus.CREATED);
  }

  @GetMapping(path = RcConstants.FETCH_TRACKING_ID_URI)
  @Operation(
      summary = "This will return workflow details by ID",
      description = "This will return a 200 on successful.")
  @Parameters({
    @Parameter(name = RcConstants.TENENT_COUNTRY_CODE, required = true),
    @Parameter(name = RcConstants.TENENT_FACLITYNUM, required = true),
    @Parameter(name = RcConstants.USER_ID_HEADER_KEY, required = true),
    @Parameter(name = RcConstants.CORRELATION_ID_HEADER_KEY, required = true)
  })
  @Timed(
      name = "rcGetWorkflowByIdTimed",
      level1 = "uwms-receiving",
      level2 = "rcWorkflowController",
      level3 = "getWorkflowById")
  @ExceptionCounted(
      name = "rcGetWorkflowByIdExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rcWorkflowController",
      level3 = "getWorkflowById")
  public ResponseEntity<String> fetchWorkFlowId(
      @PathVariable(name = "itemTrackingId", required = false) String itemTrackingId,
      @RequestHeader HttpHeaders httpHeaders) {
    return new ResponseEntity<>(
        rcWorkflowService.fetchByItemTrackingId(itemTrackingId), HttpStatus.OK);
  }

  @GetMapping(path = RcConstants.WORKFLOW_ID_URI)
  @Operation(
      summary = "This will return workflow details by ID",
      description = "This will return a 200 on successful.")
  @Parameters({
    @Parameter(name = RcConstants.TENENT_COUNTRY_CODE, required = true),
    @Parameter(name = RcConstants.TENENT_FACLITYNUM, required = true),
    @Parameter(name = RcConstants.USER_ID_HEADER_KEY, required = true),
    @Parameter(name = RcConstants.CORRELATION_ID_HEADER_KEY, required = true)
  })
  @Timed(
      name = "rcGetWorkflowByIdTimed",
      level1 = "uwms-receiving",
      level2 = "rcWorkflowController",
      level3 = "getWorkflowById")
  @ExceptionCounted(
      name = "rcGetWorkflowByIdExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rcWorkflowController",
      level3 = "getWorkflowById")
  @TimeTracing(component = AppComponent.RC, type = Type.REST, flow = "GetWorkflowById")
  public ResponseEntity<RcWorkflowResponse> getWorkflowById(
      @PathVariable(value = "workflowId") @NotEmpty String workflowId) {
    RcWorkflowResponse receivingWorkflow = rcWorkflowService.getWorkflowById(workflowId);
    return new ResponseEntity<>(receivingWorkflow, HttpStatus.OK);
  }

  @PatchMapping(path = RcConstants.WORKFLOW_ID_URI)
  @Operation(
      summary = "This will update the action of one or more workflow items",
      description = "This will return 200 on successful.")
  @Parameters({
    @Parameter(name = RcConstants.TENENT_COUNTRY_CODE, required = true),
    @Parameter(name = RcConstants.TENENT_FACLITYNUM, required = true),
    @Parameter(name = RcConstants.USER_ID_HEADER_KEY, required = true),
    @Parameter(name = RcConstants.CORRELATION_ID_HEADER_KEY, required = true)
  })
  @Timed(
      name = "rcWorkflowItemUpdateTimed",
      level1 = "uwms-receiving",
      level2 = "rcWorkflowController",
      level3 = "updateWorkflowItem")
  @ExceptionCounted(
      name = "rcUpdateWorkflowItemExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rcWorkflowController",
      level3 = "updateWorkflowItem")
  @TimeTracing(component = AppComponent.RC, type = Type.REST, flow = "UpdateWorkflowItems")
  public ResponseEntity<String> updateWorkflow(
      @PathVariable(value = "workflowId") @NotEmpty String workflowId,
      @RequestBody @Valid RcWorkflowUpdateRequest rcWorkflowUpdateRequest,
      @RequestParam(name = "publishEvents", required = false, defaultValue = "false")
          Boolean publishEvents,
      @RequestHeader HttpHeaders httpHeaders) {
    rcWorkflowService.updateWorkflow(
        workflowId, rcWorkflowUpdateRequest, httpHeaders, publishEvents);
    return new ResponseEntity<>("Successfully updated!", HttpStatus.OK);
  }

  @PostMapping(path = RcConstants.WORKFLOW_SEARCH_URI)
  @Operation(
      summary = "This will return workflow details based on search criteria",
      description = "This will return a 200 on successful.")
  @Parameters({
    @Parameter(name = RcConstants.TENENT_COUNTRY_CODE, required = true),
    @Parameter(name = RcConstants.TENENT_FACLITYNUM, required = true),
    @Parameter(name = RcConstants.USER_ID_HEADER_KEY, required = true),
    @Parameter(name = RcConstants.CORRELATION_ID_HEADER_KEY, required = true)
  })
  @Timed(
      name = "rcSearchWorkflowTimed",
      level1 = "uwms-receiving",
      level2 = "rcWorkflowController",
      level3 = "searchWorkflows")
  @ExceptionCounted(
      name = "rcSearchWorkflowExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rcWorkflowController",
      level3 = "searchWorkflows")
  @TimeTracing(component = AppComponent.RC, type = Type.REST, flow = "searchWorkflows")
  public ResponseEntity<PaginatedResponse<RcWorkflowResponse>> searchWorkflows(
      @RequestParam(
              value = "pageOffset",
              required = false,
              defaultValue = RcConstants.DEFAULT_OFFSET)
          @Min(RcConstants.MIN_OFFSET)
          int pageOffset,
      @RequestParam(
              value = "pageSize",
              required = false,
              defaultValue = RcConstants.DEFAULT_PAGE_SIZE)
          @Max(RcConstants.MAX_PAGE_SIZE)
          int pageSize,
      @RequestBody(required = false) RcWorkflowSearchRequest searchRequest,
      @RequestHeader HttpHeaders httpHeaders) {
    PaginatedResponse<RcWorkflowResponse> workflows =
        rcWorkflowService.searchWorkflows(pageOffset, pageSize, searchRequest);
    return new ResponseEntity<>(workflows, HttpStatus.OK);
  }

  @PostMapping(path = RcConstants.WORKFLOW_STATS_URI)
  @Operation(
      summary = "API will return workflow stats based on search criteria",
      description = "This will return a 200 on successful.")
  @Parameters({
    @Parameter(name = RcConstants.TENENT_COUNTRY_CODE, required = true),
    @Parameter(name = RcConstants.TENENT_FACLITYNUM, required = true),
    @Parameter(name = RcConstants.USER_ID_HEADER_KEY, required = true),
    @Parameter(name = RcConstants.CORRELATION_ID_HEADER_KEY, required = true)
  })
  @Timed(
      name = "rcWorkflowStatsTimed",
      level1 = "uwms-receiving",
      level2 = "rcWorkflowController",
      level3 = "getWorkflowStats")
  @ExceptionCounted(
      name = "rcWorkflowStatsExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rcWorkflowController",
      level3 = "getWorkflowStats")
  @TimeTracing(component = AppComponent.RC, type = Type.REST, flow = "searchWorkflows")
  public ResponseEntity<RcWorkflowStatsResponse> getWorkflowStats(
      @RequestBody(required = false) RcWorkflowStatsRequest statsRequest,
      @RequestHeader HttpHeaders httpHeaders) {
    RcWorkflowStatsResponse workflowStats = rcWorkflowService.getWorkflowStats(statsRequest);
    return new ResponseEntity<>(workflowStats, HttpStatus.OK);
  }

  @PostMapping(path = RcConstants.WORKFLOW_COMMENT_URI)
  @Operation(
      summary = "This will update workflow w/ a comment",
      description = "This will return a 200 on successful.")
  @Parameters({
    @Parameter(name = RcConstants.TENENT_COUNTRY_CODE, required = true),
    @Parameter(name = RcConstants.TENENT_FACLITYNUM, required = true),
    @Parameter(name = RcConstants.USER_ID_HEADER_KEY, required = true),
    @Parameter(name = RcConstants.CORRELATION_ID_HEADER_KEY, required = true)
  })
  @Timed(
      name = "rcSaveCommentTimed",
      level1 = "uwms-receiving",
      level2 = "rcWorkflowController",
      level3 = "saveComment")
  @ExceptionCounted(
      name = "rcSaveCommentExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rcWorkflowController",
      level3 = "saveComment")
  @TimeTracing(component = AppComponent.RC, type = Type.REST, flow = "searchWorkflows")
  public ResponseEntity<String> saveComment(
      @PathVariable(value = "workflowId") @NotEmpty String workflowId,
      @RequestBody @NotNull String comment) {
    rcWorkflowService.saveComment(workflowId, comment);
    return new ResponseEntity<>(HttpStatus.OK);
  }

  @PostMapping(path = RcConstants.WORKFLOW_IMAGE_URI)
  @Operation(
      summary = "This will upload an item image file into investigation storage",
      description = "This will return a 200 on successful.")
  @Parameters({
    @Parameter(name = RcConstants.TENENT_COUNTRY_CODE, required = true),
    @Parameter(name = RcConstants.TENENT_FACLITYNUM, required = true),
    @Parameter(name = RcConstants.USER_ID_HEADER_KEY, required = true),
    @Parameter(name = RcConstants.CORRELATION_ID_HEADER_KEY, required = true)
  })
  @Timed(
      name = "rcUploadItemImageTimed",
      level1 = "uwms-receiving",
      level2 = "rcWorkflowController",
      level3 = "uploadItemImageFile")
  @ExceptionCounted(
      name = "rcUploadItemImageExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rcWorkflowController",
      level3 = "uploadItemImageFile")
  @TimeTracing(component = AppComponent.RC, type = Type.REST, flow = "searchWorkflows")
  public ResponseEntity<String> uploadItemImageFile(
      @PathVariable(value = "workflowId") @NotEmpty String workflowId,
      @RequestParam("itemImageFile") MultipartFile[] itemImageFiles,
      RedirectAttributes redirectAttributes) {
    // TODO: Consider using only 1 file type - Add Validation
    // TODO: How shall we manage multiple image file types (w/o affecting performance to go to blob
    // to get url list)
    int successCount =
        itemImageService.uploadItemImages(
            workflowId, rcWorkflowService.getWorkflowImageCount(workflowId), itemImageFiles);
    rcWorkflowService.updateWorkflowImageCount(workflowId, successCount);
    return new ResponseEntity<>("Item Image successfully uploaded", HttpStatus.CREATED);
  }

  @GetMapping(
      path = RcConstants.WORKFLOW_IMAGE_URI + "/" + "{imageName}",
      produces = MediaType.IMAGE_JPEG_VALUE)
  @Operation(
      summary = "This will return image of an item",
      description = "This will return a 200 on successful.")
  @Parameters({
    @Parameter(name = RcConstants.TENENT_COUNTRY_CODE, required = true),
    @Parameter(name = RcConstants.TENENT_FACLITYNUM, required = true),
    @Parameter(name = RcConstants.USER_ID_HEADER_KEY, required = true),
    @Parameter(name = RcConstants.CORRELATION_ID_HEADER_KEY, required = true)
  })
  @Timed(
      name = "rcGetItemImageTimed",
      level1 = "uwms-receiving",
      level2 = "rcWorkflowController",
      level3 = "getItemImage")
  @ExceptionCounted(
      name = "rcGetItemImageExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rcWorkflowController",
      level3 = "getItemImage")
  @TimeTracing(component = AppComponent.RC, type = Type.REST, flow = "searchWorkflows")
  public @ResponseBody byte[] getItemImage(
      @PathVariable(value = "workflowId") @NotEmpty String workflowId,
      @PathVariable(value = "imageName") @NotEmpty String imageName)
      throws IOException {
    return itemImageService.downloadItemImage(workflowId, imageName);
  }
}
