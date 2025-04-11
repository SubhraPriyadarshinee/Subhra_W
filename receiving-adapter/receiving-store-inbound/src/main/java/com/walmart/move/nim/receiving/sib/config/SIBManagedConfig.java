package com.walmart.move.nim.receiving.sib.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.SourceType;
import com.walmart.move.nim.receiving.sib.utils.Constants;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.*;
import io.strati.configuration.context.ConfigurationContext;
import io.strati.configuration.listener.ChangeLog;
import java.util.*;
import lombok.Getter;

@Configuration(configName = "sibManagedConfig")
@Getter
public class SIBManagedConfig {

  @Property(propertyName = "container.event.listener.enabled.facilities")
  private List<Integer> containerEventListenerEnabledFacilities;

  @Property(propertyName = "publish.event.job.run.every.x.minute")
  private int publishEventJobRunEveryXMinute;

  @Property(propertyName = "event.processing.job.run.every.x.minute")
  private int eventProcessingJobRunEveryXMinute = 2;

  @Property(propertyName = "publish.event.max.db.fetch.size")
  private int publishEventMaxDbFetchSize;

  @Property(propertyName = "publish.event.kafka.batch.size")
  private int publishEventKafkaBatchSize;

  @Property(propertyName = "receipt.save.batch.size")
  private int receiptSaveBatchSize = Integer.MAX_VALUE;

  @Property(propertyName = "publish.container.kafka.batch.size")
  private int publishContainerKafkaBatchSize = 500;

  @Property(propertyName = "delayed.document.ingest.threshold.hours")
  private int delayedDocumentIngestThresholdHours = 2;

  @Property(propertyName = "delayed.document.ingest.sc.event.pick.time.delay.minutes")
  private int delayedDocumentScEventPickTimeDelayMinutes = 20;

  @Property(propertyName = "sc.event.pick.time.delay.hours")
  private int scEventPickTimeDelay = 2;

  @Property(propertyName = "default.event.pick.time.delay.min")
  private int defaultEventPickTimeDelay = 120;

  @Property(propertyName = "nhm.event.pick.time.delay.hours")
  private int nhmEventPickTimeDelay = 2;

  @Property(propertyName = "meat.produce.event.pick.time.delay.hours")
  private int meatProduceEventPickTimeDelay = 2;

  @Property(propertyName = "overage.event.pick.time.delay.hours")
  private int overageEventPickTimeDelay = 2;

  @Property(propertyName = "reference.shift.start.hours")
  private int referenceShiftStartHours = 9;

  @Property(propertyName = "reference.shift.end.hours")
  private int referenceShiftEndHours = 21;

  @Property(propertyName = "reference.shift.store.finalization.trigger.hours")
  private int referenceShiftSFTriggersHours = 21;

  @Property(propertyName = "ngr.parity.event.process.delivery.status")
  private List<String> ngrParityRIPEvent = Arrays.asList(Constants.EVENT_DELIVERY_ARRIVED);

  @Property(propertyName = "ngr.parity.event.process.facility")
  private List<Integer> ngrParityFacilities;

  @Property(propertyName = "child.item.containing.assortment.types")
  private List<String> assortmentTypes = Arrays.asList(Constants.SHIPPER);

  @Property(propertyName = "enable.inventory.availability.new.flow")
  private boolean enableNewAvailabilityFlow = Boolean.TRUE;

  @Property(propertyName = "mark.unstocked.as.problem.after.x.hours")
  private int markUnstockedAsProblemAfterXHours;

  @Property(propertyName = "cleanup.register.on.delivery.event")
  private String cleanupRegisterOnDeliveryEvent = Constants.EVENT_DELIVERY_ARRIVED;

  @Property(propertyName = "store.auto.initialization.on.delivery.event")
  private String storeAutoInitializationOnDeliveryEvent = Constants.EVENT_DELIVERY_ARRIVED;

  @Property(propertyName = "store.auto.initialization.event.delay.mins")
  private int storeAutoInitializationEventDelayMins = 30;

  @Property(propertyName = "cleanup.event.delay.hours")
  private int cleanupEventDelayHours = 72;

  @Property(propertyName = "manual.finalization.delay.minutes")
  private int manulFinalizationDelay = 20;

  @Property(propertyName = "correction.inv.ngr.parity.event.process.delivery.status")
  private List<String> correctionalInvNgrParityRIPEvent =
      Arrays.asList(ReceivingConstants.EVENT_DELIVERY_SHIPMENT_ADDED);

  @Property(propertyName = "ngr.event.eligible.pallet.type")
  private String ngrEventEligiblePalletType;

  @Property(propertyName = "correctional.invoice.document.type")
  private List<String> correctionalInvoiceDocumentType;

  @Property(propertyName = "ngr.event.for.charge.invoice")
  private String ngrEventForChargeInvoice;

  @Property(propertyName = "ngr.event.for.credit.invoice")
  private String ngrEventForCreditInvoice;

  @Property(propertyName = "eligible.document.type.for.mixed.pallet.processing")
  List<String> eligibleDocumentTypeForMixedPalletProcessing;

  @Property(propertyName = "correctional.invoice.event.subtype")
  private String correctionalInvoiceEventSubType;

  @Property(propertyName = "gdm.event.processing.eligible.delivery.source.type")
  private List<String> eligibleDeliverySourceTypeForGDMEventProcessing =
      Arrays.asList(SourceType.DC.name());

  @Property(propertyName = "case.create.eligible.delivery.source.type")
  private List<String> eligibleDeliverySourceTypeForCaseCreation =
      Arrays.asList(SourceType.DC.name());

  /**
   * Sample data. If document type is not configured both Store & MFC pallets will be considered by
   * default. { "MANUAL_BILLING_ASN": [ "STORE" ] } *
   */
  @Ignore private Map<String, Set<String>> ngrEventEligiblePalletTypeMap;

  @PostInit
  public void postInit(String configName, ConfigurationContext context) {

    initializeNgrEventEligiblePalletTypeMap();
  }

  @PostRefresh
  public void postRefresh(
      String configName, List<ChangeLog> changes, ConfigurationContext context) {
    initializeNgrEventEligiblePalletTypeMap();
  }

  private void initializeNgrEventEligiblePalletTypeMap() {
    TypeReference<HashMap<String, Set<String>>> typeRef =
        new TypeReference<HashMap<String, Set<String>>>() {};
    this.ngrEventEligiblePalletTypeMap =
        Objects.nonNull(ngrEventEligiblePalletType)
            ? ReceivingUtils.convertStringToObject(ngrEventEligiblePalletType, typeRef)
            : new HashMap<>();
  }
}
