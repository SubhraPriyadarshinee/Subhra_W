package com.walmart.move.nim.receiving.core.event.processor;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.VNPK;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;
import static org.testng.Assert.*;
import static org.testng.Assert.assertEquals;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.event.processor.summary.DefaultReceiptSummaryProcessor;
import com.walmart.move.nim.receiving.core.mock.data.MockGdmResponse;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.repositories.ReceiptCustomRepository;
import com.walmart.move.nim.receiving.core.service.DeliveryServiceRetryableImpl;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DefaultReceiptSummaryProcessorTest {

  @Mock private ReceiptService receiptService;
  @Mock private ReceiptCustomRepository receiptCustomRepository;
  @Mock private DeliveryServiceRetryableImpl deliveryService;
  @InjectMocks private DefaultReceiptSummaryProcessor defaultReceiptSummaryProcessor;
  @Mock private AppConfig appConfig;
  private Gson gson;
  @Mock private TenantSpecificConfigReader configUtils;
  String finalizedPoNumber = "4211300997";
  String notFinalizedPoNumber = "8926870002";

  @BeforeClass
  public void setup() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32818);
    this.gson = new Gson();
    ReflectionTestUtils.setField(defaultReceiptSummaryProcessor, "gson", this.gson);
  }

  @AfterMethod
  public void resetMocks() {
    reset(receiptService, receiptCustomRepository, appConfig, deliveryService, configUtils);
  }

  @Test
  private void testReceivedQtySummaryInVnpkByDeliveryReturnsReceipts() {
    Long deliveryNumber = 1223232L;
    when(receiptCustomRepository.receivedQtySummaryInVnpkByDelivery(anyLong()))
        .thenReturn(getMockReceiptSummaryResponse());

    List<ReceiptSummaryResponse> response =
        defaultReceiptSummaryProcessor.receivedQtySummaryInVnpkByDelivery(deliveryNumber);
    assertNotNull(response);
    assertTrue(response.size() > 0);

    verify(receiptCustomRepository, times(1)).receivedQtySummaryInVnpkByDelivery(anyLong());
  }

  @Test
  private void testReceivedQtySummaryInVnpkByDeliveryReturnsEmptyReceipts() {
    Long deliveryNumber = 1223232L;
    List<ReceiptSummaryResponse> receiptSummaryResponseList = new ArrayList<>();
    when(receiptCustomRepository.receivedQtySummaryInVnpkByDelivery(anyLong()))
        .thenReturn(receiptSummaryResponseList);

    List<ReceiptSummaryResponse> response =
        defaultReceiptSummaryProcessor.receivedQtySummaryInVnpkByDelivery(deliveryNumber);
    assertNotNull(response);
    assertEquals(response.size(), 0);

    verify(receiptCustomRepository, times(1)).receivedQtySummaryInVnpkByDelivery(anyLong());
  }

  @Test
  private void testGetDeliveryDetailsFromGDMReturnsSuccessResponse()
      throws ReceivingException, IOException {
    when(appConfig.getGdmBaseUrl()).thenReturn("gdmServer");
    when(deliveryService.getDeliveryByURI(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(MockGdmResponse.getDeliveryDetailsByUriIncludesDummyPoReturnsSuccessResponse());

    String response =
        defaultReceiptSummaryProcessor.getDeliveryDetailsFromGDM(
            323232323L, MockHttpHeaders.getHeaders());
    assertNotNull(response);
    verify(appConfig, times(1)).getGdmBaseUrl();
    verify(deliveryService, times(1)).getDeliveryByURI(any(URI.class), any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingException.class)
  private void testGetDeliveryDetailsFromGDMReturnsErrorResponse()
      throws ReceivingException, IOException {
    when(appConfig.getGdmBaseUrl()).thenReturn("gdmServer");
    doThrow(new ReceivingException("", HttpStatus.INTERNAL_SERVER_ERROR))
        .when(deliveryService)
        .getDeliveryByURI(any(URI.class), any(HttpHeaders.class));

    defaultReceiptSummaryProcessor.getDeliveryDetailsFromGDM(
        323232323L, MockHttpHeaders.getHeaders());

    verify(deliveryService, times(1)).getDeliveryByURI(any(URI.class), any(HttpHeaders.class));
  }

  @Test
  private void testGetReceivedQtByPo() {
    Long deliveryNumber = 1122332L;
    when(receiptService.getReceivedQtySummaryByPoInVnpk(deliveryNumber))
        .thenReturn(getMockReceiptSummaryResponse());

    List<ReceiptSummaryResponse> response =
        defaultReceiptSummaryProcessor.getReceivedQtyByPo(deliveryNumber);
    assertNotNull(response);
    assertTrue(response.size() > 0);

    verify(receiptService, times(1)).getReceivedQtySummaryByPoInVnpk(anyLong());
  }

  @Test
  private void testGetReceivedQtByPoLine() {
    Long deliveryNumber = 1122332L;
    String purchaseReferenceNumber = "33232323";
    when(receiptService.getReceivedQtySummaryByPoLineInVnpk(
            deliveryNumber, purchaseReferenceNumber))
        .thenReturn(getMockReceiptSummaryResponse());

    List<ReceiptSummaryResponse> response =
        defaultReceiptSummaryProcessor.getReceivedQtyByPoLine(
            deliveryNumber, purchaseReferenceNumber);
    assertNotNull(response);
    assertTrue(response.size() > 0);

    verify(receiptService, times(1)).getReceivedQtySummaryByPoLineInVnpk(anyLong(), anyString());
  }

  @Test
  private void
      testPopulateDeliveryReceiptsSummaryPosReceivedIn_MasterReceipts_Finalized_And_NotFinalized()
          throws IOException {
    GdmPOLineResponse gdmPOLineResponse =
        gson.fromJson(
            MockGdmResponse.getDeliveryDetailsByUriIncludesDummyPoReturnsSuccessResponse(),
            GdmPOLineResponse.class);
    List<DeliveryDocument> gdmDeliveryDocumentList = gdmPOLineResponse.getDeliveryDocuments();
    List<ReceiptSummaryResponse> receiptSummaryResponseList = getMockReceiptSummaryResponse();
    HashMap<String, Receipt> finalizedPoReceiptMap = getFinalizedPoReceiptMap();
    List<ReceiptSummaryQtyByPo> response =
        defaultReceiptSummaryProcessor.populateReceiptsSummaryByPo(
            gdmDeliveryDocumentList, receiptSummaryResponseList, finalizedPoReceiptMap);
    assertNotNull(response);
    assertEquals(gdmDeliveryDocumentList.size(), 12);
    assertTrue(response.size() > 0);
    assertEquals(response.size(), 11);

    List<ReceiptSummaryQtyByPo> nonZeroReceivedQtyList =
        response
            .stream()
            .parallel()
            .filter(receiptSummaryQtyByPo -> receiptSummaryQtyByPo.getReceivedQty() > 0)
            .collect(Collectors.toList());
    assertNotEquals(nonZeroReceivedQtyList.size(), response.size());

    // Master Receipt PO
    final ReceiptSummaryQtyByPo receiptSummaryQtyByPo_finalized =
        response
            .parallelStream()
            .filter(r -> r.getPurchaseReferenceNumber().equalsIgnoreCase(finalizedPoNumber))
            .findFirst()
            .get();
    assertEquals(receiptSummaryQtyByPo_finalized.isPoFinalized(), true);
    assertEquals(receiptSummaryQtyByPo_finalized.getFreightTermCode(), "COLL");

    // NOT Master Receipt PO 9111741604
    final ReceiptSummaryQtyByPo receiptSummaryQtyByPo_notFinalized =
        response
            .parallelStream()
            .filter(r -> r.getPurchaseReferenceNumber().equalsIgnoreCase("8926870002"))
            .findFirst()
            .get();
    assertEquals(receiptSummaryQtyByPo_notFinalized.isPoFinalized(), false);
    assertEquals(receiptSummaryQtyByPo_notFinalized.getFreightTermCode(), "COLL");
  }

  private List<Receipt> getMasterReceipts() {
    Receipt r = new Receipt();
    r.setPurchaseReferenceNumber(finalizedPoNumber);
    r.setFinalizeTs(new Date());
    r.setFinalizedUserId("k0c0e5k");

    ArrayList<Receipt> receipts = new ArrayList<>();
    receipts.add(r);

    return receipts;
  }

  private HashMap<String, Receipt> getFinalizedPoReceiptMap() {
    Receipt r = new Receipt();
    r.setPurchaseReferenceNumber(finalizedPoNumber);
    r.setFinalizeTs(new Date());
    r.setFinalizedUserId("k0c0e5k");

    HashMap<String, Receipt> finalizedPoReceiptMap = new HashMap<>();
    finalizedPoReceiptMap.put(finalizedPoNumber, r);

    return finalizedPoReceiptMap;
  }

  @Test
  private void
      testPopulateDeliveryReceiptsSummarySomePosReceivedInReceivingAndIgnoreDummyPOReceipts()
          throws IOException {
    GdmPOLineResponse gdmPOLineResponse =
        gson.fromJson(
            MockGdmResponse.getDeliveryDetailsByUriIncludesDummyPoReturnsSuccessResponse(),
            GdmPOLineResponse.class);
    List<DeliveryDocument> deliveryDocumentList = gdmPOLineResponse.getDeliveryDocuments();
    List<ReceiptSummaryResponse> receiptSummaryResponseList = getMockReceiptSummaryResponse();

    List<ReceiptSummaryQtyByPo> response =
        defaultReceiptSummaryProcessor.populateReceiptsSummaryByPo(
            deliveryDocumentList, receiptSummaryResponseList, null);
    assertNotNull(response);
    assertEquals(deliveryDocumentList.size(), 12);
    assertTrue(response.size() > 0);
    assertEquals(response.size(), 11);

    List<ReceiptSummaryQtyByPo> nonZeroReceivedQtyList =
        response
            .stream()
            .parallel()
            .filter(receiptSummaryQtyByPo -> receiptSummaryQtyByPo.getReceivedQty() > 0)
            .collect(Collectors.toList());
    assertNotEquals(nonZeroReceivedQtyList.size(), response.size());
  }

  @Test
  private void
      testPopulateDeliveryReceiptsSummaryAllPosReceivedInReceivingAndIgnoreDummyPOReceipts()
          throws IOException {
    GdmPOLineResponse gdmPOLineResponse =
        gson.fromJson(
            MockGdmResponse.getDeliveryDetailsByUriIncludesDummyPoReturnsSuccessResponse(),
            GdmPOLineResponse.class);
    List<DeliveryDocument> deliveryDocumentList = gdmPOLineResponse.getDeliveryDocuments();
    List<ReceiptSummaryResponse> receiptSummaryResponseList =
        getMockReceiptSummaryResponseMultiplePos();

    List<ReceiptSummaryQtyByPo> response =
        defaultReceiptSummaryProcessor.populateReceiptsSummaryByPo(
            deliveryDocumentList, receiptSummaryResponseList, null);
    assertNotNull(response);
    assertEquals(deliveryDocumentList.size(), 12);
    assertTrue(response.size() > 0);
    assertEquals(response.size(), 11);

    List<ReceiptSummaryQtyByPo> nonZeroReceivedQtyList =
        response
            .stream()
            .parallel()
            .filter(receiptSummaryQtyByPo -> receiptSummaryQtyByPo.getReceivedQty() > 0)
            .collect(Collectors.toList());
    assertEquals(nonZeroReceivedQtyList.size(), response.size());
  }

  @Test
  private void testPopulateDeliveryReceiptsSummaryByPoLineReturnsSuccess_SinglePoSinglePoLine()
      throws IOException, ReceivingException {
    String purchaseReferenceNumber = "4211300997";
    GdmPOLineResponse gdmPOLineResponse =
        gson.fromJson(
            MockGdmResponse.getDeliveryDetailsByUriIncludesDummyPoReturnsSuccessResponse(),
            GdmPOLineResponse.class);
    List<DeliveryDocument> deliveryDocumentList = gdmPOLineResponse.getDeliveryDocuments();
    DeliveryDocument deliveryDocument =
        defaultReceiptSummaryProcessor.getDeliveryDocumentByPo(
            deliveryDocumentList, purchaseReferenceNumber);
    List<ReceiptSummaryResponse> receiptSummaryResponseList = getMockReceiptSummaryResponse();

    List<ReceiptSummaryQtyByPoLine> response =
        defaultReceiptSummaryProcessor.populateReceiptsSummaryByPoLine(
            deliveryDocument, receiptSummaryResponseList);
    assertNotNull(response);
    assertEquals(response.size(), 1);
  }

  @Test
  private void testPopulateDeliveryReceiptsSummaryByPoLine_POCON_purchaseReferenceType()
      throws IOException, ReceivingException {
    String purchaseReferenceNumber = "019749106";
    File resource = new ClassPathResource("GdmResponse_GetDelivery_V2_POCON.json").getFile();
    final String gdmGetDeliveryV2 = new String(Files.readAllBytes(resource.toPath()));
    GdmPOLineResponse gdmPOLineResponse = gson.fromJson(gdmGetDeliveryV2, GdmPOLineResponse.class);
    List<DeliveryDocument> deliveryDocumentList = gdmPOLineResponse.getDeliveryDocuments();
    DeliveryDocument deliveryDocument =
        defaultReceiptSummaryProcessor.getDeliveryDocumentByPo(
            deliveryDocumentList, purchaseReferenceNumber);
    List<ReceiptSummaryResponse> receiptSummaryResponseList = getMockReceiptSummaryResponse_POCON();

    List<ReceiptSummaryQtyByPoLine> response =
        defaultReceiptSummaryProcessor.populateReceiptsSummaryByPoLine(
            deliveryDocument, receiptSummaryResponseList);
    assertNotNull(response);
    assertEquals(response.get(0).getReceivedQty().intValue(), 0);
  }

  @Test
  private void testPopulateDeliveryReceiptsSummaryByPoLineReturnsSuccess_SinglePoMutiplePoLine()
      throws IOException, ReceivingException {
    String purchaseReferenceNumber = "4211300997";
    GdmPOLineResponse gdmPOLineResponse =
        gson.fromJson(
            MockGdmResponse.getDeliveryDetailsByUriIncludesDummyPoReturnsSuccessResponse(),
            GdmPOLineResponse.class);
    List<DeliveryDocument> deliveryDocumentList = gdmPOLineResponse.getDeliveryDocuments();
    DeliveryDocument deliveryDocument =
        defaultReceiptSummaryProcessor.getDeliveryDocumentByPo(
            deliveryDocumentList, purchaseReferenceNumber);
    List<ReceiptSummaryResponse> receiptSummaryResponseList =
        getMockReceiptSummaryResponseMultiplePoLines();

    List<ReceiptSummaryQtyByPoLine> response =
        defaultReceiptSummaryProcessor.populateReceiptsSummaryByPoLine(
            deliveryDocument, receiptSummaryResponseList);
    assertNotNull(response);
    assertTrue(response.size() > 0);
  }

  @Test
  private void
      testPopulateDeliveryReceiptsSummaryByPoLineReturnsSuccess_MultiPo_Multi_PoLine_totalFob()
          throws IOException, ReceivingException {
    String purchaseReferenceNumber = "019749106";
    File resource = new ClassPathResource("GdmResponse_GetDelivery_V2.json").getFile();
    final String gdmGetDeliveryV2 = new String(Files.readAllBytes(resource.toPath()));
    GdmPOLineResponse gdmPOLineResponse = gson.fromJson(gdmGetDeliveryV2, GdmPOLineResponse.class);
    List<DeliveryDocument> deliveryDocumentList = gdmPOLineResponse.getDeliveryDocuments();
    DeliveryDocument deliveryDocument =
        defaultReceiptSummaryProcessor.getDeliveryDocumentByPo(
            deliveryDocumentList, purchaseReferenceNumber);
    List<ReceiptSummaryResponse> receiptSummaryResponseList =
        getMockReceiptSummaryResponseMultiplePoLines();

    List<ReceiptSummaryQtyByPoLine> response =
        defaultReceiptSummaryProcessor.populateReceiptsSummaryByPoLine(
            deliveryDocument, receiptSummaryResponseList);
    assertNotNull(response);
    assertEquals(response.size(), 3);

    final ReceiptSummaryQtyByPoLine receiptSummaryQtyByPoLine1 = response.get(0);
    assertEquals(receiptSummaryQtyByPoLine1.getLineNumber().intValue(), 1);
    final Integer freightBillQty1 = receiptSummaryQtyByPoLine1.getFreightBillQty();
    assertEquals(freightBillQty1.intValue(), 500);

    final ReceiptSummaryQtyByPoLine receiptSummaryQtyByPoLine2 = response.get(1);
    assertEquals(receiptSummaryQtyByPoLine2.getLineNumber().intValue(), 2);
    final Integer freightBillQty2 = receiptSummaryQtyByPoLine2.getFreightBillQty();
    assertEquals(freightBillQty2.intValue(), 600);

    final ReceiptSummaryQtyByPoLine receiptSummaryQtyByPoLine3 = response.get(2);
    assertEquals(receiptSummaryQtyByPoLine3.getLineNumber().intValue(), 3);
    final Integer freightBillQty3 = receiptSummaryQtyByPoLine3.getFreightBillQty();
    assertEquals(freightBillQty3.intValue(), 500);
  }

  @Test
  private void
      testPopulateDeliveryReceiptsSummaryByPoLineReturnsSuccess_MultiPo_Multi_PoLine_FbqIsNullByPoLineLevel()
          throws IOException, ReceivingException {
    String purchaseReferenceNumber = "019749106";
    File resource =
        new ClassPathResource("GdmResponse_GetDelivery_V2_NullFbq_ByPoLine.json").getFile();
    final String gdmGetDeliveryV2 = new String(Files.readAllBytes(resource.toPath()));
    GdmPOLineResponse gdmPOLineResponse = gson.fromJson(gdmGetDeliveryV2, GdmPOLineResponse.class);
    List<DeliveryDocument> deliveryDocumentList = gdmPOLineResponse.getDeliveryDocuments();
    DeliveryDocument deliveryDocument =
        defaultReceiptSummaryProcessor.getDeliveryDocumentByPo(
            deliveryDocumentList, purchaseReferenceNumber);
    List<ReceiptSummaryResponse> receiptSummaryResponseList =
        getMockReceiptSummaryResponseMultiplePoLines();

    List<ReceiptSummaryQtyByPoLine> response =
        defaultReceiptSummaryProcessor.populateReceiptsSummaryByPoLine(
            deliveryDocument, receiptSummaryResponseList);
    assertNotNull(response);
    assertEquals(response.size(), 3);

    final ReceiptSummaryQtyByPoLine receiptSummaryQtyByPoLine1 = response.get(0);
    assertEquals(receiptSummaryQtyByPoLine1.getLineNumber().intValue(), 1);
    final Integer freightBillQty1 = receiptSummaryQtyByPoLine1.getFreightBillQty();
    assertEquals(freightBillQty1.intValue(), 0);

    final ReceiptSummaryQtyByPoLine receiptSummaryQtyByPoLine2 = response.get(1);
    assertEquals(receiptSummaryQtyByPoLine2.getLineNumber().intValue(), 2);
    final Integer freightBillQty2 = receiptSummaryQtyByPoLine2.getFreightBillQty();
    assertEquals(freightBillQty2.intValue(), 0);

    final ReceiptSummaryQtyByPoLine receiptSummaryQtyByPoLine3 = response.get(2);
    assertEquals(receiptSummaryQtyByPoLine3.getLineNumber().intValue(), 3);
    final Integer freightBillQty3 = receiptSummaryQtyByPoLine3.getFreightBillQty();
    assertEquals(freightBillQty3.intValue(), 0);
  }

  @Test
  private void testGetReceiptsSummaryByPoResponse() throws IOException {
    Long deliveryNumber = 23232323L;
    GdmPOLineResponse gdmPOLineResponse =
        gson.fromJson(
            MockGdmResponse.getDeliveryDetailsByUriIncludesDummyPoReturnsSuccessResponse(),
            GdmPOLineResponse.class);
    List<ReceiptSummaryResponse> receiptSummaryResponseList =
        getMockReceiptSummaryResponseMultiplePos();
    List<ReceiptSummaryQtyByPo> receiptSummaryQtyByPos =
        defaultReceiptSummaryProcessor.populateReceiptsSummaryByPo(
            gdmPOLineResponse.getDeliveryDocuments(), receiptSummaryResponseList, null);

    ReceiptSummaryQtyByPoResponse receiptSummaryQtyByPoResponse =
        defaultReceiptSummaryProcessor.getReceiptsSummaryByPoResponse(
            deliveryNumber, gdmPOLineResponse, receiptSummaryQtyByPos);

    assertNotNull(receiptSummaryQtyByPoResponse);
    assertNotNull(receiptSummaryQtyByPoResponse.getDeliveryNumber());
    assertTrue(receiptSummaryQtyByPoResponse.getSummary().size() > 0);
    assertNotNull(receiptSummaryQtyByPoResponse.getFreightBillQuantity());
    assertNotNull(receiptSummaryQtyByPoResponse.getReceivedQty());
    assertNotNull(receiptSummaryQtyByPoResponse.getReceivedQtyUom());
  }

  @Test
  private void testGetReceiptsSummaryByPoResponse_totalBolFbq() throws Exception {
    Long deliveryNumber = 23232323L;
    File resource = new ClassPathResource("GdmResponse_GetDelivery_V2.json").getFile();
    final String gdmGetDeliveryV2 = new String(Files.readAllBytes(resource.toPath()));
    GdmPOLineResponse gdmPOLineResponse = gson.fromJson(gdmGetDeliveryV2, GdmPOLineResponse.class);
    List<ReceiptSummaryResponse> receiptSummaryResponseList =
        getMockReceiptSummaryResponseMultiplePos();
    List<ReceiptSummaryQtyByPo> receiptSummaryQtyByPos =
        defaultReceiptSummaryProcessor.populateReceiptsSummaryByPo(
            gdmPOLineResponse.getDeliveryDocuments(), receiptSummaryResponseList, null);

    ReceiptSummaryQtyByPoResponse receiptSummaryQtyByPoResponse =
        defaultReceiptSummaryProcessor.getReceiptsSummaryByPoResponse(
            deliveryNumber, gdmPOLineResponse, receiptSummaryQtyByPos);

    assertNotNull(receiptSummaryQtyByPoResponse);
    assertNotNull(receiptSummaryQtyByPoResponse.getDeliveryNumber());
    final List<ReceiptSummaryQtyByPo> summary = receiptSummaryQtyByPoResponse.getSummary();
    final int size = summary.size();
    assertTrue(size > 0);
    final ReceiptSummaryQtyByPo receiptSummaryQtyByPo1 = summary.get(0);
    final Integer freightBillQuantity_poLevel = receiptSummaryQtyByPo1.getFreightBillQuantity();
    assertEquals(freightBillQuantity_poLevel.intValue(), 1500);
    final Integer totalBolFbq_poLevel = receiptSummaryQtyByPo1.getTotalBolFbq();
    assertEquals(totalBolFbq_poLevel.intValue(), 1600);

    final Integer freightBillQuantity_deliveryLevel =
        receiptSummaryQtyByPoResponse.getFreightBillQuantity();
    assertNotNull(freightBillQuantity_deliveryLevel);
    assertEquals(freightBillQuantity_deliveryLevel.intValue(), 3000);
    assertNotNull(receiptSummaryQtyByPoResponse.getReceivedQty());
    assertNotNull(receiptSummaryQtyByPoResponse.getReceivedQtyUom());
    final Integer totalBolFbq_deliveryLevel = receiptSummaryQtyByPoResponse.getTotalBolFbq();
    assertNotNull(totalBolFbq_deliveryLevel);
    assertEquals(totalBolFbq_deliveryLevel.intValue(), 3200);
  }

  @Test
  private void testGetReceiptsSummaryByPoReturnsSummaryReceipts()
      throws ReceivingException, IOException {
    Long deliveryNumber = 22322323L;
    when(appConfig.getGdmBaseUrl()).thenReturn("gdmServer");
    when(deliveryService.getDeliveryByURI(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(MockGdmResponse.getDeliveryDetailsByUriIncludesDummyPoReturnsSuccessResponse());
    when(receiptService.getReceivedQtySummaryByPoInVnpk(deliveryNumber))
        .thenReturn(getMockReceiptSummaryResponse());

    ReceiptSummaryQtyByPoResponse receiptSummaryQtyByPoResponse =
        defaultReceiptSummaryProcessor.getReceiptsSummaryByPo(
            deliveryNumber, MockHttpHeaders.getHeaders());
    assertNotNull(receiptSummaryQtyByPoResponse);
    assertNotNull(receiptSummaryQtyByPoResponse.getDeliveryNumber());
    assertTrue(receiptSummaryQtyByPoResponse.getSummary().size() > 0);
    assertNotNull(receiptSummaryQtyByPoResponse.getFreightBillQuantity());
    assertNotNull(receiptSummaryQtyByPoResponse.getReceivedQty());
    assertNotNull(receiptSummaryQtyByPoResponse.getReceivedQtyUom());

    verify(appConfig, times(1)).getGdmBaseUrl();
    verify(receiptService, times(1)).getReceivedQtySummaryByPoInVnpk(anyLong());
    verify(deliveryService, times(1)).getDeliveryByURI(any(URI.class), any(HttpHeaders.class));
  }

  @Test
  private void testGetReceiptsSummaryByPoReturnsSummaryReceipts_withMasterReceipts()
      throws ReceivingException, IOException {
    Long deliveryNumber = 22322323L;
    when(appConfig.getGdmBaseUrl()).thenReturn("gdmServer");
    when(deliveryService.getDeliveryByURI(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(MockGdmResponse.getDeliveryDetailsByUriIncludesDummyPoReturnsSuccessResponse());
    when(receiptService.getReceivedQtySummaryByPoInVnpk(deliveryNumber))
        .thenReturn(getMockReceiptSummaryResponse());

    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());

    final List<Receipt> masterReceipts = getMasterReceipts();
    doReturn(masterReceipts).when(receiptService).findFinalizedReceiptsFor(anyLong());

    ReceiptSummaryQtyByPoResponse receiptSummaryQtyByPoResponse =
        defaultReceiptSummaryProcessor.getReceiptsSummaryByPo(
            deliveryNumber, MockHttpHeaders.getHeaders());

    verify(appConfig, times(1)).getGdmBaseUrl();
    verify(receiptService, times(1)).getReceivedQtySummaryByPoInVnpk(anyLong());
    verify(deliveryService, times(1)).getDeliveryByURI(any(URI.class), any(HttpHeaders.class));

    assertNotNull(receiptSummaryQtyByPoResponse);
    assertNotNull(receiptSummaryQtyByPoResponse.getDeliveryNumber());
    final List<ReceiptSummaryQtyByPo> summary = receiptSummaryQtyByPoResponse.getSummary();
    assertTrue(summary.size() > 0);
    assertNotNull(receiptSummaryQtyByPoResponse.getFreightBillQuantity());
    assertNotNull(receiptSummaryQtyByPoResponse.getReceivedQty());
    assertNotNull(receiptSummaryQtyByPoResponse.getReceivedQtyUom());
    assertEquals(receiptSummaryQtyByPoResponse.getDeliveryTypeCode(), "DROP");
    // finalized receipt
    final ReceiptSummaryQtyByPo receiptSummaryQtyByPo_finalized =
        summary
            .parallelStream()
            .filter(s -> finalizedPoNumber.equalsIgnoreCase(s.getPurchaseReferenceNumber()))
            .findFirst()
            .get();
    assertTrue(receiptSummaryQtyByPo_finalized.isPoFinalized());
    assertEquals(receiptSummaryQtyByPo_finalized.getFreightTermCode(), "COLL");

    // Not finalized receipt
    final ReceiptSummaryQtyByPo receiptSummaryQtyByPo_notFinalized =
        summary
            .parallelStream()
            .filter(s -> notFinalizedPoNumber.equalsIgnoreCase(s.getPurchaseReferenceNumber()))
            .findFirst()
            .get();
    assertFalse(receiptSummaryQtyByPo_notFinalized.isPoFinalized());
    assertEquals(receiptSummaryQtyByPo_notFinalized.getFreightTermCode(), "COLL");
  }

  @Test
  private void testGetReceiptsSummaryByPoReturnsEmptySummaryReceipts()
      throws ReceivingException, IOException {
    Long deliveryNumber = 22322323L;
    when(appConfig.getGdmBaseUrl()).thenReturn("gdmServer");
    when(deliveryService.getDeliveryByURI(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(MockGdmResponse.getDeliveryDetailsByUriIncludesDummyPoReturnsSuccessResponse());
    when(receiptService.getReceivedQtySummaryByPoInVnpk(deliveryNumber))
        .thenReturn(Collections.emptyList());

    ReceiptSummaryQtyByPoResponse receiptSummaryQtyByPoResponse =
        defaultReceiptSummaryProcessor.getReceiptsSummaryByPo(
            deliveryNumber, MockHttpHeaders.getHeaders());
    assertNotNull(receiptSummaryQtyByPoResponse);
    assertNotNull(receiptSummaryQtyByPoResponse.getDeliveryNumber());
    assertEquals(receiptSummaryQtyByPoResponse.getSummary().size(), 11);
    assertNotNull(receiptSummaryQtyByPoResponse.getFreightBillQuantity());
    assertEquals(receiptSummaryQtyByPoResponse.getReceivedQty().intValue(), 0);
    assertNotNull(receiptSummaryQtyByPoResponse.getReceivedQtyUom());

    verify(appConfig, times(1)).getGdmBaseUrl();
    verify(receiptService, times(1)).getReceivedQtySummaryByPoInVnpk(anyLong());
    verify(deliveryService, times(1)).getDeliveryByURI(any(URI.class), any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingException.class)
  private void testGetReceiptsSummaryByPoReturnsErrorResponse()
      throws ReceivingException, IOException {
    Long deliveryNumber = 22322323L;
    when(appConfig.getGdmBaseUrl()).thenReturn("gdmServer");
    doThrow(new ReceivingException("", HttpStatus.INTERNAL_SERVER_ERROR))
        .when(deliveryService)
        .getDeliveryByURI(any(URI.class), any(HttpHeaders.class));

    defaultReceiptSummaryProcessor.getReceiptsSummaryByPo(
        deliveryNumber, MockHttpHeaders.getHeaders());
    verify(appConfig, times(1)).getGdmBaseUrl();
    verify(deliveryService, times(1)).getDeliveryByURI(any(URI.class), any(HttpHeaders.class));
  }

  @Test
  private void testGetReceiptsSummaryByPoLineReturnsSummaryReceipts()
      throws ReceivingException, IOException {
    Long deliveryNumber = 22322323L;
    String purchaseReferenceNumber = "4211300997";
    when(appConfig.getGdmBaseUrl()).thenReturn("gdmServer");
    when(deliveryService.getDeliveryByURI(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(MockGdmResponse.getDeliveryDetailsByUriIncludesDummyPoReturnsSuccessResponse());
    when(receiptService.getReceivedQtySummaryByPoLineInVnpk(
            deliveryNumber, purchaseReferenceNumber))
        .thenReturn(getMockReceiptSummaryResponse());

    ReceiptSummaryQtyByPoLineResponse receiptSummaryQtyByPoLineResponse =
        defaultReceiptSummaryProcessor.getReceiptsSummaryByPoLine(
            deliveryNumber, purchaseReferenceNumber, MockHttpHeaders.getHeaders());
    assertNotNull(receiptSummaryQtyByPoLineResponse);
    assertNotNull(receiptSummaryQtyByPoLineResponse.getPurchaseReferenceNumber());
    assertTrue(receiptSummaryQtyByPoLineResponse.getSummary().size() > 0);
    assertNotNull(receiptSummaryQtyByPoLineResponse.getReceivedQtyUom());

    verify(appConfig, times(1)).getGdmBaseUrl();
    verify(receiptService, times(1)).getReceivedQtySummaryByPoLineInVnpk(anyLong(), anyString());
    verify(deliveryService, times(1)).getDeliveryByURI(any(URI.class), any(HttpHeaders.class));
  }

  @Test
  private void testGetReceiptsSummaryByPoLineWithNoneReceivedReturnsSummaryWithReceivedZeroQty()
      throws ReceivingException, IOException {
    Long deliveryNumber = 22322323L;
    String purchaseReferenceNumber = "6506871436";
    when(appConfig.getGdmBaseUrl()).thenReturn("gdmServer");
    when(deliveryService.getDeliveryByURI(any(URI.class), any(HttpHeaders.class)))
        .thenReturn(MockGdmResponse.getDeliveryDetailsByUriIncludesDummyPoReturnsSuccessResponse());
    when(receiptService.getReceivedQtySummaryByPoLineInVnpk(
            deliveryNumber, purchaseReferenceNumber))
        .thenReturn(Collections.emptyList());

    ReceiptSummaryQtyByPoLineResponse receiptSummaryQtyByPoLineResponse =
        defaultReceiptSummaryProcessor.getReceiptsSummaryByPoLine(
            deliveryNumber, purchaseReferenceNumber, MockHttpHeaders.getHeaders());
    assertNotNull(receiptSummaryQtyByPoLineResponse);
    assertNotNull(receiptSummaryQtyByPoLineResponse.getPurchaseReferenceNumber());
    assertEquals(receiptSummaryQtyByPoLineResponse.getSummary().size(), 7);
    assertNotNull(receiptSummaryQtyByPoLineResponse.getReceivedQtyUom());

    verify(appConfig, times(1)).getGdmBaseUrl();
    verify(receiptService, times(1)).getReceivedQtySummaryByPoLineInVnpk(anyLong(), anyString());
    verify(deliveryService, times(1)).getDeliveryByURI(any(URI.class), any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingException.class)
  private void testGetReceiptsSummaryByPoLineReturnsErrorResponse() throws ReceivingException {
    Long deliveryNumber = 22322323L;
    String purchaseReferenceNumber = "23232323";
    when(appConfig.getGdmBaseUrl()).thenReturn("gdmServer");
    doThrow(new ReceivingException("", HttpStatus.INTERNAL_SERVER_ERROR))
        .when(deliveryService)
        .getDeliveryByURI(any(URI.class), any(HttpHeaders.class));

    defaultReceiptSummaryProcessor.getReceiptsSummaryByPoLine(
        deliveryNumber, purchaseReferenceNumber, MockHttpHeaders.getHeaders());
    verify(appConfig, times(1)).getGdmBaseUrl();
    verify(deliveryService, times(1)).getDeliveryByURI(any(URI.class), any(HttpHeaders.class));
  }

  @Test
  private void testGetDeliveryDocumentByPoReturnsSuccess() throws IOException, ReceivingException {
    GdmPOLineResponse gdmPOLineResponse =
        gson.fromJson(
            MockGdmResponse.getDeliveryDetailsByUriIncludesDummyPoReturnsSuccessResponse(),
            GdmPOLineResponse.class);
    List<DeliveryDocument> deliveryDocumentList = gdmPOLineResponse.getDeliveryDocuments();

    DeliveryDocument deliveryDocument =
        defaultReceiptSummaryProcessor.getDeliveryDocumentByPo(
            deliveryDocumentList, notFinalizedPoNumber);

    assertNotNull(deliveryDocument);
    assertEquals(deliveryDocument.getPurchaseReferenceNumber(), notFinalizedPoNumber);
  }

  @Test(expectedExceptions = ReceivingException.class)
  private void testGetDeliveryDocumentByPo_MissingPoInGdm() throws IOException, ReceivingException {
    String purchaseReferenceNumber = "8926870003";
    GdmPOLineResponse gdmPOLineResponse =
        gson.fromJson(
            MockGdmResponse.getDeliveryDetailsByUriIncludesDummyPoReturnsSuccessResponse(),
            GdmPOLineResponse.class);
    List<DeliveryDocument> deliveryDocumentList = gdmPOLineResponse.getDeliveryDocuments();

    defaultReceiptSummaryProcessor.getDeliveryDocumentByPo(
        deliveryDocumentList, purchaseReferenceNumber);
  }

  @Test(expectedExceptions = ReceivingException.class)
  private void testGetDeliveryDocumentByPo_MissingPoLinesInGdm()
      throws IOException, ReceivingException {
    String purchaseReferenceNumber = "1234567890";
    GdmPOLineResponse gdmPOLineResponse =
        gson.fromJson(
            MockGdmResponse.getDeliveryDetailsByUriIncludesDummyPoReturnsSuccessResponse(),
            GdmPOLineResponse.class);
    List<DeliveryDocument> deliveryDocumentList = gdmPOLineResponse.getDeliveryDocuments();

    defaultReceiptSummaryProcessor.getDeliveryDocumentByPo(
        deliveryDocumentList, purchaseReferenceNumber);
  }

  @Test
  private void testGetReceiptsSummaryByPoLineResponse() {
    String purchaseReferenceNumber = "1234567890";
    List<ReceiptSummaryQtyByPoLine> receiptSummaryQtyByPoLines = new ArrayList<>();
    ReceiptSummaryQtyByPoLine receiptSummaryQtyByPoLine = new ReceiptSummaryQtyByPoLine();
    receiptSummaryQtyByPoLine.setItemNumber(3323323);
    receiptSummaryQtyByPoLine.setLineNumber(1);
    receiptSummaryQtyByPoLines.add(receiptSummaryQtyByPoLine);
    ReceiptSummaryQtyByPoLineResponse receiptSummaryQtyByPoLineResponse =
        defaultReceiptSummaryProcessor.getReceiptsSummaryByPoLineResponse(
            purchaseReferenceNumber, receiptSummaryQtyByPoLines, null, null);

    assertNotNull(receiptSummaryQtyByPoLineResponse);
    assertTrue(receiptSummaryQtyByPoLineResponse.getSummary().size() > 0);
    assertEquals(receiptSummaryQtyByPoLineResponse.getTotalReceivedQty().intValue(), 0);
    assertEquals(receiptSummaryQtyByPoLineResponse.getTotalFreightBillQty().intValue(), 0);
    assertFalse(receiptSummaryQtyByPoLineResponse.isPoFinalized());
  }

  @Test
  private void
      testGetReceiptsSummaryByPoLineResponse_isFinalizedFalse_as_noFinalizedMasterReceipt() {
    String purchaseReferenceNumber = "1234567890";
    Long deliveryNumber = 1000L;
    List<ReceiptSummaryQtyByPoLine> receiptSummaryQtyByPoLines = new ArrayList<>();
    ReceiptSummaryQtyByPoLine receiptSummaryQtyByPoLine = new ReceiptSummaryQtyByPoLine();
    receiptSummaryQtyByPoLine.setItemNumber(3323323);
    receiptSummaryQtyByPoLine.setLineNumber(1);
    receiptSummaryQtyByPoLines.add(receiptSummaryQtyByPoLine);

    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());

    ReceiptSummaryQtyByPoLineResponse receiptSummaryQtyByPoLineResponse =
        defaultReceiptSummaryProcessor.getReceiptsSummaryByPoLineResponse(
            purchaseReferenceNumber, receiptSummaryQtyByPoLines, deliveryNumber, null);

    assertNotNull(receiptSummaryQtyByPoLineResponse);
    assertEquals(receiptSummaryQtyByPoLineResponse.getTotalReceivedQty().intValue(), 0);
    assertEquals(receiptSummaryQtyByPoLineResponse.getTotalFreightBillQty().intValue(), 0);
    assertFalse(receiptSummaryQtyByPoLineResponse.isPoFinalized());
  }

  @Test
  private void
      testGetReceiptsSummaryByPoLineResponse_isFinalizedTrue_With_FinalizedMasterReceipt() {
    Long deliveryNumber = 1000L;
    List<ReceiptSummaryQtyByPoLine> lines = new ArrayList<>();
    ReceiptSummaryQtyByPoLine line1 = new ReceiptSummaryQtyByPoLine();
    line1.setItemNumber(3323323);
    line1.setLineNumber(1);
    line1.setReceivedQty(10);
    line1.setFreightBillQty(100);
    lines.add(line1);
    ReceiptSummaryQtyByPoLine line2 = new ReceiptSummaryQtyByPoLine();
    line2.setItemNumber(3323323);
    line2.setLineNumber(1);
    line2.setReceivedQty(20);
    line2.setFreightBillQty(500);
    lines.add(line2);

    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
    final List<Receipt> masterReceipts = getMasterReceipts();
    doReturn(masterReceipts)
        .when(receiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());

    ReceiptSummaryQtyByPoLineResponse receiptSummaryQtyByPoLineResponse =
        defaultReceiptSummaryProcessor.getReceiptsSummaryByPoLineResponse(
            finalizedPoNumber, lines, deliveryNumber, null);

    assertNotNull(receiptSummaryQtyByPoLineResponse);
    assertEquals(receiptSummaryQtyByPoLineResponse.getTotalReceivedQty().intValue(), 30);
    assertEquals(receiptSummaryQtyByPoLineResponse.getTotalFreightBillQty().intValue(), 600);
    assertTrue(receiptSummaryQtyByPoLineResponse.isPoFinalized());
  }

  @Test
  private void testValidateDeliveryDocumentReturnsDeliveryDocuments()
      throws IOException, ReceivingException {
    GdmPOLineResponse gdmPOLineResponse =
        gson.fromJson(
            MockGdmResponse.getDeliveryDetailsByUriIncludesDummyPoReturnsSuccessResponse(),
            GdmPOLineResponse.class);
    List<DeliveryDocument> deliveryDocuments =
        defaultReceiptSummaryProcessor.validateDeliveryDocument(gdmPOLineResponse);
    assertNotNull(deliveryDocuments);
    assertTrue(deliveryDocuments.size() > 0);
  }

  @Test(expectedExceptions = ReceivingException.class)
  private void testValidateDeliveryReturnsEmptyDeliveryDocuments()
      throws IOException, ReceivingException {
    GdmPOLineResponse gdmPOLineResponse =
        gson.fromJson(
            MockGdmResponse.getDeliveryDetailsByUriIncludesDummyPoReturnsEmptyResponse(),
            GdmPOLineResponse.class);
    defaultReceiptSummaryProcessor.validateDeliveryDocument(gdmPOLineResponse);
  }

  @Test
  private void testPopulateAsnInfo() throws IOException {
    Long deliveryNumber = 22322323L;
    GdmPOLineResponse gdmPOLineResponse =
        gson.fromJson(
            MockGdmResponse.getDeliveryDetailsByUriIncludesDummyPoReturnsSuccessResponse(),
            GdmPOLineResponse.class);
    ReceiptSummaryQtyByPoResponse receiptSummaryQtyByPoResponse =
        defaultReceiptSummaryProcessor.getReceiptsSummaryByPoResponse(
            deliveryNumber, gdmPOLineResponse, Collections.emptyList());
    receiptSummaryQtyByPoResponse =
        defaultReceiptSummaryProcessor.populateAsnInfo(
            gdmPOLineResponse, receiptSummaryQtyByPoResponse);

    assertNotNull(receiptSummaryQtyByPoResponse);
    assertNotNull(receiptSummaryQtyByPoResponse.getShipments());
    assertTrue(receiptSummaryQtyByPoResponse.getShipments().size() > 0);
    assertNotNull((receiptSummaryQtyByPoResponse.getAsnQty()));
    assertEquals(receiptSummaryQtyByPoResponse.getAsnQty().intValue(), 2286);
  }

  @Test
  private void testPopulateAsnInfoNoAsnDetails() throws IOException {
    Long deliveryNumber = 22322323L;
    GdmPOLineResponse gdmPOLineResponse =
        gson.fromJson(
            MockGdmResponse.getDeliveryDetailsByUriIncludesDummyPoReturnsSuccessResponseNoAsn(),
            GdmPOLineResponse.class);
    ReceiptSummaryQtyByPoResponse receiptSummaryQtyByPoResponse =
        defaultReceiptSummaryProcessor.getReceiptsSummaryByPoResponse(
            deliveryNumber, gdmPOLineResponse, Collections.emptyList());
    receiptSummaryQtyByPoResponse =
        defaultReceiptSummaryProcessor.populateAsnInfo(
            gdmPOLineResponse, receiptSummaryQtyByPoResponse);

    assertNotNull(receiptSummaryQtyByPoResponse);
    assertNotNull(receiptSummaryQtyByPoResponse.getShipments());
    assertEquals(receiptSummaryQtyByPoResponse.getShipments().size(), 0);
    assertNotNull((receiptSummaryQtyByPoResponse.getAsnQty()));
    assertEquals(receiptSummaryQtyByPoResponse.getAsnQty().intValue(), 0);
  }

  @Test
  private void testGetTotalReceivedQtyByDeliveries() throws ReceivingException {
    ReceiptSummaryQtyByDeliveries receiptSummaryQtyByDeliveries =
        new ReceiptSummaryQtyByDeliveries();
    receiptSummaryQtyByDeliveries.setDeliveries(Arrays.asList("24232323", "3423244"));
    receiptSummaryQtyByDeliveries.setRcvdQtyUOM("ZA");
    List<ReceiptQtySummaryByDeliveryNumberResponse> responseData =
        defaultReceiptSummaryProcessor.getReceiptQtySummaryByDeliveries(
            receiptSummaryQtyByDeliveries, MockHttpHeaders.getHeaders());
    assertNotNull(responseData);
  }

  @Test
  private void test_GetTotalReceivedQtyByDeliveries() throws ReceivingException {
    List<ReceiptSummaryVnpkResponse> receiptSummaryList = new ArrayList<>();
    ReceiptSummaryVnpkResponse receiptSummaryVnpkResponse1 =
        new ReceiptSummaryVnpkResponse(82672825L, 100L, "ZA");
    ReceiptSummaryVnpkResponse receiptSummaryVnpkResponse2 =
        new ReceiptSummaryVnpkResponse(29405337L, 200L, "ZA");
    receiptSummaryList.add(receiptSummaryVnpkResponse1);
    receiptSummaryList.add(receiptSummaryVnpkResponse2);
    when(receiptService.receivedQtySummaryByDeliveryNumbers(anyList()))
        .thenReturn(receiptSummaryList);

    List<ReceiptQtySummaryByDeliveryNumberResponse> receiptSummaryQtyByDeliveryNumberResponses =
        defaultReceiptSummaryProcessor.getReceiptQtySummaryByDeliveries(
            getMockReceiptSummaryQtyByDeliveries(), MockHttpHeaders.getHeaders());

    assertNotNull(receiptSummaryQtyByDeliveryNumberResponses);
    assertTrue(receiptSummaryQtyByDeliveryNumberResponses.size() > 0);
    assertEquals(
        receiptSummaryQtyByDeliveryNumberResponses.get(0).getReceivedQty().intValue(), 100);
    assertEquals(
        receiptSummaryQtyByDeliveryNumberResponses.get(1).getReceivedQty().intValue(), 200);

    verify(receiptService, times(1)).receivedQtySummaryByDeliveryNumbers(anyList());
  }

  private ReceiptSummaryQtyByDeliveries getMockReceiptSummaryQtyByDeliveries() {
    ReceiptSummaryQtyByDeliveries receiptSummaryQtyByDeliveries =
        new ReceiptSummaryQtyByDeliveries();
    receiptSummaryQtyByDeliveries.setRcvdQtyUOM(VNPK);
    receiptSummaryQtyByDeliveries.setDeliveries(Collections.singletonList("82672825,29405337"));
    return receiptSummaryQtyByDeliveries;
  }

  @Test
  private void test_GetTotalReceivedQtyByPoNumbers() throws ReceivingException {
    List<ReceiptSummaryVnpkResponse> receiptSummaryList = new ArrayList<>();
    ReceiptSummaryVnpkResponse receiptSummaryVnpkResponse1 =
        new ReceiptSummaryVnpkResponse("82672825", 100L, "ZA");
    ReceiptSummaryVnpkResponse receiptSummaryVnpkResponse2 =
        new ReceiptSummaryVnpkResponse("29405337", 200L, "ZA");
    receiptSummaryList.add(receiptSummaryVnpkResponse1);
    receiptSummaryList.add(receiptSummaryVnpkResponse2);
    when(receiptService.receivedQtySummaryByPoNumbers(anyList())).thenReturn(receiptSummaryList);

    List<ReceiptQtySummaryByPoNumbersResponse> receiptSummaryQtyByPoNumberResponses =
        defaultReceiptSummaryProcessor.getReceiptQtySummaryByPoNumbers(
            getMockReceiptSummaryQtyByPoNumbers(), MockHttpHeaders.getHeaders());

    assertNotNull(receiptSummaryQtyByPoNumberResponses);
    assertTrue(receiptSummaryQtyByPoNumberResponses.size() > 0);
    assertEquals(receiptSummaryQtyByPoNumberResponses.get(0).getReceivedQty().intValue(), 100);
    assertEquals(receiptSummaryQtyByPoNumberResponses.get(1).getReceivedQty().intValue(), 200);

    verify(receiptService, times(1)).receivedQtySummaryByPoNumbers(anyList());
  }

  private ReceiptSummaryQtyByPos getMockReceiptSummaryQtyByPoNumbers() {
    ReceiptSummaryQtyByPos receiptSummaryQtyByPoNumbers = new ReceiptSummaryQtyByPos();
    receiptSummaryQtyByPoNumbers.setRcvdQtyUOM(VNPK);
    receiptSummaryQtyByPoNumbers.setPoNumbers(Collections.singletonList("82672825,29405337"));
    return receiptSummaryQtyByPoNumbers;
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  private void test_getStoreDistributionByDeliveryPoPoLine() throws ReceivingException {
    Long deliveryNumber = 22322323L;
    String poNumber = "";
    int poLineNumber = 1;

    defaultReceiptSummaryProcessor.getStoreDistributionByDeliveryPoPoLine(
        deliveryNumber, poNumber, poLineNumber, MockHttpHeaders.getHeaders(), false);
  }

  private List<ReceiptSummaryResponse> getMockReceiptSummaryResponse() {
    List<ReceiptSummaryResponse> receiptSummaryResponseList = new ArrayList<>();
    ReceiptSummaryResponse receiptSummaryResponse = new ReceiptSummaryResponse();
    receiptSummaryResponse.setReceivedQty(300L);
    receiptSummaryResponse.setPurchaseReferenceNumber(finalizedPoNumber);
    receiptSummaryResponse.setPurchaseReferenceLineNumber(1);
    receiptSummaryResponseList.add(receiptSummaryResponse);
    return receiptSummaryResponseList;
  }

  private List<ReceiptSummaryResponse> getMockReceiptSummaryResponse_POCON() {
    List<ReceiptSummaryResponse> receiptSummaryResponseList = new ArrayList<>();
    ReceiptSummaryResponse receiptSummaryResponse = new ReceiptSummaryResponse();
    receiptSummaryResponse.setReceivedQty(300L);
    receiptSummaryResponse.setPurchaseReferenceNumber("019749106");
    receiptSummaryResponse.setPurchaseReferenceLineNumber(null);
    receiptSummaryResponseList.add(receiptSummaryResponse);
    return receiptSummaryResponseList;
  }

  private List<ReceiptSummaryResponse> getMockReceiptSummaryResponseMultiplePos()
      throws IOException {
    GdmPOLineResponse gdmPOLineResponse =
        gson.fromJson(
            MockGdmResponse.getDeliveryDetailsByUriIncludesDummyPoReturnsSuccessResponse(),
            GdmPOLineResponse.class);
    List<DeliveryDocument> deliveryDocumentList = gdmPOLineResponse.getDeliveryDocuments();
    List<ReceiptSummaryResponse> receiptSummaryResponseList = new ArrayList<>();

    for (DeliveryDocument deliveryDocument : deliveryDocumentList) {
      ReceiptSummaryResponse receiptSummaryResponse = new ReceiptSummaryResponse();
      receiptSummaryResponse.setReceivedQty(100L);
      receiptSummaryResponse.setPurchaseReferenceNumber(
          deliveryDocument.getPurchaseReferenceNumber());
      receiptSummaryResponse.setPurchaseReferenceLineNumber(1);
      receiptSummaryResponseList.add(receiptSummaryResponse);
    }
    return receiptSummaryResponseList;
  }

  private List<ReceiptSummaryResponse> getMockReceiptSummaryResponseMultiplePoLines()
      throws IOException, ReceivingException {
    String purchaseReferenceNumber = "8926870002";
    GdmPOLineResponse gdmPOLineResponse =
        gson.fromJson(
            MockGdmResponse.getDeliveryDetailsByUriIncludesDummyPoReturnsSuccessResponse(),
            GdmPOLineResponse.class);
    List<DeliveryDocument> deliveryDocumentList = gdmPOLineResponse.getDeliveryDocuments();
    DeliveryDocument deliveryDocument =
        defaultReceiptSummaryProcessor.getDeliveryDocumentByPo(
            deliveryDocumentList, purchaseReferenceNumber);
    List<ReceiptSummaryResponse> receiptSummaryResponseList = new ArrayList<>();

    for (DeliveryDocumentLine deliveryDocumentLine : deliveryDocument.getDeliveryDocumentLines()) {
      receiptSummaryResponseList.add(
          new ReceiptSummaryVnpkResponse(
              deliveryDocument.getPurchaseReferenceNumber(),
              deliveryDocumentLine.getPurchaseReferenceLineNumber(),
              10L));
    }
    return receiptSummaryResponseList;
  }
}
