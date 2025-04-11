package com.walmart.move.nim.receiving.acc.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.walmart.move.nim.receiving.acc.config.ACCManagedConfig;
import com.walmart.move.nim.receiving.acc.mock.data.MockLabelData;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.entity.DeliveryEvent;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.service.DeliveryEventPersisterService;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.core.service.LocationService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.mockito.AdditionalAnswers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class PreLabelDeliveryServiceTest extends ReceivingTestBase {
  @InjectMocks private PreLabelDeliveryService genericPreLabelDeliveryEventProcessor;

  @Mock private DeliveryService deliveryService;

  @Mock private DeliveryEventPersisterService deliveryEventPersisterService;

  @Mock private LocationService locationService;

  @Mock private GenericLabelGeneratorService genericLabelGeneratorService;

  @Mock private ACCManagedConfig accManagedConfig;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  private DeliveryEvent deliveryEvent;

  private DeliveryUpdateMessage deliveryUpdateMessage;

  private DeliveryDetails deliveryDetails;

  private Long deliveryNumber;

  private Map<DeliveryDocumentLine, List<LabelData>> labelDataListPerPoLine;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);

    deliveryNumber = 123456L;
    deliveryEvent =
        DeliveryEvent.builder()
            .id(1)
            .eventStatus(EventTargetStatus.PENDING)
            .eventType(ReceivingConstants.EVENT_DOOR_ASSIGNED)
            .deliveryNumber(123456L)
            .url("https://delivery.test")
            .retriesCount(0)
            .build();
    deliveryUpdateMessage =
        DeliveryUpdateMessage.builder()
            .deliveryNumber("123456")
            .countryCode("US")
            .siteNumber("6051")
            .deliveryStatus("ARV")
            .url("https://delivery.test")
            .build();
    TenantContext.setFacilityNum(54321);
    TenantContext.setFacilityCountryCode("us");
    try {
      String dataPath =
          new File("../../receiving-test/src/main/resources/json/gdm/v2/DeliveryDetails.json")
              .getCanonicalPath();
      deliveryDetails =
          JacksonParser.convertJsonToObject(
              new String(Files.readAllBytes(Paths.get(dataPath))), DeliveryDetails.class);
      labelDataListPerPoLine =
          Collections.singletonMap(
              deliveryDetails.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0),
              Collections.singletonList(MockLabelData.getMockLabelData()));

    } catch (IOException e) {
      assert (false);
    }
  }

  @AfterMethod
  public void resetMocks() {
    reset(deliveryService);
    reset(locationService);
    reset(deliveryEventPersisterService);
    reset(genericLabelGeneratorService);
    reset(accManagedConfig);
    reset(tenantSpecificConfigReader);
  }

  @BeforeMethod
  public void beforeMethod() {
    doReturn(deliveryService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.DELIVERY_SERVICE_KEY), any());
    doReturn(genericLabelGeneratorService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), eq(ReceivingConstants.LABEL_GENERATOR_SERVICE), any());
  }

  @Test
  public void testProcessEvent_notPreLabelEventThenDeliveryThenShouldNotBeFetched()
      throws ReceivingException {
    deliveryUpdateMessage.setEventType("SCHEDULED");
    deliveryDetails.setDoorNumber("D101");
    genericPreLabelDeliveryEventProcessor.processDeliveryEvent(deliveryUpdateMessage);
    when(accManagedConfig.isLabelDeltaPublishEnabled()).thenReturn(Boolean.FALSE);
    doReturn(deliveryDetails)
        .when(deliveryService)
        .getDeliveryDetails(deliveryUpdateMessage.getUrl(), deliveryNumber);
    verify(deliveryService, times(0))
        .getDeliveryDetails(deliveryUpdateMessage.getUrl(), deliveryNumber);
    verify(genericLabelGeneratorService, times(0))
        .publishLabelsToAcl(
            eq(labelDataListPerPoLine), eq(deliveryNumber), eq("D101"), eq("322683"), anyBoolean());
  }

  @Test
  public void testProcessEvent_deliveryNotInProcessableStateThenDeliveryShouldNotBeFetched()
      throws ReceivingException {
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_DOOR_ASSIGNED);
    deliveryUpdateMessage.setDeliveryStatus(DeliveryStatus.SCH.toString());
    genericPreLabelDeliveryEventProcessor.processDeliveryEvent(deliveryUpdateMessage);
    when(accManagedConfig.isLabelDeltaPublishEnabled()).thenReturn(Boolean.FALSE);
    verify(deliveryService, times(0))
        .getDeliveryDetails(deliveryUpdateMessage.getUrl(), deliveryNumber);
    verify(genericLabelGeneratorService, times(0))
        .publishLabelsToAcl(
            eq(labelDataListPerPoLine), eq(deliveryNumber), eq("D101"), eq("322683"), anyBoolean());
  }

  @Test
  public void testProcessEvent_fetchDeliveryDocumentThrowsExceptionThenShouldNotCallLocation()
      throws ReceivingException {
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_DOOR_ASSIGNED);
    deliveryUpdateMessage.setDeliveryStatus(DeliveryStatus.ARV.toString());
    doThrow(
            new ReceivingException(
                ReceivingException.DELIVERY_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                ExceptionCodes.DELIVERY_NOT_FOUND))
        .when(deliveryService)
        .getDeliveryDetails(deliveryUpdateMessage.getUrl(), deliveryNumber);
    when(accManagedConfig.isLabelDeltaPublishEnabled()).thenReturn(Boolean.FALSE);
    genericPreLabelDeliveryEventProcessor.processDeliveryEvent(deliveryUpdateMessage);
    verify(deliveryService, times(1))
        .getDeliveryDetails(deliveryUpdateMessage.getUrl(), deliveryNumber);
    verify(locationService, times(0)).isOnlineOrHasFloorLine(anyString());
    verify(genericLabelGeneratorService, times(0))
        .publishLabelsToAcl(
            eq(labelDataListPerPoLine), eq(deliveryNumber), eq("D101"), eq("322683"), anyBoolean());
  }

  @Test
  public void testProcessEvent_deliveryDocumentIsNullThenShouldNotCallLocation()
      throws ReceivingException {
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_DOOR_ASSIGNED);
    deliveryUpdateMessage.setDeliveryStatus(DeliveryStatus.ARV.toString());
    doReturn(null)
        .when(deliveryService)
        .getDeliveryDetails(deliveryUpdateMessage.getUrl(), deliveryNumber);
    when(accManagedConfig.isLabelDeltaPublishEnabled()).thenReturn(Boolean.FALSE);
    genericPreLabelDeliveryEventProcessor.processDeliveryEvent(deliveryUpdateMessage);
    verify(deliveryService, times(1))
        .getDeliveryDetails(deliveryUpdateMessage.getUrl(), deliveryNumber);
    verify(locationService, times(0)).isOnlineOrHasFloorLine(anyString());
    verify(genericLabelGeneratorService, times(0))
        .publishLabelsToAcl(
            eq(labelDataListPerPoLine), eq(deliveryNumber), eq("D101"), eq("322683"), anyBoolean());
  }

  @Test
  public void
      testProcessEvent_deliveryDocumentHasNoDoorInfoThenShouldNotCallLocationAndShouldNotCallDeliveryEventToProcess()
          throws ReceivingException {
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_DOOR_ASSIGNED);
    deliveryUpdateMessage.setDeliveryStatus(DeliveryStatus.ARV.toString());
    deliveryDetails.setDoorNumber(null);
    doReturn(deliveryDetails)
        .when(deliveryService)
        .getDeliveryDetails(deliveryUpdateMessage.getUrl(), deliveryNumber);
    when(accManagedConfig.isLabelDeltaPublishEnabled()).thenReturn(Boolean.FALSE);
    genericPreLabelDeliveryEventProcessor.processDeliveryEvent(deliveryUpdateMessage);
    verify(deliveryService, times(1))
        .getDeliveryDetails(deliveryUpdateMessage.getUrl(), deliveryNumber);
    verify(locationService, times(0)).isOnlineOrHasFloorLine(anyString());
    verify(deliveryEventPersisterService, times(0))
        .getDeliveryEventToProcess(deliveryUpdateMessage);
    verify(genericLabelGeneratorService, times(0))
        .publishLabelsToAcl(
            eq(labelDataListPerPoLine), eq(deliveryNumber), eq("D101"), eq("322683"), anyBoolean());
  }

  @Test
  public void
      testProcessEvent_deliveryDocumentHasDoorAndIsOfflineAndHasNoFloorLineThenShouldNotCallDeliveryEventToProcess()
          throws ReceivingException {
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_DOOR_ASSIGNED);
    deliveryUpdateMessage.setDeliveryStatus(DeliveryStatus.ARV.toString());
    deliveryDetails.setDoorNumber("D101");
    doReturn(deliveryDetails)
        .when(deliveryService)
        .getDeliveryDetails(deliveryUpdateMessage.getUrl(), deliveryNumber);
    doReturn(false).when(locationService).isOnlineOrHasFloorLine(anyString());
    when(accManagedConfig.isLabelDeltaPublishEnabled()).thenReturn(Boolean.FALSE);
    genericPreLabelDeliveryEventProcessor.processDeliveryEvent(deliveryUpdateMessage);
    verify(deliveryService, times(1))
        .getDeliveryDetails(deliveryUpdateMessage.getUrl(), deliveryNumber);
    verify(locationService, times(1)).isOnlineOrHasFloorLine(anyString());
    verify(deliveryEventPersisterService, times(0))
        .getDeliveryEventToProcess(deliveryUpdateMessage);
    verify(genericLabelGeneratorService, times(0))
        .publishLabelsToAcl(
            eq(labelDataListPerPoLine), eq(deliveryNumber), eq("D101"), eq("322683"), anyBoolean());
  }

  @Test
  public void
      testProcessEvent_deliveryDetailsIsNullAndDeliveryEventIsNotNullThenShouldSaveDeliveryEvent()
          throws ReceivingException {
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_DOOR_ASSIGNED);
    deliveryUpdateMessage.setDeliveryStatus(DeliveryStatus.ARV.toString());
    doReturn(null)
        .when(deliveryService)
        .getDeliveryDetails(deliveryUpdateMessage.getUrl(), deliveryNumber);
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getDeliveryEventToProcess(any());
    when(deliveryEventPersisterService.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isLabelDeltaPublishEnabled()).thenReturn(Boolean.FALSE);
    genericPreLabelDeliveryEventProcessor.processDeliveryEvent(deliveryUpdateMessage);
    verify(deliveryService, times(1))
        .getDeliveryDetails(deliveryUpdateMessage.getUrl(), deliveryNumber);
    verify(deliveryEventPersisterService, times(1))
        .getDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventPersisterService, times(1)).save(any());
    verify(genericLabelGeneratorService, times(0))
        .publishLabelsToAcl(
            eq(labelDataListPerPoLine), eq(deliveryNumber), eq("D101"), eq("322683"), anyBoolean());
  }

  @Test
  public void
      testProcessEvent_hasDeliveryDetailsAndLocationServiceThrowsExceptionAndDeliveryEventIsNotNullThenShouldSaveDeliveryEvent()
          throws ReceivingException {
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_DOOR_ASSIGNED);
    deliveryUpdateMessage.setDeliveryStatus(DeliveryStatus.ARV.toString());
    deliveryDetails.setDoorNumber("D101");
    doReturn(deliveryDetails)
        .when(deliveryService)
        .getDeliveryDetails(deliveryUpdateMessage.getUrl(), deliveryNumber);
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getDeliveryEventToProcess(any());
    doThrow(
            new ReceivingDataNotFoundException(
                ExceptionCodes.LOCATION_NOT_FOUND,
                String.format(ReceivingConstants.LOCATION_NOT_FOUND, "D101")))
        .when(locationService)
        .isOnlineOrHasFloorLine(anyString());
    when(deliveryEventPersisterService.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isLabelDeltaPublishEnabled()).thenReturn(Boolean.FALSE);
    genericPreLabelDeliveryEventProcessor.processDeliveryEvent(deliveryUpdateMessage);
    verify(deliveryService, times(1))
        .getDeliveryDetails(deliveryUpdateMessage.getUrl(), deliveryNumber);
    verify(deliveryEventPersisterService, times(1))
        .getDeliveryEventToProcess(deliveryUpdateMessage);
    verify(deliveryEventPersisterService, times(1)).save(any());
    verify(genericLabelGeneratorService, times(0))
        .publishLabelsToAcl(
            eq(labelDataListPerPoLine), eq(deliveryNumber), eq("D101"), eq("322683"), anyBoolean());
  }

  @Test
  public void
      testProcessEvent_generateGenericLabelThrowsReceivingExceptionThenShouldSaveDeliveryEvent()
          throws ReceivingException {
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_DOOR_ASSIGNED);
    deliveryUpdateMessage.setDeliveryStatus(DeliveryStatus.ARV.toString());
    deliveryDetails.setDoorNumber("D101");
    doReturn(deliveryDetails)
        .when(deliveryService)
        .getDeliveryDetails(deliveryUpdateMessage.getUrl(), deliveryNumber);
    doReturn(true).when(locationService).isOnlineOrHasFloorLine(anyString());
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getDeliveryEventToProcess(any());
    when(deliveryEventPersisterService.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isLabelDeltaPublishEnabled()).thenReturn(Boolean.FALSE);
    doThrow(
            new ReceivingException(
                ReceivingConstants.LPNS_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                ExceptionCodes.LPNS_NOT_FOUND))
        .when(genericLabelGeneratorService)
        .generateGenericLabel(any(), any());
    genericPreLabelDeliveryEventProcessor.processDeliveryEvent(deliveryUpdateMessage);
    verify(deliveryService, times(1))
        .getDeliveryDetails(deliveryUpdateMessage.getUrl(), deliveryNumber);
    verify(deliveryEventPersisterService, times(1))
        .getDeliveryEventToProcess(deliveryUpdateMessage);
    verify(genericLabelGeneratorService, times(1)).generateGenericLabel(any(), any());
    verify(deliveryEventPersisterService, times(1)).save(any());
    verify(genericLabelGeneratorService, times(0))
        .publishLabelsToAcl(
            eq(labelDataListPerPoLine), eq(deliveryNumber), eq("D101"), eq("322683"), anyBoolean());
  }

  @Test
  public void testProcessEvent_generateGenericLabelStalenessCheckThenShouldNotSaveDeliveryEvent()
      throws ReceivingException {
    deliveryEvent.setEventStatus(EventTargetStatus.STALE);
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_DOOR_ASSIGNED);
    deliveryUpdateMessage.setDeliveryStatus(DeliveryStatus.ARV.toString());
    deliveryDetails.setDoorNumber("D101");
    doReturn(deliveryDetails)
        .when(deliveryService)
        .getDeliveryDetails(deliveryUpdateMessage.getUrl(), deliveryNumber);
    doReturn(true).when(locationService).isOnlineOrHasFloorLine(anyString());
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getDeliveryEventToProcess(any());
    when(deliveryEventPersisterService.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isLabelDeltaPublishEnabled()).thenReturn(Boolean.FALSE);
    doReturn(new Pair<>(deliveryEvent, labelDataListPerPoLine))
        .when(genericLabelGeneratorService)
        .generateGenericLabel(any(), any());
    genericPreLabelDeliveryEventProcessor.processDeliveryEvent(deliveryUpdateMessage);
    verify(deliveryService, times(1))
        .getDeliveryDetails(deliveryUpdateMessage.getUrl(), deliveryNumber);
    verify(deliveryEventPersisterService, times(1))
        .getDeliveryEventToProcess(deliveryUpdateMessage);
    verify(genericLabelGeneratorService, times(1)).generateGenericLabel(any(), any());
    verify(deliveryEventPersisterService, times(0)).save(any());
    deliveryEvent.setEventStatus(EventTargetStatus.PENDING);
    verify(genericLabelGeneratorService, times(0))
        .publishLabelsToAcl(
            eq(labelDataListPerPoLine), eq(deliveryNumber), eq("D101"), eq("322683"), anyBoolean());
  }

  @Test
  public void testProcessEvent_HappyPath() throws ReceivingException {
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_DOOR_ASSIGNED);
    deliveryUpdateMessage.setDeliveryStatus(DeliveryStatus.ARV.toString());
    deliveryDetails.setDoorNumber("D101");
    doReturn(deliveryDetails)
        .when(deliveryService)
        .getDeliveryDetails(deliveryUpdateMessage.getUrl(), deliveryNumber);
    doReturn(true).when(locationService).isOnlineOrHasFloorLine(anyString());
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getDeliveryEventToProcess(any());
    when(deliveryEventPersisterService.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isLabelDeltaPublishEnabled()).thenReturn(Boolean.FALSE);
    doReturn(new Pair<>(deliveryEvent, labelDataListPerPoLine))
        .when(genericLabelGeneratorService)
        .generateGenericLabel(any(), any());
    genericPreLabelDeliveryEventProcessor.processDeliveryEvent(deliveryUpdateMessage);
    verify(deliveryService, times(1))
        .getDeliveryDetails(deliveryUpdateMessage.getUrl(), deliveryNumber);
    verify(deliveryEventPersisterService, times(1))
        .getDeliveryEventToProcess(deliveryUpdateMessage);
    verify(locationService, times(1)).isOnlineOrHasFloorLine(anyString());
    verify(genericLabelGeneratorService, times(1)).generateGenericLabel(any(), any());
    verify(deliveryEventPersisterService, times(1)).save(any());
    verify(genericLabelGeneratorService, times(1))
        .publishLabelsToAcl(
            eq(labelDataListPerPoLine), eq(deliveryNumber), eq("D101"), eq("322683"), anyBoolean());
  }

  @Test
  public void testProcessEvent_HappyPath_DeltaPublish() throws ReceivingException {
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_PO_ADDED);
    deliveryUpdateMessage.setDeliveryStatus(DeliveryStatus.ARV.toString());
    deliveryEvent.setEventType(ReceivingConstants.EVENT_PO_ADDED);
    deliveryDetails.setDoorNumber("D101");
    doReturn(deliveryDetails)
        .when(deliveryService)
        .getDeliveryDetails(deliveryUpdateMessage.getUrl(), deliveryNumber);
    doReturn(true).when(locationService).isOnlineOrHasFloorLine(anyString());
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getDeliveryEventToProcess(any());
    when(deliveryEventPersisterService.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isLabelDeltaPublishEnabled()).thenReturn(Boolean.TRUE);
    doReturn(new Pair<>(deliveryEvent, labelDataListPerPoLine))
        .when(genericLabelGeneratorService)
        .generateGenericLabel(any(), any());
    genericPreLabelDeliveryEventProcessor.processDeliveryEvent(deliveryUpdateMessage);
    verify(deliveryService, times(1))
        .getDeliveryDetails(deliveryUpdateMessage.getUrl(), deliveryNumber);
    verify(deliveryEventPersisterService, times(1))
        .getDeliveryEventToProcess(deliveryUpdateMessage);
    verify(locationService, times(1)).isOnlineOrHasFloorLine(anyString());
    verify(genericLabelGeneratorService, times(1)).generateGenericLabel(any(), any());
    verify(deliveryEventPersisterService, times(1)).save(any());
    verify(genericLabelGeneratorService, times(1))
        .publishLabelsToAcl(
            eq(labelDataListPerPoLine), eq(deliveryNumber), eq("D101"), eq("322683"), anyBoolean());
    deliveryEvent.setEventType(ReceivingConstants.EVENT_DOOR_ASSIGNED);
  }

  @Test
  public void testProcessEvent_HappyPath_CompletePublish_WithDeltaFlagEnabled()
      throws ReceivingException {
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_DOOR_ASSIGNED);
    deliveryUpdateMessage.setDeliveryStatus(DeliveryStatus.ARV.toString());
    deliveryEvent.setEventType(ReceivingConstants.EVENT_DOOR_ASSIGNED);
    deliveryDetails.setDoorNumber("D101");
    doReturn(deliveryDetails)
        .when(deliveryService)
        .getDeliveryDetails(deliveryUpdateMessage.getUrl(), deliveryNumber);
    doReturn(true).when(locationService).isOnlineOrHasFloorLine(anyString());
    doReturn(deliveryEvent).when(deliveryEventPersisterService).getDeliveryEventToProcess(any());
    when(deliveryEventPersisterService.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isLabelDeltaPublishEnabled()).thenReturn(Boolean.TRUE);
    doReturn(new Pair<>(deliveryEvent, labelDataListPerPoLine))
        .when(genericLabelGeneratorService)
        .generateGenericLabel(any(), any());
    genericPreLabelDeliveryEventProcessor.processDeliveryEvent(deliveryUpdateMessage);
    verify(deliveryService, times(1))
        .getDeliveryDetails(deliveryUpdateMessage.getUrl(), deliveryNumber);
    verify(deliveryEventPersisterService, times(1))
        .getDeliveryEventToProcess(deliveryUpdateMessage);
    verify(locationService, times(1)).isOnlineOrHasFloorLine(anyString());
    verify(genericLabelGeneratorService, times(1)).generateGenericLabel(any(), any());
    verify(deliveryEventPersisterService, times(1)).save(any());
    verify(genericLabelGeneratorService, times(1))
        .publishLabelsToAcl(
            eq(labelDataListPerPoLine), eq(deliveryNumber), eq("D101"), eq("322683"), anyBoolean());
  }

  @Test
  public void testProcessEventForScheduler_DeliveryDetailsNull() throws ReceivingException {
    doReturn(null).when(deliveryService).getDeliveryDetails(deliveryEvent.getUrl(), deliveryNumber);
    doReturn(true).when(locationService).isOnlineOrHasFloorLine(anyString());
    when(deliveryEventPersisterService.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isLabelDeltaPublishEnabled()).thenReturn(Boolean.FALSE);
    doReturn(new Pair<>(deliveryEvent, labelDataListPerPoLine))
        .when(genericLabelGeneratorService)
        .generateGenericLabel(any(), any());
    assertFalse(
        genericPreLabelDeliveryEventProcessor.processDeliveryEventForScheduler(deliveryEvent));
    verify(deliveryService, times(1)).getDeliveryDetails(deliveryEvent.getUrl(), deliveryNumber);
    verify(genericLabelGeneratorService, times(0)).generateGenericLabel(any(), any());
    verify(locationService, times(0)).isOnlineOrHasFloorLine(anyString());
    verify(deliveryEventPersisterService, times(1)).save(any());
    verify(genericLabelGeneratorService, times(0))
        .publishLabelsToAcl(
            eq(labelDataListPerPoLine), eq(deliveryNumber), eq("D101"), eq("322683"), anyBoolean());
  }

  @Test
  public void testProcessEventForScheduler_DeliveryDetailsException() throws ReceivingException {
    doThrow(
            new ReceivingException(
                ReceivingException.DELIVERY_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                ExceptionCodes.DELIVERY_NOT_FOUND))
        .when(deliveryService)
        .getDeliveryDetails(deliveryUpdateMessage.getUrl(), deliveryNumber);
    doReturn(true).when(locationService).isOnlineOrHasFloorLine(anyString());
    when(deliveryEventPersisterService.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(accManagedConfig.isLabelDeltaPublishEnabled()).thenReturn(Boolean.FALSE);
    doReturn(new Pair<>(deliveryEvent, labelDataListPerPoLine))
        .when(genericLabelGeneratorService)
        .generateGenericLabel(any(), any());
    assertFalse(
        genericPreLabelDeliveryEventProcessor.processDeliveryEventForScheduler(deliveryEvent));
    verify(deliveryService, times(1)).getDeliveryDetails(deliveryEvent.getUrl(), deliveryNumber);
    verify(locationService, times(0)).isOnlineOrHasFloorLine(anyString());
    verify(genericLabelGeneratorService, times(0)).generateGenericLabel(any(), any());
    verify(genericLabelGeneratorService, times(0))
        .publishLabelsToAcl(
            eq(labelDataListPerPoLine), eq(deliveryNumber), eq("D101"), eq("322683"), anyBoolean());
    verify(deliveryEventPersisterService, times(1)).save(any());
  }

  @Test
  public void testProcessEventForScheduler_StalenessCheck() throws ReceivingException {
    deliveryDetails.setDoorNumber("D102");
    deliveryEvent.setEventStatus(EventTargetStatus.STALE);
    doReturn(deliveryDetails)
        .when(deliveryService)
        .getDeliveryDetails(deliveryEvent.getUrl(), deliveryNumber);
    doReturn(true).when(locationService).isOnlineOrHasFloorLine(anyString());
    when(deliveryEventPersisterService.getDeliveryEventById(any())).thenReturn(deliveryEvent);
    when(accManagedConfig.isLabelDeltaPublishEnabled()).thenReturn(Boolean.FALSE);
    doReturn(new Pair<>(deliveryEvent, labelDataListPerPoLine))
        .when(genericLabelGeneratorService)
        .generateGenericLabel(any(), any());
    assertFalse(
        genericPreLabelDeliveryEventProcessor.processDeliveryEventForScheduler(deliveryEvent));
    verify(deliveryService, times(1)).getDeliveryDetails(deliveryEvent.getUrl(), deliveryNumber);
    verify(genericLabelGeneratorService, times(1)).generateGenericLabel(any(), any());
    verify(deliveryEventPersisterService, times(0)).save(any());
    verify(genericLabelGeneratorService, times(0))
        .publishLabelsToAcl(
            eq(labelDataListPerPoLine), eq(deliveryNumber), eq("D102"), eq("322683"), anyBoolean());
    deliveryEvent.setEventStatus(EventTargetStatus.PENDING);
  }

  @Test
  public void testProcessEventForScheduler_ExceptionScenario() throws ReceivingException {
    deliveryDetails.setDoorNumber("D102");
    deliveryEvent.setEventStatus(EventTargetStatus.STALE);
    doReturn(deliveryDetails)
        .when(deliveryService)
        .getDeliveryDetails(deliveryEvent.getUrl(), deliveryNumber);
    doReturn(true).when(locationService).isOnlineOrHasFloorLine(anyString());
    when(accManagedConfig.isLabelDeltaPublishEnabled()).thenReturn(Boolean.FALSE);
    when(deliveryEventPersisterService.getDeliveryEventById(any())).thenReturn(deliveryEvent);
    doThrow(
            new ReceivingException(
                ReceivingConstants.LPNS_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                ExceptionCodes.LPNS_NOT_FOUND))
        .when(genericLabelGeneratorService)
        .generateGenericLabel(any(), any());
    assertFalse(
        genericPreLabelDeliveryEventProcessor.processDeliveryEventForScheduler(deliveryEvent));
    verify(deliveryService, times(1))
        .getDeliveryDetails(eq(deliveryEvent.getUrl()), eq(deliveryNumber));
    verify(genericLabelGeneratorService, times(1)).generateGenericLabel(any(), any());
    verify(genericLabelGeneratorService, times(0))
        .publishLabelsToAcl(
            eq(labelDataListPerPoLine), eq(deliveryNumber), eq("D102"), eq("322683"), anyBoolean());
    verify(deliveryEventPersisterService, times(1)).save(any());
  }

  @Test
  public void testProcessEventForScheduler_HappyPath() throws ReceivingException {
    deliveryDetails.setDoorNumber("D102");
    doReturn(deliveryDetails)
        .when(deliveryService)
        .getDeliveryDetails(deliveryEvent.getUrl(), deliveryNumber);
    doReturn(true).when(locationService).isOnlineOrHasFloorLine(anyString());
    when(accManagedConfig.isLabelDeltaPublishEnabled()).thenReturn(Boolean.FALSE);
    when(deliveryEventPersisterService.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(deliveryEventPersisterService.getDeliveryEventById(any())).thenReturn(deliveryEvent);
    doReturn(new Pair<>(deliveryEvent, labelDataListPerPoLine))
        .when(genericLabelGeneratorService)
        .generateGenericLabel(any(), any());
    assertTrue(
        genericPreLabelDeliveryEventProcessor.processDeliveryEventForScheduler(deliveryEvent));
    verify(deliveryService, times(1)).getDeliveryDetails(deliveryEvent.getUrl(), deliveryNumber);
    verify(genericLabelGeneratorService, times(1)).generateGenericLabel(any(), any());
    verify(genericLabelGeneratorService, times(1))
        .publishLabelsToAcl(
            eq(labelDataListPerPoLine), eq(deliveryNumber), eq("D102"), eq("322683"), anyBoolean());
    verify(deliveryEventPersisterService, times(1)).save(any());
  }

  @Test
  public void testProcessEventForScheduler_HappyPath_DeltaEnabled() throws ReceivingException {
    deliveryDetails.setDoorNumber("D102");
    deliveryEvent.setEventType(ReceivingConstants.EVENT_PO_ADDED);
    doReturn(deliveryDetails)
        .when(deliveryService)
        .getDeliveryDetails(deliveryEvent.getUrl(), deliveryNumber);
    doReturn(true).when(locationService).isOnlineOrHasFloorLine(anyString());
    when(accManagedConfig.isLabelDeltaPublishEnabled()).thenReturn(Boolean.TRUE);
    when(deliveryEventPersisterService.save(any())).thenAnswer(AdditionalAnswers.returnsFirstArg());
    when(deliveryEventPersisterService.getDeliveryEventById(any())).thenReturn(deliveryEvent);
    doReturn(new Pair<>(deliveryEvent, labelDataListPerPoLine))
        .when(genericLabelGeneratorService)
        .generateGenericLabel(any(), any());
    assertTrue(
        genericPreLabelDeliveryEventProcessor.processDeliveryEventForScheduler(deliveryEvent));
    verify(deliveryService, times(1)).getDeliveryDetails(deliveryEvent.getUrl(), deliveryNumber);
    verify(genericLabelGeneratorService, times(1)).generateGenericLabel(any(), any());
    verify(genericLabelGeneratorService, times(1))
        .publishLabelsToAcl(
            eq(labelDataListPerPoLine), eq(deliveryNumber), eq("D102"), eq("322683"), anyBoolean());
    verify(deliveryEventPersisterService, times(1)).save(any());
  }
}
