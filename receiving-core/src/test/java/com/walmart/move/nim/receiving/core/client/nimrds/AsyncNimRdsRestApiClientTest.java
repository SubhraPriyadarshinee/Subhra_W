package com.walmart.move.nim.receiving.core.client.nimrds;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.walmart.move.nim.receiving.core.client.nimrds.model.RdsReceiptsSummaryByPoResponse;
import com.walmart.move.nim.receiving.core.client.nimrds.model.StoreDistribution;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryQtyByPoLineResponse;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class AsyncNimRdsRestApiClientTest {

  @Mock private NimRDSRestApiClient nimRDSRestApiClient;
  @InjectMocks private AsyncNimRdsRestApiClient asyncNimRdsRestApiClient;

  @BeforeClass
  public void setup() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32818);
  }

  @AfterClass
  public void resetMocks() {
    reset(nimRDSRestApiClient);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testGetReceivedQtySummaryByPoThrowsException() throws ReceivingException {
    Long deliveryNumber = 323223L;
    doThrow(new ReceivingBadDataException("mockErrorCode", "mockErrorMessage"))
        .when(nimRDSRestApiClient)
        .getReceivedQtySummaryByPo(anyLong(), any(Map.class));

    asyncNimRdsRestApiClient.getReceivedQtySummaryByPo(
        deliveryNumber,
        ReceivingUtils.getForwardablHeaderWithTenantData(MockHttpHeaders.getHeaders()));
    verify(nimRDSRestApiClient, times(1)).getReceivedQtySummaryByPo(anyLong(), any(Map.class));
  }

  @Test
  public void testGetReceivedQtySummaryByPoReturnsSuccessResponse() throws ReceivingException {
    Long deliveryNumber = 323223L;
    RdsReceiptsSummaryByPoResponse response = new RdsReceiptsSummaryByPoResponse();
    response.setDeliveryNumber(323223L);
    response.setReceivedQty(223);

    doReturn(response)
        .when(nimRDSRestApiClient)
        .getReceivedQtySummaryByPo(anyLong(), any(Map.class));

    asyncNimRdsRestApiClient.getReceivedQtySummaryByPo(
        deliveryNumber,
        ReceivingUtils.getForwardablHeaderWithTenantData(MockHttpHeaders.getHeaders()));
    verify(nimRDSRestApiClient, times(1)).getReceivedQtySummaryByPo(anyLong(), any(Map.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testGetReceivedQtySummaryByPoLineThrowsException() throws ReceivingException {
    Long deliveryNumber = 323223L;
    String purchaseReferenceNumber = "2323232";
    doThrow(new ReceivingBadDataException("mockErrorCode", "mockErrorMessage"))
        .when(nimRDSRestApiClient)
        .getReceivedQtySummaryByPoLine(anyLong(), anyString(), any(Map.class));

    asyncNimRdsRestApiClient.getReceivedQtySummaryByPoLine(
        deliveryNumber,
        purchaseReferenceNumber,
        ReceivingUtils.getForwardablHeaderWithTenantData(MockHttpHeaders.getHeaders()));
    verify(nimRDSRestApiClient, times(1))
        .getReceivedQtySummaryByPoLine(anyLong(), anyString(), any(Map.class));
  }

  @Test
  public void testGetReceivedQtySummaryByPoLineReturnsSuccess() throws ReceivingException {
    Long deliveryNumber = 323223L;
    String purchaseReferenceNumber = "2323232";
    ReceiptSummaryQtyByPoLineResponse response = new ReceiptSummaryQtyByPoLineResponse();
    response.setPurchaseReferenceNumber("2323232");

    doReturn(response)
        .when(nimRDSRestApiClient)
        .getReceivedQtySummaryByPoLine(anyLong(), anyString(), any(Map.class));

    asyncNimRdsRestApiClient.getReceivedQtySummaryByPoLine(
        deliveryNumber,
        purchaseReferenceNumber,
        ReceivingUtils.getForwardablHeaderWithTenantData(MockHttpHeaders.getHeaders()));
    verify(nimRDSRestApiClient, times(1))
        .getReceivedQtySummaryByPoLine(anyLong(), anyString(), any(Map.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testgetStoreDistributionByDeliveryDocumentThrowsException()
      throws ReceivingException {
    String purchaseReferenceNumber = "4576669261";
    doThrow(new ReceivingBadDataException("mockErrorCode", "mockErrorMessage"))
        .when(nimRDSRestApiClient)
        .getStoreDistributionByDeliveryDocumentLine(anyString(), anyInt(), any(Map.class));

    asyncNimRdsRestApiClient.getStoreDistributionByDeliveryDocument(
        purchaseReferenceNumber,
        1,
        ReceivingUtils.getForwardablHeaderWithTenantData(MockHttpHeaders.getHeaders()));
    verify(nimRDSRestApiClient, times(1))
        .getStoreDistributionByDeliveryDocumentLine(anyString(), anyInt(), any(Map.class));
  }

  @Test
  public void testgetStoreDistributionByDeliveryDocumentReturnSuccess() throws ReceivingException {
    String purchaseReferenceNumber = "4576669261";
    Integer purchaseReferenceLineNumber = 1;
    StoreDistribution storeDistribution = new StoreDistribution();
    List<StoreDistribution> storeDistributions = Arrays.asList(storeDistribution);
    Pair<Integer, List<StoreDistribution>> response = new Pair<>(1, storeDistributions);

    doReturn(response)
        .when(nimRDSRestApiClient)
        .getStoreDistributionByDeliveryDocumentLine(anyString(), anyInt(), any(Map.class));

    asyncNimRdsRestApiClient.getStoreDistributionByDeliveryDocument(
        purchaseReferenceNumber,
        purchaseReferenceLineNumber,
        ReceivingUtils.getForwardablHeaderWithTenantData(MockHttpHeaders.getHeaders()));
    verify(nimRDSRestApiClient, times(1))
        .getStoreDistributionByDeliveryDocumentLine(anyString(), anyInt(), any(Map.class));
  }
}
