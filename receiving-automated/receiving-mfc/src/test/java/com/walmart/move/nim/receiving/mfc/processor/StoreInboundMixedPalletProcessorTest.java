package com.walmart.move.nim.receiving.mfc.processor;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.KEY;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.SHIPMENT_ADDED;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.helper.ProcessInitiator;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.message.common.ReceivingEvent;
import com.walmart.move.nim.receiving.core.model.decant.DecantMessagePublishRequest;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ASNDocument;
import com.walmart.move.nim.receiving.core.service.AsyncPersister;
import com.walmart.move.nim.receiving.core.service.DecantService;
import com.walmart.move.nim.receiving.mfc.common.MFCConstant;
import com.walmart.move.nim.receiving.mfc.common.MFCTestUtils;
import com.walmart.move.nim.receiving.mfc.config.MFCManagedConfig;
import com.walmart.move.nim.receiving.mfc.message.publisher.ShipmentArrivalPublisher;
import com.walmart.move.nim.receiving.mfc.model.common.QuantityType;
import com.walmart.move.nim.receiving.mfc.model.mixedpallet.MixedPalletAdjustmentTO;
import com.walmart.move.nim.receiving.mfc.model.mixedpallet.PalletItem;
import com.walmart.move.nim.receiving.mfc.model.mixedpallet.StockQuantityChange;
import com.walmart.move.nim.receiving.mfc.service.MFCDeliveryService;
import com.walmart.move.nim.receiving.mfc.service.MixedPalletRejectService;
import com.walmart.move.nim.receiving.mfc.transformer.NGRShipmentTransformer;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.mockito.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class StoreInboundMixedPalletProcessorTest extends ReceivingTestBase {

  @InjectMocks private StoreInboundMixedPalletProcessor storeInboundMixedPalletProcessor;
  @Mock private MFCDeliveryService deliveryService;

  @Mock private DecantService decantService;

  @Spy private NGRShipmentTransformer ngrShipmentTransformer;

  @Mock private ShipmentArrivalPublisher kafkaShipmentArrivalPublisher;

  @Mock private ProcessInitiator processInitiator;;

  @Mock private MFCManagedConfig mfcManagedConfig;
  @Mock private MixedPalletRejectService mixedPalletRejectService;
  @Mock private AsyncPersister asyncPersister;
  private Gson gson;
  private String PREVIOUS_STATE = "PENDING";
  private int multiplier = 1;

  @BeforeClass
  private void init() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(
        storeInboundMixedPalletProcessor, "deliveryService", deliveryService);
    ReflectionTestUtils.setField(mixedPalletRejectService, "decantService", decantService);
    ReflectionTestUtils.setField(
        mixedPalletRejectService, "mixedPalletRejectScenario", "mixedPalletRemoval");
    ReflectionTestUtils.setField(
        storeInboundMixedPalletProcessor, "shipmentArrivalEventType", "ARRIVED");
    gson =
        new GsonBuilder()
            .serializeNulls()
            .registerTypeAdapter(Date.class, new GsonUTCDateAdapter("yyyy-MM-dd'T'HH:mm:ss.SSSZ"))
            .create();
    ReflectionTestUtils.setField(mixedPalletRejectService, "gson", gson);
    ReflectionTestUtils.setField(mixedPalletRejectService, "asyncPersister", asyncPersister);
    ReflectionTestUtils.setField(mixedPalletRejectService, "mfcManagedConfig", mfcManagedConfig);

    TenantContext.setFacilityNum(5504);
    TenantContext.setFacilityCountryCode("US");
  }

  @AfterMethod
  private void resetMocks() {
    Mockito.reset(decantService);
    Mockito.reset(deliveryService);
    Mockito.reset(mfcManagedConfig);
    Mockito.reset(processInitiator);
    Mockito.reset(mixedPalletRejectService);
  }

  @Test
  public void testHandleMixedPalletOperation_HappyPath() {
    DeliveryUpdateMessage deliveryUpdateMessage =
        DeliveryUpdateMessage.builder()
            .eventType(SHIPMENT_ADDED)
            .deliveryNumber("55040153")
            .shipmentDocumentId("TESTasdc201360305504_20220707_6030_DC_US")
            .build();

    ASNDocument asnDocument =
        MFCTestUtils.getASNDocument(
            "../../receiving-test/src/main/resources/json/mfc/ASNDocument.json");
    when(deliveryService.findMixedContainerFromASN(anyLong(), anyString())).thenReturn(asnDocument);
    when(mfcManagedConfig.getMixedPalletCurrentState()).thenReturn(MFCConstant.REJECTED);
    when(mfcManagedConfig.getMixedPalletPreviousState()).thenReturn(MFCConstant.PENDING);
    when(mfcManagedConfig.getMixedPalletReasonCode()).thenReturn(MFCConstant.REJECTED);
    when(mfcManagedConfig.getMixedPalletRejectLocation()).thenReturn(MFCConstant.UNKNOWN);
    when(mfcManagedConfig.getMixedPalletRequestOriginator())
        .thenReturn(MFCConstant.ATLAS_INVENTORY);
    when(mfcManagedConfig.getMixedPalletRemovalEvent()).thenReturn("INVENTORY_REMOVAL");
    when(mfcManagedConfig.getMixedPalletRejectMultiplier()).thenReturn(multiplier);
    when(mfcManagedConfig.getMixedPalletRejectReasonDesc())
        .thenReturn(MFCConstant.REASON_DESC_NOT_MFC);
    doCallRealMethod().when(mixedPalletRejectService).processMixedPalletReject(any(), any());
    ArgumentCaptor<List<DecantMessagePublishRequest>> decantMessagePublishRequestArgumentCaptor =
        ArgumentCaptor.forClass(List.class);
    storeInboundMixedPalletProcessor.handleMixedPalletOperation(deliveryUpdateMessage);
    verify(deliveryService, times(1)).findMixedContainerFromASN(anyLong(), anyString());
    verify(decantService, times(1))
        .initiateMessagePublish(decantMessagePublishRequestArgumentCaptor.capture());
    List<DecantMessagePublishRequest> decantMessagePublishRequests =
        decantMessagePublishRequestArgumentCaptor.getValue();
    assertNotNull(decantMessagePublishRequests);
    assertEquals(decantMessagePublishRequests.size(), 3);
    decantMessagePublishRequests.forEach(
        decantMessagePublishRequest -> {
          MixedPalletAdjustmentTO mixedPalletAdjustmentTO =
              gson.fromJson(
                  decantMessagePublishRequest.getMessage(), MixedPalletAdjustmentTO.class);
          assertEquals(mixedPalletAdjustmentTO.getContainerId(), "300008760310160015");
          String key = decantMessagePublishRequest.getAdditionalHeaders().get(KEY);
          assertNotNull(mixedPalletAdjustmentTO.getItems());
          assertEquals(mixedPalletAdjustmentTO.getItems().size(), 1);
          PalletItem palletItem = mixedPalletAdjustmentTO.getItems().get(0);
          assertEquals(palletItem.getPreviousState(), PREVIOUS_STATE);
          assertEquals(palletItem.getQuantityUom(), "EA");
          StockQuantityChange stockQuantityChange = palletItem.getStockStateChange().get(0);
          assertEquals(
              stockQuantityChange.getReasonCode(), QuantityType.REJECTED.getType().toUpperCase());
          assertEquals(stockQuantityChange.getLocation(), "UNKNOWN");
          switch (key) {
            case "55040153_166556116030020148_00025000044922":
              assertEquals(palletItem.getGtin(), "00025000044922");
              assertEquals(stockQuantityChange.getQuantity().intValue(), multiplier * 54);
              break;
            case "55040153_166556116030020149_00786162150004":
              assertEquals(palletItem.getGtin(), "00786162150004");
              assertEquals(stockQuantityChange.getQuantity().intValue(), multiplier * 54);
              break;
            case "55040153_166556116030020150_00070842063204":
              assertEquals(palletItem.getGtin(), "00070842063204");
              assertEquals(stockQuantityChange.getQuantity().intValue(), multiplier * 39);
              break;
          }
        });
  }

  @Test
  public void testHandleMixedPalletOperation_WithMixedItems() {
    DeliveryUpdateMessage deliveryUpdateMessage =
        DeliveryUpdateMessage.builder()
            .eventType(SHIPMENT_ADDED)
            .deliveryNumber("55040574")
            .shipmentDocumentId("NI-22460355504_20220922_6035_DC_US")
            .build();

    ASNDocument asnDocument =
        MFCTestUtils.getASNDocument(
            "../../receiving-test/src/main/resources/json/mfc/ASNDocumentWithMixedItems.json");
    when(deliveryService.findMixedContainerFromASN(anyLong(), anyString())).thenReturn(asnDocument);
    doCallRealMethod().when(mixedPalletRejectService).processMixedPalletReject(any(), any());
    when(mfcManagedConfig.getMixedPalletCurrentState()).thenReturn(MFCConstant.REJECTED);
    when(mfcManagedConfig.getMixedPalletPreviousState()).thenReturn(MFCConstant.PENDING);
    when(mfcManagedConfig.getMixedPalletReasonCode()).thenReturn(MFCConstant.REJECTED);
    when(mfcManagedConfig.getMixedPalletRejectLocation()).thenReturn(MFCConstant.UNKNOWN);
    when(mfcManagedConfig.getMixedPalletRequestOriginator())
        .thenReturn(MFCConstant.ATLAS_INVENTORY);
    when(mfcManagedConfig.getMixedPalletRemovalEvent()).thenReturn("INVENTORY_REMOVAL");
    when(mfcManagedConfig.getMixedPalletRejectMultiplier()).thenReturn(multiplier);
    when(mfcManagedConfig.getMixedPalletRejectReasonDesc())
        .thenReturn(MFCConstant.REASON_DESC_NOT_MFC);
    ArgumentCaptor<List<DecantMessagePublishRequest>> decantMessagePublishRequestArgumentCaptor =
        ArgumentCaptor.forClass(List.class);
    storeInboundMixedPalletProcessor.handleMixedPalletOperation(deliveryUpdateMessage);
    verify(deliveryService, times(1)).findMixedContainerFromASN(anyLong(), anyString());
    verify(decantService, times(1))
        .initiateMessagePublish(decantMessagePublishRequestArgumentCaptor.capture());
    List<DecantMessagePublishRequest> decantMessagePublishRequests =
        decantMessagePublishRequestArgumentCaptor.getValue();
    assertNotNull(decantMessagePublishRequests);
    assertEquals(decantMessagePublishRequests.size(), 1);
    DecantMessagePublishRequest decantMessagePublishRequest = decantMessagePublishRequests.get(0);
    MixedPalletAdjustmentTO mixedPalletAdjustmentTO =
        gson.fromJson(decantMessagePublishRequest.getMessage(), MixedPalletAdjustmentTO.class);
    assertEquals(mixedPalletAdjustmentTO.getContainerId(), "120000000000000046");
    String key = decantMessagePublishRequest.getAdditionalHeaders().get(KEY);
    assertNotNull(mixedPalletAdjustmentTO.getItems());
    assertEquals(mixedPalletAdjustmentTO.getItems().size(), 1);
    PalletItem palletItem = mixedPalletAdjustmentTO.getItems().get(0);
    assertEquals(palletItem.getPreviousState(), PREVIOUS_STATE);
    assertEquals(palletItem.getQuantityUom(), "EA");
    StockQuantityChange stockQuantityChange = palletItem.getStockStateChange().get(0);
    assertEquals(
        stockQuantityChange.getReasonCode(), QuantityType.REJECTED.getType().toUpperCase());
    assertEquals(stockQuantityChange.getLocation(), "UNKNOWN");
    assertEquals(key, "55040574_120000000000000002_00078742351926");
    assertEquals(palletItem.getGtin(), "00078742351926");
    assertEquals(stockQuantityChange.getQuantity().intValue(), multiplier * 5);
  }

  @Test
  public void testProcessEvent_WhenEventIsNotProcessable() throws ReceivingException {
    DeliveryUpdateMessage deliveryUpdateMessage = new DeliveryUpdateMessage();
    storeInboundMixedPalletProcessor.processEvent(deliveryUpdateMessage);
    verifyNoInteractions(processInitiator);
  }

  @Test
  public void testProcessEvent_WhenEventIsNotEligibleForMixedPalletProcessing()
      throws ReceivingException {
    DeliveryUpdateMessage deliveryUpdateMessage = new DeliveryUpdateMessage();
    deliveryUpdateMessage.setShipmentDocumentType("SomeDocumentType");
    deliveryUpdateMessage.setEventType("DISPATCHED");

    when(mfcManagedConfig.getCorrectionalInvoiceDocumentType())
        .thenReturn(Arrays.asList("DocumentType1", "DocumentType2"));
    when(mfcManagedConfig.getCorrectionalInvoiceTriggerEvent()).thenReturn("SomeTriggerEvent");

    storeInboundMixedPalletProcessor.processEvent(deliveryUpdateMessage);

    verifyNoInteractions(processInitiator);
  }

  @Test
  public void testProcessEvent_WhenEventIsEligibleForMixedPalletProcessing()
      throws ReceivingException {
    DeliveryUpdateMessage deliveryUpdateMessage = new DeliveryUpdateMessage();
    deliveryUpdateMessage.setShipmentDocumentType("SomeDocumentType");
    deliveryUpdateMessage.setEventType("ARRIVED");

    when(mfcManagedConfig.getCorrectionalInvoiceDocumentType())
        .thenReturn(Arrays.asList("DocumentType1", "DocumentType2"));
    when(mfcManagedConfig.getCorrectionalInvoiceTriggerEvent()).thenReturn("SomeTriggerEvent");
    when(deliveryService.getGDMData(any(DeliveryUpdateMessage.class)))
        .thenReturn(
            MFCTestUtils.getDeliveryDocument(
                "../../receiving-test/src/main/resources/json/mfc/DeliveryDoc.json"));
    doNothing().when(processInitiator).initiateProcess(any(ReceivingEvent.class), anyMap());

    storeInboundMixedPalletProcessor.processEvent(deliveryUpdateMessage);

    verify(processInitiator).initiateProcess(any(ReceivingEvent.class), anyMap());
  }

  @Test
  public void testProcessEvent_DSDDelivery() throws ReceivingException {
    DeliveryUpdateMessage deliveryUpdateMessage = new DeliveryUpdateMessage();
    deliveryUpdateMessage.setShipmentDocumentType("SomeDocumentType");
    deliveryUpdateMessage.setEventType("ARRIVED");

    when(mfcManagedConfig.getCorrectionalInvoiceDocumentType()).thenReturn(Collections.emptyList());
    when(deliveryService.getGDMData(any(DeliveryUpdateMessage.class)))
        .thenReturn(
            MFCTestUtils.getDeliveryDocument(
                "../../receiving-test/src/main/resources/json/mfc/DeliveryDocVendor.json"));
    when(mfcManagedConfig.getCorrectionalInvoiceTriggerEvent()).thenReturn("SomeTriggerEvent");
    doNothing().when(processInitiator).initiateProcess(any(ReceivingEvent.class), anyMap());

    storeInboundMixedPalletProcessor.processEvent(deliveryUpdateMessage);
    verify(deliveryService, never()).findMixedContainerFromASN(any(), any());
    verify(processInitiator, never()).initiateProcess(any(ReceivingEvent.class), anyMap());
  }
}
