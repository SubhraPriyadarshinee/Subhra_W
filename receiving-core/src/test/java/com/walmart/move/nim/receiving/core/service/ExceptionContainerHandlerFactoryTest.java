package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.service.exceptioncontainer.ChannelFlipExceptionContainerHandler;
import com.walmart.move.nim.receiving.core.service.exceptioncontainer.DockTagExceptionContainerHandler;
import com.walmart.move.nim.receiving.core.service.exceptioncontainer.ExceptionContainerHandler;
import com.walmart.move.nim.receiving.core.service.exceptioncontainer.ExceptionContainerHandlerFactory;
import com.walmart.move.nim.receiving.core.service.exceptioncontainer.NoAllocationExceptionContainerHandler;
import com.walmart.move.nim.receiving.core.service.exceptioncontainer.OverageExceptionContainerHandler;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ContainerException;
import java.util.UUID;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationContext;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ExceptionContainerHandlerFactoryTest extends ReceivingTestBase {
  @InjectMocks ExceptionContainerHandlerFactory exceptionContainerHandlerFactory;
  @Mock ApplicationContext applicationContext;
  @Mock OverageExceptionContainerHandler overageExceptionContainerHandler;
  @Mock NoAllocationExceptionContainerHandler noAllocationExceptionContainerHandler;
  @Mock DockTagExceptionContainerHandler dockTagExceptionContainerHandler;
  @Mock ChannelFlipExceptionContainerHandler channelFlipExceptionContainerHandler;

  @BeforeClass
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32987);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setCorrelationId(UUID.randomUUID().toString());

    when(applicationContext.getBean(eq(OverageExceptionContainerHandler.class)))
        .thenReturn(overageExceptionContainerHandler);
    when(applicationContext.getBean(eq(NoAllocationExceptionContainerHandler.class)))
        .thenReturn(noAllocationExceptionContainerHandler);
    when(applicationContext.getBean(eq(DockTagExceptionContainerHandler.class)))
        .thenReturn(dockTagExceptionContainerHandler);
    when(applicationContext.getBean(eq(ChannelFlipExceptionContainerHandler.class)))
        .thenReturn(channelFlipExceptionContainerHandler);
  }

  @Test
  public void testExceptionHandler_Overage() {
    ExceptionContainerHandler exceptionContainerHandler =
        exceptionContainerHandlerFactory.exceptionContainerHandler(ContainerException.OVERAGE);
    assertEquals(exceptionContainerHandler, overageExceptionContainerHandler);
  }

  @Test
  public void testExceptionHandler_NoAllocation() {
    ExceptionContainerHandler exceptionContainerHandler =
        exceptionContainerHandlerFactory.exceptionContainerHandler(
            ContainerException.NO_ALLOCATION_FOUND);
    assertEquals(exceptionContainerHandler, noAllocationExceptionContainerHandler);
  }

  @Test
  public void testExceptionHandler_DockTag() {
    ExceptionContainerHandler exceptionContainerHandler =
        exceptionContainerHandlerFactory.exceptionContainerHandler(ContainerException.DOCK_TAG);
    assertEquals(exceptionContainerHandler, dockTagExceptionContainerHandler);
  }

  @Test
  public void testExceptionHandler_ChannelFlip() {
    ExceptionContainerHandler exceptionContainerHandler =
        exceptionContainerHandlerFactory.exceptionContainerHandler(ContainerException.CHANNEL_FLIP);
    assertEquals(exceptionContainerHandler, channelFlipExceptionContainerHandler);
  }
}
