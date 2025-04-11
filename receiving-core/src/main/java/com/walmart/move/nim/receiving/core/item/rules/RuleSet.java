package com.walmart.move.nim.receiving.core.item.rules;

import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RuleSet {
  private List<ItemRule> rules;

  public RuleSet(
      LithiumIonLimitedQtyRule lithiumIonLimitedQtyFilter,
      LithiumIonRule lithiumIonFilter,
      LimitedQtyRule limitedQtyFilter) {
    rules = new ArrayList<ItemRule>();
    addRule(lithiumIonLimitedQtyFilter);
    addRule(lithiumIonFilter);
    addRule(limitedQtyFilter);
  }

  public void addRule(ItemRule rule) {
    rules.add(rule);
  }

  public boolean validateRuleSet(DeliveryDocumentLine documentLine_gdm) {
    if (Objects.nonNull(documentLine_gdm.getTransportationModes())) {
      for (ItemRule rule : rules) {
        if (rule.validateRule(documentLine_gdm)) {
          return true;
        }
      }
    }
    return false;
  }
}
