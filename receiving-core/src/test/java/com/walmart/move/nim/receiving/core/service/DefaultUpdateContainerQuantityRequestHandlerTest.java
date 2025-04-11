package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_DC_ONE_ATLAS_ENABLED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_GLS_API_ENABLED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_MANUAL_GDC_ENABLED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.PUBLISH_CANCEL_MOVE_ENABLED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.PUBLISH_TO_WITRON_DISABLED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.VNPK;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

import com.walmart.move.nim.receiving.core.builder.FinalizePORequestBodyBuilder;
import com.walmart.move.nim.receiving.core.client.dcfin.DCFinRestApiClient;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClientException;
import com.walmart.move.nim.receiving.core.client.gls.GlsRestApiClient;
import com.walmart.move.nim.receiving.core.client.itemconfig.ItemConfigApiClient;
import com.walmart.move.nim.receiving.core.client.itemconfig.ItemConfigRestApiClientException;
import com.walmart.move.nim.receiving.core.client.itemconfig.model.ItemConfigDetails;
import com.walmart.move.nim.receiving.core.common.MovePublisher;
import com.walmart.move.nim.receiving.core.common.ReceiptPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.mock.data.MockContainer;
import com.walmart.move.nim.receiving.core.mock.data.MockInstruction;
import com.walmart.move.nim.receiving.core.model.ContainerUpdateRequest;
import com.walmart.move.nim.receiving.core.model.ContainerUpdateResponse;
import com.walmart.move.nim.receiving.core.model.gdm.v3.FinalizePORequestBody;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class DefaultUpdateContainerQuantityRequestHandlerTest {
  @Mock private ContainerService containerService;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private FinalizePORequestBodyBuilder finalizePORequestBodyBuilder;
  @Mock private GlsRestApiClient glsRestApiClient;
  @Mock private ReceiptPublisher receiptPublisher;
  @Mock private DCFinRestApiClient dcFinRestApiClient;
  @Mock private MovePublisher movePublisher;
  @Mock private ReceiptService receiptService;
  @Spy private ItemConfigApiClient itemConfigApiClient;

  @InjectMocks
  private DefaultUpdateContainerQuantityRequestHandler defaultUpdateContainerQuantityRequestHandler;

  private final String trackingId = "lpn1";
  private static final String facilityNum = "6071";
  private static final String countryCode = "US";
  final Container defaultContainer = MockContainer.getContainer();

  @BeforeMethod
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode(countryCode);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    ReflectionTestUtils.setField(itemConfigApiClient, "configUtils", configUtils);
  }

  @AfterMethod
  public void resetMocks() {
    reset(containerService, configUtils, finalizePORequestBodyBuilder, receiptPublisher);
    reset(dcFinRestApiClient);
    reset(itemConfigApiClient);
  }

  /**
   * contract
   *
   * <pre>
   * VTR/Correction
   * # Prod (8852) > BAU we send to Inventory and inventory send to DcFin
   * # Full GLS(6097) >
   * ## BAU we send to GLS and GLS sends to DcFin
   * ## RCV don't send to Inventory -
   * # OneAtlas
   * ## ItemConverted
   * ### expected to work like BAU so RCV to send to Inventory, Inventory Send to DcFin
   * ##  ItemNotConverted
   * ### NEW Change, RCV send to GLS
   * ### NEW Change, RCV send to DcFin
   * ### NEW Change, RCV will NOT send to Inventory
   * </pre>
   *
   * @throws ReceivingException
   * @throws GDMRestApiClientException
   */
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

    // execute
    ContainerUpdateResponse containerUpdateResponse =
        defaultUpdateContainerQuantityRequestHandler.updateQuantityByTrackingId(
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
  }
  /**
   * Manual Dc, oneAtlas and item Converted: should send SCT, post to Inventory (Inv will send to
   * DcFin). Should NOT send to GLS and DcFin
   *
   * <pre>
   * # OneAtlas
   * ## ItemConverted
   * ### expected to work like BAU so RCV to send to Inventory, Inventory Send to DcFin
   *  </pre>
   *
   * @throws ReceivingException
   * @throws GDMRestApiClientException
   */
  @Test
  public void testUpdateContainerQuantityIsSuccess_case4_isOneAtlasAndConverted()
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
    doReturn(getItemConfigDetails_Converted())
        .when(itemConfigApiClient)
        .searchAtlasConvertedItems(any(Set.class), any(HttpHeaders.class));
    // end of GLS flags

    // execute
    ContainerUpdateResponse containerUpdateResponse =
        defaultUpdateContainerQuantityRequestHandler.updateQuantityByTrackingId(
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
    verify(itemConfigApiClient, times(1))
        .searchAtlasConvertedItems(any(Set.class), any(HttpHeaders.class));
    verify(itemConfigApiClient, times(1)).isAtlasConvertedItem(anyLong(), any(HttpHeaders.class));
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

  public static List<ItemConfigDetails> getItemConfigDetails_Converted() {
    final List<ItemConfigDetails> itemConfigList =
        Collections.singletonList(
            ItemConfigDetails.builder()
                .createdDateTime(null)
                .desc("desc")
                .item("556565795")
                .build());
    return itemConfigList;
  }

  @Test
  public void testUpdateContainerQuantityIsSuccess_case4_isOneAtlasAndConverted_VTR()
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

    // GLS
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
        .when(itemConfigApiClient)
        .searchAtlasConvertedItems(any(Set.class), any(HttpHeaders.class));
    // end of GLS flags

    final ContainerUpdateRequest receivingCorrectionRequestToVTR = getContainerUpdateRequest();
    receivingCorrectionRequestToVTR.setAdjustQuantity(0);

    // execute
    ContainerUpdateResponse containerUpdateResponse =
        defaultUpdateContainerQuantityRequestHandler.updateQuantityByTrackingId(
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
    verify(itemConfigApiClient, times(1))
        .searchAtlasConvertedItems(any(Set.class), any(HttpHeaders.class));
    verify(itemConfigApiClient, times(1)).isAtlasConvertedItem(anyLong(), any(HttpHeaders.class));
    verify(receiptPublisher, times(1)).publishReceiptUpdate(any(), any(), any());
    verify(containerService, times(1))
        .adjustQuantityInInventoryService(
            anyString(),
            anyString(),
            anyInt(),
            any(HttpHeaders.class),
            any(ContainerItem.class),
            anyInt());
    verify(movePublisher, times(1)).publishCancelMove(anyString(), any());

    // ensure no hit
    verify(dcFinRestApiClient, times(0)).adjustOrVtr(any(), any());
    verify(glsRestApiClient, times(0)).createGlsAdjustPayload(any(), any(), any(), any(), any());
    verify(glsRestApiClient, times(0)).adjustOrCancel(any(), any());
  }

  @Test
  public void testUpdateContainerQuantityIsSuccess_case4_CancelMoveFlagEnabled_VTR()
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
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, PUBLISH_CANCEL_MOVE_ENABLED, false);

    // GLS
    doNothing().when(dcFinRestApiClient).adjustOrVtr(any(), any());
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, IS_MANUAL_GDC_ENABLED, false);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, IS_DC_ONE_ATLAS_ENABLED, false);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, IS_GLS_API_ENABLED, false);
    doReturn(getItemConfigDetails_Converted())
        .when(itemConfigApiClient)
        .searchAtlasConvertedItems(any(Set.class), any(HttpHeaders.class));
    // end of GLS flags

    final ContainerUpdateRequest receivingCorrectionRequestToVTR = getContainerUpdateRequest();
    receivingCorrectionRequestToVTR.setAdjustQuantity(0);

    // execute
    ContainerUpdateResponse containerUpdateResponse =
        defaultUpdateContainerQuantityRequestHandler.updateQuantityByTrackingId(
            "abc123456",
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
    verify(itemConfigApiClient, times(1))
        .searchAtlasConvertedItems(any(Set.class), any(HttpHeaders.class));
    verify(itemConfigApiClient, times(1)).isAtlasConvertedItem(anyLong(), any(HttpHeaders.class));
    verify(receiptPublisher, times(1)).publishReceiptUpdate(any(), any(), any());
    verify(containerService, times(1))
        .adjustQuantityInInventoryService(
            anyString(),
            anyString(),
            anyInt(),
            any(HttpHeaders.class),
            any(ContainerItem.class),
            anyInt());
    verify(movePublisher, times(1)).publishCancelMove(anyString(), any());

    // ensure no hit
    verify(dcFinRestApiClient, times(0)).adjustOrVtr(any(), any());
    verify(glsRestApiClient, times(0)).createGlsAdjustPayload(any(), any(), any(), any(), any());
    verify(glsRestApiClient, times(0)).adjustOrCancel(any(), any());
  }

  @Test
  public void testUpdateContainerQuantityIsSuccess_case2_fullGls()
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

    // GLS flags
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, PUBLISH_TO_WITRON_DISABLED, false);
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, IS_DC_ONE_ATLAS_ENABLED, false);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, IS_MANUAL_GDC_ENABLED, false);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(facilityNum, IS_GLS_API_ENABLED, false);
    // end of GLS flags

    // execute
    ContainerUpdateResponse containerUpdateResponse =
        defaultUpdateContainerQuantityRequestHandler.updateQuantityByTrackingId(
            "123-abc123456",
            getContainerUpdateRequest(),
            MockHttpHeaders.getHeaders(facilityNum, countryCode));

    // verify
    assertNotNull(containerUpdateResponse);
    assertNull(containerUpdateResponse.getPrintJob());

    verify(containerService, times(1)).getContainerByTrackingId(anyString());
    verify(containerService, times(1)).isBackoutContainer(anyString(), anyString());
    verify(containerService, times(1)).adjustQuantityValidation(any(), any(), any(), any());
    verify(containerService, times(1))
        .adjustContainerItemQuantityAndGetDiff(any(), any(), any(), any(), any(), any());
    verify(containerService, times(1))
        .adjustQuantityInReceiptUseInventoryData(any(), any(), any(), any(), any(), any(), any());
    verify(finalizePORequestBodyBuilder, times(1)).buildFrom(any(), any(), any());
    verify(containerService, times(0))
        .adjustQuantityInInventoryService(any(), any(), any(), any(), any(), anyInt());
    verify(containerService, times(1)).postFinalizePoOsdrToGdm(any(), any(), any(), any());

    // Gls
    verify(receiptPublisher, times(0)).publishReceiptUpdate(any(), any(), any());

    verify(glsRestApiClient, times(1)).createGlsAdjustPayload(any(), any(), any(), any(), any());
    verify(glsRestApiClient, times(1)).adjustOrCancel(any(), any());
  }

  /**
   *
   *
   * <pre>
   * # OneAtlas
   * ##  ItemNotConverted
   * ### NEW Change, RCV send to GLS
   * ### NEW Change, RCV send to DcFin
   * ### NEW Change, RCV will NOT send to Inventory
   *
   *
   * curl --location --request POST 'https://dcfinancials.prod.us.walmart.net/v2/adjustment' \
   * --header 'facilityCountryCode: US' \
   * --header 'facilityNum: 8852' \
   * --header 'WMT-API-KEY: 25eacafa-e50c-4172-b7e1-dbc389698809' \
   * --header 'WMT-UserId: t0a057r' \
   * --header 'Content-Type: application/json' \
   * --header 'WMT-correlationId: b05e0763-eaab-42f0-94f8-293d8a0e3025' \
   * --data-raw '{
   * "transactions": [
   * {
   * "itemNumber": 556654314,
   * "promoBuyInd": "N",
   * "secondaryQtyUOM": "LB/ZA",
   * "warehousePackQty": 4,
   * "documentType": "PO",
   * "baseDivCode": "WM",
   * "primaryQtyUOM": "EA",
   * "inboundChannelMethod": "Staplestock",
   * "vendorPackQty": 4,
   * "dateAdjusted": "2023-02-04T15:34:28.604+0000",
   * "secondaryQty": 26.81,
   * "weightFormatType": "F",
   * "quantityToTransfer": 0,
   * "deliveryNum": "39013923",
   * "reasonCodeDesc": "Receiving Correction",
   * "primaryQty": 192,
   * "documentNum": "0731250409",
   * "reasonCode": "52",
   * "containerId": "R08852000020047824",
   * "currencyCode": "USD",
   * "documentLineNo": 8,
   * "financialReportGrpCode": "US"
   * }
   * ],
   * "txnId": "891476e2-926b-4ba9-8911-2e0880ca7b5e"
   * }'
   * </pre>
   *
   * @throws ReceivingException
   * @throws GDMRestApiClientException
   */
  @Test
  public void testUpdateContainerQuantityIsSuccess_case3_isOneAtlasAndNotConverted()
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
        .when(itemConfigApiClient)
        .searchAtlasConvertedItems(any(Set.class), any(HttpHeaders.class));
    // end of GLS flags

    // execute
    ContainerUpdateResponse containerUpdateResponse =
        defaultUpdateContainerQuantityRequestHandler.updateQuantityByTrackingId(
            "123-abc123456",
            getContainerUpdateRequest(),
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
    verify(itemConfigApiClient, times(1))
        .searchAtlasConvertedItems(any(Set.class), any(HttpHeaders.class));
    verify(dcFinRestApiClient, times(1)).adjustOrVtr(any(), any());
    verify(glsRestApiClient, times(1)).createGlsAdjustPayload(any(), any(), any(), any(), any());
    verify(glsRestApiClient, times(1)).adjustOrCancel(any(), any());

    // ensure no hit
    verify(receiptPublisher, times(0)).publishReceiptUpdate(any(), any(), any());
    verify(containerService, times(0))
        .adjustQuantityInInventoryService(any(), any(), any(), any(), any(), anyInt());
    verify(movePublisher, times(0)).publishCancelMove(anyString(), any());
  }
  /** similar to VTR, isOneAtlasAndNotConverted should send cancel MOVE for zero new quantity */
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
        .when(itemConfigApiClient)
        .searchAtlasConvertedItems(any(Set.class), any(HttpHeaders.class));
    // end of GLS flags

    // execute
    final ContainerUpdateRequest receivingCorrectionRequestToVTR = getContainerUpdateRequest();
    receivingCorrectionRequestToVTR.setAdjustQuantity(0);
    ContainerUpdateResponse containerUpdateResponse =
        defaultUpdateContainerQuantityRequestHandler.updateQuantityByTrackingId(
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
    verify(itemConfigApiClient, times(1))
        .searchAtlasConvertedItems(any(Set.class), any(HttpHeaders.class));
    verify(dcFinRestApiClient, times(1)).adjustOrVtr(any(), any());
    verify(glsRestApiClient, times(1)).createGlsAdjustPayload(any(), any(), any(), any(), any());
    verify(glsRestApiClient, times(1)).adjustOrCancel(any(), any());

    // ensure no hit
    verify(receiptPublisher, times(0)).publishReceiptUpdate(any(), any(), any());
    verify(containerService, times(0))
        .adjustQuantityInInventoryService(any(), any(), any(), any(), any(), anyInt());
    verify(movePublisher, times(0)).publishCancelMove(anyString(), any());
  }

  @Test
  public void testUpdateContainerQuantityIsSuccess_for_GLS_flags_invalidTrackingId()
      throws ReceivingException {

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

      // GLS flags
      doReturn(true)
          .when(configUtils)
          .getConfiguredFeatureFlag(facilityNum, IS_MANUAL_GDC_ENABLED, false);
      // end of GLS flags

      // execute
      defaultUpdateContainerQuantityRequestHandler.updateQuantityByTrackingId(
          trackingId,
          getContainerUpdateRequest(),
          MockHttpHeaders.getHeaders(facilityNum, countryCode));

    } catch (ReceivingBadDataException e) {
      // removed GLS specific validation if ManualDc enabled so no exception to be thrown
      fail();
    }
  }

  @Test
  public void testUpdateQuantityByTrackingId() {}

  private Container getMockContainer() {
    return defaultContainer;
  }

  private ContainerItem getContainerItem() {
    return defaultContainer.getContainerItems().get(0);
  }

  private ContainerUpdateRequest getContainerUpdateRequest() {
    ContainerUpdateRequest containerUpdateRequest = new ContainerUpdateRequest();
    containerUpdateRequest.setAdjustQuantity(10);
    containerUpdateRequest.setInventoryQuantity(20);
    containerUpdateRequest.setAdjustQuantityUOM(VNPK);
    return containerUpdateRequest;
  }
}
