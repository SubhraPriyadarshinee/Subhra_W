package com.walmart.move.nim.receiving.core.client.itemconfig;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.APP_NAME_VALUE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ATLAS_COMPLETE_MIGRATED_DC_LIST;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.CONTENT_TYPE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.CORRELATION_ID_HEADER_KEY;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ENTIRE_DC_ATLAS_CONVERTED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ITEM_CONFIG_SERVICE_NO_RETRY_ENABLED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ONE_ATLAS_CONVERTED_ITEM;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.REQUEST_ORIGINATOR;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.TENENT_COUNTRY_CODE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.TENENT_FACLITYNUM;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.USER_ID_HEADER_KEY;
import static java.lang.Long.valueOf;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.itemconfig.model.ItemConfigDetails;
import com.walmart.move.nim.receiving.core.client.itemconfig.model.ItemConfigRequest;
import com.walmart.move.nim.receiving.core.client.itemconfig.model.ItemConfigResponse;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.rest.SimpleRestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.retry.RetryableRestConnector;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.collections4.CollectionUtils;
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
import org.springframework.web.client.RestClientResponseException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ItemConfigApiClientTest {

  @Mock private AppConfig appConfig;
  @Mock private RetryableRestConnector retryableRestConnector;
  @Mock private SimpleRestConnector simpleRestConnector;
  @Mock private TenantSpecificConfigReader configUtils;
  @InjectMocks private ItemConfigApiClient itemConfigApiClient;

  private Gson gson;
  private final String url = "http://localhost:8080";
  private final String itemNumber = "1234567";
  private final String itemDesc = "test item";
  private final String createTs = "2021-07-23T03:48:27.133Z";

  @BeforeMethod
  public void setUp() throws Exception {
    gson = new Gson();
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(itemConfigApiClient, "gson", gson);
    TenantContext.setFacilityNum(6085);
    TenantContext.setFacilityCountryCode("US");
  }

  @AfterMethod
  public void tearDown() throws Exception {
    reset(appConfig, retryableRestConnector, simpleRestConnector);
  }

  @Test
  public void testGetAtlasConvertedItemsHappyPath() throws Exception {
    when(appConfig.getItemConfigBaseUrl()).thenReturn(url);
    when(retryableRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
        .thenReturn(mockSuccessResponse());
    List<ItemConfigDetails> itemConfigDetails =
        itemConfigApiClient.searchAtlasConvertedItems(
            Collections.singleton(Long.getLong(itemNumber)), new HttpHeaders());
    assertNotNull(itemConfigDetails);
    assertEquals(itemConfigDetails.get(0).getItem(), itemNumber);
    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    verify(simpleRestConnector, times(0))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testGetAtlasConvertedPartialAtlasConvertedItem() throws Exception {
    when(appConfig.getItemConfigBaseUrl()).thenReturn(url);
    when(retryableRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
        .thenReturn(mockPartialResponse());
    Set<Long> itemRequest = new HashSet<>(Arrays.asList(12345L, 45678L));
    List<ItemConfigDetails> itemConfigDetails =
        itemConfigApiClient.searchAtlasConvertedItems(itemRequest, new HttpHeaders());
    assertTrue(CollectionUtils.isNotEmpty(itemConfigDetails));
    assertEquals(itemConfigDetails.get(0).getItem(), itemNumber);
    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    verify(simpleRestConnector, times(0))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testGetAtlasConvertedItemsNotFound() throws Exception {
    when(appConfig.getItemConfigBaseUrl()).thenReturn(url);
    when(retryableRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
        .thenThrow(mockRestClientException(HttpStatus.NOT_FOUND));
    Set<Long> itemRequest = new HashSet<>(Arrays.asList(12345L, 45678L));
    List<ItemConfigDetails> itemConfigDetails =
        itemConfigApiClient.searchAtlasConvertedItems(itemRequest, new HttpHeaders());
    assertTrue(CollectionUtils.isEmpty(itemConfigDetails));
    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    verify(simpleRestConnector, times(0))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test(expectedExceptions = ItemConfigRestApiClientException.class)
  public void testGetAtlasConvertedItemsBadRequestServiceNotAvailable() throws Exception {
    when(appConfig.getItemConfigBaseUrl()).thenReturn(url);
    when(retryableRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
        .thenThrow(mockRestClientException(HttpStatus.SERVICE_UNAVAILABLE));
    Set<Long> itemRequest = new HashSet<>(Arrays.asList(12345L, 45678L));
    List<ItemConfigDetails> itemConfigDetails =
        itemConfigApiClient.searchAtlasConvertedItems(itemRequest, new HttpHeaders());
    assertTrue(CollectionUtils.isEmpty(itemConfigDetails));

    verify(retryableRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    verify(simpleRestConnector, times(0))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  private String mockItemConfigResponse() {
    ItemConfigResponse itemConfigResponse =
        ItemConfigResponse.builder()
            .items(
                Collections.singletonList(
                    ItemConfigDetails.builder()
                        .createdDateTime(createTs)
                        .desc(itemDesc)
                        .item(itemNumber)
                        .build()))
            .build();
    return gson.toJson(itemConfigResponse);
  }

  private String mockItemConfigResponseNonPresentItem() {
    ItemConfigResponse itemConfigResponse =
        ItemConfigResponse.builder()
            .items(
                Collections.singletonList(
                    ItemConfigDetails.builder()
                        .createdDateTime(createTs)
                        .desc(itemDesc)
                        .item("5678912")
                        .build()))
            .build();
    return gson.toJson(itemConfigResponse);
  }

  public ResponseEntity<String> mockSuccessResponse() {
    return new ResponseEntity<String>(mockItemConfigResponse(), HttpStatus.OK);
  }

  private ResponseEntity<String> mockPartialResponse() {
    return new ResponseEntity<String>(mockItemConfigResponse(), HttpStatus.PARTIAL_CONTENT);
  }

  public ResponseEntity<String> mockSuccessResponseForNonMatching() {
    return new ResponseEntity<String>(mockItemConfigResponseNonPresentItem(), HttpStatus.OK);
  }

  private RestClientResponseException mockRestClientException(HttpStatus httpStatus) {
    return new RestClientResponseException(
        "Some error.", httpStatus.value(), "", null, "".getBytes(), StandardCharsets.UTF_8);
  }

  private RestClientResponseException mockRestClientExceptionForNotFound(HttpStatus httpStatus) {
    return new RestClientResponseException(
        "Some error.", httpStatus.value(), "", null, "NO_ITEMS".getBytes(), StandardCharsets.UTF_8);
  }

  @Test
  public void testGetAtlasConvertedItemsHappyPath_NoRetry() throws Exception {
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(
            getFacilityNum().toString(), ITEM_CONFIG_SERVICE_NO_RETRY_ENABLED, false);
    when(appConfig.getItemConfigBaseUrl()).thenReturn(url);
    when(simpleRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
        .thenReturn(mockSuccessResponse());

    // execute
    List<ItemConfigDetails> itemConfigDetails =
        itemConfigApiClient.searchAtlasConvertedItems(
            Collections.singleton(Long.getLong(itemNumber)), new HttpHeaders());
    // assert
    assertNotNull(itemConfigDetails);
    assertEquals(itemConfigDetails.get(0).getItem(), itemNumber);
    verify(simpleRestConnector, atLeastOnce())
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));

    verify(retryableRestConnector, times(0))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testGetAtlasConvertedItemsHappyPath_EntireSiteConverted() throws Exception {
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(
            getFacilityNum().toString(), ITEM_CONFIG_SERVICE_NO_RETRY_ENABLED, false);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(getFacilityNum().toString(), ENTIRE_DC_ATLAS_CONVERTED, false);
    when(appConfig.getItemConfigBaseUrl()).thenReturn(url);
    when(simpleRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
        .thenReturn(mockSuccessResponse());

    // execute
    Set<Long> itemNumbers = new HashSet<>();
    itemNumbers.add(valueOf(itemNumber));
    List<ItemConfigDetails> itemConfigDetails =
        itemConfigApiClient.searchAtlasConvertedItems(itemNumbers, new HttpHeaders());
    // assert
    assertNotNull(itemConfigDetails);
    assertEquals(itemConfigDetails.get(0).getItem(), itemNumber);
    verify(simpleRestConnector, times(0))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    verify(retryableRestConnector, times(0))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testIsOneAtlasConverted() throws ReceivingException {
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(
            getFacilityNum().toString(), ITEM_CONFIG_SERVICE_NO_RETRY_ENABLED, false);
    when(appConfig.getItemConfigBaseUrl()).thenReturn(url);
    when(simpleRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
        .thenReturn(mockSuccessResponse());

    final boolean oneAtlasConverted =
        itemConfigApiClient.isAtlasConvertedItem(1234567L, new HttpHeaders());
    assertTrue(oneAtlasConverted);
    verify(simpleRestConnector, times(1))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testIsOneAtlasConverted_Entire_Dc_Converted() throws ReceivingException {
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(getFacilityNum().toString(), ENTIRE_DC_ATLAS_CONVERTED, false);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(
            getFacilityNum().toString(), ITEM_CONFIG_SERVICE_NO_RETRY_ENABLED, false);
    when(appConfig.getItemConfigBaseUrl()).thenReturn(url);
    when(simpleRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
        .thenReturn(mockSuccessResponse());

    final boolean oneAtlasConverted =
        itemConfigApiClient.isAtlasConvertedItem(1234567L, new HttpHeaders());
    assertTrue(oneAtlasConverted);
    verify(simpleRestConnector, times(0))
        .exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
  }

  @Test
  public void testGetAtlasItemState_OneAtlasConvertedItem() throws ReceivingException {
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(
            getFacilityNum().toString(), ITEM_CONFIG_SERVICE_NO_RETRY_ENABLED, false);
    when(appConfig.getItemConfigBaseUrl()).thenReturn(url);
    when(simpleRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
        .thenReturn(mockSuccessResponse());
    final StringBuilder currentItemStateSb = new StringBuilder("");
    final String atlasItemState =
        itemConfigApiClient.getOneAtlasState(true, currentItemStateSb, 1234567L, new HttpHeaders());
    assertTrue(ONE_ATLAS_CONVERTED_ITEM.equals(atlasItemState));
  }

  /**
   * Minimum headers required for itemconfig service are 4, as below:
   *
   * <pre>
   * curl --location --request POST 'https://item-config.stg.us.walmart.net/api/item/search' \
   * --header 'content-type: application/json' \
   * --header 'facilitycountrycode: US' \
   * --header 'facilitynum: 6085' \
   * --header 'wmt-correlationid: 64633e6c-d78f-4f0e-9a3f-9532e1d9ce2b' \
   * --data-raw '{
   * "data": [
   * 555394860
   * ]
   * }'
   * </pre>
   *
   * @throws ReceivingException
   */
  @Test
  public void test_validatingMandatoryHeaders() throws ReceivingException {
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(
            getFacilityNum().toString(), ITEM_CONFIG_SERVICE_NO_RETRY_ENABLED, false);
    when(appConfig.getItemConfigBaseUrl()).thenReturn(url);
    when(simpleRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
        .thenReturn(mockSuccessResponse());
    final StringBuilder currentItemStateSb = new StringBuilder("");

    ArgumentCaptor<HttpEntity> requestEntityArgumentCaptor =
        ArgumentCaptor.forClass(HttpEntity.class);
    ArgumentCaptor<String> urlCaptorArgumentCaptor = ArgumentCaptor.forClass(String.class);

    final HttpHeaders httpHeaders = new HttpHeaders();
    final String inventoryEventHeaderUserInDefect = "n0k0324";
    httpHeaders.add("WMT-UserId", inventoryEventHeaderUserInDefect);
    final String atlasItemState =
        itemConfigApiClient.getOneAtlasState(true, currentItemStateSb, 1234567L, httpHeaders);

    verify(simpleRestConnector, times(1))
        .exchange(
            urlCaptorArgumentCaptor.capture(),
            eq(HttpMethod.POST),
            requestEntityArgumentCaptor.capture(),
            eq(String.class));
    HttpEntity<String> requestEntity = requestEntityArgumentCaptor.getValue();
    ItemConfigRequest itemConfigRequest =
        gson.fromJson(requestEntity.getBody(), ItemConfigRequest.class);
    assertNotNull(itemConfigRequest);
    assertTrue(itemConfigRequest.toString().contains(itemNumber));

    // mandatory headers
    HttpHeaders headers = requestEntity.getHeaders();
    assertEquals("application/json", headers.getFirst(CONTENT_TYPE));
    assertEquals("US", headers.getFirst(TENENT_COUNTRY_CODE));
    assertEquals("6085", headers.getFirst(TENENT_FACLITYNUM));
    assertNotNull(headers.getFirst(CORRELATION_ID_HEADER_KEY));
    // optional headers
    assertEquals(headers.getFirst(REQUEST_ORIGINATOR), APP_NAME_VALUE);
    assertEquals(headers.getFirst(USER_ID_HEADER_KEY), inventoryEventHeaderUserInDefect);

    assertTrue(ONE_ATLAS_CONVERTED_ITEM.equals(atlasItemState));
  }

  @Test
  public void testGetAtlasItemState_NOT_OneAtlasConvertedItem() throws ReceivingException {
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(
            getFacilityNum().toString(), ITEM_CONFIG_SERVICE_NO_RETRY_ENABLED, false);
    when(appConfig.getItemConfigBaseUrl()).thenReturn(url);
    when(simpleRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
        .thenReturn(mockSuccessResponse());
    final StringBuilder currentItemStateSb = new StringBuilder("");
    final String atlasItemState =
        itemConfigApiClient.getOneAtlasState(true, currentItemStateSb, 00000L, new HttpHeaders());
    assertFalse(ONE_ATLAS_CONVERTED_ITEM.equals(atlasItemState));
  }

  @Test
  public void testGetAtlasItemState_NOT_OneAtlas() throws ReceivingException {
    final StringBuilder currentItemStateSb = new StringBuilder("");
    final String atlasItemState =
        itemConfigApiClient.getOneAtlasState(false, currentItemStateSb, 00000L, new HttpHeaders());
    assertTrue("".equals(atlasItemState));
  }

  @Test
  public void test_isOneAtlasItemConverted_isOneAtlas_false() throws ReceivingException {
    assertFalse(
        itemConfigApiClient.isOneAtlasConvertedItem(false, null, 00000L, new HttpHeaders()));
    assertFalse(
        itemConfigApiClient.isOneAtlasConvertedItem(
            false, new StringBuilder(""), 00000L, new HttpHeaders()));
  }

  @Test
  public void test_isOneAtlasItemConverted_isOneAtlasNotConverted_NullEmpty()
      throws ReceivingException {
    assertFalse(itemConfigApiClient.isOneAtlasConvertedItem(true, null, 00000L, new HttpHeaders()));
    assertFalse(
        itemConfigApiClient.isOneAtlasConvertedItem(
            true, new StringBuilder(""), 00000L, new HttpHeaders()));
  }

  @Test
  public void test_isOneAtlasItemConverted() throws ReceivingException {
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(
            getFacilityNum().toString(), ITEM_CONFIG_SERVICE_NO_RETRY_ENABLED, false);
    when(appConfig.getItemConfigBaseUrl()).thenReturn(url);
    when(simpleRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
        .thenReturn(mockSuccessResponse());
    assertTrue(
        itemConfigApiClient.isOneAtlasConvertedItem(
            true, new StringBuilder(""), 1234567L, new HttpHeaders()));
  }

  @Test
  public void test_isOneAtlasItemNotConverted() throws ReceivingException {
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(
            getFacilityNum().toString(), ITEM_CONFIG_SERVICE_NO_RETRY_ENABLED, false);
    when(appConfig.getItemConfigBaseUrl()).thenReturn(url);
    when(simpleRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
        .thenReturn(mockSuccessResponse());
    assertTrue(
        itemConfigApiClient.isOneAtlasNotConvertedItem(
            true, new StringBuilder(""), 9999L, new HttpHeaders()));
  }

  @Test
  public void tesAddItem() throws Exception {
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag(getFacilityNum().toString(), ENTIRE_DC_ATLAS_CONVERTED, false);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(
            getFacilityNum().toString(), ATLAS_COMPLETE_MIGRATED_DC_LIST, false);
    when(appConfig.getItemConfigBaseUrl()).thenReturn(url);

    when(retryableRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
        .thenReturn(mockSuccessResponse());
    Set<Long> itemNumbers = new HashSet<>();
    itemNumbers.add(valueOf(itemNumber));
    List<ItemConfigDetails> itemConfigDetails =
        itemConfigApiClient.searchAtlasConvertedItems(itemNumbers, new HttpHeaders());
    assertNotNull(itemConfigDetails);
  }

  @Test
  public void tesAddItemForNonMatchingItem() throws Exception {
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag(getFacilityNum().toString(), ENTIRE_DC_ATLAS_CONVERTED, false);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(
            getFacilityNum().toString(), ATLAS_COMPLETE_MIGRATED_DC_LIST, false);
    when(appConfig.getItemConfigBaseUrl()).thenReturn(url);

    when(retryableRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
        .thenReturn(mockSuccessResponseForNonMatching());
    Set<Long> itemNumbers = new HashSet<>();
    itemNumbers.add(valueOf(itemNumber));
    List<ItemConfigDetails> itemConfigDetails =
        itemConfigApiClient.searchAtlasConvertedItems(itemNumbers, new HttpHeaders());
    assertNotNull(itemConfigDetails);
  }

  @Test
  public void tesAddItemForNonPresentItem() throws Exception {
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag(getFacilityNum().toString(), ENTIRE_DC_ATLAS_CONVERTED, false);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(
            getFacilityNum().toString(), ATLAS_COMPLETE_MIGRATED_DC_LIST, false);
    when(appConfig.getItemConfigBaseUrl()).thenReturn(url);

    when(retryableRestConnector.exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
        .thenThrow(mockRestClientExceptionForNotFound(HttpStatus.NOT_FOUND));
    Set<Long> itemNumbers = new HashSet<>();
    itemNumbers.add(valueOf(itemNumber));
    List<ItemConfigDetails> itemConfigDetails =
        itemConfigApiClient.searchAtlasConvertedItems(itemNumbers, new HttpHeaders());
    assertNotNull(itemConfigDetails);
  }
}
