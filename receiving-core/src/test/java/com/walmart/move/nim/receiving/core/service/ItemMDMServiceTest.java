package com.walmart.move.nim.receiving.core.service;

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
import java.nio.charset.Charset;
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

public class ItemMDMServiceTest extends ReceivingTestBase {
  @Mock private RetryableRestConnector retryableRestConnector;
  @Mock private SimpleRestConnector simpleRestConnector;
  @Mock private AppConfig appConfig;
  @InjectMocks private ItemMDMService itemMDMService;
  private Gson gson = new Gson();
  private Map<String, Object> itemMdmMockData = null;
  private Set<Long> itemSet = null;
  private static final String WM = "WM";

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(itemMDMService, "gson", gson);
    ReflectionTestUtils.setField(itemMDMService, "appConfig", appConfig);
    ReflectionTestUtils.setField(itemMDMService, "simpleRestConnector", simpleRestConnector);
    ReflectionTestUtils.setField(itemMDMService, "retryableRestConnector", retryableRestConnector);
    itemMdmMockData =
        gson.fromJson(
            MockItemMdmData.getMockItemMdmData(
                "../receiving-test/src/main/resources/json/item_mdm_response_561298341.json"),
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
  public void testRetrieveItemDetails_WithRetry() {
    when(appConfig.getItemBatchSize()).thenReturn(2);
    when(appConfig.getMdmAuthKey()).thenReturn("a1-b2-c3-d4");
    when(appConfig.getItemMDMBaseUrl()).thenReturn("http://localhost:8080");
    when(retryableRestConnector.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
        .thenReturn(new ResponseEntity<>(itemMdmMockData, HttpStatus.OK));
    itemMDMService.retrieveItemDetails(
        itemSet, MockHttpHeaders.getHeaders(), WM, Boolean.TRUE, Boolean.FALSE);
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class));
  }

  @Test
  public void testRetrieveItemDetailsBatch() {
    when(appConfig.getItemBatchSize()).thenReturn(2);
    when(appConfig.getMdmAuthKey()).thenReturn("a1-b2-c3-d4");
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
    itemMDMService.retrieveItemDetails(
        itemNumbers, MockHttpHeaders.getHeaders(), WM, Boolean.TRUE, Boolean.FALSE);
    // 2 is the batch size . Hence , it is devided by 2
    verify(retryableRestConnector, times(itemNumbers.size() / 2))
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class));
  }

  @Test
  public void testRetrieveItemDetails_WithoutRetry() {
    when(appConfig.getItemBatchSize()).thenReturn(2);
    when(appConfig.getMdmAuthKey()).thenReturn("a1-b2-c3-d4");
    when(appConfig.getItemMDMBaseUrl()).thenReturn("http://localhost:8080");
    when(simpleRestConnector.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
        .thenReturn(new ResponseEntity<>(itemMdmMockData, HttpStatus.OK));
    itemMDMService.retrieveItemDetails(
        itemSet, MockHttpHeaders.getHeaders(), WM, Boolean.FALSE, Boolean.FALSE);
    verify(simpleRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Item mdm service is down.*")
  public void testRetrieveItemDetails_RestClientResponseException() {
    when(appConfig.getItemBatchSize()).thenReturn(2);
    when(appConfig.getMdmAuthKey()).thenReturn("a1-b2-c3-d4");
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
    itemMDMService.retrieveItemDetails(
        itemSet, MockHttpHeaders.getHeaders(), WM, Boolean.TRUE, Boolean.FALSE);
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Item mdm service is down.*")
  public void testRetrieveItemDetails_ResourceAccessException() {
    when(appConfig.getItemBatchSize()).thenReturn(2);
    when(appConfig.getMdmAuthKey()).thenReturn("a1-b2-c3-d4");
    when(appConfig.getItemMDMBaseUrl()).thenReturn("http://localhost:8080");
    when(retryableRestConnector.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
        .thenThrow(new ResourceAccessException("Error"));
    itemMDMService.retrieveItemDetails(
        itemSet, MockHttpHeaders.getHeaders(), WM, Boolean.TRUE, Boolean.FALSE);
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Item mdm service is down.*")
  public void testRetrieveItemDetails_EmptyResponse() {
    when(appConfig.getItemBatchSize()).thenReturn(2);
    when(appConfig.getMdmAuthKey()).thenReturn("a1-b2-c3-d4");
    when(appConfig.getItemMDMBaseUrl()).thenReturn("http://localhost:8080");
    when(retryableRestConnector.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
        .thenReturn(null);
    itemMDMService.retrieveItemDetails(
        itemSet, MockHttpHeaders.getHeaders(), WM, Boolean.TRUE, Boolean.FALSE);
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Item mdm service is down.*")
  public void testRetrieveItemDetails_EmptyResponseBody() {
    when(appConfig.getItemBatchSize()).thenReturn(2);
    when(appConfig.getMdmAuthKey()).thenReturn("a1-b2-c3-d4");
    when(appConfig.getItemMDMBaseUrl()).thenReturn("http://localhost:8080");
    when(retryableRestConnector.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
        .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));
    itemMDMService.retrieveItemDetails(
        itemSet, MockHttpHeaders.getHeaders(), WM, Boolean.TRUE, Boolean.FALSE);
    verify(retryableRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class));
  }

  @Test
  public void testItemCatalogUpdateIsSuccess() {
    when(appConfig.getMdmAuthKey()).thenReturn("a1-b2-c3-d4");
    when(appConfig.getItemMDMBaseUrl()).thenReturn("http://localhost:8080");
    when(simpleRestConnector.exchange(
            anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class)))
        .thenReturn(new ResponseEntity<String>("", HttpStatus.OK));

    itemMDMService.updateVendorUPC(getItemCatalogUpdateRequest(), MockHttpHeaders.getHeaders());
    verify(simpleRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testItemCatalogUpdateThrowsExceptionOnReceivingNullResponseFromMDM() {
    when(appConfig.getMdmAuthKey()).thenReturn("a1-b2-c3-d4");
    when(appConfig.getItemMDMBaseUrl()).thenReturn("http://localhost:8080");
    when(simpleRestConnector.exchange(
            anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class)))
        .thenReturn(new ResponseEntity(HttpStatus.OK));

    itemMDMService.updateVendorUPC(getItemCatalogUpdateRequest(), MockHttpHeaders.getHeaders());
    verify(simpleRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testItemCatalogUpdateThrowsExceptionOnReceivingResourceAccessException() {
    when(appConfig.getMdmAuthKey()).thenReturn("a1-b2-c3-d4");
    when(appConfig.getItemMDMBaseUrl()).thenReturn("http://localhost:8080");
    when(simpleRestConnector.exchange(
            anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class)))
        .thenThrow(new ResourceAccessException("Error"));
    itemMDMService.updateVendorUPC(getItemCatalogUpdateRequest(), MockHttpHeaders.getHeaders());
    verify(simpleRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testItemCatalogUpdateThrowsExceptionOnReceivingRestClientResponseException() {
    when(appConfig.getMdmAuthKey()).thenReturn("a1-b2-c3-d4");
    when(appConfig.getItemMDMBaseUrl()).thenReturn("http://localhost:8080");
    when(simpleRestConnector.exchange(
            anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class)))
        .thenThrow(
            new RestClientResponseException(
                "Some error.",
                HttpStatus.BAD_REQUEST.value(),
                "",
                null,
                "".getBytes(),
                Charset.forName("UTF-8")));
    itemMDMService.updateVendorUPC(getItemCatalogUpdateRequest(), MockHttpHeaders.getHeaders());
    verify(simpleRestConnector, times(1))
        .exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class));
  }

  private ItemCatalogUpdateRequest getItemCatalogUpdateRequest() {
    ItemCatalogUpdateRequest itemCatalogUpdateRequest = new ItemCatalogUpdateRequest();
    itemCatalogUpdateRequest.setItemNumber(12344L);
    itemCatalogUpdateRequest.setNewItemUPC("UPC1");
    return itemCatalogUpdateRequest;
  }
}
