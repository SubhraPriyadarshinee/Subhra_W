package com.walmart.move.nim.receiving.core.client.gdm;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ORG_UNIT_ID_HEADER;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.OUTBOX_PATTERN_ENABLED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.*;
import com.walmart.move.nim.receiving.core.common.exception.GDMTrailerTemperatureBaseException;
import com.walmart.move.nim.receiving.core.common.exception.GDMTrailerTemperatureServiceFailedException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.mock.data.MockGdmResponse;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.DeliveryWithOSDRResponse;
import com.walmart.move.nim.receiving.core.model.GdmDeliveryHistoryResponse;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v3.FinalizePORequestBody;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import com.walmart.move.nim.receiving.core.service.AsyncPersister;
import com.walmart.move.nim.receiving.core.service.RapidRelayerService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class GDMRestApiClientTest {

  @Mock private RestConnector retryableRestConnector;
  @Mock private AsyncPersister asyncPersister;

  @Mock private AppConfig appConfig;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private RapidRelayerService rapidRelayerService;

  @InjectMocks private GDMRestApiClient gdmRestApiClient = new GDMRestApiClient();

  @Mock private RestConnector simpleRestConnector;
  private GdmError gdmError;
  private Gson gson;

  @BeforeClass
  public void setup() {

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32612);

    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(gdmRestApiClient, "gson", new Gson());
  }

  @AfterMethod
  public void tearDown() {
    reset(retryableRestConnector, asyncPersister, appConfig, simpleRestConnector);
  }

  private Map<String, Object> getMockHeader() {

    Map<String, Object> mockHeaders = new HashMap<>();
    mockHeaders.put(ReceivingConstants.USER_ID_HEADER_KEY, "WMT-UserId");
    mockHeaders.put(ReceivingConstants.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    mockHeaders.put(ReceivingConstants.TENENT_FACLITYNUM, "32612");
    mockHeaders.put(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    mockHeaders.put(ReceivingConstants.CORRELATION_ID_HEADER_KEY, "a1-b2-c3-d4");
    mockHeaders.put(ORG_UNIT_ID_HEADER, "3");

    return mockHeaders;
  }

  @Test
  public void testV3GetDelivery() throws IOException, GDMRestApiClientException {

    File resource = new ClassPathResource("gdm_v3_getDelivery.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    doReturn("https://dev.gdm.prod.us.walmart.net").when(appConfig).getGdmBaseUrl();

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);

    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    Map<String, Object> mockHeaders = getMockHeader();

    DeliveryWithOSDRResponse deliveryResponse =
        gdmRestApiClient.getDelivery(9967271326l, mockHeaders);

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    assertEquals(deliveryResponse.getDeliveryNumber().longValue(), 9967271326l);
  }

  @Test()
  public void testV3GetDelivery_ResourceNotFoundCase() throws IOException {

    File resource = new ClassPathResource("gdm_v3_getDelivery.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    doReturn("https://dev.gdm.prod.us.walmart.net").when(appConfig).getGdmBaseUrl();

    ResponseEntity<String> mockResponseEntity = new ResponseEntity<String>(HttpStatus.NOT_FOUND);

    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      Map<String, Object> mockHeaders = getMockHeader();
      DeliveryWithOSDRResponse deliveryResponse =
          gdmRestApiClient.getDelivery(9967261326l, mockHeaders);
    } catch (GDMRestApiClientException e) {
      assertTrue(e.getHttpStatus().is4xxClientError());
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test()
  public void testV3GetDelivery_InternalServerError() throws IOException {

    File resource = new ClassPathResource("gdm_v3_getDelivery.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    doReturn("https://dev.gdm.prod.us.walmart.net").when(appConfig).getGdmBaseUrl();

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(HttpStatus.INTERNAL_SERVER_ERROR);

    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      Map<String, Object> mockHeaders = getMockHeader();
      DeliveryWithOSDRResponse deliveryResponse =
          gdmRestApiClient.getDelivery(9967261326l, mockHeaders);
    } catch (GDMRestApiClientException e) {
      assertTrue(e.getHttpStatus().is5xxServerError());
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testfinalizePurchaseOrder() throws IOException, GDMRestApiClientException {

    File resource = new ClassPathResource("gdm_v3_finalizepo_request_body.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    doReturn("https://dev.gdm.prod.us.walmart.net").when(appConfig).getGdmBaseUrl();

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);

    doReturn(mockResponseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    Map<String, Object> headers = getMockHeader();
    FinalizePORequestBody mockFinalizePORequestBody = new FinalizePORequestBody();
    gdmRestApiClient.finalizePurchaseOrder(
        9967261326l, "9164390046", mockFinalizePORequestBody, headers);

    verify(simpleRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testfinalizePurchaseOrderBadRequest() throws IOException {

    File resource = new ClassPathResource("gdm_v3_finalizepo_request_body.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    doReturn("https://dev.gdm.prod.us.walmart.net").when(appConfig).getGdmBaseUrl();

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.BAD_REQUEST);

    doReturn(mockResponseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      Map<String, Object> headers = getMockHeader();
      FinalizePORequestBody mockFinalizePORequestBody = new FinalizePORequestBody();
      gdmRestApiClient.finalizePurchaseOrder(
          9967261326l, "9164390046", mockFinalizePORequestBody, headers);
    } catch (GDMRestApiClientException e) {
      assertEquals(e.getHttpStatus().value(), 400);
    }

    verify(simpleRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void finalizePurchaseOrder() {
    Map<String, Object> headers = getMockHeader();
    FinalizePORequestBody mockFinalizePORequestBody = new FinalizePORequestBody();
    ResponseEntity<String> mockResponseEntity = new ResponseEntity<>("", HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    try {
      gdmRestApiClient.finalizePurchaseOrder(
          9967261326l, "9164390046", mockFinalizePORequestBody, headers);
    } catch (GDMRestApiClientException e) {
      e.printStackTrace();
    }
  }

  @Test
  public void persistFinalizePoOsdrToGdm() {
    Map<String, Object> headers = getMockHeader();
    FinalizePORequestBody mockFinalizePORequestBody = new FinalizePORequestBody();
    doNothing().when(asyncPersister).persistAsyncHttp(any(), any(), any(), any(), any());
    try {
      gdmRestApiClient.persistFinalizePoOsdrToGdm(
          9967261326l, "9164390046", mockFinalizePORequestBody, headers);
    } catch (GDMRestApiClientException e) {
      e.printStackTrace();
    }
  }

  @Test
  public void saveZoneTrailerTemperature_success_200() {
    GDMDeliveryTrailerTemperatureInfo mockTrailerTemperatureRequestBody =
        new GDMDeliveryTrailerTemperatureInfo();
    ResponseEntity<String> mockResponseEntity = new ResponseEntity<>(null, HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    ResponseEntity<GDMTemperatureResponse> response =
        gdmRestApiClient.saveZoneTrailerTemperature(
            9967261326l, mockTrailerTemperatureRequestBody, MockHttpHeaders.getHeaders());

    assertNull((response.getBody()));
    assertEquals(response.getStatusCode().toString(), "200 OK");
  }

  @Test
  public void saveZoneTrailerTemperature_failure_500()
      throws GDMTrailerTemperatureServiceFailedException {
    GDMDeliveryTrailerTemperatureInfo mockTrailerTemperatureRequestBody =
        new GDMDeliveryTrailerTemperatureInfo();
    ResponseEntity<String> mockResponseEntity = new ResponseEntity<>(null, HttpStatus.OK);
    doThrow(GDMTrailerTemperatureServiceFailedException.class)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      gdmRestApiClient.saveZoneTrailerTemperature(
          9967261326l, mockTrailerTemperatureRequestBody, MockHttpHeaders.getHeaders());
    } catch (GDMTrailerTemperatureBaseException e) {
      assertEquals(e.getErrorCode(), "GLS-RCV-GDM-500");
      assertEquals(
          e.getErrorMessage(),
          "We are unable to process the request at this time. This may be due to a system issue. Please try again or contact your supervisor if this continues.");
      assertEquals(e.getDescription(), "GDM service is down.");
    }
  }

  @Test
  public void saveZoneTrailerTemperature_partial_po_finalized_206() {
    GDMDeliveryTrailerTemperatureInfo mockTrailerTemperatureRequestBody =
        createGDMDeliveryTrailerTemperatureInfoValidRequest1();
    GDMTemperatureResponse mockResponse =
        new GDMTemperatureResponse(
            ReceivingConstants.GDM_DELIVERY_TRAILER_TEMPERATURE_PO_FINALIZED_ERROR_CODE,
            new HashSet<>(Arrays.asList("3490349")));

    doReturn(new ResponseEntity<>(mockResponse, HttpStatus.PARTIAL_CONTENT))
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    ResponseEntity<GDMTemperatureResponse> response =
        gdmRestApiClient.saveZoneTrailerTemperature(
            9967261326l, mockTrailerTemperatureRequestBody, MockHttpHeaders.getHeaders());

    assertNotNull(response.getBody());

    GDMTemperatureResponse responseBody = response.getBody();
    assertEquals(responseBody.getReasonCode(), "TRAILER_TEMPERATURE_UPDATE_FAILED_AS_PO_FINALIZED");
    assertEquals(responseBody.getFinalizedPos(), new HashSet<>(Arrays.asList("3490349")));
  }

  @Test
  public void saveZoneTrailerTemperature_all_po_finalized_206() {
    GDMDeliveryTrailerTemperatureInfo mockTrailerTemperatureRequestBody =
        createGDMDeliveryTrailerTemperatureInfoValidRequest1();
    GDMTemperatureResponse mockResponse =
        new GDMTemperatureResponse(
            ReceivingConstants.GDM_DELIVERY_TRAILER_TEMPERATURE_PO_FINALIZED_ERROR_CODE,
            new HashSet<>(Arrays.asList("3490349", "1340504")));

    doReturn(new ResponseEntity<>(mockResponse, HttpStatus.PARTIAL_CONTENT))
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    ResponseEntity<GDMTemperatureResponse> response =
        gdmRestApiClient.saveZoneTrailerTemperature(
            9967261326l, mockTrailerTemperatureRequestBody, MockHttpHeaders.getHeaders());

    assertNotNull(response.getBody());

    GDMTemperatureResponse responseBody = response.getBody();
    assertEquals(responseBody.getReasonCode(), "TRAILER_TEMPERATURE_UPDATE_FAILED_AS_PO_FINALIZED");
    assertEquals(
        responseBody.getFinalizedPos(), new HashSet<>(Arrays.asList("3490349", "1340504")));
  }

  private GDMDeliveryTrailerTemperatureInfo createGDMDeliveryTrailerTemperatureInfoValidRequest1() {
    GDMDeliveryTrailerTemperatureInfo request = new GDMDeliveryTrailerTemperatureInfo();
    Set<TrailerZoneTemperature> zones = new HashSet<>();
    zones.add(
        new TrailerZoneTemperature(
            "1",
            new TrailerTemperature("1", "F"),
            new HashSet<>(Arrays.asList("1340504", "3490349"))));
    zones.add(
        new TrailerZoneTemperature(
            "2", new TrailerTemperature("5", "F"), new HashSet<>(Arrays.asList("1340504"))));

    request.setZones(zones);
    request.setHasOneZone(false);
    return request;
  }

  @Test
  public void test_getTrailerZoneTemperature() throws ReceivingException, IOException {

    File resource = new ClassPathResource("gdmTTailer_Temperature_Response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    HttpHeaders mockHeaders = MockHttpHeaders.getHeaders();
    doReturn("https://dev.gdm.prod.us.walmart.net").when(appConfig).getGdmBaseUrl();

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);

    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    GDMDeliveryTrailerTemperatureInfo response =
        gdmRestApiClient.buildTrailerZoneTemperatureResponse(12345l, mockHeaders);

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void test_GetTrailerZoneTemperature_BadRequest() throws ReceivingException {
    GDMDeliveryTrailerTemperatureInfo response = null;
    HttpHeaders mockHeaders = MockHttpHeaders.getHeaders();
    ReceivingDataNotFoundException expectedError =
        new ReceivingDataNotFoundException(
            String.format(
                ReceivingException.TRAILER_TEMPERATURE_NOT_FOUND_ERROR_MESSAGE, 567898765L),
            ReceivingException.DELIVERY_NOT_FOUND);
    doThrow(
            new RestClientResponseException(
                "Some error.",
                HttpStatus.BAD_REQUEST.value(),
                "",
                null,
                "".getBytes(),
                StandardCharsets.UTF_8))
        .when(retryableRestConnector)
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    try {
      response = gdmRestApiClient.buildTrailerZoneTemperatureResponse(567898765L, mockHeaders);
    } catch (ReceivingDataNotFoundException e) {

      assertEquals(expectedError.getErrorCode(), e.getErrorCode());

      assertEquals(expectedError.getDescription(), e.getDescription());
    }
    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    assertNull(response);
  }

  @Test
  public void test_GetTrailerZoneTemperature_GDMDown() throws ReceivingException {
    GDMDeliveryTrailerTemperatureInfo response = null;
    HttpHeaders mockHeaders = MockHttpHeaders.getHeaders();
    ReceivingDataNotFoundException expectedError =
        new ReceivingDataNotFoundException(
            ReceivingException.GDM_SERVICE_DOWN, ReceivingException.DELIVERY_NOT_FOUND);
    doThrow(new ResourceAccessException("IO Error."))
        .when(retryableRestConnector)
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    try {
      response = gdmRestApiClient.buildTrailerZoneTemperatureResponse(567898765L, mockHeaders);
    } catch (ReceivingDataNotFoundException e) {

      assertEquals(expectedError.getErrorCode(), e.getErrorCode());

      assertEquals(expectedError.getDescription(), e.getDescription());
    }
    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    assertNull(response);
  }

  @Test
  public void testGetTrailerTempZonesRecorded() throws IOException, ReceivingException {
    File resource = new ClassPathResource("gdmTTailer_Temperature_Response.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    doReturn("https://dev.gdm.prod.us.walmart.net").when(appConfig).getGdmBaseUrl();

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);

    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    Integer trailerTempZonesRecorded =
        gdmRestApiClient.getTrailerTempZonesRecorded(12345l, MockHttpHeaders.getHeaders());

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    assertEquals(trailerTempZonesRecorded.intValue(), 2);
  }

  @Test
  public void testGetTrailerTempZonesRecordedWithEmptyRsp() throws IOException, ReceivingException {
    doReturn("https://dev.gdm.prod.us.walmart.net").when(appConfig).getGdmBaseUrl();

    String mockResponse = "{\"zones\":[],\"hasOneZone\":null}";
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);

    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    Integer trailerTempZonesRecorded =
        gdmRestApiClient.getTrailerTempZonesRecorded(12345l, MockHttpHeaders.getHeaders());

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    assertEquals(trailerTempZonesRecorded.intValue(), 0);
  }

  @Test
  public void testGetDeliveryHistory_success() throws IOException, GDMRestApiClientException {

    doReturn("https://dev.gdm.prod.us.walmart.net").when(appConfig).getGdmBaseUrl();

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(
            MockGdmResponse.getDeliveryHistoryReturnsSuccessResponse(), HttpStatus.OK);

    doReturn(mockResponseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    Map<String, Object> mockHeaders = getMockHeader();

    GdmDeliveryHistoryResponse deliveryResponse =
        gdmRestApiClient.getDeliveryHistory(20796734L, new HttpHeaders());

    verify(simpleRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    assertEquals(deliveryResponse.getDeliveryNumber(), 20796734);
  }

  @Test()
  public void testGetDeliveryHistory_ResourceNotFoundCase() throws IOException {

    doReturn("https://dev.gdm.prod.us.walmart.net").when(appConfig).getGdmBaseUrl();

    ResponseEntity<String> mockResponseEntity = new ResponseEntity<String>(HttpStatus.NOT_FOUND);

    doReturn(mockResponseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {

      GdmDeliveryHistoryResponse deliveryResponse =
          gdmRestApiClient.getDeliveryHistory(9967261326L, new HttpHeaders());
    } catch (GDMRestApiClientException e) {
      assertTrue(e.getHttpStatus().is4xxClientError());
    }

    verify(simpleRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test()
  public void testGetDeliveryHistory_InternalServerError() throws IOException {

    doReturn("https://dev.gdm.prod.us.walmart.net").when(appConfig).getGdmBaseUrl();

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(HttpStatus.INTERNAL_SERVER_ERROR);

    doReturn(mockResponseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      GdmDeliveryHistoryResponse deliveryResponse =
          gdmRestApiClient.getDeliveryHistory(9967261326L, new HttpHeaders());
    } catch (GDMRestApiClientException e) {
      assertTrue(e.getHttpStatus().is5xxServerError());
    }

    verify(simpleRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testPostReceiveEventGDM_success() throws GDMRestApiClientException {

    doReturn("https://dev.gdm.prod.us.walmart.net").when(appConfig).getGdmBaseUrl();

    ResponseEntity<String> mockResponseEntity = new ResponseEntity<String>(HttpStatus.OK);

    doReturn(mockResponseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    doReturn(Boolean.FALSE)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(anyString(), eq(OUTBOX_PATTERN_ENABLED), anyBoolean());
    Map<String, Object> mockHeaders = getMockHeader();
    mockHeaders.put(ORG_UNIT_ID_HEADER, "1");

    ReceiveEventRequestBody receiveEventRequestBody = ReceiveEventRequestBody.builder().build();
    gdmRestApiClient.receivingToGDMEvent(receiveEventRequestBody, mockHeaders);

    verify(simpleRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testPostReceiveEventGDM_outbox_success()
      throws GDMRestApiClientException, ReceivingException {

    doReturn("https://dev.gdm.prod.us.walmart.net").when(appConfig).getGdmBaseUrl();

    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(anyString(), eq(OUTBOX_PATTERN_ENABLED), anyBoolean());
    Map<String, Object> mockHeaders = getMockHeader();
    mockHeaders.put(ORG_UNIT_ID_HEADER, "1");

    ReceiveEventRequestBody receiveEventRequestBody = ReceiveEventRequestBody.builder().build();
    gdmRestApiClient.receivingToGDMEvent(receiveEventRequestBody, mockHeaders);

    verify(rapidRelayerService, atLeastOnce())
        .produceHttpMessage(anyString(), anyString(), anyMap());
  }

  @Test
  public void testPostReceiveEventGDM_fail() {

    doReturn("https://dev.gdm.prod.us.walmart.net").when(appConfig).getGdmBaseUrl();

    ResponseEntity<String> mockResponseEntity = new ResponseEntity<String>(HttpStatus.OK);

    doReturn(mockResponseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    doReturn(Boolean.FALSE)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(anyString(), eq(OUTBOX_PATTERN_ENABLED), anyBoolean());
    Map<String, Object> mockHeaders = getMockHeader();

    try {
      ReceiveEventRequestBody receiveEventRequestBody = ReceiveEventRequestBody.builder().build();
      gdmRestApiClient.receivingToGDMEvent(receiveEventRequestBody, mockHeaders);
    } catch (GDMRestApiClientException e) {
      assertTrue(e.getHttpStatus().is5xxServerError());
    }

    verify(simpleRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testGetReReceivingLPNContainers_success() {

    doReturn("https://dev.gdm.prod.us.walmart.net").when(appConfig).getGdmBaseUrl();

    ResponseEntity<String> mockResponseEntity = new ResponseEntity<String>(HttpStatus.OK);

    doReturn(mockResponseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    gdmRestApiClient.getReReceivingContainerResponseFromGDM("00123456", new HttpHeaders());

    verify(simpleRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testGetReReceivingLPNContainers_failure() throws ReceivingDataNotFoundException {

    doReturn("https://dev.gdm.prod.us.walmart.net").when(appConfig).getGdmBaseUrl();

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(HttpStatus.INTERNAL_SERVER_ERROR);

    doThrow(ReceivingDataNotFoundException.class)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      gdmRestApiClient.getReReceivingContainerResponseFromGDM("00123456", new HttpHeaders());
    } catch (Exception e) {
      verify(simpleRestConnector, atLeastOnce())
          .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    }
  }

  @Test()
  public void testGetPurchaseOrder_success() throws IOException, ReceivingException {

    doReturn("https://dev.gdm.prod.us.walmart.net").when(appConfig).getGdmBaseUrl();

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(MockGdmResponse.getPurchaseOrder(), HttpStatus.OK);

    doReturn(mockResponseEntity)
        .when(simpleRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    PurchaseOrder purchaseOrder = gdmRestApiClient.getPurchaseOrder("1708069842");

    verify(simpleRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    assertEquals(purchaseOrder.getPoNumber(), "1708069842");
  }

  @Test()
  public void testGetPurchaseOrder_InternalServerError() throws IOException {

    doReturn("https://dev.gdm.prod.us.walmart.net").when(appConfig).getGdmBaseUrl();

    when(simpleRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
        .thenThrow(new ResourceAccessException("SERVICE_UNAVAILABLE"));

    try {
      PurchaseOrder purchaseOrder = gdmRestApiClient.getPurchaseOrder("1708069842");
    } catch (ReceivingInternalException e) {
      assertEquals(e.getDescription(), ReceivingException.GDM_SERVICE_DOWN);
    }

    verify(simpleRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test()
  public void testGetPurchaseOrder_ResourceNotFoundCase() throws IOException {

    doReturn("https://dev.gdm.prod.us.walmart.net").when(appConfig).getGdmBaseUrl();

    when(simpleRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
        .thenThrow(
            new RestClientResponseException(
                "BAD REQUEST",
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.name(),
                null,
                null,
                null));

    try {
      PurchaseOrder purchaseOrder = gdmRestApiClient.getPurchaseOrder("1708069842");
    } catch (ReceivingDataNotFoundException e) {
      assertEquals(e.getDescription(), ReceivingException.PURCHASE_ORDER_NOT_FOUND);
    }

    verify(simpleRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testGetDeliveryByItemNumber() throws IOException, ReceivingException {

    File resource = new ClassPathResource("GdmMappedResponseV2.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    doReturn("https://dev.gdm.prod.us.walmart.net").when(appConfig).getGdmBaseUrl();

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);

    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    Map<String, Object> mockHeaders = getMockHeader();
    String gdmDeliveryDocumentsResponse =
        gdmRestApiClient.getDeliveryDocumentsByItemNumber(
            "232323", 43232323, MockHttpHeaders.getHeaders());
    assertNotNull(gdmDeliveryDocumentsResponse);
    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testGetDeliveryByItemNumber_ResourceNotFoundCase() {

    gdmError = GdmErrorCode.getErrorValue(ReceivingException.ITEM_NOT_FOUND_ERROR);
    doReturn("https://dev.gdm.prod.us.walmart.net").when(appConfig).getGdmBaseUrl();

    ResponseEntity<String> mockResponseEntity = new ResponseEntity<String>(HttpStatus.NOT_FOUND);

    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      Map<String, Object> mockHeaders = getMockHeader();
      String gdmDeliveryDocumentsResponse =
          gdmRestApiClient.getDeliveryDocumentsByItemNumber(
              "232323", 43232323, MockHttpHeaders.getHeaders());
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.NOT_FOUND);
      assertEquals(e.getErrorResponse().getErrorMessage(), gdmError.getErrorMessage());
      assertEquals(e.getErrorResponse().getErrorHeader(), gdmError.getErrorHeader());
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testGetDeliveryByItemNumber_ResponseEmpty() {

    gdmError = GdmErrorCode.getErrorValue(ReceivingException.ITEM_NOT_FOUND_ERROR);
    doReturn("https://dev.gdm.prod.us.walmart.net").when(appConfig).getGdmBaseUrl();

    ResponseEntity<String> mockResponseEntity = new ResponseEntity<String>("", HttpStatus.OK);

    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      Map<String, Object> mockHeaders = getMockHeader();
      String gdmDeliveryDocumentsResponse =
          gdmRestApiClient.getDeliveryDocumentsByItemNumber(
              "232323", 43232323, MockHttpHeaders.getHeaders());
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.NOT_FOUND);
      assertEquals(e.getErrorResponse().getErrorMessage(), gdmError.getErrorMessage());
      assertEquals(e.getErrorResponse().getErrorHeader(), gdmError.getErrorHeader());
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testGetDeliveryByItemNumber_InternalServer() throws ReceivingException {

    gdmError = GdmErrorCode.getErrorValue(ReceivingException.GDM_NETWORK_ERROR);
    doReturn("https://dev.gdm.prod.us.walmart.net").when(appConfig).getGdmBaseUrl();

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(HttpStatus.INTERNAL_SERVER_ERROR);

    doThrow(new ResourceAccessException("IO Error"))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      Map<String, Object> mockHeaders = getMockHeader();
      String gdmDeliveryDocumentsResponse =
          gdmRestApiClient.getDeliveryDocumentsByItemNumber(
              "232323", 43232323, MockHttpHeaders.getHeaders());
    } catch (GDMServiceUnavailableException e) {
      assertEquals(e.getErrorResponse().getErrorCode(), gdmError.getErrorCode());
      assertEquals(e.getErrorResponse().getErrorMessage(), gdmError.getErrorMessage());
      assertEquals(e.getErrorResponse().getErrorHeader(), gdmError.getErrorHeader());
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testGetDeliveryByItemNumber_ItemNotFoundError() throws ReceivingException {

    gdmError = GdmErrorCode.getErrorValue(ReceivingException.ITEM_NOT_FOUND_ERROR);
    doReturn("https://dev.gdm.prod.us.walmart.net").when(appConfig).getGdmBaseUrl();

    doThrow(new RestClientResponseException("Not Found", 404, "Not found", null, null, null))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      Map<String, Object> mockHeaders = getMockHeader();
      String gdmDeliveryDocumentsResponse =
          gdmRestApiClient.getDeliveryDocumentsByItemNumber(
              "232323", 43232323, MockHttpHeaders.getHeaders());
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.NOT_FOUND);
      assertEquals(e.getErrorResponse().getErrorMessage(), gdmError.getErrorMessage());
      assertEquals(e.getErrorResponse().getErrorHeader(), gdmError.getErrorHeader());
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testGetDeliveryDetails() throws ReceivingException, IOException {
    String dataPath =
        new File("../receiving-test/src/main/resources/json/gdm/v2/DeliveryDetails.json")
            .getCanonicalPath();
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(
            new String(Files.readAllBytes(Paths.get(dataPath))), HttpStatus.OK);
    String mockUrl =
        "https://atlas-gdm-cell001.walmart.com/document/deliveries/1234567?docNbr=123456789";
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    Map<String, Object> mockHeaders = getMockHeader();
    DeliveryDetails deliveryDetails = gdmRestApiClient.getDeliveryDetails(mockUrl, 1234567L);
    assertNotNull(deliveryDetails);
    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testGetDeliveryDetails_ItemNotFoundError() {
    String mockUrl =
        "https://atlas-gdm-cell001.walmart.com/document/deliveries/1234567?docNbr=123456789";
    doThrow(new RestClientResponseException("Not Found", 404, "Not found", null, null, null))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    try {
      DeliveryDetails deliveryDetails = gdmRestApiClient.getDeliveryDetails(mockUrl, 1234567L);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.NOT_FOUND);
      assertEquals(e.getErrorResponse().getErrorMessage(), ReceivingException.DELIVERY_NOT_FOUND);
    }
    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testGetDeliveryDetails_ResponseEmpty() {
    ResponseEntity<String> mockResponseEntity = new ResponseEntity<String>("", HttpStatus.OK);
    String mockUrl =
        "https://atlas-gdm-cell001.walmart.com/document/deliveries/1234567?docNbr=123456789";
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    try {
      DeliveryDetails deliveryDetails = gdmRestApiClient.getDeliveryDetails(mockUrl, 1234567L);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.NOT_FOUND);
      assertEquals(e.getErrorResponse().getErrorMessage(), ReceivingException.DELIVERY_NOT_FOUND);
    }
    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testGetDeliveryDetails_InternalServerError() {
    String mockUrl =
        "https://atlas-gdm-cell001.walmart.com/document/deliveries/1234567?docNbr=123456789";
    doThrow(new ResourceAccessException("IO Error"))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    try {
      DeliveryDetails deliveryDetails = gdmRestApiClient.getDeliveryDetails(mockUrl, 1234567L);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), HttpStatus.SERVICE_UNAVAILABLE);
      assertEquals(e.getErrorResponse().getErrorMessage(), ReceivingException.GDM_SERVICE_DOWN);
    }
    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }
}
