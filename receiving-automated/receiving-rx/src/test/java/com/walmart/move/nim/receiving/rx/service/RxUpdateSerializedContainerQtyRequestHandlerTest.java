package com.walmart.move.nim.receiving.rx.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.CancelContainerResponse;
import com.walmart.move.nim.receiving.core.model.ContainerUpdateResponse;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.ContainerAdjustmentHelper;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.data.MockRxHttpHeaders;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.mock.MockInstruction;
import com.walmart.move.nim.receiving.rx.mock.RxMockContainer;
import com.walmart.move.nim.receiving.rx.model.RxInstructionType;
import com.walmart.move.nim.receiving.rx.model.SerializedContainerUpdateRequest;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RxUpdateSerializedContainerQtyRequestHandlerTest {

  @Mock private RxContainerAdjustmentValidator rxContainerAdjustmentValidator;
  @Mock private ContainerAdjustmentHelper containerAdjustmentHelper;
  @Mock private ContainerService containerService;
  @Mock private NimRdsServiceImpl nimRdsService;
  @Mock private InstructionRepository instructionRepository;
  @Mock private RxReceivingCorrectionPrintJobBuilder rxReceivingCorrectionPrintJobBuilder;

  @InjectMocks
  private RxUpdateSerializedContainerQtyRequestHandler rxUpdateSerializedContainerQtyRequestHandler;

  @BeforeMethod
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32897);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setAdditionalParams("WMT-UserId", "sysadmin");
    ReflectionTestUtils.setField(rxUpdateSerializedContainerQtyRequestHandler, "gson", new Gson());
  }

  @Test
  public void test_updateQuantityByTrackingId() throws Exception {

    Container mockContainer = RxMockContainer.getContainer();
    mockContainer.setInstructionId(1l);

    Set<Container> mockContainerSet = new HashSet<>();
    mockContainerSet.add(mockContainer);

    SerializedContainerUpdateRequest mockInput = new SerializedContainerUpdateRequest();
    mockInput.setAdjustQuantity(10);
    mockInput.setTrackingIds(Arrays.asList("MOCK_CHILD_TRACKING_ID"));

    when(rxContainerAdjustmentValidator.validateContainerForAdjustment(any(), any()))
        .thenReturn(new CancelContainerResponse("trackingId", "errorCode", "errorMessage"));
    when(containerAdjustmentHelper.adjustPalletQuantity(anyInt(), any(), anyString()))
        .thenReturn(mockContainer);
    when(containerAdjustmentHelper.adjustQuantityInReceipt(
            anyInt(), anyString(), any(), anyString()))
        .thenReturn(new Receipt());
    when(containerService.getContainerByTrackingId(anyString())).thenReturn(mockContainer);
    when(rxReceivingCorrectionPrintJobBuilder.getPrintJobForReceivingCorrection(
            anyInt(), anyString(), any()))
        .thenReturn(Collections.emptyMap());
    when(instructionRepository.findById(anyLong()))
        .thenReturn(Optional.of(MockInstruction.getRxCompleteInstruction()));
    doNothing().when(nimRdsService).quantityChange(anyInt(), anyString(), any(HttpHeaders.class));

    ContainerUpdateResponse result =
        rxUpdateSerializedContainerQtyRequestHandler.updateQuantityByTrackingId(
            "trackingId", mockInput, MockRxHttpHeaders.getHeaders());

    verify(rxContainerAdjustmentValidator, times(1)).validateContainerForAdjustment(any(), any());
    verify(containerAdjustmentHelper, times(1)).adjustPalletQuantity(anyInt(), any(), anyString());
    verify(containerAdjustmentHelper, times(1))
        .adjustQuantityInReceipt(anyInt(), anyString(), any(), anyString());
    verify(containerService, times(1)).getContainerByTrackingId(anyString());
    verify(rxReceivingCorrectionPrintJobBuilder, times(1))
        .getPrintJobForReceivingCorrection(anyInt(), anyString(), any());
    verify(instructionRepository, times(1)).findById(anyLong());
    verify(nimRdsService, times(1)).quantityChange(anyInt(), anyString(), any(HttpHeaders.class));
  }

  @Test
  public void test_updateQuantityByTrackingId_partialContainer() throws Exception {

    Instruction mockInstruction = MockInstruction.getRxCompleteInstruction();
    mockInstruction.setInstructionCode(
        RxInstructionType.BUILD_PARTIAL_CONTAINER.getInstructionType());

    Container mockContainer = RxMockContainer.getContainer();
    mockContainer.setInstructionId(1l);

    Set<Container> mockContainerSet = new HashSet<>();
    mockContainerSet.add(mockContainer);

    SerializedContainerUpdateRequest mockInput = new SerializedContainerUpdateRequest();
    mockInput.setAdjustQuantity(10);
    mockInput.setTrackingIds(Arrays.asList("MOCK_CHILD_TRACKING_ID"));

    when(containerService.getContainerListByTrackingIdList(anyList())).thenReturn(mockContainerSet);
    when(rxContainerAdjustmentValidator.validateContainerForAdjustment(any(), any()))
        .thenReturn(new CancelContainerResponse("trackingId", "errorCode", "errorMessage"));
    when(containerAdjustmentHelper.adjustPalletQuantity(anyInt(), any(), anyString()))
        .thenReturn(mockContainer);
    when(containerAdjustmentHelper.adjustQuantityInReceipt(
            anyInt(), anyString(), any(), anyString()))
        .thenReturn(new Receipt());
    when(containerService.getContainerByTrackingId(anyString())).thenReturn(mockContainer);
    when(rxReceivingCorrectionPrintJobBuilder.getPrintJobForReceivingCorrection(
            anyInt(), anyString(), any()))
        .thenReturn(Collections.emptyMap());
    when(instructionRepository.findById(anyLong())).thenReturn(Optional.of(mockInstruction));
    doNothing().when(nimRdsService).quantityChange(anyInt(), anyString(), any(HttpHeaders.class));

    ContainerUpdateResponse result =
        rxUpdateSerializedContainerQtyRequestHandler.updateQuantityByTrackingId(
            "trackingId", mockInput, MockRxHttpHeaders.getHeaders());

    verify(containerService, times(1)).getContainerListByTrackingIdList(anyList());
    verify(rxContainerAdjustmentValidator, times(1)).validateContainerForAdjustment(any(), any());
    verify(containerAdjustmentHelper, times(2)).adjustPalletQuantity(anyInt(), any(), anyString());
    verify(containerAdjustmentHelper, times(1))
        .adjustQuantityInReceipt(anyInt(), anyString(), any(), anyString());
    verify(containerService, times(1)).getContainerByTrackingId(anyString());
    verify(rxReceivingCorrectionPrintJobBuilder, times(1))
        .getPrintJobForReceivingCorrection(anyInt(), anyString(), any());
    verify(instructionRepository, times(1)).findById(anyLong());
    verify(nimRdsService, times(1)).quantityChange(anyInt(), anyString(), any(HttpHeaders.class));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = RxConstants.INSTRUCTION_NOT_FOUND_FOR_CONTAINER)
  public void test_updateQuantityByTrackingId_no_instruction_found() throws Exception {

    Container mockContainer = RxMockContainer.getContainer();
    Set<Container> mockContainerSet = new HashSet<>();
    mockContainerSet.add(mockContainer);

    SerializedContainerUpdateRequest mockInput = new SerializedContainerUpdateRequest();
    mockInput.setAdjustQuantity(10);
    mockInput.setTrackingIds(Arrays.asList("MOCK_CHILD_TRACKING_ID"));

    when(rxContainerAdjustmentValidator.validateContainerForAdjustment(any(), any()))
        .thenReturn(new CancelContainerResponse("trackingId", "errorCode", "errorMessage"));
    when(containerAdjustmentHelper.adjustPalletQuantity(anyInt(), any(), anyString()))
        .thenReturn(mockContainer);
    when(containerAdjustmentHelper.adjustQuantityInReceipt(
            anyInt(), anyString(), any(), anyString()))
        .thenReturn(new Receipt());
    when(containerService.getContainerByTrackingId(anyString())).thenReturn(mockContainer);

    Set<Container> mockContainers = new HashSet<>();
    Container intermediateContainer = RxMockContainer.getContainer();
    intermediateContainer.setTrackingId("MOCK_INTERMEDIATE_TRACKING_ID");
    mockContainers.add(intermediateContainer);

    when(containerService.getContainerListByTrackingIdList(anyList())).thenReturn(mockContainers);
    when(rxReceivingCorrectionPrintJobBuilder.getPrintJobForReceivingCorrection(
            anyInt(), anyString(), any()))
        .thenReturn(Collections.emptyMap());
    when(instructionRepository.findById(anyLong()))
        .thenReturn(Optional.of(MockInstruction.getRxCompleteInstruction()));
    doNothing().when(nimRdsService).quantityChange(anyInt(), anyString(), any(HttpHeaders.class));

    ContainerUpdateResponse result =
        rxUpdateSerializedContainerQtyRequestHandler.updateQuantityByTrackingId(
            "trackingId", mockInput, MockRxHttpHeaders.getHeaders());

    verify(rxContainerAdjustmentValidator, times(1)).validateContainerForAdjustment(any(), any());
    verify(containerAdjustmentHelper, times(1)).adjustPalletQuantity(anyInt(), any(), anyString());
    verify(containerAdjustmentHelper, times(1))
        .adjustQuantityInReceipt(anyInt(), anyString(), any(), anyString());
    verify(containerService, times(1)).getContainerByTrackingId(anyString());
    verify(rxReceivingCorrectionPrintJobBuilder, times(1))
        .getPrintJobForReceivingCorrection(anyInt(), anyString(), any());
    verify(instructionRepository, times(1)).findById(anyLong());
    verify(nimRdsService, times(1)).quantityChange(anyInt(), anyString(), any(HttpHeaders.class));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = ReceivingException.CONTAINER_NOT_FOUND_ERROR_MSG)
  public void test_updateQuantityByTrackingId_no_parent_container_found() throws Exception {

    Container mockContainer = RxMockContainer.getContainer();
    Set<Container> mockContainerSet = new HashSet<>();
    mockContainerSet.add(mockContainer);

    SerializedContainerUpdateRequest mockInput = new SerializedContainerUpdateRequest();
    mockInput.setAdjustQuantity(10);
    mockInput.setTrackingIds(Arrays.asList("MOCK_CHILD_TRACKING_ID"));

    when(containerService.getContainerByTrackingId(anyString())).thenReturn(null);

    rxUpdateSerializedContainerQtyRequestHandler.updateQuantityByTrackingId(
        "trackingId", mockInput, MockRxHttpHeaders.getHeaders());
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp = ReceivingException.INVALID_PALLET_CORRECTION_QTY)
  public void test_updateQuantityByTrackingId_new_qty_greater_than_zero() throws Exception {

    Container mockContainer = RxMockContainer.getContainer();
    mockContainer.setInstructionId(1l);

    Set<Container> mockContainerSet = new HashSet<>();
    mockContainerSet.add(mockContainer);

    SerializedContainerUpdateRequest mockInput = new SerializedContainerUpdateRequest();
    mockInput.setAdjustQuantity(0);
    mockInput.setTrackingIds(Arrays.asList("MOCK_CHILD_TRACKING_ID"));

    when(containerService.getContainerByTrackingId(anyString())).thenReturn(mockContainer);

    rxUpdateSerializedContainerQtyRequestHandler.updateQuantityByTrackingId(
        "trackingId", mockInput, MockRxHttpHeaders.getHeaders());

    verify(containerService, times(1)).getContainerByTrackingId(anyString());
  }
}
