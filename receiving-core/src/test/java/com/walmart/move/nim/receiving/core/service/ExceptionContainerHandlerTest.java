package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.SorterPublisher;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.service.exceptioncontainer.ExceptionContainerHandler;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.SorterExceptionReason;
import java.util.Date;
import java.util.UUID;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ExceptionContainerHandlerTest extends ReceivingTestBase {
  @InjectMocks ExceptionContainerHandler exceptionContainerHandler;
  @Mock TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock SorterPublisher sorterPublisher;

  private String lpn = "a0000000000000001234";

  @BeforeClass
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32987);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setCorrelationId(UUID.randomUUID().toString());
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), any(Class.class)))
        .thenReturn(sorterPublisher);
  }

  @Test
  public void testPublishExceptionDivertToSorter() {
    exceptionContainerHandler.publishExceptionDivertToSorter(
        lpn, SorterExceptionReason.OVERAGE, new Date());
    verify(sorterPublisher, times(1))
        .publishException(eq(lpn), eq(SorterExceptionReason.OVERAGE), any(Date.class));
  }
}
