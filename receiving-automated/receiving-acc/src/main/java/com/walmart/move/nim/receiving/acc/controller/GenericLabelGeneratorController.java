package com.walmart.move.nim.receiving.acc.controller;

import com.walmart.move.nim.receiving.acc.constants.ACCConstants;
import com.walmart.move.nim.receiving.acc.service.GenericLabelGeneratorService;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.label.FormattedLabels;
import io.strati.metrics.annotation.Counted;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@ConditionalOnExpression("${enable.acc.app:false}")
@RestController
@Validated
@RequestMapping("label-gen")
@Tag(name = "Label generation Service", description = "Label Generation")
public class GenericLabelGeneratorController {

  @Resource(name = ACCConstants.GENERIC_LABEL_GEN_SERVICE)
  private GenericLabelGeneratorService genericLabelGeneratorService;

  /**
   * Controller method to retrieve exception label for a delivery and UPC
   *
   * @param deliveryNumber delivery number
   * @param upc upc
   * @return {@link FormattedLabels}
   */
  @GetMapping(
      path = "/deliveries/{deliveryNumber}/upcs/{upc}/exceptionLabels",
      produces = "application/json")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
      summary = "Return exception label based on delivery and UPC",
      description = "This will return a 200")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  @Timed(
      name = "exceptionLabelAPITimed",
      level1 = "uwms-receiving",
      level2 = "genericLabelGeneratorController",
      level3 = "getExceptionLabel")
  @ExceptionCounted(
      name = "exceptionLabelAPIExceptionCount",
      level1 = "uwms-receiving",
      level2 = "genericLabelGeneratorController",
      level3 = "getExceptionLabel")
  @Counted(
      name = "exceptionLabelAPIHitCount",
      level1 = "uwms-receiving",
      level2 = "genericLabelGeneratorController",
      level3 = "getExceptionLabel")
  public FormattedLabels getExceptionLabel(
      @PathVariable Long deliveryNumber, @PathVariable String upc) throws ReceivingException {
    return genericLabelGeneratorService.generateExceptionLabel(deliveryNumber, upc);
  }
}
