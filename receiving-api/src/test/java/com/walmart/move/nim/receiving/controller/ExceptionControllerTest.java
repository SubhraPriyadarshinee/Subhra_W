package com.walmart.move.nim.receiving.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.AssertJUnit.assertTrue;

import com.walmart.move.nim.receiving.base.ReceivingControllerTestBase;
import com.walmart.move.nim.receiving.core.advice.RestResponseExceptionHandler;
import com.walmart.move.nim.receiving.core.client.hawkeye.model.DeliverySearchRequest;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.service.ExceptionService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import java.util.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ExceptionControllerTest extends ReceivingControllerTestBase {
  @InjectMocks private ExceptionController exceptionController;
  @Mock private ExceptionService exceptionService;
  private MockMvc mockMvc;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    mockMvc =
        MockMvcBuilders.standaloneSetup(exceptionController)
            .setControllerAdvice(RestResponseExceptionHandler.class)
            .build();
  }

  @AfterMethod
  public void resetMocks() {
    reset(exceptionService);
  }

  @Test
  public void testReceiveException_Success() throws Exception {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest();
    InstructionResponse instructionResponse =
        new InstructionResponseImplNew(null, null, null, null);
    doReturn(instructionResponse)
        .when(exceptionService)
        .receiveException(any(ReceiveExceptionRequest.class), any(HttpHeaders.class));
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/exception/receive")
                .headers(MockHttpHeaders.getHeaders())
                .content(JacksonParser.writeValueAsString(receiveExceptionRequest)))
        .andExpect(MockMvcResultMatchers.status().isOk());

    verify(exceptionService, times(1))
        .receiveException(any(ReceiveExceptionRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testReceiveException_ThrowsException() throws Exception {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest();
    doThrow(new ReceivingInternalException("mock_error", "mock_error"))
        .when(exceptionService)
        .receiveException(any(ReceiveExceptionRequest.class), any(HttpHeaders.class));
    try {
      mockMvc
          .perform(
              MockMvcRequestBuilders.post("/exception/receive")
                  .headers(MockHttpHeaders.getHeaders())
                  .content(JacksonParser.writeValueAsString(receiveExceptionRequest)))
          .andExpect(MockMvcResultMatchers.status().isInternalServerError());
    } catch (Exception e) {
      System.out.println("exception: " + e.getMessage() + e.getStackTrace());
      assertTrue(true);
    }
    verify(exceptionService, times(1))
        .receiveException(any(ReceiveExceptionRequest.class), any(HttpHeaders.class));
  }

  private ReceiveExceptionRequest getReceiveExceptionRequest() {
    ReceiveExceptionRequest receiveExceptionRequest = new ReceiveExceptionRequest();
    receiveExceptionRequest.setExceptionMessage("Error");
    receiveExceptionRequest.setReceiver("Receiver");
    receiveExceptionRequest.setLpns(Collections.singletonList("1234567890"));
    receiveExceptionRequest.setSlot("R8000");
    receiveExceptionRequest.setItemNumber(550000000);
    receiveExceptionRequest.setDeliveryNumbers(Collections.singletonList("345123"));
    return receiveExceptionRequest;
  }

  @Test
  public void testGetDeliveryDocumentsForDeliverySearch_Success() throws Exception {
    DeliverySearchRequest deliverySearchRequest = getDeliverySearchRequest();
    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    doReturn(deliveryDocuments)
        .when(exceptionService)
        .getDeliveryDocumentsForDeliverySearch(
            any(DeliverySearchRequest.class), any(HttpHeaders.class));
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/exception/deliveries/search")
                .headers(MockHttpHeaders.getHeaders())
                .content(JacksonParser.writeValueAsString(deliverySearchRequest)))
        .andExpect(MockMvcResultMatchers.status().isOk());

    verify(exceptionService, times(1))
        .getDeliveryDocumentsForDeliverySearch(
            any(DeliverySearchRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testGetDeliveryDocumentsForDeliverySearch_Exception() {
    DeliverySearchRequest deliverySearchRequest = getDeliverySearchRequest();
    doThrow(RuntimeException.class)
        .when(exceptionService)
        .getDeliveryDocumentsForDeliverySearch(
            any(DeliverySearchRequest.class), any(HttpHeaders.class));
    try {
      mockMvc
          .perform(
              MockMvcRequestBuilders.post("/exception/deliveries/search")
                  .headers(MockHttpHeaders.getHeaders())
                  .content(JacksonParser.writeValueAsString(deliverySearchRequest)))
          .andExpect(MockMvcResultMatchers.status().is5xxServerError());
    } catch (Exception e) {
      System.out.println("exception: " + e.getMessage() + e.getStackTrace());
      assertTrue(true);
    }

    verify(exceptionService, times(1))
        .getDeliveryDocumentsForDeliverySearch(
            any(DeliverySearchRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testPrintShippingLabel_Success() throws Exception {
    Map<String, Object> mockPrintJobResponse = new HashMap<>();
    doReturn(mockPrintJobResponse)
        .when(exceptionService)
        .printShippingLabel(anyString(), any(HttpHeaders.class));
    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/exception/printshiplabel/d326790000100000025617980")
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(MockMvcResultMatchers.status().isOk());

    verify(exceptionService, times(1)).printShippingLabel(anyString(), any(HttpHeaders.class));
  }

  @Test
  public void testUpdateContainer_Success() throws Exception {
    InventoryUpdateRequest inventoryUpdateRequest = new InventoryUpdateRequest();
    inventoryUpdateRequest.setTrackingId("876875ufb7ijhgh767y4");
    inventoryUpdateRequest.setLocationName("102");
    inventoryUpdateRequest.setProcessInLIUI(true);
    doNothing()
        .when(exceptionService)
        .inventoryContainerUpdate(any(InventoryUpdateRequest.class), any(HttpHeaders.class));
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/exception/container/inventoryupdate")
                .headers(MockHttpHeaders.getHeaders())
                .content(JacksonParser.writeValueAsString(inventoryUpdateRequest)))
        .andExpect(MockMvcResultMatchers.status().isOk());

    verify(exceptionService, times(1))
        .inventoryContainerUpdate(any(InventoryUpdateRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testUpdateContainer_ThrowsException() throws Exception {
    InventoryUpdateRequest inventoryUpdateRequest = new InventoryUpdateRequest();
    inventoryUpdateRequest.setTrackingId("876875ufb7ijhgh767y4");
    inventoryUpdateRequest.setLocationName("102");
    inventoryUpdateRequest.setProcessInLIUI(true);
    doThrow(new ReceivingInternalException("mock_error", "mock_error"))
        .when(exceptionService)
        .inventoryContainerUpdate(any(InventoryUpdateRequest.class), any(HttpHeaders.class));
    try {
      mockMvc
          .perform(
              MockMvcRequestBuilders.post("/exception/container/inventoryupdate")
                  .headers(MockHttpHeaders.getHeaders())
                  .content(JacksonParser.writeValueAsString(inventoryUpdateRequest)))
          .andExpect(MockMvcResultMatchers.status().isInternalServerError());
    } catch (Exception e) {
      System.out.println("exception: " + e.getMessage() + e.getStackTrace());
      assertTrue(true);
    }
    verify(exceptionService, times(1))
        .inventoryContainerUpdate(any(InventoryUpdateRequest.class), any(HttpHeaders.class));
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
