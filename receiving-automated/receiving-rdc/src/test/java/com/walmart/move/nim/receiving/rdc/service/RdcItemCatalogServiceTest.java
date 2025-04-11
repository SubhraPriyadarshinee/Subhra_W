package com.walmart.move.nim.receiving.rdc.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.AssertJUnit.assertNotNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.walmart.move.nim.receiving.core.client.itemupdate.ItemUpdateRestApiClient;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.ItemUpdateUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.ItemCatalogUpdateLog;
import com.walmart.move.nim.receiving.core.model.ItemCatalogUpdateRequest;
import com.walmart.move.nim.receiving.core.model.itemupdate.ItemUpdateRequest;
import com.walmart.move.nim.receiving.core.model.itemupdate.ItemUpdateResponse;
import com.walmart.move.nim.receiving.core.repositories.ItemCatalogRepository;
import com.walmart.move.nim.receiving.core.retry.RetryableRestConnector;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.utils.RdcAutoReceivingUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcReceivingUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.nio.charset.Charset;
import java.util.Date;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RdcItemCatalogServiceTest {

  @Mock private RetryableRestConnector retryableRestConnector;
  @Mock private RdcManagedConfig rdcManagedConfig;
  @Mock private DeliveryService deliveryService;
  @Mock private ItemCatalogRepository itemCatalogRepository;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private ItemUpdateRestApiClient itemUpdateRestApiClient;
  @Mock private ItemUpdateUtils itemUpdateUtils;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private AppConfig appConfig;
  @Mock private RdcReceivingUtils rdcReceivingUtils;
  @Mock private RdcAutoReceivingUtils rdcAutoReceivingUtils;
  @InjectMocks private RdcItemCatalogService rdcItemCatalogService;

  private HttpHeaders headers;
  private Gson gson;
  private ItemCatalogUpdateRequest itemCatalogUpdateRequest;

  private static final String facilityNum = "32818";
  private static final String countryCode = "US";

  @BeforeClass
  public void initMocks() throws Exception {
    MockitoAnnotations.initMocks(this);
    itemCatalogUpdateRequest = new ItemCatalogUpdateRequest();
    gson = new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
  }

  @BeforeMethod
  public void setup() {
    itemCatalogUpdateRequest.setDeliveryNumber("87654321");
    itemCatalogUpdateRequest.setItemNumber(567898765L);
    itemCatalogUpdateRequest.setNewItemUPC("20000943037194");

    TenantContext.setFacilityCountryCode(countryCode);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    headers = MockHttpHeaders.getHeaders(facilityNum, countryCode);
    ReflectionTestUtils.setField(rdcItemCatalogService, "gson", gson);
    ReflectionTestUtils.setField(rdcItemCatalogService, "rdcManagedConfig", rdcManagedConfig);
    ReflectionTestUtils.setField(
        rdcItemCatalogService, "retryableRestConnector", retryableRestConnector);
  }

  @AfterMethod
  public void resetMocks() {
    reset(
        retryableRestConnector,
        deliveryService,
        itemCatalogRepository,
        configUtils,
        itemUpdateRestApiClient,
        rdcManagedConfig,
        itemUpdateUtils,
        tenantSpecificConfigReader,
        rdcAutoReceivingUtils);
    TenantContext.clear();
  }

  @Test
  public void testHappyPathForVendorUpcUpdate() {
    doReturn(getMockItemCacheConfig()).when(rdcManagedConfig).getNgrBaseUrl();
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_GDM))
        .thenReturn(true);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_MDM))
        .thenReturn(false);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), ReceivingConstants.IQS_ITEM_UPDATE_ENABLED))
        .thenReturn(false);
    doNothing()
        .when(deliveryService)
        .updateVendorUPC(anyString(), anyLong(), anyString(), any(HttpHeaders.class));
    when(itemCatalogRepository.save(any(ItemCatalogUpdateLog.class)))
        .thenReturn(getUpdatedItemCatalogLogResponse());
    when(retryableRestConnector.exchange(
            any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(gson.toJson(getUpdatedItemCatalogLogResponse()), HttpStatus.OK));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(anyLong(), anyLong()))
        .thenReturn(true);
    doNothing()
        .when(rdcAutoReceivingUtils)
        .updateCatalogInHawkeye(any(ItemCatalogUpdateRequest.class), any(HttpHeaders.class));
    when(rdcReceivingUtils.isNGRServicesEnabled()).thenReturn(true);
    String catalogedUpcResponse =
        rdcItemCatalogService.updateVendorUPC(itemCatalogUpdateRequest, headers);

    ItemCatalogUpdateLog itemCatalogUpdateLog =
        gson.fromJson(catalogedUpcResponse, ItemCatalogUpdateLog.class);

    assertNotNull(catalogedUpcResponse);
    assertNotNull(itemCatalogUpdateLog.getItemNumber());
    assertNotNull(itemCatalogUpdateLog.getDeliveryNumber());
    assertNotNull(itemCatalogUpdateLog.getNewItemUPC());

    verify(rdcManagedConfig, times(1)).getNgrBaseUrl();
    verify(configUtils, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_GDM);
    verify(configUtils, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_MDM);
    verify(deliveryService, times(1))
        .updateVendorUPC(anyString(), anyLong(), anyString(), any(HttpHeaders.class));
    verify(itemCatalogRepository, times(1)).save(any(ItemCatalogUpdateLog.class));
    verify(retryableRestConnector, times(1))
        .exchange(any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    verify(rdcAutoReceivingUtils, times(1))
        .updateCatalogInHawkeye(any(ItemCatalogUpdateRequest.class), any(HttpHeaders.class));
    verify(rdcReceivingUtils, times(1)).isNGRServicesEnabled();
  }

  @Test
  public void testHappyPathForVendorUpcUpdatetoIQS() {
    doReturn(getMockItemCacheConfig()).when(rdcManagedConfig).getNgrBaseUrl();
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
    doNothing()
        .when(deliveryService)
        .updateVendorUPC(anyString(), anyLong(), anyString(), any(HttpHeaders.class));
    when(itemCatalogRepository.save(any(ItemCatalogUpdateLog.class)))
        .thenReturn(getUpdatedItemCatalogLogResponse());
    when(itemUpdateRestApiClient.updateItem(any(ItemUpdateRequest.class), any(HttpHeaders.class)))
        .thenReturn(mockItemUpdateResponse());
    when(retryableRestConnector.exchange(
            any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(gson.toJson(getUpdatedItemCatalogLogResponse()), HttpStatus.OK));
    when(itemUpdateUtils.createItemUpdateRequest(
            any(ItemCatalogUpdateRequest.class), any(HttpHeaders.class)))
        .thenReturn(ItemUpdateRequest.builder().build());
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            RdcConstants.IS_ATLAS_AUTOMATION_RECEIVING_ENABLED,
            false))
        .thenReturn(Boolean.TRUE);
    when(rdcReceivingUtils.isNGRServicesEnabled()).thenReturn(true);
    String catalogedUpcResponse =
        rdcItemCatalogService.updateVendorUPC(itemCatalogUpdateRequest, headers);

    ItemCatalogUpdateLog itemCatalogUpdateLog =
        gson.fromJson(catalogedUpcResponse, ItemCatalogUpdateLog.class);

    assertNotNull(catalogedUpcResponse);
    assertNotNull(itemCatalogUpdateLog.getItemNumber());
    assertNotNull(itemCatalogUpdateLog.getDeliveryNumber());
    assertNotNull(itemCatalogUpdateLog.getNewItemUPC());

    verify(rdcManagedConfig, times(1)).getNgrBaseUrl();
    verify(configUtils, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_GDM);
    verify(configUtils, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_MDM);
    verify(configUtils, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), ReceivingConstants.IQS_ITEM_UPDATE_ENABLED);
    verify(deliveryService, times(0))
        .updateVendorUPC(anyString(), anyLong(), anyString(), any(HttpHeaders.class));
    verify(itemCatalogRepository, times(1)).save(any(ItemCatalogUpdateLog.class));
    verify(itemUpdateRestApiClient, times(1))
        .updateItem(any(ItemUpdateRequest.class), any(HttpHeaders.class));
    verify(retryableRestConnector, times(1))
        .exchange(any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    verify(itemUpdateUtils, times(1))
        .createItemUpdateRequest(any(ItemCatalogUpdateRequest.class), any(HttpHeaders.class));
    verify(itemUpdateRestApiClient, times(1))
        .updateItem(any(ItemUpdateRequest.class), any(HttpHeaders.class));
    verify(rdcReceivingUtils, times(3)).isNGRServicesEnabled();
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testHappyPathForVendorUpcUpdateIQSException() {
    doReturn(getMockItemCacheConfig()).when(rdcManagedConfig).getNgrBaseUrl();
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
    doThrow(mockReceivingBadDataException())
        .when(itemUpdateRestApiClient)
        .updateItem(any(ItemUpdateRequest.class), any(HttpHeaders.class));
    when(retryableRestConnector.exchange(
            any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(gson.toJson(getUpdatedItemCatalogLogResponse()), HttpStatus.OK));
    when(itemUpdateUtils.createItemUpdateRequest(
            any(ItemCatalogUpdateRequest.class), any(HttpHeaders.class)))
        .thenReturn(ItemUpdateRequest.builder().build());

    String catalogedUpcResponse =
        rdcItemCatalogService.updateVendorUPC(itemCatalogUpdateRequest, headers);
    ItemCatalogUpdateLog itemCatalogUpdateLog =
        gson.fromJson(catalogedUpcResponse, ItemCatalogUpdateLog.class);

    assertNotNull(catalogedUpcResponse);
    assertNotNull(itemCatalogUpdateLog.getItemNumber());
    assertNotNull(itemCatalogUpdateLog.getDeliveryNumber());
    assertNotNull(itemCatalogUpdateLog.getNewItemUPC());

    verify(rdcManagedConfig, times(0)).getNgrBaseUrl();
    verify(configUtils, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_GDM);
    verify(configUtils, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_MDM);
    verify(configUtils, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), ReceivingConstants.IQS_ITEM_UPDATE_ENABLED);
    verify(deliveryService, times(0))
        .updateVendorUPC(anyString(), anyLong(), anyString(), any(HttpHeaders.class));
    verify(itemCatalogRepository, times(0)).save(any(ItemCatalogUpdateLog.class));
    verify(itemUpdateRestApiClient, times(1))
        .updateItem(any(ItemUpdateRequest.class), any(HttpHeaders.class));
    verify(retryableRestConnector, times(1))
        .exchange(any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
  }

  @Test(expectedExceptions = ReceivingInternalException.class)
  public void testVendorUpcUpdateThrowsExceptionWhenBaseUrlInCCMIsEmptyOrNull() {
    doReturn(null).when(rdcManagedConfig).getNgrBaseUrl();
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
    when(itemCatalogRepository.save(any(ItemCatalogUpdateLog.class)))
        .thenReturn(getUpdatedItemCatalogLogResponse());
    when(retryableRestConnector.exchange(
            any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(gson.toJson(getUpdatedItemCatalogLogResponse()), HttpStatus.OK));
    when(rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(anyLong(), anyLong()))
        .thenReturn(false);
    when(rdcReceivingUtils.isNGRServicesEnabled()).thenReturn(true);

    String catalogedUpcResponse =
        rdcItemCatalogService.updateVendorUPC(itemCatalogUpdateRequest, headers);

    ItemCatalogUpdateLog itemCatalogUpdateLog =
        gson.fromJson(catalogedUpcResponse, ItemCatalogUpdateLog.class);

    verify(configUtils, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_GDM);
    verify(configUtils, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_MDM);
    verify(deliveryService, times(1))
        .updateVendorUPC(anyString(), anyLong(), anyString(), any(HttpHeaders.class));
    verify(itemCatalogRepository, times(1)).save(any(ItemCatalogUpdateLog.class));
    verify(rdcManagedConfig, times(1)).getNgrBaseUrl();
    verify(retryableRestConnector, times(0))
        .exchange(any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    verify(rdcReceivingUtils, times(1)).isNGRServicesEnabled();
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "Invalid request for upc update, delivery = 87654321, item = 567898765L")
  public void testVendorUpcUpdateThrowsExceptionWhenUpcIsNotFoundInGDM() {
    doReturn(getMockItemCacheConfig()).when(rdcManagedConfig).getNgrBaseUrl();
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
    when(itemCatalogRepository.save(any(ItemCatalogUpdateLog.class)))
        .thenReturn(getUpdatedItemCatalogLogResponse());
    when(retryableRestConnector.exchange(
            any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(gson.toJson(getUpdatedItemCatalogLogResponse()), HttpStatus.OK));

    String catalogedUpcResponse =
        rdcItemCatalogService.updateVendorUPC(itemCatalogUpdateRequest, headers);

    verify(configUtils, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_GDM);
    verify(configUtils, times(0))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_MDM);
    verify(deliveryService, times(1))
        .updateVendorUPC(anyString(), anyLong(), anyString(), any(HttpHeaders.class));
    verify(itemCatalogRepository, times(0)).save(any(ItemCatalogUpdateLog.class));
    verify(rdcManagedConfig, times(1)).getNgrBaseUrl();
    verify(retryableRestConnector, times(1))
        .exchange(any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "Received an invalid UPC update response from NGR_RECEIVING_LOAD for catalogGTIN = 20000943037194, item = 567898765")
  public void testVendorUpcUpdateThrowsExceptionWhenNGRUpdateUPCServiceReturnsEmptyResponse() {
    doReturn(getMockItemCacheConfig()).when(rdcManagedConfig).getNgrBaseUrl();
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
    when(itemCatalogRepository.save(any(ItemCatalogUpdateLog.class)))
        .thenReturn(getUpdatedItemCatalogLogResponse());
    when(retryableRestConnector.exchange(
            any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));
    when(rdcReceivingUtils.isNGRServicesEnabled()).thenReturn(true);

    String catalogedUpcResponse =
        rdcItemCatalogService.updateVendorUPC(itemCatalogUpdateRequest, headers);

    verify(configUtils, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_GDM);
    verify(configUtils, times(0))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_MDM);
    verify(deliveryService, times(1))
        .updateVendorUPC(anyString(), anyLong(), anyString(), any(HttpHeaders.class));
    verify(itemCatalogRepository, times(1)).save(any(ItemCatalogUpdateLog.class));
    verify(rdcManagedConfig, times(1)).getNgrBaseUrl();
    verify(retryableRestConnector, times(1))
        .exchange(any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    verify(rdcReceivingUtils, times(1)).isNGRServicesEnabled();
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "Invalid UPC update request to NGR_RECEIVING_LOAD for catalogGTIN = 20000943037194, item = 567898765")
  public void testVendorUpcUpdateThrowsExceptionWhenNGRFailedToUpdateUPC() {
    doReturn(getMockItemCacheConfig()).when(rdcManagedConfig).getNgrBaseUrl();
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
    when(itemCatalogRepository.save(any(ItemCatalogUpdateLog.class)))
        .thenReturn(getUpdatedItemCatalogLogResponse());
    when(retryableRestConnector.exchange(
            any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenThrow(
            new RestClientResponseException(
                "Some error.",
                HttpStatus.BAD_REQUEST.value(),
                "",
                null,
                "".getBytes(),
                Charset.forName("UTF-8")));
    when(rdcReceivingUtils.isNGRServicesEnabled()).thenReturn(true);
    String catalogedUpcResponse =
        rdcItemCatalogService.updateVendorUPC(itemCatalogUpdateRequest, headers);

    verify(configUtils, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_GDM);
    verify(configUtils, times(0))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_MDM);
    verify(deliveryService, times(1))
        .updateVendorUPC(anyString(), anyLong(), anyString(), any(HttpHeaders.class));
    verify(itemCatalogRepository, times(1)).save(any(ItemCatalogUpdateLog.class));
    verify(rdcManagedConfig, times(1)).getNgrBaseUrl();
    verify(retryableRestConnector, times(1))
        .exchange(any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    verify(rdcReceivingUtils, times(1)).isNGRServicesEnabled();
  }

  @Test(
      expectedExceptions = ReceivingBadDataException.class,
      expectedExceptionsMessageRegExp =
          "Receiving load service is down. Error message = Service not available")
  public void testVendorUpcUpdateThrowsExceptionWhenNGRServiceIsDown() {
    doReturn(getMockItemCacheConfig()).when(rdcManagedConfig).getNgrBaseUrl();
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
    when(itemCatalogRepository.save(any(ItemCatalogUpdateLog.class)))
        .thenReturn(getUpdatedItemCatalogLogResponse());
    when(retryableRestConnector.exchange(
            any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenThrow(new ResourceAccessException("Service not available"));
    when(rdcReceivingUtils.isNGRServicesEnabled()).thenReturn(true);

    String catalogedUpcResponse =
        rdcItemCatalogService.updateVendorUPC(itemCatalogUpdateRequest, headers);

    verify(configUtils, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_GDM);
    verify(configUtils, times(0))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_MDM);
    verify(deliveryService, times(1))
        .updateVendorUPC(anyString(), anyLong(), anyString(), any(HttpHeaders.class));
    verify(itemCatalogRepository, times(0)).save(any(ItemCatalogUpdateLog.class));
    verify(rdcManagedConfig, times(0)).getNgrBaseUrl();
    verify(retryableRestConnector, times(1))
        .exchange(any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    verify(rdcReceivingUtils, times(1)).isNGRServicesEnabled();
  }

  @Test
  public void testHappyPathForVendorUpcUpdateWithNGRTenantMappingEnabledForLowersTesting() {
    TenantContext.setFacilityNum(6020);
    doReturn(getMockItemCacheConfigWithMappedTenant()).when(rdcManagedConfig).getNgrBaseUrl();
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_GDM))
        .thenReturn(true);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_MDM))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), ReceivingConstants.IQS_ITEM_UPDATE_ENABLED))
        .thenReturn(false);
    doNothing()
        .when(deliveryService)
        .updateVendorUPC(anyString(), anyLong(), anyString(), any(HttpHeaders.class));
    when(itemCatalogRepository.save(any(ItemCatalogUpdateLog.class)))
        .thenReturn(getUpdatedItemCatalogLogResponse());
    when(retryableRestConnector.exchange(
            any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(gson.toJson(getUpdatedItemCatalogLogResponse()), HttpStatus.OK));
    when(rdcReceivingUtils.isNGRServicesEnabled()).thenReturn(true);
    String catalogedUpcResponse =
        rdcItemCatalogService.updateVendorUPC(itemCatalogUpdateRequest, headers);

    ItemCatalogUpdateLog itemCatalogUpdateLog =
        gson.fromJson(catalogedUpcResponse, ItemCatalogUpdateLog.class);

    assertNotNull(catalogedUpcResponse);
    assertNotNull(itemCatalogUpdateLog.getItemNumber());
    assertNotNull(itemCatalogUpdateLog.getDeliveryNumber());
    assertNotNull(itemCatalogUpdateLog.getNewItemUPC());

    verify(rdcManagedConfig, times(1)).getNgrBaseUrl();
    verify(configUtils, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_GDM);
    verify(configUtils, times(1))
        .getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_MDM);
    verify(deliveryService, times(1))
        .updateVendorUPC(anyString(), anyLong(), anyString(), any(HttpHeaders.class));
    verify(itemCatalogRepository, times(1)).save(any(ItemCatalogUpdateLog.class));
    verify(retryableRestConnector, times(1))
        .exchange(any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    verify(tenantSpecificConfigReader, times(2))
        .getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean());
    verify(rdcReceivingUtils, times(2)).isNGRServicesEnabled();
  }

  @Test
  public void testVendorUpcUpdateIgnore_success() {
    TenantContext.setFacilityNum(6020);
    doReturn(getMockItemCacheConfigWithMappedTenant()).when(rdcManagedConfig).getNgrBaseUrl();
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_GDM))
        .thenReturn(true);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_MDM))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), ReceivingConstants.IQS_ITEM_UPDATE_ENABLED))
        .thenReturn(false);
    doNothing()
        .when(deliveryService)
        .updateVendorUPC(anyString(), anyLong(), anyString(), any(HttpHeaders.class));
    when(itemCatalogRepository.save(any(ItemCatalogUpdateLog.class)))
        .thenReturn(getUpdatedItemCatalogLogResponse());
    when(retryableRestConnector.exchange(
            any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(gson.toJson(getUpdatedItemCatalogLogResponse()), HttpStatus.OK));
    when(rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(anyLong(), anyLong()))
        .thenReturn(false);
    rdcItemCatalogService.updateVendorUPC(itemCatalogUpdateRequest, headers);
  }

  @Test
  public void testVendorUpcUpdateIgnore_fail() {
    TenantContext.setFacilityNum(6020);
    doReturn(getMockItemCacheConfigWithMappedTenant()).when(rdcManagedConfig).getNgrBaseUrl();
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_GDM))
        .thenReturn(true);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.ITEM_CATALOG_ENABLED_FOR_MDM))
        .thenReturn(false);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), ReceivingConstants.IQS_ITEM_UPDATE_ENABLED))
        .thenReturn(false);
    doNothing()
        .when(deliveryService)
        .updateVendorUPC(anyString(), anyLong(), anyString(), any(HttpHeaders.class));
    when(itemCatalogRepository.save(any(ItemCatalogUpdateLog.class)))
        .thenReturn(getUpdatedItemCatalogLogResponse());
    when(retryableRestConnector.exchange(
            any(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            new ResponseEntity<>(gson.toJson(getUpdatedItemCatalogLogResponse()), HttpStatus.OK));
    when(rdcReceivingUtils.isPilotDeliveryItemEnabledForAutomation(anyLong(), anyLong()))
        .thenReturn(false);
    String actual = rdcItemCatalogService.updateVendorUPC(itemCatalogUpdateRequest, headers);
    assertNotNull(actual);
  }

  public ItemCatalogUpdateLog getUpdatedItemCatalogLogResponse() {
    return ItemCatalogUpdateLog.builder()
        .deliveryNumber(87654321L)
        .itemNumber(567898765L)
        .newItemUPC("20000943037194")
        .build();
  }

  private String getMockItemCacheConfig() {
    JsonObject mockItemCacheConfig = new JsonObject();
    mockItemCacheConfig.addProperty("32818", "http://nimservices.s%s.us.wal-mart.com:7099");
    return mockItemCacheConfig.toString();
  }

  private String getMockItemCacheConfigWithMappedTenant() {
    JsonObject mockItemCacheConfig = new JsonObject();
    mockItemCacheConfig.addProperty("6020", "http://nimservices.s32698.us.wal-mart.com:7099");
    return mockItemCacheConfig.toString();
  }

  private ReceivingBadDataException mockReceivingBadDataException() {
    return new ReceivingBadDataException("Some error.", "Error-500");
  }

  private ItemUpdateResponse mockItemUpdateResponse() {
    return ItemUpdateResponse.builder()
        .country("us")
        .division("WM")
        .node("atlas")
        .statusMessage("SUCCESS")
        .build();
  }
}
