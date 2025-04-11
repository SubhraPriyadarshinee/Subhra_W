package com.walmart.move.nim.receiving.core.client.dcfin;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.ADJUST_PALLET_QUANTITY_ERROR_CODE_PO_NOT_FINALIZE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.testng.Assert.*;
import static org.testng.Assert.assertEquals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.client.dcfin.model.DcFinAdjustRequest;
import com.walmart.move.nim.receiving.core.client.dcfin.model.TransactionsItem;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.RetryEntity;
import com.walmart.move.nim.receiving.core.message.service.RetryService;
import com.walmart.move.nim.receiving.core.model.DCFinPOCloseRequestBody;
import com.walmart.move.nim.receiving.core.service.AsyncPersister;
import com.walmart.move.nim.receiving.core.service.RapidRelayerService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientResponseException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class DCFinRestApiClientTest {

  @Mock private AppConfig appConfig;

  @Mock private RestConnector retryableRestConnector;
  @Mock private RestConnector simpleRestConnector;
  @Mock private AsyncPersister asyncPersister;
  @Mock private RetryService jmsRecoveryService;
  @InjectMocks private DCFinRestApiClient dCFinRestApiClient;
  @Mock RapidRelayerService rapidRelayerService;
  @Mock TenantSpecificConfigReader tenantSpecificConfigReader;

  @BeforeMethod
  public void createDCFinRestApiClient() throws Exception {

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32612);

    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(dCFinRestApiClient, "gson", new Gson());
  }

  private Map<String, Object> getMockHeader() {

    Map<String, Object> mockHeaders = new HashMap<>();
    mockHeaders.put(ReceivingConstants.USER_ID_HEADER_KEY, "WMT-UserId");
    mockHeaders.put(ReceivingConstants.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    mockHeaders.put(ReceivingConstants.TENENT_FACLITYNUM, "32612");
    mockHeaders.put(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    mockHeaders.put(ReceivingConstants.CORRELATION_ID_HEADER_KEY, "a1-b2-c3-d4");
    mockHeaders.put(ReceivingConstants.ORG_UNIT_ID_HEADER, "3");
    return mockHeaders;
  }

  @Test
  public void testPoClose_badRequest() throws Exception {

    File resource = new ClassPathResource("dcFin_closepo_error_response_body.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    doReturn("https://dcfinancials-dev.prod.us.walmart.net").when(appConfig).getGdmBaseUrl();

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.BAD_REQUEST);

    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      Map<String, Object> headers = getMockHeader();
      DCFinPOCloseRequestBody dcFinPOCloseRequestBody = new DCFinPOCloseRequestBody();
      dCFinRestApiClient.poClose(dcFinPOCloseRequestBody, headers);
    } catch (DCFinRestApiClientException e) {
      assertEquals(e.getHttpStatus().value(), 400);
      assertEquals(e.getErrorResponse().getErrorCode(), "GLS-DCFIN-CLOUD-VE-0011");
      assertEquals(
          e.getErrorResponse().getErrorMessage(),
          "message: puchaseClosureTransaction.deliveryNumber may not be empty");
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testPoClose() throws Exception {

    File resource = new ClassPathResource("dcFin_closepo_error_response_body.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    doReturn("https://dcfinancials-dev.prod.us.walmart.net").when(appConfig).getGdmBaseUrl();

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);

    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      Map<String, Object> headers = getMockHeader();
      DCFinPOCloseRequestBody dcFinPOCloseRequestBody = new DCFinPOCloseRequestBody();
      dCFinRestApiClient.poClose(dcFinPOCloseRequestBody, headers);
    } catch (DCFinRestApiClientException e) {
      fail(e.getMessage());
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void test_poCloseAsync() throws Exception {

    File resource = new ClassPathResource("dcFin_closepo_error_response_body.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    doReturn("https://dcfinancials-dev.prod.us.walmart.net").when(appConfig).getGdmBaseUrl();

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);
    doReturn(new RetryEntity())
        .when(jmsRecoveryService)
        .putForRetries(
            anyString(), any(HttpMethod.class), any(HttpHeaders.class), anyString(), any());
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    Map<String, Object> headers = getMockHeader();
    DCFinPOCloseRequestBody dcFinPOCloseRequestBody = new DCFinPOCloseRequestBody();
    dcFinPOCloseRequestBody.setTxnId("txId");
    dCFinRestApiClient.poCloseAsync(dcFinPOCloseRequestBody, headers);

    verify(jmsRecoveryService, atLeastOnce())
        .putForRetries(
            anyString(), any(HttpMethod.class), any(HttpHeaders.class), anyString(), any());
    verify(asyncPersister, atLeastOnce())
        .asyncPost(
            anyString(),
            anyString(),
            any(RetryEntity.class),
            anyString(),
            any(HttpHeaders.class),
            anyString());
    verify(rapidRelayerService, times(0)).produceHttpMessage(anyString(), anyString(), anyMap());
  }

  @Test
  public void test_poCloseAsync_outbox() throws Exception {

    File resource = new ClassPathResource("dcFin_closepo_error_response_body.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    doReturn("https://dcfinancials-dev.prod.us.walmart.net").when(appConfig).getGdmBaseUrl();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);

    Map<String, Object> headers = getMockHeader();
    DCFinPOCloseRequestBody dcFinPOCloseRequestBody = new DCFinPOCloseRequestBody();
    dcFinPOCloseRequestBody.setTxnId("txId");
    dCFinRestApiClient.poCloseAsync(dcFinPOCloseRequestBody, headers);

    verify(rapidRelayerService, times(1)).produceHttpMessage(anyString(), anyString(), anyMap());
    verify(jmsRecoveryService, times(0))
        .putForRetries(
            anyString(), any(HttpMethod.class), any(HttpHeaders.class), anyString(), any());
    verify(asyncPersister, times(0))
        .asyncPost(
            anyString(),
            anyString(),
            any(RetryEntity.class),
            anyString(),
            any(HttpHeaders.class),
            anyString());
  }

  @Test
  public void test_adjustOrVtr_Async() throws Exception {

    File resource = new ClassPathResource("dcFin_adjustOrVtr_request_body.json").getFile();
    String mockRequestJson = new String(Files.readAllBytes(resource.toPath()));

    doReturn("https://dcfinancials-dev.prod.us.walmart.net").when(appConfig).getDcFinBaseUrl();

    doReturn(new RetryEntity())
        .when(jmsRecoveryService)
        .putForRetries(
            anyString(), any(HttpMethod.class), any(HttpHeaders.class), anyString(), any());
    doReturn(new ResponseEntity<String>("", HttpStatus.OK))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    Map<String, Object> headers = getMockHeader();
    Gson gsonDcFinDateFormat =
        new GsonBuilder()
            .serializeNulls()
            .registerTypeAdapter(
                Date.class,
                new GsonUTCDateAdapter(
                    "yyyy-MM-dd'T'HH:mm:ss.SSSZ")) // "dateAdjusted": "Tue Mar 28 11:09:22 UTC
            // 2023",
            .create();
    final DcFinAdjustRequest dcFinAdjustRequest =
        gsonDcFinDateFormat.fromJson(mockRequestJson, DcFinAdjustRequest.class);

    // Execute
    dCFinRestApiClient.adjustOrVtr(dcFinAdjustRequest, headers);

    // verify
    verify(jmsRecoveryService, atLeastOnce())
        .putForRetries(
            anyString(), any(HttpMethod.class), any(HttpHeaders.class), anyString(), any());

    ArgumentCaptor<String> argumentCaptorUrl = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<HttpHeaders> httpHeadersArgumentCaptor =
        ArgumentCaptor.forClass(HttpHeaders.class);

    verify(jmsRecoveryService)
        .putForRetries(
            argumentCaptorUrl.capture(),
            any(HttpMethod.class),
            httpHeadersArgumentCaptor.capture(),
            anyString(),
            any());
    final String argumentCaptorUrlValue = argumentCaptorUrl.getValue();
    assertEquals(
        argumentCaptorUrlValue, "https://dcfinancials-dev.prod.us.walmart.net/v2/adjustment");
    final HttpHeaders httpHeadersArgumentCaptorValue = httpHeadersArgumentCaptor.getValue();
    final String requestOriginatorValue =
        httpHeadersArgumentCaptorValue.getFirst(REQUEST_ORIGINATOR);
    assertEquals(requestOriginatorValue, APP_NAME_VALUE);
  }

  @Test
  public void test_adjustOrVtr_DcFinJsonFormatResponse() throws Exception {
    // setup
    final DcFinAdjustRequest dcFinAdjustRequest = new DcFinAdjustRequest();
    TransactionsItem transactionsItems = new TransactionsItem();
    dcFinAdjustRequest.setTxnId("e99aff14-a52a-4eca-8e13-ef6d47b5834f");
    dcFinAdjustRequest.setTransactions(Arrays.asList(transactionsItems));
    transactionsItems.setAccountingDeptNbr(0);
    transactionsItems.setCostPerSecQty(0);
    transactionsItems.setItemNumber(8070172L);
    transactionsItems.setPromoBuyInd("N");
    transactionsItems.setSecondaryQtyUOM("LB");
    transactionsItems.setDocumentType("SSTKU");
    transactionsItems.setFreightBillQty(0);
    transactionsItems.setPrimaryQtyUOM("EA");
    transactionsItems.setVendorPackQty(12);
    transactionsItems.setDateAdjusted(new Date());
    transactionsItems.setTargetItemNumber(0);
    transactionsItems.setWeightFormatType("F");
    transactionsItems.setQuantityToTransfer(0);
    transactionsItems.setBaseRetailAmount(0);
    transactionsItems.setReasonCodeDesc("Void To Reinstate");
    transactionsItems.setPrimaryQty(-120);
    transactionsItems.setDocumentNum("0744333381");
    transactionsItems.setQtyReceivedFromUpstream(0);
    transactionsItems.setReasonCode("28");
    transactionsItems.setContainerId("087-AAC");
    transactionsItems.setWarehousePackQty(12);
    transactionsItems.setBaseDivCode("WM");
    transactionsItems.setInboundChannelMethod("Staplestock");
    transactionsItems.setSecondaryQty(6.43F);
    transactionsItems.setCostPerPrimaryQty(0);
    transactionsItems.setDeliveryNum("27725100");
    transactionsItems.setDocumentLineNo(1);
    transactionsItems.setFinancialReportGrpCode("US");
    // testing nulls
    transactionsItems.setDistributions(null);
    transactionsItems.setReferenceDocNum(null);
    transactionsItems.setCurrencyCode(null);
    transactionsItems.setInvTransferDestNbr(null);

    doReturn("https://dcfinancials-dev.prod.us.walmart.net").when(appConfig).getDcFinBaseUrl();
    doReturn(new RetryEntity())
        .when(jmsRecoveryService)
        .putForRetries(
            anyString(), any(HttpMethod.class), any(HttpHeaders.class), anyString(), any());
    doReturn(new ResponseEntity<String>("", HttpStatus.OK))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    Map<String, Object> headers = getMockHeader();

    // Execute
    dCFinRestApiClient.adjustOrVtr(dcFinAdjustRequest, headers);

    // verify
    verify(jmsRecoveryService, atLeastOnce())
        .putForRetries(
            anyString(), any(HttpMethod.class), any(HttpHeaders.class), anyString(), any());

    ArgumentCaptor<String> argumentCaptorUrl = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<HttpHeaders> httpHeadersArgumentCaptor =
        ArgumentCaptor.forClass(HttpHeaders.class);

    ArgumentCaptor<String> bodyArgumentCaptor = ArgumentCaptor.forClass(String.class);

    verify(jmsRecoveryService)
        .putForRetries(
            argumentCaptorUrl.capture(),
            any(HttpMethod.class),
            httpHeadersArgumentCaptor.capture(),
            bodyArgumentCaptor.capture(),
            any());
    // verify
    // url
    final String argumentCaptorUrlValue = argumentCaptorUrl.getValue();
    assertEquals(
        argumentCaptorUrlValue, "https://dcfinancials-dev.prod.us.walmart.net/v2/adjustment");

    // Headers
    final HttpHeaders httpHeadersArgumentCaptorValue = httpHeadersArgumentCaptor.getValue();
    final String requestOriginatorValue =
        httpHeadersArgumentCaptorValue.getFirst(REQUEST_ORIGINATOR);
    assertEquals(requestOriginatorValue, APP_NAME_VALUE);

    // request body payload
    final String actualJsonRequest = bodyArgumentCaptor.getValue();
    String prefix =
        "{\"transactions\":[{\"accountingDeptNbr\":0,\"costPerSecQty\":0,\"itemNumber\":8070172,\"promoBuyInd\":\"N\",\"secondaryQtyUOM\":\"LB\",\"documentType\":\"SSTKU\",\"freightBillQty\":0,\"primaryQtyUOM\":\"EA\",\"vendorPackQty\":12,\"dateAdjusted\":\"";
    String midPart = "2023-03-30T17:16:08.827+0000";
    String suffix =
        "\",\"targetItemNumber\":0,\"weightFormatType\":\"F\",\"quantityToTransfer\":0,\"baseRetailAmount\":0,\"reasonCodeDesc\":\"Void To Reinstate\",\"primaryQty\":-120,\"documentNum\":\"0744333381\",\"qtyReceivedFromUpstream\":0,\"reasonCode\":\"28\",\"containerId\":\"087-AAC\",\"warehousePackQty\":12,\"baseDivCode\":\"WM\",\"inboundChannelMethod\":\"Staplestock\",\"secondaryQty\":6.43,\"costPerPrimaryQty\":0,\"deliveryNum\":\"27725100\",\"documentLineNo\":1,\"financialReportGrpCode\":\"US\"}],\"txnId\":\"e99aff14-a52a-4eca-8e13-ef6d47b5834f\"}";

    assertTrue(actualJsonRequest.contains(prefix));
    assertTrue(actualJsonRequest.contains(suffix));
    // assertEquals(actualJsonRequest, prefix + midPart + suffix);
  }

  @Test
  public void testAdjustOrVtrWithRapid() throws ReceivingException {
    final DcFinAdjustRequest dcFinAdjustRequest = new DcFinAdjustRequest();
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(anyString(), eq(OUTBOX_PATTERN_ENABLED), anyBoolean());
    dCFinRestApiClient.adjustOrVtr(dcFinAdjustRequest, getMockHeader());
    verify(rapidRelayerService, times(1)).produceHttpMessage(anyString(), anyString(), anyMap());
  }

  @Test
  public void testAdjustOrVtrWithRapid_exception() {
    final DcFinAdjustRequest dcFinAdjustRequest = new DcFinAdjustRequest();
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(anyString(), eq(OUTBOX_PATTERN_ENABLED), anyBoolean());
    try {
      doThrow(new ReceivingException("Test Exception", INTERNAL_SERVER_ERROR))
          .when(rapidRelayerService)
          .produceHttpMessage(anyString(), anyString(), anyMap());
      dCFinRestApiClient.adjustOrVtr(dcFinAdjustRequest, getMockHeader());
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), INTERNAL_SERVER_ERROR);
    }
  }

  @Test
  public void testBuildHttpHeaders() throws Exception {
    HttpHeaders testHeaders = DCFinRestApiClient.buildHttpHeaders(getMockHeader(), "dummyKey");

    assertEquals(testHeaders.getFirst(REQUEST_ORIGINATOR), APP_NAME_VALUE);
    assertEquals(testHeaders.getFirst(ReceivingConstants.DCFIN_WMT_API_KEY), "dummyKey");
    assertEquals(testHeaders.getFirst(ORG_UNIT_ID_HEADER), "3");
  }

  @Test
  public void testIsPoFinalizedInDcFin() throws Exception {
    File resource = new ClassPathResource("dcFin_poclose_status_mock_response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    doReturn("https://dcfinancials-stg-int.prod.us.walmart.net").when(appConfig).getDcFinBaseUrl();
    // execute
    boolean result = dCFinRestApiClient.isPoFinalizedInDcFin("124", "567");
    // verify
    assertTrue(result);
    verify(simpleRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testIsPoFinalizedInDcFin_BADRequest() throws Exception {
    File resource =
        new ClassPathResource("dcFin_poclose_status_mock_error_response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.BAD_REQUEST);
    doReturn(mockResponseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    doReturn("https://dcfinancials-stg-int.prod.us.walmart.net").when(appConfig).getDcFinBaseUrl();
    // execute
    try {
      dCFinRestApiClient.isPoFinalizedInDcFin("124", "567");
    } catch (ReceivingException re) {
      assertEquals(re.getMessage(), "Invalid request to check PO=567 closure in DcFin.");
    }
  }

  @Test
  public void testIsPoFinalizedInDcFin_ServerError() throws Exception {
    File resource =
        new ClassPathResource("dcFin_poclose_status_mock_error_response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    doReturn(mockResponseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    doReturn("https://dcfinancials-stg-int.prod.us.walmart.net").when(appConfig).getDcFinBaseUrl();
    // execute
    try {
      dCFinRestApiClient.isPoFinalizedInDcFin("124", "567");
    } catch (ReceivingException re) {
      assertEquals(re.getMessage(), "Error while checking PO=567 for closure status in DcFin.");
      assertEquals(
          re.getErrorResponse().getErrorCode(), ADJUST_PALLET_QUANTITY_ERROR_CODE_PO_NOT_FINALIZE);
    }
  }

  @Test
  public void testIsPoFinalizedInDcFin_RestClientException() throws Exception {
    File resource =
        new ClassPathResource("dcFin_poclose_status_mock_error_response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.BAD_REQUEST);
    doReturn(mockResponseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    RestClientResponseException restClientResponseException =
        new RestClientResponseException(
            "Some error.",
            HttpStatus.BAD_REQUEST.value(),
            "",
            null,
            "".getBytes(),
            Charset.forName("UTF-8"));
    doThrow(restClientResponseException)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    doReturn("https://dcfinancials-stg-int.prod.us.walmart.net").when(appConfig).getDcFinBaseUrl();
    // execute
    try {
      dCFinRestApiClient.isPoFinalizedInDcFin("124", "567");
    } catch (ReceivingException re) {
      assertEquals(
          re.getMessage(),
          "Unable to verify DCFin close status for po=567, Please contact your supervisor or support.");
      assertEquals(
          re.getErrorResponse().getErrorCode(), ADJUST_PALLET_QUANTITY_ERROR_CODE_PO_NOT_FINALIZE);
    }
  }

  @Test
  public void testIsPoFinalizedInDcFin_NotClosedException() throws Exception {
    File resource =
        new ClassPathResource("dcFin_poclose_status_mock_started_response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    doReturn("https://dcfinancials-stg-int.prod.us.walmart.net").when(appConfig).getDcFinBaseUrl();
    // execute
    try {
      dCFinRestApiClient.isPoFinalizedInDcFin("124", "567");
    } catch (ReceivingException re) {
      assertEquals(re.getMessage(), "Please try once DcFin confirms the PO 567 closure.");
      assertEquals(
          re.getErrorResponse().getErrorCode(), ADJUST_PALLET_QUANTITY_ERROR_CODE_PO_NOT_FINALIZE);
    }
  }
}
