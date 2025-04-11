package com.walmart.move.nim.receiving.rx.service;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import com.walmart.move.nim.receiving.core.common.DeliveryStatusPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryResponse;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.data.MockRxHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RxCompleteDeliveryProcessorTest {

  @Mock private InstructionRepository instructionRepository;
  @Mock private ReceiptService receiptService;
  @Mock private DeliveryStatusPublisher deliveryStatusPublisher;

  @InjectMocks private RxCompleteDeliveryProcessor rxCompleteDeliveryProcessor;

  @BeforeMethod
  public void setUpBeforeClass() throws Exception {
    MockitoAnnotations.initMocks(this);

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32897);
  }

  @AfterMethod
  public void tearDown() throws Exception {
    reset(instructionRepository, receiptService, deliveryStatusPublisher);
  }

  @Test
  public void test_completeDelivery() throws ReceivingException {

    doReturn(0l)
        .when(instructionRepository)
        .countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(12345l);
    doReturn(getReceiptSummaryResponses(ReceivingConstants.Uom.EACHES))
        .when(receiptService)
        .getReceivedQtySummaryByPOForDelivery(12345l, ReceivingConstants.Uom.EACHES);
    doReturn(getDeliveryInfo())
        .when(deliveryStatusPublisher)
        .publishDeliveryStatus(anyLong(), anyString(), anyList(), anyMap());
    doReturn(getReceiptSummaryResponses(ReceivingConstants.Uom.VNPK))
        .when(receiptService)
        .getReceivedQtySummaryByPOForDelivery(12345l, ReceivingConstants.Uom.VNPK);

    DeliveryInfo completeDeliveryResponse =
        rxCompleteDeliveryProcessor.completeDelivery(12345l, false, MockRxHttpHeaders.getHeaders());

    assertNotNull(completeDeliveryResponse);
    assertEquals(
        completeDeliveryResponse.getReceipts().get(0).getQtyUOM(), ReceivingConstants.Uom.VNPK);

    verify(instructionRepository, times(1))
        .countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(12345l);
    verify(receiptService, times(1))
        .getReceivedQtySummaryByPOForDelivery(12345l, ReceivingConstants.Uom.EACHES);
    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(anyLong(), anyString(), anyList(), anyMap());
    verify(receiptService, times(1))
        .getReceivedQtySummaryByPOForDelivery(12345l, ReceivingConstants.Uom.EACHES);
  }

  @Test
  public void test_completeDelivery_pending_instructions() throws ReceivingException {

    doReturn(1l)
        .when(instructionRepository)
        .countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(12345l);

    try {
      DeliveryInfo completeDeliveryResponse =
          rxCompleteDeliveryProcessor.completeDelivery(
              12345l, false, MockRxHttpHeaders.getHeaders());
    } catch (ReceivingBadDataException rbde) {
      assertEquals(
          rbde.getErrorCode(), ExceptionCodes.COMPLETE_DELIVERY_OPEN_INSTRUCTION_ERROR_MESSAGE);
      assertEquals(
          rbde.getDescription(),
          ReceivingException.COMPLETE_DELIVERY_OPEN_INSTRUCTION_ERROR_MESSAGE);
    } catch (Exception e) {
      throw e;
    }

    verify(instructionRepository, times(1))
        .countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(12345l);
    verify(receiptService, times(0))
        .getReceivedQtySummaryByPOForDelivery(12345l, ReceivingConstants.Uom.EACHES);
    verify(deliveryStatusPublisher, times(0))
        .publishDeliveryStatus(anyLong(), anyString(), anyList(), anyMap());
    verify(receiptService, times(0))
        .getReceivedQtySummaryByPOForDelivery(12345l, ReceivingConstants.Uom.EACHES);
  }

  private List<ReceiptSummaryResponse> getReceiptSummaryResponses(String units) {
    List<ReceiptSummaryResponse> response = new ArrayList<>();

    ReceiptSummaryResponse receiptSummaryResponse = new ReceiptSummaryResponse("987654", 1, 10l);
    receiptSummaryResponse.setQtyUOM(units);

    response.add(receiptSummaryResponse);

    return response;
  }

  private DeliveryInfo getDeliveryInfo() {
    return new DeliveryInfo();
  }
}
