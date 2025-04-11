package com.walmart.move.nim.receiving.core.client.gls;

import static com.walmart.move.nim.receiving.core.client.gls.GlsRestApiClient.*;
import static com.walmart.move.nim.receiving.data.MockHttpHeaders.getHeaders;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.RECEIVING_CORRECTION;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.VNPK;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.VTR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;
import static org.testng.Assert.assertEquals;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.client.gls.model.GLSDeliveryDetailsResponse;
import com.walmart.move.nim.receiving.core.client.gls.model.GLSReceiveRequest;
import com.walmart.move.nim.receiving.core.client.gls.model.GLSReceiveResponse;
import com.walmart.move.nim.receiving.core.client.gls.model.GlsAdjustPayload;
import com.walmart.move.nim.receiving.core.client.gls.model.GlsApiInfo;
import com.walmart.move.nim.receiving.core.client.gls.model.GlsLpnRequest;
import com.walmart.move.nim.receiving.core.client.gls.model.GlsLpnResponse;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.ErrorResponse;
import com.walmart.move.nim.receiving.core.service.AsyncPersister;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class GlsRestApiClientTest extends ReceivingTestBase {

  public static final String TRACKING_ID_1 = "TAG-1";
  public static final String USER_ID = "sysadmin";
  @Mock private AsyncPersister asyncPersister;

  @Mock private AppConfig appConfig;

  @InjectMocks private GlsRestApiClient glsRestApiClient = new GlsRestApiClient();

  @Mock private RestConnector simpleRestConnector;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @BeforeClass
  public void setup() {
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32997);
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(glsRestApiClient, "gson", new Gson());
  }

  @AfterMethod
  public void tearDown() {
    reset(simpleRestConnector, asyncPersister, appConfig, simpleRestConnector);
  }

  @Test
  public void test_gls_adjustOrCancel_Invalid_request() throws IOException {
    String mockResponse = readFileFromCp("gls_adjustOrCancel_FAIL.json");
    doReturn(GLS_BASE_URL_DEFAULT)
        .when(tenantSpecificConfigReader)
        .getCcmValue(anyInt(), anyString(), anyString());
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);

    when(simpleRestConnector.post(
            anyString(), anyString(), any(HttpHeaders.class), same(String.class)))
        .thenReturn(mockResponseEntity);

    try {
      glsRestApiClient.adjustOrCancel(null, getHeaders());
      fail();
    } catch (ReceivingException e) {
      matchErrorCodeAndMessage(e, GLS_ADJUST_BAD_REQUEST_CODE, GLS_ADJUST_BAD_REQUEST_MSG);
    }
  }

  @Test
  public void test_gls_adjustOrCancel_Invalid_request_missingAllExceptTrackingId()
      throws IOException {
    String mockResponse = readFileFromCp("gls_adjustOrCancel_FAIL.json");

    doReturn(GLS_BASE_URL_DEFAULT)
        .when(tenantSpecificConfigReader)
        .getCcmValue(anyInt(), anyString(), anyString());

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);

    when(simpleRestConnector.post(
            anyString(), anyString(), any(HttpHeaders.class), same(String.class)))
        .thenReturn(mockResponseEntity);

    GlsAdjustPayload glsAdjustRequest = new GlsAdjustPayload();
    glsAdjustRequest.setPalletTagId(TRACKING_ID_1);

    try {
      glsRestApiClient.adjustOrCancel(glsAdjustRequest, getHeaders());
      fail();
    } catch (ReceivingException e) {
      matchErrorCodeAndMessage(e, GLS_ADJUST_BAD_REQUEST_CODE, GLS_ADJUST_BAD_REQUEST_MSG);
    }
  }

  @Test
  public void test_gls_adjustOrCancel_valid_request_successResponse()
      throws IOException, ReceivingException {
    String mockResponse = readFileFromCp("gls_adjustOrCancel_SUCESS.json");

    doReturn(GLS_BASE_URL_DEFAULT)
        .when(tenantSpecificConfigReader)
        .getCcmValue(anyInt(), anyString(), anyString());

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);

    when(simpleRestConnector.post(
            anyString(), anyString(), any(HttpHeaders.class), same(String.class)))
        .thenReturn(mockResponseEntity);

    GlsAdjustPayload glsAdjustRequest = new GlsAdjustPayload();
    glsAdjustRequest.setPalletTagId(TRACKING_ID_1);
    glsAdjustRequest.setNewQty(0);
    glsAdjustRequest.setOriginalQty(20);
    glsAdjustRequest.setReasonCode("RCV-CORRECTION");
    glsAdjustRequest.setQtyUOM("ZA");
    // glsAdjustRequest.setOperationTimestamp("2022-06-08T00:00:00.000Z");
    final GlsAdjustPayload glsAdjustResponse =
        glsRestApiClient.adjustOrCancel(glsAdjustRequest, getHeaders());
    assertNotNull(glsAdjustResponse);
    assertEquals(glsAdjustResponse.getPalletTagId(), TRACKING_ID_1);
    assertEquals(glsAdjustResponse.getOriginalQty().intValue(), 20);
    assertEquals(glsAdjustResponse.getNewQty().intValue(), 0);
    assertEquals(glsAdjustResponse.getQtyUOM(), "ZA");
    assertEquals(glsAdjustResponse.getReasonCode(), "RCV-CORRECTION");
  }

  @Test
  public void test_gls_adjustOrCancel_valid_request_FAIL_Response() throws IOException {
    String mockResponse = readFileFromCp("gls_adjustOrCancel_FAIL.json");

    doReturn(GLS_BASE_URL_DEFAULT)
        .when(tenantSpecificConfigReader)
        .getCcmValue(anyInt(), anyString(), anyString());

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);

    when(simpleRestConnector.post(
            anyString(), anyString(), any(HttpHeaders.class), same(String.class)))
        .thenReturn(mockResponseEntity);

    GlsAdjustPayload glsAdjustRequest = new GlsAdjustPayload();
    glsAdjustRequest.setPalletTagId(TRACKING_ID_1);
    glsAdjustRequest.setNewQty(0);
    glsAdjustRequest.setOriginalQty(20);
    glsAdjustRequest.setReasonCode("RCV-CORRECTION");
    glsAdjustRequest.setQtyUOM("ZA");
    // glsAdjustRequest.setOperationTimestamp("2022-06-08T00:00:00.000Z");
    try {
      glsRestApiClient.adjustOrCancel(glsAdjustRequest, getHeaders());
      fail();
    } catch (ReceivingException e) {
      matchErrorCodeAndMessage(
          e,
          "GLS_RCV_1024",
          "Internal Error, Please reach out to Support team. Error: Adjustment failed for pallet 97366027");
    }
  }

  @Test
  public void testGLSReceive() throws IOException, ReceivingException {

    String mockResponse = readFileFromCp("gls_receiveResponse.json");

    doReturn(GLS_BASE_URL_DEFAULT)
        .when(tenantSpecificConfigReader)
        .getCcmValue(anyInt(), anyString(), anyString());

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);

    doReturn(mockResponseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    GLSReceiveResponse glsReceiveResponse =
        glsRestApiClient.receive(new GLSReceiveRequest(), getHeaders());

    assertEquals(glsReceiveResponse.getWeightUOM(), "LB");

    verify(simpleRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testGLSReceive_Exception_NoResponse() {

    doReturn(GLS_BASE_URL_DEFAULT)
        .when(tenantSpecificConfigReader)
        .getCcmValue(anyInt(), anyString(), anyString());
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(HttpStatus.INTERNAL_SERVER_ERROR);

    doReturn(mockResponseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      glsRestApiClient.receive(new GLSReceiveRequest(), getHeaders());
      fail();
    } catch (ReceivingException e) {
      assertTrue(e.getHttpStatus() == HttpStatus.INTERNAL_SERVER_ERROR);
    }

    verify(simpleRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testGLSReceive_ClientException() {

    doReturn(GLS_BASE_URL_DEFAULT)
        .when(tenantSpecificConfigReader)
        .getCcmValue(anyInt(), anyString(), anyString());
    RestClientResponseException restClientResponseException =
        new RestClientResponseException(
            "mockErrorResponse",
            HttpStatus.CONFLICT.value(),
            "",
            null,
            "mockErrorResponse".getBytes(),
            null);

    doThrow(restClientResponseException)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      glsRestApiClient.receive(new GLSReceiveRequest(), getHeaders());
      fail();
    } catch (ReceivingException e) {
      assertTrue("glsReceiveFailed".equalsIgnoreCase(e.getErrorResponse().getErrorCode()));
    }

    verify(simpleRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testGLSReceive_ResourceAccessException() {

    doReturn(GLS_BASE_URL_DEFAULT)
        .when(tenantSpecificConfigReader)
        .getCcmValue(anyInt(), anyString(), anyString());
    ResourceAccessException resourceAccessException =
        new ResourceAccessException("mockErrorResponse");

    doThrow(resourceAccessException)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      glsRestApiClient.receive(new GLSReceiveRequest(), getHeaders());
      fail();
    } catch (ReceivingException e) {
      assertTrue(e.getHttpStatus() == HttpStatus.INTERNAL_SERVER_ERROR);
    }

    verify(simpleRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testGLSAdjustOrCancel_ResourceAccessException() {

    doReturn(GLS_BASE_URL_DEFAULT)
        .when(tenantSpecificConfigReader)
        .getCcmValue(anyInt(), anyString(), anyString());
    ResourceAccessException resourceAccessException =
        new ResourceAccessException("mockErrorResponse");

    doThrow(resourceAccessException)
        .when(simpleRestConnector)
        .post(anyString(), anyString(), any(HttpHeaders.class), same(String.class));

    try {
      GlsAdjustPayload glsAdjustRequest = new GlsAdjustPayload();
      glsAdjustRequest.setPalletTagId(TRACKING_ID_1);
      glsAdjustRequest.setNewQty(0);
      glsAdjustRequest.setOriginalQty(20);
      glsAdjustRequest.setReasonCode("RCV-CORRECTION");
      glsAdjustRequest.setQtyUOM("ZA");
      glsRestApiClient.adjustOrCancel(glsAdjustRequest, getHeaders());
      fail();
    } catch (ReceivingException e) {
      assertTrue(e.getHttpStatus() == HttpStatus.INTERNAL_SERVER_ERROR);
    }

    verify(simpleRestConnector, atLeastOnce())
        .post(anyString(), anyString(), any(HttpHeaders.class), same(String.class));
  }

  @Test
  public void testGLSAdjustOrCancel_ClientException() {

    doReturn(GLS_BASE_URL_DEFAULT)
        .when(tenantSpecificConfigReader)
        .getCcmValue(anyInt(), anyString(), anyString());
    RestClientResponseException restClientResponseException =
        new RestClientResponseException(
            "mockErrorResponse",
            HttpStatus.CONFLICT.value(),
            "",
            null,
            "mockErrorResponse".getBytes(),
            null);

    doThrow(restClientResponseException)
        .when(simpleRestConnector)
        .post(anyString(), anyString(), any(HttpHeaders.class), same(String.class));

    try {
      GlsAdjustPayload glsAdjustRequest = new GlsAdjustPayload();
      glsAdjustRequest.setPalletTagId(TRACKING_ID_1);
      glsAdjustRequest.setNewQty(0);
      glsAdjustRequest.setOriginalQty(20);
      glsAdjustRequest.setReasonCode("RCV-CORRECTION");
      glsAdjustRequest.setQtyUOM("ZA");
      glsRestApiClient.adjustOrCancel(glsAdjustRequest, getHeaders());
      fail();
    } catch (ReceivingException e) {
      assertTrue(
          GLS_ADJUST_BAD_RESPONSE_CODE.equalsIgnoreCase(e.getErrorResponse().getErrorCode()));
    }

    verify(simpleRestConnector, atLeastOnce())
        .post(anyString(), anyString(), any(HttpHeaders.class), same(String.class));
  }

  @Test
  public void test_gls_receive_valid_request_FAIL_Response() throws IOException {
    String mockResponse = readFileFromCp("gls_receiveResponse_FAIL.json");

    doReturn(GLS_BASE_URL_DEFAULT)
        .when(tenantSpecificConfigReader)
        .getCcmValue(anyInt(), anyString(), anyString());

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);

    doReturn(mockResponseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      glsRestApiClient.receive(new GLSReceiveRequest(), getHeaders());
      fail();
    } catch (ReceivingException e) {
      matchErrorCodeAndMessage(
          e,
          "GLS_RCV_1024",
          "Internal Error, Please reach out to Support team. Error: Receive failed for pallet 97366027");
    }
  }

  @Test
  public void testCreateGlsAdjustPayload_null_input() {
    String trackingId = "LPN123ABC";

    try {
      glsRestApiClient.createGlsAdjustPayload(null, null, null, null, USER_ID);
      fail();
    } catch (ReceivingException e) {
      matchErrorCodeAndMessage(e, GLS_ADJUST_BAD_REQUEST_CODE, GLS_ADJUST_BAD_REQUEST_MSG);
    }
    try {
      glsRestApiClient.createGlsAdjustPayload(RECEIVING_CORRECTION, null, null, null, USER_ID);
      fail();
    } catch (ReceivingException e) {
      matchErrorCodeAndMessage(e, GLS_ADJUST_BAD_REQUEST_CODE, GLS_ADJUST_BAD_REQUEST_MSG);
    }
    try {
      glsRestApiClient.createGlsAdjustPayload(
          RECEIVING_CORRECTION, trackingId, null, null, USER_ID);
      fail();
    } catch (ReceivingException e) {
      matchErrorCodeAndMessage(e, GLS_ADJUST_BAD_REQUEST_CODE, GLS_ADJUST_BAD_REQUEST_MSG);
    }
    try {
      glsRestApiClient.createGlsAdjustPayload(RECEIVING_CORRECTION, trackingId, 0, null, USER_ID);
      fail();
    } catch (ReceivingException e) {
      matchErrorCodeAndMessage(e, GLS_ADJUST_BAD_REQUEST_CODE, GLS_ADJUST_BAD_REQUEST_MSG);
    }
    try {
      glsRestApiClient.createGlsAdjustPayload(VTR, trackingId, 0, null, USER_ID);
      fail();
    } catch (ReceivingException e) {
      matchErrorCodeAndMessage(e, GLS_ADJUST_BAD_REQUEST_CODE, GLS_ADJUST_BAD_REQUEST_MSG);
    }
  }

  @Test
  public void testCreateGlsAdjustPayload_validInput() {
    String trackingId = "LPN123ABC";
    try {
      final GlsAdjustPayload req =
          glsRestApiClient.createGlsAdjustPayload(RECEIVING_CORRECTION, trackingId, 0, 10, USER_ID);
      assertEquals(req.getPalletTagId(), trackingId);
      assertEquals(req.getNewQty().intValue(), 0);
      assertEquals(req.getOriginalQty().intValue(), 10);
      assertEquals(req.getQtyUOM(), VNPK);
      assertEquals(req.getReasonCode(), RECEIVING_CORRECTION);
      assertNotNull(req.getOperationTimestamp());
      assertEquals(req.getCreateUser(), "sysadmin");

    } catch (ReceivingException e) {
      fail();
    }
  }

  @Test
  public void testCreateGlsAdjustPayloadVTR_validInput() {
    String trackingId = "LPN123ABC";
    try {
      final GlsAdjustPayload req =
          glsRestApiClient.createGlsAdjustPayload(VTR, trackingId, 0, 10, USER_ID);
      assertEquals(req.getPalletTagId(), trackingId);
      assertEquals(req.getNewQty().intValue(), 0);
      assertEquals(req.getOriginalQty().intValue(), 10);
      assertEquals(req.getQtyUOM(), VNPK);
      assertEquals(req.getReasonCode(), VTR);
      assertNotNull(req.getOperationTimestamp());
      assertEquals(req.getCreateUser(), "sysadmin");

    } catch (ReceivingException e) {
      fail();
    }
  }

  @Test
  public void testGLSDeliveryDetails() throws IOException, ReceivingException {

    File resource = new ClassPathResource("gls_deliveryDetailsResponse.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    doReturn(GLS_BASE_URL_DEFAULT)
        .when(tenantSpecificConfigReader)
        .getCcmValue(anyInt(), anyString(), anyString());

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);

    doReturn(mockResponseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    GLSDeliveryDetailsResponse response =
        glsRestApiClient.deliveryDetails("999999999", getHeaders());

    assertEquals(response.getDeliveryNumber(), "999999999");

    verify(simpleRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testGLSDeliveryDetails_Exception_NoResponse() {

    doReturn(GLS_BASE_URL_DEFAULT)
        .when(tenantSpecificConfigReader)
        .getCcmValue(anyInt(), anyString(), anyString());
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(HttpStatus.INTERNAL_SERVER_ERROR);

    doReturn(mockResponseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      glsRestApiClient.deliveryDetails("999999999", getHeaders());
      fail();
    } catch (ReceivingException e) {
      assertTrue(e.getHttpStatus() == HttpStatus.INTERNAL_SERVER_ERROR);
    }

    verify(simpleRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testGLSDeliveryDetails_ClientException() {

    doReturn(GLS_BASE_URL_DEFAULT)
        .when(tenantSpecificConfigReader)
        .getCcmValue(anyInt(), anyString(), anyString());
    RestClientResponseException restClientResponseException =
        new RestClientResponseException(
            "mockErrorResponse",
            HttpStatus.CONFLICT.value(),
            "",
            null,
            "mockErrorResponse".getBytes(),
            null);

    doThrow(restClientResponseException)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      glsRestApiClient.deliveryDetails("999999999", getHeaders());
      fail();
    } catch (ReceivingException e) {
      assertTrue("glsDeliveryDetailsFailed".equalsIgnoreCase(e.getErrorResponse().getErrorCode()));
    }

    verify(simpleRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testGLSDeliveryDetails_ResourceAccessException() {

    doReturn(GLS_BASE_URL_DEFAULT)
        .when(tenantSpecificConfigReader)
        .getCcmValue(anyInt(), anyString(), anyString());
    ResourceAccessException resourceAccessException =
        new ResourceAccessException("mockErrorResponse");

    doThrow(resourceAccessException)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      glsRestApiClient.deliveryDetails("999999999", getHeaders());
      fail();
    } catch (ReceivingException e) {
      assertTrue(e.getHttpStatus() == HttpStatus.INTERNAL_SERVER_ERROR);
    }

    verify(simpleRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testGls_createGlsLpn() throws IOException, ReceivingException {
    GlsLpnRequest glsLpnRequest = new GlsLpnRequest();
    final long deliveryNumber = 12345678L;
    glsLpnRequest.setDeliveryNumber(deliveryNumber);
    final String poNumber = "2822820001";
    glsLpnRequest.setPoNumber(poNumber);
    final int poLineNumber = 1;
    glsLpnRequest.setPoLineNumber(poLineNumber);
    final long itemNumber = 587040467L;
    glsLpnRequest.setItemNumber(itemNumber);

    File resource = new ClassPathResource("gls_newLpnResponse.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    doReturn(GLS_BASE_URL_DEFAULT)
        .when(tenantSpecificConfigReader)
        .getCcmValue(anyInt(), anyString(), anyString());

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);

    ArgumentCaptor<HttpEntity> argumentCaptor = ArgumentCaptor.forClass(HttpEntity.class);
    doReturn(mockResponseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), argumentCaptor.capture(), any(Class.class));

    final GlsLpnResponse response = glsRestApiClient.createGlsLpn(glsLpnRequest, getHeaders());

    // assert the full request payload
    final HttpEntity captorValue = argumentCaptor.getValue();
    final String captorValue_actual_requestBody = captorValue.getBody().toString();
    final String expectedRequestJsonFormat =
        "{\"deliveryNumber\":"
            + deliveryNumber
            + ",\"poNumber\":\""
            + poNumber
            + "\",\"poLineNumber\":"
            + poLineNumber
            + ",\"itemNumber\":"
            + itemNumber
            + "}";
    assertEquals(captorValue_actual_requestBody, expectedRequestJsonFormat);

    Gson gson = new Gson();
    final GlsLpnRequest captor_request =
        gson.fromJson(expectedRequestJsonFormat, GlsLpnRequest.class);
    assertEquals(Optional.of(deliveryNumber).get(), captor_request.getDeliveryNumber());
    assertEquals(Optional.of(poNumber).get(), captor_request.getPoNumber());
    assertEquals(Optional.of(poLineNumber).get(), captor_request.getPoLineNumber());
    assertEquals(Optional.of(itemNumber).get(), captor_request.getItemNumber());

    // Assert the response
    assertEquals(response.getPalletTagId(), "TAG-1");
    assertEquals(response.getTimestamp(), "2022-06-08T00:00:00.000Z");

    verify(simpleRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  private void matchErrorCodeAndMessage(
      ReceivingException e, String glsAdjustBadRequestCode, String glsAdjustBadRequestMsg) {
    assertEquals(e.getErrorResponse().getErrorCode(), glsAdjustBadRequestCode);
    assertEquals(e.getErrorResponse().getErrorMessage(), glsAdjustBadRequestMsg);
  }

  @Test
  public void testValidateResponseAndGetGlsApiInfo_null_ResponseEntity() {
    try {
      ResponseEntity<String> response = null;
      glsRestApiClient.validateResponseAndGetGlsApiInfo(response);
      fail();
    } catch (ReceivingException e) {

      assertTrue(e.getHttpStatus() == HttpStatus.INTERNAL_SERVER_ERROR);
      final ErrorResponse err = e.getErrorResponse();
      assertNotNull(err);
      assertEquals(err.getErrorCode(), GLS_RESPONSE_DEFAULT_ERROR_CODE);
      assertEquals(err.getErrorMessage(), GLS_RESPONSE_EMPTY_ERROR_MESSAGE);
    }
  }

  @Test
  public void testValidateResponseAndGetGlsApiInfo_null_body() {
    try {
      ResponseEntity<String> response = new ResponseEntity<>(null, HttpStatus.OK);
      glsRestApiClient.validateResponseAndGetGlsApiInfo(response);
      fail();
    } catch (ReceivingException e) {

      assertTrue(e.getHttpStatus() == HttpStatus.INTERNAL_SERVER_ERROR);
      final ErrorResponse err = e.getErrorResponse();
      assertNotNull(err);
      assertEquals(err.getErrorCode(), GLS_RESPONSE_DEFAULT_ERROR_CODE);
      assertEquals(err.getErrorMessage(), GLS_RESPONSE_EMPTY_ERROR_MESSAGE);
    }
  }

  @Test
  public void testValidateResponseAndGetGlsApiInfo_http_500() {
    try {
      String gslBody = readFileFromCp("gls_Response_invalid_http500.json");
      ResponseEntity<String> response =
          new ResponseEntity<>(gslBody, HttpStatus.INTERNAL_SERVER_ERROR);
      glsRestApiClient.validateResponseAndGetGlsApiInfo(response);
      fail();
    } catch (ReceivingException e) {
      assertTrue(e.getHttpStatus() == HttpStatus.INTERNAL_SERVER_ERROR);
      final ErrorResponse err = e.getErrorResponse();
      assertNotNull(err);
      assertEquals(err.getErrorCode(), "500");
      assertEquals(err.getErrorMessage(), "System Encountered an Issue. Please contact support.");
    }
  }

  @Test
  public void testValidateResponseAndGetGlsApiInfo_receive_success_ok() throws ReceivingException {
    String gslBody = readFileFromCp("gls_receiveResponse.json");
    ResponseEntity<String> response = new ResponseEntity<>(gslBody, HttpStatus.OK);
    final GlsApiInfo glsApiInfo = glsRestApiClient.validateResponseAndGetGlsApiInfo(response);
    assertNotNull(glsApiInfo.getPayload());
  }
}
