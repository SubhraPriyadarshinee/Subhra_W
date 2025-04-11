package com.walmart.move.nim.receiving.witron.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.COMPLETE_DELIVERY_UNCONFIRMED_PO_ERROR_CODE;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.COMPLETE_DELIVERY_UNCONFIRMED_PO_ERROR_HEADER;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ORG_UNIT_ID_HEADER;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.SUBCENTER_ID_HEADER;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.builder.ConfirmPoResponseBuilder;
import com.walmart.move.nim.receiving.core.client.dcfin.DCFinRestApiClient;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClient;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClientException;
import com.walmart.move.nim.receiving.core.common.DeliveryStatusPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.FinalizePORequestBody;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.repositories.ReceiptCustomRepository;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.service.OSDRCalculator;
import com.walmart.move.nim.receiving.core.service.OSDRRecordCountAggregator;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.OSDRCode;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.witron.common.GDCFlagReader;
import com.walmart.move.nim.receiving.witron.mock.data.MockContainer;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class GdcCompleteDeliveryProcessorTest extends ReceivingTestBase {
  @InjectMocks private OSDRCalculator osdrCalculator;
  @InjectMocks private ConfirmPoResponseBuilder confirmPoResponseBuilder;
  @InjectMocks private OSDRRecordCountAggregator osdrRecordCountAggregator;
  @InjectMocks private GdcCompleteDeliveryProcessor gdcCompleteDeliveryProcessor;
  @Mock private ReceiptService receiptService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private GDMRestApiClient gdmRestApiClient;
  @Mock private InstructionRepository instructionRepository;
  @Mock private DeliveryStatusPublisher deliveryStatusPublisher;
  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private GDCFlagReader gDCFlagReader;
  @Mock private DCFinRestApiClient dcFinRestApiClient;
  @Mock private ReceiptCustomRepository receiptCustomRepository;

  private Gson gson = new Gson();
  private final HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
  public static final long deliveryNumber = 12333333L;
  public static final String po1 = "0763140001";
  public static final String po2 = "0763140002";
  public static final String po3 = "0763140003";
  public static final String po4 = "0763140004";
  public static final String po5 = "0763140005";
  public static final String po6 = "0763140006";
  public static final String po7 = "0763140007";
  public static final String po8 = "0763140008";
  public static final String po9 = "0763140009";

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32612);
    TenantContext.setFacilityCountryCode("US");

    ReflectionTestUtils.setField(gdcCompleteDeliveryProcessor, "osdrCalculator", osdrCalculator);
    ReflectionTestUtils.setField(
        gdcCompleteDeliveryProcessor, "confirmPoResponseBuilder", confirmPoResponseBuilder);
    ReflectionTestUtils.setField(
        gdcCompleteDeliveryProcessor, "osdrRecordCountAggregator", osdrRecordCountAggregator);
    ReflectionTestUtils.setField(confirmPoResponseBuilder, "receiptService", receiptService);

    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
  }

  @BeforeMethod
  public void setUpTestDataBeforeEachTest() {}

  @AfterMethod
  public void tearDown() {
    reset(
        instructionRepository,
        receiptService,
        gdmRestApiClient,
        deliveryStatusPublisher,
        containerPersisterService,
        gDCFlagReader,
        dcFinRestApiClient,
        receiptCustomRepository);
  }

  @Test
  public void testCompleteDelivery() throws GDMRestApiClientException, ReceivingException {
    when(gDCFlagReader.isIncludePalletCount()).thenReturn(true);
    doReturn(0l)
        .when(instructionRepository)
        .countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(deliveryNumber);
    doReturn(getReceipts()).when(receiptService).findFinalizedReceiptsFor(anyLong());

    List<PurchaseOrderWithOSDRResponse> purchaseOrderWithOSDRResponses = new ArrayList<>();
    purchaseOrderWithOSDRResponses.add(createPo(po1));
    DeliveryWithOSDRResponse deliveryWithOSDRResponse = new DeliveryWithOSDRResponse();
    deliveryWithOSDRResponse.setPurchaseOrders(purchaseOrderWithOSDRResponses);
    deliveryWithOSDRResponse.setDeliveryNumber(deliveryNumber);

    doReturn(deliveryWithOSDRResponse).when(gdmRestApiClient).getDelivery(anyLong(), any());
    doReturn(getReceiptSummaryResponse(po1, 1, 10L, ReceivingConstants.Uom.VNPK))
        .when(receiptService)
        .getReceivedQtySummaryByPOForDelivery(deliveryNumber, ReceivingConstants.Uom.VNPK);
    doReturn(getReceiptSummaryResponse(po1, 1, 60L, ReceivingConstants.Uom.EACHES))
        .when(receiptService)
        .getReceivedQtySummaryByPOForDelivery(deliveryNumber, ReceivingConstants.Uom.EACHES);
    doReturn(new DeliveryInfo())
        .when(deliveryStatusPublisher)
        .publishDeliveryStatus(anyLong(), anyString(), anyList(), anyMap());
    doReturn(getContainers())
        .when(containerPersisterService)
        .findContainerByDeliveryNumber(anyLong());

    DeliveryInfo deliveryInfo =
        gdcCompleteDeliveryProcessor.completeDelivery(deliveryNumber, false, httpHeaders);

    assertEquals(deliveryInfo.getReceipts().get(0).getPurchaseReferenceNumber(), "0763140001");
    assertEquals(deliveryInfo.getReceipts().get(0).getPurchaseReferenceLineNumber().intValue(), 1);
    assertEquals(deliveryInfo.getReceipts().get(0).getReceivedQty().intValue(), 10);
    assertEquals(deliveryInfo.getReceipts().get(0).getQtyUOM(), "ZA");
    assertEquals(deliveryInfo.getNumberOfPallets().intValue(), 3);
  }

  @Test
  public void testValidateIfAllGdmPOsAreProcessed() throws GDMRestApiClientException {
    doReturn(getFinalizedReceipts()).when(receiptService).findFinalizedReceiptsFor(anyLong());
    doReturn(getDeliveryWithOSDRResponse()).when(gdmRestApiClient).getDelivery(anyLong(), any());

    try {
      gdcCompleteDeliveryProcessor.hasAllPOsFinalized(deliveryNumber, httpHeaders);
      fail();
    } catch (ReceivingException e) {
      assertEquals(
          e.getErrorResponse().getErrorCode(), COMPLETE_DELIVERY_UNCONFIRMED_PO_ERROR_CODE);
      assertEquals(
          e.getErrorResponse().getErrorMessage(),
          "There are 5 unconfirmed POs. Please confirm all POs to complete the delivery.");
      assertEquals(
          e.getErrorResponse().getErrorHeader(), COMPLETE_DELIVERY_UNCONFIRMED_PO_ERROR_HEADER);
      assertEquals(
          e.getErrorResponse().getErrorInfo(),
          COMPLETE_DELIVERY_UNCONFIRMED_PO_ERROR_CODE
              + " : [0763140009, 0763140008, 0763140007, 0763140006, 0763140005]");
    }
  }

  @Test
  public void testCompleteDeliveryWithZeroPallets()
      throws GDMRestApiClientException, ReceivingException {
    when(gDCFlagReader.isIncludePalletCount()).thenReturn(true);
    doReturn(0l)
        .when(instructionRepository)
        .countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(deliveryNumber);
    doReturn(new ArrayList<>()).when(receiptService).findFinalizedReceiptsFor(anyLong());
    doReturn(new DeliveryWithOSDRResponse()).when(gdmRestApiClient).getDelivery(anyLong(), any());
    doReturn(new ArrayList<>())
        .when(receiptService)
        .getReceivedQtySummaryByPOForDelivery(deliveryNumber, ReceivingConstants.Uom.VNPK);
    doReturn(new ArrayList<>())
        .when(receiptService)
        .getReceivedQtySummaryByPOForDelivery(deliveryNumber, ReceivingConstants.Uom.EACHES);
    doReturn(new DeliveryInfo())
        .when(deliveryStatusPublisher)
        .publishDeliveryStatus(anyLong(), anyString(), anyList(), anyMap());
    doReturn(new ArrayList<>())
        .when(containerPersisterService)
        .findContainerByDeliveryNumber(anyLong());

    DeliveryInfo deliveryInfo =
        gdcCompleteDeliveryProcessor.completeDelivery(deliveryNumber, false, httpHeaders);

    assertEquals(deliveryInfo.getNumberOfPallets().intValue(), 0);
    assertEquals(deliveryInfo.getReceipts().size(), 0);
  }

  @Test
  public void testCompleteDeliveryAndPO_withShortage()
      throws GDMRestApiClientException, IOException {
    when(tenantSpecificConfigReader.getSubcenterId()).thenReturn("1");
    doReturn(getMockDeliveryFromGDM()).when(gdmRestApiClient).getDelivery(anyLong(), any());
    doReturn(getMockReceiptWithRcvdQty(1000, 1))
        .when(receiptService)
        .findByDeliveryNumber(anyLong());
    doReturn(getMockReceiptWithRcvdQty(1000, 1))
        .when(receiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    when(receiptCustomRepository.receivedQtySummaryInEachesByDelivery(anyLong()))
        .thenReturn(getMockReceiptSummaryWithRcvdQty(Long.parseLong("30000")));
    when(deliveryStatusPublisher.publishDeliveryStatus(anyLong(), anyString(), any(), any()))
        .thenReturn(getMockDeliveryInfoWithRcvdQty(Long.parseLong("30000")));
    doReturn(Collections.emptyList()).when(receiptService).saveAll(any(List.class));
    ArgumentCaptor<Map<String, Object>> gdmPoFinalizeRequestHeaders =
        ArgumentCaptor.forClass(HashMap.class);
    ArgumentCaptor<FinalizePORequestBody> gdmPoFinalizeCaptor =
        ArgumentCaptor.forClass(FinalizePORequestBody.class);
    doNothing()
        .when(gdmRestApiClient)
        .finalizePurchaseOrder(
            anyLong(),
            anyString(),
            gdmPoFinalizeCaptor.capture(),
            gdmPoFinalizeRequestHeaders.capture());
    ArgumentCaptor<DCFinPOCloseRequestBody> dcfinPoCloseCaptor =
        ArgumentCaptor.forClass(DCFinPOCloseRequestBody.class);
    doNothing().when(dcFinRestApiClient).poCloseAsync(dcfinPoCloseCaptor.capture(), any());
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(ORG_UNIT_ID_HEADER, "1");
    DeliveryInfo response =
        gdcCompleteDeliveryProcessor.completeDeliveryAndPO(Long.parseLong("20634023"), httpHeaders);

    verify(gdmRestApiClient, times(1)).getDelivery(anyLong(), any());
    verify(receiptService, times(1)).findByDeliveryNumber(anyLong());
    verify(gdmRestApiClient, times(1)).finalizePurchaseOrder(anyLong(), anyString(), any(), any());
    verify(receiptService, times(1))
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    verify(receiptService, times(1)).saveAll(any());
    verify(dcFinRestApiClient, times(1)).poCloseAsync(any(), any());
    verify(receiptCustomRepository, times(1)).receivedQtySummaryInEachesByDelivery(anyLong());
    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(anyLong(), anyString(), any(), any());

    assertEquals(response.getReceipts().get(0).getPurchaseReferenceNumber(), "0776661555");
    assertEquals(response.getReceipts().get(0).getPurchaseReferenceLineNumber().intValue(), 1);
    assertEquals(response.getReceipts().get(0).getReceivedQty().intValue(), 30000);
    assertEquals(response.getReceipts().get(0).getQtyUOM(), "EA");

    // Validate GDM PO finalize request payload
    assertEquals(gdmPoFinalizeRequestHeaders.getValue().get("orgUnitId"), "1");
    assertEquals(
        gdmPoFinalizeCaptor.getValue().getReasonCode().toString(), "PURCHASE_ORDER_FINALIZE");
    assertEquals(gdmPoFinalizeCaptor.getValue().getReject().getQuantity().intValue(), 10);
    assertEquals(gdmPoFinalizeCaptor.getValue().getLines().get(0).getLineNumber(), 1);
    assertEquals(gdmPoFinalizeCaptor.getValue().getLines().get(0).getRcvdQty(), 1000);
    assertEquals(gdmPoFinalizeCaptor.getValue().getLines().get(0).getDamage().getCode(), "D10");
    assertEquals(
        gdmPoFinalizeCaptor.getValue().getLines().get(0).getDamage().getQuantity().intValue(), 10);
    assertNull(gdmPoFinalizeCaptor.getValue().getLines().get(0).getOverage());
    assertEquals(gdmPoFinalizeCaptor.getValue().getLines().get(0).getShortage().getCode(), "S10");
    assertEquals(
        gdmPoFinalizeCaptor.getValue().getLines().get(0).getShortage().getQuantity().intValue(),
        180);
    assertEquals(gdmPoFinalizeCaptor.getValue().getLines().get(0).getReject().getCode(), "R10");
    assertEquals(
        gdmPoFinalizeCaptor.getValue().getLines().get(0).getReject().getQuantity().intValue(), 10);

    // Validate DCFIN PO close request payload
    assertEquals(dcfinPoCloseCaptor.getValue().getTxnId(), "1a2bc3d4-0776661555");
    assertEquals(dcfinPoCloseCaptor.getValue().getDocument().getDeliveryNum(), "20634023");
    assertEquals(dcfinPoCloseCaptor.getValue().getDocument().getDocumentNum(), "0776661555");
    assertEquals(dcfinPoCloseCaptor.getValue().getDocument().getDocType(), "20");
    assertEquals(dcfinPoCloseCaptor.getValue().getDocument().getFreightBillQty().intValue(), 1200);
    assertEquals(
        dcfinPoCloseCaptor
            .getValue()
            .getDocument()
            .getDocumentLineItems()
            .get(0)
            .getDocumentLineNo()
            .intValue(),
        1);
    assertEquals(
        dcfinPoCloseCaptor
            .getValue()
            .getDocument()
            .getDocumentLineItems()
            .get(0)
            .getPrimaryQty()
            .intValue(),
        1000);
  }

  @Test
  public void testCompleteDeliveryAndPO_withOverage()
      throws GDMRestApiClientException, IOException {
    doReturn(getMockDeliveryFromGDM()).when(gdmRestApiClient).getDelivery(anyLong(), any());
    doReturn(getMockReceiptWithRcvdQty(1500, 1))
        .when(receiptService)
        .findByDeliveryNumber(anyLong());
    doReturn(getMockReceiptWithRcvdQty(1500, 1))
        .when(receiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    when(receiptCustomRepository.receivedQtySummaryInEachesByDelivery(anyLong()))
        .thenReturn(getMockReceiptSummaryWithRcvdQty(Long.parseLong("45000")));
    when(deliveryStatusPublisher.publishDeliveryStatus(anyLong(), anyString(), any(), any()))
        .thenReturn(getMockDeliveryInfoWithRcvdQty(Long.parseLong("45000")));
    doReturn(Collections.emptyList()).when(receiptService).saveAll(any(List.class));
    ArgumentCaptor<FinalizePORequestBody> gdmPoFinalizeCaptor =
        ArgumentCaptor.forClass(FinalizePORequestBody.class);
    doNothing()
        .when(gdmRestApiClient)
        .finalizePurchaseOrder(anyLong(), anyString(), gdmPoFinalizeCaptor.capture(), any());
    ArgumentCaptor<DCFinPOCloseRequestBody> dcfinPoCloseCaptor =
        ArgumentCaptor.forClass(DCFinPOCloseRequestBody.class);
    doNothing().when(dcFinRestApiClient).poCloseAsync(dcfinPoCloseCaptor.capture(), any());

    HttpHeaders headers2 = MockHttpHeaders.getHeaders();
    headers2.set(SUBCENTER_ID_HEADER, null);
    doReturn("2").when(tenantSpecificConfigReader).getSubcenterId();

    DeliveryInfo response =
        gdcCompleteDeliveryProcessor.completeDeliveryAndPO(Long.parseLong("20634023"), headers2);

    verify(gdmRestApiClient, times(1)).getDelivery(anyLong(), any());
    verify(receiptService, times(1)).findByDeliveryNumber(anyLong());
    verify(gdmRestApiClient, times(1)).finalizePurchaseOrder(anyLong(), anyString(), any(), any());
    verify(receiptService, times(1))
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    verify(receiptService, times(1)).saveAll(any());
    verify(dcFinRestApiClient, times(1)).poCloseAsync(any(), any());
    verify(receiptCustomRepository, times(1)).receivedQtySummaryInEachesByDelivery(anyLong());
    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(anyLong(), anyString(), any(), any());

    assertEquals(response.getReceipts().get(0).getPurchaseReferenceNumber(), "0776661555");
    assertEquals(response.getReceipts().get(0).getPurchaseReferenceLineNumber().intValue(), 1);
    assertEquals(response.getReceipts().get(0).getReceivedQty().intValue(), 45000);
    assertEquals(response.getReceipts().get(0).getQtyUOM(), "EA");

    // Validate GDM PO finalize request payload
    assertEquals(
        gdmPoFinalizeCaptor.getValue().getReasonCode().toString(), "PURCHASE_ORDER_FINALIZE");
    assertEquals(gdmPoFinalizeCaptor.getValue().getReject().getQuantity().intValue(), 10);
    assertEquals(gdmPoFinalizeCaptor.getValue().getLines().get(0).getLineNumber(), 1);
    assertEquals(gdmPoFinalizeCaptor.getValue().getLines().get(0).getRcvdQty(), 1500);
    assertEquals(gdmPoFinalizeCaptor.getValue().getLines().get(0).getDamage().getCode(), "D10");
    assertEquals(
        gdmPoFinalizeCaptor.getValue().getLines().get(0).getDamage().getQuantity().intValue(), 10);
    assertNull(gdmPoFinalizeCaptor.getValue().getLines().get(0).getShortage());
    assertEquals(gdmPoFinalizeCaptor.getValue().getLines().get(0).getOverage().getCode(), "O13");
    assertEquals(
        gdmPoFinalizeCaptor.getValue().getLines().get(0).getOverage().getQuantity().intValue(),
        320);
    assertEquals(gdmPoFinalizeCaptor.getValue().getLines().get(0).getReject().getCode(), "R10");
    assertEquals(
        gdmPoFinalizeCaptor.getValue().getLines().get(0).getReject().getQuantity().intValue(), 10);

    // Validate DCFIN PO close request payload
    assertEquals(dcfinPoCloseCaptor.getValue().getTxnId(), "1a2bc3d4-0776661555");
    assertEquals(dcfinPoCloseCaptor.getValue().getDocument().getDeliveryNum(), "20634023");
    assertEquals(dcfinPoCloseCaptor.getValue().getDocument().getDocumentNum(), "0776661555");
    assertEquals(dcfinPoCloseCaptor.getValue().getDocument().getDocType(), "20");
    assertEquals(dcfinPoCloseCaptor.getValue().getDocument().getFreightBillQty().intValue(), 1200);
    assertEquals(
        dcfinPoCloseCaptor
            .getValue()
            .getDocument()
            .getDocumentLineItems()
            .get(0)
            .getDocumentLineNo()
            .intValue(),
        1);
    assertEquals(
        dcfinPoCloseCaptor
            .getValue()
            .getDocument()
            .getDocumentLineItems()
            .get(0)
            .getPrimaryQty()
            .intValue(),
        1500);
  }

  @Test
  public void testCompleteDeliveryAndPO_subcenterId_From_gdmUi_Exist()
      throws GDMRestApiClientException, IOException {
    doReturn(getMockDeliveryFromGDM()).when(gdmRestApiClient).getDelivery(anyLong(), any());
    doReturn(getMockReceiptWithRcvdQty(1500, 1))
        .when(receiptService)
        .findByDeliveryNumber(anyLong());
    doReturn(getMockReceiptWithRcvdQty(1500, 1))
        .when(receiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    when(receiptCustomRepository.receivedQtySummaryInEachesByDelivery(anyLong()))
        .thenReturn(getMockReceiptSummaryWithRcvdQty(Long.parseLong("45000")));
    when(deliveryStatusPublisher.publishDeliveryStatus(anyLong(), anyString(), any(), any()))
        .thenReturn(getMockDeliveryInfoWithRcvdQty(Long.parseLong("45000")));
    doReturn(Collections.emptyList()).when(receiptService).saveAll(any(List.class));
    ArgumentCaptor<FinalizePORequestBody> gdmPoFinalizeCaptor =
        ArgumentCaptor.forClass(FinalizePORequestBody.class);
    doNothing()
        .when(gdmRestApiClient)
        .finalizePurchaseOrder(anyLong(), anyString(), gdmPoFinalizeCaptor.capture(), any());
    ArgumentCaptor<DCFinPOCloseRequestBody> dcfinPoCloseCaptor =
        ArgumentCaptor.forClass(DCFinPOCloseRequestBody.class);
    doNothing().when(dcFinRestApiClient).poCloseAsync(dcfinPoCloseCaptor.capture(), any());

    HttpHeaders headersWithsubcenterIdFromGdmUi = MockHttpHeaders.getHeaders();
    headersWithsubcenterIdFromGdmUi.set(SUBCENTER_ID_HEADER, "3");

    DeliveryInfo response =
        gdcCompleteDeliveryProcessor.completeDeliveryAndPO(
            Long.parseLong("20634023"), headersWithsubcenterIdFromGdmUi);

    verify(gdmRestApiClient, times(1)).getDelivery(anyLong(), any());
    verify(receiptService, times(1)).findByDeliveryNumber(anyLong());
    verify(gdmRestApiClient, times(1)).finalizePurchaseOrder(anyLong(), anyString(), any(), any());
    verify(receiptService, times(1))
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    verify(receiptService, times(1)).saveAll(any());
    verify(dcFinRestApiClient, times(1)).poCloseAsync(any(), any());
    verify(receiptCustomRepository, times(1)).receivedQtySummaryInEachesByDelivery(anyLong());
    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(anyLong(), anyString(), any(), any());

    assertEquals(response.getReceipts().get(0).getPurchaseReferenceNumber(), "0776661555");
    assertEquals(response.getReceipts().get(0).getPurchaseReferenceLineNumber().intValue(), 1);
    assertEquals(response.getReceipts().get(0).getReceivedQty().intValue(), 45000);
    assertEquals(response.getReceipts().get(0).getQtyUOM(), "EA");

    // Validate GDM PO finalize request payload
    assertEquals(
        gdmPoFinalizeCaptor.getValue().getReasonCode().toString(), "PURCHASE_ORDER_FINALIZE");
    assertEquals(gdmPoFinalizeCaptor.getValue().getReject().getQuantity().intValue(), 10);
    assertEquals(gdmPoFinalizeCaptor.getValue().getLines().get(0).getLineNumber(), 1);
    assertEquals(gdmPoFinalizeCaptor.getValue().getLines().get(0).getRcvdQty(), 1500);
    assertEquals(gdmPoFinalizeCaptor.getValue().getLines().get(0).getDamage().getCode(), "D10");
    assertEquals(
        gdmPoFinalizeCaptor.getValue().getLines().get(0).getDamage().getQuantity().intValue(), 10);
    assertNull(gdmPoFinalizeCaptor.getValue().getLines().get(0).getShortage());
    assertEquals(gdmPoFinalizeCaptor.getValue().getLines().get(0).getOverage().getCode(), "O13");
    assertEquals(
        gdmPoFinalizeCaptor.getValue().getLines().get(0).getOverage().getQuantity().intValue(),
        320);
    assertEquals(gdmPoFinalizeCaptor.getValue().getLines().get(0).getReject().getCode(), "R10");
    assertEquals(
        gdmPoFinalizeCaptor.getValue().getLines().get(0).getReject().getQuantity().intValue(), 10);

    // Validate DCFIN PO close request payload
    assertEquals(dcfinPoCloseCaptor.getValue().getTxnId(), "1a2bc3d4-0776661555");
    assertEquals(dcfinPoCloseCaptor.getValue().getDocument().getDeliveryNum(), "20634023");
    assertEquals(dcfinPoCloseCaptor.getValue().getDocument().getDocumentNum(), "0776661555");
    assertEquals(dcfinPoCloseCaptor.getValue().getDocument().getDocType(), "20");
    assertEquals(dcfinPoCloseCaptor.getValue().getDocument().getFreightBillQty().intValue(), 1200);
    assertEquals(
        dcfinPoCloseCaptor
            .getValue()
            .getDocument()
            .getDocumentLineItems()
            .get(0)
            .getDocumentLineNo()
            .intValue(),
        1);
    assertEquals(
        dcfinPoCloseCaptor
            .getValue()
            .getDocument()
            .getDocumentLineItems()
            .get(0)
            .getPrimaryQty()
            .intValue(),
        1500);
  }

  @Test
  public void testCompleteDeliveryAndPO_subcenterId_From_gdmUi_And_CCM_Missing()
      throws GDMRestApiClientException, IOException {
    doReturn(getMockDeliveryFromGDM()).when(gdmRestApiClient).getDelivery(anyLong(), any());
    doReturn(getMockReceiptWithRcvdQty(1500, 1))
        .when(receiptService)
        .findByDeliveryNumber(anyLong());
    doReturn(getMockReceiptWithRcvdQty(1500, 1))
        .when(receiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    when(receiptCustomRepository.receivedQtySummaryInEachesByDelivery(anyLong()))
        .thenReturn(getMockReceiptSummaryWithRcvdQty(Long.parseLong("45000")));
    when(deliveryStatusPublisher.publishDeliveryStatus(anyLong(), anyString(), any(), any()))
        .thenReturn(getMockDeliveryInfoWithRcvdQty(Long.parseLong("45000")));
    doReturn(Collections.emptyList()).when(receiptService).saveAll(any(List.class));
    ArgumentCaptor<FinalizePORequestBody> gdmPoFinalizeCaptor =
        ArgumentCaptor.forClass(FinalizePORequestBody.class);
    doNothing()
        .when(gdmRestApiClient)
        .finalizePurchaseOrder(anyLong(), anyString(), gdmPoFinalizeCaptor.capture(), any());
    ArgumentCaptor<DCFinPOCloseRequestBody> dcfinPoCloseCaptor =
        ArgumentCaptor.forClass(DCFinPOCloseRequestBody.class);
    doNothing().when(dcFinRestApiClient).poCloseAsync(dcfinPoCloseCaptor.capture(), any());

    HttpHeaders headersWithsubcenterIdFromGdmUi = MockHttpHeaders.getHeaders();
    headersWithsubcenterIdFromGdmUi.set(SUBCENTER_ID_HEADER, null);
    doReturn(null).when(tenantSpecificConfigReader).getSubcenterId();

    DeliveryInfo response =
        gdcCompleteDeliveryProcessor.completeDeliveryAndPO(
            Long.parseLong("20634023"), headersWithsubcenterIdFromGdmUi);

    verify(gdmRestApiClient, times(1)).getDelivery(anyLong(), any());
    verify(receiptService, times(1)).findByDeliveryNumber(anyLong());
    verify(gdmRestApiClient, times(1)).finalizePurchaseOrder(anyLong(), anyString(), any(), any());
    verify(receiptService, times(1))
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    verify(receiptService, times(1)).saveAll(any());
    verify(dcFinRestApiClient, times(1)).poCloseAsync(any(), any());
    verify(receiptCustomRepository, times(1)).receivedQtySummaryInEachesByDelivery(anyLong());
    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(anyLong(), anyString(), any(), any());

    assertEquals(response.getReceipts().get(0).getPurchaseReferenceNumber(), "0776661555");
    assertEquals(response.getReceipts().get(0).getPurchaseReferenceLineNumber().intValue(), 1);
    assertEquals(response.getReceipts().get(0).getReceivedQty().intValue(), 45000);
    assertEquals(response.getReceipts().get(0).getQtyUOM(), "EA");

    // Validate GDM PO finalize request payload
    assertEquals(
        gdmPoFinalizeCaptor.getValue().getReasonCode().toString(), "PURCHASE_ORDER_FINALIZE");
    assertEquals(gdmPoFinalizeCaptor.getValue().getReject().getQuantity().intValue(), 10);
    assertEquals(gdmPoFinalizeCaptor.getValue().getLines().get(0).getLineNumber(), 1);
    assertEquals(gdmPoFinalizeCaptor.getValue().getLines().get(0).getRcvdQty(), 1500);
    assertEquals(gdmPoFinalizeCaptor.getValue().getLines().get(0).getReject().getCode(), "R10");
    assertEquals(
        gdmPoFinalizeCaptor.getValue().getLines().get(0).getReject().getQuantity().intValue(), 10);
    assertNull(gdmPoFinalizeCaptor.getValue().getLines().get(0).getShortage());
    assertEquals(gdmPoFinalizeCaptor.getValue().getLines().get(0).getOverage().getCode(), "O13");
    assertEquals(
        gdmPoFinalizeCaptor.getValue().getLines().get(0).getOverage().getQuantity().intValue(),
        320);
    assertEquals(gdmPoFinalizeCaptor.getValue().getLines().get(0).getReject().getCode(), "R10");
    assertEquals(
        gdmPoFinalizeCaptor.getValue().getLines().get(0).getReject().getQuantity().intValue(), 10);

    // Validate DCFIN PO close request payload
    assertEquals(dcfinPoCloseCaptor.getValue().getTxnId(), "1a2bc3d4-0776661555");
    assertEquals(dcfinPoCloseCaptor.getValue().getDocument().getDeliveryNum(), "20634023");
    assertEquals(dcfinPoCloseCaptor.getValue().getDocument().getDocumentNum(), "0776661555");
    assertEquals(dcfinPoCloseCaptor.getValue().getDocument().getDocType(), "20");
    assertEquals(dcfinPoCloseCaptor.getValue().getDocument().getFreightBillQty().intValue(), 1200);
    assertEquals(
        dcfinPoCloseCaptor
            .getValue()
            .getDocument()
            .getDocumentLineItems()
            .get(0)
            .getDocumentLineNo()
            .intValue(),
        1);
    assertEquals(
        dcfinPoCloseCaptor
            .getValue()
            .getDocument()
            .getDocumentLineItems()
            .get(0)
            .getPrimaryQty()
            .intValue(),
        1500);
  }

  @Test
  public void testCompleteDeliveryAndPO_GDMRestApiClientException()
      throws GDMRestApiClientException, IOException {
    doReturn(getMockDeliveryFromGDM()).when(gdmRestApiClient).getDelivery(anyLong(), any());
    doReturn(getMockReceiptWithRcvdQty(1500, 1))
        .when(receiptService)
        .findByDeliveryNumber(anyLong());
    doReturn(getMockReceiptWithRcvdQty(1500, 1))
        .when(receiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    when(receiptCustomRepository.receivedQtySummaryInEachesByDelivery(anyLong()))
        .thenReturn(getMockReceiptSummaryWithRcvdQty(Long.parseLong("45000")));
    when(deliveryStatusPublisher.publishDeliveryStatus(anyLong(), anyString(), any(), any()))
        .thenReturn(getMockDeliveryInfoWithRcvdQty(Long.parseLong("45000")));
    doReturn(Collections.emptyList()).when(receiptService).saveAll(any(List.class));
    ArgumentCaptor<FinalizePORequestBody> gdmPoFinalizeCaptor =
        ArgumentCaptor.forClass(FinalizePORequestBody.class);
    ArgumentCaptor<DCFinPOCloseRequestBody> dcfinPoCloseCaptor =
        ArgumentCaptor.forClass(DCFinPOCloseRequestBody.class);
    doNothing().when(dcFinRestApiClient).poCloseAsync(dcfinPoCloseCaptor.capture(), any());

    HttpHeaders headers2 = MockHttpHeaders.getHeaders();
    headers2.set(SUBCENTER_ID_HEADER, null);
    doReturn("2").when(tenantSpecificConfigReader).getSubcenterId();

    final String gdmErrorCode = "GDM-TEST-gdmErrorResponse";
    final String gdmErrorMessage = "GDM-TEST-gdmErrorResponse";
    ErrorResponse gdmErrorResponse = new ErrorResponse(gdmErrorCode, gdmErrorMessage);
    final GDMRestApiClientException gdmRestApiClientException =
        new GDMRestApiClientException(gdmErrorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    final String errorCode = gdmRestApiClientException.getErrorResponse().getErrorCode();

    doThrow(gdmRestApiClientException)
        .when(gdmRestApiClient)
        .finalizePurchaseOrder(anyLong(), anyString(), gdmPoFinalizeCaptor.capture(), any());

    try {
      gdcCompleteDeliveryProcessor.completeDeliveryAndPO(Long.parseLong("20634023"), headers2);
      fail("completeDeliveryAndPO should fail with exception so it should not hit this line");
    } catch (ReceivingDataNotFoundException e) {
      assertEquals(e.getErrorCode(), "");
      assertEquals(
          e.getDescription(),
          "ErrorResponse(errorCode=GDM-TEST-gdmErrorResponse, errorMessage=GDM-TEST-gdmErrorResponse, errorHeader=null, values=null, errorKey=null, localisedErrorMessage=null, errorInfo=null)");

      verify(gdmRestApiClient, times(1)).getDelivery(anyLong(), any());
      verify(receiptService, times(1)).findByDeliveryNumber(anyLong());
      verify(gdmRestApiClient, times(1))
          .finalizePurchaseOrder(anyLong(), anyString(), any(), any());
      verify(receiptService, times(0))
          .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
      verify(receiptService, times(0)).saveAll(any());
      verify(dcFinRestApiClient, times(0)).poCloseAsync(any(), any());
      verify(receiptCustomRepository, times(0)).receivedQtySummaryInEachesByDelivery(anyLong());
      verify(deliveryStatusPublisher, times(0))
          .publishDeliveryStatus(anyLong(), anyString(), any(), any());
    }
  }

  @Test
  public void testCompleteDeliveryAndPO_Exception() throws GDMRestApiClientException, IOException {
    doReturn(getMockDeliveryFromGDM()).when(gdmRestApiClient).getDelivery(anyLong(), any());
    doReturn(getMockReceiptWithRcvdQty(1500, 1))
        .when(receiptService)
        .findByDeliveryNumber(anyLong());
    doReturn(getMockReceiptWithRcvdQty(1500, 1))
        .when(receiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    when(receiptCustomRepository.receivedQtySummaryInEachesByDelivery(anyLong()))
        .thenReturn(getMockReceiptSummaryWithRcvdQty(Long.parseLong("45000")));
    when(deliveryStatusPublisher.publishDeliveryStatus(anyLong(), anyString(), any(), any()))
        .thenReturn(getMockDeliveryInfoWithRcvdQty(Long.parseLong("45000")));
    doReturn(Collections.emptyList()).when(receiptService).saveAll(any(List.class));
    ArgumentCaptor<FinalizePORequestBody> gdmPoFinalizeCaptor =
        ArgumentCaptor.forClass(FinalizePORequestBody.class);
    ArgumentCaptor<DCFinPOCloseRequestBody> dcfinPoCloseCaptor =
        ArgumentCaptor.forClass(DCFinPOCloseRequestBody.class);
    doNothing().when(dcFinRestApiClient).poCloseAsync(dcfinPoCloseCaptor.capture(), any());

    HttpHeaders headers2 = MockHttpHeaders.getHeaders();
    headers2.set(SUBCENTER_ID_HEADER, null);
    doReturn("2").when(tenantSpecificConfigReader).getSubcenterId();

    // final Exception gdmGeneralException = new Exception("GdmGeneralException");
    final RuntimeException runtimeException = new RuntimeException("GdmGeneralException");
    doThrow(runtimeException)
        .when(gdmRestApiClient)
        .finalizePurchaseOrder(anyLong(), anyString(), gdmPoFinalizeCaptor.capture(), any());

    try {
      gdcCompleteDeliveryProcessor.completeDeliveryAndPO(Long.parseLong("20634023"), headers2);
      fail("completeDeliveryAndPO should fail with exception so it should not hit this line");
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), "GLS-RCV-INT-500");
      assertEquals(
          e.getDescription(),
          "Cannot complete delivery 20634023 due to error: GdmGeneralException");

      verify(gdmRestApiClient, times(1)).getDelivery(anyLong(), any());
      verify(receiptService, times(1)).findByDeliveryNumber(anyLong());
      verify(gdmRestApiClient, times(1))
          .finalizePurchaseOrder(anyLong(), anyString(), any(), any());
      verify(receiptService, times(0))
          .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
      verify(receiptService, times(0)).saveAll(any());
      verify(dcFinRestApiClient, times(0)).poCloseAsync(any(), any());
      verify(receiptCustomRepository, times(0)).receivedQtySummaryInEachesByDelivery(anyLong());
      verify(deliveryStatusPublisher, times(0))
          .publishDeliveryStatus(anyLong(), anyString(), any(), any());
    }
  }

  @Test
  public void testCompleteDeliveryAndPO_withoutOsdrMaster()
      throws GDMRestApiClientException, IOException {
    doReturn(getMockDeliveryFromGDM()).when(gdmRestApiClient).getDelivery(anyLong(), any());
    doReturn(Collections.emptyList()).when(receiptService).findByDeliveryNumber(anyLong());
    doReturn(Collections.emptyList())
        .when(receiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    when(receiptCustomRepository.receivedQtySummaryInEachesByDelivery(anyLong()))
        .thenReturn(getMockReceiptSummaryWithRcvdQty(Long.parseLong("0")));
    when(deliveryStatusPublisher.publishDeliveryStatus(anyLong(), anyString(), any(), any()))
        .thenReturn(getMockDeliveryInfoWithRcvdQty(Long.parseLong("0")));
    doReturn(new Receipt()).when(receiptService).saveAndFlushReceipt(any());
    doReturn(Collections.emptyList()).when(receiptService).saveAll(any(List.class));
    ArgumentCaptor<FinalizePORequestBody> gdmPoFinalizeCaptor =
        ArgumentCaptor.forClass(FinalizePORequestBody.class);
    doNothing()
        .when(gdmRestApiClient)
        .finalizePurchaseOrder(anyLong(), anyString(), gdmPoFinalizeCaptor.capture(), any());
    ArgumentCaptor<DCFinPOCloseRequestBody> dcfinPoCloseCaptor =
        ArgumentCaptor.forClass(DCFinPOCloseRequestBody.class);
    doNothing().when(dcFinRestApiClient).poCloseAsync(dcfinPoCloseCaptor.capture(), any());
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.add(ORG_UNIT_ID_HEADER, "2");
    DeliveryInfo response =
        gdcCompleteDeliveryProcessor.completeDeliveryAndPO(Long.parseLong("20634023"), httpHeaders);

    verify(gdmRestApiClient, times(1)).getDelivery(anyLong(), any());
    verify(receiptService, times(1)).findByDeliveryNumber(anyLong());
    verify(gdmRestApiClient, times(1)).finalizePurchaseOrder(anyLong(), anyString(), any(), any());
    verify(receiptService, times(1))
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(anyLong(), anyString());
    verify(receiptService, times(1)).saveAndFlushReceipt(any());
    verify(receiptService, times(1)).saveAll(any());
    verify(dcFinRestApiClient, times(1)).poCloseAsync(any(), any());
    verify(receiptCustomRepository, times(1)).receivedQtySummaryInEachesByDelivery(anyLong());
    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(anyLong(), anyString(), any(), any());

    assertEquals(response.getReceipts().get(0).getPurchaseReferenceNumber(), "0776661555");
    assertEquals(response.getReceipts().get(0).getPurchaseReferenceLineNumber().intValue(), 1);
    assertEquals(response.getReceipts().get(0).getReceivedQty().intValue(), 0);
    assertEquals(response.getReceipts().get(0).getQtyUOM(), "EA");

    // Validate GDM PO finalize request payload
    assertEquals(
        gdmPoFinalizeCaptor.getValue().getReasonCode().toString(), "PURCHASE_ORDER_FINALIZE");
    assertEquals(gdmPoFinalizeCaptor.getValue().getLines().get(0).getLineNumber(), 1);
    assertEquals(gdmPoFinalizeCaptor.getValue().getLines().get(0).getRcvdQty(), 0);
    assertNull(gdmPoFinalizeCaptor.getValue().getLines().get(0).getDamage());
    assertNull(gdmPoFinalizeCaptor.getValue().getLines().get(0).getOverage());
    assertNull(gdmPoFinalizeCaptor.getValue().getLines().get(0).getReject());
    assertEquals(gdmPoFinalizeCaptor.getValue().getLines().get(0).getShortage().getCode(), "S10");
    assertEquals(
        gdmPoFinalizeCaptor.getValue().getLines().get(0).getShortage().getQuantity().intValue(),
        1200);

    // Validate DCFIN PO close request payload
    assertEquals(dcfinPoCloseCaptor.getValue().getTxnId(), "1a2bc3d4-0776661555");
    assertEquals(dcfinPoCloseCaptor.getValue().getDocument().getDeliveryNum(), "20634023");
    assertEquals(dcfinPoCloseCaptor.getValue().getDocument().getDocumentNum(), "0776661555");
    assertEquals(dcfinPoCloseCaptor.getValue().getDocument().getDocType(), "20");
    assertEquals(dcfinPoCloseCaptor.getValue().getDocument().getFreightBillQty().intValue(), 1200);
    assertEquals(
        dcfinPoCloseCaptor
            .getValue()
            .getDocument()
            .getDocumentLineItems()
            .get(0)
            .getDocumentLineNo()
            .intValue(),
        1);
    assertEquals(
        dcfinPoCloseCaptor
            .getValue()
            .getDocument()
            .getDocumentLineItems()
            .get(0)
            .getPrimaryQty()
            .intValue(),
        0);
  }

  private DeliveryInfo getMockDeliveryInfoWithRcvdQty(Long qty) {
    DeliveryInfo deliveryInfo = new DeliveryInfo();
    deliveryInfo.setDeliveryNumber(deliveryNumber);
    deliveryInfo.setDeliveryStatus(DeliveryStatus.COMPLETE.name());
    deliveryInfo.setTs(new Date());
    deliveryInfo.setUserId("system");
    deliveryInfo.setReceipts(getMockReceiptSummaryWithRcvdQty(qty));

    return deliveryInfo;
  }

  private List<ReceiptSummaryResponse> getMockReceiptSummaryWithRcvdQty(Long qty) {
    List<ReceiptSummaryResponse> receiptSummaryList = new ArrayList<>();
    ReceiptSummaryResponse receiptSummary =
        new ReceiptSummaryEachesResponse("0776661555", 1, null, qty);
    receiptSummaryList.add(receiptSummary);

    return receiptSummaryList;
  }

  private List<Receipt> getMockReceiptWithRcvdQty(Integer qty, Integer osdrMaster) {
    Receipt receipt = new Receipt();
    receipt.setDeliveryNumber(Long.parseLong("20634023"));
    receipt.setPurchaseReferenceNumber("0776661555");
    receipt.setPurchaseReferenceLineNumber(1);
    receipt.setWhpkQty(30);
    receipt.setVnpkQty(30);
    receipt.setQuantity(qty);
    receipt.setQuantityUom(ReceivingConstants.Uom.VNPK);
    receipt.setFbRejectedQty(10);
    receipt.setFbRejectedQtyUOM(ReceivingConstants.Uom.VNPK);
    receipt.setFbRejectedReasonCode(OSDRCode.R10);
    receipt.setFbRejectionComment("test rejection");
    receipt.setFbDamagedQty(10);
    receipt.setFbDamagedQtyUOM(ReceivingConstants.Uom.VNPK);
    receipt.setFbDamagedReasonCode(OSDRCode.D10);
    receipt.setOsdrMaster(osdrMaster);
    List<Receipt> receipts = new ArrayList<>();
    receipts.add(receipt);

    return receipts;
  }

  private DeliveryWithOSDRResponse getMockDeliveryFromGDM() throws IOException {
    File resource = new ClassPathResource("gdm_delivery_response.json").getFile();
    String mockDelivery = new String(Files.readAllBytes(resource.toPath()));
    DeliveryWithOSDRResponse deliveryWithOSDRResponse =
        gson.fromJson(mockDelivery, DeliveryWithOSDRResponse.class);

    return deliveryWithOSDRResponse;
  }

  private DeliveryWithOSDRResponse getDeliveryWithOSDRResponse() {
    DeliveryWithOSDRResponse deliveryWithOSDRResponse = new DeliveryWithOSDRResponse();
    List<PurchaseOrderWithOSDRResponse> purchaseOrderWithOSDRResponses = new ArrayList<>();
    purchaseOrderWithOSDRResponses.add(createPo(po1));
    purchaseOrderWithOSDRResponses.add(createPo(po2));
    purchaseOrderWithOSDRResponses.add(createPo(po3));
    purchaseOrderWithOSDRResponses.add(createPo(po4));
    purchaseOrderWithOSDRResponses.add(createPo(po5));
    purchaseOrderWithOSDRResponses.add(createPo(po6));
    purchaseOrderWithOSDRResponses.add(createPo(po7));
    purchaseOrderWithOSDRResponses.add(createPo(po8));
    purchaseOrderWithOSDRResponses.add(createPo(po9));
    deliveryWithOSDRResponse.setPurchaseOrders(purchaseOrderWithOSDRResponses);

    return deliveryWithOSDRResponse;
  }

  private PurchaseOrderWithOSDRResponse createPo(String poNumber) {
    final PurchaseOrderWithOSDRResponse po1 = new PurchaseOrderWithOSDRResponse();
    po1.setPoNumber(poNumber);

    return po1;
  }

  private List<Receipt> getFinalizedReceipts() {
    Receipt receipt1 = new Receipt();
    receipt1.setDeliveryNumber(deliveryNumber);
    receipt1.setPurchaseReferenceNumber(po1);
    receipt1.setWhpkQty(1);
    receipt1.setVnpkQty(1);
    receipt1.setCreateTs(new Date());
    final String userId = "userId";
    receipt1.setCreateUserId(userId);
    receipt1.setQuantity(4);
    receipt1.setQuantityUom(ReceivingConstants.Uom.VNPK);
    receipt1.setFinalizeTs(new Date());
    receipt1.setFinalizedUserId(userId);

    Receipt receipt2 = new Receipt();
    receipt2.setDeliveryNumber(deliveryNumber);
    receipt2.setPurchaseReferenceNumber(po2);
    receipt2.setCreateTs(new Date());
    receipt2.setCreateUserId(userId);
    receipt2.setWhpkQty(1);
    receipt2.setVnpkQty(1);
    receipt2.setQuantity(4);
    receipt2.setQuantityUom(ReceivingConstants.Uom.VNPK);
    receipt2.setFinalizeTs(new Date());
    receipt2.setFinalizedUserId(userId);

    Receipt receipt3 = new Receipt();
    receipt3.setDeliveryNumber(deliveryNumber);
    receipt3.setPurchaseReferenceNumber(po3);
    receipt3.setCreateTs(new Date());
    receipt3.setCreateUserId(userId);
    receipt3.setWhpkQty(1);
    receipt3.setVnpkQty(1);
    receipt3.setQuantity(4);
    receipt3.setQuantityUom(ReceivingConstants.Uom.VNPK);
    receipt3.setFinalizeTs(new Date());
    receipt3.setFinalizedUserId(userId);

    Receipt receipt4 = new Receipt();
    receipt4.setDeliveryNumber(deliveryNumber);
    receipt4.setPurchaseReferenceNumber(po4);
    receipt4.setCreateTs(new Date());
    receipt4.setCreateUserId(userId);
    receipt4.setWhpkQty(1);
    receipt4.setVnpkQty(1);
    receipt4.setQuantity(4);
    receipt4.setQuantityUom(ReceivingConstants.Uom.VNPK);
    receipt4.setFinalizeTs(new Date());
    receipt4.setFinalizedUserId(userId);

    return Arrays.asList(receipt1, receipt2, receipt3, receipt4);
  }

  private List<ReceiptSummaryResponse> getReceiptSummaryResponse(
      String poNbr, Integer poLineNbr, Long qty, String uom) {
    ReceiptSummaryResponse receiptSummaryResponse =
        new ReceiptSummaryResponse(poNbr, poLineNbr, qty);
    receiptSummaryResponse.setQtyUOM(uom);

    List<ReceiptSummaryResponse> response = new ArrayList<>();
    response.add(receiptSummaryResponse);

    return response;
  }

  private List<Container> getContainers() {
    List<Container> containers = new ArrayList<>();
    Container container1 = MockContainer.getContainer();
    containers.add(container1);
    Container container2 = MockContainer.getContainer();
    container2.setContainerStatus("backout");
    containers.add(container2);
    Container container3 = MockContainer.getContainer();
    containers.add(container3);
    Container container4 = MockContainer.getContainer();
    container4.setContainerStatus("backout");
    containers.add(container4);
    Container container5 = MockContainer.getContainer();
    containers.add(container5);

    return containers;
  }

  private List<Receipt> getReceipts() {
    Receipt receipt = new Receipt();
    receipt.setDeliveryNumber(deliveryNumber);
    receipt.setPurchaseReferenceNumber(po1);
    receipt.setWhpkQty(6);
    receipt.setVnpkQty(6);
    receipt.setQuantity(10);
    receipt.setQuantityUom(ReceivingConstants.Uom.VNPK);
    receipt.setFinalizeTs(new Date());
    receipt.setFinalizedUserId("sysadmin");
    List<Receipt> receipts = new ArrayList<>();
    receipts.add(receipt);

    return receipts;
  }
}
