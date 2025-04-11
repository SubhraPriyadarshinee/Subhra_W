package com.walmart.move.nim.receiving.mfc.service;

import static org.mockito.Mockito.*;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.DeliveryStatusPublisher;
import com.walmart.move.nim.receiving.core.model.InstructionSearchRequest;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import java.util.Collections;
import java.util.Optional;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class TrailerOpenInstructionRequestHandlerTest extends ReceivingTestBase {

  @InjectMocks private TrailerOpenInstructionRequestHandler trailerOpenInstructionRequestHandler;

  @Mock private DeliveryStatusPublisher deliveryStatusPublisher;

  @Mock private MFCDeliveryMetadataService deliveryMetaDataService;

  @BeforeClass
  private void init() {
    MockitoAnnotations.openMocks(this);
    ReflectionTestUtils.setField(
        trailerOpenInstructionRequestHandler, "deliveryStatusPublisher", deliveryStatusPublisher);
    ReflectionTestUtils.setField(
        trailerOpenInstructionRequestHandler, "deliveryMetaDataService", deliveryMetaDataService);
  }

  @AfterMethod
  private void resetMocks() {
    Mockito.reset(deliveryStatusPublisher);
    Mockito.reset(deliveryMetaDataService);
  }

  @Test
  public void testInstructionSummaryForDeliveryStatusArrived() {
    InstructionSearchRequest instructionSearchRequest = new InstructionSearchRequest();
    instructionSearchRequest.setDeliveryNumber(43124232L);
    instructionSearchRequest.setDeliveryStatus(DeliveryStatus.ARV.toString());
    when(deliveryMetaDataService.findByDeliveryNumber(anyString())).thenReturn(Optional.empty());

    trailerOpenInstructionRequestHandler.getInstructionSummary(
        instructionSearchRequest, Collections.emptyMap());

    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(
            instructionSearchRequest.getDeliveryNumber(),
            DeliveryStatus.OPEN.toString(),
            null,
            Collections.emptyMap());
    verify(deliveryMetaDataService, times(1)).save(any());
  }

  @Test
  public void testInstructionSummaryForDeliveryStatusScheduled() {
    InstructionSearchRequest instructionSearchRequest = new InstructionSearchRequest();
    instructionSearchRequest.setDeliveryNumber(43124232L);
    instructionSearchRequest.setDeliveryStatus(DeliveryStatus.SCH.toString());
    when(deliveryMetaDataService.findByDeliveryNumber(anyString())).thenReturn(Optional.empty());

    trailerOpenInstructionRequestHandler.getInstructionSummary(
        instructionSearchRequest, Collections.emptyMap());

    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(
            instructionSearchRequest.getDeliveryNumber(),
            DeliveryStatus.OPEN.toString(),
            null,
            Collections.emptyMap());
    verify(deliveryMetaDataService, times(1)).save(any());
  }

  @Test
  public void testInstructionSummaryForDeliveryStatusOpen() {
    InstructionSearchRequest instructionSearchRequest = new InstructionSearchRequest();
    instructionSearchRequest.setDeliveryNumber(43124232L);
    instructionSearchRequest.setDeliveryStatus(DeliveryStatus.OPEN.toString());

    trailerOpenInstructionRequestHandler.getInstructionSummary(
        instructionSearchRequest, Collections.emptyMap());

    verify(deliveryStatusPublisher, never())
        .publishDeliveryStatus(
            instructionSearchRequest.getDeliveryNumber(),
            DeliveryStatus.OPEN.toString(),
            null,
            Collections.emptyMap());
  }

  @Test
  public void testInstructionSummaryForDeliveryStatusArrivedWithLowerCase() {
    InstructionSearchRequest instructionSearchRequest = new InstructionSearchRequest();
    instructionSearchRequest.setDeliveryNumber(43124232L);
    instructionSearchRequest.setDeliveryStatus("arv");
    when(deliveryMetaDataService.findByDeliveryNumber(anyString())).thenReturn(Optional.empty());
    trailerOpenInstructionRequestHandler.getInstructionSummary(
        instructionSearchRequest, Collections.emptyMap());

    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(
            instructionSearchRequest.getDeliveryNumber(),
            DeliveryStatus.OPEN.toString(),
            null,
            Collections.emptyMap());
    verify(deliveryMetaDataService, times(1)).save(any());
  }

  @Test
  public void testInstructionSummaryForDeliveryStatusWorking() {
    InstructionSearchRequest instructionSearchRequest = new InstructionSearchRequest();
    instructionSearchRequest.setDeliveryNumber(43124232L);
    instructionSearchRequest.setDeliveryStatus("WRK");
    when(deliveryMetaDataService.findByDeliveryNumber(anyString())).thenReturn(Optional.empty());
    trailerOpenInstructionRequestHandler.getInstructionSummary(
        instructionSearchRequest, Collections.emptyMap());

    verify(deliveryStatusPublisher, never())
        .publishDeliveryStatus(
            instructionSearchRequest.getDeliveryNumber(),
            DeliveryStatus.OPEN.toString(),
            null,
            Collections.emptyMap());
    verify(deliveryMetaDataService, never()).save(any());
  }
}
