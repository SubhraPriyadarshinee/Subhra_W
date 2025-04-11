package com.walmart.move.nim.receiving.acc.message.publisher;

import com.walmart.move.nim.receiving.acc.constants.ACCConstants;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.contract.prelabel.model.LabelData;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.message.publisher.MessagePublisher;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class JMSLabelDataPublisher implements MessagePublisher<LabelData> {
  private static final Logger LOGGER = LoggerFactory.getLogger(JMSLabelDataPublisher.class);

  @Autowired private JmsPublisher jmsPublisher;

  public void publish(LabelData labelData, Map<String, Object> messageHeaders) {
    String labelDataJson = JacksonParser.writeValueAsString(labelData);
    String compressedACLLabelDataInBase64;
    try {
      compressedACLLabelDataInBase64 = ReceivingUtils.compressDataInBase64(labelDataJson);
      ReceivingJMSEvent jmsEvent =
          new ReceivingJMSEvent(messageHeaders, compressedACLLabelDataInBase64);
      jmsPublisher.publish(ACCConstants.ACL_LABEL_DATA_TOPIC, jmsEvent, Boolean.TRUE);
      LOGGER.info(
          "LG: Successfully published labels to ACL for delivery {}. Data: {}",
          labelData.getDeliveryNumber(),
          labelDataJson);
    } catch (IOException ioException) {
      LOGGER.error("LG: Error occurred while publishing message to ACL ", ioException);
      throw new ReceivingInternalException(
          ExceptionCodes.UNABLE_TO_PUBLISH, ReceivingException.UNABLE_TO_PUBLISH);
    }
  }
}
