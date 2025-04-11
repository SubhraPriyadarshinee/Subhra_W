package com.walmart.move.nim.receiving.witron.publisher;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.CORRELATION_ID_HEADER_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import com.walmart.move.nim.receiving.core.client.dcfin.DCFinRestApiClientException;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.message.service.RetryService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.witron.common.GDCFlagReader;
import com.walmart.move.nim.receiving.witron.service.DeliveryCacheServiceInMemoryImpl;
import com.walmart.move.nim.receiving.witron.service.DeliveryCacheValue;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class WitronDCFinServiceImplTest {

  @Mock private DeliveryCacheServiceInMemoryImpl deliveryCache;
  @Mock private RetryService retryService;

  @Mock private AppConfig appConfig;
  @Mock private GDCFlagReader gdcFlagReader;

  @InjectMocks private WitronDCFinServiceImpl witronDCFinServiceImpl;
  @Mock TenantSpecificConfigReader tenantSpecificConfigReader;

  @BeforeMethod
  public void createWitronDCFinServiceImpl() throws Exception {
    MockitoAnnotations.initMocks(this);

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32612);
    TenantContext.setCorrelationId("a1-b2-c3-d4");

    doReturn(true).when(appConfig).getIsReceiptPostingEnaledForDCFin();
  }

  @Test
  public void testPostReceiptsToDCFin() throws DCFinRestApiClientException, ReceivingException {

    DeliveryCacheValue deliveryCacheValue = new DeliveryCacheValue();
    deliveryCacheValue.setBolWeight(123.45f);
    deliveryCacheValue.setTotalBolFbq(200);
    deliveryCacheValue.setTrailerId("11223344");

    doReturn(deliveryCacheValue)
        .when(deliveryCache)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));

    Container mockContainer = new Container();
    mockContainer.setDeliveryNumber(12345l);

    ContainerItem mockContainerItem = new ContainerItem();
    mockContainerItem.setPurchaseReferenceNumber("123");
    mockContainerItem.setPurchaseReferenceLineNumber(1);
    mockContainerItem.setItemNumber(123456789l);
    mockContainer.setContainerItems(Arrays.asList(mockContainerItem));

    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();

    ArgumentCaptor<String> dcFinPayLoad = ArgumentCaptor.forClass(String.class);
    doReturn(false).when(gdcFlagReader).isManualGdcEnabled();

    // execute
    witronDCFinServiceImpl.postReceiptsToDCFin(mockContainer, httpHeaders, null);

    // Assert
    verify(deliveryCache, times(1))
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));

    verify(retryService)
        .putForRetries(anyString(), any(), any(HttpHeaders.class), dcFinPayLoad.capture(), any());
    final String dcFinRequestPayLoad = dcFinPayLoad.getValue();
    final String isAtlasItem = "\"isAtlasItem\":false"; // CASE1
    assertTrue(dcFinRequestPayLoad.contains(isAtlasItem), "current prod will have FALSE value");
  }

  @Test
  public void testPostReceiptsToDCFin_case2_isDcOneAtlas_ConvertedItem_true()
      throws ReceivingException {

    DeliveryCacheValue deliveryCacheValue = new DeliveryCacheValue();
    deliveryCacheValue.setBolWeight(123.45f);
    deliveryCacheValue.setTotalBolFbq(200);
    deliveryCacheValue.setTrailerId("11223344");

    doReturn(deliveryCacheValue)
        .when(deliveryCache)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));

    Container mockContainer = new Container();
    mockContainer.setDeliveryNumber(12345l);

    ContainerItem mockContainerItem = new ContainerItem();
    mockContainerItem.setPurchaseReferenceNumber("123");
    mockContainerItem.setPurchaseReferenceLineNumber(1);
    mockContainerItem.setItemNumber(123456789l);
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(ReceivingConstants.IS_ATLAS_CONVERTED_ITEM, "true");
    mockContainerItem.setContainerItemMiscInfo(containerItemMiscInfo);
    mockContainer.setContainerItems(Arrays.asList(mockContainerItem));

    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    ArgumentCaptor<String> dcFinPayLoad = ArgumentCaptor.forClass(String.class);
    doReturn(true).when(gdcFlagReader).isManualGdcEnabled();
    doReturn(true).when(gdcFlagReader).isDCOneAtlasEnabled();

    // execute
    witronDCFinServiceImpl.postReceiptsToDCFin(mockContainer, httpHeaders, null);

    // Assert
    verify(retryService)
        .putForRetries(anyString(), any(), any(HttpHeaders.class), dcFinPayLoad.capture(), any());
    final String dcFinRequestPayLoad = dcFinPayLoad.getValue();
    final String isAtlasItem = "\"isAtlasItem\":true"; //
    assertTrue(
        dcFinRequestPayLoad.contains(isAtlasItem),
        "oneDc and item converted mimic like prod will have TRUE value");
  }

  @Test
  public void testPostReceiptsToDCFin_case3_isDcOneAtlas_ConvertedItem_false()
      throws ReceivingException {
    DeliveryCacheValue deliveryCacheValue = new DeliveryCacheValue();
    deliveryCacheValue.setBolWeight(123.45f);
    deliveryCacheValue.setTotalBolFbq(200);
    deliveryCacheValue.setTrailerId("11223344");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
    doReturn(deliveryCacheValue)
        .when(deliveryCache)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));

    Container mockContainer = new Container();
    mockContainer.setDeliveryNumber(12345l);

    ContainerItem mockContainerItem = new ContainerItem();
    mockContainerItem.setPurchaseReferenceNumber("123");
    mockContainerItem.setPurchaseReferenceLineNumber(1);
    mockContainerItem.setItemNumber(123456789l);
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(ReceivingConstants.IS_ATLAS_CONVERTED_ITEM, "false");
    mockContainerItem.setContainerItemMiscInfo(containerItemMiscInfo);
    mockContainer.setContainerItems(Arrays.asList(mockContainerItem));

    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    ArgumentCaptor<String> dcFinPayLoad = ArgumentCaptor.forClass(String.class);
    doReturn(true).when(gdcFlagReader).isManualGdcEnabled();
    doReturn(true).when(gdcFlagReader).isDCOneAtlasEnabled();

    // execute
    witronDCFinServiceImpl.postReceiptsToDCFin(mockContainer, httpHeaders, null);

    // Assert
    verify(retryService)
        .putForRetries(anyString(), any(), any(HttpHeaders.class), dcFinPayLoad.capture(), any());
    final String dcFinRequestPayLoad = dcFinPayLoad.getValue();
    final String isAtlasItem = "\"isAtlasItem\":false"; // isAtlasConvertedItem=true
    assertTrue(
        dcFinRequestPayLoad.contains(isAtlasItem),
        "oneDc and but item NOT converted, isAtlasItem=false value");
  }

  public void testPostReceiptsToDCFin_case4_NOT_DcOneAtlas_fullGls_false()
      throws ReceivingException {
    DeliveryCacheValue deliveryCacheValue = new DeliveryCacheValue();
    deliveryCacheValue.setBolWeight(123.45f);
    deliveryCacheValue.setTotalBolFbq(200);
    deliveryCacheValue.setTrailerId("11223344");

    doReturn(deliveryCacheValue)
        .when(deliveryCache)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));

    Container mockContainer = new Container();
    mockContainer.setDeliveryNumber(12345l);

    ContainerItem mockContainerItem = new ContainerItem();
    mockContainerItem.setPurchaseReferenceNumber("123");
    mockContainerItem.setPurchaseReferenceLineNumber(1);
    mockContainerItem.setItemNumber(123456789l);
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(ReceivingConstants.IS_ATLAS_CONVERTED_ITEM, "false");
    mockContainerItem.setContainerItemMiscInfo(containerItemMiscInfo);
    mockContainer.setContainerItems(Arrays.asList(mockContainerItem));

    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    ArgumentCaptor<String> dcFinPayLoad = ArgumentCaptor.forClass(String.class);
    doReturn(true).when(gdcFlagReader).isManualGdcEnabled();
    doReturn(false).when(gdcFlagReader).isDCOneAtlasEnabled();

    // execute
    witronDCFinServiceImpl.postReceiptsToDCFin(mockContainer, httpHeaders, true);

    // Assert
    verify(retryService)
        .putForRetries(anyString(), any(), any(HttpHeaders.class), dcFinPayLoad.capture(), any());
    final String dcFinRequestPayLoad = dcFinPayLoad.getValue();
    final String isAtlasItem = "\"isAtlasItem\":false"; // isAtlasConvertedItem=true
    assertTrue(
        dcFinRequestPayLoad.contains(isAtlasItem),
        "oneDc and but item NOT converted, isAtlasItem=false value");
  }

  @Test
  public void testPostReceiptsToDCFin_gdmError()
      throws DCFinRestApiClientException, ReceivingException {

    DeliveryCacheValue deliveryCacheValue = new DeliveryCacheValue();
    deliveryCacheValue.setBolWeight(123.45f);
    deliveryCacheValue.setTotalBolFbq(200);
    deliveryCacheValue.setTrailerId("11223344");

    doReturn(null)
        .when(deliveryCache)
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));

    Container mockContainer = new Container();
    mockContainer.setDeliveryNumber(12345l);

    ContainerItem mockContainerItem = new ContainerItem();
    mockContainerItem.setPurchaseReferenceNumber("123");
    mockContainerItem.setPurchaseReferenceLineNumber(1);
    mockContainerItem.setItemNumber(123456789l);
    mockContainer.setContainerItems(Arrays.asList(mockContainerItem));

    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    witronDCFinServiceImpl.postReceiptsToDCFin(mockContainer, httpHeaders, null);

    verify(deliveryCache, times(1))
        .getDeliveryDetailsByPoPoLine(anyLong(), anyString(), anyInt(), any(HttpHeaders.class));
  }

  @Test
  public void testGetTxnId_NoCorrelationId() {
    Map<String, Object> headersMap = new HashMap<>();
    final String txId = WitronDCFinServiceImpl.getTxnId(headersMap, null);
    assertNotNull(txId);
  }

  @Test
  public void testGetTxnId_hasCorrelationId() {
    Map<String, Object> headersMap = new HashMap<>();
    headersMap.put(CORRELATION_ID_HEADER_KEY, "test-correlationId-123");
    final String txId = WitronDCFinServiceImpl.getTxnId(headersMap, null);
    assertNotNull(txId);
    assertEquals(txId, "test-correlationId-123");
  }

  @Test
  public void testGetTxnId_hasCorrelationId_PlusTxId() {
    Map<String, Object> headersMap = new HashMap<>();
    headersMap.put(CORRELATION_ID_HEADER_KEY, "test-correlationId-123");
    final String txId = WitronDCFinServiceImpl.getTxnId(headersMap, 1);
    assertNotNull(txId);
    assertEquals(txId, "test-correlationId-123-1");
  }
}
