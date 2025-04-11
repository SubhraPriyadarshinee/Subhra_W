package com.walmart.move.nim.receiving.core.message.publisher;

import com.walmart.move.nim.receiving.core.advice.SecurePublisher;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.KafkaHelper;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.message.common.ItemUpdateInstructionMessage;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Date;
import java.util.Map;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component(ReceivingConstants.KAFKA_ITEM_UPDATE_INSTRUCTION_PUBLISHER)
public class KafkaItemUpdateInstructionPublisher
    implements MessagePublisher<ItemUpdateInstructionMessage> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(KafkaItemUpdateInstructionPublisher.class);

  @SecurePublisher private KafkaTemplate securePublisher;

  @Value("${item.update.instruction.topic:}")
  private String itemUpdateInstructionTopic;

  @Override
  public void publish(
      ItemUpdateInstructionMessage itemUpdateInstructionMessage, Map<String, Object> headers) {
    String instructionJson;
    headers.put(ReceivingConstants.MSG_TIMESTAMP, ReceivingUtils.dateConversionToUTC(new Date()));
    try {
      instructionJson = JacksonParser.writeValueAsString(itemUpdateInstructionMessage);
      Message<String> message =
          KafkaHelper.buildKafkaMessage(
              itemUpdateInstructionMessage.getItemNumber(),
              instructionJson,
              itemUpdateInstructionTopic,
              headers);
      securePublisher.send(message);
      LOGGER.info(
          "Successfully published item update instruction payload: {} over Kafka to Hawkeye",
          instructionJson);
    } catch (Exception exception) {
      LOGGER.error(
          "Unable to send payload: {} over Kafka to Hawkeye, Exception: {} ",
          itemUpdateInstructionMessage,
          ExceptionUtils.getStackTrace(exception));
      throw new ReceivingInternalException(
          ExceptionCodes.KAFKA_NOT_ACCESSABLE, ReceivingConstants.KAFKA_NOT_ACCESSIBLE_ERROR_MSG);
    }
  }
}
