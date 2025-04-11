package com.walmart.move.nim.receiving.sib.job;

import com.walmart.move.nim.receiving.sib.config.SIBManagedConfig;
import com.walmart.move.nim.receiving.sib.service.EventProcessingService;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.Calendar;
import java.util.UUID;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

public class EventProcessingJob {

  private static final Logger LOGGER = LoggerFactory.getLogger(EventProcessingJob.class);

  @ManagedConfiguration private SIBManagedConfig sibManagedConfig;

  @Autowired private EventProcessingService eventProcessingService;

  @Scheduled(fixedDelayString = "${event.processing.cron:60000}")
  @SchedulerLock(
      name = "SIBSchedulerJob_processEvent",
      lockAtMostFor = 90000,
      lockAtLeastFor = 60000)
  public void publishEvent() {
    MDC.put(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    if (sibManagedConfig.getEventProcessingJobRunEveryXMinute() == 0) {
      LOGGER.info("Processing events is disabled. Returning.");
      return;
    }

    Calendar cal = Calendar.getInstance();
    if (cal.get(Calendar.MINUTE) % sibManagedConfig.getEventProcessingJobRunEveryXMinute() != 0) {
      return;
    }

    /*
    publish events for internal application to listen and process these further.
    @see com.walmart.move.nim.receiving.sib.message.listener.CleanupEventListener
    */
    eventProcessingService.processEvents();
    MDC.clear();
  }
}
