package com.walmart.move.nim.receiving.core.common;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.mock.data.MockItemUpdateData;
import com.walmart.move.nim.receiving.core.model.ItemCatalogUpdateRequest;
import com.walmart.move.nim.receiving.core.model.gdm.v2.VendorComplianceRequestDates;
import com.walmart.move.nim.receiving.core.model.itemupdate.ItemUpdateRequest;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Date;
import org.mockito.*;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ItemUpdateUtilsTest {
  @InjectMocks private ItemUpdateUtils itemUpdateUtils;
  @Mock AppConfig appConfig;
  @Mock TenantSpecificConfigReader configReader;

  String itemNumber = "23456913";

  @BeforeMethod
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setCorrelationId("32324-434232");
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32818);
  }

  @AfterMethod
  public void resetMocks() {
    reset(appConfig, configReader);
  }

  @Test
  public void testCreateItemUpdateRequest() {
    ItemCatalogUpdateRequest itemCatalogUpdateRequest =
        MockItemUpdateData.getItemCatalogUpdateRequest();
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    ItemUpdateRequest itemUpdateRequest =
        itemUpdateUtils.createItemUpdateRequest(itemCatalogUpdateRequest, headers);
    assertEquals(
        itemCatalogUpdateRequest.getNewItemUPC(),
        itemUpdateRequest.getContent().getDcGtinAttributeList().get(0).getGtin());
    assertEquals(itemCatalogUpdateRequest.getItemNumber(), itemUpdateRequest.getItemNbr());
    assertNull(itemUpdateRequest.getContent().getLithiumIonVerifiedOn());
    assertNull(itemUpdateRequest.getContent().getLimitedQuantityLTD());
  }

  @Test
  public void testCreateVendorComplianceItemUpdateRequest_LithiumIon() {
    VendorComplianceRequestDates vendorComplianceRequestDates = getVendorComplianceRequestDates();
    vendorComplianceRequestDates.setLithiumIonVerifiedOn(
        ReceivingUtils.dateConversionToUTC(new Date()));
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    ItemUpdateRequest itemUpdateRequest =
        itemUpdateUtils.createVendorComplianceItemUpdateRequest(
            vendorComplianceRequestDates, itemNumber, headers);
    assertNotNull(
        itemUpdateRequest.getContent().getLithiumIonVerifiedOn(),
        "Lithium verified date should be set");
    assertNull(
        itemUpdateRequest.getContent().getLimitedQuantityLTD(),
        "Limited qty verified date should not be set");
  }

  @Test
  public void testCreateVendorComplianceItemUpdateRequest_LimitedQty() {
    VendorComplianceRequestDates vendorComplianceRequestDates = getVendorComplianceRequestDates();
    vendorComplianceRequestDates.setLimitedQtyVerifiedOn(
        ReceivingUtils.dateConversionToUTC(new Date()));
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    ItemUpdateRequest itemUpdateRequest =
        itemUpdateUtils.createVendorComplianceItemUpdateRequest(
            vendorComplianceRequestDates, itemNumber, headers);
    assertNull(
        itemUpdateRequest.getContent().getLithiumIonVerifiedOn(),
        "Lithium verified date should be set");
    assertNotNull(
        itemUpdateRequest.getContent().getLimitedQuantityLTD(),
        "Limited qty verified date should not be set");
  }

  @Test
  public void testCreateVendorComplianceItemUpdateRequest_LithiumIonAndLimitedQty() {
    VendorComplianceRequestDates vendorComplianceRequestDates = getVendorComplianceRequestDates();
    vendorComplianceRequestDates.setLimitedQtyVerifiedOn(
        ReceivingUtils.dateConversionToUTC(new Date()));
    vendorComplianceRequestDates.setLithiumIonVerifiedOn(
        ReceivingUtils.dateConversionToUTC(new Date()));
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    ItemUpdateRequest itemUpdateRequest =
        itemUpdateUtils.createVendorComplianceItemUpdateRequest(
            vendorComplianceRequestDates, itemNumber, headers);
    assertNotNull(
        itemUpdateRequest.getContent().getLithiumIonVerifiedOn(),
        "Lithium verified date should be set");
    assertNotNull(
        itemUpdateRequest.getContent().getLimitedQuantityLTD(),
        "Limited qty verified date should not be set");
  }

  @Test
  public void testGetIqsItemUpdateHeaders() {
    when(appConfig.getIqsChannelType()).thenReturn("234fcvgty7u");
    when(appConfig.getIqsServiceEnv()).thenReturn("tst");
    HttpHeaders headers = itemUpdateUtils.getIqsItemUpdateHeaders(MockHttpHeaders.getHeaders());
    assertEquals(
        headers.getFirst(ReceivingConstants.IQS_SVC_KEY), ReceivingConstants.IQS_SVC_VALUE);
    assertEquals(
        headers.getFirst(ReceivingConstants.IQS_CONSUMER_ID_KEY),
        appConfig.getReceivingConsumerId());
    assertEquals(headers.getFirst(ReceivingConstants.IQS_SVC_ENV_KEY), "tst");
  }

  @Test
  public void testGetBaseItemUpdateRequestWhenItemUpdateTestEnabled() {
    when(configReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IQS_ITEM_UPDATE_TEST_ENABLED,
            false))
        .thenReturn(true);
    ItemUpdateRequest itemUpdateRequest = itemUpdateUtils.getBaseItemUpdateRequest();
    assertNotNull(itemUpdateRequest);
    assertEquals(
        itemUpdateRequest.getFacilityNumber(), ReceivingConstants.IQS_TEST_FACILITY_NUMBER);
  }

  @Test
  public void testGetBaseItemUpdateRequestWhenItemUpdateTestDisabled() {
    when(configReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IQS_ITEM_UPDATE_TEST_ENABLED,
            false))
        .thenReturn(false);
    ItemUpdateRequest itemUpdateRequest = itemUpdateUtils.getBaseItemUpdateRequest();
    assertNotNull(itemUpdateRequest);
    assertEquals(itemUpdateRequest.getFacilityNumber().intValue(), 32818);
  }

  @Test
  public void testCreateVendorComplianceItemUpdateRequest_Hazmat() {
    VendorComplianceRequestDates vendorComplianceRequestDates = getVendorComplianceRequestDates();
    vendorComplianceRequestDates.setHazmatVerifiedOn(
        ReceivingUtils.dateConversionToUTC(new Date()));
    HttpHeaders headers = MockHttpHeaders.getHeaders();
    ItemUpdateRequest itemUpdateRequest =
        itemUpdateUtils.createVendorComplianceItemUpdateRequest(
            vendorComplianceRequestDates, itemNumber, headers);
    assertNotNull(
        itemUpdateRequest.getContent().getHazmatVerifiedOn(), "Hazmat verified date should be set");
  }

  private VendorComplianceRequestDates getVendorComplianceRequestDates() {
    VendorComplianceRequestDates vendorComplianceRequestDates = new VendorComplianceRequestDates();
    return vendorComplianceRequestDates;
  }
}
