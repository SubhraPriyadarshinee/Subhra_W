package com.walmart.move.nim.receiving.sib.controller;

import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.sib.service.ManualFinalizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

@ConditionalOnExpression("${enable.sib.app:false}")
@RestController
@RequestMapping("/store/deliveries")
public class StoreDeliveryController {

  private static Logger LOG = LoggerFactory.getLogger(StoreDeliveryController.class);

  @Autowired private ManualFinalizationService manualFinalizationService;

  @PutMapping("/{deliveryNumber}/manualFinalize")
  @TimeTracing(component = AppComponent.CORE, type = Type.REST, flow = "manual-finalization")
  public void manualFinalize(
      @PathVariable("deliveryNumber") long deliveryNumber,
      @RequestHeader HttpHeaders headers,
      @RequestParam(name = "withUnload", required = false) boolean withUnload,
      @RequestParam(name = "doorNumber", defaultValue = "999") String doorNumber) {

    manualFinalizationService.manualFinalize(deliveryNumber, doorNumber, withUnload, headers);
    LOG.info("Manual finalization initiated for delivery number {} ", deliveryNumber);
  }
}
