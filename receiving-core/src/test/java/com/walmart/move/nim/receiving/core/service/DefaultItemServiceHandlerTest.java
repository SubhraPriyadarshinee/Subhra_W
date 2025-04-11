package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.model.ItemOverrideRequest;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DefaultItemServiceHandlerTest extends ReceivingTestBase {
  @InjectMocks DefaultItemServiceHandler defaultItemServiceHandler;
  @Mock private DeliveryItemOverrideService deliveryItemOverrideService;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  public void resetMocks() {
    reset(deliveryItemOverrideService);
  }

  @Test
  public void testUpdateItemProperties_Success() {
    doNothing()
        .when(deliveryItemOverrideService)
        .updateDeliveryItemOverride(any(ItemOverrideRequest.class), any(HttpHeaders.class));
    ItemOverrideRequest itemOverrideRequest =
        new ItemOverrideRequest(232L, 32432L, "B", "C", "B", "C", "234234", 1, false);

    defaultItemServiceHandler.updateItemProperties(
        itemOverrideRequest, MockHttpHeaders.getHeaders());
    verify(deliveryItemOverrideService, times(1))
        .updateDeliveryItemOverride(itemOverrideRequest, MockHttpHeaders.getHeaders());
  }
}
