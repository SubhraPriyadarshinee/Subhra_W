package com.walmart.move.nim.receiving.rx.service.v2.instruction.create;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.RxDeliveryDocumentsMapperV2;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.DataHolder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingDivertLocations;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponse;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.data.MockRxHttpHeaders;
import com.walmart.move.nim.receiving.rx.mock.MockInstruction;
import com.walmart.move.nim.receiving.rx.model.RxReceivingType;
import com.walmart.move.nim.receiving.rx.service.RxDeliveryDocumentsSearchHandlerV2;
import com.walmart.move.nim.receiving.rx.service.RxDeliveryServiceImpl;
import com.walmart.move.nim.receiving.rx.service.RxInstructionService;
import com.walmart.move.nim.receiving.rx.service.RxSlottingServiceImpl;
import com.walmart.move.nim.receiving.rx.service.v2.data.CreateInstructionServiceHelper;
import com.walmart.move.nim.receiving.rx.service.v2.validation.data.CreateInstructionDataValidator;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

import static com.walmart.move.nim.receiving.rx.constants.RxConstants.ReceivingTypes.CASE;
import static com.walmart.move.nim.receiving.rx.constants.RxConstants.ReceivingTypes.FULL_PALLET;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.KEY_LOT;
import static org.mockito.ArgumentMatchers.*;
import static org.testng.Assert.*;

public class CaseCreateInstructionServiceTest {

    @Mock private RxDeliveryDocumentsSearchHandlerV2 rxDeliveryDocumentsSearchHandlerV2;
    @Mock private RxDeliveryServiceImpl rxDeliveryServiceImpl;
    @Mock private RxSlottingServiceImpl rxSlottingServiceImpl;
    @Mock private RxDeliveryDocumentsMapperV2 rxDeliveryDocumentsMapperV2;
    @Spy private ReceiptService receiptService;
    @Mock private InstructionHelperService instructionHelperService;
    @Mock private CreateInstructionDataValidator createInstructionDataValidator;
    @Mock private CreateInstructionServiceHelper createInstructionServiceHelper;
    @Mock protected InstructionPersisterService instructionPersisterService;
    @Mock private RxInstructionService rxInstructionService;
    @InjectMocks private CaseCreateInstructionService caseCreateInstructionService;
    private static Gson gson = new Gson();

    @BeforeClass
    public void initMocks() {
        TenantContext.setFacilityCountryCode("US");
        TenantContext.setFacilityNum(32897);
        MockitoAnnotations.initMocks(this);

    }

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void test_defaultBaseCreateInstructionService_validateData() throws IOException {
        Instruction instruction = MockInstruction.getInstructionV2("RxSerBuildContainer");
        DeliveryDocument deliveryDocument = gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);

        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);

        ssccScanResponse.getContainers().get(0).setReceivingStatus("OPEN");
        DataHolder mockDataHolder = DataHolder.builder()
                .instruction(instruction)
                .container(ssccScanResponse.getContainers().get(0))
                .deliveryDocumentLine(deliveryDocument.getDeliveryDocumentLines().get(0))
                .deliveryDocuments(Arrays.asList(deliveryDocument))
                .build();
        Mockito.doNothing().when(createInstructionDataValidator).performDeliveryDocumentLineValidations(any());
        Mockito.doNothing().when(rxInstructionService).filterInvalidPOs(anyList());
        Mockito.doNothing().when(createInstructionDataValidator).validatePartiallyReceivedContainers(anyString());
        Mockito.doNothing().when(createInstructionDataValidator).validateNodesReceivingStatus(anyString());

        caseCreateInstructionService.validateData(mockDataHolder);

        // VERIFY
        Mockito.verify(createInstructionDataValidator).performDeliveryDocumentLineValidations(any());
        Mockito.verify(rxInstructionService).filterInvalidPOs(anyList());
        Mockito.verify(createInstructionDataValidator).validatePartiallyReceivedContainers(anyString());
        Mockito.verify(createInstructionDataValidator).validateNodesReceivingStatus(anyString());
    }

    @Test
    void test_defaultBaseCreateInstructionService_validateData_verify_validatePartialsInSplitPallet_for_non_multi_sku() throws IOException {
        Instruction instruction = MockInstruction.getInstructionV2("RxSerBuildContainer");
        DeliveryDocument deliveryDocument = gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);

        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);
        SsccScanResponse.Container container =  ssccScanResponse.getContainers().get(0);
        container.setHints(Arrays.asList("UNIT_ITEM"));
        DeliveryDocument.GdmCurrentNodeDetail gdmCurrentNodeDetail =
                new DeliveryDocument.GdmCurrentNodeDetail(
                        Arrays.asList(container), ssccScanResponse.getAdditionalInfo());
        deliveryDocument.setGdmCurrentNodeDetail(gdmCurrentNodeDetail);



        ssccScanResponse.getContainers().get(0).setReceivingStatus("OPEN");
        DataHolder mockDataHolder = DataHolder.builder()
                .instruction(instruction)
                .container(ssccScanResponse.getContainers().get(0))
                .deliveryDocument(deliveryDocument)
                .deliveryDocumentLine(deliveryDocument.getDeliveryDocumentLines().get(0))
                .deliveryDocuments(Arrays.asList(deliveryDocument))
                .receivingFlow("PLT-UNPACKED-AND-CASES-RCVD")
                .build();

        Mockito.doNothing().when(createInstructionDataValidator).validatePartialsInSplitPallet(any(), anyBoolean());

        caseCreateInstructionService.validateData(new InstructionRequest(), mockDataHolder);

        // VERIFY
        Mockito.verify(createInstructionDataValidator).validatePartialsInSplitPallet(any(), anyBoolean());

    }

    @Test
    void test_defaultBaseCreateInstructionService_validateData_verify_validatePartialsInSplitPallet_not_called_for_multi_sku() throws IOException {
        Instruction instruction = MockInstruction.getInstructionV2("RxSerBuildContainer");
        DeliveryDocument deliveryDocument = gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);

        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);
        SsccScanResponse.Container container =  ssccScanResponse.getContainers().get(0);
        container.setHints(Arrays.asList("UNIT_ITEM"));
        DeliveryDocument.GdmCurrentNodeDetail gdmCurrentNodeDetail =
                new DeliveryDocument.GdmCurrentNodeDetail(
                        Arrays.asList(container), ssccScanResponse.getAdditionalInfo());
        deliveryDocument.setGdmCurrentNodeDetail(gdmCurrentNodeDetail);



        ssccScanResponse.getContainers().get(0).setReceivingStatus("OPEN");
        DataHolder mockDataHolder = DataHolder.builder()
                .instruction(instruction)
                .container(ssccScanResponse.getContainers().get(0))
                .deliveryDocument(deliveryDocument)
                .deliveryDocumentLine(deliveryDocument.getDeliveryDocumentLines().get(0))
                .deliveryDocuments(Arrays.asList(deliveryDocument))
                .receivingFlow("MULTI-SKU")
                .build();

        Mockito.doNothing().when(createInstructionDataValidator).validatePartialsInSplitPallet(any(), anyBoolean());

        caseCreateInstructionService.validateData(new InstructionRequest(), mockDataHolder);

        // VERIFY validatePartialsInSplitPallet WAS NOT CALLED
        Mockito.verifyNoInteractions(createInstructionDataValidator);


    }

    @Test
    public void testServeInstruction_container_sscc() throws IOException, ReceivingException {
        Instruction instruction = MockInstruction.getInstructionV2("Build Container");
        instruction.setMove(getMoveData());
        Container container = mockResponseForGetParentContainer("1234",  "123", 2);
        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);


        DeliveryDocument deliveryDocument = getDeliveryDocument();
        DeliveryDocument.GdmCurrentNodeDetail gdmCurrentNodeDetail =
                new DeliveryDocument.GdmCurrentNodeDetail(
                        ssccScanResponse.getContainers(), ssccScanResponse.getAdditionalInfo());
        deliveryDocument.setGdmCurrentNodeDetail(gdmCurrentNodeDetail);

        DeliveryDocumentLine line = selectDocumentAndDocumentLine(deliveryDocument);
        DataHolder dataHolder = new DataHolder();
        dataHolder.setInstruction(instruction);
        dataHolder.setContainer(ssccScanResponse.getContainers().get(0));
        dataHolder.setDeliveryDocument(deliveryDocument);
        dataHolder.setDeliveryDocumentLine(line);
        dataHolder.setReceivingFlow(CASE);
        InstructionRequest request = new InstructionRequest();
        request.setSscc("sscc_test");
        request.setDeliveryNumber("1234");
        request.setReceivingType("SSCC");;
        ScannedData scannedData = new ScannedData();
        scannedData.setKey("sscc");
        scannedData.setValue("200109395464720439");
        scannedData.setApplicationIdentifier("00");
        request.setScannedDataList(Collections.singletonList(scannedData));
        for(SsccScanResponse.Container c : ssccScanResponse.getContainers()) {
            c.setHints(Collections.singletonList("CASE_PACK_ITEM"));
        }



       // Mockito.when(caseCreateInstructionService.createEPCISInstruction(any(), any(), any(), any())).thenReturn(instruction);
        Mockito.when(rxDeliveryDocumentsSearchHandlerV2.getCurrentAndSiblings(any(InstructionRequest.class), any(HttpHeaders.class), nullable(String.class))).thenReturn(ssccScanResponse);
        Mockito.when(rxDeliveryServiceImpl.getCurrentAndSiblings(any(), any(), anyMap())).thenReturn(ssccScanResponse);
        Mockito.when(rxSlottingServiceImpl.acquireSlotMultiPallets(anyString(), anyInt(), anyList(), anyList(), anyString(), any())).thenReturn(mockSlottingPalletResponse());
        Mockito.when(instructionHelperService.getReceivedQtyDetailsInEaAndValidate(nullable(String.class), any(), anyString())).thenReturn(new Pair<>(10, 100L));
        Mockito.doNothing().when(createInstructionDataValidator).isNewInstructionCanBeCreated(anyString(), anyInt(), anyInt(), anyLong(), anyBoolean(), anyBoolean(), anyString());
        Mockito.when(createInstructionServiceHelper.getReceivingTypeFromUI(any())).thenReturn(RxReceivingType.SSCC);
        Mockito.when(instructionPersisterService.saveInstruction(any(Instruction.class))).thenReturn(instruction);
        InstructionResponse instructionResponse = caseCreateInstructionService.serveInstruction(request, dataHolder,  MockRxHttpHeaders.getHeaders());
        Assert.assertNotNull(instructionResponse);
    }


    @Test
    public void testServeInstruction_container_serial() throws IOException, ReceivingException {
        Instruction instruction = MockInstruction.getInstructionV2("Build Container");
        instruction.setMove(getMoveData());
        Container container = mockResponseForGetParentContainer("1234",  "123", 2);
        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);




        DeliveryDocument deliveryDocument = getDeliveryDocument();
        DeliveryDocument.GdmCurrentNodeDetail gdmCurrentNodeDetail =
                new DeliveryDocument.GdmCurrentNodeDetail(
                        ssccScanResponse.getContainers(), ssccScanResponse.getAdditionalInfo());
        deliveryDocument.setGdmCurrentNodeDetail(gdmCurrentNodeDetail);

        DeliveryDocumentLine line = selectDocumentAndDocumentLine(deliveryDocument);
        DataHolder dataHolder = new DataHolder();
        dataHolder.setInstruction(instruction);
        dataHolder.setContainer(ssccScanResponse.getContainers().get(0));
        dataHolder.setDeliveryDocument(deliveryDocument);
        dataHolder.setDeliveryDocumentLine(line);
        dataHolder.setReceivingFlow(CASE);
        InstructionRequest request = new InstructionRequest();
        request.setSscc("sscc_test");
        request.setDeliveryNumber("1234");
        request.setReceivingType("SSCC");;

        ScannedData scannedData1 = new ScannedData();
        scannedData1.setKey("serial");
        scannedData1.setValue("testserial");
        scannedData1.setApplicationIdentifier("21");

        ScannedData scannedData2 = new ScannedData();
        scannedData2.setKey("gtin");
        scannedData2.setValue("200109395464720439");
        scannedData2.setApplicationIdentifier("01");

        ScannedData scannedData3 = new ScannedData();
        scannedData3.setKey("expiryDate");
        scannedData3.setValue("261231");
        scannedData3.setApplicationIdentifier("17");

        ScannedData scannedData4 = new ScannedData();
        scannedData4.setKey("lot");
        scannedData4.setValue("testLot");
        scannedData4.setApplicationIdentifier("10");
        List<ScannedData> scannedData = new ArrayList<>();
        scannedData.add(scannedData1);
        scannedData.add(scannedData2);
        scannedData.add(scannedData3);
        scannedData.add(scannedData4);
        request.setScannedDataList(scannedData);
        for(SsccScanResponse.Container c : ssccScanResponse.getContainers()) {
            c.setHints(Collections.singletonList("CASE_PACK_ITEM"));
            c.setSscc(null);
            c.setSerial("testserial");
            c.setGtin("testgtin");
            c.setLotNumber("testLot");
            c.setExpiryDate("261231");
        }



        // Mockito.when(caseCreateInstructionService.createEPCISInstruction(any(), any(), any(), any())).thenReturn(instruction);
        Mockito.when(rxDeliveryDocumentsSearchHandlerV2.getCurrentAndSiblings(any(InstructionRequest.class), any(HttpHeaders.class), nullable(String.class))).thenReturn(ssccScanResponse);
        Mockito.when(rxDeliveryServiceImpl.getCurrentAndSiblings(any(), any(), anyMap())).thenReturn(ssccScanResponse);
        Mockito.when(rxSlottingServiceImpl.acquireSlotMultiPallets(anyString(), anyInt(), anyList(), anyList(), anyString(), any())).thenReturn(mockSlottingPalletResponse());
        Mockito.when(instructionHelperService.getReceivedQtyDetailsInEaAndValidate(nullable(String.class), any(), anyString())).thenReturn(new Pair<>(10, 100L));
        Mockito.doNothing().when(createInstructionDataValidator).isNewInstructionCanBeCreated(anyString(), anyInt(), anyInt(), anyLong(), anyBoolean(), anyBoolean(), anyString());
        Mockito.when(createInstructionServiceHelper.getReceivingTypeFromUI(any())).thenReturn(RxReceivingType.SSCC);
        Mockito.when(instructionPersisterService.saveInstruction(any(Instruction.class))).thenReturn(instruction);
        InstructionResponse instructionResponse = caseCreateInstructionService.serveInstruction(request, dataHolder,  MockRxHttpHeaders.getHeaders());
        Assert.assertNotNull(instructionResponse);
    }

    @Test
    public void testValidateProjectedReceivedQuantity() throws IOException, ReceivingException {
        Instruction instruction = MockInstruction.getInstructionV2("Build Container");
        instruction.setMove(getMoveData());
        InstructionRequest request = new InstructionRequest();
        request.setSscc("sscc_test");
        request.setDeliveryNumber("1234");
        request.setReceivingType("SSCC");;
        ScannedData scannedData = new ScannedData();
        scannedData.setKey("sscc");
        scannedData.setValue("200109395464720439");
        scannedData.setApplicationIdentifier("00");
        request.setScannedDataList(Collections.singletonList(scannedData));

        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);


        DeliveryDocument deliveryDocument = getDeliveryDocument();
        DeliveryDocument.GdmCurrentNodeDetail gdmCurrentNodeDetail =
                new DeliveryDocument.GdmCurrentNodeDetail(
                        ssccScanResponse.getContainers(), ssccScanResponse.getAdditionalInfo());
        deliveryDocument.setGdmCurrentNodeDetail(gdmCurrentNodeDetail);
        DeliveryDocumentLine line = selectDocumentAndDocumentLine(deliveryDocument);

        Mockito.doNothing().when(createInstructionDataValidator).isNewInstructionCanBeCreated(anyString(), anyInt(), anyInt(), anyLong(), anyBoolean(), anyBoolean(), anyString());

        caseCreateInstructionService.validateProjectedReceivedQuantity(request, MockRxHttpHeaders.getHeaders(), line, 10, 10L );

        Mockito.verify(createInstructionDataValidator, Mockito.times(1)).isNewInstructionCanBeCreated(anyString(), anyInt(), anyInt(), anyLong(), anyBoolean(), anyBoolean(), anyString());
    }

    private static DeliveryDocument getDeliveryDocument()  throws IOException {
        File resource =
                new ClassPathResource("delivery_documents_mock.json")
                        .getFile();
        String mockResponse = new String(Files.readAllBytes(resource.toPath()));
        DeliveryDocument deliveryDocument = gson.fromJson(mockResponse, DeliveryDocument.class);
        return deliveryDocument;
    }

    private DeliveryDocumentLine getMockDeliveryDocumentLine() {
        DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
        deliveryDocumentLine.setVendorPack(12);
        deliveryDocumentLine.setWarehousePack(2);
        ItemData additionalData = new ItemData();
        additionalData.setAtlasConvertedItem(false);
        additionalData.setPackTypeCode("B");
        additionalData.setHandlingCode("C");
        additionalData.setItemPackAndHandlingCode("BC");
        additionalData.setItemHandlingMethod("Breakpack Conveyable");
        deliveryDocumentLine.setAdditionalInfo(additionalData);
        deliveryDocumentLine.setItemNbr(34533232L);
        return deliveryDocumentLine;
    }

    private Container mockResponseForGetParentContainer(
            String parentTrackingId, String trackingId, int quantity) {
        Container container = new Container();
        container.setDeliveryNumber(12345l);
        container.setTrackingId(trackingId);
        container.setParentTrackingId(parentTrackingId);
        container.setChildContainers(mockResponseForGetContainerIncludesChildren(trackingId));
        container.setInstructionId(1L);
        container.setContainerItems(Arrays.asList(MockInstruction.getContainerItem()));
        container.setSsccNumber("test_sscc");
        HashMap<String, Object> map = new HashMap<>();
        map.put("instructionCode", "RxSerBuildUnitScan");
        container.setContainerMiscInfo(map);
        return container;
    }

    private Set<Container> mockResponseForGetContainerIncludesChildren(String trackingId) {
        Container childContainer1 = createChildContainer("12345", "123", 6);
        Container childContainer2 = createChildContainer("12345", "456", 6);
        Set<Container> childContainers = new HashSet<>();
        childContainers.add(childContainer1);
        childContainers.add(childContainer2);
        return childContainers;
    }

    private Container createChildContainer(String parentTrackingId, String trackingId, int quantity) {
        Container container = new Container();
        container.setDeliveryNumber(12345l);
        container.setTrackingId(trackingId);
        container.setParentTrackingId(parentTrackingId);

        ContainerItem containerItem = new ContainerItem();
        containerItem.setTrackingId(trackingId);
        containerItem.setPurchaseReferenceNumber("987654321");
        containerItem.setPurchaseReferenceLineNumber(1);
        containerItem.setQuantity(quantity);
        containerItem.setVnpkQty(6);
        containerItem.setWhpkQty(6);

        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put(KEY_SSCC, "test_sscc");
        map.put(KEY_GTIN, "test_gtin");
        map.put(KEY_SERIAL, trackingId.toString());
        map.put(KEY_LOT, "test_lot");
        container.setContainerMiscInfo(map);

        container.setContainerItems(Arrays.asList(containerItem));

        return container;
    }

    private LinkedTreeMap<String, Object> getMoveData() {
        LinkedTreeMap<String, Object> move = new LinkedTreeMap<>();
        move.put("lastChangedBy", "OF-SYS");
        move.put("lastChangedOn", new Date());
        move.put("sequenceNbr", 543397582);
        move.put("containerTag", "b328990000000000000048571");
        move.put("correlationID", "98e22370-f2f0-11e8-b725-95f2a20d59c0");
        move.put("toLocation", "302");
        return move;
    }

    private SlottingPalletResponse mockSlottingPalletResponse() {

        SlottingDivertLocations location = new SlottingDivertLocations();
        location.setType("success");
        location.setLocation("A1234");
        location.setItemNbr(579516308);
        location.setAsrsAlignment("SYM2");
        location.setSlotType(ReceivingConstants.PRIME_SLOT_TYPE);
        List<SlottingDivertLocations> locationList = new ArrayList();
        locationList.add(location);

        SlottingPalletResponse mockSlottingResponseBody = new SlottingPalletResponse();
        mockSlottingResponseBody.setMessageId("a1-b1-c1");
        mockSlottingResponseBody.setLocations(locationList);
        return mockSlottingResponseBody;
    }

    private DeliveryDocumentLine selectDocumentAndDocumentLine(DeliveryDocument selectedDocument) {
        List<DeliveryDocumentLine> deliveryDocumentLines = selectedDocument.getDeliveryDocumentLines();
        return deliveryDocumentLines
                .stream()
                .sorted(
                        Comparator.comparing(DeliveryDocumentLine::getPurchaseReferenceNumber)
                                .thenComparing(DeliveryDocumentLine::getPurchaseReferenceLineNumber))
                .collect(Collectors.toList())
                .get(0);
    }
}