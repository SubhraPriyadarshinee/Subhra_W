package com.walmart.move.nim.receiving.wfs.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class WFSInstructionUtilsTest {
  @Mock private InstructionPersisterService instructionPersisterService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private DeliveryService deliveryService;
  @InjectMocks private WFSInstructionUtils wfsInstructionUtils;

  @BeforeMethod
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testIsCancelInstructionAllowed() throws ReceivingException {
    Instruction instruction = new Instruction();
    Boolean result = wfsInstructionUtils.isCancelInstructionAllowed(instruction);
    assertTrue(result);
  }

  @Test
  public void testIsCancelInstructionAllowedWithCompleteInstruction() throws ReceivingException {
    Instruction instruction = new Instruction();
    instruction.setCompleteTs(new Date());
    Boolean result = wfsInstructionUtils.isCancelInstructionAllowed(instruction);
    assertFalse(result);
  }

  @Test
  public void testPersistForCancelInstructions() throws ReceivingException {
    List<Instruction> instructionList = new ArrayList<>();
    Instruction instruction = mock(Instruction.class);
    instructionList.add(instruction);
    doReturn(instructionList).when(instructionPersisterService).saveAllInstruction(anyList());
    wfsInstructionUtils.persistForCancelInstructions(instructionList);
  }

  @Test
  public void setIsHazmatTrueInDeliveryDocumentLines() {
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(
            eq(ReceivingConstants.WFS_TM_HAZMAT_CHECK_ENABLED)))
        .thenReturn(Boolean.TRUE);

    DeliveryDocument mockDeliveryDocument = new DeliveryDocument();
    DeliveryDocumentLine mockDocumentLine = new DeliveryDocumentLine();
    mockDeliveryDocument.setDeliveryDocumentLines(Collections.singletonList(mockDocumentLine));
    DeliveryDocument otherDeliveryDocument = new DeliveryDocument();

    List<DeliveryDocument> deliveryDocumentList =
        Arrays.asList(mockDeliveryDocument, otherDeliveryDocument);
    wfsInstructionUtils.setIsHazmatTrueInDeliveryDocumentLines(deliveryDocumentList);

    assertEquals(
        deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0).getIsHazmat(), Boolean.TRUE);
    assertNull(deliveryDocumentList.get(1).getDeliveryDocumentLines());
  }

  @Test
  public void testCheckIfDeliveryStatusReceivable_deliveryInWorkingState()
      throws ReceivingException, ReceivingBadDataException {
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setDeliveryStatus(DeliveryStatus.WRK);
    deliveryDocument.setDeliveryLegacyStatus("WRK");

    wfsInstructionUtils.checkIfDeliveryStatusReceivable(deliveryDocument);
  }

  @Test()
  public void testCheckIfDeliveryStatusReceivable_deliveryInNotWorkingState_throwsReopenException()
      throws ReceivingBadDataException {
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setDeliveryStatus(DeliveryStatus.FNL);
    deliveryDocument.setDeliveryLegacyStatus("FNL");
    deliveryDocument.setDeliveryNumber(60073850);
    try {
      wfsInstructionUtils.checkIfDeliveryStatusReceivable(deliveryDocument);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.DELIVERY_NOT_RECEIVABLE_REOPEN);
      assertEquals(
          e.getMessage(),
          "Delivery is in PNDFNL or FNL state. To continue receiving, please reopen the delivery 60073850 from GDM and retry.");
    }
  }

  @Test()
  public void
      testCheckIfDeliveryStatusReceivable_deliveryInNotWorkingState_throwsBadDataException() {
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setDeliveryStatus(DeliveryStatus.SCH);
    deliveryDocument.setDeliveryLegacyStatus("SCH");
    deliveryDocument.setDeliveryNumber(60073850);
    try {
      wfsInstructionUtils.checkIfDeliveryStatusReceivable(deliveryDocument);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.DELIVERY_NOT_RECEIVABLE);
      assertEquals(
          e.getMessage(),
          "Delivery 60073850 can not be received as the status is in SCH in GDM .Please contact your supervisor.");
    }
  }
}
