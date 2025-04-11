package com.walmart.move.nim.receiving.core.event.processor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.event.processor.update.DefaultDeliveryProcessor;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.model.gdm.v3.*;
import com.walmart.move.nim.receiving.core.repositories.DeliveryMetaDataRepository;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DefaultDeliveryProcessorTest {
  @InjectMocks DefaultDeliveryProcessor baseDeliveryProcessor;
  @Mock private DeliveryMetaDataRepository deliveryMetaDataRepository;
  @Mock private DeliveryService deliveryService;
  @Mock private AppConfig appConfig;
  private DeliveryUpdateMessage deliveryUpdateMessage;

  private Delivery delivery;

  @BeforeClass
  public void setUp() {
    ReflectionTestUtils.setField(baseDeliveryProcessor, "deliveryService", deliveryService);
  }

  @BeforeClass
  private void initMocks() throws IOException {
    MockitoAnnotations.initMocks(this);
    getDeliveryInformation();
  }

  @AfterMethod
  private void shutdownMocks() {
    reset(deliveryService, deliveryMetaDataRepository);
  }

  private void getDeliveryInformation() {
    deliveryUpdateMessage = new DeliveryUpdateMessage();
    deliveryUpdateMessage.setCountryCode("US");
    deliveryUpdateMessage.setSiteNumber("4321");
    deliveryUpdateMessage.setDeliveryNumber("30008889");
    deliveryUpdateMessage.setEventType("DOOR_ASSIGNED");
    deliveryUpdateMessage.setDeliveryStatus("ARV");
    deliveryUpdateMessage.setUrl(
        "https://dev.gdm.prod.us.walmart.net/document/v2/deliveries/56003401");
    deliveryUpdateMessage.setEventType(ReceivingConstants.EVENT_DOOR_ASSIGNED);
    delivery = new Delivery();
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
    TenantContext.setFacilityNum(4034);
    TenantContext.setFacilityCountryCode("us");
  }

  @Test
  public void testPersistDeliveryWhenDeliveryIsNotFoundInDeliveryMetaDataTable()
      throws ReceivingException {
    when(appConfig.getGdmDeliveryUpdateKafkaListenerEnabledFacilities())
        .thenReturn(Collections.singletonList(32818));
    when(deliveryService.getGDMData(any(DeliveryUpdateMessage.class))).thenReturn(delivery);
    when(deliveryMetaDataRepository.findByDeliveryNumber(anyString())).thenReturn(Optional.empty());
    when(deliveryMetaDataRepository.save(any(DeliveryMetaData.class)))
        .thenReturn(getDeliveryMetaData().get());

    baseDeliveryProcessor.processEvent(deliveryUpdateMessage);

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

    baseDeliveryProcessor.processEvent(deliveryUpdateMessage);

    verify(deliveryMetaDataRepository, times(1)).findByDeliveryNumber(anyString());
    verify(deliveryMetaDataRepository, times(0)).save(any(DeliveryMetaData.class));
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testGetDeliveryException() throws ReceivingInternalException, ReceivingException {
    when(deliveryService.getGDMData(deliveryUpdateMessage)).thenThrow(ReceivingException.class);
    baseDeliveryProcessor.processEvent(deliveryUpdateMessage);
  }

  private Optional<DeliveryMetaData> getDeliveryMetaData() {
    DeliveryMetaData deliveryMetaData =
        DeliveryMetaData.builder()
            .deliveryNumber(String.valueOf(30008889L))
            .deliveryStatus(DeliveryStatus.ARV)
            .build();
    return Optional.of(deliveryMetaData);
  }
}
