package com.walmart.move.nim.receiving.sib.job;

import com.walmart.move.nim.receiving.sib.config.SIBManagedConfig;
import com.walmart.move.nim.receiving.sib.service.EventPublisherService;
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

public class EventPublisherJob {

  private static final Logger LOGGER = LoggerFactory.getLogger(EventPublisherJob.class);

  @ManagedConfiguration private SIBManagedConfig sibManagedConfig;

  @Autowired private EventPublisherService eventPublisherService;

  @Scheduled(fixedDelayString = "${event.publishing.cron:60000}")
  @SchedulerLock(
      name = "SIBSchedulerJob_publishEvent",
      lockAtMostFor = 90000,
      lockAtLeastFor = 60000)
  public void publishEvent() {
    MDC.put(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    if (sibManagedConfig.getPublishEventJobRunEveryXMinute() == 0) {
      LOGGER.info("Publishing event is disabled. Returning.");
      return;
    }

    Calendar cal = Calendar.getInstance();
    if (cal.get(Calendar.MINUTE) % sibManagedConfig.getPublishEventJobRunEveryXMinute() != 0) {
      LOGGER.info("Publish event not enabled to run at {}. Returning", cal.getTime());
      return;
    }

    /*
    publish events for internal application to listen and process these further.
    @see com.walmart.move.nim.receiving.sib.message.listener.ContainerEventListener
    */
    eventPublisherService.publishEvents();
    MDC.clear();
  }
}
