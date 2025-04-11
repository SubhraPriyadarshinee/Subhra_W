package com.walmart.move.nim.receiving.rx.service.v2.data;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.validators.InstructionStateValidator;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.DataHolder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ShipmentsContainersV2Request;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rx.builders.RxContainerItemBuilder;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.mock.MockInstruction;
import com.walmart.move.nim.receiving.rx.service.RxDeliveryServiceImpl;
import com.walmart.move.nim.receiving.rx.service.RxInstructionHelperService;
import com.walmart.move.nim.receiving.rx.service.RxInstructionService;
import com.walmart.move.nim.receiving.rx.service.v2.validation.data.UpdateInstructionDataValidator;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.ReportingConstants;
import org.junit.jupiter.api.Assertions;
import org.mockito.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.ParseException;
import java.util.*;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.KEY_LOT;
import static org.mockito.ArgumentMatchers.*;
import static org.testng.Assert.*;

public class UpdateInstructionServiceHelperTest {

    @Mock private RxDeliveryServiceImpl rxDeliveryServiceImpl;
    @Mock private RxInstructionHelperService rxInstructionHelperService;
    @Mock private LPNCacheService lpnCacheService;
    @Mock private ReceiptService receiptService;
    @Mock private ContainerService containerService;
    @Mock
    private RxContainerItemBuilder containerItemBuilder;
    @Mock private ContainerItemService containerItemService;
    @Mock private RxInstructionService rxInstructionService;
    @Mock private InstructionStateValidator instructionStateValidator;
    @Mock private UpdateInstructionDataValidator updateInstructionDataValidator;
    @Mock private InstructionPersisterService instructionPersisterService;
    @Mock private AppConfig appConfig;

    @InjectMocks private UpdateInstructionServiceHelper updateInstructionServiceHelper;
    @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
    private static Gson gson = new Gson();


    @BeforeClass
    public void initMocks() {
        TenantContext.setFacilityCountryCode("US");
        TenantContext.setFacilityNum(32897);
        MockitoAnnotations.initMocks(this);
        ReflectionTestUtils.setField(updateInstructionServiceHelper, "gson", gson);

    }
    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }
    @AfterMethod
    public void tearDown() {
        Mockito.reset(rxDeliveryServiceImpl);
        Mockito.reset(rxInstructionHelperService);
        Mockito.reset(lpnCacheService);
        Mockito.reset(receiptService);
        Mockito.reset(containerService);
        Mockito.reset(containerItemBuilder);
        Mockito.reset(containerItemService);
        Mockito.reset(instructionPersisterService);
        Mockito.reset(rxInstructionService);
        Mockito.reset(instructionStateValidator);
        Mockito.reset(updateInstructionDataValidator);
        Mockito.reset(instructionPersisterService);

    }

    @Test
    public void testGetAdditionalInfoFromDeliveryDoc() {
        Instruction mockInstruction = MockInstruction.getInstructionV2("RxBuildContainer");
        DeliveryDocument deliveryDocument = gson.fromJson(mockInstruction.getDeliveryDocument(), DeliveryDocument.class);
        deliveryDocument.getDeliveryDocumentLines().get(0).getAdditionalInfo().setScannedCaseAttpQty(1000);
        deliveryDocument.getDeliveryDocumentLines().get(0).getAdditionalInfo().setIsEpcisSmartReceivingEnabled(true);
        mockInstruction.setDeliveryDocument(gson.toJson(deliveryDocument));

        ItemData returnedObj = updateInstructionServiceHelper.getAdditionalInfoFromDeliveryDoc(mockInstruction);
        Assert.assertNotNull(returnedObj);
        Assert.assertEquals(true, returnedObj.getIsEpcisSmartReceivingEnabled().booleanValue());


    }

    @Test
    public void testGetValidInstructionCodes() {
        List<String> returnedList = updateInstructionServiceHelper.getValidInstructionCodes();
        Assert.assertTrue(returnedList.size() > 0);
        Assert.assertTrue(returnedList.containsAll(Arrays.asList("RxSerBuildContainer", "RxSerCntrCaseScan",
                "RxSerCntrGtinLotScan", "RxSerBuildUnitScan", "RxSerMultiSkuPallet")));
    }

    @Test
    public void testConvertToGDMContainer() {
        ScannedData scannedData1 = new ScannedData();
        scannedData1.setKey("serial");
        scannedData1.setValue("testserial123");
        scannedData1.setApplicationIdentifier("21");

        ScannedData scannedData2 = new ScannedData();
        scannedData2.setKey("gtin");
        scannedData2.setValue("testgtin123");
        scannedData2.setApplicationIdentifier("01");

        ScannedData scannedData3 = new ScannedData();
        scannedData3.setKey("expiryDate");
        scannedData3.setValue("261231");
        scannedData3.setApplicationIdentifier("17");

        ScannedData scannedData4 = new ScannedData();
        scannedData4.setKey("lot");
        scannedData4.setValue("testlot123");
        scannedData4.setApplicationIdentifier("10");




        // THROW EXCEPTION FOR EMPTY MAP
        Throwable exception = Assertions.assertThrows(ReceivingBadDataException.class,
                () -> updateInstructionServiceHelper.convertToGDMContainer(new HashMap<String, ScannedData>()));
        Assertions.assertEquals(   "Ineligible for receiving. Mandatory ScannedData field is missing.", exception.getMessage());

        // MISSING SERIAL
        Map<String, ScannedData> scannedDataMapMissingSerial  = RxUtils.scannedDataMap(Arrays.asList(scannedData2, scannedData3, scannedData4));

        Throwable exception1 = Assertions.assertThrows(ReceivingBadDataException.class,
                () -> updateInstructionServiceHelper.convertToGDMContainer(scannedDataMapMissingSerial));
        Assertions.assertEquals(   "Ineligible for receiving. Mandatory ScannedData field Serial is missing.", exception1.getMessage());

        // MISSING GTIN
        Map<String, ScannedData> scannedDataMapMissingGtin  = RxUtils.scannedDataMap(Arrays.asList(scannedData1, scannedData3, scannedData4));

        Throwable exception2 = Assertions.assertThrows(ReceivingBadDataException.class,
                () -> updateInstructionServiceHelper.convertToGDMContainer(scannedDataMapMissingGtin));
        Assertions.assertEquals(   "Ineligible for receiving. Mandatory ScannedData field GTIN is missing.",
                exception2.getMessage());

        // MISSING LOT NUMBER
        Map<String, ScannedData> scannedDataMapMissingLot  =
                RxUtils.scannedDataMap(Arrays.asList(scannedData1, scannedData2, scannedData3));

        Throwable exception4 = Assertions.assertThrows(ReceivingBadDataException.class,
                () -> updateInstructionServiceHelper.convertToGDMContainer(scannedDataMapMissingLot));
        Assertions.assertEquals(   "Ineligible for receiving. Mandatory ScannedData field Lot Number is invalid.",
                exception4.getMessage());

        // MISSING EXP DATE
        Map<String, ScannedData> scannedDataMapMissingExpDate  =
                RxUtils.scannedDataMap(Arrays.asList(scannedData1, scannedData2, scannedData4));

        Throwable exception3 = Assertions.assertThrows(ReceivingBadDataException.class,
                () -> updateInstructionServiceHelper.convertToGDMContainer(scannedDataMapMissingExpDate));
        Assertions.assertEquals(   "Ineligible for receiving. Mandatory ScannedData field Expiry Date is missing.",
                exception3.getMessage());

        Map<String, ScannedData> scannedDataMap = RxUtils.scannedDataMap(Arrays.asList(scannedData1, scannedData2, scannedData3, scannedData4));
        SsccScanResponse.Container returnedObj = updateInstructionServiceHelper.convertToGDMContainer(scannedDataMap);
        Assert.assertEquals("testserial123", returnedObj.getSerial());
        Assert.assertEquals("2026-12-31", returnedObj.getExpiryDate());
        Assert.assertEquals("testlot123", returnedObj.getLotNumber());
        Assert.assertEquals("testgtin123", returnedObj.getGtin());
    }

    @Test
    public void testValidateInstructionOwner() {
    }

    @Test
    public void testVerifyScanned2DWithGDMData_mandatory_field_validations() throws IOException {

        String mockSerial = "testSerial123";
        String mockGtin = "testGtin123";
        String mockExpiry = "261231";
        String mockGdmExpiry = "2026-12-31";
        String mockLot = "testLot123";
        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);



        Instruction mockInstruction = MockInstruction.getInstructionV2("RxSerBuildContainer");
        Content content = new Content();
        content.setGtin("00032247267847");
        content.setItemNbr(Long.parseLong("555429067"));
        content.setBaseDivisionCode("WM");
        content.setFinancialReportingGroup("US");
        content.setPurchaseReferenceNumber("4763030227");
        content.setPurchaseReferenceLineNumber(1);
        content.setPurchaseRefType("SSTKU");
        content.setQtyUom(ReceivingConstants.Uom.VNPK);
        content.setVendorPack(6);
        content.setWarehousePack(6);
        content.setOpenQty(80);
        content.setTotalOrderQty(80);
        content.setPalletTie(8);
        content.setPalletHigh(10);
        content.setMaxReceiveQty(80);
        content.setIsConveyable(Boolean.FALSE);
        content.setVendorNumber("482497180");
        content.setProfiledWarehouseArea("OPM");
        content.setWarehouseAreaCode("1");
        content.setWarehouseAreaCodeValue("M");
        content.setCaseUPC("00032247267847");

        mockInstruction.getContainer().setContents(Arrays.asList(content));
        DeliveryDocument mockDeliveryDocument = gson.fromJson(mockInstruction.getDeliveryDocument(), DeliveryDocument.class);
        DeliveryDocumentLine mockDeliveryDocumentLine = mockDeliveryDocument.getDeliveryDocumentLines().get(0);
        mockDeliveryDocumentLine.setGtin(mockGtin);
        mockDeliveryDocumentLine.setLotNumber(mockLot);
        mockDeliveryDocumentLine.setNdc("testNDC");


        SsccScanResponse.Container container = ssccScanResponse.getContainers().get(0);
        container.setGtin(mockGtin);
        container.setSerial(mockSerial);
        container.setExpiryDate(mockGdmExpiry);
        container.setLotNumber(mockLot);
        container.setHints(Arrays.asList("SINGLE_SKU_PACKAGE"));
        container.setParentId("parentId123");
        mockInstruction.setInstructionCreatedByPackageInfo(gson.toJson(container));


        ScannedData scannedDataSerial = new ScannedData();
        scannedDataSerial.setKey("serial");
        scannedDataSerial.setValue(mockSerial);
        scannedDataSerial.setApplicationIdentifier("21");

        ScannedData scannedDataGtin = new ScannedData();
        scannedDataGtin.setKey("gtin");
        scannedDataGtin.setValue(mockGtin);
        scannedDataGtin.setApplicationIdentifier("01");

        ScannedData scannedDataExpiry = new ScannedData();
        scannedDataExpiry.setKey("expiryDate");
        scannedDataExpiry.setValue(mockExpiry);
        scannedDataExpiry.setApplicationIdentifier("17");

        ScannedData scannedDataLot = new ScannedData();
        scannedDataLot.setKey("lot");
        scannedDataLot.setValue(mockLot);
        scannedDataLot.setApplicationIdentifier("10");


        Mockito.doNothing().when(rxInstructionHelperService).saveInstruction(any(Instruction.class));

        scannedDataExpiry.setValue("101125"); // EXPIRY MISMATCH
        scannedDataLot.setValue("randomLot"); // MATCHING LOT WITH GDM
        // THROW EXCEPTION FOR BOTH EXPIRY AND LOT MISMATCH
        Throwable exception = Assertions.assertThrows(ReceivingBadDataException.class,
                () -> updateInstructionServiceHelper.verifyScanned2DWithGDMData(mockInstruction,
                        mockDeliveryDocumentLine.getAdditionalInfo(),
                        RxUtils.scannedDataMap(Arrays.asList(scannedDataGtin, scannedDataSerial, scannedDataExpiry,scannedDataLot)), 3,
                        mockDeliveryDocument,
                        mockDeliveryDocumentLine,
                        true, container));
        Assertions.assertEquals(   "Scanned Expiry Date and LOT do not match with EPCIS data. Please quarantine this freight and submit a problem ticket.", exception.getMessage());

        scannedDataExpiry.setValue("101125"); // EXPIRY MISMATCH
        scannedDataLot.setValue(mockLot); // MATCHING LOT WITH GDM
        // THROW EXCEPTION FOR EXPIRY MISMATCH ONLY
        Throwable exception1 = Assertions.assertThrows(ReceivingBadDataException.class,
                () -> updateInstructionServiceHelper.verifyScanned2DWithGDMData(mockInstruction,
                        mockDeliveryDocumentLine.getAdditionalInfo(),
                        RxUtils.scannedDataMap(Arrays.asList(scannedDataGtin, scannedDataSerial, scannedDataExpiry,scannedDataLot)), 3,
                        mockDeliveryDocument,
                        mockDeliveryDocumentLine,
                        false, container));
        Assertions.assertEquals(   "Scanned expiry date does not match with EPCIS data. Please quarantine this freight and submit a problem ticket.", exception1.getMessage());

        mockInstruction.setInstructionCode("RxSerCntrCaseScan");
        scannedDataExpiry.setValue(mockExpiry); // MATCHING EXPIRY
        scannedDataLot.setValue("randomLot"); // LOT MISMATCH
        // THROW EXCEPTION FOR EXPIRY MISMATCH ONLY
        Throwable exception2 = Assertions.assertThrows(ReceivingBadDataException.class,
                () -> updateInstructionServiceHelper.verifyScanned2DWithGDMData(mockInstruction,
                        mockDeliveryDocumentLine.getAdditionalInfo(),
                        RxUtils.scannedDataMap(Arrays.asList(scannedDataGtin, scannedDataSerial, scannedDataExpiry,scannedDataLot)), 3,
                        mockDeliveryDocument,
                        mockDeliveryDocumentLine,
                        true, container));
        Assertions.assertEquals(   "Scanned LOT does not match with EPCIS data. Please quarantine this freight and submit a problem ticket.", exception2.getMessage());

    }


    @Test
    public void testVerifyScanned2DWithGDMData() throws IOException {

        String mockSerial = "testSerial123";
        String mockGtin = "testGtin123";
        String mockExpiry = "261231";
        String mockGdmExpiry = "2026-12-31";
        String mockLot = "testLot123";
        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);



        Instruction mockInstruction = MockInstruction.getInstructionV2("RxSerBuildContainer");
        Content content = new Content();
        content.setGtin("00032247267847");
        content.setItemNbr(Long.parseLong("555429067"));
        content.setBaseDivisionCode("WM");
        content.setFinancialReportingGroup("US");
        content.setPurchaseReferenceNumber("4763030227");
        content.setPurchaseReferenceLineNumber(1);
        content.setPurchaseRefType("SSTKU");
        content.setQtyUom(ReceivingConstants.Uom.VNPK);
        content.setVendorPack(6);
        content.setWarehousePack(6);
        content.setOpenQty(80);
        content.setTotalOrderQty(80);
        content.setPalletTie(8);
        content.setPalletHigh(10);
        content.setMaxReceiveQty(80);
        content.setIsConveyable(Boolean.FALSE);
        content.setVendorNumber("482497180");
        content.setProfiledWarehouseArea("OPM");
        content.setWarehouseAreaCode("1");
        content.setWarehouseAreaCodeValue("M");
        content.setCaseUPC("00032247267847");

        mockInstruction.getContainer().setContents(Arrays.asList(content));
        DeliveryDocument mockDeliveryDocument = gson.fromJson(mockInstruction.getDeliveryDocument(), DeliveryDocument.class);
        DeliveryDocumentLine mockDeliveryDocumentLine = mockDeliveryDocument.getDeliveryDocumentLines().get(0);
        mockDeliveryDocumentLine.setGtin(mockGtin);
        mockDeliveryDocumentLine.setLotNumber(mockLot);
        mockDeliveryDocumentLine.setNdc("testNDC");


        SsccScanResponse.Container container = ssccScanResponse.getContainers().get(0);
        container.setGtin(mockGtin);
        container.setSerial(mockSerial);
        container.setExpiryDate(mockGdmExpiry);
        container.setLotNumber(mockLot);
        container.setHints(Arrays.asList("SINGLE_SKU_PACKAGE"));
        container.setParentId("parentId123");
        mockInstruction.setInstructionCreatedByPackageInfo(gson.toJson(container));


        ScannedData scannedDataSerial = new ScannedData();
        scannedDataSerial.setKey("serial");
        scannedDataSerial.setValue(mockSerial);
        scannedDataSerial.setApplicationIdentifier("21");

        ScannedData scannedDataGtin = new ScannedData();
        scannedDataGtin.setKey("gtin");
        scannedDataGtin.setValue(mockGtin);
        scannedDataGtin.setApplicationIdentifier("01");

        ScannedData scannedDataExpiry = new ScannedData();
        scannedDataExpiry.setKey("expiryDate");
        scannedDataExpiry.setValue(mockExpiry);
        scannedDataExpiry.setApplicationIdentifier("17");

        ScannedData scannedDataLot = new ScannedData();
        scannedDataLot.setKey("lot");
        scannedDataLot.setValue(mockLot);
        scannedDataLot.setApplicationIdentifier("10");


        Mockito.doNothing().when(rxInstructionHelperService).saveInstruction(any(Instruction.class));

        Assert.assertEquals(0, mockDeliveryDocumentLine.getAdditionalInfo().getAuditCompletedQty());

        Map<ItemData, SsccScanResponse.Container> returnedObj =
                updateInstructionServiceHelper.verifyScanned2DWithGDMData(mockInstruction,
                        mockDeliveryDocumentLine.getAdditionalInfo(),
                        RxUtils.scannedDataMap(Arrays.asList(scannedDataSerial, scannedDataGtin, scannedDataExpiry, scannedDataLot)), 3,
                        mockDeliveryDocument,
                        mockDeliveryDocumentLine,
                        true, container);

        Assert.assertNotNull(returnedObj);
        List<ItemData> itemDataList = new ArrayList<>(returnedObj.keySet());

        Assert.assertEquals(1, mockDeliveryDocumentLine.getAdditionalInfo().getAuditCompletedQty());
        // SHOULD BE 1
        Assert.assertEquals(1, itemDataList.get(0).getAuditCompletedQty());
    }

    @Test
    public void testTotalQuantityValueAfterReceiving() {
        Assert.assertEquals(Optional.of(5), Optional.of(updateInstructionServiceHelper.totalQuantityValueAfterReceiving(3, 2)));
    }

    @Test
    public void testCreatePalletContainer() throws ReceivingException, IOException {
        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);


        ScannedData scannedData1 = new ScannedData();
        scannedData1.setKey("sscc");
        scannedData1.setValue("testsscc");
        scannedData1.setApplicationIdentifier("00");


        Map<String, ScannedData> scannedDataMap = RxUtils.scannedDataMap(Arrays.asList(scannedData1));
        Instruction mockInstruction = MockInstruction.getInstructionV2("RxSerBuildContainer");
        Content content = new Content();
        content.setGtin("00032247267847");
        content.setItemNbr(Long.parseLong("555429067"));
        content.setBaseDivisionCode("WM");
        content.setFinancialReportingGroup("US");
        content.setPurchaseReferenceNumber("4763030227");
        content.setPurchaseReferenceLineNumber(1);
        content.setPurchaseRefType("SSTKU");
        content.setQtyUom(ReceivingConstants.Uom.VNPK);
        content.setVendorPack(6);
        content.setWarehousePack(6);
        content.setOpenQty(80);
        content.setTotalOrderQty(80);
        content.setPalletTie(8);
        content.setPalletHigh(10);
        content.setMaxReceiveQty(80);
        content.setIsConveyable(Boolean.FALSE);
        content.setVendorNumber("482497180");
        content.setProfiledWarehouseArea("OPM");
        content.setWarehouseAreaCode("1");
        content.setWarehouseAreaCodeValue("M");
        content.setCaseUPC("00032247267847");

        mockInstruction.getContainer().setContents(Arrays.asList(content));
        DeliveryDocument mockDeliveryDocument = gson.fromJson(mockInstruction.getDeliveryDocument(), DeliveryDocument.class);
        DeliveryDocumentLine mockDeliveryDocumentLine = mockDeliveryDocument.getDeliveryDocumentLines().get(0);
        mockDeliveryDocumentLine.setGtin("testGtin");
        mockDeliveryDocumentLine.setLotNumber("testLot");
        mockDeliveryDocumentLine.setNdc("testNDC");
        UpdateInstructionRequest mockUpdateInstructionRequest = getUpdateInstructionRequest();
        mockUpdateInstructionRequest.setScannedDataList(Arrays.asList(scannedData1));

        Container container = createChildContainer("parent123", "tracking123", 3);

        List<Container> containers = new ArrayList<>();
        List<ContainerItem> containerItems = new ArrayList<>();
        Mockito.when(appConfig.getPackagedAsUom()).thenReturn(ReceivingConstants.Uom.EACHES);
        Mockito.when(containerService.getContainerByTrackingId(anyString())).thenReturn(container);
        Mockito.when( containerItemBuilder.build(anyString(), any(Instruction.class), any(), anyMap())).thenReturn(container.getContainerItems().get(0));
        Mockito.when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class))).thenReturn("mockLPN");
        Mockito.when(containerService.constructContainer(any(), any(), anyBoolean(), anyBoolean(), any())).thenReturn(container);
        updateInstructionServiceHelper.createPalletContainer(containers, containerItems, mockInstruction,
                mockDeliveryDocument, mockUpdateInstructionRequest,
                MockHttpHeaders.getHeaders(), "sysadmin", ssccScanResponse.getContainers().get(0),
                mockUpdateInstructionRequest.getDeliveryDocumentLines().get(0),
                3);

        Assert.assertTrue(containers.size() > 0);

        Assert.assertEquals("parent123", containers.get(0).getParentTrackingId());
        Assert.assertTrue(containerItems.size() > 0);

    }

    @Test
    public void testCreatePalletContainer_empty_container() throws ReceivingException, IOException {
        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);


        ScannedData scannedData1 = new ScannedData();
        scannedData1.setKey("serial");
        scannedData1.setValue("testserial123");
        scannedData1.setApplicationIdentifier("21");

        ScannedData scannedData2 = new ScannedData();
        scannedData2.setKey("gtin");
        scannedData2.setValue("testgtin123");
        scannedData2.setApplicationIdentifier("01");

        ScannedData scannedData3 = new ScannedData();
        scannedData3.setKey("expiryDate");
        scannedData3.setValue("261231");
        scannedData3.setApplicationIdentifier("17");

        ScannedData scannedData4 = new ScannedData();
        scannedData4.setKey("lot");
        scannedData4.setValue("testlot123");
        scannedData4.setApplicationIdentifier("10");


        Map<String, ScannedData> scannedDataMap = RxUtils.scannedDataMap(Arrays.asList(scannedData1, scannedData2, scannedData3, scannedData4));
        Instruction mockInstruction = MockInstruction.getInstructionV2("RxSerBuildContainer");
        mockInstruction.setContainer(null); // SET CONTAINER TO NULL
        DeliveryDocument mockDeliveryDocument = gson.fromJson(mockInstruction.getDeliveryDocument(), DeliveryDocument.class);
        DeliveryDocumentLine mockDeliveryDocumentLine = mockDeliveryDocument.getDeliveryDocumentLines().get(0);
        mockDeliveryDocumentLine.setGtin("testGtin");
        mockDeliveryDocumentLine.setLotNumber("testLot");
        mockDeliveryDocumentLine.setNdc("testNDC");
        UpdateInstructionRequest mockUpdateInstructionRequest = getUpdateInstructionRequest();
        mockUpdateInstructionRequest.setScannedDataList(Arrays.asList(scannedData1, scannedData2, scannedData3, scannedData4));

        Container container = createChildContainer("parent123", "tracking123", 3);

        List<Container> containers = new ArrayList<>();
        List<ContainerItem> containerItems = new ArrayList<>();
        Mockito.when(appConfig.getPackagedAsUom()).thenReturn(ReceivingConstants.Uom.EACHES);
        Mockito.when(containerService.getContainerByTrackingId(anyString())).thenReturn(container);
        Mockito.when( containerItemBuilder.build(anyString(), any(Instruction.class), any(), anyMap())).thenReturn(container.getContainerItems().get(0));
        Mockito.when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class))).thenReturn("mockLPN");
        Mockito.when(containerService.constructContainer(any(), any(), anyBoolean(), anyBoolean(), any())).thenReturn(container);
        updateInstructionServiceHelper.createPalletContainer(containers, containerItems, mockInstruction,
                mockDeliveryDocument, mockUpdateInstructionRequest,
                MockHttpHeaders.getHeaders(), "sysadmin", ssccScanResponse.getContainers().get(0),
                mockUpdateInstructionRequest.getDeliveryDocumentLines().get(0),
                3);

        Assert.assertTrue(containers.size() > 0);
        Assert.assertEquals("FULL-PALLET", containers.get(0).getRcvgContainerType());
        Assert.assertEquals("parent123", containers.get(0).getParentTrackingId());
        Assert.assertTrue(containerItems.size() > 0);

    }

    @Test
    public void testCreateCaseContainer_RxSerCntrCaseScan() throws IOException, ReceivingException {
        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);


        ScannedData scannedData1 = new ScannedData();
        scannedData1.setKey("serial");
        scannedData1.setValue("testserial123");
        scannedData1.setApplicationIdentifier("21");

        ScannedData scannedData2 = new ScannedData();
        scannedData2.setKey("gtin");
        scannedData2.setValue("testgtin123");
        scannedData2.setApplicationIdentifier("01");

        ScannedData scannedData3 = new ScannedData();
        scannedData3.setKey("expiryDate");
        scannedData3.setValue("261231");
        scannedData3.setApplicationIdentifier("17");

        ScannedData scannedData4 = new ScannedData();
        scannedData4.setKey("lot");
        scannedData4.setValue("testlot123");
        scannedData4.setApplicationIdentifier("10");
        Map<String, ScannedData> scannedDataMap = RxUtils.scannedDataMap(Arrays.asList(scannedData1, scannedData2, scannedData3,scannedData4));
        Instruction mockInstruction = MockInstruction.getInstructionV2("RxSerCntrCaseScan");
        Content content = new Content();
        content.setGtin("00032247267847");
        content.setItemNbr(Long.parseLong("555429067"));
        content.setBaseDivisionCode("WM");
        content.setFinancialReportingGroup("US");
        content.setPurchaseReferenceNumber("4763030227");
        content.setPurchaseReferenceLineNumber(1);
        content.setPurchaseRefType("SSTKU");
        content.setQtyUom(ReceivingConstants.Uom.VNPK);
        content.setVendorPack(6);
        content.setWarehousePack(6);
        content.setOpenQty(80);
        content.setTotalOrderQty(80);
        content.setPalletTie(8);
        content.setPalletHigh(10);
        content.setMaxReceiveQty(80);
        content.setIsConveyable(Boolean.FALSE);
        content.setVendorNumber("482497180");
        content.setProfiledWarehouseArea("OPM");
        content.setWarehouseAreaCode("1");
        content.setWarehouseAreaCodeValue("M");
        content.setCaseUPC("00032247267847");

        mockInstruction.getContainer().setContents(Arrays.asList(content));
        DeliveryDocument mockDeliveryDocument = gson.fromJson(mockInstruction.getDeliveryDocument(), DeliveryDocument.class);
        DeliveryDocumentLine mockDeliveryDocumentLine = mockDeliveryDocument.getDeliveryDocumentLines().get(0);
        mockDeliveryDocumentLine.setGtin("testGtin");
        mockDeliveryDocumentLine.setLotNumber("testLot");
        mockDeliveryDocumentLine.setNdc("testNDC");
        UpdateInstructionRequest mockUpdateInstructionRequest = getUpdateInstructionRequest();
        mockUpdateInstructionRequest.setScannedDataList(Arrays.asList(scannedData1, scannedData2, scannedData3, scannedData4));

        Container container = createChildContainer("parent123", "tracking123", 3);

        List<Container> containers = new ArrayList<>();
        List<ContainerItem> containerItems = new ArrayList<>();
        Mockito.when(appConfig.getPackagedAsUom()).thenReturn(ReceivingConstants.Uom.EACHES);
        Mockito.when( containerItemBuilder.build(anyString(), any(Instruction.class), any(), anyMap())).thenReturn(container.getContainerItems().get(0));
        Mockito.when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class))).thenReturn("mockLPN");
        Mockito.when(containerService.constructContainer(any(), any(), anyBoolean(), anyBoolean(), any())).thenReturn(container);
        String returnValue = updateInstructionServiceHelper.createCaseContainer(containers, containerItems, mockInstruction,
                mockDeliveryDocument, mockUpdateInstructionRequest,
                MockHttpHeaders.getHeaders(), "sysadmin", ssccScanResponse.getContainers().get(0), mockUpdateInstructionRequest.getDeliveryDocumentLines().get(0), scannedDataMap, 3, "" );

        Assert.assertTrue(containers.size() > 0);
        Assert.assertEquals("CASE", containers.get(0).getRcvgContainerType());
        Assert.assertTrue(containerItems.size() > 0);
        Assert.assertEquals("mockLPN", returnValue);


    }

    @Test
    public void testCreateCaseContainer_RxSerCntrCaseScan_Case_revd_with_units() throws IOException, ReceivingException {
        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);


        ScannedData scannedData1 = new ScannedData();
        scannedData1.setKey("serial");
        scannedData1.setValue("testserial123");
        scannedData1.setApplicationIdentifier("21");

        ScannedData scannedData2 = new ScannedData();
        scannedData2.setKey("gtin");
        scannedData2.setValue("testgtin123");
        scannedData2.setApplicationIdentifier("01");

        ScannedData scannedData3 = new ScannedData();
        scannedData3.setKey("expiryDate");
        scannedData3.setValue("261231");
        scannedData3.setApplicationIdentifier("17");

        ScannedData scannedData4 = new ScannedData();
        scannedData4.setKey("lot");
        scannedData4.setValue("testlot123");
        scannedData4.setApplicationIdentifier("10");
        Map<String, ScannedData> scannedDataMap = RxUtils.scannedDataMap(Arrays.asList(scannedData1, scannedData2, scannedData3,scannedData4));
        Instruction mockInstruction = MockInstruction.getInstructionV2("RxSerCntrCaseScan");
        Content content = new Content();
        content.setGtin("00032247267847");
        content.setItemNbr(Long.parseLong("555429067"));
        content.setBaseDivisionCode("WM");
        content.setFinancialReportingGroup("US");
        content.setPurchaseReferenceNumber("4763030227");
        content.setPurchaseReferenceLineNumber(1);
        content.setPurchaseRefType("SSTKU");
        content.setQtyUom(ReceivingConstants.Uom.VNPK);
        content.setVendorPack(6);
        content.setWarehousePack(6);
        content.setOpenQty(80);
        content.setTotalOrderQty(80);
        content.setPalletTie(8);
        content.setPalletHigh(10);
        content.setMaxReceiveQty(80);
        content.setIsConveyable(Boolean.FALSE);
        content.setVendorNumber("482497180");
        content.setProfiledWarehouseArea("OPM");
        content.setWarehouseAreaCode("1");
        content.setWarehouseAreaCodeValue("M");
        content.setCaseUPC("00032247267847");

        mockInstruction.getContainer().setContents(Arrays.asList(content));
        mockInstruction.setReceivingMethod("CASE-RECEIVED-WITH-UNIT-SCANS");
        DeliveryDocument mockDeliveryDocument = gson.fromJson(mockInstruction.getDeliveryDocument(), DeliveryDocument.class);
        DeliveryDocumentLine mockDeliveryDocumentLine = mockDeliveryDocument.getDeliveryDocumentLines().get(0);
        mockDeliveryDocumentLine.setGtin("testGtin");
        mockDeliveryDocumentLine.setLotNumber("testLot");
        mockDeliveryDocumentLine.setNdc("testNDC");
        UpdateInstructionRequest mockUpdateInstructionRequest = getUpdateInstructionRequest();
        mockUpdateInstructionRequest.setScannedDataList(Arrays.asList(scannedData1, scannedData2, scannedData3, scannedData4));

        Container container = createChildContainer("parent123", "tracking123", 3);

        List<Container> containers = new ArrayList<>();
        List<ContainerItem> containerItems = new ArrayList<>();
        Mockito.when(appConfig.getPackagedAsUom()).thenReturn(ReceivingConstants.Uom.EACHES);
        Mockito.when( containerItemBuilder.build(anyString(), any(Instruction.class), any(), anyMap())).thenReturn(container.getContainerItems().get(0));
        Mockito.when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class))).thenReturn("mockLPN");
        Mockito.when(containerService.constructContainer(any(), any(), anyBoolean(), anyBoolean(), any())).thenReturn(container);
        String returnValue = updateInstructionServiceHelper.createCaseContainer(containers, containerItems, mockInstruction,
                mockDeliveryDocument, mockUpdateInstructionRequest,
                MockHttpHeaders.getHeaders(), "sysadmin", ssccScanResponse.getContainers().get(0), mockUpdateInstructionRequest.getDeliveryDocumentLines().get(0), scannedDataMap, 3, "" );

        Assert.assertTrue(containers.size() > 0);
        // SHOULD RETURN PARTIAL CASE
        Assert.assertEquals("PARTIAL-CASE", containers.get(0).getRcvgContainerType());
        Assert.assertTrue(containerItems.size() > 0);
        Assert.assertEquals("mockLPN", returnValue);


    }

    @Test
    public void testCreateCaseContainer_RxSerBuildUnitScan() throws IOException, ReceivingException {
        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);


        ScannedData scannedData1 = new ScannedData();
        scannedData1.setKey("serial");
        scannedData1.setValue("testserial123");
        scannedData1.setApplicationIdentifier("21");

        ScannedData scannedData2 = new ScannedData();
        scannedData2.setKey("gtin");
        scannedData2.setValue("testgtin123");
        scannedData2.setApplicationIdentifier("01");

        ScannedData scannedData3 = new ScannedData();
        scannedData3.setKey("expiryDate");
        scannedData3.setValue("261231");
        scannedData3.setApplicationIdentifier("17");

        ScannedData scannedData4 = new ScannedData();
        scannedData4.setKey("lot");
        scannedData4.setValue("testlot123");
        scannedData4.setApplicationIdentifier("10");
        Map<String, ScannedData> scannedDataMap = RxUtils.scannedDataMap(Arrays.asList(scannedData1, scannedData2, scannedData3,scannedData4));
        Instruction mockInstruction = MockInstruction.getInstructionV2("RxSerBuildUnitScan");
        Content content = new Content();
        content.setGtin("00032247267847");
        content.setItemNbr(Long.parseLong("555429067"));
        content.setBaseDivisionCode("WM");
        content.setFinancialReportingGroup("US");
        content.setPurchaseReferenceNumber("4763030227");
        content.setPurchaseReferenceLineNumber(1);
        content.setPurchaseRefType("SSTKU");
        content.setQtyUom(ReceivingConstants.Uom.VNPK);
        content.setVendorPack(6);
        content.setWarehousePack(6);
        content.setOpenQty(80);
        content.setTotalOrderQty(80);
        content.setPalletTie(8);
        content.setPalletHigh(10);
        content.setMaxReceiveQty(80);
        content.setIsConveyable(Boolean.FALSE);
        content.setVendorNumber("482497180");
        content.setProfiledWarehouseArea("OPM");
        content.setWarehouseAreaCode("1");
        content.setWarehouseAreaCodeValue("M");
        content.setCaseUPC("00032247267847");

        mockInstruction.getContainer().setContents(Arrays.asList(content));
        mockInstruction.setReceivingMethod("CASE-RECEIVED-WITH-UNIT-SCANS");
        DeliveryDocument mockDeliveryDocument = gson.fromJson(mockInstruction.getDeliveryDocument(), DeliveryDocument.class);
        DeliveryDocumentLine mockDeliveryDocumentLine = mockDeliveryDocument.getDeliveryDocumentLines().get(0);
        mockDeliveryDocumentLine.setGtin("testGtin");
        mockDeliveryDocumentLine.setLotNumber("testLot");
        mockDeliveryDocumentLine.setNdc("testNDC");
        UpdateInstructionRequest mockUpdateInstructionRequest = getUpdateInstructionRequest();
        mockUpdateInstructionRequest.setScannedDataList(Arrays.asList(scannedData1, scannedData2, scannedData3, scannedData4));

        Container container = createChildContainer("parent123", "tracking123", 3);
        ssccScanResponse.getContainers().get(0).setGtin("testGtin");
        ssccScanResponse.getContainers().get(0).setSscc("testSSCC");
        ssccScanResponse.getContainers().get(0).setSerial("testSerial");
        ssccScanResponse.getContainers().get(0).setExpiryDate("2026-12-31");
        ssccScanResponse.getContainers().get(0).setLotNumber("testLot");
        List<Container> containers = new ArrayList<>();
        List<ContainerItem> containerItems = new ArrayList<>();
        Mockito.when(appConfig.getPackagedAsUom()).thenReturn(ReceivingConstants.Uom.EACHES);
        Mockito.when( containerItemBuilder.build(anyString(), any(Instruction.class), any(), anyMap())).thenReturn(container.getContainerItems().get(0));
        Mockito.when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class))).thenReturn("mockLPN");
        Mockito.when(tenantSpecificConfigReader.getDCTimeZone(
                any()))
                .thenReturn("UTC");
        Mockito.when(containerService.constructContainer(any(), any(), anyBoolean(), anyBoolean(), any())).thenReturn(container);
        String returnValue = updateInstructionServiceHelper.createCaseContainer(containers, containerItems, mockInstruction,
                mockDeliveryDocument, mockUpdateInstructionRequest,
                MockHttpHeaders.getHeaders(), "sysadmin", ssccScanResponse.getContainers().get(0), mockUpdateInstructionRequest.getDeliveryDocumentLines().get(0), scannedDataMap, 3, "" );

        Assert.assertTrue(containers.size() > 0);
        // SHOULD RETURN PARTIAL CASE
        Assert.assertEquals("PARTIAL-CASE", containers.get(0).getRcvgContainerType());
        Assert.assertTrue(containerItems.size() > 0);
        Assert.assertEquals("mockLPN", returnValue);


    }

    @Test
    public void testCreateCaseContainer_RxSerBuildUnitScan_nonblank_parentId() throws IOException, ReceivingException {
        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);


        ScannedData scannedData1 = new ScannedData();
        scannedData1.setKey("serial");
        scannedData1.setValue("testserial123");
        scannedData1.setApplicationIdentifier("21");

        ScannedData scannedData2 = new ScannedData();
        scannedData2.setKey("gtin");
        scannedData2.setValue("testgtin123");
        scannedData2.setApplicationIdentifier("01");

        ScannedData scannedData3 = new ScannedData();
        scannedData3.setKey("expiryDate");
        scannedData3.setValue("261231");
        scannedData3.setApplicationIdentifier("17");

        ScannedData scannedData4 = new ScannedData();
        scannedData4.setKey("lot");
        scannedData4.setValue("testlot123");
        scannedData4.setApplicationIdentifier("10");
        Map<String, ScannedData> scannedDataMap = RxUtils.scannedDataMap(Arrays.asList(scannedData1, scannedData2, scannedData3,scannedData4));
        Instruction mockInstruction = MockInstruction.getInstructionV2("RxSerBuildUnitScan");
        Content content = new Content();
        content.setGtin("00032247267847");
        content.setItemNbr(Long.parseLong("555429067"));
        content.setBaseDivisionCode("WM");
        content.setFinancialReportingGroup("US");
        content.setPurchaseReferenceNumber("4763030227");
        content.setPurchaseReferenceLineNumber(1);
        content.setPurchaseRefType("SSTKU");
        content.setQtyUom(ReceivingConstants.Uom.VNPK);
        content.setVendorPack(6);
        content.setWarehousePack(6);
        content.setOpenQty(80);
        content.setTotalOrderQty(80);
        content.setPalletTie(8);
        content.setQty(10);
        content.setPalletHigh(10);
        content.setMaxReceiveQty(80);
        content.setIsConveyable(Boolean.FALSE);
        content.setVendorNumber("482497180");
        content.setProfiledWarehouseArea("OPM");
        content.setWarehouseAreaCode("1");
        content.setWarehouseAreaCodeValue("M");
        content.setCaseUPC("00032247267847");

        mockInstruction.getContainer().setContents(Arrays.asList(content));
        mockInstruction.setReceivingMethod("CASE-RECEIVED-WITH-UNIT-SCANS");
        DeliveryDocument mockDeliveryDocument = gson.fromJson(mockInstruction.getDeliveryDocument(), DeliveryDocument.class);
        DeliveryDocumentLine mockDeliveryDocumentLine = mockDeliveryDocument.getDeliveryDocumentLines().get(0);
        mockDeliveryDocumentLine.setGtin("testGtin");
        mockDeliveryDocumentLine.setLotNumber("testLot");
        mockDeliveryDocumentLine.setNdc("testNDC");
        UpdateInstructionRequest mockUpdateInstructionRequest = getUpdateInstructionRequest();
        mockUpdateInstructionRequest.setScannedDataList(Arrays.asList(scannedData1, scannedData2, scannedData3, scannedData4));

        Container container = createChildContainer("parent123", "tracking123", 3);
        ssccScanResponse.getContainers().get(0).setGtin("testGtin");
        ssccScanResponse.getContainers().get(0).setSscc("testSSCC");
        ssccScanResponse.getContainers().get(0).setSerial("testSerial");
        ssccScanResponse.getContainers().get(0).setExpiryDate("2026-12-31");
        ssccScanResponse.getContainers().get(0).setLotNumber("testLot");
        List<Container> containers = new ArrayList<>();
        List<ContainerItem> containerItems = new ArrayList<>();
        Mockito.when(appConfig.getPackagedAsUom()).thenReturn(ReceivingConstants.Uom.EACHES);
        Mockito.when( containerItemBuilder.build(anyString(), any(Instruction.class), any(), anyMap())).thenReturn(container.getContainerItems().get(0));
        Mockito.when(lpnCacheService.getLPNBasedOnTenant(any(HttpHeaders.class))).thenReturn("mockLPN");
        Mockito.when(containerService.constructContainer(any(), any(), anyBoolean(), anyBoolean(), any())).thenReturn(container);
        Mockito.when(containerItemService.findByTrackingId(anyString())).thenReturn(container.getContainerItems());
        String returnValue = updateInstructionServiceHelper.createCaseContainer(containers, containerItems, mockInstruction,
                mockDeliveryDocument, mockUpdateInstructionRequest,
                MockHttpHeaders.getHeaders(), "sysadmin", ssccScanResponse.getContainers().get(0), mockUpdateInstructionRequest.getDeliveryDocumentLines().get(0), scannedDataMap, 3, "testParentId" );

        Assert.assertTrue(containerItems.size() > 0);
        Assert.assertEquals(Optional.of(10), Optional.of(mockInstruction.getContainer().getContents().get(0).getQty()));
        // SHOULD RETURN THE INPUT PARENTID
        Assert.assertEquals("testParentId", returnValue);


    }

    @Test
    public void testCreateScannedContainer() throws IOException, ReceivingException {
        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);


        ScannedData scannedData1 = new ScannedData();
        scannedData1.setKey("serial");
        scannedData1.setValue("testserial123");
        scannedData1.setApplicationIdentifier("21");

        ScannedData scannedData2 = new ScannedData();
        scannedData2.setKey("gtin");
        scannedData2.setValue("testgtin123");
        scannedData2.setApplicationIdentifier("01");

        ScannedData scannedData3 = new ScannedData();
        scannedData3.setKey("expiryDate");
        scannedData3.setValue("261231");
        scannedData3.setApplicationIdentifier("17");

        ScannedData scannedData4 = new ScannedData();
        scannedData4.setKey("lot");
        scannedData4.setValue("testlot123");
        scannedData4.setApplicationIdentifier("10");
        Map<String, ScannedData> scannedDataMap = RxUtils.scannedDataMap(Arrays.asList(scannedData1, scannedData2, scannedData3,scannedData4));
        Instruction mockInstruction = MockInstruction.getInstructionV2("RxSerCntrCaseScan");
        Content content = new Content();
        content.setGtin("00032247267847");
        content.setItemNbr(Long.parseLong("555429067"));
        content.setBaseDivisionCode("WM");
        content.setFinancialReportingGroup("US");
        content.setPurchaseReferenceNumber("4763030227");
        content.setPurchaseReferenceLineNumber(1);
        content.setPurchaseRefType("SSTKU");
        content.setQtyUom(ReceivingConstants.Uom.VNPK);
        content.setVendorPack(6);
        content.setWarehousePack(6);
        content.setOpenQty(80);
        content.setTotalOrderQty(80);
        content.setPalletTie(8);
        content.setPalletHigh(10);
        content.setMaxReceiveQty(80);
        content.setIsConveyable(Boolean.FALSE);
        content.setVendorNumber("482497180");
        content.setProfiledWarehouseArea("OPM");
        content.setWarehouseAreaCode("1");
        content.setWarehouseAreaCodeValue("M");
        content.setCaseUPC("00032247267847");

        mockInstruction.getContainer().setContents(Arrays.asList(content));
        DeliveryDocument mockDeliveryDocument = gson.fromJson(mockInstruction.getDeliveryDocument(), DeliveryDocument.class);
        DeliveryDocumentLine mockDeliveryDocumentLine = mockDeliveryDocument.getDeliveryDocumentLines().get(0);
        mockDeliveryDocumentLine.setGtin("testGtin");
        mockDeliveryDocumentLine.setLotNumber("testLot");
        mockDeliveryDocumentLine.setNdc("testNDC");
        UpdateInstructionRequest mockUpdateInstructionRequest = getUpdateInstructionRequest();
        mockUpdateInstructionRequest.setScannedDataList(Arrays.asList(scannedData1, scannedData2, scannedData3, scannedData4));

        Container container = createChildContainer("parent123", "tracking123", 3);
        List<Container> containers = new ArrayList<>();
        List<ContainerItem> containerItems = new ArrayList<>();

        List<ContainerDetails> containerDetails = new ArrayList<>();

        Mockito.when(containerService.constructContainer(any(), any(), anyBoolean(), anyBoolean(), any())).thenReturn(new Container());
        Mockito.when(containerItemBuilder.build(anyString(), any(), any(), anyMap())).thenReturn(MockInstruction.getContainerItem());
        ContainerItem returnedObj = updateInstructionServiceHelper.createScannedContainer(containers,
                containerItems, mockInstruction, mockDeliveryDocument, mockUpdateInstructionRequest,  "sysadmin",
                mockUpdateInstructionRequest.getDeliveryDocumentLines().get(0),
                RxUtils.scannedDataMap(Arrays.asList(scannedData1, scannedData2,scannedData3,scannedData4)), 1,
                "caseContainerTrackingId", "scannedCaseTrackingId", containerDetails, ssccScanResponse.getContainers().get(0));

        Assert.assertNotNull(returnedObj);
        Assert.assertEquals(Optional.of(1), Optional.of(returnedObj.getQuantity()));
        Assert.assertEquals("EA", returnedObj.getQuantityUOM());
    }

    @Test
    public void testValidateRequest() {
    }

    @Test
    public void testValidateScannedContainer() throws IOException, ReceivingException {
        ScannedData scannedData1 = new ScannedData();
        scannedData1.setKey("serial");
        scannedData1.setValue("testserial123");
        scannedData1.setApplicationIdentifier("21");

        ScannedData scannedData2 = new ScannedData();
        scannedData2.setKey("gtin");
        scannedData2.setValue("testgtin123");
        scannedData2.setApplicationIdentifier("01");

        ScannedData scannedData3 = new ScannedData();
        scannedData3.setKey("expiryDate");
        scannedData3.setValue("261231");
        scannedData3.setApplicationIdentifier("17");

        ScannedData scannedData4 = new ScannedData();
        scannedData4.setKey("lot");
        scannedData4.setValue("testlot123");
        scannedData4.setApplicationIdentifier("10");
        Map<String, ScannedData> scannedDataMap = RxUtils.scannedDataMap(Arrays.asList(scannedData1, scannedData2, scannedData3,scannedData4));
        Instruction mockInstruction = MockInstruction.getInstructionV2("RxBuildContainer");
        DeliveryDocument mockDeliveryDocument = gson.fromJson(mockInstruction.getDeliveryDocument(), DeliveryDocument.class);
        DeliveryDocumentLine mockDeliveryDocumentLine = mockDeliveryDocument.getDeliveryDocumentLines().get(0);
        mockDeliveryDocumentLine.setGtin("testGtin");
        mockDeliveryDocumentLine.setLotNumber("testLot");
        mockDeliveryDocumentLine.setNdc("testNDC");
        UpdateInstructionRequest mockUpdateInstructionRequest = getUpdateInstructionRequest();
        mockUpdateInstructionRequest.setScannedDataList(Arrays.asList(scannedData1, scannedData2, scannedData3, scannedData4));

        Mockito.doNothing().when(updateInstructionDataValidator).validateContainerDoesNotAlreadyExist(anyInt(), anyString(), anyString(), anyInt(), anyString());

        updateInstructionServiceHelper.validateScannedContainer(mockUpdateInstructionRequest, mockInstruction);

        Mockito.verify(updateInstructionDataValidator, Mockito.times(1)).validateContainerDoesNotAlreadyExist(anyInt(), anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    public void testGetCurrentContainer() throws IOException {
        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);


        DeliveryDocument deliveryDocument = new DeliveryDocument();
        deliveryDocument.setGdmCurrentNodeDetail( new DeliveryDocument.GdmCurrentNodeDetail(
                ssccScanResponse.getContainers(), ssccScanResponse.getAdditionalInfo()));

        Assert.assertNull(updateInstructionServiceHelper.getCurrentContainer(null));
        Assert.assertNull(updateInstructionServiceHelper.getCurrentContainer(Arrays.asList(new DeliveryDocument())));
        Assert.assertNotNull(updateInstructionServiceHelper.getCurrentContainer(Arrays.asList(deliveryDocument)));
    }

    @Test
    public void testGetDataForUpdateInstruction() throws ReceivingException {
        ScannedData scannedData1 = new ScannedData();
        scannedData1.setKey("serial");
        scannedData1.setValue("testserial123");
        scannedData1.setApplicationIdentifier("21");

        ScannedData scannedData2 = new ScannedData();
        scannedData2.setKey("gtin");
        scannedData2.setValue("testgtin123");
        scannedData2.setApplicationIdentifier("01");

        ScannedData scannedData3 = new ScannedData();
        scannedData3.setKey("expiryDate");
        scannedData3.setValue("261231");
        scannedData3.setApplicationIdentifier("17");

        ScannedData scannedData4 = new ScannedData();
        scannedData4.setKey("lot");
        scannedData4.setValue("testlot123");
        scannedData4.setApplicationIdentifier("10");
        UpdateInstructionRequest mockUpdateInstructionRequest = getUpdateInstructionRequest();
        mockUpdateInstructionRequest.setScannedDataList(Arrays.asList(scannedData1, scannedData2, scannedData3, scannedData4));
        Instruction mockInstruction = MockInstruction.getInstructionV2("RxSerBuildContainer");
        mockInstruction.setReceivingMethod("TEST-RECEIVING-METHOD");
        Mockito.when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(mockInstruction);
        DataHolder returnedObj = updateInstructionServiceHelper.getDataForUpdateInstruction(1234L, mockUpdateInstructionRequest, "parentTrackingId");
        Assert.assertNotNull(returnedObj);

        Assert.assertEquals("TEST-RECEIVING-METHOD", returnedObj.getReceivingFlow());
    }

    @Test
    public void testIsEpcisSmartReceivingEnabled() {
        Instruction mockInstruction = MockInstruction.getInstructionV2("RxSerBuildContainer");
        DeliveryDocument deliveryDocument = gson.fromJson(mockInstruction.getDeliveryDocument(), DeliveryDocument.class);
        DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
        deliveryDocumentLine.getAdditionalInfo().setIsEpcisSmartReceivingEnabled(true);
        mockInstruction.setDeliveryDocument(gson.toJson(deliveryDocument));
        Assert.assertTrue(updateInstructionServiceHelper.isEpcisSmartReceivingEnabled(mockInstruction));

        // VALID
        mockInstruction.setInstructionCode("RxSerCntrCaseScan");
        Assert.assertTrue(updateInstructionServiceHelper.isEpcisSmartReceivingEnabled(mockInstruction));

        // VALID
        mockInstruction.setInstructionCode("RxSerCntrGtinLotScan");
        Assert.assertTrue(updateInstructionServiceHelper.isEpcisSmartReceivingEnabled(mockInstruction));

        // VALID
        mockInstruction.setInstructionCode("RxSerBuildUnitScan");
        Assert.assertTrue(updateInstructionServiceHelper.isEpcisSmartReceivingEnabled(mockInstruction));

        // VALID
        mockInstruction.setInstructionCode("RxSerMultiSkuPallet");
        Assert.assertTrue(updateInstructionServiceHelper.isEpcisSmartReceivingEnabled(mockInstruction));

        // INVALID
        mockInstruction.setInstructionCode("someRandom");
        Assert.assertFalse(updateInstructionServiceHelper.isEpcisSmartReceivingEnabled(mockInstruction));

        // INVALID
        deliveryDocumentLine.getAdditionalInfo().setIsEpcisSmartReceivingEnabled(false);
        mockInstruction.setDeliveryDocument(gson.toJson(deliveryDocument));
        mockInstruction.setInstructionCode("RxSerMultiSkuPallet");
        Assert.assertFalse(updateInstructionServiceHelper.isEpcisSmartReceivingEnabled(mockInstruction));
    }

    @Test
    public void testCallGDMCurrentNodeApi() throws IOException {
        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);


        ScannedData scannedData1 = new ScannedData();
        scannedData1.setKey("serial");
        scannedData1.setValue("testserial123");
        scannedData1.setApplicationIdentifier("21");

        ScannedData scannedData2 = new ScannedData();
        scannedData2.setKey("gtin");
        scannedData2.setValue("testgtin123");
        scannedData2.setApplicationIdentifier("01");

        ScannedData scannedData3 = new ScannedData();
        scannedData3.setKey("expiryDate");
        scannedData3.setValue("261231");
        scannedData3.setApplicationIdentifier("17");

        ScannedData scannedData4 = new ScannedData();
        scannedData4.setKey("lot");
        scannedData4.setValue("testlot123");
        scannedData4.setApplicationIdentifier("10");
        Map<String, ScannedData> scannedDataMap = RxUtils.scannedDataMap(Arrays.asList(scannedData1, scannedData2, scannedData3,scannedData4));
        Instruction mockInstruction = MockInstruction.getInstructionV2("RxSerBuildUnitScan");
        Content content = new Content();
        content.setGtin("00032247267847");
        content.setItemNbr(Long.parseLong("555429067"));
        content.setBaseDivisionCode("WM");
        content.setFinancialReportingGroup("US");
        content.setPurchaseReferenceNumber("4763030227");
        content.setPurchaseReferenceLineNumber(1);
        content.setPurchaseRefType("SSTKU");
        content.setQtyUom(ReceivingConstants.Uom.VNPK);
        content.setVendorPack(6);
        content.setWarehousePack(6);
        content.setOpenQty(80);
        content.setTotalOrderQty(80);
        content.setPalletTie(8);
        content.setQty(10);
        content.setPalletHigh(10);
        content.setMaxReceiveQty(80);
        content.setIsConveyable(Boolean.FALSE);
        content.setVendorNumber("482497180");
        content.setProfiledWarehouseArea("OPM");
        content.setWarehouseAreaCode("1");
        content.setWarehouseAreaCodeValue("M");
        content.setCaseUPC("00032247267847");

        mockInstruction.getContainer().setContents(Arrays.asList(content));
        mockInstruction.setReceivingMethod("CASE-RECEIVED-WITH-UNIT-SCANS");
        DeliveryDocument mockDeliveryDocument = gson.fromJson(mockInstruction.getDeliveryDocument(), DeliveryDocument.class);
        DeliveryDocumentLine mockDeliveryDocumentLine = mockDeliveryDocument.getDeliveryDocumentLines().get(0);
        mockDeliveryDocumentLine.setGtin("testGtin");
        mockDeliveryDocumentLine.setLotNumber("testLot");
        mockDeliveryDocumentLine.setNdc("testNDC");
        UpdateInstructionRequest mockUpdateInstructionRequest = getUpdateInstructionRequest();
        mockUpdateInstructionRequest.setScannedDataList(Arrays.asList(scannedData1, scannedData2, scannedData3, scannedData4));

        Container container = createChildContainer("parent123", "tracking123", 3);
        ssccScanResponse.getContainers().get(0).setGtin("testGtin");
        ssccScanResponse.getContainers().get(0).setSscc("testSSCC");
        ssccScanResponse.getContainers().get(0).setSerial("testSerial");
        ssccScanResponse.getContainers().get(0).setExpiryDate("2026-12-31");
        ssccScanResponse.getContainers().get(0).setLotNumber("testLot");

        DataHolder dataHolder = new DataHolder();
        dataHolder.setInstruction(mockInstruction);
        dataHolder.setDeliveryDocumentLine(mockDeliveryDocumentLine);
        dataHolder.setReceivingFlow("PALLET");
        dataHolder.setDeliveryDocument(mockDeliveryDocument);
        dataHolder.setDeliveryDocuments(Arrays.asList(mockDeliveryDocument));

        ssccScanResponse.getContainers().get(0).setSerial("testSerial");

        Mockito.when(rxDeliveryServiceImpl.getCurrentNode(any(ShipmentsContainersV2Request.class), any(HttpHeaders.class), anyMap())).thenReturn(ssccScanResponse);

        updateInstructionServiceHelper.callGDMCurrentNodeApi(mockUpdateInstructionRequest, MockHttpHeaders.getHeaders(), mockInstruction, dataHolder);
        Assert.assertNotNull(dataHolder.getGdmResponseForScannedData());
        Assert.assertEquals("testSerial", dataHolder.getGdmResponseForScannedData().getSerial());


    }

    private static DeliveryDocument getDeliveryDocument()  throws IOException {
        File resource =
                new ClassPathResource("delivery_documents_mock.json")
                        .getFile();
        String mockResponse = new String(Files.readAllBytes(resource.toPath()));
        DeliveryDocument deliveryDocument = gson.fromJson(mockResponse, DeliveryDocument.class);
        return deliveryDocument;
    }


    private  UpdateInstructionRequest getUpdateInstructionRequest() {
        DocumentLine documentLine = new DocumentLine();
        List<DocumentLine> documentLines = new ArrayList<>();
        UpdateInstructionRequest updateInstructionRequest = new UpdateInstructionRequest();

        updateInstructionRequest.setDeliveryNumber(Long.valueOf("21809792"));
        updateInstructionRequest.setProblemTagId(null);
        updateInstructionRequest.setDoorNumber("101");
        updateInstructionRequest.setContainerType("Chep Pallet");
        documentLine.setPurchaseReferenceNumber("056435417");
        documentLine.setPurchaseReferenceLineNumber(1);
        documentLine.setPurchaseRefType("SSTKU");
        documentLine.setQuantity(2);
        documentLine.setQuantityUOM(ReceivingConstants.Uom.EACHES);
        documentLine.setExpectedQty(Long.valueOf("1"));
        documentLine.setTotalPurchaseReferenceQty(1);
        documentLine.setMaxOverageAcceptQty(Long.valueOf("0"));
        documentLine.setMaxReceiveQty(Long.valueOf("1"));
        documentLine.setGtin("00000001234");
        documentLine.setItemNumber(Long.valueOf("557959102"));
        documentLine.setMaxOverageAcceptQty(null);
        documentLine.setPoDCNumber("32612");
        documentLine.setVnpkQty(6);
        documentLine.setWhpkQty(6);
        documentLine.setVnpkWgtQty(13.00f);
        documentLine.setVnpkWgtUom("LB");
        documentLine.setWarehouseMinLifeRemainingToReceive(30);
        documentLine.setRotateDate(new Date());
        documentLine.setPromoBuyInd("N");
        documentLine.setDescription("testDescription");
        documentLines.add(documentLine);
        updateInstructionRequest.setDeliveryDocumentLines(documentLines);

        return updateInstructionRequest;
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

}