package com.walmart.move.nim.receiving.controller;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.sanitize;

import com.google.gson.JsonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.core.model.InventoryAdjustmentTO;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

  private static final Logger LOGGER = LoggerFactory.getLogger(InventoryController.class);

  @Autowired TenantSpecificConfigReader tenantSpecificConfigReader;

  @Parameters({
    @Parameter(
        name = "facilityCountryCode",
        required = true,
        example = "Example: US",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "facilityNum",
        required = true,
        example = "Example: 32899",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        required = true,
        example = "Example: sysAdmin",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "Content-Type",
        required = true,
        example = "Example: application/json",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-CorrelationId",
        example = "Example: a1-b2-c3-d4-e6",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @PostMapping(path = "/adjustment")
  public ResponseEntity<?> processInventoryAdjustment(
      @RequestBody String inventoryAdjustmentRequest, @RequestHeader HttpHeaders httpHeaders) {
    LOGGER.debug(
        "Received inventory adjustment with message: {} , Headers: {}",
        inventoryAdjustmentRequest,
        httpHeaders);

    try {
      InventoryAdjustmentTO inventoryAdjustmentTO = new InventoryAdjustmentTO();

      inventoryAdjustmentTO.setHttpHeaders(
          ReceivingUtils.getForwardableHttpHeadersForInventoryAPI(httpHeaders));
      inventoryAdjustmentTO.setJsonObject(
          JsonParser.parseString(sanitize(inventoryAdjustmentRequest)).getAsJsonObject());
      EventProcessor inventoryAdjustmentProcessor =
          tenantSpecificConfigReader.getConfiguredInstance(
              TenantContext.getFacilityNum().toString(),
              ReceivingConstants.INVENTORY_ADJUSTMENT_PROCESSOR,
              EventProcessor.class);

      inventoryAdjustmentProcessor.processEvent(inventoryAdjustmentTO);
    } catch (Exception err) {
      LOGGER.error(
          "Exception occurred processing inventory adjustment : {}",
          ExceptionUtils.getStackTrace(err));
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
    return ResponseEntity.ok().build();
  }
}
