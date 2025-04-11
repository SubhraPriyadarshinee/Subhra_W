package com.walmart.move.nim.receiving.mfc.processor;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.MANUAL_FINALISE_DELIVERY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.google.gson.JsonArray;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.helper.ProcessInitiator;
import com.walmart.move.nim.receiving.core.message.common.ReceivingEvent;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ASNDocument;
import com.walmart.move.nim.receiving.mfc.common.MFCTestUtils;
import com.walmart.move.nim.receiving.mfc.common.PalletType;
import com.walmart.move.nim.receiving.mfc.config.MFCManagedConfig;
import com.walmart.move.nim.receiving.mfc.service.MFCDeliveryMetadataService;
import com.walmart.move.nim.receiving.mfc.service.MFCDeliveryService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import java.util.*;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class StoreDeliveryUnloadingProcessorTest extends ReceivingTestBase {

  @InjectMocks private StoreDeliveryUnloadingProcessor storeDeliveryUnloadingProcessor;

  @Mock private ProcessInitiator processInitiator;
  @Mock private MFCDeliveryService mfcDeliveryService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Mock private MFCDeliveryMetadataService mfcDeliveryMetadataService;

  @Mock private MFCManagedConfig mfcManagedConfig;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setCorrelationId(UUID.randomUUID().toString());
    TenantContext.setFacilityNum(12345);
    TenantContext.setFacilityCountryCode("US");

    when(mfcManagedConfig.getStorePalletCreateEnabledFacilities())
        .thenReturn(getStorePalletCreateEnabled("5501"));
  }

  private List<String> getStorePalletCreateEnabled(String source) {
    List<String> sourceCode = new ArrayList<>();
    sourceCode.add(source);
    return sourceCode;
  }

  private ASNDocument getStore() {
    ASNDocument asnDocument =
        MFCTestUtils.getASNDocument(
            "../../receiving-test/src/main/resources/json/mfc/ASNDocumentWithMulitplePacks.json");
    return asnDocument;
  }

  private ASNDocument getASNCCMSource() {
    ASNDocument asnDocument =
        MFCTestUtils.getASNDocument(
            "../../receiving-test/src/main/resources/json/mfc/ASNDocumentWithMulitplePacksSource.json");
    return asnDocument;
  }

  @AfterMethod
  public void resetMocks() {
    Mockito.reset(mfcDeliveryService);
    Mockito.reset(processInitiator);
    reset(tenantSpecificConfigReader);
    reset(mfcDeliveryMetadataService);
    reset(mfcManagedConfig);
  }

  @Test
  public void testUnloadingProcessingRegularUnloading() {
    when(mfcDeliveryService.getShipmentDataFromGDM(any(), any())).thenReturn(getStore());
    doNothing().when(processInitiator).initiateProcess(any(), any());
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(any())).thenReturn(false);
    JsonArray jsonElements = new JsonArray();
    jsonElements.add(PalletType.MFC.name());
    jsonElements.add(PalletType.STORE.name());
    when(tenantSpecificConfigReader.getCcmConfigValueAsJson(anyString(), anyString()))
        .thenReturn(jsonElements);
    storeDeliveryUnloadingProcessor.doProcess(new DeliveryInfo());

    ArgumentCaptor<ReceivingEvent> receivingEventToShortageProcessor =
        ArgumentCaptor.forClass(ReceivingEvent.class);
    verify(processInitiator, times(1))
        .initiateProcess(receivingEventToShortageProcessor.capture(), any());
    verify(mfcDeliveryService, times(1)).getShipmentDataFromGDM(any(), any());

    String asn = receivingEventToShortageProcessor.getValue().getPayload();
    ASNDocument asnDocument = JacksonParser.convertJsonToObject(asn, ASNDocument.class);
    Assert.assertEquals(asnDocument.getPacks().size(), 4);
  }

  @Test
  public void testUnloadingProcessingStoreAutoInitializationEnabled() {
    when(mfcDeliveryService.getShipmentDataFromGDM(any(), any())).thenReturn(getStore());
    doNothing().when(processInitiator).initiateProcess(any(), any());
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(any())).thenReturn(true);
    JsonArray jsonElements = new JsonArray();
    jsonElements.add(PalletType.MFC.name());
    when(tenantSpecificConfigReader.getCcmConfigValueAsJson(anyString(), anyString()))
        .thenReturn(jsonElements);
    storeDeliveryUnloadingProcessor.doProcess(new DeliveryInfo());

    ArgumentCaptor<ReceivingEvent> receivingEventToShortageProcessor =
        ArgumentCaptor.forClass(ReceivingEvent.class);
    verify(processInitiator, times(1))
        .initiateProcess(receivingEventToShortageProcessor.capture(), any());
    verify(mfcDeliveryService, times(1)).getShipmentDataFromGDM(any(), any());

    String asn = receivingEventToShortageProcessor.getValue().getPayload();
    ASNDocument asnDocument = JacksonParser.convertJsonToObject(asn, ASNDocument.class);
    Assert.assertEquals(asnDocument.getPacks().size(), 2);
  }

  @Test
  public void testUnloadingProcessingContainerFilterTypeMFCReceivedPallet() {
    when(mfcDeliveryService.getShipmentDataFromGDM(any(), any())).thenReturn(getASNCCMSource());
    doNothing().when(processInitiator).initiateProcess(any(), any());
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(any())).thenReturn(false);
    JsonArray jsonElements = new JsonArray();
    jsonElements.add(PalletType.MFC.name());
    jsonElements.add(PalletType.STORE.name());
    when(tenantSpecificConfigReader.getCcmConfigValueAsJson(anyString(), anyString()))
        .thenReturn(jsonElements);
    storeDeliveryUnloadingProcessor.doProcess(new DeliveryInfo());
    verify(processInitiator, times(1)).initiateProcess(any(), any());
    verify(mfcDeliveryService, times(1)).getShipmentDataFromGDM(any(), any());
  }

  @Test
  public void testUnloadingProcessing() {
    DeliveryInfo delivery = new DeliveryInfo();
    delivery.setAction(MANUAL_FINALISE_DELIVERY);
    when(mfcDeliveryService.getShipmentDataFromGDM(any(), any())).thenReturn(getStore());
    doNothing().when(processInitiator).initiateProcess(any(), any());
    storeDeliveryUnloadingProcessor.doProcess(delivery);
    verify(processInitiator, times(1)).initiateProcess(any(), any());
    verify(mfcDeliveryService, times(1)).getShipmentDataFromGDM(any(), any());
  }

  @Test
  public void testManualFinaliseFlow() {
    DeliveryInfo delivery = new DeliveryInfo();
    delivery.setAction(MANUAL_FINALISE_DELIVERY);
    DeliveryMetaData deliveryMetaData = new DeliveryMetaData();
    when(mfcDeliveryMetadataService.findByDeliveryNumber(
            String.valueOf(delivery.getDeliveryNumber())))
        .thenReturn(Optional.of(deliveryMetaData));
    when(mfcDeliveryService.getShipmentDataFromGDM(any(), any())).thenReturn(getStore());
    doNothing().when(processInitiator).initiateProcess(any(), any());
    storeDeliveryUnloadingProcessor.doProcess(delivery);
    verify(mfcDeliveryMetadataService).save(deliveryMetaData);
    verify(mfcDeliveryService, times(1)).getShipmentDataFromGDM(delivery.getDeliveryNumber(), null);
    verify(processInitiator, times(1)).initiateProcess(any(), any());
  }

  @Test
  public void testManualFinaliseForStorePalletCreateEnabledFacilities() {
    DeliveryInfo delivery = new DeliveryInfo();
    DeliveryMetaData deliveryMetaData = new DeliveryMetaData();
    deliveryMetaData.setDeliveryStatus(DeliveryStatus.WRK);

    when(mfcManagedConfig.getStorePalletCreateEnabledFacilities())
        .thenReturn(getStorePalletCreateEnabled("6035"));
    when(mfcDeliveryMetadataService.findByDeliveryNumber(
            String.valueOf(delivery.getDeliveryNumber())))
        .thenReturn(Optional.of(deliveryMetaData));
    when(mfcDeliveryService.getShipmentDataFromGDM(any(), any())).thenReturn(getStore());

    doNothing().when(processInitiator).initiateProcess(any(), any());
    storeDeliveryUnloadingProcessor.doProcess(delivery);
    verify(mfcDeliveryMetadataService).save(deliveryMetaData);
    verify(mfcDeliveryService, times(1)).getShipmentDataFromGDM(delivery.getDeliveryNumber(), null);
    verify(processInitiator, times(1)).initiateProcess(any(), any());
  }

  @Test
  public void testDoExecute() {
    when(mfcDeliveryService.getShipmentDataFromGDM(any(), any())).thenReturn(getStore());
    doNothing().when(processInitiator).initiateProcess(any(), any());
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(any())).thenReturn(false);
    JsonArray jsonElements = new JsonArray();
    jsonElements.add(PalletType.MFC.name());
    jsonElements.add(PalletType.STORE.name());
    when(tenantSpecificConfigReader.getCcmConfigValueAsJson(anyString(), anyString()))
        .thenReturn(jsonElements);

    storeDeliveryUnloadingProcessor.doExecute(
        ReceivingEvent.builder()
            .payload(JacksonParser.writeValueAsString(new DeliveryInfo()))
            .build());

    ArgumentCaptor<ReceivingEvent> receivingEventToShortageProcessor =
        ArgumentCaptor.forClass(ReceivingEvent.class);
    verify(processInitiator, times(1))
        .initiateProcess(receivingEventToShortageProcessor.capture(), any());
    verify(mfcDeliveryService, times(1)).getShipmentDataFromGDM(any(), any());

    String asn = receivingEventToShortageProcessor.getValue().getPayload();
    ASNDocument asnDocument = JacksonParser.convertJsonToObject(asn, ASNDocument.class);
    Assert.assertEquals(asnDocument.getPacks().size(), 4);
  }

  @Test
  public void testIsAsync() {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
    Assert.assertFalse(storeDeliveryUnloadingProcessor.isAsync());
  }

  @Test
  public void testUnloadingProcessingDSDDelivery() {
    when(mfcDeliveryService.getShipmentDataFromGDM(any(), any()))
        .thenReturn(
            MFCTestUtils.getASNDocument(
                "../../receiving-test/src/main/resources/json/mfc/ASNDeliveryShipmentVendor.json"));
    doNothing().when(processInitiator).initiateProcess(any(), any());
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(any())).thenReturn(true);
    JsonArray jsonElements = new JsonArray();
    jsonElements.add(PalletType.MFC.name());
    when(tenantSpecificConfigReader.getCcmConfigValueAsJson(anyString(), anyString()))
        .thenReturn(jsonElements);
    storeDeliveryUnloadingProcessor.doProcess(new DeliveryInfo());

    verify(processInitiator, never()).initiateProcess(any(), any());
    verify(mfcDeliveryService, times(1)).getShipmentDataFromGDM(any(), any());
  }
}
