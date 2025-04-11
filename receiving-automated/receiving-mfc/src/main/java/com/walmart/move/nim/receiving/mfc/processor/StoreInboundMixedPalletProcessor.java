package com.walmart.move.nim.receiving.mfc.processor;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.EVENT_DELIVERY_SHIPMENT_ADDED;

import com.walmart.move.nim.receiving.core.common.DocumentType;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.helper.ProcessInitiator;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.message.common.ReceivingEvent;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ASNDocument;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Shipment;
import com.walmart.move.nim.receiving.mfc.config.MFCManagedConfig;
import com.walmart.move.nim.receiving.mfc.message.publisher.ShipmentArrivalPublisher;
import com.walmart.move.nim.receiving.mfc.service.MFCDeliveryService;
import com.walmart.move.nim.receiving.mfc.service.MixedPalletRejectService;
import com.walmart.move.nim.receiving.mfc.transformer.NGRShipmentTransformer;
import com.walmart.move.nim.receiving.mfc.utils.MFCUtils;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.ObjectUtils;

public class StoreInboundMixedPalletProcessor extends AbstractMixedPalletProcessor {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(StoreInboundMixedPalletProcessor.class);
  private static final String EXPIRY_DATE_FORMAT = "yyyy-mm-dd";

  @Autowired private MFCDeliveryService deliveryService;

  @Autowired private NGRShipmentTransformer ngrShipmentTransformer;

  @Autowired private ShipmentArrivalPublisher kafkaShipmentArrivalPublisher;

  @Autowired private ProcessInitiator processInitiator;

  @Autowired private MixedPalletRejectService mixedPalletRejectService;

  @Value("${mixed.pallet.trigger.event:ARRIVED}")
  private String mixedPalletTriggerEvent;

  @Value("${shipment.arrival.event.status:ARRIVED}")
  private String shipmentArrivalEventType;

  @ManagedConfiguration private MFCManagedConfig mfcManagedConfig;

  @Override
  public void publishContainer(List<Container> containerList) {}

  @Timed(
      name = "storeInboundMixedPalletProcessingTimed",
      level1 = "uwms-receiving-api",
      level2 = "storeInboundMixedPalletProcessor")
  @ExceptionCounted(
      name = "storeInboundMixedPalletProcessingExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "storeInboundMixedPalletProcessor")
  @Override
  public void processEvent(MessageData messageData) throws ReceivingException {
    if (!(messageData instanceof DeliveryUpdateMessage)) {
      LOGGER.warn("StoreInbound MixedPallet : Not appropriate message to process mixed pallet.");
      return;
    }

    DeliveryUpdateMessage deliveryUpdateMessage = (DeliveryUpdateMessage) messageData;
    if (!isEventProcessable(deliveryUpdateMessage)) {
      LOGGER.warn(
          "StoreInbound MixedPallet : Unable to process deliveryEvent as not in right status = {} ",
          deliveryUpdateMessage);
      return;
    }
    Shipment shipment = getDeliveryShipment(deliveryUpdateMessage);
    if (MFCUtils.isDSDShipment(shipment)) {
      LOGGER.info("Ignoring problem container creation flow for DSD delivery.");
      return;
    }
    populateDeliveryShipmentDetails(deliveryUpdateMessage, shipment);
    if (isEligibleEventForMixedPalletProcessing(deliveryUpdateMessage)) {
      this.handleMixedPalletOperation(deliveryUpdateMessage);
    }

    if (shipmentArrivalEventType.equalsIgnoreCase(deliveryUpdateMessage.getEventType())) {
      Map<String, Object> forwardableHeaders =
          ReceivingUtils.getForwardablHeaderWithTenantData(ReceivingUtils.getHeaders());

      ReceivingEvent receivingEvent =
          ReceivingEvent.builder()
              .payload(JacksonParser.writeValueAsString(deliveryUpdateMessage))
              .name(ReceivingConstants.SHIPMENT_FINANCE_PROCESSOR)
              .additionalAttributes(forwardableHeaders)
              .processor(ReceivingConstants.SHIPMENT_FINANCE_PROCESSOR)
              .build();

      processInitiator.initiateProcess(receivingEvent, forwardableHeaders);
    }
  }

  private boolean isEligibleEventForMixedPalletProcessing(
      DeliveryUpdateMessage deliveryUpdateMessage) {

    if (CollectionUtils.isNotEmpty(mfcManagedConfig.getCorrectionalInvoiceDocumentType())
        && mfcManagedConfig
            .getCorrectionalInvoiceDocumentType()
            .contains(deliveryUpdateMessage.getShipmentDocumentType())) {
      return StringUtils.equalsIgnoreCase(
          deliveryUpdateMessage.getEventType(),
          mfcManagedConfig.getCorrectionalInvoiceTriggerEvent());
    }
    return DocumentType.ASN.equalsType(deliveryUpdateMessage.getShipmentDocumentType())
        && StringUtils.equalsIgnoreCase(
            mixedPalletTriggerEvent, deliveryUpdateMessage.getEventType());
  }

  @Override
  public void handleMixedPalletOperation(DeliveryUpdateMessage deliveryUpdateMessage) {
    ASNDocument asnDocument =
        deliveryService.findMixedContainerFromASN(
            Long.valueOf(deliveryUpdateMessage.getDeliveryNumber()),
            deliveryUpdateMessage.getShipmentDocumentId());
    mixedPalletRejectService.processMixedPalletReject(
        asnDocument, Long.valueOf(deliveryUpdateMessage.getDeliveryNumber()));
  }

  @Override
  public void handleMixedPalletCreation(DeliveryUpdateMessage deliveryUpdateMessage) {
    LOGGER.warn("No Mixed pallet container creation supported");
  }

  /**
   * Method to transform GDM shipment data to NGR shipment DTO and send it to NGR on Kafka
   *
   * @param deliveryUpdateMessage
   */
  @Override
  public Boolean isEventProcessable(DeliveryUpdateMessage deliveryUpdateMessage) {
    if (ObjectUtils.isEmpty(deliveryUpdateMessage.getEventType())) return false;
    return Arrays.asList(EVENT_DELIVERY_SHIPMENT_ADDED, ReceivingConstants.EVENT_DELIVERY_ARRIVED)
        .contains(deliveryUpdateMessage.getEventType());
  }

  private Shipment getDeliveryShipment(DeliveryUpdateMessage deliveryUpdateMessage) {

    Delivery delivery = null;
    try {
      delivery = deliveryService.getGDMData(deliveryUpdateMessage);
      Shipment shipment =
          delivery
              .getShipments()
              .stream()
              .findFirst()
              .orElseThrow(
                  () ->
                      new ReceivingDataNotFoundException(
                          ExceptionCodes.INVALID_DATA,
                          String.format(
                              "Shipment is not attached in delivery = %s",
                              deliveryUpdateMessage.getDeliveryNumber())));
      return shipment;
    } catch (ReceivingException e) {
      LOGGER.info(
          "Delivery data not found for delivery number {}",
          deliveryUpdateMessage.getDeliveryNumber());
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.DELIVERY_NOT_FOUND,
          String.format(
              "Delivery data not found for delivery number %s",
              deliveryUpdateMessage.getDeliveryNumber()));
    }
  }

  private void populateDeliveryShipmentDetails(
      DeliveryUpdateMessage deliveryUpdateMessage, Shipment shipment) {
    if (Objects.isNull(deliveryUpdateMessage.getShipmentDocumentType())) {
      deliveryUpdateMessage.setShipmentDocumentType(shipment.getDocumentType());
    }
    if (Objects.isNull(deliveryUpdateMessage.getShipmentDocumentId())) {
      deliveryUpdateMessage.setShipmentDocumentId(shipment.getDocumentId());
    }
  }
}
