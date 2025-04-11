package com.walmart.move.nim.receiving.core.client.slotting;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.MockSlottingUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.ItemData;
import com.walmart.move.nim.receiving.core.model.slotting.*;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.nio.charset.Charset;
import java.util.*;
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

public class SlottingRestApiClientTest {

  @Mock private AppConfig appConfig;
  @Mock private RestConnector retryableRestConnector;
  @Mock private TenantSpecificConfigReader configUtils;
  private SlottingErrorHandler slottingErrorHandler = new SlottingErrorHandler();
  @InjectMocks private SlottingRestApiClient slottingRestApiClient;
  private Gson gson = new Gson();

  @BeforeMethod
  public void createSlottingRestApiClient() throws Exception {
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32612);
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(slottingRestApiClient, "gson", gson);
    ReflectionTestUtils.setField(
        slottingRestApiClient, "slottingErrorHandler", slottingErrorHandler);
  }

  @AfterMethod
  public void afterMethod() {
    reset(configUtils);
  }

  private Map<String, Object> getMockHeader() {
    Map<String, Object> mockHeaders = new HashMap<>();
    mockHeaders.put(ReceivingConstants.USER_ID_HEADER_KEY, "WMT-UserId");
    mockHeaders.put(ReceivingConstants.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    mockHeaders.put(ReceivingConstants.TENENT_FACLITYNUM, "32612");
    mockHeaders.put(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    mockHeaders.put(ReceivingConstants.CORRELATION_ID_HEADER_KEY, "a1-b2-c3-d4");
    return mockHeaders;
  }

  private HttpHeaders getMockHeaderForSlotting() {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.set(ReceivingConstants.USER_ID_HEADER_KEY, "WMT-UserId");
    httpHeaders.set(ReceivingConstants.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    httpHeaders.set(ReceivingConstants.TENENT_FACLITYNUM, "32709");
    httpHeaders.set(ReceivingConstants.TENENT_COUNTRY_CODE, "US");
    httpHeaders.set(ReceivingConstants.CORRELATION_ID_HEADER_KEY, "a2-b2-c2-d2");
    httpHeaders.set(
        ReceivingConstants.SLOTTING_FEATURE_TYPE, ReceivingConstants.SLOTTING_FIND_SLOT);

    return httpHeaders;
  }

  @Test
  public void testPalletBuild() throws Exception {
    String mockRequest =
        ReceivingUtils.readClassPathResourceAsString("slotting_build_pallet_mock_request.json");
    String mockResponse =
        ReceivingUtils.readClassPathResourceAsString("slotting_build_pallet_mock_response.json");
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getGdmBaseUrl();
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    Map<String, Object> mockHeaders = getMockHeader();
    SlottingPalletBuildRequest palletBuildRequest =
        gson.fromJson(mockRequest, SlottingPalletBuildRequest.class);
    SlottingPalletBuildResponse slottingPalletBuildResponse =
        slottingRestApiClient.palletBuild(palletBuildRequest, mockHeaders);

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    assertEquals(slottingPalletBuildResponse.getContainerTrackingId(), "10852700300174");
    assertEquals(slottingPalletBuildResponse.getDivertLocation(), "Induct_Slot_01");
  }

  @Test
  public void testPalletBuild_error() throws Exception {
    String mockRequest =
        ReceivingUtils.readClassPathResourceAsString("slotting_build_pallet_mock_request.json");
    String mockErrorResponse =
        ReceivingUtils.readClassPathResourceAsString(
            "slotting_build_pallet_mock_error_response.json");
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getGdmBaseUrl();
    ResponseEntity<String> mockErrorResponseEntity =
        new ResponseEntity<String>(mockErrorResponse, HttpStatus.BAD_REQUEST);
    doReturn(mockErrorResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    Map<String, Object> mockHeaders = getMockHeader();
    SlottingPalletBuildRequest palletBuildRequest =
        gson.fromJson(mockRequest, SlottingPalletBuildRequest.class);

    try {
      slottingRestApiClient.palletBuild(palletBuildRequest, mockHeaders);
    } catch (SlottingRestApiClientException e) {
      assertEquals("GLS-SMRT-SLOTING-0001", e.getErrorResponse().getErrorCode());
      assertEquals(
          "Induct Slots/staging area not found for the given source area/door",
          e.getErrorResponse().getErrorMessage());
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  /**
   *
   *
   * <pre>
   * url=https://gls-atlas-smart-slotting-cell000-wm-gdc-stg.walmart.com/smartslotting/witron/divert/getDivertLocations,
   * request=,
   * response=
   *  no healthy upstream,
   *  error & httpStatus=org.springframework.web.client.HttpServerErrorException$ServiceUnavailable: 503 Service Unavailable: \"no healthy upstream\"
   * </pre>
   *
   * @throws Exception
   */
  @Test
  public void testPalletBuild_error_503() throws Exception {
    String mockRequest =
        ReceivingUtils.readClassPathResourceAsString("slotting_build_pallet_mock_request.json");
    String mockErrorResponse =
        ReceivingUtils.readClassPathResourceAsString(
            "slotting_build_pallet_mock_error_response.json");
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getGdmBaseUrl();

    final RestClientResponseException restClientResponseException =
        new RestClientResponseException(
            "503 Service Unavailable: \"no healthy upstream\"",
            HttpStatus.SERVICE_UNAVAILABLE.value(),
            "",
            null,
            "".getBytes(),
            Charset.forName("UTF-8"));

    doThrow(restClientResponseException)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    Map<String, Object> mockHeaders = getMockHeader();
    SlottingPalletBuildRequest palletBuildRequest =
        gson.fromJson(mockRequest, SlottingPalletBuildRequest.class);

    try {
      slottingRestApiClient.palletBuild(palletBuildRequest, mockHeaders);
    } catch (ReceivingInternalException e) {
      assertEquals(e.getErrorCode(), "GLS-RCV-SLT-503");
      assertEquals(
          e.getMessage(),
          "Smart Slotting service is down. Error MSG = 503 Service Unavailable: \"no healthy upstream\"");
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testPalletBuild_error_with_200_responseCode() throws Exception {
    String mockRequest =
        ReceivingUtils.readClassPathResourceAsString("slotting_build_pallet_mock_request.json");
    String mockErrorResponse =
        ReceivingUtils.readClassPathResourceAsString(
            "slotting_build_pallet_mock_error_response.json");
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getGdmBaseUrl();
    ResponseEntity<String> mockErrorResponseEntity =
        new ResponseEntity<String>(mockErrorResponse, HttpStatus.OK);
    doReturn(mockErrorResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    Map<String, Object> mockHeaders = getMockHeader();
    SlottingPalletBuildRequest palletBuildRequest =
        gson.fromJson(mockRequest, SlottingPalletBuildRequest.class);

    try {
      slottingRestApiClient.palletBuild(palletBuildRequest, mockHeaders);
    } catch (SlottingRestApiClientException e) {
      assertEquals("GLS-SMRT-SLOTING-0001", e.getErrorResponse().getErrorCode());
      assertEquals(
          "Induct Slots/staging area not found for the given source area/door",
          e.getErrorResponse().getErrorMessage());
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testSlotting() throws Exception {
    String mockRequest =
        ReceivingUtils.readClassPathResourceAsString("slotting_pallet_mock_request.json");
    String mockResponse =
        ReceivingUtils.readClassPathResourceAsString("slotting_pallet_mock_response.json");
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), eq("isRxSmartSlottingTestOnlyEnabled"));
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    HttpHeaders httpHeaders = getMockHeaderForSlotting();
    SlottingPalletRequest slottingPalletRequest =
        gson.fromJson(mockRequest, SlottingPalletRequest.class);
    SlottingPalletResponse slottingPalletResponse =
        slottingRestApiClient.getSlot(slottingPalletRequest, httpHeaders);

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    assertEquals(slottingPalletResponse.getMessageId(), slottingPalletRequest.getMessageId());
    assertEquals(slottingPalletResponse.getLocations().get(0).getLocation(), "H0005");
  }

  @Test
  public void testSlotting_error_with_200_responsecode() throws Exception {
    String mockRequest =
        ReceivingUtils.readClassPathResourceAsString("slotting_pallet_mock_request.json");
    String mockErrorResponse =
        ReceivingUtils.readClassPathResourceAsString("slotting_pallet_mock_error_response.json");
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    ResponseEntity<String> mockErrorResponseEntity =
        new ResponseEntity<String>(mockErrorResponse, HttpStatus.OK);
    doReturn(mockErrorResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), eq("isRxSmartSlottingTestOnlyEnabled"));
    HttpHeaders httpHeaders = getMockHeaderForSlotting();
    SlottingPalletRequest slottingPalletRequest =
        gson.fromJson(mockRequest, SlottingPalletRequest.class);

    try {
      slottingRestApiClient.getSlot(slottingPalletRequest, httpHeaders);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.AUTO_SLOT_NOT_FOUND_ERROR_IN_SMART_SLOTTING);
      assertEquals(
          e.getDescription(),
          "Resource exception from SMART-SLOTTING. Error Code = GLS-SMART-SLOTING-4040008, Error Message = No slot is available to auto-slot.User has to go for manual slotting");
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testSlottingManualSlotNotAvailableErrorResponse() throws Exception {
    String mockRequest =
        ReceivingUtils.readClassPathResourceAsString("slotting_manual_slot_mock_request.json");
    String mockErrorResponse =
        ReceivingUtils.readClassPathResourceAsString(
            "slotting_manual_slot_not_available_error_response.json");
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    ResponseEntity<String> mockErrorResponseEntity =
        new ResponseEntity<String>(mockErrorResponse, HttpStatus.OK);
    doReturn(mockErrorResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), eq("isRxSmartSlottingTestOnlyEnabled"));
    HttpHeaders httpHeaders = getMockHeaderForSlotting();
    SlottingPalletRequest slottingPalletRequest =
        gson.fromJson(mockRequest, SlottingPalletRequest.class);

    try {
      slottingRestApiClient.getSlot(slottingPalletRequest, httpHeaders);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.MANUAL_SLOT_NOT_AVAILABLE_IN_SMART_SLOTTING);
      assertEquals(
          e.getDescription(),
          "Resource exception from SMART-SLOTTING. Error Code = GLS-SMART-SLOTING-4040011, Error Message = Slot is not available for manual slotting");
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testSlottingManualSlottingNotSupportedForAtlasItems_ErrorResponse() throws Exception {
    String mockRequest =
        ReceivingUtils.readClassPathResourceAsString("slotting_manual_slot_mock_request.json");
    String mockErrorResponse =
        ReceivingUtils.readClassPathResourceAsString(
            "slotting_manual_slot_not_supported_atlas_items_error_response.json");
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    ResponseEntity<String> mockErrorResponseEntity =
        new ResponseEntity<String>(mockErrorResponse, HttpStatus.OK);
    doReturn(mockErrorResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), eq("isRxSmartSlottingTestOnlyEnabled"));
    HttpHeaders httpHeaders = getMockHeaderForSlotting();
    SlottingPalletRequest slottingPalletRequest =
        gson.fromJson(mockRequest, SlottingPalletRequest.class);

    try {
      slottingRestApiClient.getSlot(slottingPalletRequest, httpHeaders);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.MANUAL_SLOTTING_NOT_SUPPORTED_FOR_ATLAS_ITEMS);
      assertEquals(
          e.getDescription(), "Manual Split Pallet slotting is not supported for Atlas items");
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testSlotting_InventoryAvailableForDifferentItemInSamePrimeSlot_ErrorResponse()
      throws Exception {
    String mockRequest =
        ReceivingUtils.readClassPathResourceAsString("slotting_manual_slot_mock_request.json");
    String mockErrorResponse =
        ReceivingUtils.readClassPathResourceAsString(
            "slotting_inventory_available_for_diffrent_item_in_same_primeSlot_error_response.json");
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    ResponseEntity<String> mockErrorResponseEntity =
        new ResponseEntity<String>(mockErrorResponse, HttpStatus.OK);
    doReturn(mockErrorResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), eq("isRxSmartSlottingTestOnlyEnabled"));
    HttpHeaders httpHeaders = getMockHeaderForSlotting();
    SlottingPalletRequest slottingPalletRequest =
        gson.fromJson(mockRequest, SlottingPalletRequest.class);

    try {
      slottingRestApiClient.getSlot(slottingPalletRequest, httpHeaders);
    } catch (ReceivingBadDataException e) {
      assertEquals(
          e.getErrorCode(), ExceptionCodes.INVENTORY_AVAILABLE_FOR_DIFFERENT_ITEM_IN_PRIMESLOT);
      assertEquals(
          e.getDescription(),
          "Resource exception from SMART-SLOTTING. Error Code = GLS-SMART-SLOTING-4090117, Error Message = Inventory is in Available or OnHold status for a different Item in the same Prime Slot");
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testSlotting_ServerError_exception() throws Exception {
    String mockRequest =
        ReceivingUtils.readClassPathResourceAsString("slotting_pallet_mock_request.json");
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), eq("isRxSmartSlottingTestOnlyEnabled"));
    doThrow(new ResourceAccessException("mock_error"))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    HttpHeaders httpHeaders = getMockHeaderForSlotting();
    SlottingPalletRequest slottingPalletRequest =
        gson.fromJson(mockRequest, SlottingPalletRequest.class);

    try {
      slottingRestApiClient.getSlot(slottingPalletRequest, httpHeaders);
    } catch (ReceivingInternalException e) {
      assertEquals("GLS-RCV-SMART-SLOT-500", e.getErrorCode());
      assertEquals(
          "Resource exception from SMART-SLOTTING. Error Message = mock_error", e.getDescription());
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testSlotting_error_RestClientResponseException() throws Exception {
    String mockRequest =
        ReceivingUtils.readClassPathResourceAsString("slotting_pallet_mock_request.json");
    RestClientResponseException exception =
        new RestClientResponseException("mock_error", 409, "", null, null, null);
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), eq("isRxSmartSlottingTestOnlyEnabled"));
    doThrow(exception)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    HttpHeaders httpHeaders = getMockHeaderForSlotting();
    SlottingPalletRequest slottingPalletRequest =
        gson.fromJson(mockRequest, SlottingPalletRequest.class);

    try {
      slottingRestApiClient.getSlot(slottingPalletRequest, httpHeaders);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.SMART_SLOT_BAD_DATA_ERROR);
      assertEquals(e.getDescription(), ReceivingConstants.SMART_SLOT_BAD_DATA_ERROR);
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void test_freeSlot_200() throws Exception {
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), eq("isRxSmartSlottingTestOnlyEnabled"));
    doReturn(new ResponseEntity<String>(HttpStatus.OK))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    assertTrue(slottingRestApiClient.freeSlot(12345l, "A123", getMockHeaderForSlotting()));

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void test_freeSlot_error_exception() throws Exception {
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), eq("isRxSmartSlottingTestOnlyEnabled"));
    doThrow(new ResourceAccessException("mock_error"))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      slottingRestApiClient.freeSlot(12345l, "A123", getMockHeaderForSlotting());
    } catch (ReceivingBadDataException e) {
      assertEquals("GLS-RCV-SMART-SLOT-404", e.getErrorCode());
      assertEquals(
          "Resource exception from SMART-SLOTTING. Error Message = mock_error", e.getDescription());
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void test_freeSlot_error_RestClientResponseException() throws Exception {
    RestClientResponseException exception =
        new RestClientResponseException("mock_error", 409, "", null, null, null);
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), eq("isRxSmartSlottingTestOnlyEnabled"));
    doThrow(exception)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      slottingRestApiClient.freeSlot(12345l, "A123", getMockHeaderForSlotting());
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), "GLS-RCV-SMART-SLOT-409");
      assertEquals(
          e.getDescription(), "Resource exception from SMART-SLOTTING. Error Message = mock_error");
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testSlotting_error_with_200_no_prime_for_item() throws Exception {
    String mockRequest =
        ReceivingUtils.readClassPathResourceAsString("slotting_pallet_mock_request.json");
    String mockErrorResponse =
        ReceivingUtils.readClassPathResourceAsString(
            "slotting_pallet_no_prime_mock_error_response.json");
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    ResponseEntity<String> mockErrorResponseEntity =
        new ResponseEntity<String>(mockErrorResponse, HttpStatus.OK);
    doReturn(mockErrorResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), eq("isRxSmartSlottingTestOnlyEnabled"));

    HttpHeaders httpHeaders = getMockHeaderForSlotting();
    SlottingPalletRequest slottingPalletRequest =
        gson.fromJson(mockRequest, SlottingPalletRequest.class);

    try {
      slottingRestApiClient.getSlot(slottingPalletRequest, httpHeaders);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), "GLS-RCV-SMART-SLOT-PRIME-404");
      assertEquals(
          e.getDescription(),
          "Resource exception from SMART-SLOTTING. Error Code = GLS-SMART-SLOTING-4040009, Error Message = Setup issue-Prime Slot is missing for user given item");
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testSlotting_error_with_409_no_prime_for_item() throws Exception {
    String mockRequest =
        ReceivingUtils.readClassPathResourceAsString("slotting_pallet_mock_request.json");
    String mockErrorResponse =
        ReceivingUtils.readClassPathResourceAsString(
            "slotting_pallet_no_prime_mock_error_response.json");
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    RestClientResponseException restClientResponseException =
        new RestClientResponseException(
            mockErrorResponse,
            HttpStatus.CONFLICT.value(),
            "",
            null,
            mockErrorResponse.getBytes(),
            null);

    doThrow(restClientResponseException)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), eq("isRxSmartSlottingTestOnlyEnabled"));

    HttpHeaders httpHeaders = getMockHeaderForSlotting();
    SlottingPalletRequest slottingPalletRequest =
        gson.fromJson(mockRequest, SlottingPalletRequest.class);

    try {
      slottingRestApiClient.getSlot(slottingPalletRequest, httpHeaders);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.SMART_SLOT_BAD_DATA_ERROR);
      assertEquals(e.getDescription(), ReceivingConstants.SMART_SLOT_BAD_DATA_ERROR);
    }
    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void test_freeSlot_HappyPathFlow() throws Exception {
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    doReturn(new ResponseEntity<String>(HttpStatus.OK))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    assertTrue(
        slottingRestApiClient.freeSlot(
            getMockSlottingPalletRequest(), MockHttpHeaders.getHeaders()));
    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void test_freeSlot_returns_error_exception() throws Exception {
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    doThrow(new ResourceAccessException("mock_error"))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    try {
      slottingRestApiClient.freeSlot(getMockSlottingPalletRequest(), MockHttpHeaders.getHeaders());
    } catch (ReceivingBadDataException e) {
      assertEquals("GLS-RCV-SMART-SLOT-404", e.getErrorCode());
      assertEquals(
          "Resource exception from SMART-SLOTTING. Error Message = mock_error", e.getDescription());
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void test_freeSlot_returns_RestClientResponseException() throws Exception {
    RestClientResponseException exception =
        new RestClientResponseException("mock_error", 409, "", null, null, null);
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    doThrow(exception)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      slottingRestApiClient.freeSlot(getMockSlottingPalletRequest(), MockHttpHeaders.getHeaders());
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), "GLS-RCV-SMART-SLOT-409");
      assertEquals(
          e.getDescription(), "Resource exception from SMART-SLOTTING. Error Message = mock_error");
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testPrimeSlotCompatibilityForSplitPallet_HappyPathFlow() throws Exception {
    when(appConfig.getSlottingBaseUrl()).thenReturn("slottingBaseURL");
    String slottingResponse =
        ReceivingUtils.readClassPathResourceAsString(
            "splitPalletPrimeSlotCompatible_response.json");
    ResponseEntity<String> mockSuccessResponse =
        new ResponseEntity<String>(slottingResponse, HttpStatus.OK);

    when(retryableRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
        .thenReturn(mockSuccessResponse);

    SlottingPalletResponse slottingPalletResponse =
        slottingRestApiClient.getPrimeSlotForSplitPallet(
            getMockSlottingPalletRequestForSplitPallet(),
            getMockItemDataList(),
            MockHttpHeaders.getHeaders());

    assertNotNull(slottingPalletResponse);
    assertTrue(slottingPalletResponse.getLocations().size() > 0);

    verify(appConfig, times(1)).getSlottingBaseUrl();
    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testPrimeSlotCompatibilityForSplitPallet_ErrorPrimeSlotNotAvailable()
      throws Exception {
    when(appConfig.getSlottingBaseUrl()).thenReturn("slottingBaseURL");
    String slottingResponse =
        ReceivingUtils.readClassPathResourceAsString(
            "splitPalletPrimeSlotCompatible_error_noPrimeSlotFound.json");
    ResponseEntity<String> mockErrorResponse =
        new ResponseEntity<String>(slottingResponse, HttpStatus.OK);
    when(retryableRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
        .thenReturn(mockErrorResponse);

    try {
      slottingRestApiClient.getPrimeSlotForSplitPallet(
          getMockSlottingPalletRequestForSplitPallet(),
          getMockItemDataList(),
          MockHttpHeaders.getHeaders());
    } catch (ReceivingBadDataException e) {
      assertEquals("GLS-RCV-SMART-SLOT-PRIME-404", e.getErrorCode());
      assertEquals(
          "Resource exception from SMART-SLOTTING. Error Code = GLS-SMART-SLOTING-4040009, Error Message = Setup issue - Prime Slot is not present for the entered item",
          e.getDescription());
    }

    verify(appConfig, times(1)).getSlottingBaseUrl();
    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testPrimeSlotCompatibilityForSplitPallet_ErrorPrimeSlotNotCompatible()
      throws Exception {
    when(appConfig.getSlottingBaseUrl()).thenReturn("slottingBaseURL");
    String slottingResponse =
        ReceivingUtils.readClassPathResourceAsString(
            "splitPalletPrimeSlotCompatible_error_response.json");
    ResponseEntity<String> mockErrorResponse =
        new ResponseEntity<String>(slottingResponse, HttpStatus.OK);
    when(retryableRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
        .thenReturn(mockErrorResponse);

    try {
      slottingRestApiClient.getPrimeSlotForSplitPallet(
          getMockSlottingPalletRequestForSplitPallet(),
          getMockItemDataList(),
          MockHttpHeaders.getHeaders());
    } catch (ReceivingBadDataException e) {
      assertEquals("GLS-RCV-INSTR-SPLIT-PALLET-PRIMES-COMPATIBLE-SLOTTING-400", e.getErrorCode());
      assertEquals(
          "Resource exception from SMART-SLOTTING. Error Code = GLS-SMART-SLOTING-400097, Error Message = Items are not compatible for slotting",
          e.getDescription());
    }

    verify(appConfig, times(1)).getSlottingBaseUrl();
    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testPrimeSlotCompatibilityForSplitPallet_RestClientResponseException()
      throws Exception {
    when(appConfig.getSlottingBaseUrl()).thenReturn("slottingBaseURL");
    RestClientResponseException exception =
        new RestClientResponseException("mock_error", 409, "", null, null, null);
    doThrow(exception)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      slottingRestApiClient.freeSlot(getMockSlottingPalletRequest(), MockHttpHeaders.getHeaders());
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), "GLS-RCV-SMART-SLOT-409");
      assertEquals(
          e.getDescription(), "Resource exception from SMART-SLOTTING. Error Message = mock_error");
    }

    verify(appConfig, times(1)).getSlottingBaseUrl();
    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testSmartSlottingWithRdsPayLoadSuccess() throws Exception {
    String slottingRequest =
        ReceivingUtils.readClassPathResourceAsString("slottingRequestWithRdsContainers.json");
    String slottingResponse =
        ReceivingUtils.readClassPathResourceAsString("slottingResponseWithRdsContainers.json");
    HttpHeaders httpHeaders = getMockHeaderForSlotting();
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), eq("isRxSmartSlottingTestOnlyEnabled"));
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(slottingResponse, HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    SlottingPalletRequestWithRdsPayLoad slottingPalletRequestWithRdsPayLoad =
        gson.fromJson(slottingRequest, SlottingPalletRequestWithRdsPayLoad.class);
    SlottingPalletResponse slottingPalletResponse =
        slottingRestApiClient.getSlot(slottingPalletRequestWithRdsPayLoad, httpHeaders);

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    assertNotNull(slottingPalletResponse);
    assertEquals(
        slottingPalletResponse.getMessageId(), slottingPalletRequestWithRdsPayLoad.getMessageId());
    assertNotNull(slottingPalletResponse.getLocations());
    assertEquals(slottingPalletResponse.getLocations().get(0).getLocation(), "K7298");
    assertNotNull(((SlottingPalletResponseWithRdsResponse) slottingPalletResponse).getRds());
    assertEquals(
        ((SlottingPalletResponseWithRdsResponse) slottingPalletResponse)
            .getRds()
            .getReceived()
            .get(0)
            .getDestinations()
            .get(0)
            .getSlot(),
        "K7298");
    assertEquals(
        ((SlottingPalletResponseWithRdsResponse) slottingPalletResponse)
            .getRds()
            .getReceived()
            .get(0)
            .getLabelTrackingId(),
        "9785690481");
  }

  @Test
  public void testSmartSlottingWithRdsPayLoad_NimRdsThrowsError() throws Exception {
    String slottingRequest =
        ReceivingUtils.readClassPathResourceAsString("slottingRequestWithRdsContainers.json");
    String slottingResponse =
        ReceivingUtils.readClassPathResourceAsString("slottingResponseWithRdsError.json");
    HttpHeaders httpHeaders = getMockHeaderForSlotting();
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), eq("isRxSmartSlottingTestOnlyEnabled"));
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(slottingResponse, HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    SlottingPalletRequestWithRdsPayLoad slottingPalletRequestWithRdsPayLoad =
        gson.fromJson(slottingRequest, SlottingPalletRequestWithRdsPayLoad.class);

    try {
      slottingRestApiClient.getSlot(slottingPalletRequestWithRdsPayLoad, httpHeaders);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), "GLS-RCV-RDS-ERR-500");
      assertEquals(
          e.getDescription(),
          "No containers received in RDS for PO 5972408578 and PO Line 8; Error Message: No receiver found for manifest or PO");
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testSmartSlottingWithRdsPayLoad_NimRdsReturnsEmptyContainers() throws Exception {
    String slottingRequest =
        ReceivingUtils.readClassPathResourceAsString("slottingRequestWithRdsContainers.json");
    String slottingResponse =
        ReceivingUtils.readClassPathResourceAsString(
            "slottingResponseWithEmptyContainersFromRds.json");
    HttpHeaders httpHeaders = getMockHeaderForSlotting();
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), eq("isRxSmartSlottingTestOnlyEnabled"));
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(slottingResponse, HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    SlottingPalletRequestWithRdsPayLoad slottingPalletRequestWithRdsPayLoad =
        gson.fromJson(slottingRequest, SlottingPalletRequestWithRdsPayLoad.class);

    try {
      slottingRestApiClient.getSlot(slottingPalletRequestWithRdsPayLoad, httpHeaders);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), "GLS-RCV-RDS-SLT-500");
      assertEquals(
          e.getDescription(), "No containers received in RDS for PO 5972408578 and PO Line 8");
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testSmartSlottingWithRdsPayLoad_SlottingDoesNotReturnRdsResponse() throws Exception {
    String slottingRequest =
        ReceivingUtils.readClassPathResourceAsString("slottingRequestWithRdsContainers.json");
    String slottingResponse =
        ReceivingUtils.readClassPathResourceAsString("SlottingResponseWithNoRdsResponse.json");
    HttpHeaders httpHeaders = getMockHeaderForSlotting();
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), eq("isRxSmartSlottingTestOnlyEnabled"));
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(slottingResponse, HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    SlottingPalletRequestWithRdsPayLoad slottingPalletRequestWithRdsPayLoad =
        gson.fromJson(slottingRequest, SlottingPalletRequestWithRdsPayLoad.class);

    try {
      slottingRestApiClient.getSlot(slottingPalletRequestWithRdsPayLoad, httpHeaders);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), "GLS-RCV-SMART-SLOT-404");
      assertEquals(
          e.getDescription(),
          "Resource exception from SMART-SLOTTING. Error Code = GLS-SMRT-SLOTING-400, Error Message = High Watermark level is not defined for the item");
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testSmartSlottingWithRdsPayLoad_NimRdsReturnsErrorForSplitPalletReceiving()
      throws Exception {
    String slottingRequest =
        ReceivingUtils.readClassPathResourceAsString(
            "slottingRequestWithMultipleRdsContainers.json");
    String slottingResponse =
        ReceivingUtils.readClassPathResourceAsString("slottingRequestWithRdsContainers.json");
    HttpHeaders httpHeaders = getMockHeaderForSlotting();
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), eq("isRxSmartSlottingTestOnlyEnabled"));
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(slottingResponse, HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    SlottingPalletRequestWithRdsPayLoad slottingPalletRequestWithRdsPayLoad =
        gson.fromJson(slottingRequest, SlottingPalletRequestWithRdsPayLoad.class);

    try {
      slottingRestApiClient.getSlot(slottingPalletRequestWithRdsPayLoad, httpHeaders);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.NIM_RDS_MULTI_LABEL_GENERIC_ERROR);
      assertEquals(e.getDescription(), ReceivingConstants.NIM_RDS_MULTI_LABEL_GENERIC_ERROR);
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  private void testSmartSlottingPrimeSlotForMultipleItems_SlottingReturnsSuccessForAllItems() {
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), eq("isRxSmartSlottingTestOnlyEnabled"));
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(
            gson.toJson(MockSlottingUtils.getPrimeSlotForMultiItemsFromSmartSlotting()),
            HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    SlottingPalletRequest slottingPalletRequest =
        MockSlottingUtils.getMockSlottingPalletRequestForMultipleItems();
    HttpHeaders httpHeaders = getMockHeaderForSlotting();
    httpHeaders.set(
        ReceivingConstants.SLOTTING_FEATURE_TYPE, ReceivingConstants.SLOTTING_FIND_PRIME_SLOT);

    SlottingPalletResponse slottingPalletResponse =
        slottingRestApiClient.getSlot(slottingPalletRequest, httpHeaders);
    assertNotNull(slottingPalletResponse);
    assertEquals(slottingPalletResponse.getLocations().size(), 2);
    assertEquals(slottingPalletResponse.getLocations().get(0).getLocation(), "A0002");
    assertEquals(slottingPalletResponse.getLocations().get(1).getLocation(), "A0002");
    assertEquals(slottingPalletResponse.getLocations().get(0).getAsrsAlignment(), "SYMBP");
    assertEquals(slottingPalletResponse.getLocations().get(1).getAsrsAlignment(), "SYMBP");

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  private void testSmartSlottingPrimeSlotForMultipleItems_SlottingReturnsSuccessForPartialItems() {
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), eq("isRxSmartSlottingTestOnlyEnabled"));
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(
            gson.toJson(
                MockSlottingUtils.getPrimeSlotForMultiItemsFromSmartSlotting_PartialSuccess()),
            HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    SlottingPalletRequest slottingPalletRequest =
        MockSlottingUtils.getMockSlottingPalletRequestForMultipleItems();
    HttpHeaders httpHeaders = getMockHeaderForSlotting();
    httpHeaders.set(
        ReceivingConstants.SLOTTING_FEATURE_TYPE, ReceivingConstants.SLOTTING_FIND_PRIME_SLOT);

    SlottingPalletResponse slottingPalletResponse =
        slottingRestApiClient.getSlot(slottingPalletRequest, httpHeaders);
    assertNotNull(slottingPalletResponse);
    assertEquals(slottingPalletResponse.getLocations().size(), 2);
    assertEquals(slottingPalletResponse.getLocations().get(0).getLocation(), "A0002");
    assertEquals(slottingPalletResponse.getLocations().get(0).getAsrsAlignment(), "SYMBP");
    assertNull(slottingPalletResponse.getLocations().get(1).getLocation());
    assertNotNull(slottingPalletResponse.getLocations().get(1).getCode());
    assertNotNull(slottingPalletResponse.getLocations().get(1).getDesc());

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  private void
      testSmartSlottingPrimeSlotForMultipleItems_SlottingReturnsErrorResponseForAllItems() {
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), eq("isRxSmartSlottingTestOnlyEnabled"));
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(
            gson.toJson(MockSlottingUtils.getPrimeSlotForMultiItemsFromSmartSlotting_Error()),
            HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    SlottingPalletRequest slottingPalletRequest =
        MockSlottingUtils.getMockSlottingPalletRequestForMultipleItems();
    HttpHeaders httpHeaders = getMockHeaderForSlotting();
    httpHeaders.set(
        ReceivingConstants.SLOTTING_FEATURE_TYPE, ReceivingConstants.SLOTTING_FIND_PRIME_SLOT);

    SlottingPalletResponse slottingPalletResponse =
        slottingRestApiClient.getSlot(slottingPalletRequest, httpHeaders);
    assertNotNull(slottingPalletResponse);
    assertEquals(slottingPalletResponse.getLocations().size(), 2);
    assertNull(slottingPalletResponse.getLocations().get(1).getLocation());
    assertNotNull(slottingPalletResponse.getLocations().get(1).getCode());
    assertNotNull(slottingPalletResponse.getLocations().get(1).getDesc());
    assertNull(slottingPalletResponse.getLocations().get(1).getLocation());
    assertNotNull(slottingPalletResponse.getLocations().get(1).getCode());
    assertNotNull(slottingPalletResponse.getLocations().get(1).getDesc());

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testSmartSlottingWithGDCReturnSuccess() throws Exception {
    String slottingRequest =
        ReceivingUtils.readClassPathResourceAsString("slottingRequestWithGdcContainers.json");
    String slottingResponse =
        ReceivingUtils.readClassPathResourceAsString("slottingResponseWithGdcContainers.json");
    HttpHeaders httpHeaders = getMockHeaderForSlotting();
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), eq("isManualGdcEnabled"), anyBoolean());
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(slottingResponse, HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    SlottingPalletRequest slottingPalletRequest =
        gson.fromJson(slottingRequest, SlottingPalletRequest.class);
    SlottingPalletResponse slottingPalletResponse =
        slottingRestApiClient.getSlot(slottingPalletRequest, httpHeaders);

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    assertNotNull(slottingPalletResponse);
    assertEquals(slottingPalletResponse.getMessageId(), slottingPalletRequest.getMessageId());
    assertNotNull(slottingPalletResponse.getLocations());
    assertEquals(slottingPalletResponse.getLocations().get(0).getLocation(), "K7298");
  }

  @Test
  public void testSlottingForDASlotting_AtlasItems() throws Exception {
    String mockRequest =
        ReceivingUtils.readClassPathResourceAsString("slottingPalletRequestWithoutRdsRequest.json");
    String mockResponse =
        ReceivingUtils.readClassPathResourceAsString(
            "slottingResponseEntityWithoutRdsResponse.json");
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), eq("isRxSmartSlottingTestOnlyEnabled"));
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    HttpHeaders httpHeaders = getMockHeaderForSlotting();
    SlottingPalletRequest slottingPalletRequest =
        gson.fromJson(mockRequest, SlottingPalletRequest.class);
    SlottingPalletResponse slottingPalletResponse =
        slottingRestApiClient.getSlot(slottingPalletRequest, httpHeaders);

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    assertEquals(slottingPalletResponse.getMessageId(), slottingPalletRequest.getMessageId());
    assertEquals(slottingPalletResponse.getLocations().get(0).getLocation(), "L4007");
  }

  @Test
  public void testSlottingForDASlotting() throws Exception {
    String mockRequest =
        ReceivingUtils.readClassPathResourceAsString("slottingPalletRequestWithRdsRequest.json");
    String mockResponse =
        ReceivingUtils.readClassPathResourceAsString("slottingResponseEntityWithRdsResponse.json");
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), eq("isRxSmartSlottingTestOnlyEnabled"));
    ResponseEntity<String> mockResponseEntity =
        new ResponseEntity<String>(mockResponse, HttpStatus.OK);
    doReturn(mockResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    HttpHeaders httpHeaders = getMockHeaderForSlotting();
    SlottingPalletRequest slottingPalletRequest =
        gson.fromJson(mockRequest, SlottingPalletRequestWithRdsPayLoad.class);
    SlottingPalletResponse slottingPalletResponse =
        slottingRestApiClient.getSlot(slottingPalletRequest, httpHeaders);

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    assertEquals(slottingPalletResponse.getMessageId(), slottingPalletRequest.getMessageId());
    assertEquals(slottingPalletResponse.getLocations().get(0).getLocation(), "K7298");
  }

  @Test
  public void testSlottingForDASlotting_inactive_slot() throws Exception {
    String mockRequest =
        ReceivingUtils.readClassPathResourceAsString("slotting_manual_slot_mock_request.json");
    String mockErrorResponse =
        ReceivingUtils.readClassPathResourceAsString("slotting_inactive_slot_error_response.json");
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    ResponseEntity<String> mockErrorResponseEntity =
        new ResponseEntity<String>(mockErrorResponse, HttpStatus.OK);
    doReturn(mockErrorResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), eq("isRxSmartSlottingTestOnlyEnabled"));
    HttpHeaders httpHeaders = getMockHeaderForSlotting();
    SlottingPalletRequest slottingPalletRequest =
        gson.fromJson(mockRequest, SlottingPalletRequest.class);

    try {
      slottingRestApiClient.getSlot(slottingPalletRequest, httpHeaders);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.SLOTTING_INACTIVE_SLOT_ERROR_400);
      assertEquals(
          e.getDescription(),
          "Resource exception from SMART-SLOTTING. Error Code = GLS-SMART-SLOTING-4000044, Error Message = Entered slot is inactive");
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testSlottingForDASlotting_frozen_slot() throws Exception {
    String mockRequest =
        ReceivingUtils.readClassPathResourceAsString("slotting_manual_slot_mock_request.json");
    String mockErrorResponse =
        ReceivingUtils.readClassPathResourceAsString("slotting_frozen_slot_error_response.json");
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    ResponseEntity<String> mockErrorResponseEntity =
        new ResponseEntity<String>(mockErrorResponse, HttpStatus.OK);
    doReturn(mockErrorResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), eq("isRxSmartSlottingTestOnlyEnabled"));
    HttpHeaders httpHeaders = getMockHeaderForSlotting();
    SlottingPalletRequest slottingPalletRequest =
        gson.fromJson(mockRequest, SlottingPalletRequest.class);

    try {
      slottingRestApiClient.getSlot(slottingPalletRequest, httpHeaders);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.SLOTTING_FROZEN_SLOT_ERROR_ERROR_400);
      assertEquals(
          e.getDescription(),
          "Resource exception from SMART-SLOTTING. Error Code = GLS-SMART-SLOTING-4000043, Error Message = Entered slot is frozen");
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testSlottingForDASlotting_location_not_configured() throws Exception {
    String mockRequest =
        ReceivingUtils.readClassPathResourceAsString("slotting_manual_slot_mock_request.json");
    String mockErrorResponse =
        ReceivingUtils.readClassPathResourceAsString(
            "slotting_location_not_configured_error_response.json");
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    ResponseEntity<String> mockErrorResponseEntity =
        new ResponseEntity<String>(mockErrorResponse, HttpStatus.OK);
    doReturn(mockErrorResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), eq("isRxSmartSlottingTestOnlyEnabled"));
    HttpHeaders httpHeaders = getMockHeaderForSlotting();
    SlottingPalletRequest slottingPalletRequest =
        gson.fromJson(mockRequest, SlottingPalletRequest.class);

    try {
      slottingRestApiClient.getSlot(slottingPalletRequest, httpHeaders);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.SLOTTING_LOCATION_NOT_CONFIGURED_ERROR_400);
      assertEquals(
          e.getDescription(),
          "Resource exception from SMART-SLOTTING. Error Code = GLS-SMART-SLOTING-4000041, Error Message = Entered location for manual slotting is not configured");
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testSlottingForDASlotting_item_location_not_found() throws Exception {
    String mockRequest =
        ReceivingUtils.readClassPathResourceAsString("slotting_manual_slot_mock_request.json");
    String mockErrorResponse =
        ReceivingUtils.readClassPathResourceAsString(
            "slotting_item_location_not_found_error_response.json");
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    ResponseEntity<String> mockErrorResponseEntity =
        new ResponseEntity<String>(mockErrorResponse, HttpStatus.OK);
    doReturn(mockErrorResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), eq("isRxSmartSlottingTestOnlyEnabled"));
    HttpHeaders httpHeaders = getMockHeaderForSlotting();
    SlottingPalletRequest slottingPalletRequest =
        gson.fromJson(mockRequest, SlottingPalletRequest.class);

    try {
      slottingRestApiClient.getSlot(slottingPalletRequest, httpHeaders);
    } catch (ReceivingBadDataException e) {
      assertEquals(
          e.getErrorCode(), ExceptionCodes.SLOTTING_CONTAINER_ITEM_LOCATION_NOT_FOUND_ERROR_400);
      assertEquals(
          e.getDescription(),
          "Resource exception from SMART-SLOTTING. Error Code = GLS-SMART-SLOTING-4000039, Error Message = Container item details can not be empty");
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testSlottingForDASlotting_bulk_slot_delivery_not_found() throws Exception {
    String mockRequest =
        ReceivingUtils.readClassPathResourceAsString("slotting_manual_slot_mock_request.json");
    String mockErrorResponse =
        ReceivingUtils.readClassPathResourceAsString("slotting-bulk-slot-delivery-not-found.json");
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    ResponseEntity<String> mockErrorResponseEntity =
        new ResponseEntity<String>(mockErrorResponse, HttpStatus.OK);
    doReturn(mockErrorResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), eq("isRxSmartSlottingTestOnlyEnabled"));
    HttpHeaders httpHeaders = getMockHeaderForSlotting();
    SlottingPalletRequest slottingPalletRequest =
        gson.fromJson(mockRequest, SlottingPalletRequest.class);

    try {
      slottingRestApiClient.getSlot(slottingPalletRequest, httpHeaders);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.BULK_SLOT_DELIVERY_NOT_FOUND_409);
      assertEquals(
          e.getDescription(),
          "Resource exception from SMART-SLOTTING. Error Code = GLS-SMART-SLOTING-4090044, Error Message = Delivery Number can not be empty");
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testSlottingForDASlotting_bulk_slot_capacity_not_available() throws Exception {
    String mockRequest =
        ReceivingUtils.readClassPathResourceAsString("slotting_manual_slot_mock_request.json");
    String mockErrorResponse =
        ReceivingUtils.readClassPathResourceAsString(
            "slotting-bulk-slot-capacity-not-available.json");
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    ResponseEntity<String> mockErrorResponseEntity =
        new ResponseEntity<String>(mockErrorResponse, HttpStatus.OK);
    doReturn(mockErrorResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), eq("isRxSmartSlottingTestOnlyEnabled"));
    HttpHeaders httpHeaders = getMockHeaderForSlotting();
    SlottingPalletRequest slottingPalletRequest =
        gson.fromJson(mockRequest, SlottingPalletRequest.class);

    try {
      slottingRestApiClient.getSlot(slottingPalletRequest, httpHeaders);
    } catch (ReceivingBadDataException e) {
      assertEquals(
          e.getErrorCode(), ExceptionCodes.BULK_SLOT_CAPACITY_NOT_AVAILABLE_FOR_DELIVERY_404);
      assertEquals(
          e.getDescription(),
          "Resource exception from SMART-SLOTTING. Error Code = GLS-SMART-SLOTING-4040013, Error Message = Bulk slot has no capacity");
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testSlottingForSlotting_potype_DA_slot_mismatch() throws Exception {
    String mockRequest =
        ReceivingUtils.readClassPathResourceAsString("slotting_manual_slot_mock_request.json");
    String mockErrorResponse =
        ReceivingUtils.readClassPathResourceAsString(
            "slotting_po_type_sstk_mismatch_error_response.json");
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    ResponseEntity<String> mockErrorResponseEntity =
        new ResponseEntity<String>(mockErrorResponse, HttpStatus.OK);
    doReturn(mockErrorResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), eq("isRxSmartSlottingTestOnlyEnabled"));
    HttpHeaders httpHeaders = getMockHeaderForSlotting();
    SlottingPalletRequest slottingPalletRequest =
        gson.fromJson(mockRequest, SlottingPalletRequest.class);

    try {
      slottingRestApiClient.getSlot(slottingPalletRequest, httpHeaders);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.SLOT_PO_TYPE_MISMATCH_ERROR_400);
      assertEquals(
          e.getDescription(),
          "Resource exception from SMART-SLOTTING. Error Code = GLS-SMART-SLOTING-4000042, Error Message = Entered location is not configured to be a SSTK slot");
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testSlottingForSlotting_potype_SSTK_slot_mismatch() throws Exception {
    String mockRequest =
        ReceivingUtils.readClassPathResourceAsString("slotting_manual_slot_mock_request.json");
    String mockErrorResponse =
        ReceivingUtils.readClassPathResourceAsString(
            "slotting_po_type_da_mismatch_error_response.json");
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    ResponseEntity<String> mockErrorResponseEntity =
        new ResponseEntity<String>(mockErrorResponse, HttpStatus.OK);
    doReturn(mockErrorResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), eq("isRxSmartSlottingTestOnlyEnabled"));
    HttpHeaders httpHeaders = getMockHeaderForSlotting();
    SlottingPalletRequest slottingPalletRequest =
        gson.fromJson(mockRequest, SlottingPalletRequest.class);

    try {
      slottingRestApiClient.getSlot(slottingPalletRequest, httpHeaders);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.SLOT_PO_TYPE_MISMATCH_ERROR_400);
      assertEquals(
          e.getDescription(),
          "Resource exception from SMART-SLOTTING. Error Code = GLS-SMART-SLOTING-4000042, Error Message = Entered location is not configured to be a DA slot");
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testSlottingForDASlotting_prime_slot_not_found() throws Exception {
    String mockRequest =
        ReceivingUtils.readClassPathResourceAsString("slotting_manual_slot_mock_request.json");
    String mockErrorResponse =
        ReceivingUtils.readClassPathResourceAsString("slotting-prime-slot-not-found.json");
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    ResponseEntity<String> mockErrorResponseEntity =
        new ResponseEntity<String>(mockErrorResponse, HttpStatus.OK);
    doReturn(mockErrorResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), eq("isRxSmartSlottingTestOnlyEnabled"));
    HttpHeaders httpHeaders = getMockHeaderForSlotting();
    SlottingPalletRequest slottingPalletRequest =
        gson.fromJson(mockRequest, SlottingPalletRequest.class);

    try {
      slottingRestApiClient.getSlot(slottingPalletRequest, httpHeaders);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.PRIME_SLOT_NOT_FOUND_ERROR_IN_SMART_SLOTTING);
      assertEquals(
          e.getDescription(),
          "Resource exception from SMART-SLOTTING. Error Code = GLS-SMART-SLOTING-4040009, Error Message = Prime slot not found");
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testSlottingForDASlotting_slot_not_available_for_delivery() throws Exception {
    String mockRequest =
        ReceivingUtils.readClassPathResourceAsString("slotting_manual_slot_mock_request.json");
    String mockErrorResponse =
        ReceivingUtils.readClassPathResourceAsString("slot-not-available-for-delivery-error.json");
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    ResponseEntity<String> mockErrorResponseEntity =
        new ResponseEntity<String>(mockErrorResponse, HttpStatus.OK);
    doReturn(mockErrorResponseEntity)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), eq("isRxSmartSlottingTestOnlyEnabled"));
    HttpHeaders httpHeaders = getMockHeaderForSlotting();
    SlottingPalletRequest slottingPalletRequest =
        gson.fromJson(mockRequest, SlottingPalletRequest.class);

    try {
      slottingRestApiClient.getSlot(slottingPalletRequest, httpHeaders);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.BULK_SLOT_NOT_AVAILABLE_FOR_DELIVERY_409);
      assertEquals(
          e.getDescription(),
          "Resource exception from SMART-SLOTTING. Error Code = GLS-SMART-SLOTING-4090046, Error Message = Slot not available for delivery");
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  private SlottingPalletRequest getMockSlottingPalletRequest() {
    SlottingPalletRequest slottingPalletRequest = new SlottingPalletRequest();
    slottingPalletRequest.setMessageId("messageId");
    slottingPalletRequest.setDoorId("100");
    slottingPalletRequest.setContainerDetails(new ArrayList());
    return slottingPalletRequest;
  }

  private List<ItemData> getMockItemDataList() {
    List<ItemData> itemDataList = new ArrayList<>();
    ItemData itemData1 = new ItemData();
    itemData1.setAtlasConvertedItem(true);
    itemData1.setPrimeSlot("G3022");
    itemData1.setPackTypeCode("B");
    itemData1.setHandlingCode("C");
    ItemData itemData2 = new ItemData();
    itemData1.setAtlasConvertedItem(true);
    itemData1.setPrimeSlot("G3023");
    itemData1.setPackTypeCode("B");
    itemData1.setHandlingCode("C");
    ItemData itemData3 = new ItemData();
    itemData1.setAtlasConvertedItem(true);
    itemData1.setPrimeSlot("G3024");
    itemData1.setPackTypeCode("B");
    itemData1.setHandlingCode("C");
    itemDataList.add(itemData1);
    itemDataList.add(itemData2);
    itemDataList.add(itemData3);
    return itemDataList;
  }

  private SlottingPalletRequest getMockSlottingPalletRequestForSplitPallet() {
    SlottingPalletRequest slottingPalletRequest = new SlottingPalletRequest();
    slottingPalletRequest.setMessageId("messageId");
    slottingPalletRequest.setDoorId("100");
    SlottingContainerDetails slottingContainerDetails = new SlottingContainerDetails();
    List<SlottingContainerItemDetails> slottingContainerItemDetailsList = new ArrayList<>();
    SlottingContainerItemDetails slottingContainerItemDetails1 = new SlottingContainerItemDetails();
    slottingContainerItemDetails1.setItemNbr(567951354L);
    slottingContainerItemDetails1.setHandlingMthdCode("C");
    slottingContainerItemDetails1.setPackTypeCode("B");
    SlottingContainerItemDetails slottingContainerItemDetails2 = new SlottingContainerItemDetails();
    slottingContainerItemDetails1.setItemNbr(567951355L);
    slottingContainerItemDetails1.setHandlingMthdCode("C");
    slottingContainerItemDetails1.setPackTypeCode("B");
    SlottingContainerItemDetails slottingContainerItemDetails3 = new SlottingContainerItemDetails();
    slottingContainerItemDetails1.setItemNbr(567951356L);
    slottingContainerItemDetails1.setHandlingMthdCode("C");
    slottingContainerItemDetails1.setPackTypeCode("B");
    SlottingContainerItemDetails slottingContainerItemDetails4 = new SlottingContainerItemDetails();
    slottingContainerItemDetails1.setItemNbr(567951357L);
    slottingContainerItemDetails1.setHandlingMthdCode("C");
    slottingContainerItemDetails1.setPackTypeCode("B");
    slottingContainerItemDetailsList.add(slottingContainerItemDetails1);
    slottingContainerItemDetailsList.add(slottingContainerItemDetails2);
    slottingContainerItemDetailsList.add(slottingContainerItemDetails3);
    slottingContainerItemDetailsList.add(slottingContainerItemDetails4);
    slottingContainerDetails.setContainerItemsDetails(slottingContainerItemDetailsList);
    slottingPalletRequest.setContainerDetails(Collections.singletonList(slottingContainerDetails));
    return slottingPalletRequest;
  }

  @Test
  public void test_cancelPalletMoves_200() throws Exception {
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    doReturn(new ResponseEntity<String>(HttpStatus.OK))
        .when(retryableRestConnector)
        .exchange(anyString(), eq(HttpMethod.PATCH), any(HttpEntity.class), any(Class.class));
    HttpHeaders httpHeaders = getMockHeaderForSlotting();
    httpHeaders.set(ReceivingConstants.WM_USERID, "WMT-UserId");
    slottingRestApiClient.cancelPalletMoves("0603200000003434888", httpHeaders);

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void test_cancelPalletMoves_error_exception() throws Exception {
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), eq("isRxSmartSlottingTestOnlyEnabled"));
    doThrow(new ResourceAccessException("mock_error"))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      HttpHeaders httpHeaders = getMockHeaderForSlotting();
      httpHeaders.set(ReceivingConstants.WM_USERID, "WMT-UserId");
      slottingRestApiClient.cancelPalletMoves("0603200000003434888", httpHeaders);

    } catch (ReceivingBadDataException e) {
      assertEquals("GLS-RCV-SMART-SLOT-404", e.getErrorCode());
      assertEquals(
          "Resource exception from SMART-SLOTTING. Error Message = mock_error", e.getDescription());
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void test_cancelPalletMoves_error_RestClientResponseException() throws Exception {
    RestClientResponseException exception =
        new RestClientResponseException("mock_error", 409, "", null, null, null);
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), eq("isRxSmartSlottingTestOnlyEnabled"));
    doThrow(exception)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    try {
      HttpHeaders httpHeaders = getMockHeaderForSlotting();
      httpHeaders.set(ReceivingConstants.WM_USERID, "WMT-UserId");
      slottingRestApiClient.cancelPalletMoves("0603200000003434888", httpHeaders);

    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), "GLS-RCV-SMART-SLOT-409");
      assertEquals(
          e.getDescription(), "Resource exception from SMART-SLOTTING. Error Message = mock_error");
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void test_adjustMovesQty_200() throws Exception {
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    doReturn(new ResponseEntity<String>(HttpStatus.OK))
        .when(retryableRestConnector)
        .exchange(anyString(), eq(HttpMethod.PATCH), any(HttpEntity.class), any(Class.class));
    HttpHeaders httpHeaders = getMockHeaderForSlotting();
    httpHeaders.set(ReceivingConstants.WM_USERID, "WMT-UserId");
    slottingRestApiClient.adjustMovesQty(
        "0603200000003434888", new SlottingInstructionUpdateRequest(8), httpHeaders);

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void test_adjustMovesQty_error_exception() throws Exception {
    RestClientResponseException exception =
        new RestClientResponseException("mock_error", 409, "", null, null, null);
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), eq("isRxSmartSlottingTestOnlyEnabled"));
    doThrow(exception)
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      HttpHeaders httpHeaders = getMockHeaderForSlotting();
      httpHeaders.set(ReceivingConstants.WM_USERID, "WMT-UserId");
      slottingRestApiClient.adjustMovesQty(
          "0603200000003434888", new SlottingInstructionUpdateRequest(8), httpHeaders);

    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), "GLS-RCV-SMART-SLOT-409");
      assertEquals(
          e.getDescription(), "Resource exception from SMART-SLOTTING. Error Message = mock_error");
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void test_adjustMovesQty_error_RestClientResponseException() throws Exception {
    doReturn("https://smart-slotting-endgame-dev.prod.us.walmart.net")
        .when(appConfig)
        .getSlottingBaseUrl();
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), eq("isRxSmartSlottingTestOnlyEnabled"));
    doThrow(new ResourceAccessException("mock_error"))
        .when(retryableRestConnector)
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    try {
      HttpHeaders httpHeaders = getMockHeaderForSlotting();
      httpHeaders.set(ReceivingConstants.WM_USERID, "WMT-UserId");
      slottingRestApiClient.adjustMovesQty(
          "0603200000003434888", new SlottingInstructionUpdateRequest(8), httpHeaders);

    } catch (ReceivingBadDataException e) {
      assertEquals("GLS-RCV-SMART-SLOT-404", e.getErrorCode());
      assertEquals(
          "Resource exception from SMART-SLOTTING. Error Message = mock_error", e.getDescription());
    }

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }
}
