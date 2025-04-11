package com.walmart.move.nim.receiving.rx.service.v2.instruction.create;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.RxDeliveryDocumentsMapperV2;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
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

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.KEY_LOT;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.testng.Assert.*;

public class PalletFromMultiSkuCreateInstructionTest {

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
    @InjectMocks private  PalletFromMultiSkuCreateInstruction palletFromMultiSkuCreateInstruction;
    private static Gson gson = new Gson();

    @BeforeClass
    public void initMocks() {
        TenantContext.setFacilityCountryCode("US");
        TenantContext.setFacilityNum(32897);
        MockitoAnnotations.initMocks(this);
        ReflectionTestUtils.setField(palletFromMultiSkuCreateInstruction, "gson", gson);

    }

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testServeInstruction() throws ReceivingException, IOException {
        Instruction instruction = MockInstruction.getInstructionV2("Build Container");
        instruction.setMove(MockInstruction.getMoveData());

        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);



        DeliveryDocument deliveryDocument = getDeliveryDocument();
        DeliveryDocument.GdmCurrentNodeDetail gdmCurrentNodeDetail =
                new DeliveryDocument.GdmCurrentNodeDetail(
                        ssccScanResponse.getContainers(), ssccScanResponse.getAdditionalInfo());
        deliveryDocument.setGdmCurrentNodeDetail(gdmCurrentNodeDetail);
        DeliveryDocumentLine line = MockInstruction.selectDocumentAndDocumentLine(deliveryDocument);
        deliveryDocument.setDeliveryDocumentLines(Arrays.asList(line));

        for(SsccScanResponse.Container c : ssccScanResponse.getContainers()) {
            c.setHints(Collections.singletonList("CASE_PACK_ITEM"));
            c.setSscc(null);
            c.setSerial("testserial");
            c.setGtin("testgtin");
            c.setLotNumber("testLot");
            c.setExpiryDate("261231");
        }


        DataHolder dataHolder = new DataHolder();
        dataHolder.setInstruction(instruction);
        dataHolder.setContainer(ssccScanResponse.getContainers().get(0));
        dataHolder.setDeliveryDocument(deliveryDocument);
        dataHolder.setDeliveryDocumentLine(line);
        dataHolder.setReceivingFlow("FULL-PALLET");
        InstructionRequest request = new InstructionRequest();
        request.setSscc(null);
        request.setDeliveryNumber("1234");
        request.setReceivingType("MULTI_SKU_FLOW ");;
        request.setDeliveryStatus("OPN");
        request.setMessageId("12345");
        request.setIsDSDC(false);
        request.setIsPOCON(false);


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

        request.setScannedDataList(Arrays.asList(scannedData1, scannedData2, scannedData3, scannedData4));

        Mockito.when(rxDeliveryDocumentsSearchHandlerV2.getCurrentAndSiblings(any(InstructionRequest.class), any(HttpHeaders.class), nullable(String.class))).thenReturn(ssccScanResponse);
        Mockito.when(rxDeliveryServiceImpl.getCurrentAndSiblings(any(), any(), anyMap())).thenReturn(ssccScanResponse);
        Mockito.when(rxSlottingServiceImpl.acquireSlotMultiPallets(anyString(), anyInt(), anyList(), anyList(), anyString(), any())).thenReturn(MockInstruction.mockSlottingPalletResponse());
        Mockito.when(instructionHelperService.getReceivedQtyDetailsInEaAndValidate(nullable(String.class), any(), anyString())).thenReturn(new Pair<>(10, 100L));
        Mockito.doNothing().when(createInstructionDataValidator).isNewInstructionCanBeCreated(anyString(), anyInt(), anyInt(), anyLong(), anyBoolean(), anyBoolean(), anyString());
        Mockito.when(createInstructionServiceHelper.getReceivingTypeFromUI(any())).thenReturn(RxReceivingType.SSCC);
        Mockito.when(instructionPersisterService.saveInstruction(any(Instruction.class))).thenReturn(instruction);

        InstructionResponse response = palletFromMultiSkuCreateInstruction.serveInstruction(request, dataHolder, MockRxHttpHeaders.getHeaders());
        Assert.assertNotNull(response);
    }


    private DeliveryDocument getDeliveryDocument()  throws IOException {
        File resource =
                new ClassPathResource("delivery_documents_mock.json")
                        .getFile();
        String mockResponse = new String(Files.readAllBytes(resource.toPath()));
        DeliveryDocument deliveryDocument = gson.fromJson(mockResponse, DeliveryDocument.class);
        return deliveryDocument;
    }






}