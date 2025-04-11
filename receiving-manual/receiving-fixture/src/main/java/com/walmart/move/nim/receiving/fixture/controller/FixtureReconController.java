package com.walmart.move.nim.receiving.fixture.controller;

import com.walmart.move.nim.receiving.fixture.service.ControlTowerService;
import com.walmart.move.nim.receiving.fixture.service.PalletReceivingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ConditionalOnExpression("${enable.fixture.app:false}")
@RestController
@RequestMapping("/recon/fixture")
public class FixtureReconController {
  @Autowired ControlTowerService controlTowerService;
  @Autowired PalletReceivingService palletReceivingService;

  @PostMapping(path = "/controltower/refreshtoken")
  public ResponseEntity<String> refreshCTToken() {
    controlTowerService.refreshToken();
    return new ResponseEntity<>("", HttpStatus.OK);
  }

  @PostMapping(path = "/controltower/inventory/{trackingId}")
  public ResponseEntity<String> postContainerToCT(
      @PathVariable(value = "trackingId") String trackingId,
      @RequestHeader HttpHeaders httpHeaders) {
    String response = palletReceivingService.postInventoryToCT(trackingId);
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @PostMapping(path = "/inventory/{trackingId}")
  public ResponseEntity<Void> publishContainerToInventory(
      @PathVariable(value = "trackingId") String trackingId,
      @RequestHeader HttpHeaders httpHeaders) {
    palletReceivingService.publishToInventory(trackingId);
    return new ResponseEntity<>(HttpStatus.OK);
  }
}
