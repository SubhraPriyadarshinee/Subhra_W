package com.walmart.move.nim.receiving.core.config;

import io.strati.configuration.annotation.Configuration;
import io.strati.configuration.annotation.DefaultValue;
import io.strati.configuration.annotation.Property;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;

@Configuration(configName = "appConfig")
@Getter
public class AppConfig {

  @Property(propertyName = "receiving.test.queue")
  private String testQueueName;

  @Property(propertyName = "queue.timeout")
  private Long queueTimeOut;

  @Property(propertyName = "listener.concurrency")
  private String listenerConcurrency;

  @Property(propertyName = "gdm.base.url")
  private String gdmBaseUrl;

  @Property(propertyName = "move.event")
  private String moveEvent;

  @Property(propertyName = "move.type.code")
  private Integer moveTypeCode;

  @Property(propertyName = "move.type.desc")
  private String movetypeDesc;

  @Property(propertyName = "move.priority")
  private Integer movePriority;

  @Property(propertyName = "move.qty.uom")
  private String moveQtyUom;

  @Property(propertyName = "fit.base.url")
  private String fitBaseUrl;

  @Property(propertyName = "jms.auth.enabled")
  private Boolean jmsAuthEnabled;

  @Property(propertyName = "jms.async.publish.enabled")
  private Boolean jmsAsyncPublishEnabled = Boolean.TRUE;

  @Property(propertyName = "jms.retry.publish.pagesize")
  private Integer jmsRetryPublishPageSize;

  @Property(propertyName = "jms.retry.stale.timeout")
  private int jmsRetryStaleTimeOut;

  @Property(propertyName = "jms.retry.stale.scheduler.run.every.min")
  private int markRetryEventAsStaleRunEveryMin;

  @Property(propertyName = "http.client.connection.timeout.miliseconds")
  private int httpClientConnectionTimeoutMiliseconds;

  @Property(propertyName = "http.client.socket.timeout.miliseconds")
  private int httpClientSocketTimeoutMiliseconds;

  @Property(propertyName = "inventory.base.url")
  private String inventoryBaseUrl;

  /** Inv 2.0 READ operations (GET) */
  @Property(propertyName = "inventory.query.base.url")
  private String inventoryQueryBaseUrl;

  /** Inv 2.0 WRITE operations(PUT, POST etc) */
  @Property(propertyName = "inventory.core.base.url")
  private String inventoryCoreBaseUrl;

  @Property(propertyName = "multi.tenant.inventory.base.url")
  private String multiTenantInventoryBaseUrl;

  @Property(propertyName = "multi.tenant.hawkeye.ronin.base.url")
  private String multiTenantHawkEyeRoninBaseUrl;

  @Property(propertyName = "multi.tenant.labelling.base.url")
  private String multiTenantLabellingBaseUrl;

  @Property(propertyName = "jms.session.cache.size")
  private int jmsSessionCacheSize;

  @Property(propertyName = "dcfin.base.url")
  private String dcFinBaseUrl;

  @Property(propertyName = "dcfin.api.key")
  private String dcFinApiKey;

  @Property(propertyName = "dcfin.receipt.posting.enabled")
  private Boolean isReceiptPostingEnaledForDCFin;

  @Property(propertyName = "dcfin.async.receipt.posting.enabled")
  private Boolean isAsyncDCFinPostEnabled;

  @Property(propertyName = "tomcat.timeout.test")
  private Boolean isTomcatTimeout;

  @Property(propertyName = "slotting.base.url")
  private String slottingBaseUrl;

  @Property(propertyName = "pubsub.enabled")
  private Boolean pubsubEnabled;

  @Property(propertyName = "packaged.as.uom")
  private String packagedAsUom;

  @Property(propertyName = "mdm.base.url")
  private String itemMDMBaseUrl;

  @Property(propertyName = "mdm.auth.key")
  private String mdmAuthKey;

  @Property(propertyName = "damage.base.url")
  private String damageAppBaseUrl;

  @Property(propertyName = "lpn.base.url")
  private String lpnBaseUrl;

  @Property(propertyName = "location.base.url")
  private String locationBaseuRL;

  @Property(propertyName = "lpn.cache.threshold")
  private float lpnCacheThreshold;

  @Property(propertyName = "lpn.cache.max.store.count")
  private int lpnCacheMaxStoreCount;

  @Property(propertyName = "lpn.cache.poll.timeout")
  private int lpnCachePollTimeout;

  @Property(propertyName = "lpaas.base.url")
  private String lpaasBaseUrl;

  @Property(propertyName = "witron.dcFin.delivery.cache.size")
  private int dcFinDeliveryCacheSize;

  @Property(propertyName = "witron.dcFin.delivery.cache.ttlInSec")
  private int dcFinDeliveryCacheSizeTTLInSec;

  @Property(propertyName = "mdm.items.batch.size")
  private int itemBatchSize;

  @Property(propertyName = "audit.base.url")
  private String auditBaseUrl;

  @Property(propertyName = "nimrds.base.url")
  private String nimRDSServiceBaseUrl;

  @Property(propertyName = "epcis.base.url")
  private String epcisServiceBaseUrl;

  @Property(propertyName = "fixit.platform.base.url")
  private String fixitPlatformBaseUrl;

  @Property(propertyName = "load.quality.base.url")
  private String loadQualityBaseUrl;

  @Property(propertyName = "rx.problem.default.door")
  private String rxProblemDefaultDoor;

  @Property(propertyName = "receiving.consumer.id")
  private String receivingConsumerId;

  @Property(propertyName = "epcis.service.name")
  private String epcisServiceName;

  @Property(propertyName = "epcis.service.env")
  private String epcisServiceEnv;

  @Property(propertyName = "epcis.service.version")
  private String epcisServiceVersion;

  @Property(propertyName = "epcis.consumer.id")
  private String epcisConsumerId;

  @Property(propertyName = "fixit.service.name")
  private String fixitServiceName;

  @Property(propertyName = "fixit.service.env")
  private String fixitServiceEnv;

  @Property(propertyName = "load.quality.service.name")
  private String loadQualityServiceName;

  @Property(propertyName = "load.quality.service.env")
  private String loadQualityServiceEnv;

  @Property(propertyName = "dc.gln.details")
  private String glnDetails;

  @Property(propertyName = "labelling.service.base.url")
  private String labellingServiceBaseUrl;

  @Property(propertyName = "labelling.service.call.batch.count")
  private int labellingServiceCallBatchCount;

  @Property(propertyName = "labelling.service.label.ttl.in.hours")
  private int labelTTLInHours;

  @Property(propertyName = "in.sql.batch.size")
  private int inSqlBatchSize;

  @Property(propertyName = "labelling.service.pre.label.format.name")
  private String preLabelFormatName;

  @Property(propertyName = "docktag.auto.complete.hours")
  private int dockTagAutoCompleteHours;

  @Property(propertyName = "docktag.auto.complete.pageSize")
  private int dockTagAutoCompletePageSize;

  @Property(propertyName = "docktag.auto.complete.run.every.hour")
  private int dockTagAutoCompleteRunEveryHour;

  @Property(propertyName = "docktag.auto.complete.enabled.facilities")
  private List<Integer> dockTagAutoCompleteEnabledFacilities;

  @Property(propertyName = "allow.pocon.rcv.on.cancelled.po.pol")
  private boolean allowPOCONRcvOnCancelledPOPOL = Boolean.FALSE;

  @Property(propertyName = "rest.max.retries")
  private Long restMaxRetries; // Maximum number of times rest call will be retried

  @Property(propertyName = "check.delivery.receivable")
  private boolean checkDeliveryStatusReceivable;

  @Property(propertyName = "sorter.exception.topic")
  private String sorterExceptionTopic;

  @Property(propertyName = "sorter.topic")
  private String sorterTopic;

  @Property(propertyName = "rx.repackage.vendors.list")
  private String repackageVendors;

  @Property(propertyName = "gdm.shipment.search.version")
  private String shipmentSearchVersion;

  @Property(propertyName = "delivery.auto.complete.enabled.facilities")
  private List<Integer> deliveryAutoCompleteEnabledFacilities;

  @Property(propertyName = "auto.complete.delivery.job.run.every.x.minute")
  private int autoCompleteDeliveryJobRunsEveryXMinutes;

  @Property(propertyName = "auto.cancel.instruction.job.run.every.x.minute")
  private int autoCancelInstructionJobRunsEveryXMinutes;

  @Property(propertyName = "auto.cancel.instruction.enabled.facilities")
  private List<Integer> autoCancelInstructionEnabledFacilities;

  @Property(propertyName = "auto.complete.delivery.page.size")
  private int autoCompleteDeliveryPageSize;

  @Property(propertyName = "rx.filter.invalidpos.enable")
  private boolean filteringInvalidposEnabled;

  @Property(propertyName = "max.allowed.labels.at.once")
  private int maxAllowedLabelsAtOnce;

  @Property(propertyName = "fetch.labels.limit")
  private int fetchLabelsLimit;

  @Property(propertyName = "wft.publish.enabled")
  private boolean wftPublishEnabled;

  @Property(propertyName = "rx.override.serveInstrMethod")
  private boolean overrideServeInstrMethod;

  @Property(propertyName = "rx.problem.attach.latest.shipments")
  private boolean attachLatestShipments;

  @Property(propertyName = "rx.slot.unlocking.enabled")
  private boolean slotUnlockingEnabled;

  @Property(propertyName = "deliverydoc.in.place.on.conveyor.enabled")
  private boolean deliveryDocInPlaceOnConveyorEnabled;

  @Property(propertyName = "manual.auto.select.poline")
  private boolean manualPoLineAutoSelectionEnabled;

  @Property(propertyName = "rx.close.date.limit.days")
  private int closeDateLimitDays;

  @Property(propertyName = "rx.enable.check.close.date")
  private boolean closeDateCheckEnabled;

  @Property(propertyName = "gdm.deliveryupdate.kafka.listener.enabled.facilities")
  private List<Integer> gdmDeliveryUpdateKafkaListenerEnabledFacilities;

  @Property(propertyName = "publish.container.in.instruction")
  private boolean publishContainerDetailsInInstruction;

  @Property(propertyName = "reset.jmsretry.count.batchsize")
  private int resetJmsretryBatchsize;

  @Property(propertyName = "max.allowed.reprint.labels")
  private int maxAllowedReprintLabels;

  @Property(propertyName = "round.robin.po.select.enabled")
  private boolean roundRobinPOSelectEnabled;

  @Property(propertyName = "item.config.base.url")
  private String itemConfigBaseUrl;

  @Property(propertyName = "item.update.feature.enabled")
  private boolean itemUpdateFeatureEnabled;

  @Property(propertyName = "item.update.base.url")
  private String itemUpdateBaseUrl;

  @Property(propertyName = "iqs.channel.type")
  private String iqsChannelType;

  @Property(propertyName = "iqs.svc.env")
  private String iqsServiceEnv;

  @Property(propertyName = "kafka.inventory.adjustment.listener.enabled.facilities")
  private List<Integer> kafkaInventoryAdjustmentListenerEnabledFacilities;

  @Property(propertyName = "receiving-mirage.base.url")
  private String receivingMirageBaseUrlVoidLpn;

  @Property(propertyName = "receiving.mirage.base.url")
  private String receivingMirageBaseUrl;

  @Property(propertyName = "decant.base.url")
  private String decantBaseUrl;

  @Property(propertyName = "valid.sym.asrs.alignment.values")
  private List<String> validSymAsrsAlignmentValues;

  @Property(propertyName = "hawkeye.message.listener.enabled.facilities")
  private List<Integer> hawkeyeMessageListenerEnabledFacilities;

  @Property(propertyName = "atlas.sams.fc.item.request")
  @DefaultValue.Boolean(false)
  private Boolean isSamsItemRequest;

  @Property(propertyName = "uom.scaling.prefix")
  private String uomScalingPrefix = "centi";

  @Property(propertyName = "scalable.uom.list")
  private List<String> scalableUomList = Arrays.asList("LB", "OZ");

  @Property(propertyName = "move.query.base.url")
  private String moveQueryBaseUrl;

  @Property(propertyName = "instruction.download.listener.enabled.facilities")
  private List<Integer> instructionDownloadListenerEnabledFacilities;

  @Property(propertyName = "ei.source.node.division.code")
  private Integer eiSourceNodeDivisionCode;

  @Property(propertyName = "ei.destination.node.division.code")
  private Integer eiDestinationNodeDivisionCode;

  @Property(propertyName = "gdm.delivery.state.reason.codes.for.open.status")
  private List<String> gdmDeliveryStateReasonCodesForOpenStatus;

  @Property(propertyName = "hawkeye.base.url")
  private String hawkeyeBaseUrl;

  @Property(propertyName = "acl.enabled.sites.list")
  private List<Integer> aclEnabledSitesList;

  @Property(propertyName = "sym.enabled.sites.list")
  private List<Integer> symEnabledSitesList;

  @Property(propertyName = "validItem.packType.HandlingCode.Combinations.list")
  private List<String> validItemPackTypeHandlingCodeCombinations;

  @Property(propertyName = "auto.complete.dockTag.scheduler.cron")
  private String autoCompleteDockTagSchedulerCron;

  @Property(propertyName = "auto.complete.dockTag.scheduler.name")
  private String autoCompleteDockTagSchedulerName;

  @Property(propertyName = "auto.complete.deliveries.scheduler.cron")
  private String autoCompleteDeliveriesSchedulerCron;

  @Property(propertyName = "auto.complete.deliveries.scheduler.name")
  private String autoCompleteDeliveriesSchedulerName;

  @Property(propertyName = "auto.cancel.instructions.scheduler.cron")
  private String autoCancelInstructionsSchedulerCron;

  @Property(propertyName = "auto.cancel.instructions.scheduler.name")
  private String autoCancelInstructionsSchedulerName;

  @Property(propertyName = "track.problem.tag.enable")
  private boolean trackProblemTagEnabled;

  @Property(propertyName = "receiving.problem.tag.types.list")
  private List<String> problemTagTypesList;

  @Property(propertyName = "receiving.problem.tag.check.enabled")
  private boolean problemTagCheckEnabled;

  @Property(propertyName = "iqs.base.url")
  private String iqsBaseUrl;

  @Property(propertyName = "receiving.item.rest.apq.id")
  private String receivingItemApqId;

  @Property(propertyName = "iqs.receiving.consumer.id")
  private String iqsReceivingConsumerId;

  @Property(propertyName = "reprint.old.labels.enable")
  private boolean reprintOldLabelsEnabled;

  @Property(propertyName = "gdm.item.update.listener.enabled.facilities")
  private List<Integer> gdmItemUpdateListenerEnabledFacilities;

  @Property(propertyName = "orderWell.base.url")
  private String orderWellBaseUrl;

  @Property(propertyName = "orderWell.consumer.id")
  private String orderWellConsumerId;

  @Property(propertyName = "orderWell.service.name")
  private String orderWellServiceName;

  @Property(propertyName = "orderWell.service.env")
  private String orderWellServiceEnv;

  @Property(propertyName = "orderWell.daOrders.potype")
  private List<Integer> orderWellDaOrdersPotype;

  @Property(propertyName = "mfc.aligned.stores")
  private List<Integer> mfcAlignedStores;

  @Property(propertyName = "mfc.aligned.stores.enabled")
  @DefaultValue.Boolean(false)
  private boolean isMfcAlignedStoreEnabled;

  @Property(propertyName = "gdm.itemupdate.kafka.listener.enabled.facilities")
  private List<Integer> gdmItemUpdateKafkaListenerEnabledFacilities;

  @Property(propertyName = "order.fulfillment.base.url")
  private String orderFulfillmentBaseUrl;

  @Property(propertyName = "order.service.base.url")
  private String orderServiceBaseUrl;

  @Property(propertyName = "slot.update.listener.enabled.facilities")
  private List<Integer> slotUpdateListenerEnabledFacilities;

  @Property(propertyName = "inventory.delete.container.url.path")
  private String inventoryContainerDeletePath =
      "/inventory/inventories/containers/{trackingId}?" + "keyForDelete=delKey";

  @Property(propertyName = "yms.update.async.enabled")
  private boolean ymsUpdateAsyncEnable;

  @Property(propertyName = "gdm.search.pallet.request.time.range.in.days")
  private Integer gdmSearchPalletRequestTimeRangeInDays = 15;

  @Property(propertyName = "po.types.for.storage.check")
  private List<Integer> poTypesForStorageCheck;

  @Property(propertyName = "robo.depal.user.id")
  private String roboDepalUserId;

  @Property(propertyName = "gdm.search.pallet.request.time.to.addition.in.days")
  private Integer gdmSearchPalletRequestTimeToAdditionInDays = 1;

  @Property(propertyName = "handling.codes.for.conveyable.indicator")
  private List<String> handlingCodesForConveyableIndicator;

  @Property(propertyName = "scheduler.base.url")
  private String schedulerBaseUrl;

  @Property(propertyName = "oms.base.url")
  private String omsBaseUrl;

  @Property(propertyName = "enabled.http.basicconnectionmanager")
  @DefaultValue.Boolean(false)
  private Boolean basicConnectionManager;

  @Property(propertyName = "http.client.custom.connectionpooling.enabled")
  @DefaultValue.Boolean(false)
  private Boolean connectionPoolingEnabled;

  @Property(propertyName = "outbox.pattern.publisher.policy.ids")
  @DefaultValue.String("")
  private String outboxPatternPublisherPolicyIds;

  @Property(propertyName = "store.ngr.finalization.event.kafka.listener.enabled.facilities")
  private List<Integer> storeNgrFinalizationEventKafkaListenerEnabledFacilities;
}
