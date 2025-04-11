package com.walmart.move.nim.receiving.rdc.client.ngr;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.model.ItemOverrideRequest;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrSummary;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("unchecked")
public class NgrRestApiClientTest {

  @Mock private RdcManagedConfig rdcManagedConfig;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  private Gson gson = new Gson();
  @Mock private RestConnector retryableRestConnector;
  @InjectMocks private NgrRestApiClient ngrClient;
  private HttpHeaders headers;
  private ItemOverrideRequest itemOverrideRequest;

  @BeforeClass
  public void initMocks() throws Exception {
    itemOverrideRequest = new ItemOverrideRequest();
  }

  @BeforeMethod
  public void createNimRDSRestApiClient() throws Exception {
    MockitoAnnotations.initMocks(this);
    itemOverrideRequest
        .builder()
        .deliveryNumber(8765L)
        .itemNumber(5678L)
        .packTypeCode("B")
        .handlingMethodCode("C")
        .temporaryPackTypeCode("B")
        .temporaryHandlingMethodCode("I")
        .purchaseReferenceNumber("435354")
        .purchaseReferenceLineNumber(1)
        .build();
    TenantContext.setFacilityNum(32818);
    TenantContext.setFacilityCountryCode("us");
    ReflectionTestUtils.setField(ngrClient, "gson", gson);
  }

  @AfterMethod
  public void tearDown() {
    reset(rdcManagedConfig, retryableRestConnector, tenantSpecificConfigReader);
    TenantContext.clear();
  }

  private String getMockNgrConfig() {
    JsonObject mockRdsConfig = new JsonObject();
    mockRdsConfig.addProperty("32818", "http://nimservices.s32818.us:7099");
    mockRdsConfig.addProperty("6020", "http://nimservices.s32818.us:7099");
    return mockRdsConfig.toString();
  }

  @Test
  public void test_getDeliveryReceipts() throws Exception {

    doReturn(getMockNgrConfig()).when(rdcManagedConfig).getNgrBaseUrl();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
    File resource = new ClassPathResource("OsdrReceiptsSummary.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    OsdrSummary osdrSummaryResponse =
        ngrClient.getDeliveryReceipts(12345L, MockHttpHeaders.getHeaders());

    assertNotNull(osdrSummaryResponse);
    assertTrue(!CollectionUtils.isEmpty(osdrSummaryResponse.getSummary()));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
  }

  @Test
  public void test_getDeliveryReceiptsForDSDCDelivery() throws Exception {

    doReturn(getMockNgrConfig()).when(rdcManagedConfig).getNgrBaseUrl();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
    File resource = new ClassPathResource("OsdrReceiptsSummaryForDSDC.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    OsdrSummary osdrSummaryResponse =
        ngrClient.getDeliveryReceipts(12345L, MockHttpHeaders.getHeaders());

    assertNotNull(osdrSummaryResponse);
    assertFalse(CollectionUtils.isEmpty(osdrSummaryResponse.getSummary()));
    assertNotNull(osdrSummaryResponse.getSummary().get(0).getRcvdPackCount());
    assertEquals(osdrSummaryResponse.getSummary().get(0).getRcvdPackCount().intValue(), 356);
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
  }

  @Test
  public void test_getDeliveryReceiptsConflictErrorReturnsEmptyReceipts() throws Exception {

    doReturn("http://nimservices.s32818.us:7099").when(rdcManagedConfig).getNgrBaseUrl();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
    String errorResponse =
        "{\n"
            + "    \"type\": \"error\",\n"
            + "    \"desc\": \"No load found for deliveryNumber: 12345\"\n"
            + "}";
    doReturn(getMockNgrConfig()).when(rdcManagedConfig).getNgrBaseUrl();
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
    OsdrSummary osdrSummaryResponse =
        ngrClient.getDeliveryReceipts(12345L, MockHttpHeaders.getHeaders());

    assertNotNull(osdrSummaryResponse);
    assertEquals(osdrSummaryResponse.getSummary().size(), 0);
  }

  @Test
  public void test_getDeliveryReceipts_clientErrorReturnsEmptyReceipts() throws Exception {
    doReturn("http://nimservices.s32818.us:7099").when(rdcManagedConfig).getNgrBaseUrl();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
    String errorResponse = "invalid error";
    doReturn(getMockNgrConfig()).when(rdcManagedConfig).getNgrBaseUrl();
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

    OsdrSummary osdrSummaryResponse =
        ngrClient.getDeliveryReceipts(12345L, MockHttpHeaders.getHeaders());
    assertNotNull(osdrSummaryResponse);
    assertEquals(osdrSummaryResponse.getSummary().size(), 0);
    assertNotNull(osdrSummaryResponse.getTs());
    assertNotNull(osdrSummaryResponse.getUserId());
    assertNotNull(osdrSummaryResponse.getEventType());
  }

  @Test
  public void test_getDeliveryReceipts_serverError() throws Exception {

    doReturn(getMockNgrConfig()).when(rdcManagedConfig).getNgrBaseUrl();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
    doThrow(new ResourceAccessException("mock_error"))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      ngrClient.getDeliveryReceipts(12345L, MockHttpHeaders.getHeaders());
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.INVALID_DELIVERY_RECEIPTS_REQ);
    } catch (Exception e) {
      throw e;
    }
  }

  @Test
  public void test_getReceivingLoadUrlMappingForTenant() throws IOException, ReceivingException {
    TenantContext.setFacilityNum(6020);
    doReturn(getMockNgrConfig()).when(rdcManagedConfig).getNgrBaseUrl();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    File resource = new ClassPathResource("OsdrReceiptsSummary.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));

    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    OsdrSummary osdrSummaryResponse =
        ngrClient.getDeliveryReceipts(12345L, MockHttpHeaders.getHeaders());

    assertNotNull(osdrSummaryResponse);
    assertTrue(!CollectionUtils.isEmpty(osdrSummaryResponse.getSummary()));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
  }

  @Test
  public void testUpdateItemPropertiesInNgr_Success() {
    doReturn(getMockNgrConfig()).when(rdcManagedConfig).getNgrBaseUrl();
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<>("{\"update\":\"SUCCESS\"}", HttpStatus.OK);

    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    ngrClient.updateItemProperties(itemOverrideRequest, MockHttpHeaders.getHeaders("32828", "US"));
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void
      testUpdateItemPropertiesInNgr_ThrowsReceivingBadDataExceptionWhen4xxClientErrorOccurs() {
    doReturn(getMockNgrConfig()).when(rdcManagedConfig).getNgrBaseUrl();
    ResponseEntity<String> mockResponseEntity = new ResponseEntity<>("", HttpStatus.OK);
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
    ngrClient.updateItemProperties(itemOverrideRequest, MockHttpHeaders.getHeaders("32818", "US"));
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void
      testUpdateItemPropertiesInNgr_ThrowsReceivingInternalExceptionWhenResourceAccessErrorOccurs() {
    doReturn(getMockNgrConfig()).when(rdcManagedConfig).getNgrBaseUrl();
    doThrow(new ResourceAccessException("Some error."))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    ngrClient.updateItemProperties(itemOverrideRequest, MockHttpHeaders.getHeaders("32818", "US"));
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }
}
