package com.walmart.move.nim.receiving.core.job;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.OsdrConfig;
import com.walmart.move.nim.receiving.core.model.OsdrConfigSpecification;
import com.walmart.move.nim.receiving.core.service.DefaultOsdrProcessor;
import com.walmart.move.nim.receiving.core.service.OsdrProcessor;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Arrays;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class OsdrSchedulerJobTest extends ReceivingTestBase {
  @InjectMocks private OsdrSchedulerJob osdrSchedulerJob;
  @Mock private OsdrConfig osdrConfig;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  private Gson gson;

  @BeforeClass
  public void setRootUp() {
    gson = new Gson();
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(osdrSchedulerJob, "osdrConfig", osdrConfig);
    ReflectionTestUtils.setField(
        osdrSchedulerJob, "tenantSpecificConfigReader", tenantSpecificConfigReader);
    ReflectionTestUtils.setField(osdrSchedulerJob, "gson", gson);
  }

  @AfterMethod
  public void tearDown() {
    reset(osdrConfig);
    reset(tenantSpecificConfigReader);
  }

  @Test
  public void testOsdrScheduler() throws ReceivingException {
    OsdrConfigSpecification osdrConfigSpecification = new OsdrConfigSpecification();
    osdrConfigSpecification.setUom(ReceivingConstants.Uom.VNPK);
    osdrConfigSpecification.setFacilityCountryCode("US");
    osdrConfigSpecification.setFacilityNum(32987);
    osdrConfigSpecification.setFrequencyFactor(1);
    osdrConfigSpecification.setNosOfDay(15);
    when(osdrConfig.getSpecifications())
        .thenReturn(gson.toJson(Arrays.asList(osdrConfigSpecification)));
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.OSDR_PROCESSOR), eq(OsdrProcessor.class)))
        .thenReturn(new DefaultOsdrProcessor());
    osdrSchedulerJob.osdrScheduler();
    verify(osdrConfig, times(1)).getSpecifications();
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.OSDR_PROCESSOR), eq(OsdrProcessor.class));
  }

  @Test
  public void testOsdrScheduler_OsdrConfigSpecificationNotAvailable() throws ReceivingException {
    when(osdrConfig.getSpecifications()).thenReturn(null);
    osdrSchedulerJob.osdrScheduler();
    verify(osdrConfig, times(2)).getSpecifications();
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.OSDR_PROCESSOR), eq(OsdrProcessor.class));
  }
}
