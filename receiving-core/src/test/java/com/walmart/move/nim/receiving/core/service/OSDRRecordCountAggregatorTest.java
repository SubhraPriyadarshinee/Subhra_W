package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.walmart.move.nim.receiving.core.client.damage.AsyncDamageRestApiClient;
import com.walmart.move.nim.receiving.core.client.damage.DamageDeliveryInfo;
import com.walmart.move.nim.receiving.core.client.fit.AsyncFitRestApiClient;
import com.walmart.move.nim.receiving.core.client.fit.ProblemCountByDeliveryResponse;
import com.walmart.move.nim.receiving.core.client.gdm.AsyncGdmRestApiClient;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.DeliveryWithOSDRResponse;
import com.walmart.move.nim.receiving.core.model.POPOLineKey;
import com.walmart.move.nim.receiving.core.model.ReceivingCountSummary;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class OSDRRecordCountAggregatorTest {

  @Mock private AsyncGdmRestApiClient mockAsyncGdmRestApiClient;

  @Mock private AsyncDamageRestApiClient mockAsyncDamageRestApiClient;

  @Mock private AsyncFitRestApiClient mockAsyncFitRestApiClient;

  @Mock private OSDRCalculator mockOsdrCalculator;

  @Mock private ReceiptService mockReceiptService;
  @Mock private TenantSpecificConfigReader configUtils;

  @InjectMocks private OSDRRecordCountAggregator mockOsdrRecordCountAggregator;

  Gson gson = new Gson();

  @BeforeClass
  public void setup() {

    MockitoAnnotations.initMocks(this);

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32612);
    TenantContext.setCorrelationId("a1-b2-c3-d4");
  }

  @AfterTest
  public void afterTest() throws Exception {}

  private String readFile(String fileName) throws IOException {
    File resource = new ClassPathResource(fileName).getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    return mockResponse;
  }

  @AfterMethod
  public void tearDown() {
    reset(configUtils);
    reset(mockAsyncGdmRestApiClient);
    reset(mockAsyncDamageRestApiClient);
    reset(mockAsyncFitRestApiClient);
  }

  @Test
  public void testGetReceivingCountSummary() throws IOException, ReceivingException {

    String mockDeliveryResponse = readFile("gdm_v3_getDelivery.json");

    DeliveryWithOSDRResponse mockDeliveryWithOSDRResponse =
        gson.fromJson(mockDeliveryResponse, DeliveryWithOSDRResponse.class);
    doReturn(CompletableFuture.completedFuture(mockDeliveryWithOSDRResponse))
        .when(mockAsyncGdmRestApiClient)
        .getDelivery(anyLong(), any(Map.class));

    String mockDamageDeliveryResponse =
        readFile("damage_getDamageCountByDelivery_mock_response.json");
    Type type = new TypeToken<List<DamageDeliveryInfo>>() {}.getType();
    Optional<List<DamageDeliveryInfo>> mockDamageDeliveryInfoList =
        Optional.of(gson.fromJson(mockDamageDeliveryResponse, type));
    doReturn(CompletableFuture.completedFuture(mockDamageDeliveryInfoList))
        .when(mockAsyncDamageRestApiClient)
        .findDamagesByDelivery(anyLong(), any(Map.class));

    String mockProblemFitResponse = readFile("fit_getProblemCntByDelivery_mock_response.json");
    Optional<ProblemCountByDeliveryResponse> mockProblemCountByDeliveryResponse =
        Optional.of(gson.fromJson(mockProblemFitResponse, ProblemCountByDeliveryResponse.class));
    doReturn(CompletableFuture.completedFuture(mockProblemCountByDeliveryResponse))
        .when(mockAsyncFitRestApiClient)
        .findProblemCountByDelivery(anyLong(), any(Map.class));

    Receipt mockReceipt = new Receipt();
    mockReceipt.setPurchaseReferenceNumber("9164390046");
    mockReceipt.setPurchaseReferenceLineNumber(1);
    mockReceipt.setVersion(0);
    mockReceipt.setQuantity(36);
    mockReceipt.setQuantityUom("ZA");
    mockReceipt.setVnpkQty(6);

    doReturn(Arrays.asList(mockReceipt))
        .when(mockReceiptService)
        .findByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyLong(), anyString(), anyInt());

    Receipt savedReceipt = new Receipt();
    savedReceipt.setVersion(1);
    doReturn(savedReceipt).when(mockReceiptService).saveAndFlushReceipt(any(Receipt.class));

    Map<String, Object> mockHeaders =
        ReceivingUtils.getForwardablHeader(ReceivingUtils.getHeaders());

    Map<POPOLineKey, ReceivingCountSummary> receivingCountSummary =
        mockOsdrRecordCountAggregator.getReceivingCountSummary(9967271326L, mockHeaders);

    assertEquals(receivingCountSummary.size(), 6);
    assertEquals(receivingCountSummary.get(new POPOLineKey("9164390046", 1)).getDamageQty(), 5);
    assertEquals(receivingCountSummary.get(new POPOLineKey("9164390046", 1)).getReceiveQty(), 0);
    assertEquals(receivingCountSummary.get(new POPOLineKey("9164390046", 1)).getShortageQty(), 0);

    verify(mockReceiptService, atLeastOnce())
        .findByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyLong(), anyString(), anyInt());

    verify(mockAsyncGdmRestApiClient, atLeast(1)).getDelivery(anyLong(), any(Map.class));
    verify(mockAsyncDamageRestApiClient, atLeast(1))
        .findDamagesByDelivery(anyLong(), any(Map.class));
    verify(mockAsyncFitRestApiClient, atLeast(1))
        .findProblemCountByDelivery(anyLong(), any(Map.class));
  }

  @Test
  public void testGetReceivingCountSummary_exception_missing_DamageCode() {
    try {
      String mockDeliveryResponse = null;

      mockDeliveryResponse = readFile("gdm_v3_getDelivery.json");

      DeliveryWithOSDRResponse mockDeliveryWithOSDRResponse =
          gson.fromJson(mockDeliveryResponse, DeliveryWithOSDRResponse.class);
      doReturn(CompletableFuture.completedFuture(mockDeliveryWithOSDRResponse))
          .when(mockAsyncGdmRestApiClient)
          .getDelivery(anyLong(), any(Map.class));

      String mockDamageDeliveryResponse = readFile("damage_getDamages_missing_DamageCode.json");
      Type type = new TypeToken<List<DamageDeliveryInfo>>() {}.getType();
      Optional<List<DamageDeliveryInfo>> mockDamageDeliveryInfoList =
          Optional.of(gson.fromJson(mockDamageDeliveryResponse, type));
      doReturn(CompletableFuture.completedFuture(mockDamageDeliveryInfoList))
          .when(mockAsyncDamageRestApiClient)
          .findDamagesByDelivery(anyLong(), any(Map.class));

      String mockProblemFitResponse = readFile("fit_getProblemCntByDelivery_mock_response.json");
      Optional<ProblemCountByDeliveryResponse> mockProblemCountByDeliveryResponse =
          Optional.of(gson.fromJson(mockProblemFitResponse, ProblemCountByDeliveryResponse.class));
      doReturn(CompletableFuture.completedFuture(mockProblemCountByDeliveryResponse))
          .when(mockAsyncFitRestApiClient)
          .findProblemCountByDelivery(anyLong(), any(Map.class));

      Receipt mockReceipt = new Receipt();
      mockReceipt.setPurchaseReferenceNumber("9164390046");
      mockReceipt.setPurchaseReferenceLineNumber(1);
      mockReceipt.setVersion(0);
      mockReceipt.setQuantity(36);
      mockReceipt.setQuantityUom("ZA");
      mockReceipt.setVnpkQty(6);

      doReturn(Arrays.asList(mockReceipt))
          .when(mockReceiptService)
          .findByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
              anyLong(), anyString(), anyInt());

      Receipt savedReceipt = new Receipt();
      savedReceipt.setVersion(1);
      doReturn(savedReceipt).when(mockReceiptService).saveAndFlushReceipt(any(Receipt.class));

      Map<String, Object> mockHeaders =
          ReceivingUtils.getForwardablHeader(ReceivingUtils.getHeaders());

      Map<POPOLineKey, ReceivingCountSummary> receivingCountSummary =
          mockOsdrRecordCountAggregator.getReceivingCountSummary(9967271326L, mockHeaders);
      fail("should throw exception to user");
    } catch (IOException e) {
      fail(e.getMessage());
    } catch (ReceivingException receivingException) {
      assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, receivingException.getHttpStatus());
      assertEquals(receivingException.getErrorResponse().getErrorMessage(), "Missing Damage Code");
      assertEquals("GetDeliveryOSDRData", receivingException.getErrorResponse().getErrorCode());
      assertEquals(
          "Could not load information", receivingException.getErrorResponse().getErrorHeader());
    }
  }

  @Test
  public void testGetReceivingCountSummary_exception_missingClaimType() {
    try {
      String mockDeliveryResponse = null;

      mockDeliveryResponse = readFile("gdm_v3_getDelivery.json");

      DeliveryWithOSDRResponse mockDeliveryWithOSDRResponse =
          gson.fromJson(mockDeliveryResponse, DeliveryWithOSDRResponse.class);
      doReturn(CompletableFuture.completedFuture(mockDeliveryWithOSDRResponse))
          .when(mockAsyncGdmRestApiClient)
          .getDelivery(anyLong(), any(Map.class));

      String mockDamageDeliveryResponse = readFile("damage_getDamages_missing_ClaimType.json");
      Type type = new TypeToken<List<DamageDeliveryInfo>>() {}.getType();
      Optional<List<DamageDeliveryInfo>> mockDamageDeliveryInfoList =
          Optional.of(gson.fromJson(mockDamageDeliveryResponse, type));
      doReturn(CompletableFuture.completedFuture(mockDamageDeliveryInfoList))
          .when(mockAsyncDamageRestApiClient)
          .findDamagesByDelivery(anyLong(), any(Map.class));

      String mockProblemFitResponse = readFile("fit_getProblemCntByDelivery_mock_response.json");
      Optional<ProblemCountByDeliveryResponse> mockProblemCountByDeliveryResponse =
          Optional.of(gson.fromJson(mockProblemFitResponse, ProblemCountByDeliveryResponse.class));
      doReturn(CompletableFuture.completedFuture(mockProblemCountByDeliveryResponse))
          .when(mockAsyncFitRestApiClient)
          .findProblemCountByDelivery(anyLong(), any(Map.class));

      Receipt mockReceipt = new Receipt();
      mockReceipt.setPurchaseReferenceNumber("9164390046");
      mockReceipt.setPurchaseReferenceLineNumber(1);
      mockReceipt.setVersion(0);
      mockReceipt.setQuantity(36);
      mockReceipt.setQuantityUom("ZA");
      mockReceipt.setVnpkQty(6);

      doReturn(Arrays.asList(mockReceipt))
          .when(mockReceiptService)
          .findByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
              anyLong(), anyString(), anyInt());

      Receipt savedReceipt = new Receipt();
      savedReceipt.setVersion(1);
      doReturn(savedReceipt).when(mockReceiptService).saveAndFlushReceipt(any(Receipt.class));

      Map<String, Object> mockHeaders =
          ReceivingUtils.getForwardablHeader(ReceivingUtils.getHeaders());

      Map<POPOLineKey, ReceivingCountSummary> receivingCountSummary =
          mockOsdrRecordCountAggregator.getReceivingCountSummary(9967271326L, mockHeaders);
      fail("should throw exception to user");
    } catch (IOException e) {
      fail(e.getMessage());
    } catch (ReceivingException receivingException) {
      assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, receivingException.getHttpStatus());
      assertEquals(receivingException.getErrorResponse().getErrorMessage(), "Missing Claim Type");
      assertEquals("GetDeliveryOSDRData", receivingException.getErrorResponse().getErrorCode());
      assertEquals(
          "Could not load information", receivingException.getErrorResponse().getErrorHeader());
    }
  }

  @Test
  public void testGetReceivingCountSummary_exception_missingDamageCodeAndClaimType() {
    try {
      String mockDeliveryResponse = null;

      mockDeliveryResponse = readFile("gdm_v3_getDelivery.json");

      DeliveryWithOSDRResponse mockDeliveryWithOSDRResponse =
          gson.fromJson(mockDeliveryResponse, DeliveryWithOSDRResponse.class);
      doReturn(CompletableFuture.completedFuture(mockDeliveryWithOSDRResponse))
          .when(mockAsyncGdmRestApiClient)
          .getDelivery(anyLong(), any(Map.class));

      String mockDamageDeliveryResponse =
          readFile("damage_getDamages_missing_DamageCodeAndClaimType.json");
      Type type = new TypeToken<List<DamageDeliveryInfo>>() {}.getType();
      Optional<List<DamageDeliveryInfo>> mockDamageDeliveryInfoList =
          Optional.of(gson.fromJson(mockDamageDeliveryResponse, type));
      doReturn(CompletableFuture.completedFuture(mockDamageDeliveryInfoList))
          .when(mockAsyncDamageRestApiClient)
          .findDamagesByDelivery(anyLong(), any(Map.class));

      String mockProblemFitResponse = readFile("fit_getProblemCntByDelivery_mock_response.json");
      Optional<ProblemCountByDeliveryResponse> mockProblemCountByDeliveryResponse =
          Optional.of(gson.fromJson(mockProblemFitResponse, ProblemCountByDeliveryResponse.class));
      doReturn(CompletableFuture.completedFuture(mockProblemCountByDeliveryResponse))
          .when(mockAsyncFitRestApiClient)
          .findProblemCountByDelivery(anyLong(), any(Map.class));

      Receipt mockReceipt = new Receipt();
      mockReceipt.setPurchaseReferenceNumber("9164390046");
      mockReceipt.setPurchaseReferenceLineNumber(1);
      mockReceipt.setVersion(0);
      mockReceipt.setQuantity(36);
      mockReceipt.setQuantityUom("ZA");
      mockReceipt.setVnpkQty(6);

      doReturn(Arrays.asList(mockReceipt))
          .when(mockReceiptService)
          .findByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
              anyLong(), anyString(), anyInt());

      Receipt savedReceipt = new Receipt();
      savedReceipt.setVersion(1);
      doReturn(savedReceipt).when(mockReceiptService).saveAndFlushReceipt(any(Receipt.class));

      Map<String, Object> mockHeaders =
          ReceivingUtils.getForwardablHeader(ReceivingUtils.getHeaders());

      Map<POPOLineKey, ReceivingCountSummary> receivingCountSummary =
          mockOsdrRecordCountAggregator.getReceivingCountSummary(9967271326L, mockHeaders);
      fail("should throw exception to user");
    } catch (IOException e) {
      fail(e.getMessage());
    } catch (ReceivingException receivingException) {
      assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, receivingException.getHttpStatus());
      assertEquals(
          receivingException.getErrorResponse().getErrorMessage(),
          "Missing Damage Code and Claim Type");
      assertEquals("GetDeliveryOSDRData", receivingException.getErrorResponse().getErrorCode());
      assertEquals(
          "Could not load information", receivingException.getErrorResponse().getErrorHeader());
    }
  }

  //  @Test
  public void testGetReceivingCountSummary_UseDefault_missing_DamageCodeAndClaimType() {
    try {
      String mockDeliveryResponse = null;

      mockDeliveryResponse = readFile("gdm_v3_getDelivery.json");

      DeliveryWithOSDRResponse mockDeliveryWithOSDRResponse =
          gson.fromJson(mockDeliveryResponse, DeliveryWithOSDRResponse.class);
      doReturn(CompletableFuture.completedFuture(mockDeliveryWithOSDRResponse))
          .when(mockAsyncGdmRestApiClient)
          .getDelivery(anyLong(), any(Map.class));

      String mockDamageDeliveryResponse =
          readFile("damage_getDamages_missing_DamageCodeAndClaimType.json");
      Type type = new TypeToken<List<DamageDeliveryInfo>>() {}.getType();
      Optional<List<DamageDeliveryInfo>> mockDamageDeliveryInfoList =
          Optional.of(gson.fromJson(mockDamageDeliveryResponse, type));
      doReturn(CompletableFuture.completedFuture(mockDamageDeliveryInfoList))
          .when(mockAsyncDamageRestApiClient)
          .findDamagesByDelivery(anyLong(), any(Map.class));

      String mockProblemFitResponse = readFile("fit_getProblemCntByDelivery_mock_response.json");
      Optional<ProblemCountByDeliveryResponse> mockProblemCountByDeliveryResponse =
          Optional.of(gson.fromJson(mockProblemFitResponse, ProblemCountByDeliveryResponse.class));
      doReturn(CompletableFuture.completedFuture(mockProblemCountByDeliveryResponse))
          .when(mockAsyncFitRestApiClient)
          .findProblemCountByDelivery(anyLong(), any(Map.class));

      Receipt mockReceipt = new Receipt();
      mockReceipt.setPurchaseReferenceNumber("9164390046");
      mockReceipt.setPurchaseReferenceLineNumber(1);
      mockReceipt.setVersion(0);
      mockReceipt.setQuantity(36);
      mockReceipt.setQuantityUom("ZA");
      mockReceipt.setVnpkQty(6);

      doReturn(Arrays.asList(mockReceipt))
          .when(mockReceiptService)
          .findByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
              anyLong(), anyString(), anyInt());

      Receipt savedReceipt = new Receipt();
      savedReceipt.setVersion(1);
      doReturn(savedReceipt).when(mockReceiptService).saveAndFlushReceipt(any(Receipt.class));

      Map<String, Object> mockHeaders =
          ReceivingUtils.getForwardablHeader(ReceivingUtils.getHeaders());

      when(configUtils.getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean()))
          .thenReturn(true);

      Map<POPOLineKey, ReceivingCountSummary> receivingCountSummary =
          mockOsdrRecordCountAggregator.getReceivingCountSummary(9967271326L, mockHeaders);
      POPOLineKey popoLineKey =
          new POPOLineKey(
              mockReceipt.getPurchaseReferenceNumber(),
              mockReceipt.getPurchaseReferenceLineNumber());
      final ReceivingCountSummary receivingCountSummary1 = receivingCountSummary.get(popoLineKey);
      final String damageReasonCode = receivingCountSummary1.getDamageReasonCode();
      final String damageClaimType = receivingCountSummary1.getDamageClaimType();
      assertEquals(damageReasonCode, "D10");
      assertEquals(damageClaimType, "VENDOR");

    } catch (IOException e) {
      fail("should not get IOException " + e.getMessage());
    } catch (ReceivingException receivingException) {
      fail("user should not get error");
    }
  }
}
