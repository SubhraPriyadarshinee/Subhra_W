package com.walmart.move.nim.receiving.endgame.controller;

import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.model.audit.AuditFlagResponse;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.message.common.UpdateAttributesData;
import com.walmart.move.nim.receiving.endgame.model.AuditChangeRequest;
import com.walmart.move.nim.receiving.endgame.model.UpdateAttributes;
import com.walmart.move.nim.receiving.endgame.service.EndGameSlottingService;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Collections;
import java.util.Objects;
import javax.annotation.Resource;
import javax.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ConditionalOnExpression("${enable.endgame.app:false}")
@RestController
@RequestMapping("endgame/audit")
@Tag(name = "EndGame Audit Service", description = "Endgame Audit")
public class AuditController {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuditController.class);

  @Autowired private EndGameSlottingService endGameSlottingService;

  @Resource(name = EndgameConstants.ENDGAME_DELIVERY_METADATA_SERVICE)
  protected DeliveryMetaDataService deliveryMetaDataService;

  @PostMapping
  @Operation(summary = "Change audit-flag of a given item", description = "This will return a 200")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  @Timed(name = "Endgame-AuditFlip", level1 = "uwms-receiving", level2 = "Endgame-AuditFlip")
  @ExceptionCounted(
      name = "Endgame-AuditFlip-Exception",
      level1 = "uwms-receiving",
      level2 = "Endgame-AuditFlip-Exception")
  @TimeTracing(component = AppComponent.ENDGAME, type = Type.REST, flow = "AuditFlip")
  public ResponseEntity changeDivertDestination(
      @RequestBody @Valid AuditChangeRequest auditChangeRequest) {
    LOGGER.info("Audit Flip request: {}", auditChangeRequest);
    if (Objects.nonNull(auditChangeRequest.getAuditInfo().getCaseFlagged())) {
      UpdateAttributesData changeCaseAuditRequest =
          UpdateAttributesData.builder()
              .searchCriteria(auditChangeRequest.getSearchCriteria())
              .updateAttributes(
                  UpdateAttributes.builder()
                      .isAuditEnabled(auditChangeRequest.getAuditInfo().getCaseFlagged())
                      .build())
              .build();
      endGameSlottingService.updateDivertForItemAndDelivery(changeCaseAuditRequest);
    }
    DeliveryMetaData deliveryMetaData =
        deliveryMetaDataService
            .findByDeliveryNumber(
                String.valueOf(auditChangeRequest.getSearchCriteria().getDeliveryNumber()))
            .orElseThrow(
                () ->
                    new ReceivingDataNotFoundException(
                        ExceptionCodes.DELIVERY_METADATA_NOT_FOUND,
                        String.format(
                            EndgameConstants.DELIVERY_METADATA_NOT_FOUND_ERROR_MSG,
                            auditChangeRequest.getSearchCriteria().getDeliveryNumber())));
    AuditFlagResponse auditFlagResponse =
        AuditFlagResponse.builder()
            .itemNumber(Long.valueOf(auditChangeRequest.getSearchCriteria().getItemNumber()))
            .isCaseFlagged(auditChangeRequest.getAuditInfo().getCaseFlagged())
            .isPalletFlagged(auditChangeRequest.getAuditInfo().getPalletFlagged())
            .palletSellableUnits(auditChangeRequest.getAuditInfo().getPalletSellableUnits())
            .build();
    deliveryMetaDataService.updateAuditInfo(
        deliveryMetaData, Collections.singletonList(auditFlagResponse));
    return new ResponseEntity(HttpStatus.OK);
  }
}
