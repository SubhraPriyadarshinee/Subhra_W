package com.walmart.move.nim.receiving.reporting.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.ReportingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.ReportConfig;
import com.walmart.move.nim.receiving.core.config.app.TenantSpecificReportConfig;
import com.walmart.move.nim.receiving.core.entity.BaseMTEntity;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ItemCatalogUpdateLog;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.Productivity;
import com.walmart.move.nim.receiving.core.model.ReceivingProductivityRequestDTO;
import com.walmart.move.nim.receiving.core.model.ReceivingProductivityResponseDTO;
import com.walmart.move.nim.receiving.core.model.gdm.GdmDeliveryHeaderDetailsResponse;
import com.walmart.move.nim.receiving.core.repositories.ContainerRepository;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.reporting.model.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.ReportingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.collections4.map.HashedMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

/**
 * @author sks0013 Service implementation for report statistics dashboard and automated report
 *     emails
 */
@Primary
@Service(ReceivingConstants.DEFAULT_REPORT_SERVICE)
public class ReportService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ReportService.class);

  private static final int SECONDS_IN_AN_HOUR = 60 * 60;

  private static final int NO_OF_HOURS_IN_A_DAY = 24;

  @ManagedConfiguration ReportConfig reportConfig;
  @Autowired ContainerRepository containerRepository;
  @Autowired private TenantSpecificReportConfig tenantSpecificReportConfig;

  @Resource(name = ReceivingConstants.DELIVERY_SERVICE)
  protected DeliveryService deliveryService;

  @Autowired private ReportPersisterService reportPersisterService;

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  private static final String[] STATS_REPORT_COLUMN_NAMES = {"STATISTIC", "VALUE"};
  private static final String[] DEL_INFO_REPORT_COLUMN_NAMES = {
    "DeliveryNumber",
    "ArrivedTS",
    "DoorOpenTS",
    "ReceivingLastCompletedTS",
    "ReceivingFirstCompletedTS",
    "DoorOpenTime (hrs)",
    "ReceivingTime (hrs)",
    "LOS [DoorOpenTime+ReceivingTime] (hrs)",
    "PO Processing (hrs)"
  };
  private static final String[] USER_INFO_REPORT_COLUMN_NAMES = {"User", "ChannelType", "Count"};

  private static final String[] ITEM_CATALOG_REPORT_COLUMN_NAMES = {
    "Delivery Number",
    "Item number",
    "Old item UPC",
    "New Item UPC",
    "Create Timestamp",
    "Create User ID"
  };

  private static final String[] RX_ITEM_CATALOG_REPORT_COLUMN_NAMES = {
    "Facility Number",
    "Delivery Number",
    "Item number",
    "Old item UPC",
    "New Item UPC",
    "Vendor Number",
    "Vendor Stock Number",
    "Create Timestamp",
    "Create User ID",
    "Correlation ID",
    "Exempt Item"
  };

  private static final String[] PHARMACY_METRICS_REPORT_COLUMN_NAMES = {"STATISTIC", "VALUE"};
  private static final String[] BREAKPACK_BACKOUT_REPORT_COLUMN_NAMES = {
    "Induct Label",
    "Child Label",
    "Allocated Stores",
    "Date Backed Out",
    "Time Backed Out",
    "User ID"
  };

  /**
   * Method for populating the statistics report data
   *
   * @param fromDateTime Date after which the report data is to be displayed
   * @param toDateTime Date after which the report data is to be displayed
   * @param isUTC UTC timestamp flag
   * @param httpHeaders http headers from request
   * @return A map containing the report values
   */
  public ReportData populateReportData(
      long fromDateTime,
      long toDateTime,
      boolean isUTC,
      boolean shouldFetchUserCaseData,
      HttpHeaders httpHeaders) {
    String facilityNumber = TenantContext.getFacilityNum().toString();
    Date fromDate = new Date(fromDateTime);
    Date toDate = new Date(toDateTime);
    if (!isUTC) {
      String timeZone = tenantSpecificReportConfig.getDCTimeZone(facilityNumber);
      fromDate = ReportingUtils.zonedDateTimeToUTC(fromDateTime, timeZone);
      toDate = ReportingUtils.zonedDateTimeToUTC(toDateTime, timeZone);
    }

    return getReceivingReportData(fromDate, toDate, shouldFetchUserCaseData, httpHeaders);
  }

  /**
   * Get the receiving statistics based on the timestamp
   *
   * @param fromDate Date after which the report data is to be displayed
   * @param toDate Date after which the report data is to be displayed
   * @param httpHeaders HTTP Headers from request
   * @return A map containing the report values
   */
  private ReportData getReceivingReportData(
      Date fromDate, Date toDate, boolean shouldFetchUserCaseData, HttpHeaders httpHeaders) {
    List<UserCaseChannelTypeResponse> caseChannelTypeResponse;
    List<GdmDeliveryHeaderDetailsResponse> deliveryHeaderDetailsResponse = null;
    List<Long> deliveryNumbers = new ArrayList<>();

    ReportData reportData = new ReportData();
    try {
      if ((tenantSpecificReportConfig.isFeatureFlagEnabled("averageDeliveryCompletionTime"))
          || (tenantSpecificReportConfig.isFeatureFlagEnabled("totalDeliveryCompletionTime"))
          || (tenantSpecificReportConfig.isFeatureFlagEnabled("averagePOProcessingTime"))
          || (tenantSpecificReportConfig.isFeatureFlagEnabled("totalPOProcessingTime"))
          || (tenantSpecificReportConfig.isFeatureFlagEnabled("averageDoorOpenTime")
              || (tenantSpecificReportConfig.isFeatureFlagEnabled("totalDoorOpenTime")))
          || (tenantSpecificReportConfig.isFeatureFlagEnabled("averageDeliveryReceivingTime"))
          || (tenantSpecificReportConfig.isFeatureFlagEnabled("totalDeliveryReceivingTime"))) {
        LOGGER.info("Fetching GDM delivery header details");
        deliveryHeaderDetailsResponse =
            deliveryService.getDeliveryHeaderDetails(
                fromDate, toDate, deliveryNumbers, httpHeaders);
      }
    } catch (ReceivingException e) {
      LOGGER.error(
          "Error while fetching GDM delivery header details. Exception {}", e.getErrorResponse());
    }

    reportData.setStatisticsData(
        getStatisticsDataForTheGivenTimeRange(fromDate, toDate, deliveryHeaderDetailsResponse));

    if (shouldFetchUserCaseData) {
      reportData.setDeliveryHeaderDetailsResponses(deliveryHeaderDetailsResponse);
      caseChannelTypeResponse = reportPersisterService.getUserCaseChannelTypeData(fromDate, toDate);
      reportData.setCaseChannelTypeResponses(caseChannelTypeResponse);
    }
    return reportData;
  }

  private List<Pair<String, Object>> getStatisticsDataForTheGivenTimeRange(
      Date fromDate,
      Date toDate,
      List<GdmDeliveryHeaderDetailsResponse> deliveryHeaderDetailsResponse) {
    List<Pair<String, Object>> statisticsData = getCountBasedStatistics(fromDate, toDate);
    statisticsData.addAll(getTimeBasedStatistics(fromDate, toDate, deliveryHeaderDetailsResponse));
    return statisticsData;
  }

  private List<Pair<String, Object>> getTimeBasedStatistics(
      Date fromDate,
      Date toDate,
      List<GdmDeliveryHeaderDetailsResponse> deliveryHeaderDetailsResponse) {
    List<Pair<String, Object>> statisticsData = new ArrayList<>();
    Map<Long, Date> oldestContainerMap = null;

    List<Long> deliveryList = new ArrayList<>();
    if (Objects.nonNull(deliveryHeaderDetailsResponse)) {
      deliveryList =
          deliveryHeaderDetailsResponse
              .stream()
              .map(GdmDeliveryHeaderDetailsResponse::getDeliveryNumber)
              .collect(Collectors.toList());
    }
    if (tenantSpecificReportConfig.isFeatureFlagEnabled("averagePOProcessingTime")
        || tenantSpecificReportConfig.isFeatureFlagEnabled("totalPOProcessingTime")) {
      oldestContainerMap = getOldestContainerMap(deliveryList);
    }
    enrichGdmDeliveryHeadersResponse(deliveryHeaderDetailsResponse, oldestContainerMap);
    if (tenantSpecificReportConfig.isFeatureFlagEnabled("averagePalletBuildTime")) {
      LOGGER.info("Going to fetch averagePalletBuildTime");
      Long averagePalletBuildTime =
          (reportPersisterService.getAveragePalletBuildTime(fromDate, toDate));
      statisticsData.add(
          new Pair<>(ReportingConstants.AVERAGE_PALLET_BUILD_TIME_STAT, averagePalletBuildTime));
    }
    if (tenantSpecificReportConfig.isFeatureFlagEnabled("averageDeliveryCompletionTime")) {
      LOGGER.info("Going to fetch averageDeliveryCompletionTime");
      Long averageDeliveryCompletionTime =
          (averageDeliveryCompletionTime(deliveryHeaderDetailsResponse));
      statisticsData.add(
          new Pair<>(
              ReportingConstants.AVERAGE_DELIVERY_COMPLETION_TIME_STAT,
              averageDeliveryCompletionTime));
    }
    if (tenantSpecificReportConfig.isFeatureFlagEnabled("totalDeliveryCompletionTime")) {
      LOGGER.info("Going to fetch totalDeliveryCompletionTime");
      Long totalDeliveryCompletionTime =
          (totalDeliveryCompletionTime(deliveryHeaderDetailsResponse));
      statisticsData.add(
          new Pair<>(
              ReportingConstants.TOTAL_DELIVERY_COMPLETION_TIME_STAT, totalDeliveryCompletionTime));
    }
    if (tenantSpecificReportConfig.isFeatureFlagEnabled("percentageOfDeliveriesMeetingLosLimit")) {
      LOGGER.info("Going to fetch percentageOfDeliveriesMeetingLosLimit");
      Double percentageOfDeliveriesMeetingLosLimit =
          (percentageOfDeliveriesMeetingLosLimit(deliveryHeaderDetailsResponse));
      statisticsData.add(
          new Pair<>(
              ReportingConstants.PERCENTAGE_OF_DELIVERIES_MEETING_LOS_STAT,
              Double.parseDouble(
                  new DecimalFormat("#0.00").format(percentageOfDeliveriesMeetingLosLimit))));
    }
    if (tenantSpecificReportConfig.isFeatureFlagEnabled("averagePOProcessingTime")) {
      LOGGER.info("Going to fetch averagePOProcessingTime");
      Long averagePOProcessingTime = (averagePOProcessingTime(deliveryHeaderDetailsResponse));
      statisticsData.add(
          new Pair<>(ReportingConstants.AVERAGE_PO_PROCESSING_TIME_STAT, averagePOProcessingTime));
    }
    if (tenantSpecificReportConfig.isFeatureFlagEnabled("totalPOProcessingTime")) {
      LOGGER.info("Going to fetch totalPOProcessingTime");
      Long totalPOProcessingTime = (totalPOProcessingTime(deliveryHeaderDetailsResponse));
      statisticsData.add(
          new Pair<>(ReportingConstants.TOTAL_PO_PROCESSING_TIME_STAT, totalPOProcessingTime));
    }
    if (tenantSpecificReportConfig.isFeatureFlagEnabled("averageDoorOpenTime")) {
      LOGGER.info("Going to fetch averageDoorOpenTime");
      Long averagePOProcessingTime = (averageDoorOpenTime(deliveryHeaderDetailsResponse));
      statisticsData.add(
          new Pair<>(ReportingConstants.AVERAGE_DOOR_OPEN_TIME_STAT, averagePOProcessingTime));
    }
    if (tenantSpecificReportConfig.isFeatureFlagEnabled("totalDoorOpenTime")) {
      LOGGER.info("Going to fetch totalDoorOpenTime");
      Long totalPOProcessingTime = (totalDoorOpenTime(deliveryHeaderDetailsResponse));
      statisticsData.add(
          new Pair<>(ReportingConstants.TOTAL_DOOR_OPEN_TIME_STAT, totalPOProcessingTime));
    }
    if (tenantSpecificReportConfig.isFeatureFlagEnabled("averageDeliveryReceivingTime")) {
      LOGGER.info("Going to fetch averageDeliveryReceivingTime");
      Long averagePOProcessingTime = (averageDeliveryReceivingTime(deliveryHeaderDetailsResponse));
      statisticsData.add(
          new Pair<>(
              ReportingConstants.AVERAGE_DELIVERY_RECEIVING_TIME_STAT, averagePOProcessingTime));
    }
    if (tenantSpecificReportConfig.isFeatureFlagEnabled("totalDeliveryReceivingTime")) {
      LOGGER.info("Going to fetch totalDeliveryReceivingTime");
      Long totalPOProcessingTime = (totalDeliveryReceivingTime(deliveryHeaderDetailsResponse));
      statisticsData.add(
          new Pair<>(ReportingConstants.TOTAL_DELIVERY_RECEIVING_TIME_STAT, totalPOProcessingTime));
    }
    return statisticsData;
  }

  private List<Pair<String, Object>> getCountBasedStatistics(Date fromDate, Date toDate) {
    List<Pair<String, Object>> statisticsData = new ArrayList<>();
    if (tenantSpecificReportConfig.isFeatureFlagEnabled("countOfDeliveries")) {
      LOGGER.info("Going to fetch countOfDeliveries");
      Integer countOfDeliveries = (reportPersisterService.getCountOfDeliveries(fromDate, toDate));
      statisticsData.add(
          new Pair<>(ReportingConstants.NUMBER_OF_DELIVERIES_STAT, countOfDeliveries));
    }
    if (tenantSpecificReportConfig.isFeatureFlagEnabled("countOfPos")) {
      LOGGER.info("Going to fetch countOfPos");
      Integer countOfPos = (reportPersisterService.getCountOfPos(fromDate, toDate));
      statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_POS_STAT, countOfPos));
    }
    if (tenantSpecificReportConfig.isFeatureFlagEnabled("countOfUsers")) {
      LOGGER.info("Going to fetch countOfUsers");
      Integer countOfUsers = (reportPersisterService.getCountOfUsers(fromDate, toDate));
      statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_USERS_STAT, countOfUsers));
    }
    if (tenantSpecificReportConfig.isFeatureFlagEnabled("totalCasesReceived")) {
      LOGGER.info("Going to fetch totalCasesReceived");
      Integer totalCasesReceived =
          (reportPersisterService.getCountOfReceivedCases(fromDate, toDate));
      statisticsData.add(
          new Pair<>(ReportingConstants.NUMBER_OF_CASES_RECEIVED_STAT, totalCasesReceived));
    }
    if (tenantSpecificReportConfig.isFeatureFlagEnabled("countOfItems")) {
      LOGGER.info("Going to fetch countOfItems");
      Integer countOfItems = (reportPersisterService.getCountOfItems(fromDate, toDate));
      statisticsData.add(
          new Pair<>(ReportingConstants.NUMBER_OF_ITEMS_RECEIVED_STAT, countOfItems));
    }
    if (tenantSpecificReportConfig.isFeatureFlagEnabled("countOfLabelsPrinted")) {
      LOGGER.info("Going to fetch countOfLabelsPrinted");
      Integer countOfLabelsPrinted = (reportPersisterService.getCountOfLabels(fromDate, toDate));
      statisticsData.add(
          new Pair<>(ReportingConstants.NUMBER_OF_LABELS_PRINTED_STAT, countOfLabelsPrinted));
    }
    if (tenantSpecificReportConfig.isFeatureFlagEnabled("countOfDaConPallets")) {
      LOGGER.info("Going to fetch countOfDaConPallets");
      Integer countOfDaConPallets =
          (reportPersisterService.getCountOfDaConPallets(fromDate, toDate));
      statisticsData.add(
          new Pair<>(ReportingConstants.NUMBER_OF_DA_CON_PALLETS_STAT, countOfDaConPallets));
    }
    if (tenantSpecificReportConfig.isFeatureFlagEnabled("countOfDaConCases")) {
      LOGGER.info("Going to fetch countOfDaConCases");
      Integer countOfDaConCases = (reportPersisterService.getCountOfDaConCases(fromDate, toDate));
      statisticsData.add(
          new Pair<>(ReportingConstants.NUMBER_OF_DA_CON_CASES_STAT, countOfDaConCases));
    }
    if (tenantSpecificReportConfig.isFeatureFlagEnabled("countOfDaNonConPallets")
        || (tenantSpecificReportConfig.isFeatureFlagEnabled("countOfDaNonConCases"))) {
      LOGGER.info("Going to fetch countOfDaNonConPalletsAndCases");
      Pair<Integer, Integer> countOfDaNonConPalletsAndCases =
          (reportPersisterService.getCountOfDaNonConPalletsAndCases(fromDate, toDate));
      if (tenantSpecificReportConfig.isFeatureFlagEnabled("countOfDaNonConPallets")) {
        statisticsData.add(
            new Pair<>(
                ReportingConstants.NUMBER_OF_DA_NON_CON_PALLETS_STAT,
                Objects.nonNull(countOfDaNonConPalletsAndCases.getKey())
                    ? countOfDaNonConPalletsAndCases.getKey()
                    : 0));
      }
      if (tenantSpecificReportConfig.isFeatureFlagEnabled("countOfDaNonConCases")) {
        statisticsData.add(
            new Pair<>(
                ReportingConstants.NUMBER_OF_DA_NON_CON_CASES_STAT,
                Objects.nonNull(countOfDaNonConPalletsAndCases.getValue())
                    ? countOfDaNonConPalletsAndCases.getValue()
                    : 0));
      }
    }
    if (tenantSpecificReportConfig.isFeatureFlagEnabled("countOfSstkPallets")
        || (tenantSpecificReportConfig.isFeatureFlagEnabled("countOfSstkCases"))) {
      LOGGER.info("Going to fetch countOfSstkPalletsAndCases");
      Pair<Integer, Integer> countOfSstkPalletsAndCases =
          (reportPersisterService.getCountOfSstkPalletsAndCases(fromDate, toDate));
      if (tenantSpecificReportConfig.isFeatureFlagEnabled("countOfSstkPallets")) {
        statisticsData.add(
            new Pair<>(
                ReportingConstants.NUMBER_OF_SSTK_PALLETS_STAT,
                Objects.nonNull(countOfSstkPalletsAndCases.getKey())
                    ? countOfSstkPalletsAndCases.getKey()
                    : 0));
      }
      if (tenantSpecificReportConfig.isFeatureFlagEnabled("countOfSstkCases")) {
        statisticsData.add(
            new Pair<>(
                ReportingConstants.NUMBER_OF_SSTK_CASES_STAT,
                Objects.nonNull(countOfSstkPalletsAndCases.getValue())
                    ? countOfSstkPalletsAndCases.getValue()
                    : 0));
      }
    }
    if (tenantSpecificReportConfig.isFeatureFlagEnabled("countOfPbylPallets")
        || (tenantSpecificReportConfig.isFeatureFlagEnabled("countOfPbylCases"))) {
      LOGGER.info("Going to fetch countOfPbylPalletsAndCases");
      Pair<Integer, Integer> countOfPbylPalletsAndCases =
          (reportPersisterService.getCountOfPbylPalletsAndCases(fromDate, toDate));
      if (tenantSpecificReportConfig.isFeatureFlagEnabled("countOfPbylPallets")) {
        statisticsData.add(
            new Pair<>(
                ReportingConstants.NUMBER_OF_PBYL_PALLETS_STAT,
                Objects.nonNull(countOfPbylPalletsAndCases.getKey())
                    ? countOfPbylPalletsAndCases.getKey()
                    : 0));
      }
      if (tenantSpecificReportConfig.isFeatureFlagEnabled("countOfPbylCases")) {
        statisticsData.add(
            new Pair<>(
                ReportingConstants.NUMBER_OF_PBYL_CASES_STAT,
                Objects.nonNull(countOfPbylPalletsAndCases.getValue())
                    ? countOfPbylPalletsAndCases.getValue()
                    : 0));
      }
    }
    if (tenantSpecificReportConfig.isFeatureFlagEnabled("countOfAclPallets")) {
      LOGGER.info("Going to fetch getCountOfAclCases");
      Integer countOfAclPallets = (reportPersisterService.getCountOfAclCases(fromDate, toDate));
      statisticsData.add(
          new Pair<>(ReportingConstants.NUMBER_OF_ACL_CASES_STAT, countOfAclPallets));
    }

    if (tenantSpecificReportConfig.isFeatureFlagEnabled("countOfAccManualCases")) {
      LOGGER.info("Going to fetch countOfAccManualCases");
      Integer countOfAccManualCases =
          (reportPersisterService.getCountOfAccManualCases(fromDate, toDate));
      statisticsData.add(
          new Pair<>(ReportingConstants.NUMBER_OF_MANUAL_CASES_STAT, countOfAccManualCases));
    }
    if (tenantSpecificReportConfig.isFeatureFlagEnabled("countOfPoConPallets")
        || (tenantSpecificReportConfig.isFeatureFlagEnabled("countOfPoConCases"))) {
      LOGGER.info("Going to fetch countOfPoConPalletsAndCases");
      Pair<Integer, Integer> countOfPoConPalletsAndCases =
          (reportPersisterService.getCountOfPoConPalletsAndCases(fromDate, toDate));
      if (tenantSpecificReportConfig.isFeatureFlagEnabled("countOfPoConPallets")) {
        statisticsData.add(
            new Pair<>(
                ReportingConstants.NUMBER_OF_PO_CON_PALLETS_STAT,
                Objects.nonNull(countOfPoConPalletsAndCases.getKey())
                    ? countOfPoConPalletsAndCases.getKey()
                    : 0));
      }
      if (tenantSpecificReportConfig.isFeatureFlagEnabled("countOfPoConCases")) {
        statisticsData.add(
            new Pair<>(
                ReportingConstants.NUMBER_OF_PO_CON_CASES_STAT,
                Objects.nonNull(countOfPoConPalletsAndCases.getValue())
                    ? countOfPoConPalletsAndCases.getValue()
                    : 0));
      }
    }
    if (tenantSpecificReportConfig.isFeatureFlagEnabled("countOfDsdcPallets")
        || (tenantSpecificReportConfig.isFeatureFlagEnabled("countOfDsdcCases"))) {
      LOGGER.info("Going to fetch countOfDsdcPalletsAndCases");
      Pair<Integer, Integer> countOfDsdcPalletsAndCases =
          (reportPersisterService.getCountOfDsdcPalletsAndCases(fromDate, toDate));
      if (tenantSpecificReportConfig.isFeatureFlagEnabled("countOfDsdcPallets")) {
        statisticsData.add(
            new Pair<>(
                ReportingConstants.NUMBER_OF_DSDC_PALLETS_STAT,
                Objects.nonNull(countOfDsdcPalletsAndCases.getKey())
                    ? countOfDsdcPalletsAndCases.getKey()
                    : 0));
      }
      if (tenantSpecificReportConfig.isFeatureFlagEnabled("countOfDsdcCases")) {
        statisticsData.add(
            new Pair<>(
                ReportingConstants.NUMBER_OF_DSDC_CASES_STAT,
                Objects.nonNull(countOfDsdcPalletsAndCases.getValue())
                    ? countOfDsdcPalletsAndCases.getValue()
                    : 0));
      }
    }
    if (tenantSpecificReportConfig.isFeatureFlagEnabled("countOfVtrContainers")) {
      LOGGER.info("Going to fetch countOfVtrContainers");
      Integer countOfVtrContainers =
          (reportPersisterService.getCountOfVtrContainers(fromDate, toDate));
      statisticsData.add(
          new Pair<>(ReportingConstants.NUMBER_OF_VTR_CONTAINERS_STAT, countOfVtrContainers));
    }
    if (tenantSpecificReportConfig.isFeatureFlagEnabled("countOfVtrCases")) {
      LOGGER.info("Going to fetch countOfVtrCases");
      Integer countOfVtrCases = (reportPersisterService.getCountOfVtrCases(fromDate, toDate));
      statisticsData.add(new Pair<>(ReportingConstants.NUMBER_OF_VTR_CASES_STAT, countOfVtrCases));
    }
    if (tenantSpecificReportConfig.isFeatureFlagEnabled("countOfProblemPallets")) {
      LOGGER.info("Going to fetch countOfProblemPallets");
      Integer countOfProblemPallets =
          (reportPersisterService.getCountOfProblemPallets(fromDate, toDate));
      statisticsData.add(
          new Pair<>(ReportingConstants.NUMBER_OF_PROBLEM_PALLETS_STAT, countOfProblemPallets));
    }
    if (tenantSpecificReportConfig.isFeatureFlagEnabled("averageNumberOfPalletsPerDelivery")) {
      LOGGER.info("Going to fetch averageNumberOfPalletsPerDelivery");
      Double averageNumberOfPalletsPerDelivery =
          (reportPersisterService.getAverageCountOfPalletsPerDelivery(fromDate, toDate));
      statisticsData.add(
          new Pair<>(
              ReportingConstants.AVERAGE_NUMBER_OF_PALLETS_PER_DELIVERY_STAT,
              Objects.nonNull(averageNumberOfPalletsPerDelivery)
                  ? Double.parseDouble(
                      new DecimalFormat("#0.0").format(averageNumberOfPalletsPerDelivery))
                  : 0.0D)); // Kept one decimal place after
      // consulting PO
    }
    if (tenantSpecificReportConfig.isFeatureFlagEnabled("countOfDockTags")) {
      LOGGER.info("Going to fetch countOfDockTags");
      Integer countOfDockTagContainers =
          (reportPersisterService.getCountOfDockTagContainers(fromDate, toDate));
      statisticsData.add(
          new Pair<>(ReportingConstants.NUMBER_OF_DOCK_TAGS_STAT, countOfDockTagContainers));
    }
    if (tenantSpecificReportConfig.isFeatureFlagEnabled("countOfCasesReceivedAfterSysReopen")) {
      LOGGER.info("Going to fetch countOfCasesReceivedAfterSysReopen");
      Integer countOfCasesReceivedAfterSysReopen =
          (reportPersisterService.getCountOfCasesAfterSysReopen(fromDate, toDate));
      statisticsData.add(
          new Pair<>(
              ReportingConstants.NUMBER_OF_SYS_REO_CASES_STAT, countOfCasesReceivedAfterSysReopen));
    }
    return statisticsData;
  }

  private void enrichGdmDeliveryHeadersResponse(
      List<GdmDeliveryHeaderDetailsResponse> gdmDeliveryHeaderDetailsResponses,
      Map<Long, Date> oldestContainerMap) {
    if (!CollectionUtils.isEmpty(gdmDeliveryHeaderDetailsResponses)) {
      gdmDeliveryHeaderDetailsResponses
          .stream()
          .filter(o -> Objects.nonNull(o.getArrivedTimeStamp()))
          .forEach(
              o -> {
                o.setDoorOpenTime(
                    o.getDoorOpenTimeStamp().getEpochSecond()
                        - o.getArrivedTimeStamp().getEpochSecond());
                o.setReceivingTime(
                    (Objects.nonNull(o.getReceivingFirstCompletedTimeStamp())
                            ? o.getReceivingFirstCompletedTimeStamp().getEpochSecond()
                            : o.getReceivingCompletedTimeStamp().getEpochSecond())
                        - o.getDoorOpenTimeStamp().getEpochSecond());
                o.setLos(
                    (Objects.nonNull(o.getReceivingFirstCompletedTimeStamp())
                            ? o.getReceivingFirstCompletedTimeStamp().getEpochSecond()
                            : o.getReceivingCompletedTimeStamp().getEpochSecond())
                        - o.getArrivedTimeStamp().getEpochSecond());
                if (!CollectionUtils.isEmpty(oldestContainerMap)) {
                  Date oldestContainerTs = oldestContainerMap.get(o.getDeliveryNumber());
                  if (Objects.nonNull(oldestContainerTs)) {
                    o.setReceivingFirstReceivedTimeStamp(oldestContainerTs.toInstant());
                    o.setPoProcessingTime(
                        o.getReceivingCompletedTimeStamp().getEpochSecond()
                            - o.getReceivingFirstReceivedTimeStamp().getEpochSecond());
                  }
                }
              });
    }
  }

  private Long averageDeliveryCompletionTime(
      List<GdmDeliveryHeaderDetailsResponse> deliveryHeaderDetailsResponse) {
    if (CollectionUtils.isEmpty(deliveryHeaderDetailsResponse)) return 0L;
    OptionalDouble avgDeliveryCompletionTime =
        deliveryHeaderDetailsResponse
            .stream()
            .filter(o -> Objects.nonNull(o.getLos()))
            .mapToLong(GdmDeliveryHeaderDetailsResponse::getLos)
            .average();
    return avgDeliveryCompletionTime.isPresent()
        ? (long) Math.max(avgDeliveryCompletionTime.getAsDouble() / SECONDS_IN_AN_HOUR, 0)
        : 0L;
  }

  private Long totalDeliveryCompletionTime(
      List<GdmDeliveryHeaderDetailsResponse> deliveryHeaderDetailsResponse) {
    if (CollectionUtils.isEmpty(deliveryHeaderDetailsResponse)) return 0L;
    long totalDeliveryCompletionTime =
        deliveryHeaderDetailsResponse
            .stream()
            .filter(o -> Objects.nonNull(o.getLos()))
            .mapToLong(GdmDeliveryHeaderDetailsResponse::getLos)
            .sum();

    if (totalDeliveryCompletionTime > 0) return totalDeliveryCompletionTime / SECONDS_IN_AN_HOUR;
    else return 0L;
  }

  private Double percentageOfDeliveriesMeetingLosLimit(
      List<GdmDeliveryHeaderDetailsResponse> deliveryHeaderDetailsResponse) {
    if (CollectionUtils.isEmpty(deliveryHeaderDetailsResponse)) return 0.0D;
    return (deliveryHeaderDetailsResponse
                .stream()
                .filter(
                    o ->
                        (Objects.nonNull(o.getLos()))
                            && (o.getLos() / SECONDS_IN_AN_HOUR < reportConfig.getLosGoalInHours()))
                .count()
            / (double) deliveryHeaderDetailsResponse.size())
        * 100;
  }

  private Long averagePOProcessingTime(
      List<GdmDeliveryHeaderDetailsResponse> deliveryHeaderDetailsResponse) {
    if (CollectionUtils.isEmpty(deliveryHeaderDetailsResponse)) return 0L;
    OptionalDouble averagePOProcessingTime =
        deliveryHeaderDetailsResponse
            .stream()
            .filter(o -> Objects.nonNull(o.getPoProcessingTime()))
            .mapToLong(GdmDeliveryHeaderDetailsResponse::getPoProcessingTime)
            .average();
    return averagePOProcessingTime.isPresent()
        ? (long) Math.max(averagePOProcessingTime.getAsDouble() / SECONDS_IN_AN_HOUR, 0)
        : 0L;
  }

  private Long totalPOProcessingTime(
      List<GdmDeliveryHeaderDetailsResponse> deliveryHeaderDetailsResponse) {
    if (CollectionUtils.isEmpty(deliveryHeaderDetailsResponse)) return 0L;
    long totalPOProcessingTime =
        deliveryHeaderDetailsResponse
            .stream()
            .filter(o -> Objects.nonNull(o.getPoProcessingTime()))
            .mapToLong(GdmDeliveryHeaderDetailsResponse::getPoProcessingTime)
            .sum();

    if (totalPOProcessingTime > 0) return totalPOProcessingTime / SECONDS_IN_AN_HOUR;
    else return 0L;
  }

  private Long averageDoorOpenTime(
      List<GdmDeliveryHeaderDetailsResponse> deliveryHeaderDetailsResponse) {
    if (CollectionUtils.isEmpty(deliveryHeaderDetailsResponse)) return 0L;
    OptionalDouble averageDoorOpenTime =
        deliveryHeaderDetailsResponse
            .stream()
            .filter(o -> Objects.nonNull(o.getDoorOpenTime()))
            .mapToLong(GdmDeliveryHeaderDetailsResponse::getDoorOpenTime)
            .average();
    return averageDoorOpenTime.isPresent()
        ? (long) Math.max(averageDoorOpenTime.getAsDouble() / SECONDS_IN_AN_HOUR, 0)
        : 0L;
  }

  private Long totalDoorOpenTime(
      List<GdmDeliveryHeaderDetailsResponse> deliveryHeaderDetailsResponse) {
    if (CollectionUtils.isEmpty(deliveryHeaderDetailsResponse)) return 0L;
    long totalDoorOpenTime =
        deliveryHeaderDetailsResponse
            .stream()
            .filter(o -> Objects.nonNull(o.getDoorOpenTime()))
            .mapToLong(GdmDeliveryHeaderDetailsResponse::getDoorOpenTime)
            .sum();

    if (totalDoorOpenTime > 0) return totalDoorOpenTime / SECONDS_IN_AN_HOUR;
    else return 0L;
  }

  private Long averageDeliveryReceivingTime(
      List<GdmDeliveryHeaderDetailsResponse> deliveryHeaderDetailsResponse) {
    if (CollectionUtils.isEmpty(deliveryHeaderDetailsResponse)) return 0L;
    OptionalDouble averageDeliveryReceivingTime =
        deliveryHeaderDetailsResponse
            .stream()
            .filter(o -> Objects.nonNull(o.getReceivingTime()))
            .mapToLong(GdmDeliveryHeaderDetailsResponse::getReceivingTime)
            .average();
    return averageDeliveryReceivingTime.isPresent()
        ? (long) Math.max(averageDeliveryReceivingTime.getAsDouble() / SECONDS_IN_AN_HOUR, 0)
        : 0L;
  }

  private Long totalDeliveryReceivingTime(
      List<GdmDeliveryHeaderDetailsResponse> deliveryHeaderDetailsResponse) {
    if (CollectionUtils.isEmpty(deliveryHeaderDetailsResponse)) return 0L;
    long totalDeliveryReceivingTime =
        deliveryHeaderDetailsResponse
            .stream()
            .filter(o -> Objects.nonNull(o.getReceivingTime()))
            .mapToLong(GdmDeliveryHeaderDetailsResponse::getReceivingTime)
            .sum();

    if (totalDeliveryReceivingTime > 0) return totalDeliveryReceivingTime / SECONDS_IN_AN_HOUR;
    else return 0L;
  }

  /**
   * This method is used generate mail body html template for statistics report.
   *
   * @param reportData Report data
   * @return HTML template for email
   */
  public StringBuilder createHtmlTemplateForStatistics(Map<Integer, ReportData> reportData) {

    StringBuilder reportHtmlTemplate = new StringBuilder();
    reportHtmlTemplate.append("<html><body>");
    reportHtmlTemplate.append(
        "<p style='text-align:left;font-size:14'>Hi,<br> Please find the weekly report"
            + " of each DC and the attachment for the same.");

    for (Integer facilityNumber : reportData.keySet()) {
      reportHtmlTemplate
          .append("<h3 style='text-decoration: underline;'>")
          .append(facilityNumber)
          .append(":</h3>");

      // creating table headers
      StringBuilder tableBody = new StringBuilder();

      // creation of table body
      for (Pair<String, Object> report : reportData.get(facilityNumber).getStatisticsData()) {
        tableBody.append(
            "<tr><td>" + report.getKey() + "</td><td>" + report.getValue() + "</td></tr>");
      }
      String tableHeader =
          "<table border='1px solid black'><tr><th>"
              + STATS_REPORT_COLUMN_NAMES[0]
              + "</th><th>"
              + STATS_REPORT_COLUMN_NAMES[1]
              + "</th></tr>";
      reportHtmlTemplate.append(tableHeader).append(tableBody).append("</table><br><br>");
    }
    reportHtmlTemplate.append("<h4 style='text-decoration: underline;'> Note: </h4>");
    reportHtmlTemplate.append(
        "<p> It is system generated mail. Please reach out to <a href='mailto:VoltaWorldwide@wal-mart.com'>Atlas-receiving</a> team in case of any query related to the report.");
    reportHtmlTemplate.append("<p>Thanks,<br>Atlas-receiving team</p></html>");
    return reportHtmlTemplate;
  }

  /**
   * This method is used to create excel sheet report for statistics.
   *
   * @return
   */
  public Workbook createExcelReport(Map<Integer, ReportData> reportData) {

    Workbook workbook = new XSSFWorkbook();
    for (Map.Entry<Integer, ReportData> facilityNumber : reportData.entrySet()) {

      CellStyle headerCellStyle = getCellStyle(workbook);

      addXlsStatistics(
          workbook,
          ReportingConstants.WRKB_STATSSHEET,
          facilityNumber,
          STATS_REPORT_COLUMN_NAMES,
          headerCellStyle);

      addXlsStatistics(
          workbook,
          ReportingConstants.WRKB_DELIVERYINFOSSHEET,
          facilityNumber,
          DEL_INFO_REPORT_COLUMN_NAMES,
          headerCellStyle);

      addXlsStatistics(
          workbook,
          ReportingConstants.WRKB_USERINFOSSHEET,
          facilityNumber,
          USER_INFO_REPORT_COLUMN_NAMES,
          headerCellStyle);
    }

    return workbook;
  }

  private void addXlsStatistics(
      Workbook workbook,
      String sheetName,
      Map.Entry<Integer, ReportData> facilityNumber,
      String[] columnNames,
      CellStyle headerCellStyle) {
    Sheet sheet = workbook.createSheet(facilityNumber.getKey() + sheetName);

    Row headerRow = sheet.createRow(0);

    for (int i = 0; i < columnNames.length; i++) {
      Cell cell = headerRow.createCell(i);
      cell.setCellValue(columnNames[i]);
      cell.setCellStyle(headerCellStyle);
    }
    // Stats Info
    if (sheetName.equals(ReportingConstants.WRKB_STATSSHEET)) {
      int rowNum = 1;
      for (Pair<String, Object> report : facilityNumber.getValue().getStatisticsData()) {
        Row row = sheet.createRow(rowNum++);
        row.createCell(0).setCellValue(report.getKey());
        row.createCell(1).setCellValue(report.getValue().toString());
      }
    }
    // Delivery Info
    if (sheetName.equals(ReportingConstants.WRKB_DELIVERYINFOSSHEET)) {

      if (facilityNumber.getValue().getDeliveryHeaderDetailsResponses() != null) {

        AtomicInteger counter = new AtomicInteger(1);
        facilityNumber
            .getValue()
            .getDeliveryHeaderDetailsResponses()
            .forEach(
                s -> {
                  Row row = sheet.createRow(counter.getAndIncrement());
                  row.createCell(0).setCellValue(s.getDeliveryNumber());
                  row.createCell(1)
                      .setCellValue(
                          Objects.nonNull(s.getArrivedTimeStamp())
                              ? s.getArrivedTimeStamp().toString()
                              : "");
                  row.createCell(2)
                      .setCellValue(
                          Objects.nonNull(s.getDoorOpenTimeStamp())
                              ? s.getDoorOpenTimeStamp().toString()
                              : "");
                  row.createCell(3)
                      .setCellValue(
                          Objects.nonNull(s.getReceivingCompletedTimeStamp())
                              ? s.getReceivingCompletedTimeStamp().toString()
                              : "");
                  row.createCell(4)
                      .setCellValue(
                          Objects.nonNull(s.getReceivingFirstCompletedTimeStamp())
                              ? s.getReceivingFirstCompletedTimeStamp().toString()
                              : "");
                  row.createCell(5)
                      .setCellValue(
                          Objects.nonNull(s.getDoorOpenTime())
                              ? Long.toString(s.getDoorOpenTime() / SECONDS_IN_AN_HOUR)
                              : "");
                  row.createCell(6)
                      .setCellValue(
                          Objects.nonNull(s.getReceivingTime())
                              ? Long.toString(s.getReceivingTime() / SECONDS_IN_AN_HOUR)
                              : "");
                  row.createCell(7)
                      .setCellValue(
                          Objects.nonNull(s.getLos())
                              ? Long.toString(s.getLos() / SECONDS_IN_AN_HOUR)
                              : "");
                  row.createCell(8)
                      .setCellValue(
                          Objects.nonNull(s.getPoProcessingTime())
                              ? Long.toString(s.getPoProcessingTime() / SECONDS_IN_AN_HOUR)
                              : "");
                });
      }
    }

    // UserCase+ChannelMethod Info
    if (sheetName.equals(ReportingConstants.WRKB_USERINFOSSHEET)
        && (facilityNumber.getValue().getCaseChannelTypeResponses() != null)) {

      AtomicInteger atomicInteger = new AtomicInteger(1);
      facilityNumber
          .getValue()
          .getCaseChannelTypeResponses()
          .forEach(
              u -> {
                Row row = sheet.createRow(atomicInteger.getAndIncrement());
                row.createCell(0).setCellValue(u.getUser());
                row.createCell(1).setCellValue(u.getChannelType());
                row.createCell(2).setCellValue(u.getCasesCount());
              });
    }

    resizeColumns(sheet, columnNames);
  }

  private void resizeColumns(Sheet sheet, String[] columnNames) {
    // Resize all columns to fit the content size
    for (int i = 0; i < columnNames.length; i++) {
      sheet.autoSizeColumn(i);
    }
  }

  public CellStyle getCellStyle(Workbook workbook) {
    // Creating header cell style
    Font headerFont = workbook.createFont();
    headerFont.setBold(true);
    headerFont.setFontHeightInPoints((short) 14);
    headerFont.setColor(IndexedColors.BLACK1.getIndex());

    CellStyle headerCellStyle = workbook.createCellStyle();
    headerCellStyle.setFont(headerFont);
    headerCellStyle.setFillBackgroundColor(IndexedColors.GREY_40_PERCENT.getIndex());
    return headerCellStyle;
  }

  /** This method is used generate mail body html template for reporting from an entity. */
  public <T extends BaseMTEntity> StringBuilder createHtmlTemplateForReportingForEntity(
      List<T> entityLogs, String reportNameMessage, String featureFlag) {

    List<Integer> facilityNumListPresent = ReceivingUtils.getFacilityNumListPresent(entityLogs);
    List<Integer> facilityNumListAbsent =
        tenantSpecificConfigReader.getMissingFacilityNumList(facilityNumListPresent, featureFlag);

    StringBuilder reportHtmlTemplate = new StringBuilder();
    reportHtmlTemplate
        .append("<html><body>")
        .append("<p style='text-align:left;font-size:14'>Hi,<br> ");
    if (!facilityNumListPresent.isEmpty()) {
      reportHtmlTemplate
          .append("Please find the ")
          .append(reportNameMessage)
          .append(" report of each of the following DCs: ");
      for (int i = 0; i < facilityNumListPresent.size() - 1; i++) {
        reportHtmlTemplate.append(facilityNumListPresent.get(i)).append(", ");
      }
      reportHtmlTemplate
          .append(facilityNumListPresent.get(facilityNumListPresent.size() - 1))
          .append(" in the attachment.");
    }
    if (!facilityNumListAbsent.isEmpty()) {
      reportHtmlTemplate
          .append("<p style='text-align:left;font-size:14'> There were no ")
          .append(reportNameMessage)
          .append(" recorded for each of the following DCs: ");
      for (int i = 0; i < facilityNumListAbsent.size() - 1; i++) {
        reportHtmlTemplate.append(facilityNumListAbsent.get(i)).append(", ");
      }
      reportHtmlTemplate.append(facilityNumListAbsent.get(facilityNumListAbsent.size() - 1));
    }

    reportHtmlTemplate
        .append("<h4 style='text-decoration: underline;'> Note: </h4>")
        .append(
            "<p> It is system generated mail. Please reach out to <a href='mailto:VoltaWorldwide@wal-mart.com'>Atlas-receiving</a> team in case of any query related to the report.")
        .append("<p>Thanks,<br>Atlas-receiving team</p></html>");
    return reportHtmlTemplate;
  }

  /**
   * This method is used to create excel sheet report for item cataloguing.
   *
   * @return excel sheet with item catalog report
   */
  public Workbook createExcelReportForItemCatalog(
      List<ItemCatalogUpdateLog> itemCatalogUpdateLogs) {

    Workbook workbook = new XSSFWorkbook();

    for (Integer facilityNumber :
        tenantSpecificConfigReader.getEnabledFacilityNumListForFeature(
            ReceivingConstants.ITEM_CATALOG_ENABLED)) {

      List<ItemCatalogUpdateLog> itemCatalogUpdateLogsByFacility =
          itemCatalogUpdateLogs
              .parallelStream()
              .filter(o -> facilityNumber.equals(o.getFacilityNum()))
              .collect(Collectors.toList());
      if (!CollectionUtils.isEmpty(itemCatalogUpdateLogsByFacility)) {
        Sheet sheet = workbook.createSheet(facilityNumber + "-itemCatalogUpdateLogs");
        CellStyle headerCellStyle = getCellStyle(workbook);

        Row headerRow = sheet.createRow(0);

        String[] columnNames = ITEM_CATALOG_REPORT_COLUMN_NAMES;

        // Create cells
        for (int i = 0; i < columnNames.length; i++) {
          Cell cell = headerRow.createCell(i);
          cell.setCellValue(columnNames[i]);
          cell.setCellStyle(headerCellStyle);
        }

        int rowNum = 1;
        for (ItemCatalogUpdateLog itemCatalogUpdateLog : itemCatalogUpdateLogsByFacility) {
          Row row = sheet.createRow(rowNum++);
          row.createCell(0).setCellValue(itemCatalogUpdateLog.getDeliveryNumber());
          row.createCell(1).setCellValue(itemCatalogUpdateLog.getItemNumber());
          row.createCell(2).setCellValue(itemCatalogUpdateLog.getOldItemUPC());
          row.createCell(3).setCellValue(itemCatalogUpdateLog.getNewItemUPC());
          String createTs =
              DateFormatUtils.format(
                  itemCatalogUpdateLog.getCreateTs(), ReceivingConstants.UTC_DATE_FORMAT);
          row.createCell(4).setCellValue(createTs);
          row.createCell(5).setCellValue(itemCatalogUpdateLog.getCreateUserId());
        }

        // Resize all columns to fit the content size
        for (int i = 0; i < columnNames.length; i++) {
          sheet.autoSizeColumn(i);
        }
      }
    }
    return workbook;
  }

  /**
   * This method is used to create excel sheet report for item cataloguing.
   *
   * @return excel sheet with item catalog report
   */
  public Workbook createRxExcelReportForItemCatalog(
      List<RxItemCatalogReportData> itemCatalogUpdateLogs) {

    Workbook workbook = new XSSFWorkbook();

    for (Integer facilityNumber :
        tenantSpecificConfigReader.getEnabledFacilityNumListForFeature(
            ReceivingConstants.ITEM_CATALOG_ENABLED)) {

      List<RxItemCatalogReportData> itemCatalogUpdateLogsByFacility =
          itemCatalogUpdateLogs
              .parallelStream()
              .filter(o -> facilityNumber.equals(o.getFacilityNum()))
              .collect(Collectors.toList());
      if (!CollectionUtils.isEmpty(itemCatalogUpdateLogsByFacility)) {
        Sheet sheet = workbook.createSheet(facilityNumber + "-itemCatalogUpdateLogs");
        CellStyle headerCellStyle = getCellStyle(workbook);

        Row headerRow = sheet.createRow(0);

        String[] columnNames = RX_ITEM_CATALOG_REPORT_COLUMN_NAMES;

        // Create cells
        for (int i = 0; i < columnNames.length; i++) {
          Cell cell = headerRow.createCell(i);
          cell.setCellValue(columnNames[i]);
          cell.setCellStyle(headerCellStyle);
        }

        int rowNum = 1;
        for (RxItemCatalogReportData itemCatalogUpdateLog : itemCatalogUpdateLogsByFacility) {
          Row row = sheet.createRow(rowNum++);
          row.createCell(0).setCellValue(itemCatalogUpdateLog.getFacilityNum());
          row.createCell(1).setCellValue(itemCatalogUpdateLog.getDeliveryNumber());
          row.createCell(2).setCellValue(itemCatalogUpdateLog.getItemNumber());
          row.createCell(3).setCellValue(itemCatalogUpdateLog.getOldItemUPC());
          row.createCell(4).setCellValue(itemCatalogUpdateLog.getNewItemUPC());
          if (StringUtils.isBlank(itemCatalogUpdateLog.getVendorNumber())) {
            row.createCell(5).setCellType(CellType.BLANK);
          } else {
            row.createCell(5).setCellValue(itemCatalogUpdateLog.getVendorNumber());
          }
          if (StringUtils.isBlank(itemCatalogUpdateLog.getVendorStockNumber())) {
            row.createCell(6).setCellType(CellType.BLANK);
          } else {
            row.createCell(6).setCellValue(itemCatalogUpdateLog.getVendorStockNumber());
          }
          row.createCell(7).setCellValue(itemCatalogUpdateLog.getCreateTs());
          row.createCell(8).setCellValue(itemCatalogUpdateLog.getCreateUserId());
          row.createCell(9).setCellValue(itemCatalogUpdateLog.getCorrelationId());
          row.createCell(10).setCellValue(itemCatalogUpdateLog.getExemptItem());
        }

        // Resize all columns to fit the content size
      }
    }
    return workbook;
  }

  public String getDeliveryDetailsForReport(long deliveryNumber, HttpHeaders headers)
      throws ReceivingException {
    return deliveryService.getDeliveryByDeliveryNumber(deliveryNumber, headers);
  }

  public Map<Long, Date> getOldestContainerMap(List<Long> deliveryList) {
    int batchSize = 200;
    Map<Long, Date> oldestContainerMap = new HashMap<>();
    for (int i = 0; i < deliveryList.size(); i += batchSize) {
      List<Object[]> oldestContainerList =
          reportPersisterService.getOldestContainerList(
              deliveryList.subList(i, Math.min(i + batchSize, deliveryList.size())));
      for (Object[] oldestContainer : oldestContainerList) {
        oldestContainerMap.put((Long) oldestContainer[0], (Date) oldestContainer[1]);
      }
    }
    return oldestContainerMap;
  }

  /**
   * This method is used to get report data for the facilityNumbers.
   *
   * @param allFacilityNumbers List of facility numbers
   * @param countryCode country code
   */
  public Map<Integer, ReportData> getReportForAllFacilityNumbers(
      List<Integer> allFacilityNumbers, String countryCode) {
    Map<Integer, ReportData> allFacilityReports = new HashedMap<>();
    for (Integer facilityNumber : allFacilityNumbers) {
      TenantContext.setFacilityCountryCode(countryCode);
      TenantContext.setFacilityNum(facilityNumber);
      TenantContext.setCorrelationId(UUID.randomUUID().toString());

      Integer numberOfHours =
          reportConfig.getStatsReportGenerationForLastXdays() * NO_OF_HOURS_IN_A_DAY;
      long toDateTime = new Date().getTime();
      long fromDateTime = toDateTime - (numberOfHours * 60 * 60 * 1000);

      allFacilityReports.put(
          facilityNumber,
          populateReportData(fromDateTime, toDateTime, true, true, ReceivingUtils.getHeaders()));
    }
    return allFacilityReports;
  }

  public StringBuilder createHtmlTemplateForPharmacyReceivingMetrics(
      Map<String, Integer> pharmacyReport) {

    StringBuilder reportHtmlTemplate = new StringBuilder();
    reportHtmlTemplate.append("<html><body>");
    reportHtmlTemplate.append(
        "<p style='text-align:left;font-size:14'>Hi,<br> Please find the pharmacy receiving Metrics Report"
            + " and the attachment for the same.");

    // creating table headers
    StringBuilder tableBody = new StringBuilder();

    // creation of table body
    for (Map.Entry<String, Integer> report : pharmacyReport.entrySet()) {
      tableBody.append(
          "<tr><td>" + report.getKey() + "</td><td>" + report.getValue() + "</td></tr>");
    }
    String tableHeader =
        "<table border='1px solid black'><tr><th>"
            + STATS_REPORT_COLUMN_NAMES[0]
            + "</th><th>"
            + STATS_REPORT_COLUMN_NAMES[1]
            + "</th></tr>";
    reportHtmlTemplate.append(tableHeader).append(tableBody).append("</table><br><br>");

    reportHtmlTemplate.append("<h4 style='text-decoration: underline;'> Note: </h4>");
    reportHtmlTemplate.append(
        "<p> It is system generated mail. Please reach out to <a href='mailto:VoltaWorldwide@wal-mart.com'>Atlas-receiving</a> team in case of any query related to the report.");
    reportHtmlTemplate.append("<p>Thanks,<br>Atlas-receiving team</p></html>");
    return reportHtmlTemplate;
  }

  public Workbook createExcelReportForPharmacyReceivingMetrics(
      Map<String, Integer> pharmacyReport) {

    Workbook workbook = new XSSFWorkbook();

    if (!CollectionUtils.isEmpty(pharmacyReport)) {
      Sheet sheet = workbook.createSheet("Pharmacy receiving Metrics report");
      CellStyle headerCellStyle = getCellStyle(workbook);

      Row headerRow = sheet.createRow(0);

      String[] columnNames = PHARMACY_METRICS_REPORT_COLUMN_NAMES;

      // Create cells
      for (int i = 0; i < columnNames.length; i++) {
        Cell cell = headerRow.createCell(i);
        cell.setCellValue(columnNames[i]);
        cell.setCellStyle(headerCellStyle);
      }
      int rowNum = 1;
      for (Map.Entry<String, Integer> report : pharmacyReport.entrySet()) {
        Row row = sheet.createRow(rowNum++);
        row.createCell(0).setCellValue(report.getKey());
        row.createCell(1).setCellValue(String.valueOf(report.getValue()));
      }

      for (int i = 0; i < columnNames.length; i++) {
        sheet.autoSizeColumn(i);
      }
    }

    return workbook;
  }

  /**
   * Retrieve break pack receive containers
   *
   * @return
   */
  @Transactional(
      readOnly = true,
      timeout = ReceivingConstants.DEFAULT_TRANSACTION_TIME_OUT_IN_SECONDS)
  public List<List<BreakPackLabelInfo>> fetchBreakPackReceiveContainerDetails(int facilityNumber) {
    List<List<BreakPackLabelInfo>> combinedBreakPackReceiveContainers = new ArrayList<>();
    long startTime = System.currentTimeMillis();
    List<Container> backOutContainers = getBreakPackReceiveContainer(facilityNumber);
    long endTime = System.currentTimeMillis();
    long totalTime = endTime - startTime;
    LOGGER.info(
        "BreakPack BackOut Containers query time taken={}, records count={}",
        totalTime,
        backOutContainers.size());
    // filter last 24 hours record
    String dcTimeZone = tenantSpecificConfigReader.getDCTimeZone(facilityNumber);
    Instant currentTime = ReceivingUtils.getDCDateTime(dcTimeZone).toInstant();
    backOutContainers =
        backOutContainers
            .stream()
            .filter(
                backOutContainer ->
                    // with in last 24 hours
                    ChronoUnit.DAYS.between(
                            backOutContainer.getLastChangedTs().toInstant(), currentTime)
                        == 0)
            .collect(Collectors.toList());
    LOGGER.info(
        "BreakPack BackOut Containers after 24hour filter records count={}",
        backOutContainers.size());

    Set<String> trackingIds =
        backOutContainers.stream().map(Container::getTrackingId).collect(Collectors.toSet());
    startTime = System.currentTimeMillis();
    List<Container> childContainers =
        containerRepository.fetchAllocatedStores(
            trackingIds, Collections.singletonList(facilityNumber));
    totalTime = endTime - startTime;
    LOGGER.info("BreakPack BackOut Containers allocated stores query time taken={}", totalTime);
    List<BreakPackLabelInfo> breakPackReceiveContainers =
        transformContainersToBreakPackReceiveContainer(
            backOutContainers, childContainers, dcTimeZone);
    combinedBreakPackReceiveContainers.add(breakPackReceiveContainers);

    return combinedBreakPackReceiveContainers;
  }

  public List<Container> getBreakPackReceiveContainer(int facilityNumber) {
    int recordsFetchCount =
        Objects.nonNull(reportConfig.getAtlasDaBreakPackBackOutReportRecordsFetchCount())
            ? reportConfig.getAtlasDaBreakPackBackOutReportRecordsFetchCount()
            : ReportingConstants.DEFAULT_ATLAS_DA_BREAK_PACK_BACK_OUT_REPORT_RECORDS_FETCH_COUNT;
    return containerRepository.findBreakPackReceiveContainer(
        facilityNumber,
        ReceivingConstants.BACKOUT,
        ReceivingConstants.ALLOCATED,
        ReceivingConstants.FACILITY_COUNTRY_CODE,
        PageRequest.of(0, recordsFetchCount));
  }

  /**
   * Create excel with break pack receive containers
   *
   * @param breakPackReceiveContainers
   * @return
   */
  public Workbook createExcelReportForBreakPackReceivedContainers(
      List<BreakPackLabelInfo> breakPackReceiveContainers) {
    Workbook workbook = new XSSFWorkbook();
    Sheet sheet = workbook.createSheet("Break Pack backout report");
    CellStyle headerCellStyle = getCellStyle(workbook);
    Row headerRow = sheet.createRow(0);
    String[] columnNames = BREAKPACK_BACKOUT_REPORT_COLUMN_NAMES;
    // Create cells
    for (int i = 0; i < columnNames.length; i++) {
      Cell cell = headerRow.createCell(i);
      cell.setCellValue(columnNames[i]);
      cell.setCellStyle(headerCellStyle);
    }
    int rowNum = 1;
    for (BreakPackLabelInfo containerDetails : breakPackReceiveContainers) {
      for (BreakPackChildLabelInfo breakPackChildLabelInfo :
          containerDetails.getBreakPackChildLabelInfo()) {
        Row row = sheet.createRow(rowNum++);
        row.createCell(0).setCellValue(containerDetails.getInductLabelId());
        row.createCell(1).setCellValue(breakPackChildLabelInfo.getChildLabel());
        row.createCell(2).setCellValue(breakPackChildLabelInfo.getAllocatedStore());
        row.createCell(3).setCellValue(containerDetails.getBackOutDate());
        row.createCell(4).setCellValue(containerDetails.getBackOutTimeStamp());
        row.createCell(5).setCellValue(containerDetails.getUserId());
      }
    }
    return workbook;
  }

  /**
   * Transform to BreakPackLabelInfo
   *
   * @param backOutContainers
   * @param childContainers
   * @param dcTimeZone
   * @return
   */
  private List<BreakPackLabelInfo> transformContainersToBreakPackReceiveContainer(
      List<Container> backOutContainers, List<Container> childContainers, String dcTimeZone) {
    return backOutContainers
        .stream()
        .map(
            container ->
                BreakPackLabelInfo.builder()
                    .inductLabelId(container.getTrackingId())
                    .backOutDate(
                        formatInputDate(
                            ReceivingUtils.convertUTCToZoneDateTime(
                                container.getLastChangedTs(), dcTimeZone),
                            "MM/dd/yyyy"))
                    .backOutTimeStamp(
                        formatInputDate(
                            ReceivingUtils.convertUTCToZoneDateTime(
                                container.getLastChangedTs(), dcTimeZone),
                            "HH:mm"))
                    .userId(container.getLastChangedUser())
                    .breakPackChildLabelInfo(getAllocatedStore(container, childContainers))
                    .build())
        .collect(Collectors.toList());
  }

  /**
   * Retrieve allocated store for the tracking id
   *
   * @param backOutContainer
   * @param childContainers
   * @return
   */
  private List<BreakPackChildLabelInfo> getAllocatedStore(
      Container backOutContainer, List<Container> childContainers) {
    return childContainers
        .stream()
        .filter(
            childContainer ->
                StringUtils.equalsIgnoreCase(
                    childContainer.getParentTrackingId(), backOutContainer.getTrackingId()))
        .map(this::transformToBreakPackChildLabelInfo)
        .collect(Collectors.toList());
  }

  /**
   * Transform to BreakPackChildLabelInfo
   *
   * @param childContainer
   * @return
   */
  private BreakPackChildLabelInfo transformToBreakPackChildLabelInfo(Container childContainer) {
    Map<String, String> destinationMap =
        Optional.ofNullable(childContainer.getDestination()).orElse(Collections.emptyMap());
    return BreakPackChildLabelInfo.builder()
        .childLabel(childContainer.getTrackingId())
        .allocatedStore(destinationMap.getOrDefault(ReceivingConstants.BU_NUMBER, ""))
        .build();
  }

  /**
   * Email template for break pack receive container
   *
   * @param breakPackReceiveContainerDetails
   * @return
   */
  public String createHtmlTemplateForBreakPackBackOutContainers(
      List<BreakPackLabelInfo> breakPackReceiveContainerDetails) {
    StringBuilder reportHtmlTemplate = new StringBuilder();
    reportHtmlTemplate.append("<html><body>");
    reportHtmlTemplate.append(
        "<p style='text-align:left;font-size:14'>Hi,<br> Please find the Break Pack Backout Report"
            + " and the attachment for the same.");

    // creating table headers
    StringBuilder tableBody = new StringBuilder();

    // creation of table body
    for (BreakPackLabelInfo report : breakPackReceiveContainerDetails) {
      for (BreakPackChildLabelInfo breakPackChildLabelInfo : report.getBreakPackChildLabelInfo()) {
        tableBody.append(
            "<tr><td>"
                + report.getInductLabelId()
                + "</td><td>"
                + breakPackChildLabelInfo.getChildLabel()
                + "</td><td>"
                + breakPackChildLabelInfo.getAllocatedStore()
                + "</td><td>"
                + report.getBackOutDate()
                + "</td><td>"
                + report.getBackOutTimeStamp()
                + "</td><td>"
                + report.getUserId()
                + "</td></tr>");
      }
    }
    String tableHeader =
        "<table border='1px solid black'><tr><th>"
            + BREAKPACK_BACKOUT_REPORT_COLUMN_NAMES[0]
            + "</th><th>"
            + BREAKPACK_BACKOUT_REPORT_COLUMN_NAMES[1]
            + "</th><th>"
            + BREAKPACK_BACKOUT_REPORT_COLUMN_NAMES[2]
            + "</th><th>"
            + BREAKPACK_BACKOUT_REPORT_COLUMN_NAMES[3]
            + "</th><th>"
            + BREAKPACK_BACKOUT_REPORT_COLUMN_NAMES[4]
            + "</th><th>"
            + BREAKPACK_BACKOUT_REPORT_COLUMN_NAMES[5]
            + "</th></tr>";
    reportHtmlTemplate.append(tableHeader).append(tableBody).append("</table><br><br>");

    reportHtmlTemplate.append("<h4 style='text-decoration: underline;'> Note: </h4>");
    reportHtmlTemplate.append(
        "<p> It is system generated mail. Please reach out to <a href='mailto:VoltaWorldwide@wal-mart.com'>Atlas-receiving</a> team in case of any query related to the report.");
    reportHtmlTemplate.append("<p>Thanks,<br>Atlas-receiving team</p></html>");
    return reportHtmlTemplate.toString();
  }

  /**
   * Format the given input date with given format
   *
   * @param inputDate
   * @param format
   * @return
   */
  private String formatInputDate(ZonedDateTime inputDate, String format) {
    if (Objects.isNull(inputDate)) {
      return null;
    }
    return DateTimeFormatter.ofPattern(format).format(inputDate);
  }

  public Productivity getReceivingProductivity(
          Integer facilityNumber,
          String facilityCountryCode,
          ReceivingProductivityRequestDTO receivingProductivityRequestDTO) {

    LOGGER.info("Fetching Receiving Productivity report for request '{}'", receivingProductivityRequestDTO);

    Productivity productivity = new Productivity();
    Integer page;
    Integer limit;
    if (receivingProductivityRequestDTO != null
            && receivingProductivityRequestDTO.getPage() != null
            && receivingProductivityRequestDTO.getLimit() != null ) {
      page = receivingProductivityRequestDTO.getPage() != 0 ? receivingProductivityRequestDTO.getPage() : ReportingConstants.DEFAULT_PAGE;
      limit = receivingProductivityRequestDTO.getLimit() != 0 ? receivingProductivityRequestDTO.getLimit() : ReportingConstants.DEFAULT_LIMIT;
    } else {
      page = ReportingConstants.DEFAULT_PAGE;
      limit = ReportingConstants.DEFAULT_LIMIT;
    }

    Pageable reportPage = PageRequest.of(page - 1, limit);

    List<ReceivingProductivityResponseDTO>  receivingProductivityList = new ArrayList<>();

    Page<ReceivingProductivityResponseDTO>  receivingProductivityPage;


    if (StringUtils.isBlank(receivingProductivityRequestDTO.getUserId())) {
      receivingProductivityPage = reportPersisterService.getReceivingProductivityForAllUsers(
              facilityNumber,
              facilityCountryCode,
              receivingProductivityRequestDTO.getReceivedFromDate(),
              receivingProductivityRequestDTO.getReceivedToDate(),
              reportPage);
    } else {
      receivingProductivityPage = reportPersisterService.getReceivingProductivityForOneUser(
              facilityNumber,
              facilityCountryCode,
              receivingProductivityRequestDTO.getUserId(),
              receivingProductivityRequestDTO.getReceivedFromDate(),
              receivingProductivityRequestDTO.getReceivedToDate(),
              reportPage);
    }

    if (!CollectionUtils.isEmpty(receivingProductivityPage.getContent())) {
      receivingProductivityList =
              new ArrayList<>(receivingProductivityPage
                      .getContent());
    }
    productivity.setProductivity(receivingProductivityList);
    productivity.setTotalElements(receivingProductivityPage.getTotalElements());
    productivity.setTotalPages(receivingProductivityPage.getTotalPages());
    productivity.setPage(receivingProductivityPage.getNumber() + 1);
    productivity.setRecordsPerPage(receivingProductivityPage.getNumberOfElements());

    LOGGER.info("Receiving Productivity report response '{}'", productivity);

    return productivity;
  }
}
