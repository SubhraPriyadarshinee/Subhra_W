package com.walmart.move.nim.receiving.rdc.service;

import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.item.rules.HazmatValidateRule;
import com.walmart.move.nim.receiving.core.item.rules.LimitedQtyRule;
import com.walmart.move.nim.receiving.core.item.rules.LithiumIonRule;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.labeldownload.RejectReason;
import com.walmart.move.nim.receiving.core.service.LabelDataService;
import com.walmart.move.nim.receiving.core.service.LabelDownloadEventService;
import com.walmart.move.nim.receiving.rdc.utils.RdcInstructionUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RdcItemValidator {
  @Autowired LabelDataService labelDataService;
  @Autowired RdcInstructionUtils rdcInstructionUtils;
  @Autowired LimitedQtyRule limitedQtyRule;
  @Autowired LithiumIonRule lithiumIonRule;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @ManagedConfiguration private AppConfig appConfig;
  @Autowired LabelDownloadEventService labelDownloadEventService;
  @Autowired private HazmatValidateRule hazmatValidateRule;
  private static final Logger log = LoggerFactory.getLogger(RdcItemValidator.class);

  public RejectReason validateItem(List<DeliveryDocumentLine> deliveryDocumentLines) {
    RejectReason rejectReason = null;
    Boolean isAclEnabledSite =
        appConfig.getAclEnabledSitesList().contains(TenantContext.getFacilityNum());
    Boolean isSymEnabledSite =
        appConfig.getSymEnabledSitesList().contains(TenantContext.getFacilityNum());
    Boolean isRestrictedItemValidationDisabled =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_RESTRICTED_ITEM_VALIDATION_DISABLED,
            false);
    Boolean isMasterPackValidationDisabled =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_MASTER_PACK_ITEM_VALIDATION_DISABLED,
            false);

    // Check if item is XBLOCK item
    if (Boolean.FALSE.equals(isRestrictedItemValidationDisabled)
        && Arrays.asList(ReceivingConstants.X_BLOCK_ITEM_HANDLING_CODES)
            .contains(deliveryDocumentLines.get(0).getHandlingCode())) {
      return RejectReason.X_BLOCK;
    }

    // Check if the item is Master pack item
    if (Boolean.FALSE.equals(isMasterPackValidationDisabled)
        && StringUtils.equalsAny(
            deliveryDocumentLines.get(0).getAdditionalInfo().getPackTypeCode(), "M", "P")) {
      return RejectReason.RDC_MASTER_PACK;
    }

    rejectReason =
        validateBreakPack(deliveryDocumentLines.get(0), isAclEnabledSite, isSymEnabledSite);
    if (Objects.nonNull(rejectReason)) {
      return rejectReason;
    }

    // Check for valid combinations
    if (!appConfig
        .getValidItemPackTypeHandlingCodeCombinations()
        .contains(
            String.join(
                "",
                deliveryDocumentLines.get(0).getAdditionalInfo().getPackTypeCode(),
                deliveryDocumentLines.get(0).getAdditionalInfo().getHandlingCode()))) {
      return RejectReason.RDC_NONCON;
    }

    // Validate Regulated Item
    rejectReason = validateRegulatedItem(deliveryDocumentLines.get(0));
    if (Objects.nonNull(rejectReason)) {
      return rejectReason;
    }
    return null;
  }

  private RejectReason validateRegulatedItem(DeliveryDocumentLine deliveryDocumentLine) {
    Boolean isHazamtValidationDisabled =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_HAZAMT_ITEM_VALIDATION_DISABLED,
            false);
    Boolean isLimitedQtyValidationDisabled =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_LIMITED_QTY_VALIDATION_DISABLED,
            false);
    Boolean isLithiumIonValidationDisabled =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_LITHIUM_ION_VALIDATION_DISABLED,
            false);

    // Hazmat item
    if (Boolean.FALSE.equals(isHazamtValidationDisabled)
        && Boolean.TRUE.equals(hazmatValidateRule.validateRule(deliveryDocumentLine))) {
      return RejectReason.RDC_HAZMAT;
    }
    // Limited quantity
    if (Boolean.FALSE.equals(isLimitedQtyValidationDisabled)
        && Boolean.TRUE.equals(limitedQtyRule.validateRule(deliveryDocumentLine))) {
      return RejectReason.RDC_LIMITED_ITEM;
    }
    // Lithium ion
    if (Boolean.FALSE.equals(isLithiumIonValidationDisabled)
        && Boolean.TRUE.equals(lithiumIonRule.validateRule(deliveryDocumentLine))) {
      return RejectReason.RDC_LITHIUM_ION;
    }
    return null;
  }

  private RejectReason validateBreakPack(
      DeliveryDocumentLine deliveryDocumentLine,
      Boolean isAclEnabledSite,
      Boolean isSymEnabledSite) {
    Boolean isBreakPackValidationDisabled =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_BREAKPACK_VALIDATION_DISABLED,
            false);
    // Check if item is Break Pack
    if (Boolean.TRUE.equals(isAclEnabledSite && !isBreakPackValidationDisabled)
        && RdcUtils.isBreakPackConveyPicks(deliveryDocumentLine)) {
      return RejectReason.BREAKOUT;
    }
    if (Boolean.TRUE.equals(isSymEnabledSite && !isBreakPackValidationDisabled)
        && !deliveryDocumentLine.getVendorPack().equals(deliveryDocumentLine.getWarehousePack())) {
      return RejectReason.BREAKOUT;
    }
    return null;
  }
}
