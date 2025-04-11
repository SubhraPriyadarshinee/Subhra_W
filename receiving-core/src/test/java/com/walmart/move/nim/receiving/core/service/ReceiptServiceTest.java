package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ORG_UNIT_ID_HEADER;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.SUBCENTER_ID_HEADER;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.VNPK;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import com.walmart.move.nim.receiving.core.common.EventType;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.PurgeData;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.event.processor.summary.DefaultReceiptSummaryProcessor;
import com.walmart.move.nim.receiving.core.mock.data.MockContainer;
import com.walmart.move.nim.receiving.core.mock.data.MockDeliveryDocuments;
import com.walmart.move.nim.receiving.core.mock.data.MockGdmResponse;
import com.walmart.move.nim.receiving.core.mock.data.MockReceipt;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrderLine;
import com.walmart.move.nim.receiving.core.repositories.ReceiptCustomRepository;
import com.walmart.move.nim.receiving.core.repositories.ReceiptRepository;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.OSDRCode;
import com.walmart.move.nim.receiving.utils.constants.PurgeEntityType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ReceiptServiceTest {

  @InjectMocks private ReceiptService receiptService;
  @Mock private ReceiptRepository receiptRepository;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private ReceiptCustomRepository receiptCustomRepository;
  @Mock private DefaultReceiptSummaryProcessor defaultReceiptSummaryProcessor;

  private Receipt receipt;
  private List<Receipt> receipts;
  private ContainerItemResponseData containerItem1;
  private ContainerItemResponseData containerItem2;
  private ContainerItemResponseData containerItem3;
  private ContainerItemResponseData containerItem4;
  private ContainerItemResponseData containerItem5;
  private List<ContainerItemResponseData> containerItems;
  private DocumentLine documentLine;
  private List<DocumentLine> documentLines;
  private InstructionRequest instructionRequest;
  private UpdateInstructionRequest updateInstructionRequest;
  private DeliveryDocument deliveryDocument;
  private List<ReceiptSummaryResponse> receiptSummaryVnpkResponse;
  private List<ReceiptSummaryResponse> receiptSummaryEachesResponse;
  private ReceiptSummaryQtyByProblemIdResponse receiptSummaryQtyByProblemIdResponse;
  private ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponse;

  private String door = "101";
  private String userId = "sysadmin";
  private String problemTagId = "99999";
  private Long deliveryNumber = 21231313L;
  private ContainerRequest containerRequest = MockContainer.getContainerRequest();
  private PageRequest pageReq;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    // Mock receipts
    receipt = new Receipt();
    receipt.setProblemId(null);
    receipt.setCreateUserId(userId);
    receipt.setEachQty(4);
    receipt.setDeliveryNumber(deliveryNumber);
    receipt.setDoorNumber(door);
    receipt.setPurchaseReferenceLineNumber(1);
    receipt.setPurchaseReferenceNumber("9763140004");
    receipt.setQuantity(4);
    receipt.setQuantityUom("ZA");
    receipts = new ArrayList<>();
    receipts.add(receipt);

    // Mock containerItems
    containerItem1 = new ContainerItemResponseData();
    ContainerPOResponseData purchaseOrderItem1 = new ContainerPOResponseData();
    purchaseOrderItem1.setPurchaseReferenceNumber("9763140004");
    purchaseOrderItem1.setPurchaseReferenceLineNumber(1);
    containerItem1.setItemNumber(11);
    containerItem1.setItemQuantity(null);
    containerItem1.setQuantityUOM("ZA");
    containerItem1.setPurchaseOrder(purchaseOrderItem1);
    // containerItem1.setVendorPackQty(1);
    // containerItem1.setWarehousePackQty(1);

    containerItem2 = new ContainerItemResponseData();
    ContainerPOResponseData purchaseOrderItem2 = new ContainerPOResponseData();
    purchaseOrderItem2.setPurchaseReferenceNumber("9763140004");
    purchaseOrderItem2.setPurchaseReferenceLineNumber(2);
    containerItem1.setItemNumber(22);
    containerItem2.setItemQuantity(5);
    containerItem2.setQuantityUOM(null);
    containerItem2.setPurchaseOrder(purchaseOrderItem2);
    // containerItem2.setVendorPackQty(1);
    // containerItem2.setWarehousePackQty(1);

    containerItem3 = new ContainerItemResponseData();
    ContainerPOResponseData purchaseOrderItem3 = new ContainerPOResponseData();
    purchaseOrderItem3.setPurchaseReferenceNumber("9763140004");
    purchaseOrderItem3.setPurchaseReferenceLineNumber(3);
    containerItem3.setItemNumber(33);
    containerItem3.setItemQuantity(10);
    containerItem3.setQuantityUOM("ZA");
    containerItem3.setPurchaseOrder(purchaseOrderItem3);
    // containerItem3.setVendorPackQty(null);
    // containerItem3.setWarehousePackQty(1);

    containerItem4 = new ContainerItemResponseData();
    ContainerPOResponseData purchaseOrderItem4 = new ContainerPOResponseData();
    purchaseOrderItem4.setPurchaseReferenceNumber("9763140004");
    purchaseOrderItem4.setPurchaseReferenceLineNumber(4);
    containerItem1.setItemNumber(44);
    containerItem4.setItemQuantity(15);
    containerItem4.setQuantityUOM("ZA");
    containerItem4.setPurchaseOrder(purchaseOrderItem4);
    // containerItem4.setVendorPackQty(1);
    // containerItem4.setWarehousePackQty(null);

    containerItem5 = new ContainerItemResponseData();
    ContainerPOResponseData purchaseOrderItem5 = new ContainerPOResponseData();
    purchaseOrderItem5.setPurchaseReferenceNumber("9763140004");
    purchaseOrderItem5.setPurchaseReferenceLineNumber(5);
    containerItem5.setItemNumber(55);
    containerItem5.setItemQuantity(20);
    containerItem5.setQuantityUOM("ZA");
    containerItem5.setPurchaseOrder(purchaseOrderItem5);
    // containerItem5.setVendorPackQty(-1);
    // containerItem5.setWarehousePackQty(-1);

    containerItems = new ArrayList<>();
    containerItems.add(containerItem1);
    containerItems.add(containerItem2);
    containerItems.add(containerItem3);
    containerItems.add(containerItem4);
    containerItems.add(containerItem5);

    // Mock instructionRequest
    instructionRequest = new InstructionRequest();
    instructionRequest.setDeliveryNumber(deliveryNumber.toString());
    instructionRequest.setDoorNumber(door);

    // Mock DeliveryDocument
    deliveryDocument = new DeliveryDocument();
    deliveryDocument.setPurchaseReferenceNumber("9763140004");
    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setPurchaseReferenceNumber("9763140004");
    deliveryDocumentLine.setPurchaseReferenceLineNumber(1);
    deliveryDocumentLine.setVendorPack(12);
    deliveryDocumentLine.setWarehousePack(12);
    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);

    // Mock updateInstructionRequest
    updateInstructionRequest = new UpdateInstructionRequest();
    updateInstructionRequest.setDeliveryNumber(123456L);
    documentLines = new ArrayList<>();
    documentLine = new DocumentLine();
    documentLine.setPurchaseReferenceNumber("9763140004");
    documentLine.setPurchaseReferenceLineNumber(1);
    documentLine.setExpectedQty(100L);
    documentLine.setGtin("00000001234");
    documentLine.setItemNumber(1234L);
    documentLine.setMaxOverageAcceptQty(20L);
    documentLine.setPoDCNumber("32899");
    documentLine.setQuantity(100);
    documentLine.setQuantityUOM(ReceivingConstants.Uom.WHPK);
    documentLine.setVnpkQty(1);
    documentLine.setWhpkQty(1);
    documentLines.add(documentLine);
    updateInstructionRequest.setDeliveryDocumentLines(documentLines);

    // Mock receiptSummaryVnpkResponse
    receiptSummaryVnpkResponse = new ArrayList<>();
    receiptSummaryVnpkResponse.add(
        new ReceiptSummaryEachesResponse("9763140004", 1, null, Long.valueOf(4)));
    receiptSummaryVnpkResponse.add(
        new ReceiptSummaryEachesResponse("9763140005", 1, null, Long.valueOf(2)));
    receiptSummaryVnpkResponse.add(
        new ReceiptSummaryEachesResponse("9763140007", 1, null, Long.valueOf(3)));

    receiptSummaryEachesResponse = new ArrayList<>();
    receiptSummaryEachesResponse.add(
        new ReceiptSummaryEachesResponse("9763140004", 1, null, Long.valueOf(96)));
    receiptSummaryEachesResponse.add(
        new ReceiptSummaryEachesResponse("9763140005", 1, null, Long.valueOf(96)));
    receiptSummaryEachesResponse.add(
        new ReceiptSummaryEachesResponse("9763140007", 1, null, Long.valueOf(144)));

    receiptSummaryQtyByPoAndPoLineResponse =
        new ReceiptSummaryQtyByPoAndPoLineResponse("9763140004", 1, 3l);
    receiptSummaryQtyByProblemIdResponse = new ReceiptSummaryQtyByProblemIdResponse("1", 3l);
    TenantContext.setFacilityNum(12345);
    TenantContext.setFacilityCountryCode("us");
    pageReq = PageRequest.of(0, 10);
  }

  @BeforeMethod
  public void beforeMethod() {
    reset(
        receiptRepository,
        tenantSpecificConfigReader,
        receiptCustomRepository,
        defaultReceiptSummaryProcessor);
  }

  /**
   * Test case for received summary for delivery in VNPK.
   *
   * @throws NumberFormatException
   * @throws Exception
   */
  @Test
  public void testReceivedQtySummaryVNPK() throws NumberFormatException, Exception {
    when(receiptCustomRepository.receivedQtySummaryInVnpkByDelivery(any(Long.class)))
        .thenReturn(receiptSummaryVnpkResponse);

    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), any(Class.class)))
        .thenReturn(defaultReceiptSummaryProcessor);

    when(defaultReceiptSummaryProcessor.receivedQtySummaryInVnpkByDelivery(anyLong()))
        .thenReturn(receiptSummaryVnpkResponse);

    List<ReceiptSummaryResponse> receiptSummaryResponse =
        receiptService.getReceivedQtySummaryByPOForDelivery(
            Long.valueOf("21119003"), ReceivingConstants.Uom.VNPK);

    assertEquals(receiptSummaryVnpkResponse, receiptSummaryResponse);

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    verify(defaultReceiptSummaryProcessor, times(1))
        .receivedQtySummaryInVnpkByDelivery(any(Long.class));
    reset(receiptCustomRepository);
  }

  @Test
  public void testCreateReceiptsFromInstructionSuccess() {

    doAnswer((Answer<List<Receipt>>) invocation -> invocation.getArgument(0))
        .when(receiptRepository)
        .saveAll(any(List.class));

    List<Receipt> persistedReceipts =
        receiptService.createReceiptsFromInstruction(
            updateInstructionRequest, problemTagId, userId);

    assertEquals(persistedReceipts.get(0).getProblemId(), problemTagId);
    assertEquals(
        persistedReceipts.get(0).getPurchaseReferenceNumber(),
        documentLine.getPurchaseReferenceNumber());
    assertEquals(
        persistedReceipts.get(0).getPurchaseReferenceLineNumber(),
        documentLine.getPurchaseReferenceLineNumber());
    assertEquals(persistedReceipts.get(0).getQuantity(), documentLine.getQuantity());

    verify(receiptRepository, times(1)).saveAll(any());
    reset(receiptRepository);
  }

  /**
   * Test case for received summary for delivery in Eaches. If UOM is NUll default behavior.
   *
   * @throws NumberFormatException
   * @throws Exception
   */
  @Test
  public void testReceivedQtySummaryEaches() throws NumberFormatException, Exception {
    when(receiptCustomRepository.receivedQtySummaryInEachesByDelivery(any(Long.class)))
        .thenReturn(receiptSummaryEachesResponse);

    List<ReceiptSummaryResponse> receiptSummaryResponse =
        receiptService.getReceivedQtySummaryByPOForDelivery(
            deliveryNumber, ReceivingConstants.Uom.EACHES);

    List<ReceiptSummaryResponse> receiptSummaryResponseDefaultEaches =
        receiptService.getReceivedQtySummaryByPOForDelivery(deliveryNumber, "");

    assertEquals(receiptSummaryEachesResponse, receiptSummaryResponse);
    assertEquals(receiptSummaryEachesResponse, receiptSummaryResponseDefaultEaches);

    verify(receiptCustomRepository, times(2)).receivedQtySummaryInEachesByDelivery(any(Long.class));
  }

  @Test
  public void testGetReceivedQtyByPoAndPoLine() {
    when(receiptCustomRepository.receivedQtyByPoAndPoLine("9763140004", 1))
        .thenReturn(receiptSummaryQtyByPoAndPoLineResponse);

    long receivedQty = receiptService.getReceivedQtyByPoAndPoLine("9763140004", 1);
    assertEquals(receivedQty, 3l);
  }

  @Test
  public void testGetReceivedQtyByPoAndPoLineWithNull() {
    when(receiptCustomRepository.receivedQtyByPoAndPoLine("9763140004", 1)).thenReturn(null);

    long receivedQty = receiptService.getReceivedQtyByPoAndPoLine("9763140004", 1);
    assertEquals(receivedQty, 0);
  }

  @Test
  public void testGetReceivedQtyByPoAndPoLineInEach() {
    when(receiptCustomRepository.receivedQtyByPoAndPoLineInEach("9763140004", 1))
        .thenReturn(receiptSummaryQtyByPoAndPoLineResponse);

    long receivedQty = receiptService.getReceivedQtyByPoAndPoLineInEach("9763140004", 1);
    assertEquals(receivedQty, 3l);
  }

  @Test
  public void testGetReceivedQtyByPoAndPoLineInEachWithNull() {
    when(receiptCustomRepository.receivedQtyByPoAndPoLineInEach("9763140004", 1)).thenReturn(null);

    long receivedQty = receiptService.getReceivedQtyByPoAndPoLineInEach("9763140004", 1);
    assertEquals(receivedQty, 0);
  }

  @Test
  public void testGetReceivedQtyByProblemId() {
    when(receiptCustomRepository.receivedQtyByProblemIdInVnpk(problemTagId))
        .thenReturn(receiptSummaryQtyByProblemIdResponse);

    long receivedQty = receiptService.getReceivedQtyByProblemId(problemTagId);
    assertEquals(receivedQty, 3l);
  }

  @Test
  public void test_getReceivedQtyByProblemIdInVnpk() {
    when(receiptCustomRepository.receivedQtyByProblemIdInVnpk(problemTagId))
        .thenReturn(receiptSummaryQtyByProblemIdResponse);

    long receivedQty = receiptService.getReceivedQtyByProblemIdInVnpk(problemTagId);
    assertEquals(receivedQty, 3l);
  }

  @Test
  public void testGetReceivedQtyByProblemIdWithNull() {
    when(receiptCustomRepository.receivedQtyByProblemIdInVnpk("1")).thenReturn(null);

    long receivedQty = receiptService.getReceivedQtyByProblemId("1");
    assertEquals(receivedQty, 0);
  }

  @Test
  public void testCreateReceipt() {
    when(receiptRepository.save(any(Receipt.class))).thenReturn(receipt);

    Receipt response = receiptService.saveReceipt(receipt);
    assertEquals(response, receipt);
    verify(receiptRepository, times(1)).save(any(Receipt.class));
    reset(receiptRepository);
  }

  @Test
  public void testDeleteReceptList() {
    when(receiptRepository.findByDeliveryNumber(1L)).thenReturn(receipts);
    doNothing().when(receiptRepository).deleteAll(receipts);

    try {
      receiptService.deleteReceptList(1L);
    } catch (ReceivingException e) {
      e.printStackTrace();
    }
  }

  @Test
  public void testDeleteReceptListWithNullResponse() {
    when(receiptRepository.findByDeliveryNumber(1L)).thenReturn(null);

    try {
      receiptService.deleteReceptList(1L);
    } catch (ReceivingException e) {
      assertEquals(e.getMessage(), ReceivingException.RECEIPT_NOT_FOUND);
    }
  }

  @Test
  public void testDeleteReceptListWithEmptyResponse() {
    when(receiptRepository.findByDeliveryNumber(1L)).thenReturn(new ArrayList<>());

    try {
      receiptService.deleteReceptList(1L);
    } catch (ReceivingException e) {
      e.printStackTrace();
      assertEquals(e.getMessage(), ReceivingException.RECEIPT_NOT_FOUND);
    }
  }

  @Test
  public void testFindByDeliveryNumber() {
    when(receiptRepository.findByDeliveryNumber(deliveryNumber)).thenReturn(receipts);

    List<Receipt> response = receiptService.findByDeliveryNumber(deliveryNumber);
    assertEquals(response, receipts);
  }

  @Test
  public void testcreateReceiptsFromContainerItems() {
    receiptService.createReceiptsFromContainerItems(containerItems, instructionRequest, userId);

    verify(receiptRepository, times(1)).saveAll(any());
    reset(receiptRepository);
  }

  @Test
  public void testPrepareReceipts() {
    Integer secondItemEachQty =
        containerRequest.getContents().get(1).getQuantity()
            * containerRequest.getContents().get(1).getVnpkQty();
    List<Receipt> receipts =
        receiptService.prepareReceipts(deliveryNumber, containerRequest, userId);

    assertEquals(receipts.size(), containerRequest.getContents().size());
    assertEquals(receipts.get(0).getDeliveryNumber(), deliveryNumber);
    assertEquals(
        receipts.get(0).getPurchaseReferenceNumber(),
        containerRequest.getContents().get(0).getPurchaseReferenceNumber());
    assertEquals(
        receipts.get(0).getPurchaseReferenceLineNumber(),
        containerRequest.getContents().get(0).getPurchaseReferenceLineNumber());
    assertEquals(
        receipts.get(0).getQuantity(), containerRequest.getContents().get(0).getQuantity());
    assertEquals(receipts.get(0).getEachQty(), containerRequest.getContents().get(0).getQuantity());

    assertEquals(receipts.get(1).getDeliveryNumber(), deliveryNumber);
    assertEquals(
        receipts.get(1).getPurchaseReferenceNumber(),
        containerRequest.getContents().get(1).getPurchaseReferenceNumber());
    assertEquals(
        receipts.get(1).getPurchaseReferenceLineNumber(),
        containerRequest.getContents().get(1).getPurchaseReferenceLineNumber());
    assertEquals(
        receipts.get(1).getQuantity(), containerRequest.getContents().get(1).getQuantity());
    assertEquals(receipts.get(1).getEachQty(), secondItemEachQty);
  }

  @Test
  public void testCreateReceiptFromDeliveryDocumentLine() {
    PurchaseOrderLine purchaseOrderLine = MockGdmResponse.getMockPurchaseOrderLine();
    Receipt receipt = new Receipt();
    receipt.setId(1L);
    receipt.setDeliveryNumber(deliveryNumber);
    receipt.setDoorNumber(door);
    receipt.setPurchaseReferenceNumber("9763140004");
    receipt.setPurchaseReferenceLineNumber(purchaseOrderLine.getPoLineNumber());

    receipt.setQuantity(
        ReceivingUtils.conversionToVendorPack(
            1,
            purchaseOrderLine.getOrdered().getUom(),
            purchaseOrderLine.getVnpk().getQuantity(),
            purchaseOrderLine.getWhpk().getQuantity()));

    receipt.setQuantityUom(purchaseOrderLine.getOrdered().getUom());
    receipt.setVnpkQty(purchaseOrderLine.getVnpk().getQuantity());
    receipt.setWhpkQty(purchaseOrderLine.getWhpk().getQuantity());
    receipt.setEachQty(
        ReceivingUtils.conversionToEaches(
            1,
            purchaseOrderLine.getOrdered().getUom(),
            purchaseOrderLine.getVnpk().getQuantity(),
            purchaseOrderLine.getWhpk().getQuantity()));
    receipt.setCreateUserId(ReceivingConstants.DEFAULT_AUDIT_USER);
    when(receiptRepository.save(any(Receipt.class))).thenReturn(receipt);
    Receipt receipt2 =
        receiptService.createReceiptFromPurchaseOrderLine(
            door, deliveryNumber, "9763140004", purchaseOrderLine, 1);
    assertEquals(receipt, receipt2);
    verify(receiptRepository, times(1)).save(any(Receipt.class));
    reset(receiptRepository);
  }

  @Test
  public void testReceivedQtyByPoAndPoLineList() {
    List<String> purchaseReferenceNumberList = new ArrayList<>();
    purchaseReferenceNumberList.add("9763140004");
    purchaseReferenceNumberList.add("9763140005");
    Set<Integer> purchaseReferenceLineNumberSet = new HashSet<>();
    purchaseReferenceLineNumberSet.add(1);
    purchaseReferenceLineNumberSet.add(2);
    ReceiptSummaryEachesResponse response1 =
        new ReceiptSummaryEachesResponse("9763140004", 1, null, 1L);
    ReceiptSummaryEachesResponse response2 =
        new ReceiptSummaryEachesResponse("9763140005", 2, null, 1L);
    List<ReceiptSummaryEachesResponse> expectedResponses = new ArrayList<>();
    expectedResponses.add(response1);
    expectedResponses.add(response2);
    when(receiptCustomRepository.receivedQtyByPoAndPoLineList(anyList(), anySet()))
        .thenReturn(expectedResponses);
    List<ReceiptSummaryEachesResponse> actualResponses =
        receiptService.receivedQtyByPoAndPoLineList(
            purchaseReferenceNumberList, purchaseReferenceLineNumberSet);
    assertEquals(actualResponses, expectedResponses);
    verify(receiptCustomRepository, times(1)).receivedQtyByPoAndPoLineList(anyList(), anySet());
    reset(receiptCustomRepository);
  }

  @Test
  public void testIsPOFinalized() {

    doReturn(10)
        .when(receiptRepository)
        .getFinalizedReceiptCountByDeliveryAndPoRefNumber(anyString(), anyString());

    assertTrue(receiptService.isPOFinalized("1234567", "098765"));

    doReturn(0)
        .when(receiptRepository)
        .getFinalizedReceiptCountByDeliveryAndPoRefNumber(anyString(), anyString());

    assertFalse(receiptService.isPOFinalized("1234567", "098765"));

    verify(receiptRepository, times(2))
        .getFinalizedReceiptCountByDeliveryAndPoRefNumber(anyString(), anyString());
  }

  @Test
  public void testSave() {

    Receipt savedReceipt = new Receipt();
    savedReceipt.setVersion(1);

    doReturn(savedReceipt).when(receiptRepository).saveAndFlush(any(Receipt.class));

    Receipt receiptResponse = receiptService.saveAndFlushReceipt(new Receipt());
    assertEquals(receiptResponse.getVersion(), savedReceipt.getVersion());
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp =
          "No record found for this delivery number 21119003 in receipt")
  public void testGetOSDRDetailsByDeliveryNumberForNoReceipts() throws ReceivingException {
    doReturn(new ArrayList<Receipt>()).when(receiptRepository).findByDeliveryNumber(anyLong());
    receiptService.getReceiptSummary(21119003L, null, null);
    verify(receiptRepository, times(1)).findByDeliveryNumber(anyLong());
  }

  @Test
  public void
      testGetReceiptsSummaryByDeliveryNumberAndPurchaseRefereceNumberAndPurchaseReferenceLineNumber()
          throws ReceivingException {
    List<Receipt> receipts = new ArrayList<>();
    Receipt receipt1 = new Receipt();
    receipt1.setPurchaseReferenceNumber("9763140005");
    receipt1.setPurchaseReferenceLineNumber(1);
    receipt1.setDeliveryNumber(21119003L);
    receipt1.setVnpkQty(4);
    receipt1.setWhpkQty(2);
    Receipt receipt2 = new Receipt();
    receipt2.setPurchaseReferenceNumber("9763140006");
    receipt2.setPurchaseReferenceLineNumber(1);
    receipt2.setDeliveryNumber(21119003L);
    receipt2.setWhpkQty(2);
    receipt2.setVnpkQty(4);
    receipts.add(receipt1);
    receipts.add(receipt2);
    doReturn(receipts).when(receiptRepository).findByDeliveryNumber(anyLong());
    receiptService.getReceiptSummary(21119003L, null, null);
    verify(receiptRepository, times(1)).findByDeliveryNumber(anyLong());
  }

  @Test
  public void testGetReceiptsSummaryByDeliveryNumberAndPurchaseRefereceNumber()
      throws ReceivingException {
    List<Receipt> receipts = getReceipts();
    doReturn(receipts).when(receiptRepository).findByDeliveryNumber(anyLong());
    receiptService.getReceiptSummary(21119003L, null, null);
    verify(receiptRepository, times(1)).findByDeliveryNumber(anyLong());
  }

  private List<Receipt> getReceipts() {
    List<Receipt> receipts = new ArrayList<>();
    receipts.add(MockReceipt.getOSDRMasterReceipt());
    return receipts;
  }

  @Test
  public void
      testGetReceiptsSummaryByDeliveryNumberAndPurchaseRefereceNumberAndPurchaseReferenceLineNumberWithoutMaster()
          throws ReceivingException {
    List<Receipt> receipts = new ArrayList<>();
    Receipt receipt1 = new Receipt();
    receipt1.setPurchaseReferenceNumber("9763140005");
    receipt1.setPurchaseReferenceLineNumber(1);
    receipt1.setDeliveryNumber(21119003L);
    receipt1.setVnpkQty(4);
    receipt1.setWhpkQty(2);
    Receipt receipt2 = new Receipt();
    receipt2.setPurchaseReferenceNumber("9763140006");
    receipt2.setPurchaseReferenceLineNumber(1);
    receipt2.setDeliveryNumber(21119003L);
    receipt2.setWhpkQty(2);
    receipt2.setVnpkQty(4);
    receipts.add(receipt1);
    receipts.add(receipt2);
    doReturn(receipts).when(receiptRepository).findByDeliveryNumber(anyLong());
    receiptService.getReceiptSummary(21119003L, null, null);
    verify(receiptRepository, times(1)).findByDeliveryNumber(anyLong());
  }

  @Test
  public void testGetReceiptsSummaryByDeliveryNumberAndPurchaseRefereceNumberWithOsdrMaster()
      throws ReceivingException {
    List<Receipt> receipts = getReceipts();
    doReturn(receipts).when(receiptRepository).findByDeliveryNumber(anyLong());
    receiptService.getReceiptSummary(21119003L, null, null);
    verify(receiptRepository, times(1)).findByDeliveryNumber(anyLong());
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp =
          "No record found for this delivery number 21119003 in receipt")
  public void testGetOSDRDetailsByDeliveryNumberForNoReceiptsWithoutOsdrMaster()
      throws ReceivingException {
    doReturn(new ArrayList<Receipt>()).when(receiptRepository).findByDeliveryNumber(anyLong());
    receiptService.getReceiptSummary(21119003L, null, null);
    verify(receiptRepository, times(1)).findByDeliveryNumber(anyLong());
  }

  @Test
  public void testReceivedQtyInVNPKByPoAndPoLineList() {
    List<String> purchaseReferenceNumberList = new ArrayList<>();
    purchaseReferenceNumberList.add("9763140005");
    purchaseReferenceNumberList.add("9763140006");
    Set<Integer> purchaseReferenceLineNumberSet = new HashSet<>();
    purchaseReferenceLineNumberSet.add(1);
    purchaseReferenceLineNumberSet.add(2);
    ReceiptSummaryQtyByPoAndPoLineResponse response1 =
        new ReceiptSummaryQtyByPoAndPoLineResponse("9763140005", 1, 1L);
    ReceiptSummaryQtyByPoAndPoLineResponse response2 =
        new ReceiptSummaryQtyByPoAndPoLineResponse("9763140006", 2, 1L);
    List<ReceiptSummaryQtyByPoAndPoLineResponse> expectedResponses = new ArrayList<>();
    expectedResponses.add(response1);
    expectedResponses.add(response2);
    when(receiptCustomRepository.receivedQtyInVNPKByPoAndPoLineList(anyList(), anySet()))
        .thenReturn(expectedResponses);
    List<ReceiptSummaryQtyByPoAndPoLineResponse> actualResponses =
        receiptService.receivedQtyInVNPKByPoAndPoLineList(
            purchaseReferenceNumberList, purchaseReferenceLineNumberSet);
    assertEquals(actualResponses, expectedResponses);
    verify(receiptCustomRepository, times(1))
        .receivedQtyInVNPKByPoAndPoLineList(anyList(), anySet());
    reset(receiptCustomRepository);
  }

  @Test
  public void testPurge() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -60 * 24);

    Receipt receipt = MockReceipt.getReceipt();
    receipt.setId(1L);
    receipt.setCreateTs(cal.getTime());

    Receipt receipt1 = MockReceipt.getReceipt();
    receipt1.setId(10L);
    receipt1.setCreateTs(cal.getTime());

    when(receiptRepository.findByIdGreaterThanEqual(anyLong(), any()))
        .thenReturn(Arrays.asList(receipt, receipt1));
    doNothing().when(receiptRepository).deleteAll();
    PurgeData purgeData =
        PurgeData.builder()
            .purgeEntityType(PurgeEntityType.RECEIPT)
            .eventTargetStatus(EventTargetStatus.PENDING)
            .lastDeleteId(0L)
            .build();

    long lastDeletedId = receiptService.purge(purgeData, pageReq, 30);
    assertEquals(lastDeletedId, 10L);
  }

  @Test
  public void testPurgeWithNoDataToDeleteBeforeDate() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -60 * 24);

    Receipt receipt = MockReceipt.getReceipt();
    receipt.setId(1L);
    receipt.setCreateTs(cal.getTime());

    Receipt receipt1 = MockReceipt.getReceipt();
    receipt1.setId(10L);
    receipt1.setCreateTs(cal.getTime());

    when(receiptRepository.findByIdGreaterThanEqual(anyLong(), any()))
        .thenReturn(Arrays.asList(receipt, receipt1));
    doNothing().when(receiptRepository).deleteAll();
    PurgeData purgeData =
        PurgeData.builder()
            .purgeEntityType(PurgeEntityType.RECEIPT)
            .eventTargetStatus(EventTargetStatus.PENDING)
            .lastDeleteId(0L)
            .build();

    long lastDeletedId = receiptService.purge(purgeData, pageReq, 90);
    assertEquals(lastDeletedId, 0L);
  }

  @Test
  public void testPurgeWithFewDataToDeleteBeforeDate() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -60 * 24);

    Receipt receipt = MockReceipt.getReceipt();
    receipt.setId(1L);
    receipt.setCreateTs(cal.getTime());

    Receipt receipt1 = MockReceipt.getReceipt();
    receipt1.setId(10L);
    receipt1.setCreateTs(new Date());

    when(receiptRepository.findByIdGreaterThanEqual(anyLong(), any()))
        .thenReturn(Arrays.asList(receipt, receipt1));
    doNothing().when(receiptRepository).deleteAll();
    PurgeData purgeData =
        PurgeData.builder()
            .purgeEntityType(PurgeEntityType.RECEIPT)
            .eventTargetStatus(EventTargetStatus.PENDING)
            .lastDeleteId(0L)
            .build();

    long lastDeletedId = receiptService.purge(purgeData, pageReq, 30);
    assertEquals(lastDeletedId, 1L);
  }

  @Test
  public void testToGetReceiptsSummaryByDeliveryAndPoAndPoLineAndSSCCReturnsNotNull() {
    when(receiptCustomRepository.getReceiptsQtySummaryByDeliveryAndPoAndPoLineAndSSCC(
            1L, "PO123", 1, "SSCC"))
        .thenReturn(new RxReceiptsSummaryResponse("PO123", 1, "SSCC", 50L, 50L));
    Long receiptsSummary =
        receiptService
            .findReceiptsSummaryByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndSSCC(
                1L, "PO123", 1, "SSCC");
    assertNotNull(receiptsSummary);
    assertTrue(receiptsSummary == 50L);
  }

  @Test
  public void testToGetReceiptsSummaryByDeliveryAndPoAndPoLineAndSSCCReturnsNull() {
    when(receiptCustomRepository.getReceiptsQtySummaryByDeliveryAndPoAndPoLineAndSSCC(
            1L, "PO123", 1, "SSCC"))
        .thenReturn(new RxReceiptsSummaryResponse("PO123", 1, "SSCC", 0l, 0l));
    Long receiptsSummary =
        receiptService
            .findReceiptsSummaryByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndSSCC(
                1L, "PO123", 1, "SSCC");
    assertSame(0l, receiptsSummary);
  }

  /** Test report's get receipts by PO/PO line */
  @Test
  public void testGetReceiptsByPoPoLine() {
    when(receiptRepository.findByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyString(), anyInt()))
        .thenReturn(receipts);

    List<Receipt> receiptList = receiptService.getReceiptsByAndPoPoLine("9763140005", 1);
    assertEquals(receiptList.size(), 1);

    verify(receiptRepository, times(1))
        .findByPurchaseReferenceNumberAndPurchaseReferenceLineNumber("9763140005", 1);
  }

  @Test
  public void testPrepareMasterReceipt() {
    ReceivingCountSummary receivingCountSummary = new ReceivingCountSummary();
    receivingCountSummary.setVnpkQty(12);
    receivingCountSummary.setWhpkQty(6);

    Receipt receipt =
        receiptService.prepareMasterReceipt(deliveryNumber, "PO9999", 2, receivingCountSummary);

    assertEquals(receipt.getOsdrMaster().intValue(), 1);
    assertEquals(receipt.getDeliveryNumber(), deliveryNumber);
    assertEquals(receipt.getPurchaseReferenceNumber(), "PO9999");
    assertEquals(receipt.getPurchaseReferenceLineNumber().intValue(), 2);
    assertEquals(receipt.getQuantityUom(), ReceivingConstants.Uom.VNPK);
    assertEquals(receipt.getQuantity().intValue(), 0);
    assertEquals(receipt.getEachQty().intValue(), 0);
    assertEquals(receipt.getVnpkQty().intValue(), 12);
    assertEquals(receipt.getWhpkQty().intValue(), 6);
    assertEquals(receipt.getFbOverQty().intValue(), 0);
    assertEquals(receipt.getFbShortQty().intValue(), 0);
    assertEquals(receipt.getFbDamagedQty().intValue(), 0);
    assertEquals(receipt.getFbRejectedQty().intValue(), 0);
  }

  @Test
  public void getReceiptsSummaryByPoReturnsSuccess() throws ReceivingException {
    ReceiptSummaryQtyByPoResponse response = new ReceiptSummaryQtyByPoResponse();
    response.setDeliveryNumber(21119003L);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), any(Class.class)))
        .thenReturn(defaultReceiptSummaryProcessor);
    when(defaultReceiptSummaryProcessor.getReceiptsSummaryByPo(anyLong(), any(HttpHeaders.class)))
        .thenReturn(response);

    ReceiptSummaryQtyByPoResponse receiptSummaryQtyByPoResponse =
        receiptService.getReceiptsSummaryByPo(21119003L, MockHttpHeaders.getHeaders());

    assertNotNull(receiptSummaryQtyByPoResponse);
    assertEquals((long) receiptSummaryQtyByPoResponse.getDeliveryNumber(), 21119003L);

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    verify(defaultReceiptSummaryProcessor, times(1))
        .getReceiptsSummaryByPo(anyLong(), any(HttpHeaders.class));
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp = ReceivingException.NOT_IMPLEMENTED_EXCEPTION)
  public void getDeliverySummaryByPoThrowsException() throws ReceivingException {
    ReceiptSummaryQtyByPoResponse response = new ReceiptSummaryQtyByPoResponse();
    response.setDeliveryNumber(21119003L);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), any(Class.class)))
        .thenReturn(defaultReceiptSummaryProcessor);
    doThrow(
            new ReceivingException(
                ReceivingException.NOT_IMPLEMENTED_EXCEPTION, HttpStatus.NOT_IMPLEMENTED))
        .when(defaultReceiptSummaryProcessor)
        .getReceiptsSummaryByPo(anyLong(), any(HttpHeaders.class));

    receiptService.getReceiptsSummaryByPo(21119003L, MockHttpHeaders.getHeaders());

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    verify(defaultReceiptSummaryProcessor, times(1))
        .getReceiptsSummaryByPo(anyLong(), any(HttpHeaders.class));
  }

  @Test
  public void getReceiptsSummaryByPoLineReturnsSuccess() throws ReceivingException {
    String purchaseReferenceNumber = "433234243";
    ReceiptSummaryQtyByPoLineResponse response = new ReceiptSummaryQtyByPoLineResponse();
    response.setPurchaseReferenceNumber(purchaseReferenceNumber);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), any(Class.class)))
        .thenReturn(defaultReceiptSummaryProcessor);
    when(defaultReceiptSummaryProcessor.getReceiptsSummaryByPoLine(
            anyLong(), anyString(), any(HttpHeaders.class)))
        .thenReturn(response);

    ReceiptSummaryQtyByPoLineResponse receiptSummaryQtyByPoLineResponse =
        receiptService.getReceiptsSummaryByPoLine(
            21119003L, purchaseReferenceNumber, MockHttpHeaders.getHeaders());

    assertNotNull(receiptSummaryQtyByPoLineResponse);
    assertEquals(
        receiptSummaryQtyByPoLineResponse.getPurchaseReferenceNumber(), purchaseReferenceNumber);

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    verify(defaultReceiptSummaryProcessor, times(1))
        .getReceiptsSummaryByPoLine(anyLong(), anyString(), any(HttpHeaders.class));
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp = ReceivingException.NOT_IMPLEMENTED_EXCEPTION)
  public void getReceiptsSummaryByPoLineThrowsException() throws ReceivingException {
    String purchaseReferenceNumber = "433234243";
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), any(Class.class)))
        .thenReturn(defaultReceiptSummaryProcessor);
    doThrow(
            new ReceivingException(
                ReceivingException.NOT_IMPLEMENTED_EXCEPTION, HttpStatus.NOT_IMPLEMENTED))
        .when(defaultReceiptSummaryProcessor)
        .getReceiptsSummaryByPoLine(anyLong(), anyString(), any(HttpHeaders.class));

    receiptService.getReceiptsSummaryByPoLine(
        21119003L, purchaseReferenceNumber, MockHttpHeaders.getHeaders());

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    verify(defaultReceiptSummaryProcessor, times(1))
        .getReceiptsSummaryByPoLine(anyLong(), anyString(), any(HttpHeaders.class));
  }

  @Test
  private void testGetReceivedQtyByPoReturnsReceipts() {
    Long deliveryNumber = 23232323L;
    ReceiptSummaryResponse receiptSummaryResponse = new ReceiptSummaryResponse();
    List<ReceiptSummaryResponse> receiptSummaryResponseList = new ArrayList<>();
    receiptSummaryResponse.setPurchaseReferenceLineNumber(1);
    receiptSummaryResponse.setPurchaseReferenceNumber("64434323");
    receiptSummaryResponseList.add(receiptSummaryResponse);
    when(receiptCustomRepository.receivedQtySummaryByPoInVnpkByDelivery(anyLong()))
        .thenReturn(receiptSummaryResponseList);

    List<ReceiptSummaryResponse> response =
        receiptService.getReceivedQtySummaryByPoInVnpk(deliveryNumber);
    assertNotNull(response);
    assertTrue(response.size() > 0);

    verify(receiptCustomRepository, times(1)).receivedQtySummaryByPoInVnpkByDelivery(anyLong());
  }

  @Test
  private void testGetReceivedQtyByPoReturnsEmptyReceipts() {
    Long deliveryNumber = 23232323L;
    when(receiptCustomRepository.receivedQtySummaryByPoInVnpkByDelivery(anyLong()))
        .thenReturn(Collections.emptyList());
    List<ReceiptSummaryResponse> response =
        receiptService.getReceivedQtySummaryByPoInVnpk(deliveryNumber);

    assertNotNull(response);
    assertEquals(response.size(), 0);

    verify(receiptCustomRepository, times(1)).receivedQtySummaryByPoInVnpkByDelivery(anyLong());
  }

  @Test
  private void testGetReceivedQtyByPoLineReturnsReceipts() {
    String purchaseReferenceNumber = "433234243";
    ReceiptSummaryResponse receiptSummaryResponse = new ReceiptSummaryResponse();
    List<ReceiptSummaryResponse> receiptSummaryResponseList = new ArrayList<>();
    receiptSummaryResponse.setPurchaseReferenceLineNumber(1);
    receiptSummaryResponse.setPurchaseReferenceNumber(purchaseReferenceNumber);
    receiptSummaryResponse.setReceivedQty(323L);
    receiptSummaryResponseList.add(receiptSummaryResponse);
    when(receiptCustomRepository.receivedQtySummaryByPoLineInVnpkByDelivery(anyLong(), anyString()))
        .thenReturn(receiptSummaryResponseList);

    List<ReceiptSummaryResponse> response =
        receiptService.getReceivedQtySummaryByPoLineInVnpk(deliveryNumber, purchaseReferenceNumber);
    assertNotNull(response);
    assertTrue(response.size() > 0);

    verify(receiptCustomRepository, times(1))
        .receivedQtySummaryByPoLineInVnpkByDelivery(anyLong(), anyString());
  }

  @Test
  private void testGetReceivedQtyByPoLineReturnsEmptyReceipts() {
    String purchaseReferenceNumber = "433234243";
    when(receiptCustomRepository.receivedQtySummaryByPoLineInVnpkByDelivery(anyLong(), anyString()))
        .thenReturn(Collections.emptyList());
    List<ReceiptSummaryResponse> response =
        receiptService.getReceivedQtySummaryByPoLineInVnpk(deliveryNumber, purchaseReferenceNumber);

    assertNotNull(response);
    assertEquals(response.size(), 0);

    verify(receiptCustomRepository, times(1))
        .receivedQtySummaryByPoLineInVnpkByDelivery(anyLong(), anyString());
  }

  @Test
  private void testGetReceivedQtySummaryByPoLineInEachesReturnsEmptyReceipts() {
    String purchaseReferenceNumber = "433234243";
    when(receiptCustomRepository.receivedQtySummaryInEachesByDeliveryAndPo(anyLong(), anyString()))
        .thenReturn(Collections.emptyList());
    List<ReceiptSummaryVnpkResponse> response =
        receiptService.getReceivedQtySummaryByPoLineInEaches(
            deliveryNumber, purchaseReferenceNumber);

    assertNotNull(response);
    assertEquals(response.size(), 0);

    verify(receiptCustomRepository, times(1))
        .receivedQtySummaryInEachesByDeliveryAndPo(anyLong(), anyString());
  }

  @Test
  private void testGetReceivedQtySummaryByPoLineInEachesReturnsReceipts() {
    String purchaseReferenceNumber = "433234243";
    List<ReceiptSummaryVnpkResponse> receiptSummaryVnpkResponses = new ArrayList<>();
    receiptSummaryVnpkResponses.add(
        new ReceiptSummaryVnpkResponse("9763140004", 5, 48, 4, ReceivingConstants.Uom.VNPK, 96L));
    when(receiptCustomRepository.receivedQtySummaryInEachesByDeliveryAndPo(anyLong(), anyString()))
        .thenReturn(receiptSummaryVnpkResponses);
    List<ReceiptSummaryVnpkResponse> response =
        receiptService.getReceivedQtySummaryByPoLineInEaches(
            deliveryNumber, purchaseReferenceNumber);

    assertNotNull(response);
    assertTrue(response.size() > 0);

    verify(receiptCustomRepository, times(1))
        .receivedQtySummaryInEachesByDeliveryAndPo(anyLong(), anyString());
  }

  @Test
  private void testUpdateOrderFilledQuantityInReceiptsWhenMasterOrderFilledQuantityIsNUll() {
    Receipt osdrMasterReceipt = MockReceipt.getOSDRMasterReceipt();
    osdrMasterReceipt.setOrderFilledQuantity(0);
    when(receiptRepository
            .findFirstByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndOsdrMasterOrderByCreateTsAsc(
                anyLong(), anyString(), anyInt(), anyInt()))
        .thenReturn(osdrMasterReceipt);
    Receipt updatedMasterReceipt =
        receiptService.updateOrderFilledQuantityInReceipts(MockContainer.getContainer());
    assertSame(updatedMasterReceipt.getOrderFilledQuantity(), 20);
  }

  @Test
  private void tesGetTotalReceivedQtyByDeliveriesSuccess() throws ReceivingException {
    ReceiptSummaryQtyByDeliveries receiptSummaryQtyByDeliveries =
        new ReceiptSummaryQtyByDeliveries();
    receiptSummaryQtyByDeliveries.setRcvdQtyUOM(VNPK);
    List<String> deliveries = Arrays.asList("3243434", "5332323");
    receiptSummaryQtyByDeliveries.setDeliveries(deliveries);
    List<ReceiptQtySummaryByDeliveryNumberResponse> receiptSummaryResponse = new ArrayList<>();
    ReceiptQtySummaryByDeliveryNumberResponse receiptSummaryQtyByDeliveryNumberResponse1 =
        new ReceiptQtySummaryByDeliveryNumberResponse();
    receiptSummaryQtyByDeliveryNumberResponse1.setDeliveryNumber(3243434L);
    receiptSummaryQtyByDeliveryNumberResponse1.setReceivedQty(323L);
    receiptSummaryQtyByDeliveryNumberResponse1.setReceivedQtyUom(VNPK);
    ReceiptQtySummaryByDeliveryNumberResponse receiptSummaryQtyByDeliveryNumberResponse2 =
        new ReceiptQtySummaryByDeliveryNumberResponse();
    receiptSummaryQtyByDeliveryNumberResponse2.setDeliveryNumber(5332323L);
    receiptSummaryQtyByDeliveryNumberResponse2.setReceivedQty(100L);
    receiptSummaryQtyByDeliveryNumberResponse2.setReceivedQtyUom(VNPK);
    receiptSummaryResponse.add(receiptSummaryQtyByDeliveryNumberResponse1);
    receiptSummaryResponse.add(receiptSummaryQtyByDeliveryNumberResponse2);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), any(Class.class)))
        .thenReturn(defaultReceiptSummaryProcessor);
    when(defaultReceiptSummaryProcessor.getReceiptQtySummaryByDeliveries(
            any(ReceiptSummaryQtyByDeliveries.class), any(HttpHeaders.class)))
        .thenReturn(receiptSummaryResponse);

    List<ReceiptQtySummaryByDeliveryNumberResponse> response =
        receiptService.getReceiptQtySummaryByDeliveries(
            receiptSummaryQtyByDeliveries, MockHttpHeaders.getHeaders());

    assertNotNull(response);
    assertEquals(response.size(), 2);

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    verify(defaultReceiptSummaryProcessor, times(1))
        .getReceiptQtySummaryByDeliveries(
            any(ReceiptSummaryQtyByDeliveries.class), any(HttpHeaders.class));
  }

  @Test
  private void tesGetTotalReceivedQtyByDeliveriesSuccessWithEmptyResults()
      throws ReceivingException {
    ReceiptSummaryQtyByDeliveries receiptSummaryQtyByDeliveries =
        new ReceiptSummaryQtyByDeliveries();
    receiptSummaryQtyByDeliveries.setRcvdQtyUOM(VNPK);
    List<String> deliveries = Arrays.asList("3243434", "5332323");
    receiptSummaryQtyByDeliveries.setDeliveries(deliveries);
    List<ReceiptQtySummaryByDeliveryNumberResponse> receiptSummaryResponse = new ArrayList<>();
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), any(Class.class)))
        .thenReturn(defaultReceiptSummaryProcessor);
    when(defaultReceiptSummaryProcessor.getReceiptQtySummaryByDeliveries(
            any(ReceiptSummaryQtyByDeliveries.class), any(HttpHeaders.class)))
        .thenReturn(receiptSummaryResponse);

    List<ReceiptQtySummaryByDeliveryNumberResponse> response =
        receiptService.getReceiptQtySummaryByDeliveries(
            receiptSummaryQtyByDeliveries, MockHttpHeaders.getHeaders());

    assertEquals(response.size(), 0);

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    verify(defaultReceiptSummaryProcessor, times(1))
        .getReceiptQtySummaryByDeliveries(
            any(ReceiptSummaryQtyByDeliveries.class), any(HttpHeaders.class));
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp = "Exception from RDS. Error MSG = I/O Error")
  private void tesGetTotalReceivedQtyByDeliveriesExceptionFromRDS() throws ReceivingException {
    ReceiptSummaryQtyByDeliveries receiptSummaryQtyByDeliveries =
        new ReceiptSummaryQtyByDeliveries();
    receiptSummaryQtyByDeliveries.setRcvdQtyUOM(VNPK);
    List<String> deliveries = Arrays.asList("3243434", "5332323");
    receiptSummaryQtyByDeliveries.setDeliveries(deliveries);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), any(Class.class)))
        .thenReturn(defaultReceiptSummaryProcessor);
    doThrow(
            new ReceivingException(
                String.format(ReceivingConstants.RDS_RESPONSE_ERROR_MSG, "I/O Error"),
                HttpStatus.INTERNAL_SERVER_ERROR,
                ExceptionCodes.RDS_RECEIVED_QTY_SUMMARY_BY_DELIVERY_NUMBERS))
        .when(defaultReceiptSummaryProcessor)
        .getReceiptQtySummaryByDeliveries(
            any(ReceiptSummaryQtyByDeliveries.class), any(HttpHeaders.class));

    receiptService.getReceiptQtySummaryByDeliveries(
        receiptSummaryQtyByDeliveries, MockHttpHeaders.getHeaders());

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    verify(defaultReceiptSummaryProcessor, times(1))
        .getReceiptQtySummaryByDeliveries(
            any(ReceiptSummaryQtyByDeliveries.class), any(HttpHeaders.class));
  }

  @Test
  private void testUpdateOrderFilledQuantityInReceiptsWhenMasterOrderFilledQuantityIsNotNUll() {
    Receipt osdrMasterReceipt = MockReceipt.getOSDRMasterReceipt();
    osdrMasterReceipt.setOrderFilledQuantity(20);
    when(receiptRepository
            .findFirstByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndOsdrMasterOrderByCreateTsAsc(
                anyLong(), anyString(), anyInt(), anyInt()))
        .thenReturn(osdrMasterReceipt);
    Receipt updatedMasterReceipt =
        receiptService.updateOrderFilledQuantityInReceipts(MockContainer.getContainer());
    assertSame(updatedMasterReceipt.getOrderFilledQuantity(), 40);
  }

  @Test
  private void testBuildReceiptsFromInstructionWhenNoMasterReceipt() {
    when(tenantSpecificConfigReader.getOrgUnitId()).thenReturn("2");
    List<Receipt> receipts =
        receiptService.buildReceiptsFromInstruction(
            updateInstructionRequest, problemTagId, userId, 5);
    assertSame(receipts.get(0).getOsdrMaster(), null);
    assertSame(receipts.get(0).getOrderFilledQuantity(), null);
  }

  @Test
  private void testBuildReceiptsFromInstructionWhenNoMasterReceiptOsdrUpdate() {
    when(receiptRepository
            .findFirstByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndOsdrMasterOrderByCreateTsAsc(
                anyLong(), anyString(), anyInt(), anyInt()))
        .thenReturn(null);
    List<Receipt> receipts =
        receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            deliveryDocument, "102", problemTagId, userId, 5);
    assertSame(receipts.get(0).getOsdrMaster(), 1);
    assertSame(receipts.get(0).getOrderFilledQuantity(), 0);
  }

  @Test
  private void testBuildReceiptsWithOsdrUpdateForAutoReceiveRequest_PONotExistsInReceipt()
      throws IOException {
    when(receiptRepository
            .findFirstByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndOsdrMasterOrderByCreateTsAsc(
                anyLong(), anyString(), anyInt(), anyInt()))
        .thenReturn(null);
    List<Receipt> receipts =
        receiptService.buildReceiptsWithOsdrMasterUpdate(
            getAutoReceiveRequestData(),
            MockDeliveryDocuments.getDeliveryDocumentsForSSTK().get(0).getDeliveryDocumentLines(),
            userId);
    assertSame(receipts.get(0).getOsdrMaster(), 1);
    assertSame(receipts.get(0).getQuantity(), 1);
    assertSame(receipts.get(0).getOrderFilledQuantity(), 0);
  }

  @Test
  private void testBuildReceiptsWithOsdrUpdateForAutoReceiveRequest_POAlreadyInReceipt()
      throws IOException {
    Receipt masterReceipt = MockReceipt.getOSDRMasterReceipt();
    when(receiptRepository
            .findFirstByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndOsdrMasterOrderByCreateTsAsc(
                anyLong(), anyString(), anyInt(), anyInt()))
        .thenReturn(masterReceipt);
    List<Receipt> receipts =
        receiptService.buildReceiptsWithOsdrMasterUpdate(
            getAutoReceiveRequestData(),
            MockDeliveryDocuments.getDeliveryDocumentsForSSTK().get(0).getDeliveryDocumentLines(),
            userId);
    assertSame(receipts.get(0).getOsdrMaster(), 1);
    assertSame(receipts.get(0).getQuantity(), 21);
  }

  @Test
  private void testBuildReceiptsFromInstructionWhenMasterReceiptExists() {
    Receipt masterReceipt = MockReceipt.getOSDRMasterReceipt();
    when(receiptRepository
            .findByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndOsdrMaster(
                anyLong(), anyString(), anyInt(), anyInt()))
        .thenReturn(masterReceipt);
    List<Receipt> receipts =
        receiptService.buildReceiptsFromInstruction(
            updateInstructionRequest, problemTagId, userId, 5);
    assertNull(receipts.get(0).getOsdrMaster());
    assertNull(receipts.get(0).getOrderFilledQuantity());
  }

  @Test
  private void test_findMasterReceiptByDeliveryNumber() {

    final List<Receipt> receipts1 = getReceipts();
    doReturn(receipts1)
        .when(receiptRepository)
        .findByDeliveryNumberAndOsdrMaster(anyLong(), anyInt());

    List<Receipt> receipts = receiptService.findMasterReceiptByDeliveryNumber(11L);
    final Integer osdrMaster = receipts.get(0).getOsdrMaster();
    assertNotNull(osdrMaster);
    assertEquals(osdrMaster.intValue(), 1);
  }

  @Test
  public void testFetchReceiptPOsBasedOnDelivery() {

    final List<Receipt> receipts1 = getReceipts();
    doReturn(receipts1)
        .when(receiptRepository)
        .findByDeliveryNumberAndCreateTsGreaterThanEqual(anyLong(), any());
    List<String> pos = receiptService.fetchReceiptPOsBasedOnDelivery("65789", new Date());
    assertNotNull(pos);
  }

  @Test
  public void testFetchReceiptForOsrdProcess() {
    doReturn(null).when(receiptRepository).fetchReceiptForOsrdProcess(any());
    List<ReceiptForOsrdProcess> result = receiptService.fetchReceiptForOsrdProcess(new Date());
    assertNull(result);
  }

  private AutoReceiveRequest getAutoReceiveRequestData() {
    AutoReceiveRequest autoReceiveRequest = new AutoReceiveRequest();
    autoReceiveRequest.setPurchaseReferenceNumber("9763140005");
    autoReceiveRequest.setPurchaseReferenceLineNumber(1);
    autoReceiveRequest.setQuantity(1);
    autoReceiveRequest.setDeliveryNumber(3232323L);
    return autoReceiveRequest;
  }

  @Test
  public void
      test_createReceiptsFromUpdateInstructionRequestWithOsdrMaster_createNewMasterReceipt() {
    final HttpHeaders headers = MockHttpHeaders.getHeaders();
    headers.add(ORG_UNIT_ID_HEADER, "3");
    List<Receipt> persistedReceipts =
        receiptService.createReceiptsFromUpdateInstructionRequestWithOsdrMaster(
            updateInstructionRequest, headers);

    assertNotNull(persistedReceipts);
    Receipt receipt = persistedReceipts.get(0);
    assertNotNull(receipt);

    Integer quantity = receipt.getQuantity();
    assertEquals(quantity.intValue(), 100);

    Integer orgUnitId = receipt.getOrgUnitId();
    assertEquals(orgUnitId.intValue(), 3);
  }

  @Test
  public void test_createReceiptsFromUpdateInstructionRequestWithOsdrMaster_UpdateMasterReceipt() {
    final HttpHeaders headers = MockHttpHeaders.getHeaders();
    headers.add(SUBCENTER_ID_HEADER, "3");
    headers.add(ORG_UNIT_ID_HEADER, "3");
    PoLine poLineReq = new PoLine();
    poLineReq.setRejectQty(11);
    final Receipt receipt1 = new Receipt();
    doReturn(receipt1)
        .when(receiptRepository)
        .findFirstByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndOsdrMasterOrderByCreateTsAsc(
            anyLong(), anyString(), anyInt(), anyInt());
    // execute
    List<Receipt> persistedReceipts =
        receiptService.createReceiptsFromUpdateInstructionRequestWithOsdrMaster(
            updateInstructionRequest, headers);

    assertNotNull(persistedReceipts);
    Receipt receipt = persistedReceipts.get(0);
    assertNotNull(receipt);

    Integer osdrMaster = receipt.getOsdrMaster();
    assertNull(osdrMaster);

    Integer quantity = receipt.getQuantity();
    assertEquals(quantity.intValue(), 100);

    Integer orgUnitId = receipt.getOrgUnitId();
    assertEquals(orgUnitId.intValue(), 3);
  }

  @Test
  public void testreceivedQtyByPoAndPoLinesAndDelivery() {
    List<String> purchaseReferenceNumberList = new ArrayList<>();
    Long delivery = 12345678L;
    purchaseReferenceNumberList.add("9763140004");
    purchaseReferenceNumberList.add("9763140005");
    Set<Integer> purchaseReferenceLineNumberSet = new HashSet<>();
    purchaseReferenceLineNumberSet.add(1);
    purchaseReferenceLineNumberSet.add(2);
    ReceiptSummaryEachesResponse response1 =
        new ReceiptSummaryEachesResponse("9763140004", 1, null, 1L);
    ReceiptSummaryEachesResponse response2 =
        new ReceiptSummaryEachesResponse("9763140005", 2, null, 1L);
    List<ReceiptSummaryEachesResponse> expectedResponses = new ArrayList<>();
    expectedResponses.add(response1);
    expectedResponses.add(response2);
    when(receiptCustomRepository.receivedQtyByPoAndPoLinesAndDelivery(
            anyLong(), anyList(), anySet()))
        .thenReturn(expectedResponses);
    List<ReceiptSummaryEachesResponse> actualResponses =
        receiptService.receivedQtyByPoAndPoLinesAndDelivery(
            delivery, purchaseReferenceNumberList, purchaseReferenceLineNumberSet);
    assertEquals(actualResponses, expectedResponses);
    verify(receiptCustomRepository, times(1))
        .receivedQtyByPoAndPoLinesAndDelivery(anyLong(), anyList(), anySet());
    reset(receiptCustomRepository);
  }

  @Test
  private void testGetTotalReceivedQtyByPONumbersSuccess() throws ReceivingException {
    ReceiptSummaryQtyByPos receiptSummaryQtyByPoNumbers = new ReceiptSummaryQtyByPos();
    receiptSummaryQtyByPoNumbers.setRcvdQtyUOM(VNPK);
    List<String> poNumbers = Arrays.asList("23232323", "3232424343");
    receiptSummaryQtyByPoNumbers.setPoNumbers(poNumbers);
    List<ReceiptQtySummaryByPoNumbersResponse> receiptSummaryResponseByPoNumbers =
        new ArrayList<>();
    ReceiptQtySummaryByPoNumbersResponse receiptSummaryResponsePerPoNumber1 =
        new ReceiptQtySummaryByPoNumbersResponse();
    receiptSummaryResponsePerPoNumber1.setPoNumber("23232323");
    receiptSummaryResponsePerPoNumber1.setReceivedQty(323L);
    receiptSummaryResponsePerPoNumber1.setReceivedQtyUom(VNPK);
    ReceiptQtySummaryByPoNumbersResponse receiptSummaryResponsePerPoNumber2 =
        new ReceiptQtySummaryByPoNumbersResponse();
    receiptSummaryResponsePerPoNumber2.setPoNumber("3232424343");
    receiptSummaryResponsePerPoNumber2.setReceivedQty(100L);
    receiptSummaryResponsePerPoNumber2.setReceivedQtyUom(VNPK);
    receiptSummaryResponseByPoNumbers.add(receiptSummaryResponsePerPoNumber1);
    receiptSummaryResponseByPoNumbers.add(receiptSummaryResponsePerPoNumber2);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), any(Class.class)))
        .thenReturn(defaultReceiptSummaryProcessor);
    when(defaultReceiptSummaryProcessor.getReceiptQtySummaryByPoNumbers(
            any(ReceiptSummaryQtyByPos.class), any(HttpHeaders.class)))
        .thenReturn(receiptSummaryResponseByPoNumbers);

    List<ReceiptQtySummaryByPoNumbersResponse> response =
        receiptService.getReceiptQtySummaryByPoNumbers(
            receiptSummaryQtyByPoNumbers, MockHttpHeaders.getHeaders());

    assertNotNull(response);
    assertEquals(response.size(), 2);

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    verify(defaultReceiptSummaryProcessor, times(1))
        .getReceiptQtySummaryByPoNumbers(any(ReceiptSummaryQtyByPos.class), any(HttpHeaders.class));
  }

  @Test
  private void testGetTotalReceivedQtyByPONumbersSuccessWithEmptyResults()
      throws ReceivingException {
    ReceiptSummaryQtyByPos receiptSummaryQtyByPoNumbers = new ReceiptSummaryQtyByPos();
    receiptSummaryQtyByPoNumbers.setRcvdQtyUOM(VNPK);
    List<String> poNumbers = Arrays.asList("23232323", "3232424343");
    receiptSummaryQtyByPoNumbers.setPoNumbers(poNumbers);
    List<ReceiptQtySummaryByPoNumbersResponse> receiptSummaryResponseByPoNumbers =
        new ArrayList<>();
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), anyString(), any(Class.class)))
        .thenReturn(defaultReceiptSummaryProcessor);
    when(defaultReceiptSummaryProcessor.getReceiptQtySummaryByPoNumbers(
            any(ReceiptSummaryQtyByPos.class), any(HttpHeaders.class)))
        .thenReturn(receiptSummaryResponseByPoNumbers);

    List<ReceiptQtySummaryByPoNumbersResponse> response =
        receiptService.getReceiptQtySummaryByPoNumbers(
            receiptSummaryQtyByPoNumbers, MockHttpHeaders.getHeaders());

    assertEquals(response.size(), 0);

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    verify(defaultReceiptSummaryProcessor, times(1))
        .getReceiptQtySummaryByPoNumbers(any(ReceiptSummaryQtyByPos.class), any(HttpHeaders.class));
  }

  @Test
  private void testUpdateRejects() throws ReceivingException {
    Receipt receiptReject = new Receipt();
    receiptService.updateRejects(10, "ZA", "R10", "Test", receiptReject);
    assert receiptReject.getFbRejectedQty() == 10;
    assert receiptReject.getFbRejectionComment().equalsIgnoreCase("TEST");
    assert receiptReject.getFbRejectedReasonCode() == OSDRCode.R10;
  }

  @Test
  private void testDamageRejects() throws ReceivingException {
    Receipt receiptReject = new Receipt();
    receiptService.updateDamages(10, "ZA", "D10", "VENDOR", receiptReject);
    assert receiptReject.getFbDamagedQty() == 10;
    assert receiptReject.getFbDamagedClaimType().equalsIgnoreCase("VENDOR");
    assert receiptReject.getFbDamagedReasonCode() == OSDRCode.D10;
  }

  @Test
  private void testOfflineReceiptGeneration() {
    DeliveryDocument mockDeliveryDocument = setDeliveryDocumentForOfflineRcv();
    mockDeliveryDocument.setEventType(EventType.OFFLINE_RECEIVING);
    List<Receipt> receiptServiceList =
        receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            mockDeliveryDocument,
            "doorNbr",
            "problemTagId",
            "userId",
            mockDeliveryDocument.getDeliveryDocumentLines().get(0).getVendorPack());
    assertSame(receiptServiceList.get(0).getQuantityUom(), VNPK);
    assertSame(receiptServiceList.get(0).getEachQty(), receiptServiceList.get(0).getVnpkQty());
  }

  @Test
  public void testGetReceivedQtyByProblemIdInEach() {
    ReceiptSummaryQtyByProblemIdResponse receiptSummaryQtyByProblemIdResponse =
        new ReceiptSummaryQtyByProblemIdResponse("83753657454", 1L);
    when(receiptCustomRepository.getReceivedQtyByProblemIdInEa(anyString()))
        .thenReturn(receiptSummaryQtyByProblemIdResponse);
    Long currRecvQuantity = receiptService.getReceivedQtyByProblemIdInEach("83753657454");
    assertNotNull(currRecvQuantity);
  }

  @Test
  public void testBuildReceiptsFromContainerItems() {

    List<ContainerItem> containerItems = new ArrayList<ContainerItem>();
    ContainerItem containerItem = new ContainerItem();
    containerItem.setPurchaseReferenceNumber("4624624643");
    containerItem.setPurchaseReferenceLineNumber(104);
    containerItem.setQuantity(1);
    containerItem.setVnpkQty(1);
    containerItem.setWhpkQty(1);

    containerItems.add(containerItem);
    List<Receipt> receipts =
        receiptService.buildReceiptsFromContainerItems(
            deliveryDocument.getDeliveryNumber(),
            "34782375235",
            "101",
            "4305874857",
            "sysadmin",
            containerItems);
    assertNotNull(receipts);
    assertEquals(receipts.get(0).getPurchaseReferenceNumber(), "4624624643");
  }

  public DeliveryDocument setDeliveryDocumentForOfflineRcv() {
    deliveryDocument.setDeliveryNumber(123);
    deliveryDocument.setPurchaseReferenceNumber("poNbr");
    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();

    deliveryDocumentLine.setPurchaseReferenceLineNumber(123);
    deliveryDocumentLine.setPurchaseReferenceNumber("123");
    deliveryDocumentLine.setVendorPack(123);
    deliveryDocumentLine.setWarehousePack(123);

    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);
    deliveryDocument.setAsnNumber("asnNbr");

    return deliveryDocument;
  }

  @Test
  private void testBuildReceiptsFromInstructionWhenNoMasterReceiptOsdrUpdateWithLessthanacase() {
    when(receiptRepository
            .findFirstByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndOsdrMasterOrderByCreateTsAsc(
                anyLong(), anyString(), anyInt(), anyInt()))
        .thenReturn(null);
    List<Receipt> receipts =
        receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            deliveryDocument, "102", problemTagId, userId, 5, true);
    assertSame(receipts.get(0).getOsdrMaster(), 1);
    assertSame(receipts.get(0).getOrderFilledQuantity(), 0);
    assertSame(receipts.get(0).getQuantity(), 0);
    assertSame(receipts.get(0).getQuantityUom(), ReceivingConstants.Uom.WHPK);
  }

  @Test
  private void testBuildReceiptsFromInstructionWhenNoMasterReceiptOsdrUpdateWithoutLessthanacase() {
    when(receiptRepository
            .findFirstByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndOsdrMasterOrderByCreateTsAsc(
                anyLong(), anyString(), anyInt(), anyInt()))
        .thenReturn(null);
    List<Receipt> receipts =
        receiptService.buildReceiptsFromInstructionWithOsdrMasterUpdate(
            deliveryDocument, "102", problemTagId, userId, 5, false);
    assertSame(receipts.get(0).getOsdrMaster(), 1);
    assertSame(receipts.get(0).getOrderFilledQuantity(), 0);
    assertSame(receipts.get(0).getQuantity(), 5);
    assertSame(receipts.get(0).getQuantityUom(), ReceivingConstants.Uom.VNPK);
  }

  @Test
  private void testreceivedQtyByPoAndPoLineListWithoutDelivery() throws ReceivingException {
    List<String> purchaseOrderNumberList = new ArrayList<>();
    Long deliveryNymber = 12345678L;
    purchaseOrderNumberList.add("9763140004");
    Set<Integer> purchaseOrderLineNumberSet = new HashSet<>();
    purchaseOrderLineNumberSet.add(1);
    PurchaseOrder purchaseOrder = new PurchaseOrder();
    PurchaseOrderLine purchaseOrderLine = new PurchaseOrderLine();
    purchaseOrderLine.setPoLineNumber(1);
    purchaseOrder.setPoNumber("9763140004");
    purchaseOrder.setLines(new ArrayList<>());
    purchaseOrder.getLines().add(purchaseOrderLine);
    List<PurchaseOrder> poList = new ArrayList<>();
    poList.add(purchaseOrder);
    ReceiptSummaryQtyByPoAndPoLineResponse response1 =
        new ReceiptSummaryQtyByPoAndPoLineResponse("9763140004", 1, 1L);

    List<ReceiptSummaryQtyByPoAndPoLineResponse> expectedResponses = new ArrayList<>();
    expectedResponses.add(response1);
    when(receiptCustomRepository.receivedQtyByPoAndPoLineListWithoutDelivery(
            anyList(), anySet(), anyLong()))
        .thenReturn(expectedResponses);
    List<ReceiptSummaryQtyByPoAndPoLineResponse> actualResponses =
        receiptService.getReceiptSummaryQtyByPOandPOLineResponse(poList, deliveryNumber);
    assertEquals(actualResponses, expectedResponses);
    verify(receiptCustomRepository, times(1))
        .receivedQtyByPoAndPoLineListWithoutDelivery(anyList(), anySet(), anyLong());
    reset(receiptCustomRepository);
  }
}
