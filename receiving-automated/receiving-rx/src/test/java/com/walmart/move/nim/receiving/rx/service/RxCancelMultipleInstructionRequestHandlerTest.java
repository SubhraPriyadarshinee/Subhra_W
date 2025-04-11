package com.walmart.move.nim.receiving.rx.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.ContainerDetails;
import com.walmart.move.nim.receiving.core.model.MultipleCancelInstructionsRequestBody;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.data.MockRxHttpHeaders;
import com.walmart.move.nim.receiving.rx.builders.RxReceiptsBuilder;
import com.walmart.move.nim.receiving.rx.config.RxManagedConfig;
import com.walmart.move.nim.receiving.rx.publisher.RxCancelInstructionReceiptPublisher;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RxCancelMultipleInstructionRequestHandlerTest {

  @Mock private InstructionPersisterService instructionPersisterService;
  @Mock private ContainerService containerService;
  @Mock private RxInstructionHelperService rxInstructionHelperService;
  @Mock private RxReceiptsBuilder rxReceiptsBuilder;
  @Mock private RxCancelInstructionReceiptPublisher rxCancelInstructionReceiptsPublisher;
  @Mock private RxManagedConfig rxManagedConfig;

  @InjectMocks
  private RxCancelMultipleInstructionRequestHandler rxCancelMultipleInstructionRequestHandler;

  @BeforeMethod
  public void createNimRdsServiceImpl() throws Exception {
    MockitoAnnotations.initMocks(this);

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32897);
  }

  @AfterMethod
  public void afterMethod() {
    reset(instructionPersisterService);
    reset(containerService);
    reset(rxInstructionHelperService);
    reset(rxReceiptsBuilder);
    reset(rxCancelInstructionReceiptsPublisher);
  }

  @Test
  public void test_cancelInstructions() throws ReceivingException {

    MultipleCancelInstructionsRequestBody mockMultipleCancelInstructionsRequestBody =
        new MultipleCancelInstructionsRequestBody();
    mockMultipleCancelInstructionsRequestBody.setInstructionIds(Arrays.asList(1l, 2l, 3l, 4l));
    HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();

    doAnswer(
            (Answer<Instruction>)
                invocation -> {
                  Long instructionId = (Long) invocation.getArguments()[0];
                  Instruction instruction = new Instruction();
                  instruction.setId(instructionId);
                  instruction.setCreateUserId("rxTestUser");

                  instruction.setContainer(new ContainerDetails());

                  return instruction;
                })
        .when(instructionPersisterService)
        .getInstructionById(anyLong());
    doNothing()
        .when(rxInstructionHelperService)
        .rollbackContainers(anyList(), anyList(), anyList());
    doAnswer(
            (Answer<Receipt>)
                invocation -> {
                  Receipt receipt = new Receipt();
                  return receipt;
                })
        .when(rxReceiptsBuilder)
        .buildReceipt(any(Instruction.class), anyString(), anyInt(), anyInt());

    List<Container> mockContainers = new ArrayList<>();
    Container parentContainer = new Container();
    parentContainer.setTrackingId("MOCK_PARENT_TRACKING_ID");

    ContainerItem parentContainerItem = new ContainerItem();
    parentContainerItem.setQuantity(10);
    parentContainerItem.setVnpkQty(10);
    parentContainerItem.setWhpkQty(10);
    parentContainerItem.setQuantityUOM("EA");
    parentContainer.setContainerItems(Arrays.asList(parentContainerItem));

    mockContainers.add(parentContainer);

    Container childContainer = new Container();
    childContainer.setTrackingId("MOCK_CHILD_TRACKING_ID");
    childContainer.setParentTrackingId("MOCK_PARENT_TRACKING_ID");
    mockContainers.add(childContainer);

    doReturn(mockContainers).when(containerService).getContainerByInstruction(anyLong());

    rxCancelMultipleInstructionRequestHandler.cancelInstructions(
        mockMultipleCancelInstructionsRequestBody, httpHeaders);

    verify(instructionPersisterService, times(4)).getInstructionById(anyLong());
    verify(rxInstructionHelperService, times(1))
        .rollbackContainers(anyList(), anyList(), anyList());
    verify(rxReceiptsBuilder, times(4))
        .buildReceiptToRollbackInEaches(any(Instruction.class), anyString(), anyInt(), anyInt());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_cancelInstructions_invalid_user() throws ReceivingException {

    MultipleCancelInstructionsRequestBody mockMultipleCancelInstructionsRequestBody =
        new MultipleCancelInstructionsRequestBody();
    mockMultipleCancelInstructionsRequestBody.setInstructionIds(Arrays.asList(1l, 2l, 3l, 4l));
    HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();

    doAnswer(
            (Answer<Instruction>)
                invocation -> {
                  Long instructionId = (Long) invocation.getArguments()[0];
                  Instruction instruction = new Instruction();
                  instruction.setId(instructionId);
                  instruction.setCreateUserId("rxTestUser1");

                  instruction.setContainer(new ContainerDetails());

                  return instruction;
                })
        .when(instructionPersisterService)
        .getInstructionById(anyLong());
    doNothing()
        .when(rxInstructionHelperService)
        .rollbackContainers(anyList(), anyList(), anyList());
    doNothing()
        .when(rxCancelInstructionReceiptsPublisher)
        .publishReceipt(any(Instruction.class), any(HttpHeaders.class));
    doAnswer(
            (Answer<Receipt>)
                invocation -> {
                  Receipt receipt = new Receipt();
                  return receipt;
                })
        .when(rxReceiptsBuilder)
        .buildReceipt(any(Instruction.class), anyString(), anyInt(), anyInt());

    List<Container> mockContainers = new ArrayList<>();
    Container parentContainer = new Container();
    parentContainer.setTrackingId("MOCK_PARENT_TRACKING_ID");

    ContainerItem parentContainerItem = new ContainerItem();
    parentContainerItem.setQuantity(10);
    parentContainerItem.setVnpkQty(10);
    parentContainerItem.setWhpkQty(10);
    parentContainerItem.setQuantityUOM("EA");
    parentContainer.setContainerItems(Arrays.asList(parentContainerItem));

    mockContainers.add(parentContainer);

    Container childContainer = new Container();
    childContainer.setTrackingId("MOCK_CHILD_TRACKING_ID");
    childContainer.setParentTrackingId("MOCK_PARENT_TRACKING_ID");
    mockContainers.add(childContainer);

    doReturn(mockContainers).when(containerService).getContainerByInstruction(anyLong());

    rxCancelMultipleInstructionRequestHandler.cancelInstructions(
        mockMultipleCancelInstructionsRequestBody, httpHeaders);

    verify(instructionPersisterService, times(4)).getInstructionById(anyLong());
    verify(rxInstructionHelperService, times(1))
        .rollbackContainers(anyList(), anyList(), anyList());
    verify(rxCancelInstructionReceiptsPublisher, times(4))
        .publishReceipt(any(Instruction.class), any(HttpHeaders.class));
    verify(rxReceiptsBuilder, times(4))
        .buildReceipt(any(Instruction.class), anyString(), anyInt(), anyInt());
  }

  @Test
  public void test_fail_cancelInstructions_open_instructions() throws ReceivingException {

    doAnswer(
            (Answer<Instruction>)
                invocation -> {
                  Long instructionId = (Long) invocation.getArguments()[0];
                  Instruction instruction = new Instruction();
                  instruction.setId(instructionId);
                  if (instructionId == 1) {
                    instruction.setCompleteTs(new Date());
                  }
                  return instruction;
                })
        .when(instructionPersisterService)
        .getInstructionById(anyLong());

    MultipleCancelInstructionsRequestBody mockMultipleCancelInstructionsRequestBody =
        new MultipleCancelInstructionsRequestBody();
    mockMultipleCancelInstructionsRequestBody.setInstructionIds(Arrays.asList(1l, 2l, 3l, 4l));
    HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();

    try {
      rxCancelMultipleInstructionRequestHandler.cancelInstructions(
          mockMultipleCancelInstructionsRequestBody, httpHeaders);
    } catch (ReceivingBadDataException rbde) {
      assertEquals(rbde.getErrorCode(), ExceptionCodes.INSTRUCITON_IS_ALREADY_COMPLETED);
      assertEquals(rbde.getDescription(), ReceivingException.INSTRUCTION_IS_ALREADY_COMPLETED);
    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }

  @Test
  public void test_fail_cancelInstructions_generic_exception() throws ReceivingException {

    doThrow(new RuntimeException()).when(instructionPersisterService).getInstructionById(anyLong());
    MultipleCancelInstructionsRequestBody mockMultipleCancelInstructionsRequestBody =
        new MultipleCancelInstructionsRequestBody();
    mockMultipleCancelInstructionsRequestBody.setInstructionIds(Arrays.asList(1l, 2l, 3l, 4l));
    HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();

    try {
      rxCancelMultipleInstructionRequestHandler.cancelInstructions(
          mockMultipleCancelInstructionsRequestBody, httpHeaders);
    } catch (ReceivingBadDataException rbde) {
      assertEquals(rbde.getErrorCode(), ExceptionCodes.CANCEL_PALLET_ERROR);
      assertEquals(rbde.getDescription(), ReceivingException.CANCEL_INSTRUCTION_ERROR_MSG);
    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }

  @Test
  public void test_cancelInstructions_WithShipment() throws ReceivingException {
    MultipleCancelInstructionsRequestBody mockMultipleCancelInstructionsRequestBody =
        new MultipleCancelInstructionsRequestBody();
    mockMultipleCancelInstructionsRequestBody.setInstructionIds(Arrays.asList(1l, 2l, 3l, 4l));
    HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();
    doReturn(true).when(rxManagedConfig).isRollbackReceiptsByShipment();

    doAnswer(
            (Answer<Instruction>)
                invocation -> {
                  Long instructionId = (Long) invocation.getArguments()[0];
                  Instruction instruction = new Instruction();
                  instruction.setId(instructionId);
                  instruction.setCreateUserId("rxTestUser");

                  instruction.setContainer(new ContainerDetails());

                  return instruction;
                })
        .when(instructionPersisterService)
        .getInstructionById(anyLong());
    doNothing()
        .when(rxInstructionHelperService)
        .rollbackContainers(anyList(), anyList(), anyList());
    doAnswer(
            (Answer<Receipt>)
                invocation -> {
                  Receipt receipt = new Receipt();
                  return receipt;
                })
        .when(rxReceiptsBuilder)
        .buildReceipt(any(Instruction.class), anyString(), anyInt(), anyInt());

    List<Container> mockContainers = new ArrayList<>();
    Container parentContainer = new Container();
    parentContainer.setTrackingId("MOCK_PARENT_TRACKING_ID");

    ContainerItem parentContainerItem = new ContainerItem();
    parentContainerItem.setQuantity(10);
    parentContainerItem.setVnpkQty(10);
    parentContainerItem.setWhpkQty(10);
    parentContainerItem.setQuantityUOM("EA");
    parentContainer.setContainerItems(Arrays.asList(parentContainerItem));

    mockContainers.add(parentContainer);

    Container childContainer = new Container();
    childContainer.setTrackingId("MOCK_CHILD_TRACKING_ID");
    childContainer.setParentTrackingId("MOCK_PARENT_TRACKING_ID");
    mockContainers.add(childContainer);

    doReturn(mockContainers).when(containerService).getContainerByInstruction(anyLong());

    rxCancelMultipleInstructionRequestHandler.cancelInstructions(
        mockMultipleCancelInstructionsRequestBody, httpHeaders);

    verify(instructionPersisterService, times(4)).getInstructionById(anyLong());
    verify(rxInstructionHelperService, times(1))
        .rollbackContainers(anyList(), anyList(), anyList());
    verify(rxReceiptsBuilder, times(4))
        .constructRollbackReceiptsWithShipment(
            any(List.class), any(HashMap.class), any(Instruction.class));
  }
}
