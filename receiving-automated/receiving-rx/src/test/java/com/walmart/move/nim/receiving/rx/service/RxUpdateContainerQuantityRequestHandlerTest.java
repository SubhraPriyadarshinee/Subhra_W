package com.walmart.move.nim.receiving.rx.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_DC_ONE_ATLAS_ENABLED;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.inventory.InventoryRestApiClient;
import com.walmart.move.nim.receiving.core.client.slotting.SlottingRestApiClient;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.printlabel.LabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.ContainerAdjustmentHelper;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.data.MockRxHttpHeaders;
import com.walmart.move.nim.receiving.rx.mock.MockInstruction;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.apache.commons.collections4.MapUtils;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RxUpdateContainerQuantityRequestHandlerTest {

  @Mock private ContainerService containerService;

  @Mock private NimRdsServiceImpl nimRdsServiceImpl;

  @Mock private ContainerPersisterService containerPersisterService;

  @Mock private ReceiptService receiptService;

  @Mock private ContainerAdjustmentHelper containerAdjustmentHelper;

  @Mock private InstructionRepository instructionRepository;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Mock private InventoryRestApiClient inventoryRestApiClient;

  @Mock private SlottingRestApiClient slottingRestApiClient;
  private Gson gson = new Gson();

  @InjectMocks
  private RxUpdateContainerQuantityRequestHandler rxUpdateContainerQuantityRequestHandler;

  @BeforeClass
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32897);
    RxReceivingCorrectionPrintJobBuilder rxReceivingCorrectionPrintJobBuilder =
        new RxReceivingCorrectionPrintJobBuilder();
    ReflectionTestUtils.setField(rxReceivingCorrectionPrintJobBuilder, "gson", gson);

    ReflectionTestUtils.setField(
        rxUpdateContainerQuantityRequestHandler,
        "rxReceivingCorrectionPrintJobBuilder",
        rxReceivingCorrectionPrintJobBuilder);
    ReflectionTestUtils.setField(rxUpdateContainerQuantityRequestHandler, "gson", gson);
  }

  @AfterMethod
  public void tearDown() {
    reset(containerService);
    reset(nimRdsServiceImpl);
    reset(containerPersisterService);
    reset(receiptService);
    reset(instructionRepository);
    reset(containerAdjustmentHelper);
    reset(inventoryRestApiClient);
    reset(slottingRestApiClient);
  }

  // when we get parent quantity as 0 do not allow
  @Test
  public void test_updateQuantityByTrackingId_parent_trackingId_qty_0() {

    try {
      ContainerUpdateRequest containerUpdateRequest = new ContainerUpdateRequest();
      containerUpdateRequest.setAdjustQuantity(0);
      containerUpdateRequest.setPrinterId(12345);

      rxUpdateContainerQuantityRequestHandler.updateQuantityByTrackingId(
          "PARENT_TRACKING_ID", containerUpdateRequest, MockRxHttpHeaders.getHeaders());

    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.INVALID_PALLET_CORRECTION_QTY);
      assertEquals(e.getDescription(), ReceivingException.INVALID_PALLET_CORRECTION_QTY);
    }
  }

  // This is pallet correction senario
  @Test
  public void test_updateQuantityByTrackingId_palletCorrection() throws Exception {

    Container parentContainer = createContainer(null, "PARENT_CONTAINER_TRACKING_ID", 10, "40");
    doReturn(parentContainer).when(containerService).getContainerByTrackingId(anyString());
    doNothing().when(containerService).isBackoutContainer(anyString(), isNull());
    doReturn(new Receipt())
        .when(containerAdjustmentHelper)
        .adjustQuantityInReceipt(anyInt(), anyString(), any(Container.class), anyString());
    Container mockAdjustedContainer = new Container();
    mockAdjustedContainer.setInstructionId(1234l);
    doReturn(mockAdjustedContainer)
        .when(containerAdjustmentHelper)
        .adjustPalletQuantity(anyInt(), any(Container.class), anyString());
    doNothing()
        .when(nimRdsServiceImpl)
        .quantityChange(anyInt(), anyString(), any(HttpHeaders.class));

    //    doReturn(new ResponseEntity<>(HttpStatus.OK))
    //        .when(inventoryRestApiClient)
    //        .notifyReceivingCorrectionAdjustment(any(), any(HttpHeaders.class));

    Instruction completeInstruction = MockInstruction.getRxCompleteInstruction();
    completeInstruction.setCompleteTs(new Date());
    completeInstruction.setCompleteUserId("MOCK_JUNIT_USER");

    DeliveryDocument mockDeliveryDocument4mDB = new DeliveryDocument();
    DeliveryDocumentLine mockDeliveryDocumentLine = new DeliveryDocumentLine();
    ItemData mockItemData = new ItemData();
    mockItemData.setIsDscsaExemptionInd(true);
    mockDeliveryDocumentLine.setAdditionalInfo(mockItemData);
    mockDeliveryDocument4mDB.setDeliveryDocumentLines(Arrays.asList(mockDeliveryDocumentLine));
    completeInstruction.setDeliveryDocument(gson.toJson(mockDeliveryDocument4mDB));

    doReturn(Optional.of(completeInstruction)).when(instructionRepository).findById(anyLong());

    Set<Container> childContainersSet = new HashSet<>();
    List<Container> childContainersList = new ArrayList<>();
    for (int i = 1; i <= 10; i++) {
      Container childContainer =
          createContainer(
              "PARENT_CONTAINER_TRACKING_ID", "CHILD_CONTAINER_TRACKING_ID_" + i, 1, "40");
      childContainersSet.add(childContainer);
      childContainersList.add(childContainer);
    }
    parentContainer.setChildContainers(childContainersSet);

    doReturn(childContainersList)
        .when(containerService)
        .getContainerByParentTrackingIdAndContainerStatus(anyString(), anyString());

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(Boolean.FALSE);

    ContainerUpdateRequest containerUpdateRequest = new ContainerUpdateRequest();
    containerUpdateRequest.setAdjustQuantity(5);
    containerUpdateRequest.setPrinterId(12345);

    ContainerUpdateResponse updateQuantityByTrackingIdResponse =
        rxUpdateContainerQuantityRequestHandler.updateQuantityByTrackingId(
            "PARENT_TRACKING_ID", containerUpdateRequest, MockRxHttpHeaders.getHeaders());

    assertNotNull(updateQuantityByTrackingIdResponse);
    assertTrue(MapUtils.isNotEmpty(updateQuantityByTrackingIdResponse.getPrintJob()));
    Optional<LabelData> optionalLabelData =
        ((PrintLabelRequest)
                ((List) updateQuantityByTrackingIdResponse.getPrintJob().get("printRequests"))
                    .get(0))
            .getData()
            .stream()
            .filter(labelData -> labelData.getKey().equals("QTY"))
            .findFirst();
    assertTrue(optionalLabelData.isPresent());
    assertEquals("5", optionalLabelData.get().getValue());

    verify(containerService, times(1)).getContainerByTrackingId(anyString());
    verify(containerService, times(1)).isBackoutContainer(anyString(), isNull());

    //    verify(nimRdsServiceImpl, times(1))
    //        .quantityChange(anyInt(), anyString(), any(HttpHeaders.class));
    verify(instructionRepository, times(1)).findById(anyLong());
    verify(containerAdjustmentHelper, times(1))
        .adjustQuantityInReceipt(anyInt(), anyString(), any(Container.class), anyString());
    verify(containerAdjustmentHelper, times(1))
        .adjustPalletQuantity(anyInt(), any(Container.class), anyString());
    verify(containerAdjustmentHelper, times(1))
        .persistAdjustedReceiptsAndContainer(any(Receipt.class), any(Container.class));
  }

  @Test
  public void test_updateQuantityByTrackingId_palletCorrectionInv() throws Exception {

    Container parentContainer = createContainer(null, "PARENT_CONTAINER_TRACKING_ID", 10, "40");
    doReturn(parentContainer).when(containerService).getContainerByTrackingId(anyString());
    doNothing().when(containerService).isBackoutContainer(anyString(), isNull());
    doReturn(new Receipt())
        .when(containerAdjustmentHelper)
        .adjustQuantityInReceipt(anyInt(), anyString(), any(Container.class), anyString());
    Container mockAdjustedContainer = new Container();
    mockAdjustedContainer.setInstructionId(1234l);
    doReturn(mockAdjustedContainer)
        .when(containerAdjustmentHelper)
        .adjustPalletQuantity(anyInt(), any(Container.class), anyString());
    doNothing()
        .when(nimRdsServiceImpl)
        .quantityChange(anyInt(), anyString(), any(HttpHeaders.class));

    Instruction completeInstruction = MockInstruction.getRxCompleteInstruction();
    completeInstruction.setCompleteTs(new Date());
    completeInstruction.setCompleteUserId("MOCK_JUNIT_USER");

    DeliveryDocument mockDeliveryDocument4mDB = new DeliveryDocument();
    DeliveryDocumentLine mockDeliveryDocumentLine = new DeliveryDocumentLine();
    ItemData mockItemData = new ItemData();
    mockItemData.setIsDscsaExemptionInd(true);
    mockDeliveryDocumentLine.setAdditionalInfo(mockItemData);
    mockDeliveryDocument4mDB.setDeliveryDocumentLines(Arrays.asList(mockDeliveryDocumentLine));
    completeInstruction.setDeliveryDocument(gson.toJson(mockDeliveryDocument4mDB));

    doReturn(Optional.of(completeInstruction)).when(instructionRepository).findById(anyLong());

    Set<Container> childContainersSet = new HashSet<>();
    List<Container> childContainersList = new ArrayList<>();
    for (int i = 1; i <= 10; i++) {
      Container childContainer =
          createContainer(
              "PARENT_CONTAINER_TRACKING_ID", "CHILD_CONTAINER_TRACKING_ID_" + i, 1, "40");
      childContainersSet.add(childContainer);
      childContainersList.add(childContainer);
    }
    parentContainer.setChildContainers(childContainersSet);

    doReturn(childContainersList)
        .when(containerService)
        .getContainerByParentTrackingIdAndContainerStatus(anyString(), anyString());

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(Boolean.TRUE);
    ContainerUpdateRequest containerUpdateRequest = new ContainerUpdateRequest();
    containerUpdateRequest.setAdjustQuantity(5);
    containerUpdateRequest.setPrinterId(12345);

    ContainerUpdateResponse updateQuantityByTrackingIdResponse =
        rxUpdateContainerQuantityRequestHandler.updateQuantityByTrackingId(
            "PARENT_TRACKING_ID", containerUpdateRequest, MockRxHttpHeaders.getHeaders());

    assertNotNull(updateQuantityByTrackingIdResponse);
    assertTrue(MapUtils.isNotEmpty(updateQuantityByTrackingIdResponse.getPrintJob()));
    Optional<LabelData> optionalLabelData =
        ((PrintLabelRequest)
                ((List) updateQuantityByTrackingIdResponse.getPrintJob().get("printRequests"))
                    .get(0))
            .getData()
            .stream()
            .filter(labelData -> labelData.getKey().equals("QTY"))
            .findFirst();
    assertTrue(optionalLabelData.isPresent());
    assertEquals("5", optionalLabelData.get().getValue());

    verify(containerService, times(1)).getContainerByTrackingId(anyString());
    verify(containerService, times(1)).isBackoutContainer(anyString(), isNull());
    verify(inventoryRestApiClient, times(1))
        .adjustQuantity(
            anyString(),
            anyString(),
            anyInt(),
            any(HttpHeaders.class),
            any(),
            anyInt(),
            eq(ReceivingConstants.INVENTORY_RECEIVING_CORRECTION_REASON_CODE_28));

    //    verify(nimRdsServiceImpl, times(1))
    //        .quantityChange(anyInt(), anyString(), any(HttpHeaders.class));
    verify(instructionRepository, times(1)).findById(anyLong());
    verify(containerAdjustmentHelper, times(1))
        .adjustQuantityInReceipt(anyInt(), anyString(), any(Container.class), anyString());
    verify(containerAdjustmentHelper, times(1))
        .adjustPalletQuantity(anyInt(), any(Container.class), anyString());
    verify(containerAdjustmentHelper, times(1))
        .persistAdjustedReceiptsAndContainer(any(Receipt.class), any(Container.class));
  }

  @Test
  public void test_updateQuantityByTrackingId_palletCorrectionOneAtlas() throws Exception {

    Container parentContainer = createContainer(null, "PARENT_CONTAINER_TRACKING_ID", 10, "40");
    doReturn(parentContainer).when(containerService).getContainerByTrackingId(anyString());
    doNothing().when(containerService).isBackoutContainer(anyString(), isNull());
    doReturn(new Receipt())
        .when(containerAdjustmentHelper)
        .adjustQuantityInReceipt(anyInt(), anyString(), any(Container.class), anyString());
    Container mockAdjustedContainer = new Container();
    mockAdjustedContainer.setInstructionId(1234l);
    doReturn(mockAdjustedContainer)
        .when(containerAdjustmentHelper)
        .adjustPalletQuantity(anyInt(), any(Container.class), anyString());
    doNothing()
        .when(nimRdsServiceImpl)
        .quantityChange(anyInt(), anyString(), any(HttpHeaders.class));

    Instruction completeInstruction = MockInstruction.getRxCompleteInstruction();
    completeInstruction.setCompleteTs(new Date());
    completeInstruction.setCompleteUserId("MOCK_JUNIT_USER");

    DeliveryDocument mockDeliveryDocument4mDB = new DeliveryDocument();
    DeliveryDocumentLine mockDeliveryDocumentLine = new DeliveryDocumentLine();
    ItemData mockItemData = new ItemData();
    mockItemData.setIsDscsaExemptionInd(true);
    mockDeliveryDocumentLine.setAdditionalInfo(mockItemData);
    mockDeliveryDocument4mDB.setDeliveryDocumentLines(Arrays.asList(mockDeliveryDocumentLine));
    completeInstruction.setDeliveryDocument(gson.toJson(mockDeliveryDocument4mDB));

    doReturn(Optional.of(completeInstruction)).when(instructionRepository).findById(anyLong());

    Set<Container> childContainersSet = new HashSet<>();
    List<Container> childContainersList = new ArrayList<>();
    for (int i = 1; i <= 10; i++) {
      Container childContainer =
          createContainer(
              "PARENT_CONTAINER_TRACKING_ID", "CHILD_CONTAINER_TRACKING_ID_" + i, 1, "40");
      childContainersSet.add(childContainer);
      childContainersList.add(childContainer);
    }
    parentContainer.setChildContainers(childContainersSet);

    doReturn(childContainersList)
        .when(containerService)
        .getContainerByParentTrackingIdAndContainerStatus(anyString(), anyString());

    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(IS_DC_ONE_ATLAS_ENABLED), anyBoolean()))
        .thenReturn(Boolean.FALSE);
    ContainerUpdateRequest containerUpdateRequest = new ContainerUpdateRequest();
    containerUpdateRequest.setAdjustQuantity(5);
    containerUpdateRequest.setPrinterId(12345);

    ContainerUpdateResponse updateQuantityByTrackingIdResponse =
        rxUpdateContainerQuantityRequestHandler.updateQuantityByTrackingId(
            "PARENT_TRACKING_ID", containerUpdateRequest, MockRxHttpHeaders.getHeaders());

    assertNotNull(updateQuantityByTrackingIdResponse);
    assertTrue(MapUtils.isNotEmpty(updateQuantityByTrackingIdResponse.getPrintJob()));
    Optional<LabelData> optionalLabelData =
        ((PrintLabelRequest)
                ((List) updateQuantityByTrackingIdResponse.getPrintJob().get("printRequests"))
                    .get(0))
            .getData()
            .stream()
            .filter(labelData -> labelData.getKey().equals("QTY"))
            .findFirst();
    assertTrue(optionalLabelData.isPresent());
    assertEquals("5", optionalLabelData.get().getValue());

    verify(containerService, times(1)).getContainerByTrackingId(anyString());
    verify(containerService, times(1)).isBackoutContainer(anyString(), isNull());
    verify(inventoryRestApiClient, times(1))
        .adjustQuantity(
            anyString(),
            anyString(),
            anyInt(),
            any(HttpHeaders.class),
            any(),
            anyInt(),
            eq(ReceivingConstants.INVENTORY_RECEIVING_CORRECTION_REASON_CODE_28));

    verify(nimRdsServiceImpl, times(1))
        .quantityChange(anyInt(), anyString(), any(HttpHeaders.class));
    verify(instructionRepository, times(1)).findById(anyLong());
    verify(containerAdjustmentHelper, times(1))
        .adjustQuantityInReceipt(anyInt(), anyString(), any(Container.class), anyString());
    verify(containerAdjustmentHelper, times(1))
        .adjustPalletQuantity(anyInt(), any(Container.class), anyString());
    verify(containerAdjustmentHelper, times(1))
        .persistAdjustedReceiptsAndContainer(any(Receipt.class), any(Container.class));
  }

  @Test
  public void test_pallet_correction_d38_not_allowed() throws ReceivingException {

    try {

      Container parentContainer = createContainer(null, "PARENT_CONTAINER_TRACKING_ID", 10, "38");
      doReturn(parentContainer).when(containerService).getContainerByTrackingId(anyString());

      Instruction completeInstruction = MockInstruction.getInstructionWithManufactureDetails();
      completeInstruction.setCompleteTs(new Date());
      completeInstruction.setCompleteUserId("MOCK_JUNIT_USER");

      DeliveryDocument mockDeliveryDocument4mDB =
          gson.fromJson(completeInstruction.getDeliveryDocument(), DeliveryDocument.class);
      DeliveryDocumentLine mockDeliveryDocumentLine =
          mockDeliveryDocument4mDB.getDeliveryDocumentLines().get(0);
      ItemData mockItemData = new ItemData();
      mockItemData.setIsDscsaExemptionInd(false);

      mockDeliveryDocumentLine.setAdditionalInfo(mockItemData);

      mockDeliveryDocument4mDB.setDeliveryDocumentLines(Arrays.asList(mockDeliveryDocumentLine));
      completeInstruction.setDeliveryDocument(gson.toJson(mockDeliveryDocument4mDB));

      doReturn(Optional.of(completeInstruction)).when(instructionRepository).findById(anyLong());

      ContainerUpdateRequest containerUpdateRequest = new ContainerUpdateRequest();
      containerUpdateRequest.setAdjustQuantity(10);
      containerUpdateRequest.setPrinterId(12345);

      rxUpdateContainerQuantityRequestHandler.updateQuantityByTrackingId(
          "PARENT_TRACKING_ID", containerUpdateRequest, MockRxHttpHeaders.getHeaders());

    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.D38_PALLET_CORRECTION_NOT_ALLWED);
      assertEquals(e.getDescription(), ReceivingException.D38_PALLET_CORRECTION_NOT_ALLOWED);
    }
  }

  private Container createContainer(
      String parentTrackignId, String trackingId, int quantity, String deptNumber) {
    Container container = new Container();
    container.setDeliveryNumber(12345l);
    container.setInstructionId(987l);
    container.setTrackingId(trackingId);
    container.setParentTrackingId(parentTrackignId);

    ContainerItem containerItem = new ContainerItem();
    containerItem.setPurchaseReferenceNumber("987654321");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setQuantity(quantity);
    containerItem.setVnpkQty(6);
    containerItem.setWhpkQty(6);
    containerItem.setPoDeptNumber(deptNumber);
    containerItem.setBaseDivisionCode("WM");

    container.setContainerItems(Arrays.asList(containerItem));

    return container;
  }
}
