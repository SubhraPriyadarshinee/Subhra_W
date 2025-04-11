package com.walmart.move.nim.receiving.core.common;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.config.ReportConfig;
import com.walmart.move.nim.receiving.core.config.TenantSpecificBackendConfig;
import com.walmart.move.nim.receiving.data.MockFeatureFlags;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@ActiveProfiles("test")
public class TenantSpecificConfigReaderTest extends AbstractTestNGSpringContextTests {

  @InjectMocks private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private TenantSpecificBackendConfig tenantSpecificBackendConfig;
  @Mock private MarketConfigHelper marketConfigHelper;
  @Mock private ReportConfig reportConfig;
  @Mock private AppConfig appConfig;
  private Gson gson = new Gson();

  private final List<Integer> facilityNumList = new ArrayList<>();
  private final List<Integer> facilityNumListForItemCatalog = new ArrayList<>();
  private final List<String> facilityCountryCodeList =
      new ArrayList<>(); // TODO Change when added to config

  final String validTenantSpecificData =
      "{\n"
          + "    \"32612\": {\n"
          + "        \"printLabelTtlHrs\":168,\n"
          + "         \"smBaseUrl\":\"https://nimservices.s32612.us.wal-mart.com:7100/securityMgmt/us/32612\",\n"
          + "        \"whiteWoodPalletMaxWeight\":2100.0\n"
          + "    },\n"
          + "    \"8852\": {\n"
          + "        \"smBaseUrl\":\"https://nimservices.s08852.us.wal-mart.com:7100/securityMgmt/us/08852\"\n"
          + "    },    \n"
          + "    \"default\": {\n"
          + "        \"printLabelTtlHrs\":72,\n"
          + "        \"whiteWoodPalletMaxWeight\":2100.0\n"
          + "    }\n"
          + "}";

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    facilityNumList.add(6938);
    facilityNumList.add(6561);
    facilityNumList.add(32987);
    facilityNumList.add(32898);
    facilityNumList.add(32899);
    facilityNumList.add(32612);
    facilityNumListForItemCatalog.add(6561);
    facilityNumListForItemCatalog.add(32818);
    facilityCountryCodeList.add("US");
    ReflectionTestUtils.setField(tenantSpecificConfigReader, "gson", gson);
  }

  @AfterMethod
  public void tearDown() {
    reset(tenantSpecificBackendConfig, reportConfig, appConfig);
    System.clearProperty(ONEOPS_ENVIRONMENT);
    TenantContext.clear();
  }

  @Test
  public void testGetFeatureFlagsForWitronFacility() throws ReceivingException {
    when(tenantSpecificBackendConfig.getFeatureFlags()).thenReturn(MockFeatureFlags.VALID_DATA);

    String expectedJson = "{\"putawayHandler\":\"WitronPutawayHandler\"}";

    JsonObject response = tenantSpecificConfigReader.getFeatureFlagsByFacility("99999");

    assertEquals(expectedJson, response.toString());
  }

  @Test
  public void testGetEmailIdListByTenant() throws ReceivingException {
    when(reportConfig.getTenantOpsEmailRecipients()).thenReturn(MockFeatureFlags.TENANT_EMAIL_DATA);

    String expectedList = "[robin.kumar@walmart.com]";

    List<String> emailList = tenantSpecificConfigReader.getEmailIdListByTenant("6020");

    assertEquals(expectedList, emailList.toString());
  }

  @Test
  public void testGetEmailIdListByTenantDefault() throws ReceivingException {
    when(reportConfig.getTenantOpsEmailRecipients()).thenReturn(MockFeatureFlags.TENANT_EMAIL_DATA);

    String expectedList = "[robin.kumar@walmart.com]";

    List<String> emailList = tenantSpecificConfigReader.getEmailIdListByTenant("");

    assertEquals(expectedList, emailList.toString());
  }

  @Test
  public void testGetFeatureFlagsForMCCFacility() throws ReceivingException {
    when(tenantSpecificBackendConfig.getFeatureFlags()).thenReturn(MockFeatureFlags.VALID_DATA);

    String expectedJson = "{\"putawayHandler\":\"DefaultPutawayHandler\"}";

    JsonObject response = tenantSpecificConfigReader.getFeatureFlagsByFacility("6938");

    assertEquals(expectedJson, response.toString());
  }

  @Test
  public void testGetFeatureFlagsForDefaultFacility() throws ReceivingException {
    when(tenantSpecificBackendConfig.getFeatureFlags()).thenReturn(MockFeatureFlags.VALID_DATA);

    String expectedJson = "{\"putawayHandler\":\"DefaultPutawayHandler\"}";

    JsonObject response = tenantSpecificConfigReader.getFeatureFlagsByFacility("1111");

    assertEquals(expectedJson, response.toString());
  }

  @Test
  public void testGetFeatureFlagsWithNull() {
    when(tenantSpecificBackendConfig.getFeatureFlags()).thenReturn(null);
    try {
      tenantSpecificConfigReader.getFeatureFlagsByFacility("99999");
    } catch (ReceivingException e) {
      assertEquals(ReceivingException.TENANT_CONFIG_ERROR, e.getMessage());
    }
  }

  @Test
  public void testGetFeatureFlagsWithInvalidJson() {
    when(tenantSpecificBackendConfig.getFeatureFlags()).thenReturn(MockFeatureFlags.INVALID_DATA);
    try {
      tenantSpecificConfigReader.getFeatureFlagsByFacility("99999");
    } catch (ReceivingException e) {
      assertEquals(ReceivingException.TENANT_CONFIG_ERROR, e.getMessage());
    }
  }

  @Test
  public void testGetFeatureFlagsWithoutDefault() {
    when(tenantSpecificBackendConfig.getFeatureFlags())
        .thenReturn(MockFeatureFlags.MISSING_DEFAULT_DATA);
    try {
      tenantSpecificConfigReader.getFeatureFlagsByFacility("88888");
    } catch (ReceivingException e) {
      assertEquals(ReceivingException.TENANT_CONFIG_ERROR, e.getMessage());
    }
  }

  @Test
  public void testGetFacilityNumListForItemCatalog() {
    when(tenantSpecificBackendConfig.getFeatureFlags())
        .thenReturn(MockFeatureFlags.VALID_DATA_ITEM_CATALOG);
    assertEquals(
        tenantSpecificConfigReader.getEnabledFacilityNumListForFeature(
            ReceivingConstants.ITEM_CATALOG_ENABLED),
        facilityNumListForItemCatalog);
  }

  @Test
  public void testGetMissingFacilityNumList() {
    when(tenantSpecificBackendConfig.getFeatureFlags())
        .thenReturn(MockFeatureFlags.VALID_DATA_ITEM_CATALOG);
    assertEquals(
        tenantSpecificConfigReader.getMissingFacilityNumList(
            Arrays.asList(6561), ReceivingConstants.ITEM_CATALOG_ENABLED),
        Arrays.asList(32818));
  }

  @Test
  public void testIsPoConfirmationFlagEnabled() throws ReceivingException {
    when(tenantSpecificBackendConfig.getFeatureFlags())
        .thenReturn(MockFeatureFlags.VALID_DATA_PO_CONFIRMATION);

    assertTrue(tenantSpecificConfigReader.isPoConfirmationFlagEnabled(32612));
    assertFalse(tenantSpecificConfigReader.isPoConfirmationFlagEnabled(6561));
    assertFalse(tenantSpecificConfigReader.isPoConfirmationFlagEnabled(32861));
  }

  @Test
  public void testUseFbqInCbr() throws ReceivingException {
    when(tenantSpecificBackendConfig.getFeatureFlags())
        .thenReturn(MockFeatureFlags.VALID_DATA_USE_FBQ_IN_CBR);

    assertTrue(tenantSpecificConfigReader.useFbqInCbr(32623));
    assertFalse(tenantSpecificConfigReader.useFbqInCbr(6561));
    assertFalse(tenantSpecificConfigReader.useFbqInCbr(32861));
  }

  @Test
  public void testIsDeliveryItemOverrideEnabled() throws ReceivingException {
    when(tenantSpecificBackendConfig.getFeatureFlags())
        .thenReturn(MockFeatureFlags.DELIVERY_ITEM_OVERRIDE_FLAG);

    assertTrue(tenantSpecificConfigReader.isDeliveryItemOverrideEnabled(32612));
    assertFalse(tenantSpecificConfigReader.isDeliveryItemOverrideEnabled(32987));
  }

  @Test
  public void testIsEnableBOLWeightCheckEnabled() throws ReceivingException {
    when(tenantSpecificBackendConfig.getFeatureFlags())
        .thenReturn(MockFeatureFlags.VALID_ENABLE_BOL_WEIGHT_CHECK);

    assertTrue(tenantSpecificConfigReader.isBOLWeightCheckEnabled(32612));
    assertFalse(tenantSpecificConfigReader.isBOLWeightCheckEnabled(32987));
  }

  @Test
  public void testFeatureFlagPresentInDefaultOnly() {
    when(tenantSpecificBackendConfig.getFeatureFlags()).thenReturn(MockFeatureFlags.FEATURE_FLAG);
    boolean featureFlag =
        tenantSpecificConfigReader.getConfiguredFeatureFlag("32897", "enablePublishDCFin");
    assertTrue(featureFlag);
  }

  @Test
  public void testFeatureFlagOverriddenInDc() {
    when(tenantSpecificBackendConfig.getFeatureFlags()).thenReturn(MockFeatureFlags.FEATURE_FLAG);
    boolean featureFlag =
        tenantSpecificConfigReader.getConfiguredFeatureFlag("32897", "enableWfmPublishInstruction");
    assertFalse(featureFlag);
  }

  @Test
  public void testDcTimeZone() {
    when(tenantSpecificBackendConfig.getFeatureFlags())
        .thenReturn(MockFeatureFlags.DELIVERY_ITEM_OVERRIDE_FLAG);

    String dcTimeZone = tenantSpecificConfigReader.getDCTimeZone(32612);
    assertEquals("US/Central", dcTimeZone);

    dcTimeZone = tenantSpecificConfigReader.getDCTimeZone(1000);
    assertEquals("UTC", dcTimeZone);
  }

  @Test
  public void testGetDCSpecificMoveDestinationForNonConDockTag() {
    when(tenantSpecificBackendConfig.getFeatureFlags()).thenReturn(MockFeatureFlags.FEATURE_FLAG);

    String moveLoc = tenantSpecificConfigReader.getDCSpecificMoveDestinationForNonConDockTag(32897);
    assertEquals(moveLoc, "PSN");

    moveLoc = tenantSpecificConfigReader.getDCSpecificMoveDestinationForNonConDockTag(1000);
    assertEquals(moveLoc, "");
  }

  @Test
  public void testGetDCSpecificMoveDestinationForNonConDockTagError() {
    when(tenantSpecificBackendConfig.getFeatureFlags())
        .thenReturn(MockFeatureFlags.FEATURE_FLAG_INVALID_SYNTAX);

    String moveLoc = tenantSpecificConfigReader.getDCSpecificMoveDestinationForNonConDockTag(1000);
    assertEquals(moveLoc, "");
  }

  @Test
  public void testGetDCSpecificMoveFloorLineDestinationForNonConDockTag() {
    when(tenantSpecificBackendConfig.getFeatureFlags()).thenReturn(MockFeatureFlags.FEATURE_FLAG);

    String moveLoc =
        tenantSpecificConfigReader.getDCSpecificMoveFloorLineDestinationForNonConDockTag(32897);
    assertEquals(moveLoc, "EFLCP08");

    moveLoc = tenantSpecificConfigReader.getDCSpecificMoveDestinationForNonConDockTag(1000);
    assertEquals(moveLoc, "");
  }

  @Test
  public void testGetDCSpecificMoveFloorLineDestinationForNonConDockTagError() {
    when(tenantSpecificBackendConfig.getFeatureFlags())
        .thenReturn(MockFeatureFlags.FEATURE_FLAG_INVALID_SYNTAX);

    String moveLoc =
        tenantSpecificConfigReader.getDCSpecificMoveFloorLineDestinationForNonConDockTag(1000);
    assertEquals(moveLoc, "");
  }

  @Test
  public void test_getPrintLabelTtlHrs_ForSpecificTenant() {
    when(tenantSpecificBackendConfig.getFeatureFlags()).thenReturn(validTenantSpecificData);
    int ttl_for_Tenant_32612 =
        tenantSpecificConfigReader.getPrintLabelTtlHrs(32612, PRINT_LABEL_TTL_CONFIG_KEY);
    assertEquals(ttl_for_Tenant_32612, 168);
  }

  @Test
  public void test_getPrintLabelTtlHrs_ForDefaultConfig() {
    when(tenantSpecificBackendConfig.getFeatureFlags()).thenReturn(validTenantSpecificData);
    int ttl_for_Tenant_9999 =
        tenantSpecificConfigReader.getPrintLabelTtlHrs(9999, PRINT_LABEL_TTL_CONFIG_KEY);
    assertEquals(ttl_for_Tenant_9999, 72);
  }

  @Test
  public void test_getPrintLabelTtlHrs_whenError() {
    when(tenantSpecificBackendConfig.getFeatureFlags())
        .thenReturn(MockFeatureFlags.FEATURE_FLAG_INVALID_SYNTAX);
    int ttl_for_Tenant_9999 =
        tenantSpecificConfigReader.getPrintLabelTtlHrs(9999, PRINT_LABEL_TTL_CONFIG_KEY);
    assertEquals(ttl_for_Tenant_9999, 72);
  }

  @Test
  public void test_getSmBaseUrl_ForSpecificTenant() {
    when(tenantSpecificBackendConfig.getFeatureFlags()).thenReturn(validTenantSpecificData);
    final String tenantSpecificSmBaseUrlFor32612 =
        "https://nimservices.s32612.us.wal-mart.com:7100/securityMgmt/us/32612";
    String for_Tenant_32612 = tenantSpecificConfigReader.getSmBaseUrl(32612, "32612");
    assertEquals(for_Tenant_32612, tenantSpecificSmBaseUrlFor32612);
  }

  @Test
  public void test_getSmBaseUrl_ForSpecificTenant_08852() {
    when(tenantSpecificBackendConfig.getFeatureFlags()).thenReturn(validTenantSpecificData);
    final String tenantSpecificSmBaseUrlFor08852 =
        "https://nimservices.s08852.us.wal-mart.com:7100/securityMgmt/us/08852";
    String for_Tenant_08852 = tenantSpecificConfigReader.getSmBaseUrl(8852, "08852");
    assertEquals(for_Tenant_08852, tenantSpecificSmBaseUrlFor08852);
  }

  @Test
  public void test_getSmBaseUrl_For_exception_undefined_tenant() {
    when(tenantSpecificBackendConfig.getFeatureFlags()).thenReturn(validTenantSpecificData);
    final String tenantSpecificSmBaseUrlForDefault =
        "https://nimservices.s12345.us.wal-mart.com:7100/securityMgmt/us/12345";
    String for_Tenant_undefined = tenantSpecificConfigReader.getSmBaseUrl(12345, "12345");
    assertEquals(for_Tenant_undefined, tenantSpecificSmBaseUrlForDefault);
  }

  @Test
  public void test_getSmBaseUrl_when_exception_forTenantCCM_is_Null() {
    when(tenantSpecificBackendConfig.getFeatureFlags()).thenReturn(null);
    final String tenantSpecificSmBaseUrlFor08852 =
        "https://nimservices.s08852.us.wal-mart.com:7100/securityMgmt/us/08852";
    String for_Tenant_08852 = tenantSpecificConfigReader.getSmBaseUrl(8852, "08852");
    assertEquals(for_Tenant_08852, tenantSpecificSmBaseUrlFor08852);
  }

  @Test
  public void getConfiguredFeatureFlag_fromDefaultValueWhenNotDefinedInTenantOrDefaultConfig() {
    final boolean defaultValueAsTrue =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            "32612", RECEIVE_AS_CORRECTION_FEATURE, true);
    assertTrue(defaultValueAsTrue);

    boolean defaultValueAsFalse =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            "32612", RECEIVE_AS_CORRECTION_FEATURE, false);
    assertFalse(defaultValueAsFalse);
  }

  @Test
  public void getCcmValue() {
    when(tenantSpecificBackendConfig.getFeatureFlags())
        .thenReturn(MockFeatureFlags.VALID_CCM_VALUE);
    final String ccmKey = "putawayHandler";
    final String ccmValueForTenantExpected = "WitronPutawayHandler";
    final String ccmValueForDefaultTenantExpected = "DefaultPutawayHandler";
    final String ccmValueUserGivenExpected = "new CCM Missing ccmValue so return UserGiven value";

    // Tenant specific
    final String ccmValueForTenant =
        tenantSpecificConfigReader.getCcmValue(32612, ccmKey, ccmValueUserGivenExpected);
    assertEquals(ccmValueForTenant, ccmValueForTenantExpected);

    // Default Tenant specific
    final String ccmValueForDefaultTenant =
        tenantSpecificConfigReader.getCcmValue(1234, ccmKey, ccmValueUserGivenExpected);
    assertEquals(ccmValueForDefaultTenant, ccmValueForDefaultTenantExpected);

    // Nothing exist in CCM
    final String ccmValueUserGiven =
        tenantSpecificConfigReader.getCcmValue(32612, "newKeyNotInCCM", ccmValueUserGivenExpected);
    assertEquals(ccmValueUserGiven, ccmValueUserGivenExpected);
  }

  @Test
  public void test_getWhiteWoodPalletMaxWeight_ForSpecificTenant() {
    when(tenantSpecificBackendConfig.getFeatureFlags()).thenReturn(validTenantSpecificData);
    Float ttl_for_Tenant_32612 =
        tenantSpecificConfigReader.getWhiteWoodPalletMaxWeight(32612, WHITE_WOOD_MAX_WEIGHT_KEY);
    assertEquals(ttl_for_Tenant_32612, 2100.0f);
  }

  @Test
  public void test_getWhiteWoodPalletMaxWeight_ForDefaultConfig() {
    when(tenantSpecificBackendConfig.getFeatureFlags()).thenReturn(validTenantSpecificData);
    Float ttl_for_Tenant_9999 =
        tenantSpecificConfigReader.getWhiteWoodPalletMaxWeight(9999, WHITE_WOOD_MAX_WEIGHT_KEY);
    assertEquals(ttl_for_Tenant_9999, 2100.0f);
  }

  @Test
  public void test_getWhiteWoodPalletMaxWeight_whenError() {
    when(tenantSpecificBackendConfig.getFeatureFlags())
        .thenReturn(MockFeatureFlags.FEATURE_FLAG_INVALID_SYNTAX);
    Float ttl_for_Tenant_9999 =
        tenantSpecificConfigReader.getWhiteWoodPalletMaxWeight(9999, WHITE_WOOD_MAX_WEIGHT_KEY);
    assertEquals(ttl_for_Tenant_9999, 2100.0f);
  }

  @Test
  public void testGetInventoryBaseUrlByFacilityNumber_NoFacilityNumberGiven_ReturnsBaseURL() {
    when(appConfig.getInventoryBaseUrl())
        .thenReturn("https://inventory-cell001.prod.us.walmart.net");
    when(appConfig.getMultiTenantInventoryBaseUrl()).thenReturn(getMockInventoryBaseUrl());
    String inventoryBaseUrl = tenantSpecificConfigReader.getInventoryBaseUrlByFacility();
    assertNotNull(inventoryBaseUrl);
    assertEquals(inventoryBaseUrl, "https://inventory-cell001.prod.us.walmart.net");
    verify(appConfig, times(1)).getInventoryBaseUrl();
  }

  @Test
  public void
      testGetInventoryBaseUrlByFacilityNumber_MultiTenantInventoryBaseUrlConfigIsMissingInCCM_ReturnsBaseURL() {
    TenantContext.setFacilityNum(6020);
    when(appConfig.getInventoryBaseUrl())
        .thenReturn("https://inventory-cell001.prod.us.walmart.net");
    when(appConfig.getMultiTenantInventoryBaseUrl()).thenReturn(null);
    String inventoryBaseUrl = tenantSpecificConfigReader.getInventoryBaseUrlByFacility();
    assertNotNull(inventoryBaseUrl);
    assertEquals(inventoryBaseUrl, "https://inventory-cell001.prod.us.walmart.net");
    verify(appConfig, times(1)).getInventoryBaseUrl();
  }

  @Test
  public void testGetInventoryBaseUrlByFacilityNumberHappyPathReturnsTenantSpecificURL() {
    TenantContext.setFacilityNum(6020);
    when(appConfig.getInventoryBaseUrl())
        .thenReturn("https://inventory-cell001.prod.us.walmart.net");
    when(appConfig.getMultiTenantInventoryBaseUrl()).thenReturn(getMockInventoryBaseUrl());
    String inventoryBaseUrl = tenantSpecificConfigReader.getInventoryBaseUrlByFacility();
    assertNotNull(inventoryBaseUrl);
    assertEquals(inventoryBaseUrl, "https://inventory-cell004.prod.us.walmart.net");
    verify(appConfig, times(0)).getInventoryBaseUrl();
  }

  @Test
  public void testGetInventoryBaseUrlByFacilityNumbeReturnsBaseURLWhenTenantMappingDoesNotExist() {
    TenantContext.setFacilityNum(6001);
    when(appConfig.getInventoryBaseUrl())
        .thenReturn("https://inventory-cell001.prod.us.walmart.net");
    when(appConfig.getMultiTenantInventoryBaseUrl()).thenReturn(getMockInventoryBaseUrl());
    String inventoryBaseUrl = tenantSpecificConfigReader.getInventoryBaseUrlByFacility();
    assertNotNull(inventoryBaseUrl);
    assertEquals(inventoryBaseUrl, "https://inventory-cell001.prod.us.walmart.net");
    verify(appConfig, times(1)).getInventoryBaseUrl();
  }

  private String getMockInventoryBaseUrl() {
    JsonObject mockInventoryBaseUrlConfig = new JsonObject();
    mockInventoryBaseUrlConfig.addProperty("6020", "https://inventory-cell004.prod.us.walmart.net");
    mockInventoryBaseUrlConfig.addProperty("6561", "https://inventory-cell001.prod.us.walmart.net");
    return mockInventoryBaseUrlConfig.toString();
  }

  @Test
  public void getSubcenterId_nullTest() {
    String result = tenantSpecificConfigReader.getSubcenterId();
    assertNull(result);
  }

  @Test
  public void getSubcenterId_notNullTest() {
    TenantContext.setSubcenterId(2);
    String result = tenantSpecificConfigReader.getSubcenterId();
    assertEquals(result, "2");
  }

  @Test
  public void getSubcenterId_notValidIdTest() {
    TenantContext.setSubcenterId(0);
    String result = tenantSpecificConfigReader.getSubcenterId();
    assertNull(result);
  }

  @Test
  public void getOrgUnitId_nullTest() {
    String result = tenantSpecificConfigReader.getOrgUnitId();
    assertNull(result);
  }

  @Test
  public void getOrgUnitId_notNullTest() {
    TenantContext.setOrgUnitId(2);
    String result = tenantSpecificConfigReader.getOrgUnitId();
    assertEquals(result, "2");
  }

  @Test
  public void getOrgUnitId_notValidIdTest() {
    TenantContext.setOrgUnitId(0);
    String result = tenantSpecificConfigReader.getOrgUnitId();
    assertNull(result);
  }

  @Test
  public void testMarketSpecificConfig() {
    when(tenantSpecificBackendConfig.getFeatureFlags())
        .thenReturn(MockFeatureFlags.VALID_CCM_VALUE);
    when(marketConfigHelper.getFeatureFlagsByFacility("5504", null))
        .thenReturn(MockFeatureFlags.VALID_CCM_VALUE_BY_MARKET_TYPE);
    final String ccmKey1 = "inventoryAdjustmentProcessor";
    final String ccmValueForTenantExpected1 = "mfcInventoryAdjustmentProcessor";

    final String ccmValueForTenant1 =
        tenantSpecificConfigReader.getCcmValue(5504, ccmKey1, "default");
    assertEquals(ccmValueForTenant1, ccmValueForTenantExpected1);

    final String ccmKey2 = "containerCreateProcessor";
    final String ccmValueForTenantExpected2 = "v2StoreInboundContainerCreate";

    final String ccmValueForTenant2 =
        tenantSpecificConfigReader.getCcmValue(5504, ccmKey2, "default");
    assertEquals(ccmValueForTenant2, ccmValueForTenantExpected2);
  }

  @Test
  public void testDefaultCase() {
    when(tenantSpecificBackendConfig.getFeatureFlags())
        .thenReturn(MockFeatureFlags.VALID_CCM_VALUE);
    when(marketConfigHelper.getFeatureFlagsByFacility("5504", "us")).thenReturn(null);
    final String ccmKey1 = "putawayHandler";
    final String ccmValueForTenantExpected1 = "DefaultPutawayHandler";

    final String ccmValueForTenant1 =
        tenantSpecificConfigReader.getCcmValue(5504, ccmKey1, "default");
    assertEquals(ccmValueForTenant1, ccmValueForTenantExpected1);

    final String ccmKey2 = "putOnHoldService";
    final String ccmValueForTenantExpected2 = "DefaultPutOnHoldService";

    final String ccmValueForTenant2 =
        tenantSpecificConfigReader.getCcmValue(5504, ccmKey2, "default");
    assertEquals(ccmValueForTenant2, ccmValueForTenantExpected2);
  }

  @Test
  public void testGetFreightTypes() {
    when(tenantSpecificBackendConfig.getFeatureFlags())
        .thenReturn(MockFeatureFlags.ATLAS_COMPLETE_MIGRATED_FREIGHTTYPES);
    List<String> freightSpecificType = tenantSpecificConfigReader.getFreightSpecificType(32679);
    assertNotNull(freightSpecificType);
  }

  @Test
  public void testGetSorterContractVersion() {
    when(tenantSpecificBackendConfig.getFeatureFlags()).thenReturn(MockFeatureFlags.FEATURE_FLAG);
    Integer sorterContractVersion = tenantSpecificConfigReader.getSorterContractVersion(32897);
    Assert.assertEquals(Integer.valueOf(2), sorterContractVersion);
  }

  @Test
  public void testGetSorterContractVersionNull() {
    when(tenantSpecificBackendConfig.getFeatureFlags()).thenReturn(MockFeatureFlags.FEATURE_FLAG);
    // Config doesn't exist for 32679 should return null
    Integer sorterContractVersion = tenantSpecificConfigReader.getSorterContractVersion(32679);
    Assert.assertEquals(Integer.valueOf(1), sorterContractVersion);
  }

  @Test
  public void getTenantConfigurationAsListTest() {
    TenantContext.setFacilityNum(4093);
    when(tenantSpecificBackendConfig.getFeatureFlags())
        .thenReturn(MockFeatureFlags.CCM_VALUE_WITH_LIST);
    List<String> actual =
        tenantSpecificConfigReader.getTenantConfigurationAsList(
            "tclFreeAcceptableDeliveryStatusCodes");
    assertEquals(actual.size(), 3);
  }

  @Test
  public void testGetPurchaseOrderPartitionSizeReturnsDefaultValueWhenTenantMappingDoesNotExist() {
    TenantContext.setFacilityNum(6001);
    Integer purchaseOrderPartitionSize =
        tenantSpecificConfigReader.getPurchaseOrderPartitionSize(PURCHASE_ORDER_PARTITION_SIZE_KEY);
    assertNotNull(purchaseOrderPartitionSize);
    assertEquals(purchaseOrderPartitionSize, PURCHASE_ORDER_PARTITION_SIZE);
  }

  @DataProvider(name = "vnpkPalletQtyCheckDataProvider")
  public String[][] vnpkPalletQtyCheckDataProvider() {
    return new String[][] {
            {"3006", "{\"isVnpkEqualPalletQtyCheck\":\"true\"}"},
            {"99999", "{\"isVnpkEqualPalletQtyCheck\":\"false\"}"},
            {"6666", "{\"isVnpkEqualPalletQtyCheck\":\"false\"}"}
    };
  }

  @Test(dataProvider = "vnpkPalletQtyCheckDataProvider")
  public void testIsVnpkEqualPalletQtyCheck(String facilityNum, String expectedJson) throws ReceivingException {
    when(tenantSpecificBackendConfig.getFeatureFlags()).thenReturn(MockFeatureFlags.VALID_DATA_VNPK_PALLET);
    JsonObject response = tenantSpecificConfigReader.getFeatureFlagsByFacility(facilityNum);
    assertEquals(expectedJson, response.toString());
  }

  @Test
  public void enable_vnpkPalletFeature() {
    TenantContext.setFacilityNum(3006);
    when(tenantSpecificBackendConfig.getFeatureFlags()).thenReturn(MockFeatureFlags.VALID_DATA_VNPK_PALLET);
    boolean ttl_for_Tenant_32612 =
            tenantSpecificConfigReader.getConfiguredFeatureFlag(IS_VNPK_PALLET_QTY_ENABLED);
    assertEquals(ttl_for_Tenant_32612, true );
  }

  @Test
  public void disable_vnpkPalletFeature() {
    TenantContext.setFacilityNum(6006);
    when(tenantSpecificBackendConfig.getFeatureFlags()).thenReturn(MockFeatureFlags.VALID_DATA_VNPK_PALLET);
    boolean ttl_for_Tenant_32612 =
            tenantSpecificConfigReader.getConfiguredFeatureFlag(IS_VNPK_PALLET_QTY_ENABLED);
    assertEquals(ttl_for_Tenant_32612, false );
  }

  @Test
  public void setVnpkPallet_Enabled(){
    TenantContext.setFacilityNum(3006);
    boolean vnpkFeature = tenantSpecificConfigReader.getConfiguredFeatureFlag("isVnpkEqualPalletQtyCheck");
    assertNotNull(vnpkFeature);
  }

  @Test
  public void isTCLInfoOutboxKafkaPublishEnabledReturnsTrueWhenEnabled() throws ReceivingException {
    TenantContext.setFacilityNum(4034);
    when(tenantSpecificBackendConfig.getFeatureFlags())
            .thenReturn(MockFeatureFlags.OUTBOX_PUBLISHER_CONFIG_JSON);
    boolean result = tenantSpecificConfigReader.isTCLInfoOutboxKafkaPublishEnabled(4034);

    assertTrue(result);
  }


  @Test
  public void isDivertInfoOutboxKafkaPublishEnabledReturnsTrueWhenEnabled() throws ReceivingException {
    TenantContext.setFacilityNum(4034);
    when(tenantSpecificBackendConfig.getFeatureFlags())
            .thenReturn(MockFeatureFlags.OUTBOX_PUBLISHER_CONFIG_JSON);

    boolean result = tenantSpecificConfigReader.isDivertInfoOutboxKafkaPublishEnabled(4034);

    assertTrue(result);
  }

  @Test
  public void isUnloadCompleteOutboxKafkaPublishEnabledReturnsTrueWhenEnabled() throws ReceivingException {
    TenantContext.setFacilityNum(4034);
    when(tenantSpecificBackendConfig.getFeatureFlags())
            .thenReturn(MockFeatureFlags.OUTBOX_PUBLISHER_CONFIG_JSON);

    boolean result = tenantSpecificConfigReader.isUnloadCompleteOutboxKafkaPublishEnabled(4034);

    assertTrue(result);
  }
}
