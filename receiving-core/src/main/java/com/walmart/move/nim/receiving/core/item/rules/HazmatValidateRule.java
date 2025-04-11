package com.walmart.move.nim.receiving.core.item.rules;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.verifyVendorDateComplaince;

import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class HazmatValidateRule implements ItemRule {

  private static final Logger logger = LoggerFactory.getLogger(HazmatValidateRule.class);
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Override
  public boolean validateRule(DeliveryDocumentLine documentLine) {
    return isHazmatItem(documentLine)
        && !verifyVendorDateComplaince(documentLine.getHazmatVerifiedOn());
  }

  private Boolean isHazmatItem(DeliveryDocumentLine deliveryDocumentLine) {
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
}
