package com.walmart.move.nim.receiving.acc.message.publisher;

import com.walmart.move.nim.receiving.core.advice.SecurePublisher;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.KafkaHelper;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.message.publisher.MessagePublisher;
import com.walmart.move.nim.receiving.core.model.docktag.DockTagInfo;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.platform.scm.client.shared.logging.Logger;
import com.walmart.platform.scm.client.shared.logging.LoggerFactory;
import java.util.Date;
import java.util.Map;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;

public class KafkaDockTagInfoPublisher implements MessagePublisher<DockTagInfo> {
  private static final Logger LOGGER = LoggerFactory.getLogger(KafkaDockTagInfoPublisher.class);
  @SecurePublisher private KafkaTemplate securePublisher;

  @Value("${kafka.docktag.info.topic:ATLAS_RCV_DOCKTAG_CONTAINER_DEV}")
  private String dockTagInfoTopic;

  @Override
  public void publish(DockTagInfo payload, Map<String, Object> headers) {
    try {
      headers.put(ReceivingConstants.MSG_TIMESTAMP, ReceivingUtils.dateConversionToUTC(new Date()));
      String dockTagInfo = JacksonParser.writeValueAsString(payload);
      Message<String> message =
          KafkaHelper.buildKafkaMessage(
              payload.getTrackingId(), dockTagInfo, dockTagInfoTopic, headers);
      securePublisher.send(message);
      LOGGER.info(
          "Successfully published DockTagInfo payload: {} on kafka topic: {}",
          dockTagInfo,
          dockTagInfoTopic);
    } catch (Exception e) {
      LOGGER.error(
          "Unable to send payload: {} over Kafka, Exception: {} ",
          payload,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingInternalException(
          ExceptionCodes.KAFKA_NOT_ACCESSABLE, ReceivingConstants.KAFKA_NOT_ACCESSIBLE_ERROR_MSG);
    }
  }
}
