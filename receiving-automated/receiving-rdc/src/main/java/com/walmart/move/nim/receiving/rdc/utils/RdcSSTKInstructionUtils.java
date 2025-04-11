package com.walmart.move.nim.receiving.rdc.utils;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.verifyVendorDateComplaince;

import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.TransportationModes;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.List;
import java.util.Objects;
import org.apache.commons.collections4.ListUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
public class RdcSSTKInstructionUtils {

  private static final Logger logger = LoggerFactory.getLogger(RdcSSTKInstructionUtils.class);
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @ManagedConfiguration private AppConfig appConfig;

  /**
   * * This function validates if the hazmat item validation is enabled in GDM, return isHazmat else
   * Validate for hazmat item with below condition Hazmat Class != ORM-D, reg_code="UN",
   * transportation_mode=1 & dot_nbr is not null
   *
   * @param deliveryDocumentLine
   * @return true - If it is hazmat item false - otherwise
   */
  public Boolean isHazmatItemForSSTK(DeliveryDocumentLine deliveryDocumentLine) {
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        TenantContext.getFacilityNum().toString(),
        ReceivingConstants.IS_GDM_HAZMAT_ITEM_VALIDATION_ENABLED,
        false)) {
      logger.info(
          "Hazmat Information retrieved from GDM for Item: [{}], HazmatValidation:[{}]",
          deliveryDocumentLine.getItemNbr(),
          deliveryDocumentLine.getIsHazmat());
      return deliveryDocumentLine.getIsHazmat();
    }

    Boolean isItemTransportationModeValidatedForHazmat =
        InstructionUtils.isItemTransportationModeValidatedForHazmat(
            deliveryDocumentLine.getTransportationModes());
    logger.info(
        "Hazmat Information retrieved from Receiving using RDS Validations for Item:[{}], HazmatValidation:[{}]",
        deliveryDocumentLine.getItemNbr(),
        isItemTransportationModeValidatedForHazmat);
    return isItemTransportationModeValidatedForHazmat;
  }

  public boolean isAtlasItemSymEligible(DeliveryDocumentLine deliveryDocumentLine) {
    return Objects.nonNull(deliveryDocumentLine.getAdditionalInfo().getAsrsAlignment())
        && !CollectionUtils.isEmpty(appConfig.getValidSymAsrsAlignmentValues())
        && appConfig
            .getValidSymAsrsAlignmentValues()
            .contains(deliveryDocumentLine.getAdditionalInfo().getAsrsAlignment())
        && deliveryDocumentLine
            .getAdditionalInfo()
            .getSlotType()
            .equalsIgnoreCase(ReceivingConstants.PRIME_SLOT_TYPE);
  }

  public boolean validateLimitedQtyRule(DeliveryDocumentLine deliveryDocumentLine) {
    List<TransportationModes> transportationModesList =
        ListUtils.emptyIfNull(deliveryDocumentLine.getTransportationModes());
    for (TransportationModes transportationModes : transportationModesList) {
      if ((Objects.isNull(transportationModes.getDotRegionCode()))
          && (Objects.isNull(transportationModes.getDotIdNbr()))
          && (Objects.nonNull(transportationModes.getDotHazardousClass())
              && ReceivingConstants.LIMITED_QTY_DOT_HAZ_CLASS
                  .stream()
                  .anyMatch(transportationModes.getDotHazardousClass().getCode()::equalsIgnoreCase))
          && (transportationModes.getMode().getCode() == 1)
          && (Objects.isNull(transportationModes.getLimitedQty()))) {
        return !verifyVendorDateComplaince(deliveryDocumentLine.getLimitedQtyVerifiedOn());
      }
    }
    return false;
  }

  public boolean validateLithiumIonRule(DeliveryDocumentLine deliveryDocumentLine) {
    List<TransportationModes> transportationModesList =
        ListUtils.emptyIfNull(deliveryDocumentLine.getTransportationModes());
    for (TransportationModes transportationModes : transportationModesList) {
      if (Objects.nonNull(transportationModes.getDotHazardousClass())
          && ReceivingConstants.NOT_AVAILABLE.equalsIgnoreCase(
              transportationModes.getDotHazardousClass().getCode())
          && Objects.nonNull(transportationModes.getProperShipping())
          && (!CollectionUtils.isEmpty(transportationModes.getPkgInstruction())
              && ReceivingConstants.pkgInstruction.containsAll(
                  transportationModes.getPkgInstruction()))
          && (transportationModes.getMode().getCode() == 1)) {
        return !verifyVendorDateComplaince(deliveryDocumentLine.getLithiumIonVerifiedOn());
      }
    }
    return false;
  }

  public boolean validateHazmatValidateRule(DeliveryDocumentLine documentLine) {
    return isHazmatItemForSSTK(documentLine)
        && !verifyVendorDateComplaince(documentLine.getHazmatVerifiedOn());
  }

  public boolean isBreakPackConveyPicks(DeliveryDocumentLine deliveryDocumentLine) {
    return RdcConstants.DA_BREAK_PACK_CONVEY_PICKS_ITEM_HANDLING_CODE.equalsIgnoreCase(
        deliveryDocumentLine.getAdditionalInfo().getItemPackAndHandlingCode());
  }
}
