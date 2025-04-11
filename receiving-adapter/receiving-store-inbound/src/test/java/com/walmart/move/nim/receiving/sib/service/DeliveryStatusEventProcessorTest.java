package com.walmart.move.nim.receiving.sib.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.DocumentType;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.SourceType;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ASNDocument;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.sib.config.SIBManagedConfig;
import com.walmart.move.nim.receiving.sib.repositories.EventRepository;
import com.walmart.move.nim.receiving.sib.utils.Constants;
import com.walmart.move.nim.receiving.sib.utils.SIBTestUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.mockito.*;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DeliveryStatusEventProcessorTest extends ReceivingTestBase {

  @InjectMocks private DeliveryStatusEventProcessor deliveryStatusEventProcessor;

  @Mock private EventRegistrationService eventRegistrationService;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Mock private SIBManagedConfig sibManagedConfig;

  @Mock private AppConfig appConfig;

  @Mock private RestConnector restConnector;

  @Mock private EventRepository eventRepository;

  private Gson gson;
  private JsonArray configuredSite;

  private Map<String, Set<String>> documentPalletMap;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    gson = new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
    configuredSite = new JsonArray();
    configuredSite.add(5504);
    documentPalletMap = new HashMap<>();
    documentPalletMap.put(
        DocumentType.MANUAL_BILLING_ASN.getDocType(), new HashSet<>(Arrays.asList("STORE")));
    documentPalletMap.put(
        DocumentType.CHARGE_ASN.getDocType(), new HashSet<>(Arrays.asList("STORE")));
    documentPalletMap.put(
        DocumentType.CREDIT_ASN.getDocType(), new HashSet<>(Arrays.asList("STORE")));
  }

  @AfterMethod
  public void resetMocks() {
    Mockito.reset(eventRegistrationService);
    Mockito.reset(tenantSpecificConfigReader);
    Mockito.reset(sibManagedConfig);
    Mockito.reset(appConfig);
    Mockito.reset(restConnector);
    Mockito.reset(eventRepository);
  }

  @Test
  public void testDoProcessEventForManualBill() {
    when(sibManagedConfig.getCorrectionalInvoiceDocumentType())
        .thenReturn(Arrays.asList(DocumentType.MANUAL_BILLING_ASN.getDocType()));
    when(sibManagedConfig.getNgrParityRIPEvent())
        .thenReturn(Arrays.asList(Constants.EVENT_DELIVERY_ARRIVED));
    when(sibManagedConfig.getCleanupRegisterOnDeliveryEvent())
        .thenReturn(Constants.EVENT_DELIVERY_ARRIVED);
    when(sibManagedConfig.getStoreAutoInitializationOnDeliveryEvent())
        .thenReturn(Constants.EVENT_DELIVERY_ARRIVED);
    when(sibManagedConfig.getCorrectionalInvNgrParityRIPEvent())
        .thenReturn(Arrays.asList(ReceivingConstants.EVENT_DELIVERY_SHIPMENT_ADDED));
    when(sibManagedConfig.getNgrParityFacilities()).thenReturn(Arrays.asList(5504));
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            Constants.ENABLE_NGR_PARITY_FOR_CORRECTIONAL_INVOICE))
        .thenReturn(true);
    when(appConfig.getGdmBaseUrl()).thenReturn("https://gls-atlas-gdm-core.us.walmart.com");
    TenantContext.setFacilityNum(5504);
    when(sibManagedConfig.getNgrEventEligiblePalletTypeMap()).thenReturn(documentPalletMap);
    when(restConnector.exchange(
            any(), eq(HttpMethod.GET), any(HttpEntity.class), eq(ASNDocument.class)))
        .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));
    when(restConnector.exchange(any(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                gson.toJson(
                    SIBTestUtils.getASNDocument(
                        "src/test/resource/manualBill/asnWithStoreAndMFCPallets.json")),
                HttpStatus.OK));
    when(eventRegistrationService.processNGRParity(any(), any(), any())).thenReturn(null);

    when(tenantSpecificConfigReader.getCcmConfigValueAsJson(anyString(), anyString()))
        .thenReturn(configuredSite);
    when(sibManagedConfig.getEligibleDeliverySourceTypeForGDMEventProcessing())
        .thenReturn(Arrays.asList(SourceType.DC.name()));
    when(restConnector.exchange(
            any(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Delivery.class)))
        .thenReturn(
            new ResponseEntity<>(
                SIBTestUtils.getDeliveryDocument("src/test/resource/delivery/dc_delivery.json"),
                HttpStatus.OK));
    ArgumentCaptor<ASNDocument> asnDocumentArgumentCaptor =
        ArgumentCaptor.forClass(ASNDocument.class);

    deliveryStatusEventProcessor.doProcessEvent(
        SIBTestUtils.getDeliveryUpdateMessage(
            "src/test/resource/deliveryUpdateEvent/ManualBillShipmentAddedEvent.json"));
    verify(eventRegistrationService, times(1))
        .processNGRParity(asnDocumentArgumentCaptor.capture(), any(), any());
    ASNDocument asnDocument = asnDocumentArgumentCaptor.getValue();
    Assert.assertNotNull(asnDocument.getPacks());
    Assert.assertEquals(asnDocument.getPacks().size(), 1);
    Assert.assertEquals(asnDocument.getPacks().get(0).getPalletNumber(), "620000001000018511");
  }

  @Test
  public void testDoProcessEventForManualBillSiteNotEnabled() {
    when(sibManagedConfig.getCorrectionalInvoiceDocumentType())
        .thenReturn(Arrays.asList(DocumentType.MANUAL_BILLING_ASN.getDocType()));
    when(sibManagedConfig.getNgrParityRIPEvent())
        .thenReturn(Arrays.asList(Constants.EVENT_DELIVERY_ARRIVED));
    when(sibManagedConfig.getCorrectionalInvNgrParityRIPEvent())
        .thenReturn(Arrays.asList(ReceivingConstants.EVENT_DELIVERY_SHIPMENT_ADDED));
    when(sibManagedConfig.getCleanupRegisterOnDeliveryEvent())
        .thenReturn(Constants.EVENT_DELIVERY_ARRIVED);
    when(sibManagedConfig.getStoreAutoInitializationOnDeliveryEvent())
        .thenReturn(Constants.EVENT_DELIVERY_ARRIVED);
    when(sibManagedConfig.getNgrParityFacilities()).thenReturn(Arrays.asList(5504));
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            Constants.ENABLE_NGR_PARITY_FOR_CORRECTIONAL_INVOICE))
        .thenReturn(false);
    when(appConfig.getGdmBaseUrl()).thenReturn("https://gls-atlas-gdm-core.us.walmart.com");
    TenantContext.setFacilityNum(3284);
    when(sibManagedConfig.getNgrEventEligiblePalletTypeMap()).thenReturn(documentPalletMap);
    when(restConnector.exchange(
            any(), eq(HttpMethod.GET), any(HttpEntity.class), eq(ASNDocument.class)))
        .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));
    when(restConnector.exchange(any(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                gson.toJson(
                    SIBTestUtils.getASNDocument(
                        "src/test/resource/manualBill/asnWithStoreAndMFCPallets.json")),
                HttpStatus.OK));
    when(eventRegistrationService.processNGRParity(any(), any(), any())).thenReturn(null);

    when(tenantSpecificConfigReader.getCcmConfigValueAsJson(anyString(), anyString()))
        .thenReturn(configuredSite);
    when(sibManagedConfig.getEligibleDeliverySourceTypeForGDMEventProcessing())
        .thenReturn(Arrays.asList(SourceType.DC.name()));
    when(restConnector.exchange(
            any(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Delivery.class)))
        .thenReturn(
            new ResponseEntity<>(
                SIBTestUtils.getDeliveryDocument("src/test/resource/delivery/dc_delivery.json"),
                HttpStatus.OK));
    ArgumentCaptor<ASNDocument> asnDocumentArgumentCaptor =
        ArgumentCaptor.forClass(ASNDocument.class);

    deliveryStatusEventProcessor.doProcessEvent(
        SIBTestUtils.getDeliveryUpdateMessage(
            "src/test/resource/deliveryUpdateEvent/ManualBillShipmentAddedEvent.json"));
    verify(eventRegistrationService, never())
        .processNGRParity(asnDocumentArgumentCaptor.capture(), any(), any());
  }

  @Test
  public void testDoProcessEventForASNEventTypeShipmentAdded() {
    when(sibManagedConfig.getCorrectionalInvoiceDocumentType())
        .thenReturn(Arrays.asList(DocumentType.MANUAL_BILLING_ASN.getDocType()));
    when(sibManagedConfig.getNgrParityRIPEvent())
        .thenReturn(Arrays.asList(Constants.EVENT_DELIVERY_ARRIVED));
    when(sibManagedConfig.getCorrectionalInvNgrParityRIPEvent())
        .thenReturn(Arrays.asList(ReceivingConstants.EVENT_DELIVERY_SHIPMENT_ADDED));
    when(sibManagedConfig.getCleanupRegisterOnDeliveryEvent())
        .thenReturn(Constants.EVENT_DELIVERY_ARRIVED);
    when(sibManagedConfig.getStoreAutoInitializationOnDeliveryEvent())
        .thenReturn(Constants.EVENT_DELIVERY_ARRIVED);
    when(sibManagedConfig.getNgrParityFacilities()).thenReturn(Arrays.asList(5504));
    when(appConfig.getGdmBaseUrl()).thenReturn("https://gls-atlas-gdm-core.us.walmart.com");
    TenantContext.setFacilityNum(5504);
    when(sibManagedConfig.getNgrEventEligiblePalletTypeMap()).thenReturn(documentPalletMap);
    when(restConnector.exchange(
            any(), eq(HttpMethod.GET), any(HttpEntity.class), eq(ASNDocument.class)))
        .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));
    when(eventRegistrationService.processNGRParity(any(), any(), any())).thenReturn(null);

    when(tenantSpecificConfigReader.getCcmConfigValueAsJson(anyString(), anyString()))
        .thenReturn(configuredSite);
    when(sibManagedConfig.getEligibleDeliverySourceTypeForGDMEventProcessing())
        .thenReturn(Arrays.asList(SourceType.DC.name()));
    when(restConnector.exchange(
            any(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Delivery.class)))
        .thenReturn(
            new ResponseEntity<>(
                SIBTestUtils.getDeliveryDocument("src/test/resource/delivery/dc_delivery.json"),
                HttpStatus.OK));
    ArgumentCaptor<ASNDocument> asnDocumentArgumentCaptor =
        ArgumentCaptor.forClass(ASNDocument.class);

    deliveryStatusEventProcessor.doProcessEvent(
        SIBTestUtils.getDeliveryUpdateMessage(
            "src/test/resource/deliveryUpdateEvent/ASNShipmentAddedEvent.json"));
    verify(eventRegistrationService, never())
        .processNGRParity(asnDocumentArgumentCaptor.capture(), any(), any());
    verify(restConnector, never())
        .exchange(any(), eq(HttpMethod.GET), any(HttpEntity.class), eq(ASNDocument.class));
  }

  @Test
  public void testDoProcessEventForASNEventTypeArrived() {
    when(sibManagedConfig.getCorrectionalInvoiceDocumentType())
        .thenReturn(Arrays.asList(DocumentType.MANUAL_BILLING_ASN.getDocType()));
    when(sibManagedConfig.getNgrParityRIPEvent())
        .thenReturn(Arrays.asList(Constants.EVENT_DELIVERY_ARRIVED));
    when(sibManagedConfig.getCorrectionalInvNgrParityRIPEvent())
        .thenReturn(Arrays.asList(ReceivingConstants.EVENT_DELIVERY_SHIPMENT_ADDED));
    when(sibManagedConfig.getCleanupRegisterOnDeliveryEvent())
        .thenReturn(Constants.EVENT_DELIVERY_ARRIVED);
    when(sibManagedConfig.getStoreAutoInitializationOnDeliveryEvent())
        .thenReturn(Constants.EVENT_DELIVERY_ARRIVED);
    when(sibManagedConfig.getNgrParityFacilities()).thenReturn(Arrays.asList(5504));
    when(appConfig.getGdmBaseUrl()).thenReturn("https://gls-atlas-gdm-core.us.walmart.com");
    TenantContext.setFacilityNum(5504);
    when(sibManagedConfig.getNgrEventEligiblePalletTypeMap()).thenReturn(documentPalletMap);
    when(restConnector.exchange(
            any(), eq(HttpMethod.GET), any(HttpEntity.class), eq(ASNDocument.class)))
        .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));
    when(restConnector.exchange(any(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                gson.toJson(
                    SIBTestUtils.getASNDocument(
                        "src/test/resource/asn/asnWithStoreAndMFCPallets.json")),
                HttpStatus.OK));
    when(eventRegistrationService.processNGRParity(any(), any(), any())).thenReturn(null);

    when(tenantSpecificConfigReader.getCcmConfigValueAsJson(anyString(), anyString()))
        .thenReturn(configuredSite);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(anyString())).thenReturn(true);
    when(sibManagedConfig.getEligibleDeliverySourceTypeForGDMEventProcessing())
        .thenReturn(Arrays.asList(SourceType.DC.name()));
    when(restConnector.exchange(
            any(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Delivery.class)))
        .thenReturn(
            new ResponseEntity<>(
                SIBTestUtils.getDeliveryDocument("src/test/resource/delivery/dc_delivery.json"),
                HttpStatus.OK));
    ArgumentCaptor<ASNDocument> asnDocumentArgumentCaptor =
        ArgumentCaptor.forClass(ASNDocument.class);

    deliveryStatusEventProcessor.doProcessEvent(
        SIBTestUtils.getDeliveryUpdateMessage(
            "src/test/resource/deliveryUpdateEvent/ASNArrivedEvent.json"));
    verify(eventRegistrationService, times(1))
        .processNGRParity(asnDocumentArgumentCaptor.capture(), any(), any());
    ASNDocument asnDocument = asnDocumentArgumentCaptor.getValue();
    Assert.assertNotNull(asnDocument.getPacks());
    Assert.assertEquals(asnDocument.getPacks().size(), 2);
    verify(eventRepository, times(2)).findAllByDeliveryNumberAndEventType(anyLong(), any());
    verify(eventRepository, times(2)).save(any());
  }

  @Test
  public void testDoProcessEventForCreditInvoice() {
    when(sibManagedConfig.getCorrectionalInvoiceDocumentType())
        .thenReturn(
            Arrays.asList(
                DocumentType.MANUAL_BILLING_ASN.getDocType(),
                DocumentType.CREDIT_ASN.getDocType(),
                DocumentType.CHARGE_ASN.getDocType()));
    when(sibManagedConfig.getNgrParityRIPEvent())
        .thenReturn(Arrays.asList(Constants.EVENT_DELIVERY_ARRIVED));
    when(sibManagedConfig.getCleanupRegisterOnDeliveryEvent())
        .thenReturn(Constants.EVENT_DELIVERY_ARRIVED);
    when(sibManagedConfig.getStoreAutoInitializationOnDeliveryEvent())
        .thenReturn(Constants.EVENT_DELIVERY_ARRIVED);
    when(sibManagedConfig.getCorrectionalInvNgrParityRIPEvent())
        .thenReturn(Arrays.asList(ReceivingConstants.EVENT_DELIVERY_SHIPMENT_ADDED));
    when(sibManagedConfig.getNgrParityFacilities()).thenReturn(Arrays.asList(5504));
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            Constants.ENABLE_NGR_PARITY_FOR_CORRECTIONAL_INVOICE))
        .thenReturn(true);
    when(appConfig.getGdmBaseUrl()).thenReturn("https://gls-atlas-gdm-core.us.walmart.com");
    TenantContext.setFacilityNum(5504);
    when(sibManagedConfig.getNgrEventEligiblePalletTypeMap()).thenReturn(documentPalletMap);
    when(restConnector.exchange(
            any(), eq(HttpMethod.GET), any(HttpEntity.class), eq(ASNDocument.class)))
        .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));
    when(restConnector.exchange(any(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                gson.toJson(
                    SIBTestUtils.getASNDocument("src/test/resource/asn/creditInvoiceAsn.json")),
                HttpStatus.OK));
    when(eventRegistrationService.processNGRParity(any(), any(), any())).thenReturn(null);

    when(tenantSpecificConfigReader.getCcmConfigValueAsJson(anyString(), anyString()))
        .thenReturn(configuredSite);
    when(sibManagedConfig.getEligibleDeliverySourceTypeForGDMEventProcessing())
        .thenReturn(Arrays.asList(SourceType.DC.name()));
    when(restConnector.exchange(
            any(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Delivery.class)))
        .thenReturn(
            new ResponseEntity<>(
                SIBTestUtils.getDeliveryDocument("src/test/resource/delivery/dc_delivery.json"),
                HttpStatus.OK));
    ArgumentCaptor<ASNDocument> asnDocumentArgumentCaptor =
        ArgumentCaptor.forClass(ASNDocument.class);

    deliveryStatusEventProcessor.doProcessEvent(
        SIBTestUtils.getDeliveryUpdateMessage(
            "src/test/resource/deliveryUpdateEvent/CreditInvoiceShipmentAddedEvent.json"));
    verify(eventRegistrationService, times(1))
        .processNGRParity(asnDocumentArgumentCaptor.capture(), any(), any());
    ASNDocument asnDocument = asnDocumentArgumentCaptor.getValue();
    Assert.assertNotNull(asnDocument.getPacks());
    Assert.assertEquals(asnDocument.getPacks().size(), 1);
    Assert.assertEquals(asnDocument.getPacks().get(0).getPalletNumber(), "623400000000000004");
  }

  @Test
  public void testDoProcessEventForChargeAsn() {
    when(sibManagedConfig.getCorrectionalInvoiceDocumentType())
        .thenReturn(
            Arrays.asList(
                DocumentType.MANUAL_BILLING_ASN.getDocType(),
                DocumentType.CREDIT_ASN.getDocType(),
                DocumentType.CHARGE_ASN.getDocType()));
    when(sibManagedConfig.getNgrParityRIPEvent())
        .thenReturn(Arrays.asList(Constants.EVENT_DELIVERY_ARRIVED));
    when(sibManagedConfig.getCleanupRegisterOnDeliveryEvent())
        .thenReturn(Constants.EVENT_DELIVERY_ARRIVED);
    when(sibManagedConfig.getStoreAutoInitializationOnDeliveryEvent())
        .thenReturn(Constants.EVENT_DELIVERY_ARRIVED);
    when(sibManagedConfig.getCorrectionalInvNgrParityRIPEvent())
        .thenReturn(Arrays.asList(ReceivingConstants.EVENT_DELIVERY_SHIPMENT_ADDED));
    when(sibManagedConfig.getNgrParityFacilities()).thenReturn(Arrays.asList(5504));
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            Constants.ENABLE_NGR_PARITY_FOR_CORRECTIONAL_INVOICE))
        .thenReturn(true);
    when(appConfig.getGdmBaseUrl()).thenReturn("https://gls-atlas-gdm-core.us.walmart.com");
    TenantContext.setFacilityNum(5504);
    when(sibManagedConfig.getNgrEventEligiblePalletTypeMap()).thenReturn(documentPalletMap);
    when(restConnector.exchange(
            any(), eq(HttpMethod.GET), any(HttpEntity.class), eq(ASNDocument.class)))
        .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));
    when(restConnector.exchange(any(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                gson.toJson(
                    SIBTestUtils.getASNDocument("src/test/resource/asn/chargeInvoiceAsn.json")),
                HttpStatus.OK));
    when(eventRegistrationService.processNGRParity(any(), any(), any())).thenReturn(null);

    when(tenantSpecificConfigReader.getCcmConfigValueAsJson(anyString(), anyString()))
        .thenReturn(configuredSite);
    when(sibManagedConfig.getEligibleDeliverySourceTypeForGDMEventProcessing())
        .thenReturn(Arrays.asList(SourceType.DC.name()));
    when(restConnector.exchange(
            any(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Delivery.class)))
        .thenReturn(
            new ResponseEntity<>(
                SIBTestUtils.getDeliveryDocument("src/test/resource/delivery/dc_delivery.json"),
                HttpStatus.OK));
    ArgumentCaptor<ASNDocument> asnDocumentArgumentCaptor =
        ArgumentCaptor.forClass(ASNDocument.class);

    deliveryStatusEventProcessor.doProcessEvent(
        SIBTestUtils.getDeliveryUpdateMessage(
            "src/test/resource/deliveryUpdateEvent/ChargeInvoiceShipmentAddedEvent.json"));
    verify(eventRegistrationService, times(1))
        .processNGRParity(asnDocumentArgumentCaptor.capture(), any(), any());
    ASNDocument asnDocument = asnDocumentArgumentCaptor.getValue();
    Assert.assertNotNull(asnDocument.getPacks());
    Assert.assertEquals(asnDocument.getPacks().size(), 1);
    Assert.assertEquals(asnDocument.getPacks().get(0).getPalletNumber(), "623400000000000004");
  }

  @Test
  public void testDoProcessDeliveryScheduledEventForChargeAsn() {
    when(sibManagedConfig.getCorrectionalInvoiceDocumentType())
        .thenReturn(
            Arrays.asList(
                DocumentType.MANUAL_BILLING_ASN.getDocType(),
                DocumentType.CREDIT_ASN.getDocType(),
                DocumentType.CHARGE_ASN.getDocType()));
    when(sibManagedConfig.getNgrParityRIPEvent())
        .thenReturn(Arrays.asList(Constants.EVENT_DELIVERY_ARRIVED));
    when(sibManagedConfig.getCleanupRegisterOnDeliveryEvent())
        .thenReturn(Constants.EVENT_DELIVERY_ARRIVED);
    when(sibManagedConfig.getStoreAutoInitializationOnDeliveryEvent())
        .thenReturn(Constants.EVENT_DELIVERY_ARRIVED);
    when(sibManagedConfig.getCorrectionalInvNgrParityRIPEvent())
        .thenReturn(Arrays.asList(ReceivingConstants.EVENT_DELIVERY_SHIPMENT_ADDED));
    when(sibManagedConfig.getNgrParityFacilities()).thenReturn(Arrays.asList(5504));
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            Constants.ENABLE_NGR_PARITY_FOR_CORRECTIONAL_INVOICE))
        .thenReturn(true);
    when(appConfig.getGdmBaseUrl()).thenReturn("https://gls-atlas-gdm-core.us.walmart.com");
    TenantContext.setFacilityNum(5504);
    when(sibManagedConfig.getNgrEventEligiblePalletTypeMap()).thenReturn(documentPalletMap);
    when(restConnector.exchange(
            any(), eq(HttpMethod.GET), any(HttpEntity.class), eq(ASNDocument.class)))
        .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));
    when(restConnector.exchange(any(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                gson.toJson(
                    SIBTestUtils.getASNDocument("src/test/resource/asn/chargeInvoiceAsn.json")),
                HttpStatus.OK));
    when(eventRegistrationService.processNGRParity(any(), any(), any())).thenReturn(null);

    when(tenantSpecificConfigReader.getCcmConfigValueAsJson(anyString(), anyString()))
        .thenReturn(configuredSite);
    when(sibManagedConfig.getEligibleDeliverySourceTypeForGDMEventProcessing())
        .thenReturn(Arrays.asList(SourceType.DC.name()));
    when(restConnector.exchange(
            any(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Delivery.class)))
        .thenReturn(
            new ResponseEntity<>(
                SIBTestUtils.getDeliveryDocument("src/test/resource/delivery/dc_delivery.json"),
                HttpStatus.OK));
    ArgumentCaptor<ASNDocument> asnDocumentArgumentCaptor =
        ArgumentCaptor.forClass(ASNDocument.class);

    deliveryStatusEventProcessor.doProcessEvent(
        SIBTestUtils.getDeliveryUpdateMessage(
            "src/test/resource/deliveryUpdateEvent/ChargeInvoiceScheduledEvent.json"));
    verify(eventRegistrationService, never())
        .processNGRParity(asnDocumentArgumentCaptor.capture(), any(), any());
    verify(restConnector, never())
        .exchange(any(), eq(HttpMethod.GET), any(HttpEntity.class), eq(ASNDocument.class));
    verify(restConnector, never())
        .exchange(any(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
  }

  @Test
  public void testDoProcessForInvalidSourceType() {
    when(sibManagedConfig.getCorrectionalInvoiceDocumentType())
        .thenReturn(Arrays.asList(DocumentType.MANUAL_BILLING_ASN.getDocType()));
    when(sibManagedConfig.getNgrParityRIPEvent())
        .thenReturn(Arrays.asList(Constants.EVENT_DELIVERY_ARRIVED));
    when(sibManagedConfig.getCorrectionalInvNgrParityRIPEvent())
        .thenReturn(Arrays.asList(ReceivingConstants.EVENT_DELIVERY_SHIPMENT_ADDED));
    when(sibManagedConfig.getCleanupRegisterOnDeliveryEvent())
        .thenReturn(Constants.EVENT_DELIVERY_ARRIVED);
    when(sibManagedConfig.getStoreAutoInitializationOnDeliveryEvent())
        .thenReturn(Constants.EVENT_DELIVERY_ARRIVED);
    when(sibManagedConfig.getNgrParityFacilities()).thenReturn(Arrays.asList(5504));
    when(appConfig.getGdmBaseUrl()).thenReturn("https://gls-atlas-gdm-core.us.walmart.com");
    TenantContext.setFacilityNum(5504);
    when(sibManagedConfig.getNgrEventEligiblePalletTypeMap()).thenReturn(documentPalletMap);
    when(restConnector.exchange(
            any(), eq(HttpMethod.GET), any(HttpEntity.class), eq(ASNDocument.class)))
        .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));
    when(restConnector.exchange(any(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                gson.toJson(
                    SIBTestUtils.getASNDocument(
                        "src/test/resource/asn/asnWithStoreAndMFCPallets.json")),
                HttpStatus.OK));
    when(eventRegistrationService.processNGRParity(any(), any(), any())).thenReturn(null);

    when(tenantSpecificConfigReader.getCcmConfigValueAsJson(anyString(), anyString()))
        .thenReturn(configuredSite);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(anyString())).thenReturn(true);
    when(restConnector.exchange(
            any(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Delivery.class)))
        .thenReturn(
            new ResponseEntity<>(
                SIBTestUtils.getDeliveryDocument("src/test/resource/delivery/vendor_delivery.json"),
                HttpStatus.OK));

    ArgumentCaptor<ASNDocument> asnDocumentArgumentCaptor =
        ArgumentCaptor.forClass(ASNDocument.class);

    deliveryStatusEventProcessor.doProcessEvent(
        SIBTestUtils.getDeliveryUpdateMessage(
            "src/test/resource/deliveryUpdateEvent/ASNArrivedEvent.json"));
    verify(eventRegistrationService, never())
        .processNGRParity(asnDocumentArgumentCaptor.capture(), any(), any());
    verify(eventRepository, never()).findAllByDeliveryNumberAndEventType(anyLong(), any());
    verify(eventRepository, never()).save(any());
  }

  @Test(expectedExceptions = ReceivingDataNotFoundException.class)
  public void testDoProcess_ShipmentDetailsNotPresent() {
    when(sibManagedConfig.getCorrectionalInvoiceDocumentType())
        .thenReturn(Arrays.asList(DocumentType.MANUAL_BILLING_ASN.getDocType()));
    when(sibManagedConfig.getNgrParityRIPEvent())
        .thenReturn(Arrays.asList(Constants.EVENT_DELIVERY_ARRIVED));
    when(sibManagedConfig.getCorrectionalInvNgrParityRIPEvent())
        .thenReturn(Arrays.asList(ReceivingConstants.EVENT_DELIVERY_SHIPMENT_ADDED));
    when(sibManagedConfig.getCleanupRegisterOnDeliveryEvent())
        .thenReturn(Constants.EVENT_DELIVERY_ARRIVED);
    when(sibManagedConfig.getStoreAutoInitializationOnDeliveryEvent())
        .thenReturn(Constants.EVENT_DELIVERY_ARRIVED);
    when(sibManagedConfig.getNgrParityFacilities()).thenReturn(Arrays.asList(5504));
    when(appConfig.getGdmBaseUrl()).thenReturn("https://gls-atlas-gdm-core.us.walmart.com");
    TenantContext.setFacilityNum(5504);
    when(sibManagedConfig.getNgrEventEligiblePalletTypeMap()).thenReturn(documentPalletMap);
    when(restConnector.exchange(
            any(), eq(HttpMethod.GET), any(HttpEntity.class), eq(ASNDocument.class)))
        .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));
    when(restConnector.exchange(any(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                gson.toJson(
                    SIBTestUtils.getASNDocument(
                        "src/test/resource/asn/asnWithStoreAndMFCPallets.json")),
                HttpStatus.OK));
    when(eventRegistrationService.processNGRParity(any(), any(), any())).thenReturn(null);

    when(tenantSpecificConfigReader.getCcmConfigValueAsJson(anyString(), anyString()))
        .thenReturn(configuredSite);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(anyString())).thenReturn(true);
    when(restConnector.exchange(
            any(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Delivery.class)))
        .thenReturn(
            new ResponseEntity<>(
                Delivery.builder()
                    .deliveryNumber(550478600023855L)
                    .shipments(Collections.emptyList())
                    .build(),
                HttpStatus.OK));

    deliveryStatusEventProcessor.doProcessEvent(
        SIBTestUtils.getDeliveryUpdateMessage(
            "src/test/resource/deliveryUpdateEvent/ASNArrivedEvent.json"));
  }
}
