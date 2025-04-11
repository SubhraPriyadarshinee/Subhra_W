package com.walmart.move.nim.receiving.rdc.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClient;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.GDMServiceUnavailableException;
import com.walmart.move.nim.receiving.core.common.exception.GdmError;
import com.walmart.move.nim.receiving.core.common.exception.GdmErrorCode;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.core.model.GdmPOLineResponse;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.core.retry.RetryableRestConnector;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.helper.TestUtils;
import com.walmart.move.nim.receiving.rdc.message.publisher.RdcMessagePublisher;
import com.walmart.move.nim.receiving.rdc.mock.data.MockDeliveryDocuments;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RdcDeliveryServiceTest {

  @Mock private RdcMessagePublisher rdcMessagePublisher;
  @InjectMocks private RdcDeliveryService rdcDeliveryService;
  @Mock private AppConfig appConfig;
  @Mock private RetryableRestConnector retryableRestConnector;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private RestUtils restUtils;
  @Mock private GDMRestApiClient gdmRestApiClient;
  private GdmError gdmError;
  private Gson gson;
  private String gdmBaseUrl = "https://dev.gdm.prod.us.walmart.net";
  private ArgumentCaptor<String> gdmUrlCaptor;

  @BeforeClass
  public void setUpBeforeClass() throws Exception {
    gson = new Gson();
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32818);
    ReflectionTestUtils.setField(rdcDeliveryService, "gson", new Gson());
  }

  @BeforeMethod
  public void resetMocks() throws Exception {
    reset(rdcMessagePublisher);
    reset(appConfig);
    reset(retryableRestConnector);
    reset(restUtils);
    reset(gdmRestApiClient);

    gdmUrlCaptor = ArgumentCaptor.forClass(String.class);
    // add default value for this flag (will currently be set only for CCs)
    doReturn(Boolean.FALSE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ReceivingConstants.IS_IQS_ITEM_SCAN_ACTIVE_CHANNELS_ENABLED);
  }

  @Test
  public void testPublishDeliveryStatus() {
    doNothing().when(rdcMessagePublisher).publishDeliveryStatus(any(DeliveryInfo.class), anyMap());
    rdcDeliveryService.publishDeliveryStatus(new DeliveryInfo(), MockHttpHeaders.getHeaders());
  }

  @Test
  public void testFindDeliveryDocumentWhenIQSEnabled() throws IOException {
    try {
      HttpHeaders headers = MockHttpHeaders.getHeaders();
      when(appConfig.getGdmBaseUrl()).thenReturn(gdmBaseUrl);
      when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
              TenantContext.getFacilityNum().toString(),
              ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
              false))
          .thenReturn(true);
      doReturn(
              new ResponseEntity<String>(
                  getDeliveryDetailsByUriIncludesDummyPoReturnsSuccessResponse(), HttpStatus.OK))
          .when(retryableRestConnector)
          .exchange(
              gdmUrlCaptor.capture(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));

      String document = rdcDeliveryService.findDeliveryDocument(12345L, "01245557855555", headers);
      assertNotNull(document);

      Map<String, String> queryMap = TestUtils.getQueryMap(gdmUrlCaptor.getValue());
      assertEquals(queryMap.get(ReceivingConstants.QUERY_GTIN), "01245557855555");
      assertFalse(queryMap.containsKey(ReceivingConstants.INCLUDE_ACTIVE_CHANNEL_METHODS));
      verify(retryableRestConnector, times(1))
          .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    } catch (ReceivingException e) {
      fail();
    }
  }

  @Test
  public void testFindDeliveryDocumentWhenIQSEnabled_EmptyResponseScenario() throws Exception {
    gdmError = GdmErrorCode.getErrorValue(ReceivingException.ITEM_NOT_FOUND_ERROR);
    try {
      HttpHeaders headers = MockHttpHeaders.getHeaders();
      when(appConfig.getGdmBaseUrl()).thenReturn(gdmBaseUrl);
      when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
              TenantContext.getFacilityNum().toString(),
              ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
              false))
          .thenReturn(true);
      doReturn(new ResponseEntity<String>("", HttpStatus.OK))
          .when(retryableRestConnector)
          .exchange(
              gdmUrlCaptor.capture(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
      String document = rdcDeliveryService.findDeliveryDocument(12345L, "01245557855555", headers);
      Map<String, String> queryMap = TestUtils.getQueryMap(gdmUrlCaptor.getValue());
      assertEquals(queryMap.get(ReceivingConstants.QUERY_GTIN), "01245557855555");
      assertFalse(queryMap.containsKey(ReceivingConstants.INCLUDE_ACTIVE_CHANNEL_METHODS));

      verify(retryableRestConnector, times(1))
          .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
      assertNull(document);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.NOT_FOUND);
      assertEquals(e.getErrorResponse().getErrorCode(), gdmError.getErrorCode());
      assertEquals(e.getErrorResponse().getErrorMessage(), gdmError.getErrorMessage());
    }
  }

  @Test
  public void testFindDeliveryDocumentWhenIQSEnabled_ExceptionScenarioGDMDown() throws Exception {
    gdmError = GdmErrorCode.getErrorValue(ReceivingException.GDM_NETWORK_ERROR);
    try {
      HttpHeaders headers = MockHttpHeaders.getHeaders();
      when(appConfig.getGdmBaseUrl()).thenReturn(gdmBaseUrl);
      when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
              TenantContext.getFacilityNum().toString(),
              ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
              false))
          .thenReturn(true);
      doThrow(new ResourceAccessException("IO Error"))
          .when(retryableRestConnector)
          .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
      String document = rdcDeliveryService.findDeliveryDocument(12345L, "01245557855555", headers);
    } catch (GDMServiceUnavailableException e) {
      assertEquals(e.getErrorResponse().getErrorCode(), gdmError.getErrorCode());
      assertEquals(e.getErrorResponse().getErrorMessage(), gdmError.getErrorMessage());
      assertEquals(e.getErrorResponse().getErrorHeader(), gdmError.getErrorHeader());
    }
  }

  @Test
  public void testFindDeliveryDocumentWhenIQSEnabled_ExceptionScenario() throws Exception {
    gdmError = GdmErrorCode.getErrorValue(ReceivingException.ITEM_NOT_FOUND_ERROR);
    try {
      HttpHeaders headers = MockHttpHeaders.getHeaders();
      when(appConfig.getGdmBaseUrl()).thenReturn(gdmBaseUrl);
      when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
              TenantContext.getFacilityNum().toString(),
              ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
              false))
          .thenReturn(true);
      doThrow(
              new RestClientResponseException(
                  "Error Fetching URL",
                  HttpStatus.INTERNAL_SERVER_ERROR.value(),
                  "",
                  null,
                  "".getBytes(),
                  StandardCharsets.UTF_8))
          .when(retryableRestConnector)
          .exchange(
              gdmUrlCaptor.capture(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
      rdcDeliveryService.findDeliveryDocument(12345L, "01245557855555", headers);
    } catch (ReceivingException e) {
      Map<String, String> queryMap = TestUtils.getQueryMap(gdmUrlCaptor.getValue());
      assertEquals(queryMap.get(ReceivingConstants.QUERY_GTIN), "01245557855555");
      assertFalse(queryMap.containsKey(ReceivingConstants.INCLUDE_ACTIVE_CHANNEL_METHODS));

      assertEquals(e.getHttpStatus(), HttpStatus.NOT_FOUND);
      assertEquals(e.getErrorResponse().getErrorCode(), gdmError.getErrorCode());
      assertEquals(e.getErrorResponse().getErrorMessage(), gdmError.getErrorMessage());
      assertEquals(e.getErrorResponse().getErrorHeader(), gdmError.getErrorHeader());
    }
  }

  @Test
  public void testFindDeliveryDocumentWhenIQSNotEnabled() throws IOException {
    try {
      HttpHeaders headers = MockHttpHeaders.getHeaders();
      Map<String, String> pathParams = new HashMap<>();
      pathParams.put(ReceivingConstants.DELIVERY_NUMBER, "12345");
      pathParams.put(ReceivingConstants.UPC_NUMBER, "01245557855555");
      when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
              TenantContext.getFacilityNum().toString(),
              ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
              false))
          .thenReturn(false);
      when(appConfig.getGdmBaseUrl()).thenReturn(gdmBaseUrl);
      String url =
          gdmBaseUrl
              + ReceivingUtils.replacePathParams(
                  ReceivingConstants.GDM_DOCUMENT_SEARCH_URI, pathParams);
      doReturn(
              new ResponseEntity<String>(
                  getDeliveryDetailsByUriIncludesDummyPoReturnsSuccessResponse(), HttpStatus.OK))
          .when(retryableRestConnector)
          .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
      String document = rdcDeliveryService.findDeliveryDocument(12345L, "01245557855555", headers);
      assertNotNull(document);
      verify(retryableRestConnector, times(1))
          .exchange(eq(url), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    } catch (ReceivingException e) {
      fail();
    }
  }

  @Test
  public void testFindDeliveryDocumentWhenIQSNotEnabled_EmptyResponseScenario() {
    gdmError = GdmErrorCode.getErrorValue(ReceivingException.ITEM_NOT_FOUND_ERROR);
    try {
      HttpHeaders headers = MockHttpHeaders.getHeaders();
      Map<String, String> pathParams = new HashMap<>();
      pathParams.put(ReceivingConstants.DELIVERY_NUMBER, "12345");
      pathParams.put(ReceivingConstants.UPC_NUMBER, "01245557855555");
      when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
              TenantContext.getFacilityNum().toString(),
              ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
              false))
          .thenReturn(false);
      when(appConfig.getGdmBaseUrl()).thenReturn(gdmBaseUrl);
      String url =
          gdmBaseUrl
              + ReceivingUtils.replacePathParams(
                  ReceivingConstants.GDM_DOCUMENT_SEARCH_URI, pathParams);
      doReturn(new ResponseEntity<String>("", HttpStatus.OK))
          .when(retryableRestConnector)
          .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
      String document = rdcDeliveryService.findDeliveryDocument(12345L, "01245557855555", headers);
      assertNull(document);
      verify(retryableRestConnector, times(1))
          .exchange(eq(url), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.NOT_FOUND);
      assertEquals(e.getErrorResponse().getErrorCode(), gdmError.getErrorCode());
      assertEquals(e.getErrorResponse().getErrorMessage(), gdmError.getErrorMessage());
    }
  }

  @Test
  public void testFindDeliveryDocumentWhenIQSNotEnabled_ExceptionScenarioGDMDown()
      throws ReceivingException {
    gdmError = GdmErrorCode.getErrorValue(ReceivingException.GDM_NETWORK_ERROR);
    try {
      HttpHeaders headers = MockHttpHeaders.getHeaders();
      Map<String, String> pathParams = new HashMap<>();
      pathParams.put(ReceivingConstants.DELIVERY_NUMBER, "12345");
      pathParams.put(ReceivingConstants.UPC_NUMBER, "01245557855555");
      when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
              TenantContext.getFacilityNum().toString(),
              ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
              false))
          .thenReturn(false);
      when(appConfig.getGdmBaseUrl()).thenReturn(gdmBaseUrl);
      String url =
          gdmBaseUrl
              + ReceivingUtils.replacePathParams(
                  ReceivingConstants.GDM_DOCUMENT_SEARCH_URI, pathParams);
      doThrow(new ResourceAccessException("IO Error"))
          .when(retryableRestConnector)
          .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
      String document = rdcDeliveryService.findDeliveryDocument(12345L, "01245557855555", headers);
      verify(retryableRestConnector, times(1))
          .exchange(eq(url), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    } catch (GDMServiceUnavailableException e) {
      assertEquals(e.getErrorResponse().getErrorCode(), gdmError.getErrorCode());
      assertEquals(e.getErrorResponse().getErrorMessage(), gdmError.getErrorMessage());
      assertEquals(e.getErrorResponse().getErrorHeader(), gdmError.getErrorHeader());
    }
  }

  @Test
  public void testFindDeliveryDocumentWhenIQSNotEnabled_ExceptionScenario() {
    gdmError = GdmErrorCode.getErrorValue(ReceivingException.ITEM_NOT_FOUND_ERROR);
    try {
      HttpHeaders headers = MockHttpHeaders.getHeaders();
      Map<String, String> pathParams = new HashMap<>();
      pathParams.put(ReceivingConstants.DELIVERY_NUMBER, "12345");
      pathParams.put(ReceivingConstants.UPC_NUMBER, "01245557855555");
      when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
              TenantContext.getFacilityNum().toString(),
              ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
              false))
          .thenReturn(false);
      when(appConfig.getGdmBaseUrl()).thenReturn(gdmBaseUrl);
      String url =
          gdmBaseUrl
              + ReceivingUtils.replacePathParams(
                  ReceivingConstants.GDM_DOCUMENT_SEARCH_URI, pathParams);
      doThrow(
              new RestClientResponseException(
                  "Error Fetching URL",
                  HttpStatus.INTERNAL_SERVER_ERROR.value(),
                  "",
                  null,
                  "".getBytes(),
                  StandardCharsets.UTF_8))
          .when(retryableRestConnector)
          .exchange(eq(url), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
      String document = rdcDeliveryService.findDeliveryDocument(12345L, "01245557855555", headers);
      verify(retryableRestConnector, times(1))
          .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.NOT_FOUND);
      assertEquals(e.getErrorResponse().getErrorCode(), gdmError.getErrorCode());
      assertEquals(e.getErrorResponse().getErrorMessage(), gdmError.getErrorMessage());
      assertEquals(e.getErrorResponse().getErrorHeader(), gdmError.getErrorHeader());
    }
  }

  public String getDeliveryDetailsByUriIncludesDummyPoReturnsSuccessResponse() throws IOException {
    File resource =
        new ClassPathResource("GdmDeliveryDetailsResponseV2_IncludeDummyPO.json").getFile();
    return new String(Files.readAllBytes(resource.toPath()));
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testGetGDMData() throws ReceivingException {
    rdcDeliveryService.getGDMData(new DeliveryUpdateMessage());
  }

  @Test
  public void testGetDeliveryDocumentsByPoAndPoLineFromGDM_Success()
      throws IOException, ReceivingException {
    String mockGDMResponse = MockDeliveryDocuments.getDeliveryDocumentsByPoAndPoLine();
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockGDMResponse, HttpStatus.OK);
    when(restUtils.get(anyString(), any(), any())).thenReturn(mockResponseEntity);
    List<DeliveryDocument> deliveryDocumentList =
        rdcDeliveryService.getDeliveryDocumentsByPoAndPoLineFromGDM(
            "232323", "43232323", 1, MockHttpHeaders.getHeaders());
    assertNotNull(deliveryDocumentList);
    assertNotNull(deliveryDocumentList.get(0).getDeliveryStatus());
    assertNotNull(deliveryDocumentList.get(0).getStateReasonCodes());
  }

  @Test
  public void testGetDeliveryDocumentsByPoAndPoLineFromGDM_EmptyStateReasonCodes()
      throws IOException, ReceivingException {
    String mockGDMResponse = MockDeliveryDocuments.getDeliveryDocumentsByPoAndPoLine();
    GdmPOLineResponse gdmPOLineResponse = gson.fromJson(mockGDMResponse, GdmPOLineResponse.class);
    gdmPOLineResponse.setStateReasonCodes(Collections.emptyList());
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(
            gson.toJson(gdmPOLineResponse, GdmPOLineResponse.class), HttpStatus.OK);
    when(restUtils.get(anyString(), any(), any())).thenReturn(mockResponseEntity);
    List<DeliveryDocument> deliveryDocumentList =
        rdcDeliveryService.getDeliveryDocumentsByPoAndPoLineFromGDM(
            "232323", "43232323", 1, MockHttpHeaders.getHeaders());
    assertNotNull(deliveryDocumentList);
    assertNotNull(deliveryDocumentList.get(0).getDeliveryStatus());
    assertNull(deliveryDocumentList.get(0).getStateReasonCodes());
  }

  @Test
  public void testGetDeliveryDocumentsByPoAndPoLineFromGDM_BlankDeliveryStatus()
      throws IOException, ReceivingException {
    String mockGDMResponse = MockDeliveryDocuments.getDeliveryDocumentsByPoAndPoLine();
    GdmPOLineResponse gdmPOLineResponse = gson.fromJson(mockGDMResponse, GdmPOLineResponse.class);
    gdmPOLineResponse.setDeliveryStatus("");
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(
            gson.toJson(gdmPOLineResponse, GdmPOLineResponse.class), HttpStatus.OK);
    when(restUtils.get(anyString(), any(), any())).thenReturn(mockResponseEntity);
    List<DeliveryDocument> deliveryDocumentList =
        rdcDeliveryService.getDeliveryDocumentsByPoAndPoLineFromGDM(
            "232323", "43232323", 1, MockHttpHeaders.getHeaders());
    assertNotNull(deliveryDocumentList);
    assertNull(deliveryDocumentList.get(0).getDeliveryStatus());
    assertNotNull(deliveryDocumentList.get(0).getStateReasonCodes());
  }

  @Test
  public void testGetDeliveryDocumentsByPoAndPoLineFromGDM_NullDeliveryStatusAndStateReasonCodes()
      throws IOException, ReceivingException {
    String mockGDMResponse = MockDeliveryDocuments.getDeliveryDocumentsByPoAndPoLine();
    GdmPOLineResponse gdmPOLineResponse = gson.fromJson(mockGDMResponse, GdmPOLineResponse.class);
    gdmPOLineResponse.setDeliveryStatus(null);
    gdmPOLineResponse.setStateReasonCodes(null);
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(
            gson.toJson(gdmPOLineResponse, GdmPOLineResponse.class), HttpStatus.OK);
    when(restUtils.get(anyString(), any(), any())).thenReturn(mockResponseEntity);
    List<DeliveryDocument> deliveryDocumentList =
        rdcDeliveryService.getDeliveryDocumentsByPoAndPoLineFromGDM(
            "232323", "43232323", 1, MockHttpHeaders.getHeaders());
    assertNotNull(deliveryDocumentList);
    assertNull(deliveryDocumentList.get(0).getDeliveryStatus());
    assertNull(deliveryDocumentList.get(0).getStateReasonCodes());
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testGetDeliveryDocumentsByPoAndPoLineFromGDM_5xxError()
      throws IOException, ReceivingException {
    when(restUtils.get(anyString(), any(), any()))
        .thenReturn(
            new ResponseEntity<String>(
                "Error in fetching resource.", HttpStatus.SERVICE_UNAVAILABLE));
    rdcDeliveryService.getDeliveryDocumentsByPoAndPoLineFromGDM(
        "232323", "43232323", 1, MockHttpHeaders.getHeaders());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testGetDeliveryDocumentsByPoAndPoLineFromGDM_4xxError()
      throws IOException, ReceivingException {
    when(restUtils.get(anyString(), any(), any()))
        .thenReturn(new ResponseEntity<String>("No delivery documents found", HttpStatus.CONFLICT));
    rdcDeliveryService.getDeliveryDocumentsByPoAndPoLineFromGDM(
        "232323", "43232323", 1, MockHttpHeaders.getHeaders());
  }

  @Test
  public void testFindDeliveryDocumentByItemNumber_Success()
      throws IOException, ReceivingException {
    File resource = new ClassPathResource("GdmMappedResponseV2_DA_Item.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    doReturn(mockResponse)
        .when(gdmRestApiClient)
        .getDeliveryDocumentsByItemNumber(anyString(), anyInt(), any(HttpHeaders.class));
    List<DeliveryDocument> deliveryDocuments =
        rdcDeliveryService.findDeliveryDocumentByItemNumber(
            "60032433", 3804890, MockHttpHeaders.getHeaders());
    verify(gdmRestApiClient, times(1))
        .getDeliveryDocumentsByItemNumber(anyString(), anyInt(), any(HttpHeaders.class));
  }

  @Test
  public void testGetDeliveryDetails_Success() throws IOException, ReceivingException {
    String mockUrl =
        "https://atlas-gdm-cell001.walmart.com/document/deliveries/1234567?docNbr=123456789";
    String dataPath =
        new File("../../receiving-test/src/main/resources/json/gdm/v2/DeliveryDetails.json")
            .getCanonicalPath();
    DeliveryDetails mockDeliveryDetails =
        JacksonParser.convertJsonToObject(
            new String(Files.readAllBytes(Paths.get(dataPath))), DeliveryDetails.class);
    doReturn(mockDeliveryDetails).when(gdmRestApiClient).getDeliveryDetails(anyString(), anyLong());
    DeliveryDetails deliveryDetails = rdcDeliveryService.getDeliveryDetails(mockUrl, 123456L);
    verify(gdmRestApiClient, times(1)).getDeliveryDetails(anyString(), anyLong());
  }
}
