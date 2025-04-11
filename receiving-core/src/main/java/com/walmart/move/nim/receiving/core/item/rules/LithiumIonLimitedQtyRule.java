package com.walmart.move.nim.receiving.core.item.rules;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.verifyVendorDateComplaince;

import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.TransportationModes;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.List;
import java.util.Objects;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
public class LithiumIonLimitedQtyRule implements ItemRule {
  @Override
  public boolean validateRule(DeliveryDocumentLine documentLine_gdm) {
    List<TransportationModes> transportationModesList =
        ListUtils.emptyIfNull(documentLine_gdm.getTransportationModes());
    for (TransportationModes transportationModes : transportationModesList) {
      if ((Objects.isNull(transportationModes.getDotRegionCode()))
          && (Objects.isNull(transportationModes.getDotIdNbr()))
          && (Objects.nonNull(transportationModes.getDotHazardousClass())
              && ReceivingConstants.LIMITED_QTY_DOT_HAZ_CLASS
                  .stream()
                  .anyMatch(transportationModes.getDotHazardousClass().getCode()::equalsIgnoreCase))
          && (Objects.isNull(transportationModes.getLimitedQty()))
          && Objects.nonNull(transportationModes.getProperShipping())
          && (!CollectionUtils.isEmpty(transportationModes.getPkgInstruction())
              && ReceivingConstants.pkgInstruction.containsAll(
                  transportationModes.getPkgInstruction()))
          && (transportationModes.getMode().getCode() == 1)) {
        if (!verifyVendorDateComplaince(documentLine_gdm.getLimitedQtyVerifiedOn())) {
          return true;
        } else return false;
      }
    }
    return false;
  }
}
