package com.walmart.move.nim.receiving.rdc.utils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.model.Distribution;
import com.walmart.move.nim.receiving.core.service.DCFinServiceV2;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RdcDcFinUtilsTest {
  @Mock private DeliveryMetaDataService rdcDeliveryMetaDataService;
  @Mock private DCFinServiceV2 dcFinServiceV2;

  @InjectMocks private RdcDcFinUtils rdcDcFinUtils;

  private static final String facilityNum = "32818";
  private static final String countryCode = "US";

  @BeforeClass
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode(countryCode);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    TenantContext.setCorrelationId("txt-1234");
  }

  @AfterMethod
  public void tearDown() {
    reset(rdcDeliveryMetaDataService, dcFinServiceV2);
  }

  @Test
  public void testPostReceiptsToDcFinIsSuccessSSTK() {
    when(rdcDeliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(getMockDeliveryMetaData());
    doNothing()
        .when(dcFinServiceV2)
        .postReceiptUpdateToDCFin(
            anyList(),
            any(HttpHeaders.class),
            anyBoolean(),
            any(DeliveryMetaData.class),
            anyString());

    rdcDcFinUtils.postToDCFin(
        Collections.singletonList(getMockContainerSSTK()), ReceivingConstants.PO_TEXT);

    verify(rdcDeliveryMetaDataService, times(1)).findByDeliveryNumber(anyString());
    verify(dcFinServiceV2, times(1))
        .postReceiptUpdateToDCFin(
            anyList(),
            any(HttpHeaders.class),
            anyBoolean(),
            any(DeliveryMetaData.class),
            anyString());
  }

  @Test
  public void testPostReceiptsToDcFinIsSuccessDA() {
    when(rdcDeliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(getMockDeliveryMetaData());
    doNothing()
        .when(dcFinServiceV2)
        .postReceiptUpdateToDCFin(
            anyList(),
            any(HttpHeaders.class),
            anyBoolean(),
            any(DeliveryMetaData.class),
            anyString());

    rdcDcFinUtils.postToDCFin(
        Collections.singletonList(getMockContainerDA()), ReceivingConstants.PO_TEXT);

    verify(rdcDeliveryMetaDataService, times(1)).findByDeliveryNumber(anyString());
    verify(dcFinServiceV2, times(1))
        .postReceiptUpdateToDCFin(
            anyList(),
            any(HttpHeaders.class),
            anyBoolean(),
            any(DeliveryMetaData.class),
            anyString());
  }

  private Optional<DeliveryMetaData> getMockDeliveryMetaData() {
    DeliveryMetaData deliveryMetaData = new DeliveryMetaData();
    deliveryMetaData.setDeliveryNumber("D1234");
    deliveryMetaData.setDeliveryStatus(DeliveryStatus.WRK);
    deliveryMetaData.setDoorNumber("100");
    deliveryMetaData.setCarrierName("SAC123");
    deliveryMetaData.setBillCode("PMO");
    deliveryMetaData.setCarrierScacCode("SCAC1");
    deliveryMetaData.setId(123L);
    return Optional.of(deliveryMetaData);
  }

  private Container getMockContainerSSTK() {
    Container container = new Container();
    container.setInstructionId(123L);
    container.setTrackingId("lpn123");
    container.setDeliveryNumber(123456L);
    container.setParentTrackingId(null);
    container.setInventoryStatus("AVAILABLE");
    container.setContainerItems(Collections.singletonList(getMockContainerItem("SYM1")));
    Map<String, String> destination = new HashMap<>();
    destination.put(ReceivingConstants.SLOT, "A0001");
    container.setDestination(destination);
    return container;
  }

  private Container getMockContainerDA() {
    Container container = new Container();
    container.setInstructionId(123L);
    container.setTrackingId("lpn123");
    container.setDeliveryNumber(123456L);
    container.setParentTrackingId(null);
    container.setInventoryStatus("AVAILABLE");
    container.setContainerItems(Collections.singletonList(getMockContainerItemDA()));
    Map<String, String> destination = new HashMap<>();
    destination.put(ReceivingConstants.BU_NUMBER, "1083");
    container.setDestination(destination);
    return container;
  }

  private ContainerItem getMockContainerItem(String asrsAlignment) {
    ContainerItem containerItem = new ContainerItem();
    containerItem.setPurchaseReferenceNumber("PO1");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setQuantity(6);
    containerItem.setVnpkQty(6);
    containerItem.setWhpkQty(6);
    containerItem.setAsrsAlignment(asrsAlignment);
    containerItem.setRotateDate(new Date());
    Distribution distribution = new Distribution();
    distribution.setDestNbr(1083);
    distribution.setAllocQty(131);
    containerItem.setDistributions(Collections.singletonList(distribution));
    containerItem.setSlotType(ReceivingConstants.PRIME_SLOT_TYPE);
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(ReceivingConstants.IS_ATLAS_CONVERTED_ITEM, "true");
    containerItem.setContainerItemMiscInfo(containerItemMiscInfo);
    return containerItem;
  }

  private ContainerItem getMockContainerItemDA() {
    ContainerItem containerItem = new ContainerItem();
    containerItem.setPurchaseReferenceNumber("PO1");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setQuantity(6);
    containerItem.setVnpkQty(6);
    containerItem.setWhpkQty(6);
    containerItem.setRotateDate(new Date());
    Distribution distribution = new Distribution();
    distribution.setDestNbr(1083);
    distribution.setAllocQty(131);
    containerItem.setDistributions(Collections.singletonList(distribution));
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(ReceivingConstants.IS_ATLAS_CONVERTED_ITEM, "true");
    containerItem.setContainerItemMiscInfo(containerItemMiscInfo);
    return containerItem;
  }
}
