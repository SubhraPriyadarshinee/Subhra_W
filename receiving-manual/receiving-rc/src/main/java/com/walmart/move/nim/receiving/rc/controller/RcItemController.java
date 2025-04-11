package com.walmart.move.nim.receiving.rc.controller;

import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.model.ItemTrackerRequest;
import com.walmart.move.nim.receiving.core.service.ItemTrackerService;
import com.walmart.move.nim.receiving.rc.contants.RcConstants;
import com.walmart.move.nim.receiving.rc.model.dto.request.RcItemTrackerRequest;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@ConditionalOnExpression("${enable.rc.app:false}")
@RestController
@RequestMapping(RcConstants.RETURNS_ITEM_URI)
@Tag(name = "Return center item service", description = "To expose item resource")
public class RcItemController {

  @Autowired private ItemTrackerService itemTrackerService;

  @PostMapping(path = RcConstants.ITEM_TRACKER_URI)
  @Operation(
      summary = "This will track item details for a return.",
      description = "This will return a 201 on successful.")
  @Parameters({
    @Parameter(name = RcConstants.TENENT_COUNTRY_CODE, required = true),
    @Parameter(name = RcConstants.TENENT_FACLITYNUM, required = true),
    @Parameter(name = RcConstants.USER_ID_HEADER_KEY, required = true),
    @Parameter(name = RcConstants.CORRELATION_ID_HEADER_KEY, required = true)
  })
  @Timed(
      name = "rcTrackItemTimed",
      level1 = "uwms-receiving",
      level2 = "rcItemController",
      level3 = "rcTrackItem")
  @ExceptionCounted(
      name = "rcTrackItemExceptionCount",
      level1 = "uwms-receiving",
      level2 = "rcItemController",
      level3 = "rcTrackItem")
  @TimeTracing(component = AppComponent.RC, type = Type.REST, flow = "TrackItem")
  public ResponseEntity trackItem(
      @RequestBody RcItemTrackerRequest rcItemTrackerRequest,
      @RequestHeader HttpHeaders httpHeaders) {
    ItemTrackerRequest itemTrackerRequest = new ItemTrackerRequest();
    itemTrackerRequest.setTrackingId(rcItemTrackerRequest.getScannedLabel());
    itemTrackerRequest.setGtin(rcItemTrackerRequest.getScannedItemLabel());
    itemTrackerRequest.setReasonCode(rcItemTrackerRequest.getReasonCode());
    itemTrackerService.trackItem(itemTrackerRequest);
    return new ResponseEntity("Successfully created", HttpStatus.CREATED);
  }
}
