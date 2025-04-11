package com.walmart.move.nim.receiving.fixture.config;

import io.strati.configuration.annotation.Configuration;
import io.strati.configuration.annotation.DefaultValue;
import io.strati.configuration.annotation.Property;
import lombok.Getter;

@Configuration(configName = "fixtureManagedConfig")
@Getter
public class FixtureManagedConfig {
  @Property(propertyName = "ct.base.url")
  private String ctBaseUrl;

  @Property(propertyName = "ct.username")
  private String ctUserName;

  @Property(propertyName = "ct.password")
  private String ctPassword;

  @Property(propertyName = "item.count.threshold")
  private int receivableItemCountThreshold;

  @Property(propertyName = "ct.job.run.every.x.minute")
  private int ctJobRunEveryXMinute;

  @Property(propertyName = "item.rep.base.url")
  private String itemRepBaseUrl;

  @Property(propertyName = "item.rep.username")
  private String itemRepUsername;

  @Property(propertyName = "item.rep.password")
  private String itemRepPassword;

  @Property(propertyName = "item.rep.batch.size")
  private Integer itemRepBatchSize;

  @Property(propertyName = "shipment.event.processor.enabled")
  @DefaultValue.Boolean(true)
  private Boolean shipmentEventProcessorEnabled;

  @Property(propertyName = "rfc.isSlottingEnabled")
  private boolean isSlottingEnabledRFC = Boolean.TRUE;

  @Property(propertyName = "uom.scaling.prefix")
  private String uomScalingPrefix = "centi";

  @Property(propertyName = "rfc.receivingDock")
  private String receivingDock;

  @Property(propertyName = "rfc.move.type.code")
  private Integer rfcMoveTypeCode = 5;

  @Property(propertyName = "rfc.move.type.desc")
  private String rfcMoveTypeDesc = "PUTAWAY";

  @Property(propertyName = "rfc.move.qty.uom")
  private String moveQtyUom;

  @Property(propertyName = "rfc.container.name")
  private String containerName = "40con";

  @Property(propertyName = "rfc.isMoveRequired")
  private boolean isMoveRequired = Boolean.TRUE;

  @Property(propertyName = "printing.pallet.format.name")
  private String printerFormatName;

  @Property(propertyName = "printing.pallet.ttl")
  private Integer printerPalletTTL = 72;

  @Property(propertyName = "withASN.receive.poNbr")
  private String withoutAsnPoNbr = "MANUAL";

  @Property(propertyName = "printing.label.date.format")
  private String printingLabelDateFormat = "dd/MM/yyyy";

  @Property(propertyName = "rfc.mdm.auth.key")
  private String rfcMdmAuthKey;

  @Property(propertyName = "rfc.isItemMDM.enabled")
  private boolean isItemMdmEnabled = Boolean.FALSE;
}
