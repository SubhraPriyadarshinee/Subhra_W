package com.walmart.move.nim.receiving.acc.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_KOTLIN_CLIENT;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import com.walmart.move.nim.receiving.core.common.DeliveryStatusPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryResponse;
import com.walmart.move.nim.receiving.core.model.yms.v2.DefaultYms2UnloadEventProcessor;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.repositories.ReceiptRepository;
import com.walmart.move.nim.receiving.core.service.DockTagService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.data.MockRxHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ACCCompleteDeliveryProcessorTest {

  @Mock private InstructionRepository instructionRepository;
  @Mock private ReceiptRepository receiptRepository;
  @Mock private ReceiptService receiptService;
  @Mock private DeliveryStatusPublisher deliveryStatusPublisher;
  @Mock private ACCDockTagService accDockTagService;
  @Mock private DefaultYms2UnloadEventProcessor yms2UnloadEventProcessorService;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @InjectMocks private ACCCompleteDeliveryProcessor accCompleteDeliveryProcessor;

  @BeforeClass
  public void setUpBeforeClass() throws Exception {
    MockitoAnnotations.initMocks(this);

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32898);
    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(), eq(ReceivingConstants.DOCK_TAG_SERVICE), eq(DockTagService.class)))
        .thenReturn(accDockTagService);
  }

  @BeforeMethod
  public void resetMocks() throws Exception {
    reset(
        instructionRepository,
        receiptRepository,
        receiptService,
        deliveryStatusPublisher,
        accDockTagService,
        yms2UnloadEventProcessorService);
  }

  @Test
  public void test_completeDelivery() throws ReceivingException {

    doReturn(0L)
        .when(instructionRepository)
        .countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(12345L);
    doReturn(getReceiptSummaryResponses(ReceivingConstants.Uom.EACHES))
        .when(receiptService)
        .getReceivedQtySummaryByPOForDelivery(12345L, ReceivingConstants.Uom.EACHES);
    doReturn(getDeliveryInfo())
        .when(deliveryStatusPublisher)
        .publishDeliveryStatus(anyLong(), anyString(), anyList(), anyInt(), anyMap());
    doReturn(getReceiptSummaryResponses(ReceivingConstants.Uom.VNPK))
        .when(receiptService)
        .getReceivedQtySummaryByPOForDelivery(12345L, ReceivingConstants.Uom.VNPK);

    setTenantContextForHeader();
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(IS_KOTLIN_CLIENT, "false");

    DeliveryInfo completeDeliveryResponse =
        accCompleteDeliveryProcessor.completeDelivery(12345L, false, httpHeaders);

    assertNotNull(completeDeliveryResponse);

    verify(instructionRepository, times(1))
        .countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(12345L);
    verify(receiptService, times(1))
        .getReceivedQtySummaryByPOForDelivery(12345L, ReceivingConstants.Uom.EACHES);
    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(anyLong(), anyString(), anyList(), anyInt(), anyMap());
    verify(receiptService, times(1))
        .getReceivedQtySummaryByPOForDelivery(12345L, ReceivingConstants.Uom.EACHES);
  }

  @Test
  public void test_completeDeliveryCase() throws ReceivingException {

    doReturn(0L)
        .when(instructionRepository)
        .countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(12345L);
    doReturn(getReceiptSummaryResponses(ReceivingConstants.Uom.EACHES))
        .when(receiptService)
        .getReceivedQtySummaryByPOForDelivery(12345L, ReceivingConstants.Uom.EACHES);
    doReturn(getDeliveryInfo())
        .when(deliveryStatusPublisher)
        .publishDeliveryStatus(anyLong(), anyString(), anyList(), anyInt(), anyMap());
    doReturn(getReceiptSummaryResponses(ReceivingConstants.Uom.VNPK))
        .when(receiptService)
        .getReceivedQtySummaryByPOForDelivery(12345L, ReceivingConstants.Uom.VNPK);

    setTenantContextForHeader();
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(IS_KOTLIN_CLIENT, "true");

    DeliveryInfo completeDeliveryResponse =
        accCompleteDeliveryProcessor.completeDelivery(12345L, false, httpHeaders);

    assertNotNull(completeDeliveryResponse);

    verify(instructionRepository, times(1))
        .countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(12345L);
    verify(receiptService, times(1))
        .getReceivedQtySummaryByPOForDelivery(12345L, ReceivingConstants.Uom.EACHES);
    verify(receiptService, times(1))
        .getReceivedQtySummaryByPOForDelivery(12345L, ReceivingConstants.Uom.VNPK);
    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(anyLong(), anyString(), anyList(), anyInt(), anyMap());
    verify(receiptService, times(1))
        .getReceivedQtySummaryByPOForDelivery(12345L, ReceivingConstants.Uom.EACHES);
    verify(receiptService, times(1))
        .getReceivedQtySummaryByPOForDelivery(12345L, ReceivingConstants.Uom.VNPK);
  }

  @Test
  public void test_completeDelivery_pending_instructions() throws ReceivingException {

    doReturn(1L)
        .when(instructionRepository)
        .countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(12345L);

    try {
      DeliveryInfo completeDeliveryResponse =
          accCompleteDeliveryProcessor.completeDelivery(
              12345L, false, MockRxHttpHeaders.getHeaders());
    } catch (ReceivingException rbde) {
      assertEquals(rbde.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
      assertEquals(
          rbde.getErrorResponse().getErrorMessage(),
          ReceivingException.COMPLETE_DELIVERY_OPEN_INSTRUCTION_ERROR_MESSAGE);
      assertEquals(
          rbde.getErrorResponse().getErrorCode(),
          ReceivingException.COMPLETE_DELIVERY_OPEN_INSTRUCTION_ERROR_CODE);
    } catch (Exception e) {
      throw e;
    }

    verify(instructionRepository, times(1))
        .countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(12345L);
    verify(receiptService, times(0))
        .getReceivedQtySummaryByPOForDelivery(12345L, ReceivingConstants.Uom.EACHES);
    verify(deliveryStatusPublisher, times(0))
        .publishDeliveryStatus(anyLong(), anyString(), anyList(), anyMap());
    verify(receiptService, times(0))
        .getReceivedQtySummaryByPOForDelivery(12345L, ReceivingConstants.Uom.EACHES);
  }

  @Test
  public void test_completeDelivery_unload_progress() throws ReceivingException {
    doReturn(0L)
        .when(instructionRepository)
        .countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(12345L);
    doReturn(getReceiptSummaryResponses(ReceivingConstants.Uom.EACHES))
        .when(receiptService)
        .getReceivedQtySummaryByPOForDelivery(12345L, ReceivingConstants.Uom.EACHES);
    doReturn(getDeliveryInfo())
        .when(deliveryStatusPublisher)
        .publishDeliveryStatus(anyLong(), anyString(), anyList(), anyInt(), anyMap());
    doReturn(getReceiptSummaryResponses(ReceivingConstants.Uom.VNPK))
        .when(receiptService)
        .getReceivedQtySummaryByPOForDelivery(12345L, ReceivingConstants.Uom.VNPK);
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ReceivingConstants.ENABLE_PUBLISH_UNLOAD_PROGRESS_AT_DELIVERY_COMPLETE)))
        .thenReturn(true);

    when(tenantSpecificConfigReader.getConfiguredInstance(
            anyString(),
            eq(ReceivingConstants.DEFAULT_YMS2_UNLOAD_EVENT_PROCESSOR),
            eq(DefaultYms2UnloadEventProcessor.class)))
        .thenReturn(yms2UnloadEventProcessorService);
    setTenantContextForHeader();
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(IS_KOTLIN_CLIENT, "true");

    DeliveryInfo completeDeliveryResponse =
        accCompleteDeliveryProcessor.completeDelivery(12345L, false, httpHeaders);

    assertNotNull(completeDeliveryResponse);

    verify(yms2UnloadEventProcessorService, times(1)).processYMSUnloadingEvent(12345L);
  }

  private List<ReceiptSummaryResponse> getReceiptSummaryResponses(String units) {
    List<ReceiptSummaryResponse> response = new ArrayList<>();

    ReceiptSummaryResponse receiptSummaryResponse = new ReceiptSummaryResponse("987654", 1, 10L);
    receiptSummaryResponse.setQtyUOM(units);

    response.add(receiptSummaryResponse);

    return response;
  }

  private DeliveryInfo getDeliveryInfo() {
    return new DeliveryInfo();
  }

  private void setTenantContextForHeader() {
    TenantContext.clear();
    TenantContext.setFacilityNum(32987);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setCorrelationId(UUID.randomUUID().toString());
  }
}
