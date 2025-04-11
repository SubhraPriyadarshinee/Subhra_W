package com.walmart.move.nim.receiving.mfc.config;

import com.walmart.move.nim.receiving.core.common.DocumentType;
import com.walmart.move.nim.receiving.core.common.SourceType;
import com.walmart.move.nim.receiving.mfc.common.MFCConstant;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.Configuration;
import io.strati.configuration.annotation.Property;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;

@Configuration(configName = "mfcManagedConfig")
@Getter
public class MFCManagedConfig {
  @Property(propertyName = "dsd.item.type.codes")
  private List<Long> itemTypeCodes;

  @Property(propertyName = "dsd.replenish.subtype.codes")
  private List<Long> replenishSubTypeCodes;

  @Property(propertyName = "mfc.post.delivery.flow.async.enabled")
  private boolean mfcPostDeliveryFlowAsyncEnabled;

  @Property(propertyName = "problem.registration.enabled")
  private boolean problemRegistrationEnabled = true;

  @Property(propertyName = "async.bulk.receiving.enabled")
  private boolean asyncBulkReceivingEnabled;

  @Property(propertyName = "store.pallet.create.enabled.facilities")
  private List<String> storePalletCreateEnabledFacilities;

  @Property(propertyName = "pallet.found.after.unload.threshold.time.minutes")
  private int palletFoundAfterUnloadThresholdTimeMinutes;

  @Property(propertyName = "delivery.status.for.open.deliveries")
  private List<String> deliveryStatusForOpenDeliveries;

  @Property(propertyName = "correctional.invoice.trigger.event")
  private String correctionalInvoiceTriggerEvent = ReceivingConstants.EVENT_DELIVERY_SHIPMENT_ADDED;

  @Property(propertyName = "inventory.container.removal.batch.size")
  private Integer inventoryContainerRemovalBatchSize = 10;

  @Property(propertyName = "correctional.invoice.document.type")
  private List<String> correctionalInvoiceDocumentType;

  @Property(propertyName = "enable.auto.mfc.exception.processing")
  private boolean enableAutoMFCExceptionProcessing = false;

  @Property(propertyName = "delivery.auto.complete.event.threshold.minutes")
  private int deliveryAutoCompleteEventThresholdMins = 30;

  @Property(propertyName = "auto.complete.delivery.status")
  private List<String> autoCompleteDeliveryStatus =
      Arrays.asList(
          DeliveryStatus.ARV.name(), DeliveryStatus.OPN.name(), DeliveryStatus.WRK.name());

  @Property(propertyName = "delivery.auto.complete.threshold.hours")
  private int deliveryAutoCompleteThresholdHours = 240;

  @Property(propertyName = "ngr.store.finalization.event.eligible.documentType")
  private List<String> eligibleDocumentTypeForNgrFinalizationEvent =
      Arrays.asList(DocumentType.ASN.name());

  @Property(propertyName = "ngr.store.finalization.event.eligible.deliveryType")
  private List<String> eligibleDeliveryTypeForNgrFinalizationEvent = Arrays.asList(MFCConstant.DSD);

  @Property(propertyName = "dsd.delivery.unload.complete.event.threshold.minutes")
  private int dsdDeliveryUnloadCompleteEventThresholdMinutes = 120;

  @Property(propertyName = "shortage.container.create.eligible.source.type")
  private List<String> eligibleSourceTypeForShortageContainerCreation =
      Arrays.asList(SourceType.DC.name());

  @Property(propertyName = "mixed.pallet.multi.reject.enabled")
  private boolean multiRejectEnabled = false;

  @Property(propertyName = "mixed.pallet.reject.multiplier")
  private Integer mixedPalletRejectMultiplier = 1;

  @Property(propertyName = "mixed.pallet.reason.code")
  private String mixedPalletReasonCode = MFCConstant.REJECTED;

  @Property(propertyName = "mixed.pallet.previous.state")
  private String mixedPalletPreviousState = MFCConstant.PENDING;

  @Property(propertyName = "mixed.pallet.current.state")
  private String mixedPalletCurrentState = MFCConstant.REJECTED;

  @Property(propertyName = "mixed.pallet.reject.location")
  private String mixedPalletRejectLocation = MFCConstant.UNKNOWN;

  @Property(propertyName = "mixed.pallet.reject.reason.desc")
  private String mixedPalletRejectReasonDesc = MFCConstant.REASON_DESC_NOT_MFC;

  @Property(propertyName = "mixed.pallet.request.originator")
  private String mixedPalletRequestOriginator = MFCConstant.ATLAS_INVENTORY;

  @Property(propertyName = "mixed.pallet.removal.event")
  private String mixedPalletRemovalEvent = MFCConstant.EVENT_DECANTING;
}
