package com.walmart.move.nim.receiving.sib.service;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClientException;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.message.common.FireflyEvent;
import com.walmart.move.nim.receiving.core.model.GdmDeliverySummary;
import com.walmart.move.nim.receiving.core.model.GdmTimeCriteria;
import com.walmart.move.nim.receiving.core.model.decant.DecantMessagePublishRequest;
import com.walmart.move.nim.receiving.core.model.decant.PalletScanArchiveMessage;
import com.walmart.move.nim.receiving.core.model.gdm.GdmDeliverySearchRequest;
import com.walmart.move.nim.receiving.core.model.gdm.GdmDeliverySearchResponse;
import com.walmart.move.nim.receiving.core.model.v2.ContainerScanRequest;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.DecantService;
import com.walmart.move.nim.receiving.sib.config.FireflyConfig;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class FireflyEventProcessorTest {

  private static final Long DELIVERY_NUMBER = 550478600065364L;
  private static final String ASSET_ID = "00266010606400710306";
  private static final int FACILITY_NUMBER = 266;
  private static final String FACILITY_COUNTRY_CODE = "US";
  private static final String CORRELATION_ID = "correlation-id";
  private Gson gson =
      new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();

  @Mock private ContainerService containerService;

  @Mock private FireflyConfig fireflyConfig;

  @Mock private DecantService decantService;

  @Mock private AppConfig appConfig;

  @Mock private StoreDeliveryService storeDeliveryService;

  @InjectMocks private FireflyEventProcessor fireflyEventProcessor;

  @Captor ArgumentCaptor<GdmDeliverySearchRequest> gdmRequestCaptor;

  @Captor ArgumentCaptor<List<DecantMessagePublishRequest>> decantMessageCaptor;

  @Captor ArgumentCaptor<String> assetIdCaptor;

  @Captor ArgumentCaptor<ContainerScanRequest> containerScanRequestCaptor;

  @BeforeClass
  public void init() {
    MockitoAnnotations.openMocks(this);

    TenantContext.setFacilityNum(FACILITY_NUMBER);
    TenantContext.setFacilityCountryCode(FACILITY_COUNTRY_CODE);
    TenantContext.setCorrelationId(CORRELATION_ID);
    TenantContext.setAdditionalParams(
        ReceivingConstants.USER_ID_HEADER_KEY, ReceivingConstants.USER_ID_AUTO_FINALIZED);
  }

  @BeforeMethod
  public void setup() {
    doReturn("Pallet").when(fireflyConfig).getFireflyEventEnabledAssetTypes();
    doReturn("266").when(fireflyConfig).getFireflyEventEnabledStores();
    doReturn("assetUnloaded").when(fireflyConfig).getFireflyEventEnabledEventNames();
    doReturn(15).when(appConfig).getGdmSearchPalletRequestTimeRangeInDays();
    doReturn(1).when(appConfig).getGdmSearchPalletRequestTimeToAdditionInDays();
  }

  @AfterMethod
  public void resetMocks() {
    Mockito.reset(containerService);
    Mockito.reset(fireflyConfig);
    Mockito.reset(decantService);
    Mockito.reset(storeDeliveryService);
  }

  @Test
  public void testDoProcessEvent_ignored() throws GDMRestApiClientException {
    Instant current = Instant.now();
    FireflyEvent fireflyEvent = createFireflyEvent(current.toString());

    doReturn("").when(fireflyConfig).getFireflyEventEnabledStores();

    fireflyEventProcessor.doProcessEvent(fireflyEvent);

    verify(storeDeliveryService, times(0)).searchDelivery(any());
    verify(decantService, times(0)).initiateMessagePublish(any());
    verify(containerService, times(0)).receiveContainer(any(), any());
  }

  @Test
  public void testDoProcessEvent_success() {
    Instant current = Instant.now();
    FireflyEvent fireflyEvent = createFireflyEvent(current.toString());
    fireflyEvent.setEventName("assetLoaded");
    fireflyEvent.setBusinessUnitNumber(1234);

    doReturn("assetUnloaded,assetLoaded").when(fireflyConfig).getFireflyEventEnabledEventNames();
    doReturn("*").when(fireflyConfig).getFireflyEventEnabledStores();

    GdmDeliverySearchResponse response = new GdmDeliverySearchResponse();
    GdmDeliverySummary gdmDeliverySummary1 = createGdmDeliverySummary();
    GdmDeliverySummary gdmDeliverySummary2 = createGdmDeliverySummary();
    gdmDeliverySummary2.setDeliveryNumber(123456789L);
    response.setDeliveries(Arrays.asList(gdmDeliverySummary1, gdmDeliverySummary2));

    when(storeDeliveryService.searchDelivery(any())).thenReturn(response);

    fireflyEventProcessor.doProcessEvent(fireflyEvent);

    verify(storeDeliveryService, times(1)).searchDelivery(gdmRequestCaptor.capture());
    verify(decantService, times(2)).initiateMessagePublish(decantMessageCaptor.capture());
    verify(containerService, times(1))
        .receiveContainer(assetIdCaptor.capture(), containerScanRequestCaptor.capture());

    assertGdmDeliverySearchRequest(gdmRequestCaptor.getValue(), current, false);
    assertDecantMessagePublishRequests(
        decantMessageCaptor.getValue(), DELIVERY_NUMBER.toString(), "1234", "PALLET_AUTO_RECEIVED");
    assertAssetId(assetIdCaptor.getValue());
    assertContainerScanRequest(containerScanRequestCaptor.getValue(), Boolean.TRUE);
  }

  @Test
  public void testDoProcessEvent_emptyGdmDeliverySearchResponse() throws GDMRestApiClientException {
    Instant current = Instant.now();
    Instant associationTime = current.minus(16, ChronoUnit.DAYS);
    FireflyEvent fireflyEvent = createFireflyEvent(associationTime.toString());

    GdmDeliverySearchResponse response = new GdmDeliverySearchResponse();
    response.setDeliveries(Collections.emptyList());
    when(storeDeliveryService.searchDelivery(any())).thenReturn(response);

    fireflyEventProcessor.doProcessEvent(fireflyEvent);

    verify(storeDeliveryService, times(1)).searchDelivery(gdmRequestCaptor.capture());
    verify(decantService, times(1)).initiateMessagePublish(decantMessageCaptor.capture());
    verify(containerService, times(0))
        .receiveContainer(assetIdCaptor.capture(), containerScanRequestCaptor.capture());

    assertGdmDeliverySearchRequest(gdmRequestCaptor.getValue(), current, true);
    assertDecantMessagePublishRequests(
        decantMessageCaptor.getValue(), null, "266", "PALLET_NOT_FOUND");
  }

  @Test
  public void testDoProcessEvent_NotFoundException() throws GDMRestApiClientException {
    Instant current = Instant.now();
    FireflyEvent fireflyEvent =
        createFireflyEvent(current.toString().substring(1)); // invalid associationTime

    ReceivingDataNotFoundException notFoundException =
        new ReceivingDataNotFoundException(
            ReceivingException.DELIVERY_NOT_FOUND,
            ReceivingException.GDM_SEARCH_DELIVERY_ERROR_CODE);
    doThrow(notFoundException).when(storeDeliveryService).searchDelivery(any());

    fireflyEventProcessor.doProcessEvent(fireflyEvent);

    verify(storeDeliveryService, times(1)).searchDelivery(gdmRequestCaptor.capture());
    verify(decantService, times(1)).initiateMessagePublish(decantMessageCaptor.capture());
    verify(containerService, times(0))
        .receiveContainer(assetIdCaptor.capture(), containerScanRequestCaptor.capture());

    assertGdmDeliverySearchRequest(gdmRequestCaptor.getValue(), current, true);
    assertDecantMessagePublishRequests(
        decantMessageCaptor.getValue(), null, "266", "PALLET_NOT_FOUND");
  }

  @Test
  public void testDoProcessEvent_InternalException() throws GDMRestApiClientException {
    Instant current = Instant.now();
    FireflyEvent fireflyEvent = createFireflyEvent(current.toString());

    ReceivingInternalException internalException =
        new ReceivingInternalException(
            ReceivingException.GDM_SERVICE_DOWN, ReceivingException.GDM_SEARCH_DELIVERY_ERROR_CODE);
    doThrow(internalException).when(storeDeliveryService).searchDelivery(any());

    try {
      fireflyEventProcessor.doProcessEvent(fireflyEvent);
      fail("expected exception was not occurred.");
    } catch (Exception ex) {
      assertTrue(ex instanceof ReceivingInternalException);
      assertEquals("Received unexpected response from GDM", ex.getMessage());
      assertEquals(
          ExceptionCodes.UNEXPECTED_FIREFLY_EVENT_ERROR,
          ((ReceivingInternalException) ex).getErrorCode());
    }

    verify(storeDeliveryService, times(1)).searchDelivery(gdmRequestCaptor.capture());
    verify(decantService, times(0)).initiateMessagePublish(decantMessageCaptor.capture());
    verify(containerService, times(0))
        .receiveContainer(assetIdCaptor.capture(), containerScanRequestCaptor.capture());

    assertGdmDeliverySearchRequest(gdmRequestCaptor.getValue(), current, false);
  }

  @Test
  public void testDoProcessEvent_exceptionWhenReceiveContainer() throws GDMRestApiClientException {
    Instant current = Instant.now();
    FireflyEvent fireflyEvent = createFireflyEvent(current.toString());
    fireflyEvent.setTempComplianceInd('N');

    GdmDeliverySearchResponse response = new GdmDeliverySearchResponse();
    GdmDeliverySummary gdmDeliverySummary = createGdmDeliverySummary();
    response.setDeliveries(Arrays.asList(gdmDeliverySummary));

    when(storeDeliveryService.searchDelivery(any())).thenReturn(response);
    doThrow(RuntimeException.class).when(containerService).receiveContainer(any(), any());

    try {
      fireflyEventProcessor.doProcessEvent(fireflyEvent);
      fail("expected exception was not occurred.");
    } catch (Exception ex) {
      assertTrue(ex instanceof RuntimeException);
    }

    verify(storeDeliveryService, times(1)).searchDelivery(gdmRequestCaptor.capture());
    verify(decantService, times(1)).initiateMessagePublish(decantMessageCaptor.capture());
    verify(containerService, times(1))
        .receiveContainer(assetIdCaptor.capture(), containerScanRequestCaptor.capture());

    assertGdmDeliverySearchRequest(gdmRequestCaptor.getValue(), current, false);
    assertDecantMessagePublishRequests(
        decantMessageCaptor.getValue(),
        DELIVERY_NUMBER.toString(),
        "266",
        "RECEIVED_FIREFLY_SIGNAL");
    assertAssetId(assetIdCaptor.getValue());
    assertContainerScanRequest(containerScanRequestCaptor.getValue(), Boolean.FALSE);
  }

  @Test
  public void testDoProcessEvent_alreadyReceivedContainer() throws GDMRestApiClientException {
    Instant current = Instant.now();
    FireflyEvent fireflyEvent = createFireflyEvent(current.toString());
    fireflyEvent.setEventName("assetLoaded");
    fireflyEvent.setBusinessUnitNumber(1234);

    doReturn("assetUnloaded,assetLoaded").when(fireflyConfig).getFireflyEventEnabledEventNames();
    doReturn("*").when(fireflyConfig).getFireflyEventEnabledStores();

    String sampleContainerDTO =
        "{\"trackingId\":\"123\",\"createUser\":\"j0p00t1\",\"isAudited\":false,\"hasChildContainers\":false}";
    doReturn(sampleContainerDTO).when(containerService).receiveContainer(any(), any());

    GdmDeliverySearchResponse response = new GdmDeliverySearchResponse();
    GdmDeliverySummary gdmDeliverySummary1 = createGdmDeliverySummary();
    GdmDeliverySummary gdmDeliverySummary2 = createGdmDeliverySummary();
    gdmDeliverySummary2.setDeliveryNumber(123456789L);
    response.setDeliveries(Arrays.asList(gdmDeliverySummary1, gdmDeliverySummary2));

    when(storeDeliveryService.searchDelivery(any())).thenReturn(response);

    fireflyEventProcessor.doProcessEvent(fireflyEvent);

    verify(storeDeliveryService, times(1)).searchDelivery(gdmRequestCaptor.capture());
    verify(decantService, times(2)).initiateMessagePublish(decantMessageCaptor.capture());
    verify(containerService, times(1))
        .receiveContainer(assetIdCaptor.capture(), containerScanRequestCaptor.capture());

    assertGdmDeliverySearchRequest(gdmRequestCaptor.getValue(), current, false);
    assertDecantMessagePublishRequests(
        decantMessageCaptor.getValue(),
        DELIVERY_NUMBER.toString(),
        "1234",
        "PALLET_DUPLICATE_SCAN");
    assertAssetId(assetIdCaptor.getValue());
    assertContainerScanRequest(containerScanRequestCaptor.getValue(), Boolean.TRUE);
  }

  private FireflyEvent createFireflyEvent(String associationTime) {
    return FireflyEvent.builder()
        .assetId(ASSET_ID)
        .associationTimeEpoch(1704289253000L)
        .associationTime(associationTime)
        .assetType("Pallet")
        .eventName("assetUnloaded")
        .businessUnitNumber(266)
        .bannerCode("A1")
        .bannerDesc("WM Supercenter")
        .eventTime(Instant.now().toString())
        .tempComplianceInd('Y')
        .build();
  }

  private GdmDeliverySummary createGdmDeliverySummary() {
    GdmDeliverySummary gdmDeliverySummary = new GdmDeliverySummary();
    gdmDeliverySummary.setDeliveryNumber(DELIVERY_NUMBER);
    return gdmDeliverySummary;
  }

  /**
   * isMoreOlder means that the associationTime in event is more than 15 days older than the current
   * time
   */
  private void assertGdmDeliverySearchRequest(
      GdmDeliverySearchRequest gdmDeliverySearchRequest,
      Instant expectedTimeCriteriaFrom,
      boolean isMoreOlder) {
    assertEquals(ASSET_ID, gdmDeliverySearchRequest.getPalletNumber());
    List<GdmTimeCriteria> timeCriteria = gdmDeliverySearchRequest.getTimeCriteria();
    assertNotNull(timeCriteria);
    assertEquals(1, timeCriteria.size());
    GdmTimeCriteria gdmTimeCriteria = timeCriteria.get(0);
    assertEquals("scheduled", gdmTimeCriteria.getType());
    if (isMoreOlder) {
      assertEquals(gdmTimeCriteria.getFrom(), gdmTimeCriteria.getTo().minus(16, ChronoUnit.DAYS));
    } else {
      assertEquals(expectedTimeCriteriaFrom, gdmTimeCriteria.getFrom());
    }
    Instant timeCriteriaTo = gdmTimeCriteria.getTo();
    Instant current = Instant.now();
    assertEquals(-1, current.compareTo(timeCriteriaTo));
  }

  private void assertDecantMessagePublishRequests(
      List<DecantMessagePublishRequest> decantMessagePublishRequests,
      String deliveryNumber,
      String storeNumber,
      String messageType) {
    assertNotNull(decantMessagePublishRequests);
    assertEquals(1, decantMessagePublishRequests.size());
    DecantMessagePublishRequest request = decantMessagePublishRequests.get(0);
    assertEquals("storeAppMetrics", request.getScenario());

    Map<String, String> additionalHeaders = request.getAdditionalHeaders();
    assertEquals(2, additionalHeaders.size());
    assertEquals("RECEIVING_APP", additionalHeaders.get("requestOriginator"));
    assertEquals("PALLET_SCAN", additionalHeaders.get("eventType"));

    String message = request.getMessage();
    PalletScanArchiveMessage palletScanArchiveMessage =
        gson.fromJson(message, PalletScanArchiveMessage.class);
    assertEquals(ASSET_ID, palletScanArchiveMessage.getScannedTrackingId());
    assertEquals("AutoFinalized", palletScanArchiveMessage.getScannedBy());
    assertEquals(deliveryNumber, palletScanArchiveMessage.getDeliveryNumber());
    assertNotNull(palletScanArchiveMessage.getScannedAt());
    assertEquals(storeNumber, palletScanArchiveMessage.getStoreNumber());
    assertEquals(messageType, palletScanArchiveMessage.getType());
  }

  private void assertAssetId(String assetId) {
    assertEquals(ASSET_ID, assetId);
  }

  private void assertContainerScanRequest(
      ContainerScanRequest containerScanRequest, Boolean isTempCompliance) {
    assertEquals(DELIVERY_NUMBER, containerScanRequest.getDeliveryNumber());

    assertNotNull(containerScanRequest.getMiscInfo());
    Map<String, Object> miscInfo = containerScanRequest.getMiscInfo();
    assertEquals(2, miscInfo.size());
    assertTrue((Boolean) miscInfo.get(ReceivingConstants.IS_RECEIVED_THROUGH_AUTOMATED_SIGNAL));
    assertEquals(isTempCompliance, (Boolean) miscInfo.get(ReceivingConstants.IS_TEMP_COMPLIANCE));
  }
}
