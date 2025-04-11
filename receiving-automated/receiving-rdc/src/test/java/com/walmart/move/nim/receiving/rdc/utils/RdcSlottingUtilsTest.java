package com.walmart.move.nim.receiving.rdc.utils;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.walmart.move.nim.receiving.core.client.nimrds.model.ContainerOrder;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceiveContainersRequestBody;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.ItemData;
import com.walmart.move.nim.receiving.core.model.ReceiveInstructionRequest;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponse;
import com.walmart.move.nim.receiving.core.service.SlottingServiceImpl;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.mock.data.MockRdcInstruction;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RdcSlottingUtilsTest {

  @InjectMocks private RdcSlottingUtils rdcSlottingUtils;
  @Mock private SlottingServiceImpl slottingService;
  private HttpHeaders httpHeaders;
  private static final String facilityNum = "32818";
  private static final String countryCode = "US";

  @BeforeMethod
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    TenantContext.setFacilityCountryCode(countryCode);
    httpHeaders = MockHttpHeaders.getHeaders(facilityNum, countryCode);
  }

  @AfterMethod
  public void tearDown() {
    reset(slottingService);
  }

  @Test
  public void testReceiveContainers_Success() {
    String labelTrackingId = "a60203423232323213242";
    ReceiveContainersRequestBody receiveContainersRequestBody = new ReceiveContainersRequestBody();
    List<ContainerOrder> containerOrders = new ArrayList<>();
    ContainerOrder containerOrder = new ContainerOrder();
    containerOrder.setPoNumber("80702323");
    containerOrder.setPoLine(1);
    containerOrder.setQty(1);
    containerOrders.add(containerOrder);
    receiveContainersRequestBody.setContainerOrders(containerOrders);
    when(slottingService.receivePallet(
            any(ReceiveInstructionRequest.class),
            anyString(),
            any(HttpHeaders.class),
            any(ReceiveContainersRequestBody.class)))
        .thenReturn(MockRdcInstruction.getAutoSlotFromSlotting());
    SlottingPalletResponse response =
        rdcSlottingUtils.receiveContainers(
            getMockReceiveInstructionRequest(),
            labelTrackingId,
            MockHttpHeaders.getHeaders(),
            receiveContainersRequestBody);

    assertNotNull(response);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testReceiveContainers_Exception() {
    String labelTrackingId = "a60203423232323213242";
    ReceiveContainersRequestBody receiveContainersRequestBody = new ReceiveContainersRequestBody();
    List<ContainerOrder> containerOrders = new ArrayList<>();
    ContainerOrder containerOrder = new ContainerOrder();
    containerOrder.setPoNumber("80702323");
    containerOrder.setPoLine(1);
    containerOrder.setQty(1);
    containerOrders.add(containerOrder);
    receiveContainersRequestBody.setContainerOrders(containerOrders);
    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.SMART_SLOT_NOT_FOUND,
                String.format(
                    ReceivingConstants.SMART_SLOTTING_RESPONSE_ERROR_CODE_AND_MSG,
                    ExceptionCodes.PRIME_SLOT_NOT_FOUND_ERROR_IN_SMART_SLOTTING,
                    "Invalid Slot ID"),
                ExceptionCodes.PRIME_SLOT_NOT_FOUND_ERROR_IN_SMART_SLOTTING,
                "Invalid Slot ID"))
        .when(slottingService)
        .receivePallet(
            any(ReceiveInstructionRequest.class),
            anyString(),
            any(HttpHeaders.class),
            any(ReceiveContainersRequestBody.class));

    rdcSlottingUtils.receiveContainers(
        getMockReceiveInstructionRequest(),
        labelTrackingId,
        MockHttpHeaders.getHeaders(),
        receiveContainersRequestBody);
  }

  private ReceiveInstructionRequest getMockReceiveInstructionRequest() {
    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setQuantity(1);
    receiveInstructionRequest.setDeliveryNumber(23232323L);
    receiveInstructionRequest.setDoorNumber("133");
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setAdditionalInfo(new ItemData());
    receiveInstructionRequest.setDeliveryDocumentLines(
        Collections.singletonList(deliveryDocumentLine));
    return receiveInstructionRequest;
  }
}
