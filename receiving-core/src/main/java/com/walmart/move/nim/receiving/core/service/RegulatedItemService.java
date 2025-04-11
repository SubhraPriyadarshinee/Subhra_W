package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.client.itemupdate.ItemUpdateRestApiClient;
import com.walmart.move.nim.receiving.core.common.ItemUpdateUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.item.rules.RuleSet;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.VendorCompliance;
import com.walmart.move.nim.receiving.core.model.gdm.v2.VendorComplianceRequestDates;
import com.walmart.move.nim.receiving.core.model.itemupdate.ItemUpdateRequest;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Date;
import java.util.Objects;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

/** @author sks0013 Service responsible for updating vendor compliance to GDM */
@Service
public class RegulatedItemService {

  private static final Logger log = LoggerFactory.getLogger(RegulatedItemService.class);

  @Resource(name = ReceivingConstants.DELIVERY_SERVICE)
  private DeliveryService deliveryService;

  @Resource(name = "itemCategoryRuleSet")
  private RuleSet itemCategoryRuleSet;

  @Autowired private TenantSpecificConfigReader configUtils;
  @Autowired private ItemUpdateRestApiClient itemUpdateRestApiClient;
  @Autowired private ItemUpdateUtils itemUpdateUtils;

  public boolean isVendorComplianceRequired(DeliveryDocumentLine documentLine4mGDM) {
    if (Objects.nonNull(documentLine4mGDM.getTransportationModes())) {
      return itemCategoryRuleSet.validateRuleSet(documentLine4mGDM);
    }
    return false;
  }

  public void updateVendorComplianceItem(VendorCompliance regulatedItemType, String itemNumber)
      throws ReceivingException {
    if (Objects.nonNull(regulatedItemType)) {
      VendorComplianceRequestDates vendorComplianceRequestDates =
          getVendorComplianceRequestDates(regulatedItemType);
      if (configUtils.getConfiguredFeatureFlag(
          TenantContext.getFacilityNum().toString(), ReceivingConstants.IQS_ITEM_UPDATE_ENABLED)) {
        HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
        ItemUpdateRequest itemUpdateRequest =
            itemUpdateUtils.createVendorComplianceItemUpdateRequest(
                vendorComplianceRequestDates, itemNumber, httpHeaders);
        itemUpdateRestApiClient.updateItem(itemUpdateRequest, httpHeaders);
      } else {
        deliveryService.setVendorComplianceDateOnGDM(itemNumber, vendorComplianceRequestDates);
      }
    }
  }

  public VendorComplianceRequestDates getVendorComplianceRequestDates(
      VendorCompliance regulatedItemType) {
    VendorComplianceRequestDates vendorComplianceRequestDates = new VendorComplianceRequestDates();
    switch (regulatedItemType) {
      case LITHIUM_ION:
        vendorComplianceRequestDates.setLithiumIonVerifiedOn(
            ReceivingUtils.dateConversionToUTC(new Date()));
        break;
      case LITHIUM_ION_AND_LIMITED_QUANTITY:
        vendorComplianceRequestDates.setLithiumIonVerifiedOn(
            ReceivingUtils.dateConversionToUTC(new Date()));
        vendorComplianceRequestDates.setLimitedQtyVerifiedOn(
            ReceivingUtils.dateConversionToUTC(new Date()));
        break;
      case LIMITED_QTY:
        vendorComplianceRequestDates.setLimitedQtyVerifiedOn(
            ReceivingUtils.dateConversionToUTC(new Date()));
        break;
      case HAZMAT:
        vendorComplianceRequestDates.setHazmatVerifiedOn(
            ReceivingUtils.dateConversionToUTC(new Date()));
        break;
    }
    return vendorComplianceRequestDates;
  }
}
