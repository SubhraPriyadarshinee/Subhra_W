package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.*;
import static com.walmart.move.nim.receiving.core.mock.data.MockGdmResponse.getGdmDeliveryHistoryValidFinalisedDate;
import static com.walmart.move.nim.receiving.core.service.DefaultUpdateContainerQuantityRequestHandlerTest.getItemConfigDetails_Converted;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.AUTOMATION_TYPE_DEMATIC;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.VNPK;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.builder.FinalizePORequestBodyBuilder;
import com.walmart.move.nim.receiving.core.client.dcfin.DCFinRestApiClient;
import com.walmart.move.nim.receiving.core.client.gdm.AsyncGdmRestApiClient;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClientException;
import com.walmart.move.nim.receiving.core.client.gls.GlsRestApiClient;
import com.walmart.move.nim.receiving.core.client.itemconfig.ItemConfigApiClient;
import com.walmart.move.nim.receiving.core.client.itemconfig.ItemConfigRestApiClientException;
import com.walmart.move.nim.receiving.core.client.itemconfig.model.ItemConfigDetails;
import com.walmart.move.nim.receiving.core.client.move.AsyncMoveRestApiClient;
import com.walmart.move.nim.receiving.core.common.MovePublisher;
import com.walmart.move.nim.receiving.core.common.ReceiptPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.mock.data.MockContainer;
import com.walmart.move.nim.receiving.core.mock.data.MockGdmResponse;
import com.walmart.move.nim.receiving.core.mock.data.MockInstruction;
import com.walmart.move.nim.receiving.core.mock.data.MockReceipt;
import com.walmart.move.nim.receiving.core.model.ContainerUpdateRequest;
import com.walmart.move.nim.receiving.core.model.ContainerUpdateResponse;
import com.walmart.move.nim.receiving.core.model.GdmDeliveryHistoryResponse;
import com.walmart.move.nim.receiving.core.model.LocationInfo;
import com.walmart.move.nim.receiving.core.model.gdm.v3.FinalizePORequestBody;
import com.walmart.move.nim.receiving.core.model.inventory.InventoryContainerDetails;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class GdcUpdateContainerQuantityRequestHandlerTest {
  @Mock private ContainerService containerService;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private FinalizePORequestBodyBuilder finalizePORequestBodyBuilder;
  @Mock private GlsRestApiClient glsRestApiClient;
  @Mock private ReceiptPublisher receiptPublisher;
  @Mock private DCFinRestApiClient dcFinRestApiClient;
  @Mock private MovePublisher movePublisher;
  @Mock AsyncInventoryService asyncInventoryService;
  @Mock AsyncMoveRestApiClient asyncMoveRestApiClient;
  @Mock AsyncGdmRestApiClient asyncGdmRestApiClient;
  @Mock LocationService locationService;
  @Mock ReceiptService receiptService;

  @Mock
  private DefaultUpdateContainerQuantityRequestHandler defaultUpdateContainerQuantityRequestHandler;

  @Spy private ItemConfigApiClient itemConfig;
  private Gson gson = new Gson();

  @InjectMocks
  private GdcUpdateContainerQuantityRequestHandler gdcUpdateContainerQuantityRequestHandler;

  private final String trackingId = "lpn1";
  private static final String facilityNum = "6071";
  private static final String countryCode = "US";
  final Container defaultContainer = MockContainer.getContainer();

  @BeforeMethod
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode(countryCode);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    ReflectionTestUtils.setField(itemConfig, "configUtils", configUtils);
  }

  @AfterMethod
  public void resetMocks() {
    reset(containerService, configUtils, finalizePORequestBodyBuilder, receiptPublisher);
    reset(dcFinRestApiClient);
    reset(itemConfig);
  }

  @Test
  public void testUpdateContainerQuantityIsSuccess_case1_automationDc_defaultFlow()
      throws ReceivingException, GDMRestApiClientException {

    final Container mockContainer = getMockContainer();
    when(containerService.getContainerByTrackingId(anyString())).thenReturn(mockContainer);
    doNothing().when(containerService).isBackoutContainer(anyString(), anyString());
    final ContainerItem ci = getContainerItem();
    when(containerService.getContainerItem(anyString(), any())).thenReturn(ci);
    Instruction instruction = MockInstruction.getInstruction();
    when(containerService.getInstruction(anyString(), any())).thenReturn(instruction);
    when(finalizePORequestBodyBuilder.buildFrom(any(), any(), any()))
        .thenReturn(new FinalizePORequestBody());

    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, IS_MANUAL_GDC_ENABLED, false);
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, PUBLISH_TO_WITRON_DISABLED, false);
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, IS_DC_ONE_ATLAS_ENABLED, false);
    doReturn(false)
        .when(itemConfig)
        .isOneAtlasConvertedItem(
            anyBoolean(), any(StringBuilder.class), anyLong(), any(HttpHeaders.class));

    Receipt masterReceipt = MockReceipt.getOSDRMasterReceipt();
    doReturn(masterReceipt)
        .when(receiptService)
        .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyLong(), anyString(), anyInt());
    // execute
    ContainerUpdateResponse containerUpdateResponse =
        gdcUpdateContainerQuantityRequestHandler.updateQuantityByTrackingId(
            "abc123456",
            getContainerUpdateRequest(),
            MockHttpHeaders.getHeaders(facilityNum, countryCode));

    // verify
    assertNotNull(containerUpdateResponse);
    assertNull(containerUpdateResponse.getPrintJob());

    // default flow to hit
    verify(containerService, times(1)).getContainerByTrackingId(anyString());
    verify(containerService, times(1)).isBackoutContainer(anyString(), anyString());
    verify(containerService, times(1)).adjustQuantityValidation(any(), any(), any(), any());
    verify(containerService, times(1))
        .adjustContainerItemQuantityAndGetDiff(any(), any(), any(), any(), any(), any());
    verify(containerService, times(1))
        .adjustQuantityInReceiptUseInventoryData(any(), any(), any(), any(), any(), any(), any());
    verify(finalizePORequestBodyBuilder, times(1)).buildFrom(any(), any(), any());
    verify(containerService, times(1))
        .adjustQuantityInInventoryService(any(), any(), any(), any(), any(), anyInt());
    verify(containerService, times(1)).postFinalizePoOsdrToGdm(any(), any(), any(), any());
    verify(receiptPublisher, times(1)).publishReceiptUpdate(any(), any(), any());

    // GLS flow not to hit
    verify(glsRestApiClient, times(0)).createGlsAdjustPayload(any(), any(), any(), any(), any());
    verify(glsRestApiClient, times(0)).adjustOrCancel(any(), any());
    verify(movePublisher, times(0)).publishCancelMove(anyString(), any());
  }

  @Test
  public void testUpdateContainerQuantityIsSuccess_case1_automationDc_CancelMoveFlagEnabled_VTR()
      throws ReceivingException, GDMRestApiClientException {

    final Container mockContainer = getMockContainer();
    when(containerService.getContainerByTrackingId(anyString())).thenReturn(mockContainer);
    doNothing().when(containerService).isBackoutContainer(anyString(), anyString());
    final ContainerItem ci = getContainerItem();
    when(containerService.getContainerItem(anyString(), any())).thenReturn(ci);
    Instruction instruction = MockInstruction.getInstruction();
    when(containerService.getInstruction(anyString(), any())).thenReturn(instruction);
    when(finalizePORequestBodyBuilder.buildFrom(any(), any(), any()))
        .thenReturn(new FinalizePORequestBody());

    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, IS_MANUAL_GDC_ENABLED, false);
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, PUBLISH_TO_WITRON_DISABLED, false);
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, IS_DC_ONE_ATLAS_ENABLED, false);
    doReturn(false)
        .when(itemConfig)
        .isOneAtlasConvertedItem(
            anyBoolean(), any(StringBuilder.class), anyLong(), any(HttpHeaders.class));
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, PUBLISH_CANCEL_MOVE_ENABLED, false);

    ContainerUpdateRequest receivingCorrectionRequestToVTR = getContainerUpdateRequest();
    receivingCorrectionRequestToVTR.setAdjustQuantity(0);

    // execute
    ContainerUpdateResponse containerUpdateResponse =
        gdcUpdateContainerQuantityRequestHandler.updateQuantityByTrackingId(
            "abc123456",
            receivingCorrectionRequestToVTR,
            MockHttpHeaders.getHeaders(facilityNum, countryCode));

    // verify
    assertNotNull(containerUpdateResponse);
    assertNull(containerUpdateResponse.getPrintJob());

    // default flow to hit
    verify(containerService, times(1)).getContainerByTrackingId(anyString());
    verify(containerService, times(1)).isBackoutContainer(anyString(), anyString());
    verify(containerService, times(1)).adjustQuantityValidation(any(), any(), any(), any());
    verify(containerService, times(1))
        .adjustContainerItemQuantityAndGetDiff(any(), any(), any(), any(), any(), any());
    verify(containerService, times(1))
        .adjustQuantityInReceiptUseInventoryData(any(), any(), any(), any(), any(), any(), any());
    verify(finalizePORequestBodyBuilder, times(1)).buildFrom(any(), any(), any());
    verify(containerService, times(1))
        .adjustQuantityInInventoryService(any(), any(), any(), any(), any(), anyInt());
    verify(containerService, times(1)).postFinalizePoOsdrToGdm(any(), any(), any(), any());
    verify(receiptPublisher, times(1)).publishReceiptUpdate(any(), any(), any());
    verify(movePublisher, times(1)).publishCancelMove(anyString(), any());

    // GLS flow not to hit
    verify(glsRestApiClient, times(0)).createGlsAdjustPayload(any(), any(), any(), any(), any());
    verify(glsRestApiClient, times(0)).adjustOrCancel(any(), any());
  }

  @Test
  public void testUpdateContainerQuantityIsSuccess_case2_isOneAtlasAndConverted_negativeCorrection()
      throws ReceivingException, GDMRestApiClientException, ItemConfigRestApiClientException,
          IOException {

    final Container mockContainer = getMockContainer();
    when(containerService.getContainerByTrackingId(anyString())).thenReturn(mockContainer);
    doNothing().when(containerService).isBackoutContainer(anyString(), anyString());
    final ContainerItem ci = getContainerItem();

    when(containerService.getContainerItem(anyString(), any())).thenReturn(ci);
    Instruction instruction = MockInstruction.getInstruction();
    when(containerService.getInstruction(anyString(), any())).thenReturn(instruction);
    when(finalizePORequestBodyBuilder.buildFrom(any(), any(), any()))
        .thenReturn(new FinalizePORequestBody());

    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, PUBLISH_TO_WITRON_DISABLED, false);

    doNothing().when(dcFinRestApiClient).adjustOrVtr(any(), any());
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, IS_MANUAL_GDC_ENABLED, false);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, IS_DC_ONE_ATLAS_ENABLED, false);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, IS_GLS_API_ENABLED, false);
    doReturn(getItemConfigDetails_Converted())
        .when(itemConfig)
        .searchAtlasConvertedItems(any(Set.class), any(HttpHeaders.class));
    doReturn(true)
        .when(itemConfig)
        .isOneAtlasConvertedItem(
            anyBoolean(), any(StringBuilder.class), anyLong(), any(HttpHeaders.class));
    doReturn(CompletableFuture.completedFuture(getInventoryContainerDetails()))
        .when(asyncInventoryService)
        .getInventoryContainerDetails(anyString(), any(HttpHeaders.class));
    doReturn(CompletableFuture.completedFuture(getMoveContainerDetails()))
        .when(asyncMoveRestApiClient)
        .getMoveContainerDetails(anyString(), any(HttpHeaders.class));
    GdmDeliveryHistoryResponse gdmDeliveryHistoryResponse =
        gson.fromJson(
            MockGdmResponse.getDeliveryHistoryReturnsBillNotSignedResponse(),
            GdmDeliveryHistoryResponse.class);
    doReturn(CompletableFuture.completedFuture(gdmDeliveryHistoryResponse))
        .when(asyncGdmRestApiClient)
        .getDeliveryHistory(anyLong(), any(HttpHeaders.class));
    when(configUtils.getCcmValue(anyInt(), eq(INVALID_INVENTORY_STATUS_TO_ADJUST), anyString()))
        .thenReturn("ALLOCATED");
    when(configUtils.getCcmValue(anyInt(), eq(INVALID_MOVE_STATUS_CORRECTION), anyString()))
        .thenReturn("HAULWORKING");

    // execute
    ContainerUpdateResponse containerUpdateResponse =
        gdcUpdateContainerQuantityRequestHandler.updateQuantityByTrackingId(
            "123-abc123456",
            getContainerUpdateRequest(),
            MockHttpHeaders.getHeaders(facilityNum, countryCode));

    verify(containerService, times(1)).adjustQuantityValidation(any(), any(), any(), any());
    verify(containerService, times(1))
        .adjustContainerItemQuantityAndGetDiff(eq("1a2bc3d4"), any(), any(), any(), any(), any());
    verify(containerService, times(1))
        .adjustQuantityInReceiptUseInventoryData(any(), any(), any(), any(), any(), any(), any());
    verify(finalizePORequestBodyBuilder, times(1)).buildFrom(any(), any(), any());
    verify(containerService, times(1)).postFinalizePoOsdrToGdm(any(), any(), any(), any());

    // Gls
    // ensure hits
    verify(itemConfig, times(1)).searchAtlasConvertedItems(any(Set.class), any(HttpHeaders.class));
    verify(itemConfig, times(1)).isAtlasConvertedItem(anyLong(), any(HttpHeaders.class));
    verify(receiptPublisher, times(1)).publishReceiptUpdate(any(), any(), any());
    verify(containerService, times(1))
        .adjustQuantityInInventoryService(
            anyString(),
            anyString(),
            anyInt(),
            any(HttpHeaders.class),
            any(ContainerItem.class),
            anyInt());
    // ensure no hit
    verify(dcFinRestApiClient, times(0)).adjustOrVtr(any(), any());
    verify(glsRestApiClient, times(0)).createGlsAdjustPayload(any(), any(), any(), any(), any());
    verify(glsRestApiClient, times(0)).adjustOrCancel(any(), any());
    verify(movePublisher, times(0)).publishCancelMove(anyString(), any());
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp = "Enter a new quantity")
  public void testUpdateContainerQuantityIsSuccess_case2a_newQtySameAsOldQty()
      throws ReceivingException, GDMRestApiClientException, ItemConfigRestApiClientException,
          IOException {

    final Container mockContainer = getMockContainer();
    when(containerService.getContainerByTrackingId(anyString())).thenReturn(mockContainer);
    doNothing().when(containerService).isBackoutContainer(anyString(), anyString());
    final ContainerItem ci = getContainerItem();

    when(containerService.getContainerItem(anyString(), any())).thenReturn(ci);
    Instruction instruction = MockInstruction.getInstruction();
    when(containerService.getInstruction(anyString(), any())).thenReturn(instruction);
    when(finalizePORequestBodyBuilder.buildFrom(any(), any(), any()))
        .thenReturn(new FinalizePORequestBody());

    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, PUBLISH_TO_WITRON_DISABLED, false);

    doNothing().when(dcFinRestApiClient).adjustOrVtr(any(), any());
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, IS_MANUAL_GDC_ENABLED, false);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, IS_DC_ONE_ATLAS_ENABLED, false);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, IS_GLS_API_ENABLED, false);
    doReturn(getItemConfigDetails_Converted())
        .when(itemConfig)
        .searchAtlasConvertedItems(any(Set.class), any(HttpHeaders.class));
    doReturn(true)
        .when(itemConfig)
        .isOneAtlasConvertedItem(
            anyBoolean(), any(StringBuilder.class), anyLong(), any(HttpHeaders.class));
    doReturn(CompletableFuture.completedFuture(getInventoryContainerDetails()))
        .when(asyncInventoryService)
        .getInventoryContainerDetails(anyString(), any(HttpHeaders.class));
    doReturn(CompletableFuture.completedFuture(getMoveContainerDetails()))
        .when(asyncMoveRestApiClient)
        .getMoveContainerDetails(anyString(), any(HttpHeaders.class));
    GdmDeliveryHistoryResponse gdmDeliveryHistoryResponse =
        gson.fromJson(
            MockGdmResponse.getDeliveryHistoryReturnsBillNotSignedResponse(),
            GdmDeliveryHistoryResponse.class);
    doReturn(CompletableFuture.completedFuture(gdmDeliveryHistoryResponse))
        .when(asyncGdmRestApiClient)
        .getDeliveryHistory(anyLong(), any(HttpHeaders.class));
    when(configUtils.getCcmValue(anyInt(), eq(INVALID_INVENTORY_STATUS_TO_ADJUST), anyString()))
        .thenReturn("ALLOCATED");
    when(configUtils.getCcmValue(anyInt(), eq(INVALID_MOVE_STATUS_CORRECTION), anyString()))
        .thenReturn("HAULWORKING");

    ContainerUpdateRequest containerUpdateRequest = getContainerUpdateRequest();
    containerUpdateRequest.setAdjustQuantity(containerUpdateRequest.getInventoryQuantity());
    // execute

    gdcUpdateContainerQuantityRequestHandler.updateQuantityByTrackingId(
        "123-abc123456",
        containerUpdateRequest,
        MockHttpHeaders.getHeaders(facilityNum, countryCode));
  }

  @Test
  public void testUpdateContainerQuantityIsSuccess_billNotSignedAndMoveNotCompleted()
      throws ReceivingException, ItemConfigRestApiClientException, GDMRestApiClientException {

    final Container mockContainer = getMockContainer();
    when(containerService.getContainerByTrackingId(anyString())).thenReturn(mockContainer);
    doNothing().when(containerService).isBackoutContainer(anyString(), anyString());
    final ContainerItem ci = getContainerItem();

    when(containerService.getContainerItem(anyString(), any())).thenReturn(ci);
    Instruction instruction = MockInstruction.getInstruction();
    when(containerService.getInstruction(anyString(), any())).thenReturn(instruction);
    when(finalizePORequestBodyBuilder.buildFrom(any(), any(), any()))
        .thenReturn(new FinalizePORequestBody());

    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, PUBLISH_TO_WITRON_DISABLED, false);

    doNothing().when(dcFinRestApiClient).adjustOrVtr(any(), any());
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, IS_MANUAL_GDC_ENABLED, false);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, IS_DC_ONE_ATLAS_ENABLED, false);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, IS_GLS_API_ENABLED, false);
    doReturn(getItemConfigDetails_Converted())
        .when(itemConfig)
        .searchAtlasConvertedItems(any(Set.class), any(HttpHeaders.class));
    doReturn(true)
        .when(itemConfig)
        .isOneAtlasConvertedItem(
            anyBoolean(), any(StringBuilder.class), anyLong(), any(HttpHeaders.class));
    doReturn(CompletableFuture.completedFuture(getInventoryContainerDetails()))
        .when(asyncInventoryService)
        .getInventoryContainerDetails(anyString(), any(HttpHeaders.class));
    doReturn(CompletableFuture.completedFuture(getMoveContainerDetails()))
        .when(asyncMoveRestApiClient)
        .getMoveContainerDetails(anyString(), any(HttpHeaders.class));
    doReturn(
            CompletableFuture.completedFuture(
                MockGdmResponse.getGdmDeliveryHistoryValidFinalisedDate()))
        .when(asyncGdmRestApiClient)
        .getDeliveryHistory(anyLong(), any(HttpHeaders.class));
    when(configUtils.getCcmValue(anyInt(), eq(INVALID_INVENTORY_STATUS_TO_ADJUST), anyString()))
        .thenReturn("ALLOCATED");
    when(configUtils.getCcmValue(anyInt(), eq(INVALID_MOVE_STATUS_CORRECTION), anyString()))
        .thenReturn("HAULWORKING");

    // execute
    final ContainerUpdateRequest receivingCorrectionRequestToVTR = getContainerUpdateRequest();
    ContainerUpdateResponse containerUpdateResponse =
        gdcUpdateContainerQuantityRequestHandler.updateQuantityByTrackingId(
            "123-abc123456",
            receivingCorrectionRequestToVTR,
            MockHttpHeaders.getHeaders(facilityNum, countryCode));

    verify(containerService, times(1)).adjustQuantityValidation(any(), any(), any(), any());
    verify(containerService, times(1))
        .adjustContainerItemQuantityAndGetDiff(any(), any(), any(), any(), any(), any());
    verify(containerService, times(1))
        .adjustQuantityInReceiptUseInventoryData(any(), any(), any(), any(), any(), any(), any());
    verify(finalizePORequestBodyBuilder, times(1)).buildFrom(any(), any(), any());
    verify(containerService, times(1)).postFinalizePoOsdrToGdm(any(), any(), any(), any());

    // ensure hits
    // ensure hits
    verify(itemConfig, times(1)).searchAtlasConvertedItems(any(Set.class), any(HttpHeaders.class));
    verify(itemConfig, times(1)).isAtlasConvertedItem(anyLong(), any(HttpHeaders.class));
    verify(receiptPublisher, times(1)).publishReceiptUpdate(any(), any(), any());
    verify(containerService, times(1))
        .adjustQuantityInInventoryService(
            anyString(),
            anyString(),
            anyInt(),
            any(HttpHeaders.class),
            any(ContainerItem.class),
            anyInt());
    // ensure no hit
    verify(dcFinRestApiClient, times(0)).adjustOrVtr(any(), any());
    verify(glsRestApiClient, times(0)).createGlsAdjustPayload(any(), any(), any(), any(), any());
    verify(glsRestApiClient, times(0)).adjustOrCancel(any(), any());
    verify(movePublisher, times(0)).publishCancelMove(anyString(), any());
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "Negative receiving correction cannot be performed as pallet is putaway, contact QA to do an adjustment")
  public void testUpdateContainerQuantity_NegativeRC_PutawayCompleted()
      throws ReceivingException, ItemConfigRestApiClientException {

    final Container mockContainer = getMockContainer();
    when(containerService.getContainerByTrackingId(anyString())).thenReturn(mockContainer);
    doNothing().when(containerService).isBackoutContainer(anyString(), anyString());
    final ContainerItem ci = getContainerItem();

    when(containerService.getContainerItem(anyString(), any())).thenReturn(ci);
    Instruction instruction = MockInstruction.getInstruction();
    when(containerService.getInstruction(anyString(), any())).thenReturn(instruction);
    when(finalizePORequestBodyBuilder.buildFrom(any(), any(), any()))
        .thenReturn(new FinalizePORequestBody());

    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, PUBLISH_TO_WITRON_DISABLED, false);

    doNothing().when(dcFinRestApiClient).adjustOrVtr(any(), any());
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, IS_MANUAL_GDC_ENABLED, false);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, IS_DC_ONE_ATLAS_ENABLED, false);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, IS_GLS_API_ENABLED, false);
    doReturn(getItemConfigDetails_Converted())
        .when(itemConfig)
        .searchAtlasConvertedItems(any(Set.class), any(HttpHeaders.class));
    doReturn(true)
        .when(itemConfig)
        .isOneAtlasConvertedItem(
            anyBoolean(), any(StringBuilder.class), anyLong(), any(HttpHeaders.class));
    doReturn(CompletableFuture.completedFuture(getInventoryContainerDetails()))
        .when(asyncInventoryService)
        .getInventoryContainerDetails(anyString(), any(HttpHeaders.class));

    doReturn(CompletableFuture.completedFuture(getInvalidMoveContainerDetailsAfterBillSigned()))
        .when(asyncMoveRestApiClient)
        .getMoveContainerDetails(anyString(), any(HttpHeaders.class));
    doReturn(
            CompletableFuture.completedFuture(
                MockGdmResponse.getGdmDeliveryHistoryValidFinalisedDate()))
        .when(asyncGdmRestApiClient)
        .getDeliveryHistory(anyLong(), any(HttpHeaders.class));
    when(configUtils.getCcmValue(anyInt(), eq(INVALID_INVENTORY_STATUS_TO_ADJUST), anyString()))
        .thenReturn("ALLOCATED");

    when(configUtils.getCcmValue(anyInt(), eq(INVALID_MOVE_STATUS_CORRECTION), anyString()))
        .thenReturn("HAULWORKING");
    when(configUtils.getCcmValue(anyInt(), eq(INVALID_MOVE_IF_BILL_NOT_SIGNED), anyString()))
        .thenReturn("PutawayCompleted");

    // execute

    gdcUpdateContainerQuantityRequestHandler.updateQuantityByTrackingId(
        "123-abc123456",
        getContainerUpdateRequest(),
        MockHttpHeaders.getHeaders(facilityNum, countryCode));
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "Negative receiving correction can not be performed, bill is signed. Please contact QA to make an adjustment.")
  public void testUpdateContainerQuantityIsSuccess_case_BillSigned()
      throws ReceivingException, ItemConfigRestApiClientException {

    final Container mockContainer = getMockContainer();
    when(containerService.getContainerByTrackingId(anyString())).thenReturn(mockContainer);
    doNothing().when(containerService).isBackoutContainer(anyString(), anyString());
    final ContainerItem ci = getContainerItem();

    when(containerService.getContainerItem(anyString(), any())).thenReturn(ci);
    Instruction instruction = MockInstruction.getInstruction();
    when(containerService.getInstruction(anyString(), any())).thenReturn(instruction);
    when(finalizePORequestBodyBuilder.buildFrom(any(), any(), any()))
        .thenReturn(new FinalizePORequestBody());

    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, PUBLISH_TO_WITRON_DISABLED, false);

    doNothing().when(dcFinRestApiClient).adjustOrVtr(any(), any());
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, IS_MANUAL_GDC_ENABLED, false);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, IS_DC_ONE_ATLAS_ENABLED, false);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, IS_GLS_API_ENABLED, false);
    doReturn(getItemConfigDetails_Converted())
        .when(itemConfig)
        .searchAtlasConvertedItems(any(Set.class), any(HttpHeaders.class));
    doReturn(true)
        .when(itemConfig)
        .isOneAtlasConvertedItem(
            anyBoolean(), any(StringBuilder.class), anyLong(), any(HttpHeaders.class));
    doReturn(CompletableFuture.completedFuture(getInventoryContainerDetails()))
        .when(asyncInventoryService)
        .getInventoryContainerDetails(anyString(), any(HttpHeaders.class));

    doReturn(CompletableFuture.completedFuture(getInvalidMoveContainerDetailsAfterBillSigned()))
        .when(asyncMoveRestApiClient)
        .getMoveContainerDetails(anyString(), any(HttpHeaders.class));
    doReturn(CompletableFuture.completedFuture(MockGdmResponse.getGdmDeliveryHistoryBillSigned()))
        .when(asyncGdmRestApiClient)
        .getDeliveryHistory(anyLong(), any(HttpHeaders.class));
    when(configUtils.getCcmValue(anyInt(), eq(INVALID_INVENTORY_STATUS_TO_ADJUST), anyString()))
        .thenReturn("ALLOCATED");
    when(configUtils.getCcmValue(anyInt(), eq(INVALID_MOVE_STATUS_CORRECTION), anyString()))
        .thenReturn("HAULWORKING");

    when(configUtils.getCcmValue(anyInt(), eq(INVALID_MOVE_IF_BILL_NOT_SIGNED), anyString()))
        .thenReturn("PutawayCompleted");

    // execute
    gdcUpdateContainerQuantityRequestHandler.updateQuantityByTrackingId(
        "123-abc123456",
        getContainerUpdateRequest(),
        MockHttpHeaders.getHeaders(facilityNum, countryCode));
  }

  @Test
  public void testUpdateContainerQuantityIsSuccess_case3_isOneAtlasAndNotConverted_VTR()
      throws ReceivingException, GDMRestApiClientException, ItemConfigRestApiClientException {

    final Container mockContainer = getMockContainer();
    when(containerService.getContainerByTrackingId(anyString())).thenReturn(mockContainer);
    doNothing().when(containerService).isBackoutContainer(anyString(), anyString());
    final ContainerItem ci = getContainerItem();
    when(containerService.getContainerItem(anyString(), any())).thenReturn(ci);
    Instruction instruction = MockInstruction.getInstruction();
    when(containerService.getInstruction(anyString(), any())).thenReturn(instruction);
    when(finalizePORequestBodyBuilder.buildFrom(any(), any(), any()))
        .thenReturn(new FinalizePORequestBody());

    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, PUBLISH_TO_WITRON_DISABLED, false);

    doReturn(false)
        .when(itemConfig)
        .isOneAtlasConvertedItem(
            anyBoolean(), any(StringBuilder.class), anyLong(), any(HttpHeaders.class));

    // GLS
    doNothing().when(dcFinRestApiClient).adjustOrVtr(any(), any());
    // GLS flags
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, IS_MANUAL_GDC_ENABLED, false);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, IS_DC_ONE_ATLAS_ENABLED, false);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, IS_GLS_API_ENABLED, false);
    List<ItemConfigDetails> emptyList = new ArrayList<>();
    doReturn(emptyList)
        .when(itemConfig)
        .searchAtlasConvertedItems(any(Set.class), any(HttpHeaders.class));
    // end of GLS flags

    // execute
    final ContainerUpdateRequest receivingCorrectionRequestToVTR = getContainerUpdateRequest();
    receivingCorrectionRequestToVTR.setAdjustQuantity(0);
    ContainerUpdateResponse containerUpdateResponse =
        gdcUpdateContainerQuantityRequestHandler.updateQuantityByTrackingId(
            "123-abc123456",
            receivingCorrectionRequestToVTR,
            MockHttpHeaders.getHeaders(facilityNum, countryCode));

    verify(containerService, times(1)).adjustQuantityValidation(any(), any(), any(), any());
    verify(containerService, times(1))
        .adjustContainerItemQuantityAndGetDiff(any(), any(), any(), any(), any(), any());
    verify(containerService, times(1))
        .adjustQuantityInReceiptUseInventoryData(any(), any(), any(), any(), any(), any(), any());
    verify(finalizePORequestBodyBuilder, times(1)).buildFrom(any(), any(), any());
    verify(containerService, times(1)).postFinalizePoOsdrToGdm(any(), any(), any(), any());

    // Gls
    // ensure hits
    verify(itemConfig, times(1)).searchAtlasConvertedItems(any(Set.class), any(HttpHeaders.class));
    verify(defaultUpdateContainerQuantityRequestHandler, times(1))
        .notifyReceivingCorrectionToDcFin(any(Container.class), anyInt(), any(HttpHeaders.class));
    verify(defaultUpdateContainerQuantityRequestHandler, times(1))
        .adjustInGls(anyString(), any(HttpHeaders.class), any(Integer.class), any(Integer.class));

    // ensure no hit
    verify(receiptPublisher, times(0)).publishReceiptUpdate(any(), any(), any());
    verify(containerService, times(0))
        .adjustQuantityInInventoryService(any(), any(), any(), any(), any(), anyInt());
    verify(movePublisher, times(0)).publishCancelMove(anyString(), any());
  }

  @Test
  public void testUpdateContainerQuantityIsSuccess_case4_isOneAtlasAndConverted_positiveCorrection()
      throws ReceivingException, GDMRestApiClientException, ItemConfigRestApiClientException,
          IOException {

    final Container mockContainer = getMockContainer();
    when(containerService.getContainerByTrackingId(anyString())).thenReturn(mockContainer);
    doNothing().when(containerService).isBackoutContainer(anyString(), anyString());
    final ContainerItem ci = getContainerItem();

    when(containerService.getContainerItem(anyString(), any())).thenReturn(ci);
    Instruction instruction = MockInstruction.getInstruction();
    when(containerService.getInstruction(anyString(), any())).thenReturn(instruction);
    when(finalizePORequestBodyBuilder.buildFrom(any(), any(), any()))
        .thenReturn(new FinalizePORequestBody());

    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, PUBLISH_TO_WITRON_DISABLED, false);

    doNothing().when(dcFinRestApiClient).adjustOrVtr(any(), any());
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, IS_MANUAL_GDC_ENABLED, false);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, IS_DC_ONE_ATLAS_ENABLED, false);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, IS_GLS_API_ENABLED, false);
    doReturn(getItemConfigDetails_Converted())
        .when(itemConfig)
        .searchAtlasConvertedItems(any(Set.class), any(HttpHeaders.class));
    doReturn(true)
        .when(itemConfig)
        .isOneAtlasConvertedItem(
            anyBoolean(), any(StringBuilder.class), anyLong(), any(HttpHeaders.class));
    doReturn(CompletableFuture.completedFuture(getInventoryContainerDetails()))
        .when(asyncInventoryService)
        .getInventoryContainerDetails(anyString(), any(HttpHeaders.class));
    doReturn(CompletableFuture.completedFuture(getMoveContainerDetails()))
        .when(asyncMoveRestApiClient)
        .getMoveContainerDetails(anyString(), any(HttpHeaders.class));
    GdmDeliveryHistoryResponse gdmDeliveryHistoryResponse =
        getGdmDeliveryHistoryValidFinalisedDate();

    doReturn(CompletableFuture.completedFuture(gdmDeliveryHistoryResponse))
        .when(asyncGdmRestApiClient)
        .getDeliveryHistory(anyLong(), any(HttpHeaders.class));
    when(configUtils.getCcmValue(anyInt(), eq(INVALID_INVENTORY_STATUS_TO_ADJUST), anyString()))
        .thenReturn("ALLOCATED");
    when(configUtils.getCcmValue(anyInt(), eq(INVALID_MOVE_STATUS_CORRECTION), anyString()))
        .thenReturn("HAULWORKING");

    when(configUtils.getCcmValue(
            anyInt(), eq(ALLOWED_DAYS_FOR_CORRECTION_AFTER_FINALIZED), anyString()))
        .thenReturn("30");
    ContainerUpdateRequest containerUpdateRequest = getContainerUpdateRequest();
    containerUpdateRequest.setInventoryQuantity(5);
    // execute
    ContainerUpdateResponse containerUpdateResponse =
        gdcUpdateContainerQuantityRequestHandler.updateQuantityByTrackingId(
            "123-abc123456",
            containerUpdateRequest,
            MockHttpHeaders.getHeaders(facilityNum, countryCode));

    verify(containerService, times(1)).adjustQuantityValidation(any(), any(), any(), any());
    verify(containerService, times(1))
        .adjustContainerItemQuantityAndGetDiff(eq("1a2bc3d4"), any(), any(), any(), any(), any());
    verify(containerService, times(1))
        .adjustQuantityInReceiptUseInventoryData(any(), any(), any(), any(), any(), any(), any());
    verify(finalizePORequestBodyBuilder, times(1)).buildFrom(any(), any(), any());
    verify(containerService, times(1)).postFinalizePoOsdrToGdm(any(), any(), any(), any());

    // Gls
    // ensure hits
    verify(itemConfig, times(1)).searchAtlasConvertedItems(any(Set.class), any(HttpHeaders.class));
    verify(itemConfig, times(1)).isAtlasConvertedItem(anyLong(), any(HttpHeaders.class));
    verify(receiptPublisher, times(1)).publishReceiptUpdate(any(), any(), any());
    verify(containerService, times(1))
        .adjustQuantityInInventoryService(
            anyString(),
            anyString(),
            anyInt(),
            any(HttpHeaders.class),
            any(ContainerItem.class),
            anyInt());
    // ensure no hit
    verify(dcFinRestApiClient, times(0)).adjustOrVtr(any(), any());
    verify(glsRestApiClient, times(0)).createGlsAdjustPayload(any(), any(), any(), any(), any());
    verify(glsRestApiClient, times(0)).adjustOrCancel(any(), any());
    verify(movePublisher, times(0)).publishCancelMove(anyString(), any());
  }

  @Test
  public void testUpdateContainerQuantityIsSuccess_case5_negativeCorrection_validation_exception()
      throws ItemConfigRestApiClientException, IOException {
    try {
      final Container mockContainer = getMockContainer();
      when(containerService.getContainerByTrackingId(anyString())).thenReturn(mockContainer);
      doNothing().when(containerService).isBackoutContainer(anyString(), anyString());
      final ContainerItem ci = getContainerItem();

      when(containerService.getContainerItem(anyString(), any())).thenReturn(ci);
      Instruction instruction = MockInstruction.getInstruction();
      when(containerService.getInstruction(anyString(), any())).thenReturn(instruction);
      when(finalizePORequestBodyBuilder.buildFrom(any(), any(), any()))
          .thenReturn(new FinalizePORequestBody());

      doReturn(true)
          .when(configUtils)
          .getConfiguredFeatureFlag(facilityNum, PUBLISH_TO_WITRON_DISABLED, false);

      doNothing().when(dcFinRestApiClient).adjustOrVtr(any(), any());
      doReturn(true)
          .when(configUtils)
          .getConfiguredFeatureFlag(facilityNum, IS_MANUAL_GDC_ENABLED, false);
      doReturn(true)
          .when(configUtils)
          .getConfiguredFeatureFlag(facilityNum, IS_DC_ONE_ATLAS_ENABLED, false);
      doReturn(true)
          .when(configUtils)
          .getConfiguredFeatureFlag(facilityNum, IS_GLS_API_ENABLED, false);
      doReturn(getItemConfigDetails_Converted())
          .when(itemConfig)
          .searchAtlasConvertedItems(any(Set.class), any(HttpHeaders.class));
      doReturn(true)
          .when(itemConfig)
          .isOneAtlasConvertedItem(
              anyBoolean(), any(StringBuilder.class), anyLong(), any(HttpHeaders.class));
      doReturn(CompletableFuture.completedFuture(getinvalidInventoryContainerDetails()))
          .when(asyncInventoryService)
          .getInventoryContainerDetails(anyString(), any(HttpHeaders.class));
      doReturn(CompletableFuture.completedFuture(getMoveContainerDetails()))
          .when(asyncMoveRestApiClient)
          .getMoveContainerDetails(anyString(), any(HttpHeaders.class));
      GdmDeliveryHistoryResponse gdmDeliveryHistoryResponse =
          gson.fromJson(
              MockGdmResponse.getDeliveryHistoryReturnsBillNotSignedResponse(),
              GdmDeliveryHistoryResponse.class);
      doReturn(CompletableFuture.completedFuture(gdmDeliveryHistoryResponse))
          .when(asyncGdmRestApiClient)
          .getDeliveryHistory(anyLong(), any(HttpHeaders.class));
      when(configUtils.getCcmValue(anyInt(), eq(INVALID_INVENTORY_STATUS_TO_ADJUST), anyString()))
          .thenReturn("ALLOCATED");
      when(configUtils.getCcmValue(anyInt(), eq(INVALID_MOVE_STATUS_CORRECTION), anyString()))
          .thenReturn("HAULWORKING");

      // execute
      ContainerUpdateResponse containerUpdateResponse =
          gdcUpdateContainerQuantityRequestHandler.updateQuantityByTrackingId(
              "123-abc123456",
              getContainerUpdateRequest(),
              MockHttpHeaders.getHeaders(facilityNum, countryCode));
    } catch (ReceivingException e) {
      // Expected error due to inventory status is invalid
      Assert.assertEquals(e.getMessage(), PALLET_NOT_AVAILABLE_ERROR_MSG);
    }
  }

  @Test
  public void testUpdateContainerQuantityIsSuccess_case6_negativeCorrection_validation_exception()
      throws ItemConfigRestApiClientException, IOException {
    try {
      final Container mockContainer = getMockContainer();
      when(containerService.getContainerByTrackingId(anyString())).thenReturn(mockContainer);
      doNothing().when(containerService).isBackoutContainer(anyString(), anyString());
      final ContainerItem ci = getContainerItem();

      when(containerService.getContainerItem(anyString(), any())).thenReturn(ci);
      Instruction instruction = MockInstruction.getInstruction();
      when(containerService.getInstruction(anyString(), any())).thenReturn(instruction);
      when(finalizePORequestBodyBuilder.buildFrom(any(), any(), any()))
          .thenReturn(new FinalizePORequestBody());

      doReturn(true)
          .when(configUtils)
          .getConfiguredFeatureFlag(facilityNum, PUBLISH_TO_WITRON_DISABLED, false);

      doNothing().when(dcFinRestApiClient).adjustOrVtr(any(), any());
      doReturn(true)
          .when(configUtils)
          .getConfiguredFeatureFlag(facilityNum, IS_MANUAL_GDC_ENABLED, false);
      doReturn(true)
          .when(configUtils)
          .getConfiguredFeatureFlag(facilityNum, IS_DC_ONE_ATLAS_ENABLED, false);
      doReturn(true)
          .when(configUtils)
          .getConfiguredFeatureFlag(facilityNum, IS_GLS_API_ENABLED, false);
      doReturn(getItemConfigDetails_Converted())
          .when(itemConfig)
          .searchAtlasConvertedItems(any(Set.class), any(HttpHeaders.class));
      doReturn(true)
          .when(itemConfig)
          .isOneAtlasConvertedItem(
              anyBoolean(), any(StringBuilder.class), anyLong(), any(HttpHeaders.class));
      doReturn(CompletableFuture.completedFuture(getInventoryContainerDetails()))
          .when(asyncInventoryService)
          .getInventoryContainerDetails(anyString(), any(HttpHeaders.class));
      doReturn(CompletableFuture.completedFuture(getInvalidMoveContainerDetails()))
          .when(asyncMoveRestApiClient)
          .getMoveContainerDetails(anyString(), any(HttpHeaders.class));
      GdmDeliveryHistoryResponse gdmDeliveryHistoryResponse =
          gson.fromJson(
              MockGdmResponse.getDeliveryHistoryReturnsBillNotSignedResponse(),
              GdmDeliveryHistoryResponse.class);
      doReturn(CompletableFuture.completedFuture(gdmDeliveryHistoryResponse))
          .when(asyncGdmRestApiClient)
          .getDeliveryHistory(anyLong(), any(HttpHeaders.class));
      when(configUtils.getCcmValue(anyInt(), eq(INVALID_INVENTORY_STATUS_TO_ADJUST), anyString()))
          .thenReturn("ALLOCATED");
      when(configUtils.getCcmValue(anyInt(), eq(INVALID_MOVE_STATUS_CORRECTION), anyString()))
          .thenReturn("HAULWORKING");

      // execute
      ContainerUpdateResponse containerUpdateResponse =
          gdcUpdateContainerQuantityRequestHandler.updateQuantityByTrackingId(
              "123-abc123456",
              getContainerUpdateRequest(),
              MockHttpHeaders.getHeaders(facilityNum, countryCode));
    } catch (ReceivingException e) {
      // Expected error due to move status is invalid
      Assert.assertEquals(
          e.getMessage(),
          String.format(MOVE_INVALID_STATUS_MSG, getInvalidMoveContainerDetails().get(0)));
    }
  }

  @Test
  public void testUpdateContainerQuantityIsSuccess_case7_negativeCorrection_validation_exception()
      throws ItemConfigRestApiClientException, IOException {
    try {
      final Container mockContainer = getMockContainer();
      when(containerService.getContainerByTrackingId(anyString())).thenReturn(mockContainer);
      doNothing().when(containerService).isBackoutContainer(anyString(), anyString());
      final ContainerItem ci = getContainerItem();

      when(containerService.getContainerItem(anyString(), any())).thenReturn(ci);
      Instruction instruction = MockInstruction.getInstruction();
      when(containerService.getInstruction(anyString(), any())).thenReturn(instruction);
      when(finalizePORequestBodyBuilder.buildFrom(any(), any(), any()))
          .thenReturn(new FinalizePORequestBody());

      doReturn(true)
          .when(configUtils)
          .getConfiguredFeatureFlag(facilityNum, PUBLISH_TO_WITRON_DISABLED, false);

      doNothing().when(dcFinRestApiClient).adjustOrVtr(any(), any());
      doReturn(true)
          .when(configUtils)
          .getConfiguredFeatureFlag(facilityNum, IS_MANUAL_GDC_ENABLED, false);
      doReturn(true)
          .when(configUtils)
          .getConfiguredFeatureFlag(facilityNum, IS_DC_ONE_ATLAS_ENABLED, false);
      doReturn(true)
          .when(configUtils)
          .getConfiguredFeatureFlag(facilityNum, IS_GLS_API_ENABLED, false);
      doReturn(getItemConfigDetails_Converted())
          .when(itemConfig)
          .searchAtlasConvertedItems(any(Set.class), any(HttpHeaders.class));
      doReturn(true)
          .when(itemConfig)
          .isOneAtlasConvertedItem(
              anyBoolean(), any(StringBuilder.class), anyLong(), any(HttpHeaders.class));
      doReturn(CompletableFuture.completedFuture(getInventoryContainerDetails()))
          .when(asyncInventoryService)
          .getInventoryContainerDetails(anyString(), any(HttpHeaders.class));
      doReturn(CompletableFuture.completedFuture(getMoveContainerDetails()))
          .when(asyncMoveRestApiClient)
          .getMoveContainerDetails(anyString(), any(HttpHeaders.class));
      doReturn(CompletableFuture.completedFuture(MockGdmResponse.getGdmDeliveryHistoryBillSigned()))
          .when(asyncGdmRestApiClient)
          .getDeliveryHistory(anyLong(), any(HttpHeaders.class));
      when(configUtils.getCcmValue(anyInt(), eq(INVALID_INVENTORY_STATUS_TO_ADJUST), anyString()))
          .thenReturn("ALLOCATED");
      when(configUtils.getCcmValue(anyInt(), eq(INVALID_MOVE_STATUS_CORRECTION), anyString()))
          .thenReturn("HAULWORKING");

      // execute
      ContainerUpdateResponse containerUpdateResponse =
          gdcUpdateContainerQuantityRequestHandler.updateQuantityByTrackingId(
              "123-abc123456",
              getContainerUpdateRequest(),
              MockHttpHeaders.getHeaders(facilityNum, countryCode));
    } catch (ReceivingException e) {
      // Expected error due to move status is invalid
      Assert.assertEquals(e.getMessage(), BILL_SIGNED_ERROR_MSG);
    }
  }

  @Test
  public void testUpdateContainerQuantityIsSuccess_case8_positiveCorrection_validation_exception()
      throws ItemConfigRestApiClientException, IOException {
    try {
      final Container mockContainer = getMockContainer();
      when(containerService.getContainerByTrackingId(anyString())).thenReturn(mockContainer);
      doNothing().when(containerService).isBackoutContainer(anyString(), anyString());
      final ContainerItem ci = getContainerItem();

      when(containerService.getContainerItem(anyString(), any())).thenReturn(ci);
      Instruction instruction = MockInstruction.getInstruction();
      when(containerService.getInstruction(anyString(), any())).thenReturn(instruction);
      when(finalizePORequestBodyBuilder.buildFrom(any(), any(), any()))
          .thenReturn(new FinalizePORequestBody());

      doReturn(true)
          .when(configUtils)
          .getConfiguredFeatureFlag(facilityNum, PUBLISH_TO_WITRON_DISABLED, false);

      doNothing().when(dcFinRestApiClient).adjustOrVtr(any(), any());
      doReturn(true)
          .when(configUtils)
          .getConfiguredFeatureFlag(facilityNum, IS_MANUAL_GDC_ENABLED, false);
      doReturn(true)
          .when(configUtils)
          .getConfiguredFeatureFlag(facilityNum, IS_DC_ONE_ATLAS_ENABLED, false);
      doReturn(true)
          .when(configUtils)
          .getConfiguredFeatureFlag(facilityNum, IS_GLS_API_ENABLED, false);
      doReturn(getItemConfigDetails_Converted())
          .when(itemConfig)
          .searchAtlasConvertedItems(any(Set.class), any(HttpHeaders.class));
      doReturn(true)
          .when(itemConfig)
          .isOneAtlasConvertedItem(
              anyBoolean(), any(StringBuilder.class), anyLong(), any(HttpHeaders.class));
      doReturn(CompletableFuture.completedFuture(getInventoryContainerDetails()))
          .when(asyncInventoryService)
          .getInventoryContainerDetails(anyString(), any(HttpHeaders.class));
      doReturn(CompletableFuture.completedFuture(getMoveContainerDetails()))
          .when(asyncMoveRestApiClient)
          .getMoveContainerDetails(anyString(), any(HttpHeaders.class));
      GdmDeliveryHistoryResponse gdmDeliveryHistoryResponse =
          MockGdmResponse.getGdmDeliveryHistoryInValidFinalisedDate();

      doReturn(CompletableFuture.completedFuture(gdmDeliveryHistoryResponse))
          .when(asyncGdmRestApiClient)
          .getDeliveryHistory(anyLong(), any(HttpHeaders.class));
      when(configUtils.getCcmValue(anyInt(), eq(INVALID_INVENTORY_STATUS_TO_ADJUST), anyString()))
          .thenReturn("ALLOCATED");
      when(configUtils.getCcmValue(anyInt(), eq(INVALID_MOVE_STATUS_CORRECTION), anyString()))
          .thenReturn("HAULWORKING");

      when(configUtils.getCcmValue(
              anyInt(), eq(ALLOWED_DAYS_FOR_CORRECTION_AFTER_FINALIZED), anyString()))
          .thenReturn("30");
      ContainerUpdateRequest containerUpdateRequest = getContainerUpdateRequest();
      containerUpdateRequest.setInventoryQuantity(5);
      // execute
      ContainerUpdateResponse containerUpdateResponse =
          gdcUpdateContainerQuantityRequestHandler.updateQuantityByTrackingId(
              "123-abc123456",
              containerUpdateRequest,
              MockHttpHeaders.getHeaders(facilityNum, countryCode));
    } catch (ReceivingException e) {
      Assert.assertEquals(e.getMessage(), String.format(RECEIPT_ERROR_MSG, "30"));
    }
  }

  @Test
  public void testUpdateContainerQuantityIsSuccess_case9_positiveCorrection_validation_exception()
      throws ItemConfigRestApiClientException {
    try {
      final Container mockContainer = getMockContainer();
      when(containerService.getContainerByTrackingId(anyString())).thenReturn(mockContainer);
      doNothing().when(containerService).isBackoutContainer(anyString(), anyString());
      final ContainerItem ci = getContainerItem();

      when(containerService.getContainerItem(anyString(), any())).thenReturn(ci);
      Instruction instruction = MockInstruction.getInstruction();
      when(containerService.getInstruction(anyString(), any())).thenReturn(instruction);
      when(finalizePORequestBodyBuilder.buildFrom(any(), any(), any()))
          .thenReturn(new FinalizePORequestBody());

      doReturn(true)
          .when(configUtils)
          .getConfiguredFeatureFlag(facilityNum, PUBLISH_TO_WITRON_DISABLED, false);

      doNothing().when(dcFinRestApiClient).adjustOrVtr(any(), any());
      doReturn(true)
          .when(configUtils)
          .getConfiguredFeatureFlag(facilityNum, IS_MANUAL_GDC_ENABLED, false);
      doReturn(true)
          .when(configUtils)
          .getConfiguredFeatureFlag(facilityNum, IS_DC_ONE_ATLAS_ENABLED, false);
      doReturn(true)
          .when(configUtils)
          .getConfiguredFeatureFlag(facilityNum, IS_GLS_API_ENABLED, false);
      doReturn(getItemConfigDetails_Converted())
          .when(itemConfig)
          .searchAtlasConvertedItems(any(Set.class), any(HttpHeaders.class));
      doReturn(true)
          .when(itemConfig)
          .isOneAtlasConvertedItem(
              anyBoolean(), any(StringBuilder.class), anyLong(), any(HttpHeaders.class));
      doReturn(CompletableFuture.completedFuture(getInventoryContainerDetails()))
          .when(asyncInventoryService)
          .getInventoryContainerDetails(anyString(), any(HttpHeaders.class));
      doReturn(CompletableFuture.completedFuture(getInvalidMoveContainerDetailsAfterBillSigned()))
          .when(asyncMoveRestApiClient)
          .getMoveContainerDetails(anyString(), any(HttpHeaders.class));
      GdmDeliveryHistoryResponse gdmDeliveryHistoryResponse =
          MockGdmResponse.getGdmDeliveryHistoryValidFinalisedDateAndBillSigned();

      doReturn(CompletableFuture.completedFuture(gdmDeliveryHistoryResponse))
          .when(asyncGdmRestApiClient)
          .getDeliveryHistory(anyLong(), any(HttpHeaders.class));
      when(configUtils.getCcmValue(anyInt(), eq(INVALID_INVENTORY_STATUS_TO_ADJUST), anyString()))
          .thenReturn("ALLOCATED");
      when(configUtils.getCcmValue(anyInt(), eq(INVALID_MOVE_STATUS_CORRECTION), anyString()))
          .thenReturn("HAULWORKING");

      when(configUtils.getCcmValue(
              anyInt(), eq(ALLOWED_DAYS_FOR_CORRECTION_AFTER_FINALIZED), anyString()))
          .thenReturn("30");
      when(configUtils.getCcmValue(anyInt(), eq(INVALID_MOVE_IF_BILL_NOT_SIGNED), anyString()))
          .thenReturn("PUTAWAYCOMPLETED");
      ContainerUpdateRequest containerUpdateRequest = getContainerUpdateRequest();
      containerUpdateRequest.setInventoryQuantity(5);
      // execute
      ContainerUpdateResponse containerUpdateResponse =
          gdcUpdateContainerQuantityRequestHandler.updateQuantityByTrackingId(
              "123-abc123456",
              containerUpdateRequest,
              MockHttpHeaders.getHeaders(facilityNum, countryCode));
    } catch (ReceivingException e) {
      Assert.assertEquals(e.getMessage(), NEGATIVE_RC_CANNOT_BE_DONE_PUTAWAY_COMPLETE);
    }
  }

  @Test
  public void testUpdateContainerQuantityIsSuccess_case10_negativeCorrection_validation_exception()
      throws ItemConfigRestApiClientException, IOException {
    try {
      final Container mockContainer = getMockContainer();
      when(containerService.getContainerByTrackingId(anyString())).thenReturn(mockContainer);
      doNothing().when(containerService).isBackoutContainer(anyString(), anyString());
      final ContainerItem ci = getContainerItem();

      when(containerService.getContainerItem(anyString(), any())).thenReturn(ci);
      Instruction instruction = MockInstruction.getInstruction();
      when(containerService.getInstruction(anyString(), any())).thenReturn(instruction);
      when(finalizePORequestBodyBuilder.buildFrom(any(), any(), any()))
          .thenReturn(new FinalizePORequestBody());

      doReturn(true)
          .when(configUtils)
          .getConfiguredFeatureFlag(facilityNum, PUBLISH_TO_WITRON_DISABLED, false);

      doNothing().when(dcFinRestApiClient).adjustOrVtr(any(), any());
      doReturn(true)
          .when(configUtils)
          .getConfiguredFeatureFlag(facilityNum, IS_MANUAL_GDC_ENABLED, false);
      doReturn(true)
          .when(configUtils)
          .getConfiguredFeatureFlag(facilityNum, IS_DC_ONE_ATLAS_ENABLED, false);
      doReturn(true)
          .when(configUtils)
          .getConfiguredFeatureFlag(facilityNum, IS_GLS_API_ENABLED, false);
      doReturn(getItemConfigDetails_Converted())
          .when(itemConfig)
          .searchAtlasConvertedItems(any(Set.class), any(HttpHeaders.class));
      doReturn(true)
          .when(itemConfig)
          .isOneAtlasConvertedItem(
              anyBoolean(), any(StringBuilder.class), anyLong(), any(HttpHeaders.class));
      doReturn(CompletableFuture.completedFuture(getinvalidAllocatedQtyInventoryContainerDetails()))
          .when(asyncInventoryService)
          .getInventoryContainerDetails(anyString(), any(HttpHeaders.class));
      doReturn(CompletableFuture.completedFuture(getMoveContainerDetails()))
          .when(asyncMoveRestApiClient)
          .getMoveContainerDetails(anyString(), any(HttpHeaders.class));
      GdmDeliveryHistoryResponse gdmDeliveryHistoryResponse =
          gson.fromJson(
              MockGdmResponse.getDeliveryHistoryReturnsBillNotSignedResponse(),
              GdmDeliveryHistoryResponse.class);
      doReturn(CompletableFuture.completedFuture(gdmDeliveryHistoryResponse))
          .when(asyncGdmRestApiClient)
          .getDeliveryHistory(anyLong(), any(HttpHeaders.class));
      when(configUtils.getCcmValue(anyInt(), eq(INVALID_INVENTORY_STATUS_TO_ADJUST), anyString()))
          .thenReturn("ALLOCATED");
      when(configUtils.getCcmValue(anyInt(), eq(INVALID_MOVE_STATUS_CORRECTION), anyString()))
          .thenReturn("HAULWORKING");

      // execute
      ContainerUpdateResponse containerUpdateResponse =
          gdcUpdateContainerQuantityRequestHandler.updateQuantityByTrackingId(
              "123-abc123456",
              getContainerUpdateRequest(),
              MockHttpHeaders.getHeaders(facilityNum, countryCode));
    } catch (ReceivingException e) {
      // Expected error due to inventory status is invalid
      Assert.assertEquals(e.getMessage(), PALLET_NOT_AVAILABLE_ERROR_MSG);
    }
  }

  @Test
  public void testUpdateContainerQuantityIsSuccess_case11_negativeCorrection_err5()
      throws ItemConfigRestApiClientException, IOException {
    try {
      final Container mockContainer = getMockContainer();
      when(containerService.getContainerByTrackingId(anyString())).thenReturn(mockContainer);
      doNothing().when(containerService).isBackoutContainer(anyString(), anyString());
      final ContainerItem ci = getContainerItem();

      when(containerService.getContainerItem(anyString(), any())).thenReturn(ci);
      Instruction instruction = MockInstruction.getInstruction();
      when(containerService.getInstruction(anyString(), any())).thenReturn(instruction);
      when(finalizePORequestBodyBuilder.buildFrom(any(), any(), any()))
          .thenReturn(new FinalizePORequestBody());

      doReturn(true)
          .when(configUtils)
          .getConfiguredFeatureFlag(facilityNum, PUBLISH_TO_WITRON_DISABLED, false);

      doNothing().when(dcFinRestApiClient).adjustOrVtr(any(), any());
      doReturn(true)
          .when(configUtils)
          .getConfiguredFeatureFlag(facilityNum, IS_MANUAL_GDC_ENABLED, false);
      doReturn(true)
          .when(configUtils)
          .getConfiguredFeatureFlag(facilityNum, IS_DC_ONE_ATLAS_ENABLED, false);
      doReturn(true)
          .when(configUtils)
          .getConfiguredFeatureFlag(facilityNum, IS_GLS_API_ENABLED, false);
      doReturn(getItemConfigDetails_Converted())
          .when(itemConfig)
          .searchAtlasConvertedItems(any(Set.class), any(HttpHeaders.class));
      doReturn(true)
          .when(itemConfig)
          .isOneAtlasConvertedItem(
              anyBoolean(), any(StringBuilder.class), anyLong(), any(HttpHeaders.class));
      doReturn(CompletableFuture.completedFuture(getInventoryContainerDetails()))
          .when(asyncInventoryService)
          .getInventoryContainerDetails(anyString(), any(HttpHeaders.class));
      List<String> moveContainerDetailList = new ArrayList<>();
      moveContainerDetailList.add("HAULWORKING");
      moveContainerDetailList.add("PUTAWAYOPEN");
      doReturn(CompletableFuture.completedFuture(moveContainerDetailList))
          .when(asyncMoveRestApiClient)
          .getMoveContainerDetails(anyString(), any(HttpHeaders.class));
      GdmDeliveryHistoryResponse gdmDeliveryHistoryResponse =
          gson.fromJson(
              MockGdmResponse.getDeliveryHistoryReturnsBillNotSignedResponse(),
              GdmDeliveryHistoryResponse.class);
      doReturn(CompletableFuture.completedFuture(gdmDeliveryHistoryResponse))
          .when(asyncGdmRestApiClient)
          .getDeliveryHistory(anyLong(), any(HttpHeaders.class));
      when(configUtils.getCcmValue(anyInt(), eq(INVALID_INVENTORY_STATUS_TO_ADJUST), anyString()))
          .thenReturn("ALLOCATED,PICKED,LOADED");
      when(configUtils.getCcmValue(anyInt(), eq(INVALID_MOVE_STATUS_CORRECTION), anyString()))
          .thenReturn("WORKING");

      // execute
      gdcUpdateContainerQuantityRequestHandler.updateQuantityByTrackingId(
          "123-abc123456",
          getContainerUpdateRequest(),
          MockHttpHeaders.getHeaders(facilityNum, countryCode));
      fail(
          "supposed to throw exception as Receiving correction can not be performed while a move is in-progress");
    } catch (ReceivingException e) {
      // Expected error due to inventory status is invalid
      Assert.assertEquals(
          e.getMessage(), "Receiving correction can not be performed while a move is in-progress");
    }
  }

  private ContainerUpdateRequest getContainerUpdateRequest() {
    ContainerUpdateRequest containerUpdateRequest = new ContainerUpdateRequest();
    containerUpdateRequest.setAdjustQuantity(10);
    containerUpdateRequest.setInventoryQuantity(20);
    containerUpdateRequest.setAdjustQuantityUOM(VNPK);
    return containerUpdateRequest;
  }

  private Container getMockContainer() {
    return defaultContainer;
  }

  private ContainerItem getContainerItem() {
    return defaultContainer.getContainerItems().get(0);
  }

  private InventoryContainerDetails getInventoryContainerDetails() {
    InventoryContainerDetails inventoryContainerDetails = new InventoryContainerDetails();
    inventoryContainerDetails.setContainerStatus("AVAILABLE");
    inventoryContainerDetails.setInventoryQty(5);
    inventoryContainerDetails.setAllocatedQty(0);
    return inventoryContainerDetails;
  }

  private InventoryContainerDetails getinvalidInventoryContainerDetails() {
    InventoryContainerDetails inventoryContainerDetails = new InventoryContainerDetails();
    inventoryContainerDetails.setContainerStatus("ALLOCATED");
    inventoryContainerDetails.setInventoryQty(5);
    inventoryContainerDetails.setAllocatedQty(0);
    return inventoryContainerDetails;
  }

  private InventoryContainerDetails getinvalidAllocatedQtyInventoryContainerDetails() {
    InventoryContainerDetails inventoryContainerDetails = new InventoryContainerDetails();
    inventoryContainerDetails.setContainerStatus("AVAILABLE");
    inventoryContainerDetails.setInventoryQty(5);
    inventoryContainerDetails.setAllocatedQty(2);
    return inventoryContainerDetails;
  }

  private List<String> getMoveContainerDetails() {
    List<String> moveContainerDetailList = new ArrayList<>();
    moveContainerDetailList.add("HAULCOMPLETED");
    return moveContainerDetailList;
  }

  private List<String> getInvalidMoveContainerDetails() {
    List<String> moveContainerDetailList = new ArrayList<>();
    moveContainerDetailList.add("HAULPUTWORKING");
    return moveContainerDetailList;
  }

  private List<String> getInvalidMoveContainerDetailsAfterBillSigned() {
    List<String> moveContainerDetailList = new ArrayList<>();
    moveContainerDetailList.add("PUTAWAYCOMPLETED");

    return moveContainerDetailList;
  }

  @Test
  public void testValidateMechRestrictions_nulls() throws ReceivingException {
    gdcUpdateContainerQuantityRequestHandler.validateMechRestrictions(null, null);
  }

  @Test
  public void testValidateMechRestrictions_success() throws ReceivingException {
    Map<String, Object> containerMiscInfo = new HashMap<>();
    containerMiscInfo.put(IS_MECH_CONTAINER, true);
    InventoryContainerDetails inventoryContainerDetails = new InventoryContainerDetails();
    inventoryContainerDetails.setLocationName("glblstor");
    LocationInfo locationInfo = new LocationInfo();
    locationInfo.setAutomationType(AUTOMATION_TYPE_DEMATIC);
    locationInfo.setIsPrimeSlot(false); // fails the condition
    doReturn(locationInfo).when(locationService).getLocationInfo(anyString());
    gdcUpdateContainerQuantityRequestHandler.validateMechRestrictions(
        containerMiscInfo, inventoryContainerDetails.getLocationName());
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "Pallet has been inducted into Mechanization, Cancel Pallet and Receiving Correction not allowed.")
  public void testValidateMechRestrictions_error() throws ReceivingException {
    Map<String, Object> containerMiscInfo = new HashMap<>();
    containerMiscInfo.put(IS_MECH_CONTAINER, true);
    InventoryContainerDetails inventoryContainerDetails = new InventoryContainerDetails();
    inventoryContainerDetails.setLocationName("glblstor");
    LocationInfo locationInfo = new LocationInfo();
    locationInfo.setAutomationType(AUTOMATION_TYPE_DEMATIC);
    locationInfo.setIsPrimeSlot(true);
    doReturn(locationInfo).when(locationService).getLocationInfo(anyString());
    gdcUpdateContainerQuantityRequestHandler.validateMechRestrictions(
        containerMiscInfo, inventoryContainerDetails.getLocationName());
  }
}
