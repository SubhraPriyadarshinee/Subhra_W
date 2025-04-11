package com.walmart.move.nim.receiving.mfc.message.publisher;

import static com.walmart.move.nim.receiving.mfc.common.MFCConstant.UNDERSCORE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;

import com.walmart.move.nim.receiving.core.common.KafkaHelper;
import com.walmart.move.nim.receiving.core.model.gdm.v3.StatusInformation;
import com.walmart.move.nim.receiving.mfc.model.ngr.NGRShipment;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;

/** Publish Manual Finalization event to NGR */
public class ManualFinalizationPublisher extends BaseNGRShipmentPublisher {

  private static final Logger LOGGER = LoggerFactory.getLogger(ManualFinalizationPublisher.class);

  @Value("${receive.shipment.arrival.topic:TOPIC_RECEIVE_SHIPMENT_ARRIVAL}")
  private String manualFinalizationTopic;

  protected Message<String> prepareKafkaMessage(NGRShipment ngrShipment) {
    Map<String, Object> headers = new HashMap<>();
    headers.put(EVENT_TYPE, SHIPMENT_MANUAL_FINALIZATION);

    ngrShipment
        .getDelivery()
        .setStatusInformation(
            StatusInformation.builder().status(EVENT_MANUAL_FINALIZATION).build());
    return KafkaHelper.buildKafkaMessage(
        StringUtils.joinWith(
            UNDERSCORE,
            TenantContext.getFacilityNum(),
            TenantContext.getFacilityCountryCode(),
            String.valueOf(ngrShipment.getDelivery().getDeliveryNumber())),
        gson.toJson(ngrShipment),
        manualFinalizationTopic,
        headers);
  }

  @Override
  protected void performPostProcessing(NGRShipment ngrShipment, Message<String> message) {
    LOGGER.info(
        "Successfully published shipment arrival to NGR. Message = {}, Processed Messsage = {}, Topic - {}",
        ngrShipment,
        message,
        manualFinalizationTopic);
  }
}
