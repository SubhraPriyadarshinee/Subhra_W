package com.walmart.move.nim.receiving.rx.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.reset;
import static org.testng.Assert.assertEquals;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.ProblemLabel;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.repositories.ProblemRepository;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.core.service.ProblemService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.data.MockRxHttpHeaders;
import com.walmart.move.nim.receiving.rx.mock.MockInstruction;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.Arrays;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RxFixitProblemServiceTest {

  @InjectMocks private RxFixitProblemService rxFixitProblemService;

  @Mock private ProblemService problemService;
  @Mock private ReceiptService receiptService;
  @Mock private ProblemRepository problemRepository;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private InstructionPersisterService instructionPersisterService;

  private Gson gson = new Gson();

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32898);
    ReflectionTestUtils.setField(rxFixitProblemService, "receiptService", receiptService);
    ReflectionTestUtils.setField(rxFixitProblemService, "gson", gson);
    ReflectionTestUtils.setField(problemService, "gson", gson);
  }

  @AfterMethod
  public void tearDown() {
    reset(problemService);
    reset(receiptService);
    reset(problemRepository);
    reset(tenantSpecificConfigReader);
    reset(instructionPersisterService);
  }

  @Test
  public void test_receivedQtyByPoAndPoLine() {
    Resolution resolution = new Resolution();
    resolution.setResolutionPoLineNbr(1232434);
    resolution.setResolutionPoLineNbr(1);
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setVendorPack(15);
    deliveryDocumentLine.setWarehousePack(5);
    doReturn(15L).when(receiptService).getReceivedQtyByPoAndPoLineInEach(any(), any());
    long receiptQty =
        rxFixitProblemService.receivedQtyByPoAndPoLine(resolution, deliveryDocumentLine);
    assertEquals(receiptQty, 1);
  }

  @Test
  public void test_completeProblem_full_resolution_qty() throws Exception {

    FitProblemTagResponse mockFitProblemTagResponse = new FitProblemTagResponse();
    Resolution mockResolution = new Resolution();
    mockResolution.setQuantity(10);
    mockFitProblemTagResponse.setResolutions(Arrays.asList(mockResolution));

    ProblemLabel mockProblemLabel = new ProblemLabel();
    mockProblemLabel.setIssueId("MOCK_ISSUE_ID");
    mockProblemLabel.setResolutionId("MOCK_RESOLUTION_ID");
    mockProblemLabel.setProblemResponse(new Gson().toJson(mockFitProblemTagResponse));

    doReturn(problemService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    doReturn(mockProblemLabel).when(problemService).findProblemLabelByProblemTagId(anyString());
    doReturn(8l)
        .when(instructionPersisterService)
        .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
            anyString(), anyInt(), anyString());
    doReturn("MOCK_SUCCESS")
        .when(problemService)
        .notifyCompleteProblemTag(anyString(), any(Problem.class), anyLong());

    Instruction mockCompletedInstruction = MockInstruction.getRxCompleteInstruction();
    mockCompletedInstruction.setProblemTagId("MOCK_PROBLEM_TAG_ID");
    DeliveryDocumentLine mockDeliveryDocumentLine = new DeliveryDocumentLine();
    mockDeliveryDocumentLine.setPurchaseReferenceNumber("MOCK_PO");
    mockDeliveryDocumentLine.setPurchaseReferenceLineNumber(1);
    mockDeliveryDocumentLine.setVendorPack(1);
    mockDeliveryDocumentLine.setWarehousePack(1);

    rxFixitProblemService.completeProblem(
        mockCompletedInstruction, MockRxHttpHeaders.getHeaders(), mockDeliveryDocumentLine);

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    verify(problemService, times(1)).findProblemLabelByProblemTagId(anyString());
    verify(instructionPersisterService, times(1))
        .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
            anyString(), anyInt(), anyString());
    verify(problemService, times(1))
        .notifyCompleteProblemTag(anyString(), any(Problem.class), anyLong());
    verify(problemRepository, times(1)).delete(any(ProblemLabel.class));
  }

  @Test
  public void test_completeProblem_less_than_resolution_qty() throws Exception {

    FitProblemTagResponse mockFitProblemTagResponse = new FitProblemTagResponse();
    Resolution mockResolution = new Resolution();
    mockResolution.setQuantity(10);
    mockFitProblemTagResponse.setResolutions(Arrays.asList(mockResolution));

    ProblemLabel mockProblemLabel = new ProblemLabel();
    mockProblemLabel.setIssueId("MOCK_ISSUE_ID");
    mockProblemLabel.setResolutionId("MOCK_RESOLUTION_ID");
    mockProblemLabel.setProblemResponse(new Gson().toJson(mockFitProblemTagResponse));

    doReturn(problemService)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    doReturn(mockProblemLabel).when(problemService).findProblemLabelByProblemTagId(anyString());
    doReturn(2l)
        .when(instructionPersisterService)
        .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
            anyString(), anyInt(), anyString());
    doReturn("MOCK_SUCCESS")
        .when(problemService)
        .notifyCompleteProblemTag(anyString(), any(Problem.class), anyLong());

    Instruction mockCompletedInstruction = MockInstruction.getRxCompleteInstruction();
    mockCompletedInstruction.setProblemTagId("MOCK_PROBLEM_TAG_ID");
    DeliveryDocumentLine mockDeliveryDocumentLine = new DeliveryDocumentLine();
    mockDeliveryDocumentLine.setPurchaseReferenceNumber("MOCK_PO");
    mockDeliveryDocumentLine.setPurchaseReferenceLineNumber(1);
    mockDeliveryDocumentLine.setVendorPack(1);
    mockDeliveryDocumentLine.setWarehousePack(1);

    rxFixitProblemService.completeProblem(
        mockCompletedInstruction, MockRxHttpHeaders.getHeaders(), mockDeliveryDocumentLine);

    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredInstance(anyString(), anyString(), any(Class.class));
    verify(problemService, times(1)).findProblemLabelByProblemTagId(anyString());
    verify(instructionPersisterService, times(1))
        .getSumOfReceivedQuantityOfCompletedInstructionsByProblemTagIdAndPoPoLine(
            anyString(), anyInt(), anyString());
    verify(problemService, times(1))
        .notifyCompleteProblemTag(anyString(), any(Problem.class), anyLong());
    verify(problemRepository, times(0)).delete(any(ProblemLabel.class));
  }
}
