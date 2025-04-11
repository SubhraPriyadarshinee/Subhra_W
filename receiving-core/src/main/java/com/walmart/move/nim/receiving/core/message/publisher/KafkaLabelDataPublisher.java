package com.walmart.move.nim.receiving.core.message.publisher;

import com.walmart.move.nim.receiving.core.advice.SecurePublisher;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.KafkaHelper;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.contract.prelabel.model.LabelData;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Date;
import java.util.Map;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

@Service(ReceivingConstants.KAFKA_LABEL_DATA_PUBLISHER)
public class KafkaLabelDataPublisher implements MessagePublisher<LabelData> {

  private static final Logger LOGGER = LoggerFactory.getLogger(KafkaLabelDataPublisher.class);

  @SecurePublisher private KafkaTemplate securePublisher;

  @Value("${acl.label.data.topic}")
  private String aclLabelDataTopic;

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Override
  public void publish(LabelData labelData, Map<String, Object> headers) {
    String labelDataJson;

    headers.put(
        ReceivingConstants.EVENT_TYPE,
        ReceivingConstants.HAWK_EYE_LABEL_DATA_EVENT_TYPE_HEADER_VALUE);
    headers.put(ReceivingConstants.VERSION, ReceivingConstants.LABEL_DATA_VERSION);
    headers.put(ReceivingConstants.MSG_TIMESTAMP, new Date());

    try {
      labelDataJson = JacksonParser.writeValueAsString(labelData);

      if (tenantSpecificConfigReader.isFeatureFlagEnabled(
          ReceivingConstants.ENABLE_LABEL_DATA_COMPRESSION)) {
        labelDataJson = ReceivingUtils.compressDataInBase64(labelDataJson);
      }

      Message<String> message =
          KafkaHelper.buildKafkaMessage(
              labelData.getDeliveryNumber(), labelDataJson, aclLabelDataTopic, headers);

      securePublisher.send(message);
      LOGGER.info(
          "Successfully published label download payload over Kafka to Hawkeye(ACL) {}",
          labelDataJson);
    } catch (Exception exception) {
      LOGGER.error("Unable to send to hawkeye {}", ExceptionUtils.getStackTrace(exception));
      throw new ReceivingInternalException(
          ExceptionCodes.KAFKA_NOT_ACCESSABLE, ReceivingConstants.KAFKA_NOT_ACCESSIBLE_ERROR_MSG);
    }
  }
}
