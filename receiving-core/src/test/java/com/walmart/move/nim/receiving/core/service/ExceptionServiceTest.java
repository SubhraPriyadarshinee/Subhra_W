package com.walmart.move.nim.receiving.core.service;

import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.client.hawkeye.model.DeliverySearchRequest;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.model.InventoryUpdateRequest;
import com.walmart.move.nim.receiving.core.model.ReceiveExceptionRequest;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationContext;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ExceptionServiceTest extends ReceivingTestBase {
  @InjectMocks private ExceptionService exceptionService;
  @Mock private DefaultReceiveExceptionHandler defaultReceiveExceptionHandler;
  @Mock private ApplicationContext applicationContext;
  @Mock private ExceptionService mockExceptionService;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(12345);
  }

  @BeforeEach
  public void setUp() {}

  @AfterMethod
  public void resetMocks() {
    reset(applicationContext, defaultReceiveExceptionHandler);
    reset(tenantSpecificConfigReader);
  }

  @Test
  public void testReceiveException_Success() throws ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest();
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), eq(ReceiveExceptionHandler.class)))
        .thenReturn(defaultReceiveExceptionHandler);
    exceptionService.receiveException(receiveExceptionRequest, MockHttpHeaders.getHeaders());
    verify(defaultReceiveExceptionHandler, times(1))
        .receiveException(receiveExceptionRequest, MockHttpHeaders.getHeaders());
  }

  @Test
  public void testGetDeliveryDocumentsForDeliverySearch_Success() {
    DeliverySearchRequest deliverySearchRequest = getDeliverySearchRequest();
    doReturn(defaultReceiveExceptionHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), eq(ReceiveExceptionHandler.class));
    exceptionService.getDeliveryDocumentsForDeliverySearch(
        deliverySearchRequest, MockHttpHeaders.getHeaders());
    verify(defaultReceiveExceptionHandler, times(1))
        .getDeliveryDocumentsForDeliverySearch(deliverySearchRequest, MockHttpHeaders.getHeaders());
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testGetDeliveryDocumentsForDeliverySearch_ThrowsReceivingInternalException() {
    DeliverySearchRequest deliverySearchRequest = getDeliverySearchRequest();
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), eq(ReceiveExceptionHandler.class)))
        .thenReturn(defaultReceiveExceptionHandler);
    doThrow(new ReceivingInternalException("mock_error", "mock_error"))
        .when(defaultReceiveExceptionHandler)
        .getDeliveryDocumentsForDeliverySearch(deliverySearchRequest, MockHttpHeaders.getHeaders());
    exceptionService.getDeliveryDocumentsForDeliverySearch(
        deliverySearchRequest, MockHttpHeaders.getHeaders());
    verify(defaultReceiveExceptionHandler, times(1))
        .getDeliveryDocumentsForDeliverySearch(deliverySearchRequest, MockHttpHeaders.getHeaders());
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testItemOverride_ThrowsReceivingInternalException() throws ReceivingException {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest();
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), eq(ReceiveExceptionHandler.class)))
        .thenReturn(defaultReceiveExceptionHandler);
    doThrow(new ReceivingInternalException("mock_error", "mock_error"))
        .when(defaultReceiveExceptionHandler)
        .receiveException(receiveExceptionRequest, MockHttpHeaders.getHeaders());
    exceptionService.receiveException(receiveExceptionRequest, MockHttpHeaders.getHeaders());
    verify(defaultReceiveExceptionHandler, times(1))
        .receiveException(receiveExceptionRequest, MockHttpHeaders.getHeaders());
  }

  @Test
  public void testPrintShippingLabel_Success() throws ReceivingException {
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), eq(ReceiveExceptionHandler.class)))
        .thenReturn(defaultReceiveExceptionHandler);
    exceptionService.printShippingLabel("d326790000100000025617980", MockHttpHeaders.getHeaders());
    verify(defaultReceiveExceptionHandler, times(1))
        .printShippingLabel("d326790000100000025617980", MockHttpHeaders.getHeaders());
  }

  @Test
  public void testUpdateContainer() throws ReceivingException {
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), eq(ReceiveExceptionHandler.class)))
        .thenReturn(defaultReceiveExceptionHandler);
    InventoryUpdateRequest inventoryUpdateRequest = new InventoryUpdateRequest();
    inventoryUpdateRequest.setTrackingId("876875ufb7ijhgh767y4");
    inventoryUpdateRequest.setLocationName("102");
    inventoryUpdateRequest.setProcessInLIUI(true);
    exceptionService.inventoryContainerUpdate(inventoryUpdateRequest, MockHttpHeaders.getHeaders());
    verify(defaultReceiveExceptionHandler, times(1))
        .inventoryContainerUpdate(inventoryUpdateRequest, MockHttpHeaders.getHeaders());
  }

  private ReceiveExceptionRequest getReceiveExceptionRequest() {
    ReceiveExceptionRequest receiveExceptionRequest = new ReceiveExceptionRequest();
    receiveExceptionRequest.setExceptionMessage("Error");
    receiveExceptionRequest.setReceiver("Receiver");
    receiveExceptionRequest.setLpns(Collections.singletonList("1234567890"));
    receiveExceptionRequest.setItemNumber(550000000);
    receiveExceptionRequest.setSlot("R8000");
    receiveExceptionRequest.setDeliveryNumbers(Collections.singletonList("345123"));
    return receiveExceptionRequest;
  }

  private DeliverySearchRequest getDeliverySearchRequest() {
    DeliverySearchRequest deliverySearchRequest = new DeliverySearchRequest();
    deliverySearchRequest.setDoorNumber("102");
    deliverySearchRequest.setMessageId("1246caaf-8cf7-4ad9-8151-20d20a4c3210");
    deliverySearchRequest.setUpc("UPC");
    deliverySearchRequest.setFromDate("2023-06-01T13:14:15.123+01:00");
    deliverySearchRequest.setToDate("2023-06-20T13:14:15.123+01:00");
    deliverySearchRequest.setLocationId("LocationId");

    Map<String, String> scannedData = new HashMap<>();
    scannedData.put("BARCODE_SCAN", "01234567891234");
    List<Map<String, String>> scannedDataList = Arrays.asList(scannedData);
    deliverySearchRequest.setScannedDataList(scannedDataList);
    return deliverySearchRequest;
  }
}
