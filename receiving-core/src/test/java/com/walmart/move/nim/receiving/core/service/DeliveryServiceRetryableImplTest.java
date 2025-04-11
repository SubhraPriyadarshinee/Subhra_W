package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doReturn;
import static org.testng.Assert.*;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.fail;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.GDMServiceUnavailableException;
import com.walmart.move.nim.receiving.core.common.exception.GdmError;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.mock.data.MockGdmDeliveryHeaderDetails;
import com.walmart.move.nim.receiving.core.mock.data.MockGdmResponse;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.GdmPOLineResponse;
import com.walmart.move.nim.receiving.core.model.gdm.GdmDeliveryHeaderDetailsPageResponse;
import com.walmart.move.nim.receiving.core.model.gdm.GdmDeliveryHeaderDetailsResponse;
import com.walmart.move.nim.receiving.core.retry.RetryableRestConnector;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DeliveryServiceRetryableImplTest extends ReceivingTestBase {

  @InjectMocks private DeliveryServiceRetryableImpl deliveryServiceRetryable;
  private Gson gson;
  @Mock private RetryableRestConnector retryableRestConnector;
  @Mock private AppConfig appConfig;
  @Mock private DeliveryService deliveryService;
  private String deliveryNumber = "94769060";
  private String poNbr = "3615852071";
  private Integer poLineNumber = 8;

  @BeforeClass
  private void setup() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32987);
    gson = new Gson();
    ReflectionTestUtils.setField(deliveryServiceRetryable, "gson", gson);
  }

  @AfterMethod
  public void resetMocks() {
    reset(retryableRestConnector);
    reset(appConfig);
    reset(deliveryService);
  }

  @Test(
      expectedExceptions = GDMServiceUnavailableException.class,
      expectedExceptionsMessageRegExp =
          "We’re having trouble reaching GDM now. Please try again or report this to your supervisor if it continues.")
  public void testFindDeliveryDocument_GdmServiceDown() throws ReceivingException {
    when(retryableRestConnector.exchange(
            anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
        .thenThrow(new ResourceAccessException("Error"));
    deliveryServiceRetryable.findDeliveryDocument(
        1L, "00029057358162", MockHttpHeaders.getHeaders());
  }

  @Test
  public void testFindDeliveryDocument() throws ReceivingException {
    String deliveryDocument = null;
    try {
      String dataPath =
          new File("../receiving-test/src/main/resources/json/DeliveryDocuments.json")
              .getCanonicalPath();
      deliveryDocument = new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      e.printStackTrace();
    }
    when(retryableRestConnector.exchange(
            anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
        .thenReturn(new ResponseEntity<>(deliveryDocument, HttpStatus.OK));
    String deliveryDocumentResponse =
        deliveryServiceRetryable.findDeliveryDocument(
            1000000L, "00029057358162", MockHttpHeaders.getHeaders());
    verify(retryableRestConnector, times(1))
        .exchange(
            eq(
                "null/document/v2/deliveries/1000000/deliveryDocuments/itemupcs/00029057358162?includeActiveChannelMethods=true&includeCrossReferences=true"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class));
    assertEquals(deliveryDocumentResponse, deliveryDocument);
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp = "No po/poLine found for 00029057358162 in delivery 1")
  public void testGetDeliveryDocumentsByDeliveryAndGtin_NoPoLineFound() {
    when(retryableRestConnector.exchange(
            anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
        .thenReturn(new ResponseEntity<>("[]", HttpStatus.OK));
    deliveryServiceRetryable.getDeliveryDocumentsByDeliveryAndGtin(
        1L, "00029057358162", MockHttpHeaders.getHeaders());
  }

  @Test(
      expectedExceptions = GDMServiceUnavailableException.class,
      expectedExceptionsMessageRegExp =
          "We’re having trouble reaching GDM now. Please try again or report this to your supervisor if it continues.")
  public void testGetDeliveryDocumentsByDeliveryAndGtin_GdmServiceDown() {
    when(retryableRestConnector.exchange(
            anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
        .thenThrow(new ResourceAccessException("Error"));
    deliveryServiceRetryable.getDeliveryDocumentsByDeliveryAndGtin(
        1L, "00029057358162", MockHttpHeaders.getHeaders());
  }

  @Test
  public void testGetDeliveryDocumentsByDeliveryAndGtin() {
    String deliveryDocument = null;
    try {
      String dataPath =
          new File("../receiving-test/src/main/resources/json/DeliveryDocuments.json")
              .getCanonicalPath();
      deliveryDocument = new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      e.printStackTrace();
    }
    when(retryableRestConnector.exchange(
            anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
        .thenReturn(new ResponseEntity<>(deliveryDocument, HttpStatus.OK));
    List<DeliveryDocument> deliveryDocumentResponse =
        deliveryServiceRetryable.getDeliveryDocumentsByDeliveryAndGtin(
            1000000L, "00029057358162", MockHttpHeaders.getHeaders());
    verify(retryableRestConnector, times(1))
        .exchange(
            eq(
                "null/document/v2/deliveries/1000000/deliveryDocuments/itemupcs/00029057358162?includeActiveChannelMethods=true&includeCrossReferences=true"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class));
    List<DeliveryDocument> expectedResponse =
        Arrays.asList(gson.fromJson(deliveryDocument, DeliveryDocument[].class));
    assertEquals(deliveryDocumentResponse.size(), expectedResponse.size());
    assertEquals(deliveryDocumentResponse.get(0).toString(), expectedResponse.get(0).toString());
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp = "Method not implemented.")
  public void testGetGDMData() throws ReceivingException {
    deliveryServiceRetryable.getGDMData(new DeliveryUpdateMessage());
  }

  @Test(
      expectedExceptions = GDMServiceUnavailableException.class,
      expectedExceptionsMessageRegExp =
          "We’re having trouble reaching GDM now. Please try again or report this to your supervisor if it continues.")
  public void testGetPOLineInfoFromGDM_GdmServiceDown() throws ReceivingException {
    when(retryableRestConnector.exchange(
            anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
        .thenThrow(new ResourceAccessException("Error"));
    deliveryServiceRetryable.getPOLineInfoFromGDM(
        deliveryNumber, poNbr, poLineNumber, MockHttpHeaders.getHeaders());
  }

  @Test
  public void testGetPOLineInfoFromGDM_returnEmptyResponse() {
    when(retryableRestConnector.exchange(
            anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
        .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));
    try {
      deliveryServiceRetryable.getPOLineInfoFromGDM(
          deliveryNumber, poNbr, poLineNumber, MockHttpHeaders.getHeaders());
    } catch (ReceivingException e) {
      assertEquals(HttpStatus.NOT_FOUND, e.getHttpStatus());
      assertEquals(
          GdmError.ITEM_NOT_FOUND_ERROR.getErrorCode(), e.getErrorResponse().getErrorCode());
      assertEquals(
          GdmError.ITEM_NOT_FOUND_ERROR.getErrorMessage(), e.getErrorResponse().getErrorMessage());
    }
  }

  @Test
  public void testGetPOLineInfoFromGDM_returnNull() {
    when(retryableRestConnector.exchange(
            anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
        .thenReturn(null);
    try {
      deliveryServiceRetryable.getPOLineInfoFromGDM(
          deliveryNumber, poNbr, poLineNumber, MockHttpHeaders.getHeaders());
    } catch (ReceivingException e) {
      assertEquals(HttpStatus.NOT_FOUND, e.getHttpStatus());
      assertEquals(
          GdmError.ITEM_NOT_FOUND_ERROR.getErrorCode(), e.getErrorResponse().getErrorCode());
      assertEquals(
          GdmError.ITEM_NOT_FOUND_ERROR.getErrorMessage(), e.getErrorResponse().getErrorMessage());
    }
  }

  @Test
  public void testGetPOLineInfoFromGDM_ClientSeriesError() {
    doThrow(
            new RestClientResponseException(
                "Some error.",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "",
                null,
                "".getBytes(),
                StandardCharsets.UTF_8))
        .when(retryableRestConnector)
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    try {
      deliveryServiceRetryable.getPOLineInfoFromGDM(
          deliveryNumber, poNbr, poLineNumber, MockHttpHeaders.getHeaders());
    } catch (ReceivingException e) {
      assertEquals(HttpStatus.NOT_FOUND, e.getHttpStatus());
      assertEquals(
          GdmError.ITEM_NOT_FOUND_ERROR.getErrorCode(), e.getErrorResponse().getErrorCode());
      assertEquals(
          GdmError.ITEM_NOT_FOUND_ERROR.getErrorMessage(), e.getErrorResponse().getErrorMessage());
    }
  }

  @Test
  public void testGetPOLineInfoFromGDM() {
    String deliveryDetailsJson = null;
    try {
      String dataPath =
          new File("../receiving-test/src/main/resources/json/gdm/v2/DeliveryDetails.json")
              .getCanonicalPath();
      deliveryDetailsJson = new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      e.printStackTrace();
    }
    when(retryableRestConnector.exchange(
            anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
        .thenReturn(new ResponseEntity<>(deliveryDetailsJson, HttpStatus.OK));
    GdmPOLineResponse poLineInfoFromGDM = null;

    try {
      poLineInfoFromGDM =
          deliveryServiceRetryable.getPOLineInfoFromGDM(
              deliveryNumber, poNbr, poLineNumber, MockHttpHeaders.getHeaders());
    } catch (ReceivingException e) {
      e.printStackTrace();
    }
    assertNotNull(poLineInfoFromGDM);
    assertEquals(poLineInfoFromGDM.getDeliveryNumber(), Long.valueOf(deliveryNumber));
  }

  @Test
  public void testGetDeliveryHeaderDetailsPaginationResponseGdmReturnsEmptyResponse()
      throws IOException {
    List<Long> deliveryNumbers = new ArrayList<>();
    deliveryNumbers.add(3232323L);
    doReturn("gdmServer").when(appConfig).getGdmBaseUrl();
    when(retryableRestConnector.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                MockGdmDeliveryHeaderDetails.getMockGDMDeliveryHeaderDetailsEmptyResponse(),
                HttpStatus.OK));
    try {
      deliveryServiceRetryable.getDeliveryHeaderDetailsByDeliveryNumbers(deliveryNumbers);
    } catch (ReceivingException receivingException) {
      assertEquals(receivingException.getHttpStatus(), HttpStatus.NOT_FOUND);
      assertEquals(
          receivingException.getErrorResponse().getErrorCode(),
          ReceivingException.GDM_SEARCH_HEADER_DETAILS_ERROR_CODE);
      assertEquals(
          receivingException.getErrorResponse().getErrorMessage(),
          String.format(
              ReceivingException.DELIVERY_HEADER_DETAILS_NOT_FOUND_BY_DELIVERY_NUMBERS,
              deliveryNumbers));
    }
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
  }

  @Test
  public void testGetDeliveryHeaderDetailsPaginationResponseAllDeliveriesExistInGdm()
      throws IOException, ReceivingException {
    doReturn("gdmServer").when(appConfig).getGdmBaseUrl();
    String mockDeliveryHeaderDetailsResponse =
        MockGdmDeliveryHeaderDetails.getMockGDMDeliveryHeaderDetailsPageResponse();
    GdmDeliveryHeaderDetailsPageResponse gdmDeliveryHeaderDetailsPageResponse =
        gson.fromJson(
            mockDeliveryHeaderDetailsResponse, GdmDeliveryHeaderDetailsPageResponse.class);
    List<Long> deliveryNumbers =
        gdmDeliveryHeaderDetailsPageResponse
            .getData()
            .stream()
            .parallel()
            .map(GdmDeliveryHeaderDetailsResponse::getDeliveryNumber)
            .collect(Collectors.toList());
    when(retryableRestConnector.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(new ResponseEntity<>(mockDeliveryHeaderDetailsResponse, HttpStatus.OK));

    GdmDeliveryHeaderDetailsPageResponse response =
        deliveryServiceRetryable.getDeliveryHeaderDetailsByDeliveryNumbers(deliveryNumbers);

    assertNotNull(response);
    assertEquals(response.getData().size(), deliveryNumbers.size());
    assertNotNull(response.getPage());

    verify(retryableRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
  }

  @Test
  public void testGetDeliveryHeaderDetailsPaginationResponseDeliveriesPartiallyExistsInGdm()
      throws IOException, ReceivingException {
    doReturn("gdmServer").when(appConfig).getGdmBaseUrl();
    String mockDeliveryHeaderDetailsResponse =
        MockGdmDeliveryHeaderDetails.getMockGDMDeliveryHeaderDetailsPageResponse();
    GdmDeliveryHeaderDetailsPageResponse gdmDeliveryHeaderDetailsPageResponse =
        gson.fromJson(
            mockDeliveryHeaderDetailsResponse, GdmDeliveryHeaderDetailsPageResponse.class);
    List<Long> deliveryNumbers =
        gdmDeliveryHeaderDetailsPageResponse
            .getData()
            .stream()
            .parallel()
            .map(GdmDeliveryHeaderDetailsResponse::getDeliveryNumber)
            .collect(Collectors.toList());
    deliveryNumbers.add(232323232L);

    when(retryableRestConnector.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(new ResponseEntity<>(mockDeliveryHeaderDetailsResponse, HttpStatus.OK));

    GdmDeliveryHeaderDetailsPageResponse response =
        deliveryServiceRetryable.getDeliveryHeaderDetailsByDeliveryNumbers(deliveryNumbers);

    assertNotNull(response);
    assertNotNull(response.getPage());
    assertNotEquals(deliveryNumbers.size(), response.getData().size());

    verify(retryableRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
  }

  @Test
  public void testGetDeliveryHeaderDetailsPaginationResponseGDMThrows5XXError() {
    doReturn("gdmServer").when(appConfig).getGdmBaseUrl();
    List<Long> deliveryNumbers = new ArrayList<>();
    deliveryNumbers.add(3232323L);
    doThrow(new ResourceAccessException("IO Error."))
        .when(retryableRestConnector)
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));

    try {
      deliveryServiceRetryable.getDeliveryHeaderDetailsByDeliveryNumbers(deliveryNumbers);
    } catch (ReceivingException receivingException) {
      assertEquals(receivingException.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
      assertEquals(
          receivingException.getErrorResponse().getErrorCode(),
          ReceivingException.GDM_SEARCH_HEADER_DETAILS_ERROR_CODE);
      assertEquals(
          receivingException.getErrorResponse().getErrorMessage(),
          ReceivingException.GDM_SERVICE_DOWN);
    }

    verify(retryableRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
  }

  @Test
  public void testGetDeliveryHeaderDetailsPaginationResponseGDMThrows4XXError() {
    doReturn("gdmServer").when(appConfig).getGdmBaseUrl();
    List<Long> deliveryNumbers = new ArrayList<>();
    deliveryNumbers.add(3232323L);
    doThrow(
            new RestClientResponseException(
                "call failed", HttpStatus.BAD_REQUEST.value(), "call failed", null, null, null))
        .when(retryableRestConnector)
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));

    try {
      deliveryServiceRetryable.getDeliveryHeaderDetailsByDeliveryNumbers(deliveryNumbers);
    } catch (ReceivingException receivingException) {
      assertEquals(receivingException.getHttpStatus(), HttpStatus.NOT_FOUND);
      assertEquals(
          receivingException.getErrorResponse().getErrorCode(),
          ReceivingException.GDM_SEARCH_HEADER_DETAILS_ERROR_CODE);
      assertEquals(
          receivingException.getErrorResponse().getErrorMessage(),
          String.format(
              ReceivingException.DELIVERY_HEADER_DETAILS_NOT_FOUND_BY_DELIVERY_NUMBERS,
              deliveryNumbers));
    }

    verify(retryableRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
  }

  @Test
  public void testGetDeliveryDetailsByURIReturnsEmptyResponse() {
    String gdmURI = "getDeliveryDetails";
    URI gdmGetDeliveryUri = UriComponentsBuilder.fromUriString(gdmURI).buildAndExpand().toUri();
    when(retryableRestConnector.exchange(
            anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
        .thenReturn(new ResponseEntity<>("[]", HttpStatus.OK));
    try {
      deliveryServiceRetryable.getDeliveryByURI(gdmGetDeliveryUri, MockHttpHeaders.getHeaders());
    } catch (ReceivingException receivingException) {
      assertEquals(receivingException.getHttpStatus(), HttpStatus.NOT_FOUND);
      assertEquals(
          receivingException.getErrorResponse().getErrorCode(),
          ReceivingException.GDM_GET_DELIVERY_BY_URI);
      assertEquals(
          receivingException.getErrorResponse().getErrorMessage(),
          ReceivingException.DELIVERY_NOT_FOUND);
    }
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
  }

  @Test
  public void testGetDeliveryDetailsByURIReturnsSuccessResponse()
      throws IOException, ReceivingException {
    String gdmURI = "getDeliveryDetails";
    URI gdmGetDeliveryUri = UriComponentsBuilder.fromUriString(gdmURI).buildAndExpand().toUri();
    when(retryableRestConnector.exchange(
            anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(
                MockGdmResponse.getDeliveryDetailsByUriIncludesDummyPoReturnsSuccessResponse(),
                HttpStatus.OK));
    String response =
        deliveryServiceRetryable.getDeliveryByURI(gdmGetDeliveryUri, MockHttpHeaders.getHeaders());
    GdmPOLineResponse gdmPOLineResponse = gson.fromJson(response, GdmPOLineResponse.class);

    assertNotNull(response);
    assertNotNull(gdmPOLineResponse);
    assertTrue(gdmPOLineResponse.getDeliveryDocuments().size() > 0);

    verify(retryableRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
  }

  @Test
  public void testGetDeliveryDetailsByURIReturns4XXErrorResponse() {
    String gdmURI = "getDeliveryDetails";
    URI gdmGetDeliveryUri = UriComponentsBuilder.fromUriString(gdmURI).buildAndExpand().toUri();
    doThrow(
            new RestClientResponseException(
                "call failed", HttpStatus.BAD_REQUEST.value(), "call failed", null, null, null))
        .when(retryableRestConnector)
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));

    try {
      deliveryServiceRetryable.getDeliveryByURI(gdmGetDeliveryUri, MockHttpHeaders.getHeaders());
    } catch (ReceivingException receivingException) {
      assertEquals(receivingException.getHttpStatus(), HttpStatus.NOT_FOUND);
      assertEquals(
          receivingException.getErrorResponse().getErrorCode(),
          ReceivingException.GDM_GET_DELIVERY_BY_URI);
      assertEquals(
          receivingException.getErrorResponse().getErrorMessage(),
          ReceivingException.GDM_GET_DELIVERY_ERROR);
    }

    verify(retryableRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testGetDeliveryDetailsByURIReturns5XXErrorResponse() throws ReceivingException {
    String gdmURI = "getDeliveryDetails";
    URI gdmGetDeliveryUri = UriComponentsBuilder.fromUriString(gdmURI).buildAndExpand().toUri();
    doThrow(new ResourceAccessException("IO Error."))
        .when(retryableRestConnector)
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));

    deliveryServiceRetryable.getDeliveryByURI(gdmGetDeliveryUri, MockHttpHeaders.getHeaders());

    verify(retryableRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
  }

  @Test
  public void testFetchDeliveryDocumentByItemNumber() {
    try {
      deliveryServiceRetryable.findDeliveryDocumentByItemNumber(
          "21119003", 943037204, MockHttpHeaders.getHeaders());
      fail();
    } catch (ReceivingException exc) {
      assertEquals(HttpStatus.NOT_IMPLEMENTED, exc.getHttpStatus());
      assertEquals(ReceivingException.NOT_IMPLEMENTED_EXCEPTION, exc.getMessage());
    }
  }
}
