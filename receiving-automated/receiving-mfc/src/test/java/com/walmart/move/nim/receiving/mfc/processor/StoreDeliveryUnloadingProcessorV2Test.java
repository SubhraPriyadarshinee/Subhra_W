package com.walmart.move.nim.receiving.mfc.processor;

import static org.mockito.Mockito.*;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.helper.ProcessInitiator;
import com.walmart.move.nim.receiving.core.message.common.ReceivingEvent;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.UUID;
import org.mockito.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class StoreDeliveryUnloadingProcessorV2Test extends ReceivingTestBase {

  @InjectMocks private StoreDeliveryUnloadingProcessorV2 storeDeliveryUnloadingProcessorV2;

  @Mock private ProcessInitiator processInitiator;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setCorrelationId(UUID.randomUUID().toString());
    TenantContext.setFacilityNum(5504);
    TenantContext.setFacilityCountryCode("US");
  }

  @AfterMethod
  public void resetMocks() {
    Mockito.reset(processInitiator);
  }

  @Test
  public void testDoProcess() {
    DeliveryInfo deliveryInfo = new DeliveryInfo();
    deliveryInfo.setDeliveryNumber(10000001L);

    doNothing().when(processInitiator).initiateProcess(any(), any());

    storeDeliveryUnloadingProcessorV2.doProcess(deliveryInfo);
    ArgumentCaptor<ReceivingEvent> receivingEventArgumentCaptor =
        ArgumentCaptor.forClass(ReceivingEvent.class);
    verify(processInitiator, times(1))
        .initiateProcess(receivingEventArgumentCaptor.capture(), any());
  }
}
