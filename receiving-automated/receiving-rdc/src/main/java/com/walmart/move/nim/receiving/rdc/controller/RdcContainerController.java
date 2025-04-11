package com.walmart.move.nim.receiving.rdc.controller;

import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.rdc.service.RdcContainerService;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("rdc/containers")
@Tag(name = "Container service to get container details", description = "RdcContainer")
public class RdcContainerController {

  @Autowired private RdcContainerService rdcContainerService;

  @GetMapping(path = "/upc/{upcNumber}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Get container details by UPC", description = "This will return a 200")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true)
  })
  @Timed(
      name = "RdcContainerItemByUpcAPITimed",
      level1 = "uwms-receiving",
      level2 = "RdcContainerController",
      level3 = "getContainerItemsByUpc")
  @ExceptionCounted(
      name = "RdcContainerItemByUpcAPIExceptionTimed",
      level1 = "uwms-receiving",
      level2 = "RdcContainerController",
      level3 = "getContainerItemsByUpc")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public List<ContainerItem> getContainerItemsByUpc(
      @PathVariable(value = "upcNumber", required = true) String upcNumber,
      @RequestHeader HttpHeaders httpHeaders) {
    return rdcContainerService.getContainerItemsByUpc(upcNumber, httpHeaders);
  }
}
