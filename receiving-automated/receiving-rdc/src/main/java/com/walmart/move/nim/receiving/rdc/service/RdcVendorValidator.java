package com.walmart.move.nim.receiving.rdc.service;

import com.walmart.move.nim.receiving.core.service.VendorValidator;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** @author v0k00fe */
@Component(value = RdcConstants.RDC_VENDOR_VALIDATOR)
public class RdcVendorValidator implements VendorValidator {
  private static final Logger LOG = LoggerFactory.getLogger(RdcVendorValidator.class);

  @ManagedConfiguration private RdcManagedConfig rdcManagedConfig;

  public boolean isPilotVendorForAsnReceiving(String vendorId) {
    if (rdcManagedConfig.isAsnVendorCheckEnabled()) {
      return rdcManagedConfig.getAsnEnabledVendorsList().contains(vendorId);
    }
    return true;
  }

  public boolean isAsnReceivingEnabled() {
    return rdcManagedConfig.isAsnReceivingEnabled();
  }

  public List<String> getInternalAsnSourceTypes() {
    return rdcManagedConfig.getInternalAsnSourceTypes();
  }

  public List<String> getAsnEnabledVendorsList() {
    return rdcManagedConfig.getAsnEnabledVendorsList();
  }

  public boolean isAsnVendorCheckEnabled() {
    return rdcManagedConfig.isAsnVendorCheckEnabled();
  }

  public List<String> getAutoPopulateReceiveQtyVendorList() {
    return rdcManagedConfig.getAutoPopulateReceiveQtyVendorList();
  }

  public boolean isPilotVendorForDsdcAsnReceiving(String vendorId) {
    if (rdcManagedConfig.isDsdcAsnVendorCheckEnabled()) {
      return rdcManagedConfig.getDsdcAsnEnabledVendorsList().contains(vendorId);
    }
    return false;
  }
}
