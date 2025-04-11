package com.walmart.move.nim.receiving.core.common;

import com.walmart.move.nim.receiving.core.message.publisher.MessagePublisher;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.core.model.OpenDockTagCount;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryResponse;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** @author g0k0072 Responsible for sending delivery status messages */
@Component
public class DeliveryStatusPublisher {
  private static final Logger log = LoggerFactory.getLogger(DeliveryStatusPublisher.class);

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  /**
   * @param deliveryNumber delivery number
   * @param deliveryStatus status of delivery e.g. OPEN, WORKING etc.
   * @param receipts receipts for that delivery.
   * @param headers map containing values for correlationId, userId etc
   * @return status message
   */
  public DeliveryInfo publishDeliveryStatus(
      long deliveryNumber,
      String deliveryStatus,
      List<ReceiptSummaryResponse> receipts,
      Map<String, Object> headers) {
    headers.put(ReceivingConstants.IDEM_POTENCY_KEY, UUID.randomUUID().toString());
    DeliveryInfo deliveryStatusMessage =
        constructDeliveryStatusMessage(deliveryNumber, deliveryStatus, receipts, headers);
    publishDeliveryStatusMessage(headers, deliveryStatusMessage);
    return deliveryStatusMessage;
  }

  /**
   * @param deliveryNumber delivery number
   * @param deliveryStatus status of delivery e.g. OPEN, WORKING etc.
   * @param receipts receipts for that delivery.
   * @param countOfDockTags number of open dock tags for that delivery
   * @param headers map containing values for correlationId, userId etc
   * @return status message
   */
  public DeliveryInfo publishDeliveryStatus(
      long deliveryNumber,
      String deliveryStatus,
      List<ReceiptSummaryResponse> receipts,
      Integer countOfDockTags,
      Map<String, Object> headers) {
    headers.put(ReceivingConstants.IDEM_POTENCY_KEY, UUID.randomUUID().toString());
    DeliveryInfo deliveryStatusMessage =
        constructDeliveryStatusMessage(deliveryNumber, deliveryStatus, receipts, headers);
    if (DeliveryStatus.COMPLETE.name().equals(deliveryStatus)) {
      if (Objects.nonNull(countOfDockTags)) {
        OpenDockTagCount openDockTagCount =
            OpenDockTagCount.builder().count(countOfDockTags).build();
        deliveryStatusMessage.setOpenDockTags(openDockTagCount);
      }
    }
    publishDeliveryStatusMessage(headers, deliveryStatusMessage);
    return deliveryStatusMessage;
  }

  private DeliveryInfo constructDeliveryStatusMessage(
      long deliveryNumber,
      String deliveryStatus,
      List<ReceiptSummaryResponse> receipts,
      Map<String, Object> headers) {
    DeliveryInfo deliveryStatusMessage = new DeliveryInfo();
    deliveryStatusMessage.setDeliveryNumber(deliveryNumber);
    deliveryStatusMessage.setDeliveryStatus(deliveryStatus);
    deliveryStatusMessage.setUserId(headers.get(ReceivingConstants.USER_ID_HEADER_KEY).toString());
    deliveryStatusMessage.setTs(new Date());
    deliveryStatusMessage.setReceipts(receipts);
    return deliveryStatusMessage;
  }

  /**
   * @param deliveryNumber delivery number
   * @param deliveryStatus status of delivery e.g. OPEN, WORKING etc.
   * @param headers map containing values for correlationId, userId etc
   * @return status message
   */
  public DeliveryInfo publishDeliveryStatus(
      long deliveryNumber,
      String deliveryStatus,
      String doorNumber,
      List<ReceiptSummaryResponse> receipts,
      Map<String, Object> headers,
      String action) {
    headers.put(ReceivingConstants.IDEM_POTENCY_KEY, UUID.randomUUID().toString());
    DeliveryInfo deliveryStatusMessage = new DeliveryInfo();
    deliveryStatusMessage.setDeliveryNumber(deliveryNumber);
    deliveryStatusMessage.setDeliveryStatus(deliveryStatus);
    deliveryStatusMessage.setUserId(headers.get(ReceivingConstants.USER_ID_HEADER_KEY).toString());
    deliveryStatusMessage.setTs(new Date());
    deliveryStatusMessage.setReceipts(receipts);
    deliveryStatusMessage.setDoorNumber(doorNumber);
    deliveryStatusMessage.setAction(action);
    publishDeliveryStatusMessage(headers, deliveryStatusMessage);
    return deliveryStatusMessage;
  }

  /**
   * Publishes delivery status message from factory
   *
   * @param headers delivery status message headers
   * @param deliveryStatusMessage delivery status message payload
   */
  public void publishDeliveryStatusMessage(
      Map<String, Object> headers, DeliveryInfo deliveryStatusMessage) {
    MessagePublisher messagePublisher =
        tenantSpecificConfigReader.getConfiguredInstance(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.DELIVERY_STATUS_PUBLISHER,
            MessagePublisher.class);
    messagePublisher.publish(deliveryStatusMessage, headers);
  }
}
