package com.walmart.move.nim.receiving.rdc.message.listener;

import static org.apache.kafka.common.utils.Bytes.EMPTY;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.model.symbotic.LabelGroupUpdateCompletedEventMessage;
import com.walmart.move.nim.receiving.rdc.service.RdcLabelGroupUpdateCompletedEventProcessor;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.util.ObjectUtils;

public class LabelGroupUpdateCompletedEventListener {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(LabelGroupUpdateCompletedEventListener.class);
  @Autowired private Gson gson;

  @Autowired
  private RdcLabelGroupUpdateCompletedEventProcessor rdcLabelGroupUpdateCompletedEventProcessor;

  @KafkaListener(
      topics = "${hawkeye.sym.label.group.update.topic}",
      errorHandler = "kafkaErrorHandler",
      containerFactory = ReceivingConstants.HAWKEYE_SECURE_KAFKA_LISTENER_CONTAINER_FACTORY)
  @Timed(
      name = "rdcConsumeCompletionMessage",
      level1 = "uwms-receiving",
      level2 = "rdcConsumeCompletionMessage")
  @ExceptionCounted(
      name = "rdcConsumeCompletionMessage-Exception",
      level1 = "uwms-receiving",
      level2 = "rdcConsumeCompletionMessage-Exception")
  @TimeTracing(
      component = AppComponent.RDC,
      type = Type.MESSAGE,
      flow = "rdcConsumeCompletionMessage")
  public void listen(@Payload String message, @Headers Map<String, byte[]> kafkaHeaders) {
    if (ObjectUtils.isEmpty(message)) {
      LOGGER.error(ReceivingConstants.WRONG_COMPLETION_MESSAGE_FORMAT, message);
      return;
    }
    LOGGER.info("Received completed message from hawkeye, message: {}", message);
    try {
      LabelGroupUpdateCompletedEventMessage labelGroupUpdateCompletedEventMessage =
          gson.fromJson(message, LabelGroupUpdateCompletedEventMessage.class);
      labelGroupUpdateCompletedEventMessage.setHttpHeaders(
          ReceivingUtils.populateHttpHeadersFromKafkaHeaders(kafkaHeaders));

      if (!isValidEvent(kafkaHeaders, labelGroupUpdateCompletedEventMessage)) {
        LOGGER.info("Received label group message with delivery number missing: {}", message);
        return;
      }

      if (!isValidLabelGroupCompletedMessage(labelGroupUpdateCompletedEventMessage)) {
        LOGGER.info(
            "Received label group message with invalid status: {}",
            labelGroupUpdateCompletedEventMessage.getStatus());
        return;
      }
      rdcLabelGroupUpdateCompletedEventProcessor.processEvent(
          labelGroupUpdateCompletedEventMessage);

    } catch (Exception exception) {
      LOGGER.error(
          "Exception occurred processing Label Group Update : {}",
          ExceptionUtils.getStackTrace(exception));
    }
  }
  /**
   * This method Validate the event if the Label group update has the groupNbr in the header
   *
   * @param kafkaHeaders
   * @param labelGroupUpdateCompletedEventMessage
   * @return
   */
  private boolean isValidEvent(
      Map<String, byte[]> kafkaHeaders,
      LabelGroupUpdateCompletedEventMessage labelGroupUpdateCompletedEventMessage) {
    boolean retValue = false;
    if (kafkaHeaders.containsKey(ReceivingConstants.GROUP_NBR)) {
      byte[] groupNbrByteArr = kafkaHeaders.get(ReceivingConstants.GROUP_NBR);
      if (!Arrays.equals(groupNbrByteArr, EMPTY) && Objects.nonNull(groupNbrByteArr)) {
        String groupNumber = new String(groupNbrByteArr);
        labelGroupUpdateCompletedEventMessage.setDeliveryNumber(groupNumber);
        retValue = true;
      }
    }
    return retValue;
  }
  /**
   * This method validate whether the message has the status as COMPLETED. Other
   * status(COMPLETE_PENDING) will be ignored.
   *
   * @param labelGroupUpdateCompletedEventMessage
   * @return
   */
  private boolean isValidLabelGroupCompletedMessage(
      LabelGroupUpdateCompletedEventMessage labelGroupUpdateCompletedEventMessage) {
    return ReceivingConstants.COMPLETED_STATUS.equalsIgnoreCase(
        labelGroupUpdateCompletedEventMessage.getStatus());
  }
}
