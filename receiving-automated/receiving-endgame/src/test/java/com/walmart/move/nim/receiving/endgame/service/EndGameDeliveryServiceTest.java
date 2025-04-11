package com.walmart.move.nim.receiving.endgame.service;

import static com.walmart.move.nim.receiving.endgame.common.EndGameUtilsTestCase.getEventStoreData;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.fail;

import com.google.gson.Gson;
import com.walmart.atlas.global.fc.service.IOutboxPublisherService;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.DeliveryStatusPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.RestUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.config.MaasTopics;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.entity.EventStore;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.core.model.gdm.GdmAsnDeliveryResponse;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.core.repositories.DeliveryMetaDataRepository;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.retry.RetryableRestConnector;
import com.walmart.move.nim.receiving.core.service.DefaultCompleteDeliveryProcessor;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import com.walmart.move.nim.receiving.core.service.EventStoreService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.endgame.common.EndGameUtilsTestCase;
import com.walmart.move.nim.receiving.endgame.message.common.ScanEventData;
import com.walmart.move.nim.receiving.endgame.mock.data.MockDeliveryMetaData;
import com.walmart.move.nim.receiving.endgame.model.Location;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.EventStoreType;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.testng.AssertJUnit;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class EndGameDeliveryServiceTest extends ReceivingTestBase {
  private DeliveryUpdateMessage deliveryUpdateMessage;
  private Delivery delivery;
  private Gson gson = new Gson();

  @InjectMocks private EndGameDeliveryService endGameDeliveryService;

  @Mock private RetryableRestConnector restConnector;

  @Mock private RestUtils restUtils;

  @Mock private ReceiptService receiptService;

  @Mock private DeliveryStatusPublisher deliveryStatusPublisher;

  @Mock private AppConfig appConfig;

  @Mock private MaasTopics maasTopics;

  @Mock private InstructionRepository instructionRepository;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Mock private EndgameDeliveryStatusPublisher endgameDeliveryStatusPublisher;

  @Mock private EndGameLabelingService endGameLabelingService;
  @Mock private EventStoreService eventStoreService;
  @Mock private DeliveryMetaDataRepository deliveryMetaDataRepository;
  @Mock private EventProcessor eventProcessor;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private EndGameDeliveryEventProcessor endGameDeliveryEventProcessor;
  @Mock private DeliveryMetaDataService deliveryMetaDataService;

  @InjectMocks private DefaultCompleteDeliveryProcessor completeDeliveryProcessor;
  @MockBean private IOutboxPublisherService iOutboxPublisherService;

  @BeforeClass
  private void initMocksAndFields() throws IOException {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(endGameDeliveryService, "gson", gson);
    TenantContext.setFacilityNum(4321);
    TenantContext.setFacilityCountryCode("US");

    deliveryUpdateMessage = new DeliveryUpdateMessage();
    deliveryUpdateMessage.setDeliveryNumber("12345678");
    deliveryUpdateMessage.setEventType("DOOR_ASSIGNED");

    String dataPath =
        new File("../../receiving-test/src/main/resources/json/GDMDeliveryDocumentV3.json")
            .getCanonicalPath();
    delivery = gson.fromJson(new String(Files.readAllBytes(Paths.get(dataPath))), Delivery.class);
  }

  @AfterMethod
  public void restRestUtilCalls() {
    reset(restConnector);
    reset(restUtils);
    reset(instructionRepository);
    reset(receiptService);
    reset(deliveryStatusPublisher);
    reset(tenantSpecificConfigReader);
    reset(endGameLabelingService);
    reset(eventStoreService);
    reset(deliveryMetaDataRepository);
    reset(eventProcessor);
    reset(configUtils);
    reset(deliveryMetaDataService);
  }

  @Test
  public void testGetGDMDatahappyPath() {
    try {
      doReturn(new ResponseEntity<>(gson.toJson(delivery), HttpStatus.OK))
          .when(restConnector)
          .exchange(any(), any(), any(), eq(String.class));

      Delivery deliveryResponse = endGameDeliveryService.getGDMData(deliveryUpdateMessage);
      assertEquals(deliveryResponse.getDeliveryNumber(), delivery.getDeliveryNumber());
      assertEquals(deliveryResponse.getDoorNumber(), delivery.getDoorNumber());
      assertEquals(
          deliveryResponse.getPurchaseOrders().get(0).getPoNumber(),
          delivery.getPurchaseOrders().get(0).getPoNumber());
      assertEquals(
          deliveryResponse.getPurchaseOrders().get(0).getLines().get(0).getPoLineNumber(),
          delivery.getPurchaseOrders().get(0).getLines().get(0).getPoLineNumber());
    } catch (ReceivingException e) {
      assertTrue(false);
    }
  }

  @Test
  public void testFindDeliveryDocument() {
    try {
      doReturn(new ResponseEntity<String>("{}", HttpStatus.OK))
          .when(restConnector)
          .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
      endGameDeliveryService.findDeliveryDocument(
          1l, "00016017039630", MockHttpHeaders.getHeaders());
      verify(restConnector, times(1))
          .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    } catch (ReceivingException e) {
      assertTrue(false);
    }
  }

  @Test
  public void testFindDeliveryDocument_EmptyResponseScenario() {
    try {
      doReturn(new ResponseEntity<String>("", HttpStatus.OK))
          .when(restConnector)
          .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
      endGameDeliveryService.findDeliveryDocument(
          1l, "00016017039630", MockHttpHeaders.getHeaders());
      assertTrue(false);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.NOT_FOUND);
      assertEquals(
          e.getErrorResponse().getErrorCode(), ReceivingException.GDM_SEARCH_DOCUMENT_ERROR_CODE);
      assertEquals(
          e.getErrorResponse().getErrorMessage(), ReceivingException.CREATE_INSTRUCTION_NO_PO_LINE);
    }

    verify(restConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
  }

  @Test
  public void testFindDeliveryDocument_ExceptionScenario_GDM_DOWN() {
    try {
      doThrow(new ResourceAccessException("IO Error."))
          .when(restConnector)
          .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
      endGameDeliveryService.findDeliveryDocument(
          1l, "00016017039630", MockHttpHeaders.getHeaders());
      assertTrue(false);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
      assertEquals(
          e.getErrorResponse().getErrorCode(), ReceivingException.GDM_SEARCH_DOCUMENT_ERROR_CODE);
      assertEquals(e.getErrorResponse().getErrorMessage(), ReceivingException.GDM_SERVICE_DOWN);
    }

    verify(restConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
  }

  @Test
  public void testFindDeliveryDocument_ExceptionScenario() {
    try {
      doThrow(
              new RestClientResponseException(
                  "Some error.",
                  HttpStatus.INTERNAL_SERVER_ERROR.value(),
                  "",
                  null,
                  "".getBytes(),
                  Charset.forName("UTF-8")))
          .when(restConnector)
          .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
      endGameDeliveryService.findDeliveryDocument(
          1l, "00016017039630", MockHttpHeaders.getHeaders());
      assertTrue(false);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.NOT_FOUND);
      assertEquals(
          e.getErrorResponse().getErrorCode(), ReceivingException.GDM_SEARCH_DOCUMENT_ERROR_CODE);
      assertEquals(
          e.getErrorResponse().getErrorMessage(), ReceivingException.CREATE_INSTRUCTION_NO_PO_LINE);
    }

    verify(restConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
  }

  @Test
  public void testCompleteDeliveryForEndgame() throws ReceivingException {
    when(instructionRepository.countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(
            anyLong()))
        .thenReturn(0L);
    doReturn(completeDeliveryProcessor)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));

    endGameDeliveryService.completeDelivery(12345678L, false, MockHttpHeaders.getHeaders());
    verify(instructionRepository, times(1))
        .countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(anyLong());
  }

  /**
   * https://jira.walmart.com/browse/SCTNGMS-29 - Checks if the working event is published to GDM
   * for pallet receive v2 api flow
   */
  @Test
  public void testPublishNonSortWorkingEvent() {
    when(endGameLabelingService.findDeliveryMetadataByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(new DeliveryMetaData()));
    doNothing().when(endgameDeliveryStatusPublisher).publishMessage(isA(DeliveryInfo.class), any());
    when(maasTopics.getPubDeliveryStatusTopic()).thenReturn("TOPIC/RECEIVE/DELIVERYSTATUS");
    endGameDeliveryService.publishNonSortWorkingEvent(any(), DeliveryStatus.OPN);
    verify(endgameDeliveryStatusPublisher, times(1)).publishMessage(any(), any());
  }

  @Test
  public void testDoorStatusForEndgame() throws ReceivingException {

    endGameDeliveryService.getDoorStatus("200");
    verify(deliveryMetaDataService, times(1)).findDoorStatus(4321, "US", "200");
  }

  @Test
  public void processPendingDeliveryEvent() throws ReceivingException {
    Location location = new Location();
    location.setLocation("200");
    location.setMoveRequired(false);
    EventStore eventStore = getEventStoreData();
    when(configUtils.getDeliveryEventProcessor(anyString()))
        .thenReturn(endGameDeliveryEventProcessor);
    when(eventStoreService.getEventStoreByKeyStatusAndEventType(
            anyString(), any(EventTargetStatus.class), any(EventStoreType.class)))
        .thenReturn(Optional.of(eventStore));
    DeliveryMetaData deliveryMetaData =
        MockDeliveryMetaData.getDeliveryMetaData_WithReceivedQty(false);
    deliveryMetaData.setUnloadingCompleteDate(eventStore.getCreatedDate());
    when(deliveryMetaDataRepository.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(deliveryMetaData));
    endGameDeliveryService.processPendingDeliveryEvent(location);
    verify(endGameDeliveryEventProcessor, times(1)).processEvent(any());
    verify(eventStoreService, times(1)).saveEventStoreEntity(any());
  }

  @Test
  public void processPendingDeliveryEventWithNewDelivery() throws ReceivingException {
    Location location = new Location();
    location.setLocation("200");
    location.setMoveRequired(false);
    EventStore eventStore = getEventStoreData();
    when(configUtils.getDeliveryEventProcessor(anyString()))
        .thenReturn(endGameDeliveryEventProcessor);
    when(eventStoreService.getEventStoreByKeyStatusAndEventType(
            anyString(), any(EventTargetStatus.class), any(EventStoreType.class)))
        .thenReturn(Optional.of(eventStore));
    DeliveryMetaData deliveryMetaData =
        MockDeliveryMetaData.getDeliveryMetaData_WithReceivedQty(false);
    deliveryMetaData.setUnloadingCompleteDate(null);
    when(deliveryMetaDataRepository.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(deliveryMetaData));
    endGameDeliveryService.processPendingDeliveryEvent(location);
    verify(eventStoreService, times(0)).saveEventStoreEntity(any());
    verify(eventStoreService, times(0))
        .updateEventStoreEntityStatusAndLastUpdatedDateByKeyOrDeliveryNumber(
            anyString(),
            anyLong(),
            any(EventTargetStatus.class),
            any(EventStoreType.class),
            any(Date.class));
  }

  @Test
  public void testGetAsnGdmData() throws ReceivingException {
    String filePath = "../../receiving-test/src/main/resources/json/ASNShipmentDataValidPoQty.json";
    GdmAsnDeliveryResponse response = EndGameUtilsTestCase.getASNData(filePath);
    ScanEventData scanEventData = new ScanEventData();
    scanEventData.setDeliveryNumber(79102905L);
    scanEventData.setBoxIds(Collections.singletonList("Rt1627901835661834"));
    doReturn(new ResponseEntity<>(gson.toJson(response), HttpStatus.OK))
        .when(restConnector)
        .exchange(any(), any(), any(), eq(String.class));

    GdmAsnDeliveryResponse asnDeliveryResponse =
        endGameDeliveryService.getASNDataFromGDM(
            scanEventData.getDeliveryNumber(), scanEventData.getBoxIds().get(0));
    assertEquals(
        asnDeliveryResponse.getDelivery().getDeliveryNumber(),
        String.valueOf(scanEventData.getDeliveryNumber()));
    assertEquals(asnDeliveryResponse.getPacks().size(), 1);
  }

  @Test
  public void testFetchDeliveryDocumentByItemNumber() {
    try {
      endGameDeliveryService.findDeliveryDocumentByItemNumber(
          "21119003", 943037204, MockHttpHeaders.getHeaders());
      fail();
    } catch (ReceivingException exc) {
      AssertJUnit.assertEquals(HttpStatus.NOT_IMPLEMENTED, exc.getHttpStatus());
      AssertJUnit.assertEquals(ReceivingException.NOT_IMPLEMENTED_EXCEPTION, exc.getMessage());
    }
  }
}
