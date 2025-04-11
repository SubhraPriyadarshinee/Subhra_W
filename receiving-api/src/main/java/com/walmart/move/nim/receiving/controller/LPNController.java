package com.walmart.move.nim.receiving.controller;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.model.UniversalInstructionResponse;
import com.walmart.move.nim.receiving.core.service.DockTagService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("lpn")
@Tag(name = "Lpn Service", description = "To expose lpn / dock tag resource and related services")
public class LPNController {

  private static final Logger LOGGER = LoggerFactory.getLogger(LPNController.class);

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Resource(name = ReceivingConstants.DEFAULT_DOCK_TAG_SERVICE)
  private DockTagService dockTagService;

  @PutMapping("/{universalTagId}/receive")
  @Operation(
      summary =
          "Used to fetch the delivery details or instructions after scanning a non con dock tag or LPN",
      description = "This will return a 200")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true)
  })
  @Timed(
      name = "receiveUniversalTagTimed",
      level1 = "uwms-receiving",
      level2 = "lpnController",
      level3 = "receiveUniversalTag")
  @ExceptionCounted(
      name = "receiveUniversalTagExceptionCount",
      level1 = "uwms-receiving",
      level2 = "lpnController",
      level3 = "receiveUniversalTag")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public ResponseEntity<UniversalInstructionResponse> receiveUniversalTag(
      @PathVariable(value = "universalTagId") String dockTagId,
      @RequestParam(value = "doorNumber") String doorNumber,
      @RequestHeader HttpHeaders httpHeaders)
      throws ReceivingException {

    UniversalInstructionResponse universalInstructionResponse =
        tenantSpecificConfigReader
            .getConfiguredInstance(
                TenantContext.getFacilityNum().toString(),
                ReceivingConstants.DOCK_TAG_SERVICE,
                DockTagService.class)
            .receiveUniversalTag(dockTagId, doorNumber, httpHeaders);
    TenantContext.get().setAtlasRcvCrInsEnd(System.currentTimeMillis());
    long cumulativeDBCallTime =
        ReceivingUtils.getTimeDifferenceInMillis(
                TenantContext.get().getAtlasRcvChkInsExistStart(),
                TenantContext.get().getAtlasRcvChkInsExistEnd())
            + ReceivingUtils.getTimeDifferenceInMillis(
                TenantContext.get().getAtlasRcvGetRcvdQtyStart(),
                TenantContext.get().getAtlasRcvGetRcvdQtyEnd())
            + ReceivingUtils.getTimeDifferenceInMillis(
                TenantContext.get().getAtlasRcvChkNewInstCanBeCreatedStart(),
                TenantContext.get().getAtlasRcvChkNewInstCanBeCreatedEnd())
            + ReceivingUtils.getTimeDifferenceInMillis(
                TenantContext.get().getAtlasRcvCompInsSaveStart(),
                TenantContext.get().getAtlasRcvCompInsSaveEnd());
    long processTimeGdm =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getAtlasRcvGdmGetDocLineStart(),
            TenantContext.get().getAtlasRcvGdmGetDocLineEnd());
    long processTimeWithOfGdm =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getAtlasRcvCrInsStart(), TenantContext.get().getAtlasRcvCrInsEnd());
    long processTimeWithoutOfGdm =
        processTimeWithOfGdm
            - processTimeGdm
            - ReceivingUtils.getTimeDifferenceInMillis(
                TenantContext.get().getAtlasRcvOfCallStart(),
                TenantContext.get().getAtlasRcvOfCallEnd());

    long lpnServiceProcessTime =
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getAtlasRcvLpnCallStart(),
            TenantContext.get().getAtlasRcvLpnCallEnd());

    String messageId = "";
    if (universalInstructionResponse != null
        && universalInstructionResponse.getInstruction() != null) {
      messageId = universalInstructionResponse.getInstruction().getMessageId();
    }

    LOGGER.warn(
        "LatencyCheck receiving item for single PO at ts ={} time in publishing delivery update ={} timeTakenTillGDMCall={} timeTakenByGDM={} timeTillOFcall={} timeTakenByOF={} timeTakenInEntireFlow={} timeTakenByReceivingOnly={}  timeTakenByAllDBcallInReceiving={} lpnServiceProcessTime={} messageid={}",
        TenantContext.get().getAtlasRcvCrInsStart(),
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getAtlasRcvGdmDelStatPubStart(),
            TenantContext.get().getAtlasRcvGdmDelStatPubEnd()),
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getAtlasRcvCrInsStart(),
            TenantContext.get().getAtlasRcvGdmGetDocLineStart()),
        processTimeGdm,
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getAtlasRcvCrInsStart(),
            TenantContext.get().getAtlasRcvOfCallStart()),
        ReceivingUtils.getTimeDifferenceInMillis(
            TenantContext.get().getAtlasRcvOfCallStart(),
            TenantContext.get().getAtlasRcvOfCallEnd()),
        processTimeWithOfGdm,
        processTimeWithoutOfGdm,
        cumulativeDBCallTime,
        lpnServiceProcessTime,
        messageId);
    return new ResponseEntity<>(universalInstructionResponse, HttpStatus.OK);
  }
}
