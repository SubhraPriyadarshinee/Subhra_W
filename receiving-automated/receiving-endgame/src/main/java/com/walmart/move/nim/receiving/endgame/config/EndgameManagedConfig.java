package com.walmart.move.nim.receiving.endgame.config;

import io.strati.configuration.annotation.Configuration;
import io.strati.configuration.annotation.Property;
import lombok.Getter;

@Configuration(configName = "endgameManagedConfig")
@Getter
public class EndgameManagedConfig {

  @Property(propertyName = "endgame.extra.tcl.threshold")
  private float labelingThresholdLimit;

  @Property(propertyName = "endgame.extra.tcl.count")
  private long extraTCLToSend;

  @Property(propertyName = "endgame.default.destination")
  private String endgameDefaultDestination;

  // TODO this needs to fixed in other system
  @Property(propertyName = "location.orgunit.id")
  private String orgUnitId;

  @Property(propertyName = "mdm.property.isnewitem")
  private String isNewItemPath;

  @Property(propertyName = "mdm.property.assortmentPath")
  private String assortmentPath;

  @Property(propertyName = "endgame.printable.zpl.template")
  private String printableZPLTemplate;

  @Property(propertyName = "gdm.max.no.upcs.scan")
  private int nosUPCForBulkScan;

  @Property(propertyName = "publish.vendor.dimension")
  private boolean publishVendorDimension;

  @Property(propertyName = "nonsortfc.pallet.format.name")
  private String printerFormatName;

  @Property(propertyName = "walmart.default.seller.id")
  private String walmartDefaultSellerId;

  @Property(propertyName = "sams.default.seller.id")
  private String samsDefaultSellerId;
}
