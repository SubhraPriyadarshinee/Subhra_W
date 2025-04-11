package com.walmart.move.nim.receiving.rx.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.epcis.EpcisRestClient;
import com.walmart.move.nim.receiving.core.client.epcis.EpcisVerifyRequest;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.data.MockRxHttpHeaders;
import com.walmart.move.nim.receiving.rx.config.RxManagedConfig;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.mock.MockInstruction;
import com.walmart.move.nim.receiving.rx.model.RxInstructionType;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class EpcisServiceTest {

  @InjectMocks private EpcisService epcisService;
  @Mock private EpcisRestClient epcisRestClient;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private AppConfig appConfig;
  @Mock private RxManagedConfig rxManagedConfig;
  @Mock private RxCompleteInstructionOutboxHandler rxCompleteInstructionOutboxHandler;
  @Mock private ContainerService containerService;
  @Mock private RxInstructionHelperService rxInstructionHelperService;
  @Mock private InstructionPersisterService instructionPersisterService;

  private Gson gson = new Gson();

  @BeforeClass
  public void initMocks() {
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32897);
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(epcisService, "gson", gson);
  }

  @AfterMethod
  public void tearDown() {
    reset(epcisRestClient);
    reset(configUtils);
    clearInvocations(rxCompleteInstructionOutboxHandler);
  }

  private void setIsDscsaExemptionInd(
      DeliveryDocumentLine deliveryDocumentLine, boolean isDscsaExemptionInd) {

    ItemData additionalInfo = new ItemData();
    additionalInfo.setIsDscsaExemptionInd(isDscsaExemptionInd);

    deliveryDocumentLine.setAdditionalInfo(additionalInfo);
  }

  @Test
  public void testEpcisPublishData() {
    doNothing().when(epcisRestClient).publishReceiveEvent(anyList(), any(HttpHeaders.class));
    DeliveryDocumentLine deliveryDocumentLine =
        gson.fromJson(
                MockInstruction.getMockNewInstruction().getDeliveryDocument(),
                DeliveryDocument.class)
            .getDeliveryDocumentLines()
            .get(0);
    setIsDscsaExemptionInd(deliveryDocumentLine, false);
    doReturn("{\n" + "  \"32898\": \"0078742027753\"\n" + "}").when(appConfig).getGlnDetails();
    Instruction instruction = MockInstruction.getInstructionWithManufactureDetails();
    instruction.setInstructionCode(RxInstructionType.BUILD_CONTAINER.getInstructionType());
    epcisService.publishSerializedData(
        instruction,
        deliveryDocumentLine,
        new CompleteInstructionRequest(),
        MockRxHttpHeaders.getHeaders());
    verify(rxCompleteInstructionOutboxHandler, times(1))
        .outboxEpcisEvents(anyList(), any(HttpHeaders.class), anyString());
  }

  @Test
  public void testEpcisPublishData_PartialCase() {
    doNothing().when(epcisRestClient).publishReceiveEvent(anyList(), any(HttpHeaders.class));
    DeliveryDocumentLine deliveryDocumentLine =
        gson.fromJson(
                MockInstruction.getMockNewInstruction().getDeliveryDocument(),
                DeliveryDocument.class)
            .getDeliveryDocumentLines()
            .get(0);
    setIsDscsaExemptionInd(deliveryDocumentLine, false);
    CompleteInstructionRequest completeInstructionRequest = new CompleteInstructionRequest();
    completeInstructionRequest.setPartialContainer(true);
    Instruction instruction = MockInstruction.getInstructionWithManufactureDetails();
    instruction.setInstructionCode(RxInstructionType.BUILD_CONTAINER.getInstructionType());
    epcisService.publishSerializedData(
        instruction,
        deliveryDocumentLine,
        completeInstructionRequest,
        MockRxHttpHeaders.getHeaders());
    verify(rxCompleteInstructionOutboxHandler, times(1))
        .outboxEpcisEvents(anyList(), any(HttpHeaders.class), anyString());
  }

  @Test
  public void testEpcisPublishData_D40Department() {
    doNothing().when(epcisRestClient).publishReceiveEvent(anyList(), any(HttpHeaders.class));
    DeliveryDocumentLine deliveryDocumentLine =
        gson.fromJson(
                MockInstruction.getMockNewInstruction().getDeliveryDocument(),
                DeliveryDocument.class)
            .getDeliveryDocumentLines()
            .get(0);
    setIsDscsaExemptionInd(deliveryDocumentLine, true);
    epcisService.publishSerializedData(
        MockInstruction.getInstructionWithManufactureDetails(),
        deliveryDocumentLine,
        new CompleteInstructionRequest(),
        MockRxHttpHeaders.getHeaders());
    verify(epcisRestClient, times(0)).publishReceiveEvent(anyList(), any(HttpHeaders.class));
  }

  @Test
  public void testEpcisValidateGtin() throws ReceivingException {
    when(configUtils.getConfiguredFeatureFlag(anyString(), anyString())).thenReturn(true);
    doReturn(new ResponseEntity<>(HttpStatus.OK))
        .when(epcisRestClient)
        .verifySerializedData(any(EpcisVerifyRequest.class), any(HttpHeaders.class));
    DeliveryDocumentLine deliveryDocumentLine =
        gson.fromJson(
                MockInstruction.getMockNewInstruction().getDeliveryDocument(),
                DeliveryDocument.class)
            .getDeliveryDocumentLines()
            .get(0);
    setIsDscsaExemptionInd(deliveryDocumentLine, false);
    epcisService.verifySerializedData(
        getGtinMockScannedData(),
        getMockShipmentDetails(),
        deliveryDocumentLine,
        MockRxHttpHeaders.getHeaders());
    verify(epcisRestClient, times(1))
        .verifySerializedData(any(EpcisVerifyRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testEpcisValidateSscc() throws ReceivingException {
    when(configUtils.getConfiguredFeatureFlag(anyString(), anyString())).thenReturn(true);
    doReturn(new ResponseEntity<>(HttpStatus.OK))
        .when(epcisRestClient)
        .verifySerializedData(any(EpcisVerifyRequest.class), any(HttpHeaders.class));
    DeliveryDocumentLine deliveryDocumentLine =
        gson.fromJson(
                MockInstruction.getMockNewInstruction().getDeliveryDocument(),
                DeliveryDocument.class)
            .getDeliveryDocumentLines()
            .get(0);
    setIsDscsaExemptionInd(deliveryDocumentLine, false);
    epcisService.verifySerializedData(
        getSsccMockScannedData(),
        getMockShipmentDetails(),
        deliveryDocumentLine,
        MockRxHttpHeaders.getHeaders());
    verify(epcisRestClient, times(1))
        .verifySerializedData(any(EpcisVerifyRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testEpcisValidateSscc_DeptD40() throws ReceivingException {
    when(configUtils.getConfiguredFeatureFlag(anyString(), anyString())).thenReturn(true);
    doReturn(new ResponseEntity<>(HttpStatus.OK))
        .when(epcisRestClient)
        .verifySerializedData(any(EpcisVerifyRequest.class), any(HttpHeaders.class));
    DeliveryDocumentLine deliveryDocumentLine =
        gson.fromJson(
                MockInstruction.getMockNewInstruction().getDeliveryDocument(),
                DeliveryDocument.class)
            .getDeliveryDocumentLines()
            .get(0);
    setIsDscsaExemptionInd(deliveryDocumentLine, true);
    epcisService.verifySerializedData(
        getSsccMockScannedData(),
        getMockShipmentDetails(),
        deliveryDocumentLine,
        MockRxHttpHeaders.getHeaders());
    verify(epcisRestClient, times(0))
        .verifySerializedData(any(EpcisVerifyRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testEpcisValidateSscc_DeptD40_FeatureFlag() throws ReceivingException {
    when(configUtils.getConfiguredFeatureFlag("32897", RxConstants.ENABLE_DEPT_CHECK_FEATURE_FLAG))
        .thenReturn(false);
    doReturn(new ResponseEntity<>(HttpStatus.OK))
        .when(epcisRestClient)
        .verifySerializedData(any(EpcisVerifyRequest.class), any(HttpHeaders.class));
    DeliveryDocumentLine deliveryDocumentLine =
        gson.fromJson(
                MockInstruction.getMockNewInstruction().getDeliveryDocument(),
                DeliveryDocument.class)
            .getDeliveryDocumentLines()
            .get(0);
    setIsDscsaExemptionInd(deliveryDocumentLine, false);
    deliveryDocumentLine.setDeptNumber("40");
    epcisService.verifySerializedData(
        getSsccMockScannedData(),
        getMockShipmentDetails(),
        deliveryDocumentLine,
        MockRxHttpHeaders.getHeaders());
    verify(epcisRestClient, times(0))
        .verifySerializedData(any(EpcisVerifyRequest.class), any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testEpcisValidate_InvalidSerializedData() throws ReceivingException {
    when(configUtils.getConfiguredFeatureFlag(anyString(), anyString())).thenReturn(true);
    doReturn(new ResponseEntity<>(HttpStatus.OK))
        .when(epcisRestClient)
        .verifySerializedData(any(EpcisVerifyRequest.class), any(HttpHeaders.class));
    DeliveryDocumentLine deliveryDocumentLine =
        gson.fromJson(
                MockInstruction.getMockNewInstruction().getDeliveryDocument(),
                DeliveryDocument.class)
            .getDeliveryDocumentLines()
            .get(0);
    setIsDscsaExemptionInd(deliveryDocumentLine, false);
    epcisService.verifySerializedData(
        new HashMap<>(),
        getMockShipmentDetails(),
        deliveryDocumentLine,
        MockRxHttpHeaders.getHeaders());
    verify(epcisRestClient, times(0))
        .verifySerializedData(any(EpcisVerifyRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testEpcisValidate_DisableVerificationFeature() throws ReceivingException {
    when(configUtils.getConfiguredFeatureFlag(anyString(), anyString())).thenReturn(false);
    doReturn(new ResponseEntity<>(HttpStatus.OK))
        .when(epcisRestClient)
        .verifySerializedData(any(EpcisVerifyRequest.class), any(HttpHeaders.class));
    DeliveryDocumentLine deliveryDocumentLine =
        gson.fromJson(
                MockInstruction.getMockNewInstruction().getDeliveryDocument(),
                DeliveryDocument.class)
            .getDeliveryDocumentLines()
            .get(0);
    epcisService.verifySerializedData(
        getSsccMockScannedData(),
        getMockShipmentDetails(),
        deliveryDocumentLine,
        MockRxHttpHeaders.getHeaders());
    verify(epcisRestClient, times(0))
        .verifySerializedData(any(EpcisVerifyRequest.class), any(HttpHeaders.class));
  }

  private ShipmentDetails getMockShipmentDetails() {
    ShipmentDetails shipmentDetails1 = new ShipmentDetails();
    shipmentDetails1.setShipmentNumber("MOCK_SHIPMENT_NUMBER_1");

    return shipmentDetails1;
  }

  private Map<String, ScannedData> getGtinMockScannedData() {
    Map<String, ScannedData> scannedDataMap = new HashMap<>();

    List<ScannedData> scannedDataList = new ArrayList<>();

    ScannedData expScannedData = new ScannedData();
    expScannedData.setKey("expiryDate");
    expScannedData.setValue("20-05-05");
    scannedDataList.add(expScannedData);

    ScannedData gtinScannedData = new ScannedData();
    gtinScannedData.setKey("gtin");
    gtinScannedData.setValue("01123840356119");
    scannedDataList.add(gtinScannedData);

    ScannedData lotScannedData = new ScannedData();
    lotScannedData.setKey("lot");
    lotScannedData.setValue("12345678");
    scannedDataList.add(lotScannedData);

    ScannedData serialScannedData = new ScannedData();
    serialScannedData.setKey("serial");
    serialScannedData.setValue("SN345678");
    scannedDataList.add(serialScannedData);

    scannedDataList.forEach(scannedData -> scannedDataMap.put(scannedData.getKey(), scannedData));
    return scannedDataMap;
  }

  public static Map<String, ScannedData> getSsccMockScannedData() {
    Map<String, ScannedData> scannedDataMap = new HashMap<>();

    List<ScannedData> scannedDataList = new ArrayList<>();

    ScannedData ssccScannedData = new ScannedData();
    ssccScannedData.setKey(ApplicationIdentifier.SSCC.getKey());
    ssccScannedData.setValue("123456789456245");
    ssccScannedData.setApplicationIdentifier("00");
    scannedDataList.add(ssccScannedData);

    scannedDataList.forEach(scannedData -> scannedDataMap.put(scannedData.getKey(), scannedData));
    return scannedDataMap;
  }

  @Test
  public void constructAndOutboxEpcisEvent_palletRecv() throws ReceivingException {
    // given
    Instruction instruction = MockInstruction.getMockInstructionEpcis();
    instruction.setInstructionCode(RxInstructionType.SERIALIZED_CONTAINER.getInstructionType());

    Container parent = new Container();
    Container child = new Container();
    parent.setTrackingId("9700000");
    child.setTrackingId("43547-282-11_abc124");
    Set<Container> childContainers = new HashSet<>();
    childContainers.add(child);
    parent.setChildContainers(childContainers);

    when(appConfig.getGlnDetails()).thenReturn("{\n" + "  \"32898\": \"0078742027753\"\n" + "}");

    // when
    epcisService.constructAndOutboxEpcisEvent(parent, instruction, MockRxHttpHeaders.getHeaders());

    // then
    verify(rxCompleteInstructionOutboxHandler, times(1))
        .outboxClubbedEpcisEvents(any(), anyString(), any(), anyString(), any());
  }

  @Test
  public void constructAndOutboxEpcisEvent_palletRecvComplianceReceived()
      throws ReceivingException {
    // given
    Instruction instruction = MockInstruction.getMockInstructionEpcis();
    instruction.setInstructionCode(RxInstructionType.SERIALIZED_CONTAINER.getInstructionType());
    DeliveryDocument deliveryDocument =
        gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    ItemData additionalInfo = deliveryDocumentLine.getAdditionalInfo();
    additionalInfo.setIsCompliancePack(true);
    additionalInfo.setSkipEvents(true);
    deliveryDocument.setDeliveryDocumentLines(Collections.singletonList(deliveryDocumentLine));
    instruction.setDeliveryDocument(gson.toJson(deliveryDocument));

    Container parent = new Container();
    Container child = new Container();
    parent.setTrackingId("9700000");
    child.setTrackingId("43547-282-11_abc124");
    Set<Container> childContainers = new HashSet<>();
    childContainers.add(child);
    parent.setChildContainers(childContainers);

    when(appConfig.getGlnDetails()).thenReturn("{\n" + "  \"32898\": \"0078742027753\"\n" + "}");

    // when
    epcisService.constructAndOutboxEpcisEvent(parent, instruction, MockRxHttpHeaders.getHeaders());

    // then
    verify(rxCompleteInstructionOutboxHandler, times(1))
        .outboxEpcisEvent(any(), any(), anyString(), any());
  }

  @Test
  public void constructAndOutboxEpcisEvent_PalletRecvSameQty() throws ReceivingException {
    // given
    Instruction instruction = MockInstruction.getMockInstructionEpcis();
    instruction.setInstructionCode(RxInstructionType.SERIALIZED_CONTAINER.getInstructionType());
    instruction.setProjectedReceiveQty(1);
    instruction.setReceivedQuantity(1);

    Container parent = new Container();
    Container child = new Container();
    parent.setTrackingId("9700000");
    child.setTrackingId("43547-282-11_abc124");
    Set<Container> childContainers = new HashSet<>();
    childContainers.add(child);
    parent.setChildContainers(childContainers);

    when(appConfig.getGlnDetails()).thenReturn("{\n" + "  \"32898\": \"0078742027753\"\n" + "}");

    // when
    epcisService.constructAndOutboxEpcisEvent(parent, instruction, MockRxHttpHeaders.getHeaders());

    // then no NPEs
    verify(rxCompleteInstructionOutboxHandler, times(1))
        .outboxClubbedEpcisEvents(any(), anyString(), any(), anyString(), any());
  }

  @Test
  public void constructAndOutboxEpcisEvent_PartialRcv() throws ReceivingException {
    // given
    Instruction instruction = MockInstruction.getMockInstructionEpcis();
    instruction.setInstructionCode(RxInstructionType.RX_SER_BUILD_UNITS_SCAN.getInstructionType());

    Container parent = new Container();
    Container child = new Container();
    parent.setTrackingId("9700000");
    child.setTrackingId("43547-282-11_abc124");
    Set<Container> childContainers = new HashSet<>();
    childContainers.add(child);
    parent.setChildContainers(childContainers);

    when(appConfig.getGlnDetails()).thenReturn("{\n" + "  \"32898\": \"0078742027753\"\n" + "}");
    when(containerService.getContainerWithChildsByTrackingId(anyString(), anyBoolean()))
        .thenReturn(child);

    // when
    epcisService.constructAndOutboxEpcisEvent(parent, instruction, MockRxHttpHeaders.getHeaders());

    // then
    verify(rxCompleteInstructionOutboxHandler, times(1))
        .outboxClubbedEpcisEvents(any(), anyString(), any(), anyString(), any());
  }

  @Test
  public void constructAndOutboxEpcisEvent_PartialRcvPalletOfCase() throws ReceivingException {
    // given
    Instruction instruction = MockInstruction.getMockInstructionEpcis();
    instruction.setInstructionCode(RxInstructionType.RX_SER_BUILD_UNITS_SCAN.getInstructionType());
    DeliveryDocument deliveryDocument =
        gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    deliveryDocument.getDeliveryDocumentLines().get(0).getAdditionalInfo().setPalletOfCase("1234");
    instruction.setDeliveryDocument(gson.toJson(deliveryDocument));

    Container parent = new Container();
    Container child = new Container();
    parent.setTrackingId("9700000");
    child.setTrackingId("43547-282-11_abc124");
    Set<Container> childContainers = new HashSet<>();
    childContainers.add(child);
    parent.setChildContainers(childContainers);

    when(appConfig.getGlnDetails()).thenReturn("{\n" + "  \"32898\": \"0078742027753\"\n" + "}");
    when(containerService.getContainerWithChildsByTrackingId(anyString(), anyBoolean()))
        .thenReturn(child);

    // when
    epcisService.constructAndOutboxEpcisEvent(parent, instruction, MockRxHttpHeaders.getHeaders());

    // then
    verify(rxCompleteInstructionOutboxHandler, times(1))
        .outboxClubbedEpcisEvents(any(), anyString(), any(), anyString(), any());
  }


  @Test
  public void constructAndOutboxEpcisEvent_PartialRcvPalletOfCase_PalletOfMulti() throws ReceivingException {
    // given
    Instruction instruction = MockInstruction.getMockInstructionEpcis();
    instruction.setInstructionCode(RxInstructionType.RX_SER_BUILD_CONTAINER.getInstructionType());
    DeliveryDocument deliveryDocument =
            gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    ItemData additionalInfo = deliveryDocumentLine.getAdditionalInfo();
    additionalInfo.setIsCompliancePack(true);
    additionalInfo.setSkipEvents(true);
    additionalInfo.setPalletOfCase("1234");
    additionalInfo.setPalletFlowInMultiSku(true);
    instruction.setDeliveryDocument(gson.toJson(deliveryDocument));


    List<ContainerItem> containerItems = new ArrayList<>();
    ContainerItem containerItem = new ContainerItem();
    containerItem.setVnpkQty(200);
    containerItems.add(containerItem);
    Container parent = new Container();
    Container child = new Container();
    parent.setTrackingId("9700000");
    child.setTrackingId("43547-282-11_abc124");
    Set<Container> childContainer1 = new HashSet<>();
    childContainer1.add(new Container());
    child.setChildContainers(childContainer1);
    child.setContainerItems(containerItems);
    Set<Container> childContainers = new HashSet<>();
    childContainers.add(child);
    parent.setChildContainers(childContainers);

    when(appConfig.getGlnDetails()).thenReturn("{\n" + "  \"32898\": \"0078742027753\"\n" + "}");
    when(containerService.getContainerWithChildsByTrackingId(anyString(), anyBoolean()))
            .thenReturn(child);

    // when
    epcisService.constructAndOutboxEpcisEvent(parent, instruction, MockRxHttpHeaders.getHeaders());

    // then
    verify(rxCompleteInstructionOutboxHandler, times(1))
            .outboxEpcisEvent(any(), any(), anyString(), any());
  }


  @Test
  public void constructAndOutboxEpcisEvent_CaseRcvPalletOfCase() throws ReceivingException {
    // given
    Instruction instruction = MockInstruction.getMockInstructionEpcis();
    instruction.setInstructionCode(RxInstructionType.RX_SER_CNTR_CASE_SCAN.getInstructionType());
    instruction.setReceivedQuantity(0);
    DeliveryDocument deliveryDocument =
        gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    deliveryDocument.getDeliveryDocumentLines().get(0).getAdditionalInfo().setPalletOfCase("1234");
    instruction.setDeliveryDocument(gson.toJson(deliveryDocument));

    Container parent = new Container();
    Container child = new Container();
    ContainerItem childContainerItem = new ContainerItem();
    parent.setTrackingId("9700000");
    child.setTrackingId("43547-282-11_abc124");
    childContainerItem.setVnpkQty(1);
    child.setContainerItems(Collections.singletonList(childContainerItem));
    Set<Container> childContainers = new HashSet<>();
    childContainers.add(child);
    parent.setChildContainers(childContainers);

    when(appConfig.getGlnDetails()).thenReturn("{\n" + "  \"32898\": \"0078742027753\"\n" + "}");
    when(rxInstructionHelperService.fetchMultiSkuInstrDeliveryDocumentForCompleteIns(
            any(), anyString(), anyString()))
        .thenReturn(instruction);
    when(containerService.getContainerWithChildsByTrackingId(anyString(), anyBoolean()))
        .thenReturn(child);

    // when
    epcisService.constructAndOutboxEpcisEvent(parent, instruction, MockRxHttpHeaders.getHeaders());

    // then
    verify(rxCompleteInstructionOutboxHandler, times(1))
        .outboxClubbedEpcisEvents(any(), anyString(), any(), anyString(), any());
  }

  @Test
  public void constructAndOutboxEpcisEvent_CaseRcv() throws ReceivingException {
    // given
    Instruction instruction = MockInstruction.getMockInstructionEpcis();
    instruction.setInstructionCode(RxInstructionType.RX_SER_CNTR_CASE_SCAN.getInstructionType());
    instruction.setReceivedQuantity(2);
    DeliveryDocument deliveryDocument =
        gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .getSerializedInfo()
        .get(0)
        .setSscc("1234567890");
    instruction.setDeliveryDocument(gson.toJson(deliveryDocument));

    Container parent = new Container();
    Container child = new Container();
    ContainerItem childContainerItem = new ContainerItem();
    parent.setTrackingId("9700000");
    child.setTrackingId("43547-282-11_abc124");
    childContainerItem.setVnpkQty(1);
    child.setContainerItems(Collections.singletonList(childContainerItem));
    Set<Container> childContainers = new HashSet<>();
    childContainers.add(child);
    parent.setChildContainers(childContainers);

    when(appConfig.getGlnDetails()).thenReturn("{\n" + "  \"32898\": \"0078742027753\"\n" + "}");
    when(rxInstructionHelperService.fetchMultiSkuInstrDeliveryDocumentForCompleteIns(
            any(), anyString(), anyString()))
        .thenReturn(instruction);

    // when
    epcisService.constructAndOutboxEpcisEvent(parent, instruction, MockRxHttpHeaders.getHeaders());

    // then
    verify(rxCompleteInstructionOutboxHandler, times(1))
        .outboxClubbedEpcisEvents(any(), anyString(), any(), anyString(), any());
  }
}
