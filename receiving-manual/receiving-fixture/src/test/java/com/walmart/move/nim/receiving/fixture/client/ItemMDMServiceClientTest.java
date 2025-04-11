package com.walmart.move.nim.receiving.fixture.client;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.rest.SimpleRestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.ItemCatalogUpdateRequest;
import com.walmart.move.nim.receiving.core.retry.RetryableRestConnector;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.data.MockItemMdmData;
import com.walmart.move.nim.receiving.fixture.config.FixtureManagedConfig;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ItemMDMServiceClientTest extends ReceivingTestBase {
  @Mock private RetryableRestConnector retryableRestConnector;
  @Mock private SimpleRestConnector simpleRestConnector;
  @Mock private AppConfig appConfig;
  @Mock private FixtureManagedConfig fixtureManagedConfig;
  @InjectMocks private ItemMDMServiceClient itemMDMServiceClient;
  private Gson gson = new Gson();
  private Map<String, Object> itemMdmMockData = null;
  private Set<Long> itemSet = null;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(itemMDMServiceClient, "gson", gson);
    ReflectionTestUtils.setField(itemMDMServiceClient, "appConfig", appConfig);
    ReflectionTestUtils.setField(itemMDMServiceClient, "simpleRestConnector", simpleRestConnector);
    ReflectionTestUtils.setField(
        itemMDMServiceClient, "retryableRestConnector", retryableRestConnector);
    itemMdmMockData =
        gson.fromJson(
            MockItemMdmData.getMockItemMdmData(
                "../../receiving-test/src/main/resources/json/item_details_92.json"),
            Map.class);
    itemSet = new HashSet<>();
    itemSet.add(561298341L);
  }

  @BeforeMethod
  public void resetMocks() {
    reset(retryableRestConnector);
    reset(simpleRestConnector);
    reset(appConfig);
  }

  @Test
  public void testRetrieveRfcItemDetails_WithRetry() {
    when(appConfig.getItemBatchSize()).thenReturn(2);
    when(fixtureManagedConfig.getRfcMdmAuthKey()).thenReturn("a1-b2-c3-d4");
    when(appConfig.getItemMDMBaseUrl()).thenReturn("http://localhost:8080");
    when(retryableRestConnector.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
        .thenReturn(new ResponseEntity<>(itemMdmMockData, HttpStatus.OK));
    itemMDMServiceClient.retrieveItemDetails(itemSet, MockHttpHeaders.getHeaders(), Boolean.TRUE);
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class));
  }

  @Test
  public void testRetrieveRfcItemDetailsBatch() {
    when(appConfig.getItemBatchSize()).thenReturn(2);
    when(fixtureManagedConfig.getRfcMdmAuthKey()).thenReturn("a1-b2-c3-d4");
    when(appConfig.getItemMDMBaseUrl()).thenReturn("http://localhost:8080");
    when(retryableRestConnector.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
        .thenReturn(new ResponseEntity<>(itemMdmMockData, HttpStatus.OK));

    Set<Long> itemNumbers = new HashSet<>(itemSet);
    itemNumbers.add(12345678L);
    itemNumbers.add(22345678L);
    itemNumbers.add(32345678L);
    itemNumbers.add(42345678L);
    itemNumbers.add(52345678L);
    itemMDMServiceClient.retrieveItemDetails(
        itemNumbers, MockHttpHeaders.getHeaders(), Boolean.TRUE);
    // 2 is the batch size . Hence , it is devided by 2
    verify(retryableRestConnector, times(itemNumbers.size() / 2))
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class));
  }

  @Test
  public void testRetrieveRfcItemDetails_WithoutRetry() {
    when(appConfig.getItemBatchSize()).thenReturn(2);
    when(fixtureManagedConfig.getRfcMdmAuthKey()).thenReturn("a1-b2-c3-d4");
    when(appConfig.getItemMDMBaseUrl()).thenReturn("http://localhost:8080");
    when(simpleRestConnector.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
        .thenReturn(new ResponseEntity<>(itemMdmMockData, HttpStatus.OK));
    itemMDMServiceClient.retrieveItemDetails(itemSet, MockHttpHeaders.getHeaders(), Boolean.FALSE);
    verify(simpleRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Item mdm service is down.*")
  public void testRetrieveRfcItemDetails_RestClientResponseException() {
    when(appConfig.getItemBatchSize()).thenReturn(2);
    when(fixtureManagedConfig.getRfcMdmAuthKey()).thenReturn("a1-b2-c3-d4");
    when(appConfig.getItemMDMBaseUrl()).thenReturn("http://localhost:8080");
    when(retryableRestConnector.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
        .thenThrow(
            new RestClientResponseException(
                "Error",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                null,
                "Error".getBytes(),
                null));
    itemMDMServiceClient.retrieveItemDetails(itemSet, MockHttpHeaders.getHeaders(), Boolean.TRUE);
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Item mdm service is down.*")
  public void testRetrieveRfcItemDetails_ResourceAccessException() {
    when(appConfig.getItemBatchSize()).thenReturn(2);
    when(fixtureManagedConfig.getRfcMdmAuthKey()).thenReturn("a1-b2-c3-d4");
    when(appConfig.getItemMDMBaseUrl()).thenReturn("http://localhost:8080");
    when(retryableRestConnector.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
        .thenThrow(new ResourceAccessException("Error"));
    itemMDMServiceClient.retrieveItemDetails(itemSet, MockHttpHeaders.getHeaders(), Boolean.TRUE);
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Item mdm service is down.*")
  public void testRetrieveRfcItemDetails_EmptyResponse() {
    when(appConfig.getItemBatchSize()).thenReturn(2);
    when(fixtureManagedConfig.getRfcMdmAuthKey()).thenReturn("a1-b2-c3-d4");
    when(appConfig.getItemMDMBaseUrl()).thenReturn("http://localhost:8080");
    when(retryableRestConnector.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
        .thenReturn(null);
    itemMDMServiceClient.retrieveItemDetails(itemSet, MockHttpHeaders.getHeaders(), Boolean.TRUE);
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Item mdm service is down.*")
  public void testRetrieveRfcItemDetails_EmptyResponseBody() {
    when(appConfig.getItemBatchSize()).thenReturn(2);
    when(fixtureManagedConfig.getRfcMdmAuthKey()).thenReturn("a1-b2-c3-d4");
    when(appConfig.getItemMDMBaseUrl()).thenReturn("http://localhost:8080");
    when(retryableRestConnector.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
        .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));
    itemMDMServiceClient.retrieveItemDetails(itemSet, MockHttpHeaders.getHeaders(), Boolean.TRUE);
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class));
  }

  private ItemCatalogUpdateRequest getItemCatalogUpdateRequest() {
    ItemCatalogUpdateRequest itemCatalogUpdateRequest = new ItemCatalogUpdateRequest();
    itemCatalogUpdateRequest.setItemNumber(12344L);
    itemCatalogUpdateRequest.setNewItemUPC("UPC1");
    return itemCatalogUpdateRequest;
  }
}
