package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.client.itemupdate.ItemUpdateRestApiClient;
import com.walmart.move.nim.receiving.core.common.ItemUpdateUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.item.rules.RuleSet;
import com.walmart.move.nim.receiving.core.mock.data.MockInstruction;
import com.walmart.move.nim.receiving.core.mock.data.MockItemUpdateData;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DotHazardousClass;
import com.walmart.move.nim.receiving.core.model.Mode;
import com.walmart.move.nim.receiving.core.model.TransportationModes;
import com.walmart.move.nim.receiving.core.model.VendorCompliance;
import com.walmart.move.nim.receiving.core.model.gdm.v2.VendorComplianceRequestDates;
import com.walmart.move.nim.receiving.core.model.itemupdate.ItemUpdateRequest;
import com.walmart.move.nim.receiving.core.model.itemupdate.ItemUpdateResponse;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Collections;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RegulatedItemServiceTest extends ReceivingTestBase {

  @InjectMocks private RegulatedItemService regulatedItemService;

  @Mock private RuleSet itemCategoryRuleSet;
  @Mock private DeliveryService deliveryService;
  @Mock private ItemUpdateRestApiClient itemUpdateRestApiClient;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private ItemUpdateUtils itemUpdateUtils;

  List<DeliveryDocument> deliveryDocuments1;
  ItemUpdateResponse itemUpdateResponse;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    deliveryDocuments1 = MockInstruction.getDeliveryDocuments();

    TransportationModes transportationModes = new TransportationModes();
    DotHazardousClass dotHazardousClass = new DotHazardousClass();
    dotHazardousClass.setCode("ORM-D");
    Mode mode = new Mode();
    mode.setCode(1);
    transportationModes.setDotHazardousClass(dotHazardousClass);
    transportationModes.setMode(mode);
    transportationModes.setDotRegionCode(null);
    transportationModes.setDotIdNbr(null);
    transportationModes.setLimitedQty(null);

    deliveryDocuments1
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setTransportationModes(Collections.singletonList(transportationModes));
    TenantContext.setFacilityNum(32818);
    TenantContext.setFacilityCountryCode("US");
    TenantContext.setCorrelationId("asdfghjlo");

    itemUpdateResponse = ItemUpdateResponse.builder().build();
  }

  @AfterMethod
  public void resetMocks() {
    reset(itemCategoryRuleSet);
    reset(deliveryService);
    reset(itemUpdateRestApiClient);
    reset(configUtils);
    reset(itemUpdateUtils);
  }

  @Test
  public void testIsVendorComplianceRequired_IsRequired() {
    when(itemCategoryRuleSet.validateRuleSet(any())).thenReturn(true);
    assertTrue(
        regulatedItemService.isVendorComplianceRequired(
            deliveryDocuments1.get(0).getDeliveryDocumentLines().get(0)));
  }

  @Test
  public void testIsVendorComplianceRequired_IsNotRequired() {
    when(itemCategoryRuleSet.validateRuleSet(any())).thenReturn(false);
    assertFalse(
        regulatedItemService.isVendorComplianceRequired(
            deliveryDocuments1.get(0).getDeliveryDocumentLines().get(0)));
  }

  @Test
  public void testIsVendorComplianceRequired_TransportationModesIsNull() {
    when(itemCategoryRuleSet.validateRuleSet(any())).thenReturn(true);
    assertFalse(
        regulatedItemService.isVendorComplianceRequired(
            MockInstruction.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0)));
  }

  @Test
  public void testUpdateComplianceDateToGDM_LithiumIon() throws ReceivingException {
    doNothing()
        .when(deliveryService)
        .setVendorComplianceDateOnGDM(anyString(), any(VendorComplianceRequestDates.class));
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), ReceivingConstants.IQS_ITEM_UPDATE_ENABLED))
        .thenReturn(false);
    regulatedItemService.updateVendorComplianceItem(VendorCompliance.LITHIUM_ION, "12345678");
    ArgumentCaptor<VendorComplianceRequestDates> captor =
        ArgumentCaptor.forClass(VendorComplianceRequestDates.class);
    verify(deliveryService, times(1))
        .setVendorComplianceDateOnGDM(eq("12345678"), captor.capture());
    assertNotNull(
        captor.getValue().getLithiumIonVerifiedOn(), "Lithium verified date should be set");
    assertNull(
        captor.getValue().getLimitedQtyVerifiedOn(), "Limited qty verified date should not be set");
  }

  @Test
  public void testUpdateComplianceDateToGDM_LimitedQty() throws ReceivingException {
    doNothing()
        .when(deliveryService)
        .setVendorComplianceDateOnGDM(anyString(), any(VendorComplianceRequestDates.class));
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), ReceivingConstants.IQS_ITEM_UPDATE_ENABLED))
        .thenReturn(false);
    regulatedItemService.updateVendorComplianceItem(VendorCompliance.LIMITED_QTY, "12345678");
    ArgumentCaptor<VendorComplianceRequestDates> captor =
        ArgumentCaptor.forClass(VendorComplianceRequestDates.class);
    verify(deliveryService, times(1))
        .setVendorComplianceDateOnGDM(eq("12345678"), captor.capture());
    assertNull(
        captor.getValue().getLithiumIonVerifiedOn(), "Lithium verified date should not be set");
    assertNotNull(
        captor.getValue().getLimitedQtyVerifiedOn(), "Limited qty verified date should be set");
  }

  @Test
  public void testUpdateComplianceDateToGDM_LithiumIonAndLimitedQty() throws ReceivingException {
    doNothing()
        .when(deliveryService)
        .setVendorComplianceDateOnGDM(anyString(), any(VendorComplianceRequestDates.class));
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), ReceivingConstants.IQS_ITEM_UPDATE_ENABLED))
        .thenReturn(false);
    regulatedItemService.updateVendorComplianceItem(
        VendorCompliance.LITHIUM_ION_AND_LIMITED_QUANTITY, "12345678");
    ArgumentCaptor<VendorComplianceRequestDates> captor =
        ArgumentCaptor.forClass(VendorComplianceRequestDates.class);
    verify(deliveryService, times(1))
        .setVendorComplianceDateOnGDM(eq("12345678"), captor.capture());
    assertNotNull(
        captor.getValue().getLithiumIonVerifiedOn(), "Lithium verified date should be set");
    assertNotNull(
        captor.getValue().getLimitedQtyVerifiedOn(), "Limited qty verified date should be set");
  }

  @Test
  public void testUpdateComplianceDateToGDM_NoVendorCompliance() throws ReceivingException {
    doNothing()
        .when(deliveryService)
        .setVendorComplianceDateOnGDM(anyString(), any(VendorComplianceRequestDates.class));
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), ReceivingConstants.IQS_ITEM_UPDATE_ENABLED))
        .thenReturn(false);
    regulatedItemService.updateVendorComplianceItem(null, "12345678");
    ArgumentCaptor<VendorComplianceRequestDates> captor =
        ArgumentCaptor.forClass(VendorComplianceRequestDates.class);
    verify(deliveryService, times(0))
        .setVendorComplianceDateOnGDM(eq("12345678"), captor.capture());
  }

  @Test
  public void testUpdateVendorComplianceItemToNodeRt_LithiumIon() throws ReceivingException {
    when(itemUpdateRestApiClient.updateItem(any(ItemUpdateRequest.class), any(HttpHeaders.class)))
        .thenReturn(itemUpdateResponse);
    when(itemUpdateUtils.createVendorComplianceItemUpdateRequest(
            any(VendorComplianceRequestDates.class), anyString(), any(HttpHeaders.class)))
        .thenReturn(MockItemUpdateData.getItemUpdateRequest());
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), ReceivingConstants.IQS_ITEM_UPDATE_ENABLED))
        .thenReturn(true);
    regulatedItemService.updateVendorComplianceItem(VendorCompliance.LITHIUM_ION, "12345678");
    ArgumentCaptor<VendorComplianceRequestDates> captor =
        ArgumentCaptor.forClass(VendorComplianceRequestDates.class);

    verify(itemUpdateUtils, times(1))
        .createVendorComplianceItemUpdateRequest(
            captor.capture(), eq("12345678"), any(HttpHeaders.class));
    assertNotNull(
        captor.getValue().getLithiumIonVerifiedOn(), "Lithium verified date should be set");
    assertNull(
        captor.getValue().getLimitedQtyVerifiedOn(), "Limited qty verified date should not be set");
  }

  @Test
  public void testUpdateVendorComplianceItemToNodeRt_LimitedQty() throws ReceivingException {
    when(itemUpdateRestApiClient.updateItem(any(ItemUpdateRequest.class), any(HttpHeaders.class)))
        .thenReturn(itemUpdateResponse);
    when(itemUpdateUtils.createVendorComplianceItemUpdateRequest(
            any(VendorComplianceRequestDates.class), anyString(), any(HttpHeaders.class)))
        .thenReturn(MockItemUpdateData.getItemUpdateRequest());
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), ReceivingConstants.IQS_ITEM_UPDATE_ENABLED))
        .thenReturn(true);
    regulatedItemService.updateVendorComplianceItem(VendorCompliance.LIMITED_QTY, "12345678");
    ArgumentCaptor<VendorComplianceRequestDates> captor =
        ArgumentCaptor.forClass(VendorComplianceRequestDates.class);

    verify(itemUpdateUtils, times(1))
        .createVendorComplianceItemUpdateRequest(
            captor.capture(), eq("12345678"), any(HttpHeaders.class));
    assertNull(captor.getValue().getLithiumIonVerifiedOn(), "Lithium verified date should be set");
    assertNotNull(
        captor.getValue().getLimitedQtyVerifiedOn(), "Limited qty verified date should not be set");
  }

  @Test
  public void testUpdateVendorComplianceItemToNodeRt_LithiumIonAndLimitedQty()
      throws ReceivingException {
    when(itemUpdateRestApiClient.updateItem(any(ItemUpdateRequest.class), any(HttpHeaders.class)))
        .thenReturn(itemUpdateResponse);
    when(itemUpdateUtils.createVendorComplianceItemUpdateRequest(
            any(VendorComplianceRequestDates.class), anyString(), any(HttpHeaders.class)))
        .thenReturn(MockItemUpdateData.getItemUpdateRequest());
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), ReceivingConstants.IQS_ITEM_UPDATE_ENABLED))
        .thenReturn(true);
    regulatedItemService.updateVendorComplianceItem(
        VendorCompliance.LITHIUM_ION_AND_LIMITED_QUANTITY, "12345678");
    ArgumentCaptor<VendorComplianceRequestDates> captor =
        ArgumentCaptor.forClass(VendorComplianceRequestDates.class);

    verify(itemUpdateUtils, times(1))
        .createVendorComplianceItemUpdateRequest(
            captor.capture(), eq("12345678"), any(HttpHeaders.class));
    assertNotNull(
        captor.getValue().getLithiumIonVerifiedOn(), "Lithium verified date should be set");
    assertNotNull(
        captor.getValue().getLimitedQtyVerifiedOn(), "Limited qty verified date should not be set");
  }

  @Test
  public void testUpdateComplianceItemWithHazmat() throws ReceivingException {
    doNothing()
        .when(deliveryService)
        .setVendorComplianceDateOnGDM(anyString(), any(VendorComplianceRequestDates.class));
    when(configUtils.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(), ReceivingConstants.IQS_ITEM_UPDATE_ENABLED))
        .thenReturn(false);
    regulatedItemService.updateVendorComplianceItem(VendorCompliance.HAZMAT, "12345678");
    ArgumentCaptor<VendorComplianceRequestDates> captor =
        ArgumentCaptor.forClass(VendorComplianceRequestDates.class);
    verify(deliveryService, times(1))
        .setVendorComplianceDateOnGDM(eq("12345678"), captor.capture());
    assertNotNull(captor.getValue().getHazmatVerifiedOn());
  }
}
