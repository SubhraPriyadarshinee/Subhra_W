package com.walmart.move.nim.receiving.controller;

import com.walmart.move.nim.receiving.core.entity.PurgeData;
import com.walmart.move.nim.receiving.core.model.PurgeOnboardRequest;
import com.walmart.move.nim.receiving.core.service.PurgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("purge")
@Tag(name = "Purge Data", description = "PurgeData")
public class PurgeController {

  @Autowired private PurgeService purgeService;

  @PostMapping(path = "/onboard", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "On-boards an entity for purging", description = "This will return a 200")
  @ApiResponses(value = {@ApiResponse(responseCode = "201", description = "")})
  public ResponseEntity<List<PurgeData>> onBoardEntity(
      @RequestBody @Valid PurgeOnboardRequest purgeOnboardRequest) {

    return new ResponseEntity<>(
        purgeService.createEntities(purgeOnboardRequest.getEntities()), HttpStatus.CREATED);
  }
}
