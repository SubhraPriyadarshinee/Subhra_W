package com.walmart.move.nim.receiving.rx.service.v2.instruction.create;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.DataHolder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.data.MockRxHttpHeaders;
import com.walmart.move.nim.receiving.rx.mock.MockInstruction;
import com.walmart.move.nim.receiving.rx.service.v2.data.CreateInstructionServiceHelper;
import com.walmart.move.nim.receiving.rx.service.v2.validation.data.CreateInstructionDataValidator;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import org.mockito.*;
import org.springframework.core.io.ClassPathResource;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.testng.Assert.*;

public class MultiSkuCreateInstructionServiceTest {

    @Spy
    private CreateInstructionDataValidator rxValidationsService;
    @Mock private CreateInstructionServiceHelper createInstructionServiceHelper;
    @InjectMocks private MultiSkuCreateInstructionService multiSkuCreateInstructionService;

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
    public void testServeInstruction() throws IOException {
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
        InstructionRequest request = new InstructionRequest();
        request.setSscc(null);
        request.setDeliveryNumber("1234");
        request.setReceivingType("MULTI_SKU_PACKAGE");;

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
        for(SsccScanResponse.Container c : ssccScanResponse.getContainers()) {
            c.setHints(Collections.singletonList("MULTI_SKU_PACKAGE")); // HINT FOR MULTISKU
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
        dataHolder.setDeliveryDocuments(Arrays.asList(deliveryDocument));
        dataHolder.setReceivingFlow("MULTI-SKU");

        InstructionResponse instructionResponse = multiSkuCreateInstructionService.serveInstruction(request, dataHolder, MockRxHttpHeaders.getHeaders());
        Assert.assertNotNull(instructionResponse);

    }

    @Test
    public void testValidateData() throws IOException {
        Instruction instruction = MockInstruction.getInstructionV2("Build Container");
        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);

        for(SsccScanResponse.Container c : ssccScanResponse.getContainers()) {
            c.setHints(Collections.singletonList("MULTI_SKU_PACKAGE")); // HINT FOR MULTISKU
            c.setSscc(null);
            c.setSerial("testserial");
            c.setGtin("testgtin");
            c.setLotNumber("testLot");
            c.setExpiryDate("261231");
            c.setReceivingStatus("Received");
        }


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
        dataHolder.setReceivingFlow("MULTI-SKU");

        // THROW EXCEPTION FOR RECEIVED STATUS
        Mockito.doThrow(new ReceivingBadDataException("BARCODE_ALREADY_RECEIVED", "Scanned barcode has been already Received. Please scan a valid barcode.")).when(rxValidationsService).validateNodesReceivingStatus(dataHolder.getContainer().getReceivingStatus());

        assertThrows(ReceivingBadDataException.class,
                () -> rxValidationsService.
                        validateNodesReceivingStatus(dataHolder.getContainer().getReceivingStatus()));

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

    private static DeliveryDocument getDeliveryDocument()  throws IOException {
        File resource =
                new ClassPathResource("delivery_documents_mock.json")
                        .getFile();
        String mockResponse = new String(Files.readAllBytes(resource.toPath()));
        DeliveryDocument deliveryDocument = gson.fromJson(mockResponse, DeliveryDocument.class);
        return deliveryDocument;
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