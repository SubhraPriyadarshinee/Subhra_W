package com.walmart.move.nim.receiving.acc.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.acc.entity.NotificationLog;
import com.walmart.move.nim.receiving.acc.model.acl.notification.ACLNotification;
import com.walmart.move.nim.receiving.acc.model.acl.notification.ACLNotificationSearchResponse;
import com.walmart.move.nim.receiving.acc.service.ACLNotificationService;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.metrics.annotation.Counted;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Date;
import java.util.List;
import javax.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Expose REST endpoints to access ACL Notification events
 *
 * @author r0s01us
 */
@ConditionalOnExpression("${enable.acc.app:false}")
@RestController
@Validated
@RequestMapping("acl-logs")
@Tag(name = "ACL Notification Service", description = "ACL Notification")
public class ACLNotificationController {

  @Resource(name = ReceivingConstants.ACC_NOTIFICATION_SERVICE)
  private ACLNotificationService aclNotificationService;

  private Gson gson;

  public ACLNotificationController() {
    gson = new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
  }

  /**
   * Controller method to retrieve the ACL Notifications for a given locationId
   *
   * @param locationId
   * @param pageable (support for request parameters like 'page=0' and 'size=0')
   * @return {@link ACLNotificationSearchResponse}
   */
  @GetMapping(path = "/{locationId}", produces = "application/json")
  @Operation(
      summary = "Return acl notification logs based on location id as response",
      description = "This will return a 200")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  @Timed(
      name = "aclNotificationHistoryTimed",
      level1 = "uwms-receiving",
      level2 = "aclNotificationController",
      level3 = "aclNotificationHistory")
  @ExceptionCounted(
      name = "aclNotificationHistoryExceptionCount",
      level1 = "uwms-receiving",
      level2 = "aclNotificationController",
      level3 = "aclNotificationHistory")
  @Counted(
      name = "aclNotificationHistoryHitCount",
      level1 = "uwms-receiving",
      level2 = "aclNotificationController",
      level3 = "aclNotificationHistory")
  public ResponseEntity<String> getDeviceFeed(
      @PathVariable String locationId,
      @RequestParam("page") int pageIndex,
      @RequestParam("size") int pageSize) {
    if (!ReceivingUtils.isValidLocation(locationId)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_LOCATION, ReceivingConstants.INVALID_LOCATION);
    }
    ACLNotificationSearchResponse response =
        aclNotificationService.getAclNotificationSearchResponse(locationId, pageIndex, pageSize);

    return new ResponseEntity<>(
        gson.toJson(response, ACLNotificationSearchResponse.class), HttpStatus.OK);
  }

  /**
   * Controller method to save one or more NotificationLog's from an ACLNotification
   *
   * @param @link ACLNotification
   * @return list of @link NotificationLog
   */
  @PostMapping(consumes = "application/json", produces = "application/json")
  @Operation(summary = "Saves a given acl notification", description = "This will return a 200")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public ResponseEntity<String> saveLog(@RequestBody ACLNotification aclNotification) {
    List<NotificationLog> logs = aclNotificationService.saveACLMessage(aclNotification);
    return new ResponseEntity<>(logs.toString(), HttpStatus.CREATED);
  }
}
