package com.walmart.move.nim.receiving.rx.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.client.slotting.SlottingRestApiClient;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.OutboxConfig;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.CompleteInstructionRequest;
import com.walmart.move.nim.receiving.core.model.SlotDetails;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingDivertLocations;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletRequest;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponse;
import com.walmart.move.nim.receiving.data.MockRxHttpHeaders;
import com.walmart.move.nim.receiving.rx.mock.MockInstruction;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.walmart.platform.service.OutboxEventSinkService;
import org.apache.commons.lang3.StringUtils;
import org.mockito.*;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RxSlottingServiceImplTest {
  @Mock SlottingRestApiClient slottingRestApiClient;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private OutboxConfig outboxConfig;
  @Mock private OutboxEventSinkService outboxEventSinkService;
  @InjectMocks RxSlottingServiceImpl rxSlottingServiceImpl;
  private Gson gson = new GsonBuilder().create();

  @BeforeMethod
  public void createRxSlottingServiceImpl() throws Exception {
    MockitoAnnotations.initMocks(this);

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32709);
    ReflectionTestUtils.setField(rxSlottingServiceImpl, "gson", gson);
  }

  @Test
  public void test_acquireSlot_smartSlotting() {

    HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();

    SlottingDivertLocations location = new SlottingDivertLocations();
    location.setType("success");
    location.setLocation("A1234");
    location.setItemNbr(123456);
    List<SlottingDivertLocations> locationList = new ArrayList();
    locationList.add(location);

    SlottingPalletResponse mockSlottingResponseBody = new SlottingPalletResponse();
    mockSlottingResponseBody.setMessageId("a1-b1-c1");
    mockSlottingResponseBody.setLocations(locationList);

    ArgumentCaptor<SlottingPalletRequest> captor =
        ArgumentCaptor.forClass(SlottingPalletRequest.class);

    doReturn(mockSlottingResponseBody)
        .when(slottingRestApiClient)
        .getSlot(captor.capture(), any(HttpHeaders.class));

    SlotDetails mockSlotDetails = new SlotDetails();
    CompleteInstructionRequest completeInstructionRequest = new CompleteInstructionRequest();
    completeInstructionRequest.setSlotDetails(mockSlotDetails);
    completeInstructionRequest.setPartialContainer(true);

    SlottingPalletResponse acquiredSlot =
        rxSlottingServiceImpl.acquireSlot(
            "ab-cd-ef-fh",
            Arrays.asList(Long.valueOf(12345)),
            0,
            ReceivingConstants.SLOTTING_FIND_SLOT,
            httpHeaders);

    assertNotNull(acquiredSlot);
    assertTrue(StringUtils.isNotBlank(acquiredSlot.getMessageId()));
    assertEquals(acquiredSlot.getLocations().get(0).getLocation(), "A1234");
  }

  @Test
  public void test_acquireSlot_smartSlottingWithLabel() {

    HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();

    SlottingDivertLocations location = new SlottingDivertLocations();
    location.setType("success");
    location.setLocation("A1234");
    location.setItemNbr(123456);
    List<SlottingDivertLocations> locationList = new ArrayList();
    locationList.add(location);

    SlottingPalletResponse mockSlottingResponseBody = new SlottingPalletResponse();
    mockSlottingResponseBody.setMessageId("a1-b1-c1");
    mockSlottingResponseBody.setLocations(locationList);

    ArgumentCaptor<SlottingPalletRequest> captor =
        ArgumentCaptor.forClass(SlottingPalletRequest.class);

    doReturn(mockSlottingResponseBody)
        .when(slottingRestApiClient)
        .getSlot(captor.capture(), any(HttpHeaders.class));

    SlotDetails mockSlotDetails = new SlotDetails();
    CompleteInstructionRequest completeInstructionRequest = new CompleteInstructionRequest();
    completeInstructionRequest.setSlotDetails(mockSlotDetails);
    completeInstructionRequest.setPartialContainer(true);

    SlottingPalletResponse acquiredSlot =
        rxSlottingServiceImpl.acquireSlot(
            "ab-cd-ef-fh",
            Arrays.asList(Long.valueOf(12345)),
            0,
            ReceivingConstants.SLOTTING_FIND_SLOT,
            "B1834989493901",
            4,
            "EA",
            "10067678899005",
            null,
            "807",
            httpHeaders);

    assertNotNull(acquiredSlot);
    assertTrue(StringUtils.isNotBlank(acquiredSlot.getMessageId()));
    assertEquals(acquiredSlot.getLocations().get(0).getLocation(), "A1234");
  }

  @Test
  public void test_acquireSlot_smartSlottingWithNoLabel() {

    HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();

    SlottingDivertLocations location = new SlottingDivertLocations();
    location.setType("success");
    location.setLocation("A1234");
    location.setItemNbr(123456);
    List<SlottingDivertLocations> locationList = new ArrayList();
    locationList.add(location);

    SlottingPalletResponse mockSlottingResponseBody = new SlottingPalletResponse();
    mockSlottingResponseBody.setMessageId("a1-b1-c1");
    mockSlottingResponseBody.setLocations(locationList);

    ArgumentCaptor<SlottingPalletRequest> captor =
        ArgumentCaptor.forClass(SlottingPalletRequest.class);

    doReturn(mockSlottingResponseBody)
        .when(slottingRestApiClient)
        .getSlot(captor.capture(), any(HttpHeaders.class));

    SlotDetails mockSlotDetails = new SlotDetails();
    CompleteInstructionRequest completeInstructionRequest = new CompleteInstructionRequest();
    completeInstructionRequest.setSlotDetails(mockSlotDetails);
    completeInstructionRequest.setPartialContainer(true);

    SlottingPalletResponse acquiredSlot =
        rxSlottingServiceImpl.acquireSlot(
            "ab-cd-ef-fh",
            Arrays.asList(Long.valueOf(12345)),
            0,
            ReceivingConstants.SLOTTING_FIND_SLOT,
            "B1834989493901",
            null,
            null,
            null,
            null,
            null,
            httpHeaders);

    assertNotNull(acquiredSlot);
    assertTrue(StringUtils.isNotBlank(acquiredSlot.getMessageId()));
    assertEquals(acquiredSlot.getLocations().get(0).getLocation(), "A1234");
  }

  @Test
  public void test_acquireSlot_smartSlotting_withSlotSize() {

    HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();

    SlottingDivertLocations location = new SlottingDivertLocations();
    location.setType("success");
    location.setLocation("A1234");
    location.setItemNbr(123456);
    List<SlottingDivertLocations> locationList = new ArrayList();
    locationList.add(location);

    SlottingPalletResponse mockSlottingResponseBody = new SlottingPalletResponse();
    mockSlottingResponseBody.setMessageId("a1-b1-c1");
    mockSlottingResponseBody.setLocations(locationList);

    ArgumentCaptor<SlottingPalletRequest> captor =
        ArgumentCaptor.forClass(SlottingPalletRequest.class);

    doReturn(mockSlottingResponseBody)
        .when(slottingRestApiClient)
        .getSlot(captor.capture(), any(HttpHeaders.class));

    SlotDetails mockSlotDetails = new SlotDetails();
    CompleteInstructionRequest completeInstructionRequest = new CompleteInstructionRequest();
    completeInstructionRequest.setSlotDetails(mockSlotDetails);
    completeInstructionRequest.setPartialContainer(true);

    SlottingPalletResponse acquiredSlot =
        rxSlottingServiceImpl.acquireSlot(
            "ab-cd-ef-gh",
            Arrays.asList(Long.valueOf(12345)),
            72,
            ReceivingConstants.SLOTTING_FIND_SLOT,
            httpHeaders);

    assertNotNull(acquiredSlot);
    assertTrue(StringUtils.isNotBlank(acquiredSlot.getMessageId()));
    assertEquals(acquiredSlot.getLocations().get(0).getLocation(), "A1234");
  }

  @Test
  public void test_freeSlot() {

    doReturn(true).when(slottingRestApiClient).freeSlot(any(), any(), any(HttpHeaders.class));

    rxSlottingServiceImpl.freeSlot(1234l, "A123", MockRxHttpHeaders.getHeaders());

    verify(slottingRestApiClient, times(1)).freeSlot(any(), any(), any(HttpHeaders.class));
  }

  @Test
  public void test_freeSlot_exception() {

    RuntimeException mockException = new RuntimeException();
    doThrow(mockException)
        .when(slottingRestApiClient)
        .freeSlot(any(), any(), any(HttpHeaders.class));

    rxSlottingServiceImpl.freeSlot(1234l, "A123", MockRxHttpHeaders.getHeaders());

    verify(slottingRestApiClient, times(1)).freeSlot(any(), any(), any(HttpHeaders.class));
  }

  @Test
  public void test_acquireSlotMultiPallets_smartSlottingWithLabel() {

    List<String> lpns = getMockLPNs();
    Instruction instruction1 = getMockInstruction(5465465);
    Instruction instruction2 = getMockInstruction(5465466);
    ;

    HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();

    SlottingDivertLocations location = new SlottingDivertLocations();
    location.setType("success");
    location.setLocation("A1234");
    location.setItemNbr(123456);
    List<SlottingDivertLocations> locationList = new ArrayList();
    locationList.add(location);

    SlottingPalletResponse mockSlottingResponseBody = new SlottingPalletResponse();
    mockSlottingResponseBody.setMessageId("a1-b1-c1");
    mockSlottingResponseBody.setLocations(locationList);

    ArgumentCaptor<SlottingPalletRequest> captor =
        ArgumentCaptor.forClass(SlottingPalletRequest.class);

    doReturn(mockSlottingResponseBody)
        .when(slottingRestApiClient)
        .getSlot(captor.capture(), any(HttpHeaders.class));

    SlotDetails mockSlotDetails = new SlotDetails();
    CompleteInstructionRequest completeInstructionRequest = new CompleteInstructionRequest();
    completeInstructionRequest.setSlotDetails(mockSlotDetails);
    completeInstructionRequest.setPartialContainer(true);

    SlottingPalletResponse acquiredSlot =
        rxSlottingServiceImpl.acquireSlotMultiPallets(
            "ab-cd-ef-fh",
            0,
            lpns,
            Arrays.asList(instruction1, instruction2),
            "A1234",
            httpHeaders);

    assertNotNull(acquiredSlot);
    assertTrue(StringUtils.isNotBlank(acquiredSlot.getMessageId()));
    assertEquals(acquiredSlot.getLocations().get(0).getLocation(), "A1234");
  }

  @Test
  public void test_acquireSlotMultiPallets_smartSlottingWithLabel_Size() {

    List<String> lpns = getMockLPNs();
    Instruction instruction1 = getMockInstruction(5465465);
    Instruction instruction2 = getMockInstruction(5465466);
    ;

    HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();

    SlottingDivertLocations location = new SlottingDivertLocations();
    location.setType("success");
    location.setLocation("A1234");
    location.setItemNbr(123456);
    List<SlottingDivertLocations> locationList = new ArrayList();
    locationList.add(location);

    SlottingPalletResponse mockSlottingResponseBody = new SlottingPalletResponse();
    mockSlottingResponseBody.setMessageId("a1-b1-c1");
    mockSlottingResponseBody.setLocations(locationList);

    ArgumentCaptor<SlottingPalletRequest> captor =
        ArgumentCaptor.forClass(SlottingPalletRequest.class);

    doReturn(mockSlottingResponseBody)
        .when(slottingRestApiClient)
        .getSlot(captor.capture(), any(HttpHeaders.class));

    SlotDetails mockSlotDetails = new SlotDetails();
    CompleteInstructionRequest completeInstructionRequest = new CompleteInstructionRequest();
    completeInstructionRequest.setSlotDetails(mockSlotDetails);
    completeInstructionRequest.setPartialContainer(true);

    SlottingPalletResponse acquiredSlot =
        rxSlottingServiceImpl.acquireSlotMultiPallets(
            "ab-cd-ef-fh",
            72,
            lpns,
            Arrays.asList(instruction1, instruction2),
            "A1234",
            httpHeaders);

    assertNotNull(acquiredSlot);
    assertTrue(StringUtils.isNotBlank(acquiredSlot.getMessageId()));
    assertEquals(acquiredSlot.getLocations().get(0).getLocation(), "A1234");
  }

  private List<String> getMockLPNs() {
    List<String> lpns =
        Arrays.asList(
            "a47263319186056300", "a47263319186056301", "a47263319186056302", "a47263319186056303");
    return lpns;
  }

  private Instruction getMockInstruction(long instructionId) {
    Instruction mockNewInstruction = MockInstruction.getMockNewInstruction();
    mockNewInstruction.setId(instructionId);
    mockNewInstruction.setPurchaseReferenceNumber("3515421377");
    mockNewInstruction.setPurchaseReferenceLineNumber(Long.valueOf(instructionId).intValue());
    mockNewInstruction.setReceivedQuantity(1);
    mockNewInstruction.setReceivedQuantityUOM("ZA");
    return mockNewInstruction;
  }

  @Test
  public void acquireSlot_outboxCreateMoves() {
    // given
    HttpHeaders httpHeaders = MockRxHttpHeaders.getHeaders();
    httpHeaders.set(ReceivingConstants.MOVE_TO_LOCATION, "A1234");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean())).thenReturn(true);

    SlottingDivertLocations location = new SlottingDivertLocations();
    location.setType("success");
    location.setLocation("A1234");
    location.setItemNbr(123456);
    List<SlottingDivertLocations> locationList = new ArrayList<>();
    locationList.add(location);

    SlottingPalletResponse mockSlottingResponseBody = new SlottingPalletResponse();
    mockSlottingResponseBody.setMessageId("a1-b1-c1");
    mockSlottingResponseBody.setLocations(locationList);

    ArgumentCaptor<SlottingPalletRequest> captor =
            ArgumentCaptor.forClass(SlottingPalletRequest.class);

    doReturn(mockSlottingResponseBody)
            .when(slottingRestApiClient)
            .getSlot(captor.capture(), any(HttpHeaders.class));

    // when
    SlottingPalletResponse acquiredSlot =
            rxSlottingServiceImpl.acquireSlot(
                    "ab-cd-ef-fh",
                    Collections.singletonList(12345L),
                    0,
                    ReceivingConstants.SLOTTING_FIND_SLOT,
                    httpHeaders);

    // then
    assertNull(acquiredSlot);
    verify(outboxEventSinkService, times(1)).saveEvent(any());
  }
}
