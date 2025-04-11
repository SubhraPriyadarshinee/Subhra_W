package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.TRUE_STRING;
import static java.util.Objects.nonNull;

import com.walmart.move.nim.receiving.core.model.delivery.meta.PurchaseReferenceLineMeta;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
public class OverrideInfo {
  public PurchaseReferenceLineMeta line;

  public String getOverrideOverageManager() {
    return nonNull(line) ? line.getIgnoreOverageBy() : null;
  }

  public boolean isOverrideOverage() {
    return nonNull(line) && TRUE_STRING.equals(line.getIgnoreOverage());
  }
}
