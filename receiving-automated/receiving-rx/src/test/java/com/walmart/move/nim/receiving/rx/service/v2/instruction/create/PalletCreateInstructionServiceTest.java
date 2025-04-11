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
import com.walmart.move.nim.receiving.rx.service.RxSlottingServiceImpl;
import com.walmart.move.nim.receiving.rx.service.v2.data.CreateInstructionServiceHelper;
import com.walmart.move.nim.receiving.rx.service.v2.validation.data.CreateInstructionDataValidator;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.mockito.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

import static com.walmart.move.nim.receiving.rx.constants.RxConstants.ReceivingTypes.CASE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.KEY_LOT;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.testng.Assert.*;

public class PalletCreateInstructionServiceTest {

    @Mock
    private RxDeliveryDocumentsSearchHandlerV2 rxDeliveryDocumentsSearchHandlerV2;
    @Mock private RxDeliveryServiceImpl rxDeliveryServiceImpl;
    @Mock private RxSlottingServiceImpl rxSlottingServiceImpl;
    @Mock private RxDeliveryDocumentsMapperV2 rxDeliveryDocumentsMapperV2;
    @Spy
    private ReceiptService receiptService;
    @Mock private InstructionHelperService instructionHelperService;
    @Mock private CreateInstructionDataValidator createInstructionDataValidator;
    @Mock private CreateInstructionServiceHelper createInstructionServiceHelper;
    @Mock protected InstructionPersisterService instructionPersisterService;
    @InjectMocks private PalletCreateInstructionService palletCreateInstructionService;
    private static Gson gson = new Gson();

    @BeforeClass
    public void initMocks() {
        TenantContext.setFacilityCountryCode("US");
        TenantContext.setFacilityNum(32897);
        MockitoAnnotations.initMocks(this);
        ReflectionTestUtils.setField(palletCreateInstructionService, "gson", gson);

    }

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testServeInstruction() throws ReceivingException, IOException {
        Instruction instruction = MockInstruction.getInstructionV2("Build Container");
        instruction.setMove(getMoveData());

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
        deliveryDocument.setDeliveryDocumentLines(Arrays.asList(line));

        for(SsccScanResponse.Container c : ssccScanResponse.getContainers()) {
            c.setHints(Collections.singletonList("SINGLE_SKU_PACKAGE"));
        }


        DataHolder dataHolder = new DataHolder();
        dataHolder.setInstruction(instruction);
        dataHolder.setContainer(ssccScanResponse.getContainers().get(0));
        dataHolder.setDeliveryDocument(deliveryDocument);
        dataHolder.setDeliveryDocumentLine(line);
        dataHolder.setReceivingFlow("FULL-PALLET");
        InstructionRequest request = new InstructionRequest();
        request.setSscc("sscc_test");
        request.setDeliveryNumber("1234");
        request.setReceivingType("SSCC");;
        request.setDeliveryStatus("OPN");
        request.setMessageId("12345");
        request.setIsDSDC(false);
        request.setIsPOCON(false);


        ScannedData scannedData = new ScannedData();
        scannedData.setKey("sscc");
        scannedData.setValue("200109395464720439");
        scannedData.setApplicationIdentifier("00");
        request.setScannedDataList(Collections.singletonList(scannedData));




        // Mockito.when(caseCreateInstructionService.createEPCISInstruction(any(), any(), any(), any())).thenReturn(instruction);
        Mockito.when(rxDeliveryDocumentsSearchHandlerV2.getCurrentAndSiblings(any(InstructionRequest.class), any(HttpHeaders.class), nullable(String.class))).thenReturn(ssccScanResponse);
        Mockito.when(rxDeliveryServiceImpl.getCurrentAndSiblings(any(), any(), anyMap())).thenReturn(ssccScanResponse);
        Mockito.when(rxSlottingServiceImpl.acquireSlotMultiPallets(anyString(), anyInt(), anyList(), anyList(), anyString(), any())).thenReturn(mockSlottingPalletResponse());
        Mockito.when(instructionHelperService.getReceivedQtyDetailsInEaAndValidate(nullable(String.class), any(), anyString())).thenReturn(new Pair<>(10, 100L));
        Mockito.doNothing().when(createInstructionDataValidator).isNewInstructionCanBeCreated(anyString(), anyInt(), anyInt(), anyLong(), anyBoolean(), anyBoolean(), anyString());
        Mockito.when(createInstructionServiceHelper.getReceivingTypeFromUI(any())).thenReturn(RxReceivingType.SSCC);
        Mockito.when(instructionPersisterService.saveInstruction(any(Instruction.class))).thenReturn(instruction);

        InstructionResponse response = palletCreateInstructionService.serveInstruction(request, dataHolder, MockRxHttpHeaders.getHeaders());

        Assert.assertNotNull(response);

    }

    @Test
    public void testCalculateQuantitiesAndPersistIns() throws IOException, ReceivingException {
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

        line.getAdditionalInfo().setAttpQtyInEaches(100);

        deliveryDocument.setDeliveryDocumentLines(Arrays.asList(line));

        for(SsccScanResponse.Container c : ssccScanResponse.getContainers()) {
            c.setHints(Collections.singletonList("SINGLE_SKU_PACKAGE"));
        }


        Mockito.when(instructionHelperService.getReceivedQtyDetailsInEaAndValidate(nullable(String.class), any(), anyString())).thenReturn(new Pair<>(10, 100L));
        Mockito.doNothing().when(createInstructionDataValidator).isNewInstructionCanBeCreated(anyString(), anyInt(), anyInt(), anyLong(), anyBoolean(), anyBoolean(), anyString());
        Mockito.when(createInstructionServiceHelper.getReceivingTypeFromUI(any(InstructionRequest.class))).thenReturn(RxReceivingType.SSCC);
        Mockito.when(instructionPersisterService.saveInstruction(any(Instruction.class))).thenReturn(instruction);

        palletCreateInstructionService.calculateQuantitiesAndPersistIns(instruction, line, request, deliveryDocument, MockRxHttpHeaders.getHeaders());

        Mockito.verify(createInstructionServiceHelper).getReceivingTypeFromUI(any(InstructionRequest.class));

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