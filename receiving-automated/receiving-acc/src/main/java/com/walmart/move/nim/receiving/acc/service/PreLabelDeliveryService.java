package com.walmart.move.nim.receiving.acc.service;

import com.walmart.move.nim.receiving.acc.config.ACCManagedConfig;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ApplicationBaseException;
import com.walmart.move.nim.receiving.core.entity.DeliveryEvent;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.service.DeliveryEventPersisterService;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.core.service.LocationService;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

/**
 * @author g0k0072 This class will take delivery event messages as input and will create strore
 *     friendly labels for that
 */
public class PreLabelDeliveryService {

  private static final Logger LOGGER = LoggerFactory.getLogger(PreLabelDeliveryService.class);

  @ManagedConfiguration private ACCManagedConfig accManagedConfig;

  @Resource(name = ReceivingConstants.DELIVERY_EVENT_PERSISTER_SERVICE)
  private DeliveryEventPersisterService deliveryEventPersisterService;

  @Autowired private LocationService locationService;

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  /**
   * This method will process delivery events and generate and persist store friendly labels
   *
   * @param messageData message based on different events e.g. DOOR_ASSIGNED, PO_ADDED etc
   */
  @Timed(
      name = "preLabelGenTimed",
      level1 = "uwms-receiving",
      level2 = "preLabelDeliveryService",
      level3 = "processDeliveryEvent")
  @ExceptionCounted(
      name = "preLabelGenExceptionCount",
      level1 = "uwms-receiving",
      level2 = "preLabelDeliveryService",
      level3 = "processDeliveryEvent")
  public void processDeliveryEvent(MessageData messageData) {
    DeliveryUpdateMessage deliveryUpdateMessage = (DeliveryUpdateMessage) messageData;
    Long deliveryNumber = Long.parseLong(deliveryUpdateMessage.getDeliveryNumber());
    LOGGER.info(
        "Received: {} event for DeliveryNumber: {} with {} status.",
        deliveryUpdateMessage.getEventType(),
        deliveryNumber,
        deliveryUpdateMessage.getDeliveryStatus());

    if (!validatePreLabelEventAndDeliveryStatus(deliveryUpdateMessage)) {
      LOGGER.info(
          "Received: {} event with {} delivery status. Hence ignoring for pre-label generation",
          deliveryUpdateMessage.getEventType(),
          deliveryUpdateMessage.getDeliveryStatus());
      return;
    }

    DeliveryDetails deliveryDetails =
        fetchDeliveryDetails(deliveryUpdateMessage.getUrl(), deliveryNumber);

    if (!Objects.isNull(deliveryDetails) && StringUtils.isEmpty(deliveryDetails.getDoorNumber())) {
      LOGGER.info(
          "Received: {} event with {} delivery status and without door. Hence ignoring for pre-label generation",
          deliveryUpdateMessage.getEventType(),
          deliveryUpdateMessage.getDeliveryStatus());
      return;
    }

    Boolean isOnlineOrHasFloorLine = null;
    if (!Objects.isNull(deliveryDetails) && !StringUtils.isEmpty(deliveryDetails.getDoorNumber())) {
      try {
        isOnlineOrHasFloorLine =
            locationService.isOnlineOrHasFloorLine(deliveryDetails.getDoorNumber());
        if (!Boolean.TRUE.equals(isOnlineOrHasFloorLine)) {
          LOGGER.info(
              "Delivery {} is at an offline door {}. Hence ignoring for pre-label generation",
              deliveryUpdateMessage.getDeliveryNumber(),
              deliveryDetails.getDoorNumber());
          return;
        }
      } catch (ApplicationBaseException applicationBaseException) {
        LOGGER.info(
            "Can't fetch location detail for delivery: {} and door: {}. Continuing to check and persist label",
            deliveryUpdateMessage.getDeliveryNumber(),
            deliveryDetails.getDoorNumber());
      }
    }

    createAndProcessDeliveryEvent(
        deliveryUpdateMessage, deliveryNumber, deliveryDetails, isOnlineOrHasFloorLine);
  }

  private void createAndProcessDeliveryEvent(
      DeliveryUpdateMessage deliveryUpdateMessage,
      Long deliveryNumber,
      DeliveryDetails deliveryDetails,
      Boolean isOnlineOrHasFloorLine) {
    DeliveryEvent deliveryEvent =
        deliveryEventPersisterService.getDeliveryEventToProcess(deliveryUpdateMessage);
    if (Objects.isNull(deliveryEvent)) {
      LOGGER.info(
          "Nothing to process for pre-label generation for delivery no. {}", deliveryNumber);
      return;
    }

    if (Objects.isNull(deliveryDetails) || Objects.isNull(isOnlineOrHasFloorLine)) {
      LOGGER.info(
          "Failed to fetch delivery: {} details or location info. Hence storing the event and ignoring pre-label generation",
          deliveryNumber);
      deliveryEvent.setEventStatus(EventTargetStatus.PENDING);
      deliveryEventPersisterService.save(deliveryEvent);
      return;
    }

    checkDeliveryEventStatusAndPublishLabels(
        deliveryUpdateMessage, deliveryNumber, deliveryDetails, deliveryEvent);
  }

  private void checkDeliveryEventStatusAndPublishLabels(
      DeliveryUpdateMessage deliveryUpdateMessage,
      Long deliveryNumber,
      DeliveryDetails deliveryDetails,
      DeliveryEvent deliveryEvent) {
    try {
      String doorNumber = deliveryDetails.getDoorNumber();
      String trailer = deliveryDetails.getTrailerId();
      Pair<DeliveryEvent, Map<DeliveryDocumentLine, List<LabelData>>>
          refreshedDeliveryEventAndLabelData =
              tenantSpecificConfigReader
                  .getConfiguredInstance(
                      TenantContext.getFacilityNum().toString(),
                      ReceivingConstants.LABEL_GENERATOR_SERVICE,
                      GenericLabelGeneratorService.class)
                  .generateGenericLabel(deliveryEvent, deliveryDetails);
      if (EventTargetStatus.STALE.equals(
          refreshedDeliveryEventAndLabelData.getKey().getEventStatus())) {
        LOGGER.info(
            "Delivery event has been marked as STALE. Hence not saving the event for delivery {}",
            deliveryNumber);
        return;
      }
      LOGGER.info(
          "Pre generation successful for delivery no. {} and delivery event {}. Hence marking it as DELETE",
          deliveryNumber,
          deliveryUpdateMessage.getEventType());
      deliveryEvent.setEventStatus(EventTargetStatus.DELETE);
      deliveryEventPersisterService.save(deliveryEvent);

      tenantSpecificConfigReader
          .getConfiguredInstance(
              TenantContext.getFacilityNum().toString(),
              ReceivingConstants.LABEL_GENERATOR_SERVICE,
              GenericLabelGeneratorService.class)
          .publishLabelsToAcl(
              refreshedDeliveryEventAndLabelData.getValue(),
              deliveryNumber,
              doorNumber,
              trailer,
              isPartialLabel(deliveryEvent));

    } catch (ReceivingException receivingException) {
      LOGGER.error(
          "{} exception occurred while generating pre-label for delivery no. {} and event {}. Hence marking delivery event as PENDING",
          receivingException.getErrorResponse().getErrorMessage(),
          deliveryNumber,
          deliveryUpdateMessage.getEventType());
      deliveryEvent.setEventStatus(EventTargetStatus.PENDING);
      deliveryEventPersisterService.save(deliveryEvent);
    }
  }

  private boolean isPartialLabel(DeliveryEvent deliveryEvent) {
    return !ReceivingConstants.EVENT_DOOR_ASSIGNED.equals(deliveryEvent.getEventType())
        && (!ReceivingConstants.PRE_LABEL_GEN_FALLBACK.equals(deliveryEvent.getEventType()));
  }

  public boolean processDeliveryEventForScheduler(DeliveryEvent deliveryEvent) {
    Long deliveryNumber = deliveryEvent.getDeliveryNumber();
    deliveryEventPersisterService.saveEventForScheduler(deliveryEvent);
    DeliveryDetails deliveryDetails = fetchDeliveryDetails(deliveryEvent.getUrl(), deliveryNumber);

    if (Objects.isNull(deliveryDetails)) {
      LOGGER.info(
          "Failed to fetch delivery: {} details. Hence storing the event and ignoring pre-label generation through scheduler",
          deliveryNumber);
      deliveryEvent.setEventStatus(EventTargetStatus.PENDING);
      deliveryEventPersisterService.save(deliveryEvent);
      return false;
    }

    try {
      String doorNumber = deliveryDetails.getDoorNumber();
      String trailer = deliveryDetails.getTrailerId();
      Pair<DeliveryEvent, Map<DeliveryDocumentLine, List<LabelData>>>
          refreshedDeliveryEventAndLabelData =
              tenantSpecificConfigReader
                  .getConfiguredInstance(
                      TenantContext.getFacilityNum().toString(),
                      ReceivingConstants.LABEL_GENERATOR_SERVICE,
                      GenericLabelGeneratorService.class)
                  .generateGenericLabel(deliveryEvent, deliveryDetails);
      if (EventTargetStatus.STALE.equals(
          refreshedDeliveryEventAndLabelData.getKey().getEventStatus())) {
        LOGGER.info(
            "Delivery event has been marked as STALE. Hence stopping further pre-label generation through scheduler for delivery {}",
            deliveryNumber);
        return false;
      }
      LOGGER.info(
          "Pre generation through scheduler successful for delivery no. {} and delivery event {}. Hence marking it as DELETE",
          deliveryNumber,
          deliveryEvent.getEventType());
      deliveryEvent.setEventStatus(EventTargetStatus.DELETE);
      deliveryEventPersisterService.save(deliveryEvent);
      tenantSpecificConfigReader
          .getConfiguredInstance(
              TenantContext.getFacilityNum().toString(),
              ReceivingConstants.LABEL_GENERATOR_SERVICE,
              GenericLabelGeneratorService.class)
          .publishLabelsToAcl(
              refreshedDeliveryEventAndLabelData.getValue(),
              deliveryNumber,
              doorNumber,
              trailer,
              isPartialLabel(deliveryEvent));
      return true;
    } catch (ReceivingException receivingException) {
      LOGGER.error(
          "{} exception occurred while generating pre-label through scheduler for delivery no. {} and event {}. Hence marking delivery event as PENDING and stopping the upcoming events",
          receivingException.getErrorResponse().getErrorMessage(),
          deliveryNumber,
          deliveryEvent.getEventType());
      deliveryEvent.setEventStatus(EventTargetStatus.PENDING);
      deliveryEventPersisterService.save(deliveryEvent);
      return false;
    }
  }

  /**
   * Call location service to determine if a deliver is docked to a door having ACL or has floor
   * line mapping
   *
   * @param deliveryNumber Delivery number
   * @param deliveryDetails response from GDM for delivery search
   * @return true if it has ACL or floor line mapping, false if offline without fl mapping else null
   */
  public Boolean validateDeliveryDetailsAndLocationInfo(
      Long deliveryNumber, DeliveryDetails deliveryDetails) {
    Boolean isOnlineOrHasFloorLine = null;
    if (!Objects.isNull(deliveryDetails) && !StringUtils.isEmpty(deliveryDetails.getDoorNumber())) {
      try {
        String doorNumber = deliveryDetails.getDoorNumber();
        LOGGER.debug("Fetching location info for locationId:{}", doorNumber);
        return locationService.isOnlineOrHasFloorLine(doorNumber);
      } catch (ApplicationBaseException applicationBaseException) {
        LOGGER.info(
            "Can't fetch location detail for delivery: {} and door: {}. Continuing to check and persist label",
            deliveryNumber,
            deliveryDetails.getDoorNumber());
      }
    }
    return isOnlineOrHasFloorLine;
  }

  /**
   * Call GDM and get the delivery information. Exceptions should be handled.
   *
   * @param url url to be hit for fetching the delivery document
   * @param deliveryNumber the delivery number
   * @return Delivery information
   */
  public DeliveryDetails fetchDeliveryDetails(String url, Long deliveryNumber) {
    DeliveryDetails deliveryDetails = null;
    try {
      LOGGER.debug("Fetching delivery info for URL : {}", url);
      DeliveryService deliveryService =
          tenantSpecificConfigReader.getConfiguredInstance(
              TenantContext.getFacilityNum().toString(),
              ReceivingConstants.DELIVERY_SERVICE_KEY,
              DeliveryService.class);
      deliveryDetails = deliveryService.getDeliveryDetails(url, deliveryNumber);
    } catch (ReceivingException receivingException) {
      LOGGER.info(
          "Can't fetch delivery: {} details. Continuing to check and persist event",
          deliveryNumber);
    }
    return deliveryDetails;
  }

  /**
   * Used for validating the pre label event type and the delivery status
   *
   * @param deliveryUpdateMessage delivery update message to be validated
   * @return true if valid, false if invalid
   */
  private boolean validatePreLabelEventAndDeliveryStatus(
      DeliveryUpdateMessage deliveryUpdateMessage) {
    return ReceivingUtils.isValidPreLabelEvent(deliveryUpdateMessage.getEventType())
        && ReceivingUtils.isValidStatus(
            DeliveryStatus.valueOf(deliveryUpdateMessage.getDeliveryStatus()));
  }
}
