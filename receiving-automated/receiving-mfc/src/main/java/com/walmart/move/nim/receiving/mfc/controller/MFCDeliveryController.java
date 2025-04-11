package com.walmart.move.nim.receiving.mfc.controller;

import com.walmart.move.nim.receiving.mfc.model.osdr.MFCOSDRPayload;
import com.walmart.move.nim.receiving.mfc.service.MFCOSDRService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.*;

@ConditionalOnExpression("${enable.mfc.app:false}")
@RestController
@RequestMapping("/mfc/deliveries")
public class MFCDeliveryController {

  private static Logger LOGGER = LoggerFactory.getLogger(MFCDeliveryController.class);
  @Autowired private MFCOSDRService mfcosdrService;

  @PostMapping("/osdr")
  public List<MFCOSDRPayload> getOSDRDetails(@RequestBody ArrayList<Long> deliveryNumbers) {
    try {
      LOGGER.info("Initiate fetch OSDR details");
      return mfcosdrService.fetchOSDRDetails(deliveryNumbers);
    } catch (Exception e) {
      LOGGER.error("Error while fetching osdr details", e);
    }
    return Collections.emptyList();
  }
}
