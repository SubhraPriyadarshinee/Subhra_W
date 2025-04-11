package com.walmart.move.nim.receiving.rx.service.v2.validation.data;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.gdm.v3.DataHolder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.core.service.ContainerItemService;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.mock.MockInstruction;
import com.walmart.move.nim.receiving.rx.service.v2.data.UpdateInstructionServiceHelper;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import org.junit.jupiter.api.Assertions;
import org.mockito.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.testng.Assert.*;

public class UpdateInstructionDataValidatorTest {

    @Mock private ContainerService containerService;
    @Mock private UpdateInstructionServiceHelper updateInstructionServiceHelper;
    @Mock private RxUtils rxUtils;
    @Mock private ContainerItemService containerItemService;
    @InjectMocks private UpdateInstructionDataValidator updateInstructionDataValidator;

    private Gson gson = new Gson();

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

    @AfterMethod
    public void cleanUp() {
        Mockito.reset(containerService);
        Mockito.reset(updateInstructionServiceHelper);
        Mockito.reset(containerItemService);

    }

    @Test
    public void testValidateBarcodeNotAlreadyScanned() throws IOException {
        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);
        ssccScanResponse.getContainers().get(0).setSscc(null);
        ssccScanResponse.getContainers().get(0).setExpiryDate("2026-12-31");
        ssccScanResponse.getContainers().get(0).setLotNumber("gdmLot");
        ssccScanResponse.getContainers().get(0).setGtin("1234");
        ssccScanResponse.getContainers().get(0).setSerial("testSerial");

        SsccScanResponse.Container containerFromUI = new SsccScanResponse.Container();
        containerFromUI.setSerial("testSerial");
        containerFromUI.setExpiryDate("2026-12-31");
        containerFromUI.setLotNumber("gdmLot");
        containerFromUI.setGtin("1234");

        Throwable exception = Assertions.assertThrows(RuntimeException.class,
                () -> updateInstructionDataValidator.validateBarcodeNotAlreadyScanned(ssccScanResponse.getContainers().get(0), containerFromUI));

        Assertions.assertEquals(   "Barcode has already been scanned.", exception.getMessage());
    }

    @Test
    public void testValidateScannedCaseBelongsToPallet() throws IOException {
        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);
        ssccScanResponse.getContainers().get(0).setTopLevelContainerId("topLevel123");

        SsccScanResponse.Container containerFromUI = new SsccScanResponse.Container();
        containerFromUI.setSerial("testSerial");
        containerFromUI.setExpiryDate("2026-12-31");
        containerFromUI.setLotNumber("gdmLot");
        containerFromUI.setGtin("1234");
        containerFromUI.setTopLevelContainerId("someRandomPalletId");

        Throwable exception = Assertions.assertThrows(RuntimeException.class,
                () -> updateInstructionDataValidator.validateScannedCaseBelongsToPallet(ssccScanResponse.getContainers().get(0), containerFromUI));

        Assertions.assertEquals(   "Scanned case does not belong to this pallet.", exception.getMessage());

    }

    @Test
    public void testValidateScannedUnitBelongsToCaseForPartialCaseReceiving() throws IOException {
        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);

        ssccScanResponse.getContainers().get(0).setParentId(null);
        Instruction instruction = Mockito.mock(Instruction.class);
        Mockito.when(instruction.getInstructionCreatedByPackageInfo())
                .thenReturn("{\"parentId\":\"someRandomParentId2\"}");
        updateInstructionDataValidator.validateScannedUnitBelongsToCase(instruction, ssccScanResponse.getContainers().get(0));

        ssccScanResponse.getContainers().get(0).setParentId("someRandomParentId1");
        instruction = Mockito.mock(Instruction.class);
        Mockito.when(instruction.getInstructionCreatedByPackageInfo())
                .thenReturn("{}");
        updateInstructionDataValidator.validateScannedUnitBelongsToCase(instruction, ssccScanResponse.getContainers().get(0));

        ssccScanResponse.getContainers().get(0).setParentId("someRandomParentId1");
        instruction = Mockito.mock(Instruction.class);
        Mockito.when(instruction.getInstructionCreatedByPackageInfo())
                .thenReturn("{\"parentId\":\"someDifferentParentId2\"}");
        Instruction finalInstruction = instruction;
        Throwable exception = Assertions.assertThrows(RuntimeException.class,
                () -> updateInstructionDataValidator.validateScannedUnitBelongsToCase(finalInstruction, ssccScanResponse.getContainers().get(0)));
        Assertions.assertEquals("Scanned unit does not belong to this case.", exception.getMessage());
    }

    @Test
    public void testValidateScannedUnitBelongsToCaseForCaseReceiving() throws IOException {
        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);

        ssccScanResponse.getContainers().get(0).setParentId(null);
        Instruction instruction = Mockito.mock(Instruction.class);
        Mockito.when(instruction.getInstructionCreatedByPackageInfo())
                .thenReturn("{\"id\":\"someRandomId2\"}");
        updateInstructionDataValidator.validateScannedUnitBelongsToCasev2(instruction, ssccScanResponse.getContainers().get(0));

        ssccScanResponse.getContainers().get(0).setParentId("someRandomParentId1");
        instruction = Mockito.mock(Instruction.class);
        Mockito.when(instruction.getInstructionCreatedByPackageInfo())
                .thenReturn("{}");
        updateInstructionDataValidator.validateScannedUnitBelongsToCasev2(instruction, ssccScanResponse.getContainers().get(0));

        ssccScanResponse.getContainers().get(0).setParentId("someRandomId1");
        instruction = Mockito.mock(Instruction.class);
        Mockito.when(instruction.getInstructionCreatedByPackageInfo())
                .thenReturn("{\"id\":\"someDifferentId2\"}");
        Instruction finalInstruction = instruction;
        Throwable exception = Assertions.assertThrows(RuntimeException.class,
                () -> updateInstructionDataValidator.validateScannedUnitBelongsToCasev2(finalInstruction, ssccScanResponse.getContainers().get(0)));
        Assertions.assertEquals("Scanned unit does not belong to this case.", exception.getMessage());
    }


    @Test
    public void testValidateOpenReceivingStatus() throws IOException {
        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);

        ssccScanResponse.getContainers().get(0).setReceivingStatus("Received");
        Throwable exception = Assertions.assertThrows(RuntimeException.class,
                () -> updateInstructionDataValidator.validateOpenReceivingStatus(ssccScanResponse.getContainers().get(0)));

        Assertions.assertEquals(    "Given instruction id: %s is either INVALID or already in COMPLETED status, please verify", exception.getMessage());
    }

    @Test
    public void testValidateInstructionGtinMatchesCurrentNodeApiGtin() throws IOException {
        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);
        ssccScanResponse.getContainers().get(0).setGtin("345");

        Instruction instruction = MockInstruction.getInstructionV2("RxBuildContainer");
        instruction.setGtin("1234");


        Throwable exception = Assertions.assertThrows(RuntimeException.class,
                () -> updateInstructionDataValidator.validateInstructionGtinMatchesCurrentNodeApiGtin(instruction, ssccScanResponse.getContainers().get(0)));

        Assertions.assertEquals("Scanned GTIN doesn't match with the Instruction. Please scan the valid barcode.", exception.getMessage());

    }

    @Test
    public void testVerifyIfCaseCanBeReceived() {
        Instruction instruction = MockInstruction.getInstructionV2("RxBuildContainer");
        DeliveryDocument deliveryDocument = gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
        DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);

        Throwable exception = Assertions.assertThrows(ReceivingBadDataException.class,
                () -> updateInstructionDataValidator.verifyIfCaseCanBeReceived(1L,2, deliveryDocumentLine, 3));

        Assertions.assertEquals("Update exceeds quantity needed", exception.getMessage());




        /*Throwable exception1 = Assertions.assertThrows(ReceivingBadDataException.class,
                () -> updateInstructionDataValidator.verifyIfCaseCanBeReceived(1L,12, deliveryDocumentLine, 3));

        Assertions.assertEquals("All cases are received by one or more instructions, please receive Pallet.", exception1.getMessage());
*/
        deliveryDocumentLine.setShipmentDetailsList(new ArrayList<>());
        Throwable exception2 = Assertions.assertThrows(ReceivingBadDataException.class,
                () -> updateInstructionDataValidator.verifyIfCaseCanBeReceived(1L,12, deliveryDocumentLine, 3));

        Assertions.assertEquals( "Shipment Details unavailable while publishing to EPCIS", exception2.getMessage());


    }

    @Test
    public void testValidateInstructionAndInstructionOwner() throws ReceivingException {
        Mockito.doNothing().when(updateInstructionServiceHelper).validateRequest(any(Instruction.class));
        Mockito.doNothing().when(updateInstructionServiceHelper).validateInstructionOwner(any(HttpHeaders.class), any(DataHolder.class));
        DataHolder dataHolder = new DataHolder();
        dataHolder.setInstruction(new Instruction());
        updateInstructionDataValidator.validateInstructionAndInstructionOwner(dataHolder, MockHttpHeaders.getHeaders());
        Mockito.verify(updateInstructionServiceHelper).validateRequest(any(Instruction.class));
        Mockito.verify(updateInstructionServiceHelper).validateInstructionOwner(any(HttpHeaders.class), any(DataHolder.class));

    }

    @Test(expectedExceptions = ReceivingInternalException.class)
    public void test_validateContainerDoesNotAlreadyExist_dCTransfer() throws ReceivingInternalException {
        Mockito.when(containerItemService.getContainerItemByFacilityAndGtinAndSerial(anyInt(), anyString(), anyString(), anyString())).thenReturn(Optional.of(new ContainerItem()));
        updateInstructionDataValidator.validateContainerDoesNotAlreadyExist(28, "12345", "34567", 6032, "US");
    }

    @Test(expectedExceptions = ReceivingInternalException.class)
    public void test_validateContainerDoesNotAlreadyExist_normalPo() throws ReceivingInternalException {
        Mockito.when(containerItemService.getContainerItemByGtinAndSerial(anyString(), anyString())).thenReturn(Optional.of(new ContainerItem()));
        updateInstructionDataValidator.validateContainerDoesNotAlreadyExist(20, "12345", "34567", 6032, "US");
    }
}