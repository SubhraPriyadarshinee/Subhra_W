package com.walmart.move.nim.receiving.rdc.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.rdc.model.MirageExceptionRequest;
import com.walmart.move.nim.receiving.rdc.model.ReceivedQuantityByLines;
import com.walmart.move.nim.receiving.rdc.model.VoidLPNRequest;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class MirageRestApiClientTest {

  @Mock private RestConnector retryableRestConnector;
  @Mock private AppConfig appConfig;

  @InjectMocks private MirageRestApiClient mirageRestApiClient;

  private Gson gson = new Gson();

  private String receivingMirageBaseUrl =
      "http://receiving-mirage.us-32818.lb-node.cluster3.cloud.s32679.us.wal-mart.com/labels/received";
  private String receivingMirageAclExceptionUrl =
      "http://receiving-mirage.us-32679.lb-node.cluster3.cloud.s32679.us.wal-mart.com/aclexception/lpn";

  @BeforeMethod
  public void createMirageRestApiClientTest() throws Exception {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32818);
    TenantContext.setFacilityCountryCode("us");
    ReflectionTestUtils.setField(mirageRestApiClient, "gson", gson);
  }

  @AfterMethod
  public void resetMocks() {
    reset(appConfig, retryableRestConnector);
  }

  @Test
  public void testVoidLPN_2xxSuccess() {
    HttpHeaders requestHeaders = new HttpHeaders();
    requestHeaders.add(ReceivingConstants.ACCEPT, ReceivingConstants.APPLICATION_JSON);
    requestHeaders.add(ReceivingConstants.CONTENT_TYPE, ReceivingConstants.APPLICATION_JSON);
    requestHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, "32818");
    TenantContext.setFacilityNum(32818);
    ArgumentCaptor<String> receivingMirageUrlCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<HttpEntity> LPNRequestBodyCaptor = ArgumentCaptor.forClass(HttpEntity.class);
    when(appConfig.getReceivingMirageBaseUrl()).thenReturn(getMockReceivingBaseUrl());
    ResponseEntity<String> mockResponseEntity = new ResponseEntity<>("{}", HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));

    mirageRestApiClient.voidLPN(getMockVoidLPNRequest(), requestHeaders);

    verify(retryableRestConnector, times(1))
        .exchange(
            receivingMirageUrlCaptor.capture(),
            eq(HttpMethod.POST),
            LPNRequestBodyCaptor.capture(),
            eq(String.class));
    HttpEntity<String> requestEntity = LPNRequestBodyCaptor.getValue();
    VoidLPNRequest voidLPNRequestBody =
        gson.fromJson(requestEntity.getBody(), VoidLPNRequest.class);
    HttpHeaders LPNRequestHeaders = requestEntity.getHeaders();

    assertEquals(receivingMirageUrlCaptor.getValue(), receivingMirageBaseUrl);
    assertNotNull(voidLPNRequestBody);
    assertEquals(voidLPNRequestBody.getDeliveryNumber(), "123456");
    assertSame(voidLPNRequestBody.getReceivedQuantityByLines().get(0).getReceivedQty(), 25);
    assertEquals(
        voidLPNRequestBody.getReceivedQuantityByLines().get(0).getPurchaseReferenceNumber(),
        "4223042727");
    assertSame(
        voidLPNRequestBody.getReceivedQuantityByLines().get(0).getPurchaseReferenceLineNumber(), 2);
    assertEquals(
        LPNRequestHeaders.getFirst(ReceivingConstants.ACCEPT), ReceivingConstants.APPLICATION_JSON);
    assertEquals(
        LPNRequestHeaders.getFirst(ReceivingConstants.CONTENT_TYPE),
        ReceivingConstants.APPLICATION_JSON);
    assertEquals(mockResponseEntity.getStatusCode(), HttpStatus.OK);
    assertTrue(mockResponseEntity.getStatusCode().is2xxSuccessful());
  }

  @Test(expectedExceptions = Exception.class)
  public void testVoidLPN_NotSuccess() {
    HttpHeaders requestHeaders = new HttpHeaders();
    requestHeaders.add(ReceivingConstants.ACCEPT, ReceivingConstants.APPLICATION_JSON);
    requestHeaders.add(ReceivingConstants.CONTENT_TYPE, ReceivingConstants.APPLICATION_JSON);
    TenantContext.setFacilityNum(32818);
    ArgumentCaptor<String> receivingMirageUrlCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<HttpEntity> LPNRequestBodyCaptor = ArgumentCaptor.forClass(HttpEntity.class);
    when(appConfig.getReceivingMirageBaseUrl()).thenReturn(getMockReceivingBaseUrl());
    doThrow(mockException("Not Found"))
        .when(retryableRestConnector)
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));

    mirageRestApiClient.voidLPN(getMockVoidLPNRequest(), requestHeaders);

    verify(retryableRestConnector, times(1))
        .exchange(
            receivingMirageUrlCaptor.capture(),
            eq(HttpMethod.POST),
            LPNRequestBodyCaptor.capture(),
            eq(String.class));
    HttpEntity<String> requestEntity = LPNRequestBodyCaptor.getValue();
    VoidLPNRequest voidLPNRequestBody =
        gson.fromJson(requestEntity.getBody(), VoidLPNRequest.class);
    HttpHeaders LPNRequestHeaders = requestEntity.getHeaders();

    assertEquals(receivingMirageUrlCaptor.getValue(), receivingMirageBaseUrl);
    assertNotNull(voidLPNRequestBody);
    assertEquals(voidLPNRequestBody.getDeliveryNumber(), "123456");
    assertSame(voidLPNRequestBody.getReceivedQuantityByLines().get(0).getReceivedQty(), 25);
    assertEquals(
        voidLPNRequestBody.getReceivedQuantityByLines().get(0).getPurchaseReferenceNumber(),
        "4223042727");
    assertSame(
        voidLPNRequestBody.getReceivedQuantityByLines().get(0).getPurchaseReferenceLineNumber(), 2);
    assertEquals(
        LPNRequestHeaders.getFirst(ReceivingConstants.ACCEPT), ReceivingConstants.APPLICATION_JSON);
    assertEquals(
        LPNRequestHeaders.getFirst(ReceivingConstants.CONTENT_TYPE),
        ReceivingConstants.APPLICATION_JSON);
  }

  @Test
  public void testProcessACLException_2xxSuccess() {
    HttpHeaders requestHeaders = new HttpHeaders();
    requestHeaders.add(ReceivingConstants.ACCEPT, ReceivingConstants.APPLICATION_JSON);
    requestHeaders.add(ReceivingConstants.CONTENT_TYPE, ReceivingConstants.APPLICATION_JSON);
    TenantContext.setFacilityNum(32679);
    ArgumentCaptor<String> receivingMirageUrlCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<HttpEntity> LPNRequestBodyCaptor = ArgumentCaptor.forClass(HttpEntity.class);
    when(appConfig.getReceivingMirageBaseUrl()).thenReturn(getMockReceivingUrlACLException());
    ResponseEntity<String> mockResponseEntity = new ResponseEntity<>("{}", HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));

    mirageRestApiClient.processException(getMockACLExceptionRequest());

    verify(retryableRestConnector, times(1))
        .exchange(
            receivingMirageUrlCaptor.capture(),
            eq(HttpMethod.POST),
            LPNRequestBodyCaptor.capture(),
            eq(String.class));
    HttpEntity<String> requestEntity = LPNRequestBodyCaptor.getValue();
    MirageExceptionRequest mirageExceptionRequestBody =
        gson.fromJson(requestEntity.getBody(), MirageExceptionRequest.class);
    HttpHeaders LPNRequestHeaders = requestEntity.getHeaders();

    assertEquals(receivingMirageUrlCaptor.getValue(), receivingMirageAclExceptionUrl);
    assertNotNull(mirageExceptionRequestBody);
    assertEquals(mirageExceptionRequestBody.getAclErrorString(), "ERROR");
    assertEquals(mirageExceptionRequestBody.getTokenId(), "12345");
    assertEquals(
        LPNRequestHeaders.getFirst(ReceivingConstants.ACCEPT), ReceivingConstants.APPLICATION_JSON);
    assertEquals(
        LPNRequestHeaders.getFirst(ReceivingConstants.CONTENT_TYPE),
        ReceivingConstants.APPLICATION_JSON);
    assertEquals(mockResponseEntity.getStatusCode(), HttpStatus.OK);
    assertTrue(mockResponseEntity.getStatusCode().is2xxSuccessful());
  }

  @Test
  public void testProcessACLException_2xxSuccess_WithError() {
    HttpHeaders requestHeaders = new HttpHeaders();
    requestHeaders.add(ReceivingConstants.ACCEPT, ReceivingConstants.APPLICATION_JSON);
    requestHeaders.add(ReceivingConstants.CONTENT_TYPE, ReceivingConstants.APPLICATION_JSON);
    TenantContext.setFacilityNum(32679);
    ArgumentCaptor<String> receivingMirageUrlCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<HttpEntity> LPNRequestBodyCaptor = ArgumentCaptor.forClass(HttpEntity.class);
    when(appConfig.getReceivingMirageBaseUrl()).thenReturn(getMockReceivingUrlACLException());
    String body =
        "{\n"
            + "    \"title\": \"Match Found\",\n"
            + "    \"message\": \"Place case onto conveyor with label\",\n"
            + "    \"code\": \"MATCH_FOUND\"\n"
            + "}";
    ResponseEntity<String> mockResponseEntity = new ResponseEntity<>(body, HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    try {
      mirageRestApiClient.processException(getMockACLExceptionRequest());
    } catch (RestClientResponseException e) {
      assertEquals(e.getMessage(), "Place case onto conveyor with label");
      assertEquals(e.getRawStatusCode(), 404);
      verify(retryableRestConnector, times(1))
          .exchange(
              receivingMirageUrlCaptor.capture(),
              eq(HttpMethod.POST),
              LPNRequestBodyCaptor.capture(),
              eq(String.class));
      HttpEntity<String> requestEntity = LPNRequestBodyCaptor.getValue();
      MirageExceptionRequest mirageExceptionRequestBody =
          gson.fromJson(requestEntity.getBody(), MirageExceptionRequest.class);
      HttpHeaders LPNRequestHeaders = requestEntity.getHeaders();

      assertEquals(receivingMirageUrlCaptor.getValue(), receivingMirageAclExceptionUrl);
      assertNotNull(mirageExceptionRequestBody);
      assertEquals(mirageExceptionRequestBody.getAclErrorString(), "ERROR");
      assertEquals(mirageExceptionRequestBody.getTokenId(), "12345");
      assertEquals(
          LPNRequestHeaders.getFirst(ReceivingConstants.ACCEPT),
          ReceivingConstants.APPLICATION_JSON);
      assertEquals(
          LPNRequestHeaders.getFirst(ReceivingConstants.CONTENT_TYPE),
          ReceivingConstants.APPLICATION_JSON);
      assertEquals(mockResponseEntity.getStatusCode(), HttpStatus.OK);
      assertTrue(mockResponseEntity.getStatusCode().is2xxSuccessful());
    }
  }

  @Test(expectedExceptions = RestClientResponseException.class)
  public void testProcessACLException_throwReceivingBadDataExceptionWhen4xxClientErrorOccurs() {
    HttpHeaders requestHeaders = new HttpHeaders();
    requestHeaders.add(ReceivingConstants.ACCEPT, ReceivingConstants.APPLICATION_JSON);
    requestHeaders.add(ReceivingConstants.CONTENT_TYPE, ReceivingConstants.APPLICATION_JSON);
    TenantContext.setFacilityNum(32679);
    ArgumentCaptor<String> receivingMirageUrlCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<HttpEntity> LPNRequestBodyCaptor = ArgumentCaptor.forClass(HttpEntity.class);
    when(appConfig.getReceivingMirageBaseUrl()).thenReturn(getMockReceivingUrlACLException());
    doThrow(
            new RestClientResponseException(
                "Some error.",
                HttpStatus.CONFLICT.value(),
                "",
                null,
                "".getBytes(),
                StandardCharsets.UTF_8))
        .when(retryableRestConnector)
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));

    mirageRestApiClient.processException(getMockACLExceptionRequest());

    verify(retryableRestConnector, times(1))
        .exchange(
            receivingMirageUrlCaptor.capture(),
            eq(HttpMethod.POST),
            LPNRequestBodyCaptor.capture(),
            eq(String.class));
    HttpEntity<String> requestEntity = LPNRequestBodyCaptor.getValue();
    MirageExceptionRequest mirageExceptionRequestBody =
        gson.fromJson(requestEntity.getBody(), MirageExceptionRequest.class);
    HttpHeaders LPNRequestHeaders = requestEntity.getHeaders();

    assertEquals(receivingMirageUrlCaptor.getValue(), receivingMirageAclExceptionUrl);
    assertNotNull(mirageExceptionRequestBody);
    assertEquals(mirageExceptionRequestBody.getAclErrorString(), "ERROR");
    assertEquals(mirageExceptionRequestBody.getTokenId(), "12345");
    assertEquals(
        LPNRequestHeaders.getFirst(ReceivingConstants.ACCEPT), ReceivingConstants.APPLICATION_JSON);
    assertEquals(
        LPNRequestHeaders.getFirst(ReceivingConstants.CONTENT_TYPE),
        ReceivingConstants.APPLICATION_JSON);
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testProcessACLException_throwReceivingInternalException() {
    HttpHeaders requestHeaders = new HttpHeaders();
    requestHeaders.add(ReceivingConstants.ACCEPT, ReceivingConstants.APPLICATION_JSON);
    requestHeaders.add(ReceivingConstants.CONTENT_TYPE, ReceivingConstants.APPLICATION_JSON);
    TenantContext.setFacilityNum(32679);
    ArgumentCaptor<String> receivingMirageUrlCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<HttpEntity> LPNRequestBodyCaptor = ArgumentCaptor.forClass(HttpEntity.class);
    when(appConfig.getReceivingMirageBaseUrl()).thenReturn(getMockReceivingUrlACLException());
    ResponseEntity<String> mockResponseEntity = new ResponseEntity<>("{}", HttpStatus.OK);
    doThrow(new ResourceAccessException("Some error."))
        .when(retryableRestConnector)
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));

    mirageRestApiClient.processException(getMockACLExceptionRequest());

    verify(retryableRestConnector, times(1))
        .exchange(
            receivingMirageUrlCaptor.capture(),
            eq(HttpMethod.POST),
            LPNRequestBodyCaptor.capture(),
            eq(String.class));
    HttpEntity<String> requestEntity = LPNRequestBodyCaptor.getValue();
    MirageExceptionRequest mirageExceptionRequestBody =
        gson.fromJson(requestEntity.getBody(), MirageExceptionRequest.class);
    HttpHeaders LPNRequestHeaders = requestEntity.getHeaders();

    assertEquals(receivingMirageUrlCaptor.getValue(), receivingMirageAclExceptionUrl);
    assertNotNull(mirageExceptionRequestBody);
    assertEquals(mirageExceptionRequestBody.getAclErrorString(), "ERROR");
    assertEquals(mirageExceptionRequestBody.getTokenId(), "12345");
    assertEquals(
        LPNRequestHeaders.getFirst(ReceivingConstants.ACCEPT), ReceivingConstants.APPLICATION_JSON);
    assertEquals(
        LPNRequestHeaders.getFirst(ReceivingConstants.CONTENT_TYPE),
        ReceivingConstants.APPLICATION_JSON);
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testProcessACLException_throws503Error() {
    HttpHeaders requestHeaders = new HttpHeaders();
    requestHeaders.add(ReceivingConstants.ACCEPT, ReceivingConstants.APPLICATION_JSON);
    requestHeaders.add(ReceivingConstants.CONTENT_TYPE, ReceivingConstants.APPLICATION_JSON);
    TenantContext.setFacilityNum(32679);
    ArgumentCaptor<String> receivingMirageUrlCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<HttpEntity> LPNRequestBodyCaptor = ArgumentCaptor.forClass(HttpEntity.class);
    when(appConfig.getReceivingMirageBaseUrl()).thenReturn(getMockReceivingUrlACLException());
    ResponseEntity<String> mockResponseEntity = new ResponseEntity<>("{}", HttpStatus.OK);
    doThrow(
            new RestClientResponseException(
                "Service Unavailable",
                503,
                HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                null,
                null,
                null))
        .when(retryableRestConnector)
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));

    mirageRestApiClient.processException(getMockACLExceptionRequest());

    verify(retryableRestConnector, times(1))
        .exchange(
            receivingMirageUrlCaptor.capture(),
            eq(HttpMethod.POST),
            LPNRequestBodyCaptor.capture(),
            eq(String.class));
    HttpEntity<String> requestEntity = LPNRequestBodyCaptor.getValue();
    MirageExceptionRequest mirageExceptionRequestBody =
        gson.fromJson(requestEntity.getBody(), MirageExceptionRequest.class);
    HttpHeaders LPNRequestHeaders = requestEntity.getHeaders();

    assertEquals(receivingMirageUrlCaptor.getValue(), receivingMirageAclExceptionUrl);
    assertNotNull(mirageExceptionRequestBody);
    assertEquals(mirageExceptionRequestBody.getAclErrorString(), "ERROR");
    assertEquals(mirageExceptionRequestBody.getTokenId(), "12345");
    assertEquals(
        LPNRequestHeaders.getFirst(ReceivingConstants.ACCEPT), ReceivingConstants.APPLICATION_JSON);
    assertEquals(
        LPNRequestHeaders.getFirst(ReceivingConstants.CONTENT_TYPE),
        ReceivingConstants.APPLICATION_JSON);
  }

  private VoidLPNRequest getMockVoidLPNRequest() {
    VoidLPNRequest mockVoidLPNRequest = new VoidLPNRequest();
    List<ReceivedQuantityByLines> MockReceivedQuantityByLinesList = new ArrayList<>();

    mockVoidLPNRequest.setDeliveryNumber("123456");
    ReceivedQuantityByLines receivedQuantityByLines = new ReceivedQuantityByLines();
    receivedQuantityByLines.setPurchaseReferenceNumber("4223042727");
    receivedQuantityByLines.setPurchaseReferenceLineNumber(2);
    receivedQuantityByLines.setReceivedQty(25);

    MockReceivedQuantityByLinesList.add(receivedQuantityByLines);
    mockVoidLPNRequest.setReceivedQuantityByLines(MockReceivedQuantityByLinesList);

    return mockVoidLPNRequest;
  }

  private MirageExceptionRequest getMockACLExceptionRequest() {
    MirageExceptionRequest aclExceptionRequest = new MirageExceptionRequest();
    aclExceptionRequest.setAclErrorString("ERROR");
    aclExceptionRequest.setLpn("a326790000100000000012345");
    aclExceptionRequest.setItemNbr("12345678");
    aclExceptionRequest.setPrinterNbr("FLOOR 1");
    aclExceptionRequest.setGroupNbr(Collections.singletonList("21979059"));
    aclExceptionRequest.setTokenId("12345");
    return aclExceptionRequest;
  }

  private String getMockReceivingBaseUrl() {
    JsonObject mockReceivingBaseUrl = new JsonObject();
    mockReceivingBaseUrl.addProperty(
        "32818", "http://receiving-mirage.us-32818.lb-node.cluster3.cloud.s32679.us.wal-mart.com");

    return mockReceivingBaseUrl.toString();
  }

  private String getMockReceivingUrlACLException() {
    JsonObject mockReceivingBaseUrl = new JsonObject();
    mockReceivingBaseUrl.addProperty(
        "32679", "http://receiving-mirage.us-32679.lb-node.cluster3.cloud.s32679.us.wal-mart.com");

    return mockReceivingBaseUrl.toString();
  }

  private Exception mockException(String message) {
    return new Exception(message);
  }
}
