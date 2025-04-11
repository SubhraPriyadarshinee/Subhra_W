package com.walmart.move.nim.receiving.rdc.service;

import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.labeldownload.RejectReason;
import com.walmart.move.nim.receiving.rdc.utils.RdcSSTKInstructionUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.Arrays;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RdcSSTKItemValidator {
  @Autowired RdcSSTKInstructionUtils rdcSSTKInstructionUtils;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @ManagedConfiguration private AppConfig appConfig;

  public RejectReason validateItem(DeliveryDocumentLine deliveryDocumentLines) {
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

    rejectReason = validateSSTKItem(deliveryDocumentLines, isAclEnabledSite, isSymEnabledSite);
    if (Objects.nonNull(rejectReason)) {
      return rejectReason;
    }

    // Check if item is XBLOCK item
    if (Boolean.FALSE.equals(isRestrictedItemValidationDisabled)
        && Objects.nonNull(deliveryDocumentLines.getHandlingCode())
        && Arrays.asList(ReceivingConstants.X_BLOCK_ITEM_HANDLING_CODES)
            .contains(deliveryDocumentLines.getHandlingCode())) {
      return RejectReason.X_BLOCK;
    }

    // Check if the item is Master pack item
    if (Boolean.FALSE.equals(isMasterPackValidationDisabled)
        && StringUtils.equalsAny(
            deliveryDocumentLines.getAdditionalInfo().getPackTypeCode(), "M", "P")) {
      return RejectReason.RDC_MASTER_PACK;
    }

    rejectReason = validateBreakPack(deliveryDocumentLines, isAclEnabledSite, isSymEnabledSite);
    if (Objects.nonNull(rejectReason)) {
      return rejectReason;
    }

    // Validate Regulated Item
    rejectReason = validateRegulatedItem(deliveryDocumentLines);
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
        && Boolean.TRUE.equals(
            rdcSSTKInstructionUtils.validateHazmatValidateRule(deliveryDocumentLine))) {
      return RejectReason.RDC_HAZMAT;
    }
    // Limited quantity
    if (Boolean.FALSE.equals(isLimitedQtyValidationDisabled)
        && Boolean.TRUE.equals(
            rdcSSTKInstructionUtils.validateLimitedQtyRule(deliveryDocumentLine))) {
      return RejectReason.RDC_LIMITED_ITEM;
    }
    // Lithium ion
    if (Boolean.FALSE.equals(isLithiumIonValidationDisabled)
        && Boolean.TRUE.equals(
            rdcSSTKInstructionUtils.validateLithiumIonRule(deliveryDocumentLine))) {
      return RejectReason.RDC_LITHIUM_ION;
    }
    return null;
  }

  private RejectReason validateSSTKItem(
      DeliveryDocumentLine deliveryDocumentLine,
      Boolean isAclEnabledSite,
      Boolean isSymEnabledSiteEnabled) {
    Boolean isSstkValidationDisabled =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.IS_SSTK_VALIDATION_DISABLED,
            false);
    // Check if item is SSTK
    if (Boolean.FALSE.equals(isSstkValidationDisabled)
        && (Boolean.TRUE.equals(isAclEnabledSite)
            || (Boolean.TRUE.equals(isSymEnabledSiteEnabled)
                && !rdcSSTKInstructionUtils.isAtlasItemSymEligible(deliveryDocumentLine)))) {
      // SYM - Symbotic ineligible item
      return RejectReason.RDC_SSTK;
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
        && rdcSSTKInstructionUtils.isBreakPackConveyPicks(deliveryDocumentLine)) {
      return RejectReason.BREAKOUT;
    }
    if (Boolean.TRUE.equals(isSymEnabledSite && !isBreakPackValidationDisabled)
        && !deliveryDocumentLine.getVnpkQty().equals(deliveryDocumentLine.getWhpkQty())) {
      return RejectReason.BREAKOUT;
    }
    return null;
  }
}
