package com.walmart.move.nim.receiving.controller;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.sanitize;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.TENENT_FACLITYNUM;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.model.Problem;
import com.walmart.move.nim.receiving.core.model.ProblemTagResponse;
import com.walmart.move.nim.receiving.core.model.ProblemTicketResponseCount;
import com.walmart.move.nim.receiving.core.service.ProblemService;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("problems")
@Tag(name = "Problem Service", description = "To expose problem related services")
public class ProblemController {

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @GetMapping(path = "/{problemTag}", produces = "application/json")
  @Operation(
      summary = "Return problem information for a given problemTag",
      description = "This will return a 200")
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
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  @Timed(
      name = "getProblemTagDetailsTimed",
      level1 = "uwms-receiving",
      level2 = "problemController",
      level3 = "getProblemTagDetails")
  @ExceptionCounted(
      name = "getProblemTagDetailsExceptionCount",
      level1 = "uwms-receiving",
      level2 = "problemController",
      level3 = "getProblemTagDetails")
  public ProblemTagResponse getProblemTagDetails(
      @PathVariable(value = "problemTag", required = true) String problemTag,
      @RequestHeader HttpHeaders headers,
      @NotBlank @RequestHeader(required = true, name = ReceivingConstants.TENENT_FACLITYNUM)
          String facilityNum)
      throws ReceivingException {

    return tenantSpecificConfigReader
        .getConfiguredInstance(
            facilityNum, ReceivingConstants.PROBLEM_SERVICE, ProblemService.class)
        .txGetProblemTagInfo(problemTag, headers);
  }

  @GetMapping(path = "/deliveryNumber/{deliveryNumber}", produces = "application/json")
  @Operation(
      summary = "Get a problems for given delivery number",
      description = "This will return a 200")
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
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  @Timed(
      name = "getProblemsForDeliveryTimed",
      level1 = "uwms-receiving",
      level2 = "problemController",
      level3 = "getProblemsForDelivery")
  @ExceptionCounted(
      name = "getProblemsForDeliveryExceptionCount",
      level1 = "uwms-receiving",
      level2 = "problemController",
      level3 = "getProblemsForDelivery")
  public String getProblemsForDelivery(
      @PathVariable(value = "deliveryNumber") int deliveryNumber,
      @RequestHeader HttpHeaders headers,
      @NotBlank @RequestHeader(name = TENENT_FACLITYNUM) String facilityNum)
      throws ReceivingException {

    return tenantSpecificConfigReader
        .getConfiguredInstance(
            facilityNum, ReceivingConstants.PROBLEM_SERVICE, ProblemService.class)
        .getProblemsForDelivery(deliveryNumber, headers);
  }

  @PutMapping(path = "/{problemTag}/complete", produces = "application/json")
  @Operation(
      summary = "Complete a problem tag for a given id",
      description = "This will return a 200")
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
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "problemTag completed successfully")
      })
  @Timed(
      name = "completeProblemTagTimed",
      level1 = "uwms-receiving",
      level2 = "problemController",
      level3 = "completeProblemTag")
  @ExceptionCounted(
      name = "completeProblemTagExceptionCount",
      level1 = "uwms-receiving",
      level2 = "problemController",
      level3 = "completeProblemTag")
  public String completeProblemTag(
      @PathVariable(value = "problemTag", required = true) String problemTagId,
      @RequestBody /*@Valid*/
          Problem problem, // TODO Payload validation disabled to support volta (Problem App)
      @RequestHeader HttpHeaders headers,
      @NotBlank @RequestHeader(required = true, name = TENENT_FACLITYNUM) String facilityNum)
      throws ReceivingException {
    return tenantSpecificConfigReader
        .getConfiguredInstance(
            facilityNum, ReceivingConstants.PROBLEM_SERVICE, ProblemService.class)
        .completeProblemTag(problemTagId, problem, headers);
  }

  @PostMapping
  @Operation(summary = "Create a problem", description = "This will return a 200")
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
        example = "Example: sysadmin",
        description = "String",
        in = ParameterIn.HEADER)
  })
  @ApiResponses(
      value = {@ApiResponse(responseCode = "200", description = "problemTag created successfully")})
  @Timed(
      name = "createProblemTimed",
      level1 = "uwms-receiving",
      level2 = "problemController",
      level3 = "createProblem")
  @ExceptionCounted(
      name = "createProblemExceptionCount",
      level1 = "uwms-receiving",
      level2 = "problemController",
      level3 = "createProblem")
  public String createProblem(
      @RequestBody String createProblemRequest,
      @NotBlank @RequestHeader(required = true, name = TENENT_FACLITYNUM) String facilityNum)
      throws ReceivingException {
    return tenantSpecificConfigReader
        .getConfiguredInstance(
            sanitize(facilityNum), ReceivingConstants.PROBLEM_SERVICE, ProblemService.class)
        .createProblemTag(sanitize(createProblemRequest));
  }

  @GetMapping(path = "/po/{poNumber}/problemTicketCount", produces = "application/json")
  @Operation(
      summary = "Fetch open problem tickets for a given PO",
      description = "This will return a 200")
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
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  @Timed(
      name = "getProblemsTicketsForPOTimed",
      level1 = "uwms-receiving",
      level2 = "problemController",
      level3 = "getProblemsTicketsForPO")
  @ExceptionCounted(
      name = "getProblemsTicketsForPOExceptionCount",
      level1 = "uwms-receiving",
      level2 = "problemController",
      level3 = "getProblemsTicketsForPO")
  public ProblemTicketResponseCount getProblemTicketsForPO(
      @PathVariable(value = "poNumber", required = true) String poNumber,
      @RequestHeader HttpHeaders headers,
      @NotBlank @RequestHeader(name = TENENT_FACLITYNUM) String facilityNum)
      throws ReceivingException {

    return tenantSpecificConfigReader
        .getConfiguredInstance(
            facilityNum, ReceivingConstants.PROBLEM_SERVICE, ProblemService.class)
        .getProblemTicketsForPo(poNumber, headers);
  }
}
