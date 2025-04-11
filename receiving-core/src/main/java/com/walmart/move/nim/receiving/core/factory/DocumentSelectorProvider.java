package com.walmart.move.nim.receiving.core.factory;

import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.service.DefaultDeliveryDocumentSelector;
import com.walmart.move.nim.receiving.core.service.DeliveryDocumentSelector;
import com.walmart.move.nim.receiving.core.service.FbqBasedDeliveryDocumentSelector;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DocumentSelectorProvider {
  private final TenantSpecificConfigReader tenantSpecificConfigReader;
  private final DefaultDeliveryDocumentSelector defaultDeliveryDocumentSelector;
  private final FbqBasedDeliveryDocumentSelector fbqBasedDeliveryDocumentSelector;

  public DocumentSelectorProvider(
      TenantSpecificConfigReader tenantSpecificConfigReader,
      DefaultDeliveryDocumentSelector defaultDeliveryDocumentSelector,
      FbqBasedDeliveryDocumentSelector fbqBasedDeliveryDocumentSelector) {
    this.tenantSpecificConfigReader = tenantSpecificConfigReader;
    this.defaultDeliveryDocumentSelector = defaultDeliveryDocumentSelector;
    this.fbqBasedDeliveryDocumentSelector = fbqBasedDeliveryDocumentSelector;
  }

  public DeliveryDocumentSelector getDocumentSelector(List<DeliveryDocument> documentList) {
    DeliveryDocumentSelector configuredDocumentSelector =
        tenantSpecificConfigReader.getConfiguredInstance(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.DELIVERY_DOCUMENT_SELECTOR,
            ReceivingConstants.DEFAULT_DELIVERY_DOCUMENT_SELECTOR,
            DeliveryDocumentSelector.class);

    // In case configured document selector is fbq based, check if all the po lines are import POs
    // Only in that case allow for fbq based selector
    // else fallback to default selector
    if (configuredDocumentSelector instanceof FbqBasedDeliveryDocumentSelector) {
      // see if any one po is import po
      if (documentList
          .stream()
          .anyMatch(deliveryDocument -> Boolean.TRUE.equals(deliveryDocument.getImportInd()))) {
        log.info(
            "configured selector is fbq based selector, and pos: {} are import pos, resolving to fbq based selector");
        return fbqBasedDeliveryDocumentSelector;
      } else {
        log.debug(
            "configured selector is fbq based selector, for non import pos, resolving to default selector ");
        return defaultDeliveryDocumentSelector;
      }
    }

    return configuredDocumentSelector;
  }
}
