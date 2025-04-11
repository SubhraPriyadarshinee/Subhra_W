package com.walmart.move.nim.receiving.witron.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.InstructionError;
import com.walmart.move.nim.receiving.core.common.exception.InstructionErrorCode;
import com.walmart.move.nim.receiving.core.common.validators.InstructionStateValidator;
import com.walmart.move.nim.receiving.core.common.validators.PurchaseReferenceValidator;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.InstructionResponse;
import com.walmart.move.nim.receiving.core.model.UpdateInstructionRequest;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.data.GdcHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.InstructionStatus;
import com.walmart.move.nim.receiving.witron.mock.data.MockInstruction;
import java.util.HashMap;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class WitronUpdateInstructionHandlerTest extends ReceivingTestBase {
  @InjectMocks private WitronUpdateInstructionHandler witronUpdateInstructionHandler;
  @Mock private InstructionPersisterService instructionPersisterService;
  @Mock private PurchaseReferenceValidator purchaseReferenceValidator;
  @Mock private InstructionStateValidator instructionStateValidator;
  @Mock private InstructionHelperService instructionHelperService;
  @Mock private ReceiptService receiptService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  private HttpHeaders mockHttpHeaders = GdcHttpHeaders.getHeaders();

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);

    TenantContext.setFacilityNum(32612);
    TenantContext.setFacilityCountryCode("US");
  }

  @AfterMethod
  public void setUpTestDataBeforeEachTest() throws ReceivingException {
    reset(instructionPersisterService);
    reset(purchaseReferenceValidator);
    reset(instructionStateValidator);
    reset(instructionHelperService);
    reset(receiptService);
    reset(tenantSpecificConfigReader);
  }

  @Test
  public void testWitronUpdateInstruction() throws ReceivingException {
    when(instructionPersisterService.getInstructionById(any(Long.class)))
        .thenReturn(MockInstruction.getInstruction());
    doNothing().when(purchaseReferenceValidator).validatePOConfirmation(anyString(), anyString());
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    when(instructionHelperService.isManagerOverrideIgnoreExpiry(
            anyString(), anyString(), anyBoolean(), anyInt()))
        .thenReturn(true);
    when(instructionHelperService.isManagerOverrideIgnoreOverage(
            anyString(), anyString(), anyInt()))
        .thenReturn(true);
    when(receiptService.getReceivedQtyByPoAndPoLine(anyString(), anyInt()))
        .thenReturn(Long.parseLong("0"));
    when(instructionPersisterService.getPrintlabeldata(
            any(Instruction.class), anyInt(), anyInt(), any()))
        .thenReturn(new HashMap<String, Object>());
    doNothing()
        .when(instructionPersisterService)
        .createContainersAndPrintJobs(
            any(UpdateInstructionRequest.class),
            any(HttpHeaders.class),
            anyString(),
            any(Instruction.class),
            anyInt(),
            anyInt(),
            any());
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(Container.class),
            any(InstructionStatus.class),
            any(HttpHeaders.class));

    witronUpdateInstructionHandler.updateInstruction(
        MockInstruction.getInstruction().getId(),
        MockInstruction.getUpdateInstructionRequest(),
        null,
        mockHttpHeaders);

    verify(instructionPersisterService, times(1)).getInstructionById(any(Long.class));
    verify(purchaseReferenceValidator, times(1)).validatePOConfirmation(anyString(), anyString());
    verify(instructionStateValidator, times(1)).validate(any(Instruction.class));
    verify(instructionHelperService, times(1))
        .isManagerOverrideIgnoreExpiry(anyString(), anyString(), anyBoolean(), anyInt());
    verify(instructionHelperService, times(1))
        .isManagerOverrideIgnoreOverage(anyString(), anyString(), anyInt());
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(anyString(), anyInt());
    verify(instructionPersisterService, times(1))
        .getPrintlabeldata(any(Instruction.class), anyInt(), anyInt(), any());
    verify(instructionPersisterService, times(1))
        .createContainersAndPrintJobs(
            any(UpdateInstructionRequest.class),
            any(HttpHeaders.class),
            anyString(),
            any(Instruction.class),
            anyInt(),
            anyInt(),
            any());
    verify(instructionHelperService, times(1))
        .publishInstruction(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(),
            any(InstructionStatus.class),
            any(HttpHeaders.class));
  }

  @Test
  public void testWitronUpdateInstructionWithPTAG() throws ReceivingException {
    when(instructionPersisterService.getInstructionById(any(Long.class)))
        .thenReturn(MockInstruction.getInstruction());
    doNothing().when(purchaseReferenceValidator).validatePOConfirmation(anyString(), anyString());
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    when(instructionHelperService.isManagerOverrideIgnoreExpiry(
            anyString(), anyString(), anyBoolean(), anyInt()))
        .thenReturn(true);
    when(instructionHelperService.isManagerOverrideIgnoreOverage(
            anyString(), anyString(), anyInt()))
        .thenReturn(true);
    when(receiptService.getReceivedQtyByProblemId(anyString())).thenReturn(Long.parseLong("0"));
    when(instructionPersisterService.getPrintlabeldata(
            any(Instruction.class), anyInt(), anyInt(), any()))
        .thenReturn(new HashMap<String, Object>());
    doNothing()
        .when(instructionPersisterService)
        .createContainersAndPrintJobs(
            any(UpdateInstructionRequest.class),
            any(HttpHeaders.class),
            anyString(),
            any(Instruction.class),
            anyInt(),
            anyInt(),
            any());
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(Container.class),
            any(InstructionStatus.class),
            any(HttpHeaders.class));

    witronUpdateInstructionHandler.updateInstruction(
        MockInstruction.getInstruction().getId(),
        MockInstruction.getUpdateInstructionRequest(),
        null,
        mockHttpHeaders);

    verify(instructionPersisterService, times(1)).getInstructionById(any(Long.class));
    verify(purchaseReferenceValidator, times(1)).validatePOConfirmation(anyString(), anyString());
    verify(instructionStateValidator, times(1)).validate(any(Instruction.class));
    verify(instructionHelperService, times(1))
        .isManagerOverrideIgnoreExpiry(anyString(), anyString(), anyBoolean(), anyInt());
    verify(instructionHelperService, times(1))
        .isManagerOverrideIgnoreOverage(anyString(), anyString(), anyInt());
    verify(receiptService, times(1)).getReceivedQtyByPoAndPoLine(anyString(), anyInt());
    verify(instructionPersisterService, times(1))
        .getPrintlabeldata(any(Instruction.class), anyInt(), anyInt(), any());
    verify(instructionPersisterService, times(1))
        .createContainersAndPrintJobs(
            any(UpdateInstructionRequest.class),
            any(HttpHeaders.class),
            anyString(),
            any(Instruction.class),
            anyInt(),
            anyInt(),
            any());
    verify(instructionHelperService, times(1))
        .publishInstruction(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(),
            any(InstructionStatus.class),
            any(HttpHeaders.class));
  }

  @Test
  public void testWitronUpdateInstructionWithExceedsQuantity() {
    try {
      Instruction mockInstruction = MockInstruction.getPendingInstruction();
      mockInstruction.setReceivedQuantity(0);
      mockInstruction.setProjectedReceiveQty(1);

      when(instructionPersisterService.getInstructionById(any(Long.class)))
          .thenReturn(mockInstruction);
      doNothing().when(purchaseReferenceValidator).validatePOConfirmation(anyString(), anyString());
      doNothing().when(instructionStateValidator).validate(any(Instruction.class));
      when(instructionHelperService.isManagerOverrideIgnoreExpiry(
              anyString(), anyString(), anyBoolean(), anyInt()))
          .thenReturn(true);
      when(instructionHelperService.isManagerOverrideIgnoreOverage(
              anyString(), anyString(), anyInt()))
          .thenReturn(true);
      when(receiptService.getReceivedQtyByPoAndPoLine(anyString(), anyInt()))
          .thenReturn(Long.parseLong("0"));
      when(instructionPersisterService.getPrintlabeldata(
              any(Instruction.class), anyInt(), anyInt(), any()))
          .thenReturn(new HashMap<String, Object>());
      doNothing()
          .when(instructionPersisterService)
          .createContainersAndPrintJobs(
              any(UpdateInstructionRequest.class),
              any(HttpHeaders.class),
              anyString(),
              any(Instruction.class),
              anyInt(),
              anyInt(),
              any());
      doNothing()
          .when(instructionHelperService)
          .publishInstruction(
              any(Instruction.class),
              any(UpdateInstructionRequest.class),
              anyInt(),
              any(Container.class),
              any(InstructionStatus.class),
              any(HttpHeaders.class));

      witronUpdateInstructionHandler.updateInstruction(
          mockInstruction.getId(),
          MockInstruction.getUpdateInstructionRequest(),
          null,
          mockHttpHeaders);
    } catch (ReceivingException e) {
      assertEquals(e.getMessage(), ReceivingException.UPDATE_INSTRUCTION_EXCEEDS_QUANTITY);
      assertEquals(e.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @Test
  public void testWitronUpdateInstructionWithReachedMaxThreshold() {
    try {
      Instruction mockInstruction = MockInstruction.getPendingInstruction();
      mockInstruction.setReceivedQuantity(1);
      mockInstruction.setProjectedReceiveQty(12);

      when(instructionPersisterService.getInstructionById(any(Long.class)))
          .thenReturn(mockInstruction);
      doNothing().when(purchaseReferenceValidator).validatePOConfirmation(anyString(), anyString());
      doNothing().when(instructionStateValidator).validate(any(Instruction.class));
      when(instructionHelperService.isManagerOverrideIgnoreExpiry(
              anyString(), anyString(), anyBoolean(), anyInt()))
          .thenReturn(true);
      when(instructionHelperService.isManagerOverrideIgnoreOverage(
              anyString(), anyString(), anyInt()))
          .thenReturn(true);
      when(receiptService.getReceivedQtyByPoAndPoLine(anyString(), anyInt()))
          .thenReturn(Long.parseLong("1"));
      when(instructionPersisterService.getPrintlabeldata(
              any(Instruction.class), anyInt(), anyInt(), any()))
          .thenReturn(new HashMap<String, Object>());
      doNothing()
          .when(instructionPersisterService)
          .createContainersAndPrintJobs(
              any(UpdateInstructionRequest.class),
              any(HttpHeaders.class),
              anyString(),
              any(Instruction.class),
              anyInt(),
              anyInt(),
              any());
      doNothing()
          .when(instructionHelperService)
          .publishInstruction(
              any(Instruction.class),
              any(UpdateInstructionRequest.class),
              anyInt(),
              any(Container.class),
              any(InstructionStatus.class),
              any(HttpHeaders.class));

      witronUpdateInstructionHandler.updateInstruction(
          mockInstruction.getId(),
          MockInstruction.getUpdateInstructionRequest(),
          null,
          mockHttpHeaders);
    } catch (ReceivingException e) {
      assertEquals(e.getMessage(), ReceivingException.UPDATE_INSTRUCTION_REACHED_MAXIMUM_THRESHOLD);
      assertEquals(e.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @Test
  public void
      testWitronUpdateInstructionException_currentReceiveQuantity_equals_maxReceiveQuantity() {
    try {
      Instruction mockInstruction = MockInstruction.getPendingInstruction();
      mockInstruction.setReceivedQuantity(1);
      mockInstruction.setProjectedReceiveQty(12);

      when(instructionPersisterService.getInstructionById(any(Long.class)))
          .thenReturn(mockInstruction);
      doNothing().when(purchaseReferenceValidator).validatePOConfirmation(anyString(), anyString());
      doNothing().when(instructionStateValidator).validate(any(Instruction.class));
      when(instructionHelperService.isManagerOverrideIgnoreOverage(
              anyString(), anyString(), anyInt()))
          .thenReturn(false);
      when(receiptService.getReceivedQtyByPoAndPoLine(anyString(), anyInt()))
          .thenReturn(Long.parseLong("1"));
      when(instructionPersisterService.getPrintlabeldata(
              any(Instruction.class), anyInt(), anyInt(), any()))
          .thenReturn(new HashMap<String, Object>());
      doNothing()
          .when(instructionPersisterService)
          .createContainersAndPrintJobs(
              any(UpdateInstructionRequest.class),
              any(HttpHeaders.class),
              anyString(),
              any(Instruction.class),
              anyInt(),
              anyInt(),
              any());
      doNothing()
          .when(instructionHelperService)
          .publishInstruction(
              any(Instruction.class),
              any(UpdateInstructionRequest.class),
              anyInt(),
              any(Container.class),
              any(InstructionStatus.class),
              any(HttpHeaders.class));

      witronUpdateInstructionHandler.updateInstruction(
          mockInstruction.getId(),
          MockInstruction.getUpdateInstructionRequest(),
          null,
          mockHttpHeaders);
    } catch (ReceivingException e) {
      final InstructionError errorValue =
          InstructionErrorCode.getErrorValue(ReceivingException.OVERAGE_ERROR);
      assertEquals(e.getMessage(), errorValue.getErrorMessage());
      assertEquals(e.getHttpStatus(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @Test
  public void testIsReceiveCorrection() throws ReceivingException {
    Instruction mockInstruction = MockInstruction.getInstruction();
    mockInstruction.setIsReceiveCorrection(Boolean.TRUE);

    when(instructionPersisterService.getInstructionById(any(Long.class)))
        .thenReturn(mockInstruction);
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    when(instructionHelperService.isManagerOverrideIgnoreExpiry(
            anyString(), anyString(), anyBoolean(), anyInt()))
        .thenReturn(true);
    when(instructionHelperService.isManagerOverrideIgnoreOverage(
            anyString(), anyString(), anyInt()))
        .thenReturn(true);
    when(receiptService.getReceivedQtyByPoAndPoLine(anyString(), anyInt()))
        .thenReturn(Long.parseLong("0"));
    when(instructionPersisterService.getPrintlabeldata(
            any(Instruction.class), anyInt(), anyInt(), any()))
        .thenReturn(new HashMap<String, Object>());
    doNothing()
        .when(instructionPersisterService)
        .createContainersAndPrintJobs(
            any(UpdateInstructionRequest.class),
            any(HttpHeaders.class),
            anyString(),
            any(Instruction.class),
            anyInt(),
            anyInt(),
            any());
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(Container.class),
            any(InstructionStatus.class),
            any(HttpHeaders.class));

    InstructionResponse response =
        witronUpdateInstructionHandler.updateInstruction(
            mockInstruction.getId(),
            MockInstruction.getUpdateInstructionRequest(),
            null,
            mockHttpHeaders);

    verify(purchaseReferenceValidator, times(0)).validatePOConfirmation(anyString(), anyString());
    assertTrue(response.getInstruction().getIsReceiveCorrection());
  }

  @Test
  public void testWitronUpdateInstructionWithGroceryProblemReceive() throws ReceivingException {
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());

    when(instructionPersisterService.getInstructionById(any(Long.class)))
        .thenReturn(MockInstruction.getInstructionWithPTAG());
    doNothing().when(purchaseReferenceValidator).validatePOConfirmation(anyString(), anyString());
    doNothing().when(instructionStateValidator).validate(any(Instruction.class));
    when(instructionHelperService.isManagerOverrideIgnoreExpiry(
            anyString(), anyString(), anyBoolean(), anyInt()))
        .thenReturn(false);
    when(instructionHelperService.isManagerOverrideIgnoreOverage(
            anyString(), anyString(), anyInt()))
        .thenReturn(false);
    when(receiptService.getReceivedQtyByProblemId(anyString())).thenReturn(Long.parseLong("5"));
    when(instructionPersisterService.getPrintlabeldata(
            any(Instruction.class), anyInt(), anyInt(), any()))
        .thenReturn(new HashMap<String, Object>());
    doNothing()
        .when(instructionPersisterService)
        .createContainersAndPrintJobs(
            any(UpdateInstructionRequest.class),
            any(HttpHeaders.class),
            anyString(),
            any(Instruction.class),
            anyInt(),
            anyInt(),
            any());
    doNothing()
        .when(instructionHelperService)
        .publishInstruction(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(Container.class),
            any(InstructionStatus.class),
            any(HttpHeaders.class));

    witronUpdateInstructionHandler.updateInstruction(
        MockInstruction.getInstructionWithPTAG().getId(),
        MockInstruction.getUpdateInstructionRequestWithPTAG(),
        null,
        mockHttpHeaders);

    verify(instructionPersisterService, times(1)).getInstructionById(any(Long.class));
    verify(purchaseReferenceValidator, times(1)).validatePOConfirmation(anyString(), anyString());
    verify(instructionStateValidator, times(1)).validate(any(Instruction.class));
    verify(instructionHelperService, times(1))
        .isManagerOverrideIgnoreExpiry(anyString(), anyString(), anyBoolean(), anyInt());
    verify(instructionHelperService, times(1))
        .isManagerOverrideIgnoreOverage(anyString(), anyString(), anyInt());
    verify(receiptService, times(1)).getReceivedQtyByProblemId(anyString());
    verify(instructionPersisterService, times(1))
        .getPrintlabeldata(any(Instruction.class), anyInt(), anyInt(), any());
    verify(instructionPersisterService, times(1))
        .createContainersAndPrintJobs(
            any(UpdateInstructionRequest.class),
            any(HttpHeaders.class),
            anyString(),
            any(Instruction.class),
            anyInt(),
            anyInt(),
            any());
    verify(instructionHelperService, times(1))
        .publishInstruction(
            any(Instruction.class),
            any(UpdateInstructionRequest.class),
            anyInt(),
            any(),
            any(InstructionStatus.class),
            any(HttpHeaders.class));
  }
}
