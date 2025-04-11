package com.walmart.move.nim.receiving.core.message.listener.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.InventoryAdjustmentTO;
import com.walmart.move.nim.receiving.core.service.DefaultKafkaInventoryEventProcessor;
import com.walmart.move.nim.receiving.data.MockInventoryAdjustmentEvent;
import com.walmart.move.nim.receiving.data.MockMessageHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Collections;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class SecureKafkaInventoryAdjustmentListenerTest {

  @InjectMocks
  private SecureKafkaInventoryAdjustmentListener secureKafkaInventoryAdjustmentListener;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private DefaultKafkaInventoryEventProcessor defaultKafkaInventoryEventProcessor;
  @Mock private AppConfig appConfig;
  private Gson gson = new Gson();

  private static final String facilityNum = "32818";
  private static final String countryCode = "US";
  private static final String requestOriginator = "inventory-api";

  @BeforeMethod
  public void setup() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode(countryCode);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    ReflectionTestUtils.setField(secureKafkaInventoryAdjustmentListener, "gson", gson);
    when(tenantSpecificConfigReader.getConfiguredInstance(anyString(), anyString(), any()))
        .thenReturn(defaultKafkaInventoryEventProcessor);
  }

  @AfterMethod
  public void cleanup() {
    reset(tenantSpecificConfigReader, defaultKafkaInventoryEventProcessor, appConfig);
  }

  @Test
  public void testKafkaInventoryAdjustmentListenerIsSuccessForValidAdjustmentMessage()
      throws ReceivingException {
    when(appConfig.getKafkaInventoryAdjustmentListenerEnabledFacilities())
        .thenReturn(Collections.singletonList(32818));

    doNothing().when(defaultKafkaInventoryEventProcessor).processEvent(any());

    secureKafkaInventoryAdjustmentListener.listen(
        MockInventoryAdjustmentEvent.VALID_VTR_EVENT,
        MockMessageHeaders.getMockKafkaListenerHeaders());

    verify(appConfig, times(1)).getKafkaInventoryAdjustmentListenerEnabledFacilities();
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(anyString(), anyString(), any());
    verify(defaultKafkaInventoryEventProcessor, times(1)).processEvent(any());
  }

  @Test
  public void testKafkaInventoryAdjustmentListenerWithEmptyMessage() {
    when(appConfig.getKafkaInventoryAdjustmentListenerEnabledFacilities())
        .thenReturn(Collections.singletonList(32818));

    secureKafkaInventoryAdjustmentListener.listen(
        null, MockMessageHeaders.getMockKafkaListenerHeaders());

    verify(appConfig, times(1)).getKafkaInventoryAdjustmentListenerEnabledFacilities();
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredInstance(anyString(), anyString(), any());
  }

  @Test
  public void testKafkaInventoryAdjustmentListenerIsNotProcessedForNonRdcFacilities()
      throws ReceivingException {
    when(appConfig.getKafkaInventoryAdjustmentListenerEnabledFacilities())
        .thenReturn(Collections.singletonList(32944));
    doNothing().when(defaultKafkaInventoryEventProcessor).processEvent(any());

    secureKafkaInventoryAdjustmentListener.listen(
        MockInventoryAdjustmentEvent.VALID_VTR_EVENT,
        MockMessageHeaders.getMockKafkaListenerHeaders());

    verify(appConfig, times(1)).getKafkaInventoryAdjustmentListenerEnabledFacilities();
    verify(tenantSpecificConfigReader, times(0))
        .getConfiguredInstance(anyString(), anyString(), any());
    verify(defaultKafkaInventoryEventProcessor, times(0)).processEvent(any());
  }

  @Test
  public void testKafkaInventoryAdjustmentListenerHeadersForValidAdjustmentMessage()
      throws ReceivingException {
    when(appConfig.getKafkaInventoryAdjustmentListenerEnabledFacilities())
        .thenReturn(Collections.singletonList(32818));
    doNothing().when(defaultKafkaInventoryEventProcessor).processEvent(any());

    secureKafkaInventoryAdjustmentListener.listen(
        MockInventoryAdjustmentEvent.RDC_VALID_RECEIVING_CORRECTION_EVENT,
        MockMessageHeaders.getMockKafkaListenerHeaders());
    ArgumentCaptor<InventoryAdjustmentTO> captor =
        ArgumentCaptor.forClass(InventoryAdjustmentTO.class);

    verify(appConfig, times(1)).getKafkaInventoryAdjustmentListenerEnabledFacilities();
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(anyString(), anyString(), any());
    verify(defaultKafkaInventoryEventProcessor, times(1)).processEvent(captor.capture());
    assertEquals(
        ReceivingConstants.INVENTORY_EVENT_MOVED,
        captor.getValue().getHttpHeaders().getFirst(ReceivingConstants.INVENTORY_EVENT));
    assertEquals(
        requestOriginator,
        captor.getValue().getHttpHeaders().getFirst(ReceivingConstants.REQUEST_ORIGINATOR));
  }
}
