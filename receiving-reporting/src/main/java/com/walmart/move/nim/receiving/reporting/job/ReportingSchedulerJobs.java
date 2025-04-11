package com.walmart.move.nim.receiving.reporting.job;

import com.walmart.atlas.global.config.annotation.EnableInPrimaryRegionNodeCondition;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.config.ReportConfig;
import com.walmart.move.nim.receiving.core.config.app.TenantSpecificReportConfig;
import com.walmart.move.nim.receiving.core.entity.ItemCatalogUpdateLog;
import com.walmart.move.nim.receiving.core.model.MailTemplate;
import com.walmart.move.nim.receiving.core.service.MailService;
import com.walmart.move.nim.receiving.reporting.model.BreakPackLabelInfo;
import com.walmart.move.nim.receiving.reporting.model.ReportData;
import com.walmart.move.nim.receiving.reporting.model.RxItemCatalogReportData;
import com.walmart.move.nim.receiving.reporting.repositories.ReportingItemCatalogCustomRepository;
import com.walmart.move.nim.receiving.reporting.service.ReportPersisterService;
import com.walmart.move.nim.receiving.reporting.service.ReportService;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.ReportingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.io.IOException;
import java.util.*;
import javax.annotation.Resource;
import javax.mail.internet.MimeMessage;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.poi.ss.usermodel.Workbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/**
 * Reporting related scheduler jobs
 *
 * @author sks0013
 */
@Component
@Conditional(EnableInPrimaryRegionNodeCondition.class)
public class ReportingSchedulerJobs {
  private static final Logger LOGGER = LoggerFactory.getLogger(ReportingSchedulerJobs.class);
  public static final String ITEM_CATALOG_MAIL_MESSAGE = "item catalog";
  public static final String ITEM_CATALOG_PHARNACY_MAIL_MESSAGE = "pharmacy item catalog";

  @Resource(name = ReceivingConstants.DEFAULT_REPORT_SERVICE)
  private ReportService reportService;

  @Autowired private ReportPersisterService reportPersisterService;

  @Autowired private TenantSpecificReportConfig configUtils;

  @Autowired private MailService mailService;

  @ManagedConfiguration private ReportConfig reportConfig;

  @Autowired private ReportingItemCatalogCustomRepository reportingItemCatalogCustomRepository;

  /**
   * Scheduler job to send report weekly
   *
   * @throws IOException
   */
  @Scheduled(cron = "${stats.report.generation.scheduler.cron}")
  @SchedulerLock(name = "MailService_reportGeneratorScheduler", lockAtMostFor = 90000)
  public void reportGeneratorScheduler() throws IOException {
    if (reportConfig.isEmailReportEnabled()) {
      Workbook workbook = null;
      try {
        List<Integer> facilityNumbers = configUtils.getFacilityNumList();
        Map<Integer, ReportData> reportData =
            reportService.getReportForAllFacilityNumbers(
                facilityNumbers, ReceivingConstants.COUNTRY_CODE_US);
        workbook = reportService.createExcelReport(reportData);
        StringBuilder mailHtmlTemplate = reportService.createHtmlTemplateForStatistics(reportData);
        MailTemplate mailTemplate =
            MailTemplate.builder()
                .reportFromAddress(reportConfig.getReportFromAddress())
                .reportToAddresses(reportConfig.getStatsReportToAddresses())
                .mailSubject(ReportingConstants.STATS_REPORT_FILE_NAME)
                .mailReportFileName(ReportingConstants.STATS_REPORT_SUBJECT_LINE)
                .mailHtmlTemplate(mailHtmlTemplate.toString())
                .attachment(workbook)
                .build();
        MimeMessage mimeMessage = mailService.prepareMailMessage(mailTemplate);
        mailService.sendMail(mimeMessage);
      } catch (ReceivingException e) {
        LOGGER.error(ReportingConstants.REPORT_GENERATION_ERROR, e.getMessage());
      } finally {
        if (workbook != null) workbook.close();
      }
    } else {
      LOGGER.info("Email report is not enabled, skipping reporting generation");
    }
  }

  /**
   * Scheduler job to send item catalog report weekly
   *
   * @throws IOException
   */
  @Scheduled(cron = "${item.catalog.report.generation.scheduler.cron}")
  @SchedulerLock(
      name = "MailService_itemCatalogReportGeneratorScheduler",
      lockAtMostFor = 90000,
      lockAtLeastFor = 60000)
  public void itemCatalogReportGeneratorScheduler() throws IOException {
    Workbook workbook = null;
    try {
      Calendar cal = Calendar.getInstance();
      Date toDate = cal.getTime();
      cal.add(Calendar.HOUR, -reportConfig.getItemCatalogReportGenerationForLastXdays() * 24);
      Date fromDate = cal.getTime();
      List<ItemCatalogUpdateLog> itemCatalogUpdateLogs =
          reportPersisterService.getItemCatalogUpdateLogsByDate(
              fromDate, toDate, reportConfig.getItemCatalogReportExcludeDcList());
      if (!CollectionUtils.isEmpty(itemCatalogUpdateLogs)) {
        workbook = reportService.createExcelReportForItemCatalog(itemCatalogUpdateLogs);
      }
      StringBuilder mailHtmlTemplate =
          reportService.createHtmlTemplateForReportingForEntity(
              itemCatalogUpdateLogs,
              ITEM_CATALOG_MAIL_MESSAGE,
              ReceivingConstants.ITEM_CATALOG_ENABLED);

      MailTemplate mailTemplate =
          MailTemplate.builder()
              .reportFromAddress(reportConfig.getReportFromAddress())
              .reportToAddresses(reportConfig.getItemCatalogReportToAddresses())
              .mailSubject(ReportingConstants.ITEM_CATALOG_REPORT_SUBJECT_LINE)
              .mailReportFileName(ReportingConstants.ITEM_CATALOG_REPORT_FILE_NAME)
              .mailHtmlTemplate(mailHtmlTemplate.toString())
              .attachment(workbook)
              .build();
      MimeMessage mimeMessage = mailService.prepareMailMessage(mailTemplate);
      mailService.sendMail(mimeMessage);
    } catch (ReceivingException e) {
      LOGGER.error(ReportingConstants.REPORT_GENERATION_ERROR, e.getMessage());
    } finally {
      if (workbook != null) workbook.close();
    }
  }

  @Scheduled(cron = "${item.catalog.pharmacy.report.generation.scheduler.cron}")
  @SchedulerLock(
      name = "MailService_itemCatalogReportGeneratorSchedulerForPharmacy",
      lockAtMostFor = 210000,
      lockAtLeastFor = 180000)
  public void pharmacyItemCatalogReportGeneratorScheduler() throws IOException {

    Workbook workbook = null;
    try {

      Calendar cal = Calendar.getInstance();
      Date toDate = cal.getTime();
      cal.add(
          Calendar.HOUR, -reportConfig.getItemCatalogReportPharmacyGenerationForLastXdays() * 24);
      Date fromDate = cal.getTime();
      String fromDateStr = DateFormatUtils.format(fromDate, ReceivingConstants.UTC_DATE_FORMAT);
      String toDateStr = DateFormatUtils.format(toDate, ReceivingConstants.UTC_DATE_FORMAT);

      LOGGER.info(
          "Item Catalog Report Generator Scheduler For Pharmacy Started. From date : {}, To date : {}",
          fromDateStr,
          toDateStr);
      List<RxItemCatalogReportData> rxItemCatalogReportData =
          reportingItemCatalogCustomRepository.getRxItemCatalogReportData(
              reportConfig.getItemCatalogReportPharmacyIncludeDcList(), "us", fromDate, toDate);

      if (!CollectionUtils.isEmpty(rxItemCatalogReportData)) {
        workbook = reportService.createRxExcelReportForItemCatalog(rxItemCatalogReportData);
      }
      StringBuilder mailHtmlTemplate =
          reportService.createHtmlTemplateForReportingForEntity(
              rxItemCatalogReportData,
              ITEM_CATALOG_PHARNACY_MAIL_MESSAGE,
              ReceivingConstants.ITEM_CATALOG_NOTIFICATION_ENABLED);

      MailTemplate mailTemplate =
          MailTemplate.builder()
              .reportFromAddress(reportConfig.getPharmacyReportFromAddress())
              .reportToAddresses(reportConfig.getPharmacyItemCatalogReportToAddresses())
              .mailSubject(ReportingConstants.ITEM_CATALOG_REPORT_PHARMACY_SUBJECT_LINE)
              .mailReportFileName(ReportingConstants.ITEM_CATALOG_RPHARMACY_REPORT_FILE_NAME)
              .mailHtmlTemplate(mailHtmlTemplate.toString())
              .attachment(workbook)
              .build();
      MimeMessage mimeMessage = mailService.prepareMailMessage(mailTemplate);
      mailService.sendMail(mimeMessage);
      LOGGER.info("Item Catalog Report Generator Scheduler For Pharmacy Completed");
    } catch (ReceivingException e) {
      LOGGER.error(ReportingConstants.REPORT_GENERATION_ERROR, e.getMessage());
    } finally {
      if (workbook != null) workbook.close();
    }
  }

  @Scheduled(cron = "${metrics.report.generation.scheduler.cron}")
  @SchedulerLock(
      name = "MailService_metricsReportGeneratorSchedulerForPharmacy",
      lockAtMostFor = 150000,
      lockAtLeastFor = 120000)
  public void pharmacyMetricReportScheduler() throws IOException {

    Workbook workbook = null;

    try {

      Calendar cal = Calendar.getInstance();
      Date toDate = cal.getTime();
      cal.add(Calendar.HOUR, -reportConfig.getMetricsReportGenerationForLastXdays() * 24);

      Date fromDate = cal.getTime();
      String fromDateStr = DateFormatUtils.format(fromDate, ReceivingConstants.UTC_DATE_FORMAT);
      String toDateStr = DateFormatUtils.format(toDate, ReceivingConstants.UTC_DATE_FORMAT);

      LOGGER.info(
              "Metrics Report Scheduler for Pharmacy Started. From date : {}, To date : {}",
              fromDateStr,
              toDateStr);
      Map<String, Integer> pharmacyReport = new LinkedHashMap<>();
      Integer totalDeliveryCount = 0;
      Integer totalPalletSSCCCount = 0;
      Integer totalCaseSSCCCount = 0;
      Integer totalUnit2DCount = 0;
      Integer totalCase2DCount = 0;
      Integer totalLabelsCancelled = 0;
      Integer totalCasesReceivedAgainstASN = 0;
      Integer totalCasesReceivedAgainstEPCIS = 0;
      Integer totalCasesReceivedAgainstUPC = 0;
      Integer totalUnitsReceivedAgainstASN = 0;
      Integer totalUnitsReceivedAgainstEPCIS = 0;
      Integer totalUnitsReceivedAgainstUPC = 0;

      for (Integer facilityNbr : reportConfig.getMetricsReportPharmacyIncludeDcList()) {

        Integer deliveryCount =
                reportPersisterService.getAtlasReportCount(
                        fromDate, toDate, facilityNbr, "US");
        pharmacyReport.put(facilityNbr + " :: Number of deliveries received through Atlas", deliveryCount);

        totalDeliveryCount += deliveryCount;

        Integer palletSSCCScanCount =
                reportPersisterService.getPalletSSCCScanCount(
                        fromDate,
                        toDate,
                        facilityNbr,
                        "US",
                        Arrays.asList(ReportingConstants.RX_BUILD_PALLET, ReportingConstants.RX_SER_BUILD_PALLET)
                );
        pharmacyReport.put(
                facilityNbr + " :: Number of pallets received through Pallet SSCC scan", palletSSCCScanCount);

        totalPalletSSCCCount += palletSSCCScanCount;

        Integer caseSSCCScanCount =
                reportPersisterService.getCaseSSCCScanCount(
                        fromDate,
                        toDate,
                        facilityNbr,
                        "US",
                        Arrays.asList(ReportingConstants.RX_CNTR_CASE_SCAN, ReportingConstants.RX_SER_CNTR_CASE_SCAN)
                );
        pharmacyReport.put(
                facilityNbr + " :: Number of pallets received through Case SSCC scan", caseSSCCScanCount);

        totalCaseSSCCCount += caseSSCCScanCount;

        Integer unit2DScanCount =
                reportPersisterService.get2DScanCounts(
                        fromDate,
                        toDate,
                        facilityNbr,
                        "US",
                        Arrays.asList(ReportingConstants.RX_BUILD_UNIT_SCAN, ReportingConstants.RX_SER_BUILD_UNIT_SCAN)
                );
        pharmacyReport.put(
                facilityNbr + " :: Number of pallets received through Unit 2D scan", unit2DScanCount);

        totalUnit2DCount += unit2DScanCount;

        Integer case2DScanReceived =
                reportPersisterService.get2DScanCounts(
                        fromDate,
                        toDate,
                        facilityNbr,
                        "US",
                        Arrays.asList(ReportingConstants.RX_CNTR_GTIN_AND_LOT, ReportingConstants.RX_SER_CNTR_GTIN_AND_LOT)
                );
        pharmacyReport.put(facilityNbr + " :: Number of pallets received through Case 2D scan", case2DScanReceived);

        totalCase2DCount += case2DScanReceived;

        Integer palletLabelCanceledCount =
                reportPersisterService.getPalletLabelCanceledCount(
                        fromDate,
                        toDate,
                        facilityNbr,
                        "US",
                        ReportingConstants.BACKOUT);
        pharmacyReport.put(facilityNbr + " :: Number of pallet labels cancelled", palletLabelCanceledCount);

        totalLabelsCancelled += palletLabelCanceledCount;

        Integer casesReceivedCountAgainstASN =
                reportPersisterService.getNumberOfCasesReceived(
                        fromDate,
                        toDate,
                        facilityNbr,
                        "US",
                        Arrays.asList(ReportingConstants.RX_BUILD_PALLET, ReportingConstants.RX_CNTR_CASE_SCAN, ReportingConstants.RX_CNTR_GTIN_AND_LOT, ReportingConstants.RX_BUILD_UNIT_SCAN)
                );
        pharmacyReport.put(facilityNbr + " :: Number of Cases received against ASN", casesReceivedCountAgainstASN);

        totalCasesReceivedAgainstASN += casesReceivedCountAgainstASN;

        Integer casesReceivedCountAgainstEPCIS =
                reportPersisterService.getNumberOfCasesReceived(
                        fromDate,
                        toDate,
                        facilityNbr,
                        "US",
                        Arrays.asList(ReportingConstants.RX_SER_BUILD_PALLET, ReportingConstants.RX_SER_CNTR_CASE_SCAN, ReportingConstants.RX_SER_CNTR_GTIN_AND_LOT, ReportingConstants.RX_SER_BUILD_UNIT_SCAN)
                );
        pharmacyReport.put(facilityNbr + " :: Number of Cases received against EPCIS", casesReceivedCountAgainstEPCIS);

        totalCasesReceivedAgainstEPCIS += casesReceivedCountAgainstEPCIS;

        Integer casesReceivedCountAgainstUPC =
                reportPersisterService.getNumberOfCasesReceived(
                        fromDate,
                        toDate,
                        facilityNbr,
                        "US",
                        Arrays.asList(ReportingConstants.BUILD_CONTAINER, ReportingConstants.D40_UNIT_UPC_RECEIVING)
                );
        pharmacyReport.put(facilityNbr + " :: Number of Cases received against UPC(Exempted Items)", casesReceivedCountAgainstUPC);

        totalCasesReceivedAgainstUPC += casesReceivedCountAgainstUPC;

        Integer unitsReceivedCountAgainstASN =
                reportPersisterService.getNumberOfUnitsReceived(
                        fromDate,
                        toDate,
                        facilityNbr,
                        "US",
                        Arrays.asList(ReportingConstants.RX_BUILD_PALLET, ReportingConstants.RX_CNTR_CASE_SCAN, ReportingConstants.RX_CNTR_GTIN_AND_LOT, ReportingConstants.RX_BUILD_UNIT_SCAN)
                );
        pharmacyReport.put(facilityNbr + " :: Number of Units received against ASN", unitsReceivedCountAgainstASN);

        totalUnitsReceivedAgainstASN += unitsReceivedCountAgainstASN;

        Integer unitsReceivedCountAgainstEPCIS =
                reportPersisterService.getNumberOfUnitsReceived(
                        fromDate,
                        toDate,
                        facilityNbr,
                        "US",
                        Arrays.asList(ReportingConstants.RX_SER_BUILD_PALLET, ReportingConstants.RX_SER_CNTR_CASE_SCAN, ReportingConstants.RX_SER_CNTR_GTIN_AND_LOT, ReportingConstants.RX_SER_BUILD_UNIT_SCAN)
                );
        pharmacyReport.put(facilityNbr + " :: Number of Units received against EPCIS", unitsReceivedCountAgainstEPCIS);

        totalUnitsReceivedAgainstEPCIS += unitsReceivedCountAgainstEPCIS;

        Integer unitsReceivedCountAgainstUPC =
                reportPersisterService.getNumberOfUnitsReceived(
                        fromDate,
                        toDate,
                        facilityNbr,
                        "US",
                        Arrays.asList(ReportingConstants.BUILD_CONTAINER, ReportingConstants.D40_UNIT_UPC_RECEIVING)
                );
        pharmacyReport.put(facilityNbr + " :: Number of Units received against UPC(Exempted Items)", unitsReceivedCountAgainstUPC);

        totalUnitsReceivedAgainstUPC += unitsReceivedCountAgainstUPC;

        Integer itemsReceivedAgainstASN =
                reportPersisterService.getNumberOfItemsReceived(
                        fromDate,
                        toDate,
                        Arrays.asList(facilityNbr),
                        "US",
                        Arrays.asList(ReportingConstants.RX_BUILD_PALLET, ReportingConstants.RX_CNTR_CASE_SCAN, ReportingConstants.RX_CNTR_GTIN_AND_LOT, ReportingConstants.RX_BUILD_UNIT_SCAN)
                );
        pharmacyReport.put(facilityNbr + " :: Number of Unique Items received against ASN", itemsReceivedAgainstASN);

        Integer itemsReceivedAgainstEPCIS =
                reportPersisterService.getNumberOfItemsReceived(
                        fromDate,
                        toDate,
                        Arrays.asList(facilityNbr),
                        "US",
                        Arrays.asList(ReportingConstants.RX_SER_BUILD_PALLET, ReportingConstants.RX_SER_CNTR_CASE_SCAN, ReportingConstants.RX_SER_CNTR_GTIN_AND_LOT, ReportingConstants.RX_SER_BUILD_UNIT_SCAN)
                );
        pharmacyReport.put(facilityNbr + " :: Number of Unique Items received against EPCIS", itemsReceivedAgainstEPCIS);

        Integer itemsReceivedAgainstUPC =
                reportPersisterService.getNumberOfItemsReceived(
                        fromDate,
                        toDate,
                        Arrays.asList(facilityNbr),
                        "US",
                        Arrays.asList(ReportingConstants.BUILD_CONTAINER, ReportingConstants.D40_UNIT_UPC_RECEIVING)
                );
        pharmacyReport.put(facilityNbr + " :: Number of Unique Items received against UPC(Exempted Items)", itemsReceivedAgainstUPC);
      }

      Integer totalItemsReceivedAgainstASN =
              reportPersisterService.getNumberOfItemsReceived(
                      fromDate,
                      toDate,
                      reportConfig.getMetricsReportPharmacyIncludeDcList(),
                      "US",
                      Arrays.asList(ReportingConstants.RX_BUILD_PALLET, ReportingConstants.RX_CNTR_CASE_SCAN, ReportingConstants.RX_CNTR_GTIN_AND_LOT, ReportingConstants.RX_BUILD_UNIT_SCAN)
              );

      Integer totalItemsReceivedAgainstEPCIS =
              reportPersisterService.getNumberOfItemsReceived(
                      fromDate,
                      toDate,
                      reportConfig.getMetricsReportPharmacyIncludeDcList(),
                      "US",
                      Arrays.asList(ReportingConstants.RX_SER_BUILD_PALLET, ReportingConstants.RX_SER_CNTR_CASE_SCAN, ReportingConstants.RX_SER_CNTR_GTIN_AND_LOT, ReportingConstants.RX_SER_BUILD_UNIT_SCAN)
              );

      Integer totalItemsReceivedAgainstUPC =
              reportPersisterService.getNumberOfItemsReceived(
                      fromDate,
                      toDate,
                      reportConfig.getMetricsReportPharmacyIncludeDcList(),
                      "US",
                      Arrays.asList(ReportingConstants.BUILD_CONTAINER, ReportingConstants.D40_UNIT_UPC_RECEIVING)
              );

      Map<String, Integer> sortedReport = new LinkedHashMap<>();

      sortedReport.put("Total number of Cases received against EPCIS", totalCasesReceivedAgainstEPCIS);
      sortedReport.put("Total number of Units received against EPCIS", totalUnitsReceivedAgainstEPCIS);
      sortedReport.put("Number of unique Items received against EPCIS", totalItemsReceivedAgainstEPCIS);

      sortedReport.put("Total number of Cases received against ASN", totalCasesReceivedAgainstASN);
      sortedReport.put("Total number of Units received against ASN", totalUnitsReceivedAgainstASN);
      sortedReport.put("Number of unique Items received against ASN", totalItemsReceivedAgainstASN);

      sortedReport.put("Total number of Cases received against UPC(Exempted Items)", totalCasesReceivedAgainstUPC);
      sortedReport.put("Total number of Units received against UPC(Exempted Items)", totalUnitsReceivedAgainstUPC);
      sortedReport.put("Number of unique Items received against UPC(Exempted Items)", totalItemsReceivedAgainstUPC);

      sortedReport.put("Total number of containers received through Pallet SSCC scan", totalPalletSSCCCount);
      sortedReport.put("Total number of containers received through Case SSCC scan", totalCaseSSCCCount);
      sortedReport.put("Total number of containers received through Case 2D scan", totalCase2DCount);
      sortedReport.put("Total number of containers received through Unit 2D scan", totalUnit2DCount);

      sortedReport.put("Total number of Deliveries received through Atlas", totalDeliveryCount);
      sortedReport.put("Total number of pallet labels cancelled", totalLabelsCancelled);

      sortedReport.put(" ",null);
      if (!pharmacyReport.isEmpty()) {
        sortedReport.putAll(pharmacyReport);
      }

      LOGGER.info("Pharmacy Report E-Mail message {}", sortedReport);
      workbook = reportService.createExcelReportForPharmacyReceivingMetrics(sortedReport);

      StringBuilder mailHtmlTemplate =
          reportService.createHtmlTemplateForPharmacyReceivingMetrics(sortedReport);

      MailTemplate mailTemplate =
          MailTemplate.builder()
              .reportFromAddress(reportConfig.getPharmacyReportFromAddress())
              .reportToAddresses(reportConfig.getMetricsReportToAddresses())
              .mailSubject(ReportingConstants.PHARMACY_REPORT_SUBJECT_LINE)
              .mailReportFileName(ReportingConstants.PHARMACY_REPORT_SUBJECT_LINE)
              .mailHtmlTemplate(mailHtmlTemplate.toString())
              .attachment(workbook)
              .build();
      MimeMessage mimeMessage = mailService.prepareMailMessage(mailTemplate);
      mailService.sendMail(mimeMessage);
      LOGGER.info("Metrics Report of Delivery Count For Pharmacy is Completed");
    } catch (ReceivingException e) {
      LOGGER.error(ReportingConstants.REPORT_GENERATION_ERROR, e.getMessage());
    } finally {
      if (workbook != null) workbook.close();
    }
  }

  /**
   * Scheduler job to send container report daily
   *
   * @throws IOException
   */
  @Scheduled(cron = "${break.pack.receive.container.scheduler.cron:0 0 5 * * *}")
  @SchedulerLock(
      name =
          "${da.break.pack.backout.report.scheduler.name:SchedulerJobs_breakPackReceiveContainer}",
      lockAtMostFor = 90000,
      lockAtLeastFor = 60000)
  public void breakPackReceivedContainersReportGeneratorScheduler() throws IOException {
    LOGGER.info("BreakPack BackOut Containers scheduler started");
    List<Integer> facilityNumbers = reportConfig.getAtlasDaBreakPackBackOutReportFacilityIds();
    List<List<BreakPackLabelInfo>> combinedBreakPackReceiveContainers = null;
    if (facilityNumbers != null && !facilityNumbers.isEmpty()) {
      for (Integer facilityNumber : facilityNumbers) {
        combinedBreakPackReceiveContainers =
            reportService.fetchBreakPackReceiveContainerDetails(facilityNumber);
        if (CollectionUtils.isEmpty(combinedBreakPackReceiveContainers)) {
          combinedBreakPackReceiveContainers.add(Collections.emptyList());
          continue;
        }

        for (List<BreakPackLabelInfo> breakPackReceiveContainers :
            combinedBreakPackReceiveContainers) {

          if (!CollectionUtils.isEmpty(breakPackReceiveContainers)) {
            Workbook workbook = null;
            try {
              workbook =
                  reportService.createExcelReportForBreakPackReceivedContainers(
                      breakPackReceiveContainers);
              String mailHtmlTemplate =
                  reportService.createHtmlTemplateForBreakPackBackOutContainers(
                      breakPackReceiveContainers);
              MailTemplate mailTemplate =
                  MailTemplate.builder()
                      .reportFromAddress(
                          reportConfig.getAtlasDaBreakPackBackOutReportFromEmailAddress())
                      .reportToAddresses(
                          reportConfig.getAtlasDaBreakPackBackOutReportToEmailAddress())
                      .mailSubject(
                          String.format(
                              ReportingConstants.BREAKPACK_BACKOUT_REPORT_SUBJECT_LINE,
                              facilityNumber))
                      .mailReportFileName(ReportingConstants.BREAKPACK_BACKOUT_REPORT_SUBJECT_LINE)
                      .mailHtmlTemplate(mailHtmlTemplate)
                      .attachment(workbook)
                      .build();

              MimeMessage mimeMessage = mailService.prepareMailMessage(mailTemplate);
              mailService.sendMail(mimeMessage);
              LOGGER.info("BreakPack BackOut Containers scheduler completed");
            } catch (Exception exception) {
              LOGGER.error(ReportingConstants.REPORT_GENERATION_ERROR, exception);
            } finally {
              if (Objects.nonNull(workbook)) {
                workbook.close();
              }
            }
          } else {
            LOGGER.info("BreakPack BackOut Containers does not exist");
          }
        }
      }
    }
  }
}
