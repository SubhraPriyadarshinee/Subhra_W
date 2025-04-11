package com.walmart.move.nim.receiving.core.builder;

import com.walmart.move.nim.receiving.core.entity.DeliveryItemOverride;
import com.walmart.move.nim.receiving.core.model.DeliveryWithOSDRResponse;
import com.walmart.move.nim.receiving.core.model.PalletTiHi;
import com.walmart.move.nim.receiving.core.model.PurchaseOrderLineWithOSDRResponse;
import com.walmart.move.nim.receiving.core.model.PurchaseOrderWithOSDRResponse;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ItemDetails;
import com.walmart.move.nim.receiving.core.service.DeliveryItemOverrideService;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * This class is responsible for enriching DeliveryWithOSDRResponse with Temporary Pallet Ti and
 * Pallet Hi Values
 *
 * @author v0k00fe
 */
@Component
public class TemporaryTiHiEnricher {

  @Autowired private DeliveryItemOverrideService deliveryItemOverrideService;

  public void enrich(DeliveryWithOSDRResponse deliveryResponse) {
    for (PurchaseOrderWithOSDRResponse purchaseOrder : deliveryResponse.getPurchaseOrders()) {
      for (PurchaseOrderLineWithOSDRResponse purchaseOrderLine : purchaseOrder.getLines()) {
        ItemDetails itemDetails = purchaseOrderLine.getItemDetails();
        itemDetails.setActualTi(itemDetails.getPalletTi());
        itemDetails.setActualHi(itemDetails.getPalletHi());
        PalletTiHi palletTiHi = new PalletTiHi();
        palletTiHi.setPalletTi(itemDetails.getPalletTi());
        palletTiHi.setPalletHi(itemDetails.getPalletHi());
        palletTiHi.setVersion(0);

        // Enrich the PalletTi from local DB if it's available.
        Optional<DeliveryItemOverride> deliveryItemOverrideOptional =
            deliveryItemOverrideService.findByDeliveryNumberAndItemNumber(
                deliveryResponse.getDeliveryNumber(), itemDetails.getNumber());
        if (deliveryItemOverrideOptional.isPresent()) {
          DeliveryItemOverride deliveryItemOverride = deliveryItemOverrideOptional.get();
          itemDetails.setPalletTi(deliveryItemOverride.getTempPalletTi());
          palletTiHi.setPalletTi(deliveryItemOverride.getTempPalletTi());
          // For backward compatibility - can be removed later
          if (Objects.nonNull(deliveryItemOverride.getTempPalletHi())) {
            itemDetails.setPalletHi(deliveryItemOverride.getTempPalletHi());
            palletTiHi.setPalletHi(deliveryItemOverride.getTempPalletHi());
          }
          palletTiHi.setVersion(deliveryItemOverride.getVersion());
        }
        itemDetails.setPalletTiHi(palletTiHi);
      }
    }
  }
}
