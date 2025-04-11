package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.walmart.move.nim.receiving.core.common.DeliveryStatusPublisher;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.core.model.InstructionSearchRequest;
import com.walmart.move.nim.receiving.core.model.InstructionSummary;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.apache.commons.collections4.CollectionUtils;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class SplitPalletInstructionSearchRequestHandlerTest {

  @InjectMocks
  private SplitPalletInstructionSearchRequestHandler splitPalletInstructionSearchRequestHandler;

  @Mock private DeliveryServiceImpl deliveryService;

  @Mock private InstructionRepository instructionRepository;

  @Mock private DeliveryStatusPublisher deliveryStatusPublisher;

  @Mock private DefaultInstructionSearchRequestHandler defaultInstructionSearchRequestHandler;

  @Mock private TenantSpecificConfigReader configUtils;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  public void restRestUtilCalls() {
    reset(instructionRepository);
    reset(deliveryStatusPublisher);
  }

  @Test
  public void test_getInstructionSummary() {

    TenantContext.setFacilityNum(32679);
    List<Instruction> mockInstructionList = getMockInstructionList();

    when(configUtils.getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean()))
        .thenReturn(Boolean.FALSE);
    doReturn(mockInstructionList)
        .when(instructionRepository)
        .findByDeliveryNumberAndInstructionCodeIsNotNull(anyLong());
    doReturn(new DeliveryInfo())
        .when(deliveryStatusPublisher)
        .publishDeliveryStatus(anyLong(), anyString(), isNull(), any(Map.class));

    InstructionSearchRequest mockInstructionSearchRequest = new InstructionSearchRequest();
    mockInstructionSearchRequest.setDeliveryNumber(12345l);
    mockInstructionSearchRequest.setDeliveryStatus("ARV");
    mockInstructionSearchRequest.setIncludeInstructionSet(true);

    Map<String, Object> mockHeaders = new HashMap<>();
    List<InstructionSummary> instructionSummaryResponse =
        splitPalletInstructionSearchRequestHandler.getInstructionSummary(
            mockInstructionSearchRequest, mockHeaders);

    assertTrue(CollectionUtils.isNotEmpty(instructionSummaryResponse));
    assertEquals(instructionSummaryResponse.size(), 2);
    InstructionSummary instructionSetSummary = instructionSummaryResponse.get(1);
    assertEquals(instructionSetSummary.getInstructionSet().size(), 3);

    assertEquals(
        instructionSetSummary.getCreateTs(),
        instructionSetSummary.getInstructionSet().get(0).getCreateTs());
    assertEquals(
        instructionSetSummary.getCreateUserId(),
        instructionSetSummary.getInstructionSet().get(0).getCreateUserId());
    assertEquals(
        instructionSetSummary.getLastChangeTs(),
        instructionSetSummary.getInstructionSet().get(0).getLastChangeTs());
    assertEquals(
        instructionSetSummary.getLastChangeUserId(),
        instructionSetSummary.getInstructionSet().get(0).getLastChangeUserId());
    assertEquals(
        instructionSetSummary.getCompleteTs(),
        instructionSetSummary.getInstructionSet().get(0).getCompleteTs());
    assertEquals(
        instructionSetSummary.getCompleteUserId(),
        instructionSetSummary.getInstructionSet().get(0).getCompleteUserId());
    assertEquals(
        instructionSetSummary.getCompleteUserId(),
        instructionSetSummary.getInstructionSet().get(0).getCompleteUserId());
    assertEquals(instructionSetSummary.getInstructionCode(), ReceivingConstants.SPLIT_PALLET);
  }

  @Test
  public void test_getInstructionSummary_problemTag_delivery() {

    List<Instruction> mockInstructionList = new ArrayList<>();

    Instruction regularInstruction = new Instruction();
    regularInstruction.setId(1l);
    regularInstruction.setReceivedQuantity(1);
    mockInstructionList.add(regularInstruction);

    doReturn(mockInstructionList)
        .when(instructionRepository)
        .findByDeliveryNumberAndProblemTagIdAndInstructionCodeIsNotNull(anyLong(), anyString());
    doReturn(new DeliveryInfo())
        .when(deliveryStatusPublisher)
        .publishDeliveryStatus(anyLong(), anyString(), isNull(), any(Map.class));

    InstructionSearchRequest mockInstructionSearchRequest = new InstructionSearchRequest();
    mockInstructionSearchRequest.setProblemTagId("98765");
    mockInstructionSearchRequest.setDeliveryNumber(12345l);
    mockInstructionSearchRequest.setDeliveryStatus("ARV");
    mockInstructionSearchRequest.setIncludeInstructionSet(true);

    Map<String, Object> mockHeaders = new HashMap<>();
    List<InstructionSummary> instructionSummaryResponse =
        splitPalletInstructionSearchRequestHandler.getInstructionSummary(
            mockInstructionSearchRequest, mockHeaders);

    assertTrue(CollectionUtils.isNotEmpty(instructionSummaryResponse));
    assertEquals(instructionSummaryResponse.size(), 1);
  }

  @Test
  public void test_getInstructionSummary_problemTag() {

    List<Instruction> mockInstructionList = new ArrayList<>();

    Instruction regularInstruction = new Instruction();
    regularInstruction.setId(1l);
    regularInstruction.setReceivedQuantity(1);
    mockInstructionList.add(regularInstruction);

    doReturn(mockInstructionList)
        .when(instructionRepository)
        .findByProblemTagIdAndInstructionCodeIsNotNull(anyString());
    doReturn(new DeliveryInfo())
        .when(deliveryStatusPublisher)
        .publishDeliveryStatus(anyLong(), anyString(), isNull(), any(Map.class));

    InstructionSearchRequest mockInstructionSearchRequest = new InstructionSearchRequest();
    mockInstructionSearchRequest.setProblemTagId("98765");
    mockInstructionSearchRequest.setDeliveryStatus("ARV");
    mockInstructionSearchRequest.setIncludeInstructionSet(true);

    Map<String, Object> mockHeaders = new HashMap<>();
    List<InstructionSummary> instructionSummaryResponse =
        splitPalletInstructionSearchRequestHandler.getInstructionSummary(
            mockInstructionSearchRequest, mockHeaders);

    assertTrue(CollectionUtils.isNotEmpty(instructionSummaryResponse));
    assertEquals(instructionSummaryResponse.size(), 1);
  }

  @Test
  public void test_getInstructionSummary_instruction_set_false() {

    doReturn(Collections.<InstructionSummary>emptyList())
        .when(defaultInstructionSearchRequestHandler)
        .getInstructionSummary(any(InstructionSearchRequest.class), anyMap());

    InstructionSearchRequest mockInstructionSearchRequest = new InstructionSearchRequest();
    mockInstructionSearchRequest.setProblemTagId("98765");
    mockInstructionSearchRequest.setDeliveryStatus("ARV");
    mockInstructionSearchRequest.setIncludeInstructionSet(false);

    Map<String, Object> mockHeaders = new HashMap<>();

    List<InstructionSummary> instructionSummaryResponse =
        splitPalletInstructionSearchRequestHandler.getInstructionSummary(
            mockInstructionSearchRequest, mockHeaders);

    assertTrue(CollectionUtils.isEmpty(instructionSummaryResponse));

    verify(defaultInstructionSearchRequestHandler, times(1))
        .getInstructionSummary(any(InstructionSearchRequest.class), anyMap());
  }

  @Test
  public void test_getInstructionSummary_includeCompletedInstructionsSetFalse() {
    List<Instruction> mockInstructionList = getMockInstructionList();
    doReturn(mockInstructionList)
        .when(instructionRepository)
        .findByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(anyLong());

    doReturn(new DeliveryInfo())
        .when(deliveryStatusPublisher)
        .publishDeliveryStatus(anyLong(), anyString(), isNull(), any(Map.class));

    InstructionSearchRequest mockInstructionSearchRequest = new InstructionSearchRequest();
    mockInstructionSearchRequest.setDeliveryNumber(27526360L);
    mockInstructionSearchRequest.setDeliveryStatus("ARV");
    mockInstructionSearchRequest.setIncludeInstructionSet(true);
    mockInstructionSearchRequest.setIncludeCompletedInstructions(false);

    Map<String, Object> mockHeaders = new HashMap<>();

    List<InstructionSummary> instructionSummaryResponse =
        splitPalletInstructionSearchRequestHandler.getInstructionSummary(
            mockInstructionSearchRequest, mockHeaders);

    assertTrue(CollectionUtils.isNotEmpty(instructionSummaryResponse));
    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(anyLong());
    verify(instructionRepository, times(0))
        .findByDeliveryNumberAndInstructionCodeIsNotNull(anyLong());
  }

  @Test
  public void test_getInstructionSummary_includeCompletedInstructionsDefaultTrue() {
    List<Instruction> mockInstructionList = getMockInstructionList();
    doReturn(mockInstructionList)
        .when(instructionRepository)
        .findByDeliveryNumberAndInstructionCodeIsNotNull(anyLong());

    doReturn(new DeliveryInfo())
        .when(deliveryStatusPublisher)
        .publishDeliveryStatus(anyLong(), anyString(), isNull(), any(Map.class));

    InstructionSearchRequest mockInstructionSearchRequest = new InstructionSearchRequest();
    mockInstructionSearchRequest.setDeliveryNumber(27526360L);
    mockInstructionSearchRequest.setDeliveryStatus("ARV");
    mockInstructionSearchRequest.setIncludeInstructionSet(true);

    Map<String, Object> mockHeaders = new HashMap<>();
    List<InstructionSummary> instructionSummaryResponse =
        splitPalletInstructionSearchRequestHandler.getInstructionSummary(
            mockInstructionSearchRequest, mockHeaders);

    assertEquals(mockInstructionSearchRequest.isIncludeCompletedInstructions(), true);
    assertTrue(CollectionUtils.isNotEmpty(instructionSummaryResponse));
    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndInstructionCodeIsNotNull(anyLong());
    verify(instructionRepository, times(0))
        .findByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(anyLong());
  }

  @Test
  public void test_updateDeliveryStatusToOpenEventForARVStatus() {

    TenantContext.setFacilityNum(32679);
    Map<String, Object> mockHeaders = MockHttpHeaders.getHttpHeadersMap();
    List<Instruction> mockInstructionList = getMockInstructionList();
    doReturn(mockInstructionList)
        .when(instructionRepository)
        .findByDeliveryNumberAndInstructionCodeIsNotNull(anyLong());

    doReturn(new DeliveryInfo())
        .when(deliveryStatusPublisher)
        .publishDeliveryStatus(anyLong(), anyString(), isNull(), any(Map.class));
    when(configUtils.getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean()))
        .thenReturn(Boolean.TRUE);
    when(deliveryService.updateDeliveryStatusToOpen(anyLong(), any(Map.class)))
        .thenReturn(new GdmDeliveryStatusUpdateEvent());
    InstructionSearchRequest mockInstructionSearchRequest = new InstructionSearchRequest();
    mockInstructionSearchRequest.setDeliveryNumber(27526360L);
    mockInstructionSearchRequest.setDeliveryStatus("ARV");
    mockInstructionSearchRequest.setIncludeInstructionSet(true);

    List<InstructionSummary> instructionSummaryResponse =
        splitPalletInstructionSearchRequestHandler.getInstructionSummary(
            mockInstructionSearchRequest, mockHeaders);

    assertEquals(mockInstructionSearchRequest.isIncludeCompletedInstructions(), true);
    assertTrue(CollectionUtils.isNotEmpty(instructionSummaryResponse));
    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndInstructionCodeIsNotNull(anyLong());
    verify(instructionRepository, times(0))
        .findByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(anyLong());
  }

  private List<Instruction> getMockInstructionList() {
    List<Instruction> mockInstructionList = new ArrayList<>();
    Instruction regularInstruction = new Instruction();
    regularInstruction.setId(1l);
    regularInstruction.setReceivedQuantity(1);
    mockInstructionList.add(regularInstruction);

    Instruction instructionSet1 = new Instruction();
    instructionSet1.setId(2l);
    instructionSet1.setReceivedQuantity(1);
    instructionSet1.setInstructionSetId(1l);
    mockInstructionList.add(instructionSet1);

    Instruction instructionSet2 = new Instruction();
    instructionSet2.setId(3l);
    instructionSet2.setReceivedQuantity(1);
    instructionSet2.setInstructionSetId(1l);
    mockInstructionList.add(instructionSet2);

    Instruction instructionSet3 = new Instruction();
    instructionSet3.setId(4l);
    instructionSet3.setReceivedQuantity(1);
    instructionSet3.setInstructionSetId(1l);
    mockInstructionList.add(instructionSet3);

    return mockInstructionList;
  }
}
