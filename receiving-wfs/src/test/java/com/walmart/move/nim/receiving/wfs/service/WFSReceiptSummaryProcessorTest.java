package com.walmart.move.nim.receiving.wfs.service;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryQtyByPoLine;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryQtyByPoLineResponse;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryResponse;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryVnpkResponse;
import com.walmart.move.nim.receiving.core.repositories.ReceiptCustomRepository;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class WFSReceiptSummaryProcessorTest {

  @InjectMocks private WFSReceiptSummaryProcessor wfsReceiptSummaryProcessor;
  @Mock private ReceiptCustomRepository receiptCustomRepository;
  private List<ReceiptSummaryVnpkResponse> receiptSummaryByVnpkList;
  @Mock private ReceiptService receiptService;

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
        wfsReceiptSummaryProcessor.receivedQtySummaryInVnpkByDelivery(Long.valueOf(12345));
    assertEquals(response.size(), receiptSummaryByVnpkList.size());
  }

  @Test
  public void testReceivedQtySummaryInVnpkByDelivery_NoReceipts() {
    doReturn(null).when(receiptCustomRepository).receivedQtySummaryInEAByDelivery(anyLong());
    List<ReceiptSummaryResponse> response =
        wfsReceiptSummaryProcessor.receivedQtySummaryInVnpkByDelivery(Long.valueOf(12345));
    assertEquals(response.size(), 0);
  }

  @Test
  public void testGetReceivedQtyByReturnsNoReceipts() {
    Long deliveryNumber = 13131765L;
    doReturn(null).when(receiptCustomRepository).receivedQtySummaryInEAByDelivery(anyLong());
    List<ReceiptSummaryResponse> response =
        wfsReceiptSummaryProcessor.getReceivedQtyByPo(deliveryNumber);
    assertEquals(response.size(), 0);
  }

  @Test
  public void testGetReceivedQtyByReturnsReceipts() {
    Long deliveryNumber = 13131765L;
    doReturn(receiptSummaryByVnpkList)
        .when(receiptCustomRepository)
        .receivedQtySummaryInEAByDelivery(anyLong());
    List<ReceiptSummaryResponse> receiptSummaryResponseList =
        wfsReceiptSummaryProcessor.getReceivedQtyByPo(deliveryNumber);
    assertTrue(receiptSummaryResponseList.size() > 0);

    Optional<ReceiptSummaryResponse> receiptSummaryResponse =
        receiptSummaryResponseList
            .stream()
            .filter(receipt -> receipt.getPurchaseReferenceNumber().equalsIgnoreCase("9763140004"))
            .findAny();
    receiptSummaryResponse.ifPresent(
        summaryResponse -> assertEquals(summaryResponse.getReceivedQty().intValue(), 144));

    Integer totalReceivedQty =
        receiptSummaryResponseList
            .stream()
            .map(receipt -> receipt.getReceivedQty().intValue())
            .reduce(0, Integer::sum);
    assertEquals(totalReceivedQty.intValue(), 480);
  }

  @Test
  public void testGetReceivedQtyByPoLineReturnsReceipts() {
    Long deliveryNumber = 34232323L;
    String purchaseReferenceNumber = "9763140004";
    doReturn(getMockReceiptsSummaryResponseByPoLine())
        .when(receiptService)
        .getReceivedQtySummaryByPoLineInEaches(anyLong(), anyString());
    List<ReceiptSummaryResponse> receiptSummaryResponseList =
        wfsReceiptSummaryProcessor.getReceivedQtyByPoLine(deliveryNumber, purchaseReferenceNumber);

    assertTrue(receiptSummaryResponseList.size() > 0);

    Integer totalReceivedQty =
        receiptSummaryResponseList
            .stream()
            .map(receipt -> receipt.getReceivedQty().intValue())
            .reduce(0, Integer::sum);
    assertEquals(totalReceivedQty.intValue(), 480);
  }

  @Test
  public void testGetReceivedQtyByPoLineReturnsNoReceipts() {
    Long deliveryNumber = 34232323L;
    String purchaseReferenceNumber = "9763140004";
    doReturn(null)
        .when(receiptService)
        .getReceivedQtySummaryByPoLineInEaches(anyLong(), anyString());
    List<ReceiptSummaryResponse> receiptSummaryResponseList =
        wfsReceiptSummaryProcessor.getReceivedQtyByPoLine(deliveryNumber, purchaseReferenceNumber);

    assertEquals(receiptSummaryResponseList.size(), 0);
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
        wfsReceiptSummaryProcessor.getReceiptsSummaryByPoLineResponse(
            purchaseReferenceNumber, receiptSummaryQtyByPoLines, null, null);

    assertNotNull(receiptSummaryQtyByPoLineResponse);
    assertTrue(receiptSummaryQtyByPoLineResponse.getSummary().size() > 0);
    assertEquals(receiptSummaryQtyByPoLineResponse.getTotalReceivedQty().intValue(), 0);
    assertEquals(receiptSummaryQtyByPoLineResponse.getTotalFreightBillQty().intValue(), 0);
    assertFalse(receiptSummaryQtyByPoLineResponse.isPoFinalized());
  }

  @Test
  private void testGetReceiptsSummaryByPoResponse() {
    Long deliveryNumber = 34232323L;
    GdmPOLineResponse gdmPOLineResponse = mockGdmPOLineResponse();
    List<ReceiptSummaryQtyByPo> listReceiptSummaryQtyByPo = new ArrayList<>();
    ReceiptSummaryQtyByPo receiptSummaryQtyByPo = new ReceiptSummaryQtyByPo();
    receiptSummaryQtyByPo.setReceivedQty(1);
    receiptSummaryQtyByPo.setTotalBolFbq(2);
    receiptSummaryQtyByPo.setPurchaseReferenceNumber("1234567890");
    listReceiptSummaryQtyByPo.add(receiptSummaryQtyByPo);
    ReceiptSummaryQtyByPoResponse receiptSummaryQtyByPoResponse =
        wfsReceiptSummaryProcessor.getReceiptsSummaryByPoResponse(
            null, gdmPOLineResponse, listReceiptSummaryQtyByPo);
    assertNotNull(receiptSummaryQtyByPoResponse);
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

  private GdmPOLineResponse mockGdmPOLineResponse() {
    GdmPOLineResponse gdmPOLineResponse = new GdmPOLineResponse();
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    ItemData additionalInfo = new ItemData();
    additionalInfo.setWarehouseRotationTypeCode("3");
    additionalInfo.setProfiledWarehouseArea("CPS");
    additionalInfo.setWarehouseGroupCode("F");
    additionalInfo.setWarehouseAreaCode("1");
    additionalInfo.setAtlasConvertedItem(true);
    deliveryDocumentLine.setVendorNbrDeptSeq(40);
    deliveryDocumentLine.setTotalOrderQty(10);
    deliveryDocumentLine.setOverageQtyLimit(10);
    deliveryDocumentLine.setAdditionalInfo(additionalInfo);
    deliveryDocumentLine.setVendorPackCost(100F);
    deliveryDocumentLine.setWarehousePackSell(100F);

    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocument.setPurchaseReferenceNumber("4445530688");
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);
    deliveryDocument.setDeptNumber("40");
    deliveryDocument.setTotalPurchaseReferenceQty(20);
    deliveryDocuments.add(deliveryDocument);
    gdmPOLineResponse.setDeliveryNumber(34232323L);
    gdmPOLineResponse.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    gdmPOLineResponse.setDeliveryDocuments(deliveryDocuments);
    return gdmPOLineResponse;
  }
}
