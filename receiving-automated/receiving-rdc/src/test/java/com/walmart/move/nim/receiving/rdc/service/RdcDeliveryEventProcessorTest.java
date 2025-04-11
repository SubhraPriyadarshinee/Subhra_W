package com.walmart.move.nim.receiving.rdc.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.message.common.DeliveryMessageEvent;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.model.gdm.v3.*;
import com.walmart.move.nim.receiving.core.repositories.DeliveryMetaDataRepository;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.utils.RdcLabelGenerationUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RdcDeliveryEventProcessorTest {
  private DeliveryUpdateMessage deliveryUpdateMessage;
  @InjectMocks private RdcDeliveryEventProcessor rdcDeliveryEventProcessor;
  @Mock private RdcLabelGenerationService rdcLabelGenerationService;
  @Mock private RdcManagedConfig rdcManagedConfig;
  @Mock private DeliveryMetaDataRepository deliveryMetaDataRepository;
  @Mock private DeliveryService deliveryService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private RdcLabelGenerationUtils rdcLabelGenerationUtils;

  @BeforeClass
  private void initMocks() {
    MockitoAnnotations.initMocks(this);

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32987);
    TenantContext.setCorrelationId("qwert");

    deliveryUpdateMessage = new DeliveryUpdateMessage();
    deliveryUpdateMessage.setDeliveryNumber("21119003");
    deliveryUpdateMessage.setEventType("DOOR_ASSIGNED");
    deliveryUpdateMessage.setDeliveryStatus("FNL");
    deliveryUpdateMessage.setCountryCode("US");
    deliveryUpdateMessage.setSiteNumber("32987");
    deliveryUpdateMessage.setDeliveryNumber("21119003");
    deliveryUpdateMessage.setDeliveryStatus("ARV");
    deliveryUpdateMessage.setUrl(
        "https://dev.gdm.prod.us.walmart.net/document/v2/deliveries/21119003");
  }

  @AfterMethod
  public void tearDown() {
    reset(
        rdcLabelGenerationService,
        deliveryMetaDataRepository,
        deliveryService,
        tenantSpecificConfigReader);
  }

  @Test
  public void testProcessEvent_WrongMessageFormat() throws ReceivingException {
    rdcDeliveryEventProcessor.processEvent(new DeliveryMessageEvent());
    Mockito.verify(rdcLabelGenerationService, times(0)).processDeliveryEvent(any());
  }

  @Test
  public void testProcessEvent_otherEvents() throws ReceivingException {
    Delivery delivery = getDelivery();
    when(deliveryService.getGDMData(any(DeliveryUpdateMessage.class))).thenReturn(delivery);
    when(deliveryMetaDataRepository.findByDeliveryNumber(anyString())).thenReturn(Optional.empty());
    when(deliveryMetaDataRepository.save(any(DeliveryMetaData.class)))
        .thenReturn(getDeliveryMetaData().get());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    deliveryUpdateMessage.setDeliveryNumber("21119003");
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_PO_LINE_UPDATED);
    when(rdcLabelGenerationUtils.isSSTKPilotDeliveryEnabled()).thenReturn(true);
    when(rdcLabelGenerationUtils.isAtlasSSTKPilotDelivery(anyLong())).thenReturn(true);
    rdcDeliveryEventProcessor.processEvent(deliveryUpdateMessage);
    Mockito.verify(rdcLabelGenerationService, times(1))
        .processDeliveryEvent(any(DeliveryUpdateMessage.class));
  }

  @Test
  public void testProcessEvent_flagDisabled() throws ReceivingException {
    Delivery delivery = getDelivery();
    when(deliveryService.getGDMData(any(DeliveryUpdateMessage.class))).thenReturn(delivery);
    when(deliveryMetaDataRepository.findByDeliveryNumber(anyString())).thenReturn(Optional.empty());
    when(deliveryMetaDataRepository.save(any(DeliveryMetaData.class)))
        .thenReturn(getDeliveryMetaData().get());
    rdcDeliveryEventProcessor.processEvent(deliveryUpdateMessage);
    Mockito.verify(rdcLabelGenerationService, times(0))
        .processDeliveryEvent(any(DeliveryUpdateMessage.class));
  }

  @Test
  public void testPersistDeliveryWhenDeliveryIsNotFoundInDeliveryMetaDataTable()
      throws ReceivingException {
    when(deliveryService.getGDMData(any(DeliveryUpdateMessage.class))).thenReturn(getDelivery());
    when(deliveryMetaDataRepository.findByDeliveryNumber(anyString())).thenReturn(Optional.empty());
    when(deliveryMetaDataRepository.save(any(DeliveryMetaData.class)))
        .thenReturn(getDeliveryMetaData().get());

    rdcDeliveryEventProcessor.processEvent(deliveryUpdateMessage);

    verify(deliveryService, times(1)).getGDMData(any(DeliveryUpdateMessage.class));
    verify(deliveryMetaDataRepository, times(1)).findByDeliveryNumber(anyString());
    verify(deliveryMetaDataRepository, times(1)).save(any(DeliveryMetaData.class));
  }

  @Test
  public void testPersistDeliveryWhenDeliveryIsFoundInDeliveryMetaDataTable()
      throws ReceivingException {
    when(deliveryMetaDataRepository.findByDeliveryNumber(anyString()))
        .thenReturn(getDeliveryMetaData());
    when(deliveryMetaDataRepository.save(any(DeliveryMetaData.class)))
        .thenReturn(getDeliveryMetaData().get());

    rdcDeliveryEventProcessor.processEvent(deliveryUpdateMessage);

    verify(deliveryMetaDataRepository, times(1)).findByDeliveryNumber(anyString());
    verify(deliveryMetaDataRepository, times(0)).save(any(DeliveryMetaData.class));
  }

  @Test
  public void testPersistDeliveryWithDoorNumber() throws ReceivingException {
    when(deliveryMetaDataRepository.findByDeliveryNumber(anyString()))
        .thenReturn(getDeliveryMetaData());
    when(deliveryMetaDataRepository.save(any(DeliveryMetaData.class)))
        .thenReturn(getDeliveryMetaData().get());
    deliveryUpdateMessage.setDeliveryStatus(DeliveryStatus.SCH.name());
    deliveryUpdateMessage.setDoorNumber("76575899");
    rdcDeliveryEventProcessor.processEvent(deliveryUpdateMessage);

    verify(deliveryMetaDataRepository, times(1)).findByDeliveryNumber(anyString());
    verify(deliveryMetaDataRepository, times(1)).save(any(DeliveryMetaData.class));
  }

  @Test
  public void testProcessEvent_InvalidDeliveryUpdateMessage_NullEventType()
      throws ReceivingException {
    deliveryUpdateMessage.setEventType(null);
    rdcDeliveryEventProcessor.processEvent(deliveryUpdateMessage);
    verify(deliveryService, times(0)).getGDMData(any(DeliveryUpdateMessage.class));
    verify(deliveryMetaDataRepository, times(0)).findByDeliveryNumber(anyString());
    verify(deliveryMetaDataRepository, times(0)).save(any(DeliveryMetaData.class));
  }

  @Test
  public void testProcessEvent_InvalidDeliveryUpdateMessage_EventTypeConfigDisabled()
      throws ReceivingException {
    when(rdcManagedConfig.getDeliveryUpdateMessageEventTypes())
        .thenReturn(Arrays.asList(ReceivingConstants.EVENT_PO_LINE_UPDATED));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(),
            eq(ReceivingConstants.RDC_DELIVERY_EVENT_TYPE_CONFIG_ENABLED),
            anyBoolean()))
        .thenReturn(false);
    rdcDeliveryEventProcessor.processEvent(deliveryUpdateMessage);
    verify(deliveryService, times(0)).getGDMData(any(DeliveryUpdateMessage.class));
    verify(deliveryMetaDataRepository, times(0)).findByDeliveryNumber(anyString());
    verify(deliveryMetaDataRepository, times(0)).save(any(DeliveryMetaData.class));
  }

  @Test
  public void testProcessEvent_InvalidDeliveryUpdateMessage_InvalidEventType()
      throws ReceivingException {
    when(rdcManagedConfig.getDeliveryUpdateMessageEventTypes())
        .thenReturn(Arrays.asList(ReceivingConstants.EVENT_PO_LINE_UPDATED));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(),
            eq(ReceivingConstants.RDC_DELIVERY_EVENT_TYPE_CONFIG_ENABLED),
            anyBoolean()))
        .thenReturn(true);
    deliveryUpdateMessage.setEventType("PO_LINE_ADDED");
    rdcDeliveryEventProcessor.processEvent(deliveryUpdateMessage);
    verify(deliveryService, times(0)).getGDMData(any(DeliveryUpdateMessage.class));
    verify(deliveryMetaDataRepository, times(0)).findByDeliveryNumber(anyString());
    verify(deliveryMetaDataRepository, times(0)).save(any(DeliveryMetaData.class));
  }

  @Test
  public void testProcessEvent_InvalidDeliveryUpdateMessage_EmptyDeliveryNumber()
      throws ReceivingException {
    deliveryUpdateMessage.setDeliveryNumber(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    deliveryUpdateMessage.setEventType(null);
    rdcDeliveryEventProcessor.processEvent(deliveryUpdateMessage);
    verify(deliveryService, times(0)).getGDMData(any(DeliveryUpdateMessage.class));
    verify(deliveryMetaDataRepository, times(0)).findByDeliveryNumber(anyString());
    verify(deliveryMetaDataRepository, times(0)).save(any(DeliveryMetaData.class));
  }

  private Optional<DeliveryMetaData> getDeliveryMetaData() {
    DeliveryMetaData deliveryMetaData =
        DeliveryMetaData.builder()
            .deliveryNumber(String.valueOf(30008889L))
            .deliveryStatus(DeliveryStatus.ARV)
            .build();
    return Optional.of(deliveryMetaData);
  }

  private Delivery getDelivery() {
    Delivery delivery = new Delivery();
    delivery.setDeliveryNumber(30008889L);
    StatusInformation statusInformation = new StatusInformation();
    statusInformation.setStatus("ARV");
    delivery.setStatusInformation(statusInformation);
    delivery.setDoorNumber("121");
    delivery.setCarrierName("test");
    TrailerInformation trailerInformation = new TrailerInformation();
    trailerInformation.setTrailerId("232");
    trailerInformation.setScacCode("W-1234");
    LoadInformation loadInformation = new LoadInformation();
    loadInformation.setTrailerInformation(trailerInformation);
    delivery.setLoadInformation(loadInformation);
    PurchaseOrder purchaseOrder = new PurchaseOrder();
    purchaseOrder.setFreightTermCode("COLL");
    List<PurchaseOrder> purchaseOrders = new ArrayList();
    List<PurchaseOrderLine> lines = new ArrayList<>();
    PurchaseOrderLine purchaseOrderLine = new PurchaseOrderLine();
    QuantityDetail quantityDetail = new QuantityDetail();
    quantityDetail.setQuantity(900);
    purchaseOrderLine.setOrdered(quantityDetail);
    lines.add(purchaseOrderLine);
    purchaseOrder.setLines(lines);
    purchaseOrders.add(purchaseOrder);
    delivery.setPurchaseOrders(purchaseOrders);
    return delivery;
  }
}
