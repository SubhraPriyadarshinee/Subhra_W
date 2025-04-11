package com.walmart.move.nim.receiving.core.common;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertNull;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.message.publisher.DefaultDeliveryMessagePublisher;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.core.service.DockTagService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.HashMap;
import java.util.Map;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DeliveryStatusPublisherTest extends ReceivingTestBase {

  @Spy private DeliveryStatusPublisher deliveryStatusPublisher;

  @Mock private DefaultDeliveryMessagePublisher defaultDeliveryMessagePublisher;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Mock private DockTagService dockTagService;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32987);
  }

  @AfterMethod
  public void tearDown() {
    reset(deliveryStatusPublisher);
    reset(tenantSpecificConfigReader);
    reset(dockTagService);
  }

  @Test
  public void testPublishDeliveryStatus() {
    ReflectionTestUtils.setField(
        deliveryStatusPublisher, "tenantSpecificConfigReader", tenantSpecificConfigReader);

    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), any(Class.class)))
        .thenReturn(defaultDeliveryMessagePublisher);

    Map<String, Object> headers = new HashMap<>();
    headers.put(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    deliveryStatusPublisher.publishDeliveryStatus(
        1l, DeliveryStatus.OPEN.toString(), null, headers);

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
  }

  @Test
  public void testPublishDeliveryStatus_CompleteAndOpenDockTagsNull() throws ReceivingException {
    ReflectionTestUtils.setField(
        deliveryStatusPublisher, "tenantSpecificConfigReader", tenantSpecificConfigReader);

    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_STATUS_PUBLISHER), any()))
        .thenReturn(defaultDeliveryMessagePublisher);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DOCK_TAG_SERVICE), any()))
        .thenReturn(dockTagService);
    when(dockTagService.countOfOpenDockTags(anyLong())).thenReturn(null);

    Map<String, Object> headers = new HashMap<>();
    headers.put(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    headers.put(ReceivingConstants.TENENT_FACLITYNUM, 32987);
    DeliveryInfo deliveryInfo =
        deliveryStatusPublisher.publishDeliveryStatus(
            1l, DeliveryStatus.COMPLETE.toString(), null, headers);
    assertNull(deliveryInfo.getOpenDockTags());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DELIVERY_STATUS_PUBLISHER), any());
  }
}
