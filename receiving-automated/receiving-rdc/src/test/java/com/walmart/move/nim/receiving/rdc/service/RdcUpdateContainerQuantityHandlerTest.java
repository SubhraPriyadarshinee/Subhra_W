package com.walmart.move.nim.receiving.rdc.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.testng.Assert.assertNotNull;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.inventory.InventoryRestApiClient;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.SymboticPutawayPublishHelper;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.CancelContainerResponse;
import com.walmart.move.nim.receiving.core.model.ContainerUpdateRequest;
import com.walmart.move.nim.receiving.core.model.ContainerUpdateResponse;
import com.walmart.move.nim.receiving.core.model.InventoryReceivingCorrectionRequest;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.mock.data.MockInstructionResponse;
import com.walmart.move.nim.receiving.rdc.mock.data.MockLocationHeaders;
import com.walmart.move.nim.receiving.rdc.model.LabelAction;
import com.walmart.move.nim.receiving.rdc.utils.RdcContainerUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcInstructionUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.PurchaseReferenceType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.IOException;
import java.util.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RdcUpdateContainerQuantityHandlerTest {
  @Mock private ContainerAdjustmentHelper containerAdjustmentHelper;
  @Mock private ContainerService containerService;
  @Mock private InventoryRestApiClient inventoryRestApiClient;
  @Mock private NimRdsService nimRdsService;
  @Mock private RdcManagedConfig rdcManagedConfig;
  @Mock private InstructionRepository instructionRepository;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private InstructionHelperService instructionHelperService;
  @Mock private RdcInstructionUtils rdcInstructionUtils;
  @Mock private RdcContainerUtils rdcContainerUtils;
  @Mock private LocationService locationService;
  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private ReceiptService receiptService;
  @Mock private SymboticPutawayPublishHelper symboticPutawayPublishHelper;
  @Mock private AppConfig appConfig;

  @InjectMocks private RdcUpdateContainerQuantityHandler rdcUpdateContainerQuantityHandler;

  private HttpHeaders httpheaders;
  private final String trackingId = "lpn1";
  private static final String facilityNum = "32818";
  private static final String countryCode = "US";
  private static final String SYM2 = "SYM2";
  private final Gson gson = new Gson();

  @BeforeMethod
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode(countryCode);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    httpheaders = MockLocationHeaders.getHeaders(facilityNum, countryCode);
    ReflectionTestUtils.setField(rdcUpdateContainerQuantityHandler, "gson", gson);
  }

  @AfterMethod
  public void resetMocks() {
    reset(
        containerAdjustmentHelper,
        containerService,
        nimRdsService,
        inventoryRestApiClient,
        rdcManagedConfig,
        instructionRepository,
        instructionHelperService,
        rdcInstructionUtils,
        rdcContainerUtils,
        tenantSpecificConfigReader,
        locationService,
        containerPersisterService,
        receiptService,
        symboticPutawayPublishHelper,
        appConfig);
  }

  @Test
  public void testUpdateContainerQuantityIsSuccess_forNonAtlasConvertedItems()
      throws ReceivingException, IOException {
    when(containerService.getContainerByTrackingId(anyString())).thenReturn(getMockContainer());
    doNothing().when(containerService).isBackoutContainer(anyString(), anyString());
    when(containerAdjustmentHelper.validateDeliveryStatusForLabelAdjustment(
            anyString(), anyLong(), any(HttpHeaders.class)))
        .thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);
    doNothing().when(nimRdsService).quantityChange(anyInt(), anyString(), any(HttpHeaders.class));
    when(containerAdjustmentHelper.adjustPalletQuantity(
            anyInt(), any(Container.class), anyString()))
        .thenReturn(getMockContainer());
    when(tenantSpecificConfigReader.getDCTimeZone(any())).thenReturn("US/Eastern");
    when(instructionRepository.findById(anyLong())).thenReturn(getMockInstructionResponse());
    doNothing()
        .when(rdcInstructionUtils)
        .publishInstructionToWft(
            any(Container.class),
            anyInt(),
            anyInt(),
            any(LabelAction.class),
            any(HttpHeaders.class));
    when(containerPersisterService.saveContainer(any(Container.class)))
        .thenReturn(getMockContainer());
    when(rdcInstructionUtils.updateInstructionQuantity(anyLong(), anyInt()))
        .thenReturn(getMockInstructionResponse().get());

    ContainerUpdateResponse containerUpdateResponse =
        rdcUpdateContainerQuantityHandler.updateQuantityByTrackingId(
            trackingId,
            getContainerUpdateRequest(),
            MockHttpHeaders.getHeaders(facilityNum, countryCode));

    assertNotNull(containerUpdateResponse);
    assertNotNull(containerUpdateResponse.getContainer());
    assertNotNull(containerUpdateResponse.getPrintJob());

    verify(containerService, times(1)).getContainerByTrackingId(anyString());
    verify(containerService, times(1)).isBackoutContainer(anyString(), anyString());
    verify(containerAdjustmentHelper, times(1))
        .validateDeliveryStatusForLabelAdjustment(anyString(), anyLong(), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(containerAdjustmentHelper, times(1))
        .adjustPalletQuantity(anyInt(), any(Container.class), anyString());
    verify(nimRdsService, times(1)).quantityChange(anyInt(), anyString(), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(1)).getDCTimeZone(any());
    verify(instructionRepository, times(1)).findById(anyLong());
    verify(rdcInstructionUtils, times(1))
        .publishInstructionToWft(
            any(Container.class),
            anyInt(),
            anyInt(),
            eq(LabelAction.CORRECTION),
            any(HttpHeaders.class));
    verify(containerPersisterService, times(1)).saveContainer(any(Container.class));
    verify(rdcInstructionUtils, times(1)).updateInstructionQuantity(anyLong(), anyInt());
  }

  @Test
  public void testUpdateContainerQuantityIsSuccess_forNonAtlasConvertedItems_DA()
      throws ReceivingException, IOException {
    Container container = getMockContainer();
    container
        .getContainerItems()
        .get(0)
        .setInboundChannelMethod(PurchaseReferenceType.CROSSU.name());
    when(containerService.getContainerByTrackingId(anyString())).thenReturn(container);
    doNothing().when(containerService).isBackoutContainer(anyString(), anyString());
    when(containerAdjustmentHelper.validateDeliveryStatusForLabelAdjustment(
            anyString(), anyLong(), any(HttpHeaders.class)))
        .thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);
    doNothing().when(nimRdsService).quantityChange(anyInt(), anyString(), any(HttpHeaders.class));
    when(containerAdjustmentHelper.adjustPalletQuantity(
            anyInt(), any(Container.class), anyString()))
        .thenReturn(container);
    when(tenantSpecificConfigReader.getDCTimeZone(any())).thenReturn("US/Eastern");
    when(instructionRepository.findById(anyLong())).thenReturn(getMockInstructionResponse());
    doNothing()
        .when(rdcInstructionUtils)
        .publishInstructionToWft(
            any(Container.class),
            anyInt(),
            anyInt(),
            any(LabelAction.class),
            any(HttpHeaders.class));
    when(containerPersisterService.saveContainer(any(Container.class))).thenReturn(container);
    when(rdcInstructionUtils.updateInstructionQuantity(anyLong(), anyInt()))
        .thenReturn(getMockInstructionResponse().get());

    ContainerUpdateResponse containerUpdateResponse =
        rdcUpdateContainerQuantityHandler.updateQuantityByTrackingId(
            trackingId,
            getContainerUpdateRequest(),
            MockHttpHeaders.getHeaders(facilityNum, countryCode));

    assertNotNull(containerUpdateResponse);
    assertNotNull(containerUpdateResponse.getContainer());
    assertNotNull(containerUpdateResponse.getPrintJob());

    verify(containerService, times(1)).getContainerByTrackingId(anyString());
    verify(containerService, times(1)).isBackoutContainer(anyString(), anyString());
    verify(containerAdjustmentHelper, times(1))
        .validateDeliveryStatusForLabelAdjustment(anyString(), anyLong(), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(containerAdjustmentHelper, times(1))
        .adjustPalletQuantity(anyInt(), any(Container.class), anyString());
    verify(nimRdsService, times(1)).quantityChange(anyInt(), anyString(), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(1)).getDCTimeZone(any());
    verify(instructionRepository, times(1)).findById(anyLong());
    verify(rdcInstructionUtils, times(1))
        .publishInstructionToWft(
            any(Container.class),
            anyInt(),
            anyInt(),
            eq(LabelAction.DA_BACKOUT),
            any(HttpHeaders.class));
    verify(containerPersisterService, times(1)).saveContainer(any(Container.class));
    verify(rdcInstructionUtils, times(1)).updateInstructionQuantity(anyLong(), anyInt());
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testUpdateContainerQuantityThrowsException_whenExceptionThrownFromLocationService()
      throws ReceivingException {
    when(containerService.getContainerByTrackingId(anyString())).thenReturn(getMockContainer());
    doNothing().when(containerService).isBackoutContainer(anyString(), anyString());
    when(containerAdjustmentHelper.validateDeliveryStatusForLabelAdjustment(
            anyString(), anyLong(), any(HttpHeaders.class)))
        .thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);
    doNothing().when(nimRdsService).quantityChange(anyInt(), anyString(), any(HttpHeaders.class));
    doThrow(
            new ReceivingInternalException(
                ExceptionCodes.LOCATION_SERVICE_ERROR,
                String.format(ReceivingConstants.LOCATION_SERVICE_DOWN, "200")))
        .when(rdcInstructionUtils)
        .publishInstructionToWft(
            any(Container.class),
            anyInt(),
            anyInt(),
            any(LabelAction.class),
            any(HttpHeaders.class));
    when(containerAdjustmentHelper.adjustPalletQuantity(
            anyInt(), any(Container.class), anyString()))
        .thenReturn(getMockContainer());

    rdcUpdateContainerQuantityHandler.updateQuantityByTrackingId(
        trackingId,
        getContainerUpdateRequest(),
        MockHttpHeaders.getHeaders(facilityNum, countryCode));

    verify(containerService, times(1)).getContainerByTrackingId(anyString());
    verify(containerService, times(1)).isBackoutContainer(anyString(), anyString());
    verify(containerAdjustmentHelper, times(1))
        .validateDeliveryStatusForLabelAdjustment(anyString(), anyLong(), any(HttpHeaders.class));
    verify(containerAdjustmentHelper, times(1))
        .adjustPalletQuantity(anyInt(), any(Container.class), anyString());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(nimRdsService, times(1)).quantityChange(anyInt(), anyString(), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(1)).getDCTimeZone(any());
    verify(rdcInstructionUtils, times(1))
        .publishInstructionToWft(
            any(Container.class),
            anyInt(),
            anyInt(),
            eq(LabelAction.CORRECTION),
            any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testUpdateContainerQuantityThrowsException_whenContainerDoesNotExists()
      throws ReceivingException {
    doThrow(
            new ReceivingException(
                LPN_NOT_FOUND_VERIFY_LABEL_ERROR_MESSAGE,
                NOT_FOUND,
                LPN_NOT_FOUND_VERIFY_LABEL_ERROR_CODE,
                LPN_NOT_FOUND_VERIFY_LABEL_ERROR_HEADER))
        .when(containerService)
        .getContainerByTrackingId(anyString());
    rdcUpdateContainerQuantityHandler.updateQuantityByTrackingId(
        trackingId, getContainerUpdateRequest(), httpheaders);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testUpdateContainerQuantityThrowsException_whenContainerIsAlreadyBackedOut()
      throws ReceivingException {
    Container container = getMockContainer();
    container.setContainerStatus(ReceivingConstants.STATUS_BACKOUT);
    when(containerService.getContainerByTrackingId(anyString())).thenReturn(container);
    doThrow(
            new ReceivingException(
                ADJUST_PALLET_QUANTITY_ERROR_MSG_CONTAINER_BACKOUT,
                BAD_REQUEST,
                ADJUST_PALLET_QUANTITY_ERROR_CODE_CONTAINER_BACKOUT,
                ADJUST_PALLET_QUANTITY_ERROR_HEADER_CONTAINER_BACKOUT))
        .when(containerService)
        .isBackoutContainer(anyString(), anyString());
    rdcUpdateContainerQuantityHandler.updateQuantityByTrackingId(
        trackingId, getContainerUpdateRequest(), httpheaders);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testUpdateContainerQuantityThrowsException_whenDeliveryIsFinalizedInGDM()
      throws ReceivingException {
    Container container = getMockContainer();
    container.setContainerStatus(ReceivingConstants.AVAILABLE);
    container.setDeliveryNumber(123L);
    when(containerService.getContainerByTrackingId(anyString())).thenReturn(container);
    when(containerAdjustmentHelper.validateDeliveryStatusForLabelAdjustment(
            anyString(), anyLong(), any(HttpHeaders.class)))
        .thenReturn(
            new CancelContainerResponse(
                trackingId,
                ExceptionCodes.LABEL_CORRECTION_ERROR_FOR_FINALIZED_DELIVERY,
                ReceivingException.LABEL_QUANTITY_ADJUSTMENT_ERROR_MSG_FOR_FINALIZED_DELIVERY));

    rdcUpdateContainerQuantityHandler.updateQuantityByTrackingId(
        trackingId, getContainerUpdateRequest(), httpheaders);

    verify(containerService, times(1)).getContainerByTrackingId(anyString());
    verify(containerAdjustmentHelper, times(1))
        .validateDeliveryStatusForLabelAdjustment(anyString(), anyLong(), any(HttpHeaders.class));
  }

  @Test
  public void testUpdateContainerQuantityIsSuccess_whenInventoryIsEnabled_andItemIsAtlasConverted()
      throws ReceivingException, IOException {
    Container mockContainer = getMockContainerForAtlasConvertedItem();
    mockContainer.getContainerItems().get(0).setSlotType(ReceivingConstants.PRIME_SLOT_TYPE);
    mockContainer.getContainerItems().get(0).setAsrsAlignment(SYM2);
    when(containerService.getContainerByTrackingId(anyString())).thenReturn(mockContainer);
    doNothing().when(containerService).isBackoutContainer(anyString(), anyString());
    when(containerAdjustmentHelper.validateDeliveryStatusForLabelAdjustment(
            anyString(), anyLong(), any(HttpHeaders.class)))
        .thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(inventoryRestApiClient.notifyReceivingCorrectionAdjustment(
            any(InventoryReceivingCorrectionRequest.class), any(HttpHeaders.class)))
        .thenReturn(new ResponseEntity<>("Success", HttpStatus.OK));
    when(containerAdjustmentHelper.adjustQuantityInReceipt(
            anyInt(), anyString(), any(Container.class), anyString()))
        .thenReturn(new Receipt());
    when(tenantSpecificConfigReader.getDCTimeZone(any())).thenReturn("US/Eastern");
    when(instructionRepository.findById(anyLong())).thenReturn(getMockInstructionResponse());
    when(containerAdjustmentHelper.adjustPalletQuantity(
            anyInt(), any(Container.class), anyString()))
        .thenReturn(getMockContainer());
    when(containerPersisterService.saveContainer(any(Container.class))).thenReturn(mockContainer);
    when(rdcInstructionUtils.updateInstructionQuantity(anyLong(), anyInt()))
        .thenReturn(getMockInstructionResponse().get());
    doNothing()
        .when(symboticPutawayPublishHelper)
        .publishSymPutawayUpdateOrDeleteMessage(
            anyString(), any(ContainerItem.class), anyString(), anyInt(), any(HttpHeaders.class));
    doReturn(getMockInstructionResponse().get())
        .when(rdcInstructionUtils)
        .updateInstructionQuantity(anyLong(), anyInt());
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(Arrays.asList(SYM2));
    ContainerUpdateResponse containerUpdateResponse =
        rdcUpdateContainerQuantityHandler.updateQuantityByTrackingId(
            trackingId,
            getContainerUpdateRequest(),
            MockHttpHeaders.getHeaders(facilityNum, countryCode));

    assertNotNull(containerUpdateResponse);
    assertNotNull(containerUpdateResponse.getContainer());
    assertNotNull(containerUpdateResponse.getPrintJob());

    verify(containerService, times(1)).getContainerByTrackingId(anyString());
    verify(containerService, times(1)).isBackoutContainer(anyString(), anyString());
    verify(containerAdjustmentHelper, times(1))
        .validateDeliveryStatusForLabelAdjustment(anyString(), anyLong(), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(inventoryRestApiClient, times(1))
        .notifyReceivingCorrectionAdjustment(
            any(InventoryReceivingCorrectionRequest.class), any(HttpHeaders.class));
    verify(containerAdjustmentHelper, times(1))
        .adjustQuantityInReceipt(anyInt(), anyString(), any(Container.class), anyString());
    verify(tenantSpecificConfigReader, times(1)).getDCTimeZone(any());
    verify(instructionRepository, times(1)).findById(anyLong());
    verify(containerAdjustmentHelper, times(1))
        .adjustPalletQuantity(anyInt(), any(Container.class), anyString());
    verify(containerPersisterService, times(1)).saveContainer(any(Container.class));
    verify(rdcInstructionUtils, times(1)).updateInstructionQuantity(anyLong(), anyInt());
  }

  @Test
  public void
      testUpdateContainerQuantityIsSuccess_whenInventoryIsEnabled_andItemIsAtlasConverted_overrideAdjReasonCodeFromCCM()
          throws ReceivingException, IOException {
    Container mockContainer = getMockContainerForAtlasConvertedItem();
    mockContainer.getContainerItems().get(0).setSlotType(ReceivingConstants.PRIME_SLOT_TYPE);
    mockContainer.getContainerItems().get(0).setAsrsAlignment(SYM2);
    when(containerService.getContainerByTrackingId(anyString())).thenReturn(mockContainer);
    when(rdcManagedConfig.getQuantityAdjustmentReasonCode()).thenReturn(55);
    doNothing().when(containerService).isBackoutContainer(anyString(), anyString());
    when(containerAdjustmentHelper.validateDeliveryStatusForLabelAdjustment(
            anyString(), anyLong(), any(HttpHeaders.class)))
        .thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(inventoryRestApiClient.notifyReceivingCorrectionAdjustment(
            any(InventoryReceivingCorrectionRequest.class), any(HttpHeaders.class)))
        .thenReturn(new ResponseEntity<>("Success", HttpStatus.OK));
    when(containerAdjustmentHelper.adjustQuantityInReceipt(
            anyInt(), anyString(), any(Container.class), anyString()))
        .thenReturn(new Receipt());
    when(tenantSpecificConfigReader.getDCTimeZone(any())).thenReturn("US/Eastern");
    when(instructionRepository.findById(anyLong())).thenReturn(getMockInstructionResponse());
    when(containerAdjustmentHelper.adjustPalletQuantity(
            anyInt(), any(Container.class), anyString()))
        .thenReturn(getMockContainer());
    when(containerPersisterService.saveContainer(any(Container.class))).thenReturn(mockContainer);
    when(rdcInstructionUtils.updateInstructionQuantity(anyLong(), anyInt()))
        .thenReturn(getMockInstructionResponse().get());
    doNothing()
        .when(symboticPutawayPublishHelper)
        .publishSymPutawayUpdateOrDeleteMessage(
            anyString(), any(ContainerItem.class), anyString(), anyInt(), any(HttpHeaders.class));
    doReturn(getMockInstructionResponse().get())
        .when(rdcInstructionUtils)
        .updateInstructionQuantity(anyLong(), anyInt());
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(Arrays.asList(SYM2));
    ContainerUpdateResponse containerUpdateResponse =
        rdcUpdateContainerQuantityHandler.updateQuantityByTrackingId(
            trackingId,
            getContainerUpdateRequest(),
            MockHttpHeaders.getHeaders(facilityNum, countryCode));

    assertNotNull(containerUpdateResponse);
    assertNotNull(containerUpdateResponse.getContainer());
    assertNotNull(containerUpdateResponse.getPrintJob());

    verify(containerService, times(1)).getContainerByTrackingId(anyString());
    verify(containerService, times(1)).isBackoutContainer(anyString(), anyString());
    verify(containerAdjustmentHelper, times(1))
        .validateDeliveryStatusForLabelAdjustment(anyString(), anyLong(), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(inventoryRestApiClient, times(1))
        .notifyReceivingCorrectionAdjustment(
            any(InventoryReceivingCorrectionRequest.class), any(HttpHeaders.class));
    verify(containerAdjustmentHelper, times(1))
        .adjustQuantityInReceipt(anyInt(), anyString(), any(Container.class), anyString());
    verify(tenantSpecificConfigReader, times(1)).getDCTimeZone(any());
    verify(instructionRepository, times(1)).findById(anyLong());
    verify(containerAdjustmentHelper, times(1))
        .adjustPalletQuantity(anyInt(), any(Container.class), anyString());
    verify(containerPersisterService, times(1)).saveContainer(any(Container.class));
    verify(rdcInstructionUtils, times(1)).updateInstructionQuantity(anyLong(), anyInt());
    verify(rdcManagedConfig, times(2)).getQuantityAdjustmentReasonCode();
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void
      testUpdateContainerQuantityIsSuccess_DoNotAllowContainerAdjustmentForAtlasDAContainers()
          throws ReceivingException {
    Container mockContainer = getMockContainerForAtlasConvertedItem();
    mockContainer.getContainerItems().get(0).setInboundChannelMethod("CROSSU");
    when(containerService.getContainerByTrackingId(anyString())).thenReturn(mockContainer);
    when(rdcManagedConfig.getQuantityAdjustmentReasonCode()).thenReturn(55);
    doNothing().when(containerService).isBackoutContainer(anyString(), anyString());
    when(containerAdjustmentHelper.validateDeliveryStatusForLabelAdjustment(
            anyString(), anyLong(), any(HttpHeaders.class)))
        .thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    rdcUpdateContainerQuantityHandler.updateQuantityByTrackingId(
        trackingId,
        getContainerUpdateRequest(),
        MockHttpHeaders.getHeaders(facilityNum, countryCode));

    verify(containerService, times(1)).getContainerByTrackingId(anyString());
    verify(containerService, times(1)).isBackoutContainer(anyString(), anyString());
    verify(containerAdjustmentHelper, times(1))
        .validateDeliveryStatusForLabelAdjustment(anyString(), anyLong(), any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testUpdateContainerQuantityThrowsException_WhenInventoryServiceThrowsException()
      throws ReceivingException {
    when(containerService.getContainerByTrackingId(anyString()))
        .thenReturn(getMockContainerForAtlasConvertedItem());
    doNothing().when(containerService).isBackoutContainer(anyString(), anyString());
    when(containerAdjustmentHelper.validateDeliveryStatusForLabelAdjustment(
            anyString(), anyLong(), any(HttpHeaders.class)))
        .thenReturn(null);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(true);
    when(inventoryRestApiClient.notifyReceivingCorrectionAdjustment(
            any(InventoryReceivingCorrectionRequest.class), any(HttpHeaders.class)))
        .thenThrow(
            new ReceivingException(
                ADJUST_PALLET_QUANTITY_ERROR_MSG,
                BAD_REQUEST,
                ADJUST_PALLET_QUANTITY_ERROR_CODE,
                ADJUST_PALLET_QUANTITY_ERROR_HEADER));

    rdcUpdateContainerQuantityHandler.updateQuantityByTrackingId(
        trackingId, getContainerUpdateRequest(), httpheaders);

    verify(containerService, times(1)).getContainerByTrackingId(anyString());
    verify(containerService, times(1)).isBackoutContainer(anyString(), anyString());
    verify(containerAdjustmentHelper, times(1))
        .validateDeliveryStatusForLabelAdjustment(anyString(), anyLong(), any(HttpHeaders.class));
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(inventoryRestApiClient, times(1))
        .notifyReceivingCorrectionAdjustment(
            any(InventoryReceivingCorrectionRequest.class), any(HttpHeaders.class));
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testUpdateContainerQuantityThrowsException_whenRdsServiceThrowsException()
      throws ReceivingException {
    when(containerService.getContainerByTrackingId(anyString())).thenReturn(getMockContainer());
    doNothing().when(containerService).isBackoutContainer(anyString(), anyString());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false))
        .thenReturn(false);
    when(containerAdjustmentHelper.validateDeliveryStatusForLabelAdjustment(
            anyString(), anyLong(), any(HttpHeaders.class)))
        .thenReturn(null);
    doThrow(new RestClientException("rest client error"))
        .when(nimRdsService)
        .quantityChange(anyInt(), anyString(), any(HttpHeaders.class));

    rdcUpdateContainerQuantityHandler.updateQuantityByTrackingId(
        trackingId, getContainerUpdateRequest(), httpheaders);

    verify(containerService, times(1)).getContainerByTrackingId(anyString());
    verify(containerService, times(1)).isBackoutContainer(anyString(), anyString());
    verify(tenantSpecificConfigReader, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_INVENTORY_INTEGRATION_ENABLED,
            false);
    verify(containerAdjustmentHelper, times(1))
        .validateDeliveryStatusForLabelAdjustment(anyString(), anyLong(), any(HttpHeaders.class));
    verify(nimRdsService, times(1)).quantityChange(anyInt(), anyString(), any(HttpHeaders.class));
  }

  private Container getMockContainer() {
    Container container = new Container();
    container.setDeliveryNumber(123L);
    container.setInstructionId(67752L);
    container.setLocation("100");
    container.setTrackingId("lpn1");
    container.setParentTrackingId(null);
    container.setCreateUser("sysadmin");
    container.setCompleteTs(new Date());
    container.setLastChangedUser("sysadmin");
    container.setLastChangedTs(new Date());
    container.setPublishTs(new Date());
    container.setContainerStatus("Created");

    ContainerItem containerItem = new ContainerItem();
    containerItem.setTrackingId(trackingId);
    containerItem.setPurchaseReferenceNumber("PO1");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setQuantity(24);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem.setVnpkQty(6);
    containerItem.setWhpkQty(6);
    containerItem.setActualTi(4);
    containerItem.setActualHi(6);
    containerItem.setSlotType(ReceivingConstants.PRIME_SLOT_TYPE);
    containerItem.setAsrsAlignment("SYM1");
    containerItem.setFinancialReportingGroupCode("US");
    containerItem.setBaseDivisionCode("WM");
    container.setContainerItems(Collections.singletonList(containerItem));
    return container;
  }

  private Container getMockContainerForAtlasConvertedItem() {
    Container mockContainer = getMockContainer();
    Map<String, String> miscInfoMap = new HashMap<>();
    miscInfoMap.put(ReceivingConstants.IS_ATLAS_CONVERTED_ITEM, "true");
    mockContainer.getContainerItems().get(0).setContainerItemMiscInfo(miscInfoMap);
    return mockContainer;
  }

  private ContainerUpdateRequest getContainerUpdateRequest() {
    ContainerUpdateRequest containerUpdateRequest = new ContainerUpdateRequest();
    containerUpdateRequest.setAdjustQuantity(2);
    return containerUpdateRequest;
  }

  private Optional<Instruction> getMockInstructionResponse() throws IOException {
    Instruction instruction = MockInstructionResponse.getMockInstruction();
    return Optional.of(instruction);
  }
}
