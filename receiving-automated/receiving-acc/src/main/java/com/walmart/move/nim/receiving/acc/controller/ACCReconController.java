package com.walmart.move.nim.receiving.acc.controller;

import com.walmart.move.nim.receiving.acc.service.GenericLabelGeneratorService;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ConditionalOnExpression("${enable.acc.app:false}")
@RestController
@RequestMapping("automated/recon")
public class ACCReconController {

  private static final Logger LOGGER = LoggerFactory.getLogger(ACCReconController.class);

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @PutMapping("/publish-labels/{deliveryNumber}")
  public void publishLabelDataByDelivery(
      @PathVariable Long deliveryNumber, @RequestHeader HttpHeaders httpHeaders) {
    LOGGER.info("Publishing labels for deliveryNumber : {}", deliveryNumber);
    tenantSpecificConfigReader
        .getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.LABEL_GENERATOR_SERVICE,
            GenericLabelGeneratorService.class)
        .publishACLLabelDataForDelivery(deliveryNumber, httpHeaders);
  }
}
