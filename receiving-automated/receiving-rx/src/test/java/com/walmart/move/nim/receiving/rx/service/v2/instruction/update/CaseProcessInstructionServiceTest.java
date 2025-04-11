package com.walmart.move.nim.receiving.rx.service.v2.instruction.update;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.validators.InstructionStateValidator;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.DataHolder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rx.builders.RxReceiptsBuilder;
import com.walmart.move.nim.receiving.rx.config.RxManagedConfig;
import com.walmart.move.nim.receiving.rx.mock.MockInstruction;
import com.walmart.move.nim.receiving.rx.service.RxDeliveryServiceImpl;
import com.walmart.move.nim.receiving.rx.service.RxInstructionHelperService;
import com.walmart.move.nim.receiving.rx.service.RxInstructionService;
import com.walmart.move.nim.receiving.rx.service.v2.data.UpdateInstructionServiceHelper;
import com.walmart.move.nim.receiving.rx.service.v2.validation.data.UpdateInstructionDataValidator;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
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

import static org.mockito.ArgumentMatchers.*;
import static org.testng.Assert.*;

public class CaseProcessInstructionServiceTest {

    @Mock private InstructionStateValidator instructionStateValidator;
    @Mock private UpdateInstructionServiceHelper updateInstructionServiceHelper;
    @Mock private RxInstructionService rxInstructionService;
    @Mock private RxDeliveryServiceImpl rxDeliveryServiceImpl;
    @Mock private UpdateInstructionDataValidator updateInstructionDataValidator;
    @Mock private TenantSpecificConfigReader configUtils;
    @Mock private RxInstructionHelperService rxInstructionHelperService;
    @Mock private ReceiptService receiptService;
    @Mock private RxManagedConfig rxManagedConfig;
    @Mock private RxReceiptsBuilder rxReceiptsBuilder;
    @InjectMocks private CaseProcessInstructionService caseProcessInstructionService;

    private static Gson gson = new Gson();

    @BeforeClass
    public void initMocks() {
        TenantContext.setFacilityCountryCode("US");
        TenantContext.setFacilityNum(32897);
        MockitoAnnotations.initMocks(this);
        ReflectionTestUtils.setField(caseProcessInstructionService, "gson", gson);

    }

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testValidateUserEnteredQty() throws IOException, ReceivingException {
        Instruction instruction = MockInstruction.getInstructionV2("RxSerCntrCaseScan");
        instruction.setMove(MockInstruction.getMoveData());
        DeliveryDocument deliveryDocument = gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
        deliveryDocument.getDeliveryDocumentLines().get(0).getAdditionalInfo().setScannedCaseAttpQty(1000);
        instruction.setDeliveryDocument(gson.toJson(deliveryDocument));

        UpdateInstructionRequest updateInstructionRequestUserEntered = getUpdateInstructionRequest("update_instruction_request_user_entered.json");
        ScannedData scannedData = new ScannedData();
        scannedData.setKey("EA");
        scannedData.setValue("10");

        updateInstructionRequestUserEntered.setUserEnteredDataList(Arrays.asList(scannedData));

        Mockito.doNothing().when(rxInstructionHelperService).saveInstruction(any(Instruction.class));
        Mockito.when(configUtils.getCcmValue(anyInt(), anyString(), anyString())).thenReturn("10");

        InstructionResponse response = caseProcessInstructionService.validateUserEnteredQty(updateInstructionRequestUserEntered, instruction);
        Assert.assertNotNull(response);
        DeliveryDocument deliveryDocument1 = gson.fromJson(response.getInstruction().getDeliveryDocument(), DeliveryDocument.class);

        Assert.assertTrue(deliveryDocument1.getDeliveryDocumentLines().get(0).getAdditionalInfo().getQtyValidationDone());
        Assert.assertEquals(deliveryDocument1.getDeliveryDocumentLines().get(0).getAdditionalInfo().getAuditQty(), 10);
    }

    @Test
    public void testProcessUpdateInstruction_in_za() throws IOException, ReceivingException {
        Instruction instruction = MockInstruction.getInstructionV2("RxSerCntrCaseScan");
        instruction.setMove(MockInstruction.getMoveData());
        DeliveryDocument deliveryDocument = gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
        deliveryDocument.getDeliveryDocumentLines().get(0).getAdditionalInfo().setScannedCaseAttpQty(1000);
        instruction.setDeliveryDocument(gson.toJson(deliveryDocument));

        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);

        Map<ItemData, SsccScanResponse.Container> selectedSerialInfoList = new HashMap<>();
        selectedSerialInfoList.put(deliveryDocument.getDeliveryDocumentLines().get(0).getAdditionalInfo(), ssccScanResponse.getContainers().get(0));

        UpdateInstructionRequest updateInstructionRequest = getUpdateInstructionRequest("update_instruction_request_v2.json");



        //DeliveryDocumentLine line = selectDocumentAndDocumentLine(deliveryDocument);
        //deliveryDocument.setDeliveryDocumentLines(Arrays.asList(line));


        DataHolder dataHolder = new DataHolder();
        dataHolder.setInstruction(instruction);
        dataHolder.setContainer(ssccScanResponse.getContainers().get(0));
        dataHolder.setDeliveryDocument(deliveryDocument);
        dataHolder.setDeliveryDocumentLine(deliveryDocument.getDeliveryDocumentLines().get(0));
        dataHolder.setGdmResponseForScannedData(ssccScanResponse.getContainers().get(0));
        dataHolder.setReceivingFlow("CASE");

        Mockito.doNothing().when(updateInstructionServiceHelper).validateScannedContainer(any(UpdateInstructionRequest.class), any(Instruction.class));
        Mockito.doNothing().when(updateInstructionServiceHelper).callGDMCurrentNodeApi(any(UpdateInstructionRequest.class), any(HttpHeaders.class), any(Instruction.class), any(DataHolder.class));
        Mockito.doNothing().when(updateInstructionDataValidator).validateOpenReceivingStatus(any(SsccScanResponse.Container.class));
        Mockito.doNothing().when(updateInstructionDataValidator).validateInstructionGtinMatchesCurrentNodeApiGtin(any(Instruction.class), any(SsccScanResponse.Container.class));
        Mockito.doNothing().when(updateInstructionDataValidator).validateBarcodeNotAlreadyScanned(any(SsccScanResponse.Container.class), any(SsccScanResponse.Container.class));
        Mockito.when(updateInstructionServiceHelper.verifyScanned2DWithGDMData(any(Instruction.class), any(ItemData.class), anyMap(), anyInt(), any(DeliveryDocument.class), any(DeliveryDocumentLine.class), anyBoolean(), any(SsccScanResponse.Container.class))).thenReturn(selectedSerialInfoList);
        Mockito.when(updateInstructionServiceHelper.getAdditionalInfoFromDeliveryDoc(any(Instruction.class))).thenReturn(deliveryDocument.getDeliveryDocumentLines().get(0).getAdditionalInfo());
        Mockito.when(updateInstructionServiceHelper.totalQuantityValueAfterReceiving(anyInt(), anyInt())).thenReturn(2);
        Mockito.doNothing().when(updateInstructionDataValidator).verifyIfCaseCanBeReceived(anyLong(), anyInt(), any(DeliveryDocumentLine.class), anyInt());
        caseProcessInstructionService.processUpdateInstruction(updateInstructionRequest, dataHolder, deliveryDocument, deliveryDocument.getDeliveryDocumentLines().get(0), true, MockHttpHeaders.getHeaders());
        Mockito.verify(updateInstructionServiceHelper, Mockito.times(1)).validateScannedContainer(any(UpdateInstructionRequest.class), any(Instruction.class));;






    }


    @Test
    public void testProcessUpdateInstruction_in_ea() throws IOException, ReceivingException {
        Instruction instruction = MockInstruction.getInstructionV2("RxSerCntrCaseScan");
        instruction.setMove(MockInstruction.getMoveData());
        DeliveryDocument deliveryDocument = gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
        deliveryDocument.getDeliveryDocumentLines().get(0).getAdditionalInfo().setScannedCaseAttpQty(1000);
        instruction.setDeliveryDocument(gson.toJson(deliveryDocument));

        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);

        Map<ItemData, SsccScanResponse.Container> selectedSerialInfoList = new HashMap<>();
        selectedSerialInfoList.put(deliveryDocument.getDeliveryDocumentLines().get(0).getAdditionalInfo(), ssccScanResponse.getContainers().get(0));

        UpdateInstructionRequest updateInstructionRequest = getUpdateInstructionRequest("update_instruction_request_v2.json");
        updateInstructionRequest.getDeliveryDocumentLines().get(0).setQuantityUOM("EA");


        //DeliveryDocumentLine line = selectDocumentAndDocumentLine(deliveryDocument);
        //deliveryDocument.setDeliveryDocumentLines(Arrays.asList(line));


        DataHolder dataHolder = new DataHolder();
        dataHolder.setInstruction(instruction);
        dataHolder.setContainer(ssccScanResponse.getContainers().get(0));
        dataHolder.setDeliveryDocument(deliveryDocument);
        dataHolder.setDeliveryDocumentLine(deliveryDocument.getDeliveryDocumentLines().get(0));
        dataHolder.setGdmResponseForScannedData(ssccScanResponse.getContainers().get(0));
        dataHolder.setReceivingFlow("CASE");

        Mockito.doNothing().when(updateInstructionServiceHelper).validateScannedContainer(any(UpdateInstructionRequest.class), any(Instruction.class));
        Mockito.doNothing().when(updateInstructionServiceHelper).callGDMCurrentNodeApi(any(UpdateInstructionRequest.class), any(HttpHeaders.class), any(Instruction.class), any(DataHolder.class));
        Mockito.doNothing().when(updateInstructionDataValidator).validateOpenReceivingStatus(any(SsccScanResponse.Container.class));
        Mockito.doNothing().when(updateInstructionDataValidator).validateInstructionGtinMatchesCurrentNodeApiGtin(any(Instruction.class), any(SsccScanResponse.Container.class));
        Mockito.doNothing().when(updateInstructionDataValidator).validateBarcodeNotAlreadyScanned(any(SsccScanResponse.Container.class), any(SsccScanResponse.Container.class));
        Mockito.when(updateInstructionServiceHelper.verifyScanned2DWithGDMData(any(Instruction.class), any(ItemData.class), anyMap(), anyInt(), any(DeliveryDocument.class), any(DeliveryDocumentLine.class), anyBoolean(), any(SsccScanResponse.Container.class))).thenReturn(selectedSerialInfoList);
        Mockito.when(updateInstructionServiceHelper.getAdditionalInfoFromDeliveryDoc(any(Instruction.class))).thenReturn(deliveryDocument.getDeliveryDocumentLines().get(0).getAdditionalInfo());
        Mockito.when(updateInstructionServiceHelper.totalQuantityValueAfterReceiving(anyInt(), anyInt())).thenReturn(2);
        Mockito.doNothing().when(updateInstructionDataValidator).verifyIfCaseCanBeReceived(anyLong(), anyInt(), any(DeliveryDocumentLine.class), anyInt());
        caseProcessInstructionService.processUpdateInstruction(updateInstructionRequest, dataHolder, deliveryDocument, deliveryDocument.getDeliveryDocumentLines().get(0), true, MockHttpHeaders.getHeaders());
        Mockito.verify(updateInstructionServiceHelper, Mockito.times(1)).validateScannedContainer(any(UpdateInstructionRequest.class), any(Instruction.class));;

        caseProcessInstructionService.processUpdateInstruction(updateInstructionRequest, dataHolder, deliveryDocument, deliveryDocument.getDeliveryDocumentLines().get(0), true, MockHttpHeaders.getHeaders());
        Mockito.verify(updateInstructionServiceHelper, Mockito.times(2)).validateScannedContainer(any(UpdateInstructionRequest.class), any(Instruction.class));
    }

    @Test
    public void testBuildContainerAndUpdateInstruction() throws ReceivingException, IOException {
        Instruction instruction = MockInstruction.getInstructionV2("RxSerCntrCaseScan");
        instruction.setMove(MockInstruction.getMoveData());
        DeliveryDocument deliveryDocument = gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
        deliveryDocument.getDeliveryDocumentLines().get(0).getAdditionalInfo().setScannedCaseAttpQty(1000);
        instruction.setDeliveryDocument(gson.toJson(deliveryDocument));

        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);

        Map<ItemData, SsccScanResponse.Container> selectedSerialInfoList = new HashMap<>();
        selectedSerialInfoList.put(deliveryDocument.getDeliveryDocumentLines().get(0).getAdditionalInfo(), ssccScanResponse.getContainers().get(0));

        UpdateInstructionRequest updateInstructionRequest = getUpdateInstructionRequest("update_instruction_request_v2.json");
        updateInstructionRequest.getDeliveryDocumentLines().get(0).setQuantityUOM("EA");


        //DeliveryDocumentLine line = selectDocumentAndDocumentLine(deliveryDocument);
        //deliveryDocument.setDeliveryDocumentLines(Arrays.asList(line));
        instruction.setInstructionCreatedByPackageInfo(gson.toJson(ssccScanResponse.getContainers().get(0)));

        DataHolder dataHolder = new DataHolder();
        dataHolder.setInstruction(instruction);
        dataHolder.setContainer(ssccScanResponse.getContainers().get(0));
        dataHolder.setDeliveryDocument(deliveryDocument);
        dataHolder.setDeliveryDocumentLine(deliveryDocument.getDeliveryDocumentLines().get(0));
        dataHolder.setGdmResponseForScannedData(ssccScanResponse.getContainers().get(0));
        dataHolder.setReceivingFlow("CASE");
        dataHolder.setQuantityToBeReceivedInEaches(10);
        dataHolder.setQuantityToBeReceivedInVNPK(10);

        ContainerItem mockContainerItem = new ContainerItem();
        mockContainerItem.setSerial("mockSerial");
        mockContainerItem.setLotNumber("mockLotNumber");


        Mockito.when(updateInstructionServiceHelper.createCaseContainer(
                anyList(), anyList(), any(Instruction.class),
                any(DeliveryDocument.class), any(UpdateInstructionRequest.class),
                any(HttpHeaders.class), anyString(), any(SsccScanResponse.Container.class), any(DocumentLine.class), anyMap(), anyInt(), anyString() )).thenReturn("testCaseTrackingId");
        Mockito.when(updateInstructionServiceHelper.generateTrackingId(any(HttpHeaders.class))).thenReturn("mockParentTrackingId");
        Mockito.when(updateInstructionServiceHelper.
                createScannedContainer(anyList(), anyList(),  any(Instruction.class), any(DeliveryDocument.class), any(UpdateInstructionRequest.class),
                        anyString(), any(DocumentLine.class), anyMap(), anyInt(),
                        nullable(String.class), anyString(), anyList(), any(SsccScanResponse.Container.class))).thenReturn(mockContainerItem);
        Mockito.when(rxReceiptsBuilder.buildReceipts(any(Instruction.class), any(UpdateInstructionRequest.class), anyString(), anyInt(), anyInt(), anyString())).thenReturn(new ArrayList<Receipt>());
        Mockito.doNothing().when(updateInstructionServiceHelper).validateScannedContainer(any(UpdateInstructionRequest.class), any(Instruction.class));
        Mockito.doNothing().when(updateInstructionServiceHelper).callGDMCurrentNodeApi(any(UpdateInstructionRequest.class), any(HttpHeaders.class), any(Instruction.class), any(DataHolder.class));
        Mockito.doNothing().when(updateInstructionDataValidator).validateOpenReceivingStatus(any(SsccScanResponse.Container.class));
        Mockito.doNothing().when(updateInstructionDataValidator).validateInstructionGtinMatchesCurrentNodeApiGtin(any(Instruction.class), any(SsccScanResponse.Container.class));
        Mockito.doNothing().when(updateInstructionDataValidator).validateBarcodeNotAlreadyScanned(any(SsccScanResponse.Container.class), any(SsccScanResponse.Container.class));
        Mockito.when(updateInstructionServiceHelper.verifyScanned2DWithGDMData(any(Instruction.class), any(ItemData.class), anyMap(), anyInt(), any(DeliveryDocument.class), any(DeliveryDocumentLine.class), anyBoolean(), any(SsccScanResponse.Container.class))).thenReturn(selectedSerialInfoList);
        Mockito.when(updateInstructionServiceHelper.getAdditionalInfoFromDeliveryDoc(any(Instruction.class))).thenReturn(deliveryDocument.getDeliveryDocumentLines().get(0).getAdditionalInfo());
        Mockito.when(updateInstructionServiceHelper.totalQuantityValueAfterReceiving(anyInt(), anyInt())).thenReturn(2);
        Mockito.doNothing().when(updateInstructionDataValidator).verifyIfCaseCanBeReceived(anyLong(), anyInt(), any(DeliveryDocumentLine.class), anyInt());

        InstructionResponseImplNew instructionResponseImplNew = caseProcessInstructionService.buildContainerAndUpdateInstruction(updateInstructionRequest, dataHolder, "parenttracking123", MockHttpHeaders.getHeaders());
        Assert.assertNotNull(instructionResponseImplNew);
    }

    private DeliveryDocument getDeliveryDocument()  throws IOException {
        File resource =
                new ClassPathResource("delivery_documents_mock.json")
                        .getFile();
        String mockResponse = new String(Files.readAllBytes(resource.toPath()));
        DeliveryDocument deliveryDocument = gson.fromJson(mockResponse, DeliveryDocument.class);
        return deliveryDocument;
    }

    private UpdateInstructionRequest getUpdateInstructionRequest(String fileName)  throws IOException {
        File resource =
                new ClassPathResource(fileName)
                        .getFile();
        String mockResponse = new String(Files.readAllBytes(resource.toPath()));
        UpdateInstructionRequest updateInstructionRequest = gson.fromJson(mockResponse, UpdateInstructionRequest.class);
        return updateInstructionRequest;
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