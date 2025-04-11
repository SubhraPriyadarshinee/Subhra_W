package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.VTR_ERROR_CODE;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.VTR_ERROR_MSG;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.FLOW_NAME;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.INV_V2_ENABLED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_DC_ONE_ATLAS_ENABLED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_MANUAL_GDC_ENABLED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.VTR_FLOW;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.client.dcfin.DCFinRestApiClient;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClientException;
import com.walmart.move.nim.receiving.core.client.gls.GlsRestApiClient;
import com.walmart.move.nim.receiving.core.client.itemconfig.ItemConfigApiClient;
import com.walmart.move.nim.receiving.core.client.itemconfig.ItemConfigRestApiClientException;
import com.walmart.move.nim.receiving.core.client.itemconfig.model.ItemConfigDetails;
import com.walmart.move.nim.receiving.core.common.MovePublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.RestUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.rest.SimpleRestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.helper.ReceiptHelper;
import com.walmart.move.nim.receiving.core.mock.data.WitronContainer;
import com.walmart.move.nim.receiving.core.model.CancelContainerRequest;
import com.walmart.move.nim.receiving.core.model.CancelContainerResponse;
import com.walmart.move.nim.receiving.core.repositories.ContainerItemRepository;
import com.walmart.move.nim.receiving.core.repositories.ContainerRepository;
import com.walmart.move.nim.receiving.core.repositories.ReceiptRepository;
import com.walmart.move.nim.receiving.core.retry.RetryableRestConnector;
import com.walmart.move.nim.receiving.data.GdcHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class CancelContainerServiceTest extends ReceivingTestBase {

  @InjectMocks private DefaultCancelContainerProcessor cancelContainerProcessor;
  @InjectMocks private ContainerPersisterService containerPersisterService;
  @InjectMocks private ContainerAdjustmentValidator containerAdjustmentValidator;
  @Mock private ContainerAdjustmentHelper containerAdjustmentHelper;
  @Mock private RestUtils restUtils;
  @Mock private AppConfig appConfig;
  @Spy private ContainerRepository containerRepository;

  @Spy private ReceiptRepository receiptRepository;
  @Spy private ContainerItemRepository containerItemRepository;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private ReceiptService receiptService;
  @Mock private GlsRestApiClient glsRestApiClient;
  @Mock private DCFinRestApiClient dcFinRestApiClient;
  @Mock private ReceiptHelper receiptHelper;
  @Mock private MovePublisher movePublisher;
  @Spy private ItemConfigApiClient itemConfigApiClient;
  @Mock private RetryableRestConnector retryableRestConnector;
  @Mock private SimpleRestConnector simpleRestConnector;

  private HttpHeaders httpHeaders = GdcHttpHeaders.getHeaders();
  private CancelContainerRequest cancelContainerRequest = new CancelContainerRequest();
  private String deliveryNumber = "121212121";
  private String trackingId = "027734368100444931";
  final String invCoreBaseUrl = "https://gls-atlas-inventory-core-gdc-stg.walmart.com";
  private static final String itemNumber = "5689452";
  private static final String itemDesc = "test desc";
  private static final String createTs = "2021-08-11T03:48:27.133Z";

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(
        cancelContainerProcessor, "containerPersisterService", containerPersisterService);
    final Gson gson = new Gson();
    ReflectionTestUtils.setField(cancelContainerProcessor, "gson", gson);
    ReflectionTestUtils.setField(
        cancelContainerProcessor, "containerAdjustmentValidator", containerAdjustmentValidator);
    ReflectionTestUtils.setField(itemConfigApiClient, "appConfig", appConfig);
    ReflectionTestUtils.setField(itemConfigApiClient, "gson", gson);
    ReflectionTestUtils.setField(itemConfigApiClient, "configUtils", configUtils);
    ReflectionTestUtils.setField(
        itemConfigApiClient, "retryableRestConnector", retryableRestConnector);
    ReflectionTestUtils.setField(itemConfigApiClient, "simpleRestConnector", simpleRestConnector);

    List<String> trackingIds = new ArrayList<String>();
    trackingIds.add(trackingId);
    cancelContainerRequest.setTrackingIds(trackingIds);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32612);
  }

  @AfterMethod
  public void tearDown() {
    reset(configUtils);
    reset(restUtils);
    reset(appConfig);
    reset(containerRepository);
    reset(containerItemRepository);
    reset(receiptRepository);
    reset(glsRestApiClient);
    reset(containerAdjustmentHelper);
    reset(dcFinRestApiClient);
    reset(movePublisher);
    reset(itemConfigApiClient);
  }

  @Test
  public void testCancelContainers_success() throws ReceivingException {
    when(containerRepository.findByTrackingId(trackingId))
        .thenReturn(WitronContainer.getContainer1());
    when(containerRepository.findAllByParentTrackingId(trackingId))
        .thenReturn(new HashSet<Container>());
    when(containerItemRepository.findByTrackingId(trackingId))
        .thenReturn(WitronContainer.getContainer1().getContainerItems());
    when(configUtils.isPoConfirmationFlagEnabled(any(Integer.class))).thenReturn(false);

    doReturn(new ResponseEntity<String>("{}", HttpStatus.OK))
        .when(restUtils)
        .post(any(), any(), any(), any());

    List<CancelContainerResponse> responseList =
        cancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    verify(restUtils, times(1)).post(any(), any(), any(), any());
    assertEquals(responseList.size(), 0);
  }

  @Test
  public void testCancelContainers_containerNotFound() throws ReceivingException {
    when(containerRepository.findByTrackingId(trackingId)).thenReturn(null);
    when(containerRepository.findAllByParentTrackingId(trackingId)).thenReturn(null);
    when(containerItemRepository.findByTrackingId(trackingId)).thenReturn(null);

    List<CancelContainerResponse> responseList =
        cancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertEquals(
        responseList.get(0).getErrorCode(), ReceivingException.CONTAINER_NOT_FOUND_ERROR_CODE);
    assertEquals(
        responseList.get(0).getErrorMessage(), ReceivingException.CONTAINER_NOT_FOUND_ERROR_MSG);
    assertEquals(responseList.get(0).getTrackingId(), trackingId);
  }

  @Test
  public void testCancelContainers_containerAlreadyCanceled() throws ReceivingException {
    Container container = WitronContainer.getContainer1();
    container.setContainerStatus(ReceivingConstants.STATUS_BACKOUT);
    when(containerRepository.findByTrackingId(trackingId)).thenReturn(container);
    when(containerRepository.findAllByParentTrackingId(trackingId)).thenReturn(null);
    when(containerItemRepository.findByTrackingId(trackingId)).thenReturn(null);

    List<CancelContainerResponse> responseList =
        cancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertEquals(
        responseList.get(0).getErrorCode(),
        ReceivingException.CONTAINER_ALREADY_CANCELED_ERROR_CODE);
    assertEquals(
        responseList.get(0).getErrorMessage(),
        ReceivingException.CONTAINER_ALREADY_CANCELED_ERROR_MSG);
    assertEquals(responseList.get(0).getTrackingId(), trackingId);
  }

  @Test
  public void testCancelContainers_deliveryFinalized() throws ReceivingException {
    Container container = WitronContainer.getContainer1();
    when(containerRepository.findByTrackingId(trackingId))
        .thenReturn(WitronContainer.getContainer1());
    when(containerRepository.findAllByParentTrackingId(trackingId))
        .thenReturn(new HashSet<Container>());
    when(containerItemRepository.findByTrackingId(trackingId))
        .thenReturn(WitronContainer.getContainer1().getContainerItems());
    when(containerAdjustmentHelper.validateDeliveryStatusForLabelAdjustment(
            anyString(), anyLong(), any(HttpHeaders.class)))
        .thenReturn(
            new CancelContainerResponse(
                trackingId,
                ExceptionCodes.LABEL_CORRECTION_ERROR_FOR_FINALIZED_DELIVERY,
                ReceivingException.LABEL_QUANTITY_ADJUSTMENT_ERROR_MSG_FOR_FINALIZED_DELIVERY));
    when(configUtils.isFeatureFlagEnabled(ReceivingConstants.IS_FNL_CHECK_ENABLED))
        .thenReturn(true);

    List<CancelContainerResponse> responseList =
        cancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertEquals(
        responseList.get(0).getErrorCode(),
        ExceptionCodes.LABEL_CORRECTION_ERROR_FOR_FINALIZED_DELIVERY);
    assertEquals(
        responseList.get(0).getErrorMessage(),
        ReceivingException.LABEL_QUANTITY_ADJUSTMENT_ERROR_MSG_FOR_FINALIZED_DELIVERY);
    assertEquals(responseList.get(0).getTrackingId(), trackingId);
  }

  @Test
  public void testCancelContainers_containerWithChild() throws ReceivingException {
    Container container = WitronContainer.getContainer1();
    Set<Container> childContainers = new HashSet<Container>();
    Container childContainer = new Container();
    childContainer.setDeliveryNumber(container.getDeliveryNumber());
    childContainer.setParentTrackingId(trackingId);
    childContainer.setContainerStatus("");
    childContainers.add(childContainer);

    when(containerRepository.findByTrackingId(trackingId)).thenReturn(container);
    when(containerRepository.findAllByParentTrackingId(trackingId)).thenReturn(childContainers);
    when(containerItemRepository.findByTrackingId(trackingId)).thenReturn(null);

    List<CancelContainerResponse> responseList =
        cancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertEquals(
        responseList.get(0).getErrorCode(), ReceivingException.CONTAINER_WITH_CHILD_ERROR_CODE);
    assertEquals(
        responseList.get(0).getErrorMessage(), ReceivingException.CONTAINER_WITH_CHILD_ERROR_MSG);
    assertEquals(responseList.get(0).getTrackingId(), trackingId);
  }

  @Test
  public void testCancelContainers_containerWithNoContents() throws ReceivingException {
    when(containerRepository.findByTrackingId(trackingId))
        .thenReturn(WitronContainer.getContainer1());
    when(containerRepository.findAllByParentTrackingId(trackingId))
        .thenReturn(new HashSet<Container>());
    when(containerItemRepository.findByTrackingId(trackingId))
        .thenReturn(new ArrayList<ContainerItem>());

    List<CancelContainerResponse> responseList =
        cancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertEquals(
        responseList.get(0).getErrorCode(),
        ReceivingException.CONTAINER_WITH_NO_CONTENTS_ERROR_CODE);
    assertEquals(
        responseList.get(0).getErrorMessage(),
        ReceivingException.CONTAINER_WITH_NO_CONTENTS_ERROR_MSG);
    assertEquals(responseList.get(0).getTrackingId(), trackingId);
  }

  @Test
  public void testCancelContainers_containerWithNoPalletComplete() throws ReceivingException {
    String parentTrackingId = "027734368100444930";
    Container container = WitronContainer.getContainer1();
    container.setParentTrackingId(parentTrackingId);

    Container parentContainer = WitronContainer.getContainer1();
    parentContainer.setPublishTs(null);

    when(containerRepository.findByTrackingId(trackingId)).thenReturn(container);
    when(containerRepository.findAllByParentTrackingId(trackingId))
        .thenReturn(new HashSet<Container>());
    when(containerItemRepository.findByTrackingId(trackingId))
        .thenReturn(container.getContainerItems());
    when(containerRepository.findByTrackingId(parentTrackingId)).thenReturn(parentContainer);

    List<CancelContainerResponse> responseList =
        cancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    assertEquals(
        responseList.get(0).getErrorCode(),
        ReceivingException.CONTAINER_ON_UNFINISHED_PALLET_ERROR_CODE);
    assertEquals(
        responseList.get(0).getErrorMessage(),
        ReceivingException.CONTAINER_ON_UNFINISHED_PALLET_ERROR_MSG);
    assertEquals(responseList.get(0).getTrackingId(), trackingId);
  }

  @Test
  public void testCancelContainers_inventoryServiceDown() throws ReceivingException {
    when(containerRepository.findByTrackingId(trackingId))
        .thenReturn(WitronContainer.getContainer1());
    when(containerRepository.findAllByParentTrackingId(trackingId))
        .thenReturn(new HashSet<Container>());
    when(containerItemRepository.findByTrackingId(trackingId))
        .thenReturn(WitronContainer.getContainer1().getContainerItems());

    doReturn(new ResponseEntity<String>("{}", HttpStatus.SERVICE_UNAVAILABLE))
        .when(restUtils)
        .post(any(), any(), any(), any());

    List<CancelContainerResponse> responseList =
        cancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    verify(restUtils, times(1)).post(any(), any(), any(), any());
    assertEquals(
        responseList.get(0).getErrorCode(), ReceivingException.INVENTORY_SERVICE_DOWN_ERROR_CODE);
    assertEquals(
        responseList.get(0).getErrorMessage(), ReceivingException.INVENTORY_SERVICE_DOWN_ERROR_MSG);
    assertEquals(responseList.get(0).getTrackingId(), trackingId);
  }

  @Test
  public void testCancelContainers_inventoryError() throws ReceivingException {
    when(containerRepository.findByTrackingId(trackingId))
        .thenReturn(WitronContainer.getContainer1());
    when(containerRepository.findAllByParentTrackingId(trackingId))
        .thenReturn(new HashSet<Container>());
    when(containerItemRepository.findByTrackingId(trackingId))
        .thenReturn(WitronContainer.getContainer1().getContainerItems());

    doReturn(new ResponseEntity<String>("", HttpStatus.BAD_REQUEST))
        .when(restUtils)
        .post(any(), any(), any(), any());

    List<CancelContainerResponse> responseList =
        cancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    verify(restUtils, times(1)).post(any(), any(), any(), any());
    assertEquals(responseList.get(0).getErrorCode(), ReceivingException.INVENTORY_ERROR_CODE);
    assertEquals(responseList.get(0).getErrorMessage(), ReceivingException.INVENTORY_ERROR_MSG);
    assertEquals(responseList.get(0).getTrackingId(), trackingId);
  }

  @Test
  public void testCancelContainers_partialSuccess() throws ReceivingException {
    CancelContainerRequest cancelContainerRequest1 = new CancelContainerRequest();
    List<String> labels = new ArrayList<String>();
    labels.add("027734368100444931");
    labels.add("027734368100444932");
    labels.add("027734368100444933");
    labels.add("027734368100444934");
    cancelContainerRequest1.setTrackingIds(labels);

    when(containerRepository.findByTrackingId(labels.get(0)))
        .thenReturn(WitronContainer.getContainer1());
    when(containerRepository.findByTrackingId(labels.get(1)))
        .thenReturn(WitronContainer.getContainer2());
    when(containerRepository.findByTrackingId(labels.get(2)))
        .thenReturn(WitronContainer.getContainer3());
    when(containerRepository.findByTrackingId(labels.get(3))).thenReturn(null);

    when(containerRepository.findAllByParentTrackingId(labels.get(0)))
        .thenReturn(new HashSet<Container>());
    when(containerRepository.findAllByParentTrackingId(labels.get(1)))
        .thenReturn(new HashSet<Container>());
    when(containerRepository.findAllByParentTrackingId(labels.get(2)))
        .thenReturn(new HashSet<Container>());
    when(containerRepository.findAllByParentTrackingId(labels.get(3))).thenReturn(null);

    when(containerItemRepository.findByTrackingId(labels.get(0)))
        .thenReturn(WitronContainer.getContainer1().getContainerItems());
    when(containerItemRepository.findByTrackingId(labels.get(1)))
        .thenReturn(WitronContainer.getContainer2().getContainerItems());
    when(containerItemRepository.findByTrackingId(labels.get(2)))
        .thenReturn(WitronContainer.getContainer3().getContainerItems());
    when(containerItemRepository.findByTrackingId(labels.get(3))).thenReturn(null);

    doReturn(new ResponseEntity<String>("{}", HttpStatus.OK))
        .when(restUtils)
        .post(any(), any(), any(), any());

    doReturn(new ResponseEntity<String>("{}", HttpStatus.OK))
        .when(restUtils)
        .post(any(), any(), any(), any());

    List<CancelContainerResponse> responseList =
        cancelContainerProcessor.cancelContainers(cancelContainerRequest1, httpHeaders);

    assertEquals(responseList.size(), 2);

    assertEquals(
        responseList.get(0).getErrorCode(),
        ReceivingException.CONTAINER_ALREADY_CANCELED_ERROR_CODE);
    assertEquals(
        responseList.get(0).getErrorMessage(),
        ReceivingException.CONTAINER_ALREADY_CANCELED_ERROR_MSG);
    assertEquals(responseList.get(0).getTrackingId(), labels.get(2));

    assertEquals(
        responseList.get(1).getErrorCode(), ReceivingException.CONTAINER_NOT_FOUND_ERROR_CODE);
    assertEquals(
        responseList.get(1).getErrorMessage(), ReceivingException.CONTAINER_NOT_FOUND_ERROR_MSG);
    assertEquals(responseList.get(1).getTrackingId(), labels.get(3));
  }

  @Test
  public void testCancelContainers_success_with_poconfiramtion() throws ReceivingException {
    when(containerRepository.findByTrackingId(trackingId))
        .thenReturn(WitronContainer.getContainer1());
    when(containerRepository.findAllByParentTrackingId(trackingId))
        .thenReturn(new HashSet<Container>());
    when(containerItemRepository.findByTrackingId(trackingId))
        .thenReturn(WitronContainer.getContainer1().getContainerItems());
    when(configUtils.isPoConfirmationFlagEnabled(any())).thenReturn(true);
    when(receiptService.isPOFinalized(anyString(), anyString())).thenReturn(false);

    doReturn(new ResponseEntity<String>("{}", HttpStatus.OK))
        .when(restUtils)
        .post(any(), any(), any(), any());

    List<CancelContainerResponse> responseList =
        cancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    verify(restUtils, times(1)).post(any(), any(), any(), any());
    assertEquals(responseList.size(), 0);
  }

  @Test
  public void testCancelContainers_with_poconfiramtion_failed() throws ReceivingException {
    when(containerRepository.findByTrackingId(trackingId))
        .thenReturn(WitronContainer.getContainer1());
    when(containerRepository.findAllByParentTrackingId(trackingId))
        .thenReturn(new HashSet<Container>());
    when(containerItemRepository.findByTrackingId(trackingId))
        .thenReturn(WitronContainer.getContainer1().getContainerItems());
    when(configUtils.isPoConfirmationFlagEnabled(any())).thenReturn(true);
    when(receiptService.isPOFinalized(anyString(), anyString())).thenReturn(true);

    doReturn(new ResponseEntity<String>("{}", HttpStatus.OK))
        .when(restUtils)
        .post(any(), any(), any(), any());

    List<CancelContainerResponse> responseList =
        cancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);
    assertEquals(responseList.size(), 1);
    String expectedResponseList =
        "[CancelContainerResponse(trackingId=027734368100444931, errorCode=confirmPO, errorMessage=PO already confirmed, cannot cancel label)]";
    assertEquals(responseList.toString(), expectedResponseList);
  }

  /**
   *
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
  public void testCancelContainers_success_case1_automationDc_defaultFlow()
      throws ReceivingException {
    when(containerRepository.findByTrackingId(trackingId))
        .thenReturn(WitronContainer.getContainer1());
    when(containerRepository.findAllByParentTrackingId(trackingId))
        .thenReturn(new HashSet<Container>());
    when(containerItemRepository.findByTrackingId(trackingId))
        .thenReturn(WitronContainer.getContainer1().getContainerItems());
    when(configUtils.isPoConfirmationFlagEnabled(any(Integer.class))).thenReturn(false);
    doReturn(new ResponseEntity<String>("{}", HttpStatus.OK))
        .when(restUtils)
        .post(any(), any(), any(), any());
    // GLS
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag(getFacilityNum().toString(), IS_MANUAL_GDC_ENABLED, false);
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag(getFacilityNum().toString(), IS_DC_ONE_ATLAS_ENABLED, false);
    // Gls

    // execute
    List<CancelContainerResponse> responseList =
        cancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    // Prod (8852) > BAU VtrToInv (inv to dcfin)
    verify(restUtils, times(1)).post(any(), any(), any(), any());

    // Prod (8852) should not hit gls
    verify(glsRestApiClient, times(0)).adjustOrCancel(any(), any());

    // Prod (8852) should not hit dcFin directly
    verify(dcFinRestApiClient, times(0)).adjustOrVtr(any(), any());
    assertEquals(responseList.size(), 0);

    verify(itemConfigApiClient, times(0)).isAtlasConvertedItem(anyLong(), any(HttpHeaders.class));
  }

  /**
   *
   *
   * <pre>
   * # Full GLS(6097) >
   * ## BAU we send to GLS and GLS sends to DcFin
   * ## RCV don't send to Inventory -
   * </pre>
   *
   * @throws ReceivingException
   */
  @Test
  public void testCancelContainers_manualGDC_success_case2_fullGls() throws ReceivingException {
    when(containerRepository.findByTrackingId(trackingId))
        .thenReturn(WitronContainer.getContainer1());
    when(containerRepository.findAllByParentTrackingId(trackingId))
        .thenReturn(new HashSet<Container>());
    when(containerItemRepository.findByTrackingId(trackingId))
        .thenReturn(WitronContainer.getContainer1().getContainerItems());
    when(configUtils.isPoConfirmationFlagEnabled(any(Integer.class))).thenReturn(false);

    // GLS
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(getFacilityNum().toString(), IS_MANUAL_GDC_ENABLED, false);
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag(getFacilityNum().toString(), IS_DC_ONE_ATLAS_ENABLED, false);
    // Gls

    List<CancelContainerResponse> responseList =
        cancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    verify(restUtils, times(0)).post(any(), any(), any(), any());
    verify(glsRestApiClient, times(1)).adjustOrCancel(any(), any());
    verify(dcFinRestApiClient, times(0)).adjustOrVtr(any(), any());
    assertEquals(responseList.size(), 0);
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
   * </pre>
   *
   * @throws ReceivingException
   */
  @Test
  public void testCancelContainers_manualGDC_success_case3_isOneAtlasAndNotConverted()
      throws ReceivingException, ItemConfigRestApiClientException {
    when(containerRepository.findByTrackingId(trackingId))
        .thenReturn(WitronContainer.getContainer1());
    when(containerRepository.findAllByParentTrackingId(trackingId))
        .thenReturn(new HashSet<Container>());
    when(containerItemRepository.findByTrackingId(trackingId))
        .thenReturn(WitronContainer.getContainer1().getContainerItems());
    when(configUtils.isPoConfirmationFlagEnabled(any(Integer.class))).thenReturn(false);

    // GLS
    doReturn(new ArrayList<Receipt>())
        .when(receiptHelper)
        .getReceipts(anyString(), any(), any(), anyLong());
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(getFacilityNum().toString(), IS_MANUAL_GDC_ENABLED, false);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(getFacilityNum().toString(), IS_DC_ONE_ATLAS_ENABLED, false);
    doReturn(false)
        .when(itemConfigApiClient)
        .isAtlasConvertedItem(anyLong(), any(HttpHeaders.class));
    // Gls

    // exec
    List<CancelContainerResponse> responseList =
        cancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    verify(restUtils, times(0)).post(any(), any(), any(), any());
    verify(glsRestApiClient, times(1)).adjustOrCancel(any(), any());
    verify(dcFinRestApiClient, times(1)).adjustOrVtr(any(), any());
    assertEquals(responseList.size(), 0);
  }

  /**
   *
   *
   * <pre>
   * # OneAtlas
   * ## ItemConverted
   * ### expected to work like BAU so RCV to send to Inventory, Inventory Send to DcFin
   * </pre>
   *
   * @throws ReceivingException
   */
  @Test
  public void testCancelContainers_success_case4_isOneAtlasAndConverted()
      throws ReceivingException, ItemConfigRestApiClientException {
    when(containerRepository.findByTrackingId(trackingId))
        .thenReturn(WitronContainer.getContainer1());
    when(containerRepository.findAllByParentTrackingId(trackingId))
        .thenReturn(new HashSet<Container>());
    List<ContainerItem> ciList = WitronContainer.getContainer1().getContainerItems();
    doReturn(true)
        .when(itemConfigApiClient)
        .isAtlasConvertedItem(anyLong(), any(HttpHeaders.class));

    when(containerItemRepository.findByTrackingId(trackingId)).thenReturn(ciList);
    when(configUtils.isPoConfirmationFlagEnabled(any(Integer.class))).thenReturn(false);
    doReturn(new ResponseEntity<String>("{}", HttpStatus.OK))
        .when(restUtils)
        .post(any(), any(), any(), any());
    // GLS
    doReturn(new ArrayList<Receipt>())
        .when(receiptHelper)
        .getReceipts(anyString(), any(), any(), anyLong());
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(getFacilityNum().toString(), IS_MANUAL_GDC_ENABLED, false);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(getFacilityNum().toString(), IS_DC_ONE_ATLAS_ENABLED, false);
    doReturn(true)
        .when(itemConfigApiClient)
        .isAtlasConvertedItem(anyLong(), any(HttpHeaders.class));

    // Gls

    // exec
    List<CancelContainerResponse> responseList =
        cancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    // Prod (8852) > BAU VtrToInv (inv to dcfin)
    verify(restUtils, times(1)).post(any(), any(), any(), any());

    // Prod (8852) should not hit gls
    verify(glsRestApiClient, times(0)).adjustOrCancel(any(), any());

    // Prod (8852) should not hit dcFin directly
    verify(dcFinRestApiClient, times(0)).adjustOrVtr(any(), any());
    assertEquals(responseList.size(), 0);
  }
  /**
   * Inventory 2.0 required headers, new contract
   *
   * <pre>
   * # OneAtlas
   * ## ItemConverted
   * ### expected to work like BAU so RCV to send to Inventory, Inventory Send to DcFin
   * </pre>
   *
   * @throws ReceivingException
   */
  @Test
  public void testCancelContainers_success_case4_isOneAtlasAndConverted_Inv2_ContractHeaders()
      throws ReceivingException, ItemConfigRestApiClientException {
    when(containerRepository.findByTrackingId(trackingId))
        .thenReturn(WitronContainer.getContainer1());
    when(containerRepository.findAllByParentTrackingId(trackingId))
        .thenReturn(new HashSet<Container>());
    List<ContainerItem> ciList = WitronContainer.getContainer1().getContainerItems();
    doReturn(true)
        .when(itemConfigApiClient)
        .isAtlasConvertedItem(anyLong(), any(HttpHeaders.class));

    when(containerItemRepository.findByTrackingId(trackingId)).thenReturn(ciList);
    when(configUtils.isPoConfirmationFlagEnabled(any(Integer.class))).thenReturn(false);
    doReturn(new ResponseEntity<String>("{}", HttpStatus.OK))
        .when(restUtils)
        .post(any(), any(), any(), any());
    // GLS
    doReturn(new ArrayList<Receipt>())
        .when(receiptHelper)
        .getReceipts(anyString(), any(), any(), anyLong());
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(getFacilityNum().toString(), IS_MANUAL_GDC_ENABLED, false);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(getFacilityNum().toString(), IS_DC_ONE_ATLAS_ENABLED, false);

    doReturn(invCoreBaseUrl).when(appConfig).getInventoryCoreBaseUrl();

    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(getFacilityNum().toString(), INV_V2_ENABLED, false);
    // Gls

    // exec
    ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<HttpHeaders> headerCaptor = ArgumentCaptor.forClass(HttpHeaders.class);

    List<CancelContainerResponse> responseList =
        cancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    // inv
    verify(restUtils, times(1)).post(urlCaptor.capture(), headerCaptor.capture(), any(), any());
    assertEquals(
        urlCaptor.getValue(),
        "https://gls-atlas-inventory-core-gdc-stg.walmart.com/containers/adjust");
    assertEquals(headerCaptor.getValue().getFirst(FLOW_NAME), VTR_FLOW);

    verify(glsRestApiClient, times(0)).adjustOrCancel(any(), any());
    verify(dcFinRestApiClient, times(0)).adjustOrVtr(any(), any());
    assertEquals(responseList.size(), 0);
  }

  private static List<ItemConfigDetails> getItemConfigDetails_Converted() {
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
  public void testCancelContainers_manualGDC_glsException() throws ReceivingException {
    when(containerRepository.findByTrackingId(trackingId))
        .thenReturn(WitronContainer.getContainer1());
    when(containerRepository.findAllByParentTrackingId(trackingId))
        .thenReturn(new HashSet<Container>());
    when(containerItemRepository.findByTrackingId(trackingId))
        .thenReturn(WitronContainer.getContainer1().getContainerItems());
    when(configUtils.isPoConfirmationFlagEnabled(any(Integer.class))).thenReturn(false);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(getFacilityNum().toString(), IS_MANUAL_GDC_ENABLED, false);
    when(configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), ReceivingConstants.IS_INVENTORY_API_DISABLED, false))
        .thenReturn(true);

    when(glsRestApiClient.adjustOrCancel(any(), any()))
        .thenThrow(
            new ReceivingException("glsError", HttpStatus.INTERNAL_SERVER_ERROR, "GLS_ERROR"));

    List<CancelContainerResponse> responseList =
        cancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    verify(restUtils, times(0)).post(any(), any(), any(), any());
    verify(glsRestApiClient, times(1)).adjustOrCancel(any(), any());
    verify(containerRepository, times(0)).save(any());
    verify(receiptRepository, times(0)).saveAll(any());
    assertEquals(responseList.size(), 1);
    assertEquals(responseList.get(0).getErrorMessage(), "glsError");
    assertEquals(responseList.get(0).getErrorCode(), VTR_ERROR_CODE);
  }

  @Test
  public void testCancelContainers_manualGDC_glsException_emptyErrorMsg()
      throws ReceivingException {
    when(containerRepository.findByTrackingId(trackingId))
        .thenReturn(WitronContainer.getContainer1());
    when(containerRepository.findAllByParentTrackingId(trackingId))
        .thenReturn(new HashSet<Container>());
    when(containerItemRepository.findByTrackingId(trackingId))
        .thenReturn(WitronContainer.getContainer1().getContainerItems());
    when(configUtils.isPoConfirmationFlagEnabled(any(Integer.class))).thenReturn(false);
    doReturn(true)
        .when(configUtils)
        .getConfiguredFeatureFlag(getFacilityNum().toString(), IS_MANUAL_GDC_ENABLED, false);
    when(configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), ReceivingConstants.IS_INVENTORY_API_DISABLED, false))
        .thenReturn(true);

    when(glsRestApiClient.adjustOrCancel(any(), any()))
        .thenThrow(new ReceivingException("", HttpStatus.INTERNAL_SERVER_ERROR, "GLS_ERROR"));

    List<CancelContainerResponse> responseList =
        cancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    verify(restUtils, times(0)).post(any(), any(), any(), any());
    verify(glsRestApiClient, times(1)).adjustOrCancel(any(), any());
    verify(containerRepository, times(0)).save(any());
    verify(receiptRepository, times(0)).saveAll(any());
    assertEquals(responseList.size(), 1);
    assertEquals(responseList.get(0).getErrorMessage(), VTR_ERROR_MSG);
    assertEquals(responseList.get(0).getErrorCode(), VTR_ERROR_CODE);
  }

  @Test
  public void testCancelContainers_manualGDC_dbException() throws Exception {
    when(containerRepository.findByTrackingId(trackingId))
        .thenReturn(WitronContainer.getContainer1());
    when(containerRepository.findAllByParentTrackingId(trackingId))
        .thenReturn(new HashSet<Container>());
    when(containerItemRepository.findByTrackingId(trackingId))
        .thenReturn(WitronContainer.getContainer1().getContainerItems());
    when(configUtils.isPoConfirmationFlagEnabled(any(Integer.class))).thenReturn(false);
    when(configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), ReceivingConstants.IS_MANUAL_GDC_ENABLED, false))
        .thenReturn(true);
    when(configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), ReceivingConstants.IS_INVENTORY_API_DISABLED, false))
        .thenReturn(true);

    DataAccessException dbException = new RecoverableDataAccessException("Error persisting data");
    when(containerRepository.save(any())).thenThrow(dbException);

    List<CancelContainerResponse> responseList =
        cancelContainerProcessor.cancelContainers(cancelContainerRequest, httpHeaders);

    verify(restUtils, times(0)).post(any(), any(), any(), any());
    verify(glsRestApiClient, times(1)).adjustOrCancel(any(), any());
    verify(containerRepository, times(1)).save(any());
    assertEquals(responseList.size(), 1);
  }
}
