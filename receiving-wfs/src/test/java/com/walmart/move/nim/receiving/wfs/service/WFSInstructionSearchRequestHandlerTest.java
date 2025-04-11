package com.walmart.move.nim.receiving.wfs.service;

import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.walmart.move.nim.receiving.core.common.DeliveryStatusPublisher;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.model.InstructionSearchRequest;
import com.walmart.move.nim.receiving.core.model.InstructionSummary;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.HashMap;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class WFSInstructionSearchRequestHandlerTest {

  @InjectMocks private WFSInstructionSearchRequestHandler wfsInstructionSearchRequestHandler;

  @Mock private DeliveryStatusPublisher deliveryStatusPublisher;

  @Mock private TenantSpecificConfigReader configUtils;

  private InstructionSearchRequest instructionSearchRequest;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);

    instructionSearchRequest = new InstructionSearchRequest();
    instructionSearchRequest.setDeliveryNumber(Long.valueOf("21119003"));
  }

  @AfterMethod
  public void restRestUtilCalls() {
    reset(deliveryStatusPublisher);
  }

  @Test
  public void testGetInstructionSummaryWhenDeliveryIsArrivedAndFeatureFlagIsFalse() {
    instructionSearchRequest.setDeliveryStatus(DeliveryStatus.ARV.toString());

    when(configUtils.isFeatureFlagEnabled(
            ReceivingConstants.IS_DELIVERY_STATUS_PUBLISH_IN_INSTRUCTION_SEARCH_DISABLED))
        .thenReturn(false);

    when(deliveryStatusPublisher.publishDeliveryStatus(
            21119003L, DeliveryStatus.OPEN.toString(), null, new HashMap<>()))
        .thenReturn(null);

    List<InstructionSummary> response =
        wfsInstructionSearchRequestHandler.getInstructionSummary(
            instructionSearchRequest, new HashMap<>());

    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(21119003L, DeliveryStatus.OPEN.toString(), null, new HashMap<>());
  }

  @Test
  public void testGetInstructionSummaryWhenDeliveryIsArrivedAndFeatureFlagIsTrue() {
    instructionSearchRequest.setDeliveryStatus(DeliveryStatus.ARV.toString());

    when(configUtils.isFeatureFlagEnabled(
            ReceivingConstants.IS_DELIVERY_STATUS_PUBLISH_IN_INSTRUCTION_SEARCH_DISABLED))
        .thenReturn(true);

    when(deliveryStatusPublisher.publishDeliveryStatus(
            21119003L, DeliveryStatus.OPEN.toString(), null, new HashMap<>()))
        .thenReturn(null);

    List<InstructionSummary> response =
        wfsInstructionSearchRequestHandler.getInstructionSummary(
            instructionSearchRequest, new HashMap<>());

    verify(deliveryStatusPublisher, times(0))
        .publishDeliveryStatus(21119003L, DeliveryStatus.OPEN.toString(), null, new HashMap<>());
  }

  @Test
  public void testGetInstructionSummaryWhenDeliveryIsOpen() {
    instructionSearchRequest.setDeliveryStatus(DeliveryStatus.OPN.toString());
    instructionSearchRequest.setProblemTagId(null);

    List<InstructionSummary> response =
        wfsInstructionSearchRequestHandler.getInstructionSummary(
            instructionSearchRequest, new HashMap<>());

    verify(deliveryStatusPublisher, times(0))
        .publishDeliveryStatus(21119003L, DeliveryStatus.OPEN.toString(), null, new HashMap<>());
  }
}
