package com.walmart.move.nim.receiving.fixture.controller;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.sanitize;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.fixture.common.FixtureConstants;
import com.walmart.move.nim.receiving.fixture.event.processor.FixtureDeliveryEventProcessor;
import com.walmart.move.nim.receiving.fixture.model.PutAwayInventory;
import com.walmart.move.nim.receiving.fixture.service.ControlTowerService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.List;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ConditionalOnExpression("${enable.fixture.app:false}")
@RestController
@RequestMapping("fixture/test")
public class FixtureTestController {
  private static final Logger LOGGER = LoggerFactory.getLogger(FixtureTestController.class);

  @Autowired ControlTowerService controlTowerService;

  @Resource(name = FixtureConstants.FIXTURE_DELIVERY_EVENT_PROCESSOR)
  private FixtureDeliveryEventProcessor fixtureDeliveryEventProcessor;

  @PostMapping(path = "/controltower/inventory", consumes = "application/json")
  public String postInventoryToCT(@RequestBody List<PutAwayInventory> putAwayInventory) {
    ReceivingUtils.validateApiAccessibility();
    return sanitize(controlTowerService.putAwayInventory(putAwayInventory));
  }

  @PostMapping(path = "/pregen/containers", consumes = "application/json")
  public void generateContainers(
      @RequestBody DeliveryUpdateMessage deliveryUpdateMessage,
      @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    ReceivingUtils.setTenantContext(
        deliveryUpdateMessage.getEvent().getFacilityNum().toString(),
        deliveryUpdateMessage.getEvent().getFacilityCountryCode(),
        deliveryUpdateMessage.getEvent().getWMTCorrelationId(),
        this.getClass().getName());
    fixtureDeliveryEventProcessor.processEvent(deliveryUpdateMessage);
    TenantContext.clear();
  }
}
