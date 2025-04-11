package com.walmart.move.nim.receiving.acc.job;

import com.walmart.move.nim.receiving.acc.config.ACCManagedConfig;
import com.walmart.move.nim.receiving.acc.constants.ACCConstants;
import com.walmart.move.nim.receiving.acc.entity.NotificationLog;
import com.walmart.move.nim.receiving.acc.service.ACLNotificationService;
import com.walmart.move.nim.receiving.acc.service.PreLabelDeliveryService;
import com.walmart.move.nim.receiving.acc.util.ACCUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.config.PurgeConfig;
import com.walmart.move.nim.receiving.core.config.ReportConfig;
import com.walmart.move.nim.receiving.core.entity.DeliveryEvent;
import com.walmart.move.nim.receiving.core.model.MailTemplate;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.core.service.DeliveryEventPersisterService;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import com.walmart.move.nim.receiving.core.service.MailService;
import com.walmart.move.nim.receiving.reporting.service.ReportService;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.ReportingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Resource;
import javax.mail.internet.MimeMessage;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.apache.poi.ss.usermodel.Workbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.CollectionUtils;

/* Class containing all scheduler jobs for reporting and other purposes specific to ACC */
public class AccSchedulerJobs {

  private static final Logger LOGGER = LoggerFactory.getLogger(AccSchedulerJobs.class);
  private static final String ACL_LOG_MAIL_MESSAGE = "acl notification log";
  @ManagedConfiguration private ACCManagedConfig accManagedConfig;
  @ManagedConfiguration private ReportConfig reportConfig;
  @ManagedConfiguration private PurgeConfig purgeConfig;
  @ManagedConfiguration private AppConfig appConfig;
  @Autowired private ACLNotificationService aclNotificationService;
  @Autowired private MailService mailService;

  @Resource(name = ReceivingConstants.DEFAULT_REPORT_SERVICE)
  private ReportService reportService;

  @Autowired private DeliveryEventPersisterService deliveryEventPersisterService;

  @Autowired private PreLabelDeliveryService genericPreLabelDeliveryEventProcessor;

  @Resource(name = ReceivingConstants.ACC_DELIVERY_METADATA_SERVICE)
  private DeliveryMetaDataService deliveryMetaDataService;
  /**
   * Scheduler job to send ACL notification email
   *
   * @throws IOException
   */
  @Scheduled(cron = "${acl.notification.report.generation.scheduler.cron}")
  @SchedulerLock(
      name = "MailService_aclNotificationReportGeneratorScheduler",
      lockAtMostFor = 90000,
      lockAtLeastFor = 60000)
  public void aclNotificationReportGeneratorScheduler() throws IOException {
    Workbook workbook = null;
    try {
      Calendar cal = Calendar.getInstance();
      Date toDate = cal.getTime();
      cal.add(Calendar.HOUR, -reportConfig.getAclNotificationReportGenerationForLastXdays() * 24);
      Date fromDate = cal.getTime();
      List<NotificationLog> logsByDate = aclNotificationService.getAclLogsByDate(fromDate, toDate);
      if (!CollectionUtils.isEmpty(logsByDate)) {
        workbook = aclNotificationService.createExcelReportForAclNotificationLogs(logsByDate);
      }

      StringBuilder mailHtmlTemplate =
          reportService.createHtmlTemplateForReportingForEntity(
              logsByDate, ACL_LOG_MAIL_MESSAGE, ReceivingConstants.ACL_NOTIFICATION_ENABLED);
      MailTemplate mailTemplate =
          MailTemplate.builder()
              .reportFromAddress(reportConfig.getReportFromAddress())
              .reportToAddresses(reportConfig.getAclNotificationReportToAddress())
              .mailSubject(ReportingConstants.ACL_NOTIFICATION_REPORT_SUBJECT_LINE)
              .mailReportFileName(ReportingConstants.ACL_NOTIFICATION_REPORT_FILE_NAME)
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

  @Timed(
      name = "plgSchedulerTimed",
      level1 = "uwms-receiving",
      level2 = "accScheduler",
      level3 = "plgScheduler")
  @ExceptionCounted(
      name = "plgSchedulerExceptionCount",
      level1 = "uwms-receiving",
      level2 = "accScheduler",
      level3 = "plgScheduler")
  @Scheduled(cron = "${prelabel.generation.scheduler.cron}")
  @SchedulerLock(name = "AccSchedulerJobs_aclPrelabelGeneration", lockAtMostFor = 90000)
  public void preLabelGenerationScheduler() {
    try {
      DeliveryEvent deliveryEvent =
          deliveryEventPersisterService.getDeliveryForScheduler(
              accManagedConfig.getPregenSchedulerRetriesCount());
      String correlationId = UUID.randomUUID().toString();
      if (Objects.nonNull(deliveryEvent)) {
        Long deliveryNumber = deliveryEvent.getDeliveryNumber();
        LOGGER.info(
            "Picked up delivery number {}, correlation id {}", deliveryNumber, correlationId);
        ReceivingUtils.setTenantContext(
            deliveryEvent.getFacilityNum().toString(),
            deliveryEvent.getFacilityCountryCode(),
            correlationId,
            this.getClass().getName());
        List<DeliveryEvent> deliveryEvents =
            deliveryEventPersisterService.getDeliveryEventsForScheduler(
                deliveryEvent.getDeliveryNumber(),
                ACCUtils.getEventStatusesForScheduler(),
                accManagedConfig.getPregenSchedulerRetriesCount());

        if (!validateDeliveryAndLocationInfo(deliveryNumber, deliveryEvents)) return;

        if (ACCUtils.isDeliveryExistsOfType(
            deliveryEvents, ReceivingConstants.EVENT_DOOR_ASSIGNED)) {
          Optional<DeliveryEvent> doorAssignedEvent =
              deliveryEvents
                  .parallelStream()
                  .filter(de -> ReceivingConstants.EVENT_DOOR_ASSIGNED.equals(de.getEventType()))
                  .findAny();
          deliveryEvents.removeIf(
              de -> ReceivingConstants.EVENT_DOOR_ASSIGNED.equals(de.getEventType()));
          deliveryEventPersisterService.markAndSaveDeliveryEvents(
              deliveryEvents, EventTargetStatus.DELETE);
          doorAssignedEvent.ifPresent(
              event ->
                  genericPreLabelDeliveryEventProcessor.processDeliveryEventForScheduler(event));
        } else {
          for (DeliveryEvent oldestDeliveryEvent : deliveryEvents) {
            if (!genericPreLabelDeliveryEventProcessor.processDeliveryEventForScheduler(
                oldestDeliveryEvent)) {
              return;
            }
          }
        }
      }
    } catch (Exception e) {
      LOGGER.error(ACCConstants.PLG_SCHEDULER_ERROR, (Object) e.getStackTrace());
    }
  }

  private boolean validateDeliveryAndLocationInfo(
      Long deliveryNumber, List<DeliveryEvent> deliveryEvents) {
    Map<String, String> pathParams =
        Collections.singletonMap(ReceivingConstants.DELIVERY_NUMBER, deliveryNumber.toString());
    String url =
        ReceivingUtils.replacePathParams(
                appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_GET_DELIVERY_URI, pathParams)
            .toString();

    DeliveryDetails deliveryDetails =
        genericPreLabelDeliveryEventProcessor.fetchDeliveryDetails(url, deliveryNumber);

    Boolean isOnlineOrHasFloorLine =
        genericPreLabelDeliveryEventProcessor.validateDeliveryDetailsAndLocationInfo(
            deliveryNumber, deliveryDetails);

    if (Objects.isNull(deliveryDetails) || Objects.isNull(isOnlineOrHasFloorLine)) {
      LOGGER.info(
          "Failed to fetch delivery: {} details or location info. Hence stopping scheduler",
          deliveryNumber);
      deliveryEventPersisterService.markAndSaveDeliveryEvents(
          deliveryEvents, EventTargetStatus.PENDING);
      return false;
    }

    if (!Boolean.TRUE.equals(isOnlineOrHasFloorLine)) {
      LOGGER.info(
          "Delivery {} is at an offline door {}. Hence ignoring for scheduler pre-label generation and stopping all events",
          deliveryNumber,
          deliveryDetails.getDoorNumber());
      deliveryEventPersisterService.markAndSaveDeliveryEvents(
          deliveryEvents, EventTargetStatus.DELETE);
      return false;
    }
    return true;
  }

  @Scheduled(cron = "${prelabel.stale.check.scheduler.cron}")
  @SchedulerLock(name = "AccSchedulerJobs_aclPreLabelStaleCheck", lockAtMostFor = 90000)
  public void preLabelStaleCheck() {
    try {
      Calendar cal = Calendar.getInstance();
      cal.add(Calendar.SECOND, -accManagedConfig.getPregenStaleCheckTimeout());
      Date cutoffTime = cal.getTime();
      List<DeliveryEvent> staleDeliveryEvents =
          deliveryEventPersisterService.getStaleDeliveryEvents(cutoffTime);
      LOGGER.info("Marking {} events as STALE", staleDeliveryEvents.size());
      deliveryEventPersisterService.markAndSaveDeliveryEvents(
          staleDeliveryEvents, EventTargetStatus.STALE);
    } catch (Exception e) {
      LOGGER.error(ACCConstants.PLG_SCHEDULER_ERROR, (Object) e.getStackTrace());
    }
  }

  @Scheduled(fixedDelayString = "${delivery.complete.scheduler.delay:60000}")
  @SchedulerLock(name = "AccSchedulerJobs_systematicCompleteDelivery", lockAtMostFor = 90000)
  public void completeSystematicallyReopenedDeliveries() {
    LOGGER.info("Starting: complete SYS_REO opened deliveries.");
    Date beforeDate =
        Date.from(
            Instant.now().minus(Duration.ofMinutes(accManagedConfig.getSysReopenedLifeInMin())));
    // find all deliveries that were SYS_REOPEN before this time and call complete.
    try {
      deliveryMetaDataService.completeSystematicallyReopenedDeliveriesBefore(beforeDate);
    } catch (ReceivingException e) {
      LOGGER.error("Error while systematically completing deliveries");
    }
  }
}
