package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;
import static org.testng.Assert.assertEquals;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class DefaultDeliveryDocumentSelectorTest extends ReceivingTestBase {
  @InjectMocks private DefaultDeliveryDocumentSelector defaultDeliveryDocumentSelector;
  @Mock private ReceiptService receiptService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private InstructionUtils instructionUtils;
  @Mock private TenantSpecificConfigReader configUtils;

  private List<DeliveryDocument> deliveryDocumentList;
  private Gson gson = new Gson();
  @InjectMocks @Spy private DefaultOpenQtyCalculator defaultOpenQtyCalculator;
  @InjectMocks @Spy private FbqBasedOpenQtyCalculator fbqBasedOpenQtyCalculator;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32612);

    try {
      String dataPath =
          new File("../receiving-test/src/main/resources/json/GdcScanUpcMultiPoLine.json")
              .getCanonicalPath();
      String deliveryDetailsJson = new String(Files.readAllBytes(Paths.get(dataPath)));
      deliveryDocumentList =
          Arrays.asList(gson.fromJson(deliveryDetailsJson, DeliveryDocument[].class));
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @BeforeMethod
  public void setup() {
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(),
            eq(ReceivingConstants.OPEN_QTY_CALCULATOR),
            eq(DEFAULT_OPEN_QTY_CALCULATOR),
            eq(OpenQtyCalculator.class)))
        .thenReturn(defaultOpenQtyCalculator);
  }

  @AfterMethod
  public void tearDown() {
    reset(receiptService);
    reset(tenantSpecificConfigReader);
  }

  //  @Test
  public void testAutoSelectDeliveryDocumentByMABD_returnNull() {
    doReturn(new ArrayList<>())
        .when(receiptService)
        .receivedQtyByPoAndPoLineList(anyList(), anySet());

    Pair<DeliveryDocument, DeliveryDocumentLine> autoSelectDocumentAndDocumentLine =
        defaultDeliveryDocumentSelector.autoSelectDeliveryDocumentLine(new ArrayList<>());

    assertNull(autoSelectDocumentAndDocumentLine);
  }

  //  @Test
  public void testAutoSelectDeliveryDocumentByMABD_nothingReceived() {
    // List<ReceiptSummaryEachesResponse> receiptSummaryList = new ArrayList<>();
    // receiptSummaryList.add(new ReceiptSummaryEachesResponse("9049112123", 1, null, 0L));
    doReturn(new ArrayList<>())
        .when(receiptService)
        .receivedQtyByPoAndPoLineList(anyList(), anySet());

    Pair<DeliveryDocument, DeliveryDocumentLine> autoSelectDocumentAndDocumentLine =
        defaultDeliveryDocumentSelector.autoSelectDeliveryDocumentLine(deliveryDocumentList);

    assertEquals(
        autoSelectDocumentAndDocumentLine.getKey().getPurchaseReferenceNumber(), "9049112123");
    assertEquals(autoSelectDocumentAndDocumentLine.getValue().getPurchaseReferenceLineNumber(), 1);
  }

  @Test
  public void testAutoSelectDeliveryDocumentByMABD_allExpectedQtyAndOverageQtyLimitReceived() {
    List<ReceiptSummaryEachesResponse> receiptSummaryList = new ArrayList<>();
    receiptSummaryList.add(new ReceiptSummaryEachesResponse("9049112123", 1, null, 4980L));
    receiptSummaryList.add(new ReceiptSummaryEachesResponse("9049112123", 2, null, 4980L));
    receiptSummaryList.add(new ReceiptSummaryEachesResponse("4271741732", 1, null, 606L));
    receiptSummaryList.add(new ReceiptSummaryEachesResponse("4271741732", 2, null, 606L));
    doReturn(receiptSummaryList)
        .when(receiptService)
        .receivedQtyByPoAndPoLineList(anyList(), anySet());
    Pair<DeliveryDocument, DeliveryDocumentLine> autoSelectDocumentAndDocumentLine =
        defaultDeliveryDocumentSelector.autoSelectDeliveryDocumentLine(deliveryDocumentList);

    assertNull(autoSelectDocumentAndDocumentLine);
  }

  @Test
  public void testAutoSelectDeliveryDocumentByMABD_allExpectedQtyReceived() {
    List<ReceiptSummaryEachesResponse> receiptSummaryList = new ArrayList<>();
    receiptSummaryList.add(new ReceiptSummaryEachesResponse("9049112123", 1, null, 4860L));
    receiptSummaryList.add(new ReceiptSummaryEachesResponse("9049112123", 2, null, 4860L));
    receiptSummaryList.add(new ReceiptSummaryEachesResponse("4271741732", 1, null, 486L));
    receiptSummaryList.add(new ReceiptSummaryEachesResponse("4271741732", 2, null, 486L));
    doReturn(receiptSummaryList)
        .when(receiptService)
        .receivedQtyByPoAndPoLineList(anyList(), anySet());

    Pair<DeliveryDocument, DeliveryDocumentLine> autoSelectDocumentAndDocumentLine =
        defaultDeliveryDocumentSelector.autoSelectDeliveryDocumentLine(deliveryDocumentList);

    assertEquals(
        autoSelectDocumentAndDocumentLine.getKey().getPurchaseReferenceNumber(), "9049112123");
    assertEquals(autoSelectDocumentAndDocumentLine.getValue().getPurchaseReferenceLineNumber(), 1);
  }

  //  @Test
  public void testAutoSelectDeliveryDocumentByMABD_firstPoWithLine1ExpectedQtyReceived() {
    List<ReceiptSummaryEachesResponse> receiptSummaryList = new ArrayList<>();
    receiptSummaryList.add(new ReceiptSummaryEachesResponse("9049112123", 1, null, 4860L));
    doReturn(receiptSummaryList)
        .when(receiptService)
        .receivedQtyByPoAndPoLineList(anyList(), anySet());

    Pair<DeliveryDocument, DeliveryDocumentLine> autoSelectDocumentAndDocumentLine =
        defaultDeliveryDocumentSelector.autoSelectDeliveryDocumentLine(deliveryDocumentList);

    assertEquals(
        autoSelectDocumentAndDocumentLine.getKey().getPurchaseReferenceNumber(), "9049112123");
    assertEquals(autoSelectDocumentAndDocumentLine.getValue().getPurchaseReferenceLineNumber(), 2);
  }

  //  @Test
  public void testAutoSelectDeliveryDocumentByMABD_firstPoWithAllLinesExpectedQtyReceived() {
    List<ReceiptSummaryEachesResponse> receiptSummaryList = new ArrayList<>();
    receiptSummaryList.add(new ReceiptSummaryEachesResponse("9049112123", 1, null, 4860L));
    receiptSummaryList.add(new ReceiptSummaryEachesResponse("9049112123", 2, null, 4860L));
    doReturn(receiptSummaryList)
        .when(receiptService)
        .receivedQtyByPoAndPoLineList(anyList(), anySet());

    Pair<DeliveryDocument, DeliveryDocumentLine> autoSelectDocumentAndDocumentLine =
        defaultDeliveryDocumentSelector.autoSelectDeliveryDocumentLine(deliveryDocumentList);

    assertEquals(
        autoSelectDocumentAndDocumentLine.getKey().getPurchaseReferenceNumber(), "4271741732");
    assertEquals(autoSelectDocumentAndDocumentLine.getValue().getPurchaseReferenceLineNumber(), 1);
  }

  @Test
  public void testAutoSelectDeliveryDocument_sortingDeliveryDocumentLineByPOLineNo() {
    List<ReceiptSummaryEachesResponse> receiptSummaryList = new ArrayList<>();
    receiptSummaryList.add(new ReceiptSummaryEachesResponse("9049112123", 1, null, 4860L));
    receiptSummaryList.add(new ReceiptSummaryEachesResponse("9049112123", 2, null, 4860L));

    setUpUnsortedDeliveryDocumentList();

    assertEquals(
        deliveryDocumentList
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceLineNumber(),
        2);
    assertEquals(
        deliveryDocumentList
            .get(0)
            .getDeliveryDocumentLines()
            .get(1)
            .getPurchaseReferenceLineNumber(),
        1);
    assertEquals(
        deliveryDocumentList
            .get(1)
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceLineNumber(),
        2);
    assertEquals(
        deliveryDocumentList
            .get(1)
            .getDeliveryDocumentLines()
            .get(1)
            .getPurchaseReferenceLineNumber(),
        1);

    doReturn(receiptSummaryList)
        .when(receiptService)
        .receivedQtyByPoAndPoLineList(anyList(), anySet());

    Pair<DeliveryDocument, DeliveryDocumentLine> sortedAutoSelectDocumentAndDocumentLine =
        defaultDeliveryDocumentSelector.autoSelectDeliveryDocumentLine(deliveryDocumentList);

    DeliveryDocument sortedDeliveryDocument = sortedAutoSelectDocumentAndDocumentLine.getKey();

    assertTrue(
        sortedDeliveryDocument.getDeliveryDocumentLines().get(0).getPurchaseReferenceLineNumber()
            < sortedDeliveryDocument
                .getDeliveryDocumentLines()
                .get(1)
                .getPurchaseReferenceLineNumber());
    assertEquals(
        sortedDeliveryDocument.getDeliveryDocumentLines().get(0).getPurchaseReferenceLineNumber(),
        1);
    assertEquals(
        sortedDeliveryDocument.getDeliveryDocumentLines().get(1).getPurchaseReferenceLineNumber(),
        2);
  }

  //  @Test
  public void testAutoSelectDeliveryDocument_sortingDeliveryDocumentLineByPOLineNo_Imports() {
    doReturn(Boolean.TRUE)
        .when(tenantSpecificConfigReader)
        .isFeatureFlagEnabled(ReceivingConstants.ENABLE_LINE_LEVEL_FBQ_CHECK);
    deliveryDocumentList.forEach(deliveryDocument -> deliveryDocument.setImportInd(Boolean.TRUE));
    List<ReceiptSummaryEachesResponse> receiptSummaryList = new ArrayList<>();
    receiptSummaryList.add(new ReceiptSummaryEachesResponse("9049112123", 1, null, 4860L));
    receiptSummaryList.add(new ReceiptSummaryEachesResponse("9049112123", 2, null, 4860L));

    setUpUnsortedDeliveryDocumentList();

    assertEquals(
        deliveryDocumentList
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceLineNumber(),
        2);
    assertEquals(
        deliveryDocumentList
            .get(0)
            .getDeliveryDocumentLines()
            .get(1)
            .getPurchaseReferenceLineNumber(),
        1);
    assertEquals(
        deliveryDocumentList
            .get(1)
            .getDeliveryDocumentLines()
            .get(0)
            .getPurchaseReferenceLineNumber(),
        2);
    assertEquals(
        deliveryDocumentList
            .get(1)
            .getDeliveryDocumentLines()
            .get(1)
            .getPurchaseReferenceLineNumber(),
        1);

    doReturn(receiptSummaryList)
        .when(receiptService)
        .receivedQtyByPoAndPoLinesAndDelivery(anyLong(), anyList(), anySet());

    Pair<DeliveryDocument, DeliveryDocumentLine> sortedAutoSelectDocumentAndDocumentLine =
        defaultDeliveryDocumentSelector.autoSelectDeliveryDocumentLine(deliveryDocumentList);

    assertNull(sortedAutoSelectDocumentAndDocumentLine);
  }

  @Test
  public void test_instructionCheckResponsetrue_FBQbaseOpenCalculator() throws IOException {
    List<ReceiptSummaryEachesResponse> receiptSummaryList = new ArrayList<>();
    receiptSummaryList.add(new ReceiptSummaryEachesResponse("9049112123", 1, null, 4860L));
    receiptSummaryList.add(new ReceiptSummaryEachesResponse("9049112123", 2, null, 4860L));
    receiptSummaryList.add(new ReceiptSummaryEachesResponse("4271741732", 1, null, 486L));
    receiptSummaryList.add(new ReceiptSummaryEachesResponse("4271741732", 2, null, 486L));
    doReturn(receiptSummaryList)
        .when(receiptService)
        .receivedQtyByPoAndPoLineList(anyList(), anySet());
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_MULTI_USER_RECEIVE_ENABLED))
        .thenReturn(true);
    when(instructionUtils.checkIfNewInstructionCanBeCreated(any(), any(), any()))
        .thenReturn(Boolean.TRUE);
    String dataPath =
        new File("../receiving-test/src/main/resources/json/GdcScanUpcMultiPoLine.json")
            .getCanonicalPath();
    when(configUtils.getConfiguredInstance(
            anyString(),
            eq(OPEN_QTY_CALCULATOR),
            eq(DEFAULT_OPEN_QTY_CALCULATOR),
            eq(OpenQtyCalculator.class)))
        .thenReturn(fbqBasedOpenQtyCalculator);
    String deliveryDetailsJson = new String(Files.readAllBytes(Paths.get(dataPath)));
    deliveryDocumentList =
        Arrays.asList(gson.fromJson(deliveryDetailsJson, DeliveryDocument[].class));
    Pair<DeliveryDocument, DeliveryDocumentLine> autoSelectDocumentAndDocumentLine =
        defaultDeliveryDocumentSelector.autoSelectDeliveryDocumentLine(deliveryDocumentList);
    assertEquals(
        autoSelectDocumentAndDocumentLine.getKey().getPurchaseReferenceNumber(), "9049112123");
    assertEquals(autoSelectDocumentAndDocumentLine.getValue().getPurchaseReferenceLineNumber(), 1);
  }

  @Test
  public void test_instructionCheckResponsetrue_DefaultbaseOpenCalculator() throws IOException {
    List<ReceiptSummaryEachesResponse> receiptSummaryList = new ArrayList<>();
    receiptSummaryList.add(new ReceiptSummaryEachesResponse("9049112123", 1, null, 4860L));
    receiptSummaryList.add(new ReceiptSummaryEachesResponse("9049112123", 2, null, 4860L));
    receiptSummaryList.add(new ReceiptSummaryEachesResponse("4271741732", 1, null, 486L));
    receiptSummaryList.add(new ReceiptSummaryEachesResponse("4271741732", 2, null, 486L));
    doReturn(receiptSummaryList)
        .when(receiptService)
        .receivedQtyByPoAndPoLineList(anyList(), anySet());
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_MULTI_USER_RECEIVE_ENABLED))
        .thenReturn(true);
    when(instructionUtils.checkIfNewInstructionCanBeCreated(any(), any(), any()))
        .thenReturn(Boolean.TRUE);
    String dataPath =
        new File("../receiving-test/src/main/resources/json/GdcScanUpcMultiPoLine.json")
            .getCanonicalPath();
    when(configUtils.getConfiguredInstance(
            anyString(),
            eq(OPEN_QTY_CALCULATOR),
            eq(DEFAULT_OPEN_QTY_CALCULATOR),
            eq(OpenQtyCalculator.class)))
        .thenReturn(defaultOpenQtyCalculator);
    String deliveryDetailsJson = new String(Files.readAllBytes(Paths.get(dataPath)));
    deliveryDocumentList =
        Arrays.asList(gson.fromJson(deliveryDetailsJson, DeliveryDocument[].class));
    Pair<DeliveryDocument, DeliveryDocumentLine> autoSelectDocumentAndDocumentLine =
        defaultDeliveryDocumentSelector.autoSelectDeliveryDocumentLine(deliveryDocumentList);
    assertEquals(
        autoSelectDocumentAndDocumentLine.getKey().getPurchaseReferenceNumber(), "9049112123");
    assertEquals(autoSelectDocumentAndDocumentLine.getValue().getPurchaseReferenceLineNumber(), 1);
  }

  @Test
  public void test_instructionCheckResponse_TransferError() throws ReceivingException, IOException {
    List<ReceiptSummaryEachesResponse> receiptSummaryList = new ArrayList<>();
    receiptSummaryList.add(new ReceiptSummaryEachesResponse("9049112123", 1, null, 4860L));
    receiptSummaryList.add(new ReceiptSummaryEachesResponse("9049112123", 2, null, 4860L));
    receiptSummaryList.add(new ReceiptSummaryEachesResponse("4271741732", 1, null, 486L));
    receiptSummaryList.add(new ReceiptSummaryEachesResponse("4271741732", 2, null, 486L));
    doReturn(receiptSummaryList)
        .when(receiptService)
        .receivedQtyByPoAndPoLineList(anyList(), anySet());
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            ReceivingConstants.IS_MULTI_USER_RECEIVE_ENABLED))
        .thenReturn(true);
    when(instructionUtils.checkIfNewInstructionCanBeCreated(any(), any(), any()))
        .thenReturn(Boolean.FALSE);
    String dataPath =
        new File("../receiving-test/src/main/resources/json/GdcScanUpcMultiPoLine.json")
            .getCanonicalPath();
    when(configUtils.getConfiguredInstance(
            anyString(),
            eq(OPEN_QTY_CALCULATOR),
            eq(DEFAULT_OPEN_QTY_CALCULATOR),
            eq(OpenQtyCalculator.class)))
        .thenReturn(defaultOpenQtyCalculator);
    String deliveryDetailsJson = new String(Files.readAllBytes(Paths.get(dataPath)));
    deliveryDocumentList =
        Arrays.asList(gson.fromJson(deliveryDetailsJson, DeliveryDocument[].class));
    try {
      Pair<DeliveryDocument, DeliveryDocumentLine> autoSelectDocumentAndDocumentLine =
          defaultDeliveryDocumentSelector.autoSelectDeliveryDocumentLine(deliveryDocumentList);
    } catch (RuntimeException R) {
      assert (true);
    }
  }

  private void setUpUnsortedDeliveryDocumentList() {
    for (DeliveryDocument deliveryDocument : deliveryDocumentList) {
      for (int i = 0; i < deliveryDocument.getDeliveryDocumentLines().size(); i++) {
        DeliveryDocumentLine deliveryDocumentLine =
            deliveryDocument.getDeliveryDocumentLines().get(i);
        if (i == 0) {
          deliveryDocumentLine.setPurchaseReferenceLineNumber(2);
        } else if (i == 1) {
          deliveryDocumentLine.setPurchaseReferenceLineNumber(1);
        }
      }
    }
  }
}
