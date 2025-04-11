package com.walmart.move.nim.receiving.rx.config;

import io.strati.configuration.annotation.Configuration;
import io.strati.configuration.annotation.DefaultValue;
import io.strati.configuration.annotation.Property;
import lombok.Getter;

@Getter
@Configuration(configName = "rxManagedConfig")
public class RxManagedConfig {

  @Property(propertyName = "problem.item.check.enabled")
  private boolean problemItemCheckEnabled;

  @Property(propertyName = "problem.item.check.whitelisted.problemtypes")
  private String whiteListedProblemTypes;

  @Property(propertyName = "trim.update.instruction.response.enabled")
  private boolean trimUpdateInstructionResponseEnabled;

  @Property(propertyName = "trim.complete.instruction.response.enabled")
  private boolean trimCompleteInstructionResponseEnabled;

  @Property(propertyName = "split.pallet.enabled")
  private boolean splitPalletEnabled;

  @Property(propertyName = "rollback.partial.container.enabled")
  private boolean rollbackPartialContainerEnabled;

  @Property(propertyName = "wholesaler.lot.check.enabled")
  private boolean wholesalerLotCheckEnabled;

  @Property(propertyName = "wholesaler.vendors.list")
  private String wholesalerVendors;

  @Property(propertyName = "rollback.receipts.by.shipment")
  private boolean rollbackReceiptsByShipment;

  @Property(propertyName = "rollback.nimrds.receipts.enabled")
  public boolean isRollbackNimRdsReceiptsEnabled;

  @Property(propertyName = "publish.containers.enabled")
  public boolean isPublishContainersToKafkaEnabled;

  @Property(propertyName = "multisku.view.enabled")
  public boolean isMultiSkuInstructionViewEnabled;

  @Property(propertyName = "attp.event.lag.time.interval")
  @DefaultValue.Int(60)
  public int attpEventLagTimeInterval;

  @Property(propertyName = "epcis.multiskupallet.enabled")
  public boolean isEpcisMultiSkuPalletEnabled;

  @Property(propertyName = "epcis.problem.flow.default.to.asn")
  public boolean isEpcisProblemFallbackToASN;
}
