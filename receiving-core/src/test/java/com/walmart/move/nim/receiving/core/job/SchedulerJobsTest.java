package com.walmart.move.nim.receiving.core.job;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.fail;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.config.PurgeConfig;
import com.walmart.move.nim.receiving.core.service.DefaultCompleteDeliveryProcessor;
import com.walmart.move.nim.receiving.core.service.DockTagService;
import com.walmart.move.nim.receiving.core.service.InstructionService;
import com.walmart.move.nim.receiving.core.service.PurgeService;
import java.util.Arrays;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class SchedulerJobsTest extends ReceivingTestBase {

  @InjectMocks private SchedulerJobs schedulerJobs;

  @Mock private PurgeConfig purgeConfig;
  @Mock private PurgeService purgeService;
  @Mock private AppConfig appConfig;
  @Mock private DockTagService dockTagService;
  @Mock private DefaultCompleteDeliveryProcessor completeDeliveryProcessor;
  @Mock private InstructionService instructionService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  public void tearDown() {
    reset(dockTagService, tenantSpecificConfigReader);
  }

  @Test
  public void purgeJobTestEnable() {
    when(purgeConfig.getPurgeJobRunEveryXMinute()).thenReturn(1);
    schedulerJobs.purgeJob();
    verify(purgeService, times(1)).purge();
  }

  @Test
  public void purgeJobTestDisable() {
    when(purgeConfig.getPurgeJobRunEveryXMinute()).thenReturn(0);
    schedulerJobs.purgeJob();
    verify(purgeService, times(0)).purge();
  }

  @Test
  public void testAutoCompleteDisabled() {
    when(appConfig.getDockTagAutoCompleteRunEveryHour()).thenReturn(0);
    schedulerJobs.autoCompleteDockTags();
    verify(dockTagService, times(0)).autoCompleteDocks(anyInt());
  }

  @Test
  public void testAutoComplete_SingleFacilityEnabled() {
    when(appConfig.getDockTagAutoCompleteRunEveryHour()).thenReturn(1);
    when(appConfig.getDockTagAutoCompleteHours()).thenReturn(48);
    when(appConfig.getDockTagAutoCompletePageSize()).thenReturn(10);
    when(appConfig.getDockTagAutoCompleteEnabledFacilities()).thenReturn(Arrays.asList(32835));
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), any(Class.class)))
        .thenReturn(dockTagService);

    schedulerJobs.autoCompleteDockTags();
    verify(dockTagService, times(1)).autoCompleteDocks(10);
  }

  @Test
  public void testAutoComplete_MultiFacilitiesEnabled() {
    when(appConfig.getDockTagAutoCompleteRunEveryHour()).thenReturn(1);
    when(appConfig.getDockTagAutoCompleteHours()).thenReturn(48);
    when(appConfig.getDockTagAutoCompletePageSize()).thenReturn(10);
    when(appConfig.getDockTagAutoCompleteEnabledFacilities())
        .thenReturn(Arrays.asList(32835, 32898));
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), any(Class.class)))
        .thenReturn(dockTagService);

    schedulerJobs.autoCompleteDockTags();
    verify(dockTagService, times(2)).autoCompleteDocks(10);
  }

  @Test
  public void testDeliveryAutoCompleteEnabled() {
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), any(Class.class)))
        .thenReturn(completeDeliveryProcessor);
    when(appConfig.getDeliveryAutoCompleteEnabledFacilities()).thenReturn(Arrays.asList(32835));
    when(appConfig.getAutoCompleteDeliveryJobRunsEveryXMinutes()).thenReturn(1);
    try {
      schedulerJobs.autoCompleteDeliveries();
      verify(completeDeliveryProcessor, times(1)).autoCompleteDeliveries(32835);
    } catch (Exception ex) {
      fail();
    }
    reset(completeDeliveryProcessor);
  }

  @Test
  public void testDeliveryAutoCompleteDisabled() {
    when(appConfig.getAutoCompleteDeliveryJobRunsEveryXMinutes()).thenReturn(0);
    try {
      schedulerJobs.autoCompleteDeliveries();
      verify(completeDeliveryProcessor, times(0)).autoCompleteDeliveries(anyInt());
    } catch (Exception e) {
      fail();
    }
    reset(completeDeliveryProcessor);
  }

  @Test
  public void testAutoCancelInstructionEnabled() {
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), any(Class.class)))
        .thenReturn(instructionService);
    when(appConfig.getAutoCancelInstructionEnabledFacilities()).thenReturn(Arrays.asList(32835));
    when(appConfig.getAutoCancelInstructionJobRunsEveryXMinutes()).thenReturn(1);
    try {
      schedulerJobs.autoCancelInstruction();
      verify(instructionService, times(1)).autoCancelInstruction(32835);
    } catch (Exception ex) {
      fail();
    }
    reset(instructionService);
  }

  @Test
  public void testAutoCancelInstructionDisabled() {
    when(appConfig.getAutoCancelInstructionJobRunsEveryXMinutes()).thenReturn(0);
    try {
      schedulerJobs.autoCancelInstruction();
      verify(instructionService, times(0)).autoCancelInstruction(anyInt());
    } catch (Exception e) {
      fail();
    }
    reset(instructionService);
  }
}
