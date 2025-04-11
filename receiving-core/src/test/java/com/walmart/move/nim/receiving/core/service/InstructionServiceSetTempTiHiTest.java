package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.DEFAULT_OPEN_QTY_CALCULATOR;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.OPEN_QTY_CALCULATOR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.DeliveryItemOverride;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.mock.data.MockInstruction;
import com.walmart.move.nim.receiving.core.mock.data.MockTempTiHi;
import com.walmart.move.nim.receiving.core.model.FdeCreateContainerResponse;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.OpenQtyResult;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.Optional;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class InstructionServiceSetTempTiHiTest extends ReceivingTestBase {
  @InjectMocks private InstructionService instructionService;
  @Mock private FdeService fdeService;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private JmsPublisher jmsPublisher;
  @Mock private InstructionPersisterService instructionPersisterService;
  @Mock private InstructionHelperService instructionHelperService;
  @Mock private DeliveryItemOverrideService deliveryItemOverrideService;
  @Mock private DefaultOpenQtyCalculator defaultOpenQtyCalculator;

  private Gson gson = new Gson();
  private HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
  private InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
  private FdeCreateContainerResponse fdeCreateContainerResponse =
      MockInstruction.getFdeCreateContainerResponse();
  private Instruction pendingInstruction = MockInstruction.getPendingInstruction();
  private DeliveryItemOverride deliveryItemOverride = MockTempTiHi.getDeliveryItemOverride();

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32612);
    ReflectionTestUtils.setField(instructionService, "tenantSpecificConfigReader", configUtils);
  }

  @BeforeClass
  public void setReflectionTestUtil() {
    ReflectionTestUtils.setField(instructionService, "gson", gson);
    when(configUtils.getConfiguredInstance(
            anyString(),
            eq(OPEN_QTY_CALCULATOR),
            eq(DEFAULT_OPEN_QTY_CALCULATOR),
            eq(OpenQtyCalculator.class)))
        .thenReturn(defaultOpenQtyCalculator);
  }

  @AfterMethod
  public void tearDown() {
    reset(fdeService);
    reset(configUtils);
    reset(jmsPublisher);
    reset(deliveryItemOverrideService);
    reset(instructionPersisterService);
  }

  @Test
  public void testCreateInstructionForUpcReceiving() throws ReceivingException {
    doReturn(true).when(configUtils).isDeliveryItemOverrideEnabled(32612);
    doReturn(Optional.of(deliveryItemOverride))
        .when(deliveryItemOverrideService)
        .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
    doReturn(new Pair<>(10, 0L))
        .when(instructionHelperService)
        .getReceivedQtyDetailsAndValidate(eq(null), any(), any(), eq(false), eq(false));

    doReturn(OpenQtyResult.builder().openQty(10L).maxReceiveQty(10L).totalReceivedQty(0).build())
        .when(defaultOpenQtyCalculator)
        .calculate(anyLong(), any(), any());

    doReturn(gson.toJson(fdeCreateContainerResponse)).when(fdeService).receive(any(), any());
    doReturn(pendingInstruction).when(instructionPersisterService).saveInstruction(any());
    doNothing()
        .when(jmsPublisher)
        .publish(anyString(), any(ReceivingJMSEvent.class), any(Boolean.class));

    try {
      instructionService.createInstructionForUpcReceiving(instructionRequest, httpHeaders);
      assert (true);
      verify(deliveryItemOverrideService, times(1))
          .findByDeliveryNumberAndItemNumber(anyLong(), anyLong());
      verify(fdeService, times(1)).receive(any(), any());
    } catch (ReceivingException receivingException) {
      assert (false);
    }
  }
}
