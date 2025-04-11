package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.testng.Assert.*;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.fail;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.GdmError;
import com.walmart.move.nim.receiving.core.common.exception.GdmErrorCode;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.mock.data.MockInstruction;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.ErrorResponse;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RetryableGdmV3DeliveryDocumentsSearchHandlerTest extends ReceivingTestBase {
  @InjectMocks private RetryableGdmV3DeliveryDocumentsSearchHandler deliveryDocumentsSearchHandler;
  @Mock private DeliveryServiceV3Impl deliveryService;
  private Gson gson = new Gson();

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(deliveryDocumentsSearchHandler, "gson", gson);
  }

  @Test
  public void testFetchDeliveryDocument() throws ReceivingException {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    List<DeliveryDocument> deliveryDocumentsFromGdm = MockInstruction.getDeliveryDocuments();
    doReturn(gson.toJson(deliveryDocumentsFromGdm))
        .when(deliveryService)
        .findDeliveryDocument(anyLong(), anyString(), any());
    List<DeliveryDocument> deliveryDocuments =
        deliveryDocumentsSearchHandler.fetchDeliveryDocument(
            instructionRequest, MockHttpHeaders.getHeaders());
    assertTrue(deliveryDocuments.size() > 0);
  }

  @Test
  public void testFetchDeliveryDocumentByUpc() throws ReceivingException {
    List<DeliveryDocument> deliveryDocumentsFromGdm = MockInstruction.getDeliveryDocuments();
    doReturn(gson.toJson(deliveryDocumentsFromGdm))
        .when(deliveryService)
        .findDeliveryDocument(anyLong(), anyString(), any());
    List<DeliveryDocument> deliveryDocuments =
        deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
            21119003, "00000943037204", MockHttpHeaders.getHeaders());
    assertTrue(deliveryDocuments.size() > 0);
  }

  @Test
  public void testFetchDeliveryDocumentByUpc_EmptyResponseBody() throws ReceivingException {
    doReturn("[]").when(deliveryService).findDeliveryDocument(anyLong(), anyString(), any());
    try {
      List<DeliveryDocument> deliveryDocuments =
          deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
              21119003, "00000943037204", MockHttpHeaders.getHeaders());
      fail();
    } catch (ReceivingDataNotFoundException exc) {
      assertEquals(ExceptionCodes.PO_LINE_NOT_FOUND, exc.getErrorCode());
    }
  }

  @Test
  public void testFetchDeliveryDocumentByUpc_ExceptionInGDMCall() throws ReceivingException {
    GdmError gdmError = GdmErrorCode.getErrorValue(ReceivingException.ITEM_NOT_FOUND_ERROR);
    ErrorResponse errorResponse =
        ErrorResponse.builder()
            .errorMessage(gdmError.getErrorMessage())
            .errorCode(gdmError.getErrorCode())
            .errorHeader(gdmError.getLocalisedErrorHeader())
            .errorKey(ExceptionCodes.PO_LINE_NOT_FOUND)
            .build();
    doThrow(
            ReceivingException.builder()
                .httpStatus(HttpStatus.NOT_FOUND)
                .errorResponse(errorResponse)
                .build())
        .when(deliveryService)
        .findDeliveryDocument(anyLong(), anyString(), any());
    try {
      List<DeliveryDocument> deliveryDocuments =
          deliveryDocumentsSearchHandler.fetchDeliveryDocumentByUpc(
              21119003, "00000943037204", MockHttpHeaders.getHeaders());
      fail();
    } catch (ReceivingDataNotFoundException exc) {
      assertEquals(ExceptionCodes.PO_LINE_NOT_FOUND, exc.getErrorCode());
    }
  }

  @Test
  public void testFetchDeliveryDocumentByItemNumber() {
    try {
      deliveryDocumentsSearchHandler.fetchDeliveryDocumentByItemNumber(
          "21119003", 943037204, MockHttpHeaders.getHeaders());
      fail();
    } catch (ReceivingException exc) {
      assertEquals(HttpStatus.NOT_IMPLEMENTED, exc.getHttpStatus());
      assertEquals(ReceivingException.NOT_IMPLEMENTED_EXCEPTION, exc.getMessage());
    }
  }
}
