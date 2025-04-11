/** */
package com.walmart.move.nim.receiving.controller;

import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.ContainerValidationUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.core.service.ReceiveContainerService;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.thymeleaf.util.StringUtils;

/**
 * ClientApi controller exposes all REST APIs which client (tc70 or any other client) uses.
 *
 * @author a0b02ft
 */
@RestController
@RequestMapping("receipts")
@Tag(name = "Receipt Service", description = "To expose receipt resource and related services")
public class ReceiptController {
  private static final Logger log = LoggerFactory.getLogger(ReceiptController.class);
  @Autowired private ReceiptService receiptService;
  @Autowired private ReceiveContainerService receiveContainerService;

  @Resource(name = ReceivingConstants.DELIVERY_SERVICE)
  private DeliveryService deliveryService;

  @GetMapping(
      path = "/delivery/{deliveryNumber}/summary",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Returns the receipt summary",
      description = "This will return a 200 on successful and 500 on failure.")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true)
  })
  @Timed(
      name = "receivedQtySummaryByPOForDeliveryTimed",
      level1 = "uwms-receiving",
      level2 = "receiptController",
      level3 = "receivedQtySummaryByPOForDeliveryDetails")
  @ExceptionCounted(
      name = "receivedQtySummaryByPOForDeliveryExceptionCount",
      level1 = "uwms-receiving",
      level2 = "receiptController",
      level3 = "receivedQtySummaryByPOForDeliveryDetails")
  public ResponseEntity<List<ReceiptSummaryResponse>> getReceivedQtySummaryByPOForDelivery(
      @PathVariable(value = "deliveryNumber", required = true) Long deliveryNumber,
      @RequestParam(value = "uom", required = false) String uom,
      @RequestParam(value = "deliveryStatus", required = false) String deliveryStatus,
      @RequestHeader HttpHeaders headers)
      throws ReceivingException {
    log.info("Enter getReceivedQtySummaryByPOForDelivery with deliveryNumber :{}", deliveryNumber);
    List<ReceiptSummaryResponse> receivedQtySummaryResponse = null;

    if (deliveryNumber < 0) {
      ErrorResponse errorResponse =
          ErrorResponse.builder()
              .errorMessage(ReceivingException.INVALID_DELIVERY_NUMBER)
              .errorKey(ExceptionCodes.INVALID_DELIVERY_NUMBER)
              .build();
      throw ReceivingException.builder()
          .httpStatus(HttpStatus.BAD_REQUEST)
          .errorResponse(errorResponse)
          .build();
    }

    uom = uom != null ? uom.trim() : "";
    receivedQtySummaryResponse =
        receiptService.getReceivedQtySummaryByPOForDelivery(deliveryNumber, uom);

    deliveryService.publishArrivedDeliveryStatusToOpen(deliveryNumber, deliveryStatus, headers);

    return new ResponseEntity<>(receivedQtySummaryResponse, HttpStatus.OK);
  }

  @GetMapping(
      path = "/delivery/{deliveryNumber}/qtybypo/summary",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Returns receipt summary by po for delivery number",
      description = "This will return a 200 on successful and 500 on failure.")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true)
  })
  @Timed(
      name = "getReceiptsSummaryByPoTimed",
      level1 = "uwms-receiving",
      level2 = "receiptController",
      level3 = "getReceiptsSummaryByPo")
  @ExceptionCounted(
      name = "getReceiptsSummaryByPoExceptionCount",
      level1 = "uwms-receiving",
      level2 = "receiptController",
      level3 = "getReceiptsSummaryByPo")
  public ResponseEntity<ReceiptSummaryQtyByPoResponse> getReceiptsSummaryByPo(
      @PathVariable(value = "deliveryNumber", required = true) Long deliveryNumber,
      @RequestHeader HttpHeaders headers)
      throws ReceivingException {
    ReceiptSummaryQtyByPoResponse receiptSummaryQtyByPoResponse =
        receiptService.getReceiptsSummaryByPo(deliveryNumber, headers);
    return new ResponseEntity<>(receiptSummaryQtyByPoResponse, HttpStatus.OK);
  }

  @GetMapping(
      path = "/delivery/{deliveryNumber}/qtybypoline/summary",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Returns received qty po line summary for po number",
      description = "This will return a 200 on successful and 500 on failure.")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true)
  })
  @Timed(
      name = "getReceiptsSummaryByPoLineTimed",
      level1 = "uwms-receiving",
      level2 = "receiptController",
      level3 = "getDeliverySummaryByPoLine")
  @ExceptionCounted(
      name = "getReceiptsSummaryByPoLineExceptionCount",
      level1 = "uwms-receiving",
      level2 = "receiptController",
      level3 = "getReceiptsSummaryByPoLine")
  public ResponseEntity<ReceiptSummaryQtyByPoLineResponse> getReceiptsSummaryByPoLine(
      @PathVariable(value = "deliveryNumber", required = true) Long deliveryNumber,
      @RequestParam(value = "purchaseReferenceNumber", required = true)
          String purchaseReferenceNumber,
      @RequestHeader HttpHeaders headers)
      throws ReceivingException {
    if (StringUtils.isEmpty(purchaseReferenceNumber)) {
      throw new ReceivingException(
          ReceivingException.INVALID_PO_NUMBER, HttpStatus.BAD_REQUEST, ExceptionCodes.INVALID_PO);
    }
    ReceiptSummaryQtyByPoLineResponse receiptSummaryQtyByPoLineResponse =
        receiptService.getReceiptsSummaryByPoLine(deliveryNumber, purchaseReferenceNumber, headers);
    return new ResponseEntity<>(receiptSummaryQtyByPoLineResponse, HttpStatus.OK);
  }

  @PostMapping(path = "/delivery/{deliveryNumber}/containers")
  @Operation(
      summary = "Create a pre-made container for receipt",
      description = "This will return a 201")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "201", description = "")})
  @Timed(
      name = "generateReceiptTimed",
      level1 = "uwms-receiving",
      level2 = "receiptController",
      level3 = "generateReceipt")
  @ExceptionCounted(
      name = "generateReceiptCount",
      level1 = "uwms-receiving",
      level2 = "receiptController",
      level3 = "generateReceipt")
  public ResponseEntity<Container> generateReceipt(
      @PathVariable(value = "deliveryNumber", required = true) Long deliveryNumber,
      @RequestBody ContainerRequest containerRequest,
      @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {
    log.info(
        "Enter generateReceipt with deliveryNumber: {} containerRequest :{}",
        deliveryNumber,
        containerRequest);

    ContainerValidationUtils.validateContainerRequest(containerRequest);

    return new ResponseEntity<>(
        receiveContainerService.receiveContainer(deliveryNumber, containerRequest, httpHeaders),
        HttpStatus.CREATED);
  }

  @PostMapping(path = "/deliveries", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Returns total received qty by delivery numbers",
      description = "This will return a 200 on successful and 500 on failure.")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true)
  })
  @Timed(
      name = "getReceiptQtySummaryByDeliveriesTimed",
      level1 = "uwms-receiving",
      level2 = "receiptController",
      level3 = "getReceiptQtySummaryByDeliveries")
  @ExceptionCounted(
      name = "getReceiptQtySummaryByDeliveriesExceptionCount",
      level1 = "uwms-receiving",
      level2 = "receiptController",
      level3 = "getReceiptQtySummaryByDeliveries")
  @TimeTracing(
      component = AppComponent.CORE,
      executionFlow = "getReceiptQtySummaryByDeliveries",
      type = Type.REST)
  public ResponseEntity<List<ReceiptQtySummaryByDeliveryNumberResponse>>
      getReceiptQtySummaryByDeliveries(
          @RequestBody ReceiptSummaryQtyByDeliveries receiptSummaryQtyByDeliveries,
          @RequestHeader HttpHeaders headers)
          throws ReceivingException {
    List<ReceiptQtySummaryByDeliveryNumberResponse> receiptSummaryQtyByDeliveryNumberResponse =
        receiptService.getReceiptQtySummaryByDeliveries(receiptSummaryQtyByDeliveries, headers);
    return new ResponseEntity<>(receiptSummaryQtyByDeliveryNumberResponse, HttpStatus.OK);
  }

  @GetMapping(
      path = "/delivery/{deliveryNumber}/po/{poNumber}/poLine/{poLineNumber}/storeDistribution")
  @Operation(
      summary = "Returns Delivery Document by delivery numbers, PO, POLine",
      description = "This will return a 200 on successful and 500 on failure.")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true)
  })
  @Timed(
      name = "getStoreDistributionByDeliveryAndPoPoLineTimed",
      level1 = "uwms-receiving",
      level2 = "receiptController",
      level3 = "getStoreDistributionByDeliveryAndPoPoLine")
  @ExceptionCounted(
      name = "getStoreDistributionByDeliveryAndPoPoLineNumberExceptionCount",
      level1 = "uwms-receiving",
      level2 = "receiptController",
      level3 = "getStoreDistributionByDeliveryAndPoPoLine")
  @TimeTracing(
      component = AppComponent.CORE,
      executionFlow = "getStoreDistributionByDeliveryAndPoPoLine",
      type = Type.REST)
  public ResponseEntity<List<DeliveryDocument>> getStoreDistributionByDeliveryAndPoPoLine(
      @PathVariable("deliveryNumber") Long deliveryNumber,
      @PathVariable String poNumber,
      @PathVariable int poLineNumber,
      @RequestParam("isAtlasItem") boolean isAtlasItem,
      @RequestHeader HttpHeaders headers)
      throws ReceivingException {

    List<DeliveryDocument> deliveryDocuments =
        receiptService.getStoreDistributionByDeliveryAndPoPoLine(
            deliveryNumber, poNumber, poLineNumber, headers, isAtlasItem);
    return new ResponseEntity<>(deliveryDocuments, HttpStatus.OK);
  }

  @PostMapping(path = "/poNumbers", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Returns total received qty by po numbers",
      description = "This will return a 200 on successful and 500 on failure.")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true)
  })
  @Timed(
      name = "getReceiptQtySummaryByPoNumbersTimed",
      level1 = "uwms-receiving",
      level2 = "receiptController",
      level3 = "getReceiptQtySummaryByPoNumbers")
  @ExceptionCounted(
      name = "getReceiptQtySummaryByPoNumbersExceptionCount",
      level1 = "uwms-receiving",
      level2 = "receiptController",
      level3 = "getReceiptQtySummaryByPoNumbers")
  @TimeTracing(
      component = AppComponent.CORE,
      executionFlow = "getReceiptQtySummaryByPoNumbers",
      type = Type.REST)
  public ResponseEntity<List<ReceiptQtySummaryByPoNumbersResponse>> getReceiptQtySummaryByPoList(
      @RequestBody ReceiptSummaryQtyByPos receiptSummaryQtyByPos,
      @RequestHeader HttpHeaders headers)
      throws ReceivingException {
    List<ReceiptQtySummaryByPoNumbersResponse> receiptSummaryQtyByDeliveryNumberResponse =
        receiptService.getReceiptQtySummaryByPoNumbers(receiptSummaryQtyByPos, headers);
    return new ResponseEntity<>(receiptSummaryQtyByDeliveryNumberResponse, HttpStatus.OK);
  }
}
