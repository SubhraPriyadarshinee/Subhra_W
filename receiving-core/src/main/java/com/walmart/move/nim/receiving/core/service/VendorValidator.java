package com.walmart.move.nim.receiving.core.service;

import java.util.List;

/** @author v0k00fe */
public interface VendorValidator {

  boolean isPilotVendorForAsnReceiving(String vendorId);

  boolean isAsnReceivingEnabled();

  List<String> getInternalAsnSourceTypes();

  List<String> getAsnEnabledVendorsList();

  boolean isAsnVendorCheckEnabled();

  List<String> getAutoPopulateReceiveQtyVendorList();

  boolean isPilotVendorForDsdcAsnReceiving(String vendorId);
}
