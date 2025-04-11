package com.walmart.move.nim.receiving.controller;

import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.model.audit.ReceivePackRequest;
import com.walmart.move.nim.receiving.core.model.audit.ReceivePackResponse;
import com.walmart.move.nim.receiving.core.model.audit.logs.AuditLogRequest;
import com.walmart.move.nim.receiving.core.model.audit.logs.AuditLogResponse;
import com.walmart.move.nim.receiving.core.service.AuditLogProcessor;
import com.walmart.move.nim.receiving.rdc.service.RdcAtlasDsdcService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("audit/")
public class AuditContainerController {

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private RdcAtlasDsdcService rdcAtlasDsdcService;

  @GetMapping(path = "packs", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Get the audit container detail",
      description = "This will return a 200 on successful and 500 on failure.")
  @Parameters({
    @Parameter(
        name = "facilityCountryCode",
        required = true,
        example = "Example: US",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "facilityNum",
        required = true,
        example = "Example: 32899",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        required = true,
        example = "Example: sysAdmin",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "deliveryNumber",
        required = true,
        example = "Example: 13025774",
        description = "String",
        in = ParameterIn.QUERY),
    @Parameter(
        name = "status",
        required = false,
        example = "Example: complete",
        description = "String",
        in = ParameterIn.QUERY)
  })
  @Timed(name = "getAuditLogs", level1 = "uwms-receiving-api", level2 = "auditLogController")
  @ExceptionCounted(
      name = "getAuditLogsExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "auditLogController")
  public AuditLogResponse getAuditLogs(
      @RequestParam(value = "deliveryNumber") Long deliveryNumber,
      @RequestParam(value = "status", required = false) String status,
      @RequestHeader HttpHeaders httpHeaders) {
    AuditLogRequest auditLogRequest = new AuditLogRequest(deliveryNumber, status, httpHeaders);
    AuditLogProcessor auditLogProcessor =
        tenantSpecificConfigReader.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.AUDIT_LOG_PROCESSOR,
            AuditLogProcessor.class);
    AuditLogResponse auditLogResponse = auditLogProcessor.getAuditLogs(auditLogRequest);
    return auditLogResponse;
  }

  @PostMapping(path = "pack/receive", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Receive pack details",
      description =
          "This will return a 200 on successful,  404 Scanned pack Number is not found in GDM (unlikely), No Allocation found in Label Data, 409 There's no pending Audit tag available on Receiving, 500 Server error during SSCC Receiving")
  @Parameters({
    @Parameter(
        name = "facilityCountryCode",
        required = true,
        example = "Example: US",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "facilityNum",
        required = true,
        example = "Example: 32899",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        required = true,
        example = "Example: sysAdmin",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "asnNumber",
        required = true,
        example = "Example: 43432323",
        description = "String",
        in = ParameterIn.QUERY),
    @Parameter(
        name = "packNumber",
        required = false,
        example = "Example: 00000301720095496316",
        description = "String",
        in = ParameterIn.QUERY)
  })
  @Timed(name = "receivePack", level1 = "uwms-receiving-api", level2 = "auditLogController")
  @ExceptionCounted(
      name = "receivePackExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "auditLogController")
  public ReceivePackResponse receivePack(
      @RequestParam(value = "asnNumber") String asnNumber,
      @RequestParam(value = "packNumber") String packNumber,
      @RequestHeader HttpHeaders httpHeaders)
      throws Exception {
    ReceivePackRequest receivePackRequest = new ReceivePackRequest(asnNumber, packNumber, null);
    return rdcAtlasDsdcService.receivePack(receivePackRequest, httpHeaders);
  }

  @PutMapping(path = "pack", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Update Audit pack status",
      description =
          "This will return a 200 on successful, 409 There's no pending Audit tag available on Receiving or SSCC & ASN combination does not exist")
  @Parameters({
    @Parameter(
        name = "facilityCountryCode",
        required = true,
        example = "Example: US",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "facilityNum",
        required = true,
        example = "Example: 32899",
        description = "String",
        in = ParameterIn.HEADER),
    @Parameter(
        name = "WMT-UserId",
        required = true,
        example = "Example: sysAdmin",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @Timed(name = "updatePack", level1 = "uwms-receiving-api", level2 = "auditLogController")
  @ExceptionCounted(
      name = "receivePackExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "auditLogController")
  public ResponseEntity<String> updateAuditPackStatus(
      @RequestBody ReceivePackRequest receivePackRequest, @RequestHeader HttpHeaders httpHeaders)
      throws Exception {
    return new ResponseEntity<>(
        rdcAtlasDsdcService.updatePack(receivePackRequest, httpHeaders), HttpStatus.OK);
  }
}
