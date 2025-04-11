package com.walmart.move.nim.receiving.controller;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.VNPK;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testng.Assert.assertNotEquals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.walmart.move.nim.receiving.base.ReceivingControllerTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.service.DeliveryServiceImpl;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.core.service.ReceiveContainerService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.lang.reflect.Type;
import java.util.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@RunWith(MockitoJUnitRunner.Silent.class)
public class ReceiptControllerTest extends ReceivingControllerTestBase {

  @Autowired private MockMvc mockMvc;

  private List<ReceiptSummaryResponse> receiptSummaryVnpkResponse;
  private List<ReceiptSummaryResponse> receiptSummaryEachesResponse;
  private ContainerRequest containerRequest = MockContainer.getContainerRequest();

  @Autowired public Gson gson;

  @Autowired @Mock public ReceiptService receiptService;
  @Autowired @Mock public ReceiveContainerService receiveContainerService;

  @Autowired
  @Mock
  @Qualifier(ReceivingConstants.DELIVERY_SERVICE)
  private DeliveryServiceImpl deliveryService;

  /** Initialization for mockito annotations. */
  @BeforeClass
  public void initMocks() {
    receiptSummaryVnpkResponse = new ArrayList<>();
    receiptSummaryVnpkResponse.add(
        new ReceiptSummaryEachesResponse("9763140004", 1, null, Long.valueOf(4)));
    receiptSummaryVnpkResponse.add(
        new ReceiptSummaryEachesResponse("9763140005", 1, null, Long.valueOf(2)));
    receiptSummaryVnpkResponse.add(
        new ReceiptSummaryEachesResponse("9763140007", 1, null, Long.valueOf(3)));

    receiptSummaryEachesResponse = new ArrayList<>();
    receiptSummaryEachesResponse.add(
        new ReceiptSummaryEachesResponse("9763140004", 1, null, Long.valueOf(96)));
    receiptSummaryEachesResponse.add(
        new ReceiptSummaryEachesResponse("9763140005", 1, null, Long.valueOf(96)));
    receiptSummaryEachesResponse.add(
        new ReceiptSummaryEachesResponse("9763140007", 1, null, Long.valueOf(144)));
  }

  @AfterMethod
  public void resetMocks() {
    reset(receiptService, receiveContainerService, deliveryService);
  }

  /**
   * Test case for success receipts/delivery/{deliveryNumber}/summary?uom={uom}
   *
   * <p>If {uom} is null or not provided or any other value other than EA/ZA during the calling of
   * the api will return response in eaches by default.
   *
   * <p>If {uom} is EA during the calling of the api will return response in eaches.
   *
   * <p>If {uom} is ZA during the calling of the api will return response in vendor pack.
   *
   * @throws Exception
   */
  @Test
  public void getReceivedQtySummaryByPOForDelivery_ShouldReturnSuccess() throws Exception {
    when(receiptService.getReceivedQtySummaryByPOForDelivery(any(Long.class), eq(VNPK)))
        .thenReturn(receiptSummaryVnpkResponse);

    when(receiptService.getReceivedQtySummaryByPOForDelivery(
            any(Long.class), eq(ReceivingConstants.Uom.EACHES)))
        .thenReturn(receiptSummaryEachesResponse);

    when(receiptService.getReceivedQtySummaryByPOForDelivery(any(Long.class), eq("")))
        .thenReturn(receiptSummaryEachesResponse);
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(ReceivingConstants.USER_ID_HEADER_KEY, "sysadmin");
    mockMvc
        .perform(
            MockMvcRequestBuilders.get(
                    "/receipts/delivery/21119003/summary?uom=ZA&deliveryStatus=ARV")
                .headers(httpHeaders))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(content().json(gson.toJson(receiptSummaryVnpkResponse)));

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/receipts/delivery/21119003/summary?uom=EA")
                .headers(httpHeaders))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(content().json(gson.toJson(receiptSummaryEachesResponse)));

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/receipts/delivery/21119003/summary").headers(httpHeaders))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(content().json(gson.toJson(receiptSummaryEachesResponse)));

    verify(receiptService, times(3))
        .getReceivedQtySummaryByPOForDelivery(any(Long.class), any(String.class));

    verify(deliveryService, times(1))
        .publishArrivedDeliveryStatusToOpen(
            anyLong(), eq(DeliveryStatus.ARV.toString()), any(HttpHeaders.class));

    verify(deliveryService, times(3))
        .publishArrivedDeliveryStatusToOpen(
            anyLong(), nullable(String.class), any(HttpHeaders.class));
  }

  /**
   * Test case for bad request receipts/delivery/{deliveryNumber}/summary?uom={uom}
   *
   * <p>If {deliveryNumber} is null or not provided or any other value other than positive long
   * integer during the calling of the api will return HTTP 400 Bad request.
   *
   * @throws Exception
   */
  @Test
  public void getReceivedQtySummaryByPOForDelivery_ShouldReturnBadRequest() throws Exception {

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/receipts/delivery/-21119003/summary?uom=ZA")
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/receipts/delivery/abcd/summary?uom=ZA")
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/receipts/delivery/null/summary?uom=ZA")
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/receipts/delivery/-21119003/summary")
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/receipts/delivery/abcd/summary")
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/receipts/delivery/null/summary")
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isBadRequest());
  }

  /**
   * Test case for success receipts/delivery/{deliveryNumber}/containers
   *
   * @throws Exception
   */
  @Test
  public void testGenerateReceipt_ReturnSuccess() throws Exception {
    try {
      Container containerResponse = new Container();
      containerResponse.setTrackingId(containerRequest.getTrackingId());
      when(receiveContainerService.receiveContainer(any(), any(), any(HttpHeaders.class)))
          .thenReturn(containerResponse);
      String response =
          mockMvc
              .perform(
                  MockMvcRequestBuilders.post("/receipts/delivery/1234/containers")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(gson.toJson(containerRequest))
                      .headers(MockHttpHeaders.getHeaders()))
              .andExpect(status().isCreated())
              .andExpect(content().contentType("application/json"))
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertNotEquals(response, null);

      Container container =
          new GsonBuilder()
              .registerTypeAdapter(
                  Date.class,
                  new JsonDeserializer<Date>() {
                    public Date deserialize(
                        JsonElement jsonElement, Type type, JsonDeserializationContext context)
                        throws JsonParseException {
                      return new Date(jsonElement.getAsJsonPrimitive().getAsLong());
                    }
                  })
              .create()
              .fromJson(response, Container.class);
      assertEquals(container.getTrackingId(), containerRequest.getTrackingId());
    } catch (ReceivingException receivingException) {
      assert (false);
    }
  }

  /**
   * Test case for bad request receipts/delivery/{deliveryNumber}/containers
   *
   * @throws Exception
   */
  @Test
  public void testGenerateReceipt_ReturnBadRequest() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/receipts/delivery/null/containers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(gson.toJson(containerRequest))
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void getReceiptSummaryByPo_ReturnsBadResponse() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/receipts/delivery/abcd/qtybypo/summary")
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/receipts/delivery/null/qtybypo/summary")
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void getReceiptSummaryByPo_SuccessResponse() throws Exception {
    ReceiptSummaryQtyByPoResponse receiptSummaryResponse = new ReceiptSummaryQtyByPoResponse();
    receiptSummaryResponse.setDeliveryNumber(21119003L);
    receiptSummaryResponse.setReceivedQty(100);
    when(receiptService.getReceiptsSummaryByPo(any(Long.class), any(HttpHeaders.class)))
        .thenReturn(receiptSummaryResponse);

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/receipts/delivery/21119003/qtybypo/summary")
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isOk())
        .andExpect(content().json(gson.toJson(receiptSummaryResponse)));

    verify(receiptService, times(1))
        .getReceiptsSummaryByPo(any(Long.class), any(HttpHeaders.class));
  }

  @Test
  public void getReceiptSummaryByPo_ErrorResponse() throws Exception {
    Long deliveryNumber = 21119003L;
    doThrow(
            new ReceivingException(
                String.format(
                    ReceivingException.RECEIVED_QTY_SUMMARY_BY_PO_ERROR_MESSAGE,
                    deliveryNumber,
                    "No Delivery found"),
                HttpStatus.INTERNAL_SERVER_ERROR,
                ReceivingException.RECEIVED_QTY_SUMMARY_BY_PO_ERROR_CODE))
        .when(receiptService)
        .getReceiptsSummaryByPo(any(Long.class), any(HttpHeaders.class));

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/receipts/delivery/21119003/qtybypo/summary")
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().is5xxServerError());
    verify(receiptService, times(1))
        .getReceiptsSummaryByPo(any(Long.class), any(HttpHeaders.class));
  }

  @Test
  public void getReceiptSummaryByPoLine_ReturnsBadResponse() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/receipts/delivery/313132323/qtybypoline/summary")
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            MockMvcRequestBuilders.get(
                    "/receipts/delivery/313132323/qtybypoline/summary?purchaseReferenceNumber=")
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void getReceiptSummaryByPoLine_SuccessResponse() throws Exception {
    ReceiptSummaryQtyByPoLineResponse receiptSummaryQtyByPoLineResponse =
        new ReceiptSummaryQtyByPoLineResponse();
    receiptSummaryQtyByPoLineResponse.setPurchaseReferenceNumber("34533232");
    receiptSummaryQtyByPoLineResponse.setSummary(Collections.emptyList());
    receiptSummaryQtyByPoLineResponse.setPoFinalized(false);
    receiptSummaryQtyByPoLineResponse.setReceivedQtyUom(VNPK);
    when(receiptService.getReceiptsSummaryByPoLine(
            any(Long.class), anyString(), any(HttpHeaders.class)))
        .thenReturn(receiptSummaryQtyByPoLineResponse);

    final ResultMatcher resultMatcherOk = status().isOk();
    final ResultActions resultActions =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get(
                        "/receipts/delivery/21119003/qtybypoline/summary?purchaseReferenceNumber=34533232")
                    .headers(MockHttpHeaders.getHeaders()))
            .andExpect(resultMatcherOk);
    final String actualJsonResponse = resultActions.andReturn().getResponse().getContentAsString();
    final String expectedJsonResponse =
        "{\"purchaseReferenceNumber\":\"34533232\",\"receivedQtyUom\":\"ZA\",\"summary\":[],\"totalReceivedQty\":0,\"totalFreightBillQty\":0,\"poFinalized\":false}";
    assertEquals(expectedJsonResponse, actualJsonResponse);

    verify(receiptService, times(1))
        .getReceiptsSummaryByPoLine(any(Long.class), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void getReceiptSummaryByPoLine_ErrorResponse() throws Exception {
    Long deliveryNumber = 21119003L;
    doThrow(
            new ReceivingException(
                String.format(
                    ReceivingException.RECEIVED_QTY_SUMMARY_BY_PO_LINE_ERROR_MESSAGE,
                    deliveryNumber,
                    "No Delivery found"),
                HttpStatus.INTERNAL_SERVER_ERROR,
                ReceivingException.RECEIVED_QTY_SUMMARY_BY_PO_LINE_ERROR_MESSAGE))
        .when(receiptService)
        .getReceiptsSummaryByPoLine(any(Long.class), anyString(), any(HttpHeaders.class));

    mockMvc
        .perform(
            MockMvcRequestBuilders.get(
                    "/receipts/delivery/21119003/qtybypoline/summary?purchaseReferenceNumber=3223233")
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().is5xxServerError());
    verify(receiptService, times(1))
        .getReceiptsSummaryByPoLine(any(Long.class), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void getTotalReceivedQtyByDeliveries_Success() throws Exception {
    ReceiptSummaryQtyByDeliveries receiptSummaryQtyByDeliveries =
        new ReceiptSummaryQtyByDeliveries();
    receiptSummaryQtyByDeliveries.setRcvdQtyUOM(VNPK);
    List<String> deliveries = Arrays.asList("3243434", "5332323");
    receiptSummaryQtyByDeliveries.setDeliveries(deliveries);
    List<ReceiptQtySummaryByDeliveryNumberResponse> receiptSummaryResponse = new ArrayList<>();
    ReceiptQtySummaryByDeliveryNumberResponse receiptSummaryQtyByDeliveryNumberResponse1 =
        new ReceiptQtySummaryByDeliveryNumberResponse();
    receiptSummaryQtyByDeliveryNumberResponse1.setDeliveryNumber(3243434L);
    receiptSummaryQtyByDeliveryNumberResponse1.setReceivedQty(323L);
    receiptSummaryQtyByDeliveryNumberResponse1.setReceivedQtyUom(VNPK);
    ReceiptQtySummaryByDeliveryNumberResponse receiptSummaryQtyByDeliveryNumberResponse2 =
        new ReceiptQtySummaryByDeliveryNumberResponse();
    receiptSummaryQtyByDeliveryNumberResponse2.setDeliveryNumber(5332323L);
    receiptSummaryQtyByDeliveryNumberResponse2.setReceivedQty(100L);
    receiptSummaryQtyByDeliveryNumberResponse2.setReceivedQtyUom(VNPK);
    receiptSummaryResponse.add(receiptSummaryQtyByDeliveryNumberResponse1);
    receiptSummaryResponse.add(receiptSummaryQtyByDeliveryNumberResponse2);

    when(receiptService.getReceiptQtySummaryByDeliveries(
            any(ReceiptSummaryQtyByDeliveries.class), any(HttpHeaders.class)))
        .thenReturn(receiptSummaryResponse);

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/receipts/deliveries")
                .contentType(MediaType.APPLICATION_JSON)
                .content(gson.toJson(receiptSummaryQtyByDeliveries))
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isOk())
        .andExpect(content().json(gson.toJson(receiptSummaryResponse)));

    verify(receiptService, times(1))
        .getReceiptQtySummaryByDeliveries(
            any(ReceiptSummaryQtyByDeliveries.class), any(HttpHeaders.class));
  }

  @Test
  public void getTotalReceivedQtyByDeliveries_ErrorResponse() throws Exception {
    ReceiptSummaryQtyByDeliveries receiptSummaryQtyByDeliveries =
        new ReceiptSummaryQtyByDeliveries();
    receiptSummaryQtyByDeliveries.setRcvdQtyUOM(VNPK);
    List<String> deliveries = Arrays.asList("3243434", "5332323");
    receiptSummaryQtyByDeliveries.setDeliveries(deliveries);
    doThrow(
            new ReceivingException(
                String.format(ReceivingConstants.RDS_RESPONSE_ERROR_MSG, "I/O Error"),
                HttpStatus.INTERNAL_SERVER_ERROR,
                ExceptionCodes.RDS_RECEIVED_QTY_SUMMARY_BY_DELIVERY_NUMBERS))
        .when(receiptService)
        .getReceiptQtySummaryByDeliveries(
            any(ReceiptSummaryQtyByDeliveries.class), any(HttpHeaders.class));
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/receipts/deliveries")
                .contentType(MediaType.APPLICATION_JSON)
                .content(gson.toJson(receiptSummaryQtyByDeliveries))
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().is5xxServerError());
    verify(receiptService, times(1))
        .getReceiptQtySummaryByDeliveries(
            any(ReceiptSummaryQtyByDeliveries.class), any(HttpHeaders.class));
  }

  @Test
  public void getStoreDistributionByDeliveryNumberPoLinePoLineNumber_SuccessResponse()
      throws Exception {
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    List<DeliveryDocument> deliveryDocuments = Arrays.asList(deliveryDocument);

    when(receiptService.getStoreDistributionByDeliveryAndPoPoLine(
            any(Long.class),
            any(String.class),
            any(Integer.class),
            any(HttpHeaders.class),
            any(Boolean.class)))
        .thenReturn(deliveryDocuments);
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    mockMvc
        .perform(
            MockMvcRequestBuilders.get(
                    "/receipts/delivery/39602886/po/4576669261/poLine/1/storeDistribution?isAtlasItem=false")
                .headers(httpHeaders))
        .andExpect(status().isOk())
        .andExpect(content().json(gson.toJson(deliveryDocuments)));

    verify(receiptService, times(1))
        .getStoreDistributionByDeliveryAndPoPoLine(
            any(Long.class),
            any(String.class),
            any(Integer.class),
            any(HttpHeaders.class),
            any(Boolean.class));
  }

  @Test
  public void getStoreDistributionByDeliveryNumberPoLinePoLineNumber_ErrorResponse()
      throws Exception {

    doThrow(
            new ReceivingException(
                String.format(ReceivingConstants.DELIVERY_DOCUMENT_ERROR_MSG, "I/O Error"),
                HttpStatus.INTERNAL_SERVER_ERROR,
                ExceptionCodes.DELIVERY_DOCUMENT_BY_PO_POLINE_ERROR_RESPONSE))
        .when(receiptService)
        .getStoreDistributionByDeliveryAndPoPoLine(
            anyLong(), anyString(), any(), any(HttpHeaders.class), any(Boolean.class));

    mockMvc
        .perform(
            MockMvcRequestBuilders.get(
                    "/receipts/delivery/-21119003/po/4576669261/poLine/1/storeDistribution?isAtlasItem=false")
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            MockMvcRequestBuilders.get(
                    "/receipts/delivery/null/po/4576669261/poLine/1/storeDistribution?isAtlasItem=false")
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void getTotalReceivedQtyByPoNumbers_Success() throws Exception {
    ReceiptSummaryQtyByPos receiptSummaryQtyByPos = new ReceiptSummaryQtyByPos();
    receiptSummaryQtyByPos.setRcvdQtyUOM(VNPK);
    List<String> poNumbers = Arrays.asList("3243434", "5332323");
    receiptSummaryQtyByPos.setPoNumbers(poNumbers);
    List<ReceiptQtySummaryByPoNumbersResponse> receiptSummaryByPoNumber = new ArrayList<>();
    ReceiptQtySummaryByPoNumbersResponse receiptSummaryQtyByPoNumberResponse1 =
        new ReceiptQtySummaryByPoNumbersResponse();
    receiptSummaryQtyByPoNumberResponse1.setPoNumber("3243434");
    receiptSummaryQtyByPoNumberResponse1.setReceivedQty(323L);
    receiptSummaryQtyByPoNumberResponse1.setReceivedQtyUom(VNPK);
    ReceiptQtySummaryByPoNumbersResponse receiptSummaryQtyByPoNumberResponse2 =
        new ReceiptQtySummaryByPoNumbersResponse();
    receiptSummaryQtyByPoNumberResponse2.setPoNumber("5332323");
    receiptSummaryQtyByPoNumberResponse2.setReceivedQty(100L);
    receiptSummaryQtyByPoNumberResponse2.setReceivedQtyUom(VNPK);
    receiptSummaryByPoNumber.add(receiptSummaryQtyByPoNumberResponse1);
    receiptSummaryByPoNumber.add(receiptSummaryQtyByPoNumberResponse2);

    when(receiptService.getReceiptQtySummaryByPoNumbers(
            any(ReceiptSummaryQtyByPos.class), any(HttpHeaders.class)))
        .thenReturn(receiptSummaryByPoNumber);

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/receipts/poNumbers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(gson.toJson(receiptSummaryByPoNumber))
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isOk())
        .andExpect(content().json(gson.toJson(receiptSummaryByPoNumber)));

    verify(receiptService, times(1))
        .getReceiptQtySummaryByPoNumbers(any(ReceiptSummaryQtyByPos.class), any(HttpHeaders.class));
  }

  @Test
  public void getTotalReceivedQtyByPoNumbers_ErrorResponse() throws Exception {
    ReceiptSummaryQtyByPos receiptSummaryQtyByPos = new ReceiptSummaryQtyByPos();
    receiptSummaryQtyByPos.setRcvdQtyUOM(VNPK);
    List<String> poNumbers = Arrays.asList("3243434", "5332323");
    receiptSummaryQtyByPos.setPoNumbers(poNumbers);
    List<ReceiptQtySummaryByPoNumbersResponse> receiptSummaryByPoNumber = new ArrayList<>();
    doThrow(
            new ReceivingException(
                "No record found in receipt for poNumbers",
                HttpStatus.INTERNAL_SERVER_ERROR,
                ExceptionCodes.RECEIPTS_NOT_FOUND))
        .when(receiptService)
        .getStoreDistributionByDeliveryAndPoPoLine(
            anyLong(), anyString(), any(), any(HttpHeaders.class), any(Boolean.class));
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/receipts/poNumbers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(gson.toJson(receiptSummaryByPoNumber))
                .headers(MockHttpHeaders.getHeaders()))
        .andExpect(status().isOk())
        .andExpect(content().json(gson.toJson(receiptSummaryByPoNumber)));

    verify(receiptService, times(1))
        .getReceiptQtySummaryByPoNumbers(any(ReceiptSummaryQtyByPos.class), any(HttpHeaders.class));
  }
}
