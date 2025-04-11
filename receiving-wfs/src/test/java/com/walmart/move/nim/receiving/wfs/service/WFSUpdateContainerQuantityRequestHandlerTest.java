package com.walmart.move.nim.receiving.wfs.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertNotNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.common.ReceiptPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.CancelContainerResponse;
import com.walmart.move.nim.receiving.core.model.ContainerUpdateRequest;
import com.walmart.move.nim.receiving.core.model.ContainerUpdateResponse;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.ContainerAdjustmentHelper;
import com.walmart.move.nim.receiving.core.service.ContainerService;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.wfs.mock.data.MockInstruction;
import java.util.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class WFSUpdateContainerQuantityRequestHandlerTest {

  @Mock private ContainerService containerService;
  @Mock private ReceiptService receiptService;
  @Mock private WFSContainerService wfsContainerService;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private ReceiptPublisher receiptPublisher;
  @Mock private ContainerAdjustmentHelper containerAdjustmentHelper;
  @Mock private InstructionRepository instructionRepository;
  private final Gson gson = new GsonBuilder().create();

  @InjectMocks
  private WFSUpdateContainerQuantityRequestHandler wfsUpdateContainerQuantityRequestHandler;

  private final String trackingId = "d040930000200000000010023";
  private static final String facilityNum = "4093";
  private static final String countryCode = "US";
  private HttpHeaders httpheaders;

  @BeforeMethod
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    httpheaders = MockHttpHeaders.getHeaders(facilityNum, countryCode);
    ReflectionTestUtils.setField(wfsUpdateContainerQuantityRequestHandler, "gson", gson);
    when(configUtils.getDCTimeZone(anyInt())).thenReturn("US/Pacific");
  }

  @AfterMethod
  public void resetMocks() {
    reset(
        containerService,
        receiptService,
        wfsContainerService,
        configUtils,
        receiptPublisher,
        containerAdjustmentHelper,
        instructionRepository);
  }

  @Test
  public void testUpdateQuantityByTrackingId_isSuccessful() throws ReceivingException {
    when(containerService.getContainerByTrackingId(anyString())).thenReturn(getMockContainer());
    doNothing().when(containerService).isBackoutContainer(anyString(), anyString());
    when(containerAdjustmentHelper.validateDeliveryStatusForLabelAdjustment(
            anyString(), anyLong(), any(HttpHeaders.class)))
        .thenReturn(null);
    when(containerService.getContainerItem(anyString(), any(Container.class)))
        .thenReturn(getMockContainer().getContainerItems().get(0));
    when(wfsContainerService.adjustContainerItemQuantityAndGetDiff(
            anyString(),
            anyString(),
            anyInt(),
            any(ContainerUpdateResponse.class),
            any(Container.class),
            any(ContainerItem.class),
            anyInt(),
            anyString()))
        .thenReturn(1);
    doNothing()
        .when(containerService)
        .adjustQuantityByEachesInInventoryService(
            anyString(),
            anyString(),
            anyInt(),
            any(HttpHeaders.class),
            any(ContainerItem.class),
            anyInt());

    when(wfsContainerService.createDiffReceipt(
            any(Container.class), any(ContainerItem.class), anyInt(), anyString()))
        .thenReturn(getMockReceipt());
    when(receiptService.saveReceipt(any(Receipt.class))).thenReturn(getMockReceipt());
    when(instructionRepository.findById(anyLong()))
        .thenReturn(Optional.of(MockInstruction.getDockTagInstruction()));

    doNothing()
        .when(receiptPublisher)
        .publishReceiptUpdate(anyString(), any(HttpHeaders.class), anyBoolean());

    ContainerUpdateResponse containerUpdateResponse =
        wfsUpdateContainerQuantityRequestHandler.updateQuantityByTrackingId(
            trackingId,
            getContainerUpdateRequest(),
            MockHttpHeaders.getHeaders(facilityNum, countryCode));

    assertNotNull(containerUpdateResponse);
    assertNotNull(containerUpdateResponse.getPrintJob());

    verify(containerService, times(1)).getContainerByTrackingId(anyString());
    verify(containerService, times(1)).isBackoutContainer(anyString(), anyString());
    verify(containerAdjustmentHelper, times(1))
        .validateDeliveryStatusForLabelAdjustment(anyString(), anyLong(), any(HttpHeaders.class));
    verify(containerService, times(1)).getContainerItem(anyString(), any(Container.class));
    verify(wfsContainerService, times(1))
        .adjustContainerItemQuantityAndGetDiff(
            anyString(), anyString(), anyInt(), any(), any(), any(), anyInt(), anyString());
    verify(containerService, times(1))
        .adjustQuantityByEachesInInventoryService(
            anyString(),
            anyString(),
            anyInt(),
            any(HttpHeaders.class),
            any(ContainerItem.class),
            anyInt());
    verify(wfsContainerService, times(1))
        .createDiffReceipt(any(Container.class), any(ContainerItem.class), anyInt(), anyString());
    verify(receiptService, times(1)).saveReceipt(any(Receipt.class));
    verify(receiptPublisher, times(1))
        .publishReceiptUpdate(anyString(), any(HttpHeaders.class), anyBoolean());
    verify(instructionRepository, times(1)).findById(anyLong());
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testUpdateContainerQuantityThrowsException_whenContainerIsAlreadyBackedOut()
      throws ReceivingException {
    Container container = getMockContainer();
    container.setContainerStatus(ReceivingConstants.STATUS_BACKOUT);
    when(containerService.getContainerByTrackingId(anyString())).thenReturn(container);
    doThrow(
            new ReceivingException(
                ReceivingException.ADJUST_PALLET_QUANTITY_ERROR_MSG,
                HttpStatus.BAD_REQUEST,
                ReceivingException.ADJUST_PALLET_QUANTITY_ERROR_CODE,
                ReceivingException.ADJUST_PALLET_QUANTITY_ERROR_HEADER))
        .when(containerService)
        .isBackoutContainer(anyString(), anyString());
    wfsUpdateContainerQuantityRequestHandler.updateQuantityByTrackingId(
        trackingId, getContainerUpdateRequest(), httpheaders);
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testUpdateContainerQuantityThrowsException_whenContainerDoesNotExists()
      throws ReceivingException {
    doThrow(
            new ReceivingException(
                ReceivingException.ADJUST_PALLET_QUANTITY_ERROR_MSG,
                HttpStatus.BAD_REQUEST,
                ReceivingException.ADJUST_PALLET_QUANTITY_ERROR_CODE,
                ReceivingException.ADJUST_PALLET_QUANTITY_ERROR_HEADER))
        .when(containerService)
        .getContainerByTrackingId(anyString());
    wfsUpdateContainerQuantityRequestHandler.updateQuantityByTrackingId(
        trackingId, getContainerUpdateRequest(), httpheaders);
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testUpdateContainerQuantityThrowsException_whenContainerItemDoesNotExists()
      throws ReceivingException {
    doThrow(
            new ReceivingException(
                ReceivingException.ADJUST_PALLET_QUANTITY_ERROR_MSG,
                HttpStatus.BAD_REQUEST,
                ReceivingException.ADJUST_PALLET_QUANTITY_ERROR_CODE,
                ReceivingException.ADJUST_PALLET_QUANTITY_ERROR_HEADER))
        .when(containerService)
        .getContainerItem(anyString(), any(Container.class));
    wfsUpdateContainerQuantityRequestHandler.updateQuantityByTrackingId(
        trackingId, getContainerUpdateRequest(), httpheaders);
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void
      testUpdateContainerQuantityThrowsException_whenNotAdjustContainerItemQuantityAndGetDiff()
          throws ReceivingException {
    doThrow(
            new ReceivingException(
                ReceivingException.ADJUST_PALLET_QUANTITY_ERROR_MSG,
                HttpStatus.BAD_REQUEST,
                ReceivingException.ADJUST_PALLET_QUANTITY_ERROR_CODE,
                ReceivingException.ADJUST_PALLET_QUANTITY_ERROR_HEADER))
        .when(wfsContainerService)
        .adjustContainerItemQuantityAndGetDiff(
            anyString(),
            anyString(),
            anyInt(),
            any(ContainerUpdateResponse.class),
            any(Container.class),
            any(ContainerItem.class),
            anyInt(),
            anyString());
    wfsUpdateContainerQuantityRequestHandler.updateQuantityByTrackingId(
        trackingId, getContainerUpdateRequest(), httpheaders);
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testUpdateContainerQuantityThrowsException_whenNotcheckAndRepublishOsdrIfNecessary()
      throws ReceivingException {
    doThrow(
            new ReceivingException(
                ReceivingException.ADJUST_PALLET_QUANTITY_ERROR_MSG,
                HttpStatus.BAD_REQUEST,
                ReceivingException.ADJUST_PALLET_QUANTITY_ERROR_CODE,
                ReceivingException.ADJUST_PALLET_QUANTITY_ERROR_HEADER))
        .when(wfsContainerService)
        .checkAndRepublishOsdrIfNecessary(anyLong(), any(HttpHeaders.class));
    wfsUpdateContainerQuantityRequestHandler.updateQuantityByTrackingId(
        trackingId, getContainerUpdateRequest(), httpheaders);
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testUpdateContainerQuantity_ThrowsException_CannotCorrectAsDeliveryFinalized()
      throws ReceivingException {
    when(containerService.getContainerByTrackingId(anyString())).thenReturn(getMockContainer());
    doNothing().when(containerService).isBackoutContainer(anyString(), anyString());
    when(containerAdjustmentHelper.validateDeliveryStatusForLabelAdjustment(
            anyString(), anyLong(), any(HttpHeaders.class)))
        .thenReturn(
            new CancelContainerResponse(
                trackingId,
                ExceptionCodes.LABEL_CORRECTION_ERROR_FOR_FINALIZED_DELIVERY,
                ReceivingException.LABEL_QUANTITY_ADJUSTMENT_ERROR_MSG_FOR_FINALIZED_DELIVERY));
    wfsUpdateContainerQuantityRequestHandler.updateQuantityByTrackingId(
        trackingId, getContainerUpdateRequest(), httpheaders);
  }

  private Receipt getMockReceipt() {
    return new Receipt();
  }

  // mocking containers
  private Container getMockContainer() {
    Container container = new Container();
    container.setTrackingId("B67387000020002031");
    container.setMessageId("a0e24c65-991b-46ac-ae65-dc7bc52edfe6");
    container.setLocation("100");
    container.setDeliveryNumber(Long.parseLong("18278904"));
    container.setParentTrackingId(null);
    container.setContainerType("Chep Pallet");
    container.setCtrShippable(Boolean.FALSE);
    container.setCtrReusable(Boolean.FALSE);
    container.setInventoryStatus("AVAILABLE");
    container.setIsConveyable(Boolean.TRUE);
    container.setContainerStatus(ReceivingConstants.STATUS_PUTAWAY_COMPLETE);
    container.setCompleteTs(new Date());
    container.setInstructionId(101L);

    Map<String, String> facility = new HashMap<>();
    facility.put("countryCode", "US");
    facility.put("buNumber", "32612");
    container.setFacility(facility);

    List<ContainerItem> containerItems = new ArrayList<>();
    ContainerItem containerItem = new ContainerItem();
    containerItem.setTrackingId("B67387000020002031");
    containerItem.setPurchaseReferenceNumber("199557349");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setInboundChannelMethod("SSTKU");
    containerItem.setTotalPurchaseReferenceQty(100);
    containerItem.setPurchaseCompanyId(1);
    containerItem.setDeptNumber(1);
    containerItem.setPoDeptNumber("92");
    containerItem.setItemNumber(Long.parseLong("556565795"));
    containerItem.setGtin("00049807100011");
    containerItem.setQuantity(80);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem.setVnpkQty(4);
    containerItem.setWhpkQty(4);
    containerItem.setVendorPackCost(25.0);
    containerItem.setWhpkSell(25.0);
    containerItem.setBaseDivisionCode("WM");
    containerItem.setFinancialReportingGroupCode("US");
    containerItem.setVendorNumber(1234);
    containerItem.setPackagedAsUom(ReceivingConstants.Uom.VNPK);
    containerItem.setPromoBuyInd("Y");
    containerItem.setActualTi(6);
    containerItem.setActualHi(2);
    containerItem.setVnpkWgtQty(14.84F);
    containerItem.setVnpkWgtUom("LB");
    containerItem.setVnpkcbqty(0.432F);
    containerItem.setVnpkcbuomcd("CF");
    containerItem.setDescription("70QT XTREME BLUE");
    containerItem.setSecondaryDescription("WH TO ASM");
    containerItem.setRotateDate(new Date());
    containerItem.setPoTypeCode(20);
    containerItem.setVendorNbrDeptSeq(1234);
    containerItem.setLotNumber("LOT555");
    containerItems.add(containerItem);
    container.setContainerItems(containerItems);

    container.setPublishTs(new Date());

    return container;
  }

  private ContainerUpdateRequest getContainerUpdateRequest() {
    ContainerUpdateRequest containerUpdateRequest = new ContainerUpdateRequest();
    containerUpdateRequest.setAdjustQuantity(2);
    containerUpdateRequest.setInventoryQuantity(3);
    containerUpdateRequest.setAdjustQuantityUOM(ReceivingConstants.Uom.EACHES);
    return containerUpdateRequest;
  }
}
