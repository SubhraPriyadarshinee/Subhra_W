package com.walmart.move.nim.receiving.mfc.processor;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.common.rest.SimpleRestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.helper.ProcessInitiator;
import com.walmart.move.nim.receiving.core.model.InventoryAdjustmentTO;
import com.walmart.move.nim.receiving.core.model.gdm.GDMShipmentSearchResponse;
import com.walmart.move.nim.receiving.core.model.ngr.NGRPack;
import com.walmart.move.nim.receiving.core.retry.RetryableRestConnector;
import com.walmart.move.nim.receiving.mfc.common.MFCTestUtils;
import com.walmart.move.nim.receiving.mfc.config.MFCManagedConfig;
import com.walmart.move.nim.receiving.mfc.service.MFCContainerService;
import com.walmart.move.nim.receiving.mfc.service.MFCDeliveryService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.Arrays;
import org.mockito.*;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientResponseException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class StoreNGRFinalizationEventProcessorTest extends ReceivingTestBase {

  @InjectMocks private StoreNGRFinalizationEventProcessor storeNgrFinalizationEventProcessor;

  @Mock private MFCManagedConfig mfcManagedConfig;

  @Mock private MFCDeliveryService deliveryService;

  @Mock private ProcessInitiator processInitiator;

  @Mock private MFCContainerService mfcContainerService;

  @Mock private SimpleRestConnector simpleRestConnector;

  @Mock private RetryableRestConnector restConnector;

  @Mock private AppConfig appConfig;

  private Gson gson;

  @Mock private KafkaTemplate kafkaTemplate;

  @BeforeClass
  private void init() {
    MockitoAnnotations.openMocks(this);
    gson = new Gson();
    ReflectionTestUtils.setField(deliveryService, "appConfig", appConfig);
    ReflectionTestUtils.setField(deliveryService, "gson", gson);
    ReflectionTestUtils.setField(deliveryService, "simpleRestConnector", simpleRestConnector);
    ReflectionTestUtils.setField(deliveryService, "restConnector", restConnector);
  }

  @AfterMethod
  private void resetMocks() {
    Mockito.reset(mfcManagedConfig);
    Mockito.reset(deliveryService);
    Mockito.reset(processInitiator);
    Mockito.reset(mfcContainerService);
    Mockito.reset(simpleRestConnector);
    Mockito.reset(restConnector);
    Mockito.reset(appConfig);
  }

  @Test
  public void testProcessEvent_InvalidMessage() throws ReceivingException {
    storeNgrFinalizationEventProcessor.processEvent(new InventoryAdjustmentTO());
    Mockito.verify(deliveryService, Mockito.never())
        .findDeliveryDocumentByPalletAndDelivery(any(), any());
  }

  @Test
  public void testProcessEvent_NoItems() throws ReceivingException {
    storeNgrFinalizationEventProcessor.processEvent(
        NGRPack.builder()
            .destinationNumber("100")
            .documentNumber("US")
            .documentPackNumber("1010101")
            .build());
    Mockito.verify(deliveryService, Mockito.never())
        .findDeliveryDocumentByPalletAndDelivery(any(), any());
  }

  @Test(expectedExceptions = ReceivingDataNotFoundException.class)
  public void testProcessEvent_ShipmentNotFound() throws ReceivingException {
    TenantContext.setFacilityNum(100);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setCorrelationId("test123");
    when(appConfig.getGdmBaseUrl()).thenReturn("https://gdm.base.url.net");
    when(deliveryService.getShipmentDetails(any())).thenCallRealMethod();
    when(simpleRestConnector.exchange(
            any(), any(), any(HttpEntity.class), eq(GDMShipmentSearchResponse.class)))
        .thenReturn(new ResponseEntity<>(new GDMShipmentSearchResponse(), HttpStatus.OK));
    storeNgrFinalizationEventProcessor.processEvent(
        MFCTestUtils.getNGRPack("src/test/resources/ngrFinalizationEvent/ngrEventMFCItem.json"));
  }

  @Test
  public void testProcessEvent() throws ReceivingException {
    TenantContext.setFacilityNum(100);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setCorrelationId("test123");
    when(appConfig.getGdmBaseUrl()).thenReturn("https://gdm.base.url.net");
    when(mfcManagedConfig.getDeliveryStatusForOpenDeliveries())
        .thenReturn(Arrays.asList("SCH", "ARV", "WRK", "UNLOADING_COMPLETE"));
    when(deliveryService.getShipmentDetails(any())).thenCallRealMethod();
    when(simpleRestConnector.exchange(
            any(), any(), any(HttpEntity.class), eq(GDMShipmentSearchResponse.class)))
        .thenReturn(
            new ResponseEntity<>(
                MFCTestUtils.getGDMShipmentSearchResponse(
                    "src/test/resources/ngrFinalizationEvent/shipmentDetails.json"),
                HttpStatus.OK));
    when(deliveryService.findDeliveryDocumentByPalletAndDelivery(any(), any()))
        .thenReturn(
            MFCTestUtils.getASNDocument(
                "src/test/resources/ngrFinalizationEvent/asnPalletDetails.json"));
    when(mfcContainerService.createTransientContainer(any(), any()))
        .thenReturn(
            Container.builder()
                .ssccNumber("002482608329812546")
                .deliveryNumber(10000000001758L)
                .build());
    storeNgrFinalizationEventProcessor.processEvent(
        MFCTestUtils.getNGRPack("src/test/resources/ngrFinalizationEvent/ngrEventMFCItem.json"));
    Mockito.verify(deliveryService, Mockito.times(1))
        .findDeliveryDocumentByPalletAndDelivery(any(), any());
    Mockito.verify(mfcContainerService, Mockito.times(1)).createTransientContainer(any(), any());
    Mockito.verify(mfcContainerService, Mockito.times(1)).createContainer(any(), any());
  }

  @Test
  public void testProcessEventMfcPacks() throws ReceivingException {
    TenantContext.setFacilityNum(100);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setCorrelationId("test123");
    when(appConfig.getGdmBaseUrl()).thenReturn("https://gdm.base.url.net");
    when(mfcManagedConfig.getDeliveryStatusForOpenDeliveries())
        .thenReturn(Arrays.asList("SCH", "ARV", "WRK", "UNLOADING_COMPLETE"));
    when(deliveryService.getShipmentDetails(any())).thenCallRealMethod();
    when(simpleRestConnector.exchange(
            any(), any(), any(HttpEntity.class), eq(GDMShipmentSearchResponse.class)))
        .thenReturn(
            new ResponseEntity<>(
                MFCTestUtils.getGDMShipmentSearchResponse(
                    "src/test/resources/ngrFinalizationEvent/shipmentDetails.json"),
                HttpStatus.OK));
    when(deliveryService.findDeliveryDocumentByPalletAndDelivery(any(), any()))
        .thenReturn(
            MFCTestUtils.getASNDocument(
                "src/test/resources/ngrFinalizationEvent/asnPalletDetails.json"));
    when(mfcContainerService.createTransientContainer(any(), any()))
        .thenReturn(
            Container.builder()
                .ssccNumber("002482608329812546")
                .deliveryNumber(10000000001758L)
                .build());
    storeNgrFinalizationEventProcessor.processEvent(
        MFCTestUtils.getNGRPack("src/test/resources/ngrFinalizationEvent/ngrEventMFCItem2.json"));
    Mockito.verify(deliveryService, Mockito.times(1))
        .findDeliveryDocumentByPalletAndDelivery(any(), any());
    Mockito.verify(mfcContainerService, Mockito.times(1)).createTransientContainer(any(), any());
    Mockito.verify(mfcContainerService, Mockito.times(1)).createContainer(any(), any());
    Mockito.verify(kafkaTemplate, Mockito.times(1)).send(any(Message.class));
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testProcessEvent_GetShipmentDetailsFailed() throws ReceivingException {
    TenantContext.setFacilityNum(100);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setCorrelationId("test123");
    when(appConfig.getGdmBaseUrl()).thenReturn("https://gdm.base.url.net");
    when(deliveryService.getShipmentDetails(any())).thenCallRealMethod();
    when(simpleRestConnector.exchange(
            any(), any(), any(HttpEntity.class), eq(GDMShipmentSearchResponse.class)))
        .thenReturn(new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR));
    storeNgrFinalizationEventProcessor.processEvent(
        MFCTestUtils.getNGRPack("src/test/resources/ngrFinalizationEvent/ngrEventMFCItem.json"));
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testProcessEvent_GetShipmentDetailsException() throws ReceivingException {
    TenantContext.setFacilityNum(100);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setCorrelationId("test123");
    when(appConfig.getGdmBaseUrl()).thenReturn("https://gdm.base.url.net");
    when(deliveryService.getShipmentDetails(any())).thenCallRealMethod();
    when(simpleRestConnector.exchange(
            any(), any(), any(HttpEntity.class), eq(GDMShipmentSearchResponse.class)))
        .thenThrow(
            new RestClientResponseException(
                "Error",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                null,
                "Error".getBytes(),
                null));
    storeNgrFinalizationEventProcessor.processEvent(
        MFCTestUtils.getNGRPack("src/test/resources/ngrFinalizationEvent/ngrEventMFCItem.json"));
  }

  @Test(expectedExceptions = ReceivingDataNotFoundException.class)
  public void testProcessEvent_ASNNotFound() throws ReceivingException {
    TenantContext.setFacilityNum(100);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setCorrelationId("test123");
    when(appConfig.getGdmBaseUrl()).thenReturn("https://gdm.base.url.net");
    when(deliveryService.getShipmentDetails(any())).thenCallRealMethod();
    when(deliveryService.findDeliveryDocumentByPalletAndDelivery(any(), any()))
        .thenThrow(ReceivingDataNotFoundException.class);
    when(simpleRestConnector.exchange(
            any(), any(), any(HttpEntity.class), eq(GDMShipmentSearchResponse.class)))
        .thenReturn(
            new ResponseEntity<>(
                MFCTestUtils.getGDMShipmentSearchResponse(
                    "src/test/resources/ngrFinalizationEvent/shipmentDetails.json"),
                HttpStatus.OK));
    storeNgrFinalizationEventProcessor.processEvent(
        MFCTestUtils.getNGRPack("src/test/resources/ngrFinalizationEvent/ngrEventMFCItem.json"));
    Mockito.verify(deliveryService, Mockito.times(1))
        .findDeliveryDocumentByPalletAndDelivery(any(), any());
  }
}
