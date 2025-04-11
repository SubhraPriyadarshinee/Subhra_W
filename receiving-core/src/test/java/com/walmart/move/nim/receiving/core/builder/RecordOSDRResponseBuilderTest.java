package com.walmart.move.nim.receiving.core.builder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.reset;
import static org.testng.Assert.*;
import static org.testng.Assert.assertEquals;

import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClient;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClientException;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.FinalizePORequestBody;
import com.walmart.move.nim.receiving.core.repositories.RejectionsRepository;
import com.walmart.move.nim.receiving.core.service.OSDRCalculator;
import com.walmart.move.nim.receiving.core.service.OSDRRecordCountAggregator;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.OSDRCode;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RecordOSDRResponseBuilderTest {
  @InjectMocks RecordOSDRResponseBuilder recordOSDRResponseBuilder;
  @Mock OSDRRecordCountAggregator mockOsdrRecordCountAggregator;
  @Mock OSDRCalculator mockOsdrCalculator;
  @Mock ReceiptService mockReceiptService;
  @Mock private POHashKeyBuilder poHashKeyBuilder;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private GDMRestApiClient gdmRestApiClient;
  @Mock private FinalizePORequestBodyBuilder finalizePORequestBodyBuilder;
  @Mock private RejectionsRepository rejectionsRepository;

  @BeforeClass
  public void setup() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32612);
    TenantContext.setCorrelationId("a1-b2-c3-d4");
  }

  @AfterMethod()
  public void resetMocks() {
    reset(configUtils);
    reset(mockOsdrCalculator);
    reset(mockReceiptService);
    reset(gdmRestApiClient);
    reset(finalizePORequestBodyBuilder);
    reset(mockOsdrRecordCountAggregator);
    reset(rejectionsRepository);
  }

  @Test
  public void testBuild_osdrAfterPoClose() throws ReceivingException, GDMRestApiClientException {
    doReturn(getPopoLineKeyReceivingCountSummaryMap())
        .when(mockOsdrRecordCountAggregator)
        .getReceivingCountSummary(anyLong(), any(Map.class));
    doNothing().when(mockOsdrCalculator).calculate(any(ReceivingCountSummary.class));

    Receipt masterReceipt = getReceipt();
    masterReceipt.setFinalizeTs(new Date());
    masterReceipt.setFinalizedUserId("sysadmin");
    doReturn(masterReceipt)
        .when(mockReceiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyLong(), anyString(), any(Integer.class));
    doReturn(masterReceipt).when(mockReceiptService).saveAndFlushReceipt(any(Receipt.class));
    doReturn(new FinalizePORequestBody())
        .when(finalizePORequestBodyBuilder)
        .buildFrom(anyLong(), anyString(), any(Map.class));
    doNothing().when(gdmRestApiClient).persistFinalizePoOsdrToGdm(any(), any(), any(), any());

    RecordOSDRResponse recordOSDRResponse =
        recordOSDRResponseBuilder.build(
            12345l, "123456", 1, getRecordOSDRRequest(), MockHttpHeaders.getHeaders());

    verify(gdmRestApiClient, times(1))
        .persistFinalizePoOsdrToGdm(anyLong(), anyString(), any(), any());
    assertEquals(recordOSDRResponse.getShortageQty().intValue(), 794);
    assertEquals(recordOSDRResponse.getOverageQty().intValue(), 0);
    assertEquals(recordOSDRResponse.getDamageQty().intValue(), 5);
    assertEquals(recordOSDRResponse.getRejectedQty().intValue(), 5);
    assertEquals(recordOSDRResponse.getProblemQty().intValue(), 0);
  }

  @Test
  public void testBuild_exception() throws ReceivingException {

    Map<POPOLineKey, ReceivingCountSummary> mockReceivingCountSummaryMap =
        getPopoLineKeyReceivingCountSummaryMap();

    Map<String, Object> mockHeaders =
        ReceivingUtils.getForwardablHeader(ReceivingUtils.getHeaders());

    Receipt receipt = getReceipt();

    doReturn(mockReceivingCountSummaryMap)
        .when(mockOsdrRecordCountAggregator)
        .getReceivingCountSummary(anyLong(), any(Map.class));

    doNothing().when(mockOsdrCalculator).calculate(any(ReceivingCountSummary.class));

    doReturn(receipt)
        .when(mockReceiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyLong(), anyString(), any(Integer.class));

    doReturn(36l)
        .when(mockReceiptService)
        .receivedQtyByDeliveryPoAndPoLine(anyLong(), anyString(), any(Integer.class));

    Receipt savedReceipt = new Receipt();
    savedReceipt.setVersion(2);
    doReturn(savedReceipt).when(mockReceiptService).saveAndFlushReceipt(any(Receipt.class));

    RecordOSDRRequest recordOSDRRequest = getRecordOSDRRequest();

    Map<String, Object> headers = new HashMap<>();

    RecordOSDRResponse recordOSDRResponse =
        recordOSDRResponseBuilder.build(
            12345l, "123456", 1, recordOSDRRequest, MockHttpHeaders.getHeaders());

    assertEquals(recordOSDRResponse.getShortageQty().intValue(), 794);
    assertEquals(recordOSDRResponse.getOverageQty().intValue(), 0);
    assertEquals(recordOSDRResponse.getDamageQty().intValue(), 5);
    assertEquals(recordOSDRResponse.getRejectedQty().intValue(), 5);
    assertEquals(recordOSDRResponse.getProblemQty().intValue(), 0);
  }

  @Test
  public void testBuild_kotlinDisabled_exception_1_Insufficient_Reject_details()
      throws ReceivingException {
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());

    try {
      RecordOSDRRequest recordOSDRRequest = getRecordOSDRRequest();
      recordOSDRRequest.setRejectedUOM(null);
      Map<String, Object> headers = new HashMap<>();

      RecordOSDRResponse recordOSDRResponse =
          recordOSDRResponseBuilder.build(
              12345l, "123456", 1, recordOSDRRequest, MockHttpHeaders.getHeaders());
      fail("expected exception instead coming to this control falow");
    } catch (Exception e) {
      assertEquals(e.getMessage(), "Insufficient Reject details");
    }
  }

  @Test
  public void testBuild_versionMissMatch() throws ReceivingException {

    try {
      Map<POPOLineKey, ReceivingCountSummary> mockReceivingCountSummaryMap =
          getPopoLineKeyReceivingCountSummaryMap();

      Receipt receipt = getReceipt();
      receipt.setVersion(3); // set wrong version

      doReturn(mockReceivingCountSummaryMap)
          .when(mockOsdrRecordCountAggregator)
          .getReceivingCountSummary(anyLong(), any(Map.class));

      doNothing().when(mockOsdrCalculator).calculate(any(ReceivingCountSummary.class));

      doReturn(receipt)
          .when(mockReceiptService)
          .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
              anyLong(), anyString(), any(Integer.class));

      doReturn(36l)
          .when(mockReceiptService)
          .receivedQtyByDeliveryPoAndPoLine(anyLong(), anyString(), any(Integer.class));

      Receipt savedReceipt = new Receipt();
      savedReceipt.setVersion(2);
      doReturn(savedReceipt).when(mockReceiptService).saveAndFlushReceipt(any(Receipt.class));
      RecordOSDRRequest recordOSDRRequest = getRecordOSDRRequest();

      recordOSDRResponseBuilder.build(
          12345l, "123456", 1, recordOSDRRequest, MockHttpHeaders.getHeaders());

      fail("should fail above");

    } catch (ReceivingException e) {
      assertEquals(
          e.getMessage(),
          "Version mismatch for this Purchase Reference Number and Purchase Reference Line Number");
    }
  }

  private Receipt getReceipt() {
    Receipt receipt = new Receipt();
    receipt.setPurchaseReferenceNumber("123456");
    receipt.setPurchaseReferenceLineNumber(1);
    receipt.setVnpkQty(6);
    receipt.setFbOverQty(0);
    receipt.setFbShortQty(794);
    receipt.setFbDamagedQty(5);
    receipt.setFbRejectedQty(0);
    receipt.setFbProblemQty(0);
    receipt.setVersion(1);
    return receipt;
  }

  private RecordOSDRRequest getRecordOSDRRequest() {
    RecordOSDRRequest recordOSDRRequest = new RecordOSDRRequest();
    recordOSDRRequest.setRejectedQty(5);
    recordOSDRRequest.setRejectedReasonCode(OSDRCode.R10.name());
    recordOSDRRequest.setRejectedUOM("ZA");
    recordOSDRRequest.setRejectionComment("Mock Rejection Comments");
    recordOSDRRequest.setVersion(1);
    return recordOSDRRequest;
  }

  private Map<POPOLineKey, ReceivingCountSummary> getPopoLineKeyReceivingCountSummaryMap() {
    ReceivingCountSummary receivingCountSummary = getReceivingCountSummary();

    return getPopoLineKeyReceivingCountSummaryMap(receivingCountSummary);
  }

  private Map<POPOLineKey, ReceivingCountSummary> getPopoLineKeyReceivingCountSummaryMap(
      ReceivingCountSummary receivingCountSummary) {
    Map<POPOLineKey, ReceivingCountSummary> mockReceivingCountSummaryMap = new HashMap<>();
    mockReceivingCountSummaryMap.put(new POPOLineKey("123456", 1), receivingCountSummary);
    return mockReceivingCountSummaryMap;
  }

  private ReceivingCountSummary getReceivingCountSummary() {
    ReceivingCountSummary receivingCountSummary = new ReceivingCountSummary();
    receivingCountSummary.setOverageQty(0);
    receivingCountSummary.setShortageQty(794);
    receivingCountSummary.setDamageQty(5);
    receivingCountSummary.setReceiveQty(5);
    return receivingCountSummary;
  }

  @Test
  public void testHasValidRejectionDetails_false_emptyStringAsRejectUOM()
      throws ReceivingException {
    RecordOSDRResponseBuilder recordOSDRResponseBuilder = new RecordOSDRResponseBuilder();
    RecordOSDRRequest recordOSDRRequest = getOsdrRequest();
    recordOSDRRequest.setRejectedUOM("");
    try {
      recordOSDRResponseBuilder.hasValidRejectionDetails(recordOSDRRequest, false);
      fail("should not reach this line and expect to be in exception block");
    } catch (ReceivingException e) {
      assertEquals(e.getMessage(), "Insufficient Reject details");
      assertEquals(e.getHttpStatus().value(), 400);
      assertEquals(e.getErrorResponse().getErrorCode(), "recordOSDR");
    }
  }

  @Test
  public void testHasValidRejectionDetails_false() throws ReceivingException {
    RecordOSDRResponseBuilder recordOSDRResponseBuilder = new RecordOSDRResponseBuilder();
    RecordOSDRRequest recordOSDRRequest = getOsdrRequest();
    try {
      recordOSDRResponseBuilder.hasValidRejectionDetails(recordOSDRRequest, false);
      fail("should not reach this line and expect to be in exception block");
    } catch (ReceivingException e) {
      assertEquals(e.getMessage(), "Insufficient Reject details");
      assertEquals(e.getHttpStatus().value(), 400);
      assertEquals(e.getErrorResponse().getErrorCode(), "recordOSDR");
    }
  }

  @Test
  public void testHasValidRejectionDetails_true() throws ReceivingException {
    boolean isKotlin = true;
    RecordOSDRResponseBuilder recordOSDRResponseBuilder = new RecordOSDRResponseBuilder();
    RecordOSDRRequest recordOSDRRequest = getOsdrRequest();

    final boolean hasValidRejectionDetails2 =
        recordOSDRResponseBuilder.hasValidRejectionDetails(recordOSDRRequest, isKotlin);
    assertTrue(hasValidRejectionDetails2);
    assertEquals(recordOSDRRequest.getRejectedUOM(), "ZA");
  }

  private RecordOSDRRequest getOsdrRequest() {
    RecordOSDRRequest recordOSDRRequest = new RecordOSDRRequest();
    recordOSDRRequest.setRejectedQty(1);
    recordOSDRRequest.setRejectedUOM(null);
    recordOSDRRequest.setRejectionComment("test");
    recordOSDRRequest.setRejectedReasonCode("R11");
    return recordOSDRRequest;
  }

  @Test
  public void testBuild_rejection_flow_success()
      throws ReceivingException, GDMRestApiClientException {
    doReturn(getPopoLineKeyReceivingCountSummaryMap())
        .when(mockOsdrRecordCountAggregator)
        .getReceivingCountSummary(anyLong(), any(Map.class));
    doNothing().when(mockOsdrCalculator).calculate(any(ReceivingCountSummary.class));

    Receipt masterReceipt = getReceipt();
    masterReceipt.setFinalizeTs(new Date());
    masterReceipt.setFinalizedUserId("sysadmin");
    doReturn(masterReceipt)
        .when(mockReceiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyLong(), anyString(), any(Integer.class));
    doReturn(masterReceipt).when(mockReceiptService).saveAndFlushReceipt(any(Receipt.class));
    doReturn(new FinalizePORequestBody())
        .when(finalizePORequestBodyBuilder)
        .buildFrom(anyLong(), anyString(), any(Map.class));
    doNothing().when(gdmRestApiClient).persistFinalizePoOsdrToGdm(any(), any(), any(), any());

    RecordOSDRResponse recordOSDRResponse =
        recordOSDRResponseBuilder.build(
            12345l, "123456", 1, getRecordOSDRRequestRejection(), MockHttpHeaders.getHeaders());

    verify(gdmRestApiClient, times(1))
        .persistFinalizePoOsdrToGdm(anyLong(), anyString(), any(), any());
    verify(rejectionsRepository, times(1)).saveAndFlush(any());
    verify(gdmRestApiClient, times(1)).receivingToGDMEvent(any(), anyMap());
    assertEquals(recordOSDRResponse.getShortageQty().intValue(), 794);
    assertEquals(recordOSDRResponse.getOverageQty().intValue(), 0);
    assertEquals(recordOSDRResponse.getDamageQty().intValue(), 5);
    assertEquals(recordOSDRResponse.getRejectedQty().intValue(), 5);
    assertEquals(recordOSDRResponse.getProblemQty().intValue(), 0);
  }

  @Test
  public void testBuild_rejection_flow_gdmcallfails()
      throws ReceivingException, GDMRestApiClientException {
    doReturn(getPopoLineKeyReceivingCountSummaryMap())
        .when(mockOsdrRecordCountAggregator)
        .getReceivingCountSummary(anyLong(), any(Map.class));
    doNothing().when(mockOsdrCalculator).calculate(any(ReceivingCountSummary.class));

    Receipt masterReceipt = getReceipt();
    masterReceipt.setFinalizeTs(new Date());
    masterReceipt.setFinalizedUserId("sysadmin");
    doReturn(masterReceipt)
        .when(mockReceiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyLong(), anyString(), any(Integer.class));
    doReturn(masterReceipt).when(mockReceiptService).saveAndFlushReceipt(any(Receipt.class));
    doReturn(new FinalizePORequestBody())
        .when(finalizePORequestBodyBuilder)
        .buildFrom(anyLong(), anyString(), any(Map.class));
    doNothing().when(gdmRestApiClient).persistFinalizePoOsdrToGdm(any(), any(), any(), any());
    doThrow(GDMRestApiClientException.class)
        .when(gdmRestApiClient)
        .receivingToGDMEvent(any(), anyMap());

    RecordOSDRResponse recordOSDRResponse =
        recordOSDRResponseBuilder.build(
            12345l, "123456", 1, getRecordOSDRRequestRejection(), MockHttpHeaders.getHeaders());

    verify(gdmRestApiClient, times(1))
        .persistFinalizePoOsdrToGdm(anyLong(), anyString(), any(), any());
    verify(rejectionsRepository, times(1)).saveAndFlush(any());
    verify(gdmRestApiClient, times(1)).receivingToGDMEvent(any(), anyMap());
    assertEquals(recordOSDRResponse.getShortageQty().intValue(), 794);
    assertEquals(recordOSDRResponse.getOverageQty().intValue(), 0);
    assertEquals(recordOSDRResponse.getDamageQty().intValue(), 5);
    assertEquals(recordOSDRResponse.getRejectedQty().intValue(), 5);
    assertEquals(recordOSDRResponse.getProblemQty().intValue(), 0);
  }

  private RecordOSDRRequest getRecordOSDRRequestRejection() {
    RecordOSDRRequest recordOSDRRequest = new RecordOSDRRequest();
    recordOSDRRequest.setRejectedQty(5);
    recordOSDRRequest.setRejectedReasonCode(OSDRCode.R10.name());
    recordOSDRRequest.setRejectedUOM("ZA");
    recordOSDRRequest.setRejectionComment("Mock Rejection Comments");
    recordOSDRRequest.setRejectDisposition("Test Rejection");
    recordOSDRRequest.setItemNumber("1223848");
    recordOSDRRequest.setVersion(1);
    return recordOSDRRequest;
  }
}
