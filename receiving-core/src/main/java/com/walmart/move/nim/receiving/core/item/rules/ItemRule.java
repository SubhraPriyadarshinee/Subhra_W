package com.walmart.move.nim.receiving.core.item.rules;

import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;

public interface ItemRule {
  boolean validateRule(DeliveryDocumentLine documentLine_gdm);
}
