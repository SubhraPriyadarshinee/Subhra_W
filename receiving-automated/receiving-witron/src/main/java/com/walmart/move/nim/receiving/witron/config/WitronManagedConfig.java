package com.walmart.move.nim.receiving.witron.config;

import com.walmart.move.nim.receiving.witron.constants.GdcConstants;
import io.strati.configuration.annotation.Configuration;
import io.strati.configuration.annotation.Property;
import java.util.List;
import lombok.Getter;

@Configuration(configName = GdcConstants.WITRON_MANAGED_CONFIG)
@Getter
public class WitronManagedConfig {

  @Property(propertyName = "asn.enabled.vendors.list")
  private List<String> asnEnabledVendorsList;

  @Property(propertyName = "asn.receiving.enabled")
  private boolean asnReceivingEnabled;

  @Property(propertyName = "asn.vendor.check.enabled")
  private boolean asnVendorCheckEnabled;

  @Property(propertyName = "internal.asn.source.types")
  private List<String> internalAsnSourceTypes;

  @Property(propertyName = "asn.auto.populate.rcv.qty.vendors.list")
  private List<String> autoPopulateReceiveQtyVendorList;

  @Property(propertyName = "mech.gdc.profiledwarehousearea.values")
  private List<String> mechGDCProfiledWarehouseAreaValues;

  @Property(propertyName = "nonmech.gdc.profiledwarehousearea.values")
  private List<String> nonMechGDCProfiledWarehouseAreaValues;
}
