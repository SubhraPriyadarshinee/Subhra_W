package com.walmart.move.nim.receiving.rx.service;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.client.epcis.EpcisRequest;
import com.walmart.move.nim.receiving.core.client.nimrds.model.Destination;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceiveContainersResponseBody;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceivedContainer;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingDivertLocations;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponse;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.data.MockRxHttpHeaders;
import com.walmart.move.nim.receiving.rx.builders.RxContainerLabelBuilder;
import com.walmart.move.nim.receiving.rx.common.RxLpnUtils;
import com.walmart.move.nim.receiving.rx.config.RxManagedConfig;
import com.walmart.move.nim.receiving.rx.mock.MockInstruction;
import com.walmart.move.nim.receiving.rx.mock.RxMockContainer;
import com.walmart.move.nim.receiving.rx.validators.RxInstructionValidator;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RxCompleteMultipleInstructionRequestHandlerTest {

  @Mock private InstructionPersisterService instructionPersisterService;
  @Mock private EpcisService epcisService;
  @Mock private RxContainerLabelBuilder containerLabelBuilder;
  @Mock private NimRdsServiceImpl nimRdsServiceImpl;
  @Mock private ContainerService containerService;
  @Mock private RxSlottingServiceImpl rxSlottingServiceImpl;
  @Mock private RxInstructionHelperService rxInstructionHelperService;
  @Mock private RxInstructionValidator rxInstructionValidator;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private RxManagedConfig rxManagedConfig;
  @Mock private RxCompleteInstructionOutboxHandler rxCompleteInstructionOutboxHandler;
  @Mock private RxInstructionService rxInstructionService;
  @Mock private RxLpnUtils rxLpnUtils;

  @InjectMocks
  private RxCompleteMultipleInstructionRequestHandler rxCompleteMultipleInstructionRequestHandler;

  @BeforeMethod
  public void setUp() {

    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(
        containerLabelBuilder, "tenantSpecificConfigReader", tenantSpecificConfigReader);
    ReflectionTestUtils.setField(rxCompleteMultipleInstructionRequestHandler, "gson", new Gson());
    TenantContext.setFacilityNum(32897);
    TenantContext.setFacilityCountryCode("US");
  }

  @AfterMethod
  public void afterMethod() {
    reset(instructionPersisterService);
    reset(tenantSpecificConfigReader);
    reset(epcisService);
    reset(containerService);
    reset(nimRdsServiceImpl);
    reset(rxSlottingServiceImpl);
    reset(rxInstructionHelperService);
    reset(rxManagedConfig);
    reset(rxLpnUtils);
  }

  @Test
  public void test_complete() throws ReceivingException {
    doReturn(false).when(rxManagedConfig).isRollbackNimRdsReceiptsEnabled();
    doAnswer(
            (Answer<Instruction>)
                invocation -> {
                  Long deliveryNumber = (Long) invocation.getArguments()[0];
                  return getMockInstruction(deliveryNumber);
                })
        .when(instructionPersisterService)
        .getInstructionById(anyLong());
    when(epcisService.epcisCapturePayload(any(), any(), any()))
        .thenReturn(Arrays.<EpcisRequest>asList());
    when(containerLabelBuilder.generateContainerLabel(any(), any(), any(), any(), any()))
        .thenCallRealMethod();
    when(nimRdsServiceImpl.acquireSlotForSplitPallet(any(), any(), any()))
        .thenReturn(getMockReceiveContainersResponseBody());
    doAnswer(
            (Answer<Container>)
                invocation -> {
                  String trackingId = (String) invocation.getArguments()[0];
                  return getMockContainer(trackingId);
                })
        .when(containerService)
        .getContainerWithChildsByTrackingId(anyString(), anyBoolean());
    when(rxSlottingServiceImpl.acquireSlot(anyString(), any(), anyInt(), anyString(), any()))
        .thenReturn(getMockSlottingPalletResponse());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(anyString(), anyString()))
        .thenReturn(true);

    BulkCompleteInstructionRequest mockBulkCompleteInstructionRequest =
        new BulkCompleteInstructionRequest();
    CompleteMultipleInstructionData mockCompleteMultipleInstructionData1 =
        new CompleteMultipleInstructionData();
    mockCompleteMultipleInstructionData1.setInstructionId(1l);
    CompleteMultipleInstructionData mockCompleteMultipleInstructionData2 =
        new CompleteMultipleInstructionData();
    mockCompleteMultipleInstructionData2.setInstructionId(2l);
    mockBulkCompleteInstructionRequest.setInstructionData(
        Arrays.asList(mockCompleteMultipleInstructionData1, mockCompleteMultipleInstructionData2));

    CompleteMultipleInstructionResponse result =
        rxCompleteMultipleInstructionRequestHandler.complete(
            mockBulkCompleteInstructionRequest, MockRxHttpHeaders.getHeaders());

    assertNotNull(result);

    verify(instructionPersisterService, times(2)).getInstructionById(anyLong());
    verify(epcisService, times(2)).epcisCapturePayload(any(), any(), any());
    verify(containerLabelBuilder, times(2))
        .generateContainerLabel(any(), any(), any(), any(), any());
    verify(nimRdsServiceImpl, times(1)).acquireSlotForSplitPallet(any(), any(), any());
    verify(containerService, times(2))
        .getContainerWithChildsByTrackingId(anyString(), anyBoolean());
    verify(rxSlottingServiceImpl, times(1))
        .acquireSlot(anyString(), any(), anyInt(), anyString(), any());
    verify(tenantSpecificConfigReader, times(4)).getConfiguredFeatureFlag(anyString(), anyString());

    Map<String, Object> printJob = result.getPrintJob();
    List<LinkedTreeMap> printRequests = (List<LinkedTreeMap>) printJob.get("printRequests");
    assertEquals(2, printRequests.size());
    assertTrue(
        Arrays.asList("97123456", "97123457")
            .contains(printRequests.get(0).get("labelIdentifier").toString()));
    assertTrue(
        Arrays.asList("97123456", "97123457")
            .contains(printRequests.get(1).get("labelIdentifier").toString()));
  }

  @Test
  public void test_complete_RDS_OneAtlas_autoSlot() throws ReceivingException {
    doReturn(false).when(rxManagedConfig).isRollbackNimRdsReceiptsEnabled();
    doAnswer(
            (Answer<Instruction>)
                invocation -> {
                  Long deliveryNumber = (Long) invocation.getArguments()[0];
                  return getMockInstruction(deliveryNumber);
                })
        .when(instructionPersisterService)
        .getInstructionById(anyLong());
    when(epcisService.epcisCapturePayload(any(), any(), any()))
        .thenReturn(Arrays.<EpcisRequest>asList());
    when(containerLabelBuilder.generateContainerLabel(any(), any(), any(), any(), any()))
        .thenCallRealMethod();
    when(nimRdsServiceImpl.acquireSlotForSplitPallet(any(), any(), any()))
        .thenReturn(getMockReceiveContainersResponseBody());
    doAnswer(
            (Answer<Container>)
                invocation -> {
                  String trackingId = (String) invocation.getArguments()[0];
                  return getMockContainer(trackingId);
                })
        .when(containerService)
        .getContainerWithChildsByTrackingId(anyString(), anyBoolean());
    when(rxSlottingServiceImpl.acquireSlot(anyString(), any(), anyInt(), anyString(), any()))
        .thenReturn(getMockSlottingPalletResponse());
    when(rxSlottingServiceImpl.acquireSlotMultiPallets(
            anyString(), anyInt(), any(), any(), eq("M111"), any(HttpHeaders.class)))
        .thenReturn(getMockSlottingPalletResponse());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(anyString(), anyString()))
        .thenReturn(true);

    BulkCompleteInstructionRequest mockBulkCompleteInstructionRequest =
        new BulkCompleteInstructionRequest();
    CompleteMultipleInstructionData mockCompleteMultipleInstructionData1 =
        new CompleteMultipleInstructionData();
    mockCompleteMultipleInstructionData1.setInstructionId(1l);
    CompleteMultipleInstructionData mockCompleteMultipleInstructionData2 =
        new CompleteMultipleInstructionData();
    mockCompleteMultipleInstructionData2.setInstructionId(2l);
    mockBulkCompleteInstructionRequest.setInstructionData(
        Arrays.asList(mockCompleteMultipleInstructionData1, mockCompleteMultipleInstructionData2));

    CompleteMultipleInstructionResponse result =
        rxCompleteMultipleInstructionRequestHandler.complete(
            mockBulkCompleteInstructionRequest, MockRxHttpHeaders.getHeaders());

    assertNotNull(result);

    verify(instructionPersisterService, times(2)).getInstructionById(anyLong());
    verify(epcisService, times(2)).epcisCapturePayload(any(), any(), any());
    verify(containerLabelBuilder, times(2))
        .generateContainerLabel(any(), any(), any(), any(), any());
    verify(nimRdsServiceImpl, times(1)).acquireSlotForSplitPallet(any(), any(), any());
    verify(containerService, times(2))
        .getContainerWithChildsByTrackingId(anyString(), anyBoolean());
    verify(rxSlottingServiceImpl, times(1))
        .acquireSlot(anyString(), any(), anyInt(), anyString(), any());
    verify(tenantSpecificConfigReader, times(4)).getConfiguredFeatureFlag(anyString(), anyString());

    Map<String, Object> printJob = result.getPrintJob();
    List<LinkedTreeMap> printRequests = (List<LinkedTreeMap>) printJob.get("printRequests");
    assertEquals(2, printRequests.size());
    assertTrue(
        Arrays.asList("97123456", "97123457")
            .contains(printRequests.get(0).get("labelIdentifier").toString()));
    assertTrue(
        Arrays.asList("97123456", "97123457")
            .contains(printRequests.get(1).get("labelIdentifier").toString()));
  }

  @Test
  public void test_complete_RDS_OneAtlas_manualSlot() throws ReceivingException {
    doReturn(false).when(rxManagedConfig).isRollbackNimRdsReceiptsEnabled();
    doAnswer(
            (Answer<Instruction>)
                invocation -> {
                  Long deliveryNumber = (Long) invocation.getArguments()[0];
                  return getMockInstruction(deliveryNumber);
                })
        .when(instructionPersisterService)
        .getInstructionById(anyLong());
    when(epcisService.epcisCapturePayload(any(), any(), any()))
        .thenReturn(Arrays.<EpcisRequest>asList());
    when(containerLabelBuilder.generateContainerLabel(any(), any(), any(), any(), any()))
        .thenCallRealMethod();
    when(nimRdsServiceImpl.acquireSlotForSplitPallet(any(), any(), any()))
        .thenReturn(getMockReceiveContainersResponseBody());
    doAnswer(
            (Answer<Container>)
                invocation -> {
                  String trackingId = (String) invocation.getArguments()[0];
                  return getMockContainer(trackingId);
                })
        .when(containerService)
        .getContainerWithChildsByTrackingId(anyString(), anyBoolean());
    String manualSlot = "M112";
    when(rxLpnUtils.get18DigitLPNs(anyInt(), any(HttpHeaders.class))).thenReturn(getMockLPNs());

    doAnswer(
            (Answer<SlottingPalletResponse>)
                invocation -> {
                  List<String> lpns = (List<String>) invocation.getArguments()[2];

                  return lpns.get(0).equals("a47263319186056300")
                      ? getMockSlottingPalletResponse()
                      : null;
                })
        .when(rxSlottingServiceImpl)
        .acquireSlotMultiPallets(
            anyString(), anyInt(), any(), any(), eq(manualSlot), any(HttpHeaders.class));

    doAnswer(
            (Answer<Boolean>)
                invocation -> {
                  String flag = (String) invocation.getArguments()[1];
                  return !ReceivingConstants.IS_DC_RDS_RECEIPT_ENABLED.equals(flag);
                })
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());

    BulkCompleteInstructionRequest mockBulkCompleteInstructionRequest =
        new BulkCompleteInstructionRequest();
    CompleteMultipleInstructionData mockCompleteMultipleInstructionData1 =
        new CompleteMultipleInstructionData();
    mockCompleteMultipleInstructionData1.setInstructionId(1l);
    SlotDetails slotDetails = new SlotDetails();
    slotDetails.setSlot(manualSlot);
    mockCompleteMultipleInstructionData1.setSlotDetails(slotDetails);
    CompleteMultipleInstructionData mockCompleteMultipleInstructionData2 =
        new CompleteMultipleInstructionData();
    mockCompleteMultipleInstructionData2.setInstructionId(2l);
    mockBulkCompleteInstructionRequest.setInstructionData(
        Arrays.asList(mockCompleteMultipleInstructionData1, mockCompleteMultipleInstructionData2));

    CompleteMultipleInstructionResponse result =
        rxCompleteMultipleInstructionRequestHandler.complete(
            mockBulkCompleteInstructionRequest, MockRxHttpHeaders.getHeaders());

    assertNotNull(result);

    verify(instructionPersisterService, times(2)).getInstructionById(anyLong());
    verify(epcisService, times(2)).epcisCapturePayload(any(), any(), any());
    verify(containerLabelBuilder, times(2))
        .generateContainerLabel(any(), any(), any(), any(), any());
    verify(nimRdsServiceImpl, times(0)).acquireSlotForSplitPallet(any(), any(), any());
    verify(containerService, times(2))
        .getContainerWithChildsByTrackingId(anyString(), anyBoolean());
    verify(rxSlottingServiceImpl, times(0))
        .acquireSlot(anyString(), any(), anyInt(), anyString(), any());
    verify(tenantSpecificConfigReader, times(2)).getConfiguredFeatureFlag(anyString(), anyString());

    Map<String, Object> printJob = result.getPrintJob();
    List<LinkedTreeMap> printRequests = (List<LinkedTreeMap>) printJob.get("printRequests");
    assertEquals(2, printRequests.size());
    assertTrue(
        Arrays.asList("a47263319186056300", "a47263319186056301")
            .contains(printRequests.get(0).get("labelIdentifier").toString()));
    assertTrue(
        Arrays.asList("a47263319186056300", "a47263319186056301")
            .contains(printRequests.get(1).get("labelIdentifier").toString()));
  }

  @Test
  public void test_complete_rollback() throws ReceivingException {
    try {
      doReturn(true).when(rxManagedConfig).isRollbackNimRdsReceiptsEnabled();
      doAnswer(
              (Answer<Instruction>)
                  invocation -> {
                    Long deliveryNumber = (Long) invocation.getArguments()[0];
                    return getMockInstruction(deliveryNumber);
                  })
          .when(instructionPersisterService)
          .getInstructionById(anyLong());
      when(epcisService.epcisCapturePayload(any(), any(), any()))
          .thenReturn(Arrays.<EpcisRequest>asList());
      when(containerLabelBuilder.generateContainerLabel(any(), any(), any(), any(), any()))
          .thenCallRealMethod();
      when(nimRdsServiceImpl.acquireSlotForSplitPallet(any(), any(), any()))
          .thenReturn(getMockReceiveContainersResponseBody());
      doNothing()
          .when(nimRdsServiceImpl)
          .quantityChange(anyInt(), anyString(), any(HttpHeaders.class));
      doAnswer(
              (Answer<Container>)
                  invocation -> {
                    String trackingId = (String) invocation.getArguments()[0];
                    return getMockContainer(trackingId);
                  })
          .when(containerService)
          .getContainerWithChildsByTrackingId(anyString(), anyBoolean());
      when(rxSlottingServiceImpl.acquireSlot(anyString(), any(), anyInt(), anyString(), any()))
          .thenReturn(getMockSlottingPalletResponse());
      when(tenantSpecificConfigReader.getConfiguredFeatureFlag(anyString(), anyString()))
          .thenReturn(true);
      doThrow(new RuntimeException("FAKE_EXCEPTION"))
          .when(rxInstructionHelperService)
          .persist(anyList(), anyList(), anyList(), anyString());

      BulkCompleteInstructionRequest mockBulkCompleteInstructionRequest =
          new BulkCompleteInstructionRequest();
      CompleteMultipleInstructionData mockCompleteMultipleInstructionData1 =
          new CompleteMultipleInstructionData();
      mockCompleteMultipleInstructionData1.setInstructionId(1l);
      CompleteMultipleInstructionData mockCompleteMultipleInstructionData2 =
          new CompleteMultipleInstructionData();
      mockCompleteMultipleInstructionData2.setInstructionId(2l);
      mockBulkCompleteInstructionRequest.setInstructionData(
          Arrays.asList(
              mockCompleteMultipleInstructionData1, mockCompleteMultipleInstructionData2));

      CompleteMultipleInstructionResponse result =
          rxCompleteMultipleInstructionRequestHandler.complete(
              mockBulkCompleteInstructionRequest, MockRxHttpHeaders.getHeaders());

      assertNotNull(result);

      verify(instructionPersisterService, times(2)).getInstructionById(anyLong());
      verify(epcisService, times(2)).epcisCapturePayload(any(), any(), any());
      verify(containerLabelBuilder, times(2))
          .generateContainerLabel(any(), any(), any(), any(), any());
      verify(nimRdsServiceImpl, times(1)).acquireSlotForSplitPallet(any(), any(), any());
      verify(containerService, times(2))
          .getContainerWithChildsByTrackingId(anyString(), anyBoolean());
      verify(rxSlottingServiceImpl, times(1))
          .acquireSlot(anyString(), any(), anyInt(), anyString(), any());
      verify(tenantSpecificConfigReader, times(2))
          .getConfiguredFeatureFlag(anyString(), anyString());
      verify(nimRdsServiceImpl, times(2))
          .quantityChange(anyInt(), anyString(), any(HttpHeaders.class));

      Map<String, Object> printJob = result.getPrintJob();
      List<LinkedTreeMap> printRequests = (List<LinkedTreeMap>) printJob.get("printRequests");
      assertEquals(2, printRequests.size());
      assertTrue(
          Arrays.asList("97123456", "97123457")
              .contains(printRequests.get(0).get("labelIdentifier").toString()));
      assertTrue(
          Arrays.asList("97123456", "97123457")
              .contains(printRequests.get(1).get("labelIdentifier").toString()));
    } catch (Exception e) {
      assertTrue(e instanceof ReceivingBadDataException);
    }
  }

  private SlottingPalletResponse getMockSlottingPalletResponse() {
    SlottingPalletResponse slottingPalletResponse = new SlottingPalletResponse();
    SlottingDivertLocations mockSlottingDivertLocations = new SlottingDivertLocations();
    mockSlottingDivertLocations.setLocation("M111");

    slottingPalletResponse.setLocations(Arrays.asList(mockSlottingDivertLocations));

    return slottingPalletResponse;
  }

  private Instruction getMockInstruction(long instructionId) {
    Instruction mockNewInstruction = MockInstruction.getMockNewInstruction();
    mockNewInstruction.setId(instructionId);
    mockNewInstruction.setPurchaseReferenceNumber("MOCK_PO");
    mockNewInstruction.setPurchaseReferenceLineNumber(Long.valueOf(instructionId).intValue());
    mockNewInstruction.setReceivedQuantity(1);
    mockNewInstruction.setReceivedQuantityUOM("ZA");

    return mockNewInstruction;
  }

  private ReceiveContainersResponseBody getMockReceiveContainersResponseBody() {
    ReceiveContainersResponseBody receiveContainersResponseBody =
        new ReceiveContainersResponseBody();

    ReceivedContainer receivedContainer1 = new ReceivedContainer();
    receivedContainer1.setLabelTrackingId("097123456");
    receivedContainer1.setPoNumber("MOCK_PO");
    receivedContainer1.setPoLine(1);
    receivedContainer1.setReceiver(1);
    Destination destination1 = new Destination();
    destination1.setSlot("M111");
    receivedContainer1.setDestinations(Arrays.asList(destination1));

    ReceivedContainer receivedContainer2 = new ReceivedContainer();
    receivedContainer2.setLabelTrackingId("097123457");
    receivedContainer2.setPoNumber("MOCK_PO");
    receivedContainer2.setPoLine(2);
    receivedContainer2.setReceiver(2);
    Destination destination2 = new Destination();
    destination2.setSlot("M222");
    receivedContainer2.setDestinations(Arrays.asList(destination2));

    receiveContainersResponseBody.setReceived(
        Arrays.asList(receivedContainer1, receivedContainer2));

    return receiveContainersResponseBody;
  }

  private Container getMockContainer(String trackingId) {
    Container mockContainer = RxMockContainer.getContainer();
    mockContainer.setTrackingId(trackingId);

    Set<Container> childContainerList = new HashSet<>();
    childContainerList.add(RxMockContainer.getChildContainer());

    mockContainer.setChildContainers(childContainerList);
    return mockContainer;
  }

  @Test
  public void test_completeOutboxAsn() throws ReceivingException {
    // given
    Gson gson = new Gson();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    doAnswer(
            (Answer<Instruction>)
                invocation -> {
                  Long deliveryNumber = (Long) invocation.getArguments()[0];
                  Instruction instruction = getMockInstruction(deliveryNumber);
                  instruction.setChildContainers(
                      Collections.singletonList(MockInstruction.getContainerDetails()));
                  return instruction;
                })
        .when(instructionPersisterService)
        .getInstructionById(anyLong());
    when(containerLabelBuilder.generateContainerLabel(any(), any(), any(), any(), any()))
        .thenCallRealMethod();
    when(nimRdsServiceImpl.acquireSlotForSplitPallet(any(), any(), any()))
        .thenReturn(getMockReceiveContainersResponseBody());
    doAnswer(
            (Answer<Container>)
                invocation -> {
                  String trackingId = (String) invocation.getArguments()[0];
                  return getMockContainer(trackingId);
                })
        .when(containerService)
        .getContainerWithChildsByTrackingId(anyString(), anyBoolean());
    when(rxSlottingServiceImpl.acquireSlot(anyString(), any(), anyInt(), anyString(), any()))
        .thenReturn(getMockSlottingPalletResponse());
    when(rxSlottingServiceImpl.acquireSlotMultiPallets(
            anyString(), anyInt(), any(), any(), any(), any(HttpHeaders.class)))
        .thenReturn(getMockSlottingPalletResponse());

    BulkCompleteInstructionRequest instructionRequest = new BulkCompleteInstructionRequest();
    CompleteMultipleInstructionData instructionData1 = new CompleteMultipleInstructionData();
    instructionData1.setInstructionId(1L);
    CompleteMultipleInstructionData instructionData2 = new CompleteMultipleInstructionData();
    instructionData2.setInstructionId(2L);
    instructionRequest.setInstructionData(Arrays.asList(instructionData1, instructionData2));
    when(rxLpnUtils.get18DigitLPNs(2, MockRxHttpHeaders.getHeaders())).thenReturn(getMockLPNs());
    when(rxInstructionService.mockRDSResponseObj(anyString(), any(SlotDetails.class)))
        .thenReturn(getMockReceiveContainersResponseBody());
    // when
    CompleteMultipleInstructionResponse response =
        rxCompleteMultipleInstructionRequestHandler.complete(
            instructionRequest, MockRxHttpHeaders.getHeaders());

    // then
    assertNotNull(response);
  }

  @Test
  public void test_completeOutboxEpcis() throws ReceivingException {
    // given
    Gson gson = new Gson();
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    doAnswer(
            (Answer<Instruction>)
                invocation -> {
                  Long deliveryNumber = (Long) invocation.getArguments()[0];
                  Instruction instruction = getMockInstruction(deliveryNumber);
                  DeliveryDocument deliveryDocument =
                      gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
                  DeliveryDocumentLine deliveryDocumentLine =
                      deliveryDocument.getDeliveryDocumentLines().get(0);
                  ItemData additionalInfo = new ItemData();
                  additionalInfo.setSerializedInfo(Collections.emptyList());
                  deliveryDocumentLine.setAdditionalInfo(additionalInfo);
                  deliveryDocument.setDeliveryDocumentLines(
                      Collections.singletonList(deliveryDocumentLine));
                  instruction.setDeliveryDocument(gson.toJson(deliveryDocument));
                  return instruction;
                })
        .when(instructionPersisterService)
        .getInstructionById(anyLong());
    when(containerLabelBuilder.generateContainerLabel(any(), any(), any(), any(), any()))
        .thenCallRealMethod();
    when(nimRdsServiceImpl.acquireSlotForSplitPallet(any(), any(), any()))
        .thenReturn(getMockReceiveContainersResponseBody());
    doAnswer(
            (Answer<Container>)
                invocation -> {
                  String trackingId = (String) invocation.getArguments()[0];
                  return getMockContainer(trackingId);
                })
        .when(containerService)
        .getContainerWithChildsByTrackingId(anyString(), anyBoolean());
    when(rxSlottingServiceImpl.acquireSlot(anyString(), any(), anyInt(), anyString(), any()))
        .thenReturn(getMockSlottingPalletResponse());
    when(rxSlottingServiceImpl.acquireSlotMultiPallets(
            anyString(), anyInt(), any(), any(), any(), any(HttpHeaders.class)))
        .thenReturn(getMockSlottingPalletResponse());

    BulkCompleteInstructionRequest instructionRequest = new BulkCompleteInstructionRequest();
    CompleteMultipleInstructionData instructionData1 = new CompleteMultipleInstructionData();
    instructionData1.setInstructionId(1L);
    CompleteMultipleInstructionData instructionData2 = new CompleteMultipleInstructionData();
    instructionData2.setInstructionId(2L);
    instructionRequest.setInstructionData(Arrays.asList(instructionData1, instructionData2));
    when(rxLpnUtils.get18DigitLPNs(2, MockRxHttpHeaders.getHeaders())).thenReturn(getMockLPNs());
    when(rxInstructionService.mockRDSResponseObj(anyString(), any(SlotDetails.class)))
        .thenReturn(getMockReceiveContainersResponseBody());

    // when
    CompleteMultipleInstructionResponse response =
        rxCompleteMultipleInstructionRequestHandler.complete(
            instructionRequest, MockRxHttpHeaders.getHeaders());

    // then
    assertNotNull(response);
  }

  private List<String> getMockLPNs() {
    List<String> lpns =
        Arrays.asList(
            "a47263319186056300", "a47263319186056301", "a47263319186056302", "a47263319186056303");
    return lpns;
  }
}
