package com.walmart.move.nim.receiving.rx.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.epcis.EpcisRequest;
import com.walmart.move.nim.receiving.core.client.epcis.EpcisRestClient;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.config.OutboxConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.ManufactureDetail;
import com.walmart.move.nim.receiving.core.model.SlotDetails;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.core.transformer.RxContainerTransformer;
import com.walmart.move.nim.receiving.data.MockRxHttpHeaders;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.mock.MockInstruction;
import com.walmart.move.nim.receiving.rx.model.RxInstructionType;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.platform.service.OutboxEventSinkService;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.CollectionUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RxCompleteInstructionOutboxHandlerTest {

  @InjectMocks private RxCompleteInstructionOutboxHandler rxCompleteInstructionOutboxHandler;

  @Mock private RxInstructionHelperService rxInstructionHelperService;

  @Mock private OutboxEventSinkService outboxEventSinkService;
  @Mock private EpcisService epcisService;
  @Mock private EpcisRestClient epcisRestClient;

  @InjectMocks private RxContainerTransformer rxContainerTransformer;

  @Mock private ContainerService containerService;
  @Mock private ContainerPersisterService containerPersisterService;
  @Autowired private PrintJobService printJobService;
  @Mock private InstructionPersisterService instructionPersisterService;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private AppConfig appConfig;
  @Mock private OutboxConfig outboxConfig;
  @Mock private RxInstructionService rxInstructionService;

  private Gson gson = new Gson();

  @BeforeClass
  public void initMocks() {
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32897);
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(rxCompleteInstructionOutboxHandler, "gsonBuilder", gson);
    ReflectionTestUtils.setField(rxCompleteInstructionOutboxHandler, "gson", gson);
    ReflectionTestUtils.setField(
        rxCompleteInstructionOutboxHandler, "rxContainerTransformer", rxContainerTransformer);
    ReflectionTestUtils.setField(rxContainerTransformer, "gson", gson);
  }

  @AfterMethod
  public void afterMethod() {
    reset(outboxEventSinkService);
    reset(epcisService);
  }

  @Test
  public void testOutboxCompleteInstructionAsnFlow_PartialContainer() throws ReceivingException {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);

    doNothing().when(epcisRestClient).publishReceiveEvent(anyList(), any(HttpHeaders.class));
    DeliveryDocumentLine deliveryDocumentLine =
        gson.fromJson(
                MockInstruction.getMockNewInstruction().getDeliveryDocument(),
                DeliveryDocument.class)
            .getDeliveryDocumentLines()
            .get(0);
    doReturn("{\n" + "  \"32898\": \"0078742027753\"\n" + "}").when(appConfig).getGlnDetails();
    Instruction instruction = MockInstruction.getInstructionWithManufactureDetails();
    instruction.setInstructionCode(RxInstructionType.BUILD_PARTIAL_CONTAINER.getInstructionType());

    doReturn(MockInstruction.getContainer())
        .when(containerService)
        .getContainerWithChildsByTrackingId(anyString(), anyBoolean());
    Container container = MockInstruction.getContainer();
    container.setContainerItems(Arrays.asList(MockInstruction.getContainerItem()));

    rxCompleteInstructionOutboxHandler.outboxCompleteInstructionAsnFlow(
        container,
        instruction,
        "DUMMY USER",
        mock(SlotDetails.class),
        MockRxHttpHeaders.getHeaders());
  }

  @Test
  public void testOutboxCompleteInstructionAsnFlow_UpcReceiving() throws ReceivingException {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);

    doNothing().when(epcisRestClient).publishReceiveEvent(anyList(), any(HttpHeaders.class));
    DeliveryDocumentLine deliveryDocumentLine =
        gson.fromJson(
                MockInstruction.getMockNewInstruction().getDeliveryDocument(),
                DeliveryDocument.class)
            .getDeliveryDocumentLines()
            .get(0);
    doReturn("{\n" + "  \"32898\": \"0078742027753\"\n" + "}").when(appConfig).getGlnDetails();
    Instruction instruction = MockInstruction.getInstructionWithManufactureDetails();
    instruction.setInstructionCode(
        RxInstructionType.BUILD_CONTAINER_FOR_UPC_RECEIVING.getInstructionType());

    doReturn(MockInstruction.getContainer())
        .when(containerService)
        .getContainerWithChildsByTrackingId(anyString(), anyBoolean());
    Container container = MockInstruction.getContainer();
    container.setContainerItems(Arrays.asList(MockInstruction.getContainerItem()));

    rxCompleteInstructionOutboxHandler.outboxCompleteInstructionAsnFlow(
        container,
        instruction,
        "DUMMY USER",
        mock(SlotDetails.class),
        MockRxHttpHeaders.getHeaders());
  }

  @Test
  public void correctScannedInfoLevels() {
    // given
    Container parent = MockInstruction.getContainer();
    Instruction instruction = new Instruction();
    instruction.setInstructionCode(RxInstructionType.SERIALIZED_CONTAINER.getInstructionType());

    // when
    ReflectionTestUtils.invokeMethod(
        rxCompleteInstructionOutboxHandler, "correctScannedInfoLevels", parent, instruction);

    // then
    List<Container> child =
        parent.getChildContainers().parallelStream().collect(Collectors.toList());
    Assert.assertNull(child.get(0).getContainerItems().get(0).getExpiryDate());
  }

  @Test
  public void correctScannedInfoLevel_EmptyContainerItems() {
    // given
    Container parent = new Container();
    // when
    ReflectionTestUtils.invokeMethod(
        rxCompleteInstructionOutboxHandler, "correctScannedInfoLevel", parent);
    // then
    Assert.assertTrue(CollectionUtils.isEmpty(parent.getContainerItems()));
  }

  @Test
  public void eachesDetails_PartialCase() throws ReceivingException {
    // given
    Container parent = MockInstruction.getContainer();
    parent.setContainerItems(Collections.singletonList(MockInstruction.getContainerItem()));
    parent.setContainerMiscInfo(
        Collections.singletonMap(
            RxConstants.INSTRUCTION_CODE,
            RxInstructionType.RX_SER_BUILD_UNITS_SCAN.getInstructionType()));

    doReturn(parent)
        .when(containerService)
        .getContainerWithChildsByTrackingId(anyString(), anyBoolean());

    // when
    rxCompleteInstructionOutboxHandler.eachesDetail(parent, MockRxHttpHeaders.getHeaders());

    // then
    verify(outboxEventSinkService, times(1)).saveEvent(any());
  }


  @Test
  public void details_Pallet() throws ReceivingException {
    // given
    Container parent = MockInstruction.getContainer();
    parent.setPalletFlowInMultiSku(true);
    parent.setContainerItems(Collections.singletonList(MockInstruction.getContainerItem()));
    parent.setContainerMiscInfo(
            Collections.singletonMap(
                    RxConstants.INSTRUCTION_CODE,
                    RxInstructionType.RX_SER_BUILD_CONTAINER.getInstructionType()));

    doReturn(parent)
            .when(containerService)
            .getContainerWithChildsByTrackingId(anyString(), anyBoolean());

    // when
    rxCompleteInstructionOutboxHandler.eachesDetail(parent, MockRxHttpHeaders.getHeaders());

    // then
    verify(outboxEventSinkService, times(1)).saveEvent(any());
  }

  @Test
  public void outboxCompleteInstruction() {
    // given
    Instruction instruction = new Instruction();
    Container container = new Container();
    container.setTrackingId("970");

    // when
    rxCompleteInstructionOutboxHandler.outboxCompleteInstruction(
        container, instruction, "user", mock(SlotDetails.class), MockRxHttpHeaders.getHeaders());

    // then
    verify(outboxEventSinkService, times(1)).saveEvent(any());
  }

  @Test
  public void pendingContainers_PalletRcv() throws ReceivingException {
    // given
    Container parent = MockInstruction.getContainer();
    parent.setContainerItems(Collections.singletonList(MockInstruction.getContainerItem()));
    parent.setInstructionId(1L);
    doReturn(parent)
        .when(containerService)
        .getContainerWithChildsByTrackingId(anyString(), anyBoolean());

    Instruction instruction = MockInstruction.getMockInstructionEpcis();
    instruction.setInstructionCode(RxInstructionType.SERIALIZED_CONTAINER.getInstructionType());
    doReturn(instruction).when(instructionPersisterService).getInstructionById(anyLong());
    doNothing().when(epcisService).constructAndOutboxEpcisEvent(any(), any(), any());

    // when
    rxCompleteInstructionOutboxHandler.pendingContainers("970", MockRxHttpHeaders.getHeaders());

    // then
    verify(epcisService, times(1)).constructAndOutboxEpcisEvent(any(), any(), any());
  }

  @Test
  public void pendingContainers_CaseRcv() throws ReceivingException {
    // given
    Container parent = MockInstruction.getContainer();
    parent.setContainerItems(Collections.singletonList(MockInstruction.getContainerItem()));
    parent.setInstructionId(1L);
    doReturn(parent)
        .when(containerService)
        .getContainerWithChildsByTrackingId(anyString(), anyBoolean());

    Instruction instruction = MockInstruction.getMockInstructionEpcisScannedCase();
    instruction.setInstructionCode(RxInstructionType.SERIALIZED_CASES_SCAN.getInstructionType());
    doReturn(instruction).when(instructionPersisterService).getInstructionById(anyLong());
    doNothing().when(epcisService).constructAndOutboxEpcisEvent(any(), any(), any());

    // when
    rxCompleteInstructionOutboxHandler.pendingContainers("970", MockRxHttpHeaders.getHeaders());

    // then
    verify(epcisService, times(1)).constructAndOutboxEpcisEvent(any(), any(), any());
  }

  @Test
  public void pendingContainers_PalletRcv_2() throws ReceivingException {
    // given
    Container parent = MockInstruction.getContainer();
    parent.setContainerItems(Collections.singletonList(MockInstruction.getContainerItem()));
    parent.setInstructionId(1L);
    doReturn(parent)
            .when(containerService)
            .getContainerWithChildsByTrackingId(anyString(), anyBoolean());

    Instruction instruction = MockInstruction.getMockInstructionEpcis();
    DeliveryDocument deliveryDocument =
            gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setPalletFlowInMultiSku(false);
    deliveryDocumentLine.getAdditionalInfo().setAuditQty(1);
    deliveryDocumentLine.setNdc("123");
    ManufactureDetail manufactureDetail = new ManufactureDetail();
    manufactureDetail.setGtin("00368180121015");
    manufactureDetail.setSerial("abc124");
    manufactureDetail.setExpiryDate("2025-01-08");
    manufactureDetail.setReportedUom("EA");
    manufactureDetail.setLot("00L032C09C");


    List<ManufactureDetail> serializedInfo= deliveryDocumentLine.getAdditionalInfo().getSerializedInfo();
    serializedInfo.add(manufactureDetail);
    deliveryDocumentLine.getAdditionalInfo().setSerializedInfo(serializedInfo);

    instruction.setDeliveryDocument(gson.toJson(deliveryDocument));
    instruction.setInstructionCode(RxInstructionType.RX_SER_BUILD_CONTAINER.getInstructionType());
    doReturn(instruction).when(instructionPersisterService).getInstructionById(anyLong());
    doNothing().when(epcisService).constructAndOutboxEpcisEvent(any(), any(), any());

    // when
    rxCompleteInstructionOutboxHandler.pendingContainers("123_WERTYU4333", MockRxHttpHeaders.getHeaders());

    // then
    verify(epcisService, times(1)).constructAndOutboxEpcisEvent(any(), any(), any());
  }

  @Test
  public void outboxEpcisEvents() {
    // given
    EpcisRequest epcisRequest = new EpcisRequest();
    // when
    rxCompleteInstructionOutboxHandler.outboxEpcisEvents(
        Collections.singletonList(epcisRequest), MockRxHttpHeaders.getHeaders(), "970");
    // then
    verify(outboxEventSinkService, times(1)).saveEvent(any());
  }

  @Test
  public void outboxClubbedEpcisEvents() {
    // given
    LinkedHashSet<EpcisRequest> epcisRequests = new LinkedHashSet<>();

    // when
    rxCompleteInstructionOutboxHandler.outboxClubbedEpcisEvents(
        epcisRequests, "arriving-receiving", MockRxHttpHeaders.getHeaders(), "970", Instant.now());
    // then
    verify(outboxEventSinkService, times(1)).saveEvent(any());
  }
}
