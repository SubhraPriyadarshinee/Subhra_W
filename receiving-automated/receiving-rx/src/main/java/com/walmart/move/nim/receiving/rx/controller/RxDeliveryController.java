package com.walmart.move.nim.receiving.rx.controller;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelData;
import com.walmart.move.nim.receiving.rx.builders.RxSSCCValidator;
import com.walmart.move.nim.receiving.rx.service.RxDeliveryServiceImpl;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Expose REST end points to validate SSCC scan
 *
 * @author k0g0313
 */
@RestController
@RequestMapping("deliveries")
@Validated
@Tag(name = "Validate SSCC Scan Service", description = "Validate SSCC Scan")
public class RxDeliveryController {
  private static final Logger log = LoggerFactory.getLogger(RxDeliveryController.class);

  @Autowired private RxSSCCValidator rxSSCCValidator;
  @Autowired private RxDeliveryServiceImpl rxDeliveryServiceImpl;

  @GetMapping(path = "/{deliveryNumber}/shipments/{identifier}", produces = "application/json")
  @Operation(
      summary = "Return rx details list based on delivery number and sscc",
      description = "This service will return rx details if sscc scanned is valid")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  @Timed(
      name = "getvalidateScanTimed",
      level1 = "uwms-receiving-api",
      level2 = "deliveryController",
      level3 = "RxDeliveryController")
  @ExceptionCounted(
      name = "getvalidateScanExeptionCount",
      level1 = "uwms-receiving-api",
      level2 = "deliveryController",
      level3 = "RxDeliveryController")
  public DeliveryDocumentLine getValidSSCCResponse(
      @PathVariable(value = "deliveryNumber") Long deliveryNumber,
      @PathVariable(value = "identifier") String scannedSscc,
      @RequestHeader HttpHeaders headers)
      throws ReceivingException {
    log.info("Finding if valid sscc: {} for delivery: {}", scannedSscc, deliveryNumber);

    Optional<List<DeliveryDocumentLine>> validSSCCResponse =
        rxSSCCValidator.validateScannedSSCC(deliveryNumber, scannedSscc, headers);

    return validSSCCResponse.isPresent()
        ? validSSCCResponse.get().get(0)
        : new DeliveryDocumentLine();
  }

  @PostMapping(path = "/{deliveryNumber}/prepareLabel", produces = "application/json")
  @Operation(
      summary = "Return delivery label data based on delivery number",
      description = "This service will return delivery label data based on delivery number")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  @Timed(
      name = "prepareDeliveryLabelDataTimed",
      level1 = "uwms-receiving-api",
      level2 = "deliveryController",
      level3 = "RxDeliveryController")
  @ExceptionCounted(
      name = "prepareDeliveryLabelDataExeptionCount",
      level1 = "uwms-receiving-api",
      level2 = "deliveryController",
      level3 = "RxDeliveryController")
  public PrintLabelData prepareDeliveryLabelData(
      @PathVariable(value = "deliveryNumber") Long deliveryNumber,
      @RequestParam(value = "count", required = false, defaultValue = "1") int count,
      @RequestHeader HttpHeaders headers) {
    log.info("Preparing {} delieryLabelData for delivery: {}", count, deliveryNumber);

    return rxDeliveryServiceImpl.prepareDeliveryLabelData(deliveryNumber, count, headers);
  }
}
