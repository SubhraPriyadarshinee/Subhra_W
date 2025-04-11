package com.walmart.move.nim.receiving.core.client.nimrds;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.VNPK;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.walmart.move.nim.receiving.core.client.nimrds.model.*;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.ReceiptQtySummaryByDeliveryNumberResponse;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryQtyByDeliveries;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryQtyByPoLineResponse;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.data.MockRxHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("unchecked")
public class NimRDSRestApiClientTest {

  @Mock private AppConfig appConfig;

  private Gson gson = new Gson();

  @Mock private RestConnector retryableRestConnector;

  @InjectMocks private NimRDSRestApiClient nimRDSRestApiClient;

  @BeforeMethod
  public void createNimRDSRestApiClient() throws Exception {
    MockitoAnnotations.initMocks(this);

    TenantContext.setFacilityNum(32897);
    TenantContext.setFacilityCountryCode("us");
    ReflectionTestUtils.setField(nimRDSRestApiClient, "gson", gson);
  }

  @AfterMethod
  public void resetMocks() {
    reset(appConfig, retryableRestConnector);
  }

  private String getMockRdsConfig() {
    JsonObject mockRdsConfig = new JsonObject();
    mockRdsConfig.addProperty("32897", "http://nimservices.s32818.us:7099");
    mockRdsConfig.addProperty("6001", "http://nimservices.s32818.us:7099");
    mockRdsConfig.addProperty("32898", "http://nimservices.s32818.us:7099");
    return mockRdsConfig.toString();
  }

  @Test
  public void test_receiveContainers() throws Exception {

    doReturn(getMockRdsConfig()).when(appConfig).getNimRDSServiceBaseUrl();

    File resource = new ClassPathResource("NimRdsReceiveContainer_Response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    File reqResource = new ClassPathResource("NimRdsReceiveContainer_Request.json").getFile();
    String mockRequest = new String(Files.readAllBytes(reqResource.toPath()));

    ReceiveContainersRequestBody mockReceiveContainersRequestBody =
        gson.fromJson(mockRequest, ReceiveContainersRequestBody.class);

    ReceiveContainersResponseBody receiveContainersResponseBody =
        nimRDSRestApiClient.receiveContainers(
            mockReceiveContainersRequestBody, MockRxHttpHeaders.getMockHeadersMap());

    assertNotNull(receiveContainersResponseBody);
    assertTrue(CollectionUtils.isEmpty(receiveContainersResponseBody.getErrors()));

    assertEquals(
        mockReceiveContainersRequestBody.getContainerOrders().get(0).getPoNumber(),
        receiveContainersResponseBody.getReceived().get(0).getPoNumber());
  }

  @Test
  public void test_receiveContainers_clientError() throws Exception {

    doReturn("http://nimservices.s32818.us:7099").when(appConfig).getNimRDSServiceBaseUrl();
    String errorResponse =
        "{\n"
            + "    \"sneEnabled\": true,\n"
            + "    \"received\": [],\n"
            + "    \"errors\": [\n"
            + "        {\n"
            + "            \"errorCode\": \"NIMRDS-017\",\n"
            + "            \"message\": \"rc=-2, message=Unable to create label\",\n"
            + "            \"_id\": \"b328970000200000000353906\"\n"
            + "        }\n"
            + "    ]\n"
            + "}";
    doReturn(getMockRdsConfig()).when(appConfig).getNimRDSServiceBaseUrl();
    doThrow(
            new RestClientResponseException(
                "Error",
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                null,
                errorResponse.getBytes(),
                null))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    File reqResource = new ClassPathResource("NimRdsReceiveContainer_Request.json").getFile();
    String mockRequest = new String(Files.readAllBytes(reqResource.toPath()));

    ReceiveContainersRequestBody mockReceiveContainersRequestBody =
        gson.fromJson(mockRequest, ReceiveContainersRequestBody.class);

    try {
      nimRDSRestApiClient.receiveContainers(
          mockReceiveContainersRequestBody, MockRxHttpHeaders.getMockHeadersMap());

    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.INVALID_RDS_SLOTTING_REQ);
    } catch (Exception e) {
      throw e;
    }
  }

  @Test
  public void test_receiveContainers_serverError() throws Exception {

    doReturn(getMockRdsConfig()).when(appConfig).getNimRDSServiceBaseUrl();

    doThrow(new ResourceAccessException("mock_error"))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    File reqResource = new ClassPathResource("NimRdsReceiveContainer_Request.json").getFile();
    String mockRequest = new String(Files.readAllBytes(reqResource.toPath()));

    ReceiveContainersRequestBody mockReceiveContainersRequestBody =
        gson.fromJson(mockRequest, ReceiveContainersRequestBody.class);

    try {
      nimRDSRestApiClient.receiveContainers(
          mockReceiveContainersRequestBody, MockRxHttpHeaders.getMockHeadersMap());

    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.INVALID_SLOTTING_REQ);
    } catch (Exception e) {
      throw e;
    }
  }

  @Test
  public void test_quantityChange() throws Exception {
    doReturn(getMockRdsConfig()).when(appConfig).getNimRDSServiceBaseUrl();

    JsonObject mockPalletStatusResponseJson = new JsonObject();
    mockPalletStatusResponseJson.addProperty("scan_tag", "MOCK_SCAN_TAG_UNIT_TEST");
    mockPalletStatusResponseJson.addProperty("return_code", 0);
    mockPalletStatusResponseJson.addProperty("return_text", "Success");

    JsonObject mockResponseJson = new JsonObject();
    mockResponseJson.add("palletStatus", mockPalletStatusResponseJson);

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponseJson.toString(), HttpStatus.OK);

    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    QuantityChangeRequestBody quantityChangeRequestBody = new QuantityChangeRequestBody();
    quantityChangeRequestBody.setQuantity(100);
    quantityChangeRequestBody.setScanTag("MOCK_SCAN_TAG_UNIT_TEST");
    quantityChangeRequestBody.setUserId("rxTestUser");

    QuantityChangeResponseBody quantityChangeResponseBody =
        nimRDSRestApiClient.quantityChange(
            quantityChangeRequestBody, MockRxHttpHeaders.getMockHeadersMap());

    assertNotNull(quantityChangeResponseBody);
    assertNotNull(quantityChangeResponseBody.getPalletStatus());
    assertEquals(
        quantityChangeResponseBody.getPalletStatus().getScanTag(), "MOCK_SCAN_TAG_UNIT_TEST");
  }

  @Test
  public void test_quantityChange_clientError() throws Exception {

    doReturn(getMockRdsConfig()).when(appConfig).getNimRDSServiceBaseUrl();

    doThrow(
            new RestClientResponseException(
                "Error",
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                null,
                "Error".getBytes(),
                null))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    QuantityChangeRequestBody quantityChangeRequestBody = new QuantityChangeRequestBody();
    quantityChangeRequestBody.setQuantity(100);
    quantityChangeRequestBody.setScanTag("MOCK_SCAN_TAG_UNIT_TEST");
    quantityChangeRequestBody.setUserId("rxTestUser");

    try {
      nimRDSRestApiClient.quantityChange(
          quantityChangeRequestBody, MockRxHttpHeaders.getMockHeadersMap());
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.INVALID_QUANTITY_CORRECTION_REQ);
    } catch (Exception e) {
      throw e;
    }
  }

  @Test
  public void test_quantityChange_serverError() throws Exception {

    doReturn(getMockRdsConfig()).when(appConfig).getNimRDSServiceBaseUrl();

    doThrow(new ResourceAccessException("mock_error"))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    QuantityChangeRequestBody quantityChangeRequestBody = new QuantityChangeRequestBody();
    quantityChangeRequestBody.setQuantity(100);
    quantityChangeRequestBody.setScanTag("MOCK_SCAN_TAG_UNIT_TEST");
    quantityChangeRequestBody.setUserId("rxTestUser");

    try {
      nimRDSRestApiClient.quantityChange(
          quantityChangeRequestBody, MockRxHttpHeaders.getMockHeadersMap());
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.INVALID_QUANTITY_CORRECTION_REQ);
    } catch (Exception e) {
      throw e;
    }
  }

  @Test
  public void test_quantityChange_tenant_not_found() throws Exception {

    // wrong facility set for this test case
    TenantContext.setFacilityNum(32612);
    doReturn(getMockRdsConfig()).when(appConfig).getNimRDSServiceBaseUrl();

    doThrow(new ResourceAccessException("mock_error"))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    QuantityChangeRequestBody quantityChangeRequestBody = new QuantityChangeRequestBody();
    quantityChangeRequestBody.setQuantity(100);
    quantityChangeRequestBody.setScanTag("MOCK_SCAN_TAG_UNIT_TEST");
    quantityChangeRequestBody.setUserId("rxTestUser");

    try {
      nimRDSRestApiClient.quantityChange(
          quantityChangeRequestBody, MockRxHttpHeaders.getMockHeadersMap());
    } catch (ReceivingInternalException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.CONFIGURATION_ERROR);
      assertEquals(
          e.getMessage(),
          String.format(
              ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
    } catch (Exception e) {
      throw e;
    }
  }

  @Test
  public void test_receivedQuantity() throws Exception {

    doReturn(getMockRdsConfig()).when(appConfig).getNimRDSServiceBaseUrl();

    File resource = new ClassPathResource("NimRdsReceivedQuantity_Response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    File reqResource = new ClassPathResource("NimRdsReceivedQuantity_Request.json").getFile();
    String mockRequest = new String(Files.readAllBytes(reqResource.toPath()));

    RdsReceiptsRequest rdsReceiptsRequest = gson.fromJson(mockRequest, RdsReceiptsRequest.class);

    RdsReceiptsResponse rdsReceiptsResponse =
        nimRDSRestApiClient.quantityReceived(
            rdsReceiptsRequest, MockRxHttpHeaders.getMockHeadersMap());

    assertNotNull(rdsReceiptsResponse);
    assertTrue(CollectionUtils.isEmpty(rdsReceiptsResponse.getErrors()));

    assertEquals(
        rdsReceiptsRequest.getOrderLines().get(0).getPoNumber(),
        rdsReceiptsResponse.getFound().get(0).getPoNumber());
  }

  @Test
  public void test_receivedQuantity_clientError() throws Exception {

    doReturn(getMockRdsConfig()).when(appConfig).getNimRDSServiceBaseUrl();

    doThrow(
            new RestClientResponseException(
                "Error",
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                null,
                "Error".getBytes(),
                null))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    File reqResource = new ClassPathResource("NimRdsReceivedQuantity_Request.json").getFile();
    String mockRequest = new String(Files.readAllBytes(reqResource.toPath()));

    RdsReceiptsRequest rdsReceiptsRequest = gson.fromJson(mockRequest, RdsReceiptsRequest.class);

    try {
      nimRDSRestApiClient.quantityReceived(
          rdsReceiptsRequest, MockRxHttpHeaders.getMockHeadersMap());
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.INVALID_QUANTITY_RECEIVED_REQ);
    } catch (Exception e) {
      throw e;
    }
  }

  @Test
  public void test_receivedQuantity_serverError() throws Exception {

    doReturn(getMockRdsConfig()).when(appConfig).getNimRDSServiceBaseUrl();

    doThrow(new ResourceAccessException("mock_error"))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    File reqResource = new ClassPathResource("NimRdsReceivedQuantity_Request.json").getFile();
    String mockRequest = new String(Files.readAllBytes(reqResource.toPath()));

    RdsReceiptsRequest rdsReceiptsRequest = gson.fromJson(mockRequest, RdsReceiptsRequest.class);

    try {
      nimRDSRestApiClient.quantityReceived(
          rdsReceiptsRequest, MockRxHttpHeaders.getMockHeadersMap());
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.INVALID_QUANTITY_RECEIVED_REQ);
    } catch (Exception e) {
      throw e;
    }
  }

  @Test
  public void test_itemDetails() throws Exception {

    doReturn(getMockRdsConfig()).when(appConfig).getNimRDSServiceBaseUrl();

    File resource = new ClassPathResource("NimRdsItemDetails_Response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    List<String> request = Arrays.asList("3804890");
    ItemDetailsResponseBody itemDetailsResponse =
        nimRDSRestApiClient.itemDetails(request, MockRxHttpHeaders.getMockHeadersMap());

    assertNotNull(itemDetailsResponse);
    assertTrue(CollectionUtils.isEmpty(itemDetailsResponse.getErrors()));

    assertEquals(
        request.get(0), String.valueOf(itemDetailsResponse.getFound().get(0).getItem_nbr()));
  }

  @Test
  public void test_itemDetails_clientError() throws Exception {

    doReturn(getMockRdsConfig()).when(appConfig).getNimRDSServiceBaseUrl();

    doThrow(
            new RestClientResponseException(
                "Error",
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                null,
                "Error".getBytes(),
                null))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    List<String> request = Arrays.asList("563013663");

    ItemDetailsResponseBody itemDetailResponse = null;
    try {
      itemDetailResponse =
          nimRDSRestApiClient.itemDetails(request, MockRxHttpHeaders.getMockHeadersMap());
    } catch (ReceivingBadDataException e) {
      assertEquals(itemDetailResponse, null);
    } catch (Exception e) {
      throw e;
    }
  }

  @Test
  public void test_itemDetails_serverError() throws Exception {

    doReturn(getMockRdsConfig()).when(appConfig).getNimRDSServiceBaseUrl();

    doThrow(new ResourceAccessException("mock_error"))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    List<String> request = Arrays.asList("563013663");

    ItemDetailsResponseBody itemDetailResponse = null;
    try {
      itemDetailResponse =
          nimRDSRestApiClient.itemDetails(request, MockRxHttpHeaders.getMockHeadersMap());
    } catch (ReceivingBadDataException e) {
      assertEquals(itemDetailResponse, null);
    } catch (Exception e) {
      throw e;
    }
  }

  @Test
  public void testGetReceivedQtySummaryByPoReturnsSuccessResponse() throws Exception {
    Long deliveryNumber = 28668573L;
    doReturn(getMockRdsConfig()).when(appConfig).getNimRDSServiceBaseUrl();
    File resource = new ClassPathResource("NimRdsGetReceiptSummaryByPO_Response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    RdsReceiptsSummaryByPoResponse rdsReceiptsSummaryByPoResponse =
        nimRDSRestApiClient.getReceivedQtySummaryByPo(
            deliveryNumber,
            ReceivingUtils.getForwardablHeaderWithTenantData(MockHttpHeaders.getHeaders()));
    assertNotNull(rdsReceiptsSummaryByPoResponse);
    assertFalse(CollectionUtils.isEmpty(rdsReceiptsSummaryByPoResponse.getSummary()));

    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testGetReceivedQtySummaryByPoReturnsSuccessWithEmptyReceipts() throws Exception {
    Long deliveryNumber = 28668573L;
    doReturn(getMockRdsConfig()).when(appConfig).getNimRDSServiceBaseUrl();
    File resource =
        new ClassPathResource("NimRdsGetReceiptSummaryByPO_EmptyResponse.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    RdsReceiptsSummaryByPoResponse rdsReceiptsSummaryByPoResponse =
        nimRDSRestApiClient.getReceivedQtySummaryByPo(
            deliveryNumber,
            ReceivingUtils.getForwardablHeaderWithTenantData(MockHttpHeaders.getHeaders()));

    assertNotNull(rdsReceiptsSummaryByPoResponse);
    assertTrue(CollectionUtils.isEmpty(rdsReceiptsSummaryByPoResponse.getSummary()));

    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testGetReceivedQtySummaryByPoReturns4XXErrorResponse() throws Exception {
    Long deliveryNumber = 28668573L;
    doReturn(getMockRdsConfig()).when(appConfig).getNimRDSServiceBaseUrl();
    doThrow(
            new RestClientResponseException(
                "Error",
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                null,
                "Error".getBytes(),
                null))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      nimRDSRestApiClient.getReceivedQtySummaryByPo(
          deliveryNumber,
          ReceivingUtils.getForwardablHeaderWithTenantData(MockHttpHeaders.getHeaders()));
    } catch (ReceivingBadDataException exception) {
      assertEquals(
          exception.getErrorCode(), ExceptionCodes.RECEIVED_QTY_SUMMARY_BY_PO_ERROR_FROM_RDS);
      assertEquals(
          exception.getMessage(),
          String.format(ReceivingConstants.RDS_RESPONSE_ERROR_MSG, "Error"));
    }

    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testGetReceivedQtySummaryByPoReturns5XXErrorResponse() throws Exception {
    Long deliveryNumber = 28668573L;
    doReturn(getMockRdsConfig()).when(appConfig).getNimRDSServiceBaseUrl();
    doThrow(new ResourceAccessException("mock_error"))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      nimRDSRestApiClient.getReceivedQtySummaryByPo(
          deliveryNumber,
          ReceivingUtils.getForwardablHeaderWithTenantData(MockHttpHeaders.getHeaders()));
    } catch (ReceivingException exception) {
      assertEquals(
          exception.getErrorResponse().getErrorCode(),
          ExceptionCodes.RECEIVED_QTY_SUMMARY_BY_PO_ERROR_FROM_RDS);
      assertEquals(
          exception.getErrorResponse().getErrorMessage(),
          String.format(ReceivingConstants.RDS_RESPONSE_ERROR_MSG, "mock_error"));
      assertEquals(exception.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testGetReceivedQtySummaryByPoLineReturnsSuccessResponse() throws Exception {
    Long deliveryNumber = 28668573L;
    String purchaseReferenceNumber = "1523836513";
    doReturn(getMockRdsConfig()).when(appConfig).getNimRDSServiceBaseUrl();
    File resource =
        new ClassPathResource("NimRdsGetReceiptSummaryByPoLine_Response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    ReceiptSummaryQtyByPoLineResponse rdsReceiptsSummaryByPoLineResponse =
        nimRDSRestApiClient.getReceivedQtySummaryByPoLine(
            deliveryNumber,
            purchaseReferenceNumber,
            ReceivingUtils.getForwardablHeaderWithTenantData(MockHttpHeaders.getHeaders()));
    assertNotNull(rdsReceiptsSummaryByPoLineResponse);
    assertFalse(CollectionUtils.isEmpty(rdsReceiptsSummaryByPoLineResponse.getSummary()));

    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    verify(appConfig, times(1)).getNimRDSServiceBaseUrl();
  }

  @Test
  public void testGetReceivedQtySummaryByPoLineReturnsSuccessWithEmptyReceipts() throws Exception {
    Long deliveryNumber = 28668573L;
    String purchaseReferenceNumber = "1523836513";
    File resource =
        new ClassPathResource("NimRdsGetReceiptSummaryByPoLine_EmptyResponse.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    doReturn(getMockRdsConfig()).when(appConfig).getNimRDSServiceBaseUrl();
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    ReceiptSummaryQtyByPoLineResponse receiptSummaryQtyByPoLineResponse =
        nimRDSRestApiClient.getReceivedQtySummaryByPoLine(
            deliveryNumber,
            purchaseReferenceNumber,
            ReceivingUtils.getForwardablHeaderWithTenantData(MockHttpHeaders.getHeaders()));

    assertNotNull(receiptSummaryQtyByPoLineResponse);
    assertTrue(CollectionUtils.isEmpty(receiptSummaryQtyByPoLineResponse.getSummary()));

    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    verify(appConfig, times(1)).getNimRDSServiceBaseUrl();
  }

  @Test
  public void testGetReceivedQtySummaryByPoLineReturns4XXErrorResponse() throws Exception {
    Long deliveryNumber = 28668573L;
    String purchaseReferenceNumber = "1523836513";
    doReturn(getMockRdsConfig()).when(appConfig).getNimRDSServiceBaseUrl();
    doThrow(
            new RestClientResponseException(
                "Error",
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                null,
                "Error".getBytes(),
                null))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      nimRDSRestApiClient.getReceivedQtySummaryByPoLine(
          deliveryNumber,
          purchaseReferenceNumber,
          ReceivingUtils.getForwardablHeaderWithTenantData(MockHttpHeaders.getHeaders()));
    } catch (ReceivingBadDataException exception) {
      assertEquals(
          exception.getErrorCode(), ExceptionCodes.RECEIVED_QTY_SUMMARY_BY_POLINE_ERROR_FROM_RDS);
      assertEquals(
          exception.getMessage(),
          String.format(ReceivingConstants.RDS_RESPONSE_ERROR_MSG, "Error"));
    }

    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testGetReceivedQtySummaryByDeliveriesReturnsSuccessResponse() throws Exception {
    ReceiptSummaryQtyByDeliveries receiptSummaryQtyByDeliveries =
        new ReceiptSummaryQtyByDeliveries();
    receiptSummaryQtyByDeliveries.setRcvdQtyUOM(VNPK);
    List<String> deliveries = Arrays.asList("3243434", "5332323");
    receiptSummaryQtyByDeliveries.setDeliveries(deliveries);
    doReturn(getMockRdsConfig()).when(appConfig).getNimRDSServiceBaseUrl();
    File resource =
        new ClassPathResource("NimRdsReceiptSummaryByDeliveriesResponse.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    List<ReceiptQtySummaryByDeliveryNumberResponse> receiptSummaryQtyByDeliveryNumberResponse =
        nimRDSRestApiClient.getReceivedQtySummaryByDeliveryNumbers(
            receiptSummaryQtyByDeliveries,
            ReceivingUtils.getForwardablHeaderWithTenantData(MockHttpHeaders.getHeaders()));

    assertFalse(receiptSummaryQtyByDeliveryNumberResponse.isEmpty());
    assertEquals(receiptSummaryQtyByDeliveryNumberResponse.get(0).getReceivedQty().intValue(), 10);

    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    verify(appConfig, times(1)).getNimRDSServiceBaseUrl();
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testGetReceivedQtySummaryByDeliveriesReturns5xxErrorResponse() throws Exception {
    ReceiptSummaryQtyByDeliveries receiptSummaryQtyByDeliveries =
        new ReceiptSummaryQtyByDeliveries();
    receiptSummaryQtyByDeliveries.setRcvdQtyUOM(VNPK);
    List<String> deliveries = Arrays.asList("3243434", "5332323");
    receiptSummaryQtyByDeliveries.setDeliveries(deliveries);
    doReturn(getMockRdsConfig()).when(appConfig).getNimRDSServiceBaseUrl();

    doThrow(new ResourceAccessException("mock_error"))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    nimRDSRestApiClient.getReceivedQtySummaryByDeliveryNumbers(
        receiptSummaryQtyByDeliveries,
        ReceivingUtils.getForwardablHeaderWithTenantData(MockHttpHeaders.getHeaders()));

    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    verify(appConfig, times(1)).getNimRDSServiceBaseUrl();
  }

  @Test
  public void testGetReceivedQtySummaryByPoLineReturns5XXErrorResponse() throws Exception {
    Long deliveryNumber = 28668573L;
    String purchaseReferenceNumber = "1523836513";
    doReturn(getMockRdsConfig()).when(appConfig).getNimRDSServiceBaseUrl();
    doThrow(new ResourceAccessException("mock_error"))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      nimRDSRestApiClient.getReceivedQtySummaryByPoLine(
          deliveryNumber,
          purchaseReferenceNumber,
          ReceivingUtils.getForwardablHeaderWithTenantData(MockHttpHeaders.getHeaders()));
    } catch (ReceivingException exception) {
      assertEquals(
          exception.getErrorResponse().getErrorCode(),
          ExceptionCodes.RECEIVED_QTY_SUMMARY_BY_POLINE_ERROR_FROM_RDS);
      assertEquals(
          exception.getErrorResponse().getErrorMessage(),
          String.format(ReceivingConstants.RDS_RESPONSE_ERROR_MSG, "mock_error"));
      assertEquals(exception.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void test_DALabelBackout_IsSuccess() throws Exception {
    doReturn(getMockRdsConfig()).when(appConfig).getNimRDSServiceBaseUrl();
    String mockLabelBackoutStatusResponseJson =
        "{\n"
            + "  \"labels\": [\n"
            + "    {\n"
            + "      \"scan_tag\": \"lpn1234\",\n"
            + "      \"return_code\": \"0\",\n"
            + "      \"return_text\": \"Success\"\n"
            + "    },\n"
            + "    {\n"
            + "      \"scan_tag\": \"lpn1235\",\n"
            + "      \"return_code\": \"0\",\n"
            + "      \"return_text\": \"Success\"\n"
            + "    }\n"
            + "  ]\n"
            + "}";

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<>(mockLabelBackoutStatusResponseJson, HttpStatus.OK);

    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    DALabelBackoutRequest daLabelBackoutRequest = new DALabelBackoutRequest();
    daLabelBackoutRequest.setLabels(Arrays.asList("lpn1234", "lpn1235"));
    daLabelBackoutRequest.setUserId("sysadmin");

    DALabelBackoutResponse daLabelBackoutResponse =
        nimRDSRestApiClient.labelBackout(
            daLabelBackoutRequest, MockRxHttpHeaders.getMockHeadersMap());

    assertNotNull(daLabelBackoutResponse);

    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_DALabelBackout_ThrowsReceivingBadDataException() throws Exception {
    doReturn(getMockRdsConfig()).when(appConfig).getNimRDSServiceBaseUrl();

    doThrow(
            new RestClientResponseException(
                "Some error.",
                HttpStatus.BAD_REQUEST.value(),
                "",
                null,
                "".getBytes(),
                StandardCharsets.UTF_8))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    DALabelBackoutRequest daLabelBackoutRequest = new DALabelBackoutRequest();
    daLabelBackoutRequest.setLabels(Arrays.asList("lpn1234"));
    daLabelBackoutRequest.setUserId("sysadmin");

    DALabelBackoutResponse daLabelBackoutResponse =
        nimRDSRestApiClient.labelBackout(
            daLabelBackoutRequest, MockRxHttpHeaders.getMockHeadersMap());

    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void test_DALabelBackout_ThrowsReceivingInternalException() throws Exception {
    doReturn(getMockRdsConfig()).when(appConfig).getNimRDSServiceBaseUrl();

    doThrow(new ResourceAccessException("Some error."))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    DALabelBackoutRequest daLabelBackoutRequest = new DALabelBackoutRequest();
    daLabelBackoutRequest.setLabels(Arrays.asList("lpn1234"));
    daLabelBackoutRequest.setUserId("sysadmin");

    DALabelBackoutResponse daLabelBackoutResponse =
        nimRDSRestApiClient.labelBackout(
            daLabelBackoutRequest, MockRxHttpHeaders.getMockHeadersMap());

    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void test_DALabelBackout_CatchesResourceAccessException() throws Exception {
    doReturn(getMockRdsConfig()).when(appConfig).getNimRDSServiceBaseUrl();

    doThrow(new ResourceAccessException("mock_error"))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    DALabelBackoutRequest daLabelBackoutRequest = new DALabelBackoutRequest();
    daLabelBackoutRequest.setLabels(Arrays.asList("lpn1234"));
    daLabelBackoutRequest.setUserId("sysadmin");

    try {
      DALabelBackoutResponse daLabelBackoutResponse =
          nimRDSRestApiClient.labelBackout(
              daLabelBackoutRequest, MockRxHttpHeaders.getMockHeadersMap());
    } catch (ReceivingInternalException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.NIM_RDS_SERVICE_UNAVAILABLE_ERROR);
      assertEquals(e.getMessage(), ReceivingConstants.NIM_RDS_SERVICE_UNAVAILABLE_ERROR);
    }

    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void test_DsdcReceive_IsSuccess() throws Exception {
    doReturn(getMockRdsConfig()).when(appConfig).getNimRDSServiceBaseUrl();

    JsonObject mockDsdcReceiveStatusResponseJson = new JsonObject();
    mockDsdcReceiveStatusResponseJson.addProperty("message", "SUCCESS");
    mockDsdcReceiveStatusResponseJson.addProperty("slot", "R8002");
    mockDsdcReceiveStatusResponseJson.addProperty("batch", "617");
    mockDsdcReceiveStatusResponseJson.addProperty("store", "1");
    mockDsdcReceiveStatusResponseJson.addProperty("div", "2");
    mockDsdcReceiveStatusResponseJson.addProperty("pocode", "73");
    mockDsdcReceiveStatusResponseJson.addProperty("dccarton", "12345678");
    mockDsdcReceiveStatusResponseJson.addProperty("dept", "12");
    mockDsdcReceiveStatusResponseJson.addProperty("event", "DSDC EVENT");
    mockDsdcReceiveStatusResponseJson.addProperty("hazmat", "H");
    mockDsdcReceiveStatusResponseJson.addProperty("rcvr_nbr", "123456");
    mockDsdcReceiveStatusResponseJson.addProperty("po_nbr", "1234567890");
    mockDsdcReceiveStatusResponseJson.addProperty("label_bar_code", "000011212345123456");
    mockDsdcReceiveStatusResponseJson.addProperty("packs", "0");
    mockDsdcReceiveStatusResponseJson.addProperty("unscanned", "0");
    mockDsdcReceiveStatusResponseJson.addProperty("scanned", "0");
    mockDsdcReceiveStatusResponseJson.addProperty("auditFlag", "N");
    mockDsdcReceiveStatusResponseJson.addProperty("lane_nbr", "12");
    mockDsdcReceiveStatusResponseJson.addProperty("sneEnabled", "true");

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockDsdcReceiveStatusResponseJson.toString(), HttpStatus.OK);

    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    DsdcReceiveRequest dsdcReceiveRequest = new DsdcReceiveRequest();
    dsdcReceiveRequest.setPack_nbr("12345678901234567890");
    dsdcReceiveRequest.setManifest("12345678");
    dsdcReceiveRequest.setDoorNum("123");
    dsdcReceiveRequest.setUserId("sysadmin");

    DsdcReceiveResponse dsdcReceiveResponse =
        nimRDSRestApiClient.receiveDsdcPack(
            dsdcReceiveRequest, MockRxHttpHeaders.getMockHeadersMap());

    assertNotNull(dsdcReceiveResponse);

    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void test_DsdcReceiveCatchReceivingBadDataException() throws Exception {
    doReturn(getMockRdsConfig()).when(appConfig).getNimRDSServiceBaseUrl();

    String errorMessage =
        "{\n"
            + "    \"errorCode\": \"NIMRDS-022\",\n"
            + "    \"message\": \"RDS DSDC validation failed => Error: ASN information was not found\"\n"
            + "}";

    doThrow(
            new RestClientResponseException(
                "Some error.",
                HttpStatus.BAD_REQUEST.value(),
                "",
                null,
                errorMessage.getBytes(),
                StandardCharsets.UTF_8))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    DsdcReceiveRequest dsdcReceiveRequest = new DsdcReceiveRequest();
    dsdcReceiveRequest.setPack_nbr("12345678901234567890");
    dsdcReceiveRequest.setManifest("12345678");
    dsdcReceiveRequest.setDoorNum("123");
    dsdcReceiveRequest.setUserId("sysadmin");

    DsdcReceiveResponse dsdcReceiveResponse =
        nimRDSRestApiClient.receiveDsdcPack(
            dsdcReceiveRequest, MockRxHttpHeaders.getMockHeadersMap());

    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    assertEquals(dsdcReceiveResponse.getErrorCode(), "NIMRDS-022");
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void test_DsdcReceive_ThrowsReceivingInternalException() throws Exception {
    doReturn(getMockRdsConfig()).when(appConfig).getNimRDSServiceBaseUrl();

    doThrow(new ResourceAccessException("mock_error"))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    DsdcReceiveRequest dsdcReceiveRequest = new DsdcReceiveRequest();
    dsdcReceiveRequest.setPack_nbr("12345678901234567890");
    dsdcReceiveRequest.setManifest("12345678");
    dsdcReceiveRequest.setDoorNum("123");
    dsdcReceiveRequest.setUserId("sysadmin");

    DsdcReceiveResponse dsdcReceiveResponse =
        nimRDSRestApiClient.receiveDsdcPack(
            dsdcReceiveRequest, MockRxHttpHeaders.getMockHeadersMap());

    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testGetStoreDistributionByDeliveryDocumentLineSuccessResponse() throws IOException {
    String poNumber = "4576669261";
    Integer poLineNumber = 1;
    File resource = new ClassPathResource("NimRdsReceivedQuantity_Response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);
    doReturn(getMockRdsConfig()).when(appConfig).getNimRDSServiceBaseUrl();
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    nimRDSRestApiClient.getStoreDistributionByDeliveryDocumentLine(
        poNumber,
        poLineNumber,
        ReceivingUtils.getForwardablHeaderWithTenantData(MockHttpHeaders.getHeaders()));
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testGetStoreDistributionByDeliveryDocumentLine5xxErrorResponse() throws IOException {
    String poNumber = "4576669261";
    Integer poLineNumber = 1;

    doReturn(getMockRdsConfig()).when(appConfig).getNimRDSServiceBaseUrl();
    doThrow(new ResourceAccessException("mock_error"))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    nimRDSRestApiClient.getStoreDistributionByDeliveryDocumentLine(
        poNumber,
        poLineNumber,
        ReceivingUtils.getForwardablHeaderWithTenantData(MockHttpHeaders.getHeaders()));
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }
}
