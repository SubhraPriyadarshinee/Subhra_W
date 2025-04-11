package com.walmart.move.nim.receiving.rc.controller;

import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.entity.ItemTracker;
import com.walmart.move.nim.receiving.core.service.ItemTrackerService;
import com.walmart.move.nim.receiving.rc.contants.RcConstants;
import com.walmart.move.nim.receiving.rc.entity.PackageRLog;
import com.walmart.move.nim.receiving.rc.service.RcContainerService;
import com.walmart.move.nim.receiving.rc.service.RcPackageTrackerService;
import com.walmart.move.nim.receiving.rc.service.RcWorkflowService;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@ConditionalOnExpression("${enable.rc.app:false}")
@RestController
@RequestMapping(RcConstants.RETURNS_TEST_URI)
@Hidden
public class RcTestController {
  @Autowired private RcContainerService rcContainerService;
  @Autowired private RcPackageTrackerService packageTrackerService;
  @Autowired private ItemTrackerService itemTrackerService;
  @Autowired private RcWorkflowService rcWorkflowService;

  @DeleteMapping(path = "/delete/containers/{packageBarcodeValue}", produces = "application/json")
  public void deleteContainersByPackageBarcode(
      @PathVariable(value = "packageBarcodeValue") String packageBarcodeValue) {
    ReceivingUtils.validateApiAccessibility();
    rcContainerService.deleteContainersByPackageBarcode(packageBarcodeValue);
  }

  @GetMapping(path = "/tracked/package/{scannedLabel}", produces = "application/json")
  public ResponseEntity<List<PackageRLog>> getTrackedPackage(
      @PathVariable(value = "scannedLabel") String scannedLabel) {
    ReceivingUtils.validateApiAccessibility();
    return new ResponseEntity<>(
        packageTrackerService.getTrackedPackage(scannedLabel), HttpStatus.OK);
  }

  @DeleteMapping(path = "/tracked/package/{scannedLabel}")
  public void deleteTrackedPackage(@PathVariable(value = "scannedLabel") String scannedLabel) {
    ReceivingUtils.validateApiAccessibility();
    packageTrackerService.deleteTrackedPackage(scannedLabel);
  }

  @GetMapping(path = "/tracked/item/trackingId/{trackingId}", produces = "application/json")
  public ResponseEntity<List<ItemTracker>> getTrackedItemByTrackingId(
      @PathVariable(value = "trackingId") String trackingId) {
    ReceivingUtils.validateApiAccessibility();
    return new ResponseEntity<>(
        itemTrackerService.getTrackedItemByTrackingId(trackingId), HttpStatus.OK);
  }

  @DeleteMapping(path = "/tracked/item/trackingId/{trackingId}")
  public void deleteTrackedItemByTrackingId(@PathVariable(value = "trackingId") String trackingId) {
    ReceivingUtils.validateApiAccessibility();
    itemTrackerService.deleteTrackedItemByTrackingId(trackingId);
  }

  @GetMapping(path = "/tracked/item/gtin/{gtin}", produces = "application/json")
  public ResponseEntity<List<ItemTracker>> getTrackedItemByGtin(
      @PathVariable(value = "gtin") String gtin) {
    ReceivingUtils.validateApiAccessibility();
    return new ResponseEntity<>(itemTrackerService.getTrackedItemByGtin(gtin), HttpStatus.OK);
  }

  @DeleteMapping(path = "/tracked/item/gtin/{gtin}")
  public void deleteTrackedItemByGtin(@PathVariable(value = "gtin") String gtin) {
    ReceivingUtils.validateApiAccessibility();
    itemTrackerService.deleteTrackedItemByGtin(gtin);
  }

  @DeleteMapping(path = "/workflow/{workflowId}")
  public void deleteWorkflowById(@PathVariable(value = "workflowId") String workflowId) {
    ReceivingUtils.validateApiAccessibility();
    rcWorkflowService.deleteWorkflowById(workflowId);
  }

  @DeleteMapping(path = "/workflow-item/{workflowItemId}")
  public void deleteWorkflowItemById(@PathVariable(value = "workflowItemId") Long workflowItemId) {
    ReceivingUtils.validateApiAccessibility();
    rcWorkflowService.deleteWorkflowItemById(workflowItemId);
  }
}
