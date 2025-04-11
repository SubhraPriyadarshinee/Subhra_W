package com.walmart.move.nim.receiving.core.service;

import static org.mockito.Mockito.reset;
import static org.testng.AssertJUnit.assertEquals;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.validators.WeightThresholdValidator;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.mock.data.MockInstruction;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.DocumentLine;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class CCUpdateInstructionHandlerTest extends ReceivingTestBase {
  @Mock WeightThresholdValidator weightThresholdValidator;
  @InjectMocks CCUpdateInstructionHandler ccUpdateInstructionHandler;
  Gson gson;

  @BeforeClass
  public void setUpBeforeClass() throws Exception {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32898);
    gson = new Gson();
  }

  @AfterMethod
  public void teardown() {
    reset(weightThresholdValidator);
  }

  @Test
  public void testUpdateTotalReceivedQtyInInstructionDeliveryDoc() {
    DocumentLine documentLine = MockInstruction.getMockDocumentLine();
    Instruction instruction = MockInstruction.getInstruction();

    DeliveryDocument instructionDeliveryDocument =
        gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    DeliveryDocumentLine instructionDocumentLine =
        instructionDeliveryDocument.getDeliveryDocumentLines().get(0);
    instructionDocumentLine.setTotalOrderQty(200);
    instruction.setDeliveryDocument(gson.toJson(instructionDeliveryDocument));

    documentLine.setTotalReceivedQty(100);
    ccUpdateInstructionHandler.updateTotalReceivedQtyInInstructionDeliveryDoc(
        instruction, documentLine, 10);

    instructionDeliveryDocument =
        gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    instructionDocumentLine = instructionDeliveryDocument.getDeliveryDocumentLines().get(0);
    assertEquals((int) instructionDocumentLine.getTotalReceivedQty(), 110);
  }
}
