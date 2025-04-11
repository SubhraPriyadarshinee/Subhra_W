package com.walmart.move.nim.receiving.acc.controller;

import com.walmart.move.nim.receiving.acc.model.OverflowLPNReceivingRequest;
import com.walmart.move.nim.receiving.acc.service.OverflowLPNReceivingService;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import javax.validation.Valid;
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

@ConditionalOnExpression("${enable.acc.app:false}")
@RestController
@RequestMapping("/overflow/receive-lpn")
public class OverflowLPNReceivingController {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(OverflowLPNReceivingController.class);

  @Autowired private OverflowLPNReceivingService lpnReceivingService;

  @PostMapping(produces = "application/json")
  @Operation(
      summary = "Receive case by the same lpn at overflow location",
      description =
          "This will return a 200 if we are successfully able to receive case against same lpn")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public void receiveByLpnAtOverflow(
      @RequestBody @Valid OverflowLPNReceivingRequest request, @RequestHeader HttpHeaders headers)
      throws ReceivingException {

    LOGGER.info("Processing overflow lpn receiving request: {}", request);
    HttpHeaders httpHeaders = ReceivingUtils.getForwardableHttpHeadersV2(headers);
    lpnReceivingService.receiveByLPN(request, httpHeaders);
  }
}
