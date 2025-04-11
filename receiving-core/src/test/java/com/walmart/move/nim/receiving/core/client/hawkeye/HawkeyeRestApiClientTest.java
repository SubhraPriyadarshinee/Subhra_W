package com.walmart.move.nim.receiving.core.client.hawkeye;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.client.hawkeye.model.*;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

public class HawkeyeRestApiClientTest extends ReceivingTestBase {

  @Mock private AppConfig appConfig;
  @Mock private RestConnector retryableRestConnector;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @InjectMocks HawkeyeRestApiClient hawkeyeRestApiClient;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32997);
    ReflectionTestUtils.setField(hawkeyeRestApiClient, "gson", new Gson());
  }

  @Test
  public void prepareFindDeliveriesURLTest() {
    HttpHeaders headers = new HttpHeaders();
    Map<String, String> pathParams = new HashMap<>();
    pathParams.put("upc", "upc");
    pathParams.put("locationId", "1234");
    pathParams.put("fromDate", "");
    pathParams.put("toDate", "");
    when(appConfig.getHawkeyeBaseUrl()).thenReturn("{\"32997\": \"http://localhost:8080\"}");
    String url = hawkeyeRestApiClient.prepareFindDeliveriesURL(pathParams, headers);
    Assertions.assertEquals(
        "http://localhost:8080/v2/get/delivery-history?fromDate=&locationId=1234&toDate=&upc=upc",
        url);
  }

  @Test
  public void getExternalServiceBaseUrlByTenantTest() {
    String jsonObject = "{\"32997\": \"http://localhost:8080\"}";
    String baseUrl = hawkeyeRestApiClient.getExternalServiceBaseUrlByTenant(jsonObject);
    assertNotNull(baseUrl);
    assertEquals(baseUrl, "http://localhost:8080");
  }

  @Test(expected = ReceivingInternalException.class)
  public void getExternalServiceBaseUrlByTenantTest_ReceivingInternalException() {
    String jsonObject = "{\"06062\": \"http://localhost:8080\"}";
    hawkeyeRestApiClient.getExternalServiceBaseUrlByTenant(jsonObject);
  }

  @Test(expected = ReceivingInternalException.class)
  public void getExternalServiceBaseUrlByTenantTest_EmptyBaseUrl() {
    String jsonObject = null;
    hawkeyeRestApiClient.getExternalServiceBaseUrlByTenant(jsonObject);
  }

  @Test
  public void getHistoryDeliveriesFromHawkeyeTest() throws ReceivingBadDataException {

    List<String> deliveries = Arrays.asList("delivery1", "delivery2", "delivery3");
    Map<String, List<String>> deliveriesObj = new HashMap<>();
    deliveriesObj.put("groupNbrList", deliveries);
    DeliverySearchRequest deliverySearchRequest = getDeliverySearchRequest();

    HttpHeaders headers = getHttpHeaders();

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<>(String.valueOf(deliveriesObj), HttpStatus.OK);

    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    when(appConfig.getHawkeyeBaseUrl()).thenReturn("{\"32997\": \"http://localhost:8080\"}");
    Optional<List<String>> deliveryListOptional =
        hawkeyeRestApiClient.getHistoryDeliveriesFromHawkeye(deliverySearchRequest, headers);
    Assertions.assertNotNull(deliveryListOptional);
    Assertions.assertEquals(3, deliveryListOptional.get().size());
    verify(retryableRestConnector, atLeast(1))
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
  }

  @Test
  public void getHistoryDeliveriesFromHawkeyeTest_EmptyList() throws ReceivingBadDataException {

    List<String> deliveries = Arrays.asList();
    DeliverySearchRequest deliverySearchRequest = getDeliverySearchRequest();

    HttpHeaders headers = getHttpHeaders();
    Map<String, List<String>> deliveriesObj = new HashMap<>();
    deliveriesObj.put("groupNbrList", deliveries);
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(String.valueOf(deliveriesObj), HttpStatus.OK);

    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    when(appConfig.getHawkeyeBaseUrl()).thenReturn("{\"32997\": \"http://localhost:8080\"}");
    Optional<List<String>> deliveryListOptional =
        hawkeyeRestApiClient.getHistoryDeliveriesFromHawkeye(deliverySearchRequest, headers);
    Assertions.assertNotNull(deliveryListOptional);
    Assertions.assertEquals(0, deliveryListOptional.get().size());
    verify(retryableRestConnector, atLeast(1))
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
  }

  @Test
  public void testFormatToHawkeyeDate_FromDate() {
    when(tenantSpecificConfigReader.getDCTimeZone(anyInt())).thenReturn("America/Chicago");
    String utcDate = hawkeyeRestApiClient.formatToHawkeyeDate("02/27/2024", true);
    assertEquals(utcDate, "2024-02-27T06:00:00");
  }

  @Test
  public void testFormatToHawkeyeDate_ToDate() {
    when(tenantSpecificConfigReader.getDCTimeZone(anyInt())).thenReturn("America/Chicago");
    String utcDate = hawkeyeRestApiClient.formatToHawkeyeDate("02/27/2024", false);
    assertEquals(utcDate, "2024-02-28T05:59:59");
  }

  @Test
  public void testFormatToHawkeyeDate() {
    String utcDate = hawkeyeRestApiClient.formatToHawkeyeDate("2024-02-20T16:04:53.699Z", true);
    assertEquals(utcDate, "2024-02-20T16:04:53");
  }

  @Test
  public void getHistoryDeliveriesFromHawkeyeTest_Exception() throws ReceivingBadDataException {
    DeliverySearchRequest deliverySearchRequest = getDeliverySearchRequest();

    HttpHeaders headers = getHttpHeaders();

    doThrow(
            new RestClientResponseException(
                "Some error.",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "",
                null,
                "".getBytes(),
                StandardCharsets.UTF_8))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    when(appConfig.getHawkeyeBaseUrl()).thenReturn("{\"32997\": \"http://localhost:8080\"}");
    try {
      hawkeyeRestApiClient.getHistoryDeliveriesFromHawkeye(deliverySearchRequest, headers);
    } catch (ReceivingBadDataException e) {
      assertNotNull(e.getMessage());
    }
    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
  }

  @Test
  public void getHistoryDeliveriesFromHawkeyeTest_InvalidRequestException()
      throws ReceivingBadDataException {
    DeliverySearchRequest deliverySearchRequest = getDeliverySearchRequest();

    HttpHeaders headers = getHttpHeaders();
    when(appConfig.getHawkeyeBaseUrl()).thenReturn("{\"32997\": \"http://localhost:8080\"}");

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
    try {
      hawkeyeRestApiClient.getHistoryDeliveriesFromHawkeye(deliverySearchRequest, headers);
    } catch (ReceivingBadDataException e) {
      assertNotNull(e.getMessage());
    }
    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
  }

  @Test(expected = ReceivingInternalException.class)
  public void getHistoryDeliveriesFromHawkeyeTest_InvalidHawkeyeUrl()
      throws ReceivingBadDataException {
    DeliverySearchRequest deliverySearchRequest = getDeliverySearchRequest();
    HttpHeaders headers = getHttpHeaders();
    when(appConfig.getHawkeyeBaseUrl()).thenThrow(RuntimeException.class);
    hawkeyeRestApiClient.getHistoryDeliveriesFromHawkeye(deliverySearchRequest, headers);
  }

  @Test(expected = ReceivingBadDataException.class)
  public void getHistoryDeliveriesFromHawkeyeTest_ReceivingBadDataException()
      throws ReceivingBadDataException {
    List<String> deliveries = Arrays.asList("delivery1", "delivery2", "delivery3");
    Map<String, List<String>> deliveriesObj = new HashMap<>();
    deliveriesObj.put("groupNbrList", deliveries);
    DeliverySearchRequest deliverySearchRequest = getDeliverySearchRequest();
    deliverySearchRequest.setFromDate("fromDate");
    HttpHeaders headers = getHttpHeaders();
    hawkeyeRestApiClient.getHistoryDeliveriesFromHawkeye(deliverySearchRequest, headers);
  }

  @Test
  public void labelUpdateToHawkeyeTest() {
    List<LabelUpdateRequest> labelUpdateRequestList = getListOfLabelUpdateRequests();
    HttpHeaders httpHeaders = getHttpHeaders();
    ResponseEntity<String> mockResponseEntity = new ResponseEntity<>(null, HttpStatus.OK);
    when(appConfig.getHawkeyeBaseUrl()).thenReturn("{\"32997\": \"http://localhost:8080\"}");
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    hawkeyeRestApiClient.labelUpdateToHawkeye(labelUpdateRequestList, httpHeaders);
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test(expected = ReceivingBadDataException.class)
  public void labelUpdateToHawkeyeTest_BadRequestException() {
    List<LabelUpdateRequest> labelUpdateRequestList = getListOfLabelUpdateRequests();
    HttpHeaders httpHeaders = getHttpHeaders();
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
    when(appConfig.getHawkeyeBaseUrl()).thenReturn("{\"32997\": \"http://localhost:8080\"}");
    hawkeyeRestApiClient.labelUpdateToHawkeye(labelUpdateRequestList, httpHeaders);
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test(expected = ReceivingBadDataException.class)
  public void labelUpdateToHawkeyeTest_ConflictException() {
    List<LabelUpdateRequest> labelUpdateRequestList = getListOfLabelUpdateRequests();
    HttpHeaders httpHeaders = getHttpHeaders();
    doThrow(
            new RestClientResponseException(
                "Some error.",
                HttpStatus.CONFLICT.value(),
                "",
                null,
                "".getBytes(),
                StandardCharsets.UTF_8))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    when(appConfig.getHawkeyeBaseUrl()).thenReturn("{\"32997\": \"http://localhost:8080\"}");
    hawkeyeRestApiClient.labelUpdateToHawkeye(labelUpdateRequestList, httpHeaders);
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test(expected = ReceivingBadDataException.class)
  public void labelUpdateToHawkeyeTest_InternalServerException() {
    List<LabelUpdateRequest> labelUpdateRequestList = getListOfLabelUpdateRequests();
    HttpHeaders httpHeaders = getHttpHeaders();
    doThrow(
            new RestClientResponseException(
                "Some error.",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "",
                null,
                "".getBytes(),
                StandardCharsets.UTF_8))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    when(appConfig.getHawkeyeBaseUrl()).thenReturn("{\"32997\": \"http://localhost:8080\"}");
    hawkeyeRestApiClient.labelUpdateToHawkeye(labelUpdateRequestList, httpHeaders);
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test(expected = ReceivingInternalException.class)
  public void labelUpdateToHawkeyeTest_ResourceAccessException() {
    List<LabelUpdateRequest> labelUpdateRequestList = getListOfLabelUpdateRequests();
    HttpHeaders httpHeaders = getHttpHeaders();
    doThrow(new ResourceAccessException(ExceptionCodes.HAWK_EYE_ERROR))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    when(appConfig.getHawkeyeBaseUrl()).thenReturn("{\"32997\": \"http://localhost:8080\"}");
    hawkeyeRestApiClient.labelUpdateToHawkeye(labelUpdateRequestList, httpHeaders);
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void itemUpdateToHawkeyeTest() {
    HawkeyeItemUpdateRequest hawkeyeItemUpdateRequest = getHawkeyeItemUpdateRequest();
    HttpHeaders httpHeaders = getHttpHeaders();
    ResponseEntity<String> mockResponseEntity = new ResponseEntity<>(null, HttpStatus.OK);
    when(appConfig.getHawkeyeBaseUrl()).thenReturn("{\"32997\": \"http://localhost:8080\"}");
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    hawkeyeRestApiClient.sendItemUpdateToHawkeye(hawkeyeItemUpdateRequest, httpHeaders);
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test(expected = ReceivingBadDataException.class)
  public void itemUpdateToHawkeyeTest_RestClientException() {
    HawkeyeItemUpdateRequest hawkeyeItemUpdateRequest = getHawkeyeItemUpdateRequest();
    HttpHeaders httpHeaders = getHttpHeaders();
    ResponseEntity<String> mockResponseEntity = new ResponseEntity<>(null, HttpStatus.OK);
    when(appConfig.getHawkeyeBaseUrl()).thenReturn("{\"32997\": \"http://localhost:8080\"}");
    doThrow(
            new RestClientResponseException(
                "Some error.",
                HttpStatus.CONFLICT.value(),
                "",
                null,
                "".getBytes(),
                StandardCharsets.UTF_8))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    hawkeyeRestApiClient.sendItemUpdateToHawkeye(hawkeyeItemUpdateRequest, httpHeaders);
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test(expected = ReceivingInternalException.class)
  public void itemUpdateToHawkeyeTest_ResourceAccessException() {
    HawkeyeItemUpdateRequest hawkeyeItemUpdateRequest = getHawkeyeItemUpdateRequest();
    HttpHeaders httpHeaders = getHttpHeaders();
    ResponseEntity<String> mockResponseEntity = new ResponseEntity<>(null, HttpStatus.OK);
    when(appConfig.getHawkeyeBaseUrl()).thenReturn("{\"32997\": \"http://localhost:8080\"}");
    doThrow(new ResourceAccessException(ExceptionCodes.HAWK_EYE_ERROR))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    hawkeyeRestApiClient.sendItemUpdateToHawkeye(hawkeyeItemUpdateRequest, httpHeaders);
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  private List<LabelUpdateRequest> getListOfLabelUpdateRequests() {
    List<LabelUpdateRequest> labelUpdateRequestList = new ArrayList<>();
    labelUpdateRequestList.add(new LabelUpdateRequest("a007229707704640076199772", "VOID"));
    labelUpdateRequestList.add(new LabelUpdateRequest("a001453688941147068037676", "PRINT"));
    labelUpdateRequestList.add(new LabelUpdateRequest("a001453688941147068037676", "DOWNLOADED"));
    return labelUpdateRequestList;
  }

  private DeliverySearchRequest getDeliverySearchRequest() {
    DeliverySearchRequest deliverySearchRequest = new DeliverySearchRequest();
    deliverySearchRequest.setLocationId("locationId");
    deliverySearchRequest.setFromDate("2024-01-31T00:00:00.001Z");
    deliverySearchRequest.setToDate("2024-01-31T23:59:59.999Z");
    return deliverySearchRequest;
  }

  private HttpHeaders getHttpHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.add("WMT-UserId", "sysadmin");
    headers.add("WMT-correlationId", "123e4567-e89b-12d3-a456-426655440000");
    headers.add("WMT-facilityNum", "06043");
    headers.add("WMT-facilityCountryCode", "US");
    headers.add("WMT-msgTimestamp", "2019-09- 24T15:25:15.000Z");
    when(appConfig.getHawkeyeBaseUrl()).thenReturn("url");
    return headers;
  }

  private HawkeyeItemUpdateRequest getHawkeyeItemUpdateRequest() {
    return HawkeyeItemUpdateRequest.builder()
        .itemNumber("123456")
        .catalogGTIN("01234567891234")
        .build();
  }

  @Test
  public void checkLabelGroupReadinessStatusTest() {
    LabelReadinessRequest labelReadinessRequest = getLabelReadinessRequest();
    HttpHeaders httpHeaders = getHttpHeaders();
    ResponseEntity<String> mockResponseEntity = new ResponseEntity<>(null, HttpStatus.OK);
    when(appConfig.getHawkeyeBaseUrl()).thenReturn("{\"32997\": \"http://localhost:8080\"}");
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    hawkeyeRestApiClient.checkLabelGroupReadinessStatus(labelReadinessRequest, httpHeaders);
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test(expected = ReceivingBadDataException.class)
  public void checkLabelGroupReadinessStatusTest_BadRequestException() {
    LabelReadinessRequest labelReadinessRequest = getLabelReadinessRequest();
    HttpHeaders httpHeaders = getHttpHeaders();
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
    when(appConfig.getHawkeyeBaseUrl()).thenReturn("{\"32997\": \"http://localhost:8080\"}");
    hawkeyeRestApiClient.checkLabelGroupReadinessStatus(labelReadinessRequest, httpHeaders);
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void checkLabelGroupReadinessStatusTest_ConflictException() {
    LabelReadinessRequest labelReadinessRequest = getLabelReadinessRequest();
    HttpHeaders httpHeaders = getHttpHeaders();
    doThrow(
            new RestClientResponseException(
                "Some error.",
                HttpStatus.CONFLICT.value(),
                "",
                null,
                "".getBytes(),
                StandardCharsets.UTF_8))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    when(appConfig.getHawkeyeBaseUrl()).thenReturn("{\"32997\": \"http://localhost:8080\"}");
    ResponseEntity response =
        hawkeyeRestApiClient.checkLabelGroupReadinessStatus(labelReadinessRequest, httpHeaders);
    assertEquals(response.getStatusCode(), HttpStatus.CONFLICT);
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test(expected = ReceivingBadDataException.class)
  public void checkLabelGroupReadinessStatusTest_InternalServerException() {
    LabelReadinessRequest labelReadinessRequest = getLabelReadinessRequest();
    HttpHeaders httpHeaders = getHttpHeaders();
    doThrow(
            new RestClientResponseException(
                "Some error.",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "",
                null,
                "".getBytes(),
                StandardCharsets.UTF_8))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    when(appConfig.getHawkeyeBaseUrl()).thenReturn("{\"32997\": \"http://localhost:8080\"}");
    hawkeyeRestApiClient.checkLabelGroupReadinessStatus(labelReadinessRequest, httpHeaders);
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test(expected = ReceivingInternalException.class)
  public void checkLabelGroupReadinessStatusTest_ResourceAccessException() {
    LabelReadinessRequest labelReadinessRequest = getLabelReadinessRequest();
    HttpHeaders httpHeaders = getHttpHeaders();
    ResponseEntity<String> mockResponseEntity = new ResponseEntity<>(null, HttpStatus.OK);
    when(appConfig.getHawkeyeBaseUrl()).thenReturn("{\"32997\": \"http://localhost:8080\"}");
    doThrow(new ResourceAccessException(ExceptionCodes.HAWK_EYE_ERROR))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    hawkeyeRestApiClient.checkLabelGroupReadinessStatus(labelReadinessRequest, httpHeaders);
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void getLpnsFromHawkeyeTest() {
    HawkeyeGetLpnsRequest hawkeyeGetLpnsRequest = getHawkeyeGetLpnsRequest();
    HttpHeaders httpHeaders = getHttpHeaders();
    ResponseEntity<String> mockResponseEntity = new ResponseEntity<>(null, HttpStatus.OK);
    when(appConfig.getHawkeyeBaseUrl()).thenReturn("{\"32997\": \"http://localhost:8080\"}");
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    hawkeyeRestApiClient.getLpnsFromHawkeye(hawkeyeGetLpnsRequest, httpHeaders);
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test(expected = ReceivingBadDataException.class)
  public void getLpnsFromHawkeyeTest_RestClientException() {
    HawkeyeGetLpnsRequest hawkeyeGetLpnsRequest = getHawkeyeGetLpnsRequest();
    HttpHeaders httpHeaders = getHttpHeaders();
    ResponseEntity<String> mockResponseEntity = new ResponseEntity<>(null, HttpStatus.OK);
    when(appConfig.getHawkeyeBaseUrl()).thenReturn("{\"32997\": \"http://localhost:8080\"}");
    doThrow(
            new RestClientResponseException(
                "Some error.",
                HttpStatus.CONFLICT.value(),
                "",
                null,
                "".getBytes(),
                StandardCharsets.UTF_8))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    hawkeyeRestApiClient.getLpnsFromHawkeye(hawkeyeGetLpnsRequest, httpHeaders);
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test(expected = ReceivingBadDataException.class)
  public void getLpnsFromHawkeyeTest_RestClientException_NoDeliveriesFound() {
    HawkeyeGetLpnsRequest hawkeyeGetLpnsRequest = getHawkeyeGetLpnsRequest();
    HttpHeaders httpHeaders = getHttpHeaders();
    ResponseEntity<String> mockResponseEntity = new ResponseEntity<>(null, HttpStatus.OK);
    when(appConfig.getHawkeyeBaseUrl()).thenReturn("{\"32997\": \"http://localhost:8080\"}");
    doThrow(
            new RestClientResponseException(
                "Some error.",
                HttpStatus.CONFLICT.value(),
                "",
                null,
                "\"path\":\"/manual-receiving\",\"code\":\"groupNbr.or.item.not.found\",\"errors\":[{\"message\":\"No Delivery or Item found\"}]}"
                    .getBytes(),
                StandardCharsets.UTF_8))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    hawkeyeRestApiClient.getLpnsFromHawkeye(hawkeyeGetLpnsRequest, httpHeaders);
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test(expected = ReceivingInternalException.class)
  public void getLpnsFromHawkeyeTest_ResourceAccessException() {
    HawkeyeGetLpnsRequest hawkeyeGetLpnsRequest = getHawkeyeGetLpnsRequest();
    HttpHeaders httpHeaders = getHttpHeaders();
    ResponseEntity<String> mockResponseEntity = new ResponseEntity<>(null, HttpStatus.OK);
    when(appConfig.getHawkeyeBaseUrl()).thenReturn("{\"32997\": \"http://localhost:8080\"}");
    doThrow(new ResourceAccessException(ExceptionCodes.HAWK_EYE_ERROR))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    hawkeyeRestApiClient.getLpnsFromHawkeye(hawkeyeGetLpnsRequest, httpHeaders);
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void sendLabelGroupUpdateToHawkeyeTest() {
    HawkeyeLabelGroupUpdateRequest hawkeyeLabelGroupUpdateRequest = getHawkeyeGroupUpdateRequest();
    HttpHeaders httpHeaders = getHttpHeaders();
    Long deliveryNumber = 123456L;
    ResponseEntity<String> mockResponseEntity = new ResponseEntity<>(null, HttpStatus.OK);
    when(appConfig.getHawkeyeBaseUrl()).thenReturn("{\"32997\": \"http://localhost:8080\"}");
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    ResponseEntity response =
        hawkeyeRestApiClient.sendLabelGroupUpdateToHawkeye(
            hawkeyeLabelGroupUpdateRequest, deliveryNumber, httpHeaders);
    assertEquals(response.getStatusCode(), HttpStatus.OK);
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test(expected = ReceivingBadDataException.class)
  public void sendLabelGroupUpdateToHawkeye_Conflict() {
    Long deliveryNumber = 123456L;
    HawkeyeLabelGroupUpdateRequest hawkeyeLabelGroupUpdateRequest = getHawkeyeGroupUpdateRequest();
    HttpHeaders httpHeaders = getHttpHeaders();
    ResponseEntity<String> mockResponseEntity = new ResponseEntity<>(null, HttpStatus.OK);
    when(appConfig.getHawkeyeBaseUrl()).thenReturn("{\"32997\": \"http://localhost:8080\"}");
    doThrow(
            new RestClientResponseException(
                "Some error.",
                HttpStatus.CONFLICT.value(),
                "",
                null,
                "".getBytes(),
                StandardCharsets.UTF_8))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    hawkeyeRestApiClient.sendLabelGroupUpdateToHawkeye(
        hawkeyeLabelGroupUpdateRequest, deliveryNumber, httpHeaders);
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test(expected = ReceivingBadDataException.class)
  public void sendLabelGroupUpdateToHawkeye_BadRequest() {
    Long deliveryNumber = 123456L;
    HawkeyeLabelGroupUpdateRequest hawkeyeLabelGroupUpdateRequest = getHawkeyeGroupUpdateRequest();
    HttpHeaders httpHeaders = getHttpHeaders();
    ResponseEntity<String> mockResponseEntity = new ResponseEntity<>(null, HttpStatus.OK);
    when(appConfig.getHawkeyeBaseUrl()).thenReturn("{\"32997\": \"http://localhost:8080\"}");
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
    hawkeyeRestApiClient.sendLabelGroupUpdateToHawkeye(
        hawkeyeLabelGroupUpdateRequest, deliveryNumber, httpHeaders);
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test(expected = ReceivingBadDataException.class)
  public void sendLabelGroupUpdateToHawkeye_InternalServerError() {
    Long deliveryNumber = 123456L;
    HawkeyeLabelGroupUpdateRequest hawkeyeLabelGroupUpdateRequest = getHawkeyeGroupUpdateRequest();
    HttpHeaders httpHeaders = getHttpHeaders();
    ResponseEntity<String> mockResponseEntity = new ResponseEntity<>(null, HttpStatus.OK);
    when(appConfig.getHawkeyeBaseUrl()).thenReturn("{\"32997\": \"http://localhost:8080\"}");
    doThrow(
            new RestClientResponseException(
                "Some error.",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "",
                null,
                "".getBytes(),
                StandardCharsets.UTF_8))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    hawkeyeRestApiClient.sendLabelGroupUpdateToHawkeye(
        hawkeyeLabelGroupUpdateRequest, deliveryNumber, httpHeaders);
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test(expected = ReceivingInternalException.class)
  public void sendLabelGroupUpdateToHawkeye_ResourceAccessException() {
    Long deliveryNumber = 123456L;
    HawkeyeLabelGroupUpdateRequest hawkeyeLabelGroupUpdateRequest = getHawkeyeGroupUpdateRequest();
    HttpHeaders httpHeaders = getHttpHeaders();
    ResponseEntity<String> mockResponseEntity = new ResponseEntity<>(null, HttpStatus.OK);
    when(appConfig.getHawkeyeBaseUrl()).thenReturn("{\"32997\": \"http://localhost:8080\"}");
    doThrow(new ResourceAccessException(ExceptionCodes.HAWK_EYE_ERROR))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    hawkeyeRestApiClient.sendLabelGroupUpdateToHawkeye(
        hawkeyeLabelGroupUpdateRequest, deliveryNumber, httpHeaders);
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void itemUpdateToHawkeyeTest_CatalogGtinUpdate() {
    HawkeyeItemUpdateRequest hawkeyeItemUpdateRequest = getHawkeyeItemUpdateRequest();
    HttpHeaders httpHeaders = getHttpHeaders();
    httpHeaders.set(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.set(ReceivingConstants.TENENT_FACLITYNUM, "32997");
    ResponseEntity<String> mockResponseEntity = new ResponseEntity<>(null, HttpStatus.OK);
    when(appConfig.getHawkeyeBaseUrl()).thenReturn("{\"32997\": \"http://localhost:8080\"}");
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    hawkeyeRestApiClient.itemUpdateToHawkeye(hawkeyeItemUpdateRequest, httpHeaders);
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void itemUpdateToHawkeyeTest_ReceivigBadDataException() {
    HawkeyeItemUpdateRequest hawkeyeItemUpdateRequest = getHawkeyeItemUpdateRequest();
    HttpHeaders httpHeaders = getHttpHeaders();
    httpHeaders.set(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.set(ReceivingConstants.TENENT_FACLITYNUM, "32997");
    when(appConfig.getHawkeyeBaseUrl()).thenReturn("{\"32997\": \"http://localhost:8080\"}");
    when(retryableRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
        .thenThrow(
            new RestClientResponseException(
                "Some error.",
                HttpStatus.CONFLICT.value(),
                "",
                null,
                "".getBytes(),
                StandardCharsets.UTF_8));
    hawkeyeRestApiClient.itemUpdateToHawkeye(hawkeyeItemUpdateRequest, httpHeaders);
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void itemUpdateToHawkeyeTest_ReceivingInternalException() {
    HawkeyeItemUpdateRequest hawkeyeItemUpdateRequest = getHawkeyeItemUpdateRequest();
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.set(ReceivingConstants.TENENT_FACLITYNUM, "32997");
    when(appConfig.getHawkeyeBaseUrl()).thenReturn("{\"32997\": \"http://localhost:8080\"}");
    doThrow(new ResourceAccessException(ExceptionCodes.HAWK_EYE_ERROR))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    hawkeyeRestApiClient.itemUpdateToHawkeye(hawkeyeItemUpdateRequest, httpHeaders);
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  private LabelReadinessRequest getLabelReadinessRequest() {
    LabelReadinessRequest labelReadinessRequest = new LabelReadinessRequest();
    labelReadinessRequest.setGroupNbr("486579334");
    labelReadinessRequest.setLocationId("ACL_DOOR_W0282");
    labelReadinessRequest.setGroupType("RCV");
    return labelReadinessRequest;
  }

  private HawkeyeGetLpnsRequest getHawkeyeGetLpnsRequest() {
    return HawkeyeGetLpnsRequest.builder()
        .deliveryNumber("123456")
        .itemNumber(5678902)
        .storeNumber(100)
        .build();
  }

  private HawkeyeLabelGroupUpdateRequest getHawkeyeGroupUpdateRequest() {
    Label label = Label.builder().itemsCount(1).labelsCount(30).build();
    return HawkeyeLabelGroupUpdateRequest.builder()
        .status("START")
        .locationId("DOOR_11")
        .groupType("RCV_DA")
        .label(label)
        .build();
  }
}
