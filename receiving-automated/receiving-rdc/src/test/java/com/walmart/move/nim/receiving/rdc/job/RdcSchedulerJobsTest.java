package com.walmart.move.nim.receiving.rdc.job;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.ScheduleConfig;
import com.walmart.move.nim.receiving.core.entity.DeliveryEvent;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.service.DeliveryEventPersisterService;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.service.RdcLabelGenerationService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RdcSchedulerJobsTest {
  @InjectMocks RdcSchedulerJobs rdcSchedulerJobs;
  @Mock private RdcManagedConfig rdcManagedConfig;
  @Mock private ScheduleConfig scheduleConfig;
  @Mock private DeliveryEventPersisterService deliveryEventPersisterService;
  @Mock private RdcLabelGenerationService rdcLabelGenerationService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  private String facilityNum = "32679";

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    Set<Pair<String, Integer>> tenantConfigSet = new HashSet<>();
    tenantConfigSet.add(new Pair<>("US", 32679));
    when(scheduleConfig.getRdcLabelGenerationSpec()).thenReturn("RdcLabelGenerationSpec");
    when(scheduleConfig.getSchedulerTenantConfig(anyString())).thenReturn(tenantConfigSet);
  }

  @AfterMethod
  public void tearDown() {
    reset(deliveryEventPersisterService);
    reset(rdcLabelGenerationService);
  }

  @Test
  public void testPreLabelGenerationScheduler_DoorAssign() {
    List<DeliveryEvent> deliveryEvents = new ArrayList<>();
    DeliveryEvent doorAssignDeliveryEvent =
        getDeliveryEvent(ReceivingConstants.EVENT_DOOR_ASSIGNED);
    DeliveryEvent poAddedDeliveryEvent = getDeliveryEvent(ReceivingConstants.EVENT_PO_ADDED);
    DeliveryEvent poUpdatedDeliveryEvent = getDeliveryEvent(ReceivingConstants.EVENT_PO_UPDATED);
    deliveryEvents.add(doorAssignDeliveryEvent);
    deliveryEvents.add(poAddedDeliveryEvent);
    deliveryEvents.add(poUpdatedDeliveryEvent);
    doReturn(Arrays.asList("32679"))
        .when(rdcManagedConfig)
        .getFlibSstkPregenSchedulerEnabledSites();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    when(deliveryEventPersisterService.getDeliveryForRdcScheduler(anyInt()))
        .thenReturn(doorAssignDeliveryEvent);
    when(deliveryEventPersisterService.getDeliveryEventsForScheduler(anyLong(), any(), anyInt()))
        .thenReturn(deliveryEvents);
    when(rdcLabelGenerationService.processDeliveryEventForScheduler(any(DeliveryEvent.class)))
        .thenReturn(true);
    rdcSchedulerJobs.preLabelGenerationScheduler();
    verify(deliveryEventPersisterService, times(1))
        .markAndSaveDeliveryEvents(anyList(), eq(EventTargetStatus.DELETE));
    verify(rdcLabelGenerationService, times(2))
        .processDeliveryEventForScheduler(any(DeliveryEvent.class));
  }

  @Test
  public void testPreLabelGenerationScheduler_OtherEvents() {
    DeliveryEvent poLineAddedDeliveryEvent =
        getDeliveryEvent(ReceivingConstants.EVENT_PO_LINE_ADDED);
    poLineAddedDeliveryEvent.setEventStatus(EventTargetStatus.DELETE);
    doReturn(Arrays.asList("32679"))
        .when(rdcManagedConfig)
        .getFlibSstkPregenSchedulerEnabledSites();
    when(deliveryEventPersisterService.getDeliveryForRdcScheduler(anyInt()))
        .thenReturn(poLineAddedDeliveryEvent);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    when(deliveryEventPersisterService.getDeliveryEventsForScheduler(anyLong(), any(), anyInt()))
        .thenReturn(Collections.singletonList(getDeliveryEvent(ReceivingConstants.EVENT_PO_ADDED)));
    rdcSchedulerJobs.preLabelGenerationScheduler();
    verify(deliveryEventPersisterService, times(0))
        .markAndSaveDeliveryEvents(anyList(), eq(EventTargetStatus.DELETE));
    verify(rdcLabelGenerationService, times(1))
        .processDeliveryEventForScheduler(any(DeliveryEvent.class));
  }

  @Test
  public void testPreLabelGenerationScheduler_LabelGenerationFail() {
    List<DeliveryEvent> deliveryEvents = new ArrayList<>();
    DeliveryEvent doorAssignDeliveryEvent =
        getDeliveryEvent(ReceivingConstants.EVENT_DOOR_ASSIGNED);
    doReturn(Arrays.asList("32679"))
        .when(rdcManagedConfig)
        .getFlibSstkPregenSchedulerEnabledSites();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    deliveryEvents.add(doorAssignDeliveryEvent);
    when(deliveryEventPersisterService.getDeliveryForRdcScheduler(anyInt()))
        .thenReturn(doorAssignDeliveryEvent);
    when(deliveryEventPersisterService.getDeliveryEventsForScheduler(anyLong(), any(), anyInt()))
        .thenReturn(deliveryEvents);
    when(rdcLabelGenerationService.processDeliveryEventForScheduler(any(DeliveryEvent.class)))
        .thenReturn(false);
    rdcSchedulerJobs.preLabelGenerationScheduler();
    verify(deliveryEventPersisterService, times(0))
        .markAndSaveDeliveryEvents(anyList(), eq(EventTargetStatus.DELETE));
    verify(rdcLabelGenerationService, times(1))
        .processDeliveryEventForScheduler(any(DeliveryEvent.class));
  }

  @Test
  public void testPreLabelGenerationScheduler_Exception() {
    DeliveryEvent doorAssignDeliveryEvent =
        getDeliveryEvent(ReceivingConstants.EVENT_DOOR_ASSIGNED);
    doorAssignDeliveryEvent.setEventStatus(EventTargetStatus.DELETE);
    when(deliveryEventPersisterService.getDeliveryForRdcScheduler(anyInt()))
        .thenReturn(doorAssignDeliveryEvent);
    doCallRealMethod().when(rdcLabelGenerationService).processDeliveryEventForScheduler(any());
    when(deliveryEventPersisterService.getDeliveryEventsForScheduler(anyLong(), any(), anyInt()))
        .thenReturn(Collections.singletonList(getDeliveryEvent(ReceivingConstants.EVENT_PO_ADDED)));
    rdcSchedulerJobs.preLabelGenerationScheduler();
    verify(deliveryEventPersisterService, times(0)).markAndSaveDeliveryEvents(anyList(), any());
    verify(rdcLabelGenerationService, times(1))
        .processDeliveryEventForScheduler(any(DeliveryEvent.class));
  }

  private DeliveryEvent getDeliveryEvent(String eventType) {
    DeliveryEvent deliveryEvent =
        DeliveryEvent.builder()
            .id(1)
            .eventType(eventType)
            .deliveryNumber(123456L)
            .url("https://delivery.test")
            .retriesCount(0)
            .eventStatus(EventTargetStatus.PENDING)
            .build();
    deliveryEvent.setFacilityCountryCode("US");
    deliveryEvent.setFacilityNum(32818);
    return deliveryEvent;
  }
}
