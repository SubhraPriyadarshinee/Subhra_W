package com.walmart.move.nim.receiving.controller;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.TENENT_COUNTRY_CODE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.TENENT_FACLITYNUM;
import static java.util.Objects.requireNonNull;

import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.model.OutboxDto;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.platform.messages.MetaData;
import com.walmart.platform.repositories.OutboxEvent;
import com.walmart.platform.service.OutboxEventSinkService;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Objects;
import javax.annotation.Resource;
import javax.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("publishOutbox")
@Tag(name = "Outbox Publishing API Service", description = "To expose Outbox Publishing Services")
public class OutboxController {

  private static final Logger log = LoggerFactory.getLogger(OutboxController.class);

  @Resource private OutboxEventSinkService outboxEventSinkService;

  @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Enables Any to start Publishing Outbox events",
      description = "This will return a 200")
  @Parameters({
    @Parameter(
        name = "facilityCountryCode",
        required = true,
        example = "Country code",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "facilityNum",
        required = true,
        example = "Site Number",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        required = true,
        example = "User ID",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  @Timed(
      name = "publishOutboxTimed",
      level1 = "uwms-receiving",
      level2 = "outboxController",
      level3 = "publishOutbox")
  @ExceptionCounted(
      name = "publishOutboxCount",
      level1 = "uwms-receiving",
      level2 = "outboxController",
      level3 = "publishOutbox")
  public ResponseEntity<String> publishOutbox(
      @Valid @RequestBody OutboxDto outboxDto, @RequestHeader HttpHeaders httpHeaders) {

    log.info("Called publishOutbox  with outbox Request : {}");
    ResponseEntity<String> response = new ResponseEntity<>("PUBLISED", HttpStatus.OK);

    String countryCode = httpHeaders.getFirst(TENENT_COUNTRY_CODE);
    int facilityNum = Integer.parseInt(requireNonNull(httpHeaders.getFirst(TENENT_FACLITYNUM)));
    TenantContext.setFacilityCountryCode(countryCode);
    TenantContext.setFacilityNum(facilityNum);

    try {

      Instant executionTs =
          outboxDto.getExecutionTs() == null ? Instant.now() : outboxDto.getExecutionTs();
      OutboxEvent outboxEvent =
          RxUtils.buildOutboxEvent(
              outboxDto.getHeaders(),
              outboxDto.getBody(),
              outboxDto.getEventIdentifier(),
              Objects.nonNull(outboxDto.getMetaData())
                  ? outboxDto.getMetaData()
                  : MetaData.emptyInstance(),
              outboxDto.getPublisherPolicyId(),
              executionTs);
      outboxEventSinkService.saveEvent(outboxEvent);
    } catch (Exception e) {
      response = new ResponseEntity<>("Outbox Publishing failed", HttpStatus.CONFLICT);
      log.error("OUTBOX PUBLISHING Failed payload {} with Error: {}", outboxDto, e);
    }
    return response;
  }
}
