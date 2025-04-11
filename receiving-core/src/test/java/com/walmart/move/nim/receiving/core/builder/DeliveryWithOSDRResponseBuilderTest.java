package com.walmart.move.nim.receiving.core.builder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.util.ReflectionTestUtils.setField;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.walmart.move.nim.receiving.core.client.damage.AsyncDamageRestApiClient;
import com.walmart.move.nim.receiving.core.client.damage.DamageDeliveryInfo;
import com.walmart.move.nim.receiving.core.client.fit.AsyncFitRestApiClient;
import com.walmart.move.nim.receiving.core.client.fit.ProblemCountByDeliveryResponse;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClient;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClientException;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.service.OSDRCalculator;
import com.walmart.move.nim.receiving.core.service.OSDRRecordCountAggregator;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.core.service.WitronDeliveryMetaDataService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DeliveryWithOSDRResponseBuilderTest {
  @InjectMocks private DeliveryWithOSDRResponseBuilder deliveryWithOSDRResponseBuilder;
  @Mock private GDMRestApiClient mockGDMRestApiClient;
  @Mock private AsyncDamageRestApiClient mockAsyncDamageRestApiClient;
  @Mock private AsyncFitRestApiClient mockAsyncFitRestApiClient;
  @Mock private ReceiptService mockReceiptService;
  @Mock private POHashKeyBuilder poHashKeyBuilder;
  @Mock private OSDRRecordCountAggregator osdrRecordCountAggregator;
  @Mock private WitronDeliveryMetaDataService witronDeliveryMetaDataService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Spy private OSDRCalculator osdrCalculator = new OSDRCalculator();
  @Spy private OSDRRecordCountAggregator osdrRecordCountAggregator_spy;

  private Gson gson = new Gson();
  private DeliveryMetaData mockDeliveryMetaData;

  @BeforeClass
  public void setup() {

    MockitoAnnotations.initMocks(this);
    setField(osdrRecordCountAggregator_spy, "receiptService", mockReceiptService);
    setField(osdrRecordCountAggregator_spy, "configUtils", tenantSpecificConfigReader);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32612);
    TenantContext.setCorrelationId("a1-b2-c3-d4");

    mockDeliveryMetaData =
        DeliveryMetaData.builder()
            .deliveryNumber("123456")
            .deliveryStatus(DeliveryStatus.WRK)
            .doorNumber("100")
            .build();
  }

  @AfterMethod
  public void resetMocks() {
    reset(mockGDMRestApiClient);
    reset(mockAsyncDamageRestApiClient);
    reset(mockAsyncFitRestApiClient);
    reset(mockReceiptService);
    reset(witronDeliveryMetaDataService);
    reset(osdrRecordCountAggregator);
    reset(osdrRecordCountAggregator_spy);
    reset(tenantSpecificConfigReader);
  }

  private String readFile(String fileName) throws IOException {
    File resource = new ClassPathResource(fileName).getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    return mockResponse;
  }

  @Test
  public void testBuild() throws Exception {
    String mockDeliveryResponse = readFile("gdm_v3_getDelivery.json");

    DeliveryWithOSDRResponse mockDeliveryWithOSDRResponse =
        gson.fromJson(mockDeliveryResponse, DeliveryWithOSDRResponse.class);
    doReturn(mockDeliveryWithOSDRResponse)
        .when(mockGDMRestApiClient)
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

    Map<POPOLineKey, ReceivingCountSummary> deliveryFBQMap = new HashMap<>();
    ReceivingCountSummary receivingCountSummary = new ReceivingCountSummary();
    receivingCountSummary.setTotalFBQty(810);
    receivingCountSummary.setTotalFBQtyUOM("ZA");

    deliveryFBQMap.put(new POPOLineKey("9164390046", 1), receivingCountSummary);
    deliveryFBQMap.put(new POPOLineKey("9164390047", 1), receivingCountSummary);

    doReturn(deliveryFBQMap)
        .when(osdrRecordCountAggregator)
        .enrichWithGDMData(any(DeliveryWithOSDRResponse.class));

    Receipt mockReceipt = new Receipt();
    mockReceipt.setQuantity(81);
    mockReceipt.setQuantityUom("ZA");
    mockReceipt.setEachQty(486);
    mockReceipt.setVnpkQty(6);
    mockReceipt.setWhpkQty(6);

    List<Receipt> mockReceiptList = new ArrayList<>();
    mockReceiptList.add(mockReceipt);

    doReturn(mockReceiptList)
        .when(mockReceiptService)
        .findByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyLong(), anyString(), anyInt());

    Receipt savedReceipt = new Receipt();
    savedReceipt.setVersion(1);
    doReturn(savedReceipt).when(mockReceiptService).saveAndFlushReceipt(any(Receipt.class));

    Map<String, Object> mockHeaders =
        ReceivingUtils.getForwardablHeader(ReceivingUtils.getHeaders());

    when(witronDeliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.empty());
    doReturn(mockDeliveryMetaData)
        .when(witronDeliveryMetaDataService)
        .createDeliveryMetaData(any(DeliveryWithOSDRResponse.class));

    DeliveryWithOSDRResponse deliveryWithOSDRResponse =
        deliveryWithOSDRResponseBuilder.build(9967271326L, mockHeaders, true, null);

    final List<PurchaseOrderWithOSDRResponse> purchaseOrders =
        deliveryWithOSDRResponse.getPurchaseOrders();
    assertEquals(purchaseOrders.size(), 2);
    assertEquals(deliveryWithOSDRResponse.getType(), "LIVE");
    assertEquals(deliveryWithOSDRResponse.getStatusInformation().getStatus(), "WRK");

    final PurchaseOrderWithOSDRResponse po1 = purchaseOrders.get(0);
    assertEquals(po1.getLines().size(), 3);
    assertEquals(po1.getLines().get(0).getFreightBillQty().intValue(), 810);
    assertEquals(po1.getLines().get(0).getOsdr().getShortageQty().intValue(), 810);
    assertEquals(po1.getLines().get(0).getOsdr().getShortageUOM(), "ZA");
    assertEquals(po1.getLines().get(0).getOsdr().getShortageReasonCode(), "S10");

    verify(osdrRecordCountAggregator, atLeastOnce())
        .enrichWithGDMData(any(DeliveryWithOSDRResponse.class));

    assertNull(po1.getFinalizedUserId());
    assertNull(po1.getFinalizedTimeStamp());
    assertEquals(po1.getFreightTermCode(), "PRP");
    assertEquals(
        po1.getLines().get(0).getItemDetails().getAdditionalInformation().get("warehouseAreaCode"),
        "8");
    assertEquals(
        po1.getLines().get(0).getItemDetails().getAdditionalInformation().get("warehouseAreaDesc"),
        "Dry Produce");

    final PurchaseOrderWithOSDRResponse po2 = purchaseOrders.get(1);
    assertEquals(po2.getFreightTermCode(), "COLL");

    final OSDR deliveryOSDR = deliveryWithOSDRResponse.getOsdr();
    assertNotNull(deliveryOSDR);
    assertEquals(deliveryOSDR.getOverageQty().intValue(), 0);
    assertEquals(deliveryOSDR.getShortageQty().intValue(), 4860);
    assertEquals(deliveryOSDR.getDamageQty().intValue(), 0);
    assertEquals(deliveryOSDR.getRejectedQty().intValue(), 0);
    assertEquals(deliveryOSDR.getProblemQty().intValue(), 0);
    assertEquals(deliveryOSDR.getReceivedQty().intValue(), 0);
    assertEquals(deliveryOSDR.getPofbqReceivedQty().intValue(), 0);

    verify(mockGDMRestApiClient, times(1)).getDelivery(anyLong(), any(Map.class));
    verify(mockAsyncDamageRestApiClient, times(1)).findDamagesByDelivery(anyLong(), any(Map.class));
    verify(mockAsyncFitRestApiClient, times(1))
        .findProblemCountByDelivery(anyLong(), any(Map.class));
  }

  @Test
  public void testBuild_verify_poLevel_osdr() throws Exception {
    setField(
        deliveryWithOSDRResponseBuilder,
        "osdrRecordCountAggregator",
        osdrRecordCountAggregator_spy);

    String mockDeliveryResponse = readFile("gdm_v3_getDelivery.json");

    DeliveryWithOSDRResponse mockDeliveryWithOSDRResponse =
        gson.fromJson(mockDeliveryResponse, DeliveryWithOSDRResponse.class);
    doReturn(mockDeliveryWithOSDRResponse)
        .when(mockGDMRestApiClient)
        .getDelivery(anyLong(), any(Map.class));

    String mockDamageDeliveryResponse =
        readFile("damage_getDamageCountByDelivery_mock_response.json");
    Type type = new TypeToken<List<DamageDeliveryInfo>>() {}.getType();
    Optional<List<DamageDeliveryInfo>> mockDamageDeliveryInfoList =
        Optional.of(gson.fromJson(mockDamageDeliveryResponse, type));
    doReturn(CompletableFuture.completedFuture(mockDamageDeliveryInfoList))
        .when(mockAsyncDamageRestApiClient)
        .findDamagesByDelivery(anyLong(), any(Map.class));

    String mockProblemFitResponse = readFile("fit_getProblemCntByDelivery_mock_response2.json");
    Optional<ProblemCountByDeliveryResponse> mockProblemCountByDeliveryResponse =
        Optional.of(gson.fromJson(mockProblemFitResponse, ProblemCountByDeliveryResponse.class));
    doReturn(CompletableFuture.completedFuture(mockProblemCountByDeliveryResponse))
        .when(mockAsyncFitRestApiClient)
        .findProblemCountByDelivery(anyLong(), any(Map.class));

    Receipt mockReceipt = new Receipt();
    mockReceipt.setQuantity(81);
    mockReceipt.setQuantityUom("ZA");
    mockReceipt.setEachQty(486);
    mockReceipt.setVnpkQty(6);
    mockReceipt.setWhpkQty(6);

    List<Receipt> mockReceiptList = new ArrayList<>();
    mockReceiptList.add(mockReceipt);

    doReturn(mockReceiptList)
        .when(mockReceiptService)
        .findByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyLong(), anyString(), anyInt());

    Receipt savedReceipt = new Receipt();
    savedReceipt.setVersion(1);
    doReturn(savedReceipt).when(mockReceiptService).saveAndFlushReceipt(any(Receipt.class));
    doReturn(savedReceipt).when(mockReceiptService).saveReceipt(any(Receipt.class));

    Map<String, Object> mockHeaders =
        ReceivingUtils.getForwardablHeader(ReceivingUtils.getHeaders());

    when(witronDeliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.empty());
    doReturn(mockDeliveryMetaData)
        .when(witronDeliveryMetaDataService)
        .createDeliveryMetaData(any(DeliveryWithOSDRResponse.class));

    DeliveryWithOSDRResponse deliveryWithOSDRResponse =
        deliveryWithOSDRResponseBuilder.build(9967271326L, mockHeaders, true, null);

    final List<PurchaseOrderWithOSDRResponse> purchaseOrders =
        deliveryWithOSDRResponse.getPurchaseOrders();
    assertEquals(purchaseOrders.size(), 2);
    assertEquals(deliveryWithOSDRResponse.getType(), "LIVE");
    assertEquals(deliveryWithOSDRResponse.getStatusInformation().getStatus(), "WRK");

    final PurchaseOrderWithOSDRResponse po1 = purchaseOrders.get(0);
    assertEquals(po1.getLines().size(), 3);
    assertEquals(po1.getLines().get(0).getFreightBillQty().intValue(), 810);
    // Problems at Line level
    assertEquals(po1.getLines().get(0).getOsdr().getProblemQty().intValue(), 28);
    assertEquals(po1.getLines().get(1).getOsdr().getProblemQty().intValue(), 0);
    assertEquals(po1.getLines().get(2).getOsdr().getProblemQty().intValue(), 8);

    // Problems  at PO level
    assertEquals(po1.getOsdr().getProblemQty().intValue(), 36);
    verify(osdrRecordCountAggregator_spy, atLeastOnce())
        .enrichWithGDMData(any(DeliveryWithOSDRResponse.class));

    final OSDR deliveryOSDR = deliveryWithOSDRResponse.getOsdr();
    assertNotNull(deliveryOSDR);

    // Problems at Delivery/Header level
    assertEquals(deliveryOSDR.getProblemQty().intValue(), 36);
    assertEquals(deliveryOSDR.getReceivedQty().intValue(), 0);
    assertEquals(deliveryOSDR.getPofbqReceivedQty().intValue(), 0);

    // reset spy to mock for other methods to use as mock
    setField(
        deliveryWithOSDRResponseBuilder, "osdrRecordCountAggregator", osdrRecordCountAggregator);
  }

  @Test
  public void testBuild_delivery_notfound() throws GDMRestApiClientException, ReceivingException {
    Map<String, Object> mockHeaders =
        ReceivingUtils.getForwardablHeader(ReceivingUtils.getHeaders());

    GDMRestApiClientException gdmRestApiClientException =
        new GDMRestApiClientException("Delivery not found", HttpStatus.NOT_FOUND);
    doThrow(gdmRestApiClientException)
        .when(mockGDMRestApiClient)
        .getDelivery(anyLong(), any(Map.class));

    try {
      DeliveryWithOSDRResponse deliveryWithOSDRResponse =
          deliveryWithOSDRResponseBuilder.build(9967271326L, mockHeaders, true, null);
    } catch (ReceivingException receivingException) {
      assertEquals(HttpStatus.NOT_FOUND, receivingException.getHttpStatus());
      assertEquals(
          String.format(ReceivingException.DELIVERY_NOT_FOUND_ERROR_MESSAGE, 9967271326L),
          receivingException.getErrorResponse().getErrorMessage());
      assertEquals(
          ReceivingException.DELIVERY_NOT_FOUND_HEADER,
          receivingException.getErrorResponse().getErrorHeader());
      assertEquals(
          ReceivingException.GDM_SEARCH_DOCUMENT_ERROR_CODE,
          receivingException.getErrorResponse().getErrorCode());
    } catch (Exception e) {
      fail(e.getMessage());
    }

    verify(mockGDMRestApiClient, times(1)).getDelivery(anyLong(), any(Map.class));
  }

  @Test
  public void testBuild_on_finalized_gdm_po() throws Exception {
    String mockDeliveryResponse = readFile("gdm_v3_getDelivery.json");

    DeliveryWithOSDRResponse mockDeliveryWithOSDRResponse =
        gson.fromJson(mockDeliveryResponse, DeliveryWithOSDRResponse.class);

    OperationalInfo operationalInfo = new OperationalInfo();
    operationalInfo.setState("FINALIZED");

    // make po's finalized
    mockDeliveryWithOSDRResponse
        .getPurchaseOrders()
        .stream()
        .forEach(
            po -> {
              po.setOperationalInfo(operationalInfo);
              po.getLines()
                  .stream()
                  .forEach(
                      poLine -> {
                        poLine.setOperationalInfo(operationalInfo);
                      });
            });

    doReturn(mockDeliveryWithOSDRResponse)
        .when(mockGDMRestApiClient)
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

    Map<POPOLineKey, ReceivingCountSummary> deliveryFBQMap = new HashMap<>();
    ReceivingCountSummary receivingCountSummary = new ReceivingCountSummary();
    receivingCountSummary.setTotalFBQty(810);
    receivingCountSummary.setTotalFBQtyUOM("ZA");

    deliveryFBQMap.put(new POPOLineKey("9164390046", 1), receivingCountSummary);
    deliveryFBQMap.put(new POPOLineKey("9164390047", 1), receivingCountSummary);

    doReturn(deliveryFBQMap)
        .when(osdrRecordCountAggregator)
        .enrichWithGDMData(any(DeliveryWithOSDRResponse.class));

    Receipt mockReceipt = new Receipt();
    mockReceipt.setQuantity(81);
    mockReceipt.setQuantityUom("ZA");
    mockReceipt.setEachQty(486);
    mockReceipt.setVnpkQty(6);
    mockReceipt.setWhpkQty(6);

    List<Receipt> mockReceiptList = new ArrayList<>();
    mockReceiptList.add(mockReceipt);

    doReturn(mockReceiptList)
        .when(mockReceiptService)
        .findByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyLong(), anyString(), anyInt());

    Receipt savedReceipt = new Receipt();
    savedReceipt.setVersion(1);
    doReturn(savedReceipt).when(mockReceiptService).saveAndFlushReceipt(any(Receipt.class));

    Map<String, Object> mockHeaders =
        ReceivingUtils.getForwardablHeader(ReceivingUtils.getHeaders());

    when(witronDeliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.empty());
    doReturn(mockDeliveryMetaData)
        .when(witronDeliveryMetaDataService)
        .createDeliveryMetaData(any(DeliveryWithOSDRResponse.class));

    DeliveryWithOSDRResponse deliveryWithOSDRResponse =
        deliveryWithOSDRResponseBuilder.build(9967271326L, mockHeaders, true, null);

    assertEquals(deliveryWithOSDRResponse.getPurchaseOrders().size(), 2);
    assertEquals(deliveryWithOSDRResponse.getPurchaseOrders().get(0).getLines().size(), 3);
    assertEquals(
        deliveryWithOSDRResponse
            .getPurchaseOrders()
            .get(0)
            .getLines()
            .get(0)
            .getFreightBillQty()
            .intValue(),
        810);
    assertEquals(
        deliveryWithOSDRResponse
            .getPurchaseOrders()
            .get(0)
            .getLines()
            .get(0)
            .getOsdr()
            .getShortageQty()
            .intValue(),
        810);
    assertEquals(
        deliveryWithOSDRResponse
            .getPurchaseOrders()
            .get(0)
            .getLines()
            .get(0)
            .getOsdr()
            .getShortageUOM(),
        "ZA");
    assertEquals(
        deliveryWithOSDRResponse
            .getPurchaseOrders()
            .get(0)
            .getLines()
            .get(0)
            .getOsdr()
            .getShortageReasonCode(),
        "S10");

    verify(osdrRecordCountAggregator, atLeastOnce())
        .enrichWithGDMData(any(DeliveryWithOSDRResponse.class));

    verify(mockGDMRestApiClient, times(1)).getDelivery(anyLong(), any(Map.class));
    verify(mockAsyncDamageRestApiClient, times(1)).findDamagesByDelivery(anyLong(), any(Map.class));
    verify(mockAsyncFitRestApiClient, times(1))
        .findProblemCountByDelivery(anyLong(), any(Map.class));
  }

  @Test
  public void testBuild_non_finalized_gdm_po() throws Exception {
    String mockDeliveryResponse = readFile("gdm_v3_getDelivery.json");

    DeliveryWithOSDRResponse mockDeliveryWithOSDRResponse =
        gson.fromJson(mockDeliveryResponse, DeliveryWithOSDRResponse.class);

    OperationalInfo operationalInfo = new OperationalInfo();
    operationalInfo.setState("DUMMY_NON_FINALIZED");

    // make po's finalized
    mockDeliveryWithOSDRResponse
        .getPurchaseOrders()
        .stream()
        .forEach(
            po -> {
              po.setOperationalInfo(operationalInfo);
              po.getLines()
                  .stream()
                  .forEach(
                      poLine -> {
                        poLine.setOperationalInfo(operationalInfo);
                      });
            });

    doReturn(mockDeliveryWithOSDRResponse)
        .when(mockGDMRestApiClient)
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

    Map<POPOLineKey, ReceivingCountSummary> deliveryFBQMap = new HashMap<>();
    ReceivingCountSummary receivingCountSummary = new ReceivingCountSummary();
    receivingCountSummary.setTotalFBQty(810);
    receivingCountSummary.setTotalFBQtyUOM("ZA");

    deliveryFBQMap.put(new POPOLineKey("9164390046", 1), receivingCountSummary);
    deliveryFBQMap.put(new POPOLineKey("9164390047", 1), receivingCountSummary);

    doReturn(deliveryFBQMap)
        .when(osdrRecordCountAggregator)
        .enrichWithGDMData(any(DeliveryWithOSDRResponse.class));

    Receipt mockReceipt = new Receipt();
    mockReceipt.setQuantity(81);
    mockReceipt.setQuantityUom("ZA");
    mockReceipt.setEachQty(486);
    mockReceipt.setVnpkQty(6);
    mockReceipt.setWhpkQty(6);

    List<Receipt> mockReceiptList = new ArrayList<>();
    mockReceiptList.add(mockReceipt);

    doReturn(mockReceiptList)
        .when(mockReceiptService)
        .findByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyLong(), anyString(), anyInt());

    Receipt savedReceipt = new Receipt();
    savedReceipt.setVersion(1);
    doReturn(savedReceipt).when(mockReceiptService).saveAndFlushReceipt(any(Receipt.class));

    Map<String, Object> mockHeaders =
        ReceivingUtils.getForwardablHeader(ReceivingUtils.getHeaders());

    when(witronDeliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.of(mockDeliveryMetaData));
    doReturn(mockDeliveryMetaData)
        .when(witronDeliveryMetaDataService)
        .updateDeliveryMetaData(any(DeliveryMetaData.class), any(DeliveryWithOSDRResponse.class));

    DeliveryWithOSDRResponse deliveryWithOSDRResponse =
        deliveryWithOSDRResponseBuilder.build(9967271326L, mockHeaders, true, null);

    assertEquals(deliveryWithOSDRResponse.getPurchaseOrders().size(), 2);
    assertEquals(deliveryWithOSDRResponse.getPurchaseOrders().get(0).getLines().size(), 3);
    assertEquals(
        deliveryWithOSDRResponse
            .getPurchaseOrders()
            .get(0)
            .getLines()
            .get(0)
            .getFreightBillQty()
            .intValue(),
        810);
    assertEquals(
        deliveryWithOSDRResponse
            .getPurchaseOrders()
            .get(0)
            .getLines()
            .get(0)
            .getOsdr()
            .getShortageQty()
            .intValue(),
        810);
    assertEquals(
        deliveryWithOSDRResponse
            .getPurchaseOrders()
            .get(0)
            .getLines()
            .get(0)
            .getOsdr()
            .getShortageUOM(),
        "ZA");
    assertEquals(
        deliveryWithOSDRResponse
            .getPurchaseOrders()
            .get(0)
            .getLines()
            .get(0)
            .getOsdr()
            .getShortageReasonCode(),
        "S10");

    verify(osdrRecordCountAggregator, atLeastOnce())
        .enrichWithGDMData(any(DeliveryWithOSDRResponse.class));

    verify(mockGDMRestApiClient, times(1)).getDelivery(anyLong(), any(Map.class));
    verify(mockAsyncDamageRestApiClient, times(1)).findDamagesByDelivery(anyLong(), any(Map.class));
    verify(mockAsyncFitRestApiClient, times(1))
        .findProblemCountByDelivery(anyLong(), any(Map.class));
  }

  @Test
  public void testGetDeliveryWithOsdr_exception() throws Exception {
    when(witronDeliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.empty());

    doReturn(mockDeliveryMetaData)
        .when(witronDeliveryMetaDataService)
        .createDeliveryMetaData(any(DeliveryWithOSDRResponse.class));

    doReturn(gson.fromJson(readFile("gdm_v3_getDelivery.json"), DeliveryWithOSDRResponse.class))
        .when(mockGDMRestApiClient)
        .getDelivery(anyLong(), any(Map.class));

    Optional<ProblemCountByDeliveryResponse> mockProblemCountByDeliveryResponse =
        Optional.of(
            gson.fromJson(
                readFile("fit_getProblemCntByDelivery_mock_response.json"),
                ProblemCountByDeliveryResponse.class));
    doReturn(CompletableFuture.completedFuture(mockProblemCountByDeliveryResponse))
        .when(mockAsyncFitRestApiClient)
        .findProblemCountByDelivery(anyLong(), any(Map.class));

    Type type = new TypeToken<List<DamageDeliveryInfo>>() {}.getType();
    Optional<List<DamageDeliveryInfo>> mockDamageDeliveryInfoList =
        Optional.of(
            gson.fromJson(readFile("damage_getDamageCountByDelivery_mock_response.json"), type));
    doReturn(CompletableFuture.completedFuture(mockDamageDeliveryInfoList))
        .when(mockAsyncDamageRestApiClient)
        .findDamagesByDelivery(anyLong(), any(Map.class));

    ReceivingCountSummary receivingCountSummary = new ReceivingCountSummary();
    receivingCountSummary.setTotalFBQty(810);
    receivingCountSummary.setTotalFBQtyUOM("ZA");
    Map<POPOLineKey, ReceivingCountSummary> deliveryFBQMap = new HashMap<>();
    deliveryFBQMap.put(new POPOLineKey("9164390046", 1), receivingCountSummary);
    deliveryFBQMap.put(new POPOLineKey("9164390047", 1), receivingCountSummary);
    doReturn(deliveryFBQMap)
        .when(osdrRecordCountAggregator)
        .enrichWithGDMData(any(DeliveryWithOSDRResponse.class));

    doThrow(
            new ReceivingException(
                ReceivingException.GET_DELIVERY_WITH_OSDR_ERROR,
                HttpStatus.INTERNAL_SERVER_ERROR,
                ReceivingException.GET_DELIVERY_WITH_OSDR_ERROR_CODE,
                ReceivingException.GET_DELIVERY_WITH_OSDR_ERROR_HEADER))
        .when(osdrRecordCountAggregator)
        .enrichDamageCnt(any(), any());

    try {
      DeliveryWithOSDRResponse deliveryWithOSDRResponse =
          deliveryWithOSDRResponseBuilder.build(
              9967271326L,
              ReceivingUtils.getForwardablHeader(ReceivingUtils.getHeaders()),
              true,
              null);
    } catch (ReceivingException receivingException) {
      assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, receivingException.getHttpStatus());
      assertEquals(
          "There were issues loading this information. Report this to your supervisor if it continues.",
          receivingException.getErrorResponse().getErrorMessage());
      assertEquals("GetDeliveryOSDRData", receivingException.getErrorResponse().getErrorCode());
      assertEquals(
          "Could not load information", receivingException.getErrorResponse().getErrorHeader());
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void filterDeliveryResponseForPo_nulls() {
    deliveryWithOSDRResponseBuilder.filterDeliveryResponseForPo(null, null);
  }

  @Test
  public void filterDeliveryResponseForPo_emptyPo() {
    deliveryWithOSDRResponseBuilder.filterDeliveryResponseForPo("", null);
  }

  @Test
  public void filterDeliveryResponseForPo_PO_notInList() {

    DeliveryWithOSDRResponse deliveryWithOSDRResponse = new DeliveryWithOSDRResponse();
    List<PurchaseOrderWithOSDRResponse> purchaseOrderWithOSDRResponses = new ArrayList<>();
    purchaseOrderWithOSDRResponses.add(createPo("11111"));
    purchaseOrderWithOSDRResponses.add(createPo("22222"));
    purchaseOrderWithOSDRResponses.add(createPo("333333"));

    deliveryWithOSDRResponseBuilder.filterDeliveryResponseForPo("555555", deliveryWithOSDRResponse);
    final List<PurchaseOrderWithOSDRResponse> purchaseOrdersResult =
        deliveryWithOSDRResponse.getPurchaseOrders();
    final int size = purchaseOrdersResult.size();
    assertEquals(size, 1);
    assertNull(purchaseOrdersResult.get(0));
  }

  @Test
  public void filterDeliveryResponseForPo_1PO() {

    DeliveryWithOSDRResponse deliveryWithOSDRResponse = new DeliveryWithOSDRResponse();
    List<PurchaseOrderWithOSDRResponse> purchaseOrderWithOSDRResponses = new ArrayList<>();
    final String poNumber = "0000333333";
    purchaseOrderWithOSDRResponses.add(createPo(poNumber));
    deliveryWithOSDRResponse.setPurchaseOrders(purchaseOrderWithOSDRResponses);

    deliveryWithOSDRResponseBuilder.filterDeliveryResponseForPo(poNumber, deliveryWithOSDRResponse);
    final List<PurchaseOrderWithOSDRResponse> purchaseOrdersResult =
        deliveryWithOSDRResponse.getPurchaseOrders();
    final int size = purchaseOrdersResult.size();

    assertEquals(size, 1);
    assertNotNull(purchaseOrdersResult.get(0));
  }

  @Test
  public void filterDeliveryResponseForPo_MoreThan1PO() {

    final String poNumber3 = "333333";
    DeliveryWithOSDRResponse deliveryWithOSDRResponse = new DeliveryWithOSDRResponse();
    List<PurchaseOrderWithOSDRResponse> purchaseOrderWithOSDRResponses = new ArrayList<>();
    purchaseOrderWithOSDRResponses.add(createPo("11111"));
    purchaseOrderWithOSDRResponses.add(createPo("22222"));
    purchaseOrderWithOSDRResponses.add(createPo(poNumber3));
    deliveryWithOSDRResponse.setPurchaseOrders(purchaseOrderWithOSDRResponses);

    deliveryWithOSDRResponseBuilder.filterDeliveryResponseForPo(
        poNumber3, deliveryWithOSDRResponse);
    final List<PurchaseOrderWithOSDRResponse> purchaseOrdersResult =
        deliveryWithOSDRResponse.getPurchaseOrders();
    final int size = purchaseOrdersResult.size();

    assertEquals(size, 1);
    assertNotNull(purchaseOrdersResult.get(0));
  }

  private PurchaseOrderWithOSDRResponse createPo(String poNumber) {
    final PurchaseOrderWithOSDRResponse po1 = new PurchaseOrderWithOSDRResponse();
    po1.setPoNumber(poNumber);
    return po1;
  }
}
