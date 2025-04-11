package com.walmart.move.nim.receiving.rdc.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.AssertJUnit.*;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.client.nimrds.NimRDSRestApiClient;
import com.walmart.move.nim.receiving.core.client.nimrds.model.*;
import com.walmart.move.nim.receiving.core.client.nimrds.model.Error;
import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.mock.data.MockDeliveryDocuments;
import com.walmart.move.nim.receiving.rdc.mock.data.MockInstructionRequest;
import com.walmart.move.nim.receiving.rdc.mock.data.MockRdcInstruction;
import com.walmart.move.nim.receiving.rdc.mock.data.MockRdsResponse;
import com.walmart.move.nim.receiving.rdc.utils.RdcInstructionUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcReceivingUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.util.CollectionUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class NimRdsServiceTest {

  @Mock private NimRDSRestApiClient nimRDSRestApiClient;
  @Mock private ReceiptService receiptService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private RdcDeliveryMetaDataService rdcDeliveryMetaDataService;
  @Mock private RdcReceivingUtils rdcReceivingUtils;
  @Mock private RdcInstructionUtils rdcInstructionUtils;
  @InjectMocks private NimRdsService nimRdsService;

  private static final String facilityNum = "32818";
  private static final String countryCode = "US";
  private HttpHeaders headers;
  private static final String upcNumber = "023433232323";

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode(countryCode);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    headers = MockHttpHeaders.getHeaders(facilityNum, countryCode);
  }

  @AfterMethod
  public void resetMocks() {
    reset(nimRDSRestApiClient, receiptService, rdcDeliveryMetaDataService);
  }

  @Test
  public void testQuantityReceivedApiReturnsReceiptsForAllPoAndPoLines() throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    String poNumber =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getPurchaseReferenceNumber();
    int poLineNumber =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getPurchaseReferenceLineNumber();
    String key = poNumber + ReceivingConstants.DELIM_DASH + poLineNumber;

    when(nimRDSRestApiClient.quantityReceived(any(RdsReceiptsRequest.class), anyMap()))
        .thenReturn(MockRdsResponse.getRdsSuccessResponseForSinglePoAndPoLine());

    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        nimRdsService.getReceivedQtyByDeliveryDocuments(
            MockDeliveryDocuments.getDeliveryDocumentsForSSTK(), headers, upcNumber);

    assertNotNull(receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine());
    assertEquals(receivedQuantityResponseFromRDS.getErrorResponseMapByPoAndPoLine().size(), 0);
    assertEquals(
        receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine().get(key), Long.valueOf(1));

    verify(nimRDSRestApiClient, times(1)).quantityReceived(any(RdsReceiptsRequest.class), anyMap());
  }

  @Test
  public void testQuantityReceivedApiReturnsReceiptsForMultiPoAndPoLines_Success()
      throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForMoreThanOneSSTKPo();
    String poNumber1 =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getPurchaseReferenceNumber();
    int poLineNumber1 =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getPurchaseReferenceLineNumber();
    String key1 = poNumber1 + ReceivingConstants.DELIM_DASH + poLineNumber1;
    String poNumber2 =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getPurchaseReferenceNumber();
    int poLineNumber2 =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getPurchaseReferenceLineNumber();
    String key2 = poNumber2 + ReceivingConstants.DELIM_DASH + poLineNumber2;

    when(nimRDSRestApiClient.quantityReceived(any(RdsReceiptsRequest.class), anyMap()))
        .thenReturn(MockRdsResponse.getRdsReceivedQtySuccessResponseForDeliveryDocument());

    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        nimRdsService.getReceivedQtyByDeliveryDocuments(
            MockDeliveryDocuments.getDeliveryDocumentsForSSTK(), headers, upcNumber);

    assertNotNull(receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine());
    assertEquals(receivedQuantityResponseFromRDS.getErrorResponseMapByPoAndPoLine().size(), 0);
    assertEquals(
        receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine().get(key1),
        Long.valueOf(1));
    assertEquals(
        receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine().get(key2),
        Long.valueOf(1));

    verify(nimRDSRestApiClient, times(1)).quantityReceived(any(RdsReceiptsRequest.class), anyMap());
  }

  @Test
  public void testQuantityReceivedApiReturnsZeroWhenLineNotFoundErrorPoAndPoLine()
      throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    String poNumber =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getPurchaseReferenceNumber();
    int poLineNumber =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getPurchaseReferenceLineNumber();
    String key = poNumber + ReceivingConstants.DELIM_DASH + poLineNumber;

    when(nimRDSRestApiClient.quantityReceived(any(RdsReceiptsRequest.class), anyMap()))
        .thenReturn(MockRdsResponse.getLineNotFoundErrorResponseFromRdsForSinglePoAndPoLine());

    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        nimRdsService.getReceivedQtyByDeliveryDocuments(
            MockDeliveryDocuments.getDeliveryDocumentsForSSTK(), headers, upcNumber);

    assertTrue(receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine().size() > 0);
    assertEquals(
        receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine().get(key), Long.valueOf(0));

    verify(nimRDSRestApiClient, times(1)).quantityReceived(any(RdsReceiptsRequest.class), anyMap());
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "There is no active PO lines to receive for delivery: 60032433 and UPC: 023433232323")
  public void
      testQuantityReceivedApiReturnsErrorResponseAndThrowsNoActivePoLinesToReceiveForSinglePoPoLine()
          throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    when(nimRDSRestApiClient.quantityReceived(any(RdsReceiptsRequest.class), anyMap()))
        .thenReturn(MockRdsResponse.getRdsErrorResponseForSinglePoAndPoLine());

    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        nimRdsService.getReceivedQtyByDeliveryDocuments(deliveryDocuments, headers, upcNumber);

    assertEquals(receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine().size(), 0);
    assertEquals(receivedQuantityResponseFromRDS.getErrorResponseMapByPoAndPoLine().size(), 0);

    verify(nimRDSRestApiClient, times(1)).quantityReceived(any(RdsReceiptsRequest.class), anyMap());
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "There is no active PO lines to receive for delivery: 60032433 and UPC: 023433232323")
  public void
      testQuantityReceivedApiReturnsErrorResponseAndThrowsNoActivePoLinesToReceiveForMultiPoPoLine()
          throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    when(nimRDSRestApiClient.quantityReceived(any(RdsReceiptsRequest.class), anyMap()))
        .thenReturn(MockRdsResponse.getRdsErrorResponseForMultiPoPoLines());

    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        nimRdsService.getReceivedQtyByDeliveryDocuments(deliveryDocuments, headers, upcNumber);

    assertEquals(receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine().size(), 0);
    assertEquals(receivedQuantityResponseFromRDS.getErrorResponseMapByPoAndPoLine().size(), 0);

    verify(nimRDSRestApiClient, times(1)).quantityReceived(any(RdsReceiptsRequest.class), anyMap());
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = "Received error response: PO Not Found in RDS")
  public void testQuantityReceivedApiReturnsErrorResponseAndThrowsNoReceiptsFoundErrorMessage()
      throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    when(nimRDSRestApiClient.quantityReceived(any(RdsReceiptsRequest.class), anyMap()))
        .thenReturn(MockRdsResponse.getRdsErrorResponseForMultiPoPoLinesPoNotFound());

    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        nimRdsService.getReceivedQtyByDeliveryDocuments(deliveryDocuments, headers, upcNumber);

    assertEquals(receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine().size(), 0);
    assertEquals(receivedQuantityResponseFromRDS.getErrorResponseMapByPoAndPoLine().size(), 2);

    verify(nimRDSRestApiClient, times(1)).quantityReceived(any(RdsReceiptsRequest.class), anyMap());
  }

  @Test
  public void testQuantityReceivedApiReturnBothGoodAndBadPoPoLinesResponse() throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    String poNumber =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getPurchaseReferenceNumber();
    int poLineNumber =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getPurchaseReferenceLineNumber();
    String key = poNumber + ReceivingConstants.DELIM_DASH + poLineNumber;

    when(nimRDSRestApiClient.quantityReceived(any(RdsReceiptsRequest.class), anyMap()))
        .thenReturn(MockRdsResponse.getRdsMixedErrorResponseForMultiPoPoLines());
    doCallRealMethod()
        .when(rdcInstructionUtils)
        .filterDADeliveryDocuments(anyList(), any(InstructionRequest.class));
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        nimRdsService.getReceivedQtyByDeliveryDocuments(
            MockDeliveryDocuments.getDeliveryDocumentsForSSTK(), headers, upcNumber);

    assertEquals(receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine().size(), 1);
    assertEquals(receivedQuantityResponseFromRDS.getErrorResponseMapByPoAndPoLine().size(), 0);
    assertEquals(
        receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine().get(key), Long.valueOf(0));

    verify(nimRDSRestApiClient, times(1)).quantityReceived(any(RdsReceiptsRequest.class), anyMap());
  }

  @Test
  public void testQuantityReceivedApiReturnsSuccessAndErrorResponseForMultiPo() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForMoreThanOneSSTKPo();
    String poNumber1 =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getPurchaseReferenceNumber();
    int poLineNumber1 =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getPurchaseReferenceLineNumber();
    String key1 = poNumber1 + ReceivingConstants.DELIM_DASH + poLineNumber1;
    String poNumber2 =
        deliveryDocuments.get(1).getDeliveryDocumentLines().get(0).getPurchaseReferenceNumber();
    int poLineNumber2 =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getPurchaseReferenceLineNumber();
    String key2 = poNumber2 + ReceivingConstants.DELIM_DASH + poLineNumber2;

    when(nimRDSRestApiClient.quantityReceived(any(RdsReceiptsRequest.class), anyMap()))
        .thenReturn(MockRdsResponse.getRdsSuccessAndErrorResponseForDeliveryDocument());

    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        nimRdsService.getReceivedQtyByDeliveryDocuments(
            MockDeliveryDocuments.getDeliveryDocumentsForMoreThanOneSSTKPo(), headers, upcNumber);

    assertTrue(receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine().size() > 0);
    assertTrue(receivedQuantityResponseFromRDS.getErrorResponseMapByPoAndPoLine().size() > 0);
    assertEquals(
        receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine().get(key1),
        Long.valueOf(1));
    assertTrue(
        receivedQuantityResponseFromRDS
            .getErrorResponseMapByPoAndPoLine()
            .get(key2)
            .equalsIgnoreCase("PO Not Found"));

    verify(nimRDSRestApiClient, times(1)).quantityReceived(any(RdsReceiptsRequest.class), anyMap());
  }

  @Test
  public void testQuantityReceivedApiReturnsReceiptsForAllPoAndPoLines_NonAtlasConvertedItem()
      throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    String poNumber =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getPurchaseReferenceNumber();
    int poLineNumber =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getPurchaseReferenceLineNumber();
    String key = poNumber + ReceivingConstants.DELIM_DASH + poLineNumber;
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    when(nimRDSRestApiClient.quantityReceived(any(RdsReceiptsRequest.class), anyMap()))
        .thenReturn(MockRdsResponse.getRdsSuccessResponseForSinglePoAndPoLine());

    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        nimRdsService.getReceivedQtyByDeliveryDocuments(
            MockDeliveryDocuments.getDeliveryDocumentsForSSTK(), headers, upcNumber);

    assertNotNull(receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine());
    assertEquals(receivedQuantityResponseFromRDS.getErrorResponseMapByPoAndPoLine().size(), 0);
    assertEquals(
        receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine().get(key), Long.valueOf(1));

    verify(nimRDSRestApiClient, times(1)).quantityReceived(any(RdsReceiptsRequest.class), anyMap());
  }

  @Test
  public void testQuantityReceivedApiReturnsReceiptsForAllPoAndPoLines_AtlasConvertedItem()
      throws IOException {
    List<String> purchaseReferenceNumberList = new ArrayList<>();
    purchaseReferenceNumberList.add("8458708162");
    Set<Integer> purchaseReferenceLineNumberSet = new HashSet<>();
    purchaseReferenceLineNumberSet.add(1);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK_AtlasConvertedItem();
    String poNumber =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getPurchaseReferenceNumber();
    int poLineNumber =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getPurchaseReferenceLineNumber();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    String key = poNumber + ReceivingConstants.DELIM_DASH + poLineNumber;
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    when(receiptService.receivedQtyInVNPKByPoAndPoLineList(
            purchaseReferenceNumberList, purchaseReferenceLineNumberSet))
        .thenReturn(getReceiptSummaryQtyByPoAndPoLineResponseForSinglePOPOL());

    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        nimRdsService.getReceivedQtyByDeliveryDocuments(deliveryDocuments, headers, upcNumber);

    assertNotNull(receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine());
    assertEquals(receivedQuantityResponseFromRDS.getErrorResponseMapByPoAndPoLine().size(), 0);
    assertEquals(
        receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine().get(key),
        Long.valueOf(10));

    verify(nimRDSRestApiClient, times(0)).quantityReceived(any(RdsReceiptsRequest.class), anyMap());
  }

  @Test
  public void testEmptyReceiptsForGivenMultiPO_AtlasConvertedItem_ReceivedQtyAvailableForAllPOs()
      throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForMoreThanOneSSTKPo();
    String poNumber1 =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getPurchaseReferenceNumber();
    int poLineNumber1 =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getPurchaseReferenceLineNumber();
    String key1 = poNumber1 + ReceivingConstants.DELIM_DASH + poLineNumber1;
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    String poNumber2 =
        deliveryDocuments.get(1).getDeliveryDocumentLines().get(0).getPurchaseReferenceNumber();
    int poLineNumber2 =
        deliveryDocuments.get(1).getDeliveryDocumentLines().get(0).getPurchaseReferenceLineNumber();
    String key2 = poNumber2 + ReceivingConstants.DELIM_DASH + poLineNumber2;
    deliveryDocuments
        .get(1)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    when(receiptService.receivedQtyInVNPKByPoAndPoLineList(anyList(), any(HashSet.class)))
        .thenReturn(getReceiptSummaryQtyByPoAndPoLineResponseForMultiPO());
    doCallRealMethod()
        .when(rdcInstructionUtils)
        .filterDADeliveryDocuments(anyList(), any(InstructionRequest.class));
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        nimRdsService.getReceivedQtyByDeliveryDocuments(deliveryDocuments, headers, upcNumber);

    assertTrue(receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine().size() > 0);
    assertEquals(
        receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine().get(key1),
        Long.valueOf(10));
    assertEquals(
        receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine().get(key2),
        Long.valueOf(10));

    verify(nimRDSRestApiClient, times(0)).quantityReceived(any(RdsReceiptsRequest.class), anyMap());
  }

  @Test
  public void testEmptyReceiptsForGivenMultiPO_AtlasConvertedItem_ReceivedQtyAvailablePartially()
      throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForMoreThanOneSSTKPo();
    String poNumber1 =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getPurchaseReferenceNumber();
    int poLineNumber1 =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getPurchaseReferenceLineNumber();
    String key1 = poNumber1 + ReceivingConstants.DELIM_DASH + poLineNumber1;
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    String poNumber2 =
        deliveryDocuments.get(1).getDeliveryDocumentLines().get(0).getPurchaseReferenceNumber();
    int poLineNumber2 =
        deliveryDocuments.get(1).getDeliveryDocumentLines().get(0).getPurchaseReferenceLineNumber();
    String key2 = poNumber2 + ReceivingConstants.DELIM_DASH + poLineNumber2;
    deliveryDocuments
        .get(1)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    when(receiptService.receivedQtyInVNPKByPoAndPoLineList(anyList(), any(HashSet.class)))
        .thenReturn(getReceiptSummaryQtyByPoAndPoLineResponseForSinglePOPOL());
    doCallRealMethod()
        .when(rdcInstructionUtils)
        .filterDADeliveryDocuments(anyList(), any(InstructionRequest.class));
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        nimRdsService.getReceivedQtyByDeliveryDocuments(deliveryDocuments, headers, upcNumber);

    assertTrue(receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine().size() > 0);
    assertEquals(
        receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine().get(key1),
        Long.valueOf(10));
    assertEquals(
        receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine().get(key2),
        Long.valueOf(0));

    verify(nimRDSRestApiClient, times(0)).quantityReceived(any(RdsReceiptsRequest.class), anyMap());
  }

  @Test
  public void testEmptyReceiptsForGivenMultiPO_AtlasConvertedAndNonAtlasConvertedItems()
      throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForMoreThanOneSSTKPo();
    String poNumber1 =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getPurchaseReferenceNumber();
    int poLineNumber1 =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getPurchaseReferenceLineNumber();
    String key1 = poNumber1 + ReceivingConstants.DELIM_DASH + poLineNumber1;
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(false);
    String poNumber2 =
        deliveryDocuments.get(1).getDeliveryDocumentLines().get(0).getPurchaseReferenceNumber();
    int poLineNumber2 =
        deliveryDocuments.get(1).getDeliveryDocumentLines().get(0).getPurchaseReferenceLineNumber();
    String key2 = poNumber2 + ReceivingConstants.DELIM_DASH + poLineNumber2;
    deliveryDocuments
        .get(1)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    when(receiptService.receivedQtyInVNPKByPoAndPoLineList(anyList(), any(HashSet.class)))
        .thenReturn(getReceiptSummaryQtyByPoAndPoLineResponseForAtlasConvertedItem());
    doCallRealMethod()
        .when(rdcInstructionUtils)
        .filterDADeliveryDocuments(anyList(), any(InstructionRequest.class));
    when(nimRDSRestApiClient.quantityReceived(any(RdsReceiptsRequest.class), anyMap()))
        .thenReturn(MockRdsResponse.getRdsSuccessResponseForSinglePoAndPoLine());

    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        nimRdsService.getReceivedQtyByDeliveryDocuments(deliveryDocuments, headers, upcNumber);

    assertTrue(receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine().size() > 0);
    assertEquals(
        receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine().get(key1),
        Long.valueOf(1));
    assertEquals(
        receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine().get(key2),
        Long.valueOf(10));

    verify(nimRDSRestApiClient, times(1)).quantityReceived(any(RdsReceiptsRequest.class), anyMap());
  }

  @Test
  public void testEmptyReceiptsForGivenSinglePOPOL_AtlasConvertedItem() throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK_AtlasConvertedItem();
    String poNumber =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getPurchaseReferenceNumber();
    int poLineNumber =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getPurchaseReferenceLineNumber();
    String key = poNumber + ReceivingConstants.DELIM_DASH + poLineNumber;
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    when(receiptService.receivedQtyInVNPKByPoAndPoLineList(
            Collections.singletonList(poNumber), new HashSet<>(poLineNumber)))
        .thenReturn(Collections.emptyList());
    doCallRealMethod()
        .when(rdcInstructionUtils)
        .filterDADeliveryDocuments(anyList(), any(InstructionRequest.class));
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        nimRdsService.getReceivedQtyByDeliveryDocuments(deliveryDocuments, headers, upcNumber);

    assertTrue(receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine().size() > 0);
    assertEquals(
        receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine().get(key), Long.valueOf(0));

    verify(nimRDSRestApiClient, times(0)).quantityReceived(any(RdsReceiptsRequest.class), anyMap());
  }

  private List<ReceiptSummaryQtyByPoAndPoLineResponse>
      getReceiptSummaryQtyByPoAndPoLineResponseForSinglePOPOL() {
    ReceiptSummaryQtyByPoAndPoLineResponse response1 =
        new ReceiptSummaryQtyByPoAndPoLineResponse("8458708162", 1, 10L);
    List<ReceiptSummaryQtyByPoAndPoLineResponse> response = new ArrayList<>();
    response.add(response1);
    return response;
  }

  private List<ReceiptSummaryQtyByPoAndPoLineResponse>
      getReceiptSummaryQtyByPoAndPoLineResponseForAtlasConvertedItem() {
    ReceiptSummaryQtyByPoAndPoLineResponse response1 =
        new ReceiptSummaryQtyByPoAndPoLineResponse("8458708163", 1, 10L);
    List<ReceiptSummaryQtyByPoAndPoLineResponse> response = new ArrayList<>();
    response.add(response1);
    return response;
  }

  private List<ReceiptSummaryQtyByPoAndPoLineResponse>
      getReceiptSummaryQtyByPoAndPoLineResponseForMultiPO() {
    ReceiptSummaryQtyByPoAndPoLineResponse response1 =
        new ReceiptSummaryQtyByPoAndPoLineResponse("8458708162", 1, 10L);
    ReceiptSummaryQtyByPoAndPoLineResponse response2 =
        new ReceiptSummaryQtyByPoAndPoLineResponse("8458708163", 1, 10L);
    List<ReceiptSummaryQtyByPoAndPoLineResponse> response = new ArrayList<>();
    response.add(response1);
    response.add(response2);
    return response;
  }

  @Test
  public void testUpdateAdditionalItemDetails() throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);

    when(nimRDSRestApiClient.itemDetails(anyList(), anyMap()))
        .thenReturn(MockRdsResponse.getRdsItemDetailResponse());

    assertNull(deliveryDocumentLine.getAdditionalInfo().getPrimeSlot());
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPrimeSlotSize(), 0);
    assertNull(deliveryDocumentLine.getAdditionalInfo().getHandlingCode());
    assertNull(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode());

    nimRdsService.updateAdditionalItemDetails(deliveryDocuments, headers);

    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getPrimeSlot());
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPrimeSlotSize(), 72);
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getHandlingCode());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode());
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getHandlingCode(), "C");
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode(), "B");
    assertSame(
        deliveryDocumentLine.getAdditionalInfo().getItemHandlingMethod(), "Breakpack Conveyable");
    assertFalse(deliveryDocumentLine.getAdditionalInfo().getIsDefaultTiHiUsed());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testUpdateAdditionalItemDetails_SSTK_DoNotHavePrimeSlot() throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);

    when(nimRDSRestApiClient.itemDetails(anyList(), anyMap()))
        .thenReturn(MockRdsResponse.getRdsItemDetailResponse_NoPrimeDetails());
    assertNull(deliveryDocumentLine.getAdditionalInfo().getPrimeSlot());
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPrimeSlotSize(), 0);
    assertNull(deliveryDocumentLine.getAdditionalInfo().getHandlingCode());
    assertNull(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode());
    nimRdsService.updateAdditionalItemDetails(deliveryDocuments, headers);
  }

  @Test
  public void testUpdateAdditionalItemDetails_DADeliveryDocuments_WithPrimeSlotDetails()
      throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA_NoHandlingCode();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);

    when(nimRDSRestApiClient.itemDetails(anyList(), anyMap()))
        .thenReturn(MockRdsResponse.getRdsItemDetailResponse());

    assertNull(deliveryDocumentLine.getAdditionalInfo().getPrimeSlot());
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPrimeSlotSize(), 0);
    assertNull(deliveryDocumentLine.getAdditionalInfo().getHandlingCode());
    assertNull(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode());

    nimRdsService.updateAdditionalItemDetails(deliveryDocuments, headers);

    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getPrimeSlot());
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPrimeSlotSize(), 72);
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getHandlingCode());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode());
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getHandlingCode(), "C");
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode(), "C");
    assertSame(
        deliveryDocumentLine.getAdditionalInfo().getItemHandlingMethod(), "Casepack Conveyable");
    assertFalse(deliveryDocumentLine.getAdditionalInfo().getIsDefaultTiHiUsed());
  }

  @Test
  public void testUpdateAdditionalItemDetails_DADeliveryDocuments_WithoutPrimeSlotDetails()
      throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA_NoHandlingCode();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);

    when(nimRDSRestApiClient.itemDetails(anyList(), anyMap()))
        .thenReturn(MockRdsResponse.getRdsItemDetailResponse_NoPrimeDetails());

    assertNull(deliveryDocumentLine.getAdditionalInfo().getPrimeSlot());
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPrimeSlotSize(), 0);
    assertNull(deliveryDocumentLine.getAdditionalInfo().getHandlingCode());
    assertNull(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode());

    nimRdsService.updateAdditionalItemDetails(deliveryDocuments, headers);

    assertNull(deliveryDocumentLine.getAdditionalInfo().getPrimeSlot());
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPrimeSlotSize(), 0);
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getHandlingCode());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode());
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getHandlingCode(), "C");
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode(), "C");
    assertSame(
        deliveryDocumentLine.getAdditionalInfo().getItemHandlingMethod(), "Casepack Conveyable");
    assertFalse(deliveryDocumentLine.getAdditionalInfo().getIsDefaultTiHiUsed());
  }

  @Test
  public void testUpdateAdditionalItemDetails_ItemDetailsNotFound() throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);

    when(nimRDSRestApiClient.itemDetails(anyList(), anyMap()))
        .thenReturn(MockRdsResponse.getRdsItemDetailNotFoundResponse());
    try {
      nimRdsService.updateAdditionalItemDetails(deliveryDocuments, headers);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.ITEM_DETAILS_NOT_FOUND_IN_RDS);
      assertEquals(
          e.getDescription(),
          "Item details not found in RDS for item: 123456789 and error message: [Error: No Item Details found]");
    }
  }

  @Test
  public void
      testUpdateAdditionalItemDetails_SmartSlottingIntegrationDisabled_IqsDisabled_GetItemAndSlotDetailsFromRds()
          throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(false);
    when(nimRDSRestApiClient.itemDetails(anyList(), anyMap()))
        .thenReturn(MockRdsResponse.getRdsItemDetailResponse());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);

    assertNull(deliveryDocumentLine.getAdditionalInfo().getPrimeSlot());
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPrimeSlotSize(), 0);
    assertNull(deliveryDocumentLine.getAdditionalInfo().getHandlingCode());
    assertNull(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode());

    nimRdsService.updateAdditionalItemDetails(deliveryDocuments, headers);

    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getPrimeSlot());
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPrimeSlotSize(), 72);
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getHandlingCode());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode());
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getHandlingCode(), "C");
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode(), "B");
    assertSame(
        deliveryDocumentLine.getAdditionalInfo().getItemHandlingMethod(), "Breakpack Conveyable");
  }

  @Test
  public void
      testUpdateAdditionalItemDetails_SmartSlottingIntegrationEnabled_PrimeSlotNotReferredFromRDS_IqsDisabled_ItemDetailsFromRds()
          throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK_IQSIntegrationEnabled();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setPrimeSlotSize(64);
    deliveryDocumentLine.getAdditionalInfo().setPrimeSlot("A0001");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    when(nimRDSRestApiClient.itemDetails(anyList(), anyMap()))
        .thenReturn(MockRdsResponse.getRdsItemDetailResponse());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.SMART_SLOTTING_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getPrimeSlot());
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPrimeSlotSize(), 64);
    assertNull(deliveryDocumentLine.getAdditionalInfo().getHandlingCode());
    assertNull(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode());

    nimRdsService.updateAdditionalItemDetails(deliveryDocuments, headers);

    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPrimeSlot(), "A0001");
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPrimeSlotSize(), 64);
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getHandlingCode());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode());
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getHandlingCode(), "C");
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode(), "C");
    assertSame(
        deliveryDocumentLine.getAdditionalInfo().getItemHandlingMethod(), "Casepack Conveyable");

    verify(nimRDSRestApiClient, times(1)).itemDetails(anyList(), anyMap());
  }

  @Test
  public void
      testUpdateAdditionalItemDetails_SmartSlottingIntegrationDisabled_PrimeSlotReferredFromRDS_IqsEnabled_ItemDetailsNotFromRds()
          throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(nimRDSRestApiClient.itemDetails(anyList(), anyMap()))
        .thenReturn(MockRdsResponse.getRdsItemDetailResponse());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.SMART_SLOTTING_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    assertNull(deliveryDocumentLine.getAdditionalInfo().getPrimeSlot());
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPrimeSlotSize(), 0);

    nimRdsService.updateAdditionalItemDetails(deliveryDocuments, headers);

    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPrimeSlot(), "A1234");
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPrimeSlotSize(), 72);
    assertNull(deliveryDocumentLine.getAdditionalInfo().getHandlingCode());
    assertNull(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode());
    assertNull(deliveryDocumentLine.getAdditionalInfo().getItemHandlingMethod());

    verify(nimRDSRestApiClient, times(1)).itemDetails(anyList(), anyMap());
  }

  @Test
  public void
      testUpdateAdditionalItemDetailsReturnsInvalidHandlingMethodOrPackTypeMsgForInvalidItemHandlingMethod()
          throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);

    ItemDetailsResponseBody itemDetailsResponseBody = MockRdsResponse.getRdsItemDetailResponse();
    itemDetailsResponseBody.getFound().get(0).setHandlingCode(null);

    when(nimRDSRestApiClient.itemDetails(anyList(), anyMap())).thenReturn(itemDetailsResponseBody);

    assertNull(deliveryDocumentLine.getAdditionalInfo().getPrimeSlot());
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPrimeSlotSize(), 0);
    assertNull(deliveryDocumentLine.getAdditionalInfo().getHandlingCode());
    assertNull(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode());

    nimRdsService.updateAdditionalItemDetails(deliveryDocuments, headers);

    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getPrimeSlot());
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPrimeSlotSize(), 72);
    assertNull(deliveryDocumentLine.getAdditionalInfo().getHandlingCode());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode());
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode(), "B");
    assertSame(
        deliveryDocumentLine.getAdditionalInfo().getItemHandlingMethod(),
        ReceivingUtils.INVALID_HANDLING_METHOD_OR_PACK_TYPE);
  }

  @Test
  public void testUpdateAdditionalItemDetails_NonHazmatItem() throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);

    when(nimRDSRestApiClient.itemDetails(anyList(), anyMap()))
        .thenReturn(MockRdsResponse.getRdsItemDetailResponse());
    nimRdsService.updateAdditionalItemDetails(deliveryDocuments, headers);
    assertNotNull(deliveryDocumentLine.getAdditionalInfo());
    assertEquals(deliveryDocumentLine.getIsHazmat(), Boolean.FALSE);
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getIsHazardous(), 0);
  }

  @Test
  public void testUpdateAdditionalItemDetails_HazmatItem() throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);

    when(nimRDSRestApiClient.itemDetails(anyList(), anyMap()))
        .thenReturn(MockRdsResponse.getRdsItemDetailResponse_HazmatItem());
    nimRdsService.updateAdditionalItemDetails(deliveryDocuments, headers);
    assertNotNull(deliveryDocumentLine.getAdditionalInfo());
    assertEquals(deliveryDocumentLine.getIsHazmat(), Boolean.TRUE);
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getIsHazardous(), 1);
  }

  @Test
  public void testUpdateAdditionalItemDetails_invalidTiHi() throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);

    ItemDetailsResponseBody itemDetailsResponseBody = MockRdsResponse.getRdsItemDetailResponse();
    itemDetailsResponseBody.getFound().get(0).setTi(0);
    itemDetailsResponseBody.getFound().get(0).setHi(0);
    when(rdcInstructionUtils.isSSTKDocument(any(DeliveryDocument.class))).thenReturn(true);
    when(nimRDSRestApiClient.itemDetails(anyList(), anyMap())).thenReturn(itemDetailsResponseBody);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.SMART_SLOTTING_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);

    assertNull(deliveryDocumentLine.getAdditionalInfo().getPrimeSlot());
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPrimeSlotSize(), 0);
    assertNull(deliveryDocumentLine.getAdditionalInfo().getHandlingCode());
    assertNull(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode());

    nimRdsService.updateAdditionalItemDetails(deliveryDocuments, headers);

    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getPrimeSlot());
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPrimeSlotSize(), 72);
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getHandlingCode());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode());
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getHandlingCode(), "C");
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode(), "B");
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPalletTi(), Integer.valueOf(100));
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPalletHi(), Integer.valueOf(100));
    assertTrue(deliveryDocumentLine.getAdditionalInfo().getIsDefaultTiHiUsed());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testUpdateAdditionalItemDetails_no_prime_slot_info() throws IOException {
    List<DeliveryDocument> deliveryDocuments = MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.SMART_SLOTTING_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);

    ItemDetailsResponseBody itemDetailsResponseBody = MockRdsResponse.getRdsItemDetailResponse();
    itemDetailsResponseBody.getFound().get(0).setPrimeSlot(null);
    when(nimRDSRestApiClient.itemDetails(anyList(), anyMap())).thenReturn(itemDetailsResponseBody);

    assertNull(deliveryDocumentLine.getAdditionalInfo().getPrimeSlot());
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPrimeSlotSize(), 0);
    assertNull(deliveryDocumentLine.getAdditionalInfo().getHandlingCode());
    assertNull(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode());

    nimRdsService.updateAdditionalItemDetails(deliveryDocuments, headers);
  }

  @Test
  public void testUpdateAdditionalItemDetails_setPackTypeBasedOnBreakpackRatio()
      throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK_IQSIntegrationEnabled();
    deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setWarehousePack(2);
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setPrimeSlotSize(64);
    deliveryDocumentLine.getAdditionalInfo().setPrimeSlot("A0001");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    when(nimRDSRestApiClient.itemDetails(anyList(), anyMap()))
        .thenReturn(MockRdsResponse.getRdsItemDetailResponse());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_IQS_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.SMART_SLOTTING_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getPrimeSlot());
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPrimeSlotSize(), 64);
    assertNull(deliveryDocumentLine.getAdditionalInfo().getHandlingCode());
    assertNull(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode());

    nimRdsService.updateAdditionalItemDetails(deliveryDocuments, headers);

    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPrimeSlot(), "A0001");
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPrimeSlotSize(), 64);
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getHandlingCode());
    assertNotNull(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode());
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getHandlingCode(), "C");
    assertEquals(deliveryDocumentLine.getAdditionalInfo().getPackTypeCode(), "B");
    assertSame(
        deliveryDocumentLine.getAdditionalInfo().getItemHandlingMethod(), "Breakpack Conveyable");

    verify(nimRDSRestApiClient, times(1)).itemDetails(anyList(), anyMap());
  }

  @Test
  public void testGetReceivedQtyByDeliveryDocumentLine_HappyPath() throws IOException {
    DeliveryDocumentLine documentLine = new DeliveryDocumentLine();
    documentLine.setPurchaseReferenceLineNumber(23232122);
    documentLine.setPurchaseReferenceLineNumber(1);

    when(nimRDSRestApiClient.quantityReceived(any(RdsReceiptsRequest.class), anyMap()))
        .thenReturn(MockRdsResponse.getRdsSuccessResponseForSinglePoAndPoLine());

    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        nimRdsService.getReceivedQtyByDeliveryDocumentLine(documentLine, headers);

    assertNotNull(receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine());
    assertEquals(receivedQuantityResponseFromRDS.getErrorResponseMapByPoAndPoLine().size(), 0);
    assertTrue(receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine().size() > 0);

    verify(nimRDSRestApiClient, times(1)).quantityReceived(any(RdsReceiptsRequest.class), anyMap());
  }

  @Test
  public void testGetReceivedQtyByDeliveryDocumentLine_ErrorPath() {
    DeliveryDocumentLine documentLine = new DeliveryDocumentLine();
    documentLine.setPurchaseReferenceLineNumber(23232122);
    documentLine.setPurchaseReferenceLineNumber(1);

    when(nimRDSRestApiClient.quantityReceived(any(RdsReceiptsRequest.class), anyMap()))
        .thenReturn(MockRdsResponse.getRdsErrorResponseForSinglePoAndPoLinePoNotFound());

    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        nimRdsService.getReceivedQtyByDeliveryDocumentLine(documentLine, headers);

    assertNotNull(receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine());
    assertTrue(receivedQuantityResponseFromRDS.getErrorResponseMapByPoAndPoLine().size() > 0);
    assertEquals(receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine().size(), 0);

    verify(nimRDSRestApiClient, times(1)).quantityReceived(any(RdsReceiptsRequest.class), anyMap());
  }

  @Test
  public void testReceiveContainersInRDS_AutoSlot__withSlotSize_HappyPath() throws IOException {
    ReceiveInstructionRequest receiveInstructionRequest =
        getMockReceiveInstructionRequest_AutoSlot();
    when(nimRDSRestApiClient.receiveContainers(any(ReceiveContainersRequestBody.class), anyMap()))
        .thenReturn(MockRdsResponse.getReceiveContainersSuccessResponse());

    ReceiveContainersResponseBody receiveContainersResponseBody =
        nimRdsService.getContainerLabelFromRDS(
            getMockInstruction(), receiveInstructionRequest, headers);

    assertNotNull(receiveContainersResponseBody);
    assertTrue(CollectionUtils.isEmpty(receiveContainersResponseBody.getErrors()));
    assertTrue(!CollectionUtils.isEmpty(receiveContainersResponseBody.getReceived()));
    assertNotNull(receiveContainersResponseBody.getReceived().get(0).getLabelTrackingId());

    verify(nimRDSRestApiClient, times(1))
        .receiveContainers(any(ReceiveContainersRequestBody.class), anyMap());
  }

  @Test
  public void testReceiveContainersInRDS_ManualSlot_HappyPath() throws IOException {
    ReceiveInstructionRequest receiveInstructionRequest =
        getMockReceiveInstructionRequest_ManualSlot();
    when(nimRDSRestApiClient.receiveContainers(any(ReceiveContainersRequestBody.class), anyMap()))
        .thenReturn(MockRdsResponse.getReceiveContainersSuccessResponse());

    ReceiveContainersResponseBody receiveContainersResponseBody =
        nimRdsService.getContainerLabelFromRDS(
            getMockInstruction(), receiveInstructionRequest, headers);

    assertNotNull(receiveContainersResponseBody);
    assertTrue(CollectionUtils.isEmpty(receiveContainersResponseBody.getErrors()));
    assertTrue(!CollectionUtils.isEmpty(receiveContainersResponseBody.getReceived()));
    assertNotNull(receiveContainersResponseBody.getReceived().get(0).getLabelTrackingId());

    verify(nimRDSRestApiClient, times(1))
        .receiveContainers(any(ReceiveContainersRequestBody.class), anyMap());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testReceiveContainersInRDS_NoContainersReceived() throws IOException {
    ReceiveInstructionRequest receiveInstructionRequest =
        getMockReceiveInstructionRequest_AutoSlot();
    when(nimRDSRestApiClient.receiveContainers(any(ReceiveContainersRequestBody.class), anyMap()))
        .thenReturn(MockRdsResponse.getReceiveContainersErrorResponse());

    nimRdsService.getContainerLabelFromRDS(
        getMockInstruction(), receiveInstructionRequest, headers);

    verify(nimRDSRestApiClient, times(1))
        .receiveContainers(any(ReceiveContainersRequestBody.class), anyMap());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testReceiveContainersInRDS_ErrorPath() throws IOException {
    ReceiveInstructionRequest receiveInstructionRequest =
        getMockReceiveInstructionRequest_AutoSlot();
    Error error = MockRdsResponse.getReceiveContainersErrorResponse().getErrors().get(0);
    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.INVALID_RDS_SLOTTING_REQ,
                String.format(
                    ReceivingConstants.SLOTTING_RESOURCE_NIMRDS_RESPONSE_ERROR_MSG,
                    error.getMessage()),
                new Object[] {error.getErrorCode(), error.getMessage()}))
        .when(nimRDSRestApiClient)
        .receiveContainers(any(ReceiveContainersRequestBody.class), anyMap());

    nimRdsService.getContainerLabelFromRDS(
        getMockInstruction(), receiveInstructionRequest, headers);

    verify(nimRDSRestApiClient, times(1))
        .receiveContainers(any(ReceiveContainersRequestBody.class), anyMap());
  }

  @Test
  public void testGetReceiveContainerRequestBodyPullsTiHiFromAdditionalItemInfoAutoSlot() {
    String userId = "sysadmin";
    ReceiveInstructionRequest receiveInstructionRequest =
        getMockReceiveInstructionRequest_AutoSlot();
    DeliveryDocumentLine documentLine = receiveInstructionRequest.getDeliveryDocumentLines().get(0);
    ItemData itemData = new ItemData();
    itemData.setPalletTi(10);
    itemData.setPalletHi(10);
    documentLine.setAdditionalInfo(itemData);
    documentLine.setVendorPack(10);
    documentLine.setWarehousePack(10);
    documentLine.setPalletTie(10);
    documentLine.setPalletHigh(10);

    Map<Instruction, SlotDetails> mockInstructionToSlotMap = new HashMap<>();
    mockInstructionToSlotMap.put(getMockInstruction(), receiveInstructionRequest.getSlotDetails());

    ReceiveContainersRequestBody receiveContainersRequestBody =
        nimRdsService.getReceiveContainersRequestBody(mockInstructionToSlotMap, userId);
    assertNotNull(receiveContainersRequestBody);
    assertNotNull(receiveContainersRequestBody.getContainerOrders());
    assertTrue(receiveContainersRequestBody.getContainerOrders().size() > 0);
    assertEquals(
        receiveContainersRequestBody.getContainerOrders().get(0).getExpectedTi(),
        documentLine.getPalletTie());
    assertEquals(
        receiveContainersRequestBody.getContainerOrders().get(0).getExpectedHi(),
        documentLine.getPalletHigh());
    assertEquals(
        receiveContainersRequestBody.getContainerOrders().get(0).getSstkSlotSize(),
        receiveInstructionRequest.getSlotDetails().getSlotSize());
  }

  @Test
  public void testGetReceiveContainerRequestBodyManualSlottingWithSlot() {
    String userId = "sysadmin";
    ReceiveInstructionRequest receiveInstructionRequest =
        getMockReceiveInstructionRequest_ManualSlot();
    Map<Instruction, SlotDetails> mockInstructionToSlotMap = new HashMap<>();
    mockInstructionToSlotMap.put(getMockInstruction(), receiveInstructionRequest.getSlotDetails());

    ReceiveContainersRequestBody receiveContainersRequestBody =
        nimRdsService.getReceiveContainersRequestBody(mockInstructionToSlotMap, userId);
    assertNotNull(receiveContainersRequestBody);
    assertNotNull(receiveContainersRequestBody.getContainerOrders());
    assertTrue(receiveContainersRequestBody.getContainerOrders().size() > 0);
    assertTrue(
        Objects.nonNull(
            receiveContainersRequestBody.getContainerOrders().get(0).getSlottingOverride()));
    assertEquals(
        receiveContainersRequestBody.getContainerOrders().get(0).getSlottingOverride().getSlot(),
        receiveInstructionRequest.getSlotDetails().getSlot());
  }

  @Test
  public void testGetReceiveContainerRequestBodyForAutoSlottingWithSlotSize() {
    String userId = "sysadmin";
    ReceiveInstructionRequest receiveInstructionRequest =
        getMockReceiveInstructionRequest_ManualSlotSize();
    Map<Instruction, SlotDetails> mockInstructionToSlotMap = new HashMap<>();
    mockInstructionToSlotMap.put(getMockInstruction(), receiveInstructionRequest.getSlotDetails());

    ReceiveContainersRequestBody receiveContainersRequestBody =
        nimRdsService.getReceiveContainersRequestBody(mockInstructionToSlotMap, userId);
    assertNotNull(receiveContainersRequestBody);
    assertNotNull(receiveContainersRequestBody.getContainerOrders());
    assertTrue(receiveContainersRequestBody.getContainerOrders().size() > 0);
    assertTrue(
        Objects.isNull(
            receiveContainersRequestBody.getContainerOrders().get(0).getSlottingOverride()));
    assertEquals(
        receiveContainersRequestBody.getContainerOrders().get(0).getSstkSlotSize(),
        receiveInstructionRequest.getSlotDetails().getSlotSize());
  }

  @Test
  public void testGetReceiveContainerRequestBodyForSplitPalletAutoSlotting() {
    String userId = "sysadmin";
    BulkCompleteInstructionRequest bulkCompleteInstructionRequest =
        getMockReceiveInstructionRequestSplitPalletAutoSlot();
    Map<Instruction, SlotDetails> mockInstructionToSlotMap = new HashMap<>();
    for (CompleteMultipleInstructionData completeMultipleInstructionData :
        bulkCompleteInstructionRequest.getInstructionData()) {
      Instruction instruction = getMockInstruction();
      instruction.setInstructionSetId(32331L);
      mockInstructionToSlotMap.put(instruction, completeMultipleInstructionData.getSlotDetails());
    }

    ReceiveContainersRequestBody receiveContainersRequestBody =
        nimRdsService.getReceiveContainersRequestBody(mockInstructionToSlotMap, userId);
    assertNotNull(receiveContainersRequestBody);
    assertNotNull(receiveContainersRequestBody.getContainerOrders());
    assertTrue(receiveContainersRequestBody.getContainerOrders().size() > 0);
    assertEquals(receiveContainersRequestBody.getContainerOrders().size(), 4);
    assertTrue(
        Objects.nonNull(
            receiveContainersRequestBody.getContainerOrders().get(0).getSlottingOverride()));
    List<ContainerOrder> containerOrders =
        receiveContainersRequestBody
            .getContainerOrders()
            .stream()
            .filter(
                containerOrder ->
                    containerOrder
                        .getSlottingOverride()
                        .getSlottingType()
                        .equalsIgnoreCase(RdcConstants.RDS_SLOTTING_TYPE_SPLIT))
            .collect(Collectors.toList());
    assertEquals(containerOrders.size(), 4);
  }

  @Test
  public void testGetReceiveContainerRequestBodyForSplitPalletManualSlotting() {
    String userId = "sysadmin";
    BulkCompleteInstructionRequest bulkCompleteInstructionRequest =
        getMockReceiveInstructionRequestSplitPalletManualSlot();
    Map<Instruction, SlotDetails> mockInstructionToSlotMap = new HashMap<>();
    for (CompleteMultipleInstructionData completeMultipleInstructionData :
        bulkCompleteInstructionRequest.getInstructionData()) {
      Instruction instruction = getMockInstruction();
      instruction.setInstructionSetId(32331L);
      mockInstructionToSlotMap.put(instruction, completeMultipleInstructionData.getSlotDetails());
    }

    ReceiveContainersRequestBody receiveContainersRequestBody =
        nimRdsService.getReceiveContainersRequestBody(mockInstructionToSlotMap, userId);
    assertNotNull(receiveContainersRequestBody);
    assertNotNull(receiveContainersRequestBody.getContainerOrders());
    assertTrue(receiveContainersRequestBody.getContainerOrders().size() > 0);
    assertTrue(
        Objects.nonNull(
            receiveContainersRequestBody.getContainerOrders().get(0).getSlottingOverride()));
    List<ContainerOrder> containerOrders =
        receiveContainersRequestBody
            .getContainerOrders()
            .stream()
            .filter(
                containerOrder ->
                    containerOrder
                        .getSlottingOverride()
                        .getSlottingType()
                        .equalsIgnoreCase(RdcConstants.RDS_SLOTTING_TYPE_MANUAL))
            .collect(Collectors.toList());
    assertEquals(containerOrders.size(), 4);
    assertEquals(
        receiveContainersRequestBody.getContainerOrders().get(0).getSlottingOverride().getSlot(),
        bulkCompleteInstructionRequest.getInstructionData().get(0).getSlotDetails().getSlot());
  }

  @Test
  public void testGetReceiveContainerRequestBodyForSplitPalletAutoSlottingWithSlotSize() {
    String userId = "sysadmin";
    BulkCompleteInstructionRequest bulkCompleteInstructionRequest =
        getMockReceiveInstructionRequestSplitPalletAutoSlotWithSlotSize();
    Map<Instruction, SlotDetails> mockInstructionToSlotMap = new HashMap<>();
    for (CompleteMultipleInstructionData completeMultipleInstructionData :
        bulkCompleteInstructionRequest.getInstructionData()) {
      Instruction instruction = getMockInstruction();
      instruction.setInstructionSetId(32331L);
      mockInstructionToSlotMap.put(instruction, completeMultipleInstructionData.getSlotDetails());
    }

    ReceiveContainersRequestBody receiveContainersRequestBody =
        nimRdsService.getReceiveContainersRequestBody(mockInstructionToSlotMap, userId);
    assertNotNull(receiveContainersRequestBody);
    assertNotNull(receiveContainersRequestBody.getContainerOrders());
    assertTrue(receiveContainersRequestBody.getContainerOrders().size() > 0);
    assertTrue(
        Objects.nonNull(
            receiveContainersRequestBody.getContainerOrders().get(0).getSlottingOverride()));
    List<ContainerOrder> containerOrders =
        receiveContainersRequestBody
            .getContainerOrders()
            .stream()
            .filter(
                containerOrder ->
                    containerOrder
                        .getSlottingOverride()
                        .getSlottingType()
                        .equalsIgnoreCase(RdcConstants.RDS_SLOTTING_TYPE_SPLIT))
            .collect(Collectors.toList());
    assertEquals(containerOrders.size(), 4);
    assertEquals(
        receiveContainersRequestBody.getContainerOrders().get(0).getSstkSlotSize(),
        bulkCompleteInstructionRequest.getInstructionData().get(0).getSlotDetails().getSlotSize());
  }

  @Test
  public void testGetReceiveContainerRequestBodyPullsTiHiFromDocumentLine() {
    String userId = "sysadmin";
    ReceiveInstructionRequest receiveInstructionRequest =
        getMockReceiveInstructionRequest_AutoSlot();
    DeliveryDocumentLine documentLine = receiveInstructionRequest.getDeliveryDocumentLines().get(0);
    documentLine.setVendorPack(10);
    documentLine.setWarehousePack(10);
    documentLine.setPalletTie(10);
    documentLine.setPalletHigh(10);
    Map<Instruction, SlotDetails> mockInstructionToSlotMap = new HashMap<>();
    mockInstructionToSlotMap.put(getMockInstruction(), receiveInstructionRequest.getSlotDetails());

    ReceiveContainersRequestBody receiveContainersRequestBody =
        nimRdsService.getReceiveContainersRequestBody(mockInstructionToSlotMap, userId);
    assertNotNull(receiveContainersRequestBody);
    assertNotNull(receiveContainersRequestBody.getContainerOrders());
    assertTrue(receiveContainersRequestBody.getContainerOrders().size() > 0);
    assertEquals(
        receiveContainersRequestBody.getContainerOrders().get(0).getExpectedTi(),
        documentLine.getPalletTie());
    assertEquals(
        receiveContainersRequestBody.getContainerOrders().get(0).getExpectedHi(),
        documentLine.getPalletHigh());
    assertEquals(
        receiveContainersRequestBody.getContainerOrders().get(0).getSstkSlotSize(),
        receiveInstructionRequest.getSlotDetails().getSlotSize());
  }

  @Test
  public void testQuantityChangeApiReturnsSuccessResponse() {
    when(nimRDSRestApiClient.quantityChange(any(QuantityChangeRequestBody.class), anyMap()))
        .thenReturn(new QuantityChangeResponseBody());
    nimRdsService.quantityChange(0, "lpn", headers);
    verify(nimRDSRestApiClient, times(1))
        .quantityChange(any(QuantityChangeRequestBody.class), anyMap());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testQuantityChangeApiThrowsException() {
    when(nimRDSRestApiClient.quantityChange(any(QuantityChangeRequestBody.class), anyMap()))
        .thenThrow(
            new ReceivingBadDataException(
                ExceptionCodes.INVALID_QUANTITY_CORRECTION_REQ,
                String.format(
                    ReceivingConstants.SLOTTING_QTY_CORRECTION_BAD_RESPONSE_ERROR_MSG,
                    HttpStatus.BAD_REQUEST,
                    "Slotting Error from RDS")));
    nimRdsService.quantityChange(0, "lpn", headers);
    verify(nimRDSRestApiClient, times(1))
        .quantityChange(any(QuantityChangeRequestBody.class), anyMap());
  }

  @Test
  public void
      testGetReceiveContainersRequestBodyForDAReceiving_ReturnsOneContainerPayLoad_ScanToPrintReceiving()
          throws IOException {
    DeliveryDocumentLine deliveryDocumentLine =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0);
    deliveryDocumentLine.getAdditionalInfo().setItemPackAndHandlingCode("CC");
    ReceiveInstructionRequest receiveInstructionRequest =
        getMockReceiveInstructionRequest_One_Container();
    ReceiveContainersRequestBody receiveContainersRequestBody =
        nimRdsService.getReceiveContainersRequestBodyForDAReceiving(
            MockInstructionRequest.getInstructionRequestWithDAReceivingFeatureType(),
            deliveryDocumentLine,
            MockHttpHeaders.getHeaders(),
            1,
            receiveInstructionRequest);
    assertNotNull(receiveContainersRequestBody);
    assertEquals(receiveContainersRequestBody.getContainerOrders().size(), 1);
    assertEquals(receiveContainersRequestBody.getContainerOrders().get(0).getQty().intValue(), 1);
    assertEquals(
        receiveContainersRequestBody.getContainerOrders().get(0).getReceivedUomTxt(),
        ReceivingConstants.Uom.VNPK);
  }

  @Test
  public void
      testGetReceiveContainersRequestBodyForDAReceiving_ReturnsOneContainerPayLoad_ScanToPrintReceiving_BreakConveyPicks()
          throws IOException {
    when(rdcReceivingUtils.isWhpkReceiving(
            any(DeliveryDocumentLine.class), any(ReceiveInstructionRequest.class)))
        .thenReturn(Boolean.TRUE);
    DeliveryDocumentLine deliveryDocumentLine =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0);
    deliveryDocumentLine.getAdditionalInfo().setItemPackAndHandlingCode("BM");
    ReceiveInstructionRequest receiveInstructionRequest =
        getMockReceiveInstructionRequest_One_Container();
    ReceiveContainersRequestBody receiveContainersRequestBody =
        nimRdsService.getReceiveContainersRequestBodyForDAReceiving(
            MockInstructionRequest.getInstructionRequestWithDAReceivingFeatureType(),
            deliveryDocumentLine,
            MockHttpHeaders.getHeaders(),
            2,
            receiveInstructionRequest);
    assertNotNull(receiveContainersRequestBody);
    assertEquals(receiveContainersRequestBody.getContainerOrders().size(), 2);
    assertEquals(receiveContainersRequestBody.getContainerOrders().get(0).getQty().intValue(), 1);
    assertEquals(
        receiveContainersRequestBody.getContainerOrders().get(0).getReceivedUomTxt(),
        ReceivingConstants.Uom.WHPK);
  }

  @Test
  public void
      testGetReceiveContainersRequestBodyForDAReceiving_ReturnsOneContainerPayLoad_QtyReceiving()
          throws IOException {
    when(rdcReceivingUtils.isWhpkReceiving(
            any(DeliveryDocumentLine.class), any(ReceiveInstructionRequest.class)))
        .thenReturn(Boolean.FALSE);
    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setQuantity(25);
    DeliveryDocumentLine deliveryDocumentLine =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0);
    deliveryDocumentLine.getAdditionalInfo().setItemPackAndHandlingCode("CC");
    ReceiveContainersRequestBody receiveContainersRequestBody =
        nimRdsService.getReceiveContainersRequestBodyForDAReceiving(
            MockInstructionRequest.getInstructionRequestWithDAReceivingFeatureType(),
            deliveryDocumentLine,
            MockHttpHeaders.getHeaders(),
            25,
            receiveInstructionRequest);
    assertNotNull(receiveContainersRequestBody);
    assertEquals(receiveContainersRequestBody.getContainerOrders().size(), 25);
    assertEquals(receiveContainersRequestBody.getContainerOrders().get(0).getQty().intValue(), 1);
    assertEquals(
        receiveContainersRequestBody.getContainerOrders().get(0).getReceivedUomTxt(),
        ReceivingConstants.Uom.VNPK);
  }

  @Test
  public void
      testGetReceiveContainersRequestBodyForDAReceiving_ReturnsOneContainerPayLoad_QtyReceivingWithManualSlotting()
          throws IOException {
    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    SlotDetails slotDetails = new SlotDetails();
    slotDetails.setSlot("A0002");
    receiveInstructionRequest.setQuantity(25);
    receiveInstructionRequest.setSlotDetails(slotDetails);
    DeliveryDocumentLine deliveryDocumentLine =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0);
    deliveryDocumentLine.getAdditionalInfo().setItemPackAndHandlingCode("CC");
    ReceiveContainersRequestBody receiveContainersRequestBody =
        nimRdsService.getReceiveContainersRequestBodyForDAReceiving(
            MockInstructionRequest.getInstructionRequestWithDAReceivingFeatureType(),
            deliveryDocumentLine,
            MockHttpHeaders.getHeaders(),
            1,
            receiveInstructionRequest);
    assertNotNull(receiveContainersRequestBody);
    assertEquals(receiveContainersRequestBody.getContainerOrders().size(), 1);
    assertEquals(receiveContainersRequestBody.getContainerOrders().get(0).getQty().intValue(), 25);
    assertEquals(
        receiveContainersRequestBody.getContainerOrders().get(0).getReceivedUomTxt(),
        ReceivingConstants.Uom.VNPK);
  }

  @Test
  public void
      testGetReceiveContainersRequestBodyForDAReceiving_ReturnsOneContainerPayLoad_QtyReceivingWithManualSlotting_BreakPackConveyPicks()
          throws IOException {
    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    SlotDetails slotDetails = new SlotDetails();
    slotDetails.setSlot("A0002");
    receiveInstructionRequest.setQuantity(25);
    receiveInstructionRequest.setSlotDetails(slotDetails);
    DeliveryDocumentLine deliveryDocumentLine =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0);
    deliveryDocumentLine.getAdditionalInfo().setItemPackAndHandlingCode("BM");
    ReceiveContainersRequestBody receiveContainersRequestBody =
        nimRdsService.getReceiveContainersRequestBodyForDAReceiving(
            MockInstructionRequest.getInstructionRequestWithDAReceivingFeatureType(),
            deliveryDocumentLine,
            MockHttpHeaders.getHeaders(),
            1,
            receiveInstructionRequest);
    assertNotNull(receiveContainersRequestBody);
    assertEquals(receiveContainersRequestBody.getContainerOrders().size(), 1);
    assertEquals(receiveContainersRequestBody.getContainerOrders().get(0).getQty().intValue(), 25);
    assertEquals(
        receiveContainersRequestBody.getContainerOrders().get(0).getReceivedUomTxt(),
        ReceivingConstants.Uom.VNPK);
  }

  @Test
  public void
      testGetReceiveContainersRequestBodyForDAReceiving_ReturnsOneContainerPayLoad_QtyReceivingWithAutoSlotting()
          throws IOException {
    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    SlotDetails slotDetails = new SlotDetails();
    slotDetails.setMaxPallet(4);
    slotDetails.setSlotSize(60);
    slotDetails.setCrossReferenceDoor("001");
    slotDetails.setStockType("N");
    List<PalletQuantities> palletQuantities = new ArrayList<>();
    PalletQuantities palletQuantities1 = new PalletQuantities();
    palletQuantities1.setPallet(1);
    palletQuantities1.setQuantity(10);
    palletQuantities.add(palletQuantities1);
    PalletQuantities palletQuantities2 = new PalletQuantities();
    palletQuantities2.setPallet(2);
    palletQuantities2.setQuantity(15);
    palletQuantities.add(palletQuantities2);
    PalletQuantities palletQuantities3 = new PalletQuantities();
    palletQuantities3.setPallet(3);
    palletQuantities3.setQuantity(20);
    palletQuantities.add(palletQuantities3);
    PalletQuantities palletQuantities4 = new PalletQuantities();
    palletQuantities4.setPallet(4);
    palletQuantities4.setQuantity(25);
    palletQuantities.add(palletQuantities4);
    receiveInstructionRequest.setQuantity(25);
    receiveInstructionRequest.setPalletQuantities(palletQuantities);
    receiveInstructionRequest.setSlotDetails(slotDetails);
    DeliveryDocumentLine deliveryDocumentLine =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA()
            .get(0)
            .getDeliveryDocumentLines()
            .get(0);
    deliveryDocumentLine.getAdditionalInfo().setItemPackAndHandlingCode("CC");
    ReceiveContainersRequestBody receiveContainersRequestBody =
        nimRdsService.getReceiveContainersRequestBodyForDAReceiving(
            MockInstructionRequest.getInstructionRequestWithDAReceivingFeatureType(),
            deliveryDocumentLine,
            MockHttpHeaders.getHeaders(),
            1,
            receiveInstructionRequest);
    assertNotNull(receiveContainersRequestBody);
    assertEquals(receiveContainersRequestBody.getContainerOrders().size(), 4);
    assertEquals(receiveContainersRequestBody.getContainerOrders().get(0).getQty().intValue(), 10);
    assertEquals(receiveContainersRequestBody.getContainerOrders().get(1).getQty().intValue(), 15);
    assertEquals(receiveContainersRequestBody.getContainerOrders().get(2).getQty().intValue(), 20);
    assertEquals(receiveContainersRequestBody.getContainerOrders().get(3).getQty().intValue(), 25);
    assertEquals(
        receiveContainersRequestBody.getContainerOrders().get(0).getReceivedUomTxt(),
        ReceivingConstants.Uom.VNPK);
  }

  private ReceiveInstructionRequest getMockReceiveInstructionRequest_AutoSlot() {
    ReceiveInstructionRequest instructionRequest = new ReceiveInstructionRequest();
    DeliveryDocumentLine documentLine = new DeliveryDocumentLine();
    documentLine.setPurchaseReferenceLineNumber(23232122);
    documentLine.setPurchaseReferenceLineNumber(1);
    instructionRequest.setDoorNumber("6");
    instructionRequest.setDeliveryDocumentLines(Collections.singletonList(documentLine));
    SlotDetails slotDetails = new SlotDetails();
    slotDetails.setSlotSize(72);
    instructionRequest.setSlotDetails(slotDetails);

    instructionRequest.setQuantity(10);
    instructionRequest.setQuantityUOM("ZA");

    return instructionRequest;
  }

  private ReceiveInstructionRequest getMockReceiveInstructionRequest_One_Container() {
    ReceiveInstructionRequest instructionRequest = new ReceiveInstructionRequest();
    DeliveryDocumentLine documentLine = new DeliveryDocumentLine();
    documentLine.setPurchaseReferenceLineNumber(23232122);
    documentLine.setPurchaseReferenceLineNumber(1);
    instructionRequest.setDoorNumber("6");
    instructionRequest.setDeliveryDocumentLines(Collections.singletonList(documentLine));
    instructionRequest.setQuantity(1);
    instructionRequest.setQuantityUOM("ZA");
    instructionRequest.setIsLessThanCase(Boolean.FALSE);

    return instructionRequest;
  }

  private ReceiveInstructionRequest getMockReceiveInstructionRequest_ManualSlot() {
    ReceiveInstructionRequest instructionRequest = new ReceiveInstructionRequest();
    DeliveryDocumentLine documentLine = new DeliveryDocumentLine();
    documentLine.setPurchaseReferenceLineNumber(23232122);
    documentLine.setPurchaseReferenceLineNumber(1);
    instructionRequest.setDoorNumber("6");
    instructionRequest.setDeliveryDocumentLines(Collections.singletonList(documentLine));
    SlotDetails slotDetails = new SlotDetails();
    slotDetails.setSlot("T1978");
    instructionRequest.setSlotDetails(slotDetails);

    instructionRequest.setQuantity(10);
    instructionRequest.setQuantityUOM("ZA");
    return instructionRequest;
  }

  private ReceiveInstructionRequest getMockReceiveInstructionRequest_ManualSlotSize() {
    ReceiveInstructionRequest instructionRequest = new ReceiveInstructionRequest();
    DeliveryDocumentLine documentLine = new DeliveryDocumentLine();
    documentLine.setPurchaseReferenceLineNumber(23232122);
    documentLine.setPurchaseReferenceLineNumber(1);
    instructionRequest.setDoorNumber("6");
    instructionRequest.setDeliveryDocumentLines(Collections.singletonList(documentLine));
    SlotDetails slotDetails = new SlotDetails();
    slotDetails.setSlotSize(72);
    instructionRequest.setSlotDetails(slotDetails);

    instructionRequest.setQuantity(10);
    instructionRequest.setQuantityUOM("ZA");
    return instructionRequest;
  }

  private BulkCompleteInstructionRequest getMockReceiveInstructionRequestSplitPalletAutoSlot() {
    BulkCompleteInstructionRequest bulkCompleteInstructionRequest =
        new BulkCompleteInstructionRequest();
    List<CompleteMultipleInstructionData> completeMultipleInstructionDataList = new ArrayList<>();
    CompleteMultipleInstructionData completeMultipleInstructionData1 =
        new CompleteMultipleInstructionData();
    completeMultipleInstructionData1.setInstructionId(23232L);
    CompleteMultipleInstructionData completeMultipleInstructionData2 =
        new CompleteMultipleInstructionData();
    completeMultipleInstructionData2.setInstructionId(324233L);
    CompleteMultipleInstructionData completeMultipleInstructionData3 =
        new CompleteMultipleInstructionData();
    completeMultipleInstructionData2.setInstructionId(87654L);
    CompleteMultipleInstructionData completeMultipleInstructionData4 =
        new CompleteMultipleInstructionData();
    completeMultipleInstructionData2.setInstructionId(87655L);
    completeMultipleInstructionDataList.add(completeMultipleInstructionData1);
    completeMultipleInstructionDataList.add(completeMultipleInstructionData2);
    completeMultipleInstructionDataList.add(completeMultipleInstructionData3);
    completeMultipleInstructionDataList.add(completeMultipleInstructionData4);
    bulkCompleteInstructionRequest.setInstructionData(completeMultipleInstructionDataList);
    return bulkCompleteInstructionRequest;
  }

  private BulkCompleteInstructionRequest getMockReceiveInstructionRequestSplitPalletManualSlot() {
    BulkCompleteInstructionRequest bulkCompleteInstructionRequest =
        new BulkCompleteInstructionRequest();
    List<CompleteMultipleInstructionData> completeMultipleInstructionDataList = new ArrayList<>();
    CompleteMultipleInstructionData completeMultipleInstructionData1 =
        new CompleteMultipleInstructionData();
    completeMultipleInstructionData1.setInstructionId(23232L);
    SlotDetails slotDetails = new SlotDetails();
    slotDetails.setSlot("A0002");
    completeMultipleInstructionData1.setSlotDetails(slotDetails);
    CompleteMultipleInstructionData completeMultipleInstructionData2 =
        new CompleteMultipleInstructionData();
    completeMultipleInstructionData2.setInstructionId(324233L);
    completeMultipleInstructionData2.setSlotDetails(slotDetails);
    CompleteMultipleInstructionData completeMultipleInstructionData3 =
        new CompleteMultipleInstructionData();
    completeMultipleInstructionData3.setInstructionId(87654L);
    completeMultipleInstructionData3.setSlotDetails(slotDetails);
    CompleteMultipleInstructionData completeMultipleInstructionData4 =
        new CompleteMultipleInstructionData();
    completeMultipleInstructionData4.setInstructionId(87655L);
    completeMultipleInstructionData4.setSlotDetails(slotDetails);
    completeMultipleInstructionDataList.add(completeMultipleInstructionData1);
    completeMultipleInstructionDataList.add(completeMultipleInstructionData2);
    completeMultipleInstructionDataList.add(completeMultipleInstructionData3);
    completeMultipleInstructionDataList.add(completeMultipleInstructionData4);
    bulkCompleteInstructionRequest.setInstructionData(completeMultipleInstructionDataList);
    return bulkCompleteInstructionRequest;
  }

  private BulkCompleteInstructionRequest
      getMockReceiveInstructionRequestSplitPalletAutoSlotWithSlotSize() {
    BulkCompleteInstructionRequest bulkCompleteInstructionRequest =
        new BulkCompleteInstructionRequest();
    List<CompleteMultipleInstructionData> completeMultipleInstructionDataList = new ArrayList<>();
    CompleteMultipleInstructionData completeMultipleInstructionData1 =
        new CompleteMultipleInstructionData();
    completeMultipleInstructionData1.setInstructionId(23232L);
    SlotDetails slotDetails = new SlotDetails();
    slotDetails.setSlotSize(64);
    completeMultipleInstructionData1.setSlotDetails(slotDetails);
    CompleteMultipleInstructionData completeMultipleInstructionData2 =
        new CompleteMultipleInstructionData();
    completeMultipleInstructionData2.setInstructionId(324233L);
    completeMultipleInstructionData2.setSlotDetails(slotDetails);
    CompleteMultipleInstructionData completeMultipleInstructionData3 =
        new CompleteMultipleInstructionData();
    completeMultipleInstructionData3.setInstructionId(87654L);
    completeMultipleInstructionData3.setSlotDetails(slotDetails);
    CompleteMultipleInstructionData completeMultipleInstructionData4 =
        new CompleteMultipleInstructionData();
    completeMultipleInstructionData4.setInstructionId(87655L);
    completeMultipleInstructionData4.setSlotDetails(slotDetails);
    completeMultipleInstructionDataList.add(completeMultipleInstructionData1);
    completeMultipleInstructionDataList.add(completeMultipleInstructionData2);
    completeMultipleInstructionDataList.add(completeMultipleInstructionData3);
    completeMultipleInstructionDataList.add(completeMultipleInstructionData4);
    bulkCompleteInstructionRequest.setInstructionData(completeMultipleInstructionDataList);
    return bulkCompleteInstructionRequest;
  }

  private Instruction getMockInstruction() {
    Instruction instruction = MockRdcInstruction.getInstruction();
    instruction.setId(62323L);
    instruction.setPurchaseReferenceNumber("4223042727");
    instruction.setPurchaseReferenceLineNumber(2);
    instruction.setDeliveryNumber(3232323L);

    DeliveryDocument mockDeliveryDocument = InstructionUtils.getDeliveryDocument(instruction);

    DeliveryDocumentLine documentLine = mockDeliveryDocument.getDeliveryDocumentLines().get(0);
    ItemData itemData = new ItemData();
    itemData.setPalletTi(10);
    itemData.setPalletHi(10);
    documentLine.setAdditionalInfo(itemData);
    documentLine.setVendorPack(10);
    documentLine.setWarehousePack(10);
    documentLine.setPalletTie(10);
    documentLine.setPalletHigh(10);

    mockDeliveryDocument.setDeliveryDocumentLines(Collections.singletonList(documentLine));
    instruction.setDeliveryDocument(new Gson().toJson(mockDeliveryDocument));

    LinkedTreeMap<String, Object> move = new LinkedTreeMap<>();
    move.put(ReceivingConstants.MOVE_FROM_LOCATION, "6");
    instruction.setMove(move);
    return instruction;
  }

  @Test
  public void test_getMultipleContainerLabelsFromRds() {

    ReceiveContainersResponseBody mockReceiveContainersResponseBody =
        new ReceiveContainersResponseBody();
    doReturn(mockReceiveContainersResponseBody)
        .when(nimRDSRestApiClient)
        .receiveContainers(any(ReceiveContainersRequestBody.class), anyMap());

    SlotDetails mockSlotDetails = new SlotDetails();
    mockSlotDetails.setSlot("A120");

    Map<Instruction, SlotDetails> instructionSlotDetailsMap = new HashMap<>();
    instructionSlotDetailsMap.put(getMockInstruction(), mockSlotDetails);

    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    ReceiveContainersResponseBody multipleContainerLabelsFromRds =
        nimRdsService.getMultipleContainerLabelsFromRds(instructionSlotDetailsMap, mockHttpHeaders);

    assertNotNull(multipleContainerLabelsFromRds);
    verify(nimRDSRestApiClient, times(1))
        .receiveContainers(any(ReceiveContainersRequestBody.class), anyMap());
  }

  @Test
  public void testGetLastDoorNumberReturnsDoorThatIsPassed() {
    Instruction instruction = getMockInstruction();
    String location = instruction.getMove().get(ReceivingConstants.MOVE_FROM_LOCATION).toString();
    String deliveryNumber = instruction.getDeliveryNumber().toString();

    String doorNumber = nimRdsService.getLastDoorNumber(location, deliveryNumber);

    verify(rdcDeliveryMetaDataService, times(0)).findByDeliveryNumber(anyString());

    assertTrue(doorNumber.equalsIgnoreCase(location));
  }

  @Test
  public void testGetLastDoorNumberReturnsDoorFromDeliveryMetaDataTable() {
    Instruction instruction = getMockInstruction();

    LinkedTreeMap<String, Object> move = new LinkedTreeMap<>();
    move.put(ReceivingConstants.MOVE_FROM_LOCATION, "WRKID1");
    instruction.setMove(move);

    String location = instruction.getMove().get(ReceivingConstants.MOVE_FROM_LOCATION).toString();
    String deliveryNumber = instruction.getDeliveryNumber().toString();

    when(rdcDeliveryMetaDataService.findByDeliveryNumber(deliveryNumber))
        .thenReturn(mockDeliveryMetaData());

    String doorNumber = nimRdsService.getLastDoorNumber(location, deliveryNumber);

    verify(rdcDeliveryMetaDataService, times(1)).findByDeliveryNumber(anyString());

    assertTrue(doorNumber.equalsIgnoreCase(mockDeliveryMetaData().get().getDoorNumber()));
  }

  /**
   * This test will validate last scanned location when user scanned workstation location while
   * receiving DSDC cases.
   */
  @Test
  public void validateDSDCLastScannedDeliveryDoorWhenUserWorkstationLocationScanned() {

    Instruction instruction = getMockInstruction();
    LinkedTreeMap<String, Object> move = new LinkedTreeMap<>();
    move.put(ReceivingConstants.MOVE_FROM_LOCATION, "WRKID1");
    instruction.setMove(move);

    String location = instruction.getMove().get(ReceivingConstants.MOVE_FROM_LOCATION).toString();
    String deliveryNumber = instruction.getDeliveryNumber().toString();

    InstructionRequest instructionRequest =
        getMockInstructionRequestForDSDC("WORKSTATION23", deliveryNumber);

    when(rdcDeliveryMetaDataService.findByDeliveryNumber(deliveryNumber))
        .thenReturn(mockDeliveryMetaData());

    String doorNumber = nimRdsService.getLastDoorNumber(location, deliveryNumber);

    verify(rdcDeliveryMetaDataService, times(1)).findByDeliveryNumber(anyString());

    assertTrue(doorNumber.equalsIgnoreCase(mockDeliveryMetaData().get().getDoorNumber()));

    DsdcReceiveRequest dsdcRequest1 =
        nimRdsService.getDsdcReceiveContainerRequest(instructionRequest, headers);
    assertEquals(dsdcRequest1.getDoorNum(), mockDeliveryMetaData().get().getDoorNumber());
  }

  /**
   * This test will validate default location when user scanned workstation location while receiving
   * DSDC cases.
   */
  @Test
  public void validateDSDCDefaultDoorWhenUserScannedWorkstation() {
    DsdcReceiveRequest dsdcRequest =
        nimRdsService.getDsdcReceiveContainerRequest(
            getMockInstructionRequestForDSDC("Workstation23", "123456"), headers);
    assertEquals(dsdcRequest.getDoorNum(), ReceivingConstants.DEFAULT_DOOR);
  }

  /**
   * This test will validate actual location when user scanned valid location while receiving DSDC
   * cases.
   */
  @Test
  public void testGetLastDoorNumberReturnsDoorThatIsPassed1() {
    Instruction instruction = getMockInstruction();
    String location = instruction.getMove().get(ReceivingConstants.MOVE_FROM_LOCATION).toString();
    String deliveryNumber = instruction.getDeliveryNumber().toString();

    String doorNumber = nimRdsService.getLastDoorNumber(location, deliveryNumber);

    verify(rdcDeliveryMetaDataService, times(0)).findByDeliveryNumber(anyString());
    InstructionRequest instructionRequest =
        getMockInstructionRequestForDSDC(location, deliveryNumber);
    assertTrue(doorNumber.equalsIgnoreCase(location));
    DsdcReceiveRequest dsdcRequest =
        nimRdsService.getDsdcReceiveContainerRequest(instructionRequest, headers);
    assertEquals(dsdcRequest.getDoorNum(), location);
  }

  @Test
  public void testGetLastDoorNumberReturnsDefaultDoor() {
    Instruction instruction = getMockInstruction();

    LinkedTreeMap<String, Object> move = new LinkedTreeMap<>();
    move.put(ReceivingConstants.MOVE_FROM_LOCATION, "WRKID1");
    instruction.setMove(move);

    String location = instruction.getMove().get(ReceivingConstants.MOVE_FROM_LOCATION).toString();
    String deliveryNumber = instruction.getDeliveryNumber().toString();

    when(rdcDeliveryMetaDataService.findByDeliveryNumber(deliveryNumber))
        .thenReturn(Optional.empty());

    String doorNumber = nimRdsService.getLastDoorNumber(location, deliveryNumber);

    verify(rdcDeliveryMetaDataService, times(1)).findByDeliveryNumber(anyString());

    assertTrue(doorNumber.equalsIgnoreCase(ReceivingConstants.DEFAULT_DOOR));
  }

  @Test
  public void testBackoutDALabel_IsSuccess() {
    DABackoutLabel daBackoutLabel =
        DABackoutLabel.builder().scanTag("lpn1234").returnCode("0").returnText("Success").build();
    DALabelBackoutResponse daLabelBackoutResponse =
        DALabelBackoutResponse.builder().daBackoutLabels(Arrays.asList(daBackoutLabel)).build();

    when(nimRDSRestApiClient.labelBackout(any(DALabelBackoutRequest.class), anyMap()))
        .thenReturn(daLabelBackoutResponse);

    nimRdsService.backoutDALabels(Arrays.asList("lpn1234"), headers);

    verify(nimRDSRestApiClient, times(1)).labelBackout(any(DALabelBackoutRequest.class), anyMap());
  }

  @Test
  public void testBackoutDALabel_IsPartialSuccess() {
    List<DABackoutLabel> daBackoutLabels =
        new ArrayList<>(
            Arrays.asList(
                DABackoutLabel.builder()
                    .scanTag("lpn1234")
                    .returnCode("0")
                    .returnText("Success")
                    .build(),
                DABackoutLabel.builder()
                    .scanTag("lpn1235")
                    .returnCode("-1")
                    .returnText("A backout request has already been submitted")
                    .build()));
    DALabelBackoutResponse daLabelBackoutResponse =
        DALabelBackoutResponse.builder().daBackoutLabels(daBackoutLabels).build();

    when(nimRDSRestApiClient.labelBackout(any(DALabelBackoutRequest.class), anyMap()))
        .thenReturn(daLabelBackoutResponse);

    nimRdsService.backoutDALabels(Arrays.asList("lpn1234", "lpn1235"), headers);

    verify(nimRDSRestApiClient, times(1)).labelBackout(any(DALabelBackoutRequest.class), anyMap());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testBackoutDALabel_IsThrowsReceivingBadDataException_ForInvalidRequest() {
    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.INVALID_DA_LBL_BACKOUT_REQUEST,
                ReceivingConstants.INVALID_DA_LBL_BACKOUT_REQUEST))
        .when(nimRDSRestApiClient)
        .labelBackout(any(DALabelBackoutRequest.class), anyMap());

    nimRdsService.backoutDALabels(Arrays.asList("lpn1234"), headers);

    verify(nimRDSRestApiClient, times(1)).labelBackout(any(DALabelBackoutRequest.class), anyMap());
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testBackoutDALabel_IsThrowsReceivingInternalException_WhenRdsServiceIsDown() {
    doThrow(
            new ReceivingInternalException(
                ExceptionCodes.NIM_RDS_SERVICE_UNAVAILABLE_ERROR,
                ReceivingConstants.NIM_RDS_SERVICE_UNAVAILABLE_ERROR))
        .when(nimRDSRestApiClient)
        .labelBackout(any(DALabelBackoutRequest.class), anyMap());

    nimRdsService.backoutDALabels(Arrays.asList("lpn1234"), headers);

    verify(nimRDSRestApiClient, times(1)).labelBackout(any(DALabelBackoutRequest.class), anyMap());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testBackoutDALabel_IsThrowsReceivingBadDataException_WhenUnableToBackoutLabels() {
    DABackoutLabel daBackoutLabel =
        DABackoutLabel.builder()
            .scanTag("lpn1234")
            .returnCode("-1")
            .returnText("Too late to back out label")
            .build();
    DALabelBackoutResponse daLabelBackoutResponse =
        DALabelBackoutResponse.builder().daBackoutLabels(Arrays.asList(daBackoutLabel)).build();

    when(nimRDSRestApiClient.labelBackout(any(DALabelBackoutRequest.class), anyMap()))
        .thenReturn(daLabelBackoutResponse);

    nimRdsService.backoutDALabels(Arrays.asList("lpn1234"), headers);

    verify(nimRDSRestApiClient, times(1)).labelBackout(any(DALabelBackoutRequest.class), anyMap());
  }

  @Test
  public void testDsdcReceive_IsSuccess() {
    when(nimRDSRestApiClient.receiveDsdcPack(any(DsdcReceiveRequest.class), anyMap()))
        .thenReturn(getMockDsdcReceivingResponse());
    nimRdsService.receiveDsdcContainerInRds(getMockDsdcRequest(), headers);
    verify(nimRDSRestApiClient, times(1)).receiveDsdcPack(any(DsdcReceiveRequest.class), anyMap());
  }

  public void testDsdcReceiveAuditReview_IsSuccess() {
    when(nimRDSRestApiClient.receiveDsdcPack(any(DsdcReceiveRequest.class), anyMap()))
        .thenReturn(getMockDsdcReceivingResponseForAuditFlag());
    DsdcReceiveResponse dsdcReceiveResponse =
        nimRdsService.receiveDsdcContainerInRds(getMockDsdcRequest(), headers);
    assertNotNull(dsdcReceiveResponse);
    verify(nimRDSRestApiClient, times(1)).receiveDsdcPack(any(DsdcReceiveRequest.class), anyMap());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testDsdcReceive_IsThrowsReceivingBadDataException_ForInvalidRequest() {
    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.INVALID_DSDC_RECEIVE_REQUEST,
                ReceivingConstants.INVALID_DSDC_RECEIVE_REQUEST))
        .when(nimRDSRestApiClient)
        .receiveDsdcPack(any(DsdcReceiveRequest.class), anyMap());
    nimRdsService.receiveDsdcContainerInRds(getMockDsdcRequest(), headers);
    verify(nimRDSRestApiClient, times(1)).receiveDsdcPack(any(DsdcReceiveRequest.class), anyMap());
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testDsdcReceive_IsThrowsReceivingInternalException_WhenRdsServiceIsDown() {
    DsdcReceiveRequest dsdcReceiveRequest = getMockDsdcRequest();
    doThrow(
            new ReceivingInternalException(
                ExceptionCodes.NIM_RDS_SERVICE_UNAVAILABLE_ERROR,
                ReceivingConstants.NIM_RDS_SERVICE_UNAVAILABLE_ERROR))
        .when(nimRDSRestApiClient)
        .receiveDsdcPack(any(DsdcReceiveRequest.class), anyMap());
    nimRdsService.receiveDsdcContainerInRds(dsdcReceiveRequest, headers);
    verify(nimRDSRestApiClient, times(1)).receiveDsdcPack(any(DsdcReceiveRequest.class), anyMap());
  }

  private Optional<DeliveryMetaData> mockDeliveryMetaData() {
    DeliveryMetaData metaData = new DeliveryMetaData();
    metaData.setDeliveryNumber(getMockInstruction().getDeliveryNumber().toString());
    metaData.setDoorNumber(
        getMockInstruction().getMove().get(ReceivingConstants.MOVE_FROM_LOCATION).toString());
    Optional<DeliveryMetaData> deliveryMetaData = Optional.of(metaData);
    return deliveryMetaData;
  }

  private DsdcReceiveRequest getMockDsdcRequest() {
    return DsdcReceiveRequest.builder()
        ._id("1246caaf-8cf7-4ad9-8151-20d20a4c3210")
        .doorNum("123")
        .manifest("12345678")
        .pack_nbr("000011212345123456")
        .build();
  }

  private InstructionRequest getMockInstructionRequestForDSDC(
      String doorNumber, String deliveryNumber) {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setDeliveryNumber(deliveryNumber);
    instructionRequest.setDoorNumber(doorNumber);
    return instructionRequest;
  }

  private DsdcReceiveResponse getMockDsdcReceivingResponse() {
    return DsdcReceiveResponse.builder()
        .message("SUCCESS")
        .slot("R0002")
        .batch("617")
        .store("1")
        .div("2")
        .pocode("73")
        .dccarton("12345678")
        .dept("12")
        .event("DSDC Event")
        .hazmat("H")
        .rcvr_nbr("123456")
        .po_nbr("1234567890")
        .label_bar_code("000011212345123456")
        .packs("0")
        .unscanned("0")
        .scanned("0")
        .auditFlag("N")
        .lane_nbr("12")
        .sneEnabled("true")
        .build();
  }

  private DsdcReceiveResponse getMockDsdcReceivingResponseForAuditFlag() {
    return DsdcReceiveResponse.builder()
        .message("SUCCESS")
        .pocode("73")
        .packs("0")
        .unscanned("0")
        .scanned("1")
        .auditFlag("Y")
        .sneEnabled("true")
        .build();
  }

  @Test
  public void
      testQuantityReceivedApiReturnsReceiptsForAllPoAndPoLines_DA_AtlasConvertedItem_inProgressInLegacy_success_AtlasAndLegacyBothReceived()
          throws IOException {
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForMultipleDA();
    String poNumber =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getPurchaseReferenceNumber();
    int poLineNumber =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getPurchaseReferenceLineNumber();
    deliveryDocuments.forEach(
        deliveryDocument -> {
          deliveryDocument
              .getDeliveryDocumentLines()
              .get(0)
              .getAdditionalInfo()
              .setAtlasConvertedItem(true);
        });
    String key = poNumber + ReceivingConstants.DELIM_DASH + poLineNumber;
    when(rdcInstructionUtils.filterDADeliveryDocuments(anyList(), any(InstructionRequest.class)))
        .thenReturn(deliveryDocuments);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_LEGACY_RCVD_QTY_CHECK_ENABLED_FOR_DA_ATLAS_ITEMS,
            false))
        .thenReturn(true);
    when(receiptService.receivedQtyInVNPKByPoAndPoLineList(anyList(), anySet()))
        .thenReturn(getReceiptSummaryQtyByPoAndPoLineResponse_ForAtlasConvertedItem_MultipleDA());
    when(nimRDSRestApiClient.quantityReceived(any(RdsReceiptsRequest.class), anyMap()))
        .thenReturn(MockRdsResponse.getRdsSuccessResponseForMultiplePOAndLine_AtlasConverted());
    when(rdcInstructionUtils.isDADocument(any(DeliveryDocument.class))).thenReturn(true);
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        nimRdsService.getReceivedQtyByDeliveryDocuments(deliveryDocuments, headers, upcNumber);

    assertNotNull(receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine());
    assertEquals(receivedQuantityResponseFromRDS.getErrorResponseMapByPoAndPoLine().size(), 0);
    assertEquals(
        receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine().get(key),
        Long.valueOf(30));
    assertFalse(
        deliveryDocuments
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .isAtlasConvertedItem());
    verify(nimRDSRestApiClient, times(1)).quantityReceived(any(RdsReceiptsRequest.class), anyMap());
    verify(receiptService, times(1)).receivedQtyInVNPKByPoAndPoLineList(anyList(), anySet());
  }

  @Test
  public void
      testQuantityReceivedApiReturnsReceiptsForAllPoAndPoLines_AtlasConvertedItem_inProgressInLegacy_RdsThrowsException()
          throws IOException {
    List<String> purchaseReferenceNumberList = new ArrayList<>();
    purchaseReferenceNumberList.add("8458708164");
    purchaseReferenceNumberList.add("8458708163");
    Set<Integer> purchaseReferenceLineNumberSet = new HashSet<>();
    purchaseReferenceLineNumberSet.add(1);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForMultipleDA();
    String poNumber =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getPurchaseReferenceNumber();
    int poLineNumber =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getPurchaseReferenceLineNumber();
    deliveryDocuments.forEach(
        deliveryDocument -> {
          deliveryDocument
              .getDeliveryDocumentLines()
              .get(0)
              .getAdditionalInfo()
              .setAtlasConvertedItem(true);
        });
    String key = poNumber + ReceivingConstants.DELIM_DASH + poLineNumber;
    when(rdcInstructionUtils.filterDADeliveryDocuments(anyList(), any(InstructionRequest.class)))
        .thenReturn(deliveryDocuments);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_LEGACY_RCVD_QTY_CHECK_ENABLED_FOR_DA_ATLAS_ITEMS,
            false))
        .thenReturn(true);
    when(receiptService.receivedQtyInVNPKByPoAndPoLineList(
            purchaseReferenceNumberList, purchaseReferenceLineNumberSet))
        .thenReturn(getReceiptSummaryQtyByPoAndPoLineResponse_ForAtlasConvertedItem_MultipleDA());
    doThrow(new ReceivingBadDataException("Mock error", "Mock error"))
        .when(nimRDSRestApiClient)
        .quantityReceived(any(RdsReceiptsRequest.class), anyMap());
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        nimRdsService.getReceivedQtyByDeliveryDocuments(deliveryDocuments, headers, upcNumber);

    assertNotNull(receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine());
    assertEquals(receivedQuantityResponseFromRDS.getErrorResponseMapByPoAndPoLine().size(), 0);
    assertEquals(
        receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine().get(key), Long.valueOf(0));
    assertTrue(
        deliveryDocuments
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .isAtlasConvertedItem());
    verify(nimRDSRestApiClient, times(1)).quantityReceived(any(RdsReceiptsRequest.class), anyMap());
    verify(receiptService, times(1)).receivedQtyInVNPKByPoAndPoLineList(anyList(), anySet());
  }

  @Test
  public void
      testQuantityReceivedApiReturnsReceiptsForAllPoAndPoLines_SSTK_AtlasConvertedItem_inProgressInLegacy_success()
          throws IOException {
    // Multiple SSTK atlas converted item
    List<String> purchaseReferenceNumberList = new ArrayList<>();
    purchaseReferenceNumberList.add("8458708164");
    purchaseReferenceNumberList.add("8458708163");
    Set<Integer> purchaseReferenceLineNumberSet = new HashSet<>();
    purchaseReferenceLineNumberSet.add(1);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForMoreThanOneSSTKPo_AtlasConvertedItems();
    String poNumber =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getPurchaseReferenceNumber();
    int poLineNumber =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getPurchaseReferenceLineNumber();
    deliveryDocuments.forEach(
        deliveryDocument -> {
          deliveryDocument
              .getDeliveryDocumentLines()
              .get(0)
              .getAdditionalInfo()
              .setAtlasConvertedItem(true);
        });
    String key = poNumber + ReceivingConstants.DELIM_DASH + poLineNumber;
    when(rdcInstructionUtils.filterDADeliveryDocuments(anyList(), any(InstructionRequest.class)))
        .thenReturn(Collections.emptyList());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_LEGACY_RCVD_QTY_CHECK_ENABLED_FOR_DA_ATLAS_ITEMS,
            false))
        .thenReturn(true);
    when(receiptService.receivedQtyInVNPKByPoAndPoLineList(
            purchaseReferenceNumberList, purchaseReferenceLineNumberSet))
        .thenReturn(getReceiptSummaryQtyByPoAndPoLineResponseForMultiPO());
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        nimRdsService.getReceivedQtyByDeliveryDocuments(deliveryDocuments, headers, upcNumber);

    assertNotNull(receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine());
    assertEquals(receivedQuantityResponseFromRDS.getErrorResponseMapByPoAndPoLine().size(), 0);
    assertEquals(
        receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine().get(key), Long.valueOf(0));
    assertTrue(
        deliveryDocuments
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .isAtlasConvertedItem());
    verify(nimRDSRestApiClient, times(0)).quantityReceived(any(RdsReceiptsRequest.class), anyMap());
    verify(receiptService, times(1)).receivedQtyInVNPKByPoAndPoLineList(anyList(), anySet());
  }

  @Test
  public void
      testQuantityReceivedApiReturnsReceiptsForAllPoAndPoLines_DA_AtlasConvertedItem_NotReceivedInRDS_success()
          throws IOException {
    List<String> purchaseReferenceNumberList = new ArrayList<>();
    purchaseReferenceNumberList.add("8458708164");
    purchaseReferenceNumberList.add("8458708163");
    Set<Integer> purchaseReferenceLineNumberSet = new HashSet<>();
    purchaseReferenceLineNumberSet.add(1);
    List<DeliveryDocument> deliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForMultipleDA();
    String poNumber =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getPurchaseReferenceNumber();
    int poLineNumber =
        deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getPurchaseReferenceLineNumber();
    deliveryDocuments.forEach(
        deliveryDocument -> {
          deliveryDocument
              .getDeliveryDocumentLines()
              .get(0)
              .getAdditionalInfo()
              .setAtlasConvertedItem(true);
        });
    String key = poNumber + ReceivingConstants.DELIM_DASH + poLineNumber;
    when(rdcInstructionUtils.filterDADeliveryDocuments(anyList(), any(InstructionRequest.class)))
        .thenReturn(deliveryDocuments);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), RdcConstants.ATLAS_ITEMS_ONBOARDED, false))
        .thenReturn(true);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_LEGACY_RCVD_QTY_CHECK_ENABLED_FOR_DA_ATLAS_ITEMS,
            false))
        .thenReturn(true);
    when(receiptService.receivedQtyInVNPKByPoAndPoLineList(
            purchaseReferenceNumberList, purchaseReferenceLineNumberSet))
        .thenReturn(getReceiptSummaryQtyByPoAndPoLineResponse_ForAtlasConvertedItem_MultipleDA());
    when(nimRDSRestApiClient.quantityReceived(any(RdsReceiptsRequest.class), anyMap()))
        .thenReturn(MockRdsResponse.getRdsErrorResponseForMultiplePOAndLine_AtlasConverted());
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        nimRdsService.getReceivedQtyByDeliveryDocuments(deliveryDocuments, headers, upcNumber);

    assertNotNull(receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine());
    assertEquals(receivedQuantityResponseFromRDS.getErrorResponseMapByPoAndPoLine().size(), 0);
    assertEquals(
        receivedQuantityResponseFromRDS.getReceivedQtyMapByPoAndPoLine().get(key), Long.valueOf(0));
    assertTrue(
        deliveryDocuments
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .isAtlasConvertedItem());
    verify(nimRDSRestApiClient, times(1)).quantityReceived(any(RdsReceiptsRequest.class), anyMap());
    verify(receiptService, times(1)).receivedQtyInVNPKByPoAndPoLineList(anyList(), anySet());
  }

  private List<ReceiptSummaryQtyByPoAndPoLineResponse>
      getReceiptSummaryQtyByPoAndPoLineResponse_ForAtlasConvertedItem_MultipleDA() {
    ReceiptSummaryQtyByPoAndPoLineResponse response1 =
        new ReceiptSummaryQtyByPoAndPoLineResponse("8458708163", 1, 10L);
    ReceiptSummaryQtyByPoAndPoLineResponse response2 =
        new ReceiptSummaryQtyByPoAndPoLineResponse("8458708164", 1, 10L);
    List<ReceiptSummaryQtyByPoAndPoLineResponse> response = new ArrayList<>();
    response.add(response1);
    response.add(response2);
    return response;
  }
}
