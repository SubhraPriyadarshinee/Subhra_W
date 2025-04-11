package com.walmart.move.nim.receiving.endgame.controller;

import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.endgame.model.DivertPriorityChangeRequest;
import com.walmart.move.nim.receiving.endgame.service.EndGameSlottingService;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
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
@RequestMapping("endgame/divert")
@Tag(name = "EndGame Divert Service", description = "Endgame Divert")
public class DivertController {

  @Autowired private EndGameSlottingService endGameSlottingService;

  @PostMapping
  @Operation(summary = "Change divert of a given UPC", description = "This will return a 200")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  @Timed(name = "Endgame-Channel-Flip", level1 = "uwms-receiving", level2 = "Endgame-Channel-Flip")
  @ExceptionCounted(
      name = "Endgame-Channel-Flip-Exception",
      level1 = "uwms-receiving",
      level2 = "Endgame-Channel-Flip-Exception")
  @TimeTracing(component = AppComponent.ENDGAME, type = Type.REST, flow = "ChannelFlip")
  public ResponseEntity changeDivertDestination(
      @RequestBody @Valid DivertPriorityChangeRequest request) {
    endGameSlottingService.changeDivertDestination(request);
    return new ResponseEntity(HttpStatus.OK);
  }
}
