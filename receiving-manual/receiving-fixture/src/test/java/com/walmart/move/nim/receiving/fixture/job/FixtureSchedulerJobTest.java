package com.walmart.move.nim.receiving.fixture.job;

import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.fixture.config.FixtureManagedConfig;
import com.walmart.move.nim.receiving.fixture.service.PalletReceivingService;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class FixtureSchedulerJobTest extends ReceivingTestBase {

  @InjectMocks private FixtureSchedulerJob fixtureSchedulerJob;
  @Mock private FixtureManagedConfig fixtureManagedConfig;
  @Mock private PalletReceivingService palletReceivingService;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  public void tearDown() {
    reset(palletReceivingService);
  }

  @Test
  public void testCheckAndRetryCTPostingDisabled() {
    when(fixtureManagedConfig.getCtJobRunEveryXMinute()).thenReturn(0);
    fixtureSchedulerJob.checkAndRetryCTPosting();
    verify(palletReceivingService, times(0)).checkAndRetryCTInventory();
  }

  @Test
  public void testCheckAndRetryCTPostingEnabled() {
    when(fixtureManagedConfig.getCtJobRunEveryXMinute()).thenReturn(1);
    fixtureSchedulerJob.checkAndRetryCTPosting();
    verify(palletReceivingService, times(1)).checkAndRetryCTInventory();
  }
}
