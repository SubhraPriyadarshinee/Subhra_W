package com.walmart.move.nim.receiving.rdc.service;

import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.ProblemLabel;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.ReceivingType;
import com.walmart.move.nim.receiving.core.repositories.ProblemRepository;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RdcQuantityCalculatorTest {

  @Mock private ProblemRepository problemServiceFixit;

  @Mock private InstructionPersisterService instructionPersisterService;

  @InjectMocks private RdcQuantityCalculator rdcQuantityCalculator;

  @BeforeClass
  public void setUp() {

    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(rdcQuantityCalculator, "gson", new Gson());
  }

  @AfterMethod
  public void teardown() {
    reset(problemServiceFixit);
    reset(instructionPersisterService);
  }

  /*
   *
   * Case : SSCC Qty < PO Qty < TI * HI with No pending instructions
   *
   * */
  @Test
  public void testGetProjectedReceiveQtyByTiHiBasedOnScanType_1() {
    when(problemServiceFixit.findProblemLabelByProblemTagId(anyString()))
        .thenReturn(new ProblemLabel());
    when(instructionPersisterService
            .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
                anyString(), anyInt(), anyString()))
        .thenReturn(Long.valueOf(1));

    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setReceivingType(ReceivingType.SSCC.getReceivingType());
    Instruction mockInstruction = new Instruction();
    mockInstruction.setSsccNumber("00123456789");

    DeliveryDocumentLine mockDeliveryDocumentLine = new DeliveryDocumentLine();
    mockDeliveryDocumentLine.setTotalReceivedQty(0);
    mockDeliveryDocumentLine.setPalletHigh(10);
    mockDeliveryDocumentLine.setPalletTie(10);
    mockDeliveryDocumentLine.setTotalOrderQty(10);
    mockDeliveryDocumentLine.setOverageQtyLimit(10);
    mockDeliveryDocumentLine.setMaxAllowedOverageQtyIncluded(false);

    mockDeliveryDocumentLine.setShippedQty(10);
    mockDeliveryDocumentLine.setShippedQtyUom("ZA");

    int result =
        rdcQuantityCalculator.getProjectedReceiveQtyByTiHiBasedOnScanType(
            mockDeliveryDocumentLine, 0l, mockInstruction);
    Assert.assertEquals(result, 10);

    verify(problemServiceFixit, times(0)).findProblemLabelByProblemTagId(anyString());
    verify(instructionPersisterService, times(0))
        .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
            anyString(), anyInt(), anyString());
  }

  /*
   *
   * Case : PO Qty < TI * HI < SSCC Qty with No pending instructions
   *
   * */
  @Test
  public void testGetProjectedReceiveQtyByTiHiBasedOnScanType_2() {
    when(problemServiceFixit.findProblemLabelByProblemTagId(anyString()))
        .thenReturn(new ProblemLabel());
    when(instructionPersisterService
            .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
                anyString(), anyInt(), anyString()))
        .thenReturn(Long.valueOf(1));

    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setReceivingType(ReceivingType.SSCC.getReceivingType());

    Instruction mockInstruction = new Instruction();
    mockInstruction.setSsccNumber("00123456789");

    DeliveryDocumentLine mockDeliveryDocumentLine = new DeliveryDocumentLine();
    mockDeliveryDocumentLine.setTotalReceivedQty(0);
    mockDeliveryDocumentLine.setPalletHigh(10);
    mockDeliveryDocumentLine.setPalletTie(10);
    mockDeliveryDocumentLine.setTotalOrderQty(10);
    mockDeliveryDocumentLine.setOverageQtyLimit(10);
    mockDeliveryDocumentLine.setMaxAllowedOverageQtyIncluded(false);
    mockDeliveryDocumentLine.setAutoPoSelectionOverageIncluded(false);
    mockDeliveryDocumentLine.setShippedQty(100);
    mockDeliveryDocumentLine.setShippedQtyUom("ZA");

    int result =
        rdcQuantityCalculator.getProjectedReceiveQtyByTiHiBasedOnScanType(
            mockDeliveryDocumentLine, 0l, mockInstruction);
    Assert.assertEquals(result, 10);

    verify(problemServiceFixit, times(0)).findProblemLabelByProblemTagId(anyString());
    verify(instructionPersisterService, times(0))
        .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
            anyString(), anyInt(), anyString());
  }

  /*
   *
   * Case : TI * HI < SSCC Qty < PO Qty with No pending instructions
   *
   * */
  @Test
  public void testGetProjectedReceiveQtyByTiHiBasedOnScanType_3() {
    when(problemServiceFixit.findProblemLabelByProblemTagId(anyString()))
        .thenReturn(new ProblemLabel());
    when(instructionPersisterService
            .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
                anyString(), anyInt(), anyString()))
        .thenReturn(Long.valueOf(1));

    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setReceivingType(ReceivingType.SSCC.getReceivingType());

    Instruction mockInstruction = new Instruction();

    DeliveryDocumentLine mockDeliveryDocumentLine = new DeliveryDocumentLine();
    mockDeliveryDocumentLine.setTotalReceivedQty(0);
    mockDeliveryDocumentLine.setPalletHigh(2);
    mockDeliveryDocumentLine.setPalletTie(2);
    mockDeliveryDocumentLine.setTotalOrderQty(10);
    mockDeliveryDocumentLine.setOverageQtyLimit(10);
    mockDeliveryDocumentLine.setMaxAllowedOverageQtyIncluded(false);

    mockDeliveryDocumentLine.setShippedQty(100);
    mockDeliveryDocumentLine.setShippedQtyUom("ZA");

    int result =
        rdcQuantityCalculator.getProjectedReceiveQtyByTiHiBasedOnScanType(
            mockDeliveryDocumentLine, 0l, mockInstruction);
    Assert.assertEquals(result, 4);

    verify(problemServiceFixit, times(0)).findProblemLabelByProblemTagId(anyString());
    verify(instructionPersisterService, times(0))
        .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
            anyString(), anyInt(), anyString());
  }

  /*
   *
   * Case : SSCC Qty < PO Qty < TI * HI with No pending instructions and qty 2 received by other instruction
   *
   * */
  @Test
  public void testGetProjectedReceiveQtyByTiHiBasedOnScanType_4() {
    when(problemServiceFixit.findProblemLabelByProblemTagId(anyString()))
        .thenReturn(new ProblemLabel());
    when(instructionPersisterService
            .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
                anyString(), anyInt(), anyString()))
        .thenReturn(Long.valueOf(1));

    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setReceivingType(ReceivingType.SSCC.getReceivingType());

    Instruction mockInstruction = new Instruction();
    mockInstruction.setSsccNumber("00123456789");

    DeliveryDocumentLine mockDeliveryDocumentLine = new DeliveryDocumentLine();
    mockDeliveryDocumentLine.setTotalReceivedQty(2);
    mockDeliveryDocumentLine.setPalletHigh(10);
    mockDeliveryDocumentLine.setPalletTie(10);
    mockDeliveryDocumentLine.setTotalOrderQty(10);
    mockDeliveryDocumentLine.setOverageQtyLimit(10);
    mockDeliveryDocumentLine.setMaxAllowedOverageQtyIncluded(false);
    mockDeliveryDocumentLine.setAutoPoSelectionOverageIncluded(false);
    mockDeliveryDocumentLine.setShippedQty(10);
    mockDeliveryDocumentLine.setShippedQtyUom("ZA");

    int result =
        rdcQuantityCalculator.getProjectedReceiveQtyByTiHiBasedOnScanType(
            mockDeliveryDocumentLine, 0l, mockInstruction);
    Assert.assertEquals(result, 8);

    verify(problemServiceFixit, times(0)).findProblemLabelByProblemTagId(anyString());
    verify(instructionPersisterService, times(0))
        .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
            anyString(), anyInt(), anyString());
  }

  /*
   *
   * Case : SSCC Qty < PO Qty < TI * HI with No pending instructions and qty 2 received by other instruction and max allowed overage is true
   *
   * */
  @Test
  public void testGetProjectedReceiveQtyByTiHiBasedOnScanType_5() {
    when(problemServiceFixit.findProblemLabelByProblemTagId(anyString()))
        .thenReturn(new ProblemLabel());
    when(instructionPersisterService
            .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
                anyString(), anyInt(), anyString()))
        .thenReturn(Long.valueOf(1));

    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setReceivingType(ReceivingType.SSCC.getReceivingType());

    Instruction mockInstruction = new Instruction();
    mockInstruction.setSsccNumber("00123456789");

    DeliveryDocumentLine mockDeliveryDocumentLine = new DeliveryDocumentLine();
    mockDeliveryDocumentLine.setTotalReceivedQty(2);
    mockDeliveryDocumentLine.setPalletHigh(10);
    mockDeliveryDocumentLine.setPalletTie(10);
    mockDeliveryDocumentLine.setTotalOrderQty(10);
    mockDeliveryDocumentLine.setOverageQtyLimit(10);
    mockDeliveryDocumentLine.setMaxAllowedOverageQtyIncluded(true);
    mockDeliveryDocumentLine.setAutoPoSelectionOverageIncluded(false);
    mockDeliveryDocumentLine.setShippedQty(10);
    mockDeliveryDocumentLine.setShippedQtyUom("ZA");

    int result =
        rdcQuantityCalculator.getProjectedReceiveQtyByTiHiBasedOnScanType(
            mockDeliveryDocumentLine, 0l, mockInstruction);
    Assert.assertEquals(result, 8);

    verify(problemServiceFixit, times(0)).findProblemLabelByProblemTagId(anyString());
    verify(instructionPersisterService, times(0))
        .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
            anyString(), anyInt(), anyString());
  }

  @Test(
      expectedExceptions = ReceivingDataNotFoundException.class,
      expectedExceptionsMessageRegExp = "No po/poLine found for 00123456789 in delivery 123456")
  public void test_projected_received_qty_is_zero() {

    when(problemServiceFixit.findProblemLabelByProblemTagId(anyString()))
        .thenReturn(new ProblemLabel());
    when(instructionPersisterService
            .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
                anyString(), anyInt(), anyString()))
        .thenReturn(Long.valueOf(1));

    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setReceivingType(ReceivingType.SSCC.getReceivingType());

    Instruction mockInstruction = new Instruction();
    mockInstruction.setSsccNumber("00123456789");
    mockInstruction.setDeliveryNumber(123456l);

    DeliveryDocumentLine mockDeliveryDocumentLine = new DeliveryDocumentLine();
    mockDeliveryDocumentLine.setTotalReceivedQty(20);
    mockDeliveryDocumentLine.setPalletHigh(10);
    mockDeliveryDocumentLine.setPalletTie(10);
    mockDeliveryDocumentLine.setTotalOrderQty(10);
    mockDeliveryDocumentLine.setOverageQtyLimit(10);
    mockDeliveryDocumentLine.setMaxAllowedOverageQtyIncluded(true);

    mockDeliveryDocumentLine.setShippedQty(10);
    mockDeliveryDocumentLine.setShippedQtyUom("ZA");

    int result =
        rdcQuantityCalculator.getProjectedReceiveQtyByTiHiBasedOnScanType(
            mockDeliveryDocumentLine, 0l, mockInstruction);

    verify(problemServiceFixit, times(0)).findProblemLabelByProblemTagId(anyString());
    verify(instructionPersisterService, times(0))
        .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
            anyString(), anyInt(), anyString());
  }
}
