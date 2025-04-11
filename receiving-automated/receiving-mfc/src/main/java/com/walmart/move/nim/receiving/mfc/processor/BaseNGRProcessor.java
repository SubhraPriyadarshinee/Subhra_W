package com.walmart.move.nim.receiving.mfc.processor;

import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.message.common.ReceivingEvent;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ASNDocument;
import com.walmart.move.nim.receiving.mfc.model.ngr.NGRShipment;
import com.walmart.move.nim.receiving.mfc.service.MFCDeliveryService;
import com.walmart.move.nim.receiving.mfc.transformer.NGRShipmentTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class BaseNGRProcessor {
  private static final Logger LOGGER = LoggerFactory.getLogger(BaseNGRProcessor.class);

  @Autowired private MFCDeliveryService deliveryService;

  @Autowired private NGRShipmentTransformer ngrShipmentTransformer;

  public NGRShipment processReceivingEvents(ReceivingEvent receivingEvent) {
    LOGGER.info("Going to fetch shipment info from GDM");
    DeliveryUpdateMessage deliveryUpdateMessage =
        JacksonParser.convertJsonToObject(receivingEvent.getPayload(), DeliveryUpdateMessage.class);
    ASNDocument asnDocument =
        deliveryService.getShipmentDataFromGDM(
            Long.valueOf(deliveryUpdateMessage.getDeliveryNumber()),
            deliveryUpdateMessage.getShipmentDocumentId());
    LOGGER.info("Transforming GDM shipment info to NGR shipment object");
    NGRShipment ngrShipment = ngrShipmentTransformer.transform(asnDocument);
    LOGGER.info("Going to publish shipment arrival event to NGR");
    return ngrShipment;
  }
}
