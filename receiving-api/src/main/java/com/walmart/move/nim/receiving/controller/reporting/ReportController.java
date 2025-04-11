package com.walmart.move.nim.receiving.controller.reporting;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.ReportConfig;
import com.walmart.move.nim.receiving.core.config.app.TenantSpecificReportConfig;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.Productivity;
import com.walmart.move.nim.receiving.core.model.ReceivingProductivityRequestDTO;
import com.walmart.move.nim.receiving.core.model.sso.SSOConstants;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.core.service.SSOService;
import com.walmart.move.nim.receiving.reporting.service.ReportService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.libs.commons.lang.StringUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Objects;
import javax.annotation.Resource;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/** @author sks0013 */
@Controller
@RequestMapping("report")
@Tag(name = "Reporting Service", description = "To expose reporting resource and related services")
public class ReportController {

  private static final String ALL_FACILITY_NUMBERS = "allFacilityNumbers";
  private static final String ALL_FACILITY_COUNTRY_CODES = "allFacilityCountryCodes";
  private static final String CUSTOM_TIME_RANGE_ENABLED = "isCustomTimeRangeEnabled";
  private static final String DASHBOARD_REPORT_ENABLED = "isDashboardReportEnabled";
  private static final String USER_ID = "userId";
  private static final String USER_NAME = "userName";
  private static final String LOGOUT_REDIRECT_URI = "logoutRedirectUri";

  @Autowired private ReceiptService receiptService;

  @Autowired private SSOService ssoService;

  @Autowired private InstructionPersisterService instructionPersisterService;

  @Autowired private TenantSpecificConfigReader configUtils;

  @Autowired private TenantSpecificReportConfig tenantSpecificReportConfig;

  @Resource(name = ReceivingConstants.DEFAULT_REPORT_SERVICE)
  private ReportService reportService;

  @ManagedConfiguration private ReportConfig reportConfig;

  /**
   * Serving statistics html page
   *
   * @return
   */
  @GetMapping("/stats")
  public String viewStatsPage(final Model model, @RequestParam(required = false) String token) {
    if (StringUtils.isEmpty(token)) {
      return "redirect:" + ssoService.getRedirectUri();
    }
    Pair<String, String> userInfo = ssoService.validateToken(token);
    if (Objects.isNull(userInfo)) {
      return "redirect:" + ssoService.getRedirectUri();
    }
    model.addAttribute(ALL_FACILITY_NUMBERS, tenantSpecificReportConfig.getFacilityNumList());
    model.addAttribute(
        ALL_FACILITY_COUNTRY_CODES, tenantSpecificReportConfig.getFacilityCountryCodeList());
    model.addAttribute(CUSTOM_TIME_RANGE_ENABLED, reportConfig.isCustomTimeRangeEnabled());
    model.addAttribute(DASHBOARD_REPORT_ENABLED, reportConfig.isDashboardReportEnabled());
    model.addAttribute(USER_ID, userInfo.getKey());
    model.addAttribute(USER_NAME, userInfo.getValue());
    model.addAttribute(LOGOUT_REDIRECT_URI, ssoService.getLogoutRedirectUri());
    return "stats";
  }

  @GetMapping("/authenticate")
  public String authenticate(@RequestParam String code) {
    String token = ssoService.authenticate(code);
    return "redirect:stats?" + SSOConstants.TOKEN_PARAM_NAME + "=" + token;
  }

  /**
   * REST endpoint for getting report data
   *
   * @param httpHeaders http headers
   * @param fromDate starting timestamp
   * @param toDate ending timestamp
   * @param isUTC time zone indicator
   * @return
   */
  @GetMapping(path = "/stats/data", produces = "application/json")
  @Operation(summary = "Return report statistics data", description = "This will return a 200")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true),
    @Parameter(name = "hours", required = true)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public ResponseEntity<List<Pair<String, Object>>> getStatsData(
      @RequestHeader HttpHeaders httpHeaders,
      @RequestHeader Integer facilityNum,
      @NotNull @RequestParam(name = "fromdatetime") long fromDate,
      @NotNull @RequestParam(name = "todatetime") long toDate,
      @NotNull @RequestParam(name = "isUTC") boolean isUTC) {

    return new ResponseEntity<>(
        configUtils
            .getConfiguredInstance(
                facilityNum.toString(), ReceivingConstants.REPORT_SERVICE, ReportService.class)
            .populateReportData(fromDate, toDate, isUTC, false, httpHeaders)
            .getStatisticsData(),
        HttpStatus.OK);
  }

  /**
   * Serving search tool html page
   *
   * @param model
   * @return
   */
  @GetMapping(path = "/search", produces = "application/json")
  public String viewSearchToolPage(final Model model) {
    model.addAttribute(ALL_FACILITY_NUMBERS, tenantSpecificReportConfig.getFacilityNumList());
    model.addAttribute(
        ALL_FACILITY_COUNTRY_CODES, tenantSpecificReportConfig.getFacilityCountryCodeList());

    return "search";
  }

  /**
   * REST endpoint for getting delivery data
   *
   * @param headers
   * @param deliveryNumber
   * @return
   * @throws ReceivingException
   */
  @GetMapping(path = "/search/delivery/{deliveryNumber}", produces = "application/json")
  @Operation(summary = "Return delivery by delivery number", description = "This will return a 200")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true),
    @Parameter(name = "deliveryNumber", required = true)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public ResponseEntity<String> searchDeliveryByDeliveryNumber(
      @RequestHeader HttpHeaders headers,
      @PathVariable(value = "deliveryNumber") Long deliveryNumber)
      throws ReceivingException {

    String facilityNum = TenantContext.getFacilityNum().toString();

    return new ResponseEntity<>(
        configUtils
            .getConfiguredInstance(
                facilityNum, ReceivingConstants.REPORT_SERVICE, ReportService.class)
            .getDeliveryDetailsForReport(deliveryNumber, headers),
        HttpStatus.OK);
  }

  /**
   * REST endpoint for getting instruction data
   *
   * @param httpHeaders
   * @param deliveryNumber
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @return
   */
  @GetMapping(path = "/search/instructions", produces = "application/json")
  @Operation(
      summary = "Return instructions by po, po line and delivery number",
      description = "This will return a 200")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true),
    @Parameter(name = "delivery", required = true),
    @Parameter(name = "poNum", required = true),
    @Parameter(name = "poLineNum", required = true)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public ResponseEntity<List<Instruction>> searchInstructionsByPoPoLineDelivery(
      @RequestHeader HttpHeaders httpHeaders,
      @NotNull @RequestParam(name = "delivery") Long deliveryNumber,
      @NotNull @RequestParam(name = "poNum") String purchaseReferenceNumber,
      @NotNull @RequestParam(name = "poLineNum") Integer purchaseReferenceLineNumber) {

    return new ResponseEntity<>(
        instructionPersisterService.getInstructionByPoPoLineAndDeliveryNumber(
            purchaseReferenceNumber, purchaseReferenceLineNumber, deliveryNumber),
        HttpStatus.OK);
  }

  /**
   * REST endpoint for getting receipt data
   *
   * @param httpHeaders
   * @param purchaseReferenceLineNumber
   * @return
   */
  @GetMapping(path = "/search/receipts", produces = "application/json")
  @Operation(summary = "Return receipts by po and po line", description = "This will return a 200")
  @Parameters({
    @Parameter(name = "facilityCountryCode", required = true),
    @Parameter(name = "facilityNum", required = true),
    @Parameter(name = "WMT-UserId", required = true),
    @Parameter(name = "poNum", required = true),
    @Parameter(name = "poLineNum", required = true)
  })
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public ResponseEntity<List<Receipt>> searchReceiptsByPoPoLine(
      @RequestHeader HttpHeaders httpHeaders,
      @NotNull @RequestParam(name = "poNum") String purchaseReferenceNumber,
      @NotNull @RequestParam(name = "poLineNum") Integer purchaseReferenceLineNumber) {

    return new ResponseEntity<>(
        receiptService.getReceiptsByAndPoPoLine(
            purchaseReferenceNumber, purchaseReferenceLineNumber),
        HttpStatus.OK);
  }

  @PostMapping(path = "/receiving/productivity", produces = "application/json")
  @Operation(summary = "Return ReceivedData by userId and receivedDate", description = "This will return a 200")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "")})
  public ResponseEntity<Productivity> getReceivingProductivity(
          @RequestHeader(name = ReceivingConstants.TENENT_FACLITYNUM) Integer facilityNum,
          @RequestHeader(name = ReceivingConstants.TENENT_COUNTRY_CODE) String facilityCountryCode,
          @RequestHeader(name = ReceivingConstants.USER_ID_HEADER_KEY) String userId,
          @RequestHeader(name = ReceivingConstants.CORRELATION_ID_HEADER_KEY) String wmtCorrelationId,
          @RequestHeader(name = ReceivingConstants.CONTENT_TYPE) String contentType,
          @Valid@RequestBody(required = false) ReceivingProductivityRequestDTO receivingProductivityRequestDTO
  ) {

    return new ResponseEntity<>(
            reportService.getReceivingProductivity(
                    facilityNum, facilityCountryCode,
                    receivingProductivityRequestDTO
            ),
            HttpStatus.OK);
  }
}
