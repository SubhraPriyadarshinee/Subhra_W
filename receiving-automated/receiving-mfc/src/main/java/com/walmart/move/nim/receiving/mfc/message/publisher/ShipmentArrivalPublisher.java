package com.walmart.move.nim.receiving.mfc.message.publisher;

import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.UNDERSCORE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.EVENT_DELIVERY_ARRIVED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.EVENT_TYPE;

import com.walmart.move.nim.receiving.core.common.KafkaHelper;
import com.walmart.move.nim.receiving.mfc.model.ngr.NGRShipment;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;

/** Publish shipment arrival event to NGR */
public class ShipmentArrivalPublisher extends BaseNGRShipmentPublisher {

  private static final Logger LOGGER = LoggerFactory.getLogger(ShipmentArrivalPublisher.class);

  @Value("${receive.shipment.arrival.topic:TOPIC_RECEIVE_SHIPMENT_ARRIVAL}")
  String shipmentArrivalTopic;

  protected Message<String> prepareKafkaMessage(NGRShipment ngrShipment) {
    Map<String, Object> headers = new HashMap<>();
    headers.put(EVENT_TYPE, EVENT_DELIVERY_ARRIVED);
    return KafkaHelper.buildKafkaMessage(
        StringUtils.joinWith(
            UNDERSCORE,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode(),
            String.valueOf(ngrShipment.getDelivery().getDeliveryNumber())),
        gson.toJson(ngrShipment),
        shipmentArrivalTopic,
        headers);
  }

  protected void performPostProcessing(NGRShipment ngrShipment, Message<String> message) {
    LOGGER.info(
        "Successfully published shipment arrival to NGR. Message = {}, Processed Message = {}, Topic - {}",
        ngrShipment,
        message,
        shipmentArrivalTopic);
  }
}
