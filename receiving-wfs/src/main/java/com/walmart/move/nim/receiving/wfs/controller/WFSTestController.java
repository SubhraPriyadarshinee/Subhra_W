package com.walmart.move.nim.receiving.wfs.controller;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.wfs.service.WFSCompleteDeliveryProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("test")
@RestController
public class WFSTestController {
  private static final Logger LOGGER = LoggerFactory.getLogger(WFSTestController.class);

  private static final String DELIVERY_PROCESSOR = "WfsCompleteDeliveryProcessor";

  @Qualifier(DELIVERY_PROCESSOR)
  @Autowired
  private WFSCompleteDeliveryProcessor wfsCompleteDeliveryProcessor;

  @GetMapping("/scheduler")
  public String executeSchedulerWFSAutoComplete() throws ReceivingException {
    LOGGER.info("WFS test controller started");
    wfsCompleteDeliveryProcessor.autoCompleteDeliveries(4093);
    return "WFS test controller completed successfully";
  }
}
