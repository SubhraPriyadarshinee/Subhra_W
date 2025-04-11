package com.walmart.move.nim.receiving.endgame.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.EACHES;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

import com.google.gson.internal.LinkedTreeMap;
import com.walmart.atlas.global.fc.service.IOutboxPublisherService;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.model.audit.AuditFlagRequest;
import com.walmart.move.nim.receiving.core.model.audit.AuditFlagRequestItem;
import com.walmart.move.nim.receiving.core.model.audit.AuditFlagResponse;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ItemDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrderLine;
import com.walmart.move.nim.receiving.core.model.gdm.v3.QuantityDetail;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Vnpk;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Whpk;
import com.walmart.move.nim.receiving.core.repositories.DeliveryMetaDataRepository;
import com.walmart.move.nim.receiving.core.service.AuditService;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import com.walmart.move.nim.receiving.core.service.WitronDeliveryMetaDataService;
import com.walmart.move.nim.receiving.endgame.common.AuditHelper;
import com.walmart.move.nim.receiving.endgame.common.EndGameUtilsTestCase;
import com.walmart.move.nim.receiving.endgame.config.EndgameManagedConfig;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class AuditHelperTest extends ReceivingTestBase {

  private DeliveryMetaData deliveryMetaData;
  private List<AuditFlagResponse> auditFlagResponseList;
  @Mock private EndGameDeliveryMetaDataService endGameDeliveryMetaDataService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private EndgameManagedConfig endgameManagedConfig;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private AuditService auditService;
  @Mock private AuditFlagRequest auditFlagRequest;
  @Mock private DeliveryMetaDataRepository deliveryMetaDataRepository;
  @InjectMocks private AuditHelper auditHelper;
  @MockBean private IOutboxPublisherService iOutboxPublisherService;

  @BeforeClass
  private void initMocks() {
    MockitoAnnotations.initMocks(this);

    deliveryMetaData = new DeliveryMetaData();
    deliveryMetaData.setDeliveryNumber(
        EndGameUtilsTestCase.getDelivery().getDeliveryNumber().toString());
    deliveryMetaData.setTotalCaseLabelSent(0);
    when(configUtils.getTCLMaxValPerDelivery(anyString())).thenReturn(TCL_MAX_PER_DELIVERY);
    TenantContext.setFacilityNum(54321);
    TenantContext.setFacilityCountryCode("us");
    AuditFlagRequestItem auditFlagRequestItem =
        AuditFlagRequestItem.builder()
            .itemNumber(9213971L)
            .vendorNumber(8)
            .qty(100)
            .qtyUom(EACHES)
            .vnpkRatio(10)
            .isFrequentlyReceivedQuantityRequired(false)
            .build();
    auditFlagRequest =
        AuditFlagRequest.builder()
            .items(Collections.singletonList(auditFlagRequestItem))
            .deliveryNumber(57640310l)
            .build();
    auditFlagResponseList = new ArrayList<>();
  }

  @AfterMethod
  public void resetMocks() {
    reset(endGameDeliveryMetaDataService);
    reset(deliveryMetaDataRepository);
    reset(endgameManagedConfig);
    reset(auditService);
  }

  @Test
  public void testNonTrustedVendorTrustSpecifications() {
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(AUDIT_V2_ENABLED)).thenReturn(true);
    AuditFlagResponse auditFlagResponse = getAuditFlagResponse(NON_TRUSTED_VENDOR);
    auditFlagResponseList.add(auditFlagResponse);
    when(auditService.retrieveItemAuditInfo(any(AuditFlagRequest.class), any()))
        .thenReturn(auditFlagResponseList);
    when(endGameDeliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.ofNullable(deliveryMetaData));
    when(deliveryMetaDataRepository.save(deliveryMetaData)).thenReturn(deliveryMetaData);
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(IS_VNPK_PALLET_QTY_ENABLED)).thenReturn(true);
    Map<String, Boolean> auditFlagResponseMap = auditHelper.fetchAndSaveAuditInfo(auditFlagRequest);
    Assert.assertTrue(auditFlagResponseMap.get("8-9213971"));
  }

  @Test
  public void testTrustedVendorTrustSpecifications() {
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(AUDIT_V2_ENABLED)).thenReturn(true);
    AuditFlagResponse auditFlagResponse = getAuditFlagResponse(TRUSTED_VENDOR);
    auditFlagResponseList.add(auditFlagResponse);
    when(auditService.retrieveItemAuditInfo(any(AuditFlagRequest.class), any()))
        .thenReturn(auditFlagResponseList);
    when(endGameDeliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.ofNullable(deliveryMetaData));
    when(deliveryMetaDataRepository.save(deliveryMetaData)).thenReturn(deliveryMetaData);
    Map<String, Boolean> auditFlagResponseMap = auditHelper.fetchAndSaveAuditInfo(auditFlagRequest);
    Assert.assertFalse(auditFlagResponseMap.get("8-9213971"));
  }

  @Test
  public void testInconsistentVendorTrustSpecifications() {
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(AUDIT_V2_ENABLED)).thenReturn(false);
    AuditFlagResponse auditFlagResponse = getAuditFlagResponse(IN_CONSISTENT_VENDOR);
    auditFlagResponseList.add(auditFlagResponse);
    when(auditService.retrieveItemAuditInfo(any(AuditFlagRequest.class), any()))
        .thenReturn(auditFlagResponseList);
    when(endGameDeliveryMetaDataService.findByDeliveryNumber(anyString()))
        .thenReturn(Optional.ofNullable(deliveryMetaData));
    when(deliveryMetaDataRepository.save(deliveryMetaData)).thenReturn(deliveryMetaData);
    Map<String, Boolean> auditFlagResponseMap = auditHelper.fetchAndSaveAuditInfo(auditFlagRequest);
    Assert.assertFalse(auditFlagResponseMap.get("8-9213971"));
  }

  @Test
  public void testIsAuditRequired() {
    long deliveryNumber = 123456789L;
    PurchaseOrderLine poLine = new PurchaseOrderLine();
    poLine.setPoLineNumber(1);
    poLine.setOrdered(new QuantityDetail());
    poLine.setVnpk(new Vnpk());
    poLine.setWhpk(new Whpk());
    poLine.getOrdered().setUom("");
    poLine.setItemDetails(new ItemDetails());
    Integer vendorNumber = 123;
    String baseDivisionCode = "baseDivisionCode";

    DeliveryMetaData deliveryMetaData = new DeliveryMetaData();
    LinkedTreeMap<String, String> itemDetails = new LinkedTreeMap<>();
    itemDetails.put(IS_AUDIT_REQUIRED, "true");
    LinkedTreeMap<String, LinkedTreeMap<String, String>> itemDetailsMap = new LinkedTreeMap<>();
    itemDetailsMap.put("987654321", itemDetails);
    deliveryMetaData.setItemDetails(itemDetailsMap);

    when(endGameDeliveryMetaDataService.findByDeliveryNumber(String.valueOf(deliveryNumber)))
            .thenReturn(Optional.of(deliveryMetaData));

    boolean result = auditHelper.isAuditRequired(deliveryNumber, poLine, vendorNumber, baseDivisionCode);

    assertFalse(result);
    resetMocks();
  }

  private AuditFlagResponse getAuditFlagResponse(String vendorType) {
    return AuditFlagResponse.builder()
        .itemNumber(9213971L)
        .vendorNumber(8)
        .flaggedQty(80)
        .orderedQty(100)
        .vnpkRatio(10)
        .isCaseFlagged(true)
        .vendorType(vendorType)
        .build();
  }
}
