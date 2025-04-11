package com.walmart.move.nim.receiving.witron.service;

import com.walmart.move.nim.receiving.core.service.VendorValidator;
import com.walmart.move.nim.receiving.witron.config.WitronManagedConfig;
import com.walmart.move.nim.receiving.witron.constants.GdcConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** @author v0k00fe */
@Component(value = GdcConstants.WITRON_VENDOR_VALIDATOR)
public class WitronVendorValidator implements VendorValidator {
  private static final Logger LOG = LoggerFactory.getLogger(WitronVendorValidator.class);

  @ManagedConfiguration private WitronManagedConfig witronManagedConfig;

  public boolean isPilotVendorForAsnReceiving(String vendorId) {
    if (witronManagedConfig.isAsnVendorCheckEnabled()) {
      return witronManagedConfig.getAsnEnabledVendorsList().contains(vendorId);
    }
    return true;
  }

  public boolean isAsnReceivingEnabled() {
    return witronManagedConfig.isAsnReceivingEnabled();
  }

  public List<String> getInternalAsnSourceTypes() {
    return witronManagedConfig.getInternalAsnSourceTypes();
  }

  public List<String> getAsnEnabledVendorsList() {
    return witronManagedConfig.getAsnEnabledVendorsList();
  }

  public boolean isAsnVendorCheckEnabled() {
    return witronManagedConfig.isAsnVendorCheckEnabled();
  }

  public List<String> getAutoPopulateReceiveQtyVendorList() {
    return witronManagedConfig.getAutoPopulateReceiveQtyVendorList();
  }

  public boolean isPilotVendorForDsdcAsnReceiving(String vendorId) {
    return false;
  }
}
