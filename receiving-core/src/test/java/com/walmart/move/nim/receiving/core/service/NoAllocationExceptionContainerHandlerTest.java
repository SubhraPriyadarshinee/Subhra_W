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
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.mock.data.MockContainer;
import com.walmart.move.nim.receiving.core.service.exceptioncontainer.NoAllocationExceptionContainerHandler;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.SorterExceptionReason;
import java.util.Date;
import java.util.UUID;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class NoAllocationExceptionContainerHandlerTest extends ReceivingTestBase {
  @InjectMocks NoAllocationExceptionContainerHandler exceptionContainerHandler;
  @Mock TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock SorterPublisher sorterPublisher;
  @Mock ContainerService containerService;

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
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ReceivingConstants.ENABLE_NA_SORTER_DIVERT)))
        .thenReturn(Boolean.TRUE);
  }

  @Test
  public void testPublishExceptionDivertToSorter() {
    Container mockContainer = MockContainer.getContainer();
    exceptionContainerHandler.publishException(mockContainer);
    verify(containerService, times(1))
        .publishExceptionContainer(eq(mockContainer), any(HttpHeaders.class), eq(Boolean.TRUE));
    verify(sorterPublisher, times(1))
        .publishException(
            eq(mockContainer.getTrackingId()),
            eq(SorterExceptionReason.NO_ALLOCATION),
            any(Date.class));
  }
}
