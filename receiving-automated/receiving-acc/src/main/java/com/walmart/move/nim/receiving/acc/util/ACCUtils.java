package com.walmart.move.nim.receiving.acc.util;

import com.walmart.move.nim.receiving.acc.constants.ACCConstants;
import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.entity.DeliveryEvent;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.message.common.HawkeyeItemUpdateType;
import com.walmart.move.nim.receiving.core.model.ContainerModel;
import com.walmart.move.nim.receiving.core.model.FdeCreateContainerRequest;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.LocationInfo;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.label.RejectReason;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.PurchaseReferenceType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.util.StringUtils;

public class ACCUtils {

  private ACCUtils() {}

  public static boolean isDeliveryExistsOfType(
      List<DeliveryEvent> deliveryEvents, String eventType) {
    return deliveryEvents.parallelStream().anyMatch(o -> o.getEventType().equals(eventType));
  }

  public static List<EventTargetStatus> getEventStatusesForScheduler() {
    List<EventTargetStatus> eventsForScheduler = new ArrayList<>();
    eventsForScheduler.add(EventTargetStatus.PENDING);
    eventsForScheduler.add(EventTargetStatus.STALE);
    return eventsForScheduler;
  }

  public static List<EventTargetStatus> getPendingAndInProgressEventStatuses() {
    List<EventTargetStatus> eventTargetStatuses = new ArrayList<>();
    eventTargetStatuses.add(EventTargetStatus.PENDING);
    eventTargetStatuses.add(EventTargetStatus.IN_PROGRESS);
    return eventTargetStatuses;
  }

  public static DeliveryUpdateMessage getDeliveryUpdateMessageForFallback(
      Long deliveryNumber, String url) {
    return DeliveryUpdateMessage.builder()
        .deliveryNumber(deliveryNumber.toString())
        .deliveryStatus(DeliveryStatus.OPN.name())
        .countryCode(TenantContext.getFacilityCountryCode())
        .siteNumber(TenantContext.getFacilityNum().toString())
        .eventType(ReceivingConstants.PRE_LABEL_GEN_FALLBACK)
        .url(url)
        .build();
  }

  public static String extractNumber(String alphanumericString) {
    return alphanumericString.replaceAll("\\D+", "");
  }

  public static boolean checkIfLocationIsEitherOnlineOrFloorLine(LocationInfo locationInfo) {
    return locationInfo != null
        && ((Boolean.TRUE).equals(locationInfo.getIsOnline())
            || !StringUtils.isEmpty(locationInfo.getMappedFloorLine()));
  }

  /**
   * Used for getting reject reason code for DA Noncon, SSTK (Con/Noncon)
   *
   * @param deliveryDocumentLine for checking if delivery is DA Noncon, SSTK (Con/Noncon)
   * @return RejectReasonCode id it is DA Noncon, SSTK (Con/Noncon)
   */
  public static RejectReason getRejectReason(DeliveryDocumentLine deliveryDocumentLine) {
    if (PurchaseReferenceType.SSTKU
        .name()
        .equals(
            InstructionUtils.getPurchaseRefTypeIncludingChannelFlip(
                deliveryDocumentLine.getPurchaseRefType(),
                deliveryDocumentLine.getActiveChannelMethods()))) {
      return deliveryDocumentLine.getIsConveyable()
          ? RejectReason.CONVEYABLE_SSTK
          : RejectReason.NONCON_SSTK;
    }
    if (InstructionUtils.isDAFreight(
            deliveryDocumentLine.getPurchaseRefType(),
            deliveryDocumentLine.getActiveChannelMethods())
        && !deliveryDocumentLine.getIsConveyable()) {
      return RejectReason.NONCON_DA;
    }
    return null;
  }

  public static RejectReason getRejectReasonForItemUpdate(
      DeliveryDocumentLine deliveryDocumentLine, HawkeyeItemUpdateType eventType) {
    boolean isDAFreight =
        InstructionUtils.isDAFreight(
            deliveryDocumentLine.getPurchaseRefType(),
            deliveryDocumentLine.getActiveChannelMethods());
    boolean isConveyable = deliveryDocumentLine.getIsConveyable();

    switch (eventType) {
      case CONVEYABLE_TO_NONCON_GLOBAL:
        return isDAFreight ? RejectReason.NONCON_DA : null;
      case CROSSU_TO_SSTKU_DELIVERY:
        return isConveyable ? RejectReason.CONVEYABLE_SSTK : RejectReason.NONCON_SSTK_FLIP;
      case SSTKU_TO_CROSSU_DELIVERY:
        return !isConveyable ? RejectReason.NONCON_DA_FLIP : null;
      default:
        return null;
    }
  }

  /**
   * Used for getting Reject Code for delivery that has Null Reject Code
   *
   * @param IsDAConveyable for checking if it is DA Con
   * @return ACL_REJECT_ERROR_CODE if it is not DA Con
   */
  public static String getRejectCodeForNullRejectReason(boolean IsDAConveyable) {
    return Boolean.TRUE.equals(IsDAConveyable) ? null : ACCConstants.ACL_REJECT_ERROR_CODE;
  }

  public static void setFulfillmentTypeInFdeRequest(
      InstructionRequest instructionRequest, FdeCreateContainerRequest fdeCreateContainerRequest) {
    // Set PBYL recommended fulfillment type based on PByL docktag id present in instruction request
    ContainerModel containerModel = fdeCreateContainerRequest.getContainer();
    containerModel
        .getContents()
        .forEach(
            content ->
                content.setRecommendedFulfillmentType(
                    Objects.nonNull(instructionRequest.getPbylDockTagId())
                        ? ReceivingConstants.PBYL_FULFILLMENT_TYPE
                        : null));
    fdeCreateContainerRequest.setContainer(containerModel);
  }
}
