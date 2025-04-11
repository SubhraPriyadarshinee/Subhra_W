package com.walmart.move.nim.receiving.rdc.message.publisher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.message.publisher.MessagePublisher;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrSummary;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RdcMessagePublisher {

  private static final Logger log = LoggerFactory.getLogger(RdcMessagePublisher.class);

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  private Gson gson;

  public RdcMessagePublisher() {
    gson = new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
  }

  public void publish(DeliveryInfo deliveryInfo, Map<String, Object> messageHeaders) {
    // Set mandatory headers
    log.info(
        "Publishing delivery update message for deliveryNumber {}",
        deliveryInfo.getDeliveryNumber());
    messageHeaders.put(ReceivingConstants.IDEM_POTENCY_KEY, UUID.randomUUID().toString());
    messageHeaders.put(ReceivingConstants.MESSAGE_ID_HEADER, UUID.randomUUID().toString());
    messageHeaders.put(ReceivingConstants.DELIVERY_NUMBER, deliveryInfo.getDeliveryNumber());
    if (Objects.isNull(messageHeaders.get(ReceivingConstants.WMT_REQ_SOURCE)))
      messageHeaders.put(ReceivingConstants.COMPONENT_ID, ReceivingConstants.ATLAS_RECEIVING);
    else messageHeaders.put(ReceivingConstants.COMPONENT_ID, ReceivingConstants.NGR_RECEIVING);

    // publish message
    publishMessage(deliveryInfo, messageHeaders);
  }

  public void publishMessage(DeliveryInfo deliveryInfo, Map<String, Object> messageHeaders) {
    MessagePublisher messagePublisher =
        tenantSpecificConfigReader.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.DELIVERY_COMPLETE_EVENT_HANDLER,
            MessagePublisher.class);
    messagePublisher.publish(deliveryInfo, messageHeaders);
  }

  public DeliveryInfo publishDeliveryStatus(
      long deliveryNumber, String deliveryStatus, Map<String, Object> headers) {
    log.info(
        "Publishing {} delivery status update for delivery {}", deliveryStatus, deliveryNumber);
    headers.put(ReceivingConstants.JMS_EVENT_TYPE, deliveryStatus);
    DeliveryInfo deliveryStatusMessage = new DeliveryInfo();
    deliveryStatusMessage.setDeliveryNumber(deliveryNumber);
    deliveryStatusMessage.setDeliveryStatus(deliveryStatus);
    deliveryStatusMessage.setUserId(headers.get(ReceivingConstants.USER_ID_HEADER_KEY).toString());

    publish(deliveryStatusMessage, headers);
    return deliveryStatusMessage;
  }

  public void publishDeliveryStatus(
      DeliveryInfo deliveryStatusMessage, Map<String, Object> messageHeaders) {
    if ((ObjectUtils.allNotNull(
            deliveryStatusMessage.getDeliveryNumber(),
            deliveryStatusMessage.getDeliveryStatus(),
            deliveryStatusMessage.getDoorNumber(),
            deliveryStatusMessage.getTrailerNumber()))
        || (Objects.nonNull(deliveryStatusMessage.getDeliveryNumber())
            && deliveryStatusMessage
                .getDeliveryStatus()
                .equalsIgnoreCase(DeliveryStatus.TAG_COMPLETE.name()))) {
      messageHeaders.put(
          ReceivingConstants.JMS_EVENT_TYPE, deliveryStatusMessage.getDeliveryStatus());
      deliveryStatusMessage.setUserId(
          messageHeaders.get(ReceivingConstants.USER_ID_HEADER_KEY).toString());
      publish(deliveryStatusMessage, messageHeaders);
    } else {
      log.error(
          "Mandatory delivery details are missing in the GDM delivery status update request for "
              + "deliveryNumber:{}",
          deliveryStatusMessage.getDeliveryNumber());
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_DELIVERY_STATUS_UPDATE_REQUEST,
          String.format(
              ReceivingConstants.INVALID_DELIVERY_STATUS_UPDATE_REQUEST,
              deliveryStatusMessage.getDeliveryNumber()),
          deliveryStatusMessage.getDeliveryNumber().toString());
    }
  }

  public void publishDeliveryReceipts(OsdrSummary deliveryInfo, Map<String, Object> headers) {
    log.info("Publishing delivery receipts with body {}", deliveryInfo);
    headers.put(ReceivingConstants.OSDR_EVENT_TYPE_KEY, ReceivingConstants.OSDR_EVENT_TYPE_VALUE);
    publish(deliveryInfo, headers);
  }
}
