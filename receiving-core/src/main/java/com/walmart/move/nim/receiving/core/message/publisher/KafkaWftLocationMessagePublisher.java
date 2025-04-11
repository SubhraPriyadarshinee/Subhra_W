package com.walmart.move.nim.receiving.core.message.publisher;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.KAFKA_NOT_ACCESSIBLE_ERROR_MSG;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.advice.SecurePublisher;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.KafkaHelper;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.model.LocationSummary;
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

@Component
public class KafkaWftLocationMessagePublisher implements MessagePublisher<LocationSummary> {

  private static final Logger LOG = LoggerFactory.getLogger(KafkaWftLocationMessagePublisher.class);

  @Value("${kafka.wft.location.scan.topic}")
  private String locationWftKafkaTopic;

  @SecurePublisher private KafkaTemplate secureKafkaTemplate;
  private Gson gson;

  public KafkaWftLocationMessagePublisher() {
    gson = new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
  }

  @Override
  public void publish(LocationSummary locationSummary, Map<String, Object> messageHeader) {

    String payload = gson.toJson(locationSummary);
    try {
      Message<String> message =
          KafkaHelper.buildKafkaMessage(
              locationSummary.getLocation(), payload, locationWftKafkaTopic, messageHeader);
      secureKafkaTemplate.send(message);
      LOG.info(
          "Successfully published location scan message: {} on topic: {} ",
          payload,
          locationWftKafkaTopic);
    } catch (Exception exception) {
      LOG.error(
          "Error in publishing location scan message on topic: {} with exception: {}",
          locationWftKafkaTopic,
          ExceptionUtils.getStackTrace(exception));
      throw new ReceivingInternalException(
          ExceptionCodes.KAFKA_NOT_ACCESSABLE,
          String.format(
              KAFKA_NOT_ACCESSIBLE_ERROR_MSG, ReceivingConstants.WFT_LOCATION_SCAN_PUBLISH_FLOW));
    }
  }
}
