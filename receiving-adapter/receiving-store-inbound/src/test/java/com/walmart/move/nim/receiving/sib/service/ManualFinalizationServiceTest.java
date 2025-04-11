package com.walmart.move.nim.receiving.sib.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.helper.ProcessInitiator;
import com.walmart.move.nim.receiving.core.message.common.ReceivingEvent;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.core.utils.CoreUtil;
import com.walmart.move.nim.receiving.sib.config.SIBManagedConfig;
import com.walmart.move.nim.receiving.sib.entity.Event;
import com.walmart.move.nim.receiving.sib.repositories.EventRepository;
import com.walmart.move.nim.receiving.sib.utils.EventType;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ManualFinalizationServiceTest {

  @InjectMocks private ManualFinalizationService manualFinalizationService;

  @Mock private StoreDeliveryService storeDeliveryService;

  @Mock private EventRepository eventRepository;

  @Mock private ProcessInitiator processInitiator;

  @Mock private SIBManagedConfig sibManagedConfig;

  @Mock private HttpHeaders headers;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(266);
  }

  @AfterMethod
  public void resetMocks() {
    Mockito.reset(storeDeliveryService);
    Mockito.reset(eventRepository);
    Mockito.reset(processInitiator);
    Mockito.reset(sibManagedConfig);
  }

  @Test
  public void testManualFinalize_UnloadEnabled() {
    long deliveryNumber = 55047860;
    String doorNumber = "A";
    HttpHeaders headers = new HttpHeaders();
    headers.add("Authorization", "Bearer token");

    when(storeDeliveryService.unloadComplete(anyLong(), anyString(), anyString(), any()))
        .thenReturn(getDeliveryInfo());

    manualFinalizationService.manualFinalize(deliveryNumber, doorNumber, true, headers);

    verify(storeDeliveryService)
        .unloadComplete(
            deliveryNumber, doorNumber, ReceivingConstants.MANUAL_FINALISE_DELIVERY, headers);
    verify(eventRepository)
        .findByDeliveryNumberAndEventType(deliveryNumber, EventType.MANUAL_FINALIZATION);
    verify(eventRepository).save(any(Event.class));
    verifyNoMoreInteractions(processInitiator);
  }

  @Test
  public void testManualFinalize_UnloadDisabled() {
    long deliveryNumber = 55047860;
    String doorNumber = "AN671";
    HttpHeaders headers = new HttpHeaders();
    headers.put(ReceivingConstants.TENENT_FACLITYNUM, Collections.singletonList("5505"));
    headers.put(ReceivingConstants.TENENT_COUNTRY_CODE, Collections.singletonList("US"));
    headers.put(ReceivingConstants.EVENT_TYPE, Collections.singletonList("MANUAL_FINALIZATION"));

    manualFinalizationService.manualFinalize(deliveryNumber, doorNumber, false, headers);

    verify(eventRepository)
        .findByDeliveryNumberAndEventType(deliveryNumber, EventType.MANUAL_FINALIZATION);
    verify(eventRepository).save(any(Event.class));
    verifyNoInteractions(storeDeliveryService);
    verifyNoMoreInteractions(processInitiator);
  }

  @Test
  public void testManualFinalize_WithNGRServiceEnabled() {
    long deliveryNumber = 55047860;
    HttpHeaders headers = new HttpHeaders();
    headers.put(ReceivingConstants.TENENT_FACLITYNUM, Collections.singletonList("5505"));
    headers.put(ReceivingConstants.TENENT_COUNTRY_CODE, Collections.singletonList("US"));
    headers.put(ReceivingConstants.EVENT_TYPE, Collections.singletonList("MANUAL_FINALIZATION"));

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    doNothing().when(processInitiator).initiateProcess(any(), anyMap());

    manualFinalizationService.manualFinalize(deliveryNumber, "A", true, headers);

    verify(processInitiator).initiateProcess(any(ReceivingEvent.class), anyMap());
    verify(eventRepository)
        .findByDeliveryNumberAndEventType(deliveryNumber, EventType.MANUAL_FINALIZATION);
    verify(eventRepository).save(any(Event.class));
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testManualFinalize_WithDuplicateEvent() {
    long deliveryNumber = 55047860;
    HttpHeaders headers = new HttpHeaders();
    headers.put(ReceivingConstants.TENENT_FACLITYNUM, Collections.singletonList("5505"));
    headers.put(ReceivingConstants.TENENT_COUNTRY_CODE, Collections.singletonList("US"));
    headers.put(ReceivingConstants.EVENT_TYPE, Collections.singletonList("MANUAL_FINALIZATION"));

    when(eventRepository.findByDeliveryNumberAndEventType(
            deliveryNumber, EventType.MANUAL_FINALIZATION))
        .thenReturn(getEventObject());

    manualFinalizationService.manualFinalize(deliveryNumber, "A", true, headers);
  }

  private Event getEventObject() {
    String deliveryNo = "550478600078407";

    Date updatedPickupTime = CoreUtil.addMinutesToJavaUtilDate(new Date(), 20);

    Event containerEvent = new Event();
    containerEvent.setKey(deliveryNo);
    containerEvent.setDeliveryNumber(Long.valueOf(deliveryNo));
    containerEvent.setEventType(EventType.MANUAL_FINALIZATION);
    containerEvent.setPayload(EventType.MANUAL_FINALIZATION.name());
    containerEvent.setRetryCount(0);
    containerEvent.setStatus(EventTargetStatus.PENDING);
    containerEvent.setPickUpTime(updatedPickupTime);
    containerEvent.setFacilityNum(TenantContext.getFacilityNum());
    containerEvent.setFacilityCountryCode(TenantContext.getFacilityCountryCode());
    containerEvent.setMetaData(new HashMap<>());
    containerEvent.setAdditionalInfo(new HashMap<>());
    return containerEvent;
  }

  private DeliveryInfo getDeliveryInfo() {
    DeliveryInfo deliveryInfo = new DeliveryInfo();
    deliveryInfo.setDeliveryNumber(Long.valueOf("550478600078407"));
    return deliveryInfo;
  }
}
