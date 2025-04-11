package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.walmart.move.nim.receiving.core.entity.EventStore;
import com.walmart.move.nim.receiving.core.repositories.EventStoreRepository;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.EventStoreType;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import java.util.Date;
import java.util.Optional;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class EventStoreServiceTest {

  @Mock private EventStoreRepository eventStoreRepository;
  @InjectMocks private EventStoreService eventStoreService;

  @BeforeClass
  public void setup() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32679);
  }

  @AfterClass
  public void cleanUp() {
    //        reset(eventStoreRepository);
  }

  @Test
  public void test_getEventStoreByKeyStatusAndEventType() {
    EventStore eventStore = getEventStoreData();
    when(eventStoreRepository.findByFacilityNumAndEventStoreKeyAndStatusAndEventStoreType(
            anyInt(), anyString(), any(EventTargetStatus.class), any(EventStoreType.class)))
        .thenReturn(Optional.of(eventStore));
    eventStoreService.getEventStoreByKeyStatusAndEventType(
        eventStore.getEventStoreKey(), eventStore.getStatus(), eventStore.getEventStoreType());
  }

  @Test
  public void test_saveEventStoreEntity() {
    EventStore eventStore = getEventStoreData();
    eventStoreService.saveEventStoreEntity(eventStore);
    verify(eventStoreRepository, times(1)).save(any());
  }

  @Test
  public void test_updateEventStoreEntity() {
    eventStoreService.updateEventStoreEntityStatusAndLastUpdatedDateByKeyOrDeliveryNumber(
        "200", 1234L, EventTargetStatus.DELETE, EventStoreType.DOOR_ASSIGNMENT, new Date());
    verify(eventStoreRepository, times(1))
        .updateDeliveryStatusAndLastUpdatedDateByFacilityNumberAndKey(
            any(EventTargetStatus.class),
            any(Date.class),
            any(),
            anyString(),
            anyLong(),
            any(EventStoreType.class));
  }

  @Test
  public void test_updateEventStoreEntityStatusAndLastUpdatedDateByCriteria() {
    EventStore eventStore = getEventStoreData();
    when(eventStoreRepository.updateDeliveryStatusAndLastUpdatedDateByFacilityNumber(
            any(EventTargetStatus.class),
            any(),
            anyInt(),
            anyString(),
            anyLong(),
            any(EventStoreType.class)))
        .thenReturn(1);
    eventStoreService.updateEventStoreEntityStatusAndLastUpdatedDateByCriteria(
        eventStore.getEventStoreKey(),
        100L,
        eventStore.getStatus(),
        eventStore.getEventStoreType(),
        new Date());
    when(eventStoreRepository.updateDeliveryStatusAndLastUpdatedDateByFacilityNumber(
            any(EventTargetStatus.class),
            any(),
            anyInt(),
            anyString(),
            anyLong(),
            any(EventStoreType.class)))
        .thenReturn(1);
    eventStoreService.updateEventStoreEntityStatusAndLastUpdatedDateByCriteria(
        eventStore.getEventStoreKey(),
        100L,
        eventStore.getStatus(),
        eventStore.getEventStoreType(),
        new Date());
  }

  private static EventStore getEventStoreData() {
    EventStore eventStore = new EventStore();
    eventStore.setId(10L);
    eventStore.setEventStoreKey("200");
    eventStore.setFacilityCountryCode("US");
    eventStore.setFacilityNum(7552);
    eventStore.setDeliveryNumber(1234L);
    eventStore.setContainerId("");
    eventStore.setStatus(EventTargetStatus.PENDING);
    eventStore.setEventStoreType(EventStoreType.DOOR_ASSIGNMENT);
    eventStore.setPayload("");
    eventStore.setRetryCount(0);
    eventStore.setCreatedDate(new Date());
    eventStore.setLastUpdatedDate(new Date());
    return eventStore;
  }
}
