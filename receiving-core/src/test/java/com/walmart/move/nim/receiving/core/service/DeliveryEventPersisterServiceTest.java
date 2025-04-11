package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.entity.DeliveryEvent;
import com.walmart.move.nim.receiving.core.entity.PurgeData;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.repositories.DeliveryEventRepository;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.PurgeEntityType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.mockito.AdditionalAnswers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageRequest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class DeliveryEventPersisterServiceTest extends ReceivingTestBase {
  @InjectMocks private DeliveryEventPersisterService deliveryEventPersisterService;

  @Mock private DeliveryEventRepository deliveryEventRepository;

  private DeliveryEvent deliveryEvent;

  private DeliveryUpdateMessage deliveryUpdateMessage;

  private List<DeliveryEvent> deliveryEventList;

  private PageRequest pageReq;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    deliveryEvent =
        DeliveryEvent.builder()
            .id(1)
            .eventStatus(EventTargetStatus.PENDING)
            .eventType(ReceivingConstants.EVENT_DOOR_ASSIGNED)
            .deliveryNumber(12345L)
            .url("https://delivery.test")
            .retriesCount(0)
            .build();
    deliveryUpdateMessage =
        DeliveryUpdateMessage.builder()
            .deliveryNumber("12345")
            .countryCode("US")
            .siteNumber("6051")
            .deliveryStatus("ARV")
            .url("https://delivery.test")
            .build();
    deliveryEventList = new ArrayList<>();
    deliveryEventList.add(deliveryEvent);
    pageReq = PageRequest.of(0, 10);
    TenantContext.setFacilityNum(Integer.valueOf("32679"));
    TenantContext.setFacilityCountryCode("US");
  }

  @BeforeMethod
  public void beforeMethod() {
    reset(deliveryEventRepository);
  }

  @Test
  public void testSave() {
    when(deliveryEventRepository.save(any(DeliveryEvent.class))).thenReturn(deliveryEvent);
    DeliveryEvent response = deliveryEventPersisterService.save(deliveryEvent);
    assertEquals(response, deliveryEvent);
    verify(deliveryEventRepository, times(1)).save(any(DeliveryEvent.class));
  }

  @Test
  public void
      testGetRdcDeliveryEventToProcess_whenPreLabelGenFallBack_shouldMarkDeleteOthersAndReturnPreLabelGenFallBackEventInProgress() {
    deliveryEventList.get(0).setEventStatus(EventTargetStatus.EVENT_PENDING);
    when(deliveryEventRepository.findByDeliveryNumber(12345L)).thenReturn(deliveryEventList);
    when(deliveryEventRepository.saveAll(anyList()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(deliveryEventRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    deliveryUpdateMessage.setEventType(ReceivingConstants.PRE_LABEL_GEN_FALLBACK);
    DeliveryEvent returnedDeliveryEvent =
        deliveryEventPersisterService.getRdcDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventRepository, times(1)).save(any(DeliveryEvent.class));
    assertEquals(returnedDeliveryEvent.getEventStatus(), EventTargetStatus.EVENT_IN_PROGRESS);
    assertEquals(returnedDeliveryEvent.getEventType(), ReceivingConstants.PRE_LABEL_GEN_FALLBACK);
    assertEquals(deliveryEventList.get(0).getEventStatus(), EventTargetStatus.DELETE);
  }

  @Test
  public void
      testGetRdcDeliveryEventToProcess_whenAnyEventOtherThanPreLabelComesBeforeDoorAssign_shouldReturnNull() {
    doReturn(new ArrayList<>()).when(deliveryEventRepository).findByDeliveryNumber(12345L);
    when(deliveryEventRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_PO_ADDED);
    DeliveryEvent returnedDeliveryEvent =
        deliveryEventPersisterService.getRdcDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventRepository, times(0)).save(any(DeliveryEvent.class));
    assertEquals(returnedDeliveryEvent, null);
  }

  @Test
  public void
      testGetRdcDeliveryEventToProcess_whenDoorAssignedEventComesForFirstTime_shouldReturnDoorAssignInProgress() {
    doReturn(new ArrayList<>()).when(deliveryEventRepository).findByDeliveryNumber(12345L);
    when(deliveryEventRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_DOOR_ASSIGNED);
    DeliveryEvent returnedDeliveryEvent =
        deliveryEventPersisterService.getRdcDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventRepository, times(1)).save(any(DeliveryEvent.class));
    assertEquals(returnedDeliveryEvent.getEventStatus(), EventTargetStatus.EVENT_IN_PROGRESS);
    assertEquals(returnedDeliveryEvent.getEventType(), ReceivingConstants.EVENT_DOOR_ASSIGNED);
  }

  @Test
  public void
      testGetRdcDeliveryEventToProcess_whenDoorAssignedEventComesForSecondTime_shouldReturnNull() {
    deliveryEventList.get(0).setEventStatus(EventTargetStatus.EVENT_PENDING);
    when(deliveryEventRepository.findByDeliveryNumber(12345L)).thenReturn(deliveryEventList);
    when(deliveryEventRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_DOOR_ASSIGNED);
    DeliveryEvent returnedDeliveryEvent =
        deliveryEventPersisterService.getRdcDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventRepository, times(0)).save(any(DeliveryEvent.class));
    assertEquals(returnedDeliveryEvent, null);
  }

  @Test
  public void
      testGetRdcDeliveryEventToProcess_whenDoorAssignedEventComesAfterFallback_shouldReturnNull() {
    deliveryEventList.get(0).setEventType(ReceivingConstants.PRE_LABEL_GEN_FALLBACK);
    when(deliveryEventRepository.findByDeliveryNumber(12345L)).thenReturn(deliveryEventList);
    when(deliveryEventRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_DOOR_ASSIGNED);
    DeliveryEvent returnedDeliveryEvent =
        deliveryEventPersisterService.getRdcDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventRepository, times(0)).save(any(DeliveryEvent.class));
    assertNull(returnedDeliveryEvent);
  }

  @Test
  public void
      testGetRdcDeliveryEventToProcess_whenAnyOtherEventComesAfterFallbackInProgress_shouldSaveEventInPendingAndReturnNull() {
    deliveryEventList.get(0).setEventType(ReceivingConstants.PRE_LABEL_GEN_FALLBACK);
    deliveryEventList.get(0).setEventStatus(EventTargetStatus.EVENT_PENDING);
    when(deliveryEventRepository.findByDeliveryNumber(12345L)).thenReturn(deliveryEventList);
    when(deliveryEventRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_PO_UPDATED);
    DeliveryEvent returnedDeliveryEvent =
        deliveryEventPersisterService.getRdcDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventRepository, times(1)).save(any(DeliveryEvent.class));
    assertNull(returnedDeliveryEvent);
  }

  @Test
  public void
      testGetRdcDeliveryEventToProcess_whenAnyOtherEventComesAfterFallbackCompleted_shouldReturnEventInProgress() {
    deliveryEventList.get(0).setEventType(ReceivingConstants.PRE_LABEL_GEN_FALLBACK);
    deliveryEventList.get(0).setEventStatus(EventTargetStatus.DELETE);
    when(deliveryEventRepository.findByDeliveryNumber(12345L)).thenReturn(deliveryEventList);
    when(deliveryEventRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_PO_UPDATED);
    DeliveryEvent returnedDeliveryEvent =
        deliveryEventPersisterService.getRdcDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventRepository, times(1)).save(any(DeliveryEvent.class));
    assertEquals(returnedDeliveryEvent.getEventStatus(), EventTargetStatus.EVENT_IN_PROGRESS);
    assertEquals(returnedDeliveryEvent.getEventType(), ReceivingConstants.EVENT_PO_UPDATED);
  }

  @Test
  public void
      testGetRdcDeliveryEventToProcess_whenAnyEventOtherThanPreLabelAndDoorAssignComesAndDoorAssignInPending_shouldSaveEventInPendingAndReturnNull() {
    deliveryEventList.get(0).setEventStatus(EventTargetStatus.EVENT_PENDING);
    when(deliveryEventRepository.findByDeliveryNumber(12345L)).thenReturn(deliveryEventList);
    when(deliveryEventRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_PO_UPDATED);
    DeliveryEvent returnedDeliveryEvent =
        deliveryEventPersisterService.getRdcDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventRepository, times(1)).save(any(DeliveryEvent.class));
    assertNull(returnedDeliveryEvent);
  }

  @Test
  public void
      testGetRdcDeliveryEventToProcess_whenAnyEventOtherThanPreLabelAndDoorAssignComesAfterDoorAssignAndSomethingInPending_shouldSaveEventInPendingAndReturnNull() {
    deliveryEventList.get(0).setEventStatus(EventTargetStatus.EVENT_PENDING);
    when(deliveryEventRepository.findByDeliveryNumber(12345L)).thenReturn(deliveryEventList);
    when(deliveryEventRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_PO_LINE_ADDED);
    DeliveryEvent returnedDeliveryEvent =
        deliveryEventPersisterService.getRdcDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventRepository, times(1)).save(any(DeliveryEvent.class));
    assertNull(returnedDeliveryEvent);
  }

  @Test
  public void
      testGetRdcDeliveryEventToProcess_whenAnyEventOtherThanPreLabelAndDoorAssignComesAfterDoorAssignAndSomethingInProgress_shouldSaveEventInPendingAndReturnNull() {
    deliveryEventList.get(0).setEventStatus(EventTargetStatus.EVENT_IN_PROGRESS);
    when(deliveryEventRepository.findByDeliveryNumber(12345L)).thenReturn(deliveryEventList);
    when(deliveryEventRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_PO_LINE_UPDATED);
    DeliveryEvent returnedDeliveryEvent =
        deliveryEventPersisterService.getRdcDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventRepository, times(1)).save(any(DeliveryEvent.class));
    assertNull(returnedDeliveryEvent);
  }

  @Test
  public void
      testGetRdcDeliveryEventToProcess_whenAnyEventOtherThanPreLabelAndDoorAssignComesAfterDoorAssignAndSomethingInRetry_shouldSaveEventInPendingAndReturnNull() {
    deliveryEventList.get(0).setEventStatus(EventTargetStatus.IN_RETRY);
    when(deliveryEventRepository.findByDeliveryNumber(12345L)).thenReturn(deliveryEventList);
    when(deliveryEventRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_PO_ADDED);
    DeliveryEvent returnedDeliveryEvent =
        deliveryEventPersisterService.getRdcDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventRepository, times(1)).save(any(DeliveryEvent.class));
    assertNull(returnedDeliveryEvent);
  }

  @Test
  public void
      testGetRdcDeliveryEventToProcess_whenAnyEventOtherThanPreLabelAndDoorAssignComesAfterDoorAssignAndNothingInRetryPendingProgress_shouldReturnEventInProgress() {
    deliveryEventList.get(0).setEventStatus(EventTargetStatus.DELETE);
    when(deliveryEventRepository.findByDeliveryNumber(12345L)).thenReturn(deliveryEventList);
    when(deliveryEventRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_PO_ADDED);
    DeliveryEvent returnedDeliveryEvent =
        deliveryEventPersisterService.getRdcDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventRepository, times(1)).save(any(DeliveryEvent.class));
    assertEquals(returnedDeliveryEvent.getEventStatus(), EventTargetStatus.EVENT_IN_PROGRESS);
    assertEquals(returnedDeliveryEvent.getEventType(), ReceivingConstants.EVENT_PO_ADDED);
  }

  @Test
  public void
      testGetDeliveryEventToProcess_whenPreLabelGenFallBack_shouldMarkDeleteOthersAndReturnPreLabelGenFallBackEventInProgress() {
    deliveryEventList.get(0).setEventStatus(EventTargetStatus.PENDING);
    when(deliveryEventRepository.findByDeliveryNumber(12345L)).thenReturn(deliveryEventList);
    when(deliveryEventRepository.saveAll(anyList()))
        .thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(deliveryEventRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    deliveryUpdateMessage.setEventType(ReceivingConstants.PRE_LABEL_GEN_FALLBACK);
    DeliveryEvent returnedDeliveryEvent =
        deliveryEventPersisterService.getDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventRepository, times(1)).save(any(DeliveryEvent.class));
    assertEquals(returnedDeliveryEvent.getEventStatus(), EventTargetStatus.IN_PROGRESS);
    assertEquals(returnedDeliveryEvent.getEventType(), ReceivingConstants.PRE_LABEL_GEN_FALLBACK);
    assertEquals(deliveryEventList.get(0).getEventStatus(), EventTargetStatus.DELETE);
  }

  @Test
  public void
      testGetDeliveryEventToProcess_whenAnyEventOtherThanPreLabelComesBeforeDoorAssign_shouldReturnNull() {
    doReturn(new ArrayList<>()).when(deliveryEventRepository).findByDeliveryNumber(12345L);
    when(deliveryEventRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_PO_ADDED);
    DeliveryEvent returnedDeliveryEvent =
        deliveryEventPersisterService.getDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventRepository, times(0)).save(any(DeliveryEvent.class));
    assertEquals(returnedDeliveryEvent, null);
  }

  @Test
  public void
      testGetDeliveryEventToProcess_whenDoorAssignedEventComesForFirstTime_shouldReturnDoorAssignInProgress() {
    doReturn(new ArrayList<>()).when(deliveryEventRepository).findByDeliveryNumber(12345L);
    when(deliveryEventRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_DOOR_ASSIGNED);
    DeliveryEvent returnedDeliveryEvent =
        deliveryEventPersisterService.getDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventRepository, times(1)).save(any(DeliveryEvent.class));
    assertEquals(returnedDeliveryEvent.getEventStatus(), EventTargetStatus.IN_PROGRESS);
    assertEquals(returnedDeliveryEvent.getEventType(), ReceivingConstants.EVENT_DOOR_ASSIGNED);
  }

  @Test
  public void
      testGetDeliveryEventToProcess_whenDoorAssignedEventComesForSecondTime_shouldReturnNull() {
    deliveryEventList.get(0).setEventStatus(EventTargetStatus.PENDING);
    when(deliveryEventRepository.findByDeliveryNumber(12345L)).thenReturn(deliveryEventList);
    when(deliveryEventRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_DOOR_ASSIGNED);
    DeliveryEvent returnedDeliveryEvent =
        deliveryEventPersisterService.getDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventRepository, times(0)).save(any(DeliveryEvent.class));
    assertEquals(returnedDeliveryEvent, null);
  }

  @Test
  public void
      testGetDeliveryEventToProcess_whenDoorAssignedEventComesAfterFallback_shouldReturnNull() {
    deliveryEventList.get(0).setEventType(ReceivingConstants.PRE_LABEL_GEN_FALLBACK);
    when(deliveryEventRepository.findByDeliveryNumber(12345L)).thenReturn(deliveryEventList);
    when(deliveryEventRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_DOOR_ASSIGNED);
    DeliveryEvent returnedDeliveryEvent =
        deliveryEventPersisterService.getDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventRepository, times(0)).save(any(DeliveryEvent.class));
    assertNull(returnedDeliveryEvent);
  }

  @Test
  public void
      testGetDeliveryEventToProcess_whenAnyOtherEventComesAfterFallbackInProgress_shouldSaveEventInPendingAndReturnNull() {
    deliveryEventList.get(0).setEventType(ReceivingConstants.PRE_LABEL_GEN_FALLBACK);
    deliveryEventList.get(0).setEventStatus(EventTargetStatus.PENDING);
    when(deliveryEventRepository.findByDeliveryNumber(12345L)).thenReturn(deliveryEventList);
    when(deliveryEventRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_PO_UPDATED);
    DeliveryEvent returnedDeliveryEvent =
        deliveryEventPersisterService.getDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventRepository, times(1)).save(any(DeliveryEvent.class));
    assertNull(returnedDeliveryEvent);
  }

  @Test
  public void
      testGetDeliveryEventToProcess_whenAnyOtherEventComesAfterFallbackCompleted_shouldReturnEventInProgress() {
    deliveryEventList.get(0).setEventType(ReceivingConstants.PRE_LABEL_GEN_FALLBACK);
    deliveryEventList.get(0).setEventStatus(EventTargetStatus.DELETE);
    when(deliveryEventRepository.findByDeliveryNumber(12345L)).thenReturn(deliveryEventList);
    when(deliveryEventRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_PO_UPDATED);
    DeliveryEvent returnedDeliveryEvent =
        deliveryEventPersisterService.getDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventRepository, times(1)).save(any(DeliveryEvent.class));
    assertEquals(returnedDeliveryEvent.getEventStatus(), EventTargetStatus.IN_PROGRESS);
    assertEquals(returnedDeliveryEvent.getEventType(), ReceivingConstants.EVENT_PO_UPDATED);
  }

  @Test
  public void
      testGetDeliveryEventToProcess_whenAnyEventOtherThanPreLabelAndDoorAssignComesAndDoorAssignInPending_shouldSaveEventInPendingAndReturnNull() {
    deliveryEventList.get(0).setEventStatus(EventTargetStatus.PENDING);
    when(deliveryEventRepository.findByDeliveryNumber(12345L)).thenReturn(deliveryEventList);
    when(deliveryEventRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_PO_UPDATED);
    DeliveryEvent returnedDeliveryEvent =
        deliveryEventPersisterService.getDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventRepository, times(1)).save(any(DeliveryEvent.class));
    assertNull(returnedDeliveryEvent);
  }

  @Test
  public void
      testGetDeliveryEventToProcess_whenAnyEventOtherThanPreLabelAndDoorAssignComesAfterDoorAssignAndSomethingInPending_shouldSaveEventInPendingAndReturnNull() {
    deliveryEventList.get(0).setEventStatus(EventTargetStatus.PENDING);
    when(deliveryEventRepository.findByDeliveryNumber(12345L)).thenReturn(deliveryEventList);
    when(deliveryEventRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_PO_LINE_ADDED);
    DeliveryEvent returnedDeliveryEvent =
        deliveryEventPersisterService.getDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventRepository, times(1)).save(any(DeliveryEvent.class));
    assertNull(returnedDeliveryEvent);
  }

  @Test
  public void
      testGetDeliveryEventToProcess_whenAnyEventOtherThanPreLabelAndDoorAssignComesAfterDoorAssignAndSomethingInProgress_shouldSaveEventInPendingAndReturnNull() {
    deliveryEventList.get(0).setEventStatus(EventTargetStatus.IN_PROGRESS);
    when(deliveryEventRepository.findByDeliveryNumber(12345L)).thenReturn(deliveryEventList);
    when(deliveryEventRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_PO_LINE_UPDATED);
    DeliveryEvent returnedDeliveryEvent =
        deliveryEventPersisterService.getDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventRepository, times(1)).save(any(DeliveryEvent.class));
    assertNull(returnedDeliveryEvent);
  }

  @Test
  public void
      testGetDeliveryEventToProcess_whenAnyEventOtherThanPreLabelAndDoorAssignComesAfterDoorAssignAndSomethingInRetry_shouldSaveEventInPendingAndReturnNull() {
    deliveryEventList.get(0).setEventStatus(EventTargetStatus.IN_RETRY);
    when(deliveryEventRepository.findByDeliveryNumber(12345L)).thenReturn(deliveryEventList);
    when(deliveryEventRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_PO_ADDED);
    DeliveryEvent returnedDeliveryEvent =
        deliveryEventPersisterService.getDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventRepository, times(1)).save(any(DeliveryEvent.class));
    assertNull(returnedDeliveryEvent);
  }

  @Test
  public void
      testGetDeliveryEventToProcess_whenAnyEventOtherThanPreLabelAndDoorAssignComesAfterDoorAssignAndNothingInRetryPendingProgress_shouldReturnEventInProgress() {
    deliveryEventList.get(0).setEventStatus(EventTargetStatus.DELETE);
    when(deliveryEventRepository.findByDeliveryNumber(12345L)).thenReturn(deliveryEventList);
    when(deliveryEventRepository.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_PO_ADDED);
    DeliveryEvent returnedDeliveryEvent =
        deliveryEventPersisterService.getDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventRepository, times(1)).save(any(DeliveryEvent.class));
    assertEquals(returnedDeliveryEvent.getEventStatus(), EventTargetStatus.IN_PROGRESS);
    assertEquals(returnedDeliveryEvent.getEventType(), ReceivingConstants.EVENT_PO_ADDED);
  }

  private DeliveryEvent getDeliveryEvent() {
    return DeliveryEvent.builder()
        .id(1)
        .eventStatus(EventTargetStatus.PENDING)
        .eventType(ReceivingConstants.EVENT_DOOR_ASSIGNED)
        .deliveryNumber(12345L)
        .url("https://delivery.test")
        .retriesCount(0)
        .build();
  }

  @Test
  public void testPurge() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -60 * 24);

    DeliveryEvent deliveryEvent = getDeliveryEvent();
    deliveryEvent.setId(1L);
    deliveryEvent.setCreateTs(cal.getTime());

    DeliveryEvent deliveryEvent1 = getDeliveryEvent();
    deliveryEvent1.setId(10L);
    deliveryEvent1.setCreateTs(cal.getTime());

    when(deliveryEventRepository.findByIdGreaterThanEqual(anyLong(), any()))
        .thenReturn(Arrays.asList(deliveryEvent, deliveryEvent1));
    doNothing().when(deliveryEventRepository).deleteAll();
    PurgeData purgeData =
        PurgeData.builder()
            .purgeEntityType(PurgeEntityType.DELIVERY_EVENT)
            .eventTargetStatus(EventTargetStatus.PENDING)
            .lastDeleteId(0L)
            .build();

    long lastDeletedId = deliveryEventPersisterService.purge(purgeData, pageReq, 30);
    assertEquals(lastDeletedId, 10L);
  }

  @Test
  public void testPurgeWithNoDataToDeleteBeforeDate() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -60 * 24);

    DeliveryEvent deliveryEvent = getDeliveryEvent();
    deliveryEvent.setId(1L);
    deliveryEvent.setCreateTs(cal.getTime());

    DeliveryEvent deliveryEvent1 = getDeliveryEvent();
    deliveryEvent1.setId(10L);
    deliveryEvent1.setCreateTs(cal.getTime());

    when(deliveryEventRepository.findByIdGreaterThanEqual(anyLong(), any()))
        .thenReturn(Arrays.asList(deliveryEvent, deliveryEvent1));
    doNothing().when(deliveryEventRepository).deleteAll();
    PurgeData purgeData =
        PurgeData.builder()
            .purgeEntityType(PurgeEntityType.DELIVERY_EVENT)
            .eventTargetStatus(EventTargetStatus.PENDING)
            .lastDeleteId(0L)
            .build();

    long lastDeletedId = deliveryEventPersisterService.purge(purgeData, pageReq, 90);
    assertEquals(lastDeletedId, 0L);
  }

  @Test
  public void testPurgeWithFewDataToDeleteBeforeDate() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -60 * 24);

    DeliveryEvent deliveryEvent = getDeliveryEvent();
    deliveryEvent.setId(1L);
    deliveryEvent.setCreateTs(cal.getTime());

    DeliveryEvent deliveryEvent1 = getDeliveryEvent();
    deliveryEvent1.setId(10L);
    deliveryEvent1.setCreateTs(new Date());

    when(deliveryEventRepository.findByIdGreaterThanEqual(anyLong(), any()))
        .thenReturn(Arrays.asList(deliveryEvent, deliveryEvent1));
    doNothing().when(deliveryEventRepository).deleteAll();
    PurgeData purgeData =
        PurgeData.builder()
            .purgeEntityType(PurgeEntityType.DELIVERY_EVENT)
            .eventTargetStatus(EventTargetStatus.PENDING)
            .lastDeleteId(0L)
            .build();

    long lastDeletedId = deliveryEventPersisterService.purge(purgeData, pageReq, 30);
    assertEquals(lastDeletedId, 1L);
  }

  @Test
  public void testDeliveryForRdcScheduler_NoDeliveryInProgress() {
    when(deliveryEventRepository.findDeliveriesWithEventInProgress(anyInt(), anyString()))
        .thenReturn(Collections.emptyList());
    when(deliveryEventRepository
            .findFirstByEventStatusInAndRetriesCountIsLessThanOrderByEventTypePriorityAscCreateTsAsc(
                Collections.singletonList(EventTargetStatus.EVENT_PENDING), 3))
        .thenReturn(new DeliveryEvent());
    deliveryEventPersisterService.getDeliveryForRdcScheduler(3);
    verify(deliveryEventRepository, times(1))
        .findFirstByEventStatusInAndRetriesCountIsLessThanOrderByEventTypePriorityAscCreateTsAsc(
            Collections.singletonList(EventTargetStatus.EVENT_PENDING), 3);
    verify(deliveryEventRepository, times(0))
        .findFirstByDeliveryNumberNotInAndEventStatusInAndRetriesCountIsLessThanOrderByEventTypePriorityAscCreateTsAsc(
            anyList(), anyList(), anyInt());
  }

  @Test
  public void testDeliveryForRdcScheduler_DeliveryInProgress() {
    when(deliveryEventRepository.findDeliveriesWithEventInProgress(anyInt(), anyString()))
        .thenReturn(Collections.singletonList(98932932L));
    when(deliveryEventRepository
            .findFirstByDeliveryNumberNotInAndEventStatusInAndRetriesCountIsLessThanOrderByEventTypePriorityAscCreateTsAsc(
                Collections.singletonList(98932932L),
                Collections.singletonList(EventTargetStatus.EVENT_PENDING),
                3))
        .thenReturn(new DeliveryEvent());
    deliveryEventPersisterService.getDeliveryForRdcScheduler(3);
    verify(deliveryEventRepository, times(1))
        .findFirstByDeliveryNumberNotInAndEventStatusInAndRetriesCountIsLessThanOrderByEventTypePriorityAscCreateTsAsc(
            Collections.singletonList(98932932L),
            Collections.singletonList(EventTargetStatus.EVENT_PENDING),
            3);
  }
}
