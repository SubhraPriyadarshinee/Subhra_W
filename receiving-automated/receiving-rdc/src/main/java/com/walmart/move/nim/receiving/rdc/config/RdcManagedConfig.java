package com.walmart.move.nim.receiving.rdc.config;

import io.strati.configuration.annotation.Configuration;
import io.strati.configuration.annotation.DefaultValue;
import io.strati.configuration.annotation.Property;
import java.util.List;
import lombok.Getter;

@Configuration(configName = "rdcManagedConfig")
@Getter
public class RdcManagedConfig {

  @Property(propertyName = "ngr.base.url")
  private String ngrBaseUrl;

  @Property(propertyName = "split.pallet.enabled")
  private boolean splitPalletEnabled;

  @Property(propertyName = "smart.slotting.integration.enabled")
  private boolean smartSlottingIntegrationEnabled;

  @Property(propertyName = "move.type.code")
  private int moveTypeCode;

  @Property(propertyName = "move.type.desc")
  private String moveTypeDesc;

  @Property(propertyName = "delivery.status.check.enabled")
  private boolean deliveryStatusCheckEnabled;

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

  @Property(propertyName = "move.to.location.for.docktag")
  private String moveToLocationForDockTag;

  @Property(propertyName = "docktag.move.type.code")
  private int dockTagMoveTypeCode;

  @Property(propertyName = "docktag.move.type.desc")
  private String dockTagMoveTypeDesc;

  @Property(propertyName = "docktag.move.priority.code")
  private Integer docktagMovePriorityCode;

  @Property(propertyName = "vtr.reason.code")
  private Integer vtrReasonCode;

  @Property(propertyName = "qty.adjustment.reason.code")
  private Integer quantityAdjustmentReasonCode;

  @Property(propertyName = "da.qty.receive.max.limit")
  private Integer daQtyReceiveMaxLimit;

  @Property(propertyName = "da.atlas.item.list")
  private List<String> daAtlasItemList;

  @Property(propertyName = "da.atlas.item.enabled.vendor.list")
  private List<String> daAtlasItemEnabledVendorList;

  @Property(propertyName = "da.atlas.item.enabled.pack.handling.code")
  private List<String> daAtlasItemEnabledPackHandlingCode;

  @Property(propertyName = "sym.label.type.for.sorter")
  private String symEligibleLabelType;

  @Property(propertyName = "in.progress.cut.off.deliveries.list")
  private List<String> inProgressCutOffDeliveriesList;

  @Property(propertyName = "pre.label.freight.source.sites.list")
  private List<Integer> preLabelFreightSourceSites;

  @Property(propertyName = "sym.eligible.handling.codes.for.routing.label")
  private List<String> symEligibleHandlingCodesForRoutingLabel;

  @Property(propertyName = "dsdc.asn.vendor.check.enabled")
  private boolean dsdcAsnVendorCheckEnabled;

  @Property(propertyName = "dsdc.asn.enabled.vendors.list")
  private List<String> dsdcAsnEnabledVendorsList;

  @Property(propertyName = "automation.valid.deliveries.list")
  private List<String> validAutomationDeliveries;

  @Property(propertyName = "automation.valid.items.list")
  private List<String> validAutomationItems;

  @Property(propertyName = "atlas.da.pilot.deliveries")
  private List<String> atlasDaPilotDeliveries;

  @Property(propertyName = "flib.ineligible.exceptions")
  private List<String> flibInEligibleExceptions;

  @Property(propertyName = "atlas.conveyable.handling.codes.for.da.slotting")
  private List<String> atlasConveyableHandlingCodesForDaSlotting;

  @Property(propertyName = "atlas.non.conveyable.handling.codes.for.da.slotting")
  private List<String> atlasNonConveyableHandlingCodesForDaSlotting;

  @Property(propertyName = "offline.eligible.item.handling.codes")
  private List<String> offlineEligibleItemHandlingCodes;

  @Property(propertyName = "enable.single.transaction.for.offline")
  @DefaultValue.Boolean(false)
  private Boolean enableSingleTransactionForOffline;

  @Property(propertyName = "dc.eligible.for.prepare.consolidated.container")
  @DefaultValue.List({"6014", "06014"})
  private List<String> dcListEligibleForPrepareConsolidatedContainer;

  @Property(propertyName = "dsdc.child.containers.limit.for.async.flow")
  private Integer dsdcAsyncFlowChildContainerCount;

  @Property(propertyName = "publish.labels.to.hawkeye.by.async.enabled")
  private boolean publishLabelsToHawkeyeByAsyncEnabled;

  @Property(propertyName = "item.update.hawkeye.deliveries.day.limit")
  private int itemUpdateHawkeyeDeliveriesDayLimit;

  @Property(propertyName = "da.atlas.item.enabled.pack.handling.code.with.item.config")
  private List<String> daAtlasItemEnabledPackHandlingCodeWithItemConfig;

  @Property(propertyName = "case.pack.sym.ineligible.handling.codes")
  private List<String> casePackSymIneligibleHandlingCodes;

  @Property(propertyName = "da.atlas.label.type.validations.eligible.pack.handling.codes")
  private List<String> daAtlasLabelTypeValidationsEligiblePackHandlingCodes;

  @Property(propertyName = "atlas.da.non.supported.handling.codes")
  private List<String> atlasDaNonSupportedHandlingCodes;

  @Property(propertyName = "atlas.conveyable.handling.codes.for.da.automation.slotting")
  private List<String> atlasConveyableHandlingCodesForDaAutomationSlotting;

  @Property(propertyName = "wpm.sites")
  @DefaultValue.List({"6014", "06014"})
  private List<String> wpmSites;

  @Property(propertyName = "rdc2rdc.sites")
  @DefaultValue.List({"1234", "12345"})
  private List<String> rdc2rdcSites;

  @Property(propertyName = "is.dummy.delivery.enabled")
  @DefaultValue.Boolean(false)
  private boolean isDummyDeliveryEnabled;

  @Property(propertyName = "flib.sstk.pregen.scheduler.retries.count")
  private int flibSstkPregenSchedulerRetriesCount;

  @Property(propertyName = "flib.sstk.pregen.scheduler.enabled.sites")
  private List<String> flibSstkPregenSchedulerEnabledSites;

  @Property(propertyName = "atlas.da.non.con.interchangeable.handling.codes")
  private List<String> atlasDaNonConInterchangeableHandlingCodes;

  @Property(propertyName = "atlas.da.breakpack.interchangeable.handling.codes")
  private List<String> atlasDaBreakPackInterchangeableHandlingCodes;

  @Property(propertyName = "atlas.sstk.pilot.deliveries")
  private List<String> atlasSSTKPilotDeliveries;

  @Property(propertyName = "delivery.update.message.event.types")
  private List<String> deliveryUpdateMessageEventTypes;

  @Property(propertyName = "atlas.da.non.con.slotting.pack.and.handling.codes")
  private List<String> atlasDaNonConPackAndHandlingCodes;

  @Property(propertyName = "atlas.da.printing.async.blocking.handling.codes")
  private List<String> atlasDaPrintingAsyncBlockedHandlingCodes;
}
