package com.walmart.move.nim.receiving.core.job;

import com.walmart.atlas.global.config.annotation.EnableInPrimaryRegionNodeCondition;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.config.PurgeConfig;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.Calendar;
import java.util.List;
import javax.annotation.Resource;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * cron scheduled every day , how ever purge policy has to be fetched from CCM. for now executing it
 * every day midnight. The reason there are 3 different method is that we can apply different purge
 * policy on different table if needed.
 *
 * @author a0s01qi
 */
@Component
@Conditional(EnableInPrimaryRegionNodeCondition.class)
public class SchedulerJobs {
  private static final Logger LOGGER = LoggerFactory.getLogger(SchedulerJobs.class);

  @ManagedConfiguration PurgeConfig purgeConfig;
  @Autowired PurgeService purgeService;
  @ManagedConfiguration private AppConfig appConfig;
  @Autowired TenantSpecificConfigReader tenantSpecificConfigReader;

  @Resource(name = ReceivingConstants.DEFAULT_COMPLETE_DELIVERY_PROCESSOR)
  private DefaultCompleteDeliveryProcessor completeDeliveryProcessor;

  /** purges job */
  @Scheduled(fixedDelay = 60000)
  @SchedulerLock(name = "SchedulerJobs_purgeJob", lockAtLeastFor = 60000, lockAtMostFor = 90000)
  @TimeTracing(component = AppComponent.CORE, type = Type.SCHEDULER, flow = "purgeJob")
  public void purgeJob() {
    if (purgeConfig.getPurgeJobRunEveryXMinute() == 0) {
      LOGGER.info("PurgeJob disabled. Returning");
      return;
    }

    Calendar cal = Calendar.getInstance();
    if (cal.get(Calendar.MINUTE) % purgeConfig.getPurgeJobRunEveryXMinute() != 0) {
      LOGGER.info("PurgeJob not enabled to run at {}. Returning", cal.getTime());
      return;
    }

    LOGGER.info("PurgeJob : Start");
    try {
      purgeService.purge();
    } catch (Exception e) {
      LOGGER.error(
          "Got an error while executing purge job [error={}]", ExceptionUtils.getStackTrace(e));
    }
    LOGGER.info("PurgeJob : End");
  }

  @Scheduled(cron = "${auto.complete.dockTag.scheduler.cron:0 0 * ? * *}")
  @SchedulerLock(
      name = "${auto.complete.dockTag.scheduler.name:SchedulerJobs_autoCompleteDockTags}",
      lockAtLeastFor = 60000,
      lockAtMostFor = 90000)
  @Timed(
      name = "autoCompleteDTSchedulerTimed",
      level1 = "uwms-receiving",
      level2 = "schedulerJob",
      level3 = "autoCompleteDockTags")
  @ExceptionCounted(
      name = "autoCompleteDTExceptionCount",
      level1 = "uwms-receiving",
      level2 = "schedulerJob",
      level3 = "autoCompleteDockTags")
  @TimeTracing(component = AppComponent.CORE, type = Type.SCHEDULER, flow = "autoCompleteDockTags")
  public void autoCompleteDockTags() {

    if (appConfig.getDockTagAutoCompleteRunEveryHour() == 0) {
      LOGGER.info("Auto complete dock tags disabled. Returning");
      return;
    }

    Calendar cal = Calendar.getInstance();
    if (cal.get(Calendar.HOUR_OF_DAY) % appConfig.getDockTagAutoCompleteRunEveryHour() != 0) {
      LOGGER.info("Auto complete dock tags not enabled to run at {}. Returning", cal.getTime());
      return;
    }

    List<Integer> listOfFacilities = appConfig.getDockTagAutoCompleteEnabledFacilities();
    int pageSize = appConfig.getDockTagAutoCompletePageSize();
    for (Integer facilityNumber : listOfFacilities) {
      TenantContext.setFacilityNum(facilityNumber);
      TenantContext.setFacilityCountryCode(ReceivingConstants.COUNTRY_CODE_US);
      LOGGER.info("Auto complete dock tags for facilityNumber:{} : Starting ", facilityNumber);
      try {
        DockTagService dockTagService =
            tenantSpecificConfigReader.getConfiguredInstance(
                TenantContext.getFacilityNum().toString(),
                ReceivingConstants.DOCK_TAG_SERVICE,
                DockTagService.class);
        dockTagService.autoCompleteDocks(pageSize);
      } catch (Exception ex) {
        LOGGER.error(
            "Failed for facility number {} with exception {}, continuing for other facilities",
            facilityNumber,
            ExceptionUtils.getStackTrace(ex));
      }
    }
  }

  @Scheduled(cron = "${auto.complete.deliveries.scheduler.cron:0 0/5 * * * *}")
  @SchedulerLock(
      name = "${auto.complete.deliveries.scheduler.name:SchedulerJobs_autoCompleteDeliveries}",
      lockAtLeastFor = 60000,
      lockAtMostFor = 90000)
  @Timed(
      name = "autoCompleteDeliveriesSchedulerTimed",
      level1 = "uwms-receiving",
      level2 = "schedulerJob",
      level3 = "autoCompleteDeliveries")
  @ExceptionCounted(
      name = "autoCompleteDeliveriesExceptionCount",
      level1 = "uwms-receiving",
      level2 = "schedulerJob",
      level3 = "autoCompleteDeliveries")
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.SCHEDULER,
      flow = "autoCompleteDeliveries")
  public void autoCompleteDeliveries() {

    if (appConfig.getAutoCompleteDeliveryJobRunsEveryXMinutes() == 0) {
      LOGGER.info("Auto complete delivery disabled. Returning");
      return;
    }

    Calendar cal = Calendar.getInstance();
    if (cal.get(Calendar.MINUTE) % appConfig.getAutoCompleteDeliveryJobRunsEveryXMinutes() != 0) {
      LOGGER.info("Auto complete delivery not enabled to run at {}. Returning", cal.getTime());
      return;
    }

    List<Integer> listOfFacilities = appConfig.getDeliveryAutoCompleteEnabledFacilities();
    for (Integer facilityNumber : listOfFacilities) {
      TenantContext.setFacilityNum(facilityNumber);
      TenantContext.setFacilityCountryCode(ReceivingConstants.COUNTRY_CODE_US);
      try {
        LOGGER.info("Auto-complete delivery started for facility {}.", facilityNumber);
        tenantSpecificConfigReader
            .getConfiguredInstance(
                String.valueOf(TenantContext.getFacilityNum()),
                ReceivingConstants.COMPLETE_DELIVERY_PROCESSOR,
                CompleteDeliveryProcessor.class)
            .autoCompleteDeliveries(facilityNumber);
        LOGGER.info("Auto-complete delivery successfully done for facility {}.", facilityNumber);
      } catch (Exception ex) {
        LOGGER.error(
            "Failed for facility number {} with exception {}, continuing for other facilities",
            facilityNumber,
            ExceptionUtils.getStackTrace(ex));
      }
    }
  }

  @Scheduled(cron = "${auto.cancel.instructions.scheduler.cron:0 0/5 * * * *}")
  @SchedulerLock(
      name = "${auto.cancel.instructions.scheduler.name:SchedulerJobs_autoCancelInstruction}",
      lockAtLeastFor = 60000,
      lockAtMostFor = 90000)
  @Timed(
      name = "autoCancelInstructionSchedulerTimed",
      level1 = "uwms-receiving",
      level2 = "schedulerJob",
      level3 = "autoCancelInstructions")
  @ExceptionCounted(
      name = "autoCancelInstruction",
      level1 = "uwms-receiving",
      level2 = "schedulerJob",
      level3 = "autoCancelInstructions")
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.SCHEDULER,
      flow = "autoCancelInstructions")
  public void autoCancelInstruction() {

    if (appConfig.getAutoCancelInstructionJobRunsEveryXMinutes() == 0) {
      LOGGER.info("Auto cancel instruction disabled. Returning");
      return;
    }

    Calendar cal = Calendar.getInstance();
    if (cal.get(Calendar.MINUTE) % appConfig.getAutoCancelInstructionJobRunsEveryXMinutes() != 0) {
      LOGGER.info("Auto cancel instruction not enabled to run at {}. Returning", cal.getTime());
      return;
    }

    List<Integer> listOfFacilities = appConfig.getAutoCancelInstructionEnabledFacilities();
    for (Integer facilityNumber : listOfFacilities) {
      TenantContext.setFacilityNum(facilityNumber);
      TenantContext.setFacilityCountryCode(ReceivingConstants.COUNTRY_CODE_US);
      try {
        LOGGER.info("Auto-cancel instruction started for facility {}.", facilityNumber);
        tenantSpecificConfigReader
            .getConfiguredInstance(
                String.valueOf(TenantContext.getFacilityNum()),
                ReceivingConstants.INSTRUCTION_SERVICE,
                InstructionService.class)
            .autoCancelInstruction(facilityNumber);
        LOGGER.info("Auto-cancel instruction successfully done for facility {}.", facilityNumber);
      } catch (Exception ex) {
        LOGGER.error(
            "Failed for facility number {} with exception {}, continuing for other facilities",
            facilityNumber,
            ExceptionUtils.getStackTrace(ex));
      }
    }
  }
}
