package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.fail;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.mock.data.MockInstruction;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.gdm.GdmDeliveryDocumentResponse;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import java.util.ArrayList;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class DefaultDeliveryDocumentsSearchHandlerTest {

  @InjectMocks private DefaultDeliveryDocumentsSearchHandler deliveryDocumentsSearchHandler;
  private Gson gson = new Gson();
  GdmDeliveryDocumentResponse gdmDeliveryDocumentResponse = new GdmDeliveryDocumentResponse();
  @Mock private DeliveryService deliveryService;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(deliveryDocumentsSearchHandler, "gson", gson);
  }

  @BeforeMethod
  public void beforeMethod() {
    List<DeliveryDocument> deliveryDocuments1 = MockInstruction.getDeliveryDocuments();
    List<DeliveryDocument> oneDeliveryDocuments = new ArrayList<>();
    oneDeliveryDocuments.add(deliveryDocuments1.get(0));
    gdmDeliveryDocumentResponse.setDeliveryDocuments(oneDeliveryDocuments);
  }

  @Test
  public void testFetchDeliveryDocument_UPC() throws ReceivingException {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();

    doReturn(gson.toJson(gdmDeliveryDocumentResponse.getDeliveryDocuments()))
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
