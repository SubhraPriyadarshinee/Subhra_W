package com.walmart.move.nim.receiving.fixture.job;

import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.fixture.config.FixtureManagedConfig;
import com.walmart.move.nim.receiving.fixture.service.PalletReceivingService;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.Calendar;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

public class FixtureSchedulerJob {
  private static final Logger LOGGER = LoggerFactory.getLogger(FixtureSchedulerJob.class);
  @ManagedConfiguration private FixtureManagedConfig fixtureManagedConfig;
  @Autowired private PalletReceivingService palletReceivingService;

  @Scheduled(fixedDelay = 60000)
  @SchedulerLock(name = "FixtureSchedulerJob_checkAndRetryCTPosting", lockAtMostFor = 90000)
  @TimeTracing(
      component = AppComponent.FIXTURE,
      type = Type.SCHEDULER,
      flow = "checkAndRetryCTPosting")
  public void checkAndRetryCTPosting() {
    if (fixtureManagedConfig.getCtJobRunEveryXMinute() == 0) {
      LOGGER.info("checkAndRetryCTPosting disabled. Returning");
      return;
    }

    Calendar cal = Calendar.getInstance();
    if (cal.get(Calendar.MINUTE) % fixtureManagedConfig.getCtJobRunEveryXMinute() != 0) {
      LOGGER.info("checkAndRetryCTPosting not enabled to run at {}. Returning", cal.getTime());
      return;
    }

    LOGGER.info("checkAndRetryCTPosting : Start");
    palletReceivingService.checkAndRetryCTInventory();
    LOGGER.info("checkAndRetryCTPosting : End");
  }
}
