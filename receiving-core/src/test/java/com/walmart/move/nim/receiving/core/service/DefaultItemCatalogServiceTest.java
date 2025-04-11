package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.itemupdate.ItemUpdateRestApiClient;
import com.walmart.move.nim.receiving.core.common.ItemUpdateUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.ItemCatalogUpdateLog;
import com.walmart.move.nim.receiving.core.entity.PurgeData;
import com.walmart.move.nim.receiving.core.mock.data.MockItemUpdateData;
import com.walmart.move.nim.receiving.core.model.ItemCatalogDeleteRequest;
import com.walmart.move.nim.receiving.core.model.ItemCatalogUpdateRequest;
import com.walmart.move.nim.receiving.core.model.itemupdate.ItemUpdateRequest;
import com.walmart.move.nim.receiving.core.repositories.ItemCatalogRepository;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.PurgeEntityType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class DefaultItemCatalogServiceTest {
  @InjectMocks private DefaultItemCatalogService defaultItemCatalogService;
  @Mock private DeliveryService deliveryService;
  @Mock private ItemMDMService itemMDMService;
  @Mock private ItemCatalogRepository itemCatalogRepository;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private ItemUpdateUtils itemUpdateUtils;
  @Mock private ItemUpdateRestApiClient itemUpdateRestApiClient;
  private Gson gson = new Gson();

  private ItemCatalogUpdateRequest itemCatalogUpdateRequest;
  private PageRequest pageReq;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32818);
    TenantContext.setFacilityCountryCode("US");
  }

  @BeforeMethod
  public void setup() {
    itemCatalogUpdateRequest = new ItemCatalogUpdateRequest();
    itemCatalogUpdateRequest.setDeliveryNumber("87654321");
    itemCatalogUpdateRequest.setItemNumber(567898765L);
    itemCatalogUpdateRequest.setNewItemUPC("20000943037194");
    itemCatalogUpdateRequest.setOldItemUPC("00000943037194");
    itemCatalogUpdateRequest.setItemInfoHandKeyed(Boolean.TRUE);
    pageReq = PageRequest.of(0, 10);
  }

  @AfterMethod()
  public void resetMocks() {
    reset(
        deliveryService,
        itemMDMService,
        itemCatalogRepository,
        configUtils,
        itemUpdateRestApiClient,
        itemUpdateUtils);
  }

  @Test
  public void testUpdateVendorUpcSuccessScenarioForBothGdmAndMdmEnabled() {
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_GDM))
        .thenReturn(true);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_MDM))
        .thenReturn(true);
    doNothing()
        .when(deliveryService)
        .updateVendorUPC(anyString(), anyLong(), anyString(), any(HttpHeaders.class));
    doNothing()
        .when(itemMDMService)
        .updateVendorUPC(any(ItemCatalogUpdateRequest.class), any(HttpHeaders.class));

    defaultItemCatalogService.updateVendorUPC(
        itemCatalogUpdateRequest,
        ReceivingUtils.getForwardableHttpHeaders(MockHttpHeaders.getHeaders()));

    verify(deliveryService, Mockito.times(1))
        .updateVendorUPC(anyString(), anyLong(), anyString(), any(HttpHeaders.class));
    verify(itemMDMService, Mockito.times(1))
        .updateVendorUPC(any(ItemCatalogUpdateRequest.class), any(HttpHeaders.class));
    verify(itemCatalogRepository, Mockito.times(1)).save(any());
  }

  @Test
  public void testUpdateVendorUpcSuccessScenarioEnabledOnlyForGdm() {
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_GDM))
        .thenReturn(true);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_MDM))
        .thenReturn(false);
    doNothing()
        .when(deliveryService)
        .updateVendorUPC(anyString(), anyLong(), anyString(), any(HttpHeaders.class));
    doNothing()
        .when(itemMDMService)
        .updateVendorUPC(any(ItemCatalogUpdateRequest.class), any(HttpHeaders.class));

    defaultItemCatalogService.updateVendorUPC(
        itemCatalogUpdateRequest,
        ReceivingUtils.getForwardableHttpHeaders(MockHttpHeaders.getHeaders()));

    verify(deliveryService, Mockito.times(1))
        .updateVendorUPC(anyString(), anyLong(), anyString(), any(HttpHeaders.class));
    verify(itemMDMService, Mockito.times(0))
        .updateVendorUPC(any(ItemCatalogUpdateRequest.class), any(HttpHeaders.class));
    verify(itemCatalogRepository, Mockito.times(1)).save(any());
  }

  @Test
  public void testUpdateVendorUpcInGDMItemV3SuccessScenarioEnabledOnlyForGdm() {
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_VENDOR_UPC_UPDATE_ENABLED_FOR_GDM))
        .thenReturn(true);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_MDM))
        .thenReturn(false);
    doNothing()
        .when(deliveryService)
        .updateVendorUpcItemV3(anyLong(), anyString(), any(HttpHeaders.class));
    doNothing()
        .when(itemMDMService)
        .updateVendorUPC(any(ItemCatalogUpdateRequest.class), any(HttpHeaders.class));

    defaultItemCatalogService.updateVendorUPC(
        itemCatalogUpdateRequest,
        ReceivingUtils.getForwardableHttpHeaders(MockHttpHeaders.getHeaders()));

    verify(deliveryService, Mockito.times(1))
        .updateVendorUpcItemV3(anyLong(), anyString(), any(HttpHeaders.class));
    verify(itemMDMService, Mockito.times(0))
        .updateVendorUPC(any(ItemCatalogUpdateRequest.class), any(HttpHeaders.class));
    verify(itemCatalogRepository, Mockito.times(1)).save(any());
  }

  @Test
  public void testUpdateVendorUpcSuccessScenarioEnabledOnlyForMdm() {
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_GDM))
        .thenReturn(false);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_MDM))
        .thenReturn(true);
    doNothing()
        .when(deliveryService)
        .updateVendorUPC(anyString(), anyLong(), anyString(), any(HttpHeaders.class));
    doNothing()
        .when(itemMDMService)
        .updateVendorUPC(any(ItemCatalogUpdateRequest.class), any(HttpHeaders.class));

    defaultItemCatalogService.updateVendorUPC(
        itemCatalogUpdateRequest,
        ReceivingUtils.getForwardableHttpHeaders(MockHttpHeaders.getHeaders()));

    verify(deliveryService, Mockito.times(0))
        .updateVendorUPC(anyString(), anyLong(), anyString(), any(HttpHeaders.class));
    verify(itemMDMService, Mockito.times(1))
        .updateVendorUPC(any(ItemCatalogUpdateRequest.class), any(HttpHeaders.class));
    verify(itemCatalogRepository, Mockito.times(1)).save(any());
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "Invalid request for upc update, delivery = 87654321, item = 567898765L")
  public void testUpdateVendorUpcWhenGDMServiceThrowsError() {
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_GDM))
        .thenReturn(true);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_MDM))
        .thenReturn(false);
    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.INVALID_ITEM_DETAILS,
                String.format(
                    ReceivingConstants.GDM_CATALOG_BAD_REQUEST, "87654321", "567898765L")))
        .when(deliveryService)
        .updateVendorUPC(anyString(), anyLong(), anyString(), any(HttpHeaders.class));
    doNothing()
        .when(itemMDMService)
        .updateVendorUPC(any(ItemCatalogUpdateRequest.class), any(HttpHeaders.class));

    defaultItemCatalogService.updateVendorUPC(
        itemCatalogUpdateRequest,
        ReceivingUtils.getForwardableHttpHeaders(MockHttpHeaders.getHeaders()));

    verify(deliveryService, Mockito.times(1))
        .updateVendorUPC(anyString(), anyLong(), anyString(), any(HttpHeaders.class));
    verify(itemMDMService, Mockito.times(0))
        .updateVendorUPC(any(ItemCatalogUpdateRequest.class), any(HttpHeaders.class));
    verify(itemCatalogRepository, Mockito.times(0)).save(any());
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "Item mdm service is down. Error message = Service not available")
  public void testUpdateVendorUpcWhenMDMServiceThrowsError() {
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_GDM))
        .thenReturn(false);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_MDM))
        .thenReturn(true);
    doNothing()
        .when(deliveryService)
        .updateVendorUPC(anyString(), anyLong(), anyString(), any(HttpHeaders.class));
    doThrow(
            new ReceivingBadDataException(
                ExceptionCodes.ITEM_NOT_FOUND,
                String.format(
                    ExceptionDescriptionConstants.ITEM_MDM_SERVICE_DOWN_ERROR_MSG,
                    "Service not available")))
        .when(itemMDMService)
        .updateVendorUPC(any(ItemCatalogUpdateRequest.class), any(HttpHeaders.class));

    defaultItemCatalogService.updateVendorUPC(
        itemCatalogUpdateRequest,
        ReceivingUtils.getForwardableHttpHeaders(MockHttpHeaders.getHeaders()));

    verify(deliveryService, Mockito.times(0))
        .updateVendorUPC(anyString(), anyLong(), anyString(), any(HttpHeaders.class));
    verify(itemMDMService, Mockito.times(1))
        .updateVendorUPC(any(ItemCatalogUpdateRequest.class), any(HttpHeaders.class));
    verify(itemCatalogRepository, Mockito.times(0)).save(any());
  }

  @Test
  public void
      testUpdateVendorUpcSuccessScenarioEnabledOnlyForIQS_GTIN14Format_CatalogUPCConversionDisabled() {
    ItemCatalogUpdateLog itemCatalogUpdateLog = new ItemCatalogUpdateLog();
    itemCatalogUpdateLog.setNewItemUPC(itemCatalogUpdateRequest.getNewItemUPC());
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_GDM))
        .thenReturn(false);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_MDM))
        .thenReturn(false);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), ReceivingConstants.IQS_ITEM_UPDATE_ENABLED))
        .thenReturn(true);
    when(itemUpdateUtils.createItemUpdateRequest(
            any(ItemCatalogUpdateRequest.class), any(HttpHeaders.class)))
        .thenReturn(MockItemUpdateData.getItemUpdateRequest());
    when(itemUpdateRestApiClient.updateItem(any(ItemUpdateRequest.class), any(HttpHeaders.class)))
        .thenReturn(MockItemUpdateData.getMockItemUpdateResponse());
    when(itemCatalogRepository.save(any())).thenReturn(itemCatalogUpdateLog);

    String response =
        defaultItemCatalogService.updateVendorUPC(
            itemCatalogUpdateRequest,
            ReceivingUtils.getForwardableHttpHeaders(MockHttpHeaders.getHeaders()));
    ItemCatalogUpdateLog catalogLog = gson.fromJson(response, ItemCatalogUpdateLog.class);
    assertNotNull(response);
    assertEquals(catalogLog.getNewItemUPC(), "20000943037194");
    assertEquals(itemCatalogUpdateRequest.getNewItemUPC(), "20000943037194");

    verify(itemUpdateUtils, times(1))
        .createItemUpdateRequest(any(ItemCatalogUpdateRequest.class), any(HttpHeaders.class));
    verify(itemUpdateRestApiClient, times(1))
        .updateItem(any(ItemUpdateRequest.class), any(HttpHeaders.class));
    verify(itemCatalogRepository, times(1)).save(any());
  }

  @Test
  public void
      testUpdateVendorUpcSuccessScenarioEnabledOnlyForIQS_GTIN14Format_CatalogUPCConversionEnabled() {
    ItemCatalogUpdateLog itemCatalogUpdateLog = new ItemCatalogUpdateLog();
    itemCatalogUpdateLog.setNewItemUPC(itemCatalogUpdateRequest.getNewItemUPC());
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_GDM))
        .thenReturn(false);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_MDM))
        .thenReturn(false);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), ReceivingConstants.IQS_ITEM_UPDATE_ENABLED))
        .thenReturn(true);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.CATALOG_UPC_CONVERSION_ENABLED,
            false))
        .thenReturn(true);
    when(itemUpdateUtils.createItemUpdateRequest(
            any(ItemCatalogUpdateRequest.class), any(HttpHeaders.class)))
        .thenReturn(MockItemUpdateData.getItemUpdateRequest());
    when(itemUpdateRestApiClient.updateItem(any(ItemUpdateRequest.class), any(HttpHeaders.class)))
        .thenReturn(MockItemUpdateData.getMockItemUpdateResponse());
    when(itemCatalogRepository.save(any())).thenReturn(itemCatalogUpdateLog);

    String response =
        defaultItemCatalogService.updateVendorUPC(
            itemCatalogUpdateRequest,
            ReceivingUtils.getForwardableHttpHeaders(MockHttpHeaders.getHeaders()));
    ItemCatalogUpdateLog catalogLog = gson.fromJson(response, ItemCatalogUpdateLog.class);
    assertNotNull(response);
    assertEquals(catalogLog.getNewItemUPC(), "20000943037194");
    assertEquals(itemCatalogUpdateRequest.getNewItemUPC(), "20000943037194");

    verify(itemUpdateUtils, times(1))
        .createItemUpdateRequest(any(ItemCatalogUpdateRequest.class), any(HttpHeaders.class));
    verify(itemUpdateRestApiClient, times(1))
        .updateItem(any(ItemUpdateRequest.class), any(HttpHeaders.class));
    verify(itemCatalogRepository, times(1)).save(any());
  }

  @Test
  public void testUpdateVendorUpcSuccessScenarioEnabledOnlyForIQS_GTIN12Format() {
    String catalogUPC = "123456789012";
    itemCatalogUpdateRequest.setNewItemUPC(catalogUPC);
    ItemCatalogUpdateLog itemCatalogUpdateLog = new ItemCatalogUpdateLog();
    itemCatalogUpdateLog.setNewItemUPC(itemCatalogUpdateRequest.getNewItemUPC());
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_GDM))
        .thenReturn(false);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_MDM))
        .thenReturn(false);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), ReceivingConstants.IQS_ITEM_UPDATE_ENABLED))
        .thenReturn(true);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.CATALOG_UPC_CONVERSION_ENABLED,
            false))
        .thenReturn(true);
    when(itemUpdateUtils.createItemUpdateRequest(
            any(ItemCatalogUpdateRequest.class), any(HttpHeaders.class)))
        .thenReturn(MockItemUpdateData.getItemUpdateRequest());
    when(itemUpdateRestApiClient.updateItem(any(ItemUpdateRequest.class), any(HttpHeaders.class)))
        .thenReturn(MockItemUpdateData.getMockItemUpdateResponse());
    when(itemCatalogRepository.save(any())).thenReturn(itemCatalogUpdateLog);

    String response =
        defaultItemCatalogService.updateVendorUPC(
            itemCatalogUpdateRequest,
            ReceivingUtils.getForwardableHttpHeaders(MockHttpHeaders.getHeaders()));
    ItemCatalogUpdateLog catalogLog = gson.fromJson(response, ItemCatalogUpdateLog.class);
    assertNotNull(response);
    assertEquals(catalogLog.getNewItemUPC(), catalogUPC);
    assertEquals(itemCatalogUpdateRequest.getNewItemUPC(), "00123456789012");

    verify(itemUpdateUtils, times(1))
        .createItemUpdateRequest(any(ItemCatalogUpdateRequest.class), any(HttpHeaders.class));
    verify(itemUpdateRestApiClient, times(1))
        .updateItem(any(ItemUpdateRequest.class), any(HttpHeaders.class));
    verify(itemCatalogRepository, times(1)).save(any());
  }

  @Test
  public void testUpdateVendorUpcSuccessScenarioEnabledOnlyForIQS_GTIN13Format() {
    String catalogUPC = "1234567890123";
    itemCatalogUpdateRequest.setNewItemUPC(catalogUPC);
    ItemCatalogUpdateLog itemCatalogUpdateLog = new ItemCatalogUpdateLog();
    itemCatalogUpdateLog.setNewItemUPC(itemCatalogUpdateRequest.getNewItemUPC());
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_GDM))
        .thenReturn(false);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_MDM))
        .thenReturn(false);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), ReceivingConstants.IQS_ITEM_UPDATE_ENABLED))
        .thenReturn(true);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.CATALOG_UPC_CONVERSION_ENABLED,
            false))
        .thenReturn(true);
    when(itemUpdateUtils.createItemUpdateRequest(
            any(ItemCatalogUpdateRequest.class), any(HttpHeaders.class)))
        .thenReturn(MockItemUpdateData.getItemUpdateRequest());
    when(itemUpdateRestApiClient.updateItem(any(ItemUpdateRequest.class), any(HttpHeaders.class)))
        .thenReturn(MockItemUpdateData.getMockItemUpdateResponse());
    when(itemCatalogRepository.save(any())).thenReturn(itemCatalogUpdateLog);

    String response =
        defaultItemCatalogService.updateVendorUPC(
            itemCatalogUpdateRequest,
            ReceivingUtils.getForwardableHttpHeaders(MockHttpHeaders.getHeaders()));
    ItemCatalogUpdateLog catalogLog = gson.fromJson(response, ItemCatalogUpdateLog.class);
    assertNotNull(response);
    assertEquals(catalogLog.getNewItemUPC(), catalogUPC);
    assertEquals(itemCatalogUpdateRequest.getNewItemUPC(), "01234567890123");

    verify(itemUpdateUtils, times(1))
        .createItemUpdateRequest(any(ItemCatalogUpdateRequest.class), any(HttpHeaders.class));
    verify(itemUpdateRestApiClient, times(1))
        .updateItem(any(ItemUpdateRequest.class), any(HttpHeaders.class));
    verify(itemCatalogRepository, times(1)).save(any());
  }

  @Test
  public void testUpdateVendorUpcSuccessScenarioEnabledOnlyForIQS_GTIN8Format() {
    String catalogUPC = "72712876";
    itemCatalogUpdateRequest.setNewItemUPC(catalogUPC);
    ItemCatalogUpdateLog itemCatalogUpdateLog = new ItemCatalogUpdateLog();
    itemCatalogUpdateLog.setNewItemUPC(itemCatalogUpdateRequest.getNewItemUPC());
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_GDM))
        .thenReturn(false);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_MDM))
        .thenReturn(false);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), ReceivingConstants.IQS_ITEM_UPDATE_ENABLED))
        .thenReturn(true);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.CATALOG_UPC_CONVERSION_ENABLED,
            false))
        .thenReturn(true);
    when(itemUpdateUtils.createItemUpdateRequest(
            any(ItemCatalogUpdateRequest.class), any(HttpHeaders.class)))
        .thenReturn(MockItemUpdateData.getItemUpdateRequest());
    when(itemUpdateRestApiClient.updateItem(any(ItemUpdateRequest.class), any(HttpHeaders.class)))
        .thenReturn(MockItemUpdateData.getMockItemUpdateResponse());
    when(itemCatalogRepository.save(any())).thenReturn(itemCatalogUpdateLog);

    String response =
        defaultItemCatalogService.updateVendorUPC(
            itemCatalogUpdateRequest,
            ReceivingUtils.getForwardableHttpHeaders(MockHttpHeaders.getHeaders()));
    ItemCatalogUpdateLog catalogLog = gson.fromJson(response, ItemCatalogUpdateLog.class);
    assertNotNull(response);
    assertEquals(catalogLog.getNewItemUPC(), catalogUPC);
    assertEquals(itemCatalogUpdateRequest.getNewItemUPC(), "00000072712876");

    verify(itemUpdateUtils, times(1))
        .createItemUpdateRequest(any(ItemCatalogUpdateRequest.class), any(HttpHeaders.class));
    verify(itemUpdateRestApiClient, times(1))
        .updateItem(any(ItemUpdateRequest.class), any(HttpHeaders.class));
    verify(itemCatalogRepository, times(1)).save(any());
  }

  private ItemCatalogUpdateLog getItemCatalog() {
    return ItemCatalogUpdateLog.builder()
        .id(0L)
        .itemNumber(567898765L)
        .deliveryNumber(87654321L)
        .newItemUPC("20000943037194")
        .oldItemUPC("00000943037194")
        .build();
  }

  @Test
  public void testPurge() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -60 * 24);

    ItemCatalogUpdateLog itemCatalog = getItemCatalog();
    itemCatalog.setId(1L);
    itemCatalog.setCreateTs(cal.getTime());

    ItemCatalogUpdateLog itemCatalog1 = getItemCatalog();
    itemCatalog1.setId(10L);
    itemCatalog1.setCreateTs(cal.getTime());

    when(itemCatalogRepository.findByIdGreaterThanEqual(anyLong(), any()))
        .thenReturn(Arrays.asList(itemCatalog, itemCatalog1));
    doNothing().when(itemCatalogRepository).deleteAll();
    PurgeData purgeData =
        PurgeData.builder()
            .purgeEntityType(PurgeEntityType.ITEM_CATALOG_UPDATE_LOG)
            .eventTargetStatus(EventTargetStatus.PENDING)
            .lastDeleteId(0L)
            .build();

    long lastDeletedId = defaultItemCatalogService.purge(purgeData, pageReq, 30);
    assertEquals(lastDeletedId, 10L);
  }

  @Test
  public void testPurgeWithNoDataToDeleteBeforeDate() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -60 * 24);

    ItemCatalogUpdateLog itemCatalog = getItemCatalog();
    itemCatalog.setId(1L);
    itemCatalog.setCreateTs(cal.getTime());

    ItemCatalogUpdateLog itemCatalog1 = getItemCatalog();
    itemCatalog1.setId(10L);
    itemCatalog1.setCreateTs(cal.getTime());

    when(itemCatalogRepository.findByIdGreaterThanEqual(anyLong(), any()))
        .thenReturn(Arrays.asList(itemCatalog, itemCatalog1));
    doNothing().when(itemCatalogRepository).deleteAll();
    PurgeData purgeData =
        PurgeData.builder()
            .purgeEntityType(PurgeEntityType.ITEM_CATALOG_UPDATE_LOG)
            .eventTargetStatus(EventTargetStatus.PENDING)
            .lastDeleteId(0L)
            .build();

    long lastDeletedId = defaultItemCatalogService.purge(purgeData, pageReq, 90);
    assertEquals(lastDeletedId, 0L);
  }

  @Test
  public void testPurgeWithFewDataToDeleteBeforeDate() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.HOUR, -60 * 24);

    ItemCatalogUpdateLog itemCatalog = getItemCatalog();
    itemCatalog.setId(1L);
    itemCatalog.setCreateTs(cal.getTime());

    ItemCatalogUpdateLog itemCatalog1 = getItemCatalog();
    itemCatalog1.setId(10L);
    itemCatalog1.setCreateTs(new Date());

    when(itemCatalogRepository.findByIdGreaterThanEqual(anyLong(), any()))
        .thenReturn(Arrays.asList(itemCatalog, itemCatalog1));
    doNothing().when(itemCatalogRepository).deleteAll();
    PurgeData purgeData =
        PurgeData.builder()
            .purgeEntityType(PurgeEntityType.ITEM_CATALOG_UPDATE_LOG)
            .eventTargetStatus(EventTargetStatus.PENDING)
            .lastDeleteId(0L)
            .build();

    long lastDeletedId = defaultItemCatalogService.purge(purgeData, pageReq, 30);
    assertEquals(lastDeletedId, 1L);
  }

  @Test
  public void test_deleteItemCatalog() {

    doNothing()
        .when(itemCatalogRepository)
        .deleteByDeliveryNumberAndNewItemUPC(anyLong(), anyString());

    ItemCatalogDeleteRequest mockItemCatalogUpdateRequest = new ItemCatalogDeleteRequest();
    mockItemCatalogUpdateRequest.setDeliveryNumber("1234");
    mockItemCatalogUpdateRequest.setNewItemUPC("MOCK_UPC");

    defaultItemCatalogService.deleteItemCatalog(mockItemCatalogUpdateRequest);

    verify(itemCatalogRepository, Mockito.times(1))
        .deleteByDeliveryNumberAndNewItemUPC(anyLong(), anyString());
  }
}
