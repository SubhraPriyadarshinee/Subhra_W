package com.walmart.move.nim.receiving.rx.service;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.testng.Assert.*;
import static org.testng.Assert.assertNotNull;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.repositories.ReceiptCustomRepository;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RxReceiptSummaryProcessorTest {

  @InjectMocks private RxReceiptSummaryProcessor rxReceiptSummaryProcessor;
  @Mock private ReceiptCustomRepository receiptCustomRepository;
  private List<ReceiptSummaryVnpkResponse> receiptSummaryByVnpkList;
  @Mock private ReceiptService receiptService;
  private Gson gson = new Gson();

  @BeforeMethod
  public void setUp() {
    receiptSummaryByVnpkList = new ArrayList<>();
    receiptSummaryByVnpkList.add(
        new ReceiptSummaryVnpkResponse("2323", 3, 48, 4, ReceivingConstants.Uom.VNPK, 96L));
    receiptSummaryByVnpkList.add(
        new ReceiptSummaryVnpkResponse("9763140004", 2, 24, 4, ReceivingConstants.Uom.VNPK, 48L));
    receiptSummaryByVnpkList.add(
        new ReceiptSummaryVnpkResponse("9763140004", 1, 24, 4, ReceivingConstants.Uom.VNPK, 96L));
    receiptSummaryByVnpkList.add(
        new ReceiptSummaryVnpkResponse("9763140005", 1, 48, 4, ReceivingConstants.Uom.VNPK, 96L));
    receiptSummaryByVnpkList.add(
        new ReceiptSummaryVnpkResponse("9763140007", 1, 48, 4, ReceivingConstants.Uom.VNPK, 144L));
  }

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  public void resetMocks() {
    reset(receiptService, receiptCustomRepository);
  }

  @Test
  public void testReceivedQtySummaryInVnpkByDelivery() {
    doReturn(receiptSummaryByVnpkList)
        .when(receiptCustomRepository)
        .receivedQtySummaryInEAByDelivery(anyLong());
    List<ReceiptSummaryResponse> response =
        rxReceiptSummaryProcessor.receivedQtySummaryInVnpkByDelivery(Long.valueOf(12345));
    assertEquals(response.size(), receiptSummaryByVnpkList.size());
  }

  @Test
  public void testReceivedQtySummaryInVnpkByDelivery_NoReceipts() {
    doReturn(null).when(receiptCustomRepository).receivedQtySummaryInEAByDelivery(anyLong());
    List<ReceiptSummaryResponse> response =
        rxReceiptSummaryProcessor.receivedQtySummaryInVnpkByDelivery(Long.valueOf(12345));
    assertEquals(response.size(), 0);
  }

  @Test
  private void testGetReceiptsSummaryByPoResponseWithAsn() throws IOException {
    Long deliveryNumber = 23232323L;
    File resource = new ClassPathResource("GdmMappedResponseV2_deliverySummary.json").getFile();
    String deliveryResponse = new String(Files.readAllBytes(resource.toPath()));
    GdmPOLineResponse gdmPOLineResponse = gson.fromJson(deliveryResponse, GdmPOLineResponse.class);
    List<ReceiptSummaryResponse> receiptSummaryResponseList = getMockReceiptSummaryResponse();
    List<ReceiptSummaryQtyByPo> receiptSummaryQtyByPos =
        rxReceiptSummaryProcessor.populateReceiptsSummaryByPo(
            gdmPOLineResponse.getDeliveryDocuments(), receiptSummaryResponseList, null);

    ReceiptSummaryQtyByPoResponse receiptSummaryQtyByPoResponse =
        rxReceiptSummaryProcessor.getReceiptsSummaryByPoResponse(
            deliveryNumber, gdmPOLineResponse, receiptSummaryQtyByPos);

    assertNotNull(receiptSummaryQtyByPoResponse);
    assertNotNull(receiptSummaryQtyByPoResponse.getDeliveryNumber());
    assertTrue(receiptSummaryQtyByPoResponse.getSummary().size() > 0);
    assertNotNull(receiptSummaryQtyByPoResponse.getFreightBillQuantity());
    assertNotNull(receiptSummaryQtyByPoResponse.getReceivedQty());
    assertNotNull(receiptSummaryQtyByPoResponse.getReceivedQtyUom());
    assertEquals(receiptSummaryQtyByPoResponse.getReceivedQtyUom(), ReceivingConstants.Uom.VNPK);
    assertNotNull(receiptSummaryQtyByPoResponse.getShipments());
    assertTrue(receiptSummaryQtyByPoResponse.getSummary().size() > 0);
    assertNotNull((receiptSummaryQtyByPoResponse.getAsnQty()));
    assertEquals(receiptSummaryQtyByPoResponse.getAsnQty().intValue(), 22);
  }

  @Test
  private void testGetReceiptsSummaryByPoResponseWithOutAsn() throws IOException {
    Long deliveryNumber = 23232323L;
    File resource =
        new ClassPathResource("GdmMappedResponseV2_deliverySummaryNoAsn.json").getFile();
    String deliveryResponse = new String(Files.readAllBytes(resource.toPath()));
    GdmPOLineResponse gdmPOLineResponse = gson.fromJson(deliveryResponse, GdmPOLineResponse.class);
    List<ReceiptSummaryResponse> receiptSummaryResponseList = getMockReceiptSummaryResponse();
    List<ReceiptSummaryQtyByPo> receiptSummaryQtyByPos =
        rxReceiptSummaryProcessor.populateReceiptsSummaryByPo(
            gdmPOLineResponse.getDeliveryDocuments(), receiptSummaryResponseList, null);

    ReceiptSummaryQtyByPoResponse receiptSummaryQtyByPoResponse =
        rxReceiptSummaryProcessor.getReceiptsSummaryByPoResponse(
            deliveryNumber, gdmPOLineResponse, receiptSummaryQtyByPos);

    assertNotNull(receiptSummaryQtyByPoResponse);
    assertNotNull(receiptSummaryQtyByPoResponse.getDeliveryNumber());
    assertTrue(receiptSummaryQtyByPoResponse.getSummary().size() > 0);
    assertNotNull(receiptSummaryQtyByPoResponse.getFreightBillQuantity());
    assertNotNull(receiptSummaryQtyByPoResponse.getReceivedQty());
    assertNotNull(receiptSummaryQtyByPoResponse.getReceivedQtyUom());
    assertEquals(receiptSummaryQtyByPoResponse.getReceivedQtyUom(), ReceivingConstants.Uom.VNPK);
    assertTrue(receiptSummaryQtyByPoResponse.getSummary().size() > 0);
    assertEquals(receiptSummaryQtyByPoResponse.getShipments().size(), 0);
    assertNotNull((receiptSummaryQtyByPoResponse.getAsnQty()));
    assertEquals(receiptSummaryQtyByPoResponse.getAsnQty().intValue(), 0);
  }

  @Test
  public void testGetReceivedQtyByReturnsNoReceipts() {
    Long deliveryNumber = 13131765L;
    doReturn(null).when(receiptCustomRepository).receivedQtySummaryInEAByDelivery(anyLong());
    List<ReceiptSummaryResponse> response =
        rxReceiptSummaryProcessor.getReceivedQtyByPo(deliveryNumber);
    assertEquals(response.size(), 0);
  }

  @Test
  public void testGetReceivedQtyByReturnsReceipts() {
    Long deliveryNumber = 13131765L;
    doReturn(receiptSummaryByVnpkList)
        .when(receiptCustomRepository)
        .receivedQtySummaryInEAByDelivery(anyLong());
    List<ReceiptSummaryResponse> receiptSummaryResponseList =
        rxReceiptSummaryProcessor.getReceivedQtyByPo(deliveryNumber);
    assertTrue(receiptSummaryResponseList.size() > 0);

    Optional<ReceiptSummaryResponse> receiptSummaryResponse =
        receiptSummaryResponseList
            .stream()
            .filter(receipt -> receipt.getPurchaseReferenceNumber().equalsIgnoreCase("9763140004"))
            .findAny();
    receiptSummaryResponse.ifPresent(
        summaryResponse -> assertEquals(summaryResponse.getReceivedQty().intValue(), 6));

    Integer totalReceivedQty =
        receiptSummaryResponseList
            .stream()
            .map(receipt -> receipt.getReceivedQty().intValue())
            .reduce(0, Integer::sum);
    assertEquals(totalReceivedQty.intValue(), 13);
  }

  @Test
  public void testGetReceivedQtyByPoLineReturnsReceipts() {
    Long deliveryNumber = 34232323L;
    String purchaseReferenceNumber = "9763140004";
    doReturn(getMockReceiptsSummaryResponseByPoLine())
        .when(receiptService)
        .getReceivedQtySummaryByPoLineInEaches(anyLong(), anyString());
    List<ReceiptSummaryResponse> receiptSummaryResponseList =
        rxReceiptSummaryProcessor.getReceivedQtyByPoLine(deliveryNumber, purchaseReferenceNumber);

    assertTrue(receiptSummaryResponseList.size() > 0);

    Integer totalReceivedQty =
        receiptSummaryResponseList
            .stream()
            .map(receipt -> receipt.getReceivedQty().intValue())
            .reduce(0, Integer::sum);
    assertEquals(totalReceivedQty.intValue(), 13);
  }

  @Test
  public void testGetReceivedQtyByPoLineReturnsNoReceipts() {
    Long deliveryNumber = 34232323L;
    String purchaseReferenceNumber = "9763140004";
    doReturn(null)
        .when(receiptService)
        .getReceivedQtySummaryByPoLineInEaches(anyLong(), anyString());
    List<ReceiptSummaryResponse> receiptSummaryResponseList =
        rxReceiptSummaryProcessor.getReceivedQtyByPoLine(deliveryNumber, purchaseReferenceNumber);

    assertEquals(receiptSummaryResponseList.size(), 0);
  }

  private List<ReceiptSummaryResponse> getMockReceiptsSummaryResponseByPoLine() {
    List<ReceiptSummaryResponse> receiptSummaryResponseList = new ArrayList<>();
    receiptSummaryResponseList.add(
        new ReceiptSummaryVnpkResponse("9763140004", 5, 48, 4, ReceivingConstants.Uom.VNPK, 96L));
    receiptSummaryResponseList.add(
        new ReceiptSummaryVnpkResponse("9763140004", 4, 24, 4, ReceivingConstants.Uom.VNPK, 48L));
    receiptSummaryResponseList.add(
        new ReceiptSummaryVnpkResponse("9763140004", 3, 24, 4, ReceivingConstants.Uom.VNPK, 96L));
    receiptSummaryResponseList.add(
        new ReceiptSummaryVnpkResponse("9763140004", 2, 48, 4, ReceivingConstants.Uom.VNPK, 96L));
    receiptSummaryResponseList.add(
        new ReceiptSummaryVnpkResponse("9763140004", 1, 48, 4, ReceivingConstants.Uom.VNPK, 144L));
    return receiptSummaryResponseList;
  }

  private List<ReceiptSummaryResponse> getMockReceiptSummaryResponse() throws IOException {
    File resource = new ClassPathResource("GdmMappedResponseV2_deliverySummary.json").getFile();
    String deliveryResponse = new String(Files.readAllBytes(resource.toPath()));
    GdmPOLineResponse gdmPOLineResponse = gson.fromJson(deliveryResponse, GdmPOLineResponse.class);
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
}
