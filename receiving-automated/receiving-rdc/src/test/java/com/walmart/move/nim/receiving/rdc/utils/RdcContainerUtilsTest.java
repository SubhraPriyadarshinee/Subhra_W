package com.walmart.move.nim.receiving.rdc.utils;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.client.nimrds.model.Destination;
import com.walmart.move.nim.receiving.core.client.nimrds.model.DsdcReceiveResponse;
import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceivedContainer;
import com.walmart.move.nim.receiving.core.common.*;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.*;
import com.walmart.move.nim.receiving.core.framework.transformer.Transformer;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.ei.*;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;
import com.walmart.move.nim.receiving.core.model.symbotic.SymFreightType;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.core.transformer.InventoryTransformer;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.mock.data.MockDeliveryDocuments;
import com.walmart.move.nim.receiving.rdc.mock.data.MockRdcInstruction;
import com.walmart.move.nim.receiving.rdc.service.RdcDeliveryMetaDataService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ContainerType;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.InventoryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.IOException;
import java.util.*;
import org.apache.commons.collections4.MapUtils;
import org.mockito.*;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RdcContainerUtilsTest {
  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private ContainerAdjustmentHelper containerAdjustmentHelper;
  @Mock private ContainerAdjustmentValidator containerAdjustmentValidator;
  @Mock private DeliveryService deliveryService;
  @Mock private ReceiptService receiptService;
  @Mock private InstructionPersisterService instructionPersisterService;
  @Mock private SymboticPutawayPublishHelper symboticPutawayPublishHelper;
  @Mock private RdcInstructionUtils rdcInstructionUtils;
  @Mock private AppConfig appConfig;
  @Mock private RdcDeliveryMetaDataService rdcDeliveryMetaDataService;
  @Captor private ArgumentCaptor<String> argumentCaptor;
  @Mock private ContainerService containerService;
  @Mock private Transformer<Container, ContainerDTO> transformer;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @InjectMocks private RdcContainerUtils rdcContainerUtils;
  @Mock private RdcDcFinUtils rdcDcFinUtils;
  @Mock private MovePublisher movePublisher;
  @Mock InventoryTransformer inventoryTransformer;
  @Mock private RdcReceivingUtils rdcReceivingUtils;
  @Mock private EIService eiService;

  @Mock private RdcManagedConfig rdcManagedConfig;
  private HttpHeaders httpHeaders;
  private static final String facilityNum = "32818";
  private static final String countryCode = "US";
  private String userId = "sysadmin";
  private String labelTrackingId = "009700936505";
  private String slotId = "A0001";
  private Integer receivedQuantity = 2;
  private final Gson gson = new Gson();

  @BeforeMethod
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    TenantContext.setFacilityCountryCode(countryCode);
    httpHeaders = MockHttpHeaders.getHeaders(facilityNum, countryCode);
    ReflectionTestUtils.setField(rdcContainerUtils, "gson", gson);
    ReflectionTestUtils.setField(rdcContainerUtils, "transformer", transformer);
  }

  @AfterMethod
  public void shutdownMocks() {
    reset(
        deliveryService,
        receiptService,
        containerPersisterService,
        containerAdjustmentValidator,
        symboticPutawayPublishHelper,
        containerAdjustmentHelper,
        instructionPersisterService,
        appConfig,
        rdcReceivingUtils);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testBackoutContainerThrowsExceptionWhenAdjustmentValidationsFailsForTheContainer()
      throws ReceivingException {
    Container container = getMockContainer();
    JsonObject mockDeliveryResponse = new JsonObject();
    mockDeliveryResponse.addProperty("deliveryStatus", DeliveryStatus.FNL.name());

    doReturn(
            new CancelContainerResponse(
                "lpn123",
                ExceptionCodes.LABEL_CORRECTION_ERROR_FOR_FINALIZED_DELIVERY,
                ReceivingException.LABEL_QUANTITY_ADJUSTMENT_ERROR_MSG_FOR_FINALIZED_DELIVERY))
        .when(containerAdjustmentHelper)
        .validateContainerForAdjustment(any(Container.class), any(HttpHeaders.class));

    rdcContainerUtils.backoutContainer(container, MockHttpHeaders.getHeaders());
  }

  @Test
  public void testBackOutContainerIsSuccessForNonAtlasConvertedItem()
      throws ReceivingException, IOException {
    Container container = getMockContainer();
    rdcContainerUtils.backoutContainer(container, MockHttpHeaders.getHeaders());
    verify(containerAdjustmentHelper, times(1))
        .validateContainerForAdjustment(any(Container.class), any(HttpHeaders.class));
  }

  @Test
  public void testBackOutContainerPutAwayMessageIsSuccessForAtlasConvertedSymEligibleItem()
      throws ReceivingException, IOException {
    Container container = getMockContainer();
    container.getContainerItems().get(0).setAsrsAlignment(ReceivingConstants.SYM_BRKPK_ASRS_VALUE);
    container.getContainerItems().get(0).setSlotType(ReceivingConstants.PRIME_SLOT_TYPE);

    Map<String, String> miscInfoMap = new HashMap<>();
    miscInfoMap.put(ReceivingConstants.IS_ATLAS_CONVERTED_ITEM, "true");
    container.getContainerItems().get(0).setContainerItemMiscInfo(miscInfoMap);
    when(appConfig.getValidSymAsrsAlignmentValues())
        .thenReturn(
            Arrays.asList(
                ReceivingConstants.SYM_BRKPK_ASRS_VALUE,
                ReceivingConstants.SYM_CASE_PACK_ASRS_VALUE));
    when(containerAdjustmentHelper.validateContainerForAdjustment(
            any(Container.class), any(HttpHeaders.class)))
        .thenReturn(null);
    doNothing()
        .when(symboticPutawayPublishHelper)
        .publishSymPutawayUpdateOrDeleteMessage(
            anyString(), any(ContainerItem.class), anyString(), anyInt(), any(HttpHeaders.class));
    doReturn(new Receipt()).when(containerAdjustmentHelper).adjustReceipts(any(Container.class));
    doNothing()
        .when(containerAdjustmentHelper)
        .persistAdjustedReceiptsAndContainer(any(Receipt.class), any(Container.class));
    when(rdcInstructionUtils.updateInstructionQuantity(anyLong(), anyInt()))
        .thenReturn(getMockInstruction());

    rdcContainerUtils.backoutContainer(container, MockHttpHeaders.getHeaders());

    verify(containerAdjustmentHelper, times(1))
        .validateContainerForAdjustment(any(Container.class), any(HttpHeaders.class));
    verify(containerAdjustmentHelper, times(1)).adjustReceipts(any(Container.class));
    verify(containerAdjustmentHelper, times(1))
        .persistAdjustedReceiptsAndContainer(any(Receipt.class), any(Container.class));
    verify(symboticPutawayPublishHelper, times(1))
        .publishSymPutawayUpdateOrDeleteMessage(
            anyString(), any(ContainerItem.class), anyString(), anyInt(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1)).updateInstructionQuantity(anyLong(), anyInt());
  }

  @Test
  public void testApplyReceivingCorrectionIsSuccess_NonAtlasItems()
      throws ReceivingException, IOException {
    when(containerAdjustmentHelper.validateContainerForAdjustment(
            any(Container.class), any(HttpHeaders.class)))
        .thenReturn(null);
    rdcContainerUtils.applyReceivingCorrections(
        getMockContainer(), -20, MockHttpHeaders.getHeaders());
    verify(containerAdjustmentHelper, times(1))
        .validateContainerForAdjustment(any(Container.class), any(HttpHeaders.class));
  }

  @Test
  public void testApplyReceivingCorrectionPublishPutAwayUpdateMessageForAtlasConvertedAndSymItem()
      throws IOException, ReceivingException {
    Container container = getMockContainer();
    Instruction instruction = getMockInstruction();
    container
        .getContainerItems()
        .get(0)
        .setAsrsAlignment(ReceivingConstants.SYM_CASE_PACK_ASRS_VALUE);
    container.getContainerItems().get(0).setSlotType(ReceivingConstants.PRIME_SLOT_TYPE);

    Map<String, String> miscInfoMap = new HashMap<>();
    miscInfoMap.put(ReceivingConstants.IS_ATLAS_CONVERTED_ITEM, "true");
    container.getContainerItems().get(0).setContainerItemMiscInfo(miscInfoMap);
    when(appConfig.getValidSymAsrsAlignmentValues())
        .thenReturn(
            Arrays.asList(
                ReceivingConstants.SYM_BRKPK_ASRS_VALUE,
                ReceivingConstants.SYM_CASE_PACK_ASRS_VALUE));
    when(containerAdjustmentHelper.validateContainerForAdjustment(
            any(Container.class), any(HttpHeaders.class)))
        .thenReturn(null);
    doNothing()
        .when(symboticPutawayPublishHelper)
        .publishSymPutawayUpdateOrDeleteMessage(
            anyString(), any(ContainerItem.class), anyString(), anyInt(), any(HttpHeaders.class));
    doReturn(new Receipt())
        .when(containerAdjustmentHelper)
        .adjustQuantityInReceipt(anyInt(), anyString(), any(Container.class), anyString());
    doReturn(getMockContainer())
        .when(containerAdjustmentHelper)
        .adjustPalletQuantity(anyInt(), any(Container.class), anyString());
    doNothing()
        .when(containerAdjustmentHelper)
        .persistAdjustedReceiptsAndContainer(any(Receipt.class), any(Container.class));
    when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instruction);
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(instruction);

    rdcContainerUtils.applyReceivingCorrections(container, -20, MockHttpHeaders.getHeaders());

    verify(containerAdjustmentHelper, times(1))
        .validateContainerForAdjustment(any(Container.class), any(HttpHeaders.class));
    verify(symboticPutawayPublishHelper, times(1))
        .publishSymPutawayUpdateOrDeleteMessage(
            anyString(), any(ContainerItem.class), anyString(), anyInt(), any(HttpHeaders.class));
    verify(containerAdjustmentHelper, times(1))
        .adjustQuantityInReceipt(eq(-15), anyString(), any(Container.class), anyString());
    verify(containerAdjustmentHelper, times(1))
        .adjustPalletQuantity(eq(60), any(Container.class), anyString());
    verify(containerAdjustmentHelper, times(1))
        .persistAdjustedReceiptsAndContainer(any(Receipt.class), any(Container.class));
    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    assertSame(instruction.getReceivedQuantity(), 15);
  }

  @Test
  public void testApplyReceivingCorrectionDoNotPublishPutAwayUpdateMessage()
      throws ReceivingException, IOException {
    Container container = getMockContainer();
    Instruction instruction = getMockInstruction();
    container.getContainerItems().get(0).setAsrsAlignment("SYM1");
    container.getContainerItems().get(0).setSlotType(ReceivingConstants.RESERVE_SLOT_TYPE);

    Map<String, String> miscInfoMap = new HashMap<>();
    miscInfoMap.put(ReceivingConstants.IS_ATLAS_CONVERTED_ITEM, "true");
    container.getContainerItems().get(0).setContainerItemMiscInfo(miscInfoMap);

    when(containerAdjustmentHelper.validateContainerForAdjustment(
            any(Container.class), any(HttpHeaders.class)))
        .thenReturn(null);
    doReturn(new Receipt())
        .when(containerAdjustmentHelper)
        .adjustQuantityInReceipt(anyInt(), anyString(), any(Container.class), anyString());
    doReturn(getMockContainer())
        .when(containerAdjustmentHelper)
        .adjustPalletQuantity(anyInt(), any(Container.class), anyString());
    doNothing()
        .when(containerAdjustmentHelper)
        .persistAdjustedReceiptsAndContainer(any(Receipt.class), any(Container.class));
    when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instruction);
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(instruction);

    rdcContainerUtils.applyReceivingCorrections(container, -20, MockHttpHeaders.getHeaders());

    verify(symboticPutawayPublishHelper, times(0))
        .publishSymPutawayUpdateOrDeleteMessage(
            anyString(), any(ContainerItem.class), anyString(), anyInt(), any(HttpHeaders.class));
    verify(containerAdjustmentHelper, times(1))
        .adjustQuantityInReceipt(eq(-15), anyString(), any(Container.class), anyString());
    verify(containerAdjustmentHelper, times(1))
        .adjustPalletQuantity(eq(60), any(Container.class), anyString());
    verify(containerAdjustmentHelper, times(1))
        .persistAdjustedReceiptsAndContainer(any(Receipt.class), any(Container.class));
    verify(instructionPersisterService, times(1)).getInstructionById(anyLong());
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    assertSame(instruction.getReceivedQuantity(), 15);
  }

  @Test
  public void testApplyReceivingCorrectionInvalidAsrsAlignmentPutaway()
      throws ReceivingException, IOException {
    Container container = getMockContainer();
    Instruction instruction = getMockInstruction();
    container.getContainerItems().get(0).setAsrsAlignment(ReceivingConstants.PTL_ASRS_VALUE);
    container.getContainerItems().get(0).setSlotType(ReceivingConstants.PRIME_SLOT_TYPE);

    Map<String, String> miscInfoMap = new HashMap<>();
    miscInfoMap.put(ReceivingConstants.IS_ATLAS_CONVERTED_ITEM, "true");
    container.getContainerItems().get(0).setContainerItemMiscInfo(miscInfoMap);
    when(appConfig.getValidSymAsrsAlignmentValues())
        .thenReturn(Arrays.asList(ReceivingConstants.SYM_BRKPK_ASRS_VALUE));
    when(containerAdjustmentHelper.validateContainerForAdjustment(
            any(Container.class), any(HttpHeaders.class)))
        .thenReturn(null);
    doReturn(new Receipt())
        .when(containerAdjustmentHelper)
        .adjustQuantityInReceipt(anyInt(), anyString(), any(Container.class), anyString());
    doReturn(getMockContainer())
        .when(containerAdjustmentHelper)
        .adjustPalletQuantity(anyInt(), any(Container.class), anyString());
    doNothing()
        .when(containerAdjustmentHelper)
        .persistAdjustedReceiptsAndContainer(any(Receipt.class), any(Container.class));
    when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instruction);
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(instruction);

    rdcContainerUtils.applyReceivingCorrections(container, -20, MockHttpHeaders.getHeaders());

    verify(symboticPutawayPublishHelper, times(0))
        .publishSymPutawayUpdateOrDeleteMessage(
            anyString(), any(ContainerItem.class), anyString(), anyInt(), any(HttpHeaders.class));
  }

  @Test
  public void testApplyReceivingCorrectionValidAsrsPutawayPublish()
      throws ReceivingException, IOException {
    Container container = getMockContainer();
    Instruction instruction = getMockInstruction();
    container.getContainerItems().get(0).setAsrsAlignment(ReceivingConstants.SYM_BRKPK_ASRS_VALUE);
    container.getContainerItems().get(0).setSlotType(ReceivingConstants.PRIME_SLOT_TYPE);
    when(appConfig.getValidSymAsrsAlignmentValues())
        .thenReturn(Arrays.asList(ReceivingConstants.SYM_BRKPK_ASRS_VALUE));
    Map<String, String> miscInfoMap = new HashMap<>();
    miscInfoMap.put(ReceivingConstants.IS_ATLAS_CONVERTED_ITEM, "true");
    container.getContainerItems().get(0).setContainerItemMiscInfo(miscInfoMap);

    when(containerAdjustmentHelper.validateContainerForAdjustment(
            any(Container.class), any(HttpHeaders.class)))
        .thenReturn(null);
    doReturn(new Receipt())
        .when(containerAdjustmentHelper)
        .adjustQuantityInReceipt(anyInt(), anyString(), any(Container.class), anyString());
    doReturn(getMockContainer())
        .when(containerAdjustmentHelper)
        .adjustPalletQuantity(anyInt(), any(Container.class), anyString());
    doNothing()
        .when(containerAdjustmentHelper)
        .persistAdjustedReceiptsAndContainer(any(Receipt.class), any(Container.class));
    when(instructionPersisterService.getInstructionById(anyLong())).thenReturn(instruction);
    when(instructionPersisterService.saveInstruction(any(Instruction.class)))
        .thenReturn(instruction);

    rdcContainerUtils.applyReceivingCorrections(container, -20, MockHttpHeaders.getHeaders());

    verify(symboticPutawayPublishHelper, times(1))
        .publishSymPutawayUpdateOrDeleteMessage(
            anyString(), any(ContainerItem.class), anyString(), anyInt(), any(HttpHeaders.class));
  }

  @Test
  public void testBuildContainer_Pallet_AvailableInInventory() throws IOException {
    Instruction mockInstruction = getMockInstruction();
    UpdateInstructionRequest mockUpdateInstructionRequest = getMockUpdateInstructionRequest();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentForReceiveInstructionFromInstructionEntity();
    when(rdcDeliveryMetaDataService.findDeliveryMetaData(anyLong()))
        .thenReturn(getDeliveryMetaData());
    Container container =
        rdcContainerUtils.buildContainer(
            mockInstruction,
            mockUpdateInstructionRequest,
            deliveryDocument,
            userId,
            labelTrackingId,
            slotId,
            null);
    assertNotNull(container);
    assertNotNull(container.getDestination().get("slot"));
    assertEquals(
        deliveryDocument
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .isAtlasConvertedItem(),
        false);
    assertEquals(ContainerType.PALLET.getText(), container.getContainerType());
    assertNull(container.getLabelType());
    assertNull(container.getContainerMiscInfo().get(ReceivingConstants.TRAILER_NUMBER));
  }

  @Test
  public void testBuildContainer() {
    InstructionRequest instructionRequest = mockInstructionRequest();
    DsdcReceiveResponse dsdcReceiveResponse = getMockReceivedDsdcContainers("R8002", "");
    Container container =
        rdcContainerUtils.buildContainer(
            instructionRequest, dsdcReceiveResponse, 123l, "sysadmin", null);
    assertNotNull(container);
    assertNotNull(container.getDestination().get("slot"));
    assertEquals(container.getInstructionId().toString(), "123");
    assertEquals(ContainerType.CASE.toString(), container.getContainerType());
  }

  /** @return */
  private InstructionRequest mockInstructionRequest() {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("8e1df00-ebf6-11e8-9c25-dd4bfc2a06f8");
    instructionRequest.setDeliveryStatus(DeliveryStatus.OPN.toString());
    instructionRequest.setDeliveryNumber("21231313");
    instructionRequest.setDoorNumber("123");
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setProblemTagId(null);
    instructionRequest.setUpcNumber(null);
    return instructionRequest;
  }

  private DsdcReceiveResponse getMockReceivedDsdcContainers(String slot, String audit_flag) {
    DsdcReceiveResponse dsdcReceiveResponse = new DsdcReceiveResponse();
    dsdcReceiveResponse.setAuditFlag(audit_flag);
    dsdcReceiveResponse.setPocode("73");
    dsdcReceiveResponse.setBatch("123");
    dsdcReceiveResponse.setDccarton("12345123456");
    dsdcReceiveResponse.setDept("01");
    dsdcReceiveResponse.setDiv("23");
    dsdcReceiveResponse.setErrorCode("0");
    dsdcReceiveResponse.setEvent("TEST");
    dsdcReceiveResponse.setHazmat("N");
    dsdcReceiveResponse.setLabel_bar_code("123451212345123456");
    dsdcReceiveResponse.setLane_nbr("12");
    dsdcReceiveResponse.setMessage("Hello, world!");
    dsdcReceiveResponse.setPacks("1234567890123456789012345678");
    dsdcReceiveResponse.setRcvr_nbr("123456");
    dsdcReceiveResponse.setScanned("1");
    dsdcReceiveResponse.setUnscanned("0");
    dsdcReceiveResponse.setSlot(slot);
    dsdcReceiveResponse.setSneEnabled("Y");
    dsdcReceiveResponse.setStore("12345");
    return dsdcReceiveResponse;
  }

  @Test
  public void testBuildContainer_InventoryStatus_Picked() throws IOException {
    Instruction mockInstruction = getMockInstruction();
    UpdateInstructionRequest mockUpdateInstructionRequest = getMockUpdateInstructionRequest();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentForReceiveInstructionFromInstructionEntity();
    when(rdcDeliveryMetaDataService.findDeliveryMetaData(anyLong()))
        .thenReturn(getDeliveryMetaData());
    Container container =
        rdcContainerUtils.buildContainer(
            mockInstruction,
            mockUpdateInstructionRequest,
            deliveryDocument,
            userId,
            labelTrackingId,
            "R8001",
            "01223");
    assertNotNull(container);
    assertNotNull(container.getDestination().get("slot"));
    assertFalse(
        deliveryDocument
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .isAtlasConvertedItem());
    assertEquals(InventoryStatus.PICKED.toString(), container.getInventoryStatus());
    assertNull(container.getContainerMiscInfo().get(ReceivingConstants.TRAILER_NUMBER));
  }

  @Test
  public void testBuildContainer_InventoryStatus_Allocated() throws IOException {
    Instruction mockInstruction = getMockInstruction();
    UpdateInstructionRequest mockUpdateInstructionRequest = getMockUpdateInstructionRequest();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentForReceiveInstructionFromInstructionEntity();
    when(rdcDeliveryMetaDataService.findDeliveryMetaData(anyLong()))
        .thenReturn(getDeliveryMetaData());
    Container container =
        rdcContainerUtils.buildContainer(
            mockInstruction,
            mockUpdateInstructionRequest,
            deliveryDocument,
            userId,
            labelTrackingId,
            "V0050",
            null);
    assertNotNull(container);
    assertNotNull(container.getDestination().get("slot"));
    assertEquals(
        deliveryDocument
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .isAtlasConvertedItem(),
        false);
    assertEquals(InventoryStatus.ALLOCATED.toString(), container.getInventoryStatus());
    assertNull(container.getContainerMiscInfo().get(ReceivingConstants.TRAILER_NUMBER));
  }

  @Test
  public void testBuildContainerForAtlasConvertedItemSSSTK() throws IOException {
    Instruction mockInstruction = getMockInstruction();
    UpdateInstructionRequest mockUpdateInstructionRequest = getMockUpdateInstructionRequest();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentForReceiveInstructionFromInstructionEntity();
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(rdcDeliveryMetaDataService.findDeliveryMetaData(anyLong()))
        .thenReturn(getDeliveryMetaData());
    when(rdcInstructionUtils.isSSTKDocument(any(DeliveryDocument.class))).thenReturn(true);
    Container container =
        rdcContainerUtils.buildContainer(
            mockInstruction,
            mockUpdateInstructionRequest,
            deliveryDocument,
            userId,
            labelTrackingId,
            slotId,
            null);
    assertNotNull(container);
    assertEquals(container.getContainerMiscInfo().get(ReceivingConstants.TRAILER_NUMBER), "WMT");
  }

  @Test
  public void testBuildContainerForAtlasConvertedItemWithNoTrailerInformation() throws IOException {
    Instruction mockInstruction = getMockInstruction();
    UpdateInstructionRequest mockUpdateInstructionRequest = getMockUpdateInstructionRequest();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentForReceiveInstructionFromInstructionEntity();
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(rdcDeliveryMetaDataService.findDeliveryMetaData(anyLong())).thenReturn(null);
    Container container =
        rdcContainerUtils.buildContainer(
            mockInstruction,
            mockUpdateInstructionRequest,
            deliveryDocument,
            userId,
            labelTrackingId,
            slotId,
            null);
    assertNotNull(container);
    assertNotNull(container.getDestination().get("slot"));
    assertEquals(
        deliveryDocument
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .isAtlasConvertedItem(),
        true);
    assertNull(container.getContainerMiscInfo().get(ReceivingConstants.TRAILER_NUMBER));
  }

  @Test
  public void testBuildContainerForAtlasConvertedItemWithContainerMiscEmptyTrailerNumber()
      throws IOException {
    Instruction mockInstruction = getMockInstruction();
    UpdateInstructionRequest mockUpdateInstructionRequest = getMockUpdateInstructionRequest();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentForReceiveInstructionFromInstructionEntity();
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(rdcDeliveryMetaDataService.findDeliveryMetaData(anyLong()))
        .thenReturn(getDeliveryMetaDataWithEmptyTrailerNumber());
    Container container =
        rdcContainerUtils.buildContainer(
            mockInstruction,
            mockUpdateInstructionRequest,
            deliveryDocument,
            userId,
            labelTrackingId,
            slotId,
            null);
    assertNotNull(container);
    assertNotNull(container.getDestination().get("slot"));
    assertEquals(
        deliveryDocument
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .isAtlasConvertedItem(),
        true);
    assertNull(container.getContainerMiscInfo().get(ReceivingConstants.TRAILER_NUMBER));
  }

  @Test
  public void testBuildContainerItemWithImageDetails() throws IOException {
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentForReceiveInstructionFromInstructionEntity();
    List<ContainerItem> containerItem =
        rdcContainerUtils.buildContainerItem(
            labelTrackingId, deliveryDocument, receivedQuantity, null);
    assertNotNull(containerItem.get(0));
    assertNotNull(containerItem.get(0).getContainerItemMiscInfo().get("isAtlasConvertedItem"));
    assertNotNull(
        containerItem.get(0).getContainerItemMiscInfo().get(ReceivingConstants.PACK_TYPE_CODE));
    assertNotNull(
        containerItem.get(0).getContainerItemMiscInfo().get(ReceivingConstants.HANDLING_CODE));
    assertNotNull(containerItem.get(0).getPoTypeCode());
    assertNotNull(
        containerItem.get(0).getContainerItemMiscInfo().get(ReceivingConstants.IMAGE_URL));
    assertEquals(
        containerItem.get(0).getContainerItemMiscInfo().get(ReceivingConstants.IMAGE_URL),
        "https://i5.walmartimages.com/asr/65ef69c2-a13f-4601-bf1e-4ac9a9c99e30_9.91e70d2feaa250e7acfc5a9139237360.jpeg?odnHeight=450&odnWidth=450&odnBg=ffffff");
  }

  @Test
  public void testBuildContainerItemForBreakPackConveyPicks() throws IOException {
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack().get(0);
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode(RdcConstants.DA_BREAK_PACK_CONVEY_PICKS_ITEM_HANDLING_CODE);
    List<ContainerItem> containerItems =
        rdcContainerUtils.buildContainerItem(labelTrackingId, deliveryDocument, 1, null);
    assertNotNull(containerItems);
    assertTrue(containerItems.size() > 0);
    assertEquals(
        containerItems.get(0).getQuantity(),
        deliveryDocument.getDeliveryDocumentLines().get(0).getWarehousePack());
    assertEquals(containerItems.get(0).getQuantityUOM(), ReceivingConstants.Uom.EACHES);
  }

  @Test
  public void testBuildContainerItemForSSTKBreakPackConveyPicks() throws IOException {
    Integer receivedQty = 10;
    DeliveryDocument deliveryDocument = MockDeliveryDocuments.getDeliveryDocumentsForSSTK().get(0);
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode(RdcConstants.DA_BREAK_PACK_CONVEY_PICKS_ITEM_HANDLING_CODE);
    Integer totalReceivedQty =
        receivedQty * deliveryDocument.getDeliveryDocumentLines().get(0).getVendorPack();
    List<ContainerItem> containerItems =
        rdcContainerUtils.buildContainerItem(labelTrackingId, deliveryDocument, receivedQty, null);
    assertNotNull(containerItems);
    assertTrue(containerItems.size() > 0);
    assertEquals(containerItems.get(0).getQuantity(), totalReceivedQty);
    assertEquals(containerItems.get(0).getQuantityUOM(), ReceivingConstants.Uom.EACHES);
  }

  @Test
  public void testBuildContainerItemForDetailsForDA() throws IOException {
    Integer receivedQty = 10;
    List<Distribution> distributions = new ArrayList<>();
    Distribution distribution = new Distribution();
    distribution.setAllocQty(12);
    distribution.setDestNbr(2032);
    distributions.add(distribution);
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode(RdcConstants.DA_BREAK_PACK_CONVEY_PICKS_ITEM_HANDLING_CODE);
    Integer totalReceivedQty =
        receivedQty * deliveryDocument.getDeliveryDocumentLines().get(0).getVendorPack();
    ContainerItem containerItem =
        rdcContainerUtils.buildContainerItemDetails(
            labelTrackingId, deliveryDocument, receivedQty, null, "MANUAL", distributions, null);
    assertNotNull(containerItem);
    assertEquals(
        containerItem.getContainerItemMiscInfo().get(ReceivingConstants.PO_EVENT), "POS REPLEN");
  }

  @Test
  public void testBuildContainerItemDetailsDA_AtlasItemReceiving() throws IOException {
    String labelTrackingId = "a602032323232323";
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("CC");
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocument.getDeliveryDocumentLines().get(0).setEvent("MFC REPLEN");
    Distribution distribution = new Distribution();
    distribution.setAllocQty(10);
    distribution.setQtyUom(ReceivingConstants.Uom.EACHES);
    ContainerItem containerItem =
        rdcContainerUtils.buildContainerItemDetails(
            labelTrackingId,
            deliveryDocument,
            10,
            null,
            "MANUAL",
            Collections.singletonList(distribution),
            null);

    assertNotNull(containerItem);
    assertEquals(containerItem.getQuantity().intValue(), 10);
    assertEquals(
        containerItem.getContainerItemMiscInfo().get(ReceivingConstants.PO_EVENT), "MFC REPLEN");
  }

  @Test
  public void testBuildContainerItemDetailsDA_NonAtlasItemReceiving() throws IOException {
    String labelTrackingId = "012345678912345678";
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack().get(0);
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("BC");
    deliveryDocument.getDeliveryDocumentLines().get(0).setEvent("");
    ContainerItem containerItem =
        rdcContainerUtils.buildContainerItemDetails(
            labelTrackingId, deliveryDocument, 10, null, null, null, null);

    assertNotNull(containerItem);
    assertEquals(containerItem.getQuantity().intValue(), 60);
    assertNull(containerItem.getContainerItemMiscInfo().get(ReceivingConstants.PO_EVENT));
  }

  @Test
  public void testBuildContainerItemDetailsDA_NonAtlasItemReceiving_ConveyPicks()
      throws IOException {
    String labelTrackingId = "012345678912345678";
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack().get(0);
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("BM");
    ContainerItem containerItem =
        rdcContainerUtils.buildContainerItemDetails(
            labelTrackingId, deliveryDocument, 1, null, null, null, null);

    assertNotNull(containerItem);
    assertEquals(containerItem.getQuantity().intValue(), 3);
  }

  @Test
  public void testBuildContainerItemDetailsSSTK_PalletReceiving() throws IOException {
    String labelTrackingId = "012345678912345678";
    DeliveryDocument deliveryDocument = MockDeliveryDocuments.getDeliveryDocumentsForSSTK().get(0);
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode("BM");
    ContainerItem containerItem =
        rdcContainerUtils.buildContainerItemDetails(
            labelTrackingId, deliveryDocument, 20, null, null, null, null);

    assertNotNull(containerItem);
    assertEquals(containerItem.getQuantity().intValue(), 200);
  }

  @Test
  public void testBuildContainerItemWithFinancialReportingGroupCode() throws IOException {
    TenantContext.setFacilityCountryCode("us");
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments
            .getDeliveryDocumentForReceiveInstructionFromInstructionEntityWithoutFinancialGroupCode();
    List<ContainerItem> containerItem =
        rdcContainerUtils.buildContainerItem(
            labelTrackingId, deliveryDocument, receivedQuantity, null);
    assertNotNull(containerItem.get(0));
    assertEquals(containerItem.get(0).getFinancialReportingGroupCode(), "US");
  }

  @Test
  public void testBuildContainerItemDoNotPersistImageForNonSymEligibleItems() throws IOException {
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK_AtlasConvertedItem().get(0);
    List<ContainerItem> containerItem =
        rdcContainerUtils.buildContainerItem(
            labelTrackingId, deliveryDocument, receivedQuantity, null);
    assertNotNull(containerItem.get(0));
    assertNotNull(containerItem.get(0).getContainerItemMiscInfo().get("isAtlasConvertedItem"));
    assertNotNull(containerItem.get(0).getPoTypeCode());
    assertNull(containerItem.get(0).getContainerItemMiscInfo().get(ReceivingConstants.IMAGE_URL));
  }

  @Test
  public void testGetContainerDetailsReturnsContainer() {
    List<PrintLabelRequest> printLabelRequests = new ArrayList<>();
    PrintLabelRequest printLabelRequest = new PrintLabelRequest();
    printLabelRequest.setFormatName(ReceivingConstants.PRINT_LABEL_FORMAT_NAME);
    printLabelRequest.setTtlInHours(ReceivingConstants.PRINT_LABEL_DEFAULT_TTL);
    printLabelRequest.setLabelIdentifier(labelTrackingId);
    printLabelRequests.add(printLabelRequest);

    Map<String, Object> printLabelData = new HashMap<>();
    printLabelData.put(ReceivingConstants.PRINT_HEADERS_KEY, "Headers");
    printLabelData.put(ReceivingConstants.PRINT_CLIENT_ID_KEY, "Atlas-RCV");
    printLabelData.put(ReceivingConstants.PRINT_REQUEST_KEY, printLabelRequests);

    ContainerDetails containerDetails =
        rdcContainerUtils.getContainerDetails(
            labelTrackingId,
            printLabelData,
            ContainerType.PALLET,
            RdcConstants.OUTBOUND_CHANNEL_METHOD_SSTKU);
    assertNotNull(containerDetails);
    assertSame(containerDetails.getTrackingId(), labelTrackingId);
    assertSame(containerDetails.getCtrType(), ContainerType.PALLET.getText());
    assertSame(containerDetails.getInventoryStatus(), InventoryStatus.PICKED.name());
    assertSame(containerDetails.getCtrStatus(), ReceivingConstants.STATUS_COMPLETE);
    assertSame(
        containerDetails.getOutboundChannelMethod(), RdcConstants.OUTBOUND_CHANNEL_METHOD_SSTKU);
    assertNotNull(containerDetails.getCtrLabel());
    assertFalse(containerDetails.getCtrReusable());
    assertFalse(containerDetails.getCtrShippable());
  }

  @Test
  public void
      testIsAtlasConvertedItemReturnsTrueWhenAtlasConvertedFlagIsTrueInContainerItemMiscInfo() {
    Container container = getMockContainer();
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(ReceivingConstants.IS_ATLAS_CONVERTED_ITEM, "true");
    container.getContainerItems().get(0).setContainerItemMiscInfo(containerItemMiscInfo);
    boolean isAtlasConvertedItem =
        ContainerUtils.isAtlasConvertedItem(container.getContainerItems().get(0));
    assertTrue(isAtlasConvertedItem);
  }

  @Test
  public void
      testIsAtlasConvertedItemReturnsFalseWhenAtlasConvertedFlagIsFalseInContainerItemMiscInfo() {
    Container container = getMockContainer();
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(ReceivingConstants.IS_ATLAS_CONVERTED_ITEM, "false");
    container.getContainerItems().get(0).setContainerItemMiscInfo(containerItemMiscInfo);
    boolean isAtlasConvertedItem =
        ContainerUtils.isAtlasConvertedItem(container.getContainerItems().get(0));
    assertFalse(isAtlasConvertedItem);
  }

  @Test
  public void testIsAtlasConvertedItemReturnsFalseWhenContainerItemMiscInfoIsEmpty() {
    boolean isAtlasConvertedItem =
        ContainerUtils.isAtlasConvertedItem(getMockContainer().getContainerItems().get(0));
    assertFalse(isAtlasConvertedItem);
  }

  @Test
  public void testReceivedContainerQuantityBySSCC() {
    doReturn(1)
        .when(containerPersisterService)
        .receivedContainerQuantityBySSCCAndStatus(anyString());
    int receivedQty = rdcContainerUtils.receivedContainerQuantityBySSCC("SSCC");
    assertEquals(1, 1);
    verify(containerPersisterService, times(1))
        .receivedContainerQuantityBySSCCAndStatus(anyString());
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testProcessWarehouseDamageAdjustmentMessageThrowsExceptionOnContainerValidation() {
    Container container = getMockContainer();
    container.getContainerItems().get(0).setAsrsAlignment("SYM1");
    container.getContainerItems().get(0).setSlotType(ReceivingConstants.PRIME_SLOT_TYPE);

    Map<String, String> miscInfoMap = new HashMap<>();
    miscInfoMap.put(ReceivingConstants.IS_ATLAS_CONVERTED_ITEM, "true");
    container.getContainerItems().get(0).setContainerItemMiscInfo(miscInfoMap);

    when(containerAdjustmentHelper.validateContainerForAdjustment(
            any(Container.class), any(HttpHeaders.class)))
        .thenReturn(
            new CancelContainerResponse(
                container.getTrackingId(),
                ReceivingException.CONTAINER_ALREADY_CANCELED_ERROR_CODE,
                ReceivingException.CONTAINER_ALREADY_CANCELED_ERROR_MSG));

    rdcContainerUtils.processWarehouseDamageAdjustments(container, -10, httpHeaders);

    verify(containerAdjustmentHelper, times(1))
        .validateContainerForAdjustment(any(Container.class), any(HttpHeaders.class));
  }

  @Test
  public void
      testProcessWarehouseDamageAdjustmentMessageDidNotPublishPutAwayUpdateOrDeleteMsgToHawkEyeForNonAtlasConvertedItem() {
    Container container = getMockContainer();

    Map<String, String> miscInfoMap = new HashMap<>();
    miscInfoMap.put(ReceivingConstants.IS_ATLAS_CONVERTED_ITEM, "false");
    container.getContainerItems().get(0).setContainerItemMiscInfo(miscInfoMap);

    when(containerAdjustmentHelper.validateContainerForAdjustment(
            any(Container.class), any(HttpHeaders.class)))
        .thenReturn(null);

    rdcContainerUtils.processWarehouseDamageAdjustments(container, -10, httpHeaders);

    verify(containerAdjustmentHelper, times(1))
        .validateContainerForAdjustment(any(Container.class), any(HttpHeaders.class));
  }

  @Test
  public void
      testProcessWarehouseDamageAdjustmentMessagePublishPutAwayUpdateMsgToHawkEyeWhenDamagedQtyIsLesserThanOriginalQuantity() {
    Container container = getMockContainer();
    container
        .getContainerItems()
        .get(0)
        .setAsrsAlignment(ReceivingConstants.SYM_CASE_PACK_ASRS_VALUE);
    container.getContainerItems().get(0).setSlotType(ReceivingConstants.PRIME_SLOT_TYPE);

    Map<String, String> miscInfoMap = new HashMap<>();
    miscInfoMap.put(ReceivingConstants.IS_ATLAS_CONVERTED_ITEM, "true");
    container.getContainerItems().get(0).setContainerItemMiscInfo(miscInfoMap);

    argumentCaptor = ArgumentCaptor.forClass(String.class);
    when(appConfig.getValidSymAsrsAlignmentValues())
        .thenReturn(
            Arrays.asList(
                ReceivingConstants.SYM_CASE_PACK_ASRS_VALUE,
                ReceivingConstants.SYM_BRKPK_ASRS_VALUE));
    when(containerAdjustmentHelper.validateContainerForAdjustment(
            any(Container.class), any(HttpHeaders.class)))
        .thenReturn(null);
    doNothing()
        .when(symboticPutawayPublishHelper)
        .publishSymPutawayUpdateOrDeleteMessage(
            anyString(), any(ContainerItem.class), anyString(), anyInt(), any(HttpHeaders.class));

    rdcContainerUtils.processWarehouseDamageAdjustments(
        container, -20, MockHttpHeaders.getHeaders());

    verify(containerAdjustmentHelper, times(1))
        .validateContainerForAdjustment(any(Container.class), any(HttpHeaders.class));
    verify(symboticPutawayPublishHelper, times(1))
        .publishSymPutawayUpdateOrDeleteMessage(
            anyString(),
            any(ContainerItem.class),
            argumentCaptor.capture(),
            anyInt(),
            any(HttpHeaders.class));

    assertTrue(
        argumentCaptor.getValue().equalsIgnoreCase(ReceivingConstants.PUTAWAY_UPDATE_ACTION));
  }

  @Test
  public void testBuildContainerForAutoReceiveRequest() throws IOException {
    AutoReceiveRequest autoReceiveRequest = new AutoReceiveRequest();
    autoReceiveRequest.setDeliveryNumber(2121212L);
    autoReceiveRequest.setDoorNumber("121");
    autoReceiveRequest.setLpn("a60202323232323");
    autoReceiveRequest.setPurchaseReferenceNumber("823232323");
    autoReceiveRequest.setPurchaseReferenceLineNumber(1);
    autoReceiveRequest.setMessageId("2hf9jf-3rwewewe-2rwe");
    String userId = "testUser";
    String lpn = "a60320023232323";
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setLabelTrackingId(lpn);
    List<Destination> destinations = new ArrayList<>();
    Destination destination = new Destination();
    destination.setSlot("R8000");
    destinations.add(destination);
    receivedContainer.setDestinations(destinations);
    receivedContainer.setLabelType(InventoryLabelType.R8000_DA_FULL_CASE.getType());
    receivedContainer.setFulfillmentMethod(FulfillmentMethodType.CASE_PACK_RECEIVING.getType());

    when(rdcInstructionUtils.isDADocument(any(DeliveryDocument.class))).thenReturn(true);
    when(rdcReceivingUtils.isWhpkReceiving(
            any(DeliveryDocumentLine.class), any(ReceiveInstructionRequest.class)))
        .thenReturn(false);

    Container container =
        rdcContainerUtils.buildContainer(
            autoReceiveRequest.getDoorNumber(),
            null,
            autoReceiveRequest.getDeliveryNumber(),
            autoReceiveRequest.getMessageId(),
            MockDeliveryDocuments.getDeliveryDocumentsForSSTK().get(0),
            userId,
            receivedContainer,
            null,
            null);
    assertNotNull(container);
    assertEquals(ContainerType.CASE.getText(), container.getContainerType());
    assertEquals(container.getDestination().get(ReceivingConstants.SLOT), "R8000");
  }

  @Test
  public void testBuildContainerForMFCReceiveRequest() throws IOException {
    AutoReceiveRequest autoReceiveRequest = new AutoReceiveRequest();
    autoReceiveRequest.setDeliveryNumber(2121212L);
    autoReceiveRequest.setDoorNumber("121");
    autoReceiveRequest.setLpn("a60202323232323");
    autoReceiveRequest.setPurchaseReferenceNumber("823232323");
    autoReceiveRequest.setPurchaseReferenceLineNumber(1);
    autoReceiveRequest.setMessageId("2hf9jf-3rwewewe-2rwe");
    String userId = "testUser";
    String lpn = "a60320023232323";
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setLabelTrackingId(lpn);
    List<Destination> destinations = new ArrayList<>();
    Destination destination = new Destination();
    destination.setSlot("R8000");
    destinations.add(destination);
    receivedContainer.setDestinations(destinations);
    receivedContainer.setLabelType(InventoryLabelType.R8000_DA_FULL_CASE.getType());
    receivedContainer.setFulfillmentMethod(FulfillmentMethodType.CASE_PACK_RECEIVING.getType());
    receivedContainer.setDestType("MFC");

    when(rdcInstructionUtils.isDADocument(any(DeliveryDocument.class))).thenReturn(true);
    when(rdcReceivingUtils.isWhpkReceiving(
            any(DeliveryDocumentLine.class), any(ReceiveInstructionRequest.class)))
        .thenReturn(false);

    Container container =
        rdcContainerUtils.buildContainer(
            autoReceiveRequest.getDoorNumber(),
            null,
            autoReceiveRequest.getDeliveryNumber(),
            autoReceiveRequest.getMessageId(),
            MockDeliveryDocuments.getDeliveryDocumentsForSSTK().get(0),
            userId,
            receivedContainer,
            null,
            null);
    assertNotNull(container);
    assertEquals(ContainerType.CASE.getText(), container.getContainerType());
    assertEquals(container.getDestination().get(ReceivingConstants.SLOT), "R8000");
    assertEquals(container.getContainerMiscInfo().get(ReceivingConstants.DEST_TYPE), "MFC");
  }

  @Test
  public void testBuildContainerForDAReceivingNonAtlasItems_PickedInventoryStatus()
      throws IOException {
    InstructionRequest instructionRequest = MockRdcInstruction.getInstructionRequest();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    String userId = "testUser";
    String lpn = "a60320023232323";
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setLabelTrackingId(lpn);
    receivedContainer.setLabelType(InventoryLabelType.R8000_DA_FULL_CASE.getType());
    List<Destination> destinations = new ArrayList<>();
    Destination destination = new Destination();
    destination.setSlot("R8000");
    destinations.add(destination);
    receivedContainer.setDestinations(destinations);
    when(rdcReceivingUtils.isWhpkReceiving(
            any(DeliveryDocumentLine.class), any(ReceiveInstructionRequest.class)))
        .thenReturn(false);
    when(rdcInstructionUtils.isDADocument(any(DeliveryDocument.class))).thenReturn(true);
    Container container =
        rdcContainerUtils.buildContainer(
            instructionRequest.getDoorNumber(),
            null,
            343434L,
            instructionRequest.getMessageId(),
            deliveryDocument,
            userId,
            receivedContainer,
            null,
            null);
    assertNotNull(container);
    assertEquals(ContainerType.CASE.getText(), container.getContainerType());
    assertEquals(container.getDestination().get(ReceivingConstants.SLOT), "R8000");
    assertEquals(container.getInventoryStatus(), InventoryStatus.PICKED.name());
    assertTrue(MapUtils.isNotEmpty(container.getContainerMiscInfo()));
  }

  @Test
  public void
      testBuildContainerForDAReceivingNonAtlasItems_PopulateInnerPicksWithStorePickBatchDetails()
          throws IOException {
    InstructionRequest instructionRequest = MockRdcInstruction.getInstructionRequest();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack().get(0);
    String userId = "testUser";
    String lpn = "a60320023232323";
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setLabelTrackingId(lpn);
    receivedContainer.setParentTrackingId("a6033232332");
    receivedContainer.setBatch(323);
    receivedContainer.setPickBatch(323);
    receivedContainer.setAisle("E");
    receivedContainer.setLabelType(InventoryLabelType.DA_BREAK_PACK_INNER_PICK.getType());
    receivedContainer.setInventoryLabelType(InventoryLabelType.DA_BREAK_PACK_INNER_PICK);
    List<Destination> destinations = new ArrayList<>();
    Destination destination = new Destination();
    destination.setSlot("P1001");
    destinations.add(destination);
    receivedContainer.setDestinations(destinations);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_SORTER_ENABLED_ON_KAFKA,
            false))
        .thenReturn(false);
    when(rdcReceivingUtils.isWhpkReceiving(
            any(DeliveryDocumentLine.class), any(ReceiveInstructionRequest.class)))
        .thenReturn(false);
    when(rdcInstructionUtils.isDADocument(any(DeliveryDocument.class))).thenReturn(true);
    Container container =
        rdcContainerUtils.buildContainer(
            instructionRequest.getDoorNumber(),
            null,
            343434L,
            instructionRequest.getMessageId(),
            deliveryDocument,
            userId,
            receivedContainer,
            null,
            null);
    assertNotNull(container);
    assertEquals(ContainerType.VIRTUAL.getText(), container.getContainerType());
    assertEquals(container.getDestination().get(ReceivingConstants.SLOT), "P1001");
    assertEquals(container.getInventoryStatus(), InventoryStatus.ALLOCATED.name());
    assertTrue(MapUtils.isNotEmpty(container.getContainerMiscInfo()));
  }

  @Test
  public void testBuildContainerForDAReceivingAtlasItems_PickedInventoryStatus()
      throws IOException {
    InstructionRequest instructionRequest = MockRdcInstruction.getInstructionRequest();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    String userId = "testUser";
    String lpn = "a60320023232323";
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setLabelTrackingId(lpn);
    receivedContainer.setLabelType(InventoryLabelType.R8000_DA_FULL_CASE.getType());
    List<Destination> destinations = new ArrayList<>();
    Destination destination = new Destination();
    destination.setSlot("R8000");
    destinations.add(destination);
    receivedContainer.setDestinations(destinations);
    when(rdcReceivingUtils.isWhpkReceiving(
            any(DeliveryDocumentLine.class), any(ReceiveInstructionRequest.class)))
        .thenReturn(false);
    when(rdcInstructionUtils.isDADocument(any(DeliveryDocument.class))).thenReturn(true);
    Container container =
        rdcContainerUtils.buildContainer(
            instructionRequest.getDoorNumber(),
            null,
            343434L,
            instructionRequest.getMessageId(),
            deliveryDocument,
            userId,
            receivedContainer,
            null,
            null);
    assertNotNull(container);
    assertEquals(ContainerType.CASE.getText(), container.getContainerType());
    assertEquals(container.getDestination().get(ReceivingConstants.SLOT), "R8000");
    assertEquals(container.getInventoryStatus(), InventoryStatus.PICKED.name());
    assertTrue(MapUtils.isNotEmpty(container.getContainerMiscInfo()));
  }

  @Test
  public void testBuildContainerForDAReceivingAtlasItems_AllocatedInventoryStatus_RoutingLabel()
      throws IOException {
    InstructionRequest instructionRequest = MockRdcInstruction.getInstructionRequest();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    String userId = "testUser";
    String lpn = "a60320023232323";
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setLabelTrackingId(lpn);
    receivedContainer.setLabelType(InventoryLabelType.R8000_DA_FULL_CASE.getType());
    receivedContainer.setInventoryLabelType(InventoryLabelType.R8000_DA_FULL_CASE);
    receivedContainer.setRoutingLabel(true);
    List<Destination> destinations = new ArrayList<>();
    Destination destination = new Destination();
    destination.setSlot("R8000");
    destinations.add(destination);
    receivedContainer.setDestinations(destinations);
    when(rdcReceivingUtils.isWhpkReceiving(
            any(DeliveryDocumentLine.class), any(ReceiveInstructionRequest.class)))
        .thenReturn(false);
    when(rdcInstructionUtils.isDADocument(any(DeliveryDocument.class))).thenReturn(true);
    Container container =
        rdcContainerUtils.buildContainer(
            instructionRequest.getDoorNumber(),
            null,
            343434L,
            instructionRequest.getMessageId(),
            deliveryDocument,
            userId,
            receivedContainer,
            null,
            null);
    assertNotNull(container);
    assertEquals(ContainerType.CASE.getText(), container.getContainerType());
    assertEquals(container.getDestination().get(ReceivingConstants.SLOT), "R8000");
    assertEquals(container.getInventoryStatus(), InventoryStatus.ALLOCATED.name());
    assertTrue(MapUtils.isNotEmpty(container.getContainerMiscInfo()));
  }

  @Test
  public void testBuildContainerForDAReceivingAtlasItems_AllocatedInventoryStatus_BreakPackLabels()
      throws IOException {
    InstructionRequest instructionRequest = MockRdcInstruction.getInstructionRequest();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocument.setChannelMethod("test");
    deliveryDocument.setMessageNumber("%");
    String userId = "testUser";
    String lpn = "a60320023232323";
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setLabelTrackingId(lpn);
    receivedContainer.setLabelType(InventoryLabelType.DA_BREAK_PACK_PUT_INDUCT.getType());
    receivedContainer.setRoutingLabel(false);
    List<Destination> destinations = new ArrayList<>();
    Destination destination = new Destination();
    destination.setSlot("P1001");
    destinations.add(destination);
    receivedContainer.setDestinations(destinations);
    when(rdcReceivingUtils.isWhpkReceiving(
            any(DeliveryDocumentLine.class), any(ReceiveInstructionRequest.class)))
        .thenReturn(false);
    when(rdcInstructionUtils.isDADocument(any(DeliveryDocument.class))).thenReturn(true);
    Container container =
        rdcContainerUtils.buildContainer(
            instructionRequest.getDoorNumber(),
            null,
            343434L,
            instructionRequest.getMessageId(),
            deliveryDocument,
            userId,
            receivedContainer,
            null,
            null);
    assertNotNull(container);
    assertEquals(ContainerType.CASE.getText(), container.getContainerType());
    assertEquals(container.getDestination().get(ReceivingConstants.SLOT), "P1001");
    assertEquals(container.getInventoryStatus(), InventoryStatus.ALLOCATED.name());
    assertTrue(MapUtils.isNotEmpty(container.getContainerMiscInfo()));
  }

  @Test
  public void
      testBuildContainerForDAReceivingAtlasItems_AllocatedInventoryStatus_BreakPackConveyPicks_ContainerTypeCase()
          throws IOException {
    InstructionRequest instructionRequest = MockRdcInstruction.getInstructionRequest();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setItemPackAndHandlingCode(RdcConstants.DA_BREAK_PACK_CONVEY_PICKS_ITEM_HANDLING_CODE);
    String userId = "testUser";
    String lpn = "a60320023232323";
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setLabelTrackingId(lpn);
    receivedContainer.setLabelType(InventoryLabelType.R8000_DA_FULL_CASE.getType());
    receivedContainer.setInventoryLabelType(InventoryLabelType.R8000_DA_FULL_CASE);
    receivedContainer.setRoutingLabel(false);
    List<Destination> destinations = new ArrayList<>();
    Destination destination = new Destination();
    destination.setSlot("R8000");
    destinations.add(destination);
    receivedContainer.setDestinations(destinations);
    when(rdcReceivingUtils.isWhpkReceiving(
            any(DeliveryDocumentLine.class), any(ReceiveInstructionRequest.class)))
        .thenReturn(false);
    when(rdcInstructionUtils.isDADocument(any(DeliveryDocument.class))).thenReturn(true);
    Container container =
        rdcContainerUtils.buildContainer(
            instructionRequest.getDoorNumber(),
            null,
            343434L,
            instructionRequest.getMessageId(),
            deliveryDocument,
            userId,
            receivedContainer,
            null,
            null);
    assertNotNull(container);
    assertEquals(ContainerType.CASE.getText(), container.getContainerType());
    assertEquals(container.getDestination().get(ReceivingConstants.SLOT), "R8000");
    assertEquals(container.getInventoryStatus(), InventoryStatus.PICKED.name());
    assertTrue(MapUtils.isNotEmpty(container.getContainerMiscInfo()));
  }

  @Test
  public void
      testBuildContainerForDAReceivingAtlasItems_AllocatedInventoryStatus_BreakPack_Put_InnerPicks()
          throws IOException {
    InstructionRequest instructionRequest = MockRdcInstruction.getInstructionRequest();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    String userId = "testUser";
    String lpn = "032323232323232";
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setLabelTrackingId(lpn);
    receivedContainer.setLabelType(InventoryLabelType.DA_BREAK_PACK_INNER_PICK.getType());
    receivedContainer.setInventoryLabelType(InventoryLabelType.DA_BREAK_PACK_INNER_PICK);
    receivedContainer.setRoutingLabel(false);
    receivedContainer.setParentTrackingId("a06020223232323232");
    List<Destination> destinations = new ArrayList<>();
    Destination destination = new Destination();
    destination.setSlot("P1001");
    destinations.add(destination);
    receivedContainer.setDestinations(destinations);
    when(rdcReceivingUtils.isWhpkReceiving(
            any(DeliveryDocumentLine.class), any(ReceiveInstructionRequest.class)))
        .thenReturn(false);
    when(rdcInstructionUtils.isDADocument(any(DeliveryDocument.class))).thenReturn(true);
    Container container =
        rdcContainerUtils.buildContainer(
            instructionRequest.getDoorNumber(),
            null,
            343434L,
            instructionRequest.getMessageId(),
            deliveryDocument,
            userId,
            receivedContainer,
            null,
            null);
    assertNotNull(container);
    assertEquals(ContainerType.VIRTUAL.getText(), container.getContainerType());
    assertEquals(container.getDestination().get(ReceivingConstants.SLOT), "P1001");
    assertEquals(container.getInventoryStatus(), InventoryStatus.ALLOCATED.name());
    assertTrue(MapUtils.isNotEmpty(container.getContainerMiscInfo()));
  }

  @Test
  public void testBuildContainerForDAReceivingHasVendorPackContainerType() throws IOException {
    InstructionRequest instructionRequest = MockRdcInstruction.getInstructionRequest();
    Instruction instruction = MockRdcInstruction.getInstruction();
    String userId = "testUser";
    String lpn = "a60320023232323";
    String slotId = "V0051";
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setLabelTrackingId(lpn);
    List<Destination> destinations = new ArrayList<>();
    Destination destination = new Destination();
    destination.setSlot("R8000");
    destinations.add(destination);
    receivedContainer.setDestinations(destinations);
    when(rdcReceivingUtils.isWhpkReceiving(
            any(DeliveryDocumentLine.class), any(ReceiveInstructionRequest.class)))
        .thenReturn(false);
    when(rdcInstructionUtils.isDADocument(any(DeliveryDocument.class))).thenReturn(true);
    Container container =
        rdcContainerUtils.buildContainer(
            instructionRequest.getDoorNumber(),
            null,
            343434L,
            instructionRequest.getMessageId(),
            MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0),
            userId,
            receivedContainer,
            null,
            null);
    assertNotNull(container);
    assertEquals(ContainerType.CASE.getText(), container.getContainerType());
    assertEquals(container.getDestination().get(ReceivingConstants.SLOT), "R8000");
    assertTrue(MapUtils.isNotEmpty(container.getContainerMiscInfo()));
  }

  @Test
  public void testBuildContainerForDAReceivingHasPalletContainerTypeForPalletPull()
      throws IOException {
    InstructionRequest instructionRequest = MockRdcInstruction.getInstructionRequest();
    String userId = "testUser";
    String lpn = "a60320023232323";
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setLabelTrackingId(lpn);
    receivedContainer.setPalletPullByStore(true);
    receivedContainer.setLabelType(InventoryLabelType.R8000_DA_FULL_CASE.getType());
    receivedContainer.setInventoryLabelType(InventoryLabelType.R8000_DA_FULL_CASE);
    List<Destination> destinations = new ArrayList<>();
    Destination destination = new Destination();
    destination.setSlot("R8001");
    destinations.add(destination);
    receivedContainer.setDestinations(destinations);
    when(rdcReceivingUtils.isWhpkReceiving(
            any(DeliveryDocumentLine.class), any(ReceiveInstructionRequest.class)))
        .thenReturn(false);
    when(rdcInstructionUtils.isDADocument(any(DeliveryDocument.class))).thenReturn(true);
    Container container =
        rdcContainerUtils.buildContainer(
            instructionRequest.getDoorNumber(),
            null,
            343434L,
            instructionRequest.getMessageId(),
            MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0),
            userId,
            receivedContainer,
            null,
            null);
    assertNotNull(container);
    assertEquals(ContainerType.PALLET.getText(), container.getContainerType());
    assertEquals(container.getDestination().get(ReceivingConstants.SLOT), "R8001");
    assertTrue(MapUtils.isNotEmpty(container.getContainerMiscInfo()));
  }

  @Test
  public void testBuildContainerForDAReceivingHasVendorPackContainerType_AtlasItem()
      throws IOException {
    InstructionRequest instructionRequest = MockRdcInstruction.getInstructionRequest();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setAtlasConvertedItem(Boolean.TRUE);
    String userId = "testUser";
    String lpn = "a60320023232323";
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setLabelTrackingId(lpn);
    List<Destination> destinations = new ArrayList<>();
    Destination destination = new Destination();
    destination.setSlot("R8000");
    destinations.add(destination);
    receivedContainer.setDestinations(destinations);

    when(rdcInstructionUtils.isDADocument(any(DeliveryDocument.class))).thenReturn(true);
    when(rdcReceivingUtils.isWhpkReceiving(
            any(DeliveryDocumentLine.class), any(ReceiveInstructionRequest.class)))
        .thenReturn(false);

    Container container =
        rdcContainerUtils.buildContainer(
            instructionRequest.getDoorNumber(),
            null,
            343434L,
            instructionRequest.getMessageId(),
            deliveryDocument,
            userId,
            receivedContainer,
            null,
            null);
    assertNotNull(container);
    assertEquals(ContainerType.CASE.getText(), container.getContainerType());
  }

  @Test
  public void testBuildContainerForSSTKReceivingHasVendorPackContainerType_AtlasItem()
      throws IOException {
    InstructionRequest instructionRequest = MockRdcInstruction.getInstructionRequest();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setAtlasConvertedItem(Boolean.TRUE);
    String userId = "testUser";
    String lpn = "a60320023232323";
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setLabelTrackingId(lpn);
    List<Destination> destinations = new ArrayList<>();
    Destination destination = new Destination();
    destination.setSlot("R8000");
    destinations.add(destination);
    receivedContainer.setDestinations(destinations);

    when(rdcInstructionUtils.isSSTKDocument(any(DeliveryDocument.class))).thenReturn(true);

    Container container =
        rdcContainerUtils.buildContainer(
            instructionRequest.getDoorNumber(),
            null,
            343434L,
            instructionRequest.getMessageId(),
            deliveryDocument,
            userId,
            receivedContainer,
            null,
            null);
    assertNotNull(container);
    assertNotNull(container.getMessageId());
    assertEquals(ContainerType.PALLET.getText(), container.getContainerType());
  }

  @Test
  public void
      testBuildContainerForDAReceivingHasWarehousePack_ParentTrackingId_ContainerType_AtlasItem_WarehousePack()
          throws IOException {
    InstructionRequest instructionRequest = MockRdcInstruction.getInstructionRequest();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setAtlasConvertedItem(Boolean.TRUE);
    String userId = "testUser";
    String lpn = "a60320023232323";
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setLabelType(InventoryLabelType.DA_BREAK_PACK_INNER_PICK.getType());
    receivedContainer.setInventoryLabelType(InventoryLabelType.DA_BREAK_PACK_INNER_PICK);
    receivedContainer.setLabelTrackingId(lpn);
    List<Destination> destinations = new ArrayList<>();
    Destination destination = new Destination();
    destination.setSlot("R8000");
    destinations.add(destination);
    receivedContainer.setDestinations(destinations);
    receivedContainer.setParentTrackingId("Lpn1");

    when(rdcInstructionUtils.isDADocument(any(DeliveryDocument.class))).thenReturn(true);

    Container container =
        rdcContainerUtils.buildContainer(
            instructionRequest.getDoorNumber(),
            null,
            343434L,
            instructionRequest.getMessageId(),
            deliveryDocument,
            userId,
            receivedContainer,
            null,
            null);
    assertNotNull(container);
    assertNotNull(container.getMessageId());
    assertEquals(ContainerType.VIRTUAL.getText(), container.getContainerType());
  }

  @Test
  public void testBuildContainerForDAReceivingBreakPackInductContainerType_AtlasItem_VendorPack()
      throws IOException {
    InstructionRequest instructionRequest = MockRdcInstruction.getInstructionRequest();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setAtlasConvertedItem(Boolean.TRUE);
    String userId = "testUser";
    String lpn = "a60320023232323";
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setLabelType(InventoryLabelType.DA_BREAK_PACK_PUT_INDUCT.getType());
    receivedContainer.setLabelTrackingId(lpn);
    List<Destination> destinations = new ArrayList<>();
    Destination destination = new Destination();
    destination.setSlot("P1001");
    destinations.add(destination);
    receivedContainer.setDestinations(destinations);

    when(rdcInstructionUtils.isDADocument(any(DeliveryDocument.class))).thenReturn(true);

    Container container =
        rdcContainerUtils.buildContainer(
            instructionRequest.getDoorNumber(),
            null,
            343434L,
            instructionRequest.getMessageId(),
            deliveryDocument,
            userId,
            receivedContainer,
            null,
            null);
    assertNotNull(container);
    assertNotNull(container.getMessageId());
    assertEquals(ContainerType.CASE.getText(), container.getContainerType());
  }

  @Test
  public void testBuildContainerForDAReceivingHasWarehousePack_ConveyPicks_ContainerType_AtlasItem()
      throws IOException {
    InstructionRequest instructionRequest = MockRdcInstruction.getInstructionRequest();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setAtlasConvertedItem(Boolean.TRUE);
    deliveryDocumentLine
        .getAdditionalInfo()
        .setItemPackAndHandlingCode(RdcConstants.DA_BREAK_PACK_CONVEY_PICKS_ITEM_HANDLING_CODE);
    String userId = "testUser";
    String lpn = "a60320023232323";
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setLabelTrackingId(lpn);
    List<Destination> destinations = new ArrayList<>();
    Destination destination = new Destination();
    destination.setSlot("R8000");
    destinations.add(destination);
    receivedContainer.setDestinations(destinations);
    when(rdcInstructionUtils.isDADocument(any(DeliveryDocument.class))).thenReturn(true);
    when(rdcReceivingUtils.isWhpkReceiving(
            any(DeliveryDocumentLine.class), nullable(ReceiveInstructionRequest.class)))
        .thenReturn(true);

    Container container =
        rdcContainerUtils.buildContainer(
            instructionRequest.getDoorNumber(),
            null,
            343434L,
            instructionRequest.getMessageId(),
            deliveryDocument,
            userId,
            receivedContainer,
            null,
            null);
    assertNotNull(container);
    assertNotNull(container.getMessageId());
    assertEquals(ContainerType.VIRTUAL.getText(), container.getContainerType());
  }

  @Test
  public void
      testBuildContainerForDAReceivingHasWarehousePack_LessThanACase_ContainerType_AtlasItem()
          throws IOException {
    InstructionRequest instructionRequest = MockRdcInstruction.getInstructionRequest();
    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setIsLessThanCase(Boolean.TRUE);
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setAtlasConvertedItem(Boolean.TRUE);
    deliveryDocumentLine
        .getAdditionalInfo()
        .setHandlingCode(RdcConstants.PALLET_RECEIVING_HANDLING_CODE);
    String userId = "testUser";
    String lpn = "a60320023232323";
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setLabelTrackingId(lpn);
    List<Destination> destinations = new ArrayList<>();
    Destination destination = new Destination();
    destination.setSlot("R8000");
    destinations.add(destination);
    receivedContainer.setDestinations(destinations);
    when(rdcInstructionUtils.isDADocument(any(DeliveryDocument.class))).thenReturn(true);
    when(rdcReceivingUtils.isWhpkReceiving(
            any(DeliveryDocumentLine.class), any(ReceiveInstructionRequest.class)))
        .thenReturn(true);

    Container container =
        rdcContainerUtils.buildContainer(
            instructionRequest.getDoorNumber(),
            null,
            343434L,
            instructionRequest.getMessageId(),
            deliveryDocument,
            userId,
            receivedContainer,
            null,
            receiveInstructionRequest);
    assertNotNull(container);
    assertNotNull(container.getMessageId());
    assertEquals(ContainerType.VIRTUAL.getText(), container.getContainerType());
  }

  @Test
  public void testBuildContainer_Case_CasePackPallet_NonAtlasItem() throws IOException {
    InstructionRequest instructionRequest = MockRdcInstruction.getInstructionRequest();
    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setIsLessThanCase(Boolean.TRUE);
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setAtlasConvertedItem(Boolean.FALSE);
    deliveryDocumentLine
        .getAdditionalInfo()
        .setHandlingCode(RdcConstants.PALLET_RECEIVING_HANDLING_CODE);
    deliveryDocumentLine.getAdditionalInfo().setPackTypeCode(RdcConstants.CASE_PACK_TYPE_CODE);
    String userId = "testUser";
    String lpn = "a60320023232323";
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setLabelTrackingId(lpn);
    List<Destination> destinations = new ArrayList<>();
    Destination destination = new Destination();
    destination.setSlot("R8000");
    destinations.add(destination);
    receivedContainer.setDestinations(destinations);
    when(rdcInstructionUtils.isDADocument(any(DeliveryDocument.class))).thenReturn(true);
    doCallRealMethod()
        .when(rdcInstructionUtils)
        .isCasePackPalletReceiving(any(DeliveryDocumentLine.class));
    Container container =
        rdcContainerUtils.buildContainer(
            instructionRequest.getDoorNumber(),
            null,
            343434L,
            instructionRequest.getMessageId(),
            deliveryDocument,
            userId,
            receivedContainer,
            null,
            receiveInstructionRequest);
    assertNotNull(container);
    assertNotNull(container.getMessageId());
    assertEquals(ContainerType.CASE.getText(), container.getContainerType());
  }

  @Test
  public void testBuildContainer_Case_CasePackPallet_AtlasItem() throws IOException {
    InstructionRequest instructionRequest = MockRdcInstruction.getInstructionRequest();
    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setIsLessThanCase(Boolean.TRUE);
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setAtlasConvertedItem(Boolean.TRUE);
    deliveryDocumentLine
        .getAdditionalInfo()
        .setHandlingCode(RdcConstants.PALLET_RECEIVING_HANDLING_CODE);
    deliveryDocumentLine.getAdditionalInfo().setPackTypeCode(RdcConstants.CASE_PACK_TYPE_CODE);
    String userId = "testUser";
    String lpn = "a60320023232323";
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setLabelTrackingId(lpn);
    List<Destination> destinations = new ArrayList<>();
    Destination destination = new Destination();
    destination.setSlot("R8000");
    destinations.add(destination);
    receivedContainer.setDestinations(destinations);
    when(rdcInstructionUtils.isDADocument(any(DeliveryDocument.class))).thenReturn(true);
    doCallRealMethod()
        .when(rdcInstructionUtils)
        .isCasePackPalletReceiving(any(DeliveryDocumentLine.class));
    Container container =
        rdcContainerUtils.buildContainer(
            instructionRequest.getDoorNumber(),
            null,
            343434L,
            instructionRequest.getMessageId(),
            deliveryDocument,
            userId,
            receivedContainer,
            null,
            receiveInstructionRequest);
    assertNotNull(container);
    assertNotNull(container.getMessageId());
    assertEquals(ContainerType.PALLET.getText(), container.getContainerType());
  }

  @Test
  public void testBuildContainerForDSDCRequest() {
    DsdcReceiveResponse dsdcReceiveResponse = new DsdcReceiveResponse();
    dsdcReceiveResponse.setLabel_bar_code("03223232323232323");
    dsdcReceiveResponse.setPo_nbr("432323");
    dsdcReceiveResponse.setBatch("212");
    dsdcReceiveResponse.setAuditFlag("N");
    dsdcReceiveResponse.setDiv("3");
    dsdcReceiveResponse.setDept("12");
    dsdcReceiveResponse.setDccarton("2323");
    dsdcReceiveResponse.setPocode("43");
    String labelTrackingId = "03223232323232323";
    List<ContainerItem> containerItems =
        rdcContainerUtils.buildContainerItem(
            dsdcReceiveResponse, labelTrackingId, 4, new ContainerItem());
    assertFalse(containerItems.isEmpty());
  }

  @Test
  public void testBuildContainerForDAReceivingHasPalletContainerType() throws IOException {
    InstructionRequest instructionRequest = MockRdcInstruction.getInstructionRequest();
    Instruction instruction = MockRdcInstruction.getInstruction();
    String userId = "testUser";
    String lpn = "a60320023232323";
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setLabelTrackingId(lpn);
    List<Destination> destinations = new ArrayList<>();
    Destination destination = new Destination();
    destination.setSlot("R8000");
    destinations.add(destination);
    receivedContainer.setDestinations(destinations);
    when(rdcInstructionUtils.isDADocument(any(DeliveryDocument.class))).thenReturn(true);
    when(rdcReceivingUtils.isWhpkReceiving(
            any(DeliveryDocumentLine.class), any(ReceiveInstructionRequest.class)))
        .thenReturn(false);

    Container container =
        rdcContainerUtils.buildContainer(
            instructionRequest.getDoorNumber(),
            instruction.getId(),
            instruction.getDeliveryNumber(),
            instructionRequest.getMessageId(),
            MockDeliveryDocuments.getDeliveryDocumentsForSSTK().get(0),
            userId,
            receivedContainer,
            null,
            null);

    assertNotNull(container);
    assertNotNull(container.getMessageId());
    assertEquals(ContainerType.CASE.getText(), container.getContainerType());
    assertEquals(container.getDestination().get(ReceivingConstants.SLOT), "R8000");
  }

  @Test
  public void
      testProcessWarehouseDamageAdjustmentMessagePublishPutAwayUpdateMsgToHawkEyeWhenDamagedQtyEqualsToOriginalQuantity() {
    Container container = getMockContainer();
    container
        .getContainerItems()
        .get(0)
        .setAsrsAlignment(ReceivingConstants.SYM_CASE_PACK_ASRS_VALUE);
    container.getContainerItems().get(0).setSlotType(ReceivingConstants.PRIME_SLOT_TYPE);

    Map<String, String> miscInfoMap = new HashMap<>();
    miscInfoMap.put(ReceivingConstants.IS_ATLAS_CONVERTED_ITEM, "true");
    container.getContainerItems().get(0).setContainerItemMiscInfo(miscInfoMap);

    argumentCaptor = ArgumentCaptor.forClass(String.class);
    when(appConfig.getValidSymAsrsAlignmentValues())
        .thenReturn(
            Arrays.asList(
                ReceivingConstants.SYM_BRKPK_ASRS_VALUE,
                ReceivingConstants.SYM_CASE_PACK_ASRS_VALUE));
    when(containerAdjustmentHelper.validateContainerForAdjustment(
            any(Container.class), any(HttpHeaders.class)))
        .thenReturn(null);
    doNothing()
        .when(symboticPutawayPublishHelper)
        .publishSymPutawayUpdateOrDeleteMessage(
            anyString(), any(ContainerItem.class), anyString(), anyInt(), any(HttpHeaders.class));

    rdcContainerUtils.processWarehouseDamageAdjustments(
        container, -80, MockHttpHeaders.getHeaders());

    verify(containerAdjustmentHelper, times(1))
        .validateContainerForAdjustment(any(Container.class), any(HttpHeaders.class));
    verify(symboticPutawayPublishHelper, times(1))
        .publishSymPutawayUpdateOrDeleteMessage(
            anyString(),
            any(ContainerItem.class),
            argumentCaptor.capture(),
            anyInt(),
            any(HttpHeaders.class));

    assertTrue(
        argumentCaptor.getValue().equalsIgnoreCase(ReceivingConstants.PUTAWAY_DELETE_ACTION));
  }

  @Test
  public void testPublishContainersToInventory_Enabled() {
    doNothing().when(containerService).publishMultipleContainersToInventory(anyList());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    rdcContainerUtils.publishContainersToInventory(getMockContainer());
    verify(containerService, times(1)).publishMultipleContainersToInventory(anyList());
  }

  @Test
  public void testPublishContainersToInventory_Disabled() {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
    rdcContainerUtils.publishContainersToInventory(getMockContainer());
    verify(containerService, times(0)).publishMultipleContainersToInventory(anyList());
  }

  @Test
  public void testPublishContainersToDcFin_Enabled() {
    doNothing().when(rdcDcFinUtils).postToDCFin(anyList(), anyString());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    rdcContainerUtils.postReceiptsToDcFin(getMockContainer(), "SSTK");
    verify(rdcDcFinUtils, times(1)).postToDCFin(anyList(), anyString());
  }

  @Test
  public void testPublishContainersToDcFin_Disabled() {
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
    rdcContainerUtils.postReceiptsToDcFin(getMockContainer(), "SSTK");
    verify(rdcDcFinUtils, times(0)).postToDCFin(anyList(), anyString());
  }

  @Test
  public void testPublishContainersToMM_Enabled() {
    LinkedTreeMap<String, Object> moveTreeMap = new LinkedTreeMap<>();
    moveTreeMap.put(ReceivingConstants.MOVE_TO_LOCATION, "C0823");
    doNothing()
        .when(movePublisher)
        .publishMove(anyInt(), anyString(), any(LinkedTreeMap.class), any(HttpHeaders.class));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    rdcContainerUtils.publishMove("223", 1, moveTreeMap, MockHttpHeaders.getHeaders());
    verify(movePublisher, times(1))
        .publishMove(anyInt(), anyString(), any(LinkedTreeMap.class), any(HttpHeaders.class));
  }

  @Test
  public void testPublishContainersToMM_Disabled() {
    LinkedTreeMap<String, Object> moveTreeMap = new LinkedTreeMap<>();
    moveTreeMap.put(ReceivingConstants.MOVE_TO_LOCATION, "C0823");
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
    rdcContainerUtils.publishMove("223", 1, moveTreeMap, MockHttpHeaders.getHeaders());
    verify(movePublisher, times(0))
        .publishMove(anyInt(), anyString(), any(LinkedTreeMap.class), any(HttpHeaders.class));
  }

  @Test
  public void testPublishPutawayMessage_SSTK() throws IOException {
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setLabelTrackingId("A443423232393");
    DeliveryDocument deliveryDocument = MockDeliveryDocuments.getDeliveryDocumentsForSSTK().get(0);
    when(rdcInstructionUtils.isSSTKDocument(deliveryDocument)).thenReturn(true);
    doNothing()
        .when(symboticPutawayPublishHelper)
        .publishPutawayAddMessage(
            any(ReceivedContainer.class),
            any(DeliveryDocument.class),
            any(Instruction.class),
            any(SymFreightType.class),
            any(HttpHeaders.class));
    rdcContainerUtils.publishPutawayMessageToHawkeye(
        deliveryDocument,
        receivedContainer,
        MockRdcInstruction.getInstruction(),
        MockHttpHeaders.getHeaders());
    verify(rdcInstructionUtils, times(1)).isSSTKDocument(any(DeliveryDocument.class));
    verify(symboticPutawayPublishHelper, times(1))
        .publishPutawayAddMessage(
            any(ReceivedContainer.class),
            any(DeliveryDocument.class),
            any(Instruction.class),
            any(SymFreightType.class),
            any(HttpHeaders.class));
  }

  @Test
  public void testPublishPutawayMessage_DA() throws IOException {
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setLabelTrackingId("A443423232393");
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    when(rdcInstructionUtils.isSSTKDocument(deliveryDocument)).thenReturn(false);
    when(rdcInstructionUtils.isDADocument(deliveryDocument)).thenReturn(true);
    doNothing()
        .when(symboticPutawayPublishHelper)
        .publishPutawayAddMessage(
            any(ReceivedContainer.class),
            any(DeliveryDocument.class),
            any(Instruction.class),
            any(SymFreightType.class),
            any(HttpHeaders.class));
    rdcContainerUtils.publishPutawayMessageToHawkeye(
        deliveryDocument,
        receivedContainer,
        MockRdcInstruction.getInstruction(),
        MockHttpHeaders.getHeaders());
    verify(rdcInstructionUtils, times(1)).isDADocument(any(DeliveryDocument.class));
    verify(symboticPutawayPublishHelper, times(1))
        .publishPutawayAddMessage(
            any(ReceivedContainer.class),
            any(DeliveryDocument.class),
            any(Instruction.class),
            any(SymFreightType.class),
            any(HttpHeaders.class));
  }

  @Test
  public void testBuildContainerForAtlasConvertedItemWithParentTrackingIdNull() throws IOException {
    Instruction mockInstruction = getMockInstruction();
    UpdateInstructionRequest mockUpdateInstructionRequest = getMockUpdateInstructionRequest();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentForReceiveInstructionFromInstructionEntity();
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    when(rdcDeliveryMetaDataService.findDeliveryMetaData(anyLong()))
        .thenReturn(getDeliveryMetaData());
    Container container =
        rdcContainerUtils.buildContainer(
            mockInstruction,
            mockUpdateInstructionRequest,
            deliveryDocument,
            userId,
            labelTrackingId,
            slotId,
            null);
    assertNotNull(container);
    assertNotNull(container.getMessageId());
    assertNotNull(container.getDestination().get("slot"));
    assertEquals(container.getContainerMiscInfo().get(ReceivingConstants.TRAILER_NUMBER), "WMT");
    assertNull(container.getParentTrackingId());
  }

  private Instruction getMockInstruction() throws IOException {
    Instruction instruction = new Instruction();
    instruction.setId(12345L);
    instruction.setLastChangeUserId("sysadmin");
    instruction.setCreateUserId("sysadmin");
    instruction.setDeliveryNumber(23371015L);
    instruction.setReceivedQuantity(20);
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentForReceiveInstructionFromInstructionEntity();
    instruction.setDeliveryDocument(gson.toJson(deliveryDocument));
    return instruction;
  }

  private UpdateInstructionRequest getMockUpdateInstructionRequest() throws IOException {
    UpdateInstructionRequest updateInstructionRequest = new UpdateInstructionRequest();
    updateInstructionRequest.setDoorNumber("6");
    updateInstructionRequest.setDeliveryNumber(87654321L);
    return updateInstructionRequest;
  }

  private Container getMockContainer() {
    Container container = new Container();
    container.setDeliveryNumber(123L);
    container.setInstructionId(12345L);
    container.setTrackingId("lpn123");
    container.setParentTrackingId(null);
    container.setCreateUser("sysadmin");
    container.setCompleteTs(new Date());
    container.setLastChangedUser("sysadmin");
    container.setLastChangedTs(new Date());
    container.setPublishTs(new Date());

    ContainerItem containerItem = new ContainerItem();
    containerItem.setTrackingId("lpn123");
    containerItem.setPurchaseReferenceNumber("PO1");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setItemNumber(12345678L);
    containerItem.setQuantity(80);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem.setVnpkQty(4);
    containerItem.setWhpkQty(4);
    containerItem.setActualTi(5);
    containerItem.setActualHi(4);
    Map<String, String> destination = new HashMap<>();
    destination.put(ReceivingConstants.SLOT, slotId);
    container.setDestination(destination);

    container.setContainerItems(Collections.singletonList(containerItem));
    return container;
  }

  private Container getMockContainerWithChildContainers() {
    Container container = new Container();
    container.setDeliveryNumber(123L);
    container.setInstructionId(12345L);
    container.setTrackingId("lpn123");
    container.setParentTrackingId(null);
    container.setCreateUser("sysadmin");
    container.setCompleteTs(new Date());
    container.setLastChangedUser("sysadmin");
    container.setLastChangedTs(new Date());
    container.setPublishTs(new Date());
    container.setInventoryStatus(InventoryStatus.ALLOCATED.name());
    Container childContainer = new Container();
    childContainer.setInventoryStatus(InventoryStatus.ALLOCATED.name());
    childContainer.setDeliveryNumber(123L);
    childContainer.setInstructionId(12345L);
    childContainer.setTrackingId("lpn123");
    childContainer.setParentTrackingId(null);
    childContainer.setCreateUser("sysadmin");
    childContainer.setCompleteTs(new Date());
    childContainer.setLastChangedUser("sysadmin");
    childContainer.setLastChangedTs(new Date());
    childContainer.setPublishTs(new Date());

    ContainerItem containerItem = new ContainerItem();
    containerItem.setTrackingId("lpn123");
    containerItem.setPurchaseReferenceNumber("PO1");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setItemNumber(12345678L);
    containerItem.setQuantity(80);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem.setVnpkQty(4);
    containerItem.setWhpkQty(4);
    containerItem.setActualTi(5);
    containerItem.setActualHi(4);
    Map<String, String> destination = new HashMap<>();
    destination.put(ReceivingConstants.SLOT, slotId);
    container.setDestination(destination);
    childContainer.setContainerItems(Collections.singletonList(containerItem));

    Container childContainer1 = new Container();
    childContainer1.setTrackingId("lpn124");
    childContainer1.setInventoryStatus(InventoryStatus.ALLOCATED.name());

    Set childContainerSet = new HashSet();
    childContainerSet.add(childContainer);
    childContainerSet.add(childContainer1);
    container.setChildContainers(childContainerSet);

    return container;
  }

  private DeliveryMetaData getDeliveryMetaData() {
    DeliveryMetaData deliveryMetaData =
        DeliveryMetaData.builder()
            .deliveryNumber("123")
            .trailerNumber("WMT")
            .carrierName("WMT")
            .carrierScacCode("WMT")
            .billCode("COLL")
            .build();

    return deliveryMetaData;
  }

  private DeliveryMetaData getDeliveryMetaDataWithEmptyTrailerNumber() {
    DeliveryMetaData deliveryMetaData = DeliveryMetaData.builder().deliveryNumber("123").build();
    return deliveryMetaData;
  }

  @Test
  public void testPublishContainerDCReceivingAndDCPicksToEI() throws Exception {
    Container container = getMockContainer();
    InventoryDetails inventoryDetails = mockInventoryDetails();

    when(inventoryTransformer.transformToInventory(container, ReceivingConstants.DC_RECEIVING))
        .thenReturn(inventoryDetails);
    doNothing()
        .when(eiService)
        .publishContainerToEI(container, inventoryDetails, ReceivingConstants.DC_RECEIVING);
    when(inventoryTransformer.transformToInventory(container, ReceivingConstants.DC_PICKS))
        .thenReturn(inventoryDetails);
    doNothing()
        .when(eiService)
        .publishContainerToEI(container, inventoryDetails, ReceivingConstants.DC_PICKS);

    rdcContainerUtils.publishContainerToEI(
        container, ReceivingConstants.DC_RECEIVING, ReceivingConstants.DC_PICKS);

    verify(inventoryTransformer, times(1))
        .transformToInventory(container, ReceivingConstants.DC_RECEIVING);
    verify(eiService, times(1))
        .publishContainerToEI(container, inventoryDetails, ReceivingConstants.DC_RECEIVING);
    verify(inventoryTransformer, times(1))
        .transformToInventory(container, ReceivingConstants.DC_PICKS);
    verify(eiService, times(1))
        .publishContainerToEI(container, inventoryDetails, ReceivingConstants.DC_PICKS);
  }

  @Test
  public void testPublishContainerDCReceivingEventForBreakPackInnerPicksToEI() throws Exception {
    Container container = getMockContainerWithChildContainers();
    InventoryDetails inventoryDetails = mockInventoryDetails();
    when(inventoryTransformer.transformToInventory(any(Container.class), anyString()))
        .thenReturn(inventoryDetails);
    doNothing()
        .when(eiService)
        .publishContainerToEI(any(Container.class), any(InventoryDetails.class), anyString());
    rdcContainerUtils.publishContainerToEI(container, ReceivingConstants.EI_DC_RECEIVING_EVENT);

    verify(inventoryTransformer, times(2)).transformToInventory(any(Container.class), anyString());
    verify(eiService, times(2))
        .publishContainerToEI(any(Container.class), any(InventoryDetails.class), anyString());
  }

  @Test
  public void testPublishContainerDCVoidToEI() throws Exception {
    Container container = getMockContainer();
    InventoryDetails inventoryDetails = mockInventoryDetails();

    when(inventoryTransformer.transformToInventory(container, ReceivingConstants.DC_VOID))
        .thenReturn(inventoryDetails);
    doNothing()
        .when(eiService)
        .publishContainerToEI(container, inventoryDetails, ReceivingConstants.DC_VOID);

    rdcContainerUtils.publishContainerToEI(container, ReceivingConstants.DC_VOID);

    verify(inventoryTransformer, times(1))
        .transformToInventory(container, ReceivingConstants.DC_VOID);
    verify(eiService, times(1))
        .publishContainerToEI(container, inventoryDetails, ReceivingConstants.DC_VOID);
  }

  @Test
  public void testPublishContainerDCReceivingToEIException() throws Exception {
    Container container = getMockContainer();
    InventoryDetails inventoryDetails = mockInventoryDetails();

    when(inventoryTransformer.transformToInventory(container, ReceivingConstants.DC_RECEIVING))
        .thenReturn(inventoryDetails);
    doThrow(new RuntimeException())
        .when(eiService)
        .publishContainerToEI(container, inventoryDetails, ReceivingConstants.DC_RECEIVING);

    rdcContainerUtils.publishContainerToEI(container, ReceivingConstants.DC_RECEIVING);

    verify(inventoryTransformer, times(1))
        .transformToInventory(container, ReceivingConstants.DC_RECEIVING);
    verify(eiService, times(1))
        .publishContainerToEI(container, inventoryDetails, ReceivingConstants.DC_RECEIVING);
    verify(inventoryTransformer, times(0))
        .transformToInventory(container, ReceivingConstants.DC_PICKS);
    verify(eiService, times(0))
        .publishContainerToEI(container, inventoryDetails, ReceivingConstants.DC_PICKS);
  }

  @Test
  public void testBuildContainerItemDetailsForOfflineRcv() throws Exception {
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();

    ContainerItem containerItem1 =
        rdcContainerUtils.buildContainerItemDetails(
            "3214251343114",
            mockDeliveryDocuments.get(0),
            12,
            null,
            "MANUAL",
            new ArrayList<>(),
            null);
    assertEquals(containerItem1.getTrackingId(), "3214251343114");
    // verify(rdcContainerUtils, times(1)).buildContainerItemDetails(anyString(),
    // any(DeliveryDocument.class), anyInt(), any(ContainerItem.class), anyString(), anyList());
  }

  @Test
  public void testBuildContainerForOfflineRcv() throws Exception {
    InstructionRequest instructionRequest = MockRdcInstruction.getInstructionRequest();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments
            .getDeliveryDocumentForReceiveInstructionFromInstructionAtlasConvertedItem();
    String userId = "testUser";
    String lpn = "a60320023232323";
    ReceivedContainer receivedContainer = mockReceivedContainers().get(0);
    List<Destination> destinations = new ArrayList<>();
    Destination destination = new Destination();
    destination.setSlot("R8000");
    destinations.add(destination);
    receivedContainer.setDestinations(destinations);

    when(rdcInstructionUtils.isDADocument(any(DeliveryDocument.class))).thenReturn(true);
    when(rdcReceivingUtils.isWhpkReceiving(
            any(DeliveryDocumentLine.class), any(ReceiveInstructionRequest.class)))
        .thenReturn(false);

    Container container =
        rdcContainerUtils.buildContainer(
            instructionRequest.getDoorNumber(),
            null,
            343434L,
            instructionRequest.getMessageId(),
            deliveryDocument,
            userId,
            receivedContainer,
            null,
            null);
    assertNotNull(container);
    assertEquals(ContainerType.CASE.getText(), container.getContainerType());
    assertEquals(container.getInventoryStatus(), InventoryStatus.ALLOCATED.name());
  }

  @Test
  public void testBuildContainerForWPMOfflineRcv() throws Exception {
    InstructionRequest instructionRequest = MockRdcInstruction.getInstructionRequest();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    deliveryDocument.setOriginFacilityNum(6014);
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    String userId = "testUser";
    String lpn = "a60320023232323";
    ReceivedContainer receivedContainer = mockReceivedContainers().get(0);
    List<Destination> destinations = new ArrayList<>();
    Destination destination = new Destination();
    destination.setSlot("R8000");
    destinations.add(destination);
    receivedContainer.setDestinations(destinations);
    receivedContainer.setLabelType("XDK2");

    when(rdcInstructionUtils.isDADocument(any(DeliveryDocument.class))).thenReturn(true);
    when(rdcReceivingUtils.isWhpkReceiving(
            any(DeliveryDocumentLine.class), any(ReceiveInstructionRequest.class)))
        .thenReturn(false);
    when(rdcManagedConfig.getWpmSites()).thenReturn(Arrays.asList("6014"));
    when(rdcManagedConfig.isDummyDeliveryEnabled()).thenReturn(true);

    Container container =
        rdcContainerUtils.buildContainer(
            instructionRequest.getDoorNumber(),
            null,
            343434L,
            instructionRequest.getMessageId(),
            deliveryDocument,
            userId,
            receivedContainer,
            null,
            null);
    assertNotNull(container);
    assertEquals(ContainerType.CASE.getText(), container.getContainerType());
    assertEquals(container.getInventoryStatus(), InventoryStatus.PICKED.name());
  }

  /**
   * Added TC for message number in inner-picks for WPM offline receiving
   *
   * @throws Exception
   */
  @Test
  public void testBuildContainerForWPMOfflineRcvMessageNbr() throws Exception {
    InstructionRequest instructionRequest = MockRdcInstruction.getInstructionRequest();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    deliveryDocument.setOriginFacilityNum(6014);
    deliveryDocument.setPalletId("palletId");
    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setChildTrackingId("343242132");
    deliveryDocumentLine.setMessageNumber("91");
    ItemData additionalInfo = new ItemData();
    additionalInfo.setAtlasConvertedItem(true);
    deliveryDocumentLine.setAdditionalInfo(additionalInfo);
    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);
    String userId = "testUser";
    String lpn = "a60320023232323";
    ReceivedContainer receivedContainer = mockReceivedContainers().get(0);
    List<Destination> destinations = new ArrayList<>();
    Destination destination = new Destination();
    destination.setSlot("R8000");
    destinations.add(destination);
    receivedContainer.setDestinations(destinations);
    receivedContainer.setLabelType("XDK2");

    when(rdcInstructionUtils.isDADocument(any(DeliveryDocument.class))).thenReturn(true);
    when(rdcReceivingUtils.isWhpkReceiving(
            any(DeliveryDocumentLine.class), any(ReceiveInstructionRequest.class)))
        .thenReturn(false);
    when(rdcManagedConfig.getWpmSites()).thenReturn(Arrays.asList("6014"));
    when(rdcManagedConfig.isDummyDeliveryEnabled()).thenReturn(true);

    Container container =
        rdcContainerUtils.buildContainer(
            instructionRequest.getDoorNumber(),
            null,
            343434L,
            instructionRequest.getMessageId(),
            deliveryDocument,
            userId,
            receivedContainer,
            null,
            null);
    assertNotNull(container);
    assertEquals(ContainerType.CASE.getText(), container.getContainerType());
    assertEquals(container.getInventoryStatus(), InventoryStatus.PICKED.name());
  }

  @Test
  public void testBuildContainerForWPMOfflineRcvWithPickedStatus() throws Exception {
    InstructionRequest instructionRequest = MockRdcInstruction.getInstructionRequest();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    deliveryDocument.setOriginFacilityNum(6014);
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    String userId = "testUser";
    String lpn = "a60320023232323";
    ReceivedContainer receivedContainer = mockReceivedContainers().get(0);
    List<Destination> destinations = new ArrayList<>();
    Destination destination = new Destination();
    destination.setSlot("R8000");
    destinations.add(destination);
    receivedContainer.setDestinations(destinations);
    receivedContainer.setLabelType("XDK2");

    when(rdcInstructionUtils.isDADocument(any(DeliveryDocument.class))).thenReturn(true);
    when(rdcReceivingUtils.isWhpkReceiving(
            any(DeliveryDocumentLine.class), any(ReceiveInstructionRequest.class)))
        .thenReturn(false);
    when(rdcManagedConfig.getWpmSites()).thenReturn(Arrays.asList("6014"));
    when(rdcManagedConfig.isDummyDeliveryEnabled()).thenReturn(true);

    Container container =
        rdcContainerUtils.buildContainer(
            instructionRequest.getDoorNumber(),
            null,
            343434L,
            instructionRequest.getMessageId(),
            deliveryDocument,
            userId,
            receivedContainer,
            null,
            null);
    assertNotNull(container);
    assertEquals(ContainerType.CASE.getText(), container.getContainerType());
  }

  @Test
  public void testBuildContainerForRdc2RdcOfflineRcvWithPickedStatus() throws Exception {
    InstructionRequest instructionRequest = MockRdcInstruction.getInstructionRequest();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    deliveryDocument.setOriginFacilityNum(6014);
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    String userId = "testUser";
    String lpn = "a60320023232323";
    ReceivedContainer receivedContainer = mockReceivedContainers().get(0);
    List<Destination> destinations = new ArrayList<>();
    Destination destination = new Destination();
    destination.setSlot("R8000");
    destinations.add(destination);
    receivedContainer.setDestinations(destinations);
    receivedContainer.setLabelType("XDK2");

    when(rdcInstructionUtils.isDADocument(any(DeliveryDocument.class))).thenReturn(true);
    when(rdcReceivingUtils.isWhpkReceiving(
            any(DeliveryDocumentLine.class), any(ReceiveInstructionRequest.class)))
        .thenReturn(false);
    when(rdcManagedConfig.getRdc2rdcSites()).thenReturn(Arrays.asList("6014"));
    when(rdcManagedConfig.isDummyDeliveryEnabled()).thenReturn(true);

    Container container =
        rdcContainerUtils.buildContainer(
            instructionRequest.getDoorNumber(),
            null,
            343434L,
            instructionRequest.getMessageId(),
            deliveryDocument,
            userId,
            receivedContainer,
            null,
            null);
    assertNotNull(container);
    assertEquals(ContainerType.CASE.getText(), container.getContainerType());
  }

  @Test
  public void testBuildContainerForRdc2RdcOfflineRcvWithPickedStatus_false() throws Exception {
    InstructionRequest instructionRequest = MockRdcInstruction.getInstructionRequest();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    deliveryDocument.setOriginFacilityNum(6014);
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    String userId = "testUser";
    String lpn = "a60320023232323";
    ReceivedContainer receivedContainer = mockReceivedContainers().get(0);
    List<Destination> destinations = new ArrayList<>();
    Destination destination = new Destination();
    destination.setSlot("R8000");
    destinations.add(destination);
    receivedContainer.setDestinations(destinations);
    receivedContainer.setLabelType("XDK2");

    when(rdcInstructionUtils.isDADocument(any(DeliveryDocument.class))).thenReturn(true);
    when(rdcReceivingUtils.isWhpkReceiving(
            any(DeliveryDocumentLine.class), any(ReceiveInstructionRequest.class)))
        .thenReturn(false);
    when(rdcManagedConfig.getRdc2rdcSites()).thenReturn(Arrays.asList("1234"));
    when(rdcManagedConfig.isDummyDeliveryEnabled()).thenReturn(true);

    Container container =
        rdcContainerUtils.buildContainer(
            instructionRequest.getDoorNumber(),
            null,
            343434L,
            instructionRequest.getMessageId(),
            deliveryDocument,
            userId,
            receivedContainer,
            null,
            null);
    assertNotNull(container);
    assertEquals(ContainerType.CASE.getText(), container.getContainerType());
  }

  private InventoryDetails mockInventoryDetails() throws Exception {
    InventoryDetails inventoryDetails = new InventoryDetails();
    inventoryDetails.setInventory(Arrays.asList(new Inventory()));
    Inventory inventory = inventoryDetails.getInventory().get(0);
    inventory.setEventInfo(prepareEventInfo());
    inventory.setChannelType(ReceivingConstants.DIST_CHANNEL_TYPE);
    return inventoryDetails;
  }

  /**
   * Preparation of EventInfo
   *
   * @return
   * @throws Exception
   */
  private EventInfo prepareEventInfo() {
    EventInfo eventInfo = new EventInfo();
    eventInfo.setProducerIdentifier(23);
    eventInfo.setCorelationId(
        Objects.isNull(TenantContext.getCorrelationId())
            ? UUID.randomUUID().toString()
            : TenantContext.getCorrelationId());
    eventInfo.setEventFromTimeZone(ReceivingConstants.UTC_TIME_ZONE);
    return eventInfo;
  }

  private static List<ContainerItem> mockContainerItem() {
    List<ContainerItem> containerItems = new ArrayList<>();
    ContainerItem containerItem = new ContainerItem();
    //    containerItem.set
    containerItems.add(containerItem);
    return containerItems;
  }

  private static List<ReceivedContainer> mockReceivedContainers() {
    List<ReceivedContainer> receivedContainers = new ArrayList<>();
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setLabelTrackingId("343242132");
    receivedContainer.setStoreAlignment("MANUAL");
    receivedContainer.setInventoryLabelType(InventoryLabelType.XDK1);
    receivedContainers.add(receivedContainer);
    return receivedContainers;
  }

  @Test
  public void testEighteenToSixteen() {
    String trackingId = "062365206172635721";
    String result = rdcContainerUtils.convertEighteenToSixteenDigitLabel(trackingId);
    assertEquals(result.length(), 16);
    assertEquals(result, "6236526172635721");
  }

  @Test
  public void testBuildContainerForXDKLabelType() throws IOException {
    AutoReceiveRequest autoReceiveRequest = new AutoReceiveRequest();
    autoReceiveRequest.setDeliveryNumber(2121212L);
    autoReceiveRequest.setDoorNumber("121");
    autoReceiveRequest.setLpn("a60202323232323");
    autoReceiveRequest.setPurchaseReferenceNumber("823232323");
    autoReceiveRequest.setPurchaseReferenceLineNumber(1);
    autoReceiveRequest.setMessageId("2hf9jf-3rwewewe-2rwe");
    String userId = "testUser";
    String lpn = "a60320023232323";
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setLabelTrackingId(lpn);
    List<Destination> destinations = new ArrayList<>();
    Destination destination = new Destination();
    destination.setSlot("R8000");
    destinations.add(destination);
    receivedContainer.setDestinations(destinations);
    receivedContainer.setLabelType(InventoryLabelType.XDK1.getType());
    receivedContainer.setFulfillmentMethod(FulfillmentMethodType.CASE_PACK_RECEIVING.getType());

    when(rdcInstructionUtils.isDADocument(any(DeliveryDocument.class))).thenReturn(true);
    when(rdcReceivingUtils.isWhpkReceiving(
            any(DeliveryDocumentLine.class), any(ReceiveInstructionRequest.class)))
        .thenReturn(false);

    Container container =
        rdcContainerUtils.buildContainer(
            autoReceiveRequest.getDoorNumber(),
            null,
            autoReceiveRequest.getDeliveryNumber(),
            autoReceiveRequest.getMessageId(),
            MockDeliveryDocuments.getDeliveryDocumentsForSSTK().get(0),
            userId,
            receivedContainer,
            null,
            null);
    assertNotNull(container);
    assertEquals(ContainerType.CASE.getText(), container.getContainerType());
    assertEquals(container.getDestination().get(ReceivingConstants.SLOT), "R8000");
  }

  @Test
  public void testBuildContainerForXDKLabelTypeWithParentContainers() throws IOException {
    InstructionRequest instructionRequest = MockRdcInstruction.getInstructionRequest();
    DeliveryDocument deliveryDocument =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA().get(0);
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .getAdditionalInfo()
        .setAtlasConvertedItem(true);
    String userId = "testUser";
    String lpn = "032323232323232";
    ReceivedContainer receivedContainer = new ReceivedContainer();
    receivedContainer.setLabelTrackingId(lpn);
    receivedContainer.setLabelType(InventoryLabelType.XDK2.getType());
    receivedContainer.setInventoryLabelType(InventoryLabelType.XDK2);
    receivedContainer.setRoutingLabel(false);
    receivedContainer.setParentTrackingId("a06020223232323232");
    List<Destination> destinations = new ArrayList<>();
    Destination destination = new Destination();
    destination.setSlot("P1001");
    destinations.add(destination);
    receivedContainer.setDestinations(destinations);
    when(rdcReceivingUtils.isWhpkReceiving(
            any(DeliveryDocumentLine.class), any(ReceiveInstructionRequest.class)))
        .thenReturn(false);
    when(rdcInstructionUtils.isDADocument(any(DeliveryDocument.class))).thenReturn(true);
    Container container =
        rdcContainerUtils.buildContainer(
            instructionRequest.getDoorNumber(),
            null,
            343434L,
            instructionRequest.getMessageId(),
            deliveryDocument,
            userId,
            receivedContainer,
            null,
            null);
    assertNotNull(container);
    assertEquals(ContainerType.VIRTUAL.getText(), container.getContainerType());
    assertEquals(container.getDestination().get(ReceivingConstants.SLOT), "P1001");
    assertEquals(container.getInventoryStatus(), InventoryStatus.ALLOCATED.name());
    assertTrue(MapUtils.isNotEmpty(container.getContainerMiscInfo()));
  }

  @Test
  public void test_isXdkLabelType_XDK1() throws IOException {
    assertTrue(rdcContainerUtils.isXdkLabelType("XDK1"));
  }

  @Test
  public void test_isXdkLabelType_XDK2() throws IOException {
    assertTrue(rdcContainerUtils.isXdkLabelType("XDK2"));
  }

  @Test
  public void test_isXdkLabelType_nonXDK() throws IOException {
    assertFalse(rdcContainerUtils.isXdkLabelType("DQRL"));
  }

  @Test
  public void test_isXdkLabelType_nullOrEmpty() throws IOException {
    assertFalse(rdcContainerUtils.isXdkLabelType(""));
  }

  @Test
  public void testGetConsolidatedContainerForPublish_ValidProDate() throws ReceivingException {
    Container daContainer = getDAContainer();
    daContainer.setShipmentId("test1234");
    Map<String, Object> containerMiscInfo = new HashMap<>();
    daContainer.setContainerMiscInfo(containerMiscInfo);
    daContainer.getContainerMiscInfo().put(ReceivingConstants.PRO_DATE, "Jun 12, 2024 3:13:52 PM");

    rdcContainerUtils.convertDateFormatForProDate(daContainer, daContainer.getTrackingId());
  }

  @Test
  public void testGetConsolidatedContainerForPublish_WithChild_ValidateProDate()
      throws ReceivingException {
    Container daContainer = getDAContainer();
    Set<Container> childContainers = daContainer.getChildContainers();

    for (Container childContainer : childContainers) {
      Map<String, Object> containerMiscInfo = new HashMap<>();
      childContainer.setContainerMiscInfo(containerMiscInfo);
      childContainer
          .getContainerMiscInfo()
          .put(ReceivingConstants.PRO_DATE, "Jun 1, 2024 3:13:52 PM");
    }
    rdcContainerUtils.convertDateFormatForProDate(daContainer, daContainer.getTrackingId());
  }

  @Test
  public void testGetConsolidatedContainerForPublish_WithChild_nullProDate()
      throws ReceivingException {
    Container daContainer = getDAContainer();
    Map<String, Object> parentContainerMiscInfo = new HashMap<>();
    daContainer.setContainerMiscInfo(parentContainerMiscInfo);
    daContainer.getContainerMiscInfo().put(ReceivingConstants.PRO_DATE, null);
    Set<Container> childContainers = daContainer.getChildContainers();

    for (Container childContainer : childContainers) {
      Map<String, Object> containerMiscInfo = new HashMap<>();
      childContainer.setContainerMiscInfo(containerMiscInfo);
      childContainer.getContainerMiscInfo().put(ReceivingConstants.PRO_DATE, null);
    }
    rdcContainerUtils.convertDateFormatForProDate(daContainer, daContainer.getTrackingId());
  }

  public static Container getDAContainer() {
    Container parentContainer = new Container();
    parentContainer.setDeliveryNumber(1234L);
    parentContainer.setParentTrackingId(null);
    parentContainer.setTrackingId("a329870000000000000000001");
    parentContainer.setContainerStatus("");
    parentContainer.setCompleteTs(new Date());
    parentContainer.setLastChangedUser("sysadmin");
    parentContainer.setDestination(getDestinationInfo());
    parentContainer.setFacility(getFacilityInfo());
    parentContainer.setFacilityCountryCode("US");
    parentContainer.setFacilityNum(32987);
    parentContainer.setLocation("14B");
    parentContainer.setCreateUser("sysadmin");

    Set<Container> childContainers = new HashSet<>();

    Container childContainer1 = new Container();
    childContainer1.setDeliveryNumber(1234L);
    childContainer1.setTrackingId("a329870000000000000000002");
    childContainer1.setContainerStatus("");
    childContainer1.setCompleteTs(new Date());
    childContainer1.setDestination(getDestinationInfo());
    childContainer1.setFacility(getFacilityInfo());
    childContainer1.setParentTrackingId("a329870000000000000000001");

    List<ContainerItem> containerItems = new ArrayList<>();
    ContainerItem containerItem1 = new ContainerItem();
    containerItem1.setTrackingId("a329870000000000000000002");
    containerItem1.setPurchaseReferenceNumber("34734743");
    containerItem1.setPurchaseReferenceLineNumber(1);
    containerItem1.setInboundChannelMethod("CROSSU");
    containerItem1.setVnpkQty(24);
    containerItem1.setWhpkQty(6);
    containerItem1.setItemNumber(100000L);
    containerItem1.setQuantity(24);
    containerItem1.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem1.setDistributions(getDistributionInfo());
    containerItem1.setGtin("7437838348");
    containerItem1.setDescription("Dummy desc item1");
    containerItems.add(containerItem1);
    childContainer1.setContainerItems(containerItems);

    Container childContainer2 = new Container();
    childContainer2.setDeliveryNumber(1234L);
    childContainer2.setTrackingId("a329870000000000000000003");
    childContainer2.setContainerStatus("");
    childContainer2.setCompleteTs(new Date());
    childContainer2.setDestination(getDestinationInfo());
    childContainer2.setFacility(getFacilityInfo());
    childContainer2.setParentTrackingId("a329870000000000000000001");

    containerItems.clear();
    ContainerItem containerItem2 = new ContainerItem();
    containerItem2.setTrackingId("a329870000000000000000003");
    containerItem2.setPurchaseReferenceNumber("34734743");
    containerItem2.setPurchaseReferenceLineNumber(1);
    containerItem2.setInboundChannelMethod("CROSSU");
    containerItem2.setVnpkQty(24);
    containerItem2.setWhpkQty(6);
    containerItem2.setItemNumber(100000L);
    containerItem2.setQuantity(24);
    containerItem2.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem2.setDistributions(getDistributionInfo());
    containerItem2.setGtin("7437838349");
    containerItem2.setDescription("Dummy desc item2");
    containerItems.add(containerItem2);
    childContainer2.setContainerItems(containerItems);

    childContainers.add(childContainer1);
    childContainers.add(childContainer2);

    parentContainer.setChildContainers(childContainers);
    return parentContainer;
  }

  public static Map<String, String> getDestinationInfo() {
    Map<String, String> facility = new HashMap<>();
    facility.put("countryCode", "US");
    facility.put("buNumber", "32987");
    return facility;
  }

  public static Map<String, String> getFacilityInfo() {
    Map<String, String> facility = new HashMap<>();
    facility.put("countryCode", "US");
    facility.put("buNumber", "32987");
    return facility;
  }

  public static List<Distribution> getDistributionInfo() {
    Map<String, String> item = new HashMap<>();
    item.put("financialReportingGroup", "US");
    item.put("baseDivisionCode", "WM");
    item.put("itemNbr", "500110");

    Distribution distribution1 = new Distribution();
    distribution1.setAllocQty(10);
    distribution1.setOrderId("0aa3080c-5e62-4337-a373-9e874aa7a2a3");
    distribution1.setItem(item);

    List<Distribution> distributions = new ArrayList<Distribution>();
    distributions.add(distribution1);

    return distributions;
  }
}
