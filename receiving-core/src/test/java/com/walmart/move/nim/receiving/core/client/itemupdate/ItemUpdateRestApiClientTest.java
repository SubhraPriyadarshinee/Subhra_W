package com.walmart.move.nim.receiving.core.client.itemupdate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ItemUpdateUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.mock.data.MockItemUpdateData;
import com.walmart.move.nim.receiving.core.model.gdm.v2.VendorComplianceRequestDates;
import com.walmart.move.nim.receiving.core.model.itemupdate.ItemUpdateResponse;
import com.walmart.move.nim.receiving.core.retry.RetryableRestConnector;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.libs.http.nio.reactor.IOReactorException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ItemUpdateRestApiClientTest {

  @Mock private AppConfig appConfig;
  @Mock private TenantSpecificConfigReader configUtils;
  private Gson gson = new Gson();
  @Mock private RetryableRestConnector retryableRestConnector;
  @InjectMocks private ItemUpdateRestApiClient itemUpdateRestApiClient;
  @Mock private ItemUpdateUtils itemUpdateUtils;
  private final String url = "http://localhost:8080";
  String itemNumber = "1234567";

  @BeforeMethod
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32818);
    TenantContext.setFacilityCountryCode("us");
    TenantContext.setCorrelationId(UUID.randomUUID().toString());
    ReflectionTestUtils.setField(itemUpdateRestApiClient, "gson", gson);
    ReflectionTestUtils.setField(itemUpdateRestApiClient, "configUtils", configUtils);
  }

  @AfterMethod
  public void tearDown() {
    reset(appConfig, retryableRestConnector, configUtils);
  }

  @Test
  public void testUpdateUPCCatalogToNodeRtHappyPath() throws RestClientResponseException {
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    doReturn(url).when(appConfig).getItemUpdateBaseUrl();
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(
            gson.toJson(MockItemUpdateData.getMockItemUpdateResponse()), HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    when(itemUpdateUtils.getIqsItemUpdateHeaders(any(HttpHeaders.class)))
        .thenReturn(MockItemUpdateData.getIqsItemUpdateHeaders(headers));
    ItemUpdateResponse response =
        itemUpdateRestApiClient.updateItem(MockItemUpdateData.getItemUpdateRequest(), headers);
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), any(Class.class));
    assertNotNull(response);
    assertEquals(
        response.getStatusMessage(),
        MockItemUpdateData.getMockItemUpdateResponse().getStatusMessage());
  }

  @Test
  public void testUpdateUPCCatalogToNodeRtHappyPath_POSTEnabled()
      throws RestClientResponseException {
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    doReturn(url).when(appConfig).getItemUpdateBaseUrl();
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.IQS_ITEM_UPSERT_ENABLED))
        .thenReturn(Boolean.TRUE);
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(
            gson.toJson(MockItemUpdateData.getMockItemUpdateResponse()), HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    when(itemUpdateUtils.getIqsItemUpdateHeaders(any(HttpHeaders.class)))
        .thenReturn(MockItemUpdateData.getIqsItemUpdateHeaders(headers));
    ItemUpdateResponse response =
        itemUpdateRestApiClient.updateItem(MockItemUpdateData.getItemUpdateRequest(), headers);
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), any(Class.class));
    assertNotNull(response);
    assertEquals(
        response.getStatusMessage(),
        MockItemUpdateData.getMockItemUpdateResponse().getStatusMessage());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testUpdateUPCCatalogToNodeRt500Exception() throws ReceivingBadDataException {
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    doReturn(url).when(appConfig).getItemUpdateBaseUrl();
    doThrow(mockRestClientException(HttpStatus.INTERNAL_SERVER_ERROR))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    when(itemUpdateUtils.getIqsItemUpdateHeaders(any(HttpHeaders.class)))
        .thenReturn(MockItemUpdateData.getIqsItemUpdateHeaders(headers));
    ItemUpdateResponse response =
        itemUpdateRestApiClient.updateItem(MockItemUpdateData.getItemUpdateRequest(), headers);
    assertNotNull(response);
    assertEquals(
        response.getStatusMessage(),
        MockItemUpdateData.getMockItemUpdateResponse().getStatusMessage());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testUpdateUPCCatalogToNodeRt404Exception() throws ReceivingBadDataException {
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    doReturn(url).when(appConfig).getItemUpdateBaseUrl();
    doThrow(mockRestClientException(HttpStatus.NOT_FOUND))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    when(itemUpdateUtils.getIqsItemUpdateHeaders(any(HttpHeaders.class)))
        .thenReturn(MockItemUpdateData.getIqsItemUpdateHeaders(headers));
    ItemUpdateResponse response =
        itemUpdateRestApiClient.updateItem(MockItemUpdateData.getItemUpdateRequest(), headers);
    assertNotNull(response);
    assertEquals(
        response.getStatusMessage(),
        MockItemUpdateData.getMockItemUpdateResponse().getStatusMessage());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testUpdateUPCCatalogToNodeRt503Exception() throws ReceivingBadDataException {
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    doReturn(url).when(appConfig).getItemUpdateBaseUrl();
    doThrow(mockRestClientException(HttpStatus.SERVICE_UNAVAILABLE))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    when(itemUpdateUtils.getIqsItemUpdateHeaders(any(HttpHeaders.class)))
        .thenReturn(MockItemUpdateData.getIqsItemUpdateHeaders(headers));
    ItemUpdateResponse response =
        itemUpdateRestApiClient.updateItem(MockItemUpdateData.getItemUpdateRequest(), headers);
    assertNotNull(response);
    assertEquals(
        response.getStatusMessage(),
        MockItemUpdateData.getMockItemUpdateResponse().getStatusMessage());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testUpdateUPCCatalogToNodeRt400Exception() throws ReceivingBadDataException {
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    doReturn(url).when(appConfig).getItemUpdateBaseUrl();
    doThrow(mockRestClientException(HttpStatus.BAD_REQUEST))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    when(itemUpdateUtils.getIqsItemUpdateHeaders(any(HttpHeaders.class)))
        .thenReturn(MockItemUpdateData.getIqsItemUpdateHeaders(headers));
    ItemUpdateResponse response =
        itemUpdateRestApiClient.updateItem(MockItemUpdateData.getItemUpdateRequest(), headers);
    assertNotNull(response);
    assertEquals(
        response.getStatusMessage(),
        MockItemUpdateData.getMockItemUpdateResponse().getStatusMessage());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testUpdateUPCCatalogIOException() throws ReceivingBadDataException {
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    doReturn(url).when(appConfig).getItemUpdateBaseUrl();
    doThrow(mockResourceAccessException())
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    when(itemUpdateUtils.getIqsItemUpdateHeaders(any(HttpHeaders.class)))
        .thenReturn(MockItemUpdateData.getIqsItemUpdateHeaders(headers));
    ItemUpdateResponse response =
        itemUpdateRestApiClient.updateItem(MockItemUpdateData.getItemUpdateRequest(), headers);
    assertNotNull(response);
    assertEquals(
        response.getStatusMessage(),
        MockItemUpdateData.getMockItemUpdateResponse().getStatusMessage());
  }

  @Test
  public void testUpdateVendorComplianceIteToNodeRtHappyPath() {
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    headers.set(ReceivingConstants.TENENT_FACLITYNUM, "32818");
    doReturn(url).when(appConfig).getItemUpdateBaseUrl();
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(
            gson.toJson(MockItemUpdateData.getMockItemUpdateResponse()), HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    when(itemUpdateUtils.getIqsItemUpdateHeaders(any(HttpHeaders.class)))
        .thenReturn(MockItemUpdateData.getIqsItemUpdateHeaders(headers));
    ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
    ItemUpdateResponse itemUpdateResponse =
        itemUpdateRestApiClient.updateItem(MockItemUpdateData.getItemUpdateRequest(), headers);
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), captor.capture(), any(Class.class));
    assertNotNull(itemUpdateResponse);
    assertEquals(
        itemUpdateResponse.getStatusMessage(),
        MockItemUpdateData.getMockItemUpdateResponse().getStatusMessage());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testUpdateVendorComplianceIteToNodeRt400Exception_LithiumIonAndLimitedQty()
      throws ReceivingBadDataException {
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    headers.set(ReceivingConstants.TENENT_FACLITYNUM, "32818");
    doReturn(url).when(appConfig).getItemUpdateBaseUrl();
    when(itemUpdateUtils.getIqsItemUpdateHeaders(any(HttpHeaders.class)))
        .thenReturn(MockItemUpdateData.getIqsItemUpdateHeaders(headers));
    doThrow(mockRestClientException(HttpStatus.BAD_REQUEST))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    itemUpdateRestApiClient.updateItem(MockItemUpdateData.getItemUpdateRequest(), headers);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testUpdateVendorComplianceIteToNodeRt503Exception_LithiumIonAndLimitedQty()
      throws ReceivingBadDataException {
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    headers.set(ReceivingConstants.TENENT_FACLITYNUM, "32818");
    doReturn(url).when(appConfig).getItemUpdateBaseUrl();
    when(itemUpdateUtils.getIqsItemUpdateHeaders(any(HttpHeaders.class)))
        .thenReturn(MockItemUpdateData.getIqsItemUpdateHeaders(headers));
    doThrow(mockRestClientException(HttpStatus.SERVICE_UNAVAILABLE))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    itemUpdateRestApiClient.updateItem(MockItemUpdateData.getItemUpdateRequest(), headers);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testUpdateVendorComplianceIteToNodeRt404Exception_LithiumIonAndLimitedQty()
      throws ReceivingBadDataException {
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    headers.set(ReceivingConstants.TENENT_FACLITYNUM, "32818");
    doReturn(url).when(appConfig).getItemUpdateBaseUrl();
    when(itemUpdateUtils.getIqsItemUpdateHeaders(any(HttpHeaders.class)))
        .thenReturn(MockItemUpdateData.getIqsItemUpdateHeaders(headers));
    doThrow(mockRestClientException(HttpStatus.NOT_FOUND))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    itemUpdateRestApiClient.updateItem(MockItemUpdateData.getItemUpdateRequest(), headers);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testUpdateVendorComplianceIteToNodeRtIOException_LithiumIonAndLimitedQty()
      throws ReceivingBadDataException {
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    headers.set(ReceivingConstants.TENENT_FACLITYNUM, "32818");
    doReturn(url).when(appConfig).getItemUpdateBaseUrl();
    when(itemUpdateUtils.getIqsItemUpdateHeaders(any(HttpHeaders.class)))
        .thenReturn(MockItemUpdateData.getIqsItemUpdateHeaders(headers));
    doThrow(mockResourceAccessException())
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    itemUpdateRestApiClient.updateItem(MockItemUpdateData.getItemUpdateRequest(), headers);
  }

  private RestClientResponseException mockRestClientException(HttpStatus httpStatus) {
    return new RestClientResponseException(
        "Some error.", httpStatus.value(), "", null, "".getBytes(), StandardCharsets.UTF_8);
  }

  private ResourceAccessException mockResourceAccessException() {
    return new ResourceAccessException("Some IO Exception.", new IOReactorException("Errror"));
  }

  private VendorComplianceRequestDates getVendorComplianceRequestDates() {
    VendorComplianceRequestDates vendorComplianceRequestDates = new VendorComplianceRequestDates();
    return vendorComplianceRequestDates;
  }
}
